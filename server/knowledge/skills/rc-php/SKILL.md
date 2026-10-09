---
name: rc-php
description: Current PHP standards (PHP 8.2-8.5, 8.6 in RC), Composer 2.10, PER-CS, PHPStan/Psalm/Rector, PHPUnit/Pest, Laravel 13, Symfony 8/7.4 LTS, FrankenPHP/Octane, PDO, PHP security and WordPress. Use when writing, reviewing or upgrading any .php file, composer.json, phpunit.xml, phpstan.neon, Blade/Twig templates, WordPress themes/plugins, or when choosing a PHP version, framework or host.
---
# PHP (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

## Currency check
Verified 2026-10-09 on php.net, packagist.org and project sites.

| Branch | Released | Active support until | Security until | Status now |
|---|---|---|---|---|
| 8.1 and older | - | - | ended 2025-12-31 | EOL: do not deploy |
| 8.2 | 2022-12-08 | 2024-12-31 | **2026-12-31** | security-only, EOL in ~3 months |
| 8.3 | 2023-11-23 | 2025-12-31 | 2027-12-31 | security-only |
| 8.4 | 2024-11-21 | 2026-12-31 | 2028-12-31 | active |
| 8.5 | 2025-11-20 | 2027-12-31 | 2029-12-31 | active, **default for new projects** |
| 8.6 | GA planned 2026-11-19 | | | RC3 out 2026-10-08; not for production |

Latest patches (2026-09-24 security release): 8.5.11, 8.4.26, 8.3.35, 8.2.34. Policy: 2 years active + 2 years security.

What shipped recently (details per version in `references/versions.md`):
- **8.4** (2024-11): property hooks, asymmetric visibility (`public private(set)`), lazy objects, `#[\Deprecated]`, `array_find/_key/any/all`, `new Foo()->bar()` without parens, `Dom\HTMLDocument` (HTML5), `BcMath\Number`, `Pdo\Mysql`/`Pdo\Sqlite` subclasses, `mb_trim`/`mb_ucfirst`, new IR-based JIT, bcrypt default cost 10 -> 12.
- **8.5** (2025-11): pipe operator `|>`, URI extension (`Uri\Rfc3986\Uri`, `Uri\WhatWg\Url`), clone-with `clone($obj, ['prop' => $v])`, `#[\NoDiscard]`, `array_first()`/`array_last()`, closures and first-class callables in constant expressions, fatal-error backtraces, `#[\Override]` on properties, final promoted properties, persistent cURL share handles.
- **8.6** (RC, may still change): partial function application, `clamp()`, readonly properties with defaults, safer session cookie defaults (`use_strict_mode=1`, `cookie_httponly=1`, `cookie_samesite=Lax`), URI builders.

Tooling and frameworks, latest stable on Packagist (2026-10-09):

| Thing | Version | Notes |
|---|---|---|
| Composer | 2.10.3 (2026-08-27) | 2.10 (2026-05-28) added `policy` config; malware blocking on by default |
| Laravel | 13.x (13.0 on 2026-03-17) | PHP 8.3-8.5; security fixes to 2028-03-17. L12 security ends 2027-02-24 |
| Symfony | 8.1 (stable, PHP >= 8.4); **7.4 LTS** (bugs to 2028-11, security to 2029-11); 6.4 security to 2027-11 | 8.2 due 2026-11 |
| PHPUnit | 13.4 (13.0 on 2026-02-06, PHP >= 8.4) | 12 = PHP >= 8.3, bugfixes to 2027-02-05; 11 out of bugfix support |
| Pest | 5.3 (PHP >= 8.4, PHPUnit 13) | 5.0 at Laracon US 2026: test impact analysis |
| PHPStan | 2.3.1 | levels 0-10 (`max` = 10) |
| Psalm | 6.20 (7.0 in RC) | |
| Rector | 2.7 | |
| PHP-CS-Fixer | 3.95 | Pint 1.32 (Laravel wrapper) |
| PHP_CodeSniffer | 4.0 | WPCS 3.4 for WordPress |
| Infection | 0.35 | mutation testing |
| Doctrine ORM | 3.7 | Twig 3.30 |
| Slim | 4.15 | CodeIgniter 4.7 (PHP >= 8.2) |
| Laminas MVC | 3.8, **security-only until 2028-12-31** | Mezzio and Laminas components still active |
| FrankenPHP | 1.13 (2026-10-04) | worker mode, Caddy-based |
| WordPress | 7.1 (2026-08) | supports PHP 7.4-8.5 |

