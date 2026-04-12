#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

print_status_line "converter-web" "$WEB_PID_FILE"
if is_pid_running "$OLLAMA_PID_FILE"; then
  print_status_line "converter-ollama" "$OLLAMA_PID_FILE"
elif is_http_reachable "$OLLAMA_URL/api/tags"; then
  printf "%s: reachable (%s)\n" "converter-ollama" "$OLLAMA_URL"
else
  printf "%s: stopped\n" "converter-ollama"
fi

if command -v curl >/dev/null 2>&1; then
  echo
  echo "health:"
  curl -fsS "http://127.0.0.1:$WEB_PORT/health" || true
  echo
fi
