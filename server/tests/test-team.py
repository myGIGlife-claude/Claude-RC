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
assert "key=unset" in text and "tok=unset" in text, text    # main account's tokens never reach a worker
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
proc.stdin.write("this is not json\n")
proc.stdin.flush()
err, text = tool("list_workers")
assert not err and "research" in text, text
proc.stdin.close()
proc.wait(timeout=5)
print("test-team: ok")
