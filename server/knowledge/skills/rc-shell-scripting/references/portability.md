# Portability reference (as of 2026-10)

Items without a source in SKILL.md "Sources" are from long-standing tool behaviour; re-check with `man` on the target before relying on an edge case.

## GNU (Linux) vs BSD (macOS, FreeBSD) utilities

| Task | GNU | BSD / macOS | Portable choice |
|---|---|---|---|
| In-place edit | `sed -i 's/a/b/' f` | `sed -i '' 's/a/b/' f` | `sed -i.bak 's/a/b/' f && rm f.bak`, or `perl -pi -e 's/a/b/' f` |
| Extended regex | `sed -E` (also `-r`) | `sed -E` | `sed -E` |
| Newline in replacement | `sed 's/,/\n/g'` | literal backslash-newline needed | `tr ',' '\n'` or awk |
| Date from string | `date -d '2026-10-09' +%s` | `date -j -f '%Y-%m-%d' '2026-10-09' +%s` | `perl`/`python3`, or branch on `date --version` |
| Relative date | `date -d '1 day ago'` | `date -v-1d` | same |
| Epoch now | `date +%s` | `date +%s` | `date +%s` (or `$EPOCHSECONDS`, bash 5) |
| ISO 8601 UTC | `date -u +%FT%TZ` | `date -u +%FT%TZ` | works on both |
| File size / mtime | `stat -c '%s %Y' f` | `stat -f '%z %m' f` | `wc -c < f`; `find f -prune -newer ref` for comparisons |
| Canonical path | `readlink -f`, `realpath` | `realpath` (macOS 13+), `readlink -f` (macOS 12.3+) (unverified versions) | `cd -P -- "$(dirname -- "$p")" && pwd -P` |
| PCRE grep | `grep -P` | not available | `grep -E`, or `perl -ne` |
| Recursive grep | `grep -r` | `grep -r` | fine; prefer `rg` if installed, never require it |
| find formatting | `find -printf '%s %p\n'` | not available | `find ... -exec stat ... {} +` or `-print0` + loop |
| find null output | `-print0` | `-print0` | POSIX since Issue 8 |
| xargs no-run-if-empty | `xargs -r` | default behaviour (`-r` accepted on newer BSD, unverified) | `-r` is POSIX since Issue 8; else `find ... -exec cmd {} +` |
| xargs parallel | `xargs -P N` | `xargs -P N` | ok |
| Version sort | `sort -V` | supported on current macOS (unverified on old) | avoid or fall back to `sort -t. -k1,1n -k2,2n -k3,3n` |
| Unique stable sort | `sort -u` | `sort -u` | ok |
| Base64 one line | `base64 -w0` | `base64` (no wrap), `-b 0` | `base64 \| tr -d '\n'` |
| Decode base64 | `base64 -d` | `base64 -d` (newer) / `-D` (old) | `openssl base64 -d -A` |
| mktemp | `mktemp`, `mktemp -d`, `--suffix` | `mktemp` needs template on old macOS; no `--suffix` | `mktemp "${TMPDIR:-/tmp}/name.XXXXXX"` |
| Sequence | `seq 1 10` | `seq` (macOS has it) | `i=1; while [ $i -le 10 ]; do ...` in sh |
| Hashing | `sha256sum` | `shasum -a 256` (macOS 14+ also ships `sha256sum`, unverified) | `openssl dgst -sha256` |
| CPU count | `nproc` | `sysctl -n hw.ncpu` | `getconf _NPROCESSORS_ONLN` |
| Timeout | `timeout 10 cmd` (coreutils) | not on stock macOS (`gtimeout` via Homebrew) | POSIX Issue 8 adds `timeout`; check availability |
| Locking | `flock` (util-linux) | none | `mkdir "$lockdir"` as an atomic lock + `trap` removal |
| awk | gawk or mawk (Debian default mawk) | BSD awk (one-true-awk) | POSIX awk only: no `gensub`, `asort`, `systime`, `-i inplace` |
| tar | GNU tar | bsdtar (libarchive) | common flags `-czf`/`-xzf`; avoid `--transform`, `--wildcards` |
| echo | builtin varies | builtin varies | `printf` |
| ps | `ps -eo pid,cmd` | `ps -axo pid,command` | `pgrep -f` (both) |

