# Entity comparison (as of 2026-10)

High level only. Tax lines are orientation, not tax advice: a CPA confirms elections, payroll and state tax. State law varies; the
NC/OH/DE specifics are in `nc-oh-de-formation.md`.

## Table
| Form | Created by | Owner liability | Default federal tax | Governance | Cost/compliance | Fits when |
|---|---|---|---|---|---|---|
| Sole proprietorship | Doing business (plus assumed-name filing if not using own name) | Unlimited | Owner's Schedule C; self-employment tax | Owner decides | Lowest; licenses, assumed name | Very low risk, testing an idea; insurance carries the risk |
| General partnership | Two+ co-owners carrying on business for profit, even without paperwork | Unlimited, joint and several | Partnership (Form 1065, K-1s) | Partnership agreement or statute default (equal say) | Low; but accidental GPs are common | Rarely chosen on purpose today; use an LLC |
| Limited partnership (LP) | State filing | GP unlimited (unless LLLP); LPs limited if passive | Partnership | GP manages | Filing + annual fees (DE $400/yr) | Investment funds, real estate with a sponsor GP |
| LLP / LLLP | Registration by a partnership | Shield for partners' vicarious liability (scope per state) | Partnership | Partnership agreement | Registration + annual (NC) or biennial (OH) report | Professional firms where state law allows (law, accounting) |
| LLC, single-member | Articles/certificate filed with SOS | Limited (subject to veil piercing) | Disregarded (Schedule C or parent's return); may elect C or S | Operating agreement; member-managed default in most states | Filing + annual report/tax per state | Default for a one-owner business with liability exposure |
| LLC, multi-member | Same | Limited | Partnership; may elect C or S | Member-managed (all members agents/decide) or manager-managed (named managers run it, members vote on big items) | Same + more complex agreement and K-1s | Small co-owned businesses, real estate, joint ventures |
| C corporation | Articles/certificate of incorporation | Limited | Entity-level corporate tax + tax on dividends | Shareholders elect directors; directors appoint officers; bylaws | Filing + annual report; DE franchise tax | Venture capital, many investors, stock options, planned IPO/acquisition |
| S corporation | Not an entity: an IRS election (Form 2553) by a corporation or LLC | As the underlying entity | Pass-through; owner-employees on payroll with reasonable compensation | As underlying entity | Payroll, separate return (1120-S) | Profitable owner-operated business where a CPA shows net savings; max 100 shareholders, one class of stock, no nonresident aliens |
| Nonprofit corporation | State nonprofit articles | Limited | Taxable until IRS recognizes exemption (e.g. 501(c)(3) via Form 1023/1023-EZ) | Board; no owners; private benefit limits | Annual filings with state and IRS (990 series) | Charitable, educational, member-serving missions |
| Benefit corporation / PBC | Charter opting in (where the state has a statute) | Limited | As a corporation | Directors balance stated public benefit with stockholder value | Extra reporting | Mission-driven companies that still want equity investors. DE has PBCs and statutory public benefit LLCs/LPs; Ohio: "beneficial purpose" stated in the articles (R.C. 1701.01(FF), 1701.03, since 2021-03-24); NC: no statute (2026-10-10) |
| Professional entity (PC, PLLC, PA) | Articles + licensing-board certificate | Limited for business debts; never for own malpractice | As corporation or LLC | Owners usually must be licensees | Board fees and renewals | Licensed professions (medicine, law, CPA, engineering, etc.) |
| Series LLC | Operating agreement/certificate where authorized | Each series shielded from others' debts if statutory formalities met | Unsettled federal treatment per series (CPA) | Per series | Records per series; untested in non-series states | Several properties or product lines; DE and OH allow; NC: no series provision in Ch. 57D (2026-10-10). Courts in other states may not respect the shield |

## Decision questions to ask the owner
1. Where will it actually operate, hire and hold property? (Form there unless there is a reason not to.)
2. How many owners now and later? Outside investors? Venture funding? Employee equity?
3. Liability profile: vehicles, customers on premises, professional advice, product risk. (Insurance first; entity second.)
4. Expected profit in year 1-3 (drives S-election value; CPA).
5. Who manages day to day? Who can sign, borrow, hire?
6. What happens on death, disability, divorce, deadlock or an owner leaving? (Buy-sell terms.)
7. Licensed profession? (Professional entity rules.)
8. Budget for annual fees, registered agent, tax prep, payroll.

## Holding companies and subsidiaries
- A parent owning operating subsidiaries isolates risk only if each entity is run separately (own accounts, contracts, insurance,
  intercompany agreements at arm's length). Otherwise it adds cost and invites piercing or "single enterprise" claims.
- Intercompany leases/licenses and management fees need written agreements and tax review (transfer pricing at small scale is still a CPA topic).

## Tax classification vs entity status (say this explicitly)
- State law decides the entity and liability shield. The IRS decides tax treatment: default rules (Treas. Reg. "check-the-box"), Form 8832
  to elect corporate treatment, Form 2553 for S status. Changing tax status does not change the entity, its name or its liability shield.
- States may tax differently from the IRS (e.g. Ohio CAT on gross receipts above the exclusion; Delaware's flat $400 LLC tax regardless of income).
