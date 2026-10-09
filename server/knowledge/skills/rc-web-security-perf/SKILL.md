---
name: rc-web-security-perf
description: Cross-cutting web security, performance and delivery for any language or framework (OWASP Top 10:2025, security headers/CSP/Trusted Types, CORS, cookies, CSRF, XSS, SSRF, TLS and certificate lifetimes, supply chain and CI hardening, AI/LLM web risks, Core Web Vitals, caching/CDN, images/fonts/JS cost, PWA, technical SEO, privacy/consent). Use when adding headers, auth/session cookies, CORS, file uploads, dependencies or GitHub Actions, TLS/ACME, CDN/cache config, perf budgets, or reviewing a web app before launch.
---
# Web security, performance & delivery  (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Language-agnostic. Pair with the stack skill (rc-web-frontend, rc-node-backend, rc-web-backend, rc-php, ...).
Header templates and longer tables: `references/headers-and-policies.md`. Perf and delivery details: `references/perf-delivery.md`.

## Currency check
- **OWASP Top 10:2025** (draft presented 2025-11-06 at Global AppSec DC; final early 2026, exact date unverified). A01 Broken Access
  Control (now includes SSRF), A02 Security Misconfiguration (up from #5), **A03 Software Supply Chain Failures (new, widened from
  "vulnerable components")**, A04 Cryptographic Failures, A05 Injection, A06 Insecure Design, A07 Authentication Failures,
  A08 Software or Data Integrity Failures, A09 Security Logging and **Alerting** Failures, **A10 Mishandling of Exceptional Conditions (new)**.
- **OWASP Top 10 for LLM Apps 2025**: LLM01 Prompt Injection, LLM05 Improper Output Handling, LLM06 Excessive Agency, LLM07 System Prompt Leakage, LLM10 Unbounded Consumption.
- **TLS certificate lifetimes** (CA/B Forum SC-081v3, approved 2025-04): max 200 days from 2026-03-15, 100 days from 2027-03-15,
  **47 days from 2029-03-15**; domain-validation reuse shrinks to 10 days by 2029. Manual renewal is no longer viable.
- **Let's Encrypt**: OCSP service ended 2025-08-06 (CRLs only); 6-day ("shortlived" profile) and IP-address certificates GA 2026-01-15; opt-in 45-day
  `tlsserver` profile 2026-05-13; default `classic` goes to 64 days on 2027-02-10 and 45 days on 2028-02-16.
- **Chrome Root Program**: public TLS certs with the clientAuth EKU are being phased out (subordinate CAs 2026-06-15, leaf 2027-03-15).
  Do mTLS with a private CA.
- **Chrome 154 (October 2026)** turns on "Always Use Secure Connections" by default (Chrome 147 for Enhanced Safe Browsing users): Chrome asks before the first visit to a public HTTP-only site.
- **Chrome 142 (2025-10-28)**: Local Network Access permission prompt for public pages that reach private/loopback addresses.
- **Third-party cookies**: Google dropped the removal plan (2025-04) and retired most Privacy Sandbox APIs (announced 2025-10-17;
  Topics, Protected Audience, Attribution Reporting deprecated from Chrome 144, removal targeted Chrome 150). Safari and Firefox
  still block/partition by default. Build as if 3P cookies do not exist.
- **CHIPS (`Partitioned`)**: Chrome 114+, Firefox 141+, Safari 26.2+.
- **Trusted Types**: Baseline since Firefox 148 (2026-02); Chrome 83+, Safari 26.
- **Sanitizer API `Element.setHTML()`**: Firefox 148 (2026-02-24), Chrome 146; Safari not yet. Feature-detect.
- **Core Web Vitals**: LCP, INP, CLS. INP replaced FID 2024-03-12. **LCP and INP measurable in all engines since Safari 26.2 (2025-12-12)**,
  Baseline Newly available. CrUX still only reports Chrome users.
