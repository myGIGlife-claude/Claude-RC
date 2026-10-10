# C# features by version and library notes (as of 2026-10)

Check the project's TFM first: the default C# version follows it (net8.0 -> 12, net9.0 -> 13, net10.0 -> 14, net11.0 -> 15).
Don't use a feature newer than the project's language version. Sources: SKILL.md "Sources" (accessed 2026-10-09).

## C# by version

| C# | Ships with | Features worth using |
|---|---|---|
| 9 | .NET 5 | records, `init`, top-level statements, relational/logical patterns (`is > 0 and < 10`, `is not null`) |
| 10 | .NET 6 | `global using`, file-scoped namespaces, record structs, extended property patterns (`{ A.B: 1 }`) |
| 11 | .NET 7 | raw string literals `"""`, list patterns `[1, .., var last]`, `required` members, generic math, `file` types, UTF-8 literals `"x"u8` |
| 12 | .NET 8 | primary constructors on classes/structs, collection expressions `[..a, b]`, `alias any type`, default lambda params, `[InlineArray]` |
| 13 | .NET 9 | `params` collections (`params ReadOnlySpan<T>`), `System.Threading.Lock`, `\e` escape, partial properties/indexers, `ref struct` in generics (`allows ref struct`), `OverloadResolutionPriority` |
| 14 | .NET 10 (2025-11) | `extension` blocks (extension properties, static extensions, operators), `field` keyword, `x?.P = v` / `x?.P += v`, `nameof(List<>)`, implicit `Span`/`ReadOnlySpan` conversions, modifiers on untyped lambda params (`(text, out result) => ...`), partial constructors/events, user-defined compound assignment and `++`/`--`, file-based app directives |
| 15 | .NET 11 (GA expected 2026-11; RC1 2026-09-08) | `union Pet(Cat, Dog, Bird);` with exhaustive `switch`, `closed` class hierarchies (implicitly abstract, derivation only in the declaring assembly), extension indexers, labeled `break outer;`/`continue outer;`, collection expression arguments `[with(capacity: n), ..xs]`, `[with(StringComparer.OrdinalIgnoreCase), ...]`. Memory-safety rework (`unsafe(expr)`, pointer relaxations, `safe` keyword) is **preview only** (`LangVersion preview`) |

### Notes and traps
- Records: value equality and `with` copies. Good for DTOs, messages, value objects; bad for EF entities (identity semantics) and for Unity serialization.
- Primary constructor parameters on classes are captured mutable state, not fields: assign to a `readonly` field if you need immutability, and don't also declare a field with the same meaning.
- `required` + object initializers enforce construction at compile time; `[SetsRequiredMembers]` on constructors that set them.
- `field`: in C# 14 any existing member named `field` inside an accessor now binds to the keyword; rename or use `@field`.
- Extension blocks live in a non-generic `static class`; old `this` extension methods still work and can coexist.
- Unions (C# 15): implicit conversion from each case type; runtime types `UnionAttribute`/`IUnion` live in `System.Runtime.CompilerServices` (.NET 11). STJ in .NET 11 serializes C#/F# unions; minimal APIs describe them as OpenAPI `anyOf` (body only).
- Pattern matching: prefer `is null`/`is not null` over `== null` (operator overloads); list and property patterns over index arithmetic.
- Nullable reference types: annotations are compile-time only. Validate at trust boundaries anyway (`ArgumentNullException.ThrowIfNull`, `ArgumentException.ThrowIfNullOrEmpty`).

## Library and API notes

| Area | Current guidance |
|---|---|
| JSON | `System.Text.Json`; `JsonSerializerOptions.Web` for web defaults; `[JsonSerializable]` context for AOT/perf; `JsonSerializerOptions.Strict` and duplicate-property rejection in .NET 10 (check exact option names in the docs); JSONL output and union support in .NET 11 |
| Time | `TimeProvider` (inject; `FakeTimeProvider` in tests), `DateTimeOffset` for instants, `DateOnly`/`TimeOnly` for calendar values; `TimeZoneInfo.FindSystemTimeZoneById` accepts IANA ids on all OSes (ICU) |
| Collections | `FrozenDictionary`/`FrozenSet` for read-mostly lookups; `SearchValues<T>` for repeated `IndexOfAny`; `PriorityQueue<T,P>`; `OrderedDictionary<K,V>` (generic, .NET 9) |
| LINQ | `CountBy`, `AggregateBy`, `Index` (.NET 9); `LeftJoin`/`RightJoin` (.NET 10); `FullJoin` and tuple `Join` (.NET 11); `System.Linq.AsyncEnumerable` in-box (.NET 10) |
| HTTP | `IHttpClientFactory`, typed clients, `AddStandardResilienceHandler()`; `SocketsHttpHandler.PooledConnectionLifetime` for long-lived clients |
| Crypto | `RandomNumberGenerator.GetInt32/GetString`, `AesGcm`, `HKDF`, `Rfc2898DeriveBytes.Pbkdf2`; ML-KEM/ML-DSA (.NET 10, OS-dependent); `X25519DiffieHellman` and AES Key Wrap (.NET 11) |
| Compression | Brotli/GZip/ZLib; Zstandard in `System.IO.Compression` (.NET 11) |
| Process | .NET 11 adds run-and-capture helpers, `Process.Signal`, `ProcessExitStatus`, `Process.TryGetProcessById` |
| Async | Runtime-native async in .NET 11 (better stack traces, fewer allocations; no code change) |
| Validation | DataAnnotations + `AddValidation()` (minimal APIs, .NET 10); async validators `AsyncValidationAttribute`/`IAsyncValidatableObject` (.NET 11) |
| Caching | `HybridCache` (L1 memory + optional L2 `IDistributedCache`, stampede protection, tags, `RemoveByTagAsync("*")` invalidates all, logically); keys from trusted ids only |

## Release cadence reminder
- New major every November. Even numbers are LTS (3 years), odd are STS (24 months since the policy change; .NET 9 ends together with .NET 8 on 2026-11-10).
- Monthly patches on Patch Tuesday; keep the runtime on the latest patch of its major.
- .NET 12 is the next LTS (expected 2027-11, unverified).
