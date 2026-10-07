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
# CLAUDE_CONFIG_DIR must go: a worker that runs this suite has it pointing at its REAL login, and the stub `claude auth
# login` would overwrite the real credentials (that logged CodeWriter out three times).
unset XDG_CONFIG_HOME XDG_STATE_HOME XDG_CACHE_HOME TMUX ANTHROPIC_API_KEY GH_TOKEN GITHUB_TOKEN CLAUDE_CONFIG_DIR CLAUDE_CODE_OAUTH_TOKEN
mkdir -p "$HOME/bin" "$HOME/.local/bin" "$STUB_STATE" "$TMUX_TMPDIR"
# Stubs go where the real tools live, so the scripts' own PATH setup finds them.
cp "$HERE"/stubs/* "$HOME/.local/bin/"
cp "$SERVER/claude-autostart.sh" "$HOME/.local/bin/claude-autostart"
cp "$SERVER/claude-push" "$HOME/.local/bin/"
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
  "open a/b/c" "$(printf 'x%.0s' {1..500})" "start --dangerously-skip-permissions" "start -x" \
  "open owner/-x" "open -o/x" "new x --owner --start" "tail -x" "clone-status nope" \
  "login-gitlab glpat-x" "login-docker docker.io" "self-update" "self-update main" \
  "self-update 0123456789abcdef0123456789abcdef0123456" "self-update ../../etc" "install-cli" \
  "install-cli glab extra" "install-cli rm" "login-token" "login-token evil" "login-token vercel x" \
  "skills-update" "skills-update demo" "skills-update demo/x extra" "skills-update a/b/c" "skills-update demo/x;id" "skills-update demo/x --force" \
  "migrate-plan x" "migrate-keygen x" "migrate-authorize x" "migrate-authorize --force" "migrate-send x" "migrate-send 1.2.3.4" \
  "migrate-passphrase x" "migrate-status x" "migrate-status --all" "migrate-restore" "migrate-restore ../../etc/passwd" \
  "migrate-restore incoming-1.gpg" "migrate-restore incoming-20260101-010101.gpgx" "migrate-restore incoming-20260101-010101.gpg extra" \
  "migrate-restore incoming-20260101-010101.gpg pass" "migrate-restore -incoming-20260101-010101.gpg" "migrate-restore incoming-20260101-010101.gpg;id" \
  "migrate-restore /etc/passwd" \
  "migrate-sudo-check x" "migrate-sudo-check --password" "migrate-reboot x" "migrate-reboot now" "migrate-create-user" "migrate-create-user Bad" \
  "migrate-create-user 1abc" "migrate-create-user -x" "migrate-create-user a b" "migrate-create-user ab;id" "migrate-create-user ab/cd" \
  "migrate-create-user ab pw-ok" "migrate-create-user $(printf 'a%.0s' {1..40})" "migrate-verify a b" "migrate-clone" "migrate-clone ../x" "migrate-clone a b" "migrate-verify ../x" "migrate-verify .hidden" \
  "migrate-verify a/b" "migrate-verify $(printf 'a%.0s' {1..120})" "migrate-signout-old" "migrate-signout-old bogus" "migrate-signout-old claude claude" \
  "migrate-signout-old claude workers github autostart claude" "migrate-signout-old claude --all" "migrate-signout-old ../claude" "migrate-signout-old claude;id" \
  "migrate-signout-old all"; do
  api "$bad"
  check "forbidden: '${bad:0:30}'" "$(jqt '.ok==false and .error.code=="forbidden"')"
done
[[ -s "$HOME/.local/state/claude-launcher/api.log" ]]; check "audit log written" $?
api "ghp_SECRETTOKENVALUE123456"
grep -q SECRETTOKEN "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "unknown first word not logged" $?

echo "status"
api "status"
check "status not logged in" "$(jqt '.ok and .data.claude.logged_in==false and .data.github.logged_in==false and .data.aws.logged_in==false')"
[[ -f "$HOME/.config/claude-launcher/config" ]]; check "config created on first run" $?
check "status has services, none logged in" "$(jqt '.data.services.gitlab.installed and (.data.services.gitlab.logged_in|not) and (.data.services.docker.logged_in|not) and (.data.services.cloudflare.logged_in|not)')"

echo "service logins"
api "login-gitlab" "glpat-bad-token-000000000"
check "gitlab bad token rejected" "$(jqt '.ok==false and .error.code=="not_logged_in"')"
api "login-gitlab" "short"
check "gitlab malformed token" "$(jqt '.error.code=="invalid_name"')"
api "login-gitlab" "glpat-good-token-00000000"
check "gitlab login ok" "$(jqt '.ok and .data.user=="demo-gl"')"
api "login-docker" $'ghcr.io\ndemo\nbad-password'
check "docker bad password rejected" "$(jqt '.ok==false and .error.code=="not_logged_in"')"
api "login-docker" $'ghcr.io\n-rf\ngood-password'
check "docker username can't start with -" "$(jqt '.error.code=="invalid_name"')"
api "login-docker" $'-registry\ndemo\ngood-password'
check "docker registry can't start with -" "$(jqt '.error.code=="invalid_name"')"
api "login-gitlab" $'glpat-good-token-00000000\n--hostname=evil'
check "gitlab host can't start with -" "$(jqt '.error.code=="invalid_name"')"
api "login-docker" $'ghcr.io\ndemo\ngood-password'
check "docker login ok" "$(jqt '.ok and .data.registry=="ghcr.io"')"
api "status"
check "status shows gitlab + docker" "$(jqt '.data.services.gitlab.detail=="demo-gl" and .data.services.docker.detail=="ghcr.io"')"

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
touch "$STUB_STATE/fail_list"
api "repos --refresh"
check "gh failure is an error, not an empty list" "$(jqt '.ok==false and .error.code=="internal"')"
rm "$STUB_STATE/fail_list"
api "owners"
check "owners" "$(jqt '.ok and .data.user=="demo-user" and .data.orgs[0].login=="example-org"')"
api "repos --refresh"
check "repos newest first + fields" "$(jqt '.ok and .data.repos[0].full_name=="example-org/org-app" and .data.repos[0].owner_type=="Organization" and .data.repos[0].private==false and .data.repos[1].private==true and .data.repos[0].local==false')"
check "repos marks archived ones (phone asks for them)" "$(jqt '[.data.repos[] | select(.archived)] | map(.name) == ["old-app"]')"

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
# A session whose name differs from its folder: Stop/Tail by session name.
tmux new-session -d -s work -c "$HOME/projects/demo-app2" "claude --remote-control demo-app2x; exec bash"
api "tail work"
check "tail by session name" "$(jqt '.ok and .data.session=="work"')"
api "stop work"
check "stop by session name" "$(jqt '.ok and .data.session=="work" and .data.stopped')"
tmux has-session -t "=demo-app2" 2>/dev/null; check "the other session was left alone" $?
api "sessions"
check "sessions lists them" "$(jqt '.ok and ([.data.sessions[].name] | index("demo-app2") != null and index("my-dotted") != null) and (.data.sessions[] | select(.name=="my-dotted") | .project=="my.dotted")')"
api "tail demo-app2 --lines 5"
check "tail ok" "$(jqt '.ok and .data.lines==5')"
api "start demo-app2"
check "start already running is ok" "$(jqt '.ok and .data.already_running==true')"
api "stop my.dotted"
check "stop" "$(jqt '.ok and .data.stopped==true and .data.session=="my-dotted"')"
grep -q "^my-dotted	" "$LIST"; [[ $? -ne 0 ]]; check "stopped session dropped from autostart list" $?
old_pid="$(tmux display-message -p -t "=demo-app2:" '#{pane_pid}')"
D2="$(tmux display-message -p -t "=demo-app2:" '#{pane_current_path}')"
SID=11111111-2222-3333-4444-555555555555
mkdir -p "$HOME/.claude/projects/${D2//[\/.]/-}" && echo '{}' >"$HOME/.claude/projects/${D2//[\/.]/-}/$SID.jsonl"
api "restart demo-app2"
check "restart resumes the same conversation" "$(jqt '.ok and .data.restarted and .data.session=="demo-app2" and .data.resumed and .data.conversation=="'$SID'"')"
tmux list-panes -t "=demo-app2" -F '#{pane_start_command}' | grep -q -- "--resume $SID"; check "restarted with --resume <id>" $?
[[ "$(tmux display-message -p -t "=demo-app2:" '#{pane_pid}' 2>/dev/null)" != "$old_pid" ]]; check "restart made a new process" $?
grep -q "^demo-app2	" "$LIST"; check "restarted session still in autostart list" $?
api "keys demo-app2 Enter"
check "keys sends and returns the screen" "$(jqt '.ok and .data.session=="demo-app2" and (.data.text|type)=="string"')"
for bad in "keys demo-app2" "keys demo-app2 ls" "keys demo-app2 C-c" "keys -x Enter" "keys demo-app2 1 2 3 4 5 6"; do
  api "$bad"
  check "keys refuses '$bad'" "$(jqt '.ok==false and .error.code=="forbidden"')"
done
tmux send-keys -t "=demo-app2:" "echo working... esc to interrupt" Enter; sleep 0.5
api "restart demo-app2"
check "restart refuses while Claude is working" "$(jqt '.error.code=="session_busy"')"
api "restart demo-app2 --force"
check "restart --force goes ahead" "$(jqt '.ok and .data.restarted')"
api "restart demo-app2 --now"
check "restart refuses other options" "$(jqt '.error.code=="forbidden"')"
api "restart nope"
check "restart missing session" "$(jqt '.error.code=="invalid_name"')"
api "restart -x"
check "restart refuses -x" "$(jqt '.error.code=="forbidden"')"
api "start nope"
check "start missing folder" "$(jqt '.error.code=="invalid_name"')"

echo "open"
mkdir -p "$HOME/projects/demo-app" && git -C "$HOME/projects/demo-app" init -q &&
  git -C "$HOME/projects/demo-app" remote add origin https://github.com/someone-else/demo-app.git
api "repos --refresh"
check "folder of another repo isn't 'On server'" "$(jqt '(.data.repos[] | select(.full_name=="demo-user/demo-app") | .local)==false')"
api "open demo-user/demo-app --start"
check "open refuses a folder holding another repo" "$(jqt '.error.code=="repo_exists"')"
rm -rf "$HOME/projects/demo-app"
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
api "clone-status demo-user/demo-app"
check "clone-status running" "$(jqt '.ok and .data.state=="running"')"
api "repos"
check "repos marks it cloning, not local" "$(jqt '(.data.repos[] | select(.full_name=="demo-user/demo-app") | .cloning==true and .local==false)')"
pkill -f -- '--clone-worker' 2>/dev/null; sleep 1
api "clone-status demo-user/demo-app"
check "killed clone reported failed" "$(jqt '.ok and .data.state=="failed"')"
api "clone-status demo-user/demo-app"
check "then forgotten" "$(jqt '.ok and .data.state=="none"')"
rm -rf "$HOME/projects/demo-app"

echo "claude-autostart (reboot)"
"$HOME/.local/bin/claude-autostart" save >/dev/null
tmux kill-server 2>/dev/null; sleep 0.5
"$HOME/.local/bin/claude-autostart" restore >/dev/null
tmux has-session -t "=org-app" 2>/dev/null && tmux has-session -t "=demo-app2" 2>/dev/null; check "autostart restores phone-started sessions" $?
d="$(awk -F'\t' '$1 == "org-app" {print $2}' "$LIST")"; t="$HOME/.claude/projects/${d//[\/.]/-}"
mkdir -p "$t" && touch "$t/11111111-2222-3333-4444-555555555555.jsonl"
tmux kill-server 2>/dev/null; sleep 0.5
"$HOME/.local/bin/claude-autostart" restore >/dev/null
tmux list-panes -t "=org-app" -F '#{pane_start_command}' | grep -q -- '--resume 11111111-2222-3333-4444-555555555555'; check "autostart resumes the last conversation" $?

echo "stop the last session drops it from autostart"
tmux kill-server 2>/dev/null; sleep 0.3
api "start org-app"
"$HOME/.local/bin/claude-autostart" save >/dev/null
api "stop org-app"
grep -q "^org-app	" "$LIST"; [[ $? -ne 0 ]]; check "last session removed from autostart list" $?
api "start demo-app2"

echo "claude login"
api "login-claude-start"
check "login url returned" "$(jqt '.ok and (.data.url | startswith("https://claude.ai/oauth/authorize"))')"
api "login-claude-code" "bad-code-123"
check "bad code -> not_logged_in_claude" "$(jqt '.error.code=="not_logged_in_claude"')"
api "login-claude-start"
"$HOME/.local/bin/claude-autostart" save >/dev/null
grep -q "^claude-login	" "$LIST"; [[ $? -ne 0 ]]; check "login session not saved by autostart" $?
api "login-claude-code" "good-code-123"
check "good code -> logged in" "$(jqt '.ok and .data.logged_in')"
api "status"
check "status shows claude + github" "$(jqt '.data.claude.logged_in and .data.github.logged_in and .data.github.missing_scopes==[]')"
api "login-claude-start"
api "login-claude-code" "bad-code-456"
check "old credentials don't fake a success" "$(jqt '.error.code=="not_logged_in_claude"')"
grep -q "^claude-login	" "$LIST"; [[ $? -ne 0 ]]; check "failed login not in autostart list" $?

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
cp "$HOME/.config/claude-launcher/config" "$WORK/config.bak"
echo 'FOO=$UNSET_VAR_FOR_TEST' >>"$HOME/.config/claude-launcher/config"
api "status"
check "broken config still gives one JSON error" "$(jqt '.ok==false and .error.code=="internal"')"
cp "$WORK/config.bak" "$HOME/.config/claude-launcher/config"

echo "interactive menu still runs"
OUT="$(printf '2\nq\n' | "$HOME/bin/claude-setup.sh" 2>&1)"
[[ "$OUT" == *"What are you working on?"* && "$OUT" == *"example-org/org-app"* && "$OUT" == *"Running sessions on the Pi:"* ]]
check "menu lists repos and exits on q" $?

echo "run"
api "run" $'\n\necho hi'
check "run is off by default" "$(jqt '.ok==false and .error.code=="run_disabled"')"
api "run echo"
check "run takes no arguments" "$(jqt '.error.code=="forbidden"')"
echo 'ALLOW_RUN=1' >>"$HOME/.config/claude-launcher/config"
api "run" $'\n\necho hi; pwd'
check "run returns output" "$(jqt '.ok and .data.exit_code==0 and .data.output=="hi\n'"$HOME"'"')"
api "run" $'\n\nexit 3'
check "run reports the exit code" "$(jqt '.ok and .data.exit_code==3')"
api "run" $'\n1\nsleep 5'
check "run timeout stops it" "$(jqt '.data.exit_code==124 and (.data.output|test("stopped after 1 s"))')"
api "run" $'pw-ok\n\nsudo echo root-ok'
check "run feeds the sudo password" "$(jqt '.data.exit_code==0 and .data.output=="root-ok"')"
api "run" $'pw-bad\n\nsudo echo root-ok'
check "wrong sudo password fails" "$(jqt '.data.exit_code!=0')"
grep -q "pw-ok" "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "sudo password not logged" $?
sed -i '/^ALLOW_RUN=1$/d' "$HOME/.config/claude-launcher/config"

echo "env hook"
mkdir -p "$HOME/.config/claude-launcher" "$HOME/.claude"
printf "export DEMO_TOKEN='hook-ok'\nexport OTHER_KEY='two'\n" >"$HOME/.config/claude-launcher/env"
echo '{"permissions":{"allow":["Bash(ls)"]},"env":{"MINE":"keep"}}' >"$HOME/.claude/settings.json"
api "status"
S="$HOME/.claude/settings.json"
jq -e '.env.DEMO_TOKEN=="hook-ok" and .env.OTHER_KEY=="two" and .env.MINE=="keep" and .permissions.allow[0]=="Bash(ls)"' "$S" >/dev/null
check "tokens mirrored into Claude's settings env, the rest kept" $?
[[ "$(stat -c %a "$S")" == 600 ]]; check "settings.json made private (600)" $?
printf "export DEMO_TOKEN='hook-ok'\n" >"$HOME/.config/claude-launcher/env"; api "status"
jq -e '(.env.OTHER_KEY|not) and .env.MINE=="keep"' "$S" >/dev/null; check "a removed token is taken out; your own env kept" $?
echo 'not json' >"$S"; api "status"
[[ "$(cat "$S")" == "not json" ]]; check "an unparsable settings.json is left alone" $?
rm -f "$HOME/.config/claude-launcher/env" "$S" "$HOME/.config/claude-launcher/settings-env-names"

echo "custom keys"
api "set-secret ACME_API_KEY" "abc123"
check "custom key saved" "$(jqt '.ok and .data.saved=="ACME_API_KEY"')"
grep -q "^export ACME_API_KEY='abc123'$" "$HOME/.config/claude-launcher/env"; check "custom key in the env file" $?
jq -e '.env.ACME_API_KEY=="abc123"' "$HOME/.claude/settings.json" >/dev/null; check "custom key reaches Claude's settings env" $?
api "status"; check "status lists custom key names (not values)" "$(jqt '.data.custom==["ACME_API_KEY"]')"
for bad in "set-secret PATH" "set-secret LD_PRELOAD" "set-secret CLAUDE_CODE_TOKEN" "set-secret acme_key" "set-secret NODE_OPTIONS" "set-secret"; do
  api "$bad" "x"; check "set-secret refuses '$bad'" "$(jqt '.ok==false')"
done
api "set-secret ACME_API_KEY" "it's"; check "set-secret refuses a single quote" "$(jqt '.error.code=="invalid_name"')"
api "remove-secret CLOUDFLARE_API_TOKEN"; check "remove-secret only removes custom keys" "$(jqt '.ok==false')"
api "remove-secret ACME_API_KEY"; check "custom key removed" "$(jqt '.ok and .data.removed=="ACME_API_KEY"')"
api "set-secret TS_AUTHKEY" "tskey-auth-abc"; check "TS_AUTHKEY accepted" "$(jqt '.ok and .data.saved=="TS_AUTHKEY"')"
api "remove-secret TS_AUTHKEY"; check "TS_AUTHKEY removed" "$(jqt '.ok')"
grep -q ACME_API_KEY "$HOME/.config/claude-launcher/env" "$HOME/.claude/settings.json"; [[ $? -ne 0 ]]; check "removed from env file and settings" $?
grep -q abc123 "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "custom key value never logged" $?
SF="$HOME/.config/claude-launcher/secret-files/GOOGLE_APPLICATION_CREDENTIALS.json"
api "set-secret-file GOOGLE_APPLICATION_CREDENTIALS" "$(printf '{"type":"service_account","private_key":"sf-secret-xyz"}' | base64 -w 76)"
check "set-secret-file saves a JSON file" "$(jqt '.ok and .data.saved=="GOOGLE_APPLICATION_CREDENTIALS" and .data.file')"
[[ "$(stat -c %a "$SF")" == 600 && "$(jq -r .private_key "$SF")" == sf-secret-xyz ]]; check "secret file saved, mode 600" $?
[[ "$(stat -c %a "${SF%/*}")" == 700 ]]; check "secret-files dir is 700" $?
jq -e --arg p "$SF" '.env.GOOGLE_APPLICATION_CREDENTIALS==$p' "$HOME/.claude/settings.json" >/dev/null; check "env var holds the file's path" $?
api "status"; check "status lists the file key with custom keys" "$(jqt '.data.custom|index("GOOGLE_APPLICATION_CREDENTIALS")')"
api "set-secret-file ACME_KEY_FILE" "$(printf 'not json' | base64)"; check "set-secret-file refuses non-JSON" "$(jqt '.ok==false and .error.code=="invalid_name"')"
[[ ! -e "$HOME/.config/claude-launcher/secret-files/ACME_KEY_FILE.json" ]]; check "refused file not saved" $?
api "set-secret-file ACME_KEY_FILE" ""; check "set-secret-file refuses an empty file" "$(jqt '.ok==false')"
for bad in "set-secret-file CLAUDE_X_FILE" "set-secret-file acme_key_file" "set-secret-file PATH" "set-secret-file"; do
  api "$bad" "$(printf '{}' | base64)"; check "set-secret-file refuses '$bad'" "$(jqt '.ok==false')"
done
api "remove-secret GOOGLE_APPLICATION_CREDENTIALS"; check "file key removed" "$(jqt '.ok')"
[[ ! -e "$SF" ]]; check "remove-secret deletes the saved file" $?
grep -q GOOGLE_APPLICATION_CREDENTIALS "$HOME/.config/claude-launcher/env"; [[ $? -ne 0 ]]; check "file key removed from env file" $?
grep -q sf-secret-xyz "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "secret file contents never logged" $?
rm -f "$HOME/.config/claude-launcher/env"

echo "signing keys"
KT="$(command -v keytool || ls /home/*/.jdks/*/bin/keytool /usr/lib/jvm/*/bin/keytool 2>/dev/null | head -n 1)"
if [[ -n "$KT" ]]; then
  "$KT" -genkeypair -keystore "$WORK/up.jks" -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 -validity 30 \
    -dname CN=test -storepass s3cret-pw -keypass s3cret-pw >/dev/null 2>&1
  api "login-keystore GTG" "$(printf 'upload\nwrong-pw\nwrong-pw\n'; base64 -w0 "$WORK/up.jks")"
  check "signing key: wrong password refused" "$(jqt '.error.code=="not_logged_in"')"
  api "login-keystore GTG" "$(printf 'upload\ns3cret-pw\ns3cret-pw\n'; base64 -w0 "$WORK/up.jks")"
  check "signing key saved" "$(jqt '.ok and .data.saved=="GTG" and .data.alias=="upload"')"
  cmp -s "$WORK/up.jks" "$HOME/.config/claude-launcher/keystores/GTG.jks" && [[ "$(stat -c %a "$HOME/.config/claude-launcher/keystores/GTG.jks")" == 600 ]]
  check "keystore stored intact, mode 600" $?
  jq -e '.env.GTG_KEY_ALIAS=="upload" and .env.GTG_KEYSTORE_PASSWORD=="s3cret-pw" and (.env.GTG_KEYSTORE_FILE|endswith("/keystores/GTG.jks"))' "$HOME/.claude/settings.json" >/dev/null
  check "signing key reaches Claude's settings env" $?
  api "status"; check "status lists signing keys" "$(jqt '.data.keystores==["GTG"]')"
  grep -q "s3cret-pw" "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "keystore password never logged" $?
  api "remove-keystore GTG"; check "signing key removed" "$(jqt '.ok and .data.removed=="GTG"')"
  [[ ! -e "$HOME/.config/claude-launcher/keystores/GTG.jks" ]] && ! grep -q GTG_ "$HOME/.config/claude-launcher/env"; check "file and variables gone" $?
