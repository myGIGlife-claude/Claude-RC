#!/usr/bin/env python3
"""Self-check for claude-plugin-updates against local git repos and a fake GitHub compare API."""
import http.server, json, os, subprocess, sys, tempfile, threading, time

HERE = os.path.dirname(os.path.abspath(__file__))
HELPER = os.path.join(os.path.dirname(HERE), "claude-plugin-updates")

api_calls = []
api_files = {}  # "old...new" -> list of changed file names
api_fail = []


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        api_calls.append(self.path)
        if api_fail:
            self.send_response(500); self.end_headers(); return
        rng = self.path.rsplit("/compare/", 1)[-1]
        files = api_files.get(rng)
        if files is None:
            self.send_response(404); self.end_headers(); self.wfile.write(b"{}"); return
        out = json.dumps({"status": "ahead", "files": [{"filename": f} for f in files]}).encode()
        self.send_response(200); self.end_headers(); self.wfile.write(out)

    def log_message(self, *a):
        pass


srv = http.server.HTTPServer(("127.0.0.1", 0), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = "http://127.0.0.1:%d" % srv.server_port

tmp = tempfile.mkdtemp()
plugin_dir = os.path.join(tmp, "plugins")
xdg = os.path.join(tmp, "xdg")
cache_file = os.path.join(xdg, "claude-launcher", "plugin-updates.json")
repo = os.path.join(tmp, "mkrepo")
other = os.path.join(tmp, "other")
git_log = os.path.join(tmp, "git.log")
git_off = os.path.join(tmp, "git.off")


def git(cwd, *args):
    return subprocess.run(["git", "-c", "user.email=t@example.com", "-c", "user.name=t", *args], cwd=cwd,
                          check=True, capture_output=True, text=True).stdout.strip()


def commit(path):
    git(path, "commit", "--allow-empty", "-q", "-m", "c")
    return git(path, "rev-parse", "HEAD")


for p in (repo, other):
    os.makedirs(p)
    git(p, "init", "-q", "-b", "main")
first = commit(repo)
head = commit(repo)
other_head = commit(other)

# Stand-in for git: the marketplace's GitHub URLs resolve to local repos; calls are logged;
# the "off" switch makes every call fail.
wrapper = os.path.join(tmp, "git-wrapper")
with open(wrapper, "w") as f:
    f.write('#!/bin/sh\necho "$@" >>"%s"\n[ -e "%s" ] && exit 1\n'
            'exec git -c "url.%s.insteadOf=https://github.com/demo/market.git" '
            '-c "url.%s.insteadOf=https://github.com/demo/other.git" "$@"\n' % (git_log, git_off, repo, other))
os.chmod(wrapper, 0o755)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f)


def catalog(mk, plugins):
    write(os.path.join(plugin_dir, "marketplaces", mk, ".claude-plugin", "marketplace.json"), {"plugins": plugins})


write(os.path.join(plugin_dir, "known_marketplaces.json"), {
    "market": {"source": {"source": "github", "repo": "demo/market"}},
    "selfhosted": {"source": {"source": "git", "url": "https://git.example.com/x/y.git"}},
})
catalog("market", [
    {"name": "pinned", "source": {"source": "url", "url": "https://example.com/p.git", "sha": head}},
    {"name": "remote", "source": {"source": "github", "repo": "demo/other"}},
    {"name": "inrepo", "source": "./plugins/inrepo"},
    {"name": "inrepo2", "source": "./plugins/inrepo2"},
    {"name": "versioned", "source": {"source": "npm", "package": "versioned"}, "version": "2.0.0"},
])
catalog("selfhosted", [{"name": "rel", "source": "./rel"}])


def install(**plugins):
    write(os.path.join(plugin_dir, "installed_plugins.json"), {"version": 2, "plugins": {
        k: [{"scope": "user", **v}] for k, v in plugins.items()}})


env = dict(os.environ, CLAUDE_PLUGIN_DIR=plugin_dir, XDG_CONFIG_HOME=xdg, CLAUDERC_GIT=wrapper,
           CLAUDERC_GITHUB_API=base, CLAUDERC_PLUGIN_TTL="0")
fails = 0


def run(**extra):
    r = subprocess.run([sys.executable, HELPER], env=dict(env, **extra), capture_output=True, text=True, timeout=120)
    assert r.returncode == 0, r.stderr
    return json.loads(r.stdout)


def check(name, cond, got=None):
    global fails
    print("  %s %s" % ("ok  " if cond else "FAIL", name))
    if not cond:
        fails += 1
        print("       got: %s" % (got,))


# Pinned sha: no network at all.
install(**{
    "pinned@market": {"version": "1.0", "gitCommitSha": head},
})
open(git_log, "w").close()
out = run()
check("pinned sha equal -> current", out["pinned@market"] == {"state": "current", "latest": head[:12]}, out)
check("pinned sha needs no network", not api_calls and not open(git_log).read(), (api_calls, open(git_log).read()))
install(**{"pinned@market": {"gitCommitSha": head[:9]}})
out = run()
check("pinned sha prefix match -> current", out["pinned@market"]["state"] == "current", out)
install(**{"pinned@market": {"gitCommitSha": first}})
out = run()
check("pinned sha differs -> available", out["pinned@market"] == {"state": "available", "latest": head[:12]}, out)

