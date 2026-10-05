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
# 18 = MCP sign-in, 19 = repo delete/rename/visibility, 20 = doctor-start, 21 = chat-file, 22 = chat mode, 23 = chat model, 24 = chat questions, 25 = team, 26 = cluster (accounts + usage), 27 = per-chat workers, 32 = cluster-config (per-chat parallel tasks, hand back to main near the 5 h limit), 30 = push (push-config/-register/-session/-test, sessions.push_done), 33 = skills-update, 34 = migrate (migrate-plan/-keygen/-authorize/-send/-passphrase/-status/-restore), 35 = migrate verify/user/signout/reboot (migrate-sudo-check/-create-user/-verify/-signout-old/-reboot), 36 = migrate-clone + a login key and apt tools in migrate-create-user, 37 = worker kinds (worker-add <name> [claude|codex|gemini], kind in worker-list/cluster).
# Bump when the app starts needing a new server feature.
SCRIPT_API=37
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
CUSTOM_NAME_RE='^[A-Z][A-Z0-9_]{0,55}_(KEY|TOKEN|SECRET|PASSWORD|USERNAME|USER|SERVER|HOST|URL|ID|EMAIL|REGION|PROJECT|ENDPOINT|ORG|ACCOUNT|AUTHKEY|APIKEY)$'
TOKEN_SERVICES="cloudflare vercel netlify fly railway supabase neon npm stripe huggingface b2 gcp firebase mxroute googleplay youtube"

# A question on Claude's screen: a confirm footer, a y/n, a numbered menu with the cursor on it, or the auto-mode opt-in.
WAIT_RE='Enter to confirm|\(y/n\)|^ *❯ [0-9]+\. |Auto mode lets Claude'
CLAUDE_LOGIN_SESSION="claude-login"
AWS_LOGIN_SESSION="aws-sso-login"
ATTACH_DIR="$LAUNCHER_CONFIG_DIR/attach"     # which workers each project's chat may hand work to (read by clauderc-team)
PUSH_DIR="$LAUNCHER_CONFIG_DIR/push-done"       # a file per session whose "finished" alerts are on (read by claude-push)
PUSH_TOKENS="$LAUNCHER_CONFIG_DIR/push-tokens"  # one phone (FCM token) per line
PUSH_CONFIG="$LAUNCHER_CONFIG_DIR/push.json"    # public Firebase ids; fcm-key.json next to it is the service-account key
WORKERS_DIR="$LAUNCHER_CONFIG_DIR/workers"   # Team: one folder per extra Claude account
WORKER_RE='^[A-Za-z][A-Za-z0-9_-]{0,29}$'
WORKER_MODES=" acceptEdits plan bypassPermissions "

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

# The folder Claude keeps a project's conversations in: every character that isn't a letter or digit becomes "-".
claude_proj_slug() { printf '%s' "${1//[^A-Za-z0-9]/-}"; }

# A session's project folder: claude-autostart's list first, else where the pane is.
session_dir() {
  local d
  d="$(awk -F'\t' -v s="$1" '$1 == s {print $2; exit}' "$AUTOSTART_LIST" 2>/dev/null)"
  [[ -n "$d" ]] || d="$(tmux display-message -p -t "=$1:" '#{pane_current_path}' 2>/dev/null)"
  printf '%s' "$d"
}

# A credentials file that has a token (a logged-out Claude leaves `{"claudeAiOauth":{}}` behind, which must not count as signed in).
creds_ok() { jq -e '(.claudeAiOauth.accessToken // empty) | strings | length > 0' "$1" >/dev/null 2>&1; }

# Workers can be other CLIs: kind is claude (default), codex (ChatGPT) or gemini. Each is signed in under its own home folder.
WORKER_KINDS=" claude codex gemini "
worker_kind() { jq -r '.kind // "claude"' "$1/meta.json" 2>/dev/null || echo claude; }
# worker_signed <worker dir>: its login file has something in it.
worker_signed() {
  case "$(worker_kind "$1")" in
    codex) jq -e 'length > 0' "$1/home/auth.json" >/dev/null 2>&1 ;;
    gemini) jq -e 'length > 0' "$1/home/.gemini/oauth_creds.json" >/dev/null 2>&1 ;;
    *) creds_ok "$1/home/.credentials.json" ;;
  esac
}

claude_logged_in() { [[ -s "${LOGIN_CONFIG_DIR:-$HOME/.claude}/.credentials.json" ]]; }

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
  # Through stdin: an argument tops out at 128 KB, which a screenshot or a long repo list exceeds.
  printf '%s' "$1" | jq -c '{ok:true,data:.}' >&3
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
  ensure_push_hook
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
    --argjson apple "$([[ -f "$APPLE_DIR/AuthKey.p8" ]] && echo true || echo false)" \
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
      hostname:$host, services:$services, custom:$custom, keystores:$keystores, apple:$apple,
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

# The running Claude sessions as a JSON array (what `sessions` and `migrate-plan` report).
sessions_list_json() {
  local s
  # Same detection as claude-autostart: any session whose pane started claude.
  s="$({ tmux list-panes -a -F '#{session_name}@@#{pane_current_path}@@#{pane_start_command}@@#{pane_current_command}@@#{session_created}@@#{session_attached}' 2>/dev/null || true; } |
    awk -F'@@' -v l1="$CLAUDE_LOGIN_SESSION" -v l2="$AWS_LOGIN_SESSION" \
      '($3 ~ /claude/ || $4 == "claude") && $1 != l1 && $1 != l2 && $1 != "mcp-auth" && $1 !~ /^worker-login-/ && !seen[$1]++' |
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
    out="$(jq -c --argjson d "$([[ -e "$PUSH_DIR/$name" ]] && echo true || echo false)" \
      '.[-1].push_done = $d' <<<"$out")"
  done < <(jq -c '.[]' <<<"${s:-[]}")
  printf '%s' "$out"
}

do_sessions() {
  api_ok "$(jq -cn --argjson s "$(sessions_list_json)" --argjson now "$(date +%s)" '{now:$now, sessions:$s}')"
}

# Push alerts (Firebase Cloud Messaging; claude-push sends them from Claude's hooks).
# push-config: what the phone needs to join the project (public ids only).
do_push_config() {
  local c=false
  [[ -s "$PUSH_CONFIG" && -s "$LAUNCHER_CONFIG_DIR/fcm-key.json" ]] && jq -e '.project_id and .app_id and .api_key and .sender_id' "$PUSH_CONFIG" >/dev/null 2>&1 && c=true
  if $c; then
    api_ok "$(jq -c '{configured:true, project_id, app_id, api_key, sender_id}' "$PUSH_CONFIG")"
  else
    api_ok '{"configured":false}'
  fi
}