fi
echo "cluster settings"
api "cluster-config"; check "cluster defaults" "$(jqt '.data == {max_parallel:3, handback:true, handback_pct:95}')"
api "cluster-config-set max_parallel 7"; check "set parallel" "$(jqt '.ok and .data.max_parallel==7')"
api "cluster-config-set handback false"; check "set handback" "$(jqt '.ok and .data.handback==false and .data.max_parallel==7')"
api "cluster-config-set handback_pct 80"; api "cluster-config"; check "settings persist" "$(jqt '.data == {max_parallel:7, handback:false, handback_pct:80}')"
for bad in "max_parallel 11" "max_parallel 0" "handback maybe" "handback_pct 49" "handback_pct 101" "color red" "max_parallel"; do
  api "cluster-config-set $bad"; check "cluster-config-set $bad refused" "$(jqt '.ok == false')"
done
echo "apple developer key"
P8="$(printf -- '-----BEGIN PRIVATE KEY-----\nMIGTfake\n-----END PRIVATE KEY-----\n' | base64 -w0)"
api "login-apple" "$(printf 'BADKEY\n69a6de70-8c3e-4b1e-9f1a-0123456789ab\n\n'; echo "$P8")"; check "apple: bad key id refused" "$(jqt '.error.code=="invalid_name"')"
api "login-apple" "$(printf 'ABC1234567\n69a6de70-8c3e-4b1e-9f1a-0123456789ab\nTEAM123456\n'; echo "$P8")"; check "apple key saved" "$(jqt '.ok and .data.key_id=="ABC1234567"')"
[[ "$(stat -c %a "$HOME/.config/claude-launcher/apple/AuthKey.p8")" == 600 ]]; check "apple key file mode 600" $?
jq -e '.env.APPLE_API_KEY_ID=="ABC1234567" and .env.APPLE_TEAM_ID=="TEAM123456" and (.env.APPLE_API_KEY_FILE|endswith("/apple/AuthKey.p8"))' "$HOME/.claude/settings.json" >/dev/null; check "apple vars reach Claude's settings env" $?
api "status"; check "status reports apple" "$(jqt '.data.apple==true')"
api "remove-apple"; check "apple key removed" "$(jqt '.ok')"
[[ ! -e "$HOME/.config/claude-launcher/apple/AuthKey.p8" ]] && ! grep -q APPLE_ "$HOME/.config/claude-launcher/env"; check "apple file and variables gone" $?
api "login-keystore gtg" "x"; check "signing key name must be upper case" "$(jqt '.error.code=="forbidden"')"
rm -f "$HOME/.config/claude-launcher/env"

echo "youtube"
api "youtube-login-start" $'not-a-client-id\nsecret1234567'
check "youtube: bad client ID refused" "$(jqt '.error.code=="invalid_name"')"
api "youtube-login-poll"; check "youtube: poll without a sign-in in progress" "$(jqt '.error.code=="not_logged_in"')"
api "youtube-login-start extra"; check "youtube-login-start takes no arguments" "$(jqt '.error.code=="forbidden"')"
python3 -m py_compile "$SERVER/youtube-upload"; check "youtube-upload is valid Python" $?
OUT="$(env -u YOUTUBE_CLIENT_ID python3 "$SERVER/youtube-upload" /etc/hostname --title t 2>&1)"
[[ "$OUT" == *"connect YouTube in the cLaudeRC app"* ]]; check "youtube-upload explains missing credentials" $?

echo "mcp, plugins, disconnect"
mkdir -p "$HOME/.cache/claude-launcher"
printf 'Checking MCP server health…\n\nclaude.ai Docs: https://example.com/mcp - ✔ Connected\nplugin:gh:github: https://api.example.com/mcp/ (HTTP) - ✘ Failed to connect\nmine: uv run --script /x/server.py --token SECRET - ✔ Connected\n' >"$HOME/.cache/claude-launcher/mcp-list.txt"
echo '{"mcpServers":{"mine":{}}}' >"$HOME/.claude.json"
api "mcp"
check "mcp lists cached servers with scope and health" "$(jqt '.ok and (.data.servers|length)==3 and .data.servers[0].scope=="claude.ai" and .data.servers[0].label=="Docs" and .data.servers[1].scope=="plugin" and .data.servers[1].health=="failed" and .data.servers[2].scope=="user"')"
check "mcp never shows stdio arguments (they can hold secrets)" "$(jqt '.data.servers[2].target=="uv run" and (tostring|test("SECRET")|not)')"
api "mcp-auth-start" "bad name; id"; check "mcp sign-in rejects a bad server name" "$(jqt '.error.code=="invalid_name"')"
api "mcp-auth-finish" "https://evil.example/callback?code=x"; check "mcp sign-in takes only a localhost callback" "$(jqt '.error.code=="invalid_name"')"
api "mcp-auth-cancel"; check "mcp sign-in cancel" "$(jqt '.ok')"
api "repo-edit rename demo-user/demo-app demo-app2"; check "repo rename" "$(jqt '.ok and .data.value=="demo-app2"')"
api "repo-edit visibility demo-user/demo-app public"; check "repo visibility" "$(jqt '.ok and .data.done=="visibility"')"
api "repo-edit visibility demo-user/demo-app secret"; check "repo visibility only private/public" "$(jqt '.error.code=="forbidden"')"
api "repo-edit rename demo-user/demo-app -x"; check "repo rename rejects a bad name" "$(jqt '.error.code=="forbidden"')"
touch "$STUB_STATE/gh_no_delete"
api "repo-edit delete demo-user/demo-app"; check "repo delete without delete_repo -> missing_scopes" "$(jqt '.error.code=="missing_scopes" and .error.missing==["delete_repo"]')"
rm -f "$STUB_STATE/gh_no_delete"
api "repo-edit delete demo-user/demo-app"; check "repo delete" "$(jqt '.ok and .data.done=="delete"')"
api "doctor-start"; check "doctor-start runs /doctor in its own session" "$(jqt '.ok and .data.session=="claude-doctor"')"
tmux list-panes -t "=claude-doctor" -F "#{pane_start_command}" | grep -q "claude /doctor"; check "doctor session command" $?
tmux kill-session -t "=claude-doctor" 2>/dev/null
api "plugins"
check "plugins: installed, available, marketplaces, version" "$(jqt '.ok and .data.installed[0].name=="demo" and .data.installed[0].enabled and (.data.available|length)==2 and .data.available[0].installed and (.data.available[1].installed|not) and .data.marketplaces[0].name=="market" and .data.claude_version=="2.1.999"')"
check "plugins without the update helper: no update field" "$(jqt '.data.installed[0] | has("update") | not')"
PD="$HOME/.claude/plugins"; mkdir -p "$PD/marketplaces/market/.claude-plugin"
echo '{"plugins":[{"name":"demo","source":{"source":"url","url":"https://example.com/demo.git","sha":"abcdef0123456789abcdef0123456789abcdef01"}}]}' >"$PD/marketplaces/market/.claude-plugin/marketplace.json"
echo '{"version":2,"plugins":{"demo@market":[{"scope":"user","version":"111111122222","gitCommitSha":"1111111222222233333334444444555555566666"}]}}' >"$PD/installed_plugins.json"
cp "$SERVER/claude-plugin-updates" "$HOME/.local/bin/"; chmod +x "$HOME/.local/bin/claude-plugin-updates"
api "plugins"
check "plugins: installed plugin carries update and latest" "$(jqt '.ok and .data.installed[0].update=="available" and .data.installed[0].latest=="abcdef012345" and .data.installed[0].enabled and (.data.available|length)==2')"
check "plugins: a non-skills plugin has no via/source" "$(jqt '.data.installed[0] | has("via") or has("source") | not')"
# A plugin from a marketplace the skills CLI manages: via/source come through (the unreachable API makes it "error").
echo '{"market":{"source":{"source":"github","repo":"demo-org/demo-skills"}}}' >"$PD/known_marketplaces.json"
mkdir -p "$HOME/.agents"
echo '{"version":3,"skills":{"one":{"source":"Demo-Org/Demo-Skills","sourceType":"github","updatedAt":"2026-01-01T00:00:00.000Z"}}}' >"$HOME/.agents/.skill-lock.json"
rm -f "$HOME/.config/claude-launcher/plugin-updates.json"
CLAUDERC_GITHUB_API=http://127.0.0.1:9 api "plugins"
check "plugins: a skills-managed plugin carries via and source" "$(jqt '.ok and .data.installed[0].via=="skills" and .data.installed[0].source=="Demo-Org/Demo-Skills" and .data.installed[0].update=="error"')"
rm -rf "$PD" "$HOME/.local/bin/claude-plugin-updates" "$HOME/.config/claude-launcher/plugin-updates.json" "$HOME/.agents"
api "sessions"; check "sessions carry preview/waiting/busy" "$(jqt '.ok and (.data.sessions|length) > 0 and (.data.sessions[0]|has("preview") and has("waiting") and has("busy"))')"
mkdir -p "$HOME/.config/claude-launcher"; printf "export VERCEL_TOKEN='abc'\n" >"$HOME/.config/claude-launcher/env"
api "disconnect vercel"; check "disconnect removes a service's token" "$(jqt '.ok and .data.disconnected=="vercel"')"
grep -q VERCEL_TOKEN "$HOME/.config/claude-launcher/env"; [[ $? -ne 0 ]]; check "token gone from the env file" $?
api "disconnect github"; check "disconnect only takes token services" "$(jqt '.error.code=="forbidden"')"
api "login-token youtube" "x"; check "youtube can't be set with login-token" "$(jqt '.ok==false')"
rm -f "$HOME/.config/claude-launcher/env" "$HOME/.claude.json"

