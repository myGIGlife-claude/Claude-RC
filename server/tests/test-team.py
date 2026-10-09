#!/usr/bin/env python3
"""Self-check for clauderc-team: drives the MCP server over stdio with a stub `claude`.
Run: server/tests/test-team.py   (needs jq for the stub)."""
import json, os, subprocess, sys, tempfile, time
from pathlib import Path

import http.server, threading

HITS = [0]; ERR = [0]   # requests the fake endpoint got; 429 mode
USAGE = {"tok": [10, 5]}   # bearer token -> what the fake usage endpoint says that account has used: [5-hour %, weekly %]


class Usage(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        HITS[0] += 1
        if ERR[0]:
            self.send_response(429); self.send_header("Retry-After", "30"); self.send_header("Content-Length", "0"); self.end_headers(); return
        five, seven = USAGE.get(self.headers.get("Authorization", "").replace("Bearer ", ""), (None, None))
        body = json.dumps({"five_hour": {"utilization": five}, "seven_day": {"utilization": seven}}).encode()
        self.send_response(200); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def log_message(self, *a):
        pass


usage_srv = http.server.HTTPServer(("127.0.0.1", 0), Usage)
threading.Thread(target=usage_srv.serve_forever, daemon=True).start()
HERE = Path(__file__).resolve().parent
tmp = Path(tempfile.mkdtemp())
cfg = tmp / "cfg" / "claude-launcher" / "workers"
test_home = tmp / "home"; test_home.mkdir()
claude_stub = tmp / "claude-stub"
stub_text = (HERE / "stubs" / "claude").read_text()
stub_text = stub_text.replace("svc=${CLAUDERC_TEST_SECRET:-unset} args=", "svc=${CLAUDERC_TEST_SECRET:-unset} ssh=${SSH_AUTH_SOCK:-unset} gh=${GH_TOKEN:-unset} ghub=${GITHUB_TOKEN:-unset} settings=${SETTINGS_ONLY_SECRET:-unset} args=")
claude_stub.write_text(stub_text); claude_stub.chmod(0o755)
env = {**os.environ, "HOME": str(test_home), "CLAUDERC_SANDBOX": "off", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "XDG_CONFIG_HOME": str(tmp / "cfg"), "CLAUDERC_CLAUDE": str(claude_stub), "CLAUDERC_CODEX": str(HERE / "stubs" / "codex"), "CLAUDERC_GEMINI": str(HERE / "stubs" / "gemini"),
       "ANTHROPIC_API_KEY": "secret-should-not-leak", "CLAUDE_CODE_OAUTH_TOKEN": "main-token", "CLAUDECODE": "1", "CLAUDERC_TEST_STUB_STATE": str(tmp / "stub"), "STUB_STATE": str(tmp / "stub"),
       "SSH_AUTH_SOCK": "/tmp/fake-agent.sock", "GH_TOKEN": "fake-gh-token", "GITHUB_TOKEN": "fake-github-token",
       "CLAUDERC_TEST_SECRET": "service-token", "CLAUDERC_WORKER_SLOTS": "3", "CLAUDERC_USAGE_TTL": "0", "CLAUDERC_START_GAP": "0",
       "CLAUDERC_USAGE_URL": f"http://127.0.0.1:{usage_srv.server_port}/", "CLAUDERC_SSH": str(HERE / "stubs" / "ssh"), "CLAUDERC_SCP": str(HERE / "stubs" / "scp")}
cfg.parent.mkdir(parents=True, exist_ok=True)
(cfg.parent / "env").write_text("export CLAUDERC_TEST_SECRET='service-token'\n")
(test_home / ".claude").mkdir()
(test_home / ".claude" / "settings.json").write_text('{"env":{"SETTINGS_ONLY_SECRET":"settings-secret"}}')
for name, signed in (("research", True), ("ui", False)):
    (cfg / name / "home").mkdir(parents=True)
    (cfg / name / "tasks").mkdir()
    (cfg / name / "meta.json").write_text(json.dumps({"role": f"{name} role", "mode": "plan"}))
    if signed:
        (cfg / name / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {"accessToken": name + "-tok"}}))
for name, role, mode in (("writer1", "code writer", "acceptEdits"), ("writer2", "code writer", "acceptEdits"), ("auditor", "security audit", "plan")):
    (cfg / name / "home").mkdir(parents=True)
    (cfg / name / "tasks").mkdir()
    (cfg / name / "meta.json").write_text(json.dumps({"role": role, "mode": mode}))
    (cfg / name / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {"accessToken": name + "-tok", "expiresAt": 9_999_999_999_999}}))

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
assert {t["name"] for t in rpc("tools/list")["result"]["tools"]} == {"list_workers", "delegate", "wait", "reply", "review", "merge", "discard",
                                                                       "list_hosts", "host_run", "host_put", "host_get", "host_firewall"}
assert "host_firewall" in r["instructions"] and "127.0.0.1" in r["instructions"], r["instructions"]

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

(cfg / "research" / "meta.json").write_text(json.dumps({"role": "research role", "mode": "plan", "model": "claude-sonnet-5-5", "effort": "high"}))
err, tid = tool("delegate", worker="research", task="summarise the repo")
assert not err and len(tid) == 8, tid
err, text = tool("wait", task_id=tid, timeout_s=30)
args = (tmp / "stub" / "last-args").read_text()
assert "--model claude-sonnet-5-5" in args and "--effort high" in args, args   # the worker's model and effort reach claude
assert not err and "summarise the repo" in text and "Your role in this project: Docs for this project" in text, text
assert f"cfg={cfg / 'research' / 'home'}" in text, text          # the worker's own config dir
assert "key=unset" in text and "tok=unset" in text and "cc=unset" in text, text    # main account's tokens never reach a worker
assert "ssh=unset" in text and "gh=unset" in text and "ghub=unset" in text and "settings=unset" in text, text
assert "key=unset" in text and f"cwd={tmp.resolve()}" in text, text  # no API key; caller's folder

