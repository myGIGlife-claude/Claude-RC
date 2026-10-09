---
name: rc-ui-ux-design
description: UI and UX design rules for building web and mobile interfaces that look intentional and are usable and accessible - layout, spacing, type, color/OKLCH, dark mode, design tokens (DTCG), components, forms, states, motion, WCAG 2.2, EAA, Material 3 Expressive vs Apple Liquid Glass, responsive/adaptive, i18n, AI-feature UX, dark-pattern law. Use when designing or reviewing any screen, component, CSS/Tailwind theme, Compose MaterialTheme, SwiftUI view, Figma handoff, dashboard, form, onboarding, settings or landing page, or when the output risks looking like generic "AI slop".
---
# UI and UX design for web and mobile  (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

References: `references/components-and-states.md` (component and state patterns, forms, tables, AI UX),
`references/a11y-and-law.md` (WCAG 2.2 criteria, EAA, dark-pattern and cancellation law),
`references/tokens-and-platforms.md` (DTCG tokens, Figma, CSS/Compose/SwiftUI theme mapping, platform side by side).

## Currency check

| Thing | Status (2026-10) |
|---|---|
| WCAG | **2.2** is the target (W3C Rec Oct 2023; ISO/IEC 40500:2025 since 2025-10-21). 2.2 removed 4.1.1 Parsing. |
| WCAG 3.0 | Working Draft only (latest 2026-09-10). Not a compliance target. Its contrast algorithm is "yet to be determined"; APCA is NOT in it. |
| European Accessibility Act | Applies since **2025-06-28**. Services already in use may run under transition until 2030-06-28; micro-enterprise *services* exempt. Presumption of conformity via EN 301 549 v3.2.1 (= WCAG 2.1 AA); ETSI published WCAG 2.2-based **v4.1.1 on 2026-09-02**, OJ citation pending (date unverified). Design to WCAG 2.2 AA. |
| EU withdrawal button | Directive (EU) 2023/2673 (new CRD Art. 11a) applies from **2026-06-19**: online B2C contracts need a clearly labelled "withdraw from contract here"-style function. |
| EU Digital Fairness Act | Commission proposal planned Q4 2026, not law yet. Dark patterns already covered by UCPD, DSA Art. 25, GDPR consent rules. |
| US "click to cancel" | FTC 2024 rule vacated by the 8th Circuit on 2025-07-08; FTC ANPRM in the Federal Register 2026-03-13 (comments closed 2026-04-13). ROSCA + ~30 state auto-renewal laws (California AB 2863, in force 2025-07-01: cancel in the same medium) still apply. |
| Design tokens | DTCG Format **2025.10** = first stable spec (2025-10-28). Style Dictionary v5 supports it partially (structured color since 5.3.0, object dimensions since 5.4.0). Figma announced native DTCG variable import/export (Nov 2025; ship date unverified, check your Figma). |
| Material | M3 **Expressive** (announced May 2025). Compose material3 stable 1.4.x; 1.5.0 is in beta (2026-10) and promotes most expressive components (buttons, FAB menu, toolbars, lists) to stable. Check the release notes before using `MaterialExpressiveTheme`. |
| Apple | **Liquid Glass** (iOS/macOS 26, 2025-06). iOS 27 (WWDC 2026-06) reportedly refines it and adds a user clear-to-tinted slider (press reports; not confirmed on Apple docs). Respects Reduce Transparency / Increase Contrast. |
| Android adaptive | Window size classes now 5 widths: compact <600, medium 600-839, expanded 840-1199, **large 1200-1599, extra-large ≥1600** dp (WindowManager 1.5; Compose: `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`). Android 17 (2026-06): apps targeting API 37 cannot lock orientation/resizability on ≥600dp displays (no opt-out; games exempt). Play requires target 37 from 2027-08. |
| Web platform | Baseline: container size queries, `:has()`, OKLCH/`color-mix()`, `light-dark()`, popover, `@starting-style`, same-document View Transitions (2025-10), `contrast-color()` (2026-04, black/white only). Not Baseline: cross-document view transitions (no Firefox), customizable `<select>` (`appearance: base-select`: Chromium 135+, Safari 27; Firefox no), scroll-state queries, `corner-shape`. Interop 2026 includes anchor positioning, container style queries, dialog/popover additions, scroll-driven animations, view transitions. |

## What changed / stop doing

