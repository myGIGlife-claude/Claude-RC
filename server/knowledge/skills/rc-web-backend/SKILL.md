---
name: rc-web-backend
description: Language-agnostic back-end engineering as of 2026-10 - HTTP/REST API design (OpenAPI 3.2, problem+json, pagination, idempotency, QUERY method, versioning/deprecation), GraphQL/gRPC/webhooks, authN/authZ (OAuth 2.1, OIDC, PKCE, passkeys, sessions vs JWT, RBAC/ReBAC), PostgreSQL 17/18/19, SQLite, pgvector, migrations, caching (Redis/Valkey), queues/outbox, rate limiting, S3 uploads, email deliverability, OpenTelemetry, reliability, Docker/Kubernetes/serverless deploys, backups, OWASP API Top 10 and ASVS 5. Use when designing or reviewing an API, auth flow, schema, migration, Dockerfile, deploy pipeline or any server-side service.
---
# Web back-end engineering (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Stack-agnostic. For language specifics see rc-node-backend, rc-python, rc-go, rc-java, rc-php. Postgres/auth detail tables are in
`references/data-and-auth.md`.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| PostgreSQL | **18** (18.6) GA 2025-09-25, EOL 2030-11-14; 17 EOL 2029-11; 14 EOL **2026-11-12**; 13 and older EOL | 19 is at Beta 4 (2026-09-24), GA expected Oct 2026 (date unverified). Start new projects on 18. |
| SQLite | 3.54.0 (2026-10-09) | |
| pgvector | 0.8.7 (2026-10-01, fixes IVFFlat buffer overflow CVE) | 0.8.0 (2024-10) added iterative index scans. |
| Redis | 8.x, licensed RSALv2 / SSPLv1 / **AGPLv3** (AGPL option added with Redis 8, 2025-05) | 7.4 to 7.x were source-available only. |
| Valkey | 9.0 (GA 2025-10-21), latest 9.0.6 (2026-09-01), BSD-3 | Linux Foundation fork of Redis 7.2.4. |
| Kubernetes | 1.37 / 1.36 / 1.35 maintained; 1.34 EOL 2026-10-27; 1.33 and older EOL | **ingress-nginx retired 2026-03** (no more security fixes); use Gateway API. |
| OpenAPI | **3.2** (3.2.0 2025-09-19, 3.2.1 2026-09-10); 3.1.x still widely tooled | 3.2 adds `query` method, streaming `itemSchema` (SSE, JSONL), `in: querystring`, hierarchical tags. |
| GraphQL spec | **September 2025** edition (first since 2021) | `@oneOf` input objects, schema coordinates. |
| HTTP QUERY method | **RFC 10008** (2026, Proposed Standard) | Safe+idempotent request with a body; cacheable; needs CORS preflight. |
| OAuth 2.1 | draft-ietf-oauth-v2-1-**16** (2026-09-03), not an RFC yet | Normative today: **RFC 9700** OAuth 2.0 Security BCP (BCP 240, 2025-01). |
| OWASP API Security Top 10 | **2023** edition is still the latest | |
| OWASP ASVS | **5.0.0** (2025-05-30), 17 chapters, ~350 reqs, IDs renumbered | New chapters: Self-contained Tokens (V9), OAuth/OIDC (V10), WebRTC (V17). |
| NIST SP 800-63B | **Rev 4** final (2025-08) | 15-char min for password-only, no composition rules, no forced rotation, syncable passkeys OK up to AAL2. |
| Docker Hardened Images | Free, Apache-2.0 since 2025-12-17, registry `dhi.io` | Debian/Alpine based, SBOM + SLSA L3 provenance. |
| Authorization engines | OpenFGA = CNCF Incubating (announced 2025-11-11); Cedar = CNCF Sandbox (announced 2025-12-15) | |
| Email | Gmail/Yahoo bulk rules since 2024-02; **Outlook.com since 2025-05-05** (junk, then 550 5.7.515 reject) | >5,000 msgs/day: SPF+DKIM+DMARC, alignment, RFC 8058 one-click unsubscribe, spam rate <0.3%. |
| IETF drafts to know | `Idempotency-Key` header (draft-07, expired 2026-04, de-facto standard); `RateLimit`/`RateLimit-Policy` (draft-11, 2026-05) | Use them, but they are not RFCs. |