## What changed / stop doing
| Old / outdated | Do instead | Since |
|---|---|---|
| Running PHP 8.1 or older, or "PHP 7 is fine" | 8.4 or 8.5; plan off 8.2 before 2026-12-31 | 8.1 EOL 2025-12-31 |
| `mysql_*` functions | PDO with prepared statements (or mysqli prepared) | removed in 7.0 |
| String-built SQL, `addslashes`, `mysql_real_escape_string` | bound parameters | always |
| Dynamic properties on plain classes | declare properties; `#[\AllowDynamicProperties]` only for legacy | deprecated 8.2 |
| `"${var}"` / `"${expr}"` interpolation | `"$var"` or `"{$var}"` | deprecated 8.2 |
| `utf8_encode()` / `utf8_decode()` | `mb_convert_encoding($s, 'UTF-8', 'ISO-8859-1')` | deprecated 8.2 |
| `function f(Foo $x = null)` (implicit nullable) | `?Foo $x = null` | deprecated 8.4 |
| `E_STRICT`, `trigger_error(..., E_USER_ERROR)` | exceptions; `E_ALL` | deprecated 8.4 |
| `fputcsv/fgetcsv/str_getcsv` without `escape` arg | pass `escape: ''` (RFC 4180) or `'\\'` explicitly | deprecated 8.4 |
| `lcg_value()`, `rand()`/`mt_rand()` for secrets | `random_int()`, `random_bytes()`, `Random\Randomizer` | 8.4 / 8.2 |
| `mysqli_ping/kill/refresh`, `mysqli_execute()` | reconnect logic / `mysqli_stmt_execute()` | 8.4 / 8.5 |
| `Serializable` interface, `__sleep`/`__wakeup` | `__serialize()` / `__unserialize()`; JSON for data | 8.1 / 8.5 |
| Backtick operator `` `ls` `` | `proc_open()` with array command, or Symfony Process | deprecated 8.5 |
| `(integer)`, `(boolean)`, `(double)`, `(binary)` casts | `(int)`, `(bool)`, `(float)`, `(string)` | deprecated 8.5 |
| `curl_close()`, `imagedestroy()`, `finfo_close()`, `xml_parser_free()` | nothing: objects free themselves | deprecated 8.5 |
| `ReflectionProperty::setAccessible()` | not needed since 8.1 | deprecated 8.5 |
| `$http_response_header` | `http_get_last_response_headers()` | deprecated 8.5 |
| `null` as array key, `$s++` on non-numeric strings | `''` key; `str_increment()` | deprecated 8.5 |
| `PDO::MYSQL_ATTR_*` driver constants | `Pdo\Mysql::ATTR_*` and `PDO::connect()` | deprecated 8.5 |
| Getter/setter boilerplate for simple rules | property hooks or `public private(set)` | 8.4 |
| `array_filter(...)[0] ?? null` / manual loops | `array_find()`, `array_any()`, `array_all()`; `array_first()` | 8.4 / 8.5 |
| `new DOMDocument` + `loadHTML` for HTML5 | `Dom\HTMLDocument::createFromString()` + `querySelector()` | 8.4 |
| `parse_url()` for security decisions | `Uri\WhatWg\Url` / `Uri\Rfc3986\Uri` | 8.5 |
| `withX()` methods copying every readonly prop | `clone($this, ['x' => $x])` | 8.5 |
| `Closure::fromCallable('strlen')`, `[$o, 'm']` strings | first-class callable `strlen(...)`, `$o->m(...)` | 8.1 |
| Class constants as enum, `MyCLabs\Enum` | native `enum` (backed when persisted) | 8.1 |
| PSR-2 / PSR-12 as "the" standard | PER Coding Style 3.x (PSR-12 successor) | PER-CS 3.x |
| `include 'header.php'` chains, `require_once` everywhere | Composer PSR-4 autoload + front controller | |
| Globals, `global $db`, singletons | constructor injection, PSR-11 container | |
| `magic_quotes`, `register_globals`, `safe_mode` advice | gone since 5.4; validate input explicitly | removed |
| `md5`/`sha1`/`crypt` for passwords | `password_hash()` (`PASSWORD_DEFAULT` or `PASSWORD_ARGON2ID`) | 5.5 |
| Laravel Breeze/Jetstream, `app/Http/Kernel.php` | Laravel starter kits (React/Vue/Svelte/Livewire); `bootstrap/app.php` slim skeleton | L12 / L11 |
| `withConsecutive()`, `any()` in PHPUnit mocks | `withParameterSetsInOrder()`; `createStub()` when not verifying | PHPUnit 13 |
| Composer `audit.block-insecure` / `audit.ignore` | `config.policy.advisories.*` | Composer 2.10 |

