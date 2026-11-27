# Motion, gestures and animation libraries (details for rc-ui-craft)

Distilled 2026-11-25 from MIT-licensed skills by jakubkrehel, emilkowalski, raphaelsalaja, greensock, ibelick and pbakaus (Apache-2.0). Paraphrased; tune on a real device.

## Curves and tokens
- Strong ease-out `cubic-bezier(0.23, 1, 0.32, 1)` for enter and exit; `cubic-bezier(0.77, 0, 0.175, 1)` for on-screen movement; drawer/sheet `cubic-bezier(0.32, 0.72, 0, 1)` at about 500 ms (small floating drawers about 200 ms).
- Tool ladder: CSS transition, then `@starting-style`, then CSS animation, then WAAPI, then a library. Keyframes only for run-once sequences; interactive state uses transitions or springs. Main-thread JS animation drops frames when the page is busy: prefer CSS or WAAPI for critical motion.
- Project motion tokens win over these numbers. No user-started motion past 300 ms except panels and drawers (400 ms) and deliberate holds.

## Numbers
- Springs for clicks: bounce 0 (0.3 small swaps, 0.4 tab indicators, about 0.45 drawers). Color and opacity never spring. Drag release: stiffness about 500, damping about 30.
- Scale: tooltip and dropdown enter from 0.96 to 0.97; modal and panel from 0.98 to 0.99; press to 0.96 to 0.98 over 150 ms; disabled never scales; squash and stretch within 0.95 to 1.05.
- Travel: text swap 4px, page 8px, staged entrance 12px with 4px blur, exits about -12px over 150 ms; sheets up to about 70% of their own size. Quiet variants shorten movement to 10 to 20px.
- Stagger 30 to 80 ms between items (about 30 ms common), whole group under about 300 ms; items after the fifth share one delay; vary treatment, never uniform on lists; never on routine UI. Under `mode="wait"` halve each duration; use `animation-fill-mode: both` on delayed animations.
- Hold-to-confirm: linear fill while held (about 2 s), fast ease-out reset on release. Blur of 2px can hide an imperfect crossfade; keep blur under 20px (8px or less when animated on large areas); motion blur only on untracked motion, never on text being read.
- Name animated properties; never `transition: all`; suppress transitions on theme switch; `will-change` only after you see a 1 to 2px shift, naming the property.

## Gestures
- Drag starts after about 3px, locks axis after about 10px, preview at 0.8 opacity. While down: follow 1:1 with direct style writes, no spring, no React state per move. On release: spring seeded with release velocity. Fling dismiss when velocity over about 0.11 px/ms in the last 100 ms, or distance over 40 to 50% of travel. Snap target = position + projected velocity with deceleration about 0.998. Rubber-band edges (elasticity about 0.2); dismiss along the axis the element entered; bounce only after a drag release, not after a close button.
- Touch: 44px targets, `touch-action` on the drag axis, never `touch-action:none` on interactive content, test on a real phone. A custom slider drag must finish, and a swipe across it must still scroll the page.

## Choreography
- Cast each moving element as lead, support, decoration or static; two leads is a hierarchy problem. One entrance per container (never animate a parent and also stagger its children). Pair relationships: together, overlap, sequence, carry. Sub-animations share one duration and easing family.
- Leaving elements leave layout at once (`AnimatePresence mode="popLayout"`), exits are simultaneous, shorter, and mirror the entry direction. Test re-triggering mid-sequence and interruption.
- Hover: move a child, not the hovered element, to avoid edge flicker. Gate hover behind `(hover: hover) and (pointer: fine)`; add `-webkit-tap-highlight-color: transparent` on custom pressed states.

## Reduced motion
- Tiered: remove parallax, big slides and scales, spins, scroll hijack, auto-carousels; swap movement for a fade of 200 ms or less; keep opacity, color and focus changes. Never a blanket 0s kill that removes useful feedback (0.01ms keeps `animationend`). JS-driven motion needs a `matchMedia` check; GSAP: `gsap.matchMedia()` and zero duration. No more than 3 flashes per second.

## GSAP
- Defaults duration 0.5 s, ease power1.out. Prefer `autoAlpha` over `opacity` (hidden elements stop taking clicks). Second `from()` on the same property needs `immediateRender:false`. Use transform aliases; never animate width, height, top or left. Timelines: position parameter ("<", "<0.2", labels), duration comes from children, prefer timelines to chained delays. ScrollTrigger: never on child tweens, `scrub` wins over `toggleActions`, horizontal scroll via `containerAnimation` needs ease none, kill triggers on route changes, remove `markers:true`, `refresh()` only when layout changes (debounced). Perf: `gsap.quickTo` for pointer followers; pause or kill off-screen animations. React: `useGSAP` with a scope ref; `contextSafe` for handlers; never during SSR.

## Libraries worth reaching for (check package.json first; do not churn an installed competitor)
- base-ui or similar primitives, cmdk (command menus), Sonner (toasts), Vaul (drawers and sheets), input-otp, Motion (springs, layout), NumberFlow (numbers), dnd kit, Virtuoso (virtualization). Hand-built toasts, div dropdowns with manual focus and 1,000+ rows rendered directly are smells.

## SVG, canvas and decorative effects
- Decorative WebGL or canvas: poster image first, paused off-screen and under reduced motion, DPR capped at 1.5 to 2, headline and CTA readable within 3 s, contrast kept over moving backgrounds, handle context loss and resize. One dominant visual lane per page.
- SVG draw-on, morph and motion paths: `transform-box: fill-box` for part origins; animate a wrapper div rather than the SVG element in React.
