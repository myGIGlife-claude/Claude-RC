---
name: rc-c-cpp
description: Current C (C17/C23/C2y) and C++ (C++20/23/26) practice, GCC 16/Clang 23/MSVC 14.51 support, hardening flags, sanitizers, clang-tidy, CMake presets, vcpkg/Conan, GoogleTest/Catch2, fuzzing, gdb/rr, UB and concurrency pitfalls. Use when writing, reviewing or building *.c, *.h, *.cpp, *.hpp, *.cppm, CMakeLists.txt, CMakePresets.json, meson.build, conanfile.py, vcpkg.json, .clang-tidy, .clang-format, or when advice might be pre-C++17.
---
# C and C++  (as of 2026-10)

> Facts are dated (see Sources). Compiler support moves every release: check the compiler's own status page before relying on a
> C++26 feature. Anything marked (unverified) is a lead, not a fact.

## Currency check
- **Standards**
  - C23 = ISO/IEC 9899:2024 (adopted 2024). Next C ("C2y", working draft N3886, 2026-05): `_Countof` / `<stdcountof.h>`, named loops
    (`break label;`), `if` declarations, case ranges, `++/--` on complex. Publication year (unverified; drafts call it 202y).
  - C++23 = ISO/IEC 14882:2024. **C++26 technically complete 2026-03** (London/Croydon meeting), sent to DIS ballot; ISO publication
    expected late 2026 (unverified whether published yet). Final vote 114 for, 12 against, 3 abstain (contracts were the dispute).
  - C++26 headline: static reflection (P2996), contracts (`pre`/`post`/`contract_assert`, P2900), `std::execution` senders/receivers
    (P2300), **erroneous behavior** for uninitialized locals (P2795: reading one is no longer UB), hardened standard library (P3471),
    `std::simd`, `std::inplace_vector`, `#embed`, pack indexing, `= delete("why")`, `_` placeholder, expansion statements
    (`template for`), `std::optional<T&>`, `std::function_ref`, `std::indirect`/`std::polymorphic`.
  - C++29 work started (3-year cycle): focus on more UB removal and safety profiles.
- **Compilers** (details: `references/support.md`)
  - GCC 16.1 2026-04-30, **16.2 2026-08-07**; maintained 15.3, 14.4, 13.5 (2026-09-11). GCC 17 in development.
    GCC 16 **defaults to `-std=gnu++20`** (was gnu++17) and C++20 in libstdc++ is no longer experimental; experimental C++26 reflection
    (`-freflection`), contracts, expansion statements, constexpr exceptions, `std::simd`, `std::inplace_vector`.
    GCC 15 **defaults C to `-std=gnu23`** (`bool`/`true`/`false` keywords, `()` means no parameters).
  - LLVM/Clang **23.1.0 2026-08-25**, latest 23.1.3 (2026-10-06); 22.1.x (last 22.1.8, 2026-06-16). Clang has NO reflection, contracts or
    erroneous-behavior support upstream yet; expansion statements partial (23). New `-Wlifetime-safety` (23).
  - MSVC Build Tools **14.51** (Visual Studio 2026 18.6, 2026-05); 14.50 = VS 2026 18.0 (2025-11). `/std:c++latest` for new features,
    `/std:c++23preview` to stop at C++23. Binary compatible back to VS 2015. Targets Windows 10+ only; ARM32 toolchain removed.
- **Libraries**: libc++ has no `std::execution`, `std::simd`, `inplace_vector` yet (2026-10). libstdc++ 16 has `simd`/`inplace_vector`,
  no `std::execution` listed. MSVC STL: none of these listed. Use NVIDIA `stdexec` (reference impl, no tagged releases) if you need P2300 now.