## What changed / stop doing

| Stop (old) | Do instead (new) | Since |
|---|---|---|
| MD5/SHA-1/SHA-256 (even salted) for passwords | Argon2id (m=19 MiB, t=2, p=1 min); bcrypt cost>=10 only for legacy (72-byte limit); PBKDF2-SHA256 600k if FIPS | OWASP cheat sheet, current |
| Postgres `md5` auth | `scram-sha-256` (md5 deprecated in PG 18 with warnings when setting md5 passwords; PG 19 also warns after md5 login) | PG 18 (2025-09) |
| Implicit grant, Resource Owner Password grant | Authorization Code + PKCE for every client; exact redirect URI match | RFC 9700 (2025), OAuth 2.1 draft |
| JWT access/refresh tokens in `localStorage` | BFF pattern: server-side session, `HttpOnly; Secure; SameSite=Lax` cookie, `__Host-` prefix | browser-apps BCP |
| Long-lived JWT as a session ("stateless logout") | Opaque server sessions, or short JWT (5-15 min) + rotating refresh tokens with reuse detection | RFC 9700 |
| Forced 90-day password rotation, composition rules, SMS as strong MFA | Length >=15 (or >=8 with MFA), breached-password blocklist, passkeys/WebAuthn, TOTP fallback | NIST 800-63B-4 (2025) |
| Ad-hoc error JSON per endpoint | `application/problem+json` (**RFC 9457**, obsoletes 7807) | 2023-07 |
| POST for complex searches | `QUERY` method (RFC 10008) where clients/proxies support it; else POST `/search` documented as safe | 2026 |
| OFFSET pagination on big tables | Keyset/cursor pagination (opaque cursor) | timeless, still common in AI code |
| `Sunset` only / changelog-only deprecation | `Deprecation` header (**RFC 9745**, 2025-03) + `Sunset` (RFC 8594) + `Link rel="deprecation"` | 2025 |
| `X-RateLimit-*` ad-hoc headers | `RateLimit-Policy` / `RateLimit` (IETF draft) + `Retry-After` on 429 | draft-11 2026 |
| SOAP/WSDL for new public APIs | REST+OpenAPI, or gRPC/Connect internally, GraphQL for client-driven aggregation | - |
| `uuid_generate_v4()` / random UUID PKs on hot tables | `uuidv7()` built into PG 18 (time-ordered, index-friendly), or bigint identity | PG 18 |
| `SERIAL` | `GENERATED ALWAYS AS IDENTITY` | PG 10+ |
| `VACUUM FULL` / pg_repack to shrink tables | `REPACK ... CONCURRENTLY` (PG 19); pg_repack until then | PG 19 |
| Big-bang migrations in the deploy step | Expand/contract migrations, `CREATE INDEX CONCURRENTLY`, `NOT VALID` + `VALIDATE` | - |
| Assuming Redis is BSD | Redis 8 = RSAL/SSPL/AGPL; Valkey = BSD; check what your cloud runs | 2024-03 / 2025-05 |
| ingress-nginx | Gateway API (`ingress2gateway` to convert) or a maintained controller | retired 2026-03 |
| `FROM node:latest`, running as root, fat images | Pinned digest, multi-stage, distroless/DHI/Chainguard, `USER` non-root | - |
| Secrets in committed `.env`, baked into images, `ARG` | Secret manager (Vault/OpenBao, cloud SM, SOPS), BuildKit `--mount=type=secret`, OIDC CI-to-cloud | - |
| Sticky sessions | Stateless app nodes; session in DB/Valkey | - |
| `List-Unsubscribe: mailto:` only | Add `List-Unsubscribe-Post: List-Unsubscribe=One-Click` + HTTPS URL, honour in 48 h | Gmail/Yahoo 2024-02 |
| Logs-only observability, vendor agents | OpenTelemetry SDK + Collector (OTLP); traces/metrics stable; logs stable only in some SDKs | - |
| Retry immediately in a loop | Exponential backoff + full jitter, retry budget, only idempotent ops | - |

