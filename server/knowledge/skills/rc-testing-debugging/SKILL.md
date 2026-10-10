---
name: rc-testing-debugging
description: Cross-language test strategy and debugging method (Oct 2026). Use when writing or reviewing tests, fixing flaky tests, adding e2e (Playwright, Cypress), a11y (axe), load (k6, Gatling), contract (Pact), Testcontainers, mutation/property tests, SAST/DAST in CI, mobile UI tests (Espresso, Maestro, XCUITest), coverage gates, or when hunting a bug, leak, race or slowdown with debuggers, profilers, strace or tracing.
---
# Testing and debugging  (as of 2026-10)

> Cross-cutting strategy and the current tool landscape. Per-language test runners live in rc-javascript-typescript, rc-python,
> rc-go, rc-java, rc-android, rc-ios, rc-php: read those for runner config. Versions are dated; confirm before pinning.
> Full version table: `references/tool-versions.md`. Per-ecosystem debugger/profiler commands: `references/debugging-toolbox.md`.

## Currency check
- **Web e2e**: Playwright 1.64.0 (2026-10-07; ships monthly-ish). Since 1.57 it runs Chrome for Testing builds instead of Chromium;
  1.56 added test agents (planner/generator/healer); 1.62 `retryStrategy: 'isolated'`; 1.63 named test locks + `locator.visible()`;
  1.64 `--shuffle` and `locator.within()`. Cypress 16.0 (2026-09-01): Node 22/24/26 only, `cy.exec()` removed (use `cy.task()`), `Cypress.env()`
  removed (use `cy.env()` for secrets, `Cypress.expose()` for browser values), Electron browser deprecated. Cypress 15.0 was 2025-08-20.
- **WebDriver BiDi**: Firefox removed CDP (Firefox 141, 2025-07-22); Cypress drives Firefox 135+ over BiDi since 14.1 (2025-02-25);
  Puppeteer 23+ (2024-08) uses BiDi for Firefox by default (Chrome still CDP by default); Selenium is moving from CDP to BiDi. Selenium 4.51 (2026-10-09),
  WebdriverIO 10.0 (2026-10-05). The BiDi spec is still a W3C draft (exact maturity unverified).
- **Component/browser tests**: Vitest browser mode is stable since 4.0 (2025-10-22): providers are separate packages
  (`@vitest/browser-playwright`, `-webdriverio`, `-preview`), import from `vitest/browser`, `toMatchScreenshot` for visual diffs.
  Vitest 5.0 (2026-09-03): Node >= 22.12, `clearMocks` on by default, strict locators, unawaited assertions fail, trace view for
  browser mode, `vi.when()`, Temporal mocking. Jest 30 (30.5.2) still maintained. MSW 3.0 (2026-09-28).
- **A11y**: axe-core 4.14.0 (2026-10-05), `@axe-core/playwright` 4.13.0, Pa11y 10.0.0 (2026-08-28, Node 22.13+), Lighthouse 13.5.0
  (2026-09-18). Lighthouse CI last released 0.15.1 (2025-06-25): slow cadence, bundles Lighthouse 12.6.1, not 13.
- **Load**: k6 2.3.0 (2026-09-21); k6 1.0 was 2025-05-06, 2.0 (2026-05-11) removed `externally-controlled`, `k6 pause/scale`,
  FID from web vitals, Go module now `go.k6.io/k6/v2`. Gatling 3.16.0 (2026-09-29; Java/Kotlin/Scala and a JavaScript/TypeScript SDK).
  Locust 2.46.7 (2026-10-04). JMeter 5.6.3 is still the latest release (2024-01).
- **Contract/DB**: Pact JS 17 (17.0.0 2026-06-25, latest 17.1.4). Testcontainers Java 2.0 (2025-10-14; JUnit 4 removed, artifacts
  renamed `testcontainers-<module>`, classes moved to `org.testcontainers.<module>`; latest 2.0.5), Node 12.2.0, Python 4.15.0.
- **Mutation/property**: Stryker 10.0 (2026-08-14), PIT 1.30.0 (2026-08-27), mutmut 3.8.0; Hypothesis 6.168, fast-check 4.10.
- **Security in CI**: Semgrep 1.180 (2026-10-07; Opengrep 1.30 is the LGPL community fork), CodeQL bundle 2.27.2 (2026-10-07),
  ZAP 2.17.0 (2025-12-15, plus weekly builds), gitleaks 8.30.1, TruffleHog 3.99, OSV-Scanner 2.6.0.
