# cLaudeRC — notes for Claude Code sessions

## APK signing (read before building or sending any APK)

Releases are signed with the private release key: certificate SHA-256
`b62dd1599ca2dd7f9a637f215da28321e6ec1c45981a940e594f4595efd570f7`
(CN=cLaudeRC, O=myGIGlife). CI reads it from the Actions secrets
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`, and fails any APK signed with anything else. The
keystore is never in this repo; the owner keeps the backup.

- Never generate a new keystore: a different key means every user has to
  uninstall and lose the app's SSH key and settings.
- Never give anyone an APK that isn't a CI release build. Pull-request builds
  (no secrets) and local builds are signed with the committed
  `app/debug.keystore`, which is public: those are for testing only.
- `versionCode` must only go up. CI uses `100 + GITHUB_RUN_NUMBER`.

## Layout

- `app/`: Android app (Kotlin, Compose), package `life.mygig.clauderc`.
- `server/`: `install.sh` (curl-able installer, no clone), `claude-setup.sh` (the user's interactive menu, unchanged, plus
  `--api` JSON mode), `claude-launcher-api` (forced-command runner),
  `install-launcher-key.sh`, and `claude-autostart.sh` (the user's original: keep changes small).

## Checks

Build and test the app on GitHub Actions only (push, then `gh run download`),
not on the server: it saves server resources. The server shell tests are fine locally.

```bash
server/tests/test-api.sh                 # server, stub CLIs
sudo server/tests/test-sshd.sh --app     # real sshd + app SSH code
./gradlew testDebugUnitTest assembleDebug
```

## Server script API

`claude-setup.sh` has `SCRIPT_API`; the app has `Updates.MIN_SCRIPT_API`.
When the app starts needing a new server action or field, bump `SCRIPT_API`
and raise `MIN_SCRIPT_API` so old servers see "Server scripts need an update".

## Credentials for Claude's sessions

Anything a Claude session needs from its environment (service tokens, key
file paths) goes in `~/.config/claude-launcher/env` through `set_env` in
`claude-setup.sh`. `ensure_env_hook` mirrors it into the `env` block of
`~/.claude/settings.json` (kept mode 600): Claude Code sets those on its own
process, so commands Claude runs and MCP servers (including `${VAR}` in
`.mcp.json`) see them. Claude sessions do NOT read `~/.bashrc`: tmux starts
`claude` directly and Claude's shell snapshot keeps only functions, aliases
and PATH. Changes apply when a session restarts. Tools install into
`~/.local/bin`, which is on the sessions' PATH. Group changes (e.g.
`docker`) only reach sessions after the server restarts, so tell the user.

## Public repo

No personal values in code, docs or commits: usernames, home paths,
hostnames, IPs, emails, org names, keys. They live in
`~/.config/claude-launcher/config` on the server and in the app's settings.

## Project notes

- Workflow: branch, PR, merge right away; then watch only the main build
  (`gh run list -b main -w build`). The owner updates through the app's
  **Update now**; server-only changes reach the server the same way.
- Server tests: `server/tests/stubs/` fake the CLIs (state in `$STUB_STATE`).
  They take ~1.5 min; run them after any server change.
- `api_err` exits the script: never call it inside `$(…)`.
- `claude-autostart.sh` runs with `set -euo pipefail`: end pipelines that may
  find nothing with `|| true`.
- Driving Claude's TUI in tmux: use a wide pane (`-x 1000`) so URLs don't
  wrap; `claude /mcp` and `claude /doctor` open those directly; a new folder
  shows the trust prompt first (`trust_folder` avoids it).
- A session's live conversation is the newest `*.jsonl` in
  `~/.claude/projects/<dir with / and . as ->/`.
- Push alerts: `server/claude-push` (python, stdlib) is both the Claude hook and
  `--setup/--test`; `server/tests/test-push.py` runs it against a fake Google.
  Config in `~/.config/claude-launcher/`: `fcm-key.json` (600), `push.json`,
  `push-tokens`, `push-done/`. `ensure_push_hook` adds the hooks to
  `~/.claude/settings.json` (via `status`). An FCM token has a `:`, so
  `claude-launcher-api` skips its generic TOKEN_RE for `push-register`.
- iOS (branch `iOS`): `ios/` is a native SwiftUI port (no Mac here, so it is only
  ever compiled by `.github/workflows/ios.yml` on a macOS runner: unsigned IPA
  artifact). The Xcode project is generated from `ios/project.yml` with XcodeGen.
  SSH is swift-nio-ssh (`Ssh.swift`), same forced-command protocol as Android.
  Not ported yet: logins, MCP/plugins, cluster, file transfer, push (APNs needs
  an Apple developer account). Android code is untouched.