echo "in-app chat"
api "chat-pin-status"; check "chat PIN not set yet" "$(jqt '.ok and (.data.set|not)')"
api "chat-open demo-app2" "123456"; check "chat needs a PIN first" "$(jqt '.error.code=="pin_not_set"')"
api "chat-pin-set" "12ab"; check "PIN must be 6-12 digits" "$(jqt '.error.code=="invalid_name"')"
api "chat-pin-set" "482913"; check "PIN set" "$(jqt '.ok and .data.set')"
[[ "$(stat -c %a "$HOME/.config/claude-launcher/chat-pin")" == 600 ]] && grep -q '^PIN=482913$' "$HOME/.config/claude-launcher/chat-pin"; check "PIN file readable only on the server (600)" $?
api "chat-pin-set" $'111111\n000000'; check "changing the PIN needs the current one" "$(jqt '.error.code=="wrong_pin"')"
rm -f "$HOME/.local/state/claude-launcher/chat-pin-fails"
D2="$(tmux display-message -p -t "=demo-app2:" '#{pane_current_path}')"; TD="$HOME/.claude/projects/${D2//[\/.]/-}"; mkdir -p "$TD"
cat >"$TD/99999999-0000-0000-0000-000000000000.jsonl" <<'JL'
{"type":"user","uuid":"u1","timestamp":"2026-09-29T10:00:00Z","message":{"role":"user","content":"Add a forecast screen"}}
{"type":"user","uuid":"u0","timestamp":"2026-09-29T10:00:01Z","message":{"role":"user","content":"<command-name>/clear</command-name>"}}
{"type":"assistant","uuid":"a1","timestamp":"2026-09-29T10:00:05Z","message":{"role":"assistant","content":[{"type":"text","text":"On it."},{"type":"tool_use","id":"t1","name":"Bash","input":{"command":"npm test"}}]}}
{"type":"user","uuid":"u2","timestamp":"2026-09-29T10:00:09Z","message":{"role":"user","content":[{"type":"tool_result","content":"ok"}]}}
{"type":"attachment","uuid":"q1","timestamp":"2026-09-29T10:00:10Z","attachment":{"type":"queued_command","prompt":"also add tests"}}
JL
touch "$TD/99999999-0000-0000-0000-000000000000.jsonl"
api "chat-history demo-app2" "482913"
check "chat history: your text, Claude's text, tool lines, messages sent mid-turn; a slash command shown as typed; other system lines left out" "$(jqt '.ok and ([.data.messages[] | .role] == ["user","user","assistant","tool","user"]) and .data.messages[0].text=="Add a forecast screen" and .data.messages[1].text=="/clear" and .data.messages[3].text=="Bash: npm test" and .data.messages[4].text=="also add tests"')"
printf '%s\n' '{"type":"user","uuid":"b1","timestamp":"2026-09-29T10:00:11Z","message":{"role":"user","content":"<bash-input>echo hello</bash-input>"}}' '{"type":"user","uuid":"b2","timestamp":"2026-09-29T10:00:12Z","message":{"role":"user","content":"<bash-stdout>hello</bash-stdout><bash-stderr></bash-stderr>"}}' >>"$TD/99999999-0000-0000-0000-000000000000.jsonl"
api "chat-history demo-app2" "482913"; check "chat history: a ! command shows as typed, then its output" "$(jqt '.data.messages[-2].text=="!echo hello" and .data.messages[-2].role=="user" and (.data.messages[-1].text|contains("hello"))')"
mkdir -p "$HOME/.claude/skills/demoskill"; printf -- '---\nname: demoskill\ndescription: >\n  Does a demo thing.\n---\n' >"$HOME/.claude/skills/demoskill/SKILL.md"
api "chat-commands demo-app2" "482913"; check "chat-commands lists your skills with their hint" "$(jqt '.ok and (.data.commands | map(select(.name=="/demoskill" and .hint=="Does a demo thing.")) | length)==1')"
api "chat-send demo-app2" $'482913\nline one\nline two'; check "chat send" "$(jqt '.ok and .data.sent')"
api "chat-history demo-app2" "482913"; check "chat history carries Claude's permission mode" "$(jqt '.ok and (.data.mode | IN("default","auto","plan","edits","bypass"))')"
tmux send-keys -t "=demo-app2:" -l "⏸ plan mode on (shift+tab to cycle)"; sleep 0.3
api "chat-history demo-app2" "482913"; check "mode read from the status line" "$(jqt '.data.mode=="plan"')"
api "keys demo-app2 BTab"; check "Shift+Tab is an allowed key" "$(jqt '.ok')"
tmux send-keys -t "=demo-app2:" C-u; tmux send-keys -t "=demo-app2:" -l "❯ 1. Yes, enable auto mode"; sleep 0.3
api "chat-history demo-app2" "482913"; check "a numbered menu on screen counts as a question" "$(jqt '.data.waiting==true')"
tmux send-keys -t "=demo-app2:" C-u; tmux send-keys -t "=demo-app2:" -l "clear"; tmux send-keys -t "=demo-app2:" Enter; sleep 0.3
TF="$TD/99999999-0000-0000-0000-000000000000.jsonl"
echo '{"type":"assistant","uuid":"q9","timestamp":"2026-09-29T10:02:00Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_ask","name":"AskUserQuestion","input":{"questions":[{"question":"Colour?","header":"Colour","multiSelect":false,"options":[{"label":"Red","description":"warm"},{"label":"Blue","description":"cool"}]}]}}]}}' >>"$TF"
tmux send-keys -t "=demo-app2:" C-u; tmux send-keys -t "=demo-app2:" -l "❯ 1. Red"; sleep 0.3
api "chat-history demo-app2" "482913"
check "a pending AskUserQuestion comes back as data" "$(jqt '.data.waiting and (.data.ask|length)==1 and .data.ask[0].question=="Colour?" and (.data.ask[0].options|length)==2 and .data.ask[0].options[1].label=="Blue"')"
echo '{"type":"user","uuid":"q10","timestamp":"2026-09-29T10:02:05Z","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_ask","content":"ok"}]}}' >>"$TF"
api "chat-history demo-app2" "482913"; check "an answered question is gone" "$(jqt '.data.ask == null')"
tmux send-keys -t "=demo-app2:" C-u; tmux send-keys -t "=demo-app2:" -l "clear"; tmux send-keys -t "=demo-app2:" Enter; sleep 0.3
api "chat-send no-such-proj" $'482913\nhi'; check "chat send to a stopped session fails once, with one JSON line" "$([[ "$(wc -l <<<"$OUT")" == 1 ]] && jqt '.ok==false' || echo 1)"
api "chat-send demo-app2" $'000000\nhi'; check "chat send with a wrong PIN refused" "$(jqt '.error.code=="wrong_pin"')"
for _ in 1 2 3 4; do api "chat-open demo-app2" "000000"; done
check "5 wrong PINs lock chat" "$(jqt '.error.code=="chat_locked"')"
api "chat-open demo-app2" "482913"; check "locked even with the right PIN" "$(jqt '.error.code=="chat_locked"')"
rm -f "$HOME/.config/claude-launcher/chat-locked"
api "chat-open demo-app2" "482913"; check "deleting the lock file on the server unlocks" "$(jqt '.ok')"
grep -q "482913\|line one" "$HOME/.local/state/claude-launcher/api.log" "$HOME/.local/state/claude-launcher/chat.log"; [[ $? -ne 0 ]]; check "PIN and message text never logged" $?
grep -q "send" "$HOME/.local/state/claude-launcher/chat.log"; check "chat access log records opens and sends" $?
api "chat-history demo-app2 extra"; check "chat-history takes one session" "$(jqt '.error.code=="forbidden"')"
api "upload demo-app2" "$(printf '482913\n../../etc/pass wd.png\n'; printf 'hello image' | base64 -w0)"
check "upload lands in <project>/uploads with a safe name" "$(jqt '.ok and .data.path=="uploads/pass_wd.png" and .data.bytes==11')"
[[ "$(cat "$D2/uploads/pass_wd.png")" == "hello image" ]]; check "uploaded bytes intact" $?
api "upload demo-app2" "$(printf '000000\nx.png\n'; printf 'x' | base64 -w0)"; check "upload needs the PIN" "$(jqt '.error.code=="wrong_pin"')"
rm -f "$HOME/.local/state/claude-launcher/chat-pin-fails"
api "chat-log"; check "chat log lists opens/sends/uploads, newest first" "$(jqt '.ok and .data[0].action=="upload" and any(.data[]; .action=="send")')"
printf '%s\n' '{"type":"assistant","uuid":"a9","timestamp":"2026-09-29T10:01:00Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"t9","name":"SendUserFile","input":{"files":["'"$D2"'/uploads/pass_wd.png"],"caption":"the shot"}}]}}' >>"$TD/99999999-0000-0000-0000-000000000000.jsonl"
api "chat-history demo-app2" "482913"
check "chat history shows a file Claude sent as a card" "$(jqt '.ok and (.data.messages[-1] | .role=="file" and .text=="the shot" and .files==["'"$D2"'/uploads/pass_wd.png"])')"
api "chat-file demo-app2" "$(printf '482913\n%s' "$D2/uploads/pass_wd.png")"
check "chat-file returns the file" "$(jqt '.ok and .data.name=="pass_wd.png" and .data.bytes==11 and (.data.data|@base64d)=="hello image"')"
head -c 300000 /dev/urandom >"$D2/uploads/big.bin"
api "chat-file demo-app2" "$(printf '482913\n%s' "$D2/uploads/big.bin")"
check "chat-file serves a file over the 128 KB argument limit" "$(jqt '.ok and .data.bytes==300000 and (.data.data|length)==400000')"
rm -f "$D2/uploads/big.bin"
api "chat-file demo-app2" "$(printf '482913\n%s' "$D2/uploads/../../../../etc/hostname")"
check "chat-file refuses files outside the project" "$(jqt ".ok | not")"
api "chat-file demo-app2" "$(printf '482913\n/etc/hostname')"
check "chat-file refuses /etc" "$(jqt '.error.code=="forbidden"')"
ln -sf /etc/hostname "$D2/uploads/link.png"
api "chat-file demo-app2" "$(printf '482913\n%s' "$D2/uploads/link.png")"
check "chat-file refuses a link out of the project" "$(jqt '.error.code=="forbidden"')"
echo "sent outside" >"$HOME/sent-by-claude.txt"
api "chat-file demo-app2" "$(printf '482913\n%s' "$HOME/sent-by-claude.txt")"; check "chat-file refuses an outside file Claude didn't send" "$(jqt '.error.code=="forbidden"')"
echo '{"type":"assistant","uuid":"a10","timestamp":"2026-09-29T10:03:00Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"t10","name":"SendUserFile","input":{"files":["'"$HOME"'/sent-by-claude.txt"],"caption":"x"}}]}}' >>"$TD/99999999-0000-0000-0000-000000000000.jsonl"
api "chat-file demo-app2" "$(printf '482913\n%s' "$HOME/sent-by-claude.txt")"; check "chat-file serves an outside file Claude sent in this conversation" "$(jqt '.ok and .data.name=="sent-by-claude.txt"')"
rm -f "$HOME/sent-by-claude.txt"
api "chat-file demo-app2" "$(printf '000000\n%s' "$D2/uploads/pass_wd.png")"
check "chat-file needs the PIN" "$(jqt '.error.code=="wrong_pin"')"
rm -f "$HOME/.local/state/claude-launcher/chat-pin-fails"
rm -f "$HOME/.config/claude-launcher/chat-pin"

echo "token services"
api "status"
check "status lists token services" "$(jqt '.data.services | (.vercel and .b2 and .gcp and .firebase and .cloudflare) and (.vercel.logged_in|not)')"
api "login-token vercel" "short"
check "token login: malformed token" "$(jqt '.error.code=="invalid_name"')"
api "login-token vercel" "abcdefghijklmnopqrstuvwxyz0123"
check "token login: provider rejects (no network) → nothing saved" "$(jqt '.error.code=="not_logged_in"')"
grep -q VERCEL_TOKEN "$HOME/.config/claude-launcher/env" 2>/dev/null; [[ $? -ne 0 ]]; check "rejected token not saved" $?
api "login-token cloudflare" "cfat_abcdefghijklmnopqrstuvwxyz0123456789ABCD"
check "cloudflare account token needs its account ID" "$(jqt '.error.code=="invalid_name" and (.error.message|test("Account ID"))')"
api "login-token cloudflare" $'cfat_abcdefghijklmnopqrstuvwxyz0123456789ABCD\nnot-an-id'
check "cloudflare account ID must be 32 hex" "$(jqt '.error.code=="invalid_name"')"
api "login-token mxroute" $'Mx8d989005f0cded8371b7d7271c50K1\n-evil.example\nuser'
check "mxroute server can't start with -" "$(jqt '.error.code=="invalid_name"')"
api "login-token b2" $'onlyonevalue'
check "b2 needs two valid values" "$(jqt '.error.code=="invalid_name"')"
KEY='{"type":"service_account","project_id":"demo-proj","private_key":"x","client_email":"bot@demo-proj.iam.gserviceaccount.com"}'
api "login-token gcp" "$KEY"
check "gcp key accepted" "$(jqt '.ok and .data.user=="bot@demo-proj.iam.gserviceaccount.com"')"
[[ "$(stat -c %a "$HOME/.config/claude-launcher/gcp-key.json")" == 600 ]]; check "key file is mode 600" $?
grep -q "^export CLOUDSDK_CORE_PROJECT='demo-proj'" "$HOME/.config/claude-launcher/env"; check "gcp project exported" $?
api "login-token googleplay" "$KEY"
check "google play: a key Google doesn't accept is refused" "$(jqt '.error.code=="invalid_name"')"
grep -q SUPPLY_JSON_KEY "$HOME/.config/claude-launcher/env"; [[ $? -ne 0 ]]; check "refused Play key not saved" $?
api "login-token firebase" '{"type":"user"}'
check "not a service account → refused" "$(jqt '.error.code=="invalid_name"')"
api "status"
check "status shows gcp connected with its account" "$(jqt '.data.services.gcp.logged_in and .data.services.gcp.detail=="bot@demo-proj.iam.gserviceaccount.com"')"
grep -q "demo-proj\|private_key" "$HOME/.local/state/claude-launcher/api.log"; [[ $? -ne 0 ]]; check "key never logged" $?

echo "claude-cmd"
api "claude-cmd" "doctor"
check "claude-cmd doctor, colours stripped" "$(jqt '.ok and .data.exit_code==0 and .data.output=="No installation issues found."')"
api "claude-cmd" "plugin install demo@market --scope user"
check "claude-cmd plugin install" "$(jqt '.ok and .data.output=="Installed demo@market"')"
for bad in "mcp add x -- bash -c id" "auth logout" "doctor; id" 'plugin install $(id)' "--dangerously-skip-permissions" "" "doctor extra"; do
  api "claude-cmd" "$bad"
  check "claude-cmd refuses '${bad:0:25}'" "$(jqt '.ok==false')"
done
api "claude-cmd doctor"
check "claude-cmd takes nothing on the command line" "$(jqt '.error.code=="forbidden"')"

echo "install-cli"
api "install-cli glab"
check "install-cli without the internet fails cleanly" "$(jqt '.ok==false and .error.code=="internal"')"
[[ ! -e "$HOME/.local/bin/glab.new" ]]; check "install-cli leaves nothing half-installed" $?

echo "skills-update"
LOCK="$HOME/.agents/.skill-lock.json"
api "skills-update demo-org/demo-skills"
check "skills-update without a lock file refused" "$(jqt '.ok==false and .error.code=="invalid_name"')"
mkdir -p "$HOME/.agents"
echo '{"version":3,"skills":{"one":{"source":"Demo-Org/Demo-Skills","sourceType":"github","installedAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z"},"two":{"source":"other-org/other","updatedAt":"2026-01-01T00:00:00.000Z"}}}' >"$LOCK"
api "skills-update demo-org/not-in-lock"
check "skills-update refuses a repo that isn't in the lock" "$(jqt '.ok==false and .error.code=="invalid_name"')"
OUT="$("$HOME/bin/claude-setup.sh" --api skills-update "bad repo" </dev/null)"
check "skills-update refuses bad arguments" "$(jqt '.ok==false and .error.code=="bad_args"')"
[[ ! -e "$STUB_STATE/npx-args" ]]; check "refused skills-update never ran npx" $?
STUB_NPX_FAIL=1 api "skills-update demo-org/demo-skills"
check "skills-update reports an npx failure with its last lines, colours stripped" "$(jqt '.ok==false and .error.code=="internal" and (.error.message | contains("repository not found") and (contains("\u001b") | not))')"
[[ "$(jq -r '.skills.one.updatedAt' "$LOCK")" == 2026-01-01T00:00:00.000Z ]]; check "failed skills-update leaves the lock alone" $?
api "skills-update demo-org/demo-skills"
check "skills-update runs (repo matched case-insensitively), colours stripped" "$(jqt '.ok and .data.updated=="demo-org/demo-skills" and (.data.output | contains("done") and (contains("\u001b") | not))')"
[[ "$(cat "$STUB_STATE/npx-args")" == "-y skills add demo-org/demo-skills -g -y -s *" ]]; check "skills-update passes the right arguments to npx" $?
[[ "$(jq -r '.skills.one.updatedAt' "$LOCK")" != 2026-01-01T00:00:00.000Z && "$(jq -r '.skills.two.updatedAt' "$LOCK")" == 2026-01-01T00:00:00.000Z ]]; check "stub moved only that repo's updatedAt" $?
rm -rf "$HOME/.agents" "$STUB_STATE/npx-args"

echo "self-update"
api "status"
check "status reports script_api, no commit yet, run off" "$(jqt '.data.script_api >= 5 and .data.commit == null and .data.run_enabled == false')"
SHA=0123456789abcdef0123456789abcdef01234567
mkdir -p "$WORK/raw/myGIGlife-claude/Claude-RC/$SHA"
ln -s "$SERVER" "$WORK/raw/myGIGlife-claude/Claude-RC/$SHA/server"
export CLAUDERC_RAW="file://$WORK/raw"
api "self-update $SHA"
check "self-update installs that commit" "$(jqt '.ok and .data.commit=="'$SHA'"')"
[[ "$(cat "$HOME/.config/claude-launcher/installed-commit")" == "$SHA" && -x "$HOME/claude-setup.sh" ]]; check "self-update recorded the commit" $?
api "status"
check "status reports the installed commit" "$(jqt '.data.commit=="'$SHA'"')"
api "self-update ${SHA/0123/9999}"
check "self-update of a missing commit fails cleanly" "$(jqt '.ok==false and .error.code=="internal"')"
unset CLAUDERC_RAW

echo "install.sh (no clone)"
IH="$WORK/installhome"; mkdir -p "$IH"
ssh-keygen -q -t ed25519 -N '' -C clauderc -f "$WORK/phone" </dev/null
echo old >"$IH/claude-setup.sh"
# Already set up, so the test never runs its sudo install.
mkdir -p "$IH/.local/bin" && touch "$IH/.local/bin/claude-autostart" && chmod +x "$IH/.local/bin/claude-autostart"
# Real curl, not the stub.
OUT="$(HOME="$IH" PATH=/usr/local/bin:/usr/bin:/bin CLAUDERC_BASE="file://$SERVER" bash -s -- "$(cat "$WORK/phone.pub")" <"$SERVER/install.sh" 2>&1)"
[[ -x "$IH/claude-setup.sh" && -x "$IH/bin/claude-launcher-api" && -x "$IH/bin/install-launcher-key.sh" && -x "$IH/.local/bin/youtube-upload" ]]; check "install.sh installs the scripts (and youtube-upload)" $?
[[ "$(cat "$IH/claude-setup.sh.bak")" == old ]]; check "install.sh backs up a changed claude-setup.sh" $?
grep -q "^restrict,command=\"$IH/bin/claude-launcher-api\" ssh-ed25519 " "$IH/.ssh/authorized_keys"; check "install.sh authorizes the key" $?
[[ "$OUT" == *"already set up"* && "$OUT" == *"Username: $(id -un)"* ]]; check "install.sh skips autostart and prints app details" $?

