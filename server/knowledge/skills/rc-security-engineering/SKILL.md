---
name: rc-security-engineering
description: Security engineering process and non-web surfaces: secure SDLC, threat modeling (STRIDE, LINDDUN, 30-minute method), ASVS 5/MASVS/SAMM, mobile app security, secrets/KMS, crypto and post-quantum, vuln triage (CVSS, EPSS, KEV, SBOM/VEX), SAST/DAST/SCA, cloud/container IAM, security.txt, incident response, GDPR/CCPA/HIPAA/PCI/COPPA basics, LLM/agent security. Use for design reviews, threat models, audits, compliance questions.
---
# Security engineering, privacy and compliance  (as of 2026-10)

> Facts here are dated (see Sources). Standards, laws and deadlines move: confirm the primary source before promising a date. Anything marked (unverified) is a lead, not a fact. Compliance notes are engineering guidance, not legal advice.

Process and non-web surfaces. Web recipes (headers, CSP, cookies, CSRF, XSS, SSRF, TLS certs, npm/Actions supply chain) live in
**rc-web-security-perf**: use it, don't repeat it. Platform code: rc-android, rc-ios. Compliance and privacy detail: `references/compliance-privacy.md`.

## Currency check
- **OWASP ASVS 5.0.0** (2025-05-30): ~350 requirements, 17 chapters, levels L1-L3; requirement IDs changed from 4.0.3, so old mappings break.
- **OWASP MASVS 2.1.0** (2024-01-18) still current, added MASVS-PRIVACY. **MASTG 2.0.0** (2026-06-30): modular atomic tests linked to
  **MASWE** weaknesses (MASWE 1.0.0 2026-08-17). MASVS v2 dropped L1/L2/R levels: use MAS testing profiles.
- **OWASP SAMM 2.2.0** (2026-07-06; 2.1.0 was 2024-09-18). **NIST SSDF** SP 800-218 v1.1 + SP 800-218A (generative AI profile). NIST CSF 2.0 (2024-02).
- **OWASP Top 10 for LLM Apps 2025** (LLM01 Prompt Injection ... LLM10 Unbounded Consumption) and **OWASP Top 10 for Agentic
  Applications 2026** (released 2025-12-09: ASI01 Agent Goal Hijack, ASI02 Tool Misuse, ASI03 Identity and Privilege Abuse, ...).
- **Post-quantum**: FIPS 203 ML-KEM, 204 ML-DSA, 205 SLH-DSA final 2024-08-13. FIPS 206 FN-DSA (Falcon) not final (no FIPS 206 on the
  csrc FIPS list, 2026-10); HQC selected 2025-03-11 as backup KEM (NIST: draft ~1 year later, final 2027). NIST IR 8547 (deprecate 112-bit
  RSA/ECC after 2030, disallow after 2035) is still the 2024-11-12 **initial public draft** on csrc.nist.gov as of 2026-10.
- **Hybrid TLS key exchange X25519MLKEM768 is on by default** in OpenSSL 3.5 (2025-04-08, an LTS), Go 1.24+ (when `CurvePreferences` is nil;
  `GODEBUG=tlsmlkem=0` reverts), Chrome 131+, and all TLS 1.3 on iOS/macOS 26. CryptoKit on 26 adds ML-KEM, ML-DSA and X-Wing HPKE.
  JDK 24 (2025-03-18) adds ML-KEM/ML-DSA (JEP 496/497).
- **Vuln data**: CVSS 4.0 (2023-11). EPSS v4 (2025-03-17). CISA KEV is the "exploited in the wild" list. The CVE program survived the
  2025 funding scare (CISA/MITRE contract renegotiated, reported 2026-03; budget details unconfirmed). NVD enrichment backlog persists: don't depend on NVD CVSS alone.
- **SBOM**: CISA "2026 Minimum Elements for an SBOM" (2026-07, guidance, not a legal requirement) replaced the 2021 NTIA list; adds component hash, license, tool, generation context.
- **EU Cyber Resilience Act**: actively exploited vulns and severe incidents in products sold in the EU must be reported via ENISA's
  Single Reporting Platform from **2026-09-11** (24 h early warning, 72 h notification); full obligations (SBOM, security updates,
  vulnerability handling) from **2027-12-11**. Open-source stewards have lighter duties (reporting from 2027-12-11).
