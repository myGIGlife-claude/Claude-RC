---
name: rc-shell-scripting
description: Writing and reviewing scripts in Bash, POSIX sh (dash, busybox ash), zsh, PowerShell 7 / Windows PowerShell 5.1 and modern Perl. Quoting, set -euo pipefail pitfalls, traps, mktemp, flock, getopts, find -print0, GNU vs BSD/macOS sed/date/stat, jq/yq, curl, cron/systemd timers, Docker entrypoints, Makefile/just/Task, ShellCheck, shfmt, bats, Pester, PSScriptAnalyzer. Use for *.sh, *.bash, *.zsh, *.ps1, *.psm1, *.pl, Makefile, justfile.
---
# Shell scripting: Bash, POSIX sh, zsh, PowerShell, Perl  (as of 2026-10)

> Facts are dated (see Sources). Anything marked (unverified) is a lead, not a fact. GNU vs BSD utility table and
> PowerShell 5.1 vs 7 differences: `references/portability.md`. Server ops (systemd units, Docker, SSH hardening) live in
> **rc-devops-linux**; test strategy in **rc-testing-debugging**; Python as the "bigger script" language in **rc-python**.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| Bash | **5.3** (2025-07-30), official patches up to `bash53-020` = 5.3.20 (2026-09-14) | 5.2 (2022-09-26, last tarball 5.2.37) is what Debian 13 ships (5.2.37); Ubuntu 26.04 ships 5.3 |
| Bash 5.3 new | `${ cmd; }` (command substitution in the current shell, no fork), `${\| cmd; }` (result in `REPLY`), `GLOBSORT`, `source -p PATH`, `read -E`, `array_expand_once` (replaces `assoc_expand_once`), `BASH_MONOSECONDS`, `BASH_TRAPSIG`, `compgen -V` | `${ ...; }` and `GLOBSORT` are 5.3-only: guard or avoid in scripts that run elsewhere |
| Bash 5.2 changes | `patsub_replacement` **on by default** (`&` in `${v//pat/rep}` means "the match"), `globskipdots` on by default, `${v@k}`, `varredir_close` | `${path//x/&}` silently changed meaning in 5.2: quote the replacement (`"&"`) or escape `\&` |
| Bash 5.1 / 5.0 | 5.1: `wait -p VAR`, `SRANDOM`, `${v@U}`/`@L`; 5.0: `EPOCHSECONDS`, `EPOCHREALTIME` | |
| macOS | `/bin/bash` has long been **3.2.57** (GPLv2 era; not re-checked on macOS 26, run `/bin/bash --version`); zsh is the login shell since 10.15 | No assoc arrays, `mapfile`, `${v,,}`, `wait -n`, `${v@Q}` in 3.2. Use Homebrew bash + `#!/usr/bin/env bash` if you need 4+/5 |
| zsh | **5.9.2** latest tag (zsh-users/zsh) | |
| POSIX | **POSIX.1-2024 (Issue 8)**: adds `$'...'`, `read -d`, `find -print0`, `readlink`, `realpath`, `timeout`, `set -o pipefail`, `xargs -0` and `xargs -r` (all verified on spec pages) | dash / busybox ash support of Issue 8 features varies: test on the target shell |
| dash / busybox | Debian/Ubuntu `/bin/sh` = dash; Alpine `/bin/sh` = busybox ash | dash has no arrays, no `[[`, no `local -n`; `pipefail` landed upstream 2024-04 (first release believed 0.5.13, unverified): older dash lacks it |
| PowerShell | **7.6 LTS** (2026-03-18, .NET 10, EOS 2028-11-14; current 7.6.6); 7.5 stable (7.5.11, EOS **2026-11-10**); 7.4 LTS (EOS **2026-11-10**); 7.7 preview (7.7.0-preview.5, 2026-09-23, .NET 11) | 7.6: `Microsoft.PowerShell.ThreadJob` replaces `ThreadJob`, `Join-Path -ChildPath` takes `string[]`, `PSRedirectToVariable` / `PSNativeWindowsTildeExpansion` now mainstream, ships PSResourceGet 1.2.0 |
| Windows PowerShell | **5.1** (2016) only; in-box on Windows, follows the Windows lifecycle, no new features | PowerShell 2.0 engine removed from Windows 11 24H2 (2025-08 update) and Windows Server 2025 (2025-09); scripts asking for 2.0 get 5.1 |
| Pester | **6.2.0** (2026-09-09); 6.0.0 (2026-07-07) | Runs on 5.1 and 7.4+. New `Should-*` assertions (`Should-Be`); old `Should -Be` still works |
| PSScriptAnalyzer | **1.25.0** (2026-03-20) | |
| Perl | **5.44.0** (2026-07-15); maintenance 5.42.3 and 5.40.5 (2026-08-02) | 5.44: named parameters in signatures (experimental), multi-var `foreach` with ref aliases, `enhanced_xx` (experimental), Unicode 17; `goto` into a loop body now throws |
| ShellCheck | **0.11.0** (2025-08-04) | New: SC2327/2328 (capture vs redirect), SC2329 (function never called), SC2331 (`-e` not `-a`); SC2002 (useless cat) now optional |
| shfmt | **3.14.1** (2026-09-06) | Parses zsh since 3.13 |
| bats-core | **1.14.0** (2026-07-21) | **Breaking:** `run` now honours `set -e` inside your functions; fails when no tests found unless `--allow-empty-suite` |
| ShellSpec | 0.28.1 (2021-01-11), no release since | Works, but prefer bats-core for new suites |
| jq / yq | jq **1.8.2** (2026-06-20, 22 security fixes incl. 16 CVEs: upgrade); yq (mikefarah) **4.54.1** (2026-09-29, adds jq-style `if-then-elif-else-end`) | Two different "yq" exist (Python wrapper vs Go mikefarah): check `yq --version` |
| just / Task | just **1.58.0** (2026-08-03); Task **3.54.0** (2026-10-01) | |

