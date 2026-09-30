Last updated: 2026-09-30 16:45 UTC

## Goal
cLaudeRC: an Android app plus server scripts to run, watch, chat with and manage Claude Code sessions on your own server from a phone.

## Current task
Waiting on the owner: the notification bug needs the phone's Notification history (which app posts the vibrating notification). Next step: once known, fix how the app posts it.

## Tasks
- [ ] Notifications: phone vibrates but nothing shows in the shade. Get Notification history from the owner, then fix (channel/importance, or it's another app).
- [ ] Try on a phone (all untested on a device): chat links (tap a URL), files Claude sends (thumbnail, zoom, Save), Full checkup (own `claude-doctor` session), repo rename/visibility/delete, chat PIN "don't ask again" after swiping the app away, "Disable the <plugin> plugin" on MCP pages.
- [ ] MCP sign-in: finish a real sign-in end to end with the paste box (Notion still needs sign-in).
- [ ] Repo delete: owner needs a GitHub token with `delete_repo` (the app offers New token).
- [ ] Optional: claude-mem note-taking via OmniRoute (free models) through `CLAUDE_MEM_OPENROUTER_BASE_URL`. Offered, not decided.

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

## Known issues
- Notifications vibrate without showing (see Tasks).
- Plugin-provided MCP servers can only be removed by disabling their plugin; project-scope MCP servers can't be signed in from the app (use /mcp in that project).
