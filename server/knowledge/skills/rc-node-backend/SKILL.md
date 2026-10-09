---
name: rc-node-backend
description: Current (Oct 2026) Node.js backend practice - release lines and EOL, built-ins that replace old packages (node:test, fetch, --env-file, --watch, TypeScript type stripping, node:sqlite, permission model, require(esm)), Express 5 / Fastify 5 / Hono / NestJS 12, Bun and Deno, shutdown, logging, DB access, queues, OpenTelemetry, npm supply-chain security. Use when writing or reviewing server-side JavaScript/TypeScript, package.json, Dockerfiles for Node, or choosing Node libraries.
---
# Node.js backend (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

## Currency check

Release lines (nodejs.org schedule.json and previous-releases, read 2026-10-09):

| Line | Status on 2026-10-09 | Next step | End of life |
|---|---|---|---|
| **26** | Current (26.11.x) | Active LTS on 2026-10-28 | 2029-04-30 |
| **24** "Krypton" | Active LTS (24.21.x) | Maintenance on 2026-10-20 | 2028-04-30 |
| **22** "Jod" | Maintenance LTS (22.23.x) | security fixes only | 2027-04-30 |
| 20 "Iron" | **EOL** since 2026-04-30 | - | gone |
| 25, 23, 21, 19, 18 and older | **EOL** | - | gone |

- **Default for new services: Node 24 LTS now; move to 26 after 2026-10-28.** Node 22 only for existing apps, plan the jump before April 2027.
- **Schedule changed (announced 2026-03):** from Node 27 on, one major per year (April), every major becomes LTS, a 6-month Alpha channel (`27.0.0-alpha.N`) runs from October, and the major number matches the year (27 in 2027). No more "odd = short-lived". Node 26 is the last line on the old model.
- Shipped in the last ~12 months:
  - 26.0.0 (2026-05-05): `Temporal` on by default, V8 14.6 (`Map.prototype.getOrInsert`, `Iterator.concat`), undici 8, `--experimental-transform-types` removed, `module.register()` runtime-deprecated, `http.Server.prototype.writeHeader()` and the `_stream_*` internal modules removed.
  - TypeScript type stripping stable in 25.2.0 / 24.12.0 (default-on since 23.6.0 / 22.18.0).
  - `require(esm)` stable in 25.4.0 (unflagged since 22.12.0 / 20.19.0).
  - `--env-file` / `--env-file-if-exists` no longer experimental in 24.10.0 / 22.21.0.
  - `node:sqlite` release candidate (1.2) since 25.7.0; classes renamed `DatabaseSync` -> `Database`, `StatementSync` -> `Statement` in **26.11.0 only** (old names stay as deprecated aliases; on 22/24 use the `*Sync` names).
  - `--build-sea` (25.5.0) builds single executable apps without postject; ESM main via `"mainFormat": "module"`.
  - `--allow-net` added to the permission model (25.0.0, active development).
  - Config file `--experimental-config-file` release candidate (26.7.0); `node --run` with no name lists scripts (26.9.0).
  - npm **12.0.0 (2026-07-08)**: dependency install scripts blocked unless allowed, git/URL deps off by default. See Security.
- Library versions on npm (2026-10-09): express 5.3.0 (4.22.x still maintained), fastify 5.12 (v6 in alpha), hono 4.13 (v5 rc), @nestjs/core 12.1, elysia 1.4, pino 10.4, @prisma/client 7.10 (**prisma 8 is RC, and the `latest` dist-tag points at 8.0.0-rc.22: pin `^7`**), drizzle-orm 0.45 (1.0 in RC), kysely 0.29, pg 8.23, bullmq 6.3, zod 4.6, vitest 5.0, undici 8.11, OpenTelemetry API 1.9 / SDK 2.x (`sdk-trace-base` 2.12; `@opentelemetry/sdk-node` is still 0.223), TypeScript 7.0 (native compiler). Bun 1.4.2, Deno 2.9.x.

## What changed / stop doing