### Older versions (what differs on legacy hosts)
- **Bash 4.x** (RHEL 8 = 4.4, CentOS 7 = 4.2): no `wait -p` (5.1), `EPOCHSECONDS` (5.0), `@U/@L` (5.1). Bash < 4.4: `set -u` + empty array
  `"${a[@]}"` errors "unbound variable"; no `inherit_errexit`, `${v@Q}`, `mapfile -d`. Workaround: `${a[@]+"${a[@]}"}`.
- **Bash 3.2 (macOS)**: no `declare -A`, `mapfile`/`readarray`, `|&`, `&>>`, `${v,,}`/`${v^^}`, `coproc`, `globstar`, `wait -n`, negative array
  indexes. If a script must run with stock macOS, write POSIX sh or bash-3.2 code and test it there (CI `macos-latest` runner).
- **Bash 5.0/5.1 vs 5.2**: replacement `&` in `${v//p/r}` is literal before 5.2 and "the match" from 5.2 (unless `shopt -u patsub_replacement`).
- **PowerShell 7.4 / 7.5**: both reach end of support **2026-11-10**: new work targets 7.6 LTS. Do not push an upgrade the task did not ask for, but
  flag it. Windows PowerShell 5.1 scripts: see `references/portability.md` (encoding, `&&`/`||`, `-Parallel`, ternary, `??` all 7.x only).
- **Perl 5.36-5.40**: `use v5.36` already gives strict, warnings, signatures, `say`, `isa`; `try/catch` is stable from 5.40 (the `try` bundle); 5.42+
  bundles drop `smartmatch` and the `'` package separator. Perl on RHEL 8 is 5.26 (no signatures without `use feature`, experimental there).

## What changed / stop doing

| Old | New | Since |
|---|---|---|
| Backticks `` `cmd` `` | `$(cmd)` (nests, readable) | POSIX |
| `which foo` | `command -v foo` (POSIX, builtin, reliable exit code) | always |
| `egrep` / `fgrep` | `grep -E` / `grep -F` (GNU grep warns "obsolescent") | GNU grep 3.8 (2022) |
| `#!/bin/sh` + `[[`, arrays, `function`, `source` | `#!/usr/bin/env bash`, or real POSIX: `[ ]`, `.` instead of `source` | dash is `/bin/sh` on Debian/Ubuntu |
| `echo -e "a\tb"` / `echo -n` | `printf 'a\tb\n'` / `printf '%s' "$x"` | `echo` options differ across shells |
| `for f in $(ls *.txt)` / `ls \| while read` | `for f in ./*.txt; do [ -e "$f" ] \|\| continue` or `find ... -print0` | parsing `ls` breaks on spaces/newlines |
| `cat file \| while read line` (vars lost) | `while IFS= read -r line; do ...; done < file` or `< <(cmd)` | subshell scope |
| `x=$(cmd); [ $? -eq 0 ]` | `if x=$(cmd); then` | |
| `local out=$(cmd)` (masks exit code) | `local out; out=$(cmd)` | |
| `$[ a + b ]`, `expr` | `$(( a + b ))` | |
| `curl url \| sudo bash` | download, verify checksum/signature, read, then run; or a distro package | supply chain |
| `curl -s url` | `curl -fsSL --retry 3 --retry-all-errors --connect-timeout 10 --max-time 60` (`--fail-with-body` to keep error body) | curl 7.71 (`--retry-all-errors`) / 7.76 (`--fail-with-body`) |
| `mktemp` path guessing `/tmp/foo.$$` | `mktemp` / `mktemp -d` + `trap` cleanup | races |
| `set -e` alone as "error handling" | `set -Eeuo pipefail` + `shopt -s inherit_errexit` + explicit checks (see pitfalls) | bash 4.4 |
| PowerShell `Install-Module` (PowerShellGet 2) | `Install-PSResource` (Microsoft.PowerShell.PSResourceGet, in-box since 7.4) | PS 7.4 |
| `ThreadJob\Start-ThreadJob` | `Microsoft.PowerShell.ThreadJob\Start-ThreadJob` (unqualified name unchanged) | PS 7.6 |
| Pester `Should -Be` (v5) | `Should-Be` and friends (`Should-BeEquivalent`); old syntax still works | Pester 6.0 |
| `use strict; use warnings;` + `my ($a,$b) = @_;` | `use v5.36;` + `sub f ($a, $b) { }` | Perl 5.36 |
| Perl `given/when`, `~~` | `if/elsif`, `List::Util::any`; smartmatch is gone from the 5.42 bundle | 5.42 |
| `eval { }; if ($@)` | `use v5.40; try { } catch ($e) { }` | 5.40 |
| ShellSpec for new suites | bats-core 1.14 (active) | 2021 last ShellSpec release |

