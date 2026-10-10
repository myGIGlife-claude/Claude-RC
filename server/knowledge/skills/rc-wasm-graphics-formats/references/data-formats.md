# Data and config formats: pitfalls (as of 2026-10)

Versions: JSON Schema 2020-12 (current), YAML 1.2.2 (2021-10), TOML 1.1.0 (2025-12-18), js-yaml 5.4.3, `yaml` 2.9.1,
smol-toml 1.9.1, Ajv 8.20.0, `@bufbuild/protobuf` 2.16.0, msgpackr 2.1.0, cbor-x 1.6.6, papaparse 5.7.0, fast-xml-parser 5.11.2
(npm/GitHub, checked 2026-10-09). Items marked (unverified) were not checked on a primary source.

## Choosing
| Need | Pick | Avoid |
|---|---|---|
| Public HTTP API | JSON + JSON Schema / OpenAPI 3.1 | XML unless the partner requires it |
| Service-to-service RPC | Protobuf + gRPC or Connect | hand-rolled binary |
| Human-edited app config | TOML, or JSONC | YAML for new formats; INI for nested data |
| Ecosystem config (CI, k8s, compose, OpenAPI) | YAML, because the tool demands it | inventing YAML tags |
| Logs, exports, streaming | NDJSON / JSON Lines | one giant JSON array |
| Tabular export for spreadsheets | CSV (RFC 4180) + UTF-8 BOM if Excel users | TSV/CSV without quoting rules |
| Compact schemaless binary | CBOR (RFC 8949, IETF, used by WebAuthn/COSE) or MessagePack | BSON outside MongoDB |
| Event streams with registry | Avro (or Protobuf) + schema registry | unversioned JSON blobs |
| Analytics files | Parquet (columnar, compressed, typed; read with DuckDB/Arrow/pandas) | CSV for multi-GB analytics |
Binary vs text: binary wins on size/parse speed for numeric and byte data and gives types; text wins on debuggability, tooling and
diffing. After Brotli/gzip the size gap shrinks: measure before switching.

## JSON (RFC 8259, ECMA-404)
- UTF-8 only on the wire, no BOM required; no comments, no trailing commas, no `NaN`/`Infinity`, strings in double quotes.
- Numbers: JS doubles. Integers > 2^53-1 (9007199254740991) silently round: send IDs/money as strings (money: integer minor units or decimal strings).
  `JSON.parse(text, (k, v, ctx) => ...)` gets `ctx.source` (JSON.parse source text access, newly Baseline 2025-03) to recover exact digits.
- Duplicate keys: RFC says names SHOULD be unique; JS keeps the last, some parsers keep the first or error. Reject duplicates at trust
  boundaries when two services parse the same document (smuggling).
- Dates: ISO 8601 / RFC 3339 strings with offset (`2026-10-09T12:00:00Z`); never locale strings or epoch-without-unit.
- `JSON.stringify` drops `undefined`, functions, symbols; `Map`/`Set` become `{}`; `BigInt` throws (add `toJSON` or convert).
- JSON5 / JSONC: for human config only (`tsconfig.json`, VS Code settings are JSONC). Parse with `jsonc-parser`/`json5`; never send them over an API.
- NDJSON / JSON Lines: one compact JSON value per `\n`; stream-parse line by line; content type `application/x-ndjson` or `application/jsonl` (neither is in the IANA registry as of 2026-10; the registered
  cousin is `application/json-seq`, RFC 7464, which uses RS separators).
- JSON Pointer (RFC 6901) for paths, JSON Patch (RFC 6902) / Merge Patch (RFC 7396) for partial updates: Merge Patch cannot set `null` values.
- Import in JS: `import cfg from './cfg.json' with { type: 'json' }` (newly 2025-04); old `assert {}` syntax is removed.

### JSON Schema 2020-12
- Declare `"$schema": "https://json-schema.org/draft/2020-12/schema"`. Use `$defs` (not `definitions`), `prefixItems` (old tuple
  `items` array), `unevaluatedProperties: false` for closed objects that use `allOf`/`$ref` (plain `additionalProperties: false` does not see composed properties).
