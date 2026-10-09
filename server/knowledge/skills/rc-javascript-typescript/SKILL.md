---
name: rc-javascript-typescript
description: Current JavaScript (ES2024-ES2027) and TypeScript (6.0 / native 7.0) standards, tsconfig, module systems (ESM/CJS, require(esm), package.json exports), async patterns, runtime validation, linting, package managers, testing, bundling and npm supply-chain security. Use when writing or reviewing .js/.mjs/.cjs/.ts/.mts/.tsx files, tsconfig.json, package.json, eslint.config.*, biome.json, pnpm-workspace.yaml, vitest/playwright config, or publishing to npm.
---
# JavaScript & TypeScript (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Language feature support tables: `references/es-features.md`. Tool versions and config snippets: `references/toolchain.md`.

## Currency check

| Thing | Current (2026-10) | Notes |
|---|---|---|
| ECMAScript | ES2026 is the latest edition (June 2026) | Temporal, `using`, iterator chunking/join/zip are Stage 4 but slated for **ES2027** per TC39 finished-proposals list |
| TypeScript | **7.0.2** (`typescript` on npm, 2026-07-08) | Go-based native compiler, binary still `tsc`. 8-12x faster full builds. **No programmatic API until 7.1** |
| TypeScript 6.0 | 6.0.2 (2026-03-23), also as `@typescript/typescript6` (binary `tsc6`) | Last JS-based compiler. Bridge release: new defaults + deprecations that 7.0 removes |
| Node.js | 26 = Current (2026-05-05), **LTS from 2026-10-28**; 24 = Active LTS, maintenance from 2026-10-20, EOL 2028-04-30; 22 = Maintenance, EOL 2027-04-30; 20 EOL 2026-04-30 | From Node 27 (2027-04) one major per year, every major becomes LTS |
| npm | 12.x (12.0.0 on 2026-07-08) | Dependency install scripts blocked by default; git/remote deps off by default |
| pnpm | 12.x (Rust rewrite, 12.0.0 on 2026-08-26); 11 (2026-04-28) | 11 added `minimumReleaseAge` 1 day default and `allowBuilds` |
| Yarn | 4.18 (Berry) | Yarn 1 classic is legacy |
| Bun | 1.4.x (1.4.0 on 2026-08-20) | |
| ESLint | 10.x (10.0.0 on 2026-02-06) | eslintrc removed entirely |
| typescript-eslint | 8.71 | peer `typescript >=4.8.4 <6.1.0`: does NOT run on TS 7 (no API) |
| Vitest | 5.x (5.0.0 on 2026-09-03) | Node >=22.12 |
| Vite / Rolldown | Vite 8 (2026-03-12, Rolldown inside); Rolldown 1.0 (2026-05-07) | |
| Zod / Valibot / ArkType | 4.6 / 1.5 / 2.2 | All implement Standard Schema |

What shipped in the last ~12 months: TS 6.0 + 7.0, Node 26 with Temporal on by default, Temporal (2026-03) and Explicit Resource Management (2026) reach Stage 4, decorators moved **back to Stage 2.7**, ESLint 10, npm 12, pnpm 11/12, Vitest 5, Vite 8, Rolldown 1.0, oxfmt beta (2026-02), classic npm tokens revoked (2025-12-09).

## What changed / stop doing