## Do this

### Pick the language first (when NOT to write shell)
Shell is glue: run programs, move files, set env. Switch to Python (stdlib) or Go when **any** of these holds:
- more than ~150 lines, or more than 3-4 functions with shared state;
- you need data structures beyond a flat list/map, JSON beyond a `jq` one-liner, dates/time zones, floats, Unicode text processing;
- real error handling/retries/concurrency with results, or a long-running daemon;
- it must run on Windows and Unix (use PowerShell 7 or Python, not bash-via-WSL);
- untrusted input is parsed (injection risk grows with every `eval`, `sh -c`, `ssh host "$cmd"`).
Perl is still right for: one-liner text munging beyond `sed`/`awk` (`perl -pi -e`), legacy Perl codebases, and systems where Perl is already
in the base image (it is on Debian/Ubuntu/macOS; Python may not be). Do not add a CPAN or npm dependency to a script that `awk` covers.

### Bash script template
```bash
#!/usr/bin/env bash
# usage: deploy.sh [-n] [-v] <target>
set -Eeuo pipefail
shopt -s inherit_errexit nullglob      # bash >= 4.4
IFS=$'\n\t'                            # optional: stops accidental word splitting on spaces

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd); readonly SCRIPT_DIR   # split: SC2155
log()  { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" >&2; }   # logs to stderr, data to stdout
die()  { log "ERROR: $*"; exit 1; }
usage(){ sed -n '2s/^# //p' "$0"; exit "${1:-0}"; }

dry_run=0 verbose=0
while getopts ':nvh' opt; do
  case $opt in
    n) dry_run=1 ;; v) verbose=1 ;; h) usage 0 ;;
    *) usage 2 ;;
  esac
done
shift $((OPTIND - 1))
(( $# == 1 )) || usage 2
target=$1

tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
trap 'log "failed at line $LINENO: $BASH_COMMAND"' ERR

command -v jq >/dev/null || die "jq is required"
if (( verbose )); then set -x; fi   # not `(( v )) && set -x`: as a last line it makes the script exit 1
```
- Exit codes: 0 ok, 1 general error, 2 usage error; 126/127 cannot execute/not found; 128+N killed by signal N (130 = Ctrl-C, 141 = SIGPIPE).
- Long options: `getopts` has none; hand-roll a `while (( $# )); do case $1 in --name=*) ...; --) shift; break;; esac; shift; done` loop.
  Avoid `getopt(1)`: GNU and BSD versions differ.
- `--help` to stdout + exit 0; errors to stderr + non-zero. Honour `NO_COLOR` and only colour when `[ -t 2 ]`.

### Quoting (the rule that prevents most bugs)
- Quote every expansion: `"$var"`, `"$(cmd)"`, `"${arr[@]}"`. Unquoted only on purpose (and comment why).
- `"$@"` passes args intact; `$*` / `$@` unquoted re-split them.
- Single quotes for literals, `printf '%q'` or `${v@Q}` (bash 4.4) to build a string that is safe to re-read by the shell (e.g. for `ssh`).
- Inside `[[ ]]` the right side of `==`/`=~` is a pattern: quote it to compare literally (`[[ $a == "$b" ]]`). Put regexes in a variable:
  `re='^v([0-9]+)\.([0-9]+)$'; [[ $tag =~ $re ]] && major=${BASH_REMATCH[1]}` (unquoted `$re`).