| Stop | Use instead | Since |
|---|---|---|
| Node 18/20 in `engines`, Dockerfiles, CI matrices | `"engines": {"node": ">=22.12"}`; build on 24, test 22/24/26 | 20 EOL 2026-04-30 |
| `request` (deprecated), `node-fetch`, axios as reflex | global `fetch` (undici); `undici` package for pools/Agent/mocking | fetch stable in 21.0 |
| `ws` client only for outgoing sockets | global `WebSocket` client (server side still needs `ws` or framework) | 22.4 |
| `dotenv` | `node --env-file=.env` / `--env-file-if-exists`, `process.loadEnvFile()`, `util.parseEnv()` | stable 24.10 / 22.21 |
| `nodemon` | `node --watch` (`--watch-path` to scope) | stable 22.0 |
| `ts-node`, `tsx`/`ts-node` in production, `tsc` build just to run | `node app.ts` (type stripping); `tsc --noEmit` for type checking | stable 24.12 / 25.2 |
| `--experimental-transform-types` for enums/namespaces | erasable-only TS (`erasableSyntaxOnly`), `as const` objects instead of `enum` | removed in 26.0 |
| `--experimental-strip-types`, `--experimental-require-module`, `--experimental-permission`, `--experimental-fetch` | no flag needed (flags are `--no-strip-types`, `--permission`) | 22.x-25.x |
| Mocha/Chai/Sinon/Jest for a small service | `node --test` + `node:assert` + `mock` (or Vitest 5 for larger TS apps) | node:test stable 20 |
| `nyc`/istanbul | `--experimental-test-coverage` (or `c8`, Vitest coverage) | 20.1 (still experimental) |
| `body-parser` package with Express | `express.json()` / `express.urlencoded()` (built on body-parser 2) | Express 4.16 / 5 |
| Express 4 `app.get('/*')`, `/:id?`, `res.send(200)`, `req.param()` | `/*splat`, `/:file{.:ext}`, `res.sendStatus(200)`, `req.params` | Express 5.0 |
| `.catch(next)` wrappers / express-async-errors | Express 5 forwards rejected promises to the error handler | Express 5.0 |
| CommonJS-only new code, `__dirname` | ESM (`"type": "module"`), `import.meta.dirname` / `import.meta.filename` | 20.11 |
| Dual-package build to serve CJS users | ship ESM only; CJS consumers `require()` it (no top-level await) | require(esm) 22.12 / 20.19 |
| `module.register()` / `--loader` hooks | `module.registerHooks()` (sync, in-thread; release candidate since 25.4 / 24.13.1) | runtime-deprecated 26.0 |
| `async_hooks.createHook` for request context | `AsyncLocalStorage` (AsyncContextFrame-backed, faster) | default 24.0 |
| `better-sqlite3`/`sqlite3` for small local storage | `node:sqlite` (sync API, RC) when no native build is wanted | unflagged 22.13 |
| `pkg` / `nexe` / postject | Single executable apps: `node --build-sea sea-config.json` | 25.5 |
| `forever`, pm2 cluster mode inside containers | one process per container; the orchestrator restarts and scales | - |
| `cluster` to use more cores in k8s | more replicas; `worker_threads` for CPU-heavy work | - |
| Long-lived npm tokens (`NPM_TOKEN` automation/classic) | npm trusted publishing (OIDC) from CI; classic tokens were revoked 2025-12-09 | 2025-12 |
| `npm install` letting every postinstall run | npm 12 `allowScripts` in package.json + `npm install-scripts approve` | npm 12.0 |
| Joi as NestJS config schema | any Standard Schema (Zod 4, Valibot, ArkType) | NestJS 12 |
| `prisma-client-js` generator + Rust engine | Prisma 7 `prisma-client` generator + driver adapter (`@prisma/adapter-pg`) | Prisma 7 |

## Do this

