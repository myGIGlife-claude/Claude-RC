Last updated: 2026-10-01 20:02 UTC

## Goal
cLaudeRC: an Android app plus server scripts to run, watch, chat with and manage Claude Code sessions on your own server from a phone.

## Current task
In flight: (1) main build for #85 (cluster hardening) then tell the owner to tap Update now (server scripts too); (2) a subagent (worktree, branch `claude/readme-refresh`) is refreshing README, screenshots and demo video: when it reports, check its PR (no personal values, images render) and tell the owner to merge. Next: remaining cluster review items (see Decisions 2026-10-01 "Opus review"), then the notification bug (needs phone's Notification history).

## Tasks
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

## Known issues
- Not fixed from the scan: draft lost after >30 s in a file picker (app re-locks); env/credentials files have no cross-process lock (writes are atomic); login-claude-code can report success when a running session refreshes its token.
- Notifications vibrate without showing (see Tasks).
- Plugin-provided MCP servers can only be removed by disabling their plugin; project-scope MCP servers can't be signed in from the app (use /mcp in that project).