- `[[ ]]` (bash/zsh) has no word splitting and supports `&&`, `<`, `=~`. In sh use `[ ]`, quote both sides, use `=` not `==`, and never `-a`/`-o`.

### `set -euo pipefail`: what it does not do
- `set -e` is ignored inside `if`/`while` conditions, left of `&&`/`||`, after `!`, and in **any function called from those**: `f || true`
  disables `-e` for everything `f` runs.
- `local x=$(false)` and `export x=$(false)` succeed (exit status of `local`). Split declaration and assignment.
- `$(...)` does not inherit `-e` without `shopt -s inherit_errexit` (bash 4.4+).
- `(( count++ ))` returns 1 when `count` was 0 and kills the script: use `count=$((count + 1))` or `(( ++count ))`.
- `pipefail`: `producer | head -1` can fail with 141 (SIGPIPE) when the producer is still writing. Accept it explicitly or restructure.
- `grep` returns 1 on "no match": `n=$(grep -c x f || true)`.
- `set -u` with bash < 4.4 trips on empty arrays; `${var:-}` for optional env vars.
- `-e` in a subshell exits only the subshell; `cmd &` failures need `wait "$pid"` to surface.
Treat `-e` as a safety net, and still check commands that matter: `cp -- "$a" "$b" || die "copy failed"`.

### Files, temp files, atomic writes, locking
```bash
tmp=$(mktemp "${out}.XXXXXX")           # same directory = same filesystem, so mv is atomic
generate >"$tmp" && mv -f -- "$tmp" "$out"
exec 9>/run/lock/myjob.lock             # or "$XDG_RUNTIME_DIR"; flock is util-linux (not on macOS)
flock -n 9 || die "already running"
cd -- "$dir" || exit                    # never continue in the wrong directory
```
- Iterate files safely: `find . -type f -name '*.log' -print0 | xargs -0 -r gzip --` or
  `while IFS= read -r -d '' f; do ...; done < <(find . -type f -print0)`.
- `mapfile -t lines < file` (bash 4+) reads lines into an array; `mapfile -d '' -t files < <(find ... -print0)` (bash 4.4+).
- Globs: `shopt -s nullglob` (no match -> nothing, not the literal pattern); `globstar` for `**`; `dotglob` to include hidden files.
- Here-docs: `<<'EOF'` (quoted) for literal text, `<<EOF` expands variables, `<<-` strips leading **tabs** only.

### Subshells and scope
- A pipeline's parts run in subshells: `cmd | while read -r x; do n=$((n+1)); done` loses `n`. Use `done < <(cmd)` or `shopt -s lastpipe`
  (non-interactive bash only).
- `( cd dir && make )` keeps the parent's cwd; `{ ...; }` groups without a subshell.
- `${ cmd; }` (bash 5.3) captures output in the current shell; do not use it in scripts that may run on bash < 5.3.

### Arrays and associative arrays (bash 4+)
```bash
args=(--quiet --output "$out")
[[ $verbose == 1 ]] && args+=(--verbose)
tool "${args[@]}" -- "$input"             # build commands as arrays, never as strings
declare -A seen=()
for k in "${keys[@]}"; do [[ -v seen[$k] ]] && continue; seen[$k]=1; done   # -v on elements: bash 4.3+
```

### Concurrency
- `cmd1 & p1=$!; cmd2 & p2=$!; wait "$p1"; wait "$p2"` collects each status. `wait -n` (4.3) returns the next finished job; `wait -n -p id` (5.1)
  tells you which. For bounded parallelism prefer `xargs -0 -P "$(nproc)" -n1` over hand-written job pools.

### POSIX sh (dash, busybox ash) when you need it
- Shebang `#!/bin/sh`, then **only** POSIX: `[ ]`, `$(( ))`, `case` for patterns, `.` not `source`, no arrays (use `"$@"` via `set --`),
  `local` is not POSIX but dash/ash support it. Check with `shellcheck -s sh` and run under `dash -n` and `busybox sh`.
- `set -o pipefail` is POSIX since Issue 8, but older dash/ash lack it: guard it (`(set -o pipefail) 2>/dev/null && set -o pipefail`).

### zsh essentials (interactive config and zsh scripts)
- Unquoted `$var` does **not** word-split in zsh (unless `setopt sh_word_split`); arrays are 1-based; a glob with no match is an error
  (`(N)` qualifier for nullglob: `for f in *.txt(N)`).
- Do not write portable scripts in zsh; `emulate -L sh` inside a function if you must run sh code. Config: `~/.zshenv` (all), `~/.zshrc` (interactive).

