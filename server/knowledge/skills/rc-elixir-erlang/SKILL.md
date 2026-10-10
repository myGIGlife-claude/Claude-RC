---
name: rc-elixir-erlang
description: Current Elixir (1.20) and Erlang/OTP (27-29) practice: BEAM processes, OTP behaviours, supervision, Phoenix 1.8, LiveView 1.x, Ecto, Oban, Req/Finch, Bandit, releases, clustering, telemetry, ExUnit/Mox, Credo/Dialyzer, Sobelow. Use when writing or reviewing *.ex, *.exs, *.heex, *.erl, mix.exs, rebar.config, config/runtime.exs, Dockerfiles for releases, or when advice might be Distillery/HTTPoison/pre-1.0 LiveView era.
---
# Elixir and Erlang/OTP  (as of 2026-10)

> Facts are dated (see Sources). Confirm the primary source before pinning a version. Anything marked (unverified) is a lead, not a fact.

## Currency check
- **Elixir 1.20** (released 2026-06-03; latest patches 1.20.4 / 1.19.6 / 1.18.5 on 2026-08-28). Support: 1.20 gets bug fixes + security;
  1.16-1.19 get security patches only. A new minor arrives roughly every 6 months (1.19 was 2025-10-16).
- **Erlang/OTP 29** (29.0 2026-05-13; latest 29.1.1 2026-09-22). OTP supports the **last 3 majors**: 29, 28 (28.5.0.7), 27 (27.3.4.18). OTP 26 and older: no fixes.
- Compatibility: Elixir 1.20 needs OTP 27-29; 1.19 needs OTP 26-28 (28.1+ for 28); 1.18 needs OTP 25-27.
- What shipped in the last ~18 months:
  - **Elixir 1.20**: "gradually typed language": every program is type-checked by inference (set-theoretic types, `dynamic()`), no annotations;
    guards, `tuple_size`, `is_map_key` and pattern matching refine types; warns about verified bugs, dead code and redundant clauses.
    Faster parallel compile. `elixirc_options: [module_definition: :interpreted]` option. User-written type signatures are NOT there yet.
  - **Elixir 1.19**: type checks protocol dispatch (e.g. interpolating a non-`String.Chars` value) and anonymous functions; lazy module loading
    (2x+ faster compiles); `MIX_OS_DEPS_COMPILE_PARTITION_COUNT=N` compiles deps in parallel (up to 4x); OpenChain-certified SBoMs.
  - **Elixir 1.18**: stdlib `JSON` module (`JSON.encode!/1`, `JSON.decode/1`, `@derive {JSON.Encoder, only: [...]}`); type checking of calls;
    ExUnit parameterized tests (`use ExUnit.Case, parameterize: [...]`) and `:group`; `mix format --migrate`.
  - **Elixir 1.17**: `Duration`, `Date/Time/DateTime.shift/2`, `Kernel.to_timeout/1`, `Process.set_label/1` (OTP 27+), `is_non_struct_map/1`.
  - **OTP 29**: experimental native records (`-record #name{}`); compiler warns on old-style `catch` and `and`/`or`; `is_integer/3` guard;
    `io_ansi`, `graph` modules; consistent map iteration order; **TLS hybrid PQ key exchange (ML-KEM-768 + X25519) by default**;
    **SSH daemon has shell/exec/sftp disabled by default** (enable explicitly); cwd moved to the end of the code path; Secure Coding guide.
  - **OTP 28**: priority messages (`alias([priority])`), strict generators `<:-`, zip generators `&&`, `erlang:hibernate/0`, PCRE2 regex
    (stricter: some old regexes now fail to compile), TLS 1.3 15-25% faster, `-nominal` types.
  - **OTP 27**: `json` module, `proc_lib:set_label/1`, triple-quoted strings and sigils, `-doc` attributes, `maybe` on by default, `tprof`, `trace` sessions.
