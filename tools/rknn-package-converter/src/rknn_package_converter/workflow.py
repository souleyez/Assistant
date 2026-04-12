from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, Optional

from .gemma_review import ReviewConfig, review_package
from .models import Manifest
from .packager import build_package


def build_package_workflow(
    manifest: Manifest,
    review_config: Optional[ReviewConfig] = None,
) -> Dict[str, Any]:
    report = build_package(manifest)
    result: Dict[str, Any] = {"report": report}

    effective_review = review_config or ReviewConfig(enabled=False)
    if effective_review.enabled:
        output_dir = Path(report["output_dir"])
        result["gemma_review"] = review_package(output_dir, report, effective_review)

    return result
