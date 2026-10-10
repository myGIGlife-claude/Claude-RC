---
name: rc-rust
description: Current Rust (1.99 stable, edition 2024) language, std, cargo workspaces/features/MSRV, error handling, async/Tokio, axum/actix, serde, sqlx/diesel, clap, tracing, testing (nextest, proptest, insta, miri, fuzz), unsafe/FFI/wasm/embedded, build size/speed and supply-chain security. Use when writing or reviewing *.rs, Cargo.toml, Cargo.lock, rust-toolchain.toml, clippy.toml, deny.toml, or choosing Rust vs Go/C++/Java.
---
# Rust  (as of 2026-10)

> Facts are dated (see Sources). Rust ships every 6 weeks; confirm the primary source before pinning a version. "(unverified)" = lead, not fact.

## Currency check
- **Stable: Rust 1.99.0 (2026-10-01).** Next: 1.100 on 2026-11-12, 1.101 on 2026-12-24 (releases.rs). Only the newest stable gets fixes;
  there is no project LTS (certified long-term toolchains come from vendors such as Ferrocene).
- **Edition 2024** (Rust 1.85, 2025-02-20) is current. A 2027 edition is discussed, not announced (unverified). Editions are per crate and interoperate.
- Last ~12 months (details per release: `references/releases.md`):
  - 1.99: C-variadic fn definitions, `Vec::into_parts/from_parts`, `fs::set_times`. 1.98: `NumBuffer`/`format_into`, float `algebraic_*`.
  - 1.97: v0 symbol mangling default; `CARGO_BUILD_WARNINGS`. 1.96: `core::range` Copy range types, `assert_matches!`, wasm undefined symbols now link errors.
  - 1.95: `cfg_select!` (replaces `cfg-if`), **if-let guards** in match, `Vec::push_mut`, `Atomic*::update`. 1.94: `array_windows`, `LazyLock::get`, cargo config `include`, TOML 1.1.
  - 1.93: musl 1.2.5, `fmt::from_fn`. 1.92: never-type lints deny-by-default, `RwLockWriteGuard::downgrade`. 1.91: `aarch64-pc-windows-msvc` Tier 1, `strict_*` ints.
- Earlier baselines still worth knowing: 1.90 **LLD default linker on x86_64 Linux**, `cargo publish --workspace`; 1.89 `[x; _]` inferred const args, `File::lock`;
  1.88 **let chains** (2024 edition only), cargo cache auto-GC; 1.87 `io::pipe`, `use<..>` in traits; 1.86 **trait upcasting**, `get_disjoint_mut`;
  1.85 **async closures**; 1.84 MSRV-aware resolver; 1.80 `LazyLock`/`LazyCell`; 1.75 `async fn` / RPITIT in traits; 1.70 `OnceLock`.
- Not stable yet: never type `!` lands in **1.100** (Infallible becomes `= !`); `gen` blocks nightly-only; next trait solver and new borrow checker nightly-only;
  `cargo -Zscript` and `build-dir-new-layout` still unstable.
- Ecosystem (crates.io, 2026-10-09): tokio 1.53.2 (LTS 1.51.x to 2027-03, 1.53.x to 2027-09; MSRV 1.85 from 1.54), axum 0.8.9, actix-web 4.15.0,
  tower 0.5.3, hyper 1.12.0, reqwest 0.13.5, rustls 0.23.45, serde 1.0.229, thiserror 2.0.21, anyhow 1.0.104, miette 7.6.0, clap 4.6.7,
  sqlx 0.9.0, diesel 2.3.14, sea-orm 2.0.4, tracing 0.1.44, tracing-subscriber 0.3.23, proptest 1.11.0, insta 1.49.0, cargo-nextest 0.9.148,
  cargo-deny 0.20.2, cargo-audit 0.22.2, cargo-vet 0.10.2, cargo-semver-checks 0.51.0, cargo-fuzz 0.13.2, wasm-bindgen 0.2.129,
  pyo3 0.29.3, napi 3.14.2, uniffi 0.32.2, cxx 1.0.203, embassy-executor 0.10.0, embedded-hal 1.0.0, sccache 0.18.0.
