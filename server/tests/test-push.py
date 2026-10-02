#!/usr/bin/env python3
"""Self-check for claude-push against a local fake Google (token + FCM endpoints)."""
import http.server, json, os, subprocess, sys, tempfile, threading

HERE = os.path.dirname(os.path.abspath(__file__))
PUSH = os.path.join(os.path.dirname(HERE), "claude-push")
sent = []
created = []


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith("/androidApps"):
            out = {"apps": [{"appId": "1:2:android:3", "packageName": "life.mygig.clauderc"}] if created else []}
        else:
            gs = {"project_info": {"project_number": "2"}, "client": [{"client_info": {"mobilesdk_app_id": "1:2:android:3"}, "api_key": [{"current_key": "AIzaK"}]}]}
            import base64
            out = {"configFileContents": base64.b64encode(json.dumps(gs).encode()).decode()}
        self.send_response(200); self.end_headers(); self.wfile.write(json.dumps(out).encode())

    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"]))
        if self.path.endswith("/androidApps"):
            created.append(json.loads(body)["packageName"])
            out = b"{}"
        elif self.path == "/token":
            assert b"assertion=" in body
            out = json.dumps({"access_token": "tok"}).encode()
        else:
            assert self.headers["Authorization"] == "Bearer tok"
            m = json.loads(body)["message"]
            if m["token"] == "dead":
                self.send_response(404); self.end_headers(); self.wfile.write(b'{"error":{"status":"NOT_FOUND"}}'); return
            sent.append(m)
            out = b"{}"
        self.send_response(200); self.end_headers(); self.wfile.write(out)

    def log_message(self, *a):
        pass


srv = http.server.HTTPServer(("127.0.0.1", 0), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = "http://127.0.0.1:%d" % srv.server_port

home = tempfile.mkdtemp()
cfg = os.path.join(home, "claude-launcher")
os.makedirs(os.path.join(cfg, "push-done"))
pem = os.path.join(home, "k.pem")
subprocess.run(["openssl", "genrsa", "-out", pem, "2048"], check=True, capture_output=True)
json.dump({"project_id": "p1"}, open(os.path.join(cfg, "push.json"), "w"))
json.dump({"project_id": "p1", "client_email": "a@b.c", "private_key": open(pem).read()}, open(os.path.join(cfg, "fcm-key.json"), "w"))
open(os.path.join(cfg, "push-tokens"), "w").write("dead\nphone1\n")
fake = os.path.join(home, "bin"); os.makedirs(fake)
open(os.path.join(fake, "tmux"), "w").write("#!/bin/sh\necho demo\n"); os.chmod(os.path.join(fake, "tmux"), 0o755)
env = dict(os.environ, CLAUDE_PUSH_DEBUG="1", XDG_CONFIG_HOME=home, TMUX_PANE="%1", PATH=fake + ":" + os.environ["PATH"],
           CLAUDE_PUSH_TOKEN_URL=base + "/token", CLAUDE_PUSH_SEND_URL=base + "/{}/send", CLAUDE_PUSH_FIREBASE_URL=base)


def run(*args, ev=None):
    del sent[:]
    r = subprocess.run([sys.executable, PUSH, *args], input=json.dumps(ev or {}), capture_output=True, text=True, env=env)
    assert r.returncode == 0, r.stderr
    return r.stdout


os.remove(os.path.join(cfg, "push.json"))
ids = json.loads(run("--setup"))
assert ids == {"project_id": "p1", "app_id": "1:2:android:3", "api_key": "AIzaK", "sender_id": "2"}, ids
assert created == ["life.mygig.clauderc"] and json.load(open(os.path.join(cfg, "push.json"))) == ids, "app registered, ids saved"
assert json.loads(run("--test"))["sent"] == 1
assert open(os.path.join(cfg, "push-tokens")).read() == "phone1\n", "dead token dropped"
run(ev={"hook_event_name": "Notification", "notification_type": "permission_prompt"})
n = sent[0]["data"]
assert len(sent) == 1 and n["channel"] == "alerts" and "demo" in n["title"] and "awaiting a response" in n["body"], sent
run(ev={"hook_event_name": "Notification", "notification_type": "idle_prompt"})
assert not sent, "idle prompt stays quiet"
run(ev={"hook_event_name": "Stop"})
assert not sent, "finished is off by default"
open(os.path.join(cfg, "push-done", "demo"), "w").close()
run(ev={"hook_event_name": "Stop"})
assert sent and sent[0]["data"]["channel"] == "finished" and sent[0]["data"]["session"] == "demo", sent
env.pop("TMUX_PANE")
run(ev={"hook_event_name": "Notification"})
assert not sent, "outside tmux: silent"
env["TMUX_PANE"] = "%1"; env.pop("CLAUDE_PUSH_DEBUG")
os.remove(os.path.join(cfg, "fcm-key.json"))
assert run(ev={"hook_event_name": "Notification"}) == "", "broken config never fails the hook"
print("ok")
