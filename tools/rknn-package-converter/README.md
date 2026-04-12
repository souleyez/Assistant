# RKNN Package Converter

This tool is intentionally standalone.

It does not call the current training platform backend or frontend. It only converts trained RKNN algorithm outputs into vendor algorithm packages based on an existing template bundle such as `m1` or `m31`.

## Install

```powershell
cd C:\Users\soulzyn\Desktop\codex\Assistant
pip install -e .\tools\rknn-package-converter
```

## Build A Package

1. Copy `manifest.example.json` and edit it.
2. Run:

```powershell
python -m rknn_package_converter build .\tools\rknn-package-converter\manifest.example.json
```

If you want Gemma 4 to review the generated package structure through local Ollama:

```powershell
python -m rknn_package_converter build .\tools\rknn-package-converter\manifest.example.json --review --ollama-url http://127.0.0.1:11435 --ollama-model gemma4:31b-tuned
```

The build command still writes the package even if the review call fails. In that case `gemma-review.json` records the error.

## Start The Standalone Web Form

```powershell
python -m rknn_package_converter serve --host 0.0.0.0 --port 4174 --workdir .\tools\rknn-package-converter\runtime --enable-review --ollama-url http://127.0.0.1:11435 --ollama-model gemma4:31b-tuned
```

Then open `http://127.0.0.1:4174`.

The web form accepts:

- template zip
- `.rknn` model
- optional `nn_server`
- engine metadata
- class metadata JSON
- optional JSON pointer overrides

The generated package zip and review files are stored under the web runtime directory.

## Inputs

- vendor template directory or zip
- trained `.rknn` model file
- engine metadata such as `geid` and engine name
- class metadata
- optional `nn_server` replacement
- optional JSON pointer overrides for template-specific fields

## Outputs

- patched package directory
- optional zip archive
- `conversion-report.json`
- optional `gemma-review.json`

## Why Template-Based

The Tencent document makes it clear that delivery uses vendor package templates, not just raw RKNN files. This tool therefore patches a real template instead of inventing a synthetic package structure.

## Linux Deployment

This tool has its own deploy scripts and can run independently from the main training platform:

```bash
cd /home/xigma01/apps/Assistant
bash tools/rknn-package-converter/deploy/linux/setup-server.sh
```

Runtime defaults:

- converter service: `4174`
- optional user-mode Ollama: `11435`
- logs: `tools/rknn-package-converter/logs`