| Old advice | Do now | Since |
|---|---|---|
| "Use APCA, WCAG 2 contrast is obsolete" | Meet WCAG 2.x ratios (4.5:1 text, 3:1 large text and UI parts). APCA can be an extra readability check, never instead | WCAG 3 WD 2026-09 still says algorithm TBD |
| Focus appearance is an AA rule | 2.4.13 Focus Appearance is **AAA**; AA needs 2.4.7 visible focus + 2.4.11 focus not *fully* hidden by sticky headers/toasts. Still design a 2px+ high-contrast ring | WCAG 2.2 |
| 44x44 everywhere is "the WCAG rule" | AA = 2.5.8: 24x24 CSS px or spacing. Platform minimums are stricter and what you should use: Apple 44x44pt, Material 48x48dp | WCAG 2.2 |
| HSL palettes, lighten()/darken() | OKLCH ramps (even perceived lightness), `color-mix(in oklch, …)`, relative color syntax | Baseline 2023-24 |
| Separate dark stylesheet, `prefers-color-scheme` everywhere | Semantic tokens + `color-scheme: light dark` + `light-dark()` | Baseline 2024 |
| Media queries only | Container queries for components, media queries for page layout | Baseline 2023 |
| JS modal libs, z-index wars | `<dialog>` (`showModal`), `popover`, anchor positioning (progressive), `closedby` for light dismiss (check support) | 2023-26 |
| JS page-transition libraries | View Transitions API (same-document Baseline); cross-document as progressive enhancement | 2025-10 |
| Custom JS dropdown for styling | Native `<select>`; `appearance: base-select` as enhancement | Chromium 135, Safari 27 |
| Lock phone apps to portrait | Adaptive layouts by window size class; Android 17 ignores the lock on ≥600dp | Android 16/17 |
| Material 2 / M3 baseline easing only | M3 motion scheme springs (`MotionScheme.standard()/expressive()`) | M3 Expressive 2025 |
| Flat opaque iOS bars, custom blurred tab bars | System bars/controls get Liquid Glass for free; use `.glassEffect()` only for custom floating controls, never on content | iOS 26 |
| Tokens in ad-hoc JSON / Tokens Studio dialect | DTCG 2025.10 (`$value`, `$type`, `$extends`, structured colors) | 2025-10 |
| "Are you sure?" dialogs for every delete | Undo snackbar for reversible actions; typed confirmation only for irreversible, high-impact ones | long-standing, still violated |
| Placeholder as label | Persistent visible `<label>`; placeholder only for format hints | always |
| Inline validation on every keystroke | Validate on blur (and after first submit, live while fixing) | always |
| Cookie banner with "Accept" big and "Reject" hidden | Equal-prominence Accept/Reject at the first layer; no pre-ticked boxes | GDPR/EDPB, CNIL fines |
| Cancel by phone/chat retention maze | Cancel online in the same medium, as easy as signup; at most one save offer | CA AB 2863 2025; EU 2026-06 |

## Do this

### Design brief to implementation (recipe)
1. **Write the brief in 6 lines** before any code: user + context (device, one-handed? glanceable?), the top 3 tasks, the primary action per screen, content/data shape (real examples, worst-case lengths), platform(s), brand adjectives (pick 3, e.g. "calm, precise, warm").
2. **Inventory existing design system**: tokens, components, fonts already in the repo. Reuse; never invent a parallel scale.
3. **Information architecture**: list objects and verbs; group into ≤5 top destinations; name them with user words (card-sort mentally: "where would I look for X?").
4. **Task flows**: for each top task write the happy path in ≤5 steps, then add: empty, loading, error, offline, partial, permission-denied, success. Every screen gets all of them (see `references/components-and-states.md`).
5. **Pick the platform pattern** (nav model, component set) from the table below; follow the platform unless a brand reason is written down.
6. **Set tokens first**: spacing scale, type scale, color roles (light + dark), radius, elevation, motion. Write them as CSS variables / `MaterialTheme` / SwiftUI extensions.
7. **Lay out mobile-narrow first** with real content (longest name, 0 items, 10k items, German strings, RTL).
8. **Build with semantic, native elements**; style with tokens only (no raw hex/px in components).
9. **Check**: contrast, keyboard/focus order, screen reader names, 200% text, reduced motion, dark mode, 320px width, touch targets. Run the AI-slop checklist.
10. **Validate** with the tasks: 5 users or at least a hallway test of the top task; instrument the funnel events.