## Do this
**Project layout (framework-free)**
```
composer.json  composer.lock  public/index.php  src/  tests/  config/  var/
phpstan.neon  phpunit.xml(.dist)  .php-cs-fixer.dist.php  rector.php
```
Only `public/` is the web root. Everything else is outside it.

**composer.json essentials**
```json
{
  "require": { "php": "^8.4" },
  "autoload": { "psr-4": { "App\\": "src/" } },
  "autoload-dev": { "psr-4": { "App\\Tests\\": "tests/" } },
  "config": {
    "sort-packages": true,
    "platform": { "php": "8.4.0" },
    "allow-plugins": { "phpstan/extension-installer": true },
    "policy": { "abandoned": { "audit": "report" } }
  }
}
```
- Commit `composer.lock` for apps (not for libraries). CI and deploy run `composer install`, never `composer update`.
- `config.platform.php` = the LOWEST production PHP, so the lock never pulls packages needing newer PHP.
- Deploy: `composer install --no-dev --classmap-authoritative --no-interaction` (or `--optimize-autoloader`, plus `--apcu-autoloader` if APCu exists).
- `allow-plugins` defaults to nothing allowed: whitelist each plugin by name; never `"allow-plugins": true`.
- `composer audit` in CI (exit 1 on findings). By default `composer update`/`require` refuse versions with security advisories (since 2.9; `policy.advisories.block` since 2.10), and versions flagged as malware are also blocked on `composer install` (`policy.malware.block`, 2.10). `composer install` from an existing lock does NOT block advisories: keep `composer audit` in CI. Ignore a specific advisory with `policy.advisories.ignore-id`, with a comment why.
- `composer validate --strict`, `composer dump-autoload --strict-psr` in CI. `secure-http` stays `true`.

**Modern class (8.4/8.5)**
```php
<?php
declare(strict_types=1);

namespace App\Domain;

final class Money
{
    public function __construct(
        public private(set) int $cents,              // asymmetric visibility
        public readonly Currency $currency,          // backed enum
    ) {}

    public string $formatted {                       // property hook, virtual
        get => number_format($this->cents / 100, 2) . ' ' . $this->currency->value;
    }

    #[\NoDiscard]
    public function add(int $cents): static
    {
        return clone($this, ['cents' => $this->cents + $cents]); // 8.5 clone-with
    }
}

enum Currency: string { case EUR = 'EUR'; case USD = 'USD'; }
```
Clone-with on readonly props works only from a scope that may write them (here, inside the class).

**Idioms**
- `declare(strict_types=1);` in every file. Type every parameter, return and property; `never`, `mixed`, DNF types `(A&B)|null` where needed.
- `match` over `switch`; enums with methods over constant lists; `readonly` value objects.
- Pipe (8.5) for linear transforms: `$slug = $title |> trim(...) |> strtolower(...);` Multi-arg steps need an arrow function in parentheses: `|> (fn($s) => str_replace(' ', '-', $s))`.
- `#[\Override]` on overriding methods (8.3), properties (8.5). `#[\Deprecated('use X', since: '2.0')]` on your own APIs (8.4).
- Lazy objects (8.4) are for DI containers/ORMs (`ReflectionClass::newLazyGhost()`); app code rarely needs them.
- Fibers (8.1) are the low-level primitive under Revolt/AMPHP/ReactPHP; do not hand-roll schedulers.
- JIT: off by default (`opcache.jit` defaults to `disable` since 8.4); helps CPU-bound code, rarely typical web I/O. Measure before enabling.
- Interfaces from PHP-FIG: PSR-4 (autoload), PSR-3 (log), PSR-7/17 (HTTP messages/factories), PSR-15 (middleware), PSR-18 (HTTP client), PSR-11 (container), PSR-14 (events), PSR-20 (clock). Type against these, not concrete libraries, in reusable code.

