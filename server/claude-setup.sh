#!/usr/bin/env bash
# claude-setup.sh — make sure you're logged in to Claude, GitHub and AWS, then
# start a new project or resume an existing one. Every project runs Claude Code
# in a detached tmux session with Remote Control on, named after the project.
#
# Usage: ./claude-setup.sh                     interactive menu
#        ./claude-setup.sh --api <cmd> [args]  non-interactive, prints one JSON object
#
# Per-machine settings live in ~/.config/claude-launcher/config (created on
# first run; see config.example). Nothing personal is written in this file.

set -uo pipefail

SCRIPT_VERSION="2.0.0"
# What the phone can rely on: 2 = extra services, 3 = self-update,
# 4 = install-cli, 5 = run, 6 = restart + claude-cmd, 7 = token services +
# more CLIs, 8 = keys, 9 = safe restart (resume + busy check) and BASH_ENV.
# Bump when the app starts needing a new server feature.
SCRIPT_API=9
CLAUDERC_REPO="${CLAUDERC_REPO:-myGIGlife-claude/Claude-RC}"
CLAUDERC_RAW="${CLAUDERC_RAW:-https://raw.githubusercontent.com}"
SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"

STATE_DIR="$HOME/.config/claude-setup"
RC_MARKER="$STATE_DIR/remote-control-confirmed"
LAUNCHER_CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/claude-launcher"
CONFIG_FILE="$LAUNCHER_CONFIG_DIR/config"
API_STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/claude-launcher"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/claude-launcher"
# Tokens for services whose CLIs only read an environment variable.
SERVICES_ENV="$LAUNCHER_CONFIG_DIR/env"
SERVICES_ENV_HOOK="[ -f \"$SERVICES_ENV\" ] && . \"$SERVICES_ENV\"  # cLaudeRC"
LOCK_FILE="$API_STATE_DIR/api.lock"
INSTALLED_COMMIT_FILE="$LAUNCHER_CONFIG_DIR/installed-commit"   # written by install.sh
SERVICES_INFO="$LAUNCHER_CONFIG_DIR/services.json"   # who each token service is logged in as
TOKEN_SERVICES="cloudflare vercel netlify fly railway supabase neon npm stripe huggingface b2 gcp firebase"

CLAUDE_LOGIN_SESSION="claude-login"
AWS_LOGIN_SESSION="aws-sso-login"

# Names never start with '-', so they can't be read as options by git, gh,
# tmux or claude.
PROJECT_RE='^[A-Za-z0-9._][A-Za-z0-9._-]{0,99}$'
REPO_RE='^[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9._][A-Za-z0-9._-]*$'
OWNER_RE='^[A-Za-z0-9][A-Za-z0-9-]{0,38}$'
AUTOSTART_LIST="$HOME/.config/claude-setup/sessions.tsv"
REQUIRED_SCOPES=(repo read:org workflow)

EMITTED=0

green()  { printf "\033[32m%s\033[0m\n" "$*"; }
yellow() { printf "\033[33m%s\033[0m\n" "$*"; }
red()    { printf "\033[31m%s\033[0m\n" "$*"; }
step()   { printf "\n\033[1;36m==> %s\033[0m\n" "$*"; }

# ======================================================================
# Config
# ======================================================================
load_config() {
  PROJECTS_DIR="$HOME/projects"
  DEFAULT_OWNER=""
  AWS_PROFILE_NAME=""
  AWS_DEFAULT_REGION_NAME="us-east-1"
  EXTRA_PATH=""
  REPOS_CACHE_TTL=600
  ALLOW_RUN=0

  if [[ ! -f "$CONFIG_FILE" ]]; then
    mkdir -p "$LAUNCHER_CONFIG_DIR"
    cat >"$CONFIG_FILE" <<'EOF'
# claude-setup.sh / cLaudeRC config — see config.example in the repo.
PROJECTS_DIR="$HOME/projects"
EOF
    chmod 600 "$CONFIG_FILE"
  fi
  # shellcheck source=/dev/null
  . "$CONFIG_FILE"

  # ~/.local/bin first, as the menu always had it (that's where claude lives);
  # the system dirs are appended for forced-command SSH sessions, which get a
  # bare PATH. EXTRA_PATH wins over everything.
  PATH="$HOME/.local/bin:$PATH:/usr/local/bin:/usr/bin:/bin"
  [[ -n "$EXTRA_PATH" ]] && PATH="$EXTRA_PATH:$PATH"
  export PATH

  PROJECTS_DIR="${PROJECTS_DIR%/}"
  [[ "$PROJECTS_DIR" == /?* ]] || PROJECTS_DIR="$HOME/projects"   # must be absolute
  mkdir -p "$STATE_DIR" "$API_STATE_DIR" "$CACHE_DIR"
  AWS_ARGS=()
  [[ -n "$AWS_PROFILE_NAME" ]] && AWS_ARGS=(--profile "$AWS_PROFILE_NAME")
}

# ======================================================================
# Shared helpers (menu and --api use the same logic)
# ======================================================================

# The first tmux call forks the tmux server, which would inherit and hold open
# the SSH channel (fd 3) and the api lock (fd 9). Never let it.
tmux() { command tmux "$@" 3>&- 9>&-; }

t() { local s="$1"; shift; timeout --kill-after=5 "$s" "$@"; }

session_name() { local n="$1"; echo "${n//[.:]/-}"; }   # tmux can't use . or :

claude_logged_in() { [[ -s "$HOME/.claude/.credentials.json" ]]; }

# Prints missing scopes, space separated. admin:org / write:org cover read:org.
missing_scopes() {
  local have
  have="$(t 20 gh api -i user 2>/dev/null </dev/null | tr -d '\r' | grep -i '^x-oauth-scopes:' | cut -d: -f2- | tr -d ' ')"
  for sc in "${REQUIRED_SCOPES[@]}"; do
    if [[ "$sc" == "read:org" ]]; then
      [[ ",$have," == *",read:org,"* || ",$have," == *",write:org,"* || ",$have," == *",admin:org,"* ]] || printf "%s " "$sc"
    else
      [[ ",$have," == *",$sc,"* ]] || printf "%s " "$sc"
    fi
  done
}

github_logged_in() { t 20 gh auth status --hostname github.com >/dev/null 2>&1 </dev/null; }

load_github() {
  GH_USER="$(t 20 gh api user --jq .login </dev/null)"
  mapfile -t GH_ORGS < <(t 30 gh api user/orgs --paginate --jq '.[].login' 2>/dev/null </dev/null || true)
}

git_identity() {
  if [[ -z "$(git config --global user.name || true)" ]]; then
    git config --global user.name "$(t 20 gh api user --jq '.name // .login' </dev/null)"
  fi
  if [[ -z "$(git config --global user.email || true)" ]]; then
    git config --global user.email "$(t 20 gh api user --jq '.id' </dev/null)+${GH_USER}@users.noreply.github.com"
  fi
  git config --global init.defaultBranch main
}

aws_sso_configured() { grep -q "sso_" "$HOME/.aws/config" 2>/dev/null; }

# Pre-accept Claude's "trust this folder?" prompt so detached sessions don't hang on it
trust_folder() {
  local dir="$1" cfg="$HOME/.claude.json" tmp
  [[ -s "$cfg" ]] || echo '{}' >"$cfg"
  tmp="$(mktemp "$cfg.XXXXXX")"   # same filesystem, so the mv is atomic
  if jq --arg d "$dir" '.projects[$d].hasTrustDialogAccepted = true' "$cfg" >"$tmp"; then
    mv "$tmp" "$cfg"
  else
    rm -f "$tmp"
  fi
}

# Tell claude-autostart about the change now instead of at its next 2-minute save.
autostart_save() {
  local bin
  bin="$(command -v claude-autostart 2>/dev/null || true)"
  [[ -n "$bin" ]] && "$bin" save >/dev/null 2>&1 </dev/null || true
}

# Drop a session from claude-autostart's list. Needed when it can't see the
# change itself: after the last session stops, tmux exits and its save keeps
# the old list; and login sessions (which run claude) must never be restored.
autostart_forget() {
  local sess="$1" tmp
  [[ -f "$AUTOSTART_LIST" ]] || return 0
  tmp="$(mktemp "$AUTOSTART_LIST.XXXXXX")" || return 0
  awk -F'\t' -v s="$sess" '$1 != s' "$AUTOSTART_LIST" >"$tmp" && mv "$tmp" "$AUTOSTART_LIST" || rm -f "$tmp"
}

# Starts the session exactly as the menu always has, so claude-autostart picks
# it up. Returns 0 if started, 2 if already running.
launch_session() {
  local name="$1" dir="$2" sess
  sess="$(session_name "$name")"
  if tmux has-session -t "=$sess" 2>/dev/null; then
    return 2
  fi
  trust_folder "$dir"
  tmux new-session -d -s "$sess" -c "$dir" \
    "env -u ANTHROPIC_API_KEY claude --remote-control $(printf %q "$name"); exec bash" || return 1
  autostart_save
  return 0
}

# ======================================================================
# Interactive menu (unchanged behaviour)
# ======================================================================
start_session() {
  local name="$1" dir="$2" sess rc=0
  sess="$(session_name "$name")"
  launch_session "$name" "$dir" || rc=$?
  if ((rc == 2)); then
    yellow "  $name: already running"
    return
  elif ((rc != 0)); then
    red "  $name: tmux couldn't start the session"
    return
  fi
  green "  $name: started (detached, Remote Control on)"

  # Very first time on this machine, Remote Control asks for a one-time "y"
  if [[ ! -f "$RC_MARKER" ]]; then
    echo
    yellow "One-time step: Claude will ask 'Enable Remote Control? (y/n)'."
    yellow "Answer y, then detach with Ctrl-b then d."
    if [[ -n "${TMUX:-}" ]]; then
      yellow "You're inside tmux already, so run this after the script: tmux attach -t $sess"
    else
      read -rp "Press Enter to open it…" _
      tmux attach -t "=$sess" || true
    fi
    touch "$RC_MARKER"
  fi
}

quit_script() {
  trap - INT
  step "Exiting"
  echo "Running sessions on the Pi:"
  tmux ls 2>/dev/null | sed 's/^/  /' || echo "  (none)"
  echo
  echo "Open the Claude app → Code. Each project shows up by name with a green dot."
  echo "Peek at one on the Pi:  tmux attach -t <project>   (detach: Ctrl-b then d)"
  exit 0
}

# ask VAR "prompt"  → returns 1 on b (back), exits on q
ask() {
  local __var="$1" __prompt="$2" __in=""
  read -rp "$__prompt" __in || __in="q"
  __in="${__in#"${__in%%[![:space:]]*}"}"
  __in="${__in%"${__in##*[![:space:]]}"}"
  case "$__in" in
    q | Q) quit_script ;;
    b | B) return 1 ;;
  esac
  printf -v "$__var" '%s' "$__in"
}

hint() { printf "\033[2m  (b = back, q = exit)\033[0m\n"; }

token_login() {
  echo
  yellow "Paste your GitHub personal access token (classic)."
  yellow "It needs these scopes: ${REQUIRED_SCOPES[*]}"
  local token=""
  while [[ -z "$token" ]]; do
    read -rsp "Token (hidden): " token
    echo
  done
  if printf "%s\n" "$token" | gh auth login --hostname github.com --git-protocol https --with-token; then
    return 0
  fi
  red "GitHub didn't accept that token."
  return 1
}

