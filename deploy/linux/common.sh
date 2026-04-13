#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$APP_ROOT/backend"
FRONTEND_DIR="$APP_ROOT/frontend"

RUNTIME_DIR="$APP_ROOT/.runtime"
PID_DIR="$RUNTIME_DIR/pids"
LOG_DIR="$APP_ROOT/logs"

BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"

BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
CRON_LOG="$LOG_DIR/service-cron.log"

BACKEND_JAR="${BACKEND_JAR:-$BACKEND_DIR/target/assistant-backend-0.1.0.jar}"
VENV_DIR="${VENV_DIR:-$APP_ROOT/.venv}"
YOLO_PYTHON_CMD="${YOLO_PYTHON:-$VENV_DIR/bin/python}"
GEMMA_OLLAMA_URL="${ASSISTANT_GEMMA_OLLAMA_URL:-http://127.0.0.1:11435}"
GEMMA_MODEL="${ASSISTANT_GEMMA_MODEL:-gemma4:26b}"
GEMMA_FALLBACK_MODEL="${ASSISTANT_GEMMA_FALLBACK_MODEL:-gemma4:e4b}"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4173}"

JAVA_CMD="${JAVA_CMD:-java}"
PYTHON_CMD="${PYTHON_CMD:-python3}"

ensure_runtime_dirs() {
  mkdir -p "$PID_DIR" "$LOG_DIR"
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

resolve_maven() {
  if [[ -n "${MVN_CMD:-}" ]]; then
    echo "$MVN_CMD"
    return 0
  fi

  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return 0
  fi

  if [[ -x "$HOME/tools/apache-maven-3.9.9/bin/mvn" ]]; then
    echo "$HOME/tools/apache-maven-3.9.9/bin/mvn"
    return 0
  fi

  echo "Maven 3.9+ not found. Set MVN_CMD or install Maven." >&2
  return 1
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
