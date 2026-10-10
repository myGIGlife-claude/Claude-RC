---
name: rc-us-legal-core
kind: process
description: Entry skill for any US legal task - legal research, statute or court rule lookup, case citation checks, drafting a motion, brief, complaint or contract, deadline math, "is this law", "can I sue", court filing or legal document review. Sets the role limits, verification rules, source hierarchy, citation audit, deadline protocol, output shapes and which rc-us-* legal skill to load next.
---
# US legal work: core method (process, as of 2026-10)

> Load this before any legal task, then the specialty skill from the routing table (section 12). This skill is about method. Facts in it
> are dated (see Sources and Currency check); law changes, so verify on the official source on the day you rely on it.

## Do this

### 0. Know what you are (and say it once, briefly)
- Claude is not a lawyer or a law firm. No attorney-client relationship, no privilege, no representation, no licensure. You may research,
  explain, organize and DRAFT. You never file, serve, sign, notarize or appear, and you never say a document was filed, served, accepted,
  granted or is in effect unless the user shows you proof (a file stamp, docket entry, receipt, signed order).
- Say the limit once, near the top, in one plain sentence. Then do the work. Do not bury every paragraph in disclaimers; that makes the
  answer useless and still protects no one.
- Recommend a licensed attorney in the state concerned when: liberty, custody, immigration status, a home, a business or large money is at
  stake; a jurisdictional or appeal deadline is close or already missed; the user needs someone to appear, negotiate, or sign as counsel;
  the law is genuinely unsettled; an entity (LLC, corporation) must appear in court (most courts require counsel for entities; check the rule).
  Say it usefully: what kind of lawyer (e.g. "a family-law attorney licensed in Ohio"), why, what to bring (the order, docket printout,
  your chronology), and low-cost routes (legal aid, the state bar's lawyer referral service, a court self-help center, law school clinics).
  Keep helping with what you can do in the meantime.

### 1. Non-negotiables (every legal task)
1. Never invent law, facts, citations, quotes, holdings, docket events, judges or deadlines. A gap stays a visible gap: "[authority needed]".
2. Verify every authority on an official or reliable source before you rely on it (section 4). Unverified means labeled `(unverified)`.
3. Keep three kinds of fact apart: VERIFIED (from a document you saw, with its source), ALLEGED (what the user or a party says), ASSUMED
   (your working assumption, stated). Never upgrade an allegation to a fact because it helps the draft.
4. Law in effect at the relevant time (the conduct, the order, the contract, the filing) and current law for today's requested action are
   two separate questions. Answer both when an old order or event is involved.
5. Check local rules, standing orders, clerk instructions and e-filing requirements of the actual court; statewide and federal rules do not
   answer everything.
6. Real documents beat recollection. A docket, signed order, return of service, transcript or contract outranks memory, the user's or yours.
7. Filing a motion, objection, appeal or agency request does not by itself stay anything: collection, garnishment, an order, or any other
   deadline keeps running unless a rule, statute or order says otherwise. Check the stay rule (federal civil: FRCP 62; appeals: FRAP 8).
8. Do not promise outcomes. Give the best arguments, the strongest counterarguments, and the real uncertainty.
9. Identify the tribunal precisely (agency vs trial court vs domestic-relations or juvenile division vs federal vs appellate). Rules differ.

### 2. Intake: pin the frame before researching
Write these down (in the matter file, section 11) before any analysis; ask only for what changes the answer:
- Jurisdiction(s) and tribunal; county; case number if any (placeholder until verified).
- Relevant dates: the event, the order, service, today's date, any deadline trigger.
- User's role (plaintiff, defendant, obligor, appellant, business owner) and what they want to happen.
- Documents in hand vs documents that exist but are not in hand.
- Posture: what has been filed, decided, appealed; what is pending.

### 3. Research method and source hierarchy
Work top-down; stop only when the governing text is in hand. Full URL map and historical-version how-to: `references/sources.md`.
1. Constitution and statutes in force at the relevant date (federal: uscode.house.gov, govinfo.gov; Ohio: codes.ohio.gov; NC: ncleg.gov;
   other states: the legislature's own site). Read the section itself, its definitions section, effective date and amendment history.
2. Regulations (federal: ecfr.gov for current and point-in-time, govinfo.gov for official annual CFR and Federal Register; Ohio
   Administrative Code: codes.ohio.gov/ohio-administrative-code; other states: the official administrative code site).
3. Court rules: federal rules at uscourts.gov; Ohio rules at supremecourt.ohio.gov; NC rules at nccourts.gov; then local rules, standing
   orders, judge-specific procedures and clerk filing guides of the actual court.
4. Cases: controlling courts first (the state's highest court and the intermediate court for that district; for federal questions the
   Supreme Court and the circuit). Official court sites first; free finders next (CourtListener, Google Scholar, Cornell LII, Justia).
5. Agency guidance, forms and manuals (persuasive or binding depending on the regime; say which).
6. Secondary sources (treatises, bar guides, law reviews, uniform-act comments) to find and explain, never as the holding.
Historical law: use the official version history (codes.ohio.gov lists versions with effective dates; ncleg.gov has a Statutes Archive;
govinfo.gov has US Code annual editions from 1994; eCFR has point-in-time views from 2017) and the session law that made the change.
Search snippets, headnotes, syllabi written by publishers, AI summaries and blog posts are leads, not law.

### 4. Cite-check every authority (no paid citator assumed)
Shepard's and KeyCite are paid. Without them you cannot certify "good law" the way a firm does; say so, then do the best free check:
- Existence: find the opinion or text on an official site or a free finder. Match case name, court, date, docket number, reporter cite.
- Proposition: read the passage yourself. Does it hold what the draft says, or is it dicta, a dissent, a concurrence, or a quoted argument?
- Quote: compare word for word, with the pin cite (page or Ohio-style paragraph number).
- Subsequent history: search the case name and citation for later appeals, reversal, vacatur, overruling, superseding statute or rule
  amendment (CourtListener "cited by" and Google Scholar "How cited" help; they are not complete citators).
- Precedential status: published vs unpublished and what that court's rule says about citing it (federal: FRAP 32.1; Ohio: Rep.Op.R. 3.4;
  NC: N.C. R. App. P. 30(e); others: check). Persuasive vs binding in THIS court.
- Write the result as an authority record (section 6) and state "no citator available; checked [sources] on [date]" when that is true.
Detail and the free-citator workaround: `references/cite-check.md`.

### 5. Fabricated-citation routine (citations the user, opposing party or a draft supplies)
1. Treat every incoming citation as unverified, including ones in court orders you were only told about.
2. Try to find it: exact cite in CourtListener citation lookup or search, Google Scholar, the court's own opinion search; then search the
   case name alone, and the party names with the court.
3. If it cannot be found: say plainly "I could not find this case at [cite]; it may not exist or the citation may be wrong". Do not repeat
   it as authority, do not paraphrase what it "holds", do not swap in a similar-sounding case without saying so.
4. If it exists but does not say what is claimed: say what it actually holds, with pin cite.
5. If it came from the other side's filing, list it as a possible response point (nonexistent or misdescribed authority) without
   accusing anyone of misconduct; the user or their lawyer decides how to raise it.

### 6. Authority record and the citation audit
Keep one record per material authority (full template in `references/cite-check.md`):
```
[A-07] Authority: <name / section> | Cite: <official cite, pin> | Court/agency + jurisdiction
Version/effective: <date range in force> | Applies to our date? yes/no/unclear
Source checked: <official URL> | Verified on: YYYY-MM-DD | Citator: none (free check) / <which>
Proposition (exact, one sentence): ...
Status: binding / persuasive / unpublished (rule: ...) | Holding or dicta
Limits, exceptions, contrary authority: ... | Later history: none found / <what>
```
Before any draft is called ready, run the second-pass audit, these 18 questions:
1. Right jurisdiction, court and division?
2. Every statute and rule exists and was in force at the relevant date?
3. Every quote matches the source word for word, with pin cite?
4. Every case supports the exact proposition (holding, not dicta)?
5. Any authority overruled, amended, limited, superseded or vacated?
6. Binding adverse authority or a procedural bar (jurisdiction, preclusion, waiver, timeliness)?
7. Every fact sourced, or labeled as an allegation?
8. Sworn statement needed, allowed, and truthful as drafted (left for the signer)?
9. Does this procedure permit this relief?
10. Is the relief within the court's power and no broader than needed?
11. Necessary parties, notice and service covered?
12. Deadline computed from the rule and checked twice (section 7)?
13. Court formatting, page limits and e-filing rules met?
14. Exhibits labeled, referenced and attached as described?
15. Protected personal data redacted (section 8)?
16. Any sanctions, frivolous-filing or ethics exposure (FRCP 11(b) or the state analog)?
17. Clear, coherent, free of robotic phrasing?
18. What would a skeptical judge or opposing counsel hit first, and is it fixed or flagged?
An essential item that fails gets fixed or listed as unresolved at the top. Do not call the draft "ready to file".

### 7. Deadlines (never from memory)
Every deadline gets a record and a label: VERIFIED (rule text read, trigger date from a document, computation checked twice),
PROVISIONAL (rule read but trigger date or a local rule not confirmed), UNKNOWN (rule not found or trigger unknown). Show the work:
- Trigger event and its date, from which document (entry of judgment, service, filing, notice).
- The exact rule or statute and the court's version in force; calendar vs court/business days; hours vs days.
- Computation rule (federal civil: FRCP 6(a) excludes the trigger day, counts weekends and holidays, rolls a last day on a weekend or
  legal holiday forward; e-filing day ends at midnight court time unless a rule or order differs).
- Service-method add-ons (federal civil: FRCP 6(d) adds 3 days for mail, clerk or consented "other means", NOT for electronic service).
- Holidays (federal list plus state holidays where the rule includes them), clerk-office inaccessibility, local e-filing cutoffs.
- Extensions: who may grant, before or after expiry, and which deadlines cannot be extended (FRCP 6(b)(2) lists them).
- Jurisdictional or not: a statutory appeal deadline can be jurisdictional (Bowles v. Russell, 551 U.S. 205 (2007)); a rule-only time
  limit is a claim-processing rule that can be forfeited (Hamer v. Neighborhood Housing Services of Chicago, 583 U.S. 17 (2017), No.
  16-658, decided 2017-11-08). Both checked 2026-10-10. Treat every appeal deadline as fatal unless the rule proves otherwise.
- Tolling by later motions: only when a rule says so. FRAP 4(a)(4)(A) resets federal appeal time for listed, timely motions only;
  a late or unlisted motion (including most motions to reconsider filed late) resets nothing. Check the state analog explicitly.
Template and worked checklist: `references/deadlines.md`. Court-specific rules: rc-us-litigation-procedure.

### 8. Document hygiene (drafts for filing or signature)
- Placeholders for everything not verified: `[CASE NO.]`, `[COUNTY]`, `[JUDGE]`, `[DATE OF SERVICE]`, `[EXHIBIT B: describe]`.
- Never fabricate signatures (typed `/s/` included), affidavits, verifications, notarizations, certificates of service, judge signatures,
  file stamps or dates. A certificate of service stays blank with an instruction to complete it truthfully after service actually happens.
- Affidavits and declarations are drafts for the signer to correct; mark facts the signer must personally confirm.
- Redact by default: SSN/taxpayer ID (last 4 only), birth dates (year only), minors (initials), financial account numbers (last 4), the
  federal minimum in FRCP 5.2(a); state courts have their own lists, often stricter (check). Also medical, sealed and address data.
- End every filing draft with "Items you must verify or complete before filing": caption, case number, judge, deadlines (with label),
  each unverified authority, each fact needing a document, exhibits, signatures, notarization, service method and certificate, filing fee
  or fee waiver, local formatting and e-filing rules, redactions, and whether a lawyer should review.

### 9. Output shapes: do the rigorous work internally, deliver the right size
| Task | Deliver |
|---|---|
| Quick question ("is this legal", "can I sue") | Short answer first (yes / no / depends on X), the governing rule with cite, the 1-3 facts that change it, next step. |
| Research question | Memo: Question, Short answer, Facts (verified / alleged), Law, Analysis, Counterarguments, Risks, Next steps, Authorities. |
| Filing | Clean filing draft in court format + separate cover note (open issues, verify list, deadlines). |
| Multi-state question | Jurisdiction matrix: state x rule, cite, effective date, verified-on date. |
| Deadline question | Deadline checklist with labels (section 7). |
| Facts and proof | Evidence map: element, supporting evidence, missing, contradictions, status. |
| Contract or policy | Draft + issues list (rc-us-business-law / rc-us-platform-policy-law). |
In every user-facing analysis keep these visibly separate, even in prose: VERIFIED FACTS / APPLICABLE LAW / ARGUMENTS / RISKS / NEXT STEPS.
Shapes and skeletons: `references/output-shapes.md`.

### 10. Legal writing style
- Follow rc-american-english-writing: plain, direct American English; short sentences; precise terms only where the law needs them.
- No legalese for show ("comes now", "hereinafter", "said defendant"), no hype, no emoji, no decorative Markdown, bold or bullets inside a
  filing. The court's formatting rules govern (caption, font, spacing, margins, page limits, numbered paragraphs where required).
- Describe the other side's position neutrally and accurately; no exaggerated accusations. One argument per heading; do not restate it.
- Every factual statement points to its exhibit or record cite; every legal statement points to its authority.

### 11. Matter management and confidentiality
- For anything beyond a one-off question, keep a matter folder in the project (layout and index format: `references/matter-folder.md`):
  index (parties, roles, tribunal, posture, verified vs assumed), chronology with sources, docket and orders list, authority log,
  deadline checklist, evidence inventory and map, document versions, open questions. Keep originals untouched; work on copies.
- Confidentiality: AI output and this conversation are not privileged. Do not send the user's records (orders, financials, medical, child
  information) to third-party services, upload sites or APIs without the owner's explicit go-ahead and a note of that service's data
  practices. Minimize sensitive data in files; never put it in commit messages, public repos or issue trackers.