echo "team workers"
for bad in "worker-add" "worker-add ../x" "worker-add -x" "worker-add .x" "worker-add a b" "worker-set a" \
  "worker-set a color" "worker-remove" "worker-remove a/b" "worker-list extra" "worker-runs" "worker-login-start" \
  "worker-login-code a b" "worker-add a other" "worker-add a codex x"; do
  api "$bad"
  check "runner rejects: $bad" "$(jqt '.ok == false')"
done
echo "codex and gemini workers"
api "worker-add gpt codex" "Second opinion"
check "add codex worker" "$(jqt '.ok and .data.added == "gpt"')"
api "worker-add gem gemini" "Research"
check "add gemini worker" "$(jqt '.ok and .data.added == "gem"')"
api "worker-list"
check "kinds listed, not signed in" "$(jqt '([.data.workers[] | select(.name=="gpt" or .name=="gem") | .kind + (.signed_in|tostring)] | sort) == ["codexfalse","geminifalse"]')"
api "cluster"
check "cluster lists them without usage" "$(jqt '.data.accounts[] | select(.name=="gpt") | .kind=="codex" and .usage==null')"
api "worker-login-start gpt"
check "codex login gives the OpenAI link and asks for the pasted address" "$(jqt '.ok and (.data.url | startswith("https://auth.openai.com")) and .data.paste_url')"
api "worker-login-code gpt" "http://evil.example/auth/callback?code=good"
check "codex paste of another host refused" "$(jqt '.ok == false')"
api "worker-login-code gpt" "http://localhost:1455/auth/callback?code=good&state=xyz"
check "codex login finishes after the pasted address" "$(jqt '.ok and .data.logged_in')"
api "worker-list"
check "codex worker signed in" "$(jqt '.data.workers[] | select(.name=="gpt") | .signed_in')"
api "worker-login-start gem"
check "gemini has no browser sign-in" "$(jqt '.ok == false')"
api "worker-set-key gpt" "AIzaSyDemoDemoDemoDemoDemo1234567890"
check "key refused for a non-Gemini worker" "$(jqt '.ok == false')"
api "worker-set-key gem" "AQ.Ab8RNDemoDemoDemoDemoDemo-1234567890_x"
check "newer dotted key format accepted" "$(jqt '.ok')"
api "worker-set-key gem" "short"
check "bad key refused" "$(jqt '.ok == false')"
mkdir -p "$HOME/.config/claude-launcher/workers/gem/home/.gemini"; echo '{"x":1}' >"$HOME/.config/claude-launcher/workers/gem/home/.gemini/oauth_creds.json"
api "worker-set-key gem" "AIzaSyDemoDemoDemoDemoDemo1234567890"
check "gemini API key saved" "$(jqt '.ok and .data.saved == "gem"')"
[[ "$(stat -c %a "$HOME/.config/claude-launcher/workers/gem/home/gemini-api-key")" == 600 ]]; check "key file is mode 600" $?
[[ ! -e "$HOME/.config/claude-launcher/workers/gem/home/.gemini/oauth_creds.json" ]]; check "old Google login removed" $?
api "worker-list"
check "gemini worker signed in with the key" "$(jqt '.data.workers[] | select(.name=="gem") | .signed_in')"
grep -q "AIzaSyDemo" "$HOME/.local/state/claude-launcher/api.log" 2>/dev/null; [[ $? -ne 0 ]]; check "key is not in the api log" $?
api "worker-remove gpt"; api "worker-remove gem"
api "worker-list"
check "no workers yet" "$(jqt '.ok and (.data.workers | length == 0)')"
api "worker-add research" "Reads docs & \"quotes\", finds sources"
check "add worker" "$(jqt '.ok and .data.added == "research"')"
api "worker-add research" "again"
check "duplicate worker refused" "$(jqt '.ok == false')"
api "worker-list"
check "role stored verbatim, default mode, not signed in" "$(jqt '.data.workers[0] | .name=="research" and .role=="Reads docs & \"quotes\", finds sources" and .mode=="acceptEdits" and .signed_in==false')"
api "worker-set research mode" "bypassPermissions"
check "set mode" "$(jqt '.ok')"
api "worker-set research mode" "rm -rf"
check "bad mode refused" "$(jqt '.ok == false')"
api "worker-set research model" "claude-sonnet-5-5"; check "set model" "$(jqt '.ok')"
api "worker-set research model" "bad model!"; check "bad model refused" "$(jqt '.ok == false')"
api "worker-set research effort" "high"; check "set effort" "$(jqt '.ok')"
api "worker-set research effort" "turbo"; check "bad effort refused" "$(jqt '.ok == false')"
jq -e '.model=="claude-sonnet-5-5" and .effort=="high"' "$HOME/.config/claude-launcher/workers/research/meta.json" >/dev/null; check "model and effort in meta.json" $?
api "worker-set research role" "Docs"
api "worker-list"
check "mode and role saved" "$(jqt '.data.workers[0].mode=="bypassPermissions" and .data.workers[0].role=="Docs"')"
mkdir -p "$HOME/.config/claude-launcher/workers/research/tasks"
printf '{"id":"aaaa1111","task":"t1","status":"done","reply":"hello","started":100}' >"$HOME/.config/claude-launcher/workers/research/tasks/aaaa1111.json"
printf '{"id":"bbbb2222","task":"t2","status":"failed","error":"boom","started":200}' >"$HOME/.config/claude-launcher/workers/research/tasks/bbbb2222.json"
api "worker-runs research"
check "runs newest first" "$(jqt '.data.runs[0].id=="bbbb2222" and .data.runs[1].reply=="hello"')"
api "worker-runs nope"
check "runs of unknown worker refused" "$(jqt '.ok == false')"
api "worker-remove research"
check "remove worker" "$(jqt '.ok')"
api "worker-add ops" "Ops"
api "worker-login-start ops"
check "worker login gives a URL" "$(jqt '.ok and (.data.url | startswith("https://"))')"
grep -q "^worker-login-ops	" "$LIST"; [[ $? -ne 0 ]]; check "worker login not saved by autostart" $?
api "worker-login-code ops" "bad-code-789"
check "bad worker code refused" "$(jqt '.ok == false')"
api "worker-login-start ops"
api "worker-login-code ops" "good-code-789"
check "worker login succeeds" "$(jqt '.ok and .data.logged_in')"
[[ -s "$HOME/.config/claude-launcher/workers/ops/home/.credentials.json" ]]; check "creds landed in the worker's own dir" $?
api "worker-list"
check "worker shows signed in" "$(jqt '.data.workers[] | select(.name=="ops") | .signed_in')"
api "sessions"
grep -q "worker-login-ops" <<<"$OUT"; [[ $? -ne 0 ]]; check "login helper not listed as a session" $?
api "cluster-session demo-app2"
check "chat lists workers, none attached" "$(jqt '.ok and (.data.workers | map(select(.attached)) | length == 0) and (.data.workers | map(.name) | index("ops"))')"
api "cluster-attach demo-app2 ops on"
check "attach a worker to a chat" "$(jqt '.ok and .data.state=="on"')"
api "cluster-assign demo-app2 ops role" "Build the UI"
api "cluster-assign demo-app2 ops mode" "plan"
check "per-chat role and mode saved" "$(jqt '.ok')"
api "cluster-assign demo-app2 ops mode" "rm -rf"
check "bad per-chat mode refused" "$(jqt '.ok == false')"
api "cluster-session demo-app2"
check "attached with this chat's role and mode" "$(jqt '.data.workers[] | select(.name=="ops") | .attached and .role=="Build the UI" and .mode=="plan"')"
PD="$(readlink -f "$HOME/projects/demo-app2")"
TD2="$HOME/.config/claude-launcher/workers/ops/tasks"
printf '{"id":"cccc0001","worker":"ops","task":"open one","status":"done","branch":"cluster/ops/cccc0001","project":"%s","started":5}' "$PD" >"$TD2/cccc0001.json"
printf '{"id":"cccc0002","worker":"ops","task":"merged","status":"done","branch":"cluster/ops/cccc0002","closed":"merged","project":"%s","started":6}' "$PD" >"$TD2/cccc0002.json"
printf '{"id":"cccc0003","worker":"ops","task":"other project","status":"running","project":"/elsewhere","started":7}' >"$TD2/cccc0003.json"
printf '{"id":"cccc0004","worker":"ops","task":"going","status":"running","project":"%s","started":8}' "$PD" >"$TD2/cccc0004.json"
api "cluster-session demo-app2"
check "chat sees its own open work (running task, unmerged branch) only" "$(jqt '.data.tasks | map(.id) == ["cccc0004","cccc0001"]')"
rm -f "$TD2"/cccc000*.json
api "cluster-assign demo-app2 nobody role" "x"
check "role for an unknown worker refused" "$(jqt '.ok == false')"
api "worker-list"
check "the worker's own role is untouched" "$(jqt '.data.workers[] | select(.name=="ops") | .role=="Ops" and .mode=="acceptEdits"')"
api "cluster-assign demo-app2 ops role" ""
api "cluster-session demo-app2"
check "empty role goes back to the worker's own" "$(jqt '.data.workers[] | select(.name=="ops") | .role=="Ops" and .mode=="plan"')"
api "cluster-attach demo-app2 ops off"
api "cluster-session demo-app2"
check "detach" "$(jqt '.data.workers[] | select(.name=="ops") | .attached == false')"
api "cluster-attach nosuch ops on"; check "attach needs a running session" "$(jqt '.ok == false')"
api "cluster-attach demo-app2 nobody on"; check "attach needs a real worker" "$(jqt '.ok == false')"
for bad in "cluster-session" "cluster-session a b" "cluster-attach a" "cluster-attach a ops maybe" "cluster-attach ../x ops on" "cluster-assign a ops color" "cluster-assign a b"; do
  api "$bad"
  check "runner rejects: $bad" "$(jqt '.ok == false')"
done
api "worker-remove ops"
check "remove signed-in worker" "$(jqt '.ok')"
api "worker-list"
check "worker gone" "$(jqt '.data.workers | length == 0')"

echo "hosts"
ALOG="$HOME/.local/state/claude-launcher/api.log"
HD="$HOME/.config/claude-launcher/hosts"
cp "$HERE/stubs/ssh" "$HOME/.local/bin/ssh"   # (the migrate tests put their own ssh there later)
ssh-keygen -q -t ed25519 -N '' -C hostkey -f "$WORK/hostkey" </dev/null
ssh-keygen -q -t ed25519 -N '' -C hostkey2 -f "$WORK/hostkey2" </dev/null
ssh-keygen -q -t ed25519 -N '' -C userkey -f "$WORK/userkey" </dev/null
ssh-keygen -q -t ed25519 -N '' -C otherkey -f "$WORK/otherkey" </dev/null
ssh-keygen -q -t ed25519 -N 'key-pass-phrase' -C locked -f "$WORK/lockedkey" </dev/null
cp "$WORK/hostkey.pub" "$STUB_STATE/host_key.pub"
HFP="$(ssh-keygen -lf "$WORK/hostkey.pub" | awk '{print $2}')"
mkdir -p "$STUB_STATE/remote/.ssh"; cp "$WORK/userkey.pub" "$STUB_STATE/remote/.ssh/authorized_keys"
UK64="$(base64 -w0 "$WORK/userkey")"
HPW="Pw-Very-Secret-42"; echo "$HPW" >"$STUB_STATE/host_password"
add_in() { printf '%s\n' "$@"; }
nothing_new() { [[ ! -e "$HD/$1" && -z "$(find "$HD" -maxdepth 1 -name '.new-*' 2>/dev/null)" ]]; }
for bad in "host-list x" "host-probe x" "host-add" "host-add Web" "host-add -x" "host-add 1web" "host-add web extra" "host-add ../x" \
  "host-add $(printf 'a%.0s' {1..31})" "host-test" "host-test a b" "host-remove web_1" "host-session" "host-session a b" "host-session ../x" \
  "host-attach a web" "host-attach a web maybe" "host-attach ../x web on" "host-attach a Web on" "host-attach a web on x"; do
  api "$bad"
  check "forbidden: $bad" "$(jqt '.ok==false and .error.code=="forbidden"')"
done
api "host-list"
check "no hosts yet" "$(jqt '.ok and .data.hosts == []')"
api "host-probe" "$(add_in web.example.com 22)"
check "probe returns the fingerprint" "$(jqt ".ok and .data.fingerprint == \"$HFP\" and .data.keytype == \"ED25519\"")"
api "host-probe" "$(add_in 2001:db8::10 2222)"
check "probe an IPv6 address on another port" "$(jqt ".ok and .data.fingerprint == \"$HFP\"")"
[[ "$(cat "$STUB_STATE/keyscan-argv")" == $'2001:db8::10\n2222' ]]; check "keyscan got the address and port" $?
for bad in "bad host|22" "-oProxyCommand=x|22" "h.example.com|0" "h.example.com|70000" "h.example.com|22x" "h.example.com;id|22" "|"; do
  api "host-probe" "$(tr '|' '\n' <<<"$bad")"
  check "probe refuses: $bad" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
touch "$STUB_STATE/ssh-unreachable"
api "host-probe" "$(add_in web.example.com 22)"
check "probe: unreachable is an error" "$(jqt '.ok==false and .error.code=="unreachable"')"
api "host-add web" "$(add_in web.example.com 2222 deploy "$HFP" key "$UK64")"
check "add: unreachable is an error" "$(jqt '.ok==false and .error.code=="unreachable"')"
nothing_new web; check "...nothing saved" $?
rm -f "$STUB_STATE/ssh-unreachable"

api "host-add web" "$(add_in web.example.com 2222 deploy "$HFP" key "$UK64")"
check "add with a key" "$(jqt '.ok and .data.saved=="web"')"
[[ "$(stat -c %a "$HD")" == 700 && "$(stat -c %a "$HD/web")" == 700 && "$(stat -c %a "$HD/web/key")" == 600 && "$(stat -c %a "$HD/web/known_hosts")" == 600 ]]
check "hosts folder 700, key and known_hosts 600" $?
cmp -s "$HD/web/key" "$WORK/userkey"; check "the key is stored as sent" $?
[[ "$(cat "$HD/web/known_hosts")" == "[web.example.com]:2222 $(awk '{print $1, $2}' "$WORK/hostkey.pub")" ]]; check "known_hosts pins exactly the confirmed key" $?
grep -qx 'StrictHostKeyChecking=yes' "$STUB_STATE/ssh-argv" && grep -qx 'BatchMode=yes' "$STUB_STATE/ssh-argv" && grep -qx 'PasswordAuthentication=no' "$STUB_STATE/ssh-argv" &&
  grep -qx 'IdentitiesOnly=yes' "$STUB_STATE/ssh-argv" && grep -qx 'ConnectTimeout=10' "$STUB_STATE/ssh-argv" && [[ "$(tail -n 1 "$STUB_STATE/ssh-argv")" == true ]]
