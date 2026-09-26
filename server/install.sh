#!/usr/bin/env bash
# install.sh — install or update the cLaudeRC server scripts without cloning the repo.
#
#   curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash
#   curl -fsSL …/install.sh | bash -s -- 'ssh-ed25519 AAAA… clauderc'   # also authorize the phone
#
# Puts claude-setup.sh in ~/, claude-launcher-api and install-launcher-key.sh in
# ~/bin/, keeps a backup of a changed ~/claude-setup.sh, sets up claude-autostart
# (saves running sessions, restores them at boot; asks for sudo once) if it isn't
# installed yet, authorizes the key if given, and prints what to enter in the app.
# Safe to run again to update.

set -euo pipefail

# Everything runs from main() so a half-downloaded script never runs.
main() {
  local base="${CLAUDERC_BASE:-https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server}"
  local f missing=()
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT  # tmp is global so the trap still sees it after main returns

  for f in jq tmux git flock curl; do command -v "$f" >/dev/null || missing+=("$f"); done
  ((${#missing[@]} == 0)) || echo "Note: install these first: ${missing[*]} (e.g. sudo apt install ${missing[*]})" >&2

  for f in claude-setup.sh claude-launcher-api install-launcher-key.sh claude-autostart.sh; do
    curl -fsSL "$base/$f" -o "$tmp/$f"
  done

  if [[ -f "$HOME/claude-setup.sh" ]] && ! cmp -s "$tmp/claude-setup.sh" "$HOME/claude-setup.sh"; then
    cp "$HOME/claude-setup.sh" "$HOME/claude-setup.sh.bak"
    echo "Backed up your old ~/claude-setup.sh to ~/claude-setup.sh.bak"
  fi
  install -m 755 "$tmp/claude-setup.sh" "$HOME/claude-setup.sh"
  mkdir -p "$HOME/bin"
  install -m 755 "$tmp/claude-launcher-api" "$tmp/install-launcher-key.sh" "$HOME/bin/"
  echo "Installed ~/claude-setup.sh, ~/bin/claude-launcher-api, ~/bin/install-launcher-key.sh"

  # First run writes ~/.config/claude-launcher/config.
  "$HOME/claude-setup.sh" --api status </dev/null >/dev/null 2>&1 || true

  if command -v claude-autostart >/dev/null || [[ -x "$HOME/.local/bin/claude-autostart" ]]; then
    echo "claude-autostart is already set up."
  else
    echo "Setting up claude-autostart (restores Claude sessions after a reboot; asks for sudo)..."
    bash "$tmp/claude-autostart.sh" install </dev/null ||
      echo "Note: claude-autostart wasn't set up. Run it later: bash claude-autostart.sh install" >&2
  fi

  if [[ $# -ge 1 ]]; then
    "$HOME/bin/install-launcher-key.sh" "$@" </dev/null
  else
    echo "Next: in the app tap 'Copy install command' and run it here to authorize the phone."
  fi

  local port
  port="$(awk 'tolower($1)=="port" {print $2; exit}' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null || true)"
  echo
  echo "Now enter this in the app:"
  echo "  Host:     $(hostname -I 2>/dev/null | awk '{print $1}') (or $(hostname))"
  echo "  Port:     ${port:-22}"
  echo "  Username: $(id -un)"
  echo "When the app shows the server's fingerprint, it must be one of these:"
  for f in /etc/ssh/ssh_host_*_key.pub; do
    [[ -r "$f" ]] && ssh-keygen -lf "$f" | awk '{print "  " $2 " " $NF}'
  done
  echo "Settings: ~/.config/claude-launcher/config (PROJECTS_DIR, EXTRA_PATH)."
}

main "$@"
