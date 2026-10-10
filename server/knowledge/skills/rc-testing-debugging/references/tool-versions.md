# Test and debug tool versions (checked 2026-10-09)

Latest release on the primary registry or GitHub releases on 2026-10-09. "Major since" = first release of the current major.
Always check the project's lockfile first and write code for the installed version.

| Area | Tool | Latest (date) | Major since / notes |
|---|---|---|---|
| Web e2e | Playwright | 1.64.0 (2026-10-07) | 1.x; 1.57 Chrome for Testing builds |
| Web e2e | Cypress | 16.1.1 (2026-09-29) | 16.0.0 2026-09-01; Node 22/24/26 |
| Web e2e | WebdriverIO | 10.0.2 | 10.0.0 2026-10-05 |
| Web e2e | selenium-webdriver (JS) | 4.51.0 (2026-10-09) | 4.x, monthly-ish |
| Unit/component | Vitest | 5.0.3 | 5.0.0 2026-09-03 (Node >= 22.12, Vite >= 6.4) |
| Unit/component | Jest | 30.5.2 | 30.0.0 2025-06-10 |
| Unit/component | @testing-library/react | 16.3.3 | - |
| HTTP mocking | MSW | 3.0.2 | 3.0.0 2026-09-28 |
| A11y | axe-core | 4.14.0 (2026-10-05) | - |
| A11y | @axe-core/playwright | 4.13.0 | - |
| A11y | Pa11y | 10.0.0 (2026-08-28) | Node ^22.13 or >= 24 |
| Perf/a11y audit | Lighthouse | 13.5.0 (2026-09-18) | - |
| Perf/a11y audit | Lighthouse CI (@lhci/cli) | 0.15.1 (2025-06-25) | slow cadence; bundles Lighthouse 12.6.1 |
| Contract | Pact JS | 17.1.4 (2026-09-07) | 17.0.0 2026-06-25 |
| API fuzz | Schemathesis | 4.30.1 (2026-10-09) | - |
| Containers | Testcontainers Java | 2.0.5 (2026-04-20) | 2.0.0 2025-10-14 (JUnit 4 gone, renamed artifacts) |
| Containers | Testcontainers Node | 12.2.0 | 12.0.0 2026-05-19 |
| Containers | Testcontainers Python | 4.15.0 | - |
| Property | Hypothesis | 6.168.5 (2026-10-05) | - |
| Property | fast-check | 4.10.2 | - |
| Mutation | Stryker (JS) | 10.0.0 (2026-08-14) | - |
| Mutation | PIT (JVM) | 1.30.0 (2026-08-27) | - |
| Mutation | mutmut (Python) | 3.8.0 | - |
| Load | k6 | 2.3.0 (2026-09-21) | 2.0.0 2026-05-11; 1.0.0 2025-05-06 |
| Load | Gatling | 3.16.0 (2026-09-29) | Java/Kotlin/Scala + JS/TS SDK |
| Load | Locust | 2.46.7 (2026-10-04) | - |
| Load | Apache JMeter | 5.6.3 (2024-01) | no newer release found |
| SAST | Semgrep | 1.180.0 (2026-10-07) | - |
| SAST | Opengrep (fork) | 1.30.2 (2026-10-07) | LGPL fork of Semgrep CE |
| SAST | CodeQL bundle | 2.27.2 (2026-10-07) | via github/codeql-action |
| DAST | ZAP | 2.17.0 (2025-12-15) | weekly builds in between |
| Secrets | gitleaks | 8.30.1 (2026-03-21) | - |
| Secrets | TruffleHog | 3.99.2 (2026-10-08) | - |
| Deps | OSV-Scanner | 2.6.0 (2026-09-14) | - |
| Android UI | Espresso | 3.7.0 | - |
| Android screenshot | Roborazzi | 1.76.0 (2026-09-29) | - |
| Android screenshot | Paparazzi | 2.0.0-alpha05.1 (2026-09-28) | 1.3.5 is the last stable release |
| Android leaks | LeakCanary | 2.14 stable (2024-04) | 3.0-alpha-9 (2026-06-25) |
| Mobile flows | Maestro CLI | 2.11.0 (2026-09-29) | Apache-2.0 |
| Mobile flows | Appium | 3.8.0 (2026-09-24) | 3.0.0 2025-08-18, W3C only |
| iOS snapshot | swift-snapshot-testing | 1.19.6 (2026-09-21) | - |
| Profiling | async-profiler | 4.5 (2026-07-20) | - |
| Debugging | Delve | 1.27.2 (2026-09-09) | - |
| Profiling | py-spy | 0.4.2 (2026-04-24) | - |
| Profiling | memray | 1.20.0 (2026-08-07) | Linux/macOS only |
| Profiling | Perfetto | v58.2 (2026-08-24) | - |
| Network | mitmproxy | 12.2.3 (2026-05-12) | - |
| Profiling | clinic (Node) | 13.0.0 (2023-06-28) | repo idle since 2024-09: avoid as a new default |
| Profiling | 0x (Node) | 6.0.0 (2025-07-07) | - |
| Agent tools | chrome-devtools-mcp | 1.10.1 (2026-09-23) | 0.1.0 2025-09-16 |
| Agent tools | @playwright/mcp | 0.0.83 | pre-1.0 |
