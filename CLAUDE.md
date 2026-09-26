# cLaudeRC — notes for Claude Code sessions

## APK signing (read before building or sending any APK)

**Pre-release (now):** every build is signed with the committed
`app/debug.keystore` (password `android`, alias `androiddebugkey`, certificate
SHA-256 `ff714e476e95c2d2af85c31a6cc7455011ea74825f182dad9a6a74c4dc1df5c3`).
The user chose this so CI builds and publishes installable APKs with no setup.
Don't replace or regenerate this keystore: a different key means the user has
to uninstall the app and lose its SSH key and settings.

**At release:** set the Actions secrets `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` from
`clauderc-release.jks` (certificate SHA-256
`cc833ec5438cbf8089cb511ab5612ff28149f0b8a0afcb18b09b020771cf3cd1`, CN=cLaudeRC).
CI then signs with that key instead. Switching keys needs one uninstall on the
phone. The release keystore is **never** in this repo; never generate a new one.

- CI fails any APK whose certificate doesn't match the key it expects.
- `versionCode` must only go up. CI uses `100 + GITHUB_RUN_NUMBER`. Builds made
  by hand used 1 to 4.

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

## Public repo

No personal values in code, docs or commits: usernames, home paths,
hostnames, IPs, emails, org names, keys. They live in
`~/.config/claude-launcher/config` on the server and in the app's settings.