check "key login verified with the safe options" $?
api "host-list"
check "host-list shows it" "$(jqt ".data.hosts == [{name:\"web\",address:\"web.example.com\",port:2222,user:\"deploy\",auth:\"key\",fingerprint:\"$HFP\",added:.data.hosts[0].added}] and (.data.hosts[0].added | type) == \"number\"")"
grep -qF "${UK64:40:40}" <<<"$OUT"; [[ $? -ne 0 ]]; check "host-list has no key material" $?

api "host-add pw" "$(add_in web.example.com 22 deploy "$HFP" password wrong-password)"
check "add: wrong password refused" "$(jqt '.ok==false and .error.code=="auth_failed"')"
nothing_new pw; check "...nothing saved" $?
rm -f "$STUB_STATE/ssh-env-askpass"
api "host-add pw" "$(add_in web.example.com 22 deploy "$HFP" password "$HPW")"
check "add with a password" "$(jqt '.ok and .data.saved=="pw"')"
[[ -e "$STUB_STATE/ssh-env-askpass" ]]; check "the password went through askpass" $?
[[ "$(tail -n 1 "$STUB_STATE/remote/.ssh/authorized_keys")" == "$(ssh-keygen -y -f "$HD/pw/key" | awk '{print $1, $2}') clauderc-host-pw" ]]
check "the server's own new key was installed on the host" $?
[[ "$(jq -r .auth "$HD/pw/meta.json")" == password && "$(ls -A "$HD/pw" | sort | tr '\n' ' ')" == "key known_hosts meta.json " ]]; check "only key, known_hosts and meta are kept" $?
! grep -rqF "$HPW" "$HD" "$ALOG" "$STUB_STATE/ssh-argv"; check "the password is stored nowhere (hosts, api.log, ssh argv)" $?
grep -qF "${UK64:40:40}" "$ALOG"; [[ $? -ne 0 ]]; check "no key material in api.log" $?

cp "$WORK/hostkey2.pub" "$STUB_STATE/host_key.pub"
api "host-add moved" "$(add_in web.example.com 22 deploy "$HFP" key "$UK64")"
check "add: a changed host key is refused" "$(jqt '.ok==false and .error.code=="fingerprint_mismatch"')"
nothing_new moved; check "...nothing saved" $?
cp "$WORK/hostkey.pub" "$STUB_STATE/host_key.pub"
api "host-add bad" "$(add_in web.example.com 22 deploy "$HFP" key "$(printf 'not a private key at all' | base64 -w0)")"
check "add: not a key refused" "$(jqt '.ok==false and .error.code=="invalid_key"')"
api "host-add bad" "$(add_in web.example.com 22 deploy "$HFP" key "not base64!")"
check "add: not base64 refused" "$(jqt '.ok==false and .error.code=="invalid_key"')"
api "host-add bad" "$(add_in web.example.com 22 deploy "$HFP" key "$(base64 -w0 "$WORK/lockedkey")")"
check "add: a passphrase key is refused with a clear message" "$(jqt '.ok==false and .error.code=="invalid_key" and (.error.message | test("passphrase"))')"
api "host-add bad" "$(add_in web.example.com 22 deploy "$HFP" key "$(base64 -w0 "$WORK/otherkey")")"
check "add: a key the host doesn't accept is refused" "$(jqt '.ok==false and .error.code=="auth_failed"')"
nothing_new bad; check "...nothing saved after any of those" $?
for bad in "a b|22|deploy|$HFP|key" "web.example.com|22|Root|$HFP|key" "web.example.com|22|-oProxyCommand=x|$HFP|key" \
  "web.example.com|22|deploy|SHA256:short|key" "web.example.com|22|deploy|$HFP|token" "web.example.com|0|deploy|$HFP|key"; do
  api "host-add bad" "$(tr '|' '\n' <<<"$bad")"$'\n'"$UK64"
  check "add refuses: ${bad:0:40}" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
api "host-add bad" "$(add_in web.example.com 22 deploy "$HFP" password "")"
check "add refuses: no password" "$(jqt '.ok==false and .error.code=="invalid_name"')"
nothing_new bad; check "...nothing saved" $?
api "host-add web" "$(add_in web.example.com 22 deploy "$HFP" key "$UK64")"
check "add again is refused (remove it first)" "$(jqt '.ok==false and .error.code=="invalid_name"')"
[[ "$(jq -r .port "$HD/web/meta.json")" == 2222 ]]; check "...the existing host is untouched" $?
mkdir -p "$HD/.new-old.stale" && touch -d '30 minutes ago' "$HD/.new-old.stale"
api "host-add moved2" "$(add_in web.example.com 22 deploy "$HFP" key "$UK64")"
[[ ! -e "$HD/.new-old.stale" ]]; check "stale half-made host folders are cleaned up" $?
api "host-remove moved2"

api "host-test web"
check "host-test ok" "$(jqt '.ok and .data.ok')"
touch "$STUB_STATE/ssh-unreachable"
api "host-test web"
check "host-test shows ssh's error" "$(jqt '.ok==false and (.error.message | test("Connection refused"))')"
rm -f "$STUB_STATE/ssh-unreachable"
api "host-test nosuch"
check "host-test unknown host" "$(jqt '.ok==false and .error.code=="invalid_name"')"

HATT="$HOME/.config/claude-launcher/attach/${PD//[^A-Za-z0-9]/-}.hosts.json"
api "host-session demo-app2"
check "host-session lists hosts, none attached" "$(jqt '.ok and (.data.hosts | map(.name) == ["pw","web"]) and (.data.hosts | map(select(.attached)) == []) and (.data.hosts[0] | keys == ["address","attached","name","user"])')"
api "host-attach demo-app2 web on"
check "attach a host" "$(jqt '.ok and .data.host=="web" and .data.state=="on"')"
api "host-attach demo-app2 pw on"
[[ "$(jq -c 'keys' "$HATT")" == '["pw","web"]' ]]; check "attach file per project" $?
! jq -e 'has("web")' "${HATT%.hosts.json}.json" >/dev/null 2>&1; check "the workers' attach file is untouched" $?
api "host-attach demo-app2 pw off"
api "host-session demo-app2"
check "host-session shows the attachment" "$(jqt '[.data.hosts[] | select(.attached) | .name] == ["web"]')"
api "host-attach demo-app2 nosuch on"; check "attach needs a real host" "$(jqt '.ok == false')"
api "host-attach nosuch web on"; check "attach needs a running session" "$(jqt '.ok == false')"
api "host-remove web"
check "remove a host" "$(jqt '.ok and .data.removed=="web"')"
[[ ! -e "$HD/web" && "$(jq -c . "$HATT")" == '{}' ]]; check "remove deletes its files and every attachment" $?
api "host-remove web"; check "remove twice is an error" "$(jqt '.ok == false')"
api "host-remove pw"
api "host-list"
check "no hosts left" "$(jqt '.ok and .data.hosts == []')"

echo "worker-login helper sessions stay out of the lists"
printf '#!/usr/bin/env bash\nexec sleep 300\n' >"$WORK/claude"; chmod +x "$WORK/claude"
tmux new-session -d -s worker-login-zz "$WORK/claude 300"
sleep 0.5
api "sessions"
grep -q "worker-login-zz" <<<"$OUT"; [[ $? -ne 0 ]]; check "sessions hides worker-login-*" $?
api "chat-history worker-login-zz"
check "chat refuses worker-login-*" "$(jqt '.ok == false')"
"$HOME/.local/bin/claude-autostart" save >/dev/null
grep -q "^worker-login-zz	" "$LIST"; [[ $? -ne 0 ]]; check "autostart does not save worker-login-*" $?
tmux kill-session -t worker-login-zz 2>/dev/null

echo "cluster"
W="$HOME/.config/claude-launcher/workers"
mkdir -p "$HOME/.claude" "$W/ops/home" "$W/old/home"
FUT=$(( ($(date +%s) + 3600) * 1000 )); PAST=$(( ($(date +%s) - 3600) * 1000 ))
echo "{\"claudeAiOauth\":{\"accessToken\":\"sekrit-main\",\"expiresAt\":$FUT,\"subscriptionType\":\"max\"}}" >"$HOME/.claude/.credentials.json"
echo '{"oauthAccount":{"emailAddress":"me@example.com"}}' >"$HOME/.claude.json"
echo "{\"claudeAiOauth\":{\"accessToken\":\"sekrit-old\",\"expiresAt\":$PAST}}" >"$W/old/home/.credentials.json"
echo '{"role":"research","mode":"plan"}' >"$W/ops/meta.json"; echo '{"role":"","mode":"acceptEdits"}' >"$W/old/meta.json"
rm -f "$STUB_STATE/usage-config" "$STUB_STATE/usage-argv"
mkdir -p "$W/ops/home/projects/p"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"; T2="$(date -u -d '2 days ago' +%Y-%m-%dT%H:%M:%S.000Z)"; D9="$(date -u -d '9 days ago' +%Y-%m-%dT%H:%M:%S.000Z)"
{
  echo '{"type":"assistant","timestamp":"'"$NOW"'","message":{"id":"m1","usage":{"input_tokens":10,"output_tokens":1,"cache_creation_input_tokens":100,"cache_read_input_tokens":1000}}}'
  echo '{"type":"assistant","timestamp":"'"$NOW"'","message":{"id":"m1","usage":{"input_tokens":10,"output_tokens":50,"cache_creation_input_tokens":100,"cache_read_input_tokens":1000}}}'
  echo '{"type":"assistant","timestamp":"'"$T2"'","message":{"id":"m2","usage":{"input_tokens":1,"output_tokens":2,"cache_creation_input_tokens":3,"cache_read_input_tokens":4}}}'
  echo '{"type":"assistant","timestamp":"'"$D9"'","message":{"id":"m3","usage":{"input_tokens":999,"output_tokens":999,"cache_creation_input_tokens":999,"cache_read_input_tokens":999}}}'
  echo '{"type":"user","timestamp":"'"$NOW"'","message":{"role":"user","content":"hi"}}'
  echo 'not json {"output_tokens"'
} >"$W/ops/home/projects/p/s.jsonl"
api "cluster"
check "token counts: one row per message, windows respected, bad lines skipped" "$(jqt '.data.accounts[2].tokens | .five_hour == {in:110,out:50,cached:1000} and .seven_day == {in:114,out:52,cached:1004}')"
check "no transcripts: zero tokens, no folder: null" "$(jqt '.data.accounts[0].tokens.five_hour.out == 0 and .data.accounts[1].tokens == null')"
check "cluster lists main + workers" "$(jqt '.ok and (.data.accounts | map(.name) == ["main","old","ops"])')"
check "main: email, plan, usage" "$(jqt '.data.accounts[0] | .email == "me@example.com" and .plan == "max" and .usage.five_hour.pct == 42 and .usage.seven_day.pct == 7.5')"
check "expired token: no usage, says so" "$(jqt '.data.accounts[1] | .usage == null and .usage_error == "expired" and .signed_in')"
check "not signed in worker" "$(jqt '.data.accounts[2] | .signed_in == false and .role == "research" and .usage_error == null')"
grep -q "sekrit-main" "$STUB_STATE/usage-config"; check "token sent on curl's stdin" $?
grep -q "sekrit" "$STUB_STATE/usage-argv"; [[ $? -ne 0 ]]; check "token never in curl's arguments" $?
grep -q "sekrit-old" "$STUB_STATE/usage-config"; [[ $? -ne 0 ]]; check "expired token never sent" $?
api "cluster x"
check "cluster takes no arguments" "$(jqt '.ok == false')"
rm -rf "$W/ops" "$W/old" "$HOME/.claude.json"

echo "push alerts"
api "push-config"; check "push-config: not configured at first" "$(jqt '.ok and .data.configured == false')"
api "push-setup" "not json"; check "push-setup refuses a non-key" "$(jqt '.ok == false and .error.code == "invalid_name"')"
[[ ! -e "$HOME/.config/claude-launcher/fcm-key.json" ]]; check "push-setup keeps nothing from a bad key" $?
api "push-register short"; check "push-register refuses a short token" "$(jqt '.ok == false')"
api "push-register aaaaaaaaaaaaaaaaaaaaaaaaaaaa:bbbb_cc-dd"; check "push-register stores a phone" "$(jqt '.ok and .data.devices == 1')"
api "push-register aaaaaaaaaaaaaaaaaaaaaaaaaaaa:bbbb_cc-dd"; check "same phone twice stays one" "$(jqt '.data.devices == 1')"
[[ "$(stat -c %a "$HOME/.config/claude-launcher/push-tokens")" == 600 ]]; check "token file is private" $?
echo '{"project_id":"p","app_id":"1:2:android:3","api_key":"k","sender_id":"2"}' >"$HOME/.config/claude-launcher/push.json"
api "push-config"; check "push-config: still needs the key file" "$(jqt '.data.configured == false')"
echo '{}' >"$HOME/.config/claude-launcher/fcm-key.json"
api "push-config"; check "push-config hands the app its ids" "$(jqt '.data.configured and .data.project_id == "p" and .data.sender_id == "2" and (.data | has("private_key") | not)')"
api "push-session demo-app on"; check "push-session on" "$(jqt '.ok and .data.push_done')"
api "sessions"; check "sessions carry push_done" "$(jqt '.ok and (.data.sessions | all(has("push_done")))')"
api "push-session demo-app maybe"; check "push-session refuses other words" "$(jqt '.ok == false')"
api "push-session demo-app off"; [[ ! -e "$HOME/.config/claude-launcher/push-done/demo-app" ]]; check "push-session off" $?
echo '{"theme":"dark"}' >"$HOME/.claude/settings.json"
api "status"; check "status installs the push hooks" "$(jq -e '.theme == "dark" and (.hooks.Notification[0].hooks[0].command | endswith("claude-push")) and (.hooks.Stop | length) == 1' "$HOME/.claude/settings.json" >/dev/null 2>&1; echo $?)"
api "status"; check "push hooks are added once" "$(jq -e '(.hooks.Notification | length) == 1 and (.hooks.Stop | length) == 1' "$HOME/.claude/settings.json" >/dev/null 2>&1; echo $?)"
rm -f "$HOME/.config/claude-launcher/push.json" "$HOME/.config/claude-launcher/fcm-key.json"
OUT="$(python3 "$HERE/test-push.py" 2>&1)"; check "claude-push against a fake Google" $?

echo "migrate"
# Earlier tests run install.sh against this HOME, which installs the REAL claude-backup over the stub: put the stub back.
cp "$HERE/stubs/claude-backup" "$HOME/.local/bin/claude-backup"; chmod +x "$HOME/.local/bin/claude-backup"
MIG="$HOME/.config/claude-launcher/migrate"; MST="$HOME/.local/state/claude-launcher/migrate"; AK="$HOME/.ssh/authorized_keys"
APILOG="$HOME/.local/state/claude-launcher/api.log"
KB=AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
mkdir -p "$HOME/.ssh"; [[ -f "$AK" ]] && cp "$AK" "$WORK/authorized_keys.orig"
# A stub ssh: remembers its arguments and the pinned known_hosts, then plays the new server by running `claude-backup receive`.
cat >"$HOME/.local/bin/ssh" <<'EOF'
#!/usr/bin/env bash
echo "$*" >"$STUB_STATE/ssh-argv"
for a in "$@"; do
  case "$a" in UserKnownHostsFile=*) cp "${a#*=}" "$STUB_STATE/ssh-known-hosts" ;; esac
done
[[ -e "$STUB_STATE/ssh-fail" ]] && { echo "Host key verification failed." >&2; exit 255; }
[[ -e "$STUB_STATE/ssh-sleep" ]] && sleep "$(cat "$STUB_STATE/ssh-sleep")"
exec claude-backup receive
EOF
chmod +x "$HOME/.local/bin/ssh"
mig_wait() { # mig_wait <jq condition> [tries]: poll migrate-status until it holds
  local i
  for ((i = 0; i < ${2:-80}; i++)); do
    api "migrate-status"
    [[ "$(jqt "$1")" == 0 ]] && return 0
    sleep 0.5
  done
  return 1
}
send_in() { printf '%s\n%s\n%s\n%s\n%s' "$1" "$2" "$3" "$4" "$5"; }

# Task 6: the actions that sudo, push this server's secrets or show the passphrase need ALLOW_RUN=1, like `run`.
grep -q '^ALLOW_RUN=1$' "$HOME/.config/claude-launcher/config" 2>/dev/null; [[ $? -ne 0 ]]; check "migrate: ALLOW_RUN is off at this point" $?
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
check "send: refused while Run-a-command is off" "$(jqt '.ok==false and .error.code=="run_disabled" and (.error.message | contains("ALLOW_RUN=1"))')"
[[ ! -e "$MST/status.json" && ! -e "$MIG/passphrase" ]]; check "...and nothing was started" $?
api "migrate-passphrase"
check "passphrase: refused while Run-a-command is off" "$(jqt '.ok==false and .error.code=="run_disabled" and (.error.message | contains("ALLOW_RUN=1"))')"
api "migrate-sudo-check"
check "sudo-check: refused while Run-a-command is off" "$(jqt '.ok==false and .error.code=="run_disabled" and (.error.message | contains("ALLOW_RUN=1"))')"
echo 'ALLOW_RUN=1' >>"$HOME/.config/claude-launcher/config"

api "status"; check "migrate: script_api is 34 or more" "$(jqt '.data.script_api >= 34')"
api "migrate-status"
check "migrate-status: nothing yet" "$(jqt '.ok and .data == {job:"none",phase:"none",message:"",pct:0,bytes:0}')"
api "migrate-passphrase"
check "migrate-passphrase: not_ready before a send" "$(jqt '.ok==false and .error.code=="not_ready"')"
CLAUDERC_BACKUP_BIN=/nonexistent api "migrate-plan"
check "migrate-plan without claude-backup: not_configured" "$(jqt '.ok==false and .error.code=="not_configured" and .error.message=="claude-backup is missing: run Update now"')"
tmux new-session -d -s migsess -c "$HOME" "claude --remote-control migsess; exec bash"
api "migrate-plan"
tmux kill-session -t "=migsess" 2>/dev/null
check "migrate-plan: merged shape" "$(jqt '.ok and (.data | keys) == ["docker_volumes","estimate_mb","logins","other_dirs","repos","services","sessions"] and .data.estimate_mb==12 and .data.logins == {claude:true,workers:["ops"]} and .data.docker_volumes==["db"] and .data.other_dirs==["/home/x/data"] and .data.repos[0].dir=="/home/x/projects/app"')"
check "migrate-plan: sessions are name/busy/waiting, services are ids" "$(jqt '(.data.sessions | length > 0 and all(keys == ["busy","name","waiting"])) and (.data.services | all(type=="string"))')"
[[ "$(tail -n 1 "$STUB_STATE/backup-argv")" == "plan --json" ]]; check "migrate-plan runs 'plan --json'" $?

