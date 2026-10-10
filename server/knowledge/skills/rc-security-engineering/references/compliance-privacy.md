# Compliance and privacy for developers  (as of 2026-10)

Engineering guidance, not legal advice. Confirm scope with counsel; these are the things code and infrastructure must make possible.

## Which regime applies (quick triage)
| Regime | Applies when | What engineering must provide |
|---|---|---|
| GDPR (EU) / UK GDPR + DPA 2018 | You process personal data of people in the EU/UK, or are established there | Lawful basis per purpose, data inventory (RoPA), minimization, retention, DSAR export/delete/rectify, DPIA for high-risk processing, breach notice 72 h, DPAs with processors, transfer mechanism (SCCs, EU-US DPF) |
| UK Data (Use and Access) Act 2025 | UK GDPR controllers | DSAR searches "reasonable and proportionate" (since 2025-06-19); electronic complaints procedure, ack within 30 days (from 2026-06-19); some cookie consent exemptions (analytics, unverified scope) |
| CCPA/CPRA (California) | For-profit meeting revenue/volume thresholds and handling CA residents' data | Notice at collection, right to know/delete/correct, opt-out of sale/sharing (honor Global Privacy Control), sensitive-PI limit, contracts with service providers; 45-day response (+45) |
| CPPA regulations (2026-01-01) | Same, for higher-risk processing | Risk assessments before high-risk processing (submissions to CPPA from 2028-04-01), ADMT notice/opt-out/access from 2027-01-01, annual independent cybersecurity audits phased by revenue (unverified per-tier dates) |
| Other US state laws | ~20 states with comprehensive privacy laws (unverified count) | Same building blocks: notices, rights, opt-outs, GPC, data protection assessments |
| HIPAA | Covered entities and their business associates handling PHI | BAA with every vendor touching PHI, access controls, audit logs, encryption (current rule "addressable", treat as required), risk analysis, breach notice within 60 days. Security Rule overhaul (NPRM 2025-01-06) is NOT final as of 2026-10 |
| PCI DSS 4.0.1 | You store, process or transmit card data, or can affect its security | Minimize scope: hosted fields/redirect (SAQ A) so card data never touches your servers; payment-page script inventory + integrity (6.4.3) and tamper detection (11.6.1) mandatory since 2025-03-31; MFA into the CDE; no PAN in logs |
| SOC 2 (AICPA TSC) | B2B buyers ask for it | Not a law: an audit of your controls. Type I = design at a point in time; Type II = operating over 3-12 months. Evidence: access reviews, change management (PR reviews, CI), vendor reviews, incident drills, backups tested |
| ISO/IEC 27001:2022 | Buyers (esp. EU) ask for it | ISMS: risk assessment, Statement of Applicability over 93 Annex A controls; 2013 certificates expired after the 2025-10-31 transition |
| COPPA (US, under 13) | Child-directed service or actual knowledge of under-13 users | Amended rule, full compliance 2026-04-22: verifiable parental consent, separate consent for third-party disclosure (incl. targeted ads), written data retention policy (no indefinite retention), written security program, broader "personal information" (biometrics) |
| UK Age Appropriate Design Code / Online Safety Act | Services likely accessed by UK children (under 18) | High-privacy defaults, no nudges, geolocation off, profiling off by default, DPIA; OSA child-safety and age-assurance duties (in force 2025-07-25) for user-to-user and search services |
| EU Cyber Resilience Act | Products with digital elements sold in the EU (apps, firmware, libraries sold commercially) | Report actively exploited vulns/severe incidents via ENISA SRP from 2026-09-11 (24 h / 72 h / final report); security by default, SBOM, 5-year-ish support period, vulnerability handling from 2027-12-11 |
| EU AI Act | Placing AI systems on the EU market | Art. 50 transparency from 2026-08-02 (disclose AI interaction, mark synthetic content); high-risk (Annex III) duties from 2027-12-02 after the 2026 omnibus |
| NIS2 (EU) | Medium/large entities in listed sectors (incl. cloud, DNS, managed services) | Risk management measures, incident reporting 24 h/72 h, management accountability; national transposition varies |

