# cLaudeRC — server side

Everything the phone app talks to. The app connects over SSH with its own key,
and `authorized_keys` pins that key to one program, `claude-launcher-api`, so
the phone can run the allowlisted actions below and nothing else — no shell.

| File | What it does |
| --- | --- |
| `claude-setup.sh` | The interactive menu (no arguments) plus `--api <subcommand>`, which prints exactly one JSON object and never waits for input. |
| `claude-launcher-api` | Forced command. Splits `SSH_ORIGINAL_COMMAND`, checks it against the allowlist, re-validates every argument, runs `claude-setup.sh --api …`. Anything else → `{"ok":false,"error":{"code":"forbidden"}}`. |
| `install.sh` | Downloads and installs the three scripts below without cloning, then authorizes the phone key if given. |
| `install-launcher-key.sh` | Adds the phone's public key to `~/.ssh/authorized_keys` locked to the runner. |
| `claude-autostart.sh` | Unchanged from the original: a systemd service + 2-minute timer that saves the running Claude tmux sessions and restores them at boot. Sessions started from the phone are picked up the same way. |
| `config.example` | Per-machine settings. The real file lives at `~/.config/claude-launcher/config` and is never committed. |

## Install

Needs `bash`, `jq`, `tmux`, `git`, `flock` (util-linux) and the CLIs the menu
already installs: `claude`, `gh`, `aws` (v2).

One command, no clone. It downloads `claude-setup.sh`, `claude-launcher-api`,
`install-launcher-key.sh` and `claude-autostart.sh`, backs up a changed
`~/claude-setup.sh`, installs them, sets up `claude-autostart` if it isn't
already (asks for sudo once), and prints what to enter in the app (rerun it to
update):

```bash
curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash
# or, with the key (the app's "Copy command" does this), also authorize the phone:
curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash -s -- 'ssh-ed25519 AAAA… clauderc'
```

The first run writes `~/.config/claude-launcher/config`; set `PROJECTS_DIR` there.

The old script hard-coded the projects folder; it now comes from
`PROJECTS_DIR` in `~/.config/claude-launcher/config` (default `~/projects`).
See `config.example` for the other options. If `claude`, `gh` or `aws` live
somewhere other than `~/.local/bin` or `/usr/local/bin`, add that folder to
`EXTRA_PATH`: SSH calls from the phone don't read your shell profile.

`./claude-setup.sh` with no arguments runs the same menu as before: tool
install, the three logins, then New / Existing project with `b`/`q` at every
prompt.

`install.sh` sets up `claude-autostart` only if it isn't installed yet. Already installed? Nothing to do — it detects sessions
by scanning tmux, and `claude-setup.sh` now also tells it to save right after
a start or stop instead of waiting for the 2-minute timer.

## Connect the phone

1. Install the app from the GitHub Releases page and open it. It generates its
   own Ed25519 key on first launch.
2. In the app, tap **Copy command** on the setup screen (or **Copy install
   command** in Settings) and run it on the server (the `install.sh` line above). If the scripts are already installed, just the key
   works too: `~/bin/install-launcher-key.sh 'ssh-ed25519 AAAA… clauderc'`.
3. That writes:

   ```
   restrict,command="/home/<you>/bin/claude-launcher-api" ssh-ed25519 AAAA… clauderc
   ```

4. In the app enter the host, port and username the command printed and tap
   **Connect**. Compare the fingerprint it shows with the ones the command
   printed, or:

   ```bash
   ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
   ```

   and tap **Trust**. From then on the app refuses to connect if the host key
   changes.

To revoke the phone: `~/bin/install-launcher-key.sh --remove 'ssh-ed25519 AAAA…'`.

Your existing SSH hardening (key-only auth, fail2ban, firewall) stays exactly as
it is; no new ports are opened.

## API

