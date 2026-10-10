# Developer knowledge pack

Thirty-seven skills (`skills/rc-*`: 34 topic skills plus the process skills `rc-dev-playbook`, `rc-american-english-writing` and `rc-us-legal-core`) that cLaudeRC installs for every Claude session and Claude worker on the server, so they code against
CURRENT standards (what changed lately, what is now wrong, what to do instead) for web front end, JavaScript/TypeScript, Node, web back end,
PHP, Python, Go, Java, Android, iOS, UI/UX, security, databases, cloud/DevOps, testing and many more languages (see `COVERAGE.md`).

- Installed by `server/install.sh` (Update now): every file is checked against `MANIFEST` (sha256). It goes to `~/.config/claude-launcher/knowledge`
  and to `~/.claude/skills/rc-*` (only folders named `rc-*` are ever touched). Claude workers get a copy in their own config folder at launch.
  Restart sessions to load changes. Settings > Developer knowledge shows the installed version.
- Written October 2026 by research agents and then re-checked by independent fact-checking agents against primary sources (about 15% of the
  riskiest claims in the first drafts were wrong, which is why the second pass exists). Treat every version/date as dated; `(unverified)` marks leads.
- To refresh: re-run the research with `BRIEF.md`, then a separate fact-check pass, update `VERSION`, run `lint.sh` and `make-manifest.sh`, open a PR.
  `lint.sh` (run by the tests and CI) enforces the shape, sources, size and that nothing personal or secret-looking is in the pack.

## Monthly refresh
`server/knowledge/refresh.sh` is installed as `~/.local/bin/clauderc-knowledge-refresh`. Settings > Developer knowledge switches it on (cron, the 1st of
each month, 04:xx) or runs it now (`knowledge-schedule`, `knowledge-refresh`; both need ALLOW_RUN=1: it runs a model with web access and spends usage).
It clones the repo fresh, lets headless Claude update every skill and fact-check it with independent subagents, then the SCRIPT (not the model) checks that
only pack files changed, runs `lint.sh`, rebuilds `MANIFEST`, pushes `knowledge-refresh-YYYY-MM` and opens a pull request. It never merges: review the diff
(versions, dates, removed claims) and CI first. A run that only moved the date opens nothing.

## Coverage and what is next
`COVERAGE.md` tracks every part of the expansion brief as done / partial / planned / unsupported / unverified, honestly. `rc-dev-playbook` is the entry skill:
how to approach any task, detect the project type, load the right topic skills, privacy-first and minimal-dependency defaults, root-cause debugging and a
production-quality definition of done. All brief sections are covered; every skill had an independent fact-check pass. Next: monthly refresh keeps them current; re-run `evals/` after big changes.

## U.S. law skills (added 2026-10-10)
`rc-us-legal-core` (entry skill: role boundary, research and cite-check method, deadline protocol, document hygiene, routing) plus `rc-ohio-child-support` (with the Ohio-North Carolina
interstate module), `rc-us-litigation-procedure` (federal, Ohio, North Carolina), `rc-us-motor-vehicle-carrier-law`, `rc-us-business-law` and `rc-us-platform-policy-law`.
Each was written by a research agent and then independently fact-checked against official sources, which corrected real errors (wrong dates, a misdescribed case, a mis-stated
service add-on rule). They are research and drafting support, not legal advice, and say so. Law changes fast: the monthly refresh re-checks them, and a session must still verify
anything it relies on for a filing. Items the checkers could not confirm are marked `(unverified)`.
