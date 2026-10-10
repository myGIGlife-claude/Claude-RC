# SQL patterns (PostgreSQL syntax unless noted; as of 2026-10)

Own minimal examples. Test each on your engine/version; dialect notes inline.

## Keyset pagination
```sql
CREATE INDEX CONCURRENTLY orders_tenant_created_idx
  ON orders (tenant_id, created_at DESC, id DESC);

-- first page
SELECT id, created_at, total_cents FROM orders
WHERE tenant_id = $1
ORDER BY created_at DESC, id DESC LIMIT 50;

-- next page: cursor = last row's (created_at, id)
SELECT id, created_at, total_cents FROM orders
WHERE tenant_id = $1 AND (created_at, id) < ($2, $3)
ORDER BY created_at DESC, id DESC LIMIT 50;
```
- Row-value comparison works in PG, MySQL 8+, SQLite 3.15+; SQL Server needs `created_at < @c OR (created_at = @c AND id < @i)`.
- Mixed sort directions break row comparison: expand the OR form.
- Total counts: show "more" instead, or an estimate (`reltuples`), or cache counts.

## Top-N per group, dedupe
```sql
SELECT * FROM (
  SELECT o.*, row_number() OVER (PARTITION BY customer_id ORDER BY created_at DESC) AS rn
  FROM orders o
) x WHERE rn <= 3;

-- delete duplicates keeping the lowest id (PG syntax; destructive: run the
-- matching SELECT first, inside BEGIN, and check the row count before COMMIT)
DELETE FROM contacts c USING contacts d
WHERE c.email = d.email AND c.id > d.id;
```
PG alternative for top-1: `SELECT DISTINCT ON (customer_id) ... ORDER BY customer_id, created_at DESC`. For top-N on large tables, a `LATERAL` join with `LIMIT` per group using an index beats the window.

## Running totals and gaps
```sql
SELECT day, amount,
       sum(amount) OVER (ORDER BY day ROWS UNBOUNDED PRECEDING) AS running,
       amount - lag(amount) OVER (ORDER BY day) AS delta
FROM daily_revenue;
```
Specify `ROWS` explicitly: the default frame is `RANGE`, which groups ties.

## Recursive CTE (tree), bounded
```sql
WITH RECURSIVE sub AS (
  SELECT id, parent_id, 1 AS depth FROM categories WHERE id = $1
  UNION ALL
  SELECT c.id, c.parent_id, s.depth + 1
  FROM categories c JOIN sub s ON c.parent_id = s.id
  WHERE s.depth < 20
) SELECT * FROM sub;
```
PG 14+ has `CYCLE ... SET ... USING` to stop on cycles.

## Upserts
```sql
-- PG: needs a unique index/constraint on (tenant_id, sku)
INSERT INTO stock (tenant_id, sku, qty) VALUES ($1, $2, $3)
ON CONFLICT (tenant_id, sku) DO UPDATE SET qty = stock.qty + EXCLUDED.qty
RETURNING qty;
```
- `DO NOTHING ... RETURNING` returns no row on conflict; PG 19 adds `ON CONFLICT DO SELECT ... RETURNING` for get-or-create.
- MySQL 8.0.19+: `INSERT ... VALUES (...) AS new ON DUPLICATE KEY UPDATE qty = qty + new.qty` (the `VALUES(col)` function is deprecated). Beware: any unique key collision triggers the update, not only the one you meant.
- SQLite 3.24+: `ON CONFLICT (...) DO UPDATE SET ... excluded.col`.
- SQL Server: `MERGE` needs `WITH (HOLDLOCK)` to avoid duplicate-key races; the update-then-insert pattern under `UPDLOCK, SERIALIZABLE` is simpler.

## Atomic counters, optimistic locking
```sql
UPDATE accounts SET balance = balance - $2
WHERE id = $1 AND balance >= $2;            -- check rows affected = 1

UPDATE docs SET body = $2, version = version + 1
WHERE id = $1 AND version = $3;             -- 0 rows = someone else won: reload
```