- **zstd Content-Encoding**: Chrome 123+, Firefox 126+, Safari 26.3+ (only on macOS Tahoe / iOS 26.3+, not older macOS). Keep Brotli/gzip fallback.
- **`scheduler.yield()`**: Chromium 129+, Firefox 142+; not Safari. Always fall back.
- **Speculation Rules (prerender)**: Chromium only; Safari implementation off by default; Firefox none.
- **JPEG XL**: Safari 17+ decodes it; Chromium re-landed a Rust decoder (jxl-rs) in Chrome 145 code, still behind a flag
  (default-on date unverified). Don't ship JXL without AVIF/WebP fallback.
- **bfcache**: Chrome now allows `Cache-Control: no-store` pages into bfcache (rollout complete 2025-04), evicting on cookie change.
- **npm**: classic tokens revoked 2025-12-09; `npm login` gives 2-hour sessions; write tokens capped at 90 days; use
  OIDC trusted publishing. `min-release-age` (days) in npm 11.10.0 (2026-02); pnpm `minimumReleaseAge` (minutes; pnpm 11 defaults to 1440).
  Dependabot version updates wait 3 days by default since 2026-07-14 (security updates are not delayed).
- **Shai-Hulud worm** (npm): 2025-09 wave (500+ packages, `postinstall`) and 2025-11 "Second Coming" (600-800 packages, `preinstall`):
  stole npm/GitHub/cloud tokens, self-republished via the victim's packages.
- **GitHub Actions**: `pull_request_target` always runs the default branch's workflow and ref since 2025-12-08; org/enterprise
  policy can enforce full-SHA pinning and block actions (2025-08-15).
- **NIST SP 800-63B-4** final 2025-07: 15-char minimum for password-only, 8 with MFA; no composition rules, no forced rotation; blocklist check; allow paste.
- **iOS web apps**: Declarative Web Push (no service worker needed) iOS/iPadOS 18.4+, Safari 18.5 macOS; iOS 26 opens every Home
  Screen site as a web app. Push still needs Home Screen install on iOS.
- **EU**: Digital Omnibus (proposed 2025-11-19) would move cookie rules into GDPR (Art. 88a) and exempt aggregated first-party
  analytics; still in legislation as of 2026-10. Current ePrivacy consent rules apply until it passes.
- **AI crawler preferences**: IETF `aipref` (Content-Usage in robots.txt / HTTP header) is still a working-group draft (2026-09). Not a standard yet.

