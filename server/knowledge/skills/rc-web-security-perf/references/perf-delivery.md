# Performance and delivery reference  (as of 2026-10)

## Core Web Vitals
| Metric | Good | Poor | Measures | Field API |
|---|---|---|---|---|
| LCP | <= 2.5 s | > 4.0 s | When the largest image/text block in the viewport rendered | Largest Contentful Paint API (all engines since Safari 26.2) |
| INP | <= 200 ms | > 500 ms | Worst-ish interaction latency (input -> next paint) over the visit | Event Timing API (all engines since Safari 26.2) |
| CLS | <= 0.1 | > 0.25 | Largest burst of unexpected layout shift | Layout Instability API (Chromium only) |

- Assessed at p75 per page/origin. CrUX = Chrome users only, 28-day window; Search uses it as one ranking signal among many.
- Non-CWV diagnostics: TTFB (good <= 0.8 s), FCP (<= 1.8 s). TBT is a lab proxy for INP.
- RUM: `web-vitals` library (`onLCP`, `onINP`, `onCLS`) with the attribution build; send via `navigator.sendBeacon` on `visibilitychange`.

## Critical path checklist
- HTML streams early (flush head first); one render-blocking CSS file, critical CSS inline only if small.
- Scripts: `type="module"` or `defer`; `async` only for independent third parties.
- Resource hints: `preconnect` to 1-3 critical third-party origins; `dns-prefetch` for others; `preload` only for late-discovered
  critical resources (LCP image in CSS, primary font); `modulepreload` for critical module chains.
- `fetchpriority="high"` on the LCP image; `"low"` on non-critical carousels/below-fold images. Baseline across engines (Firefox 132, Safari 17.2).
- 103 Early Hints for preconnect/preload while the server thinks (Chrome; Safari/Firefox support partial, unverified).

## Speculation rules (Chromium)
```html
<script type="speculationrules">
{ "prerender": [{ "where": { "href_matches": "/*" }, "eagerness": "moderate" }] }
</script>
```
- Exclude logout/add-to-cart/state-changing URLs. Prerendered pages run JS: defer analytics until `document.prerendering` is false
  (`prerenderingchange` event). Can also be sent as `Speculation-Rules` header.

## bfcache
- Blockers: `unload` handler, open WebSocket/IndexedDB transactions at navigation, `window.opener` references; check DevTools
  Application > Back/forward cache, or `PerformanceNavigationTiming.notRestoredReasons` (Chromium).
- `Cache-Control: no-store` no longer blocks it in Chrome (evicted on cookie change); Safari/Firefox rules differ.

## Images
| Format | Status 2026-10 | Use |
|---|---|---|
| AVIF | All engines | First choice for photos; slow encode, do at build/CDN |
| WebP | All engines | Fallback / animations |
| JPEG XL | Safari 17+; Chromium code re-landed (Chrome 145) behind flag; Firefox flag/nightly | Only via `<picture>` with fallback |
| SVG | All | Icons, logos (sanitize user SVG: it can carry script) |

```html
<img src="hero-800.avif" srcset="hero-400.avif 400w, hero-800.avif 800w, hero-1600.avif 1600w"
     sizes="(max-width: 600px) 100vw, 50vw" width="1600" height="900" alt="..." fetchpriority="high">
```
- `loading="lazy"` + `decoding="async"` below the fold only. Use an image CDN for format negotiation (`Accept` header) and resizing.
- Sprite sheets and base64-inlined images: obsolete under HTTP/2; inline only tiny SVGs.

## Fonts
- WOFF2 only; variable fonts replace multiple weights; subset (`unicode-range`, glyphhanger/pyftsubset).
- `font-display: swap` (brand text) or `optional` (best for CLS); metric overrides (`size-adjust`, `ascent-override`) on the fallback.
- Self-host: no cross-site cache benefit from Google Fonts since browsers partitioned HTTP caches (Chrome 86, 2020).

