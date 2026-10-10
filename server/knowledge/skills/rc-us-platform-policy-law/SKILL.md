---
name: rc-us-platform-policy-law
description: US law and platform rules for app/website policies as of 2026-10: privacy policy, terms of service/use, EULA, acceptable use, refund policy, subscription auto-renewal, cookie notice, data deletion, DMCA, COPPA, CCPA/CPRA and state privacy laws, FTC, CAN-SPAM, TCPA, ADA/WCAG, App Store privacy labels, Play Data safety form. Use when drafting or reviewing these documents or checking them against the code.
---
# US platform policy law for apps and websites  (as of 2026-10)

> Drafting aid, not legal advice. Facts are dated; laws here change monthly. Anything marked (unverified) is a lead, not a fact.
> Engineering controls (DSAR plumbing, breach response, encryption, PCI scope, threat models) live in **rc-security-engineering**
> (`references/compliance-privacy.md`): link there, don't repeat it. Write every policy in plain American English (**rc-american-english-writing**).

References: `references/state-privacy-laws.md` (state table, kids' laws, breach notice), `references/federal-law.md` (FTC Act, COPPA,
ROSCA, CAN-SPAM, TCPA, DMCA, E-SIGN, ADA, PCI), `references/app-store-requirements.md` (Apple, Google Play),
`references/policy-checklists.md` (document-by-document must/should/don't, policy-vs-code reconciliation), `references/templates.md`
(fill-in skeletons for a privacy policy and terms of service).

## Currency check
Federal (verified on official sources unless marked):
- **FTC "click-to-cancel" is gone; ROSCA is not.** The Eighth Circuit vacated the 2024 Negative Option Rule amendments in
  *Custom Commc'ns, Inc. v. FTC*, 142 F.4th 1060 (8th Cir. July 8, 2025) (No. 24-3137; cite checked 2026-10-10 in 91 FR 6507 n.3 and on CourtListener).
  FTC restored the old 1973 prenotification rule text on **2026-02-12** (91 FR 6507, now 16 CFR 425) and published an **ANPRM on 2026-03-13**
  (91 FR 12318, comments closed 2026-04-13) to start over. No new proposed rule as of 2026-10. ROSCA (15 U.S.C. 8403) still requires: material
  terms before billing info, express informed consent before charging, simple way to stop charges.
- **COPPA amended Rule**: published 2025-04-22 (90 FR 16918), effective 2025-06-23, compliance deadline for most provisions
  **2026-04-22** (rule's DATES section). Current 16 CFR 312 (eCFR, 2026-10-01) has: separate
  verifiable parental consent for disclosure to third parties unless integral (312.5(a)(2)); written information security program,
  annual risk assessment (312.8); written data retention policy, no indefinite retention, published in the notice (312.10).
  FTC age-verification enforcement policy statement 2026-02-25 (data collected only to check age, deleted promptly; still in force).
  The FTC's 2026-09-09 "Withdraws Obsolete Policy Statement" release rescinded the 2021 Health Breach Notification policy statement on
  health apps, not the age-verification statement (ftc.gov, read 2026-10-10).
- **TAKE IT DOWN Act** (Pub. L. 119-12, signed 2025-05-19): covered platforms (user-generated content) needed a notice-and-removal process by **2026-05-19**; remove
  reported non-consensual intimate images and known identical copies within **48 hours** of a valid request. FTC enforces since
  2026-05-19 (press releases 2026-05-19 and 2026-05-20: warning letters to 12 "nudify" sites; up to $53,088 per violation).
- **TCPA**: the FCC one-to-one consent rule was vacated by the Eleventh Circuit (*Insurance Marketing Coalition Ltd. v. FCC*, 127 F.4th
  303 (11th Cir. Jan. 24, 2025), CourtListener); current 47 CFR
  64.1200(f)(9) has no one-seller-per-consent wording (eCFR 2026-10-01). Revocation rule in force: any reasonable method
  (STOP, quit, end, revoke, opt out, cancel, unsubscribe), honored within **10 business days**, one confirmation text allowed.
- **Fake reviews**: 16 CFR 465 (Consumer Reviews and Testimonials Rule) effective 2024-10-21; civil penalties for knowing violations
  (up to $53,088 per violation per FTC 2025 letters, inflation-adjusted yearly). FTC sent warning letters 2025-12.
- **CAN-SPAM** penalty up to $53,088 per email (FTC guide); opt-outs honored within 10 business days.
- **FTC Health Breach Notification Rule** (16 CFR 318, amendments published 2024-05-30, effective 2024-07-29): health apps not covered by HIPAA; notice within 60 days.
- **ADA Title II web rule** (state/local government): WCAG 2.1 AA, compliance moved by interim final rule (2026-04-20) to
  **2027-04-26** (50,000+ population) and **2028-04-26** (smaller, special districts) (28 CFR 35.200, eCFR 2026-10-01). No DOJ web
  rule for private businesses (Title III); courts and settlements use WCAG 2.x AA as the yardstick. **WCAG 2.2** is the current W3C
  Recommendation (2023-10, edition dated 2024-12-12); WCAG 3 is a draft.
- **DMCA agent**: designation in the Copyright Office directory **expires after 3 years** unless renewed (37 CFR 201.38(c)(4)).
- **PCI DSS 4.0.1** is the only active version; future-dated requirements mandatory since 2025-03-31. No v5 date announced (unverified).

State (details, thresholds and sources in `references/state-privacy-laws.md`):
- **~20 states have comprehensive privacy laws in force** as of 2026-10 (CA plus 19 Virginia/Connecticut-style laws; Indiana,
  Kentucky, Rhode Island took effect 2026-01-01). **Enacted 2026, not yet in force** (thresholds read on the enrolled acts):
  Oklahoma (SB 546, signed 2026-03-20, eff. 2027-01-01; 100,000 consumers, or 25,000 + >50% revenue from sale), Louisiana (SB 386,
  Act 502, signed 2026-05-29, eff. 2027-01-01; California-style: revenue > $25M, or data of 75,000+ consumers/households/devices, or
  50%+ revenue from sale), Alabama (HB 351, eff. 2027-05-01; > 25,000 consumers, or > 25% revenue from sale; signed 2026-04-16 or
  -17 per law-firm reports), Vermont (S.71, Act 145, signed 2026-06-16, eff. **2028-01-01**; 35,000 / 3,000 sensitive / 3,000 sale).
  Delaware lowers its thresholds to 10,000 (or 5,000 + >20% sale) on 2027-01-01 (delcode.delaware.gov).
- **Ohio and North Carolina have no comprehensive consumer privacy law** as of 2026-10 (bills only: NC S 1022 filed 2026-04-30,
  parked in Senate Appropriations 2026-05-05; Ohio 136th GA has no consumer-rights bill). They still have breach-notice laws (Ohio R.C. 1349.19, 45 days; N.C.G.S. 75-65, notify the AG too)
  and Ohio's cybersecurity safe harbor (R.C. 1354, affirmative defense with a written program conforming to NIST/ISO/CIS etc.).
- **California**: CPPA regulations effective 2026-01-01 (risk assessments, ADMT from 2027-01-01, cybersecurity audits from 2028);
  revenue threshold $26,625,000 (adjusted 2025-01-01, next adjustment 2027-01-01). Auto-renewal law (AB 2863) amended effective
  **2025-07-01**: express affirmative consent, keep proof 3 years, annual reminder, cancel in the same medium, click-to-cancel online.
  AB 566 (browsers must offer an opt-out signal) and AB 1043 (OS age signal apps must request) start 2027-01-01 (law-firm reports).
- **Age/app-store laws**: Texas SB 2420 is in force: the Fifth Circuit stayed the injunction pending appeal (No. 26-50001,
  2026-06-04) and the Supreme Court refused to vacate the stay (No. 25A1390, 2026-07-06, docket read); the merits appeal is still
  open. Apple applies it to new Texas Apple Accounts from 2026-06-04 (Apple news 2026-06-03); developers use the Declared Age Range
  and PermissionKit Significant Change APIs. Utah: key duties delayed to 2027-05-06, enforcement only by private suit (HB 498, 2026;
  law-firm reports). Louisiana: delayed to 2027-07-01 (HB 977, Act 185, signed 2026-05-15; date per law-firm reports). California
  AADC: *NetChoice v. Bonta* (9th Cir. 2026-03-12) kept the injunction on the data-use limits and dark-pattern ban (vague), lifted it
  on the coverage definition and age estimation and sent those back; the 2024 injunction on impact assessments stands (holding per
  law-firm reports; opinion text not retrieved). Nothing in the AADC is being enforced yet.

Platforms: Apple App Review Guidelines last updated **2026-06-08**; Google Play User Data, Account deletion and Subscriptions policies (pages © 2026, no revision date shown).

### Older documents
- Policies written before 2025-07 often promise FTC click-to-cancel compliance as "the law", or cite a California ARL without the
  2025 amendments: re-check both. Policies from before 2026-04-22 for child-directed services lack the COPPA retention policy and
  separate third-party consent.
- A policy that lists "the 5 states with privacy laws" (2023 era) is stale. A policy that still says "we respond to Do Not Track"
  with nothing about Global Privacy Control is incomplete in GPC states.
- Don't rewrite an old policy wholesale inside an unrelated task: flag what changed and let the owner decide.

## What changed / stop doing
| Old advice | Do instead | Since |
|---|---|---|
| "Comply with the FTC click-to-cancel rule" | ROSCA + state ARLs (CA, NC, others) + app-store rules; FTC rulemaking restarted (ANPRM) | vacated 2025 (8th Cir.); restored text 2026-02-12 |
| "TCPA needs one-to-one consent per seller" | Prior express written consent per 64.1200(f)(9); honor revocation by any reasonable method within 10 business days | vacated 2025-01 |
| Keep children's data "as long as needed", one bundled parental consent | Written retention schedule with deletion timeframe; separate consent for third-party disclosure; written security program | COPPA compliance 2026-04-22 |
| Boilerplate "we use industry-standard security" | Say what you actually do (TLS, encryption at rest, access control) and nothing you don't | FTC Section 5 practice |
| "We collect no personal data" | List the real minimum: account email, auth records, payment metadata, logs, IPs, push tokens | always |
| Only "Do Not Track" text | Honor Global Privacy Control / opt-out preference signals where required (CA, CO, CT, TX, OR, MT, NJ, MN, MD and others) | 2023-2026 |
| UGC site with only a DMCA page | Add a TAKE IT DOWN removal channel (48 h) and renew the DMCA agent every 3 years | 2026-05-19 |
| Browsewrap "by using this site you agree" | Clickwrap: unchecked box or clear button next to a visible link, logged with version and timestamp | case-law trend |
| "WCAG 2.0/2.1 AA" in an accessibility statement | Target WCAG 2.2 AA; say what is known not to conform | WCAG 2.2, 2023-10 |
| One privacy policy for the website only | Policy also linked in the app and store listing; matches App Store labels, `PrivacyInfo.xcprivacy` and Play Data safety | Apple/Google rules |
| "Account deletion: email support" | In-app deletion path (Apple, Google) plus a web deletion link (Google) | Apple 2022, Google 2024-05-31 |

## Do this

### 1. The policy-writing method (follow in order; directive sections 15 and 20F)
1. **Establish what the product actually does.** Build the data inventory first (section 2). Read the code, manifests, build files and
   vendor dashboards; ask the owner only for what the code can't show (vendors, payments, jurisdictions, user ages).
2. **Identify the laws that truly apply**, not every law that exists: use the triage in section 3. Write down why each one applies
   or doesn't (thresholds, audience, data types, states served).
3. **Sort every clause** into: mandatory disclosure, optional commitment, prohibited term, prudent practice. Optional promises become
   legal obligations once published (FTC deception): make only promises the operation keeps.
4. **Draft accurate statements** that match implemented behavior. Never "we collect no data" when emails, auth records, payment
   metadata, server logs or user-requested transaction data exist. Describe the minimum actually collected, and why.
5. **Check FTC rules, state consumer-protection and privacy law, platform rules and sector rules** (GLBA, HIPAA, FERPA, COPPA).
6. **List the operational controls** each promise needs (deletion job, opt-out handling, GPC detection, retention timer, support inbox,
   vendor contracts) and hand them to engineering. A promise without a control is a future deception claim.
7. **Review consent and notice mechanics**: timing (before collection), placement, affirmative action, records kept.
8. **Accessibility and readability**: headings, short sentences, a layered summary, readable on a phone, WCAG 2.2 AA page.
9. **Mark owner decisions** as `[OWNER DECISION: ...]` (governing law, arbitration, refund window, retention periods, age limits).
10. **Keep a change history** (date, what changed, why) and list review triggers (section 7).

Policy workflow: product behavior -> data and transaction flows -> applicable duties -> disclosure checklist -> draft -> product-team
validation -> compliance gaps -> owner approval -> publish -> maintain.

### 2. Data inventory (fill before drafting; one row per data item)
| Data item | Source (user, device, vendor) | Purpose | Legal basis / notice | Where stored (region) | Shared with (vendor, role) | Retention + deletion method | Sensitive? |
|---|---|---|---|---|---|---|---|
Cover at least: account data (email, name, password hash, OAuth IDs), authentication/session records, payment metadata (processor
customer ID, last 4, billing country; never full card numbers if you use hosted fields), purchase/receipt records (tax law retention),
support messages, server and access logs (IP, user agent, timestamps), crash reports, analytics events, push tokens (FCM/APNs),
device identifiers, location, contacts, photos/files, user content, AI prompts/outputs and the model vendor, SSH keys/credentials
or API tokens the app stores (where, encrypted how, ever sent to you?), backups (how long deletions take to age out), email/SMS lists.
Then list every SDK and vendor (`references/policy-checklists.md` has the reconciliation commands).

### 3. Which laws apply (triage, US-only product)
| Trigger | Law / rule | Main policy consequence |
|---|---|---|
| Any consumer-facing statement | FTC Act s.5 + state UDAP (e.g., Ohio CSPA R.C. 1345, N.C.G.S. 75-1.1) | Every statement true and complete; no dark patterns |
| Under-13 audience or actual knowledge | COPPA (16 CFR 312) | Direct notice, verifiable parental consent, retention policy, security program |
| Residents of a privacy-law state and over its threshold | State comprehensive laws (CA, VA, CO, CT, ... see reference) | Notice, rights (access, delete, correct, port, opt-out), GPC, sensitive-data consent, contracts with processors |
| California, any commercial site collecting PII | CalOPPA (Bus. & Prof. 22575) (secondary sources) | Posted privacy policy; Do Not Track disclosure |
| Health data outside HIPAA | FTC HBNR, Washington MHMDA, Nevada SB 370, Connecticut/Vermont health provisions | Separate consumer health policy (WA), consent, breach notice |
| Recurring charges | ROSCA, state ARLs (CA Bus. & Prof. 17600+, NC 75-41, others), Apple 3.1.2 / Play Subscriptions | Terms before billing, express consent, easy cancel, reminders |
| Marketing email / SMS / calls | CAN-SPAM, TCPA + state mini-TCPAs (FL, OK, others) | Opt-out, sender ID, address; written consent for marketing texts |
| User-generated content | DMCA 512, Section 230, TAKE IT DOWN Act, state laws | Agent + notice procedure, repeat-infringer policy, 48 h NCII removal |
| Reviews, testimonials, influencers | 16 CFR 465, Endorsement Guides 16 CFR 255 | No fake/bought reviews, disclose material connections |
| Payments by card | PCI DSS 4.0.1 (contract), processor terms, Apple/Google IAP rules | Hosted fields; say who processes payments |
| Financial data / health records / student records | GLBA, HIPAA, FERPA | Stop: these change the whole analysis; involve counsel |
| Contract by click | E-SIGN (15 U.S.C. 7001) / UETA | Valid e-contracts; E-SIGN consumer consent only where law requires paper |
| Public website or app | ADA Title III (courts), Title II rule if government | WCAG 2.2 AA target, accessibility statement |
| Data breach | All 50 states + DC, PR, VI, Guam (reference lists how to check) | Incident plan; don't promise timelines you can't meet |
International rules (GDPR, UK, EU accessibility) only when the operation actually triggers them.

### 4. Document guide (must / don't, short; full lists in `references/policy-checklists.md`)
- **Privacy policy**: must list categories collected, sources, purposes, sharing (by category of recipient), sale/share/targeted ads
  yes/no, retention per category, rights and how to use them (with appeal where state law requires), GPC handling, children,
  security in honest terms, contact, effective date, change log. Don't: copy a competitor's, list data you don't collect "to be safe".
- **Terms of service**: who may use it, account rules, payment/renewal terms, license to the service and to user content, acceptable
  use (or linked AUP), termination, disclaimers and liability limits (with consumer-law carve-outs), dispute resolution, governing law, changes.
- **EULA** (installed software): license scope, restrictions, updates, third-party/open-source notices, export, termination. Apple's
  standard EULA applies unless you provide your own.
- **Refund, cancellation, subscription disclosures**: price, period, renewal, trial conversion date, how to cancel in-app and on the
  web, refund rule. For IAP, refunds go through Apple/Google: say so.
- **DMCA procedure, AUP/community guidelines, vulnerability disclosure, accessibility statement, cookie notice, retention/deletion
  policy, children's notice**: see checklists.

### 5. Consent and notice mechanics
- **Clickwrap over browsewrap.** Show the terms link next to the action button ("By creating an account you agree to the Terms"),
  or an unchecked checkbox; log user ID, document version, timestamp, and the UI shown. Courts enforce reasonably conspicuous notice +
  unambiguous assent; browsewrap alone often fails (general trend; no case cited here).
- **Notice of changes**: email or in-app notice for material changes before they take effect; get fresh consent for material
  privacy changes that apply to data already collected (FTC position). Never "check this page for changes" as the only mechanism.
- **Arbitration and class waivers**: generally enforceable under the FAA, but need clear assent, an opt-out window is prudent,
  small-claims carve-out, and mass-arbitration fees planning. `[OWNER DECISION]` and counsel review; many consumer products skip it.
- **Limitation of liability**: consumer protection laws void some limits (e.g., gross negligence, personal injury, statutory
  rights); add "to the extent permitted by law" and state-specific carve-outs instead of an absolute cap.
- **Governing law**: pick the owner's home state; don't claim to waive non-waivable consumer rights of the user's state.
- **Sensitive data, precise location, kids, health**: opt-in consent in most state laws; a just-in-time prompt beats a policy clause.

### 6. Privacy-first defaults and how to say them truthfully
- Defaults: no ad trackers or ad SDKs, no third-party analytics unless needed (prefer self-hosted, aggregated), minimal permissions,
  minimal SDKs, short retention, deletion that actually deletes (and ages out of backups), user data kept apart from business analytics.
- When NOT to add a dependency: don't add a consent-management platform, analytics SDK or crash reporter just because a template
  mentions one. If there are no trackers, no cookie banner is needed for them; say so plainly instead.
- Truthful phrasing: "We don't sell your personal information or share it for cross-context behavioral advertising." Only if true
  across all SDKs. "We don't use advertising trackers." Not "we never collect anything." "Server logs (IP address, time, request)
  are kept for [30] days for security, then deleted."

### 7. Review triggers (put them in the change history section)
New SDK or vendor, new data type or permission, new state crosses a threshold, payments or subscriptions added, AI features
added, kids' audience, ads, sale/share of data, new country, any law in `references/state-privacy-laws.md` taking effect, store
policy update, and at least every 12 months (CCPA requires annual privacy policy update).

### 8. What Claude must say
- State once, plainly: this is a drafting aid, not legal advice, and Claude is not a lawyer or law firm.
- Recommend a licensed attorney before publishing when: children's data or a kids' audience, health or financial data, selling or
  sharing data for ads, biometrics or precise location, large scale (any state threshold likely met), regulated sectors (health,
  finance, education, insurance, telecom), arbitration clauses, or a regulator letter or lawsuit already exists.
- Never claim a document is "fully compliant" or "lawyer-approved". Say what was checked, on what date, against which sources.

## Security
Policy text creates security obligations; the controls are in **rc-security-engineering**.
- Don't promise "bank-level" or "military-grade" security, or "your data is never accessible to us", unless technically true
  (e.g., end-to-end encryption with keys only on the device). FTC treats false security claims as deception.
- If the app stores credentials (SSH keys, API tokens): say where they live (device keystore, server), whether they leave the device,
  and how they are protected. Never imply a key is "never stored" when it is.
- Breach notice: describe the process, not a fixed promise ("within 24 hours") you can't meet; state law sets the clock.
- Vulnerability disclosure: publish `/.well-known/security.txt` and a safe-harbor policy (rc-security-engineering has the format).
- Children's data: the written security program (312.8) is mandatory for COPPA operators since 2026-04-22.
- Don't put personal data of real people, contact emails of employees, or internal hostnames in public policy repos.

## Performance & quality
- Readability: aim for grade 8-10 (check with a readability tool), sentences under ~25 words, defined terms used sparingly,
  layered format (short summary up top, details below). Mobile-first: no PDFs (Google Play forbids PDF policies).
- Accessibility of the policy page: semantic headings, links that say where they go, contrast and zoom per WCAG 2.2 AA.
- Consistency checks (run before every release): privacy policy vs App Store privacy label vs `PrivacyInfo.xcprivacy` vs Play Data
  safety form vs actual network traffic. Any mismatch is a store-rejection and deception risk.
- Version every document (date + version number); keep old versions retrievable; log user acceptance per version.

## Common mistakes in AI-written/AI-assisted legal work
- Inventing statutes, sections, cases, effective dates or thresholds. Every cite must be checked on an official source this session; mark the rest (unverified).
- Copying GDPR language ("lawful basis", "data controller", "DPO") into a US-only policy where it doesn't apply, or the reverse.
- Claiming "we comply with all applicable laws" or "CCPA compliant" when the business is below thresholds or hasn't built the controls.
- "We collect no personal data" in an app with accounts, logs, crash reports or push notifications.
- Listing rights but no working way to use them (no form, no inbox, no verification method, no response clock).
- Citing the vacated FTC click-to-cancel rule or the vacated TCPA one-to-one rule as current law.
- Treating Ohio or North Carolina as having a comprehensive privacy law (they don't as of 2026-10), or forgetting their breach laws.
- Liability caps or "no refunds ever" with no consumer-law carve-out; class waivers buried in paragraph 37 without assent.
- Templates with fake company names, addresses or placeholder emails that ship to production. Use `[OWNER DECISION]` markers.
- Generic AI prose ("we are committed to your privacy", "navigate", "robust"). Use plain American English: short, specific, true.
- Over-quoting statutes; long quotes. Paraphrase and cite.
- Forgetting the app-store layer: the policy is fine but the Play Data safety form says "no data collected" or the App Store label omits crash data.

## Before you ship
- [ ] Data inventory complete and checked against the code, SDK list and network traffic (`references/policy-checklists.md`).
- [ ] Applicable-law memo: which laws apply and why, with dates and sources; unverified items flagged to the owner.
- [ ] Every promise has an operational control and an owner (deletion job, GPC, opt-out, support inbox, retention timer).
- [ ] Privacy policy linked in app, website footer, App Store Connect and Play Console; public, non-geofenced, not a PDF.
- [ ] Store disclosures (Apple label, `PrivacyInfo.xcprivacy`, Play Data safety, account-deletion web link) match the policy.
- [ ] Subscriptions: terms shown before purchase, express consent captured, cancel path in-app and on the web, reminders scheduled.
- [ ] Clickwrap in sign-up with acceptance logging; change-notice process defined.
- [ ] DMCA agent registered (renew every 3 years), TAKE IT DOWN channel live if UGC, security.txt published.
- [ ] Owner decisions resolved; counsel review where section 8 says so; "not legal advice" note given to the owner.
- [ ] Change history entry, effective date, version, and next review date set.

## Sources
All accessed 2026-10-10 unless noted.
- https://www.federalregister.gov/documents/2026/02/12/2026-02866/revision-of-the-negative-option-rule-withdrawal-of-the-cars-rule-removal-of-the-non-compete-rule-to - negative option rule restored; cites Custom Commc'ns v. FTC, 142 F.4th 1060
- https://www.federalregister.gov/documents/2026/03/13/2026-04952/rule-concerning-the-use-of-prenotification-negative-option-plans - ANPRM, comments closed 2026-04-13
- https://www.ecfr.gov/current/title-16/chapter-I/subchapter-C/part-312 - COPPA Rule 312.5, 312.8, 312.10 (read via eCFR API, 2026-10-01 text)
- https://www.ecfr.gov/current/title-47/chapter-I/subchapter-B/part-64/subpart-L/section-64.1200 - TCPA consent and revocation text
- https://www.ecfr.gov/current/title-28/chapter-I/part-35/subpart-H/section-35.200 - ADA Title II web rule dates
- https://www.ada.gov/resources/2024-03-08-web-rule/ - Title II compliance dates after 2026-04-20 interim final rule
- https://www.ftc.gov/news-events/news/press-releases/2026/05/ftc-begins-enforcing-take-it-down-act - TAKE IT DOWN 48-hour removal (2026-05-19)
- https://www.ftc.gov/news-events/news/press-releases/2026/05/ftc-sends-warning-letters-companies-about-compliance-take-it-down-act - warning letters 2026-05-20, $53,088 per violation
- https://www.govinfo.gov/content/pkg/PLAW-119publ12/html/PLAW-119publ12.htm - TAKE IT DOWN Act text (approved 2025-05-19; process within 1 year; 48 hours)
- https://www.ftc.gov/news-events/news/press-releases/2026/09/ftc-withdraws-obsolete-policy-statement - rescinds the 2021 HBNR health-app statement only
- https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule - COPPA: effective 2025-06-23, compliance 2026-04-22
- https://www.courtlistener.com/opinion/10625881/custom-communications-inc-v-federal-trade-commission/ and https://www.courtlistener.com/opinion/10320775/insurance-marketing-coalition-limited-v-fcc/ - decision dates of the two vacaturs
- https://www.supremecourt.gov/docket/docketfiles/html/public/25a1390.html - CCIA v. Paxton: application to vacate the Fifth Circuit stay denied 2026-07-06
- https://developer.apple.com/news/?id=sg176nne - Apple: new Texas Apple Accounts subject to SB 2420 (2026-06-03)
- https://www.ftc.gov/business-guidance/resources/can-spam-act-compliance-guide-business - CAN-SPAM requirements, penalty
- https://www.ftc.gov/business-guidance/resources/consumer-reviews-testimonials-rule-questions-answers - fake reviews rule
- https://www.ftc.gov/news-events/news/press-releases/2026/02/ftc-issues-coppa-policy-statement-incentivize-use-age-verification-technologies-protect-children - age verification statement
- https://www.law.cornell.edu/uscode/text/15/8403 - ROSCA
- https://www.law.cornell.edu/uscode/text/15/7001 - E-SIGN consumer consent
- https://www.ecfr.gov/current/title-37/chapter-II/subchapter-A/part-201/section-201.38 - DMCA agent 3-year expiry
- https://www.w3.org/TR/WCAG22/ - WCAG 2.2 Recommendation status
- https://cppa.ca.gov/regulations/ and https://www.cppa.ca.gov/regulations/cpi_adjustment.html - CCPA regulations, threshold
- https://legislature.vermont.gov/bill/status/2026/S.71 - Vermont Act 145 (signed 2026-06-16; effective 2028-01-01 per act summary)
- https://legis.la.gov/legis/BillInfo.aspx?s=26RS&b=SB386&sbi=y - Louisiana Act 502, effective 2027-01-01; enrolled text https://www.legis.la.gov/Legis/ViewDocument.aspx?d=1459766 (thresholds)
- http://www.oklegislature.gov/BillInfo.aspx?Bill=SB546&Session=2600 - Oklahoma SB 546 approved 2026-03-20; enrolled text at webserver1.lsb.state.ok.us (eff. 2027-01-01, thresholds)
- https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB351-enr.pdf - Alabama HB 351 enrolled (eff. 2027-05-01, thresholds)
- https://delcode.delaware.gov/title6/c012d/index.html - Delaware thresholds now and from 2027-01-01
- https://codes.ohio.gov/ohio-revised-code/section-1349.19 and https://codes.ohio.gov/ohio-revised-code/chapter-1354 - Ohio breach law, safe harbor
- https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_75/GS_75-65.html - NC breach notice; GS_75-41 auto-renewal
- https://developer.apple.com/app-store/review/guidelines/ - Apple 5.1.1, 5.1.2, 3.1.2, 1.3 (last updated 2026-06-08)
- https://support.google.com/googleplay/android-developer/answer/10144311 - Play User Data policy
- https://support.google.com/googleplay/android-developer/answer/13327111 - Play account deletion
- https://support.google.com/googleplay/android-developer/answer/9900533 - Play subscriptions policy
- Leads only (law-firm/vendor pages, not sole sources): Barnes & Thornburg and DWT on CA ARL (leginfo blocked, HTTP 403); Perkins
  Coie on AB 566 / AB 1043; Holland & Knight and Cooley on NetChoice v. Bonta; Loeb & Loeb on Utah HB 498; JD Supra on Louisiana HB 977.