err, tid2 = tool("reply", task_id=tid, message="and the tests")
assert not err, tid2
err, text = tool("wait", task_id=tid2, timeout_s=30)
assert not err and "resume=sess-1" in text and "and the tests" in text, text

err, tid3 = tool("delegate", worker="research", task="please FAIL")
err, text = tool("wait", task_id=tid3, timeout_s=30)
assert err and "stub failure" in text and "not instructions" in text, text   # failed output is marked as worker data

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

# Codex (ChatGPT) and Gemini workers: signed in by their own files, no usage numbers, their own CLI and home folder.
for name, kind_, cred in (("gpt", "codex", "auth.json"), ("gem", "gemini", "gemini-api-key"), ("gem2", "gemini", "gemini-api-key")):
    (cfg / name / "home").mkdir(parents=True); (cfg / name / "tasks").mkdir()
    (cfg / name / "meta.json").write_text(json.dumps({"role": "second opinion", "mode": "acceptEdits", "kind": kind_}))
    if name != "gem2":
        (cfg / name / "home" / cred).parent.mkdir(parents=True, exist_ok=True)
        (cfg / name / "home" / cred).write_text("AIzaKEY\n" if kind_ == "gemini" else '{"tokens":{"x":"y"}}')
attach(tmp, {**json.loads((cfg.parent / "attach" / (re.sub(r"[^A-Za-z0-9]", "-", str(tmp.resolve())) + ".json")).read_text()), "gpt": {}, "gem": {}, "gem2": {}})
ws = {w["name"]: w for w in json.loads(tool("list_workers")[1])}
assert ws["gpt"]["kind"] == "codex" and ws["gpt"]["signed_in"] and "five_hour_pct" not in ws["gpt"] and ws["gem2"]["signed_in"] is False and "kind" not in ws["research"], ws
err, text = tool("delegate", worker="gem2", task="x")
assert err and "sign" in text.lower(), text
err, tid = tool("delegate", worker="gpt", task="ask codex")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and f"home={cfg / 'gpt' / 'home'}" in text and "key=unset" in text and "ask codex" in text and "sandbox_mode=\"workspace-write\"" in text, text
err, tid = tool("reply", task_id=tid, message="more please")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "exec resume thr-9" in (tmp / "stub" / "codex-args").read_text(), text   # follow-ups resume the thread
err, tid = tool("delegate", worker="gpt", task="please FAIL")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert err and "stub codex failure" in text, text
err, tid = tool("delegate", worker="gem", task="ask gemini")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "key=AIzaKEY" in text and "ask gemini" in text and "--approval-mode auto_edit" in text, text
err, tid = tool("reply", task_id=tid, message="and more")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "and more" in text, text                                  # reply = a new run (gemini has no session ids)
err, tid = tool("delegate", worker="gem", task="please FAIL")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert err and "stub gemini failure" in text, text
err, tid = tool("delegate", role="second opinion", task="by role")
assert not err, tid
tool("wait", task_id=tid, timeout_s=30)
proc.stdin.close()
proc.wait(timeout=5)

# Hosts: servers attached to a project's chat (attach/<slug>.hosts.json). ssh and scp are the stubs; the host's home is
# $STUB_STATE/remote, which only accepts the pinned host key and the keys in its authorized_keys.
stub = tmp / "stub"
(stub / "remote" / ".ssh").mkdir(parents=True, exist_ok=True)
keygen = lambda f: subprocess.run(["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", str(f)], check=True, stdin=subprocess.DEVNULL)
keygen(tmp / "hostkey")
hostpub = (tmp / "hostkey.pub").read_text().split()
(stub / "host_key.pub").write_text(" ".join(hostpub) + "\n")
hosts = cfg.parent / "hosts"
for n in ("web", "other"):
    (hosts / n).mkdir(parents=True)
    keygen(hosts / n / "key")
    with open(stub / "remote" / ".ssh" / "authorized_keys", "a") as f:
        f.write((hosts / n / "key.pub").read_text())
    (hosts / n / "key.pub").unlink()
    (hosts / n / "known_hosts").write_text(f"web.example.com {hostpub[0]} {hostpub[1]}\n")
    (hosts / n / "meta.json").write_text(json.dumps({"address": "web.example.com", "port": 22, "user": "deploy", "fingerprint": "SHA256:" + "A" * 43, "auth": "key", "added": 1}))
site = tmp / "my site"
site.mkdir()


def attach_hosts(folder, names):
    f = cfg.parent / "attach" / (re.sub(r"[^A-Za-z0-9]", "-", str(Path(folder).resolve())) + ".hosts.json")
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(names))


proc = spawn(site)
_id = 0
err, text = tool("list_hosts")
assert not err and json.loads(text) == [], text
err, text = tool("host_run", host="web", command="true")
assert err and "isn't attached" in text and "👥" in text, text
attach_hosts(site, {"web": {}, "ghost": {}, "../x": {}})
attach(site, {"research": {}})                                   # workers attached here stay workers, hosts stay hosts
err, text = tool("list_hosts")
assert not err and json.loads(text) == [{"name": "web", "address": "web.example.com", "user": "deploy", "workspace": "~/sites/my-site"}], text
err, text = tool("list_workers")
assert not err and [w["name"] for w in json.loads(text)] == ["research"], text
for h in ("other", "ghost", "../x", None):
    err, text = tool("host_run", host=h, command="true")
    assert err and "isn't attached" in text, (h, text)
