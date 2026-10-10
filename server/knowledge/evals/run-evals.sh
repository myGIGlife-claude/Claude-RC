#!/usr/bin/env bash
# Does the knowledge pack change what a session writes? Each scenario in scenarios.jsonl is a realistic prompt plus OBJECTIVE checks
# (regexes the answer must match / must not match). Every prompt runs twice with headless Claude and no tools except Skill:
#   with    = a clean config with only this pack (skills + the always-on rule that tells sessions to load them), like a real session
#   without = a clean config with nothing: the baseline
# Usage: run-evals.sh [--repeat N] [--only id,id] [--model NAME] [--out DIR]
# "practice" = habits a good model often knows; "known" = recent facts the model usually already knows (a control); "postcutoff" = facts that
# appeared after the model's knowledge cutoff (only a pack, or a web search, can supply them: the real test). Results are indicative, not proof: models vary run to run (use --repeat 3 for a steadier number) and the checks are simple patterns.
set -uo pipefail
cd "$(dirname "$(readlink -f "$0")")" || exit 1
repeat=1 only="" model="${EVAL_MODEL:-sonnet}" out="${EVAL_OUT:-$(mktemp -d)}"
while (($#)); do
  case "$1" in
    --repeat) repeat="$2"; shift 2 ;;
    --only) only="$2"; shift 2 ;;
    --model) model="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    *) echo "usage: run-evals.sh [--repeat N] [--only id,id] [--model NAME] [--out DIR]" >&2; exit 2 ;;
  esac
done
command -v claude >/dev/null && command -v jq >/dev/null || { echo "claude and jq are needed" >&2; exit 1; }
mkdir -p "$out"; work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
# Two CLEAN Claude config folders (no hooks, plugins, memory or other skills of the owner's): "with" gets only this pack (skills + the always-on
# rule); "without" gets nothing. Both borrow the login (a copy of the credentials file, deleted at the end).
cred="${CLAUDE_CONFIG_DIR:-$HOME/.claude}/.credentials.json"
[[ -f "$cred" ]] || { echo "no Claude login found at $cred" >&2; exit 1; }
for m in with without; do
  mkdir -p "$work/cfg-$m"; cp "$cred" "$work/cfg-$m/.credentials.json"; chmod 600 "$work/cfg-$m/.credentials.json"
done
mkdir -p "$work/cfg-with/skills" "$work/cfg-with/rules"
cp -r ../skills/rc-* "$work/cfg-with/skills/" && cp ../rules/rc-knowledge.md "$work/cfg-with/rules/" || { echo "the pack (../skills, ../rules) is incomplete" >&2; exit 1; }
ask() {  # <mode> <prompt> -> answer on stdout
  local mode="$1" prompt="$2" tools="Skill"
  [[ "$mode" == with ]] || tools=""
  (cd "$work" && CLAUDE_CONFIG_DIR="$work/cfg-$mode" timeout 300 claude -p "$prompt" --model "$model" --output-format text --tools "$tools" </dev/null 2>/dev/null)
}
check() {  # <answer file> <scenario json> -> prints PASS or FAIL: reason
  local f="$1" j="$2" re
  # scope "code": judge only what is inside ``` fences (a mention like "skipped nodemon" in the prose is not a use); no fences = the whole answer
  if [[ "$(jq -r '.scope // "all"' <<<"$j")" == code ]] && grep -q '^```' "$f"; then awk '/^```/ {c = !c; next} c' "$f" >"$f.code"; f="$f.code"; fi
  while IFS= read -r re; do grep -Eiq -- "$re" "$f" || { echo "FAIL: missing /$re/"; return; }; done < <(jq -r '.must[]' <<<"$j")
  while IFS= read -r re; do ! grep -Eiq -- "$re" "$f" || { echo "FAIL: has /$re/"; return; }; done < <(jq -r '.must_not[]' <<<"$j")
  echo PASS
}
tw=0 to=0 n=0
declare -A cw co cn
printf '%-20s %-24s %-8s %-8s\n' scenario skill with without
while IFS= read -r j; do
  id="$(jq -r .id <<<"$j")"
  [[ -z "$only" || ",$only," == *",$id,"* ]] || continue
  pw=0 po=0
  for ((r = 1; r <= repeat; r++)); do
    for mode in with without; do
      ask "$mode" "$(jq -r .prompt <<<"$j")" >"$out/$id.$mode.$r.txt"
      res="$(check "$out/$id.$mode.$r.txt" "$j")"
      echo "$res" >"$out/$id.$mode.$r.result"
      [[ "$res" == PASS ]] && { [[ $mode == with ]] && pw=$((pw + 1)) || po=$((po + 1)); }
    done
  done
  printf '%-20s %-24s %-8s %-8s\n' "$id" "$(jq -r .skill <<<"$j")" "$pw/$repeat" "$po/$repeat"
  tw=$((tw + pw)); to=$((to + po)); n=$((n + repeat))
  cat="$(jq -r '.category // "practice"' <<<"$j")"; cw[$cat]=$((${cw[$cat]:-0} + pw)); co[$cat]=$((${co[$cat]:-0} + po)); cn[$cat]=$((${cn[$cat]:-0} + repeat))
done <scenarios.jsonl
echo
for cat in "${!cn[@]}"; do echo "  $cat: with ${cw[$cat]}/${cn[$cat]}, without ${co[$cat]}/${cn[$cat]}"; done
echo "with the pack: $tw/$n passed   without: $to/$n passed   (model: $model; answers and per-check results in $out)"
