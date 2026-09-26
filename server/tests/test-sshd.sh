#!/usr/bin/env bash
# Integration test against a real sshd (needs root; meant for CI or a throwaway
# container). Creates a local test user, starts sshd on 127.0.0.1:2222 and checks:
#   - the forced command blocks shells for an OpenSSH client key
#   - the app's own SSH code (JVM unit test) pins the host key, authenticates
#     with its Ed25519 key, runs allowlisted actions and sends secrets on stdin.
#
#   sudo server/tests/test-sshd.sh            # OpenSSH checks only
#   sudo server/tests/test-sshd.sh --app      # also run the app's SshIntegrationTest

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(dirname "$HERE")"
REPO="$(dirname "$SERVER")"
TUSER=cltest
PORT=2222
WORK="$(mktemp -d)"
chmod 755 "$WORK"

[[ $EUID -eq 0 ]] || { echo "run as root" >&2; exit 1; }
command -v sshd >/dev/null || [[ -x /usr/sbin/sshd ]] || { echo "install openssh-server" >&2; exit 1; }

id "$TUSER" >/dev/null 2>&1 || useradd -m -s /bin/bash "$TUSER"
usermod -p '*' "$TUSER" # unlocked, but no password
THOME="$(getent passwd "$TUSER" | cut -d: -f6)"

install -d -o "$TUSER" -m 755 "$THOME/bin" "$THOME/stubs"
install -o "$TUSER" -m 755 "$SERVER/claude-setup.sh" "$SERVER/claude-launcher-api" \
  "$SERVER/claude-autostart.sh" "$SERVER/install-launcher-key.sh" "$THOME/bin/"
install -o "$TUSER" -m 755 "$HERE"/stubs/* "$THOME/stubs/"
install -d -o "$TUSER" -m 700 "$THOME/.config" "$THOME/.config/claude-launcher"
cat >"$THOME/.config/claude-launcher/config" <<'EOF'
PROJECTS_DIR="$HOME/projects"
EXTRA_PATH="$HOME/stubs"
EOF
chown "$TUSER" "$THOME/.config/claude-launcher/config"
rm -f "$THOME/.ssh/authorized_keys"

ssh-keygen -q -t ed25519 -N '' -f "$WORK/hostkey"
mkdir -p /run/sshd
cat >"$WORK/sshd_config" <<EOF
Port $PORT
ListenAddress 127.0.0.1
HostKey $WORK/hostkey
PidFile $WORK/sshd.pid
AuthorizedKeysFile .ssh/authorized_keys
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
UsePAM no
AllowUsers $TUSER
EOF
/usr/sbin/sshd -f "$WORK/sshd_config" -E "$WORK/sshd.log"
cleanup() { kill "$(cat "$WORK/sshd.pid" 2>/dev/null)" 2>/dev/null || true; su - "$TUSER" -c 'tmux kill-server' 2>/dev/null || true; rm -rf "$WORK"; }
trap cleanup EXIT
sleep 1

pass=0 failn=0
check() { if eval "$2"; then pass=$((pass + 1)); echo "  ok   $1"; else failn=$((failn + 1)); echo "  FAIL $1"; echo "       $OUT"; fi; }

echo "OpenSSH client with a forced-command key"
ssh-keygen -q -t ed25519 -N '' -C phone -f "$WORK/phonekey"
cp "$WORK/phonekey.pub" "$WORK/pub"; chmod 644 "$WORK/pub"
su - "$TUSER" -c "\$HOME/bin/install-launcher-key.sh < '$WORK/pub'" >/dev/null
SSH=(ssh -i "$WORK/phonekey" -p "$PORT" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR "$TUSER@127.0.0.1")

OUT="$("${SSH[@]}" </dev/null 2>&1 || true)"
check "plain ssh (shell) is forbidden" '[[ "$OUT" == *"\"code\":\"forbidden\""* ]]'
OUT="$("${SSH[@]}" bash </dev/null 2>&1 || true)"
check "ssh ... bash is forbidden" '[[ "$OUT" == *"\"code\":\"forbidden\""* ]]'
OUT="$("${SSH[@]}" 'status; id' </dev/null 2>&1 || true)"
check "ssh ... 'status; id' is forbidden" '[[ "$OUT" == *"\"code\":\"forbidden\""* && "$OUT" != *uid=* ]]'
OUT="$("${SSH[@]}" status </dev/null 2>&1 || true)"
check "ssh ... status works" '[[ "$OUT" == "{\"ok\":true"* ]]'
OUT="$(cat "$THOME/.ssh/authorized_keys")"
check "authorized_keys: restrict + forced runner" '[[ "$OUT" == "restrict,command=\"$THOME/bin/claude-launcher-api\" ssh-ed25519 "* ]]'

if [[ "${1:-}" == "--app" ]]; then
  echo "App SSH code (SshIntegrationTest)"
  cat >"$WORK/install-key" <<EOF
#!/bin/sh
printf '%s\n' "\$*" | su - $TUSER -c '\$HOME/bin/install-launcher-key.sh' >/dev/null
EOF
  chmod 755 "$WORK/install-key"
  # Start from a clean authorized_keys so the app's first call must fail auth.
  rm -f "$THOME/.ssh/authorized_keys"
  export CLAUDERC_IT_HOST=127.0.0.1 CLAUDERC_IT_PORT=$PORT CLAUDERC_IT_USER=$TUSER
  CLAUDERC_IT_HOSTKEY_FP="$(ssh-keygen -lf "$WORK/hostkey.pub" | awk '{print $2}')"
  export CLAUDERC_IT_HOSTKEY_FP CLAUDERC_IT_INSTALL="$WORK/install-key"
  if (cd "$REPO" && ./gradlew --no-daemon -q testDebugUnitTest --tests 'life.mygig.clauderc.SshIntegrationTest' --rerun); then
    pass=$((pass + 1)); echo "  ok   app end-to-end"
  else
    failn=$((failn + 1)); echo "  FAIL app end-to-end (see app/build/reports/tests)"
  fi
fi

echo
echo "$pass passed, $failn failed"
((failn == 0))