err, text = tool("host_run", host="web", command="pwd; echo hi; echo oops >&2; exit 3")
r = json.loads(text)
assert not err and r["exit_code"] == 3 and r["stdout"].splitlines() == [str(stub / "remote" / "sites" / "my-site"), "hi"] and r["stderr"] == "oops\n", r
argv = (stub / "ssh-argv").read_text().splitlines()
k = str(hosts / "web" / "key")
assert argv[:4] == ["-F", "/dev/null", "-i", k], argv
for o in ("IdentitiesOnly=yes", "IdentityAgent=none", "BatchMode=yes", "PasswordAuthentication=no", "StrictHostKeyChecking=yes",
          "UserKnownHostsFile=" + str(hosts / "web" / "known_hosts"), "GlobalKnownHostsFile=/dev/null", "ConnectTimeout=10", "Port=22", "User=deploy"):
    assert argv[argv.index(o) - 1] == "-o", (o, argv)
i = argv.index("-T")   # (the stub writes one argument per line; the remote script has two lines)
assert argv[i + 1] == "web.example.com" and argv[i + 2:] == ["mkdir -p sites/my-site && cd sites/my-site || exit 97", "pwd; echo hi; echo oops >&2; exit 3"], argv
err, text = tool("host_run", host="web", command="head -c 150000 /dev/zero | tr '\\0' a")
r = json.loads(text)
assert not err and len(r["stdout"]) < 101000 and "[cut at 100000 bytes of 100001]" in r["stdout"], len(r["stdout"])
(stub / "ssh-unreachable").touch()
err, text = tool("host_run", host="web", command="true")
assert err and "Couldn't connect to web" in text and "Connection refused" in text, text
(stub / "ssh-unreachable").unlink()
# Copies: remote paths stay inside the workspace, local paths inside the project.
(site / "index.html").write_text("<h1>hi</h1>\n")
err, text = tool("host_put", host="web", local_path="index.html", remote_path="public/index.html")
assert not err and (stub / "remote" / "sites" / "my-site" / "public" / "index.html").read_text() == "<h1>hi</h1>\n", text
assert "UserKnownHostsFile=" + str(hosts / "web" / "known_hosts") in (stub / "scp-argv").read_text().splitlines(), (stub / "scp-argv").read_text()
err, text = tool("host_get", host="web", remote_path="public/index.html", local_path="copy/index.html")
assert not err and (site / "copy" / "index.html").read_text() == "<h1>hi</h1>\n", text
os.symlink("/etc", site / "escape")
for bad in ("../x", "/etc/passwd", "a/../../x", "..", "-oProxyCommand=x", "a b", "~/x", "a;id", "", None):
    err, text = tool("host_put", host="web", local_path="index.html", remote_path=bad)
    assert err and "remote_path" in text, (bad, text)
    err, text = tool("host_get", host="web", remote_path=bad, local_path="x")
    assert err and "remote_path" in text, (bad, text)
for bad in ("../outside", "/etc/passwd", "escape/passwd", str(tmp / "repo")):
    err, text = tool("host_put", host="web", local_path=bad, remote_path="x")
    assert err and ("inside this project" in text or "doesn't exist" in text), (bad, text)
    err, text = tool("host_get", host="web", remote_path="public/index.html", local_path=bad)
    assert err and "inside this project" in text, (bad, text)
for side in ("host_put", "host_get"):
    args = {"host": "web", "local_path": ".git/config", "remote_path": "x"} if side == "host_put" else {"host": "web", "remote_path": ".git/config", "local_path": "x"}
    err, text = tool(side, **args)
    assert err and ".git" in text, (side, text)
(site / "folder").mkdir()
os.symlink("/etc/passwd", site / "folder" / "escape")
err, text = tool("host_put", host="web", local_path="folder", remote_path="folder")
assert err and "symlink" in text, text
err, text = tool("host_put", host="other", local_path="index.html", remote_path="x")
assert err and "isn't attached" in text, text
# Firewall: ufw through sudo -n on the host (the ufw and sudo stubs; rules in $STUB_STATE/ufw-rules).
for h in ("other", "ghost", None):
    err, text = tool("host_firewall", host=h, action="status")
    assert err and "isn't attached" in text, (h, text)
err, text = tool("host_firewall", host="web", action="status")
assert err and "passwordless sudo" in text, text                 # the sudo stub asks for a password by default
(stub / "sudo-mode").write_text("nopasswd\n")
err, text = tool("host_firewall", host="web", action="open", port=8080)
assert not err and "Status: active" in text and "8080/tcp" in text, text
err, text = tool("host_firewall", host="web", action="open", port="51820", proto="udp")
assert not err and "51820/udp" in text, text
assert (stub / "ufw-rules").read_text().split() == ["8080/tcp", "51820/udp"], (stub / "ufw-rules").read_text()
assert "ufw allow 8080/tcp" in (stub / "sudo-argv").read_text(), (stub / "sudo-argv").read_text()
err, text = tool("host_firewall", host="web", action="close", port=8080)
assert not err and "8080/tcp" not in text and "51820/udp" in text, text
err, text = tool("host_firewall", host="web", action="status")
assert not err and text.startswith("Status: active") and "51820/udp" in text, text
err, text = tool("host_firewall", host="web", action="close", port=22)
assert err and "SSH port" in text, text
# The host's own sshd ports can't be closed either: the one this connection reached (behind a port forward) and sshd -T's.
(stub / "ssh-connection").write_text("192.0.2.1 50000 192.0.2.2 2200\n")
(stub / "sshd-ports").write_text("22\n2022\n")
for p in (2200, 2022):
    before = (stub / "ufw-log").read_text()
    err, text = tool("host_firewall", host="web", action="close", port=p)
    assert err and "SSH port" in text, (p, text)
    assert (stub / "ufw-log").read_text() == before, p