- **Mobile**: Espresso 3.7.0, Maestro CLI 2.11.0 (2026-09-29, Apache-2.0), Appium 3 (3.0.0 2025-08-18, W3C protocol only,
  latest 3.8.0), Roborazzi 1.76, Paparazzi 2.0 alphas, swift-snapshot-testing 1.19.6. Swift Testing/XCUITest: see rc-ios.
- **Debugging/profiling news**: Python 3.14 `python -m pdb -p PID` remote attach + `python -m asyncio ps|pstree PID`; Python 3.15
  (2026-10-09) adds the `profiling.sampling` sampling profiler (`python -m profiling.sampling attach PID`) and frame pointers by
  default. Go 1.25 `runtime/trace.FlightRecorder` + `testing/synctest` GA; Go 1.27 `goroutineleak` pprof profile. async-profiler 4.5,
  Delve 1.27.2, py-spy 0.4.2, memray 1.20, Perfetto v58, mitmproxy 12.2. Agent-facing: `chrome-devtools-mcp` 1.10, `@playwright/mcp`.
- **Older versions** (legacy projects): Playwright < 1.57 uses Chromium builds (screenshot baselines can shift on upgrade); Vitest 3
  has browser mode as experimental with `@vitest/browser/context`; Cypress 13/14 on Node 18/20 still runs CDP for Chrome and BiDi
  only for Firefox 135+; Testcontainers Java 1.x uses old artifact names and supports JUnit 4; k6 0.x/1.x scripts using
  `externally-controlled` break on 2.x; Python <= 3.13 has no `pdb -p` (use py-spy/`gdb` + `py-bt`). Match the project's version
  in examples; do not upgrade unless asked.

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| Protractor | Playwright (or Cypress); Angular dropped Protractor | Protractor EOL 2023 |
| PhantomJS, headless-only Chrome hacks | Playwright/Puppeteer headless real engines | PhantomJS archived 2018 |
| Karma + Jasmine for Angular | Vitest (Angular default since v21) or Jest | Karma deprecated 2023 |
| Enzyme (shallow render, `.state()`) | Testing Library: query by role/label, assert on output | Enzyme unmaintained; no official adapter past React 16 (only community ones) |
| Selenium-only for a NEW web e2e suite | Playwright (auto-wait, traces, multi-browser); keep Selenium/WebdriverIO where a grid or real-device cloud is needed | - |
| Firefox via CDP | WebDriver BiDi | Firefox 141 (2025-07) removed CDP |
| `jest.mock` of every import, mocks asserting calls | Real collaborators or fakes; mock only process boundaries (network, clock, randomness) | - |
| `sleep(2000)` / `cy.wait(2000)` / `Thread.sleep` | Wait for a condition: web-first assertions, `expect.poll`, Espresso idling resources, `waitFor` | - |
| 100% coverage targets | Coverage as a gap finder + mutation score on core logic | - |
| Snapshot of whole component trees | Small explicit assertions; aria snapshots or targeted visual diffs | - |
| H2/SQLite as stand-in for Postgres/MySQL | Testcontainers with the production engine and version | - |
| Appium JSON Wire Protocol caps | W3C capabilities with `appium:` prefix | Appium 2/3 |
| `console.log`/`print` as the only debugger | Debugger breakpoints, logpoints, profilers, structured logs + traces | - |
| clinic.js (Doctor/Flame) as Node default | `node --cpu-prof`/`--heap-prof`, DevTools via `--inspect`, 0x; clinic last release 2023, repo idle since 2024-09 | - |
| Retrying flaky tests forever | Quarantine with an owner and a deadline, fix the cause | - |
| JMeter GUI-recorded load tests in CI | Load tests as code (k6, Gatling, Locust) in VCS | - |

## Do this

### Strategy: what to test where
- Pyramid vs trophy is not religion: put most tests at the **cheapest level that can catch the bug**. Pure logic: unit. Code
  whose risk is wiring (SQL, HTTP handlers, serialization, auth middleware): integration against real dependencies. A few e2e
  flows for revenue/critical paths (sign-up, login, checkout, the core task). Frontend apps lean "trophy" (many integration/
  component tests), libraries lean "pyramid" (many unit tests).
