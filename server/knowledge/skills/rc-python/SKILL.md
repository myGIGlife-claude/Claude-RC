---
name: rc-python
description: Current Python practice (Oct 2026) for writing, reviewing or upgrading Python code - versions 3.11-3.15, typing, uv/pyproject.toml packaging, ruff, pytest, FastAPI/Django/Flask/SQLAlchemy, pandas/Polars/NumPy, asyncio, free-threading, security. Use for any .py, pyproject.toml, requirements*.txt, uv.lock, pylock.toml, setup.py, Dockerfile with Python, or "python"/"pip"/"venv" task.
---
# Python  (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Detailed version tables and framework release notes: `references/versions.md`.

## Currency check

- **CPython** (devguide, checked 2026-10-09): 3.15.0 released 2026-10-09 and 3.14 (3.14.8) are bugfix;
  3.13, 3.12, 3.11 are security-only; **3.10 went EOL 2026-10-01**; 3.9 EOL 2025-10-31.
  EOLs: 3.11 2027-10, 3.12 2028-10, 3.13 2029-10, 3.14 2030-10, 3.15 2031-10.
- **New projects:** target 3.14 (or 3.15 once your deps ship wheels). Libraries: `requires-python = ">=3.11"` at most
  one year from now; many core libs already need 3.11+ (pandas 3, SQLAlchemy 2.1) or 3.12+ (NumPy 2.5, Django 6.x).
- **3.14 (2025-10-07)** shipped: deferred annotations (PEP 649/749, `annotationlib`), t-strings (PEP 750),
  `except A, B:` without parens (PEP 758), `concurrent.interpreters` + `InterpreterPoolExecutor` (PEP 734),
  free-threaded build officially supported but optional (PEP 779), experimental JIT in macOS/Windows binaries
  (`PYTHON_JIT=1`, not for production), `compression.zstd` (PEP 784), `pathlib.Path.copy/move`, colour REPL
  highlighting, `python -m asyncio ps PID`, `pdb -p PID`, Linux multiprocessing default `forkserver`,
  tarfile `filter='data'` default.
- **3.15 (2026-10-09)** shipped: `lazy import x` (PEP 810), `frozendict` (PEP 814), `sentinel` (PEP 661), UTF-8 default
  encoding everywhere (PEP 686), `profiling.sampling` sampling profiler (PEP 799), free-threaded stable ABI `abi3t`
  (PEP 803), `TaskGroup.cancel()`, TypedDict extra items (PEP 728), `TypeForm` (PEP 747).
- **3.13 (2024-10-07)** shipped: new REPL, experimental free-threading (`python3.13t`), TypeVar defaults, `ReadOnly`,
  `TypeIs`, `warnings.deprecated`; removed the 19 PEP 594 "dead batteries" (cgi, crypt, telnetlib, imghdr, pipes...) and lib2to3.
- **Tooling now:** uv 0.13.0, ruff 0.17.0, ty 0.0.85 (beta), pyrefly 1.3.2, mypy 2.4.0, pip 26.2.1, pytest 9.1.1.
  Astral (uv/ruff/ty) acquisition by OpenAI announced 2026-03-19; tools remain open source.
- **Frameworks now:** FastAPI 0.143.0, Pydantic 2.14.0, Django 6.1.2 (5.2 LTS to 2028-04; 6.2 LTS 2027-04, then
  "Django 2028" calendar versions), Flask 3.1.3, Litestar 2.24.0, SQLAlchemy 2.1.4, Alembic 1.20.0,
  pandas 3.0.6, Polars 2.0.0, NumPy 2.5.3, PyTorch 2.14.1.

## What changed / stop doing