err, text = tool("host_firewall", host="web", action="close", port=2022, proto="udp")
assert not err, text
(stub / "ssh-connection").unlink(); (stub / "sshd-ports").unlink()
for bad in ({"action": "open"}, {"action": "open", "port": 0}, {"action": "open", "port": 70000}, {"action": "open", "port": "80;id"},
            {"action": "open", "port": True}, {"action": "open", "port": 80, "proto": "sctp"}, {"action": "open", "port": 80, "proto": "tcp;id"},
            {"action": "reset"}, {}):
    before = (stub / "ufw-log").read_text()
    err, text = tool("host_firewall", host="web", **bad)
    assert err and ("port" in text or "proto" in text or "action" in text), (bad, text)
    assert (stub / "ufw-log").read_text() == before, bad
assert "22/tcp" not in (stub / "ufw-rules").read_text()
(stub / "sudo-mode").unlink()
# Broken settings never reach ssh.
(hosts / "web" / "meta.json").write_text(json.dumps({"address": "-oProxyCommand=x", "port": 22, "user": "deploy"}))
err, text = tool("host_run", host="web", command="true")
assert err and "incomplete" in text, text
proc.stdin.close()
proc.wait(timeout=5)

# In a git project each task gets its own branch and worktree; the main Claude reviews, then merges or discards.
repo = tmp / "repo"
repo.mkdir()
g = lambda *a, c=repo: subprocess.run(["git", "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "protocol.file.allow=user", "-c", "user.name=t", "-c", "user.email=t@t", *a], cwd=c, capture_output=True, text=True, check=True).stdout
g("init", "-q"); (repo / "a.txt").write_text("one\n"); g("add", "."); g("commit", "-qm", "init")
ghooks = repo / ".githooks"; ghooks.mkdir()
marker = tmp / "git-hook-ran"
for hookname in ("post-commit", "post-checkout"):
    hook = ghooks / hookname; hook.write_text("#!/bin/sh\ntouch " + str(marker) + "\n"); hook.chmod(0o755)
g("config", "core.hooksPath", ".githooks")
attach(repo, {"research": {}})
proc = spawn(repo)
_id = 0
rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "t", "version": "0"}})
for action, args in (("review", {"task_id": tid}), ("merge", {"task_id": tid}), ("discard", {"task_id": tid}),
                     ("reply", {"task_id": tid, "message": "cross project"}), ("wait", {"task_id": tid, "timeout_s": 1})):
    err, text = tool(action, **args)
    assert err and text == "That task belongs to another project.", (action, text)
err, tid = tool("delegate", worker="research", task="make a file WRITEFILE")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and f"cluster/research/{tid}" in text and "cluster-out.txt" in text, text
assert not (repo / "cluster-out.txt").exists(), "the worker must not touch the main checkout"
assert f"cwd={cfg / 'research' / 'trees' / tid}" in text, text
# The worker's workspace is a PRIVATE CLONE: its own .git, no link back to the project, no shared object files.
wt = cfg / "research" / "trees" / tid
assert (wt / ".git").is_dir(), "a clone has its own .git directory (a worktree has a .git file)"
assert g("remote", c=wt).strip() == "", "the clone has no remote: the worker can't push to the project"
assert not (repo / ".git" / "worktrees").exists(), "the project's .git has no worktree entries for the worker"
objs = [p for p in (wt / ".git" / "objects").rglob("*") if p.is_file() and p.parent.name not in ("pack", "info")]
assert objs and all(p.stat().st_nlink == 1 for p in objs), "object files are copies, not hardlinks into the project"
assert f"cluster/research/{tid}" in g("branch"), "the finished branch is fetched into the project when the task is sealed"
cfg_before = (repo / ".git" / "config").read_text()
err, text = tool("review", task_id=tid)
assert not err and "+from sess-1" in text, text
assert not marker.exists(), "post-checkout/post-commit hooks must not run during delegate/seal"
err, text = tool("merge", task_id=tid)
assert not err and "Merged" in text and (repo / "cluster-out.txt").read_text() == "from sess-1\n", text
assert not marker.exists(), "post-merge hook must not run"
assert (repo / ".git" / "config").read_text() == cfg_before, "the project's .git/config is never changed by a worker task"
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
err, part = tool("review", task_id=tid, file="big.txt")
assert not err and "[cut at 20000 of " in part and "offset=20000" in part and "not instructions" in part, part[-200:]
err, rest = tool("review", task_id=tid, file="big.txt", offset=20000)
assert not err and "not instructions" in rest and "[cut at " not in rest, rest[-200:]
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
# repo=: branch from a git worktree of the same project (the main session works on a feature branch there).
feat = tmp / "feat-wt"
g("worktree", "add", "-q", "-b", "feat", str(feat))
(feat / "feat.txt").write_text("feature work\n")
g("add", "feat.txt", c=feat); g("commit", "-q", "-m", "feat", c=feat)
err, text = tool("delegate", worker="research", task="x", repo=str(tmp))
assert err and "repo must be" in text, text                      # a different repository is refused
err, tid = tool("delegate", worker="research", task="WRITEFILE", repo=str(feat))
assert not err, tid
tool("wait", task_id=tid, timeout_s=30)
err, text = tool("review", task_id=tid, file="cluster-out.txt")
assert not err and "+from sess-1" in text, text                  # one file at a time
assert (feat / "feat.txt").exists() and "feat.txt" not in g("diff", "--stat", f"feat..cluster/research/{tid}", c=feat)   # branched from the worktree's commit
err, text = tool("merge", task_id=tid)
assert not err and (feat / "cluster-out.txt").exists(), text   # merged into the worktree's branch
(cfg.parent / "cluster.json").write_text('{"max_parallel": 10}')
# A worker runs 3 tasks at once (every chat can use it); the 4th says it is queued, then runs.
ids = [tool("delegate", worker="research", task=f"SLOW {i}")[1] for i in range(4)]
err, text = tool("wait", task_id=ids[3], timeout_s=1)
assert not err and text.startswith("Queued"), text
err, text = tool("wait", task_id=ids[0], timeout_s=1)
assert not err and not text.startswith("Queued"), text           # the first three really started
for i in ids:
    err, text = tool("wait", task_id=i, timeout_s=30)
    assert not err and not text.startswith(("Queued", "Still")), text
    tool("discard", task_id=i)
