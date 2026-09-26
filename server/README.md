# cLaudeRC — server side

Everything the phone app talks to. The app connects over SSH with its own key,
and `authorized_keys` pins that key to one program, `claude-launcher-api`, so
the phone can run the allowlisted actions below and nothing else — no shell.

| File | What it does |
| --- | --- |
| `claude-setup.sh` | The interactive menu (no arguments) plus `--api <subcommand>`, which prints exactly one JSON object and never waits for input. |
| `claude-launcher-api` | Forced command. Splits `SSH_ORIGINAL_COMMAND`, checks it against the allowlist, re-validates every argument, runs `claude-setup.sh --api …`. Anything else → `{"ok":false,"error":{"code":"forbidden"}}`. |
| `install-launcher-key.sh` | Adds the phone's public key to `~/.ssh/authorized_keys` locked to the runner. |
| `claude-autostart.sh` | Restores the Claude sessions after a reboot. |
| `config.example` | Per-machine settings. The real file lives at `~/.config/claude-launcher/config` and is never committed. |

## Install

Needs `bash`, `jq`, `tmux`, `git`, `flock` (util-linux), and the CLIs you use:
`claude`, `gh`, `aws` (v2).

```bash
git clone https://github.com/myGIGlife-claude/Claude-RC.git
cd Claude-RC/server
mkdir -p ~/bin
install -m 755 claude-setup.sh claude-launcher-api claude-autostart.sh install-launcher-key.sh ~/bin/

# Keep a copy of your old script if you had one
[ -f ~/claude-setup.sh ] && cp ~/claude-setup.sh ~/claude-setup.sh.bak

~/bin/claude-setup.sh --api status | jq .   # first run creates ~/.config/claude-launcher/config
```

Edit `~/.config/claude-launcher/config` if your projects are not in `~/projects`
(see `config.example` for every option). If `claude`, `gh` or `aws` live
somewhere unusual, add that folder to `EXTRA_PATH`: forced-command SSH sessions
don't read your shell profile.

Restore sessions at boot (user crontab):

```bash
( crontab -l 2>/dev/null; echo '@reboot sleep 20 && $HOME/bin/claude-autostart.sh >/dev/null 2>&1' ) | crontab -
```

## Connect the phone

1. Install the app from the GitHub Releases page and open it. It generates its
   own Ed25519 key on first launch.
2. In the app, tap **Copy install cmd** (or **Copy** for just the key).
3. On the server:

   ```bash
   ~/bin/install-launcher-key.sh 'ssh-ed25519 AAAA… clauderc'
   ```

   That writes:

   ```
   command="/home/<you>/bin/claude-launcher-api",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-user-rc ssh-ed25519 AAAA… clauderc
   ```

4. In the app enter host, port and username and tap **Connect**. Compare the
   fingerprint it shows with the server's:

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
| `status` | — | `claude.logged_in`, `github.{logged_in,user,missing_scopes}`, `aws.{logged_in,identity,profile,sso_configured}`, `hostname`, `version` |
| `owners` | — | `user`, `orgs[{login,role}]`, `default_owner` |
| `repos` | `[--refresh]` | `repos[{full_name,name,owner,owner_type,private,pushed_at,local,running}]` (cached for `REPOS_CACHE_TTL`) |
| `sessions` | — | `sessions[{name,project,dir,started_at,attached,uptime_seconds}]` |
| `new` | `<name> --owner <owner> --visibility private\|public [--start]` | `repo`, `url`, `path`, `session` |
| `open` | `<owner/repo> [--start]` | `path`, `action` (`cloned`, `pulled`, `skipped_empty`, `cloning`), `pending`, `session` |
| `start` | `<project>` | `session`, `already_running` |
| `stop` | `<project>` | `session`, `stopped` |
| `tail` | `<project> [--lines N]` | `text` (last N lines, default 40, max 200) |
| `login-claude-start` | — | `url` |
| `login-claude-code` | code on stdin | `logged_in` |
| `login-claude-cancel` | — | `cancelled` |
| `login-github` | token on stdin | `user` (or error `missing_scopes` with `missing`) |
| `login-aws-keys` | key id, secret, region on stdin (one per line) | `account`, `arn`, `region` |
| `login-aws-sso-start` | — | `url`, `code` (then poll `status`) |

Error codes: `not_logged_in_claude`, `not_logged_in_github`, `missing_scopes`,
`not_logged_in_aws`, `repo_exists`, `folder_dirty`, `invalid_name`, `busy`,
`internal`, `bad_args`, and `forbidden` from the runner.

Notes:

- `new` and `open` take a `flock` lock; a second call while one runs gets `busy`.
- A clone that takes longer than ~45 s keeps running in the background
  (`action: "cloning"`, `pending: true`); it still starts Claude when it
  finishes if `--start` was given.
- Tokens, codes and keys only ever arrive on stdin. The runner's audit log
  (`~/.local/state/claude-launcher/api.log`, rotated at 1 MB) records time,
  subcommand and result code — never arguments.
- Sessions are named after the project (plus optional `SESSION_PREFIX`; `.` and
  `:` become `_` as tmux requires) and run
  `claude --remote-control "<project>"`. Every start — menu, phone or autostart —
  is recorded in `~/.local/state/claude-launcher/sessions.list`, which is what
  `claude-autostart.sh` replays after a reboot. Stopping a session removes it.
- `ANTHROPIC_API_KEY` is always unset: Claude uses the claude.ai subscription login.
- `login-claude-start` drives `claude auth login` in a tmux session named
  `claude-login`. If a login-method picker appears it chooses the claude.ai
  subscription entry by its label, not a hard-coded number. If a future Claude
  Code version changes the prompts, the error includes the pane text.

## Tests

```bash
server/tests/test-api.sh              # runner + every subcommand, with stub gh/claude/aws
sudo server/tests/test-sshd.sh        # real sshd: shells are forbidden, status works
sudo server/tests/test-sshd.sh --app  # also runs the app's SSH code against it
```
