from __future__ import annotations

from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Optional
import cgi
import html
import json
import shutil
import urllib.parse
import uuid

from .gemma_review import ReviewConfig
from .models import Manifest
from .workflow import build_package_workflow


@dataclass
class WebConfig:
    host: str
    port: int
    workdir: Path
    default_review_enabled: bool = False
    ollama_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "gemma4:26b"
    review_timeout_seconds: int = 240


class ConverterHTTPServer(ThreadingHTTPServer):
    def __init__(self, server_address: tuple[str, int], config: WebConfig) -> None:
        super().__init__(server_address, ConverterRequestHandler)
        self.config = config
        self.config.workdir.mkdir(parents=True, exist_ok=True)


class ConverterRequestHandler(BaseHTTPRequestHandler):
    server: ConverterHTTPServer

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/":
            self._send_html(_render_form(self.server.config))
            return

        if path == "/health":
            self._send_json(
                {
                    "status": "ok",
                    "service": "rknn-package-converter",
                    "workdir": str(self.server.config.workdir),
                    "default_review_enabled": self.server.config.default_review_enabled,
                    "ollama_url": self.server.config.ollama_url,
                    "ollama_model": self.server.config.ollama_model,
                }
            )
            return

        if path.startswith("/artifacts/"):
            self._serve_artifact(path)
            return

        self._send_text("Not found", status=HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/build":
            self._send_text("Not found", status=HTTPStatus.NOT_FOUND)
            return

        try:
            form = self._parse_form_data()
            result = self._build_from_form(form)
            self._send_html(_render_result(result))
        except Exception as exc:
            self._send_html(
                _render_form(self.server.config, error=str(exc)),
                status=HTTPStatus.BAD_REQUEST,
            )

    def log_message(self, format: str, *args: object) -> None:
        return

    def _parse_form_data(self) -> cgi.FieldStorage:
        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            raise ValueError("Only multipart/form-data is supported.")

        environ = {
            "REQUEST_METHOD": "POST",
            "CONTENT_TYPE": content_type,
            "CONTENT_LENGTH": self.headers.get("Content-Length", "0"),
        }
        return cgi.FieldStorage(
            fp=self.rfile,
            headers=self.headers,
            environ=environ,
            keep_blank_values=True,
        )

    def _build_from_form(self, form: cgi.FieldStorage) -> Dict[str, Any]:
        job_id = _generate_job_id()
        job_dir = self.server.config.workdir / "jobs" / job_id
        uploads_dir = job_dir / "uploads"
        uploads_dir.mkdir(parents=True, exist_ok=True)

        template_path = _save_upload(form, "template_file", uploads_dir, required=True)
        model_path = _save_upload(form, "model_file", uploads_dir, required=True)
        nn_server_path = _save_upload(form, "nn_server_file", uploads_dir, required=False)

        package_name = _required_text(form, "package_name")
        classes = json.loads(_required_text(form, "classes_json"))
        json_overrides = json.loads(form.getfirst("json_overrides", "{}").strip() or "{}")
        conf_thresh_raw = form.getfirst("conf_thresh", "").strip()
        conf_thresh = float(conf_thresh_raw) if conf_thresh_raw else None

        manifest_payload: Dict[str, Any] = {
            "template_path": str(template_path),
            "output_dir": str(job_dir / "package"),
            "output_archive": str(job_dir / "package.zip"),
            "package_name": package_name,
            "variant": _required_text(form, "variant"),
            "engine": {
                "name": _required_text(form, "engine_name"),
                "geid": int(_required_text(form, "engine_geid")),
            },
            "model": {
                "source_path": str(model_path),
                "package_path": _required_text(form, "model_package_path"),
                "framework": _required_text(form, "framework"),
            },
            "classes": classes,
            "json_overrides": json_overrides,
        }
        if conf_thresh is not None:
            manifest_payload["conf_thresh"] = conf_thresh
        if nn_server_path is not None:
            manifest_payload["nn_server_source_path"] = str(nn_server_path)

        manifest_path = job_dir / "manifest.json"
        manifest_path.write_text(json.dumps(manifest_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        manifest = Manifest.from_dict(manifest_payload, manifest_path, job_dir)
        review_requested = "gemma_review" in form
        review_config = ReviewConfig(
            enabled=review_requested,
            ollama_url=form.getfirst("ollama_url", self.server.config.ollama_url).strip() or self.server.config.ollama_url,
            model=form.getfirst("ollama_model", self.server.config.ollama_model).strip() or self.server.config.ollama_model,
            timeout_seconds=self.server.config.review_timeout_seconds,
        )
        workflow_result = build_package_workflow(manifest, review_config)

        package_dir = Path(workflow_result["report"]["output_dir"])
        report_path = package_dir / "conversion-report.json"
        if report_path.exists():
            shutil.copy2(report_path, job_dir / "conversion-report.json")

        review_path = package_dir / "gemma-review.json"
        if review_path.exists():
            shutil.copy2(review_path, job_dir / "gemma-review.json")

        return {
            "job_id": job_id,
            "job_dir": str(job_dir),
            "manifest_path": str(manifest_path),
            "result": workflow_result,
        }

    def _serve_artifact(self, path: str) -> None:
        relative = path.removeprefix("/artifacts/")
        requested = (self.server.config.workdir / relative).resolve()
        jobs_root = (self.server.config.workdir / "jobs").resolve()

        if jobs_root not in requested.parents and requested != jobs_root:
            self._send_text("Forbidden", status=HTTPStatus.FORBIDDEN)
            return
        if not requested.exists() or not requested.is_file():
            self._send_text("Not found", status=HTTPStatus.NOT_FOUND)
            return

        content_type = "application/octet-stream"
        if requested.suffix.lower() == ".json":
            content_type = "application/json; charset=utf-8"
        elif requested.suffix.lower() == ".zip":
            content_type = "application/zip"

        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(requested.stat().st_size))
        self.send_header("Content-Disposition", f'attachment; filename="{requested.name}"')
        self.end_headers()
        with requested.open("rb") as file_handle:
            shutil.copyfileobj(file_handle, self.wfile)

    def _send_html(self, body: str, status: HTTPStatus = HTTPStatus.OK) -> None:
        payload = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _send_json(self, payload: Dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        data = (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _send_text(self, body: str, status: HTTPStatus = HTTPStatus.OK) -> None:
        payload = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def create_server(config: WebConfig) -> ConverterHTTPServer:
    return ConverterHTTPServer((config.host, config.port), config)


def serve(config: WebConfig) -> None:
    with create_server(config) as server:
        print(
            f"RKNN package converter service listening on http://{config.host}:{server.server_address[1]} "
            f"(workdir={config.workdir})"
        )
        server.serve_forever()


def main(argv: Optional[list[str]] = None) -> int:
    del argv
    config = WebConfig(host="0.0.0.0", port=4174, workdir=Path(__file__).resolve().parents[2] / "runtime")
    serve(config)
    return 0


def _required_text(form: cgi.FieldStorage, field_name: str) -> str:
    value = form.getfirst(field_name, "").strip()
    if not value:
        raise ValueError(f"Field is required: {field_name}")
    return value


def _save_upload(
    form: cgi.FieldStorage,
    field_name: str,
    uploads_dir: Path,
    required: bool,
) -> Optional[Path]:
    if field_name not in form:
        if required:
            raise ValueError(f"File is required: {field_name}")
        return None

    field = form[field_name]
    if isinstance(field, list):
        field = field[0]

    if not getattr(field, "filename", None):
        if required:
            raise ValueError(f"File is required: {field_name}")
        return None

    filename = Path(field.filename).name or field_name
    target = uploads_dir / filename
    with target.open("wb") as file_handle:
        shutil.copyfileobj(field.file, file_handle)
    return target


def _generate_job_id() -> str:
    return uuid.uuid4().hex[:8]


def _render_form(config: WebConfig, error: str = "") -> str:
    error_block = ""
    if error:
        error_block = (
            "<div style='padding:12px;border:1px solid #d9534f;background:#fff3f2;color:#8a1f17;'>"
            f"{html.escape(error)}</div>"
        )

    review_checked = "checked" if config.default_review_enabled else ""
    classes_example = html.escape(
        json.dumps(
            [
                {"id": 0, "name": "fire", "display_name": "火焰", "enabled": True},
                {"id": 1, "name": "smoke", "display_name": "烟雾", "enabled": True},
            ],
            ensure_ascii=False,
            indent=2,
        )
    )
    overrides_example = html.escape(
        json.dumps(
            {"base.json": {}, "nn.json": {}, "nn.extend.json": {}},
            ensure_ascii=False,
            indent=2,
        )
    )

    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>RKNN Package Converter</title>
  <style>
    body {{ font-family: "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #16202a; }}
    .shell {{ max-width: 980px; margin: 32px auto; padding: 0 20px 40px; }}
    .panel {{ background: white; border: 1px solid #d9e0ea; border-radius: 16px; padding: 24px; box-shadow: 0 8px 24px rgba(16,24,40,0.06); }}
    h1 {{ margin: 0 0 8px; font-size: 30px; }}
    p {{ line-height: 1.6; }}
    form {{ display: grid; gap: 18px; }}
    .grid {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }}
    label {{ display: grid; gap: 8px; font-weight: 600; }}
    input, select, textarea {{ width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #bcc7d6; font: inherit; box-sizing: border-box; }}
    textarea {{ min-height: 180px; resize: vertical; }}
    .full {{ grid-column: 1 / -1; }}
    .hint {{ font-size: 13px; color: #586577; font-weight: 400; }}
    button {{ padding: 12px 18px; border: 0; border-radius: 999px; background: #0f5bd8; color: white; font-weight: 700; cursor: pointer; }}
    code {{ background: #eef3fb; padding: 2px 6px; border-radius: 6px; }}
  </style>
</head>
<body>
  <div class="shell">
    <div class="panel">
      <h1>RKNN Package Converter</h1>
      <p>这个服务只负责把训练产物打成厂商算法包。它不依赖当前训练平台，不共享任何业务状态。</p>
      {error_block}
      <form action="/build" method="post" enctype="multipart/form-data">
        <div class="grid">
          <label>算法包名称
            <input name="package_name" value="fire_smoke_m31" required>
          </label>
          <label>模板类型
            <select name="variant">
              <option value="m1">m1</option>
              <option value="m31" selected>m31</option>
            </select>
          </label>
          <label>引擎名称
            <input name="engine_name" value="fire_smoke" required>
          </label>
          <label>GEID
            <input name="engine_geid" value="1001" required>
          </label>
          <label>模型框架
            <select name="framework">
              <option value="yolov8" selected>yolov8</option>
              <option value="yolov5">yolov5</option>
            </select>
          </label>
          <label>模型写入路径
            <input name="model_package_path" value="models/fire_smoke.rknn" required>
          </label>
          <label>置信度阈值
            <input name="conf_thresh" value="0.25">
          </label>
          <label>Gemma 复核模型
            <input name="ollama_model" value="{html.escape(config.ollama_model)}">
          </label>
          <label class="full">模板 zip
            <input type="file" name="template_file" accept=".zip" required>
            <span class="hint">Web 入口默认接收 zip 模板；CLI 仍然支持目录或 zip。</span>
          </label>
          <label class="full">RKNN 模型文件
            <input type="file" name="model_file" accept=".rknn" required>
          </label>
          <label class="full">可选 nn_server
            <input type="file" name="nn_server_file">
          </label>
          <label class="full">类别配置 JSON
            <textarea name="classes_json" required>{classes_example}</textarea>
          </label>
          <label class="full">JSON Overrides
            <textarea name="json_overrides">{overrides_example}</textarea>
          </label>
          <label class="full">Ollama URL
            <input name="ollama_url" value="{html.escape(config.ollama_url)}">
          </label>
          <label class="full">
            <span>Gemma 复核</span>
            <div class="hint">勾选后会调用本机 Ollama 做结构复核；未启动时构建仍会完成，只会返回复核失败信息。</div>
            <input type="checkbox" name="gemma_review" value="1" {review_checked}>
          </label>
        </div>
        <button type="submit">开始转换</button>
      </form>
      <p class="hint">健康检查: <code>/health</code>，下载路径: <code>/artifacts/jobs/&lt;job_id&gt;/package.zip</code></p>
    </div>
  </div>
</body>
</html>"""


def _render_result(payload: Dict[str, Any]) -> str:
    workflow_result = payload["result"]
    report = workflow_result["report"]
    review = workflow_result.get("gemma_review")
    job_id = payload["job_id"]
    archive_url = f"/artifacts/jobs/{job_id}/package.zip"
    report_url = f"/artifacts/jobs/{job_id}/conversion-report.json"
    review_url = f"/artifacts/jobs/{job_id}/gemma-review.json"

    review_block = "<p>Gemma 复核: 未启用</p>"
    if review is not None:
        review_block = (
            "<div style='margin-top:18px;'>"
            "<h2>Gemma 复核</h2>"
            f"<p>状态: {html.escape(review.get('status', 'unknown'))}</p>"
            f"<p>模型: {html.escape(review.get('model', ''))}</p>"
            f"<p><a href='{review_url}'>下载 gemma-review.json</a></p>"
            f"<pre style='white-space:pre-wrap;background:#0f1720;color:#dbe8ff;padding:16px;border-radius:12px;'>{html.escape(review.get('summary', review.get('error', '')))}</pre>"
            "</div>"
        )

    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>转换完成</title>
  <style>
    body {{ font-family: "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #16202a; }}
    .shell {{ max-width: 980px; margin: 32px auto; padding: 0 20px 40px; }}
    .panel {{ background: white; border: 1px solid #d9e0ea; border-radius: 16px; padding: 24px; box-shadow: 0 8px 24px rgba(16,24,40,0.06); }}
    .actions a {{ display:inline-block; margin-right:12px; margin-bottom:12px; padding:10px 14px; border-radius:999px; background:#0f5bd8; color:white; text-decoration:none; }}
    pre {{ white-space: pre-wrap; background:#0f1720; color:#dbe8ff; padding:16px; border-radius:12px; }}
  </style>
</head>
<body>
  <div class="shell">
    <div class="panel">
      <h1>转换完成</h1>
      <p>任务号: <code>{html.escape(job_id)}</code></p>
      <div class="actions">
        <a href="{archive_url}">下载算法包 zip</a>
        <a href="{report_url}">下载 conversion-report.json</a>
        <a href="/">继续转换</a>
      </div>
      <h2>构建结果</h2>
      <pre>{html.escape(json.dumps(report, ensure_ascii=False, indent=2))}</pre>
      {review_block}
    </div>
  </div>
</body>
</html>"""