# The owner's per-chat limit (cluster.json, set from the app) caps running tasks.
(cfg.parent / "cluster.json").write_text('{"max_parallel": 2}')
ids = [tool("delegate", worker="research", task=f"SLOW {i}")[1] for i in range(2)]
err, text = tool("delegate", worker="research", task="one too many")
assert err and "already has 2 worker tasks" in text, text
for i in ids:
    tool("wait", task_id=i, timeout_s=30); tool("discard", task_id=i)
err, tid = tool("delegate", worker="research", task="room again")
assert not err, tid                                              # finished tasks free the room
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid)
# Near its 5-hour limit the worker is not handed work: main does it itself.
(cfg.parent / "cluster.json").write_text('{"max_parallel": 3, "handback": true, "handback_pct": 95}')
(cfg / "research" / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {"accessToken": "tok", "expiresAt": 9_999_999_999_999}}))
USAGE["tok"][0] = 96
err, text = tool("delegate", worker="research", task="x")
assert err and "96%" in text and "do this task yourself" in text, text
ws = json.loads(tool("list_workers")[1])
assert [(w.get("five_hour_pct"), w.get("seven_day_pct"), w.get("available")) for w in ws if w["name"] == "research"] == [(96, 5, False)], ws
(cfg.parent / "cluster.json").write_text('{"max_parallel": 3, "handback": false, "handback_pct": 95}')
err, tid = tool("delegate", worker="research", task="handback off")
assert not err, tid                                              # the owner can switch hand-back off
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid)
err, tid = tool("delegate", worker="research", task="stats please")
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "Ran 42s, 3 turns, 1200 tokens in (+50000 cached), 340 out." in text, text   # what the run cost
tool("discard", task_id=tid)

