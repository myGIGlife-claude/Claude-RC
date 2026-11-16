# Starred-chat queue (design)

Status: design, not built. Covers PLAN tasks T14 and T15 and the queue half of T16.

Goal: chats the owner stars keep working by themselves, one PLAN.md task per turn, round-robin, whenever the
owner's Claude account has usage left. Nothing new is allowed by the queue: a turn has exactly the powers of a message
the owner typed in that chat, and every "ask first" stop still stops it.

## Facts this design builds on (checked in the code)

- Every code chat is a tmux session running `claude` under the owner's own login (`~/.claude/.credentials.json`).
  So all starred chats share ONE account; workers (`clauderc-team`) have their own and are gated there already.
- `do_chat_send` (claude-setup.sh) pastes a message into a session (tmux load-buffer + bracketed paste + Enter,
  retries the Enter for long pastes). `chat_transcript` finds the live `*.jsonl`. `session_state_json` and
  `do_chat_history` tell busy (`esc to interrupt`) and waiting (`WAIT_RE`) from the pane.
- Usage: the undocumented `api/oauth/usage` endpoint rate-limits (429), so `clauderc-team` keeps one shared file cache
  per account in `~/.config/claude-launcher/usage-cache/<worker>.json` = `{ts, retry_until, five, seven}`.
- `claude-launcher-api` allows only `[A-Za-z0-9 ._/:=-]`, max 400 chars, each argument `TOKEN_RE`, per-action arity.
- Secrets and free text go on stdin; the chat PIN is the first stdin line (`check_pin`, 5 tries then locked).
- Scheduled jobs already use the user crontab (`cron_line_set`, knowledge refresh, Drive backup).
- `claude-push` already runs as the Stop hook; `chat_log` records each `chat-send` with a timestamp.

## Shape: a one-minute tick, not a daemon

`claude-queue` (new, `~/.local/bin`) is a 3-line wrapper: `flock -n <queue>/lock claude-setup.sh --queue-tick`.
A crontab line runs it every minute (`cron_line_set`, added on the first star, removed when the star list is empty).
Each tick reads the state, does at most a few cheap checks and sends, writes the state, exits. Why:
- survives reboots and crashes with no systemd unit and no sudo; nothing to keep alive;
- all logic lives in claude-setup.sh next to the helpers it reuses (paste, transcript, pane state, PIN, jq);
- a tick is a plain function call, so test-api.sh drives it step by step with a fake clock (`CLAUDERC_NOW`).
One minute of latency is irrelevant for turns that take 10-60 minutes.

## State (all under `~/.config/claude-launcher/queue/`, dir 700, files 600)

- `queue.json` (owner settings, written by the actions):
  `{"enabled":true, "parallel":1, "max_5h":90, "max_7d":95, "max_turn_min":240, "stars":["proj-a","proj-b"]}`
  `stars` order = round-robin order (new stars go to the end).
- `state.json` (written only by the tick and by `queue-star`), one entry per starred session:
  `{"proj-a":{"state":"idle","since":1763300000,"repo":"/home/u/projects/a","turn":{"started":…,"transcript":"…jsonl",
  "lines":1234},"fails":0,"next_try":0,"turns":17,"note":"",
  "plan_sum":"<sha1 of PLAN.md when it said nothing to do>"}, "_cursor":"proj-a", "_limited_until":0}`
- `log`: TSV `time  session  event  detail` (events: sent, done, question, nothing, needs_you, limited, resumed,
  fail, skip, on, off, star, unstar). Session names and states only, never chat text. Rotated at 1 MB (`log.1`).
- `lock`: flock target shared by the tick and every `queue-*` action (no lost updates). Writes are tmp + `mv`.
- Usage cache: `usage-cache/_main.json` for the owner's account (leading `_` can't collide with `WORKER_RE` names),
  same format as `clauderc-team` plus `five_resets`/`seven_resets` (ISO `resets_at` from the endpoint). Extra keys
  are ignored by `clauderc-team`, so it stays compatible.

Chat states: `idle` (eligible), `running`, `question` (last reply has a `QUESTION:` line), `nothing` (said
`QUEUE: nothing to do`), `needs_you` (pane shows a prompt: a permission ask, a hard stop), `limited` (usage),
`backoff` (after a failure, until `next_try`), `error` (3 failures in a row), `stopped` (no tmux session),
`unsafe` (bypass permissions mode; see Security).