- Ecosystem (hex.pm, 2026-10-09): Phoenix **1.8.15** (1.8.0 2025-08-05); LiveView **1.2.12** (1.2.0 2026-06-10, 1.1.0 2025-07-30, 1.0.0 2024-12-03);
  Ecto 3.14.2 / ecto_sql 3.14.0; Postgrex 0.22.4; Oban 2.24.1; Req 0.7.5 (0.8.0-rc.0 out); Finch 0.24.0; Mint 1.11.0; Bandit 1.12.5;
  Plug 1.20.3; Credo 1.7.19; Dialyxir 1.4.8; Sobelow 0.16.0; Mox 1.3.2; StreamData 1.4.0; Broadway 1.3.0; libcluster 3.5.0 (2025-01);
  Igniter 0.8.4; Nerves 1.15.0 (2.0 in pre-release); Gleam 1.19.1 (2026-10-07).

### Older versions (legacy projects)
- Stay inside the project's existing Elixir/OTP pins (`.tool-versions`, `mix.exs` `elixir:`); do not upgrade unless asked.
- Elixir < 1.18: no `JSON` module, use the `Jason` dep already there. < 1.17: no `Duration`/`shift`/`to_timeout`. < 1.15: no `Logger` handlers config.
- OTP < 27: no `json`, no process labels. OTP < 28 TLS lacks PQ default; < 29 the SSH daemon still allows shell/exec by default (disable it yourself).
- Phoenix 1.7 apps: no scopes, password-based `phx.gen.auth`, nested `app.html.heex` layout, Tailwind 3 `tailwind.config.js`. Keep that style
  in an existing 1.7 app; do not half-port 1.8 generators into it.
- LiveView 0.x (< 1.0): `phx-` bindings mostly the same, but `phx-feedback-for` was removed in 1.0.0-rc.0 in favour of `used_input?/1` (compat shim provided); tests used Floki.
- Ecto < 3.13: `Repo.transaction/2` (fine there); no `Repo.transact`, no `all_by`.

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| Distillery / exrm releases | `mix release` (+ `mix phx.gen.release --docker` in Phoenix) | Elixir 1.9 |
| HTTPoison/Tesla+hackney as default HTTP client | `Req` (on Finch/Mint); Finch directly for hot paths | Phoenix gens use Req |
| Poison; `Jason` in brand-new code on 1.18+ | stdlib `JSON` (Jason still fine where it exists, e.g. Phoenix `:json_library`) | Elixir 1.18 |
| Cowboy via `plug_cowboy` in new apps | `Bandit` (`Bandit.PhoenixAdapter`) | Phoenix 1.7.11 (2024-02) |
| `Repo.transaction(fn -> ... end)` | `Repo.transact(fn -> {:ok, x} end)` returns `{:ok, _}`/`{:error, _}` | Ecto 3.13 |
| `unless cond do` | `if !cond do` (`mix format --migrate` rewrites) | Elixir 1.18 soft-deprecated (no hard deprecation yet) |
| `'charlist'` single quotes | `~c"charlist"` | Elixir 1.15+ (migrate) |
| `<%# comment %>` in EEx/HEEx | `<%!-- comment --%>` | Elixir 1.18 deprecated |
| `Tuple.append/2` | `Tuple.insert_at(t, tuple_size(t), x)` | 1.18 deprecated |
| `<<x::size(y)>>` pattern with outer var | `<<x::size(^y)>>` | 1.20 deprecated |
| `config :logger, backends: [...]`, `Logger.enable/disable` | `:default_handler` config / `:logger_backends` pkg; `Logger.put_process_level` | 1.15/1.19-1.20 |
| `mix do a, b` | `mix do a + b` | 1.19 deprecated |
| Manual `:timer.hours(1)` everywhere | `to_timeout(hour: 1)`; `DateTime.shift(dt, month: 1)` | 1.17 |
| Phoenix nested layouts (`app.html.heex` auto-wrap) | single `root.html.heex` + `<Layouts.app flash={@flash}>` in templates | Phoenix 1.8 |
| `tailwind.config.js` + Tailwind 3 | Tailwind v4 CSS-first config in `assets/css/app.css` (+ daisyUI in generated apps) | Phoenix 1.8 |
| Password-first `phx.gen.auth`; `current_user` assigns | magic links default, `require_sudo_mode`, `%Scope{}` in `current_scope` | Phoenix 1.8 |
| `config` var in endpoint `init/2`; `:trailing_slash` | `Application.compile_env/3`; verified routes `~p` | Phoenix 1.8 |
| `Routes.page_path(conn, :x)` helpers | verified routes `~p"/path/#{id}"` | Phoenix 1.7 |
| LiveComponent per list row just for diffing | `:for={x <- @xs} :key={x.id}` (comprehensions are change-tracked) | LiveView 1.1 |
| Hooks in a big `app.js` | colocated hooks `<script :type={Phoenix.LiveView.ColocatedHook} name=".Name">` | LiveView 1.1 |
| `:colocated_js` config | `:colocated_assets` (also `ColocatedCSS`) | LiveView 1.2 |
| Floki in LiveViewTest; `fl-contains` selectors | LazyHTML (`:has()`, `:is()`); use `element(view, sel, text_filter)` | LiveView 1.1 |
| `phx-feedback-for` | `Phoenix.Component.used_input?/1` | LiveView 1.0.0-rc.0 |
| Large lists in assigns | `stream/3`, `stream_insert/4`, `phx-update="stream"` | LiveView 0.18+ |
| `Oban.Plugins.Cron`, `Oban.Plugins.Pruner` names | top-level `Oban.Cron`, `Oban.Pruner`, `Oban.Lifeline`, `Oban.Reindexer`, unified config (old names deprecated, still delegate) | Oban 2.24 |
| Req `run_finch`/`put_plug` steps | `Req.Finch` / `Req.Plug` adapters; Req 0.8 rc moves steps to modules (`Req.Retry`...) | Req 0.7 / 0.8-rc |
| `catch Expr` in Erlang | `try ... catch ... end` (warns in OTP 29) | OTP 28/29 |
| `jsx`/`jiffy` in new Erlang code | OTP `json` module | OTP 27 |