# ---------------- New project ----------------
new_project() {
  local stage="owner" owner="$GH_USER" name="" vis="private" raw c i
  while true; do
    case "$stage" in
      owner)
        if ((${#GH_ORGS[@]} == 0)); then owner="$GH_USER"; stage="name"; continue; fi
        step "New project — where should the repo live?"
        echo "  1) $GH_USER (personal)"
        for ((i = 0; i < ${#GH_ORGS[@]}; i++)); do echo "  $((i + 2))) ${GH_ORGS[$i]}"; done
        hint
        ask c "> [1] " || return 1
        if [[ -z "$c" || "$c" == "1" ]]; then
          owner="$GH_USER"
        elif [[ "$c" =~ ^[0-9]+$ ]] && ((c >= 2 && c <= ${#GH_ORGS[@]} + 1)); then
          owner="${GH_ORGS[$((c - 2))]}"
        else
          red "Pick a number from the list."; continue
        fi
        stage="name"
        ;;
      name)
        step "New project — name (under $owner)"
        hint
        if ! ask raw "Project name: "; then
          if ((${#GH_ORGS[@]})); then stage="owner"; else return 1; fi
          continue
        fi
        name="$(tr -s ' ' '-' <<<"$raw")"
        if [[ ! "$name" =~ ^[A-Za-z0-9._-]+$ ]]; then
          red "Use letters, numbers, dashes, dots or underscores."
        elif [[ -e "$PROJECTS_DIR/$name" ]]; then
          red "$PROJECTS_DIR/$name already exists — pick another name, or go back and choose Existing."
        elif gh repo view "$owner/$name" >/dev/null 2>&1; then
          red "$owner/$name already exists on GitHub — pick another name, or go back and choose Existing."
        else
          stage="vis"
        fi
        ;;
      vis)
        step "New project — visibility"
        echo "  1) Private"
        echo "  2) Public"
        hint
        ask c "> [1] " || { stage="name"; continue; }
        case "$c" in
          "" | 1) vis="private" ;;
          2) vis="public" ;;
          *) red "Pick 1 or 2."; continue ;;
        esac
        stage="confirm"
        ;;
      confirm)
        echo
        echo "About to create a $vis repo: $owner/$name"
        echo "Folder: $PROJECTS_DIR/$name"
        hint
        ask c "Create it? (Y/n) " || { stage="vis"; continue; }
        if [[ "$c" =~ ^[Nn] ]]; then stage="vis"; continue; fi

        step "Creating $vis repo $owner/$name"
        if ! (cd "$PROJECTS_DIR" && gh repo create "$owner/$name" "--$vis" --add-readme --clone); then
          red "GitHub couldn't create the repo. Going back."
          stage="name"; continue
        fi
        [[ -d "$PROJECTS_DIR/$name/.git" ]] || gh repo clone "$owner/$name" "$PROJECTS_DIR/$name" || true
        if [[ ! -d "$PROJECTS_DIR/$name/.git" ]]; then
          red "Repo was created but couldn't be cloned. Try it from Existing."
          return 0
        fi
        rm -f "$CACHE_DIR/repos.json"
        green "Repo ready at $PROJECTS_DIR/$name"
        step "Starting Claude"
        start_session "$name" "$PROJECTS_DIR/$name"
        return 0
        ;;
    esac
  done
}

# ---------------- Existing project ----------------
existing_project() {
  local repos count i name full shown visib pushed flag picks n dir started
  step "Loading your repos"
  repos="$(list_repos_raw || echo '[]')"
  count="$(jq length <<<"$repos")"
  if ((count == 0)); then
    yellow "No GitHub repos found. Going back."
    return 1
  fi

  while true; do
    step "Existing project"
    printf "%-4s %-42s %-9s %-11s %s\n" "#" "PROJECT" "TYPE" "LAST PUSH" ""
    for ((i = 0; i < count; i++)); do
      name="$(jq -r ".[$i].name" <<<"$repos")"
      full="$(jq -r ".[$i].nameWithOwner" <<<"$repos")"
      shown="$name"; [[ "${full%%/*}" != "$GH_USER" ]] && shown="$full"
      visib="$(jq -r ".[$i].visibility" <<<"$repos" | tr 'A-Z' 'a-z')"
      pushed="$(jq -r ".[$i].pushedAt" <<<"$repos")"
      flag=""
      [[ -d "$PROJECTS_DIR/$name/.git" ]] && flag="[on Pi]"
      tmux has-session -t "=$(session_name "$name")" 2>/dev/null && flag="$flag [running]"
      printf "%-4s %-42s %-9s %-11s %s\n" "$((i + 1))" "$shown" "$visib" "${pushed:0:10}" "$flag"
    done
    echo
    echo "Don't see it? Press b to go back (it may need to be created as New)."
    hint
    ask picks "Project number(s), e.g. 2 or 1 3: " || return 1
    [[ -z "$picks" ]] && continue

    started=0
    for n in $picks; do
      if ! [[ "$n" =~ ^[0-9]+$ ]] || ((n < 1 || n > count)); then
        yellow "Skipping invalid choice: $n"
        continue
      fi
      full="$(jq -r ".[$((n - 1))].nameWithOwner" <<<"$repos")"
      name="${full#*/}"
      dir="$PROJECTS_DIR/$name"

      step "$name"
      if [[ -d "$dir/.git" ]]; then
        if [[ -n "$(git -C "$dir" status --porcelain)" ]]; then
          yellow "  Uncommitted changes — not pulling"
        elif git -C "$dir" pull --ff-only --quiet 2>/dev/null; then
          green "  Updated from GitHub"
        else
          yellow "  Couldn't fast-forward — check it manually"
        fi
      elif [[ -e "$dir" ]]; then
        red "  $dir exists but isn't a git repo — skipping"
        continue
      else
        if ! gh repo clone "$full" "$dir" -- --quiet; then
          red "  Clone failed — skipping"
          continue
        fi
        green "  Cloned to $dir"
      fi
      start_session "$name" "$dir"
      started=1
    done
    ((started)) && return 0
    yellow "Nothing started — pick again, or b to go back."
  done
}

menu_main() {
  set -e

  # ====================================================================
  # 1. Tools
  # ====================================================================
  step "Checking tools"
  missing=()
  for p in git curl jq tmux unzip; do
    command -v "$p" >/dev/null 2>&1 || missing+=("$p")
  done
  if ((${#missing[@]})); then
    yellow "Installing: ${missing[*]}"
    sudo apt-get update -y
    sudo apt-get install -y "${missing[@]}"
  fi

  if ! command -v gh >/dev/null 2>&1; then
    yellow "Installing GitHub CLI"
    sudo mkdir -p -m 755 /etc/apt/keyrings
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg |
      sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg >/dev/null
    sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" |
      sudo tee /etc/apt/sources.list.d/github-cli.list >/dev/null
    sudo apt-get update -y
    sudo apt-get install -y gh
  fi

  if ! command -v claude >/dev/null 2>&1; then
    yellow "Installing Claude Code"
    curl -fsSL https://claude.ai/install.sh | bash
    # shellcheck disable=SC2016  # written literally into .bashrc
    grep -q '.local/bin' "$HOME/.bashrc" 2>/dev/null ||
      echo 'export PATH="$HOME/.local/bin:$PATH"' >>"$HOME/.bashrc"
  fi

  if ! command -v aws >/dev/null 2>&1; then
    yellow "Installing AWS CLI"
    case "$(uname -m)" in
      aarch64 | arm64) aws_arch="aarch64" ;;
      x86_64) aws_arch="x86_64" ;;
      *) red "Unsupported CPU for AWS CLI: $(uname -m)"; exit 1 ;;
    esac
    tmp="$(mktemp -d)"
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${aws_arch}.zip" -o "$tmp/aws.zip"
    unzip -q "$tmp/aws.zip" -d "$tmp"
    sudo "$tmp/aws/install"
    rm -rf "$tmp"
  fi
  green "Tools OK"

  # ====================================================================
  # 2. Logins
  # ====================================================================
  step "Claude login"
  # Remote Control needs a claude.ai subscription login, not an API key
  if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
    yellow "ANTHROPIC_API_KEY is set — ignoring it (Remote Control needs your claude.ai login)."
    unset ANTHROPIC_API_KEY
  fi
  if ! claude_logged_in; then
    yellow "Choose the claude.ai (subscription) option, open the link on your phone,"
    yellow "and paste the code back here."
    claude auth login || true
  fi
  if claude_logged_in; then
    green "Claude: logged in"
  else
    red "Claude login failed. Run 'claude auth login' and try again."
    exit 1
  fi

  step "GitHub login"
  # Logs in with a personal access token (classic) — no browser code needed.
  # Create one at github.com → Settings → Developer settings → Personal access tokens
  # → Tokens (classic), with scopes: repo, read:org, workflow
  if [[ -n "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]]; then
    yellow "GH_TOKEN/GITHUB_TOKEN is set in your environment — ignoring it for this script."
    unset GH_TOKEN GITHUB_TOKEN
  fi

  while true; do
    if ! github_logged_in; then
      token_login || continue
    fi
    need="$(missing_scopes)"
    if [[ -z "$need" ]]; then
      break
    fi
    red "Your GitHub token is missing scopes: $need"
    yellow "Edit the token on GitHub to add them (or make a new one), then paste it here."
    token_login || true
  done
  gh auth setup-git >/dev/null 2>&1 || true
  load_github
  git_identity
  green "GitHub: logged in as $GH_USER"
  if ((${#GH_ORGS[@]})); then
    green "GitHub orgs: ${GH_ORGS[*]}"
    yellow "(If an org's private repos are missing, click 'Configure SSO' next to your token"
    yellow " on GitHub and authorize that org, or have an org owner allow personal access tokens.)"
  fi

  step "AWS login"
  if ! aws sts get-caller-identity >/dev/null 2>&1; then
    if aws_sso_configured; then
      yellow "Your AWS SSO session has expired — refreshing (enter the code on your phone)."
      aws sso login --use-device-code || true
    else
      echo "How do you sign in to AWS?"
      echo "  1) SSO / IAM Identity Center"
      echo "  2) Access keys"
      echo "  3) Skip for now"
      read -rp "> " aws_choice
      case "$aws_choice" in
        1) aws configure sso --use-device-code || true ;;
        2) aws configure || true ;;
        *) yellow "Skipping AWS" ;;
      esac
    fi
  fi
  if aws_id="$(aws sts get-caller-identity --query Account --output text 2>/dev/null)"; then
    green "AWS: logged in (account $aws_id)"
  else
    yellow "AWS: not logged in — projects will still start, but AWS commands won't work."
  fi

  # ====================================================================
  # 3. Projects folder
  # ====================================================================
  step "Projects folder"
  if [[ -d "$PROJECTS_DIR" ]]; then
    green "$PROJECTS_DIR exists"
  else
    mkdir -p "$PROJECTS_DIR"
    green "Created $PROJECTS_DIR"
  fi

  # ====================================================================
  # 4. Menus — every prompt accepts  b = back   q = exit
  # ====================================================================
  trap 'echo; yellow "Cancelled."; quit_script' INT

  mode=""
  while true; do
    step "What are you working on?"
    echo "  1) New project"
    echo "  2) Existing project"
    printf "\033[2m  (q = exit)\033[0m\n"
    ask mode "> " || continue
    case "$mode" in
      1) new_project && green "All set. Start another, or q to exit." || true ;;
      2) existing_project && green "All set. Start another, or q to exit." || true ;;
      *) red "Pick 1, 2 or q." ;;
    esac
  done
}

# Your repos plus every org's, newest push first (gh repo list JSON).
# Fails if your own list can't be fetched; an org that fails is skipped.
list_repos_raw() {
  local owner out lists=()
  for owner in "$GH_USER" "${GH_ORGS[@]}"; do
    if out="$(t 30 gh repo list "$owner" --limit 200 --no-archived \
      --json nameWithOwner,name,pushedAt,visibility 2>/dev/null </dev/null)"; then
      lists+=("$out")
    elif [[ "$owner" == "$GH_USER" ]]; then
      return 1
    fi
  done
  printf '%s\n' "${lists[@]}" | jq -s 'add | unique_by(.nameWithOwner) | sort_by(.pushedAt) | reverse'
}

# ======================================================================
# --api mode: one JSON object on stdout, nothing ever waits for input
# ======================================================================

api_ok() {
  # A failed jq builder leaves nothing; never report that as success.
  if [[ -z "${1:-}" ]] || ! jq -e . >/dev/null 2>&1 <<<"$1"; then
    api_err internal "The server couldn't build its answer."
  fi
  jq -cn --argjson d "$1" '{ok:true,data:$d}' >&3
  EMITTED=1
  exit 0
}

# api_err <code> <message> [extra-json-object] [exit-code]
api_err() {
  jq -cn --arg c "$1" --arg m "$2" --argjson x "${3:-"{}"}" \
    '{ok:false,error:({code:$c,message:$m} + $x)}' >&3
  EMITTED=1
  exit "${4:-1}"
}

bad_args() { api_err bad_args "$1" '{}' 2; }

on_exit() {
  local rc=$?
  if ((EMITTED == 0)); then
    jq -cn --arg m "unexpected exit ($rc)" '{ok:false,error:{code:"internal",message:$m}}' >&3
  fi
}

need() { command -v "$1" >/dev/null 2>&1 || api_err internal "'$1' is not installed on the server"; }

valid_project() { [[ "$1" =~ $PROJECT_RE && "$1" != "." && "$1" != ".." ]]; }

take_lock() {
  exec 9>>"$LOCK_FILE"
  flock -n 9 || api_err busy "Another create/open is still running. Try again in a moment."
}

require_github() {
  need gh
  github_logged_in || api_err not_logged_in_github "GitHub is not logged in on the server."
  load_github
  [[ -n "$GH_USER" ]] || api_err not_logged_in_github "GitHub is not logged in on the server."
}

# The very first Remote Control start asks "Enable Remote Control? (y/n)".
# The menu has you answer it by hand; from the phone, answer y here.
api_confirm_remote_control() {
  local sess="$1" i text
  [[ -f "$RC_MARKER" ]] && return 0
  for ((i = 0; i < 30; i++)); do
    sleep 0.5
    text="$(tmux capture-pane -p -J -t "=$sess:" 2>/dev/null)"
    if grep -qiE 'enable remote control.*\(y/n\)|remote control\?.*\(y/n\)' <<<"$text"; then
      tmux send-keys -t "=$sess:" y Enter
      touch "$RC_MARKER"
      return 0
    fi
  done
}

# api_start_session <project> — sets STARTED_SESSION / ALREADY_RUNNING
api_start_session() {
  local name="$1" dir rc=0
  dir="$PROJECTS_DIR/$name"
  need tmux
  command -v claude >/dev/null 2>&1 || api_err internal "'claude' is not installed on the server."
  [[ -d "$dir" ]] || api_err invalid_name "No project folder named '$name' on the server."
  STARTED_SESSION="$(session_name "$name")"
  ALREADY_RUNNING=false
  launch_session "$name" "$dir" || rc=$?
  if ((rc == 2)); then
    ALREADY_RUNNING=true
  elif ((rc != 0)); then
    api_err internal "tmux could not start the session."
  else
    api_confirm_remote_control "$STARTED_SESSION"
  fi
}

do_status() {
  ensure_env_hook   # also sets up servers connected before BASH_ENV was used
  local claude_ok=false gh_ok=false gh_user="" scopes="[]" aws_ok=false aws_json="null" ident
  claude_logged_in && claude_ok=true
  if command -v gh >/dev/null 2>&1 && github_logged_in; then
    gh_user="$(gh api user --jq .login 2>/dev/null </dev/null)"
    if [[ -n "$gh_user" ]]; then
      gh_ok=true
      scopes="$(missing_scopes | tr ' ' '\n' | grep -v '^$' | jq -R . | jq -sc .)"
    fi
  fi
  if command -v aws >/dev/null 2>&1 &&
    ident="$(t 20 aws sts get-caller-identity "${AWS_ARGS[@]}" --output json 2>/dev/null </dev/null)" && [[ -n "$ident" ]]; then
    aws_ok=true
    aws_json="$(jq -c '{account:.Account, arn:.Arn}' <<<"$ident")"
  fi
  local commit
  commit="$(grep -oE '^[0-9a-f]{40}$' "$INSTALLED_COMMIT_FILE" 2>/dev/null | head -n 1)"
  api_ok "$(jq -cn --argjson services "$(services_json)" --arg commit "$commit" --argjson api "$SCRIPT_API" \
    --argjson run "$([[ "$ALLOW_RUN" == 1 ]] && echo true || echo false)" \
    --argjson c "$claude_ok" --argjson g "$gh_ok" --arg gu "$gh_user" --argjson gm "$scopes" \
    --argjson a "$aws_ok" --argjson ai "$aws_json" --arg ap "${AWS_PROFILE_NAME:-default}" \
    --argjson sso "$(aws_sso_configured && echo true || echo false)" \
    --arg host "$(hostname -s 2>/dev/null || hostname)" --arg v "$SCRIPT_VERSION" '{
      claude:{logged_in:$c},
      github:{logged_in:$g, user:(if $gu=="" then null else $gu end), missing_scopes:$gm},
      aws:{logged_in:$a, identity:$ai, profile:$ap, sso_configured:$sso},
      hostname:$host, version:$v, services:$services,
      commit:(if $commit=="" then null else $commit end), script_api:$api, run_enabled:$run}')"
}

# Extra services the phone can log in to. Each: installed, logged_in, detail.
services_json() {
  local gl_i=false gl_ok=false gl_user="" dk_i=false dk_regs=""
  if command -v glab >/dev/null 2>&1; then
    gl_i=true
    gl_user="$(t 15 glab api user 2>/dev/null </dev/null | jq -r '.username // empty' 2>/dev/null)"
    [[ -n "$gl_user" ]] && gl_ok=true
  fi
  if command -v docker >/dev/null 2>&1; then
    dk_i=true
    dk_regs="$(jq -r '(.auths // {}) | keys | join(", ")' "$HOME/.docker/config.json" 2>/dev/null)"
  fi
  jq -cn --argjson gi "$gl_i" --argjson go "$gl_ok" --arg gu "$gl_user" \
    --argjson di "$dk_i" --arg dr "$dk_regs" '{
      gitlab:{installed:$gi, logged_in:$go, detail:(if $gu=="" then null else $gu end)},
      docker:{installed:$di, logged_in:($dr!=""), detail:(if $dr=="" then null else $dr end)}}' |
    jq -c --argjson t "$(token_services_json)" '. + $t'
}

# installed / logged_in / detail for every token service (no network calls).
token_services_json() {
  local id v ok inst
  for id in $TOKEN_SERVICES; do
    svc_def "$id"
    ok=true
    grep -q "^export ${SVC_VARS[0]}=" "$SERVICES_ENV" 2>/dev/null || ok=false   # the first var is the required one
    inst=true
    [[ -z "$SVC_CLI" ]] || command -v "$SVC_CLI" >/dev/null 2>&1 || inst=false
    jq -cn --arg id "$id" --argjson i "$inst" --argjson o "$ok" \
      --arg d "$(jq -r --arg id "$id" '.[$id] // empty' "$SERVICES_INFO" 2>/dev/null)" \
      '{($id):{installed:$i, logged_in:$o, detail:(if $d=="" then null else $d end)}}'
  done | jq -sc 'add // {}'
}

do_owners() {
  require_github
  api_ok "$(printf '%s\n' "${GH_ORGS[@]}" | grep -v '^$' | jq -R '{login:.}' | jq -sc \
    --arg u "$GH_USER" --arg d "$DEFAULT_OWNER" '{user:$u, orgs:., default_owner:(if $d=="" then $u else $d end)}')"
}

# "owner/name" of a folder's origin remote, lower-cased; empty if none.
origin_slug() {
  git -C "$1" remote get-url origin 2>/dev/null </dev/null |
    sed -E 's#^(https?://[^/]+/|[^@]+@[^:]+:|ssh://[^/]+/)##; s#\.git$##' | tr 'A-Z' 'a-z'
}

# Clone status files of clones still running in the background.
clone_status_file() { printf '%s/clone-%s.json' "$API_STATE_DIR" "${1//\//__}"; }
clone_running() {
  local f="$1" pid
  [[ "$(jq -r '.state // ""' "$f" 2>/dev/null)" == running ]] || return 1
  pid="$(jq -r '.pid // ""' "$f" 2>/dev/null)"
  # No pid yet = the worker is just starting.
  [[ -z "$pid" ]] || kill -0 "$pid" 2>/dev/null
}

do_repos() {
  local refresh=0 cache="$CACHE_DIR/repos.json" raw=""
  [[ "${1:-}" == "--refresh" ]] && refresh=1
  require_github
  if ((refresh == 0)) && [[ -s "$cache" ]] && (($(date +%s) - $(stat -c %Y "$cache") < REPOS_CACHE_TTL)); then
    raw="$(cat "$cache")"
    jq -e 'type == "array"' >/dev/null 2>&1 <<<"$raw" || { rm -f "$cache"; raw=""; }
  fi
  if [[ -z "$raw" ]]; then
    raw="$(list_repos_raw)" || api_err internal "Could not list your GitHub repos."
    printf '%s' "$raw" >"$cache.$$" && mv "$cache.$$" "$cache"
  fi

  # A folder counts as this repo only if its origin is this repo, so
  # user/foo and org/foo don't both show up as "On server".
  local folders running cloning d f
  folders="$(
    for d in "$PROJECTS_DIR"/*/; do
      [[ -d "$d.git" ]] || continue
      d="${d%/}"
      printf '%s\t%s\n' "${d##*/}" "$(origin_slug "$d")"
    done | jq -Rsc 'split("\n") | map(select(. != "") | split("\t") | {key:.[0], value:(.[1] // "")}) | from_entries'
  )"
  running="$({ tmux list-sessions -F '#{session_name}' 2>/dev/null || true; } | jq -R . | jq -sc .)"
  cloning="$(
    for f in "$API_STATE_DIR"/clone-*.json; do
      [[ -f "$f" ]] && clone_running "$f" && jq -r '.repo // empty' "$f"
    done | jq -R . | jq -sc .
  )"
  api_ok "$(jq -c --argjson fo "${folders:-{\}}" --argjson r "$running" --argjson cl "${cloning:-[]}" --arg gu "$GH_USER" '
    {user:$gu, repos: map(
      .nameWithOwner as $full
      | ($full | ascii_downcase) as $slug
      | ($cl | index($full) != null) as $cloning
      | ($fo[.name]) as $origin
      | (($origin != null) and ($origin == $slug or $origin == "") and ($cloning | not)) as $local
      | {
          full_name:.nameWithOwner, name, owner:(.nameWithOwner | split("/")[0]),
          owner_type:(if (.nameWithOwner | split("/")[0]) == $gu then "User" else "Organization" end),
          private:((.visibility // "" | ascii_downcase) != "public"), pushed_at:.pushedAt,
          local:$local, cloning:$cloning,
          running:($local and ((.name | gsub("[.:]"; "-")) as $s | $r | index($s) != null))
        })}' <<<"$raw")"
}

do_sessions() {
  local s
  # Same detection as claude-autostart: any session whose pane started claude.
  s="$({ tmux list-panes -a -F '#{session_name}@@#{pane_current_path}@@#{pane_start_command}@@#{pane_current_command}@@#{session_created}@@#{session_attached}' 2>/dev/null || true; } |
    awk -F'@@' -v l1="$CLAUDE_LOGIN_SESSION" -v l2="$AWS_LOGIN_SESSION" \
      '($3 ~ /claude/ || $4 == "claude") && $1 != l1 && $1 != l2 && !seen[$1]++' |
    jq -Rc 'split("@@") | {name:.[0], dir:.[1], project:(.[1] | split("/") | last),
      started_at:(.[4] | tonumber), attached:((.[5] | tonumber) > 0)}' |
    jq -sc --argjson now "$(date +%s)" 'map(. + {uptime_seconds:($now - .started_at)}) | sort_by(.started_at)')"
  api_ok "$(jq -cn --argjson s "${s:-[]}" --argjson now "$(date +%s)" '{now:$now, sessions:$s}')"
}

