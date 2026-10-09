# Components, states and flows (as of 2026-10)

## Buttons
- Hierarchy per view: **one** primary (filled), secondary (tonal/outlined), tertiary (text). Destructive = danger color, never primary-by-default.
- Label = verb + object ("Save changes"). Loading: keep width, show inline spinner, disable repeat submit, keep label readable.
- Disabled buttons fail to explain why; prefer enabled + validation message, or a hint next to it.
- Icon-only: accessible name + tooltip on pointer devices; ≥24px web (AA), 44pt iOS, 48dp Android.

## Inputs and selects
- Text field: label, optional helper, error slot that reserves space (no layout jump), character counter only near the limit.
- ≤5 options → radio/segmented; 6-15 → select; long or searchable → combobox with type-ahead. Multi-select → checkboxes or chips, not ctrl-click lists.
- Dates: native `<input type="date">` / platform pickers for near dates; free-text with format hint for birthdates (spinners are slow).
- Toggle switches act immediately; checkboxes need a Save. Don't mix in one form.

## Dialogs vs sheets vs popovers
| Need | Use |
|---|---|
| Blocking decision, short | Dialog (`<dialog>.showModal()`, M3 AlertDialog, SwiftUI `.alert`/`.confirmationDialog`) |
| Task with several inputs on mobile | Bottom sheet / full-screen dialog (SwiftUI `.sheet` with detents, M3 `ModalBottomSheet`) |
| Contextual non-blocking info/actions | Popover / menu (`popover` attribute, anchor positioning, M3 `DropdownMenu`) |
| Hint for an icon | Tooltip (hover+focus, dismissable with Esc, persistent while hovered: WCAG 1.4.13); never essential info |
- Modal rules: focus moves in, is trapped, returns to the trigger on close, Escape closes, background inert. Native `<dialog>` does this.

## Tabs vs segmented control vs accordion
- Tabs: switch between peer views of the same object; keep state; reflect in URL on web. Arrow-key navigation (ARIA tabs pattern).
- Segmented control: 2-5 mutually exclusive options that filter/modes of one view.
- Accordion: long pages of optional detail (FAQs, settings groups); don't hide required fields in collapsed sections. `<details>/<summary>` is native; `name` attribute gives exclusive accordions.

## Lists, cards, tables
- Lists for scanning homogenous items; cards only when items are heterogeneous or visual (photos). Don't put cards in cards.
- Whole-row tap target with one primary action; secondary actions in an overflow menu or swipe (with a visible alternative).
- Tables: right-align numbers, tabular numerals, sticky header, sortable columns marked with `aria-sort`, column chooser for wide data, horizontal scroll container with visible cue on mobile or switch to stacked rows. Row selection with checkboxes + bulk action bar.
- Pagination when users need position/sharing (search results, admin tables); "Load more" for feeds where footers matter; infinite scroll only for endless feeds with no footer, and restore scroll position on back.

## Search, filter, sort
- Search field visible on content-heavy apps (not hidden behind an icon on desktop). Show recent searches and suggestions; tolerate typos; highlight matches.
- Filters: show active filters as removable chips + "Clear all"; counts per facet; apply instantly on desktop, batch-apply ("Show 42 results") in mobile sheets.
- Zero results: say what was searched, suggest removing filters, offer alternatives.
- Persist search/filter/sort in the URL (web) and in saved state (mobile).

## Dashboards
- Start from decisions: which question does each widget answer? 3-5 headline metrics with comparison and trend; details below.
- Consistent time range control that applies to all widgets; timezone shown.
- Charts: bar for comparison, line for trend, avoid pie beyond 3-4 slices, no 3D, start bar axes at zero, label directly, accessible table alternative.
- Empty/insufficient-data states per widget.

## Settings screens
- Group by user mental model (Account, Notifications, Privacy, Appearance), not by code module. Search for >20 settings.
- Auto-save toggles with confirmation feedback; explicit Save only for multi-field forms.
- Show current value in the row ("Theme: System"). Danger zone at the bottom.

## Notifications and toasts
- In-app: snackbar (transient, optional action), banner (persistent until resolved), inline message (field/section level), badge (count).
- Push: only for things the user asked for or time-critical; granular categories (Android channels, iOS interruption levels); deep-link to the exact item.
- Toast rules: one at a time, 4-10s, pause on hover/focus, action reachable by keyboard, `role="status"`.

## Drag and drop
- Always a non-drag alternative (WCAG 2.5.7): move up/down buttons, "Move to…" menu, keyboard pick-up (Space) + arrows.
- Show a drag handle, a placeholder where it will drop, auto-scroll near edges, and announce moves to screen readers.

## Bottom navigation
- 3-5 destinations, icon + label always (no label-less icons), current item clearly indicated, re-tap scrolls to top/resets stack.
- Hide on scroll only if it reappears on any upward scroll; never hide when it holds the primary action.

## State matrix (fill for every view)
| State | Show |
|---|---|
| First use / empty | Explanation + primary action + optional example/template |
| Loading (first) | Skeleton of final layout (content) or progress (action) |
| Loading (refresh) | Keep data, subtle indicator (pull-to-refresh, top progress bar) |
| Partial | What loaded + what failed with retry per section |
| Error | Plain cause, what to do, Retry, keep input; error code for support |
| Offline | Banner "Offline - showing data from 10:42", queue writes, sync status |
| No permission | Why, what the user can do, request-access action |
| Success | Confirm result in place (updated item, toast), next step |

## Undo and destructive patterns
- Reversible → act + Undo (snackbar/toast), soft-delete with trash/30-day restore.
- Irreversible but routine → confirm dialog naming the object and consequence ("Delete 3 files permanently?") with buttons named by action ("Delete files" / "Cancel").
- Irreversible + high impact → type-to-confirm, re-auth, delay (e.g., account deletion scheduled 14 days, cancelable).

## AI-feature UX
- **Streaming**: render incrementally; stable layout (don't reflow code blocks constantly); Stop button replaces Send during generation; auto-scroll only if user is at the bottom; one `aria-live` announcement at completion.
- **Latency**: show stage status ("Searching 12 documents…") for agentic steps; allow cancel; background long tasks with notification.
- **Uncertainty**: show confidence or "may be wrong" only where actionable; prefer "I couldn't find…" over guessing; show sources with snippets and links next to claims.
- **Control**: user edits are first-class (edit prompt, regenerate, branch); diffs for changes to user data/code; explicit approval before sends, purchases, deletions, external messages; undo afterwards.
- **Error recovery**: keep prompt and partial answer; specific errors (rate limit with retry time, content too long with limit); retry button.
- **Prompt box**: autosizing textarea with max height, visible send, attachment chips with remove, model/tool selectors out of the way, keyboard shortcuts, paste images, character/limit warnings late.
- **Trust/safety**: label AI-generated content; never fake typing delays for canned text; data-use disclosure near the input. EU AI Act Art. 50 (tell people they are talking to an AI, mark synthetic content) applies from 2026-08-02; systems already on the market before then get until 2026-12-02 for the machine-readable marking duty only (Commission FAQ).
