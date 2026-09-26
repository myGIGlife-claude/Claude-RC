# cLaudeRC

[![build](https://github.com/myGIGlife-claude/Claude-RC/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/myGIGlife-claude/Claude-RC/actions/workflows/build.yml)
[![latest build](https://img.shields.io/github/v/release/myGIGlife-claude/Claude-RC?include_prereleases&label=latest%20build)](https://github.com/myGIGlife-claude/Claude-RC/releases/tag/latest-main)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Server](https://img.shields.io/badge/server-bash%20%2B%20OpenSSH-4EAA25?logo=gnubash&logoColor=white)

**[⬇ Download the latest APK](https://github.com/myGIGlife-claude/Claude-RC/releases/tag/latest-main)**

Start and manage Claude Code **Remote Control** sessions on your own server
from your Android phone — check logins, create a new GitHub project or open an
existing one, and start Claude in it, without opening an SSH terminal.

- **App** (`app/`): Kotlin + Jetpack Compose, package `life.mygig.clauderc`.
- **Server** (`server/`): `claude-setup.sh` (menu + `--api` JSON mode), the
  forced-command runner `claude-launcher-api`, and `install-launcher-key.sh`.
  See [server/README.md](server/README.md).

## How it works

```
phone app ──SSH (its own Ed25519 key)──▶ sshd ──forced command──▶ claude-launcher-api
                                                                      │ allowlist + validation
                                                                      ▼
                                                     claude-setup.sh --api <action>  → one JSON object
```

Every call is short: connect, run one allowlisted action, read the JSON,
disconnect. The phone key can't open a shell, forward ports or run anything
off the allowlist. There is no web server, open port or cloud service.

## Screens

| Tab | Shows | Actions |
| --- | --- | --- |
| **Status** | Claude / GitHub / AWS login state, server name, last check | Pull to refresh; tap a red row to log in from the phone |
| **New** | Name (validated live), owner (you or an org), Private/Public, “Start Claude now” | Create → repo link + **Open in Claude** |
| **Projects** | All your repos, newest push first, with Private/Public, Org, On server, Running badges | Clone/pull + start, stop, tail log (long-press) |
| **Sessions** | Running Claude sessions, uptime, attached | Stop, tail, **Open in Claude** |

**Servers**: add as many as you like; tap the server name under the title to
switch. All of them use this phone's one key.

**Settings**: servers (add, switch, remove), host, port, username, pinned host key fingerprint, this phone's
public key (copy / share / regenerate), app lock (fingerprint/PIN before
create, stop, logins), theme.

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
same key. Until the first real release that is the committed
`app/debug.keystore`; switching to the release key later needs one uninstall.

## Security

- The app generates its own Ed25519 key; the private part is encrypted with an
  Android Keystore AES-GCM key and never leaves the phone or its backups.
- The server pins that key to `claude-launcher-api` with `restrict` (no pty, no
  forwarding of any kind), so the key can only run that one program.
- App lock (optional) asks for fingerprint/PIN to open the app, after 30 s in
  the background, and before creating, stopping, logging in or showing the key
  and username; it also hides the app from the Recents screen.
- The runner never uses `eval` or a shell on the request string; it only
  accepts known subcommands with validated arguments.
- Host key is pinned on first connect after you compare the fingerprint; a
  changed key is refused.
- Tokens, codes and AWS keys travel on stdin only, never on a command line, and
  the audit log records only time, action and result code.
- Nothing personal is in this repo: server details live in the app's settings
  and in `~/.config/claude-launcher/config` on the server.

## Building

CI (`.github/workflows/build.yml`) runs gitleaks over the full history, the
server tests (including a real sshd), unit tests, and builds a signed release
APK. Tags `v*` publish a GitHub Release; pushes to `main` update the
`latest-main` pre-release.

Signing: with no secrets set, CI signs with the committed
`app/debug.keystore` (pre-release). Setting the four repository secrets
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD` switches CI to the release key. CI fails any APK whose
certificate isn't the one it expects.

Locally:

```bash
./gradlew assembleDebug
./gradlew assembleRelease   # debug-key signed; an untracked keystore.properties switches to the release key
```

## License

[MIT](LICENSE): free to use, modify and share. Keep the copyright notice to
credit the original project.
