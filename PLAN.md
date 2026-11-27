Last updated: 2026-11-27 16:30 UTC

## Goal
cLaudeRC: an Android app plus server scripts to run, watch, chat with and manage Claude Code sessions on your own server from a phone.

## Current task
Connection improvements (Jonathan 2026-11-27): C1-C5 merged in #254 (SCRIPT_API 57 installed); C6 merged in #255 (SCRIPT_API 58 installed). Old AWS server (Lightsail ai_paradise) STOPPED, not deleted, until everything is verified.
- [x] C1 Drop cached SSH on network change (ClaudeRcApp NetworkCallback -> SshRunner.retireAll). Check: CI green, phone switch Wi-Fi/mobile reconnects at once.
- [x] C2 fail2ban never bans Tailscale ranges (host harden script + this server's /etc/fail2ban/jail.d/clauderc-tailnet.local). Check: test-api.sh green.
- [x] C3 SSH compression zlib@openssh.com. Check: CI + release APK connects.
- [x] C4 Tailscale address per server, tried first with fallback (worker task 56a3998b). Check: unit tests, Settings field.
- [x] C5 chat-watch long-poll replacing chat-history polling (Opus design in progress, then build; SCRIPT_API bump).
- [x] C6 Copy between servers merged in #255, SCRIPT_API 58 installed. Still to do: a real copy between two servers on the phone; `test-sshd.sh` copy checks passed on CI.

## Model rule (Jonathan, 2026-11-16, master rule in ~/.claude/CLAUDE.md)
Orchestrator splits work into small tasks and picks the cheapest model that does each reliably: Haiku = simple, repetitive; Sonnet = standard build, refactor, tests, moderate debugging; Opus = architecture, hard debugging, ambiguous or system-wide, security design/review (Opus takes every top-tier task by default, Agent `model: opus` or `planner`); Fable 5.2 only when Opus failed or is clearly not enough (one-line reason). Same bug survives 2 fixes: go up one level. Orchestrator keeps review, integration and final verification (real build on CI, never "it compiles").

## Tasks (open work only; each has its model and why)
- [x] T1 README PR #229 (README brought up to date, CI green): merge, then watch main build. Owner OK needed (merging was blocked once). Model: orchestrator, mechanical.
- [ ] T2 Housekeeping: close the superseded PLAN PRs #198, #204, #211 (this refresh replaces them); merge Dependabot #214 (Gradle libraries, 5 updates) and #228 (Actions, 4 updates) only after a CI build, and install the release APK on a phone before trusting the Gradle bump (R8 can break at run time). Model: Haiku for closing and merging; Sonnet to read the Gradle changelog for breaking changes.
- [ ] T3 PR #195 (YouTube honest channel-lookup errors): rebase on main, review, merge. Model: Sonnet, small and isolated.
- [ ] T4 General-chat race: two `general-open` calls on one stopped chat can kill each other's tmux session (`do_general_open` has no lock). Add a per-chat `flock` and a test in `server/tests/test-api.sh`. Model: Sonnet writes, orchestrator reviews (server script, could kill sessions: run all server tests).
- [ ] T14 Star button + queue protocol (Jonathan 2026-11-16): a star icon in the chat top bar adds the chat to the auto-continue queue (server-side state, new action, bump SCRIPT_API and MIN_SCRIPT_API). Markers a session prints: `QUESTION:` (waiting for Jonathan) and `QUEUE: nothing to do`. Model: Opus writes the protocol (unattended, system-wide), Sonnet builds app + server.
- [ ] T15 `claude-queue` scheduler on the server, running all the time for maximum usage: round-robin over starred chats; each turn the chat finishes one PLAN task completely; reads each account's 5-hour/weekly usage and keeps going while there is room, resumes right after a window resets; skips chats waiting on a QUESTION, empty plans, and two chats in one repo at once; kill switch in the app; log. A session may merge its own PR when CI is green, never on red. Hard stops stay: deleting data, production, DNS/mail, spending money, access changes. Model: Opus designs and reviews, Sonnet builds.
- [ ] Q3 follow-ups (queue tick built on branch queue-tick, tests green): (a) TODO push alert on question/needs_you/error: `claude-push` has no "send an alert" mode (only the Claude hook events and `--test`), so add a small `--alert <session> <kind>` option to it, then call it from `qstate` in claude-setup.sh; (b) one manual run on the real server with two throwaway projects; (c) the usage-limit banner wording stays `unverified` (QUEUE_LIMIT_RE), none was found in a real transcript. `stop_reason == "end_turn"` was verified on 231 real replies. Model: Sonnet.
- [ ] T16 Make the "ask first" rules real: `permissions.ask` for DNS/mail/public-post tools (done 2026-11-16 in settings.json); branch protection (required CI check) and `gh pr merge --auto` so a red build cannot merge (needs Jonathan's OK on repo rules); a done-check per queue turn (`/goal` or a Stop hook), `--max-turns`. Model: Opus.
- [ ] T17 "Ask another session": a button in the chat to pick another live session and send it a question (tools exist: `ListAgents`, `SendMessage`; messages to a session in another permission mode wait for approval, test that first). Model: Sonnet.
- [ ] T5 Verify the `unverified` guesses in `clauderc-team` against real Grok and Gemini runs (answer JSON keys, error shape, Gemini login file name and refused-code wording, headless reuse of the login), fix, drop the markers. Needs Jonathan's logged-in workers. Model: Sonnet.
- [ ] T6 Real cluster test with a second account: `delegate(role=...)`, failover, `from_task` writer-to-auditor, 3 parallel tasks, hand-back at 95%, all four kinds (Claude, Codex, Grok, Gemini). Report what breaks. Model: Sonnet runs; Opus (`planner`) reads the results only if something fails.
- [ ] T7 Cluster `wait` still returns about once a minute: make it return when a task finishes. Model: Sonnet (one subsystem); Opus only if the fix touches the detached-run design.
- [ ] T8 More unit tests (Cluster/Team models, chat reducers, widget layout picks). Model: Sonnet.
- [ ] T9 README: real mock-up screens for General chat, Permissions, widgets, natural voice, add-server (source in docs/mockups, never real data). Model: Sonnet builds HTML mock-ups; Grok worker only if a raster picture is needed.
- [ ] T10 Migrate wizard follow-ups (verify first, may be partly done): re-sign-in step for Codex/Grok/Gemini workers, restart sessions and check docker/uv/tools the old server had. Model: Sonnet.
- [ ] T11 Collaboration relay hardening (Cloudflare Worker in ~/projects/clauderc-relay, not in this repo): one-time/expiring invites, per-person revoke, encryption at rest. Model: Opus designs and reviews (security), Sonnet implements.
- [-] T12 iOS: PAUSED by Jonathan 2026-11-16 (Android only, no release planned). Leave `ios/` and the iOS workflow untouched; no iOS work in any task.
- [ ] T13 Widgets, optional next: more actions, general-chat session names, refresh-button feedback. Model: Sonnet. Only after Jonathan has seen them on the phone.

## Owner (Jonathan: needs the phone, an account or a decision)
- [ ] Collaboration tools: this session has pair_* but not collab_new/collab_invite/collab_list because PAIR_RELAY_URL is a channel link, not the hub link (the relay only lists hub tools for the hub link). Put the hub link in the app (+ > Custom API key > PAIR_RELAY_URL), then Sessions > Restart.
- [ ] Update now + Restart sessions, then try on the phone, one pass: release APK (SSH connect, chat, push; R8), fold/unfold and chat PIN lock, widgets (cover screen, unfolded, dark, taps), natural voice (+ speed idea), General chat, Permissions screen, add-server with a password, server purpose, Copy boxes and multi-photo attach, Plugins lights, Cluster settings, Gemini and Grok account sign-in, Apple key dialog.
- [ ] Google Drive backup (needs real Google: Drive API, consent screen drive.appdata, OAuth client "TVs and Limited Input devices", keys GOOGLE_DRIVE_CLIENT_ID / _SECRET in the app). Untested against real Google. Phase 2 native sign-in later.
- [ ] OVHcloud: create a read-only token, add the 4 values in the app, then test `ovh_request GET /me`. Open question: VPS or Public Cloud.
- [ ] Hosts: run "Set up this server" on the web VPS and report.
- [ ] Decisions: branch protection ruleset on main (stops merging with red CI); Grok video (blocked by zero data retention even with an R2 bucket; the Antigravity worker was advised against, consumer-terms risk) drop or pursue; Gemini free tier is 20 requests a day, so use a paid key or accept fail-fast; GitHub token with `delete_repo` if repo delete is wanted; claude-mem via OmniRoute (offered, undecided).

## Decisions
- 2026-11-16: Default model is Opus (settings.json), Fable via /model or Agent model only when Opus is not enough; the task breakdown is shown and work starts without waiting; PLAN.md stays the only task list (not the claude-mem list). Master CLAUDE.md replaced by Fable's reviewed version; "ask first" tools (DNS, mail, OVH, X posts) now need approval each time.
- 2026-11-16: Queue: all the time, maximum usage, self-merge on green CI, stop only for a direct question or the hard-stop list.
- 2026-11-16: Jonathan: go with every open task, but iOS is paused (he uses Android only).
- 2026-11-16: Model rule: lead orchestrator, cheapest model that is reliable per task, Opus for every top-tier task, Fable 5.2 only when Opus is not enough. This plan lists a model for each open task.
- 2026-11-16: PLAN refreshed from GitHub, not from memory. Finished work is one line below, not re-listed.
- 2026-11-04: Gemini worker signs in with a Google account (Google's link, code pasted in the app) and stops fast on quota/overload errors (#227); Grok worker kind and account sign-in (#224-#226): image requests go to the Grok worker (`delegate(role="images")`). Grok video is blocked by ZDR; R2 test credentials were revoked and removed.
- 2026-10-28: Home-screen widgets (#212), notes (#213). 2026-10-24: repo audit, all 20 findings fixed (#210); left by design: tail/keys/attach don't ask the chat PIN, Git LFS worker commits are full files.
- 2026-10-21 to 10-23: UI refresh finished on all screens (#196-#206); adaptive layout (#170), DataStore, MainViewModel split (#172), toolchain AGP 9 + R8 (#167).
- 2026-10-14 to 10-16: Push alerts on the new Firebase project (#185-#187), natural voice Kokoro (#179-#189, #194), OVHcloud service (#192), update bar progress (#193), per-chat Permissions screen (#178).
- 2026-10: In-app migrate wizard and fixes (#120-#123), Drive backup (#136), Hosts (#132-#158), Codex and Gemini workers (#124-#129), knowledge pack 37 skills with U.S. law skills (#169, #174), add-server by password (#215), server purpose (#216), chat upgrades (#209, #217-#219, #222).
- 2026-10-01/03: Plugin lights, cluster design (workers per chat, branch per task, detached runs, hand-back at 95%, role routing, sandbox via bubblewrap); push = Claude hooks to FCM; iOS port in `ios/` (SwiftUI, macOS CI, unsigned IPA).
- 2026-10-01: Busy screens use pill tabs; status dots amber needs you, green working, grey idle; chat PIN "don't ask again" tied to boot count; Claude's folder slug maps every non-alphanumeric to `-`; `api_ok` pipes JSON (argv limit 128 KB); the usage endpoint rate-limits, so lookups share one file cache.
- Details for each shipped feature live in CLAUDE.md "Project notes" and in docs/.

## Known issues
- Draft lost after >30 s in a file picker (app re-locks); env/credentials files have no cross-process lock (writes are atomic); `login-claude-code` can report success when a running session refreshes its token.
- Worker run that hits a usage limit mid-task fails (re-delegate by role); the app's usage bars and the server share one rate-limited endpoint.
- Server tests don't gate merges (no branch protection): always check CI before merging.
- Plugin-provided MCP servers can only be removed by disabling their plugin; project-scope MCP servers can't be signed in from the app (use /mcp in that project).
- Codex can't make images; Gemini on a free key hits the daily quota.
- Notifications on the phone: vibrate-without-showing was addressed by the `alerts` channel (#79); confirm once on the phone. Session alerts only fire while the phone is unlocked (owner's choice).
