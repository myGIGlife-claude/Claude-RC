---
name: rc-java
description: Current Java (JDK 21/25 LTS, JDK 27) and JVM backend practice as of 2026-10, covering language features, Maven/Gradle builds, Spring Boot 4 / Spring Framework 7, Quarkus/Micronaut/Helidon, JPA/Hibernate 7, JUnit 6/Testcontainers 2, GraalVM native image, containers, GC/JFR tuning and security. Use when writing or reviewing *.java, pom.xml, build.gradle(.kts), application.properties/yml, Spring/Jakarta code, or when upgrading a JDK or framework.
---
# Java and the JVM ecosystem (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Your training data predates most of this. If a version or API here disagrees with your memory, trust this file and check the sources.
Per-release JEP list: `references/jdk-features.md`.

## Currency check

| Thing | Current (2026-10) | Notes |
|---|---|---|
| JDK LTS | **25** (2025-09-16), 21 (2023-09), 17 (2021-09) | Next LTS: 29 (2027-09, planned) |
| JDK feature release | **27** (2026-09-15) | 26 lost Oracle support 2026-09; 28 EA (GA 2027-03) |
| Oracle Premier support | 17 until 2026-09 (ended), 21 until 2028-09, 25 until 2030-09 | Temurin: 17 to at least 2027-10, 21 to 2029-12, 25 to 2031-09 |
| Spring Boot | **4.1.x** (4.1.1); 4.0.x OSS until 2026-12 | 4.1 OSS until 2027-07; 3.5 OSS ended 2026-06; 4.2 planned 2026-11 |
| Spring Framework | 7.0 (2025-11); 7.1 planned 2026-11 | Baseline JDK 17, recommends 25; Jakarta EE 11 |
| Jakarta EE | 11 (Java 17+; Servlet 6.1, Persistence 3.2, Data 1.0, CDI 4.1) | EE 12 in progress |
| Hibernate ORM | **7.4** stable (JPA 3.2); 8.0 beta (JPA 4.0) | 7.0+ is Apache-2.0 licensed |
| Maven | **3.10.0** (2026-09-27); 3.9.16 (2026-05) previous series; 4.0.0-rc-7 (2026-09-24), not GA | Maven 4 needs Java 17 to run |
| Gradle | **9.8.1** (2026-10) | 9.x needs Java 17+ to run; config cache preferred |
| Kotlin | 2.4.21 (2026-10-08) | 2.5.0 planned 2026-12 |
| JUnit | **6.1.3** (2026-08) | Java 17 baseline, one version for Platform/Jupiter/Vintage |
| Mockito | 5.24.0 (2026-09) | |
| Testcontainers | 2.0.x (2.0.5) | JUnit 4 support removed in 2.0 |
| Quarkus | 3.40 LTS (2026-09-30); 4.0.0.Beta1 (2026-10-01) | |
| Micronaut / Helidon | 5.2.x / 27 (Helidon now versions with the JDK) | Helidon 27 needs JDK 27; Helidon 4 is the LTS line |
| GraalVM | 25.x line (JDK 25 based) | Oracle detached GraalVM from Java SE products after JDK 24 |

Shipped in the last ~12 months (dated): JDK 25 LTS (2025-09: scoped values, compact source files + `void main()`, module imports, flexible constructor bodies, AOT cache one-step, compact object headers, generational Shenandoah); Spring Boot 4.0 / Framework 7.0 (2025-11); JDK 26 (2026-03: HTTP/3 client, `Thread.stop` removed, final-field mutation warnings, AOT cache with any GC); Spring Boot 4.1 (2026: gRPC support); JDK 27 (2026-09: G1 default everywhere, compact headers on by default, PQ hybrid TLS 1.3).

**Pick a JDK:** new projects target **25**. 21 is fine but upgrade if you use virtual threads heavily (pinning on `synchronized` was only fixed in 24). Use 27 only if you track every 6-month release. Never ship on a non-LTS release past its 6 months (26, 24, 23, 22) or anything below 17.

## What changed / stop doing

