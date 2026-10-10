# Rust releases 1.85 to 1.99: what each shipped (as of 2026-10)

Source for every line: the "Announcing Rust 1.NN" posts on blog.rust-lang.org (accessed 2026-10-09). Highlights only; read the
release notes for the full list. Stable ships every 6 weeks; only the newest stable gets fixes (no LTS from the Rust project).

## 1.99 (2026-10-01)
- Language: C-variadic function *definitions* (`extern "C" fn f(args: ...)` with `VaList`, `VaArgSafe`).
- `Layout::for_value_raw`, `mem::size_of_val_raw`, `mem::align_of_val_raw`.
- `Vec::into_parts` / `from_parts`, `Box::into_non_null` / `from_non_null`, `String::from_utf8_lossy_owned`, `VecDeque::retain_back`,
  `IntoIterator for Box<[T; N]>`, `fs::set_times` / `set_times_nofollow`. `Box::leak` docs now discourage leak-then-unleak round trips.

## 1.98 (2026-08-20), patch 1.98.1 (2026-09-03)
- f32/f64 `algebraic_add/sub/mul/div/rem` (opt-in fast-math style reassociation).
- `NumBuffer` + integer `format_into` (fast decimal formatting without `fmt`), `str::substr_range`, `slice::subslice_range`,
  `String::from_utf16le/be(_lossy)`, `strip_circumfix`, `Atomic*::from_mut`, `NonZero::from_str_radix`, `std::range::legacy`.

## 1.97 (2026-07-09), patch 1.97.1 (2026-07-16)
- v0 symbol mangling is the default (legacy mangling nightly-only). Linker output now warns by default (`linker_messages`; release notes).
- Integer `highest_one`, `lowest_one`, `isolate_*_one`, `bit_width`. Cargo: `CARGO_BUILD_WARNINGS` (allow/warn/deny) without cache invalidation.

## 1.96 (2026-05-28), patch 1.96.1 (2026-06-30)
- New `Copy` range types in `core::range` (RFC 3550): `Range`, `RangeFrom`, `RangeInclusive` implement `IntoIterator`, not `Iterator`.
  `a..b` syntax still makes the legacy types (an edition may switch it later).
- `assert_matches!` / `debug_assert_matches!` (`use std::assert_matches;`, not in prelude).
- wasm targets: `--allow-undefined` no longer passed; undefined symbols are link errors.
- Cargo CVE-2026-5223 (tarball symlink extraction) and CVE-2026-5222 fixed; crates.io users were not affected.

## 1.95 (2026-04-16)
- `cfg_select!` (built-in replacement for the `cfg-if` crate). **if-let guards** in `match` arms.
- `Vec::push_mut` / `insert_mut` (return `&mut T`), `VecDeque` / `LinkedList` `push_*_mut`, `Atomic*::update` / `try_update`,
  `hint::cold_path`, `bool: TryFrom<int>`. Custom JSON target specs removed from stable rustc.

## 1.94 (2026-03-05), patch 1.94.1 (2026-03-26, Cargo CVE-2026-33056 in the `tar` crate)
- `slice::array_windows`, `element_offset`, `LazyLock/LazyCell::get/get_mut/force_mut`, `Peekable::next_if_map`.
- Cargo: `include` in `.cargo/config.toml`; TOML 1.1 in manifests (raises dev MSRV of the manifest; rewritten on publish).

## 1.93 (2026-01-22), patch 1.93.1 (2026-02-12)
- musl targets on musl 1.2.5. `String/Vec::into_raw_parts`, `slice::as_array`, `VecDeque::pop_front_if/pop_back_if`, `fmt::from_fn`,
  `MaybeUninit` slice helpers, `#[cfg]` on individual `asm!` lines.

## 1.92 (2025-12-11)
- Never-type future-compat lints deny-by-default (`never_type_fallback_flowing_into_unsafe`, `dependency_on_unit_never_type_fallback`).
- `RwLockWriteGuard::downgrade`, `Box/Rc/Arc::new_zeroed(_slice)`, `btree_map::Entry::insert_entry`. Unwind tables kept with `panic=abort` on Linux.

## 1.91 (2025-10-30)
- `aarch64-pc-windows-msvc` Tier 1. Warn lint on dangling raw pointers to locals.
- `strict_*` integer ops, `carrying_add`/`borrowing_sub`, `BTreeMap/BTreeSet::extract_if`, `Duration::from_mins/from_hours`,
  `Path::file_prefix`, `PathBuf::add_extension`, `str::floor_char_boundary/ceil_char_boundary`, `core::iter::chain`.

## 1.90 (2025-09-18)
- **LLD is the default linker on x86_64-unknown-linux-gnu** (opt out: `-C linker-features=-lld`). `cargo publish --workspace`.
- `x86_64-apple-darwin` demoted to Tier 2 (with host tools).

## 1.89 (2025-08-07)
- `_` for inferred const generic args (`[false; _]`). `mismatched_lifetime_syntaxes` lint (write `Iter<'_, u8>`).
- `File::lock/lock_shared/try_lock/unlock`, `Result::flatten`, `NonNull::from_ref/from_mut`. Cross-compiled doctests now run.

## 1.88 (2025-06-26)
- **let chains** (`if let Some(x) = a && x > 0 && let Ok(y) = f(x)`), edition 2024 only. `#[unsafe(naked)]` functions. `cfg(true)`/`cfg(false)`.
- `Cell::update`, `HashMap/HashSet::extract_if`, `slice::as_chunks`. Cargo auto-GC of its cache (3 months network, 1 month local).

## 1.87 (2025-05-15)
- `io::pipe()` anonymous pipes, precise capturing `+ use<..>` in trait `impl Trait`, `asm!` label operands, safe `std::arch` intrinsics under
  matching `#[target_feature]`. `Vec::extract_if`, `String::extend_from_within`, `is_multiple_of`, `cast_signed/cast_unsigned`.

## 1.86 (2025-04-03)
- **Trait upcasting** (`&dyn Sub` -> `&dyn Super`). Safe `#[target_feature]` fns. Debug null-pointer checks. `missing_abi` lint.
- `slice::get_disjoint_mut`, `HashMap::get_disjoint_mut`, `Vec::pop_if`, `OnceLock::wait`.

## 1.85 (2025-02-20): Rust 2024 edition
- **Async closures** (`async || {}`, `AsyncFn*` traits). `#[diagnostic::do_not_recommend]`. Tuple `FromIterator/Extend` up to 12.
- Edition 2024: RPIT captures all in-scope lifetimes (use `use<..>` to narrow); `if let` and tail-expression temporaries drop earlier;
  `unsafe extern` blocks; `#[unsafe(no_mangle)]` / `#[unsafe(export_name)]` / `#[unsafe(link_section)]`; `unsafe_op_in_unsafe_fn` warns;
  references to `static mut` are errors; never-type fallback change; `gen` keyword reserved; `Future`/`IntoFuture` in prelude;
  `std::env::set_var/remove_var` are `unsafe`; resolver 3 (MSRV-aware) default; rustfmt style editions; combined doctests.

## Coming / not stable yet (as of 2026-10-09)
- Never type `!` stable in **1.100 (2026-11-12)**, `Infallible` becomes an alias for `!` (LWN, merged 2026-08-24).
- `gen` blocks: nightly only (`#![feature(gen_blocks)]`), no stabilization date.
- Next-generation trait solver and next borrow checker (Polonius line): enabled on nightly only (blog, 2026-08).
- `cargo -Zscript` single-file packages and `build-dir-new-layout`: still unstable in the Cargo book.
- Next edition: none announced on blog.rust-lang.org (checked 2026-10-10).
