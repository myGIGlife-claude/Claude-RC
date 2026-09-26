#!/usr/bin/env bash
# claude-setup.sh — check logins, create or open projects, and run Claude
# with Remote Control in one detached tmux session per project.
#
#   claude-setup.sh                      interactive menu
#   claude-setup.sh --api <cmd> [args]   non-interactive; prints one JSON object
#
# Nothing personal lives in this file. Per-machine choices are read from
# ~/.config/claude-launcher/config (created on first run, see config.example).

set -uo pipefail

SCRIPT_VERSION="2.0.0"
SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/claude-launcher"
CONFIG_FILE="$CONFIG_DIR/config"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/claude-launcher"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/claude-launcher"
REGISTRY_FILE="$STATE_DIR/sessions.list"
LOCK_FILE="$STATE_DIR/api.lock"

CLAUDE_LOGIN_SESSION="claude-login"
AWS_LOGIN_SESSION="aws-sso-login"

PROJECT_RE='^[A-Za-z0-9._-]{1,100}$'
REPO_RE='^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$'
OWNER_RE='^[A-Za-z0-9-]{1,39}$'
REQUIRED_SCOPES=(repo read:org workflow)

API_MODE=0
EMITTED=0

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

load_config() {
  PROJECTS_DIR="$HOME/projects"
  DEFAULT_OWNER=""
  DEFAULT_VISIBILITY="private"
  SESSION_PREFIX=""
  AWS_PROFILE_NAME=""
  AWS_DEFAULT_REGION_NAME="us-east-1"
  EXTRA_PATH=""
  REPOS_CACHE_TTL=600

  if [[ ! -f "$CONFIG_FILE" ]]; then
    mkdir -p "$CONFIG_DIR"
    cat >"$CONFIG_FILE" <<'EOF'
# cLaudeRC server config — see config.example in the repo for every option.
PROJECTS_DIR="$HOME/projects"
DEFAULT_OWNER=""
DEFAULT_VISIBILITY="private"
SESSION_PREFIX=""
AWS_PROFILE_NAME=""
AWS_DEFAULT_REGION_NAME="us-east-1"
EOF
    chmod 600 "$CONFIG_FILE"
  fi
  # shellcheck source=/dev/null
  . "$CONFIG_FILE"

  # Forced-command SSH sessions get a bare PATH, so add the usual install
  # locations after it, and let EXTRA_PATH win over everything.
  PATH="$PATH:$HOME/.local/bin:$HOME/bin:$HOME/.npm-global/bin:/usr/local/bin"
  [[ -n "$EXTRA_PATH" ]] && PATH="$EXTRA_PATH:$PATH"
  export PATH

  mkdir -p "$STATE_DIR" "$CACHE_DIR"
  chmod 700 "$STATE_DIR" "$CACHE_DIR" 2>/dev/null || true
  touch "$REGISTRY_FILE"

  # Never use an API key for Claude — always the claude.ai subscription login.
  unset ANTHROPIC_API_KEY
  export GH_PROMPT_DISABLED=1 GH_NO_UPDATE_NOTIFIER=1 GIT_TERMINAL_PROMPT=0 AWS_PAGER=""
  AWS_ARGS=()
  [[ -n "$AWS_PROFILE_NAME" ]] && AWS_ARGS=(--profile "$AWS_PROFILE_NAME")
}

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

if [[ -t 2 ]]; then
  C_RED=$'\e[31m' C_GREEN=$'\e[32m' C_YELLOW=$'\e[33m' C_BOLD=$'\e[1m' C_OFF=$'\e[0m'
else
  C_RED="" C_GREEN="" C_YELLOW="" C_BOLD="" C_OFF=""
fi

# Progress text: stderr only, and silent in --api mode.
say()  { ((API_MODE)) || printf '%s\n' "$*" >&2; }
ok()   { say "${C_GREEN}✔${C_OFF} $*"; }
warn() { say "${C_YELLOW}!${C_OFF} $*"; }
fail() { say "${C_RED}✘${C_OFF} $*"; }

# Result carriers. In --api mode they print the one JSON object on fd 3 and
# exit; in menu mode they set RESULT_* for the caller and return.
RESULT_DATA="{}" RESULT_CODE="" RESULT_MSG="" RESULT_EXTRA="{}"

api_ok() {
  RESULT_DATA="${1:-{\}}" RESULT_CODE="" RESULT_MSG=""
  if ((API_MODE)); then
    jq -cn --argjson d "$RESULT_DATA" '{ok:true,data:$d}' >&3
    EMITTED=1
    exit 0
  fi
  return 0
}

# api_err <code> <message> [extra-json-object] [exit-code]
api_err() {
  RESULT_CODE="$1" RESULT_MSG="$2" RESULT_EXTRA="${3:-{\}}"
  if ((API_MODE)); then
    jq -cn --arg c "$1" --arg m "$2" --argjson x "$RESULT_EXTRA" \
      '{ok:false,error:({code:$c,message:$m} + $x)}' >&3
    EMITTED=1
    exit "${4:-1}"
  fi
  return 1
}

bad_args() { api_err bad_args "$1" '{}' 2; }

on_exit() {
  local rc=$?
  if ((API_MODE)) && ((EMITTED == 0)); then
    jq -cn --arg m "unexpected exit ($rc)" '{ok:false,error:{code:"internal",message:$m}}' >&3
  fi
}

need() {
  command -v "$1" >/dev/null 2>&1 || { api_err internal "'$1' is not installed on the server"; return 1; }
}

# The first tmux call forks the tmux server, which would inherit and hold open
# the SSH channel (fd 3) and the lock (fd 9). Never let it.
tmux() { command tmux "$@" 3>&- 9>&-; }

