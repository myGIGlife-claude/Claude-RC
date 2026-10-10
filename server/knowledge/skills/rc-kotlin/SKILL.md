---
name: rc-kotlin
description: Kotlin language and non-Android Kotlin as of 2026-10: K2 and 2.x features, coroutines/Flow, kotlinx.serialization, Multiplatform (expect/actual, Compose MP), Ktor/Spring/Exposed servers, Gradle Kotlin DSL, convention plugins, libs.versions.toml, Java interop, Kotest/MockK/Turbine, detekt/ktlint. Use for *.kt, *.kts, build-logic, commonMain, Flow or suspend code. Android specifics are in rc-android.
---
# Kotlin: language, coroutines, KMP and server (as of 2026-10)

> Facts are dated (see Sources). Confirm on the primary source before pinning a version. (unverified) marks a lead, not a fact.
> Android (AGP 9, Compose UI, Room, Play policy) lives in **rc-android**. Spring itself lives in **rc-java**. This skill is the language, coroutines, KMP, server and build side.
> Feature-by-feature status table and all library versions: `references/language-and-libs.md`.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| Kotlin | **2.4.21** (2026-10-08). 2.4.20 tooling 2026-09-07. 2.4.0 language 2026-06-03 | **2.5.0 planned 2026-12** (2.5.0-Beta1 out 2026-09-23). 2.5.20 planned 2027-03 |
| Cadence | Language release 2.x.0 every 6 months, tooling 2.x.20 three months later, bug fixes 2.x.yz | From 2.4, the JVM stdlib of each line is supported 18 months (2.4 until 2027-12-03) |
| Compiler | K2 only. **K1 removed in 2.4** (`-language-version=1.9` rejected) | K2 stable since 2.0.0 (2024-05-21) |
| Stable in 2.4 | Context parameters, explicit backing fields, `@all:` target, common `Uuid`, `isSorted()`, Java 26 target | Collection literals, explicit context args: experimental |
| Stable in 2.2/2.3 | Guard conditions in `when`, non-local `break`/`continue`, `$$` interpolation (2.2); nested type aliases, `kotlin.time.Instant`/`Clock` (2.3) | |
| Coming | Name-based destructuring stable (syntax-only) in 2.5; `val (a, b)` turns name-based in 2.7 (end 2027). Rich errors: KEEP discussion only, no flag | Do not write rich-error syntax |
| Gradle | KGP 2.4.20 tested with Gradle 7.6.3 to 9.7.0. Minimum AGP 8.5.2 | Gradle 9.8.1 is current (rc-android) |
| Libraries | coroutines 1.11.0, serialization 1.11.0 (1.12.0-RC), Ktor 3.6.0, Compose MP 1.12.1, SQLDelight 2.4.1, Koin 4.2.2, Exposed 1.5.1, KSP 2.3.12 | Full table in references |
| Platforms | JVM, Native, JS, KMP: Stable. **Wasm: Beta** (roadmap: promote to Stable). Compose MP: Android/iOS/desktop Stable, web (Wasm) Beta | Swift export: Alpha |
| Servers | Ktor 3.6 (HTTP/3 experimental, OIDC plugin experimental). Spring Boot 4.1.1 (Kotlin 2.2+ baseline, Jackson 3, kotlinx.serialization starter). Micronaut 5.2.2 | |
| Test/lint | JUnit 6.1.3 (Java 17, Kotlin 2.2 baseline, `suspend` test methods), Kotest 6.2.5, MockK 1.14.11, Turbine 1.2.1, detekt 1.23.8 (2.0 alpha), ktlint 1.8.0 | |

### Older versions (what differs on a legacy project)
- **Kotlin 1.9 / K1 projects**: can't jump straight to 2.4 if they rely on K1-only compiler plugins or `-language-version=1.9`. Go to 2.0-2.3 first. No guard conditions, context parameters or `$$` strings there. Don't use them.
- **2.2/2.3**: context parameters need `-Xcontext-parameters`. Explicit backing fields need `-Xexplicit-backing-fields`. Context receivers are deprecated in 2.2 and removed in 2.3.20. `kotlinOptions {}` is an error from 2.2: use `compilerOptions {}`.
- **Pre-2.3 time code** uses `kotlinx.datetime.Instant`/`Clock`. Stay with it unless asked to migrate (kotlinx-datetime 0.7+ moved to `kotlin.time`).
- **Spring Boot 3.x** pairs with Kotlin 1.9/2.x and Jackson 2. Boot 4 requires Kotlin 2.2+. **JUnit 5** has no `suspend` tests: wrap in `runTest`.
- Don't upgrade Kotlin, Gradle or libraries unless the task asks. Match the project's `libs.versions.toml`.

