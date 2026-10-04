#!/usr/bin/env bash
# End-to-end tests for server/claude-backup (export / import / list) in throwaway
# HOME directories, with a throwaway GNUPGHOME and stub docker/scp/claude.
# Needs bash, gpg, tar, jq, git. No network.
#
#   server/tests/test-backup.sh

# shellcheck disable=SC2319  # `$?` after [[ ]] is the point here
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(dirname "$HERE")"

for t in gpg tar jq git gzip; do
  command -v "$t" >/dev/null || { echo "SKIP: $t is missing"; exit 0; }
done

WORK="$(mktemp -d)"
OLD="$WORK/old" H2="$WORK/new2" H3="$WORK/new3" H4="$WORK/new4" H5="$WORK/new5"
export GNUPGHOME="$WORK/gnupg" STUB_DOCKER="$WORK/docker" TMPDIR="$WORK/tmp"
mkdir -p "$GNUPGHOME" "$TMPDIR" "$STUB_DOCKER/vol" "$WORK/stubs" "$WORK/failbin" "$OLD" "$H2" "$H3" "$H4" "$H5"
chmod 700 "$GNUPGHOME"
unset XDG_CONFIG_HOME CLAUDE_BACKUP_PASS_FILE CLAUDE_BACKUP_PROJECTS CLAUDE_BACKUP_RESCUE_MAX_MB
cleanup() { gpgconf --kill gpg-agent 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

CB="$WORK/stubs/claude-backup"
cp "$SERVER/claude-backup" "$CB"
chmod +x "$CB"

# ---- stubs: claude, scp (the "old server" is $OLD), docker (volumes are folders in $STUB_DOCKER/vol)
cat >"$WORK/stubs/claude" <<'EOF'
#!/usr/bin/env bash
echo "9.9.9 (Claude Code)"
EOF
cat >"$WORK/stubs/scp" <<'EOF'
#!/usr/bin/env bash
# scp [-p] [-o X] [--] user@host:path dest   (relative remote paths are relative to the old home)
while [[ $# -gt 2 ]]; do
  case "$1" in -o) shift 2 ;; --) shift ;; *) shift ;; esac
