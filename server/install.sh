#!/usr/bin/env bash
# install.sh — install or update the cLaudeRC server scripts without cloning the repo.
#
#   curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash
#   curl -fsSL …/install.sh | bash -s -- 'ssh-ed25519 AAAA… clauderc'   # also authorize the phone
#
# Puts claude-setup.sh in ~/, claude-launcher-api and install-launcher-key.sh in
# ~/bin/, youtube-upload and claude-backup (encrypted backup for moving servers) in ~/.local/bin, keeps a backup of a changed ~/claude-setup.sh, sets up claude-autostart
# (saves running sessions, restores them at boot; asks for sudo once) if it isn't
# installed yet, authorizes the key if given, and prints what to enter in the app.
# Safe to run again to update.

set -euo pipefail

# Everything runs from main() so a half-downloaded script never runs.
main() {
  local repo="myGIGlife-claude/Claude-RC" base sha="${CLAUDERC_COMMIT:-}"
  local raw="${CLAUDERC_RAW:-https://raw.githubusercontent.com}"
  # Download from the last commit that changed server/, not the main branch:
  # raw.githubusercontent.com caches a branch for ~5 minutes, a commit URL is
  # never stale, and the app compares this commit to spot updates.
  if [[ -z "$sha" && -z "${CLAUDERC_BASE:-}" ]]; then
    sha="$(curl -fsSL --max-time 10 "https://api.github.com/repos/$repo/commits?path=server&per_page=1" 2>/dev/null |
      grep -oE '"sha": ?"[0-9a-f]{40}"' | head -n 1 | grep -oE '[0-9a-f]{40}')" || sha=""
    [[ -n "$sha" ]] || sha="main"
  fi
  base="${CLAUDERC_BASE:-$raw/$repo/$sha/server}"
  local f missing=()
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT  # tmp is global so the trap still sees it after main returns

  for f in jq tmux git flock curl; do command -v "$f" >/dev/null || missing+=("$f"); done
  ((${#missing[@]} == 0)) || echo "Note: install these first: ${missing[*]} (e.g. sudo apt install ${missing[*]})" >&2

  for f in claude-setup.sh claude-launcher-api install-launcher-key.sh claude-autostart.sh youtube-upload clauderc-team claude-push claude-plugin-updates claude-backup; do
    curl -fsSL "$base/$f" -o "$tmp/$f"
  done

  if [[ -f "$HOME/claude-setup.sh" ]] && ! cmp -s "$tmp/claude-setup.sh" "$HOME/claude-setup.sh"; then
    cp "$HOME/claude-setup.sh" "$HOME/claude-setup.sh.bak"
    echo "Backed up your old ~/claude-setup.sh to ~/claude-setup.sh.bak"
  fi
  mkdir -p "$HOME/bin" "$HOME/.config/claude-launcher"
  # Write a new file and rename it over the old one: these scripts may be
  # running right now (an update started from the phone), and bash reads a
  # script while it runs, so the old file must never be changed in place.
  put() { install -m 755 "$1" "$2.new" && mv -f "$2.new" "$2"; }
  put "$tmp/claude-setup.sh" "$HOME/claude-setup.sh"
  put "$tmp/claude-launcher-api" "$HOME/bin/claude-launcher-api"
  put "$tmp/install-launcher-key.sh" "$HOME/bin/install-launcher-key.sh"
  # Tools every Claude session can run (~/.local/bin is on their PATH).
  mkdir -p "$HOME/.local/bin"
  put "$tmp/youtube-upload" "$HOME/.local/bin/youtube-upload"
  put "$tmp/clauderc-team" "$HOME/.local/bin/clauderc-team"
  put "$tmp/claude-push" "$HOME/.local/bin/claude-push"
  put "$tmp/claude-plugin-updates" "$HOME/.local/bin/claude-plugin-updates"
  put "$tmp/claude-backup" "$HOME/.local/bin/claude-backup"
  if [[ "$sha" =~ ^[0-9a-f]{40}$ ]]; then
    printf '%s\n' "$sha" >"$HOME/.config/claude-launcher/installed-commit"
  fi
  echo "Installed ~/claude-setup.sh, ~/bin/claude-launcher-api, ~/bin/install-launcher-key.sh (${sha:0:7})"

  # First run writes ~/.config/claude-launcher/config.
  "$HOME/claude-setup.sh" --api status </dev/null >/dev/null 2>&1 || true

  # Bun: some Claude Code plugins' hooks run on it.
  if ! command -v bun >/dev/null && [[ ! -x "$HOME/.local/bin/bun" ]]; then
    echo "Installing Bun into ~/.local/bin (plugin hooks need it)..."
    timeout 90 "$HOME/claude-setup.sh" --api install-cli bun </dev/null >/dev/null 2>&1 || echo "Note: Bun wasn't installed." >&2
  fi

  # Team: the MCP server the main Claude uses to hand work to other accounts (user scope, absolute path).
  if command -v claude >/dev/null && command -v python3 >/dev/null; then
    claude mcp get clauderc-team >/dev/null 2>&1 ||
      claude mcp add -s user clauderc-team -- "$HOME/.local/bin/clauderc-team" mcp >/dev/null 2>&1 ||
      echo "Note: the Team MCP server wasn't registered (claude mcp add failed)." >&2
  fi

  if command -v claude-autostart >/dev/null || [[ -x "$HOME/.local/bin/claude-autostart" ]]; then
    # Update the script itself (no sudo); the systemd units stay as they are.
    install -m 755 "$tmp/claude-autostart.sh" "$HOME/.local/bin/claude-autostart"
    echo "claude-autostart is already set up (script updated)."
    [[ ! -d /etc/needrestart/conf.d || -f /etc/needrestart/conf.d/claude-sessions.conf ]] ||
      echo "Tip: run 'claude-autostart install' once so system updates stop restarting your Claude sessions."
  else
    echo "Setting up claude-autostart (restores Claude sessions after a reboot; asks for sudo)..."
    bash "$tmp/claude-autostart.sh" install </dev/null ||
      echo "Note: claude-autostart wasn't set up. Run it later: bash claude-autostart.sh install" >&2
  fi

  if [[ $# -ge 1 ]]; then
    "$HOME/bin/install-launcher-key.sh" "$@" </dev/null
  else
    echo "No phone key given. To authorize a phone, run the command from the app's setup screen."
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