| Old (stop) | New (do) | Since |
|---|---|---|
| Fixed `ThreadPoolExecutor` / `new Thread` for blocking I/O | `Executors.newVirtualThreadPerTaskExecutor()`, or `spring.threads.virtual.enabled=true` | JDK 21 (pinning-free 24) |
| `ThreadLocal` for request context | `ScopedValue` | JDK 25 (final) |
| `CompletableFuture` fan-out with manual cancel | `StructuredTaskScope` (still **preview** in 27; only behind `--enable-preview`) | preview |
| Lombok `@Data`/`@Value` for carriers | `record` (+ compact constructor for validation) | JDK 16 |
| `instanceof` + cast chains, visitor pattern | sealed interfaces + `switch` with record patterns | JDK 21 |
| unused catch/lambda names `ignored` | `_` unnamed variables | JDK 22 |
| `list.get(list.size()-1)`, `LinkedHashSet` tricks | `getFirst()/getLast()/reversed()` (`SequencedCollection`) | JDK 21 |
| Custom collectors for windowing | `Stream.gather(Gatherers.windowFixed(n))` | JDK 24 |
| ASM / `jdk.internal.classfile` | `java.lang.classfile` (Class-File API) | JDK 24 |
| JNI, `sun.misc.Unsafe` memory | Foreign Function & Memory API (`java.lang.foreign`) | JDK 22 |
| String templates `STR."..."` | Withdrawn; use `formatted()` / text blocks | removed in 23 |
| `public static void main(String[] args)` for scripts | `void main()` in a compact source file, `IO.println` | JDK 25 |
| Security Manager sandboxing | Process/container isolation | disabled JDK 24 |
| `finalize()` | `try`-with-resources, `Cleaner` | deprecated for removal JDK 18 |
| `Thread.stop()` | cooperative interrupt | removed JDK 26 |
| Reflection writes to `final` fields | constructor injection; `--illegal-final-field-mutation=deny` to find offenders | warns JDK 26 |
| `-XX:+UseZGC -XX:+ZGenerational` | just `-XX:+UseZGC` (generational only) | JDK 24 |
| CDS archives by hand | `-XX:AOTCacheOutput=app.aot` then `-XX:AOTCache=app.aot` | JDK 25 |
| `java.util.Date`/`Calendar`/`SimpleDateFormat` | `java.time` (`Instant`, `LocalDate`, `ZonedDateTime`, `DateTimeFormatter`) | JDK 8 |
| `javax.servlet`, `javax.persistence`, `javax.inject` | `jakarta.*` (Spring 7 dropped `javax.annotation`/`javax.inject` entirely) | Boot 3 / Spring 7 |
| JAXB/JAX-WS from the JDK | Explicit `jakarta.xml.bind` dependency (or JSON) | removed JDK 11 |
| `RestTemplate` | `RestClient` (sync), `WebClient` (reactive), `@HttpExchange` interfaces | deprecated in docs 7.0, `@Deprecated` 7.1 |
| `TestRestTemplate` | `RestTestClient` | Spring 7 / Boot 4 |
| `@MockBean`/`@SpyBean` | `@MockitoBean`/`@MockitoSpyBean` | removed Boot 4 |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (+ `-webmvc-test`) | Boot 4 (modular starters) |
| Jackson 2 `com.fasterxml.jackson.databind` | Jackson 3 `tools.jackson.*` (annotations keep `com.fasterxml.jackson.annotation`) | Boot 4 (Jackson 2 deprecated) |
| Undertow | Tomcat 11 or Jetty 12.1 | removed Boot 4 |
| JSR-305 / Spring `@Nullable` | JSpecify `@NullMarked` + `@Nullable` | Spring 7 / JUnit 6 |
| Spring XML bean config | Java `@Configuration`, Boot auto-config | long ago |
| Hibernate `save/update/saveOrUpdate/delete/load` | `persist/merge/remove/getReference` | removed Hibernate 7 |
| Hibernate `@Where`, `@Proxy`, `@LazyToOne` | `@SQLRestriction`, JPA standard mappings | removed Hibernate 7 |
| JUnit 4 (`@RunWith`, `SpringRunner`) | JUnit Jupiter (JUnit 6), `SpringExtension` | Spring 7 deprecated SpringRunner |
| Testcontainers `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql`, package `org.testcontainers.postgresql` | TC 2.0 |
| `mvn` with model 4.0.0 `<modules>` | Still fine on Maven 3.9; Maven 4 uses `<subprojects>` and model 4.1.0 | Maven 4 (RC) |
| Groovy `build.gradle` for new builds | Kotlin DSL `build.gradle.kts` + version catalog | Gradle 8.2 default |
| Oracle GraalVM as a Java SE product | GraalVM CE / Liberica NIK / Mandrel for native image; Leyden AOT cache for startup | after JDK 24 |

