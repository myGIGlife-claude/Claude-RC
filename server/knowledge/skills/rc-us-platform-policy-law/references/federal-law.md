# Federal rules that shape app and website policies  (as of 2026-10-10)

> Drafting aid, not legal advice. "Verified" = read on an official source (eCFR, Federal Register, ftc.gov, ada.gov,
> copyright.gov, Cornell LII statute text) on 2026-10-10. Everything else is marked.

## FTC Act Section 5 (15 U.S.C. 45)
- **Deception**: a material representation or omission likely to mislead a reasonable consumer. Every sentence in a privacy policy,
  store label, onboarding screen or marketing page is a representation. Broken promises (deleting data, not sharing, security) are the
  classic privacy case.
- **Unfairness**: substantial injury, not reasonably avoidable, not outweighed by benefits. Used for weak security, sharing sensitive
  data (health, location) without consent, and dark patterns.
- Enforcement themes 2025-2026 (ftc.gov listings; details not opened): children's privacy (Disney order approved 2025-12-31,
  Sendit 2025-09, Apitor), sharing personal data with third parties contrary to promises (Match/OkCupid action 2026-03-30), student data
  security (Illuminate final order 2026-06-05), fake reviews warning letters (2025-12), TAKE IT DOWN warning letters (2026-05),
  AI claims (proposed policy statement on AI accuracy, Federal Register 2026-07-07). (Case names here are agency matters listed on
  ftc.gov, not court holdings; read the order before citing any term.)
- No general federal privacy statute exists as of 2026-10 (no comprehensive bill enacted; unverified beyond searches).

## COPPA (15 U.S.C. 6501-6506; 16 CFR 312) - verified text
- Applies to operators of child-directed sites/apps (under 13) and general-audience operators with actual knowledge of a child user;
  also ad networks/plugins with actual knowledge.
- Amended Rule published 2025-04-22 (90 FR 16918), effective 2025-06-23; compliance date for most provisions **2026-04-22**
  (except 312.11(d)(1), (d)(4) and (g), which applied earlier). Key duties now in the CFR:
  - Separate verifiable parental consent before disclosing a child's data to third parties (including for targeted ads) unless
    disclosure is integral to the service (312.5(a)(2)).
  - Written information security program: named coordinator, annual risk assessment, safeguards, testing, annual review; written
    assurances from vendors (312.8).
  - Written data retention policy (purposes, business need, deletion timeframe), no indefinite retention, published in the online
    notice (312.10).
  - Broader "personal information" (includes biometric identifiers per the 2025 amendments; read 312.2 for the exact list).
- FTC enforcement policy statement (2026-02-25): operators may collect data solely to determine age without prior consent if they
  limit use, delete promptly, and secure it; FTC plans a COPPA review on age verification. The statement stays in force until the
  FTC amends the Rule or withdraws it. The FTC's 2026-09-09 "Withdraws Obsolete Policy Statement" release rescinded the 2021 Health
  Breach Notification policy statement on health apps (superseded by the 2024 HBNR amendments), not this one (ftc.gov, read 2026-10-10).
- Teens (13-17) are not covered by COPPA; state laws cover them (see state-privacy-laws.md).

## Subscriptions and negative options
- **ROSCA** (15 U.S.C. 8403), verified: online negative-option sales need (1) clear disclosure of material terms before billing info,
  (2) express informed consent before charging, (3) simple mechanisms to stop recurring charges. FTC uses it plus Section 5.
- **FTC Negative Option Rule**: the 2024 "click-to-cancel" amendments were vacated (*Custom Commc'ns, Inc. v. FTC*, 142 F.4th 1060
  (8th Cir. July 8, 2025), No. 24-3137; cite verified 2026-10-10 in 91 FR 6507 n.3, decision date on CourtListener). FTC recodified the
  original prenotification-plan rule (16 CFR 425, source note "91 FR 6509, Feb. 12, 2026") effective 2026-02-12 and issued an ANPRM
  2026-03-13 (91 FR 12318, comments closed 2026-04-13). Treat click-to-cancel as best practice and as state law where enacted, not as
  a federal rule.