| Old (stop) | New (do) | Since |
|---|---|---|
| Targeting/supporting 3.8, 3.9, 3.10 | 3.11+ minimum, 3.14 default | 3.10 EOL 2026-10-01 |
| `setup.py` / `setup.cfg` as project config | `pyproject.toml` `[project]` (PEP 621) + build backend | long-standing; pip removed `setup.py develop` editable fallback in 25.3 |
| `requirements.txt` as the only source of truth | deps in `[project.dependencies]`, lock with `uv.lock` or `pylock.toml` (PEP 751) | PEP 751 accepted 2025-04; `pip lock` 25.1 |
| `requirements-dev.txt`, extras for dev tools | `[dependency-groups]` (PEP 735), `uv add --dev`, `pip install --group dev` | pip 25.1 |
| `python -m venv` + `pip install` by hand, pyenv | `uv sync` / `uv run` (uv installs Python too) | uv |
| Pipenv, Poetry 1.x `[tool.poetry]` deps | uv (default) or Poetry 2 / PDM with standard `[project]` table | Poetry 2.0 |
| `pipx install` for tools | `uvx tool` / `uv tool install` (pipx still fine) | |
| black + isort + flake8 (+ pyupgrade, autoflake) | `ruff check --fix` + `ruff format` | |
| nose, `unittest`-only suites, `setup.py test` | pytest 9 (`[tool.pytest]` native TOML) | pytest 9.0 2025-11 |
| `typing.List/Dict/Tuple/Set/Optional/Union` | `list[int]`, `dict[str, X]`, `X \| None` | 3.9/3.10 |
| `typing.Callable/Iterable/Mapping/Sequence` | import from `collections.abc` | 3.9 |
| `TypeVar("T")` + `Generic[T]`, `TypeAlias` | `def f[T](x: T) -> T`, `class Box[T]:`, `type Alias = ...` (PEP 695) | 3.12 |
| `from __future__ import annotations` by default | not needed on 3.14+ (deferred by default); deprecated, removal after 2029 | 3.14 |
| `"QuotedForwardRef"` strings | bare names work on 3.14+ | 3.14 |
| `except (A, B):` only | `except A, B:` allowed (keep parens if you need `as e`) | 3.14 |
| `os.path.join`, `open(os.path...)`, `shutil.copytree` for one path | `pathlib.Path`, `Path.copy()/move()` | `copy/move` 3.14 |
| `asyncio.get_event_loop()` at top level, policies | `asyncio.run(main())`, `asyncio.Runner`; `loop_factory=` | `get_event_loop` raises in 3.14; policies deprecated, removal planned 3.16 |
| `asyncio.gather` for fan-out, `wait_for` | `asyncio.TaskGroup`, `asyncio.timeout()` | 3.11 |
| `asyncio.iscoroutinefunction` | `inspect.iscoroutinefunction` | deprecated 3.14 |
| `datetime.utcnow()` / naive datetimes | `datetime.now(UTC)` (`from datetime import UTC`) | utcnow deprecated 3.12 |
| `pytz` | `zoneinfo` (pandas 3 dropped pytz requirement) | 3.9 / pandas 3.0 |
| relying on locale encoding / `open()` without `encoding=` | pass `encoding="utf-8"`; 3.15 defaults to UTF-8 | PEP 686, 3.15 |
| `cProfile` / `profile` module | `python -m profiling.sampling` / py-spy; `profile` removed 3.17 | 3.15 |
| `cgi`, `crypt`, `telnetlib`, `imghdr`, `pipes`, `lib2to3` | `email`/`multipart`, `hashlib.scrypt`/argon2, `telnetlib3`, `filetype`/Pillow, `shlex`/`subprocess` | removed 3.13 |
| `ast.Num/Str`, `sre_*` modules | `ast.Constant`; `re` | removed 3.14 / 3.15 |
| Pydantic v1 (`class Config`, `.dict()`, `@validator`, `pydantic.v1`) | v2: `model_config = ConfigDict(...)`, `.model_dump()`, `@field_validator` | FastAPI dropped `pydantic.v1` in 0.128.0 |
| FastAPI `ORJSONResponse` for speed | return a typed model; FastAPI serializes via Pydantic (Rust) | 0.130/0.131 |
| SQLAlchemy 1.x `session.query(...)`, `Select[Tuple[int,str]]` | 2.x `select()` + `session.execute/scalars`; `Select[int, str]` | 2.0 / 2.1 |
| pandas chained assignment `df[m]["b"] = 1`, `SettingWithCopyWarning` | `df.loc[m, "b"] = 1` (Copy-on-Write always on) | pandas 3.0 |
| `np.float_`, `np.NaN`, `np.product`, `copy=False` meaning "maybe" | `np.float64`, `np.nan`, `np.prod`, `np.asarray` | NumPy 2.0 |
| pytest-asyncio `event_loop` fixture | `loop_scope=` on markers/fixtures | pytest-asyncio 1.0 |
| `multiprocessing` assuming `fork` on Linux | code must be picklable/import-safe (`if __name__ == "__main__":`) | default `forkserver` 3.14 |
| `PGP`-verifying CPython downloads | Sigstore | 3.14 |

