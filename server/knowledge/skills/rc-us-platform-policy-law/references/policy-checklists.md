# Document-by-document checklists and policy-vs-code reconciliation  (as of 2026-10-10)

> Drafting aid, not legal advice. Must = required by a law or store rule that applies to most US apps/sites (check which apply,
> SKILL.md section 3). Should = prudent practice. Don't = deceptive, unenforceable or risky.

## Privacy policy
- Must: effective date and version; who you are and how to contact you (privacy contact, not a personal address); categories of
  personal information collected, sources, purposes; categories of recipients (service providers, payment processor, hosting,
  analytics, AI vendors); whether you sell, "share" (CA) or use data for targeted ads; retention per category or the criteria; rights
  available and how to use them (form, email, in-app), how you verify, response time, appeal process (most state laws); GPC/opt-out
  signal handling; Do Not Track statement (CalOPPA); children (under-13 and teen handling); how users are told about changes; data
  deletion and account deletion paths (Apple, Google); security in honest, specific terms.
- Should: layered summary at the top ("What we collect", "What we don't do"); a table of data -> purpose -> retention; list of
  vendors by name when short (Rhode Island and Oregon lean this way); state-specific section instead of 20 separate notices;
  change log at the bottom.
- Don't: "we collect no data" when any account, log or payment exists; "we may" for things you do (say "we do"); promises you
  can't keep (instant deletion from backups); GDPR terms in a US-only product; a PDF; a geofenced URL.

## Terms of service / terms of use
- Must: who can use it (age minimum, `[OWNER DECISION: 13+/16+/18+]`); account responsibilities; fees, billing, renewal and
  cancellation (or link to the subscription terms); license to use the service; license you need to user content (narrow: host,
  display, process to run the service) and that users keep ownership; acceptable use (or linked AUP); termination by either side and
  what happens to data; disclaimers; limitation of liability "to the extent permitted by law"; dispute resolution and governing law;
  how terms change and how users are told; contact.
- Should: plain-language summary per section; separate AUP and community guidelines; DMCA and TAKE IT DOWN procedures linked;
  notice-and-cure before termination for non-severe breaches; survival clause.
- Don't: waive rights consumers can't waive (statutory damages, gross negligence, personal injury); unilateral "we can change
  anything at any time without notice" (enforceability risk); arbitration buried without clear assent; "no refunds" that conflicts with
  state law or store rules.

## EULA (installed apps, desktop software, CLIs)
- Must: license grant (personal, non-exclusive, revocable, non-transferable), restrictions (reverse engineering only "except as
  permitted by law"), ownership, updates, termination, warranty disclaimer, liability limits, open-source notices (licenses of
  bundled components, e.g., Apache-2.0 NOTICE files).
- Should: for iOS, either rely on Apple's Standard EULA or include Apple's minimum terms in a custom one (Apple's instructions
  for custom EULAs; secondary). Export-control and government-user clauses only if relevant.
- Don't: claim ownership of user files; prohibit benchmarking or security research without thinking about it.

## Acceptable use policy and community guidelines / moderation
- Must (UGC platforms): prohibited content and conduct in plain words; how to report; what happens (removal, warning, suspension);
  appeal path; NCII removal within 48 hours (TAKE IT DOWN); repeat-infringer policy (DMCA); law-enforcement request handling summary.
- Should: examples for gray areas; transparency about automated moderation; response targets you can meet; state laws on
  moderation transparency (e.g., California AB 587 reports for large platforms, secondary) only if thresholds apply.
- Don't: promise to review every report within minutes; publish moderator names.

## Refund and cancellation; subscription disclosures
- Must: price, billing period, renewal, trial length and conversion date, price after trial, how to cancel (online, same medium as
  sign-up), effect of cancellation (end of period), refunds rule; express consent before charging (ROSCA); confirmation with terms;
  annual reminder and change-of-terms notice (CA); renewal notice 15-45 days ahead for terms over 60 days (NC).
- Should: cancellation in two clicks; pro-rated refunds where cheap to offer; one support contact for billing; state that IAP refunds
  go through Apple/Google and link their refund pages.
- Don't: monthly price shown for an annual plan; pre-checked boxes; "call to cancel" for online sign-ups; save-offer loops.