- Test **behavior through public interfaces**: inputs -> outputs and observable side effects. A refactor that keeps behavior
  must not break tests. If it does, the tests are coupled to implementation.
- Every bug fix starts with a **failing test that reproduces it**; keep it as the regression test.
- Name tests as behavior: `rejects_expired_token`, not `test_validate_2`.
- Arrange-Act-Assert, one behavior per test, the assertion message says what was expected.
- **Test data**: builders/factories with sensible defaults (`aUser().withRole("admin")`), only set the fields the test cares
  about. No shared mutable fixtures; each test creates and cleans its own data (transaction rollback or unique IDs per test).
  Never use production data copies containing personal data; generate synthetic data.
- **Control nondeterminism**: inject a clock (`Clock`, `time.Now` func, `vi.useFakeTimers`, Playwright `page.clock`), seed
  randomness and log the seed, fix timezone and locale in CI (`TZ=UTC`), never depend on test order (run shuffled:
  `go test -shuffle=on`, `pytest -p random_order` style plugins, Playwright `--shuffle` 1.64+).

### Test doubles discipline
- Prefer, in order: real object -> in-memory fake (e.g. a fake repository with a map) -> stub (canned answers) -> mock (verifies
  calls). Use a mock only when the call itself is the behavior (an email was sent, a payment was captured exactly once).
- Mock at the boundary you own (your `PaymentGateway` interface), not the third-party SDK internals.
- Network: intercept at the HTTP layer (MSW, undici `MockAgent`, WireMock, `httptest`, `responses`/`respx`) rather than
  monkeypatching the client library.
- A fake needs its own small contract test against the real thing, or it drifts.

### Integration, API and database tests
- Testcontainers: one container per test run (not per test), migrations applied once, isolation per test via transactions or
  truncation. Pin the image tag to the production major (`postgres:18`), not `latest`. CI needs Docker (or Testcontainers Cloud).
- API tests: assert status, headers that matter (cache, CORS, auth), body schema, and error shapes; test authz with two users
  (IDOR) and every role. Schema-driven fuzzing (Schemathesis) catches 500s the happy path never hits.
- **Contract testing (Pact)**: consumer tests produce a pact, the provider verifies it in its own CI, `can-i-deploy` gates
  releases (needs a Pact Broker or PactFlow). Worth it when separate teams own both sides and deploy independently. One team
  with an OpenAPI spec: lint the spec + breaking-change diff (oasdiff/`buf breaking`) is usually enough (see rc-web-backend).

### Property-based, mutation, snapshot
- **Property-based** (Hypothesis, fast-check, jqwik, Go native fuzzing): for parsers, serializers, encoders, money math, sorting,
  state machines. Properties: round-trip (`decode(encode(x)) == x`), invariants, oracle comparison, idempotence. Commit shrunk
  failing cases as fixed regression examples.
- **Mutation testing** (Stryker, PIT, mutmut, Infection, cargo-mutants): run on core domain modules, incrementally on changed
  files in PRs, full runs nightly. Surviving mutants show untested behavior; a high coverage number with a low mutation score
  means tests execute code without checking it.
- **Snapshot/golden tests**: keep them small, reviewed and deterministic (sorted keys, no timestamps/IDs). Never update
  snapshots blindly (`-u`) to make CI green; read the diff. Prefer `toMatchAriaSnapshot` for structure over HTML snapshots.

### Web e2e (Playwright default)
```ts
test('user can reset password', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await page.getByLabel('Email').fill('user@example.test');
  await page.getByRole('button', { name: 'Send link' }).click();
  await expect(page.getByRole('status')).toHaveText(/check your inbox/i); // web-first, auto-retries
});
```
- Locators by role/label/text, then `data-testid`; never CSS chains or XPath on layout.
- Log in once via `storageState` setup project; seed data via API, not by clicking through the UI.
- `trace: 'on-first-retry'` in CI and upload the HTML report as an artifact; read the trace before touching the test.
- Run in parallel with isolated data; shard (`--shard=1/4`) in CI; keep retries at 1-2 and treat any retry as a flaky signal.
- **Cypress** is fine for existing suites and good DX for component tests; it runs in-browser (one tab, same-origin limits
  relaxed via `cy.origin`). **WebdriverIO/Selenium**: when you need a Selenium grid, real Safari on device clouds, or Appium reuse.