## Do this

### API design (REST)
- Resources are nouns, plural: `GET /v1/orders/{id}`. Methods carry semantics: GET/HEAD/QUERY safe; PUT/DELETE idempotent; POST/PATCH not.
- Status codes: 201 + `Location` on create, 202 for async (return a status URL), 204 no body, 400 malformed, 401 no/invalid
  credentials (+ `WWW-Authenticate`), 403 authenticated but forbidden, 404 also for "exists but not yours" (avoid enumeration), 409 conflict,
  412 failed precondition, 415, 422 validation, 429 + `Retry-After`.
- Errors: RFC 9457 problem details. `type` is a stable URI you document; never leak stack traces or SQL.
  ```json
  {"type":"https://api.example.com/problems/insufficient-funds","title":"Insufficient funds",
   "status":409,"detail":"Balance 30, cost 50","instance":"/v1/payments/abc","traceId":"4bf92f..."}
  ```
- Contract-first: write OpenAPI 3.1/3.2, lint it (Spectral/Redocly), generate server stubs/clients, run contract tests. 3.1+ uses full JSON Schema 2020-12 (`type: [string, "null"]`, not `nullable`).
- Pagination: `?limit=50&cursor=<opaque>`; response `{ "data": [...], "next_cursor": "..." }` or `Link: <...>; rel="next"`. Cap `limit` server-side. Cursor = base64 of the last sort key(s), with a tie-breaker (`created_at, id`).
- Filtering/sorting: allow-list fields; never splice user input into `ORDER BY`. JSON:API is fine if you adopt it wholesale; do not half-implement it.
- Concurrency: return `ETag`, require `If-Match` on PUT/PATCH/DELETE of contended resources (412 on mismatch).
- Idempotency for POST: client sends `Idempotency-Key: <uuid>`. Server stores (key, principal, request hash, response) for ~24 h in the same transaction as the effect; replay returns the stored response; same key + different body = 422; in-flight duplicate = 409.
- Versioning: one major version in the path (`/v1`) or a date header (`API-Version: 2026-10-01`, Stripe style). Additive changes need no new version. Deprecate with `Deprecation` + `Sunset` + docs link, and log callers of deprecated endpoints.
- Long work: 202 + job resource, or webhooks; SSE for one-way streams (now describable in OpenAPI 3.2 via `itemSchema`).
- Timestamps RFC 3339 UTC; money as integer minor units + currency; IDs as strings.

### GraphQL, gRPC, webhooks
- GraphQL: persisted/trusted documents in production (block arbitrary queries), depth + cost limits, disable introspection publicly, per-field authZ in resolvers, DataLoader to kill N+1, `@oneOf` for "exactly one of" inputs. Errors in `errors[]` with HTTP 200 is legacy; GraphQL-over-HTTP spec uses `application/graphql-response+json` with proper status codes.
- gRPC: service-to-service, protobuf in a Buf-managed repo with `buf breaking` in CI. Connect protocol (connectrpc.com) when browsers or plain HTTP/1.1 clients need the same API. Always set deadlines.
- Webhooks (sending): sign with HMAC-SHA256 over `id.timestamp.body` (Standard Webhooks spec), include `webhook-id`, `webhook-timestamp`; retry with backoff for ~3 days; at-least-once, so receivers dedupe on id. Block SSRF: resolve the target, deny private/link-local/metadata IPs, re-check after redirects (or disable redirects).
- Webhooks (receiving): verify signature with constant-time compare on the RAW body, reject timestamps >5 min old, ack fast (2xx) and process from a queue.
- Async APIs: describe event payloads with AsyncAPI 3.x and CloudEvents envelope if consumers are external.

### Authentication
- Prefer an IdP (OIDC) over rolling your own. If you build it: passkeys (WebAuthn) first, password + TOTP fallback, recovery codes; rate-limit and lock by account+IP; breached-password check (k-anonymity HIBP API).
- Browser apps: **BFF** - the server does the OAuth code+PKCE flow, keeps tokens server-side, gives the browser a session cookie:
  `Set-Cookie: __Host-sid=<random 128+ bits>; Path=/; Secure; HttpOnly; SameSite=Lax`. Rotate the session id on login and privilege change; idle + absolute timeouts; server-side revocation.