- **async-std is discontinued** (announced in 1.13.1, 2025-02; last crates.io release 1.13.2, 2025-08; points users to `smol`). Rust in the Linux kernel is no longer experimental (Dec 2025).

### Older versions
- On a 2021-edition crate: no let chains (use nested `if let` or `let ... else`), RPIT does not capture all lifetimes, `env::set_var` is still safe
  (but still racy: treat it as unsafe), resolver 2 (set `resolver.incompatible-rust-versions = "fallback"` for MSRV-aware lock).
- Respect `rust-version` in Cargo.toml: before using an API, check its "since" version in docs (e.g. `LazyLock` 1.80, let chains 1.88, `cfg_select!` 1.95).
  Below 1.80 keep `once_cell`; below 1.75 keep `async-trait`. Do not bump edition, MSRV or major deps unless the task asks for it.
- tokio LTS lines (1.51.x/1.53.x) are fine to stay on; axum 0.7 projects use `/:id` paths and `#[async_trait]` extractors.

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| `lazy_static!`, `once_cell::sync::Lazy/OnceCell` | `std::sync::LazyLock`, `OnceLock` (`LazyCell`/`OnceCell` single-thread) | 1.80 / 1.70 |
| `#[async_trait]` on your own traits | native `async fn` in traits (keep `async-trait`/`trait-variant`/boxing only when you need `dyn Trait`) | 1.75 |
| `failure`, `error-chain`, hand-rolled `Box<dyn Error>` everywhere | `thiserror` 2 (libraries), `anyhow` or `miette` (apps) | - |
| `try!(x)` | `x?` | 2018 |
| nested `if let` pyramids | let chains `if let Some(a) = x && let Ok(b) = f(a) { }` (2024 edition) | 1.88 |
| `match` + `if let` inside arm | if-let guards `Some(x) if let Ok(y) = parse(x) => ...` | 1.95 |
| `cfg-if` crate | `cfg_select! { unix => {...}, _ => {...} }` | 1.95 |
| `as_any()` downcast helpers for supertrait objects | trait upcasting `let s: &dyn Super = sub;` | 1.86 |
| `move \|\| async move { }` closures returning futures | `async move \|\| { }` + `AsyncFn` / `AsyncFnMut` bounds | 1.85 |
| `split_at_mut` gymnastics for two `&mut` | `slice.get_disjoint_mut([i, j])`, `HashMap::get_disjoint_mut` | 1.86 |
| `drain_filter` (nightly), retain+collect | `Vec::extract_if`, `HashMap::extract_if` | 1.87 / 1.88 |
| `fs2`/`fs4` for file locks | `File::lock`, `try_lock`, `lock_shared` | 1.89 |
| `os_pipe` crate | `std::io::pipe()` | 1.87 |
| `std::env::set_var` in multi-threaded code | `unsafe { set_var }` (2024) only before threads start; prefer passing config | 2024 |
| `#[no_mangle]`, `extern "C" { }` | `#[unsafe(no_mangle)]`, `unsafe extern "C" { }` | 2024 |
| `&mut STATIC_MUT` | `AtomicX`, `Mutex`, `OnceLock`, or `&raw mut` | 2024 (error) |
| `matches!` + `assert!` | `assert_matches!` (`use std::assert_matches::assert_matches`) | 1.96 |
| mold/lld configured by hand on x86_64 Linux | LLD is the default linker there | 1.90 |
| `async-std` | `tokio` (default) or `smol` | discontinued 2025 |
| `structopt` | `clap` 4 derive (`#[derive(Parser)]`) | clap 3+ |
| `log` + `env_logger` for services | `tracing` + `tracing-subscriber` (`log` OK for small libs) | - |
| axum `/:id`, `/*rest`, `#[async_trait]` extractors | axum 0.8 `/{id}`, `/{*rest}`, plain `impl FromRequestParts` | axum 0.8 |
| `wasm32-wasi` target | `wasm32-wasip1` / `wasm32-wasip2` | 1.84 (2025-01; new targets since 1.78) |
| `cargo publish` per crate in order | `cargo publish --workspace` | 1.90 |
| `.unwrap()` on I/O, parse, lock in production paths | `?` with context; `expect("invariant: ...")` only for real invariants | - |
| nightly toolchain for "nice" features | stable; nightly only for miri, fuzz, `-Z` tooling in CI | - |