## Do this
### Project shape
- New web app: `mix phx.new app` (Bandit, Ecto/Postgres, Tailwind v4, esbuild, `dns_cluster`, `AGENTS.md`). API only: `--no-html --no-assets`.
  Library: `mix new lib --sup` only if it truly owns processes. Elixir by default; plain Erlang/rebar3 only for an existing Erlang codebase or OTP-lib work.
- Runtime config (env vars, secrets) in `config/runtime.exs` with `System.fetch_env!/1`; compile-time config only for things that change code.
- Least dependency: stdlib `JSON`, `Duration`, `Registry`, `Task`, `:ets`, `Logger` and Req cover most needs. Don't add a dep for
  a GenServer wrapper, a cache (ETS), a JSON lib on 1.18+, or a retry helper (Req has retries). Add Oban only when jobs must survive restarts.
- `mix igniter.install <pkg>` can install and wire packages that support it (Oban, Ash...); review the diff it makes.

### Processes and OTP: pick the smallest tool
| Need | Use |
|---|---|
| Pure data transformation | A plain function. No process. |
| Run something concurrently and get the result | `Task.async/await`, `Task.async_stream(xs, f, max_concurrency: n, timeout: t)` |
| Fire-and-forget work that must not take down the caller | `Task.Supervisor.start_child(MyApp.TaskSup, fn -> ... end)` |
| Long-lived state + serialized access, timers, owned resource | `GenServer` under a `Supervisor` |
| Many runtime-created workers (per user/room/device) | `DynamicSupervisor` + `Registry` (`{:via, Registry, {MyApp.Reg, key}}`) |
| Read-heavy shared data | `:ets` (`read_concurrency: true`) owned by a supervised process |
| Rarely-changing global config read on hot paths | `:persistent_term` (writes trigger a global GC scan: no frequent updates) |
| Producer/consumer with back-pressure, SQS/Kafka/RabbitMQ ingestion | `Broadway` (on `GenStage`) |
| Durable/retried/scheduled jobs | `Oban` (Postgres; also SQLite/MySQL engines) |
- `Agent` only for trivial state; it is a GenServer without the ability to handle messages or timers. Do not build caches or queues on it.
- Every long-lived process is started by a supervisor in the app tree; never bare `spawn`/`Task.start` for real work.
- Let it crash: match the happy path (`{:ok, x} = ...` where failure is a bug); handle expected errors with `with`/tagged tuples.
  Pick restart strategies on purpose (`:one_for_one` default, `:rest_for_one` when later children depend on earlier ones).
