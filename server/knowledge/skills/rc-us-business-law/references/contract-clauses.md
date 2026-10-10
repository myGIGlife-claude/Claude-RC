# Contract law points and clause checklists (as of 2026-10)

General US contract principles; the governing state's statutes and cases decide. Statute cites checked 2026-10-10 unless marked.
No case law is stated here as authority: research the governing state's cases fresh for each matter.

## Core law
- **Formation**: offer, acceptance, consideration, mutual assent, capacity, legality. Clickwrap (affirmative "I agree" with conspicuous
  terms) is far safer than browsewrap. Sign in the entity's name with title; confirm signer authority.
- **Statute of frauds**: goods $500+ need a signed record (UCC 2-201: NC G.S. 25-2-201, Ohio R.C. 1302.04); also real-estate interests,
  contracts not performable within a year, guaranties (state versions differ). NC non-competes: written and signed (G.S. 75-4).
- **E-signatures**: E-SIGN 15 U.S.C. §7001 (electronic signature/record cannot be denied effect solely for being electronic; consumer
  consent rules when a law requires written disclosures); UETA (NC G.S. 66-311 et seq.; Ohio R.C. Ch. 1306). Exclusions (wills, some
  notices) exist: check.
- **UCC Art. 2 vs common law**: goods -> UCC (implied warranties of merchantability/fitness, gap fillers, battle of the forms 2-207,
  perfect tender, 4-year limitations default 2-725); services/software-as-service/IP licenses -> common law. Mixed: predominant purpose,
  and in states with the 2022 amendments the hybrid-transaction rule (NC G.S. 25-2-102, S.L. 2025-25; Ohio HB 195, eff. 2026-10-06).
- **Parol evidence and integration**: a merger clause makes the writing the full deal; list exhibits/SOWs/order forms and an order of precedence.
- **Conditions vs covenants**: say "condition precedent" when non-occurrence should excuse performance; otherwise it is a promise.
- **Reps, warranties, covenants**: reps = facts at signing (misrepresentation claims), warranties = promises facts are true (breach),
  covenants = future conduct. Say how long each survives.
- **Indemnity**: defines who pays third-party claims (and sometimes direct losses). Courts read indemnity for the indemnitee's own
  negligence narrowly; many states require express, conspicuous language. Specify defense control, notice, settlement consent, cap and
  carve-outs, and whether it covers first-party claims. Insurance must back it.
- **Limitation of liability / consequential damages**: generally enforceable between businesses, but not for fraud, willful misconduct,
  and often gross negligence (state law); UCC 2-719: exclusive remedy that fails of its essential purpose falls away; consumer
  personal-injury limits on goods are prima facie unconscionable. Carve out confidentiality, IP infringement indemnity, data breach
  (or give a super-cap), and payment obligations.
- **Liquidated damages**: enforceable if actual damages are hard to estimate at signing and the amount is a reasonable forecast; a
  penalty is void (UCC 2-718 for goods). Don't label a penalty "liquidated".
- **Assignment / change of control**: default rules allow assignment of most rights; draft anti-assignment plus change-of-control
  triggers; carve out a sale of the whole business.
- **Force majeure**: only what the clause lists (plus common-law impossibility/impracticability, UCC 2-615). Name pandemics, government
  orders, supplier failure, cyberattack if intended; require notice and mitigation; payment obligations usually not excused.
- **Termination and cure**: notice content, cure period, termination for convenience (with fee?), effects (data return, transition,
  survival list).
- **Dispute resolution**: FAA 9 U.S.C. §2 makes arbitration agreements enforceable except on general contract defenses; sexual
  harassment/assault claims can't be forced to arbitration at the claimant's election (9 U.S.C. §§401-402). Class waivers in arbitration
  are generally enforceable under the FAA; check consumer and employment overlays and mass-arbitration fee risk.
- **Governing law and venue**: courts honor a reasonable chosen law with a substantial relationship unless it violates the forum's
  fundamental policy (non-competes, franchise, consumer, wage laws often override). Forum-selection clauses are generally enforced
  between businesses; some states void out-of-state venue for in-state construction or consumer contracts (verify per state).
- **Unconscionability**: procedural (adhesion, hidden terms) + substantive (one-sided terms); courts sever or refuse enforcement.
- **Consumer overlays**: FTC Act §5 (unfair/deceptive), state UDAP laws (NC G.S. 75-1.1, treble damages G.S. 75-16; Ohio Consumer Sales
  Practices Act R.C. 1345.01-1345.13: 1345.02 deceptive acts, 1345.03 unconscionable acts, 1345.09 remedies), ROSCA and state
  auto-renewal laws for subscriptions (FTC click-to-cancel amendments vacated by the 8th Circuit 2025-07-08, *Custom Communications v.
  FTC*, No. 24-3137, law-firm reports; FTC recodified the pre-2024 rule 2026-02-12), Magnuson-Moss for consumer product warranties.
- **Non-competes**: no federal ban (FTC rule set aside; removal notice 2026-02-12, 91 FR 6507). FTC still pursues overbroad ones under §5
  (Gateway Services: consent order finalized 2025-11-26). **NC**: written and signed by the restrained party (G.S. 75-4); courts require
  reasonable time/territory, valuable consideration (new employment or new consideration for existing employees), legitimate business
  interest; strict blue-pencil (courts strike severable terms, don't rewrite; case law not retrieved this session). H 973 (Uniform
  Restrictive Employment Agreement Act) has sat in House Rules since 2025-04-14 (ncleg.gov, 2026-10-10). **Ohio**: common-law
  reasonableness test from a 1975 Ohio Supreme Court decision; courts may modify overbroad terms (case not retrieved this session);
  **SB 11** (136th GA) would ban most worker non-competes: in Senate committee, never reported out, not enacted (legislature.ohio.gov
  status page, 2026-10-10).
  Many states have income thresholds or bans (CA, MN, others): check the worker's state. Sale-of-business non-competes are treated more leniently.
