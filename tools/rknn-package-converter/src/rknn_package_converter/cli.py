from __future__ import annotations

from pathlib import Path
import argparse
import json

from .gemma_review import ReviewConfig
from .models import Manifest
from .webapp import WebConfig, serve
from .workflow import build_package_workflow


def build_command(args: argparse.Namespace) -> int:
    manifest = Manifest.from_file(Path(args.manifest))
    review_config = ReviewConfig(
        enabled=args.review,
        ollama_url=args.ollama_url,
        model=args.ollama_model,
        timeout_seconds=args.review_timeout,
    )
    result = build_package_workflow(manifest, review_config)
    output = dict(result["report"])
    if "gemma_review" in result:
        output["gemma_review"] = result["gemma_review"]
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


def serve_command(args: argparse.Namespace) -> int:
    config = WebConfig(
        host=args.host,
        port=args.port,
        workdir=Path(args.workdir).resolve(),
        default_review_enabled=args.enable_review,
        ollama_url=args.ollama_url,
        ollama_model=args.ollama_model,
        review_timeout_seconds=args.review_timeout,
    )
    serve(config)
    return 0


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="rknn-package-converter",
        description="Standalone converter for patching RKNN vendor algorithm packages.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Build a package from a manifest file.")
    build_parser.add_argument("manifest", help="Path to the manifest JSON file.")
    build_parser.add_argument("--review", action="store_true", help="Run Gemma review via local Ollama.")
    build_parser.add_argument(
        "--ollama-url",
        default="http://127.0.0.1:11434",
        help="Base URL for Ollama. Default: http://127.0.0.1:11434",
    )
    build_parser.add_argument(
        "--ollama-model",
        default="gemma4:31b-tuned",
        help="Ollama model name used for review.",
    )
    build_parser.add_argument(
        "--review-timeout",
        type=int,
        default=240,
        help="Review request timeout in seconds.",
    )
    build_parser.set_defaults(func=build_command)

    serve_parser = subparsers.add_parser("serve", help="Start the standalone web form service.")
    serve_parser.add_argument("--host", default="0.0.0.0", help="Bind host. Default: 0.0.0.0")
    serve_parser.add_argument("--port", type=int, default=4174, help="Bind port. Default: 4174")
    serve_parser.add_argument(
        "--workdir",
        default=str((Path(__file__).resolve().parents[2] / "runtime")),
        help="Runtime workspace for uploaded files and generated packages.",
    )
    serve_parser.add_argument(
        "--enable-review",
        action="store_true",
        help="Enable Gemma review checkbox by default.",
    )
    serve_parser.add_argument(
        "--ollama-url",
        default="http://127.0.0.1:11434",
        help="Base URL for Ollama. Default: http://127.0.0.1:11434",
    )
    serve_parser.add_argument(
        "--ollama-model",
        default="gemma4:31b-tuned",
        help="Default Ollama model name for review.",
    )
    serve_parser.add_argument(
        "--review-timeout",
        type=int,
        default=240,
        help="Review request timeout in seconds.",
    )
    serve_parser.set_defaults(func=serve_command)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = create_parser()
    args = parser.parse_args(argv)
    return args.func(args)
