# Go releases 1.22 to 1.27: what each shipped (as of 2026-10)

Source for every line: the go.dev/doc/go1.NN release notes (accessed 2026-10-09). Minor details trimmed; read the notes for the rest.

## Go 1.27 (2026-08-19), latest go1.27.2 (2026-10-08)
- Language: **generic methods** (a method may declare its own type parameters; interface methods may not, and a generic
  method cannot satisfy an interface method). Struct literal keys may be any field selector (promoted fields).
  Function type inference now works wherever a generic func is assigned/converted to a matching func type.
- New packages: `encoding/json/v2` + `encoding/json/jsontext`; v1 `encoding/json` is now backed by v2 (same behavior, error text may
  differ; `GOEXPERIMENT=nojsonv2` restores the old implementation),
  `uuid` (`New`, `NewV4`, `NewV7`, `Parse`, `MustParse`, `Nil`, `Max`; type `UUID` is comparable), `crypto/mldsa` (ML-DSA, FIPS 204),
  experimental portable `simd` (`GOEXPERIMENT=simd`).
- json/v2 defaults vs v1: rejects invalid UTF-8 and duplicate object names; `inline` tag renamed `embed`; `format`/`unknown` tag options and `DiscardUnknownMembers` removed.
- Runtime: `goroutineleak` pprof profile is now standard (`/debug/pprof/goroutineleak`); size-specialized small allocs (~30% faster
  for <80 B); tracebacks show pprof goroutine labels; `asynctimerchan` GODEBUG removed (timer channels always unbuffered).
- Std additions: `strings.CutLast`/`bytes.CutLast`, `(*rand.Rand).N` generic method, `testing/synctest.Sleep`,
  `httptest.NewTestServer` (in-memory network), `net/url` `URL.Clone`/`Values.Clone`, `math/big` `Int.Divide`,
  `http.Server.MaxHeaderValueCount`, HTTP/1 client auto-drains unread bodies on Close, `database/sql/driver.RowsColumnScanner`.
- Tools: `go test` runs vet `stdversion`; `go fix` modernizers gained `atomictypes`, `embedlit`, `slicesbackward`, `unsafefuncs`
  (`waitgroup` renamed `waitgroupgo`); `go doc pkg@version`; `go mod tidy` (go >= 1.27) collapses require blocks to two.
  Vet printf flags `%w` with a `*E` where `E` (not `*E`) implements error. `bzr` support removed.
- Removed GODEBUGs: `gotypesalias`, `tlsrsakex`, `tls10server`, `tls3des`, `tlsunsafeekm`, `x509keypairleaf`, `asynctimerchan`.
- Ports: macOS 13+ required; big-endian ppc64 Linux now ELFv2.

## Go 1.26 (2026-02-10), latest go1.26.9 (2026-10-08)
- Language: `new(expr)` (e.g. `new(42)`, handy for optional pointer fields); generic types may refer to themselves in their
  type parameter list.
- Runtime: **Green Tea GC on by default** (10-40% less GC overhead; opt out `GOEXPERIMENT=nogreenteagc`, slated for removal in 1.27);
  ~30% cheaper cgo calls; heap base randomization on 64-bit; experimental goroutine-leak profile.
- `go fix` rebuilt as the home of "modernizers" (`go fix ./...` rewrites old idioms) and `//go:fix inline`.
  `go mod init` writes `go 1.(N-1).0`. `go tool doc` removed (use `go doc`). pprof web UI defaults to flame graph.
- Std: `errors.AsType[T]`, `slog.NewMultiHandler`, `bytes.Buffer.Peek`, `crypto/hpke`, `testing/cryptotest.SetGlobalRandom`,
  `T.ArtifactDir` (+ `-artifacts`), `reflect` iterators (`Type.Fields()` ...), `signal.NotifyContext` cancels with a cause,
  `net.Dialer.DialTCP/DialUDP/...` with context, `io.ReadAll` ~2x faster.