- CSRF: `SameSite=Lax` + check `Origin`/`Sec-Fetch-Site` on state-changing requests (or a synchronizer token). CORS is not CSRF protection.
- APIs/mobile: access tokens short (5-15 min), audience-restricted; refresh tokens rotate on every use and a reused one revokes the family. Consider sender-constraining (DPoP RFC 9449 or mTLS RFC 8705). Use PAR (RFC 9126) for high-assurance flows.
- JWT validation: pin `alg` allow-list (never `none`, never let the token choose HS vs RS), verify `iss`, `aud`, `exp`, `nbf`, fetch keys from JWKS with caching and `kid` lookup. Use a maintained library.
- Service-to-service: workload identity (SPIFFE/cloud IAM roles, OIDC federation) over static API keys. If you must issue API keys: prefix + random (`sk_live_...`), store only a hash, show once, allow rotation and scoping.

### Authorization
- Deny by default; check object ownership on every request (BOLA is API1). Centralize it in one policy layer, not scattered `if`s.
- RBAC for coarse roles; ABAC (attributes, e.g. Cedar, OPA/Rego) for context; **ReBAC** (OpenFGA, SpiceDB - Zanzibar-style) when permissions follow sharing graphs (docs, folders, orgs).
- Multi-tenancy: tenant id on every row + enforced by Postgres RLS (`USING (tenant_id = current_setting('app.tenant')::uuid)`) and in the query layer; set the setting per transaction (`SET LOCAL`), which is pooler-safe.
- Mass assignment / BOPLA (API3): explicit input DTOs and output serializers; never bind request JSON straight to an ORM model.

### Secrets
- Secret manager or SOPS/age-encrypted files; inject at runtime; rotate; short-lived credentials (DB IAM auth, Vault dynamic secrets). CI authenticates to clouds via OIDC, no stored cloud keys. Run gitleaks/trufflehog in CI and pre-commit.

### Databases (PostgreSQL first)
- Default to Postgres 18. Use: `GENERATED ALWAYS AS IDENTITY` or `uuidv7()`; `timestamptz` never `timestamp`; `text` + `CHECK` over `varchar(n)`; `numeric` for money; FKs with indexes on the referencing column; `NOT NULL` by default.
- Index for your queries: composite in equality-then-range order, partial indexes (`WHERE deleted_at IS NULL`), covering `INCLUDE`, GIN for jsonb/full text, BRIN for append-only time series. PG 18 skip scan helps when the leading column is missing, but do not design for it. Verify with `EXPLAIN (ANALYZE, BUFFERS)` (buffers are on by default in 18).
- Transactions: default READ COMMITTED; use `SERIALIZABLE` (and retry on `40001`) or `SELECT ... FOR UPDATE` for invariants; `FOR UPDATE SKIP LOCKED` for job queues. Keep transactions short; never call external HTTP inside one. Set `statement_timeout`, `lock_timeout`, `idle_in_transaction_session_timeout`.
- Zero-downtime migrations (expand/contract): add nullable column -> backfill in batches -> dual-write -> switch reads -> enforce constraint (`ADD CONSTRAINT ... NOT VALID` then `VALIDATE CONSTRAINT`) -> drop old in a later release. `CREATE INDEX CONCURRENTLY` (outside a transaction). Always `SET lock_timeout = '3s'` in migrations and retry. Tools: Flyway, Liquibase, Atlas, sqitch, or the framework's tool; lint with squawk.
- Pooling: PgBouncer (transaction mode; supports prepared statements since 1.21) or provider pooler; app pool size small (~2-4x cores of the DB in total, not per pod). Serverless: use an HTTP/pooled driver.
- ORMs vs query builders: either is fine; require typed queries, visible SQL in logs during dev, and no N+1 (check query count per request in tests). Raw SQL only via parameters.
- SQLite in production is legitimate for single-node apps: WAL mode, `busy_timeout`, `synchronous=NORMAL`, `foreign_keys=ON`, one writer; Litestream/LiteFS for replication/backup. Not for multi-writer horizontal scaling.
- Vectors: pgvector (`vector`, `halfvec`, HNSW index, iterative scans from 0.8 so filtered queries return enough rows) before adding a separate vector DB; keep pgvector patched (0.8.7 security fix).
- Search: Postgres FTS (`tsvector` + GIN) or `pg_trgm` first; OpenSearch/Meilisearch/Typesense when you need relevance tuning, facets, typo tolerance at scale.

