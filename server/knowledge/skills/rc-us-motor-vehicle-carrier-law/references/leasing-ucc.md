# Fleet leasing, lease-to-own, repossession (as of 2026-10-10)

Statute numbering verified 2026-10-10 on codes.ohio.gov and ncleg.gov. Ohio HB 195 (136th GA) amended Chapter 1310 sections effective
2026-10-06 (2022 UCC amendments; "record" replaces "writing" in places): read the current text, not a pre-October-2026 copy.
NC enacted the same 2022 amendments by S.L. 2025-25 Part VIII (new Article 12 controllable electronic records, conforming changes to
Articles 1, 2A and 9, e.g. 25-2A-504 by s. 112), effective 2025-10-01.

| UCC section | Ohio R.C. | North Carolina G.S. | Topic |
|---|---|---|---|
| 1-203 | 1301.203 | 25-1-203 | Lease vs security interest |
| Art. 2A | Chapter 1310 (1310.01 = 2A-103) | Chapter 25, Art. 2A | True leases |
| 2A-504 | 1310.50 | 25-2A-504 | Liquidated damages: reasonable in light of anticipated harm |
| 2A-109 | 1310.07 | 25-2A-109 | "At will"/insecurity acceleration = good-faith belief only |
| 9-609 | 1309.609 | 25-9-609 | Repossession: judicial process or self-help without breach of peace |
| 9-602 | 1309.602 | 25-9-602 | Breach-of-peace duty cannot be waived |
| 9-610 to 9-615 | 1309.610-.615 | 25-9-610 to -615 | Disposition, notice, application of proceeds |
| Title perfection | 4505.13 | 20-58 to 20-58.10 | Lien noted on certificate of title |

## Step 1: characterize the deal (UCC 1-203)
- Security interest if the lessee cannot terminate AND any of: term >= remaining economic life; lessee must renew for economic life or
  must buy; renewal for economic life or purchase option for no or nominal added consideration.
- Not decisive alone: present value of payments >= value; lessee bears risk of loss, pays taxes/insurance/maintenance; fixed-price
  purchase option that is not nominal.
- A "lease-to-own" with nominal buyout is almost always a secured sale. Consequences: perfect by title notation (lessor/lender named as
  lienholder); Article 9 default and disposition rules; possible state retail-installment or consumer-credit laws if the buyer is an
  individual using it for personal purposes; usury analysis of the implicit rate (unverified per state).
- True lease (meaningful residual risk on the lessor, FMV option): Article 2A; lessor holds title; still consider UCC-1 precautionary
  filing and lessor named as owner on title.

## Step 2: who carries DOT responsibility
- Bare equipment lease (no driver) to a carrier from a business principally engaged in leasing: Part 376 exempt except identification
  (376.21(c)); the lessee operating the truck is the motor carrier (USDOT, insurance, driver qualification).
- Equipment with driver (owner-operator) leased to an authorized carrier: written lease meeting 376.12; carrier has exclusive possession
  and complete responsibility for operation during the lease; carrier's USDOT on the vehicle; receipts at start and end (376.11(b)).
- 376.12 required items: parties and signatures; specific duration; exclusive possession and responsibility; compensation on the face
  of the lease; payment within 15 days after delivery documents; lessor's right to see the rated freight bill when pay is a percentage;
  itemized charge-backs; no forced purchase of products or services from the carrier; who provides insurance and the carrier's legal
  obligation; escrow terms (amount, uses, accounting, interest, return within 45 days of termination, 376.12(k)); copies to each party and in the vehicle.
- 376.12(c)(4): those control clauses don't decide employee vs contractor status.
- Private-carrier lessees and leases between authorized carriers have partial exemptions (376.22, 376.26).

## Step 3: clause-risk list for a commercial truck lease-to-own
| Clause | Include | High risk / likely unenforceable |
|---|---|---|
| Characterization | State plainly whether it is a lease or a sale with security interest; consistent tax and title treatment | Calling it a lease while the buyout is $1 and hoping for 2A remedies |
| Title and lien | Lienholder notation within 20 days (NC perfection relates back if application delivered within 20 days, G.S. 20-58.2) | Unperfected interest; title held by an unrelated party |
| Payments, late fees | Fixed schedule; late fee tied to real cost; grace period | Escalating fees that work as penalties; compounding fees on fees |
| Default and cure | Defined events; written notice and cure period; acceleration only on real default | "Insecurity" acceleration without good faith (2A-109 / 1-309) |
| Liquidated damages | Formula reasonable for anticipated harm (2A-504) | Full remaining payments plus keeping the truck plus residual |
| Repossession | Self-help only without breach of peace; stop on objection; personal property inventory and return; post-repossession notices | Entry into closed garages, threats, police intimidation, waiver of breach-of-peace limits |
| Disposition | Commercially reasonable sale; notice of disposition; surplus to debtor, deficiency per Article 9 | Strict foreclosure without consent; private sale to insider at low price |
| Insurance | Primary auto liability at the Part 387 level if for-hire; physical damage with lessor as loss payee; lessor as additional insured; certificates verified with insurer | Relying on a certificate PDF; gaps between bobtail/non-trucking and carrier coverage |
| Indemnity | Mutual, limited to each party's negligence; insurance-backed | Lessee indemnifies lessor for lessor's own negligence (state limits, unverified) |
| Maintenance, inspection | Who maintains; Part 396 annual inspection records; return condition standard | Shifting all repair cost for pre-existing defects |
| GPS/telematics | Written disclosure: data collected, purpose, retention, who sees it, no off-duty personal tracking | Covert tracking; selling location data |
| Remote disablement | Only if lawful in the state, only when parked, after notice; safety carve-outs | Disabling a moving vehicle; disabling a vehicle carrying a load without arranging cargo protection |
| DOT allocation | Who holds USDOT/authority, who is the motor carrier of record, who files | Silent contract; truck runs under lessor's USDOT without control |
| Dispute resolution | Venue, governing law, fee-shifting as allowed | Arbitration that ignores FAA §1 transportation-worker exclusion (see New Prime) |
| Early buyout/transfer | Payoff formula; title transfer on final payment within set days | Hidden balloon payments |

## Repossession playbook (outline)
1. Confirm default and any required notice/cure under the contract and state law.
2. Choose self-help (no breach of peace) or replevin/claim-and-delivery through the court.
3. No confrontation; if the debtor objects in person, leave and go to court.
4. Inventory personal property and any cargo; notify the carrier/shipper if a load is aboard.
5. Send post-repossession notices; dispose commercially reasonably; account for surplus/deficiency.
6. Record everything; keep GPS logs only as long as needed.

## Consumer vs commercial pitfalls
- Individual lessee, personal/household use: consumer lease definitions (Ohio 1310.01(A)(5)); federal Consumer Leasing Act thresholds
  (unverified this session); retail installment rules for disguised sales (Ohio R.C. Chapter 1317: 1317.12 requires a default-and-cure
  notice within 5 business days after repossession in a consumer transaction; NC G.S. Chapter 25A covers consumer credit sales).
- Sole-proprietor owner-operator buying a truck for business: usually commercial, but check state retail installment statutes, which can
  turn on the buyer's purpose rather than the label.
