#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sleep 15
bash "$SCRIPT_DIR/start.sh" >> "$SCRIPT_DIR/../../logs/service-cron.log" 2>&1
