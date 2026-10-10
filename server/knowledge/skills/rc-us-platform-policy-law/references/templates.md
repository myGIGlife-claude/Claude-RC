# Fill-in skeletons: privacy policy and terms of service  (as of 2026-10-10)

> Starting outlines, not finished documents and not legal advice. Fill every `[BRACKET]` from the data inventory and the owner's
> answers; delete sections that don't apply instead of leaving them vague. `[OWNER DECISION: ...]` marks a business or legal
> choice Claude must not make alone. Never ship placeholder names, addresses or emails. Write in plain American English
> (rc-american-english-writing): short sentences, "we" and "you", say what you do, not what you "may" do.

## Privacy policy skeleton

```
# Privacy Policy

Effective date: [YYYY-MM-DD]    Version: [N]

## The short version
- We collect [the minimum list, e.g., your email address to run your account, and basic server logs to keep the service secure].
- We don't sell your personal information or share it for targeted advertising. [Delete if untrue.]
- We don't use advertising trackers. [Delete if untrue.]
- You can see, download, correct or delete your data at [in-app path] or [web link].

## Who we are
[Legal entity name as shown in the app store listing], [business mailing address]. Privacy questions: [privacy contact: form URL
or role-based email].

## What we collect and why
| What | Where it comes from | Why we use it | How long we keep it |
|---|---|---|---|
| [Email address] | You, when you sign up | [Sign-in, account notices] | [Until you delete your account] |
| [Payment details: our processor gives us a customer ID, card brand and last 4 digits, never the full card number] | [Processor] | [Billing, refunds, tax records] | [N years, for tax law] |
| [Server logs: IP address, time, request, device type] | Your device | [Security, debugging, abuse prevention] | [N days] |
| [Crash reports] | The app | [Fixing bugs] | [N days] |
| [Push notification token] | Your device | [Sending the alerts you turn on] | [Until you turn alerts off or delete the account] |
| [Content you create] | You | [Providing the service] | [Until you delete it] |
[Add a row per item in the data inventory. If the app stores keys or credentials, say where (on your device / on our servers),
whether they ever leave the device, and how they are protected.]

## What we don't collect
[Only list things that are true and that users might worry about, e.g., "We don't access your contacts or precise location."]

## Who we share it with
We share personal information only with companies that help us run the service, under contracts that limit their use of it:
[hosting provider], [payment processor], [email delivery], [crash reporting], [AI provider, if prompts or files are sent].
[OWNER DECISION: name vendors, or list categories.] We disclose information when the law requires it [describe your approach
to legal requests]. If we sell or merge the business, your information may transfer with it under this policy.

## Sale, sharing and targeted ads
[True statement for each: sale, "sharing" for cross-context behavioral advertising, targeted ads, profiling with legal effects.]
Global Privacy Control: [we treat a GPC signal as a request to opt out of sale and sharing for that browser and, if you're signed
in, your account / we don't sell or share, so GPC changes nothing, but we honor it if that changes].

## Your choices and rights
Depending on where you live, you can ask to: see the personal information we have about you, get a copy, correct it, delete it,
and opt out of sale, sharing, targeted advertising and certain profiling. You can also limit our use of sensitive information
[if applicable]. To make a request: [in-app path], [web form], [toll-free number if required]. We'll confirm it's you by
[method]. We answer within [45] days [and tell you if we need more time]. If we deny your request, you can appeal by [method];
if you disagree with the result, you can contact your state attorney general. We won't treat you differently for using your rights.
[Authorized agents: how they submit requests.]

## Deleting your account
[In-app path] or [web link]. We delete your account data within [N] days. Copies in backups are overwritten within [N] days.
We keep [invoices for N years for tax law; records needed to prevent fraud] and nothing else.

## Children
[General audience:] The service isn't for children under [13 / OWNER DECISION]. If we learn we collected information from a
child under 13 without parental consent, we delete it. [Child-directed: replace with a full COPPA notice drafted with counsel.]

## Security
[Specific and true, e.g., "Data is encrypted in transit (TLS) and at rest. Access is limited to staff who need it and requires
two-factor sign-in."] No system is perfectly secure; if a breach affects you, we'll notify you as the law requires.

## State-specific information
[Only for states whose laws apply: required disclosures for California (categories in the past 12 months, sources, purposes,
recipients, retention), and any state with extra notice rules.]

## Changes to this policy
We'll post the new version here and tell you by [email / in-app notice] before material changes take effect. [For material
changes to how we use data we already have: we'll ask for your consent.]

## Contact
[Privacy contact], [mailing address].

## Change history
- [YYYY-MM-DD] Version [N]: [what changed].
```

