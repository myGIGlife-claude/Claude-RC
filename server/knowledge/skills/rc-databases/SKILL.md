---
name: rc-databases
description: Databases as of 2026-10 - choosing a store (Postgres, MySQL/MariaDB, SQLite/Turso, SQL Server, MongoDB, Redis/Valkey, Elasticsearch/OpenSearch, DynamoDB, Firestore, Supabase, pgvector, ClickHouse/Timescale), schema modeling, SQL (joins, CTEs, window functions, upserts, keyset pagination, EXPLAIN, indexes), isolation, locking, zero-downtime migrations, RLS, GDPR erasure, backup/PITR, replication, pooling, ORM N+1.
---
# Databases: selection, modeling, SQL, operations (as of 2026-10)

> Every version/date below was checked on 2026-10-09 against the source in "Sources" unless marked (unverified). Recheck before pinning.
> rc-web-backend already has the Postgres 17/18/19 feature table, expand/contract basics, outbox and Postgres-backed queues: this skill goes
> deeper on modeling, SQL, isolation, operations and the other engines. Engine-by-engine detail: `references/engines.md`.
> SQL patterns with examples: `references/sql-patterns.md`.

## Currency check

| Engine | Current (2026-10) | Supported lines / EOL | Licence / notes |
|---|---|---|---|
| PostgreSQL | 18.6 (18 GA 2025-09-25) | 18 to 2030-11-14, 17 to 2029-11-08, 16 to 2028-11-09, 15 to 2027-11-11, **14 EOL 2026-11-12** | PostgreSQL licence. 19 at Beta 4 (2026-09-24), not GA. |
| MySQL | 9.7 LTS (GA 2026-04-21, 9.7.3 2026-08-18) | 9.7 premier to 2031-04, 8.4 LTS premier to 2029-04-30; **8.0 EOL 2026-04-30**; innovation 9.x lines live ~1 quarter | GPLv2 + commercial. `mysql_native_password` removed in 9.0. |
| MariaDB | 12.3 LTS (stable 2026-05-29), 13.0 rolling (2026-09-15) | LTS 11.4 (to 2029-05-29), 11.8 (to 2028-06-04), 10.11 (to 2028-02-16); **10.6 EOL 2026-07** | GPLv2. From 12.3, each major's `.3` is LTS. |
| SQLite | 3.54.0 (2026-10-09) | single line, long-term file-format stability | Public domain. 3.51.3 (2026-03-13) and 3.53.0 (2026-04-09) fixed a WAL-reset corruption bug: be on >= 3.51.3, ideally 3.53+. |
| SQL Server | 2025 (17.x, GA 2025-11) | 2022 mainstream to 2028-01-11; 2019 extended to 2030-01-08; **2016 EOL 2026-07-14** | Commercial; Express now 50 GB per DB; Web edition discontinued. |
| MongoDB | 9.0 (GA 2026-09-28) | 9.0 to 2031-10-31; 8.0 and 8.3 to 2029-10-31; 7.0 to 2027-08-31; 6.0 EOL 2025-07 | SSPL (not OSI). Patch MongoBleed CVE-2025-14847 (8.2.3+ and backports). |
| Redis | 8.10 (8.10.2, 2026-09-17) | latest minor + previous minor + previous major get fixes | 8.x: RSALv2 / SSPLv1 / **AGPLv3** tri-licence; <= 7.2 was BSD. |
| Valkey | 9.1.2 (2026-09-01); 9.2 at RC1 (2026-09-16) | 9.0, 8.1, 8.0 still patched | BSD-3, Linux Foundation fork of Redis 7.2.4. |
| Elasticsearch | 9.5 / 9.4 maintained; 8.19 to 2027-07-15 | | ELv2 / SSPL / **AGPLv3** (AGPL added 2024-08). |
| OpenSearch | 3.9.0 (2026-09-29); 2.19.x maintenance | | Apache-2.0, Linux Foundation. |
| pgvector | 0.8.7 (2026-10-01, IVFFlat buffer-overflow fix) | | PostgreSQL licence. |
| TimescaleDB | 2.30.2 (2026-09-29), PG 16-18 | PG 15 dropped in 2.29 | Apache-2.0 core + TSL (compression, continuous aggregates). |
| ClickHouse | 26.9 stable; LTS 26.8 and 26.3 | | Apache-2.0. |
| Turso (Rust SQLite rewrite) | pre-1.0, MIT | | Replaces libSQL as Turso's direction; keep your own backups. |

