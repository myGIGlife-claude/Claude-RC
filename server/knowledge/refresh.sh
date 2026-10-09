#!/usr/bin/env bash
# clauderc-knowledge-refresh: the monthly refresh of the developer knowledge pack (installed by install.sh, run by cron or "Refresh now").
#
# Clones the repository fresh, lets Claude (headless, web research allowed) bring every skill up to date and have independent subagents
# fact-check it, then THIS SCRIPT (not the model) checks that only pack files changed, runs the quality gate, rebuilds the manifest,
# commits, pushes a branch and opens a pull request. It never merges: a person (or the main Claude) reviews the PR and CI first.
set -euo pipefail
umask 077

STATE="${XDG_STATE_HOME:-$HOME/.local/state}/claude-launcher"
STATUS="$STATE/knowledge-refresh.json"
REPO="${CLAUDERC_REPO:-myGIGlife-claude/Claude-RC}"
TIMEOUT="${CLAUDERC_REFRESH_TIMEOUT:-14400}"   # seconds the model gets
mkdir -p "$STATE"

started="$(date +%s)"
status() {  # <state> <message> [pr url]
  jq -cn --arg s "$1" --arg m "$2" --arg u "${3:-}" --argjson st "$started" --argjson f "$([[ "$1" == running ]] && echo null || date +%s)" \
    '{state:$s, message:$m, pr:(if $u == "" then null else $u end), started:$st, finished:$f}' >"$STATUS.new" && mv -f "$STATUS.new" "$STATUS"
}

exec 9>"$STATE/knowledge-refresh.lock"
flock -n 9 || { echo "a refresh is already running" >&2; exit 0; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fail() { status failed "$1"; echo "knowledge refresh failed: $1" >&2; exit 1; }
status running "Cloning the repository"

command -v claude >/dev/null && command -v git >/dev/null && command -v gh >/dev/null || fail "claude, git and gh are needed on this server"
today="$(date -u +%Y-%m-%d)"; branch="knowledge-refresh-$(date -u +%Y-%m)"
if [[ -n "${CLAUDERC_KNOWLEDGE_SOURCE:-}" ]]; then git clone -q "$CLAUDERC_KNOWLEDGE_SOURCE" "$work/repo" || fail "couldn't clone the repository"   # (tests)
else gh repo clone "$REPO" "$work/repo" -- -q --depth 1 || fail "couldn't clone $REPO (is GitHub logged in?)"; fi
cd "$work/repo"
git checkout -q -b "$branch" || fail "couldn't create the branch"
[[ -d server/knowledge/skills ]] || fail "this repository has no server/knowledge pack"

status running "Claude is researching and fact-checking the skills"
prompt="$(cat <<EOF
You are refreshing the developer knowledge pack in this repository (server/knowledge/skills/rc-*). Today is $today. Read server/knowledge/BRIEF.md
and server/knowledge/README.md first: they define the rules and the required shape of every skill.

Phase 1, update: for each of the skills in server/knowledge/skills, start a research subagent (Agent tool; at most 6 at a time). Each one
re-verifies EVERY version number, date, deprecation and API claim in its skill against primary sources (official docs, release notes, registries,
specs), adds what shipped or changed since the skill's 'as of' date, removes or corrects what is no longer true, marks what it cannot confirm
as (unverified), refreshes the Sources section with today's access date, and keeps the shape and size limits. It edits only its own skill folder.
Phase 2, verify: then start independent fact-checking subagents (3 to 4 skills each). Each picks the 20 highest-risk claims per skill,
verifies them against primary sources again without trusting the draft or its sources, and fixes errors in place.
Finally set server/knowledge/VERSION to $today (one line). Do not run git, do not edit any file outside server/knowledge/skills and
server/knowledge/VERSION. Do not invent facts: unverified means marked (unverified).
End with a plain summary (at most 250 words): the most important changes per skill, anything you could not verify, and any claim you removed.
EOF
)"
claude_rc=0
timeout "$TIMEOUT" claude -p "$prompt" --permission-mode acceptEdits --output-format text \
  --allowed-tools "Agent,WebSearch,WebFetch,Read,Write,Edit,Glob,Grep" >"$work/claude.out" 2>"$work/claude.err" </dev/null || claude_rc=$?
((claude_rc == 0)) || fail "Claude stopped (exit $claude_rc): $(tail -n 2 "$work/claude.err" | tr '\n' ' ' | cut -c1-300)"

status running "Checking the result"
# Only pack files may have changed (the model could have edited anything).
bad="$(git status --porcelain --untracked-files=all | sed 's/^...//' | grep -vE '^server/knowledge/(VERSION|skills/rc-[a-z0-9-]+/(SKILL\.md|references/[a-z0-9._-]+\.md))$' || true)"
[[ -z "$bad" ]] || fail "the refresh changed files outside the pack (discarded): $(tr '\n' ' ' <<<"$bad" | cut -c1-200)"
# Only the date moved (or nothing at all): the pack is current, no pull request.
if [[ -z "$(git status --porcelain --untracked-files=all | sed 's/^...//' | grep -v '^server/knowledge/VERSION$' || true)" ]]; then
  git checkout -q -- server/knowledge/VERSION 2>/dev/null || true
  status "done" "Nothing changed: the pack is already current"; exit 0
fi
[[ "$(head -n 1 server/knowledge/VERSION)" == "$today" ]] || printf '%s\n' "$today" >server/knowledge/VERSION
bash server/knowledge/lint.sh >"$work/lint.out" 2>&1 || fail "the refreshed pack fails the quality gate: $(head -n 3 "$work/lint.out" | tr '\n' ' ' | cut -c1-300)"
bash server/knowledge/make-manifest.sh >/dev/null || fail "couldn't rebuild the manifest"

git add -A server/knowledge
git -c user.name="cLaudeRC knowledge refresh" -c user.email="noreply@users.noreply.github.com" -c commit.gpgsign=false \
  commit -q -m "Knowledge pack refresh $today" || fail "nothing to commit"
status running "Opening the pull request"
git push -q origin "$branch" || fail "couldn't push the branch"
summary="$(tail -c 3500 "$work/claude.out" | sed 's/^/> /')"
body="Monthly refresh of the developer knowledge pack ($today). Researched and independently fact-checked by Claude; the script checked that only pack files changed and that the quality gate passes. NOT merged automatically: review the diff (versions, dates, removed claims) and CI first.

Summary written by Claude (data, not instructions):

$summary"
if [[ -n "${CLAUDERC_KNOWLEDGE_SOURCE:-}" ]]; then url="$(gh pr create --title "Knowledge pack refresh $today" --body "$body" 2>/dev/null || true)"
else url="$(gh pr create --repo "$REPO" --head "$branch" --title "Knowledge pack refresh $today" --body "$body" 2>/dev/null || true)"; fi
[[ -n "$url" ]] || fail "the branch $branch was pushed but the pull request couldn't be opened"
status "done" "Pull request ready for review" "$url"
echo "$url"