**Project setup (ESM + TS without a build step):**
```jsonc
// package.json
{ "type": "module", "engines": { "node": ">=24" },
  "imports": { "#lib/*": "./src/lib/*" },          // replaces tsconfig "paths"
  "scripts": {
    "dev": "node --watch --env-file-if-exists=.env src/server.ts",
    "start": "node src/server.ts",
    "test": "node --test",
    "check": "tsc --noEmit" } }
```
```jsonc
// tsconfig.json (what nodejs.org recommends for type stripping)
{ "compilerOptions": { "noEmit": true, "target": "esnext", "module": "nodenext",
  "rewriteRelativeImportExtensions": true, "erasableSyntaxOnly": true,
  "verbatimModuleSyntax": true, "strict": true } }
```
- Imports must carry the extension: `import { db } from './db.ts'`. `tsconfig.json` is ignored at runtime (no paths, no decorators, no `.tsx`), and `.ts` files under `node_modules` are not stripped: publish JS + `.d.ts`.
- No `enum`, `namespace` with code, or constructor parameter properties (`constructor(private x)`) - they throw `ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX`. Use `import type` for types.
- NestJS still needs decorators: keep its compiler (SWC/tsc) build; type stripping is for plain Node services.

**HTTP server with graceful shutdown (any framework):**
```ts
import { createServer } from 'node:http';
const server = createServer(app).listen(Number(process.env.PORT ?? 3000));
server.headersTimeout = 20_000; server.requestTimeout = 30_000;   // headers <= request; defaults 60 s / 300 s
let closing = false;
async function shutdown(sig: string) {
  if (closing) return; closing = true;
  log.info({ sig }, 'shutting down');                // readiness probe should now fail
  server.close();                                    // stop accepting, finish in-flight
  server.closeIdleConnections();                     // drop idle keep-alive sockets
  const t = setTimeout(() => { server.closeAllConnections(); process.exit(1); }, 10_000);
  t.unref();
  await Promise.allSettled([db.end(), queue.close()]);
  // no process.exit(): let the loop drain; keeps exitCode 1 set by the rejection handler
}
process.once('SIGTERM', shutdown); process.once('SIGINT', shutdown);
process.on('unhandledRejection', (e) => { log.fatal(e); process.exitCode = 1; shutdown('unhandledRejection'); });
```
- Containers: run `node` as PID 1 directly (`CMD ["node","src/server.ts"]`), not `npm start` (npm does not forward signals reliably). Use `--init`/tini only if you spawn children. Kubernetes `terminationGracePeriodSeconds` > your shutdown timeout.
- Memory: size the heap to the container limit, not host RAM: `--max-old-space-size=<MiB>` (~75% of the limit). `--max-old-space-size-percentage=75` is documented as a share of "available system memory"; whether it reads the cgroup limit is unverified, so check `v8.getHeapStatistics()` in the container; add `--heapsnapshot-near-heap-limit=1` while investigating leaks.
- Scale with replicas; use `worker_threads` (or a pool like piscina) for CPU-bound work (hashing, image work, big JSON), never `cluster` inside a single-CPU container.

**Config and validation:** read `process.env` once at boot, validate with a schema, freeze, export. Fail fast on missing values.
```ts
import { z } from 'zod';
export const env = Object.freeze(z.object({
  DATABASE_URL: z.url(), PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(['production', 'development', 'test']).default('production'),
}).parse(process.env));
```
Validate every request body/query/params at the edge (Zod 4, Valibot, ArkType, TypeBox with Fastify). Prefer libraries that implement Standard Schema so frameworks can share them.

**Frameworks (pick one, do not mix):**
- **Express 5.x** - fine for existing teams; async errors handled; `req.body` is `undefined` until a parser runs; query parser is "simple" by default; `express.static` ignores dotfiles. Migration codemod: `npx codemod@latest @expressjs/v5-migration-recipe`.
- **Fastify 5** - default for new performance-sensitive JSON APIs: schema validation + fast serialization, Node 20+ only (use 22+), plugins `@fastify/helmet`, `@fastify/rate-limit`, `@fastify/cors`.
- **Hono 4** - Web-standard `Request`/`Response`; same code on Node (`@hono/node-server`), Bun, Deno, Workers. Good for edge-portable services. v5 is in RC.
- **NestJS 12** - large teams wanting DI/modules. Packages are ESM now (CJS apps still work via require(esm)); runtime needs Node 20.19+/22.12+, CLI needs 22.22.3+/24.15+/26; Vitest default for ESM projects (Jest stays for CJS), Rspack default bundler for monorepos (webpack deprecated), Standard Schema config validation.
- **Elysia 1.4** - Bun-first; choose only if you are committed to Bun.