do_start() {
  [[ $# -eq 1 ]] || bad_args "usage: start <project>"
  valid_project "$1" || api_err invalid_name "Invalid project name."
  api_start_session "$1"
  api_ok "$(jq -cn --arg s "$STARTED_SESSION" --arg p "$PROJECTS_DIR/$1" --argjson a "$ALREADY_RUNNING" \
    '{session:$s, path:$p, already_running:$a}')"
}

# stop/tail take a project name or a tmux session name (the Sessions list
# sends session names, which may differ from the folder name).
resolve_session() {
  if tmux has-session -t "=$1" 2>/dev/null; then echo "$1"; else session_name "$1"; fi
}

do_stop() {
  [[ $# -eq 1 ]] || bad_args "usage: stop <project>"
  valid_project "$1" || api_err invalid_name "Invalid project name."
  local sess stopped=false
  sess="$(resolve_session "$1")"
  if tmux has-session -t "=$sess" 2>/dev/null && tmux kill-session -t "=$sess" 2>/dev/null; then
    stopped=true
    autostart_forget "$sess"
    autostart_save
  fi
  api_ok "$(jq -cn --arg s "$sess" --argjson st "$stopped" '{session:$s, stopped:$st}')"
}

# Safe restart: stop a session and start it again in the same folder,
# resuming the exact conversation it had (claude --resume <session id>), so
# Claude picks up new plugins, skills and MCP servers without losing context.
# Refuses while Claude is still working unless --force.
do_restart() {
  [[ $# -eq 1 || ($# -eq 2 && "$2" == --force) ]] || bad_args "usage: restart <project or session> [--force]"
  valid_project "$1" || api_err invalid_name "Invalid project name."
  local sess dir name sid="" pp resumed=false
  sess="$(resolve_session "$1")"
  tmux has-session -t "=$sess" 2>/dev/null || api_err invalid_name "No running session named '$1'."
  if [[ "${2:-}" != --force ]] && tmux capture-pane -p -t "=$sess:" 2>/dev/null |
    sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n 8 | grep -q "esc to interrupt"; then
    api_err session_busy "Claude is still working in '$sess'. Wait for it to finish, or restart anyway."
  fi
  # The folder: claude-autostart's list first, else where the pane is.
  dir="$(awk -F'\t' -v s="$sess" '$1 == s {print $2; exit}' "$AUTOSTART_LIST" 2>/dev/null)"
  [[ -n "$dir" ]] || dir="$(tmux display-message -p -t "=$sess:" '#{pane_current_path}' 2>/dev/null)"
  [[ -d "$dir" ]] || api_err internal "Couldn't find the folder of session '$sess'."
  name="$(basename "$dir")"
  valid_project "$name" || api_err invalid_name "The folder name '$name' can't be used as a session name."
  # The live conversation is the newest transcript in Claude's folder for this project.
  sid="$(ls -t "$HOME/.claude/projects/${dir//[\/.]/-}/"*.jsonl 2>/dev/null | head -n 1 | xargs -r basename | sed 's/\.jsonl$//')"
  [[ "$sid" =~ ^[0-9a-f-]{36}$ ]] || sid=""
  tmux kill-session -t "=$sess" 2>/dev/null
  STARTED_SESSION="$(session_name "$name")"
  if [[ -n "$sid" ]]; then
    trust_folder "$dir"
    tmux new-session -d -s "$STARTED_SESSION" -c "$dir" \
      "env -u ANTHROPIC_API_KEY claude --remote-control $(printf %q "$name") --resume $sid; exec bash" &&
      autostart_save
    sleep 4
    pp="$(tmux display-message -p -t "=$STARTED_SESSION:" '#{pane_pid}' 2>/dev/null)"
    # Claude runs as a child of the pane's shell; once it exits the shell becomes bash with no child.
    if [[ -n "$pp" ]] && pgrep -P "$pp" >/dev/null 2>&1; then
      resumed=true
    else   # resuming didn't stick: start fresh rather than leave nothing running
      tmux kill-session -t "=$STARTED_SESSION" 2>/dev/null
    fi
  fi
  if ! $resumed; then
    launch_session "$name" "$dir" || api_err internal "tmux could not start the session again."
  fi
  api_confirm_remote_control "$STARTED_SESSION"
  # A question on screen (e.g. "New MCP server found… Enter to confirm") means the
  # session is waiting for you; the app then opens it with the answer keys.
  local screen waiting=false
  sleep 2
  screen="$(tmux capture-pane -p -J -t "=$STARTED_SESSION:" 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n 120)"
  grep -qE "Enter to confirm|\(y/n\)" <<<"$(tail -n 15 <<<"$screen")" && waiting=true
  api_ok "$(jq -cn --arg s "$STARTED_SESSION" --arg p "$dir" --argjson r "$resumed" --arg id "$sid" \
    --argjson w "$waiting" --arg t "$screen" \
    '{session:$s, path:$p, restarted:true, resumed:$r, conversation:(if $r then $id else null end),
      waiting:$w, text:(if $w then $t else null end)}')"
}

do_tail() {
  local project="" lines=40
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --lines) [[ "${2:-}" =~ ^[0-9]{1,4}$ ]] || bad_args "--lines needs a number"; lines="$2"; shift 2 ;;
      -*) bad_args "unknown option $1" ;;
      *) [[ -z "$project" ]] || bad_args "usage: tail <project> [--lines N]"; project="$1"; shift ;;
    esac
  done
  [[ -n "$project" ]] || bad_args "usage: tail <project> [--lines N]"
  valid_project "$project" || api_err invalid_name "Invalid project name."
  ((lines < 1)) && lines=1
  ((lines > 200)) && lines=200
  local sess text
  sess="$(resolve_session "$project")"
  tmux has-session -t "=$sess" 2>/dev/null || api_err invalid_name "No running session for '$project'."
  text="$(tmux capture-pane -p -J -t "=$sess:" -S -500 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n "$lines")"
  api_ok "$(jq -cn --arg s "$sess" --arg t "$text" --argjson n "$lines" '{session:$s, lines:$n, text:$t}')"
}

# keys <session> <key>…: answer a prompt in a session (e.g. Claude asking to
# approve a new MCP server after a restart). Only these keys, never text.
KEYS_ALLOWED="1 2 3 4 5 6 7 8 9 Enter Escape Up Down Tab Space y n"
do_keys() {
  (($# >= 2 && $# <= 6)) || bad_args "usage: keys <session> <key>… (up to 5)"
  valid_project "$1" || api_err invalid_name "Invalid project name."
  local sess k text
  sess="$(resolve_session "$1")"; shift
  for k in "$@"; do [[ " $KEYS_ALLOWED " == *" $k "* ]] || api_err invalid_name "Key '$k' isn't allowed."; done
  tmux has-session -t "=$sess" 2>/dev/null || api_err invalid_name "No running session named '$sess'."
  for k in "$@"; do tmux send-keys -t "=$sess:" "$k"; sleep 0.2; done
  sleep 1   # let the screen redraw before reading it back
  text="$(tmux capture-pane -p -J -t "=$sess:" -S -500 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n 120)"
  api_ok "$(jq -cn --arg s "$sess" --arg t "$text" '{session:$s, lines:120, text:$t}')"
}

do_new() {
  local name="" owner="" vis="private" start=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --owner) owner="${2:-}"; shift 2 || bad_args "--owner needs a value" ;;
      --visibility) vis="${2:-}"; shift 2 || bad_args "--visibility needs a value" ;;
      --start) start=1; shift ;;
      -*) bad_args "unknown option $1" ;;
      *) [[ -z "$name" ]] || bad_args "usage: new <name> --owner <owner> --visibility private|public [--start]"; name="$1"; shift ;;
    esac
  done
  [[ -n "$name" ]] || bad_args "usage: new <name> --owner <owner> --visibility private|public [--start]"
  valid_project "$name" || api_err invalid_name "Use letters, numbers, dashes, dots or underscores (max 100)."
  [[ "$vis" == private || "$vis" == public ]] || bad_args "--visibility must be private or public"

  take_lock
  require_github
  [[ -z "$owner" ]] && owner="${DEFAULT_OWNER:-$GH_USER}"
  [[ "$owner" =~ $OWNER_RE ]] || api_err invalid_name "Invalid owner."
  local dir="$PROJECTS_DIR/$name" repo="$owner/$name" err
  [[ -e "$dir" ]] && api_err repo_exists "$dir already exists — pick another name, or open it from Projects."
  t 20 gh repo view "$repo" >/dev/null 2>&1 </dev/null &&
    api_err repo_exists "$repo already exists on GitHub — pick another name, or open it from Projects."

  git_identity >/dev/null 2>&1 || true
  mkdir -p "$PROJECTS_DIR"
  if ! err="$(cd "$PROJECTS_DIR" && t 50 gh repo create "$repo" "--$vis" --add-readme --clone 2>&1 </dev/null)"; then
    api_err internal "GitHub couldn't create the repo: $(tail -n 2 <<<"$err")"
  fi
  [[ -d "$dir/.git" ]] || t 40 gh repo clone "$repo" "$dir" >/dev/null 2>&1 </dev/null || true
  [[ -d "$dir/.git" ]] || api_err internal "Repo was created but couldn't be cloned. Try it from Projects."
  rm -f "$CACHE_DIR/repos.json"

  local sess=""
  if ((start)); then
    api_start_session "$name"
    sess="$STARTED_SESSION"
  fi
  api_ok "$(jq -cn --arg r "$repo" --arg u "https://github.com/$repo" --arg p "$dir" --arg s "$sess" --arg v "$vis" \
    '{repo:$r, url:$u, path:$p, visibility:$v, session:(if $s=="" then null else $s end)}')"
}