| Old / outdated | Do instead | Since |
|---|---|---|
| `var`, IIFE modules, `arguments` | `const`/`let`, ESM, rest params | ES2015 |
| Callback-first APIs, `util.promisify` everywhere | `async`/`await`, `node:fs/promises`, `node:timers/promises` | Node 16+ |
| CommonJS-first packages, dual-package hazard builds | ESM-only packages; CJS consumers can `require(esm)` | require(esm) on by default Node 22.12/20.19, stable 25.4 |
| `"moduleResolution": "node"` (node10), `classic` | `nodenext` (Node) or `bundler` (Vite/esbuild/Rolldown apps) | deprecated TS 6.0, **removed TS 7.0** |
| `baseUrl` for aliases | `paths` (relative to tsconfig) or package.json `"imports"` (`#/…`) | removed TS 7.0 |
| `target: es5`, `downlevelIteration`, `module: amd/umd/system/none`; `outFile` | ES2015+ target, a bundler | removed TS 7.0 (`outFile`: deprecated 6.0) |
| `esModuleInterop: false` | always on (cannot be disabled) | TS 7.0 |
| `import x from "./a.json" assert { type: "json" }` | `with { type: "json" }` | `assert` deprecated TS 6.0 |
| `enum`, `namespace`, parameter properties | `as const` objects + union types, ES modules, explicit fields | `erasableSyntaxOnly` TS 5.8, Node type stripping |
| `experimentalDecorators` + `emitDecoratorMetadata` for new code | plain functions/composition; standard decorators only when a framework requires them | TC39 decorators back at Stage 2.7 (2026-05), no engine ships them |
| `any` as default, `// @ts-ignore` | `unknown` + narrowing, `// @ts-expect-error` with reason | |
| `"strict": false` default | TS 6.0 defaults `strict: true`, `module: esnext`, `target: es2025`, `types: []` | TS 6.0 |
| `ts-node` / `ts-node-dev` | `node file.ts` (type stripping, stable) or `tsx` | Node 22.18 / 23.6 default on, stable 24.12 / 25.2 |
| `--experimental-transform-types` | erasable syntax only | flag removed Node 26 |
| `.eslintrc.*`, tslint | `eslint.config.js` (flat) + `typescript-eslint`, or Biome / oxlint | eslintrc removed ESLint 10; tslint dead since 2019 |
| `tsup` | `tsdown` (Rolldown-based) | tsup README: "not actively maintained" |
| Jest for new ESM/TS projects | Vitest or `node:test` | |
| moment.js | `Temporal` (polyfill where missing), `Intl.*`, date-fns | moment in maintenance mode since 2020 |
| lodash `groupBy`, `cloneDeep`, `uniq`, `last`, `flatten`, `isEqual`-for-sets | `Object.groupBy`/`Map.groupBy`, `structuredClone`, `new Set()`, `.at(-1)`, `.flat()`, Set methods | Baseline 2022-2024 |
| `arr.sort()` / `reverse()` / `splice()` on shared arrays | `toSorted()` / `toReversed()` / `toSpliced()` / `with()` | ES2023 |
| `new Promise((res, rej) => …)` to leak resolvers | `Promise.withResolvers()` | ES2024 |
| Hand-written regex escaping | `RegExp.escape(str)` | ES2025 |
| `try/finally` cleanup boilerplate | `using` / `await using` + `Symbol.dispose` (TS, Node 24+, Chrome 134+, Firefox 141+; not Safari) | Stage 4 2026 |
| `btoa(String.fromCharCode(...bytes))` | `bytes.toBase64()`, `Uint8Array.fromBase64()` | Baseline 2025-09 |
| `instanceof Error` across realms | `Error.isError(x)` (not Safari yet) | ES2026 |
| Float sums with drift | `Math.sumPrecise(iterable)` (browsers; not yet in Node 26) | Baseline 2026-04 |
| `if (!m.has(k)) m.set(k, v); m.get(k)` | `m.getOrInsert(k, v)` / `getOrInsertComputed(k, fn)` | Baseline 2026-02 |
| `setTimeout` + manual abort wiring | `AbortSignal.timeout(ms)`, `AbortSignal.any([...])` | Baseline 2024 |
| node-fetch / axios by default on Node | global `fetch` (Undici) | Node 18+ |
| npm classic tokens / long-lived `NPM_TOKEN` in CI | Trusted publishing (OIDC) | classic tokens revoked 2025-12-09 |
| `module.register()` loader hooks | `module.registerHooks()` (sync, in-thread) | runtime-deprecated Node 26 |

## Do this

