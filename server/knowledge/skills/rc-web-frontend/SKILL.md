---
name: rc-web-frontend
description: Current (Oct 2026) front-end standards for HTML, CSS, accessibility, responsive images and the framework/tooling landscape (React 19.3, Next.js 16, Vue 3.5/3.6, Svelte 5 + SvelteKit 3, Angular 22, Astro 7, Solid, Qwik, HTMX, Lit, Tailwind v4, Vite 8/Rolldown, TanStack). Use when writing or reviewing .html/.css/.jsx/.tsx/.vue/.svelte/.astro files, picking a framework or bundler, building UI components (dialogs, popovers, menus, selects, tooltips, forms), or checking browser support (Baseline).
---
# Web front-end (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Facts below were checked on 2026-10-09 against npm registry metadata, `web-features` 3.42.0 (the Baseline dataset), MDN, W3C and
official framework blogs. Per-feature Baseline table: `references/baseline-2026-10.md`.

## Currency check

Browsers (in Baseline data on 2026-10-09): Chrome 155 (2026-10-06), Firefox 157 (2026-09-29), Safari 27 (2026-09-14).
"Baseline widely available" = in all core browsers for 30+ months; "newly" = in all of them now.

| Package (npm `latest`) | Version | Major released | Notes |
|---|---|---|---|
| react / react-dom | 19.3.0 | 19.0 2024-12-05; 19.2 2025-10-01; 19.3 2026-09-09 | 19.3: `<ViewTransition>`, `addTransitionType`, Fragment refs, `use(browser())`, Trusted Types |
| babel-plugin-react-compiler | 1.0.0 | 2025-10-07 | React Compiler stable |
| next | 16.4.0 | 16.0 2025-10-21; 16.4 2026-10-06 | Turbopack default, Cache Components, `proxy.ts` |
| react-router | 8.4.0 | 8.0 2026-06-17 | v7 on `version-7` tag |
| vue | 3.5.43 | 3.5 2024-09-03 | 3.6 is `rc` (3.6.0-rc.10, Vapor Mode): not stable |
| nuxt | 4.6.0 | 4.0 2025-07-15 | |
| pinia / vue-router | 4.0.3 / 5.4.0 | 2026-07-14 / 2026-01-29 | |
| svelte | 5.57.2 | 5.0 2024-10-19 | runes |
| @sveltejs/kit | 3.0.1 | 3.0 2026-10-01 | brand new major: read migration notes |
| @angular/core | 22.2.2 | 21 2025-11-19; 22 2026-06-03 | zoneless default since v21, Vitest default runner since v21 |
| astro | 7.3.8 | 6.0 2026-03-10; 7.0 2026-06-22 | 7: Vite 8, Rust compiler |
| solid-js | 1.9.17 | | 2.0 on `next` (rc) |
| Qwik | @builder.io/qwik 1.20.2 | | Qwik 2 = `@qwik.dev/core` 2.0.0-rc.2 |
| htmx.org | 2.0.11 | | 4.0 released 2026-08-28, npm tag `next`; `latest` moves in 2027 |
| lit | 3.3.3 | | |
| tailwindcss | 4.3.3 | 4.0 2025-01-21; 4.3 2026-05-08 | v3 on `v3-lts` (3.4.19) |
| vite | 8.3.4 | 7.0 2025-06-24; 8.0 2026-03-12 | Rolldown + Oxc; Node 20.19+/22.12+ |
| rolldown | 1.2.13 | 1.0 2026-05-07 | |
| @rspack/core | 2.2.8 | 2.0 2026-04-22 | webpack-compatible |
| @tanstack/react-query | 5.104.1 | | Router 1.170, Start 1.168 |
| zustand / jotai / preact | 5.0.15 / 3.0.1 / 11.0.1 | jotai 3 2026-09-08; preact 11 2026-09-30 | |
| vitest / @playwright/test | 5.0.3 / 1.64.0 | vitest 5 2026-09-03 | |
| eslint / @biomejs/biome | 10.12.0 / 2.5.15 | eslint 10 2026-02-06 | flat config only |
| typescript | 7.0.2 | | see rc-javascript-typescript |

Accessibility standard: WCAG 2.2 (W3C Rec 2023-10-05, updated 2024-12-12). WCAG 3 is still an early draft: do not target it.

## What changed / stop doing