# Role routing, failover and stacked tasks. Writers and the auditor are attached next to research.
(cfg.parent / "cluster.json").write_text('{"max_parallel": 10, "handback": true, "handback_pct": 95}')
attach(repo, {"research": {}, "writer1": {}, "writer2": {}, "auditor": {}})
USAGE.update({"writer1-tok": [40, 10], "writer2-tok": [20, 10], "auditor-tok": [5, 5]})
by_name = lambda: {w["name"]: w for w in json.loads(tool("list_workers")[1])}
assert by_name()["writer1"]["seven_day_pct"] == 10 and "available" not in by_name()["writer1"]
err, text = tool("delegate", role="translator", task="x")
assert err and "No attached worker has the role" in text and "code writer" in text and "security audit" in text, text
err, text = tool("delegate", role="code writer", worker="writer1", task="x")
assert err and "exactly one" in text, text
err, text = tool("delegate", task="x")
assert err and "exactly one" in text, text
base = g("rev-parse", "HEAD").strip()
err, wtid = tool("delegate", role="Code Writer", task="WRITEFILE by role")            # case-insensitive; writer2 has used less
assert not err, wtid
err, text = tool("wait", task_id=wtid, timeout_s=30)
assert not err and "Output from worker writer2" in text and f"cfg={cfg / 'writer2' / 'home'}" in text and "Your role in this project: code writer" in text, text
assert "Routed" not in text, text
# Stacked: the auditor's branch starts on the writer's branch and its diffs show both.
err, text = tool("delegate", role="audit", task="audit the change", from_task="nope1234")
assert err, text
err, aid = tool("delegate", role="audit", task="audit the change", from_task=wtid)    # part of the role is enough
assert not err, aid
err, text = tool("wait", task_id=aid, timeout_s=30)
assert not err and "Output from worker auditor" in text and "already contains another worker's changes" in text, text
assert f"see `git diff {base}..HEAD`" in text and "No files changed on branch" in text, text
assert (cfg / "auditor" / "trees" / aid / "cluster-out.txt").read_text() == "from sess-1\n", "the writer's file is in the auditor's workspace"
err, text = tool("review", task_id=aid)
assert not err and "cluster-out.txt" in text and "+from sess-1" in text, text           # the writer's changes show against the shared base
assert g("rev-parse", f"cluster/auditor/{aid}") == g("rev-parse", f"cluster/writer2/{wtid}"), "the auditor added no commit"
err, text = tool("discard", task_id=aid)
assert not err, text
err, text = tool("delegate", role="audit", task="again", from_task=aid)
assert err and "no open branch" in text, text                                         # discarded
tool("discard", task_id=wtid)
err, slow = tool("delegate", worker="writer1", task="SLOW writer")
err, text = tool("delegate", role="audit", task="x", from_task=slow)
assert err and "still running" in text, text                                          # not finished yet
# Fewest running tasks breaks a tie in usage: writer1 is busy, so writer2 gets it.
USAGE["writer1-tok"], USAGE["writer2-tok"] = [30, 30], [30, 30]
err, tid = tool("delegate", role="code writer", task="tie")
assert "Output from worker writer2" in tool("wait", task_id=tid, timeout_s=30)[1]; tool("discard", task_id=tid)
tool("wait", task_id=slow, timeout_s=30); tool("discard", task_id=slow)
err, tid = tool("delegate", role="code writer", task="tie, none busy")
assert "Output from worker writer1" in tool("wait", task_id=tid, timeout_s=30)[1]; tool("discard", task_id=tid)   # then by name
# A writer over the threshold on either window is skipped.
USAGE["writer1-tok"], USAGE["writer2-tok"] = [30, 10], [96, 10]
err, tid = tool("delegate", role="code writer", task="5h window")
assert "Output from worker writer1" in tool("wait", task_id=tid, timeout_s=30)[1]; tool("discard", task_id=tid)
USAGE["writer1-tok"], USAGE["writer2-tok"] = [30, 10], [10, 97]
err, tid = tool("delegate", role="code writer", task="weekly window")
assert "Output from worker writer1" in tool("wait", task_id=tid, timeout_s=30)[1]; tool("discard", task_id=tid)
w2 = by_name()["writer2"]
assert (w2["five_hour_pct"], w2["seven_day_pct"], w2["available"]) == (10, 97, False), w2
# Both over: main does it itself.
USAGE["writer1-tok"], USAGE["writer2-tok"] = [96, 10], [10, 97]
err, text = tool("delegate", role="code writer", task="x")
assert err and "All workers with role 'code writer' are near their usage limit" in text and "writer1: 5h 96%, 7d 10%" in text \
    and "writer2: 5h 10%, 7d 97%" in text and "do this task yourself now and try again later" in text, text
err, text = tool("delegate", worker="writer1", task="x")
assert err and "96%" in text and "do this task yourself" in text, text                # nothing to fail over to: refused as before
# A named worker near its limit hands over to another account with the same role.
USAGE["writer1-tok"], USAGE["writer2-tok"] = [96, 10], [20, 10]
err, tid = tool("delegate", worker="writer1", task="named")
assert not err, tid
err, text = tool("wait", task_id=tid, timeout_s=30)
assert not err and "Output from worker writer2" in text and "[cluster] Routed to writer2 because writer1 is at 96% of its limit." in text, text
tool("discard", task_id=tid)
err, tid = tool("delegate", worker="auditor", task="named, has room")
assert not err and "Routed" not in tool("wait", task_id=tid, timeout_s=30)[1]; tool("discard", task_id=tid)   # no hand-over
# The usage endpoint rate-limits: a 429 backs off (no hammering), keeps the last good numbers, and the cache is shared.
USAGE["auditor-tok"] = [91, 12]
assert by_name()["auditor"]["five_hour_pct"] == 91
assert (cfg.parent / "usage-cache" / "auditor.json").is_file(), "usage is cached in a file every chat's MCP process shares"
(cfg.parent / "usage-cache" / "auditor.json").write_text(json.dumps({**json.loads((cfg.parent / "usage-cache" / "auditor.json").read_text()), "ts": 1}))   # make it stale
ERR[0] = 1
assert "five_hour_pct" not in by_name()["auditor"]   # numbers older than the stale limit are unknown, not trusted
(cfg.parent / "usage-cache" / "auditor.json").unlink()
USAGE["auditor-tok"] = [91, 12]; ERR[0] = 0
assert by_name()["auditor"]["five_hour_pct"] == 91
fresh = json.loads((cfg.parent / "usage-cache" / "auditor.json").read_text()); fresh["ts"] -= 500   # older than the TTL, still inside the stale limit
(cfg.parent / "usage-cache" / "auditor.json").write_text(json.dumps(fresh))
ERR[0] = 1; before = HITS[0]
assert by_name()["auditor"]["five_hour_pct"] == 91, "a 429 keeps the last good numbers"
assert HITS[0] == before + 1
by_name(); by_name()
assert HITS[0] == before + 1, "after a 429 it backs off instead of asking again"
ERR[0] = 0
(cfg.parent / "usage-cache" / "auditor.json").unlink()
USAGE["auditor-tok"] = [5, 5]
# Two runs must not refresh the same login at once (that logs the worker out): a token that expires within a run's
# lifetime makes the run start alone.
(cfg.parent / "cluster.json").write_text('{"max_parallel": 10, "handback": false, "handback_pct": 95}')
(cfg / "writer1" / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {"accessToken": "writer1-tok", "expiresAt": (time.time() + 100) * 1000}}))
err, a1 = tool("delegate", worker="writer1", task="SLOW token a")
err, a2 = tool("delegate", worker="writer1", task="token b")
err, text = tool("wait", task_id=a2, timeout_s=1)
assert not err and text.startswith("Queued"), text                  # alone: the second waits for the first
tool("wait", task_id=a1, timeout_s=30)
err, text = tool("wait", task_id=a2, timeout_s=30)
assert not err and not text.startswith(("Queued", "Still")), text
tool("discard", task_id=a1); tool("discard", task_id=a2)
(cfg / "writer1" / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {"accessToken": "writer1-tok", "expiresAt": 9_999_999_999_999}}))
err, a1 = tool("delegate", worker="writer1", task="SLOW fresh a")
err, a2 = tool("delegate", worker="writer1", task="fresh b")
err, text = tool("wait", task_id=a2, timeout_s=1)
assert not err and not text.startswith("Queued"), text              # a fresh token: runs side by side
tool("wait", task_id=a1, timeout_s=30); tool("wait", task_id=a2, timeout_s=30); tool("discard", task_id=a1); tool("discard", task_id=a2)
# Starts of one worker's runs are spaced apart: two claude processes starting together can log the worker out.
import runpy
ns = runpy.run_path(str(HERE.parent / "clauderc-team"), run_name="not_main")
lk = tmp / "gap"; lk.mkdir()
def start_wrapper(tag):
    return subprocess.Popen(["sh", "-c", ns["SLOT_SH"], "slot", str(lk / "run.lock"), "3", str(lk / f"{tag}.started"), str(lk / "none.json"), "1000", ns["TOKEN_LEFT_PY"], "3",
                             "sh", "-c", f"date +%s.%N >{lk}/{tag}.at"])