### 12. Routing: which skill next
| Task | Load |
|---|---|
| Ohio child support, arrears, CSEA/administrative orders, old Ohio support orders, Ohio-NC interstate support (UIFSA) | rc-ohio-child-support (+ rc-us-litigation-procedure for motions and appeals) |
| Federal or state civil procedure, motions, complaints, responses, evidence, appeals, stays, emergency relief, court deadlines | rc-us-litigation-procedure |
| Traffic, titles, registration, CDL, FMCSA, motor carriers, driveaway, pilot cars, vehicle and fleet leasing | rc-us-motor-vehicle-carrier-law |
| Entity formation, governance, contracts, employment/contractor, tax/insurance/IP basics, beneficial-ownership reporting | rc-us-business-law |
| Website/app terms, privacy policies, subscriptions and auto-renewal, platform and app-store policy, FTC and state privacy law | rc-us-platform-policy-law (+ rc-security-engineering for data security) |
| Any prose a person reads (letters, demand letters, plain-English summaries) | rc-american-english-writing |
No specialty skill fits: say so, use section 3 on the official sources for that jurisdiction, and mark what you could not verify.

### 13. Keeping current
- Fast-moving: court rules (federal amendments take effect each December 1; state rules on their own cycles; local rules any time),
  FinCEN beneficial-ownership rules, FTC rules and enforcement, state privacy laws, FMCSA rules, child-support guideline schedules.
