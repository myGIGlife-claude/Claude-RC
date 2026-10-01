#!/usr/bin/env python3
"""Self-check for clauderc-team: drives the MCP server over stdio with a stub `claude`.
Run: server/tests/test-team.py   (needs jq for the stub)."""
import json, os, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
tmp = Path(tempfile.mkdtemp())
cfg = tmp / "cfg" / "claude-launcher" / "workers"
env = {**os.environ, "XDG_CONFIG_HOME": str(tmp / "cfg"), "CLAUDERC_CLAUDE": str(HERE / "stubs" / "claude"),
       "ANTHROPIC_API_KEY": "secret-should-not-leak", "CLAUDE_CODE_OAUTH_TOKEN": "main-token", "CLAUDECODE": "1", "STUB_STATE": str(tmp / "stub"),
       "CLAUDERC_TEST_SECRET": "service-token"}
cfg.parent.mkdir(parents=True, exist_ok=True)
(cfg.parent / "env").write_text("export CLAUDERC_TEST_SECRET='service-token'\n")
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
tool("discard", task_id=t1)

# A git project that can't get its own branch must not run the worker in the main checkout.
(cfg / "research" / "trees").rename(cfg / "research" / "trees.bak")
(cfg / "research" / "trees").write_text("in the way")
err, text = tool("delegate", worker="research", task="edit WRITEFILE")
assert err and "nothing was started" in text, text
assert not (repo / "cluster-out.txt").read_text().startswith("from sess-1\nfrom"), "the main checkout was touched"
(cfg / "research" / "trees").unlink()
(cfg / "research" / "trees.bak").rename(cfg / "research" / "trees")

# A failed run keeps its partial work on the branch; it can be reviewed and discarded.
err, tid = tool("delegate", worker="research", task="half done WRITEFILE FAIL")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert err and f"cluster/research/{tid}" in text and "cluster-out.txt" in text, text
err, text = tool("review", task_id=tid)
assert not err and "cluster-out.txt" in text, text
err, text = tool("discard", task_id=tid)
assert not err and not (cfg / "research" / "trees" / tid).exists() and f"cluster/research/{tid}" not in g("branch"), text

# Merging never runs the repo's hooks, and a diff longer than review shows needs force.
hook = repo / ".git" / "hooks" / "post-merge"
hook.write_text("#!/bin/sh\ntouch " + str(tmp / "hook-ran") + "\n"); hook.chmod(0o755)
err, tid = tool("delegate", worker="research", task="make BIGFILE")
tool("wait", task_id=tid, timeout_s=30)
err, text = tool("review", task_id=tid)
assert not err and "merge will need force=true" in text, text[-200:]
err, text = tool("merge", task_id=tid)
assert err and "force=true" in text, text
assert not (repo / "big.txt").exists()
err, text = tool("merge", task_id=tid, force=True)
assert not err and (repo / "big.txt").exists(), text
assert not (tmp / "hook-ran").exists(), "post-merge hook must not run"
# Runs are detached: killing the MCP server mid-run loses nothing; the next server collects the result.
err, tid = tool("delegate", worker="research", task="slow one SLOW")
os.kill(proc.pid, 9)
proc.wait()
proc = spawn(repo)
_id = 0
rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "t", "version": "0"}})
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "slow one" in text and "untrusted" not in text and "not instructions" in text, text   # collected, and marked as data
tool("discard", task_id=tid)

# Main's absolute paths are rewritten to the worker's own copy.
err, tid = tool("delegate", worker="research", task=f"read {repo}/a.txt")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and f"{repo}/a.txt" not in text and f"{cfg / 'research' / 'trees' / tid}/a.txt" in text, text
tool("discard", task_id=tid)

# A branch can't be merged or discarded while a follow-up on it runs; closing it closes the follow-up too.
err, tid = tool("delegate", worker="research", task="base")
tool("wait", task_id=tid, timeout_s=30)
err, tid2 = tool("reply", task_id=tid, message="more SLOW")
err, text = tool("merge", task_id=tid)
assert err and "still running" in text, text
tool("wait", task_id=tid2, timeout_s=30)
err, text = tool("merge", task_id=tid)
assert not err, text
err, text = tool("reply", task_id=tid2, message="again")
assert err and "merged or discarded" in text, text

# A worker that hit its usage limit is a failed task, not a finished one.
err, tid = tool("delegate", worker="research", task="LIMIT")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert err and "usage limit" in text, text
tool("discard", task_id=tid)

# Files listed in .worktreeinclude reach the worker's copy but never land on its branch.
(repo / ".env").write_text("SECRET=1\n"); (repo / ".worktreeinclude").write_text(".env\n../outside\n")
g("add", ".worktreeinclude"); g("commit", "-qm", "include .env")
err, tid = tool("delegate", worker="research", task="ENVCHECK")
tool("wait", task_id=tid, timeout_s=30)
err, text = tool("review", task_id=tid)
assert not err and "env-seen.txt" in text and "SECRET" not in text and "+++ b/.env" not in text, text
tool("discard", task_id=tid)
# Service tokens mirrored into the main session never reach a worker; allowed tools come from the owner's file only.
(repo / ".cluster-allowed-tools").write_text("Bash(npm test:*)\nBash(rm -rf /)\nBash(a,b)\nnot a pattern\n")
attach(repo, {"research": {"mode": "acceptEdits"}})
err, tid = tool("delegate", worker="research", task="check env")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "svc=unset" in text, text
assert "--allowed-tools Bash(npm test:*),Bash(rm -rf /)" in text and "a,b" not in text.replace("Bash(npm test:*),Bash(rm -rf /)", ""), text
tool("discard", task_id=tid)
proc.stdin.close()
proc.wait(timeout=5)
print("test-team: ok")