# push-setup: stdin = the Firebase service-account JSON key. Saves it (mode 600),
# registers the Android app in that project and saves its public ids.
do_push_setup() {
  local key bin="$HOME/.local/bin/claude-push" r tmp
  key="$(head -c 20000)"
  exec 0</dev/null
  jq -e 'select(.type == "service_account" and .project_id and .client_email and .private_key)' >/dev/null 2>&1 <<<"$key" ||
    api_err invalid_name "That isn't a service-account JSON key. In Firebase: Project settings › Service accounts › Generate new private key."
  [[ -x "$bin" ]] || api_err not_configured "The push helper isn't installed on the server: run Update now."
  mkdir -p "$LAUNCHER_CONFIG_DIR"
  tmp="$(mktemp "$LAUNCHER_CONFIG_DIR/fcm-key.json.XXXXXX")"
  chmod 600 "$tmp"
  printf '%s\n' "$key" >"$tmp"
  mv "$tmp" "$LAUNCHER_CONFIG_DIR/fcm-key.json"
  unset key
  r="$(timeout 60 "$bin" --setup 2>/dev/null)"
  jq -e '.app_id' >/dev/null 2>&1 <<<"$r" || { rm -f "$LAUNCHER_CONFIG_DIR/fcm-key.json" "$PUSH_CONFIG"; api_err invalid_name "$(jq -r '.error // "Firebase didn'"'"'t accept that key."' <<<"$r" 2>/dev/null)"; }
  ensure_push_hook
  do_push_config
}

# push-register <fcm token>: remember a phone (newest five kept).
do_push_register() {
  [[ $# -eq 1 && "$1" =~ ^[A-Za-z0-9:_-]{20,200}$ ]] || bad_args "usage: push-register <token>"
  local tmp n
  mkdir -p "$LAUNCHER_CONFIG_DIR"
  tmp="$(mktemp "$PUSH_TOKENS.XXXXXX")"
  chmod 600 "$tmp"
  { grep -vxF "$1" "$PUSH_TOKENS" 2>/dev/null || true; echo "$1"; } | tail -n 5 >"$tmp"
  mv "$tmp" "$PUSH_TOKENS"
  n="$(wc -l <"$PUSH_TOKENS")"
  api_ok "$(jq -cn --argjson n "$n" '{registered:true, devices:$n}')"
}

# push-session <session> on|off: "finished" alerts for one session (questions always alert).
do_push_session() {
  [[ $# -eq 2 && ( "$2" == on || "$2" == off ) ]] && valid_project "$1" || bad_args "usage: push-session <session> on|off"
  mkdir -p "$PUSH_DIR"
  if [[ "$2" == on ]]; then : >"$PUSH_DIR/$1"; else rm -f "$PUSH_DIR/$1"; fi
  api_ok "$(jq -cn --arg s "$1" --argjson on "$([[ "$2" == on ]] && echo true || echo false)" '{session:$s, push_done:$on}')"
}

# push-test: send a push to every registered phone now.
do_push_test() {
  local bin="$HOME/.local/bin/claude-push" r
  [[ -x "$bin" && -s "$PUSH_CONFIG" ]] || api_err not_configured "Push alerts aren't set up on this server yet."
  r="$(timeout 40 "$bin" --test 2>/dev/null)"
  jq -e '.sent != null' >/dev/null 2>&1 <<<"$r" || r='{"sent":0}'
  api_ok "$r"
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
  dir="$(session_dir "$sess")"
  [[ -d "$dir" ]] || api_err internal "Couldn't find the folder of session '$sess'."
  name="$(basename "$dir")"
  valid_project "$name" || api_err invalid_name "The folder name '$name' can't be used as a session name."
  # The live conversation is the newest transcript in Claude's folder for this project.
  sid="$(ls -t "$HOME/.claude/projects/$(claude_proj_slug "$dir")/"*.jsonl 2>/dev/null | head -n 1 | xargs -r basename | sed 's/\.jsonl$//')"
  [[ "$sid" =~ ^[0-9a-f-]{36}$ ]] || sid=""
  tmux kill-session -t "=$sess" 2>/dev/null
  STARTED_SESSION="$(session_name "$name")"
  if [[ -n "$sid" ]]; then
    trust_folder "$dir"
    pp=""
    if tmux new-session -d -s "$STARTED_SESSION" -c "$dir" \
      "env -u ANTHROPIC_API_KEY claude --remote-control $(printf %q "$name") --resume $sid; exec bash"; then
      autostart_save
      sleep 4
      pp="$(tmux display-message -p -t "=$STARTED_SESSION:" '#{pane_pid}' 2>/dev/null)"
    fi   # (a name already taken leaves pp empty: never judge another session's Claude)
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
  local envs=(-e "LOGIN_BIN=$bin")
  # Team: sign a worker account in to its own config dir.
  [[ -n "${LOGIN_CONFIG_DIR:-}" ]] && envs+=(-e "CLAUDE_CONFIG_DIR=$LOGIN_CONFIG_DIR" -e "CODEX_HOME=$LOGIN_CONFIG_DIR" -e "GEMINI_CLI_HOME=$LOGIN_CONFIG_DIR" -e NO_BROWSER=true)
  tmux new-session -d -s "$name" -x 1000 -y 60 "${envs[@]}" \
    bash -c 'env -u ANTHROPIC_API_KEY "$LOGIN_BIN" "$@"; echo "[exit $?]"; sleep 900' login "$@" >/dev/null 2>&1
}

end_login() {
  tmux kill-session -t "=$1" 2>/dev/null
  autostart_forget "$1"
}

# Secrets come on stdin; never wait for them forever.
read_secret_line() { local v=""; IFS= read -r -t 15 v || true; v="${v//$'\r'/}"; printf '%s' "$v"; }

# Changes whenever claude writes new credentials.
claude_creds_sig() { stat -c '%Y:%s' "${LOGIN_CONFIG_DIR:-$HOME/.claude}/.credentials.json" 2>/dev/null || echo none; }

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

# ---------------- Team: extra Claude accounts the main Claude can delegate to ----------------

worker_check() { [[ "$1" =~ $WORKER_RE ]] || api_err invalid_name "Worker names are letters, digits, - and _ (start with a letter)."; }
worker_exists() { worker_check "$1"; [[ -f "$WORKERS_DIR/$1/meta.json" ]] || api_err invalid_name "No worker named '$1'."; }

do_worker_list() {
  local d n signed out=""
  for d in "$WORKERS_DIR"/*/; do
    [[ -f "$d/meta.json" ]] || continue
    n="$(basename "$d")"; signed=false
    worker_signed "${d%/}" && signed=true
    out+="$(jq -c --arg n "$n" --argjson s "$signed" '{name:$n, kind:(.kind // "claude"), role:(.role // ""), mode:(.mode // "acceptEdits"), signed_in:$s}' "$d/meta.json")"$'\n'
  done
  api_ok "$(printf '%s' "$out" | jq -sc '{workers:.}')"
}

# Tokens this account used on THIS server in the last 5 h / 7 days, from Claude's own session logs ($1 = config dir).
# (in = input + cache writes; cached = cache reads.) Rolling windows, so only an estimate of the plan's own windows.
token_usage() {
  local dir="$1/projects" c5 c7 cache
  [[ -d "$dir" ]] || { echo null; return; }
  # A refresh reads every recent session log: reuse the answer for a minute.
  cache="$HOME/.cache/claude-launcher/tokens-$(printf '%s' "$dir" | cksum | cut -d' ' -f1)"
  if [[ -n "$(find "$cache" -mmin -1 2>/dev/null)" ]]; then cat "$cache"; return; fi
  c5="$(date -u -d '5 hours ago' +%Y-%m-%dT%H:%M:%S)"; c7="$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%S)"
  local out
  out="$({ find "$dir" -name '*.jsonl' -mtime -8 -print0 | xargs -0 -r grep -h '"output_tokens"' 2>/dev/null || true; } |
    jq -rR 'fromjson? | select(.type == "assistant" and .message.usage != null) | [(.message.id // .uuid), .timestamp, (.message.usage.input_tokens // 0), (.message.usage.output_tokens // 0), (.message.usage.cache_creation_input_tokens // 0), (.message.usage.cache_read_input_tokens // 0)] | @tsv' 2>/dev/null |
    awk -F'\t' -v c5="$c5" -v c7="$c7" '
      { ts[$1] = $2; i[$1] = $3 + $5; o[$1] = $4; r[$1] = $6 }   # one row per message: the last chunk has the final counts
      END {
        for (k in ts) {
          if (ts[k] >= c7) { ai += i[k]; ao += o[k]; ar += r[k] }
          if (ts[k] >= c5) { bi += i[k]; bo += o[k]; br += r[k] }
        }
        printf "{\"five_hour\":{\"in\":%.0f,\"out\":%.0f,\"cached\":%.0f},\"seven_day\":{\"in\":%.0f,\"out\":%.0f,\"cached\":%.0f}}\n", bi, bo, br, ai, ao, ar
      }' || echo null)"
  { mkdir -p "${cache%/*}" && chmod 700 "${cache%/*}" && printf '%s\n' "$out" >"$cache"; } 2>/dev/null || true
  printf '%s\n' "$out"
}

# One account for the cluster view as a JSON line: who it is and how much usage is left.
# $1 name, $2 config dir (holds .credentials.json), $3 .claude.json, $4 role, $5 mode
cluster_account() {
  local name="$1" dir="$2" cj="$3" role="$4" mode="$5" cred="$2/.credentials.json"
  local email="" plan="" exp=0 tok="" raw="" usage=null uerr=null signed=false
  if creds_ok "$cred"; then
    signed=true
    plan="$(jq -r '.claudeAiOauth.subscriptionType // empty' "$cred" 2>/dev/null || true)"
    exp="$(jq -r '.claudeAiOauth.expiresAt // 0' "$cred" 2>/dev/null || echo 0)"
    tok="$(jq -r '.claudeAiOauth.accessToken // empty' "$cred" 2>/dev/null || true)"
  fi
  [[ -s "$cj" ]] && email="$(jq -r '.oauthAccount.emailAddress // empty' "$cj" 2>/dev/null || true)"
  if [[ -z "$tok" ]]; then
    [[ "$signed" == true ]] && uerr='"unavailable"'
  elif [[ "$exp" =~ ^[0-9]+$ ]] && ((exp > 0 && exp / 1000 < $(date +%s))); then
    # Claude refreshes its own token when it next runs; refreshing here would rotate it under a running session.
    uerr='"expired"'
  else
    # ponytail: undocumented endpoint (what Claude Code's /usage reads); if it changes the tile just says "unavailable".
    # The token goes through curl's config on stdin so it never shows in `ps`.
    raw="$(printf 'header = "Authorization: Bearer %s"\nheader = "anthropic-beta: oauth-2025-04-20"\n' "$tok" |
      curl -sf --max-time 8 --config - https://api.anthropic.com/api/oauth/usage 2>/dev/null || true)"
    usage="$(jq -c '{five_hour:{pct:(.five_hour.utilization // null), resets_at:(.five_hour.resets_at // null)}, seven_day:{pct:(.seven_day.utilization // null), resets_at:(.seven_day.resets_at // null)}}' <<<"$raw" 2>/dev/null || true)"
    [[ -n "$usage" ]] || { usage=null; uerr='"unavailable"'; }
  fi
  local tokens; tokens="$(token_usage "$dir")"
  jq -nc --arg n "$name" --arg e "$email" --arg p "$plan" --arg r "$role" --arg m "$mode" \
    --argjson s "$signed" --argjson u "$usage" --argjson ue "$uerr" --argjson tk "${tokens:-null}" \
    '{name:$n, email:(if $e=="" then null else $e end), plan:(if $p=="" then null else $p end), signed_in:$s, role:$r, mode:$m, usage:$u, usage_error:$ue, tokens:$tk}'
}

# The main account and every worker, looked up in parallel (one slow request doesn't hold up the rest).
do_cluster() {
  local tmp d n i=0 f
  tmp="$(mktemp -d)"
  cluster_account main "$HOME/.claude" "$HOME/.claude.json" "" "" >"$tmp/00" &
  for d in "$WORKERS_DIR"/*/; do
    [[ -f "$d/meta.json" ]] || continue
    n="$(basename "$d")"; i=$((i + 1))
    if [[ "$(worker_kind "${d%/}")" == claude ]]; then
      cluster_account "$n" "$d/home" "$d/home/.claude.json" "$(jq -r '.role // ""' "$d/meta.json")" "$(jq -r '.mode // "acceptEdits"' "$d/meta.json")" >"$tmp/$(printf %02d "$i")" &
    else   # no usage numbers or token counts for these
      jq -c --arg n "$n" --argjson s "$({ worker_signed "${d%/}" && echo true; } || echo false)" \
        '{name:$n, kind:.kind, email:null, plan:null, signed_in:$s, role:(.role // ""), mode:(.mode // "acceptEdits"), usage:null, usage_error:"unavailable", tokens:null}' "$d/meta.json" >"$tmp/$(printf %02d "$i")"
    fi
  done
  wait
  f="$(cat "$tmp"/* | jq -sc '{accounts:.}')"
  rm -rf "$tmp"
  api_ok "$f"
}

CLUSTER_CONFIG="$LAUNCHER_CONFIG_DIR/cluster.json"
cluster_config_json() {
  local d='{"max_parallel":3,"handback":true,"handback_pct":95}'
  jq -c --argjson d "$d" '($d + (if type == "object" then . else {} end)) |
    {max_parallel: ((.max_parallel | numbers | floor) // 3 | if . < 1 then 1 elif . > 10 then 10 else . end),
     handback: (if (.handback | type) == "boolean" then .handback else true end),
     handback_pct: ((.handback_pct | numbers | floor) // 95 | if . < 50 then 50 elif . > 100 then 100 else . end)}' \
    "$CLUSTER_CONFIG" 2>/dev/null || echo "$d"
}

do_cluster_config() {
  [[ $# -eq 0 ]] || bad_args "cluster-config takes no arguments"
  api_ok "$(cluster_config_json)"
}

# cluster-config-set max_parallel 1-10 | handback true|false | handback_pct 50-100
do_cluster_config_set() {
  [[ $# -eq 2 ]] || bad_args "usage: cluster-config-set max_parallel|handback|handback_pct <value>"
  local cur new tmp
  cur="$(cluster_config_json)"
  case "$1" in
    max_parallel) [[ "$2" =~ ^([1-9]|10)$ ]] || api_err invalid_name "Parallel tasks must be 1 to 10."
      new="$(jq -c --argjson v "$2" '.max_parallel = $v' <<<"$cur")" ;;
    handback) [[ "$2" == true || "$2" == false ]] || api_err invalid_name "Hand back must be true or false."
      new="$(jq -c --argjson v "$2" '.handback = $v' <<<"$cur")" ;;
    handback_pct) [[ "$2" =~ ^([5-9][0-9]|100)$ ]] || api_err invalid_name "The limit must be 50 to 100 percent."
      new="$(jq -c --argjson v "$2" '.handback_pct = $v' <<<"$cur")" ;;
    *) bad_args "usage: cluster-config-set max_parallel|handback|handback_pct <value>" ;;
  esac
  mkdir -p "$LAUNCHER_CONFIG_DIR"
  tmp="$(mktemp "$LAUNCHER_CONFIG_DIR/cluster.json.XXXXXX")"
  printf '%s\n' "$new" >"$tmp" && mv -f "$tmp" "$CLUSTER_CONFIG" || { rm -f "$tmp"; api_err internal "Couldn't save the cluster settings."; }
  api_ok "$new"
}

do_worker_add() {
  local name="${1:-}" kind="${2:-claude}" role
  role="$(read_secret_line)"
  exec 0</dev/null
  worker_check "$name"
  [[ "$WORKER_KINDS" == *" $kind "* && $# -le 2 ]] || api_err invalid_name "Kind must be claude, codex or gemini."
  [[ ! -f "$WORKERS_DIR/$name/meta.json" ]] || api_err invalid_name "A worker named '$name' already exists."
  mkdir -p "$WORKERS_DIR/$name/home" "$WORKERS_DIR/$name/tasks"
  chmod 700 "$WORKERS_DIR" "$WORKERS_DIR/$name" "$WORKERS_DIR/$name/home" "$WORKERS_DIR/$name/tasks"
  jq -cn --arg r "${role:0:200}" --arg k "$kind" '{role:$r, mode:"acceptEdits", kind:$k}' >"$WORKERS_DIR/$name/meta.json"
  api_ok "$(jq -cn --arg n "$name" '{added:$n}')"
}

do_worker_set() {
  local name="${1:-}" field="${2:-}" val tmp
  val="$(read_secret_line)"
  exec 0</dev/null
  worker_exists "$name"
  case "$field" in
    role) ;;
    mode) [[ "$WORKER_MODES" == *" $val "* ]] || api_err invalid_name "Mode must be acceptEdits, plan or bypassPermissions." ;;
    model) [[ -z "$val" || "$val" =~ ^[A-Za-z0-9._-]{1,60}$ ]] || api_err invalid_name "That doesn't look like a model name (e.g. claude-sonnet-5-5)." ;;
    effort) [[ -z "$val" || "$val" =~ ^(low|medium|high|xhigh|max)$ ]] || api_err invalid_name "Effort must be low, medium, high, xhigh or max." ;;
    *) bad_args "usage: worker-set <name> role|mode|model|effort" ;;
  esac
  tmp="$(mktemp "$WORKERS_DIR/$name/meta.XXXXXX")"
  jq -c --arg f "$field" --arg v "${val:0:200}" '.[$f] = $v' "$WORKERS_DIR/$name/meta.json" >"$tmp" &&
    mv -f "$tmp" "$WORKERS_DIR/$name/meta.json" || { rm -f "$tmp"; api_err internal "Couldn't save the worker."; }
  api_ok "$(jq -cn --arg n "$name" '{saved:$n}')"
}

do_worker_remove() {
  worker_exists "${1:-}"
  end_login "worker-login-$1"
  rm -rf -- "${WORKERS_DIR:?}/$1"
  api_ok "$(jq -cn --arg n "$1" '{removed:$n}')"
}

do_worker_runs() {
  worker_exists "${1:-}"
  local f
  api_ok "$( { for f in "$WORKERS_DIR/$1"/tasks/*.json; do [[ -f "$f" ]] && cat "$f" && echo; done; } 2>/dev/null |
    jq -sc '{runs: (map({id, task:(.task // "" | .[0:300]), status, reply:(.reply // null | if . then .[0:4000] else . end), error:(.error // null), started:(.started // 0)}) | sort_by(-.started) | .[0:20])}')"
}

# Which workers a chat's project hands work to. clauderc-team reads <attach>/<project folder slug>.json:
# {"<worker>": {"role": "…", "mode": "…"}}  (present = attached; role/mode override the worker's own).
# attach_session <project>: sets ATTACH_FILE (not echoed: api_err must not run inside $(…)).
attach_session() {
  chat_session "$1"
  local d; d="$(session_dir "$CHAT_SESS")"
  [[ -n "$d" ]] || api_err invalid_name "Couldn't find that session's folder."
  d="$(readlink -f -- "$d" 2>/dev/null || printf '%s' "$d")"   # clauderc-team uses the physical path
  ATTACH_DIR_OF="$d"
  ATTACH_FILE="$ATTACH_DIR/$(claude_proj_slug "$d").json"
}
attach_read() { { [[ -s "$ATTACH_FILE" ]] && jq -c 'if type == "object" then . else {} end' "$ATTACH_FILE" 2>/dev/null; } || echo '{}'; }
attach_write() {  # $1 = jq filter, rest = jq args
  local f="$1" tmp; shift
  mkdir -p "$ATTACH_DIR" && chmod 700 "$ATTACH_DIR"
  tmp="$(mktemp "$ATTACH_DIR/a.XXXXXX")"
  attach_read | jq -c "$@" "$f" >"$tmp" && mv -f "$tmp" "$ATTACH_FILE" || { rm -f "$tmp"; api_err internal "Couldn't save."; }
}

do_cluster_session() {
  [[ $# -eq 1 ]] || bad_args "usage: cluster-session <project>"
  attach_session "$1"
  local att d n signed out=""
  att="$(attach_read)"
  for d in "$WORKERS_DIR"/*/; do
    [[ -f "$d/meta.json" ]] || continue
    n="$(basename "$d")"; signed=false
    worker_signed "${d%/}" && signed=true
    out+="$(jq -c --arg n "$n" --argjson s "$signed" --argjson a "$att" \
      '{name:$n, kind:(.kind // "claude"), signed_in:$s, attached:($a | has($n)), role:((try $a[$n].role catch null) // .role // ""), mode:((try $a[$n].mode catch null) // .mode // "acceptEdits")}' "$d/meta.json")"$'\n'
  done
  # Open work for this project: running tasks and branches waiting for the main Claude to merge or discard.
  local tasks f
  tasks="$({ for f in "$WORKERS_DIR"/*/tasks/*.json; do [[ -f "$f" ]] && cat "$f" && echo; done; } 2>/dev/null |
    jq -sc --arg d "$ATTACH_DIR_OF" '[.[] | select(.project == $d and (.status == "running" or (.branch and (.closed | not))))
      | {id, worker, task:(.task // "" | .[0:120]), status, branch:(.branch // null), started:(.started // 0)}] | sort_by(-.started) | .[0:12]' 2>/dev/null || true)"
  api_ok "$(printf '%s' "$out" | jq -sc --argjson t "${tasks:-[]}" '{workers:., tasks:$t}')"
}

do_cluster_attach() {
  [[ $# -eq 3 ]] || bad_args "usage: cluster-attach <project> <worker> on|off"
  worker_exists "$2"
  attach_session "$1"
  case "$3" in
    on) attach_write '.[$n] //= {}' --arg n "$2" ;;
    off) attach_write 'del(.[$n])' --arg n "$2" ;;
    *) bad_args "usage: cluster-attach <project> <worker> on|off" ;;
  esac
  api_ok "$(jq -cn --arg n "$2" --arg v "$3" '{worker:$n, state:$v}')"
}

# cluster-assign <project> <worker> role|mode, stdin = value (empty = back to the worker's own).
do_cluster_assign() {
  local val
  val="$(read_secret_line)"
  exec 0</dev/null
  [[ $# -eq 3 ]] || bad_args "usage: cluster-assign <project> <worker> role|mode"
  worker_exists "$2"
  case "$3" in
    role) ;;
    mode) [[ -z "$val" || "$WORKER_MODES" == *" $val "* ]] || api_err invalid_name "Mode must be acceptEdits, plan or bypassPermissions." ;;
    *) bad_args "usage: cluster-assign <project> <worker> role|mode" ;;
  esac
  attach_session "$1"
  if [[ -z "$val" ]]; then attach_write 'if has($n) then .[$n] |= del(.[$f]) else . end' --arg n "$2" --arg f "$3"
  else attach_write 'if has($n) then .[$n] = ((.[$n] // {}) + {($f): $v}) else . end' --arg n "$2" --arg f "$3" --arg v "${val:0:200}"; fi
  api_ok "$(jq -cn --arg n "$2" '{saved:$n}')"
}

# Worker sign-in = the main account's login flow, aimed at the worker's folder and its own tmux session.
worker_login_target() {
  worker_exists "${1:-}"
  CLAUDE_LOGIN_SESSION="worker-login-$1"
  LOGIN_CONFIG_DIR="$WORKERS_DIR/$1/home"
}
do_worker_login_start() {
  worker_login_target "${1:-}"
  case "$(worker_kind "$WORKERS_DIR/$1")" in
    codex) do_login_codex_start ;;
    gemini) do_login_gemini_start ;;
    *) do_login_claude_start ;;
  esac
}
do_worker_login_code() {
  worker_login_target "${1:-}"
  case "$(worker_kind "$WORKERS_DIR/$1")" in
    codex) do_login_codex_code ;;
    gemini) do_login_gemini_code ;;
    *) do_login_claude_code ;;
  esac
}

# The Codex and Gemini CLIs install on first use (npm into ~/.local, on the sessions' PATH).
ensure_cli() {  # <binary> <npm package>
  command -v "$1" >/dev/null 2>&1 && return 0
  command -v npm >/dev/null 2>&1 || api_err internal "Installing $1 needs npm, which isn't on the server."
  timeout 300 npm install -g --prefix "$HOME/.local" "$2" >/dev/null 2>&1 || true
  command -v "$1" >/dev/null 2>&1 || api_err internal "Couldn't install $1 ($2) on the server."
}

# Waits for the first URL in a login pane. Returns it in LOGIN_URL.
login_wait_url() {  # <session> <seconds> [enter-on-pattern]
  local i text="" k
  LOGIN_URL=""
  for ((i = 0; i < $2 * 2; i++)); do
    sleep 0.5
    text="$(pane_text "$1")"
    LOGIN_URL="$(grep -oE 'https://[^[:space:]]+' <<<"$text" | grep -vE 'docs|github\.com|gemini\.google\.com/?$' | head -n 1 | tr -d '\r')"
    [[ -n "$LOGIN_URL" ]] && break
    grep -q '\[exit ' <<<"$text" && break
    # first-run prompts (trust this folder, pick the Google sign-in): the default answer is the right one
    [[ -n "${3:-}" ]] && grep -qiE "$3" <<<"$text" && { tmux send-keys -t "=$1:" Enter; sleep 1; }
  done
  LOGIN_PANE="$(tail -n 15 <<<"$text")"
}

# ChatGPT (Codex): plain `codex login` listens on 127.0.0.1:1455 and prints an OpenAI link. After sign-in the phone's browser is sent to
# http://127.0.0.1:1455/auth/callback?... which only exists on the server, so the user pastes that address back and we fetch it here.
# (Device codes are an account setting OpenAI refuses on some accounts, so they are not used.)
do_login_codex_start() {
  need tmux; ensure_cli codex @openai/codex
  start_login_session "$CLAUDE_LOGIN_SESSION" "$(command -v codex)" login || api_err internal "Could not start the login session."
  login_wait_url "$CLAUDE_LOGIN_SESSION" 30
  if [[ -z "$LOGIN_URL" ]]; then
    end_login "$CLAUDE_LOGIN_SESSION"
    api_err internal "No login URL appeared within 30 s." "$(jq -cn --arg t "$LOGIN_PANE" '{pane:$t}')"
  fi
  api_ok "$(jq -cn --arg u "$LOGIN_URL" --arg s "$CLAUDE_LOGIN_SESSION" '{url:$u, paste_url:true, session:$s}')"
}

do_login_codex_code() {
  local url
  url="$(read_secret_line)"
  exec 0</dev/null
  url="${url//[[:space:]]/}"
  [[ "$url" =~ ^https?://(localhost|127\.0\.0\.1):1455(/auth/callback\?[A-Za-z0-9%\&=._~+/-]{1,3000})$ ]] ||
    api_err invalid_name "Paste the whole address of the page that failed to load (it starts with http://localhost:1455/auth/callback)."
  tmux has-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null || api_err not_logged_in_claude "The login session expired. Start the sign-in again."
  curl -s --max-time 20 -o /dev/null "http://127.0.0.1:1455${BASH_REMATCH[2]}" || true
  do_login_device_wait codex
}

# Gemini: NO_BROWSER makes the CLI print a Google URL and ask for the code it shows afterwards.
do_login_gemini_start() {
  need tmux; ensure_cli gemini @google/gemini-cli
  start_login_session "$CLAUDE_LOGIN_SESSION" "$(command -v gemini)" --skip-trust || api_err internal "Could not start the login session."
  login_wait_url "$CLAUDE_LOGIN_SESSION" 40 'login with google|sign in with google|trust'
  if [[ -z "$LOGIN_URL" ]]; then
    end_login "$CLAUDE_LOGIN_SESSION"
    api_err internal "No login URL appeared within 40 s." "$(jq -cn --arg t "$LOGIN_PANE" '{pane:$t}')"
  fi
  api_ok "$(jq -cn --arg u "$LOGIN_URL" --arg s "$CLAUDE_LOGIN_SESSION" '{url:$u, session:$s}')"
}

# Success = the CLI wrote its login file under the worker's home.
do_login_device_wait() {  # <codex|gemini>
  local w="${CLAUDE_LOGIN_SESSION#worker-login-}" i text=""
  exec 0</dev/null
  for ((i = 0; i < 45; i++)); do
    worker_signed "$WORKERS_DIR/$w" && { end_login "$CLAUDE_LOGIN_SESSION"; api_ok '{"logged_in":true}'; }
    tmux has-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null || break
    sleep 1
  done
  text="$(pane_text "$CLAUDE_LOGIN_SESSION")"
  api_err not_logged_in_claude "Login did not complete. Start the sign-in again." "$(jq -cn --arg t "$(tail -n 15 <<<"$text")" '{pane:$t}')"
}

do_login_gemini_code() {
  local code
  code="$(read_secret_line)"
  exec 0</dev/null
  code="${code//[[:space:]]/}"
  [[ "$code" =~ ^[A-Za-z0-9#_.~=+/-]{4,1024}$ ]] || api_err invalid_name "That doesn't look like a login code."
  tmux has-session -t "=$CLAUDE_LOGIN_SESSION" 2>/dev/null || api_err not_logged_in_claude "The login session expired. Start the sign-in again."
  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" -l -- "$code"
  tmux send-keys -t "=$CLAUDE_LOGIN_SESSION:" Enter
  do_login_device_wait gemini
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
  trap 'rc=$?; rm -f "$csv"; (exit $rc); on_exit' EXIT
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
    { cmp -s "$tmp" "$f" || { chmod 600 "$tmp"; mv "$tmp" "$f"; }; jq -r 'keys[]' <<<"$vars" >"$managed"; }   # rewrite (atomically) only on change
  rm -f "$tmp"
}

# ensure_push_hook: run claude-push when a session asks something (Notification)
# or finishes (Stop). Added once, found again by the command; never touches a
# settings file we can't parse. Applies when a session (re)starts.
ensure_push_hook() {
  local f="$HOME/.claude/settings.json" bin="$HOME/.local/bin/claude-push" tmp
  [[ -x "$bin" && -f "$PUSH_CONFIG" && -s "$f" ]] || return 0
  jq -e . "$f" >/dev/null 2>&1 || return 0
  tmp="$(mktemp "$f.XXXXXX")" || return 0
  jq --arg c "$bin" '
    reduce ("Notification", "Stop") as $e (.;
      if ([.hooks[$e][]?.hooks[]?.command] | index($c)) then .
      else .hooks[$e] = ((.hooks[$e] // []) + [{hooks:[{type:"command", command:$c}]}]) end)' "$f" >"$tmp" &&
    { cmp -s "$tmp" "$f" || { chmod 600 "$tmp"; mv "$tmp" "$f"; }; }
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
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
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

APPLE_DIR="$LAUNCHER_CONFIG_DIR/apple"

# login-apple: an App Store Connect API key (signs and uploads iOS builds). stdin:
# key ID, issuer ID, team ID (may be empty), then the .p8 file base64-encoded.
# Saved as apple/AuthKey.p8 (mode 600); every session gets APPLE_API_KEY_ID,
# APPLE_API_ISSUER_ID, APPLE_API_KEY_FILE and (if given) APPLE_TEAM_ID.
do_login_apple() {
  [[ $# -eq 0 ]] || bad_args "login-apple takes no arguments"
  local kid iss team dir f
  kid="$(read_secret_line)"; iss="$(read_secret_line)"; team="$(read_secret_line)"
  dir="$(mktemp -d)" && chmod 700 "$dir"
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
  head -c 20000 | tr -d '[:space:]' | base64 -d >"$dir/key" 2>/dev/null || api_err invalid_name "The key file didn't come through."
  exec 0</dev/null
  [[ "$kid" =~ ^[A-Z0-9]{10}$ ]] || api_err invalid_name "The Key ID is 10 letters/digits (App Store Connect › Integrations)."
  [[ "$iss" =~ ^[0-9a-fA-F-]{36}$ ]] || api_err invalid_name "The Issuer ID is a UUID like 69a6de70-…"
  [[ -z "$team" || "$team" =~ ^[A-Z0-9]{10}$ ]] || api_err invalid_name "The Team ID is 10 letters/digits."
  grep -q -- '-----BEGIN PRIVATE KEY-----' "$dir/key" || api_err invalid_name "That isn't an App Store Connect .p8 key file."
  mkdir -p "$APPLE_DIR" && chmod 700 "$APPLE_DIR"
  f="$APPLE_DIR/AuthKey.p8"
  install -m 600 "$dir/key" "$f.new" && mv -f "$f.new" "$f"
  set_env APPLE_API_KEY_ID "$kid"
  set_env APPLE_API_ISSUER_ID "$iss"
  set_env APPLE_API_KEY_FILE "$f"
  set_env APPLE_TEAM_ID "$team"
  ensure_env_hook
  api_ok "$(jq -cn --arg k "$kid" '{saved:true, key_id:$k}')"
}

do_remove_apple() {
  [[ $# -eq 0 ]] || bad_args "remove-apple takes no arguments"
  [[ -f "$APPLE_DIR/AuthKey.p8" ]] || api_err invalid_name "No Apple developer key saved."
  rm -f "$APPLE_DIR/AuthKey.p8"
  local v
  for v in APPLE_API_KEY_ID APPLE_API_ISSUER_ID APPLE_API_KEY_FILE APPLE_TEAM_ID; do set_env "$v" ""; done
  ensure_env_hook
  api_ok '{"removed":true}'
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
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
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
  # "once" (a password test): the second prompt, after a wrong password, finds no file, so sudo gives up
  # after one failed try (three would count as three failures for pam_faillock).
  if [[ "${3:-}" == once ]]; then printf '#!/bin/sh\ncat %q && rm -f %q\n' "$dir/pw" "$dir/pw" >"$dir/askpass"
  else printf '#!/bin/sh\ncat %q\n' "$dir/pw" >"$dir/askpass"; fi
  chmod 700 "$dir/askpass"
  chmod 600 "$dir/pw"
  # (called again for the same dir: only the password and askpass change; a second wrapper would exec itself)
  [[ ! -x "$dir/bin/sudo" ]] || return 0
  mkdir -p "$dir/bin"
  printf '#!/bin/sh\nexec %q -A "$@"\n' "$real_sudo" >"$dir/bin/sudo"
  chmod 700 "$dir/bin/sudo"
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
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
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
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
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
  rm -f "$HOME/.claude/projects/$(claude_proj_slug "$dir")/"*.jsonl
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
  [[ "$cb" =~ ^http://(localhost|127\.0\.0\.1):[0-9]{1,5}/[A-Za-z0-9._~:/?#@!$\&\'()*+,\;=%-]*$ && ${#cb} -le 4000 ]] ||
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
  # Update lights: the helper says per plugin whether a newer version exists.
  # Not installed or failing: the list goes out without the extra fields.
  if [[ -x "$HOME/.local/bin/claude-plugin-updates" ]]; then
    t 100 "$HOME/.local/bin/claude-plugin-updates" </dev/null >"$d.up" 2>/dev/null || true
    if jq -e 'type == "object"' "$d.up" >/dev/null 2>&1 &&
      jq -c --slurpfile u "$d.up" '.installed |= map(if ($u[0][.id] | type) == "object"
        then . + {update:($u[0][.id].state // "unknown"), latest:($u[0][.id].latest // "")}
          + (if ($u[0][.id].via | type) == "string" and ($u[0][.id].source | type) == "string"
             then {via:$u[0][.id].via, source:$u[0][.id].source} else {} end) else . end)' "$d.res" >"$d.res2" 2>/dev/null; then
      mv -f "$d.res2" "$d.res"
    fi
    rm -f "$d.up" "$d.res2"
  fi
  api_ok_file "$d.res"
}

# skills-update <owner/repo>: update a plugin the `skills` CLI manages (npx skills add), which
# `claude plugin update` can't. Only a package already in the skills lock file: the phone can't
# install arbitrary repos this way.
do_skills_update() {
  local re='^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' repo="${1:-}" lock="${CLAUDERC_SKILL_LOCK:-$HOME/.agents/.skill-lock.json}" d out rc
  [[ $# -eq 1 && "$repo" =~ $re ]] || bad_args "usage: skills-update <owner/repo>"
  jq -e --arg r "$repo" '[(.skills // {})[]? | .source? | strings | ascii_downcase] | index($r | ascii_downcase) != null' "$lock" >/dev/null 2>&1 ||
    api_err invalid_name "That skills package isn't installed on this server."
  need npx
  d="$(mktemp -d)"
  out="$(cd "$d" && t 240 npx -y skills add "$repo" -g -y -s '*' </dev/null 2>&1)"; rc=$?
  rm -rf "$d"
  out="$(sed 's/\x1b\[[0-9;?]*[A-Za-z]//g; s/\r//g' <<<"$out" | grep -v '^[[:space:]]*$')"
  [[ $rc -eq 0 ]] || api_err internal "Updating $repo failed: $(tail -n 3 <<<"$out")"
  api_ok "$(jq -cn --arg r "$repo" --arg o "$(tail -n 5 <<<"$out")" '{updated:$r, output:$o}')"
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
  # One guess at a time: parallel calls would each read the same fail count.
  exec 8>>"$CHAT_FAILS.lock"; flock -w 10 8 || api_err busy "Try again in a moment."
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
  dir="$(session_dir "$sess")"
  ls -t "$HOME/.claude/projects/$(claude_proj_slug "$dir")/"*.jsonl 2>/dev/null | head -n 1
}

# chat_session <arg>: validated, running session name in CHAT_SESS (or the call ends).
# Not echoed: api_err inside $(…) would only end the subshell and leave an empty name,
# which tmux reads as "any session".
chat_session() {
  valid_project "$1" || api_err invalid_name "Invalid project name."
  local s; s="$(resolve_session "$1")"
  tmux has-session -t "=$s" 2>/dev/null || api_err invalid_name "No running session named '$1'."
  # Login and sign-in helpers aren't project chats (their folder would be $HOME).
  [[ " $CLAUDE_LOGIN_SESSION $AWS_LOGIN_SESSION mcp-auth " != *" $s "* && "$s" != worker-login-* ]] || api_err invalid_name "No running session named '$1'."
  CHAT_SESS="$s"
}

# chat-open <session>: stdin = PIN. Just checks it (the app's gate).
do_chat_open() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-open <session>"
  local pin sess; pin="$(read_secret_line)"; exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
  check_pin "$pin"
  chat_log "$sess" open
  api_ok "$(jq -cn --arg s "$sess" '{session:$s}')"
}

# chat-history <session>: stdin = PIN. The last messages of the conversation:
# your text, Claude's text, and one-line summaries of its tool calls; plus
# whether it's working or waiting on a question (with that screen).
do_chat_history() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-history <session>"
  local pin sess f screen waiting=false busy=false tmp mode model="" ask=null
  pin="$(read_secret_line)"; exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
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
      | if startswith("<bash-input>") then
          # A "!" command you typed: shown as you typed it, then its output.
          {id:$l.uuid, role:"user", text:("!" + (capture("<bash-input>(?<c>.*)</bash-input>"; "s").c)[0:8000]), ts:$l.timestamp}
        elif contains("<command-name>") then
          # A slash command you ran: shown as you typed it.
          (capture("<command-name>(?<n>[^<]*)</command-name>") | .n) as $n
          | ((capture("<command-args>(?<a>.*)</command-args>"; "s") | .a) // "") as $a
          | {id:$l.uuid, role:"user", text:(($n + (if $a != "" then " " + $a else "" end))[0:8000]), ts:$l.timestamp}
        elif startswith("<bash-stdout>") then
          (capture("<bash-stdout>(?<o>.*)</bash-stdout><bash-stderr>(?<e>.*)</bash-stderr>"; "s") | (.o + .e)) as $out
          | select($out | length > 0) | {id:$l.uuid, role:"assistant", text:("```\n" + $out[0:8000] + "\n```"), ts:$l.timestamp}
        elif length > 0 and (startswith("<") | not) then {id:$l.uuid, role:"user", text:.[0:8000], ts:$l.timestamp}
        else empty end
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
  chat_session "$1"; sess="$CHAT_SESS"
  check_pin "$pin"
  [[ -n "${msg//[[:space:]]/}" ]] || bad_args "empty message"
  printf '%s' "$msg" | tmux load-buffer -b "clauderc-chat-$$" - || api_err internal "Couldn't hand the message to tmux."
  tmux paste-buffer -p -d -b "clauderc-chat-$$" -t "=$sess:" || api_err internal "Couldn't paste the message."
  # A long or multi-line paste is collapsed to "[Pasted text …]" and takes a moment to register: give it longer, and press Enter again if it is still sitting in the box.
  if [[ ${#msg} -gt 300 || "$msg" == *$'\n'* ]]; then sleep 1; else sleep 0.3; fi
  tmux send-keys -t "=$sess:" Enter
  if [[ ${#msg} -gt 300 || "$msg" == *$'\n'* ]]; then
    local i
    for i in 1 2 3 4 5; do   # a busy session can swallow the Enter more than once
      sleep 1
      tmux capture-pane -p -t "=$sess:" 2>/dev/null | tail -n 6 | grep -q '^❯ .*\[Pasted text' || break
      tmux send-keys -t "=$sess:" Enter
    done
  fi
  unset msg
  chat_log "$sess" send
  api_ok "$(jq -cn --arg s "$sess" '{sent:true, session:$s}')"
}

# chat-commands <session>: stdin = PIN. The slash commands Claude can run there beyond the built-ins:
# your skills and commands, the project's, and those of enabled plugins (as plugin:name).
do_chat_commands() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-commands <session>"
  local pin sess dir; pin="$(read_secret_line)"; exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
  check_pin "$pin"
  dir="$(tmux display-message -p -t "=$sess:" '#{pane_current_path}' 2>/dev/null || true)"
  local plug="$HOME/.claude/plugins/cache" enabled
  enabled="$(jq -r '.enabledPlugins // {} | to_entries[] | select(.value == true) | .key' "$HOME/.claude/settings.json" 2>/dev/null || true)"
  local list
  list="$({
    cmds_in "" "$HOME/.claude"
    [[ -n "$dir" && "$dir" != "$HOME" ]] && cmds_in "" "$dir/.claude"
    local m p v
    for p in "$plug"/*/*/; do
      [[ -d "$p" ]] || continue
      m="$(basename "$(dirname "$p")")"; p="${p%/}"
      grep -qx "$(basename "$p")@$m" <<<"$enabled" || continue
      v="$(ls -v "$p" 2>/dev/null | tail -n 1)"
      [[ -n "$v" ]] && cmds_in "$(basename "$p"):" "$p/$v"
    done
  } 2>/dev/null | sort -u -t$'\t' -k1,1 | head -n 400 | jq -Rcn '[inputs | split("\t") | {name:.[0], hint:(.[1] // "")}]' 2>/dev/null)" || list='[]'
  api_ok "$(jq -cn --argjson c "${list:-[]}" '{commands:$c}')"
}

# cmds_in <prefix> <dir>: "name<TAB>hint" for each skill and command under <dir>/skills and <dir>/commands.
cmds_in() {
  local pre="$1" d="$2" f n h
  for f in "$d"/skills/*/SKILL.md; do
    [[ -f "$f" ]] || continue
    n="$(basename "$(dirname "$f")")"; h="$(skill_hint "$f")"
    printf '/%s%s\t%s\n' "$pre" "$n" "$h"
  done
  for f in "$d"/commands/*.md; do
    [[ -f "$f" ]] || continue
    n="$(basename "$f" .md)"; h="$(skill_hint "$f")"
    printf '/%s%s\t%s\n' "$pre" "$n" "$h"
  done
}

# skill_hint <file>: the front matter's description, first line only, cut short.
skill_hint() {
  awk '/^---/ {c++; next} c==1 && /^description:/ {sub(/^description:[ ]*/, ""); if ($0 ~ /^[>|]/) {w=1; next} print; exit} w && NF {print; exit}' "$1" 2>/dev/null \
    | sed 's/^["'"'"']//; s/["'"'"']$//' | tr -d '\t' | sed 's/^ *//' | cut -c1-80
}

# chat-interrupt <session>: stdin = PIN. Stops what Claude is doing (Esc).
do_chat_interrupt() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-interrupt <session>"
  local pin sess; pin="$(read_secret_line)"; exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
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
  trap 'rc=$?; rm -f "$tmp"; (exit $rc); on_exit' EXIT
  head -c 21000000 | tr -d '[:space:]' | base64 -d >"$tmp" 2>/dev/null || { rm -f "$tmp"; api_err invalid_name "The file didn't come through."; }
  exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
  check_pin "$pin"
  # A plain file name: no folders, no leading dot or dash.
  name="$(basename -- "$name")"
  name="$(printf '%s' "$name" | tr -c 'A-Za-z0-9._-' '_' | sed 's/^[.-]*//' | cut -c1-100)"
  [[ -n "$name" ]] || name="upload-$(date +%s)"
  size=$(stat -c %s "$tmp")
  ((size > 0 && size <= 15728640)) || { rm -f "$tmp"; api_err invalid_name "The file is empty or bigger than 15 MB."; }
  dir="$(session_dir "$sess")"
  [[ -d "$dir" ]] || { rm -f "$tmp"; api_err internal "Couldn't find the session's folder."; }
  d="$dir/uploads"; mkdir -p "$d"
  [[ -e "$d/.gitignore" ]] || printf '*\n' >"$d/.gitignore"   # private files: keep them out of `git add -A`
  [[ -e "$d/$name" ]] && name="$(date +%H%M%S)-$$-$name"
  install -m 600 "$tmp" "$d/$name"; rm -f "$tmp"
  chat_log "$sess" upload
  api_ok "$(jq -cn --arg p "uploads/$name" --argjson n "$size" '{path:$p, bytes:$n}')"
}

# chat-file <session>: stdin = PIN, then a file path. A file from this session's
# project folder or Claude's temp folder for it, or one Claude itself sent to
# you in this conversation (SendUserFile), wherever it is; up to 10 MB, base64
# in `data`.
do_chat_file() {
  [[ $# -eq 1 ]] || bad_args "usage: chat-file <session>"
  local pin path sess dir real size ok=false tf p
  pin="$(read_secret_line)"; path="$(read_secret_line)"; exec 0</dev/null
  chat_session "$1"; sess="$CHAT_SESS"
  check_pin "$pin"
  dir="$(session_dir "$sess")"
  [[ -d "$dir" ]] || api_err internal "Couldn't find the session's folder."
  real="$(realpath -e -- "$path" 2>/dev/null)" && [[ -f "$real" ]] || api_err invalid_name "That file isn't on the server any more."
  case "$real" in
    "$(realpath "$dir")"/* | "/tmp/claude-$(id -u)/$(claude_proj_slug "$dir")"/*) ok=true ;;
  esac
  if ! $ok; then   # a file Claude sent in this conversation counts, wherever it is
    tf="$(chat_transcript "$sess")"
    if [[ -n "$tf" ]]; then
      while IFS= read -r p; do
        [[ "$(realpath -e -- "$p" 2>/dev/null)" == "$real" ]] && { ok=true; break; }
      done < <(tail -n 3000 "$tf" 2>/dev/null | jq -r 'select(.message.content | type == "array") | .message.content[] | select(.type == "tool_use" and .name == "SendUserFile") | .input.files[]? | strings' 2>/dev/null)
    fi
  fi
  $ok || api_err forbidden "That file isn't in this project and wasn't sent in this conversation."
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

# ======================================================================
# Migrate: move this server's setup to another one (the contract is docs/migrate-design.md).
# These actions touch only ~/.config/claude-launcher/migrate, ~/.local/state/claude-launcher/migrate,
# ~/backups and the clauderc-migrate lines of ~/.ssh/authorized_keys. Passphrases live in mode-600
# files and on stdin, never in argv, the environment or a log.
# ======================================================================
MIGRATE_CFG="$LAUNCHER_CONFIG_DIR/migrate"       # the ephemeral ssh key, the passphrase, the pinned known_hosts
MIGRATE_STATE="$API_STATE_DIR/migrate"           # status.json, manifest.json
MIGRATE_STATUS="$MIGRATE_STATE/status.json"
MIGRATE_BACKUPS="$HOME/backups"
MIGRATE_HOST_RE='^([A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|[0-9A-Fa-f]{0,4}(:[0-9A-Fa-f]{0,4}){2,7})$'
MIGRATE_USER_RE='^[A-Za-z_][A-Za-z0-9_.-]{0,31}$'
MIGRATE_HOSTKEY_TYPE_RE='^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521))$'
MIGRATE_BLOB_RE='^[A-Za-z0-9+/]{20,2000}={0,2}$'
MIGRATE_PUBKEY_RE='^ssh-ed25519 [A-Za-z0-9+/]+={0,2}( [A-Za-z0-9@._-]{1,64})?$'
MIGRATE_ADDR_RE='^[A-Za-z0-9][A-Za-z0-9.:-]{0,252}$'
MIGRATE_INCOMING_RE='^incoming-[0-9]{8}-[0-9]{6}(-[0-9]{1,3})?\.gpg$'

# claude-backup does the real work; the env override is for the tests.
migrate_backup_bin() { printf '%s' "${CLAUDERC_BACKUP_BIN:-$HOME/.local/bin/claude-backup}"; }
migrate_need_backup() {
  [[ -x "$(migrate_backup_bin)" ]] || api_err not_configured "claude-backup is missing: run Update now"
}
# Same switch as `run`: the actions that sudo, read the passphrase or push this server's secrets need ALLOW_RUN=1 here.
migrate_need_run() {
  [[ "$ALLOW_RUN" == 1 ]] ||
    api_err run_disabled "Running commands from the phone is off on this server. To allow it, run this on the server: echo 'ALLOW_RUN=1' >> ~/.config/claude-launcher/config"
}

migrate_last_lines() {   # the last few lines of a file, colours stripped, on one line
  tail -n 5 "$1" 2>/dev/null | sed 's/\x1b\[[0-9;?]*[A-Za-z]//g; s/\r//g' | grep -v '^[[:space:]]*$' | tr '\n' ' ' | cut -c1-500
}

# migrate_status_write <job> <phase> <message> <pct> <bytes> <file> <started> <error>
# (pid = MIGRATE_PID while a worker is writing; migrate-status never shows it)
migrate_status_write() {
  local tmp
  mkdir -p "$MIGRATE_STATE" && chmod 700 "$MIGRATE_STATE" || return 1
  tmp="$(mktemp "$MIGRATE_STATE/status.XXXXXX")" || return 1
  if jq -cn --arg job "$1" --arg phase "$2" --arg message "$3" --argjson pct "$4" --argjson bytes "$5" \
    --arg file "$6" --argjson started "$7" --arg error "$8" --argjson pid "${MIGRATE_PID:-null}" --argjson updated "$(date +%s)" \
    '{job:$job, phase:$phase, message:$message, pct:$pct, bytes:$bytes, file:$file, started:$started, updated:$updated, error:$error, pid:$pid}' >"$tmp"; then
    mv -f "$tmp" "$MIGRATE_STATUS"
  else
    rm -f "$tmp"
    return 1
  fi
}

# Is the worker named in status.json still running? (A worker that never wrote its pid counts for a minute.)
migrate_job_alive() {
  local pid updated
  pid="$(jq -r '(.pid // "") | tostring' "$MIGRATE_STATUS" 2>/dev/null)"
  if [[ "$pid" =~ ^[0-9]+$ ]]; then
    kill -0 "$pid" 2>/dev/null && grep -aq -- '--migrate-worker' "/proc/$pid/cmdline" 2>/dev/null
  else
    updated="$(jq -r '.updated // 0' "$MIGRATE_STATUS" 2>/dev/null)"
    [[ "$updated" =~ ^[0-9]+$ ]] || updated=0
    (($(date +%s) - updated < 60))
  fi
}

# 0 when status.json describes a job that is still running.
migrate_job_active() {
  [[ -s "$MIGRATE_STATUS" ]] || return 1
  case "$(jq -r '.phase // ""' "$MIGRATE_STATUS" 2>/dev/null)" in
    export | transfer | restore) migrate_job_alive ;;
    *) return 1 ;;
  esac
}

# Takes the one-job-at-a-time lock (fd 8, released when this action exits) and refuses while a job runs.
migrate_take_job() {
  mkdir -p "$MIGRATE_STATE" "$MIGRATE_CFG" "$MIGRATE_BACKUPS" && chmod 700 "$MIGRATE_STATE" "$MIGRATE_CFG" "$MIGRATE_BACKUPS" ||
    api_err internal "Couldn't create the migration folders."
  exec 8>"$MIGRATE_STATE/job.lock"
  flock -n 8 || api_err busy "A migration step is already running."
  migrate_job_active && api_err busy "A migration step is already running."
  return 0
}

# migrate-plan: what a move would take (claude-backup's plan, plus the running sessions and connected services).
do_migrate_plan() {
  [[ $# -eq 0 ]] || bad_args "migrate-plan takes no arguments"
  migrate_need_backup
  local plan sessions svc
  plan="$(t 120 "$(migrate_backup_bin)" plan --json 2>/dev/null </dev/null)" || api_err internal "claude-backup couldn't check this server."
  jq -e 'type == "object"' >/dev/null 2>&1 <<<"$plan" || api_err internal "claude-backup gave no usable plan."
  sessions="$(sessions_list_json | jq -c 'map({name, busy, waiting})')"
  svc="$(services_json | jq -c 'to_entries | map(select(.value.logged_in == true) | .key)')"
  api_ok "$(jq -c --argjson s "${sessions:-[]}" --argjson v "${svc:-[]}" '{
    sessions:$s, repos:(.repos // []), logins:(.logins // {claude:false, workers:[]}), services:$v,
    docker_volumes:(.docker_volumes // []), estimate_mb:(.estimate_mb // 0), other_dirs:(.other_dirs // [])}' <<<"$plan")"
}

# migrate-keygen: the ephemeral key this server uses to push the backup (kept between calls).
do_migrate_keygen() {
  [[ $# -eq 0 ]] || bad_args "migrate-keygen takes no arguments"
  umask 077
  need ssh-keygen
  local key="$MIGRATE_CFG/key" pub
  mkdir -p "$MIGRATE_CFG" && chmod 700 "$MIGRATE_CFG" || api_err internal "Couldn't create $MIGRATE_CFG."
  if [[ ! -s "$key" || ! -s "$key.pub" ]]; then
    rm -f "$key" "$key.pub"
    ssh-keygen -q -t ed25519 -N '' -C clauderc-migrate -f "$key" </dev/null >/dev/null 2>&1 || api_err internal "Couldn't create the transfer key."
  fi
  chmod 600 "$key"
  pub="$(awk 'NR == 1 {print $1, $2, "clauderc-migrate"}' "$key.pub")"
  [[ "$pub" =~ ^ssh-ed25519\ [A-Za-z0-9+/]+=*\ clauderc-migrate$ ]] || api_err internal "The transfer key is unreadable: delete $MIGRATE_CFG and try again."
  api_ok "$(jq -cn --arg k "$pub" '{public_key:$k}')"
}

# migrate-authorize: stdin = the From server's public key line, then (optional) its address.
# The key may only run `claude-backup receive`; an older clauderc-migrate line is replaced.
do_migrate_authorize() {
  [[ $# -eq 0 ]] || bad_args "migrate-authorize reads the key on stdin"
  local key addr blob opts tmp ak="$HOME/.ssh/authorized_keys"
  key="$(read_secret_line)"; addr="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  [[ "$key" =~ $MIGRATE_PUBKEY_RE && ${#key} -le 300 ]] || api_err invalid_name "That isn't a public key line (ssh-ed25519 AAAA…)."
  [[ -z "$addr" || "$addr" =~ $MIGRATE_ADDR_RE ]] || api_err invalid_name "That isn't a host name or IP address."
  [[ "$HOME" =~ ^/[A-Za-z0-9._/-]+$ ]] || api_err internal "The home folder name can't be put in an authorized_keys option."
  [[ -x "$HOME/.local/bin/claude-backup" ]] || api_err not_configured "claude-backup is missing: run Update now"
  blob="$(awk '{print $2}' <<<"$key")"
  opts="restrict,command=\"$HOME/.local/bin/claude-backup receive\""
  # from= only for a literal IP (sshd compares it with the client's IP, it doesn't resolve names): a host name would lock the sender out.
  addr="${addr,,}"
  if migrate_is_ip "$addr"; then opts+=",from=\"$addr\""; fi
  [[ -d "$HOME/.ssh" ]] || mkdir -m 700 "$HOME/.ssh" || api_err internal "Couldn't create ~/.ssh."
  # An existing file is copied first (minus the older clauderc-migrate line); if that fails it is left exactly as it was.
  if [[ -e "$ak" && ( ! -f "$ak" || ! -r "$ak" ) ]]; then
    api_err internal "Your authorized_keys file can't be read: left as it is."
  fi
  tmp="$(mktemp "$HOME/.ssh/authorized_keys.XXXXXX")" || api_err internal "Couldn't write ~/.ssh/authorized_keys."
  if [[ -f "$ak" ]] && ! awk '$NF != "clauderc-migrate"' "$ak" >"$tmp"; then
    rm -f "$tmp"
    api_err internal "Couldn't read ~/.ssh/authorized_keys: left as it is."
  fi
  if printf '%s ssh-ed25519 %s clauderc-migrate\n' "$opts" "$blob" >>"$tmp" && chmod 600 "$tmp" && mv -f "$tmp" "$ak"; then
    api_ok '{"authorized":true}'
  fi
  rm -f "$tmp"
  api_err internal "Couldn't write ~/.ssh/authorized_keys."
}

# A literal IPv4 (no leading zeros) or IPv6 address, nothing else.
migrate_is_ip() {
  local o='(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])'
  [[ "$1" =~ ^$o\.$o\.$o\.$o$ ]] && return 0
  [[ "$1" == *:*:* && "$1" =~ ^[0-9a-f:]{2,39}$ && "$1" != *:::* ]]
}

# ---- the detached jobs (internal: started as `claude-setup.sh --migrate-worker`, not reachable through the runner)

MIGRATE_JOB="" MIGRATE_STARTED=0 MIGRATE_FILE="" MIGRATE_FINISHED=0 MIGRATE_CHILD=""
MIGRATE_ON_FAIL=() MIGRATE_ALWAYS=()   # files removed when the job fails / whenever it ends

migrate_progress() { migrate_status_write "$MIGRATE_JOB" "$1" "$2" "$3" "$4" "$MIGRATE_FILE" "$MIGRATE_STARTED" ""; }

migrate_fail() {
  local f pct=0 bytes=0
  read -r pct bytes < <(jq -r '[(.pct // 0), (.bytes // 0)] | @tsv' "$MIGRATE_STATUS" 2>/dev/null) || true
  for f in "${MIGRATE_ON_FAIL[@]}" "${MIGRATE_ALWAYS[@]}"; do rm -f -- "$f"; done
  MIGRATE_FINISHED=1
  migrate_status_write "$MIGRATE_JOB" failed "Failed" "${pct:-0}" "${bytes:-0}" "$MIGRATE_FILE" "$MIGRATE_STARTED" "$1"
  exit 1
}

migrate_worker_exit() {
  local f
  [[ -z "$MIGRATE_CHILD" ]] || kill "$MIGRATE_CHILD" 2>/dev/null
  ((MIGRATE_FINISHED == 1)) || migrate_fail "The job stopped unexpectedly"
  for f in "${MIGRATE_ALWAYS[@]}"; do rm -f -- "$f"; done
}

# PROGRESS lines (CLAUDE_BACKUP_PROGRESS=1) on stdin; anything else is kept in $1 for the error text.
# $2 = send|restore decides how a phase's percent maps onto the job's 0-100.
migrate_read_progress() {
  local log="$1" kind="$2" line ph p lo hi msg
  while IFS= read -r line; do
    line="${line//$'\r'/}"
    if [[ "$line" =~ ^PROGRESS[[:space:]]+([A-Za-z_-]+)[[:space:]]+([0-9]{1,3})([[:space:]].*)?$ ]]; then
      ph="${BASH_REMATCH[1]}"; p=$((10#${BASH_REMATCH[2]})); ((p > 100)) && p=100
      if [[ "$kind" == send ]]; then
        lo=0 hi=60 msg="Packing up this server ($ph)"
      else
        case "$ph" in
          verify)  lo=0  hi=20 msg="Checking the file" ;;
          extract) lo=20 hi=70 msg="Unpacking" ;;
          clone)   lo=70 hi=99 msg="Cloning the repos" ;;
          *)       lo=0  hi=99 msg="Restoring ($ph)" ;;
        esac
      fi
      migrate_progress "$([[ "$kind" == send ]] && echo export || echo restore)" "$msg" $((lo + p * (hi - lo) / 100)) 0
    elif [[ -n "$line" ]]; then
      printf '%s\n' "$line" >>"$log"
    fi
  done
}

# migrate_worker_send <host> <port> <user> <host-key type> <known_hosts file> <out file> <started>
migrate_worker_send() {
  local host="$1" port="$2" user="$3" ktype="$4" kh="$5" out="$6" started="$7"
  local pf="$MIGRATE_CFG/passphrase" errlog sshout rc size pos pct msg name hostalg="$4" t0
  MIGRATE_JOB=send MIGRATE_STARTED="$started"
  errlog="$(mktemp "$MIGRATE_STATE/err.XXXXXX")"; sshout="$(mktemp "$MIGRATE_STATE/out.XXXXXX")"
  MIGRATE_ON_FAIL=("$out"); MIGRATE_ALWAYS=("$kh" "$errlog" "$sshout")
  migrate_progress export "Packing up this server" 0 0

  CLAUDE_BACKUP_PROGRESS=1 "$(migrate_backup_bin)" export --no-claude-login --pass-file "$pf" --out "$out" </dev/null 2>&1 >/dev/null |
    migrate_read_progress "$errlog" send
  rc="${PIPESTATUS[0]}"
  ((rc == 0)) || migrate_fail "The export failed: $(migrate_last_lines "$errlog")"
  [[ -s "$out" ]] || migrate_fail "The export produced no file."
  size="$(stat -c %s "$out")"

  : >"$errlog"
  migrate_progress transfer "Sending to the new server" 60 0
  [[ "$ktype" == ssh-rsa ]] && hostalg="rsa-sha2-512,rsa-sha2-256,ssh-rsa"
  ssh -F /dev/null -i "$MIGRATE_CFG/key" -o BatchMode=yes -o IdentitiesOnly=yes -o IdentityAgent=none \
    -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$kh" -o GlobalKnownHostsFile=/dev/null -o UpdateHostKeys=no \
    -o "HostKeyAlgorithms=$hostalg" -o ConnectTimeout=30 -o ServerAliveInterval=30 -o ServerAliveCountMax=10 \
    -o ForwardAgent=no -o LogLevel=ERROR -T -p "$port" "$user@$host" receive <"$out" >"$sshout" 2>"$errlog" &
  MIGRATE_CHILD=$!
  t0=$SECONDS
  while kill -0 "$MIGRATE_CHILD" 2>/dev/null; do
    # How far ssh has read the file: fd 0's offset.
    pos="$(awk '/^pos:/ {print $2}' "/proc/$MIGRATE_CHILD/fdinfo/0" 2>/dev/null)"
    if [[ "$pos" =~ ^[0-9]+$ ]] && ((size > 0)); then
      ((pos > size)) && pos="$size"
      pct=$((60 + 39 * pos / size)); msg="Sending to the new server: $((pos / 1048576)) of $((size / 1048576)) MB"
    else
      pos=0 pct=60 msg="Sending to the new server: $((SECONDS - t0)) s, $((size / 1048576)) MB"
    fi
    migrate_progress transfer "$msg" "$pct" "$pos"
    sleep 1
  done
  wait "$MIGRATE_CHILD"; rc=$?
  MIGRATE_CHILD=""
  ((rc == 0)) || migrate_fail "Couldn't send the file: $(migrate_last_lines "$errlog")"
  name="$(grep -oE 'incoming-[0-9]{8}-[0-9]{6}(-[0-9]{1,3})?\.gpg' "$sshout" | head -n 1 || true)"
  [[ -n "$name" ]] || migrate_fail "The new server didn't confirm that it got the file."
  MIGRATE_FINISHED=1
  migrate_status_write send "done" "Sent to the new server" 100 "$size" "$name" "$started" ""
}

# migrate_worker_restore <incoming file name> <pass file> <started>
migrate_worker_restore() {
  local name="$1" pf="$2" started="$3" file="$MIGRATE_BACKUPS/$1" errlog rc detail
  MIGRATE_JOB=restore MIGRATE_STARTED="$started" MIGRATE_FILE="$name"
  errlog="$(mktemp "$MIGRATE_STATE/err.XXXXXX")"
  MIGRATE_ALWAYS=("$pf" "$errlog")   # the incoming file stays after a failure, so a retry works
  migrate_progress restore "Checking the file" 0 0

  CLAUDE_BACKUP_PROGRESS=1 "$(migrate_backup_bin)" import "$file" --pass-file "$pf" --clone \
    --manifest-out "$MIGRATE_STATE/manifest.json" </dev/null 2>&1 | migrate_read_progress "$errlog" restore
  rc="${PIPESTATUS[0]}"
  if ((rc != 0)); then
    detail="$(migrate_last_lines "$errlog")"
    if grep -qiE 'decrypt|passphrase|bad session key' "$errlog" 2>/dev/null; then
      migrate_fail "The passphrase didn't work (decryption failed). Check it and try again. $detail"
    fi
    migrate_fail "The restore failed: $detail"
  fi
  rm -f -- "$file"
  MIGRATE_FINISHED=1
  MIGRATE_FILE=""
  migrate_status_write restore "done" "Restored" 100 0 "" "$started" ""
}

migrate_worker() {
  local kind="${1:-}"
  shift || true
  umask 077
  MIGRATE_PID=$$
  trap migrate_worker_exit EXIT
  trap 'exit 143' TERM INT HUP
  case "$kind" in
    send) migrate_worker_send "$@" ;;
    restore) migrate_worker_restore "$@" ;;
    *) MIGRATE_FINISHED=1; exit 2 ;;
  esac
}

# migrate-send: stdin = host, port, user, host-key type, host-key base64 (the To server as the app pinned it).
do_migrate_send() {
  [[ $# -eq 0 ]] || bad_args "migrate-send reads the target on stdin"
  migrate_need_run
  local host port user ktype blob kh out pass started hostpat
  host="$(read_secret_line)"; port="$(read_secret_line)"; user="$(read_secret_line)"
  ktype="$(read_secret_line)"; blob="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  [[ "$host" =~ $MIGRATE_HOST_RE ]] || api_err invalid_name "That isn't a host name or IP address."
  [[ "$port" =~ ^[0-9]{1,5}$ ]] && ((10#$port >= 1 && 10#$port <= 65535)) || api_err invalid_name "The port must be 1-65535."
  port=$((10#$port))
  [[ "$user" =~ $MIGRATE_USER_RE ]] || api_err invalid_name "That isn't a valid user name."
  [[ "$ktype" =~ $MIGRATE_HOSTKEY_TYPE_RE ]] || api_err invalid_name "That host key type isn't supported."
  [[ "$blob" =~ $MIGRATE_BLOB_RE ]] || api_err invalid_name "That host key isn't valid base64."
  migrate_need_backup
  need ssh
  migrate_take_job
  [[ -s "$MIGRATE_CFG/key" ]] || api_err not_ready "The transfer key doesn't exist yet (migrate-keygen)."

  # known_hosts with exactly the one pinned key: no other host is accepted.
  host="${host,,}"
  if ((port == 22)); then hostpat="$host"; else hostpat="[$host]:$port"; fi
  kh="$MIGRATE_CFG/known_hosts"
  printf '%s %s %s\n' "$hostpat" "$ktype" "$blob" >"$kh" || api_err internal "Couldn't write the pinned host key."

  # Five groups of four, no look-alike characters (I, O, 0, 1).
  pass="$(LC_ALL=C tr -dc 'A-HJ-NP-Z2-9' </dev/urandom | head -c 20 || true)"
  ((${#pass} == 20)) || api_err internal "Couldn't generate a passphrase."
  printf '%s-%s-%s-%s-%s\n' "${pass:0:4}" "${pass:4:4}" "${pass:8:4}" "${pass:12:4}" "${pass:16:4}" >"$MIGRATE_CFG/passphrase.new" &&
    mv -f "$MIGRATE_CFG/passphrase.new" "$MIGRATE_CFG/passphrase" || api_err internal "Couldn't save the passphrase."
  unset pass

  started="$(date +%s)"
  out="$MIGRATE_BACKUPS/migrate-$(date +%Y%m%d-%H%M%S).gpg"
  migrate_status_write send export "Starting the export" 0 0 "" "$started" "" || api_err internal "Couldn't write the status."
  # Detach fully: fd 3 is the SSH channel, 8 the job lock.
  setsid "$SCRIPT_PATH" --migrate-worker send "$host" "$port" "$user" "$ktype" "$kh" "$out" "$started" \
    </dev/null >/dev/null 2>&1 3>&- 8>&- 9>&- &
  api_ok '{"started":true}'
}

# migrate-passphrase: the passphrase migrate-send generated (the app shows it to Jonathan to type on the new server).
do_migrate_passphrase() {
  [[ $# -eq 0 ]] || bad_args "migrate-passphrase takes no arguments"
  migrate_need_run
  [[ -s "$MIGRATE_CFG/passphrase" ]] || api_err not_ready "The export hasn't started yet."
  api_ok "$(jq -cn --rawfile p "$MIGRATE_CFG/passphrase" '{passphrase:($p | rtrimstr("\n"))}')"
}

# migrate-status: where the current job is. A job whose worker died without finishing is reported (and recorded) as failed.
do_migrate_status() {
  [[ $# -eq 0 ]] || bad_args "migrate-status takes no arguments"
  if [[ -s "$MIGRATE_STATUS" ]] && jq -e 'type == "object" and has("phase")' "$MIGRATE_STATUS" >/dev/null 2>&1; then
    case "$(jq -r '.phase' "$MIGRATE_STATUS")" in
      export | transfer | restore)
        if ! migrate_job_alive; then
          rm -f "$MIGRATE_STATE"/restore-pass.*   # a dead restore leaves its passphrase file behind
          jq -c '.phase = "failed" | .message = "Failed" | .error = "The job stopped unexpectedly" | .updated = (now | floor) | .pid = null' \
            "$MIGRATE_STATUS" >"$MIGRATE_STATUS.new" && mv -f "$MIGRATE_STATUS.new" "$MIGRATE_STATUS"
        fi
        ;;
    esac
    api_ok "$(jq -c 'del(.pid)' "$MIGRATE_STATUS")"
  fi
  api_ok '{"job":"none","phase":"none","message":"","pct":0,"bytes":0}'
}

# migrate-restore <incoming file>: stdin = the passphrase. Runs claude-backup import --clone in the background.
do_migrate_restore() {
  [[ $# -eq 1 && "$1" =~ $MIGRATE_INCOMING_RE ]] || bad_args "usage: migrate-restore <incoming-….gpg> (passphrase on stdin)"
  local name="$1" pass pf started
  pass="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  [[ "$pass" =~ ^[[:print:]]{8,200}$ ]] || api_err invalid_name "That passphrase doesn't look right."
  migrate_need_backup
  [[ -f "$MIGRATE_BACKUPS/$name" && ! -L "$MIGRATE_BACKUPS/$name" ]] || api_err invalid_name "There is no $name in ~/backups."
  migrate_take_job
  pf="$(mktemp "$MIGRATE_STATE/restore-pass.XXXXXX")" || api_err internal "Couldn't save the passphrase."
  printf '%s\n' "$pass" >"$pf"
  unset pass
  started="$(date +%s)"
  migrate_status_write restore restore "Starting the restore" 0 0 "$name" "$started" "" || { rm -f "$pf"; api_err internal "Couldn't write the status."; }
  setsid "$SCRIPT_PATH" --migrate-worker restore "$name" "$pf" "$started" </dev/null >/dev/null 2>&1 3>&- 8>&- 9>&- &
  api_ok '{"started":true}'
}

# ======================================================================
# Migrate, second half: sudo check, new user, verify, sign out, reboot (docs/migrate-design.md).
# The sudo password is read from stdin and reaches sudo only through the askpass helper (a mode-600 file in a
# private temp dir): never in argv, the environment or a log. Tests put stub sudo/adduser/systemctl/... on PATH.
# ======================================================================
MIGRATE_NAME_RE='^[a-z][a-z0-9_-]{0,30}$'
MIGRATE_RESERVED_RE='^(admin|root|daemon|bin|sys|sync|games|man|lp|mail|news|uucp|proxy|www-data|backup|list|irc|gnats|nobody|sshd|syslog|messagebus|operator|shutdown|halt|ftp|adm|sudo|wheel|docker|users|staff|postgres|mysql|redis|nginx|apache|tcpdump|polkitd|chrony|dnsmasq|avahi|tss|uuidd|lxd|landscape|pollinate|usbmux|systemd-.*)$'
MIGRATE_HOME_RE='^/[A-Za-z0-9._/-]+$'
MIGRATE_NOSUDO_RE='may not run sudo|not allowed to run sudo|not in the sudoers'
MIGRATE_SUDO=""    # root | nopasswd | password_ok | password_needed | none (set by migrate_sudo_mode)
MIGRATE_TMO=120    # seconds msudo / as_user allow one command
MIG_LOG=""         # where migrate_step keeps a step's output
MIG_STDIN=""       # a file migrate_step feeds the command on stdin

# migrate_sudo_mode <dir> <password>: sets MIGRATE_SUDO. A given password is tested through the askpass helper (one try).
migrate_sudo_mode() {
  local dir="$1" pw="$2" out
  if [[ "$(id -u)" == 0 ]]; then MIGRATE_SUDO=root; return 0; fi
  command -v sudo >/dev/null 2>&1 || { MIGRATE_SUDO=none; return 0; }
  if t 15 sudo -n true </dev/null >/dev/null 2>&1; then MIGRATE_SUDO=nopasswd; return 0; fi
  out="$(t 15 sudo -n -l </dev/null 2>&1)"
  if [[ "$out" =~ $MIGRATE_NOSUDO_RE ]]; then MIGRATE_SUDO=none; return 0; fi
  MIGRATE_SUDO=password_needed
  [[ -n "$pw" ]] || return 0
  sudo_askpass "$dir" "$pw" once   # (puts a `sudo -A` wrapper first on PATH)
  if out="$(t 30 sudo -k true </dev/null 2>&1)"; then MIGRATE_SUDO=password_ok
  elif [[ "$out" =~ $MIGRATE_NOSUDO_RE ]]; then MIGRATE_SUDO=none; fi
  return 0
}

# Stops the action unless sudo works (root, no password, or the password that was given); sets up the askpass for later calls.
migrate_need_sudo() {   # migrate_need_sudo <dir> <password>
  migrate_sudo_mode "$1" "$2"
  case "$MIGRATE_SUDO" in
    root | nopasswd) ;;
    password_ok) sudo_askpass "$1" "$2" ;;
    password_needed) api_err sudo_password "The sudo password is missing or wrong." ;;
    *) api_err sudo_none "This user can't use sudo on this server." ;;
  esac
}

# A command as root: bare for root, `sudo -n` when no password is needed, else sudo through the askpass wrapper.
msudo() {
  case "$MIGRATE_SUDO" in
    root) t "$MIGRATE_TMO" "$@" ;;
    nopasswd) t "$MIGRATE_TMO" sudo -n "$@" ;;
    *) t "$MIGRATE_TMO" sudo "$@" ;;
  esac
}

# as_user <name> <command...>: the command as that user, with their HOME.
as_user() {
  local u="$1"
  shift
  case "$MIGRATE_SUDO" in
    root)
      if command -v sudo >/dev/null 2>&1; then t "$MIGRATE_TMO" sudo -u "$u" -H "$@"
      else t "$MIGRATE_TMO" runuser -u "$u" -- env "HOME=$(getent passwd "$u" | cut -d: -f6)" "$@"; fi ;;
    nopasswd) t "$MIGRATE_TMO" sudo -n -u "$u" -H "$@" ;;
    *) t "$MIGRATE_TMO" sudo -u "$u" -H "$@" ;;
  esac
}

# migrate_step <id> <what> <command...>: output to $MIG_LOG; a failure ends the action and names the step.
migrate_step() {
  local id="$1" what="$2"
  shift 2
  "$@" >"$MIG_LOG" 2>&1 <"${MIG_STDIN:-/dev/null}" ||
    api_err internal "Couldn't $what: $(migrate_last_lines "$MIG_LOG")" "$(jq -cn --arg s "$id" '{step:$s}')"
}

# Is claude-autostart's boot service enabled (for user $1)? The unit dir override is for the tests.
migrate_autostart_enabled() {
  local unit="${CLAUDERC_UNIT_DIR:-/etc/systemd/system}/claude-sessions.service"
  command -v systemctl >/dev/null 2>&1 || return 1
  [[ "$(t 10 systemctl is-enabled claude-sessions.service 2>/dev/null </dev/null)" == enabled ]] || return 1
  [[ ! -r "$unit" ]] || grep -qx "User=$1" "$unit"
}

# migrate-sudo-check: how can this SSH user get root? stdin line 1 (optional) = a sudo password to test.
do_migrate_sudo_check() {
  [[ $# -eq 0 ]] || bad_args "migrate-sudo-check takes no arguments (an optional password on stdin)"
  migrate_need_run
  local pw dir
  pw="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  dir="$(mktemp -d)"
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
  migrate_sudo_mode "$dir" "$pw"
  unset pw
  api_ok "$(jq -cn --arg m "$MIGRATE_SUDO" '{mode:$m}')"
}

# migrate_prereqs: the tools the scripts and Claude need (jq tmux git curl gpg flock) and the GitHub CLI, from apt (other distros: the checklist says what is missing).
migrate_prereqs() {
  command -v apt-get >/dev/null 2>&1 || return 0
  export DEBIAN_FRONTEND=noninteractive
  msudo apt-get update -qq || true
  msudo apt-get install -y -qq jq tmux git curl ca-certificates gnupg util-linux || return 1
  command -v gh >/dev/null 2>&1 && return 0
  msudo apt-get install -y -qq gh && return 0
  # Older releases don't have gh: GitHub's own apt repository.
  msudo bash -c 'set -e; mkdir -p -m 755 /etc/apt/keyrings
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o /etc/apt/keyrings/githubcli-archive-keyring.gpg
    chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" >/etc/apt/sources.list.d/github-cli.list
    apt-get update -qq; apt-get install -y -qq gh'
}

# migrate-create-user <name>: stdin line 1 = sudo password (empty for root / no-password sudo), line 2 = the phone's public key,
# line 3 (optional) = a public key for a normal shell login (the app keeps the private half for the user to save).
# Every step can be repeated: a user an earlier try created (its root-owned marker file is there) is reused.
do_migrate_create_user() {
  [[ $# -eq 1 ]] || bad_args "usage: migrate-create-user <name> (sudo password, then the phone key, on stdin)"
  migrate_need_run
  local name="$1" pw key shellkey dir home pwent uid mark out blob line url inst akscript pending=true created=0
  pw="$(read_secret_line)"; key="$(read_secret_line)"; shellkey="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  [[ "$name" =~ $MIGRATE_NAME_RE && ! "$name" =~ $MIGRATE_RESERVED_RE ]] ||
    api_err invalid_name "That isn't a user name I can create (lower case letters, digits, - and _; not a system name)."
  [[ "$key" =~ $MIGRATE_PUBKEY_RE && ${#key} -le 300 ]] || api_err invalid_name "That isn't a public key line (ssh-ed25519 AAAA…)."
  [[ -z "${CLAUDERC_BASE:-}" || "$CLAUDERC_BASE" =~ ^[A-Za-z0-9:/._@%+=~-]{1,300}$ ]] || api_err internal "CLAUDERC_BASE has characters I won't pass on."
  [[ "$CLAUDERC_RAW" =~ ^[A-Za-z0-9:/._@%+=~-]{1,300}$ && "$CLAUDERC_REPO" =~ ^[A-Za-z0-9._/-]{1,100}$ ]] ||
    api_err internal "CLAUDERC_RAW or CLAUDERC_REPO has characters I won't pass on."
  [[ -z "$shellkey" || ( "$shellkey" =~ $MIGRATE_PUBKEY_RE && ${#shellkey} -le 300 ) ]] || api_err invalid_name "That isn't a public key line (ssh-ed25519 AAAA…)."
  dir="$(mktemp -d)"
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
  MIG_LOG="$dir/log"
  migrate_need_sudo "$dir" "$pw"
  unset pw
  MIGRATE_TMO=600 migrate_step prereqs "install jq, tmux, git, curl and gh" migrate_prereqs

  mark=".clauderc-migrate-user"
  pwent="$(getent passwd "$name" 2>/dev/null || true)"
  if [[ -n "$pwent" ]]; then
    # Already there: only a user this feature created is reused. Its marker file was written as root (a regular file
    # owned by root, mode 644), which the user can delete but can't forge.
    home="$(cut -d: -f6 <<<"$pwent")"; uid="$(cut -d: -f3 <<<"$pwent")"
    [[ "$uid" =~ ^[0-9]+$ && "$uid" -ge 1000 && "$home" =~ $MIGRATE_HOME_RE ]] || api_err user_exists "A user named $name already exists on this server."
    [[ "$(msudo stat -c '%U %a %F' -- "$home/$mark" 2>/dev/null)" == "root 644 regular file" ]] ||
      api_err user_exists "A user named $name already exists on this server (and wasn't created by Migrate)."
  else
    ! getent group "$name" >/dev/null 2>&1 || api_err user_exists "A group named $name already exists on this server."
    if command -v adduser >/dev/null 2>&1; then
      migrate_step adduser "create the user" msudo adduser --disabled-password --gecos "" "$name"
    else
      migrate_step adduser "create the user" msudo useradd -m -s /bin/bash "$name"
    fi
    created=1
    pwent="$(getent passwd "$name" 2>/dev/null || true)"
    home="$(cut -d: -f6 <<<"$pwent")"
    [[ "$home" =~ $MIGRATE_HOME_RE ]] || api_err internal "The user was created but I can't find the home folder." '{"step":"adduser"}'
    # The marker goes in first, as root, so a retry after any later failure finds the user again.
    migrate_step marker "write the marker file" msudo install -m 644 -o root -g root /dev/null "$home/$mark"
  fi
  # Whoever creates this user must not hand out root: a sudo rule (a catch-all group rule, say) deletes the new user again.
  if command -v sudo >/dev/null 2>&1; then
    out="$(msudo sudo -l -U "$name" 2>&1 </dev/null || true)"
    if ! [[ "$out" =~ $MIGRATE_NOSUDO_RE ]]; then
      if ((created)); then
        msudo userdel -r "$name" >"$MIG_LOG" 2>&1 || true
        api_err sudo_rule "The new user $name would have sudo rights on this server (a sudoers rule matches it), so it was deleted again. Pick another name or fix the sudoers rule." '{"step":"sudo_rule"}'
      fi
      api_err sudo_rule "The user $name has sudo rights on this server: Migrate won't use it." '{"step":"sudo_rule"}'
    fi
  fi
  if ((created)) && getent group docker >/dev/null 2>&1; then
    migrate_step docker "add $name to the docker group" msudo usermod -aG docker "$name"
  fi
  migrate_step home "find the home folder" msudo test -d "$home"

  # The phone key, locked to the runner exactly like install-launcher-key.sh does it (restrict + the forced command).
  # (The line goes in on stdin; a line with the same key is replaced.)
  blob="$(awk '{print $2}' <<<"$key")"
  line="restrict,command=\"$home/bin/claude-launcher-api\" ssh-ed25519 $blob clauderc"
  printf '%s\n' "$line" >"$dir/ak"
  read -r -d '' akscript <<'EOS' || true
umask 077
ak="$HOME/.ssh/authorized_keys"
mkdir -p "$HOME/.ssh" && chmod 700 "$HOME/.ssh" || exit 1
IFS= read -r line || exit 1
blob="$(awk '{ for (i = 1; i < NF; i++) if ($i == "ssh-ed25519") { print $(i + 1); exit } }' <<<"$line")"
[ -n "$blob" ] || exit 1
tmp="$(mktemp "$HOME/.ssh/authorized_keys.XXXXXX")" || exit 1
{ awk -v b="$blob" '{ for (i = 1; i <= NF; i++) if ($i == b) next } 1' "$ak" 2>/dev/null; printf '%s\n' "$line"; } >"$tmp" &&
  chmod 600 "$tmp" && mv -f "$tmp" "$ak" || { rm -f "$tmp"; exit 1; }
EOS
  MIG_STDIN="$dir/ak" migrate_step authorize "authorize the phone key" as_user "$name" bash -c "$akscript"
  if [[ -n "$shellkey" ]]; then   # a plain login key (no forced command): for ssh from a computer
    printf 'ssh-ed25519 %s clauderc-shell\n' "$(awk '{print $2}' <<<"$shellkey")" >"$dir/ak2"
    MIG_STDIN="$dir/ak2" migrate_step authorize_shell "authorize the login key" as_user "$name" bash -c "$akscript"
  fi

  # Claude Code (the official installer), then the cLaudeRC scripts without a key.
  if ! as_user "$name" test -x "$home/.local/bin/claude" >/dev/null 2>&1; then
    MIGRATE_TMO=300 migrate_step claude_install "install Claude Code" as_user "$name" bash -lc 'set -o pipefail; curl -fsSL https://claude.ai/install.sh | bash'
  fi
  if [[ -n "${CLAUDERC_BASE:-}" ]]; then url="$CLAUDERC_BASE/install.sh"; else url="$CLAUDERC_RAW/$CLAUDERC_REPO/main/server/install.sh"; fi
  inst="set -o pipefail; export CLAUDERC_RAW=$(printf %q "$CLAUDERC_RAW");"
  [[ -z "${CLAUDERC_BASE:-}" ]] || inst+=" export CLAUDERC_BASE=$(printf %q "$CLAUDERC_BASE");"
  inst+=" curl -fsSL $(printf %q "$url") | bash"
  # (its own sudo step for autostart can't ask anything: no terminal and stdin closed, so it declines)
  MIGRATE_TMO=300 migrate_step installer "install the cLaudeRC scripts" as_user "$name" bash -lc "$inst"
  # claude-autostart asks for the user's own sudo and this user has none: write its boot service as root instead (not fatal: no systemd, say).
  if ! migrate_autostart_enabled "$name" && [[ -x "$home/.local/bin/claude-autostart" ]]; then
    msudo env "CLAUDERC_AUTOSTART_USER=$name" "$home/.local/bin/claude-autostart" install >"$MIG_LOG" 2>&1 </dev/null || true
  fi
  ! migrate_autostart_enabled "$name" || pending=false
  api_ok "$(jq -cn --arg h "$home" --argjson p "$pending" --argjson k "$([[ -n "$shellkey" ]] && echo true || echo false)" '{created:true, home:$h, autostart_pending:$p, shell_key:$k}')"
}

# ---- migrate-verify

# migrate_vitem <id> <ok true|false> <detail>: adds a checklist item to MIGRATE_ITEMS.
MIGRATE_ITEMS="[]"
migrate_vitem() {
  MIGRATE_ITEMS="$(jq -c --arg id "$1" --argjson ok "$2" --arg d "$3" '. + [{id:$id, ok:$ok, detail:$d}]' <<<"$MIGRATE_ITEMS")"
}

migrate_names() {   # "a, b, c (+2 more)": the first 8 of the arguments
  local out="" i=0 x
  for x; do
    ((i++ < 8)) && out+="${out:+, }$x"
  done
  ((i > 8)) && out+=" (+$((i - 8)) more)"
  printf '%s' "$out"
}

# The folder a manifest repo has on this server: its path relative to $HOME (where claude-backup restores it), else under the projects folder.
migrate_repo_path() {
  local p="$1" d="$2"
  if [[ "$p" =~ ^[A-Za-z0-9._][A-Za-z0-9._/-]*$ && "/$p/" != *"/../"* && -d "$HOME/$p" ]]; then printf '%s' "$HOME/$p"; return 0; fi
  if [[ "$d" =~ $PROJECT_RE && "$d" != . && "$d" != .. && -d "$PROJECTS_DIR/$d" ]]; then printf '%s' "$PROJECTS_DIR/$d"; return 0; fi
  return 1
}

# migrate-verify [manifest file]: the checklist at the end of the wizard. Read-only, except that it removes the migrate key.
do_migrate_verify() {
  [[ $# -le 1 ]] || bad_args "usage: migrate-verify [manifest file name]"
  local mf="" f m w miss=() rest=() cfg_bad=() out v ak tmp
  if [[ $# -eq 1 ]]; then
    [[ "$1" =~ ^[A-Za-z0-9._-]{1,100}$ && "$1" != .* ]] || bad_args "That isn't a manifest file name."
    mf="$MIGRATE_STATE/$1"
    [[ -f "$mf" && ! -L "$mf" ]] || api_err invalid_name "There is no manifest named $1."
    jq -e 'type == "object"' "$mf" >/dev/null 2>&1 || api_err invalid_name "The manifest $1 can't be read."
  fi
  MIGRATE_ITEMS="[]"

  # scripts and API
  for f in "$HOME/claude-setup.sh" "$HOME/bin/claude-launcher-api" "$HOME/.local/bin/claude-backup" "$HOME/.local/bin/clauderc-team" "$HOME/.local/bin/claude-push"; do
    [[ -x "$f" ]] || miss+=("${f#"$HOME"/}")
  done
  if ((${#miss[@]} == 0)); then migrate_vitem scripts_installed true "all in place"
  else migrate_vitem scripts_installed false "missing: $(migrate_names "${miss[@]}"). Run Update now."; fi
  if ((SCRIPT_API >= 36)); then migrate_vitem script_api_current true "script API $SCRIPT_API"
  else migrate_vitem script_api_current false "script API $SCRIPT_API is older than 36: run Update now."; fi

  # folders and their modes
  if [[ -d "$HOME/.claude" && "$(stat -c %a "$HOME/.claude" 2>/dev/null)" == 700 ]]; then migrate_vitem claude_home true "the .claude folder is there (mode 700)"
  elif [[ -d "$HOME/.claude" ]]; then migrate_vitem claude_home false "the .claude folder has mode $(stat -c %a "$HOME/.claude" 2>/dev/null), it should be 700"
  else migrate_vitem claude_home false "the .claude folder is missing"; fi
  if [[ ! -d "$LAUNCHER_CONFIG_DIR" ]]; then cfg_bad+=("$LAUNCHER_CONFIG_DIR is missing")
  else
    [[ "$(stat -c %a "$LAUNCHER_CONFIG_DIR" 2>/dev/null)" == 700 ]] || cfg_bad+=("the launcher folder has mode $(stat -c %a "$LAUNCHER_CONFIG_DIR" 2>/dev/null), it should be 700")
    [[ ! -e "$SERVICES_ENV" || "$(stat -c %a "$SERVICES_ENV" 2>/dev/null)" == 600 ]] || cfg_bad+=("the env file has mode $(stat -c %a "$SERVICES_ENV" 2>/dev/null), it should be 600")
  fi
  if ((${#cfg_bad[@]} == 0)); then migrate_vitem launcher_config true "present, modes are right"
  else migrate_vitem launcher_config false "$(printf '%s; ' "${cfg_bad[@]}" | sed 's/; $//')"; fi

  # what the manifest says should be here
  if [[ -n "$mf" ]]; then
    miss=()
    while IFS= read -r w; do
      [[ "$w" =~ $WORKER_RE && -d "$WORKERS_DIR/$w" ]] || miss+=("$w")
    done < <(jq -r '(.claude_logins.workers // [])[]? | strings' "$mf")
    if ((${#miss[@]} == 0)); then migrate_vitem workers true "all worker folders are there"
    else migrate_vitem workers false "missing worker folders: $(migrate_names "${miss[@]}")"; fi

    local present=() broken=() rdir rpath rstate p
    miss=()
    while IFS=$'\x1f' read -r rdir rpath rstate; do
      if p="$(migrate_repo_path "$rpath" "$rdir")"; then
        present+=("$p")
        if [[ "$rstate" == clean ]] && ! git -C "$p" rev-parse HEAD >/dev/null 2>&1; then broken+=("${rdir:-$rpath}"); fi
      else
        miss+=("${rdir:-$rpath}")
      fi
    done < <(jq -r '(.repos // [])[]? | select(type == "object") | [(.dir // ""), (.path // ""), (.state // "")] | join("\u001f")' "$mf")
    if ((${#miss[@]} == 0 && ${#broken[@]} == 0)); then migrate_vitem repos true "${#present[@]} repo(s) present"
    else
      out=""
      ((${#miss[@]} == 0)) || out="missing: $(migrate_names "${miss[@]}")"
      ((${#broken[@]} == 0)) || out+="${out:+; }no commits (clone incomplete): $(migrate_names "${broken[@]}")"
      migrate_vitem repos false "$out"
    fi

    # remotes: the lookups run at once, 15 s at most, for the first 12 repos
    local td i=0 bad=()
    td="$(mktemp -d)"
    for p in "${present[@]:0:12}"; do
      f="$(git -C "$p" remote 2>/dev/null | grep -x origin || git -C "$p" remote 2>/dev/null | head -n 1)"
      [[ -n "$f" ]] || continue
      printf '%s\n' "$(basename -- "$p")" >"$td/$i.name"
      ( t 15 git -C "$p" ls-remote --heads "$f" </dev/null >/dev/null 2>&1; echo $? >"$td/$i.rc" ) 3>&- 9>&- &
      i=$((i + 1))
    done
    wait
    for ((m = 0; m < i; m++)); do
      [[ "$(cat "$td/$m.rc" 2>/dev/null)" == 0 ]] || bad+=("$(cat "$td/$m.name")")
    done
    rm -rf "$td"
    if ((i == 0)); then migrate_vitem remotes true "not checked"
    elif ((${#bad[@]} == 0)); then migrate_vitem remotes true "$i remote(s) reachable"
    else migrate_vitem remotes false "can't reach the remote of: $(migrate_names "${bad[@]}")"; fi
  fi

  # tools
  if ! command -v claude >/dev/null 2>&1; then migrate_vitem claude_runs false "claude isn't installed for this user"
  elif v="$(t 20 claude --version </dev/null 2>&1)"; then migrate_vitem claude_runs true "$(head -n 1 <<<"$v" | cut -c1-80)"
  else migrate_vitem claude_runs false "claude --version failed"; fi
  if ! command -v gh >/dev/null 2>&1; then migrate_vitem gh false "gh isn't installed for this user"
  elif t 20 gh auth status </dev/null >/dev/null 2>&1; then migrate_vitem gh true "signed in to GitHub"
  else migrate_vitem gh false "gh isn't signed in to GitHub"; fi
  if migrate_autostart_enabled "$(id -un)"; then migrate_vitem autostart true "sessions come back after a reboot"
  else migrate_vitem autostart false "claude-autostart isn't set up (no boot service): run 'claude-autostart install' on the server as this user; it asks for sudo"; fi

  # sign-ins the user still has to do (information only)
  if [[ -n "$mf" ]]; then
    rest=()
    [[ "$(jq -r '.claude_logins.main // false' "$mf")" != true ]] || creds_ok "$HOME/.claude/.credentials.json" || rest+=(main)
    while IFS= read -r w; do
      [[ "$w" =~ $WORKER_RE ]] || continue
      worker_signed "$WORKERS_DIR/$w" || rest+=("$w")
    done < <(jq -r '(.claude_logins.workers // [])[]? | strings' "$mf")
    if ((${#rest[@]} == 0)); then migrate_vitem pending_logins true "nothing to sign in"
    else migrate_vitem pending_logins true "still to sign in: $(migrate_names "${rest[@]}")"; fi
  fi

  # the transfer key must not stay authorized
  ak="$HOME/.ssh/authorized_keys"
  if [[ -f "$ak" ]] && awk '$NF == "clauderc-migrate" { f = 1 } END { exit !f }' "$ak"; then
    tmp="$(mktemp "$HOME/.ssh/authorized_keys.XXXXXX")"
    if [[ -n "$tmp" ]] && awk '$NF != "clauderc-migrate"' "$ak" >"$tmp" && chmod 600 "$tmp" && mv -f "$tmp" "$ak"; then migrate_vitem migrate_key_removed true removed
    else rm -f "$tmp"; migrate_vitem migrate_key_removed false "couldn't remove the transfer key from ~/.ssh/authorized_keys"; fi
  else
    migrate_vitem migrate_key_removed true "no transfer key found"
  fi
  api_ok "$(jq -c '{items:., ok:(all(.[]; .ok))}' <<<"$MIGRATE_ITEMS")"
}

# ---- migrate-clone <manifest file>: clone the repos the restore couldn't (e.g. GitHub wasn't signed in yet).
do_migrate_clone() {
  [[ $# -eq 1 && "$1" =~ ^[A-Za-z0-9._-]{1,100}$ && "$1" != .* ]] || bad_args "usage: migrate-clone <manifest file name>"
  local mf="$MIGRATE_STATE/$1" out rc=0
  [[ -f "$mf" && ! -L "$mf" ]] || api_err invalid_name "There is no manifest named $1."
  [[ -x "$HOME/.local/bin/claude-backup" ]] || api_err not_ready "claude-backup isn't installed here: run Update now."
  out="$(t 580 "$HOME/.local/bin/claude-backup" clone "$mf" </dev/null 2>&1)" || rc=$?
  ((rc == 0)) || api_err internal "Some repos still can't be cloned: $(migrate_last_lines <(printf '%s\n' "$out"))"
  api_ok "$(jq -cn --arg m "$(tail -n 1 <<<"$out")" '{message:$m}')"
}

# ---- migrate-signout-old <claude|workers|github|autostart...>: local files only; nothing is revoked on Anthropic's or GitHub's side.
do_migrate_signout_old() {
  [[ $# -ge 1 && $# -le 4 ]] || bad_args "usage: migrate-signout-old <claude|workers|github|autostart>..."
  local item seen=" " f n done_=() names=() list="$AUTOSTART_LIST"
  for item in "$@"; do
    [[ "$item" =~ ^(claude|workers|github|autostart)$ && "$seen" != *" $item "* ]] || bad_args "Each of claude, workers, github, autostart at most once."
    seen+="$item "
  done
  for item in "$@"; do
    case "$item" in
      claude)   # only the local token file (`claude auth logout` would revoke the grant)
        if [[ -e "$HOME/.claude/.credentials.json" ]]; then
          rm -f "$HOME/.claude/.credentials.json" || api_err internal "Couldn't delete ~/.claude/.credentials.json."
          done_+=(claude)
        fi ;;
      workers)
        n=0
        for f in "$WORKERS_DIR"/*/home/.credentials.json; do
          [[ -e "$f" ]] || continue
          rm -f "$f" || api_err internal "Couldn't delete $f."
          n=$((n + 1))
        done
        ((n == 0)) || done_+=(workers) ;;
      github)
        if command -v gh >/dev/null 2>&1 && t 20 gh auth status </dev/null >/dev/null 2>&1; then
          t 30 gh auth logout --hostname github.com </dev/null >/dev/null 2>&1 || api_err internal "gh couldn't sign out of GitHub."
          done_+=(github)
        fi ;;
      autostart)
        # Stop the sessions (the saved list and any running Claude session), then set the saved list aside so a restore does nothing.
        while IFS= read -r n; do names+=("$n"); done < <({ awk -F'\t' 'NF {print $1}' "$list" 2>/dev/null; sessions_list_json | jq -r '.[].name'; } | sort -u)
        for n in "${names[@]}"; do tmux kill-session -t "=$n" 2>/dev/null || true; done
        n=0
        if [[ -e "$list" ]]; then
          mv -f "$list" "$list.migrated" || api_err internal "Couldn't set the saved session list aside."
          n=1
        fi
        if ((n == 1 || ${#names[@]} > 0)); then done_+=(autostart); fi ;;
    esac
  done
  # Whatever was ticked: the transfer key (the private half), the passphrase files and the exported backups go.
  rm -rf -- "${MIGRATE_CFG:?}" || api_err internal "Couldn't delete the transfer key folder $MIGRATE_CFG."
  for f in "$MIGRATE_STATE"/restore-pass.* "$MIGRATE_BACKUPS"/migrate-*.gpg; do
    [[ -e "$f" || -L "$f" ]] || continue
    rm -f -- "$f" || api_err internal "Couldn't delete $f."
  done
  api_ok "$(jq -cn '{done:$ARGS.positional}' --args "${done_[@]}")"
}

# ---- migrate-reboot: stdin line 1 = sudo password. The answer goes out first; the reboot follows a few seconds later from a detached job.
do_migrate_reboot() {
  [[ $# -eq 0 ]] || bad_args "migrate-reboot takes no arguments (the sudo password on stdin)"
  migrate_need_run
  migrate_job_active && api_err busy "A migration step is still running: wait for it to finish before rebooting."
  local pw dir delay="${CLAUDERC_REBOOT_DELAY:-5}"
  pw="$(read_secret_line)"
  exec 0</dev/null
  umask 077
  [[ "$delay" =~ ^[0-9]{1,3}$ ]] || delay=5
  dir="$(mktemp -d)"
  trap 'rc=$?; rm -rf "$dir"; (exit $rc); on_exit' EXIT
  migrate_need_sudo "$dir" "$pw"
  unset pw
  # From here the detached job owns $dir (it holds the password file) and removes it after the reboot call.
  trap on_exit EXIT
  setsid bash -c '
    sleep "$2"
    case "$3" in root) c=() ;; nopasswd) c=(sudo -n) ;; *) c=(sudo) ;; esac
    if command -v systemctl >/dev/null 2>&1; then "${c[@]}" systemctl reboot; else "${c[@]}" reboot; fi
    rm -rf "$1"' _ "$dir" "$delay" "$MIGRATE_SUDO" </dev/null >/dev/null 2>&1 3>&- 8>&- 9>&- &
  api_ok '{"rebooting":true}'
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
    login-claude-code | login-github | login-aws-keys | login-gitlab | login-docker | run | claude-cmd | login-token | install-cli | set-secret | login-keystore | login-apple | youtube-login-start | \
      chat-pin-set | chat-open | chat-history | chat-send | chat-interrupt | chat-commands | chat-file | upload | mcp-auth-start | mcp-auth-finish | push-setup | worker-add | worker-set | worker-login-code | cluster-assign | \
      migrate-authorize | migrate-send | migrate-restore | migrate-sudo-check | migrate-create-user | migrate-reboot) ;;  # these read stdin
    *) exec 0</dev/null ;;
  esac

  case "$cmd" in
    status)              [[ $# -eq 0 ]] || bad_args "status takes no arguments"; do_status ;;
    owners)              [[ $# -eq 0 ]] || bad_args "owners takes no arguments"; do_owners ;;
    repos)               [[ $# -eq 0 || ( $# -eq 1 && "$1" == --refresh ) ]] || bad_args "usage: repos [--refresh]"; do_repos "$@" ;;
    push-config)         [[ $# -eq 0 ]] || bad_args "push-config takes no arguments"; do_push_config ;;
    push-setup)          [[ $# -eq 0 ]] || bad_args "push-setup reads the key on stdin"; do_push_setup ;;
    push-register)       do_push_register "$@" ;;
    push-session)        do_push_session "$@" ;;
    push-test)           [[ $# -eq 0 ]] || bad_args "push-test takes no arguments"; do_push_test ;;
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
    login-apple)         do_login_apple "$@" ;;
    remove-apple)        do_remove_apple "$@" ;;
    youtube-login-start) do_youtube_login_start "$@" ;;
    youtube-login-poll)  do_youtube_login_poll "$@" ;;
    repo-edit)           do_repo_edit "$@" ;;
    doctor-start)        do_doctor_start "$@" ;;
    cluster)             [[ $# -eq 0 ]] || bad_args "cluster takes no arguments"; do_cluster ;;
    worker-list)         [[ $# -eq 0 ]] || bad_args "worker-list takes no arguments"; do_worker_list ;;
    worker-add)          do_worker_add "$@" ;;
    worker-set)          do_worker_set "$@" ;;
    cluster-config)      do_cluster_config "$@" ;;
    cluster-config-set)  do_cluster_config_set "$@" ;;
    worker-remove)       do_worker_remove "$@" ;;
    worker-runs)         do_worker_runs "$@" ;;
    cluster-session)     do_cluster_session "$@" ;;
    cluster-attach)      do_cluster_attach "$@" ;;
    cluster-assign)      do_cluster_assign "$@" ;;
    worker-login-start)  do_worker_login_start "$@" ;;
    worker-login-code)   do_worker_login_code "$@" ;;
    mcp)                 do_mcp "$@" ;;
    mcp-refresh)         do_mcp_refresh "$@" ;;
    mcp-auth-start)      [[ $# -eq 0 ]] || bad_args "mcp-auth-start reads the name on stdin"; do_mcp_auth_start ;;
    mcp-auth-finish)     [[ $# -eq 0 ]] || bad_args "mcp-auth-finish reads the URL on stdin"; do_mcp_auth_finish ;;
    mcp-auth-cancel)     do_mcp_auth_cancel ;;
    plugins)             do_plugins "$@" ;;
    skills-update)       do_skills_update "$@" ;;
    migrate-plan)        do_migrate_plan "$@" ;;
    migrate-keygen)      do_migrate_keygen "$@" ;;
    migrate-authorize)   do_migrate_authorize "$@" ;;
    migrate-send)        do_migrate_send "$@" ;;
    migrate-passphrase)  do_migrate_passphrase "$@" ;;
    migrate-status)      do_migrate_status "$@" ;;
    migrate-restore)     do_migrate_restore "$@" ;;
    migrate-sudo-check)  do_migrate_sudo_check "$@" ;;
    migrate-create-user) do_migrate_create_user "$@" ;;
    migrate-verify)      do_migrate_verify "$@" ;;
    migrate-clone)       do_migrate_clone "$@" ;;
    migrate-signout-old) do_migrate_signout_old "$@" ;;
    migrate-reboot)      do_migrate_reboot "$@" ;;
    disconnect)         do_disconnect "$@" ;;
    chat-pin-status)     do_chat_pin_status "$@" ;;
    chat-pin-set)        do_chat_pin_set "$@" ;;
    chat-open)           do_chat_open "$@" ;;
    chat-history)        do_chat_history "$@" ;;
    chat-send)           do_chat_send "$@" ;;
    chat-interrupt)      do_chat_interrupt "$@" ;;
    chat-commands)       do_chat_commands "$@" ;;
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
    --migrate-worker) shift; migrate_worker "$@" ;;
    --mcp-refresh) mcp_refresh_now ;;
    --version) echo "$SCRIPT_API" ;;
    -h | --help) sed -n '2,10p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//' ;;
    "") menu_main ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
}

main "$@"
