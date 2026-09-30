# Workflow rules (read every session)

## Session start
1. Read `PLAN.md` and this file before doing anything else.
2. Run `date`. If the "Last updated" line in `PLAN.md` is more than ~12 hours old, refresh the plan (see "Planning") before starting new work.
3. Pick the next unchecked task in `PLAN.md` and work on it. Don't ask which task to do unless the plan is empty or blocked.

## Models
- This project runs on `opusplan`: Opus in plan mode, Sonnet for building.
- Enter plan mode yourself for: new features, architecture changes, anything touching 3+ files, or refreshing `PLAN.md`.
- Small fixes, UI tweaks, and single-file changes: skip planning and just build.
- Escalation: if the same bug or error survives 2 fix attempts, stop guessing and delegate it to the `planner` subagent (Opus) for a root-cause analysis. Then continue building from its answer.
- Don't ask permission to switch between planning and building or to use the `planner` subagent. Just do it and mention it in one line.

## Planning (PLAN.md)
- `PLAN.md` is the single source of truth. Structure:
  - `Last updated: <date/time>`
  - `## Goal` (1-3 lines)
  - `## Current task` (what's in progress, next concrete step)
  - `## Tasks` (checkbox list, in order)
  - `## Decisions` (short log: date, decision, why)
  - `## Known issues`
- Refresh it at most once or twice a day, or right away if a decision changes the plan. Refreshing means: check off finished tasks, reorder, add new tasks, update "Current task" and "Last updated".
- After finishing any task, check it off and update "Current task" (quick edit, not a full refresh).
- Keep it short. Remove finished tasks older than a week into a one-line summary under Decisions.

## CLAUDE.md upkeep
- When you learn something future sessions need (build commands, gotchas, conventions, file layout, a fix that took a while to find), add it below under "Project notes" without asking.
- Keep this file under ~150 lines. Tighten or remove stale notes rather than piling on.
- Never put secrets, keys, or passwords in either file.

## Context and clearing
- You can't run /clear yourself, so work in checkpoints instead:
  - At the end of each task (or when the conversation gets long), make sure `PLAN.md` holds everything needed to continue, then tell me in one line: "Checkpoint saved - good time to /clear."
  - After a /clear or auto-compact, restart from "Session start" above.
- Don't re-read large files you've already read this session unless they changed. Read only the parts you need.

## Working style
- I mostly work from my phone. Keep status messages short: what changed, what's next, anything you need from me.
- Commit after each finished task with a clear message.
- Only stop to ask me when something is genuinely ambiguous, risky (deleting data, touching production, spending money), or blocked.

## Project notes
<!-- Claude adds build commands, conventions, and gotchas here -->

### APK signing (read before building or sending any APK)

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

### Layout

- `app/`: Android app (Kotlin, Compose), package `life.mygig.clauderc`.
- `server/`: `install.sh` (curl-able installer, no clone), `claude-setup.sh` (the user's interactive menu, unchanged, plus
  `--api` JSON mode), `claude-launcher-api` (forced-command runner),
  `install-launcher-key.sh`, and `claude-autostart.sh` (the user's original: keep changes small).

### Checks

Build and test the app on GitHub Actions only (push, then `gh run download`),
not on the server: it saves server resources. The server shell tests are fine locally.

```bash
server/tests/test-api.sh                 # server, stub CLIs
sudo server/tests/test-sshd.sh --app     # real sshd + app SSH code
./gradlew testDebugUnitTest assembleDebug
```

### Server script API

`claude-setup.sh` has `SCRIPT_API`; the app has `Updates.MIN_SCRIPT_API`.
When the app starts needing a new server action or field, bump `SCRIPT_API`
and raise `MIN_SCRIPT_API` so old servers see "Server scripts need an update".

### Credentials for Claude's sessions

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

### Public repo

No personal values in code, docs or commits: usernames, home paths,
hostnames, IPs, emails, org names, keys. They live in
`~/.config/claude-launcher/config` on the server and in the app's settings.