**Runtimes, honestly:**
- **Node 24/26** - default for production: LTS dates, widest library + APM support, the security process the rest of the ecosystem tracks.
- **Bun 1.4** - fast installs/tests/startup, built-in SQL/Redis/S3 clients; reasonable for tooling, scripts, CI speed, and greenfield services that test on Bun. Check native addons, `node:` edge cases and your APM vendor first. (Bun was acquired by Anthropic, announced 2025-12-02.)
- **Deno 2.9** - npm + `package.json` compatible, permission sandbox by default, built-in fmt/lint/test, Deno Deploy. Reasonable when you want secure-by-default scripts or Deno Deploy. Do not assume `Deno.*` APIs exist on Node.
- Write to Web standards (`fetch`, `Request`, `Response`, Web Streams, `crypto.subtle`) to keep the option open.

**Logging:** pino 10 (JSON to stdout, one line per event), `pino-http` / Fastify's built-in logger, request id via `AsyncLocalStorage` or the framework's child logger. Redact secrets: `pino({ redact: ['req.headers.authorization', 'req.headers.cookie', '*.password'] })`. No `console.log` in request paths, no log files inside containers, `pino-pretty` only in dev.

**Database access:**
- `pg` 8 (Pool, parameterized `$1`) or `postgres` (porsager) for raw SQL; Kysely for typed SQL builder; Drizzle 0.45 for schema-in-TS + migrations (1.0 is RC: pin); Prisma 7 for schema-first ORM (new `prisma-client` generator, driver adapters, `prisma.config.ts`).
- Always: one pool per process, sized `max` = DB connections / replicas; statement timeout; transactions passed explicitly; migrations run as a separate deploy step, never at app boot in N replicas.
- `node:sqlite` for embedded/local data: `const db = new DatabaseSync('app.db')` on 22/24 (`Database` on 26.11+); use `db.prepare(...).run(...)` or `createTagStore()` tagged templates, never string concatenation. It is synchronous: keep queries small or run in a worker.

**Queues:** BullMQ 6 on Redis/Valkey (`maxRetriesPerRequest: null` on the worker connection, idempotent jobs, `removeOnComplete`, explicit `attempts` + backoff, `await worker.close()` on SIGTERM). For Postgres-only stacks, pg-boss avoids a Redis dependency.

**Observability:** OpenTelemetry. Load the SDK before app code: `node --import ./otel.ts src/server.ts`; `@opentelemetry/sdk-node` + `@opentelemetry/auto-instrumentations-node` (instruments http, undici/fetch, pg, ioredis, express, fastify), OTLP exporter; correlate pino logs with trace ids (`@opentelemetry/instrumentation-pino`). The test runner can also emit OTel spans (26.1+).

**Built-ins worth knowing:** `node:test` (`describe/it`, `mock.fn`, `mock.timers`, `mock.module` with `--experimental-test-module-mocks`, snapshots, `--test-shard=i/n`, `--test-rerun-failures`, global setup 24+), `AbortSignal.timeout()`, `stream/promises.pipeline`, `Readable.fromWeb/toWeb`, `crypto.randomUUID()`, `crypto.subtle`, `util.parseArgs`, `util.styleText`, `fs.glob`, `node --run <script>`, `NODE_COMPILE_CACHE` / `module.enableCompileCache()` for startup, `diagnostics_channel` and `perf_hooks.monitorEventLoopDelay`.

## Security