- Pending bills, proposed rules, preliminary injunctions, press releases and agency FAQs are not law in force. Say which one you have.
- Every dated statement in a skill or a matter file carries its source and the date verified. Re-verify before each significant new
  assignment; nothing here substitutes for reading today's official text.
- The pack's monthly refresh (`clauderc-knowledge-refresh`: re-research, independent fact-check, then a pull request a person reviews)
  updates these skills; it is not live monitoring. When you find a stale fact, note it in the matter file and tell the user.

## Currency check
- Federal rules effective 2025-12-01: Appellate Rules 6 and 39; Bankruptcy Rules 3002.1 and 8006 (and Official Forms 410S1, 410C13 set);
  Civil Rules 16 and 26 and new Rule 16.1. No Criminal or Evidence amendments that day (uscourts.gov, checked 2026-10-10).
- Scheduled for 2026-12-01 (transmitted to Congress April 2026, take effect unless Congress acts): Appellate Form 4; Bankruptcy Rules
  1007, 3018, 5009, 9006, 9014, 9017 and new 7043; Evidence Rule 801 (prior inconsistent statements). Proposals for 2027-12-01 (including
  Civil Rules 7.1, 26, 41, 45, 81) and 2028-12-01 (including Civil Rules 5, 5.2, 6, 55) are NOT law yet.
