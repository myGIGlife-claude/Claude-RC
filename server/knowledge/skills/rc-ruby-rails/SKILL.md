---
name: rc-ruby-rails
description: Current Ruby (3.3-4.0) and Rails (8.0/8.1, 8.2 on main) standards, Bundler 4, mise/rbenv, Solid Queue/Cache/Cable, Kamal 2, Propshaft, Hotwire, auth generator, Minitest/RSpec, RuboCop/Standard, Brakeman, Puma, Sidekiq, Sinatra/Roda/Hanami. Use when writing, reviewing or upgrading .rb/.erb files, Gemfile, Gemfile.lock, config/routes.rb, config/deploy.yml, .ruby-version, or choosing a Ruby version, framework or host.
---
# Ruby and Rails (as of 2026-10)

> Facts are dated (see Sources). Confirm on the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

## Currency check
Verified 2026-10-09 on ruby-lang.org, rubyonrails.org and rubygems.org.

**Ruby** (new major every Dec 25; ~2y normal + ~1y security maintenance):

| Branch | Released | Status now | EOL |
|---|---|---|---|
| 3.2 and older | | EOL | 3.2 ended 2026-04-01: do not deploy |
| 3.3 | 2023-12-25 | security-only since 2026-04-01 (latest 3.3.12, 2026-07-16) | 2027-03-31 (expected) |
| 3.4 | 2024-12-25 | normal maintenance (3.4.11, 2026-09-23) | TBD |
| 4.0 | 2025-12-25 | normal maintenance (4.0.7, 2026-09-15), **default for new projects** | TBD |
| 4.1 | expected 2026-12-25 (unverified) | not released | |

**Rails** (feature release ~every 6 months; bug fixes 1 year, security 2 years from release):

| Series | Released | Bug fixes until | Security until | Ruby |
|---|---|---|---|---|
| 7.2 and older | | ended | 7.2.4 (2026-09-24) was the **last** 7.2 release | |
| 8.0 | 2024-11 | ended 2026-05-07 | **2026-11-07** | >= 3.2 |
| 8.1 | 2025-10-22 | 2026-10-10 | 2027-10-10 | >= 3.2; latest 8.1.4 (2026-09-24) |
| 8.2 | not released; `main` = 8.2.0.alpha | | | main requires >= 3.3.5 |

Tooling, latest stable on rubygems.org (2026-10-09): RubyGems/Bundler 4.0.22 (4.0 on 2025-12-03), Puma 8.0.2, Kamal 2.12, Thruster 0.1.27, Solid Queue 1.7, Solid Cache 1.0.10, Solid Cable 4.1, Propshaft 1.3.2, importmap-rails 2.2, turbo-rails 2.0, stimulus-rails 1.3, Minitest 6.0 (2025-12-17), rspec-rails 8.0, Capybara 3.40, RuboCop 1.91, Standard 1.57, rubocop-rails-omakase 1.1, Brakeman 8.1, bundler-audit 0.9.3, Sidekiq 8.1, GoodJob 4.22, Devise 5.0, Rack 3.2, Sinatra 4.2, Roda 3.109, Grape 4.0 (Ruby >= 3.3), Hanami 3.0 (2026-07, Ruby >= 3.3), RBS 4.2, Steep 2.1, Sorbet 0.6, Prism 1.9.

