# ECMAScript features: edition and browser support (as of 2026-10)

Edition = year in TC39 `finished-proposals.md`. Baseline = web-features 3.42.0 (`low` = newly available in all core browsers,
`high` = widely available, 30 months later). Versions are the first shipping release. Node column is derived from the V8 version each Node line ships (Chrome N ~ V8 N/10); treat as a guide and check `node -p` if it matters. Accessed 2026-10-09.

| Feature | Edition | Baseline | Chrome / Firefox / Safari | Node |
|---|---|---|---|---|
| `Array.prototype.findLast/findLastIndex` | ES2023 | high (2022-08) | 97 / 104 / 15.4 | 18+ |
| `toSorted/toReversed/toSpliced/with` | ES2023 | high (2023-07) | 110 / 115 / 16 | 20+ |
| `structuredClone()` (web/Node API, not ECMA) | n/a | high (2022-03) | 98 / 94 / 15.4 | 17+ |
| `Object.groupBy` / `Map.groupBy` | ES2024 | high (2024-03) | 117 / 119 / 17.4 | 21+ |
| `Promise.withResolvers` | ES2024 | high (2024-03) | 119 / 121 / 17.4 | 22+ |
| `Array.fromAsync` | ES2026 | high (2024-01) | 121 / 115 / 16.4 | 22+ |
| `AbortSignal.any` (DOM) | n/a | high (2024-03) | 116 / 124 / 17.4 | 20.3+ |
| `AbortSignal.timeout` (DOM) | n/a | low (2024-04) | 124 / 100 / 16 | 17.3+ |
| Set methods (`union`, `intersection`, `difference`, `symmetricDifference`, `isSubsetOf`, ...) | ES2025 | low (2024-06) | 122 / 127 / 17 | 22+ |
| `Promise.try` | ES2025 | low (2025-01) | 128 / 134 / 18.2 | 24+ (V8 12.8+; Node 22 is V8 12.4) |
| Sync iterator helpers (`.map/.filter/.take/.drop/.flatMap/.reduce/.toArray`, `Iterator.from`) | ES2025 | low (2025-03) | 122 / 131 / 18.4 | 22+ |
| `RegExp.escape` | ES2025 | low (2025-05) | 136 / 134 / 18.2 | 24+ |
| JSON modules / import attributes `with { type: "json" }` | ES2025 | low (2025-04) | 123 / 138 / 17.2 | 22+ (stable) |
| Float16Array, `Math.f16round` | ES2025 | low (2025-04) | 135 / 129 / 18.2 | 24+ |
| RegExp modifiers `(?i:...)`, duplicate named groups | ES2025 | unverified | unverified | 23+ (unverified) |
| `JSON.parse` source text access (`context.source`, `JSON.rawJSON`) | ES2026 | low (2025-03) | 114 / 135 / 18.4 | 22+ (unverified) |
| `Uint8Array.fromBase64/fromHex`, `.toBase64()/.toHex()` | ES2026 | low (2025-09) | 140 / 133 / 18.2 | 25+ (V8 14.0+) |
| `Map/WeakMap.prototype.getOrInsert(Computed)` (upsert) | ES2026 | low (2026-02) | 145 / 144 / 26.2 | 26+ |
| `Iterator.concat` (sequencing) | ES2026 | low (2026-03) | 146 / 147 / 26.4 | 26+ |
| `Math.sumPrecise` | ES2026 | low (2026-04) | 147 / 137 / 26.2 | not in Node 26 (V8 14.6) |
| `Error.isError` | ES2026 | not Baseline | 134 / 138 / - | 24+ (unverified) |
| `using` / `await using`, `DisposableStack`, `Symbol.dispose` | ES2027 (Stage 4, 2026) | not Baseline | 134 / 141 / - | 24+ |
| Temporal | ES2027 (Stage 4, 2026-03) | not Baseline | 144 / 139 / - | 26+ (default on) |
| Iterator chunking, `Iterator.zip` (joint iteration), iterator includes/join | ES2027 | not yet | check MDN | check |
| Import maps (HTML) | n/a | high (2023-03) | 89 / 108 / 16.4 | n/a |

## Not standard yet (do not present as standard)
- Decorators: Stage 2.7 (README of tc39/proposals, 2026). TS supports the 2022 design (`experimentalDecorators: false`); no browser ships it.
- Stage 3: `import defer` (Deferring Module Evaluation), Source Phase Imports, Import Text (`with { type: "text" }`, Chrome 155 / Firefox 153 per web-features), Await Dictionary.
- AsyncContext: Stage 2. ShadowRealm: Stage 2.7. Type annotations proposal: not active. Records & Tuples withdrawn; "Composites" at Stage 2.
- Pipeline operator, pattern matching: not in the active list; do not use (Babel-only).

## TypeScript lib targeting
- TS 6.0 adds `target`/`lib` `es2025`; Temporal types via `esnext` or `esnext.temporal`.
- `lib: ["dom"]` now includes `dom.iterable` and `dom.asynciterable` (TS 6.0).
- Type support existing does not mean runtime support: gate on your real browser/Node targets.
