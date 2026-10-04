Last updated: 2026-10-04 16:30 UTC

## Goal
cLaudeRC: an Android app plus server scripts to run, watch, chat with and manage Claude Code sessions on your own server from a phone.

## Current task
SAVE POINT before moving to a bigger VPS (2026-10-04). Everything below is merged to main (#92-#117) and CI is green. Next: migrate the server (see docs/migration.md; the owner's private inventory lives outside the repo), then on the new server run the server tests, Update now from the app, re-sign in the CodeWriter worker, restart sessions. After that: Apple developer account (iOS signing/TestFlight), phone-testing the new screens, and the optional "offload to server programs" ideas (output condensers, lint/gitleaks pre-pass, repo index, self-hosted runner + local builds, emulator screenshot QA).

## Tasks
- [ ] MIGRATE to the new VPS: on the old server `claude-backup export` (1.7 GB, ~1 min, encrypted, prints a passphrase once), on the new one install cLaudeRC then `claude-backup import --from user@old:FILE --clone`; docs/migration.md has the details and the manual fallback. Stop old sessions before copying logins; copy ~/.claude, ~/.config/claude-launcher, ~/.agents, project folders; run install.sh with the phone's key; add the new server in the app and trust its host key; re-sign in workers; check docker volumes/services. Push or copy unpushed work first (owner's private list).
- [ ] Try on the phone after Update now + new APK: Plugins tab lights (green current / yellow update / red can't check / hollow off, tab dot, Update on `npx skills` plugins runs `skills-update`), Cluster settings card, Apple developer account dialog, instant alerts (done, working).
- [ ] Cluster: delegate by role (`delegate(role=...)`), failover, `from_task` writer->auditor pipelines, 3 parallel tasks/chat, 5 h + weekly hand-back at 95%: untested with a second real account. CodeWriter was logged out twice by concurrent starts; starts are now 30 s apart. If it happens again consider long-lived worker tokens (loses usage numbers).
- [ ] Cluster (#105-#109): try on the phone after Update now + new APK: Claude tab › cLaudeCluster › Cluster settings (parallel tasks per chat 1-10, hand back at 50-100% of the 5 h limit). Real test of slots / repo= / file= / usage hand-back needs the updated server scripts. Open: `wait` still polls every ~60 s; iOS has no cluster screen.
- [ ] Apple developer key in the app (PR apple-developer, SCRIPT_API 31): once the account exists, App Store Connect › Integrations › create key, then + › Apple developer account in the app. Then add signing/TestFlight to ios.yml (secrets via `gh secret set`).
- [x] iOS port (branch `iOS`, `ios/`): builds green on macOS CI (unsigned IPA artifact, 2nd attempt). Next: sign/TestFlight needs an Apple developer account; port logins, MCP/plugins, cluster, files, push.
- [x] Instant push alerts: working on the phone (2026-10-03: Firebase key registered, test push and a session "finished" alert both arrived).
- [ ] Try on the phone: 👥 in a chat (attach a worker, set role/mode), ask main to delegate and merge; usage bars fill with used.
- [ ] cLaudeCluster (#81, replaces Team): try on the phone. Claude tab › cLaudeCluster shows usage left per account (undocumented OAuth usage endpoint; verify real numbers show), + Add account (worker + sign in), add a 2nd server signed in to another account → join prompt. Also check #80 (keyboard no longer opens over the chat box) and queued messages surviving leaving the chat.
- [ ] Notifications: phone vibrates but nothing shows in the shade. #79 added a clearer icon, a pop-up channel (`alerts`) and Settings › Send a test notification: try it on the phone; if the test shows, the old one was the faint icon/low importance. Session alerts only fire while the phone is unlocked (SSH key needs it; owner chose to keep that).
- [ ] Try on a phone (all untested on a device): chat links (tap a URL), speaker switch + per-reply Play/Pause, files Claude sends (thumbnail, zoom, Save; big files were slow, fixed in #75), Full checkup (own `claude-doctor` session), repo rename/visibility/delete, chat PIN "don't ask again" after swiping the app away, "Disable the <plugin> plugin" on MCP pages.
- [ ] MCP sign-in: finish a real sign-in end to end with the paste box (Notion still needs sign-in).
- [ ] Repo delete: owner needs a GitHub token with `delete_repo` (the app offers New token).
- [ ] Optional: claude-mem note-taking via OmniRoute (free models) through `CLAUDE_MEM_OPENROUTER_BASE_URL`. Offered, not decided.

- [ ] Owner decisions from the 2026-10-01 audit (not changed yet): gate `plugin install`/`marketplace add` and `open --start` on others' repos (they run code with `ALLOW_RUN=0`); require the chat PIN for `tail`/`keys`/`restart`; check `self-update` sha is on main; deny behaviour-changing names in `set-secret` (AWS_ENDPOINT_URL, PIP_INDEX_URL, DOCKER_HOST…); CI publishes debug-signed builds if signing secrets go missing; rename leaves the server folder under the old name.

## Decisions
- 2026-10-01: Busy screens split with `PillTabs` (components/Tabs.kt): Claude = Accounts/Plugins/Tools, Connections = Logins/MCP/Keys, Settings = Connection/Security/App (first-run setup stays one page). Owner picked the tabs option from the HTML preview. Sessions/Projects are single lists: no tabs.
- 2026-10-01: Mic/dictation removed from chat. Full code scan fixed in #79 (chat_session subshell bug sent messages to the wrong session; team workers killed by process group; uploads private + git-ignored; PIN grace one-shot; stale server data on switch; polling pauses in background). SSH key stays unlocked-device-only.
- 2026-09-29: Status light: green idle, amber working, red when waiting on an answer or working 5+ min (timed from when the app first saw it busy). Shared `claudeHealth()` for chat and session tiles.
- 2026-09-29: MCP sign-in drives `claude /mcp` in a hidden tmux session; the phone pastes back the `http://localhost…/callback` URL. Only typed while the paste box shows; success judged by a fresh `claude mcp list`. A 4xx "client_id" answer drops the saved OAuth entry and retries once.
- 2026-09-29: Repo rename/visibility/delete via `repo-edit` (GitHub only; server folder keeps its name). Delete needs typing "delete"; rename and visibility ask "Are you sure?".
- 2026-09-29: Chat PIN "don't ask again for 30 min/1 h/4 h": PIN kept AES-GCM encrypted with an Android Keystore key, tied to the boot count and an uptime deadline, so a restart asks again.
- 2026-09-29: A libevent update made needrestart restart `claude-sessions.service`, killing all sessions. Fixes: autostart restore resumes each project's newest conversation; `claude-autostart install` adds a needrestart exclusion; install.sh refreshes the installed autostart script.
- 2026-09-30: Full checkup runs `/doctor` in its own `claude-doctor` session, not a project's chat. Autostart never saves helper sessions (logins, mcp-auth, claude-doctor).
- 2026-09-30: Bun is installed by install.sh when missing (claude-mem hooks need it).
- 2026-09-30: Use the `gh` CLI, not the GitHub MCP plugin (disabled).
- 2026-09-30: Workflow rules, planner subagent and `opusplan` live in the server's user-level Claude config, not in this repo.

- 2026-09-30: Chat shows files Claude sends to the Claude app (SendUserFile in the transcript): `chat-file` (project folder or Claude's temp folder only, 10 MB) + cards with thumbnails and Save. URLs in chat are tappable.

- 2026-10-01: A file Claude sent is served by `chat-file` from anywhere (its path is in a SendUserFile call); everything else must be in the project or Claude's temp folder. App messages show as a banner inside an open chat (the snackbar sits behind the dialog).
- 2026-10-01: Claude's project folder name maps every non-alphanumeric to `-` (checked live); `api_ok` pipes its JSON (argv tops out at 128 KB).

- 2026-10-01: Chat speaker switch is saved in prefs (`chat_voice`) so it stays on/off across chats and restarts; each Claude reply has Play/Pause (spoken by sentence, Pause resumes at that sentence). SSH read loop must block on `read()`: polling `available()` + sleep throttled JSch to ~32 KB/s.

- 2026-10-01: Team = other Claude accounts as "workers" (own `CLAUDE_CONFIG_DIR` under `~/.config/claude-launcher/workers/`); main Claude delegates via the `clauderc-team` MCP server (headless `claude -p`, main waits). Same project folder; mode per worker (acceptEdits default).

- 2026-10-01: Team renamed cLaudeCluster: `cluster` action (SCRIPT_API 26) = main + workers with email (`.claude.json`) and usage; expired tokens are never refreshed by us. Other servers' accounts are usage-only (workers run on one machine). `Server.inCluster` null/true/false; email compare on add-server.

- 2026-10-01: Workers are attached per chat (👥 button in the chat header → `cluster-session/-attach/-assign`, SCRIPT_API 27). State in `~/.config/claude-launcher/attach/<project folder slug>.json`, read live by clauderc-team: only attached workers are visible, role/mode can override per chat. In a git project each task runs in its own worktree on branch `cluster/<worker>/<id>` (server commits the worker's changes); the main Claude uses `review` then `merge`/`discard`. Usage bars now fill with what's USED (owner found "left" confusing).

- 2026-10-01: Cluster accounts also show tokens used on THIS server (5 h / 7 days, summed from Claude's session logs, one row per message id); an estimate, not the plan's own window, and blind to claude.ai/other devices.

- 2026-10-01: Opus review of the cluster; first three fixes shipped (no silent run in main checkout when a worktree fails; failed/timed-out tasks keep partial work and can be reviewed/discarded; merge with hooks off, diffs over 20k need force=true). Still open, in order: detach worker runs so they survive MCP restarts (+ refuse merge/discard while a reply on the same branch runs); rewrite main's absolute paths to the worktree; open-branches/running-tasks list in the 👥 sheet; copy .env/.worktreeinclude + submodules into worktrees; per-worker lock (shared token refresh); usage-limit detection; wrap worker replies as untrusted; cache token_usage 60 s; realpath in attach_session; role only if attached.

- 2026-10-01: Cluster round 2 shipped: runs are detached (`flock` per worker + `timeout`, output in tasks/<id>.out; the next `wait` finalizes them, MCP restarts lose nothing); merge/discard refused while a follow-up on the branch runs; main's paths rewritten to the worker's copy; `.worktreeinclude` files + submodules copied into worktrees (never committed); usage-limit replies become failed tasks; worker replies are marked as data; token scan cached 60 s; 👥 sheet lists open work. Still open from the review: strip main's mirrored secrets from worker env (workers run as the same Unix user, no real isolation), `--allowedTools` allowlist so edit-only workers can run tests, a one-time hint that workers are off by default per chat.

- 2026-10-01: Cluster round 3: workers get none of the env-file variables (service tokens); edit-only workers may run the commands the OWNER lists in the project's `.cluster-allowed-tools` (read from the main project, `--allowed-tools`); Accounts help says to attach workers per chat with 👥. Only one Claude account exists so far: workers are tested with stubs only, try with a real second account when there is one. Still true: workers run as the same Unix user (no real isolation).

- 2026-10-02: Push = Claude hooks (Notification/Stop) → `server/claude-push` → FCM v1 data message → `PushService` shows it on channel `alerts` (needs answer, always) or `finished` (only for sessions with the bell on: `~/.config/claude-launcher/push-done/<session>`). No keys in the repo or build: the server holds `fcm-key.json` + `push.json` (public ids); the app gets the ids from `push-config` and starts Firebase at run time (no google-services plugin). `push-setup` (key on stdin) registers the Android app via the Firebase Management API. SCRIPT_API 30. The 15-min SessionWatcher goes quiet about sessions once push is registered. The poller still alerts "finished" for every session when push is off.

## Known issues
- Not fixed from the scan: draft lost after >30 s in a file picker (app re-locks); env/credentials files have no cross-process lock (writes are atomic); login-claude-code can report success when a running session refreshes its token.
- Notifications vibrate without showing (see Tasks).
- `wait` still returns about once a minute; a worker run that hits its usage limit mid-task fails (re-delegate by role). The app's own usage bars call the same rate-limited endpoint separately.
- Server tests don't gate merges (no branch protection): always check CI before merging.
- Plugin-provided MCP servers can only be removed by disabling their plugin; project-scope MCP servers can't be signed in from the app (use /mcp in that project).

- 2026-10-03: iOS app = native SwiftUI in `ios/` (swift-nio-ssh, XcodeGen, macOS CI `ios.yml`, unsigned IPA); Android untouched. Apple developer key goes in the app (**+ › Apple developer account**, `login-apple`) so every session gets APPLE_* env.
- 2026-10-03: Cluster: workers run up to 10 tasks at once per account (flock slots), the owner's per-chat limit (1-10, default 3) and hand-back point (50-100%, default 95, applies to 5 h AND weekly usage) are in `cluster.json`, set from the app (`cluster-config`). `delegate(role=)` picks the account with the most room, a named worker near its limit fails over to the same role, `from_task` stacks a task on another task's branch, `review file=` reads big diffs per file, `delegate repo=` branches from a worktree.
- 2026-10-03: The usage endpoint rate-limits (429): one shared file cache (`usage-cache/`, TTL 120 s), backoff, last good numbers kept 30 min. Worker logins: starts 30 s apart, a run that could outlive its token starts alone.
- 2026-10-04: Plugin update lights come from `claude-plugin-updates` (pinned sha, git ls-remote, GitHub compare for in-repo plugins, declared manifest version for versioned plugins; 1 h cache); plugins managed by `npx skills` are judged by the skills lock file and updated by `skills-update`.
- 2026-10-04: README rewritten (hero, contents, features with pictures); screenshots are mock-ups from docs/mockups (never real data), social images in docs/social.