# Run with a hard time limit so nothing on the phone waits forever.
t() { local s="$1"; shift; timeout --kill-after=5 "$s" "$@"; }

# ---------------------------------------------------------------------------
# Validation and naming
# ---------------------------------------------------------------------------

valid_project() { [[ "$1" =~ $PROJECT_RE && "$1" != "." && "$1" != ".." ]]; }
valid_repo()    { [[ "$1" =~ $REPO_RE ]]; }
valid_owner()   { [[ "$1" =~ $OWNER_RE ]]; }

# tmux turns '.' and ':' into '_' in session names; do the same up front.
session_name() { local s="${SESSION_PREFIX}$1"; s="${s//./_}"; printf '%s' "${s//:/_}"; }
project_dir()  { printf '%s/%s' "$PROJECTS_DIR" "$1"; }

# ---------------------------------------------------------------------------
# Locking — two phone taps must not run new/open at the same time
# ---------------------------------------------------------------------------

take_lock() {
  exec 9>>"$LOCK_FILE"
  flock -n 9 || { api_err busy "Another create/open is still running. Try again in a moment."; return 1; }
}

# ---------------------------------------------------------------------------
# Login checks
# ---------------------------------------------------------------------------

claude_logged_in() {
  command -v claude >/dev/null 2>&1 || return 1
  local out
  out="$(t 20 claude auth status 2>&1 </dev/null)"
  local rc=$?
  if jq -e 'type=="object"' >/dev/null 2>&1 <<<"$out"; then
    jq -e '(.loggedIn // .logged_in // false) == true' >/dev/null <<<"$out"
    return
  fi
  if grep -qiE 'not (logged|signed) in|no credentials|please (log|sign) in' <<<"$out"; then
    return 1
  fi
  ((rc == 0)) && grep -qiE 'logged in|signed in|authenticated|subscription' <<<"$out" && return 0
  # Older versions without `auth status`: look for the OAuth credentials file.
  grep -qs 'claudeAiOauth' "$HOME/.claude/.credentials.json"
}

# Prints the GitHub login on success.
github_user() { t 20 gh api user --jq .login 2>/dev/null </dev/null; }

github_scopes() {
  t 20 gh api -i user 2>/dev/null </dev/null |
    tr -d '\r' | awk -F': ' 'tolower($1)=="x-oauth-scopes"{print $2}' | tr ',' '\n' | sed 's/^ *//;s/ *$//' | grep -v '^$'
}

# Prints missing scopes, one per line. admin:org / write:org cover read:org.
github_missing_scopes() {
  local have
  have="$(github_scopes)"
  local s
  for s in "${REQUIRED_SCOPES[@]}"; do
    if [[ "$s" == "read:org" ]]; then
      grep -qxE 'read:org|write:org|admin:org' <<<"$have" || echo "$s"
    else
      grep -qx "$s" <<<"$have" || echo "$s"
    fi
  done
}

aws_identity() { t 20 aws sts get-caller-identity "${AWS_ARGS[@]}" --output json 2>/dev/null </dev/null; }

# ---------------------------------------------------------------------------
# Core actions (shared by the menu and --api)
# ---------------------------------------------------------------------------

do_status() {
  local claude_ok=false gh_user="" gh_ok=false missing="[]" aws_ok=false aws_json="null"

  if claude_logged_in; then claude_ok=true; fi

  if command -v gh >/dev/null 2>&1 && gh_user="$(github_user)" && [[ -n "$gh_user" ]]; then
    gh_ok=true
    missing="$(github_missing_scopes | jq -R . | jq -sc .)"
  fi

  local ident
  if command -v aws >/dev/null 2>&1 && ident="$(aws_identity)" && [[ -n "$ident" ]]; then
    aws_ok=true
    aws_json="$(jq -c '{account:.Account, arn:.Arn, user_id:.UserId}' <<<"$ident")"
  fi

  api_ok "$(jq -cn \
    --argjson c "$claude_ok" --argjson g "$gh_ok" --arg gu "$gh_user" --argjson gm "$missing" \
    --argjson a "$aws_ok" --argjson ai "$aws_json" --arg ap "$AWS_PROFILE_NAME" \
    --arg host "$(hostname -s 2>/dev/null || hostname)" --arg v "$SCRIPT_VERSION" \
    --argjson sso "$(aws_sso_configured && echo true || echo false)" '{
      claude:{logged_in:$c},
      github:{logged_in:$g, user:(if $gu=="" then null else $gu end), missing_scopes:$gm},
      aws:{logged_in:$a, identity:$ai, profile:(if $ap=="" then "default" else $ap end), sso_configured:$sso},
      hostname:$host, version:$v
    }')"
}

require_github() {
  command -v gh >/dev/null 2>&1 || { api_err not_logged_in_github "GitHub CLI (gh) is not installed on the server."; return 1; }
  GH_USER="$(github_user)"
  [[ -n "$GH_USER" ]] || { api_err not_logged_in_github "GitHub is not logged in on the server."; return 1; }
}

do_owners() {
  require_github || return 1
  local orgs
  orgs="$(t 30 gh api --paginate 'user/memberships/orgs?state=active&per_page=100' \
    --jq '.[] | {login:.organization.login, role:.role}' 2>/dev/null </dev/null | jq -sc .)" || orgs="[]"
  [[ -n "$orgs" ]] || orgs="[]"
  api_ok "$(jq -cn --arg u "$GH_USER" --argjson o "$orgs" --arg d "${DEFAULT_OWNER:-}" \
    '{user:$u, orgs:$o, default_owner:(if $d=="" then $u else $d end)}')"
}