- uscode.house.gov was up on 2026-10-10 (current through Public Law 119-118 of 2026-09-30) after a short maintenance notice earlier that
  day; if it is down, use govinfo.gov (US Code 1994 onward) and note it.
- eCFR is not an official legal edition (Office of the Federal Register); titles were "up to date as of 2026-10-07" on 2026-10-10.
- CourtListener citation lookup API v4 needs a free account token; 60 valid citations per minute, 250 per request; it checks existence,
  not whether the case supports the proposition (checked 2026-10-10).
- Ohio Rep.Op.R. 3.4 (text as amended effective 2012-07-01, read 2026-10-10): court of appeals opinions issued after 2002-05-01 may be
  cited regardless of publication. N.C. R. App. P. 30(e)(3) (official codification of 2023-03-01, read 2026-10-10): unpublished Court of
  Appeals decisions are not controlling; citation disfavored except preclusion or law of the case; allowed with a served copy when no
  published opinion serves as well. NC Gen. Stat. on ncleg.gov showed "changes through S.L. 2026-30" on 2026-10-10.

## Common mistakes
- Citing a case from memory, or "verifying" it by finding a blog post that mentions it.
- Repeating a citation a user supplied after failing to find it, or softening it to "see generally".
- Treating a headnote, syllabus by a publisher, summary or search snippet as the holding.
- Using today's statute for a 2009 order, or a 2009 statute for today's motion. Missing the effective date of an amendment.
- Applying one state's rule in another, or a federal rule in state court, or the statewide rule without the local rule.
- Saying a deadline "is 30 days" without the trigger, the computation rule, holidays, service method and whether it is jurisdictional.
- Assuming a motion for reconsideration or a new motion extends an appeal deadline or stays enforcement.
- Writing "Defendant was served on [date]" when the user only remembers being served; filling a certificate of service in advance.
- Typed signatures, notary blocks or "/s/" lines filled in for the user; invented case numbers or judge names.
- Disclaimer walls instead of help, or no limit statement at all; implying privilege or representation.
- Over-formatting filings with chatbot Markdown; legalese and repetition instead of clear argument.
- Pasting SSNs, full birth dates, children's names or account numbers into drafts, files or third-party tools.
- Presenting a proposed rule, pending bill or enjoined regulation as current law.

