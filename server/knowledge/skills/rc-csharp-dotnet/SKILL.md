---
name: rc-csharp-dotnet
description: Current C# and .NET practice as of 2026-10 (.NET 10 LTS, .NET 11 RC, C# 14/15), covering ASP.NET Core minimal APIs/Blazor/SignalR, EF Core 10, Dapper, hosted services, Aspire, DI/config, OpenTelemetry, xUnit v3/Testcontainers, Native AOT, performance and security. Use for *.cs, *.csproj, *.sln/*.slnx, global.json, Directory.Packages.props, appsettings.json, Program.cs, dotnet CLI, or .NET upgrades.
---
# C# and .NET (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Your training data predates most of this. If a version or API here disagrees with your memory, trust this file and check the sources.
C# features by version and per-area detail: `references/language-and-libraries.md`.

## Currency check

| Thing | Current (2026-10) | Notes |
|---|---|---|
| .NET LTS | **10** (2025-11-11), patch 10.0.12 (2026-09-08) | Supported until **2028-11-14** |
| .NET 9 (STS) | 9.0.20 | Ends **2026-11-10** (STS is now 24 months) |
| .NET 8 (LTS) | 8.0.31 | Ends **2026-11-10**: same day as 9. Plan the move to 10 now |
| .NET 11 (STS) | **RC1** (2026-09-08, go-live license) | GA expected 2026-11; C# 15 default |
| Out of support | 7, 6, 5, Core 3.1 and older | No security patches |
| C# | **14** with .NET 10; **15** with .NET 11 | `LangVersion` follows the TFM; don't force `latest` on old TFMs |
| EF Core | **10** (LTS, until 2028-11-10); 11 in RC | EF10 needs the .NET 10 runtime; no .NET Framework |
| Aspire | **13.6** (2026-09-29), `Aspire.Hosting` 13.6.1 (2026-10-07) | Renamed from ".NET Aspire" in 13; polyglot (C#, TS, Python, Java, Rust) |
| .NET MAUI | **10** (10.0.110), supported to 2027-05-11 | MAUI 9 ended 2026-05-12 |
| Windows App SDK (WinUI 3) | 2.5.1 (2026-09-16) | |
| xUnit v3 | `xunit.v3` **4.0.2** (2026-10-09) | Package went 3.2.x -> 4.0 within the v3 line; .NET 8+ / net472+ |
| Testcontainers | 4.16.0 (2026-10-08) | |
| Dapper | 2.1.89 (2026-09-23) | Apache-2.0 |
| Hangfire / Quartz.NET | 1.8.25 (LGPLv3) / 4.4.0 (Apache-2.0) | |
| BenchmarkDotNet | 0.15.8 (2025-11-30) | |
| MediatR / AutoMapper | 14.2.0 / 16.2.0: **commercial** (RPL-1.5 or paid) | Since MediatR 13 / AutoMapper 15 (2025-07-02) |

Shipped in the last ~12 months (dated):
- **.NET 10 LTS (2025-11-11)**: C# 14 (extension members, `field`, `?.=` assignment, first-class spans); file-based apps (`dotnet run app.cs`, `#:package`, publish to Native AOT by default); `dotnet tool exec` / `dnx`; Microsoft.Testing.Platform in `dotnet test`; post-quantum ML-DSA/ML-KEM; `WebSocketStream`; JSON duplicate-property rejection and strict options.
- **ASP.NET Core 10**: minimal API validation (`AddValidation()`), OpenAPI 3.1 default + YAML, SSE (`TypedResults.ServerSentEvents`), Identity passkeys, cookie auth returns 401/403 for API endpoints, Blazor `[PersistentState]`, `NavigationManager.NotFound()`. Obsoleted: `WebHostBuilder`, `IWebHost`, `WithOpenApi()`, Razor runtime compilation.
- **EF Core 10**: complex types (optional, JSON, structs), `LeftJoin`/`RightJoin`, named query filters, `ExecuteUpdateAsync` with a normal lambda, SQL Server 2025 `vector`/`json` types, parameterized collections as padded scalar parameters.
- **.NET 11 RC1 (2026-09-08)**: runtime-native async (no preview flag for `net11.0`), C# 15 unions/`closed`/extension indexers/labeled `break`/`continue`, Zstandard, `Process` run-and-capture helpers, `DnsResolver`, LINQ `FullJoin`, async DataAnnotations validation, higher minimum CPU instruction sets. ASP.NET Core 11: SignalR auth refresh, OpenAPI 3.2, union types in minimal APIs, Blazor `CacheView`/`TempData`, antiforgery auto-enabled for Blazor Web Apps.

**Pick a version:** new production work targets **net10.0** (LTS). Use net11.0 only if the team takes yearly upgrades (STS, 24 months). Projects on .NET 8 or 9 must move to 10 before **2026-11-10**. Never start new work on .NET Framework.

### Older versions (legacy projects)
- **.NET 8 / C# 12** (until 2026-11-10): has primary constructors, collection expressions, keyed DI, `TimeProvider`, `FrozenDictionary`, Native AOT for minimal APIs, `IExceptionHandler`. No `field`, no extension blocks, no `?.=`, no `Lock` type, no built-in OpenAPI (`Microsoft.AspNetCore.OpenApi` arrived in 9; on 8 use Swashbuckle or NSwag), no HybridCache GA, no `AddValidation()`, no `LeftJoin`. EF Core 8: no complex-type JSON, no named filters; `Contains` on a list uses `OPENJSON`.
- **.NET 9 / C# 13**: `params` collections, `System.Threading.Lock`, `\e`, `MapStaticAssets`, built-in OpenAPI document (3.0), `BinaryFormatter` always throws. HybridCache shipped after 9 as `Microsoft.Extensions.Caching.Hybrid` (supports net472+ too).
- **.NET Framework 4.8.x**: maintenance only, follows the Windows lifecycle. No C# beyond what the old compiler supports without polyfills; don't add new projects. Migrate with GitHub Copilot app modernization (the .NET Upgrade Assistant is deprecated; still the fallback without Copilot or for VB projects).
- Don't upgrade a project's TFM, C# version or major packages unless the task asks; note the EOL risk instead.

## What changed / stop doing

| Old (stop) | New (do) | Since |
|---|---|---|
| `Startup.cs` with `ConfigureServices`/`Configure`, `WebHostBuilder`/`IWebHost` | `WebApplication.CreateBuilder(args)` in `Program.cs` (top-level statements) | .NET 6; WebHostBuilder obsolete in 10 |
| Swashbuckle by default | `builder.Services.AddOpenApi()` + `app.MapOpenApi()` (Microsoft.AspNetCore.OpenApi); UI via Scalar or Swagger UI pointed at the doc | .NET 9 |
| `.WithOpenApi()` on endpoints | Metadata (`.WithSummary()`, `.Produces<T>()`) + document/operation transformers | obsolete .NET 10 |
| Newtonsoft.Json by default | `System.Text.Json` with source generation (`JsonSerializerContext`); Newtonsoft only for legacy contracts | .NET Core 3.0+ |
| `BinaryFormatter`, `NetDataContractSerializer`, `SoapFormatter` | `System.Text.Json`, protobuf, MessagePack with explicit types | throws since .NET 9 |
| `IMemoryCache` + `IDistributedCache` glue, hand-rolled stampede locks | `HybridCache` (`AddHybridCache`, `GetOrCreateAsync`, tags) | GA after .NET 9 |
| Owned entities for value objects / JSON | EF complex types (`ComplexProperty(..., b => b.ToJson())`) | EF Core 10 |
| `GroupJoin` + `SelectMany` + `DefaultIfEmpty` for left joins | `LeftJoin(...)` / `RightJoin(...)`; `FullJoin` in 11 | .NET 10 / 11 |
| Load-modify-`SaveChanges` loops for bulk updates | `ExecuteUpdateAsync` / `ExecuteDeleteAsync` | EF Core 7 (lambda form 10) |
| Explicit backing field just to validate a setter | `field` keyword | C# 14 |
| Static helper classes / `this` extension methods only | `extension(T receiver) { ... }` blocks: properties, static members, operators | C# 14 |
| `if (x != null) x.P = v;` | `x?.P = v;` | C# 14 |
| `new List<T>(cap) { ... }`, `new[] { }` | Collection expressions `[a, b, ..rest]`; `[with(capacity: n), ..]` in C# 15 | C# 12 / 15 |
| Flag variables to exit nested loops, `goto` | Labeled `break outer;` / `continue outer;` | C# 15 |
| `abstract` base + `_ => throw` default arm | `closed` class hierarchy or `union` type: exhaustive switch | C# 15 |
| `lock (object)` | `private readonly Lock _gate = new();` then `lock (_gate)` | .NET 9 |
| `DateTime.Now` / `DateTime.UtcNow` in logic | Inject `TimeProvider` (`TimeProvider.System`; `FakeTimeProvider` in tests); store `DateTimeOffset` | .NET 8 |
| `async void` (except UI event handlers), `.Result`, `.Wait()`, `GetAwaiter().GetResult()` | `async Task` all the way; pass `CancellationToken` | always |
| `Task.Run` wrapping sync I/O in ASP.NET | Real async I/O APIs | always |
| `services.BuildServiceProvider()` inside registration | Options pattern, factory overloads, `IConfigureOptions<T>` | always |
| `WebClient`, `HttpWebRequest`, `new HttpClient()` per call | `IHttpClientFactory` / typed clients + `AddStandardResilienceHandler()` (Microsoft.Extensions.Http.Resilience) | Polly v8 based |
| `.sln` | `.slnx` (XML solution, `dotnet sln migrate`); `dotnet new sln` creates `.slnx` by default in SDK 10 | SDK 9.0.200+ / default SDK 10 |
| `dotnet add package` | `dotnet package add` (noun-first; old form still works) | SDK 10 |
| VSTest-only `dotnet test` | Microsoft.Testing.Platform (`"test": {"runner": "Microsoft.Testing.Platform"}` in global.json) | SDK 10 |
| Razor runtime compilation | Hot Reload / `dotnet watch` | obsolete .NET 10 |
| MediatR / AutoMapper as reflexive defaults | Plain handlers/services and hand-written mapping (or Mapperly source generator); check license before adding | 2025-07 licensing |
| WebForms, WCF server, .NET Remoting, AppDomains | Razor Pages/Blazor, gRPC or CoreWCF, HTTP APIs, processes/containers | not in .NET Core |
| Xamarin | .NET MAUI (or Avalonia/Uno) | Xamarin support ended 2024-05-01 |

## Do this

### Project layout and build
```
repo/
  global.json                  # pins SDK: {"sdk":{"version":"10.0.1xx","rollForward":"latestFeature"}}
  Directory.Build.props        # shared: TargetFramework, Nullable, TreatWarningsAsErrors, analyzers
  Directory.Packages.props     # central package management: <ManagePackageVersionsCentrally>true
  .editorconfig                # style + analyzer severities (dotnet format reads it)
  App.slnx
  src/Api/Api.csproj           # <Project Sdk="Microsoft.NET.Sdk.Web">
  tests/Api.Tests/Api.Tests.csproj
```
`Directory.Build.props` baseline:
```xml
<Project>
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <AnalysisLevel>latest-recommended</AnalysisLevel>
    <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
    <NuGetAudit>true</NuGetAudit><NuGetAuditMode>all</NuGetAuditMode>
    <RestorePackagesWithLockFile>true</RestorePackagesWithLockFile>
  </PropertyGroup>
</Project>
```
- `Nullable` is on in every new template; treat nullable warnings as bugs, not noise. Don't sprinkle `!`.
- Central package management: versions only in `Directory.Packages.props`, `<PackageReference Include="X" />` without `Version` in projects.
- Lock files + `dotnet restore --locked-mode` in CI.

### Least dependency
Before adding a package, check the BCL/ASP.NET Core first: JSON (`System.Text.Json`), HTTP resilience (`Microsoft.Extensions.Http.Resilience`), caching (`HybridCache`), validation (`AddValidation()`, DataAnnotations), rate limiting (`AddRateLimiter`), output caching, health checks, OpenAPI, background work (`BackgroundService`), mapping (a constructor or `static` method), mediator (a method call). Don't add MediatR, AutoMapper, FluentAssertions (v8+ needs a paid license for commercial use), Newtonsoft or Swashbuckle unless the project already uses them or the task asks. Check license changes before upgrading any of them.

### Minimal API (the default for new HTTP APIs)
```csharp
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddProblemDetails();
builder.Services.AddValidation();            // .NET 10
builder.Services.AddOpenApi();
builder.Services.AddDbContext<AppDb>(o => o.UseNpgsql(builder.Configuration.GetConnectionString("db")));
builder.Services.AddAuthentication().AddJwtBearer();
builder.Services.AddAuthorization();
builder.Services.AddRateLimiter(o => o.AddFixedWindowLimiter("api", l => { l.PermitLimit = 100; l.Window = TimeSpan.FromMinutes(1); }));

var app = builder.Build();
app.UseExceptionHandler();
app.UseAuthentication(); app.UseAuthorization(); app.UseRateLimiter();
if (app.Environment.IsDevelopment()) app.MapOpenApi();

var orders = app.MapGroup("/orders").RequireAuthorization().RequireRateLimiting("api");
orders.MapGet("/{id:int}", async Task<Results<Ok<OrderDto>, NotFound>> (int id, AppDb db, CancellationToken ct) =>
    await db.Orders.Where(o => o.Id == id).Select(o => new OrderDto(o.Id, o.Total)).FirstOrDefaultAsync(ct)
        is { } dto ? TypedResults.Ok(dto) : TypedResults.NotFound());
app.Run();

public record OrderDto(int Id, decimal Total);
```
- Return `TypedResults` + `Results<...>` (OpenAPI gets real response types). Errors as RFC 9457 ProblemDetails.
- Controllers are still supported: use them when a team already has them or needs filters/conventions at scale. Don't mix styles inside one feature.
- Group endpoints per feature in static `MapXxx(this IEndpointRouteBuilder)` methods; no Startup class.

### Auth
- Prefer an external OIDC provider (`AddOpenIdConnect`, `AddJwtBearer`) over rolling tokens. Validate issuer, audience, lifetime, signing keys (defaults do; don't turn them off).
- ASP.NET Core Identity for local accounts: passkeys supported since .NET 10. `MapIdentityApi<TUser>()` exists for SPA/mobile but is not a full OAuth server (no third-party clients); use an OIDC server (e.g. OpenIddict, Duende IdentityServer: commercial license) when you need one.
- Cookie auth for API endpoints now returns 401/403 instead of redirecting (.NET 10); don't add custom redirect suppression.
- Authorization: policies (`AddAuthorizationBuilder().AddPolicy(...)`), resource-based checks via `IAuthorizationService`. Never trust a client-supplied user id.

### Blazor
- Blazor Web App template; pick render mode per component/page: static SSR (default), `@rendermode InteractiveServer`, `InteractiveWebAssembly`, `InteractiveAuto`. Keep pages static SSR unless they need interactivity.
- `[PersistentState]` (.NET 10) to carry prerendered state into interactive mode; `NavigationManager.NotFound()` for 404s.
- Forms: `EditForm` + `DataAnnotationsValidator`; antiforgery is automatic (auto-enabled middleware in .NET 11). InteractiveServer keeps a circuit per user: budget memory and reconnects.

### Real time, RPC, caching
- SignalR for push to browsers (auth refresh without reconnect in .NET 11). Scale out with Azure SignalR or a Redis backplane.
- gRPC (`Grpc.AspNetCore`) for service-to-service; JSON transcoding if browsers also need it.
- Output caching (`AddOutputCache`, `.CacheOutput()`) for whole responses; `HybridCache` for data. Response caching middleware is for HTTP-cache headers only.

### EF Core
- One `DbContext` per unit of work (scoped). `AddDbContextPool` for hot paths.
- Reads: `AsNoTracking()`, project to DTOs with `Select`, paginate with keyset (`Where(x => x.Id > last).Take(n)`).
- **N+1**: never query inside a loop or rely on lazy loading; use `Include` (+ `AsSplitQuery()` for multiple collections) or a projection.
- Bulk: `ExecuteUpdateAsync(s => { s.SetProperty(b => b.Views, 0); })`, `ExecuteDeleteAsync()`; they bypass change tracking and interceptors.
- Migrations: `dotnet ef migrations add X`; deploy with an idempotent script (`dotnet ef migrations script --idempotent`) or a migration bundle, not `Database.Migrate()` at app startup on multi-instance deployments.
- Compiled queries (`EF.CompileAsyncQuery`) only after a profiler shows query compilation cost; compiled models (`dotnet ef dbcontext optimize`) for big models / AOT.
- Raw SQL: `FromSql($"... {param}")` / `SqlQuery<T>($"...")` (interpolations become parameters). `FromSqlRaw` with concatenation now raises an analyzer warning (EF10).
- Dates: SQL Server `datetimeoffset`, PostgreSQL `timestamptz` with UTC `DateTime` or `DateTimeOffset` (Npgsql rejects non-UTC kinds for timestamptz).

### Dapper
Use for hot read paths or reporting where you want hand-written SQL. Always parameters: `conn.QueryAsync<Order>("select * from orders where customer_id = @id", new { id })`. Never interpolate. Dapper.AOT exists for AOT (unverified current status).

### Background work
- `BackgroundService` for in-process loops; create a scope per iteration (`IServiceScopeFactory`) for scoped services; honor `stoppingToken`; catch and log per iteration (an unhandled exception stops the host since .NET 6 by default).
- Use `PeriodicTimer` for intervals. For queues: `System.Threading.Channels`.
- Persistent/retryable jobs: Hangfire (LGPL; Pro is paid) or Quartz.NET (Apache-2.0, cron, clustering). Cloud-native: a queue + worker service. Don't fire-and-forget `Task.Run` from a request.

### DI, configuration, options
- Constructor injection (primary constructors are fine: `public sealed class OrderService(AppDb db, TimeProvider clock)`). Remember primary-constructor parameters are mutable captures, not `readonly` fields.
- Lifetimes: singleton must not depend on scoped (enable `ValidateScopes`/`ValidateOnBuild`, on in Development by default). Keyed services: `AddKeyedSingleton<IStore, S3Store>("s3")` + `[FromKeyedServices("s3")]`.
- Options: `services.AddOptions<SmtpOptions>().BindConfiguration("Smtp").ValidateDataAnnotations().ValidateOnStart();`
- Config order: appsettings.json < appsettings.{Env}.json < user secrets (Development) < env vars (`Section__Key`) < args. Secrets: user-secrets locally, a vault (Key Vault, AWS Secrets Manager, etc.) or platform env in production; never in appsettings committed to git.

### Logging and telemetry
- `ILogger<T>` with message templates (`"Order {OrderId} paid"`), not string interpolation. Hot paths: `[LoggerMessage]` source-generated methods.
- OpenTelemetry: `builder.Services.AddOpenTelemetry().WithTracing(t => t.AddAspNetCoreInstrumentation().AddHttpClientInstrumentation()).WithMetrics(m => m.AddAspNetCoreInstrumentation()).UseOtlpExporter();` Configure via standard `OTEL_*` env vars. Aspire's ServiceDefaults project wires this for you.
- Never log tokens, passwords, full request bodies or PII; EF10 redacts inlined constants by default; keep `EnableSensitiveDataLogging` off outside dev.

### Aspire
Use it for local orchestration of multi-service apps (AppHost declares Postgres/Redis/containers/projects; dashboard shows logs, traces, metrics) and for deploy manifests. Optional: a single API + DB doesn't need it. Aspire 13 needs the .NET 10 SDK; 13.6's multithreaded MSBuild (`-mt`) builds only kick in with SDK 11.0.100-rc.1+ (older SDKs keep the old behavior).

### Async and modern C# idioms
```csharp
public sealed class Clock(TimeProvider time)            // primary constructor
{
    public string Name { get; set => field = value?.Trim() ?? ""; } = "";   // C# 14 field
    public DateTimeOffset Now => time.GetUtcNow();
}
static string Describe(Shape s) => s switch               // pattern matching
{
    Circle { Radius: > 10 } => "big circle",
    Circle c => $"circle {c.Radius}",
    Rect(var w, var h) when w == h => "square",
    _ => "other",
};
```
- `ValueTask` only for hot paths that usually complete synchronously; never await a `ValueTask` twice.
- `ConfigureAwait(false)` in libraries; unnecessary in ASP.NET Core app code (no sync context).
- `IAsyncEnumerable<T>` + `await foreach` for streams; `System.Linq.AsyncEnumerable` is in-box since .NET 10 (drop `System.Linq.Async` package).

### Native AOT and trimming
- `<PublishAot>true</PublishAot>`: fast start, small memory; good for functions, CLIs, minimal APIs. Template: `dotnet new webapiaot`.
- Not supported or limited: MVC controllers, Razor Pages/Blazor Server, reflection-heavy libs (AutoMapper, Newtonsoft), EF Core (experimental / limited, unverified). Use `CreateSlimBuilder`, STJ source generation, `[DynamicallyAccessedMembers]`; treat every IL2xxx/IL3xxx warning as an error.
- File-based apps publish AOT by default (`#:property PublishAot=false` to opt out).

### File-based apps (scripts)
```csharp
#!/usr/bin/env dotnet
#:package Humanizer@2.*
#:property PublishAot=false
using Humanizer;
Console.WriteLine(TimeSpan.FromMinutes(90).Humanize());
```
`dotnet run tool.cs`; `dotnet project convert tool.cs` turns it into a project (`--dry-run`, `-o <dir>`). `#:project` references a csproj; .NET 11 adds `#:include`.

### Desktop, mobile, games
- **MAUI**: supported, yearly with .NET; a version is supported until 6 months after the next ships. Fine for line-of-business apps on .NET-heavy teams; weigh Avalonia or Uno Platform (both cross-platform incl. Linux), or a native/Flutter/React Native app (see rc-cross-platform). Blazor Hybrid (`BlazorWebView`) to share Razor UI.
- **WPF / WinForms**: maintained and shipping with every .NET (WPF Fluent styles in progress, WinForms clipboard + JSON APIs); fine for Windows-only internal apps. **WinUI 3** via Windows App SDK 2.x for new modern Windows UI. UWP: no new work.
- **Unity**: Unity 6.3 LTS compiles C# 9 on Mono/IL2CPP, with gaps (no `init` setters, records unusable for serialization). Don't use C# 10+ syntax or newer BCL APIs in Unity scripts; a CoreCLR move is announced but not shipped (unverified).

## Security
- **Injection**: EF/Dapper parameters only; `FromSqlRaw`/`ExecuteSqlRaw` never with user input. Validate identifiers (column/table names) against an allowlist.
- **Deserialization**: no `BinaryFormatter` (throws), no `TypeNameHandling.All/Auto` in Newtonsoft, STJ polymorphism only with declared `[JsonDerivedType]`. Set `MaxDepth` and request size limits. .NET 10 STJ can reject duplicate properties: set `AllowDuplicateProperties = false` (or use `JsonSerializerOptions.Strict`) for security-sensitive payloads.
- **CSRF**: `UseAntiforgery()` + antiforgery tokens for cookie-authenticated form posts (Razor Pages/MVC/Blazor do it automatically); minimal API `[FromForm]` endpoints require tokens unless `DisableAntiforgery()`. Bearer-token APIs are not CSRF targets: don't also accept cookies there. `SameSite=Lax` minimum.
- **Data Protection**: in multi-instance or container deploys persist keys (`PersistKeysToDbContext`/blob/Redis) and protect them (`ProtectKeysWith...`); otherwise cookies and antiforgery tokens break on restart and keys sit unencrypted.
- **Secrets**: user-secrets for dev only, a vault or platform secrets in prod; managed identity over connection-string passwords where available. `NuGetAudit` + `dotnet list package --vulnerable` in CI.
- **Headers/transport**: `UseHsts()` + `UseHttpsRedirection()` in prod; set CSP in Blazor/Razor apps; CORS with explicit origins, never `AllowAnyOrigin` with credentials.
- **Abuse**: `AddRateLimiter` on auth and expensive endpoints; request body size limits (Kestrel `MaxRequestBodySize`); timeouts (`AddRequestTimeouts`).
- **Crypto**: `RandomNumberGenerator` for tokens, `Rfc2898DeriveBytes.Pbkdf2` or Identity's `PasswordHasher` for passwords, `AesGcm` for AEAD; never `MD5`/`SHA1` for security, never `System.Random` for secrets. Post-quantum (ML-KEM, ML-DSA) available in .NET 10 where the OS supports it.
- **Privacy**: log and store only what the feature needs; no third-party telemetry SDKs by default (SDK CLI telemetry: `DOTNET_CLI_TELEMETRY_OPTOUT=1`).

## Performance & quality
- Measure first: BenchmarkDotNet (`[MemoryDiagnoser]`, Release build, no debugger) for micro; `dotnet-counters`, `dotnet-trace`, `dotnet-gcdump` and OpenTelemetry metrics for apps. Targets: p95 latency and allocations/request, not averages.
- Allocation tools: `Span<T>`/`ReadOnlySpan<T>` (implicit conversions in C# 14), `stackalloc` for small buffers, `ArrayPool<T>.Shared`, `string.Create`, `SearchValues<T>`, `FrozenDictionary` for read-mostly lookups, `CompositeFormat` for repeated formatting.
- JSON: STJ source generation (`[JsonSerializable]`) removes reflection cost and is required for AOT.
- GC: Server GC is the ASP.NET default; in small containers consider `<ConcurrentGarbageCollection>` defaults and DATAS (dynamic adaptation, default with Server GC since .NET 9) before hand-tuning `GCHeapHardLimit`. Set container memory limits; .NET respects cgroups.
- EF: check generated SQL (`ToQueryString()`, logging), indexes, `AsNoTracking`, projections; avoid cartesian explosion with `AsSplitQuery`.
- `HttpClient` via factory (socket reuse, DNS refresh). Avoid `async` over sync and thread-pool starvation (watch `ThreadPool.QueueLength` in counters).
- Analyzers on (`AnalysisLevel latest-recommended`); CA1873 (expensive logging args) and CA18xx perf rules are worth fixing.

## Testing & tooling
- `dotnet new xunit3` (xUnit v3, MTP runner; on SDK 10 first `dotnet new install xunit.v3.templates`, SDK 11 templates include v3) / `nunit` / `mstest`. All three are current; keep what the repo uses. xUnit v3 test projects are executables; `TestContext.Current.CancellationToken` for cancellation.
- Integration: `WebApplicationFactory<Program>` (.NET 10 source-generates `public partial class Program`; add it by hand only on .NET 8/9) + Testcontainers (`PostgreSqlBuilder`, `MsSqlBuilder`, `RedisBuilder`) instead of EF InMemory or SQLite stand-ins (different SQL semantics).
- Time and randomness: `FakeTimeProvider` (Microsoft.Extensions.TimeProvider.Testing). HTTP: a stub `HttpMessageHandler` or WireMock.Net.
- Assertions: built-in `Assert` is enough; Shouldly/AwesomeAssertions are free alternatives to FluentAssertions (v8+: free for OSS/non-commercial, paid for commercial use).
- Mocks: NSubstitute or Moq; prefer hand-written fakes for your own interfaces.
- Formatting/linting: `dotnet format` (style + analyzers from .editorconfig) in CI with `--verify-no-changes`; Roslyn analyzers built into the SDK; optional Meziantou.Analyzer / Roslynator.
- CI: `dotnet restore --locked-mode`, `dotnet build -c Release --no-restore`, `dotnet test --no-build`, coverage via `Microsoft.Testing.Extensions.CodeCoverage` or coverlet. Containers: `dotnet publish /t:PublishContainer` (no Dockerfile needed; chiseled/distroless base images).
- `dotnet tool exec` / `dnx` to run tools without installing; local tools in `.config/dotnet-tools.json` (e.g. `dotnet-ef`).

## Common mistakes in AI-written code
- Generating `Startup.cs`, `IWebHostBuilder`, `UseMvc`, `services.AddMvc()` for new apps.
- Adding Swashbuckle, Newtonsoft.Json, AutoMapper or MediatR to a fresh project by reflex (licenses changed for the last two).
- `async void` methods, `.Result`/`.Wait()`, missing `CancellationToken`, `Task.Run` in controllers.
- `new HttpClient()` per request; `HttpClient` in a singleton without `PooledConnectionLifetime`.
- `DateTime.Now` in business logic and tests; storing local times; `DateTime` without `Kind` sent to Npgsql timestamptz.
- Injecting a scoped `DbContext` into a singleton or `BackgroundService` constructor; sharing one `DbContext` across threads (`Parallel.ForEach` over a context).
- Lazy loading + loops (N+1); `ToList()` before `Where` (client evaluation); `Count()` to check existence instead of `Any()`.
- `Database.EnsureCreated()` in production; `Migrate()` at startup across many replicas.
- `FromSqlRaw($"...{input}")`: interpolation into a raw API is NOT parameterized.
- Using `record` for EF entities (value equality breaks change tracking); using `record` with `required` + `init` in Unity (not supported).
- Invented APIs: `builder.Services.AddSwaggerGen` with `MapOpenApi` mixed incorrectly, `app.UseOpenApi()`, `IHostedService.ExecuteAsync` (it's `BackgroundService.ExecuteAsync`), `HybridCache.GetAsync` (use `GetOrCreateAsync`), `TypedResults.Problem` confused with `Results.Problem` return types.
- C# 15 syntax (`union`, `closed`, `with(...)` in collections, labeled `break`) in a net10.0 project: compile error. C# 14 syntax (`field`, `extension`) in net8.0/net9.0 projects.
- Using `field` as an identifier inside property accessors in C# 14 (now a keyword; use `@field`).
- Treating `<Nullable>` warnings as noise and suppressing with `!` or `#nullable disable`.
- Native AOT with controllers/reflection; ignoring trim warnings.
- Catching `Exception` and returning 200; leaking exception details in prod instead of ProblemDetails.
- Forgetting `UseAuthentication()` before `UseAuthorization()`, or `UseRateLimiter()`/`UseCors()` in the wrong order.

## Before you ship
- [ ] TFM is a supported version (net10.0 now; net8/net9 end 2026-11-10); SDK pinned in `global.json`.
- [ ] `dotnet build -c Release` clean with `TreatWarningsAsErrors`, nullable on, analyzers on; `dotnet format --verify-no-changes` passes.
- [ ] Tests pass: unit + `WebApplicationFactory` integration against real DB via Testcontainers.
- [ ] No vulnerable or license-changed packages (`NuGetAudit`, `dotnet list package --vulnerable --include-transitive`).
- [ ] Secrets only from a vault/env; Data Protection keys persisted and protected; HTTPS/HSTS, CORS, CSP, antiforgery, rate limits configured.
- [ ] Errors return ProblemDetails; no stack traces in prod; logs structured, no PII/tokens; OpenTelemetry exporting.
- [ ] EF migrations reviewed and applied by script/bundle; rollback plan (down script or forward fix) written.
- [ ] Health checks (`MapHealthChecks`) for liveness/readiness; graceful shutdown honors `CancellationToken`.
- [ ] Blazor/Razor UI: labels, keyboard focus, contrast checked (see rc-ui-ux-design).
- [ ] Container image built with `PublishContainer` or a pinned base image; runs as non-root.

## Sources
All accessed 2026-10-09.
- https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core : .NET versions, patches, LTS/STS, EOL dates (8 and 9 end 2026-11-10, 10 ends 2028-11-14).
- https://dotnet.microsoft.com/en-us/platform/support/policy/maui : MAUI 10 support to 2027-05-11, MAUI 9 ended.
- https://devblogs.microsoft.com/dotnet/dotnet-11-rc-1/ : .NET 11 RC1 (2026-09-08), go-live, component highlights.
- https://learn.microsoft.com/en-us/dotnet/core/whats-new/dotnet-11/overview : .NET 11 runtime/libraries/SDK, C# 15 list, GA expected 2026-11.
- https://learn.microsoft.com/en-us/dotnet/core/whats-new/dotnet-10/overview , .../dotnet-10/sdk : .NET 10 highlights, file-based apps, dnx, MTP, CLI noun-first commands.
- https://learn.microsoft.com/en-us/dotnet/csharp/whats-new/csharp-14 : extension members, field, null-conditional assignment, spans.
- https://learn.microsoft.com/en-us/dotnet/csharp/whats-new/csharp-15 : unions, closed, extension indexers, labeled break/continue, collection arguments, memory safety (preview).
- https://learn.microsoft.com/en-us/aspnet/core/release-notes/aspnetcore-10.0 : validation, OpenAPI 3.1, SSE, passkeys, cookie 401/403, obsoletions.
- https://learn.microsoft.com/en-us/aspnet/core/release-notes/aspnetcore-11 : SignalR auth refresh, OpenAPI 3.2, unions in minimal APIs, antiforgery change (page updated 2026-10-08).
- https://learn.microsoft.com/en-us/aspnet/core/release-notes/aspnetcore-9.0 : MapStaticAssets; HybridCache shipped after .NET 9.
- https://learn.microsoft.com/en-us/aspnet/core/performance/caching/hybrid : HybridCache API, tags, stampede protection, AOT notes.
- https://learn.microsoft.com/en-us/ef/core/what-is-new/ef-core-10.0/whatsnew : EF10 LTS dates, complex types, LeftJoin, named filters, ExecuteUpdate lambda, raw SQL analyzer, log redaction.
- https://learn.microsoft.com/en-us/ef/core/what-is-new/ef-core-11.0/whatsnew : EF11 features (FullJoin, MaxBy, migrations --add, SQL Server compat level 160 default).
- https://learn.microsoft.com/en-us/dotnet/core/compatibility/serialization/9.0/binaryformatter-removal : BinaryFormatter always throws from .NET 9.
- https://learn.microsoft.com/en-us/dotnet/desktop/wpf/whats-new/net100 : WPF in .NET 10 (Fluent styles, clipboard).
- https://aspire.dev/whats-new/aspire-13-6/ : Aspire 13.6 (2026-09-29), polyglot, SDK 11 requirement.
- https://www.jimmybogard.com/automapper-and-mediatr-commercial-editions-launch-today/ : AutoMapper 15 / MediatR 13 commercial, RPL-1.5, free tier (2025-07-02).
- https://www.nuget.org/packages/MediatR , /AutoMapper , /xunit.v3 , /Aspire.Hosting , /Testcontainers , /Dapper , /BenchmarkDotNet , /Hangfire.Core , /Quartz , /Microsoft.WindowsAppSDK : current package versions and licenses.
- https://xunit.net/releases/ : xUnit v3 release line (3.2.x -> 4.0.x).
- https://docs.unity3d.com/6000.3/Documentation/Manual/csharp-compiler.html : Unity 6.3 supports C# 9 with listed gaps.
- Fact-check pass (2026-10-10): https://learn.microsoft.com/en-us/dotnet/core/compatibility/10.0 (`dotnet new sln` -> .slnx);
  https://learn.microsoft.com/en-us/aspnet/core/breaking-changes/10/overview (WebHostBuilder/WithOpenApi/Razor runtime compilation obsolete);
  https://learn.microsoft.com/en-us/dotnet/core/whats-new/dotnet-10/libraries (`AllowDuplicateProperties`, `Strict`, ML-KEM/ML-DSA);
  https://learn.microsoft.com/en-us/dotnet/core/tools/dotnet-project-convert ; https://dotnet.microsoft.com/en-us/platform/support/policy/xamarin (ended 2024-05-01);
  https://learn.microsoft.com/en-us/dotnet/core/porting/upgrade-assistant-overview (deprecated for Copilot app modernization);
  https://www.nuget.org/packages/FluentAssertions (v8 commercial terms) ; https://aspire.dev/whats-new/aspire-13/ (rename).