### Project baseline
- `package.json`: `"type": "module"`, `"engines": { "node": ">=24" }`, `"packageManager": "pnpm@12.x"` (or npm). One lockfile, committed.
- Use `node:` prefix for built-ins (`import { readFile } from "node:fs/promises"`).
- Internal aliases via `"imports"` (works in Node, TS, bundlers, no `paths` needed):
  ```json
  { "imports": { "#/*": "./src/*" } }
  ```
  TS 6.0+ supports `#/` subpath imports.
- Libraries: ESM-only with `"exports"`, ship `.d.ts`, set `"files"`. Add a `"module-sync"` condition only if you must serve sync-ESM to `require()`.
  ```json
  { "exports": { ".": { "types": "./dist/index.d.ts", "default": "./dist/index.js" },
                 "./package.json": "./package.json" } }
  ```

### tsconfig (app run by Node, no build step)
```jsonc
{
  "compilerOptions": {
    "target": "es2024", "module": "nodenext",      // or "node20" for a frozen model
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "noImplicitOverride": true,
    "noFallthroughCasesInSwitch": true,
    "verbatimModuleSyntax": true,                  // forces `import type`
    "erasableSyntaxOnly": true,                    // no enum/namespace/param props
    "rewriteRelativeImportExtensions": true,       // write "./x.ts" in imports
    "types": ["node"],                             // TS 6+: types default is []
    "skipLibCheck": true,
    "noEmit": true
  }
}
```
- Bundled app (Vite etc.): `"module": "esnext"`, `"moduleResolution": "bundler"`, `"noEmit": true`.
- Library: add `"declaration": true`, `"isolatedDeclarations": true` (lets tsdown/oxc emit `.d.ts` without the checker), drop `noEmit`, keep `module: nodenext`.
- TS 6/7 default `types: []`: forgetting `"types": ["node"]` gives "Cannot find name 'process'".

### TypeScript 7 adoption
- CLI checking: `typescript@7` (`tsc`). Tools that need the compiler API (typescript-eslint type-aware rules, Vue/Svelte/Astro/MDX/Angular template checkers) still need 6.0: keep `"typescript": "npm:@typescript/typescript6@^6.0.2"` and install 7 under an alias (e.g. `"@typescript/native": "npm:typescript@^7.0.2"`), run both in CI until 7.1 ships an API (this alias pair is the one the TS 7.0 announcement shows; binaries `tsc` = 7, `tsc6` = 6).
- JSDoc-typed JS in TS 7: no `@enum`, no Closure function syntax, values can't stand in for types (use `typeof`).

### Type-level idioms
```ts
const ROLE = { Admin: "admin", User: "user" } as const;      // enum replacement
type Role = (typeof ROLE)[keyof typeof ROLE];

const cfg = { port: 8080, host: "x" } satisfies Record<string, string | number>; // check, keep literal type

function pick<const T extends readonly string[]>(keys: T) { return keys; } // infers readonly tuple
function fill<T>(items: T[], fallback: NoInfer<T>) {}        // fallback can't widen T (TS 5.4)

type Result<T, E = Error> = { ok: true; value: T } | { ok: false; error: E }; // discriminated union
function assertNever(x: never): never { throw new Error(`unhandled: ${String(x)}`); }
```
- Validate at trust boundaries, infer types from the schema:
  ```ts
  import * as z from "zod";               // Zod 4 is the root export; "zod/mini" for small bundles
  const User = z.object({ id: z.uuid(), email: z.email() });   // v4: top-level string formats
  type User = z.infer<typeof User>;
  const user = User.parse(await req.json());
  ```
  Valibot (tree-shakable, smallest) and ArkType (TS-syntax strings, fastest) are fine; all three implement Standard Schema, so libraries should accept `StandardSchemaV1`, not a specific validator.