Detect GNU: `if sed --version >/dev/null 2>&1; then gnu=1; fi`. On macOS, Homebrew's coreutils/gnu-sed give `g`-prefixed tools (`gsed`, `gdate`).

## Shell feature floor

| Feature | POSIX sh | dash | busybox ash | bash 3.2 (macOS) | bash 4.4+ | zsh |
|---|---|---|---|---|---|---|
| `[[ ]]` | no | no | yes (partial) | yes | yes | yes |
| Indexed arrays | no | no | no | yes | yes | yes (1-based) |
| Associative arrays | no | no | no | no | yes (4.0) | yes |
| `local` | no (but common) | yes | yes | yes | yes | yes |
| `$'...'` | Issue 8 | newer versions (unverified) | yes | yes | yes | yes |
| `pipefail` | Issue 8 | upstream since 2024-04 (0.5.13?, unverified) | yes | yes | yes | yes |
| `read -d` | Issue 8 | check | check | yes | yes | (`read -d`) yes |
| `mapfile` | no | no | no | no | yes | no (use `${(f)"$(<f)"}`) |
| `${v,,}` `${v^^}` | no | no | no | no | yes | `${(L)v}` `${(U)v}` |
| `${v@Q}` | no | no | no | no | yes | `${(q)v}` |
| `wait -n` | no | no | no | no | yes (4.3) | no |
| `inherit_errexit` | n/a | n/a | n/a | no | yes | n/a |
| Process substitution `<( )` | no | no | no | yes | yes | yes |
| Unquoted `$v` splits | yes | yes | yes | yes | yes | **no** |

## Windows PowerShell 5.1 vs PowerShell 7.x

| Area | 5.1 | 7.x |
|---|---|---|
| Runtime / platforms | .NET Framework 4.x, Windows only, executable `powershell.exe` | .NET (7.6 = .NET 10), Windows/Linux/macOS, `pwsh` |
| Pipeline chain `&&` `\|\|` | no | yes (7.0) |
| Ternary `a ? b : c`, `??`, `??=`, `?.` | no | yes (7.0/7.1) |
| `ForEach-Object -Parallel` | no (use `Start-Job`/runspaces) | yes (7.0) |
| Default file encoding | mixed per cmdlet (often UTF-16 LE for `>`/`Out-File`, ANSI for `Set-Content`) | UTF-8 without BOM everywhere |
| Native exit code as error | no | `$PSNativeCommandUseErrorActionPreference` (7.4 mainstream) |
| Native argument passing | legacy quoting bugs | `$PSNativeCommandArgumentPassing` (Windows default mode keeps legacy for some exes) |
| `ConvertFrom-Json -AsHashtable`, `-Depth` | no / limited | yes |
| `Invoke-RestMethod` retries, `-SkipHttpErrorCheck` | no | `-MaximumRetryCount`, `-RetryIntervalSec`, `-SkipHttpErrorCheck` |
| Remoting | WinRM | WinRM (Windows) and SSH (all platforms) |
| Package management | PowerShellGet 1.x/2.x `Install-Module` | PSResourceGet `Install-PSResource` in-box (7.4+) |
| Windows-only modules | in-box (e.g. ActiveDirectory via RSAT) | most load natively; others through the Windows compatibility layer (`Import-Module -UseWindowsPowerShell`) |
| Support | Windows lifecycle, maintenance only | 7.6 LTS to 2028-11-14 |

Write for both: `#Requires -Version 5.1`, avoid the 7-only syntax above, pass `-Encoding utf8` explicitly, test with Pester 6 on both hosts.
Detect: `$PSVersionTable.PSEdition` (`Desktop` = 5.1, `Core` = 7.x).
