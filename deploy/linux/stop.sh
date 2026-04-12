#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

stop_pid_file "$FRONTEND_PID_FILE"
stop_pid_file "$BACKEND_PID_FILE"

echo "services stopped"
