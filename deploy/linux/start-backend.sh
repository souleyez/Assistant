#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ensure_runtime_dirs

if is_pid_running "$BACKEND_PID_FILE"; then
  echo "backend already running with pid $(cat "$BACKEND_PID_FILE")"
  exit 0
fi

if [[ ! -f "$BACKEND_JAR" ]]; then
  echo "Backend jar not found: $BACKEND_JAR" >&2
  exit 1
fi

cd "$BACKEND_DIR"
nohup env YOLO_PYTHON="$YOLO_PYTHON_CMD" "$JAVA_CMD" -jar "$BACKEND_JAR" >> "$BACKEND_LOG" 2>&1 &
echo $! > "$BACKEND_PID_FILE"
sleep 2

if ! is_pid_running "$BACKEND_PID_FILE"; then
  echo "Backend failed to start. Check $BACKEND_LOG" >&2
  exit 1
fi

echo "backend started with pid $(cat "$BACKEND_PID_FILE")"