- Crypto: `rand io.Reader` params of crypto/* key/sign functions are ignored (always secure randomness);
  TLS hybrid PQ `SecP256r1MLKEM768`/`SecP384r1MLKEM1024` on by default; RSA PKCS#1 v1.5 encryption deprecated;
  `httputil.ReverseProxy.Director` deprecated for `Rewrite`; `ServeMux` trailing-slash redirect is now 307.
- `net/url.Parse` rejects unbracketed colons in host (`http://::1/`).
- `testing.B.Loop` no longer blocks inlining. Experimental: `simd/archsimd`, `runtime/secret`.
- Ports: last release for macOS 12; 32-bit windows/arm removed; race detector on linux/riscv64.

## Go 1.25 (2025-08-12), latest go1.25.14 (2026-08-19); unsupported since 1.27
- Runtime: **container-aware GOMAXPROCS** (Linux cgroup CPU limit; re-checked periodically; off via `GOMAXPROCS` env or
  `GODEBUG=containermaxprocs=0`/`updatemaxprocs=0`); Green Tea GC experiment; `runtime/trace.FlightRecorder`.
- Compiler: fixed the 1.21 bug that delayed nil checks: using `f` before checking `err` now panics as it should. DWARF 5 default.
- Std: `testing/synctest` GA (`synctest.Test`, `synctest.Wait`); `sync.WaitGroup.Go`; `http.CrossOriginProtection` (CSRF via
  Fetch metadata); many `os.Root` methods (`ReadFile`, `WriteFile`, `MkdirAll`, `RemoveAll`, `Rename`, ...); `T.Attr`, `T.Output`;
  `slog.GroupAttrs`; `reflect.TypeAssert`; `encoding/json/v2` behind `GOEXPERIMENT=jsonv2`.
- go.mod `ignore` directive; `go doc -http`; `work` package pattern; `go get` no longer adds a `toolchain` line automatically.
- Vet: `waitgroup` (misplaced `Add`), `hostport` (use `net.JoinHostPort`).
- TLS: SHA-1 signatures disallowed in TLS 1.2.

## Go 1.24 (2025-02-11), unsupported
- Generic type aliases. go.mod `tool` directive (`go get -tool`, `go tool <name>`). `go build -json`. VCS tag version stamped in binary.
- Swiss-table maps; `os.Root`/`os.OpenRoot`; `weak` package; `runtime.AddCleanup` (replaces `SetFinalizer`).
- `testing.B.Loop`, `T.Context`, `T.Chdir`. `encoding/json` `omitzero` (uses `IsZero()` if present).
- Crypto: `crypto/mlkem`, `crypto/hkdf`, `crypto/pbkdf2`, `crypto/sha3`; FIPS 140-3 module (`GOFIPS140`, `GODEBUG=fips140=on`);
  `cipher.NewGCMWithRandomNonce`; `crypto/rand.Read` never fails, `rand.Text()`; X25519MLKEM768 TLS default; RSA < 1024 bits rejected.
- `strings`/`bytes` iterators: `Lines`, `SplitSeq`, `FieldsSeq`, ... `math/rand` top-level `Seed` is a no-op.
- `go:wasmexport`. Vet `tests` analyzer; printf flags non-constant format strings.

## Go 1.23 (2024-08-13) and 1.22 (2024-02-06), long unsupported
- 1.23: range-over-func iterators, `iter` package, `slices`/`maps` iterator funcs (`slices.Collect`, `maps.Keys`, `slices.Sorted`),
  `unique` package, timers collectible when unreferenced and their channels unbuffered, opt-in telemetry.
- 1.22: per-iteration loop variables (for modules with `go >= 1.22`), `range` over int, `math/rand/v2`,
  `net/http.ServeMux` method + wildcard patterns (`"GET /items/{id}"`, `r.PathValue("id")`), `go vet` loopclosure obsolete.
- 1.21: `min`, `max`, `clear` builtins; `log/slog`, `slices`, `maps`, `cmp` packages; toolchain management (`toolchain` line).