running_sessions_json() {
  { tmux list-sessions -F '#{session_name}' 2>/dev/null || true; } | jq -R . | jq -sc .
}

local_dirs_json() {
  [[ -d "$PROJECTS_DIR" ]] || { echo '[]'; return; }
  { find "$PROJECTS_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null || true; } | jq -R . | jq -sc .
}

fetch_repos() {
  local cache="$CACHE_DIR/repos.json" refresh="$1"
  if [[ "$refresh" != 1 && -s "$cache" ]]; then
    local age=$(($(date +%s) - $(stat -c %Y "$cache")))
    if ((age < REPOS_CACHE_TTL)); then cat "$cache"; return 0; fi
  fi
  local out
  out="$(t 50 gh api --paginate \
    'user/repos?per_page=100&affiliation=owner,collaborator,organization_member&sort=pushed' \
    --jq '.[] | {full_name, name, owner:.owner.login, owner_type:.owner.type, private, pushed_at, archived}' \
    2>/dev/null </dev/null | jq -sc 'unique_by(.full_name)')" || return 1
  [[ -n "$out" ]] || return 1
  printf '%s' "$out" >"$cache.tmp" && mv "$cache.tmp" "$cache"
  printf '%s' "$out"
}

do_repos() {
  local refresh=0
  [[ "${1:-}" == "--refresh" ]] && refresh=1
  require_github || return 1
  local repos
  repos="$(fetch_repos "$refresh")" || { api_err internal "Could not list GitHub repos."; return 1; }
  api_ok "$(jq -c --argjson l "$(local_dirs_json)" --argjson r "$(running_sessions_json)" \
    --arg p "$SESSION_PREFIX" --arg gu "$GH_USER" '
    {user:$gu, repos: (map(. + {
      local: (.name as $n | $l | index($n) != null),
      running: (($p + .name | gsub("[.:]"; "_")) as $s | $r | index($s) != null)
    }) | sort_by(.pushed_at) | reverse)}' <<<"$repos")"
}

sessions_json() {
  # tmux turns tabs into '_' in formats, so use a separator no path contains.
  local fmt='#{session_name}|#|#{session_created}|#|#{session_attached}|#|#{@claude_project}|#|#{@claude_dir}|#|#{pane_start_command}|#|#{pane_current_path}'
  { tmux list-panes -a -F "$fmt" 2>/dev/null || true; } |
    awk -F'[|]#[|]' '!seen[$1]++' |
    jq -Rc --arg login1 "$CLAUDE_LOGIN_SESSION" --arg login2 "$AWS_LOGIN_SESSION" '
      split("|#|") as $f
      | select($f[0] != $login1 and $f[0] != $login2)
      | select(($f[3] // "") != "" or (($f[5] // "") | test("claude.*remote-control")))
      | {name:$f[0],
         project:(if ($f[3] // "") != "" then $f[3] else $f[0] end),
         dir:(if ($f[4] // "") != "" then $f[4] else $f[6] end),
         started_at:($f[1] | tonumber),
         attached:(($f[2] | tonumber) > 0)}' |
    jq -sc 'sort_by(.started_at)'
}

do_sessions() {
  local s
  s="$(sessions_json)"
  [[ -n "$s" ]] || s="[]"
  api_ok "$(jq -cn --argjson s "$s" --argjson now "$(date +%s)" \
    '{now:$now, sessions:($s | map(. + {uptime_seconds:($now - .started_at)}))}')"
}

registry_add() {
  grep -qxF -- "$1" "$REGISTRY_FILE" 2>/dev/null || printf '%s\n' "$1" >>"$REGISTRY_FILE"
}
registry_remove() {
  local tmp="$REGISTRY_FILE.tmp"
  grep -vxF -- "$1" "$REGISTRY_FILE" >"$tmp" 2>/dev/null
  mv "$tmp" "$REGISTRY_FILE"
}

# start_session <project> — sets STARTED_SESSION and ALREADY_RUNNING.
start_session() {
  local project="$1" dir sess
  dir="$(project_dir "$project")"
  sess="$(session_name "$project")"
  STARTED_SESSION="$sess" ALREADY_RUNNING=false
  need tmux || return 1
  [[ -d "$dir" ]] || { api_err invalid_name "No project folder named '$project' on the server."; return 1; }
  if tmux has-session -t "=$sess" 2>/dev/null; then
    ALREADY_RUNNING=true
    registry_add "$project"
    return 0
  fi
  local claude_bin
  claude_bin="$(command -v claude)" || { api_err internal "'claude' is not installed on the server."; return 1; }
  tmux new-session -d -s "$sess" -c "$dir" -x 200 -y 50 \
    env -u ANTHROPIC_API_KEY "$claude_bin" --remote-control "$project" \; \
    set-option @claude_project "$project" \; \
    set-option @claude_dir "$dir" >/dev/null 2>&1 ||
    { api_err internal "tmux could not start the session."; return 1; }
  registry_add "$project"
}

do_start() {
  [[ $# -eq 1 ]] || { bad_args "usage: start <project>"; return 1; }
  valid_project "$1" || { api_err invalid_name "Invalid project name."; return 1; }
  start_session "$1" || return 1
  api_ok "$(jq -cn --arg s "$STARTED_SESSION" --arg p "$(project_dir "$1")" --argjson a "$ALREADY_RUNNING" \
    '{session:$s, path:$p, already_running:$a}')"
}

do_stop() {
  [[ $# -eq 1 ]] || { bad_args "usage: stop <project>"; return 1; }
  valid_project "$1" || { api_err invalid_name "Invalid project name."; return 1; }
  local sess stopped=false
  sess="$(session_name "$1")"
  if tmux has-session -t "=$sess" 2>/dev/null; then
    tmux kill-session -t "=$sess" 2>/dev/null && stopped=true
  fi
  registry_remove "$1"
  api_ok "$(jq -cn --arg s "$sess" --argjson st "$stopped" '{session:$s, stopped:$st}')"
}

do_tail() {
  local project="" lines=40
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --lines) [[ "${2:-}" =~ ^[0-9]{1,4}$ ]] || { bad_args "--lines needs a number"; return 1; }; lines="$2"; shift 2 ;;
      -*) bad_args "unknown option $1"; return 1 ;;
      *) [[ -z "$project" ]] || { bad_args "usage: tail <project> [--lines N]"; return 1; }; project="$1"; shift ;;
    esac
  done
  [[ -n "$project" ]] || { bad_args "usage: tail <project> [--lines N]"; return 1; }
  valid_project "$project" || { api_err invalid_name "Invalid project name."; return 1; }
  ((lines < 1)) && lines=1
  ((lines > 200)) && lines=200
  local sess text
  sess="$(session_name "$project")"
  tmux has-session -t "=$sess" 2>/dev/null || { api_err invalid_name "No running session for '$project'."; return 1; }
  text="$(tmux capture-pane -p -J -t "=$sess:" -S -500 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n "$lines")"
  api_ok "$(jq -cn --arg s "$sess" --arg t "$text" --argjson n "$lines" '{session:$s, lines:$n, text:$t}')"
}

do_new() {
  local name="" owner="" vis="" start=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --owner) owner="${2:-}"; shift 2 || { bad_args "--owner needs a value"; return 1; } ;;
      --visibility) vis="${2:-}"; shift 2 || { bad_args "--visibility needs a value"; return 1; } ;;
      --start) start=1; shift ;;
      -*) bad_args "unknown option $1"; return 1 ;;
      *) [[ -z "$name" ]] || { bad_args "usage: new <name> --owner <owner> --visibility private|public [--start]"; return 1; }; name="$1"; shift ;;
    esac
  done
  [[ -n "$name" ]] || { bad_args "usage: new <name> --owner <owner> --visibility private|public [--start]"; return 1; }
  valid_project "$name" || { api_err invalid_name "Names may use letters, digits, '.', '_' and '-' (max 100)."; return 1; }
  [[ -z "$vis" ]] && vis="$DEFAULT_VISIBILITY"
  [[ "$vis" == private || "$vis" == public ]] || { bad_args "--visibility must be private or public"; return 1; }

  take_lock || return 1
  require_github || return 1
  [[ -z "$owner" ]] && owner="${DEFAULT_OWNER:-$GH_USER}"
  valid_owner "$owner" || { api_err invalid_name "Invalid owner."; return 1; }

  local dir repo="$owner/$name"
  dir="$(project_dir "$name")"
  if [[ -e "$dir" ]] && [[ -n "$(ls -A "$dir" 2>/dev/null)" ]]; then
    api_err repo_exists "A folder named '$name' already exists on the server."; return 1
  fi
  if t 20 gh repo view "$repo" --json name >/dev/null 2>&1 </dev/null; then
    api_err repo_exists "The repo $repo already exists on GitHub. Open it from Projects instead."; return 1
  fi

  say "Creating $vis repo $repo…"
  local err
  if ! err="$(t 50 gh repo create "$repo" "--$vis" 2>&1 </dev/null)"; then
    api_err internal "gh repo create failed: $(tail -n 3 <<<"$err")"; return 1
  fi
  mkdir -p "$PROJECTS_DIR"
  rmdir "$dir" 2>/dev/null
  if ! err="$(t 50 gh repo clone "$repo" "$dir" 2>&1 </dev/null)"; then
    # Empty repo clone can fail on older git; fall back to a local init.
    mkdir -p "$dir" && git -C "$dir" init -q -b main >/dev/null 2>&1 &&
      git -C "$dir" remote add origin "https://github.com/$repo.git" ||
      { api_err internal "Repo created, but the folder could not be set up: $(tail -n 2 <<<"$err")"; return 1; }
  fi
  rm -f "$CACHE_DIR/repos.json"
  ok "Created $repo in $dir"

  local sess=""
  if ((start)); then
    start_session "$name" || return 1
    sess="$STARTED_SESSION"
  fi
  api_ok "$(jq -cn --arg r "$repo" --arg u "https://github.com/$repo" --arg p "$dir" --arg s "$sess" --arg v "$vis" \
    '{repo:$r, url:$u, path:$p, visibility:$v, session:(if $s=="" then null else $s end)}')"
}

# Background clone worker (internal; not reachable through the runner).
clone_worker() {
  local repo="$1" dir="$2" start="$3" status="$4" err
  if err="$(gh repo clone "$repo" "$dir" 2>&1 </dev/null)"; then
    local sess=""
    if [[ "$start" == 1 ]]; then
      API_MODE=0
      start_session "$(basename "$dir")" && sess="$STARTED_SESSION"
    fi
    jq -cn --arg s "$sess" '{state:"done", session:(if $s=="" then null else $s end)}' >"$status"
  else
    rm -rf "$dir"
    jq -cn --arg m "$(tail -n 3 <<<"$err")" '{state:"failed", message:$m}' >"$status"
  fi
  rm -f "$CACHE_DIR/repos.json"
}

do_open() {
  local repo="" start=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --start) start=1; shift ;;
      -*) bad_args "unknown option $1"; return 1 ;;
      *) [[ -z "$repo" ]] || { bad_args "usage: open <owner/repo> [--start]"; return 1; }; repo="$1"; shift ;;
    esac
  done
  [[ -n "$repo" ]] || { bad_args "usage: open <owner/repo> [--start]"; return 1; }
  valid_repo "$repo" || { api_err invalid_name "Invalid repo name."; return 1; }
  local name="${repo#*/}"
  valid_project "$name" || { api_err invalid_name "Invalid repo name."; return 1; }

  take_lock || return 1
  require_github || return 1
  local dir action sess="" pending=false
  dir="$(project_dir "$name")"

  if [[ -d "$dir" ]]; then
    if ! git -C "$dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
      api_err folder_dirty "The folder $dir exists but is not a git repo, so it wasn't updated. Start anyway?" \
        "$(jq -cn --arg p "$dir" '{path:$p}')"; return 1
    fi
    if [[ -n "$(git -C "$dir" status --porcelain 2>/dev/null)" ]]; then
      api_err folder_dirty "The folder has uncommitted changes, so it wasn't updated. Start anyway?" \
        "$(jq -cn --arg p "$dir" '{path:$p}')"; return 1
    fi
    say "Pulling $repo…"
    local err
    if ! git -C "$dir" rev-parse --verify -q HEAD >/dev/null; then
      action="skipped_empty"
    elif err="$(t 50 git -C "$dir" pull --ff-only 2>&1 </dev/null)"; then
      action="pulled"
    else
      api_err internal "git pull failed: $(tail -n 3 <<<"$err")" "$(jq -cn --arg p "$dir" '{path:$p}')"; return 1
    fi
    if ((start)); then start_session "$name" || return 1; sess="$STARTED_SESSION"; fi
  else
    say "Cloning $repo…"
    mkdir -p "$PROJECTS_DIR"
    local status="$STATE_DIR/clone-${repo//\//__}.json"
    rm -f "$status"
    # Detach fully (fd 3 is the SSH channel) but keep the lock (fd 9) held
    # until the clone finishes.
    setsid "$SCRIPT_PATH" --clone-worker "$repo" "$dir" "$start" "$status" \
      </dev/null >/dev/null 2>&1 3>&- &
    local waited=0
    while [[ ! -s "$status" ]] && ((waited < 45)); do sleep 1; ((waited++)); done
    if [[ -s "$status" ]]; then
      if [[ "$(jq -r .state "$status")" == failed ]]; then
        api_err internal "Clone failed: $(jq -r .message "$status")"; return 1
      fi
      action="cloned"
      sess="$(jq -r '.session // ""' "$status")"
    else
      action="cloning"
      pending=true
    fi
  fi
  api_ok "$(jq -cn --arg p "$dir" --arg a "$action" --arg s "$sess" --argjson pe "$pending" --arg r "$repo" \
    '{repo:$r, path:$p, action:$a, pending:$pe, session:(if $s=="" then null else $s end)}')"
}