## What changed / stop doing

| Old | New | Since |
|---|---|---|
| kapt for annotation processing | **KSP** (KSP2, versioned on its own, 2.3.x). kapt still works but is slow | KSP2 default 2024-25. Room 3 and Hilt want KSP |
| Context receivers `context(Logger)` | Context parameters `context(log: Logger)` | 2.2 preview, stable 2.4. Receivers removed 2.3.20 |
| Private `_state` + public `state` pair | Explicit backing field: `val state: StateFlow<S> field = MutableStateFlow(init)` | Stable 2.4 |
| `kotlinOptions { jvmTarget = "17" }` | `kotlin { jvmToolchain(21); compilerOptions { ... } }` | Error since 2.2 |
| Groovy `build.gradle` for new projects | `build.gradle.kts` + `gradle/libs.versions.toml` + convention plugins in `build-logic` | Kotlin DSL default since Gradle 8.2 |
| `buildSrc` for shared build logic | Included build `build-logic` (better caching, no global invalidation) | |
| `allprojects {}` / `subprojects {}` config | Convention plugins applied per module | Required for configuration cache isolation |
| `GlobalScope.launch`, `runBlocking` in production paths | A scope you own (`CoroutineScope(SupervisorJob() + dispatcher)`), cancelled on shutdown | Structured concurrency |
| `Thread`, `synchronized`, `ExecutorService` for app concurrency | Coroutines, `Mutex`, `Dispatchers.IO.limitedParallelism(n)` | `limitedParallelism` stable in coroutines 1.9 |
| RxJava for new code | `Flow`, `StateFlow`, `SharedFlow` | |
| `ConflatedBroadcastChannel`, `BroadcastChannel` | `StateFlow` / `SharedFlow` | Deprecated (removed) |
| `TestCoroutineDispatcher`, `runBlockingTest` | `runTest` + `StandardTestDispatcher` | coroutines-test 1.6 |
| `CoroutineDispatcher` as a context key | `ContinuationInterceptor` key | coroutines 1.11 |
| Gson / Moshi-reflection in Kotlin | kotlinx.serialization (compile-time, KMP) | Gson ignores defaults and nullability |
| `kotlinx.datetime.Instant` | `kotlin.time.Instant` | 2.3.0 |
| `java.util.UUID` in common code | `kotlin.uuid.Uuid` | Common API stable in 2.4 |
| `!!` and `lateinit` to silence the compiler | Constructor injection, `?:`, `requireNotNull(x) { "why" }`, `by lazy` | |
| standalone `binary-compatibility-validator` plugin for new libs | KGP built-in `kotlin { abiValidation() }` (experimental, `checkKotlinAbi`) | Task renamed in 2.3.20 |
| detekt 1.x group `io.gitlab.arturbosch.detekt` | still current. 2.0 moves to `dev.detekt` (alpha) | Don't use 2.0 alphas unless asked |
| `@Deprecated` Ktor 2 APIs (`receiveNullable`, `respondHtmlFragment`) | `receive<T?>()`, `respondHtmlPartial()` | Ktor 3.6 |
| `-Xjvm-default=all` | `jvmDefault` compiler option (default `enable`) | 2.2 |

## Do this