done
src="${1#*:}"
[[ "$src" == /* ]] || src="$OLD_HOME/$src"
cp -p -- "$src" "$2"
EOF
cat >"$WORK/stubs/docker" <<'EOF'
#!/usr/bin/env bash
V="$STUB_DOCKER/vol"
case "$1 $2" in
  "volume ls") ls "$V"; printf 'a%.0s' {1..64}; echo ;;
  "volume inspect") [[ -d "$V/$3" ]] ;;
  "volume create") mkdir -p "$V/$3" ;;
  "ps -a") echo "demo-db:postgres:16" ;;
  "run --rm")
    shift 2
    vol=""
    while [[ $# -gt 0 && "$1" != busybox ]]; do
      if [[ "$1" == -v ]]; then vol="${2%%:*}"; shift; fi
      shift
    done
    shift
    case "$1 $2" in
      "tar -C") shift 3; exec tar -C "$V/$vol" "$@" ;;
      "sh -c") ls -A "$V/$vol" | head -n 1 ;;
    esac ;;
  *) exit 1 ;;
esac
EOF
printf '#!/usr/bin/env bash\ncat >/dev/null\nexit 2\n' >"$WORK/failbin/gpg"
chmod +x "$WORK/stubs/"* "$WORK/failbin/gpg"
export OLD_HOME="$OLD" PATH="$WORK/stubs:$PATH"

pass=0 failn=0 OUT="" RC=0
check() { # check <name> <condition-exit-code>
  if [[ "$2" == 0 ]]; then pass=$((pass + 1)); printf '  ok   %s\n' "$1"
  else failn=$((failn + 1)); printf '  FAIL %s\n' "$1"; printf '       output: %s\n' "${OUT:0:600}"; fi
}
# as <home> <command...>: run with that HOME, capture OUT and RC
as() { local h="$1"; shift; OUT="$(HOME="$h" "$@" 2>&1)"; RC=$?; }
mode() { stat -c %a "$1"; }
ok() { "$@" >/dev/null 2>&1; echo $?; }
# dump <file> <passfile>: the decrypted tar.gz
dump() { gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$2" -d <"$1" 2>/dev/null | gzip -dc; }
empty_dir() { [[ -z "$(ls -A "$1")" ]]; }

PW="$WORK/pass"
printf 'demo passphrase 1\n' >"$PW"
printf 'wrong passphrase\n' >"$WORK/wrong"

# ---------------------------------------------------------------- fixture: the old server's home
export HOME="$OLD"
git config --global user.email test@example.com
git config --global user.name test
git config --global init.defaultBranch main
mkdir -p "$HOME/.config/claude-launcher/workers/demo-worker/"{home,trees/t1,tasks} "$HOME/.config/claude-launcher/usage-cache" \
  "$HOME/.claude/"{plugins/cache,plugins/data,shell-snapshots,session-env,projects/demo} "$HOME/.ssh" "$HOME/.agents" "$HOME/bin" "$HOME/.local/bin"
L="$HOME/.config/claude-launcher"
echo "export DEMO_API_KEY='SECRET-MARKER-123'" >"$L/env"
echo '{"private_key":"SECRET-MARKER-123"}' >"$L/fcm-key.json"
echo '{"token":"SECRET-MARKER-123"}' >"$L/workers/demo-worker/home/.credentials.json"
echo tree >"$L/workers/demo-worker/trees/t1/file"
echo out >"$L/workers/demo-worker/tasks/x.out"
echo in >"$L/workers/demo-worker/tasks/x.in"
echo '{}' >"$L/workers/demo-worker/tasks/keep.json"
touch "$L/workers/demo-worker/run.lock.1234"
echo cache >"$L/usage-cache/u.json"
echo '{"theme":"dark"}' >"$HOME/.claude/settings.json"
echo '{}' >"$HOME/.claude/plugins/installed_plugins.json"
echo data >"$HOME/.claude/plugins/data/d"
head -c 1048576 /dev/zero >"$HOME/.claude/plugins/cache/big"
echo snap >"$HOME/.claude/shell-snapshots/x"
echo chat >"$HOME/.claude/projects/demo/chat.jsonl"
echo '{"token":"SECRET-MARKER-123"}' >"$HOME/.claude/.credentials.json"
echo 'ssh-ed25519 AAAA demo' >"$HOME/.ssh/authorized_keys"
echo 'PRIVATE-KEY-DEMO' >"$HOME/.ssh/id_demo"
chmod 600 "$HOME/.ssh/id_demo" "$HOME/.claude/.credentials.json" "$L/env"
chmod 755 "$HOME/.ssh" "$L" "$HOME/.claude"   # deliberately loose: import must tighten them
echo '{}' >"$HOME/.agents/.skill-lock.json"
printf '#!/bin/sh\necho setup\n' >"$HOME/claude-setup.sh"
printf '#!/bin/sh\necho tool\n' >"$HOME/bin/demo-tool"
printf '#!/bin/sh\necho push\n' >"$HOME/.local/bin/claude-push"
chmod 755 "$HOME/claude-setup.sh" "$HOME/bin/demo-tool" "$HOME/.local/bin/claude-push"
echo "password=SECRET-MARKER-123" >"$HOME/.git-credentials"
chmod 600 "$HOME/.git-credentials"
mkdir -p "$STUB_DOCKER/vol/demo-data"
echo "volume-row" >"$STUB_DOCKER/vol/demo-data/db.txt"

P="$HOME/projects"
mkdir -p "$P" "$WORK/remotes"
seed() { # seed <name>: a repo cloned from its own bare remote, one commit pushed
  git init -q --bare "$WORK/remotes/$1.git"
  git clone -q "$WORK/remotes/$1.git" "$P/$1" 2>/dev/null
  (cd "$P/$1" && echo one >f.txt && echo 'ignored.log' >.gitignore && git add . && git commit -qm first && git push -q -u origin main 2>/dev/null)
}
seed demo-clean
seed demo-unpushed
seed demo-dirty
(cd "$P/demo-unpushed" && echo two >g.txt && git add . && git commit -qm "only here")
(cd "$P/demo-dirty" && echo changed >f.txt && echo new >new.txt && echo junk >ignored.log)
for n in demo-noremote "my project"; do
  git init -q "$P/$n" && (cd "$P/$n" && echo x >x.txt && git add . && git commit -qm init)
done
git init -q "$P/demo-token" && (cd "$P/demo-token" && echo x >x.txt && git add . && git commit -qm init &&
  git remote add origin 'https://demo:TOKEN-SECRET-9@example.invalid/demo/x.git')
mkdir -p "$P/demo-plain/node_modules/m"
echo note >"$P/demo-plain/note.txt"
echo js >"$P/demo-plain/node_modules/m/x.js"

# ---------------------------------------------------------------- export
echo "export"
B="$WORK/out/b.tar.gz.gpg"
as "$OLD" "$CB" export --out "$B" --pass-file "$PW"
check "export succeeds" "$RC"
[[ -f "$B" ]]; check "output file exists" $?
[[ "$(mode "$B")" == 600 ]]; check "output file mode 600" $?
[[ "$(mode "$WORK/out")" == 700 ]]; check "output folder mode 700" $?
grep -aq SECRET-MARKER "$B"; [[ $? -ne 0 ]]; check "no plain secret in the raw file" $?
grep -aq 'demo passphrase' "$B"; [[ $? -ne 0 ]]; check "no passphrase in the raw file" $?
[[ "$(find "$WORK/out" -mindepth 1 | wc -l)" == 1 ]]; check "no temp or partial files left" $?
grep -q 'claude-backup import' <<<"$OUT"; check "prints the restore command" $?
grep -q 'demo passphrase' <<<"$OUT"; [[ $? -ne 0 ]]; check "given passphrase is not printed" $?
grep -q 'STORED .*demo-unpushed' <<<"$OUT"; check "summary names the rescued repo" $?
dump "$B" "$PW" | tar -tf - >"$WORK/members.txt"
[[ "$(head -n 1 "$WORK/members.txt")" == manifest.json ]]; check "manifest.json is the first member" $?
[[ "$(grep -c 'projects/demo-clean' "$WORK/members.txt")" == 0 ]]; check "clean pushed repo is not in the archive" $?
grep -q '^projects/demo-unpushed/.git/' "$WORK/members.txt"; check "unpushed repo is stored with .git" $?
grep -q '^projects/my project/x.txt$' "$WORK/members.txt"; check "path with a space" $?
grep -q 'ignored.log' "$WORK/members.txt"; [[ $? -ne 0 ]]; check "gitignored file left out" $?
grep -q 'demo-plain' "$WORK/members.txt"; [[ $? -ne 0 ]]; check "non-git folder not stored without --projects" $?
grep -q 'workers/demo-worker/home/.credentials.json' "$WORK/members.txt"; check "worker login stored" $?
for x in 'workers/demo-worker/trees' 'usage-cache' 'shell-snapshots' 'session-env' 'tasks/x.out' 'tasks/x.in' 'run.lock'; do
  grep -q "$x" "$WORK/members.txt"; [[ $? -ne 0 ]]; check "excluded: $x" $?
done
grep -q 'tasks/keep.json' "$WORK/members.txt"; check "other task files kept" $?
grep -q '^.claude/plugins/cache/big$' "$WORK/members.txt"; check "plugin cache kept without --slim" $?
grep -q '^.claude/plugins/data/d$' "$WORK/members.txt"; check "plugins/data kept" $?

echo "export: failures leave nothing behind"
as "$OLD" "$CB" export --out "$B" --pass-file "$PW"
[[ "$RC" -ne 0 ]]; check "never overwrites an existing file" $?
mkdir "$WORK/o2"
as "$OLD" "$CB" export --out "$WORK/o2/x.gpg" --pass-file "$WORK/nonexistent"
[[ "$RC" -ne 0 ]] && empty_dir "$WORK/o2"; check "missing pass file: fails, nothing left" $?
OUT="$(HOME="$OLD" PATH="$WORK/failbin:$PATH" "$CB" export --out "$WORK/o2/x.gpg" --pass-file "$PW" 2>&1)"; RC=$?
[[ "$RC" -ne 0 ]] && empty_dir "$WORK/o2"; check "gpg failure: non-zero, partial file deleted" $?

echo "export: generated passphrase"
as "$OLD" "$CB" export --out "$WORK/out/gen.gpg" --slim
check "export without a pass file" "$RC"
GEN="$(grep -oE '\b[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){4}\b' <<<"$OUT" | head -n 1)"
[[ -n "$GEN" ]]; check "passphrase has 5 groups of 4 without look-alikes" $?
[[ "$(grep -c "$GEN" <<<"$OUT")" == 1 ]]; check "passphrase printed once" $?
grep -q 'not saved anywhere' <<<"$OUT"; check "write-it-down warning" $?
printf '%s\n' "$GEN" >"$WORK/gen.pass"
as "$OLD" "$CB" list "$WORK/out/gen.gpg" --pass-file "$WORK/gen.pass"
check "generated passphrase opens the file" "$RC"
dump "$WORK/out/gen.gpg" "$WORK/gen.pass" | tar -tf - >"$WORK/members-slim.txt"
grep -q 'plugins/cache' "$WORK/members-slim.txt"; [[ $? -ne 0 ]]; check "--slim drops plugins/cache" $?
grep -q '^.claude/plugins/installed_plugins.json$' "$WORK/members-slim.txt"; check "--slim keeps installed_plugins.json" $?

# ---------------------------------------------------------------- list
echo "list"
as "$OLD" "$CB" list "$B" --pass-file "$PW"
check "list works" "$RC"
M="$OUT"
jq -e '.version == 1 and .user != "" and (.claude_version | startswith("9.9.9"))' <<<"$M" >/dev/null; check "manifest basics" $?
st() { jq -r --arg d "$1" '.repos[] | select(.dir == $d) | .state + ":" + (.rescued | tostring)' <<<"$M"; }
[[ "$(st demo-clean)" == clean:false ]]; check "state clean, not rescued" $?
[[ "$(st demo-unpushed)" == unpushed:true ]]; check "state unpushed, rescued" $?
[[ "$(st demo-dirty)" == dirty:true ]]; check "state dirty, rescued" $?
[[ "$(st demo-noremote)" == no-remote:true ]]; check "state no-remote, rescued" $?
[[ "$(st 'my project')" == no-remote:true ]]; check "state of a folder with a space" $?
[[ "$(st demo-token)" == no-upstream:true ]]; check "state no-upstream, rescued" $?
jq -e '.other_dirs == ["demo-plain"]' <<<"$M" >/dev/null; check "other_dirs lists the non-git folder" $?
jq -e '.env_var_names == ["DEMO_API_KEY"]' <<<"$M" >/dev/null; check "env var names only" $?
jq -e '.backed_up | index(".claude") and index("projects/demo-dirty")' <<<"$M" >/dev/null; check "backed_up lists top-level paths" $?
grep -q 'SECRET-MARKER\|TOKEN-SECRET' <<<"$M"; [[ $? -ne 0 ]]; check "manifest holds no secret values" $?
jq -e '.repos[] | select(.dir == "demo-token") | .remote == "https://example.invalid/demo/x.git"' <<<"$M" >/dev/null; check "token stripped from remote URL" $?
as "$OLD" "$CB" list "$B" --pass-file "$WORK/wrong"
[[ "$RC" -ne 0 ]]; check "list: wrong passphrase fails" $?

# ---------------------------------------------------------------- import
echo "import: failures change nothing"
as "$H2" "$CB" import "$B" --pass-file "$WORK/wrong"
[[ "$RC" -ne 0 ]] && empty_dir "$H2"; check "wrong passphrase: fails, writes nothing" $?
head -c 3000 "$B" >"$WORK/truncated.gpg"
as "$H2" "$CB" import "$WORK/truncated.gpg" --pass-file "$PW"
[[ "$RC" -ne 0 ]] && empty_dir "$H2"; check "damaged file: fails, writes nothing" $?
as "$H2" "$CB" import "$B" --pass-file "$PW" --dry-run
check "dry run succeeds" "$RC"
empty_dir "$H2"; check "dry run writes nothing" $?
grep -q 'demo-unpushed' <<<"$OUT" && grep -q 'Still to do' <<<"$OUT"; check "dry run shows the summary and the to-do list" $?
as "$H2" "$CB" import --pass-file "$PW"
[[ "$RC" -eq 2 ]]; check "import without a file: usage, exit 2" $?

echo "import: full restore with --clone"
as "$H2" "$CB" import "$B" --pass-file "$PW" --clone
check "import succeeds" "$RC"
R="$H2/.config/claude-launcher"
grep -q SECRET-MARKER "$R/env"; check "credentials are back" $?
grep -q SECRET-MARKER "$H2/.claude/.credentials.json" && grep -q SECRET-MARKER "$R/workers/demo-worker/home/.credentials.json"; check "claude + worker logins are back" $?
[[ "$(mode "$H2/.ssh")" == 700 && "$(mode "$R")" == 700 && "$(mode "$H2/.claude")" == 700 && "$(mode "$H2/backups")" == 700 ]]; check "folders are mode 700" $?
[[ "$(mode "$R/env")" == 600 && "$(mode "$R/fcm-key.json")" == 600 && "$(mode "$H2/.ssh/id_demo")" == 600 && "$(mode "$H2/.git-credentials")" == 600 ]]; check "secret files are mode 600" $?
[[ -x "$H2/claude-setup.sh" && -x "$H2/bin/demo-tool" && -x "$H2/.local/bin/claude-push" ]]; check "scripts are executable" $?
[[ -f "$H2/.ssh/authorized_keys" && -f "$H2/.agents/.skill-lock.json" && -f "$H2/.claude/settings.json" ]]; check "other files restored" $?
[[ ! -e "$R/workers/demo-worker/trees" && ! -e "$R/usage-cache" && ! -e "$H2/.claude/shell-snapshots" && ! -e "$R/workers/demo-worker/tasks/x.out" ]]; check "excluded paths are not restored" $?
[[ ! -e "$H2/docker" && ! -e "$H2/manifest.json" ]]; check "manifest and docker/ are not extracted into HOME" $?
[[ "$(git -C "$H2/projects/demo-unpushed" log --oneline | wc -l)" == 2 && "$(git -C "$H2/projects/demo-unpushed" rev-list --count '@{u}..HEAD')" == 1 ]]; check "unpushed commit survived" $?
[[ -f "$H2/projects/demo-dirty/new.txt" && "$(cat "$H2/projects/demo-dirty/f.txt")" == changed && -n "$(git -C "$H2/projects/demo-dirty" status --porcelain)" ]]; check "dirty repo keeps its changes" $?
[[ ! -e "$H2/projects/demo-dirty/ignored.log" ]]; check "ignored file was not copied" $?
[[ -f "$H2/projects/my project/x.txt" && -d "$H2/projects/demo-noremote/.git" ]]; check "no-remote repos restored" $?
[[ -d "$H2/projects/demo-clean/.git" && "$(git -C "$H2/projects/demo-clean" log --oneline | wc -l)" == 1 ]]; check "--clone brought the clean repo back from its remote" $?
[[ ! -e "$H2/projects/demo-plain" ]]; check "non-git folder is not copied" $?
grep -q 'demo-plain' <<<"$OUT" && grep -q 'claude-autostart install' <<<"$OUT" && grep -q 'Re-sign in' <<<"$OUT"; check "to-do list names non-git folders, autostart, worker logins" $?
[[ "$(find "$H2/backups" -maxdepth 1 -name 'pre-restore-*' | wc -l)" == 0 ]]; check "no pre-restore copy when nothing existed" $?

echo "import: pre-restore copies"
echo 'export DEMO_API_KEY=CHANGED-LOCALLY' >"$R/env"
as "$H2" "$CB" import "$B" --pass-file "$PW" --clone
check "second import succeeds" "$RC"
PRE="$(find "$H2/backups" -maxdepth 1 -name 'pre-restore-*' | head -n 1)"
[[ -n "$PRE" && "$(cat "$PRE/.config/claude-launcher/env")" == 'export DEMO_API_KEY=CHANGED-LOCALLY' ]]; check "overwritten file saved in pre-restore-<time>" $?
grep -q SECRET-MARKER "$R/env"; check "overwritten with the backup's version" $?
[[ -d "$PRE/projects/demo-dirty/.git" ]]; check "existing rescued repo copied too" $?

echo "import: --from (scp) and --slim"
mkdir -p "$OLD/backups"
as "$OLD" "$CB" export --out "$OLD/backups/slim.gpg" --pass-file "$PW" --slim
check "slim export" "$RC"
as "$H3" "$CB" import backups/slim.gpg --from demo@oldhost --pass-file "$PW"
check "import --from fetches and restores" "$RC"
[[ -f "$H3/backups/slim.gpg" && "$(mode "$H3/backups/slim.gpg")" == 600 ]]; check "fetched file is mode 600 in ~/backups" $?
[[ -f "$H3/.claude/settings.json" && ! -e "$H3/.claude/plugins/cache" ]]; check "slim restore has no plugins/cache" $?
[[ ! -e "$H3/projects/demo-clean" ]]; check "without --clone the clean repo is not cloned" $?
as "$H3" "$CB" import --from 'bad host' x --pass-file "$PW"
[[ "$RC" -eq 2 ]]; check "--from with a bad value: usage, exit 2" $?
as "$H3" "$CB" import x --from demo@oldhost:backups/slim.gpg --dry-run --pass-file "$PW"
[[ "$RC" -ne 0 ]]; check "--dry-run with --from is refused" $?

echo "export --projects and --docker"
as "$OLD" "$CB" export --out "$WORK/out/all.gpg" --pass-file "$PW" --projects --docker
check "export --projects --docker" "$RC"
dump "$WORK/out/all.gpg" "$PW" | tar -tf - >"$WORK/members-all.txt"
grep -q '^projects/demo-clean/.git/' "$WORK/members-all.txt" && grep -q '^projects/demo-plain/note.txt$' "$WORK/members-all.txt"; check "--projects stores clean repos and non-git folders" $?
grep -q 'node_modules' "$WORK/members-all.txt"; [[ $? -ne 0 ]]; check "node_modules left out of non-git folders" $?
grep -q '^docker/demo-data.tar.gz$' "$WORK/members-all.txt"; check "docker volume stored" $?
grep -q "^docker/a\{64\}" "$WORK/members-all.txt"; [[ $? -ne 0 ]]; check "anonymous docker volumes skipped" $?
as "$OLD" "$CB" list "$WORK/out/all.gpg" --pass-file "$PW"
jq -e '.docker_volumes == ["demo-data"] and .docker_containers == ["demo-db:postgres:16"] and .other_dirs_saved == ["demo-plain"]' <<<"$OUT" >/dev/null; check "manifest lists docker volumes and containers" $?
rm -rf "$STUB_DOCKER/vol/demo-data"
as "$H4" "$CB" import "$WORK/out/all.gpg" --pass-file "$PW"
check "import with docker volumes" "$RC"
[[ "$(cat "$STUB_DOCKER/vol/demo-data/db.txt" 2>/dev/null)" == volume-row ]]; check "docker volume restored into a new volume" $?
[[ -f "$H4/projects/demo-plain/note.txt" && -d "$H4/projects/demo-clean/.git" && ! -e "$H4/projects/demo-plain/node_modules" ]]; check "--projects restore" $?
as "$H5" "$CB" import "$WORK/out/all.gpg" --pass-file "$PW"
grep -q 'demo-data exists and is not empty' <<<"$OUT" && [[ "$(cat "$STUB_DOCKER/vol/demo-data/db.txt")" == volume-row ]]; check "existing non-empty volume is not overwritten" $?

echo "huge repos are skipped, not crashed"
OUT="$(CLAUDE_BACKUP_RESCUE_MAX_MB=0 HOME="$OLD" "$CB" export --out "$WORK/out/small.gpg" --pass-file "$PW" 2>&1)"; RC=$?
check "export with a tiny size limit succeeds" "$RC"
grep -q 'NOT STORED' <<<"$OUT"; check "skipped repos are reported loudly" $?
as "$OLD" "$CB" list "$WORK/out/small.gpg" --pass-file "$PW"
jq -e '[.repos[] | select(.state != "clean")] | all(.rescued == false and (.skipped_reason | test("larger than")))' <<<"$OUT" >/dev/null; check "manifest records skipped_reason" $?

echo "usage"
as "$OLD" "$CB" --help
[[ "$RC" -eq 0 ]] && grep -q 'export' <<<"$OUT" && grep -q 'import' <<<"$OUT"; check "--help" $?
as "$OLD" "$CB" frobnicate
[[ "$RC" -eq 2 ]]; check "unknown command: exit 2" $?
as "$OLD" "$CB" export --bogus
[[ "$RC" -eq 2 ]]; check "unknown flag: exit 2" $?
as "$OLD" "$CB"
[[ "$RC" -eq 2 ]]; check "no command: exit 2" $?

echo "the real home was not touched"
[[ ! -e "$WORK/old/.gnupg" ]]; check "no ~/.gnupg created (throwaway GNUPGHOME)" $?

echo
echo "$pass passed, $failn failed"
((failn == 0))
