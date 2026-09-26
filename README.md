# cLaudeRC

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

**Settings**: host, port, username, pinned host key fingerprint, this phone's
public key (copy / share / regenerate), app lock (fingerprint/PIN before
create, stop, logins), theme.

## Install on the phone

1. Download the APK from the [Releases page](https://github.com/myGIGlife-claude/Claude-RC/releases)
   on the phone and install it (allow installs from your browser when asked).
2. Open **cLaudeRC**, tap **Copy install cmd**.
3. On the server, set up `server/` (see [server/README.md](server/README.md)) and paste:
   `~/bin/install-launcher-key.sh 'ssh-ed25519 AAAA… clauderc'`
4. Back in the app enter host, port, username → **Connect** → compare the
   fingerprint → **Trust**.

Updates install over the old version because every release is signed with the
same key.

## Security

- The app generates its own Ed25519 key; the private part is encrypted with an
  Android Keystore AES-GCM key and never leaves the phone or its backups.
- The server pins that key to `claude-launcher-api` with `no-pty`,
  `no-port-forwarding`, `no-agent-forwarding`, `no-X11-forwarding`.
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

Release signing needs four repository secrets: `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Without
them CI still builds, but only an unsigned APK and no release.

Locally:

```bash
./gradlew assembleDebug
# signed release: put storeFile/storePassword/keyAlias/keyPassword in an untracked keystore.properties
./gradlew assembleRelease
```
