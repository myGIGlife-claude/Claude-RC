# FMCSA thresholds and tables (verified on eCFR, current through 2026-10-07; accessed 2026-10-10)

## Which definition answers which question
| Question | Definition | Threshold (rating OR actual weight, whichever is greater) |
|---|---|---|
| Do FMCSRs (Parts 390-397) apply? | 49 CFR 390.5T "commercial motor vehicle" (interstate; 390.5 is suspended, same text) | 10,001 lb GVWR/GCWR/GVW/GCW; or 9+ passengers incl. driver for compensation; or 16+ not for compensation; or placarded hazmat |
| Is a CDL required? | 49 CFR 383.5 / 383.91 (interstate AND intrastate, 390.3T(b)) | A: 26,001+ lb GCWR with towed unit > 10,000 lb. B: single vehicle 26,001+ lb GVWR (may tow <= 10,000 lb). C: 16+ passengers incl. driver, or hazmat (placard/select agent) |
| Drug and alcohol testing / Clearinghouse? | 49 CFR 382.103 | Only operators subject to the CDL rules (Part 383); 390.3T(f) exceptions do not exempt from Part 382 |
| Minimum public liability? | 49 CFR 387.9 (interstate for-hire; hazmat incl. private) and 387.303T | see table below |
| Ohio intrastate | OAC 4901:2-5-01(B) | for-hire: 390.5 definition (10,001 lb); private: 383.5 definition (26,001 lb) |
| NC intrastate | G.S. 20-376(5); 14B NCAC 07C .0101(b) | 26,001 lb; or 16+ passengers incl. driver; or placarded hazmat (but 396.17-.23 periodic inspection from 10,001 lb GVWR, 07C .0101(d)) |

GCWR (390.5T): the manufacturer's GCWR on the FMVSS label of the power unit, or the sum of GVWRs/GVWs of power unit and towed units,
whichever gives the highest value. The power unit's GCWR is not used when it is not towing.

## Worked examples (reasoning patterns, not legal advice)
| Vehicle and use | FMCSR CMV? | CDL? |
|---|---|---|
| 26-ft box truck, label 25,999 lb, empty, interstate for-hire | Yes (>= 10,001) | No (Group B needs 26,001) |
| Same truck loaded to 26,400 lb on the scale | Yes | Yes, Group B (actual GVW counts) |
| 16-ft box truck, label 14,500 lb, intrastate Ohio, own goods (private) | Ohio private uses 26,001: generally no | No |
| Same truck, intrastate Ohio, for-hire | Yes under PUCO rules (10,001) | No |
| Pickup 14,000 lb GVWR towing 14,000 lb GVWR gooseneck, interstate for-hire | Yes | Yes, Group A (28,000 combined, towed unit > 10,000) |
| Pickup 11,500 lb GVWR towing 12,000 lb GVWR trailer, label GCWR 23,000 | Yes | No, unless actual combined weight reaches 26,001 (sum of ratings 23,500) |
| Cargo van 9,350 lb GVWR, actual loaded 9,800 lb, interstate | No | No |

Always compute from the labels and scale tickets; never infer from body style or length.

## Public liability minimums (387.9 table, 387.303T)
| Carriage | Commodity | Minimum |
|---|---|---|
| For-hire interstate, 10,001+ lb GVWR | Property, nonhazardous | $750,000 |
| For-hire and private, interstate/intrastate, 10,001+ lb | Bulk hazmat classes listed in 387.9 row 2 (cargo tanks, explosives 1.1-1.3, etc.) | $5,000,000 |
| For-hire and private, interstate any quantity / intrastate bulk, 10,001+ lb | Oil, hazardous waste/materials/substances not in rows 2 or 4 | $1,000,000 |
| For-hire and private, interstate, under 10,001 lb | Explosives 1.1-1.3, Zone A 2.3/6.1 PG I, HRCQ Class 7 | $5,000,000 |
| For-hire, fleet of only vehicles under 10,001 lb (387.303T(b)(1)) | Property, nonhazardous | $300,000 |
| Passenger carriers (387.33T) | 16+ seats incl. driver / 15 or fewer | $5,000,000 / $1,500,000 |
| Brokers and freight forwarders (387.307; 387.405) | Surety bond BMC-84 or trust BMC-85 | $75,000 (88 FR 78656; 387.307 fully in force 2026-01-16) |
| NC intrastate CDL-class CMV owner (G.S. 20-309(a1), 20-4.01(3d)) | Same as 387.9 for-hire nonhazardous property | $750,000 |

