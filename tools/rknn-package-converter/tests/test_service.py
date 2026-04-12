from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import json
import socket
import sys
import tempfile
import unittest
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from rknn_package_converter.gemma_review import ReviewConfig  # noqa: E402
from rknn_package_converter.models import Manifest  # noqa: E402
from rknn_package_converter.webapp import WebConfig, create_server  # noqa: E402
from rknn_package_converter.workflow import build_package_workflow  # noqa: E402


class BuildWithGemmaReviewTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="rknn-package-converter-review-tests-")
        self.workspace = Path(self.temp_dir.name)
        self.fixtures = ROOT / "tests" / "fixtures"
        self.ollama_server, self.ollama_thread = start_server(FakeOllamaHandler)
        self.ollama_url = f"http://127.0.0.1:{self.ollama_server.server_address[1]}"

    def tearDown(self) -> None:
        self.ollama_server.shutdown()
        self.ollama_server.server_close()
        self.ollama_thread.join(timeout=5)
        self.temp_dir.cleanup()

    def test_build_with_fake_gemma_review(self) -> None:
        manifest_payload = {
            "template_path": str(self.fixtures / "template_m1"),
            "output_dir": str(self.workspace / "out" / "fire_smoke_m1"),
            "output_archive": str(self.workspace / "out" / "fire_smoke_m1.zip"),
            "package_name": "fire_smoke_m1",
            "variant": "m1",
            "engine": {"name": "fire_smoke", "geid": 1001},
            "model": {
                "source_path": str(self.fixtures / "artifacts" / "fire_smoke.rknn"),
                "package_path": "models/fire_smoke.rknn",
                "framework": "yolov8",
            },
            "classes": [
                {"id": 0, "name": "fire", "display_name": "火焰", "enabled": True},
                {"id": 1, "name": "smoke", "display_name": "烟雾", "enabled": True},
            ],
            "conf_thresh": 0.25,
            "json_overrides": {"base.json": {}, "nn.json": {}, "nn.extend.json": {}},
        }

        manifest_path = self.workspace / "manifest.json"
        manifest = Manifest.from_dict(manifest_payload, manifest_path, self.workspace)
        result = build_package_workflow(
            manifest,
            ReviewConfig(
                enabled=True,
                ollama_url=self.ollama_url,
                model="fake-gemma",
                timeout_seconds=10,
            ),
        )

        self.assertEqual(result["gemma_review"]["status"], "succeeded")
        self.assertIn("base.json", result["gemma_review"]["summary"])
        review_path = Path(result["report"]["output_dir"]) / "gemma-review.json"
        self.assertTrue(review_path.exists())


class WebServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="rknn-package-converter-web-tests-")
        self.workspace = Path(self.temp_dir.name)
        self.server = create_server(
            WebConfig(
                host="127.0.0.1",
                port=find_free_port(),
                workdir=self.workspace / "runtime",
                default_review_enabled=False,
            )
        )
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)
        self.temp_dir.cleanup()

    def test_health_endpoint(self) -> None:
        with urllib.request.urlopen(self.base_url + "/health", timeout=10) as response:
            payload = json.loads(response.read().decode("utf-8"))
        self.assertEqual(payload["status"], "ok")
        self.assertEqual(payload["service"], "rknn-package-converter")

    @property
    def base_url(self) -> str:
        return f"http://127.0.0.1:{self.server.server_address[1]}"


class FakeOllamaHandler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        request_json = json.loads(body.decode("utf-8"))
        response = {
            "model": request_json["model"],
            "response": "- base.json 字段齐全\n- nn.json 结构有效\n- nn.extend.json 包含 class 和 conf_thresh\n- m31 nn_server 未发现异常",
        }
        payload = json.dumps(response, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: object) -> None:
        return


def find_free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def start_server(handler: type[BaseHTTPRequestHandler]) -> tuple[ThreadingHTTPServer, Thread]:
    server = ThreadingHTTPServer(("127.0.0.1", find_free_port()), handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread


if __name__ == "__main__":
    unittest.main()
