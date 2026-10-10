# Engine notes (as of 2026-10; versions verified 2026-10-09, see SKILL.md Sources)

## PostgreSQL
- Default choice. Version feature table (17/18/19) lives in rc-web-backend `references/data-and-auth.md`; do not duplicate here.
- Config that matters first: `shared_buffers` ~25% RAM, `effective_cache_size` ~50-75%, `work_mem` small (per sort node per connection), `maintenance_work_mem` larger, `random_page_cost` ~1.1 on SSD, `max_connections` low + pooler, `log_min_duration_statement`, `pg_stat_statements`.
- Extensions worth knowing: `pg_stat_statements`, `pg_trgm`, `citext`, `pgcrypto`, `pgvector`, `postgis`, `pg_partman`, `pg_cron`, `timescaledb`, `citus`. Managed providers limit which ones you can install: check before designing around one.
- Upgrades: `pg_upgrade --link` (minutes) or logical replication blue/green (seconds). PG 18 keeps planner stats across `pg_upgrade`; older targets need `vacuumdb --analyze-in-stages` after.

## MySQL / MariaDB
- MySQL lines: 8.4 LTS and 9.7 LTS for production; 9.x innovation releases (quarterly, ~3-month life) only if you upgrade every quarter. 8.0 is EOL (2026-04-30).
- MariaDB is no longer a drop-in MySQL replacement: diverged JSON (stored as LONGTEXT), GTID format, auth plugins, optimizer. Pick one and test with it. LTS: 10.11, 11.4, 11.8, 12.3; 13.0 is a rolling release.
- Must-haves: InnoDB only, `utf8mb4` everywhere (server, database, table, connection), `sql_mode` strict (default since 5.7), `innodb_buffer_pool_size` ~60-75% RAM on a dedicated host, binlog `ROW` format + GTIDs for replication/PITR.
- Clustered PK: the table is stored in PK order; random UUID PKs cause page splits. Use `BIGINT AUTO_INCREMENT` or time-ordered UUIDs stored as `BINARY(16)` (`UUID_TO_BIN(uuid, 1)` swaps time bits for v1 UUIDs; for v7 store as-is).
- No `RETURNING` in MySQL (MariaDB has it for INSERT/DELETE). No transactional DDL: a failed multi-statement migration leaves partial state; one DDL per migration step.
- HA: InnoDB Cluster / Group Replication, or managed (RDS/Aurora, Cloud SQL). Online DDL tools: gh-ost, pt-online-schema-change.
- MySQL 9.x adds a `VECTOR` type; vector search functions are limited in Community edition (unverified details: check docs before relying on it). MariaDB: `VECTOR(N)` + `VECTOR INDEX` from 11.7, GA in 11.8 LTS.

## SQLite (and libSQL/Turso)
- Production-ready for: single-server web apps, embedded, mobile, desktop, edge caches, tests of SQLite-specific code. Not for: many writers across machines.
- Per connection: `PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;`. Use `BEGIN IMMEDIATE` for write transactions to avoid `SQLITE_BUSY` upgrades mid-transaction. One writer at a time: serialize writes in the app (single writer connection) for predictability.
- `STRICT` tables (3.37+) for type enforcement; otherwise columns accept anything.
- Backups: Litestream (continuous WAL shipping to S3), `VACUUM INTO` or the backup API for snapshots; never copy the file while it is being written without the WAL.
- Upgrade to >= 3.51.3 (2026-03-13) or 3.53.0 (2026-04-09): both fixed a WAL-reset corruption bug; 3.52.x is not fixed.
- libSQL (fork with server mode, embedded replicas) vs Turso Database (Rust rewrite, MIT, pre-1.0, concurrent writes, vector search): Turso says the rewrite replaces libSQL as their direction. For new production use, plain SQLite unless you need their sync/replication features; keep independent backups.

