# YOLO Quick Start Design

## Goal

Add a minimal entry path for the single-machine Gemma 4 + YOLO platform:

- upload images or a zip
- describe the target in natural language
- let Gemma 4 infer classes and a baseline training plan
- auto-create the dataset and training project
- auto-start training only when complete YOLO labels are present

## Why

The existing platform starts from explicit dataset and project forms. That is workable for operators, but too heavy for the first-use path. The new quick-start flow reduces the front door to one upload action and one text description, while keeping the existing detailed pages for power users.

## Runtime Behavior

1. The frontend landing page becomes `一键开训`.
2. The backend accepts multipart uploads.
3. Uploads are materialized into a local quick-start workspace.
4. If the upload already contains `train/val` YOLO structure, it is used directly.
5. If the upload is flat images, the backend prepares a split dataset layout.
6. Gemma 4 is asked to produce:
   - class names
   - dataset name
   - project name
   - training objective
   - baseline hyper-parameters
   - a concise readiness summary
7. The backend creates the dataset and project automatically.
8. A training job is auto-started only when every image has a matching YOLO label file.

## Guardrails

- Pure images without labels are accepted, but they do not trigger fake training.
- The UI states clearly when the next step is labeling rather than training.
- Gemma 4 failures fall back to deterministic heuristics so the quick-start path still works.
- Training launch is now cross-platform, so Linux deployment is not blocked by the previous PowerShell-only launcher.
