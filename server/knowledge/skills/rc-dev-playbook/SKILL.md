---
name: rc-dev-playbook
kind: process
description: How to approach any software task like a senior engineer - understand first, read existing code, pick the right rc-* skills for the project type, privacy-first and minimal-dependency defaults, root-cause debugging, production-quality definition of done, honest verification. Use at the start of any build, fix, refactor, review or deploy task.
---
# Senior engineering playbook (process, not facts)

> This skill is about judgment and workflow. The facts (versions, APIs, deadlines) live in the topic skills it points to; those are dated, so confirm anything volatile on the primary source before relying on it.

## Do this

### 1. Understand before you build
- Restate the goal, the user, and what "done" looks like in one or two sentences. If a missing answer would change the design (platform, scale, data sensitivity, deadline, budget), ask one precise question; otherwise pick the sensible default, say so, and continue.
- Find out what already exists: the repo layout, the build and test commands, CI config, CLAUDE.md/README/contributing notes, existing conventions, and recent history (`git log` on the files you will touch). Match the codebase's style, naming and comment density instead of imposing yours.
- Separate "demo" from "production" explicitly. If nobody said demo, build for production: error handling, validation, security, tests, accessibility, logging, deploy and rollback.
- Search before you write: an existing helper, a standard-library function, a platform feature, or an already-installed dependency beats new code. Reuse, then stdlib, then platform, then installed deps, then new code.

### 2. Detect the project type and load the right skills
Look at the files, then read the matching skill(s) before writing code (they are short; read the "What changed" and "Common mistakes" parts first).

| You see | Read |
|---|---|
| `*.html`, `*.css`, `*.jsx/tsx`, `*.vue`, `*.svelte`, `*.astro`, Tailwind/Vite config | rc-web-frontend, rc-ui-ux-design, rc-web-security-perf |
| `package.json`, `tsconfig.json`, `*.ts`, `eslint.config.*` | rc-javascript-typescript (+ rc-node-backend for servers) |
| `composer.json`, `*.php`, WordPress theme/plugin | rc-php (+ rc-web-security-perf) |
| `pyproject.toml`, `*.py`, `uv.lock` | rc-python |
| `go.mod`, `*.go` | rc-go |
| `pom.xml`, `build.gradle(.kts)` with Java/Spring | rc-java |
| `AndroidManifest.xml`, `build.gradle.kts` with `com.android.*`, Compose | rc-android (+ rc-ui-ux-design) |
| `*.xcodeproj`, `Package.swift`, `*.swift`, `Info.plist`, `project.yml` | rc-ios (+ rc-ui-ux-design) |
| An API, auth flow, database schema, queue, Dockerfile, deploy pipeline | rc-web-backend (+ rc-web-security-perf) |
| Anything user-facing | rc-ui-ux-design |
| Anything that handles credentials, user data, uploads, payments, or the network | rc-web-security-perf |

If the stack is not covered by a skill, say so, fetch the primary documentation for the exact version in use, and mark what you could not verify instead of guessing.

### 3. Defaults that protect users
- Privacy first: collect only what the feature needs, no analytics or ad trackers, no third-party SDKs without a stated reason, minimal permissions requested at the moment of use with a plain-language reason, keep data on the device or in the user's own infrastructure when possible, give a way to export and delete.
- Least privilege everywhere: scoped tokens, read-only database users for read paths, non-root containers, per-feature permissions.
- Secure by default: validate input at trust boundaries, parameterize queries, escape output for its context, hash passwords with a memory-hard algorithm, never put secrets in code, logs, URLs, client bundles or version control.
- Fewer dependencies: each one is code you did not write and must keep patched. Add one only when it replaces a lot of risky code you would otherwise write, is maintained, and its license fits. Say in the PR why it earned a place.