## Privacy-by-design checklist (per feature)
- [ ] Purpose written down; each field justified; optional fields actually optional
- [ ] Lawful basis / notice updated (privacy policy, app store labels, Data safety, `PrivacyInfo.xcprivacy`)
- [ ] Defaults are the most private setting; no dark patterns in consent
- [ ] Retention period set and enforced by a job; backups and logs covered
- [ ] Access restricted (role, tenant); production data not copied to dev/test (use synthetic or masked data)
- [ ] Third parties: DPA/BAA signed, data sent minimized, region/transfer checked, sub-processor list updated
- [ ] DSAR: new store included in export and delete paths; tested
- [ ] DPIA / CPPA risk assessment done if: large-scale sensitive data, profiling with significant effects, monitoring, children, new tech (AI)
- [ ] Analytics: first-party, aggregated, no fingerprinting; trackers/ad SDKs only with a documented need and consent

## Techniques
| Technique | What it gives | Pitfalls |
|---|---|---|
| Pseudonymization (keyed HMAC/tokenization) | Separates identity from data; still personal data under GDPR | Unkeyed hash of email/phone is reversible by dictionary |
| Anonymization (aggregation, k-anonymity, differential privacy) | Out of GDPR scope only if re-identification is not reasonably likely | Small groups, location traces and free text re-identify |
| Field-level encryption | Limits blast radius of DB dumps and insiders | Key management, searchability (blind indexes leak equality) |
| Crypto-shredding | Delete a per-user key to "delete" data in backups/logs | Keys must truly be per subject and not backed up elsewhere |
| Data residency | Region-pinned storage/processing | Logs, support tools, LLM APIs and analytics often leak across regions |

## DSAR handling (minimum viable)
1. Intake: one channel (form/email) logged with timestamp; verify identity proportionately (logged-in session, or email challenge).
2. Locate: a registry of stores keyed by user id (primary DB, search index, analytics, logs, backups, support desk, email tool, processors).
3. Act: export machine-readable (JSON/CSV) for access/portability; delete or anonymize, keeping legally required records (invoices) with a note.
4. Respond inside the clock (GDPR 1 month, +2 for complex with notice; CCPA 45 days, +45); record what was done.
5. Processors: forward deletion requests; get confirmation.

## Breach notification clocks (from awareness)
- GDPR/UK GDPR: supervisory authority within 72 h if risk to people; individuals without undue delay if high risk.
- EU CRA: 24 h early warning, 72 h notification, final report later (14 days after a fix for vulns; 1 month for incidents, unverified).
- HIPAA: individuals without unreasonable delay, no later than 60 days; HHS (immediately if 500+ people, else annual log).
- US states: 30-60 days typical, varies by state (unverified per state). PCI: notify the acquirer/brands per contract, immediately.
- Contracts (DPAs, enterprise MSAs) often require 24-72 h notice to customers: read them before the incident.

## Sources (accessed 2026-10-09)
- https://www.enisa.europa.eu/topics/product-security/vulnerability-services/eu-incident-response-and-cyber-crisis-management/single-reporting-platform-srp - CRA SRP
- https://www.crowell.com/en/insights/client-alerts/its-live-the-cyber-resilience-act-reporting-is-mandatory-as-of-today-11-september-2026 - CRA reporting live (lead)
- https://www.cppa.ca.gov/announcements/2025/20250923.html - CPPA regulations approved, effective 2026-01-01
- https://www.hunton.com/privacy-and-cybersecurity-law-blog/newly-approved-ccpa-regulations-have-staggered-deadlines-for-compliance - staggered deadlines (lead)
- https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule - COPPA amended rule (compliance 2026-04-22)
- https://www.hhs.gov/hipaa/for-professionals/security/hipaa-security-rule-nprm/factsheet/index.html - HIPAA Security Rule NPRM (still proposed)
- https://www.pcisecuritystandards.org/ - PCI DSS 4.0.1, future-dated requirements effective 2025-03-31
- https://privacymatters.dlapiper.com/2026/02/uk-commencement-of-the-data-protection-provisions-in-the-data-use-and-access-act/ - DUAA commencement (lead)
- https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/childrens-information/childrens-code-guidance-and-resources/ - Children's code
- https://www.iso.org/standard/27001 - ISO/IEC 27001:2022
- https://gdpr-info.eu/art-33-gdpr/ and https://gdpr-info.eu/art-12-gdpr/ - 72 h breach notice, 1-month DSAR clock
