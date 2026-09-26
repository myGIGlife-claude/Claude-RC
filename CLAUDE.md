# cLaudeRC — notes for Claude Code sessions

## APK signing (read before building or sending any APK)

The app is sideloaded. Android only installs an update over the existing app
if it is signed with the **same certificate**, otherwise the user has to
uninstall and lose the app's key and settings. So:

- There is exactly one release key. Its certificate SHA-256 is
  `cc833ec5438cbf8089cb511ab5612ff28149f0b8a0afcb18b09b020771cf3cd1`
  (CN=cLaudeRC). It is public, so it's safe to keep here.
- The keystore itself is **never** in this repo. CI reads it from the Actions
  secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
- **Never generate a new keystore**, and never give the user a debug APK or an
  APK signed with any other key. If you don't have the keystore, don't build an
  APK for the user. Point them at the latest GitHub Release, or push and let
  CI build it.
- `versionCode` must only go up. CI uses `100 + GITHUB_RUN_NUMBER`. Builds made
  by hand used 1 and 2.
- CI fails any release APK whose certificate doesn't match the SHA-256 above.

## Layout

- `app/`: Android app (Kotlin, Compose), package `life.mygig.clauderc`.
- `server/`: `claude-setup.sh` (the user's interactive menu, unchanged, plus
  `--api` JSON mode), `claude-launcher-api` (forced-command runner),
  `install-launcher-key.sh`, and `claude-autostart.sh` (the user's original, keep it
  unchanged).

## Checks

```bash
server/tests/test-api.sh                 # server, stub CLIs
sudo server/tests/test-sshd.sh --app     # real sshd + app SSH code
./gradlew testDebugUnitTest assembleDebug
```

## Public repo

No personal values in code, docs or commits: usernames, home paths,
hostnames, IPs, emails, org names, keys. They live in
`~/.config/claude-launcher/config` on the server and in the app's settings.