| Old | New | Since |
|---|---|---|
| `create-react-app` | Vite (`npm create vite@latest`) or a framework (Next, React Router, TanStack Start) | CRA sunset 2025-02-14 |
| Class components, `componentDidMount` | Function components + hooks; Actions/`useActionState` for mutations | React 16.8+, 19 |
| Manual `useMemo`/`useCallback`/`React.memo` everywhere | React Compiler (`reactCompiler: true` in Next, Babel plugin elsewhere) | Compiler 1.0, 2025-10 |
| `forwardRef` | `ref` is a normal prop | React 19 |
| `<Context.Provider>` | render `<Context>` directly (also in Server Components in 19.3) | React 19 / 19.3 |
| `useEffect` + `useState` fetch waterfalls | framework loaders / RSC, `use(promise)`, or TanStack Query | React 19 |
| Next pages router as default, `getServerSideProps` | App Router, async Server Components, Server Actions | Next 13+; default |
| Next implicit fetch caching, `experimental.ppr`, `dynamicIO` | `cacheComponents: true` + `"use cache"`, `cacheLife`, `cacheTag` (caching is opt-in) | Next 16 |
| `middleware.ts` | `proxy.ts` exporting `proxy` (Node runtime) | Next 16 (middleware deprecated) |
| sync `cookies()`, `headers()`, `params` | `await cookies()`, `await params` | Next 16 (removed sync) |
| `revalidateTag(tag)` | `revalidateTag(tag, 'max')`, or `updateTag(tag)` in a Server Action | Next 16 |
| `next lint`, AMP, `publicRuntimeConfig`, `images.domains` | ESLint/Biome CLI, env vars, `images.remotePatterns` | Next 16 |
| `next build --webpack` by habit | Turbopack is default | Next 16 |
| Zone.js, `NgModule`, `*ngIf`/`*ngFor` | zoneless, standalone, `@if`/`@for`/`@defer`, signals | Angular 21 (zoneless default), 17+ |
| Karma + Jasmine | Vitest (Angular default runner) | Angular 21 |
| Svelte 4 `export let`, `$:`, stores for local state | `$props()`, `$state`, `$derived`, `$effect` | Svelte 5 |
| Vue Options API / `reactive()` destructuring hacks | `<script setup>`, reactive props destructure, `useTemplateRef`, `useId` | Vue 3.5 |
| `tailwind.config.js`, `@tailwind base;` directives, PostCSS-only setup | `@import "tailwindcss";` + `@theme { --color-brand: oklch(...) }` in CSS, `@tailwindcss/vite` | Tailwind 4.0 |
| `build.rollupOptions`, `esbuild:` in vite.config | `build.rolldownOptions`, `oxc:` (auto-converted, but update) | Vite 8 |
| webpack 5 hand-rolled config for new apps | Vite 8, or Rspack 2 when you must keep webpack config | 2026 |
| jQuery, `$(document).ready`, `.ajax` | DOM APIs, `fetch` + `AbortController`, `<script type="module">` | - |
| Float/clearfix layouts, Bootstrap-era grid classes | Flexbox, Grid, subgrid | subgrid widely 2026-03 |
| Viewport media queries for component layout | `@container` (size queries widely 2025-08) | - |
| `px` breakpoints and `font-size` in px | `em`/`rem` breakpoints, `clamp()` fluid type | - |
| `100vh` mobile hacks | `100dvh` / `svh` | widely 2025-06 |
| Sass for nesting/variables/colour maths | native nesting, custom properties, `color-mix()`, relative colour | widely 2025-11 / 2026-06 |
| BEM-only scoping, CSS-in-JS runtime | `@layer`, `@scope` (newly 2026-03), CSS Modules, compiled CSS | - |
| hex/HSL palettes | `oklch()`; `light-dark()` with `color-scheme` | - |
| JS modal/focus-trap libraries | `<dialog>.showModal()` (traps focus, `inert` outside, Esc closes) | widely 2024-09 |
| JS dropdown/tooltip toggles | `popover` + `popovertarget`, or `command`/`commandfor` | newly 2025-01 / 2025-12 |
| Popper/Floating UI for simple tooltips | CSS anchor positioning (core works in all current engines; see refs) | 2026 |
| JS accordion "only one open" | `<details name="group">` | newly 2024-09 |
| JS autosize textarea | `field-sizing: content` | newly 2026-06 |
| `lazysizes`, IntersectionObserver lazy images | `loading="lazy"` (never on the LCP image) | widely 2026-06 |
| `role="search"` on `<form>` | `<search>` element | widely 2026-04 |
| `outline: none` + `:focus` | `:focus-visible` with a visible ring | widely 2024-09 |
| WCAG 2.1 checklists, 4.1.1 Parsing | WCAG 2.2 AA; 4.1.1 obsolete | 2023-10 |
| htmx 1.x | htmx 2 (4 is out on `next`) | - |

