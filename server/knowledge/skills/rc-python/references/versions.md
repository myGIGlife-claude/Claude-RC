# rc-python: version and release detail (as of 2026-11-19)

Version tables below were re-checked 2026-11-19 (devguide, python.org/downloads, PyPI JSON). Rows not re-checked are marked (unverified)
and keep their 2026-10-09 value. Feature lists under 3.13-3.15 are unchanged from 2026-10-09.

## CPython lines (devguide.python.org/versions)

| Line | Status | First release | EOL (planned) | Latest patch seen |
|------|--------|---------------|---------------|-------------------|
| 3.16 | feature (main) | 2027-10-06 (planned) | 2032-10 | - |
| 3.15 | bugfix | 2026-10-09 | 2031-10 | 3.15.2 (downloads page: 2026-10-25) |
| 3.14 | bugfix | 2025-10-07 | 2030-10 | 3.14.10 (2026-10-25) |
| 3.13 | security | 2024-10-07 | 2029-10 | 3.13.18 (2026-10-25) |
| 3.12 | security | 2023-10-02 | 2028-10 | 3.12.16 (2026-10-25) |
| 3.11 | security | 2022-10-24 | 2027-10 | 3.11.18 (2026-10-25) |
| 3.10 | **EOL** | 2021-10-04 | 2026-10-01 | 3.10.22 (last) |
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

## Ecosystem versions seen on PyPI (2026-11-19 unless marked)

| Package | Version | Requires Python | Note |
|---------|---------|-----------------|------|
| uv | 0.13.9 (2026-11-17) | - | `pylock.toml` export/install and `uv audit` still preview |
| ruff | 0.17.6 (2026-11-19) | - | 0.17.0: default target 3.11 when unset |
| ty | 0.0.107 | - | pre-1.0 (beta) |
| pyrefly | 1.5.0 | (unverified) | Meta |
| mypy | 2.4.1 | >=3.10 | |
| pyright / basedpyright | 1.1.414 / 1.40.2 (unverified, 2026-10-09) | - | |
| pip | 26.3 | >=3.10 | `pip lock`, `--group`, `-r pylock.toml` |
| setuptools / hatchling | 84.0.0 / 1.32.4 (unverified, 2026-10-09) | >=3.10 | |
| poetry / pdm / pipx / pipenv | 2.5.1 / 2.29.2 / 1.17.12 / 2026.8.0 (unverified, 2026-10-09) | | all maintained |
| pytest / pytest-asyncio | 9.2.0 / 1.4.0 (asyncio unverified) | >=3.10 | |
| hypothesis / coverage | 6.168.5 / 7.16.2 (unverified, 2026-10-09) | >=3.10 | |
| tox / nox | 4.65.0 / 2026.8.17 (unverified, 2026-10-09) | >=3.10 | |
| fastapi / starlette / uvicorn | 0.165.0 (2026-11-18) / 1.7.0 / 0.54.0 (last two unverified) | >=3.11 (fastapi) | |
| pydantic / pydantic-settings | 2.14.x (changelog: 2.14.4, 2026-11-19; unverified) / 2.15.0 | >=3.11 (settings) | |
| django | 6.1.3 | >=3.12 | 5.2 LTS until 2028-04; 6.2 LTS due 2027-04 |
| flask / litestar | 3.1.3 / 2.24.0 (litestar unverified) | | |
| sqlalchemy / alembic | 2.1.9 / 1.20.0 (alembic unverified) | >=3.11 | |
| numpy / pandas / polars | 2.5.5 / 3.1.1 / 2.1.0 | >=3.12 / >=3.11 / >=3.11 | |
| torch | 2.15.0 | >=3.11 | |
| typer / click | 0.27.3 / 8.5.0 | >=3.10 | |
| structlog / attrs / msgspec | 26.1.0 / 26.1.0 / 0.22.0 | | |
| pip-audit / bandit / py-spy | 2.10.1 / 1.9.4 / 0.4.2 | | |
| black / isort / flake8 | 26.10.0 / 9.0.2 / 7.4.1 | | maintained, but ruff replaces all three |

## Changes 2026-10-09 to 2026-11-19
- pytest 9.2.0 (2026-10-27): `-c` errors on bad paths; `indirect`/`ids`/`scope` keyword-only in parametrize; plugin import failures give
  exit codes 4/3; `yield_fixture` removal planned for pytest 10 or 11. pytest 9.1 (2026-06-13) added official Python 3.15 support.
- uv 0.13.4 (2026-10-20) security release; 0.13.5 no more Docker images on Python 3.9/3.10; 0.13.8 owner-only plaintext credential files.
- ruff 0.17.1-0.17.6: no breaking changes listed; 0.17.0 stabilized TID254/TID255/RUF071.
- Django download page (2026-11-19): 6.0 mainstream ended 2026-08-04, extended to 2027-04; 6.1 mainstream to 2027-04, extended 2027-12;
  6.2 LTS 2027-04 (mainstream 2027-12, extended 2030-04); 2028 releases each get 3 years.
- FastAPI shipped 0.143.1 to 0.165.0 (2026-10-14 to 2026-11-18); breaking-change entries at 0.145-0.151, 0.155, 0.160, 0.162, 0.162.6
  (contents not read: unverified). Pin an upper bound and read notes before upgrading.
- CPython 3.15.1/3.15.2 patch releases carry security fixes (tarfile filters reject drive-relative names, http.cookiejar, expat 2.10.0).

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
