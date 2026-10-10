---
name: rc-scala
description: Current Scala practice as of 2026-10 - Scala 3.9 LTS / 3.3 LTS / 2.13, migration from Scala 2, sbt 1.x/2.x, Mill, scala-cli, Cats Effect, ZIO, Ox, http4s, Tapir, Play, Pekko vs Akka licensing, Spark/Flink, Scala.js/Native, Metals, scalafmt, Scalafix, MUnit/ScalaCheck. Use when editing *.scala, *.sc, build.sbt, project/*.sbt, build.mill, .scalafmt.conf, or choosing or upgrading a Scala stack.
---
# Scala (as of 2026-10)

> Facts are dated (see Sources). Versions move: check the primary source before pinning one. Anything marked (unverified) is a lead, not a fact.

Your training data predates most of this. If a version here disagrees with your memory, trust this file and check the sources.
Library versions and per-library notes: `references/ecosystem.md`.

## Currency check

| Thing | Current (2026-10) | Notes |
|---|---|---|
| Scala 3 LTS | **3.9.0** (2026-09-03) | New LTS line, maintained at least 3 years. Needs **JDK 17+** |
| Scala 3 previous LTS | 3.3.8 (2026-06-10) | Maintained one more year after 3.9.0 (to ~2027-09); runs on JDK 8+ |
| Scala 3 Next | 3.10.0-RC1 | Next line: may break source (stdlib changes); libraries should publish on LTS |
| Scala 2.13 | 2.13.18 (2025-11) | 2.13.19 planned. VirtusLab took over Scala 2 maintenance (2026-10-07) "as long as necessary" |
| Scala 2.12 | 2.12.21 (2025-12) | Only for sbt 1 plugins, Flink Scala APIs and legacy Spark 3 |
| sbt | **2.0.10** (2026-10-02); 1.13.0 (2026-08-21) | sbt 2.0.0 GA 2026-06-14 (JDK 17+, Scala 3 build DSL); 2.1.0-M3 milestone |
| Mill | 1.1.x (1.1.10) | 1.x is the stable line; 1.3.0-M1 pre-release |
| scala-cli | Is the `scala` command since Scala 3.5; 1.14.0 bundled with 3.8.4 | Ammonite support dropped |
| Scala.js | **1.22.0** (2026-06-20) | WebAssembly backend now stable; sbt 2 support; JDK < 17 deprecated (1.21) |
| Scala Native | 0.5.12 (2026-05-22) | Supports Scala up to 3.8.3 / 2.13.18; 3.9 support (unverified) |
| JDK support | Scala 3.8+: JDK 17+. 2.13.18 / 2.12.21: JDK 8-26 | JDK 25 needs 2.13.17+ / 2.12.21+ / 3.8.0+ (on 3.x the 17 floor applies); JDK 27 support (unverified) |

Shipped in the last ~12 months (dated):
- **Scala 3.8** (2026-01-22): JDK 17 minimum; stdlib now compiled by Scala 3 (context bounds desugar to `given`); REPL split into `scala3-repl`; stabilized **better fors** (SIP-62) and `runtimeChecked` (SIP-57). 3.8.0/3.8.1 had runtime regressions: use the latest 3.8 patch.
- **Scala 3.8.4** (2026-06-05): security-audit fixes (hardened TASTy reading, Scaladoc stored XSS), `-<flag>:help`.
- **sbt 2.0** (2026-06-14): client/server default (sbtn), Bazel-compatible local/remote cache, incremental `test` (`testFull` runs all), built-in project matrix, `%%` works for JS/Native too, plugins publish as `_sbt2_3`.
- **sbt RCE GHSA-m2pw-22cj-jq4v** (2026-08-10): builds with `serverConnectionType := ConnectionType.Tcp`. Fixed in sbt 1.12.15+ / 2.0.6+.
- **Scala 3.9 LTS** (2026-09-03): `into` (SIP-71, explicit implicit conversions) stable; inaccessible companion implicits warn under `-deprecation` and stop resolving in 3.10. Named tuples (stable since 3.7) and better fors are in the LTS.
- **Scala 2.13 `-Ytasty-reader`** reads Scala 3 artifacts only up to 3.7: a 2.13 project cannot depend on libraries built with 3.8+.

### Older versions (what differs on lines you will meet)
- **3.3 LTS projects**: no named tuples, no better fors, no `into`, no `runtimeChecked`; `-Wunused` exists but is less complete. Runs on JDK 8/11. Don't use 3.4+ syntax there; keep libraries on 3.3 until the project moves to 3.9 (that move also moves the JDK floor to 17).
- **Libraries**: publishing on 3.9 forces all users to 3.9+ and JDK 17+. Libraries that must serve old users stay on 3.3.x for now (unverified ecosystem convention; check what the library's deps already require).
- **2.13 projects**: use `-Xsource:3` (plus `-Xsource-features:` as needed) to get Scala 3 semantics early; can consume Scala 3 libs only up to 3.7 via `-Ytasty-reader`. `scala-reflect` runtime reflection may fail against 3.8+ artifacts.
- **sbt 1.x builds**: still fully supported (1.13.0) and build Scala 2 and 3. The `sbt` launcher picks the right client from `project/build.properties`. Do not migrate to sbt 2 unless asked: plugins must have `_sbt2_3` builds (Play was at 3.1.0-M9 for sbt 2 at launch).
- **Spark** is 2.13-only from 4.0; Spark 3.5 offers 2.12 and 2.13. No Scala 3 Spark build.

**Pick a version:** new apps: Scala **3.9.x**, JDK 21 or 25, sbt 2.0.x (or 1.13 if a needed plugin lacks sbt 2) or scala-cli for small tools. Spark jobs: Scala **2.13** (the Spark build's version). Never start new code on 2.12.

## What changed / stop doing

| Old (stop) | New (do) | Since |
|---|---|---|
| `implicit val`/`implicit def` for instances and params | `given` / `using`; `summon[T]` instead of `implicitly` | Scala 3.0 |
| `implicit class` for extensions | `extension (x: T) def ...` | 3.0 |
| `implicit def` conversions | Avoid; if needed `Conversion[A, B]` given + `into` on the parameter type | `into` stable 3.9 |
| `sealed trait` + `case object` enum boilerplate | `enum Color { case Red, Green }`; ADTs with `enum` + params | 3.0 |
| Wrapper case classes / tagged types for IDs | `opaque type UserId = Long` (zero cost) | 3.0 |
| `Either[A, B]` with hand-written coproducts | Union types `A \| B` for error sets; intersection `A & B` | 3.0 |
| Forwarder methods | `export member.*` / `export a.{f, g}` | 3.0 |
| Scala 2 `def macro` / whitebox macros | `inline` + `scala.quoted` macros, `Mirror` derivation (`derives`) | 3.0 (2.x macros don't run on 3) |
| Shapeless 2 generic derivation | `deriving.Mirror` + `inline`/`summonInline`, or Magnolia for Scala 3 | 3.0 |
| Procedure syntax `def f() { ... }` | `def f(): Unit = ...` | dropped in 3.0 |
| `_` wildcard type args `List[_]` | `List[?]` | 3.x (`_` deprecated path) |
| `import x._` | `import x.*`; renames `import x.{a as b}` | 3.0 |
| Braces everywhere in new Scala 3 code | Pick one style per repo and enforce it with scalafmt (`rewrite.scala3.removeOptionalBraces`); significant indentation is the default syntax | 3.0 |
| `x: @unchecked` match | `x.runtimeChecked match` | 3.8 |
| `.map` trailing in for-comprehension desugar (perf/semantic surprises) | Better fors (SIP-62) on 3.8+ | 3.8 |
| Tuples `(String, Int)` returned with `_1/_2` | Named tuples `(name: String, age: Int)` | 3.7 |
| Ammonite / `scala` v2 runner scripts | `scala-cli` (`scala run`, `//> using dep ...`) | 3.5 |
| Akka (any version after 2.6.x) added without a license review | Apache **Pekko** (Apache 2.0); Akka is BSL 1.1: production needs a commercial license | Akka 2.7 (2022) |
| Play on Akka (2.8 and older) | Play 3.0 (Pekko) or 2.9 (Akka); Play 3.0.12 / 2.9.12 current | Play 3.0 |
| `Future`-only designs for cancellation, timeouts, resource safety | Effect system (Cats Effect 3 / ZIO 2) or direct-style Ox on virtual threads | - |
| `scala.collection.JavaConverters` | `scala.jdk.CollectionConverters` (`.asScala`/`.asJava`) | 2.13 |
| `mapValues`/`filterKeys` returning lazy views | `.view.mapValues(...).toMap` | 2.13 |
| `%%%` for JS/Native deps | `%%` in sbt 2 (all platforms) | sbt 2.0 |
| `ThisBuild /` to share settings to subprojects | Plain settings in sbt 2 build.sbt apply to all subprojects | sbt 2.0 |
| Fat jars by default with sbt-assembly | `sbt-native-packager` (Universal/Docker) or a jib-style layered image; assembly only for Spark/Flink submission or single-jar CLI tools, with an explicit `assemblyMergeStrategy` | - |
| `-Xlint` flags from old blog posts on Scala 3 | `-Wunused:all -Wvalue-discard -Wnonunit-statement -deprecation -feature -Werror` | 3.3+ |

## Do this

### Language idioms (Scala 3)
```scala
//> using scala 3.9.0          // scala-cli header; harmless in sbt projects
opaque type UserId = Long
object UserId:
  def apply(v: Long): UserId = v
  extension (id: UserId) def value: Long = id

enum PayError:
  case NotFound(id: UserId)
  case Declined(reason: String)

trait Show[A]:
  def show(a: A): String
given Show[UserId] = id => s"user-${id.value}"
def render[A: Show](a: A): String = summon[Show[A]].show(a)

case class Account(id: UserId, name: String) derives CanEqual
```
- Model domains with `enum`/`case class` + opaque types; keep `given`s in companions so they resolve without imports.
- Name givens that users import (`given intOrd: Ord[Int]`); import them with `import x.given` (plain `*` does not bring givens).
- `-language:strictEquality` + `derives CanEqual` for domain types catches `==` between unrelated types.
- `inline`/macros: only in libraries, and only when a plain function or `Mirror` derivation fails. Macros tie you to TASTy/compiler versions.
- `-Yexplicit-nulls` is still not default; Java calls return `T | Null` only with it. Wrap Java results in `Option(...)` at the boundary.
- Prefer `Either`/union error types inside domain code; throw only for bugs. In effect code use the effect's error channel.

### Functional vs direct style: pick rule
- **Existing Cats Effect / ZIO codebase** -> stay in it; don't mix runtimes in one service.
- **New service, team knows FP** -> Cats Effect 3 (+ fs2, http4s, skunk/doobie) or ZIO 2 (+ zio-http, zio-json). Strong resource safety, cancellation, structured concurrency (`parTraverse`, `Supervisor`, `ZIO.foreachPar`, `Scope`).
- **New service, team mostly Java/Kotlin background** -> direct style: Ox (1.0.x, JDK 21+ virtual threads: `supervised`, `par`, `fork`, channels) with Tapir + Netty/JDK server, or plain blocking code on virtual threads. Less ceremony, easier hiring.
- **Caprese / capture checking** (`import language.experimental.captureChecking`) is still experimental and unstable: do not build production code on it.
- `scala.concurrent.Future` is fine for simple one-shot async glue; it is eager, not cancellable, and needs an `ExecutionContext`. Never use `ExecutionContext.global` for blocking I/O; never `Await.result` inside request handlers.

### Builds
```scala
// project/build.properties: sbt.version=2.0.10   (or 1.13.0)
// build.sbt (sbt 2 applies these to every subproject)
scalaVersion := "3.9.0"
scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Wvalue-discard", "-Werror")
lazy val core = project
lazy val app  = project.dependsOn(core)
  .settings(libraryDependencies += "org.typelevel" %% "cats-effect" % "3.7.1")
```
- Cross-building 2.13 + 3: `crossScalaVersions := Seq("2.13.18", "3.3.8")` (3.3 keeps 2.13 users able to read your TASTy; `-Ytasty-reader` stops at 3.7). Use `scala-2`/`scala-3` source dirs only for the few files that differ.
- Pin dependency updates with Scala Steward or Dependabot (Dependabot supports sbt); commit a lock with `sbt-dependency-lock` only if reproducibility is required.
- **Mill** (`build.mill`, Scala-based config, fast) is a good choice for new multi-module builds if the team agrees; **scala-cli** for scripts, single-module tools, and teaching. **Bazel** (rules_scala) only for big polyglot monorepos.
- Never set `Global / serverConnectionType := ConnectionType.Tcp` (RCE advisory).

### Data: Spark and Flink
- Spark 4.x: Scala **2.13** only, JDK 17/21/25 (25 needs 25.0.3+ as of Spark 4.2). Mark Spark deps `% Provided`; build an assembly jar with shading for conflicting libs (Jackson, Guava, protobuf).
- Prefer the Dataset/DataFrame API over RDDs; avoid UDFs when a built-in function exists (UDFs block Catalyst optimization). Don't `collect()` big data to the driver.
- Closures capture enclosing classes: put functions in `object`s to avoid `Task not serializable`.
- Flink 2.x: Table API Scala modules are `_2.12` only; for DataStream jobs use the Java API from Scala (Scala DataStream API removed in 2.0 (unverified)).

### JVM interop
- Use `scala.jdk.CollectionConverters`, `scala.jdk.FutureConverters`, `scala.jdk.OptionConverters`.
- For Java callers: `@throws`, avoid default args/implicits/`given` in public Java-facing APIs, expose `java.util` types, `@varargs` for varargs.
- The 3.8 JDK-17 floor exists so Scala can drop `sun.misc.Unsafe` uses ahead of JDK 26+ restrictions. Older Scala/tools on JDK 24+ may print Unsafe warnings: upgrade Scala first; don't add `--sun-misc-unsafe-memory-access=allow` permanently.

### When NOT to add a dependency
- JSON for 3 case classes: one library already in the stack (circe/zio-json/jsoniter-scala/upickle), never two.
- Retries, timeouts, parallelism: the effect system or Ox already has them; no extra resilience lib.
- Cats only for `Semigroup`/`traverse` in a non-FP codebase: write the 5 lines.
- Don't add Akka; don't add Shapeless/Magnolia when `derives` covers it; don't add an HTTP framework to a CLI that calls one endpoint (`java.net.http.HttpClient` or sttp).

## Security
- **Dependencies**: sbt-dependency-check / OWASP or GitHub dependency graph (`sbt-dependency-submission`); http4s 0.23.35+/1.0.0-M47+ were security-hardening releases; Play 3.0.12 / 2.9.12 bumped Netty for CVEs. Keep sbt at 1.12.15+/2.0.6+.
- **Deserialization**: no Java serialization of untrusted input (Pekko/Akka remoting: use Jackson/protobuf serializers, keep `allow-java-serialization = off`). Don't let JSON decoders build arbitrary class names.
- **JSON limits**: cap request body size at the server (http4s `EntityLimiter`, Play `maxMemoryBuffer`); jsoniter-scala/circe parse depth and big-number limits for untrusted input.
- **SQL**: doobie `sql"..."` / skunk / Quill / Slick interpolators are parameterized; `Fragment.const` and `#$` splice raw strings - never with user input.
- **Templates/XSS**: Twirl escapes by default; `Html(...)`/`@Html` bypasses it. Scalatags escapes text nodes.
- **Secrets**: from env/secret store via config (pureconfig/ciris/zio-config); never log config objects with `toString` of case classes that hold secrets (wrap in a type with a redacted `toString`).
- **Build**: sbt and Mill run arbitrary code from `project/` and plugins: treat a PR touching them like a code change. No TCP sbt server.
- **Licensing is a security/compliance item**: Akka BSL (3-year change date to Apache 2.0); check before adding any `com.typesafe.akka` / `io.akka` artifact.

## Performance & quality
- JIT warmup: benchmark with **sbt-jmh** (`Jmh/run`), never with `System.nanoTime` loops. For CLI startup consider GraalVM native-image (scala-cli `--power package --native-image`) or Scala Native.
- Allocation: boxing in generic code (`List[Int]`), tuples, closures in hot loops, `Option` per element. Use `Array`/`ArrayBuffer`, `while` loops or `inline` in proven hotspots only; measure with async-profiler / JFR (`-XX:StartFlightRecording`).
- Collections: `List` is O(n) for `apply`/`:+`/`length`; use `Vector` or `ArrayBuffer` for indexed access and appends; `.view` to fuse chains on large data; `Map` lookups over `find` on lists.
- Effects: Cats Effect 3.6+/ZIO 2.1 runtimes use work-stealing pools; block only inside `IO.blocking` / `ZIO.attemptBlocking`; enable CE fiber dumps / ZIO runtime metrics.
- Compile time: deep implicit/given search, big macro derivations and huge `match` types slow builds. Use `-Vprofile` style flags (`-Yprofile-*` on 3, unverified names) and split modules.
- Targets: p99 latency and allocation rate per request from load tests; compile times tracked in CI.

## Testing & tooling
- IDE: **Metals** (VS Code, Neovim, others) or IntelliJ IDEA + Scala plugin. Metals works with sbt via BSP, Mill and scala-cli.
- Format: **scalafmt** (`.scalafmt.conf` with `version = ...` and `runner.dialect = scala3`); CI runs `scalafmtCheckAll`.
- Lint/refactor: **Scalafix** (`OrganizeImports` is built in since 0.11; `RemoveUnused` needs `-Wunused:all`); WartRemover for extra bans; scapegoat (unverified Scala 3 status).
- Compiler flags are the first linter: `-deprecation -feature -Wunused:all -Wvalue-discard -Wnonunit-statement -Werror` (3.x); on 2.13 `-Xlint -Wunused -Xsource:3 -Werror`.
- Tests: **MUnit** (light, default in many templates), ScalaTest 3.2 (many styles; pick one, `AnyFunSuite`/`AnyFlatSpec`), **weaver-test** for Cats Effect, **zio-test** for ZIO, `munit-cats-effect` for CE + MUnit.
- Property-based: **ScalaCheck** (`munit-scalacheck`, `scalatestplus-scalacheck`) or zio-test `check`. Use for parsers, codecs (round-trip), and pure domain rules.
- Integration: testcontainers-scala; http4s `HttpApp` tests in-memory without a socket.
- Migration: Scala 3 `-source:3.0-migration -rewrite` (then `-source:3.x-migration` per step); 2.13 side `-Xsource:3`; Scalafix rules; scala3-migrate sbt plugin (maintenance status unverified).
- CI: `sbt -Dsbt.ci=true +test scalafmtCheckAll "scalafixAll --check"`; cache `~/.cache/coursier`, `~/.ivy2`, `~/.sbt` (sbt 2 also has its own cache dir).

## Common mistakes in AI-written code
- Writing Scala 2 `implicit` style in new Scala 3 code; using `implicitly` / `implicit class`.
- Mixing `import x._` with Scala 3 `*` imports; forgetting `import x.given` so givens are not found.
- Using Scala 3.8/3.9 features (named tuples, better fors, `runtimeChecked`, `into`) in a 3.3 LTS project, or the reverse: telling a 3.9 project it can still run on JDK 11.
- Suggesting a Scala 3 build of Spark (there is none) or `_2.12` Spark 4 artifacts.
- Adding `com.typesafe.akka` deps as if they were Apache-licensed; Akka HTTP code for a new service instead of Pekko HTTP / http4s / Tapir.
- `%%%` in sbt 2 builds; `ThisBuild /` everywhere in sbt 2; sbt 1 plugin coordinates in an sbt 2 build.
- `Future { blockingCall() }` on `global`; `Await.result` in handlers; `Future` as a lazy value (it is eager).
- Mixing Cats Effect and ZIO (via interop) in a new service; calling `unsafeRunSync()` inside effect code.
- Shapeless 2 / Scala 2 macro libraries in Scala 3 projects (`scala-reflect` macros don't exist on 3).
- `Enumeration` instead of `enum`; procedure syntax; `return` inside lambdas (non-local return is deprecated in 3; use `boundary`/`break`).
- `mapValues` without `.view` on 2.13+; `JavaConverters` imports.
- Inventing library versions: check Maven Central (`cs complete-dep org.typelevel:cats-effect_3:`) before pinning.
- `Await`/`Thread.sleep` in tests; tests depending on the order of parallel suites.

## Before you ship
- [ ] Scala version is the latest patch of its line (3.9.x, 3.3.x or 2.13.18+); JDK matches (17+ for 3.8+).
- [ ] Compiles warning-free with `-Werror` and the unused/value-discard flags; scalafmt and scalafix checks pass in CI.
- [ ] No Akka without a license decision on record; sbt 1.12.15+/2.0.6+; no TCP sbt server.
- [ ] Untrusted input: body size limits, parameterized SQL, no Java deserialization, escaped templates.
- [ ] Blocking calls wrapped (`IO.blocking`, virtual threads, dedicated pool); timeouts and cancellation on every outbound call.
- [ ] Tests: unit + ScalaCheck for codecs/parsers, integration via testcontainers; no flaky sleeps.
- [ ] Packaging: native-packager / container image, non-root, `-XX:MaxRAMPercentage`, health checks; fat jar only where the runtime requires it, with a reviewed merge strategy.
- [ ] Rollback: previous image/tag kept; schema migrations backward compatible for one release.
- [ ] Load-tested p99 and allocation numbers recorded for hot paths.

## Choosing Scala vs Kotlin/Java (2026)
- Choose **Scala** when: existing Scala codebase or team, Spark-heavy data work, effect-system concurrency is wanted, strong typing of domain/DSLs pays off.
- Choose **Kotlin** for Android, Spring-centric backends with mixed-skill teams, or when hiring breadth matters. Choose **Java 25** for the widest hiring pool and longest-lived enterprise code.
- Reality: smaller hiring pool than Java/Kotlin; libraries are maintained by small teams (check last release and maintainers before adopting); Scala 2 -> 3 and sbt 1 -> 2 migrations cost real time. Budget for upgrades each year.

## Sources
All accessed 2026-10-09.
- https://www.scala-lang.org/download/all.html : 3.9.0, 3.3.8 LTS, 2.13.18, 2.12.21.
- https://scala-lang.org/news/3.9/ : 3.9 LTS date, 3-year support, 3.3 LTS one more year, `into` stable, 3.10.0-RC1, `-Ytasty-reader` stops at 3.7.
- https://www.scala-lang.org/news/3.8/ : 3.8 date, JDK 17 floor, stdlib compiled by Scala 3, better fors, runtimeChecked, REPL split.
- https://www.scala-lang.org/news/3.8.4/ : security fixes, scala-cli 1.14.0.
- https://endoflife.date/scala : release policy (Next vs LTS).
- https://www.scala-lang.org/blog/scala2-maintenance.html : VirtusLab maintains 2.12/2.13 (2026-10-07).
- https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html : JDK vs Scala minimum versions.
- https://www.scala-lang.org/blog/2026/06/29/sbt2.html , https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html : sbt 2 requirements and changes.
- https://eed3si9n.com/ : sbt 2.0.0 (2026-06-14), 2.0.10, 2.1.0-M3, 1.13.0 dates.
- https://www.scala-lang.org/blog/2026/08/10/sbt-remote-tcp-advisory.html : GHSA-m2pw-22cj-jq4v, fixed versions.
- https://docs.scala-lang.org/scala3/reference/experimental/cc.html : capture checking still experimental.
- https://docs.scala-lang.org/scala3/guides/migration/tooling-migration-mode.html : `-source:3.0-migration -rewrite`.
- https://www.scala-js.org/news/index.html : Scala.js 1.19-1.22, Wasm stable.
- https://github.com/scala-native/scala-native/releases : Scala Native 0.5.12 and supported Scala versions.
- https://github.com/com-lihaoyi/mill/releases : Mill 1.1.10.
- https://spark.apache.org/downloads.html , https://spark.apache.org/docs/latest/ : Spark 4.2.0, Scala 2.13 only, Java 17/21/25.
- https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/configuration/overview/ : Flink 2.3 Scala Table API `_2.12`.
- https://akka.io/bsl-license-faq : Akka BSL 1.1, 3-year change date, production license.
- https://pekko.apache.org/download.html : Pekko 1.7.1, Pekko HTTP 1.4.1.
- Library release pages listed in `references/ecosystem.md`.

Unverified in this pass: Scala 3.9 on Scala Native and JDK 27; Flink 2.0 Scala DataStream removal; scala3-migrate and scapegoat status; exact Scala 3 profiling flag names; library-on-3.3-vs-3.9 convention.
