---
name: rc-ui-craft
kind: process
description: "Craft playbook for polished app and web UI: audit loop, color roles and contrast, motion timing and gestures, animation and page performance, 3D/WebGL budgets, Remotion video, UI debugging, anti-slop taste. Load with rc-ui-ux-design for any screen, animation, theme, shader or video work."
---
# UI craft: build, polish, debug and prove it (process, not facts)

> Distilled in November 2026 from a read of the public UI Skills directory (ui-skills.com, 843 entries) and the repos behind its most useful entries. Written in our own words; numbers are starting points, not laws. `rc-ui-ux-design` holds the base rules (layout, forms, WCAG, platforms); this skill is how to apply them well and verify the result. Where a rule says "measure", measure.

## How to use this skill

1. Pick the section for the job (audit, color, motion, performance, 3D, video, debug, taste). Do not read them all.
2. Look at what the project already has (tokens, theme, motion helpers) and extend it. Never bolt on a second system.
3. Change one thing, check it in a real viewport (phone width first), then the next.
4. Say what you could not check (real device, slow phone, screen reader) instead of implying it.

## Do this

### 1. Audit loop (any UI change)
- Scope the review to the change, not the whole screen: read the diff, then the one hop of callers and shared tokens it touches. Sort each finding as introduced, regression or already there; report at most a few already-there ones.
- Read every removed line. A deleted `aria-label`, focus style or reduced-motion rule is the usual hidden regression.
- Each finding carries: where (file:line), what the user sees, how bad, fix size, how sure you are. Drop findings the code shows are deliberate.
- Fix order: broken for someone (missing label, no keyboard, unreadable) before ugly, ugly before polish.
- Stress it: empty, one item, 500 items, longest name, offline, slow, error, RTL, large text. A screen that only works with demo data is not done.
- Render every state of a component (default, hover, focus, pressed, disabled, loading, error) on one scratch page and look at them side by side.
- Measure before and after, one change at a time; revert a change that did not move the number.