**Database**
```php
$pdo = new PDO($dsn, $user, $pass, [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,       // default since 8.0
    PDO::ATTR_EMULATE_PREPARES => false,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
]);
$st = $pdo->prepare('SELECT id, email FROM users WHERE id = :id');
$st->execute(['id' => $id]);
```
- MySQL DSN: `charset=utf8mb4`. Identifiers (table/column names) cannot be bound: whitelist them.
- `PDO::connect($dsn)` (8.4) returns the driver subclass (`Pdo\Mysql`, `Pdo\Sqlite`...).
- Eloquent: kill N+1 with `with()`/`load()`; call `Model::preventLazyLoading(! app()->isProduction())` in a service provider. Doctrine: fetch joins / `addSelect`, watch the Symfony profiler query count.
- Schema changes only through migrations (Laravel migrations, Doctrine Migrations); never edit a migration that has run in production; make destructive changes in two deploys (expand, then contract).

**Frameworks: what to pick (2026-10)**
- Laravel 13 (PHP >= 8.3): slim skeleton (`bootstrap/app.php` holds middleware/exception config, no HTTP Kernel since L11), starter kits for React/Vue/Svelte/Livewire, controller attributes `#[Middleware]`, `#[Authorize]`, CSRF middleware now `PreventRequestForgery` (origin-aware + token), `Queue::route()`, first-party AI SDK, JSON:API resources.
- Symfony: new apps on 8.1 (PHP >= 8.4) if you will follow minors every 6 months; 7.4 LTS if you upgrade every few years.
- Slim 4 / Mezzio for small PSR-7/15 services. CodeIgniter 4.7 for simple hosting-friendly apps. Laminas MVC: do not start new projects (security-only).

## Security
- **SQL injection**: prepared statements always; whitelist ORDER BY columns and directions; Eloquent `whereRaw`/`DB::raw` and Doctrine DQL string concat are still injectable.
- **XSS**: Twig autoescapes, Blade `{{ }}` escapes; `{!! !!}`, Twig `|raw` and `|e('html')` in the wrong context (JS, attribute, URL) are the bugs. Plain PHP: `htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')` (ENT_QUOTES is default since 8.1). Add a CSP.
- **CSRF**: framework token middleware on all state-changing routes; `SameSite=Lax` cookies are a backup, not the defence. WordPress: nonces (`wp_nonce_field`, `check_admin_referer`, `check_ajax_referer`).
- **Passwords**: `password_hash($pw, PASSWORD_DEFAULT)` (bcrypt, cost 12 since 8.4) or `PASSWORD_ARGON2ID`; `password_verify()` + `password_needs_rehash()` on login. Store in `VARCHAR(255)`. Bcrypt ignores bytes past 72: cap length or pre-hash with HMAC.
- **Deserialization / POP chains**: never `unserialize()` user input (cookies, form fields, cache from untrusted sources). Use `json_decode(..., flags: JSON_THROW_ON_ERROR)`. If unavoidable: `unserialize($s, ['allowed_classes' => false])`. `phar://` wrappers on user paths trigger the same issue in older code: never pass user input to filesystem functions as a full path.
- **File uploads**: check `$_FILES[...]['error']`, size limit, detect type with `finfo` (not the client MIME or extension), generate your own filename, store outside the web root or in object storage, serve via a controller with `Content-Disposition`, never execute (no `.php` in upload dirs; deny in web server config).
- **SSRF**: user-supplied URLs fetched by Guzzle/cURL/`file_get_contents`: parse with `Uri\WhatWg\Url`, allow-list scheme + host, resolve DNS and reject private/loopback/link-local/metadata IPs (169.254.169.254), disable redirects or re-check each hop. `allow_url_include=Off` always.
- **Command injection**: `escapeshellarg` is a last resort; prefer `proc_open(['cmd', $arg])` (array form, no shell) or Symfony Process with an array.
- **Sessions**: `session.use_strict_mode=1`, `cookie_httponly=1`, `cookie_secure=1`, `cookie_samesite=Lax`, `use_only_cookies=1`; `session_regenerate_id(true)` after login and privilege change. (8.6 makes the first three defaults; set them explicitly until then.)
- **php.ini (production)**: `display_errors=Off`, `log_errors=On`, `expose_php=Off`, `allow_url_include=Off`, `error_reporting=E_ALL`, sensible `upload_max_filesize`/`post_max_size`/`memory_limit`, `open_basedir` on shared hosts. `disable_functions` is defence in depth, not a sandbox. (`disable_classes` was removed in 8.5.)
- **Dependencies**: `composer audit` in CI plus Dependabot/Renovate; `roave/security-advisories` as a dev conflict guard is optional now that Composer blocks advisories on update.
- **Secrets**: from env / secret manager, never in the repo; `.env` must not be web-reachable (only `public/` is the docroot). Laravel `APP_DEBUG=false` in production (debug pages leak env).
- **Mass assignment**: Laravel `$fillable` (not `$guarded = []`); Symfony forms/DTOs with explicit fields.