## What changed / stop doing
| Old advice | Do instead | Since |
|---|---|---|
| `X-XSS-Protection: 1; mode=block` | Omit it or send `0`; MDN marks it deprecated and it can create XSS. Use CSP | Browsers removed auditors 2019-2020 |
| HPKP / `Public-Key-Pins` | Nothing. CT + CAA + short-lived certs | Removed Chrome 72 (2019) |
| `Expect-CT` | Drop it; CT is enforced by browsers | Deprecated |
| CSP allow-lists + `'unsafe-inline'` | Nonce or hash + `'strict-dynamic'`, `object-src 'none'`, `base-uri 'none'` | CSP3 |
| `X-Frame-Options` only | `frame-ancestors` in CSP (keep XFO only for very old clients) | CSP2 |
| `report-uri` only | `report-to` + `Reporting-Endpoints` header (keep `report-uri` too: Firefox support of report-to lags, unverified) | Reporting API |
| JWT/session in `localStorage` | `HttpOnly; Secure; SameSite=Lax` cookie, `__Host-` prefix; BFF pattern for SPAs | - |
| `Access-Control-Allow-Origin: *` with credentials | Exact-origin allow-list, `Vary: Origin` (browsers reject `*` + credentials anyway; reflecting Origin blindly is worse) | Fetch spec |
| CSRF tokens on everything or nothing | SameSite=Lax default + Fetch Metadata (`Sec-Fetch-Site`) / Origin check; tokens where legacy clients matter | Sec-Fetch-* in all engines since Safari 16.4 (2023-03) |
| MD5/SHA-1 for passwords or integrity | Argon2id (or scrypt/bcrypt) for passwords; SHA-256+ / SRI sha384 | - |
| Password complexity rules + 90-day rotation | Length (15+ alone), breached-password check, passkeys, MFA | NIST 800-63B-4, 2025-07 |
| SMS OTP as strong MFA | Passkeys (WebAuthn); TOTP as fallback | - |
| "jQuery from a CDN" with no SRI | Self-host from the bundle, or `integrity` + `crossorigin` | - |
| Plan for 1-year (398-day) certs, manual renewal | ACME automation, ARI (renewal info), monitor expiry | SC-081v3 (2026-03-15) |
| OCSP stapling as a must | Short-lived certs; Let's Encrypt has no OCSP | 2025 |
| Long-lived npm/PyPI tokens in CI | OIDC trusted publishing, provenance | npm 2025-12-09 |
| `uses: actions/x@v4` (tag) | Pin to full commit SHA + Dependabot updates it | - |
| FID as the responsiveness metric | INP <= 200 ms (p75) | 2024-03-12 |
| Chasing the Lighthouse/PageSpeed score | Field data (CrUX/RUM) at p75; Lighthouse is a lab debugging tool | - |
| Sprite sheets, base64-inline every asset, domain sharding | HTTP/2-3 multiplexing, inline only tiny critical CSS/SVG | HTTP/2 |
| `<link rel=prerender>` | Speculation Rules `<script type="speculationrules">` | Chrome 109 |
| `unload` handlers | `pagehide` / `visibilitychange` (unload blocks bfcache, being deprecated in Chrome) | - |
| Lazy-load everything incl. hero | Hero image eager + `fetchpriority="high"`; lazy below the fold only | - |
| Chrome third-party cookie "phase-out" planning around Privacy Sandbox | Privacy Sandbox APIs are being removed; use CHIPS / Storage Access API / first-party designs | 2025-10 |

## Do this

### Security headers (baseline for HTML responses)
```
Content-Security-Policy: script-src 'nonce-{RANDOM}' 'strict-dynamic'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'; require-trusted-types-for 'script'; report-to csp
Reporting-Endpoints: csp="/csp-report"
Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), browsing-topics=()
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Resource-Policy: same-origin
```
- New nonce per response (>=128 bits, base64). Cached HTML + nonces do not mix: use hashes for static pages.
- Roll out CSP as `Content-Security-Policy-Report-Only` first; read reports; then enforce.
- `require-trusted-types-for 'script'` only after auditing sinks (`innerHTML`, `eval`, `script.src`); start Report-Only.
- `COOP: same-origin` breaks OAuth/payment popups that need `window.opener`: use `same-origin-allow-popups` there.
- COEP (`require-corp` or `credentialless`) only when you need `SharedArrayBuffer`/high-res timers (cross-origin isolation).
- HSTS preload: `max-age>=31536000`, `includeSubDomains`, `preload`, HTTP->HTTPS on same host first, every subdomain on HTTPS.
  Removal takes months: only preload when all subdomains are HTTPS forever.

### Cookies
```
Set-Cookie: __Host-sid=...; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=28800
```
- `__Host-` = Secure, no Domain, Path=/ (cannot be overwritten by a subdomain). `__Secure-` when you need Domain.
- `SameSite=Strict` for high-value admin apps; `None` only for real cross-site embeds, plus `Secure` and usually `Partitioned`.
- Rotate the session id on login and privilege change; server-side invalidation on logout.

### CSRF in the SameSite era
- SameSite=Lax blocks most cross-site POSTs but NOT same-site (sibling subdomain) attacks or GET-with-side-effects.
- Reject state-changing requests unless `Sec-Fetch-Site` is `same-origin` or `none` (so `same-site` sibling subdomains are
  rejected too; allow-list trusted origins explicitly); fall back to an `Origin` vs Host check when Sec-Fetch-Site is missing.
  Go 1.25+ ships this as `http.CrossOriginProtection` (see rc-go).
- Never change state on GET. JSON APIs: require `Content-Type: application/json` (forces CORS preflight).