- **Visual regression**: Playwright `toHaveScreenshot` or Vitest `toMatchScreenshot`, run in one pinned Docker image (fonts and
  GPU differ per OS), mask dynamic regions, disable animations. Hosted review: Chromatic, Percy, Argos.

### Accessibility testing
- Automated: `@axe-core/playwright` on every page and state (open menus, dialogs, errors), fail on serious/critical; Pa11y or
  Lighthouse CI for URL lists. Automation finds only part of the issues (often quoted near a third).
- Manual each release for changed flows: keyboard only (focus order, visible focus, no traps), screen reader pass (VoiceOver on
  macOS/iOS, NVDA on Windows, TalkBack on Android), 200% zoom/large text, reduced motion. Details: rc-ui-ux-design.

### Performance and load testing
- Define SLOs first (e.g. p95 < 300 ms and error rate < 0.1% at 200 req/s), encode them as thresholds that fail the run.
- Workload model from production: request mix, think time, data cardinality (not one cached user ID), arrival rate
  (open model: k6 `constant-arrival-rate`) rather than only closed VU loops.
- Test types: smoke (1 VU, every CI), load (expected peak), stress (find the knee), soak (hours, leaks), spike.
- Measure percentiles (p50/p95/p99), throughput, errors, and the server side (CPU, memory, GC, DB pool wait, queue depth)
  together; averages hide tails. Run from a separate machine; never load-test production without an agreement.
```js
// k6: fail the build when the SLO breaks
export const options = {
  scenarios: { api: { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: '5m', preAllocatedVUs: 100 } },
  thresholds: { http_req_duration: ['p(95)<300'], http_req_failed: ['rate<0.001'] },
};
```
- Tool choice: k6 (JS scripts, Go engine, browser module), Gatling (JVM or JS/TS SDK, strong reports), Locust (Python),
  JMeter only where a team already has plans. Frontend: Lighthouse CI budgets + RUM for Core Web Vitals (rc-web-security-perf).

### Security testing in CI
- SAST: CodeQL (free for public repos) or Semgrep/Opengrep on every PR, high-confidence rules blocking, rest triaged.
- Secrets: gitleaks or TruffleHog in pre-commit and CI, plus platform push protection. A leaked secret is rotated, not just deleted.
- Dependencies: `npm audit`/`pip-audit`/`govulncheck`/OSV-Scanner, Dependabot/Renovate. Containers: Trivy/Grype.
- DAST: ZAP baseline scan against a staging deploy; full/active scans on a schedule, never against production without consent.
  Details and headers: rc-web-security-perf.

### Mobile testing
- Android: JVM unit tests + Compose tests (Robolectric for speed, device for fidelity), Espresso for View screens, UI Automator
  for system UI, screenshot tests (Roborazzi/Paparazzi/Compose Preview Screenshot). Gradle Managed Devices for CI emulators.
- iOS: Swift Testing for logic, XCUITest for flows with accessibility identifiers (see rc-ios).
- Cross-platform black-box flows: **Maestro** (YAML flows, tolerant waits, fast to write); Appium 3 when you need WebDriver
  clients, many languages or an existing grid. Detox for React Native.
- Real devices: Firebase Test Lab, AWS Device Farm, BrowserStack, Sauce Labs; run a small smoke set per PR, full matrix nightly.
  Test on a low-end Android device and the oldest supported OS.

### CI quality gates, coverage, release
- Gate order (fast first): format/lint -> type check -> unit -> integration -> build -> e2e smoke -> security scans.
- Coverage: report it, gate on **not decreasing** coverage of changed lines (diff coverage, e.g. 80%) rather than a global
  number. Coverage says what code ran, never that it was checked. Use branch coverage. Exclude generated code honestly.
- Flaky-test policy: detect (retries reported, rerun history), quarantine within a day with an owner and ticket, fix or delete.
- Reproducible builds: lockfiles committed and used (`npm ci`, `uv sync --locked`, `go mod verify`), pinned toolchain
  versions, pinned container digests and CI actions by SHA, `SOURCE_DATE_EPOCH` where supported; build twice and compare hashes
  for release artifacts if the ecosystem supports it.
