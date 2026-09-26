#!/usr/bin/env bash
# End-to-end tests for claude-launcher-api + claude-setup.sh --api +
# claude-autostart.sh, using stub gh/claude/aws binaries and a private tmux
# server. Needs bash, jq, tmux, git.
#
#   server/tests/test-api.sh

# shellcheck disable=SC2319  # `$?` after [[ ]] is the point here
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(dirname "$HERE")"

WORK="$(mktemp -d)"
export HOME="$WORK/home" STUB_STATE="$WORK/stub" TMUX_TMPDIR="$WORK/tmux"
unset XDG_CONFIG_HOME XDG_STATE_HOME XDG_CACHE_HOME TMUX ANTHROPIC_API_KEY GH_TOKEN GITHUB_TOKEN
mkdir -p "$HOME/bin" "$HOME/.local/bin" "$STUB_STATE" "$TMUX_TMPDIR"
# Stubs go where the real tools live, so the scripts' own PATH setup finds them.
cp "$HERE"/stubs/* "$HOME/.local/bin/"
cp "$SERVER/claude-autostart.sh" "$HOME/.local/bin/claude-autostart"
cp "$SERVER/claude-setup.sh" "$SERVER/claude-launcher-api" "$HOME/bin/"
chmod +x "$HOME/bin/"* "$HOME/.local/bin/"*
export PATH="$HOME/.local/bin:$PATH"
git config --global user.email test@example.com
git config --global user.name test
cleanup() { tmux kill-server 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

LIST="$HOME/.config/claude-setup/sessions.tsv"
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
check "repos newest first + fields" "$(jqt '.ok and .data.repos[0].full_name=="example-org/org-app" and .data.repos[0].owner_type=="Organization" and .data.repos[0].private==false and .data.repos[1].private==true and .data.repos[0].local==false')"

echo "new (first start answers the one-time Remote Control prompt)"
api "new bad/name --owner demo-user"
check "new invalid name forbidden" "$(jqt '.error.code=="forbidden"')"
api "new demo-app2 --owner demo-user --visibility private --start"
check "new ok + started" "$(jqt '.ok and .data.session=="demo-app2" and .data.url=="https://github.com/demo-user/demo-app2"')"
[[ -f "$HOME/projects/demo-app2/README.md" ]]; check "repo created with README and cloned" $?
tmux has-session -t "=demo-app2" 2>/dev/null; check "tmux session exists" $?
[[ -f "$STUB_STATE/rc_enabled" && -f "$HOME/.config/claude-setup/remote-control-confirmed" ]]; check "Enable Remote Control answered y" $?
jq -e --arg d "$HOME/projects/demo-app2" '.projects[$d].hasTrustDialogAccepted' "$HOME/.claude.json" >/dev/null; check "folder pre-trusted" $?
grep -q "^demo-app2	$HOME/projects/demo-app2$" "$LIST"; check "claude-autostart list updated" $?
api "new demo-app2 --owner demo-user --visibility private"
check "new again -> repo_exists" "$(jqt '.error.code=="repo_exists"')"
api "new my.dotted --owner example-org --visibility public --start"
check "dotted name -> dashed session" "$(jqt '.ok and .data.session=="my-dotted"')"

echo "sessions / tail / start / stop"
api "sessions"
check "sessions lists them" "$(jqt '.ok and ([.data.sessions[].name] | index("demo-app2") != null and index("my-dotted") != null) and (.data.sessions[] | select(.name=="my-dotted") | .project=="my.dotted")')"
api "tail demo-app2 --lines 5"
check "tail ok" "$(jqt '.ok and .data.lines==5')"
api "start demo-app2"
check "start already running is ok" "$(jqt '.ok and .data.already_running==true')"
api "stop my.dotted"
check "stop" "$(jqt '.ok and .data.stopped==true and .data.session=="my-dotted"')"
grep -q "^my-dotted	" "$LIST"; [[ $? -ne 0 ]]; check "stopped session dropped from autostart list" $?
api "start nope"
check "start missing folder" "$(jqt '.error.code=="invalid_name"')"

echo "open"
api "open example-org/org-app --start"
check "open clones + starts" "$(jqt '.ok and .data.action=="cloned" and .data.session=="org-app"')"
api "open example-org/org-app"
check "open again (pull attempted)" "$(jqt '.ok and (.data.action=="pulled" or .data.action=="not_updated")')"
touch "$HOME/projects/org-app/dirty.txt"
api "open example-org/org-app"
check "dirty folder" "$(jqt '.error.code=="folder_dirty" and (.error.path|endswith("/projects/org-app"))')"
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
pkill -f -- '--clone-worker' 2>/dev/null; rm -rf "$HOME/projects/demo-app"

echo "claude-autostart (reboot)"
"$HOME/.local/bin/claude-autostart" save >/dev/null
tmux kill-server 2>/dev/null; sleep 0.5
"$HOME/.local/bin/claude-autostart" restore >/dev/null
tmux has-session -t "=org-app" 2>/dev/null && tmux has-session -t "=demo-app2" 2>/dev/null; check "autostart restores phone-started sessions" $?

echo "claude login"
api "login-claude-start"
check "login url returned" "$(jqt '.ok and (.data.url | startswith("https://claude.ai/oauth/authorize"))')"
api "login-claude-code" "bad-code-123"
check "bad code -> not_logged_in_claude" "$(jqt '.error.code=="not_logged_in_claude"')"
api "login-claude-start"
api "login-claude-code" "good-code-123"
check "good code -> logged in" "$(jqt '.ok and .data.logged_in')"
api "status"
check "status shows claude + github" "$(jqt '.data.claude.logged_in and .data.github.logged_in and .data.github.missing_scopes==[]')"

echo "aws"
api "login-aws-sso-start"
check "sso not configured" "$(jqt '.error.code=="not_logged_in_aws" and .error.sso_configured==false')"
mkdir -p "$HOME/.aws" && printf '[default]\nsso_session = demo\n' >"$HOME/.aws/config"
api "login-aws-sso-start"
check "sso url + code" "$(jqt '.ok and .data.code=="ABCD-EFGH" and (.data.url|startswith("https://device.sso"))')"
api "login-aws-keys" "$(printf 'AKIAEXAMPLEEXAMPLE12\nwJalrXUtnFEMIK7MDENGbPxRfiCYEXAMPLEKEY\nus-east-1')"
check "aws keys" "$(jqt '.ok and .data.account=="123456789012"')"

echo "direct script: exit codes and stdout purity"
OUT="$("$HOME/bin/claude-setup.sh" --api bogus </dev/null 2>/dev/null)"; RC=$?
[[ $RC == 2 ]]; check "bad args exit 2" $?
OUT="$("$HOME/bin/claude-setup.sh" --api status </dev/null 2>/dev/null)"
[[ "$(wc -l <<<"$OUT")" == 1 ]]; check "exactly one JSON line" $?

echo "interactive menu still runs"
OUT="$(printf '2\nq\n' | "$HOME/bin/claude-setup.sh" 2>&1)"
[[ "$OUT" == *"What are you working on?"* && "$OUT" == *"example-org/org-app"* && "$OUT" == *"Running sessions on the Pi:"* ]]
check "menu lists repos and exits on q" $?

echo
echo "$pass passed, $failn failed"
((failn == 0))
