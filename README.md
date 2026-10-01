# cLaudeRC

[![build](https://github.com/myGIGlife-claude/Claude-RC/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/myGIGlife-claude/Claude-RC/actions/workflows/build.yml)
[![latest build](https://img.shields.io/github/v/release/myGIGlife-claude/Claude-RC?include_prereleases&label=latest%20build)](https://github.com/myGIGlife-claude/Claude-RC/releases/tag/latest-main)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Server](https://img.shields.io/badge/server-bash%20%2B%20OpenSSH-4EAA25?logo=gnubash&logoColor=white)

**[⬇ Download the latest APK](https://github.com/myGIGlife-claude/Claude-RC/releases/tag/latest-main)**

<p align="center"><img src="docs/screens/demo.gif" width="300" alt="Illustrative mock-up: the Claude tab switching Accounts, Plugins and Tools, attaching workers to a chat with the 👥 button, tasks finishing on their own branches and being merged"><br><sub>Illustrative mock-up with example data, not a real recording.</sub></p>

Start and manage Claude Code **Remote Control** sessions on your own server
from your Android phone — check logins, create a new GitHub project or open an
existing one, and start Claude in it, without opening an SSH terminal.

**Features**

- Sessions and projects: start, stop, restart and open Claude Code sessions; create or clone GitHub projects.
- **Chat** with a session inside the app (behind a chat PIN): tool steps folded, Claude's questions as tappable options, a 🔊 switch with Play/Pause on every reply, files Claude sends, 📎 to send files; messages you queue survive leaving the chat.
- **cLaudeCluster**: connect several Claude accounts as workers, see each one's usage, and let the main Claude hand them work, one git branch per task (see below).
- Pill tabs split the busy screens: Claude = **Accounts / Plugins / Tools**, Connections = **Logins / MCP / Keys**, Settings = **Connection / Security / App**.
- Everything runs over SSH with the phone's own key: no open port, web server or cloud service.

- **App** (`app/`): Kotlin + Jetpack Compose, package `life.mygig.clauderc`.
- **Server** (`server/`): `claude-setup.sh` (menu + `--api` JSON mode), the
  forced-command runner `claude-launcher-api`, `install-launcher-key.sh`,
  `claude-autostart.sh` and the one-line `install.sh`.
  See [server/README.md](server/README.md).

## What you need

- A Linux server you can SSH into, with [Claude Code](https://claude.com/claude-code)
  (a claude.ai subscription login; Remote Control is used, never an API key).
- `bash`, `curl`, `jq`, `tmux`, `git` and `flock` on it. `gh` and `aws` are
  optional (the menu can install them).
- An Android phone, Android 8.0 or newer, and the Claude app to open the
  sessions.

## Screenshots

<table>
<tr>
<td><img src="docs/screens/sessions.png" width="200" alt="Sessions: each Claude session with its last lines; one waiting for an answer"></td>
<td><img src="docs/screens/chat.png" width="200" alt="In-app chat: bubbles, folded tool steps, the 👥 cluster button, speaker, attach"></td>
<td><img src="docs/screens/chat-cluster.png" width="200" alt="The 👥 sheet: attach workers to this chat, a role and a mode for each"></td>
<td><img src="docs/screens/session-log.png" width="200" alt="Session log: green terminal with answer keys"></td>
</tr>
<tr><td align="center">Sessions</td><td align="center">Chat</td><td align="center">Chat: 👥 workers</td><td align="center">Session log</td></tr>
<tr>
<td><img src="docs/screens/accounts.png" width="200" alt="Claude tab, Accounts: usage bars that fill with what is used"></td>
<td><img src="docs/screens/accounts-more.png" width="200" alt="More accounts: amber from 75% used, red from 90%, role, mode, tokens used on the server"></td>
<td><img src="docs/screens/plugins.png" width="200" alt="Claude tab, Plugins: installed plugins as tiles"></td>
<td><img src="docs/screens/tools.png" width="200" alt="Claude tab, Tools: doctor, full checkup, update, marketplaces"></td>
</tr>
<tr><td align="center">Claude: Accounts</td><td align="center">Accounts, scrolled</td><td align="center">Claude: Plugins</td><td align="center">Claude: Tools</td></tr>
<tr>
<td><img src="docs/screens/conn-logins.png" width="200" alt="Connections, Logins: core logins and services"></td>
<td><img src="docs/screens/conn-mcp.png" width="200" alt="Connections, MCP: servers with a live health dot"></td>
<td><img src="docs/screens/conn-keys.png" width="200" alt="Connections, Keys: signing keys and custom API keys"></td>
<td><img src="docs/screens/details.png" width="200" alt="A connection's details with edit, test and disconnect"></td>
</tr>
<tr><td align="center">Connections: Logins</td><td align="center">Connections: MCP</td><td align="center">Connections: Keys</td><td align="center">Details</td></tr>
<tr>
<td><img src="docs/screens/settings-connection.png" width="200" alt="Settings, Connection: server, host key, servers"></td>
<td><img src="docs/screens/settings-security.png" width="200" alt="Settings, Security: phone key, chat access log, app lock"></td>
<td><img src="docs/screens/settings-app.png" width="200" alt="Settings, App: notifications and theme"></td>
<td><img src="docs/screens/install-plugin.png" width="200" alt="Install a plugin from your marketplaces"></td>
</tr>
<tr><td align="center">Settings: Connection</td><td align="center">Settings: Security</td><td align="center">Settings: App</td><td align="center">Install a plugin</td></tr>
<tr>
<td><img src="docs/screens/projects.png" width="200" alt="Projects as tiles with Running / On server / All / Archived"></td>
<td><img src="docs/screens/add-service.png" width="200" alt="Connect a service: searchable A-Z list"></td>
<td><img src="docs/screens/run-command.png" width="200" alt="Run a command: terminal with scrollback and sudo password"></td>
<td><img src="docs/screens/chat-pin.png" width="200" alt="Chat PIN: asked on every open and after leaving the app"></td>
</tr>
<tr><td align="center">Projects</td><td align="center">+ Connect a service</td><td align="center">Run a command</td><td align="center">Chat PIN</td></tr>
</table>

Screens are shown with made-up example data (projects, accounts and hosts are not real). The Claude tab, chat, Connections and Settings pictures are illustrative mock-ups drawn with the app's dark theme; their source is in [docs/mockups](docs/mockups/) (regenerate with `docs/mockups/render.sh`: headless Chromium and Pillow).

## How it works

```
phone app ──SSH (its own Ed25519 key)──▶ sshd ──forced command──▶ claude-launcher-api
                                                                      │ allowlist + validation
                                                                      ▼
                                                     claude-setup.sh --api <action>  → one JSON object
```

Every call is short: connect, run one allowlisted action, read the JSON,
disconnect. The phone key can't open a shell, forward ports or run anything
off the allowlist (except **Run a command**, which is off unless you turn it on;
see Security). There is no web server, open port or cloud service.

## Screens

Five tabs, the same tile style throughout: **Sessions · Projects · New · Claude · Connections** (the app opens on Sessions; an update bar appears on top when a new build or server scripts are waiting).

| Tab | Shows | Actions |
| --- | --- | --- |
| **New** | Name (validated live), owner and visibility chips, “Start Claude now” | Create → repo link + **Open in Claude** |
| **Projects** | Your repos as tiles, newest first: running / on server / GitHub only, private/public, org | Chips: **Running** (default), **On server**, **All**, **Archived** (repos archived on GitHub, kept out of All); search; tap for Open in Claude, clone/pull + start, stop, log, GitHub, rename, make public/private, delete (type `delete` to confirm); long-press a running one for its log |
| **Sessions** | Each running Claude session with its last lines in green, busy/idle, and an amber **needs an answer** card when it's waiting on a question (e.g. approving a new MCP server) | **Chat** right in the app (behind a chat PIN: messages from Claude's own conversation, tool steps as short lines, tappable links, Claude's multiple-choice questions as tappable options, **Stop** while it works, a chip to switch model and one to cycle Claude's permission mode, a 🔊 switch that reads replies aloud (stays on until you turn it off) with Play/Pause under every reply, files Claude sends shown inline (image thumbnails, tap to zoom; **Save** for others), 📎 to send a file or photo into the project's `uploads/` folder; the same conversation as in the Claude app), **Open in Claude app** (menu), **Log** (a full-screen green-on-black terminal with A−/A+, live refresh and answer keys 1/2/3, ↑/↓, y/n, Esc, Enter), safe **Restart** / **Restart all** (reopens the same conversation, waits if Claude is busy), Stop; with App lock on, **Run a command** (terminal-style, type or paste, optional sudo password, keeps a scrollback of runs) |
| **Claude** | Claude Code's version and three pill tabs: **Accounts** (cLaudeCluster: every Claude account with usage bars that fill with what is used, plan, email, role, mode, tokens used on this server), **Plugins** (installed plugins as tiles, on / disabled), **Tools** (Doctor, Full checkup, Update Claude, other `claude` commands, marketplaces) | Accounts: **+ Add account** (signs in with a link and a code), **Sign in / Re-sign in**, **Mode**, **Tasks**, **Remove**. Plugins: tap a plugin to enable, disable, update or uninstall; **+ Install** searches every plugin across your marketplaces. Tools: **Doctor**, **Full checkup** (runs `/doctor` in its own session, shown in the chat), **Update Claude**, **+ Add marketplace** |
| **Connections** | Everything Claude on your server is connected to as tiles, in three pill tabs: **Logins** (Core: Claude, GitHub; Services: AWS, GitLab, Docker/GHCR, YouTube, Cloudflare, Vercel, MXroute, ...), **MCP** (found automatically: your own, plugin ones and claude.ai connectors, with a live health dot, refreshed every 5 min), **Keys** (signing keys, custom API keys). Server update card on top. | Tap a tile for its details: status, public info, the variable names sessions get (never values), **Edit** (new key), **Test now**, **Disconnect/Remove**. **+** (top right) connects a new service from a searchable A-Z list (AWS, GitLab, Docker/GHCR, Cloudflare, Vercel, Netlify, Fly.io, Railway, Supabase, Neon, npm, Stripe, Hugging Face, Backblaze B2, Google Cloud, Firebase, MXroute, Google Play Console, YouTube with a `youtube-upload` command for every session, an **Android signing key**, or any **Custom API key**); it installs the CLI if needed, then asks for credentials. |

**Servers**: add as many as you like; tap the server name under the title to
switch. All of them use this phone's one key.

**Settings** (gear, behind App lock) has three pill tabs. **Connection**: host, port, username, the pinned host key fingerprint, test connection, and your servers (add, switch, remove). **Security**: this phone's key (regenerate, with two warnings, then the new install command), the **chat access log**, app lock (fingerprint/PIN before create, stop, logins). **App**: session **notifications** (a session needs an answer or finished; off until you allow Android's notification permission) and theme. The first-run setup stays one page.

## cLaudeCluster

Use several Claude accounts from one chat. The main Claude hands work to the
others ("workers") and you stay in charge of what lands in the project.

1. **Accounts.** Claude tab > Accounts: **+ Add account** gives a worker a name
   and a role and signs it in. Each account shows usage bars that fill with
   what is *used* (amber from 75%, red from 90%), its plan, email, role, mode
   and tokens used on this server (last 5 h / 7 days).
2. **Attach per chat.** The 👥 button in a chat's header opens a sheet: switch
   on the workers this chat may use, set what each does here, and pick a mode:
   **Edit files**, **Read-only** or **Full access**.
3. **A branch per task.** The main Claude delegates through the `clauderc-team`
   MCP server (`list_workers`, `delegate`, `wait`, `reply`, `review`, `merge`,
   `discard`). In a project with a commit, each task runs in its own worktree
   on a branch `cluster/<worker>/<id>`, so workers don't share files with the
   main checkout or each other.
4. **Review and merge.** When a task finishes, the main Claude reads the diff
   and merges the branch (a conflict changes nothing) or discards it. Nothing
   reaches your branch until it is merged.

Needs the server scripts to be up to date (the app says so on Connections).

## Install on the phone

1. Download the APK from [latest-main](https://github.com/myGIGlife-claude/Claude-RC/releases/tag/latest-main)
   (every push to `main`) or a tagged [Release](https://github.com/myGIGlife-claude/Claude-RC/releases)
   on the phone and install it (allow installs from your browser when asked).
2. Open **cLaudeRC**. The setup screen shows one command with this phone's key
   already in it. Tap **Copy command** (or **Share** to send it to your computer).
3. Run it in a terminal on the server, as the user Claude should run as:
   ```bash
   curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash -s -- 'ssh-ed25519 AAAA… clauderc'
   ```
   It downloads the server scripts (no clone), sets up `claude-autostart`
   (session restore after reboot; asks for sudo once), authorizes the phone,
   and prints the Host, Port, Username and host-key fingerprints.
   Needs `curl`, `jq`, `tmux`, `git`, `flock`. Run it again any time to update.
4. In the app enter Host, Port, Username → **Connect** → check the fingerprint
   is one the command printed → **Trust**.

Updates install over the old version because every build is signed with the
same key.

## Updates

The app checks GitHub when it opens and every hour while it's open. A new
app build shows a bar across the top of every tab with **Install** (it
downloads the APK and opens Android's installer; the APK is deleted once the
new build starts, so none pile up on the phone). With notifications on, you
also get one notification per new build. Newer server scripts show the same
bar (**View**) and a card on Connections with **Update now** (the server runs
that commit's `install.sh`, after you confirm) or **Copy command** to paste on
the server yourself. `install.sh` records the commit it installed so the app
can compare. The app tells you when the server scripts need an update.

## Security

- The app generates its own Ed25519 key; the private part is encrypted with an
  Android Keystore AES-GCM key and never leaves the phone or its backups.
- The server pins that key to `claude-launcher-api` with `restrict` (no pty, no
  forwarding of any kind), so the key can only run that one program.
- **Run a command** is the one exception: it runs what you type as your user
  (and sudo with the password you enter). It only exists when App lock is on
  in the app **and** the server's config has `ALLOW_RUN=1`, so a lost phone
  can't enable it. Each run asks for your fingerprint/PIN. The sudo password
  reaches sudo through a private askpass file, never a command line or log.
- App lock is required: fingerprint, face or screen lock to open the app and
  again after 30 s in the background, and to open Settings (servers, this
  phone's key) and Run a command; it also hides the app from the Recents
  screen. Everyday actions (start, stop, restart, create, install, connect)
  don't ask again: they're what the Claude app itself does.
- In-app chat needs a second secret, a chat PIN (6–12 digits), asked when
  a chat opens and again after any trip away from the app, unless you chose
  "don't ask again" for 30 min / 1 h / 4 h. It exists only in
  `~/.config/claude-launcher/chat-pin` (mode 600) on the server, which is how
  you recover it; no action returns it and the app never stores it. Every chat
  call carries it and the server checks it; 5 wrong PINs lock chat for 30
  minutes (or until you delete `~/.config/claude-launcher/chat-locked`). Chat
  opens and sends are logged (without contents) in
  `~/.local/state/claude-launcher/chat.log`.
- The runner never uses `eval` or a shell on the request string; it only
  accepts known subcommands with validated arguments.
- Host key is pinned on first connect after you compare the fingerprint; a
  changed key is refused.
- Tokens, codes and AWS keys travel on stdin only, never on a command line, and
  the audit log records only time, action and result code.
- Service tokens (Cloudflare, Vercel, …) are checked with the provider, then
  kept in `~/.config/claude-launcher/env` (mode 600); JSON keys in mode-600
  files next to it. They reach Claude's sessions (commands and MCP servers)
  through the `env` block of `~/.claude/settings.json`, which is kept mode 600.
- Nothing personal is in this repo: server details live in the app's settings
  and in `~/.config/claude-launcher/config` on the server.

Found a security problem? Please open a
[private security advisory](https://github.com/myGIGlife-claude/Claude-RC/security/advisories/new)
instead of a public issue.

## Building

CI (`.github/workflows/build.yml`) runs gitleaks over the full history, the
server tests (including a real sshd), unit tests, and builds a signed release
APK. Tags `v*` publish a GitHub Release; pushes to `main` update the
`latest-main` pre-release.

Signing: releases are signed with the project's private key (from the
repository secrets) and CI fails any APK with a different certificate. Only
install APKs from this repo's Releases. Builds from pull requests or your own
machine use the public `app/debug.keystore`, are for testing only, and can't
update a release install. Forks: set the four `SIGNING_*` secrets to your own
key and change `EXPECTED_CERT_SHA256` in the workflow.

Locally:

```bash
./gradlew assembleDebug
./gradlew assembleRelease   # signed with the public debug key unless keystore.properties points at yours
```

## Contributing

Issues and pull requests are welcome. CI must pass: gitleaks, shellcheck, the
server tests (`server/tests/test-api.sh`, `server/tests/test-sshd.sh`) and the
Android build. Keep personal values (hosts, usernames, emails, keys) out of
code, docs and commits.

## License and credit

[MIT](LICENSE) © 2026 myGIGlife. Free to use, change, share and build on,
including commercially. The one condition: keep the copyright notice and the
license text in copies and forks, so the original project is credited.

If cLaudeRC helps you or you build on it, a link back to
<https://github.com/myGIGlife-claude/Claude-RC> is appreciated.