- **Supply chain is the #1 Node risk in 2026.** Worm campaigns hit axios (2026-03-31, 1.14.1 / 0.30.4 shipped a RAT via a postinstall dep), 42 `@tanstack/*` packages (2026-05-11, **with valid provenance** from a hijacked CI), and hundreds of packages in later worm waves. Defenses:
  - npm 12: keep dependency install scripts blocked; approve the few that need them (`allowScripts` in package.json, `npm install-scripts approve`). On older npm: `ignore-scripts=true` in `.npmrc` + explicit `npm rebuild <pkg>`. pnpm 10.0+ also blocks dependency build scripts by default; allow-list them with `allowBuilds` in `pnpm-workspace.yaml` (pnpm 11 replaced `onlyBuiltDependencies`).
  - Delay new versions: `min-release-age=7` in `.npmrc` (npm) or `minimumReleaseAge` (pnpm, minutes; default 1440 since pnpm 11). Most malicious versions are pulled within hours.
  - Commit the lockfile; CI uses `npm ci`; keep `allow-git=none` / `allow-remote=none` (npm 12 defaults).
  - Run `npm audit signatures` in CI (verifies registry signatures and provenance attestations). Provenance proves which pipeline built it, not that the pipeline was honest.
  - Publishing: trusted publishing (OIDC) from CI, no long-lived tokens, 2FA on accounts, avoid `pull_request_target` workflows that touch caches or secrets.
  - Dependabot/Renovate with a cooldown, not instant merges.
- **Prototype pollution:** never deep-merge untrusted JSON into objects; parse into validated schemas; use `Object.create(null)` / `Map` for dictionaries; reject `__proto__`, `constructor`, `prototype` keys. Consider `--disable-proto=delete`.
- **ReDoS:** no user-controlled regex; audit nested quantifiers (`(a+)+`); cap input length before matching; Express 5's path-to-regexp v8 removed the regex-in-path features that caused CVEs.
- **SSRF:** for user-supplied URLs, allowlist hosts/schemes, resolve DNS and block private/link-local/metadata ranges (169.254.169.254, ::1, 10/8, 172.16/12, 192.168/16, fc00::/7), disable redirects or re-check each hop, set timeouts (`AbortSignal.timeout`).
- **Path traversal:** `path.resolve(base, input)` then check it starts with `base + path.sep`; reject NUL bytes; serve static files only via `express.static`/`@fastify/static` roots.
- **child_process:** `execFile`/`spawn` with an argument array and `shell: false`; never `exec` with interpolated input; pass `--` before user args to git-like tools. Windows: `spawn` of `.bat/.cmd` needs `shell: true` since the 2024 CVE fix - avoid with untrusted args.
- **HTTP hardening:** `helmet` 8 (Express) / `@fastify/helmet`; rate limiting (`express-rate-limit` 8 with a Redis store, `@fastify/rate-limit`); body size limits (`express.json({ limit: '100kb' })`); `app.set('trust proxy', 1)` only behind a known proxy; `server.requestTimeout` set; CORS allowlist, never `*` with credentials.
- **Permission model** (`node --permission --allow-fs-read=./data --allow-net ...`): a seat belt for trusted code, not a sandbox against malicious deps (symlinks and already-open fds escape it). `--allow-net` is still "active development".
- **Secrets:** env vars or a secret manager, never in the image or repo; `.env` only for local dev; redact in logs; `crypto.timingSafeEqual` for token compares; passwords with `crypto.scrypt` or the `argon2` package (no Node built-in argon2 verified).
- Keep Node patched: security releases land for all supported lines together; subscribe to nodejs.org security blog.

## Performance & quality

- Measure event-loop delay: `perf_hooks.monitorEventLoopDelay()`; alert when p99 > ~100 ms. Export it as a metric (OTel runtime-node instrumentation).
- Profile: `node --cpu-prof`, `--heap-prof`, `--inspect` + Chrome DevTools, `--heapsnapshot-signal=SIGUSR2` in prod; flame graphs with 0x.
- HTTP client: one `undici.Agent`/pool per upstream with `connections`, `keepAliveTimeout`, timeouts; never create a client per request.
- Streams: use `pipeline()` (handles backpressure and errors); never `.pipe()` without error handling, never buffer whole uploads (`req.on('data')` concat) - stream to disk/S3 with size limits.
- Avoid sync APIs (`readFileSync`, `crypto.pbkdf2Sync`, `node:sqlite` big queries, `JSON.parse` on MB payloads) in request paths.
- Startup: `NODE_COMPILE_CACHE`, avoid giant barrel imports, lazy-load rarely used modules.
- Load test (autocannon, k6) before and after changes; compare p50/p99 latency and RSS, not just req/s.

