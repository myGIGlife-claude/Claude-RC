# Deadline protocol (checked 2026-10-10)

Never compute a critical deadline from memory or a generic day counter. Read the rule text in force, get the trigger date from a document,
compute, then recompute independently (count again from the other direction, or by a second method).

## Deadline record
```
ID:         D-03     Label: VERIFIED | PROVISIONAL | UNKNOWN   (why not VERIFIED: ...)
Action:     <what must be filed/served/paid, by whom>
Court:      <tribunal, division>      Rule/statute: <cite, version in force>   Local rule/order: <cite or "checked, none">
Trigger:    <event> on YYYY-MM-DD, source: <document, docket entry #>
Counting:   calendar days | court days | hours; exclude trigger day? ; last-day rollover rule
Add-ons:    service method (<mail/e-service/hand>) -> +N days per <rule> | none
Holidays:   <which list applies; any in the window>
Cutoff:     <e-filing time of day, time zone; clerk hours for paper>
Result:     due YYYY-MM-DD HH:MM <tz>   Rechecked by: <second method> on YYYY-MM-DD
Extendable? <who may extend, before/after expiry, rule>   Jurisdictional? yes/no/unclear (authority)
Effect of other motions: tolls? <rule says / does not say>   Stay? <rule says / does not say>
```
Label rules: VERIFIED needs the rule text read, the trigger from a document, local rules checked, and two matching computations.
PROVISIONAL when any of those is missing (say which). UNKNOWN when the rule or trigger is not identified. Tell the user to file early on
anything not VERIFIED, and that a lawyer or the clerk's office can confirm local practice (clerks give procedure, not legal advice).

## Federal civil computation (FRCP 6, text read 2026-10-10)
- 6(a)(1): exclude the trigger day; count every day including weekends and legal holidays; if the last day is a weekend or legal holiday,
  continue to the next day that is neither.
- 6(a)(2): hour periods count every hour; a period ending on a weekend or holiday runs to the same time on the next business day.
- 6(a)(3): clerk's office inaccessible on the last day: extend to the first accessible day that is not a weekend or holiday.
- 6(a)(4): the last day ends at midnight in the court's time zone for e-filing, at the clerk's scheduled closing for other filing,
  unless a statute, local rule or order says otherwise (check local rules for earlier e-filing cutoffs).
- 6(a)(6): legal holidays are the listed federal holidays (including Juneteenth), days the President or Congress declares, and, for
  forward-counted periods, state holidays where the district court sits.
- 6(b): extensions for good cause before expiry, or after expiry only on motion showing excusable neglect; never for Rules 50(b) and (d),
  52(b), 59(b), (d), (e), or 60(b).
- 6(d): +3 days only for service by mail, leaving with the clerk, or other consented means; electronic service gets no added days (since 2016).
- Proposed amendments to Civil Rules 5, 6 and others are slated no earlier than 2028-12-01: not law yet.

## Federal appeals (FRAP 4, read 2026-10-10)
- 4(a)(1): civil notice of appeal within 30 days after entry of the judgment or order; 60 days when the United States, its agency or
  officer is a party.
- 4(a)(4)(A): a TIMELY motion under Rule 50(b), 52(b), 54 (fees, only if the court acts under Rule 58), 59 (alter/amend or new trial),
  or 60 (only if filed within the Rule 59 time) resets appeal time to run from the order disposing of the last such motion.
  An untimely or unlisted motion does not reset it.
- 4(a)(5): extension only if moved for within 30 days after the time expires, on excusable neglect or good cause; capped at 30 days after
  the original deadline or 14 days after the order granting it, whichever is later.
- Statutory appeal deadlines are jurisdictional: Bowles v. Russell, 551 U.S. 205 (2007). Rule-only limits are mandatory claim-processing
  rules that can be forfeited if not raised: Hamer v. Neighborhood Housing Services of Chicago, 583 U.S. 17 (2017) (No. 16-658).
  Plan as if every appeal deadline is fatal.

## Stays are separate from deadlines
- Federal civil: FRCP 62(a) automatic stay of execution for 30 days after entry unless the court orders otherwise; 62(b) stay by bond or
  other security once the court approves it; 62(c) injunction and receivership judgments are not stayed by an appeal unless ordered.
  Stays pending appeal: FRAP 8 (move in the district court first, ordinarily).
- State courts, agencies and child-support enforcement have their own stay rules: check them (rc-us-litigation-procedure,
  rc-ohio-child-support). Filing a motion stays nothing by default.

## State courts
Ohio and NC (and every other state) have their own computation, service add-on, holiday and appeal-time rules, and some appeal clocks run
from entry/journalization or from service of the judgment. Use rc-us-litigation-procedure and the court's local rules; record the version.

## Deadline checklist output (for the user)
| # | Action | Due (with time zone) | Label | Rule | Trigger (source) | Notes (add-ons, holidays, tolling) |
|---|---|---|---|---|---|---|