## Performance & quality
- **OPcache on** in every non-dev environment: `opcache.enable=1`, `opcache.memory_consumption` 256+ MB for frameworks, `opcache.max_accelerated_files` above your file count, `opcache.validate_timestamps=0` in immutable deploys (then reset OPcache or reload FPM on deploy).
- **Preloading** (`opcache.preload` + `opcache.preload_user`): small gain, needs FPM restart on each deploy; Symfony generates a preload file. Skip on shared hosting.
- **Realpath cache**: `realpath_cache_size=4096K`, `realpath_cache_ttl=600` for big codebases.
- **Worker mode** (app stays booted between requests): FrankenPHP worker mode, Laravel Octane (FrankenPHP, Swoole, RoadRunner drivers), RoadRunner, Swoole/OpenSwoole. Typical gain is bootstrapping cost per request. Requirements: no request data in static props/singletons, reset services between requests, `MAX_REQUESTS`/`--max-requests` to recycle leaky workers. Test for state leaks before switching.
- **PHP-FPM**: size `pm.max_children` from RAM / per-worker RSS; `pm = static` or `dynamic` on dedicated boxes, `ondemand` on small ones.
- Measure: p95 latency, queries per request (Laravel Debugbar/Telescope/Pulse, Symfony profiler), memory per request, OPcache hit rate (`opcache_get_status()`), profilers Xdebug (dev), SPX, Blackfire, Tideways.
- Quality targets: PHPStan level 8+ (`max` = 10) on new code with a baseline for legacy, zero new baseline entries, mutation score (Infection MSI) tracked on core domain code.

## Testing & tooling
- Tests: PHPUnit 12 (PHP 8.3) / 13 (PHP 8.4+), or Pest 4 (PHPUnit 12) / Pest 5 (PHP 8.4, PHPUnit 13). Use attributes (`#[Test]`, `#[DataProvider]`, `#[CoversClass]`): docblock annotations were removed in PHPUnit 12.
- `createStub()` when you only need return values, `createMock()` only when verifying calls (PHPUnit 13 adds opt-in `seal()` for doubles and hard-deprecates `any()`).
- Static analysis: PHPStan 2.x (Larastan for Laravel, phpstan-symfony/doctrine extensions) or Psalm 6. One is enough.
- Refactoring/upgrades: Rector 2.x (`withPhpSets(php85: true)`, `withPreparedSets(deadCode: true, typeDeclarations: true)`); run in CI with `--dry-run`.
- Formatting: PHP-CS-Fixer with `@PER-CS` (+ the matching `@PHPxxMigration` set) or Laravel Pint (preset `laravel` or `per`). PHPCS 4 + WPCS 3 for WordPress.
- Mutation: Infection on the domain layer (`--min-msi`).
- CI order: `composer validate` -> `composer install` -> `composer audit` -> cs check -> PHPStan -> tests with coverage -> (Rector dry-run). Matrix the PHP versions you support (e.g. 8.4, 8.5; add 8.6 as allowed-failure until GA).
- Local env: Docker (official `php:8.5-fpm` images), Laravel Herd/Sail, Symfony CLI, or `php -S` for tiny things. WordPress: `wp-env` or WordPress Playground.