### Caching
- HTTP first: `Cache-Control: private, no-store` for personal data; `public, max-age=60, stale-while-revalidate=300` for shared; `ETag`/`Last-Modified` + 304; `Vary` correctly; `immutable` for hashed assets. CDN for public GETs.
- App cache (Valkey/Redis): cache-aside with TTL + jitter, request coalescing (single-flight) against stampedes, namespaced keys with a version, never cache authZ decisions longer than revocation tolerance. Treat cache as lossy.

### Queues, events, jobs
- **Transactional outbox**: write the business row and an `outbox` row in one transaction; a relay (poller or CDC/Debezium) publishes; consumers are idempotent (dedupe table keyed by message id). Never "write DB then publish" without it.
- Delivery is at-least-once; design for duplicates and reordering. Dead-letter queue with alerting. Include trace context in message headers.
- Background jobs: durable queue (Postgres-backed is fine at moderate scale, e.g. SKIP LOCKED; otherwise SQS/RabbitMQ/NATS/Kafka), retries with backoff, max attempts, unique job keys, timeouts, and visibility of stuck jobs. Durable workflow engines (Temporal, etc.) for multi-step sagas.

### Rate limiting and abuse
- Limit by principal (API key/user) and by IP for anonymous; token bucket or GCRA in Valkey or at the gateway. Separate tighter limits for login, signup, password reset, OTP, expensive search. Return 429 + `Retry-After` (+ `RateLimit` headers). Cap request body size, page size, upload size, GraphQL cost.