## Do this

### Project layout and pyproject.toml
```
myproj/
  pyproject.toml
  uv.lock              # commit it (apps). Libraries: commit too, CI tests against it
  .python-version      # e.g. 3.14 (uv reads it)
  src/myproj/__init__.py
  tests/
```
```toml
[project]
name = "myproj"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = ["httpx>=0.28", "pydantic>=2.12"]

[project.scripts]
myproj = "myproj.cli:app"

[dependency-groups]            # PEP 735: not published, unlike extras
dev = ["pytest>=9", "ruff>=0.17", "mypy>=2"]

[build-system]
requires = ["uv_build>=0.13,<0.14"]   # or hatchling / setuptools>=77
build-backend = "uv_build"
```
- Commands: `uv init --package`, `uv add httpx`, `uv add --dev pytest`, `uv sync --locked` (CI),
  `uv run pytest`, `uv lock --upgrade-package x`, `uv python install 3.14`, `uv build`, `uv publish`.
- Lockfiles: `uv.lock` is the uv-native lock. For tool-neutral installs: `uv export --format pylock.toml`
  (preview in uv 0.13) and `pip install -r pylock.toml` (experimental since pip 26.1). `pip lock` also writes it.
- Docker: copy `uv.lock` + `pyproject.toml`, `uv sync --locked --no-dev --no-install-project`, then copy code.
- Cooldown against freshly published malicious releases: uv `exclude-newer = "7 days"` (relative durations
  supported), pip `--uploaded-prior-to P7D` (pip 26.x).

### Single-file scripts (PEP 723)
```python
# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx"]
# ///
import httpx
print(httpx.get("https://example.org").status_code)
```
Run with `uv run script.py`; add deps with `uv add --script script.py rich`. pip 26 can read them via
`--requirements-from-script`.

### Typing today
```python
from collections.abc import Callable, Iterable
from typing import Protocol, Self, TypedDict, ReadOnly, override

type Json = dict[str, "Json"] | list["Json"] | str | int | float | bool | None   # PEP 695 alias

def first[T](xs: Iterable[T], default: T | None = None) -> T | None: ...

class Repo[T, K = int]:          # PEP 696 default (3.13)
    def get(self, key: K) -> T | None: ...

class Closer(Protocol):          # structural; prefer over ABCs for "anything with .close()"
    def close(self) -> None: ...

class User(TypedDict):
    id: ReadOnly[int]            # PEP 705 (3.13)
    name: str
```
- Use `X | None`, built-in generics, `collections.abc` for ABCs; `typing` for `Protocol`, `TypedDict`, `Literal`,
  `Self`, `override` (3.12), `TypeIs` (3.13), `Annotated`, `Never`.
- On 3.14+ annotations are lazy: forward refs need no quotes. Code that reads `__annotations__` directly should use
  `annotationlib.get_annotations(obj, format=Format.FORWARDREF)` (or `inspect.get_annotations`).
- Type checkers: **mypy 2.x** (reference, plugins for Django/Pydantic), **pyright/basedpyright** (fast, editor),
  **pyrefly** (Meta, stable 1.x, fast), **ty** (Astral, still 0.0.x beta: fine as a second opinion, not the CI gate).
  Pick one as the CI gate; run it in strict mode on new code.

### Lint and format
```toml
[tool.ruff]
target-version = "py312"         # set it: ruff 0.17 default is py311
line-length = 100
[tool.ruff.lint]
select = ["E", "F", "W", "I", "UP", "B", "SIM", "S", "ASYNC", "DTZ", "PTH", "RUF", "PERF"]
```
`ruff check --fix && ruff format`. `UP` rewrites old typing/syntax, `S` = Bandit rules, `PTH` = pathlib,
`ASYNC` = blocking calls in async code, `DTZ` = naive datetimes (opt-in rule set).