- **EU AI Act**: Digital Omnibus on AI (Reg. (EU) 2026/1744, per law-firm reports) in force 2026-07-27: Annex III high-risk duties moved to 2027-12-02, Annex I to 2028-08-02; Art. 50 transparency (tell users
  they're talking to AI, label synthetic content) still from 2026-08-02.
- **Privacy law**: COPPA amended rule full compliance **2026-04-22** (separate parental consent for third-party disclosure/ads, written
  retention limits, written security program). California CPPA rules effective 2026-01-01 (risk assessments now; ADMT duties 2027-01-01;
  cybersecurity audit certifications 2028-04-01 to 2030-04-01 by revenue). UK Data (Use and Access) Act 2025: DSAR search only "reasonable and proportionate"; mandatory complaints
  procedure from 2026-06-19. HIPAA Security Rule overhaul still a **proposal** (NPRM 2025-01-06; final targeted 2027, unverified):
  the current rule applies. PCI DSS 4.0.1: all future-dated requirements mandatory since 2025-03-31 (incl. 6.4.3/11.6.1 payment-page scripts).
- **Android 17** (stable 2026-06): `usesCleartextTraffic` slated for deprecation (use network security config), per-app Keystore key cap
  (50,000 when targeting 17, else 200,000), cross-profile loopback blocked, OTP SMS withheld from most apps for 3 h (use SMS Retriever/User
  Consent); implicit URI grants for SEND/SEND_MULTIPLE/IMAGE_CAPTURE end in Android 18 (set `FLAG_GRANT_*_URI_PERMISSION`).
  Android 16: built-in intent-redirection protection. **Play Integrity**: standard requests, `appAccessRiskVerdict`, `recentDeviceActivity`,
  `deviceRecall` (beta); SafetyNet Attestation is gone.

### Older versions
- ASVS 4.0.3 projects: keep the old IDs in existing reports; map new work to 5.0 and note the mapping. Don't rewrite past audits.
- OpenSSL 3.0/3.2 (still on many LTS distros): no ML-KEM; hybrid PQ needs the oqs-provider or a newer TLS terminator (proxy/CDN). Don't
  upgrade the OS crypto stack inside an unrelated task.
- Android 12 and lower: `MEETS_STRONG_INTEGRITY` does not imply recent patches; opt in to device attributes and check `sdkVersion`. iOS below 26: no PQ TLS or CryptoKit ML-KEM.
- PCI DSS 3.2.1 is retired (2024-03-31); any "PCI 3.2.1 compliant" claim is stale.

## What changed / stop doing
| Old advice | Do instead | Since |
|---|---|---|
| Security by obscurity (hidden endpoints, obfuscation as the control) | Assume the attacker has the binary and the API; enforce on the server | always |
| Roll your own crypto / "simple XOR" / custom protocols | libsodium, Tink, platform CryptoKit/Keystore, TLS 1.3, age, JOSE libs | always |
| MD5 / SHA-1 / unsalted SHA-256 for passwords or signatures | Argon2id (passwords), SHA-256/SHA-3 (hash), Ed25519/ECDSA P-256 (sign), HMAC-SHA-256 (MAC) | SHA-1 collisions 2017 |
| AES-CBC + HMAC hand-assembled, AES-ECB | AEAD: AES-256-GCM or ChaCha20-Poly1305; XChaCha20 / AES-GCM-SIV when nonces may repeat | - |
| RSA key exchange, RSA-PKCS#1 v1.5 encryption | ECDHE (X25519), hybrid X25519MLKEM768 where offered; RSA-OAEP only for legacy wrap | TLS 1.3 |
| "PQ later" | Inventory crypto now; enable hybrid KEX where the stack defaults it (harvest-now-decrypt-later); PQ signatures can wait | FIPS 203, 2024-08 |
| Annual-only pentest as the security program | Threat model per feature, SAST/SCA in CI, DAST on staging, pentest before big launches and yearly | SSDF, SAMM |
| Patch by CVSS score alone | KEV first, then EPSS + reachability + exposure; CVSS as severity input | EPSS v4, 2025 |
| "We're too small to be targeted" | Attacks are automated (credential stuffing, exposed keys, worm-style supply chain); small = easy | - |
| Root/jailbreak detection as a security control | Server-side attestation (Play Integrity, App Attest) as one risk signal; never sole gate | - |
| SafetyNet Attestation | Play Integrity API (standard requests) | SafetyNet fully turned down 2025-01 |
| `EncryptedSharedPreferences` | Keystore AES-GCM key + DataStore (or Tink) | security-crypto 1.1.0 (2025-07-30) deprecates all APIs |
| Static long-lived cloud keys in CI and apps | OIDC workload identity, short-lived STS tokens, IMDSv2 | - |
| Log everything incl. request bodies | Allow-list fields; redact tokens, passwords, PII; structured logs | - |
| SBOM as a one-off PDF | Machine-readable CycloneDX/SPDX per build + VEX for "not affected" | CISA 2026 elements |
| Privacy as a banner and a policy page | Data inventory, minimization, retention jobs, DSAR tooling built in | GDPR Art. 25 |
| Prompt "guardrails" as the LLM security boundary | Least-privilege tools, human approval for side effects, output treated as untrusted | OWASP LLM 2025 |
| ASVS 4.0.3 IDs in new work | ASVS 5.0 IDs | 2025-05-30 |

## Do this

### Secure SDLC: where each activity fits
| Phase | Activity | Output, time box |
|---|---|---|
| Requirements | Security + privacy requirements from ASVS level (L1 default, L2 for PII/money, L3 high-value), MASVS for mobile; data inventory | checklist in the ticket |
| Design | Threat model (30-minute method below) for new data flows, auth changes, new trust boundaries, new third parties | `docs/threat-model.md` per feature |
| Build | Secure defaults, reviewed libs, secrets scanning pre-commit, SAST + SCA in CI | CI gates |
| Verify | Unit tests for authz rules, abuse-case tests, DAST on staging, ASVS/MASTG checks on risky features | test evidence |
| Release | SBOM + provenance, signed artifacts, security sign-off for high-risk changes | release notes |
| Operate | Logging/alerting, vuln triage SLAs, security.txt, incident runbook, key rotation | runbooks |
- Use SAMM to measure the program yearly; do not try to hit SAMM level 3 everywhere. Small team target: level 1 across the board, level 2 in Threat Assessment and Defect Management.
- When NOT to add a dependency: no security SDK, RASP or "anti-tamper" wrapper unless a threat model shows the risk and the platform can't
  cover it. Every SDK is attack surface and a privacy liability. Prefer platform crypto/keystores and one well-known library (libsodium/Tink).

### Threat modeling: the 30-minute method (per feature)
1. **Draw** (5 min): data-flow diagram: actors, processes, data stores, flows; mark **trust boundaries** (device/server, tenant/tenant,
   service/third party, user/admin, LLM/tool). Mermaid in the repo is enough.
2. **Ask STRIDE per element crossing a boundary** (10 min): Spoofing (authn), Tampering (integrity), Repudiation (audit log),
   Information disclosure (encryption, authz), Denial of service (limits, quotas), Elevation of privilege (authz, input handling).
3. **Privacy pass with LINDDUN** (5 min) when personal data flows: Linking, Identifying, Non-repudiation (unwanted), Detecting,
   Data disclosure, Unawareness/unintervenability, Non-compliance. LINDDUN GO cards for a quick session.
4. **Rank and decide** (5 min): likelihood x impact (high/med/low); for each: mitigate, accept (named owner), transfer, remove the feature.
5. **Record** (5 min): threats, decisions, follow-up tickets with tests. Revisit when the diagram changes.
- Attack trees for one high-value goal ("take over an admin account", "exfiltrate tenant data"): root = goal, children = OR/AND ways;
  cost each leaf; mitigate the cheapest paths first.
- Four questions (Threat Modeling Manifesto): what are we working on, what can go wrong, what are we going to do, did we do a good job.

### AuthN/AuthZ design review points (beyond the web recipes)
- One authorization decision point (policy function/service), deny by default, checked on every request at the object level; tenant id
  comes from the session, never from the request body. Test with two accounts and every role.
- Model: RBAC for coarse roles, ReBAC/ABAC (OpenFGA, Cedar, OPA) when sharing/ownership rules grow; don't build a policy engine yourself.
- Tokens: short-lived access tokens (5-15 min) + rotating refresh tokens with reuse detection; validate `iss`, `aud`, `exp`, `alg` pinned;
  OAuth 2.1/BCP: PKCE for every client, no implicit flow, no ROPC; DPoP or mTLS sender-constraining for high-value APIs.
- Mobile/native: AppAuth + system browser (ASWebAuthenticationSession / Custom Tabs), never embedded WebView login; no client secrets in apps.
- Service-to-service: workload identity (SPIFFE/mTLS, cloud IAM roles), not shared API keys. Admin paths: separate role, MFA/passkey, audit log.
- Account recovery, invites, email change, and support tooling are part of authn: same review bar.

### Secrets and key management
- Hierarchy: **KMS/HSM root key** (never leaves the HSM) -> wraps **data keys** -> encrypt data (**envelope encryption**). Store the
  wrapped data key next to the ciphertext; rotate the KEK without re-encrypting data; re-key data only on compromise.
- Use the managed service (AWS KMS, GCP Cloud KMS, Azure Key Vault, HashiCorp Vault/OpenBao) for keys and a secrets manager for
  credentials; apps get secrets at runtime via workload identity, not env files baked into images.
- Rotation: automate; every secret has an owner, an expiry and a rotation runbook. Dual-key windows (old + new valid) for zero-downtime.
- Leaked secret = revoke and rotate first, then clean history. Secret scanning pre-commit + push protection (tools in rc-web-security-perf).
- Mobile: nothing secret ships in the app. Device keys live in Android Keystore (StrongBox when present) / iOS Keychain + Secure Enclave,
  non-exportable, bound to user auth for high-value ops. Third-party API keys go behind your backend.

### Cryptography choices (use a library; pick from this list)
| Need | Use | Avoid |
|---|---|---|
| Encrypt data | AES-256-GCM (random 96-bit nonce, < 2^32 msgs/key) or XChaCha20-Poly1305; Tink AEAD / libsodium `secretbox` | ECB, CBC without MAC, reused GCM nonce |
| Password storage | Argon2id (OWASP: m=19 MiB, t=2, p=1 minimum), scrypt, bcrypt (cost >= 10, 72-byte limit) | SHA-x, PBKDF2 < 600k iterations |
| Key from a secret | HKDF-SHA-256 (high-entropy input), Argon2id (passwords) | raw hash as key |
| Signatures | Ed25519; ECDSA P-256 for FIPS/hardware; RSA-PSS 3072 for legacy; ML-DSA/hybrid only when a standard asks | RSA-1024, DSA, PKCS#1 v1.5 for new |
| MAC / tokens | HMAC-SHA-256; constant-time compare; 128+ bit CSPRNG tokens | `==` compare, `Math.random`, timestamps |
| Key exchange | X25519; hybrid X25519MLKEM768 in TLS 1.3 | static RSA, custom DH params |
| Files/backups | age, or Tink streaming AEAD; gpg only for legacy interop | zip passwords |
| TLS | 1.3 preferred, 1.2 with ECDHE+AEAD only; disable 1.0/1.1, RC4, 3DES, CBC-SHA1 where possible | pinning leaf certs in apps without backup pins |
- FIPS 140-3 required (US federal, some regulated buyers)? Use a validated module (BoringCrypto, OpenSSL FIPS provider, AWS-LC FIPS); don't just pick "FIPS algorithms".
- PQ plan: inventory (where RSA/ECC is used, data shelf life), turn on hybrid KEX where your TLS stack or CDN offers it, keep crypto agility
  (algorithm ids in stored formats). Don't hand-roll hybrid schemes: use HPKE/X-Wing/TLS from the library.
- Certificate pinning in mobile apps: pin the SPKI of your CA or intermediate plus a backup, with an update path; or skip pinning and rely
  on CT (Chrome/Apple enforce it). A bad pin bricks the app.

### Mobile app security (Android + iOS), MASVS-aligned
- **Storage (MASVS-STORAGE)**: tokens in Keystore-encrypted storage / Keychain `...ThisDeviceOnly`; exclude secrets from backups
  (`dataExtractionRules`, Keychain non-syncing); no PII in logs, screenshots of sensitive screens hidden (`FLAG_SECURE`, privacy overlay).
- **Network**: TLS only (Android network security config, iOS ATS on); no `usesCleartextTraffic="true"`, no `NSAllowsArbitraryLoads`; no trust-all `TrustManager`/`URLSessionDelegate`.
- **IPC/platform**: Android: `exported` explicit, intent-filters imply export, validate extras, use `PendingIntent.FLAG_IMMUTABLE`,
  FileProvider with narrow paths, grant URI permissions explicitly, verified App Links. iOS: Universal Links over custom URL schemes
  (any app can claim a scheme); validate every deep-link parameter; treat it as untrusted input that may trigger actions only after confirmation.
- **WebViews**: JS off unless needed; no `addJavascriptInterface`/`WKScriptMessageHandler` on pages you don't control; no file access
  (`setAllowFileAccess(false)`); load only allow-listed https origins; open other links in the browser.
- **Binary protections (MASVS-RESILIENCE)**: R8/obfuscation, anti-debug, root/jailbreak checks raise attacker cost only. Frida and
  Magisk/KernelSU modules bypass them in hours. Use them for anti-cheat/anti-fraud economics, never to protect secrets or authz.
- **Attestation**: Play Integrity (standard requests, `requestHash` = hash of the action, decrypt + verify server-side, check
  package/cert/freshness; tier responses) and Apple App Attest (`DCAppAttestService`: key per install, server verifies attestation once,
  then assertions with a monotonically increasing counter; roll out gradually, handle unsupported devices). Both are signals for risk
  scoring, not identity; have a fallback path, never hard-block a legitimate user on one bad verdict.
- **Privacy**: Play Data safety form and Apple privacy nutrition labels + `PrivacyInfo.xcprivacy` must match what the code and every SDK do.
- Test with MASTG 2.0 tests for the MASVS controls in scope; tools: MobSF (static), Frida/objection (dynamic), apktool/jadx.

### Logging, detection, incident response (small teams)
- Log: authn success/failure, MFA changes, password/email/recovery changes, authz denials, admin actions, privilege changes, data export,
  key/secret access, config changes, payment events. Fields: UTC time, actor, tenant, action, target, result, source IP, request id.
- Never log: passwords, tokens, session ids, API keys, full card numbers (PCI), health data, raw request bodies. Redact at the logger, test it.
- Detect: alert on auth failure spikes, impossible travel, new admin, MFA disabled, mass export, KEV hits on your SBOM, cloud root login.
  Ship logs off-host, append-only/retention-locked; retention per purpose (security logs often 90 days-1 year).
- IR runbook (one page, NIST SP 800-61r3 (2025-04, a CSF 2.0 profile) aligned): roles (lead, comms, scribe), contacts (host, registrar,
  payment processor, lawyer, insurer), severity levels, steps: detect -> contain (revoke keys, disable accounts, block) -> preserve evidence
  (snapshots, logs) -> eradicate -> recover -> notify -> post-incident review (blameless, tickets). Practice once a year (tabletop).
- Notification clocks: GDPR authority 72 h from awareness; CRA 24 h/72 h; HIPAA individuals without unreasonable delay, max 60 days;
  US states vary; contracts may be stricter. Decide "who calls the lawyer" before the incident.

### Vulnerability management
- Triage order: (1) in **CISA KEV** or vendor says exploited -> fix in days; (2) high **EPSS** (e.g. >= 0.1, team choice) and reachable/
  internet-facing -> fix this sprint; (3) everything else by CVSS severity within SLA (critical 7-14 d, high 30 d, medium 90 d: team policy).
- Reachability: is the vulnerable function called? Use govulncheck-style call analysis where available; record "not affected" with a **VEX**
  statement (OpenVEX or CycloneDX VEX) so scanners and customers stop re-raising it.
- SBOM: CycloneDX or SPDX generated in CI per release (Syft, cdxgen, build-tool plugins), stored with the artifact, scanned continuously (Grype, OSV-Scanner, Dependency-Track).
- Dependency policy: allow-list licenses, minimum maintenance signals (OpenSSF Scorecard), pinned versions + lockfiles, cooldown for new
  releases, owner for every direct dependency, remove unused deps quarterly. Fewer deps beats faster patching.

### Tool landscape (pick one per row)
| Type | What it finds | Open/free options | When |
|---|---|---|---|
| SAST | code patterns (injection, crypto misuse) | Semgrep CE, CodeQL, gosec, Bandit, SpotBugs+FindSecBugs, MobSF | every PR |
| SCA | known-vuln dependencies, licenses | OSV-Scanner, Dependabot/Renovate, Trivy, Grype, pip-audit, govulncheck | every PR + daily |
| Secrets | committed credentials | gitleaks, TruffleHog, GitHub push protection | pre-commit + PR |
| DAST | running-app issues (authn, headers, injection) | OWASP ZAP, Nuclei; Burp for manual | staging, nightly |
| IAST/RASP | runtime-instrumented findings | mostly commercial (Contrast, Datadog) | large apps only |
| IaC/container | misconfig, image CVEs | Checkov, Trivy, KICS, kube-bench (CIS), Prowler (cloud) | PR + scheduled |
| Fuzzing | parser/crash bugs | Go fuzzing, cargo-fuzz, Atheris, Jazzer, OSS-Fuzz | parsers, protocols |
- Tune before gating: block only on high-confidence rules; a noisy gate gets disabled.

### Cloud and container basics
- IAM: least privilege per workload (one role per service), no wildcard `*:*`, no long-lived user keys, MFA/passkeys + SSO for humans,
  break-glass root account locked away with alerts. Review unused permissions (IAM Access Analyzer / Recommender).
- Baselines: CIS Benchmarks for the cloud account, OS, Kubernetes, Docker; check with Prowler / kube-bench; fix highs, document exceptions.
- Network: private by default; no public buckets/DBs; IMDSv2 (hop limit 1); egress allow-lists for sensitive workloads.
- Images: minimal base (distroless/Chainguard/Alpine/slim), non-root user, read-only root FS, no secrets in layers, pinned by digest,
  signed (cosign/Sigstore) with SLSA provenance; admission policy verifies signatures. Kubernetes: Pod Security `restricted`, NetworkPolicies, no default service-account token.
- Encrypt at rest with KMS keys you control for regulated data; turn on cloud audit logs (CloudTrail/Audit Logs/Activity Log) org-wide.

### Vulnerability disclosure
- Publish `/.well-known/security.txt` (RFC 9116): `Contact:` and `Expires:` required (< 1 year), plus `Policy:`, `Preferred-Languages:`,
  optional `Encryption:`. Renew before Expires. Mirror it in `SECURITY.md` and enable GitHub private vulnerability reporting.
- Policy: safe harbor wording, scope, response time (ack in 3 business days), coordinated disclosure (90 days default), credit. CVE via
  GitHub Security Advisories (a CNA). EU CRA products: also report exploited vulns via the ENISA SRP.

### Privacy engineering (any jurisdiction)
- Data inventory per field: purpose, legal basis, where stored, who accesses, retention, deletion path. No inventory, no new field.
- Minimize: don't collect it; else aggregate; else pseudonymize (keyed HMAC with the key in KMS, separate from data); else encrypt
  field-level. Hashing an email is pseudonymization, not anonymization.
- Retention: TTL columns + scheduled deletion jobs, including backups (expire them) and logs. Test that deletion actually deletes.
- DSAR: one export and one delete path keyed by user id across all stores and processors (tickets to each vendor). Verify identity
  proportionately. Clocks: GDPR 1 month (+2 if complex), CCPA 45 days (+45). Details: `references/compliance-privacy.md`.
- Consent: off by default, granular, as easy to withdraw as to give, logged with version and timestamp; no tracking SDK loads before consent.
- Children: if users may be under 13 (US) / under 18 (UK/EU age-appropriate design), default to high privacy, no profiling/ads, no precise location, verifiable parental consent where required.

### AI/LLM application security
- Trust model: everything in the context window (user text, retrieved docs, web pages, emails, tool results, other agents) can carry
  instructions. Prompt injection has no complete fix: limit what a hijacked model can DO.
- Tools/agents: least privilege per tool and per user (the agent acts with the user's permissions, never a superuser token); allow-listed
  tools; human confirmation for irreversible or external side effects (send, pay, delete, merge, deploy); sandbox code execution
  (no network/secrets by default); budget caps (tokens, calls, time) per user (LLM10); contain failures between agents (ASI08 Cascading Failures).
- Data leakage: no secrets in system prompts (assume they leak, LLM07); RAG respects the caller's authz at retrieval time, per-tenant indexes
  or filters (LLM08); redact PII before sending to third-party models; check the provider's retention/training terms (DPA).
- Output handling: model output is untrusted input to HTML, SQL, shell, file paths, URLs (LLM05); block markdown image/link exfiltration.
- MCP/agent supply chain: pin and review MCP servers and plugins like dependencies; tool descriptions are prompt input (tool poisoning);
  scope OAuth tokens per server.
- Log prompts/tool calls for audit with PII redaction; red-team with garak/PyRIT/promptfoo before launch (tool choice, not a standard).

## Security
The "Do this" section is the security content. Concrete top risks for a typical small product, in order: broken object-level authorization,
leaked/long-lived credentials (repo, CI, mobile app), unpatched exploited dependencies (KEV), over-privileged cloud roles, missing logging
to notice any of these, and LLM agents with ambient permissions.

## Performance & quality
- Measure the program, not tool output volume: mean time to remediate by severity (KEV items < 7 days), % repos with SAST/SCA/secret scanning,
  % features with a threat model, false-positive rate of gates (< ~20% or developers ignore them), keys older than their rotation period.
- Crypto cost is rarely the bottleneck: Argon2id parameters tuned to ~100-500 ms on the login server, with a concurrency limit to avoid self-DoS.
  Hybrid PQ key share adds ~1.1 KB to the ClientHello; watch middleboxes that break on large ClientHellos.

## Testing & tooling
- Authz tests as unit/integration tests (matrix of role x action x object owner), abuse cases from the threat model as tests.
- CI: secrets scan, SAST, SCA, IaC scan on PR; SBOM + signature on release; DAST nightly on staging. Fuzz parsers.
- Mobile: MobSF in CI, MASTG tests for chosen MASVS controls, manual Frida session before big releases.
- Crypto: use test vectors (Wycheproof) when wrapping primitives; never test crypto by "it decrypts".
- Compliance evidence: keep CI logs, access reviews, and incident drills as artifacts (SOC 2/ISO auditors ask for them).

## Common mistakes in AI-written code
- Hard-coded keys/IVs, `AES/ECB/PKCS5Padding`, Java `Cipher.getInstance("AES")` (defaults to ECB), static GCM nonce, `SecureRandom` seeded manually.
- `TrustManager` that accepts all certs, `HostnameVerifier` returning true, `ServicePointManager.ServerCertificateValidationCallback = true`, `verify=False`.
- Password hashing with SHA-256 + salt, or PBKDF2 with 1,000 iterations; comparing MACs with `==`.
- JWT verified without pinning `alg`, or decoded (not verified) on the server; API keys embedded in mobile apps or `BuildConfig`.
- Root/jailbreak detection presented as protection for secrets; client-side checks of Play Integrity/App Attest results.
- `exported="true"` components, mutable `PendingIntent`, `addJavascriptInterface` on remote pages, custom URL schemes for OAuth callbacks.
- Logging full request/response bodies, tokens or emails "for debugging"; error messages that echo secrets.
- IAM policies with `"Action": "*"`, Dockerfiles running as root with `ENV API_KEY=...`, `latest` image tags.
- Citing outdated standards: ASVS 4 IDs, MASVS L1/L2/R, PCI DSS 3.2.1, OWASP LLM Top 10 2023 names, SafetyNet, "NIST requires password rotation".
- Claims PQ is "not standardized yet" (FIPS 203-205 are final) or that RSA/ECC are already banned (IR 8547 is a draft with 2030/2035 targets).
- Says "anonymized" for hashed or tokenized personal data; treats a cookie banner as GDPR compliance.
- LLM agents given a shared admin token, or tool calls executed without confirmation because "the system prompt says not to".

## Before you ship
- [ ] Threat model (DFD + STRIDE, LINDDUN if personal data) recorded for new flows; decisions and owners noted
- [ ] ASVS level chosen and its checks done for touched areas (MASVS for mobile)
- [ ] Authz centralized, deny by default, two-account tests pass; admin actions audited
- [ ] No secrets in code, app binaries, images or logs; secrets in a manager, rotation documented; KMS/envelope for sensitive data
- [ ] Crypto from the table above via a vetted library; TLS 1.2+ (1.3 preferred); hybrid PQ KEX where the stack supports it
- [ ] SAST, SCA, secret and IaC scans green or triaged; SBOM generated; KEV-listed deps fixed; VEX for not-affected findings
- [ ] Cloud roles least privilege, CIS highs fixed, images non-root, signed, pinned by digest
- [ ] Security logging + alerts live; IR runbook and contacts current; restore from backup tested
- [ ] security.txt valid (Expires in the future), SECURITY.md, disclosure inbox monitored
- [ ] Data inventory, retention jobs, DSAR export/delete, consent logging; privacy labels/Data safety match the code
- [ ] LLM features: tool least privilege, confirmations for side effects, output escaping, cost caps, red-team pass
- [ ] Rollback plan for security changes (key rotation, policy gates) so a bad rollout can be reverted without exposure

## Sources
- https://github.com/OWASP/ASVS/releases - ASVS 5.0.0, 2025-05-30 (accessed 2026-10-09)
- https://github.com/OWASP/mastg/releases/tag/v2.0.0 - MASTG 2.0.0 (2026-10-09)
- https://mas.owasp.org/MASVS/ - MASVS 2.1.0, MASVS-PRIVACY (2026-10-09)
- https://github.com/OWASP/maswe/releases - MASWE 1.0.0, 2026-08-17 (2026-10-10)
- https://github.com/owaspsamm/core/releases - SAMM 2.2.0 2026-07-06, 2.1.0 2024-09-18 (2026-10-10)
- https://csrc.nist.gov/pubs/sp/800/61/r3/final - SP 800-61r3, 2025-04 (2026-10-10)
- https://www.nist.gov/news-events/news/2025/03/nist-selects-hqc-fifth-algorithm-post-quantum-encryption - HQC, 2025-03-11 (2026-10-10)
- https://go.dev/doc/go1.24 , https://openjdk.org/projects/jdk/24/ , https://security.googleblog.com/2024/09/a-new-path-for-kyber-on-web.html - PQ TLS/API defaults (2026-10-10)
- https://developer.android.com/training/safetynet/attestation , https://developer.android.com/jetpack/androidx/releases/security - SafetyNet off 2025-01; security-crypto deprecated (2026-10-10)
- https://csrc.nist.gov/projects/ssdf - SSDF 1.1, SP 800-218A (2026-10-09)
- https://genai.owasp.org/llm-top-10/ - LLM Top 10 2025; https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/ - Agentic Top 10, 2025-12-09 (2026-10-10)
- https://csrc.nist.gov/pubs/fips/203/final (and /204, /205) - PQC standards 2024-08-13 (2026-10-09)
- https://csrc.nist.gov/pubs/ir/8547/ipd - IR 8547 still initial public draft (2026-10-09)
- https://openssl-library.org/news/openssl-3.5-notes/ - OpenSSL 3.5 PQC, default hybrid keyshare (2026-10-09)
- https://support.apple.com/en-us/122756 - iOS/macOS 26 X25519MLKEM768 by default (2026-10-09)
- https://www.first.org/epss/ - EPSS v4 2025-03-17; https://www.cisa.gov/known-exploited-vulnerabilities-catalog - KEV (2026-10-09)
- https://www.csoonline.com/article/4142600/cve-program-funding-secured-easing-fears-of-repeat-crisis.html - CVE contract renegotiated 2026-03 (lead; budget line unconfirmed) (2026-10-10)
- https://www.cisa.gov/sites/default/files/2026-07/2026_cisa_sbom_minimum_elements_508c.pdf - SBOM minimum elements 2026 (2026-10-09)
- https://www.enisa.europa.eu/news/the-cra-single-reporting-platform-is-launched - CRA reporting from 2026-09-11 (2026-10-09)
- https://www.orrick.com/en/Insights/2026/07/EU-AI-Act-Update-Digital-Omnibus-Finalizes-8-Compliance-Changes - AI Act omnibus dates (lead) (2026-10-09)
- https://developer.android.com/about/versions/17/behavior-changes-all - Android 17 security changes (2026-10-09)
- https://developer.android.com/about/versions/16/behavior-changes-all - Android 16 intent redirection protection (2026-10-09)
- https://developer.android.com/google/play/integrity/verdicts - Play Integrity verdicts (2026-10-09)
- https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity - App Attest (2026-10-09)
- https://www.rfc-editor.org/rfc/rfc9116 - security.txt (2026-10-09)
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html - Argon2id/PBKDF2 parameters (2026-10-09)
- https://www.linddun.org/ - LINDDUN privacy threat modeling; https://www.threatmodelingmanifesto.org/ - four questions (2026-10-09)
- Compliance sources: see `references/compliance-privacy.md`
