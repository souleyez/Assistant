#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ensure_runtime_dirs

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  "$PYTHON_CMD" -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/pip" install -r "$APP_ROOT/requirements-train.txt"

cd "$FRONTEND_DIR"
if [[ ! -d node_modules ]]; then
  npm install
fi
npm run build

cd "$BACKEND_DIR"
"$(resolve_maven)" -DskipTests package