### PowerShell 7 (scripts, modules)
```powershell
#Requires -Version 7.4
[CmdletBinding(SupportsShouldProcess)]
param(
  [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Path,
  [switch]$Force
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true   # 7.4+: non-zero exit of native tools throws
try {
  Get-ChildItem -LiteralPath $Path -File |
    Where-Object Length -gt 1MB |
    ForEach-Object { if ($PSCmdlet.ShouldProcess($_.FullName, 'Remove')) { Remove-Item -LiteralPath $_.FullName } }
} catch {
  Write-Error "Cleanup failed: $_"; exit 1
}
```
- The pipeline carries **objects**: filter with `Where-Object`/`Select-Object`, do not parse text. Output with `Write-Output` (data), `Write-Verbose`/
  `Write-Information`/`Write-Warning` (diagnostics); `Write-Host` only for UI.
- `-LiteralPath` whenever a path comes from data (wildcards `[ ]` in names). `Join-Path`, `[IO.Path]::Combine`, never string `+ '\'`.
- Non-terminating errors do not hit `catch` unless `-ErrorAction Stop` or `$ErrorActionPreference = 'Stop'`.
- Native exit codes: `$LASTEXITCODE`; with `$PSNativeCommandUseErrorActionPreference = $true` they throw.
- Modules: a `.psd1` manifest with explicit `FunctionsToExport` (no `*`), `Install-PSResource`/`Publish-PSResource`. Pin versions in CI.
- Parallel: `ForEach-Object -Parallel { } -ThrottleLimit 8` (7.x); `$using:var` for outer variables.
- Remoting: PowerShell over SSH (`Enter-PSSession -HostName host -UserName u`, `Invoke-Command -HostName`) works cross-platform; WinRM only Windows.
- Execution policy is **not a security boundary** (bypass is trivial); real control is code signing (`Set-AuthenticodeSignature`) + AppLocker/WDAC.
  Do not tell users to `Set-ExecutionPolicy Unrestricted`; `RemoteSigned` at CurrentUser scope is the usual default.
- Encoding: 7.x writes UTF-8 without BOM by default; 5.1 defaults differ per cmdlet (see references). Pass `-Encoding utf8` when 5.1 matters.

### Modern Perl
```perl
#!/usr/bin/env perl
use v5.36;                       # strict, warnings, say, signatures, no indirect (bareword FHs off from v5.38)
use autodie;                     # open/close/unlink die on failure
sub slurp ($path) { open my $fh, '<:encoding(UTF-8)', $path; local $/; <$fh> }
my @out = qx{git rev-parse HEAD};             # avoid; prefer list form below
system('git', 'tag', '--', $name) == 0 or die "git failed: $?";   # list form, no shell
```
- 3-arg `open` with a lexical handle, always. List-form `system`/`exec`/`open(my $fh, '-|', @cmd)`: no shell, no injection.
- Taint mode (`#!perl -T`) for setuid/CGI-style scripts handling untrusted input; untaint only through a strict regex capture.
- CPAN hygiene: `cpanfile` + `cpanm --installdeps .` or Carton (`carton install`, commit `cpanfile.snapshot`); never `sudo cpan` into system perl;
  use `local::lib` or perlbrew/plenv. Prefer core modules (`File::Temp`, `JSON::PP`, `Getopt::Long`, `Time::Piece`, `List::Util`).
- Perl 5.44 named signature params (experimental) and `class` (experimental since 5.38): do not use in code that must run on older perls.

### JSON, YAML, HTTP
- `jq -r --arg name "$name" '.items[] | select(.name == $name) | .id'` (pass data with `--arg`/`--argjson`, never splice into the filter).
  `jq -e` sets exit status from the result; `jq -n --arg k "$v" '{key:$k}'` builds JSON safely (never `printf '{"k":"%s"}'`).
- yq (mikefarah): `yq -i '.image.tag = strenv(TAG)' values.yaml`; use `strenv`/`env` to inject values. `fx` is for interactive browsing.
- curl: `-f` (fail on HTTP >= 400), `-sS` (quiet but show errors), `-L`, `--retry N --retry-all-errors`, `--connect-timeout`, `--max-time`,
  `-o file` then check; secrets via `-H @file` or `--netrc-file`, not argv. Check `-w '%{http_code}'` when you need the status.

### ssh / rsync / scp in scripts
- `ssh -o BatchMode=yes -o ConnectTimeout=10 host -- cmd` (no password prompt hang). The remote side re-parses the string with its shell:
  quote with `printf '%q'` / `${v@Q}` or pipe a script: `ssh host bash -s -- "$arg" < remote.sh`.
- `rsync -a --delete --partial` with a trailing `/` on the source to copy contents; `--dry-run` first for `--delete`. `scp` now uses SFTP by
  default (OpenSSH 9.0); prefer `rsync` or `sftp -b` for scripted transfers.