### Layout and spacing
- One spacing scale on a **4pt base** (4, 8, 12, 16, 24, 32, 48, 64, 96). Components snap to it; 8pt rhythm for layout.
- Proximity carries meaning: space *inside* a group < space *between* groups (e.g., label→input 4-8, field→field 16-24, section→section 32-48).
- Content max-width ~**60-75 characters** for body text (`max-inline-size: 65ch`).
- Align to a few edges; left-align text blocks (centered only for short headings and empty states).
- Density: comfortable default for consumer apps; offer compact for data-heavy pro tools (rows 32-40px desktop, never below target-size minimums on touch).

```css
:root {
  --space-1: .25rem; --space-2: .5rem; --space-3: .75rem; --space-4: 1rem;
  --space-6: 1.5rem; --space-8: 2rem; --space-12: 3rem;
  --step-0: clamp(1rem, .95rem + .25vw, 1.125rem);   /* body */
  --step-1: clamp(1.25rem, 1.1rem + .6vw, 1.5rem);
  --step-2: clamp(1.6rem, 1.3rem + 1.2vw, 2.25rem);
}
body { font-size: var(--step-0); line-height: 1.5; }
.prose { max-inline-size: 65ch; }
```

### Typography
- One family (or system UI stack) + optionally one display face. 2-3 weights. A modular scale (1.2-1.25 for apps, 1.25-1.333 for marketing).
- Fluid type with `clamp()` that includes `rem` so user zoom still works (WCAG 1.4.4). Never `px`-only or pure `vw` font sizes.
- Line height 1.4-1.6 body, 1.1-1.25 headings. `text-wrap: balance` on headings, `text-wrap: pretty` on paragraphs.
- Tabular numbers in tables and counters: `font-variant-numeric: tabular-nums`.
- Mobile: honor Dynamic Type (iOS, `.font(.body)` not fixed sizes) and Android font scale up to 200% (use `sp`, test non-linear scaling).

### Color
- Build palettes in **OKLCH**: fix hue, step lightness evenly (e.g., L 98→20 in 10-12 steps), reduce chroma at the extremes. Same L across hues = similar perceived weight.
- Use **semantic roles**, not raw ramps, in components: `bg`, `surface`, `surface-raised`, `text`, `text-muted`, `border`, `primary`, `on-primary`, `danger`, `warning`, `success`, `info`, `focus`.
- Contrast: text 4.5:1 (3:1 at ≥24px or ≥18.66px bold), UI boundaries/icons/focus 3:1 (1.4.11). "Muted" text still ≥4.5:1.
- Never color alone for state (1.4.1): add icon, text or shape. Red/green pairs also need a lightness difference.
- Data viz: categorical ≤6-8 hues with distinct lightness; sequential = single-hue lightness ramp; diverging = two hues around a neutral midpoint. Direct-label series instead of legends where possible.
- `contrast-color()` returns only black/white with WCAG 2 math; fine for badges on light/dark fills, unreliable on mid-tones.

```css
:root { color-scheme: light dark;
  --brand-h: 255;
  --primary: oklch(52% .17 var(--brand-h));
  --bg: light-dark(oklch(99% .005 var(--brand-h)), oklch(17% .01 var(--brand-h)));
  --surface: light-dark(white, oklch(22% .012 var(--brand-h)));
  --text: light-dark(oklch(22% .02 var(--brand-h)), oklch(94% .01 var(--brand-h)));
  --text-muted: light-dark(oklch(45% .02 var(--brand-h)), oklch(75% .015 var(--brand-h)));
  --primary-hover: color-mix(in oklch, var(--primary), black 12%);
}
:root[data-theme="dark"] { color-scheme: dark; }
:root[data-theme="light"] { color-scheme: light; }
```

