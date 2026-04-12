#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ensure_runtime_dirs

if is_pid_running "$FRONTEND_PID_FILE"; then
  echo "frontend already running with pid $(cat "$FRONTEND_PID_FILE")"
  exit 0
fi

if [[ ! -d "$FRONTEND_DIR/dist" ]]; then
  echo "Frontend dist not found: $FRONTEND_DIR/dist" >&2
  exit 1
fi

cd "$FRONTEND_DIR"
nohup "$PYTHON_CMD" "$FRONTEND_DIR/spa_server.py" "$FRONTEND_PORT" >> "$FRONTEND_LOG" 2>&1 &
echo $! > "$FRONTEND_PID_FILE"
sleep 2

if ! is_pid_running "$FRONTEND_PID_FILE"; then
  echo "Frontend failed to start. Check $FRONTEND_LOG" >&2
  exit 1
fi

echo "frontend started with pid $(cat "$FRONTEND_PID_FILE")"
