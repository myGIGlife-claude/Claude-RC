#!/usr/bin/env python3
"""Self-check for clauderc-team: drives the MCP server over stdio with a stub `claude`.
Run: server/tests/test-team.py   (needs jq for the stub)."""
import json, os, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
tmp = Path(tempfile.mkdtemp())
cfg = tmp / "cfg" / "claude-launcher" / "workers"
env = {**os.environ, "XDG_CONFIG_HOME": str(tmp / "cfg"), "CLAUDERC_CLAUDE": str(HERE / "stubs" / "claude"),
       "ANTHROPIC_API_KEY": "secret-should-not-leak", "CLAUDE_CODE_OAUTH_TOKEN": "main-token", "CLAUDECODE": "1", "STUB_STATE": str(tmp / "stub")}
for name, signed in (("research", True), ("ui", False)):
    (cfg / name / "home").mkdir(parents=True)
    (cfg / name / "tasks").mkdir()
    (cfg / name / "meta.json").write_text(json.dumps({"role": f"{name} role", "mode": "plan"}))
    if signed:
        (cfg / name / "home" / ".credentials.json").write_text("{}")

import re


def attach(folder, names):
    """What the app's Cluster button writes: the workers attached to a project folder."""
    f = cfg.parent / "attach" / (re.sub(r"[^A-Za-z0-9]", "-", str(Path(folder).resolve())) + ".json")
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(names))


def spawn(folder):
    return subprocess.Popen([sys.executable, str(HERE.parent / "clauderc-team"), "mcp"], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, text=True, env=env, cwd=folder)


proc = spawn(tmp)
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
assert {t["name"] for t in rpc("tools/list")["result"]["tools"]} == {"list_workers", "delegate", "wait", "reply", "review", "merge", "discard"}

# Nothing is attached to this project yet: no workers visible, none usable.
err, text = tool("list_workers")
assert not err and json.loads(text) == [], text
err, text = tool("delegate", worker="research", task="x")
assert err and "attached" in text, text
attach(tmp, {"research": {"role": "Docs for this project", "mode": "plan"}, "ui": {}})

err, text = tool("list_workers")
ws = json.loads(text)
assert not err and {w["name"]: w["signed_in"] for w in ws} == {"research": True, "ui": False}, ws
assert [(w["role"], w["mode"]) for w in ws if w["name"] == "research"] == [("Docs for this project", "plan")], ws   # per-project override
assert [(w["role"], w["mode"]) for w in ws if w["name"] == "ui"] == [("ui role", "plan")], ws                      # falls back to the worker's own

err, text = tool("delegate", worker="nobody", task="x")
assert err and "nobody" in text, text
err, text = tool("delegate", worker="ui", task="x")
assert err and "sign" in text.lower(), text                      # not signed in
err, text = tool("delegate", worker="../research", task="x")
assert err, text                                                 # bad name

err, tid = tool("delegate", worker="research", task="summarise the repo")
assert not err and len(tid) == 8, tid
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "summarise the repo" in text and "Your role in this project: Docs for this project" in text, text
assert f"cfg={cfg / 'research' / 'home'}" in text, text          # the worker's own config dir
assert "key=unset" in text and "tok=unset" in text and "cc=unset" in text, text    # main account's tokens never reach a worker
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

# Bad input must not kill the server (and with it every running task).
err, text = tool("wait", task_id=tid, timeout_s="soon")
assert err, text
proc.stdin.write("this is not json\n[]\n{\"method\":\"initialize\",\"id\":1,\"params\":\"x\"}\n")
proc.stdin.flush()
err, text = tool("list_workers")
assert not err and "research" in text, text
proc.stdin.close()
proc.wait(timeout=5)

# In a git project each task gets its own branch and worktree; the main Claude reviews, then merges or discards.
repo = tmp / "repo"
repo.mkdir()
g = lambda *a, c=repo: subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *a], cwd=c, capture_output=True, text=True, check=True).stdout
g("init", "-q"); (repo / "a.txt").write_text("one\n"); g("add", "."); g("commit", "-qm", "init")
attach(repo, {"research": {}})
proc = spawn(repo)
_id = 0
rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "t", "version": "0"}})
err, tid = tool("delegate", worker="research", task="make a file WRITEFILE")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and f"cluster/research/{tid}" in text and "cluster-out.txt" in text, text
assert not (repo / "cluster-out.txt").exists(), "the worker must not touch the main checkout"
assert f"cwd={cfg / 'research' / 'trees' / tid}" in text, text
err, text = tool("review", task_id=tid)
assert not err and "+from sess-1" in text, text
err, text = tool("merge", task_id=tid)
assert not err and "Merged" in text and (repo / "cluster-out.txt").read_text() == "from sess-1\n", text
assert not (cfg / "research" / "trees" / tid).exists() and f"cluster/research/{tid}" not in g("branch"), "merge cleans up"
err, text = tool("merge", task_id=tid)
assert err, text                                                  # already merged
err, tid = tool("delegate", worker="research", task="make a file WRITEFILE again")
tool("wait", task_id=tid, timeout_s=30)
err, text = tool("discard", task_id=tid)
assert not err and not (repo / "cluster-out.txt").read_text().startswith("again") and f"cluster/research/{tid}" not in g("branch"), text
(repo / "cluster-out.txt").write_text("main version\n"); g("commit", "-qam", "main moves")        # a conflicting edit on both sides
err, t1 = tool("delegate", worker="research", task="edit WRITEFILE")
tool("wait", task_id=t1, timeout_s=30)
(repo / "cluster-out.txt").write_text("conflict\n"); g("add", "."); g("commit", "-qm", "main also writes it")
err, text = tool("merge", task_id=t1)
assert err and "nothing changed" in text, text
assert g("status", "--porcelain").strip() == "", "a failed merge leaves the checkout clean"
proc.stdin.close()
proc.wait(timeout=5)
print("test-team: ok")