## One tick

1. `enabled` false or no stars: exit (`log off` once).
2. Usage of `_main`: reuse the cache if `ts` < 120 s old and not in `retry_until`; else call the endpoint the way
   `cluster_account` does (token via `curl --config -`, never argv; expired token = leave alone, treat as unknown) and
   rewrite the cache. `room` = five < `max_5h` AND seven < `max_7d`. Unknown (null) counts as room, but then at most
   one turn runs at a time whatever `parallel` says.
3. Update every `running` chat (see Completion below).
4. If no room: set `_limited_until` = the reset time of whichever window is over (+120 s; unknown reset = now + 15 min),
   log `limited` once, exit. A tick after that time starts again by itself: that is "resume right after a reset".
5. Free slots = `parallel` - running. Walk `stars` from the entry after `_cursor`, wrapping once. A chat is eligible
   when ALL hold: state `idle` (or `backoff`/`limited` whose time has passed, or `nothing` whose PLAN.md hash changed,
   or `question` whose latest reply no longer has the marker); its tmux session exists (else `stopped`); pane not
   busy and not waiting; no owner activity (no `chat_log` send for it in the last 20 min and no tmux client attached:
   `tmux list-clients -t`); permission mode is not bypass; no running chat has the same `repo`.
   `repo` = `git -C <session dir> rev-parse --show-toplevel` (else the session dir), so two sessions in one checkout or
   its subfolders never run together. General chats (`chat-<id>`) can't be starred.
6. Send the turn message (below) with the shared paste helper, record `turn.started`, transcript path and its line
   count, set `running`, move `_cursor`, log `sent`. Then stop: at most `parallel` sends per tick.

### Turn message (fixed text, in claude-setup.sh; public repo, so no owner name)

```
[cLaudeRC queue] Automatic turn. Follow "Auto-continue queue" in ~/.claude/CLAUDE.md: Session start, then ONE task
from PLAN.md, finished completely (tested, committed, PLAN.md updated). All "Always ask first" stops still apply.
If you need the owner, put the question last, on a line starting with QUESTION:. If nothing is left, reply exactly:
QUEUE: nothing to do
```

Every chat gets the same text: nothing from one chat (names, replies, paths) ever goes into another.
After a `limited` stop in the middle of a turn the chat gets `[cLaudeRC queue] Usage window reset: continue the task
you were on.` instead (state carries `resume:true`).

### Completion detection (step 3)

A `running` chat is done when, on two ticks in a row:
- the pane is not busy (`esc to interrupt` absent) and not waiting (`WAIT_RE`);
- the transcript has an `assistant` record after line `turn.lines` and the newest assistant record has
  `message.stop_reason == "end_turn"` (`unverified`: check the field on a real transcript first; fallback is the
  Stop-hook stamp below);
- the transcript file was not modified for 90 s (background subagents and task notifications wake the session again).

Then read the text of the newest assistant reply (same jq as `chat-history`):
- a line matching `^QUESTION:` -> `question`; a line equal to `QUEUE: nothing to do` -> `nothing` (store PLAN.md sha1;
  re-check also after 24 h); otherwise `idle`, `turns+1`, `fails=0`, log `done`.
- "starts with" vs "last line": CLAUDE.md asks for the question LAST; we match any line so both work.

Other outcomes while `running`:
- pane waiting -> `needs_you` (the queue never sends keys, never answers a prompt); cleared when the pane is no longer
  waiting and the chat is idle again (the owner answered).
- pane shows the usage-limit banner (`usage limit|limit reached|resets [0-9]`, matched loosely, `unverified`) ->
  `limited` with `resume:true`; also sets `_limited_until`.
- not busy within 90 s of the send and no new transcript line -> failure (the paste did not land).
- busy longer than `max_turn_min` -> log `fail overrun`, free the slot, leave the chat alone (it stays busy, so it is
  not picked again until it settles).
- session gone -> `stopped` (claude-autostart restores sessions after a reboot; the queue never starts one).
Failures: `backoff` 15 min, then 60 min, then `error` (shown in the app; un-star and star again to reset).

Optional fallback if `stop_reason` proves unreliable: a 5-line `Stop` hook entry (in `claude-push`, which already runs
there) touches `queue/stop/<session>`; "done" then = stamp newer than `turn.started` + not busy + 90 s quiet.