## Registration calendar (390.19T)
- File MCS-150 before operating and every 24 months. Month = last digit of USDOT number (1 Jan, 2 Feb ... 9 Sep, 0 Oct).
- Year: next-to-last digit odd -> odd years; even -> even years. Penalty and USDOT deactivation for a missed update (390.19T(b)(4)),
  but FMCSA paused inactivation for updates due on/after 2026-06-01 during the Motus rollout (fmcsa.dot.gov, updated 2026-06-22, via
  Wayback). Name/address changes: within 30 days under the suspended 390.201 text; do them in Motus.
- New applicants: 390.200T (Form MCSA-1) covers new private and exempt for-hire carriers; non-exempt for-hire applicants file the
  OP-1 series with the MCS-150 (390.19T(e)). Since 2026-05-19 all of this is done in Motus (FR notice 2026-04-29; FMCSA "Move to Motus").

## Hours of service and ELD exemptions that matter for box trucks (395.1, 395.8)
- 395.1(e)(1): any driver within 150 air-miles, released within 14 h, 10 h off (property), carrier keeps 6-month time records: no RODS/ELD.
- 395.1(e)(2): non-CDL property drivers within 150 air-miles returning to the reporting location; may drive until the 14th hour on 5 days
  and the 16th hour on 2 days of any 7; exempt from 395.3(a)(2), 395.8, 395.11; time records 6 months.
- 395.8(a)(1)(ii): paper RODS allowed instead of ELD if RODS needed on 8 or fewer days in any 30; driveaway where the driven vehicle is the
  shipment; driveaway of motor homes/RV trailers; engines older than model year 2000.

## Recent and pending federal actions (federalregister.gov, checked 2026-10-10)
| Date | Action | Status |
|---|---|---|
| 2025-07-24 | Speed limiter NPRM/ANSPRM withdrawn | Final (withdrawn) |
| 2025-09-29 | Non-domiciled CDL interim final rule (90 FR 46509) | Stayed by D.C. Cir. 2025-11-10/13 (Lujan v. FMCSA, No. 25-1215); replaced by final rule |
| 2026-02-13 | Non-domiciled CDL final rule (91 FR 7044) | Effective 2026-03-16; new petition for review reported (unverified) |
| 2026-02-19 | eDVIR rule (91 FR 7893); rear impact guard labels; flares; spare fuses (91 FR 7877) | Effective 2026-03-23 (spare fuses 2026-04-20) |
| 2026-02-27 | DOL IC status NPRM (rescind 2024 rule) | Proposed only |
| 2026-04-29 | Motus availability notice (91 FR 23144; docket corrected 91 FR 24643) | Phase II launched 2026-05-19 (FMCSA page) |
| 2026-06-22 | ELD manual rescinded (91 FR 37050); CDL self-reporting removed (91 FR 37047); completed inspection report disposition (91 FR 37053) | Effective 2026-07-22 |
| 2026-07-21 | Technical amendments (91 FR 45653), incl. 387.9 | Effective 2026-07-21, no amount change |
| 2026-10-05 | Emergency exemptions: automatic relief after a regional declaration 14 -> 30 days (91 FR 63156) | Effective 2026-10-05 |
| 2026-08-10 | English proficiency OOS NPRM (91 FR 51422) | Proposed; comments closed 2026-10-09 |
| 2026-09-01 | UCR fees 2027 | Effective 2026-10-01 |
| 2024-11-20 | Broker transparency NPRM | No final rule found |