w1, w2 = start_wrapper("one"), start_wrapper("two")
w1.wait(timeout=30); w2.wait(timeout=30)
at = [float((lk / f"{x}.at").read_text()) for x in ("one", "two")]
assert abs(at[0] - at[1]) >= 2.5, at                            # 3 s gap between the two starts
orphan = subprocess.Popen(["sh", "-c", ns["SLOT_SH"], "slot", str(lk / "run.lock"), "3", str(lk / "orphan.started"), str(lk / "none.json"), "1000", ns["TOKEN_LEFT_PY"], "0", "sh", "-c", "sleep 3 &"])
orphan.wait(timeout=5)
free = subprocess.run(["sh", "-c", ns["SLOT_SH"], "slot", str(lk / "run.lock"), "3", str(lk / "free.started"), str(lk / "none.json"), "1000", ns["TOKEN_LEFT_PY"], "0", "true"], timeout=5)
assert free.returncode == 0, "background worker descendants must not retain slot locks"
# A logged-out Claude leaves a credentials file without a token: that is not "signed in".
(cfg / "writer2" / "home" / ".credentials.json").write_text(json.dumps({"claudeAiOauth": {}}))
assert by_name()["writer2"]["signed_in"] is False
err, text = tool("delegate", worker="writer2", task="x")
assert err and "isn't signed in" in text, text
# A run whose process died without anyone collecting it must not count against the parallel limit for ever.
(cfg.parent / "cluster.json").write_text('{"max_parallel": 1, "handback": false, "handback_pct": 95}')
err, dead = tool("delegate", worker="writer1", task="SLOW then forgotten")
d = json.loads((cfg / "writer1" / "tasks" / f"{dead}.json").read_text())
os.kill(d["pid"], 9); time.sleep(0.5)                              # the run dies, nobody called wait
err, tid = tool("delegate", worker="writer1", task="room again after a dead run")
assert not err, tid
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid); tool("discard", task_id=dead)
# ---- the developer knowledge pack: a Claude worker gets the owner's rc-* skills in its own config folder (copied when VERSION changes) ----
kp = tmp / "cfg" / "claude-launcher" / "knowledge"; (kp / "skills" / "rc-demo").mkdir(parents=True)
(kp / "VERSION").write_text("2026-10-09\n"); (kp / "skills" / "rc-demo" / "SKILL.md").write_text("---\nname: rc-demo\ndescription: demo\n---\nv1\n")
attach(repo, {"writer1": {}})   # (the server reads the attach file on every call)
wsk = cfg / "writer1" / "home" / "skills"
(wsk / "mine").mkdir(parents=True); (wsk / "mine" / "SKILL.md").write_text("mine\n"); (wsk / "rc-old").mkdir(); (wsk / "rc-old" / "SKILL.md").write_text("old\n")
err, tid = tool("delegate", worker="writer1", task="fresh sync 1"); assert not err, tid
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid)
assert (wsk / "rc-demo" / "SKILL.md").read_text().endswith("v1\n") and (wsk / ".rc-version").read_text() == "2026-10-09\n", "the worker got the pack"
assert (wsk / "mine" / "SKILL.md").read_text() == "mine\n" and not (wsk / "rc-old").exists(), "its own skills stay, stale rc-* are removed"
(kp / "skills" / "rc-demo" / "SKILL.md").write_text("---\nname: rc-demo\ndescription: demo\n---\nv2\n")
err, tid = tool("delegate", worker="writer1", task="fresh sync 2")
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid)
assert (wsk / "rc-demo" / "SKILL.md").read_text().endswith("v1\n"), "same VERSION: not copied again"
(kp / "VERSION").write_text("2026-11-01\n")
err, tid = tool("delegate", worker="writer1", task="fresh sync 3")
tool("wait", task_id=tid, timeout_s=30); tool("discard", task_id=tid)
assert (wsk / "rc-demo" / "SKILL.md").read_text().endswith("v2\n") and (wsk / ".rc-version").read_text() == "2026-11-01\n", "a new VERSION refreshes it"
import shutil; shutil.rmtree(kp)

