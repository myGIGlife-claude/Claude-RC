# Coverage against the "Senior Development Knowledge Expansion" brief

Status values: **done** (written and independently fact-checked), **partial** (touched inside another skill, not a skill of its own),
**planned** (queued, not started), **unsupported** (cannot be done through skills; stated honestly). Updated 2026-10-09.

| Brief section | Status | Where / what remains |
|---|---|---|
| 1 HTML, CSS, JS, TS | done | rc-web-frontend, rc-javascript-typescript |
| 1 WebAssembly, SVG/Canvas, Web Components, config formats | partial | Web Components in rc-web-frontend; planned: rc-wasm-graphics-formats |
| 1 PHP, Python, Node, Go, Java | done | rc-php, rc-python, rc-node-backend, rc-go, rc-java |
| 1 Kotlin, Swift | partial | inside rc-android / rc-ios; planned: rc-kotlin (server, coroutines, KMP), Swift outside iOS |
| 1 C, C++, C#, Rust, Ruby, Dart, Elixir/Erlang, Scala, Perl | planned | rc-rust, rc-c-cpp, rc-csharp-dotnet, rc-ruby-rails, rc-dart-flutter, rc-elixir-erlang, rc-scala (Perl in shell) |
| 1 Shell (Bash, Zsh, PowerShell), SQL | planned | rc-shell-scripting, rc-sql |
| 2 Frontend frameworks, rendering, PWA, a11y, perf | done | rc-web-frontend, rc-web-security-perf, rc-ui-ux-design |
| 3 APIs, auth, DB basics, caching, queues, observability, reliability | done | rc-web-backend |
| 3 Architecture choices (monolith vs modular vs microservices), event-driven, distributed systems, payments, search, email, HA/DR | partial | pieces in rc-web-backend; planned: rc-architecture-systems |
| 3 Backend frameworks (Rails, ASP.NET Core, Spring, Django, Laravel, NestJS, Gin/Fiber) | partial | Spring/Laravel/Symfony/Django/FastAPI/Express/NestJS inside their language skills; Rails and ASP.NET Core planned |
| 4 Android | done | rc-android (Compose, Play policy, security, adaptive); Google Play Billing and device APIs (camera, BLE, NFC) are shallow: refine |
| 5 iOS and Apple platforms | done | rc-ios; macOS/watchOS shallow: refine |
| 6 Cross-platform (Flutter, React Native, Expo, KMP/CMP, MAUI, Capacitor, Tauri, Electron) | planned | rc-cross-platform (decision guide, native-first when it wins) |
| 7 UI/UX and product design | done | rc-ui-ux-design |
| 8 Databases and data engineering | partial | PostgreSQL/SQLite/pgvector in rc-web-backend; planned: rc-databases (MySQL/MariaDB, SQL Server, MongoDB, Redis/Valkey, search engines, DynamoDB, Firestore, Supabase, modeling, backup) |
| 9 Cloud, Linux, Docker, Kubernetes, IaC, CI/CD, proxies, DNS/TLS, observability, DR, hardening | partial | Docker/K8s/CI basics in rc-web-backend, supply chain in rc-web-security-perf; planned: rc-devops-linux, rc-cloud-platforms (AWS, Azure, GCP, Cloudflare, OVH, IBM) |
| 10 Security, privacy, compliance | partial | rc-web-security-perf (web, supply chain, OWASP 2025); planned: rc-security-engineering (threat modeling, MASVS mobile security, SAST/DAST, GDPR/CCPA basics) |
| 10 Privacy-first as a default principle | planned (this change) | rc-dev-playbook |
| 11 Testing, debugging, code quality | partial | per-language tooling in each skill; planned: rc-testing-debugging (Playwright, Cypress, Vitest, Jest, pytest, JUnit, XCTest, Espresso, contract/load/a11y testing, root-cause method, profiling) |
| 12 Senior judgment (requirements first, read before changing, avoid overengineering, dependencies, refactor vs replace) | planned (this change) | rc-dev-playbook (process skill) |
| 13 Verification discipline | done | BRIEF.md rules + independent fact-check pass (about 15% of the riskiest first-draft claims were wrong) |
| 14 Modular integration, project-type detection, retrieval | partial | skills load on demand by description; rc-dev-playbook adds project-type detection and routing; version-aware notes: BRIEF.md now requires an "Older versions" section |
| 15 Autonomy and capability | **unverified** | no measured improvement yet: needs the evaluation scenarios below |
| 16 Continuous maintenance | done | monthly refresh (research + fact-check, ends in a pull request) |
| 17 Phase 5 testing against realistic scenarios | planned | server/knowledge/evals/ (scenario prompts + objective checks, with vs without the pack) |
| 17 Phase 7 documentation and status tracking | done (this file) | README.md, COVERAGE.md |

## Honest limits
- The model is not retrained. All of this is persistent skills, rules and tooling the sessions load when relevant.
- Every skill is research output reviewed by a second agent against primary sources; unverified items are marked. It is not a guarantee.
- Every session pays a small context cost for each skill description, so descriptions are kept short and the pack grows only where it earns it.