## JavaScript cost
- Ship less: route-level code splitting, dynamic `import()` for rare features, drop polyfills for Baseline features.
- Hydration options: SSR + full hydration (heaviest) -> partial/islands (Astro, Fresh) -> server components (React RSC) -> resumability (Qwik).
- Long tasks: yield with `scheduler.yield()` (Chromium 129+, Firefox 142+) or `setTimeout` fallback:
  ```js
  const yieldToMain = () => globalThis.scheduler?.yield?.() ?? new Promise(r => setTimeout(r, 0));
  ```
- Third-party tags: load after interaction or via a facade (YouTube/maps embeds); measure each with DevTools third-party badges.
- Web Workers for heavy parsing/crypto; `content-visibility: auto` for long off-screen sections.

## Protocols, compression, caching
- HTTP/2 everywhere; HTTP/3 (QUIC) mostly comes free with CDNs. HTTP/2 Server Push is removed from Chrome (106): use Early Hints/preload.
- Compression: Brotli 11 precompressed for static; Brotli 4-6 or zstd for dynamic; gzip fallback. zstd Content-Encoding: Chrome 123+, Firefox 126+, Safari 26.3+.
  Compression dictionaries (`Use-As-Dictionary`, dcb/dcz): Chrome 130+, Firefox 160+, not Safari (caniuse, 2026-10).
- Cache-Control recipes:
  - Hashed assets: `public, max-age=31536000, immutable`
  - HTML (public): `public, max-age=0, s-maxage=300, stale-while-revalidate=86400` (CDN serves stale while refreshing)
  - HTML (personalized): `private, no-cache` + `ETag`
  - API JSON: explicit per endpoint; `Vary` only on headers that really change the body.
- `stale-if-error` for resilience; purge by tag/surrogate key at the CDN on deploy.

## Edge and runtime choices
- Edge functions (Workers, Vercel/Netlify edge, Deno Deploy) win for auth checks, redirects, A/B, personalization near users;
  lose when every request goes back to a single-region database (put data near compute or cache).
- Static first (SSG/ISR) where content allows; SSR for personalized/fresh pages; avoid client-only rendering for content pages (SEO, LCP).

## PWA status per platform
| Capability | Chromium (Android/desktop) | Safari iOS/iPadOS | Safari macOS | Firefox |
|---|---|---|---|---|
| Install | `beforeinstallprompt` + install menu | Share > Add to Home Screen (iOS 26: always opens as web app) | Add to Dock (Safari 17+) | Android; Windows taskbar web apps since Firefox 143 (no `beforeinstallprompt`) |
| Web Push | Yes | Installed web apps only, iOS 16.4+; Declarative Web Push 18.4+ | Yes; Declarative 18.5+ | Yes |
| Background sync, periodic sync | Yes | No | No | No |
- Service worker basics: precache app shell, network-first for HTML, cache-first for hashed assets; version caches and clean up on `activate`.

## Technical SEO that touches engineering
- Content must be in server-rendered HTML (or reliably rendered); Googlebot renders JS but later and not every crawler does (AI crawlers mostly don't run JS).
- One `<link rel="canonical">` per page, absolute URL; consistent trailing slash; 301 for moves; real 404 status (no soft 404).
- XML sitemap with accurate `lastmod`; reference it in robots.txt. robots.txt blocks crawling, not indexing: use `noindex` for that.
- Structured data as JSON-LD, matching visible content; validate with Google's Rich Results Test.
- AI crawlers: opt out of training per agent in robots.txt (e.g. `Google-Extended`, `GPTBot`, `ClaudeBot`, `CCBot`, `Applebot-Extended`);
  blocking `Google-Extended` does not affect Search ranking. Content-Usage / aipref is a draft. Many CDNs offer bot management toggles.
- CWV is a ranking signal but small vs relevance; fix it for users and conversions first.
- `hreflang` for localized variants; `<html lang>` set.