### CORS
- Allow-list exact origins; echo the matched origin, add `Vary: Origin`. Never regex `.*example.com` (matches `evilexample.com`).
- `null` origin is attacker-controllable (sandboxed iframes): never allow it.
- CORS is not auth: it only controls what browsers let JS read. Server must still authorize every request.

### XSS by context
- Default to framework auto-escaping. Escape hatches are the bugs: `dangerouslySetInnerHTML`, `v-html`, `[innerHTML]`/`bypassSecurityTrust*`,
  `{@html}`, `|safe`/`{!! !!}`/`mark_safe`, `html/template` vs `text/template`.
- URL context: validate scheme (`https:`/`mailto:`) before `href`; `javascript:` survives HTML escaping.
- Rich HTML from users: `el.setHTML(html)` where supported, else DOMPurify; enforce with Trusted Types.
- Markdown renderers: disable raw HTML or sanitize the output.

### SSRF (now under A01) and agent tools
- Allow-list destination hosts; resolve DNS once, check the IP is public, connect to that IP (prevents DNS rebinding).
- Block 169.254.169.254, `fd00:ec2::254`, 127/8, 10/8, 172.16/12, 192.168/16, ::1, link-local, `metadata.google.internal`.
  On AWS require IMDSv2 (hop limit 1). Disable redirects or re-check every hop.
- Applies to webhooks, URL previews, PDF/image renderers, importers, and LLM tools that fetch URLs.

### Injection, XXE, deserialization, uploads, redirects
- SQL: parameterized queries only; identifiers (sort column) from an allow-list. NoSQL: reject objects where strings are expected (`{"$gt":""}`).
- Command: no shell; pass argv arrays (`execFile`, `subprocess.run([...])`). Template injection: never compile user strings as templates.
- XXE: disable DTDs/external entities (defaults are safe in most modern parsers; Java and old libxml configs are not).
- Deserialization: never `pickle`/Java native/PHP `unserialize`/YAML full-load on untrusted data; use JSON + schema validation.
- Uploads: size limit, sniff type server-side, random filename, store outside web root or on object storage, serve from another
  domain with `Content-Disposition: attachment` and `nosniff`; re-encode images (strips metadata/polyglots); scan if needed.
- Open redirects: only relative paths or allow-listed hosts; reject `//evil`, `/\evil`, `https:evil`.
- Clickjacking: `frame-ancestors`.
- SRI on any third-party script/style: `integrity="sha384-..." crossorigin="anonymous"`.

### TLS / DNS
- TLS 1.2 + 1.3 only, ECDHE AEAD suites; HTTP/3 where the CDN offers it. Automate ACME (cert-manager, Caddy, certbot, lego) with
  ARI-aware renewal; alert on expiry < 1/3 lifetime.
- CAA records naming your CA(s); monitor Certificate Transparency (crt.sh, CT monitors) for unexpected certs.
- DNSSEC: worth it for registrars/zones you control end to end; low browser value; misconfiguration = outage. Lock the registrar account with MFA.

### Secrets
- Nothing secret in front-end bundles: every `NEXT_PUBLIC_*`, `VITE_*`, `PUBLIC_*` value is public.
- Pre-commit + CI secret scanning (gitleaks/TruffleHog; GitHub push protection). A leaked key is rotated, not just deleted from history.
- Short-lived credentials via OIDC (cloud, registries) instead of static keys in CI.

### Supply chain (A03)
- Commit lockfiles; CI uses `npm ci` / `pnpm install --frozen-lockfile` / `pip install --require-hashes` / `go mod verify`.
- Cooldown on new versions (npm `min-release-age`, pnpm `minimumReleaseAge`, Renovate `minimumReleaseAge`, Dependabot `cooldown`).
- Block install scripts by default (pnpm 10+ runs none unless allow-listed: `onlyBuiltDependencies` in pnpm 10, `allowBuilds` in pnpm 11; npm `--ignore-scripts` in CI).
- Publish with provenance (npm `--provenance`, PyPI trusted publishers, Sigstore/cosign for images). Generate SBOM (CycloneDX/SPDX via Syft).
- Dependency Review action on PRs; watch typosquats (new deps with few downloads, near-miss names, recently transferred ownership).
- GitHub Actions: pin by SHA; `permissions: {}` at top, grant per job; never check out or run PR code in `pull_request_target` or
  `workflow_run`; avoid `${{ github.event.* }}` inside `run:` (pass via `env:`); `persist-credentials: false`; scan with zizmor.