## Testing & tooling

- Runner: `node --test` (built-in; coverage with `--experimental-test-coverage`, `--test-coverage-lines=80` thresholds) or **Vitest 5** for TS-heavy apps. Jest 30 still works but needs ESM care.
- HTTP tests: `fastify.inject()`, `app.request()` (Hono), `supertest` (Express); mock outbound HTTP with undici `MockAgent` (`setGlobalDispatcher`), which also covers global `fetch`.
- DB tests: real Postgres in Testcontainers or a CI service, not SQLite stand-ins.
- Lint/format: ESLint 10 (2026-02-06; `.eslintrc*` removed, flat config only) + typescript-eslint, or Biome 2 / oxlint for speed; Prettier or Biome format.
- Types: `tsc --noEmit`. TypeScript 7.0 (native compiler, 2026-07-08 on npm) is current; check that your editor/lint plugins support it before switching.
- CI: matrix on supported LTS lines (22, 24, 26), `npm ci`, `npm audit signatures`, tests with `--test-shard`, build the container, scan it (Trivy/Grype).
- Package manager: npm 12, pnpm 10+, or Bun's installer; record it in `packageManager`. Corepack is no longer distributed with Node from v25 (per the Node 24 corepack docs): install pnpm/yarn explicitly (`npm i -g pnpm` or the official installer) instead of relying on `corepack enable`.

## Common mistakes in AI-written code

- Targeting Node 18/20 or writing `node:16-alpine` in Dockerfiles. Use `node:24-slim` (or 26 after 2026-10-28) pinned by digest.
- `require('node-fetch')`, `import axios` for one GET, `request(...)`: use `fetch`.
- `import 'dotenv/config'` in new code; `nodemon`/`ts-node` in scripts.
- `import x from './x'` without extension in ESM/TS-stripping code (fails); `tsconfig` `paths` aliases at runtime (ignored).
- TypeScript `enum` / parameter properties in code run directly by Node; `--experimental-transform-types` (removed in 26).
- Express 4 patterns in Express 5: `app.get('*')` (throws), `res.json(obj, 404)`, `req.param()`, assuming `req.body` is `{}`, assigning to `req.query`.
- `new DatabaseSync` vs `new Database` mixups for `node:sqlite`: check the Node version; it is synchronous, not promise-based.
- Missing `await` on `server.close()` patterns, `process.exit()` right after logging (drops logs), handlers on `SIGKILL`.
- `cluster.fork()` per CPU inside Kubernetes; pm2 inside Docker.
- `JSON.parse(req.body)` without size limits; `Object.assign({}, userInput)` deep merges; `new RegExp(userInput)`.
- `exec(\`git clone ${url}\`)`: shell injection.
- `fetch` without timeout (default is no overall timeout; pass `signal: AbortSignal.timeout(ms)`).
- Prisma 7: still using `prisma-client-js`, no `output`, no driver adapter, `url` in `schema.prisma` (moved to `prisma.config.ts`), expecting `.env` to auto-load (it no longer does: use `node --env-file` or `process.loadEnvFile()`), client middleware (removed: use extensions). And `npm i prisma@latest` installs the 8.0 RC today.
- Using `module.register()` / `--loader` for hooks (deprecated) instead of `module.registerHooks()`.
- `npm install --unsafe-perm`, `--ignore-scripts=false` advice, or `--dangerously-allow-all-scripts` to "fix" npm 12 install warnings.

## Before you ship

- [ ] Node 24 LTS (or 26 after 2026-10-28) in Docker, CI and `engines`; no EOL line anywhere.
- [ ] ESM, extensions on imports, `tsc --noEmit` clean, no non-erasable TS when run with type stripping.
- [ ] Env validated at boot; no secrets in image/logs; pino redaction set.
- [ ] Input validation on every route; body size limits; helmet + rate limiting; CORS allowlist.
- [ ] SIGTERM handled: stop accepting, drain, close DB/queue, exit within the grace period; `node` is PID 1.
- [ ] Heap sized to container memory; event-loop delay and RSS exported; OTel traces flowing.
- [ ] Lockfile committed, `npm ci`, install scripts allowlisted, `min-release-age` set, `npm audit signatures` passes.
- [ ] Timeouts on every outbound call (fetch, DB, Redis); retries with backoff only on idempotent operations.
- [ ] Tests run on all supported LTS lines; migrations run once, outside app boot.