### Dark mode and elevation
- Not an inversion: dark gray background (not pure #000 except OLED-true-black option), desaturate brand colors (lower chroma, raise L), avoid pure white text on large areas (use ~94% L).
- Elevation in dark = **lighter surface**, not bigger shadows (M3 uses tonal surface containers: `surfaceContainerLowest…Highest`). In light, keep shadows soft and few (2-3 levels total).
- Respect system setting by default; offer System/Light/Dark override; persist it; no flash of wrong theme (set theme attribute before first paint).

### Shape, borders, icons, imagery
- One radius scale (e.g., 4/8/12/16 + full). Nested radius = outer − padding. Not everything is a pill.
- Borders OR shadows to separate surfaces, not both everywhere. Hairlines at 1px `--border`.
- One icon set (Material Symbols, SF Symbols, Lucide, Phosphor) at consistent size/stroke (20/24). Icons without text need an accessible name and a tooltip on pointer devices. **No emoji as UI icons.**
- Imagery: real product screenshots or purposeful photography; `aspect-ratio` + `object-fit`; meaningful `alt`, `alt=""` for decorative.

### Navigation by app type
| App type | Pattern |
|---|---|
| Mobile, 3-5 peer destinations | Bottom nav bar (Android navigation bar / iOS tab bar); rail at medium, drawer/rail at expanded+ |
| Mobile, 1 main flow | Stack navigation, no tabs; top app bar with back |
| Web app / SaaS | Left sidebar (collapsible) + top bar with search/account; breadcrumbs for deep hierarchies |
| Content site / marketing | Top nav ≤7 items, footer sitemap, search if >~50 pages |
| Dashboards | Overview → drill-down; filters persistent in URL |
- Always: current location visible, back works (browser/system back and predictive back on Android), deep links restore state, URL reflects filters/tabs on web.

### Forms (most common failure area)
- Single column; label above field; mark optional fields (or required ones, consistently); group with `fieldset/legend`.
- Correct input types and autofill: `type="email|tel|url|number"` only for real numbers (not card/zip: use `inputmode="numeric"`), `autocomplete="email|name|street-address|postal-code|cc-number|one-time-code|new-password|current-password"`, `enterkeyhint`. Android: `KeyboardOptions(keyboardType, autoCorrect)` + `ContentType` autofill semantics; iOS `.textContentType(...)`.
- Validate on blur and on submit; after a failed submit, re-validate live as the user fixes. Show errors next to the field + summary at top on long forms, move focus to the first error, link with `aria-describedby`, `aria-invalid`.
- Error copy: what happened + how to fix, in user terms ("Enter a date after 1 Jan 2026"), never "Invalid input". Preserve what the user typed.
- Don't block paste; allow password managers; show-password toggle; passkeys first-class (WCAG 3.3.8: no cognitive test without alternative).
- Don't ask again for data already given (3.3.7 Redundant Entry): prefill, "same as billing".
- Buttons say the outcome ("Create account", "Pay €24"), disabled-submit-until-valid is worse than enabled + clear errors.

### States, feedback, perceived performance
- Every data view: **empty** (why empty + primary action), **loading**, **error** (what + retry + keep stale data), **offline** (cached data marked stale, queued actions), **partial**, **success**.
- Under ~1s: show nothing (or delay spinner ~300-500ms to avoid flash). 1-10s: skeleton matching final layout for content, spinner/progress for actions. >10s: determinate progress + let the user leave.
- Optimistic UI for low-risk, high-success actions (like, reorder, toggle); roll back with a visible message on failure.
- **Undo over confirm**: delete → remove immediately + snackbar "Deleted. Undo" (5-10s, longer if it contains an action: WCAG 2.2.1), actually delete later or soft-delete.
- Destructive irreversible (delete account, drop database): separate danger zone, explain consequences, require typing the name; never default focus on the destructive button.
- Toasts/snackbars: brief, non-blocking, one at a time, `role="status"` (polite) or `alert` for errors; never the only place an error lives; don't cover the primary action or focused element (2.4.11).

### Onboarding and permissions
- Let people use the product before signup when possible; ask for the minimum fields.
- Ask for OS permissions **in context, at the moment of need**, with a pre-prompt explaining value; handle "denied" with a degraded path and a link to settings. Android 13+ notifications need runtime permission; iOS allows provisional notifications.
- Skip/close on every tutorial; prefer contextual tips and good empty states over carousels.

### Motion
- Durations: micro (hover, toggles) 100-150ms; small transitions 200-300ms; large/full-screen 300-500ms. Exits faster than entries.
- Easing: ease-out for entering, ease-in for leaving, standard/emphasized for moving; springs for gesture-driven or expressive UI (M3 Expressive `MotionScheme`, SwiftUI `.spring`/`.snappy`/`.bouncy`).
- Motion must explain (where did it come from/go), never decorate long tasks. No parallax/auto-play loops on content.
- Reduced motion: replace movement with fades/instant changes, don't just remove feedback.

```css
@media (prefers-reduced-motion: no-preference) {
  ::view-transition-old(root), ::view-transition-new(root) { animation-duration: 200ms; }
  .sheet { transition: translate 250ms cubic-bezier(.2,0,0,1); }
}
```
Compose: check `LocalAccessibilityManager`/system animator scale; SwiftUI: `@Environment(\.accessibilityReduceMotion)`.

### Responsive and adaptive
- Breakpoints by content (where the layout breaks), aligned to platform classes: 600 / 840 / 1200 / 1600 dp-or-px are a good default set.
- Components use **container queries**; page shells use media queries.
- Input-aware, not device-aware: `@media (pointer: coarse)` / `(hover: hover)`; hover never the only way to reveal actions.
- Safe areas: `viewport-fit=cover` + `env(safe-area-inset-*)`; Android edge-to-edge is enforced when targeting API 35+: apply `WindowInsets` padding.
- Use `dvh`/`svh` for full-height mobile layouts, not `100vh`.
- Thumb zone: primary actions bottom-reachable on phones (bottom bar, FAB, bottom sheet); destructive actions away from the primary thumb path.
- Tablets/foldables/desktop: list-detail and supporting-pane layouts (Compose `ListDetailPaneScaffold`, SwiftUI `NavigationSplitView`), keyboard shortcuts, pointer hover states, resizable windows.

### Platform: follow which?
- Native app: follow the platform (M3 on Android, HIG on iOS) for navigation, controls, gestures, sheets, typography. Brand lives in color, illustration, voice, and custom content views.
- Cross-platform (Flutter/RN/KMP-Compose): share IA, tokens, copy and flows; adapt navigation chrome, back behavior, dialogs, date pickers, and haptics per platform. Don't ship Material on iOS or iOS-isms on Android.
- Web: neither; use your design system, but borrow platform conventions for mobile web (bottom sheets, safe areas).
- Details: `references/tokens-and-platforms.md`.

### Internationalization
- Plan for **+30-40% text expansion** (up to 200-300% for very short strings), no fixed-width buttons, truncate with tooltip/full view.
- RTL: CSS logical properties (`margin-inline-start`, `inset-inline-end`), `dir="auto"` for user text; Compose `start/end`; SwiftUI leading/trailing. Mirror directional icons (back arrows), not media/clocks/checkmarks.
- Plurals/gender via ICU MessageFormat / `Intl.PluralRules` / Android `plurals` / String Catalogs; never `count + " items"`.
- `Intl.DateTimeFormat`, `Intl.NumberFormat`, `Intl.RelativeTimeFormat`; store UTC, show local; don't hardcode first day of week or 12/24h.

### Microcopy and tone
- Sentence case. Verbs on buttons. Same word for the same thing everywhere. Short, plain, specific; numbers as digits.
- Errors: no blame, no jokes, no "Oops!". Empty states: say what will appear and how to start.
- Write the copy with the screen, not after; lorem ipsum hides layout bugs.

### AI-feature UX (short; more in references)
- Stream output token-by-token with a visible Stop; keep scroll anchored unless the user scrolled up; announce completion for screen readers (not every token).
- Show sources/citations inline next to claims; mark uncertainty; never present generated content as verified fact.
- Human-in-the-loop for actions with side effects: preview diff → approve/edit → undo.
- Errors: keep the prompt, offer retry/edit; partial output stays.
- Prompt box: multiline, Enter to send (Shift+Enter newline) on desktop, explicit send button on touch, suggested starters only in the empty state, attachments visible as chips.

## Security
UI is part of the attack surface; the design must make the safe path the easy one.
- **Phishing-resistant auth UX**: passkeys first; don't train users to type passwords into in-app webviews; show the real domain in auth flows.
- **Clickjacking/overlay**: never make critical confirm buttons appear where a previous tap lands (tap-jacking); Android `filterTouchesWhenObscured` / `setHideOverlayWindows` on sensitive screens; web `frame-ancestors`.
- **Sensitive data on screen**: mask by default with reveal (card numbers, tokens, recovery codes); Android `FLAG_SECURE`/`setRecentsScreenshotEnabled(false)` for secrets; hide content in iOS app switcher snapshot.
- **Error messages** must not leak (no "user exists" on login/reset; same message and timing).
- **Destructive and permission prompts** say exactly what is shared/deleted; no auto-focus on "Allow".
- **Untrusted content** (user names, AI output, markdown) rendered as text; links show destination; confirm before opening external links from AI output.
- **Dark patterns are a legal risk**, not only an ethical one: see `references/a11y-and-law.md` (GDPR consent, DSA Art. 25, UCPD, EU withdrawal button, ROSCA, state auto-renewal laws).

## Performance & quality
- Core Web Vitals (p75 field data): **LCP ≤2.5s, INP ≤200ms, CLS ≤0.1**. Reserve space for images/ads/skeletons (`aspect-ratio`, width/height) to protect CLS; respond to input within one frame (show pressed state immediately) for INP.
- Mobile: 60/120 fps scrolling, no jank on first frame; Android startup with baseline profiles; avoid layout thrash from animating `width/height` (animate `transform`/`opacity`).
- Perceived speed: skeletons, optimistic updates, prefetch on intent (hover/viewport), keep previous data while refetching.
- Fonts: ≤2 families, `font-display: swap` + metric-matched fallback (`size-adjust`), subset.
- Measure: Lighthouse + field RUM (CrUX / web-vitals lib), Android Studio profiler / Macrobenchmark, Xcode Instruments.
- Quality bars: axe/Lighthouse a11y with zero criticals, manual keyboard + screen reader pass, 200% zoom/text, 320 CSS px reflow (1.4.10).

## Testing & tooling
- **Design**: Figma (Variables with modes, Dev Mode for specs/annotations, Code Connect, Figma MCP server for agents). Tokens exported as DTCG JSON → Style Dictionary v5 (or Terrazzo) → CSS variables / Compose / Swift.
- **A11y**: axe-core (`@axe-core/playwright`), Lighthouse, Accessibility Insights, WAVE; Android Accessibility Scanner + Compose `composeTestRule` semantics + Espresso `AccessibilityChecks`; Xcode Accessibility Inspector + `performAccessibilityAudit()` in XCUITest; real VoiceOver/TalkBack/NVDA passes.
- **Visual regression**: Playwright `toHaveScreenshot`, Storybook + Chromatic, Compose Preview screenshot testing, swift-snapshot-testing. Test light/dark, RTL, large text.
- **Contrast**: browser DevTools contrast picker, Polypane/Stark; scripts on token pairs (fail CI if any role pair < target).
- **Research**: 5-user moderated tests per round find most usability issues of one user type (iterate, test again); unmoderated tools for scale. Analytics: define events per task step (`checkout_step_viewed`, `…_error_shown {field, code}`), not just page views.
- **A/B pitfalls**: decide sample size and primary metric up front, no peeking/early stopping without sequential stats, watch guardrail metrics (refunds, cancellations, support tickets), novelty effects, never A/B-test away legal/accessibility requirements.
- Prefer heuristic review (Nielsen's 10) before every PR with UI: status visibility, real-world language, user control/undo, consistency, error prevention, recognition over recall, flexibility (shortcuts), minimalist design, error recovery, help.

## Common mistakes in AI-written code

### Generic "AI-slop" UI checklist (and the fix)
| Smell | Instead |
|---|---|
| Centered hero with purple-blue gradient text and blurred blobs | Left-aligned headline stating the concrete value, a real product screenshot or demo, brand color used sparingly |
| Three identical icon+title+blurb cards ("Fast. Secure. Scalable.") | Show the product doing the thing; vary layout by content importance; specific claims with numbers |
| Emoji as icons / section bullets | One icon set at consistent size, or no icon |
| Light gray text (#999 on white) for "elegance" | Muted text ≥4.5:1; hierarchy via size/weight/spacing |
| Everything `rounded-2xl`/pill + shadow-lg | One radius scale; shadows only for things that float |
| Glassmorphism/blur on content cards | Solid surfaces for content; translucency only for system-like floating chrome |
| Fake testimonials, logos, "10,000+ users", star ratings | Real quotes with permission or nothing (fake reviews are illegal: FTC 2024 rule, EU UCPD) |
| Gradient buttons, glow on hover, bouncing everything | One primary button style, subtle state changes, motion only for meaning |
| Inter at 14px everywhere, no type scale | A defined scale, body ≥16px web, real headings |
| Dashboard of 8 KPI cards with sparkline decoration | 3-5 metrics that drive decisions, with comparison (vs last period, vs target) and drill-down |
| Lorem ipsum / "John Doe" / perfect 3-item data | Real-shaped data: long names, empty, 1, many, errors |
| Dark mode = inverted with pure black + saturated neon | Tonal dark surfaces, desaturated accents |
| Modal on page load (newsletter, cookie + promo stacked) | No interruptions before value; one consent banner with equal choices |
| Every section `py-24 text-center max-w-3xl mx-auto` | Rhythm from content: vary density, alignment and width |

### Code-level mistakes
- `div`/`span` with `onClick` instead of `<button>`/`<a href>`; missing `type="button"` in forms; links that do actions, buttons that navigate.
- `outline: none` without a replacement focus style; focus hidden behind sticky header (fix: `scroll-padding-top`).
- `aria-label` on everything (or wrong roles) instead of native semantics; `role="button"` without key handling.
- Icon-only buttons without accessible names; `alt="image"`.
- Using `placeholder` as label; `type="number"` for phone/card/zip; no `autocomplete`.
- Custom modals without focus trap/return focus/Escape; use `<dialog>.showModal()`.
- `100vh` on mobile; fixed `px` font sizes; `user-scalable=no` / `maximum-scale=1` in viewport meta (blocks zoom).
- Hardcoded hex colors in components; separate dark CSS duplicated; Tailwind arbitrary values (`text-[#6b7280]`) everywhere instead of theme tokens.
- Compose: hardcoded `Color(0xFF…)` instead of `MaterialTheme.colorScheme.*`; `dp` for text; `Modifier.clickable` on tiny icons without `minimumInteractiveComponentSize()`; ignoring `WindowInsets` under edge-to-edge.
- SwiftUI: fixed `.font(.system(size:))` instead of text styles; custom tab bars that fight Liquid Glass; `.glassEffect()` on content/lists; ignoring `accessibilityReduceTransparency`.
- Hallucinated APIs to watch: `@media (prefers-contrast: more)` is real, `prefers-reduced-transparency` is limited support; `color-contrast()` (old name) does not exist, it is `contrast-color()`; DTCG uses `$value`/`$type` (not `value`/`type`), and color values in 2025.10 are objects (`colorSpace`, `components`), strings are legacy.
- Spinners for 200ms operations; full-page spinner replacing already-visible data on refetch.
- "Are you sure?" on undoable actions; red "Delete" as the default focused button.

## Before you ship
- [ ] Every screen has empty/loading/error/offline/success states with real copy
- [ ] Tokens only (no raw colors/sizes in components); light + dark both checked
- [ ] Contrast: text 4.5:1, large 3:1, UI/focus 3:1; no color-only meaning
- [ ] Keyboard: all actions reachable, visible focus not obscured, logical order, Escape closes overlays
- [ ] Screen reader: names, roles, states, headings, live regions for async results
- [ ] Targets ≥24px (web AA), ≥44pt iOS, ≥48dp Android; drag actions have a tap alternative
- [ ] 200% text / Dynamic Type / font scale 200%, 320px reflow, RTL, +40% strings
- [ ] Reduced motion and reduced transparency respected
- [ ] Forms: labels, types, autocomplete, paste allowed, errors on blur + summary, no redundant entry, passkey/password-manager friendly
- [ ] Destructive actions have undo or explicit consequence + typed confirm
- [ ] Consent/cancellation flows are symmetric (reject as easy as accept; cancel as easy as signup; EU withdrawal button)
- [ ] CWV budget met (LCP, INP, CLS); no layout shift from late content
- [ ] AI-slop checklist passed; no fake social proof
- [ ] Top task tested with ≥1 real person (ideally 5), events instrumented

## Sources
- https://www.w3.org/TR/WCAG22/ - WCAG 2.2 criteria and levels (2.4.11 AA, 2.4.13 AAA, 2.5.7, 2.5.8, 3.2.6, 3.3.7, 3.3.8) - 2026-10-09
- https://www.w3.org/WAI/news/2025-10-21/wcag22-iso - WCAG 2.2 = ISO/IEC 40500:2025 - 2026-10-09
- https://www.w3.org/TR/wcag-3.0/ - WCAG 3 WD 2026-09-10, contrast algorithm TBD, no APCA - 2026-10-09
- https://eur-lex.europa.eu/eli/dir/2019/882/oj - EAA application 2025-06-28, transition to 2030, micro-enterprise exemption - 2026-10-09
- https://commission.europa.eu/strategy-and-policy/policies/justice-and-fundamental-rights/disability/union-equality-strategy-rights-persons-disabilities-2021-2030/european-accessibility-act_en - EAA scope - 2026-10-09
- https://eur-lex.europa.eu/eli/dir/2023/2673/oj - withdrawal function (CRD Art. 11a) from 2026-06-19 - 2026-10-09
- https://www.europarl.europa.eu/legislative-train/theme-protecting-our-democracy-upholding-our-values/file-digital-fairness-act - DFA planned Q4 2026, no proposal yet - 2026-10-09
- https://www.jonesday.com/en/insights/2026/05/ftc-revives-clicktocancel-rule-new-risks-for-subscription-businesses - FTC ANPRM March 2026, ROSCA/state laws (lead; law firm) - 2026-10-09
- https://www.wilmerhale.com/en/insights/client-alerts/20250801-eighth-circuit-vacates-the-ftcs-click-to-cancel-rule-but-federal-and-state-regulators-likely-to-remain-active - 8th Circuit vacatur 2025-07-08 - 2026-10-09
- https://btlaw.com/en/insights/alerts/2025/california-expands-automatic-renewal-law-new-requirements-now-in-effect - CA AB 2863 effective 2025-07-01 - 2026-10-09
- https://www.w3.org/community/design-tokens/2025/10/28/design-tokens-specification-reaches-first-stable-version/ - DTCG 2025.10 stable - 2026-10-09
- https://www.designtokens.org/tr/2025.10/format/ - DTCG token shapes, aliases, $extends, types, media type - 2026-10-09
- https://www.designtokens.org/tr/2025.10/resolver/ - DTCG resolver (sets, modifiers, contexts) - 2026-10-09
- https://www.reedsmith.com/articles/doj-extends-digital-accessibility-compliance-dates-under-title-ii-of-the-ada/ - DOJ IFR 2026-04-20, Title II dates 2027/2028 (law firm; DOJ Federal Register not opened) - 2026-10-09
- https://github.com/style-dictionary/style-dictionary/releases - SD v5 DTCG 2025.10 partial support (5.3.0 colors, 5.4.0 dimensions) - 2026-10-09
- https://www.federalregister.gov/documents/2026/03/13/2026-04952/rule-concerning-the-use-of-prenotification-negative-option-plans - FTC ANPRM 2026-03-13 - 2026-10-09
- https://www.federalregister.gov/documents/2026/04/20/2026-07663/ - DOJ IFR: Title II dates 2027-04-26 / 2028-04-26 - 2026-10-09
- https://www.etsi.org/deliver/etsi_en/301500_301599/301549/ - EN 301 549 v4.1.1 published 2026-09-02 - 2026-10-09
- https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes - 5 width classes, `supportLargeAndXLargeWidth` - 2026-10-09
- https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act - AI Act Art. 50 from 2026-08-02, marking grace to 2026-12-02 - 2026-10-09
- https://figma.obra.studio/design-tokens-community-group-w3c-release/ - Figma native DTCG import/export announcement (third-party; ship date unverified) - 2026-10-09
- https://developer.android.com/jetpack/androidx/releases/compose-material3 - material3 1.4 stable, 1.5.0-beta01, expressive APIs promotion - 2026-10-09
- https://android-developers.googleblog.com/2025/10/jetpack-windowmanager-15-is-stable.html - large/extra-large size classes - 2026-10-09
- https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored - Android 17 orientation/resizability ignored on ≥600dp - 2026-10-09
- https://www.macrumors.com/2026/06/10/how-liquid-glass-is-changing-in-ios-27/ - iOS 27 Liquid Glass changes (press only; not confirmed on Apple sources) - 2026-10-09
- https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Values/color_value/contrast-color - contrast-color() Baseline 2026, black/white only - 2026-10-09
- https://developer.chrome.com/blog/new-in-web-ui-io26 - current web UI features (closedby, scroll-state, corner-shape, view transitions) - 2026-10-09
- https://github.com/web-platform-tests/interop/blob/main/2026/README.md - Interop 2026 focus areas - 2026-10-09
- https://developer.mozilla.org/en-US/docs/Web/API/Document/startViewTransition - view transitions - 2026-10-09
- https://www.nngroup.com/articles/ten-usability-heuristics/ - Nielsen heuristics - 2026-10-09
- https://www.nngroup.com/articles/why-you-only-need-to-test-with-5-users/ - 5-user testing - 2026-10-09
- https://web.dev/articles/vitals - Core Web Vitals thresholds - 2026-10-09
- https://m3.material.io/blog/m3-expressive-motion-theming - M3 Expressive motion springs - 2026-10-09
- https://developer.apple.com/design/human-interface-guidelines/ - HIG (44pt targets, Liquid Glass materials) - 2026-10-09
