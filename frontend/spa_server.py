from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import os
import sys

ROOT = Path(__file__).resolve().parent / "dist"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 4173
HOST = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("SPA_HOST", "0.0.0.0")


class SpaHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def _rewrite_for_spa(self):
        request_path = self.path.split("?", 1)[0].split("#", 1)[0]
        candidate = ROOT / request_path.lstrip("/")
        if request_path == "/" or candidate.exists():
            return
        self.path = "/index.html"

    def do_GET(self):
        self._rewrite_for_spa()
        return super().do_GET()

    def do_HEAD(self):
        self._rewrite_for_spa()
        return super().do_HEAD()


if __name__ == "__main__":
    os.chdir(ROOT)
    server = ThreadingHTTPServer((HOST, PORT), SpaHandler)
    print(f"Serving {ROOT} on {HOST}:{PORT}", flush=True)
    server.serve_forever()