echo "migrate keygen / authorize"
api "migrate-keygen"
check "keygen returns an ed25519 key" "$(jqt '.ok and (.data.public_key | test("^ssh-ed25519 [A-Za-z0-9+/]+=* clauderc-migrate$"))')"
PUB1="$(jq -r .data.public_key <<<"$OUT")"
[[ "$(stat -c %a "$MIG/key")" == 600 && "$(stat -c %a "$MIG")" == 700 ]]; check "keygen: key 600, folder 700" $?
api "migrate-keygen"
[[ "$(jq -r .data.public_key <<<"$OUT")" == "$PUB1" ]]; check "keygen is idempotent" $?
printf '%s\n' 'ssh-ed25519 AAAAOTHERKEY phone' 'restrict,command="x" ssh-rsa AAAAB3OTHER other-key' >"$AK"
api "migrate-authorize" "$(printf '%s\n%s' "$PUB1" 203.0.113.5)"
check "authorize ok" "$(jqt '.ok and .data.authorized')"
BLOB1="$(awk '{print $2}' <<<"$PUB1")"
[[ "$(grep -c ' clauderc-migrate$' "$AK")" == 1 && "$(wc -l <"$AK")" == 3 ]]; check "authorize writes exactly one line" $?
[[ "$(tail -n 1 "$AK")" == "restrict,command=\"$HOME/.local/bin/claude-backup receive\",from=\"203.0.113.5\" ssh-ed25519 $BLOB1 clauderc-migrate" ]]; check "the line is restricted to claude-backup receive from that address" $?
[[ "$(head -n 2 "$AK")" == $'ssh-ed25519 AAAAOTHERKEY phone\nrestrict,command="x" ssh-rsa AAAAB3OTHER other-key' && "$(stat -c %a "$AK")" == 600 ]]; check "authorize leaves other lines alone, file 600" $?
ssh-keygen -q -t ed25519 -N '' -C 'someone@host' -f "$WORK/k2" </dev/null
PUB2="$(cat "$WORK/k2.pub")"; BLOB2="$(awk '{print $2}' <<<"$PUB2")"
api "migrate-authorize" "$PUB2"
check "authorize again ok" "$(jqt '.ok and .data.authorized')"
[[ "$(grep -c ' clauderc-migrate$' "$AK")" == 1 && "$(wc -l <"$AK")" == 3 && "$(tail -n 1 "$AK")" == "restrict,command=\"$HOME/.local/bin/claude-backup receive\" ssh-ed25519 $BLOB2 clauderc-migrate" ]]; check "an older clauderc-migrate line is replaced (no address: no from=)" $?
grep -q "$BLOB1" "$AK"; [[ $? -ne 0 ]]; check "the old key is gone" $?
SUM="$(cksum <"$AK")"
for bad in "ssh-rsa AAAAB3NzaC1yc2E x" "ssh-ed25519 AAAA;id" 'ssh-ed25519 AAAA"x' "ssh-ed25519 AAAA two words" "ssh-ed25519" "" "ssh-ed25519 AAAA x
ssh-ed25519 BBBB"; do
  api "migrate-authorize" "$bad"
  check "authorize refuses key '${bad:0:24}'" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
for bad in 'a"b' "evil.com,command=x" "a b" "*" "-x" "a;b"; do
  api "migrate-authorize" "$(printf '%s\n%s' "$PUB2" "$bad")"
  check "authorize refuses address '$bad'" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
[[ "$(cksum <"$AK")" == "$SUM" ]]; check "refused authorizations changed nothing" $?
CLAUDERC_BACKUP_BIN=/nonexistent api "migrate-authorize" "$PUB2"
check "authorize with the stub present uses the fixed path (env override can't change it)" "$(jqt '.ok')"
# Task 6: from= only for a literal IP address
api "migrate-authorize" "$(printf '%s\n%s' "$PUB2" Backup.Example.com)"
[[ "$(jqt '.ok')" == 0 && "$(tail -n 1 "$AK")" == "restrict,command=\"$HOME/.local/bin/claude-backup receive\" ssh-ed25519 $BLOB2 clauderc-migrate" ]]; check "authorize: a host name gets no from= (sshd would never match it)" $?
api "migrate-authorize" "$(printf '%s\n%s' "$PUB2" 198.51.100.7)"
[[ "$(tail -n 1 "$AK")" == "restrict,command=\"$HOME/.local/bin/claude-backup receive\",from=\"198.51.100.7\" ssh-ed25519 $BLOB2 clauderc-migrate" ]]; check "authorize: an IPv4 address gets from=" $?
api "migrate-authorize" "$(printf '%s\n%s' "$PUB2" 2001:DB8::5)"
[[ "$(tail -n 1 "$AK")" == "restrict,command=\"$HOME/.local/bin/claude-backup receive\",from=\"2001:db8::5\" ssh-ed25519 $BLOB2 clauderc-migrate" ]]; check "authorize: an IPv6 address gets from= (lower case)" $?
for notip in 999.1.1.1 1.2.3 01.2.3.4 1.2.3.4.5 12:34 a.b.c.d 1-2-3-4; do
  api "migrate-authorize" "$(printf '%s\n%s' "$PUB2" "$notip")"
  [[ "$(jqt '.ok')" == 0 && "$(tail -n 1 "$AK")" != *from=* ]]; check "authorize: '$notip' is not an IP address: no from=" $?
done
[[ "$(grep -c ' clauderc-migrate$' "$AK")" == 1 && "$(wc -l <"$AK")" == 3 ]]; check "authorize: still exactly one transfer line" $?
# Task 6: an unreadable authorized_keys is never wiped
if [[ "$(id -u)" != 0 ]]; then
  SUM="$(cksum <"$AK")"; chmod 000 "$AK"
  api "migrate-authorize" "$PUB2"
  chmod 600 "$AK"
  [[ "$(jqt '.ok==false and .error.code=="internal"')" == 0 && "$(cksum <"$AK")" == "$SUM" ]]; check "authorize: an unreadable authorized_keys makes it fail and stays untouched" $?
  [[ -z "$(ls "$HOME"/.ssh/authorized_keys.* 2>/dev/null)" ]]; check "...and no temp file is left" $?
fi

echo "migrate send"
for bad in "bad host|2222|ops|ssh-ed25519|$KB" "h.example.com|0|ops|ssh-ed25519|$KB" "h.example.com|99999|ops|ssh-ed25519|$KB" \
  "h.example.com|22x|ops|ssh-ed25519|$KB" "h.example.com|2222|-oProxyCommand=x|ssh-ed25519|$KB" "h.example.com|2222|ops|ssh-dss|$KB" \
  "h.example.com|2222|ops|ssh-ed25519|not base64!" "h.example.com|2222|ops|ssh-ed25519|AAAA" "-oProxyCommand=x|2222|ops|ssh-ed25519|$KB" "|||" "h.example.com|2222|op s|ssh-ed25519|$KB"; do
  IFS='|' read -r h p u ty bl <<<"$bad"
  api "migrate-send" "$(send_in "$h" "$p" "$u" "$ty" "$bl")"
  check "send refuses '${bad:0:34}'" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
[[ ! -e "$MST/status.json" && ! -e "$MIG/passphrase" && ! -e "$MIG/known_hosts" ]]; check "refused sends started nothing" $?
echo 1 >"$STUB_STATE/backup-sleep"; echo 3 >"$STUB_STATE/ssh-sleep"
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
check "send starts" "$(jqt '.ok and .data.started')"
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
check "a second send while one runs is busy" "$(jqt '.ok==false and .error.code=="busy"')"
touch "$HOME/backups/incoming-20260101-010101.gpg"
api "migrate-restore incoming-20260101-010101.gpg" "AAAA-BBBB-CCCC-DDDD-EEEE"
check "a restore while a send runs is busy" "$(jqt '.ok==false and .error.code=="busy"')"
rm -f "$HOME/backups/incoming-20260101-010101.gpg"
api "migrate-status"
check "status while running: job send, exactly the contract's fields" "$(jqt '.ok and .data.job=="send" and (.data.phase=="export" or .data.phase=="transfer") and (.data|keys) == ["bytes","error","file","job","message","pct","phase","started","updated"] and .data.error==""')"
api "migrate-passphrase"
check "passphrase: five groups of four, no look-alikes" "$(jqt '.ok and (.data.passphrase | test("^[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){4}$"))')"
PASS="$(jq -r .data.passphrase <<<"$OUT")"
[[ "$(stat -c %a "$MIG/passphrase")" == 600 && "$(stat -c %a "$MST/status.json")" == 600 && "$(stat -c %a "$MST")" == 700 ]]; check "passphrase file 600, status.json 600, state folder 700" $?
mig_wait '.data.phase=="done"'; check "send reaches done" $?
check "done: 100%, the name the new server printed, all bytes" "$(jqt '.data.job=="send" and .data.pct==100 and .data.bytes==3000000 and .data.error=="" and (.data.file | test("^incoming-[0-9]{8}-[0-9]{6}\\.gpg$"))')"
INC="$(jq -r .data.file <<<"$OUT")"
[[ "$(stat -c %s "$HOME/backups/$INC")" == 3000000 ]]; check "the new server's file arrived whole" $?
[[ "$(ls "$HOME"/backups/migrate-*.gpg | wc -l)" == 1 ]]; check "the local export is kept after a good send" $?
[[ "$(cat "$STUB_STATE/backup-export-pass")" == "$PASS" ]]; check "export used the generated passphrase file" $?
grep -q -- "export --no-claude-login --pass-file $MIG/passphrase --out $HOME/backups/migrate-" "$STUB_STATE/backup-argv"; check "export arguments: --no-claude-login, pass file, out" $?
[[ "$(cat "$STUB_STATE/ssh-argv")" == *"-i $MIG/key "* && "$(cat "$STUB_STATE/ssh-argv")" == *"-o BatchMode=yes"* && "$(cat "$STUB_STATE/ssh-argv")" == *"-o IdentitiesOnly=yes"* && "$(cat "$STUB_STATE/ssh-argv")" == *"-o StrictHostKeyChecking=yes"* && "$(cat "$STUB_STATE/ssh-argv")" == *"-p 2222 ops@192.0.2.10 receive" ]]; check "ssh arguments: key, batch, pinned host key, port, user@host receive" $?
[[ "$(cat "$STUB_STATE/ssh-known-hosts")" == "[192.0.2.10]:2222 ssh-ed25519 $KB" ]]; check "known_hosts holds exactly the pinned key ([host]:port form)" $?
[[ ! -e "$MIG/known_hosts" ]]; check "the temporary known_hosts is removed" $?
grep -rq "$PASS" "$STUB_STATE/backup-argv" "$STUB_STATE/ssh-argv" "$APILOG"; [[ $? -ne 0 ]]; check "passphrase in no argv and not in the api log" $?
grep -q "migrate-passphrase" "$APILOG"; check "(the passphrase call itself is logged by name only)" $?

echo "migrate send failures"
rm -f "$STUB_STATE/backup-sleep" "$STUB_STATE/ssh-sleep" "$STUB_STATE/ssh-argv"
touch "$STUB_STATE/ssh-fail"
api "migrate-send" "$(send_in Host.Example.com 22 ops ssh-ed25519 "$KB")"
check "send (port 22) starts" "$(jqt '.ok and .data.started')"
mig_wait '.data.phase=="failed"'; check "a refused host key ends in failed" $?
check "failed: error says why" "$(jqt '.data.job=="send" and (.data.error | contains("Host key verification failed"))')"
[[ "$(cat "$STUB_STATE/ssh-known-hosts")" == "host.example.com ssh-ed25519 $KB" ]]; check "port 22 known_hosts line has no brackets" $?
[[ "$(ls "$HOME"/backups/migrate-*.gpg | wc -l)" == 1 && ! -e "$MIG/known_hosts" ]]; check "failed send deleted its local file and known_hosts" $?
rm -f "$STUB_STATE/ssh-fail" "$STUB_STATE/ssh-argv"; touch "$STUB_STATE/backup-fail-export"
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
mig_wait '.data.phase=="failed"'; check "a failing export ends in failed" $?
check "export failure: error has claude-backup's last line, colours stripped" "$(jqt '.data.error | contains("The export failed") and contains("out of disk space") and (contains("\u001b") | not)')"
[[ "$(ls "$HOME"/backups/migrate-*.gpg | wc -l)" == 1 && ! -e "$STUB_STATE/ssh-argv" ]]; check "the partial export is deleted and nothing was sent" $?
rm -f "$STUB_STATE/backup-fail-export"

echo "migrate status"
jq -cn '{job:"send",phase:"transfer",message:"x",pct:70,bytes:5,file:"",started:1,updated:1,error:"",pid:999999}' >"$MST/status.json"
echo leftover >"$MST/restore-pass.dead1"
api "migrate-status"
check "a dead pid mid-job is reported failed" "$(jqt '.data.phase=="failed" and .data.error=="The job stopped unexpectedly" and .data.pct==70 and (.data|has("pid")|not)')"
[[ ! -e "$MST/restore-pass.dead1" ]]; check "...and a dead job's pass file in the state folder is removed" $?
api "migrate-status"
check "and stays failed" "$(jqt '.data.phase=="failed"')"
jq -cn --argjson n "$(date +%s)" '{job:"send",phase:"export",message:"x",pct:0,bytes:0,file:"",started:$n,updated:$n,error:"",pid:null}' >"$MST/status.json"
api "migrate-status"
check "a job that hasn't written its pid yet counts as starting" "$(jqt '.data.phase=="export"')"
jq -cn '{job:"send",phase:"export",message:"x",pct:0,bytes:0,file:"",started:1,updated:1,error:"",pid:null}' >"$MST/status.json"
api "migrate-status"
check "...for a minute only" "$(jqt '.data.phase=="failed"')"
jq -cn '{job:"restore",phase:"restore",message:"x",pct:5,bytes:0,file:"",started:1,updated:1,error:"",pid:999999}' >"$MST/status.json"

echo "migrate restore"
api "migrate-restore" "AAAA-BBBB-CCCC-DDDD-EEEE"
check "restore needs the file name argument" "$(jqt '.ok==false')"
api "migrate-restore incoming-20200101-000000.gpg" "AAAA-BBBB-CCCC-DDDD-EEEE"
check "restore of a file that isn't there" "$(jqt '.ok==false and .error.code=="invalid_name"')"
api "migrate-restore $INC" "abc"
check "restore refuses a short passphrase" "$(jqt '.ok==false and .error.code=="invalid_name"')"
[[ -f "$HOME/backups/$INC" ]]; check "refusals left the file" $?
echo "RIGHT-PASS-PHRASE-1" >"$STUB_STATE/backup-expect-pass"
api "migrate-restore $INC" "WRONG-PASS-PHRASE-9"
check "restore starts (a stale dead job doesn't block it)" "$(jqt '.ok and .data.started')"
mig_wait '.data.phase=="failed"'; check "wrong passphrase ends in failed" $?
check "wrong passphrase: a clear error, colours stripped" "$(jqt '.data.job=="restore" and (.data.error | contains("passphrase didn'"'"'t work") and (contains("\u001b") | not)) and .data.file=="'"$INC"'"')"
[[ -f "$HOME/backups/$INC" ]]; check "failed restore keeps the incoming file" $?
[[ -z "$(ls "$MST"/restore-pass.* "$MIG"/restore-pass.* 2>/dev/null)" ]]; check "failed restore removed its pass file" $?
echo "git clone failed for example/app" >"$STUB_STATE/backup-fail-import"
api "migrate-restore $INC" "RIGHT-PASS-PHRASE-1"
mig_wait '.data.phase=="failed"'; check "an import failure ends in failed" $?
check "import failure: its last lines are the error" "$(jqt '.data.error | contains("The restore failed") and contains("git clone failed for example/app")')"
[[ -f "$HOME/backups/$INC" && -z "$(ls "$MST"/restore-pass.* "$MIG"/restore-pass.* 2>/dev/null)" ]]; check "...file kept, pass file gone" $?
rm -f "$STUB_STATE/backup-fail-import"; echo 1 >"$STUB_STATE/backup-sleep"
api "migrate-restore $INC" "RIGHT-PASS-PHRASE-1"
check "retry starts" "$(jqt '.ok and .data.started')"
PFS="$(ls "$MST"/restore-pass.* 2>/dev/null)"
[[ -n "$PFS" && "$(stat -c %a $PFS)" == 600 && -z "$(ls "$MIG"/restore-pass.* 2>/dev/null)" && "$(stat -c %a "$MST")" == 700 ]]; check "while it runs the pass file is in the state folder (600), not in the config folder" $?
api "migrate-restore $INC" "RIGHT-PASS-PHRASE-1"
check "a second restore while one runs is busy" "$(jqt '.ok==false and .error.code=="busy"')"
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
check "and so is a send" "$(jqt '.ok==false and .error.code=="busy"')"
api "migrate-status"
check "restore status while running" "$(jqt '.data.job=="restore" and .data.phase=="restore" and .data.file=="'"$INC"'" and .data.pct < 100')"
mig_wait '.data.phase=="done"'; check "restore reaches done" $?
check "done: 100%" "$(jqt '.data.job=="restore" and .data.pct==100 and .data.error==""')"
[[ ! -e "$HOME/backups/$INC" && -z "$(ls "$MST"/restore-pass.* "$MIG"/restore-pass.* 2>/dev/null)" ]]; check "success deletes the incoming file and the pass file" $?
[[ -s "$MST/manifest.json" ]]; check "the manifest was written for migrate-verify" $?
grep -q -- "import $HOME/backups/$INC --pass-file $MST/restore-pass\.[A-Za-z0-9]* --clone --manifest-out $MST/manifest.json" "$STUB_STATE/backup-argv"; check "import arguments: pass file (in the state folder, not the config folder), --clone, --manifest-out" $?
grep -rq "RIGHT-PASS-PHRASE-1\|WRONG-PASS-PHRASE-9" "$STUB_STATE/backup-argv" "$APILOG"; [[ $? -ne 0 ]]; check "restore passphrases in no argv and not in the api log" $?
# Task 6: receive names a clash incoming-<time>-N.gpg; send's status, the runner and restore all accept it
echo -7 >"$STUB_STATE/receive-suffix"; echo 0 >"$STUB_STATE/backup-sleep"
api "migrate-send" "$(send_in 192.0.2.10 2222 ops ssh-ed25519 "$KB")"
mig_wait '.data.phase=="done"'; check "send reaches done when the new server names the file with a -N suffix" $?
check "...and the status carries that name" "$(jqt '.data.file | test("^incoming-[0-9]{8}-[0-9]{6}-7\\.gpg$")')"
INC7="$(jq -r .data.file <<<"$OUT")"
[[ -f "$HOME/backups/$INC7" ]]; check "...the file is there" $?
api "migrate-restore $INC7" "RIGHT-PASS-PHRASE-1"
check "restore accepts incoming-<time>-N.gpg (runner and action)" "$(jqt '.ok and .data.started')"
mig_wait '.data.phase=="done"'; check "...and finishes" $?
[[ ! -e "$HOME/backups/$INC7" ]]; check "...deleting that file" $?
api "migrate-restore incoming-20260101-010101-1000.gpg" "RIGHT-PASS-PHRASE-1"
check "restore refuses a 4-digit suffix" "$(jqt '.ok==false and .error.code=="forbidden"')"
api "migrate-restore incoming-20260101-010101-.gpg" "RIGHT-PASS-PHRASE-1"
check "restore refuses an empty suffix" "$(jqt '.ok==false and .error.code=="forbidden"')"
rm -f "$STUB_STATE/receive-suffix"
rm -f "$STUB_STATE/backup-sleep" "$STUB_STATE/backup-expect-pass" "$HOME/.local/bin/ssh"
if [[ -f "$WORK/authorized_keys.orig" ]]; then cp "$WORK/authorized_keys.orig" "$AK"; else rm -f "$AK"; fi

