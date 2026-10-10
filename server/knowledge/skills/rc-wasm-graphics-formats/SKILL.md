---
name: rc-wasm-graphics-formats
description: WebAssembly (Wasm 3.0, WASI 0.2/0.3, components, Wasmtime, wasm-bindgen, Emscripten, TinyGo, Kotlin/Wasm, Blazor, wasm-opt), SVG, Canvas/OffscreenCanvas, WebGL 2/WebGPU/WGSL (three.js, Babylon, PixiJS, wgpu), image formats (AVIF, JPEG XL, Lottie), data formats (JSON, YAML, TOML, XML, CSV, .env, Protobuf, CBOR, Parquet) and custom elements/Lit. Use for .wasm/.wat/.wit/.svg/.wgsl/.yaml/.toml/.proto files.
---
# WebAssembly, graphics and data formats (as of 2026-10)

> Facts are dated (see Sources), checked 2026-10-09. Anything marked (unverified) is a lead, not a fact.
> rc-web-frontend owns HTML/CSS, responsive `<img>`/`srcset`, Lit/declarative shadow DOM basics and Baseline: not repeated here.
> Data-format pitfalls in depth (JSON, YAML, TOML, XML, CSV, Protobuf, binary formats): `references/data-formats.md`.

## Currency check

Browsers: Chrome 155, Firefox 157, Safari 27 (web-features 3.42.0). Baseline "newly" = all core browsers now; "widely" = 30+ months.

| Thing | Current (2026-10) | Notes |
|---|---|---|
| WebAssembly core spec | **Wasm 3.0** (completed 2025-09-17); 2.0 completed 2025-03-20 | 3.0 = memory64, multiple memories, GC, typed function refs, tail calls, exception handling (exnref), relaxed SIMD, deterministic profile, annotations, JS string builtins |
| Wasm proposals (2026-10) | Phase 5: JSPI, Web CSP. Phase 4: threads, wide arithmetic, compact import section. Phase 3: ESM integration, stack switching, custom page sizes, custom descriptors | Threads is still not in the core spec but has shipped in all browsers since 2021 |
| WASI | 0.3.0 shipped 2026-06-11 (native async: `stream`/`future`), 0.3.1 2026-08-11; 0.2.0 2024-01, last 0.2.x = 0.2.12 | 0.3.x every 2 months (0.3.2 planned 2026-10-13). WASI 1.0 has no date |
| Wasmtime | 49.0.2 stable, 50.0.0-rc (monthly majors on the 20th) | LTS every 12th major, 24 months (36, 48); other majors 2 months |
| Wasmer / WasmEdge | 7.5.0 (2026-10-01) / 0.18.0 (2026-10-05) | |
| Rust | wasm-bindgen 0.2.129, wasm-pack 0.15.0, trunk 0.21.14 | `rustwasm` org archived 2025-09; wasm-bindgen and wasm-pack live in the `wasm-bindgen` GitHub org, `twiggy` (now `AlexEne/twiggy`) is archived, last commit 2026-02 |
| Emscripten | 6.0.12 (2026-10-08) | |
| Binaryen (`wasm-opt`) | version_133 (2026-09-21); npm `binaryen` 132.0.0 | crate `wasm-opt` 0.116.1 is stale (2024): prefer the Binaryen release binary |
| jco (JS host for components) | 1.37.0 | `jco transpile` turns a component into ES modules |
| AssemblyScript | 0.28.20 (2026-07) | still 0.x; no closures/exceptions/async; opposes WASI/component model |
| Go | `GOOS=js`/`wasip1`; `//go:wasmexport` + `-buildmode=c-shared` reactor since 1.24 | support files moved `misc/wasm` -> `lib/wasm` in 1.24; min binary ~2 MB, TinyGo ~10s of KB |
| Kotlin/Wasm | Beta | needs browsers with WasmGC + legacy exception handling |
| Blazor | .NET 10 LTS (to 2028-11-14); .NET 11 at go-live RC | .NET 8 and 9 EOL 2026-11-10 |
| three.js / Babylon.js / PixiJS | 0.186.1 / 9.30.0 / 8.22.0 | regl 2.1.1 (2024, WebGL only, quiet) |
| wgpu (Rust) | 30.0.1 | WebGPU impl used by Firefox and Deno |
| SVGO | 4.1.0 (v3 line still patched: 3.3.5) | v4: `removeViewBox` and `removeTitle` OFF by default |
| Lottie | Lottie Animation Format spec 1.0.1 (2025-06); `@lottiefiles/dotlottie-web` 0.81.0 | `lottie-web` 5.13.0 (2025-05) is in slow maintenance |
| JSON Schema | 2020-12 is still current | no newer stable release |
| YAML spec | 1.2.2 (2021-10) | js-yaml 5.0 (2026-06-20, 5.4.3 now), `yaml` 2.9.1, PyYAML 6.0.3, ruamel.yaml 0.19.1 |
| TOML | **1.1.0 (2025-12-18)** | multi-line inline tables + trailing commas, `\xHH`, `\e`, optional seconds |
| Protobuf JS | `@bufbuild/protobuf` 2.16.0, protobufjs 8.8.0 | |