### Modern JS idioms
```js
const byType = Object.groupBy(events, e => e.type);            // ES2024
const { promise, resolve, reject } = Promise.withResolvers();
const v = await Promise.try(() => maybeSyncOrAsync());         // ES2025, Node 24+
const firstTen = iter.values().filter(ok).map(f).take(10).toArray(); // iterator helpers, lazy
const common = a.intersection(b);                              // Set methods ES2025
const { default: config } = await import("./cfg.json", { with: { type: "json" } }); // JSON modules
const re = new RegExp(`^${RegExp.escape(userInput)}$`);
const snapshot = structuredClone(state);                       // handles Map/Set/Date/cycles
{ await using conn = await pool.connect(); /* auto [Symbol.asyncDispose] */ }
const all = await Array.fromAsync(asyncIterable);
```
- Temporal for date/time logic (`Temporal.Now.zonedDateTimeISO()`, `Temporal.PlainDate`). Native: Node 26, Chrome/Edge 144+, Firefox 139+. **Not Safari** as of 2026-10: ship `temporal-polyfill` for browsers or keep `Date` + `Intl`.

### Async patterns
```ts
const ac = new AbortController();
const signal = AbortSignal.any([ac.signal, AbortSignal.timeout(5_000)]);
const res = await fetch(url, { signal });                      // TimeoutError vs AbortError
const results = await Promise.allSettled(tasks);               // don't let one failure hide others
for await (const chunk of res.body!.pipeThrough(new TextDecoderStream())) { /* web streams; async-iterable streams only newly Baseline 2026-09 (Safari 27) */ }
```
- Pass `signal` through every async layer you write. Use `node:stream/promises` `pipeline()` for Node streams (handles errors/cleanup).
- Never `await` in a loop when the calls are independent; bound concurrency (`p-limit` or a small semaphore) instead of unbounded `Promise.all` over thousands of items.
- Top-level `await` blocks `require(esm)` of that module (`ERR_REQUIRE_ASYNC_MODULE`); avoid it in library entry points.

### Module interop rules
- ESM imports CJS: default import = `module.exports`; named imports only when statically detectable.
- CJS `require()` of ESM works (sync graphs only). Export `{ X as "module.exports" }` to control what `require` returns.
- Relative ESM specifiers need extensions (`./x.js`, or `./x.ts` with `rewriteRelativeImportExtensions`/type stripping).
- Browser without bundler: `<script type="importmap">` (Baseline 2023).

## Security

- **Install scripts** are the main npm attack path (2025 worm campaigns hit popular packages). npm 12 and pnpm 10+ block dependency lifecycle scripts by default: keep it. Approve explicitly: npm `allowScripts` in package.json (recorded with `npm install-scripts approve`, then `npm rebuild`; `--allow-scripts` is for npx/global installs), `strict-allow-scripts=true`; pnpm `allowBuilds` in `pnpm-workspace.yaml`.
- **Release-age gate** against freshly hijacked versions: pnpm `minimumReleaseAge` (default 1440 min in pnpm 11+); npm `min-release-age=<days>` in `.npmrc`.
- Block git/tarball deps: npm 12 `allow-git`/`allow-remote` default `none`; pnpm 11 `blockExoticSubdeps: true` default. Consider pnpm `trustPolicy: no-downgrade`.
- CI installs: `npm ci` / `pnpm install --frozen-lockfile`; never `npm install` in CI. Review lockfile diffs.
- **Publishing**: trusted publishing (OIDC) from GitHub Actions / GitLab / CircleCI cloud (npm >=11.5.1, Node >=22.14); provenance automatic only for public packages from public repos on GitHub/GitLab; then set package to "require 2FA and disallow tokens". Classic tokens no longer exist; granular write tokens max 90 days.
- `npm audit signatures` verifies registry signatures and provenance attestations.
- Prototype pollution: never deep-merge untrusted JSON into objects; use `Object.create(null)`/`Map`, validate with a schema first, reject `__proto__`/`constructor` keys.
- ReDoS: `RegExp.escape` for user input in patterns; avoid nested quantifiers on untrusted input.
- No `eval`/`new Function`/`vm` as a sandbox. `JSON.parse` + schema, not `eval`.
- Secrets: never in bundles (anything `VITE_*`/`NEXT_PUBLIC_*` is public). `.env` in `.gitignore`.
- `child_process`: `execFile`/`spawn` with arg arrays, never `exec` with interpolated strings.

## Performance & quality

