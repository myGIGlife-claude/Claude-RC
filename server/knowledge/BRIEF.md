# Developer knowledge pack: how each skill is written

Each topic is one Claude skill: `server/knowledge/skills/rc-<topic>/SKILL.md` (+ optional `references/*.md`). RC installs the pack for every
session and worker so they code against CURRENT standards instead of stale training data. Written October 2026; every fact is dated.

## Rules for the author (a research agent)
- Use the browser tools (WebSearch/WebFetch). Primary sources first: official docs, release notes, specs (MDN, WHATWG, W3C, TC39, nodejs.org,
  python.org, go.dev, openjdk.org, developer.android.com, developer.apple.com, language/framework changelogs, OWASP, CVE/advisory pages).
  Blogs only to find leads; never the sole source of a version number, date or API claim.
- Verify every version number, release/EOL date and API name on a primary source. If you cannot verify, write "(unverified)". Do not guess.
- Your training data is out of date on purpose: assume things changed since 2025 and go look. Spend your effort on WHAT CHANGED and what is
  now WRONG in common advice, not on timeless basics.
- Paraphrase. No long quotes (15 words max, in quotation marks). No copied code blocks from docs longer than 15 lines; write your own minimal examples.
- No personal values (names, hosts, IPs, emails, keys). Public repo.
- Be prescriptive and compact: bullets and short code, no prose essays. SKILL.md at most ~450 lines; put long tables/details in `references/<name>.md`.

## Required shape of SKILL.md
```
---
name: rc-<topic>
description: <one or two sentences: WHAT it covers and WHEN to use it, with the trigger words a session would have (file types, frameworks, tasks)>
---
# <Title>  (as of 2026-10)
## Currency check        current stable versions, LTS lines, support/EOL dates, what shipped in the last ~12 months (dated)
## What changed / stop doing   deprecated, removed or outdated advice and the modern replacement (a table: old -> new -> since version)
## Do this               current best practices with minimal examples (project layout, config, idioms, APIs)
## Security              the concrete risks of this stack today and the fix (OWASP-style), secure defaults
## Performance & quality   measurable targets and tools (and what to measure)
## Testing & tooling     the current default toolchain, linters, formatters, CI
## Common mistakes in AI-written code   hallucinated or outdated APIs, wrong defaults, patterns that look right but are wrong now
## Before you ship       a short checklist
## Sources               URL + what it supports + date accessed (YYYY-MM-DD)
```

## Topics (12)
rc-web-frontend, rc-javascript-typescript, rc-node-backend, rc-web-backend, rc-php, rc-python, rc-go, rc-java, rc-android, rc-ios, rc-ui-ux-design, rc-web-security-perf