### Authentication
- Passkeys first (WebAuthn, conditional UI `autocomplete="username webauthn"`); keep password + TOTP fallback.
- Rate limit per account and per IP/ASN; breached-password check (k-anonymity HIBP API); generic error messages; bot challenge after N failures.
- Account recovery is the weakest link: same strength as login, notify on every change, delay + notify for email/2FA changes.

### AI-specific web risks
- Any fetched page, email, PDF or tool result is untrusted input that can carry instructions (indirect prompt injection).
- Least privilege per tool; human confirmation for side effects (payments, sending, deleting); no ambient credentials in agent tools.
- Treat model output as untrusted: escape before HTML, never pass to shell/SQL/eval; block markdown image/link exfiltration
  (render only allow-listed URLs).
- Agent URL fetchers need the same SSRF controls as above. Cap tokens/cost per user (LLM10).

### Incident basics
- Log auth events, authz denials, admin actions, with request id; never log secrets/tokens. Alert, not just log (A09).
- Runbook: contain (revoke keys, disable accounts), preserve logs, rotate, patch, notify (GDPR: authority within 72 h of awareness).
- Fail closed (A10): an exception in an authz check must deny.

## Security
The section above is the security content. Testing tools are under "Testing & tooling".

## Performance & quality
- **Core Web Vitals** at the 75th percentile of real page loads, mobile and desktop separately:
  LCP good <= 2.5 s (poor > 4 s); INP good <= 200 ms (poor > 500 ms); CLS good <= 0.1 (poor > 0.25).
- **Field first**: CrUX (Chrome only, 28-day rolling), Search Console, your own RUM with the `web-vitals` library (attribution build
  shows the slow element/interaction). Lighthouse/PageSpeed lab scores don't measure INP and are for debugging only.
- **LCP**: server TTFB < 0.8 s; LCP image discoverable in HTML (not CSS/JS), `fetchpriority="high"`, not lazy; preconnect to its origin;
  AVIF/WebP with `srcset`/`sizes`; avoid client-side rendering of the hero.
- **INP**: break long tasks (>50 ms) with `await scheduler.yield()` (fallback `setTimeout(0)`); less hydration (islands, server
  components, resumability); defer third-party tags; avoid layout thrash.
- **CLS**: width/height or `aspect-ratio` on media; reserve ad/embed slots; `font-display: optional` or metric-matched fallback
  (`size-adjust`); animate `transform` only.
- **Delivery**: HTTP/2+ (HTTP/3 at the CDN), Brotli for static (precompress), zstd/Brotli for dynamic; hashed assets
  `Cache-Control: public, max-age=31536000, immutable`; HTML `no-cache` + ETag or short `s-maxage` + `stale-while-revalidate`.
- **Navigation**: keep pages bfcache-eligible (no `unload`, close connections on `pagehide`); Speculation Rules prerender for likely next pages (Chromium).
- **Budgets**: JS < ~150-200 KB compressed on the critical path for mobile (team target, not a standard); fonts: 1-2 WOFF2 variable files, subset, preload only the above-the-fold one.
- Details, image/font/PWA/SEO/privacy tables: `references/perf-delivery.md`.

## Testing & tooling
- DAST: OWASP ZAP (baseline scan in CI against staging). Burp Suite for manual testing.
- SAST: Semgrep (OSS rules), CodeQL (free on public GitHub repos). Fix high-confidence findings; triage the rest.
- Dependencies: Dependabot or Renovate (with cooldown, grouped PRs), `npm audit`/`pip-audit`/`govulncheck`/OSV-Scanner.
- Secrets: gitleaks or TruffleHog pre-commit + CI; GitHub secret scanning + push protection.
- CI: zizmor (Actions linter), OpenSSF Scorecard. Headers: check with securityheaders-style scanners and the CSP Evaluator.
- Perf: Lighthouse CI (budgets, regressions), WebPageTest, Chrome DevTools Performance panel (live CWV, field data overlay), RUM.
- Pentest basics: test authz with two accounts (IDOR), every role, every API verb; test with the browser bypassed (curl).