- Release validation: post-deploy smoke tests against the real environment (health endpoint, login, one read, one write with a
  test account), synthetic checks running continuously. Canary (1% -> 10% -> 50% -> 100%) with automatic rollback on SLO burn.
  Feature flags decouple deploy from release: every flag has an owner and a removal date; test both flag states.

### Debugging: the root-cause method
1. **Reproduce** reliably; write the exact steps, inputs, versions, environment. Turn it into a failing automated test if possible.
2. **Read the evidence**: full error, full stack trace (bottom-most frame in your code; the first "Caused by" in Java is usually
   the root), logs around the timestamp, recent changes (`git log -p` on the area, deploy history).
3. **Isolate**: shrink input and scope until the failure stays and nothing else does (delta debugging); remove variables
   (cache, network, concurrency, config) one at a time.
4. **Hypothesize** one cause, predict what you would observe if it were true, then test that prediction. One change at a time.
5. **Bisect** when it used to work: `git bisect run ./repro.sh` (exit 0 good, 1 bad, 125 skip). Also bisect dependency versions
   and config.
6. **Instrument**: breakpoints/logpoints, conditional breakpoints, watch expressions, a profiler or tracer for performance,
   targeted logs with IDs. Remove temporary logs after.
7. **Fix the cause, not the symptom**; check every caller of the code you changed; keep the regression test; write down what
   happened if it took more than an hour (project notes).
- If two fix attempts fail, stop guessing: re-read the evidence, list assumptions, verify each one.

### Hunting specific bug classes
- **Memory leaks**: compare heap snapshots over time (DevTools 3-snapshot technique, memray, `jcmd GC.heap_dump` + Eclipse MAT,
  Go `pprof -base`, LeakCanary on Android, Instruments Leaks/Allocations). Usual suspects: unbounded caches/maps, listeners and
  subscriptions not removed, closures holding large objects, goroutines/threads blocked forever.
- **Concurrency**: run race detectors (`go test -race`, ThreadSanitizer `-fsanitize=thread` for C/C++/Rust/Swift, Xcode TSan,
  Swift 6 strict concurrency checks), stress loops (`go test -count=1000`, `pytest --count` style), Java jcstress for memory-model
  issues, thread dumps for deadlocks (`jcmd PID Thread.print`, `py-spy dump`, `kill -QUIT` for Go). Deterministic schedulers
  (Go `synctest`, virtual time) make timing bugs reproducible.
- **Flaky tests**: rerun the single test many times and shuffled; check shared state, order dependence, time/timezone, async
  waits, test pollution of globals/env, port collisions, resource limits in CI (slower CPU exposes races).
- **Crashes**: native core dumps (`ulimit -c unlimited`, `coredumpctl gdb`), symbolicated crash logs (Xcode Organizer, Play Console
  with mapping/R8 file uploaded), JVM `hs_err_pid*.log`.
- **Network**: `curl -v`/`--trace-ascii`, HTTPie, browser Network panel (copy as cURL), mitmproxy for app traffic,
  tcpdump/Wireshark for TLS/DNS/TCP-level issues, `dig`, `openssl s_client`.
- **System level**: `strace -f -e trace=file,network -p PID`, `ltrace`, `lsof -p`, `perf top`, eBPF tools (`bpftrace`), gdb/lldb.

### Production debugging
- Structured logs (JSON) with request/trace IDs, OpenTelemetry traces across services, metrics with exemplars linking to traces.
  Log errors once at the boundary, with context, without secrets or personal data.
- Continuous/flight-recorder profiling (JFR, Go FlightRecorder, Pyroscope/Parca-style profilers) to catch issues you cannot
  reproduce locally. Feature-flag a verbose log level per request or tenant instead of redeploying.
- Post-incident review: blameless, timeline, impact, root cause(s) and contributing factors, what detected it, what slowed the
  response, action items with owners and dates. Track action items to done.

### When NOT to add a dependency
- Do not add Cypress next to Playwright, or Jest next to Vitest: one tool per layer.
- Do not add an assertion/mocking library when the runner has one (Vitest, `node:test`, Go `testing`, pytest `monkeypatch`).
- Do not add Pact for a single team with one consumer; do not add a load-test SaaS before a k6 script runs locally.
- Do not add a hosted visual-diff service until local screenshot tests prove valuable.

