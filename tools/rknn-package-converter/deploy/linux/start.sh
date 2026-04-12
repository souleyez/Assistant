#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ensure_runtime_dirs

if [[ "$ENABLE_OLLAMA" == "1" && -x "$OLLAMA_BIN" ]]; then
  if is_http_reachable "$OLLAMA_URL/api/tags"; then
    rm -f "$OLLAMA_PID_FILE"
  elif ! is_pid_running "$OLLAMA_PID_FILE"; then
    (
      cd "$APP_ROOT"
      nohup env OLLAMA_HOST="$OLLAMA_HOST" OLLAMA_MODELS="$OLLAMA_MODELS" "$OLLAMA_BIN" serve >>"$OLLAMA_LOG" 2>&1 &
      echo $! >"$OLLAMA_PID_FILE"
    )
  fi
fi

if is_pid_running "$WEB_PID_FILE"; then
  echo "Converter web service already running with pid $(cat "$WEB_PID_FILE")."
  exit 0
fi

review_flag=()
if [[ "$ENABLE_REVIEW" == "1" ]]; then
  review_flag+=(--enable-review)
fi

(
  cd "$APP_ROOT"
  nohup env PYTHONPATH="$PYTHONPATH_VALUE" "$PYTHON_CMD" -m rknn_package_converter serve \
    --host "$WEB_HOST" \
    --port "$WEB_PORT" \
    --workdir "$WORK_DIR" \
    "${review_flag[@]}" \
    --ollama-url "$OLLAMA_URL" \
    --ollama-model "$OLLAMA_MODEL" \
    --review-timeout "$REVIEW_TIMEOUT" >>"$WEB_LOG" 2>&1 &
  echo $! >"$WEB_PID_FILE"
)

if command -v curl >/dev/null 2>&1; then
  for _ in $(seq 1 20); do
    if curl -fsS "http://127.0.0.1:$WEB_PORT/health" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
fi

echo "Started RKNN converter web service on port $WEB_PORT"
