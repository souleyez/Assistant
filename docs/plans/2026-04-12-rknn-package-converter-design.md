# RKNN Algorithm Package Converter Design

## Goal

Build a standalone converter for trained algorithm outputs. It must not depend on the current `Gemma 4 YOLO Studio` backend, frontend, runtime storage, or deployment flow.

The tool reads:

- a vendor algorithm-package template directory or zip
- a trained RKNN model file
- algorithm metadata such as `geid`, engine name, classes, framework version

The tool writes:

- a patched algorithm package directory
- an optional zip archive for delivery
- a conversion report for traceability

## Source Requirements Captured From The Tencent Doc

The document title is `Docker转换RKNN文件`.

The usable requirements extracted from the document are:

- The runtime conversion chain is `pt -> onnx -> rknn`.
- Delivery to devices is not only the `rknn` file. It requires a dedicated algorithm package.
- The package is based on an existing vendor template such as `m1` or `m31`, not an arbitrary folder layout invented by us.
- Common files to patch are:
  - `base.json`
  - `nn.json`
  - `nn.extend.json`
- `m31` may also require `nn_server`.
- Required patched fields called out by the document:
  - `base.json`: `geid`, `name`
  - `nn.json`: `model_path`, `cls_num`, `cls_name`, `cls_enable`, `type`
  - `nn.extend.json`: class exposure mapping and confidence threshold related fields
- Framework type mapping is:
  - YOLOv5 -> `type = 6`
  - YOLOv8 -> `type = 8`
- `geid` is important and should remain stable once assigned.

## Chosen Approach

Use a standalone Python CLI with a manifest file.

Why this approach:

- no coupling to the existing platform
- works on build machines and developer laptops
- can patch real vendor templates instead of guessing final package layout
- easy to automate later from CI or from the training platform, without embedding the logic there

## Tool Contract

The converter will require a manifest JSON file containing:

- template source path
- output directory and optional output zip path
- package variant such as `m1` or `m31`
- model source path and destination path inside the package
- engine metadata: `name`, `geid`
- framework: `yolov5` or `yolov8`
- classes: id, name, optional display name, enabled flag
- optional `nn_server` source path
- optional JSON pointer overrides for template-specific patches

## Patching Rules

The converter will:

1. unpack or copy the template into a staging directory
2. locate `base.json`, `nn.json`, `nn.extend.json`
3. patch standard fields using built-in rules
4. apply optional JSON pointer overrides from the manifest
5. copy the RKNN file into the configured package path
6. optionally replace `nn_server`
7. emit a delivery-ready package directory and zip
8. emit a `conversion-report.json`

## Non-Goals

- No direct `pt -> onnx -> rknn` conversion in this tool
- No dependency on the current training platform APIs
- No device deployment or SSH upload in this tool
- No attempt to infer hidden vendor file semantics beyond the fields explicitly surfaced by the document

## Validation

Ship the tool with:

- a sample manifest
- a sample template fixture
- unit tests that verify:
  - field patching
  - RKNN model placement
  - zip creation
  - optional `nn_server` replacement