## Security
- Tests and fixtures never contain real secrets or personal data; use synthetic data and CI secrets with least privilege.
  Fork PRs must not get deploy or cloud secrets (`pull_request`, not `pull_request_target` with checkout of PR code).
- Test-only endpoints, seeders and auth bypasses are compiled out or blocked in production builds; assert this in a test.
- Debug ports (`--inspect=0.0.0.0`, JDWP, `pdb` remote, Delve headless) bind to localhost only; tunnel with SSH. Never ship debuggable
  Android release builds. Python 3.14 remote attach can be disabled with `PYTHON_DISABLE_REMOTE_DEBUG`.
- Core dumps, heap dumps, HARs and traces contain secrets and personal data: store encrypted, short retention, never in public CI
  artifacts or issues.
- DAST and load tests only against environments you own, with written consent for anything shared.

## Performance & quality
- Unit suite < 1-2 min locally; whole PR pipeline < 10-15 min; e2e smoke < 5 min (shard to stay there).
- Flake rate target: < 1% of runs need a retry; any test retried 3+ times in a week gets quarantined.
- Mutation score on core modules tracked over time (no universal target; watch the trend on changed code).
- Profile before optimizing: get a flame graph, find the top frames, change one thing, measure again with the same workload.
- Web: INP debugging with DevTools Performance panel (live metrics, interaction track, long animation frames); field data first.

## Testing & tooling
- Per-language runners: see the language skills. Cross-cutting defaults for a new project:
  - Web e2e: Playwright + `@axe-core/playwright`; component tests: Vitest (browser mode for real DOM) + Testing Library.
  - HTTP mocking: MSW (JS), WireMock (JVM), `httptest` (Go), `respx`/`responses` (Python).
  - Integration: Testcontainers. Contract: Pact (multi-team) or OpenAPI diff (single team).
  - Load: k6. Property: Hypothesis / fast-check / jqwik / Go fuzzing. Mutation: Stryker / PIT / mutmut / Infection.
  - Security: CodeQL or Semgrep, gitleaks, OSV-Scanner or ecosystem audit, ZAP baseline on staging.
  - Mobile: Compose/Espresso + Roborazzi, Swift Testing + XCUITest, Maestro for black-box flows.
- Debuggers: VS Code/JetBrains debuggers over DAP, Chrome DevTools, `node --inspect`, `python -m pdb`/debugpy, Delve, jdb/IDE,
  lldb/gdb. Command reference: `references/debugging-toolbox.md`.

## Common mistakes in AI-written code
**Tests that look right but are wrong**
- Asserting on the mock: `mock.return_value = 5; assert service() == 5` tests the mock, not the code.
- No assertion at all, or an assertion inside a callback/promise that never runs (missing `await`, `expect.assertions(n)` absent,
  `done` never called). Vitest 5 fails unawaited assertions; older runners pass silently.
- `try { act() } catch (e) { expect(e)... }` with no failure if nothing throws: use `expect(() => act()).toThrow()` / `pytest.raises`.
- Over-mocking: every collaborator mocked, so the test re-states the implementation and passes when the real integration is broken.
- Verifying call order and internal method calls instead of results.
- Order-dependent tests sharing a DB row, a singleton, env vars or a module-level cache; pass alone, fail together (or vice versa).
- `sleep` instead of waiting for a condition; tight timeouts that pass on a fast laptop and fail in CI.
- Tests that pass when the code is deleted: check by breaking the code (or running a mutation tool) once.
- Snapshot updated to match a bug; golden files regenerated without review.
- Hard-coded "today", timezones, locale-formatted numbers; random data without a logged seed.
- Catch-all `@pytest.mark.skip`/`it.skip`/`xit` left in, or `retries: 5` hiding real failures.
- Hallucinated APIs: `page.waitForTimeout` as a sync strategy (exists, but is a smell), `cy.exec` (removed in Cypress 16),
  `@vitest/browser/context` (moved to `vitest/browser` in 4.0), `Cypress.env()` (removed in 16), Testcontainers Java
  `org.testcontainers.containers.PostgreSQLContainer` on 2.x (moved to `org.testcontainers.postgresql`), JUnit 4 rules with
  Testcontainers 2, Appium caps without the `appium:` prefix, Enzyme `shallow()`.
**Debugging mistakes**
- Changing several things at once, "fixing" by adding retries/timeouts/null checks at the symptom, declaring victory without
  rerunning the original reproduction, or catching and swallowing the exception that was the clue.