## DMCA procedure
- Must: designated agent (name/role, address, phone, email) on the site and in the Copyright Office directory, renewed every 3 years;
  required notice elements (signature, work, infringing material and location, contact, good-faith statement, accuracy and
  authority statement under penalty of perjury); counter-notice steps; repeat-infringer termination.
- Don't: require a lawyer letter; ignore notices about your own content.

## Security and vulnerability disclosure policy
- Must: where to report (security.txt `Contact`, a form or email), what's in scope, a good-faith safe-harbor statement, no-legal-action
  promise for good-faith research within the rules, response expectation you can keep.
- Should: `/.well-known/security.txt` with `Expires`; acknowledgments page; no bug bounty promise unless funded.
- Don't: promise a payout or a fixed fix timeline; threaten researchers.

## Age restrictions and children's privacy
- General-audience app: state the minimum age; if you learn a child under 13 signed up, delete the data; don't ask age in a way that
  invites lying (neutral age screen). Respond to state teen rules where you meet thresholds.
- Child-directed (or mixed audience): full COPPA notice (direct notice to parents + online notice), verifiable parental consent,
  separate consent for third-party disclosure, written retention policy published, written security program, parental access and
  deletion. Involve counsel.
- Don't: "children under 13 may not use this app" while marketing to kids; collect more than needed for an activity.

## Accessibility statement
- Should: standard targeted (WCAG 2.2 AA), date of last review, known gaps and workarounds, how to report a barrier and get help,
  response time you can keep. Don't: claim "fully accessible" or "ADA compliant"; rely on an overlay widget as the fix.

## Cookie and tracking notice
- If you use no non-essential cookies, SDK trackers or pixels: say so in the privacy policy; no banner needed for that purpose in the
  US. If you do: list categories (strictly necessary, preferences, analytics, ads), vendors, opt-out (and GPC), and honor it before the
  tags fire. Don't: banner that loads trackers before the choice; "by continuing you accept" for targeted ads in opt-in states.

## Data retention and deletion policy
- Must: retention per category (account, content, logs, payments, support, backups); what account deletion removes and when; what is
  kept and why (tax records, fraud, legal holds) and for how long; deletion requests to processors; backup age-out period.
- Should: a deletion job with logs; test it; one table shared with engineering.

## Reconcile the policy with the code (run before publishing and every release)
1. **Dependencies and SDKs**: list them and classify each (does it send data off-device? to whom?).
   - Android: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`; check `AndroidManifest.xml` permissions and
     merged manifest (`app/build/intermediates/merged_manifest/`); search for `firebase`, `crashlytics`, `sentry`, `analytics`, `ads`.
   - iOS: `Package.resolved`, `Podfile.lock`; every `PrivacyInfo.xcprivacy` in the app and its SDKs; `Info.plist` usage strings.
   - Web/Node: `package-lock.json` / `pnpm-lock.yaml`; script tags and tag managers in HTML; server middleware logging.
   - Server: log format and retention (`journald`, nginx/Caddy access logs, app logs), backup schedule, error trackers.
2. **Network calls**: grep for hostnames and HTTP clients (`grep -rnE "https?://" src app ios --include=*.kt --include=*.swift --include=*.ts`);
   run the app through a proxy (mitmproxy, Charles) or browser devtools for a full session; list every third-party domain contacted.
3. **Analytics and crash reporting**: what events, what IDs, sampling, retention in the vendor dashboard.
4. **Push**: which provider (FCM, APNs, Web Push), what is stored server-side (token + user ID), how a user stops it.
5. **Accounts and payments**: what the auth provider stores; what the payment processor returns to you (never card numbers).
6. **AI features**: which model provider receives prompts/files, their retention and training terms, whether users are told (Apple 5.1.2).
7. **Credentials the product handles** (SSH keys, API tokens): stored where, encrypted how, ever transmitted to your servers?
8. Write the result into the data inventory (SKILL.md section 2). Any mismatch: fix the code or fix the policy before release.

## Change history and review
- Keep at the bottom of each document: version, date, summary of changes. Keep prior versions retrievable.
- Review triggers: new SDK/vendor/data type/permission; new state threshold; new law in force; store policy change; incident;
  at least every 12 months.
