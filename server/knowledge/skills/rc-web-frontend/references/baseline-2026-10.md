# Baseline status of front-end features (snapshot 2026-10-09)

Source: `web-features` npm package v3.42.0 (published 2026-10-09), the dataset behind web.dev/baseline and MDN's Baseline
banners. "Newly" = in all core browsers (Chrome, Edge, Firefox, Safari, desktop + mobile) since the date; "Widely" = 30 months
after that. Re-check with `npx web-features` data or MDN before relying on a "Limited" row: browsers ship roughly monthly.

Browser releases in the dataset at snapshot time: Chrome 155 (2026-10-06), Firefox 157 (2026-09-29), Safari 27 (2026-09-14).

## Widely available (use without fallback)

| Feature | Newly since | Widely since |
|---|---|---|
| `<dialog>` + `showModal()` | 2022-03 | 2024-09 |
| Cascade layers `@layer` | 2022-03 | 2024-09 |
| `:focus-visible` | 2022-03 | 2024-09 |
| Logical properties (`margin-inline`, `inset-block` ...) | 2021-09 | 2024-03 |
| `aspect-ratio` | 2021-09 | 2024-03 |
| `color-scheme` | 2022-02 | 2024-08 |
| `dvh`/`svh`/`lvh` viewport units | 2022-12 | 2025-06 |
| Container size queries `@container`, `cq*` units | 2023-02 | 2025-08 |
| Import maps | 2023-03 | 2025-09 |
| `inert` attribute | 2023-04 | 2025-10 |
| `oklch()`/`oklab()`, `color()` | 2023-05 | 2025-11 |
| `color-mix()` | 2023-05 | 2025-11 |
| Subgrid | 2023-09 | 2026-03 |
| `image-set()` | 2023-09 | 2026-03 |
| `<search>` element | 2023-10 | 2026-04 |
| `lh` / `rlh` units | 2023-11 | 2026-05 |
| CSS nesting | 2023-12 | 2026-06 |
| `:has()` | 2023-12 | 2026-06 |
| `loading="lazy"` on img/iframe | 2023-12 | 2026-06 |
| Preloading responsive images (`imagesrcset`) | 2023-12 | 2026-06 |
| AVIF | 2024-01 | 2026-07 |
| Declarative shadow DOM (`<template shadowrootmode>`) | 2024-02 | 2026-08 |
| `AbortSignal.any()` | 2024-03 | 2026-09 |

## Newly available (fine for most audiences; widely date = newly + 30 months)

| Feature | Newly since |
|---|---|
| `light-dark()` | 2024-05 (widely 2026-11) |
| `text-wrap: balance` | 2024-05 (widely 2026-11) |
| `@starting-style`, `transition-behavior: allow-discrete` | 2024-08 |
| `<details name>` exclusive accordions | 2024-09 |
| Relative color syntax `oklch(from var(--c) l c h)` | 2024-09 |
| `fetchpriority` | 2024-10 |
| `text-wrap` shorthand | 2024-10 |
| `scrollbar-gutter` | 2024-12 |
| Popover API (`popover`, `popovertarget`) | 2025-01 |
| JSON modules (`import x from './a.json' with {type:'json'}`) | 2025-04 |
| `content-visibility` | 2025-09 |
| `::details-content` | 2025-09 |
| `URLPattern` | 2025-09 |
| Same-document View Transitions + `view-transition-class` | 2025-10 |
| Invoker commands (`command`/`commandfor`) | 2025-12 |
| Navigation API | 2026-01 |
| `:active-view-transition` | 2026-01 |
| Trusted Types | 2026-02 |
| `shape()` | 2026-02 |
| `@scope` | 2026-03 |
| `contrast-color()` | 2026-04 |
| Container style queries `@container style(--x: y)` | 2026-05 |
| `field-sizing: content` | 2026-06 |
| `sibling-count()` / `sibling-index()` | 2026-08 |
| `progress()` | 2026-09 |

## Limited availability (progressive enhancement only)

| Feature | Supported in | Missing |
|---|---|---|
| Anchor positioning (`anchor-name`, `position-anchor`, `position-area`, `@position-try`) | Core: Chrome 125, Firefox 147, Safari 26 | Only `position-visibility: anchor-valid/anchor-visible` blocks Baseline (Safari 27 only). Core is usable everywhere current. |
| Customizable select (`appearance: base-select`, `::picker(select)`, `<selectedcontent>`) | Chrome 135, Safari 27 | Firefox |
| Cross-document View Transitions (`@view-transition { navigation: auto }`) | Chrome 126, Safari 18.2 | Firefox |
| Scroll-driven animations (`animation-timeline: scroll()/view()`) | Chrome 115, Safari 26 | Firefox |
| `text-wrap: pretty` | Chrome 117, Safari 26 | Firefox |
| Speculation rules (`<script type="speculationrules">`) | Chromium only | Firefox, Safari |
| `dialog closedby` | Chrome 134, Firefox 141 | Safari |
| Sanitizer API (`setHTML`) | Chrome 146, Firefox 148 | Safari |
| `popover="hint"` | Chrome 151, Firefox 153 | Safari |
| Typed `attr()` | Chrome 133, Firefox 155 | Safari |
| `sizes="auto"` for lazy images | Chrome 126, Firefox 150 | Safari |
| `hidden="until-found"` | Chrome 102, Firefox 148 | Safari |
| `moveBefore()` | Chrome 133, Firefox 144 | Safari |
| Scoped custom element registries | Chrome 146, Safari 26 | Firefox |
| CSS `if()` | Chrome 137 | Firefox, Safari |
| CSS `@function` custom functions | Chrome 139 | Firefox, Safari |
| `interpolate-size` / `calc-size()` | Chromium 129 | Firefox, Safari |
| Interest invokers (`interestfor`) | Chrome 142 | Firefox, Safari |
| Scroll-state container queries | Chrome 133 | Firefox, Safari |
| `reading-flow` | Chrome 137 | Firefox, Safari |
| `corner-shape` | Chrome 139 | Firefox, Safari |
| `text-box` (trim) | Chrome 133, Safari 18.2 | Firefox |
| Grid lanes (masonry) | Safari 26.4 | Chrome, Firefox |
| `accent-color` | Chrome 93, Firefox 92, Safari 26.2 (listed not Baseline in data; recheck) | - |