## WordPress (concise)
- WP 7.1 (2026-08) runs on PHP 7.4-8.5; run 8.4/8.5 anyway. Plugins should still declare `Requires PHP` honestly.
- Block themes: `theme.json` (schema version 3 since WP 6.6; check for newer (unverified)) for settings/styles, HTML templates in `templates/` and `parts/`, patterns in `patterns/`. Avoid new classic-theme PHP template hierarchies for new themes.
- Blocks: `block.json` + `@wordpress/scripts`; `register_block_type( __DIR__ . '/build/my-block' )`. Front-end interactivity via the Interactivity API (`data-wp-interactive`, `@wordpress/interactivity` store) instead of jQuery.
- Security: escape late (`esc_html`, `esc_attr`, `esc_url`, `wp_kses_post`), sanitize early (`sanitize_text_field`, `absint`), nonces for intent AND `current_user_can()` for permission (a nonce is not authorization), `$wpdb->prepare()` for every query, REST routes need a real `permission_callback` (never `__return_true` on writes).
- Tooling: WP-CLI (`wp core update`, `wp plugin list --update=available`, `wp search-replace` with `--dry-run`), WPCS 3 via PHPCS, PHPStan with `szepeviktor/phpstan-wordpress`, Playground (`playground.wordpress.net`, blueprints) for demos and PR previews.

## Shared-hosting realities
- Many hosts still default to old PHP: pick the version per site in the panel (cPanel MultiPHP / Plesk). Anything below 8.2 is EOL.
- No shell or no Composer: build `vendor/` in CI with `--no-dev --classmap-authoritative` and `config.platform.php` = host version, then upload. Never upload `.env`, `.git`, `tests/`, `composer.*` into the docroot.
- If the docroot cannot be changed to `public/`, point it via host config or a root `.htaccess` rewrite into `public/`, and deny access to everything else.
- No long-running workers: use the host's cron for `schedule:run`/queues (`queue:work --stop-when-empty`), no Octane/FrankenPHP worker mode.
- Set `ini` values via `.user.ini` (FPM) or `php_value` in `.htaccess` (mod_php) only where allowed.

## Common mistakes in AI-written code
- Generating PHP 7-era code: no types, no `strict_types`, `array()` syntax, `isset($a[$k]) ? $a[$k] : null` instead of `??`, `switch` instead of `match`, docblock types instead of native ones.
- `function f(Type $x = null)`: deprecated (8.4). Write `?Type $x = null`.
- Inventing APIs: there is no `array_find_index()` (it is `array_find_key()`), no `str_contains_any()`, no `Uri\Url` (it is `Uri\WhatWg\Url` or `Uri\Rfc3986\Uri`), no `array_first()` before 8.5, no `clamp()` before 8.6.
- Pipe operator misuse: `$x |> str_replace(' ', '-', ...)` is not valid in 8.5 (partial application is 8.6, RC); wrap multi-arg calls in `(fn($s) => ...)` with parentheses.
- Property hooks on `readonly` properties (not allowed) or expecting hooks to work with `public private(set)` + `readonly` the same way: check the RFC rules; readonly props are implicitly `protected(set)` since 8.4.
- PHPUnit `@test`/`@dataProvider` annotations: ignored since PHPUnit 12. `withConsecutive()`: removed in PHPUnit 10.
- Laravel: editing `app/Http/Kernel.php` or `app/Console/Kernel.php` (gone since L11), recommending Breeze/Jetstream (no longer updated), `$guarded = []`, `env()` outside config files (null after `config:cache`).
- `md5(uniqid())` or `rand()` for tokens: use `bin2hex(random_bytes(32))`.
- `filter_var($url, FILTER_VALIDATE_URL)` as an SSRF guard (accepts internal hosts and odd schemes).
- `$_SERVER['HTTP_X_FORWARDED_FOR']` trusted as client IP without a trusted-proxy list.
- Catching `Exception` but not `Error` (`TypeError`, `ValueError`) - catch `\Throwable` at the top boundary only.
- `composer update` in deploy scripts; `"minimum-stability": "dev"` without `prefer-stable`.
- `mb_*` assumptions: set `default_charset=UTF-8`; use `mb_str_pad` (8.3) and `mb_trim` (8.4) instead of byte functions for user text; `mbregex` is deprecated in 8.6 (RC) - prefer `preg_*` with `/u`.