## Common mistakes in AI-written code
- Adds `X-XSS-Protection: 1; mode=block`, `Expect-CT`, or `Public-Key-Pins`. Remove them.
- CSP with `'unsafe-inline' 'unsafe-eval'` "to make it work", or a static nonce baked into a template/cached page.
- CORS middleware with `origin: true` / reflecting any Origin plus `credentials: true`.
- Stores JWTs in `localStorage`; signs JWTs with `alg: none` accepted or HS256 with a short secret; never checks `aud`/`iss`/`exp`.
- `helmet()`-style defaults assumed sufficient; CSP left at the library default or disabled.
- SSRF "fix" that blocks the string `169.254.169.254` but follows redirects or allows decimal/IPv6 forms.
- `sanitize-html`/regex escaping hand-rolled instead of framework escaping or `setHTML`/DOMPurify.
- `pull_request_target` + `actions/checkout` with `ref: ${{ github.event.pull_request.head.sha }}`.
- Uses `<img loading="lazy">` on the hero, or `preload` for every font and script (preload is a scarce priority slot).
- Measures FID, or claims Lighthouse 100 = good CWV.
- Suggests JPEG XL as a primary format, or `<link rel="prerender">`.
- Sets `Cache-Control: no-store` everywhere "for security" (kills CDN caching; use `private, no-cache` for user pages, no-store only for truly sensitive responses).
- Puts API keys in `VITE_`/`NEXT_PUBLIC_` env vars.
- MD5/SHA-1 for anything security-relevant; `Math.random()` for tokens (use `crypto.getRandomValues`/`secrets`).
- Treats the LLM's tool output or a fetched web page as trusted instructions.

## Before you ship
- [ ] HTTPS only, HSTS, cert automation with alerting; CAA set; no mixed content (Chrome 154 warns on HTTP)
- [ ] CSP (nonce/hash + strict-dynamic) enforced, reports collected; `frame-ancestors`, `nosniff`, Referrer-Policy, Permissions-Policy
- [ ] Session cookies `__Host-`, HttpOnly, Secure, SameSite; CSRF via Fetch Metadata/Origin; CORS allow-list
- [ ] Authz checked server-side on every object (two-account IDOR test); rate limits on login, signup, reset, expensive APIs
- [ ] Outbound fetches behind SSRF guard; uploads isolated; redirects allow-listed
- [ ] Lockfile committed, cooldown + Dependabot/Renovate, install scripts restricted, Actions pinned by SHA with minimal permissions
- [ ] Secret scan clean; no secrets in client bundles; rotation plan documented
- [ ] CWV p75 in field: LCP <= 2.5 s, INP <= 200 ms, CLS <= 0.1; RUM in place
- [ ] Caching: immutable hashed assets, sensible HTML caching, Brotli/zstd on
- [ ] Canonicals, sitemap, robots.txt reviewed (incl. AI crawler choices); consent banner matches regions served
- [ ] Logging + alerting on auth/admin events; incident runbook and contacts; `/.well-known/security.txt`

