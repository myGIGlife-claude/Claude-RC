#!/usr/bin/env bash
# claude-autostart.sh — bring your Claude Code sessions back after a reboot.
#
# It keeps a list of the Claude tmux sessions you have running (checked every
# 2 minutes and again at shutdown), and on boot restarts each one detached,
# with Remote Control on, named after its project.
#
#   ./claude-autostart.sh install     set it up (run once, as yourself — not sudo)
#   claude-autostart status           show saved sessions and what's running
#   claude-autostart save             save the current list right now
#   claude-autostart restore          start any saved sessions that aren't running
#   claude-autostart uninstall        remove the boot/timer setup

set -euo pipefail

STATE_DIR="$HOME/.config/claude-setup"
LIST="$STATE_DIR/sessions.tsv"          # session<TAB>folder
BIN="$HOME/.local/bin/claude-autostart"
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/bin:/bin:$PATH"

log() { echo "[claude-autostart] $*"; }

# ----------------------------------------------------------------------
save() {
  mkdir -p "$STATE_DIR"
  # No tmux server running (e.g. just booted) → keep the last saved list
  if ! tmux ls >/dev/null 2>&1; then
    log "tmux not running — keeping saved list"
    return 0
  fi
  local tmp
  tmp="$(mktemp)"
  # (tmux turns tabs into "_", so fields are split on a marker instead)
  tmux list-panes -a -F '#{session_name}@@#{pane_current_path}@@#{pane_start_command}@@#{pane_current_command}' |
    awk -F'@@' '($3 ~ /claude/ || $4 == "claude") && !seen[$1]++ { print $1 "\t" $2 }' >"$tmp"
  mv "$tmp" "$LIST"
  log "saved $(wc -l <"$LIST") session(s)"
}

# ----------------------------------------------------------------------
restore() {
  if [[ ! -s "$LIST" ]]; then
    log "no saved sessions"
    return 0
  fi

  # Wait (up to ~2 min) for the internet so Remote Control can connect
  for _ in $(seq 1 24); do
    curl -s -o /dev/null --max-time 5 https://claude.ai && break
    sleep 5
  done

  local sess dir name sid cmd
  while IFS=$'\t' read -r sess dir; do
    [[ -z "$sess" ]] && continue
    if [[ ! -d "$dir" ]]; then
      log "$sess: folder $dir is gone — skipping"
      continue
    fi
    if tmux has-session -t "=$sess" 2>/dev/null; then
      log "$sess: already running"
      continue
    fi
    name="$(basename "$dir")"
    # Pick up the project's last conversation (the newest transcript), so a
    # restart doesn't leave a blank chat; start fresh if that can't resume.
    sid="$(ls -t "$HOME/.claude/projects/${dir//[\/.]/-}/"*.jsonl 2>/dev/null | head -n 1 | xargs -r basename | sed 's/\.jsonl$//' || true)"
    [[ "$sid" =~ ^[0-9a-f-]{36}$ ]] || sid=""
    cmd="env -u ANTHROPIC_API_KEY claude --remote-control $(printf %q "$name")"
    [[ -n "$sid" ]] && cmd="$cmd --resume $sid || $cmd"
    tmux new-session -d -s "$sess" -c "$dir" "$cmd; exec bash"
    log "$sess: started in $dir${sid:+ (resumed $sid)}"
    sleep 2
  done <"$LIST"
}

# ----------------------------------------------------------------------
install() {
  if [[ $EUID -eq 0 ]]; then
    echo "Run this as your normal user (not sudo) — it will ask for sudo when needed."
    exit 1
  fi
  command -v tmux >/dev/null || { echo "tmux isn't installed — run claude-setup.sh first."; exit 1; }

  mkdir -p "$HOME/.local/bin" "$STATE_DIR"
  cp "$(readlink -f "$0")" "$BIN"
  chmod +x "$BIN"

  local user="$USER" home="$HOME"
  local envs="Environment=HOME=$home
Environment=PATH=$home/.local/bin:/usr/local/bin:/usr/bin:/bin"

  # Restores sessions at boot and saves the list at shutdown.
  # The tmux server lives in this service, so sessions survive SSH logouts too.
  sudo tee /etc/systemd/system/claude-sessions.service >/dev/null <<EOF
[Unit]
Description=Restore Claude Code tmux sessions (Remote Control)
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
User=$user
$envs
ExecStart=$BIN restore
ExecStop=$BIN save
TimeoutStartSec=5min

[Install]
WantedBy=multi-user.target
EOF

  # Saves the list every 2 minutes so a crash or power cut still restores
  sudo tee /etc/systemd/system/claude-sessions-save.service >/dev/null <<EOF
[Unit]
Description=Save list of running Claude Code sessions

[Service]
Type=oneshot
User=$user
$envs
ExecStart=$BIN save
EOF

  sudo tee /etc/systemd/system/claude-sessions-save.timer >/dev/null <<EOF
[Unit]
Description=Save Claude Code session list every 2 minutes

[Timer]
OnBootSec=4min
OnUnitActiveSec=2min

[Install]
WantedBy=timers.target
EOF

  # Ubuntu's needrestart restarts services after library updates (e.g. libevent
  # for tmux), which would kill every session: leave this one alone.
  if [[ -d /etc/needrestart/conf.d ]]; then
    echo '$nrconf{override_rc}{qr(^claude-sessions)} = 0;' | sudo tee /etc/needrestart/conf.d/claude-sessions.conf >/dev/null
  fi

  save
  sudo systemctl daemon-reload
  sudo systemctl enable --now claude-sessions.service claude-sessions-save.timer
  echo
  echo "Installed. Sessions saved right now:"
  status_list
  echo
  echo "Check anytime with:  claude-autostart status"
}

# ----------------------------------------------------------------------
uninstall() {
  sudo systemctl disable --now claude-sessions-save.timer claude-sessions.service 2>/dev/null || true
  sudo rm -f /etc/systemd/system/claude-sessions.service \
    /etc/systemd/system/claude-sessions-save.service \
    /etc/systemd/system/claude-sessions-save.timer
  sudo systemctl daemon-reload
  rm -f "$BIN"
  echo "Removed. Running sessions were left alone; saved list kept at $LIST"
}

status_list() {
  if [[ -s "$LIST" ]]; then
    while IFS=$'\t' read -r sess dir; do
      if tmux has-session -t "=$sess" 2>/dev/null; then
        printf "  %-30s %-10s %s\n" "$sess" "running" "$dir"
      else
        printf "  %-30s %-10s %s\n" "$sess" "stopped" "$dir"
      fi
    done <"$LIST"
  else
    echo "  (none saved)"
  fi
}

status() {
  echo "Saved sessions (restored at boot):"
  status_list
  echo
  local svc tmr
  svc="$(systemctl is-enabled claude-sessions.service 2>/dev/null || true)"
  tmr="$(systemctl is-active claude-sessions-save.timer 2>/dev/null || true)"
  [[ "$svc" == "enabled" ]] || svc="not installed"
  [[ "$tmr" == "active" ]] || tmr="not running"
  echo "Boot service: $svc"
  echo "Save timer:   $tmr"
}

case "${1:-}" in
  install) install ;;
  uninstall) uninstall ;;
  save) save ;;
  restore) restore ;;
  status) status ;;
  *)
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
