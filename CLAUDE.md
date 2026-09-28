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
  `install-launcher-key.sh`, and `claude-autostart.sh` (the user's original, keep it
  unchanged).

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