## Usage thresholds

- Defaults: start no new turn at >= 90 % of the 5-hour window or >= 95 % of the weekly window. A turn already running
  is never cut off; Claude's own limit stop is handled as `limited` above.
- One account for all starred chats, so `parallel` (default 1, max 3) is the real cost knob: 2-3 parallel turns empty a
  5-hour window much faster but use more of it.
- Cache TTL 120 s and the endpoint's `Retry-After` are honoured, so the tick never hammers the endpoint (one call per
  2 min at most, shared with the cluster view).

## Security

- Starring = letting the server type into that chat unattended, the same power as `chat-send`, so `queue-star ... on`
  and `queue-on` need the chat PIN (first stdin line, `check_pin`). Turning things OFF (`queue-off`, `queue-star ... off`)
  needs no PIN: the kill switch must always work, and stopping can't do harm.
- Runner (`claude-launcher-api`): new names in `KNOWN`; arities: `queue-status`, `queue-on`, `queue-off`: 0 args;
  `queue-log`: 0 args or `--lines N` (1-3 digits); `queue-star`: `<session> on|off` with `PROJECT_RE`. No free text in
  argv; all characters already inside the allowed set.
- The tick sends only the fixed text above; it never sends keys, never answers a prompt, never runs a command in a chat.
- Chats in `bypass permissions` mode are refused (`unsafe`): with no prompts, the hard stops would not stop anything.
  `auto` mode is allowed if T16 confirms `permissions.ask` rules still prompt in auto mode (`unverified`).
- Hard stops are enforced by the session's own permissions (T16: `permissions.ask` for DNS/mail/public posts, delete,
  production deploy tools; branch protection with a required CI check so a red PR cannot merge, `gh pr merge --auto`).
  The queue's part: a prompt makes the chat `needs_you`, and it stays parked until the owner acts.
- Isolation: the tick reads only its own state, each starred chat's pane tail and transcript tail (the same reads
  `chat-history` does), and the usage cache. The log and `queue-status` carry names and states, never chat text.
  One repo, one running turn.
- Files 600 under a 700 dir; the usage token never touches argv or the log.

## API actions (SCRIPT_API 53, or the next free number when built; app `MIN_SCRIPT_API` to match)

| action | stdin | answer `data` |
|---|---|---|
| `queue-status` | - | `{enabled, parallel, max_5h, max_7d, usage:{five, seven, five_resets, seven_resets}, limited_until, chats:[{session, state, since, note, turns, next_try, running_min}]}` |
| `queue-on` | PIN | same as `queue-status` |
| `queue-off` | - | same as `queue-status` |
| `queue-star <session> on\|off` | PIN (on only) | `{session, starred}`; errors `invalid_name` (no such session, general chat), `unsafe_mode` (bypass) |
| `queue-log [--lines N]` | - | `{lines:[{ts, session, event, detail}]}` (default 100, max 500) |

`sessions` gains `"starred": true|false` per session so the list shows stars without a second call.
`queue-off` does not interrupt a running turn (it finishes its task); the app offers "Stop running turns too", which
calls the existing `chat-interrupt` per running chat (PIN).

## App

- Chat top bar (`ChatScreen.kt`): a star `IconButton` before Workers (`Icons.Filled.Star` / `Icons.Outlined.StarBorder`,
  primary tint when on, content description "Add to auto-continue queue" / "Remove from queue"). Hidden for general
  chats, a locked chat and old servers. A small state line under the title when starred ("queue: running",
  "waiting for you", "nothing to do", "paused: usage until 14:05").
- Sessions › Code chat tab: one `CalmGroup` row at the top "Auto-continue queue" with a `Switch` (the kill switch),
  usage `5h 62% · 7d 41%`, and "Log" opening a sheet with `queue-log`. Starred rows show a star and a state chip
  (amber = `question`/`needs_you`/`error`, green = `running`, grey = the rest; the existing `claudeHealth` colours).
- `ui/vm/Queue.kt` as `vm.queue` (pattern of the other groups: `VmTools`, `clearServerState()`); models in
  `api/Models.kt`; calls in the API class; the PIN comes from the chat PIN vault like chat actions.