echo "migrate (2): sudo check"
# Everything here runs against stubs on PATH (sudo, adduser, usermod, getent, id, systemctl, reboot, gh, claude, curl) and the
# test HOME: nothing may reach the real system. The stub users and their homes live under $STUB_STATE.
MS="$STUB_STATE"; mkdir -p "$WORK/tmp"; export TMPDIR="$WORK/tmp"
REALHOME="$(getent passwd "$(id -un)" | cut -d: -f6)"
# (files a real server doesn't change by itself: the logins and the saved session list do refresh while a test runs)
realsnap() { local f; for f in .ssh/authorized_keys .config/claude-launcher/migrate .config/gh/hosts.yml; do
  stat -c '%n %Y %s' "$REALHOME/$f" 2>/dev/null || echo "$f none"; done; }
REAL_BEFORE="$(realsnap)"
[[ -f "$AK" ]] && cp "$AK" "$WORK/authorized_keys.orig2"
echo password >"$MS/sudo-mode"; rm -f "$MS/sudo-argv"
api "status"; check "migrate (2): script_api is 36 or more" "$(jqt '.data.script_api >= 36')"
STUB_ID_U=0 api "migrate-sudo-check"
check "sudo-check: root" "$(jqt '.ok and .data == {mode:"root"}')"
[[ ! -s "$MS/sudo-argv" ]]; check "sudo-check: root never calls sudo" $?
echo nopasswd >"$MS/sudo-mode"
api "migrate-sudo-check"
check "sudo-check: nopasswd" "$(jqt '.ok and .data == {mode:"nopasswd"}')"
echo password >"$MS/sudo-mode"
api "migrate-sudo-check"
check "sudo-check: password_needed without a password" "$(jqt '.ok and .data == {mode:"password_needed"}')"
api "migrate-sudo-check" "pw-ok"
check "sudo-check: password_ok with the right password" "$(jqt '.ok and .data == {mode:"password_ok"}')"
api "migrate-sudo-check" "pw-wrong"
check "sudo-check: password_needed with a wrong password" "$(jqt '.ok and .data == {mode:"password_needed"}')"
[[ "$(grep -c -- '-A -k true' "$MS/sudo-argv")" == 2 ]]; check "sudo-check: one sudo try per password" $?
echo none >"$MS/sudo-mode"
api "migrate-sudo-check"
check "sudo-check: none" "$(jqt '.ok and .data == {mode:"none"}')"
api "migrate-sudo-check" "pw-ok"
check "sudo-check: none even with a password" "$(jqt '.ok and .data == {mode:"none"}')"
grep -rq "pw-ok\|pw-wrong" "$MS/sudo-argv" "$APILOG"; [[ $? -ne 0 ]]; check "sudo-check: the password is in no argv and not in the api log" $?
[[ -z "$(find "$WORK/tmp" -name pw 2>/dev/null)" ]]; check "sudo-check: no password file is left behind" $?
echo password >"$MS/sudo-mode"

echo "migrate (2): create user"
FB="$WORK/fakebase"; mkdir -p "$FB"
cat >"$FB/install.sh" <<'EOF'
#!/usr/bin/env bash
# Stand-in for the cLaudeRC installer (the real one is tested above): records how it was started.
[[ ! -e "$STUB_STATE/installer-fail" ]] || { echo "installer exploded" >&2; exit 1; }
echo "HOME=$HOME args=$# base=${CLAUDERC_BASE:-}" >>"$STUB_STATE/installer-log"
mkdir -p "$HOME/bin" "$HOME/.local/bin"
EOF
PUBK="$(cat "$WORK/phone.pub")"; BLOBK="$(awk '{print $2}' "$WORK/phone.pub")"
cu() { CLAUDERC_BASE="file://$FB" api "migrate-create-user $1" "$(printf '%s\n%s' "$2" "$3")"; }   # cu <name> <password> <key>
sed -i '/^ALLOW_RUN=1$/d' "$HOME/.config/claude-launcher/config"
cu newguy pw-ok "$PUBK"
check "create-user: refused while Run-a-command is off" "$(jqt '.ok==false and .error.code=="run_disabled"')"
[[ ! -e "$MS/adduser-log" ]]; check "...and nothing was created" $?
echo 'ALLOW_RUN=1' >>"$HOME/.config/claude-launcher/config"
cu newguy pw-ok "$PUBK"
check "create-user: ok, home under the stub, autostart left pending" "$(jqt '.ok and .data == {created:true, home:"'"$MS/home/newguy"'", autostart_pending:true, shell_key:false}')"
grep -q -- "^-A adduser --disabled-password --gecos  newguy$" "$MS/sudo-argv"; check "create-user: adduser --disabled-password --gecos \"\" NAME, through the sudo askpass" $?
[[ "$(cat "$MS/adduser-log")" == "--disabled-password --gecos  newguy" ]]; check "create-user: adduser ran once with those arguments" $?
[[ ! -e "$MS/usermod-log" ]]; check "create-user: no docker group, no usermod" $?
NH="$MS/home/newguy"
[[ "$(cat "$NH/.ssh/authorized_keys")" == "restrict,command=\"$NH/bin/claude-launcher-api\" ssh-ed25519 $BLOBK clauderc" ]]; check "create-user: authorized_keys holds exactly the forced-command line" $?
[[ "$(stat -c %a "$NH/.ssh")" == 700 && "$(stat -c %a "$NH/.ssh/authorized_keys")" == 600 ]]; check "create-user: .ssh is 700, authorized_keys 600" $?
grep -q "claude installer: $NH" "$MS/claude-install-log"; check "create-user: Claude Code's installer ran as the new user (HOME = theirs)" $?
[[ "$(cat "$MS/installer-log")" == "HOME=$NH args=0 base=file://$FB" ]]; check "create-user: the cLaudeRC installer ran as them, with no key, honouring CLAUDERC_BASE" $?
grep -q -- "-u newguy -H bash -lc" "$MS/sudo-argv"; check "create-user: user steps run as sudo -u NAME -H bash -lc" $?
[[ -e "$NH/.clauderc-migrate-user" ]]; check "create-user: marker file written" $?
[[ "$(stat -c '%U %a %F' -- "$NH/.clauderc-migrate-user")" == "root 644 regular file" && "$(grep -c "^-A install -m 644 -o root -g root /dev/null $NH/.clauderc-migrate-user$" "$MS/sudo-argv")" == 1 ]]; check "create-user: the marker is written as root (owner root, mode 644) through the sudo helper" $?
grep -q -- "^-A sudo -l -U newguy$" "$MS/sudo-argv" || grep -q -- "^-l -U newguy$" "$MS/sudo-argv"; check "create-user: sudo -l -U NAME checked the new user" $?
[[ ! -e "$MS/userdel-log" ]]; check "create-user: a user without sudo rules is kept (no userdel)" $?
grep -rq "pw-ok" "$MS/sudo-argv" "$MS/adduser-log" "$MS/installer-log" "$APILOG"; [[ $? -ne 0 ]]; check "create-user: the password is in no argv and not in the api log" $?
grep -q "$BLOBK" "$MS/sudo-argv"; [[ $? -ne 0 ]]; check "create-user: the key is not on any command line" $?
grep -q "install -y -qq jq tmux git curl" "$MS/apt-log"; check "create-user: apt installed jq, tmux, git, curl before the user was made" $?
SK="$(ssh-keygen -q -t ed25519 -N '' -f "$WORK/shell" -C x >/dev/null 2>&1; awk '{print $1" "$2}' "$WORK/shell.pub")"
cu newguy pw-ok "$PUBK
$SK"
check "create-user: a login key is accepted for the user made earlier" "$(jqt '.ok and .data.shell_key == true')"
grep -qx "ssh-ed25519 $(awk '{print $2}' <<<"$SK") clauderc-shell" "$NH/.ssh/authorized_keys"; check "create-user: the login key is a plain line (no forced command)" $?
[[ "$(grep -c "restrict,command" "$NH/.ssh/authorized_keys")" == 1 ]]; check "create-user: the phone key line is still the only restricted one" $?
cu newguy pw-ok "$PUBK
not-a-key"
check "create-user: a bad login key is refused" "$(jqt '.ok==false and .error.code=="invalid_name"')"
[[ -z "$(find "$WORK/tmp" -name pw 2>/dev/null)" ]]; check "create-user: no password file is left behind" $?
cu newguy pw-ok "$PUBK"
check "create-user: running it again reuses the user it made" "$(jqt '.ok and .data.created and .data.home=="'"$NH"'"')"
[[ "$(wc -l <"$MS/adduser-log")" == 1 && "$(grep -c "^restrict" "$NH/.ssh/authorized_keys")" == 1 ]]; check "...no second adduser, still one phone-key line" $?
# refusals
cu "ab" pw-ok "$PUBK"; [[ "$(jqt '.ok')" == 0 ]]; check "create-user: a short valid name works" $?
for bad in Bad 1abc -x "a b" 'a;b' "a/b" "$(printf 'a%.0s' {1..40})"; do
  api "migrate-create-user $bad" "$(printf 'pw-ok\n%s' "$PUBK")"
  check "create-user: runner refuses '${bad:0:12}'" "$(jqt '.ok==false and .error.code=="forbidden"')"
done
for bad in root daemon www-data nobody systemd-network sshd docker admin sudo wheel adm staff; do
  OUT="$(CLAUDERC_BASE="file://$FB" "$HOME/bin/claude-setup.sh" --api migrate-create-user "$bad" <<<"$(printf 'pw-ok\n%s' "$PUBK")" 2>/dev/null)"
  check "create-user: reserved/system name '$bad' refused" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
mkdir -p "$MS/users" "$MS/home/olduser" && echo 1500 >"$MS/users/olduser" && echo data >"$MS/home/olduser/notes.txt"
cu olduser pw-ok "$PUBK"
check "create-user: an existing user with files is refused" "$(jqt '.ok==false and .error.code=="user_exists"')"
[[ "$(wc -l <"$MS/adduser-log")" == 2 && ! -e "$MS/home/olduser/.ssh" ]]; check "...and left alone" $?
mkdir -p "$MS/users" "$MS/groups" "$MS/home/lowuid" && echo 500 >"$MS/users/lowuid"
cu lowuid pw-ok "$PUBK"
check "create-user: an existing user with a system uid is refused" "$(jqt '.error.code=="user_exists"')"
touch "$MS/groups/taken"
cu taken pw-ok "$PUBK"
check "create-user: a name that is already a group is refused" "$(jqt '.error.code=="user_exists"')"
mkdir -p "$MS/home/skel" && echo 1502 >"$MS/users/skel" && touch "$MS/home/skel/.bashrc" "$MS/home/skel/.profile"
cu skel pw-ok "$PUBK"
# (changed in task 6: this used to continue a user with only skeleton files; now only a user with the root-owned marker is reused)
check "create-user: an existing user with only skeleton files and no root-owned marker is refused" "$(jqt '.ok==false and .error.code=="user_exists"')"
[[ ! -e "$MS/home/skel/.ssh" ]]; check "...and left alone (no key authorized)" $?
mkdir -p "$MS/home/forged" && echo 1503 >"$MS/users/forged" && touch "$MS/home/forged/.bashrc" "$MS/home/forged/.clauderc-migrate-user"
cu forged pw-ok "$PUBK"
check "create-user: a marker file the user owns (not root) doesn't count" "$(jqt '.ok==false and .error.code=="user_exists"')"
rm -f "$MS/home/forged/.clauderc-migrate-user"
ln -s "$NH/.clauderc-migrate-user" "$MS/home/forged/.clauderc-migrate-user"
cu forged pw-ok "$PUBK"
check "create-user: ...nor a symlink to somebody's real marker" "$(jqt '.ok==false and .error.code=="user_exists"')"
[[ ! -e "$MS/home/forged/.ssh" ]]; check "...nothing was authorized for that user" $?
# no sudo rights for the new user
mkdir -p "$MS/sudo-rules"; echo "(ALL) ALL" >"$MS/sudo-rules/ruled"
cu ruled pw-ok "$PUBK"
check "create-user: a sudo rule that matches the new user fails with sudo_rule" "$(jqt '.ok==false and .error.code=="sudo_rule" and (.error.message | contains("deleted"))')"
[[ "$(cat "$MS/userdel-log")" == "-r ruled" && ! -e "$MS/users/ruled" && ! -e "$MS/home/ruled" ]]; check "...and the user it just created is deleted again (userdel -r)" $?
grep -q -- "^-A userdel -r ruled$" "$MS/sudo-argv"; check "...through the sudo helper" $?
echo "(ALL) NOPASSWD: ALL" >"$MS/sudo-rules/newguy"
cu newguy pw-ok "$PUBK"
check "create-user: a reused user that has a sudo rule is refused" "$(jqt '.ok==false and .error.code=="sudo_rule"')"
[[ -e "$MS/users/newguy" && "$(wc -l <"$MS/userdel-log")" == 1 ]]; check "...but not deleted (this run didn't create it)" $?
rm -f "$MS/sudo-rules/newguy" "$MS/userdel-log"
cu newguy pw-wrong "$PUBK"
check "create-user: a wrong sudo password is refused" "$(jqt '.ok==false and .error.code=="sudo_password"')"
cu newguy "" "$PUBK"
check "create-user: a missing sudo password is refused" "$(jqt '.ok==false and .error.code=="sudo_password"')"
for badkey in "" "ssh-rsa AAAAB3Nza clauderc" "ssh-ed25519 AAAA bad comment" 'ssh-ed25519 AAAA";command="id"'; do
  cu fresh1 pw-ok "$badkey"
  check "create-user: bad key '${badkey:0:20}' refused" "$(jqt '.ok==false and .error.code=="invalid_name"')"
