# Ruby and Rails changes by version (as of 2026-11-19)

Sources: ruby-lang.org release posts and NEWS, Rails release notes and CHANGELOGs (see SKILL.md Sources).

## Ruby
- **3.3** (2023-12-25; security-only since 2026-04-01, EOL 2027-03-31 expected): Prism shipped as a default gem, YJIT faster with `RubyVM::YJIT.enable` at runtime, M:N thread scheduler (opt-in).
- **3.4** (2024-12-25):
  - `it` refers to the single block parameter (`list.map { it * 2 }`).
  - Prism is the default parser (`--parser=parse.y` for the old one).
  - Chilled strings: mutating a string literal in a file without `# frozen_string_literal:` emits a deprecation warning (shown with `-W:deprecated`). Not frozen by default.
  - Error messages use single quotes and class names: `undefined method 'foo' for an instance of Bar`; tests matching backticks break.
  - `Hash#inspect` prints `{user: 1, "k" => 2}`.
  - Modular GC (`RUBY_GC_LIBRARY`, experimental MMTk), `GC.config`.
  - YJIT: `--yjit-mem-size`, lower metadata memory, more inlining.
  - 13 default gems became bundled gems (must be in Gemfile): mutex_m, getoptlong, base64, bigdecimal, observer, abbrev, resolv-replace, rinda, drb, nkf, syslog, csv, repl_type_completor.
  - Happy Eyeballs v2 in `Socket.tcp`.
- **4.0** (2025-12-25; 3.5 was renamed 4.0):
  - ZJIT: new method JIT in Rust, `--zjit`, experimental and slower than YJIT for now. YJIT remains the production JIT. RJIT removed.
  - `Ruby::Box` (`RUBY_BOX=1`): experimental isolated namespaces for definitions/monkey patches.
  - Ractor: `Ractor::Port`, `Ractor#join`, `Ractor#value`, `Ractor.shareable_proc`; `Ractor.yield`, `Ractor#take`, `close_incoming/outgoing` removed. Still experimental; most gems are not Ractor-safe.
  - `Set` core (`Set[1, 2]` inspect), `Pathname` core; `SortedSet` needs the `sorted_set` gem.
  - Became bundled gems: ostruct, pstore, benchmark, logger, rdoc, win32ole, irb, reline, readline, fiddle.
  - CGI library removed except `cgi/escape`; `Kernel#open("|cmd")` and `IO` pipe-by-`|` removed; `ObjectSpace._id2ref` deprecated; `*nil` no longer calls `nil.to_a`.
  - Lines starting with `&&`/`||`/`and`/`or` continue the previous line.
  - ArgumentError error_highlight shows caller and callee snippets; backtraces show `Foo#bar` and drop internal frames.
  - `Net::HTTP` no longer sets a default `Content-Type` on requests with a body: set it explicitly.
- **4.1**: 4.1.0-preview1 released 2026-10-12 (Ractor-local GC, YJIT on Windows mswin x64, `autoload_relative`, `Integer#bit_count`, `Range#clamp`, `Module#descendants`, `ENV.fetch_values`). Final expected 2026-12-25 per annual cadence (unverified; no date announced).
- **Patch releases / security**: 2026-11-18 4.0.10, 3.4.13, 3.3.14 fix seven Regexp/String-case-conversion memory bugs (GHSA ids, possible RCE); 2026-10-23 4.0.9, 3.4.12, 3.3.13 fixed Regexp compilation bugs. 3.3 shows as "legacy maintenance" on the branches page.

## Rails
- **7.1** (2023-10): Dockerfile generated, `normalizes`, `generates_token_for`, `authenticate_by`, async queries, Bun support. EOL.
- **7.2** (2024-08): `allow_browser`, `rate_limit`, YJIT on by default (Ruby >= 3.3), dev container, default RuboCop omakase + Brakeman + GitHub CI, PWA files, `secrets` removed. Last release 7.2.4 (2026-09-24): EOL.
- **8.0** (2024-11): Solid Queue/Cache/Cable, Kamal 2 + kamal-proxy, Thruster, Propshaft default, authentication generator, `params.expect`, `Regexp.timeout` 1s, SQLite production tuning. Security fixes until 2026-11-07.
- **8.1** (2025-10-22): Active Job Continuations, `Rails.event`, `bin/ci` with `config/ci.rb`, `bin/bundler-audit`, markdown rendering, `credentials:fetch`, deprecated associations, Kamal registry-free deploys, scaffolds skip system tests, YJIT off in dev/test, removed `bin/rake stats` and the `:azure` storage service; deprecated order-less `first`/`last` on models with no order columns (no PK/`implicit_order_column`), `mb_chars`, Rails' Sidekiq adapter (sidekiq >= 7.3.3 ships it). Bug fixes ended 2026-10-10, security until 2027-10-10; 8.1.4.1 (2026-11-04) is the latest.
- **8.0 security**: 8.0.5.2 (2026-11-04) is the last known release; support ended 2026-11-07.
- **8.2**: in development on `main` (8.2.0.alpha, Ruby >= 3.3.5), no release date seen. Confirmed on main (2026-10/11 This Week in Rails): `config.zjit`, JSON message serializer default under `load_defaults 8.2`, `ssl_default_redirect_status:` deprecated, `RAILS_MASTER_KEY` accepts comma-separated keys, new apps set `capybara`/`selenium-webdriver` `require: false`. Rails World 2026 talks mention Lexxy, Active Search, Herb and Active Job workflows "in Rails 8.2" (unverified as shipped). Dynamic `:controller`/`:action` route segments now raise.