Shipped recently (details in `references/versions.md`):
- **Ruby 3.4**: `it` block param, Prism is the default parser, chilled-string warnings (mutating a literal without the magic comment warns with `-W:deprecated`), new `Hash#inspect` (`{user: 1}`), error messages use `'Foo#bar'` not backticks, modular GC, base64/csv/bigdecimal/mutex_m/drb etc. are bundled gems now.
- **Ruby 4.0**: ZJIT (experimental, `--zjit`; slower than YJIT, not for production yet), `Ruby::Box` (experimental, `RUBY_BOX=1`), `Set` and `Pathname` are core, `Ractor::Port` replaces `Ractor.yield`/`#take` (removed), ostruct/logger/benchmark/pstore/irb/rdoc/reline/fiddle became bundled gems, CGI mostly removed, `open("|cmd")` removed, ArgumentError highlights caller and callee. Frozen string literals are still NOT the default.
- **Rails 8.0**: Solid Queue/Cache/Cable by default (no Redis), Kamal 2 + kamal-proxy, Thruster in front of Puma, Propshaft default (Sprockets out), `bin/rails generate authentication`, SQLite fit for production, `params.expect`, `Regexp.timeout = 1s`.
- **Rails 8.1**: Active Job Continuations (`ActiveJob::Continuable`), `Rails.event` structured events, `bin/ci` + `config/ci.rb`, `bin/bundler-audit`, `format.md { render markdown: }`, `bin/rails credentials:fetch`, `deprecated: true` on associations, registry-free Kamal deploys (Kamal >= 2.8), scaffolds skip system tests, YJIT not enabled in dev/test, Sidekiq adapter moved into the sidekiq gem.
- **Bundler 4**: lockfile checksums and `cache_all` on by default; `bundle viz/inject`, `--binstubs`, multiple global sources removed. **Cooldown** (Bundler >= 4.0.13): `source "https://rubygems.org", cooldown: 7`.

### Older versions (legacy projects)
- Rails 7.1/7.2 on Ruby 3.1-3.3 is common: no Solid trio or auth generator by default; Sprockets or jsbundling; `params.require(:x).permit(...)` instead of `params.expect`; `rate_limit` exists from 7.2. Upgrade only when asked; then one minor at a time (7.1 -> 7.2 -> 8.0 -> 8.1) with `bin/rails app:update` and `config.load_defaults` bumped last.
- Ruby 3.2/3.3 apps: no `it` (use `_1` or a named param), YJIT via `--yjit` or Rails' `config.yjit`. On Ruby 4.0 upgrades, add now-bundled gems (`ostruct`, `logger`, `benchmark`, `csv`, `base64`, ...) to the Gemfile or boot fails under Bundler.
- Rails 8.0 security support ends 2026-11-07: flag it, do not upgrade unasked.

## What changed / stop doing
| Old / outdated | Do instead | Since |
|---|---|---|
| Ruby <= 3.2, Rails <= 7.2 for new work | Ruby 4.0 (or 3.4), Rails 8.1 | see tables |
| Sprockets, Webpacker, `rails webpacker:*` | Propshaft + importmap-rails; jsbundling/cssbundling (esbuild/bun) only when you need a build step | Webpacker retired 2022, Propshaft default 8.0 |
| Redis for jobs/cache/cable by default | Solid Queue / Solid Cache / Solid Cable on the DB; Redis only when measured need | 8.0 |
| Capistrano + Unicorn/Passenger assumptions | Kamal 2 (Docker + kamal-proxy) or a PaaS; Puma (+ Thruster) | 8.0 |
| Devise for simple email+password login | `bin/rails g authentication` (sessions, `has_secure_password`, resets; no sign-up page) | 8.0 |
| `attr_accessible` / `protected_attributes` | strong params: `params.expect(user: [:name, :email])` | 4.0 / expect 8.0 |
| `params.require(:u).permit(...)` in new code | `params.expect(...)` (returns 400, not 500, on bad shape) | 8.0 |
| Paperclip / CarrierWave by default | Active Storage | Paperclip deprecated 2018 |
| `config.secrets`, `secrets.yml` | encrypted credentials (`bin/rails credentials:edit`), ENV | secrets removed 7.2 |
| `Ractor.yield` / `Ractor#take` | `Ractor::Port`, `Ractor#value` | Ruby 4.0 |
| `require "set"`, `SortedSet` | `Set` is core; `sorted_set` gem | Ruby 4.0 / 3.0 |
| `OpenStruct` | `Data.define`, `Struct` (keyword init works without `keyword_init:` since 3.2) or a Hash | ostruct bundled gem 4.0 |
| `minitest/mock` built-in, `MiniTest::` | `minitest-mock` gem; `Minitest::` | Minitest 6.0 |
| Puma `on_worker_boot`, `on_restart` hooks | `before_worker_boot`, `before_restart` (`preload_app!` is default in cluster mode) | Puma 7.0 |
| Spring preloader | not in new apps; remove it when you see stale-code bugs | Rails 7.0 |
| `rbenv` + `ruby-build` only | `mise` (`.ruby-version` or `mise.toml`) or rbenv; both use ruby-build | |
| `bundle install --deployment` / `--binstubs` | `bundle config set deployment true`; `bundle binstubs <gem>` | Bundler 4 |
| `first`/`last` with no `order` on a model with no primary key/`implicit_order_column` | add an explicit `order` (raises with the 8.1 defaults) | deprecated 8.1 |
| `status: :unprocessable_entity` | `:unprocessable_content` (Rack 3.1 renamed 422; old symbol warns); Rails 8.1 scaffolds use `ActionDispatch::Constants::UNPROCESSABLE_CONTENT` | Rack 3.1 |
| `String#mb_chars` | plain String methods | deprecated 8.1 |
| `config.active_job.queue_adapter = :sidekiq` relying on Rails | the adapter now ships in sidekiq (>= 7.3.3) | 8.1 |

