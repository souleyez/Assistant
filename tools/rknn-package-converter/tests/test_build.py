from __future__ import annotations

from pathlib import Path
import json
import os
import shutil
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from rknn_package_converter.models import Manifest  # noqa: E402
from rknn_package_converter.packager import build_package  # noqa: E402


class BuildPackageTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="rknn-package-converter-tests-")
        self.workspace = Path(self.temp_dir.name)
        self.fixtures = ROOT / "tests" / "fixtures"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_build_m1_package_directory_and_zip(self) -> None:
        manifest_path = self.workspace / "manifest.json"
        manifest_path.write_text(json.dumps({
            "template_path": str(self.fixtures / "template_m1"),
            "output_dir": str(self.workspace / "out" / "fire_smoke_m1"),
            "output_archive": str(self.workspace / "out" / "fire_smoke_m1.zip"),
            "package_name": "fire_smoke_m1",
            "variant": "m1",
            "engine": {"name": "fire_smoke", "geid": 1001},
            "model": {
                "source_path": str(self.fixtures / "artifacts" / "fire_smoke.rknn"),
                "package_path": "models/fire_smoke.rknn",
                "framework": "yolov8"
            },
            "classes": [
                {"id": 0, "name": "fire", "display_name": "火焰", "enabled": True},
                {"id": 1, "name": "smoke", "display_name": "烟雾", "enabled": True}
            ],
            "conf_thresh": 0.25,
            "json_overrides": {"base.json": {}, "nn.json": {}, "nn.extend.json": {}}
        }, ensure_ascii=False, indent=2), encoding="utf-8")

        manifest = Manifest.from_file(manifest_path)
        report = build_package(manifest)

        output_dir = Path(report["output_dir"])
        self.assertTrue(output_dir.exists())
        self.assertTrue((output_dir / "models" / "fire_smoke.rknn").exists())

        base_json = json.loads((output_dir / "base.json").read_text(encoding="utf-8"))
        nn_json = json.loads((output_dir / "nn.json").read_text(encoding="utf-8"))
        extend_json = json.loads((output_dir / "nn.extend.json").read_text(encoding="utf-8"))

        self.assertEqual(base_json["geid"], 1001)
        self.assertEqual(base_json["name"], "fire_smoke")
        self.assertEqual(nn_json["model_path"], "models/fire_smoke.rknn")
        self.assertEqual(nn_json["cls_num"], 2)
        self.assertEqual(nn_json["cls_name"], ["火焰", "烟雾"])
        self.assertEqual(nn_json["cls_enable"], [0, 1])
        self.assertEqual(nn_json["type"], 8)
        self.assertEqual(extend_json["conf_thresh"], 0.25)
        self.assertEqual(
            extend_json["class"],
            [{"classes": 0, "value": "火焰"}, {"classes": 1, "value": "烟雾"}],
        )

        archive_path = Path(report["output_archive"])
        self.assertTrue(archive_path.exists())
        with zipfile.ZipFile(archive_path) as zip_file:
            self.assertIn("base.json", zip_file.namelist())
            self.assertIn("models/fire_smoke.rknn", zip_file.namelist())

    def test_build_m31_package_with_nn_server_replacement(self) -> None:
        manifest_path = self.workspace / "manifest_m31.json"
        manifest_path.write_text(json.dumps({
            "template_path": str(self.fixtures / "template_m31"),
            "output_dir": str(self.workspace / "out" / "legacy_m31"),
            "package_name": "legacy_m31",
            "variant": "m31",
            "engine": {"name": "legacy_fire", "geid": 2002},
            "model": {
                "source_path": str(self.fixtures / "artifacts" / "fire_smoke.rknn"),
                "package_path": "models/legacy_fire.rknn",
                "framework": "yolov5"
            },
            "classes": [
                {"id": 0, "name": "fire", "enabled": True},
                {"id": 1, "name": "smoke", "enabled": False}
            ],
            "conf_thresh": 0.4,
            "nn_server_source_path": str(self.fixtures / "artifacts" / "nn_server"),
            "json_overrides": {"base.json": {}, "nn.json": {}, "nn.extend.json": {}}
        }, ensure_ascii=False, indent=2), encoding="utf-8")

        manifest = Manifest.from_file(manifest_path)
        report = build_package(manifest)
        output_dir = Path(report["output_dir"])

        base_json = json.loads((output_dir / "base.json").read_text(encoding="utf-8"))
        nn_json = json.loads((output_dir / "nn.json").read_text(encoding="utf-8"))
        extend_json = json.loads((output_dir / "nn.extend.json").read_text(encoding="utf-8"))
        nn_server_path = output_dir / "nn_server"

        self.assertEqual(base_json["meta"]["geid"], 2002)
        self.assertEqual(base_json["meta"]["name"], "legacy_fire")
        self.assertEqual(nn_json["nn"]["type"], 6)
        self.assertEqual(nn_json["nn"]["cls_enable"], [0])
        self.assertEqual(extend_json["extend"]["class"], [{"classes": 0, "value": "fire"}])
        self.assertTrue(nn_server_path.exists())
        self.assertTrue(os.access(nn_server_path, os.X_OK))


if __name__ == "__main__":
    unittest.main()
