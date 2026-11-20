# Test and debug tool versions (checked 2026-11-19)

Latest release on the primary registry or GitHub releases on 2026-11-19. "Major since" = first release of the current major.
Always check the project's lockfile first and write code for the installed version. "(unchanged)" = not re-verified on a date newer than 2026-10-09.

| Area | Tool | Latest (date) | Major since / notes |
|---|---|---|---|
| Web e2e | Playwright | 1.65.1 (npm latest; 1.64.0 was 2026-10-07) | 1.x; 1.57 Chrome for Testing builds; 1.65 Bun support |
| Web e2e | Cypress | 16.3.0 (2026-11-10) | 16.0.0 2026-09-01; Node 22/24/26; 15.21.2 (2026-11-12) security backport |
| Web e2e | WebdriverIO | 10.2.0 | 10.0.0 2026-10-05 |
| Web e2e | selenium-webdriver (JS) | 4.53.0 | 4.x, monthly-ish |
| Unit/component | Vitest | 5.1.1 (2026-11-17) | 5.0.0 2026-09-03 (Node 22+, Vite 6.4+; the ".12" minor is unverified); 4.1.12 backports |
| Unit/component | Jest | 30.5.2 | 30.0.0 2025-06-10 |
| Unit/component | @testing-library/react | 16.3.3 (unchanged) | - |
| HTTP mocking | MSW | 3.0.7 | 3.0.0 2026-09-28 |
| A11y | axe-core | 4.14.1 | 4.14.0 was 2026-10-05 |
| A11y | @axe-core/playwright | 4.14.0 | - |
| A11y | Pa11y | 10.0.0 (2026-08-28) | Node ^22.13 or >= 24 |
| Perf/a11y audit | Lighthouse | 13.6.0 | - |
| Perf/a11y audit | Lighthouse CI (@lhci/cli) | 0.16.1 (2026-11-11) | 0.15.1 was 2025-06-25; bundled Lighthouse version unverified |
| Contract | Pact JS | 17.1.4 | 17.0.0 2026-06-25 |
| API fuzz | Schemathesis | 4.33.4 | - |
| Containers | Testcontainers Java | 2.0.5 (2026-04-20) | 2.0.0 2025-10-14 (JUnit 4 gone, renamed artifacts) |
| Containers | Testcontainers Node | 12.2.2 | 12.0.0 2026-05-19 |
| Containers | Testcontainers Python | 4.15.0 | - |
| Property | Hypothesis | 6.170.16 (2026-11-19) | - |
| Property | fast-check | 4.10.3 | - |
| Mutation | Stryker (JS) | 10.1.1 | 10.0.0 2026-08-14 |
| Mutation | PIT (JVM) | 1.30.2 (2026-11-05) | - |
| Mutation | mutmut (Python) | 3.8.1 | - |
| Load | k6 | 2.4.0 (2026-11-12) | 2.0.0 2026-05-11; 1.0.0 2025-05-06; 1.8.3 patch line (2026-11-13); built-in InfluxDB and Prometheus remote-write outputs deprecated before v3 |
| Load | Gatling | 3.16.1 (2026-11-16) | Java/Kotlin/Scala + JS/TS SDK |
| Load | Locust | 2.47.0 (2026-10-21) | - |
| Load | Apache JMeter | 5.6.3 (2024-01) | no newer release found |
| SAST | Semgrep | 1.186.0 (2026-11-18) | - |
| SAST | Opengrep (fork) | 2.0.0 (2026-11-13) | LGPL fork of Semgrep CE; Python removed, `install-ci` removed, interfile only with `--enable-interfile` |
| SAST | CodeQL bundle | 2.27.5 (2026-11-19) | via github/codeql-action |
| DAST | ZAP | 2.17.0 (latest stable on zaproxy.org 2026-11-19; release date unverified) | weekly builds in between |
| Secrets | gitleaks | 8.30.1 (2026-03-21) | - |
| Secrets | TruffleHog | 3.100.0 (2026-11-19) | - |
| Deps | OSV-Scanner | 2.7.1 (2026-11-09) | - |
| Android UI | Espresso | 3.7.0 (unchanged) | - |
| Android screenshot | Roborazzi | 1.79.0 (2026-11-12) | - |
| Android screenshot | Paparazzi | 2.0.0-alpha05.1 (2026-09-28) | 1.3.5 is the last stable release (unchanged) |
| Android leaks | LeakCanary | 2.14 stable (2024-04, unchanged) | 3.0-alpha-9 (2026-06-25) |
| Mobile flows | Maestro CLI | 2.12.1 (2026-11-02) | Apache-2.0 |
| Mobile flows | Appium | 4.0.0 (2026-11-15) | W3C only, JSONWP + Grid 3 support removed, Node ^22.22.2 / ^24.15.0 / >=26; 3.0.0 2025-08-18, last 3.x seen 3.8.0 |
| iOS snapshot | swift-snapshot-testing | 1.19.7 (2026-10-21) | - |
| Profiling | async-profiler | 4.6 (2026-10-19) | - |
| Debugging | Delve | 1.27.2 (2026-09-09) | - |
| Profiling | py-spy | 0.4.4 (2026-11-19) | - |
| Profiling | memray | 1.20.0 (2026-08-07) | Linux/macOS only |
| Profiling | Perfetto | v59.1 (2026-10-27) | - |
| Network | mitmproxy | 12.3.1 (2026-10-25) | - |
| Profiling | clinic (Node) | 13.0.0 (2023-06-28, unchanged) | repo idle since 2024-09: avoid as a new default |
| Profiling | 0x (Node) | 6.0.0 (2025-07-07, unchanged) | - |
| Agent tools | chrome-devtools-mcp | 1.13.0 (2026-11-11) | 0.1.0 2025-09-16 |
| Agent tools | @playwright/mcp | 0.0.86 | pre-1.0 |