- **Confidentiality / trade secrets**: DTSA (18 U.S.C. §1836) plus state UTSA (NC Trade Secrets Protection Act G.S. 66-152 et seq.;
  Ohio R.C. 1333.61-1333.69). Include the **§1833(b) immunity notice** in any employee/contractor/consultant
  agreement governing confidential information, or lose exemplary damages and fees against that person. Don't bar whistleblowing to
  agencies (SEC Rule 21F-17, NLRA concerns for non-supervisory employees).
- **IP ownership**: copyright vests in the author; "work made for hire" covers employees in scope of employment, and contractors only
  for nine statutory categories in a signed writing (software usually isn't one). Always add a present assignment ("hereby assigns"),
  moral-rights waiver, further-assurances and license-back of pre-existing IP. Patents: assignment, not WMFH.
- **Open source**: inventory licenses (SBOM); permissive (MIT, BSD, Apache-2.0: notices, Apache patent terms) vs copyleft (GPL: source
  obligations on distribution; AGPL: also network use). Contract reps on OSS use and no copyleft contamination of deliverables.

## Clause checklist per contract type
Format: **Ask the owner** (business decisions) / **High-risk clauses**.

### LLC operating agreement
- Ask: members, contributions (cash/property/services), percentages, member- or manager-managed, who signs/borrows, major decisions
  needing unanimous or supermajority vote, distributions (tax distributions?), salaries/guaranteed payments, capital calls, new members,
  transfers and right of first refusal, buy-sell triggers (death, disability, divorce, departure, deadlock) and valuation/funding,
  non-compete/non-solicit among members, dissolution.
- High-risk: fiduciary-duty waivers (DE allows broad elimination, but not the implied covenant of good faith, §18-1101(c)); drag/tag;
  forced capital calls with dilution; deadlock without tie-breaker; tax partnership-representative designation; S-election "one class of stock" traps.

### Independent contractor agreement
- Ask: scope and deliverables, fees and expenses, schedule, tools, exclusivity, IP ownership, confidentiality, insurance, term/termination.
- High-risk: control language that makes the worker an employee (set hours, training, supervision); non-competes on contractors;
  IP clause without assignment; DTSA notice missing; misclassification indemnity that is unenforceable against wage claims; 1099/W-9 collection.

### Vendor / supplier / reseller
- Ask: products, pricing and changes, minimums, territory and exclusivity, delivery terms (Incoterms/UCC), acceptance and returns,
  warranties, marketing/trademark use, MAP pricing, termination and sell-off.
- High-risk: exclusivity without performance minimums, battle of forms (whose terms govern POs), warranty disclaimers that are not
  conspicuous ("merchantability" must be mentioned, UCC 2-316), antitrust (resale price, territorial limits), recall cost allocation.

### SaaS / subscription
- Ask: plans, fees, auto-renewal, price changes, SLA and credits, support, data location, sub-processors, acceptable use, suspension,
  exit/data export, customer type (consumer vs business).
- High-risk: limitation of liability vs data breach (super-cap), indemnity for IP infringement, security commitments you can't meet,
  auto-renewal notices (state laws, ROSCA for consumers), unilateral amendment clauses, DPA/privacy terms (see a privacy skill/lawyer).

### MSA + SOW
- Ask: which terms live in the MSA vs each SOW, change-order process, acceptance tests, milestones, rates, key personnel, non-solicit.
- High-risk: order-of-precedence gaps, SOW silently overriding the cap, acceptance by deemed silence, IP ownership of reusable tools.

### NDA
- Ask: mutual or one-way, purpose, what is confidential (marking?), term and survival (trade secrets: as long as secret), residuals clause.
- High-risk: no DTSA notice when signed by individuals, overbroad definitions, no carve-outs (public, already known, independently
  developed, legally compelled), injunctive-relief clause, non-solicit hidden in an NDA.

### IP assignment (founder, employee, contractor)
- Ask: what IP, pre-existing IP list, consideration.
- High-risk: future-assignment language ("agrees to assign" vs "hereby assigns"); state limits on assigning employee inventions made on
  own time (NC G.S. 66-57.1 voids assignment of inventions made entirely on the employee's own time without employer resources,
  unless they relate to the employer's business or R&D or result from the employee's work; Ohio has no such statute found);
  missing moral-rights waiver and power of attorney.

### Asset purchase
- Ask: assets and excluded assets, assumed liabilities, price and adjustments, escrow/holdback, earn-out, employees, seller non-compete,
  transition services, bulk-sale/tax clearance (state certificates), licenses that need consent.
- High-risk: successor liability, unassignable contracts/permits, sales tax on the sale, reps survival and caps, seller's personal guaranty.

### Demand, breach and cure notices
- Ask: which contract and clause, what breach, cure period, desired remedy, deadline.
- High-risk: sending to the wrong notice address/method (follow the notices clause exactly), waiver by continued performance, threats
  that are extortionate or defamatory, missing reservation of rights, accelerating debt without contractual right. Don't claim it was
  delivered without proof (certified mail receipt, courier tracking, email if allowed).