### Checklist for AI-written tests
- [ ] Each test fails if the behavior it names is broken (verified once by breaking the code or a mutation run).
- [ ] Asserts on outputs/observable effects, not on mocks' return values or private calls.
- [ ] Mocks only at process boundaries; fakes for owned interfaces; no mocking of the unit under test.
- [ ] Deterministic: fixed clock, seeded randomness, no sleeps, no order dependence (passes shuffled and alone).
- [ ] Async: every promise awaited, every async assertion awaited.
- [ ] Data built per test with builders; no personal data; cleanup or rollback.
- [ ] Edge cases: empty, null/missing, max size, unicode, timezone/DST, concurrency, error paths and permission denied.
- [ ] Uses APIs of the project's installed versions (check the lockfile before writing code).
- [ ] Readable name and a failure message that tells the next person what broke.

## Before you ship
- [ ] Regression test for every fixed bug; new behavior covered at the cheapest effective level.
- [ ] CI green without retries masking failures; no new skips; flaky tests quarantined with an owner.
- [ ] Diff coverage meets the project gate; core logic mutation-tested when the project uses it.
- [ ] e2e smoke for critical paths, axe clean on changed pages, manual keyboard + screen reader check for changed UI.
- [ ] Load test with SLO thresholds for changes on hot paths; profile compared before/after for performance work.
- [ ] SAST, secret scan and dependency audit pass; DAST baseline on staging for web changes.
- [ ] Release plan: canary or flag, post-deploy smoke test, rollback steps known and tested.
- [ ] Debug code, verbose logs, test-only routes and debug ports removed or disabled in production.

## Sources
- https://playwright.dev/docs/release-notes - Playwright 1.56-1.64 features (agents, Chrome for Testing, isolated retries, locks, shuffle) - 2026-10-09
- https://registry.npmjs.org (`npm view <pkg> version time`) - Playwright, Cypress, Vitest, Jest, MSW, axe-core, Pa11y, LHCI, Pact, Testcontainers Node, Stryker, fast-check, clinic, 0x, Appium, WebdriverIO, Selenium versions and dates - 2026-10-09
- https://docs.cypress.io/app/references/changelog - Cypress 16.0 breaking changes and Node support - 2026-10-09
- https://vitest.dev/blog/vitest-4 and https://vitest.dev/blog/vitest-5 - browser mode stable, providers, toMatchScreenshot, 5.0 changes - 2026-10-09
- https://fxdx.dev/cdp-retirement-in-firefox/ - Firefox 141 removes CDP, BiDi replacement - 2026-10-09
- https://www.selenium.dev/blog/ - Selenium release cadence and BiDi direction - 2026-10-09
- https://github.com/grafana/k6/releases - k6 1.0 (2025-05-06), 2.0 (2026-05-11) breaking changes, 2.3.0 - 2026-10-09
- https://docs.gatling.io/reference/script/ - Gatling Java and JavaScript SDKs - 2026-10-09
- https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0 - Testcontainers 2.0 changes - 2026-10-09
- https://appium.io/docs/en/latest/guides/migrating-2-to-3/ - Appium 3 W3C-only, Node requirement - 2026-10-09
- https://docs.python.org/3/whatsnew/3.14.html and https://docs.python.org/3.15/whatsnew/3.15.html - pdb -p, asyncio ps, profiling.sampling, frame pointers - 2026-10-09
- https://go.dev/doc/go1.25 - FlightRecorder, synctest GA - 2026-10-09
- GitHub releases API (`gh api repos/<owner>/<repo>/releases/latest`) - Semgrep, Opengrep, CodeQL bundle, ZAP, gitleaks, TruffleHog, OSV-Scanner, Maestro, Delve, async-profiler, py-spy, memray, LeakCanary, mitmproxy, Perfetto, PIT, Hypothesis, Schemathesis, Gatling, Locust, JMeter, Lighthouse, Roborazzi, Paparazzi, swift-snapshot-testing - 2026-10-09
- https://repo1.maven.org and https://dl.google.com/android/maven2 - LeakCanary 2.14, Espresso 3.7.0, Testcontainers Java 2.0.5 - 2026-10-09
- https://pypi.org - Locust, Testcontainers Python, mutmut versions - 2026-10-09