### Language idioms
- Prefer `val`, read-only `List`/`Map` in APIs, `data class` with `copy()`. Immutable in, immutable out.
- Model states and results with `sealed interface` + `data class`/`data object`. Make `when` exhaustive (no `else`), so new cases fail the build.
```kotlin
sealed interface LoadResult<out T> {
    data object Loading : LoadResult<Nothing>
    data class Ok<T>(val value: T) : LoadResult<T>
    data class Failed(val error: AppError) : LoadResult<Nothing>
}
fun render(r: LoadResult<User>) = when (r) {
    LoadResult.Loading -> "..."
    is LoadResult.Ok if r.value.isBanned -> "banned"   // guard condition, 2.2+
    is LoadResult.Ok -> r.value.name
    is LoadResult.Failed -> r.error.message
}
```
- Expected failures: return a sealed result or `Result<T>`. Throw only for bugs. Never catch `CancellationException` without rethrowing (see coroutines). `runCatching` catches it too, so don't wrap `suspend` calls in it.
- Wrap IDs and units in `@JvmInline value class` (no allocation when used as its own type, boxed when nullable, generic or used as an interface).
- Scope functions: `apply` to configure, `also` for side effects, `let` for nullable chains, `run`/`with` sparingly. Never nest more than one. A local `val` is often clearer.
- Extension functions for small helpers on types you don't own. Keep them in the file or package where they're used. Not for business logic that needs state.
- Context parameters (2.4+) for cross-cutting deps (logger, transaction, clock) passed through many layers. Not as a DI replacement for ordinary constructor deps.
- Type-safe DSLs only where the structure is real (HTML, routing, build config). A plain function or data class beats a DSL for 3 options.
- `require()` for argument checks, `check()` for state, `error()` for unreachable. All give clear messages.
- Names: `camelCase` functions, `PascalCase` types, `SCREAMING_CASE` only for `const val` and true constants. Official style = `kotlin.code.style=official` in `gradle.properties`.

### Coroutines and Flow
- `suspend` for one-shot work, `Flow` for streams. Main-safety is the callee's job: `withContext(io)` inside the repository.
- Every coroutine belongs to a scope with an owner and an end: request scope (Ktor handlers are already scoped), `coroutineScope {}` for parallel work, a service scope cancelled on shutdown.
```kotlin
class Syncer(private val io: CoroutineDispatcher = Dispatchers.IO) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + io + CoroutineName("syncer"))
    fun start() = scope.launch { while (isActive) { syncOnce(); delay(30.seconds) } }
    override fun close() = scope.cancel()
}
suspend fun loadBoth(a: Api) = coroutineScope {        // fails fast, cancels the sibling
    val u = async { a.user() }; val o = async { a.orders() }
    u.await() to o.await()
}
```
- Inject dispatchers (constructor param with a default) so tests can swap them.
- Cancellation is cooperative: blocking loops call `ensureActive()`/`yield()`. Blocking IO goes to `Dispatchers.IO` (or `limitedParallelism(n)` view for a pool you cap). Clean up in `finally`. Use `withContext(NonCancellable)` only for short suspending cleanup.
- `catch (e: Exception)` around suspend code must rethrow `CancellationException` (`catch (e: CancellationException) { throw e }` first).
- `withTimeout` throws, `withTimeoutOrNull` returns null. Put timeouts on every network call.
- Hot state: `StateFlow` (always has a value, conflated, `distinctUntilChanged`). Events: `SharedFlow` (`replay = 0`, choose `extraBufferCapacity` / `BufferOverflow`) or a `Channel` when exactly one consumer must get each event.
- `stateIn(scope, SharingStarted.WhileSubscribed(5.seconds), initial)` to share an upstream. `shareIn` for events. Don't create a new `stateIn` per call (each one leaks a collector in that scope).
- Operators: `flatMapLatest` for "latest query wins", `combine` for derived state, `flowOn` to change upstream context (never `withContext` inside `flow {}`: it throws), `conflate`/`buffer` for slow collectors. Use `callbackFlow` + `awaitClose { unregister() }` to wrap callbacks.
- Mutable shared state: `MutableStateFlow.update {}` (atomic), `Mutex.withLock {}`, or confinement to one coroutine. Not `synchronized` around suspend points.