done
[[ ! -e "$MS/users/fresh1" ]]; check "...and no user was created for them" $?
echo none >"$MS/sudo-mode"
cu fresh2 pw-ok "$PUBK"
check "create-user: a user without sudo gets sudo_none" "$(jqt '.ok==false and .error.code=="sudo_none"')"
echo password >"$MS/sudo-mode"
touch "$MS/adduser-fail"
cu fresh3 pw-ok "$PUBK"
check "create-user: adduser failing names the step" "$(jqt '.ok==false and .error.step=="adduser" and (.error.message | contains("Couldn'"'"'t create the user"))')"
rm -f "$MS/adduser-fail"
touch "$MS/installer-fail"
cu fresh3 pw-ok "$PUBK"
check "create-user: the installer failing names the step and says why" "$(jqt '.ok==false and .error.step=="installer" and (.error.message | contains("installer exploded"))')"
[[ -e "$MS/users/fresh3" && -s "$MS/home/fresh3/.ssh/authorized_keys" ]]; check "...the user stays in place with the key authorized" $?
rm -f "$MS/installer-fail"
cu fresh3 pw-ok "$PUBK"
check "create-user: re-running continues after the failure" "$(jqt '.ok and .data.created')"
[[ "$(wc -l <"$MS/adduser-log")" == 5 ]]; check "...without a second adduser" $?   # (newguy, ab, ruled, fresh3 once failing + once ok)
# docker group, root, no-password sudo
touch "$MS/has-docker-group"
cu dockerguy pw-ok "$PUBK"
check "create-user: with a docker group the user joins it" "$(jqt '.ok')"
grep -q -- "-A usermod -aG docker dockerguy" "$MS/sudo-argv"; check "create-user: usermod -aG docker NAME via sudo" $?
cu dockerguy pw-ok "$PUBK"
check "create-user: running it again for that user works" "$(jqt '.ok and .data.created')"
[[ "$(wc -l <"$MS/usermod-log")" == 1 ]]; check "create-user: a reused user is NOT added to docker again (only the run that created them does it)" $?
rm -f "$MS/has-docker-group" "$MS/sudo-argv"
echo nopasswd >"$MS/sudo-mode"
cu nopwguy "" "$PUBK"
check "create-user: no-password sudo needs no password" "$(jqt '.ok and .data.created')"
grep -q -- "^-n adduser " "$MS/sudo-argv" && ! grep -q -- "^-A " "$MS/sudo-argv"; check "create-user: ...and uses sudo -n, never -A" $?
echo password >"$MS/sudo-mode"; rm -f "$MS/sudo-argv"
STUB_ID_U=0 cu rootmade "" "$PUBK"
check "create-user: as root no password and no sudo for adduser" "$(jqt '.ok and .data.created')"
! grep -q " adduser\|^adduser" "$MS/sudo-argv" && grep -q "rootmade" "$MS/adduser-log"; check "create-user: as root adduser runs bare" $?
# autostart_pending
UD="$WORK/units"; mkdir -p "$UD"; touch "$MS/systemd-enabled"
CLAUDERC_UNIT_DIR="$UD" cu auto1 pw-ok "$PUBK"
check "create-user: boot service enabled (no unit file to compare) -> not pending" "$(jqt '.ok and .data.autostart_pending==false')"
printf '[Service]\nUser=someone-else\n' >"$UD/claude-sessions.service"
CLAUDERC_UNIT_DIR="$UD" cu auto2 pw-ok "$PUBK"
check "create-user: boot service enabled for another user -> pending" "$(jqt '.ok and .data.autostart_pending==true')"
printf '[Service]\nUser=auto3\n' >"$UD/claude-sessions.service"
CLAUDERC_UNIT_DIR="$UD" cu auto3 pw-ok "$PUBK"
check "create-user: boot service enabled for this user -> not pending" "$(jqt '.ok and .data.autostart_pending==false')"
rm -f "$MS/systemd-enabled" "$UD/claude-sessions.service"
[[ ! -e /home/newguy && ! -e /home/auto3 && ! -e /home/rootmade ]]; check "create-user: nothing was created under /home" $?
sed -i '/^ALLOW_RUN=1$/d' "$HOME/.config/claude-launcher/config"

echo "migrate (2): verify"
cp "$SERVER/claude-setup.sh" "$HOME/claude-setup.sh"; cp "$SERVER/clauderc-team" "$HOME/.local/bin/clauderc-team"
chmod +x "$HOME/claude-setup.sh" "$HOME/.local/bin/clauderc-team"
mkdir -p "$HOME/.claude" "$MST" "$HOME/.config/claude-launcher/workers/vw/home"
chmod 700 "$HOME/.claude" "$HOME/.config/claude-launcher"; [[ ! -e "$HOME/.config/claude-launcher/env" ]] || chmod 600 "$HOME/.config/claude-launcher/env"
echo '{"claudeAiOauth":{"accessToken":"tok"}}' >"$HOME/.claude/.credentials.json"
echo '{"claudeAiOauth":{"accessToken":"tok"}}' >"$HOME/.config/claude-launcher/workers/vw/home/.credentials.json"
echo ghp_x >"$MS/gh_token"; touch "$MS/systemd-enabled"
export CLAUDERC_UNIT_DIR="$UD"
rm -f "$MS/sudo-argv"
git init -q --bare "$WORK/vremote.git"
mkgit() { mkdir -p "$HOME/projects/$1" && git -C "$HOME/projects/$1" init -q && git -C "$HOME/projects/$1" commit -q --allow-empty -m init; }
mkgit vapp; git -C "$HOME/projects/vapp" remote add origin "$WORK/vremote.git"
mkgit vdirty
jq -cn '{repos:[{dir:"vapp",path:"projects/vapp",state:"clean",remote:"x"},{dir:"vdirty",path:"projects/vdirty",state:"dirty"}],claude_logins:{main:true,workers:["vw"]}}' >"$MST/m1.json"
mkdir -p "$HOME/.ssh"; printf '%s\n%s\n' "ssh-ed25519 AAAAkeep keepme" "restrict,command=\"x\" ssh-ed25519 AAAAmig clauderc-migrate" >"$AK"
api "migrate-verify m1.json"
check "verify: everything ok -> ok true, 12 items" "$(jqt '.ok and .data.ok and (.data.items | length) == 12 and all(.data.items[]; .ok and (.detail | length) > 0)')"
check "verify: item ids in order" "$(jqt '[.data.items[].id] == ["scripts_installed","script_api_current","claude_home","launcher_config","workers","repos","remotes","claude_runs","gh","autostart","pending_logins","migrate_key_removed"]')"
check "verify: details" "$(jqt '(.data.items | map({(.id): .detail}) | add) | .repos=="2 repo(s) present" and .remotes=="1 remote(s) reachable" and .pending_logins=="nothing to sign in" and .migrate_key_removed=="removed"')"
[[ "$(cat "$AK")" == "ssh-ed25519 AAAAkeep keepme" && "$(stat -c %a "$AK")" == 600 ]]; check "verify: the migrate key line was removed, the others kept (mode 600)" $?
api "migrate-verify m1.json"
check "verify: again, no key to remove, still ok" "$(jqt '.data.ok and ((.data.items[] | select(.id=="migrate_key_removed")) | .ok and .detail=="no transfer key found")')"
api "migrate-verify"
check "verify without a manifest: only the manifest-independent items" "$(jqt '.ok and ([.data.items[].id] == ["scripts_installed","script_api_current","claude_home","launcher_config","claude_runs","gh","autostart","migrate_key_removed"]) and .data.ok')"
# failures
jq -cn '{repos:[{dir:"vapp",path:"projects/vapp",state:"clean"},{dir:"gone",path:"projects/gone",state:"clean"},{dir:"gone2",state:"dirty"}],claude_logins:{main:true,workers:["vw","ghost"]}}' >"$MST/m2.json"
api "migrate-verify m2.json"
check "verify: a missing repo and a missing worker fail with details" "$(jqt '(.data.items | map({(.id): .}) | add) as $i | (.data.ok|not) and ($i.repos.ok|not) and ($i.repos.detail | contains("missing: gone, gone2")) and ($i.workers.ok|not) and ($i.workers.detail | contains("ghost")) and $i.scripts_installed.ok and $i.gh.ok')"
rm -f "$HOME/.config/claude-launcher/workers/vw/home/.credentials.json" "$HOME/.claude/.credentials.json"
api "migrate-verify m1.json"
check "verify: missing logins are information only (pending_logins ok:true, lists them)" "$(jqt '.data.ok and ((.data.items[] | select(.id=="pending_logins")) | .ok and .detail=="still to sign in: main, vw")')"
echo '{"claudeAiOauth":{}}' >"$HOME/.claude/.credentials.json"
api "migrate-verify m1.json"
check "verify: a logged-out credentials file counts as no login" "$(jqt '((.data.items[] | select(.id=="pending_logins")) | .detail=="still to sign in: main, vw")')"
git -C "$HOME/projects/vapp" remote set-url origin "$WORK/no-such-remote.git"
mkdir -p "$HOME/projects/vempty" && git -C "$HOME/projects/vempty" init -q
jq -cn '{repos:[{dir:"vapp",path:"projects/vapp",state:"clean"},{dir:"vempty",path:"projects/vempty",state:"clean"}],claude_logins:{main:false,workers:[]}}' >"$MST/m3.json"
chmod 755 "$HOME/.claude"; chmod 644 "$HOME/.config/claude-launcher/env" 2>/dev/null || { touch "$HOME/.config/claude-launcher/env"; chmod 644 "$HOME/.config/claude-launcher/env"; }
rm -f "$MS/gh_token" "$MS/systemd-enabled" "$HOME/.local/bin/claude-push"
api "migrate-verify m3.json"
check "verify: unreachable remote, empty clone, bad modes, no gh, no autostart, missing script: each fails with a clear detail" "$(jqt '
  (.data.items | map({(.id): .}) | add) as $i | (.data.ok|not) and
  ($i.remotes.ok|not) and ($i.remotes.detail | contains("vapp")) and
  ($i.repos.ok|not) and ($i.repos.detail | contains("vempty")) and
  ($i.claude_home.ok|not) and ($i.claude_home.detail | contains("755")) and
  ($i.launcher_config.ok|not) and ($i.launcher_config.detail | contains("env file has mode 644")) and
  ($i.gh.ok|not) and ($i.autostart.ok|not) and ($i.autostart.detail | contains("claude-autostart")) and
  ($i.scripts_installed.ok|not) and ($i.scripts_installed.detail | contains("claude-push"))')"
chmod 700 "$HOME/.claude"; chmod 600 "$HOME/.config/claude-launcher/env"; cp "$SERVER/claude-push" "$HOME/.local/bin/claude-push"; chmod +x "$HOME/.local/bin/claude-push"
api "migrate-verify nope.json"
check "verify: unknown manifest" "$(jqt '.ok==false and .error.code=="invalid_name"')"
echo '[1]' >"$MST/bad.json"
api "migrate-verify bad.json"
check "verify: unreadable manifest" "$(jqt '.ok==false and .error.code=="invalid_name"')"
[[ ! -e "$MS/claude-logout-called" ]]; check "verify never calls claude auth logout" $?
unset CLAUDERC_UNIT_DIR

echo "migrate (2): sign out the old server"
rm -f "$MS/gh-logout-argv"
fix_signout() {
  mkdir -p "$HOME/.claude" "$HOME/.config/claude-launcher/workers/vw/home" "$HOME/.config/claude-launcher/workers/vx/home" "$MIG" "$HOME/.config/claude-setup"
  echo '{"claudeAiOauth":{"accessToken":"tok"}}' | tee "$HOME/.claude/.credentials.json" "$HOME/.config/claude-launcher/workers/vw/home/.credentials.json" "$HOME/.config/claude-launcher/workers/vx/home/.credentials.json" >/dev/null
  echo ghp_x >"$MS/gh_token"
  echo key >"$MIG/key"; echo pass >"$MIG/passphrase"; echo pass >"$MST/restore-pass.abc"
  mkdir -p "$HOME/backups" "$MST"; touch "$HOME/backups/migrate-20260101-000000.gpg" "$HOME/backups/incoming-20260101-000000.gpg"
  tmux has-session -t "=mig-a" 2>/dev/null || tmux new-session -d -s mig-a -c "$HOME" "claude --remote-control mig-a; exec bash"
  printf 'mig-a\t%s\n' "$HOME" >"$LIST"
}
fix_signout
api "migrate-signout-old claude"
check "signout claude: done [claude]" "$(jqt '.ok and .data == {done:["claude"]}')"
[[ ! -e "$HOME/.claude/.credentials.json" && -e "$HOME/.config/claude-launcher/workers/vw/home/.credentials.json" && -e "$MS/gh_token" ]] && tmux has-session -t "=mig-a" 2>/dev/null; check "signout claude: only ~/.claude/.credentials.json is gone" $?
# Task 6: whatever is ticked, the transfer key folder, the pass files and the exported backups go (an incoming file is not ours to delete)
[[ ! -e "$MIG" && ! -e "$MST/restore-pass.abc" && ! -e "$HOME/backups/migrate-20260101-000000.gpg" && -e "$HOME/backups/incoming-20260101-000000.gpg" ]]; check "signout claude: ...and the migrate key folder, passphrase files and ~/backups/migrate-*.gpg are deleted too (incoming-*.gpg stays)" $?
api "migrate-signout-old claude"
check "signout claude: nothing left to do -> done []" "$(jqt '.ok and .data == {done:[]}')"
api "migrate-signout-old workers"
check "signout workers: done [workers]" "$(jqt '.ok and .data == {done:["workers"]}')"
[[ ! -e "$HOME/.config/claude-launcher/workers/vw/home/.credentials.json" && ! -e "$HOME/.config/claude-launcher/workers/vx/home/.credentials.json" && -d "$HOME/.config/claude-launcher/workers/vw/home" ]]; check "signout workers: every worker's token file is gone, the folders stay" $?
api "migrate-signout-old github"
check "signout github: done [github]" "$(jqt '.ok and .data == {done:["github"]}')"
[[ "$(cat "$MS/gh-logout-argv")" == "auth logout --hostname github.com" && ! -e "$MS/gh_token" ]]; check "signout github: gh auth logout --hostname github.com (local only)" $?
api "migrate-signout-old github"
check "signout github: not signed in -> done []" "$(jqt '.ok and .data == {done:[]}')"
api "migrate-signout-old autostart"
check "signout autostart: done [autostart]" "$(jqt '.ok and .data == {done:["autostart"]}')"
tmux has-session -t "=mig-a" 2>/dev/null; [[ $? -ne 0 ]]; check "signout autostart: the managed tmux session was killed" $?
[[ ! -e "$LIST" && "$(cat "$LIST.migrated")" == "mig-a	$HOME" ]]; check "signout autostart: the saved list is set aside (.migrated copy kept), so restore does nothing" $?
[[ ! -e "$MIG" ]]; check "signout autostart: the migrate key and pass files are gone" $?
OUT="$(claude-autostart restore 2>&1)"; [[ "$OUT" == *"no saved sessions"* ]] && ! tmux has-session -t "=mig-a" 2>/dev/null; check "signout autostart: claude-autostart restore is now a no-op" $?
fix_signout
api "migrate-signout-old claude workers github autostart"
check "signout all four at once" "$(jqt '.ok and .data == {done:["claude","workers","github","autostart"]}')"
[[ ! -e "$MS/claude-logout-called" ]]; check "signout: claude auth logout is never called" $?
[[ "$(realsnap)" == "$REAL_BEFORE" ]]; check "signout / verify / create-user touched nothing outside the test HOME" $?

echo "migrate (2): reboot"
rm -f "$MS/systemctl-log" "$MS/sudo-argv"
export CLAUDERC_REBOOT_DELAY=2
api "migrate-reboot" "pw-ok"
check "reboot: refused while Run-a-command is off" "$(jqt '.ok==false and .error.code=="run_disabled"')"
echo 'ALLOW_RUN=1' >>"$HOME/.config/claude-launcher/config"
# Task 6: no reboot while a migrate job runs
jq -cn --argjson n "$(date +%s)" '{job:"send",phase:"transfer",message:"x",pct:60,bytes:5,file:"",started:$n,updated:$n,error:"",pid:null}' >"$MST/status.json"
api "migrate-reboot" "pw-ok"
check "reboot: busy while a migrate job is running" "$(jqt '.ok==false and .error.code=="busy"')"
sleep 3; ! grep -q reboot "$MS/systemctl-log" 2>/dev/null; check "reboot: ...and nothing rebooted" $?
jq -cn '{job:"send",phase:"done",message:"x",pct:100,bytes:5,file:"",started:1,updated:1,error:"",pid:null}' >"$MST/status.json"
api "migrate-reboot" "pw-wrong"
check "reboot: a wrong sudo password is refused" "$(jqt '.ok==false and .error.code=="sudo_password"')"
sleep 3; [[ ! -e "$MS/systemctl-log" ]] || ! grep -q reboot "$MS/systemctl-log"; check "reboot: ...and nothing rebooted" $?
api "migrate-reboot" "pw-ok"
REPLY_T="$(date +%s%N)"
check "reboot: answers {rebooting:true}" "$(jqt '.ok and .data == {rebooting:true}')"
! grep -q reboot "$MS/systemctl-log" 2>/dev/null; check "reboot: the reply came before the reboot was recorded" $?
for _ in $(seq 1 40); do grep -q reboot "$MS/systemctl-log" 2>/dev/null && break; sleep 0.25; done
grep -q "reboot" "$MS/systemctl-log"; check "reboot: the stub systemctl reboot was called a moment later" $?
[[ "$(awk '/reboot/ {print $1; exit}' "$MS/systemctl-log")" -gt "$REPLY_T" ]]; check "reboot: its timestamp is after the reply" $?
grep -q -- "^-A systemctl reboot$" "$MS/sudo-argv"; check "reboot: via sudo with the askpass" $?
grep -rq "pw-ok\|pw-wrong" "$MS/sudo-argv" "$MS/systemctl-log" "$APILOG"; [[ $? -ne 0 ]]; check "reboot: the password is in no argv and not in the api log" $?
sleep 1; [[ -z "$(find "$WORK/tmp" -name pw 2>/dev/null)" ]]; check "reboot: the password file is removed after the reboot call" $?
rm -f "$MS/systemctl-log" "$MS/sudo-argv"
STUB_ID_U=0 api "migrate-reboot" ""
check "reboot as root: needs no password" "$(jqt '.ok and .data.rebooting')"
for _ in $(seq 1 40); do grep -q reboot "$MS/systemctl-log" 2>/dev/null && break; sleep 0.25; done
grep -q reboot "$MS/systemctl-log" && [[ ! -s "$MS/sudo-argv" ]]; check "reboot as root: systemctl reboot without sudo" $?
sed -i '/^ALLOW_RUN=1$/d' "$HOME/.config/claude-launcher/config"
unset CLAUDERC_REBOOT_DELAY TMPDIR
rm -f "$MS/sudo-mode" "$HOME/claude-setup.sh"
if [[ -f "$WORK/authorized_keys.orig2" ]]; then cp "$WORK/authorized_keys.orig2" "$AK"; else rm -f "$AK"; fi

echo "team MCP server"
OUT="$(python3 "$HERE/test-team.py" 2>&1)"; check "clauderc-team MCP self-check" $?

echo
echo "$pass passed, $failn failed"
((failn == 0))