### 2. Color
- Work in roles, not swatches: surface, text, action, border, status, data. Every ramp step has a named job; do not generate steps nothing uses. Components use semantic tokens only, never raw palette values.
- Build new palettes in OKLCH; lower chroma near white and black. Gradients: Oklab by default, OKLCH when a two-hue blend goes grey. Declare an sRGB value first, then a wider-gamut value inside `@media (color-gamut: p3)`.
- Contrast on the rendered background (after any transparency), not the page color: body text 4.5:1, large text (24px, or 18.66px bold) and controls, icons, focus rings, field edges 3:1. Disabled controls and decorative dividers are exempt. APCA is a second opinion and never overrides a failing WCAG ratio.
- Text over an image needs a scrim; measure at the lightest point under the text.
- Color is never the only signal (add icon, text or pattern); check with a color-blindness preview.
- Dark mode is its own design: surfaces get lighter as they rise, text is off-white (about #ededed, not pure white), accents are retuned. Check light and dark separately.
- Pick a color dosage before editing: one strong color owns one deliberate region; the rest stays quiet. Keep confirmed brand colors.
- Token drift audits: match by purpose first, then value (two equal hexes with different jobs are not a match); label each row exact, close, ambiguous or gap. Many gaps usually means the token list is wrong, not the code.

### 3. Motion
- Gate first: keyboard-triggered or used 100+ times a day means no animation. Motion explains a change or gives feedback; if it does neither, remove it.
- Timing starting points: press feedback 100 to 160 ms; tooltips about 150; dropdowns 150 to 250; modals, sheets and drawers 200 to 450; UI stays under 300 ms unless it is a large surface. Exits are shorter than enters and simpler.
- Easing: ease-out for entering and exiting, never ease-in on UI, linear only for loops and progress. A strong ease-out such as `cubic-bezier(0.23, 1, 0.32, 1)` feels responsive. Springs: no bounce for routine UI; light bounce only on a gesture release.
- Never start from scale 0; start near 0.9 to 0.97 with opacity 0. Press scale about 0.96 to 0.97. Popovers grow from the trigger; modals stay centered.
- One entrance per container: do not animate a parent and also stagger its children. Stagger only product reveals (30 to 80 ms between items, whole group under about 300 ms), never routine or repeated UI.
- Gestures: while the finger is down, follow it 1:1 (no easing). On release, use a spring seeded with the release velocity. Dismiss on distance (about 40 to 50% of travel) or velocity, so a fast flick counts. Rubber-band at the edges. Hit targets 44px; set `touch-action` on the drag axis; test on a real phone.
- Everything must survive interruption and re-trigger (tap during an animation, reverse mid-way). Prefer springs and transitions over fixed keyframes for anything interactive.
- Reduced motion: remove parallax, large slides and scaling, spinning, scroll hijacking and auto-carousels; replace movement with a fade of 200 ms or less; keep opacity, color and focus transitions. A global reset should use 0.01ms (not 0s) so `animationend` still fires. JS-driven motion needs its own `matchMedia` check. Auto-moving content longer than 5 s needs a pause control.
- Hover effects go inside `@media (hover: hover) and (pointer: fine)`.
- Compose, SwiftUI and Android: the same ideas hold; use the platform's spring and transition APIs, honor the system "reduce motion" setting, and test on a mid-range phone.

### 4. Performance (UI and pages)
- Measure on a production build, on a throttled mid-range device, one defined interaction at a time. Field data (p75) beats one lab run. Do not claim a field gain right after a change.
- Core Web Vitals "good" at p75: LCP 2.5 s or less, INP 200 ms or less, CLS 0.1 or less. TBT is not INP.
- Page budgets as guardrails: whole page about 1.5 MB, compressed JS 300 KB, CSS 100 KB, above-the-fold images 500 KB, fonts 100 KB, third-party 200 KB. TTFB under about 800 ms. Hashed assets cache for a year (immutable); HTML does not.
- LCP image is in the HTML, not injected by script, with high fetch priority; do not lazy-load it. Lazy-load below the fold only. Lists past about 100 items: virtualize or `content-visibility: auto`.
- Frame budget 16.7 ms at 60 Hz (8 ms at 120 Hz). Animate `transform` and `opacity` only; width, height, margin and padding force layout, shadows and radius force paint. Never `transition: all`. Do not drive animation with per-frame state updates; write to refs or motion values. Animated blur stays small (a few px; under 20 px at the very most). `will-change` only after you see a problem, never on big containers or `*`. Batch layout reads before writes.
- React: remove waterfalls first (start independent promises together, await late), then bundle size (direct imports, no barrel files, dynamic-import heavy parts), then re-renders. Move ticking state (timers, scroll, input) into a small child. Stable keys, never indexes, when order can change. Memoize only where the profiler shows cost; a high render count is evidence, not a defect.
- React Native and long lists: virtualized list (FlashList or FlatList), memoized rows, stable callbacks, no inline style objects; measure release builds only.
- Android Compose: stable keys in lazy lists, avoid reading state in composition that only the draw phase needs, check Baseline Profiles and R8 (see `rc-android`).

### 5. 3D and WebGL
- Is 3D worth it? It must carry meaning (product, spatial data) and have a static fallback. Decorative WebGL must not block input, scrolling or first paint, and must pause off-screen and in reduced-motion.
- Read `renderer.info` after rendering: draw calls, triangles, textures, geometries. Merge static meshes, use instancing for repeats, one material per many objects.
- Cap pixel ratio at 2 (cost grows with its square). Keep shadow maps small and freeze static ones. Compile shaders before the first frame to avoid a stall.
- Assets: compress glTF (Draco or meshopt for geometry, KTX2 for textures); a 2048 px RGBA texture with mipmaps is roughly 22 MB of GPU memory, so size textures to what is visible.
- Dispose of geometry, materials and textures yourself when removing objects (walk the whole subtree); avoid allocating inside the frame loop; stop the loop when the canvas is hidden.
- React Three Fiber: keep scene state out of React state that changes per frame; mutate refs in the frame callback.

### 6. Video (code-made, Remotion and similar)
- Plan scenes on a timeline first: length, one idea per scene, transitions matched to the motion's speed so cuts do not jolt. Keep text on screen long enough to read twice.
- Everything driven by the frame number, never wall-clock time or random without a seed, so renders are repeatable. Preview in the studio, render with the CLI, and watch the real output file.
- Captions: burned-in or overlay, high contrast, safe margins, never over the faces or key UI. Audio: mix and normalize; check sync at start, middle, end.
- Product films: use real screens at device sizes, not placeholders; cursor and click moments should be believable and slow enough to follow.
- Images and video generated by AI are checked by eye before use; keep the prompt, size and file name in the task notes.

### 7. Debugging UI
- Build a fast, repeatable way to see the bug first (a command or script that fails on the exact symptom). For a flaky bug, raise the repro rate (loop it) before theorizing.
- Write 3 to 5 falsifiable guesses, each with the result you expect; test one variable at a time; tag debug logs with a unique prefix so you can remove them with grep. Shrink the repro until every remaining piece matters.
- Layout bugs: inspect computed styles and box sizes, not the CSS you wrote; check overflow, min-width in flex, safe areas, the viewport meta and large text. Check at 320, 375, 768 and a foldable's inner width.
- Jank: record the interaction, find dropped frames, name the work in them (layout, paint, script), fix, record again.
- Release-only crashes (R8, minify): suspect missing keep rules first; reproduce on the release build.
- After the fix, add a test or a note that would catch it again.

### 8. Taste: avoid generic "AI" UI
- Decide one direction in a sentence (purpose, audience, feel) before choosing anything. If a choice would suit any product in the sector, justify it or change it.
- Restraint: one accent color, one gray family, one radius scale, one spacing scale; nested radius = outer radius minus inset; padding is never smaller than the gap between its children; off-by-1 to 4 px is a bug, bigger is intent.
- Elevation by shadow, structure by border; keep a transparent 1px border under shadow rings so forced-colors mode keeps edges. Outline content images 1px at 10% black or white. Icons use `currentColor`; stroke weight follows text weight.
- No emoji as UI chrome, no gradient text or rainbow borders by default, no centered-everything hero, no identical three-card rows. Real content and real data beat lorem ipsum.
- Copy: short plain labels, verbs on buttons, no filler ("seamless", "unlock", "supercharge"). Edit by removing filler while keeping the author's voice and facts.
- Delight is small and earned (a good empty state, a satisfying press), never in the way of speed.

## Common mistakes

- Polishing before the states exist (loading, empty, error).
- Animating layout properties, or animating everything because it is available.
- Checking only the light theme, only a flagship phone, or only the happy path.
- Reporting a performance win from one lab run, a dev build or a warm cache.
- Raw palette values in components; a second token system next to the first.
- Contrast measured on the page color instead of the actual background.
- Trusting "it compiles" or a passing build as proof that the screen works.

## Before you ship

- [ ] Every state rendered and looked at (default, focus, pressed, disabled, loading, empty, error).
- [ ] Phone width and one large-text setting checked; light and dark checked.
- [ ] Contrast numbers measured; color is never the only signal; focus visible; targets at least 44px.
- [ ] Reduced motion honored; no layout or paint animation per frame.
- [ ] A before and after number for anything called a performance fix.
- [ ] No console errors; no leftover debug logs.
- [ ] Anything you could not verify (real device, screen reader, slow network) is said out loud.

## Sources

Read 2026-11-25; facts reduced to starting numbers and rewritten. Individual skills are the work of their authors; use their own repos for the full text.
- https://www.ui-skills.com/ - directory of 843 skills for design engineers (listing pages for color, motion, performance, 3D, Three.js, video, Remotion, debugging, testing, accessibility, taste).
- Entries read most closely: jakubkrehel (better-motion, better-colors, better-ui, interface-review), emilkowalski (animate, animation-performance, gestures, choreograph), raphaelsalaja (color, alignment), pbakaus (colorize, optimize), addyosmani (core-web-vitals, performance), mattpocock (diagnosing-bugs, tdd), iart-ai (accessible-animation, 60fps-animation), cloudai-x (threejs-performance), vercel-labs (react-best-practices, react-native-skills), callstackincubator (react-native-best-practices), elithrar (web-perf, anti-slop), millionco (performance), remotion-dev (Remotion skills).
- https://web.dev/articles/vitals - Core Web Vitals thresholds.
- https://www.w3.org/TR/WCAG22/ - contrast and target-size criteria.
