# Apple platform and App Store policy timeline (as of 2026-10-09)

All dates from developer.apple.com/news and upcoming-requirements unless marked.

## Toolchain and SDK
| Date | What |
|---|---|
| 2025-09 | iOS/iPadOS/macOS 26 (year-based numbering starts), Xcode 26, Swift 6.2 (2025-09-15) |
| 2026-03-24 | Swift 6.3 (swift.org) |
| 2026-04-28 | Uploads must use Xcode 26 + iOS/iPadOS/tvOS/visionOS/watchOS 26 SDK |
| 2026-06-08 | WWDC26: iOS 27 betas, Xcode 27 beta, App Review Guidelines + DPLA update |
| 2026-09-09 | App Store accepts iOS 27 / macOS 27 builds (Xcode 27 RC) |
| 2026-09-09 | Minimum deployment target for uploads: iOS 13 |
| 2026-09-14/15 | Xcode 27 (09-14) + Swift 6.4 (09-15) release (Apple silicon only; needs macOS Tahoe 26.6+) |
| 2026-10-23 | iPhone Duo (foldable) ships with iOS 27.1; build with Xcode 27.1; its screenshots required from April 2027 |
| 2026-12-02 | CocoaPods trunk read-only (cocoapods.org blog) |
| 2027-02-01 | Original Developer ID Sub-CA expires: re-sign Mac pkg/apps with G2 certificates (notarized + timestamped stay valid) |
| 2027-04 | Uploads must use the iOS 27 family SDKs (exact day not announced) |

## Review, privacy and age
- Privacy manifests + required-reason APIs: enforced at upload since 2024-05-01.
- Account deletion in app: guideline 5.1.1(v).
- Third-party AI: 5.1.2(i), disclose and get explicit permission before sharing personal data.
- Mini apps, HTML5 games, chatbots, plugins: allowed under 4.7, but all guidelines (privacy, UGC moderation, age gating) apply.
- Random/anonymous chat apps: treated under UGC 1.2 (2026-02-06 update), may be removed.
- External purchase links: 3.1.1(a); US storefront may link to the developer's website for digital purchases.
- Age ratings: new questionnaire (answers were due 2026-01-31); Social Media content descriptor and questions
  (required for submissions from 2026-09); Time Allowances (iOS 27) for Entertainment/Games/Social Media categories;
  regional overrides (Korea GRAC 2026-08, Australia/Vietnam changes 2026-05).
- Age assurance laws: Texas SB 2420 from 2026-06-04; Brazil, Australia, Singapore, Utah, Louisiana announced 2026-02-24.
  Use Declared Age Range API, PermissionKit Significant Change API, StoreKit age rating property, and App Store
  Server Notifications for consent revocation.
- Regulated medical device status must be declared for health/medical apps in EEA/UK/US (announced 2026-03-26;
  new apps now, existing apps by early 2027 or updates are blocked).
- Accessibility Nutrition Labels: voluntary, shown on iOS 26+ App Store; Apple says they will become required (no date).
- Export compliance: set `ITSAppUsesNonExemptEncryption`; HTTPS/OS crypto only is generally exempt (still answer the question).
- EU: DSA trader status required (apps without it removed since 2025-02-17). Alternative marketplaces and web
  distribution need notarization by Apple; new EU business terms with a Core Technology Commission (2026-08-18,
  DPLA Attachment 14 effective 2026-10-01). Japan (iOS 26.2, 2025-12) and Brazil (iOS 26.5, 2026-06) also allow alternative distribution.
- EU ATT: alternative system prompt available in iOS 27.2; the only option in DE, FR, IT, PL, RO.
- Sign in with Apple relay addresses: new ones on `private.icloud.com`; old `privaterelay.appleid.com` still works.

## StoreKit (iOS 27)
- Subscription Bundles and Suites (across apps/developers), multiseat purchasing; bundles/suites and multiseat roll out
  late 2026; monthly subscriptions with a 12-month commitment (2026-04). Use StoreKit 2 APIs; original StoreKit (`SKPaymentQueue`) deprecated in iOS 18.

## TestFlight and notarization basics
- TestFlight: internal testers (up to 100, team members) get builds right away; external testers (public link or
  invite) need Beta App Review for the first build of a version. Builds expire after 90 days. (counts unverified for 2026)
- Mac apps outside the Mac App Store: Developer ID sign + `xcrun notarytool submit --wait` + `xcrun stapler staple`.
  `altool` notarization was retired 2023-11-01.
