from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict
import json
import time
import urllib.error
import urllib.request


@dataclass
class ReviewConfig:
    enabled: bool = False
    ollama_url: str = "http://127.0.0.1:11434"
    model: str = "gemma4:26b"
    timeout_seconds: int = 240

    def normalized_url(self) -> str:
        return self.ollama_url.rstrip("/")


def review_package(output_dir: Path, report: Dict[str, Any], config: ReviewConfig) -> Dict[str, Any]:
    result = {
        "enabled": config.enabled,
        "status": "skipped",
        "model": config.model,
        "ollama_url": config.normalized_url(),
        "duration_seconds": 0.0,
        "summary": "",
    }
    if not config.enabled:
        return result

    started_at = time.time()
    base_json = _read_text(output_dir / report["files"]["base.json"])
    nn_json = _read_text(output_dir / report["files"]["nn.json"])
    nn_extend_json = _read_text(output_dir / report["files"]["nn.extend.json"])

    prompt = _build_review_prompt(report, base_json, nn_json, nn_extend_json)
    payload = {
        "model": config.model,
        "prompt": prompt,
        "stream": False,
    }
    request = urllib.request.Request(
        url=f"{config.normalized_url()}/api/generate",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=config.timeout_seconds) as response:
            response_json = json.loads(response.read().decode("utf-8"))
        result["status"] = "succeeded"
        result["summary"] = response_json.get("response", "").strip()
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError, ValueError) as exc:
        result["status"] = "failed"
        result["error"] = str(exc)
    finally:
        result["duration_seconds"] = round(time.time() - started_at, 2)

    review_path = output_dir / "gemma-review.json"
    review_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def _build_review_prompt(
    report: Dict[str, Any],
    base_json: str,
    nn_json: str,
    nn_extend_json: str,
) -> str:
    return "\n".join(
        [
            "你是 RKNN 算法包验收审查员。只按下面规则检查，不要发散。",
            "规则:",
            "1. base.json 必须包含 geid 和 name，值要与本次算法一致。",
            "2. nn.json 必须包含 model_path、cls_num、cls_name、cls_enable、type。",
            "3. YOLOv5 / v5 的 type 必须是 6。",
            "4. YOLOv8 / v8 的 type 必须是 8。",
            "5. nn.extend.json 必须包含 conf_thresh 和 class 映射。",
            "6. m31 包如果替换了 nn_server，需要在检查结果里明确指出。",
            "输出要求:",
            "- 用 4 到 6 条简短要点输出",
            "- 每条要点直接写结论",
            "- 如果发现风险，直接指出具体字段",
            "",
            f"构建报告: {json.dumps(report, ensure_ascii=False, indent=2)}",
            "",
            "base.json:",
            base_json,
            "",
            "nn.json:",
            nn_json,
            "",
            "nn.extend.json:",
            nn_extend_json,
        ]
    )


def _read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")
