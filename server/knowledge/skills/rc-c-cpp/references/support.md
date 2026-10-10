# C/C++ compiler support and hardening flags (as of 2026-10-09)

Check the live pages before relying on a row: gcc.gnu.org/projects/cxx-status.html, clang.llvm.org/cxx_status.html,
learn.microsoft.com/cpp (MSVC conformance), libcxx.llvm.org/Status, github.com/microsoft/STL/wiki/Changelog.

## Current compiler lines
| Compiler | Latest | Date | Default C++ | Default C | Notes |
|---|---|---|---|---|---|
| GCC 16 | 16.2 | 2026-08-07 | gnu++20 | gnu23 | 16.1 2026-04-30; C++26 reflection (`-freflection`), contracts, erroneous behavior |
| GCC 15 | 15.3 | 2026-06-12 | gnu++17 | gnu23 | `import std`, `#embed`, pack indexing |
| GCC 14 | 14.4 | 2026-06-26 | gnu++17 | gnu17 | `-fhardened`; modules usable with CMake |
| GCC 13 | 13.5 | 2026-09-11 | gnu++17 | gnu17 | final GCC 13 release; branch closed, unsupported |
| Clang 23 | 23.1.3 | 2026-10-06 | gnu++17 (since Clang 16) | gnu17 (no change in 23 notes) | 23.1.0 2026-08-25; `-Wlifetime-safety`; expansion statements partial |
| Clang 22 | 22.1.8 | 2026-06-16 | | | 22.1.0 2026-02-24 |
| MSVC 14.51 | VS 2026 18.6 | 2026-05 | C++14 for cl.exe; new VS projects use C++20 | | `/std:c++latest`, `/std:c++23preview` |
| MSVC 14.50 | VS 2026 18.0 | 2025-11 | | | Win10+ targets only, ARM32 removed |

## C++26 feature -> first compiler (P-number)
| Feature | GCC | Clang | MSVC |
|---|---|---|---|
| Static reflection P2996 | 16 (`-freflection`) | no | no (unverified) |
| Contracts P2900 | 16 | no | no (unverified) |
| Erroneous behavior P2795 | 16 | no | (unverified) |
| Expansion statements P1306 | 16 | 23 partial | (unverified) |
| constexpr exceptions P3068 | 16 | no | (unverified) |
| Structured binding packs P1061 | 16 | 21 | (unverified) |
| Pack indexing P2662 | 15 | 19 | (unverified) |
| `= delete("reason")` P2573 | 15 | 19 | (unverified) |
| `#embed` P1967 | 15 | no for C++ (cxx_status); C23 `#embed` since 19 | (unverified) |
| `_` placeholder P2169 | 14 | 18 | (unverified) |

## C++26 library
| Feature | libstdc++ | libc++ | MSVC STL |
|---|---|---|---|
| `std::execution` P2300 | not listed in GCC 16 notes | not started | not listed (use NVIDIA stdexec) |
| Hardened library P3471 | `_GLIBCXX_ASSERTIONS` (pre-standard) | not listed; hardening modes since 18 | `_MSVC_STL_HARDENING=1` since VS 2022 17.14; P3697 partial in 14.50 |
| `std::simd` | 16 (experimental) | no | no |
| `std::inplace_vector` | 16 (experimental) | no | no |
| `std::optional<T&>` | 16 | 22 | (unverified) |
| `std::function_ref` | 16 | 24 (unreleased trunk, 2026-10) | (unverified) |
| `std::indirect`/`polymorphic` | 16 | no | (unverified) |
| `std::text_encoding` | (unverified) | 23 | (unverified) |

## Modules
- Named modules: CMake 3.28+, Ninja 1.11+ or VS 2022/2026 generators; GCC 14+, Clang 16+, MSVC 14.34+.
- `import std`: Clang 18.1.2+, GCC 15+, MSVC 14.36+, CMake behind `CMAKE_EXPERIMENTAL_CXX_IMPORT_STD`, Ninja only.
- Header units: not supported by CMake. GCC still needs `-fmodules` (GCC 16 adds `--compile-std-module`).

## Hardening flags (OpenSSF guide, updated 2026-08-20)
Baseline (GCC/Clang):
```
-O2 -Wall -Wformat -Wformat=2 -Wconversion -Wimplicit-fallthrough -Werror=format-security
-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=3 -D_GLIBCXX_ASSERTIONS
-D_LIBCPP_HARDENING_MODE=_LIBCPP_HARDENING_MODE_FAST
-fstrict-flex-arrays=3 -fstack-clash-protection -fstack-protector-strong
-Wl,-z,nodlopen -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now -Wl,--as-needed -Wl,--no-copy-dt-needed-entries
```
| When | Add |
|---|---|
| GCC | `-Wtrampolines -fzero-init-padding-bits=all` (newer GCC only; check yours) |
| executables / shared libs | `-fPIE -pie` / `-fPIC -shared` |
| x86_64 | `-fcf-protection=full -fzero-call-used-regs=used-gpr` |
| AArch64 | `-mbranch-protection=standard -fzero-call-used-regs=used-gpr` |
| production | `-fno-delete-null-pointer-checks -fno-strict-overflow -fno-strict-aliasing -ftrivial-auto-var-init=zero` |
| C code | `-Werror=implicit -Werror=incompatible-pointer-types -Werror=int-conversion` |
| dev only | `-Werror` (never in distributed build files) |

- `_FORTIFY_SOURCE` needs optimization (`-O1`+) to do anything. `-D_FORTIFY_SOURCE=3`: GCC 12+, Clang 9+, glibc 2.34+.
- Flag minimums (OpenSSF): `-fhardened` GCC 14 (no Clang); `-fstrict-flex-arrays=3` GCC 13 / Clang 16; `-fzero-init-padding-bits=all`
  GCC 15; `-ftrivial-auto-var-init` GCC 12 / Clang 8. `counted_by`: GCC 15 (pointer members GCC 16).
- libc++ modes: `NONE` (default unless vendor sets one), `FAST` (production), `EXTENSIVE`, `DEBUG` (tests only). libc++ 18+.
- MSVC: `/W4 /permissive- /sdl /guard:cf /Qspectre` (as needed), `/fsanitize=address` (x64/x86; ARM64 preview in 14.50), linker `/DYNAMICBASE /HIGHENTROPYVA /CETCOMPAT` (from MSVC docs knowledge, not re-fetched 2026-10-09).
- Clang CFI: `-fsanitize=cfi -flto -fvisibility=hidden` (needs LTO and hidden visibility).

## Sanitizer matrix
| Sanitizer | Flag | Finds | Combine with |
|---|---|---|---|
| ASan | `-fsanitize=address` | OOB, UAF, double free, leaks (LSan) | UBSan |
| UBSan | `-fsanitize=undefined` (+ `-fno-sanitize-recover=all` to fail) | signed overflow, bad shifts, misaligned, null | ASan |
| TSan | `-fsanitize=thread` | data races | alone |
| MSan | `-fsanitize=memory` (Clang, all code incl. libc++ instrumented) | uninitialized reads | alone |
| HWASan / MTE | `-fsanitize=hwaddress` / GCC 16 `-fsanitize=memtag` stack tagging (AArch64) | like ASan, lower overhead | alone |
| Valgrind memcheck | no rebuild | uninit, leaks, OOB heap | not with ASan |