## Terms of service skeleton

```
# Terms of Service

Effective date: [YYYY-MM-DD]    Version: [N]

These terms are an agreement between you and [legal entity name] ("we"). By [creating an account / tapping "Agree"], you
accept them. Please also read our Privacy Policy [link].

## 1. Who can use [product]
You must be at least [OWNER DECISION: 13 / 16 / 18] [and able to form a contract]. [Business accounts: you confirm you can bind
your organization.]

## 2. Your account
Keep your sign-in details safe and tell us at [contact] if someone uses your account without permission. You're responsible
for activity under your account [unless caused by our failure].

## 3. Plans, payments and renewal
[Free / paid plans.] Paid plans renew automatically every [period] at [price, or "the price shown at checkout"] until you
cancel. Free trials turn into paid plans on [the date shown at signup] unless you cancel before then. Cancel anytime in
[in-app path / web link / your App Store or Google Play subscription settings]. Cancellation takes effect at the end of the
current period. Refunds: [OWNER DECISION: policy]. Purchases through Apple or Google follow their refund processes [links].
We'll tell you before a price change takes effect, and you can cancel before it does.

## 4. Your content
You own what you create. You give us permission to host, store, copy, and display it only to run and improve [product] for you
[OWNER DECISION: narrower or broader]. You're responsible for having the rights to what you upload.

## 5. Acceptable use
Don't use [product] to break the law, infringe others' rights, harass people, distribute malware, probe or overload our
systems (except under our vulnerability disclosure policy [link]), or [product-specific rules]. Full rules: [AUP link].

## 6. Copyright and intimate-image complaints
Copyright: send notices to our designated agent: [agent details / DMCA page link]. We end accounts of repeat infringers.
Non-consensual intimate images: request removal at [link]; we act within 48 hours of a valid request. [Only if UGC.]

## 7. Our service
We may change or stop features. If we discontinue a paid service, we'll [OWNER DECISION: refund unused time / give notice].
[Open-source components are licensed under their own terms: link.]

## 8. Ending this agreement
You can stop using [product] and delete your account anytime [path]. We may suspend or end your access if you seriously or
repeatedly break these terms, or if the law requires it. [Notice and chance to fix for non-urgent issues.] After termination,
[what happens to data, with link to deletion policy].

## 9. Disclaimers
[Product] is provided "as is" to the extent the law allows. [Keep warranties the law gives consumers that can't be waived.]

## 10. Limitation of liability
To the extent the law allows, [OWNER DECISION: cap, e.g., the amount you paid in the last 12 months]. This doesn't limit
liability for [gross negligence, intentional misconduct, personal injury, or anything else the law doesn't let us limit].

## 11. Disputes
[OWNER DECISION, with counsel: courts of [state] / arbitration with small-claims carve-out, opt-out window and class waiver.]
These terms are governed by the laws of [state], except where your state's consumer protection laws say otherwise.

## 12. Changes to these terms
We'll give notice by [email / in-app] at least [N] days before material changes take effect. If you keep using [product] after
that, the new terms apply; if you don't agree, you can cancel [and get a pro-rated refund, OWNER DECISION].

## 13. Contact
[Contact method], [mailing address].

## Change history
- [YYYY-MM-DD] Version [N]: [what changed].
```

## Owner questions to ask before filling these in
1. Legal entity name, state of formation, mailing address, support and privacy contacts (role-based).
2. Audience: minimum age; any child-directed content; states/countries served.
3. Every vendor and SDK (from the reconciliation checklist), and what each receives.
4. Payments: processor, IAP or web, plans, trials, refund rule.
5. Retention periods per data category, backup age-out period.
6. Ads, data sales, sharing, profiling: yes/no each.
7. Disputes: courts or arbitration; governing state.
8. UGC: moderation process, DMCA agent, NCII removal process.
