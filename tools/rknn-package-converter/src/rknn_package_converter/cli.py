from __future__ import annotations

from pathlib import Path
import argparse
import json

from .models import Manifest
from .packager import build_package


def build_command(args: argparse.Namespace) -> int:
    manifest = Manifest.from_file(Path(args.manifest))
    report = build_package(manifest)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="rknn-package-converter",
        description="Standalone converter for patching RKNN vendor algorithm packages.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Build a package from a manifest file.")
    build_parser.add_argument("manifest", help="Path to the manifest JSON file.")
    build_parser.set_defaults(func=build_command)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = create_parser()
    args = parser.parse_args(argv)
    return args.func(args)
