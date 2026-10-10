# Scala library ecosystem (as of 2026-10-09)

Versions are from the project's release page on the access date. "-" means not checked this pass: look it up on Maven Central
(`cs complete-dep org:artifact_3:`) before pinning. Never invent a version.

## Effects and concurrency
| Library | Version | Notes |
|---|---|---|
| Cats Effect | 3.7.1 (3.6.x still patched) | 3.6 added the integrated runtime (I/O polling in the work-stealing pool); 3.7 adds Scala Native 0.5 multithreading. No 4.0 announced |
| fs2 | - | Streams for CE; pairs with http4s, skunk |
| ZIO | 2.1.26 | 2.1.25 had a long-running-stream regression (zio-kafka): skip it. No ZIO 3 release |
| Ox | 1.0.9 | Scala 3 + JDK 21+; direct style on virtual threads: `supervised`, `par`, `fork`, `Flow`, retries, circuit breaker |
| Apache Pekko | core 1.7.1 (2026-09-04), HTTP 1.4.1 (2026-09-06), gRPC 1.2.0 | Apache-2.0 fork of Akka 2.6; package `org.apache.pekko` |
| Akka | BSL 1.1 | Each release becomes Apache 2.0 after 3 years; production use needs a commercial license (free paths exist for some orgs, check the FAQ). Do a license review first |

Rule: one runtime per service. Interop modules (zio-interop-cats) are for migrations, not designs.

## HTTP and web
| Library | Version | Notes |
|---|---|---|
| http4s | 0.23.38 stable; 1.0.0-M49 | 0.23.35 / 1.0.0-M47 fixed 18 security issues (HTTP/2 DoS, smuggling, cookies, multipart). Stay on 0.23 for production; 1.0 is still milestones |
| Tapir | 1.13.33 | Endpoint-as-value; interpreters for http4s, Netty (incl. sync/Ox), Pekko HTTP, ZIO HTTP, Play; OpenAPI docs. Built with sbt 2 now |
| sttp client | - | Client side of the Tapir world; sttp 4 is current (unverified) |
| Play | 3.0.12 (Pekko) / 2.9.12 (Akka) | Netty CVE bumps in these patches. 3.1.0-M9 milestone published for sbt 2 |
| zio-http | - | ZIO-native server/client |

## JSON
| Library | Version | Notes |
|---|---|---|
| circe | 0.14.17 | Typelevel default; `derives Codec.AsObject` on Scala 3 |
| zio-json | 1.0.0 | Reached 1.0 (2026, per Tapir release notes) |
| jsoniter-scala | - | Fastest; compile-time codecs; good for hot paths |
| upickle | - | Li Haoyi stack (Mill, scala-cli scripts, os-lib) |

## Databases
| Library | Version | Notes |
|---|---|---|
| doobie | 1.0.0-RC13 | **Breaking**: groupId `org.typelevel`, packages `org.typelevel.doobie` (scalafix rule `doobie-package-rename-scalafix`). `java.time` instances moved to `doobie-postgres`/`doobie-mysql`; auto derivation now opt-in; queries cancel on fiber cancel |
| skunk | - | Pure-functional Postgres protocol client for CE (no JDBC) |
| Quill / ZIO Quill | - | Compile-time SQL quotation; Scala 3 support via ProtoQuill (check status before adopting) |
| Slick | - | Still maintained; avoid early-2010s patterns (blocking `Await` around `db.run`, `Future` everywhere without timeouts) |
| Magnum | - | Small JDBC library for direct-style Scala 3 code (unverified current status) |

## Testing
| Library | Notes |
|---|---|
| MUnit | Light, good IDE support; `munit-cats-effect`, `munit-scalacheck` |
| ScalaTest 3.2 | Many styles; pick one per repo. ScalaCheck via `scalatestplus` |
| weaver-test | Cats Effect native, parallel by default |
| zio-test | ZIO native; `check` for property tests, `TestClock` |
| ScalaCheck | 1.20.0 (seen in circe's deps) |

## Platforms and data
| Thing | Version | Notes |
|---|---|---|
| Scala.js | 1.22.0 | Wasm backend stable; `js.async`/`js.await` since 1.19 |
| Scala Native | 0.5.12 | LLVM; CE 3.7 multithreaded support |
| Spark | 4.2.0 (2026-07-14); 4.1.3, 4.0.4, 3.5.9 patches | 4.x = Scala 2.13 only, Java 17/21/25 |
| Flink | 2.3.0 | Scala Table API `_2.12` only; use the Java DataStream API from Scala |

## Sources (accessed 2026-10-09)
- https://github.com/typelevel/cats-effect/releases , https://github.com/zio/zio/releases , https://github.com/softwaremill/ox
- https://pekko.apache.org/download.html , https://akka.io/bsl-license-faq
- https://github.com/http4s/http4s/releases , https://github.com/softwaremill/tapir/releases , https://github.com/playframework/playframework/releases
- https://github.com/circe/circe/releases , https://github.com/typelevel/doobie/releases
- https://www.scala-js.org/news/index.html , https://github.com/scala-native/scala-native/releases
- https://spark.apache.org/downloads.html , https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/configuration/overview/