## Sources
- https://top10.owasp.org/2025/ - Top 10:2025 list (accessed 2026-10-09)
- https://top10.owasp.org/2025/0x00_2025-Introduction/ - changes vs 2021, SSRF merged into A01 (2026-10-09)
- https://www.scworld.com/resource/owasp-global-appsec-conference-the-new-top-10-list - 2025-11-06 presentation (lead only) (2026-10-09)
- https://genai.owasp.org/llm-top-10/ - LLM Top 10 2025 list (2026-10-09)
- https://cheatsheetseries.owasp.org/ - Cheat Sheet Series index; CSRF cheat sheet Fetch Metadata guidance (2026-10-09)
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/X-XSS-Protection - deprecated, use CSP (2026-10-09)
- https://hstspreload.org/ - preload requirements and removal caveats (2026-10-09)
- https://cabforum.org/2025/04/11/ballot-sc081v3-introduce-schedule-of-reducing-validity-and-data-reuse-periods/ - SC-081v3 200/100/47 days, 10-day DCV reuse (2026-10-09)
- https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability - 6-day and IP certs GA (2026-10-09)
- https://letsencrypt.org/2025/12/02/from-90-to-45 - Let's Encrypt 45/64-day schedule; https://letsencrypt.org/2025/08/06/ocsp-service-has-reached-end-of-life - OCSP end (2026-10-09)
- https://googlechrome.github.io/chromerootprogram/crp/policy - Chrome Root Program Policy v1.8, serverAuth-only dates (2026-10-09)
- https://github.blog/changelog/2026-07-14-dependabot-version-updates-introduce-default-package-cooldown/ - Dependabot 3-day default cooldown (2026-10-09)
- https://pkg.go.dev/net/http#CrossOriginProtection - Go 1.25 CSRF protection (2026-10-09)
- https://blog.google/security/https-by-defau/ - HTTPS by default, Chrome 154 Oct 2026 (2026-10-09)
- https://developer.chrome.com/blog/local-network-access - Local Network Access prompt, Chrome 142 (2026-10-09)
- https://privacysandbox.google.com/cookies/chips - CHIPS; https://caniuse.com/wf-partitioned-cookies - support (2026-10-09)
- https://caniuse.com/trusted-types and https://webkit.org/blog/17333/webkit-features-in-safari-26-0/ - Trusted Types (2026-10-09)
- https://hacks.mozilla.org/2026/02/goodbye-innerhtml-hello-sethtml-stronger-xss-protection-in-firefox-148/ - setHTML in Firefox 148 (2026-10-09)
- https://web.dev/blog/lcp-and-inp-are-now-baseline-newly-available - Safari 26.2 LCP/INP, 2025-12-12 (2026-10-09)
- https://web.dev/articles/vitals - CWV thresholds (2026-10-09)
- https://caniuse.com/mdn-api_scheduler_yield - scheduler.yield support (2026-10-09)
- https://caniuse.com/zstd and https://webkit.org/blog/17798/webkit-features-for-safari-26-3/ - zstd (2026-10-09)
- https://developer.chrome.com/docs/web-platform/bfcache-ccns - bfcache with no-store (2026-10-09)
- https://www.phoronix.com/news/JPEG-XL-Returns-Chrome-Chromium - JXL back in Chromium code (lead only) (2026-10-09)
- https://github.blog/changelog/2025-11-07-actions-pull_request_target-and-environment-branch-protections-changes/ - pull_request_target change 2025-12-08 (2026-10-09)
- https://github.blog/changelog/2025-08-15-github-actions-policy-now-supports-blocking-and-sha-pinning-actions/ - SHA pinning policy (2026-10-09)
- https://github.blog/changelog/2025-12-09-npm-classic-tokens-revoked-session-based-auth-and-cli-token-management-now-available/ - npm tokens (2026-10-09)
- https://pnpm.io/supply-chain-security and https://pnpm.io/blog/releases/11.0 - minimumReleaseAge default, `allowBuilds` (2026-10-09)
- https://www.cisa.gov/news-events/alerts/2025/09/23/widespread-supply-chain-compromise-impacting-npm-ecosystem - Shai-Hulud wave 1; wave 2 figures from vendor reports (2026-10-09)
- https://pages.nist.gov/800-63-4/sp800-63b.html - NIST 800-63B-4 password rules (2026-10-09)
- https://webkit.org/blog/16535/meet-declarative-web-push/ - Declarative Web Push (2026-10-09)
- https://words.filippo.io/csrf/ - Fetch Metadata CSRF design behind Go's CrossOriginProtection (2026-10-09)
- https://datatracker.ietf.org/wg/aipref/ - AI preferences drafts status (2026-10-09)
