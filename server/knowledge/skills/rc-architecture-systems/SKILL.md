---
name: rc-architecture-systems
description: System architecture decisions as of 2026-10 - monolith vs modular monolith vs microservices, domain boundaries, event-driven design (outbox, sagas, CQRS, event sourcing), distributed-systems limits, Kafka/RabbitMQ/NATS/SQS choice, gateway/BFF/mesh, scaling, multi-tenancy, payments (Stripe, webhooks, 3DS, PCI), search, media, real-time, jobs, HA/DR, ADRs, strangler fig. Use when designing a system, writing an ADR or reviewing architecture.
---
# System architecture and distributed systems (as of 2026-10)

> Facts are dated (see Sources). Versions and licences move: confirm the primary source before pinning one. "(unverified)" marks a lead, not a fact.

Decision-level skill. Mechanics live elsewhere: API design, idempotency keys, outbox table, rate-limit headers, email DNS, presigned uploads,
OTel setup, backups -> **rc-web-backend**. Engines and data modelling -> rc-databases. Deploy/infra -> rc-devops-linux.
Long tables (broker comparison, payments flow, HA/DR tiers, ADR template) are in `references/decisions.md`.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| Apache Kafka | **4.3** (4.3.0 2026-05-22, 4.3.1 2026-06-25); 4.2.2 2026-09-29; 4.1.2 2026-03-17 | ZooKeeper gone since 4.0 (KRaft only). **Share groups ("Queues for Kafka", KIP-932) production-ready in 4.2** (2026-02-17): per-record ack, many consumers per partition. |
| RabbitMQ | **4.3** (4.3.6, 2026-09-16; community support to 2027-01-31); 4.2 community support ended 2026-07-31 | Classic mirrored queues **removed in 4.0**: use quorum queues or streams. AMQP 1.0 is core since 4.0. **Khepri** metadata store: default for new clusters in 4.2, the **only** store in 4.3 (Mnesia gone): enable the `khepri_db` feature flag before upgrading to 4.3. |
| NATS Server | **2.15.1** / 2.14.8 (both 2026-10-09), Apache-2.0, stays in CNCF | 2025-05 settlement: Synadia assigned trademarks to the LF and dropped the BSL fork plan. 2.12 added atomic batch publish and counter streams. |
| Redis / Valkey | Redis 8.10.2 (2026-09-17, RSAL/SSPL/AGPL); Valkey 9.0.6 / 9.1.2 (2026-09-01, BSD-3) | Both have Streams (consumer groups, XACK). |
| Apache Pulsar | 5.0.0 (2026-10-05); 4.0.x LTS line still patched | |
| AWS SQS / SNS / EventBridge | SQS max payload **1 MiB** (was 256 KiB, 2025-08); **fair queues** for standard queues (2025-07, message group id = tenant); EventBridge can target fair queues (2025-11) | Old SDK wrappers may still cap at 256 KiB; Extended Client Library (S3 offload) for up to 2 GB. |
| Temporal (durable workflows) | server 1.32.1 (2026-10-08) | |
| Debezium (CDC for outbox) | 3.7.0.Final (latest tag) | |
| Search | Elasticsearch 9.5.5 / 8.19.23 (2026-10-06), licences ELv2/SSPL/**AGPLv3** (AGPL option since 2024-08); OpenSearch 3.9.0 (2026-09-29, Apache-2.0, OpenSearch Software Foundation/LF since 2024-09); Meilisearch 1.54.x; Typesense 30.2 | |
| Object storage | **MinIO community edition: no binaries/images since 2025-10, maintenance mode 2025-12, repo archived 2026-04-25** | Pick a maintained S3 store (cloud S3/R2/GCS, Ceph RGW, Garage, SeaweedFS) for new self-hosting. |
| Service mesh | Istio **ambient mode GA in 1.24** (2024-11): ztunnel per node (L4 mTLS) + optional waypoints (L7), no sidecars | |
| Real-time | **WebTransport Baseline**: Safari 26.4 (2026-03-24) completed support | WebSocket/SSE remain defaults; WebTransport for unreliable/multiplexed streams. |
| Stripe | API version **2026-09-30.endive** (new major); 2026-03-25.dahlia before it; monthly non-breaking versions, two breaking majors a year since 2024-09 (acacia) | Stripe now recommends **Checkout Sessions** (+ Payment Element) over raw PaymentIntents for most integrations. |
| PCI DSS | v4.0.1; future-dated reqs mandatory after 2025-03-31. SAQ A (2025-01 revision) dropped 6.4.3/11.6.1 but merchants embedding a payment iframe must confirm their site is not susceptible to script attacks | Redirect/hosted page = smallest scope. |

### Older versions
- Kafka 3.x clusters may still run ZooKeeper; 3.9 was the bridge release for migration to KRaft. Do not suggest share groups below 4.2 (early access in 4.0, preview in 4.1: not for production).
- RabbitMQ 3.13 and older: mirrored classic queues exist but are deprecated; plan quorum queues before a 4.x upgrade. 3.13 and 4.0/4.1 are out of community support.
- SQS clients pinned to old SDK limits: 256 KiB; Extended Client Library (S3 offload) still needed there.
- Self-hosted MinIO already running: keep it patched from source or a vendor build, plan migration; do not add it to new projects.
- Legacy Stripe integrations on Charges/Sources or card-only Tokens: work, but new flows go through Checkout Sessions/PaymentIntents (SCA-ready). Do not force a version bump the task did not ask for; pin the API version per request or in the SDK.

## What changed / stop doing

| Stop (old) | Do instead (new) | Since |
|---|---|---|
| Microservices by default for a new product | Modular monolith; extract a service only for a measured reason (see decision procedure) | industry consensus, 2023+ |
| Services sharing one database schema | One owner per table/schema; others use its API or its events | - |
| "Distributed monolith" (services that must deploy together, sync call chains) | Merge them back or decouple with events; deploy independence is the test | - |
| Two-phase commit / XA across services | Local transaction + outbox; saga with compensations; durable workflow engine | - |
| Home-grown broker on a DB table polled by every service | Postgres queue library for one app (SKIP LOCKED: pgmq, River, Oban, Solid Queue), managed broker across apps | - |
| "Exactly-once delivery" claims | At-least-once + idempotent consumer (effectively-once); Kafka EOS only covers read-process-write inside Kafka | - |
| ZooKeeper-based Kafka | KRaft | Kafka 4.0 (2025-03) |
| Kafka partitions as a work queue (consumers <= partitions) | Share groups for queue semantics, or a real queue (SQS/RabbitMQ/JetStream work-queue) | Kafka 4.2 (2026-02) |
| RabbitMQ mirrored classic queues | Quorum queues (or streams for replay) | RabbitMQ 4.0 (2024-09) |
| MinIO community as the default S3 for self-hosting | Cloud object store or a maintained S3-compatible server | 2025-10 to 2026-04 |
| Sidecar-per-pod mesh as the only option | No mesh until you need mTLS/L7 policy across many services; then ambient mode | Istio 1.24 (2024-11) |
| Event sourcing for CRUD apps | Plain tables + audit log/outbox; event sourcing only where the event history IS the domain | - |
| Active-active multi-region "for availability" on day 1 | Single region, multi-AZ, tested backups; then warm standby; active-active only with a real RTO need and conflict strategy | - |
| Raw card fields on your page / PaymentIntents-only custom flows for simple checkout | Hosted Checkout / Checkout Sessions + Payment Element; webhooks as the source of truth | Stripe guidance 2025-2026 |
| Long-polling for live updates | SSE for server->client, WebSocket for bidirectional, WebTransport/WebRTC for media/unreliable | WebTransport Baseline 2026-03 |

## Do this

### Start-here defaults (small team, new product)
- One deployable (modular monolith), one Postgres, one cache (Valkey) only when measured, a Postgres-backed job queue, object storage, a managed email
  provider, hosted payments. Single region, multi-AZ. OTel from day one. ADRs in `docs/adr/`.
- Add each extra moving part only with a written reason (ADR) naming the load, team or compliance driver. **When NOT to add a dependency**: if Postgres
  (FTS, SKIP LOCKED, LISTEN/NOTIFY, pgvector, partitioning) or the platform already does it at your measured load, do not add a broker, search engine,
  cache or mesh. Each new system adds on-call, upgrades, backups, licences and a privacy surface.
- Privacy-first: collect only what the feature needs; keep PII in one owned store; events carry ids, not personal data, where possible (crypto-shredding or
  short retention for event logs that must hold PII; GDPR erasure is hard on immutable logs).

### Monolith vs modular monolith vs microservices: decision procedure
1. Default: **modular monolith**. Modules = bounded contexts; each owns its tables (schema per module), exposes an in-process API; no cross-module joins
   or table reads. Enforce with build rules (ArchUnit, import-linter, eslint boundaries, Go `internal/`, Java modules / Spring Modulith).
2. Extract a service only if at least one holds, measured not guessed:
   - **Team**: > ~2 teams (each ~5-9 people) blocked on one deploy pipeline or release train.
   - **Scale profile**: one module needs very different scaling/hardware (GPU, memory, bursty batch) and it is a real cost.
   - **Fault/security isolation**: a module must not take the rest down, or must sit in a separate compliance scope (payments, PHI).
   - **Different lifecycle/tech**: e.g. ML inference in Python next to a Java core.
3. Prerequisites before the first extraction: CI/CD per service, central logs/traces, contract tests, on-call ownership, an independent data store.
   No platform maturity -> no microservices.
4. Honest costs of microservices: network failures and partial failure on every call; no cross-service transactions (sagas instead); eventual
   consistency visible to users; schema/contract versioning; N pipelines, N dashboards, N upgrade cycles; harder local dev and debugging; latency per hop;
   more cloud spend. Typical break-even is organisational, not technical.
5. Test for a **distributed monolith**: if two services must deploy together, share a DB, or a request needs 4+ synchronous hops, merge or decouple.

| Situation | Choose |
|---|---|
| 1-15 engineers, one product | Modular monolith |
| Several teams, one domain, shared release pain | Modular monolith + a few extracted services along clear contexts |
| Many autonomous teams, independent release cadence, platform team exists | Microservices (service per bounded context, not per entity) |
| Spiky isolated workload (image transcode, PDF, ML) | Monolith + separate worker/function for that job |

### Domain boundaries
- Find bounded contexts from language and change patterns: where the same word means different things (an "Order" in sales vs fulfilment), where data
  changes together, where teams differ. Event storming is a fast workshop for this.
- A context owns its data and invariants. Integrate via published API or domain events; translate at the edge (anti-corruption layer) for legacy or vendors.
- Prefer coarse services; split later. Splitting is cheaper inside a modular monolith than across the network.
- Conway's law is real: align module/service ownership with team ownership.

### Event-driven design
- **Commands** ("ChargeCard") target one handler, may be rejected, imperative name. **Events** ("PaymentCaptured") are facts, past tense, many
  subscribers, never rejected. Do not disguise commands as events.
- Event payload: `id` (unique), `type`, `occurred_at`, `source`, aggregate id, schema version, minimal data (or "thin event + fetch"). CloudEvents envelope
  for cross-org. Version schemas (Avro/Protobuf/JSON Schema registry); only additive changes in place.
- **Outbox** on the producer (mechanics in rc-web-backend). **Inbox / idempotent consumer**: store processed message ids (or use a natural idempotent
  upsert) in the same transaction as the side effect.
  ```sql
  -- consumer: dedupe and effect in one transaction
  INSERT INTO inbox(message_id) VALUES ($1) ON CONFLICT DO NOTHING RETURNING 1;
  -- no row returned -> already processed: ack and skip
  ```
- Ordering: only per key (partition/message group). Design handlers to tolerate out-of-order (version numbers, `updated_at` guards, ignore stale).
- **Sagas** for multi-service workflows: each step a local transaction with a compensating action (refund, release stock). Orchestration (one
  coordinator, e.g. Temporal, Step Functions, a state table) is easier to reason about than choreography beyond 3 steps. Compensations must be idempotent
  and can fail too: alert and park.
- **CQRS**: separate read models when reads are very different from writes (dashboards, search). Not needed for CRUD; a SQL view or replica is often enough.
- **Event sourcing**: store events as the source of truth. Use only for audit-native domains (ledgers, bookings, workflows with history queries).
  **When NOT**: CRUD, small teams, data that must be erased (GDPR), unclear domain, no snapshot/upcasting plan. Costs: schema evolution of old events,
  replays, projections lag, harder queries.

### Distributed-systems facts to design with
- **CAP** applies only during a partition: pick consistency (refuse) or availability (serve possibly stale). **PACELC**: without partitions you still trade
  latency vs consistency (sync replication = slower writes). Say which one each feature needs.
- Consistency models to name in designs: linearizable (single up-to-date copy), sequential, causal, read-your-writes, monotonic reads, eventual. Replica
  reads break read-your-writes: route a user's reads to the primary for a few seconds after their write, or use a session LSN/token.
- **Clocks lie**: wall clocks skew and jump; never order events across machines by timestamp. Use DB sequences, per-key versions, Lamport/vector clocks,
  or hybrid logical clocks. Use monotonic clocks for timeouts. Leases need fencing tokens.
- **Retries**: a timeout means "unknown", not "failed". Retry only idempotent ops; bounded, jittered, with a retry budget; retry at one layer only
  (retries x layers = storm). Exactly-once delivery does not exist end-to-end; effectively-once = at-least-once + dedupe.
- **Backpressure**: bounded queues everywhere; when full, block producers, shed (429/503), or drop low-priority work. Unbounded in-memory queues are a
  memory leak with a delay.
- **Consensus** (Raft/Paxos): use it as a black box via etcd, ZooKeeper, Consul, KRaft, a DB with leader election, or Postgres advisory locks. Never
  implement your own. Majority quorum: 3 nodes tolerate 1 failure, 5 tolerate 2; even counts add nothing.
- Fallacies still bite: the network is not reliable, latency is not zero, bandwidth is not infinite, topology changes.

### Messaging and streaming: which one
| Need | Default |
|---|---|
| Background jobs in one app | Postgres queue (SKIP LOCKED library) or the framework's queue |
| Work queue across services, AWS | SQS (+ DLQ; fair queues for multi-tenant) ; fan-out SNS->SQS; routing/scheduling/SaaS events EventBridge |
| Work queue / routing, self-hosted | RabbitMQ 4.x quorum queues, or NATS JetStream work-queue stream |
| Event log with replay, high throughput, many consumers, stream processing | Kafka 4.x (managed if possible) or Redpanda (core is BSL, not open source) / Pulsar |
| Low-latency request/reply, edge/IoT, lightweight pub/sub | NATS (core) + JetStream for persistence |
| GCP | Pub/Sub (ordering keys, exactly-once delivery option within Pub/Sub, DLQ) |
| Small scale with Redis/Valkey already present | Streams + consumer groups (accept weaker durability; AOF fsync settings matter) |
Detail matrix in `references/decisions.md`.

### Edge: API gateway, BFF, service mesh
- **Gateway** (cloud API GW, Envoy Gateway, Kong, Traefik, Gateway API on K8s) when several services sit behind one public API: TLS, auth offload,
  rate limits, routing. One service -> a reverse proxy is enough.
- **BFF** per client type when web and mobile need different shapes, and for browser auth (token handling, see rc-web-backend). Keep business rules out.
- **Service mesh** only with many services needing mTLS, L7 policy, retries/traffic shifting uniformly and a team to run it. Prefer Istio ambient or
  Linkerd; first ask if platform-native mTLS (cloud service networking) suffices.

### Scalability patterns (in this order)
1. Measure (traces, `pg_stat_statements`). Fix queries, indexes, N+1 first.
2. HTTP/CDN caching, then app cache (cache-aside; see rc-web-backend for stampede control). Know your invalidation story before adding a cache.
3. Vertical scale the DB; connection pooling. A single modern Postgres primary handles far more than most products need.
4. **Read replicas** for read-heavy, staleness-tolerant queries (mind read-your-writes).
5. **Queue-based load leveling**: accept fast, process async (202 + status), size workers to throughput.
6. **Load shedding and rate limiting**: per tenant and per endpoint; reject early with 429/503 and `Retry-After`; prioritise paid/critical traffic.
7. Partition tables (time or tenant), archive cold data.
8. **Sharding** last: by tenant/customer id; cross-shard queries and rebalancing are the cost. Consider Citus or a distributed SQL DB before
   hand-rolled sharding.

### Multi-tenancy models
| Model | Use when | Cost |
|---|---|---|
| Pool: shared tables + `tenant_id` + RLS | Default for SaaS, many small tenants | Noisy neighbours (use per-tenant limits, SQS fair queues); careful indexes `(tenant_id, ...)` |
| Bridge: schema per tenant | Tens to hundreds of tenants, per-tenant customisation | Migrations x N, connection churn |
| Silo: DB/stack per tenant | Enterprise isolation, data residency, BYOK | Ops cost per tenant; automate provisioning |
- Tenant id comes from the authenticated principal, never the request body. Put it in logs, traces, metrics, cache keys, object-storage prefixes, queue
  message group ids. Test cross-tenant access explicitly.

### Payments (Stripe-style)
- Use hosted Checkout or Checkout Sessions + Payment Element; card data never touches your servers (SAQ A scope; with an embedded form you must also confirm the page resists script attacks). Server creates the session/intent with
  amount from **your** DB (never the client), plus an `Idempotency-Key`.
- **Webhooks are the source of truth** for fulfilment (`checkout.session.completed`, `payment_intent.succeeded`, `invoice.paid`, disputes, refunds), not
  the client redirect. Verify signature on the raw body, dedupe by event id, handle out-of-order and replays, respond 2xx fast and process async.
- SCA/3DS: let the provider trigger it (`requires_action` status); off-session renewals need a saved, SCA-authenticated payment method and a recovery
  path (email the customer to authenticate).
- Model an internal payment/order state machine; reconcile nightly against provider data (payouts, balance transactions). Money in integer minor units.
- Pin the API version; upgrade deliberately with webhook endpoint versions. Full flow in `references/decisions.md`.

### Transactional email and notifications
- One notification service/module: event -> template -> channel (email, push, SMS, in-app) -> provider, with user preferences, quiet hours, dedupe key and
  per-user rate limits. Send via a queue; store delivery status from provider webhooks (bounce, complaint -> suppress).
- Use a managed provider (SES, Postmark, Resend, SendGrid, Mailgun etc.); separate transactional and marketing streams (deliverability in rc-web-backend).
- Never put secrets or full PII in push payloads or email subjects.

### Search infrastructure
- Start with Postgres FTS / `pg_trgm` / pgvector. Add a search engine when you need typo tolerance, facets, relevance tuning or scale.
- Engine is a **derived index**: feed it from outbox/CDC, make reindex-from-source a scripted, tested operation (alias swap). Never the system of record.
- Choice: Meilisearch/Typesense for product/site search (simple ops); OpenSearch/Elasticsearch for large, log-like or complex aggregations (check licence
  fit: OpenSearch Apache-2.0; Elasticsearch AGPL/ELv2/SSPL); managed (Algolia, Elastic Cloud, OpenSearch Service) when ops is the bottleneck.
- Enforce tenant/authorisation filters inside the search query; do not post-filter in the app (leaks counts and facets).

### Files, object storage and media
- Upload direct to object storage (presigned, see rc-web-backend) -> event -> worker pipeline: validate, malware scan, strip metadata (EXIF GPS), transcode
  or resize, write derivatives under content-addressed keys -> mark ready. Idempotent steps; status per asset.
- Serve via CDN with signed URLs/cookies for private media. Images: generate AVIF/WebP variants or use an image CDN. Video: HLS/DASH via a managed
  service unless video is your core business.
- Lifecycle rules (temp uploads, old versions), versioning + object lock for backups.

### Real-time: pick the transport
| Need | Choose |
|---|---|
| Server -> client updates (feeds, progress, LLM tokens) | **SSE** (HTTP, auto-reconnect, `Last-Event-ID`) |
| Bidirectional low-latency messages (chat, collaboration, games) | **WebSocket** |
| Audio/video, P2P, sub-200 ms media | **WebRTC** (SFU like LiveKit/mediasoup; TURN needed) |
| Many independent streams, unreliable datagrams, client-server | **WebTransport** (HTTP/3; Baseline since 2026-03), keep a WebSocket fallback for proxies |
| Infrequent changes | Polling with ETag is fine |
- Real-time servers are stateful: fan-out via pub/sub (Valkey, NATS, Postgres LISTEN/NOTIFY at small scale), auth on connect + re-check on
  subscribe, heartbeats, reconnection with resume cursor, per-connection rate limits. Managed options (Ably, Pusher, Supabase Realtime, Cloudflare Durable
  Objects) reduce ops.

### Background jobs and schedulers
- Jobs: durable, idempotent, unique key, timeout, max attempts, backoff, DLQ/"dead" state, visible in an admin view. Small payloads (ids, not blobs).
- Scheduled jobs: one scheduler (cron in the job library, K8s CronJob with `concurrencyPolicy: Forbid`, EventBridge Scheduler, Cloud Scheduler). With
  several app instances, guard with a lock/lease or unique job key so a run happens once. Make runs catch-up-safe (process "since last success").
- Multi-step, long-running, human-in-the-loop: a durable workflow engine (Temporal, Restate, Step Functions, Inngest etc.) instead of chained jobs.

### High availability and disaster recovery
- Write **RPO** (max data loss) and **RTO** (max downtime) per system with the business. They drive cost; most products: RPO minutes, RTO hours.
- Tiers: backup/restore (cheapest) -> pilot light -> warm standby (active-passive) -> active-active. Multi-AZ in one region covers most failures.
- Multi-region honesty: active-active needs conflict handling (or per-region data ownership), global routing, and doubles ops. Many "multi-region" setups
  still depend on one region's control plane (DNS, IAM, CI, secrets). Map and test those dependencies.
- DR is real only when exercised: scheduled restore tests and failover game days with a runbook; measure achieved RPO/RTO.
- Design for **graceful degradation**: which features turn off when search, payments or email are down.

### Architecture decision records
- One markdown file per decision in `docs/adr/NNNN-title.md`: context, options, decision, consequences, status (proposed/accepted/superseded).
  Short (one page). Supersede, never edit history. Template in `references/decisions.md`.
- Write one for: new datastore/broker, service extraction, sync vs async boundary, tenancy model, build-vs-buy, region strategy.

### Evolutionary architecture and strangler fig
- Fitness functions: automated checks for the qualities you care about (module dependency rules, p95 budgets, bundle size, no cross-schema queries) in CI.
- **Strangler fig** migration: put a routing facade (proxy/gateway) in front of the legacy system; move one capability at a time to the new code;
  sync data via CDC/events during transition; route traffic gradually (flags, percentages); delete the legacy path when traffic is zero.
  Never big-bang rewrite. Use an anti-corruption layer to keep legacy concepts out of the new model.
- Parallel run / shadow traffic for risky moves (compare results before switching).

### Observability by design
- Every boundary propagates trace context (HTTP `traceparent`, message headers). Correlate events and jobs to the request that caused them.
- Define SLOs per user journey before building; emit business metrics (orders/min, payment failures) next to RED metrics. Queue depth and age of oldest
  message are first-class signals. Setup details in rc-web-backend.

### Anti-overengineering checklist (run before adding anything)
- [ ] Is there a measured problem (numbers, incident, team blockage), or a guess?
- [ ] Can Postgres, the framework or the platform do it at our load?
- [ ] Who runs it at 3am, upgrades it, backs it up? Is that person on the team?
- [ ] What is the licence and the exit path?
- [ ] Is it reversible? If not, write an ADR.
- [ ] Does it add a network hop to the hot path? A new consistency problem?
- [ ] Would a modular boundary in code give 80% of the benefit?

## Security
- Zero trust between services: authenticate every call (mTLS or workload identity tokens), authorise per operation; the internal network is not trusted.
- Brokers: TLS, per-service credentials with topic/queue ACLs (producer can write only its topics), no anonymous access; encrypt sensitive payload
  fields; treat consumed messages as untrusted input (validate schema).
- Event logs and search indexes copy data: apply the same tenant isolation, retention and erasure rules as the source.
- Webhooks in and out: signatures, replay windows, SSRF protection on outbound (rc-web-backend).
- Payments: SAQ A by keeping card entry on the provider; CSP and script inventory on any page that hosts the payment iframe; never log PAN/CVC.
- Multi-tenant: tenant from auth context, RLS or equivalent, per-tenant keys for silo customers, test cross-tenant reads.
- DR copies: encrypted, in another account with immutable retention (ransomware).

## Performance & quality
- Budget latency per hop: each synchronous service call adds network + serialisation; keep user-facing paths at <= 2-3 sync hops.
- Track: p95/p99 per journey, error-budget burn, queue depth + oldest-message age, consumer lag (Kafka), DLQ count, replica lag, cache hit ratio,
  saga stuck count, webhook processing delay.
- Capacity: load-test with production-like data; know the first bottleneck (usually the DB) and its headroom.
- Cost is a quality metric: track per-tenant and per-feature cloud cost when choosing architecture.

## Testing & tooling
- Architecture rules in CI: ArchUnit (JVM), Spring Modulith verify, import-linter (Python), dependency-cruiser / eslint boundaries (JS/TS), go-arch-lint.
- Contracts: Pact or schema-registry compatibility checks for events; `buf breaking` for protobuf; AsyncAPI for event docs.
- Integration: Testcontainers for Kafka/RabbitMQ/NATS/LocalStack. Test duplicate delivery, reordering and consumer crash between effect and ack.
- Resilience: fault injection (toxiproxy, chaos tools) on timeouts and dependency outages; DR restore drills on a schedule.
- Diagrams as code (C4 via Structurizr, Mermaid) next to ADRs.

## Common mistakes in AI-written code
- Proposes microservices, Kafka, Kubernetes and a service mesh for an MVP or a 3-person team.
- "Write to DB, then publish to the broker" without outbox; consumer without dedupe; assumes in-order delivery across partitions/keys.
- Claims exactly-once delivery from SQS standard, RabbitMQ or Kafka for side effects outside Kafka.
- Distributed transactions via 2PC/XA, or sagas without compensations and without idempotency.
- Orders events by `Date.now()` from different hosts; uses wall clock for timeouts.
- Retries at client, gateway and service at once; retries non-idempotent payment calls without an idempotency key.
- Fulfils orders on the payment success redirect instead of the verified webhook; trusts the amount sent by the browser.
- Recommends ZooKeeper Kafka setups, RabbitMQ mirrored queues, MinIO community images, or Redis as BSD.
- Uses Kafka consumers as a work queue and hits "consumers > partitions" idling (share groups exist since 4.2).
- Event sourcing or CQRS for a CRUD admin panel; separate databases per microservice joined through a shared reporting DB read by all.
- WebSocket for one-way streams where SSE suffices; no reconnection/resume logic.
- Multi-region active-active without a conflict strategy; "HA" with a single-region DNS/secrets dependency.
- Tenant id taken from request body or query string; cache keys without tenant prefix.

## Before you ship
- [ ] ADR exists for each new datastore, broker, service boundary, tenancy or region decision; anti-overengineering checklist answered.
- [ ] Module/service boundaries enforced in CI; no shared tables across owners.
- [ ] Every async path: outbox, idempotent consumer, DLQ with alert, trace context, schema versioning.
- [ ] Timeouts, bounded retries with jitter, backpressure/load shedding on every dependency.
- [ ] Payments: webhook-driven fulfilment, signature + dedupe, idempotency keys, reconciliation job, SCA recovery path, card data off your servers.
- [ ] Tenant isolation tested (cross-tenant read attempt fails) in DB, cache, search, storage and queues.
- [ ] RPO/RTO written; restore test and failover runbook exercised; degradation modes known.
- [ ] SLOs and dashboards for journeys, queues, lag, replicas; on-call owner for every component.
- [ ] Rollback plan: feature flags or routing switch (strangler facade) to go back; migrations backward-compatible.
- [ ] Privacy: data minimised, PII not spread into events/logs/indexes without retention and erasure rules.

## Sources
All accessed 2026-10-09; re-checked 2026-10-10.
- https://kafka.apache.org/community/downloads/ - Kafka 4.3.1/4.3.0/4.2.2/4.1.2 versions and dates.
- https://kafka.apache.org/blog/2026/02/17/apache-kafka-4.2.0-release-announcement/ - 4.2.0 date, share groups production-ready (corroborated by confluent.io release blog).
- https://www.rabbitmq.com/release-information - RabbitMQ series, patches, community support dates.
- https://github.com/rabbitmq/rabbitmq-server/releases (v4.0.1, v4.2.0, v4.3.0 notes) - mirrored queue removal, AMQP 1.0 core, Khepri default in 4.2 and only store in 4.3; LICENSE file: MPL-2.0.
- https://kafka.apache.org/blog/2025/03/18/apache-kafka-4.0.0-release-announcement/ - KRaft only, share groups early access in 4.0; https://kafka.apache.org/blog/2025/09/04/apache-kafka-4.1.0-release-announcement/ - Queues for Kafka preview in 4.1.
- https://github.com/nats-io/nats-server/releases (GitHub API) - NATS 2.15.1/2.14.8 dates; https://docs.nats.io/release-notes/whats_new/whats_new_212 - 2.12 atomic batch, counters.
- https://www.cncf.io/blog/2025/05/01/protecting-nats-and-the-integrity-of-open-source-cncfs-commitment-to-the-community/ and https://thenewstack.io/cncf-and-synadia-reach-an-agreement-on-nats/ - NATS settlement.
- GitHub releases API: redis/redis, valkey-io/valkey, apache/pulsar, temporalio/temporal, debezium tags, elastic/elasticsearch, opensearch-project/OpenSearch, meilisearch, typesense - versions and dates.
- https://aws.amazon.com/about-aws/whats-new/2025/07/amazon-sqs-introduces-fair/ - SQS fair queues; https://aws.amazon.com/about-aws/whats-new/2025/11/amazon-eventbridge-sqs-fair-queue-targets - EventBridge targets.
- https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html - SQS max message 1,048,576 bytes; Extended Client Library up to 2 GB.
- https://developer.mozilla.org/en-US/docs/Web/API/WebTransport - WebTransport "Baseline newly available" since 2026-03.
- https://github.com/redpanda-data/redpanda/tree/dev/licenses - Redpanda core BSL, enterprise features RCL.
- https://www.elastic.co/blog/elasticsearch-is-open-source-again - Elasticsearch AGPL option (2024-08-29); https://www.linuxfoundation.org/press/linux-foundation-announces-opensearch-software-foundation-to-foster-open-collaboration-in-search-and-analytics - OpenSearch Software Foundation (2024-09-16).
- GitHub API `repos/minio/minio` (archived=true, last push 2026-04-24) and its README commit history (source-only 2025-10-15, maintenance mode 2025-12-03) - MinIO timeline.
- https://istio.io/latest/blog/2024/ambient-reaches-ga/ - Istio ambient GA in 1.24.
- https://webkit.org/blog/17862/webkit-features-for-safari-26-4/ - Safari 26.4 WebTransport (2026-03-24).
- https://docs.stripe.com/api/versioning and https://docs.stripe.com/changelog - Stripe release model (monthly + two majors a year since 2024-09-30.acacia); current version 2026-09-30.endive.
- https://docs.stripe.com/payments/checkout-sessions-and-payment-intents-comparison - Checkout Sessions recommended.
- https://blog.pcisecuritystandards.org/important-updates-announced-for-merchants-validating-to-self-assessment-questionnaire-a and https://blog.pcisecuritystandards.org/faq-clarifies-new-saq-a-eligibility-criteria-for-e-commerce-merchants - SAQ A 2025-01: 6.4.3/11.6.1 removed; script-attack eligibility criterion applies to embedded payment forms, not redirects.
