# App-store and platform privacy rules  (as of 2026-10-10)

> Contract terms, not law, but a mismatch gets the app rejected or removed. Re-read the live pages before each release:
> Apple's guidelines were last updated 2026-06-08; Google's policy pages show no revision date (© 2026).
> Native implementation details (manifest keys, Play Console screens) are in **rc-ios** and **rc-android**.

## Apple (App Store Review Guidelines, read 2026-10-10)
- **5.1.1(i) Privacy policy**: link in App Store Connect metadata AND inside the app, easy to find. It must say what data is
  collected, how, and all uses; confirm that third parties receiving data (analytics, ads, SDKs, affiliates) give equal protection;
  explain retention and deletion; and say how to revoke consent or request deletion.
- **5.1.1(ii)-(iv) Consent and minimization**: get consent before collecting user or usage data (even anonymous); paid features
  can't depend on granting access; easy withdrawal; clear purpose strings; ask only for data relevant to core features; prefer
  out-of-process pickers; offer alternatives when a permission is declined.
- **5.1.1(v) Accounts**: no forced login when the app has no significant account features; apps that support account creation
  must offer **account deletion in the app**; don't require personal info not core to the app; let users revoke social login.
- **5.1.2 Sharing**: permission before sharing personal data, including with **third-party AI**; App Tracking Transparency for
  tracking; can't require push, location or tracking to use features; no repurposing; no covert profiles or re-identification.
- **3.1.2 Auto-renewable subscriptions**: ongoing value, at least 7-day period, available on all the user's devices; describe what
  users get before they subscribe; meet Schedule 2 of the Apple Developer Program License Agreement (secondary on its contents).
- **Kids (1.3, 5.1.4)**: Kids Category apps: no third-party analytics or ads (narrow exceptions), no personal or device info to third
  parties, parental gate before links out or purchases. Collect birthdate/parent contact only to comply with COPPA and similar laws.
  A parental gate is not parental consent.
- **Age ratings and age laws**: answer age-rating questions honestly (2.3.6). Texas SB 2420 applies to new Apple Accounts in Texas
  from 2026-06-04 (Apple news 2026-06-03, after the Fifth Circuit lifted the injunction; Apple had paused on 2025-12-23 when the
  district court enjoined the law). Apple's tools, also meant for the Utah and Louisiana laws: the **Declared Age Range API**,
  PermissionKit **Significant Change API** (parental consent to significant changes; the developer decides what counts), a StoreKit
  age-rating property type and App Store Server Notifications (Apple news pages read 2026-10-10; the age-category list is from
  secondary reports, so open the "Age assurance" docs before implementing).
- **Privacy nutrition label** (App Store Connect "App Privacy"): declare every data type collected by you and every SDK, whether
  linked to identity, whether used for tracking. Must match the policy and the manifest.
- **Privacy manifest `PrivacyInfo.xcprivacy`** (Apple news "Privacy updates for App Store submissions", 2024, read 2026-10-10; the
  docs page did not render): keys `NSPrivacyTracking`, `NSPrivacyTrackingDomains`, `NSPrivacyCollectedDataTypes`,
  `NSPrivacyAccessedAPITypes` (required-reason APIs such as UserDefaults, file timestamp, system boot time, disk space, active
  keyboards; key names from Apple docs, secondary). Since **2024-05-01** a new or updated app that uses a listed API without an
  approved reason in its manifest can't be uploaded to App Store Connect (warning emails since 2024-03-13); a newly added SDK from
  Apple's list must ship its own manifest and, as a binary dependency, a signature (developer.apple.com/support/third-party-SDK-requirements/).
- **Sign in with Apple** (4.8) when offering third-party social login (check current exceptions).

## Google Play (Developer Policy Center, read 2026-10-10)
- **User Data policy (privacy policy)**: required for every app, even ones that access no personal data. Must include developer
  info and a privacy contact, data types accessed/collected/used/shared and with whom, secure handling, retention and deletion policy,
  and be clearly labeled as a privacy policy naming the entity in the store listing. Link in Play Console AND inside the app; active,
  public, **not geofenced, not a PDF**, not editable.
- **Prominent disclosure and consent**: when collection goes beyond what users would reasonably expect (e.g., background location),
  show an in-app disclosure during normal use, immediately before the runtime permission or consent request; consent needs an
  affirmative tap (back/home or auto-dismiss is not consent); no collection before consent. SDK collection counts: Google can demand
  proof within 2 weeks.
- **Data safety form**: covers you and every SDK; you are responsible for accuracy; must be consistent with the privacy policy.
- **Account deletion** (Data deletion questions due 2023-12-07, extended to 2024-05-31; enforcement, up to removal, after that):
  apps that allow account creation (including via an external flow) need an in-app
  deletion path AND a web link where users can request deletion without reinstalling; disclose any data you keep (security, fraud,
  legal) and for how long; ask processors to delete too; answer the Data deletion questions in the Data safety form.
- **Subscriptions policy**: disclose offer terms, price, billing frequency, renewal, whether a subscription is needed to use the app;
  for trials: length, price after, when it converts, how to cancel; easy online cancellation (link to Play subscription center or
  your own); banned: hiding renewal, showing only a monthly price for an annual plan, "Free Trial" SKU names on auto-renewing plans,
  accidental-tap purchase flows, subscriptions without recurring value.
- **Families policy**: apps targeting children must follow Families requirements (certified ad SDKs only, no AAID/precise location from
  kids, COPPA compliance) (secondary; open the Families policy page before relying).
- **Permissions**: sensitive permissions (SMS/Call Log, All files access, background location, accessibility, exact alarms, photo/video)
  need a declared core use; see rc-android.

## Reconcile the three layers before every release
| Fact in code | Privacy policy | Apple label + manifest | Play Data safety |
|---|---|---|---|
| Crash reporter SDK sends device info | "Crash reports" section | Crash Data, Diagnostics | "App info and performance" |
| Push notifications (FCM/APNs token) | Push token, purpose, how to stop | Device ID if linked (check Apple definitions) | "Device or other IDs" |
| Account email | Account section | Contact Info: Email, linked | "Personal info: Email" |
| Server logs with IP | Logs, retention | Only if linked/used (check guidance) | Collected if transmitted off device |
| No ad SDKs | "We don't use advertising trackers" | Tracking = No; no tracking domains | No "Advertising" purpose |

## Sources (accessed 2026-10-10)
- https://developer.apple.com/app-store/review/guidelines/ (last updated 2026-06-08)
- https://developer.apple.com/support/third-party-SDK-requirements/ (SDK list, manifest and signature rules) ; https://developer.apple.com/news/?id=3d8a9yyh (2024-03-13 / 2024-05-01 dates)
- https://developer.apple.com/news/?id=8jzbigf4 (2025-12-23: Texas paused after injunction; tool list) ; https://developer.apple.com/news/?id=sg176nne (2026-06-03: new Texas accounts covered)
- https://developer.apple.com/documentation/bundleresources/privacy-manifest-files (did not render: JS page)
- https://support.google.com/googleplay/android-developer/answer/10144311 (User Data)
- https://support.google.com/googleplay/android-developer/answer/13327111 (Account deletion)
- https://support.google.com/googleplay/android-developer/answer/9900533 (Subscriptions)
