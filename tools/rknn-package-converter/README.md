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

## Why Template-Based

The Tencent document makes it clear that delivery uses vendor package templates, not just raw RKNN files. This tool therefore patches a real template instead of inventing a synthetic package structure.
