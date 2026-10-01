Last updated: 2026-10-01 16:20 UTC

## Goal
cLaudeRC: an Android app plus server scripts to run, watch, chat with and manage Claude Code sessions on your own server from a phone.

## Current task
Waiting on the owner: the notification bug needs the phone's Notification history (which app posts the vibrating notification). Next step: once known, fix how the app posts it.

## Tasks
- [ ] Team (multi-account delegation): server done (worker-* actions, `clauderc-team` MCP, installer); app Team dialog next, then PR. Plan: `docs/superpowers/plans/2026-10-01-team-workers.md`. Owner must try a real second-account sign-in.
- [ ] Notifications: phone vibrates but nothing shows in the shade. Get Notification history from the owner, then fix (channel/importance, or it's another app).
- [ ] Try on a phone (all untested on a device): chat links (tap a URL), speaker switch + per-reply Play/Pause, files Claude sends (thumbnail, zoom, Save; big files were slow, fixed in #75), Full checkup (own `claude-doctor` session), repo rename/visibility/delete, chat PIN "don't ask again" after swiping the app away, "Disable the <plugin> plugin" on MCP pages.
- [ ] MCP sign-in: finish a real sign-in end to end with the paste box (Notion still needs sign-in).
- [ ] Repo delete: owner needs a GitHub token with `delete_repo` (the app offers New token).
- [ ] Optional: claude-mem note-taking via OmniRoute (free models) through `CLAUDE_MEM_OPENROUTER_BASE_URL`. Offered, not decided.

- [ ] Owner decisions from the 2026-10-01 audit (not changed yet): gate `plugin install`/`marketplace add` and `open --start` on others' repos (they run code with `ALLOW_RUN=0`); require the chat PIN for `tail`/`keys`/`restart`; check `self-update` sha is on main; deny behaviour-changing names in `set-secret` (AWS_ENDPOINT_URL, PIP_INDEX_URL, DOCKER_HOST…); CI publishes debug-signed builds if signing secrets go missing; rename leaves the server folder under the old name.

## Decisions
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

## Known issues
- Notifications vibrate without showing (see Tasks).
- Plugin-provided MCP servers can only be removed by disabling their plugin; project-scope MCP servers can't be signed in from the app (use /mcp in that project).
