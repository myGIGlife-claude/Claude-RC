---
name: rc-go
description: Current Go (1.26/1.27) language, stdlib, modules/toolchain, concurrency, testing, tooling, web, security and deployment practice. Use when writing, reviewing or upgrading Go code (*.go, go.mod, go.work, .golangci.yml), Go HTTP services, CLIs, tests/benchmarks, Dockerfiles for Go, or when advice might be GOPATH-era or pre-generics.
---
# Go  (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

## Currency check
- **Supported: Go 1.27 and Go 1.26.** Policy: a major is supported until two newer majors ship. Latest: go1.27.2 and go1.26.9 (both 2026-10-08).
  Go 1.25 (last patch go1.25.14, 2026-08-19) and older get no security fixes. Next major (1.28) expected ~2027-02 (unverified; cadence is Feb/Aug).
- Release dates: 1.27 2026-08-19, 1.26 2026-02-10, 1.25 2025-08-12, 1.24 2025-02-11.
- Per-release details: `references/releases.md`. Headline items of the last ~20 months:
  - **1.27**: generic methods; `encoding/json/v2` + `jsontext` GA (v1 `encoding/json` now runs on the v2 engine; `GOEXPERIMENT=nojsonv2` opts out); new `uuid` package (`uuid.NewV7()`); `crypto/mldsa`;
    `goroutineleak` pprof profile GA; `strings.CutLast`; `synctest.Sleep`; `httptest.NewTestServer`.
  - **1.26**: Green Tea GC default (10-40% less GC overhead); `new(expr)`; `go fix` = modernizers; `errors.AsType[T]`;
    `slog.NewMultiHandler`; PQ hybrid TLS key exchange default; crypto/* ignore caller `rand` args.
  - **1.25**: container-aware GOMAXPROCS; `testing/synctest` GA; `sync.WaitGroup.Go`; `http.CrossOriginProtection`; more `os.Root` methods;
    nil-check compiler fix (code using a result before checking `err` now panics).
  - **1.24**: generic type aliases; `tool` directive in go.mod; swiss-table maps; `os.Root`; `weak`; `runtime.AddCleanup`;
    `testing.B.Loop`; `T.Context`; `omitzero`; FIPS 140-3 module; `crypto/mlkem|hkdf|pbkdf2|sha3`.
- Still-current older baselines: 1.21 `min/max/clear`, `slog`, `slices`, `maps`, `cmp`, toolchain lines; 1.22 per-iteration loop vars,
  `range n`, `math/rand/v2`, ServeMux method+wildcard routing; 1.23 range-over-func, `iter`, `unique`.
- Ecosystem (verify before pinning): golangci-lint v2.14.0 (2026-09-24); Fiber v3.0.0 GA 2026-02-02 (needs Go 1.25+);
  Echo v5.4.0 and v4.16.0 both released 2026-09-27; gorilla/mux not archived, last push 2024-08-15.

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| GOPATH layout, `dep`, `glide`, `GO111MODULE=on` | Modules only; `go mod init`, no env toggle | 1.16 default |
| `io/ioutil.ReadFile/ReadAll/TempDir` | `os.ReadFile`, `io.ReadAll`, `os.MkdirTemp` | 1.16 (deprecated) |
| `interface{}` | `any` | 1.18 |
| `x := x` copy inside loops / goroutines | Not needed: each iteration has its own var (module `go >= 1.22`) | 1.22 |
| `for i := 0; i < n; i++` for counting | `for i := range n` | 1.22 |
| `sort.Slice`, `sort.Strings`, hand-rolled `contains`/`index` | `slices.SortFunc(s, cmp)`, `slices.Sort`, `slices.Contains`, `slices.Index` | 1.21 |
| Manual `if a < b` min/max helpers; loop to clear a map | builtins `min`, `max`, `clear` | 1.21 |
| `rand.Seed(time.Now().UnixNano())`, `math/rand` | `math/rand/v2` (auto-seeded); `Seed` is a no-op since 1.24 | 1.22/1.24 |
| logrus / zap by default | `log/slog` (zap/zerolog only if profiling proves need) | 1.21 |
| gorilla/mux, chi just for `GET /x/{id}` | `http.NewServeMux` with `"GET /x/{id}"` + `r.PathValue` | 1.22 |
| `errors.As(err, &target)` boilerplate | `errors.AsType[*MyErr](err)` | 1.26 |
| `wg.Add(1); go func(){ defer wg.Done() ... }()` | `wg.Go(func(){ ... })` | 1.25 |
| `for i := 0; i < b.N; i++` in benchmarks | `for b.Loop() { ... }` | 1.24 |
| `tools.go` with blank imports | `tool` directive: `go get -tool <pkg>`, run with `go tool <name>` | 1.24 |
| `filepath.Clean` + prefix check to sandbox paths | `os.OpenRoot(dir)` / `root.Open(name)` | 1.24 |
| `runtime.SetFinalizer` | `runtime.AddCleanup` | 1.24 |
| `json:",omitempty"` on structs/time.Time (never omitted) | `json:",omitzero"` | 1.24 |
| `encoding/json` v1 for new code needing strictness/speed | `encoding/json/v2` (`json.Marshal(v, opts...)`, `UnmarshalRead`) | 1.27 |
| `github.com/google/uuid` for new code | stdlib `uuid` (`uuid.NewV7()` for DB keys) | 1.27 |
| `p := new(int); *p = 42` / `ptr(v)` helpers | `new(42)` | 1.26 |
| Free func `NewThing[T](x *X, ...)` beside a type | generic method `func (x *X) Do[T any](...)` (not for interfaces) | 1.27 |
| `uber-go/automaxprocs` in containers | Built-in cgroup-aware GOMAXPROCS (delete the import) | 1.25 |
| `GOEXPERIMENT=greenteagc`, `jsonv2`, `synctest` flags | Defaults now; remove the flags | 1.26/1.27/1.25 |
| `synctest.Run` (experiment) | `synctest.Test(t, func(t *testing.T){...})` | 1.25 |
| golangci-lint v1 config (`enable-all`, `linters-settings`) | v2: `version: "2"`, `linters.default`, `formatters:`; run `golangci-lint migrate` | lint v2 (2025) |
| separate gosimple/stylecheck linters | merged into `staticcheck` in golangci-lint v2 | lint v2 |
| `httputil.ReverseProxy{Director: ...}` | `Rewrite: func(*httputil.ProxyRequest)` | deprecated 1.26 |
| `rsa.EncryptPKCS1v15` | `rsa.EncryptOAEP` / `EncryptOAEPWithOptions`, or HPKE | deprecated 1.26 |
| `go tool doc` / `cmd/doc` | `go doc` (`go doc -http` for a browser) | removed 1.26 |
| Mandatory `/pkg`, `/cmd`, `/src`, "golang-standards/project-layout" | Flat package at root; add `cmd/` only for several binaries; `internal/` to hide | always |

## Do this
### Modules and toolchain
```
module example.com/svc

go 1.26.0               // minimum Go; also the language version. Libraries: oldest supported release.

tool (
    golang.org/x/tools/cmd/stringer
    golang.org/x/vuln/cmd/govulncheck
)
```
- `go` line = minimum requirement (enforced since 1.21; older toolchains refuse the module). `toolchain go1.27.2` = preferred toolchain,
  only add it in apps when you need a newer compiler than the `go` line. Since 1.25 `go get` no longer adds it automatically.
- `GOTOOLCHAIN=auto` (default) downloads the needed toolchain via the proxy; CI images that must not download: `GOTOOLCHAIN=local`.
- Upgrade: `go get go@1.27.2 && go mod tidy && go fix ./...` (modernizers rewrite old idioms), then run tests with `-race`.
- Workspaces (`go work init ./a ./b`) for local multi-module dev; usually do not commit `go.work` for libraries.
- Private modules: `GOPRIVATE=example.com/org/*` (implies no proxy/sumdb); auth via `.netrc` or git `insteadOf`. Never disable `GOSUMDB` globally.
- Vendoring (`go mod vendor`) only if builds must be hermetic offline; otherwise rely on the module proxy + `go.sum`.
- `go install pkg@vX.Y.Z` for global dev tools; per-project tools go in the `tool` block (versions pinned in go.mod/go.sum).
- `ignore ./node_modules` in go.mod (1.25) keeps `./...` out of non-Go trees.

### Layout (no cargo-cult)
- Small service: `go.mod`, `main.go`, a few packages by domain (`store/`, `api/`). Name packages for what they provide, not `utils`/`common`/`models`.
- Several binaries: `cmd/<name>/main.go`. Code you do not want imported: `internal/`. Skip `/pkg`.
- Keep `main` thin: parse config, build deps, call `run(ctx) error`; `os.Exit(1)` only in `main`.

### Errors
```go
var ErrNotFound = errors.New("not found")
if err != nil { return fmt.Errorf("load user %d: %w", id, err) }   // wrap with context, lower case, no "failed to"
if errors.Is(err, ErrNotFound) { ... }
if pe, ok := errors.AsType[*fs.PathError](err); ok { ... }           // 1.26+
return errors.Join(errA, errB)                                     // multi-error, works with Is/As
```
- Handle an error once: log OR return, not both. Panic only for programmer bugs. Check `err` before touching other results.

### Concurrency
```go
g, ctx := errgroup.WithContext(ctx)   // golang.org/x/sync/errgroup
g.SetLimit(8)                         // bounded worker pool, no hand-rolled semaphore
for _, u := range urls {
    g.Go(func() error { return fetch(ctx, u) })   // u is per-iteration since 1.22
}
if err := g.Wait(); err != nil { ... }
```
- No error to collect: `var wg sync.WaitGroup; wg.Go(f); wg.Wait()` (1.25).
- Every goroutine needs an exit path: select on `ctx.Done()` around every blocking send/receive. Sender closes channels, never receiver.
- `context.Context` is the first param, never stored in structs; use `context.WithTimeout`, `context.WithCancelCause`, `context.AfterFunc`,
  `context.WithoutCancel` (for work that must outlive the request). Graceful shutdown: `signal.NotifyContext` + `srv.Shutdown(ctx)`.
- Mutex for protecting state; channels for handing off ownership/signalling. `sync.OnceValue(s)` for lazy init. `atomic.Int64` types over `atomic.AddInt64`.
- Leak check in prod: `/debug/pprof/goroutineleak` (1.27). In tests: `testing/synctest` for timers/time-dependent code (no real sleeps).

### Generics
- Use for containers, algorithms over `slices`/`maps`, typed helpers (`errors.AsType`). Do not use to replace a 1-method interface, or
  "for future flexibility". If only one type argument is ever used, write the concrete type.
- Iterators: return `iter.Seq[T]` / `iter.Seq2[K,V]` for lazy sequences; consume with `for v := range seq`; `slices.Collect`, `maps.Keys`,
  `slices.Sorted(maps.Keys(m))`. `strings.SplitSeq`/`Lines` avoid allocating slices.

### Std library picks
- Logging: `slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: lvl}))`, `slog.SetDefault`; pass attrs, not formatted strings;
  `logger.InfoContext(ctx, ...)` so handlers can add trace IDs. Fan out with `slog.NewMultiHandler` (1.26).
- Interning: `unique.Make(s)` for many repeated strings. Caches that must not pin memory: `weak.Pointer`.
- JSON: new code `encoding/json/v2` (strict: rejects dup keys + bad UTF-8). Existing v1 code keeps working; `omitzero` on both.
- Randomness: `math/rand/v2` for non-security; `crypto/rand.Text()` / `crypto/rand.Read` for tokens.

### Web
```go
mux := http.NewServeMux()
mux.HandleFunc("GET /users/{id}", getUser)        // r.PathValue("id"); 405 handled for you
mux.HandleFunc("POST /users", createUser)
mux.Handle("GET /static/{path...}", http.FileServerFS(staticFS))
srv := &http.Server{Addr: ":8080", Handler: http.NewCrossOriginProtection().Handler(mux),
    ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 30 * time.Second, WriteTimeout: 30 * time.Second, IdleTimeout: 120 * time.Second}
```
- Start with `net/http`. chi: thin, net/http-compatible middleware ecosystem. echo/gin: batteries, own context type. Fiber: fasthttp,
  NOT net/http-compatible (no `http.Handler` middleware, HTTP/2 caveats); pick only for a measured need.
- RPC: Connect (`connectrpc.com/connect`) serves gRPC, gRPC-Web and HTTP/JSON from one handler on net/http; grpc-go if you need its ecosystem.
- SQL: Postgres via `pgx/v5` (`pgxpool`); type-safe queries via `sqlc`; ent/GORM only if you want an ORM. With `database/sql` set
  `SetMaxOpenConns`, `SetMaxIdleConns`, `SetConnMaxLifetime`; always `defer rows.Close()` and check `rows.Err()`; use `QueryContext`.
- Clients: never `http.DefaultClient` for outbound calls (no timeout); build `&http.Client{Timeout: ...}` once and reuse.

### Observability
- OpenTelemetry Go SDK (`go.opentelemetry.io/otel`) + `otelhttp` middleware; slog bridge for trace-correlated logs.
- `net/http/pprof` on a separate internal-only listener; `runtime/trace.FlightRecorder` (1.25) to snapshot the last seconds on an incident;
  `runtime/metrics` (1.26 scheduler metrics) for goroutine/thread counts.

### Build and deploy
```dockerfile
FROM golang:1.27 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /app ./cmd/svc
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /app /app
ENTRYPOINT ["/app"]
```
- Cross-compile: `GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build`. cgo needs a C cross toolchain; avoid cgo (e.g. pure-Go SQLite
  `modernc.org/sqlite`) when you want static binaries. `scratch` needs CA certs + tzdata copied in (or `import _ "time/tzdata"`).
- GOMAXPROCS respects container CPU limits since 1.25; set `GOMEMLIMIT` to ~90% of the memory limit.
- WebAssembly: `GOOS=js GOARCH=wasm` (support files in `$(go env GOROOT)/lib/wasm` since 1.24), `GOOS=wasip1` for WASI,
  `//go:wasmexport` (1.24). TinyGo when binary size matters.

## Security
- **Vulns**: `govulncheck ./...` in CI (reports only reachable vulnerable code); stay on a supported Go patch release (stdlib CVEs land monthly).
- **Path traversal**: user-controlled file names go through `root, err := os.OpenRoot(base)` (check err, `defer root.Close()`), then `root.Open(name)` (blocks `..` and symlink escapes).
  `http.FileServerFS(os.DirFS(...))` for static files; never `filepath.Join(base, userInput)` alone.
- **SQL injection**: placeholders only (`$1` pgx, `?` MySQL); sqlc generates them. Never `fmt.Sprintf` SQL; whitelist identifiers for ORDER BY.
- **SSRF**: for user-supplied URLs use a custom `net.Dialer{Control: ...}` that rejects private/loopback/link-local IPs after DNS
  resolution (`netip.Addr.Unmap()` then `IsPrivate/IsLoopback/IsLinkLocalUnicast/IsUnspecified/IsMulticast`), limit redirects (`CheckRedirect`), set timeouts.
- **CSRF**: `http.CrossOriginProtection` (1.25) for cookie-authenticated forms/APIs.
- **DoS**: `ReadHeaderTimeout` (gosec G112), `http.MaxBytesReader` on bodies, `http.MaxBytesReader` before any `json.Decoder`, bounded worker pools.
- **Crypto**: AEAD via `cipher.NewGCMWithRandomNonce` (1.24) or `chacha20poly1305`; passwords with `golang.org/x/crypto/argon2` or
  `bcrypt` (`crypto/pbkdf2` only where FIPS demands); compare secrets with `subtle.ConstantTimeCompare`; tokens with `crypto/rand.Text()`.
  No `math/rand` for secrets, no MD5/SHA-1 for security, no RSA PKCS#1 v1.5 encryption.
- **TLS**: defaults are good (TLS 1.2 min for clients, 1.3 preferred, PQ hybrid `X25519MLKEM768` on). Do not set `InsecureSkipVerify`,
  do not hand-pick cipher suites; set `MinVersion: tls.VersionTLS13` for internal services.
- **FIPS 140-3**: build with `GOFIPS140=certified` (v1.0.0 has CMVP cert #5247; v1.26.0 was "in process" as of 2026-04-28) or
  `latest`, and/or run with `GODEBUG=fips140=on` (`only` is for testing). Check `crypto/fips140.Enabled()`.
  Native Go Cryptographic Module; no BoringCrypto/cgo needed anymore.
- Secrets never in logs: implement `slog.LogValuer` on secret-bearing types to redact.
- `html/template` (auto-escapes) for HTML, never `text/template`.

## Performance & quality
- Measure first: `go test -bench=. -benchmem -count=10 | benchstat`; `go test -cpuprofile`/`-memprofile`; `go tool pprof -http=:0` (flame graph default).
- Escape analysis: `go build -gcflags=-m=2 ./pkg`; reduce allocations by preallocating (`make([]T, 0, n)`), `strings.Builder`,
  `Append*` APIs (`strconv.AppendInt`, `encoding.TextAppender`), avoiding `any` boxing in hot paths.
- `sync.Pool`: only for short-lived, frequently allocated, same-size objects proven hot in profiles; reset before Put; do not pool
  huge or variably sized buffers (memory bloat); it is a cache that the GC empties, not a free list.
- PGO: drop a CPU profile as `default.pgo` in the main package dir; `go build` uses it (`-pgo=auto` default), 2-14% gain in Go's own benchmarks (as of 1.22).
- GC knobs: `GOGC` and `GOMEMLIMIT`; Green Tea GC is default in 1.26+ (opt out only to debug a regression).
- Maps are swiss tables (1.24); small allocs are faster (1.27): re-baseline benchmarks after upgrading.
- Targets for services: p99 latency SLO, allocs/op in hot handlers, goroutine count flat under steady load, no `goroutineleak` entries.

## Testing & tooling
- Stdlib `testing` is the default: table-driven tests with `t.Run(tc.name, ...)`, `t.Parallel()` where safe, `t.Context()` (1.24),
  `t.Chdir`, `t.TempDir`, `t.Setenv`, `t.ArtifactDir()` (1.26). `go-cmp` (`cmp.Diff`) for struct diffs.
  testify is fine if the repo already uses it; do not add it to a stdlib-only repo.
- Golden files: `testdata/*.golden`, regenerate behind a `-update` flag.
- Fuzzing: `func FuzzX(f *testing.F)` + `go test -fuzz=FuzzX -fuzztime=30s`; commit `testdata/fuzz` crashers as regressions.
- Benchmarks: `for b.Loop() { ... }` (1.24; setup above the loop is excluded automatically).
- Time/concurrency tests: `synctest.Test(t, func(t *testing.T){ ... synctest.Wait() })` with fake clock (1.25); `httptest.NewTestServer` (1.27).
- Always `go test -race ./...` in CI (needs cgo on most platforms). `go vet` runs a subset automatically during `go test`.
- Tooling: `gofmt`/`goimports` (or gofumpt), `go vet`, `staticcheck`, golangci-lint v2 (`version: "2"`; `golangci-lint fmt` for formatters),
  `govulncheck`, `gopls` (editor; runs modernize analyzers), Delve (`dlv test`, `dlv debug`), `go fix ./...` after upgrades.
- Minimal CI: `go mod tidy -diff` (fails if untidy, 1.23+), `go vet ./...`, `golangci-lint run`, `go test -race -shuffle=on ./...`, `govulncheck ./...`.

## Common mistakes in AI-written code
- Pre-1.22 loop-var copies (`v := v`) and `go vet` loopclosure workarounds: noise now.
- `ioutil.*`, `interface{}`, `sort.Slice`, `rand.Seed`, `strings.Title`: deprecated or replaced (see table).
- `ServeMux` patterns: method and path separated by ONE space (`"GET /x"`); `{name...}` only as the last segment; `/x/` vs `/x/{$}` matters.
  Do not pull in gorilla/mux or chi for routing alone.
- Inventing APIs: there is no `slices.Map`/`slices.Filter`, no `errors.Wrap` in stdlib, no `context.WithValue` typed helper, no
  `sync.WaitGroup.Go` before 1.25, no `errors.AsType` before 1.26, no `uuid` stdlib package before 1.27. Generic methods cannot satisfy interfaces.
- `json:"omitempty"` on `time.Time` or a struct expecting omission (use `omitzero`).
- `http.Get`/`http.DefaultClient` in production (no timeout); `http.ListenAndServe` without `ReadHeaderTimeout`; not closing `resp.Body`.
- Goroutines without cancellation; unbuffered result channel that nobody reads after a timeout (leak); `wg.Add` inside the goroutine.
- Using a result before checking `err` (panics since 1.25 compiler fix, was silently "working").
- Storing `context.Context` in structs; `context.Background()` deep in request paths instead of the request ctx.
- `log.Fatal` inside library code; `panic` for expected errors; logging AND returning the same error.
- Adding `automaxprocs`, `pkg/errors`, `logrus`, `github.com/google/uuid`, `golang.org/x/exp/slices` to a new project: stdlib covers them.
- `go.mod` `go` line set to an unreleased/unsupported version, or a library `go` line bumped needlessly (forces consumers to upgrade).
- golangci-lint configs in v1 format (`enable-all`, `linters-settings`, `gosimple`): v2 rejects them.
- Dockerfile `FROM golang:1.xx` with an unsupported version (anything below 1.26 today).

## Before you ship
- [ ] Go 1.26.x or 1.27.x latest patch; `go` line correct; `go mod tidy -diff` clean; `go fix ./...` run after upgrade.
- [ ] `go vet`, golangci-lint v2, `go test -race ./...`, `govulncheck ./...` all green in CI.
- [ ] HTTP servers have timeouts, body limits, graceful shutdown; outbound clients have timeouts.
- [ ] Every goroutine has a cancellation path; no `goroutineleak` reports under load.
- [ ] File paths from users go through `os.Root`; SQL uses placeholders; secrets redacted in slog.
- [ ] Static `CGO_ENABLED=0 -trimpath` build on distroless/scratch, non-root; `GOMEMLIMIT` set.

## Sources
- https://go.dev/doc/devel/release : release dates, latest patches, support policy (accessed 2026-10-09)
- https://go.dev/doc/go1.27 : generic methods, json/v2 GA, uuid, mldsa, goroutineleak, removed GODEBUGs (2026-10-09)
- https://pkg.go.dev/uuid : uuid package API (New, NewV4, NewV7, Parse) (2026-10-09)
- https://go.dev/doc/go1.26 : Green Tea default, new(expr), go fix modernizers, errors.AsType, crypto changes (2026-10-09)
- https://go.dev/doc/go1.25 : container GOMAXPROCS, synctest, WaitGroup.Go, CrossOriginProtection, os.Root methods, nil-check fix (2026-10-09)
- https://go.dev/doc/go1.24 : type aliases, tool directive, swiss maps, os.Root, weak, B.Loop, omitzero, FIPS 140-3, rand.Seed no-op (2026-10-09)
- https://go.dev/doc/go1.23 and https://go.dev/doc/go1.22 : iterators/unique; loopvar, ServeMux patterns, math/rand/v2 (from release notes; not re-fetched, 2026-10-09)
- https://go.dev/doc/security/fips140 : GOFIPS140 values, fips140 GODEBUG, certified module versions (2026-10-09)
- https://pkg.go.dev/net/http#CrossOriginProtection : constructor and methods (2026-10-09)
- https://go.dev/doc/toolchain : go/toolchain lines, GOTOOLCHAIN (2026-10-09)
- https://golangci-lint.run/docs/product/migration-guide/ : v2 config, migrate command, merged staticcheck (2026-10-09)
- https://github.com/golangci/golangci-lint/releases : v2.14.0, 2026-09-24 (2026-10-09)
- https://github.com/gofiber/fiber/releases/tag/v3.0.0 : Fiber v3 GA 2026-02-02, requires Go 1.25 (2026-10-09)
- https://github.com/labstack/echo/releases : Echo v5.4.0 / v4.16.0 (2026-10-09)
- https://go.dev/doc/pgo : PGO 2-14% (as of Go 1.22), default.pgo (2026-10-09)
- https://github.com/gorilla/mux : repo not archived, last push 2024-08-15 (GitHub API, 2026-10-09)