- Links propagate crashes (use with supervision); monitors observe without dying (`Process.monitor/1`, handle `{:DOWN, ...}`).
- `GenServer.call` has a 5 s default timeout; slow work belongs in a Task, not the GenServer loop (one GenServer = one bottleneck).
- `handle_continue/2` for post-init work so `init/1` returns fast; label processes with `Process.set_label/1` (1.17+/OTP 27) for Observer.
- Mailboxes are unbounded: if producers outrun a GenServer, add back-pressure (call instead of cast, GenStage/Broadway, or shed load).
```elixir
children = [
  MyApp.Repo,
  {Registry, keys: :unique, name: MyApp.Reg},
  {DynamicSupervisor, name: MyApp.RoomSup, strategy: :one_for_one},
  {Task.Supervisor, name: MyApp.TaskSup},
  {Oban, Application.fetch_env!(:my_app, Oban)},
  MyAppWeb.Endpoint
]
Supervisor.start_link(children, strategy: :one_for_one, name: MyApp.Supervisor)
```

### Phoenix 1.8
- Contexts take the scope first: `Blog.list_posts(socket.assigns.current_scope)`; queries filter by `scope.user.id`. Keep this pattern
  for any user-owned data (it prevents IDOR); PubSub topics are per scope too.
- `mix phx.gen.auth Accounts User users` -> magic link login, optional password, `require_sudo_mode` for sensitive pages.
- LiveView: `stream/3` for lists, `assign_async/3`/`start_async/3` for slow loads, `<.form for={@form}>` with `to_form(changeset)`,
  `phx-debounce` on inputs, `JS` commands for client-only UI, `<.portal>` (1.1+) for modals/tooltips, `JS.ignore_attributes(["open"])` for `<dialog>`.
- Channels for non-LiveView clients (mobile, games); `Phoenix.PubSub.broadcast/3` for fan-out; Presence for who's-online.
- Plug pipelines: `:browser` keeps `protect_from_forgery` and `put_secure_browser_headers` (1.8 adds `base-uri 'self'; frame-ancestors 'self'` CSP default).

### Ecto
- All writes through changesets: `cast/3` with an explicit allow-list, `validate_*`, then `unique_constraint/3`, `foreign_key_constraint/3`
  matching real DB constraints (constraint = truth, validation = UX).
- N+1: `Repo.preload(posts, :comments)` or `preload: [comments: c]` with a join; never `Repo.get` inside `Enum.map`.
- Multi-step writes: `Ecto.Multi` or `Repo.transact/2` (3.13+). Upserts: `on_conflict: {:replace, [...]}, conflict_target: ...`.
- Migrations: add indexes `concurrently: true` with `@disable_ddl_transaction true` and `@disable_migration_lock true` on big Postgres tables;
  split "add column" from "backfill" from "add NOT NULL". Use `timestamps(type: :utc_datetime_usec)`.
- `Repo.stream` only inside a transaction; batch big updates with `update_all`/`insert_all` (chunks of a few thousand).