# Background clone worker (internal; not reachable through the runner).
clone_worker() {
  local repo="$1" dir="$2" start="$3" status="$4" err sess=""
  jq -cn --arg r "$repo" --argjson p "$$" '{state:"running", repo:$r, pid:$p}' >"$status"
  if err="$(t 1800 gh repo clone "$repo" "$dir" -- --quiet 2>&1 </dev/null)"; then
    if [[ "$start" == 1 ]]; then
      launch_session "${repo#*/}" "$dir" || true
      sess="$(session_name "${repo#*/}")"
      api_confirm_remote_control "$sess"
    fi
    jq -cn --arg r "$repo" --arg s "$sess" '{state:"done", repo:$r, session:(if $s=="" then null else $s end)}' >"$status"
  else
    # do_open only starts a worker when the folder didn't exist, so it's ours to remove.
    [[ "$dir" == /*/* && -d "$dir" ]] && rm -rf -- "$dir"
    jq -cn --arg r "$repo" --arg m "$(tail -n 3 <<<"$err")" '{state:"failed", repo:$r, message:$m}' >"$status"
  fi
  rm -f "$CACHE_DIR/repos.json"
}

do_clone_status() {
  [[ $# -eq 1 && "$1" =~ $REPO_RE ]] || bad_args "usage: clone-status <owner/repo>"
  local f
  f="$(clone_status_file "$1")"
  if [[ ! -f "$f" ]]; then
    api_ok '{"state":"none"}'
  elif clone_running "$f"; then
    api_ok '{"state":"running"}'
  elif [[ "$(jq -r '.state // ""' "$f" 2>/dev/null)" == running ]]; then
    rm -f "$f"
    api_ok '{"state":"failed","message":"The clone stopped unexpectedly."}'
  else
    local d
    d="$(jq -c '{state, message, session}' "$f" 2>/dev/null)"
    rm -f "$f"   # reported once, then forgotten
    api_ok "$d"
  fi
}

do_open() {
  local repo="" start=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --start) start=1; shift ;;
      -*) bad_args "unknown option $1" ;;
      *) [[ -z "$repo" ]] || bad_args "usage: open <owner/repo> [--start]"; repo="$1"; shift ;;
    esac
  done
  [[ -n "$repo" ]] || bad_args "usage: open <owner/repo> [--start]"
  [[ "$repo" =~ $REPO_RE ]] || api_err invalid_name "Invalid repo name."
  local name="${repo#*/}"
  valid_project "$name" || api_err invalid_name "Invalid repo name."

  take_lock
  require_github
  local dir="$PROJECTS_DIR/$name" action sess="" pending=false note="" origin
  if [[ -d "$dir/.git" ]]; then
    origin="$(origin_slug "$dir")"
    if [[ -n "$origin" && "$origin" != "$(tr 'A-Z' 'a-z' <<<"$repo")" ]]; then
      api_err repo_exists "The folder $dir holds $origin, not $repo, so it was left alone." \
        "$(jq -cn --arg p "$dir" '{path:$p}')"
    fi
    if [[ -n "$(git -C "$dir" status --porcelain 2>/dev/null)" ]]; then
      api_err folder_dirty "The folder has uncommitted changes, so it wasn't updated. Start anyway?" \
        "$(jq -cn --arg p "$dir" '{path:$p}')"
    fi
    # fds 3/9 closed: a detached `git gc --auto` must not hold the SSH channel or lock.
    if t 50 git -C "$dir" pull --ff-only --quiet >/dev/null 2>&1 </dev/null 3>&- 9>&-; then
      action="pulled"
    else
      action="not_updated"
      note="Couldn't fast-forward — check it manually."
    fi
    if ((start)); then api_start_session "$name"; sess="$STARTED_SESSION"; fi
  elif [[ -e "$dir" ]]; then
    api_err internal "$dir exists but isn't a git repo — skipping."
  else
    mkdir -p "$PROJECTS_DIR"
    local status
    status="$(clone_status_file "$repo")"
    jq -cn --arg r "$repo" '{state:"running", repo:$r}' >"$status"
    # Detach fully (fd 3 is the SSH channel) but keep the lock (fd 9) held
    # until the clone finishes.
    setsid "$SCRIPT_PATH" --clone-worker "$repo" "$dir" "$start" "$status" \
      </dev/null >/dev/null 2>&1 3>&- &
    local waited=0
    while [[ "$(jq -r '.state // ""' "$status" 2>/dev/null)" == running ]] && ((waited < 45)); do
      sleep 1; ((waited++))
    done
    local state
    state="$(jq -r '.state // ""' "$status" 2>/dev/null)"
    if [[ "$state" == failed ]]; then
      local msg
      msg="$(jq -r '.message // ""' "$status")"
      rm -f "$status"
      api_err internal "Clone failed: $msg"
    elif [[ "$state" == "done" ]]; then
      action="cloned"
      sess="$(jq -r '.session // ""' "$status")"
      rm -f "$status"
    else
      action="cloning"
      pending=true
    fi
  fi
  api_ok "$(jq -cn --arg p "$dir" --arg a "$action" --arg s "$sess" --argjson pe "$pending" --arg r "$repo" --arg n "$note" \
    '{repo:$r, path:$p, action:$a, pending:$pe, note:(if $n=="" then null else $n end), session:(if $s=="" then null else $s end)}')"
}

# ---------------- Login flows (secrets arrive on stdin only) ----------------

pane_text() { tmux capture-pane -p -J -t "=$1:" -S -200 2>/dev/null; }

# start_login_session <session> <binary> [args...]
# The binary goes in via the environment so the pane's start command doesn't
# say "claude"; claude-autostart saves any such pane and would bring a login
# back after a reboot. end_login also takes it off that list.
start_login_session() {
  local name="$1" bin="$2"; shift 2
  end_login "$name"
  tmux new-session -d -s "$name" -x 1000 -y 60 -e "LOGIN_BIN=$bin" \
    bash -c 'env -u ANTHROPIC_API_KEY "$LOGIN_BIN" "$@"; echo "[exit $?]"; sleep 900' login "$@" >/dev/null 2>&1
}

end_login() {
  tmux kill-session -t "=$1" 2>/dev/null
  autostart_forget "$1"
}

# Secrets come on stdin; never wait for them forever.
read_secret_line() { local v=""; IFS= read -r -t 15 v || true; v="${v//$'\r'/}"; printf '%s' "$v"; }

# Changes whenever claude writes new credentials.
claude_creds_sig() { stat -c '%Y:%s' "$HOME/.claude/.credentials.json" 2>/dev/null || echo none; }

do_login_claude_start() {
  need tmux
  command -v claude >/dev/null 2>&1 || api_err internal "'claude' is not installed on the server."
  start_login_session "$CLAUDE_LOGIN_SESSION" "$(command -v claude)" auth login ||
    api_err internal "Could not start the login session."
  local i text="" url="" enters=0 line
  for ((i = 0; i < 60; i++)); do
    sleep 0.5
    text="$(pane_text "$CLAUDE_LOGIN_SESSION")"
    url="$(grep -oE 'https://[^[:space:]]*(oauth|authorize|login)[^[:space:]]*' <<<"$text" | tail -n 1)"
    [[ -n "$url" ]] && break
    grep -q '\[exit ' <<<"$text" && break
    # A method picker: choose the claude.ai subscription entry by its label.
    if ((enters < 3)) && grep -qiE 'subscription|claude\.ai|select login method|choose' <<<"$text"; then
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
    end_login "$CLAUDE_LOGIN_SESSION"
    api_err internal "No login URL appeared within 30 s." "$(jq -cn --arg t "$(tail -n 15 <<<"$text")" '{pane:$t}')"
  fi
  api_ok "$(jq -cn --arg u "$url" --arg s "$CLAUDE_LOGIN_SESSION" '{url:$u, session:$s}')"
}

do_login_claude_code() {
  local code i text=""
  code="$(read_secret_line)"
  exec 0</dev/null
  code="${code//[[:space:]]/}"
  [[ "$code" =~ ^[A-Za-z0-9#_.~=+/-]{4,1024}$ ]] || api_err invalid_name "That doesn't look like a login code."
  tmux has-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null ||
    api_err not_logged_in_claude "The login session expired. Start the Claude login again."
  # Success means claude wrote *new* credentials: an old file doesn't count.
  local before
  before="$(claude_creds_sig)"
  tmux clear-history -t "=$CLAUDE_LOGIN_SESSION:" 2>/dev/null
  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" -l -- "$code"
  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" Enter
  for ((i = 0; i < 45; i++)); do
    sleep 1
    if claude_logged_in && [[ "$(claude_creds_sig)" != "$before" ]]; then
      end_login "$CLAUDE_LOGIN_SESSION"
      api_ok '{"logged_in":true}'
    fi
    text="$(pane_text "$CLAUDE_LOGIN_SESSION")"
    grep -qiE 'invalid|error|failed|expired|\[exit [0-9]' <<<"$text" && break
  done
  sleep 1
  if claude_logged_in && [[ "$(claude_creds_sig)" != "$before" ]]; then
    end_login "$CLAUDE_LOGIN_SESSION"
    api_ok '{"logged_in":true}'
  fi
  # If claude gave up, the login has to start over.
  grep -q '\[exit ' <<<"$text" && end_login "$CLAUDE_LOGIN_SESSION"
  api_err not_logged_in_claude "Login did not complete. Start the Claude login again." \
    "$(jq -cn --arg t "$(tail -n 15 <<<"$text")" '{pane:$t}')"
}

do_login_claude_cancel() {
  end_login "$CLAUDE_LOGIN_SESSION"
  api_ok '{"cancelled":true}'
}

do_login_github() {
  local token err user need_scopes
  token="$(read_secret_line)"
  exec 0</dev/null
  token="${token//[[:space:]]/}"
  [[ "$token" =~ ^[A-Za-z0-9_]{20,255}$ ]] || api_err invalid_name "That doesn't look like a GitHub token."
  need gh
  if ! err="$(printf '%s\n' "$token" | t 30 gh auth login --hostname github.com --git-protocol https --with-token 2>&1)"; then
    api_err not_logged_in_github "GitHub didn't accept that token: $(tail -n 2 <<<"$err")"
  fi
  unset token
  gh auth setup-git >/dev/null 2>&1 </dev/null || true
  user="$(gh api user --jq .login 2>/dev/null </dev/null)"
  [[ -n "$user" ]] || api_err not_logged_in_github "Token saved, but GitHub login still fails."
  GH_USER="$user"
  git_identity >/dev/null 2>&1 || true
  rm -f "$CACHE_DIR/repos.json"
  need_scopes="$(missing_scopes)"
  if [[ -n "$need_scopes" ]]; then
    api_err missing_scopes "Your GitHub token is missing scopes: $need_scopes" \
      "$(tr ' ' '\n' <<<"$need_scopes" | grep -v '^$' | jq -R . | jq -sc --arg u "$user" '{missing:., user:$u}')"
  fi
  api_ok "$(jq -cn --arg u "$user" '{logged_in:true, user:$u}')"
}

do_login_aws_keys() {
  local key_id secret region profile csv err ident
  key_id="$(read_secret_line)"; secret="$(read_secret_line)"; region="$(read_secret_line)"
  exec 0</dev/null
  key_id="${key_id//[[:space:]]/}" secret="${secret//[[:space:]]/}" region="${region//[[:space:]]/}"
  [[ -z "$region" ]] && region="$AWS_DEFAULT_REGION_NAME"
  [[ "$key_id" =~ ^[A-Z0-9]{16,128}$ ]] || api_err invalid_name "That access key ID doesn't look right."
  [[ "$secret" =~ ^[A-Za-z0-9/+=]{16,128}$ ]] || api_err invalid_name "That secret key doesn't look right."
  [[ "$region" =~ ^[a-z0-9-]{2,32}$ ]] || api_err invalid_name "That region doesn't look right."
  need aws
  # `aws configure import` reads the secret from a file, so it never shows up
  # in the process list the way `aws configure set <secret>` would.
  profile="${AWS_PROFILE_NAME:-default}"
  csv="$(mktemp "$API_STATE_DIR/awskeys.XXXXXX")"
  trap 'rm -f "$csv"; on_exit' EXIT
  chmod 600 "$csv"
  printf 'User Name,Access key ID,Secret access key\n%s,%s,%s\n' "$profile" "$key_id" "$secret" >"$csv"
  unset secret
  err="$(t 30 aws configure import --csv "file://$csv" --skip-invalid 2>&1 </dev/null)"
  local rc=$?
  rm -f "$csv"
  ((rc == 0)) || api_err internal "Saving keys failed (AWS CLI v2 is required): $(tail -n 2 <<<"$err")"
  aws configure set region "$region" --profile "$profile" </dev/null >/dev/null 2>&1
  aws configure set output json --profile "$profile" </dev/null >/dev/null 2>&1
  ident="$(t 20 aws sts get-caller-identity "${AWS_ARGS[@]}" --output json 2>/dev/null </dev/null)"
  [[ -n "$ident" ]] || api_err not_logged_in_aws "Keys saved, but AWS rejected them."
  api_ok "$(jq -c --arg r "$region" '{logged_in:true, account:.Account, arn:.Arn, region:$r}' <<<"$ident")"
}

do_login_aws_sso_start() {
  need aws
  need tmux
  aws_sso_configured ||
    api_err not_logged_in_aws "AWS SSO isn't configured on the server yet. Use access keys, or run 'aws configure sso --use-device-code' there once." '{"sso_configured":false}'
  start_login_session "$AWS_LOGIN_SESSION" "$(command -v aws)" sso login --use-device-code --no-browser "${AWS_ARGS[@]}" ||
    api_err internal "Could not start the SSO login."
  local i text="" url="" code=""
  for ((i = 0; i < 60; i++)); do
    sleep 0.5
    text="$(pane_text "$AWS_LOGIN_SESSION")"
    url="$(grep -oE 'https://[^[:space:]]+' <<<"$text" | tail -n 1)"
    code="$(grep -oE '\b[A-Z0-9]{4}-[A-Z0-9]{4}\b' <<<"$text" | tail -n 1)"
    [[ -n "$url" && -n "$code" ]] && break
    grep -q '\[exit ' <<<"$text" && break
  done
  if [[ -z "$url" ]]; then
    end_login "$AWS_LOGIN_SESSION"
    api_err internal "No SSO URL appeared within 30 s." "$(jq -cn --arg t "$(tail -n 15 <<<"$text")" '{pane:$t}')"
  fi
  api_ok "$(jq -cn --arg u "$url" --arg c "$code" '{url:$u, code:(if $c=="" then null else $c end)}')"
}

do_login_gitlab() {
  local token host err user
  token="$(read_secret_line)"; host="$(read_secret_line)"
  exec 0</dev/null
  token="${token//[[:space:]]/}" host="${host//[[:space:]]/}"
  [[ -z "$host" ]] && host="gitlab.com"
  [[ "$token" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{19,254}$ ]] || api_err invalid_name "That doesn't look like a GitLab token."
  [[ "$host" =~ ^[A-Za-z0-9][A-Za-z0-9.-]{0,252}(:[0-9]{1,5})?$ ]] || api_err invalid_name "That GitLab host doesn't look right."
  need glab
  if ! err="$(printf '%s\n' "$token" | t 30 glab auth login --hostname "$host" --stdin 2>&1)"; then
    api_err not_logged_in "GitLab didn't accept that token: $(tail -n 2 <<<"$err")"
  fi
  unset token
  user="$(t 15 glab api user --hostname "$host" 2>/dev/null </dev/null | jq -r '.username // empty' 2>/dev/null)"
  [[ -n "$user" ]] || api_err not_logged_in "Token saved, but GitLab login still fails."
  api_ok "$(jq -cn --arg u "$user" '{logged_in:true, user:$u}')"
}

do_login_docker() {
  local reg user token err
  reg="$(read_secret_line)"; user="$(read_secret_line)"; token="$(read_secret_line)"
  exec 0</dev/null
  reg="${reg//[[:space:]]/}" user="${user//[[:space:]]/}" token="${token//[[:space:]]/}"
  [[ -z "$reg" ]] && reg="docker.io"
  [[ "$reg" =~ ^[a-z0-9][a-z0-9.-]{0,252}(:[0-9]{1,5})?$ ]] || api_err invalid_name "That registry doesn't look right."
  [[ "$user" =~ ^[A-Za-z0-9][A-Za-z0-9._@-]{0,99}$ ]] || api_err invalid_name "That username doesn't look right."
  [[ "$token" =~ ^[^[:space:]]{8,512}$ ]] || api_err invalid_name "That token or password doesn't look right."
  need docker
  if ! err="$(printf '%s\n' "$token" | t 30 docker login "$reg" -u "$user" --password-stdin 2>&1)"; then
    api_err not_logged_in "The registry didn't accept that login: $(tail -n 2 <<<"$err")"
  fi
  unset token
  api_ok "$(jq -cn --arg r "$reg" --arg u "$user" '{logged_in:true, user:$u, registry:$r}')"
}

# ---- Token services ----------------------------------------------------------
# CLIs that read a token from the environment. The token is checked against
# the provider's API, then kept in a mode-600 env file that ~/.bashrc loads
# (Claude Code picks up the shell's environment from it).

# svc_def <id>: SVC_VARS (env var names, in stdin order), SVC_RES (a regex per
# var), SVC_CLI (binary to look for; empty = none needed), SVC_KEYFILE (the
# credential is a service-account JSON file instead of a token).
svc_def() {
  SVC_CLI="" SVC_KEYFILE="" SVC_VARS=() SVC_RES=()
  local tok='^[A-Za-z0-9_-]{20,300}$'
  case "$1" in
    # User tokens, or account tokens (cfat_…) with their account ID (optional var).
    cloudflare) SVC_VARS=(CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID) SVC_RES=('^[A-Za-z0-9_-]{30,100}$' '^([0-9a-f]{32})?$') ;;
    vercel) SVC_VARS=(VERCEL_TOKEN) SVC_RES=("$tok") SVC_CLI=vercel ;;
    netlify) SVC_VARS=(NETLIFY_AUTH_TOKEN) SVC_RES=("$tok") SVC_CLI=netlify ;;
    fly) SVC_VARS=(FLY_API_TOKEN) SVC_RES=('^FlyV1 [A-Za-z0-9_+/=,-]{20,8000}$') SVC_CLI=flyctl ;;
    railway) SVC_VARS=(RAILWAY_API_TOKEN) SVC_RES=("$tok") SVC_CLI=railway ;;
    supabase) SVC_VARS=(SUPABASE_ACCESS_TOKEN) SVC_RES=("$tok") SVC_CLI=supabase ;;
    neon) SVC_VARS=(NEON_API_KEY) SVC_RES=("$tok") SVC_CLI=neon ;;
    npm) SVC_VARS=(NPM_TOKEN) SVC_RES=('^npm_[A-Za-z0-9]{20,100}$') SVC_CLI=npm ;;
    stripe) SVC_VARS=(STRIPE_API_KEY) SVC_RES=('^(sk|rk)_(test|live)_[A-Za-z0-9]{10,250}$') SVC_CLI=stripe ;;
    huggingface) SVC_VARS=(HF_TOKEN) SVC_RES=('^hf_[A-Za-z0-9]{20,100}$') SVC_CLI=hf ;;
    b2) SVC_VARS=(B2_APPLICATION_KEY_ID B2_APPLICATION_KEY) SVC_RES=('^[A-Za-z0-9]{12,40}$' '^[A-Za-z0-9/+]{20,80}$') SVC_CLI=b2 ;;
    gcp) SVC_VARS=(CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE CLOUDSDK_CORE_PROJECT) SVC_KEYFILE=gcp-key.json SVC_CLI=gcloud ;;
    firebase) SVC_VARS=(GOOGLE_APPLICATION_CREDENTIALS) SVC_KEYFILE=firebase-key.json SVC_CLI=firebase ;;
    *) return 1 ;;
  esac
}

