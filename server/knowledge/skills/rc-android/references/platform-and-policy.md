# Android platform changes and Play policy (as of 2026-10)

Verified on developer.android.com and the Play Console Help on 2026-10-09 unless marked.

## Behavior changes by targetSdk (what breaks when you raise it)

### Target 35 (Android 15)
- Edge-to-edge is enforced. A temporary opt-out attribute existed, but it is ignored at target 36.
- `dataSync` / `mediaProcessing` foreground services get a runtime timeout (about 6 h in 24 h). Handle `Service.onTimeout`.
- A `BOOT_COMPLETED` receiver can't launch certain FGS types. `SYSTEM_ALERT_WINDOW` only exempts background FGS starts while an overlay is visible.

### Target 36 (Android 16), required on Play since 2026-08-31
- `windowOptOutEdgeToEdgeEnforcement` is deprecated and disabled.
- Predictive back is on by default: `onBackPressed()` isn't called and `KEYCODE_BACK` isn't dispatched. Temporary per-activity opt-out: `android:enableOnBackInvokedCallback="false"`.
- On displays with sw >= 600dp, the system ignores `screenOrientation`, `resizableActivity`, `min/maxAspectRatio` and `setRequestedOrientation`. Temporary opt-out: the `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` property. Games are exempt.
- `elegantTextHeight` is ignored. Check layouts for Arabic, Thai and Indic scripts.
- `scheduleAtFixedRate` runs at most one missed execution on resume.
- `BODY_SENSORS` becomes `android.permission.health.*` (for example `READ_HEART_RATE`), and background access becomes `READ_HEALTH_DATA_IN_BACKGROUND`. Health apps must declare a privacy-policy activity.
- Opt-in safer intents: `android:intentMatchingFlags="enforceIntentFilter"`.
- Local network permission is opt-in for testing (`adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>`).
- Jobs started from a foreground service count against job quotas. Use user-initiated data transfer jobs for user-started transfers.
- `MediaStore.getVersion()` is unique per app.

### Target 37 (Android 17, stable 2026-06-16), not yet required by Play
- Large-screen orientation, resizability and aspect-ratio restrictions are ignored with **no opt-out** (games exempt).
- `ACCESS_LOCAL_NETWORK` runtime permission (NEARBY_DEVICES group) is **enforced** for LAN discovery and connections (mDNS, SSDP, raw sockets, `NsdManager`). Alternatively, use system-mediated device pickers.
- Certificate Transparency is on by default. ECH is used when the stack and server support it. Network security config has a new `<domainEncryption>` element.
- Background activity launch rules extend to `IntentSender`. Migrate from `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` to `..._ALLOW_IF_VISIBLE`.
- Native libraries loaded with `System.load()` must be read-only, or the load throws `UnsatisfiedLinkError`.
- `static final` fields can't be changed via reflection (`IllegalAccessException`) or JNI (crash).
- New lock-free `MessageQueue`: reflection on its private fields breaks.
- Background audio hardening: playback, focus and volume calls from the background need a foreground service with while-in-use capability. Otherwise they fail silently.
- `setContentCaptureEnabled(false)` no longer works. Use `FLAG_SECURE`.
- RemoteViews (widget) bitmap memory is capped at 1.5 x screen w x h x 4 bytes. Going over crashes the app.
- Standard OTP SMS messages are withheld from other apps for 3 h. Use SMS Retriever or User Consent.
- Contacts Provider: account columns are removed from the data view, and SQL checks are strict without `READ_CONTACTS`.
- Bluetooth RFCOMM `read()` returns -1 on close.
- Direct NPU access requires declaring `FEATURE_NEURAL_PROCESSING_UNIT`.
- From the release post: activities aren't recreated for keyboard, navigation, touchscreen or color-mode config changes. Opt back in with `android:recreateOnConfigChanges`.

### All apps on Android 17 devices (regardless of target)
- Per-app memory limits based on device RAM. The exit reason contains "MemoryLimiter:AnonSwap".
- WebOTP-format SMS is delayed 3 h for apps that aren't the recipient.
- IME visibility isn't restored after rotation.
- Keystore cap: 50,000 keys per non-system app targeting 37 (200,000 for others). Over the cap throws `KeyStoreException`.
- Cross-profile loopback traffic is blocked.
- Touchpads deliver relative events during pointer capture.
- `usesCleartextTraffic` will be deprecated. Use a network security config.
- Coming in Android 18: `ACTION_SEND`, `ACTION_SEND_MULTIPLE` and `ACTION_IMAGE_CAPTURE` stop granting URI permissions implicitly.