### HTTP, releases, deployment
- `Req.get!(url, receive_timeout: 15_000, retry: :transient)` (pin `~> 0.7`; 0.8 is a redesign in RC). Set timeouts on every external call.
- Release: `MIX_ENV=prod mix release`; `bin/app eval "MyApp.Release.migrate()"` before start; multi-stage Docker (build on `hexpm/elixir`
  image, run on slim Debian with matching OpenSSL/glibc, non-root user). `mix phx.gen.release --docker` writes this.
- Clustering: `dns_cluster` (default in Phoenix, works on Fly.io/k8s headless services) or `libcluster` for gossip/k8s strategies.
  Set `RELEASE_COOKIE` from a secret; distribution traffic is unencrypted by default (use TLS dist or a private network).
- Fly.io: `fly launch` detects Phoenix and generates the Dockerfile + `rel/env.sh.eex` for clustering (verify on fly.io docs).
- Nerves: build firmware for embedded Linux boards (Raspberry Pi, BeagleBone) as a minimal BEAM-only image; push updates with
  `mix upload` or NervesHub; supervise hardware access in GenServers. Gleam is a typed BEAM language that interops with Elixir/Erlang;
  consider it only if the team wants static types now and accepts a smaller ecosystem.

## Security
- **Atom exhaustion**: atoms are never GC'd (default limit ~1M, then the VM dies). Never `String.to_atom/1` on input; use
  `String.to_existing_atom/1` or an explicit map. Same for `Jason.decode(keys: :atoms)` and `JSON` decoders on untrusted data.
- **Code evaluation**: never `Code.eval_string`, `EEx.eval_string`, or `:erlang.binary_to_term/1` on user data. Use
  `Plug.Crypto.non_executable_binary_to_term(bin, [:safe])` if you must decode terms.
- **Mass assignment**: `cast/3` only the fields the user may set; never cast `:role`, `:user_id`, `:admin`.
- **Authorization**: scope every query (Phoenix 1.8 scopes); check ownership in `handle_event` too, not only in `mount` (events are forgeable).
- SQL injection: Ecto queries are parameterized; danger is `fragment/1` with interpolated strings and `Ecto.Adapters.SQL.query` with string building.
- XSS: HEEx escapes; `raw/1` and `Phoenix.HTML.raw` on user content are bugs. CSRF tokens stay on in `:browser` pipeline.
- Redirects: `redirect(to: ...)` only accepts local paths; `external:` with user input is an open redirect.
- Secrets in `runtime.exs` from env; `secret_key_base` per environment; `force_ssl` + HSTS in prod; `Plug.RewriteOn` only behind a trusted proxy.
- SSH/TLS: on OTP < 29 explicitly disable SSH shell/exec if you run `:ssh` daemons; prefer OTP 28+ TLS defaults over custom cipher lists.
- Run `mix sobelow --config`, `mix deps.audit` (mix_audit), `mix hex.audit` (retired packages) in CI.
- Privacy: log only what you need; `@schema_redact` / `redact: true` on sensitive fields so changesets and inspect don't leak them.

## Performance & quality
- Measure first: `:observer.start()` (or `observer_cli`), LiveDashboard (`/dev/dashboard`), `:recon.proc_count(:memory, 10)`,
  `:recon.proc_count(:message_queue_len, 10)`, `:tprof` (OTP 27+), `Benchee` for micro benchmarks.
