#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENTRY="@reboot /bin/bash -lc 'bash \"$SCRIPT_DIR/autostart.sh\"' # gemma4-yolo-studio"

current_cron="$(crontab -l 2>/dev/null || true)"
filtered_cron="$(printf "%s\n" "$current_cron" | grep -v 'gemma4-yolo-studio' || true)"

{
  printf "%s\n" "$filtered_cron"
  printf "%s\n" "$ENTRY"
} | sed '/^$/N;/^\n$/D' | crontab -

echo "Installed reboot hook for $APP_ROOT"
