#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

print_status_line "converter-web" "$WEB_PID_FILE"
print_status_line "converter-ollama" "$OLLAMA_PID_FILE"

if command -v curl >/dev/null 2>&1; then
  echo
  echo "health:"
  curl -fsS "http://127.0.0.1:$WEB_PORT/health" || true
  echo
fi