## Microsoft SQL Server / Azure SQL
- 2025 (17.x) current; 2022 widely deployed; 2016 out of support 2026-07-14.
- Turn on `READ_COMMITTED_SNAPSHOT` (row versioning) to stop readers blocking writers; Query Store on (default in 2022+); ADR (accelerated database recovery).
- Clustered index = table order: prefer narrow, ever-increasing keys (`bigint IDENTITY`, `NEWSEQUENTIALID()`); `NEWID()` clustered keys fragment.
- `NVARCHAR` for Unicode or UTF-8 collations (`_UTF8`, 2019+) with `VARCHAR`.
- Parameter sniffing: 2022+ PSP optimization, 2025 optional parameter plan optimization; otherwise `OPTION (RECOMPILE)` selectively.
- Connection: `Encrypt=Strict` (TDS 8.0) and validate the certificate; `TrustServerCertificate=True` is for local dev only.
- Express: 50 GB per database in 2025 (10 GB before). Developer editions are free for non-production only.

## MongoDB
- Use 8.0 (long support to 2029-10-31) or 9.0 (to 2031-10-31) for new work; 7.0 ends 2027-08-31. Atlas follows majors; minors (8.1-8.3) are for Atlas auto-upgrade / Enterprise.
- Model by access pattern: embed what is read together and bounded; reference what grows unbounded or is shared. 16 MB document limit.
- Always: schema validation (`$jsonSchema`, `validationAction: "error"`), indexes per query shape (ESR rule: Equality, Sort, Range), `writeConcern: majority` for important writes, `readConcern: majority` / causal sessions when you need read-your-writes.
- Multi-document transactions exist (since 4.0) but cost more; 9.0 caps concurrent multi-document transactions (default 10,000). Design so most writes touch one document.
- Security: auth on, TLS required, bind to private IP; patch MongoBleed CVE-2025-14847 (unauthenticated memory read) on any self-hosted instance.
- Licence: SSPL; managed alternatives with Mongo-compatible APIs (Firestore with MongoDB compatibility, Amazon DocumentDB, FerretDB on Postgres) are not 100% compatible: test the operators you use.

## Redis vs Valkey
- Valkey: BSD-3, Linux Foundation, default on several clouds (ElastiCache/MemoryDB offer it). Redis 8: tri-licence RSALv2/SSPLv1/AGPLv3, ships query engine, JSON, time series, probabilistic types and vector sets in core.
- Pick Valkey when licence clarity matters or your cloud defaults to it; Redis 8 when you need its bundled modules (Redis Query Engine, vector sets) and AGPL/RSAL is acceptable. Wire protocol and core commands remain compatible for common use (verify module commands).
- Persistence: RDB snapshots + AOF `everysec` if you care about data; otherwise treat as a cache. `maxmemory` + eviction policy (`allkeys-lru` for caches, `noeviction` for queues/locks).
- Never `KEYS *` in prod (use `SCAN`); avoid big keys (> ~1 MB) and huge collections; set TTLs; ACL users instead of one shared password; TLS.
- Distributed locks: a single-instance `SET key val NX PX ttl` with a fencing token is fine for efficiency locks; for correctness-critical locks use the database.

## Elasticsearch / OpenSearch
- Search/log store, not the system of record: rebuildable from the primary DB via CDC/outbox.
- Elasticsearch 9.x (9.5/9.4 maintained, 8.19 until 2027-07-15); licences ELv2/SSPL/AGPLv3. OpenSearch 3.x (Apache-2.0); 2.19 in maintenance. APIs diverged after the 7.10 fork: clients are not interchangeable at major versions.
- Explicit mappings (`dynamic: strict` or `false` for user JSON) to avoid mapping explosion; index aliases for zero-downtime reindex; ILM/ISM for time-based indices; security on (TLS + auth) always.
- Lighter options when you do not need a cluster: Postgres FTS + `pg_trgm`, Meilisearch, Typesense.

## DynamoDB
- Design from a written list of access patterns; if you cannot list them, you want SQL.
- Single-table design is an optimization, not a requirement: AWS docs also endorse multiple tables. It pays off when items are fetched together in one Query; it costs readability, analytics and evolving patterns.
- Partition key must spread load (no date-only or status-only PKs); sort key for ranges. GSIs are eventually consistent; since 2025-11 GSIs can use up to 4 partition + 4 sort key attributes (equality on all PK parts, range only on the last sort part).
- Transactions: `TransactWriteItems` (up to 100 items, unverified current limit), conditional writes for uniqueness/optimistic locking. Streams + Lambda for derived views.
- On-demand capacity unless traffic is steady and you have measured; enable PITR (35 days) and deletion protection; export to S3 for analytics instead of `Scan`.

