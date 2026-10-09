# Accessibility as design, and dark-pattern law (as of 2026-10)

## WCAG 2.2 criteria new designs commonly fail
| SC | Level | Design rule |
|---|---|---|
| 1.4.3 / 1.4.11 Contrast | AA | Text 4.5:1 (large 3:1); input borders, icons, focus rings, chart lines 3:1 against adjacent colors |
| 1.4.10 Reflow | AA | Works at 320 CSS px without 2-D scrolling (tables/maps excepted) |
| 1.4.12 Text spacing | AA | Layout survives line-height 1.5, letter 0.12em, word 0.16em, paragraph 2em (no fixed heights on text boxes) |
| 1.4.13 Content on hover/focus | AA | Tooltips dismissable (Esc), hoverable, persistent |
| 2.4.7 Focus visible | AA | Every focusable element has a visible indicator |
| 2.4.11 Focus not obscured (min) | AA | Focused item not entirely hidden by sticky headers, cookie bars, toasts, chat widgets |
| 2.4.13 Focus appearance | AAA | ≥2px perimeter, 3:1 change; aim for it anyway |
| 2.5.7 Dragging movements | AA | Every drag (sliders, sortable lists, maps, kanban) has a single-pointer alternative |
| 2.5.8 Target size (min) | AA | ≥24x24 CSS px, or 24px spacing circle; inline text links exempt |
| 3.2.6 Consistent help | A | Help/contact mechanism in the same relative place across pages |
| 3.3.7 Redundant entry | A | Don't make users re-enter info in the same process (autofill or select) |
| 3.3.8 Accessible authentication (min) | AA | No memory/transcription/puzzle test without alternative; allow paste and password managers; passkeys/magic links OK; object-recognition CAPTCHA allowed at AA |
| 2.2.1 Timing adjustable | A | Session timeouts warn and extend; toasts with actions don't vanish too fast |
| 1.3.5 Identify input purpose | AA | `autocomplete` tokens on personal-data fields |
| 4.1.2 / 4.1.3 Name, role, value; status messages | A / AA | Custom controls expose state; async results announced via live regions |

Mobile: same rules apply to native apps under EN 301 549 chapter 11 (EAA). Apple 44x44pt and Material 48x48dp exceed 2.5.8; use them.

## Contrast standards, honestly
- Legal/contract compliance today = WCAG 2.x contrast math (relative luminance ratio). Known flaws: overstates contrast of dark colors, ignores font weight, mid-tones problematic.
- APCA (Lc values) was removed from WCAG 3 drafts in 2023; the 2026-09 WD says the algorithm is undecided. Use APCA only as a supplemental check (e.g., avoid thin light text that passes 4.5:1 but reads badly). Never ship a pair that fails WCAG 2 because APCA "passes".

## European Accessibility Act (Directive 2019/882)
- In force for products/services placed on the market after **2025-06-28**. Covers e-commerce, consumer banking, e-books, transport ticketing/info, telecom, computers/OS, smartphones, self-service terminals, AV media access.
- Transitional: service contracts agreed before 2025-06-28 may continue unchanged until 2030-06-28; self-service terminals in use may stay until end of useful life (max 20 years).
- Micro-enterprises (<10 staff and ≤€2M turnover/balance) providing services are exempt.
- Technical baseline: EN 301 549 v3.2.1 (WCAG 2.1 AA) is the cited harmonised standard; ETSI published v4.1.1 (WCAG 2.2 AA) on 2026-09-02, OJ citation pending (unverified). Also requires an accessibility statement/information about how the service meets requirements. Enforcement and penalties are national.
- Practical: WCAG 2.2 AA for web and native, accessible docs (PDF), accessible authentication and payment, support channels that are accessible.

## US
- ADA Title II rule (2024): state/local government web and apps must meet WCAG 2.1 AA. DOJ interim final rule of 2026-04-20 moved the dates one year: population ≥50k by **2027-04-26**, smaller entities by **2028-04-26**. Title III private-sector lawsuits continue referencing WCAG 2.x AA.

## Dark patterns: what to avoid and the law
| Pattern | Do instead | Rules that bite |
|---|---|---|
| Cookie wall / reject hidden in second layer / pre-ticked | Accept and Reject equally prominent on first layer; granular, no pre-ticks; withdraw as easy as give | GDPR Art. 4(11), 7; ePrivacy; CJEU Planet49 (2019); EDPB guidelines 03/2022 on deceptive design; CNIL fines |
| Roach motel (easy join, hard cancel) | Cancel online, same medium, few steps, one optional save offer | EU: CRD as amended by 2023/2673 (withdrawal function from 2026-06-19); DFA proposal planned Q4 2026. US: ROSCA, state ARLs (CA AB 2863 from 2025-07-01), FTC enforcement; FTC 2024 rule vacated 2025-07-08, new ANPRM 2026-03 |
| Confirmshaming ("No, I hate saving money") | Neutral decline label ("No thanks") | UCPD (misleading/aggressive practices), DSA Art. 25 (platforms) |
| Fake urgency/scarcity timers, fake "3 people viewing" | Real deadlines and stock only | UCPD Annex I, FTC Act §5 |
| Fake reviews/testimonials, review gating | Real, verified, unfiltered reviews | FTC consumer reviews rule (2024), UCPD (Omnibus 2019/2161) |
| Drip pricing / hidden fees at checkout | All-in price from first display | FTC junk-fees rule for live-event tickets and short-term lodging (2025); EU price indication rules |
| Pre-selected add-ons/insurance | Opt-in only | CRD Art. 22 (no pre-ticked extra payments) |
| Nagging permission/notification prompts | Ask once in context, respect "no" | Platform policies (App Store, Play), DSA Art. 25 |
| Disguised ads, trick questions, double negatives | Labeled ads, plain affirmative choices | UCPD, DSA Art. 26 (ad labeling) |
| Free trial auto-converts silently | Clear terms at signup, reminder before charge, easy cancel | ROSCA, CA ARL (free-to-paid conversions covered) |

Design test: would the flow still work if every choice were made with equal effort in both directions? If no, fix it.
