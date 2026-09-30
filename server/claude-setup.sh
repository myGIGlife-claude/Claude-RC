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

# What the phone can rely on: 2 = extra services, 3 = self-update,
# 4 = install-cli, 5 = run, 6 = restart + claude-cmd, 7 = token services +
# more CLIs, 8 = keys, 9 = safe restart (resume + busy check), 10 = MXroute
# and tokens in Claude's settings env, 11 = custom API keys, 12 = Google Play,
# 13 = Android signing keys, 14 = YouTube, 15 = mcp/plugins/disconnect and
# session previews, 16 = in-app chat (PIN), 17 = chat uploads + chat log,
# 18 = MCP sign-in, 19 = repo delete/rename/visibility, 20 = doctor-start, 21 = chat-file, 22 = chat mode, 23 = chat model, 24 = chat questions.
# Bump when the app starts needing a new server feature.
SCRIPT_API=24
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
CUSTOM_NAMES="$LAUNCHER_CONFIG_DIR/custom-names"   # names added with set-secret
# A custom key's name: upper case, a credential-like suffix, never something
# that changes how programs run (PATH, LD_*, CLAUDE_*, …).
CUSTOM_NAME_RE='^[A-Z][A-Z0-9_]{0,55}_(KEY|TOKEN|SECRET|PASSWORD|USERNAME|USER|SERVER|HOST|URL|ID|EMAIL|REGION|PROJECT|ENDPOINT|ORG|ACCOUNT)$'
TOKEN_SERVICES="cloudflare vercel netlify fly railway supabase neon npm stripe huggingface b2 gcp firebase mxroute googleplay youtube"

# A question on Claude's screen: a confirm footer, a y/n, a numbered menu with the cursor on it, or the auto-mode opt-in.
WAIT_RE='Enter to confirm|\(y/n\)|^ *❯ [0-9]+\. |Auto mode lets Claude'
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
# Archived repos only with --with-archived (the phone's Archived filter);
# the menu keeps its original list.
list_repos_raw() {
  local owner out lists=() arch=(--no-archived)
  [[ "${1:-}" == --with-archived ]] && arch=()
  for owner in "$GH_USER" "${GH_ORGS[@]}"; do
    if out="$(t 30 gh repo list "$owner" --limit 200 "${arch[@]}" \
      --json nameWithOwner,name,pushedAt,visibility,isArchived 2>/dev/null </dev/null)"; then
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

# api_ok_file <file>: like api_ok, for answers too big for an argument.
api_ok_file() {
  jq -e . "$1" >/dev/null 2>&1 || { rm -f "$1"; api_err internal "The server couldn't build its answer."; }
  jq -c '{ok:true,data:.}' "$1" >&3
  rm -f "$1"
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
  ensure_env_hook   # keeps Claude's settings in step with the saved tokens
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
    --argjson keystores "$(ls "$KEYSTORE_DIR" 2>/dev/null | sed -n 's/\.jks$//p' | jq -Rsc 'split("\n") | map(select(. != ""))')" \
    --argjson custom "$(if [[ -f "$CUSTOM_NAMES" ]]; then jq -Rsc 'split("\n") | map(select(. != ""))' "$CUSTOM_NAMES"; else echo '[]'; fi)" \
    --argjson run "$([[ "$ALLOW_RUN" == 1 ]] && echo true || echo false)" \
    --argjson c "$claude_ok" --argjson g "$gh_ok" --arg gu "$gh_user" --argjson gm "$scopes" \
    --argjson a "$aws_ok" --argjson ai "$aws_json" --arg ap "${AWS_PROFILE_NAME:-default}" \
    --argjson sso "$(aws_sso_configured && echo true || echo false)" \
    --arg host "$(hostname -s 2>/dev/null || hostname)" '{
      claude:{logged_in:$c},
      github:{logged_in:$g, user:(if $gu=="" then null else $gu end), missing_scopes:$gm},
      aws:{logged_in:$a, identity:$ai, profile:$ap, sso_configured:$sso},
      hostname:$host, services:$services, custom:$custom, keystores:$keystores,
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
    raw="$(list_repos_raw --with-archived)" || api_err internal "Could not list your GitHub repos."
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
          local:$local, cloning:$cloning, archived:(.isArchived // false),
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
  # Each session's last lines, and whether it's waiting on a question or working.
  local out="[]" row name screen tail4 waiting busy
  while IFS= read -r row; do
    [[ -n "$row" ]] || continue
    name="$(jq -r .name <<<"$row")"
    screen="$(tmux capture-pane -p -J -t "=$name:" 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n 15)"
    # Drop Claude's input box and status bar: separators, the empty ❯ prompt, mode/shortcut hints.
    tail4="$(grep -v '^[[:space:]]*$' <<<"$screen" | grep -vE '^[─━╭╰│ ]+$|^ *❯|⏵⏵|shift\+tab|for shortcuts|← for agents|esc to interrupt|^ *⎿? *Tip:|/clear to save|Restart to update' |
      sed -e 's/[[:space:]]*$//' -e 's/^[[:space:]]\{8,\}//' | tail -n 3 | cut -c1-120)"
    waiting=false busy=false
    grep -qE "$WAIT_RE" <<<"$screen" && waiting=true
    grep -q "esc to interrupt" <<<"$screen" && busy=true
    out="$(jq -c --argjson r "$row" --arg p "$tail4" --argjson w "$waiting" --argjson b "$busy" \
      '. + [$r + {preview:$p, waiting:$w, busy:$b}]' <<<"$out")"
  done < <(jq -c '.[]' <<<"${s:-[]}")
  api_ok "$(jq -cn --argjson s "$out" --argjson now "$(date +%s)" '{now:$now, sessions:$s}')"
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
  grep -qE "$WAIT_RE" <<<"$(tail -n 15 <<<"$screen")" && waiting=true
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
KEYS_ALLOWED="1 2 3 4 5 6 7 8 9 Enter Escape Up Down Tab BTab Space y n"
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

# repo-edit delete|rename|visibility <owner/repo> [<new name> | private | public]
# Changes the repo on GitHub only: a folder on the server keeps its name.
do_repo_edit() {
  local what="${1:-}" repo="${2:-}" arg="${3:-}" err rc
  [[ "$repo" =~ $REPO_RE ]] || api_err invalid_name "Invalid repo name."
  case "$what" in
    delete) (($# == 2)) || bad_args "usage: repo-edit delete <owner/repo>" ;;
    rename) (($# == 3)) || bad_args "usage: repo-edit rename <owner/repo> <new name>"
      valid_project "$arg" || api_err invalid_name "Names may use letters, digits, '.', '_' and '-' (not first), up to 100." ;;
    visibility) (($# == 3)) && [[ "$arg" == private || "$arg" == public ]] || bad_args "usage: repo-edit visibility <owner/repo> private|public" ;;
    *) bad_args "usage: repo-edit delete|rename|visibility <owner/repo> [arg]" ;;
  esac
  require_github
  case "$what" in
    delete) err="$(t 60 gh repo delete "$repo" --yes 2>&1 </dev/null)" ;;
    rename) err="$(t 60 gh repo rename "$arg" -R "$repo" --yes 2>&1 </dev/null)" ;;
    visibility) err="$(t 60 gh repo edit "$repo" --visibility "$arg" --accept-visibility-change-consequences 2>&1 </dev/null)" ;;
  esac
  rc=$?
  if ((rc != 0)); then
    [[ "$what" == delete ]] && grep -qiE 'delete_repo|HTTP 403' <<<"$err" &&
      api_err missing_scopes "Deleting repos needs the delete_repo scope on your GitHub token." '{"missing":["delete_repo"]}'
    api_err internal "GitHub refused: $(tail -n 2 <<<"$err")"
  fi
  rm -f "$CACHE_DIR/repos.json"
  api_ok "$(jq -cn --arg w "$what" --arg r "$repo" --arg a "$arg" '{done:$w, repo:$r, value:(if $a=="" then null else $a end)}')"
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
    # Google Play Developer API: one service-account key, handed to the common
    # tools: a path for fastlane (SUPPLY_JSON_KEY) and others, the contents for
    # Gradle Play Publisher (ANDROID_PUBLISHER_CREDENTIALS).
    googleplay) SVC_VARS=(GOOGLE_PLAY_JSON_KEY SUPPLY_JSON_KEY ANDROID_PUBLISHER_CREDENTIALS) SVC_KEYFILE=googleplay-key.json ;;
    # Signed in with youtube-login-start / -poll (Google's device flow), not login-token.
    youtube) SVC_VARS=(YOUTUBE_REFRESH_TOKEN YOUTUBE_CLIENT_ID YOUTUBE_CLIENT_SECRET) SVC_CLI=youtube-upload ;;
    mxroute) SVC_VARS=(MXROUTE_API_KEY MXROUTE_SERVER MXROUTE_USERNAME)
      SVC_RES=('^[A-Za-z0-9]{16,64}$' '^[a-z0-9][a-z0-9.-]{2,252}$' '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') ;;
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
    mxroute) printf 'header = "X-API-Key: %s"\nheader = "X-Server: %s"\nheader = "X-Username: %s"\n' "$1" "$2" "$3" |
      curl -fsS --max-time 15 -K - https://api.mxroute.com/domains >/dev/null 2>&1 && echo "$3@$2" ;;
    googleplay) jq -e 'select(.type == "service_account" and .client_email and .private_key)' <<<"$1" >/dev/null &&
      sa_token_ok "$1" https://www.googleapis.com/auth/androidpublisher && jq -r '.client_email' <<<"$1" ;;
    gcp | firebase) jq -er 'select(.type == "service_account" and .project_id and .client_email) | .client_email' <<<"$1" ;;
  esac
}

