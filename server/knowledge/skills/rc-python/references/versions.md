# rc-python: version and release detail (as of 2026-10-09)

All values checked on the listed primary source on 2026-10-09 unless marked (unverified).

## CPython lines (devguide.python.org/versions)

| Line | Status | First release | EOL (planned) | Latest patch seen |
|------|--------|---------------|---------------|-------------------|
| 3.16 | feature (main) | 2027-10 (planned) | 2032-10 | - |
| 3.15 | bugfix | 2026-10-09 | 2031-10 | 3.15.0 |
| 3.14 | bugfix | 2025-10-07 | 2030-10 | 3.14.8 (2026-09-30) |
| 3.13 | security | 2024-10-07 | 2029-10 | 3.13.16 (2026-09-30) |
| 3.12 | security | 2023-10-02 | 2028-10 | 3.12.15 (2026-09-30) |
| 3.11 | security | 2022-10-24 | 2027-10 | 3.11.17 (2026-10-01) |
| 3.10 | **EOL** | 2021-10 | 2026-10-01 | 3.10.22 (last) |
| 3.9 | EOL | - | 2025-10-31 | - |
| 3.8 | EOL | - | 2024-10-07 | - |

Since 3.13 (PEP 602 update): 2 years bugfix + 3 years security = 5 years.

## 3.13 (2024-10-07) highlights
- New colour REPL (multiline edit, F1 help, F2 history, F3 paste). `PYTHON_BASIC_REPL` to disable.
- Free-threaded build experimental (PEP 703), `python3.13t`. Experimental JIT (PEP 744), build flag only.
- PEP 667 defined `locals()` semantics. `copy.replace()`. `dbm.sqlite3` default dbm backend.
- Typing: TypeVar/ParamSpec defaults (PEP 696), `ReadOnly` for TypedDict (PEP 705), `TypeIs` (PEP 742), `warnings.deprecated` (PEP 702).
- Removed (PEP 594 "dead batteries"): aifc, audioop, cgi, cgitb, chunk, crypt, imghdr, mailcap, msilib, nis, nntplib, ossaudiodev, pipes, sndhdr, spwd, sunau, telnetlib, uu, xdrlib. Also lib2to3/2to3, tkinter.tix.
- iOS (PEP 730) and Android (PEP 738) tier 3.

## 3.14 (2025-10-07) highlights
- PEP 649/749 deferred annotations; new `annotationlib` (`Format.VALUE/FORWARDREF/STRING`).
  `from __future__ import annotations` is deprecated, removal not before 3.13 EOL (2029).
- PEP 750 t-strings: `t"..."` gives `string.templatelib.Template` (not `str`).
- PEP 758 `except A, B:` without parentheses (only when no `as`).
- PEP 765 `SyntaxWarning` for `return`/`break`/`continue` leaving `finally`.
- PEP 734 `concurrent.interpreters` + `concurrent.futures.InterpreterPoolExecutor`.
- PEP 779 free-threaded build "supported, no longer experimental" (phase II), still not the default build.
- JIT in official macOS/Windows binaries, off by default, `PYTHON_JIT=1`; "10% slower to 20% faster".
- PEP 784 `compression.zstd` (+ `compression.gzip` etc. re-exports); zstd in tarfile/zipfile/shutil.
- PEP 768 `sys.remote_exec`, `python -m pdb -p PID`; `python -m asyncio ps|pstree PID`.
- pathlib: `Path.copy()`, `copy_into()`, `move()`, `move_into()`, `Path.info`.
- multiprocessing default start method on Linux/BSD: `forkserver` (was `fork`).
- tarfile extraction `filter` defaults to `'data'`.
- `asyncio.get_event_loop()` raises `RuntimeError` when no loop; asyncio policy system deprecated (removal 3.16);
  `asyncio.iscoroutinefunction` deprecated (use `inspect.iscoroutinefunction`).
- Removed: `ast.Num/Str/Bytes/NameConstant/Ellipsis`, asyncio child watchers, `urllib` `URLopener/FancyURLopener`, `pkgutil.get_loader/find_loader`.
- Release signing: Sigstore only, PGP signatures discontinued.

## 3.15 (2026-10-09) highlights
- PEP 810 explicit lazy imports: `lazy import json`, `lazy from pathlib import Path`.
- PEP 814 `frozendict` builtin; PEP 661 `sentinel` builtin; PEP 798 `[*xs for xs in lists]`.
- PEP 686 UTF-8 is the default encoding regardless of locale (`PYTHONUTF8=0` to opt out).
- PEP 799 `profiling` package: `profiling.tracing` (= cProfile) and `profiling.sampling` (Tachyon sampling profiler,
  attaches to running PIDs, flamegraph output). `profile` module deprecated, removal in 3.17.
- PEP 803 stable ABI for free-threaded builds (`abi3t`); macOS installer installs free-threading by default.
- JIT upgraded, still experimental (project reports ~7-8% geomean speedup on some platforms).
- Typing: PEP 728 TypedDict extra items, PEP 747 `TypeForm`, PEP 800 disjoint bases.
- `re.prefixmatch()` added; `re.match()` soft-deprecated (still works, no removal planned).
- `asyncio.TaskGroup.cancel()`. `tomllib` supports TOML 1.1.
- Removed: `sre_compile/sre_constants/sre_parse`, `http.server.CGIHTTPRequestHandler`, `pathlib.PurePath.is_reserved`,
  `platform.java_ver`, `zipimporter.load_module`, `glob.glob0/glob1`.
