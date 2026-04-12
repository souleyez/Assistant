#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

bash "$SCRIPT_DIR/build.sh"

pkill -f 'assistant-backend-0.1.0.jar' 2>/dev/null || true
pkill -f "$FRONTEND_DIR/spa_server.py $FRONTEND_PORT" 2>/dev/null || true
rm -f "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"

bash "$SCRIPT_DIR/start.sh"
bash "$SCRIPT_DIR/install-cron.sh"
bash "$SCRIPT_DIR/status.sh"