- `format` is annotation-only by default: enable assertion (Ajv: `ajv-formats`) if you rely on `email`/`date-time`.
- OpenAPI 3.1 uses 2020-12 dialect; OpenAPI 3.0 uses an extended draft-04 subset (`nullable`), so do not mix.
- Tools: Ajv 8 (`new Ajv2020()` import from `ajv/dist/2020`), Python `jsonschema`, `check-jsonschema` CLI, or schema-first types with zod/valibot/TypeBox.

## YAML
- YAML 1.2 (core schema) vs 1.1 (what PyYAML, many Go/Ruby libs and old tools still implement):
  - Norway problem: `NO`, `no`, `on`, `off`, `y`, `n`, `yes` are booleans in 1.1 -> country code `NO` becomes `false`. In 1.2 only `true`/`false`.
  - Octal: 1.1 reads `0755` as octal 493; 1.2 needs `0o755`. Sexagesimal: 1.1 reads `1:30` as 90.
  - Floats: `1.10` -> `1.1` (version strings!), `1e3` may be a string or number depending on the library.
  - Rule: quote every string that could look like a bool, number, date or null (`"no"`, `"1.10"`, `"2026-10-09"`, `"~"`).
- Anchors/aliases (`&a`, `*a`) and merge keys (`<<: *a`): merge is a 1.1 type, not in 1.2 core; js-yaml 5 `load` disables `!!merge` by
  default. Alias bombs exist: cap with library limits.
- Safe loading: PyYAML `yaml.safe_load` (never `yaml.load` without `Loader=SafeLoader`, never `FullLoader`/`UnsafeLoader` on input);
  ruamel.yaml `YAML(typ='safe')`; js-yaml `load` (safe; `!!js/*` tags gone since v4); `yaml` (eemeli) `parse`; Go `gopkg.in/yaml.v3` /
  `goccy/go-yaml` (the `go-yaml/yaml` repo was archived 2025-04-01 as unmaintained: prefer a maintained fork for new code).
- Indentation is significant, tabs forbidden; multiple documents with `---`; keys are case-sensitive; a trailing space after `:` matters.
- Lint with `yamllint`; for k8s/CI validate against the tool's JSON Schema (`check-jsonschema --schemafile`).

## TOML
- 1.0.0 (2021-01-11) and 1.1.0 (2025-12-18, per the toml-lang CHANGELOG). 1.1: newlines and trailing commas inside inline tables, `\xHH`, `\e`, seconds optional
  in times. Keep files 1.0-compatible unless every reader (Cargo, pip/`tomllib`, uv, your parser) supports 1.1 (verified: Python 3.15 `tomllib`, tomli 2.4.0; others unverified).
- Tables cannot be redefined; dotted keys and `[table]` headers for the same table must not conflict; arrays of tables `[[bin]]`.
- Integers are 64-bit signed: JS parsers return `bigint` or lose precision above 2^53 (smol-toml has an option; check).
- Datetimes: offset datetime, local datetime, local date, local time are 4 distinct types. Python stdlib reads TOML (`tomllib`, 3.11+) but cannot write it (`tomli-w`).
- Format/lint: `taplo`.

## XML
- Use only when required (SAML, SOAP, RSS/Atom, Office/ODF, SVG, Android resources, Maven). Not for new configs.
- Namespaces: match on namespace URI + local name, not prefixes; default namespace does not apply to attributes.
- XXE / SSRF / billion laughs: disable DTDs and external entities in every parser (Python `defusedxml`; Java `disallow-doctype-decl`
  + `FEATURE_SECURE_PROCESSING`; .NET `DtdProcessing.Prohibit`, `XmlResolver = null`; libxml2 no `NOENT`/`DTDLOAD`).
- Validation: XSD 1.0 widely supported (`xmllint --schema`); XSD 1.1 only in some engines (Saxon EE, Xerces-J). RELAX NG is simpler.
- Signed XML (SAML): use a maintained library; signature wrapping attacks make hand-rolled verification unsafe.