- Push (later, optional): a `question`/`needs_you`/`error` transition can reuse `claude-push` for an alert.

## Test plan (server, `server/tests/test-api.sh`, stubs, private tmux, temp HOME)

Fixtures: two or three tmux sessions in temp project dirs (two sharing one git repo), each running `cat` or `sleep`
(the pane text is what we control: `tmux send-keys` a fake "esc to interrupt" line to fake busy); a hand-written
transcript `*.jsonl` per session under `$HOME/.claude/projects/<slug>/`; `usage-cache/_main.json` written with a fresh
`ts` so no curl runs (plus one case with the curl stub). Fake clock: `CLAUDERC_NOW`. Check pastes with
`tmux capture-pane`.
1. Runner: bad args denied (`queue-star x maybe`, extra args, a `;`), good ones pass (test-sshd `--app` path optional).
2. `queue-star on` needs the right PIN; `off` works without; general chat and bypass mode refused; `sessions` shows star.
3. `queue-on`/`queue-off`; disabled tick sends nothing; crontab line added on first star, removed on last un-star
   (crontab stub).
4. Tick sends the fixed text to the first eligible chat only; the second chat in the same repo is skipped while the
   first runs; the third (other repo) goes when `parallel` = 2.
5. Completion: transcript gains an `end_turn` reply + 90 s quiet (clock) on two ticks -> `idle`, `turns` = 1, cursor
   moves on (round-robin order holds over 4 ticks).
6. Markers: reply with a `QUESTION:` line -> `question`, skipped; a newer reply without it -> eligible again.
   `QUEUE: nothing to do` -> skipped until PLAN.md changes.
7. Usage: five = 91 -> no send, `limited_until` = `five_resets` + 120; clock past it -> sends. Limit banner mid-turn ->
   resume message, not the turn message.
8. Owner activity: a fresh `chat_log` send entry or an attached client -> skipped.
9. Failure: paste never lands -> backoff 15 min, 60 min, then `error`; star off/on resets.
10. Log: only names/events, rotation at 1 MB; no transcript text in `log` or `queue-status` (grep for a marker word
    planted in a reply).
App: unit tests for the status/log JSON parsing and the chip mapping (`QueueTest`), build on CI as usual.

## Build split (each Sonnet-sized, disjoint files where possible, in order)

- Q1 Paste helper (server, Sonnet): move the paste+Enter part of `do_chat_send` into `chat_paste <sess> <msg>`; no
  behaviour change. Done: test-api.sh green, `chat-send` cases unchanged.
- Q2 Queue actions (server, Sonnet): state dir, `queue-status/-on/-off/-star/-log`, `starred` in `sessions`, runner
  allowlist, crontab line, SCRIPT_API bump + comment line. Done: tests 1-3 and 10 pass.
- Q3 Tick (server, Sonnet; Opus reviews): `--queue-tick`, `server/claude-queue` wrapper, installed by `install.sh`
  (file list), usage cache read/write, eligibility, completion, markers, backoff, limit/resume, log. Done: tests 4-9
  pass; one manual run on the real server with two throwaway projects (first verify `stop_reason` and the limit-banner
  wording on a real transcript; drop or keep the `unverified` marks).
- Q4 App data (Sonnet): models, API calls, `vm.queue`, `MIN_SCRIPT_API`. Done: `QueueTest` passes on CI.
- Q5 App UI (Sonnet): star button, state line, queue row with kill switch, chips, log sheet, per `docs/mockups/
  ui-refresh.html`. Done: CI build green; checked on the owner's phone (folded and unfolded, dark mode).
- Q6 Guard rails (Opus, T16): `permissions.ask` coverage review, branch protection + required check, auto mode
  question. Needs the owner's OK for repo rules.
- Q7 CLAUDE.md notes (orchestrator): add the queue's file layout and gotchas under Project notes.

## Open questions for the owner

1. Weekly reserve: stop new turns at 95 % of the week (default), or keep more for your own chats (e.g. 80 %)?
2. How many queue turns at once: 1 (default, cheapest and safest), 2 or 3?
3. Should the queue also run chats in "auto" permission mode, or only default/accept-edits (auto is allowed only if
   ask rules still prompt there)?
4. A starred chat whose session is not running: leave it (default), or have the queue start it again?
5. Push alert when a starred chat asks a QUESTION or hits a stop: yes or no?
