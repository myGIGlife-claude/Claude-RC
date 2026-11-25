---
name: rc-audit-and-verify
kind: process
description: "How to audit code for security, review changes and prove work is done: evidence-gated findings, three verdicts, severity anchors, false-positive controls, sandboxed validation, independent verification, pipefail and target-acceptance checks. Load for security review, code review, 'is it done', test or CI claims."
---
# Auditing, reviewing and verifying (process, not facts)

> Distilled 2026-11 from MIT-licensed public repos (cloudflare/security-audit-skill, affaan-m/ECC, alirezarezvani/claude-skills, anthropics/skills skill-creator), rewritten in our own words. Jonathan's CLAUDE.md wins on conflict. Pairs with `rc-security-engineering` (facts and standards) and `rc-testing-debugging`.

## Do this

### Security and code audit
1. **Mode first.** A question or a focused review gets guidance in chat and no files. A full audit (explicit request) runs the phases below and writes a report. If unclear, ask one question.
2. **Admission gate.** A finding needs: the lower-trust actor, the input or action it can supply, the control that should stop it, the boundary crossed, and the observed result. A missing best practice, or something an actor can only do to their own data, is not a finding.
3. **Three verdicts.** confirmed (full source trace plus a bounded observed result; only these get a severity), needs_validation (name the one exact unresolved fact; no severity), rejected (kept in the notes so the same claim is not raised again unless evidence changes).
4. **Severity is capped by demonstrated impact.** Critical: unauthenticated code execution, whole data store, or any account takeover. High: an actor fully defeats an explicit control for a consequential action. Medium: real boundary violation, narrow blast radius. If you cannot state concrete damage, lower it. Score likelihood and impact separately.
5. **Defense in depth is a note, not a bug:** if layer A really blocks the attack, a missing layer B is hardening.
6. **Hunt by coverage, not by checklist.** Build a ledger of surface x boundary x subsystem x attack class; "covered" means named files and checks, not "auth reviewed". Compare sibling, legacy, retry, batch and migration paths that reach the same effect, and test equivalence of controls, not just presence. Check parser and policy handoffs (canonicalization, units, encodings) and the sad paths (errors, timeouts must fail closed).
7. **Fixed-pattern severities (starting points):** hardcoded secret, shell with user input, SQL built by concatenation, plaintext password comparison are critical; unsanitized HTML from user input, fetching a user-supplied URL without an allowlist (SSRF), no rate limit on auth are high; secrets in logs medium. Not findings: `.env.example` values, marked test credentials, intentionally public keys, checksums using MD5/SHA.
8. **Validation safely.** Run target code only in a sandbox: no external network, empty allowlisted environment, read-only target, writes to scratch only, hard CPU, memory, process and time limits. Missing any of those: do not run it; record needs_validation with that blocker. Dummy data and local fixtures only; never probe deployed or shared systems; stop at the minimum observed effect.
9. **Independent verifier.** A different agent re-reads every cited line, rebuilds the strongest control on the path and tries to knock the finding down. Hunter output is structured (one JSON object, no prose); malformed output is re-run, never repaired by hand. A run is complete only when a separate final pass accepts no further work. If budget cannot cover every candidate, say the run is incomplete and keep unvalidated items out of the findings.
10. **Fix guidance:** state the invariant, then the smallest change at the last trusted decision point, plus a regression test. An audit does not edit the target.

### Code review
11. **Pre-report gate (every finding):** can you cite the exact line, name the trigger (input, state, outcome), have you read callers, imports and tests, is the severity defensible? Report only what you are about 80% sure of. High and critical also need the failure scenario and why existing guards miss it. A review with zero findings is valid; invented nits are the main failure.
12. **Skip list:** "add error handling" where the caller or framework handles it; "missing validation" on internal functions whose callers validate (trace one caller); magic numbers; long switch or config code; N+1 on fixed-size loops; a "missing await" on intentional fire-and-forget; weak randomness in non-crypto code. Tie-breaker: would a senior engineer on this team change it in review?
13. **Sideways sweep:** when you find a bad pattern, grep for it at every other call site. Data loss, trust-boundary bypass and uncapped spend are critical by default. Check that tests, timeouts and snapshots were not weakened to get green. A flaky-test fix needs before and after failure rates.

### Verifying "done"
14. **Fresh evidence only.** Run the real command now and read all of its output. "Should", "probably" and "looks right" are red flags. Check the version-control diff of agent work, not the agent's report.
15. **Do not hide failures.** Use `set -o pipefail` and read the whole stream (a `| tail` can mask the exit status; a closed pipe can too). Stop the checks at the first build failure.
16. **Local is not target.** Local PASS is not staging or production acceptance: report each as verified, failed, not performed or not applicable, with the next step.
17. **Evidence table.** For each claimed guarantee: test name, command, actual result. Never write a PASS you did not see. Coverage is necessary, not sufficient: for auth and money paths run mutation testing (aim 85% or more) or at least invert one condition and expect red.
18. **Skill and prompt changes** are compared against a baseline (no skill or the old version) on realistic multi-step prompts; checks that pass either way are dropped.

## Common mistakes

- Reporting a checklist deviation as a vulnerability.
- Severity higher than what was demonstrated.
- Live-testing a shared system when local evidence would do.
- Re-reporting a rejected claim, or carrying an old finding over after the code changed.
- Verifying with the same agent that produced the finding.
- Claiming CI is green without reading the run.

## Before you ship

- [ ] Every finding has the five admission facts and a verdict; no severity on needs_validation.
- [ ] Each confirmed finding was re-checked by someone other than its author.
- [ ] Coverage gaps and anything not validated are stated plainly.
- [ ] Verification output was read in full; local and target results reported separately.

## Sources

Read 2026-11-25 (MIT): cloudflare/security-audit-skill (phases, verdicts, validation, coverage ledger), affaan-m/ECC (review gate, skip list, verification loop, TDD evidence), alirezarezvani/claude-skills (code-review bands, secops exit codes, spec-to-test), anthropics/skills (skill-creator eval loop). Install scripts, hooks and telemetry were not adopted.