## Do this

### HTML primitives first (ladder: native element > CSS > JS > library)
```html
<!-- Modal: focus trap, Esc, top layer, ::backdrop come free -->
<button commandfor="confirm" command="show-modal">Delete</button>
<dialog id="confirm" aria-labelledby="confirm-title">
  <h2 id="confirm-title">Delete file?</h2>
  <form method="dialog">
    <button value="cancel" autofocus>Cancel</button>
    <button value="ok">Delete</button>
  </form>
</dialog>

<!-- Non-modal menu / disclosure: light dismiss, top layer -->
<button popovertarget="menu">Options</button>
<div id="menu" popover>...</div>
```
- `command`/`commandfor` is newly Baseline (2025-12); for older Safari/Firefox keep a JS `click` fallback or use `popovertarget`.
- `dialog.returnValue` gives the clicked button's `value`. `closedby="any"` is not in Safari yet: add a backdrop click handler if needed.
- Animate open/close with `@starting-style` + `transition-behavior: allow-discrete` on `display` and `overlay`.
- Customizable `<select>` (`select, ::picker(select) { appearance: base-select; }`) is Chrome + Safari 27 only: it degrades to a
  normal select, so it is safe as enhancement. Never rebuild a select from divs.
- Speculation rules are Chromium-only; harmless elsewhere. Prefer `"eagerness": "moderate"` and only same-origin GET pages with no side effects.

### CSS architecture
```css
@layer reset, tokens, base, components, utilities;   /* order = priority */

@layer tokens {
  :root {
    color-scheme: light dark;
    --surface: light-dark(oklch(99% 0 0), oklch(18% 0.01 260));
    --brand: oklch(62% 0.19 255);
    --brand-hover: oklch(from var(--brand) calc(l - 0.08) c h);
  }
}
@layer components {
  .card-list { container-type: inline-size; }
  .card {
    display: grid; gap: 1rem;
    & h2 { text-wrap: balance; }
    @container (width > 40rem) { grid-template-columns: 12rem 1fr; }
  }
  .field:has(input:user-invalid) { --ring: oklch(55% 0.2 25); }
}
@media (prefers-reduced-motion: no-preference) {
  @view-transition { navigation: auto; }   /* Chrome + Safari; ignored by Firefox */
}
```
- Use logical properties (`margin-inline`, `padding-block`, `inset-inline-start`) so RTL works.
- `if()`, `@function`, typed `attr()`, `interpolate-size`, scroll-state queries, `corner-shape`: Chromium-first. Wrap in `@supports` or skip.
- Scroll-driven animations: Chrome + Safari 26, not Firefox. Wrap in `@supports (animation-timeline: view())`.
- Anchor positioning: core properties ship in Chrome 125, Firefox 147, Safari 26. Fine with `@supports (anchor-name: --a)` fallback for older browsers.

### Responsive images
```html
<img src="hero-1200.avif" srcset="hero-800.avif 800w, hero-1600.avif 1600w"
     sizes="(width > 60rem) 60rem, 100vw" width="1600" height="900"
     alt="..." fetchpriority="high" decoding="async">
<img src="thumb.avif" loading="lazy" width="400" height="300" alt="...">
```
- Always set `width`/`height` (or `aspect-ratio`) to stop CLS. AVIF is widely Baseline (2026-07); WebP fallback via `<picture>` only if you serve very old Safari.
- LCP image: never `loading="lazy"`; add `fetchpriority="high"`; preload only if it is discovered late (CSS background, JS-inserted).
- `sizes="auto"` only works with lazy images in Chrome/Firefox: still give a real `sizes` value.