- Message passing copies data (except refcounted binaries > 64 bytes): send IDs, not big maps; ETS reads also copy.
- Sub-binaries keep the whole parent binary alive: `:binary.copy/1` small slices you keep long-term.
- Long-running processes holding big binaries: `:erlang.garbage_collect/1`, hibernation, or shorter-lived processes.
- Reductions = CPU fairness; a NIF that runs > ~1 ms must be dirty (`ERL_NIF_DIRTY_JOB_CPU_BOUND`) or yield. Prefer Rustler for safe NIFs.
- The JIT (BeamAsm, OTP 24+) is on by default on x86-64/arm64: no flags needed.
- DB: pool size (`pool_size`) ~ CPU cores x 2 to start; watch `[:my_app, :repo, :query]` telemetry `queue_time`.
- Telemetry: `:telemetry` events + `Telemetry.Metrics` (Phoenix generates `MyAppWeb.Telemetry`); OpenTelemetry via `opentelemetry` +
  `opentelemetry_phoenix`/`opentelemetry_ecto`/`opentelemetry_bandit` (check each package's current version).

## Testing & tooling
- `mix test` (ExUnit): `async: true` with `Ecto.Adapters.SQL.Sandbox`; `Phoenix.LiveViewTest` (`live/2`, `render_click`, `form/3`);
  `PhoenixTest` for one API over LiveView and controllers; `phoenix_test_playwright` or Wallaby for real-browser/JS tests.
- Mocks: `Mox` against behaviours (`defmock`, `expect`, `verify_on_exit!`); no mocking of modules you don't own without a behaviour.
- Property tests: `StreamData` (`use ExUnitProperties`, `check all ...`). Oban: `use Oban.Testing` + `testing: :manual`, `assert_enqueued`.
- Format: `mix format --check-formatted` (plugins: `Phoenix.LiveView.HTMLFormatter` for `.heex`; `Styler` if the project uses it).
- Lint: `mix credo --strict`; types: compiler warnings are now real type errors (1.19/1.20), plus `mix dialyzer` (dialyxir) where already set up.
- CI baseline: `mix deps.get && mix compile --warnings-as-errors && mix format --check-formatted && mix credo --strict && mix test && mix sobelow`.
- `mix xref graph --label compile-connected` to find compile-time dependency chains that slow rebuilds.

## Common mistakes in AI-written code
- Wrapping pure logic in a GenServer ("for state") and creating a single-process bottleneck; or using `Agent` as a cache.
- `spawn`/`Task.start` without a supervisor; `Task.async` without `await` (leaks, crashes the caller on failure).
- `String.to_atom(params["sort"])`; `Jason.decode!(body, keys: :atoms)` on request bodies.
- Inventing APIs: `Repo.find`, `Ecto.Changeset.validate_presence`, `Phoenix.LiveView.assign_new/2` arity mistakes, `JSON.parse`.
  Real ones: `Repo.get/get_by/all_by`, `validate_required`, `assign_new(socket, key, fn -> ... end)`, `JSON.decode/1`.
- `Repo.transaction` returning `{:ok, {:ok, x}}` double-wrapped; on Ecto 3.13+ use `Repo.transact`.
- Generating Phoenix 1.6/1.7 code in a 1.8 app: `Routes.*` helpers, `@current_user` instead of `@current_scope`, `live_patch`/`live_redirect`
  (deprecated since LiveView 0.18: use `<.link patch=...>`/`navigate`), `phx-feedback-for`, `Phoenix.HTML.Form.text_input` (use `<.input field={@form[:x]}>`).
- Assigning a full list then appending in LiveView (re-renders everything); use streams.
- `Enum` over huge data loaded into memory instead of `Repo.stream`/`Stream`; `++` in a loop (quadratic).
- Using `Application.get_env` at compile time in module bodies (stale value in releases); use `compile_env` or read at runtime.
- `Process.sleep` in tests to wait for async work; use `assert_receive` or Oban/Task test helpers.
- `@derive Jason.Encoder` without `only:` (leaks password hashes); same for `JSON.Encoder`.
- Hard-coding the Erlang cookie or `secret_key_base` in config files.

## Before you ship
- [ ] `mix compile --warnings-as-errors`, format, Credo, tests (incl. LiveView), Sobelow, `mix deps.audit`, `mix hex.audit` all pass.
- [ ] Every process is supervised; restart strategy chosen; GenServer calls have sane timeouts; no unbounded mailboxes on hot paths.
- [ ] No atoms/eval/`binary_to_term` from user input; every query/event checks the scope/ownership.
- [ ] Migrations safe on a live DB (concurrent indexes, staged NOT NULL); rollback path (`mix ecto.rollback` / down migration) tested.
- [ ] Release built with `mix release`; migrations run via `bin/app eval`; secrets only from env; non-root container; health check endpoint.
- [ ] Telemetry/logging wired (no PII in logs); LiveDashboard protected by auth in prod or dev-only.
- [ ] Accessible HEEx: labels on inputs (`<.input label=...>`), focus handling for modals, `phx-disable-with` on submit buttons.
- [ ] Previous release kept for rollback; clustering cookie from secret; distribution not exposed publicly.

## Sources
- https://elixir.hexdocs.pm/compatibility-and-deprecations.html : support levels, Elixir/OTP compatibility, deprecations 1.18-1.20 (accessed 2026-10-09)
- https://github.com/elixir-lang/elixir/releases : 1.20.4/1.19.6/1.18.5 patches 2026-08-28 (2026-10-09)
- https://elixir-lang.org/blog/2026/06/03/elixir-v1-20-0-released/ : gradual typing, dynamic(), module_definition option (2026-10-09)
- https://elixir-lang.org/blog/2025/10/16/elixir-v1-19-0-released/ : protocol/fn type checking, MIX_OS_DEPS_COMPILE_PARTITION_COUNT, OTP 28.1+ (2026-10-09)
- https://elixir-lang.org/blog/2024/12/19/elixir-v1-18-0-released/ : JSON module, parameterized tests, format --migrate (2026-10-09)
- https://elixir-lang.org/blog/2024/06/12/elixir-v1-17-0-released/ : Duration, shift, to_timeout, Process.set_label (2026-10-09)
- https://www.erlang.org/downloads : latest 29.1.1, 28.5.0.7, 27.3.4.18 (2026-10-09)
- https://github.com/erlang/otp/releases : OTP 29.0 2026-05-13, 28.0 2025-05-21, 27.0 2024-05-20, patches 2026-09-22 (2026-10-09)
- https://github.com/erlang/otp/blob/master/SECURITY.md : last 3 OTP releases supported (2026-10-09)
- https://www.erlang.org/blog/highlights-otp-29/ : native records, PQ TLS default, SSH daemon defaults, warnings (2026-10-09)
- https://www.erlang.org/blog/highlights-otp-28/ : priority messages, generators, PCRE2, hibernate/0, nominal types (2026-10-09)
- https://www.erlang.org/blog/highlights-otp-27/ : json module, process labels, -doc, tprof, trace (2026-10-09)
- https://www.phoenixframework.org/blog/phoenix-1-8-released : scopes, magic links, sudo mode, daisyUI/Tailwind v4, layouts, AGENTS.md (2026-10-09)
- https://raw.githubusercontent.com/phoenixframework/phoenix/v1.8/CHANGELOG.md : 1.8.x dates, OTP 25+, deprecations, CSP default (2026-10-09)
- https://raw.githubusercontent.com/phoenixframework/phoenix/v1.7/CHANGELOG.md : Bandit default 1.7.11, DNSCluster 1.7.8 (2026-10-09)
- https://raw.githubusercontent.com/phoenixframework/phoenix_live_view/v1.1/CHANGELOG.md and .../v1.2/CHANGELOG.md : colocated hooks/CSS, :key, portal, LazyHTML (2026-10-09)
- https://raw.githubusercontent.com/elixir-ecto/ecto/master/CHANGELOG.md : Repo.transact, all_by, 3.14 features (2026-10-09)
- https://raw.githubusercontent.com/oban-bg/oban/main/CHANGELOG.md : Oban 2.24 unified config, Oban.Cron rename (2026-10-09)
- https://raw.githubusercontent.com/wojtekmach/req/main/CHANGELOG.md : Req 0.6 security fixes, 0.7 adapters, 0.8-rc redesign (2026-10-09)
- https://hex.pm/api/packages/<name> : current versions/dates of all listed packages (2026-10-09)
- https://api.github.com/repos/gleam-lang/gleam/releases/latest : Gleam v1.19.1, 2026-10-07 (2026-10-09)