# curl_auth <header> [curl args…] <url>: the header travels in curl's config on
# stdin, so the secret never shows up in the process list.
curl_auth() {
  local hdr="$1"; shift
  printf 'header = "%s"\n' "$hdr" | curl -fsS --max-time 15 -K - "$@" 2>/dev/null
}

# svc_verify <id> <values…>: prints who the credential belongs to; fails if the
# provider rejects it.
svc_verify() {
  local id="$1" r; shift
  case "$id" in
    cloudflare)  # account tokens verify under their account, user tokens under /user
      if [[ -n "${2:-}" ]]; then r="accounts/$2"; else r="user"; fi
      curl_auth "Authorization: Bearer $1" "https://api.cloudflare.com/client/v4/$r/tokens/verify" |
        jq -er --arg k "${r%%/*}" 'select(.success == true) | if $k == "user" then "user token" else "account token" end' ;;
    vercel) curl_auth "Authorization: Bearer $1" https://api.vercel.com/v2/user | jq -er '.user.username' ;;
    netlify) curl_auth "Authorization: Bearer $1" -A "cLaudeRC" https://api.netlify.com/api/v1/user | jq -er '.email' ;;
    fly) echo "token saved (Fly checks it on first use)" ;;
    railway) curl_auth "Authorization: Bearer $1" -H 'Content-Type: application/json' \
      -d '{"query":"query { me { email } }"}' https://backboard.railway.com/graphql/v2 | jq -er '.data.me.email' ;;
    supabase) curl_auth "Authorization: Bearer $1" https://api.supabase.com/v1/profile |
      jq -er '.username // .primary_email' ;;
    neon) r="$(curl_auth "Authorization: Bearer $1" -H 'Accept: application/json' https://console.neon.tech/api/v2/users/me)" &&
      jq -er '.email' <<<"$r" ||
      { curl_auth "Authorization: Bearer $1" -H 'Accept: application/json' https://console.neon.tech/api/v2/projects >/dev/null &&
        echo "API key OK"; } ;;
    npm) curl_auth "Authorization: Bearer $1" https://registry.npmjs.org/-/whoami | jq -er '.username' ;;
    stripe) curl_auth "Authorization: Bearer $1" https://api.stripe.com/v1/balance |
      jq -er 'select(.object == "balance") | if .livemode then "live mode" else "test mode" end' ;;
    huggingface) curl_auth "Authorization: Bearer $1" https://huggingface.co/api/whoami-v2 | jq -er '.name' ;;
    b2) printf 'user = "%s:%s"\n' "$1" "$2" |
      curl -fsS --max-time 15 -K - https://api.backblazeb2.com/b2api/v4/b2_authorize_account 2>/dev/null |
      jq -er '"account " + .accountId' ;;
    gcp | firebase) jq -er 'select(.type == "service_account" and .project_id and .client_email) | .client_email' <<<"$1" ;;
  esac
}

