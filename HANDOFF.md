# Handoff: cLaudeRC

The first round of work was done in a cloud session. This note is for the
Claude Code session that continues it **on the server itself** (started from
the cLaudeRC app). Read `CLAUDE.md` too: its signing rules are strict.

## Where things are

- **Branch:** all work is on `claude/clauderc-apk-setup-myk0vf`. `main` only has
  the initial commit. No PR has been opened.
- **Working and tested on the real server and phone:** Test connection,
  Status, Projects, Sessions, New project (create + start), Stop, Tail
  (auto-refresh). The phone runs **0.1.3 (versionCode 4)**.
- **Tests:** `server/tests/test-api.sh` (83 checks with stub CLIs),
  `sudo server/tests/test-sshd.sh --app` (real sshd plus the app's SSH code),
  `./gradlew testDebugUnitTest lintDebug`. CI (`.github/workflows/build.yml`)
  runs all of them plus gitleaks, and is green.
- **Server install:** the user installed the scripts from a clone at
  `~/Claude-RC`, with `claude-setup.sh` at `~/claude-setup.sh` and the runner
  and key installer in `~/bin/`. The phone's key is in
  `~/.ssh/authorized_keys` as a `restrict,command=` line. `claude-autostart` is
  the user's original and must stay unchanged.

## Do first

1. **Signing: done differently.** The user chose to sign every build with
   the committed `app/debug.keystore` until release (see CLAUDE.md). Installing
   the first debug-signed build needs one uninstall of 0.1.3 (release-signed).
   The release-key secrets step is deferred to release time.

2. **Get it onto `main`.** Ask the user before opening a PR or merging. After
   merging, pushes to `main` update the `latest-main` pre-release, and a
   `v0.2.0` tag makes a proper Release. CI's versionCode is
   `100 + run number`, above the phone's 4, so CI builds install over 0.1.3.

## Rules that bit before

- **Signing:** see CLAUDE.md. Never regenerate either keystore. Push and let
  CI build.
- **Public repo:** no usernames, home paths, hostnames, IPs, emails, org names
  or keys in code, docs or commits. The server's real values live in
  `~/.config/claude-launcher/config`, never in the repo.
- **`claude-setup.sh`:** its interactive menu must stay line-for-line the
  user's original (only the shared functions it calls may change).
  `--api` must print exactly one JSON object and never wait for input. Names
  may never start with `-`.
- **Sessions:** start them only via `launch_session` (same command and naming
  as the menu), so `claude-autostart` saves and restores them. Login sessions
  must never land in `~/.config/claude-setup/sessions.tsv`.
- **Testing:** don't run `pkill -f` with a pattern that also appears in your own
  shell command, or it kills your shell.

## Known gaps / ideas (none started)

- **Git history:** the repo's first commit (made before this work) has a
  personal email as author. Removing it means rewriting `main`'s history. Ask
  the user first.
- **Login dialogs and the keyboard:** the main screens have IME padding; the
  login pop-ups weren't checked on a real phone.
- **Claude login flow:** `claude auth login` is driven by reading the tmux
  pane. It has only been tested against a stub, never the real prompts. If it
  fails, the app shows the pane text.
- **Release size:** the APK is about 16 MB. R8 is off because JSch and Bouncy
  Castle load classes by name. Turning it on needs a device test.
- **Dependencies:** lint lists newer AndroidX versions. Some need compileSdk 37
  / AGP 9, so upgrade carefully.
- **Optional from the brief:** the home-screen widget (3 recent projects plus a
  running count), and a server switcher if a second server is added.

## Handy commands on the server

```bash
git checkout claude/clauderc-apk-setup-myk0vf
server/tests/test-api.sh
tail -n 20 ~/.local/state/claude-launcher/api.log     # what the phone called
~/claude-setup.sh --api status | jq .
# after changing server scripts, reinstall them:
install -m 755 server/claude-setup.sh ~/claude-setup.sh
install -m 755 server/claude-launcher-api server/install-launcher-key.sh ~/bin/
```

Building the APK on the server needs the Android SDK (not installed there).
Prefer pushing and letting CI build it.