What shipped in the last ~12 months (dated):
- PG 18 (2025-09): `uuidv7()`, async I/O, virtual generated columns, `RETURNING OLD/NEW`, `WITHOUT OVERLAPS`, skip scan, stats kept by `pg_upgrade`, checksums on by default, md5 deprecated. PG 19 beta: `REPACK CONCURRENTLY`, `ON CONFLICT DO SELECT`, `WAIT FOR LSN`, SQL/PGQ, JIT off by default.
- SQL Server 2025 (2025-11): `vector` type + `VECTOR_DISTANCE`, native `json` type, `REGEXP_*`, optimized locking, ZSTD backup compression, PBKDF2 password hashes by default, TLS 1.3/TDS 8.0. Vector index, `VECTOR_SEARCH`, fuzzy matching still need `PREVIEW_FEATURES`.
- MongoDB 9.0 (2026-09): per-operation memory limit, WASM-sandboxed server-side JS back, `constraint` validation level, Queryable Encryption prefix/suffix/substring GA, time-series stored as one namespace, `mongocryptd` deprecated.
- MySQL 9.7 LTS (2026-04): first LTS since 8.4; SCRAM-SHA-256 default for SASL LDAP. 8.0 went EOL 2026-04-30.
- MariaDB 12.3 LTS (2026-05), 13.0 rolling (2026-09).
- DynamoDB (2025-11): GSIs take up to 4 partition + 4 sort key attributes (no more concatenated synthetic keys for GSIs).
- Firestore: MongoDB-compatible API GA (2025-08-26); Enterprise edition Native mode + Pipeline queries GA (2026-04-20).
- Supabase: `sb_publishable_...` / `sb_secret_...` keys; legacy `anon`/`service_role` JWT keys deprecated by end of 2026.
- Valkey 9 (2025-10) / 9.1; Redis 8.x bundles the query engine, JSON, time series and vector sets.
- ORMs: SQLAlchemy 2.1 (2026-09-24); Prisma 7 (7.0.0 2025-11-19, 7.10 latest stable) with 8.0 at RC (npm `latest` tag already points at the RC: pin 7.x explicitly); Drizzle 0.45 stable, 1.0 in RC.

### Older versions (legacy projects: do not upgrade unless asked)
- PG 14-16: no `uuidv7()` (use bigint identity or an app-side UUIDv7); no virtual generated columns; `MERGE` from 15, `MERGE ... RETURNING` and `JSON_TABLE` from 17; `NULLS NOT DISTINCT` from 15; md5 still silent. PG 14 dies 2026-11-12: flag it, do not silently upgrade.
- MySQL 8.0 (EOL) / 8.4: 8.4 has `mysql_native_password` disabled by default but loadable; 8.0 still has it enabled. No `VECTOR` type before 9.0. `utf8` means `utf8mb3` on all of them: still write `utf8mb4`.
- MariaDB 10.6/10.11: `UUID` type from 10.7, `VECTOR` type + vector index from 11.7 (GA line: 11.8 LTS), `RETURNING` on INSERT/DELETE (not UPDATE).
- SQL Server 2019/2022: no `json`/`vector` types (JSON is `nvarchar` + `ISJSON`), no `REGEXP_*`; 2022 has `GREATEST/LEAST`, `DATE_BUCKET`, `IS DISTINCT FROM`.
- MongoDB 7.0: no `constraint` validation level; use `validationLevel: "strict"` + `validationAction: "error"`.
- Redis 7.2 or Valkey 7.2/8.x: no hash-field TTL (`HEXPIRE`, `HSETEX`) before Redis 7.4 / Valkey 9.0.

## What changed / stop doing