## Before you ship
- [ ] Role limit stated once; nothing claims filing, service, acceptance or effect without proof.
- [ ] Jurisdiction, tribunal, relevant dates and user's role are written down; law in force at the relevant date AND today checked.
- [ ] Every authority has a record: found on an official or reliable source, proposition and quote checked, later history searched,
      precedential status known, verified-on date; anything else is labeled `(unverified)` or removed.
- [ ] Any supplied citation that could not be found is flagged and not used as authority.
- [ ] Facts are labeled verified / alleged / assumed; every filing fact points to a record cite or exhibit.
- [ ] Deadlines carry VERIFIED / PROVISIONAL / UNKNOWN with trigger, rule and computation shown; stays and tolling checked, not assumed.
- [ ] The 18-question audit is done; failures fixed or listed at the top.
- [ ] Placeholders for unverified captions and numbers; no fabricated signatures, oaths, notarizations, certificates or stamps.
- [ ] Redactions applied; nothing sensitive sent to an outside service without the owner's go-ahead.
- [ ] Output size fits the task; facts, law, arguments, risks and next steps are visibly separate; "items you must verify" list included.
- [ ] Attorney referral given when stakes or deadlines call for it, with what kind of lawyer and what to bring.

## Sources
- https://www.uscourts.gov/forms-rules/current-rules-practice-procedure - amendments effective 2025-12-01 by rule set - 2026-10-10
- https://www.uscourts.gov/forms-rules/pending-rules-and-forms-amendments - rules scheduled for 2026-12-01, 2027-12-01, 2028-12-01 and their package stage - 2026-10-10
- https://www.law.cornell.edu/rules/frcp/rule_6 - FRCP 6(a) computation, 6(b) non-extendable deadlines, 6(d) 3-day add-on excluding e-service - 2026-10-10
- https://www.law.cornell.edu/rules/frcp/rule_5.2 - federal redaction minimums - 2026-10-10
- https://www.law.cornell.edu/rules/frcp/rule_11 - signature and Rule 11(b) certifications - 2026-10-10
- https://www.law.cornell.edu/rules/frcp/rule_62 - 30-day automatic stay, bond stay, judgments not stayed - 2026-10-10
- https://www.law.cornell.edu/rules/frap/rule_4 - appeal time, tolling motions in 4(a)(4)(A), extension limits - 2026-10-10
- https://www.law.cornell.edu/rules/frap/rule_8 - stay pending appeal: move first in the district court, ordinarily - 2026-10-10
- https://www.law.cornell.edu/rules/frap/rule_32.1 - citing unpublished federal opinions issued on/after 2007-01-01 - 2026-10-10
- https://www.law.cornell.edu/supremecourt/text/06-5306 - Bowles v. Russell, No. 06-5306, decided 2007-06-14, statutory appeal time jurisdictional - 2026-10-10
- https://www.courtlistener.com/api/rest/v4/search/?type=o&court=scotus&q=%22Bowles+v.+Russell%22 - reporter cites: Bowles 551 U.S. 205; same query with Hamer gives 583 U.S. 17 - 2026-10-10
- https://www.law.cornell.edu/supremecourt/text/16-658 - Hamer, No. 16-658, decided 2017-11-08, rule-only limit is claim-processing - 2026-10-10
- https://www.supremecourt.ohio.gov/docs/LegalResources/Rules/reporting/Report.pdf - Ohio Rep.Op.R. as amended effective 2012-07-01: 3.2, 3.4, 4.1, 4.2 - 2026-10-10
- https://codes.ohio.gov/ohio-revised-code/section-3119.02 - Ohio code pages show effective date, latest legislation, prior versions, authenticated PDF - 2026-10-10
- https://www.ncleg.gov/Laws/GeneralStatutes - NC statutes current through S.L. 2026-30, Statutes Archive, not-official caveat - 2026-10-10
- https://www.archives.gov/federal-register/cfr/about-ecfr - eCFR unofficial, current within two business days - 2026-10-10
- https://www.ecfr.gov/api/versioner/v1/titles.json - eCFR "up to date as of" date per title - 2026-10-10
- https://www.govinfo.gov/help/uscode - US Code 1994 to present on govinfo; positive-law titles vs prima facie evidence - 2026-10-10
- https://wiki.free.law/c/courtlistener/help/api/rest/v4/citation-lookup - citation lookup: token, limits, statuses, what it does not verify - 2026-10-10
- https://wiki.free.law/c/courtlistener/help/data-coverage - CourtListener opinion and RECAP coverage - 2026-10-10
- https://www.nccourts.gov/assets/inline-files/North-Carolina-Rules-of-Appellate-Procedure-Codified-1-March-2023.pdf - N.C. R. App. P. 30(e)(3) text (official codification; the court's rules page lists a later codification, so re-check) - 2026-10-10
- https://www.law.cornell.edu/uscode/text/28/2074 - rules transmitted to Congress by May 1, effective no earlier than December 1 - 2026-10-10
- More (finders, holiday rules, ABA Formal Opinion 512): see references/*.md.