### 4. Design with judgment
- Choose the simplest architecture that meets today's requirements and leaves room to grow: a modular monolith before microservices, a managed service before self-hosting, a boring proven tool before a fashionable one. Write down the alternatives you rejected and why (two lines is enough).
- Native or cross-platform is a decision, not a default: native when the app depends on platform UX, performance, background behavior or newest APIs; cross-platform when one team must ship several platforms with mostly shared UI and modest native needs.
- Design the failure paths first: timeouts, retries with backoff, idempotency, partial failure, bad input, empty states, offline, slow networks, concurrent edits.
- Keep backwards compatibility when others depend on the interface (public API, database schema, file formats): add, deprecate, migrate; do not break in place. Use expand/contract migrations for data.
- Refactor when the existing structure fights the change and tests can protect you; replace only when the code is unsalvageable or the platform is gone. Never mix a refactor and a behavior change in one commit.

### 5. Implement in small, verifiable steps
- Make the smallest change that solves the problem and is easy to review. Keep unrelated cleanup out.
- Write or update tests with the change: the happy path, the edge cases you identified, and one test that fails without the fix. Assert on behavior, not on implementation details; never write a test just to make it green.
- Run the real checks the project uses (build, tests, linter, type check, formatter). If you could not run something, say exactly that.
- Commit in logical units with messages that explain why.

### 6. Debug by finding the root cause
1. Reproduce reliably and write down the exact failing input, version and environment.
2. Read the actual error and stack trace; read the code on the path; check recent changes (`git bisect` when the regression is recent).
3. Form one hypothesis, predict what you would observe if it were true, and test that prediction (a log, a breakpoint, a minimal reproduction). Change one thing at a time.
4. Fix the cause, not the symptom: no sleeps, blanket try/catch, retries that hide a bug, or special-casing the failing test.
5. After two failed attempts, stop guessing: re-read the code from the entry point, question an assumption you have not verified, and consider asking for a second opinion.
6. Add a regression test and note what you learned where the next person will find it.

### 7. Be honest about what you verified
- Say what you ran and what it showed. Say what you did not run. "It should work" is not a result.
- Never fabricate an API, flag, version, or test result. If unfamiliar or recently changed, read the primary documentation for the version in use, and prefer the project's own pinned versions over the newest ones.
- Mark guesses as guesses and unverified facts as unverified. State limits and known gaps at the end of the work, not buried.

### 8. Leave it maintainable
- Write documentation people use: how to run, test, configure and deploy; why non-obvious decisions were made; known limitations. Update the docs you touched.
- Leave the project's notes file (CLAUDE.md or equivalent) with commands, gotchas and conventions future sessions need.
- Remove dead code you created; leave the code better than you found it only where you are already working.

## Common mistakes
- Starting to code before reading the existing code and conventions; rewriting what a helper already does.
- Delivering a demo when production was needed (no error handling, no validation, hard-coded secrets, no tests, no deploy path).
- Adding a dependency, SDK, permission or analytics call "just in case".
- Over-engineering: abstractions with one implementation, config nobody sets, microservices for a team of two, generic frameworks for one use case.
- Choosing the newest or most popular tool without checking it fits the project's constraints and versions.
- "Fixing" a failing test by weakening it; swallowing exceptions; retrying until it passes.
- Upgrading frameworks or languages the task did not ask to upgrade.
- Claiming something was tested, researched or secure without having done it.
- Copying an example without understanding why it works (especially security code, crypto and auth).

## Before you ship
- [ ] The requirement is met and the edge cases and failure paths are handled.
- [ ] Tests exist, pass, and would fail without the change; the project's lint, type check and build are green.
- [ ] No secrets, tokens or personal data in code, logs, fixtures or history; inputs validated; permissions minimal.
- [ ] No new dependency, tracker or permission without a written reason.
- [ ] Accessible and usable on the target devices and sizes (see rc-ui-ux-design); loading, empty and error states exist.
- [ ] Performance checked where it matters (startup, first render, hot queries, memory, battery).
- [ ] Deploy and rollback steps are known and documented; migrations are reversible or backwards compatible; backups verified for data changes.
- [ ] Docs and the project notes are updated; known limitations and anything unverified are stated plainly.