### Accessibility (WCAG 2.2 AA)
- New 2.2 AA criteria to check: 2.4.11 Focus Not Obscured (sticky headers must not hide focus: `scroll-padding-top`), 2.5.7 Dragging
  Movements (single-pointer alternative for drag), 2.5.8 Target Size Minimum (24x24 CSS px), 3.3.8 Accessible Authentication (allow paste
  and password managers, no puzzle-only CAPTCHA). Level A: 3.2.6 Consistent Help, 3.3.7 Redundant Entry.
- Native elements over ARIA. Follow the ARIA Authoring Practices pattern exactly when you must build a widget (combobox, tabs, menu).
- Every input has a `<label for>`; errors linked with `aria-describedby`; use `autocomplete` tokens; announce async status with a polite live region.
- Focus management: move focus into new content (dialog does it), return it on close, use `inert` on background, never `tabindex > 0`.
- Respect `prefers-reduced-motion` (wrap motion in `no-preference`), `prefers-contrast`, `forced-colors` (use `CanvasText`, `ButtonText`).
- Contrast: 4.5:1 text, 3:1 large text and UI. Do not rely on `contrast-color()` alone to meet it.
- SPA route changes: move focus to the `<h1>` or announce the title.

### Frameworks: pick by need
- Content site / docs / marketing: Astro 7 (islands, zero JS by default) or plain HTML + Vite.
- Server-rendered app with forms: HTMX 2 or a meta-framework (SvelteKit 3, Nuxt 4, Next 16, React Router 8, TanStack Start, Angular SSR).
- Highly interactive SPA behind login: Vite 8 + React/Vue/Svelte/Solid + TanStack Router/Query. SSR is optional here.
- Reusable widgets across stacks: Web Components (Lit 3; declarative shadow DOM is widely Baseline for SSR).
- Rendering choice: SSG for content that changes rarely; SSR/streaming for per-request data; islands/partial hydration when most of the page is static.

### React 19 idioms
```tsx
'use client';
import { useActionState } from 'react';
import { save } from './actions';           // 'use server' function

export function Form() {
  const [state, action, pending] = useActionState(save, { error: null });
  return (
    <form action={action}>
      <input name="title" required />
      <button disabled={pending}>Save</button>
      {state.error && <p role="alert">{state.error}</p>}
    </form>
  );
}
```
- Also current: `useOptimistic`, `useFormStatus`, `use()` (promises and context), `<Activity>`, `useEffectEvent` (19.2),
  `<ViewTransition>` + `addTransitionType`, Fragment refs, `use(browser())` to skip SSR (19.3), `<title>`/`<link>`/`<meta>` hoisting, `ref` cleanup functions.
- Next 16: `cacheComponents: true`; mark cached work with `"use cache"` + `cacheLife('hours')` + `cacheTag('posts')`; dynamic work streams inside `<Suspense>`.

### Vue / Svelte / Angular idioms
- Vue 3.5: `<script setup lang="ts">`, `const { size = 'md' } = defineProps<{ size?: string }>()`, `defineModel()`, `useTemplateRef('el')`, Pinia for shared state. Vapor Mode only after 3.6 is stable.
- Svelte 5: `let { items } = $props(); let count = $state(0); const total = $derived(items.length);` Use `$effect` only for side effects, never to sync state.
- Angular 22: standalone components, `signal`/`computed`/`linkedSignal`, `input()`/`output()`/`model()`, `resource`/`httpResource`, Signal Forms, `@defer` for lazy chunks.

### Tailwind v4
```css
@import "tailwindcss";
@theme { --color-brand: oklch(62% 0.19 255); --font-sans: "Inter", system-ui, sans-serif; }
@custom-variant dark (&:where([data-theme=dark], [data-theme=dark] *));
```
- Requires Safari 16.4+, Chrome 111+, Firefox 128+. Need older? stay on v3 (`v3-lts`).
- Plugin: `@tailwindcss/vite` (Vite) or `@tailwindcss/postcss`. No `content: []` array: sources are detected automatically (`@source` to add).

## Security
- XSS: avoid `dangerouslySetInnerHTML`, `v-html`, `{@html}`, `[innerHTML]`, `innerHTML`. If unavoidable: sanitize (DOMPurify; native
  `setHTML()` is Chrome 146/Firefox 148 only) and enforce Trusted Types (newly Baseline 2026-02) via CSP `require-trusted-types-for 'script'`.
