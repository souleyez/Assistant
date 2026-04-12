from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional
import json


@dataclass
class ClassSpec:
    id: int
    name: str
    display_name: Optional[str] = None
    enabled: bool = True

    @property
    def effective_name(self) -> str:
        return self.display_name or self.name


@dataclass
class EngineSpec:
    name: str
    geid: int


@dataclass
class ModelSpec:
    source_path: Path
    package_path: str
    framework: str


@dataclass
class Manifest:
    manifest_path: Path
    template_path: Path
    output_dir: Path
    output_archive: Optional[Path]
    package_name: str
    variant: str
    engine: EngineSpec
    model: ModelSpec
    classes: List[ClassSpec]
    conf_thresh: Optional[float] = None
    nn_server_source_path: Optional[Path] = None
    json_overrides: Dict[str, Dict[str, Any]] = field(default_factory=dict)

    @classmethod
    def from_file(cls, manifest_path: Path) -> "Manifest":
        raw = json.loads(manifest_path.read_text(encoding="utf-8"))

        template_path = (manifest_path.parent / raw["template_path"]).resolve()
        output_dir = (manifest_path.parent / raw["output_dir"]).resolve()
        output_archive = raw.get("output_archive")
        output_archive_path = ((manifest_path.parent / output_archive).resolve()
                               if output_archive else None)

        nn_server_source_path = raw.get("nn_server_source_path")
        nn_server_path = ((manifest_path.parent / nn_server_source_path).resolve()
                          if nn_server_source_path else None)

        model = raw["model"]
        classes = [ClassSpec(**item) for item in raw["classes"]]

        return cls(
            manifest_path=manifest_path.resolve(),
            template_path=template_path,
            output_dir=output_dir,
            output_archive=output_archive_path,
            package_name=raw["package_name"],
            variant=raw["variant"],
            engine=EngineSpec(**raw["engine"]),
            model=ModelSpec(
                source_path=(manifest_path.parent / model["source_path"]).resolve(),
                package_path=model["package_path"],
                framework=model["framework"],
            ),
            classes=classes,
            conf_thresh=raw.get("conf_thresh"),
            nn_server_source_path=nn_server_path,
            json_overrides=raw.get("json_overrides", {}),
        )

    def validate(self) -> None:
        if not self.template_path.exists():
            raise FileNotFoundError(f"Template path not found: {self.template_path}")
        if not self.model.source_path.exists():
            raise FileNotFoundError(f"Model file not found: {self.model.source_path}")
        if self.nn_server_source_path and not self.nn_server_source_path.exists():
            raise FileNotFoundError(
                f"nn_server file not found: {self.nn_server_source_path}"
            )
        if self.variant not in {"m1", "m31"}:
            raise ValueError(f"Unsupported package variant: {self.variant}")
        if self.model.framework.lower() not in {"yolov5", "v5", "yolov8", "v8"}:
            raise ValueError(f"Unsupported framework: {self.model.framework}")
        if not self.classes:
            raise ValueError("At least one class must be configured")
