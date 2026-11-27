# Inbox, morning briefing, lock-screen actions (design, phase 1)

Status: design, not built. Three features, each shippable alone, in the order below. Style and rigor follow
`docs/queue-design.md`; everything marked `unverified` must be checked on a real server or phone before it ships.

Goal: one place that says what needs the owner right now, one short summary each morning, and the two most
common answers (approve a permission prompt, merge a green PR) from a notification without opening a terminal.
Nothing here gives the server or the phone a power it doesn't have today: approving = the Tail keys, merging =
the owner's own `gh` login, both now behind the chat PIN.

## Facts this design builds on (checked in the code)

- `SCRIPT_API=54` (claude-setup.sh), app `Updates.MIN_SCRIPT_API = 52`, per-feature gates like
  `QUEUE_MIN_SCRIPT_API = 53` (`api/Queue.kt`) with `supported()` in the controller (`ui/vm/Queue.kt`). New UI uses
  the same per-feature constant, so old servers just hide it.
- `session_state_json` reads a pane with `tmux capture-pane` (no typing) and sets `waiting` from `WAIT_RE`
  (`Enter to confirm|\(y/n\)|^ *❯ [0-9]+\. |Auto mode lets Claude`). `sessions` already returns it per session.
- `keys <session> <key>…` (the app's Tail keys) sends only `KEYS_ALLOWED` keys with `tmux send-keys -t "=$sess:"`,
  logs `chat_log <sess> keys`. It needs NO PIN today (see Risks).
- `check_pin` (first stdin line, 5 tries then a 30 min lock, flock'd) guards chat actions and `queue-on/-star`.
- `queue_status_json` gives per starred chat `state` (`question`, `needs_you`, `error`, `stopped`, …) and `note`,
  which is a fixed string ("asked you", "session gone", "interrupted", …), never reply text.
- `claude-push` (python, stdlib) sends FCM **data-only** messages `{title, body, channel, session, kind}`; it runs as
  the Notification hook (`notification_type` `permission_prompt` is already in `test-push.py`) and the Stop hook.
  There is no `--alert` mode yet (the queue's pending push TODO).
- App: `PushService.onMessageReceived` -> `SessionWatcher.notify(ctx, id, title, text, channel)`, which already sets a
  public lock-screen version ("A session needs you") and opens `MainActivity` with requestCode 0 +
  `FLAG_UPDATE_CURRENT` (fine for one tap target, wrong for per-item extras: see P4).
- App lock: `MainActivity` uses `BiometricPrompt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`). `ChatPinVault` keeps
  the chat PIN Keystore-encrypted for N minutes, cleared on reboot.
- Scheduled jobs use the user crontab via `cron_line_set`. minSdk 26, targetSdk 36.
- Test stubs exist for `gh`, `crontab`, `curl`; `test-push.py` runs `claude-push` against a fake Google.

## 1. Inbox

### Item kinds (server decides; app only renders)

| kind | source | one-line `text` (names/status only) | actions |
|---|---|---|---|
| `question` | queue state `question` | "asked you a question" | open, dismiss |
| `parked` | queue state `needs_you`, `error`, `stopped` | the queue `note` (fixed strings) | open, dismiss |
| `prompt` | any session pane with `waiting` | "wants to use <Tool>" or "is waiting for an answer" | open, approve*, deny*, dismiss |
| `pr` | open PR, green, mergeable, own repo | "#<n> ready to merge · <k> checks green" | merge*, open, dismiss |
| `build` | newest completed run of a workflow on the default branch failed | "<workflow> failed on <branch>" | open, dismiss |
| `login` | Claude / GitHub not signed in, a worker with `signed:false` | "GitHub sign-in failed" | open, dismiss |

`*` = needs `act` (P3); until then the app shows only open/dismiss. Approve/deny only when the prompt is a
permission prompt (pane matches `PERM_RE`, below) AND a pending `tool_use` id was found; otherwise open only.
PR `text` deliberately has no title in the server answer's one-liner; the title travels separately as `title`
(GitHub metadata of the owner's own repo, truncated to 80, shown in the app only, never in a push).

### Detection details

- Prompt: reuse `session_state_json`'s capture (last 15 lines). `PERM_RE='Do you want to (proceed|make this edit|
  create|run)|Allow .* to'` (`unverified`: wording from current Claude Code; check on a real pane). Tool name = the
  `name` of the newest `tool_use` block in the session's transcript (`chat_transcript`) that has no matching
  `tool_result`; read with `tail -n 200 | jq`, never sent anywhere but the `name` field.
- PR / build, per repo: repos = github.com `origin` of each git dir in `$PROJECTS_DIR` whose owner is `$GH_USER` or
  `$DEFAULT_OWNER` (max 30). Per repo, `timeout 8`:
  `gh pr list -R o/r --state open -L 20 --json number,title,headRefOid,isDraft,mergeable,isCrossRepository,baseRefName,statusCheckRollup,updatedAt`
  `gh run list -R o/r -b <default> -L 10 --json databaseId,workflowName,status,conclusion,headSha,createdAt`
  Default branch = `git symbolic-ref --short refs/remotes/origin/HEAD` minus `origin/`, else `main`.
- Green = rollup non-empty and every entry is a CheckRun with conclusion in SUCCESS/NEUTRAL/SKIPPED or a
  StatusContext with state SUCCESS; `mergeable == "MERGEABLE"` (UNKNOWN = not yet); not draft;
  `isCrossRepository == false` (no fork PRs, ever); base = default branch.
- Build: group runs by `workflowName`, take the newest with `status == "completed"`; failed = conclusion in
  failure/timed_out/startup_failure. A later green run of the same workflow clears it.
- Login: the checks `status` and `worker-list` already make (no new probes), run during the gh refresh.
- gh results and login state are cached in `inbox/gh.json` `{ts, repos:{…}, logins:{…}}` for 5 min. `inbox` uses the
  cache; if older than 5 min it refreshes inline under `flock -w 30 inbox/lock` (total cap 25 s, a repo that times
  out keeps its old entry and sets `stale:true`). After P5 the tick keeps it fresh, so calls are always fast.

### Ids, fingerprints, dismiss

- `key` = `kind|scope|discriminator` (prompt: `prompt|<session>`; pr: `pr|o/r|<n>`; build: `build|o/r|<workflow>`;
  queue: `question|<session>`; login: `login|<service or worker>`). `id` = `<kind>-` + first 12 hex of `sha1(key)`.
- `fp` (12 hex) = sha1 of the exact state an action would act on: prompt = session + pending `tool_use` id + the
  prompt lines; pr = head SHA + rollup conclusions; build = run `databaseId`; queue = `since`; login = state.
- Dismiss stores `id fp ts` in `inbox/dismissed` (TSV, pruned after 7 days). An item is hidden while its `id fp`
  pair is dismissed, so a new commit, a new run or a new prompt shows it again.
- State dir `~/.config/claude-launcher/inbox/` (700, files 600, writes tmp + `mv`).

### Server actions (SCRIPT_API 55)

| action | stdin | answer `data` |
|---|---|---|
| `inbox` | - | `{now, gh_age, stale, count, items:[Item]}` |
| `inbox-dismiss <id> <fp>` | - | `{count}` |

```json
{"id":"pr-3f9a0c1d2e4b","fp":"a1b2c3d4e5f6","kind":"pr","scope":"owner/repo","session":"repo",
 "text":"#246 ready to merge · 3 checks green","title":"chat-file: relative path means the project folder",
 "since":1764000000,"actions":["merge","open","dismiss"],"number":246,"run":null}
```

`session` = the tmux session for that project if one exists (for "open"), else null. `since` = when the state
started (pane: first seen, kept in `inbox/seen`; pr: `updatedAt`; build: `createdAt`; queue: `since`).
Items sorted: prompt, question, parked, pr, build, login; then oldest first.

Runner (`claude-launcher-api`): add `inbox inbox-dismiss` (and later `act briefing briefing-schedule`) to `KNOWN`.
```
inbox)          ((nargs == 0)) || deny "$sub" ;;
inbox-dismiss)  ((nargs == 2)) && [[ "${args[0]}" =~ ^[a-z]{2,10}-[0-9a-f]{12}$ && "${args[1]}" =~ ^[0-9a-f]{12}$ ]] || deny "$sub" ;;
```
`inbox-dismiss` needs no PIN: hiding an item can't do harm, and it reappears when anything changes.

Privacy: the answer carries session/repo names, tool names, PR numbers/titles, workflow names, states and times.
Never pane text, reply text, the QUESTION line, commands or file paths. The inbox never sends keys.

### App (P2)

- `api/Inbox.kt`: `INBOX_MIN_SCRIPT_API = 55`, `@Serializable InboxItem/InboxData`, `ACT_MIN_SCRIPT_API = 56`.
- `LauncherApi.inbox()`, `inboxDismiss(id, fp)`.
- `ui/vm/Inbox.kt` as `vm.inbox` (pattern of `QueueController`: `VmTools`, `scriptApi`, `supported()`,
  `clearServerState()`); `refresh()` rides the existing sessions refresh timer (quiet on failure); `count` StateFlow.
- Sessions screen gets a third pill tab first: **Inbox · General chat · Code chat** (`PillTabs`), shown only when
  `supported()`. No new bottom-bar tab (five is already the compact limit). Badge: `TabItem(…, Tab.SESSIONS, …)` gets a
  count badge (`BadgedBox`, the bar already imports it), hidden at 0; rail and expanded layouts the same.
- `ui/screens/InboxTab.kt`: one `CalmGroup`, a row per item: `StatusDot` (amber = prompt/question/parked/build/login,
  green = pr), project name, `text`, age ("12 min"), trailing tonal pill for the main action (Open / Merge / Approve),
  overflow for the rest + Dismiss (swipe to dismiss too). Empty state: "Nothing needs you." One filled accent button
  at most (none on the list). Pure mapping `itemRow(item, now): RowModel` so it is unit-testable.
- Open: prompt/question/parked -> `chats.openChat(session)` (general chats via `general-open` first, per the existing
  rule); pr/build -> browser `https://github.com/<scope>/pull/<n>` or `/actions/runs/<run>` (built in the app from
  validated fields, `REPO_RE`); login -> Connections tab.

## 2. Morning briefing

### Server: `briefing` (SCRIPT_API 57)

Built only from data that exists: `sessions`, the queue `log` and state, `git log` per project repo, the inbox
gh cache, the usage cache `usage-cache/_main.json`, and cheap health reads. `since` = `briefing/last` (ts of the
last briefing the owner saw or was pushed), default now - 24 h.

```json
{"since":1763950000,"now":1764036400,
 "headline":"6 tasks done · 1 question · 2 PRs to merge · 1 build failed",
 "sessions":{"total":7,"working":2,"waiting":1,"idle":4},
 "queue":{"enabled":true,"done":6,"questions":1,"limited_min":40,"per_chat":[{"session":"app","done":3}]},
 "repos":[{"repo":"owner/app","commits":5,"subjects":["Drive backup: run on IO",""],"build":"green","prs_ready":1}],
 "usage":{"five":42,"seven":61,"five_resets":"…","seven_resets":"…"},
 "health":{"disk_free_pct":38,"load1":0.4,"uptime_days":12,"scripts_ok":true,"logins_failed":["github"],
           "knowledge_age_days":3,"drive_backup_age_days":1},
 "inbox_count":4}
```

- Commits: `git -C <dir> log --since=@<since> --format=%s -n 3` + `rev-list --count`; subjects truncated to 80
  (own repo metadata, like the PR title; not chat text). Only repos with activity or a red build are listed.
- Queue counts: `log` lines since `since` grouped by event (`done`, `question`, `limited`).
- `briefing --seen` (arg) writes `briefing/last = now` when the app has shown it.

### Daily push (designs the queue's pending `claude-push --alert`)

- `claude-push --alert <kind> <session>`: title and body as one JSON line on stdin (`{"title":…, "body":…}`), sends
  the usual data message with `channel:"alerts"` (or `"finished"` for `briefing`) and the given `kind`. Same
  never-fail rule; prints `{"sent":N}`. Kinds: `briefing`, `queue` (question/needs_you/error transitions, from the
  queue tick), `pr`, `build` (from the tick below). Body = counts and names only (e.g. the `headline`).
- `briefing-schedule <HH:MM|off> [<tz>]`: stores `{time, tz}` in `briefing/schedule.json`; minutes must be 00/15/30/45.
  `tz` is the phone's IANA zone (e.g. `America/Chicago`, already inside the runner's character set), checked
  server-side with `TZ=$tz date` and `^[A-Za-z]+(/[A-Za-z0-9_+-]+){0,2}$`. Using the phone's zone avoids cron TZ
  differences and DST math.
- One crontab line `*/5 * * * * claude-setup.sh --tick` (`cron_line_set`, added when a schedule or PR/build alerts are
  on, removed when both are off). `--tick` under `flock -n inbox/tick.lock`: (1) refresh the gh cache if > 5 min;
  (2) push new `pr`/`build` items not yet in `inbox/pushed` (if `alerts.pr`/`alerts.build` on; default on once push is
  set up); (3) if `TZ=$tz date +%H:%M` >= `time` and `briefing/sent` != today (in `tz`): build, `--alert briefing`,
  write `briefing/sent`. Missed ticks (server down at 07:00) send at the first tick after, same day only.
- The queue tick calls `claude-push --alert queue <session>` on a transition into `question`/`needs_you`/`error`
  (one alert per transition; respects `push-done`-style per-session mute if the owner turned alerts off).

Runner:
```
briefing)           ((nargs == 0)) || { ((nargs == 1)) && [[ "${args[0]}" == --seen ]]; } || deny "$sub" ;;
briefing-schedule)  { ((nargs == 1)) && [[ "${args[0]}" == off ]]; } || { ((nargs == 2)) && [[ "${args[0]}" =~ ^([01][0-9]|2[0-3]):(00|15|30|45)$ && "${args[1]}" =~ ^[A-Za-z]+(/[A-Za-z0-9_+-]+){0,2}$ ]]; } || deny "$sub" ;;
```
(`:` is already allowed by the raw-string check, and `TOKEN_RE` has no `:` — so these two args are checked here and
the generic `TOKEN_RE` loop skips `briefing-schedule`, exactly like `push-register`. `+`/`_` in zones like `Etc/GMT+5`:
`+` is outside the raw charset, so those zones are refused; the app maps them to the nearest named zone or sends off.)

### App (P6)

- `ui/screens/BriefingScreen.kt`: large title "This morning", `headline` as the lead line, then `SectionLabel`s
  (Sessions, Queue, Repos, Usage, Server) with `InfoRows`/`CalmGroup`; repo rows open the repo in Projects. Reached
  from a `CalmGroup` row on top of the Inbox tab ("Briefing · since 18:02") and from the push tap (`kind:"briefing"`
  -> `WidgetLink`-style extra -> `vm.openFromWidget`-like route, validated). Shown -> `briefing --seen`.
- Settings › Notifications: "Morning briefing" switch + time picker (quarter hours), "Alert me about green PRs" and
  "…failed builds" switches. Hidden below `BRIEFING_MIN_SCRIPT_API = 57`.

## 3. Lock-screen actions

### Server: `act` (SCRIPT_API 56)

`act <id> <verb> <fp>`, verb `approve|deny|merge`, chat PIN as the first stdin line.
```
act)  ((nargs == 3)) && [[ "${args[0]}" =~ ^(prompt|pr)-[0-9a-f]{12}$ && "${args[1]}" =~ ^(approve|deny|merge)$ && "${args[2]}" =~ ^[0-9a-f]{12}$ ]] || deny "$sub" ;;
```
Steps, all inside `flock -w 20 inbox/act.lock`:
1. `check_pin` (lockout applies). `prompt-*` takes approve/deny only; `pr-*` takes merge only (else `bad_args`).
2. Find the item LIVE, never from the cache: prompt = scan current sessions' panes; pr = `gh pr view <n> -R o/r --json
   headRefOid,state,isDraft,mergeable,isCrossRepository,baseRefName,statusCheckRollup` for the repo/number recorded in
   `inbox/index.json` (written by `inbox`/`--tick`: id -> {scope, number|session}; ids are hashes, so the index is how
   the server maps back; an unknown id = `stale`).
3. Recompute `fp`; different -> `stale` ("This changed. Open the app to see it now."). Item no longer qualifies
   (prompt gone, PR red/draft/fork/not own repo/not mergeable) -> `stale` too.
4. `id fp` already in `inbox/used` (TSV, 7 days) -> `replayed`. Else append it BEFORE acting (single use even if the
   action then fails; the owner retries from the app with a fresh fp).
5. Act:
   - approve: re-capture the pane and re-check `PERM_RE` + fp immediately before `tmux send-keys -t "=$sess:" 1`
     (Yes, once). Never `2` ("don't ask again"), never text. deny: `Escape`. `unverified`: that `1` selects and
     confirms in the current prompt; check on a real pane (fallback: `Enter` when the cursor is on option 1).
     Through a shared `send_keys_safe <sess> <key>` factored out of `do_keys` (same allowlist, same `chat_log keys`).
   - merge: `gh pr merge <n> -R o/r --squash --match-head-commit <sha>` (the project's workflow squash-merges;
     `--match-head-commit` makes GitHub refuse if a commit landed after the check). Branch protection with a required
     check (queue design Q6) stays the real guard.
6. Log `time id verb result` to `inbox/acts.log` (no PIN, no text). Answer `{done:true, kind, session|scope}`; errors
   `stale`, `replayed`, `wrong_pin`, `chat_locked`, `merge_failed` (gh's first line, sanitized to 120 chars).

### Push for permission prompts (P3, claude-push)

On `Notification` with `notification_type == "permission_prompt"`: run `claude-setup.sh --prompt-item <session>`
(internal flag, not an API action; prints `{id, fp, tool}` or nothing) and send `kind:"permission"` with extra data
`{item, fp, tool, exp}` (`exp` = now + 600). Title "<session> needs you", body "Wants to use <tool>." Other
notification types keep today's `kind:"needs"`. PR pushes (P5) carry `{item, fp, repo, number, exp}`, body
"<repo> #<n> is green and ready to merge." No command, path, title or chat text ever goes through FCM.

### App (P4)

- `PushService`: for `kind` `permission`/`pr` (and only if `ACT_MIN_SCRIPT_API` is met per the last known status),
  validate every field (`id`/`fp` regexes above, session `PROJECT_RE`, repo `REPO_RE`, number digits, `exp` in the
  future and < now + 1 h) or fall back to a plain notification. Then `SessionWatcher.notify` gains optional actions:
  permission -> **Approve**, **Deny**; pr -> **Merge**; all keep tap-to-open. Each action is an explicit
  `PendingIntent.getActivity` to `ActionConfirmActivity` (not exported, `FLAG_IMMUTABLE`, requestCode = hash of
  `id+verb`, so items don't overwrite each other's extras). The lock-screen public version stays "A session needs
  you" with no buttons' details; `setVisibility(PRIVATE)`.
- Starting an activity from a notification on a locked phone makes Android ask for the device unlock first (no
  `showWhenLocked`). On API 31+ also `Action.Builder.setAuthenticationRequired(true)`.
- `ActionConfirmActivity` (small dialog-styled Compose activity): 1) `BiometricPrompt` with the app-lock
  authenticators; 2) loads live context over SSH: permission -> `tail <session> --lines 40` (the same pane text the
  Tail screen already shows) with the prompt block highlighted; merge -> `inbox` item (title, checks) ; 3) one filled
  button ("Approve once" / "Deny" / "Merge #246") + Cancel; 4) PIN from `ChatPinVault.load()` or a PIN field (never
  saved beyond the vault's existing rules); 5) `act`; result as a toast-like line, notification cancelled on success
  or `stale`/`replayed` ("Already handled"). Expired `exp` -> skip straight to "This is old, open the app".
- In-app inbox rows get the same verbs through the same `act` call (PIN via the existing vault/ask pattern).
- The app never decides from push text: everything shown before a tap comes from the server over SSH.

### Threat model

| threat | what it could do | mitigation |
|---|---|---|
| Someone holds the locked phone | tap Approve/Merge | activity needs device unlock; biometric again in the activity; server needs the chat PIN |
| Shoulder-surfing the lock screen | read what's pending | public version has no names; FCM data has names/tool only |
| Replay of an old notification or `act` call | approve a later prompt, merge a later commit | fp binds the exact prompt (`tool_use` id) / head SHA + checks; `id fp` single use; app-side `exp`; live recompute |
| Prompt changes between view and tap | approve the wrong thing | fp re-check inside the lock right before `send-keys`; mismatch = `stale` |
| Forged/abused FCM message (stolen sender key) | spoof a convincing prompt | push only triggers a UI; confirm screen shows live server text; server acts only on current state + PIN |
| Escalation through "don't ask again" | permanent allow rule from a phone tap | only key `1` or `Escape` are ever sent by `act` |
| Fork PR / foreign repo / fake status | merge outsider code | own-owner repos only, `isCrossRepository == false`, all checks green and non-empty, `--match-head-commit`, branch protection |
| Argument injection | run something else | fixed verbs, hash-shaped ids, runner regexes, no free text in argv |
| PIN brute force via `act` | guess the PIN | existing `check_pin` lockout (5 tries, 30 min) |
| PIN leak | reuse elsewhere | PIN only on SSH stdin; never in a notification, intent extra, log or FCM |
| Lost phone with unexpired vault PIN | act after unlock | needs device unlock + biometric; owner can remove the phone key (`authorized_keys`) |

## Tests

Server, `server/tests/test-api.sh` (stubs, private tmux, temp HOME, `CLAUDERC_NOW`):
1. Runner: `inbox x`, `inbox-dismiss pr-zz 123`, `act pr-<12hex> approve <fp>` with a `;`, `briefing-schedule 7:05 X`,
   `briefing-schedule 07:10 America/Chicago` (not a quarter) denied; good forms pass.
2. `inbox` with a pane showing a fake permission block + a transcript with a pending `tool_use` -> one `prompt` item,
   `text` "wants to use Bash", approve/deny offered; without `tool_use` -> open only; a planted secret word in the
   pane and transcript never appears in the answer (grep).
3. Queue states question/needs_you/error map to items with the fixed `note`.
4. gh stub: green own PR -> `pr` item; draft, fork (`isCrossRepository:true`), UNKNOWN mergeable, a FAILURE check,
   empty rollup, other owner -> none. Failed newest run -> `build`; newer green run -> gone. Cache: second call within
   5 min makes no gh call (stub counter); a timing-out repo keeps its old entry and sets `stale`.
5. Dismiss hides `id fp`; changed fp shows it again; prune after 7 days.
6. `act`: wrong PIN -> `wrong_pin` and no keys sent; right PIN -> pane got `1` (capture); same call again ->
   `replayed`; changed prompt -> `stale`; deny sends `Escape`; `pr-*` + approve -> `bad_args`; merge calls the gh stub
   with `--squash --match-head-commit <sha>`; a PR turned red between `inbox` and `act` -> `stale`, no merge.
7. `briefing`: counts from a fake queue log, git repo with 3 commits since `since`, usage cache; `--seen` moves `last`.
8. `--tick`: pushes a new green PR once (`claude-push` stub records calls), not twice; briefing sent at the first tick
   >= time in the given zone, once per local day; crontab line added/removed (crontab stub).
Python, `server/tests/test-push.py`: `--alert` sends the stdin title/body with the given kind; `permission_prompt`
sends `kind:"permission"` with `item/fp/tool/exp` (stub `--prompt-item`), others keep `needs`; bad stdin -> exit 0.
App unit tests (CI): `InboxTest` (JSON parsing incl. unknown kinds ignored, row mapping, age text, sort),
`PushActionTest` (field validation: bad id/fp/repo/expired -> plain notification; requestCodes distinct),
`BriefingTest` (parsing, headline fallback), gates (`supported()` false below each API).

## Build order (each its own PR; branch, PR, merge; app on CI)

| PR | scope | model | why |
|---|---|---|---|
| P1 | server `inbox`, `inbox-dismiss`, gh cache, runner rules, SCRIPT_API 55, tests 1-5 | Sonnet | standard bash/jq on existing helpers; well specified |
| P2 | app inbox tab, badge, models, `vm.inbox`, `InboxTest` | Sonnet | Compose screen in an existing pattern |
| P3 | server `act`, `send_keys_safe`, `inbox/index.json`, `--prompt-item`, claude-push `permission` kind, SCRIPT_API 56, tests 6 + push | Opus | security-critical (keys into panes, merges, replay); worth the reasoning |
| P4 | app notification actions, `ActionConfirmActivity`, inbox verbs, `PushActionTest` | Sonnet, Opus review | Android plumbing is standard; the trust boundary gets an independent read |
| P5 | `claude-push --alert`, `--tick` cron, PR/build pushes, queue transition alerts, `briefing`, `briefing-schedule`, SCRIPT_API 57, tests 7-8 | Sonnet | scheduling + aggregation on existing data; no new trust |
| P6 | app briefing screen, schedule + alert settings, briefing/pr push routing, `BriefingTest` | Sonnet | screen + settings in existing patterns |
| P7 | CLAUDE.md Project notes (state dirs, kinds, `unverified` results) | Haiku | short notes edit |

Before P3 merges: on the real server, verify `PERM_RE`, the `tool_use` id in the transcript while a prompt waits,
and that `1` approves once; drop or keep the `unverified` marks. P4 is checked on the owner's phone (locked and
unlocked, folded, dark mode); the release build must be a CI build.

## Risks and what could break

- Pre-existing: `keys` (Tail keys) needs no PIN, so anyone with the phone's SSH key can already answer prompts. `act`
  is PIN-gated; gating `keys` too would change the Tail screen (out of scope, flag for later).
- Claude Code changes prompt wording or option order: `PERM_RE` stops matching -> items fall back to "open" (fail
  closed); if option `1` ever meant something else, `act` would send it. Mitigation: the confirm screen shows the live
  pane, and P3 adds a test fixture copied from a real prompt; re-check after Claude Code updates.
- `gh` rate limits with many repos: 30-repo cap, 5 min cache, per-repo timeout; stale data is marked.
- `inbox` on a server without the tick does gh calls inline: slow first open (up to 25 s); the app shows the cached
  list immediately next time.
- Cron with `--tick` every 5 min adds load: one flock'd run, gh calls only when the cache is stale.
- Notifications: wrong requestCode handling would mix items (tests cover it); Android 14+ may block
  activity starts from background — we only start from a user tap on a notification, which is allowed.
- FCM payload grows by tool name, ids, repo/number: still names only; documented here for the public repo.
- Old servers: every new UI piece checks its own `*_MIN_SCRIPT_API`; `Updates.MIN_SCRIPT_API` is NOT raised, so the
  rest of the app keeps working on 54.
- Briefing zone check: zones with `+` are refused by the runner charset (handled in the app).

## Open questions for Jonathan

1. Lock-screen Merge: allowed for every one of your repos, or only ones you pick in Settings?
2. Morning briefing time: is 7:00 your time a good default (you can change it)?
3. Should the Tail keys also need your chat PIN (safer, one more step), or leave them as they are?
