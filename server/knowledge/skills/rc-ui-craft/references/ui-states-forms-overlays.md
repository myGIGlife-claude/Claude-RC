# Forms, states, overlays, keyboard, type and layout (details for rc-ui-craft)

Distilled 2026-11-25 from MIT-licensed skills by jakubkrehel, raphaelsalaja, pbakaus (Apache-2.0), ibelick, addyosmani and vercel-labs. Paraphrased; numbers are starting points.

## Forms
- Labels above fields; a label click focuses its control; checkbox and radio groups use fieldset + legend. Required = visible asterisk (aria-hidden) plus native `required`.
- Show rules before input and tie them with `aria-describedby`. Set `type`, `inputmode`, `autocomplete`; `type=number` only for steppers (card numbers: `inputmode=numeric`). Never block paste, password managers or one-time-code autofill.
- Inputs at 16px or larger, or iOS zooms. Soft length limit with a live count past about 75%. Textarea `field-sizing: content`, min about 3 lines, max about 10.
- Validate on submit and keep submit enabled. `aria-invalid` plus `aria-describedby`, focus the first invalid field, error summary at 3+ errors, page title prefixed "Error: " until fixed. Enter submits; ignore Enter during IME composition.
- 2 to 3 options: radios; over about 15: searchable combobox; unavailable options stay visible and disabled. Read-only values use `readonly`, not `disabled`.

## States (design every one or rule it out in a sentence)
- Three different empties: first run (create or import), cleared (calm), filtered to zero (name the query, offer Clear filters). Keep the frame when a region is empty or failed.
- Retry automatically only on network failure, 5xx, 429 (up to 3 tries, jittered backoff from 1 s, honor Retry-After); never other 4xx; writes only with an idempotency key.
- A failed count shows "unavailable", never 0. Saves send the loaded version; on conflict offer Reload and Overwrite, never a silent merge. Session timeouts warn at least 20 s ahead with one extend action.
- Loading: spinner for about 2 to 10 s; known-size work shows "3 of 50" with Cancel; fail a no-progress request after about 30 s. Long lists end with a marker; a failed next page keeps loaded items and shows Retry in place.
- Stress data: long email, count of 1 ("1 members" is a bug, use plural rules), 1,284 counts via `Intl.NumberFormat`, emoji names, single-letter names, initials via `Intl.Segmenter`. Truncate by field: wrap names, end-truncate metadata, middle-truncate file names, never truncate numbers. Fixes: `flex-shrink:0` on avatars, `min-width:0` on flex children, `overflow-wrap:anywhere` for emails. Budget 30 to 40% text growth for translation; test RTL, CJK, 320px, 200% zoom.
- Each HTTP failure gets its own action text (400, 401, 403, 404, 429, 500). Prevent double submit. An interrupted drag (pointercancel, blur, lost capture) must clear drag state.

## Overlays
- Blocking overlays: `<dialog>` with `showModal()`; menus, tooltips, toasts: `popover`. Focus enters, returns to the trigger on close; labelled with `aria-labelledby`; visible Close.
- No stacked dialogs, none on page load. Footer: Cancel left, primary right. Destructive actions are never autofocused; rare ones ask to type the name. Dirty forms ignore outside clicks.
- Scroll lock with `overscroll-behavior: contain` and `html:has(dialog:modal){overflow:hidden}`; never `position:fixed` on body.
- Anchored popovers flip, shift, then cap height; 8px from trigger and viewport edge. Submenus one level deep, hover triangle keeps them open about 300 ms.
- Tooltips: never the only copy of a limit or price, open on focus too, none on disabled buttons; the first waits, neighbors open instantly. Sheets below 600px width. Only blocking overlays dim the page (25 to 50%).
- Toasts: max 3 visible, 4 to 6 s, pause on hover, focus and hidden tab; toasts with an action stay until dismissed; inset 24px (16px small screens). Use `role=status` for polite updates (render the region empty first), `role=alert` only for urgent errors.

## Keyboard and focus
- Test by pressing keys in the running page, not by reading markup. Tab order equals visual order; no positive tabindex; DOM order matches `row-reverse` and grid placement.
- Every stop has a visible `:focus-visible` indicator (custom rings 3:1 against neighbors); sticky chrome must not fully cover the focused item (add `scroll-padding`/`scroll-margin`). Skip link first.
- Composite widgets (tabs, menus, grids): one Tab stop, arrow keys inside. Escape closes only the topmost layer. Focus survives deletion (move to the next row); after a route change focus goes to the page heading (`tabindex=-1`). Scroll regions: `tabindex=0`, `role=region`, `aria-label`.
- Targets: WCAG 2.2 floor 24x24 CSS px; design 44px touch, 40px desktop; enlarged hit areas must not overlap. Every drag has a single-pointer alternative. Never `user-scalable=no`. Auto-updating content has a pause control.
- Findings in audits cite a WCAG 2.2 criterion or a concrete failing task; the rest are recommendations. A Lighthouse 100 is not conformance; automated tools find roughly 30 to 50% of issues.

## Type
- Unitless line-height: display 1.1, headings 1.2 to 1.3, body 1.5 to 1.6 (3+ line paragraphs at least 1.4). Weight 400+ under 18px; 100 to 300 only at 28px and up.
- Letter-spacing in em: large headings -0.01 to -0.02em; small uppercase about +0.05em. Measure 45 to 75 characters. `text-wrap: balance` for headings, `pretty` for paragraphs. `tabular-nums` on timers, counters, prices and numeric columns.
- Body 16px in rem, UI 14px, captions 13px, rarely under 12. At most 3 families in an app; load only weights used; metric-matched fallbacks. Typographic punctuation in rendered text. Light text on dark: slightly more leading and tracking.

## Layout
- Space before lines: gap between groups at least 2x the gap inside a group. Squint test: primary, secondary and grouping must still read blurred. Group by proximity before adding boxes; never cards inside cards.
- 4px base scale (8px alone misses middle steps). Logical properties (`margin-inline-start`, `text-align:start`). Grid `minmax(0,1fr)`; flex children `min-width:0`.
- `100vw` becomes `100%`; full-height panes use `100dvh`; `env(safe-area-inset-*)` is zero without `viewport-fit=cover`. Reusable components use `@container`, not viewport media queries. Pick breakpoints where content breaks. Use `(pointer: coarse)` and `(hover: hover)`; never rely on hover for function.
- Elevation by shadow, structure by border; stack of three neutral shadow tiers (small controls, cards and popovers, hero or modal), one tier per state, never tinted. Border gradients: 1px (2px for hero or active), 135 or 160 degrees, most stops under 40% alpha, no rainbow. Squint-test each section: strip the copy and the structure should still say what it is.

## Audit scoring and workflow
- Score five dimensions 0 to 4 (accessibility, performance, theming, responsive, anti-patterns), total out of 20: 18 to 20 excellent, 14 to 17 good, 10 to 13 acceptable, 6 to 9 poor. Severity: P0 blocks the task, P1 is a WCAG AA violation, P2 has a workaround, P3 polish. For design critique, score Nielsen's 10 heuristics 0 to 4.
- Refinement keeps the existing look; redesign replaces it. Never smuggle a redesign into a polish. Polish order: blocked tasks, missing states, flow and hierarchy, visual and motion, cleanup.
- Read-only advisors: every finding needs evidence (file:line), user impact, effort, risk, confidence, plus a specific fix; reject by-design behavior; cap the report (top 3 to 6) and wait for the user to choose.
- Build only the ask: no extra badges, settings or icons; local state before a store; abstract at two callers; "remove" deletes code, styles, variables and routes. Remove debug logs before saying done.