| Old advice | Do instead | Since |
|---|---|---|
| MySQL `utf8` / `utf8_general_ci` | `utf8mb4` + `utf8mb4_0900_ai_ci` (MySQL) / `utf8mb4_uca1400_ai_ci` (MariaDB default since 11.5; 11.8 is the first LTS with it) | MySQL 8.0 default |
| MyISAM tables | InnoDB (transactions, crash safety, row locks, FKs) | MySQL 5.5 default |
| `mysql_native_password`, Postgres `md5` | `caching_sha2_password` (MySQL); `scram-sha-256` (PG) | MySQL 9.0 removed; PG 18 deprecated |
| MongoDB "schemaless" collections | `$jsonSchema` validators, `validationAction: "error"`; 9.0 `constraint` level | MongoDB 3.6+, 9.0 |
| `OFFSET` pagination on large tables | Keyset: `WHERE (created_at, id) < ($1, $2) ORDER BY created_at DESC, id DESC LIMIT n` | timeless |
| "NoSQL is faster" | Pick by access pattern and consistency; a tuned Postgres handles most OLTP up to TBs | - |
| "UUID PKs fragment indexes, never use them" | Random v4 hurts locality; UUIDv7 / ULID are time-ordered and fine; bigint identity is still smallest | PG 18 `uuidv7()` |
| `SERIAL` / `AUTO_INCREMENT` exposed in URLs | `GENERATED ALWAYS AS IDENTITY` internally; opaque id (UUIDv7) externally if enumeration matters | PG 10 |
| Plaintext PII and passwords "because the disk is encrypted" | Passwords: Argon2id/scrypt/bcrypt in the app; PII: column- or app-level encryption for the sensitive fields; at-rest disk encryption is a baseline, not a control against SQL access | - |
| Soft delete for everything (`deleted = true`) | Decide per table: hard delete + audit log, or soft delete with a partial unique index and a purge job | GDPR Art. 17 |
| Redis is "BSD open source" | Redis 8 = RSAL/SSPL/AGPL; Valkey = BSD; check what your cloud actually runs | 2024-03 / 2025-05 |
| Elasticsearch "not open source" | AGPLv3 option exists since 2024-08; OpenSearch is the Apache-2.0 fork | 2024-08 |
| DynamoDB GSI synthetic keys `TENANT#x#STATUS#y` | Multi-attribute GSI keys (up to 4+4 attributes) | 2025-11 |
| Supabase `anon` / `service_role` keys | `sb_publishable_` (client, RLS applies) / `sb_secret_` (server only, bypasses RLS) | 2025, legacy deprecated end-2026 |
| Separate vector DB by default | pgvector (HNSW) in the DB you already run until > ~10-50 M vectors or strict latency SLOs (rule of thumb) | - |
| `CREATE INDEX` / `ALTER ... NOT NULL` on a live table | `CREATE INDEX CONCURRENTLY`; `CHECK (...) NOT VALID` then `VALIDATE`; MySQL `ALGORITHM=INSTANT/INPLACE, LOCK=NONE` | - |
| `VACUUM FULL` / `pg_repack` to shrink a table | PG 19 `REPACK CONCURRENTLY`; until then `pg_repack` (extension) | PG 19 |

## Do this

### 1. Choose the store by access pattern (decision table)
Default: **one PostgreSQL** (or SQLite for a single-node app/embedded/mobile). Add a second store only when a measured requirement forces it.

| Need | Pick | Not |
|---|---|---|
| General OLTP, relations, constraints, reporting later | PostgreSQL 18 (managed if possible) | MongoDB "for flexibility" |
| Single process / single server / desktop / mobile / edge read replicas | SQLite (WAL) ; Litestream for backup; Turso/libSQL only if you need their sync | Postgres for a CLI tool |
| Existing MySQL/LAMP/WordPress/Laravel shop | MySQL 8.4 or 9.7 LTS / MariaDB 11.8 or 12.3 LTS | Migrating just because |
| Windows/.NET shop with licences, SSRS/SSIS | SQL Server 2022/2025 (Azure SQL if cloud) | |
| Documents with varied shape, nested reads by id, team already knows it | MongoDB 8/9 **with validators**, or Postgres `jsonb` | Mongo for financial ledgers without transactions design |
| Cache, rate limits, sessions, leaderboards, pub/sub | Valkey (BSD) or Redis 8 | Using it as the system of record |
| Full-text relevance, facets, logs at scale | Postgres FTS/`pg_trgm` first; then OpenSearch / Elasticsearch / Meilisearch / Typesense | Elasticsearch as primary DB |
| Serverless key-value at any scale, known access patterns, AWS | DynamoDB | DynamoDB for ad-hoc queries/analytics |
| Mobile/web app with offline sync and direct client access, Firebase stack | Firestore (+ Security Rules) | Firestore for relational reporting |
| Postgres + auth + storage + realtime as a service | Supabase (it is plain Postgres + PostgREST + RLS) | Treating its client SDK as a backend without RLS |
| Embeddings / semantic search | pgvector (HNSW) in your Postgres; dedicated (Qdrant, Milvus, Weaviate, Pinecone) at very large scale | A second DB for 100 k vectors |
| Time-series metrics / events, analytics dashboards | TimescaleDB (in Postgres) for moderate; ClickHouse for large columnar analytics | Row-store OLTP tables for billions of events |
| Deep multi-hop relationship queries (fraud rings, recommendations, permissions graphs) | Postgres recursive CTE / PG 19 SQL/PGQ first; Neo4j or similar if traversals dominate | A graph DB for a normal FK schema |
| Warehouse / BI over many sources | See "Warehouses" below | Running BI on the OLTP primary |

Consistency questions to answer before picking: do you need multi-row/multi-entity atomic transactions? Read-your-writes after failover? Strong
unique constraints (emails, balances)? If yes to any, prefer a relational engine; DynamoDB/Mongo/Firestore can do it but with limits you must design for.