# ---------------------------------------------------------------------------
# Login flows (secrets arrive on stdin only)
# ---------------------------------------------------------------------------

pane_text() { tmux capture-pane -p -J -t "=$1:" -S -200 2>/dev/null; }

# Start a throwaway tmux session that keeps its output after the command exits.
start_login_session() {
  local name="$1"; shift
  tmux kill-session -t "=$name" 2>/dev/null
  tmux new-session -d -s "$name" -x 1000 -y 60 \
    bash -c '"$@"; echo "[exit $?]"; sleep 900' login "$@" >/dev/null 2>&1
}

do_login_claude_start() {
  need tmux || return 1
  command -v claude >/dev/null 2>&1 || { api_err internal "'claude' is not installed on the server."; return 1; }
  start_login_session "$CLAUDE_LOGIN_SESSION" env -u ANTHROPIC_API_KEY claude auth login ||
    { api_err internal "Could not start the login session."; return 1; }

  local i text url="" enters=0
  for ((i = 0; i < 60; i++)); do
    sleep 0.5
    text="$(pane_text "$CLAUDE_LOGIN_SESSION")"
    url="$(grep -oE 'https://[^[:space:]]*(oauth|authorize|login)[^[:space:]]*' <<<"$text" | tail -n 1)"
    [[ -n "$url" ]] && break
    if grep -q '\[exit ' <<<"$text"; then break; fi
    # A method picker: pick the claude.ai subscription entry, never an API key.
    if ((enters < 3)) && grep -qiE 'subscription|claude\.ai|select login method|choose' <<<"$text"; then
      local line
      line="$(grep -iE '^\s*(❯|>)?\s*[0-9]+\..*(subscription|claude\.ai|Claude account)' <<<"$text" | head -n 1)"
      if [[ "$line" =~ ([0-9]+)\. ]]; then
        tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" "${BASH_REMATCH[1]}"
        sleep 0.3
      fi
      tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" Enter
      ((enters++))
      sleep 1
    elif ((enters < 3)) && grep -qiE 'press enter|enter to continue' <<<"$text"; then
      tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" Enter
      ((enters++))
    fi
  done
  if [[ -z "$url" ]]; then
    local tailtext
    tailtext="$(tail -n 15 <<<"$text")"
    tmux kill-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null
    api_err internal "No login URL appeared within 30 s." "$(jq -cn --arg t "$tailtext" '{pane:$t}')"; return 1
  fi
  api_ok "$(jq -cn --arg u "$url" '{url:$u, session:"'"$CLAUDE_LOGIN_SESSION"'"}')"
}