## Do this

### Language idioms (JDK 25)
```java
sealed interface Shape permits Circle, Rect {}
record Circle(double r) implements Shape {
    Circle { if (r <= 0) throw new IllegalArgumentException("r"); }
}
record Rect(double w, double h) implements Shape {}

static double area(Shape s) {
    return switch (s) {                      // exhaustive: no default needed
        case Circle(var r)      -> Math.PI * r * r;
        case Rect(var w, var h) -> w * h;
    };
}
```
- `var` for locals when the type is obvious from the right-hand side; not for fields, not when it hides a numeric type.
- `Optional` only as a return type for "maybe absent". Never as a field, parameter, or collection element. No `opt.get()`: use `orElseThrow()`.
- Immutability: records, `List.of`/`Map.of`/`Stream.toList()` (unmodifiable), `final` fields, constructor injection.
- Text blocks for SQL/JSON literals; `String.formatted(...)`.
- Loops are fine. Streams are not mandatory; use them where they read better, not for side effects.
- Pattern matching with guards: `case Circle c when c.r() > 10 -> ...`.

### Virtual threads
```java
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = urls.stream().map(u -> exec.submit(() -> fetch(u))).toList();
    for (var f : futures) results.add(f.get());
}
```
- Never pool virtual threads. Limit concurrency to a scarce resource with a `Semaphore`, not a small pool.
- Don't cache expensive objects in `ThreadLocal` (one per task now). Use `ScopedValue` for context.
- On JDK 21 avoid `synchronized` around blocking I/O (pins the carrier); use `ReentrantLock`, or move to 24+.
- CPU-bound work gains nothing; keep a platform-thread pool / `ForkJoinPool` for it.
- Spring Boot: `spring.threads.virtual.enabled=true` switches Tomcat/Jetty, `@Async`, scheduling and more. Size the JDBC pool deliberately: thousands of virtual threads will queue on 10 connections.

### Build: Gradle 9 (Kotlin DSL)
```kotlin
// build.gradle.kts
plugins { java; alias(libs.plugins.spring.boot) }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
tasks.test { useJUnitPlatform() }
```
- Versions live in `gradle/libs.versions.toml`. Commit the wrapper (`gradlew`, `gradle/wrapper/*`); pin `distributionSha256Sum`.
- Toolchains decouple the JDK that runs Gradle (17+) from the one that compiles. Enable `org.gradle.configuration-cache=true`.

### Build: Maven 3.9 / 3.10
- Set `<maven.compiler.release>25</maven.compiler.release>` (`release`, not `source`/`target`: it also checks API usage).
- Import BOMs in `<dependencyManagement>`; use the Maven Wrapper (`mvnw`); pin every plugin version; `maven-enforcer-plugin` with `requireJavaVersion` and `dependencyConvergence`.
- Maven 4 is RC: try it with `mvnup`, but don't switch production builds until GA.

### Spring Boot 4 / Framework 7
- HTTP: `RestClient` for sync calls; declarative clients via `@HttpExchange` interfaces and `@ImportHttpServices` groups. Always set connect/read timeouts.
- Built-in resilience: `@Retryable`, `@ConcurrencyLimit` (enable with `@EnableResilientMethods`) replace many Spring Retry uses.
- API versioning: `spring.mvc.apiversion.*` or `ApiVersionConfigurer`.
- Observability: Micrometer + `spring-boot-starter-opentelemetry` (OTLP). Expose only `health`, `info`, `prometheus` publicly.
- Null-safety: Spring APIs are JSpecify-annotated. Put `@NullMarked` in `package-info.java` and run NullAway (Error Prone) so nulls fail the build. Kotlin reads JSpecify as real nullability.
- Native image / AOT: `spring-boot:process-aot` + GraalVM 25 `native-image` (or `bootBuildImage`). Costs: long builds, reachability metadata for reflection, closed-world surprises. Choose it for scale-to-zero/CLI; otherwise prefer JVM + AOT cache (`-XX:AOTCache`) for startup.
- `JdbcClient` for simple SQL, `JmsClient` (new in 7.0) for JMS.