- CSP: nonce- or hash-based `script-src` with `'strict-dynamic'`, no `'unsafe-inline'`. Frameworks: Next nonces via `proxy.ts`, Astro 7 has CSP controls, SvelteKit `csp` config.
- React Server Components: CVE-2025-55182 (CVSS 10, unauthenticated RCE via Server Function endpoints), fixed in react-server-dom-* 19.0.1/19.1.2/19.2.1,
  plus follow-up DoS/source-exposure advisories (2025-12-11; CVE-2026-23864 on 2026-01-26). Keep `react`, `react-dom`, `next` on the latest patch; watch nextjs.org/blog security posts.
- Server Actions/Functions are public HTTP endpoints: validate input (zod/valibot), check auth inside every action, never trust hidden fields.
- Never put secrets in client env (`NEXT_PUBLIC_*`, `VITE_*`, `PUBLIC_*` are shipped to the browser).
- Next 16 `next/image`: keep `images.remotePatterns` tight; local IP optimization is blocked by default.
- Third-party scripts: SRI (`integrity`) for CDN files, or self-host. `rel="noopener"` is default for `target=_blank` but keep `rel="noreferrer"` where needed.
- `postMessage`: always check `event.origin`. Do not store tokens in `localStorage`; use `HttpOnly; Secure; SameSite=Lax` cookies.

## Performance & quality
- Core Web Vitals (p75 field data): LCP <= 2.5 s, INP <= 200 ms, CLS <= 0.1. INP replaced FID on 2024-03-12.
- Measure field data (CrUX, `web-vitals` library), lab with Lighthouse / Chrome DevTools Performance panel; React Performance Tracks in DevTools (React 19.2).
- JS budget: aim < ~150 KB compressed initial JS on mobile (rule of thumb); check with a bundle analyzer (Next 16.1+ ships one, experimental; Vite/Rolldown visualizer plugins).
- INP: break long tasks (`scheduler.yield()` in Chrome 129/Firefox 142, not Safari; else `setTimeout`), move work off the main thread, avoid hydrating static parts (islands, RSC).
- `content-visibility: auto` for long off-screen sections (newly 2025-09). Fonts: `font-display: swap`, subset, preload one or two files at most.
- Speculation rules prerender (Chromium) and cross-document view transitions are cheap wins for MPAs.

## Testing & tooling
- Scaffolding: `npm create vite@latest`, `npx create-next-app@latest`, `npx sv create`, `npm create astro@latest`, `npm create vue@latest`, `ng new`.
- Unit/component: Vitest 5 (+ Testing Library, browser mode for real DOM). Angular uses Vitest by default since v21.
- E2E + a11y: Playwright 1.64 + `@axe-core/playwright`; run axe on every page state, then do a keyboard-only and screen-reader pass (axe finds roughly a third of issues).
- Lint/format: ESLint 10 (flat `eslint.config.js` only) + `eslint-plugin-jsx-a11y`/`eslint-plugin-react-hooks` (includes Compiler rules), or Biome 2. Prettier or Biome format.
- Browser support: declare targets with `browserslist` or `baseline-browser-mapping`; Vite 8 default target = Baseline widely available on 2026-01-01 (Chrome 111, Firefox 114, Safari 16.4).
- Type checking: `tsc --noEmit` / `vue-tsc` / `svelte-check` / `astro check` in CI.

## Common mistakes in AI-written code
- Writing Next 13-15 code for Next 16: sync `params`, `middleware.ts`, `export const revalidate` with implicit fetch caching, `experimental.ppr`, `next lint`.
- `import { useFormState } from 'react-dom'` (renamed to `useActionState` from `react`).
- Adding `forwardRef`, `React.FC` children typing, or `useCallback` on every handler when the Compiler is on.
- Generating `tailwind.config.js` + `@tailwind` directives for a v4 project; using `bg-opacity-*` (v4: `bg-black/50`).
- Svelte 4 syntax (`export let`, `on:click`, `$:`) in Svelte 5 files: use `$props()`, `onclick`, `$derived`.
- Angular `NgModule`s, `*ngIf`, `zone.js` imports, Karma configs in new projects.
- Vue `this.$refs`, mixins, Vuex for new code (use Pinia).
- Claiming a feature is "supported everywhere" without checking: customizable select, `if()`, `@function`, scroll-driven animations, cross-document view transitions, `text-wrap: pretty` are NOT Baseline.
- `<div onClick>` buttons, `aria-label` on non-interactive divs, `role="button"` without key handling, `placeholder` as label, removing focus outlines.
- `loading="lazy"` on the hero image; missing `width`/`height`; `srcset` without `sizes`.
- `@media (max-width: 768px)` to resize a card: use `@container`. Ordering `min-width` queries in px for typography instead of `clamp()`.
- Treating `100vh` as the mobile viewport; using `vh` in font sizes without a rem floor (breaks zoom, WCAG 1.4.4).
- Animations without a `prefers-reduced-motion` guard; view transitions included.
- Using `create-react-app`, `react-scripts`, `vue-cli`, `@vue/cli`, Gatsby for new projects.
- Fetching in `useEffect` with no abort/cleanup and no loading/error state.