### kotlinx.serialization
- Plugin `kotlin("plugin.serialization")` at the Kotlin version + runtime `kotlinx-serialization-json`.
- One shared `Json` instance (building it is costly): `Json { ignoreUnknownKeys = true; explicitNulls = false }` for external APIs. Keep `encodeDefaults` off unless the API needs it (or mark fields `@EncodeDefault`).
- Use `@SerialName` for wire names (don't rename a property and break the API). Sealed hierarchies are polymorphic by default with a `type` discriminator (`classDiscriminator` to change it).
- Add a default for every new field so old payloads still decode. Treat decoding failures (`SerializationException` / `JsonException` family) as 400s, not 500s.

### Kotlin Multiplatform
- Layout: `src/commonMain`, `commonTest`, `androidMain`, `iosMain`, `jvmMain`. The default hierarchy template creates the intermediate source sets (`iosMain`, `appleMain`, `nativeMain`). Don't hand-wire `dependsOn` unless it's needed.
- Prefer an interface in common + implementations injected per platform. Use `expect`/`actual` only for small leaf things (`expect fun platformName(): String`). `expect`/`actual` classes are still Beta (unverified for 2.4).
- Share data and domain first (Ktor client, kotlinx.serialization, SQLDelight or Room KMP, DataStore, Koin, lifecycle-viewmodel). Share UI with Compose Multiplatform where the team accepts a non-native look on iOS.
- Compose MP 1.12: Android/iOS/desktop Stable. Web via Wasm is Beta: fine for internal tools, plan for a fallback on public sites (Wasm GC needs current browsers).
- iOS: ship an XCFramework (or SwiftPM via KMMBridge-style tooling). Swift export is Alpha: don't make it the only path. Expose suspend/Flow to Swift through a thin wrapper or a tested bridge.
- Kotlin/Native: new memory model is the only model (no `freeze()`). CMS GC is the default in 2.4. Watch binary size and link times on CI (macOS runners only for Apple targets).

### Server side
- **Ktor 3** (`io.ktor:ktor-server-*` 3.6): `embeddedServer(Netty)` or `EngineMain` + `application.conf`/`yaml`. Plugins: `ContentNegotiation` (kotlinx.serialization), `StatusPages` (map exceptions to responses), `CallLogging` + call IDs, `Authentication` (JWT/OIDC), `RequestValidation`, `RateLimit`, `CORS` (explicit hosts). Built-in DI (3.2+) or Koin for wiring.
- Handlers are coroutines: never call blocking JDBC directly on the event loop. Use `withContext(Dispatchers.IO)` or a dedicated pool, or the DB library's suspend API (Exposed `suspendTransaction` (unverified name for 1.x), R2DBC).
- **Spring Boot 4 + Kotlin**: `kotlin("plugin.spring")` (all-open) and `kotlin("plugin.jpa")` (no-arg) are required. Constructor injection, `suspend` controller functions and `Flow` returns work in WebFlux and MVC. JSpecify nullability flows into Kotlin types. JPA entities: not `data class` (equality and lazy loading break), `var` fields, id-based `equals`.
- **Micronaut 5**: compile-time DI via KSP. Good when startup and memory matter. GraalVM native image works with any of the three, but test reflection config.
- **SQL**: Exposed 1.x (DSL for typed SQL, DAO optional) or jOOQ 3.21 (generated from the schema, best for SQL-heavy apps). Always bind parameters. Migrations with Flyway or Liquibase, never `SchemaUtils.create` in production.
- Config from env vars or files, validated at startup into a data class. Fail fast on missing secrets.

### Gradle (Kotlin DSL)
```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.4.21"
coroutines = "1.11.0"
[libraries]
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```
```kotlin
// build-logic/src/main/kotlin/kotlin-jvm-conventions.gradle.kts
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors = true; freeCompilerArgs.add("-Xjsr305=strict") }
}
tasks.test { useJUnitPlatform() }
```
- `settings.gradle.kts`: `pluginManagement { includeBuild("build-logic") }`, `dependencyResolutionManagement` with `repositoriesMode = FAIL_ON_PROJECT_REPOS`.
- `gradle.properties`: `org.gradle.configuration-cache=true`, `org.gradle.caching=true`, `org.gradle.parallel=true`, `kotlin.code.style=official`.
- Commit the wrapper and `gradle/wrapper/gradle-wrapper.properties` with `distributionSha256Sum`. Enable dependency verification (`gradle/verification-metadata.xml`) for libraries and anything security-sensitive.
- Use the version catalog only (no hard-coded versions in modules). One BOM per family (`platform(libs.ktor.bom)`).
- **When NOT to add a dependency**: stdlib covers `Uuid`, `Instant`/`Clock`, `Duration`, `Result`, `buildList`, `chunked`/`windowed`, `Regex`, Base64 (`kotlin.io.encoding`), `HexFormat`. Don't add Arrow for `Either` in a small app (a sealed result is 6 lines), Guava for collections, Apache Commons for strings, or a DI framework for a 10-class CLI.

### Java interop
- Java types without nullability annotations are platform types (`String!`). Annotate at the boundary: give them an explicit Kotlin type (`val name: String? = javaObj.name`).
- Prefer JSpecify (`@NullMarked`) on Java code you own. Kotlin reads JSpecify/JSR-305 (`-Xjsr305=strict`) as real nullability.
- For Java callers: `@JvmStatic` on companion functions, `@JvmOverloads` for default args, `@JvmName` for clashes and file facades, `@JvmField` for constants, `@Throws` for checked exceptions Java must catch. Don't expose `suspend` or value classes to Java APIs (mangled names). Offer a `CompletableFuture` wrapper (`future {}` from `kotlinx-coroutines-jdk8`, now in core) if Java must call it.
- Collections from Kotlin are read-only views, not immutable. Java can mutate them. Copy at trust boundaries.

## Security
- **Injection**: Exposed/jOOQ/JDBC with bound parameters only. Never build SQL, shell commands (`ProcessBuilder` with a list, no `sh -c`), LDAP or paths from strings with user input. Normalize and check paths stay under a base dir.
- **Deserialization**: kotlinx.serialization has no gadget chain, but polymorphic `Any`/open polymorphism with class names from input is a risk. Register subclasses explicitly. Never Java `ObjectInputStream` on untrusted data. Set size limits on request bodies (Ktor: limit in the engine/`receive`).
- **Error leakage**: kotlinx.serialization error messages can echo user input. Hide them from clients (`StatusPages` generic message). 1.11 added `exceptionsWithDebugInfo` to turn that off, and it will default to enabled later.
- **Secrets**: from env/secret store, never in `application.conf`, `gradle.properties` in VCS or logs. `data class` `toString()` prints every field: override it or wrap secrets (`value class Secret(val v: String) { override fun toString() = "***" }`).
- **Auth (Ktor)**: verify JWT issuer, audience, algorithm and expiry. `CORS` with explicit hosts (`anyHost()` only for public read-only APIs). CSRF protection for cookie sessions. Session cookies `secure`, `httpOnly`, `SameSite`.
- **Crypto**: `java.security.SecureRandom`/`Uuid.random()` for tokens, never `kotlin.random.Random`. Constant-time compare (`MessageDigest.isEqual`). Password hashing with Argon2/bcrypt libraries, not SHA.
- **Coroutines**: one unbounded `launch` per request becomes a DoS. Bound concurrency (`Semaphore`, `limitedParallelism`, channel capacity) and set timeouts.
- **Supply chain**: Gradle dependency verification, wrapper checksum, pinned plugin versions, Dependabot/Renovate on the catalog. Review new transitive deps. No build scripts that `curl | sh`.
- **Privacy**: log IDs, not payloads. No analytics SDKs in libraries. Collect only fields the feature needs.

## Performance & quality
- Measure before tuning: JMH (`kotlinx-benchmark` for KMP) for hot code, async-profiler/JFR on the JVM, Ktor/Micrometer metrics (p50/p95/p99 latency, error rate) on servers.
- Boxing: `List<Int>` boxes. Use `IntArray` for hot numeric code. Nullable or generic use of value classes boxes them.
- Lambdas: `inline` higher-order functions in hot paths (stdlib `map`/`filter` already are). Capturing lambdas passed to non-inline functions allocate.
- `Sequence` (lazy) pays per-element overhead: use it for long chains on large or infinite data, or early exit (`first`). Plain list ops are faster for small collections.
- Avoid `String` concatenation in loops (`buildString`). Regex: compile once into a `val`.
- Reflection (`kotlin-reflect`) is slow and large: avoid it at runtime. kotlinx.serialization is compile-time.
- Coroutines: don't make tiny CPU functions `suspend`. Don't switch dispatchers per element. A `Flow` per element is slower than a list.
- Build speed: configuration cache + build cache on, Gradle and Kotlin daemons warm, `build-logic` instead of `buildSrc`, KSP instead of kapt, fewer modules that apply `kotlin-multiplatform` targets you don't ship. Kotlin/Native: build only the target you test locally (`linkDebugFrameworkIosSimulatorArm64`).
- Targets: CI build of a mid-size JVM service under ~3 min with a warm cache (rule of thumb, not a standard). Startup and memory matter on serverless: consider GraalVM native image.

## Testing & tooling
- Runner: JUnit 6 (Jupiter, `suspend` test methods) or Kotest 6 (specs, property tests, assertions). `kotlin.test` for KMP `commonTest`.
- Coroutines: `runTest` (virtual time, 60 s default timeout). Pass the test's `StandardTestDispatcher(testScheduler)` into code under test. `backgroundScope` for never-ending collectors. Delays on `Dispatchers.Default`/`IO` are not skipped.
- Flow: Turbine `flow.test { assertEquals(x, awaitItem()); cancelAndIgnoreRemainingEvents() }`.
- Mocks: fakes first (a small in-memory class). MockK when needed (`coEvery`/`coVerify` for suspend). Don't mock data classes or types you own.
- Server: Ktor `testApplication {}` (in-process, no ports). Spring `@SpringBootTest` sparingly, slices otherwise. Testcontainers for real Postgres instead of H2.
- Coverage: Kover 0.9.x. Lint: detekt 1.23.8 (+ `detekt-formatting` or ktlint 1.8.0), Android lint for Android modules. Libraries: ABI validation (`abiValidation()` in KGP or the BCV plugin), `explicitApi()` mode.
- CI: `./gradlew check` (tests + detekt + ABI check), `--configuration-cache`, Gradle cache action, macOS runner only for Apple targets.

## Common mistakes in AI-written code
- Writing `context(Logger)` (removed receivers) or using 2.4 features in a 1.9/2.1 project. Check `kotlin` in `libs.versions.toml` first.
- Inventing syntax: rich errors `fun f(): User | NotFound`, `val [a, b]` without the flag, collection literals `[1, 2]` without `-Xcollection-literals`. Name-based destructuring `(val x = y) = obj` needs the flag before 2.5.
- Assuming `val (name, email) = user` matches by name. It's positional until 2.7. Mismatched order silently swaps values.
- `GlobalScope`, `runBlocking` inside a request handler or a coroutine, `CoroutineScope(Dispatchers.IO)` created per call and never cancelled.
- Swallowing `CancellationException` in `catch (e: Exception)` or `runCatching`, so cancelled work keeps running.
- `withContext` inside `flow {}` (throws "Flow invariant is violated"). Use `flowOn`.
- `MutableStateFlow.value = state.value.copy(...)` from several coroutines (lost updates). Use `update {}`.
- Using `SharedFlow(replay = 1)` for one-time events (they replay to new collectors) or `StateFlow` for events (equal events get dropped).
- `Dispatchers.setMain` without `resetMain`, `TestCoroutineDispatcher`, `runBlockingTest`: old test APIs.
- kapt in new builds, `kotlinOptions {}`, `buildSrc` for everything, hard-coded versions next to a catalog, Groovy for new scripts.
- `data class` JPA entities. Missing `plugin.spring`, then wondering why `@Transactional` doesn't work (final classes).
- `!!` after Java calls instead of handling the platform type. `lateinit var` for constructor-injectable deps.
- Gson with Kotlin data classes. `Json {}` created per request. `encodeDefaults = true` leaking internal fields.
- Hallucinated Ktor APIs: `call.receiveOrNull()` (use `receive<T?>()` on 3.6), Ktor 1/2 `install(Routing)` style or `featureOrNull`. Check the Ktor version.
- Calling blocking JDBC on Ktor's event loop.
- `expect class` for everything in KMP instead of a common interface.
- Overusing scope functions (`x?.let { it.run { also { ... } } }`) and one-off DSLs.

## Before you ship
- [ ] Kotlin, Gradle and library versions match the catalog. No new dependency the stdlib covers. No experimental `-X` flags unless agreed.
- [ ] `./gradlew check` green: tests, detekt/ktlint, ABI check (libraries), `allWarningsAsErrors` if the project uses it.
- [ ] No `GlobalScope`, no unowned scopes, cancellation rethrown, timeouts on IO, bounded concurrency.
- [ ] Errors: expected failures are typed. Server maps them to 4xx/5xx without leaking stack traces or input.
- [ ] Security: parameterized SQL, validated input, size limits, auth checks per route, secrets from env, `toString()` doesn't print secrets.
- [ ] Serialization: new fields have defaults, wire names fixed with `@SerialName`, unknown keys policy decided.
- [ ] Public APIs (libraries): `explicitApi()`, KDoc, ABI dump updated on purpose, Java-callable if Java users exist.
- [ ] KMP: every shipped target builds on CI (macOS for Apple). Tests run in `commonTest` plus each platform.
- [ ] Deploy: health/readiness endpoints, graceful shutdown cancels scopes, config validated at startup, rollback = previous image/artifact.

## Sources
All accessed 2026-10-09.
- https://kotlinlang.org/docs/releases.html: 2.4.21/2.4.20/2.4.0 dates, cadence, 2.5.0 (2026-12) and 2.5.20 plans, 18-month stdlib support
- https://kotlinlang.org/docs/whatsnew24.html: 2.4 stable/experimental features, K1 removal, Gradle/AGP minimums, CMS GC, Swift export alpha (page shows a 2026-07-14 date, releases page says 2026-06-03 for 2.4.0)
- https://kotlinlang.org/docs/whatsnew2420.html: 2.4.20 features, Gradle 7.6.3 to 9.7.0
- https://kotlinlang.org/docs/whatsnew2320.html: name-based destructuring experimental, context receivers removed, `checkKotlinAbi`, Intel Apple targets deprecated, Lombok Alpha
- https://kotlinlang.org/docs/whatsnew23.html: nested type aliases, `kotlin.time.Instant`, explicit backing fields experimental, iOS 14 minimum
- https://kotlinlang.org/docs/whatsnew22.html: guard conditions, non-local break/continue, `$$` stable; context parameters preview; `kotlinOptions` error; jvmDefault
- https://kotlinlang.org/docs/whatsnew-eap.html: 2.5.0-Beta1 (name-based destructuring stable in only-syntax mode, companion blocks)
- https://github.com/JetBrains/kotlin/releases: 2.5.0-Beta1 on 2026-09-23, 2.4.21 on 2026-10-08
- https://blog.jetbrains.com/kotlin/2026/05/the-road-to-name-based-destructuring/: destructuring timeline to 2.7
- https://kotlinlang.org/docs/destructuring-declarations.html: current syntax and flags
- https://kotlinlang.org/docs/roadmap.html (2026-08-20): Wasm to Stable, Swift export to Beta, kapt performance work; rich errors not listed (KEEP-0462 discussion per search results)
- https://kotlinlang.org/docs/components-stability.html: component stability levels
- https://kotlinlang.org/docs/multiplatform/supported-platforms.html: KMP and Compose MP stability per platform
- https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html: `abiValidation`, experimental
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/kotlinx.coroutines.test/run-test.html: runTest timeout, backgroundScope, virtual time
- https://github.com/Kotlin/kotlinx.coroutines/releases: 1.11.0 changes (ContinuationInterceptor key, new APIs)
- https://github.com/Kotlin/kotlinx.serialization/releases: 1.11.0 `exceptionsWithDebugInfo`, 1.12.0-RC stabilizations
- https://ktor.io/docs/whats-new-360.html: Ktor 3.6.0 (2026-09-17), HTTP/3, OIDC, `receive<T?>`, `respondHtmlPartial`
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes: Kotlin 2.2.20, Jackson 3, kotlinx.serialization starter
- https://docs.junit.org/6.0.0/release-notes/: Java 17 and Kotlin 2.2 baseline, suspend test methods, JSpecify
- repo1.maven.org maven-metadata.xml (2026-10-09): versions of KGP, KSP, coroutines, serialization, Ktor, Compose MP, SQLDelight, Koin, Coil, Exposed, jOOQ, Spring Boot, Micronaut, JUnit, Kotest, MockK, Turbine, detekt, ktlint, Kover, BCV, kotlinx-datetime, collections-immutable