# ensure_env_hook: make the env file reach Claude's commands.
# Claude sessions don't read ~/.bashrc (tmux starts claude directly, and its
# shell snapshot keeps only functions, aliases and PATH), so the reliable path
# is Claude Code's own settings: "env": {"BASH_ENV": <env file>} in
# ~/.claude/settings.json. Bash sources $BASH_ENV before every non-interactive
# command, which is how Claude runs them. The .bashrc line is kept for your own
# terminal. Takes effect when a session (re)starts.
ensure_env_hook() {
  [[ -f "$SERVICES_ENV" ]] || return 0
  grep -qF "$SERVICES_ENV_HOOK" "$HOME/.bashrc" 2>/dev/null || printf '\n%s\n' "$SERVICES_ENV_HOOK" >>"$HOME/.bashrc"
  local f="$HOME/.claude/settings.json" tmp
  [[ "$(jq -r '.env.BASH_ENV // empty' "$f" 2>/dev/null)" == "$SERVICES_ENV" ]] && return 0
  mkdir -p "$HOME/.claude"
  [[ -s "$f" ]] || echo '{}' >"$f"
  jq -e . "$f" >/dev/null 2>&1 || return 0   # never overwrite a settings file we can't parse
  tmp="$(mktemp "$f.XXXXXX")" || return 0
  jq --arg p "$SERVICES_ENV" '.env = ((.env // {}) + {BASH_ENV: $p})' "$f" >"$tmp" && cat "$tmp" >"$f"
  rm -f "$tmp"
}