Browser support of what matters here (web-features 3.42.0):

| Feature | Status |
|---|---|
| Wasm SIMD, threads, bulk memory, reference types, legacy EH | widely |
| WasmGC, tail calls | newly (2024-12) - Safari 18.2; typed function refs newly (2024-09) - Safari 18 |
| exnref exceptions | newly (2025-05) - Chrome 137, Firefox 131, Safari 18.4 |
| JSPI (JS Promise Integration) | newly (2026-09-14) - Chrome 137, Firefox 153, Safari 27 |
| memory64, multi-memory, relaxed SIMD | Chrome + Firefox only, **not Safari** |
| JS string builtins | Chrome 130, Firefox 134, Safari 26.2 (all core browsers since 2025-12) |
| WebGL 2 | widely (2024-03) |
| WebGPU | Chrome/Edge 113+ desktop (Linux from 144, Intel Gen12+), Chrome Android 121, Safari 26 (macOS/iOS); Firefox 141 Windows, 145+/147 Apple-silicon macOS only, no Linux, no Android. **Not Baseline** |
| OffscreenCanvas | widely (2025-09) |
| AVIF / WebP | widely |
| JPEG XL | Safari 17+, Chrome 155 (2026-10-06); not Edge, not Firefox: not Baseline |
| HEIC | Safari only; Chrome and Firefox do not decode it |
| Form-associated custom elements | widely (2025-09) |
| Custom states `:state()` | newly (2024-05) |
| Scoped custom element registries | Chrome 146, Safari 26, not Firefox |
| Customized built-ins (`is="..."`) | never in Safari: do not use |
| SMIL in SVG | supported everywhere (Baseline widely) but feature-frozen |

### Older versions (legacy projects: do not upgrade unless asked)
- Safari < 18.2 / iOS < 18.2: no WasmGC or tail calls; Kotlin/Wasm, Dart/Flutter-Wasm and other GC targets need a JS fallback.
  Safari < 16.4: no Wasm SIMD, no OffscreenCanvas.
- WASI 0.2 (`wasm32-wasip2`) code is still the common case: 0.3 adds async; do not rewrite working p2 components unprompted. WASI preview1 (`wasm32-wasip1`, Go `wasip1`) is still the only target many runtimes/languages have.
- Wasmtime LTS 36 projects: keep its API; the embedding API changes every major.
- Rust: target `wasm32-wasi` was renamed `wasm32-wasip1` (new name stable since 1.78, 2024-05; old name removed in 1.84, 2025-01).
- SVGO 3 configs have `removeViewBox`/`removeTitle` on by default: disable them explicitly. `removeScriptElement` was renamed `removeScripts` in v4.
- js-yaml 4: `load` is already safe (no `!!js/function`), `safeLoad` throws "removed"; 5.x removed `safeLoad/safeDump` exports entirely and `load('')` now throws.
- TOML 1.0 parsers reject 1.1 syntax (multi-line inline tables, `\e`, `14:15` times): write 1.0-compatible TOML unless every consumer's parser supports 1.1.
- .NET 8 Blazor: EOL 2026-11-10; flag it, do not migrate unasked.
- WebGL 1-only code: still runs; when touching it, prefer WebGL 2 (widely) rather than extending WebGL 1 extension juggling.

## What changed / stop doing