## Job queue claim
```sql
WITH next AS (
  SELECT id FROM jobs WHERE state = 'ready' AND run_at <= now()
  ORDER BY run_at LIMIT 10 FOR UPDATE SKIP LOCKED
)
UPDATE jobs j SET state = 'running', locked_at = now()
FROM next WHERE j.id = next.id RETURNING j.*;
```
Partial index: `CREATE INDEX ON jobs (run_at) WHERE state = 'ready';`. MySQL 8.0+ also supports `SKIP LOCKED`.

## JSON columns
```sql
-- containment with GIN (jsonb_path_ops is smaller, supports @> only)
CREATE INDEX ON events USING gin (payload jsonb_path_ops);
SELECT id FROM events WHERE payload @> '{"type":"signup"}';

-- frequently filtered key -> real column
ALTER TABLE events ADD COLUMN kind text GENERATED ALWAYS AS (payload->>'type') STORED;
CREATE INDEX CONCURRENTLY ON events (kind);
```
- PG 18 generated columns default to VIRTUAL; PG 18 rejects indexes on virtual columns (PG 19 status unverified; its release notes list no change): say `STORED` when you will index.
- PG 17+: `JSON_TABLE` turns JSON arrays into rows. MySQL: multi-valued indexes on JSON arrays (8.0.17+); functional index on `(CAST(doc->>'$.type' AS CHAR(32)))`. SQL Server 2025: native `json` type; earlier, computed column + index.

## Index cookbook (PG)
| Query shape | Index |
|---|---|
| `WHERE tenant_id = ? AND created_at > ? ORDER BY created_at` | `(tenant_id, created_at)` |
| `WHERE lower(email) = ?` | `(lower(email))` unique, or `citext` column |
| Only active rows queried | `... WHERE deleted_at IS NULL` partial |
| Read 2-3 extra columns without heap visit | `(a, b) INCLUDE (c, d)` |
| `LIKE '%term%'` | GIN `gin_trgm_ops` (pg_trgm) |
| Full text | GIN on `to_tsvector('english', body)` (or stored tsvector column) |
| Huge append-only log by time | BRIN `(created_at)` |
| Uniqueness that ignores NULL semantics | `UNIQUE NULLS NOT DISTINCT` (PG 15+) |
| No overlapping bookings | `EXCLUDE USING gist (room WITH =, during WITH &&)` or PG 18 `WITHOUT OVERLAPS` |

## Reading EXPLAIN (PG)
```sql
BEGIN;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) UPDATE ...;   -- ANALYZE really runs it
ROLLBACK;
```
Check in order: (1) estimated vs actual rows per node, a 10x+ gap means stale or missing stats (`ANALYZE t;`, extended statistics `CREATE STATISTICS` for correlated columns); (2) the node with the most time (`actual time` x `loops`); (3) Seq Scan with high `Rows Removed by Filter` -> index; (4) `Sort Method: external merge` -> more `work_mem` for that query or an index providing the order; (5) `Heap Fetches` high on index-only scans -> vacuum. Visualizers: explain.dalibo.com (PEV2; stores uploaded plans, or run its standalone HTML locally), pgMustard (paid, PG 9.6-18). Plans can contain literal values: strip sensitive data before uploading.

## Pitfalls
- `NOT IN (SELECT x ...)` where x can be NULL -> empty result. Use `NOT EXISTS`.
- `WHERE ts BETWEEN '2026-01-01' AND '2026-01-31'` misses most of the 31st. Use half-open ranges.
- `count(col)` skips NULLs; `count(*)` does not.
- Function on an indexed column (`WHERE date(created_at) = ...`) disables the index: rewrite as a range or index the expression.
- Implicit casts (comparing `varchar` to a number in MySQL) skip indexes and can match wrong rows.
- `ORDER BY random()` on big tables: use `TABLESAMPLE` or a keyed sample.