### File uploads and object storage
- Browser uploads go straight to storage via **presigned PUT/POST** (short expiry, e.g. 5-15 min, fixed key generated server-side, `Content-Type` and `content-length-range` in the POST policy). Then confirm server-side (HEAD the object), scan (malware), and only then mark usable.
- Never trust the client filename or MIME; sniff magic bytes; serve user content from a separate domain with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`. Re-encode images.
- Buckets private, Block Public Access on, SSE enabled, lifecycle rules for incomplete multipart uploads. S3 conditional writes (`If-None-Match: *`, `If-Match`) avoid overwrite races (2024-08 / 2024-11).

### Email
- Transactional and marketing on separate subdomains/streams. SPF (`-all` or `~all`, <=10 DNS lookups), DKIM 2048-bit, DMARC starting `p=none` with `rua` reports, move to `quarantine`/`reject`. Aligned From domain. Valid PTR, TLS. Marketing mail: RFC 8058 headers, honour within 48 h, keep spam complaints <0.1% (hard limit 0.3%). Consider BIMI only after `p=quarantine`+.

### Observability
- OpenTelemetry: SDK auto-instrumentation + manual spans around business operations; export OTLP to a Collector (batching, tail sampling, PII scrubbing) then any backend. Follow semantic conventions (`http.request.method`, `http.response.status_code`, `db.system.name`...).
- Structured JSON logs with `trace_id`/`span_id`; no secrets/PII; log levels per environment. RED metrics (rate, errors, duration histograms) per endpoint, USE for resources.
- SLOs: pick 2-4 user-facing SLIs (availability, p95/p99 latency), set objectives (e.g. 99.9% / 30 days), alert on **error-budget burn rate** (multi-window), not on CPU.

### Reliability
- Every outbound call has a timeout (connect + total) and a deadline propagated downstream. Retries only for idempotent requests or with idempotency keys; exponential backoff with full jitter (`sleep = random(0, min(cap, base*2^n))`), max 2-3 attempts, global retry budget.
- Circuit breakers / bulkheads (separate pools) around flaky dependencies; graceful degradation (serve stale cache, hide a widget) over failing the page. Load-shed early (429/503) under overload. Health: `/livez` (process alive, no deps), `/readyz` (can serve: DB reachable, warmed), startup probe for slow boots.
- Graceful shutdown: on SIGTERM stop accepting, fail readiness, drain in-flight requests (within `terminationGracePeriodSeconds`), close pools.

### Containers and deploy
```dockerfile
# syntax=docker/dockerfile:1
FROM <builder-image>@sha256:<digest> AS build
WORKDIR /src
COPY lockfile manifest ./
RUN --mount=type=cache,target=/root/.cache <install deps>
COPY . .
RUN <build>
FROM <distroless-or-dhi-runtime>@sha256:<digest>
COPY --from=build /src/out /app
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/app/server"]
```
- Pin base images by digest (Renovate/Dependabot keeps them fresh); `.dockerignore`; no shell/package manager in runtime; read-only root FS; drop all capabilities; one process per container (PID 1 handles signals, or `--init`/tini). Generate SBOM and sign images (cosign/Sigstore), scan (Trivy/Grype/Docker Scout).
- 12-factor still holds: config via env from a secret store, stateless processes, logs to stdout, dev/prod parity, disposability.
- Pick the smallest platform that works: PaaS (Fly, Render, Railway, Cloud Run, App Runner) or serverless/edge (Cloudflare Workers with D1/Durable Objects/Queues; Lambda) before Kubernetes. Kubernetes when you have several services and a platform team: requests/limits, PodDisruptionBudgets, probes, NetworkPolicies, Pod Security `restricted`, Gateway API for ingress.
- IaC: Terraform or **OpenTofu** (MPL fork after Terraform's 2023 BSL change), Pulumi, or cloud-native (CDK/Bicep). Remote state with locking, plan in PR, apply from CI only, drift detection.
- CI/CD: lint, test, build once, scan, sign, deploy the same artifact to every environment; trunk-based with feature flags; OIDC to clouds; pin third-party CI actions by commit SHA.
- Zero-downtime: rolling or blue/green/canary with automatic rollback on SLO burn; DB migrations backward-compatible with the previous app version (expand before deploy, contract after).

### Backups
- PITR: base backups + WAL archiving (pgBackRest, Barman, WAL-G; PG 17+ native incremental `pg_basebackup --incremental` + `pg_combinebackup`). 3-2-1, one copy immutable/off-account (S3 Object Lock). Encrypt.
- **Restore tests are the backup**: automated scheduled restore into a scratch instance + a data sanity query; record RPO/RTO actually achieved.

## Security

OWASP API Security Top 10 (2023, still current) mapped to the fix:

| Risk | Fix |
|---|---|
| API1 BOLA | Ownership check per object in a central policy layer; RLS for tenants; random non-sequential ids are not a control. |
| API2 Broken Authentication | IdP/OIDC, passkeys, rate-limit + lockout on login/OTP/reset, strict JWT validation, no tokens in URLs. |
| API3 BOPLA (mass assignment / excessive data) | Explicit request/response schemas; allow-list writable fields; never return whole ORM rows. |
| API4 Unrestricted Resource Consumption | Rate limits, max page/body/upload size, timeouts, GraphQL cost limits, budget alerts on paid third-party calls (SMS, LLM). |
| API5 Broken Function Level AuthZ | Deny by default; admin routes on a separate router with its own guard; test every role x endpoint. |
| API6 Sensitive Business Flows | Bot/abuse controls on signup, checkout, referrals (device signals, CAPTCHA/Turnstile, velocity limits). |
| API7 SSRF | Allow-list outbound hosts; block private/metadata IPs after DNS resolution; IMDSv2 on AWS; no redirects. |
| API8 Security Misconfiguration | Secure headers, TLS only, CORS allow-list (no `*` with credentials), verbose errors off, default creds removed. |
| API9 Improper Inventory | OpenAPI as the source of truth, gateway route list, retire old versions, no forgotten `/v0` or staging hosts. |
| API10 Unsafe Consumption of APIs | Validate third-party responses, timeouts, TLS verification, treat webhooks/LLM output as untrusted input. |

- Use **ASVS 5.0** as the requirements checklist: L1 for every app, L2 for anything with personal/financial data. Note IDs changed from 4.0.3; do not cite old `V2.1.x` numbers.
- Injection: parameterized queries everywhere (including `LIKE`, `IN`, `ORDER BY` via allow-list). Deserialize only into typed schemas.
- Supply chain: lockfiles committed, dependency review in PRs, SBOM, signed images, minimal base images, Renovate for patching.
- Logging: no passwords, tokens, full card numbers, session ids; hash or truncate identifiers where possible.

## Performance & quality

- Targets (typical web API): p95 < 200-300 ms, p99 < 1 s for reads; error rate < 0.1%; DB queries per request bounded (no N+1); pool wait time ~0.
- Measure: OTel traces for latency breakdown; `pg_stat_statements` top total time; `EXPLAIN (ANALYZE, BUFFERS)`; PG 19 `pg_stat_lock` for lock waits; autovacuum lag (`n_dead_tup`); cache hit ratio; queue depth and age of oldest message.
- Load test before launch and after major changes (k6, Gatling, Locust) against production-like data volumes; test the 429 and degradation paths too.
- Payloads: gzip/br/zstd compression, field selection or sparse fieldsets for heavy resources, avoid chatty APIs (batch endpoints).

## Testing & tooling

- API contract: Spectral/Redocly lint, Schemathesis (property-based from OpenAPI), oasdiff or `buf breaking` for breaking-change checks in CI.
- Integration tests against real dependencies via Testcontainers (Postgres, Valkey, LocalStack/MinIO), not mocks of the DB.
- Migrations: run up + down (or forward-fix) in CI on a copy of prod schema; squawk for dangerous DDL.
- Security: SAST (Semgrep/CodeQL), dependency scanning, secret scanning (gitleaks), container scanning (Trivy), DAST (ZAP) on staging, IaC scanning (Checkov/tfsec/Trivy config).
- Docker: hadolint; `docker buildx` with provenance/SBOM attestations.

## Common mistakes in AI-written code

- Uses OAuth implicit flow, ROPC, or a SPA holding refresh tokens in `localStorage`; JWT `decode()` instead of `verify()`; trusts `alg` from the header.
- Hashes passwords with SHA-256/MD5 or bcrypt without handling the 72-byte limit; compares secrets with `==` (use constant-time compare).
- Writes `CREATE INDEX` (blocking) or `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT <volatile>` in a migration on a large table; no `lock_timeout`.
- Uses `timestamp` without time zone, `float` for money, `SERIAL`, `uuid4` PKs, OFFSET pagination.
- Publishes to a queue after commit without an outbox ("dual write"), or inside the transaction before commit.
- Retries non-idempotent POSTs, retries without jitter, no timeouts on HTTP clients (many default to infinite).
- Returns 200 with `{"error": ...}`; returns 403 vs 404 in a way that leaks existence; invents headers like `X-Idempotency-Key` instead of `Idempotency-Key`.
- Dockerfiles with `:latest`, `apt-get upgrade`, running as root, copying `.env` into the image, `ADD` of remote URLs, one giant stage.
- Recommends ingress-nginx, `nullable: true` in OpenAPI 3.1, Redis as "BSD licensed", PG 13 as supported, "OWASP API Top 10 2019".
- Kubernetes liveness probe that checks the database (causes restart storms); readiness that never fails during shutdown.
- Webhook verification on parsed-and-reserialized JSON instead of the raw body.
- CORS `Access-Control-Allow-Origin: *` reflected with credentials; CORS assumed to stop CSRF.

## Before you ship

- [ ] OpenAPI spec linted, published, matches the implementation (contract tests green); breaking-change check in CI.
- [ ] Every endpoint: authN, object-level authZ, input schema, output schema, rate limit, size limits, timeout.
- [ ] Errors are RFC 9457 problem+json, no stack traces; 401/403/404/409/422/429 used correctly.
- [ ] Passwords Argon2id; sessions `__Host-` HttpOnly Secure SameSite cookies or short tokens + rotating refresh; MFA/passkeys available.
- [ ] Secrets only from a secret manager; secret scanning on; no keys in images or repo.
- [ ] Migrations expand/contract, `CONCURRENTLY`, `lock_timeout`; tested against prod-sized data.
- [ ] Outbox for events; consumers idempotent; DLQ alerting.
- [ ] Images pinned by digest, non-root, minimal, scanned, signed; liveness/readiness/startup probes; graceful SIGTERM.
- [ ] OTel traces/metrics/logs flowing; SLOs and burn-rate alerts defined; dashboards for RED + DB + queue.
- [ ] Backups with PITR and a **passed restore test** this month; RPO/RTO written down.
- [ ] Email: SPF/DKIM/DMARC aligned, one-click unsubscribe on marketing mail.
- [ ] Postgres on a supported major (14 goes EOL 2026-11-12), scram-sha-256 auth, TLS required.

## Sources

All accessed 2026-10-09.
- https://www.postgresql.org/support/versioning/ - PG supported versions, minors, EOL dates.
- https://www.postgresql.org/about/news/postgresql-18-released-3142/ - PG 18 features (AIO, uuidv7, virtual generated cols, OAuth, skip scan, md5 deprecation).
- https://www.postgresql.org/about/news/postgresql-19-beta-1-released-3313/ - PG 19 features (REPACK, ON CONFLICT DO SELECT, SQL/PGQ, JIT off, RADIUS removed).
- https://www.postgresql.org/about/newsarchive/ - PG 19 Beta 4 (2026-09-24), pgvector 0.8.7 security release.
- https://github.com/pgvector/pgvector/blob/master/CHANGELOG.md - pgvector versions and dates.
- https://www.sqlite.org/changes.html - SQLite 3.54.0 date.
- https://redis.io/blog/agplv3/ - Redis 8 AGPLv3 option, 2024 SSPL change.
- https://valkey.io/download/releases/ - Valkey 9.0 and 9.0.6 dates.
- https://kubernetes.io/releases/ - supported Kubernetes minors and EOL.
- https://opensource.googleblog.com/2026/02/the-end-of-an-era-transitioning-away-from-ingress-nginx.html - ingress-nginx retirement March 2026 (lead; corroborated by kubernetes.io blog listing).
- https://www.openapis.org/blog/2025/09/23/announcing-openapi-v3-2 - OpenAPI 3.2 features.
- https://spec.graphql.org/September2025/ and https://graphql.org/blog/2025-09-08-september-edition/ - GraphQL Sept 2025 edition, @oneOf.
- https://www.rfc-editor.org/info/rfc10008 - HTTP QUERY method RFC.
- https://www.rfc-editor.org/info/rfc9745 - Deprecation header.
- https://www.rfc-editor.org/rfc/rfc9457 - Problem Details.
- https://www.rfc-editor.org/info/rfc9700 - OAuth 2.0 Security BCP.
- https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/ - OAuth 2.1 draft-16 status.
- https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ - Idempotency-Key draft status.
- https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/ - RateLimit headers draft-11.
- https://api-security.owasp.org/ - OWASP API Security Top 10 2023 is latest.
- https://owasp.org/www-project-application-security-verification-standard/ - ASVS 5.0.0 is current; release date/chapters from https://softwaremill.com/whats-new-in-asvs-5-0/ (secondary).
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html - password hashing parameters.
- https://pages.nist.gov/800-63-4/sp800-63b.html - NIST 800-63B-4 password and syncable authenticator rules.
- https://support.google.com/a/answer/81126 - Gmail sender requirements, one-click unsubscribe.
- https://dmarcian.com/microsoft-enforces-spf-dkim-dmarc/ - Outlook.com May 2025 enforcement (secondary; Microsoft post not fetched).
- https://www.docker.com/press-release/docker-makes-hardened-images-free-open-and-transparent-for-everyone/ - DHI free, Apache-2.0, dhi.io.
- https://www.cncf.io/blog/2025/11/11/openfga-becomes-a-cncf-incubating-project/ - OpenFGA incubating.
- https://aws.amazon.com/blogs/opensource/cedar-joins-cncf-as-a-sandbox-project - Cedar CNCF sandbox.
- https://opentelemetry.io/status/ - OTel signal stability per language.
- https://aws.amazon.com/about-aws/whats-new/2024/11/amazon-s3-enforcement-conditional-write-operations-general-purpose-buckets/ - S3 conditional writes.