### Persistence
- JPA entities are classes (no records: they need a no-arg constructor and mutable state). Use records for DTOs, projections and `@Embeddable` values.
- Default every association to `LAZY` (`@ManyToOne(fetch = LAZY)`); fetch with join fetch / entity graphs per query. Watch N+1 with `hibernate.generate_statistics` or a query-count assertion.
- `spring.jpa.open-in-view=false`. Transactions at the service layer. Never `ddl-auto=update` in prod.
- Schema migrations: Flyway or Liquibase. Liquibase Community 5.0+ is under the Functional Source License (FSL, converts to Apache 2.0 or MIT after two years): check your legal policy.
- jOOQ 3.20+ needs Java 21+; Boot 4.1 manages jOOQ 3.20, so jOOQ users on Boot 4.1 need Java 21. Good fit for SQL-heavy code with type-safe queries.
- Spring Data: derived queries for trivial lookups, `@Query` / jOOQ for anything else; return projections, not entities, from read APIs.

### Containers and runtime
- The JVM is container-aware: set `-XX:MaxRAMPercentage=75` instead of a hard `-Xmx`; no need for old `UseContainerSupport` flags.
- JDK 27 defaults to G1 everywhere; on 21/25 a container with <2 CPUs or <1792 MB gets Serial GC: set the GC explicitly.
- Small images: `jdeps --print-module-deps` then `jlink --add-modules ... --strip-debug --no-header-files --no-man-pages`; or a distroless/`-jre` base. Run as non-root.
- Spring Boot: layered jar + Buildpacks (`bootBuildImage` / `spring-boot:build-image`) or a multi-stage Dockerfile with `java -Djarmode=tools -jar app.jar extract`.
- CRaC (checkpoint/restore) needs a CRaC-enabled JDK build (e.g. Azul, BellSoft); not in mainline OpenJDK. Leyden AOT cache is the standard path.

### Alternatives
- Quarkus (3.40 LTS; 4.0 beta): build-time DI, Dev Services, strong native image story.
- Micronaut 5: compile-time DI, no reflection.
- Helidon SE: virtual-thread-native web server, minimal magic.
- Javalin: tiny library for small HTTP services.
- Kotlin in a Java shop: fine for new modules; compile with `-Xjsr305=strict`/JSpecify, use `kotlin("plugin.spring")` (all-open) and `plugin.jpa` (no-arg); keep public APIs Java-friendly (`@JvmStatic`, `@JvmOverloads`).

