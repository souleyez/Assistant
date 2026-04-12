from __future__ import annotations

from dataclasses import asdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple
import json
import os
import shutil
import tempfile
import zipfile

from .models import Manifest


FRAMEWORK_TYPE = {
    "yolov5": 6,
    "v5": 6,
    "yolov8": 8,
    "v8": 8,
}


def build_package(manifest: Manifest) -> Dict[str, Any]:
    manifest.validate()

    with tempfile.TemporaryDirectory(prefix="rknn-package-converter-") as tmp_dir:
        staging_root = Path(tmp_dir) / manifest.package_name
        _materialize_template(manifest.template_path, staging_root)

        base_path = _find_unique_file(staging_root, "base.json")
        nn_path = _find_unique_file(staging_root, "nn.json")
        nn_extend_path = _find_unique_file(staging_root, "nn.extend.json")

        base_json = _read_json(base_path)
        nn_json = _read_json(nn_path)
        nn_extend_json = _read_json(nn_extend_path)

        report = {
            "package_name": manifest.package_name,
            "variant": manifest.variant,
            "files": {
                "base.json": str(base_path.relative_to(staging_root)),
                "nn.json": str(nn_path.relative_to(staging_root)),
                "nn.extend.json": str(nn_extend_path.relative_to(staging_root)),
            },
            "patches": [],
            "warnings": [],
        }

        _patch_base_json(base_json, manifest, report)
        _patch_nn_json(nn_json, manifest, report)
        _patch_nn_extend_json(nn_extend_json, manifest, report)

        _apply_json_overrides(base_json, manifest.json_overrides.get("base.json", {}), report, "base.json")
        _apply_json_overrides(nn_json, manifest.json_overrides.get("nn.json", {}), report, "nn.json")
        _apply_json_overrides(nn_extend_json, manifest.json_overrides.get("nn.extend.json", {}), report, "nn.extend.json")

        _write_json(base_path, base_json)
        _write_json(nn_path, nn_json)
        _write_json(nn_extend_path, nn_extend_json)

        model_target = staging_root / manifest.model.package_path
        model_target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(manifest.model.source_path, model_target)
        report["model_target"] = str(model_target.relative_to(staging_root))

        if manifest.nn_server_source_path:
            nn_server_path = _find_unique_file(staging_root, "nn_server", required=False)
            if nn_server_path is None:
                nn_server_path = staging_root / "nn_server"
            shutil.copy2(manifest.nn_server_source_path, nn_server_path)
            os.chmod(nn_server_path, 0o755)
            report["nn_server_target"] = str(nn_server_path.relative_to(staging_root))
        elif manifest.variant == "m31":
            report["warnings"].append(
                "Variant m31 usually requires nn_server, but nn_server_source_path was not provided."
            )

        conversion_meta = {
            "engine": asdict(manifest.engine),
            "model": {
                "source_path": str(manifest.model.source_path),
                "package_path": manifest.model.package_path,
                "framework": manifest.model.framework,
                "framework_type": FRAMEWORK_TYPE[manifest.model.framework.lower()],
            },
            "classes": [asdict(item) for item in manifest.classes],
            "conf_thresh": manifest.conf_thresh,
            "variant": manifest.variant,
        }

        _write_json(staging_root / "conversion-report.json", report)
        _write_json(staging_root / "package.meta.json", conversion_meta)

        if manifest.output_dir.exists():
            shutil.rmtree(manifest.output_dir)
        manifest.output_dir.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(staging_root, manifest.output_dir)

        if manifest.output_archive:
            manifest.output_archive.parent.mkdir(parents=True, exist_ok=True)
            _zip_directory(manifest.output_dir, manifest.output_archive)

        report["output_dir"] = str(manifest.output_dir)
        report["output_archive"] = str(manifest.output_archive) if manifest.output_archive else None
        return report


def _materialize_template(template_path: Path, staging_root: Path) -> None:
    if template_path.is_dir():
        shutil.copytree(template_path, staging_root)
        return

    if template_path.suffix.lower() == ".zip":
        staging_root.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(template_path) as zip_file:
            zip_file.extractall(staging_root)
        return

    raise ValueError(f"Unsupported template input: {template_path}")