### Scheduling
- systemd timer over cron on systemd hosts (logging in the journal, `Persistent=true` catches missed runs, `RandomizedDelaySec`). Units: rc-devops-linux.
- cron: absolute paths, minimal env (`PATH` is short), `%` must be escaped, wrap with `flock -n` to avoid overlap, send output to a log or journald (`| systemd-cat -t job`).

### Task runners
- `Makefile`: recipes need **tabs**; mark non-file targets `.PHONY`; `SHELL := bash` + `.SHELLFLAGS := -euo pipefail -c` for strictness; each recipe line
  is a new shell (use `.ONESHELL:` or `\`).
- `just` (justfile) or Task (Taskfile.yml) when you want a command runner without make's file-timestamp semantics. Do not add one to a repo that has a
  working Makefile or `package.json` scripts.

### Container entrypoints and signals
```sh
#!/bin/sh
set -eu
# one-time setup here (render config, wait for DB with a timeout)
exec "$@"        # replace the shell: the app becomes PID 1 and receives SIGTERM
```
- Without `exec`, the shell is PID 1, ignores/does not forward SIGTERM, and the container is killed after the stop timeout (10 s default).
- PID 1 gets no default signal handlers: use `docker run --init` / `init: true` (tini) if the app does not handle SIGTERM or reaps no zombies.
- Exec-form `CMD ["app","--flag"]`, not shell form. In bash supervisors: `trap 'kill -TERM "$child"; wait "$child"' TERM INT`.

### CI scripts
- `set -Eeuo pipefail` explicitly (GitHub Actions `shell: bash` already uses `-eo pipefail`, but not `-u`). Print the tool versions you depend on.
- Group/mask: `::add-mask::` for derived secrets in Actions; never `set -x` in steps that touch secrets.

### Logging and verbosity
- Diagnostics to stderr, data to stdout (so `script | jq` works). `-v` / `-q` flags or `LOG_LEVEL`; timestamps in UTC ISO 8601.
- `set -x` with `PS4='+ ${BASH_SOURCE##*/}:${LINENO}: '` for debugging; `BASH_XTRACEFD` to send the trace elsewhere.

## Security
- **Command injection**: never `eval`, `sh -c "$str"`, `ssh host "$str"`, `xargs sh -c` with untrusted data spliced in. Pass data as arguments:
  `sh -c 'grep -- "$1" "$2"' _ "$pat" "$file"`. Validate with `case`/`[[ =~ ]]` allow-lists (`^[A-Za-z0-9._-]+$`).
- **Option injection**: a filename `-rf` becomes a flag. Use `--` before operands (`rm -- "$f"`, `grep -- "$p"`), or `./*` globs.
- **Arithmetic injection**: `(( x == $input ))` and `[[ $a -eq $b ]]` evaluate `a[$(cmd)]`-style payloads. Validate as `^[0-9]+$` first.
- **Secrets**: never in argv (visible in `ps`, `/proc/*/cmdline`), avoid exporting them broadly, never `set -x` around them, read with
  `read -rs` or from a 0600 file / `systemd-creds` / CI secret store; `HISTCONTROL=ignorespace` interactively. Pass to curl via `-H @file` or stdin.
- **Temp races**: only `mktemp`; `umask 077` before creating secret files; do not reuse predictable names in `/tmp`.
- **PATH hijack**: scripts run by root/cron/sudo set `PATH=/usr/sbin:/usr/bin:/sbin:/bin` explicitly; never include `.` or writable dirs.
  `sudo` resets env (`secure_path`): do not rely on the caller's `PATH`/env, and grant narrow sudoers rules (full path, fixed args) not `ALL`.
  Ubuntu 26.04 ships sudo-rs as `sudo`: test unusual sudoers features.
- **Remote installers**: no `curl | sh` in docs or automation without a pinned version and checksum/signature verification.
- **Perl**: list-form `system`, 3-arg `open`, taint mode for untrusted input. **PowerShell**: no `Invoke-Expression` on input; `-LiteralPath`;
  `SecureString`/`SecretManagement` for credentials; sign scripts distributed to others.
- Unsafe globs and `rm`: guard `rm -rf -- "${dir:?}/"*` (`:?` aborts on empty) so an empty variable cannot become `rm -rf /*`.

## Performance & quality
- Forks dominate: avoid `$(echo ...)`, `$(cat f)` (use `$(<f)`), `$(basename "$p")` in loops (use `${p##*/}`, `${p%/*}`, `${f%.*}`).
- Never call `sed`/`awk`/`grep` once per line in a `while read` loop over a big file: one `awk` over the whole file is 100-1000x faster.
- `printf -v var` assigns without a subshell; `${ cmd; }` (5.3) avoids the fork of `$( )`.
- Use `xargs -P` / GNU `parallel` for CPU-bound fan-out; `find -exec ... {} +` batches arguments.
- Measure with `time`, `hyperfine 'script a' 'script b'`; `bash -x` / `PS4` with `$EPOCHREALTIME` for per-line timing.
- Quality bar: ShellCheck clean (or each disable has a comment), shfmt formatted, `--help` works, idempotent re-runs, non-zero exit on failure.

## Testing & tooling
- **ShellCheck** 0.11 in CI and the editor: `shellcheck -x -S style script.sh` (`-x` follows `source`). Notable rules: SC2086 (quote), SC2046
  (unquoted `$( )`), SC2155 (`local x=$(...)` masks status), SC2164 (`cd` without `|| exit`), SC2068 (`$@` unquoted), SC2010/SC2012 (parsing `ls`),
  SC2124 (array to string), SC2128, SC3xxx (non-POSIX in sh). Set `# shellcheck shell=bash` for sourced files.
- **shfmt** 3.14: `shfmt -i 2 -ci -bn -d .` in CI (`-w` locally); `.editorconfig` is read.
- **bats-core** 1.14 (+ bats-support/bats-assert/bats-file): `run --separate-stderr cmd; [ "$status" -eq 0 ]`. Remember 1.14: `run` honours `set -e`
  in functions; `--errexit` exists. bashate (OpenStack style) is optional; ShellCheck + shfmt cover most needs.
- **PowerShell**: PSScriptAnalyzer (`Invoke-ScriptAnalyzer -Path . -Recurse -Settings PSGallery`), Pester 6 (`Invoke-Pester -CI`), format with
  `Invoke-Formatter`. **Perl**: `perlcritic`, `perltidy`, `prove -lr t/` with `Test2::V0`.
- CI matrix for portable scripts: Ubuntu (bash 5.x + dash), Alpine (busybox), macOS (bash 3.2, BSD tools).

## Common mistakes in AI-written code
- `#!/bin/sh` with `[[`, arrays, `function f {`, `source`, `==`, `$'..'`, `echo -e`: breaks on dash/Alpine.
- `local x=$(cmd)` then checking `$?`; `set -e` assumed to catch failures inside `if f; then` / `f || ...`.
- `for f in $(find ...)`, `for line in $(cat file)`, `ls | grep`, `read` without `-r` or `IFS=`.
- Building commands as strings (`cmd="tar -czf $out $dir"; $cmd`) instead of arrays; `eval` to "fix" quoting.
- GNU-only flags on macOS: `sed -i` without suffix, `date -d`, `stat -c`, `grep -P`, `find -printf`, `xargs -r`, `readlink -f` on old macOS,
  `base64 -w0`, `sort -V` on old BSD. See `references/portability.md`.
- `which` for existence checks; `[ $var = x ]` unquoted (fails when empty); `[ -n $var ]` (always true when empty).
- `trap cleanup EXIT` set **after** creating the temp dir (leaks on early failure) or cleanup with unquoted `rm -rf $tmp`.
- `${var//x/&}` written for bash < 5.2 semantics; `declare -A` / `mapfile` in a script meant for macOS `/bin/bash`.
- Entrypoint without `exec`; `CMD` in shell form; ignoring SIGTERM.
- `curl` without `-f` (HTML error page saved as the artifact), without timeouts, or with a token on the command line.
- JSON assembled with `printf`/`echo` (breaks on quotes); `jq` filter built by string interpolation.
- PowerShell: `$ErrorActionPreference` left at Continue, `Write-Host` for data, string-parsing `ls` output, `Invoke-Expression`, `-Path` with user data,
  `&&`/ternary/`??`/`-Parallel` in scripts that must run on 5.1, `Install-Module` advice where PSResourceGet is available.
- Perl: 2-arg `open`, missing `use strict` (or `use v5.36`), backticks with interpolated input, `given/when`, `sudo cpan`.
- Inventing options: `bash --strict`, `set -o errtrace` confused with `-e` (it is `-E`: ERR trap inherits into functions), `mktemp --suffix` on BSD,
  `flock` on macOS.

## Before you ship
- [ ] Right language (threshold list above); shebang matches the features used; `bash --version` floor documented if > 3.2.
- [ ] `shellcheck` clean, `shfmt -d` clean (PS: PSScriptAnalyzer; Perl: perlcritic) in CI.
- [ ] Strict mode set, every critical command checked, `cd` guarded, `trap` cleanup registered before temp files are created.
- [ ] All expansions quoted; `--` before user operands; no `eval`/`Invoke-Expression`/string commands on input; inputs validated.
- [ ] No secrets in argv, logs or `set -x`; files with secrets created under `umask 077`.
- [ ] Atomic writes (temp + `mv`), lock for anything cron/timer runs, idempotent on re-run.
- [ ] Works on every target: tested under dash/busybox/macOS if it claims portability; GNU vs BSD flags checked.
- [ ] `--help`, meaningful exit codes, diagnostics on stderr, `--dry-run` for destructive actions.
- [ ] Tests (bats/Pester/prove) cover the happy path, a failure path, and filenames with spaces/newlines/leading dashes.
- [ ] Rollback: destructive scripts keep a backup or are reversible; containers `exec` the app and stop cleanly on SIGTERM.

## Sources
- https://tiswww.case.edu/php/chet/bash/NEWS - bash 5.3 feature list (2026-10-09)
- https://cgit.git.savannah.gnu.org/cgit/bash.git/plain/NEWS?h=bash-5.3 - 5.3 and 5.2 features (`patsub_replacement`, `globskipdots`, `source -p`) (2026-10-09)
- https://ftp.gnu.org/gnu/bash/ - release dates 5.2 (2022-09-26), 5.3 (2025-07-30), 5.3 patches dir (2026-10-09)
- https://learn.microsoft.com/en-us/powershell/scripting/install/powershell-support-lifecycle - PS versions, LTS, EOS dates (2026-10-09)
- https://learn.microsoft.com/en-us/powershell/scripting/whats-new/what-s-new-in-powershell-76 - 7.6 changes (2026-10-09)
- https://github.com/PowerShell/PowerShell/releases - v7.6.6 2026-09-08, v7.7.0-preview.5 2026-09-23 (2026-10-10)
- https://github.com/pester/Pester/releases - 6.0.0 (2026-07-07) notes, 6.2.0 (2026-09-09) (2026-10-09)
- https://github.com/PowerShell/PSScriptAnalyzer/releases - 1.25.0 (2026-10-09)
- https://metacpan.org/dist/perl and https://perldoc.perl.org/perldelta - Perl 5.44.0 date and changes, 5.42.3/5.40.5 (2026-10-09)
- https://perldoc.perl.org/feature - feature bundles 5.36-5.44 (2026-10-09)
- https://github.com/koalaman/shellcheck/releases - 0.11.0 checks (2026-10-09)
- https://github.com/mvdan/sh/releases - shfmt 3.14.1 (2026-10-09)
- https://github.com/bats-core/bats-core/releases - 1.14.0 breaking change (2026-10-09)
- https://github.com/shellspec/shellspec/releases - 0.28.1 last release (2026-10-09)
- https://github.com/jqlang/jq/releases - jq 1.8.2 security fixes (2026-10-09)
- https://github.com/mikefarah/yq/releases - yq 4.54.1 (2026-10-09)
- https://github.com/casey/just/releases and https://github.com/go-task/task/releases - just 1.58.0, Task 3.54.0 (2026-10-09)
- https://github.com/zsh-users/zsh/tags - zsh-5.9.2 (2026-10-09)
- https://pubs.opengroup.org/onlinepubs/9799919799/xrat/V4_xcu_chap01.html - Issue 8: `$'...'`, readlink/realpath/timeout (2026-10-09)
- https://pubs.opengroup.org/onlinepubs/9799919799/utilities/read.html and .../find.html - `read -d`, `-print0` added in Issue 8 (2026-10-09)
- https://ftp.gnu.org/gnu/bash/bash-5.3-patches/ - patches 001-020, newest 2026-09-14 (2026-10-10)
- https://www.gnu.org/software/bash/manual/html_node/The-Shopt-Builtin.html - `patsub_replacement`, `globskipdots` on by default (2026-10-10)
- https://packages.debian.org/trixie/bash and https://packages.ubuntu.com/resolute/bash - Debian 13 bash 5.2.37, Ubuntu 26.04 bash 5.3 (2026-10-10)
- https://pubs.opengroup.org/onlinepubs/9799919799/utilities/V3_chap02.html and .../utilities/xargs.html - `set -o pipefail`, `xargs -0`/`-r` in Issue 8 (2026-10-10)
- https://curl.se/docs/manpage.html - `--retry-all-errors` 7.71.0, `--fail-with-body` 7.76.0 (2026-10-10)
- https://www.openssh.org/txt/release-9.0 - scp uses SFTP by default, 2022-04-08 (2026-10-10)
- https://lists.gnu.org/archive/html/info-gnu/2022-09/msg00001.html - grep 3.8: egrep/fgrep warn as obsolescent (2026-10-10)
- https://support.microsoft.com/help/5065506 - PowerShell 2.0 removed from Windows 11 24H2 / Server 2025 (2026-10-10)
- Still unverified (leads only): macOS 26 `/bin/bash` version, first dash release with `pipefail`, RHEL 8 perl 5.26.
