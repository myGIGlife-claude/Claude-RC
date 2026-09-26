#!/usr/bin/env bash
# claude-autostart.sh — restore Claude Remote Control sessions after a reboot.
#
# Every session started by claude-setup.sh (menu or --api) is recorded in
# ~/.local/state/claude-launcher/sessions.list; stopping one removes it.
# Run this at boot, e.g. with a user crontab line:
#   @reboot sleep 20 && $HOME/bin/claude-autostart.sh >/dev/null 2>&1

set -uo pipefail

STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/claude-launcher"
REGISTRY_FILE="$STATE_DIR/sessions.list"
SETUP="$(dirname "$(readlink -f "$0")")/claude-setup.sh"
[[ -x "$SETUP" ]] || SETUP="$HOME/bin/claude-setup.sh"
[[ -x "$SETUP" ]] || SETUP="$HOME/claude-setup.sh"

[[ -s "$REGISTRY_FILE" ]] || exit 0

while IFS= read -r project; do
  [[ "$project" =~ ^[A-Za-z0-9._-]{1,100}$ ]] || continue
  result="$("$SETUP" --api start "$project" </dev/null 2>/dev/null)"
  echo "$project: $result"
done <"$REGISTRY_FILE"
