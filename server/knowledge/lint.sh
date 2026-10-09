#!/usr/bin/env bash
# Quality gate for the knowledge pack (run by the tests and CI): every skill has valid frontmatter, the required sections, sources,
# a sane size, and nothing personal or secret-looking in it.
set -uo pipefail
cd "$(dirname "$(readlink -f "$0")")" || exit 1
bad=0
fail() { echo "knowledge lint: $1" >&2; bad=1; }
[[ -f VERSION ]] || fail "VERSION is missing"
n=0
for d in skills/rc-*/; do
  d="${d%/}"; name="${d#skills/}"; f="$d/SKILL.md"; n=$((n + 1))
  [[ -f "$f" ]] || { fail "$name: SKILL.md is missing"; continue; }
  [[ "$(sed -n 1p "$f")" == --- ]] || fail "$name: frontmatter must start on line 1"
  [[ "$(sed -n '2,6p' "$f" | grep -c "^name: $name$")" == 1 ]] || fail "$name: 'name:' must equal the folder name"
  desc="$(sed -n '2,8p' "$f" | grep '^description:' | head -n 1)"
  [[ -n "$desc" ]] || fail "$name: description is missing"
  ((${#desc} >= 60 && ${#desc} <= 700)) || fail "$name: description should be 60-700 characters (it is loaded into every session): ${#desc}"
  lines="$(wc -l <"$f")"
  ((lines <= 520)) || fail "$name: SKILL.md is $lines lines (max 520): move detail to references/"
  for h in "Currency check" "What changed" "Do this" "Security" "Common mistakes" "Before you ship" "Sources"; do
    grep -qi "^## .*$h" "$f" || fail "$name: missing section '$h'"
  done
  [[ "$(sed -n '/^## Sources/,$p' "$f" | grep -c 'https\?://')" -ge 5 ]] || fail "$name: Sources needs at least 5 URLs"
  grep -qE '20[0-9]{2}-[0-9]{2}' "$f" || fail "$name: no dates (every skill states 'as of')"
  [[ -s "$f" ]] || fail "$name: empty"
done
((n >= 1)) || fail "no skills found"
# nothing personal or secret-looking anywhere in the pack
if grep -rEn '(/home/[a-z0-9_-]+/|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}|\b([0-9]{1,3}\.){3}[0-9]{1,3}\b|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9]{20,})' skills VERSION 2>/dev/null |
  grep -vE '(127\.0\.0\.1|0\.0\.0\.0|192\.0\.2\.|198\.51\.100\.|203\.0\.113\.|169\.254\.169\.254|10\.0\.0\.|192\.168\.|example\.(com|org|net)|noreply@|user@example)' | head -5 | grep .; then
  fail "personal or secret-looking text found (see above)"
fi
((bad == 0)) && echo "knowledge lint: $n skills OK"
exit "$bad"