- `typing.ByteString` / `collections.abc.ByteString` dropped from `__all__`; removal scheduled for 3.17.

## Ecosystem versions seen on PyPI (2026-10-09)

| Package | Version | Requires Python | Note |
|---------|---------|-----------------|------|
| uv | 0.13.0 (2026-10-09) | - | default Python 3.15; `pylock.toml` export/install in preview |
| ruff | 0.17.0 (2026-10-09) | - | default target now 3.11 when unset |
| ty | 0.0.85 (2026-10-06) | - | pre-1.0 (beta) |
| pyrefly | 1.3.2 | >=3.8 | Meta; Production/Stable |
| mypy | 2.4.0 | >=3.10 | 2.0: `--local-partial-types`, `--strict-bytes` default |
| pyright / basedpyright | 1.1.414 / 1.40.2 | - | |
| pip | 26.2.1 (2026-08-04) | - | `pip lock`, `--group`, `-r pylock.toml` |
| setuptools / hatchling | 84.0.0 / 1.32.4 | >=3.10 | |
| poetry / pdm / pipx / pipenv | 2.5.1 / 2.29.2 / 1.17.12 / 2026.8.0 | | all maintained |
| pytest / pytest-asyncio | 9.1.1 / 1.4.0 | >=3.10 | |
| hypothesis / coverage | 6.168.5 / 7.16.2 | >=3.10 | |
| tox / nox | 4.65.0 / 2026.8.17 | >=3.10 | |
| fastapi / starlette / uvicorn | 0.143.0 / 1.7.0 / 0.54.0 | >=3.10 | |
| pydantic / pydantic-settings | 2.14.0 (2026-10-08) / 2.15.0 | >=3.10 | |
| django | 6.1.2 | >=3.12 | 5.2 LTS until 2028-04; 6.2 LTS due 2027-04 |
| flask / litestar | 3.1.3 / 2.24.0 | | |
| sqlalchemy / alembic | 2.1.4 / 1.20.0 | >=3.11 / >=3.10 | |
| numpy / pandas / polars | 2.5.3 / 3.0.6 / 2.0.0 | >=3.12 / >=3.11 / >=3.10 | |
| torch | 2.14.1 | >=3.10 | |
| typer / click | 0.27.3 / 8.5.0 | >=3.10 | |
| structlog / attrs / msgspec | 26.1.0 / 26.1.0 / 0.22.0 | | |
| pip-audit / bandit / py-spy | 2.10.1 / 1.9.4 / 0.4.2 | | |
| black / isort / flake8 | 26.10.0 / 9.0.2 / 7.4.1 | | maintained, but ruff replaces all three |

## Framework release notes worth knowing
- Django 6.0 (2025-12-03): `django.tasks` background tasks framework (you still need a worker backend), built-in CSP
  (`SECURE_CSP`, `ContentSecurityPolicyMiddleware`), template partials (`{% partialdef %}`), modern email API,
  `DEFAULT_AUTO_FIELD` defaults to `BigAutoField`, Python 3.12+.
- Django 6.1 (2026-08-05, Python 3.12-3.14): fetch modes (`FETCH_PEERS`, `FETCH_RAISE`), DB-level
  `on_delete=DB_CASCADE/DB_SET_NULL/DB_SET_DEFAULT`, `MAILERS` setting, calendar versioning next: after 6.2 LTS (2027-04)
  comes Django 2028 (January releases, each with 3 years support).
- FastAPI: dropped Python 3.8 (0.125.0) and 3.9 (0.129.0); `pydantic.v1` support dropped (0.128.0);
  `ORJSONResponse`/`UJSONResponse` deprecated, JSON is serialized by Pydantic in Rust when a return type or
  response model exists (0.131/0.130); `strict_content_type` on by default for JSON bodies (0.132.0);
  routers keep `APIRouter`/`APIRoute` instances (0.137.0); OpenTelemetry auto exporter setup opt-in (0.143.0).
- SQLAlchemy 2.1: Python 3.11+, `greenlet` no longer installed by default (use the `[asyncio]` extra),
  `Select[int, str]` typing instead of `Select[Tuple[...]]`, autoflush for all executions, t-string support.
- pandas 3.0 (2026-01-21): Copy-on-Write always, chained assignment does nothing, `str` dtype by default,
  datetime parsing infers `us` resolution, zoneinfo instead of pytz, Python 3.11+, offset aliases `ME/QE/YE`.
- Polars 2.0: streaming engine default for lazy queries, string-to-temporal `cast` removed (use `str.to_datetime`),
  `concat(how="horizontal")` requires equal heights, `LazyFrame.profile()` removed.
- NumPy 2.5 (2026-06-21): Python 3.12+, `numpy.distutils` removed, many 2.0 deprecations expired.
- pytest 9.0 (2025-11-05): native `[tool.pytest]` TOML table, `strict` option, subtests; 9.1 (2026-06-13) `--max-warnings`.
- pytest-asyncio 1.0 (2025-05-26) removed the `event_loop` fixture.
- mypy 2.x: `--python-version 3.9` no longer supported; native parser + parallel checking (`-n auto`) default in 2.4.
- Astral (uv, ruff, ty): acquisition by OpenAI announced 2026-03-19; tools stay MIT/Apache open source (per announcement coverage).