- **State auto-renewal laws** (examples): California Bus. & Prof. 17600-17606 (amended by AB 2863, operative 2025-07-01: express
  affirmative consent, keep consent proof 3 years or 1 year after termination if longer, annual reminder, cancel online/same medium,
  limits on save offers; secondary, leginfo blocked HTTP 403); North Carolina 75-41 (verified: disclosure, how to cancel, written
  notice 15-45 days before renewal for terms over 60 days, violation voids the clause); Louisiana HB 750 (2026, Act 830, signed
  2026-06-09, effective 2027-01-01, "cancellation of automatic renewal subscriptions"; bill page read, act text not read); many
  others (NY, VA, CO, MN, TN...). Ohio: no specific statute found (unverified).
- Practical floor for any US subscription: price, billing period, renewal, trial end date and post-trial price shown next to the buy
  button; unchecked consent or a clearly labeled button; confirmation email with terms and how to cancel; cancel online in as few
  steps as signing up; reminder before annual renewals and before free-trial conversion.

## Marketing messages
- **CAN-SPAM** (15 U.S.C. 7701+; 16 CFR 316), verified on ftc.gov: accurate headers, honest subject, identify as an ad, valid postal
  address, clear opt-out working 30 days after sending, honored within 10 business days, no fee or extra data to opt out; you stay liable
  for vendors. Penalty up to $53,088 per email. Transactional/relationship messages are exempt from most duties.
- **TCPA** (47 U.S.C. 227; 47 CFR 64.1200), verified text: marketing calls/texts with an autodialer or artificial/prerecorded voice
  need prior express written consent (signed, may be electronic under E-SIGN; disclosure that consent is not a condition of purchase).
  Revocation by any reasonable means honored within 10 business days (64.1200(a)(10)); one confirmation text allowed ((a)(12)). One-to-one
  consent rule vacated (*Insurance Marketing Coalition Ltd. v. FCC*, 127 F.4th 303 (11th Cir. Jan. 24, 2025), CourtListener; current
  CFR text has no such requirement). State mini-TCPAs (Florida, Oklahoma, others)
  are stricter on timing and consent (unverified details). National and internal do-not-call lists still apply.

## Reviews, endorsements, ads
- **16 CFR 465** (verified, effective 2024-10-21): no fake or AI-fabricated reviews/testimonials, no buying reviews conditioned on
  sentiment, disclose insider reviews, no company-controlled "independent" review sites, no review suppression by threats, no fake
  social-media indicators. Civil penalties for knowing violations.
- **Endorsement Guides** (16 CFR 255, revised 2023, secondary): disclose material connections (paid, free product, family, employee)
  clearly in the endorsement itself; platform tools alone may be insufficient.
- Affiliate links: disclose near the link, not only in a footer.

## Health, finance, education (know when they trigger; then involve counsel)
- **HIPAA**: covered entities (providers, plans, clearinghouses) and their business associates. A consumer wellness app is usually
  not covered unless it works for a covered entity; then the **FTC Health Breach Notification Rule** (16 CFR 318, amended 2024, verified:
  notice within 60 days; FTC notice for 500+) applies to personal health records, plus state health-data laws.
- **GLBA**: financial institutions (broadly: lenders, payments, some fintech). Privacy notice and Safeguards Rule (16 CFR 314).
- **FERPA**: education records held by schools receiving federal funds; ed-tech vendors act as "school officials" under contract.
  COPPA school-authorization rules also apply for under-13.

## Electronic signatures and records
- **E-SIGN** (15 U.S.C. 7001), verified: e-signatures and records valid in interstate commerce. Where a law requires information be
  given to a consumer in writing, electronic delivery needs E-SIGN consumer consent: disclosures (right to paper, how to withdraw,
  scope, hardware/software needs) and consent given electronically in a way that shows the consumer can access the format.
- **UETA** adopted by 49 states plus DC (New York has its own ESRA) (secondary).
- Clickwrap acceptance is an e-signature for ordinary terms; keep the record (who, when, which version, what screen).

## User content and copyright
- **DMCA 512(c)** safe harbor (17 U.S.C. 512): designate an agent with the Copyright Office (online only) and on your site; the
  designation **expires 3 years** after registration unless renewed (37 CFR 201.38(c)(4), verified). Also need: expeditious takedown on
  a compliant notice, notice to the user, counter-notice process (restore in 10-14 business days unless suit filed), and a reasonably
  implemented repeat-infringer termination policy (512(i)). No knowledge/red-flag awareness; no direct financial benefit with control.
- **Section 230** (47 U.S.C. 230): platforms generally aren't treated as the publisher of users' content and may moderate in good faith.
  Not a shield for: federal criminal law, intellectual property (use DMCA), ECPA, sex-trafficking (FOSTA), your own content or design
  choices some courts treat separately. Product-design and age-verification claims are an active litigation area (unverified details).
