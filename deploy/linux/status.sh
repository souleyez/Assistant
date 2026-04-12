#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

print_status_line "backend" "$BACKEND_PID_FILE"
print_status_line "frontend" "$FRONTEND_PID_FILE"

if command -v curl >/dev/null 2>&1; then
  curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/health" >/dev/null && echo "backend health: ok" || echo "backend health: unavailable"
  curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/" >/dev/null && echo "frontend http: ok" || echo "frontend http: unavailable"
fi
