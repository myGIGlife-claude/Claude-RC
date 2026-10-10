# Coverage against the "Senior Development Knowledge Expansion" brief

Status values: **done** (written and independently fact-checked; a few leads stay marked `(unverified)` inside the skills), **partial** (touched inside another skill, not a skill of its own),
**planned** (queued, not started), **unsupported** (cannot be done through skills; stated honestly). Updated 2026-10-10.

| Brief section | Status | Where / what remains |
|---|---|---|
| 1 HTML, CSS, JS, TS | done | rc-web-frontend, rc-javascript-typescript |
| 1 WebAssembly, SVG/Canvas, Web Components, config formats | done | rc-wasm-graphics-formats, Web Components in rc-web-frontend |
| 1 PHP, Python, Node, Go, Java | done | rc-php, rc-python, rc-node-backend, rc-go, rc-java |
| 1 Kotlin, Swift | done | rc-kotlin (server, coroutines, KMP); Swift inside rc-ios |
| 1 C, C++, C#, Rust, Ruby, Dart, Elixir/Erlang, Scala, Perl | done | rc-c-cpp, rc-csharp-dotnet, rc-rust, rc-ruby-rails, rc-dart-flutter, rc-elixir-erlang, rc-scala (Perl in rc-shell-scripting) |
| 1 Shell (Bash, Zsh, PowerShell), SQL | done | rc-shell-scripting; SQL in rc-databases |
| 2 Frontend frameworks, rendering, PWA, a11y, perf | done | rc-web-frontend, rc-web-security-perf, rc-ui-ux-design |
| 3 APIs, auth, DB basics, caching, queues, observability, reliability | done | rc-web-backend |
| 3 Architecture, event-driven, distributed systems, payments, search, email, HA/DR | done | rc-architecture-systems, rc-web-backend |
| 3 Backend frameworks (Rails, ASP.NET Core, Spring, Django, Laravel, NestJS, Gin/Fiber) | done | each in its language skill (rc-ruby-rails, rc-csharp-dotnet, rc-java, rc-python, rc-php, rc-node-backend, rc-go) |
| 4 Android | done | rc-android (Compose, Play policy, security, adaptive); Google Play Billing and device APIs (camera, BLE, NFC) are shallow: refine |
| 5 iOS and Apple platforms | done | rc-ios; macOS/watchOS shallow: refine |
| 6 Cross-platform (Flutter, React Native, Expo, KMP/CMP, MAUI, Capacitor, Tauri, Electron) | done | rc-cross-platform (decision guide, native-first when it wins), rc-dart-flutter |
| 7 UI/UX and product design | done | rc-ui-ux-design |
| 8 Databases and data engineering | done | rc-databases |
| 9 Cloud, Linux, Docker, Kubernetes, IaC, CI/CD, DNS/TLS, observability, DR, hardening | done | rc-devops-linux, rc-cloud-platforms |
| 10 Security, privacy, compliance | done | rc-web-security-perf, rc-security-engineering |
| 10 Privacy-first as a default principle | done | rc-dev-playbook |
| 11 Testing, debugging, code quality | done | rc-testing-debugging |
| 12 Senior judgment | done | rc-dev-playbook (process skill) |
| 13 Verification discipline | done | BRIEF.md rules + independent fact-check pass (about 15% of the riskiest first-draft claims were wrong) |
| 14 Modular integration, project-type detection, retrieval | partial | skills load on demand by description; rc-dev-playbook adds project-type detection and routing; version-aware notes: BRIEF.md now requires an "Older versions" section |
| 15 Autonomy and capability | measured, partial | evals: on 14 questions about things after the model's knowledge cutoff, 13 correct with the pack vs 7 without (one model, one run each; practice scenarios not yet re-run) |
| Writing: American English, editing, voice, UI copy (separate brief, 2026-10-10) | done (first pass), partial | rc-american-english-writing (process skill + `references/ui-copy.md`, `references/sources.md`) and a pointer in `rules/rc-knowledge.md`. Sources actually read: digital.gov plain language (overview only), Google developer style highlights, Microsoft style guide welcome. NOT read (page empty or 403): Apple HIG writing, Material 3 content design, Merriam-Webster. Chicago, AP, Garner, American Heritage are proprietary and were not inspected. Not done: a researched deep reference library, per-project voice guides, a before/after benchmark with human review; 12 regex evals (`wr-*`, objective checks only: facts kept, no stock phrases or Markdown, voice words kept, US dates/money). Results on Sonnet: first 4 easy ones 4/4 with and without; 8 harder ones 13/16 with vs 12/16 without, and after fixing two over-strict patterns of mine, 14/15 vs 14/15 on a 3x rerun. Verdict: the current model already writes these well, so the evals show NO measurable gain from the skill; any real difference (voice, rhythm, naturalness) needs human review, which has not been done. |
| 16 Continuous maintenance | done | monthly refresh (research + fact-check, ends in a pull request) |
| 17 Phase 5 testing against realistic scenarios | done | server/knowledge/evals/ (52 scenarios, objective checks, with vs without the pack) |
| 17 Phase 7 documentation and status tracking | done (this file) | README.md, COVERAGE.md |

## Honest limits
- The model is not retrained. All of this is persistent skills, rules and tooling the sessions load when relevant.
- Every skill is research output reviewed by a second agent against primary sources; unverified items are marked. It is not a guarantee.
- Every session pays a small context cost for each skill description, so descriptions are kept short and the pack grows only where it earns it.