read_secret_line() { local v=""; IFS= read -r v || true; v="${v//$'\r'/}"; printf '%s' "$v"; }

do_login_claude_code() {
  local code
  code="$(read_secret_line)"
  exec 0</dev/null
  code="${code//[[:space:]]/}"
  [[ "$code" =~ ^[A-Za-z0-9#_.~=+/-]{4,1024}$ ]] || { api_err invalid_name "That doesn't look like a login code."; return 1; }
  tmux has-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null ||
    { api_err not_logged_in_claude "The login session expired. Start the Claude login again."; return 1; }

  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" -l -- "$code"
  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" Enter
  local i text
  for ((i = 0; i < 45; i++)); do
    sleep 1
    text="$(pane_text "$CLAUDE_LOGIN_SESSION")"
    if grep -qiE 'login successful|logged in|successfully|\[exit 0\]' <<<"$text"; then
      sleep 1
      if claude_logged_in; then
        tmux kill-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null
        api_ok '{"logged_in":true}'; return 0
      fi
    fi
    if grep -qiE 'invalid|error|failed|expired|\[exit [1-9]' <<<"$text"; then break; fi
  done
  if claude_logged_in; then
    tmux kill-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null
    api_ok '{"logged_in":true}'; return 0
  fi
  api_err not_logged_in_claude "Login did not complete." \
    "$(jq -cn --arg t "$(tail -n 15 <<<"$text")" '{pane:$t}')"
}

do_login_claude_cancel() {
  tmux kill-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null
  api_ok '{"cancelled":true}'
}

do_login_github() {
  local token
  token="$(read_secret_line)"
  exec 0</dev/null
  token="${token//[[:space:]]/}"
  [[ "$token" =~ ^[A-Za-z0-9_]{20,255}$ ]] || { api_err invalid_name "That doesn't look like a GitHub token."; return 1; }
  need gh || return 1
  local err
  if ! err="$(printf '%s\n' "$token" | t 30 gh auth login --hostname github.com --with-token 2>&1)"; then
    api_err not_logged_in_github "GitHub rejected the token: $(tail -n 2 <<<"$err")"; return 1
  fi
  unset token
  t 20 gh auth setup-git >/dev/null 2>&1 </dev/null
  local user missing
  user="$(github_user)"
  [[ -n "$user" ]] || { api_err not_logged_in_github "Token saved, but GitHub login still fails."; return 1; }
  missing="$(github_missing_scopes | jq -R . | jq -sc .)"
  rm -f "$CACHE_DIR/repos.json"
  if [[ "$missing" != "[]" ]]; then
    api_err missing_scopes "Logged in as $user, but the token is missing: $(jq -r 'join(", ")' <<<"$missing")." \
      "$(jq -cn --argjson m "$missing" --arg u "$user" '{missing:$m, user:$u}')"; return 1
  fi
  api_ok "$(jq -cn --arg u "$user" '{logged_in:true, user:$u}')"
}

do_login_aws_keys() {
  local key_id secret region
  key_id="$(read_secret_line)"; secret="$(read_secret_line)"; region="$(read_secret_line)"
  exec 0</dev/null
  key_id="${key_id//[[:space:]]/}" secret="${secret//[[:space:]]/}" region="${region//[[:space:]]/}"
  [[ -z "$region" ]] && region="$AWS_DEFAULT_REGION_NAME"
  [[ "$key_id" =~ ^[A-Z0-9]{16,128}$ ]] || { api_err invalid_name "That access key ID doesn't look right."; return 1; }
  [[ "$secret" =~ ^[A-Za-z0-9/+=]{16,128}$ ]] || { api_err invalid_name "That secret key doesn't look right."; return 1; }
  [[ "$region" =~ ^[a-z0-9-]{2,32}$ ]] || { api_err invalid_name "That region doesn't look right."; return 1; }
  need aws || return 1

  # `aws configure import` reads the secret from a file, so it never shows up
  # in the process list the way `aws configure set <secret>` would.
  local profile="${AWS_PROFILE_NAME:-default}" csv err
  csv="$(mktemp "$STATE_DIR/awskeys.XXXXXX")"
  chmod 600 "$csv"
  printf 'User Name,Access key ID,Secret access key\n%s,%s,%s\n' "$profile" "$key_id" "$secret" >"$csv"
  unset secret
  err="$(t 30 aws configure import --csv "file://$csv" --skip-invalid 2>&1 </dev/null)"
  local rc=$?
  rm -f "$csv"
  ((rc == 0)) || { api_err internal "Saving keys failed (AWS CLI v2 is required): $(tail -n 2 <<<"$err")"; return 1; }
  aws configure set region "$region" --profile "$profile" </dev/null >/dev/null 2>&1
  aws configure set output json --profile "$profile" </dev/null >/dev/null 2>&1

  local ident
  ident="$(aws_identity)" || ident=""
  [[ -n "$ident" ]] || { api_err not_logged_in_aws "Keys saved, but AWS rejected them."; return 1; }
  api_ok "$(jq -c --arg r "$region" '{logged_in:true, account:.Account, arn:.Arn, region:$r}' <<<"$ident")"
}

aws_sso_configured() {
  command -v aws >/dev/null 2>&1 || return 1
  [[ -n "$(aws configure get sso_start_url "${AWS_ARGS[@]}" 2>/dev/null </dev/null)" ||
     -n "$(aws configure get sso_session "${AWS_ARGS[@]}" 2>/dev/null </dev/null)" ]]
}

do_login_aws_sso_start() {
  need aws || return 1
  need tmux || return 1
  aws_sso_configured ||
    { api_err not_logged_in_aws "AWS SSO isn't configured on the server for this profile. Use access keys instead." '{"sso_configured":false}'; return 1; }

  local args=(aws sso login --no-browser "${AWS_ARGS[@]}")
  # Newer CLIs default to a localhost callback; ask for the device-code flow.
  if aws sso login help 2>/dev/null </dev/null | grep -q -- '--use-device-code'; then
    args+=(--use-device-code)
  fi
  start_login_session "$AWS_LOGIN_SESSION" "${args[@]}" || { api_err internal "Could not start the SSO login."; return 1; }

  local i text url="" code=""
  for ((i = 0; i < 60; i++)); do
    sleep 0.5
    text="$(pane_text "$AWS_LOGIN_SESSION")"
    url="$(grep -oE 'https://[^[:space:]]+' <<<"$text" | tail -n 1)"
    code="$(grep -oE '\b[A-Z0-9]{4}-[A-Z0-9]{4}\b' <<<"$text" | tail -n 1)"
    [[ -n "$url" && -n "$code" ]] && break
    grep -q '\[exit ' <<<"$text" && break
  done
  if [[ -z "$url" ]]; then
    local tailtext
    tailtext="$(tail -n 15 <<<"$text")"
    tmux kill-session -t "=$AWS_LOGIN_SESSION" 2>/dev/null
    api_err internal "No SSO URL appeared within 30 s." "$(jq -cn --arg t "$tailtext" '{pane:$t}')"; return 1
  fi
  api_ok "$(jq -cn --arg u "$url" --arg c "$code" '{url:$u, code:(if $c=="" then null else $c end)}')"
}

# ---------------------------------------------------------------------------
# --api dispatcher
# ---------------------------------------------------------------------------

api_main() {
  API_MODE=1
  # fd 3 = the one JSON object; everything else goes to stderr.
  exec 3>&1 1>&2
  trap on_exit EXIT
  need jq || { printf '{"ok":false,"error":{"code":"internal","message":"jq is not installed on the server"}}\n' >&3; EMITTED=1; exit 1; }

  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    login-claude-code | login-github | login-aws-keys) ;;  # these read stdin
    *) exec 0</dev/null ;;
  esac

  case "$cmd" in
    status)              [[ $# -eq 0 ]] || bad_args "status takes no arguments"; do_status ;;
    owners)              [[ $# -eq 0 ]] || bad_args "owners takes no arguments"; do_owners ;;
    repos)               [[ $# -eq 0 || ( $# -eq 1 && "$1" == --refresh ) ]] || bad_args "usage: repos [--refresh]"; do_repos "$@" ;;
    sessions)            [[ $# -eq 0 ]] || bad_args "sessions takes no arguments"; do_sessions ;;
    new)                 do_new "$@" ;;
    open)                do_open "$@" ;;
    start)               do_start "$@" ;;
    stop)                do_stop "$@" ;;
    tail)                do_tail "$@" ;;
    login-claude-start)  do_login_claude_start ;;
    login-claude-code)   do_login_claude_code ;;
    login-claude-cancel) do_login_claude_cancel ;;
    login-github)        do_login_github ;;
    login-aws-keys)      do_login_aws_keys ;;
    login-aws-sso-start) do_login_aws_sso_start ;;
    "")                  bad_args "missing subcommand" ;;
    *)                   bad_args "unknown subcommand '$cmd'" ;;
  esac
  # Every handler emits and exits; reaching here is a bug.
  api_err internal "no result"
}

