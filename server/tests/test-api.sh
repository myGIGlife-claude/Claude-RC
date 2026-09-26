#!/usr/bin/env bash
# End-to-end tests for claude-launcher-api + claude-setup.sh --api, using stub
# gh/claude/aws binaries and a private tmux server. Needs bash, jq, tmux, git.
#
#   server/tests/test-api.sh

# shellcheck disable=SC2319  # `$?` after [[ ]] is the point here
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(dirname "$HERE")"

WORK="$(mktemp -d)"
export HOME="$WORK/home" STUB_STATE="$WORK/stub" TMUX_TMPDIR="$WORK/tmux"
unset XDG_CONFIG_HOME XDG_STATE_HOME XDG_CACHE_HOME TMUX
mkdir -p "$HOME/bin" "$STUB_STATE" "$TMUX_TMPDIR"
export PATH="$HERE/stubs:$PATH"
git config --global user.email test@example.com >/dev/null 2>&1 || true
cp "$SERVER/claude-setup.sh" "$SERVER/claude-launcher-api" "$SERVER/claude-autostart.sh" "$HOME/bin/"
chmod +x "$HOME/bin/"*
cleanup() { tmux kill-server 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

pass=0 failn=0
check() { # check <name> <condition-exit-code>
  if [[ "$2" == 0 ]]; then pass=$((pass + 1)); printf '  ok   %s\n' "$1"
  else failn=$((failn + 1)); printf '  FAIL %s\n' "$1"; printf '       output: %s\n' "$OUT"; fi
}
# api "<SSH_ORIGINAL_COMMAND>" [stdin]
api() {
  OUT="$(SSH_ORIGINAL_COMMAND="$1" "$HOME/bin/claude-launcher-api" <<<"${2:-}")"
  RC=$?
}
jqt() { jq -e "$1" >/dev/null 2>&1 <<<"$OUT"; echo $?; }

echo "runner allowlist"
for bad in "" "bash" "status; bash" 'status $(id)' "status && id" "rm -rf /" "start ../etc" \
  "start a b" "repos --all" "open not-a-repo" "open a/b --evil" "new x --owner" \
  "new x --visibility secret" "tail x --lines 9999" "status extra" "new -x" "login-github token" \
  "open a/b/c" "$(printf 'x%.0s' {1..500})"; do
  api "$bad"
  check "forbidden: '${bad:0:30}'" "$(jqt '.ok==false and .error.code=="forbidden"')"
done
[[ -s "$HOME/.local/state/claude-launcher/api.log" ]]; check "audit log written" $?

echo "status"
api "status"
check "status not logged in" "$(jqt '.ok and .data.claude.logged_in==false and .data.github.logged_in==false and .data.aws.logged_in==false')"
[[ -f "$HOME/.config/claude-launcher/config" ]]; check "config created on first run" $?

echo "github login"
api "login-github" "not-a-token!"
check "bad token rejected" "$(jqt '.error.code=="invalid_name"')"
api "login-github" "ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaa"
check "github login ok" "$(jqt '.ok and .data.user=="demo-user"')"
echo "repo, workflow" >"$STUB_STATE/gh_scopes"
api "login-github" "ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaa"
check "missing scopes reported" "$(jqt '.error.code=="missing_scopes" and .error.missing==["read:org"]')"
rm "$STUB_STATE/gh_scopes"
grep -q ghp_ "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "token not in audit log" $?

echo "owners / repos"
api "owners"
check "owners" "$(jqt '.ok and .data.user=="demo-user" and .data.orgs[0].login=="example-org"')"
api "repos --refresh"
check "repos sorted newest first" "$(jqt '.ok and .data.repos[0].full_name=="example-org/org-app" and .data.repos[0].local==false')"

echo "new"
api "new bad/name --owner demo-user"
check "new invalid name forbidden" "$(jqt '.error.code=="forbidden"')"
api "new demo-app2 --owner demo-user --visibility private --start"
check "new ok + started" "$(jqt '.ok and .data.session=="demo-app2" and .data.url=="https://github.com/demo-user/demo-app2"')"
tmux has-session -t "=demo-app2" 2>/dev/null; check "tmux session exists" $?
api "new demo-app2 --owner demo-user --visibility private"
check "new again -> repo_exists" "$(jqt '.error.code=="repo_exists"')"
grep -qx demo-app2 "$HOME/.local/state/claude-launcher/sessions.list"; check "registered for autostart" $?

echo "sessions / tail / start / stop"
api "sessions"
check "sessions lists it" "$(jqt '.ok and (.data.sessions | map(.name) | index("demo-app2")) != null and .data.sessions[0].attached==false')"
api "tail demo-app2 --lines 5"
check "tail ok" "$(jqt '.ok and .data.lines==5')"
api "start demo-app2"
check "start already running is ok" "$(jqt '.ok and .data.already_running==true')"
api "stop demo-app2"
check "stop" "$(jqt '.ok and .data.stopped==true')"
grep -qx demo-app2 "$HOME/.local/state/claude-launcher/sessions.list"; [[ $? -ne 0 ]]; check "unregistered" $?
api "start nope"
check "start missing folder" "$(jqt '.error.code=="invalid_name"')"

echo "open"
api "open example-org/org-app --start"
check "open clones + starts" "$(jqt '.ok and .data.action=="cloned" and .data.session=="org-app"')"
api "open example-org/org-app"
check "open again (empty repo)" "$(jqt '.ok and (.data.action=="skipped_empty" or .data.action=="pulled")')"
touch "$HOME/projects/org-app/dirty.txt"
api "open example-org/org-app"
check "dirty folder" "$(jqt '.error.code=="folder_dirty"')"
api "repos"
check "repos local+running flags" "$(jqt '.data.repos[0].local==true and .data.repos[0].running==true')"

echo "lock"
(
  exec 9>>"$HOME/.local/state/claude-launcher/api.lock"
  flock 9
  sleep 3
) &
sleep 0.5
api "open demo-user/demo-app"
check "concurrent open -> busy" "$(jqt '.error.code=="busy"')"
wait

echo "slow clone runs in background"
STUB_CLONE_SLEEP=50 api "open demo-user/demo-app" &
sleep 1
api "open demo-user/demo-app"
check "second open while cloning -> busy" "$(jqt '.error.code=="busy"')"
kill %1 2>/dev/null; wait 2>/dev/null

echo "autostart"
tmux kill-server 2>/dev/null
"$HOME/bin/claude-autostart.sh" >/dev/null
tmux has-session -t "=org-app" 2>/dev/null; check "autostart restores session" $?

echo "claude login"
api "login-claude-start"
check "login url returned" "$(jqt '.ok and (.data.url | startswith("https://claude.ai/oauth/authorize"))')"
api "login-claude-code" "bad-code-123"
check "bad code -> not_logged_in_claude" "$(jqt '.error.code=="not_logged_in_claude"')"
api "login-claude-start"
api "login-claude-code" "good-code-123"
check "good code -> logged in" "$(jqt '.ok and .data.logged_in')"
api "status"
check "status shows claude logged in" "$(jqt '.data.claude.logged_in and .data.github.logged_in')"

echo "aws"
api "login-aws-sso-start"
check "sso not configured" "$(jqt '.error.code=="not_logged_in_aws" and .error.sso_configured==false')"
touch "$STUB_STATE/aws_sso"
api "login-aws-sso-start"
check "sso url + code" "$(jqt '.ok and .data.code=="ABCD-EFGH" and (.data.url|startswith("https://device.sso"))')"
api "login-aws-keys" "$(printf 'AKIAEXAMPLEEXAMPLE12\nwJalrXUtnFEMIK7MDENGbPxRfiCYEXAMPLEKEY\nus-east-1')"
check "aws keys" "$(jqt '.ok and .data.account=="123456789012"')"

echo "direct script: exit codes and stdout purity"
OUT="$("$HOME/bin/claude-setup.sh" --api bogus </dev/null 2>/dev/null)"; RC=$?
[[ $RC == 2 ]]; check "bad args exit 2" $?
OUT="$("$HOME/bin/claude-setup.sh" --api status </dev/null 2>/dev/null)"
[[ "$(wc -l <<<"$OUT")" == 1 ]]; check "exactly one JSON line" $?

echo
echo "$pass passed, $failn failed"
((failn == 0))