## Do this
Toolchain:
```bash
mise use ruby@4.0          # writes mise.toml; or: echo 4.0.7 > .ruby-version
gem install rails && rails new app --database=postgresql   # sqlite3 default is fine for small apps
bin/setup && bin/dev
```
- Gemfile: pin Ruby with `ruby file: ".ruby-version"`; commit `Gemfile.lock` (it has checksums now); add `cooldown: 7` to the source. Keep `require: false` for dev tools.
- **When NOT to add a gem**: Rails already has auth (generator, `has_secure_password`, `authenticate_by`, `generates_token_for`), rate limiting (`rate_limit to: 10, within: 1.minute`), jobs (Solid Queue), caching, file upload (Active Storage), encryption (`encrypts :ssn`), enums, normalizations (`normalizes :email, with: ->(e) { e.strip.downcase }`), JSON (`as_json`/jbuilder). Add Devise/Pundit/Sidekiq only for a stated need (OAuth/2FA providers, complex policies, very high job throughput).
- Ruby idioms (3.4+): `it` for one-param blocks; endless methods for one-liners; pattern matching (`case x in {status: 200, body:}`); `Data.define(:x, :y)` for value objects; keyword args; `# frozen_string_literal: true` still worth adding (RuboCop enforces).

Controllers and models:
```ruby
class PostsController < ApplicationController
  rate_limit to: 20, within: 1.minute, only: :create
  def create
    @post = Current.user.posts.new(post_params)
    @post.save ? redirect_to(@post) : render(:new, status: :unprocessable_content)
  end
  private
  def post_params = params.expect(post: [:title, :body])
end
```
- Scope every lookup by owner (`Current.user.posts.find(params[:id])`), never `Post.find` for user data.
- Turbo: return `422` on failed form submits (above; `:unprocessable_entity` on Rack < 3.1) or Turbo won't render the errors; `303` (`redirect_to ..., status: :see_other`) after DELETE.
- Hotwire first (Turbo Drive/Frames/Streams + Stimulus); reach for React only for genuinely client-heavy UIs.
- Jobs: `perform_later` with IDs/GlobalID, idempotent `perform`, `retry_on`/`discard_on`; enqueue after the transaction commits so the job sees the row (`after_commit`; Rails' `enqueue_after_transaction_commit` default varies by version: check). Mission Control Jobs for a dashboard. Long jobs: `include ActiveJob::Continuable` with `step`.
- Mail: `deliver_later`; previews in `test/mailers/previews`; never `deliver_now` in a request path.
- Config: `config/credentials.yml.enc` + `RAILS_MASTER_KEY` in the deploy secret store; `bin/rails credentials:fetch` in Kamal `.kamal/secrets`.
- Deploy: `config/deploy.yml` + `kamal setup` / `kamal deploy`; health check `/up`; DB migrations run in `bin/docker-entrypoint` (`db:prepare`). SQLite in production: put DBs on a Kamal volume, one host only, back up (e.g. Litestream). Fly: `fly launch` reads the Dockerfile. Rollback: `kamal rollback <version>`.
- APIs: `rails new api --api` for JSON-only; Roda or Sinatra for tiny services; Grape to mount versioned APIs in Rails; Hanami 3 for a non-Rails full framework. Rack 3 everywhere.
- Fit: Rails is strong for CRUD/SaaS/admin/content apps with a small team. Prefer something else for CPU-bound or very low-latency services (Go/Rust), heavy realtime fan-out at scale, or mobile/edge-only backends.

## Security
- Run `bin/brakeman` and `bin/bundler-audit` (both generated by Rails 8.1) in CI; fail on new warnings.
- Mass assignment: strong params only; never `permit!`, never `params.to_unsafe_h` into a model; don't permit `role`, `admin`, `user_id`.
- SQL injection: never interpolate into `where("name = '#{x}'")`, `order(params[:sort])`, `pluck`, `find_by_sql`, `exists?`. Use hashes, `?`/named binds, `sanitize_sql_like`, and allowlist sort columns.
- CSRF: keep `protect_from_forgery` (default in `ActionController::Base`); API mode with cookies needs it too. Session cookies: `SameSite=Lax`, `secure` via `config.force_ssl` / `assume_ssl` behind a proxy.
- XSS: ERB escapes; `html_safe`/`raw` only on sanitized content (`sanitize`), `content_security_policy` initializer with nonces for importmap.
- Deserialization: `YAML.safe_load` / `YAML.load` (Psych 4 is safe by default), never `YAML.unsafe_load` or `Marshal.load` on untrusted data; prefer JSON columns. Rails cookies use JSON serializer (`config.action_dispatch.cookies_serializer = :json`).
- SSRF: when fetching user-supplied URLs (webhooks, image import), allowlist schemes/hosts, resolve and block private/link-local/metadata IPs, disable redirects or recheck each hop; `ssrf_filter` gem or equivalent.
- Command injection: `system("cmd", arg)` array form; never backticks/`Kernel#open` with user input; `send`/`constantize` only on allowlisted names.
- Redirects: `redirect_to params[:url]` raises in Rails 7+ unless `allow_other_host: true`; keep it that way.
- File uploads: Active Storage with content-type + size validation; serve via proxy/signed URLs; image processing (libvips) on trusted variants only.
- Secrets: credentials or ENV, `config.filter_parameters` covers tokens/passwords; never log `params` raw.
- Supply chain: Bundler cooldown, lockfile checksums, review new gems' maintainers; rubygems.org had spam-publishing campaigns in 2026.
- Auth generator: rate-limits login/reset; add account lockout/2FA only when required. Use `has_secure_password` (bcrypt), `authenticate_by` (timing-safe).

## Performance & quality
- N+1: `includes`/`preload`, `strict_loading` (`config.active_record.strict_loading_by_default = true` in dev/test or per-association), Bullet 8 in development. Target: no N+1 on index pages; < 10 queries per request typical.
- Caching layers: HTTP (`fresh_when`, `stale?`), fragment/Russian-doll (`cache @post`), low-level `Rails.cache.fetch(key, expires_in:)` on Solid Cache; counter caches; database indexes for every foreign key and WHERE/ORDER column.
- YJIT on in production (Rails 7.2+ default via `config.yjit`; Ruby >= 3.3). Keep ZJIT out of production until it beats YJIT on your benchmark.
- Memory: jemalloc (`LD_PRELOAD` of `libjemalloc.so.2`, the Rails Dockerfile installs it) or `MALLOC_ARENA_MAX=2`; watch RSS per worker; `find_each`/`in_batches` for large sets; avoid loading blobs into memory.
- Puma: `WEB_CONCURRENCY` = cores (processes), `RAILS_MAX_THREADS` 3 (Rails default) for typical IO-mixed apps; DB pool >= threads. Solid Queue can run inside Puma (`SOLID_QUEUE_IN_PUMA=1`) for small apps, separate `bin/jobs` container when load grows.
- Measure: `rack-mini-profiler`, `memory_profiler`, `stackprof`/`vernier`, `Rails.event` + logs, p95 latency, queue latency in Mission Control.

## Testing & tooling
- Default: Minitest (Rails' built-in) with fixtures; parallel tests (`parallelize(workers: :number_of_processors)`). RSpec (rspec-rails 8) + FactoryBot if the project already uses it: follow the project, do not convert.
- Fixtures are fast and Rails-native; FactoryBot is fine but avoid deep `create` chains (use `build`/`build_stubbed`).
- System tests: the scaffold generator no longer creates them since 8.1; write them for critical flows only. Capybara + Selenium (headless Chrome) is the default; `capybara-playwright-driver` is an alternative. Prefer integration tests (`ActionDispatch::IntegrationTest`) for most flows.
- Style: `rubocop-rails-omakase` (Rails default) or Standard; run `bin/rubocop -a`. Types: optional; RBS + Steep (RBS 4 has experimental inline `# @rbs` comments) or Sorbet; don't add to a project that has none unless asked.
- CI: `bin/ci` (8.1) runs setup, rubocop, bundler-audit, brakeman, tests; the generated `.github/workflows/ci.yml` does the same.
- Upgrades: `bin/rails app:update`, `new_framework_defaults_X_Y.rb` flipped one by one, deprecation warnings at `:raise` in test, `bundle outdated`, then `config.load_defaults X.Y`.

## Common mistakes in AI-written code
- Generating Webpacker/Sprockets config, `rails webpacker:install`, `app/javascript/packs` in a Rails 8 app.
- `attr_accessible`, `update_attributes` (removed 6.1), `before_filter` (removed 5.1), `find_by_<attr>!` everywhere, `render :text`.
- `params.require(...).permit(...)` in new Rails 8 code (works, but `params.expect` is the current API) or `permit!`.
- Forgetting the 422 status (`:unprocessable_content`; `:unprocessable_entity` is deprecated on Rack 3.1+) on failed form renders (Turbo shows nothing).
- Adding Redis/Sidekiq/Devise/Paperclip/Pundit by reflex on a fresh Rails 8 app.
- `YAML.load` assumptions from Psych 3 (allowed arbitrary objects); `Marshal` for caching user input.
- String-interpolated SQL in `where`, `order`, `group`; `.where("id IN (#{ids.join(',')})")`.
- `Ractor.yield`/`take`, `require "set"` assumed necessary, `OpenStruct` without the gem on Ruby 4.0.
- Claiming Ruby 3.4/4.0 freezes string literals by default (it does not; only warnings).
- Puma configs with `on_worker_boot`, explicit `preload_app!` comments as if optional, Unicorn configs for new apps.
- `config.secret_key_base` in code, `secrets.yml`, master key committed.
- Old Bundler flags (`--deployment`, `--path`, `--binstubs`).
- `MiniTest::Unit::TestCase`, `assert_equal nil, x` (use `assert_nil`), `require "minitest/mock"` without the gem.
- Inventing gem APIs: check the gem's README/version in `Gemfile.lock` before using a method.

## Before you ship
- [ ] Ruby and Rails on supported lines (or flagged to the owner if not); `Gemfile.lock` committed with checksums.
- [ ] `bin/ci` green: rubocop, `bin/brakeman` (no new warnings), `bin/bundler-audit`, tests.
- [ ] Strong params via `params.expect`; every user-owned record scoped to `Current.user`; no interpolated SQL.
- [ ] No new dependency without a reason; privacy: log/filter only what is needed, no trackers.
- [ ] N+1 checked (Bullet or `strict_loading`); indexes added with migrations; migrations reversible.
- [ ] Errors: rescue at boundaries, 404/422 pages, job `retry_on`/`discard_on`; error tracking configured.
- [ ] Forms/views accessible (labels, errors tied to fields, focus after Turbo updates).
- [ ] Credentials via `RAILS_MASTER_KEY`/secret store; `force_ssl`/`assume_ssl`; CSP on.
- [ ] Deploy: `/up` healthy, `db:prepare` on boot, backup for DB (SQLite volume!), `kamal rollback` path known.

## Sources
- https://www.ruby-lang.org/en/downloads/branches/ - Ruby branch status and EOL dates - 2026-10-09
- https://www.ruby-lang.org/en/news/ - latest patches (4.0.7, 3.4.11, 3.3.12), 2026 CVEs - 2026-10-09
- https://www.ruby-lang.org/en/news/2025/12/25/ruby-4-0-0-released/ - Ruby 4.0 features/removals - 2026-10-09
- https://docs.ruby-lang.org/en/master/NEWS/NEWS-4_0_0_md.html - 4.0 bundled gems, Ractor::Port, error highlight - 2026-10-09
- https://www.ruby-lang.org/en/news/2024/12/25/ruby-3-4-0-released/ - Ruby 3.4 features - 2026-10-09
- https://rubyonrails.org/maintenance - 8.0/8.1 support dates - 2026-10-09
- https://guides.rubyonrails.org/maintenance_policy.html - support policy, release cadence - 2026-10-09
- https://rubyonrails.org/blog/ - 8.1.4 and final 7.2.4 (2026-09-24), Rails World 2026 - 2026-10-09
- https://rubyonrails.org/2026/10/2/this-week-in-rails - main requires Ruby 3.3.5 - 2026-10-09
- https://github.com/rails/rails (main RAILS_VERSION, rails.gemspec) - 8.2.0.alpha, Ruby >= 3.3.5 - 2026-10-09
- https://guides.rubyonrails.org/8_1_release_notes.html - Rails 8.1 features/deprecations - 2026-10-09
- https://guides.rubyonrails.org/8_0_release_notes.html - Rails 8.0 features - 2026-10-09
- https://github.com/rails/rails/blob/8-1-stable/railties/CHANGELOG.md - no system tests by default, bin/ci, bundler-audit, YJIT dev/test - 2026-10-09
- https://rubygems.org/api/v1/versions/<gem>.json - all gem versions, dates and Ruby requirements in this file - 2026-10-09
- https://blog.rubygems.org/2025/12/03/4.0.0-released.html - RubyGems/Bundler 4 changes - 2026-10-09
- https://blog.rubygems.org/2026/06/03/cooldown-let-new-gems-be-vetted.html - Bundler cooldown - 2026-10-09
- https://github.com/minitest/minitest/blob/master/History.rdoc - Minitest 6 changes - 2026-10-09
- https://github.com/puma/puma/blob/master/History.md - Puma 7/8 changes - 2026-10-09
- https://github.com/ruby/rbs/blob/master/CHANGELOG.md - RBS 4 inline syntax - 2026-10-09
- https://github.com/rack/rack/blob/main/lib/rack/utils.rb - `:unprocessable_entity` obsolete, maps to `:unprocessable_content` - 2026-10-10
- https://github.com/rails/rails/blob/8-1-stable/railties/lib/rails/generators/rails/scaffold_controller/templates/controller.rb.tt - scaffold 422 constant - 2026-10-10
- https://github.com/sidekiq/sidekiq/blob/main/Changes.md - Sidekiq 7.3.3 took over the Active Job adapter - 2026-10-10
- https://github.com/rails/rails/blob/8-1-stable/activerecord/CHANGELOG.md - order-less finder deprecation scope - 2026-10-10