# ---------------------------------------------------------------------------
# Interactive menu
# ---------------------------------------------------------------------------

pause() { read -r -p "Press Enter to continue…" _ || true; }

attach_offer() {
  local sess="$1"
  [[ -t 0 ]] || return 0
  local a
  read -r -p "Attach to session '$sess' now? [y/N] " a || return 0
  [[ "$a" =~ ^[Yy] ]] && tmux attach-session -t "=$sess"
}

menu_logins() {
  say "${C_BOLD}Checking logins…${C_OFF}"
  if claude_logged_in; then ok "Claude: logged in (claude.ai subscription)"
  else
    fail "Claude: not logged in"
    local a; read -r -p "Log in to Claude now? [Y/n] " a
    [[ "$a" =~ ^[Nn] ]] || env -u ANTHROPIC_API_KEY claude auth login
  fi

  local user
  if command -v gh >/dev/null && user="$(github_user)" && [[ -n "$user" ]]; then
    local missing; missing="$(github_missing_scopes | paste -sd, -)"
    if [[ -z "$missing" ]]; then ok "GitHub: $user (all scopes present)"
    else
      warn "GitHub: $user, missing scopes: $missing"
      say "  Create a classic token with repo, read:org, workflow at https://github.com/settings/tokens/new"
      local a; read -r -p "Paste a new token now? [y/N] " a
      if [[ "$a" =~ ^[Yy] ]]; then
        local tok; read -r -s -p "Token: " tok; echo >&2
        printf '%s\n' "$tok" | gh auth login --hostname github.com --with-token && gh auth setup-git
      fi
    fi
  else
    fail "GitHub: not logged in"
    say "  Create a classic token with repo, read:org, workflow at https://github.com/settings/tokens/new"
    local a; read -r -p "Paste a token now? [Y/n] " a
    if ! [[ "$a" =~ ^[Nn] ]]; then
      local tok; read -r -s -p "Token: " tok; echo >&2
      printf '%s\n' "$tok" | gh auth login --hostname github.com --with-token && gh auth setup-git
    fi
  fi

  local ident
  if command -v aws >/dev/null && ident="$(aws_identity)" && [[ -n "$ident" ]]; then
    ok "AWS: $(jq -r .Arn <<<"$ident")"
  else
    fail "AWS: not logged in"
    local a; read -r -p "Log in via (s)SO, access (k)eys, or skip? [s/k/N] " a
    case "$a" in
      [Ss]*) aws sso login "${AWS_ARGS[@]}" ;;
      [Kk]*) aws configure "${AWS_ARGS[@]}" ;;
    esac
  fi
}