Warehouses (one paragraph): when analytics outgrow a read replica, copy data (CDC via Debezium / logical replication, or the vendor's ELT) into a
columnar store: BigQuery, Snowflake, Redshift, Databricks, or self-hosted ClickHouse / DuckDB for small teams. Model as facts + dimensions; never
point BI tools at the OLTP primary; keep PII out or masked; the warehouse has its own retention and erasure duties.

### 2. Model it
- Start normalized (3NF): one fact in one place, FKs with `ON DELETE` chosen deliberately (`RESTRICT` default; `CASCADE` only for owned children).
- Constraints are the cheapest correctness you will ever get: `NOT NULL`, `CHECK`, `UNIQUE`, FKs, exclusion constraints, PG 18 `WITHOUT OVERLAPS` for bookings.
- Index every FK column used in joins or cascades (Postgres does not do it for you; MySQL InnoDB does).
- Types: `timestamptz`; `numeric`/integer cents for money; `text` + `CHECK (length(x) <= n)`; `boolean` not `char(1)`; enums via lookup table or `CHECK` (PG enums are hard to remove values from).
- Denormalize deliberately and write down why: counters/caches maintained by trigger or app in the same transaction, materialized views refreshed `CONCURRENTLY`, or a read model fed by an outbox. Every denormalized field needs a rebuild script.
- `jsonb` for genuinely variable attributes (settings, vendor payloads), not for fields you filter/join on daily: promote those to columns (generated columns can extract them).
- Multi-tenant: `tenant_id` on every tenant table, first column of composite indexes and unique keys `(tenant_id, email)`, plus RLS (below).
- Ids: bigint identity internally is smallest/fastest; UUIDv7 when ids are generated client-side, merged across shards or exposed. Do not use random UUIDv4 as the clustered PK in MySQL/SQL Server (InnoDB and clustered indexes order the table by PK).

### 3. SQL that is correct and fast (details and examples: `references/sql-patterns.md`)
- Joins: know your cardinality; a join that multiplies rows then `DISTINCT` is a bug. Use `EXISTS` for "has any", not `JOIN ... DISTINCT`. `NOT IN (subquery)` with a NULL returns nothing: use `NOT EXISTS`.
- CTEs: Postgres 12+ inlines non-recursive CTEs (use `MATERIALIZED` to force a fence). Recursive CTEs for trees; always bound depth.
- Window functions for top-N per group, running totals, dedupe (`row_number() OVER (PARTITION BY ... ORDER BY ...)`).
- Upserts: PG `INSERT ... ON CONFLICT (cols) DO UPDATE` (needs a unique index); MySQL `INSERT ... ON DUPLICATE KEY UPDATE` (alias syntax `AS new`; `VALUES()` deprecated since 8.0.20); SQL Server `MERGE` has known race issues: use `UPDATE ... ; IF @@ROWCOUNT = 0 INSERT` inside `SERIALIZABLE`/`UPDLOCK, HOLDLOCK`.
- Pagination: keyset with a unique tie-breaker and a matching composite index; offset only for small, bounded admin lists.
- Read plans: PG `EXPLAIN (ANALYZE, BUFFERS)` (careful: ANALYZE executes; wrap writes in `BEGIN; ... ROLLBACK;`); MySQL `EXPLAIN ANALYZE` / `EXPLAIN FORMAT=TREE`; SQL Server actual plan + Query Store. Look for: estimated vs actual rows off by 10x+ (stale stats -> `ANALYZE`), Seq Scan on big tables inside loops, sorts spilling to disk, `Rows Removed by Filter` large.
- Index design (PG): B-tree (equality then range columns), multicolumn in query order, partial (`WHERE deleted_at IS NULL`, `WHERE status = 'pending'`), covering (`INCLUDE (...)`) for index-only scans, expression (`lower(email)`), GIN for `jsonb @>`, arrays, FTS, `pg_trgm`; BRIN for huge append-only tables ordered by time; HNSW for vectors. Every index costs writes and WAL: drop unused ones (`pg_stat_user_indexes.idx_scan = 0` over a full business cycle).

### 4. Transactions, isolation, locking
| Level | PG | MySQL InnoDB | Anomalies still possible |
|---|---|---|---|
| READ COMMITTED | default | available | non-repeatable reads, phantoms, lost update via read-modify-write, write skew |
| REPEATABLE READ | snapshot; errors `40001` on concurrent update | **default**; consistent reads but locking reads see latest rows | PG: write skew. MySQL: write skew, lost updates if you read without `FOR UPDATE` |
| SERIALIZABLE | SSI: true serializability, retry on `40001` | plain SELECTs become `SELECT ... FOR SHARE` (locking) when autocommit is off | none (but retries/deadlocks) |
SQL Server default is READ COMMITTED with locking; turn on `READ_COMMITTED_SNAPSHOT` for new databases (Azure SQL has it on).
- Lost update fix: `UPDATE t SET qty = qty - 1 WHERE id = $1 AND qty > 0` (atomic), or `SELECT ... FOR UPDATE`, or optimistic `version` column (`WHERE version = $v`, check rows affected).
- Write skew (two doctors go off-call): SERIALIZABLE + retry loop, or lock a shared parent row, or a constraint.
- Every write path that uses SERIALIZABLE/REPEATABLE READ must retry on `40001`/`40P01` (PG) or 1213/1205 (MySQL) with backoff, a bounded number of times.
- Deadlocks: lock rows in a consistent order (sort ids), keep transactions short, index FK columns, no user think-time or HTTP calls inside a transaction. Read the deadlock log (PG `log_lock_waits`, MySQL `SHOW ENGINE INNODB STATUS`).
- Advisory locks (`pg_advisory_xact_lock`) for "only one worker does X"; job queues use `FOR UPDATE SKIP LOCKED`.

### 5. Migrations without downtime
- Expand -> migrate -> contract across at least two deploys; the old app version must work against the new schema.
- PG: `SET lock_timeout = '3s'` + retry; `CREATE INDEX CONCURRENTLY` (not in a transaction; check `indisvalid` after failure); add FKs/CHECKs `NOT VALID` then `VALIDATE`; `NOT NULL` via validated `CHECK (col IS NOT NULL)` then `SET NOT NULL` (PG 12+ skips the scan); `ADD COLUMN ... DEFAULT <constant>` is instant (PG 11+), volatile defaults rewrite.
- Renames are breaking: add new column, dual-write, backfill in batches (1-10 k rows, commit each, sleep), switch reads, drop later.
- MySQL: many `ADD COLUMN` are `ALGORITHM=INSTANT` (8.0.29+ any position); state `ALGORITHM=..., LOCK=NONE` so it fails instead of silently copying; big changes via `gh-ost` or `pt-online-schema-change`.
- Lint DDL in CI (squawk for PG; Atlas lint), run migrations against a prod-sized copy, and keep a forward-fix plan (down migrations rarely survive real data).

### 6. Deletion, retention, GDPR erasure
- Write a retention table per data class (what, why, how long, legal basis) before the schema; collect only fields the feature needs.
- Soft delete (`deleted_at`) is for undo windows, not erasure. Add partial unique indexes `WHERE deleted_at IS NULL`, filter in RLS/views, and purge after N days.
- Erasure request: delete or irreversibly anonymize rows in the DB, search indexes, caches, analytics copies, logs and object storage; backups expire on their normal schedule (document it) and restores must re-apply an erasure ledger (ids to re-delete).
- Crypto-shredding: encrypt a user's sensitive fields with a per-user key; deleting the key erases data everywhere including backups.
- Keep an audit log separate, minimal (ids, action, time), with its own retention.

### 7. Operations
- Backup = base backup + WAL/binlog archive for PITR (pgBackRest, WAL-G, Barman; MySQL `mysqlbackup`/XtraBackup + binlogs; SQL Server full + diff + log backups; Mongo Atlas continuous backup or `mongodump` only for small DBs). `pg_dump`/`mysqldump` are logical exports, not PITR.
- **Test restores** on a schedule: restore to a scratch instance, run row-count and checksum queries, record actual RTO/RPO. An untested backup is a hope.
- Replication: async streaming replicas for read scaling and failover; sync (`synchronous_standby_names`, semi-sync in MySQL) when you cannot lose committed writes. Replicas lag: route read-your-writes reads to the primary or wait for LSN (PG 19 `WAIT FOR LSN`).
- Failover: use a managed service or Patroni (PG) / Orchestrator or InnoDB Cluster (MySQL) / Always On AG (SQL Server) / replica sets (Mongo). Fence the old primary; apps reconnect via a stable endpoint and retry.
- Partitioning (single node): PG declarative range/list/hash partitions for time-based retention (drop a partition instead of `DELETE`) and very large tables; every query must include the partition key to prune. Sharding (multi node: Citus, Vitess, app-level): last resort, after vertical scale, replicas, partitioning and archiving; choose a shard key that keeps transactions on one shard (usually `tenant_id`).
- Pooling: PgBouncer transaction mode (no session state: use `SET LOCAL`, avoid session advisory locks/`LISTEN`), or provider poolers (Supabase Supavisor, RDS Proxy). Total server connections ~ a few per CPU core; app pool per instance small (5-20). MySQL handles more connections but still pool (ProxySQL).
- Timeouts everywhere: `statement_timeout`, `idle_in_transaction_session_timeout`, `lock_timeout` (PG); `max_execution_time`, `innodb_lock_wait_timeout` (MySQL); client query timeouts.

### 8. When NOT to add a dependency
- No second database until Postgres/SQLite has been measured failing the requirement. No Redis for a cache that fits in process memory on one node. No ORM plugin for soft delete, audit or multi-tenancy when a view, trigger or RLS policy does it. No separate search/vector DB under ~a few million docs (rule of thumb). No migration framework beyond the one your framework already ships.

### 9. ORMs and query builders
| Ecosystem | Common choice (2026-10) | Pitfall to check |
|---|---|---|
| TypeScript | Prisma 7, Drizzle 0.45 (1.0 RC), Kysely, TypeORM, MikroORM | Prisma: N+1 via nested awaits in loops, use `include`/`relationLoadStrategy: "join"`; `prisma migrate dev` must never run in prod (`migrate deploy`); `db push` is not a migration. |
| Python | SQLAlchemy 2.1 / 2.0, Django ORM, SQLModel | Lazy loading in loops: `selectinload`/`joinedload`; Django `select_related`/`prefetch_related`; async SQLAlchemy raises on implicit lazy loads. Alembic autogenerate misses renames (emits drop+add). |
| Java/Kotlin | Hibernate/JPA, jOOQ, Spring Data JDBC, Exposed | `FetchType.EAGER` and open-session-in-view hide N+1; use fetch joins / entity graphs; Flyway/Liquibase for DDL, never `ddl-auto=update` in prod. |
| .NET | EF Core 10 (unverified GA 2025-11), Dapper | Lazy-loading proxies N+1; `AsNoTracking` for reads; split queries for big includes; `EnsureCreated` is not migrations. |
| Go | `database/sql` + sqlc, pgx, GORM, ent | GORM silently ignores zero values on update and `AutoMigrate` is not a migration tool; close `rows`. |
| PHP | Eloquent (Laravel), Doctrine | `with()` eager loading; `Model::preventLazyLoading()` in dev. |
| Ruby | Active Record | `includes`/`preload`; `strict_loading`. |
Universal: log SQL in dev, assert query counts in tests for list endpoints, keep raw SQL parameterized, review generated migrations by hand.

## Security
- Injection: parameters only (`$1`, `?`, named binds); never concatenate identifiers from input: whitelist them. ORMs' `raw`/`$queryRawUnsafe`/`extra()` are injection points.
- Least privilege: separate roles for migration (DDL owner), app (DML on its schema), read-only/analytics, backup. App role does not own tables, is not superuser/`rds_superuser`, has no `CREATE` on `public` (PG 15+ default). `REVOKE ALL ... FROM PUBLIC` on sensitive functions.
- RLS (Postgres): `ALTER TABLE t ENABLE ROW LEVEL SECURITY` **and** `FORCE ROW LEVEL SECURITY` (otherwise the table owner bypasses it); policies `TO app_role` with both `USING` and `WITH CHECK`; set tenant per transaction (`SET LOCAL app.tenant_id = ...` or `set_config(..., true)`); views need `WITH (security_invoker = true)` (PG 15+) or they run as their owner; `SECURITY DEFINER` functions bypass RLS: pin `search_path`. Test RLS with a non-owner role.
- Supabase specifics: any table in an exposed schema without RLS is world-readable via the publishable key; enable RLS on every table; wrap `auth.uid()` as `(select auth.uid())` in policies (evaluated once per statement) and index the filtered columns; check `auth.uid() IS NOT NULL`; never trust `user_metadata` in policies (users can edit it; use `app_metadata`); `sb_secret_`/`service_role` bypass RLS and must stay server-side; run the Security Advisor.
- Firestore: Security Rules are the only server-side check for client access; default-deny, validate fields and ownership in rules, test with the emulator. DynamoDB: IAM fine-grained access (`dynamodb:LeadingKeys`) per tenant, not app trust alone.
- Encryption in transit: require TLS (`sslmode=verify-full` for PG clients; MySQL `require_secure_transport=ON`; SQL Server TDS 8.0 strict). Disable plain-text listeners for Redis/Valkey (`tls-port`, `requirepass`/ACL users, never `protected-mode no` on a public IP), Mongo `net.tls.mode: requireTLS` + auth enabled.
- At rest: managed disk/TDE encryption on, keys in a KMS, backups encrypted with separate keys; column/app-level encryption (or Mongo Queryable Encryption / SQL Server Always Encrypted) for high-risk fields (national ids, health data).
- Never expose a database port to the internet; private network + bastion/SSM; rotate credentials (IAM auth / short-lived tokens where offered).
- Exposed MongoDB, Elasticsearch, Redis instances remain a top data-leak source: bind to private addresses and enable auth from day one.

## Performance & quality
- Targets (typical OLTP): p95 query < 10-50 ms for indexed lookups; cache hit ratio > 99% (PG `pg_stat_database`); no query > 1 s on hot paths; replica lag < 1-5 s; connection pool wait ~0; autovacuum keeping dead tuples < ~10-20% on hot tables.
- Tools: `pg_stat_statements` (top total time), `auto_explain` for slow queries, `pg_stat_user_tables` (seq scans, dead tuples), `pgBadger`; MySQL performance_schema + `sys` schema, slow query log; SQL Server Query Store; Mongo profiler + `explain("executionStats")` + `$queryStats`.
- Bloat/vacuum: tune autovacuum per hot table (`autovacuum_vacuum_scale_factor`), watch transaction ID age (`age(datfrozenxid)`), avoid long-running transactions (they block vacuum everywhere).
- Load test with realistic data volume and distribution; a plan on 1 k rows says nothing about 100 M.

## Testing & tooling
- Integration tests on the real engine and version via Testcontainers (or the framework's DB test harness), never SQLite standing in for Postgres or mocks of the DB.
- Each test in a transaction rolled back, or a template DB clone (PG `CREATE DATABASE ... TEMPLATE`) for speed.
- Migration CI: apply all migrations on an empty DB and on a prod-schema snapshot; lint (squawk, Atlas); check for long locks.
- RLS tests: pgTAP or app tests that connect as the app role and assert cross-tenant reads return 0 rows and writes fail.
- Formatting/linting SQL: sqlfluff (unverified current version); schema diff: Atlas, migra-style tools.
- Supabase: `supabase db lint`, Security/Performance Advisors, pgTAP tests in `supabase/tests/`.

## Common mistakes in AI-written code
- `OFFSET` pagination, `ORDER BY` without a unique tie-breaker, `COUNT(*)` on every page of a huge table.
- `SELECT *` in app queries; `NOT IN` with nullable subqueries; `BETWEEN` on timestamps (use `>= start AND < end`).
- Read-modify-write in app code without a lock/version (lost updates); no retry on serialization failures.
- `CREATE INDEX` without `CONCURRENTLY`, `ALTER TABLE` without `lock_timeout`, single-transaction backfill of millions of rows.
- Missing indexes on FK columns; indexing every column; composite index columns in the wrong order (range column first).
- Storing money as float, timestamps without zone, emails without a case-insensitive unique index (`citext` or `lower(email)`).
- MySQL `utf8`, MyISAM, `mysql_native_password`, `VALUES()` in upserts; Postgres `md5`, `SERIAL`, `uuid_generate_v4()` PKs.
- Mongo without validators or indexes, unbounded arrays growing in one document (16 MB limit), `$lookup` everywhere (you wanted relational).
- DynamoDB `Scan` in request paths, hot partition keys (date as PK), copying a single-table design from a blog without listing access patterns first.
- Supabase: tables without RLS, `service_role` key in the client bundle, policies reading `user_metadata`, views without `security_invoker`.
- Redis/Valkey used as the source of truth without persistence understood (RDB/AOF settings), `KEYS *` in production (use `SCAN`).
- Elasticsearch/OpenSearch treated as transactional primary storage; mapping explosions from dynamic mappings on user JSON.
- pgvector: no index (exact scan), IVFFlat built on an empty table (build after loading data), filtered ANN queries returning too few rows (enable `hnsw.iterative_scan`), distance operator not matching the index opclass.
- Invented APIs: `pg_dump --pitr`, "Postgres `UPSERT` statement", `ON CONFLICT` without a matching unique index, MySQL `RETURNING` (MySQL has none; MariaDB does on INSERT/DELETE).

## Before you ship
- [ ] Store choice justified by access patterns; one DB unless measured otherwise.
- [ ] Constraints (NOT NULL, FK, UNIQUE, CHECK) in the schema; FK columns indexed; money/time types right.
- [ ] Every list endpoint uses keyset pagination with an index that matches `ORDER BY`; `EXPLAIN ANALYZE` checked on prod-like data.
- [ ] Concurrency: lost-update and write-skew paths identified; locks/versions/SERIALIZABLE with retries in place.
- [ ] Migrations expand/contract, `CONCURRENTLY`, `lock_timeout`, linted, tested on a prod-sized copy; rollback/forward-fix written.
- [ ] Roles least-privilege; RLS enabled + forced where multi-tenant; no secret keys client-side; TLS required.
- [ ] Sensitive fields encrypted or not stored; retention + erasure path covers DB, caches, search, analytics, logs, backups.
- [ ] Backups with PITR, encrypted, off-account copy, and a restore test passed this month; RPO/RTO written down.
- [ ] Pooling and timeouts configured; slow-query logging and `pg_stat_statements` (or equivalent) on; alerts on replica lag, disk, connections, XID age.
- [ ] Engine on a supported version (PG 14 EOL 2026-11-12, MySQL 8.0 EOL 2026-04-30, SQL Server 2016 EOL 2026-07-14, MariaDB 10.6 EOL 2026-07).

## Sources
All accessed 2026-10-09.
- https://www.postgresql.org/support/versioning/ - PG supported versions, minors, EOL dates; 19 Beta 4.
- https://www.postgresql.org/about/news/postgresql-18-released-3142/ - PG 18 features (uuidv7, AIO, virtual generated columns, RETURNING OLD/NEW, WITHOUT OVERLAPS, skip scan, checksums, md5 deprecation).
- https://www.postgresql.org/about/news/postgresql-19-beta-1-released-3313/ - PG 19 features (REPACK, ON CONFLICT DO SELECT, WAIT FOR LSN, SQL/PGQ, JIT off, RADIUS removed).
- https://endoflife.date/mysql - MySQL 9.7/8.4/8.0 dates and LTS status.
- https://dev.mysql.com/doc/relnotes/mysql/9.7/en/ - MySQL 9.7.0 GA 2026-04-21, 9.7.3 2026-08-18.
- https://dev.mysql.com/doc/refman/9.7/en/mysql-nutshell.html - MySQL 9.7 changes (SCRAM-SHA-256 default, removed variables).
- https://dev.mysql.com/doc/relnotes/mysql/9.7/en/news-9-0-0.html - `mysql_native_password` removed in 9.0, disabled in 8.4.
- https://mariadb.org/about/#maintenance-policy - MariaDB LTS policy and 11.8/11.4/10.11 EOL dates.
- https://mariadb.org/mariadb/all-releases/ - MariaDB 12.3 stable 2026-05-29, 13.0 stable 2026-09-15, latest patches.
- https://endoflife.date/mariadb - 12.3 LTS EOL 2029-06, 10.6 EOL 2026-07.
- https://www.sqlite.org/changes.html - SQLite 3.54.0 / 3.53.0 dates and WAL-reset fix.
- https://learn.microsoft.com/en-us/sql/sql-server/what-s-new-in-sql-server-2025 - SQL Server 2025 features, editions, preview features.
- https://endoflife.date/mssqlserver - SQL Server support dates.
- https://www.mongodb.com/docs/manual/release-notes/9.0/ - MongoDB 9.0 GA 2026-09-28 and features.
- https://www.mongodb.com/legal/support-policy/lifecycles - MongoDB EOL dates.
- https://endoflife.date/mongodb - MongoBleed CVE-2025-14847 note.
- https://github.com/redis/redis/releases - Redis 8.10.2 (2026-09-17) and maintained lines.
- https://endoflife.date/redis - Redis licensing by version and support policy.
- https://github.com/valkey-io/valkey/releases - Valkey 9.1.2, 9.2 RC.
- https://www.elastic.co/blog/elasticsearch-is-open-source-again - Elasticsearch AGPL option (2024-08-29).
- https://endoflife.date/elasticsearch - Elasticsearch maintained lines.
- https://endoflife.date/opensearch - OpenSearch 3.9.0, 2.19 maintenance.
- https://github.com/pgvector/pgvector/blob/master/CHANGELOG.md - pgvector 0.8.x dates and fixes.
- https://github.com/timescale/timescaledb/releases - TimescaleDB 2.30.2, supported PG versions, licences.
- https://github.com/ClickHouse/ClickHouse/releases - ClickHouse 26.9 stable, 26.8/26.3 LTS.
- https://github.com/tursodatabase/turso - Turso status (pre-1.0, MIT, replaces libSQL direction).
- https://aws.amazon.com/about-aws/whats-new/2025/11/amazon-dynamodb-multi-attribute-composite-keys-global-secondary-indexes/ - DynamoDB multi-attribute GSI keys.
- https://docs.cloud.google.com/firestore/docs/release-notes - Firestore Enterprise/Pipeline GA 2026-04-20.
- https://cloud.google.com/blog/products/databases/firestore-with-mongodb-compatibility-is-now-ga - Firestore MongoDB compatibility GA.
- https://supabase.com/docs/guides/api/api-keys - Supabase publishable/secret keys, legacy deprecation.
- https://supabase.com/docs/guides/database/postgres/row-level-security - Supabase RLS pitfalls and performance.
- https://www.sqlalchemy.org/ - SQLAlchemy 2.1.4 / 2.0.54.
- https://github.com/drizzle-team/drizzle-orm/releases - Drizzle 0.45.4 stable, 1.0 RC.
- https://registry.npmjs.org/prisma - Prisma 7.0.0 published 2025-11-19, 7.10.0 latest stable, 8.0.0 only as RC (`latest` tag = RC).
- https://valkey.io/ and https://github.com/valkey-io/valkey/releases/tag/9.0.0 - Valkey 9.0.0 (2025-10-21) hash-field expiration commands.