- **TAKE IT DOWN Act** (Pub. L. 119-12, approved 2025-05-19; platforms had 1 year to set up the process; FTC enforcing since
  2026-05-19, verified on govinfo and ftc.gov): covered platforms provide a clear removal-request process for non-consensual intimate
  images (including AI-generated) and remove them and known identical copies within 48 hours of a valid request. Violations are
  treated as FTC rule violations (civil penalties, $53,088 per violation in the FTC's 2026-05-20 letters).

## Accessibility
- **ADA Title III** (private businesses, "public accommodations"): DOJ never issued a web rule; it withdrew its pending rulemakings
  (secondary). Courts split on whether web-only businesses are covered; plaintiffs file thousands of website suits a year using WCAG 2.x
  AA as the yardstick (secondary). Practical target: WCAG 2.2 AA, plus an accessibility statement and a feedback channel.
- **ADA Title II** (state/local government, and vendors building for them): 28 CFR 35.200, WCAG 2.1 AA, compliance 2027-04-26 (50,000+
  population) and 2028-04-26 (smaller, special districts) after the 2026-04-20 interim final rule (verified, ada.gov and eCFR).
- **WCAG 2.2**: W3C Recommendation (first 2023-10-05; current edition 2024-12-12), 9 new success criteria, 4.1.1 Parsing removed (verified).
- Section 508 applies to federal agencies (and what you sell them): WCAG 2.0 AA via the 2017 refresh (secondary).

## Payments
- **PCI DSS 4.0.1** (PCI SSC, released 2024-06): the only active version since 2024-12-31; future-dated requirements mandatory
  2025-03-31 (payment-page script inventory 6.4.3, tamper detection 11.6.1). Contractual through the card brands and your acquirer,
  not a statute. Use hosted fields/checkout so you stay in SAQ A scope; details in rc-security-engineering.
- **Apple/Google payments**: digital goods in apps generally use In-App Purchase / Play Billing (with jurisdiction-specific
  exceptions such as US link-outs after court rulings and alternative billing programs; check current Apple 3.1.1/3.1.3 and Play
  Payments policy before relying, unverified). Physical goods and services use a normal processor.

## Sources (accessed 2026-10-10)
- https://www.ecfr.gov/ (API reads of 16 CFR 312, 318, 425, 465; 28 CFR 35.200; 37 CFR 201.38; 47 CFR 64.1200, all as of 2026-10-01)
- https://www.federalregister.gov/documents/2026/02/12/2026-02866/ ; https://www.federalregister.gov/documents/2026/03/13/2026-04952/
- https://www.ftc.gov/business-guidance/resources/can-spam-act-compliance-guide-business
- https://www.ftc.gov/news-events/news/press-releases/2026/05/ftc-begins-enforcing-take-it-down-act ; .../2026/05/ftc-sends-warning-letters-companies-about-compliance-take-it-down-act
- https://www.govinfo.gov/content/pkg/PLAW-119publ12/html/PLAW-119publ12.htm (TAKE IT DOWN Act text)
- https://www.ftc.gov/news-events/news/press-releases/2026/09/ftc-withdraws-obsolete-policy-statement ; .../2026/02/ftc-issues-coppa-policy-statement-incentivize-use-age-verification-technologies-protect-children
- https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule (effective and compliance dates)
- https://www.courtlistener.com/opinion/10625881/custom-communications-inc-v-federal-trade-commission/ ; https://www.courtlistener.com/opinion/10320775/insurance-marketing-coalition-limited-v-fcc/
- https://www.ftc.gov/legal-library/browse/rules/childrens-online-privacy-protection-rule-coppa
- https://www.legis.la.gov/legis/BillInfo.aspx?s=26RS&b=HB750&sbi=y (Louisiana auto-renewal act, 2027-01-01)
- https://www.ftc.gov/news-events/topics/protecting-consumer-privacy-security/privacy-security-enforcement (search listing only)
- https://www.law.cornell.edu/uscode/text/15/8403 ; https://www.law.cornell.edu/uscode/text/15/7001
- https://www.copyright.gov/dmca-directory/
- https://www.ada.gov/resources/2024-03-08-web-rule/ ; https://www.w3.org/TR/WCAG22/
- https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_75/GS_75-41.html
- https://blog.pcisecuritystandards.org/just-published-pci-dss-v4-0-1 (search result; v4.0.1 limited revision)