## Before you ship
- [ ] Every feature used is Baseline for the audience, or has a tested fallback (`@supports`, progressive enhancement)
- [ ] Keyboard-only pass: visible focus, logical order, nothing hidden by sticky UI, dialogs return focus
- [ ] axe/Playwright run is clean; labels, alt text, contrast 4.5:1, 24px targets, reduced-motion respected
- [ ] LCP image not lazy, has dimensions and `fetchpriority="high"`; CLS < 0.1 on a throttled mobile profile
- [ ] No secrets in client bundles; CSP set; no unsanitized HTML sinks; Server Actions validate input and auth
- [ ] Framework and React/Next on the latest patch (RSC advisories); lockfile committed
- [ ] Light and dark (`color-scheme`, `light-dark()`), RTL (logical properties) and 200% zoom checked

## Sources
- https://registry.npmjs.org (via `npm view <pkg> version time dist-tags`) - all versions and release dates above - 2026-10-09
- https://www.npmjs.com/package/web-features v3.42.0 `data.json` - every Baseline status and date - 2026-10-09
- https://web.dev/baseline - Baseline definitions (newly vs widely, 30 months) - 2026-10-09
- https://react.dev/blog - React 19.2 (2025-10-01), Compiler 1.0 (2025-10-07), CRA sunset (2025-02-14), 19.3 (2026-09-09) - 2026-10-09
- https://react.dev/blog/2026/09/09/react-19-3 - ViewTransition, Fragment refs, browser(), Trusted Types, Context in RSC - 2026-10-09
- https://react.dev/blog/2025/12/03/critical-security-vulnerability-in-react-server-components - CVE-2025-55182 and fixed versions - 2026-10-09
- https://nextjs.org/blog/next-16 - Cache Components, proxy.ts, removals, revalidateTag/updateTag/refresh, image defaults, Node 20.9+ - 2026-10-09
- https://web.dev/blog/inp-cwv-march-12 - INP replaced FID 2024-03-12 - 2026-10-09
- https://nextjs.org/blog - 16.1-16.4 release list, security posts - 2026-10-09
- https://vite.dev/blog/announcing-vite8 and https://vite.dev/guide/migration - Rolldown/Oxc, renamed options, default targets - 2026-10-09
- https://angular.dev/guide/zoneless - zoneless default since v21 - 2026-10-09
- https://angular.dev/roadmap - signals/linkedSignal stable v20, Signal Forms stable, Vitest default since v21 - 2026-10-09
- https://github.com/vuejs/core/releases - 3.5.43 stable, 3.6.0-rc.10 - 2026-10-09
- https://svelte.dev/blog - SvelteKit 3 (2026-10-01), async Svelte, remote functions - 2026-10-09
- https://astro.build/blog - Astro 7.0 (2026-06-22) Vite 8, Rust compiler; 7.x CSP controls - 2026-10-09
- https://htmx.org - htmx 4.0 released 2026-08-28, not npm latest until 2027 - 2026-10-09
- https://tailwindcss.com/blog - v4.0 CSS-first config and browser targets, 4.1, 4.3 - 2026-10-09
- https://www.w3.org/WAI/standards-guidelines/wcag/ and /new-in-22/ - WCAG 2.2 dates, 9 new criteria, 4.1.1 obsolete - 2026-10-09
- https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Forms/Customizable_select - base-select syntax and fallback - 2026-10-09

Unverified in this pass: SvelteKit 3 breaking-change details, htmx 2 -> 4 migration details, Svelte remote functions stability,
Vue Vapor Mode feature scope, the ~150 KB JS budget (rule of thumb, not a standard).
