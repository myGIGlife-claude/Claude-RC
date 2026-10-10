# Ruby and Rails changes by version (as of 2026-10-09)

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
  - 13 default gems became bundled gems (must be in Gemfile), including (full list unverified): mutex_m, getoptlong, base64, bigdecimal, observer, abbrev, resolv-replace, rinda, drb, nkf, syslog, csv.
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
- **4.1**: expected 2026-12-25 per annual cadence (unverified; no preview seen on ruby-lang.org news on 2026-10-09).

## Rails
- **7.1** (2023-10): Dockerfile generated, `normalizes`, `generates_token_for`, `authenticate_by`, async queries, Bun support. EOL.
- **7.2** (2024-08): `allow_browser`, `rate_limit`, YJIT on by default (Ruby >= 3.3), dev container, default RuboCop omakase + Brakeman + GitHub CI, PWA files, `secrets` removed. Last release 7.2.4 (2026-09-24): EOL.
- **8.0** (2024-11): Solid Queue/Cache/Cable, Kamal 2 + kamal-proxy, Thruster, Propshaft default, authentication generator, `params.expect`, `Regexp.timeout` 1s, SQLite production tuning. Security fixes until 2026-11-07.
- **8.1** (2025-10-22): Active Job Continuations, `Rails.event`, `bin/ci` with `config/ci.rb`, `bin/bundler-audit`, markdown rendering, `credentials:fetch`, deprecated associations, Kamal registry-free deploys, no system tests by default, YJIT off in dev/test, removed `rake stats` and Azure storage service; deprecated order-less `first`, `mb_chars`, Rails' Sidekiq adapter. Bug fixes until 2026-10-10, security until 2027-10-10.
- **8.2**: in development on `main` (8.2.0.alpha, Ruby >= 3.3.5); Rails World 2026 talks mention Lexxy, Active Search, Herb and Active Job workflows "in Rails 8.2" (unverified as shipped features). Dynamic `:controller`/`:action` route segments now raise.