## CSV (RFC 4180 vs reality)
- RFC: CRLF line endings, `,` separator, fields with `,`, `"` or newlines quoted, `"` escaped as `""`, optional header row.
- Reality: `;` separators in European Excel, BOM-prefixed UTF-8 (needed for Excel to detect UTF-8), Windows-1252 legacy files,
  ragged rows, embedded newlines. Always use a parser (Python `csv`, papaparse, `encoding/csv`) and state encoding + delimiter.
- Excel mangles leading zeros, long numbers (>15 digits), and date-like strings: document it or ship XLSX for Excel users.
- Formula injection: prefix `'` to user cells starting with `= + - @` or tab/CR when exporting.

## INI and .env
- INI: no spec; sections `[name]`, `key=value`; comment chars (`;` vs `#`), case sensitivity and duplicate handling vary by parser
  (Python `configparser` lowercases keys and interpolates `%(x)s` by default: use `RawConfigParser`/`interpolation=None`).
- .env: de facto (dotenv, Docker Compose, systemd `EnvironmentFile` differ on quotes, `export`, multiline and `${VAR}` expansion).
  Node has `--env-file` (20.6+) and `process.loadEnvFile()` (20.12+ / 21.7+); Python `python-dotenv`. Keep values simple; never commit real `.env`; commit `.env.example` with no secrets.

## Protocol Buffers / gRPC
- Use `edition = "2023"` (or `"2024"` where your protoc/Buf supports it - unverified per language) or `syntax = "proto3"`; proto2 only for legacy.
- Evolution rules (wire compatibility):
  - Never change a field number or reuse a deleted one: `reserved 4, 7; reserved "old_name";`.
  - Never change a field's type (except a few compatible int widenings; avoid even those). Adding fields is safe; removing = reserve.
  - Enums: first value `FOO_UNSPECIFIED = 0`; prefix values with the enum name; unknown values must be handled.
  - `optional` (proto3) gives presence; scalars without it cannot tell "0" from "unset".
  - Renaming a field is wire-safe but breaks JSON mapping and generated code: treat as breaking for JSON clients.
  - `oneof` changes (moving fields in/out) are breaking.
- Tooling: Buf CLI (`buf lint`, `buf breaking --against '.git#branch=main'`, `buf generate`), Connect (gRPC + gRPC-Web + JSON over HTTP)
  for browsers; `@bufbuild/protobuf` v2 for TS (protobufjs is older style). Version packages (`acme.billing.v1`) and add `v2` for breaks.
- gRPC: set deadlines on every call, use status codes, keep messages < 4 MB default limit (configurable), health/reflection services in dev.

## MessagePack, CBOR, Avro, Parquet
- MessagePack: JSON-like, compact, extension types for dates; JS `msgpackr` (fast, records extension) or `@msgpack/msgpack`. No schema: validate after decode.
- CBOR (RFC 8949): IETF standard, deterministic encoding rules (for signatures), tags for dates/bignums; used in WebAuthn, COSE, CWT. JS `cbor-x`.
- Avro: schema travels with the data or a registry; reader/writer schema resolution allows adding fields with defaults; renames via aliases.
- Parquet: columnar, per-column compression and statistics, ideal for analytics and data lakes (DuckDB, Arrow, Spark); not for
  row-at-a-time updates or small RPC payloads.

## Schema evolution and versioning (any format)
- Additive changes only within a version: new optional fields with defaults; never change meaning or type of an existing field.
- Tolerant readers: ignore unknown fields (do not set `additionalProperties: false` on responses consumed by old clients; do on inputs you own).
- Version in the schema id/package/media type (`application/vnd.acme.v2+json`), not by guessing from content.
- Keep compatibility checks in CI: `buf breaking`, JSON Schema diff tools, Avro registry compatibility mode (BACKWARD by default in Confluent - unverified).
- Store a format/version field in persisted documents and files so you can migrate them later.