### asyncio (structured concurrency)
```python
import asyncio

async def main(urls: list[str]) -> list[bytes]:
    async with asyncio.timeout(10):
        async with asyncio.TaskGroup() as tg:          # cancels siblings on first error
            tasks = [tg.create_task(fetch(u)) for u in urls]
    return [t.result() for t in tasks]

asyncio.run(main(urls))
```
- Errors from a TaskGroup arrive as `ExceptionGroup`: handle with `except* httpx.HTTPError:`.
- Never call blocking I/O (`requests`, `time.sleep`, sync DB drivers, big file reads) in a coroutine:
  use async libs (httpx, asyncpg/psycopg 3) or `await asyncio.to_thread(fn, ...)`.
- Keep references to fire-and-forget tasks (or use a TaskGroup); bare `create_task` results can be garbage collected.
- 3.15: `tg.cancel()` stops a whole group early. Debug stuck apps with `python -m asyncio pstree PID` (3.14+).

### Concurrency choice (2026)
- I/O-bound: asyncio (or threads for sync libraries).
- CPU-bound pure Python: `ProcessPoolExecutor`, or `InterpreterPoolExecutor` (3.14, per-interpreter GIL,
  objects are not shared), or threads on the free-threaded build (`python3.14t`) if every C extension you import
  declares support; otherwise importing a non-ready extension re-enables the GIL (with a warning). Check `sys._is_gil_enabled()`.
  Free-threaded costs ~1-8% single-thread speed and more memory. Shared mutable state still needs locks.
- Numeric work: vectorize with NumPy/Polars (they release the GIL) before reaching for threads.

### Web
- **FastAPI**: declare the return type (it becomes the response model and is serialized by Pydantic in Rust);
  `Annotated[..., Depends(...)]` dependencies; `lifespan=` context manager, not `@app.on_event`;
  `pip install "fastapi[standard]"`, `fastapi dev` / `fastapi run`. JSON bodies need a JSON `Content-Type`
  (strict by default since 0.132.0). Python 3.10+, Pydantic v2 only.
- **Pydantic v2**: `model_config = ConfigDict(extra="forbid", frozen=True)`, `model_validate`, `model_dump(mode="json")`,
  `TypeAdapter` for non-model types. Settings: `pydantic-settings` `BaseSettings` with
  `SettingsConfigDict(env_prefix="APP_", env_file=".env")`; use `SecretStr` for secrets.
- **Django**: new apps on 6.1 (or 5.2 LTS if you need support past 2027-12). 6.0 added `django.tasks`
  (needs a backend/worker), built-in CSP (`SECURE_CSP`), template partials, `BigAutoField` default.
  6.1 adds fetch modes (`FETCH_PEERS` kills N+1), DB-level `on_delete=models.DB_CASCADE`, `MAILERS`.
  Use async views/ORM (`aget`, `acount`, async iteration) only with ASGI.
- **Flask 3.1**: fine for small sync apps; app factory + blueprints; `flask --app`.
- **Litestar 2.x**: alternative ASGI framework with msgspec/attrs/Pydantic support.
- **SQLAlchemy 2.1** async: `create_async_engine("postgresql+asyncpg://...")`, `async_sessionmaker(expire_on_commit=False)`,
  `Mapped[int]` / `mapped_column()`; install `sqlalchemy[asyncio]` (greenlet no longer pulled in by default).
  One `AsyncSession` per request/task, never shared across tasks.
- **Alembic**: `alembic revision --autogenerate`, review every generated migration; `alembic init -t async` for async engines.

### Data and ML touch points
- **pandas 3**: Copy-on-Write always; strings are `str` dtype (Arrow-backed if pyarrow present); `pd.col("a")`
  expressions; datetimes default to `us`; upgrade via 2.3 with warnings fixed first.
- **Polars 2.0**: lazy API (`pl.scan_parquet(...).filter(...).collect()`), streaming engine is the default for lazy queries.
  Prefer Polars for new pipelines that do not need the pandas ecosystem.
- **NumPy 2.x**: NEP 50 promotion (Python scalars no longer upcast arrays), removed aliases (`np.float_`,
  `np.NaN`, `np.Inf`), `copy=False` now raises if a copy is needed. 2.5 needs Python 3.12+.
- **PyTorch 2.14**: install from the index matching your CUDA/ROCm build (see pytorch.org selector); use
  `torch.compile` for speedups; `torch.load(..., weights_only=True)` (the default since 2.6) - do not turn it off for untrusted files; prefer `safetensors`.

