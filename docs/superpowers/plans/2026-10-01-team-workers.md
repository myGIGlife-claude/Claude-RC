# Team Workers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The main Claude chat can hand work to the owner's other signed-in Claude accounts ("workers") through an MCP server, and the app manages those accounts.

**Architecture:** Each worker is a folder with its own `CLAUDE_CONFIG_DIR`. `claude-setup.sh --api worker-*` manages the folders and signs accounts in (reusing the existing Claude login flow, pointed at the worker's folder). A stdlib-only Python MCP server (`clauderc-team`) runs a worker task as a headless `claude -p` in the caller's project folder and returns its reply. The app gets a Team dialog on the Claude tab.

**Tech Stack:** bash (`claude-setup.sh`, `claude-launcher-api`), Python 3 stdlib (MCP stdio server), Kotlin/Compose (app), JUnit.

**Spec:** `docs/superpowers/specs/2026-10-01-team-workers-design.md`

## Global Constraints

- Public repo: no usernames, home paths, hostnames, emails or keys in code, docs or commits.
- Build and test the **app on GitHub Actions only** (never run Gradle on the server). Server shell tests run locally: `server/tests/test-api.sh` (~1.5 min).
- `api_err` exits the script: never call it inside `$(…)`.
- `claude-launcher-api` allowlist: every new action must be in `KNOWN` and have an argument rule. Command line charset is `[A-Za-z0-9 ._/:=-]`, so free text (role) goes on **stdin**.
- Worker name regex (server and app): `^[A-Za-z][A-Za-z0-9_-]{0,29}$`.
- Worker modes: `acceptEdits` (default), `plan`, `bypassPermissions`.
- `SCRIPT_API` 24 → 25 and `Updates.MIN_SCRIPT_API` 24 → 25 together.
- `ANTHROPIC_API_KEY` is never passed to a worker (subscription logins only).
- Workflow: work on branch `claude/team-workers-spec` (already holds the spec), PR, merge, then watch only the main build.

## Review Focus

- Worker name with `../`, spaces, leading `-` or `.`: rejected by server and runner (Task 1 test).
- Worker folder removed while a task runs / worker removed then `delegate` called: tool error, not a crash (Task 3 test).
- Worker not signed in: `delegate` returns a clear tool error naming the app's Team screen (Task 3 test).
- Worker's `claude` exits non-zero or prints `is_error`: task becomes `failed` with the reason, `wait` returns it as a tool error (Task 3 test).
- MCP server restarted mid-task: `wait` reports the task was lost, never hangs (Task 3 test).
- Role text with spaces/quotes: stored verbatim via stdin, never reaches a command line (Task 1 test).

---

### Task 1: Worker store and `worker-*` actions (server)

**Files:**
- Modify: `server/claude-setup.sh` (constants near line 47; new functions after `do_login_claude_cancel` ~line 1246; `api_main` dispatch ~2400; `SCRIPT_API` line 22; stdin list ~2391; session guard ~2207)
- Modify: `server/claude-launcher-api` (`KNOWN`, new `case` arms)
- Modify: `server/tests/test-api.sh`

**Interfaces:**
- Produces (JSON data, all via `api_ok`):
  - `worker-list` → `{"workers":[{"name","role","mode","signed_in"}]}`
  - `worker-add <name>` (role on stdin) → `{"added":"<name>"}`
  - `worker-set <name> role|mode` (value on stdin) → `{"saved":"<name>"}`
  - `worker-remove <name>` → `{"removed":"<name>"}`
  - `worker-runs <name>` → `{"runs":[{"id","task","status","reply","error","started"}]}` (newest first, max 20)
- Disk layout (Tasks 2–3 rely on it): `$WORKERS_DIR/<name>/meta.json` = `{"role":"…","mode":"…"}`, `$WORKERS_DIR/<name>/home/` (the worker's `CLAUDE_CONFIG_DIR`), `$WORKERS_DIR/<name>/tasks/<id>.json`. `WORKERS_DIR="$LAUNCHER_CONFIG_DIR/workers"`.

- [ ] **Step 1: Write the failing tests**

Append to `server/tests/test-api.sh` just before its final summary block (find the line that prints `$pass` / exits; add this above it):

```bash
echo "team workers"
for bad in "worker-add" "worker-add ../x" "worker-add -x" "worker-add .x" "worker-add a b" "worker-set a" \
  "worker-set a color" "worker-remove" "worker-remove a/b" "worker-list extra" "worker-runs" "worker-login-start" \
  "worker-login-code a b"; do
  api "$bad"
  check "runner rejects: $bad" "$(jqt '.ok == false')"
done
api "worker-list"
check "no workers yet" "$(jqt '.ok and (.data.workers | length == 0)')"
api "worker-add research" "Reads docs & \"quotes\", finds sources"
check "add worker" "$(jqt '.ok and .data.added == "research"')"
api "worker-add research" "again"
check "duplicate worker refused" "$(jqt '.ok == false')"
api "worker-list"
check "role stored verbatim, default mode, not signed in" "$(jqt '.data.workers[0] | .name=="research" and .role=="Reads docs & \"quotes\", finds sources" and .mode=="acceptEdits" and .signed_in==false')"
api "worker-set research mode" "bypassPermissions"
check "set mode" "$(jqt '.ok')"
api "worker-set research mode" "rm -rf"
check "bad mode refused" "$(jqt '.ok == false')"
api "worker-set research role" "Docs"
api "worker-list"
check "mode and role saved" "$(jqt '.data.workers[0].mode=="bypassPermissions" and .data.workers[0].role=="Docs"')"
mkdir -p "$HOME/.config/claude-launcher/workers/research/tasks"
printf '{"id":"aaaa1111","task":"t1","status":"done","reply":"hello","started":100}' >"$HOME/.config/claude-launcher/workers/research/tasks/aaaa1111.json"
printf '{"id":"bbbb2222","task":"t2","status":"failed","error":"boom","started":200}' >"$HOME/.config/claude-launcher/workers/research/tasks/bbbb2222.json"
api "worker-runs research"
check "runs newest first" "$(jqt '.data.runs[0].id=="bbbb2222" and .data.runs[1].reply=="hello"')"
api "worker-runs nope"
check "runs of unknown worker refused" "$(jqt '.ok == false')"
api "worker-remove research"
check "remove worker" "$(jqt '.ok')"
api "worker-list"
check "worker gone" "$(jqt '.data.workers | length == 0')"
```

- [ ] **Step 2: Run to verify it fails**

Run: `server/tests/test-api.sh 2>&1 | grep -A3 "team workers" | head -20`
Expected: `FAIL` lines (actions unknown → `deny`).

- [ ] **Step 3: Runner allowlist**

In `server/claude-launcher-api`: append ` worker-list worker-add worker-set worker-remove worker-runs worker-login-start worker-login-code` to the end of the `KNOWN="…"` string, add `WORKER_RE='^[A-Za-z][A-Za-z0-9_-]{0,29}$'` under `OWNER_RE`, and add these arms before the final `*)`:

```bash
  worker-list)
    ((nargs == 0)) || deny "$sub"
    ;;
  worker-add | worker-remove | worker-runs | worker-login-start | worker-login-code)
    ((nargs == 1)) && [[ "${args[0]}" =~ $WORKER_RE ]] || deny "$sub"
    ;;
  worker-set)
    ((nargs == 2)) && [[ "${args[0]}" =~ $WORKER_RE && ( "${args[1]}" == role || "${args[1]}" == mode ) ]] || deny "$sub"
    ;;
```

- [ ] **Step 4: Setup script**

In `server/claude-setup.sh`:

1. `SCRIPT_API=24` → `SCRIPT_API=25`.
2. After `AWS_LOGIN_SESSION="aws-sso-login"` (line 48) add:
```bash
WORKERS_DIR="$LAUNCHER_CONFIG_DIR/workers"   # Team: one folder per extra Claude account
WORKER_RE='^[A-Za-z][A-Za-z0-9_-]{0,29}$'
WORKER_MODES=" acceptEdits plan bypassPermissions "
```
(Confirm `LAUNCHER_CONFIG_DIR` is defined above line 47; it is used at line 34.)
3. After `do_login_claude_cancel` (ends ~line 1246) add:

```bash
# ---------------- Team: extra Claude accounts the main Claude can delegate to ----------------

worker_check() { [[ "$1" =~ $WORKER_RE ]] || api_err invalid_name "Worker names are letters, digits, - and _ (start with a letter)."; }
worker_exists() { worker_check "$1"; [[ -f "$WORKERS_DIR/$1/meta.json" ]] || api_err invalid_name "No worker named '$1'."; }

do_worker_list() {
  local d n signed out=""
  for d in "$WORKERS_DIR"/*/; do
    [[ -f "$d/meta.json" ]] || continue
    n="$(basename "$d")"; signed=false
    [[ -s "$d/home/.credentials.json" ]] && signed=true
    out+="$(jq -c --arg n "$n" --argjson s "$signed" '{name:$n, role:(.role // ""), mode:(.mode // "acceptEdits"), signed_in:$s}' "$d/meta.json")"$'\n'
  done
  api_ok "$(printf '%s' "$out" | jq -sc '{workers:.}')"
}

do_worker_add() {
  local name="${1:-}" role
  role="$(read_secret_line)"
  exec 0</dev/null
  worker_check "$name"
  [[ ! -e "$WORKERS_DIR/$name" ]] || api_err invalid_name "A worker named '$name' already exists."
  mkdir -p "$WORKERS_DIR/$name/home" "$WORKERS_DIR/$name/tasks"
  jq -cn --arg r "${role:0:200}" '{role:$r, mode:"acceptEdits"}' >"$WORKERS_DIR/$name/meta.json"
  api_ok "$(jq -cn --arg n "$name" '{added:$n}')"
}

do_worker_set() {
  local name="${1:-}" field="${2:-}" val tmp
  val="$(read_secret_line)"
  exec 0</dev/null
  worker_exists "$name"
  case "$field" in
    role) ;;
    mode) [[ "$WORKER_MODES" == *" $val "* ]] || api_err invalid_name "Mode must be acceptEdits, plan or bypassPermissions." ;;
    *) bad_args "usage: worker-set <name> role|mode" ;;
  esac
  tmp="$(mktemp "$WORKERS_DIR/$name/meta.XXXXXX")"
  jq -c --arg f "$field" --arg v "${val:0:200}" '.[$f] = $v' "$WORKERS_DIR/$name/meta.json" >"$tmp" &&
    mv -f "$tmp" "$WORKERS_DIR/$name/meta.json" || { rm -f "$tmp"; api_err internal "Couldn't save the worker."; }
  api_ok "$(jq -cn --arg n "$name" '{saved:$n}')"
}

do_worker_remove() {
  worker_exists "${1:-}"
  end_login "worker-login-$1"
  rm -rf -- "${WORKERS_DIR:?}/$1"
  api_ok "$(jq -cn --arg n "$1" '{removed:$n}')"
}

do_worker_runs() {
  worker_exists "${1:-}"
  local f
  api_ok "$( { for f in "$WORKERS_DIR/$1"/tasks/*.json; do [[ -f "$f" ]] && cat "$f" && echo; done; } 2>/dev/null |
    jq -sc '{runs: (map({id, task:(.task // "" | .[0:300]), status, reply:(.reply // null | if . then .[0:4000] else . end), error:(.error // null), started:(.started // 0)}) | sort_by(-.started) | .[0:20])}')"
}
```

4. Add dispatch lines in `api_main` next to `doctor-start)`:
```bash
    worker-list)         [[ $# -eq 0 ]] || bad_args "worker-list takes no arguments"; do_worker_list ;;
    worker-add)          do_worker_add "$@" ;;
    worker-set)          do_worker_set "$@" ;;
    worker-remove)       do_worker_remove "$@" ;;
    worker-runs)         do_worker_runs "$@" ;;
```
and add `worker-add | worker-set` to the stdin-reading list (the `chat-pin-set | chat-open | …` case near line 2391, same pattern as its other entries).

- [ ] **Step 5: Run to verify it passes**

Run: `server/tests/test-api.sh 2>&1 | tail -30`
Expected: every `team workers` line `ok`, final failure count 0, and no earlier test regressed. Also run `shellcheck -S warning server/claude-setup.sh server/claude-launcher-api server/tests/test-api.sh` (if shellcheck is missing locally, CI runs it).

- [ ] **Step 6: Commit**

```bash
git add server/claude-setup.sh server/claude-launcher-api server/tests/test-api.sh
git commit -m "Team: worker store and worker-* server actions (SCRIPT_API 25)"
```
(End the message with the two attribution lines from the session reminder.)

---

### Task 2: Worker sign-in reuses the Claude login flow

**Files:**
- Modify: `server/claude-setup.sh` (`claude_logged_in`/`claude_creds_sig` ~lines 125 and 1075; `start_login_session` ~line 1156; new wrappers after `do_worker_runs`; dispatch; stdin list)
- Modify: `server/tests/stubs/claude` (login honours `CLAUDE_CONFIG_DIR`)
- Modify: `server/tests/test-api.sh`

**Interfaces:**
- Consumes: Task 1 `worker_exists`, `WORKERS_DIR`; existing `do_login_claude_start`, `do_login_claude_code`, `end_login`.
- Produces: `worker-login-start <name>` → `{"url","session":"worker-login-<name>"}`; `worker-login-code <name>` (code on stdin) → `{"logged_in":true}`. Success writes `$WORKERS_DIR/<name>/home/.credentials.json`, so `worker-list` shows `signed_in:true`.

- [ ] **Step 1: Make the stub's login write into the config dir, then write failing tests**

In `server/tests/stubs/claude`, change the credentials write to:
```bash
      d="${CLAUDE_CONFIG_DIR:-$HOME/.claude}"; mkdir -p "$d" && echo '{"claudeAiOauth":{}}' >"$d/.credentials.json"
```
(replacing the `mkdir -p "$HOME/.claude" && echo … >"$HOME/.claude/.credentials.json"` line).

Append to the `team workers` section of `server/tests/test-api.sh` (before the final `worker-remove` lines, or re-add a worker `ops` first):
```bash
api "worker-add ops" "Ops"
api "worker-login-start ops"
check "worker login gives a URL" "$(jqt '.ok and (.data.url | startswith("https://"))')"
grep -q "^worker-login-ops	" "$LIST"; [[ $? -ne 0 ]]; check "worker login not saved by autostart" $?
api "worker-login-code ops" "bad-code-789"
check "bad worker code refused" "$(jqt '.ok == false')"
api "worker-login-start ops"
api "worker-login-code ops" "good-code-789"
check "worker login succeeds" "$(jqt '.ok and .data.logged_in')"
[[ -s "$HOME/.config/claude-launcher/workers/ops/home/.credentials.json" ]]; check "creds landed in the worker's own dir" $?
api "worker-list"
check "worker shows signed in" "$(jqt '.data.workers[] | select(.name=="ops") | .signed_in')"
api "sessions"
grep -q "worker-login-ops" <<<"$OUT"; [[ $? -ne 0 ]]; check "login helper not listed as a session" $?
api "worker-remove ops"
```
(`$LIST` is the autostart list defined at the top of the test file. The earlier main-account login tests run before this section, so `$HOME/.claude/.credentials.json` may exist; the worker check above only asserts the worker's own file.)

- [ ] **Step 2: Run to verify it fails**

Run: `server/tests/test-api.sh 2>&1 | grep -B1 -A1 "worker login"`
Expected: FAIL (unknown actions).

- [ ] **Step 3: Point the shared login at a config dir**

Edit `server/claude-setup.sh`:

1. `claude_logged_in`:
```bash
claude_logged_in() { [[ -s "${LOGIN_CONFIG_DIR:-$HOME/.claude}/.credentials.json" ]]; }
```
2. `claude_creds_sig`:
```bash
claude_creds_sig() { stat -c '%Y:%s' "${LOGIN_CONFIG_DIR:-$HOME/.claude}/.credentials.json" 2>/dev/null || echo none; }
```
3. In `start_login_session` replace the `tmux new-session` line with:
```bash
  local envs=(-e "LOGIN_BIN=$bin")
  # Team: sign in a worker account into its own config dir.
  [[ -n "${LOGIN_CONFIG_DIR:-}" ]] && envs+=(-e "CLAUDE_CONFIG_DIR=$LOGIN_CONFIG_DIR")
  tmux new-session -d -s "$name" -x 1000 -y 60 "${envs[@]}" \
    bash -c 'env -u ANTHROPIC_API_KEY "$LOGIN_BIN" "$@"; echo "[exit $?]"; sleep 900' login "$@" >/dev/null 2>&1
```
4. After `do_worker_runs` add:
```bash
# Worker sign-in = the main account's login flow, aimed at the worker's folder and its own tmux session.
worker_login_target() {
  worker_exists "${1:-}"
  CLAUDE_LOGIN_SESSION="worker-login-$1"
  LOGIN_CONFIG_DIR="$WORKERS_DIR/$1/home"
}
do_worker_login_start() { worker_login_target "${1:-}"; do_login_claude_start; }
do_worker_login_code() { worker_login_target "${1:-}"; do_login_claude_code; }
```
5. Dispatch (`api_main`): `worker-login-start)  do_worker_login_start "$@" ;;` and `worker-login-code)   do_worker_login_code "$@" ;;`; add `worker-login-code` to the stdin-reading list.

Note: `do_login_claude_code` reads its code with `read_secret_line` itself, so the wrapper must not read stdin first.

- [ ] **Step 4: Run to verify it passes**

Run: `server/tests/test-api.sh 2>&1 | tail -30`
Expected: all worker login lines `ok`; the earlier main `login-claude-*` tests still pass; 0 failures.

- [ ] **Step 5: Commit**

```bash
git add server/claude-setup.sh server/tests/stubs/claude server/tests/test-api.sh
git commit -m "Team: sign a worker account in through the existing Claude login flow"
```

---

### Task 3: `clauderc-team` MCP server

**Files:**
- Create: `server/clauderc-team`
- Create: `server/tests/test-team.py`
- Modify: `server/tests/stubs/claude` (headless `-p` mode)
- Modify: `server/tests/test-api.sh` (run the Python test)

**Interfaces:**
- Consumes: Task 1 disk layout (`meta.json`, `home/`, `tasks/`) and Task 2 (`home/.credentials.json` means signed in).
- Produces: `clauderc-team mcp` speaks MCP over stdio. Tools: `list_workers {}`, `delegate {worker, task}` → text `task_id`, `wait {task_id, timeout_s?}` → worker reply text (or "still running"), `reply {task_id, message}` → new `task_id`. Task JSON at `tasks/<id>.json`: `{id, worker, task, prompt, cwd, status: running|done|failed, reply?, error?, session_id?, started, finished?}`.

- [ ] **Step 1: Stub support for headless runs**

Add to the `case` in `server/tests/stubs/claude` (before `*)`):
```bash
  "-p --output-format")
    prompt="$(cat)"
    sid="sess-1"; [[ " $* " == *" --resume "* ]] && sid="$(sed 's/.*--resume \([^ ]*\).*/\1/' <<<"$*")"
    if [[ "$prompt" == *FAIL* ]]; then echo '{"is_error":true,"result":"stub failure","session_id":"'"$sid"'"}'; exit 1; fi
    jq -cn --arg r "cfg=${CLAUDE_CONFIG_DIR:-none} key=${ANTHROPIC_API_KEY:-unset} cwd=$PWD resume=${sid} prompt=$prompt" --arg s "$sid" \
      '{is_error:false,result:$r,session_id:$s}' ;;
```
(`$1 ${2:-}` for `claude -p --output-format json …` is `-p --output-format`.)

- [ ] **Step 2: Write the failing test**

Create `server/tests/test-team.py`:

```python
#!/usr/bin/env python3
"""Self-check for clauderc-team: drives the MCP server over stdio with a stub `claude`.
Run: server/tests/test-team.py   (needs the stub on PATH or CLAUDERC_CLAUDE set)."""
import json, os, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
tmp = Path(tempfile.mkdtemp())
cfg = tmp / "cfg" / "claude-launcher" / "workers"
env = {**os.environ, "XDG_CONFIG_HOME": str(tmp / "cfg"), "CLAUDERC_CLAUDE": str(HERE / "stubs" / "claude"),
       "ANTHROPIC_API_KEY": "secret-should-not-leak", "STUB_STATE": str(tmp / "stub")}
for name, signed in (("research", True), ("ui", False)):
    (cfg / name / "home").mkdir(parents=True)
    (cfg / name / "tasks").mkdir()
    (cfg / name / "meta.json").write_text(json.dumps({"role": f"{name} role", "mode": "plan"}))
    if signed:
        (cfg / name / "home" / ".credentials.json").write_text("{}")

proc = subprocess.Popen([sys.executable, str(HERE.parent / "clauderc-team"), "mcp"], stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, text=True, env=env, cwd=tmp)
_id = 0
def rpc(method, params=None):
    global _id
    _id += 1
    proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": _id, "method": method, "params": params or {}}) + "\n")
    proc.stdin.flush()
    return json.loads(proc.stdout.readline())
def tool(name, **args):
    r = rpc("tools/call", {"name": name, "arguments": args})["result"]
    return r.get("isError", False), r["content"][0]["text"]

r = rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "t", "version": "0"}})["result"]
assert r["serverInfo"]["name"] == "clauderc-team" and "list_workers" in r["instructions"], r
assert {t["name"] for t in rpc("tools/list")["result"]["tools"]} == {"list_workers", "delegate", "wait", "reply"}

err, text = tool("list_workers")
ws = json.loads(text)
assert not err and {w["name"]: w["signed_in"] for w in ws} == {"research": True, "ui": False}, ws

err, text = tool("delegate", worker="nobody", task="x")
assert err and "nobody" in text, text
err, text = tool("delegate", worker="ui", task="x")
assert err and "sign" in text.lower(), text                      # not signed in
err, text = tool("delegate", worker="../research", task="x")
assert err, text                                                 # bad name

err, tid = tool("delegate", worker="research", task="summarise the repo")
assert not err and len(tid) == 8, tid
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "summarise the repo" in text, text
assert f"cfg={cfg / 'research' / 'home'}" in text, text          # the worker's own config dir
assert "key=unset" in text and f"cwd={tmp.resolve()}" in text, text  # no API key; caller's folder

err, tid2 = tool("reply", task_id=tid, message="and the tests")
assert not err, tid2
err, text = tool("wait", task_id=tid2, timeout_s=30)
assert not err and "resume=sess-1" in text and "and the tests" in text, text

err, tid3 = tool("delegate", worker="research", task="please FAIL")
err, text = tool("wait", task_id=tid3, timeout_s=30)
assert err and "stub failure" in text, text                       # failed run is a tool error

# A task left 'running' by a previous server process is reported lost, never waited on forever.
(cfg / "research" / "tasks" / "dead0000.json").write_text(json.dumps(
    {"id": "dead0000", "worker": "research", "status": "running", "started": 1}))
err, text = tool("wait", task_id="dead0000", timeout_s=5)
assert err and "lost" in text.lower(), text

err, text = tool("wait", task_id="nope1234")
assert err, text
proc.stdin.close(); proc.wait(timeout=5)
print("test-team: ok")
```

Make it executable: `chmod +x server/tests/test-team.py`.

- [ ] **Step 3: Run to verify it fails**

Run: `python3 server/tests/test-team.py`
Expected: traceback (`clauderc-team` does not exist yet).

- [ ] **Step 4: Write the MCP server**

Create `server/clauderc-team` (then `chmod +x`):

```python
#!/usr/bin/env python3
"""clauderc-team: MCP server (stdio) that lets the main Claude hand tasks to the
owner's other Claude accounts ("workers") set up in the cLaudeRC app's Team screen.

A task is a headless `claude -p` run in the caller's project folder under the
worker's own CLAUDE_CONFIG_DIR. State lives in <workers>/<name>/tasks/<id>.json.
"""
import json, os, re, subprocess, sys, threading, time, uuid
from pathlib import Path

WORKERS = Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config") / "claude-launcher" / "workers"
CLAUDE = os.environ.get("CLAUDERC_CLAUDE", "claude")
RUN_TIMEOUT = 1800        # seconds one worker run may take
NAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{0,29}$")
ID_RE = re.compile(r"^[0-9a-f]{8}$")
live = set()              # task ids run by this process; anything else 'running' on disk is lost
# ponytail: tasks die with this process (one MCP server per Claude session); persist/detach if long runs must survive restarts.

INSTRUCTIONS = (
    "Hand work to the owner's other Claude accounts. Call list_workers first, then follow the owner's routing "
    "rules from this chat. Start every independent piece with delegate, then collect each with wait. "
    "Workers cannot ask questions and share this project folder: give each a complete, self-contained task "
    "and avoid sending two workers to edit the same files. Use reply to continue a worker's conversation."
)

TOOLS = [
    {"name": "list_workers", "description": "List the worker accounts: name, role, permission mode, signed_in.",
     "inputSchema": {"type": "object", "properties": {}}},
    {"name": "delegate", "description": "Start a task on a worker. Returns a task_id; collect the answer with wait.",
     "inputSchema": {"type": "object", "required": ["worker", "task"], "properties": {
         "worker": {"type": "string"}, "task": {"type": "string", "description": "Complete, self-contained instructions."}}}},
    {"name": "wait", "description": "Wait for a task and return the worker's final reply.",
     "inputSchema": {"type": "object", "required": ["task_id"], "properties": {
         "task_id": {"type": "string"}, "timeout_s": {"type": "integer", "description": "Default 600, max 900."}}}},
    {"name": "reply", "description": "Send a follow-up to a finished task's worker (same conversation). Returns a new task_id.",
     "inputSchema": {"type": "object", "required": ["task_id", "message"], "properties": {
         "task_id": {"type": "string"}, "message": {"type": "string"}}}},
]


class ToolError(Exception):
    pass


def worker_dir(name):
    if not isinstance(name, str) or not NAME_RE.match(name) or not (WORKERS / name / "meta.json").is_file():
        raise ToolError(f"No worker named {name!r}. Call list_workers.")
    return WORKERS / name


def list_workers():
    out = []
    for meta in sorted(WORKERS.glob("*/meta.json")):
        m = json.loads(meta.read_text())
        out.append({"name": meta.parent.name, "role": m.get("role", ""), "mode": m.get("mode", "acceptEdits"),
                    "signed_in": (meta.parent / "home" / ".credentials.json").is_file()})
    return out


def task_path(worker, tid):
    return WORKERS / worker / "tasks" / f"{tid}.json"


def save(t):
    p = task_path(t["worker"], t["id"])
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(t))
    tmp.replace(p)


def find_task(tid):
    if not isinstance(tid, str) or not ID_RE.match(tid):
        raise ToolError(f"Bad task_id {tid!r}.")
    for p in WORKERS.glob(f"*/tasks/{tid}.json"):
        return json.loads(p.read_text())
    raise ToolError(f"No task {tid}.")


def run_task(t, resume):
    w = WORKERS / t["worker"]
    mode = json.loads((w / "meta.json").read_text()).get("mode", "acceptEdits")
    cmd = [CLAUDE, "-p", "--output-format", "json", "--permission-mode", mode] + (["--resume", resume] if resume else [])
    env = {k: v for k, v in os.environ.items() if k != "ANTHROPIC_API_KEY"}
    env["CLAUDE_CONFIG_DIR"] = str(w / "home")
    try:
        p = subprocess.run(cmd, input=t["prompt"], cwd=t["cwd"], env=env, capture_output=True, text=True, timeout=RUN_TIMEOUT)
        try:
            out = json.loads(p.stdout)
        except ValueError:
            out = {}
        t["session_id"] = out.get("session_id") or resume
        if p.returncode or out.get("is_error"):
            t.update(status="failed", error=(out.get("result") or p.stderr or f"claude exited {p.returncode}")[-2000:])
        else:
            t.update(status="done", reply=out.get("result", ""))
    except subprocess.TimeoutExpired:
        t.update(status="failed", error=f"Timed out after {RUN_TIMEOUT // 60} minutes.")
    except Exception as e:  # e.g. claude not installed
        t.update(status="failed", error=str(e))
    t["finished"] = time.time()
    save(t)
    live.discard(t["id"])


def start(worker, prompt, resume=None):
    w = worker_dir(worker)
    if not (w / "home" / ".credentials.json").is_file():
        raise ToolError(f"Worker {worker} isn't signed in. Sign it in from the Team screen in the cLaudeRC app.")
    if not isinstance(prompt, str) or not prompt.strip():
        raise ToolError("The task is empty.")
    t = {"id": uuid.uuid4().hex[:8], "worker": worker, "task": prompt[:300], "prompt": prompt,
         "cwd": os.getcwd(), "status": "running", "started": time.time()}
    save(t)
    live.add(t["id"])
    threading.Thread(target=run_task, args=(t, resume), daemon=True).start()
    return t["id"]


def wait(tid, timeout_s=600):
    deadline = time.time() + min(int(timeout_s or 600), 900)
    while True:
        t = find_task(tid)
        if t["status"] == "done":
            return t.get("reply", "")
        if t["status"] == "failed":
            raise ToolError(t.get("error", "The worker failed."))
        if tid not in live:
            raise ToolError("This task was lost (the Team server restarted while it ran). Delegate it again.")
        if time.time() >= deadline:
            return f"Still running. Call wait again with task_id {tid}."
        time.sleep(1)


def call_tool(name, a):
    if name == "list_workers":
        return json.dumps(list_workers())
    if name == "delegate":
        return start(a.get("worker"), a.get("task"))
    if name == "wait":
        return wait(a.get("task_id"), a.get("timeout_s"))
    if name == "reply":
        t = find_task(a.get("task_id"))
        if t["status"] != "done" or not t.get("session_id"):
            raise ToolError("Only a finished task can be replied to.")
        return start(t["worker"], a.get("message"), resume=t["session_id"])
    raise ToolError(f"Unknown tool {name}.")


def handle(msg):
    method, mid, params = msg.get("method"), msg.get("id"), msg.get("params") or {}
    if mid is None:
        return None  # notification
    if method == "initialize":
        res = {"protocolVersion": params.get("protocolVersion", "2025-03-26"), "capabilities": {"tools": {}},
               "serverInfo": {"name": "clauderc-team", "version": "1"}, "instructions": INSTRUCTIONS}
    elif method == "tools/list":
        res = {"tools": TOOLS}
    elif method == "tools/call":
        try:
            res = {"content": [{"type": "text", "text": str(call_tool(params.get("name"), params.get("arguments") or {}))}]}
        except ToolError as e:
            res = {"isError": True, "content": [{"type": "text", "text": str(e)}]}
    elif method == "ping":
        res = {}
    else:
        return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": f"Unknown method {method}"}}
    return {"jsonrpc": "2.0", "id": mid, "result": res}


def main():
    if sys.argv[1:] != ["mcp"]:
        sys.exit("usage: clauderc-team mcp")
    for line in sys.stdin:
        if line.strip():
            out = handle(json.loads(line))
            if out:
                print(json.dumps(out), flush=True)


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run to verify it passes**

Run: `python3 server/tests/test-team.py`
Expected: `test-team: ok`. If a worker run hangs, the stub `claude` isn't executable or `CLAUDERC_CLAUDE` is wrong.

- [ ] **Step 6: Hook into the server test run**

In `server/tests/test-api.sh`, after the `team workers` section add:
```bash
echo "team MCP server"
OUT="$(python3 "$HERE/test-team.py" 2>&1)"; check "clauderc-team MCP self-check" $?
```
Run `server/tests/test-api.sh 2>&1 | tail -15` → 0 failures.

- [ ] **Step 7: Commit**

```bash
git add server/clauderc-team server/tests/test-team.py server/tests/stubs/claude server/tests/test-api.sh
git commit -m "Team: clauderc-team MCP server (delegate/wait/reply/list_workers)"
```

---

### Task 4: Installer registers the MCP server

**Files:**
- Modify: `server/install.sh` (download list line 35; `put` lines ~47-52; after the Bun block)
- Modify: `server/README.md` (one paragraph) and `PLAN.md` (task entry)

**Interfaces:**
- Consumes: Task 3 `server/clauderc-team`.
- Produces: `~/.local/bin/clauderc-team` and a user-scope MCP registration `clauderc-team`.

- [ ] **Step 1: Failing check**

Run: `grep -c clauderc-team server/install.sh`
Expected: `0`.

- [ ] **Step 2: Edit `server/install.sh`**

1. Add `clauderc-team` to the `for f in claude-setup.sh … youtube-upload; do curl …` list.
2. After `put "$tmp/youtube-upload" "$HOME/.local/bin/youtube-upload"` add:
```bash
  put "$tmp/clauderc-team" "$HOME/.local/bin/clauderc-team"
```
3. After the Bun block add:
```bash
  # Team: the MCP server the main Claude uses to hand work to other accounts (user scope, absolute path).
  if command -v claude >/dev/null && command -v python3 >/dev/null; then
    claude mcp get clauderc-team >/dev/null 2>&1 ||
      claude mcp add -s user clauderc-team -- "$HOME/.local/bin/clauderc-team" mcp >/dev/null 2>&1 ||
      echo "Note: the Team MCP server wasn't registered (claude mcp add failed)." >&2
  fi
```

- [ ] **Step 3: Verify**

Run: `bash -n server/install.sh && shellcheck -S warning server/install.sh && grep -c clauderc-team server/install.sh`
Expected: no output from the first two, then `4` or more. Check the test harness still passes: `server/tests/test-api.sh 2>&1 | tail -5` (it may exercise `install.sh`; 0 failures).

- [ ] **Step 4: Docs**

Add to `server/README.md` a short "Team" paragraph (what `clauderc-team` is, that workers live under `~/.config/claude-launcher/workers/`, that accounts are signed in from the app). Add a task line and a Decisions line to `PLAN.md` for Team workers.

- [ ] **Step 5: Commit**

```bash
git add server/install.sh server/README.md PLAN.md
git commit -m "Team: installer puts clauderc-team on PATH and registers it at user scope"
```

---

### Task 5: App API, models and ViewModel

**Files:**
- Modify: `app/src/main/java/life/mygig/clauderc/api/Models.kt` (append)
- Modify: `app/src/main/java/life/mygig/clauderc/api/LauncherApi.kt` (after `mcpAuthCancel`, ~line 134; name regex near `PROJECT_NAME_RE`)
- Modify: `app/src/main/java/life/mygig/clauderc/api/Updates.kt` (`MIN_SCRIPT_API = 25`)
- Modify: `app/src/main/java/life/mygig/clauderc/ui/MainViewModel.kt`
- Create: `app/src/test/java/life/mygig/clauderc/TeamModelsTest.kt`

**Interfaces:**
- Consumes: server actions from Tasks 1–2.
- Produces (used by Task 6):
  - `data class Worker(name, role, mode, signedIn)`, `data class WorkerRun(id, task, status, reply: String?, error: String?, started: Double)`
  - `LauncherApi`: `workers(): WorkersData`, `workerAdd(name, role)`, `workerSet(name, field, value)`, `workerRemove(name)`, `workerLoginStart(name): LoginUrl`, `workerLoginCode(name, code): LoginDone`, `workerRuns(name): WorkerRunsData`; `WORKER_NAME_RE`.
  - `MainViewModel`: `workers: StateFlow<List<Worker>?>`, `workerLogin: StateFlow<Pair<String, LoginUrl>?>`, `workerRuns: StateFlow<Pair<String, List<WorkerRun>>?>`, `loadWorkers()`, `addWorker(name, role)`, `setWorker(name, field, value)`, `removeWorker(name)`, `workerLoginStart(name)`, `workerLoginCode(name, code)`, `closeWorkerLogin()`, `showWorkerRuns(name)`, `closeWorkerRuns()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/life/mygig/clauderc/TeamModelsTest.kt`:

```kotlin
package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.WorkerRunsData
import life.mygig.clauderc.api.WorkersData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesWorkerList() {
        val d = json.decodeFromString<WorkersData>(
            """{"workers":[{"name":"research","role":"Docs & \"quotes\"","mode":"plan","signed_in":true},{"name":"ui"}]}""",
        )
        assertEquals("Docs & \"quotes\"", d.workers[0].role)
        assertTrue(d.workers[0].signedIn)
        assertEquals("acceptEdits", d.workers[1].mode)
        assertFalse(d.workers[1].signedIn)
    }

    @Test fun parsesRuns() {
        val d = json.decodeFromString<WorkerRunsData>(
            """{"runs":[{"id":"aaaa1111","task":"t","status":"done","reply":"hi","error":null,"started":100.5}]}""",
        )
        assertEquals("hi", d.runs[0].reply)
        assertNull(d.runs[0].error)
    }

    @Test fun workerNameRule() {
        for (ok in listOf("research", "UI_2", "a-b")) assertTrue(ok, WORKER_NAME_RE.matches(ok))
        for (bad in listOf("", "../x", "-x", ".x", "a b", "1x", "x".repeat(31))) assertFalse(bad, WORKER_NAME_RE.matches(bad))
    }
}
```

- [ ] **Step 2: Models and API**

Append to `Models.kt`:
```kotlin
@Serializable
data class Worker(
    val name: String,
    val role: String = "",
    val mode: String = "acceptEdits",
    @SerialName("signed_in") val signedIn: Boolean = false,
)

@Serializable
data class WorkersData(val workers: List<Worker> = emptyList())

@Serializable
data class WorkerRun(
    val id: String,
    val task: String = "",
    val status: String = "",
    val reply: String? = null,
    val error: String? = null,
    val started: Double = 0.0,
)

@Serializable
data class WorkerRunsData(val runs: List<WorkerRun> = emptyList())
```
In `LauncherApi.kt` below `PROJECT_NAME_RE`:
```kotlin
/** Same rule as the server's worker names (Team). */
val WORKER_NAME_RE = Regex("^[A-Za-z][A-Za-z0-9_-]{0,29}$")
```
and after `mcpAuthCancel`:
```kotlin
    // Team: extra Claude accounts the main Claude can delegate to.
    suspend fun workers(): WorkersData = call("worker-list")
    suspend fun workerAdd(name: String, role: String): JsonObject { requireWorker(name); return call("worker-add $name", stdin = role.trim().replace('\n', ' ')) }
    suspend fun workerSet(name: String, field: String, value: String): JsonObject {
        requireWorker(name)
        require(field == "role" || field == "mode")
        return call("worker-set $name $field", stdin = value.trim().replace('\n', ' '))
    }
    suspend fun workerRemove(name: String): JsonObject { requireWorker(name); return call("worker-remove $name") }
    suspend fun workerLoginStart(name: String): LoginUrl { requireWorker(name); return call("worker-login-start $name", timeoutMs = 60_000) }
    suspend fun workerLoginCode(name: String, code: String): LoginDone { requireWorker(name); return call("worker-login-code $name", stdin = code.trim(), timeoutMs = 120_000) }
    suspend fun workerRuns(name: String): WorkerRunsData { requireWorker(name); return call("worker-runs $name") }
    private fun requireWorker(name: String) = require(WORKER_NAME_RE.matches(name)) { "Bad worker name" }
```
`Updates.kt`: `MIN_SCRIPT_API = 24` → `25`.

- [ ] **Step 3: ViewModel**

In `MainViewModel.kt`, next to the `_mcpAuth` block (~line 494) add (imports `Worker`, `WorkerRun`, `LoginUrl` as needed):

```kotlin
    // ---- Team (extra Claude accounts) ---------------------------------------------

    private val _workers = MutableStateFlow<List<Worker>?>(null)
    val workers = _workers.asStateFlow()
    private val _workerLogin = MutableStateFlow<Pair<String, LoginUrl>?>(null)
    val workerLogin = _workerLogin.asStateFlow()
    private val _workerRuns = MutableStateFlow<Pair<String, List<WorkerRun>>?>(null)
    val workerRuns = _workerRuns.asStateFlow()

    fun loadWorkers() = action("Loading team…") { _workers.value = api.workers().workers }
    fun addWorker(name: String, role: String) = action("Adding $name…") {
        api.workerAdd(name, role)
        _workers.value = api.workers().workers
    }
    fun setWorker(name: String, field: String, value: String) = action("Saving…") {
        api.workerSet(name, field, value)
        _workers.value = api.workers().workers
    }
    fun removeWorker(name: String) = action("Removing $name…") {
        api.workerRemove(name)
        _workers.value = api.workers().workers
    }
    fun workerLoginStart(name: String) = action("Starting sign-in for $name…") {
        _workerLogin.value = name to api.workerLoginStart(name)
    }
    fun workerLoginCode(name: String, code: String) = action("Checking code…") {
        api.workerLoginCode(name, code)
        _workerLogin.value = null
        _workers.value = api.workers().workers
    }
    // ponytail: the server's login helper session just times out; add a cancel action if stale ones annoy.
    fun closeWorkerLogin() { _workerLogin.value = null }
    fun showWorkerRuns(name: String) = action("Loading runs…") { _workerRuns.value = name to api.workerRuns(name).runs }
    fun closeWorkerRuns() { _workerRuns.value = null }
```

- [ ] **Step 4: Commit and let CI verify (no local Gradle)**

```bash
git add app/src/main app/src/test
git commit -m "Team: app API, models and ViewModel for worker accounts"
```
Push happens in Task 7; the unit test runs in CI's `./gradlew testDebugUnitTest`.

---

### Task 6: Team dialog on the Claude tab

**Files:**
- Create: `app/src/main/java/life/mygig/clauderc/ui/screens/TeamDialog.kt`
- Modify: `app/src/main/java/life/mygig/clauderc/ui/screens/ClaudeScreen.kt` (state + card + dialog call)

**Interfaces:**
- Consumes: Task 5 ViewModel members; existing `CardBox`, `SectionLabel`, `OneLine` (`ui/components/Tiles.kt`), `openUrl` (`ui/Friendly.kt`).
- Produces: `@Composable fun TeamDialog(vm: MainViewModel, onDismiss: () -> Unit)`.

- [ ] **Step 1: Write the dialog**

Create `TeamDialog.kt`:

```kotlin
package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.Worker
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.openUrl

private val MODES = listOf("acceptEdits", "plan", "bypassPermissions")
private fun modeHint(m: String) = when (m) {
    "acceptEdits" -> "edits files, can't run commands"
    "plan" -> "read-only: research and plans"
    else -> "runs anything without asking"
}

/** Extra Claude accounts ("workers") the main Claude can hand work to through the clauderc-team MCP server. */
@Composable
fun TeamDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val workers by vm.workers.collectAsState()
    val login by vm.workerLogin.collectAsState()
    val runs by vm.workerRuns.collectAsState()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadWorkers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Team") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Other Claude accounts of yours. In the main chat, tell Claude which kind of work goes to which worker " +
                        "(for example \"research goes to research, UI goes to ui\") and it hands tasks over for you.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (workers == null) Text("Loading…")
                workers?.forEach { w -> WorkerCard(vm, w, onRemove = { confirmRemove = w.name }) }
                if (workers?.isEmpty() == true) Text("No workers yet.")
                Text("Add a worker", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.trim() }, label = { Text("Name (research, coding, ui…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.isNotEmpty() && !WORKER_NAME_RE.matches(name),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = role, onValueChange = { role = it }, label = { Text("What it's for") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.addWorker(name, role); name = ""; role = "" },
                    enabled = WORKER_NAME_RE.matches(name), modifier = Modifier.fillMaxWidth(),
                ) { Text("Add worker") }
            }
        },
    )

    login?.let { (who, url) ->
        AlertDialog(
            onDismissRequest = { vm.closeWorkerLogin(); code = "" },
            confirmButton = {
                TextButton(onClick = { vm.workerLoginCode(who, code); code = "" }, enabled = code.length >= 4) { Text("Submit code") }
            },
            dismissButton = { TextButton(onClick = { vm.closeWorkerLogin(); code = "" }) { Text("Cancel") } },
            title = { Text("Sign in $who") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Use the Claude account meant for this worker (sign out of other accounts in your browser first, or use a private tab).")
                    OutlinedButton(onClick = { openUrl(context, url.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.trim() }, label = { Text("Code") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
            },
        )
    }

    runs?.let { (who, list) ->
        AlertDialog(
            onDismissRequest = vm::closeWorkerRuns,
            confirmButton = { TextButton(onClick = vm::closeWorkerRuns) { Text("Close") } },
            title = { Text("$who: recent tasks") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isEmpty()) Text("Nothing delegated yet.")
                    list.forEach { r ->
                        CardBox {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${r.status} · ${r.task}", style = MaterialTheme.typography.labelMedium)
                                Text(r.reply ?: r.error ?: "Running…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
        )
    }

    confirmRemove?.let { who ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            confirmButton = { TextButton(onClick = { vm.removeWorker(who); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep") } },
            title = { Text("Remove $who?") },
            text = { Text("Signs the account out on the server and forgets its past tasks.") },
        )
    }
}

@Composable
private fun WorkerCard(vm: MainViewModel, w: Worker, onRemove: () -> Unit) {
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(w.name + if (w.signedIn) "" else " · not signed in", style = MaterialTheme.typography.titleSmall)
            if (w.role.isNotBlank()) Text(w.role, style = MaterialTheme.typography.bodySmall)
            Text("Mode: ${w.mode} (${modeHint(w.mode)})", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.workerLoginStart(w.name) }) { Text(if (w.signedIn) "Re-sign in" else "Sign in") }
                OutlinedButton(onClick = { vm.setWorker(w.name, "mode", MODES[(MODES.indexOf(w.mode) + 1) % MODES.size]) }) { Text("Mode") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.showWorkerRuns(w.name) }) { Text("Tasks") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}
```
(`bypassPermissions` is reachable only by cycling Mode; its hint text says it runs anything.)

- [ ] **Step 2: Hook into the Claude tab**

In `ClaudeScreen.kt`: add `var team by remember { mutableStateOf(false) }` beside the other `remember` states, add this card right after the "Claude Code … Doctor" `fullItem` block:

```kotlin
            fullItem {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Team", fontWeight = FontWeight.SemiBold)
                            Text("Sign in other Claude accounts and let the main Claude hand them work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { team = true }) { Text("Open") }
                    }
                }
            }
```
and after the grid/PullToRefresh block (next to where other dialogs such as `install`/`custom` are rendered in this composable) add `if (team) TeamDialog(vm) { team = false }`. Add `import life.mygig.clauderc.ui.screens.*` is unnecessary (same package).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/life/mygig/clauderc/ui/screens
git commit -m "Team: dialog on the Claude tab to add, sign in, configure and remove workers"
```

---

### Task 7: Ship it

- [ ] **Step 1: Push the branch and open the PR**

```bash
git push -u origin claude/team-workers-spec
gh pr create --title "Team: delegate work across several Claude accounts" --body "<what changed, how it was tested, manual acceptance below>"
```
(Body ends with the PR attribution lines from the session reminder.)

- [ ] **Step 2: Watch CI**

Run: `gh pr checks --watch`
Expected: secrets-scan, server (shellcheck + `test-api.sh` incl. `test-team.py`), android (unit tests + build), Analyze, CodeQL all pass. Fix and push on failure.

- [ ] **Step 3: Merge and watch the main build only**

```bash
gh pr merge --squash --delete-branch
gh run list -b main -w build --limit 1
```
Update `PLAN.md` (check off, "Current task"), commit "Plan: Team workers shipped", say "Checkpoint saved - good time to /clear."

- [ ] **Step 4: Manual acceptance (owner, on the phone, after Update now)**

1. Claude tab › Team › add worker `research` › Sign in › finish the code flow with a second Claude account › card shows signed in.
2. Server: `claude mcp get clauderc-team` shows it at user scope (restart sessions via **Sessions › Restart** so chats see the new MCP server).
3. In a project chat: "Use `research` for anything that needs web research; ask it to summarise what this repo does, then show me its answer." Expect `delegate` + `wait` tool calls and the worker's reply.
4. Team › Tasks shows that run.

Things to confirm during step 1 and 3 (the CLI behaviour could not be tested without a second real account): the worker's login writes `.credentials.json` inside the worker's `home/`; headless `claude -p` runs in a project folder it has not been trusted in; `--permission-mode acceptEdits` is accepted by `-p`. If any fails, fix `claude-setup.sh`/`clauderc-team` accordingly and note it in `CLAUDE.md` Project notes.