# ensure_env_hook: make the saved tokens reach Claude sessions.
# Claude sessions don't read ~/.bashrc (tmux starts claude directly, and its
# shell snapshot keeps only functions, aliases and PATH). Claude Code's own
# settings do reach everything: the "env" block of ~/.claude/settings.json is
# set on the claude process itself, so commands Claude runs AND MCP servers
# (including ${VAR} in .mcp.json) see it. So the env file is mirrored there,
# the file is kept mode 600, and names we added before but no longer have are
# removed. The .bashrc line is kept for your own terminal. Applies when a
# session (re)starts.
ensure_env_hook() {
  [[ -f "$SERVICES_ENV" ]] || return 0
  grep -qF "$SERVICES_ENV_HOOK" "$HOME/.bashrc" 2>/dev/null || printf '\n%s\n' "$SERVICES_ENV_HOOK" >>"$HOME/.bashrc"
  local f="$HOME/.claude/settings.json" managed="$LAUNCHER_CONFIG_DIR/settings-env-names" tmp vars line old
  vars="$(while IFS= read -r line; do
      [[ "$line" =~ ^export\ ([A-Z_][A-Z0-9_]*)=\'(.*)\'$ ]] && jq -cn --arg k "${BASH_REMATCH[1]}" --arg v "${BASH_REMATCH[2]}" '{($k):$v}'
    done <"$SERVICES_ENV" | jq -sc 'add // {}')"
  mkdir -p "$HOME/.claude"
  [[ -s "$f" ]] || echo '{}' >"$f"
  jq -e . "$f" >/dev/null 2>&1 || return 0   # never overwrite a settings file we can't parse
  tmp="$(mktemp "$f.XXXXXX")" || return 0
  old='[]'
  [[ -f "$managed" ]] && old="$(jq -Rsc 'split("\n") | map(select(. != ""))' "$managed")"
  jq --argjson v "$vars" --argjson old "$old" '
    .env = (((.env // {}) | with_entries(select((.key as $k | $old | index($k)) | not))) + $v)' "$f" >"$tmp" &&
    { chmod 600 "$f"; cat "$tmp" >"$f"; jq -r 'keys[]' <<<"$vars" >"$managed"; }
  rm -f "$tmp"
}

# sa_token_ok <service-account JSON> <scope>: sign a JWT with the key and ask
# Google for an access token, which proves the key exists and isn't revoked.
# (It can't prove Play Console access; that needs a package name.)
sa_token_ok() {
  local d email now hdr claims sig tok
  command -v openssl >/dev/null 2>&1 || return 0   # can't check here: accept the well-formed key
  d="$(mktemp -d)" && chmod 700 "$d"
  jq -r '.private_key' <<<"$1" >"$d/k.pem"; chmod 600 "$d/k.pem"
  email="$(jq -r '.client_email' <<<"$1")"; now="$(date +%s)"
  b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
  hdr="$(printf '{"alg":"RS256","typ":"JWT"}' | b64)"
  claims="$(jq -cn --arg e "$email" --arg s "$2" --argjson n "$now" \
    '{iss:$e, scope:$s, aud:"https://oauth2.googleapis.com/token", iat:$n, exp:($n + 600)}' | b64)"
  sig="$(printf '%s.%s' "$hdr" "$claims" | openssl dgst -sha256 -sign "$d/k.pem" 2>/dev/null | b64)"
  rm -rf "$d"
  [[ -n "$sig" ]] || return 1
  tok="$(curl -fsS --max-time 15 https://oauth2.googleapis.com/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer -d "assertion=$hdr.$claims.$sig" 2>/dev/null |
    jq -r '.access_token // empty' 2>/dev/null)"
  [[ -n "$tok" ]]
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
  [[ $# -eq 1 && "$1" != youtube ]] && svc_def "$1" || bad_args "usage: login-token <service>"
  local id="$1" who v i vals=() key tmp
  if [[ -n "$SVC_KEYFILE" ]]; then
    key="$(head -c 20000)"
    exec 0</dev/null
    who="$(svc_verify "$id" "$key")" ||
      api_err invalid_name "That isn't a working Google service-account JSON key (Google didn't accept it, or it's not a service-account key)."
    mkdir -p "$LAUNCHER_CONFIG_DIR"
    tmp="$(mktemp "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE.XXXXXX")"
    chmod 600 "$tmp"
    printf '%s\n' "$key" >"$tmp"
    mv "$tmp" "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
    set_env "${SVC_VARS[0]}" "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
    [[ "$id" == gcp ]] && set_env CLOUDSDK_CORE_PROJECT "$(jq -r '.project_id' <<<"$key" | tr -cd 'a-z0-9-')"
    if [[ "$id" == googleplay ]]; then
      set_env SUPPLY_JSON_KEY "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
      # One line of JSON; a key never contains a single quote, but refuse it if it does.
      key="$(jq -c . <<<"$key")"
      [[ "$key" != *"'"* ]] && set_env ANDROID_PUBLISHER_CREDENTIALS "$key"
    fi
    unset key
  else
    for ((i = 0; i < ${#SVC_VARS[@]}; i++)); do
      v="$(read_secret_line)"
      v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"   # trim
      [[ "$v" =~ ${SVC_RES[$i]} ]] || api_err invalid_name "That ${SVC_VARS[$i]} doesn't look right."
      vals+=("$v")
    done
    exec 0</dev/null
    [[ "$id" == supabase && "${vals[0]}" != sbp_* ]] &&
      api_err invalid_name "That's a project API key. Supabase needs a personal access token (starts with sbp_): Account › Access Tokens."
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

custom_name_ok() { [[ "$1" =~ $CUSTOM_NAME_RE && "$1" != CLAUDE_* && "$1" != ANTHROPIC_* ]]; }

# set-secret <NAME>: stdin = the value. For APIs without a built-in service:
# saved like the others, so every Claude session and MCP server sees it.
do_set_secret() {
  [[ $# -eq 1 ]] && custom_name_ok "$1" || bad_args "usage: set-secret <NAME ending in _KEY, _TOKEN, _SECRET, …>"
  local v
  v="$(read_secret_line)"
  exec 0</dev/null
  v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"
  [[ -n "$v" && ${#v} -le 4000 && "$v" != *"'"* ]] || api_err invalid_name "The value is empty, too long, or contains a single quote."
  set_env "$1" "$v"
  unset v
  { grep -vx "$1" "$CUSTOM_NAMES" 2>/dev/null; echo "$1"; } | sort -u >"$CUSTOM_NAMES.tmp" && mv "$CUSTOM_NAMES.tmp" "$CUSTOM_NAMES"
  ensure_env_hook
  api_ok "$(jq -cn --arg n "$1" '{saved:$n}')"
}

KEYSTORE_DIR="$LAUNCHER_CONFIG_DIR/keystores"

find_keytool() {
  command -v keytool 2>/dev/null && return
  ls "$HOME"/.jdks/*/bin/keytool /usr/lib/jvm/*/bin/keytool 2>/dev/null | head -n 1
}

# login-keystore <NAME>: an Android signing (upload) key. stdin: alias, keystore
# password, key password, then the keystore file base64-encoded. Saved as
# keystores/<NAME>.jks (mode 600); every session gets <NAME>_KEYSTORE_FILE,
# <NAME>_KEYSTORE_PASSWORD, <NAME>_KEY_ALIAS and <NAME>_KEY_PASSWORD.
do_login_keystore() {
  [[ $# -eq 1 && "$1" =~ ^[A-Z][A-Z0-9_]{0,30}$ && "$1" != CLAUDE* && "$1" != ANTHROPIC* ]] ||
    bad_args "usage: login-keystore <NAME, e.g. GTG>"
  local n="$1" alias sp kp dir kt f out
  alias="$(read_secret_line)"; sp="$(read_secret_line)"; kp="$(read_secret_line)"
  dir="$(mktemp -d)" && chmod 700 "$dir"
  trap 'rm -rf "$dir"; on_exit' EXIT
  head -c 200000 | tr -d '[:space:]' | base64 -d >"$dir/ks" 2>/dev/null || api_err invalid_name "The keystore file didn't come through."
  exec 0</dev/null
  [[ -s "$dir/ks" ]] || api_err invalid_name "The keystore file is empty."
  [[ "$alias" =~ ^[A-Za-z0-9._-]{1,100}$ ]] || api_err invalid_name "That key alias doesn't look right."
  [[ -n "$sp" && "$sp" != *"'"* && "$kp" != *"'"* ]] ||
    api_err invalid_name "Passwords can't be empty or contain a single quote (')."
  [[ -n "$kp" ]] || kp="$sp"
  # The passwords must open the key: keytool if a JDK is here, else openssl (PKCS12 keystores).
  printf '%s' "$sp" >"$dir/sp"; printf '%s' "$kp" >"$dir/kp"; chmod 600 "$dir/sp" "$dir/kp"
  kt="$(find_keytool)"
  if [[ -n "$kt" ]]; then
    out="$("$kt" -list -keystore "$dir/ks" -storepass:file "$dir/sp" -alias "$alias" 2>&1)" ||
      api_err not_logged_in "keytool couldn't open it: $(tail -n 1 <<<"$out")"
  elif ! openssl pkcs12 -in "$dir/ks" -passin "file:$dir/sp" -noout 2>/dev/null; then
    api_err not_logged_in "The keystore password doesn't open this file (or it's an old JKS keystore and no JDK is installed to check it)."
  fi
  mkdir -p "$KEYSTORE_DIR" && chmod 700 "$KEYSTORE_DIR"
  f="$KEYSTORE_DIR/$n.jks"
  install -m 600 "$dir/ks" "$f.new" && mv -f "$f.new" "$f"
  set_env "${n}_KEYSTORE_FILE" "$f"
  set_env "${n}_KEYSTORE_PASSWORD" "$sp"
  set_env "${n}_KEY_ALIAS" "$alias"
  set_env "${n}_KEY_PASSWORD" "$kp"
  unset sp kp
  ensure_env_hook
  api_ok "$(jq -cn --arg n "$n" --arg a "$alias" '{saved:$n, alias:$a}')"
}

# remove-keystore <NAME>
do_remove_keystore() {
  [[ $# -eq 1 && "$1" =~ ^[A-Z][A-Z0-9_]{0,30}$ ]] || bad_args "usage: remove-keystore <NAME>"
  [[ -f "$KEYSTORE_DIR/$1.jks" ]] || api_err invalid_name "No signing key named $1."
  rm -f "$KEYSTORE_DIR/$1.jks"
  local v
  for v in KEYSTORE_FILE KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do set_env "${1}_$v" ""; done
  ensure_env_hook
  api_ok "$(jq -cn --arg n "$1" '{removed:$n}')"
}

# ---- YouTube: Google's device flow ("TVs and Limited Input devices" client).
# youtube-upload.upload isn't allowed in that flow, the broader youtube scope is.
YT_PENDING="$API_STATE_DIR/youtube-login.json"
YT_SCOPE="https://www.googleapis.com/auth/youtube"

# youtube-login-start: stdin = client ID, client secret. Returns the code to
# enter at the verification URL; the app then polls youtube-login-poll.
do_youtube_login_start() {
  [[ $# -eq 0 ]] || bad_args "youtube-login-start takes no arguments"
  local id secret resp
  id="$(read_secret_line)"; secret="$(read_secret_line)"
  exec 0</dev/null
  id="${id//[[:space:]]/}" secret="${secret//[[:space:]]/}"
  [[ "$id" =~ ^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$ ]] || api_err invalid_name "That doesn't look like a Google OAuth client ID (…apps.googleusercontent.com)."
  [[ "$secret" =~ ^[A-Za-z0-9_-]{10,100}$ ]] || api_err invalid_name "That client secret doesn't look right."
  need curl
  resp="$(curl -sS --max-time 20 https://oauth2.googleapis.com/device/code \
    --data-urlencode "client_id=$id" --data-urlencode "scope=$YT_SCOPE" 2>/dev/null)"
  jq -e '.device_code' <<<"$resp" >/dev/null 2>&1 ||
    api_err not_logged_in "Google didn't start the sign-in: $(jq -r '.error_description // .error // "no answer"' <<<"$resp" 2>/dev/null). The client must be type 'TVs and Limited Input devices'."
  ( umask 077; jq -c --arg i "$id" --arg s "$secret" '{client_id:$i, client_secret:$s, device_code, interval}' <<<"$resp" >"$YT_PENDING" )
  api_ok "$(jq -c '{url:(.verification_url // .verification_uri), code:.user_code, interval:(.interval // 5), expires_in}' <<<"$resp")"
}

# youtube-login-poll: {pending:true} until you approve, then saves the login.
do_youtube_login_poll() {
  [[ $# -eq 0 ]] || bad_args "youtube-login-poll takes no arguments"
  [[ -f "$YT_PENDING" ]] || api_err not_logged_in "No YouTube sign-in in progress. Start it again."
  local id secret dc resp err rt at chan
  id="$(jq -r .client_id "$YT_PENDING")"; secret="$(jq -r .client_secret "$YT_PENDING")"; dc="$(jq -r .device_code "$YT_PENDING")"
  resp="$(curl -sS --max-time 20 https://oauth2.googleapis.com/token \
    --data-urlencode "client_id=$id" --data-urlencode "client_secret=$secret" --data-urlencode "device_code=$dc" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:device_code" 2>/dev/null)"
  err="$(jq -r '.error // empty' <<<"$resp" 2>/dev/null)"
  case "$err" in
    authorization_pending | slow_down) api_ok '{"pending":true}' ;;
    "") ;;
    *) rm -f "$YT_PENDING"; api_err not_logged_in "Google says: $(jq -r '.error_description // .error' <<<"$resp"). Start the sign-in again." ;;
  esac
  rt="$(jq -r '.refresh_token // empty' <<<"$resp")"; at="$(jq -r '.access_token // empty' <<<"$resp")"
  [[ -n "$rt" && -n "$at" ]] || { rm -f "$YT_PENDING"; api_err not_logged_in "Google didn't return a lasting login. Start again."; }
  chan="$(curl_auth "Authorization: Bearer $at" "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true" |
    jq -r '.items[0].snippet.title // empty')"
  [[ -n "$chan" ]] || { rm -f "$YT_PENDING"; api_err not_logged_in "Signed in, but that Google account has no YouTube channel."; }
  set_env YOUTUBE_CLIENT_ID "$id"; set_env YOUTUBE_CLIENT_SECRET "$secret"; set_env YOUTUBE_REFRESH_TOKEN "$rt"
  rm -f "$YT_PENDING"
  local tmp; tmp="$(mktemp "$SERVICES_INFO.XXXXXX")"
  { jq -c . "$SERVICES_INFO" 2>/dev/null || echo '{}'; } | jq -c --arg w "$chan" '.youtube = $w' >"$tmp" && mv "$tmp" "$SERVICES_INFO"
  ensure_env_hook
  api_ok "$(jq -cn --arg c "$chan" '{logged_in:true, user:$c}')"
}

# remove-secret <NAME>: only names added with set-secret.
do_remove_secret() {
  [[ $# -eq 1 ]] && custom_name_ok "$1" || bad_args "usage: remove-secret <NAME>"
  grep -qx "$1" "$CUSTOM_NAMES" 2>/dev/null || api_err invalid_name "$1 isn't a custom key."
  set_env "$1" ""
  grep -vx "$1" "$CUSTOM_NAMES" >"$CUSTOM_NAMES.tmp"; mv "$CUSTOM_NAMES.tmp" "$CUSTOM_NAMES"
  ensure_env_hook
  api_ok "$(jq -cn --arg n "$1" '{removed:$n}')"
}

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

INSTALLABLE="glab docker supabase flyctl stripe railway neon b2 vercel netlify firebase hf gcloud bun"

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
    bun)  # JavaScript runtime some plugins' hooks need (e.g. claude-mem); x64 CPUs without AVX2 get the baseline build
      need unzip
      f="bun-linux-$($a64 && echo aarch64 || { grep -qw avx2 /proc/cpuinfo && echo x64 || echo x64-baseline; })"
      fetch "https://github.com/oven-sh/bun/releases/latest/download/$f.zip" "$dir/$f.zip" \
        "https://github.com/oven-sh/bun/releases/latest/download/SHASUMS256.txt"
      unzip -q "$dir/$f.zip" -d "$dir/x" 2>/dev/null && [[ -f "$dir/x/$f/bun" ]] || api_err internal "$f.zip isn't a valid archive."
      put_bin "$dir/x/$f/bun" bun; ln -sf bun "$HOME/.local/bin/bunx"
      ver="$("$HOME/.local/bin/bun" --version 2>/dev/null)" ;;
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

# ---- MCP servers --------------------------------------------------------------
# `claude mcp list` starts every server to check it (~10 s), so its output is
# cached and refreshed in the background at most every 5 minutes.
MCP_CACHE="$CACHE_DIR/mcp-list.txt"

mcp_refresh_now() {
  local d; d="$(mktemp -d)"
  (cd "$d" && t 120 claude mcp list </dev/null >"$MCP_CACHE.tmp" 2>&1) && mv -f "$MCP_CACHE.tmp" "$MCP_CACHE"
  rm -rf "$d" "$MCP_CACHE.tmp"
}

# The cached list as JSON: name, scope (user|project|plugin|claude.ai), kind,
# target (a URL, or just the program for stdio: arguments can hold secrets),
# health (connected|failed|needs_auth|unknown).
mcp_json() {
  local users
  users="$(jq -c '.mcpServers // {} | keys' "$HOME/.claude.json" 2>/dev/null || echo '[]')"
  { grep -E '^.+: .+ - (✔|✘|!)' "$MCP_CACHE" 2>/dev/null || true; } | jq -Rsc --argjson users "$users" '
    split("\n") | map(select(length > 0)) | map(
      (index(": ")) as $i | .[0:$i] as $name | .[$i+2:] as $rest |
      ($rest | split(" - ") | last) as $st | ($rest | split(" - ") | .[0:-1] | join(" - ")) as $target |
      {name:$name,
       scope:(if ($name | startswith("claude.ai ")) then "claude.ai"
              elif ($name | startswith("plugin:")) then "plugin"
              elif ($users | index($name)) then "user" else "project" end),
       plugin:(if ($name | startswith("plugin:")) then ($name | split(":")[1]) else null end),
       label:(if ($name | startswith("claude.ai ")) then $name[10:]
              elif ($name | startswith("plugin:")) then ($name | split(":") | last) else $name end),
       kind:(if ($target | test("^https?://")) then "http" else "stdio" end),
       target:(if ($target | test("^https?://")) then ($target | split(" ")[0])
               else ($target | split(" ")[0:2] | join(" ") | .[0:60]) end),
       health:(if ($st | startswith("✔")) then "connected" elif ($st | startswith("!")) then "needs_auth"
               elif ($st | startswith("✘")) then "failed" else "unknown" end),
       detail:($st | sub("^[✔✘!] *"; "") | .[0:160])})'
}

# mcp: the cached list (starts a background refresh when it's stale).
do_mcp() {
  [[ $# -eq 0 ]] || bad_args "mcp takes no arguments"
  local age=999999 refreshing=false
  [[ -f "$MCP_CACHE" ]] && age=$(($(date +%s) - $(stat -c %Y "$MCP_CACHE")))
  if ((age > 300)) && command -v claude >/dev/null 2>&1 && ! pgrep -f "claude-setup.sh --mcp-refresh" >/dev/null; then
    # Fully detached, holding neither the SSH channel (fd 3) nor the lock (fd 9).
    setsid "$SCRIPT_PATH" --mcp-refresh </dev/null >/dev/null 2>&1 3>&- 9>&- &
    refreshing=true
  fi
  api_ok "$(jq -cn --argjson s "$(mcp_json)" --argjson age "$age" --argjson r "$refreshing" \
    '{servers:$s, checked_seconds_ago:(if $age == 999999 then null else $age end), refreshing:$r}')"
}

# mcp-refresh: check every server now (~10 s), then the list.
do_mcp_refresh() {
  [[ $# -eq 0 ]] || bad_args "mcp-refresh takes no arguments"
  need claude
  mcp_refresh_now
  api_ok "$(jq -cn --argjson s "$(mcp_json)" '{servers:$s, checked_seconds_ago:0, refreshing:false}')"
}

# MCP sign-in, driven through `claude /mcp` in a hidden session: the phone
# opens the sign-in link, then sends back the localhost callback URL from its
# address bar (that page fails to load on a phone, but the URL is all Claude
# needs). Only user and plugin servers: /mcp here doesn't list project ones.
MCP_AUTH_SESSION="mcp-auth"
DOCTOR_SESSION="claude-doctor"

# doctor-start: a fresh session of its own running /doctor (Claude's full
# checkup, which can also fix things), so it doesn't land in a project's chat.
# The app opens it in the chat.
do_doctor_start() {
  [[ $# -eq 0 ]] || bad_args "doctor-start takes no arguments"
  need tmux
  command -v claude >/dev/null 2>&1 || api_err internal "'claude' is not installed on the server."
  local dir="$CACHE_DIR/doctor"
  mkdir -p "$dir"
  trust_folder "$dir"
  tmux kill-session -t "=$DOCTOR_SESSION" 2>/dev/null
  # Only this run's conversation, so the chat shows the new one.
  rm -f "$HOME/.claude/projects/${dir//[\/.]/-}/"*.jsonl
  tmux new-session -d -s "$DOCTOR_SESSION" -c "$dir" "env -u ANTHROPIC_API_KEY claude /doctor; exec bash" ||
    api_err internal "tmux could not start the checkup."
  api_ok "$(jq -cn --arg s "$DOCTOR_SESSION" '{session:$s}')"
}
mcp_auth_screen() { tmux capture-pane -p -t "=$MCP_AUTH_SESSION:" 2>/dev/null; }
mcp_auth_key() { tmux send-keys -t "=$MCP_AUTH_SESSION:" "$1"; sleep 0.4; }

# mcp-auth-start (server name on stdin): the sign-in URL.
do_mcp_auth_start() {
  local name i text sel url="" moves=0
  name="$(read_secret_line)"
  exec 0</dev/null
  [[ "$name" =~ ^[A-Za-z0-9_.:@-]{1,100}$ ]] || api_err invalid_name "That isn't an MCP server name."
  need tmux
  command -v claude >/dev/null 2>&1 || api_err internal "'claude' is not installed on the server."
  mkdir -p "$CACHE_DIR/mcp-auth"
  printf '%s' "$name" >"$CACHE_DIR/mcp-auth/server"
  (cd "$CACHE_DIR/mcp-auth" && start_login_session "$MCP_AUTH_SESSION" "$(command -v claude)" /mcp) ||
    api_err internal "Could not start Claude."
  for ((i = 0; i < 120; i++)); do
    sleep 0.5
    text="$(mcp_auth_screen)"
    url="$(grep -A1 'copy this URL manually' <<<"$text" | grep -oE 'https://[^[:space:]]+' | head -n 1)"
    [[ -n "$url" ]] && break
    sel="$(grep -m1 '❯' <<<"$text")"
    if grep -q 'Yes, I trust this folder' <<<"$text"; then
      [[ "$sel" == *"Yes, I trust"* ]] && mcp_auth_key Enter || mcp_auth_key Down
    elif grep -qE '[0-9]\. (Re-?a|A)uthenticate' <<<"$text"; then
      [[ "$sel" == *uthenticate* ]] && mcp_auth_key Enter || mcp_auth_key Down
    elif grep -q 'Manage MCP servers' <<<"$text"; then
      ((moves++ < 40)) || break
      [[ " $sel " == *" $name "* ]] && mcp_auth_key Enter || mcp_auth_key Down
    fi
  done
  # The provider can forget the app registration Claude saved for this server
  # ("Unrecognized client_id"): drop it so Claude registers again, and retry once.
  local probe=""
  [[ -n "$url" && -z "${MCP_AUTH_RETRY:-}" ]] && probe="$(t 15 curl -s --max-time 10 -w '\n%{http_code}' "$url" 2>/dev/null)"
  if [[ "${probe##*$'\n'}" =~ ^4[0-9][0-9]$ ]] && grep -qiE 'client_id|invalid_client|unknown client' <<<"$probe"; then
    end_login "$MCP_AUTH_SESSION"
    local cred="$HOME/.claude/.credentials.json" tmp
    tmp="$(mktemp "$cred.XXXXXX")" &&
      jq --arg n "$name" '.mcpOAuth |= with_entries(select(.value.serverName != $n))' "$cred" >"$tmp" 2>/dev/null &&
      chmod 600 "$tmp" && mv -f "$tmp" "$cred" || rm -f "$tmp"
    printf '%s\n' "$name" | MCP_AUTH_RETRY=1 "$SCRIPT_PATH" --api mcp-auth-start >&3
    EMITTED=1; exit 0
  fi
  if [[ -z "$url" ]]; then
    end_login "$MCP_AUTH_SESSION"
    api_err internal "Claude didn't offer a sign-in for $name." "$(jq -cn --arg t "$(grep -v '^\s*$' <<<"$text" | tail -n 12)" '{pane:$t}')"
  fi
  api_ok "$(jq -cn --arg u "$url" '{url:$u}')"
}

# mcp-auth-finish (callback URL on stdin): the MCP list, checked again.
# Claude can finish the sign-in by itself and leave the paste box, so the URL
# is only typed while that box is on screen (never into Claude's chat), and
# the answer comes from a fresh `claude mcp list`.
do_mcp_auth_finish() {
  local cb i text msg="" name
  cb="$(read_secret_line)"
  exec 0</dev/null
  cb="${cb//[[:space:]]/}"
  [[ "$cb" =~ ^http://(localhost|127\.0\.0\.1):[0-9]{1,5}/ && ${#cb} -le 4000 ]] ||
    api_err invalid_name "Paste the whole address from the browser: it starts with http://localhost:"
  name="$(cat "$CACHE_DIR/mcp-auth/server" 2>/dev/null)"
  need claude
  if tmux has-session -t "=$MCP_AUTH_SESSION" 2>/dev/null && grep -q 'paste the URL' <<<"$(mcp_auth_screen)"; then
    tmux send-keys -t "=$MCP_AUTH_SESSION:" -l -- "$cb"
    tmux send-keys -t "=$MCP_AUTH_SESSION:" Enter
    for ((i = 0; i < 45; i++)); do
      sleep 1
      text="$(mcp_auth_screen)"
      # A URL from another sign-in: this one is still waiting for the right one.
      if ((i >= 3)) && msg="$(grep -m1 "isn't this sign-in" <<<"$text")"; then
        api_err invalid_name "$(sed 's/^ *//; s/ *$//' <<<"$msg")"
      fi
      grep -q 'paste the URL' <<<"$text" && continue
      # Back on the server's page: any error shows above its menu.
      grep -q 'Config location' <<<"$text" &&
        msg="$(awk '/Config location/{f=1; next} /❯/{f=0} f' <<<"$text" | sed 's/^ *//; s/ *$//' | grep -v '^$' | head -n 2)"
      break
    done
  fi
  end_login "$MCP_AUTH_SESSION"
  mcp_refresh_now
  local servers
  servers="$(mcp_json)"
  if [[ -n "$name" ]] && jq -e --arg n "$name" 'any(.[]; .name == $n and .health == "connected")' >/dev/null <<<"$servers"; then
    api_ok "$(jq -cn --argjson s "$servers" '{servers:$s, checked_seconds_ago:0, refreshing:false}')"
  fi
  api_err internal "Sign-in didn't complete${msg:+: $msg}. Start it again."
}

do_mcp_auth_cancel() {
  end_login "$MCP_AUTH_SESSION"
  api_ok '{"cancelled":true}'
}

# plugins: installed + available (from your marketplaces) + marketplaces + Claude's version.
do_plugins() {
  [[ $# -eq 0 ]] || bad_args "plugins takes no arguments"
  need claude
  local d pl mk ver
  d="$(mktemp -d)"
  pl="$(cd "$d" && t 60 claude plugin list --json --available </dev/null 2>/dev/null)"
  mk="$(cd "$d" && t 30 claude plugin marketplace list --json </dev/null 2>/dev/null)"
  ver="$(t 10 claude --version 2>/dev/null </dev/null | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+' | head -n 1)"
  rm -rf "$d"
  # The plugin catalog is large: pass it to jq through files, not arguments.
  printf '%s' "$pl" >"$d.pl"; printf '%s' "$mk" >"$d.mk"
  jq -e . "$d.pl" >/dev/null 2>&1 || { rm -f "$d.pl" "$d.mk"; api_err internal "claude plugin list gave no answer."; }
  jq -e . "$d.mk" >/dev/null 2>&1 || echo '[]' >"$d.mk"
  local res
  res="$(jq -cn --slurpfile pp "$d.pl" --slurpfile mm "$d.mk" --arg v "$ver" '$pp[0] as $p | $mm[0] as $m |
    ($p.installed // []) as $inst | ($inst | map(.id)) as $ids |
    {claude_version:(if $v == "" then null else $v end),
     installed:($inst | map({id, name:(.id | split("@")[0]), marketplace:(.id | split("@")[1:] | join("@")),
       version:(.version // ""), enabled:(.enabled // false), scope:(.scope // "user")})),
     available:(($p.available // []) | map({id:.pluginId, name, marketplace:.marketplaceName,
       description:((.description // "") | .[0:200]), installs:(.installCount // 0),
       installed:(.pluginId as $x | $ids | index($x) != null)})),
     marketplaces:($m | map({name, source:(.repo // .url // .source // "")}))}')"
  rm -f "$d.pl" "$d.mk"
  printf '%s' "$res" >"$d.res"
  api_ok_file "$d.res"
}

# disconnect <service>: forget a token service's credentials (and its key file).
do_disconnect() {
  [[ $# -eq 1 && " $TOKEN_SERVICES " == *" $1 "* ]] && svc_def "$1" || bad_args "usage: disconnect <service>"
  local v tmp
  for v in "${SVC_VARS[@]}"; do set_env "$v" ""; done
  [[ "$1" == gcp ]] && set_env CLOUDSDK_CORE_PROJECT ""
  [[ "$1" == googleplay ]] && { set_env SUPPLY_JSON_KEY ""; set_env ANDROID_PUBLISHER_CREDENTIALS ""; }
  [[ -n "$SVC_KEYFILE" ]] && rm -f "$LAUNCHER_CONFIG_DIR/$SVC_KEYFILE"
  if [[ -f "$SERVICES_INFO" ]]; then
    tmp="$(mktemp "$SERVICES_INFO.XXXXXX")" && jq -c --arg id "$1" 'del(.[$id])' "$SERVICES_INFO" >"$tmp" && mv "$tmp" "$SERVICES_INFO"
  fi
  ensure_env_hook
  api_ok "$(jq -cn --arg id "$1" '{disconnected:$id}')"
}

# ---- In-app chat ----------------------------------------------------------------
# Chat needs a PIN on top of the phone's key. It lives only in this file on the
# server (mode 600), so you can read it here if you forget it; no action ever
# returns it. 5 wrong PINs lock chat for 30 minutes (or until you delete
# chat-locked on the server).
CHAT_PIN_FILE="$LAUNCHER_CONFIG_DIR/chat-pin"
CHAT_LOCK="$LAUNCHER_CONFIG_DIR/chat-locked"
CHAT_FAILS="$API_STATE_DIR/chat-pin-fails"
CHAT_LOG="$API_STATE_DIR/chat.log"

chat_pin() { sed -n 's/^PIN=\([0-9]\{6,12\}\)$/\1/p' "$CHAT_PIN_FILE" 2>/dev/null | head -n 1; }

chat_locked_until() {
  local t
  [[ -f "$CHAT_LOCK" ]] || return 1
  t=$(($(stat -c %Y "$CHAT_LOCK") + 1800))
  if (($(date +%s) >= t)); then rm -f "$CHAT_LOCK"; return 1; fi
  echo "$t"
}

# check_pin <pin>: returns, or ends the call with an error (counting failures).
check_pin() {
  local want n until
  want="$(chat_pin)"
  [[ -n "$want" ]] || api_err pin_not_set "Set a chat PIN in the app first."
  if until="$(chat_locked_until)"; then
    api_err chat_locked "Too many wrong PINs. Chat is locked until $(date -d "@$until" +%H:%M), or delete ~/.config/claude-launcher/chat-locked on the server."
  fi
  if [[ "$1" == "$want" ]]; then rm -f "$CHAT_FAILS"; return 0; fi
  n=$(($(cat "$CHAT_FAILS" 2>/dev/null || echo 0) + 1))
  echo "$n" >"$CHAT_FAILS"
  if ((n >= 5)); then
    rm -f "$CHAT_FAILS"; touch "$CHAT_LOCK"
    api_err chat_locked "Too many wrong PINs. Chat is locked for 30 minutes (or delete ~/.config/claude-launcher/chat-locked on the server)."
  fi
  api_err wrong_pin "Wrong PIN ($((5 - n)) tries left). Forgot it? It's in ~/.config/claude-launcher/chat-pin on the server."
}

chat_log() { printf '%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" >>"$CHAT_LOG" 2>/dev/null; }

# chat-pin-status: whether a PIN is set and whether chat is locked.
do_chat_pin_status() {
  [[ $# -eq 0 ]] || bad_args "chat-pin-status takes no arguments"
  local set=false until
  [[ -n "$(chat_pin)" ]] && set=true
  until="$(chat_locked_until)" || until=""
  api_ok "$(jq -cn --argjson s "$set" --arg u "$until" '{set:$s, locked_until:(if $u == "" then null else ($u|tonumber) end)}')"
}

# chat-pin-set: stdin = new PIN, then the current PIN when one is set.
do_chat_pin_set() {
  [[ $# -eq 0 ]] || bad_args "chat-pin-set takes no arguments"
  local new old tmp
  new="$(read_secret_line)"; old="$(read_secret_line)"
  exec 0</dev/null
  [[ "$new" =~ ^[0-9]{6,12}$ ]] || api_err invalid_name "The PIN must be 6 to 12 digits."
  [[ -z "$(chat_pin)" ]] || check_pin "$old"
  mkdir -p "$LAUNCHER_CONFIG_DIR"
  tmp="$(mktemp "$CHAT_PIN_FILE.XXXXXX")"; chmod 600 "$tmp"
  printf '# cLaudeRC chat PIN. The app never shows it; this file is how you get it back.\n# Delete this file to remove the PIN (then set a new one in the app).\nPIN=%s\n' "$new" >"$tmp"
  mv "$tmp" "$CHAT_PIN_FILE"
  chat_log - pin-set
  api_ok '{"set":true}'
}

# The session's live conversation file (newest transcript in its project folder).
chat_transcript() {
  local sess="$1" dir
  dir="$(awk -F'\t' -v s="$sess" '$1 == s {print $2; exit}' "$AUTOSTART_LIST" 2>/dev/null)"
  [[ -n "$dir" ]] || dir="$(tmux display-message -p -t "=$sess:" '#{pane_current_path}' 2>/dev/null)"
  ls -t "$HOME/.claude/projects/${dir//[\/.]/-}/"*.jsonl 2>/dev/null | head -n 1
}

# chat_session <arg>: validated, running session name (or the call ends).
chat_session() {
  valid_project "$1" || api_err invalid_name "Invalid project name."
  local s; s="$(resolve_session "$1")"
  tmux has-session -t "=$s" 2>/dev/null || api_err invalid_name "No running session named '$1'."
  echo "$s"
}

# chat-open <session>: stdin = PIN. Just checks it (the app's gate).
do_chat_open() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-open <session>"
  local pin sess; pin="$(read_secret_line)"; exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  chat_log "$sess" open
  api_ok "$(jq -cn --arg s "$sess" '{session:$s}')"
}

# chat-history <session>: stdin = PIN. The last messages of the conversation:
# your text, Claude's text, and one-line summaries of its tool calls; plus
# whether it's working or waiting on a question (with that screen).
do_chat_history() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-history <session>"
  local pin sess f screen waiting=false busy=false tmp mode model= ask=null
  pin="$(read_secret_line)"; exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  f="$(chat_transcript "$sess")"
  screen="$(tmux capture-pane -p -J -t "=$sess:" 2>/dev/null | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | tail -n 15)"
  # The permission mode Claude's status line shows (none shown = default).
  mode="$(grep -oE '(auto mode|plan mode|accept edits|bypass permissions) on' <<<"$screen" | tail -n 1)"
  case "$mode" in "auto mode"*) mode=auto ;; "plan mode"*) mode=plan ;; "accept edits"*) mode=edits ;; "bypass"*) mode=bypass ;; *) mode=default ;; esac
  grep -qE "$WAIT_RE" <<<"$screen" && waiting=true
  grep -q "esc to interrupt" <<<"$screen" && busy=true
  # Claude's multiple-choice question (AskUserQuestion) still waiting for an answer, as data for the app.
  if $waiting && [[ -n "$f" ]]; then
    ask="$(tail -n 400 "$f" 2>/dev/null | jq -c -s '
      [.[] | select(.type == "assistant" or .type == "user") | .message.content | if type == "array" then .[] else empty end] as $c
      | [$c[] | select(.type == "tool_result") | .tool_use_id] as $done
      | [$c[] | select(.type == "tool_use" and .name == "AskUserQuestion" and ((.id as $i | $done | index($i)) | not))] | last
      | (.input.questions // null)
      | if . == null then null else map({question:(.question // "" | .[0:500]), header:(.header // "" | .[0:30]), multiSelect:(.multiSelect // false),
          options:((.options // [])[0:8] | map({label:(.label // "" | .[0:120]), description:(.description // "" | .[0:300])}))}) end' 2>/dev/null)"
    [[ -n "$ask" ]] || ask=null
  fi
  # The model of Claude's latest reply.
  [[ -n "$f" ]] && model="$(tail -n 300 "$f" 2>/dev/null | jq -r 'select(.type == "assistant") | .message.model // empty' 2>/dev/null | grep -v '^<' | tail -n 1)"
  tmp="$(mktemp)"
  { [[ -n "$f" ]] && tail -n 1500 "$f"; } 2>/dev/null | jq -c '
    select(.type == "user" or .type == "assistant" or (.type == "attachment" and .attachment.type == "queued_command")) | . as $l |
    if .type == "attachment" then
      # A message you sent while Claude was working.
      (.attachment.prompt // "") | tostring | select(length > 0 and (startswith("<") | not))
      | {id:$l.uuid, role:"user", text:.[0:8000], ts:$l.timestamp}
    elif .type == "user" then
      (.message.content | if type == "string" then [.] else [.[]? | select(.type == "text") | .text] end)[]
      | select(length > 0 and (startswith("<") | not))
      | {id:$l.uuid, role:"user", text:.[0:8000], ts:$l.timestamp}
    else
      (.message.content // [])[] |
      if .type == "text" then {id:$l.uuid, role:"assistant", text:(.text[0:12000]), ts:$l.timestamp}
      elif .type == "tool_use" and .name == "SendUserFile" then
        # A file Claude sent to the Claude app: shown as a card, fetched with chat-file.
        {id:($l.uuid + "-" + (.id // "")), role:"file", ts:$l.timestamp,
         text:((.input.caption // "") | tostring | .[0:2000]), files:((.input.files // []) | map(tostring) | .[0:10])}
      elif .type == "tool_use" then
        {id:($l.uuid + "-" + (.id // "")), role:"tool", ts:$l.timestamp,
         text:(.name + ": " + ((.input.description // .input.command // .input.file_path // .input.path // .input.pattern // .input.url // .input.query // "") | tostring | .[0:160]))}
      else empty end
    end' 2>/dev/null | jq -sc '
      # The last 60 of your messages and Claude replies, with the tool steps between them.
      ([to_entries[] | select(.value.role != "tool") | .key] | .[-60] // 0) as $from | .[$from:]' >"$tmp"
  jq -e . "$tmp" >/dev/null 2>&1 || echo '[]' >"$tmp"
  jq -c --arg s "$sess" --argjson w "$waiting" --argjson b "$busy" --arg sc "$screen" --arg mo "$mode" --arg md "$model" --argjson ak "$ask" \
    '{session:$s, messages:., waiting:$w, busy:$b, mode:$mo, model:$md, ask:$ak, screen:(if $w then $sc else null end)}' "$tmp" >"$tmp.out"
  rm -f "$tmp"
  api_ok_file "$tmp.out"
}

# chat-send <session>: stdin = PIN, then the message (multi-line is fine: it
# goes in as a bracketed paste, then Enter).
do_chat_send() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-send <session>"
  local pin sess msg
  pin="$(read_secret_line)"
  msg="$(head -c 20000)"
  exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  [[ -n "${msg//[[:space:]]/}" ]] || bad_args "empty message"
  printf '%s' "$msg" | tmux load-buffer -b clauderc-chat - || api_err internal "Couldn't hand the message to tmux."
  tmux paste-buffer -p -d -b clauderc-chat -t "=$sess:"
  sleep 0.3
  tmux send-keys -t "=$sess:" Enter
  unset msg
  chat_log "$sess" send
  api_ok "$(jq -cn --arg s "$sess" '{sent:true, session:$s}')"
}

# chat-interrupt <session>: stdin = PIN. Stops what Claude is doing (Esc).
do_chat_interrupt() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-interrupt <session>"
  local pin sess; pin="$(read_secret_line)"; exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  tmux send-keys -t "=$sess:" Escape
  chat_log "$sess" interrupt
  api_ok '{"interrupted":true}'
}

# upload <session>: stdin = PIN, file name, then the file base64-encoded (up to
# ~15 MB). Saved as <project>/uploads/<name> so Claude can open it; returns
# that path.
do_upload() {
  [[ $# -eq 1 ]] || bad_args "usage: upload <session>"
  local pin name sess dir d tmp size
  pin="$(read_secret_line)"; name="$(read_secret_line)"
  tmp="$(mktemp)"
  head -c 21000000 | tr -d '[:space:]' | base64 -d >"$tmp" 2>/dev/null || { rm -f "$tmp"; api_err invalid_name "The file didn't come through."; }
  exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  # A plain file name: no folders, no leading dot or dash.
  name="$(basename -- "$name")"
  name="$(printf '%s' "$name" | tr -c 'A-Za-z0-9._-' '_' | sed 's/^[.-]*//' | cut -c1-100)"
  [[ -n "$name" ]] || name="upload-$(date +%s)"
  size=$(stat -c %s "$tmp")
  ((size > 0 && size <= 15728640)) || { rm -f "$tmp"; api_err invalid_name "The file is empty or bigger than 15 MB."; }
  dir="$(awk -F'\t' -v s="$sess" '$1 == s {print $2; exit}' "$AUTOSTART_LIST" 2>/dev/null)"
  [[ -n "$dir" ]] || dir="$(tmux display-message -p -t "=$sess:" '#{pane_current_path}' 2>/dev/null)"
  [[ -d "$dir" ]] || { rm -f "$tmp"; api_err internal "Couldn't find the session's folder."; }
  d="$dir/uploads"; mkdir -p "$d"
  [[ -e "$d/$name" ]] && name="$(date +%H%M%S)-$name"
  install -m 644 "$tmp" "$d/$name"; rm -f "$tmp"
  chat_log "$sess" upload
  api_ok "$(jq -cn --arg p "uploads/$name" --argjson n "$size" '{path:$p, bytes:$n}')"
}

# chat-file <session>: stdin = PIN, then a file path. A file Claude sent (or
# made) in this session: only from the project's folder or Claude's temp folder
# for it, up to 10 MB, base64 in `data`.
do_chat_file() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-file <session>"
  local pin path sess dir real size
  pin="$(read_secret_line)"; path="$(read_secret_line)"; exec 0</dev/null
  sess="$(chat_session "$1")"
  check_pin "$pin"
  dir="$(awk -F'\t' -v s="$sess" '$1 == s {print $2; exit}' "$AUTOSTART_LIST" 2>/dev/null)"
  [[ -n "$dir" ]] || dir="$(tmux display-message -p -t "=$sess:" '#{pane_current_path}' 2>/dev/null)"
  [[ -d "$dir" ]] || api_err internal "Couldn't find the session's folder."
  real="$(realpath -e -- "$path" 2>/dev/null)" && [[ -f "$real" ]] || api_err invalid_name "That file isn't on the server any more."
  case "$real" in
    "$(realpath "$dir")"/* | "/tmp/claude-$(id -u)/${dir//[\/.]/-}"/*) ;;
    *) api_err forbidden "That file is outside this project." ;;
  esac
  size=$(stat -c %s "$real")
  ((size <= 10485760)) || api_err invalid_name "That file is bigger than 10 MB."
  chat_log "$sess" file
  api_ok "$(jq -cn --arg n "$(basename -- "$real")" --argjson b "$size" --rawfile d <(base64 -w0 -- "$real") '{name:$n, bytes:$b, data:$d}')"
}

# chat-log: the last chat opens/sends/uploads (no contents), newest first.
do_chat_log() {
  [[ $# -eq 0 ]] || bad_args "chat-log takes no arguments"
  api_ok "$( { tail -n 100 "$CHAT_LOG" 2>/dev/null || true; } | jq -Rsc '
    split("\n") | map(select(length > 0) | split("\t") | {ts:.[0], session:.[1], action:.[2]}) | reverse')"
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
    login-claude-code | login-github | login-aws-keys | login-gitlab | login-docker | run | claude-cmd | login-token | install-cli | set-secret | login-keystore | youtube-login-start | \
      chat-pin-set | chat-open | chat-history | chat-send | chat-interrupt | chat-file | upload | mcp-auth-start | mcp-auth-finish) ;;  # these read stdin
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
    login-token)         do_login_token "$@" ;;
    self-update)         do_self_update "$@" ;;
    install-cli)         do_install_cli "$@" ;;
    run)                 do_run "$@" ;;
    claude-cmd)          do_claude_cmd "$@" ;;
    restart)             do_restart "$@" ;;
    keys)                do_keys "$@" ;;
    set-secret)          do_set_secret "$@" ;;
    remove-secret)       do_remove_secret "$@" ;;
    login-keystore)      do_login_keystore "$@" ;;
    youtube-login-start) do_youtube_login_start "$@" ;;
    youtube-login-poll)  do_youtube_login_poll "$@" ;;
    repo-edit)           do_repo_edit "$@" ;;
    doctor-start)        do_doctor_start "$@" ;;
    mcp)                 do_mcp "$@" ;;
    mcp-refresh)         do_mcp_refresh "$@" ;;
    mcp-auth-start)      [[ $# -eq 0 ]] || bad_args "mcp-auth-start reads the name on stdin"; do_mcp_auth_start ;;
    mcp-auth-finish)     [[ $# -eq 0 ]] || bad_args "mcp-auth-finish reads the URL on stdin"; do_mcp_auth_finish ;;
    mcp-auth-cancel)     do_mcp_auth_cancel ;;
    plugins)             do_plugins "$@" ;;
    disconnect)          do_disconnect "$@" ;;
    chat-pin-status)     do_chat_pin_status "$@" ;;
    chat-pin-set)        do_chat_pin_set "$@" ;;
    chat-open)           do_chat_open "$@" ;;
    chat-history)        do_chat_history "$@" ;;
    chat-send)           do_chat_send "$@" ;;
    chat-interrupt)      do_chat_interrupt "$@" ;;
    upload)              do_upload "$@" ;;
    chat-file)           do_chat_file "$@" ;;
    chat-log)            do_chat_log "$@" ;;
    remove-keystore)     do_remove_keystore "$@" ;;
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
    --mcp-refresh) mcp_refresh_now ;;
    --version) echo "$SCRIPT_API" ;;
    -h | --help) sed -n '2,10p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//' ;;
    "") menu_main ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
}

main "$@"