- Type-check time: TS 7 `tsc --noEmit` should be 8-12x faster than 6.0; measure with `tsc --extendedDiagnostics`. `skipLibCheck: true`, `types: [...]` explicit, project references for big monorepos.
- Bundle: measure with `vite build` + `rollup-plugin-visualizer` or Rolldown's analyzer; keep deps ESM so tree-shaking works; `zod/mini` or Valibot when schema code ships to browsers.
- Startup: `import defer * as m from "./heavy.js"` (Stage 3, TS 5.9 syntax; check runtime support, unverified in Node as of 2026-10) or dynamic `import()` for cold paths.
- Prefer iterator helpers over chained array methods on large/lazy data (no intermediate arrays).
- Lint for floating promises (`@typescript-eslint/no-floating-promises`, oxlint type-aware equivalent).

## Testing & tooling

- **Default stack (new project)**: pnpm or npm, TS 7 `tsc --noEmit` for types, Vitest 5 for unit tests, Playwright for E2E, ESLint 10 + typescript-eslint (or Biome 2 / oxlint + oxfmt), Prettier 3 or oxfmt/Biome formatter, tsdown for libraries, Vite 8 for apps.
- Vitest 5: mocks cleared before every test by default, unawaited async assertions fail, reports under `.vitest/`.
- `node --test` (built-in runner, mocks, coverage, watch) for small libs/scripts with zero deps.
- Lint choice: typescript-eslint has the deepest type-aware rules (needs TS 6 API); oxlint 1.x is much faster (Rust) and has type-aware rules via tsgolint (built on the Go compiler; speed-up figures unverified); Biome 2 is lint+format in one binary. Pick one formatter only.
- Monorepo: pnpm/npm workspaces first; add Turborepo or Nx only when task caching is actually needed. Use TS project references or `isolatedDeclarations` so packages build independently.
- CI: `npm ci`, `tsc --noEmit`, lint, `vitest run --coverage`, `npx playwright test`, `npm audit signatures`, `publint`/`attw` (Are The Types Wrong) for libraries.

## Common mistakes in AI-written code

- Emitting `"moduleResolution": "node"` or `baseUrl`: hard error in TS 7.
- Assuming TS 7 has `ts.createProgram`: it has no API; typescript-eslint breaks with only `typescript@7` installed.
- Using `enum`/`namespace`/constructor parameter properties in files run by `node file.ts`: `ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX`.
- Importing types without `import type` under `verbatimModuleSyntax` / type stripping: runtime crash.
- Writing `.eslintrc.json` or `extends: ["plugin:@typescript-eslint/recommended"]` strings: ESLint 10 is flat-config only (`tseslint.configs.recommendedTypeChecked`).
- Zod 3 API in Zod 4 code: `z.string().email()` is deprecated in favour of `z.email()`; `.errors` -> `.issues`; `message` -> `error` param; import root `"zod"` (it is v4; `"zod/v4"` still works permanently).
- `assert { type: "json" }` instead of `with`.
- `using` code shipped to Safari without transpiling; Temporal used in browsers without a polyfill.
- Calling decorators "Stage 3 standard": they were moved to Stage 2.7; TS `experimentalDecorators` is a different, legacy design.
- `JSON.parse(JSON.stringify(x))` for cloning (drops Dates/Maps/undefined): use `structuredClone`.
- `arr[i]` treated as `T` (enable `noUncheckedIndexedAccess`); `obj.prop?: T` assigned `undefined` explicitly (with `exactOptionalPropertyTypes` declare `prop?: T | undefined`).
- Floating promises in event handlers; `forEach(async …)` (does not await).
- `npm install` in CI; `--ignore-scripts` off with blanket `allowScripts: "*"`.
- Inventing package names (slopsquatting): verify every new dependency exists, is maintained, and has provenance before adding.
- `tsup` or `ts-node` recommended for new projects.

## Before you ship