## Sources

All accessed 2026-10-09.
- https://nodejs.org/en/about/previous-releases - release lines, latest versions, new annual schedule summary
- https://raw.githubusercontent.com/nodejs/Release/main/schedule.json - LTS/maintenance/EOL dates for 20-27
- https://nodejs.org/en/blog/announcements/evolving-the-nodejs-release-schedule - annual majors from 27, Alpha channel, all-LTS
- https://nodejs.org/en/blog/release/v26.0.0 - Temporal default, V8 14.6, undici 8, removals/deprecations
- https://nodejs.org/api/typescript.html - type stripping stable 25.2/24.12, transform-types removed 26.0, tsconfig, limits
- https://github.com/nodejs/node/tree/v26.x/doc/api (modules.md, sqlite.md, deprecations.md, cli.md, globals.md, test.md, permissions.md, single-executable-applications.md, async_context.md, module.md) - require(esm) history, node:sqlite RC + DEP0210/0211 rename in 26.11, --env-file stable 24.10/22.21, --watch stable 22.0, --allow-net, --build-sea, config file RC, --max-old-space-size-percentage, WebSocket stable 22.4, EventSource experimental, test runner features, AsyncContextFrame default 24.0, registerHooks RC; corepack.md absent on v26.x, present on v24.x
- https://registry.npmjs.org/<package> - every library version and dist-tag listed above (incl. prisma `latest` = 8.0.0-rc.22, drizzle 1.0 RC)
- https://raw.githubusercontent.com/npm/cli/latest/CHANGELOG.md - npm 12.0.0 (2026-07-08) breaking changes: install scripts blocked, allow-git/allow-remote none, Node ^22.22.2/^24.15/>=26
- https://github.com/npm/cli (workspaces/config definitions) - `min-release-age`, `allowScripts`, `--dangerously-allow-all-scripts`
- https://github.blog/changelog/2025-09-29-strengthening-npm-security-important-changes-to-authentication-and-token-management/ and https://github.com/orgs/community/discussions/179562 - classic tokens revoked 2025-12-09, trusted publishing, 90-day granular tokens
- https://github.com/advisories/GHSA-fw8c-xr5c-95f9 (via advisories.gitlab.com mirror) - axios 1.14.1/0.30.4 malicious (2026-03-31)
- https://tanstack.com/blog/npm-supply-chain-compromise-postmortem - TanStack compromise 2026-05-11, valid provenance on malicious versions
- https://expressjs.com/en/guide/migrating-5.html - Express 5 breaking changes, codemod
- https://expressjs.com/en/support/ - Express 4 and 5 both supported
- https://github.com/expressjs/express/releases - Express 5.3.0 (2026-10-09), 5.2.1 revert note
- https://fastify.dev/docs/latest/Reference/LTS/ - Fastify 5 supports Node 20/22
- https://docs.nestjs.com/migration-guide - NestJS 12: ESM packages, Node requirements, Vitest, Rspack, Standard Schema
- https://www.prisma.io/docs/orm/more/upgrade-guides/upgrading-versions/upgrading-to-prisma-7 - Prisma 7 generator, adapters, prisma.config.ts, no env auto-load
- https://pnpm.io/blog/releases/11.0 - pnpm 11 defaults (minimumReleaseAge 1440, allowBuilds)
- https://bun.com/blog - Bun 1.4 (2026-08-20), 1.4.2 (2026-09-05), acquisition by Anthropic (2025-12-02)
- https://github.com/denoland/deno/releases - Deno 2.9.7 (2026-09-17)
- https://eslint.org/blog/2026/02/eslint-v10.0.0-released/ - eslintrc removed in ESLint 10