- **Tools** (latest, 2026-10): CMake 4.4.4, Ninja 1.13.2, Meson 1.12.1, Bazel 9.3.0, vcpkg 2026.07.29, Conan 2.33.0,
  GoogleTest 1.18.0, Catch2 3.16.1, doctest 2.5.3, Google Benchmark 1.9.5, AFL++ 5.03c, cppcheck 2.22.0, CodeQL CLI 2.27.2,
  include-what-you-use 0.27, Valgrind 3.27.1 (2026-05-20), rr 5.9.0 (2025-02), {fmt} 12.2.0.
- **Policy**: CISA/NSA joint guide on memory-safe languages (2025-06-23); CISA/FBI expected vendors to publish a memory-safety roadmap
  by 2026-01-01 (guidance, not law, but now asked in procurement). New critical-infrastructure code in C/C++ needs a justification.
- **Older versions** (do not upgrade unless asked):
  - Ubuntu 24.04 / RHEL 9-era toolchains: GCC 13/14, Clang 18: C++20 mostly, C++23 partial; no `import std` (needs GCC 15 / Clang 18.1.2+ / MSVC 14.36+).
  - GCC < 15: C default gnu17 (`bool` needs `<stdbool.h>`). GCC < 16: C++ default gnu++17, so `std::format`, concepts need `-std=c++20`.
  - VS 2022 (MSVC 14.3x, v143): still supported; C++20 complete, C++23 partial. Code must build there if the project targets it.
  - libc++ hardening modes need libc++ 18+; older uses `_LIBCPP_ENABLE_ASSERTIONS` (removed). `-D_FORTIFY_SOURCE=3` needs GCC 12+/Clang 9+ and glibc 2.34+ (glibc part unverified).
  - CMake 3.x projects: fine; CMake 4 errors on `cmake_minimum_required` below 3.5 (escape hatch: `-DCMAKE_POLICY_VERSION_MINIMUM=3.5`).

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| `new`/`delete`, owning `T*` | `std::make_unique`, `std::vector`, values; `shared_ptr` only for real shared ownership | C++14 |
| `NULL`, `0` as pointer | `nullptr` (C23 also has `nullptr`) | C++11 / C23 |
| `(T)x` C-style casts | `static_cast`, `std::bit_cast`; `reinterpret_cast` only at boundaries | C++11/20 |
| `std::auto_ptr` | `std::unique_ptr` (auto_ptr removed) | C++17 |
| `T* p, size_t n` params | `std::span<T>` / `std::string_view` (non-owning, don't store) | C++20/17 |
| error codes + out-params, or exceptions for expected failures | `std::expected<T,E>` / `std::optional<T>` | C++23/17 |
| `union` + tag enum | `std::variant` + `std::visit` | C++17 |
| `printf`, iostream `<<` chains, `sprintf` | `std::format`, `std::print`/`println` | C++20/23 |
| SFINAE, `enable_if` | `concept`s and `requires` | C++20 |
| `begin()/end()` loops with index math | ranges / `std::views` (watch dangling views) | C++20 |
| `std::thread` + manual join | `std::jthread` + `std::stop_token` | C++20 |
| `#define` constants, `constexpr` only for trivia | `constexpr`/`consteval`, `constinit` for globals | C++20 |
| `gets`, `strcpy`, `strcat`, `sprintf`, `strtok` | `fgets`, `snprintf`, `memcpy` with checked sizes, `strtok_r`; in C++ `std::string` | always (`gets` removed C11) |
| K&R / `f()` meaning "unspecified args" | prototypes; in C23 `f()` == `f(void)` | C23 |
| `ARRAY_SIZE` macro | `std::size(a)`; C2y `countof(a)` | C++17 / C2y |
| `#include <bits/stdc++.h>`, `using namespace std;` in headers | explicit includes, qualified names; `import std;` where supported | always / C++23 |
| `_LIBCPP_ENABLE_ASSERTIONS`, `_GLIBCXX_DEBUG` in release | `_LIBCPP_HARDENING_MODE_FAST`, `_GLIBCXX_ASSERTIONS` (ABI-safe) | libc++ 18 |
| `-D_FORTIFY_SOURCE=2` | `-D_FORTIFY_SOURCE=3` | GCC 12 / Clang 9 |
| Hand-written Makefiles, `include_directories`/`add_definitions` globals | target-based CMake (`target_*`), `CMakePresets.json`, Ninja | CMake 3.x |
| git submodules for deps, vendored header-only sprawl | vcpkg manifest (`vcpkg.json`) or Conan 2, `FetchContent` for small deps | — |
| Conan 1 recipes / `conanbuildinfo.cmake` | Conan 2 (`CMakeToolchain`, `CMakeDeps`, profiles) | Conan 2.0 |
| `<experimental/coroutine>`, `/await` (MSVC) | `<coroutine>`; experimental headers deprecated 14.51 | C++20 |
| GCC JSON diagnostics | SARIF (`-fdiagnostics-format=sarif-file`) | GCC 16 (JSON removed) |
| `= {0}` to zero a struct incl. padding | `= {}` (GCC 15 no longer clears padding for `{0}` on unions) | GCC 15 |

## Do this
**Project skeleton (new C++ project)**
```
CMakeLists.txt  CMakePresets.json  vcpkg.json (or conanfile.py)  .clang-format  .clang-tidy
src/  include/<proj>/  tests/  fuzz/
```
```cmake
cmake_minimum_required(VERSION 3.28...4.4)
project(app LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 23)          # 20 if VS 2022 / GCC 13 must build it
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)   # clang-tidy, clangd
add_library(core src/core.cpp)
target_include_directories(core PUBLIC include)
target_compile_options(core PRIVATE $<$<CXX_COMPILER_ID:GNU,Clang>:-Wall -Wextra -Wpedantic -Wconversion>)
add_executable(app src/main.cpp)
target_link_libraries(app PRIVATE core)
```
- Presets: `CMakePresets.json` checked in (configure/build/test/workflow presets: `dev`, `release`, `asan`), `CMakeUserPresets.json` git-ignored.
  `cmake --workflow --preset dev` runs configure+build+test in one go.
- Dependencies, least first: (1) stdlib, (2) one small dep via `FetchContent_Declare(... URL <tarball> URL_HASH SHA256=...)` or
  `GIT_TAG <full sha>` with `FIND_PACKAGE_ARGS` so a system package wins, (3) vcpkg manifest with `builtin-baseline` pinned, or
  Conan 2 lockfile. Don't add Boost/abseil/fmt for one function: `std::format`, `<ranges>`, `<charconv>` cover most.
- Meson + Ninja is a fine choice for C and Linux system projects; Bazel only for big polyglot monorepos.
- Modules reality check (2026-10): named modules work with CMake 3.28+ + Ninja and GCC 14+/Clang 16+/MSVC 14.34+; `import std` still needs
  `CMAKE_EXPERIMENTAL_CXX_IMPORT_STD` (experimental), header units unsupported in CMake. Libraries shipped to others: keep headers.
  Do not convert an existing codebase to modules unasked.

**Idioms**
```cpp
#include <expected>
#include <span>
#include <print>
enum class ParseError { empty, bad_digit };
std::expected<int, ParseError> sum(std::span<const std::string_view> xs) {
    if (xs.empty()) return std::unexpected(ParseError::empty);
    int total = 0;
    for (auto s : xs) {
        int v{};
        auto [p, ec] = std::from_chars(s.data(), s.data() + s.size(), v);
        if (ec != std::errc{} || p != s.data() + s.size()) return std::unexpected(ParseError::bad_digit);
        total += v;   // overflow: use checked add if inputs are untrusted
    }
    return total;
}
int main() { if (auto r = sum({}); !r) std::println("error {}", int(r.error())); }
```
- RAII for every resource (files, locks, sockets, handles): `unique_ptr` with a custom deleter for C handles. Rule of zero; if you write one
  of dtor/copy/move, consider all five.
- Pass: small/trivial by value; read-only big by `const&`; sinks by value + `std::move`; views (`span`, `string_view`) never stored past the call.
- `const` by default, `[[nodiscard]]` on functions whose result must be checked, `enum class`, `std::array` over C arrays.
- Coroutines: there is still no standard task type in C++23 (C++26 adds `std::execution::task`, unverified in any shipping lib); use a library
  (cppcoro forks, asio, folly) or `std::generator` (C++23) for lazy sequences.
- C++26 now: only behind compiler-specific flags (GCC 16 `-std=c++26 -freflection`). Feature-test macros (`__cpp_contracts`,
  `__cpp_impl_reflection` (name unverified)) not compiler versions.

**C (C17/C23)**
- `-std=c17` or `-std=c23` explicitly. C23 gives `nullptr`, `constexpr` objects, `typeof`, `[[nodiscard]]`, `static_assert` w/o message,
  `#embed`, `<stdckdint.h>` (`ckd_add(&r, a, b)` returns true on overflow), `<stdbit.h>`, `_BitInt(N)`, `memset_explicit`.
- Bounds: annotate flexible arrays with `__attribute__((counted_by(n)))` (GCC 15+/Clang 18+, GCC 16 also on pointer members) and
  build with `-fstrict-flex-arrays=3`. Clang `-fbounds-safety` is still being upstreamed (Apple fork; not usable upstream 2026-10).
- Ownership comments on every pointer API (who frees); pair every `malloc` with a single `free` path (`goto cleanup` is fine).

**Core Guidelines essentials** (isocpp CppCoreGuidelines): R.1 RAII, R.11 no naked new/delete, I.11 never transfer ownership by raw pointer,
F.15/F.16 pass cheap by value, ES.20 always initialize, ES.48 avoid casts, Con.1 const by default, CP.20 RAII locks, CP.8 no `volatile` for sync,
SF.7 no `using namespace` in headers, C.21 rule of zero/five. Enforce via clang-tidy `cppcoreguidelines-*`.

**Concurrency**
- `std::jthread` + `stop_token`; `std::scoped_lock` for multiple mutexes; `std::atomic<T>` with default `seq_cst` unless profiled.
  Relaxed/acquire/release only with a written reason; `volatile` is not synchronization. `std::latch`/`barrier`/`counting_semaphore` (C++20).
- A data race is UB, not "a stale value". Run TSan in CI on the concurrent tests.

**Interop / ABI**
- Export a C ABI (`extern "C"`, opaque handle + create/destroy, no exceptions or STL types across) for FFI to Rust/Python/Go/Swift.
  Catch all exceptions at the boundary. Bindings: pybind11 / nanobind for Python, `cxx`/`bindgen` for Rust.
- Shared libs: `-fvisibility=hidden` + explicit export macro; SONAME bump on ABI break; check with `abidiff` (libabigail) in CI.
- libstdc++ dual ABI, MSVC `/MD` vs `/MT` and debug/release runtimes must match across all linked objects. GCC 16 changed some ABI
  (`std::variant`, atomic wait, `std::format`); C++20 code built with GCC < 16 may not link with GCC 16 objects (check release notes).

**Embedded / freestanding**
- `-ffreestanding`, no exceptions/RTTI if the platform forbids (`-fno-exceptions -fno-rtti`), no heap after init, `std::array`,
  `std::span`, `std::expected`, `inplace_vector` (C++26) fit. Rules: MISRA C:2025 / MISRA C++:2023 (unverified edition names),
  AUTOSAR C++14 now folded into MISRA C++:2023, CERT C/C++ for security. Static analyzers check these, not reviewers.

**When to pick Rust or Go instead**: new network-facing service or parser with no C++ codebase and no hard C++ dependency: Go (services)
or Rust (systems, no GC). Stay in C/C++ when extending an existing codebase, using C++-only ecosystems (game engines, CUDA, Qt, HPC),
or on toolchains Rust lacks. Mixed: new memory-sensitive components in Rust behind a C ABI.

## Security
- Memory safety first: bounds (`span`, `.at()` at trust boundaries, hardened library), lifetime (no `string_view`/`span`/iterator stored
  beyond owner; `-Wdangling`, `-Wlifetime-safety`), initialization (`-ftrivial-auto-var-init=zero`), integer overflow (`ckd_*`,
  `std::in_range`, `-fno-strict-overflow` in prod).
- Baseline hardening flags (OpenSSF guide 2026-08-20; full list `references/support.md`):
  `-O2 -Wall -Wformat=2 -Wconversion -Wimplicit-fallthrough -Werror=format-security -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=3
  -D_GLIBCXX_ASSERTIONS -D_LIBCPP_HARDENING_MODE=_LIBCPP_HARDENING_MODE_FAST -fstrict-flex-arrays=3 -fstack-clash-protection
  -fstack-protector-strong -fPIE -pie -Wl,-z,relro,-z,now,-z,noexecstack` + x86_64 `-fcf-protection=full`, AArch64 `-mbranch-protection=standard`.
  GCC 14+ shortcut: `-fhardened` (Linux glibc targets).
- Hardened library: libc++ `FAST` mode in production (cheap bounds checks), `DEBUG` in tests; libstdc++ `_GLIBCXX_ASSERTIONS`
  (GCC 15 enables it by default at `-O0`); MSVC `_MSVC_STL_HARDENING=1` (unverified version). Google measured ~0.3% overhead for libc++ hardening (unverified).
- Never: `system()`/`popen` with user data, format strings from input, `rand()` for secrets (use OS CSPRNG: `getrandom`, `BCryptGenRandom`),
  hand-rolled crypto (use OpenSSL 3 / BoringSSL / libsodium), `memset` to wipe secrets (use `memset_explicit` C23 / `explicit_bzero`).
- Parsers of untrusted input get a fuzz target and run under ASan+UBSan in CI. Publish a memory-safety roadmap if you sell into gov/critical infra.

## Performance & quality
- Measure first: `perf record -g` / `perf stat -e cache-misses`, `hotspot`/flamegraphs, VTune, Tracy for games; Google Benchmark for micro.
- Release: `-O2` (or `-O3` measured), LTO (`CMAKE_INTERPROCEDURAL_OPTIMIZATION ON`), PGO (`-fprofile-generate`/`-fprofile-use`; MSVC
  sample PGO preview in 14.51), BOLT for big binaries. `-march=native` only for binaries that never leave the build machine.
- Data layout beats micro-tweaks: contiguous `std::vector`, SoA for hot loops, avoid `std::list`/`std::map` in hot paths
  (`std::flat_map` C++23), reserve capacity, avoid false sharing (`std::hardware_destructive_interference_size`).
- Build speed: Ninja, ccache/sccache, precompiled headers or modules, IWYU 0.27 to cut includes, `-ftime-trace` (Clang) to find slow headers.

## Testing & tooling
- Default stack: CMake + Ninja + presets; clang-format (pin version in CI); clang-tidy with `.clang-tidy` (bugprone-*, cert-*,
  cppcoreguidelines-*, modernize-*, performance-*, readability-* minus noisy); cppcheck 2.22; CodeQL (GitHub, free for public repos)
  or Coverity Scan for open source; compiler warnings as errors in CI only (never shipped as `-Werror` in a source release).
- Tests: GoogleTest 1.18 (+gMock) or Catch2 v3 (not v2 single-header) or doctest (fast compile); register with `ctest` (`gtest_discover_tests`,
  `catch_discover_tests`). Run `ctest --output-on-failure -j`.
- Sanitizer CI jobs: ASan+UBSan (`-fsanitize=address,undefined -fno-omit-frame-pointer -fno-sanitize-recover=all`), TSan separately,
  MSan only with an instrumented libc++ (Clang). Valgrind 3.27 where sanitizers can't (prebuilt binaries); not combined with ASan.
- Fuzzing: libFuzzer (`-fsanitize=fuzzer,address`, `LLVMFuzzerTestOneInput`), AFL++ 5.x for black-box/persistent mode; OSS-Fuzz for OSS.
- Debugging: gdb / lldb, `rr record`/`rr replay` for reverse debugging on Linux (Intel/AMD/arm64), core dumps via `coredumpctl` (systemd)
  or `ulimit -c unlimited`; keep split debug info (`-g -gsplit-dwarf` / separate `.debug` files, `debuginfod`).

## Common mistakes in AI-written code
- Returning `std::string_view`/`std::span` to a local or temporary; range views over temporaries (`for (auto x : f() | views::filter(..))`
  when `f()` returns a view of a temporary). Lambda capturing `[&]` in a thread/coroutine that outlives the scope.
- Coroutines taking parameters by reference (`const std::string&`) — the reference dangles after the first suspend.
- Claiming C++26 features "work in Clang/MSVC": reflection, contracts and `std::execution` are GCC-16-experimental or library-only (2026-10).
  Inventing APIs: `std::print` needs `<print>` (C++23); `std::expected` needs C++23 and `<expected>`; there is no `std::task` in C++23.
- Signed overflow, shift by >= width, `int` index vs `size_t` loops, `abs(INT_MIN)`, strict-aliasing via pointer casts (use `memcpy`/`bit_cast`),
  reading inactive union members in C++, `memcpy` on non-trivially-copyable types, modifying a container while iterating.
- `std::shared_ptr` everywhere; `std::move` on a `const` object (silently copies); `std::move` then reuse.
- `strncpy` "fixes" (does not NUL-terminate), `snprintf` return value ignored (truncation), `scanf("%s")`, `atoi` (no errors; use `from_chars`/`strtol`).
- `volatile`/`sleep` used for thread sync; double-checked locking without atomics; `std::thread` never joined (terminate).
- CMake: global `include_directories`, `CMAKE_CXX_FLAGS` string-edits, `file(GLOB)` for sources, `FetchContent` with a branch name,
  `cmake_minimum_required(VERSION 3.0)` (errors on CMake 4).
- Writing `-std=c++17` flags into code meant for GCC 16 "for safety" when the project needs C++20; or forcing `/std:c++latest` in shipped builds.

## Before you ship
- [ ] Builds warning-clean on every supported compiler (GCC, Clang, MSVC versions the project claims) with `-Wall -Wextra -Wconversion`.
- [ ] Hardening flags on in release; hardened library mode chosen; PIE, RELRO/now, stack protector verified (`checksec` or `readelf`).
- [ ] Unit tests pass under ASan+UBSan and TSan (concurrent code); fuzz targets for every untrusted-input parser ran (≥ minutes in CI).
- [ ] clang-tidy + cppcheck/CodeQL clean or findings triaged; clang-format applied.
- [ ] No raw owning pointers, no banned C functions, every error path frees/closes (RAII or single cleanup).
- [ ] ABI: exported symbols intended, SONAME/version bumped on break, runtime library choices consistent.
- [ ] Dependencies pinned (vcpkg baseline / Conan lockfile / FetchContent hash); licenses checked; no new dep a few std lines could replace.
- [ ] Release artifacts carry separate debug symbols; crash/core collection documented; rollback = previous binary + matching symbols.

## Sources
- https://gcc.gnu.org/ : GCC 16.2 (2026-08-07), 15.3, 14.4, 13.5 release dates, GCC 17 in development (2026-10-09)
- https://gcc.gnu.org/pipermail/gcc/2026-April/248065.html : GCC 16.1 released 2026-04-30 (via search index, 2026-10-09)
- https://gcc.gnu.org/gcc-16/changes.html : default gnu++20, C++26 reflection/contracts/expansion statements, libstdc++ simd/inplace_vector/optional<T&>, ABI changes, JSON diags removed, C2y items, counted_by on pointers (2026-10-09)
- https://gcc.gnu.org/gcc-15/changes.html : default gnu23, C2y named loops/if decls/case ranges, `{0}` padding change, assertions at -O0, std module (2026-10-09)
- https://gcc.gnu.org/projects/cxx-status.html : per-feature GCC versions for C++26 (reflection needs -freflection) (2026-10-09)
- https://clang.llvm.org/cxx_status.html : Clang C++26 status (no reflection/contracts/P2795), modules partial (2026-10-09)
- https://releases.llvm.org/23.1.0/tools/clang/docs/ReleaseNotes.html : Clang 23 changes, -Wlifetime-safety, C2y work (2026-10-09)
- https://github.com/llvm/llvm-project/releases : 23.1.0 2026-08-25, 23.1.3 2026-10-06, 22.1.8 2026-06-16 (GitHub API, 2026-10-09)
- https://learn.microsoft.com/en-us/cpp/overview/what-s-new-for-msvc : MSVC 14.51 (VS 2026 18.6, May 2026), 14.50, /std flags, removals (2026-10-09)
- https://github.com/microsoft/STL/wiki/Changelog : MSVC STL C++26 items in 14.51 (2026-10-09)
- https://libcxx.llvm.org/Hardening.html : libc++ hardening modes and macro (2026-10-09)
- https://libcxx.llvm.org/Status/Cxx26.html : libc++ status of execution/simd/inplace_vector (2026-10-09)
- https://herbsutter.com/2026/03/29/c26-is-done-trip-report-march-2026-iso-c-standards-meeting-london-croydon-uk/ : C++26 complete, contents, C++29 direction (2026-10-09)
- https://www.theregister.com/software/2026/03/31/contracts-are-in-c26-despite-disagreement-over-their-value/5229065 : vote count (lead; secondary, 2026-10-09)
- https://www.open-std.org/jtc1/sc22/wg14/ and https://www.open-std.org/jtc1/sc22/wg14/www/docs/n3886.pdf : C23 adopted 2024, C2y draft N3886 (2026-10-09)
- https://www.open-std.org/jtc1/sc22/wg14/www/docs/n3355.htm : named loops (2026-10-09)
- https://best.openssf.org/Compiler-Hardening-Guides/Compiler-Options-Hardening-Guide-for-C-and-C++.html : baseline flags, updated 2026-08-20 (2026-10-09)
- https://www.cisa.gov/news-events/alerts/2025/06/24/new-guidance-released-reducing-memory-related-vulnerabilities : CISA/NSA MSL guide 2025-06 (2026-10-09)
- https://discourse.llvm.org/t/rfc-enforcing-bounds-safety-in-c-fbounds-safety/70854 : -fbounds-safety upstreaming (2026-10-09)
- https://cmake.org/cmake/help/latest/manual/cmake-cxxmodules.7.html : modules/import std support matrix (2026-10-09)
- https://cmake.org/cmake/help/latest/release/4.0.html : CMake 4 removed < 3.5 compat, CMAKE_POLICY_VERSION_MINIMUM (2026-10-09)
- GitHub releases API (2026-10-09): CMake 4.4.4, vcpkg 2026.07.29, Conan 2.33.0, GoogleTest 1.18.0, Catch2 3.16.1, doctest 2.5.3, Meson 1.12.1, Bazel 9.3.0, Ninja 1.13.2, AFL++ 5.03c, cppcheck 2.22.0, rr 5.9.0, fmt 12.2.0, CodeQL 2.27.2, IWYU 0.27, Google Benchmark 1.9.5
- https://valgrind.org/ : Valgrind 3.27.1, 2026-05-20 (2026-10-09)
- https://isocpp.github.io/CppCoreGuidelines/CppCoreGuidelines : rule IDs (from knowledge; not re-fetched, 2026-10-09)
