# Kotlin language features and library versions (as of 2026-10-09)

## Language features by status

| Feature | Status | Since | Minimal form |
|---|---|---|---|
| K2 compiler | Stable, only compiler | 2.0.0 (2024-05-21); K1 removed in 2.4 | `-language-version=1.9` no longer accepted |
| Sealed interfaces | Stable | 1.5 | `sealed interface Result<out T>` |
| Value classes | Stable (single field on JVM) | 1.5 | `@JvmInline value class UserId(val raw: String)` |
| Data objects | Stable | 1.9 | `data object Loading : UiState` |
| Guard conditions in `when` | Stable | 2.2.0 | `is Ok if r.value > 0 -> ...` |
| Non-local `break`/`continue` | Stable | 2.2.0 | `list.forEach { if (it < 0) break }` inside a loop |
| Multi-dollar interpolation | Stable | 2.2.0 | `$$"""{"$schema": "$$id"}"""` |
| Nested type aliases | Stable | 2.3.0 | `class A { typealias Ids = Set<Id> }` |
| Data-flow exhaustiveness in `when` | Stable | 2.3.0 | early returns count toward exhaustiveness |
| `kotlin.time.Instant` / `Clock` | Stable | 2.3.0 | use instead of `kotlinx.datetime.Instant` |
| Context parameters | Stable (not explicit context arguments or callable refs) | 2.4.0 | `context(log: Logger) fun f()` |
| Explicit backing fields | Stable | 2.4.0 | `val x: StateFlow<Int> field = MutableStateFlow(0)` |
| `@all:` annotation target, new defaulting rules | Stable | 2.4.0 | `@all:Email val email: String` |
| Common `Uuid` API | Stable | 2.4.0 | `Uuid.parse()`; V4/V7 generators (`generateV4()`/`generateV7()`) still experimental (opt-in); whether `Uuid.random()` still needs opt-in: unverified |
| `when` via `invokedynamic` on JVM 21+ | Stable, default | 2.4.20 | none |
| Unused return value checker | Experimental | 2.3.0 | `-Xreturn-value-checker=check` |
| Context-sensitive resolution | Preview/experimental | 2.2.0 | `-Xcontext-sensitive-resolution` |
| Collection literals | Experimental | 2.4.0 | `-Xcollection-literals` |
| Explicit context arguments | Experimental | 2.4.0 | `-Xexplicit-context-arguments` |
| Name-based destructuring | Experimental in 2.3.20/2.4; **stable in `only-syntax` mode in 2.5.0-Beta1** (2026-09-23) | 2.3.20 | `(val mail = email, val name = username) = user` |
| Companion blocks/extensions | Experimental | 2.5.0-Beta1 | `-Xcompanion-blocks-and-extensions` |
| Rich errors (error union types, KEEP-0462) | Not in the 2.4 or 2.5.0-Beta1 release notes; no flag known (unverified) | - | do not write it |
| Contracts (`contract { }` in your own code) | Experimental API, needs `@OptIn(ExperimentalContracts::class)` (still so in the 2.4 API docs) | 1.3 | stdlib contracts are fine to rely on |

### Name-based destructuring timeline (JetBrains blog, 2026-05)
- 2.3.20: experimental, `-Xname-based-destructuring=only-syntax|name-mismatch|complete`.
- 2.5.0 (planned 2026-12): stable, migration hints on by default.
- 2.7.0 (planned end of 2027): `val (a, b) = x` becomes name-based. Positional form becomes `val [a, b] = pair`.
- Today `val (email, name) = user` is still positional: a name/order mismatch silently swaps values.

## Component stability (kotlinlang.org components-stability)
- Stable: Kotlin/JVM, /Native (1.9.0), /JS, stdlib, kotlinx.coroutines, kotlinx.serialization, KMP + its Gradle plugin (1.9.20), kapt, all-open/no-arg.
- Beta: Kotlin/Wasm (since 2.2.20), kotlin-reflect, Native C/ObjC interop. Roadmap (2026-08-20): promote Wasm to Stable; Swift export Alpha -> Beta.
- Alpha: kotlinx-datetime, kotlinx-io, Lombok plugin (2.3.20). Experimental: power-assert.

## KMP / Compose Multiplatform (supported-platforms page)
| Platform | KMP core | Compose MP |
|---|---|---|
| Android, iOS, Desktop JVM | Stable | Stable |
| Server JVM | Stable | n/a |
| Web Kotlin/JS | Stable | n/a |
| Web Kotlin/Wasm | Beta | Beta |
| watchOS, tvOS | Beta | - |

- Apple: iOS min 14 since 2.3.0, raised to iOS/tvOS 15, macOS 12, watchOS 8 in 2.4.0; Intel `macosX64`/`tvosX64`/`watchosX64` deprecated in 2.3.20 (`iosX64` tier 3).
- Swift export: Alpha (2.4 adds suspend -> async/await, Flow -> AsyncSequence). Most projects still ship an ObjC framework (SKIE is a third-party option, unverified current status).
- Android library modules in KMP: `com.android.kotlin.multiplatform.library` (required with AGP 9, see rc-android).

## Library versions (Maven Central metadata, 2026-10-09)
| Library | Stable | Pre-release |
|---|---|---|
| Kotlin Gradle plugin | 2.4.21 | 2.5.0-Beta1 |
| KSP | 2.3.12 | |
| kotlinx-coroutines | 1.11.0 (2026-05) | |
| kotlinx-serialization | 1.11.0 | 1.12.0-RC (2026-09-04) |
| kotlinx-datetime | 0.8.0 (alpha component) | |
| kotlinx-collections-immutable | 0.5.2 | |
| Ktor | 3.6.0 (2026-09-17) | |
| Compose Multiplatform | 1.12.1 | 1.13.0-alpha02 |
| SQLDelight | 2.4.1 | |
| Koin | 4.2.2 | |
| Coil 3 | 3.6.3 | |
| Exposed | 1.5.1 | |
| jOOQ | 3.21.9 | |
| Spring Boot | 4.1.1 | 4.2.0-M2 |
| Micronaut platform | 5.2.2 | |
| JUnit (Jupiter) | 6.1.3 | |
| Kotest | 6.2.5 | |
| MockK | 1.14.11 | |
| Turbine | 1.2.1 | |
| detekt | 1.23.8 (`io.gitlab.arturbosch.detekt`) | 2.0.0-alpha.6 (`dev.detekt`) |
| ktlint | 1.8.0 | |
| Kover | 0.9.11 | |
| kotlinx binary-compatibility-validator | 0.18.2 | KGP built-in `abiValidation` (experimental) |