proc.stdin.close()
proc.wait(timeout=5)
# ---- the worker sandbox (bubblewrap): what the argv grants, and (when bwrap works here) what a run can really see ----
sbx_home = tmp / "sbxhome"; (sbx_home / ".config" / "claude-launcher").mkdir(parents=True); (sbx_home / ".ssh").mkdir()
(sbx_home / ".config" / "claude-launcher" / "env").write_text("export SECRET_TOKEN='owner-secret'\n")
(sbx_home / ".ssh" / "id_ed25519").write_text("PRIVATE KEY\n")
(sbx_home / ".local" / "bin").mkdir(parents=True)
sbx_w = tmp / "sbxworkers" / "w1"; (sbx_w / "home").mkdir(parents=True); (sbx_w / "tasks").mkdir()
sbx_ws = tmp / "sbxws"; sbx_ws.mkdir(); (sbx_ws / "f.txt").write_text("keep\n")
sbx_env = {"PATH": f"{sbx_home}/.local/bin:/usr/bin:/bin", "HOME": str(sbx_home), "LANG": "C.UTF-8", "CODEX_HOME": str(sbx_w / "home")}
sbx_t = {"id": "feedbeef", "cwd": str(sbx_ws), "wt": str(sbx_ws), "worker": "w1"}
argv = ns["sandbox_argv"](sbx_t, "codex", sbx_w, sbx_env, read_only=False)
argv[0] = argv[0] or "bwrap"   # (no bubblewrap on this machine, e.g. CI: only the argument list is checked)
j = " ".join(argv)
assert "--unshare-pid" in argv and "--cap-drop" in argv and "--clearenv" in argv, argv
assert argv[argv.index("--tmpfs", argv.index("/tmp") + 1) + 1] == str(sbx_home), "the home folder is an empty tmpfs"
assert f"--bind {sbx_ws} {sbx_ws}" in j and f"--bind {sbx_w / 'home'} {sbx_w / 'home'}" in j, "workspace and the worker's own login folder are writable"
assert f"--bind {sbx_w / 'tasks' / 'feedbeef.last'}" in j, "codex's answer file is bound"
assert str(sbx_home / ".config") not in j and str(sbx_home / ".ssh") not in j, "none of the owner's files are mounted"
assert "--setenv HOME " + str(sbx_home) in j and "--setenv CODEX_HOME " in j and argv[-3:] == ["--chdir", str(sbx_ws), "--"], argv[-4:]
argv_ro = ns["sandbox_argv"](sbx_t, "claude", sbx_w, sbx_env, read_only=True)
argv_ro[0] = argv_ro[0] or "bwrap"
assert f"--ro-bind {sbx_ws} {sbx_ws}" in " ".join(argv_ro), "plan mode: the workspace is read-only"
if ns["bwrap_ok"]():
    def inside(cmd, a=argv):
        return subprocess.run(a + ["sh", "-c", cmd], capture_output=True, text=True, timeout=30)
    r = inside(f"cat {sbx_home}/.config/claude-launcher/env; cat {sbx_home}/.ssh/id_ed25519; ls {sbx_home}/.config/claude-launcher")
    assert "owner-secret" not in r.stdout + r.stderr and "PRIVATE KEY" not in r.stdout + r.stderr, "a sandboxed run can't read the owner's secrets"
    assert inside(f"echo new > {sbx_ws}/g.txt && cat {sbx_ws}/g.txt").stdout.strip() == "new" and (sbx_ws / "g.txt").read_text() == "new\n", "it can write in its workspace"
    assert inside("ps -e | wc -l").stdout.strip().isdigit() and int(inside("ps -e | wc -l").stdout) < 10, "it can't see the server's other processes"
    assert inside(f"echo x > {sbx_ws}/h.txt", argv_ro).returncode != 0 and not (sbx_ws / "h.txt").exists(), "plan mode can't write"
    assert inside(f"echo x > {sbx_home}/dropped", argv).returncode == 0 and not (sbx_home / "dropped").exists(), "writes to the home folder vanish"
else:
    print("test-team: bubblewrap unusable here, the sandbox run checks were skipped")
# modes: the cluster setting, the env override, and 'on' refuses to run without bubblewrap
os.environ.pop("CLAUDERC_SANDBOX", None); os.environ["XDG_CONFIG_HOME"] = str(tmp / "cfg2"); (tmp / "cfg2" / "claude-launcher").mkdir(parents=True)
ns2 = runpy.run_path(str(HERE.parent / "clauderc-team"), run_name="not_main")
assert ns2["sandbox_mode"]() == "auto", "default is auto"
(tmp / "cfg2" / "claude-launcher" / "cluster.json").write_text('{"sandbox":"on"}'); assert ns2["sandbox_mode"]() == "on"
(tmp / "cfg2" / "claude-launcher" / "cluster.json").write_text('{"sandbox":"bogus"}'); assert ns2["sandbox_mode"]() == "auto"
os.environ["CLAUDERC_SANDBOX"] = "off"; assert ns2["sandbox_mode"]() == "off"; os.environ.pop("CLAUDERC_SANDBOX")
os.environ["CLAUDERC_BWRAP"] = "/nonexistent/bwrap"; ns3 = runpy.run_path(str(HERE.parent / "clauderc-team"), run_name="not_main")
assert ns3["bwrap_ok"]() is False, "no bubblewrap: not usable"
os.environ.pop("CLAUDERC_BWRAP")
print("test-team: ok")