## Do this
### Toolchain and project setup
```toml
# rust-toolchain.toml (apps; pin exact so CI = laptop)
[toolchain]
channel = "1.99.0"
components = ["clippy", "rustfmt"]
```
```toml
# Cargo.toml (workspace root)
[workspace]
members = ["crates/*"]
resolver = "3"                     # default for edition 2024 members; required explicitly in virtual workspaces

[workspace.package]
edition = "2024"
rust-version = "1.85"              # MSRV you actually test in CI

[workspace.dependencies]
serde = { version = "1", features = ["derive"] }
tokio = { version = "1.53", features = ["rt-multi-thread", "macros"] }

[workspace.lints.rust]
unsafe_code = "forbid"             # relax per crate that truly needs it
[workspace.lints.clippy]
all = { level = "warn", priority = -1 }
```
- Members use `serde.workspace = true` and `[lints] workspace = true`.
- **Commit `Cargo.lock`** for apps AND libraries (cargo's guidance since 2023); CI tests with `--locked`.
- MSRV: libraries set `rust-version` and test it in CI (`cargo +1.85 check --locked`); resolver 3 picks deps compatible with it.
  Apps can track latest stable. Raising MSRV is a minor-version change for libraries; say so in the changelog.
- Features must be **additive** (enabling one never removes API). No `std`-disabling feature named `no_std`: use a default `std` feature.
  `default-features = false` on deps in libraries; check with `cargo hack check --feature-powerset` (unverified tool name stability).
- Dev automation: `cargo xtask` pattern (a `xtask` binary crate + alias in `.cargo/config.toml`) instead of shell/Makefile sprawl.
- Least dependency: check std first (`LazyLock`, `io::pipe`, `File::lock`, `cfg_select!`, `thread::scope`, `fmt::from_fn`). Do not add a crate
  for <50 lines of obvious code, for one helper (`itertools` for one call), or a second crate doing the same thing (two HTTP clients, two runtimes).
  Each dep = compile time + supply-chain risk; prefer `default-features = false`.

### Errors and panics
```rust
#[derive(Debug, thiserror::Error)]
pub enum StoreError {
    #[error("user {0} not found")]
    NotFound(u64),
    #[error("database error")]
    Db(#[from] sqlx::Error),
}

use anyhow::Context;

fn main() -> anyhow::Result<()> {
    let path = std::path::Path::new("app.toml");
    let cfg = std::fs::read_to_string(path).with_context(|| format!("reading {}", path.display()))?;
    Ok(())
}
```
- Libraries: typed errors (`thiserror`), never `anyhow` in a public API. Apps: `anyhow`/`miette` (miette for user-facing diagnostics).
- Panic = bug. Allowed: violated invariants (`expect("why this can't fail")`), tests, startup config that cannot be recovered. Never on input, I/O,
  network or lock poisoning you can handle. Do not log AND return the same error.
- Release `panic = "abort"` only if nothing relies on unwinding (`catch_unwind`, tokio task isolation turns panics into `JoinError`).

### Ownership, borrowing, lifetimes
- Take `&str`, `&[T]`, `impl AsRef<Path>`; return owned values. Accept `impl Into<String>` only for constructors.
- Fighting the borrow checker? Restructure, do not `clone()` blindly: shrink borrow scope (block or compute index first), split structs into
  fields borrowed separately, use indices/IDs instead of references in graphs (`Vec<Node>` + `usize`, or `slotmap`), `std::mem::take`/`replace`
  to move out of `&mut`, `entry()` API for maps, `get_disjoint_mut` for two `&mut`.
- Shared ownership: `Arc<T>` across threads, `Rc<T>` single thread; interior mutability `Mutex`/`RwLock`/atomics (threads), `Cell`/`RefCell` (single).
  `Arc<Mutex<_>>` everywhere is a design smell: prefer message passing or owning tasks.
- Self-referential structs: don't. Store offsets/indices, or use `Pin` only in futures/low-level code.
- Lifetimes: let elision work; write `'_` where a lifetime is hidden (`Iter<'_, T>`, lint since 1.89). Avoid `'static` bounds unless spawning.
- `Cow<'_, str>` for "usually borrowed, sometimes owned". `Box<str>`/`Arc<str>` for immutable shared strings.

### Traits, generics, dyn, iterators
- Generics (`impl Trait`/`<T: Trait>`) by default: zero-cost, monomorphized. `dyn Trait` when you need heterogeneous collections, plugin points or
  to cut compile time/binary size. `async fn` in a trait is not dyn-compatible: box (`Pin<Box<dyn Future + Send>>`) or keep `async-trait` for dyn.
- Public async trait methods used across threads: declare the Send bound, e.g. `fn get(&self) -> impl Future<Output = T> + Send;`.
- Newtypes for IDs/units (`struct UserId(u64)`); `#[non_exhaustive]` on public enums/structs you will extend; derive `Debug` on everything public.
- Iterators over index loops: `iter().map().filter().collect::<Result<Vec<_>, _>>()?` (collect into `Result` stops on first error).
  Return `impl Iterator<Item = T> + '_` instead of `Vec` when callers stream.
- Conversions: implement `From` (gives `Into`), `TryFrom` for fallible, `FromStr` for parsing, `Display` for user text.

### Async (Tokio)
```rust
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let token = tokio_util::sync::CancellationToken::new();
    let mut set = tokio::task::JoinSet::new();
    for url in urls { let t = token.clone(); set.spawn(async move { fetch(t, url).await }); }
    while let Some(res) = set.join_next().await { res??; }          // JoinError, then task error
    Ok(())
}
```
- One runtime (tokio). Never block in async: CPU-heavy or blocking I/O -> `tokio::task::spawn_blocking` (or rayon for CPU).
  Never hold a `std::sync::Mutex` guard across `.await` (use a block to drop it, or `tokio::sync::Mutex` if you must hold it).
- `tokio::spawn` needs `Send + 'static`: clone `Arc`s in, do not borrow. Non-Send state (`Rc`, `RefCell` guard) across `.await` = error.
- **Cancellation safety**: a future dropped in `select!` loses partial progress. Only use cancel-safe ops in loops (`recv`, `accept`);
  pin and reuse long futures; use `CancellationToken` + graceful shutdown. Add timeouts (`tokio::time::timeout`) to every network call.
- Bound concurrency (`Semaphore`, `JoinSet` with a limit, `buffer_unordered(n)`), bound channels (`mpsc::channel(n)`).
- Pick features explicitly (`rt-multi-thread`, `macros`, `net`, `time`, `sync`); avoid `full` in libraries. Libraries should be runtime-agnostic where cheap.

### Web, serde, DB, CLI, logging
- axum 0.8 (tower-based, default choice) or actix-web 4. Use `tower-http` layers: `TraceLayer`, `TimeoutLayer`, `RequestBodyLimitLayer`, `CorsLayer`
  (explicit origins), `CompressionLayer`. Graceful shutdown: `axum::serve(listener, app).with_graceful_shutdown(signal)`.
- State: `Router::with_state(AppState)` with `Clone` state holding `PgPool` (already an `Arc` inside). Extractors: `State`, `Path`, `Json` (last).
- serde: `#[serde(deny_unknown_fields)]` on untrusted input where strictness is wanted, `rename_all = "camelCase"`, `#[serde(default)]`,
  `skip_serializing_if = "Option::is_none"`. Validate after deserialize (types first: parse into newtypes).
- sqlx: compile-time checked `query!`/`query_as!` with `cargo sqlx prepare` + `SQLX_OFFLINE=true` in CI; migrations via `sqlx::migrate!`.
  diesel 2 for a typed query DSL; sea-orm 2 for an ORM. Always bind parameters; never `format!` SQL.
- clap 4 derive: `#[derive(Parser)] #[command(version, about)]`; `env = "APP_X"` for config; exit codes via `std::process::ExitCode`.
- tracing: `#[tracing::instrument(skip(password, body))]`, structured fields `info!(user_id, "login")`; JSON fmt layer in prod; `EnvFilter` from `RUST_LOG`.

### unsafe, FFI, wasm, embedded, cross
- `unsafe_code = "forbid"` by default. Where needed: a small module, every block with `// SAFETY:` explaining the invariant, safe wrapper API,
  `clippy::undocumented_unsafe_blocks` on, and Miri in CI. Prefer `&raw const/mut` over `&` to unaligned/uninit data; `NonNull`, `MaybeUninit`.
- FFI: `bindgen` (C headers -> Rust), `cbindgen` (Rust -> C header), `cxx` (C++), `pyo3`+`maturin` (Python), `napi-rs` (Node), `uniffi` (Kotlin/Swift).
  Use `#[unsafe(no_mangle)] pub extern "C" fn`, `#[repr(C)]`, never let a panic unwind across `extern "C"` (use `extern "C-unwind"` or catch).
- WebAssembly: browser `wasm32-unknown-unknown` + `wasm-bindgen`; server/WASI `wasm32-wasip2` (component model) or `wasip1`.
  Since 1.96 undefined imports fail at link time: declare imports with `#[link(wasm_import_module = ...)]`.
- Embedded: `#![no_std]`, `embedded-hal` 1.0 traits, `embassy` for async, `probe-rs` to flash/debug, `defmt` for logging.
- Cross-compile: `rustup target add <triple>`; Linux static: `x86_64-unknown-linux-musl`; for C deps use `cross` (containers) or `cargo zigbuild` (unverified versions).

### Release size and build speed
```toml
[profile.release]
lto = "thin"          # "fat" + codegen-units = 1 for max speed/size, slower builds
codegen-units = 1
strip = "symbols"
panic = "abort"       # only if no unwinding is needed
# opt-level = "z"     # size-critical (wasm/embedded); measure
[profile.dev.package."*"]
opt-level = 1         # faster dev runtime for heavy deps
```
- Build speed: `sccache` (RUSTC_WRAPPER) or CI cache (`Swatinem/rust-cache`), split big crates, trim features, `cargo build --timings`,
  `cargo llvm-lines`/`cargo bloat` for size. mold only if LLD is still slow for you; on x86_64 Linux LLD is already default.

## Security
- **Supply chain** (biggest real risk): crates.io had malicious crates and the `arrayref` 0.3.10 compromise (RUSTSEC-2026-0260, 2026-08-20: a malicious build-script dependency, removed within ~90 minutes; 0.3.9 is clean);
  the Rust blog warned of targeted attacks on prominent maintainers (2026-09-17). Commit `Cargo.lock`, build `--locked`, run `cargo deny check`
  (advisories, licenses, bans, sources) and/or `cargo audit` in CI; `cargo vet` for audited deps in high-assurance projects. Review `build.rs`
  and proc-macros of new deps: they run code at build time. Use crates.io trusted publishing from CI instead of long-lived tokens (unverified details).
- Keep cargo current: Cargo CVE-2026-5223/5222 fixed in 1.96 (registries other than crates.io).
- CI secrets: the Rust blog (2026-09-21) reported GitHub Actions leaking secrets because Miri wrote env vars into `target/`: do not cache `target/`
  in jobs that run Miri with secrets, and never give secrets to jobs that can write PR-accessible caches (fixed in nightly 2026-09-22+).
- Memory safety holds only outside `unsafe` and sound deps: watch RustSec "unsound" advisories; Miri + fuzz for `unsafe` code.
- Web: SQL bound params; body size limits; timeouts; `rustls` (no OpenSSL by default); validate JWT `aud/iss/exp` (`jsonwebtoken`);
  hash passwords with `argon2`; constant-time compare (`subtle`). Untrusted deserialization: limit sizes, beware deep recursion.
- Integer overflow: wraps silently in release. Use `checked_*`/`strict_*` (1.91) on lengths, money, offsets; or `overflow-checks = true` in release.
- Path traversal: never join user paths without canonicalize + prefix check. Secrets: `secrecy` crate or redact in `Debug`; never log tokens.
- Privacy: no telemetry/analytics crates unless the feature needs them; minimal data in logs.

## Performance & quality
- Measure first: `criterion` or `divan` benches, `cargo flamegraph`/`samply`, `tokio-console` for async stalls, `cargo bloat` for size.
- Common wins: avoid `clone()` in hot loops, `Vec::with_capacity`, `&str` not `String` params, `SmallVec`/`ArrayVec` only when measured,
  `BufReader/BufWriter` for I/O, `HashMap` with `foldhash`/`ahash` only for non-adversarial keys (default SipHash resists HashDoS).
- Allocator swap (`mimalloc`, `jemalloc`) only when profiling shows allocator cost.
- Release profile + PGO (`cargo pgo`, unverified) for CPU-bound services. Targets: zero clippy warnings, no `unwrap` in non-test code paths.

## Testing & tooling
- `cargo fmt --check`, `cargo clippy --all-targets --all-features -- -D warnings`, `cargo test` (or `cargo nextest run` + `cargo test --doc`).
- Unit tests in `#[cfg(test)] mod tests` beside code; integration tests in `tests/`; doc tests on public APIs (combined doctests in 2024 edition).
- `proptest` for invariants/round-trips; `insta` snapshots (`cargo insta review`); `tokio::test` (use `start_paused = true` for time); `wiremock` for HTTP.
- `cargo +nightly miri test` on crates with `unsafe`; `cargo fuzz` (libFuzzer, nightly) for parsers; `cargo-semver-checks` before publishing libraries;
  `cargo-llvm-cov` for coverage. rust-analyzer in the editor; `cargo machete`/`cargo udeps` for unused deps (unverified versions).
- CI: `dtolnay/rust-toolchain` or rustup with `rust-toolchain.toml`, cache, `--locked`, an MSRV job, a `cargo deny` job.

## Common mistakes in AI-written code
- `lazy_static!`/`once_cell` in new code; `#[async_trait]` on traits that are never `dyn`; `try!`; `failure` crate.
- `.unwrap()` everywhere; `clone()` to silence the borrow checker; `Rc<RefCell<_>>` / `Arc<Mutex<_>>` as the default design.
- Let chains or `use<..>` in a 2021-edition crate; APIs newer than the crate's `rust-version` (check "since" in docs).
- Blocking (`std::fs`, `reqwest::blocking`, `std::thread::sleep`, heavy CPU) inside async fns; `std::sync::MutexGuard` held across `.await`.
- `tokio::spawn` with borrowed data; forgetting `JoinHandle` errors; `select!` on non-cancel-safe futures in loops.
- Mixing runtimes (async-std/smol + tokio); `features = ["full"]` in a library; enabling `reqwest` default features (native-tls) when rustls is wanted.
- axum 0.7 syntax (`/:id`) on axum 0.8; `axum::Server` (removed in 0.7; use `axum::serve`); `Json` extractor not last.
- Hallucinated APIs: `Vec::remove_if`, `HashMap::get_or_insert`, `Option::unwrap_or_default_with`, `String::trim_in_place` do not exist (check docs.rs).
- `std::env::set_var` in tests/threads (unsafe in 2024 edition); `static mut` references; `#[no_mangle]` without `unsafe(...)` in 2024.
- `format!` into SQL; `as` casts that truncate (`u64 as u32`) instead of `try_from`; ignoring `#[must_use]` results.
- Non-additive features, missing `default-features = false` in libraries, not committing `Cargo.lock`.

## Before you ship
- [ ] Latest stable (or pinned toolchain) builds; `cargo build --locked`; edition and `rust-version` correct and MSRV tested.
- [ ] `cargo fmt --check`, `cargo clippy -D warnings`, tests (nextest + doctests) green; Miri for `unsafe`; fuzz targets for parsers.
- [ ] `cargo deny check` / `cargo audit` clean; new deps reviewed (build.rs, proc-macros, maintainers); `Cargo.lock` committed.
- [ ] No `unwrap`/`expect` on external input; errors carry context; panics only for invariants.
- [ ] Async: no blocking in tasks, timeouts on I/O, bounded channels/concurrency, graceful shutdown with cancellation.
- [ ] Web: body limits, timeouts, CORS explicit, bound SQL params, secrets redacted from logs/Debug.
- [ ] Release profile set (LTO, strip); binary size and startup checked; libraries pass `cargo semver-checks`.
- [ ] Rollback: previous binary/image kept; DB migrations reversible or backward-compatible.

## When to choose Rust
- Choose Rust: performance + memory safety without GC (systems, CLIs, proxies, parsers, embedded, wasm, kernels/drivers, safe replacements for C/C++
  under memory-safety policies), long-lived correctness-critical code, small static binaries.
- Choose Go: network services/ops tools where team speed and simple concurrency matter more than peak performance and latency tails.
- Choose Java/Kotlin (JVM): large business apps, mature enterprise frameworks, big teams, GC acceptable.
- Choose C++: existing C++ codebase/ecosystem (game engines, CUDA, Qt) where interop cost outweighs safety gains; add Rust at boundaries via `cxx`.

## Sources
- https://blog.rust-lang.org/ : release list and dates 1.85-1.99, patch releases, security/policy posts (accessed 2026-10-09)
- https://blog.rust-lang.org/2026/10/01/Rust-1.99.0/ : C variadics, Vec::into_parts, fs::set_times (2026-10-09)
- https://blog.rust-lang.org/2026/08/20/Rust-1.98.0/ , .../2026/07/09/Rust-1.97.0/ , .../2026/05/28/Rust-1.96.0/ : 1.96-1.98 features, wasm change, Cargo CVEs (2026-10-09)
- https://blog.rust-lang.org/2026/04/16/Rust-1.95.0/ , .../2026/03/05/Rust-1.94.0/ , .../2026/01/22/Rust-1.93.0/ : cfg_select!, if-let guards, TOML 1.1 (2026-10-09)
- https://blog.rust-lang.org/2025/12/11/Rust-1.92.0/ , .../2025/10/30/Rust-1.91.0/ , .../2025/09/18/Rust-1.90.0/ : never-type lints, Tier changes, LLD default (2026-10-09)
- https://blog.rust-lang.org/2025/08/07/Rust-1.89.0/ , .../2025/06/26/Rust-1.88.0/ : const `_`, File::lock, let chains, cargo GC (2026-10-09)
- https://blog.rust-lang.org/2025/05/15/Rust-1.87.0/ , .../2025/04/03/Rust-1.86.0/ : io::pipe, precise capturing in traits, trait upcasting (2026-10-09)
- https://blog.rust-lang.org/2025/02/20/Rust-1.85.0/ : edition 2024 change list, async closures (2026-10-09)
- https://releases.rs/ : 1.100 (2026-11-12) and 1.101 (2026-12-24) dates (2026-10-09)
- https://lwn.net/Articles/1092273/ : never type stable in 1.100, merged 2026-08-24 (2026-10-09)
- https://doc.rust-lang.org/cargo/reference/resolver.html : resolver 1/2/3, incompatible-rust-versions (2026-10-09)
- https://doc.rust-lang.org/cargo/reference/unstable.html : -Zscript and build-dir-new-layout still unstable (2026-10-09)
- https://github.com/tokio-rs/tokio/blob/master/README.md : LTS 1.51.x/1.53.x and MSRV policy (2026-10-09)
- https://github.com/async-rs/async-std (CHANGELOG) and crates.io : discontinued in 1.13.1, last release 1.13.2 (2025-08-15), use smol (2026-10-09)
- https://blog.rust-lang.org/2024/04/09/updates-to-rusts-wasi-targets/ : wasm32-wasip1/p2 added in 1.78, wasm32-wasi removed in 1.84 (2025-01-09) (2026-10-09)
- https://blog.rust-lang.org/2026/09/21/github-actions-leaking-secrets-when-miri-output-is-cached/ : Miri target/ cache leak and mitigations (2026-10-09)
- https://lwn.net/Articles/1049831/ : end of the kernel Rust experiment (Dec 2025) (2026-10-09)
- https://github.com/rustsec/advisory-db/blob/main/crates/arrayref/RUSTSEC-2026-0260.md and https://blog.rust-lang.org/2026/08/20/supply-chain-attack-on-arrayref : arrayref 0.3.10 malicious release (2026-10-09)
- https://crates.io/api/v1/crates/<name> : all crate versions listed under Currency check (2026-10-09)
- https://github.com/rust-lang/rust/issues/117078 : gen blocks tracking issue, still unstable (2026-10-09)