### CLI and logging
- CLI: Typer (type-hint driven, on Click) or Click 8.5; argparse is fine for zero-dependency scripts (3.14 adds colour help, suggestions).
- Logging: stdlib `logging` configured once at entry (`logging.config.dictConfig`), `logger = logging.getLogger(__name__)`
  per module, lazy `%s` args. For JSON/structured logs use structlog 26 with contextvars. Never `print` in libraries.

### Data classes
- `@dataclass(slots=True, frozen=True, kw_only=True)` for internal records; `attrs` when you need validators/converters;
  `msgspec.Struct` for fastest (de)serialization; Pydantic at trust boundaries (input validation).
- `copy.replace(obj, field=...)` (3.13) works on dataclasses and namedtuples.

## Security

- **Deserialization:** never `pickle`/`marshal`/`shelve`/`joblib.load`/`torch.load(weights_only=False)` untrusted data.
  YAML: `yaml.safe_load` only. Prefer JSON, msgspec, safetensors.
- **Command injection:** `subprocess.run([...], check=True)` with an argument list; no `shell=True` with user input,
  no `os.system`. If a shell is unavoidable, `shlex.quote` each part.
- **SQL injection:** bound parameters (`cursor.execute("... where id = %s", (id_,))`, SQLAlchemy `text(":id")`),
  never f-strings. t-strings (3.14) only help if the library consumes `Template` (SQLAlchemy 2.1 supports them); an f-string is still unsafe.
- **Templates/XSS:** Jinja2 with `autoescape=True` (Flask default for .html); Django autoescape on; `|safe`/`mark_safe` only on trusted data.
- **SSRF:** server-side fetches of user URLs need scheme allow-list, DNS resolve + block private/link-local/metadata
  ranges (`ipaddress.ip_address(x).is_private`, 169.254.169.254), no redirects to unchecked hosts, timeouts.
- **Path traversal / archives:** resolve both sides: `base = base.resolve()`, then check `(base / name).resolve().is_relative_to(base)`; tarfile
  uses `filter="data"` by default on 3.14 - pass it explicitly on older versions; `zipfile` member names need the same check.
- **Secrets:** `secrets.token_urlsafe(32)`, `secrets.compare_digest`; never `random` for tokens. Passwords:
  argon2-cffi or Django's hashers, not raw `hashlib`. Config from env (`pydantic-settings`), never in code.
- **XML:** use `defusedxml` for untrusted XML.
- **Supply chain:** lock with hashes (`uv.lock`, `pylock.toml`, or `pip install --require-hashes`); add a release
  cooldown (`exclude-newer`); for private packages, pin the index per package (uv `[[tool.uv.index]]` with
  `explicit = true` + `[tool.uv.sources]`) - never `--extra-index-url` for internal names (dependency confusion).
  Run `pip-audit` (or `uv audit`, a preview command in uv 0.13) in CI; Bandit via ruff `S` rules.
- **Publishing:** PyPI Trusted Publishing (OIDC: GitHub Actions, GitLab CI/CD, Google Cloud) with
  `pypa/gh-action-pypi-publish` and `permissions: id-token: write`; it uploads PEP 740 attestations by default. No long-lived API tokens.
- **Debug surfaces:** `sys.remote_exec` / `pdb -p` (3.14) let same-user processes inject code: set
  `PYTHON_DISABLE_REMOTE_DEBUG=1` in hardened containers. Never run Django `DEBUG=True` or Flask debugger in production.

## Performance & quality

- Measure before optimizing: `python -m profiling.sampling` (3.15, attach to a PID, flamegraphs) or `py-spy record/top`
  (any version, no restart); `cProfile` + snakeviz for deterministic call counts; Scalene for CPU+memory; `tracemalloc` for leaks.
- Benchmarks: `pytest-benchmark` or `pyperf`; compare medians, not single runs.
- Startup: 3.15 `lazy import` for heavy optional modules in CLIs; check with `python -X importtime`.
- Memory: `__slots__` / `dataclass(slots=True)` for many small objects; generators over lists for streams.
- Upgrading the interpreter is the cheapest speedup (3.11+ is much faster than 3.10). The JIT (`PYTHON_JIT=1`)
  is experimental: benchmark, do not ship it on by default.
- Quality gates: ruff clean, type checker clean (strict on new modules), coverage >= 80% on branch coverage
  (`coverage run --branch`), no new `DeprecationWarning`s (`-W error::DeprecationWarning` in tests).