### New Android 17 APIs worth using
- Contact picker (`ACTION_PICK_CONTACTS`)
- Photo picker UI customization
- System-rendered location button (session precise location)
- Eye dropper (`ACTION_OPEN_EYE_DROPPER`)
- Handoff ("Continue On")
- AppFunctions: app capabilities exposed to on-device agents. The Jetpack library is alpha.
- `ProfilingManager` anomaly triggers
- ML-DSA keys in secure hardware
- APK Signature Scheme v3.2 (hybrid PQC)

## Play Console policy quick reference
- **Target API**: new apps and updates must target 36 since 2026-08-31 (Wear OS and Automotive OS: 35. XR: 34. TV: 34, deadline was 2025-08-31). An extension to 2026-11-01 is available on the Policy status page. The next bump (likely API 37 in Aug 2027) is unverified.
- **16 KB pages**: per the page-sizes doc, updates that don't support 16 KB are blocked from 2027-02-01. Earlier 2025 dates appeared in older announcements, so check the Console's own notice for your app.
- **Restricted permissions** (declaration form + core-feature justification):
  - SMS and Call Log: only for the default handler
  - `MANAGE_EXTERNAL_STORAGE`: only for core file management
  - `REQUEST_INSTALL_PACKAGES`: only for core install use cases. Self-updating is not an allowed use
  - `QUERY_ALL_PACKAGES`: use targeted `<queries>` instead
  - Exact alarms: `USE_EXACT_ALARM` only for alarm and calendar apps
  - `READ_MEDIA_IMAGES/VIDEO`: use the photo picker unless the app's core use needs broad access
  - Accessibility API: needs prominent disclosure unless the app is an accessibility tool
  - Full-screen intent: auto-granted only to calling and alarm apps
  - Health: granular permissions
  - Foreground service types: declared in the Console with a video or description
- **Data safety form** must match the actual collection by the app and all SDKs. **Account deletion**: apps that let users create accounts need an in-app deletion path and a web link (enforced since 2024).
- **Developer verification** (affects distribution outside Play): from 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, certified devices (Android 7+) verify the developer of apps installed from participating stores (Google Play, Galaxy Store, Xiaomi GetApps, OPPO, vivo, HONOR, Transsion). `adb install` stays the same. Expands globally in 2027+. Register via the Android Developer Console (outside-Play only, ID + fee) or the Play Console (~99% of Play apps auto-registered). Register the package name and signing key. "Limited distribution" accounts are for students and hobbyists (up to 20 devices, no ID or fee). A separate path exists for open-source apps, plus an "advanced flow" so power users can still install unverified apps.

## Version table (stable unless noted, 2026-10-09)
| Component | Version |
|---|---|
| Android | 17 (API 37). Previous: 16 (36), 15 (35) |
| AGP | 9.4.1 (9.4.0 notes: Gradle >= 9.6.0, JDK 17, build-tools 36.0.0, NDK default 28.2.13676358) |
| Gradle | 9.8.1 |
| Kotlin | 2.4.21 (Kotlin 2.4 Gradle plugin needs AGP >= 8.5.2) |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0, adaptive 1.3.0). material3 1.5.0-beta01 |
| Compose Multiplatform | 1.12.1 (1.13.0-alpha02 in preview) |
| Navigation 3 | 1.2.0. Navigation (nav2) 2.10.2 |
| Activity / Lifecycle | 1.13.0 / 2.11.0 |
| Room / Room 3 | 2.8.5 / 3.0.3 |
| DataStore / WorkManager | 1.2.1 / 2.12.0 |
| Credentials / Biometric | 1.6.0 / 1.1.0 stable (1.4.0-alpha07 preview) |
| Benchmark / profileinstaller | 1.5.0 / 1.4.1 |
| Window / UI Automator | 1.5.1 / 2.4.0 |
| core-splashscreen | 1.2.0 |
| Hilt (Dagger) / androidx.hilt | 2.60.1 / 1.4.0 |
| Koin | 4.2.2 |
| Coil 3 / Ktor / OkHttp / Retrofit | 3.6.3 / 3.6.0 / 5.5.0 / 3.0.0 |
| coroutines / serialization | 1.11.0 / 1.11.0 (1.12.0-RC preview) |
| Turbine / Robolectric / MockK | 1.2.1 / 4.17 / 1.14.11 |
| Play Integrity | 1.6.0 |
