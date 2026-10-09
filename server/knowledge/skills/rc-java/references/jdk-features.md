# JDK 21 to 28: what shipped, by release (verified on openjdk.org, 2026-10-09)

Status legend: FINAL = permanent feature; PREVIEW = needs `--enable-preview` (compile and run) and may change; INCUBATOR = `--add-modules jdk.incubator.*`; EXP = experimental VM flag.

## Release dates and support

| JDK | GA | Kind | Oracle Premier / Extended | Temurin (Adoptium) at least until |
|---|---|---|---|---|
| 17 | 2021-09 | LTS | 2026-09 / 2029-09 | 2027-10 |
| 21 | 2023-09-19 | LTS | 2028-09 / 2031-09 | 2029-12 |
| 25 | 2025-09-16 | LTS | 2030-09 / 2033-09 | 2031-09 |
| 26 | 2026-03-17 | feature | ended 2026-09 | ended (superseded) |
| 27 | 2026-09-15 | feature (current) | until 2027-03 | 2027-03 |
| 28 | 2027-03 (planned) | feature (EA builds now) | until 2027-09 | - |
| 29 | 2027-09 (planned) | next LTS | 2032-09 / 2035-09 | - |

Non-LTS releases get updates for 6 months only: never run production on 26 now.

## JDK 21 (LTS)
- FINAL: Virtual threads (444), Record patterns (440), Pattern matching for switch (441), Sequenced collections (431), Generational ZGC (439, opt-in then), Key Encapsulation Mechanism API (452).
- PREVIEW: String templates (430, later withdrawn), unnamed patterns/variables (443), unnamed classes + instance main (445), scoped values (446), structured concurrency (453), FFM (442).
- 451: warning when agents load dynamically (affects Mockito's inline mock maker: attach it with `-javaagent`).
- Caveat: on 21, `synchronized` blocks pin virtual threads to their carrier. Fixed in 24 (JEP 491).

## JDK 22
- FINAL: Foreign Function & Memory API (454), Unnamed variables & patterns `_` (456), launch multi-file source programs (458), G1 region pinning (423).
- PREVIEW: Stream gatherers, Class-File API, statements before `super(...)`, string templates 2nd preview (its last).

## JDK 23
- FINAL: Markdown doc comments `///` (467), ZGC generational by default (474).
- 471: `sun.misc.Unsafe` memory methods deprecated for removal.
- String templates removed: not in 23, no replacement yet. Do not generate `STR."..."`.

## JDK 24
- FINAL: Stream Gatherers (485), Class-File API `java.lang.classfile` (484), Synchronize virtual threads without pinning (491), AOT class loading & linking (483, Leyden), Quantum-resistant ML-KEM (496) and ML-DSA (497), jlink without JMODs (493).
- 486: Security Manager permanently disabled (cannot be enabled).
- 490: ZGC non-generational mode removed. 472: warnings for JNI use. 498: warnings for `Unsafe` memory access. 479: Windows 32-bit x86 removed.
- EXP: Compact object headers (450), generational Shenandoah (404).

## JDK 25 (LTS)
- FINAL: Scoped values (506), Module import declarations `import module java.base;` (511), Compact source files + instance main methods + `java.lang.IO` (512), Flexible constructor bodies (513), Key Derivation Function API (510), AOT command-line ergonomics `-XX:AOTCacheOutput` (514), AOT method profiling (515), Compact object headers as product option (519), Generational Shenandoah product (521), JFR cooperative sampling (518), JFR method timing & tracing (520).
- PREVIEW: Structured concurrency 5th (505), Stable values (502), PEM encodings (470), primitive types in patterns 3rd (507). INCUBATOR: Vector API 10th (508). EXP: JFR CPU-time profiling on Linux (509).
- 503: 32-bit x86 port removed.

## JDK 26
- FINAL: HTTP/3 for `java.net.http.HttpClient` (517), AOT object caching with any GC incl. ZGC (516), G1 throughput improvement (522).
- 500: warnings when deep reflection mutates `final` fields (`--illegal-final-field-mutation=warn|deny`); a later release will throw.
- 504: Applet API removed. `Thread.stop()` removed (JDK-8368237; old binaries get `NoSuchMethodError`).
- PREVIEW: Structured concurrency 6th (525), Lazy constants 2nd (526, renamed from stable values), PEM 2nd (524), primitive patterns 4th (530). INCUBATOR: Vector API 11th (529).

## JDK 27 (current feature release)
- 523: G1 is the default GC in all environments (previously Serial on small machines). 534: Compact object headers on by default.
- 527: Post-quantum hybrid key exchange for TLS 1.3. 536: JFR in-process data redaction.
- PREVIEW: Structured concurrency 7th (533), Lazy constants 3rd (531), primitive patterns 5th (532), PEM 3rd (538). INCUBATOR: Vector API 12th (537).

## JDK 28 (in development, GA 2027-03)
- JEP 401 Value Classes and Objects (Project Valhalla) integrated as PREVIEW. Not for production. Do not write `value class` in shipping code.

## Still not final anywhere (as of 2026-10)
Structured concurrency, lazy constants, primitive types in patterns, PEM encodings (preview); Vector API (incubator, waits for Valhalla); value classes (preview in 28). String templates: withdrawn.
