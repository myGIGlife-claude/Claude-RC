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
| `status` | — | `claude.logged_in`, `github.{logged_in,user,missing_scopes}`, `aws.{logged_in,identity,profile,sso_configured}`, `services.{gitlab,docker,cloudflare}.{installed,logged_in,detail}`, `commit` (last commit that changed `server/`, recorded by `install.sh`), `script_api`, `hostname`, `version` |
| `owners` | — | `user`, `orgs[{login}]`, `default_owner` |
| `repos` | `[--refresh]` | `repos[{full_name,name,owner,owner_type,private,pushed_at,local,running,cloning}]` (cached for `REPOS_CACHE_TTL`). `local` means the folder's `origin` is this repo. |
| `sessions` | — | `sessions[{name,project,dir,started_at,attached,uptime_seconds}]` |
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
| `install-cli` | `glab` | `installed`, `name`, `version`. Latest glab release from gitlab.com, checked against its `checksums.txt`, into `~/.local/bin` (no sudo). |
| `login-gitlab` | token, host (default gitlab.com) on stdin | `user` (uses `glab auth login --stdin`) |
| `login-docker` | registry (default docker.io), username, token on stdin | `registry`, `user` (uses `docker login --password-stdin`) |
| `login-cloudflare` | API token on stdin | `logged_in`. Verified with Cloudflare, then saved as `CLOUDFLARE_API_TOKEN` in `~/.config/claude-launcher/env` (mode 600); one line in `~/.bashrc` loads it so Claude's sessions (and `wrangler`) see it. |

Error codes: `not_logged_in_claude`, `not_logged_in_github`, `missing_scopes`,
`not_logged_in_aws`, `not_logged_in` (GitLab / registry / Cloudflare), `repo_exists`, `folder_dirty`, `invalid_name`, `busy`,
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