| Old | New | Since |
|---|---|---|
| asm.js, Emscripten `-s WASM=0` | Wasm (all browsers); asm.js is dead | 2017+ |
| Shipping your own GC/runtime in linear memory (Kotlin, Dart, Java ports) | WasmGC targets (Kotlin/Wasm, Dart, J2Wasm, OCaml) | Wasm 3.0 / Safari 18.2 |
| Emscripten setjmp/C++ exceptions via JS trampolines (`-fexceptions` JS mode) | `-fwasm-exceptions` (native EH; exnref encoding) | EH widely; exnref 2025 |
| Asyncify everywhere to call async JS from sync Wasm | JSPI (`WebAssembly.Suspending`/`promising`), Asyncify only for old browsers | JSPI newly 2026-09 |
| `wasm32-wasi`, WASI preview1 for new server code | `wasm32-wasip2` components with WIT interfaces; 0.3 for async | WASI 0.2 2024-01, 0.3 2026-06 |
| Hand-written JS glue for components | `wit-bindgen` (guest), `jco` / `wasmtime::component::bindgen!` (host) | - |
| `wasm-opt` crate or no optimizer | Binaryen `wasm-opt -O3`/`-Oz` from current releases (toolchains often run it: check before adding a step) | - |
| `rustwasm/*` repos and `rustwasm.github.io` book as "latest" | `wasm-bindgen` GitHub org; check its docs | 2025-09 (org archived) |
| WebGL 1 + extension checks | WebGL 2 baseline; WebGPU where available | WebGL 2 widely 2024-03 |
| GLSL-only shader pipelines for new WebGPU code | WGSL (WebGPU's only shading language); TSL in three.js for both backends | - |
| Main-thread `<canvas>` render loops for heavy work | `transferControlToOffscreen()` + Worker | OffscreenCanvas widely 2025-09 |
| CSS image sprites, icon fonts | inline SVG `<symbol>` + `<use>`, or individual SVG files (HTTP/2+) | - |
| GIF animations | `<video autoplay muted loop playsinline>` (AV1/H.264) or animated AVIF/WebP | - |
| `lottie-web` + huge JSON | dotLottie (`.lottie`, zipped, WASM renderer `@lottiefiles/dotlottie-web`) | - |
| SMIL for app animation | CSS animations / Web Animations API on SVG; SMIL only inside self-contained `<img>` SVGs | - |
| SVGO default preset as-is (v3) | v4 keeps `viewBox` and `<title>`; review any custom `preset-default` overrides | SVGO 4 |
| YAML `yaml.load(s)` (PyYAML) / js-yaml `safeLoad` | `yaml.safe_load` / js-yaml `load` (safe), `yaml` package `parse` | PyYAML 5.1+/6.0; js-yaml 4 |
| YAML or XML for new data interchange | JSON (+ JSON Schema) for APIs; Protobuf/CBOR for binary | - |
| `JSON.parse` for 64-bit IDs | send IDs as strings; or reviver with `context.source` (JSON.parse source text, newly 2025-03) | - |
| JSON Schema draft-04/07 | 2020-12 (`$defs`, `prefixItems`, `unevaluatedProperties`) | 2020-12 |
| TOML 1.0 only | 1.1 allowed where parsers support it (smol-toml, Python 3.15 `tomllib`, tomli 2.4+) | TOML 1.1, 2025-12 |
| `JSON.parse(await import(...))` hacks | `import data from './x.json' with { type: 'json' }` | newly 2025-04 |

## Do this

### When Wasm is the right choice
- Good fits: existing C/C++/Rust/Go libraries ported to the web (codecs, SQLite, PDF, CAD, physics, image processing), CPU-bound
  hot loops on typed data (SIMD helps), plugin sandboxes on servers (Wasmtime/Extism-style), edge functions, polyglot components.
- Bad fits: DOM-heavy UI, small utilities, anything dominated by JS<->Wasm crossings or string marshalling. Modern JS JITs are close
  to Wasm on scalar code; measure before porting. Boundary costs: each call is cheap, but copying strings/arrays across is not.
- Rules: batch work per call; pass pointers + lengths into linear memory (`new Uint8Array(memory.buffer, ptr, len)`), re-create views
  after `memory.grow` (old views detach); use `TextDecoder` once per batch; keep long-lived state inside Wasm.
- Least dependency: a 20-line JS function does not need a Rust toolchain. Do not add Wasm for "speed" without a benchmark.

### Load and instantiate
```js
// Streaming compile needs Content-Type: application/wasm
const { instance } = await WebAssembly.instantiateStreaming(fetch('/m.wasm'), imports);
// Feature detection: wasm-feature-detect (or WebAssembly.validate on a tiny probe)
```
- Cache with long-lived immutable URLs (content hash). Serve with Brotli/gzip: Wasm compresses ~2-4x (rule of thumb).
- Threads need `SharedArrayBuffer` => cross-origin isolation: `Cross-Origin-Opener-Policy: same-origin` +
  `Cross-Origin-Embedder-Policy: require-corp` (or `credentialless`). This breaks un-CORS'd third-party embeds: plan for it.
- CSP: Wasm compilation needs `script-src 'wasm-unsafe-eval'` (not `'unsafe-eval'`).

### Toolchains
- Rust (browser): `wasm-bindgen` + `wasm-pack build --target web` (library) or `trunk serve` (app). Keep `wasm-bindgen` CLI and crate
  versions identical. `[profile.release] opt-level = "z"` or `"s"`, `lto = true`, `codegen-units = 1`, `panic = "abort"` (unless you
  need unwinding), then `wasm-opt -Oz`. Check size with `twiggy` (archived 2026-02, still works) or `wasm-tools`.
- Rust (server/components): `cargo build --target wasm32-wasip2`; WIT + `wit-bindgen`; run with `wasmtime run` / `wasmtime serve`.
- C/C++: Emscripten 6 (`emcc -O3 -flto -sMODULARIZE -sEXPORT_ES6 -fwasm-exceptions`); `-sALLOW_MEMORY_GROWTH` when sizes unknown;
  `-msimd128` only if your audience has SIMD (all current browsers do). For WASI, use wasi-sdk.
- Go: `GOOS=js GOARCH=wasm` + `lib/wasm/wasm_exec.js` matching the compiler version, or `GOOS=wasip1`. For size use TinyGo
  (subset of Go; check reflection/`encoding/json` support before choosing it).
- Kotlin/Wasm (Beta) and Blazor WebAssembly: fine for whole apps where the team already lives in that language; heavy first download
  (Blazor: enable AOT only where CPU-bound, it grows size; use trimming). Not for small widgets on content sites.
- AssemblyScript: OK for small numeric kernels; not a TypeScript replacement (no closures, exceptions, async).

### Size and startup
- Order: remove unused deps/features -> compiler size flags -> LTO -> `wasm-opt -Oz` (or `-O3` for speed) -> strip names/debug
  (`--strip-debug`) -> Brotli. Measure compressed size and time-to-instantiate (DevTools Performance).
- `instantiateStreaming` compiles while downloading; browsers cache compiled code for large modules fetched with stable URLs.
- Lazy-load the module on first use (dynamic `import()` of the glue) instead of blocking first paint.

### Security model (and its limits)
- Wasm is memory-safe for the HOST (bounds-checked linear memory, no raw syscalls, only imported capabilities), but NOT inside its own
  memory: C/C++ buffer overflows still corrupt the module's data, and there are no stack canaries/ASLR-like defenses by default.
- On servers, the sandbox is only as strong as what you import: grant WASI capabilities explicitly (`--dir` preopens, no blanket
  network), set fuel/epoch interruption and memory limits (`StoreLimits`) against infinite loops and memory bombs.
- Treat untrusted `.wasm` as code: validate size, time-limit compilation, and keep runtimes patched (Wasmtime security fixes are
  backported only to supported majors).
- Spectre: shared memory + high-res timers are gated by cross-origin isolation for a reason; keep it.

### SVG
- `<img src="x.svg" alt="...">`: cached, no scripts run, cannot be styled from page CSS. Best for illustrations/logos.
- Inline `<svg>`: stylable (`currentColor`, CSS custom properties), animatable, adds DOM weight per instance. Best for icons that
  follow text colour and for interactive graphics.
- Icon system: one sprite file of `<symbol id="i-x" viewBox="0 0 24 24">` and `<svg><use href="/icons.svg#i-x"/></svg>`
  (same-origin only). Decorative icon: `aria-hidden="true" focusable="false"`; icon-only button: label the `<button>` (`aria-label`), not the svg.
- Meaningful inline SVG: `<svg role="img" aria-labelledby="t d"><title id="t">...</title><desc id="d">...</desc>`. Charts need a
  text/table alternative too.
- Always keep `viewBox`; size with CSS `width`/`height`; set `width`/`height` attributes to avoid CLS.
- Animation: CSS transitions/animations on `transform`, `opacity`, `stroke-dashoffset`; Web Animations API (`el.animate()`) for
  JS control. Respect `prefers-reduced-motion`. SMIL works but gets no new features; use only where CSS cannot reach (inside `<img>` SVG).
- Filters (`feGaussianBlur`, `feTurbulence`) are expensive on large areas; prefer CSS `filter` functions for simple blur/shadow.
- SVGO: run in the build (`svgo -rf src/icons`), keep `viewBox` and `<title>`, check `cleanupIds`/`prefixIds` when inlining many SVGs
  (ID collisions break gradients/`<use>`), and diff the render visually.

### Canvas 2D
```js
const dpr = window.devicePixelRatio || 1;
canvas.width = Math.round(cssW * dpr); canvas.height = Math.round(cssH * dpr);
canvas.style.width = cssW + 'px'; canvas.style.height = cssH + 'px';
ctx.setTransform(dpr, 0, 0, dpr, 0, 0);   // draw in CSS px
```
- Re-run on `resize` (ResizeObserver with `devicePixelContentBoxSize` where available) and when `dpr` changes (zoom, monitor move).
- Heavy drawing: `const off = canvas.transferControlToOffscreen(); worker.postMessage({ off }, [off]);` then render in the worker.
  Pixel work: `getContext('2d', { willReadFrequently: true })` only if you read back often.
- Canvas is invisible to assistive tech: put real content as fallback children of `<canvas>`, add `role="img"` + `aria-label` for
  a static picture, or keep an HTML/SVG layer for text and controls. Interactive canvas UI needs a parallel DOM for focus and keyboard.
- Handle `contextlost`/`contextrestored` (2D in Chrome/Firefox; WebGL everywhere).

### WebGL 2 vs WebGPU
- Pick a library first: three.js (general 3D; `WebGPURenderer` falls back to WebGL 2; TSL node shaders), Babylon.js (engine,
  WebGPU + WebGL), PixiJS 8 (2D, WebGPU or WebGL), regl (thin WebGL, unmaintained-ish), wgpu (Rust/native + browser).
- WebGPU when: compute shaders (ML, simulation, particles), many draw calls, modern pipeline; require HTTPS and a fallback because it is
  not Baseline (Firefox Linux/Android, older Safari). WebGL 2 when: maximum reach, existing GLSL code.
```js
const adapter = await navigator.gpu?.requestAdapter();
if (!adapter) return startWebGL2();          // always have a fallback path
const device = await adapter.requestDevice();
device.lost.then(info => recover(info));     // devices can be lost (driver reset, tab background)
```
- Shaders: WGSL is the only WebGPU shading language. Check `device.limits`/`adapter.features` before using optional features
  (`shader-f16`, `timestamp-query`, subgroups are Chrome-only).
- Upload textures as compressed GPU formats (KTX2 + Basis Universal) for big scenes; meshes as glTF 2.0 with Draco/Meshopt.

### Images and media in graphics
- Photos: AVIF first, WebP fallback only for very old Safari; JPEG XL is not Baseline (no Firefox) so only behind `<picture>` with a fallback.
- Lossless UI/screenshots: PNG (or lossless WebP/AVIF). Animation: APNG/animated WebP for small; video for anything big.
- HEIC from iPhones: convert server-side on upload (only Safari renders it). Strip EXIF/GPS on upload (privacy) but apply orientation first.
- Colour: tag images with an ICC profile (sRGB or Display P3); CSS/canvas can use `color(display-p3 ...)` and
  `getContext('2d', { colorSpace: 'display-p3' })` (Chrome, Safari). HDR images (gain maps, `dynamic-range-limit`) are Chromium-led: enhancement only.
- Lottie: ship `.lottie` via `@lottiefiles/dotlottie-web` (or its `<dotlottie-wc>` element), lazy-load, pause off-screen, honour
  reduced motion. For simple loaders a CSS/SVG animation is smaller than any Lottie runtime.

### Data/config formats (summary; details in references/data-formats.md)
- Interchange: JSON (RFC 8259, UTF-8, no comments, no trailing commas). Validate at the boundary with JSON Schema 2020-12 (Ajv 8 in
  JS, `jsonschema` in Python) or zod/valibot generated types. Integers above 2^53-1 lose precision in JS: send as strings.
- Human-edited config: TOML (simple, typed) or JSONC (tsconfig/VS Code style). YAML only where the ecosystem demands it (CI, k8s,
  compose): quote strings like `"no"`, `"on"`, `"0755"`, version numbers; always use safe loaders.
- Logs/streams: NDJSON/JSON Lines (one object per line). Tables for humans: CSV per RFC 4180 with a real parser (never `split(',')`).
- Binary RPC/storage: Protobuf (gRPC, schema evolution rules), CBOR (IETF, COSE/WebAuthn), MessagePack (simple, schemaless);
  Avro for Kafka-style schema registries; Parquet for columnar analytics files.
- XML only when the counterpart requires it (SAML, SOAP, Office, RSS, SVG): disable DTDs/external entities.
- `.env`: no standard; one `KEY=value` per line, no spaces around `=`, quote values with spaces/`#`; never commit it (commit `.env.example`).

### Web Components (beyond rc-web-frontend)
```js
class RcCounter extends HTMLElement {
  static formAssociated = true;
  static observedAttributes = ['value'];
  #internals = this.attachInternals();
  constructor() { super(); this.attachShadow({ mode: 'open', delegatesFocus: true }); }
  connectedCallback() { /* render, add listeners (may run many times: guard) */ }
  disconnectedCallback() { /* remove global listeners, abort fetches */ }
  attributeChangedCallback(name, oldV, newV) { this.#internals.setFormValue(newV); }
  formResetCallback() { this.setAttribute('value', '0'); }
}
customElements.define('rc-counter', RcCounter);
```
- Lifecycle: constructor (no attribute reads, no children), `connectedCallback` (can fire repeatedly on moves), `disconnectedCallback`,
  `attributeChangedCallback`, `adoptedCallback`; `connectedMoveCallback` for `moveBefore()`: Chrome 133, Firefox 144, not Safari.
- Form-associated: `attachInternals()` -> `setFormValue`, `setValidity`, `internals.states.add('checked')` + CSS `:state(checked)`,
  `formDisabledCallback`, `formStateRestoreCallback`. Label it with a real `<label for>`.
- Styling hooks: CSS custom properties (inherit through shadow), `::part(name)` (expose deliberately), `::slotted()`, `:host`,
  `:host-context()` is Chromium only (avoid). Share styles with constructable stylesheets (`adoptedStyleSheets`).
- SSR: declarative shadow DOM (`<template shadowrootmode="open">`), Lit SSR (`@lit-labs/ssr` 4.1). Hydrate with `@lit-labs/ssr-client`.
- Lit 3.3: reactive properties, `static styles = css\`...\``, `@lit/context`, `@lit/task`. Decorators: standard TC39 decorators with
  `accessor` keyword or `experimentalDecorators` (do not mix).
- ARIA in shadow DOM: IDREFs do not cross shadow roots; use `ElementInternals` ARIA properties (`internals.role`, `ariaLabel`) or
  reference target (`shadowrootreferencetarget`: Chrome 152 only; Firefox and Safari behind flags). Do not use customized built-ins (`is=`): Safari never shipped them.

## Security
- Wasm: see "Security model" above. CSP `'wasm-unsafe-eval'`; never `'unsafe-eval'` just for Wasm. Pin and patch runtimes.
- SVG uploads are XSS vectors (`<script>`, `onload`, `javascript:` hrefs, `<foreignObject>`): serve user SVG via `<img>` only, from a
  separate origin or with `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'` and `Content-Disposition: attachment`
  on direct fetch; or sanitize (DOMPurify with SVG profile). SVGO's `removeScripts` is not a sanitizer.
- Image decoders are a top RCE surface (libwebp CVE-2023-4863, 2023): process uploads in a sandbox/worker, keep libvips/ImageMagick/
  libheif patched, cap pixel dimensions (decompression bombs), re-encode instead of passing originals through.
- XML: XXE and billion-laughs: disable DTD and external entity resolution (Python `defusedxml`, Java `XMLConstants.FEATURE_SECURE_PROCESSING`
  + `disallow-doctype-decl`, .NET `DtdProcessing.Prohibit`). `fast-xml-parser` does not resolve external entities, but limit entity expansion.
- YAML: `yaml.load` without SafeLoader (PyYAML) and Ruby `YAML.load` on <4 Psych execute code; use safe loaders. Cap alias expansion
  (billion laughs via anchors): js-yaml 5.4.1 hard-limits merge sequences.
- JSON: prototype pollution when merging parsed objects (`__proto__`, `constructor`): use `Object.create(null)`, schema validation,
  or `structuredClone`; duplicate keys: last wins in JS, but other parsers differ (request smuggling between services).
- CSV injection: cells starting with `= + - @ \t \r` run as formulas in Excel; prefix with `'` when exporting user data.
- Protobuf/CBOR/MessagePack: cap message size and nesting depth before decoding untrusted input.
- WebGPU/WebGL: never trust shader input from users; fingerprinting via `WEBGL_debug_renderer_info` (do not collect it: privacy).

## Performance & quality
- Wasm: measure compressed size (target: as small as the feature allows; a "hello" Rust+wasm-bindgen module should be tens of KB,
  Go standard ~2 MB), instantiate time, and JS<->Wasm call counts per frame. Profile in DevTools (Wasm frames show with names if you keep a name section in dev).
- Rendering: hold 60 fps = ~16.7 ms per frame budget including JS; check with DevTools Performance and `requestAnimationFrame` timing;
  WebGPU `timestamp-query` for GPU time.
- SVG: keep inline icon count reasonable (each is DOM); large charts (>~5k nodes) belong on canvas/WebGL (rule of thumb).
- Images: compare AVIF/WebP/JPEG with real visual metrics (SSIMULACRA2/Butteraugli) instead of a fixed quality number.
- Data: JSON parse cost scales with size; stream (NDJSON) large exports; Protobuf/CBOR save size mainly on numeric/binary-heavy payloads; after Brotli, JSON is often within ~20-30% (rule of thumb, measure).

## Testing & tooling
- Wasm: `wasm-tools validate|print|component wit` (inspect), `wasm-objdump`, `twiggy` (size), `wasm-bindgen-test` (Rust in browser/Node),
  `cargo component`, Wasmtime/Node for unit tests; Playwright for real browsers (include Safari for GC/memory64 gaps).
- Graphics: Playwright screenshot diffs with tolerance (GPU output differs across drivers); WebGPU in headless Chrome is software-only unless the runner has a GPU + drivers and flags (`--enable-unsafe-webgpu --use-angle=vulkan --enable-features=Vulkan`), so keep a WebGL 2 path testable.
- SVG: `svgo`, axe for `role="img"` names; check forced-colors mode (`currentColor`).
- Formats: `ajv-cli` / `check-jsonschema` in CI, `yamllint`, `taplo` (TOML format/lint), `buf lint` + `buf breaking` (Protobuf), `xmllint --schema` for XSD.
- Web Components: `@open-wc/testing` or Vitest browser mode; Custom Elements Manifest (`@custom-elements-manifest/analyzer`) for docs/types.

## Common mistakes in AI-written code
- Claiming memory64, multi-memory or relaxed SIMD "work in all browsers": not in Safari (2026-10). (JS string builtins do: Safari 26.2+.)
- Saying WebGPU is Baseline or works in Firefox everywhere: Firefox is Windows + Apple-silicon macOS only.
- `WebAssembly.instantiate(await (await fetch(u)).arrayBuffer())` instead of `instantiateStreaming`; serving `.wasm` as `application/octet-stream`.
- Keeping a `Uint8Array` view over `memory.buffer` across calls that may grow memory (detached buffer errors).
- Mismatched `wasm-bindgen` CLI vs crate version; using `wasm32-wasi` target name; using `wasm-opt` crate 0.116 as "latest".
- Inventing WASI APIs (`wasi:http` request/response shapes changed between 0.2 and 0.3): read the WIT in the version you target.
- Using asm.js, `-s WASM=0`, or Asyncify where JSPI is enough for current browsers.
- SVG: removing `viewBox`, `<title>` dropped, `aria-label` on `<svg>` without `role="img"`, `xlink:href` (use plain `href`), `fill="#000"` instead of `currentColor` for icons.
- Canvas drawn at CSS size on hi-DPI (blurry), no fallback content, `getImageData` in a hot loop without `willReadFrequently`.
- WebGL code without `webglcontextlost` handling; WebGPU without a fallback or `device.lost` handler; GLSL passed to WebGPU.
- Image sprites, icon fonts, GIFs, `lottie-web` for a spinner.
- `JSON.parse` on 64-bit IDs; `yaml.load` unsafe; unquoted `NO`/`on`/`1.10` in YAML; `split(',')` CSV; XML parsers with DTDs enabled.
- Protobuf: reusing/renumbering field numbers, changing a field's type, `required` in proto2, making enums without a zero `UNSPECIFIED` value.
- Custom elements: reading attributes or children in the constructor, not cleaning up in `disconnectedCallback`, `is="..."` customized built-ins, IDREF ARIA across shadow roots.

## Before you ship
- [ ] Wasm earns its place (benchmark vs JS); module lazy-loaded, `wasm-opt`'d, Brotli'd, served as `application/wasm` with a hashed URL
- [ ] Feature detection + fallback for anything not Baseline (WebGPU, JPEG XL, memory64, JSPI on older Safari)
- [ ] Runtime limits on server Wasm (memory, fuel/epoch), minimal WASI capabilities, runtime on a supported major
- [ ] CSP has `'wasm-unsafe-eval'` only if needed; COOP/COEP only if threads are needed, and embeds still work
- [ ] SVG: `viewBox` kept, accessible names or `aria-hidden`, user SVG never inlined unsanitized
- [ ] Canvas/WebGL/WebGPU: hi-DPI, context/device loss handled, text alternative, reduced motion respected
- [ ] Uploads: decoders patched, dimension caps, EXIF stripped, re-encoded
- [ ] Parsers: safe YAML/XML settings, schema validation at trust boundaries, size/depth limits on binary formats
- [ ] Protobuf `buf breaking` passes; JSON Schema/contract tests in CI; custom elements tested in Chrome, Firefox and Safari

## Sources
- https://webassembly.org/news/ and https://webassembly.org/news/2025-09-17-wasm-3.0/ - Wasm 2.0 (2025-03-20), 3.0 (2025-09-17) and its feature list - 2026-10-09
- https://github.com/WebAssembly/proposals - proposal phases (threads/wide arithmetic phase 4, JSPI phase 5, ESM/stack switching phase 3) - 2026-10-09
- https://www.npmjs.com/package/web-features 3.42.0 `data.json` - all browser support/Baseline rows above - 2026-10-09
- https://github.com/mdn/browser-compat-data `api/GPU.json` - WebGPU per-platform notes (Chrome 144 Linux, Firefox 141/145/147) - 2026-10-09
- https://wasi.dev/roadmap - WASI 0.2.0 (2024-01), 0.3.0 (2026-06-11), 0.3.1, release train - 2026-10-09
- https://docs.wasmtime.dev/stability-release.html - monthly majors, LTS every 12, 24 months - 2026-10-09
- crates.io API and GitHub releases (wasmtime, wasmer, WasmEdge, wasm-bindgen, wasm-pack, trunk, wgpu, emscripten, binaryen, svgo, toml, lottie-spec, dotlottie-web) - versions/dates above - 2026-10-09
- https://registry.npmjs.org (npm view) - three, @babylonjs/core, pixi.js, regl, lit, @lit-labs/ssr, svgo, assemblyscript, binaryen, jco, yaml, js-yaml, smol-toml, ajv, @bufbuild/protobuf, protobufjs, msgpackr, cbor-x - 2026-10-09
- https://go.dev/doc/go1.24 and https://go.dev/wiki/WebAssembly - go:wasmexport, c-shared reactor, lib/wasm, binary sizes - 2026-10-09
- https://kotlinlang.org/docs/wasm-overview.html - Kotlin/Wasm Beta, needs WasmGC - 2026-10-09
- https://www.assemblyscript.org/status.html - AssemblyScript limitations and WASI stance - 2026-10-09
- https://dotnetcli.blob.core.windows.net/dotnet/release-metadata/releases-index.json - .NET 10 LTS, 11 go-live, 8/9 EOL 2026-11-10 - 2026-10-09
- https://github.com/svg/svgo/releases/tag/v4.0.0 - removeViewBox/removeTitle off by default, removeScripts rename - 2026-10-09
- https://github.com/toml-lang/toml CHANGELOG - TOML 1.0.0 (2021-01-11), 1.1.0 (2025-12-18) changes - 2026-10-09
- https://json-schema.org/specification - 2020-12 still current - 2026-10-09
- https://github.com/nodeca/js-yaml CHANGELOG - js-yaml 5.0 (2026-06-20) API changes, 5.4.1 merge limits - 2026-10-09
- https://blog.rust-lang.org/2024/04/09/updates-to-rusts-wasi-targets/ - wasm32-wasip1 stable 1.78, wasm32-wasi removed 1.84 - 2026-10-09
- https://blog.rust-lang.org/inside-rust/2025/07/21/sunsetting-the-rustwasm-github-org/ - rustwasm org archived 2025-09, wasm-bindgen org - 2026-10-09
- https://docs.python.org/3.15/whatsnew/3.15.html - Python 3.15 (2026-10-09) tomllib reads TOML 1.1 - 2026-10-09
- https://github.com/mdn/browser-compat-data - moveBefore/connectedMoveCallback (Chrome 133, Firefox 144), ShadowRoot.referenceTarget (Chrome 152) - 2026-10-09
- https://developer.chrome.com/blog/supercharge-web-ai-testing - WebGPU flags for headless Chrome - 2026-10-09
- https://webkit.org/blog/14445/webkit-features-in-safari-17-0/ - Safari 17.0 adds JPEG XL and HEIC - 2026-10-10
- https://webkit.org/blog/17640/webkit-features-for-safari-26-2/ - Safari 26.2 ships Wasm JS String Builtins - 2026-10-10
- https://github.com/mdn/browser-compat-data `webassembly/*.json` - exnref (Chrome 137, Firefox 131, Safari 18.4), tail calls, typed refs, JS string builtins (Safari 26.2), relaxed SIMD/multi-memory (Safari preview only) - 2026-10-10
- https://web-platform-dx.github.io/web-features-explorer/ (wasm-jspi, wasm-memory64, webgpu, jpegxl, offscreen-canvas, scoped-custom-element-registries) - Baseline status/dates - 2026-10-10

Unverified in this pass (rules of thumb only): Basis/KTX2 advice, size/perf numbers.