# set_env <VAR> <value>: replace VAR's line in the env file (values are
# regex-checked, so they never contain a quote). An empty value removes it.
set_env() {
  local tmp
  mkdir -p "$LAUNCHER_CONFIG_DIR"
  tmp="$(mktemp "$SERVICES_ENV.XXXXXX")"
  chmod 600 "$tmp"
  { grep -v "^export $1=" "$SERVICES_ENV" 2>/dev/null; [[ -z "$2" ]] || printf "export %s='%s'\n" "$1" "$2"; } >"$tmp"
  mv "$tmp" "$SERVICES_ENV"
}

# login-token <service>: stdin = one value per SVC_VARS line, or the whole
# service-account JSON for key-file services.
do_login_token() {
  [[ $# -eq 1 ]] && svc_def "$1" || bad_args "usage: login-token <service>"
  local id="$1" who v i vals=() key tmp
  if [[ -n "$SVC_KEYFILE" ]]; then
    key="$(head -c 20000)"
    exec 0</dev/null
    who="$(svc_verify "$id" "$key")" || api_err invalid_name "That isn't a Google service-account JSON key."
    mkdir -p "$LAUNCHER_CONFIG_DIR"
    tmp="$(mktemp "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE.XXXXXX")"
    chmod 600 "$tmp"
    printf '%s\n' "$key" >"$tmp"
    mv "$tmp" "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
    set_env "${SVC_VARS[0]}" "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
    [[ "$id" == gcp ]] && set_env CLOUDSDK_CORE_PROJECT "$(jq -r '.project_id' <<<"$key" | tr -cd 'a-z0-9-')"
    unset key
  else
    for ((i = 0; i < ${#SVC_VARS[@]}; i++)); do
      v="$(read_secret_line)"
      v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"   # trim
      [[ "$v" =~ ${SVC_RES[$i]} ]] || api_err invalid_name "That ${SVC_VARS[$i]} doesn't look right."
      vals+=("$v")
    done
    exec 0</dev/null
    [[ "$id" == cloudflare && "${vals[0]}" == cfat_* && -z "${vals[1]}" ]] &&
      api_err invalid_name "That's an account token (cfat_…): add its Account ID too (shown when you created it)."
    need curl
    who="$(svc_verify "$id" "${vals[@]}")" || api_err not_logged_in "The provider didn't accept that. Check it and try again."
    for ((i = 0; i < ${#SVC_VARS[@]}; i++)); do set_env "${SVC_VARS[$i]}" "${vals[$i]}"; done
    unset vals
    # npm reads the token through ~/.npmrc, which expands ${NPM_TOKEN} itself.
    if [[ "$id" == npm ]] && ! grep -qF '//registry.npmjs.org/:_authToken=${NPM_TOKEN}' "$HOME/.npmrc" 2>/dev/null; then
      printf '%s\n' '//registry.npmjs.org/:_authToken=${NPM_TOKEN}' >>"$HOME/.npmrc"
    fi
  fi
  ensure_env_hook
  tmp="$(mktemp "$SERVICES_INFO.XXXXXX")"
  { jq -c . "$SERVICES_INFO" 2>/dev/null || echo '{}'; } | jq -c --arg id "$id" --arg w "$who" '.[$id] = $w' >"$tmp"
  mv "$tmp" "$SERVICES_INFO"
  api_ok "$(jq -cn --arg w "$who" '{logged_in:true, user:$w}')"
}

# Older apps call this name.
do_login_cloudflare() { do_login_token cloudflare; }

# Update these scripts to <commit> by running that commit's install.sh.
do_self_update() {
  local sha="${1:-}" dir out
  [[ $# -eq 1 && "$sha" =~ ^[0-9a-f]{40}$ ]] || bad_args "usage: self-update <commit>"
  need curl
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"; on_exit' EXIT
  curl -fsSL --max-time 30 "$CLAUDERC_RAW/$CLAUDERC_REPO/$sha/server/install.sh" -o "$dir/install.sh" 2>/dev/null ||
    api_err internal "Couldn't download install.sh for commit ${sha:0:7}."
  out="$(CLAUDERC_COMMIT="$sha" t 180 bash "$dir/install.sh" </dev/null 2>&1)" ||
    api_err internal "The update failed: $(tail -n 3 <<<"$out")"
  [[ "$(cat "$INSTALLED_COMMIT_FILE" 2>/dev/null)" == "$sha" ]] ||
    api_err internal "The update ran but didn't record commit ${sha:0:7}."
  api_ok "$(jq -cn --arg c "$sha" '{commit:$c}')"
}

# sudo_askpass <dir> <password>: later `sudo` calls in this shell read the
# password through an askpass helper from a private file: never on a command
# line, never in the environment.
sudo_askpass() {
  local dir="$1" real_sudo
  [[ -n "$2" ]] && real_sudo="$(command -v sudo)" || return 0
  printf '%s\n' "$2" >"$dir/pw"
  printf '#!/bin/sh\ncat %q\n' "$dir/pw" >"$dir/askpass"
  mkdir -p "$dir/bin"
  printf '#!/bin/sh\nexec %q -A "$@"\n' "$real_sudo" >"$dir/bin/sudo"
  chmod 700 "$dir/askpass" "$dir/bin/sudo"
  chmod 600 "$dir/pw"
  export SUDO_ASKPASS="$dir/askpass" PATH="$dir/bin:$PATH"
}

put_bin() {  # put_bin <file> <name>: into ~/.local/bin, replaced by rename
  mkdir -p "$HOME/.local/bin"
  install -m 755 "$1" "$HOME/.local/bin/$2.new" && mv -f "$HOME/.local/bin/$2.new" "$HOME/.local/bin/$2"
}

gh_latest_tag() { curl -fsSL --max-time 20 "https://api.github.com/repos/$1/releases/latest" 2>/dev/null | jq -r '.tag_name // empty'; }

# fetch <url> <file> [checksums-url]: download; with a checksums file, the
# file's sha256 must be listed in it.
fetch() {
  curl -fsSL --max-time 480 "$1" -o "$2" 2>/dev/null || api_err internal "Downloading $(basename "$1") failed."
  [[ -z "${3:-}" ]] && return 0
  curl -fsSL --max-time 30 "$3" -o "$2.sums" 2>/dev/null || api_err internal "Downloading the checksums failed."
  grep -qi "$(sha256sum "$2" | cut -d' ' -f1)" "$2.sums" ||
    api_err internal "$(basename "$1") failed its checksum check; nothing was installed."
}

# from_tar <tgz> <binary> <dir>: extract, set FOUND to <binary>'s path inside.
# (Not a $(…) helper: api_err must exit the script, not a subshell.)
from_tar() {
  mkdir -p "$3/x" && tar -xzf "$1" -C "$3/x" 2>/dev/null || api_err internal "$(basename "$1") isn't a valid archive."
  FOUND="$(find "$3/x" -type f -name "$2" | head -n 1)"
  [[ -n "$FOUND" ]] || api_err internal "$2 wasn't in $(basename "$1")."
}

INSTALLABLE="glab docker supabase flyctl stripe railway neon b2 vercel netlify firebase hf gcloud"

# Install a CLI the phone can't otherwise get onto the server: into ~/.local/bin,
# no sudo, checksum-verified where the vendor publishes checksums. Docker is
# the one that needs root: stdin line 1 = sudo password.
do_install_cli() {
  [[ $# -eq 1 && " $INSTALLABLE " == *" $1 "* ]] || bad_args "usage: install-cli <$INSTALLABLE>"
  local name="$1" pw="" dir tag ver f a64 bin out
  IFS= read -r -t 5 pw || true
  exec 0</dev/null
  need curl; need tar; need sha256sum
  case "$(uname -m)" in x86_64) a64=false ;; aarch64 | arm64) a64=true ;; *) api_err internal "No $name build for this CPU ($(uname -m))." ;; esac
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"; on_exit' EXIT
  gh_ver() { tag="$(gh_latest_tag "$1")"; ver="${tag#v}"; [[ -n "$tag" ]] || api_err internal "Couldn't find the latest $name release."; }
  case "$name" in
    glab) install_glab "$dir" "$a64"; return ;;
    docker)
      sudo_askpass "$dir" "$pw"; unset pw
      fetch https://get.docker.com "$dir/get-docker.sh"
      out="$(t 600 sudo sh "$dir/get-docker.sh" </dev/null 2>&1 && sudo usermod -aG docker "$(id -un)" </dev/null 2>&1)" ||
        api_err internal "Installing Docker failed: $(tail -n 3 <<<"$out")"
      ver="$(docker --version 2>/dev/null)"
      # Running tmux (and so every Claude session) keeps the groups it started
      # with, so the new docker group only applies after a restart.
      id -nG | tr ' ' '\n' | grep -qx docker || NOTE="Docker is installed. Reboot the server so Claude's sessions can use docker without sudo." ;;
    supabase)
      gh_ver supabase/cli; f="supabase_${ver}_linux_$($a64 && echo arm64 || echo amd64).tar.gz"
      fetch "https://github.com/supabase/cli/releases/download/$tag/$f" "$dir/$f" "https://github.com/supabase/cli/releases/download/$tag/checksums.txt"
      from_tar "$dir/$f" supabase "$dir"; put_bin "$FOUND" supabase ;;
    flyctl)
      gh_ver superfly/flyctl; f="flyctl_${ver}_Linux_$($a64 && echo arm64 || echo x86_64).tar.gz"
      fetch "https://github.com/superfly/flyctl/releases/download/$tag/$f" "$dir/$f" "https://github.com/superfly/flyctl/releases/download/$tag/flyctl_${ver}_checksums.txt"
      from_tar "$dir/$f" flyctl "$dir"; put_bin "$FOUND" flyctl
      ln -sf flyctl "$HOME/.local/bin/fly" ;;
    stripe)
      gh_ver stripe/stripe-cli; f="stripe_${ver}_linux_$($a64 && echo arm64 || echo x86_64).tar.gz"
      fetch "https://github.com/stripe/stripe-cli/releases/download/$tag/$f" "$dir/$f" "https://github.com/stripe/stripe-cli/releases/download/$tag/stripe-linux-checksums.txt"
      from_tar "$dir/$f" stripe "$dir"; put_bin "$FOUND" stripe ;;
    railway)  # no checksums published
      gh_ver railwayapp/cli; f="railway-$tag-$($a64 && echo aarch64 || echo x86_64)-unknown-linux-musl.tar.gz"
      fetch "https://github.com/railwayapp/cli/releases/download/$tag/$f" "$dir/$f"
      from_tar "$dir/$f" railway "$dir"; put_bin "$FOUND" railway ;;
    neon)  # standalone binary, no checksums published
      f="neon-linux-$($a64 && echo arm64 || echo x64)"
      fetch "https://github.com/neondatabase/neon-pkgs/releases/latest/download/$f" "$dir/$f"
      put_bin "$dir/$f" neon; ln -sf neon "$HOME/.local/bin/neonctl"; ver="latest" ;;
    b2)
      f="b2-linux$($a64 && echo -aarch64)"
      fetch "https://github.com/Backblaze/B2_Command_Line_Tool/releases/latest/download/$f" "$dir/$f" \
        "https://github.com/Backblaze/B2_Command_Line_Tool/releases/latest/download/${f}_hashes.txt"
      put_bin "$dir/$f" b2; ver="latest" ;;
    vercel | netlify | firebase)
      command -v npm >/dev/null 2>&1 || api_err internal "$name needs Node.js and npm on the server first (e.g. sudo apt install nodejs npm)."
      f="$name"; [[ "$name" == netlify ]] && f=netlify-cli; [[ "$name" == firebase ]] && f=firebase-tools
      out="$(t 480 npm install -g --prefix "$HOME/.local" "$f" </dev/null 2>&1)" || api_err internal "npm couldn't install $f: $(tail -n 3 <<<"$out")"
      ver="latest" ;;
    hf)  # Hugging Face's own installer: a venv in ~/.hf-cli and a wrapper in ~/.local/bin
      command -v python3 >/dev/null 2>&1 || api_err internal "hf needs Python 3.10+ on the server first."
      fetch https://hf.co/cli/install.sh "$dir/hf-install.sh"
      out="$(t 480 bash "$dir/hf-install.sh" --exclude-skill --no-modify-path </dev/null 2>&1)" || api_err internal "The hf installer failed: $(tail -n 3 <<<"$out")"
      ver="latest" ;;
    gcloud)
      [[ ! -e "$HOME/google-cloud-sdk" ]] || api_err internal "google-cloud-sdk already exists in your home folder; update it with 'gcloud components update'."
      f="google-cloud-cli-linux-$($a64 && echo arm || echo x86_64).tar.gz"
      fetch "https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/$f" "$dir/$f"
      tar -xzf "$dir/$f" -C "$HOME" 2>/dev/null || api_err internal "$f isn't a valid archive."
      out="$(t 480 "$HOME/google-cloud-sdk/install.sh" --quiet --usage-reporting=false --path-update=false --command-completion=false </dev/null 2>&1)" ||
        api_err internal "The gcloud installer failed: $(tail -n 3 <<<"$out")"
      for bin in gcloud gsutil bq; do ln -sf "$HOME/google-cloud-sdk/bin/$bin" "$HOME/.local/bin/$bin"; done
      ver="latest" ;;
  esac
  api_ok "$(jq -cn --arg n "$name" --arg v "${ver:-}" --arg note "${NOTE:-}" \
    '{installed:true, name:$n, version:$v} + (if $note == "" then {} else {note:$note} end)')"
}