- [ ] `tsc --noEmit` clean on TS 7 (and TS 6 if lint/tools need it); builds without `ignoreDeprecations`.
- [ ] `strict`, `noUncheckedIndexedAccess`, `verbatimModuleSyntax`, `erasableSyntaxOnly` on (or a reason recorded).
- [ ] ESM, `exports` map correct (`publint`, `attw --pack`), `engines.node` set to a supported LTS (>=24 recommended; 22 EOL 2027-04-30).
- [ ] Every external input parsed by a schema; no `any` leaking from it.
- [ ] Lockfile committed; CI uses frozen install; install scripts allow-listed; release-age gate on.
- [ ] Lint (incl. no-floating-promises) and tests green; E2E for user flows.
- [ ] Features used are Baseline for your browser targets, or polyfilled (Temporal, `using`, `Error.isError` lack Safari).
- [ ] Publishing via trusted publishing, tokens disallowed on the package.

## Sources

All accessed 2026-10-09.
- https://github.com/tc39/proposals/blob/main/finished-proposals.md : finished proposals by ES year (2024-2027)
- https://github.com/tc39/proposals (README) : Stage 3 list; decorators and ShadowRealm at Stage 2.7
- https://devblogs.microsoft.com/typescript/ : TS 5.9 (2025-08-01), 6.0 (2026-03-23), 7.0 (2026-07-08) dates
- https://devblogs.microsoft.com/typescript/announcing-typescript-6-0/ : new defaults, deprecations, `#/` imports, es2025 target, Temporal types
- https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/ : removed options, no API until 7.1, `@typescript/typescript6`, perf numbers, JSDoc changes
- https://devblogs.microsoft.com/typescript/announcing-typescript-5-9/ : `import defer`, `--module node20`
- https://github.com/typescript-eslint/typescript-eslint/issues/12518 : TS 7 not supported; side-by-side install
- https://registry.npmjs.org/<pkg> : latest versions and release dates of typescript, eslint, typescript-eslint (peer range), vitest, vite, rolldown, zod, valibot, arktype, pnpm, npm, yarn, bun, tsdown, tsup, biome, oxlint, oxfmt, playwright, jest, prettier, turbo, nx
- https://raw.githubusercontent.com/nodejs/Release/main/schedule.json : Node LTS/EOL dates
- https://nodejs.org/en/about/previous-releases : annual release schedule from Node 27
- https://nodejs.org/en/blog/release/v26.0.0 : Temporal default, upsert, Iterator.concat, removals, module.register deprecation
- https://nodejs.org/api/typescript.html : type stripping stable (24.12/25.2), unsupported syntax, recommended tsconfig
- https://nodejs.org/api/modules.html : require(esm) versions, `module.exports` export name, `module-sync`
- https://unpkg.com/web-features/data.json (web-features 3.42.0) : Baseline status/versions of JS features
- https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Temporal : Temporal not Baseline
- https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/using : `using` semantics, not Baseline
- https://eslint.org/blog/2026/02/eslint-v10.0.0-released/ : eslintrc removed, per-file config lookup
- https://github.com/vitest-dev/vitest/releases/tag/v5.0.0 : Vitest 5 breaking changes
- https://pnpm.io/blog/releases/11.0 and https://pnpm.io/blog/releases/12.0 : pnpm defaults and breaking changes
- https://pnpm.io/supply-chain-security : allowBuilds, minimumReleaseAge, trustPolicy, blockExoticSubdeps
- https://github.com/npm/cli/releases/tag/v12.0.0 : npm 12 breaking changes (scripts blocked, allow-git/remote)
- https://docs.npmjs.com/cli/v12/using-npm/config : allow-scripts, strict-allow-scripts, min-release-age
- https://docs.npmjs.com/trusted-publishers : OIDC trusted publishing requirements
- https://github.blog/changelog/2025-12-09-npm-classic-tokens-revoked-session-based-auth-and-cli-token-management-now-available/ : classic tokens revoked, 90-day granular tokens
- https://zod.dev/v4/versioning : Zod 4 import paths
- https://oxc.rs/blog/2026-02-24-oxfmt-beta : oxfmt beta, Prettier conformance
- https://voidzero.dev/posts/announcing-rolldown-1-0 : Rolldown 1.0
- https://github.com/egoist/tsup (README) : tsup unmaintained, use tsdown