pick_owner() {
  require_github || return 1
  local orgs
  mapfile -t orgs < <(t 30 gh api --paginate 'user/memberships/orgs?state=active&per_page=100' --jq '.[].organization.login' 2>/dev/null)
  local choices=("$GH_USER" "${orgs[@]}") i
  say "Owner:"
  for i in "${!choices[@]}"; do say "  $((i + 1))) ${choices[$i]}"; done
  local n; read -r -p "Choose [1]: " n
  n="${n:-1}"
  [[ "$n" =~ ^[0-9]+$ ]] && ((n >= 1 && n <= ${#choices[@]})) || { fail "Invalid choice"; return 1; }
  PICKED_OWNER="${choices[$((n - 1))]}"
}

menu_new() {
  local name
  read -r -p "Project name: " name
  valid_project "$name" || { fail "Names may use letters, digits, '.', '_' and '-'."; return 1; }
  pick_owner || return 1
  local v; read -r -p "Visibility (p)rivate or p(u)blic? [p]: " v
  local vis=private; [[ "$v" =~ ^[Uu] ]] && vis=public
  if do_new "$name" --owner "$PICKED_OWNER" --visibility "$vis" --start; then
    ok "Started session '$(session_name "$name")' running claude --remote-control \"$name\""
    attach_offer "$(session_name "$name")"
  else
    fail "$RESULT_MSG"
  fi
}

menu_existing() {
  require_github || { fail "$RESULT_MSG"; return 1; }
  say "Loading repos…"
  local repos
  repos="$(fetch_repos 1)" || { fail "Could not list repos."; return 1; }
  local list
  mapfile -t list < <(jq -r 'sort_by(.pushed_at) | reverse | .[] | "\(.full_name)\t\(if .private then "private" else "public" end)"' <<<"$repos")
  local i
  for i in "${!list[@]}"; do
    local fn="${list[$i]%%$'\t'*}" vis="${list[$i]#*$'\t'}" mark=""
    [[ -d "$(project_dir "${fn#*/}")" ]] && mark=" [on server]"
    printf '%4d) %s (%s)%s\n' "$((i + 1))" "$fn" "$vis" "$mark" >&2
  done
  local n; read -r -p "Choose a repo (number or owner/name): " n
  local repo
  if [[ "$n" =~ ^[0-9]+$ ]] && ((n >= 1 && n <= ${#list[@]})); then repo="${list[$((n - 1))]%%$'\t'*}"
  else repo="$n"; fi
  if do_open "$repo" --start; then
    ok "Ready: $(project_dir "${repo#*/}")"
    attach_offer "$(session_name "${repo#*/}")"
  elif [[ "$RESULT_CODE" == folder_dirty ]]; then
    warn "$RESULT_MSG"
    local a; read -r -p "[y/N] " a
    if [[ "$a" =~ ^[Yy] ]] && start_session "${repo#*/}"; then attach_offer "$STARTED_SESSION"; fi
  else
    fail "$RESULT_MSG"
  fi
}

menu_sessions() {
  local s
  s="$(sessions_json)"
  if [[ -z "$s" || "$s" == "[]" ]]; then say "No Claude sessions running."; return 0; fi
  local names
  mapfile -t names < <(jq -r '.[].name' <<<"$s")
  jq -r --argjson now "$(date +%s)" 'to_entries[] | "\(.key + 1)) \(.value.name)  up \((($now - .value.started_at) / 60 | floor))m\(if .value.attached then "  (attached)" else "" end)"' <<<"$s" >&2
  local n; read -r -p "Number to (a)ttach or (s)top, e.g. 'a1' / 's2' (Enter to go back): " n
  [[ "$n" =~ ^([as])([0-9]+)$ ]] || return 0
  local idx=$((BASH_REMATCH[2] - 1))
  ((idx >= 0 && idx < ${#names[@]})) || return 0
  if [[ "${BASH_REMATCH[1]}" == a ]]; then tmux attach-session -t "=${names[$idx]}"
  else
    tmux kill-session -t "=${names[$idx]}" && ok "Stopped ${names[$idx]}"
    local proj
    proj="$(jq -r ".[$idx].project" <<<"$s")"
    registry_remove "$proj"
  fi
}

menu_main() {
  need jq || { echo "Please install jq." >&2; exit 1; }
  while true; do
    say ""
    say "${C_BOLD}Claude setup${C_OFF} — projects in $PROJECTS_DIR"
    say "  1) Check / fix logins"
    say "  2) New project"
    say "  3) Existing project"
    say "  4) Running sessions"
    say "  q) Quit"
    local c
    read -r -p "Choose: " c || exit 0
    case "$c" in
      1) menu_logins; pause ;;
      2) menu_new; pause ;;
      3) menu_existing; pause ;;
      4) menu_sessions ;;
      q | Q | "") exit 0 ;;
      *) warn "Unknown choice" ;;
    esac
  done
}

# ---------------------------------------------------------------------------

main() {
  load_config
  case "${1:-}" in
    --api) shift; api_main "$@" ;;
    --clone-worker) shift; API_MODE=1; clone_worker "$@" ;;
    --version) echo "$SCRIPT_VERSION" ;;
    -h | --help) sed -n '2,9p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//' ;;
    "") menu_main ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
}

main "$@"