def _find_unique_file(root: Path, file_name: str, required: bool = True) -> Path | None:
    matches = list(root.rglob(file_name))
    if not matches:
        if required:
            raise FileNotFoundError(f"Required file not found in template: {file_name}")
        return None
    if len(matches) > 1:
        raise ValueError(f"Multiple {file_name} files found in template: {matches}")
    return matches[0]


def _read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, data: Dict[str, Any]) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _patch_base_json(data: Dict[str, Any], manifest: Manifest, report: Dict[str, Any]) -> None:
    _set_first_scalar_key(data, "geid", manifest.engine.geid, report, "base.json")
    _set_first_scalar_key(data, "name", manifest.engine.name, report, "base.json")


def _patch_nn_json(data: Dict[str, Any], manifest: Manifest, report: Dict[str, Any]) -> None:
    class_names = [item.effective_name for item in manifest.classes]
    enabled_ids = [item.id for item in manifest.classes if item.enabled]
    framework_type = FRAMEWORK_TYPE[manifest.model.framework.lower()]

    _set_first_scalar_key(data, "model_path", manifest.model.package_path, report, "nn.json")
    _set_first_scalar_key(data, "cls_num", len(manifest.classes), report, "nn.json")
    _set_first_scalar_key(data, "type", framework_type, report, "nn.json")
    _set_first_collection_key(data, "cls_name", class_names, report, "nn.json")
    _set_first_collection_key(data, "cls_enable", enabled_ids, report, "nn.json")


def _patch_nn_extend_json(data: Dict[str, Any], manifest: Manifest, report: Dict[str, Any]) -> None:
    if manifest.conf_thresh is not None:
        _set_first_scalar_key(data, "conf_thresh", manifest.conf_thresh, report, "nn.extend.json")

    class_entries = [
        {"classes": item.id, "value": item.effective_name}
        for item in manifest.classes
        if item.enabled
    ]
    _set_first_collection_key(data, "class", class_entries, report, "nn.extend.json")


def _set_first_scalar_key(
    node: Any,
    key: str,
    value: Any,
    report: Dict[str, Any],
    file_name: str,
) -> bool:
    if isinstance(node, dict):
        if key in node and not isinstance(node[key], (dict, list)):
            node[key] = value
            report["patches"].append({"file": file_name, "key": key, "value": value})
            return True
        for child in node.values():
            if _set_first_scalar_key(child, key, value, report, file_name):
                return True
    elif isinstance(node, list):
        for child in node:
            if _set_first_scalar_key(child, key, value, report, file_name):
                return True
    return False


def _set_first_collection_key(
    node: Any,
    key: str,
    value: Any,
    report: Dict[str, Any],
    file_name: str,
) -> bool:
    if isinstance(node, dict):
        if key in node:
            node[key] = value
            report["patches"].append({"file": file_name, "key": key, "value": value})
            return True
        for child in node.values():
            if _set_first_collection_key(child, key, value, report, file_name):
                return True
    elif isinstance(node, list):
        for child in node:
            if _set_first_collection_key(child, key, value, report, file_name):
                return True
    return False


def _apply_json_overrides(
    document: Dict[str, Any],
    overrides: Dict[str, Any],
    report: Dict[str, Any],
    file_name: str,
) -> None:
    for pointer, value in overrides.items():
        _set_json_pointer(document, pointer, value)
        report["patches"].append({"file": file_name, "pointer": pointer, "value": value})


def _set_json_pointer(document: Dict[str, Any], pointer: str, value: Any) -> None:
    if not pointer.startswith("/"):
        raise ValueError(f"JSON pointer must start with '/': {pointer}")

    parts = [part.replace("~1", "/").replace("~0", "~") for part in pointer.strip("/").split("/")]
    current: Any = document
    for part in parts[:-1]:
        if isinstance(current, list):
            current = current[int(part)]
        else:
            current = current[part]

    last = parts[-1]
    if isinstance(current, list):
        current[int(last)] = value
    else:
        current[last] = value


def _zip_directory(source_dir: Path, archive_path: Path) -> None:
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as zip_file:
        for file_path in source_dir.rglob("*"):
            if file_path.is_file():
                zip_file.write(file_path, arcname=file_path.relative_to(source_dir))