# The latest official glab release from gitlab.com, checksum-verified.
install_glab() {
  local dir="$1" arch rel ver tgz sums_url tgz_url
  arch="$($2 && echo arm64 || echo amd64)"
  rel="$(curl -fsSL --max-time 20 "https://gitlab.com/api/v4/projects/gitlab-org%2Fcli/releases/permalink/latest" 2>/dev/null)"
  ver="$(jq -r '.tag_name // empty' <<<"$rel" 2>/dev/null)"; ver="${ver#v}"
  [[ "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || api_err internal "Couldn't find the latest glab release on gitlab.com."
  tgz="glab_${ver}_linux_${arch}.tar.gz"
  tgz_url="$(jq -r --arg n "$tgz" '.assets.links[] | select(.name==$n) | .direct_asset_url' <<<"$rel")"
  sums_url="$(jq -r '.assets.links[] | select(.name=="checksums.txt") | .direct_asset_url' <<<"$rel")"
  [[ "$tgz_url" == https://gitlab.com/* && "$sums_url" == https://gitlab.com/* ]] ||
    api_err internal "The glab $ver release has no $tgz."
  fetch "$tgz_url" "$dir/$tgz" "$sums_url"
  from_tar "$dir/$tgz" glab "$dir"; put_bin "$FOUND" glab
  api_ok "$(jq -cn --arg v "$ver" '{installed:true, name:"glab", version:$v}')"
}

# Run a command typed on the phone (behind App lock there). Off unless the
# server's config says ALLOW_RUN=1, so a lost phone can't turn it on.
# stdin: line 1 = sudo password (may be empty), line 2 = timeout seconds,
# the rest = the command. Output is stdout+stderr, last 64 KB.
do_run() {
  [[ $# -eq 0 ]] || bad_args "run takes no arguments"
  [[ "$ALLOW_RUN" == 1 ]] ||
    api_err run_disabled "Running commands from the phone is off on this server. To allow it, run this on the server: echo 'ALLOW_RUN=1' >> ~/.config/claude-launcher/config"
  local pw secs cmd dir out rc
  IFS= read -r -t 15 pw || true; pw="${pw//$'\r'/}"
  IFS= read -r -t 15 secs || true
  cmd="$(head -c 65536)"
  exec 0</dev/null
  [[ "$secs" =~ ^[0-9]{1,3}$ ]] && ((secs >= 1 && secs <= 600)) || secs=120
  [[ -n "${cmd//[[:space:]]/}" ]] || bad_args "empty command"
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"; on_exit' EXIT
  sudo_askpass "$dir" "$pw"
  unset pw
  out="$(cd "$HOME" && timeout --kill-after=5 "$secs" bash -c "$cmd" </dev/null 2>&1)"
  rc=$?
  rm -rf "$dir"
  ((rc == 124)) && out+=$'\n'"[stopped after $secs s]"
  api_ok "$(jq -cn --arg o "$(tail -c 65536 <<<"$out")" --argjson rc "$rc" '{exit_code:$rc, output:$o}')"
}

# Command Center: a fixed set of `claude` management commands (no shell).
# stdin: the arguments after `claude`, space separated, on one line.
do_claude_cmd() {
  [[ $# -eq 0 ]] || bad_args "claude-cmd takes its arguments on stdin"
  local line out rc x
  IFS= read -r -t 15 line || true
  exec 0</dev/null
  local -a a=()
  read -r -a a <<<"${line//$'\r'/}"   # plain word split: no globbing, no quotes, no expansion
  ((${#a[@]} > 0 && ${#a[@]} <= 20)) || bad_args "usage: claude-cmd (arguments on stdin)"
  for x in "${a[@]}"; do
    [[ "$x" =~ ^[A-Za-z0-9@._:/=+~-]{1,200}$ ]] || api_err invalid_name "'$x' has characters Command Center doesn't allow."
  done
  local allowed=false
  case "${a[0]}" in
    doctor | --version | update | upgrade) ((${#a[@]} == 1)) && allowed=true ;;
    plugin | plugins)
      case "${a[1]:-}" in
        list | install | i | uninstall | remove | enable | disable | update | details) allowed=true ;;
        marketplace) [[ "${a[2]:-}" =~ ^(list|add|remove|rm|update)$ ]] && allowed=true ;;
      esac ;;
    mcp) [[ "${a[1]:-}" =~ ^(list|get|remove)$ ]] && allowed=true ;;
  esac
  $allowed || api_err forbidden "Command Center runs: doctor, update, --version, plugin …, plugin marketplace …, mcp list/get/remove."
  need claude
  out="$(cd "$HOME" && t 300 env -u ANTHROPIC_API_KEY claude "${a[@]}" </dev/null 2>&1)"
  rc=$?
  out="$(sed 's/\x1b\[[0-9;?]*[A-Za-z]//g' <<<"$out" | tail -c 65536)"
  api_ok "$(jq -cn --arg o "$out" --argjson rc "$rc" '{exit_code:$rc, output:$o}')"
}

api_main() {
  # fd 3 = the one JSON object; everything else goes to stderr.
  exec 3>&1 1>&2
  trap on_exit EXIT
  command -v jq >/dev/null 2>&1 ||
    { printf '{"ok":false,"error":{"code":"internal","message":"jq is not installed on the server"}}\n' >&3; EMITTED=1; exit 1; }
  unset ANTHROPIC_API_KEY GH_TOKEN GITHUB_TOKEN
  export GH_PROMPT_DISABLED=1 GH_NO_UPDATE_NOTIFIER=1 GIT_TERMINAL_PROMPT=0 AWS_PAGER=""

  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    login-claude-code | login-github | login-aws-keys | login-gitlab | login-docker | login-cloudflare | run | claude-cmd | login-token | install-cli) ;;  # these read stdin
    *) exec 0</dev/null ;;
  esac

  case "$cmd" in
    status)              [[ $# -eq 0 ]] || bad_args "status takes no arguments"; do_status ;;
    owners)              [[ $# -eq 0 ]] || bad_args "owners takes no arguments"; do_owners ;;
    repos)               [[ $# -eq 0 || ( $# -eq 1 && "$1" == --refresh ) ]] || bad_args "usage: repos [--refresh]"; do_repos "$@" ;;
    sessions)            [[ $# -eq 0 ]] || bad_args "sessions takes no arguments"; do_sessions ;;
    new)                 do_new "$@" ;;
    open)                do_open "$@" ;;
    clone-status)        do_clone_status "$@" ;;
    start)               do_start "$@" ;;
    stop)                do_stop "$@" ;;
    tail)                do_tail "$@" ;;
    login-claude-start)  do_login_claude_start ;;
    login-claude-code)   do_login_claude_code ;;
    login-claude-cancel) do_login_claude_cancel ;;
    login-github)        do_login_github ;;
    login-aws-keys)      do_login_aws_keys ;;
    login-aws-sso-start) do_login_aws_sso_start ;;
    login-gitlab)        do_login_gitlab ;;
    login-docker)        do_login_docker ;;
    login-cloudflare)    do_login_cloudflare ;;
    login-token)         do_login_token "$@" ;;
    self-update)         do_self_update "$@" ;;
    install-cli)         do_install_cli "$@" ;;
    run)                 do_run "$@" ;;
    claude-cmd)          do_claude_cmd "$@" ;;
    restart)             do_restart "$@" ;;
    keys)                do_keys "$@" ;;
    "")                  bad_args "missing subcommand" ;;
    *)                   bad_args "unknown subcommand '$cmd'" ;;
  esac
  api_err internal "no result"
}

# ======================================================================

main() {
  load_config
  case "${1:-}" in
    --api) shift; api_main "$@" ;;
    --clone-worker) shift; clone_worker "$@" ;;
    --version) echo "$SCRIPT_VERSION" ;;
    -h | --help) sed -n '2,10p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//' ;;
    "") menu_main ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
}

main "$@"
