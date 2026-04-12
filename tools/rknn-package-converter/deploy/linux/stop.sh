#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

stop_pid_file "$WEB_PID_FILE"
stop_pid_file "$OLLAMA_PID_FILE"

echo "Stopped RKNN converter services."