## Testing & tooling

- **pytest 9**: configure in `[tool.pytest]` (native TOML types) and set `strict = true`
  (strict markers, config, xfail, parametrization ids). Use fixtures, `parametrize`, `tmp_path`, `monkeypatch`.
  Subtests are built in (9.0). `--max-warnings` (9.1).
- **Async tests**: pytest-asyncio 1.x (`asyncio_mode = "auto"`, `loop_scope`), or anyio's pytest plugin.
- **Property tests**: Hypothesis for parsers, serializers and invariants.
- **Coverage**: coverage.py 7.x (`pytest --cov` via pytest-cov), branch coverage on.
- **Matrix**: tox 4 or nox (both can use uv: `tox-uv`, `nox -db uv`) or a CI matrix calling `uv run --python 3.x`.
- **CI recipe**: `uv sync --locked` -> `uv run ruff check` -> `uv run ruff format --check` -> `uv run mypy .`
  (or pyright/pyrefly) -> `uv run pytest` -> `uv run pip-audit` (or `uvx pip-audit`). Use `astral-sh/setup-uv` in GitHub Actions.
- **pre-commit** (or `prek`, a faster drop-in, unverified maturity) with ruff hooks.

## Common mistakes in AI-written code

- Writing `from typing import List, Dict, Optional` and `Optional[X]`: use built-ins and `X | None`.
- Adding `from __future__ import annotations` everywhere "for performance": unnecessary on 3.14+ and it breaks
  runtime-annotation users in subtle ways (Pydantic/FastAPI handle it, but plain `__annotations__` readers see strings).
- Emitting `setup.py`, `requirements.txt` + `pip install -r`, or manual `python -m venv` instructions for new projects; use `pyproject.toml` + uv.
- Inventing uv commands: real ones are `uv add/remove/sync/lock/run/tool/python/build/publish/export/init/tree/pip`;
  `uv venv` exists but is rarely needed. `uv pip install` does not update `pyproject.toml`.
- Putting dev tools in `[project.optional-dependencies]` instead of `[dependency-groups]`.
- Pydantic v1 API (`class Config`, `.dict()`, `.json()`, `parse_obj`, `@validator`, `orm_mode`) in v2 code.
- FastAPI `@app.on_event("startup")` (deprecated, use `lifespan`), `ORJSONResponse` (deprecated), `response_model=` where a return annotation suffices.
- SQLAlchemy `session.query()` in new code, sharing one `AsyncSession` across `gather`ed tasks, forgetting `expire_on_commit=False` with async.
- `asyncio.get_event_loop().run_until_complete(...)` (raises on 3.14 without a loop); `asyncio.gather` without error handling;
  `time.sleep` / `requests` inside `async def`.
- pandas: chained assignment, `inplace=True` everywhere, `df.append` (removed in 2.0), `M`/`Y` frequency aliases (now `ME`/`YE`),
  assuming string columns are `object`.
- NumPy: `np.float_`, `np.NaN`, `np.in1d`, `np.row_stack` (2.0 removals/deprecations).
- `datetime.utcnow()`, naive datetimes in APIs, `pytz.localize`.
- `os.path` chains where `pathlib` is clearer; `open()` without `encoding="utf-8"` (matters before 3.15 on Windows).
- Mutable default arguments, bare `except:`, `except Exception: pass`, `assert` for runtime validation (stripped by `-O`).
- `yaml.load(f)` without a Loader, `pickle.loads` on network data, `subprocess.run(cmd, shell=True)` with interpolated input,
  `random.choice` for tokens, `verify=False` on HTTP clients.
- Using `return` in `finally` (SyntaxWarning in 3.14). Importing removed modules (`cgi`, `imp`, `distutils`, `asynchat`, `asyncore`, `smtpd`, `telnetlib`).
- Claiming the GIL is gone: it is only absent on the separate free-threaded build (`python3.14t`), and returns if a non-ready extension loads.
- Writing Django code for 4.x/5.0 settings (`DEFAULT_AUTO_FIELD` boilerplate, `EMAIL_BACKEND` only) without checking the target version.

## Before you ship

