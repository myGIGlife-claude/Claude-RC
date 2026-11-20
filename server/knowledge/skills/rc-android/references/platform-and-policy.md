# Android platform changes and Play policy (as of 2026-11)

Verified on developer.android.com and the Play Console Help on 2026-11-19 (Android 17 target-37 list re-read; Android 15/16 and all-app Android 17 sections last checked 2026-10-09).

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

### Target 37 (Android 17, stable 2026-06-16), required by Play from August 2027 (per the 2026-11-18 Android developers blog)
- Large-screen orientation, resizability and aspect-ratio restrictions are ignored with **no opt-out** (games exempt).
- `ACCESS_LOCAL_NETWORK` runtime permission (NEARBY_DEVICES group) is **enforced** for LAN discovery and connections (mDNS, SSDP, raw sockets, `NsdManager`). Alternatively, use system-mediated device pickers.
- Certificate Transparency is on by default. ECH is used when the stack and server support it. Network security config has a new `<domainEncryption>` element.
- Background activity launch rules extend to `IntentSender`. Migrate from `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` to `..._ALLOW_IF_VISIBLE`.
- Native libraries loaded with `System.load()` must be read-only, or the load throws `UnsatisfiedLinkError`.
- `static final` fields can't be changed via reflection (`IllegalAccessException`) or JNI (crash).
- New lock-free `MessageQueue`: reflection on its private fields breaks.
- Background audio hardening: playback, focus and volume calls from the background need a foreground service with while-in-use capability. Otherwise they fail silently.
- `setContentCaptureEnabled(false)` no longer works. Use `FLAG_SECURE`.
- RemoteViews (widget) bitmap memory is capped at 6 x width x height bytes (1.5 x a full-screen ARGB_8888 buffer). Going over throws `IllegalArgumentException` at target 37 (a logged warning below that). URI-backed images aren't in the cap but are decoded at full size in the host.
- Physical keyboards: password characters stay hidden (`show_passwords_physical`).
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
- AppFunctions: app capabilities exposed to on-device agents. The Jetpack library reached 1.0.0 stable on 2026-11-18 (the Android blog of 2026-10-28 called it beta).
- `ProfilingManager` anomaly triggers
- ML-DSA keys in secure hardware
- APK Signature Scheme v3.2 (hybrid PQC)

## Play Console policy quick reference
- **Target API**: new apps and updates must target 36 since 2026-08-31 (Wear OS and Automotive OS: 35. XR: 34. TV: 34, deadline was 2025-08-31). The extension to 2026-11-01 has passed. The Android developers blog (2026-11-18) says new and updated apps must target API 37 from August 2027. The Play Help page does not list that date yet, so confirm it in Play Console before promising it.
- **Adaptive quality (blog 2026-11-18)**: from April 2027 apps need baseline Tier 3 adaptive quality for foldables (fill the display, no letterboxing, keep state across rotate/fold/resize, multi-window) and must add foldable screenshots (3, plus 2 phone). Games: screenshots April 2027, Tier 3 August 2027. Failing apps can't publish or update. Foldable screenshot upload opened in November 2026.
- **16 KB pages**: per the page-sizes doc (re-read 2026-11-19), apps targeting 35+ must support 16 KB pages on 64-bit devices, and updates that don't are blocked from 2027-02-01. Earlier 2025 dates appeared in older announcements, so check the Console's own notice for your app. Android 17 adds `adb shell setprop bionic.linker.16kb.app_compat.enabled fatal` to abort on unaligned binaries and the `android:pageSizeCompat` manifest property.
- **Vitals** (Play, 28-day): crash 1.09% overall / 8% per phone model, ANR 0.47% / 8%, excessive partial wake locks 5%. Memory usage, bitmap memory (200 MB background and user-perceived services, 400 MB cached) and DEX code optimization (25% minimum for apps with > 10 MB DEX) may affect visibility from February 2027.
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
- **Developer verification** (affects distribution outside Play): live since 2026-09-30 in Brazil, Indonesia, Singapore and Thailand on certified devices running Android 8 (API 26) or higher, for apps installed from participating stores (Google Play, Galaxy Store, Xiaomi GetApps, OPPO, vivo, HONOR, Transsion). `adb install` stays the same. Per the 2026-11-19 blog, the global rollout begins in January 2027 with Play, Galaxy Store, Honor, Oppo, Xiaomi, Transsion and Vivo, then expands to all install sources over several months. Register via the Android Developer Console (outside-Play only, ID + fee) or the Play Console (~99% of Play apps auto-registered). Register the package name and signing key. "Limited distribution" accounts are for students and hobbyists (now up to 50 devices, no ID or fee). Android Developer ID Status API and Android Developer Console API automate registration in CI. A separate path exists for open-source registries, plus an "advanced flow" so power users can still install unregistered apps.

## Version table (stable unless noted, 2026-11-19)
| Component | Version |
|---|---|
| Android | 17 (API 37). QPR1 = API 37.1 (Platform Stability reached). Previous: 16 (36), 15 (35) |
| AGP | 9.5.1 (9.5 notes: Gradle >= 9.6.0, JDK 17, build-tools 36.0.0, NDK default 28.2.13676358, max API 37). 9.6.0 alphas exist |
| Gradle | 9.9.2 |
| Kotlin | 2.4.21 (2.5.0-RC out 2026-11-11, stable planned December 2026). Kotlin 2.4 Gradle plugin needs AGP >= 8.5.2 (unverified for November) |
| KSP | 2.3.14 |
| Compose BOM | 2026.11.01 (ui/foundation/animation/runtime 1.13.0, material3 1.5.0, adaptive 1.3.0). material3 1.6.0-alpha01, adaptive 1.4.0-alpha03 |
| Compose Multiplatform | 1.12.2 (1.13.0-rc01 in preview) |
| Navigation 3 | 1.2.2 (1.3.0-alpha05). Navigation (nav2) 2.10.2 |
| Activity / Lifecycle | 1.13.0 / 2.11.1 |
| Room / Room 3 | 2.8.5 / 3.0.3 (3.1.0-alpha04) |
| DataStore / WorkManager | 1.3.0 / 2.12.0 |
| Credentials / Biometric | 1.6.0 / 1.1.0 stable (1.4.0-rc01 preview) |
| Benchmark / profileinstaller | 1.5.0 / 1.4.1 |
| Window / UI Automator | 1.5.1 / 2.4.1 |
| core-splashscreen | 1.2.0 (unverified in November) |
| Hilt (Dagger) / androidx.hilt | 2.60.1 / 1.4.0 |
| Koin | 4.2.2 (4.3.0-Beta1 preview) |
| Coil 3 / Ktor / OkHttp / Retrofit | 3.6.3 / 3.6.1 / 5.5.1 / 3.0.0 |
| coroutines / serialization | 1.11.0 / 1.13.0 |
| Turbine / Robolectric / MockK | 1.2.1 / 4.18.1 / 1.14.12 |
| Play Integrity | 1.6.0 |
| AppFunctions | 1.0.0 (stable 2026-11-18) |
