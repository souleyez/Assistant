#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
APP_ROOT="$(cd "$TOOL_ROOT/../.." && pwd)"

RUNTIME_DIR="${RKNN_CONVERTER_RUNTIME_DIR:-$TOOL_ROOT/runtime}"
PID_DIR="$RUNTIME_DIR/pids"
LOG_DIR="${RKNN_CONVERTER_LOG_DIR:-$TOOL_ROOT/logs}"
WORK_DIR="${RKNN_CONVERTER_WORK_DIR:-$RUNTIME_DIR/work}"

WEB_PID_FILE="$PID_DIR/web.pid"
OLLAMA_PID_FILE="$PID_DIR/ollama.pid"

WEB_LOG="$LOG_DIR/web.log"
OLLAMA_LOG="$LOG_DIR/ollama.log"
CRON_LOG="$LOG_DIR/cron.log"

WEB_HOST="${RKNN_CONVERTER_HOST:-0.0.0.0}"
WEB_PORT="${RKNN_CONVERTER_PORT:-4174}"
ENABLE_REVIEW="${RKNN_CONVERTER_ENABLE_REVIEW:-1}"
OLLAMA_URL="${RKNN_CONVERTER_OLLAMA_URL:-http://127.0.0.1:11435}"
OLLAMA_MODEL="${RKNN_CONVERTER_OLLAMA_MODEL:-gemma4:31b-tuned}"
REVIEW_TIMEOUT="${RKNN_CONVERTER_REVIEW_TIMEOUT:-240}"

ENABLE_OLLAMA="${RKNN_CONVERTER_ENABLE_OLLAMA:-1}"
OLLAMA_BIN="${RKNN_CONVERTER_OLLAMA_BIN:-$HOME/ollama/bin/ollama}"
OLLAMA_HOST="${RKNN_CONVERTER_OLLAMA_HOST:-127.0.0.1:11435}"
OLLAMA_MODELS="${RKNN_CONVERTER_OLLAMA_MODELS:-$HOME/.ollama-host/models}"

PYTHON_CMD="${PYTHON_CMD:-python3}"
PYTHONPATH_VALUE="${RKNN_CONVERTER_PYTHONPATH:-$TOOL_ROOT/src}"

ensure_runtime_dirs() {
  mkdir -p "$PID_DIR" "$LOG_DIR" "$WORK_DIR"
}

is_pid_running() {
  local pid_file="$1"
  if [[ ! -f "$pid_file" ]]; then
    return 1
  fi

  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -z "$pid" ]]; then
    return 1
  fi

  kill -0 "$pid" 2>/dev/null
}

stop_pid_file() {
  local pid_file="$1"
  if ! is_pid_running "$pid_file"; then
    rm -f "$pid_file"
    return 0
  fi

  local pid
  pid="$(cat "$pid_file")"
  kill "$pid" 2>/dev/null || true

  for _ in $(seq 1 20); do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      return 0
    fi
    sleep 1
  done

  kill -9 "$pid" 2>/dev/null || true
  rm -f "$pid_file"
}

print_status_line() {
  local name="$1"
  local pid_file="$2"
  if is_pid_running "$pid_file"; then
    printf "%s: running (pid %s)\n" "$name" "$(cat "$pid_file")"
  else
    printf "%s: stopped\n" "$name"
  fi
}