- [ ] `requires-python` excludes EOL lines (3.10 and older); CI tests the min and newest supported version.
- [ ] `pyproject.toml` is the single config; lockfile committed and `uv sync --locked` (or hashed pylock) used in CI/Docker.
- [ ] `ruff check`, `ruff format --check`, type checker, `pytest` (strict), coverage gate all green.
- [ ] `pip-audit` clean; no `--extra-index-url` for private names; release cooldown configured.
- [ ] No pickle/yaml.load/shell=True/f-string SQL on untrusted input; secrets from env; `DEBUG` off.
- [ ] Async code: no blocking calls, timeouts on every network call, TaskGroup for fan-out.
- [ ] Deprecation warnings treated as errors in tests (catches 3.15/3.16 removals early).
- [ ] Publishing via Trusted Publishing with attestations; wheels built with `uv build` / `python -m build`.

## Sources

- https://devguide.python.org/versions/ - branch status and EOL dates (accessed 2026-10-09)
- https://www.python.org/downloads/ - latest patch releases (2026-10-09)
- https://docs.python.org/3/whatsnew/3.13.html - 3.13 features, PEP 594 removals (2026-10-09)
- https://docs.python.org/3/whatsnew/3.14.html and cpython `Doc/whatsnew/3.14.rst` - 3.14 features, PEP 779 phase II, JIT, removals, `__future__` deprecation (2026-10-09)
- https://docs.python.org/3.15/whatsnew/3.15.html and cpython `Doc/whatsnew/3.15.rst` - 3.15 features, removals, re.prefixmatch, profile deprecation (2026-10-09)
- https://docs.python.org/3/library/tarfile.html (3.14 source) - default `filter='data'` (2026-10-09)
- https://docs.python.org/3/howto/free-threading-python.html - free-threading overhead, GIL re-enable, `sys._is_gil_enabled` (2026-10-09)
- https://packaging.python.org/en/latest/specifications/pylock-toml/ - PEP 751 lockfile spec (2026-10-09)
- https://github.com/pypa/pip/blob/main/NEWS.rst - pip lock, --group, -r pylock.toml, --uploaded-prior-to, setup.py develop removal (2026-10-09)
- https://github.com/astral-sh/uv/releases and https://docs.astral.sh/uv/concepts/resolution/ - uv 0.13.0, pylock preview, exclude-newer durations (2026-10-09)
- https://github.com/astral-sh/ruff/releases - ruff 0.17.0 defaults (2026-10-09)
- https://github.com/astral-sh/ty/releases - ty 0.0.85 (2026-10-09)
- https://pypi.org/pypi/<name>/json - versions/requires-python for all packages in references/versions.md (2026-10-09)
- https://mypy.readthedocs.io/en/stable/changelog.html - mypy 2.0/2.4 changes (2026-10-09)
- https://docs.pytest.org/en/stable/changelog.html - pytest 9.0/9.1 (2026-10-09)
- https://pytest-asyncio.readthedocs.io/en/stable/reference/changelog.html - event_loop fixture removal (2026-10-09)
- https://github.com/fastapi/fastapi/blob/master/docs/en/docs/release-notes.md - FastAPI breaking changes 0.125-0.143 (2026-10-09)
- https://www.djangoproject.com/download/ - Django support dates, calendar versioning (2026-10-09)
- https://docs.djangoproject.com/en/dev/releases/6.0/ and /en/6.1/releases/6.1/ - Django 6.0/6.1 features (2026-10-09)
- https://docs.sqlalchemy.org/en/21/changelog/migration_21.html - SQLAlchemy 2.1 changes (2026-10-09)
- https://pandas.pydata.org/docs/whatsnew/v3.0.0.html - pandas 3.0 (2026-10-09)
- https://docs.pola.rs/releases/upgrade/2/ - Polars 2.0 breaking changes (2026-10-09)
- https://numpy.org/news/ - NumPy 2.5 notes (2026-10-09); https://numpy.org/doc/stable/numpy_2_0_migration_guide.html - 2.0 removals (not re-fetched)
- https://docs.pypi.org/trusted-publishers/ and https://docs.pypi.org/attestations/producing-attestations/ - Trusted Publishing, PEP 740 (2026-10-09)
- https://blog.jetbrains.com/pycharm/2026/03/openai-acquires-astral-what-it-means-for-pycharm-users/ - Astral acquisition lead (blog, 2026-10-09)
