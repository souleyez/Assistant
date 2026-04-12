#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ensure_runtime_dirs

current_cron="$(crontab -l 2>/dev/null || true)"
entry="@reboot cd $APP_ROOT && bash $TOOL_ROOT/deploy/linux/start.sh >>$CRON_LOG 2>&1"

if grep -Fq "$entry" <<<"$current_cron"; then
  echo "Cron entry already installed."
  exit 0
fi

{
  printf "%s\n" "$current_cron"
  printf "%s\n" "$entry"
} | crontab -

echo "Installed crontab @reboot entry."
