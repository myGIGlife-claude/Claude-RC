#!/usr/bin/env bash
# install-launcher-key.sh — authorize the cLaudeRC phone key, locked to the runner.
#
#   ./install-launcher-key.sh 'ssh-ed25519 AAAA... clauderc@phone'
#   ./install-launcher-key.sh < phone.pub
#   ./install-launcher-key.sh --remove 'ssh-ed25519 AAAA...'
#
# Writes one line to ~/.ssh/authorized_keys:
#   command="$HOME/bin/claude-launcher-api",no-pty,no-port-forwarding,
#   no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... clauderc
# Any earlier line with the same key is replaced.

set -euo pipefail

RUNNER="$HOME/bin/claude-launcher-api"
AUTH_KEYS="$HOME/.ssh/authorized_keys"
OPTS="command=\"$RUNNER\",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-user-rc"

remove=0
if [[ "${1:-}" == "--remove" ]]; then remove=1; shift; fi

if [[ $# -ge 1 ]]; then key="$*"; else
  [[ -t 0 ]] && echo "Paste the public key from the app, then press Enter:" >&2
  IFS= read -r key
fi
key="$(printf '%s' "$key" | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

if ! [[ "$key" =~ ^ssh-ed25519\ ([A-Za-z0-9+/]+={0,2})(\ [A-Za-z0-9@._-]{0,64})?$ ]]; then
  echo "That is not an ssh-ed25519 public key." >&2
  exit 2
fi
blob="${BASH_REMATCH[1]}"

if command -v ssh-keygen >/dev/null; then
  printf 'ssh-ed25519 %s\n' "$blob" | ssh-keygen -l -f /dev/stdin >/dev/null 2>&1 ||
    { echo "ssh-keygen rejected the key." >&2; exit 2; }
fi

mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
touch "$AUTH_KEYS"
chmod 600 "$AUTH_KEYS"

tmp="$(mktemp "$HOME/.ssh/authorized_keys.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
grep -vF -- " ssh-ed25519 $blob" "$AUTH_KEYS" | grep -vxF -- "ssh-ed25519 $blob" >"$tmp" || true
if ((remove == 0)); then
  printf '%s ssh-ed25519 %s clauderc\n' "$OPTS" "$blob" >>"$tmp"
fi
chmod 600 "$tmp"
cp "$AUTH_KEYS" "$AUTH_KEYS.bak"
mv "$tmp" "$AUTH_KEYS"
trap - EXIT

if ((remove)); then
  echo "Removed the key from $AUTH_KEYS."
  exit 0
fi

echo "Installed. The key can only run $RUNNER."
[[ -x "$RUNNER" ]] || echo "Note: $RUNNER is missing — copy server/claude-launcher-api there and chmod +x it." >&2
if command -v ssh-keygen >/dev/null; then
  printf 'ssh-ed25519 %s\n' "$blob" | ssh-keygen -l -f /dev/stdin 2>/dev/null | awk '{print "Key fingerprint: " $2}'
fi
