# PostgreSQL 17/18/19 and auth details (as of 2026-10)

## PostgreSQL: what each recent major gave you

| Version | GA / EOL | Use it for |
|---|---|---|
| 17 | 2024-09-26 / 2029-11-08 | `JSON_TABLE`, SQL/JSON `JSON_EXISTS`/`JSON_QUERY`/`JSON_VALUE`; `MERGE ... RETURNING`; `COPY ... (ON_ERROR ignore)`; incremental backups (`pg_basebackup --incremental` + `pg_combinebackup`); failover logical slots; `pg_createsubscriber`; `sslnegotiation=direct` (TLS via ALPN); `pg_maintain` role; vacuum uses up to 20x less memory; `EXPLAIN (SERIALIZE, MEMORY)`. |
| 18 | 2025-09-25 / 2030-11-14 | Async I/O (`io_method = worker` default, `io_uring` on Linux); `uuidv7()`; virtual generated columns (now the default kind); `RETURNING OLD.*, NEW.*`; temporal `PRIMARY KEY/UNIQUE ... WITHOUT OVERLAPS` and `FOREIGN KEY ... PERIOD`; B-tree skip scan; OR-clause index use; parallel GIN builds; planner stats kept across `pg_upgrade` (+ `--swap`, `--jobs` checks); `oauth` auth method; data checksums on by default in `initdb`; EXPLAIN ANALYZE shows buffers by default; wire protocol 3.2; **md5 password auth deprecated**. |
| 19 | Beta 4 2026-09-24, GA expected Oct 2026 (unverified) | `REPACK [CONCURRENTLY]`; `INSERT ... ON CONFLICT DO SELECT ... RETURNING` (atomic get-or-create); `UPDATE/DELETE ... FOR PORTION OF`; SQL/PGQ property graphs; `GROUP BY ALL`; `WAIT FOR LSN` (read-your-writes on replicas); `MERGE/SPLIT PARTITIONS`; parallel autovacuum; `pg_plan_advice`; logical replication of sequences; online checksum enable; `pg_stat_lock`. Incompatible: **JIT off by default**, `default_toast_compression = lz4`, **RADIUS auth removed**, warning after each md5 login (`md5_password_warnings`, a PG 18 setting). |

Upgrade notes
- 18: new clusters have checksums on; `pg_upgrade` from a non-checksum cluster needs `--no-data-checksums` on initdb or enabling checksums first.
- 14 reaches EOL 2026-11-12: plan upgrades now. Use `pg_upgrade --link`/`--swap` or logical replication for near-zero downtime.
- Feature-detect before using 19-only syntax; managed providers lag GA by weeks to months.

Handy patterns
```sql
-- time-ordered ids (PG 18)
CREATE TABLE orders (
  id uuid PRIMARY KEY DEFAULT uuidv7(),
  tenant_id uuid NOT NULL,
  total_cents bigint NOT NULL CHECK (total_cents >= 0),
  created_at timestamptz NOT NULL DEFAULT now()
);
-- safe constraint add on a big table
ALTER TABLE orders ADD CONSTRAINT orders_tenant_fk
  FOREIGN KEY (tenant_id) REFERENCES tenants(id) NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT orders_tenant_fk;
-- job queue claim
UPDATE jobs SET state='running', locked_at=now()
WHERE id = (SELECT id FROM jobs WHERE state='queued'
            ORDER BY run_at FOR UPDATE SKIP LOCKED LIMIT 1)
RETURNING *;
```

Session settings for apps (set per role or connection): `statement_timeout`, `lock_timeout`, `idle_in_transaction_session_timeout`,
`application_name` (shows in `pg_stat_activity` and traces). `password_encryption = scram-sha-256`; `hostssl` lines only in `pg_hba.conf`.

## Auth flows: what to pick

| Client | Flow | Token handling |
|---|---|---|
| Server-rendered web app | OIDC auth code + PKCE (confidential client) | Server session cookie; tokens stay server-side |
| SPA | BFF in front of the SPA (same origin), BFF is the OAuth client | `__Host-` session cookie; SPA never sees tokens |
| Native/mobile | Auth code + PKCE via system browser (AppAuth), claimed https redirect | Refresh token in Keychain/Keystore, rotation on |
| Machine to machine | Client credentials with `private_key_jwt` or mTLS, or workload identity federation | Short access tokens, no long-lived secrets |
| CLI / TV | Device authorization grant (RFC 8628) | Store in OS keychain |

Removed/forbidden: implicit grant, resource-owner password grant, wildcard redirect URIs, tokens in query strings, `alg: none`.

Passkeys (WebAuthn L3): register with `residentKey: "required"` and `userVerification: "preferred"`; store credential id, public
key, sign count, transports, backup flags; use a maintained server library (SimpleWebAuthn, webauthn4j, py_webauthn, go-webauthn).
Syncable passkeys satisfy NIST AAL2, not AAL3 (800-63B-4).

Refresh token rotation: every refresh returns a new refresh token, old one invalid; if an already-used token is presented, revoke the
whole token family and force re-login. Bind refresh tokens to client id and (ideally) DPoP key.

Sources (accessed 2026-10-09): https://www.postgresql.org/about/news/postgresql-17-released-2936/,
https://www.postgresql.org/about/news/postgresql-18-released-3142/, https://www.postgresql.org/about/news/postgresql-19-beta-1-released-3313/,
https://www.postgresql.org/support/versioning/, https://www.rfc-editor.org/info/rfc9700, https://pages.nist.gov/800-63-4/sp800-63b.html
