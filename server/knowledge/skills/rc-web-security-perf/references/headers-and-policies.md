# Headers and policies reference  (as of 2026-10)

## Response header cheat table
| Header | Recommended value | Notes |
|---|---|---|
| Content-Security-Policy | `script-src 'nonce-R' 'strict-dynamic'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'` | Strict CSP. `'strict-dynamic'` lets nonced scripts load others; old host allow-lists are ignored by CSP3 browsers. Add `https: 'unsafe-inline'` only as a fallback for pre-CSP3 browsers (ignored when a nonce is present). |
| Content-Security-Policy (TT) | `require-trusted-types-for 'script'; trusted-types app-policy` | Baseline 2026-02. Start with Report-Only. |
| Reporting-Endpoints | `csp="https://.../csp"` | Pair with `report-to csp`; keep `report-uri` for older engines. |
| Strict-Transport-Security | `max-age=63072000; includeSubDomains; preload` | Preload is near-permanent. Start with short max-age when unsure. |
| X-Content-Type-Options | `nosniff` | Still useful. |
| Referrer-Policy | `strict-origin-when-cross-origin` | Browser default since 2020-21; set explicitly. `no-referrer` for sensitive apps. |
| Permissions-Policy | `camera=(), microphone=(), geolocation=(), payment=()` | Deny what you don't use. `interest-cohort` is obsolete (FLoC). |
| Cross-Origin-Opener-Policy | `same-origin` (or `same-origin-allow-popups`) | Process isolation, blocks opener attacks (XS-Leaks). |
| Cross-Origin-Embedder-Policy | `require-corp` or `credentialless` | Only for cross-origin isolation (SharedArrayBuffer). |
| Cross-Origin-Resource-Policy | `same-origin` (APIs, private assets); `cross-origin` for public CDN assets | Stops other sites embedding your resources (Spectre). |
| X-Frame-Options | `DENY`/`SAMEORIGIN` optional | Superseded by `frame-ancestors`; harmless to keep. |
| Cache-Control (authenticated HTML) | `private, no-cache` | `no-store` only for truly sensitive data. |
| X-XSS-Protection | omit or `0` | Deprecated. |
| Public-Key-Pins, Expect-CT, Feature-Policy | omit | Obsolete; Feature-Policy renamed Permissions-Policy. |
| Server, X-Powered-By | remove | Minor info leak. |

## CSP nonce sketch (any server)
```
nonce = base64(random_bytes(16))           # per response
header: script-src 'nonce-{nonce}' 'strict-dynamic'; object-src 'none'; base-uri 'none'
html:   <script nonce="{nonce}" src="/app.js"></script>
```
- Inline event handlers (`onclick=`) are blocked; move to `addEventListener`.
- Static/CDN-cached pages: use `'sha256-...'` hashes of inline scripts instead of nonces.

## Trusted Types minimal policy
```js
if (window.trustedTypes) {
  trustedTypes.createPolicy('default', {
    createHTML: (s) => DOMPurify.sanitize(s, { RETURN_TRUSTED_TYPE: false }),
  });
}
```
A `default` policy is a migration crutch; prefer named policies at each sink.

## Cookie attributes
| Attribute | Use |
|---|---|
| `__Host-` prefix | Session cookies. Requires Secure, Path=/, no Domain. |
| `HttpOnly` | Any cookie JS doesn't need. |
| `SameSite=Lax` | Default for sessions (browsers default to Lax-ish when unset in Chrome; set explicitly). |
| `SameSite=None; Secure; Partitioned` | Third-party embeds (CHIPS): cookie is keyed to the top-level site. |
| `Max-Age` | Prefer over `Expires`. Chrome caps cookie lifetime at 400 days. |

Unpartitioned cross-site access in Safari/Firefox: Storage Access API (`document.requestStorageAccess()`) after a user gesture.

## CORS decision list
1. Same-origin only? Send no CORS headers.
2. Public read-only API, no cookies? `Access-Control-Allow-Origin: *` is fine.
3. Credentialed? Exact origin from allow-list, `Access-Control-Allow-Credentials: true`, `Vary: Origin`.
4. Preflight cache: `Access-Control-Max-Age: 600` (Chrome caps at 7200 s).
5. Private Network Access preflights are superseded in Chrome by the Local Network Access permission (Chrome 142).

## SSRF guard outline
```
parse URL -> scheme in {https} -> host in allow-list?
resolve all A/AAAA -> every IP public (not private, loopback, link-local, CGNAT 100.64/10, ULA fc00::/7, IPv4-mapped IPv6)
connect to the resolved IP with SNI/Host = original host; no redirects (or repeat checks per hop); timeout + size cap
```
Better: route outbound fetches through an egress proxy that enforces this.

## GitHub Actions hardening snippet
```yaml
permissions: {}
jobs:
  build:
    permissions: { contents: read }
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@<full-40-char-sha> # vX.Y.Z
        with: { persist-credentials: false }
      - run: echo "$TITLE"
        env: { TITLE: "${{ github.event.pull_request.title }}" }
```
- `pull_request_target`/`workflow_run`: never build or execute fork code with secrets in scope.
- Use OIDC (`id-token: write` only in the publish job) for cloud and registry auth.
- Self-hosted runners: never on public repos.

## Supply chain settings
| Tool | Setting |
|---|---|
| npm (11.10+) | `.npmrc`: `min-release-age=7`, CI `npm ci --ignore-scripts` (then run needed builds explicitly) |
| pnpm 10/11 | `minimumReleaseAge` (minutes; 1440 default in 11); build allow-list `onlyBuiltDependencies` (10) / `allowBuilds` map (11, old keys removed) |
| Renovate | `minimumReleaseAge: "3 days"`, `pinDigests: true` for Actions/Docker |
| Dependabot | `cooldown:` (`default-days`, `semver-*-days`) in `dependabot.yml`; 3-day default since 2026-07-14, security updates exempt |
| pip | `--require-hashes` with `pip-compile --generate-hashes` or `uv lock` |
| Publishing | npm trusted publishing (OIDC) with provenance; PyPI trusted publishers; cosign keyless signing for images |

## Auth rate-limit defaults (starting points, tune with data)
- Login: 5-10 failures per account per 15 min -> step-up challenge, not hard lock (lockout is a DoS vector).
- Per IP: sliding window; per ASN/residential-proxy signals for credential stuffing.
- Password reset/OTP send: 3-5 per hour per account; tokens single-use, 15-60 min expiry, hashed at rest.

## Privacy and legal engineering basics
- **EU/UK (GDPR + ePrivacy/PECR)**: prior opt-in for non-essential cookies/storage; "Reject all" as easy as "Accept all";
  no pre-ticked boxes; record consent; withdraw as easily as given. Strictly necessary (session, security, load balancing) is exempt.
  Digital Omnibus may exempt aggregated first-party analytics (proposal, not law as of 2026-10).
- **US (CCPA/CPRA and other state laws)**: opt-out model; "Do Not Sell or Share" link; honor Global Privacy Control
  (`Sec-GPC: 1`) as a valid opt-out in California and several other states.
- Google Consent Mode v2 is required by Google for EEA ad measurement; it does not make a non-compliant banner compliant.
- Data minimization: don't log full IPs/user agents longer than needed; set retention; DPA with every processor.
- Analytics without consent banners (EU, verify per DPA): self-hosted or cookieless tools (Plausible, Fathom, Matomo configured
  cookieless, Umami). "Cookieless" fingerprinting still needs consent.
- Breach notification: GDPR 72 hours to the authority after awareness.
