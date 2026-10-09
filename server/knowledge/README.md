# Developer knowledge pack

Twelve skills (`skills/rc-*`) that cLaudeRC installs for every Claude session and Claude worker on the server, so they code against
CURRENT standards (what changed lately, what is now wrong, what to do instead) for web front end, JavaScript/TypeScript, Node, web back end,
PHP, Python, Go, Java, Android, iOS, UI/UX and web security/performance.

- Installed by `server/install.sh` (Update now): every file is checked against `MANIFEST` (sha256). It goes to `~/.config/claude-launcher/knowledge`
  and to `~/.claude/skills/rc-*` (only folders named `rc-*` are ever touched). Claude workers get a copy in their own config folder at launch.
  Restart sessions to load changes. Settings > Developer knowledge shows the installed version.
- Written October 2026 by research agents and then re-checked by independent fact-checking agents against primary sources (about 15% of the
  riskiest claims in the first drafts were wrong, which is why the second pass exists). Treat every version/date as dated; `(unverified)` marks leads.
- To refresh: re-run the research with `BRIEF.md`, then a separate fact-check pass, update `VERSION`, run `lint.sh` and `make-manifest.sh`, open a PR.
  `lint.sh` (run by the tests and CI) enforces the shape, sources, size and that nothing personal or secret-looking is in the pack.