## Firestore
- Editions: Standard and Enterprise; Enterprise adds Pipeline queries (GA 2026-04-20) and the MongoDB-compatible API (GA 2025-08-26).
- Clients talk to it directly: Security Rules are your authorization layer. Default deny, validate `request.auth.uid` ownership and field types, limit query shapes in rules, test with the emulator in CI.
- Model for reads: denormalize into documents per screen, subcollections for unbounded children, avoid hot single documents (write rate per document is limited: shard counters).
- Composite indexes must be declared (`firestore.indexes.json`); deploy them with the rules.
- Enable PITR and scheduled backups; data residency is fixed at database creation.

## Supabase
- What it is: managed Postgres + PostgREST (auto REST), GoTrue auth, Realtime, Storage, Edge Functions, Supavisor pooler. Everything is Postgres roles and RLS: `anon` and `authenticated` roles hit your tables through the API.
- Keys: `sb_publishable_...` in clients (RLS applies), `sb_secret_...` only on servers (bypasses RLS). Legacy `anon`/`service_role` JWT keys are deprecated by end of 2026: migrate.
- RLS pitfalls: tables without RLS in an exposed schema are readable/writable by anyone with the publishable key; views run as owner unless `security_invoker = true`; `SECURITY DEFINER` functions in exposed schemas are callable via RPC; `auth.uid()` returns NULL for anon (guard it); wrap as `(select auth.uid())`; index policy columns; never base policies on `raw_user_meta_data`/`user_metadata`.
- Migrations via the Supabase CLI (`supabase migration new`, `supabase db push`), not dashboard edits in production; pgTAP tests; run Security and Performance Advisors before release.
- Connection: direct (5432) for migrations/long sessions; pooler transaction mode for serverless (no prepared statements unless the driver handles it).

## Vectors
- pgvector 0.8.7: `vector` (up to 2,000 dims indexed), `halfvec` (up to 4,000 indexed), `sparsevec`, `bit`. HNSW: better recall/speed trade-off, slower build, more memory, can be built on an empty table. IVFFlat: faster build, less memory, build after data is loaded and choose `lists` ~ rows/1000 (up to 1 M rows), needs periodic rebuild as data drifts.
- Filtered search: enable iterative scans (`SET hnsw.iterative_scan = relaxed_order;`, 0.8.0+) or use partial indexes per tenant/category; otherwise the LIMIT may return too few rows.
- Match distance operator to opclass (`<=>` cosine with `vector_cosine_ops`, `<->` L2, `<#>` inner product). Store the embedding model name/version per row; re-embed on model change.
- Dedicated vector DBs (Qdrant, Milvus, Weaviate, Pinecone) or OpenSearch k-NN when vectors outgrow one Postgres node's RAM or you need hybrid search features out of the box. SQL Server 2025 and MySQL 9 have vector types too (SQL Server's ANN index still preview).

## Graph
- First try: adjacency table + recursive CTE (bounded depth), closure tables for read-heavy hierarchies, `ltree` for paths. PG 19 adds SQL/PGQ property-graph queries (beta at time of writing).
- A graph DB (Neo4j, Memgraph, Amazon Neptune) earns its place when most queries are variable-length traversals over many hops on large graphs. Keep it as a derived store if the system of record is relational.

## Time-series and analytics
- TimescaleDB 2.30 (PG 16-18): hypertables, compression, continuous aggregates (TSL-licensed features; Apache-only builds lack them; managed clouds may ship the Apache build).
- ClickHouse (26.x, LTS 26.3 / 26.8): columnar, very fast aggregations over billions of rows; append-mostly; updates/deletes are expensive mutations (lightweight deletes exist); no multi-row transactions in the OLTP sense. Feed it via Kafka/CDC; keep OLTP in Postgres/MySQL.
- Plain Postgres with BRIN + range partitions by month handles moderate time-series well; drop old partitions for retention.