## Security
- **Deserialization:** never `ObjectInputStream` on untrusted data. If unavoidable, set a JVM-wide filter (`-Djdk.serialFilter=...` allow-list, or `ObjectInputFilter.Config.setSerialFilterFactory`). Jackson: no default typing / `@JsonTypeInfo(use = CLASS)` on untrusted input.
- **JNDI / Log4Shell (CVE-2021-44228) lessons:** never let user input reach lookups or expression evaluators (JNDI, SpEL, OGNL, EL, template engines). Keep Log4j 2 at the latest 2.x; prefer SLF4J + Logback/Log4j2 with parameterized logging (`log.info("id={}", id)`).
- **SQL injection:** bind parameters (`JdbcClient.param`, JPA `:name`, jOOQ). Never build JPQL/HQL/native SQL with string concatenation, including `ORDER BY` columns (allow-list them).
- **XXE:** for `DocumentBuilderFactory`/`SAXParserFactory`/`XMLInputFactory`/`TransformerFactory`, disable DTDs (`disallow-doctype-decl`) and external entities; set `XMLConstants.FEATURE_SECURE_PROCESSING`.
- **Randomness:** tokens/keys use `new SecureRandom()` (default, non-blocking). Not `Random`/`ThreadLocalRandom`/`Math.random`. Avoid `getInstanceStrong()` on servers (can block).
- **TLS:** use the JDK defaults (TLS 1.3/1.2; 1.0/1.1 disabled). Never install trust-all `TrustManager`s or `HostnameVerifier`s. JDK 27 adds post-quantum hybrid key exchange for TLS 1.3.
- **Crypto:** AES-GCM with a fresh 12-byte IV, never ECB; passwords via Spring Security `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt/argon2), never SHA-x.
- **Secrets:** from env / secret manager / Spring Cloud config, never in `application.yml` committed. Mask in actuator (`/env` off publicly).
- **Supply chain:** OWASP Dependency-Check or Snyk/Trivy/Grype in CI, Dependabot/Renovate, Gradle dependency verification (`verification-metadata.xml`) or Maven checksum policy; SBOM via CycloneDX plugin.
- **Spring Security 7:** lambda DSL only, `SecurityFilterChain` beans (no `WebSecurityConfigurerAdapter`), CSRF on for browser sessions.
- **Reflection hardening:** run with `--illegal-final-field-mutation=deny` in tests (JDK 26+) and watch JNI/Unsafe warnings (JDK 24+): they become errors later.

## Performance & quality
- **GC choice:** G1 default (balanced). ZGC (`-XX:+UseZGC`) for large heaps needing <1 ms pauses. Generational Shenandoah also low-pause. Parallel for batch throughput. Serial only for tiny single-core containers.
- **Startup:** JDK 25+ AOT cache: `java -XX:AOTCacheOutput=app.aot -jar app.jar` (training run) then `java -XX:AOTCache=app.aot -jar app.jar`. Same JDK and classpath required.
- **Memory:** `-XX:+UseCompactObjectHeaders` on 25/26 (default on 27) cut SPECjbb2015 heap 22% per JEP 534; your gain varies (measure).
- **Profiling:** JFR always-on in prod is cheap: `-XX:StartFlightRecording=maxage=1h,filename=rec.jfr`; analyze with JDK Mission Control or `jfr print`. async-profiler for flame graphs (CPU, alloc, lock). JDK 25 adds JFR method timing/tracing and experimental CPU-time sampling on Linux.
- **Microbenchmarks:** only JMH; never `System.nanoTime()` loops.
- **Measure:** p99 latency, GC pause time and frequency, allocation rate, heap after GC, startup to first request, RSS in container, DB query count per request.

## Testing & tooling
- JUnit 6 (Jupiter) + AssertJ + Mockito 5. Add Mockito as `-javaagent` in the test JVM (JDK 21+ warns on dynamic agent attach and a future JDK will refuse it).
- Spring slices: `@WebMvcTest`, `@DataJpaTest`, `@JdbcTest`; full tests `@SpringBootTest` + `@AutoConfigureMockMvc` (no longer implicit in Boot 4) or `RestTestClient`.
- Testcontainers 2 with `@ServiceConnection` (Boot) for real Postgres/Kafka; never H2 as a stand-in for a production database.
- ArchUnit for layering rules; Error Prone + NullAway; Spotless with google-java-format or palantir-java-format; Checkstyle/PMD optional; JaCoCo for coverage; PIT for mutation testing on core logic.
- CI: `./gradlew build` / `./mvnw verify` on the target JDK (Temurin via `actions/setup-java`), plus the next feature release as a non-blocking canary.

## Common mistakes in AI-written code
- `STR."Hello \{name}"`: string templates do not exist in any shipping JDK.
- `StructuredTaskScope.ShutdownOnFailure`: old preview API, gone. Current preview uses `StructuredTaskScope.open(Joiner...)` and still needs `--enable-preview`.
- `ScopedValue.where(...).run(...)` is fine on 25; claiming it needs preview there is wrong. Calling `ScopedValue` on 21 without preview is wrong.
- `Thread.stop()`, `finalize()`, `SecurityManager`, `Runtime.runFinalization`: gone or dead.
- `javax.persistence.*`, `javax.servlet.*`, `javax.annotation.PostConstruct` with Spring Boot 3/4.
- `WebSecurityConfigurerAdapter`, `antMatchers()`, `authorizeRequests()`: removed; use `requestMatchers` and `authorizeHttpRequests`.
- `@MockBean`, `@RunWith(SpringRunner.class)`, `TestRestTemplate` in Boot 4 code.
- `new RestTemplate()` in new code; missing timeouts on any HTTP client.
- `spring-boot-starter-web` + `com.fasterxml.jackson.databind.ObjectMapper` imports in a Boot 4 project (Jackson 3 `tools.jackson.databind.json.JsonMapper`).
- `session.save(entity)` / `@Where` with Hibernate 7.
- Records annotated `@Entity`; `FetchType.EAGER` everywhere; `@Transactional` on private methods or self-invocation (proxy bypass).
- Lombok added by default for getters/builders on what should be a record.
- `Executors.newFixedThreadPool(200)` for blocking calls; pooling virtual threads.
- `new Date()`, `SimpleDateFormat` as a static field (not thread-safe).
- `-XX:+ZGenerational`, `-XX:+UseContainerSupport`, `-XX:MaxPermSize`: obsolete flags.
- Targeting Java 8/11 or EOL JDK 26 in Dockerfiles (`FROM openjdk:...` images are deprecated; use `eclipse-temurin:25-jre` or similar).
- Generating `value class` / `--enable-preview` code for production.
- `catch (Exception e) {}` swallowing; `e.printStackTrace()` instead of logging.

## Before you ship
- [ ] JDK 25 (or 21) LTS, same version in build toolchain, CI and container base image.
- [ ] No preview/incubator flags in production unless explicitly accepted.
- [ ] Spring Boot 4.x on a supported line (4.0 OSS ends 2026-12); no `javax.*` imports.
- [ ] Wrapper committed, plugin and dependency versions pinned, dependency scan clean, SBOM produced.
- [ ] Tests on JUnit 6 + Testcontainers against the real DB engine; ArchUnit/NullAway pass.
- [ ] HTTP clients have timeouts; DB pool sized for the thread model; `open-in-view=false`.
- [ ] No string-built SQL, XML parsers hardened, no untrusted Java deserialization, secrets from env/secret store.
- [ ] Container: non-root, `MaxRAMPercentage`, explicit GC, health/readiness probes, JFR available.
- [ ] Startup/latency measured; AOT cache or native image only if the numbers justify it.

## Sources
All accessed 2026-10-09.
- https://openjdk.org/projects/jdk/21/ , /22/ , /23/ , /24/ , /25/ , /26/ , /27/ : GA dates and JEP lists per release.
- https://jdk.java.net/27/ : JDK 27 GA builds; JDK 28 early access.
- https://www.oracle.com/java/technologies/java-se-support-roadmap.html : Oracle Premier/Extended support dates, 29 as next LTS.
- https://adoptium.net/support/ : Temurin support windows.
- https://openjdk.org/jeps/401 : Value classes integrated as preview, target JDK 28.
- https://openjdk.org/jeps/512 , /jeps/514 , /jeps/525 , /jeps/500 : compact source files, AOT cache flags, structured concurrency API, final-field mutation warnings.
- https://bugs.openjdk.org/browse/JDK-8368370 : Thread.stop removed in JDK 26.
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes , .../Spring-Boot-4.0-Migration-Guide , .../Spring-Boot-4.1-Release-Notes : Boot 4 changes, starters, Jackson 3, test changes, 4.1 features.
- https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes : Framework 7 baselines, removals, JSpecify, resilience, RestTestClient.
- https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring/ : RestTemplate deprecation timeline.
- https://spring.io/projects/spring-boot : current Boot 4.1.1. https://api.spring.io/projects/spring-boot/generations : Boot OSS/commercial support end dates.
- https://jakarta.ee/release/11/ : Jakarta EE 11 contents.
- https://hibernate.org/orm/releases/ , https://docs.hibernate.org/orm/7.0/migration-guide/migration-guide.html : Hibernate series and 7.0 removals.
- https://maven.apache.org/docs/history.html , https://maven.apache.org/whatsnewinmaven4.html : Maven versions, Maven 4 changes.
- https://gradle.org/releases/ , https://docs.gradle.org/9.0.0/release-notes.html : Gradle versions, 9.0 requirements.
- https://kotlinlang.org/docs/releases.html : Kotlin 2.4.21.
- https://docs.junit.org/current/release-notes/ : JUnit 6.1.3, 6.0 changes.
- https://github.com/mockito/mockito/releases : Mockito 5.24.0.
- https://java.testcontainers.org/ , https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0 : Testcontainers 2 breaking changes.
- https://quarkus.io/blog/ , https://micronaut.io/ , https://helidon.io/ : framework versions. https://medium.com/helidon/helidon-27-released-9ce206503e0a (Helidon team blog): Helidon 27 needs JDK 27, 4.x is LTS.
- https://www.graalvm.org/release-notes/ , https://blogs.oracle.com/java/detaching-graalvm-from-the-java-ecosystem-train : GraalVM 25 line, decoupling from Java SE.
- https://docs.liquibase.com/community/release-notes/5-0 : Liquibase 5.0 FSL license.

Unverified in this pass: JSpecify current version, Flyway current version, async-profiler current version, Boot 4.1 GA day (spring.io lists 2026-06-30 as the generation start).
