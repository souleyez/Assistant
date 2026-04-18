# Quick Start Auto-Label Design

## Goal

Turn the current "upload images + describe target" flow into a guarded auto-label pipeline:

1. Gemma 4 parses the user description into training classes and English detection prompts.
2. The backend runs YOLOWorld against unlabeled images and writes YOLO `.txt` labels.
3. If every image is covered, Quick Start can auto-start training.
4. If coverage is partial, the session is kept in review state with explicit recovery guidance.

This is intentionally not a fully unsupervised labeling system. The design optimizes for fast first-pass labeling while keeping a manual review gate in front of training whenever confidence or coverage is incomplete.

## Architecture

- `GemmaRuntimeService`
  - Keep class planning.
  - Extend the quick-start plan schema with `detectionPrompts`, intended for open-vocabulary detection.

- `AutoLabelService`
  - New backend service.
  - Writes a manifest plus a short Python runner.
  - Uses `ultralytics.YOLOWorld` to generate YOLO labels for images that are still missing `.txt` files.
  - Produces a JSON report with created-label count, remaining unlabeled images, selected model, and coverage.

- `QuickStartService`
  - Runs auto-labeling after dataset ingestion and Gemma planning.
  - Re-scans the dataset after auto-labeling.
  - Promotes the session to `ready` only when every image has a non-empty YOLO label file.
  - Otherwise records an `auto-label-*` state and keeps the workflow in review.

- `QuickStartPage`
  - Shows the auto-label runtime model and threshold.
  - Shows auto-label status, generated count, remaining count, and next action in the latest result and recent history.

## Guardrails

- Auto-label only fills images that are still missing labels.
- Existing human labels are preserved.
- Empty detections do not count as "ready for training".
- The platform still supports direct auto-train when a full labeled dataset is uploaded.

## Validation

- Backend must compile on JDK 8 / Spring Boot 2.7.
- Frontend must still build under the current Vite setup.
- Runtime fallback remains safe: if YOLOWorld fails, Quick Start still creates the dataset and project and reports the failure instead of blocking the session.