## Before you ship
- [ ] PHP 8.4 or 8.5 in production; `composer.json` `require.php` and `config.platform.php` match it.
- [ ] `composer.lock` committed; deploy uses `composer install --no-dev --classmap-authoritative`; `composer audit` clean.
- [ ] `declare(strict_types=1)`; PHPStan at agreed level with no new baseline entries; CS fixer clean; Rector dry-run clean.
- [ ] Tests pass on every supported PHP version; no deprecation notices in the test run (`failOnDeprecation="true"` in phpunit.xml).
- [ ] All SQL parameterized; templates use auto-escaping; no `{!! !!}`/`|raw` on user data; no `unserialize()` on input.
- [ ] Uploads validated with `finfo`, stored outside docroot; outgoing user URLs allow-listed (SSRF).
- [ ] Session cookies `Secure`, `HttpOnly`, `SameSite`; strict mode on; id regenerated at login.
- [ ] `display_errors=Off`, `expose_php=Off`, framework debug off; only `public/` is web-reachable.
- [ ] OPcache enabled and sized; worker mode (if used) tested for state leaks.

## Sources
- https://www.php.net/supported-versions.php - branch support/EOL dates - 2026-10-09
- https://www.php.net/ - latest patch releases, 8.6 RC schedule - 2026-10-09
- https://wiki.php.net/todo/php86 - 8.6 GA target 2026-11-19 - 2026-10-09
- https://github.com/php/php-src/blob/php-8.6.0RC3/UPGRADING - 8.6 changes (RC) - 2026-10-09
- https://www.php.net/releases/8.5/en.php - 8.5 features - 2026-10-09
- https://www.php.net/manual/en/migration85.deprecated.php - 8.5 deprecations - 2026-10-09
- https://www.php.net/releases/8.4/en.php - 8.4 features/deprecations - 2026-10-09
- https://www.php.net/manual/en/migration84.deprecated.php - 8.4 deprecations - 2026-10-09
- https://www.php.net/manual/en/function.password-hash.php - bcrypt cost 12 since 8.4, 72-byte limit - 2026-10-09
- https://getcomposer.org/doc/06-config.md - policy, allow-plugins, platform, autoloader options - 2026-10-09
- https://github.com/composer/composer/releases - Composer 2.10.x dates and features - 2026-10-09
- https://repo.packagist.org/p2/<vendor>/<package>.json - latest stable versions of all tools/frameworks in the table - 2026-10-09
- https://laravel.com/docs/13.x/releases - Laravel 13 date, PHP range, features, support table - 2026-10-09
- https://symfony.com/releases - Symfony 8.1 / 7.4 LTS / 6.4 support dates - 2026-10-09
- https://phpunit.de/supported-versions.html and https://phpunit.de/announcements/phpunit-13.html - PHPUnit support and 13 changes - 2026-10-09
- https://pestphp.com/docs/installation and https://pestphp.com/docs/pest5-now-available - Pest 5 requirements - 2026-10-09
- https://www.php-fig.org/per/coding-style/ - PER-CS 3.1 replaces PSR-12; covers hooks, set-visibility, pipe - 2026-10-09
- https://getlaminas.org/blog/2026-03-06-laminas-mvc-eol-schedule.html - Laminas MVC security-only to 2028-12-31 - 2026-10-09
- https://frankenphp.dev/docs/worker/ - worker mode, MAX_REQUESTS, state gotchas - 2026-10-09
- https://make.wordpress.org/core/handbook/references/php-compatibility-and-wordpress-versions/ - WP 7.1 PHP 7.4-8.5 - 2026-10-09
- https://api.wordpress.org/core/version-check/1.7/ - current WordPress version - 2026-10-09