`claude-setup.sh --api <subcommand> [args]` → one line on stdout:
`{"ok":true,"data":{…}}` or `{"ok":false,"error":{"code":"…","message":"…"}}`.
Exit code 0 = success, 1 = handled error, 2 = bad arguments.

| Subcommand | Args | `data` |
| --- | --- | --- |
| `status` | — | `claude.logged_in`, `github.{logged_in,user,missing_scopes}`, `aws.{logged_in,identity,profile,sso_configured}`, `services.{gitlab,docker,cloudflare}.{installed,logged_in,detail}`, `commit` (last commit that changed `server/`, recorded by `install.sh`), `script_api`, `hostname` |
| `owners` | — | `user`, `orgs[{login}]`, `default_owner` |
| `repos` | `[--refresh]` | `repos[{full_name,name,owner,owner_type,private,pushed_at,local,running,cloning}]` (cached for `REPOS_CACHE_TTL`). `local` means the folder's `origin` is this repo. |
| `sessions` | — | `sessions[{name,project,dir,started_at,attached,uptime_seconds,preview,waiting,busy}]`: `preview` is the last lines (Claude's input box and status bar left out), `waiting` a question on screen, `busy` Claude working. |
| `new` | `<name> --owner <owner> --visibility private\|public [--start]` | `repo`, `url`, `path`, `visibility`, `session` |
| `open` | `<owner/repo> [--start]` | `repo`, `path`, `action` (`cloned`, `pulled`, `not_updated`, `cloning`), `pending`, `note`, `session` |
| `clone-status` | `<owner/repo>` | `state` (`running`, `done`, `failed`, `none`), `message`, `session`. A finished result is reported once. |
| `start` | `<project>` | `session`, `path`, `already_running` |
| `stop` | `<project or session>` | `session`, `stopped` |
| `tail` | `<project or session> [--lines N]` | `session`, `lines`, `text` (last N lines, default 40, max 200) |
| `login-claude-start` | — | `url`, `session` |
| `login-claude-code` | code on stdin | `logged_in` |
| `login-claude-cancel` | — | `cancelled` |
| `login-github` | token on stdin | `user` (or error `missing_scopes` with `missing`) |
| `login-aws-keys` | key id, secret, region on stdin (one per line) | `account`, `arn`, `region` |
| `login-aws-sso-start` | — | `url`, `code` (then poll `status`) |
| `self-update` | `<40-hex commit>` | `commit`. Downloads that commit's `install.sh` and runs it (the phone's **Update now** button). |
| `install-cli` | `glab`, `docker`, `supabase`, `flyctl`, `stripe`, `railway`, `neon`, `b2`, `vercel`, `netlify`, `firebase`, `hf`, `gcloud`, `bun`; stdin: sudo password (docker only) | `installed`, `name`, `version`. Into `~/.local/bin` without sudo (Docker: get.docker.com with the password via askpass). SHA-256 checked where the vendor publishes checksums (glab, supabase, flyctl, stripe, b2, bun). `install.sh` installs `bun` itself when it's missing (plugin hooks need it). npm-based ones need Node.js. |
| `login-token` | `<service>`; stdin: one value per line, or the whole service-account JSON (`gcp`, `firebase`) | `user`. Services: cloudflare, vercel, netlify, fly, railway, supabase, neon, npm, stripe, huggingface, b2, gcp, firebase, mxroute, googleplay. Checked with the provider's API, then saved to `~/.config/claude-launcher/env` (mode 600) and mirrored into the `env` block of `~/.claude/settings.json` (mode 600), so Claude's commands and MCP servers see them after a restart; JSON keys go to a mode-600 file. |
| `run` | stdin: sudo password line (may be empty), timeout seconds line, then the command | `exit_code`, `output` (stdout+stderr, last 64 KB). Off unless `ALLOW_RUN=1` in the config (error `run_disabled`). |
| `keys` | `<session> <key>…` (up to 5) | `session`, `text` (the screen after). Keys: `1`–`9`, `Enter`, `Escape`, `Up`, `Down`, `Tab`, `BTab` (Shift+Tab: cycles Claude's permission mode), `Space`, `y`, `n`; never text. For answering prompts such as approving a new MCP server. |
| `restart` | `<project or session> [--force]` | `session`, `path`, `restarted`, `resumed`, `conversation`, `waiting`, `text`. Safe restart: refuses with `session_busy` while Claude is working (unless `--force`), then starts it again in the same folder with `claude --resume <id>` so the same conversation comes back (falls back to a fresh start). `waiting` + `text` when the new session stops on a question, e.g. approving a new MCP server. |
| `claude-cmd` | stdin: the arguments after `claude` | `exit_code`, `output`. Only `doctor`, `update`, `--version`, `plugin …`, `plugin marketplace …`, `mcp list/get/remove`; no shell. |
| `login-keystore` / `remove-keystore` | `<NAME>` (upper case); stdin: alias, keystore password, key password, then the keystore base64 | `saved`, `alias`. Checked with `keytool` (or `openssl` for PKCS12), stored as `keystores/<NAME>.jks` (mode 600); sessions get `<NAME>_KEYSTORE_FILE`, `<NAME>_KEYSTORE_PASSWORD`, `<NAME>_KEY_ALIAS`, `<NAME>_KEY_PASSWORD`. `status` lists names as `keystores`. |
| `youtube-login-start` / `youtube-login-poll` | start: client ID and secret on stdin | start: `url`, `code`, `interval`; poll: `pending` until approved, then `user` (channel). Google's device flow (client type "TVs and Limited Input devices", scope `youtube`); saves `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_REFRESH_TOKEN`. `install.sh` puts `youtube-upload` (resumable uploader) in `~/.local/bin`. |
| `set-secret` / `remove-secret` | `<NAME>` (upper case, ending in `_KEY`, `_TOKEN`, `_SECRET`, …; never `CLAUDE_*`/`ANTHROPIC_*`); stdin: the value | `saved` / `removed`. Custom API keys, saved and mirrored like the services above; `status` lists their names as `custom`. |
| `mcp` | — | `servers[{name,label,scope,plugin,kind,target,health,detail}]`, `checked_seconds_ago`, `refreshing`. From `claude mcp list`, cached and refreshed in the background at most every 5 minutes (it starts every server). `scope`: user, project, plugin or claude.ai; `target` is a URL or just the program (arguments can hold secrets); `health`: connected, failed, needs_auth, unknown. |
| `mcp-refresh` | — | Same, checked now (~10 s). |
| `chat-file` | `<session>`; stdin: PIN, then a file path | `name`, `bytes`, `data` (base64). Only inside the session's project folder or Claude's temp folder for it, or a file Claude sent in this conversation (SendUserFile) from anywhere; up to 10 MB. `chat-history` shows a file Claude sent to the Claude app (SendUserFile) as `{role:"file", text: caption, files:[paths]}`. |
| `doctor-start` | — | `session`. Starts `claude /doctor` in a session of its own (`claude-doctor`, replacing an earlier one) for the app to open in the chat. |
| `repo-edit` | `delete <owner/repo>`, `rename <owner/repo> <new name>` or `visibility <owner/repo> private\|public` | `done`, `repo`, `value`. On GitHub only; a folder on the server keeps its name. Delete needs the `delete_repo` token scope (error `missing_scopes` with `missing:["delete_repo"]`). |
| `mcp-auth-start` | server name on stdin | `url` to sign in (a user or plugin server that needs it). |
| `mcp-auth-finish` | stdin: the `http://localhost…` address the browser ended on | Same as `mcp-refresh` once signed in. |
| `mcp-auth-cancel` | — | `cancelled` |
| `plugins` | — | `claude_version`, `installed[{id,name,marketplace,version,enabled,scope}]`, `available[{id,name,marketplace,description,installs,installed}]`, `marketplaces[{name,source}]` (from `claude plugin list --json --available`). |
| `disconnect` | `<service>` | `disconnected`. Deletes a token service's (or YouTube's) credentials and key file; sessions lose them on restart. |
| `chat-pin-status` | — | `set`, `locked_until`. |
| `chat-pin-set` | stdin: new PIN (6–12 digits), then the current one if set | `set`. Written to `~/.config/claude-launcher/chat-pin` (mode 600); never returned by any action. |
| `chat-open` | `<session>`; stdin: PIN | `session`. Checks the PIN (5 wrong → `chat_locked` for 30 min; `wrong_pin`, `pin_not_set`). |
| `chat-history` | `<session>`; stdin: PIN | `messages[{id,role,text,ts}]` (role user/assistant/tool, from Claude's transcript), `waiting`, `busy`, `screen` (the question when waiting). |
| `chat-send` | `<session>`; stdin: PIN, then the message | `sent`. Pasted into the session (bracketed paste, multi-line ok), then Enter. |
| `upload` | `<session>`; stdin: PIN, file name, file base64 (≤15 MB) | `path` (`uploads/<name>` in the project), `bytes`. |
| `chat-log` | — | The last 100 chat opens/sends/uploads/pin-sets (`ts`, `session`, `action`), newest first; never contents. |
| `chat-interrupt` | `<session>`; stdin: PIN | `interrupted` (Esc). |
| `login-gitlab` | token, host (default gitlab.com) on stdin | `user` (uses `glab auth login --stdin`) |
| `login-docker` | registry (default docker.io), username, token on stdin | `registry`, `user` (uses `docker login --password-stdin`) |

Error codes: `not_logged_in_claude`, `not_logged_in_github`, `missing_scopes`,
`not_logged_in_aws`, `not_logged_in` (GitLab / registry / Cloudflare), `repo_exists`, `folder_dirty`, `invalid_name`, `run_disabled`, `session_busy`, `wrong_pin`, `chat_locked`, `pin_not_set`, `busy`,
`internal`, `bad_args`, and `forbidden` from the runner.

`open` returns `action: "not_updated"` with a `note` when `git pull --ff-only`
can't fast-forward (it still starts the session, like the menu).

Notes:

- Project, repo and owner names never start with `-` (so they can't be read
  as command-line options); the runner and the script both reject them.

- `new` and `open` take a `flock` lock; a second call while one runs gets `busy`.
- A clone that takes longer than ~45 s keeps running in the background
  (`action: "cloning"`, `pending: true`); it still starts Claude when it
  finishes if `--start` was given.
- Tokens, codes and keys only ever arrive on stdin. The runner's audit log
  (`~/.local/state/claude-launcher/api.log`, rotated at 1 MB) records time,
  subcommand and result code — never arguments.
- Sessions are started exactly like the menu starts them: named after the
  project with `.` and `:` turned into `-`, folder pre-trusted in
  `~/.claude.json`, running
  `env -u ANTHROPIC_API_KEY claude --remote-control <project>; exec bash`.
  That is what `claude-autostart` looks for, so it saves and restores them.
- The very first Remote Control start asks "Enable Remote Control? (y/n)". The
  menu has you answer it by attaching; from the phone the script answers `y`
  itself and writes the same `~/.config/claude-setup/remote-control-confirmed`
  marker.
- `ANTHROPIC_API_KEY` is always unset: Claude uses the claude.ai subscription login.
- `login-claude-start` drives `claude auth login` in a tmux session named
  `claude-login`. If a login-method picker appears it chooses the claude.ai
  subscription entry by its label, not a hard-coded number. If a future Claude
  Code version changes the prompts, the error includes the pane text.

## Tests

```bash
server/tests/test-api.sh              # runner, every subcommand, autostart save/restore, menu (stub gh/claude/aws)
sudo server/tests/test-sshd.sh        # real sshd: shells are forbidden, status works
sudo server/tests/test-sshd.sh --app  # also runs the app's SSH code against it
```
