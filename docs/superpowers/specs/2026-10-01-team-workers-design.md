# Team: delegate work across several Claude accounts

## Goal

Let the owner sign several of their own Claude accounts into the server, name
each one by role (research, coding, UI…), and let the main Claude chat split
a job across them using rules the owner types in that chat. cLaudeRC only.

## Decisions (agreed 2026-10-01)

- Delegation is done by **MCP tools** the main Claude calls, not by the app.
- The main Claude **waits** for each worker's reply and can combine results.
- Accounts are signed in **from the app** (paste-the-code flow, like MCP sign-in).
- Workers use the **same project folder** as the main chat. No worktrees.
- Worker permission mode defaults to `acceptEdits`, set per worker.
- Rules are typed in the main chat. No saved-rules field.
- All accounts must be the owner's own. Nothing is shared or proxied: each
  account logs in normally under its own config dir.

## Non-goals

Worktrees per worker, saved/structured routing rules, parallel-wait helpers,
driving a worker's interactive TUI, any use outside cLaudeRC.

## Server

**Worker store:** `~/.config/claude-launcher/workers/<name>/` containing
`home/` (that worker's `CLAUDE_CONFIG_DIR`) and `meta` (role line, permission
mode). `<name>` matches the existing `PROJECT_RE`-style charset.

**`claude-setup.sh --api` actions** (bump `SCRIPT_API`, raise the app's
`Updates.MIN_SCRIPT_API`), each allowed in `claude-launcher-api`:
- `worker-add <name> <role>`: create the folder and meta.
- `worker-login <name>`: runs `claude` with that `CLAUDE_CONFIG_DIR` in a hidden
  tmux session (`worker-login-<name>`); the app shows the login URL; reuses
  the MCP sign-in paste-back mechanism for the code.
- `worker-list`: JSON of name, role, permission mode, signed-in yes/no.
- `worker-set <name> role|mode <value>`, `worker-remove <name>`.
- Never `api_err` inside `$(…)`. Helper tmux sessions are skipped by
  autostart, like logins and doctor.

**Running a task:** `claude -p "<task>" --output-format json
--permission-mode <mode>` with `CLAUDE_CONFIG_DIR=<worker>/home`, cwd = the
project, `ANTHROPIC_API_KEY` unset. Follow-ups add `--resume <session_id>`.
The result JSON and status are kept in
`~/.config/claude-launcher/workers/<name>/tasks/<id>.json`. The worker's
transcript stays in its own `home/projects/…`, so the app can show it.

## MCP server `clauderc-team`

Stdlib-only Python stdio server, installed by `install.sh` at a fixed
absolute path and registered at **user scope** (`claude mcp add -s user`).
Tools:
- `list_workers()`: names, roles, whether signed in.
- `delegate(worker, task)`: starts the run in the background, returns `task_id`.
- `wait(task_id, timeout_s)`: returns the worker's final reply, or "still
  running".
- `reply(task_id, message)`: resumes the same worker conversation.

The server's instructions text tells the main Claude to follow the owner's
routing rules from the chat and to `list_workers` first. Errors (unknown
worker, not signed in, run failed) come back as tool errors with the reason.

## App

New **Team** screen: list workers, add (name, role), Sign in (paste code),
change permission mode, remove. A worker's runs open as a read-only chat.
Hidden when the server's `SCRIPT_API` is too old (existing update prompt).

## Testing

- `server/tests/`: stub `claude` (extend `stubs/`): add/list/remove, login
  helper session, task run + resume, bad names rejected.
- MCP server: a Python self-check feeding JSON-RPC lines to stdin.
- App: unit test for the `worker-list` parser; build on GitHub Actions only.

## Risks

- A headless worker cannot ask questions: a task needing a permission outside
  its mode fails and reports it to the main Claude.
- Two workers editing the same file in the same folder can clash. Accepted;
  worktrees are the upgrade.
- Account logins use the normal `claude` login; the exact paste-back flow must
  be verified against the installed CLI version during implementation.