# ls-remote of a git source without a sha.
install(**{"remote@market": {"gitCommitSha": other_head}})
out = run()
check("ls-remote same head -> current", out["remote@market"] == {"state": "current", "latest": other_head[:12]}, out)
install(**{"remote@market": {"gitCommitSha": head}})
out = run()
check("ls-remote different head -> available", out["remote@market"] == {"state": "available", "latest": other_head[:12]}, out)

# Plugin inside the marketplace repo: compare API on the plugin's path.
install(**{"inrepo@market": {"gitCommitSha": head}})
out = run()
check("in-repo plugin, repo head unchanged -> current", out["inrepo@market"]["state"] == "current", out)
api_calls.clear()
install(**{"inrepo@market": {"gitCommitSha": first}, "inrepo2@market": {"gitCommitSha": first}})
api_files["%s...%s" % (first, head)] = ["README.md", "plugins/inrepo2/skill.md"]
out = run()
check("in-repo plugin, other dirs touched -> current", out["inrepo@market"] == {"state": "current", "latest": head[:12]}, out)
check("in-repo plugin, its path touched -> available", out["inrepo2@market"] == {"state": "available", "latest": head[:12]}, out)
check("one compare call serves both plugins", len(api_calls) == 1, api_calls)
api_files["%s...%s" % (first, head)] = ["plugins/inrepo-other/x"]
out = run()
check("path prefix needs a directory boundary", out["inrepo@market"]["state"] == "current", out)
api_files["%s...%s" % (first, head)] = ["f%d" % i for i in range(300)]
out = run()
check("truncated file list -> available", out["inrepo@market"]["state"] == "available", out)
install(**{"inrepo@market": {"version": "1.0"}})
out = run()
check("in-repo plugin without installed sha -> unknown", out["inrepo@market"]["state"] == "unknown", out)

# Odd data.
install(**{"rel@selfhosted": {"gitCommitSha": first}, "gone@nowhere": {"gitCommitSha": first},
           "missing@market": {"gitCommitSha": first}})
out = run()
check("missing marketplace folder -> error", out["gone@nowhere"]["state"] == "error", out)
check("missing catalog entry -> unknown", out["missing@market"]["state"] == "unknown", out)
check("relative path in a non-GitHub marketplace -> unknown", out["rel@selfhosted"]["state"] == "unknown", out)
install(**{"versioned@market": {"version": "1.0"}})
out = run()
check("catalog version differs -> available", out["versioned@market"] == {"state": "available", "latest": "2.0.0"}, out)
install(**{"versioned@market": {"version": "2.0.0"}})
check("catalog version same -> current", run()["versioned@market"]["state"] == "current")

# Errors, stale-good answers, cache.
install(**{"remote@market": {"gitCommitSha": head}})
run()  # good answer cached
open(git_off, "w").close()
out = run()
check("failing ls-remote with a good cached answer -> served stale", out["remote@market"]["state"] == "available", out)
os.remove(cache_file)
out = run()
check("failing ls-remote, nothing cached -> error", out["remote@market"]["state"] == "error", out)
cache = json.load(open(cache_file))
check("error is cached as an error", cache["remote@market"]["state"] == "error", cache)
os.remove(git_off)
cache["remote@market"] = {"for": head, "ts": 1, "state": "available", "latest": "old"}
json.dump(cache, open(cache_file, "w"))
out = run()
check("recovers once git works again", out["remote@market"]["state"] == "available" and out["remote@market"]["latest"] == other_head[:12], out)
cache["remote@market"] = {"for": head, "ts": time.time() - 30 * 3600, "state": "current", "latest": "old"}
json.dump(cache, open(cache_file, "w"))
open(git_off, "w").close()
out = run()
check("stale-good older than 24 h -> error", out["remote@market"]["state"] == "error", out)
os.remove(git_off)
os.remove(cache_file)

open(git_log, "w").close()
install(**{"remote@market": {"gitCommitSha": head}})
out = run(CLAUDERC_PLUGIN_TTL="3600")
calls_first = len(open(git_log).read().splitlines())
out2 = run(CLAUDERC_PLUGIN_TTL="3600")
check("cache avoids a second network call within the TTL",
      out == out2 and calls_first == 1 and len(open(git_log).read().splitlines()) == 1, (out, out2, calls_first))
install(**{"remote@market": {"gitCommitSha": other_head}})
out3 = run(CLAUDERC_PLUGIN_TTL="3600")
check("a newly installed version is rechecked despite the cache", out3["remote@market"]["state"] == "current", out3)

# Never crashes.
write(os.path.join(plugin_dir, "installed_plugins.json"), ["not", "a", "dict"])
check("odd installed_plugins.json -> {}", run() == {})
open(os.path.join(plugin_dir, "installed_plugins.json"), "w").write("{bad json")
check("bad JSON -> {}", run() == {})
os.remove(os.path.join(plugin_dir, "installed_plugins.json"))
check("no installed_plugins.json -> {}", run() == {})

print("FAILED: %d" % fails if fails else "all passed")
sys.exit(1 if fails else 0)
