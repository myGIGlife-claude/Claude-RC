---
name: rc-android
description: Native Android development (Kotlin, Jetpack Compose, Material 3, Gradle/AGP) as of October 2026, covering platform behavior changes (API 35-37), Play policy and target-API deadlines, architecture, security, performance, testing and UI/UX. Use when you write or review Android code: build.gradle(.kts), libs.versions.toml, AndroidManifest.xml, Compose screens, ViewModels, Room, WorkManager, permissions, R8 rules, Play Console releases, or when you upgrade targetSdk, AGP, Kotlin or the Compose BOM.
---
# Native Android development (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Platform details and the full version table are in `references/platform-and-policy.md`. Read it before raising `targetSdk` or answering a Play policy question.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| Newest stable Android | **Android 17, API 37**. Stable 2026-06-16 | Android 17 QPR betas (37.1, 37.2) are in progress. Android 16 = API 36, 15 = API 35 |
| Play target API | New apps and updates: **API 36** from 2026-08-31 (Wear OS/Automotive OS: 35, XR: 34. TV: 34 since 2025-08-31) | Extension to **2026-11-01** on request. API 37 is not yet required |
| 16 KB page size | Play: updates that don't support 16 KB **blocked from 2027-02-01** (wording on the page-sizes doc) | Only affects apps with native `.so` files (including those from SDKs). AGP >= 8.5.1 and NDK r28+ align by default |
| Developer verification | Certified devices (Android 7+) in BR/ID/SG/TH check developer registration from **2026-09-30**, global in 2027+ | Phase 1 checks installs from participating app stores (Play, Galaxy Store, Xiaomi GetApps and others). `adb install` is unchanged. Play registers ~99% of its apps automatically. Hobbyist "limited distribution" accounts allow up to 20 devices |
| Kotlin | **2.4.21** (2026-10-08). 2.4.0 language release on 2026-06-03 | K1 is gone. Context parameters and explicit backing fields are **stable in 2.4** |
| AGP | **9.4.1** (2026-09-18; 9.4.0 notes: needs Gradle >= 9.6, JDK 17, build-tools 36. Max API 37) | AGP 9.0 (2026-01) turned on built-in Kotlin and the new DSL. **AGP 10 is planned for late 2026** and removes the opt-outs |
| Gradle | **9.8.1** (2026-10-07) | |
| KSP | 2.3.x (KSP2. Versioned separately from Kotlin since 2.3) | |
| Compose BOM | **2026.09.00**: ui/foundation 1.12.1, material3 1.4.0, adaptive 1.3.0 | material3 1.5.0 is in beta: `ButtonGroup`, `FloatingToolbar` and the expressive top app bars become stable there. `LoadingIndicator` stays experimental |
| Navigation 3 | **1.2.0** (2026-09-23). First stable 1.0.0 shipped 2025-11-19 | Use it for new Compose apps. Nav2 is listed with the View libraries in maintenance |
| Room | **2.8.5** and **Room 3.0.3** (`androidx.room3`, KSP-only, coroutine-first, KMP) | Room 3 has a new package and Maven group, so it can live side by side with 2.x |
| Lifecycle / Activity | 2.11.0 / 1.13.0 | |
| Others (stable) | DataStore 1.2.1, WorkManager 2.12.0, Credentials 1.6.0, Hilt (Dagger) 2.60.1, Koin 4.2.2, Coil 3.6.3, Ktor 3.6.0, OkHttp 5.5.0, Retrofit 3.0.0, coroutines 1.11.0, kotlinx.serialization 1.11.0, Compose Multiplatform 1.12.1 (2026-09-22) | |
| Policy shift | **Compose-first** (announced with Android 17): new APIs, libraries and guidance target Compose only. Fragment, RecyclerView, ViewPager(2), ConstraintLayout, CoordinatorLayout, MDC-Views, Preference and DataBinding get **critical fixes only** | Views still work. Interop is supported in both directions |

## What changed / stop doing

| Old | New | Since |
|---|---|---|
| XML layouts, Fragments, RecyclerView for new screens | Compose + Navigation 3 + `LazyColumn` | Compose-first, 2026 (View libs in maintenance) |
| `android:windowOptOutEdgeToEdgeEnforcement`, `window.statusBarColor` / `navigationBarColor`, `setDecorFitsSystemWindows` | `enableEdgeToEdge()` + `WindowInsets` / `Scaffold` padding. Draw your own scrim if you need one | Enforced at target 35. The opt-out is ignored at target 36 |
| `onBackPressed()`, `KEYCODE_BACK` interception | `OnBackPressedCallback`, Compose `BackHandler` / `PredictiveBackHandler`, Nav3 handles back | At target 36 `onBackPressed` is **not called**. Predictive back is on by default |
| `screenOrientation="portrait"`, `resizeableActivity=false`, min/max aspect ratio | Adaptive layouts (window size classes) | Ignored on sw >= 600dp at target 36 (property opt-out). At target 37: **no opt-out** (games exempt) |
| `startActivityForResult` / `onActivityResult` / `requestPermissions` | `registerForActivityResult` + `ActivityResultContracts` (in Compose: `rememberLauncherForActivityResult`) | AndroidX Activity 1.2+ |
| AsyncTask, Loaders, raw `Thread`, RxJava for new code | Coroutines + Flow, `viewModelScope`, `lifecycleScope` | AsyncTask deprecated in API 30 |
| LiveData-only UI state | `StateFlow` + `collectAsStateWithLifecycle()` | lifecycle-runtime-compose |
| kapt | **KSP**, or `com.android.legacy-kapt` as a stopgap | AGP 9 built-in Kotlin. Room 3 is KSP-only |
| `org.jetbrains.kotlin.android` plugin, `kotlinOptions {}` | Built-in Kotlin (no plugin), `kotlin { compilerOptions {} }` | AGP 9.0 |
| `applicationVariants.all {}`, Transform API, `buildConfigField` in variant code | `androidComponents { onVariants {} }`, Artifacts API | AGP 9 new DSL. Removed in AGP 10 |
| `proguard-android.txt`, broad `-keep`, `-dontoptimize` | `proguard-android-optimize.txt` (or the AGP 9.3+ `optimization { enable = true }` block), narrow keep rules | R8 full mode has been the default since AGP 8.0 |
| `SharedPreferences` for new data | DataStore (Preferences or typed). Use Room for relational data | |
| `EncryptedSharedPreferences` / `MasterKey` (security-crypto) | Android Keystore directly (AES-GCM key, non-exportable) + DataStore | security-crypto **deprecated** in 1.1.0 (2025) |
| `requestLegacyExternalStorage`, `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` for media | Photo picker (`PickVisualMedia`), SAF, MediaStore | Scoped storage (API 29+). Play restricts broad media and "All files" access |
| Support libraries (`com.android.support`) | AndroidX. AGP 9 sets `useAndroidx=true` | |
| Dagger by default in small apps | Manual DI or Koin for small apps. Hilt (KSP) for large, multi-module apps | |
| Google Sign-In (`GoogleSignInClient`), Smart Lock, One Tap, FIDO2 API | Credential Manager (`androidx.credentials`): passkeys, passwords, Sign in with Google | |
| `BODY_SENSORS` | Granular `android.permission.health.*` (Health Connect) | Target 36 |
| `NEARBY_WIFI_DEVICES` workaround / unrestricted LAN sockets | `ACCESS_LOCAL_NETWORK` runtime permission or a system device picker | Mandatory at target 37 |
| `usesCleartextTraffic` | Network security config file | Deprecation planned (Android 17 notice) |
| SMS read for OTP | SMS Retriever / SMS User Consent | Android 17 withholds WebOTP-format SMS from non-recipient apps for 3 h (all apps). Standard OTP SMS too at target 37 |
| `ContentCaptureManager.setContentCaptureEnabled(false)` | `FLAG_SECURE` | Target 37 |
| `WindowWidthSizeClass` enums (old material3-window-size-class) | `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(...)` | material3-adaptive 1.1+ (unverified) |
| Nav2 string routes / `NavHost` for new apps | Navigation 3 (`NavDisplay`, your own back stack of `NavKey`s) | Nav3 1.0 (2025-11) |
| Context receivers (`-Xcontext-receivers`) | Context parameters (`context(x: X)`) | Stable in Kotlin 2.4 |

## Do this

### Build setup (AGP 9, version catalog)
```toml
# gradle/libs.versions.toml (excerpt)
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
composeBom = "2026.09.00"
[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.12" }
```
```kotlin
// app/build.gradle.kts: no kotlin-android plugin under AGP 9
plugins { alias(libs.plugins.android.application); alias(libs.plugins.compose.compiler) }
android {
    compileSdk = 37
    defaultConfig { minSdk = 26; targetSdk = 36 }   // always set targetSdk explicitly
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { compilerOptions { /* jvmTarget follows compileOptions */ } }
```
- `gradle.properties`: `org.gradle.configuration-cache=true`, `org.gradle.caching=true`. Keep `android.newDsl` / `android.builtInKotlin` at their default (true). AGP 10 deletes the opt-outs.
- Ship Android App Bundles (`bundleRelease`). Play requires AABs for new apps.
- Add a baseline profile module (`androidx.baselineprofile` plugin + Macrobenchmark) and keep `profileinstaller`.

### Architecture (UDF)
- Layers: UI (Compose + ViewModel) → optional domain (use cases only when logic is reused) → data (repositories over Room/DataStore/network). Data flows down as immutable state. Events flow up as lambdas.
- Use a ViewModel per screen. Expose `StateFlow<UiState>`. Keep restorable input in `SavedStateHandle`.
```kotlin
@Serializable data object Home : NavKey
data class UiState(val items: List<Item> = emptyList(), val loading: Boolean = true, val error: String? = null)

class HomeViewModel(repo: ItemRepo, private val saved: SavedStateHandle) : ViewModel() {
    val query = saved.getStateFlow("q", "")
    val state: StateFlow<UiState> = query.flatMapLatest(repo::observe)
        .map { UiState(items = it, loading = false) }
        .catch { emit(UiState(loading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
    fun onQuery(q: String) { saved["q"] = q }
}

// viewModel() alone can't build a VM with constructor params: use hiltViewModel(), koinViewModel() or a viewModelFactory
@Composable fun HomeRoute(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, onQuery = vm::onQuery)   // stateless, previewable
}
```
- Navigation 3: you own the back stack (`rememberNavBackStack(Home)`) and `NavDisplay(backStack, entryProvider = entryProvider { entry<Home> { HomeRoute() } })`. Use scene strategies for multi-pane layouts (list-detail and supporting-pane strategies come from the Material 3 adaptive library, not Nav3 itself). Keys are `@Serializable`, so state survives process death.
- DI: constructor injection everywhere. Hilt (KSP, `hilt-android` + `androidx.hilt:hilt-navigation-compose` or the lifecycle-viewmodel integration) for big teams. Koin 4 or a hand-written `AppContainer` for small apps.
- Data: use Room for structured data. New KMP or greenfield code can start on **Room 3** (`androidx.room3`, KSP, suspend/Flow DAOs, `SQLiteDriver`). Use DataStore for settings, WorkManager for deferrable guaranteed work, Paging 3 (`collectAsLazyPagingItems`) for long lists, and Coil 3 (`AsyncImage`) for images.
- Networking: Retrofit 3 or Ktor 3 over OkHttp 5 + kotlinx.serialization. Don't use Gson for new Kotlin code: it ignores defaults and nullability.
- Kotlin idioms: `suspend` for one-shot calls, `Flow` for streams, structured concurrency (no `GlobalScope`), and inject the dispatchers. Main-safety is the callee's job (`withContext(io)` inside the repo). Use `sealed interface` for results and events. `@JvmInline value class` for IDs.
- KMP: share data and domain modules (Room 3, DataStore, Ktor, kotlinx.serialization and lifecycle-viewmodel are all KMP). Compose Multiplatform (1.12) shares UI on iOS (stable) and desktop. Web is still maturing. Under AGP 9, a module with `kotlin("multiplatform")` can't also apply `com.android.application/library`: use the Android-KMP library plugin.

### Compose UI
- Theme: `MaterialTheme` with dynamic color (`dynamicLightColorScheme(ctx)` on API 31+, brand fallback), a dark theme, and M3 type and shape scales. Material 3 Expressive: `ButtonGroup`, `FloatingToolbar` and expressive top app bars are stable in material3 **1.5** (beta). `LoadingIndicator` is still experimental, and `LocalMotionScheme` is deprecated (use `MaterialTheme.motionScheme`). On 1.4 all of it needs `@OptIn`.
- Adaptive: `NavigationSuiteScaffold` picks a bar, rail or drawer from the window size. Use `ListDetailPaneScaffold` / `SupportingPaneScaffold` or Nav3 scenes. Breakpoints: compact <600, medium 600-840, expanded 840-1200, large 1200-1600, extra-large >=1600 dp (large/extra-large only with `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`). Decide from `currentWindowAdaptiveInfo()`, never from device type or orientation.
- Insets: call `enableEdgeToEdge()` in `onCreate`. Apply `Scaffold`'s `innerPadding` (or `contentWindowInsets`), use `Modifier.safeDrawingPadding()` / `imePadding()`, and add `contentPadding` on lazy lists so the last item clears the nav bar.
- State: hoist state and keep screens stateless. Use `rememberSaveable` for UI-only state and `derivedStateOf` for values computed from fast-changing state. Use `LaunchedEffect(key)` for keyed side effects, `rememberUpdatedState` for long-lived lambdas, and `DisposableEffect` for listeners.
- Performance: strong skipping is the default (Kotlin 2.0.20+), so unstable params compare by instance. Pass immutable data, use `kotlinx.collections.immutable` or a stability config file for foreign types, and give `key = { it.id }` and `contentType` to lazy items. Defer reads with lambda modifiers (`Modifier.offset { }`, `graphicsLayer { }`).
- Shared elements: `SharedTransitionLayout` + `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope)`.
- Previews: write `@Preview` on stateless screens with fake state. Use `@PreviewLightDark`, `@PreviewScreenSizes` and `@PreviewFontScale`.

### Permissions, background work and system UX
- Ask in context, right before the feature, with a rationale after the first denial. If the user denies again, degrade gracefully and link to Settings. Never ask at launch.
- `POST_NOTIFICATIONS` (API 33+) is runtime. Create channels with meaningful importance. Full-screen intent is only for calling and alarm apps.
- Media: the photo picker needs no permission. On 34+, handle "selected photos" partial access (`READ_MEDIA_VISUAL_USER_SELECTED`).
- Contacts: on API 37, prefer the system contact picker (`ACTION_PICK_CONTACTS`) over `READ_CONTACTS`.
- Foreground services: declare `foregroundServiceType` and the matching `FOREGROUND_SERVICE_*` permission (API 34+). `dataSync` and `mediaProcessing` time out after about 6 h a day (API 35). You can't start one from the background except in exempt cases. For user-initiated transfers, prefer **user-initiated data transfer jobs** (Android 16 applies job quotas even when jobs start from an FGS).
- Background activity launches are blocked. Android 17 extends this to `IntentSender`: use `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`, not the legacy ALLOWED mode.
- Exact alarms: `SCHEDULE_EXACT_ALARM` is denied by default for new installs (API 34+). Check `canScheduleExactAlarms()`. Only alarm and calendar apps may use `USE_EXACT_ALARM`. Use WorkManager or inexact alarms for anything else.
- Background audio (target 37): playback, focus and volume calls from the background need a while-in-use FGS. Otherwise they fail silently.
- Config changes (Android 17): activities are no longer recreated for keyboard, navigation, touchscreen or color-mode changes. Opt back in with `android:recreateOnConfigChanges`. IME visibility isn't restored after rotation.

### Accessibility (non-negotiable)
- Touch targets >= 48x48dp (M3 components enforce this via `minimumInteractiveComponentSize`). Text contrast >= 4.5:1 (3:1 for large text and icons).
- Every icon-only control needs a `contentDescription`. Decorative images use `null`. Use `Modifier.semantics { heading() }`, `mergeDescendants` for list rows, `Role`, `stateDescription`, and `clickable(onClickLabel = ...)`.
- Size text in `sp` and test at 200% font scale (nonlinear scaling from API 34). Don't put fixed heights on text containers.
- Test with TalkBack and Switch Access, and add the Accessibility Scanner or Compose `enableAccessibilityChecks()` in tests.

### UI/UX defaults
- Every data screen has four states: loading (skeleton or `LoadingIndicator`), content, empty (explains and offers an action), and error (plain message + retry). Use a snackbar for transient errors.
- Follow M3: top app bar + FAB for the primary action, bottom bar (3-5 destinations) on compact, rail on medium+. Respect gesture navigation: no custom gestures on screen edges.
- Use M3 easing and motion tokens. Respect "remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE` = 0).
- Onboarding: show value before sign-in, skip optional steps, and ask for permissions at the point of use.
- Splash: use `core-splashscreen` (`installSplashScreen()`). Don't build a custom splash Activity.

## Security
- **Exported components**: set `android:exported` explicitly. Leave everything unexported unless it must be reachable, and guard what is exported with a signature-level permission. Validate every extra.
- **Intent redirection**: never launch an `Intent` taken from another app's extras. Use `PendingIntent` with `FLAG_IMMUTABLE` and explicit components. Consider `android:intentMatchingFlags="enforceIntentFilter"` (Android 16).
- **URI grants**: from Android 18, `ACTION_SEND` / `ACTION_IMAGE_CAPTURE` stop auto-granting. Add `FLAG_GRANT_READ_URI_PERMISSION` (and WRITE for capture) yourself now. Share files via `FileProvider`, never `file://`.
- **Network**: use HTTPS only through a network security config (`cleartextTrafficPermitted="false"`). Certificate Transparency is on by default at target 37, and ECH is used when the server supports it. Pinning: pin only if you control rotation (pin the SPKI of an intermediate or your own CA and include a backup pin with an expiry). Otherwise, prefer CT and don't pin.
- **Secrets**: never ship API secrets in the APK, because R8 doesn't hide them. Generate keys in the Keystore (`setIsStrongBoxBacked` when available, `setUserAuthenticationParameters` for biometric-bound keys). Store tokens encrypted with a Keystore key and keep them out of backups.
- **Auth**: Credential Manager + passkeys. Use BiometricPrompt (`BIOMETRIC_STRONG` + `CryptoObject` for real gating). Prompt-only `DEVICE_CREDENTIAL` isn't proof of anything server-side.
- **Backups**: use `android:dataExtractionRules` (API 31+) plus `fullBackupContent` for older versions. Exclude tokens, keys and device-bound data.
- **WebView**: `javaScriptEnabled` only when needed, `allowFileAccess=false`, and no `addJavascriptInterface` on untrusted content. Use `WebViewAssetLoader` instead of `file://`, and restrict navigation to your origins.
- **Play Integrity**: use standard requests with `requestHash`, decrypt and verify on your server, and enforce in tiers (on Android 13+, `MEETS_STRONG_INTEGRITY` also requires security patches from the last year; on 12 and lower it doesn't, so check the SDK level too). Never trust a verdict checked on the device.
- **Native code**: no dynamic code loading. At target 37, `System.load` of writable files throws.
- **Sideloaded APKs**: the signing key must stay stable, and register package + key for developer verification (checks start 2026-09-30 in the first four countries).
- Logs: no PII or tokens in `Log.*`. Strip release logging with R8 `-assumenosideeffects`.

## Performance & quality
- Play bad-behavior thresholds (28-day): user-perceived crash rate **1.09%** (8% per phone model), user-perceived ANR **0.47%** (8% per model), excessive partial wake locks **5%** of sessions. New memory vitals (memory by RAM tier, bitmap memory 200 MB for background/user-perceived services) and the DEX-optimization criterion may affect store visibility from **2027-02**.
- Startup: aim for a cold start well under 1 s on mid-range hardware. Use baseline + startup profiles, lazy initialization (no work in `Application.onCreate`, and use App Startup only where needed), and R8 on. Measure with Macrobenchmark `StartupTimingMetric`, not stopwatch logs.
- Jank: `FrameTimingMetric`, the JankStats library, and Perfetto traces (`androidx.tracing`). In Compose, check recomposition counts in the Layout Inspector.
- Memory: avoid leaking Activity/Context into singletons. Use LeakCanary in debug, `ApplicationExitInfo` for kill reasons (Android 17: "MemoryLimiter:AnonSwap"), and `ProfilingManager` for field heap dumps.
- Battery: respect Doze and App Standby buckets. Batch work with WorkManager constraints and release wake locks. Avoid polling, and use FCM for server-to-device pushes.
- Turn on `StrictMode` (thread + VM policies, `penaltyLog`) in debug builds only.

## Testing & tooling
- Unit: JUnit 4 (Android instrumentation is still JUnit 4. JUnit 5 for JVM-only modules is fine via a third-party plugin), `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `Dispatchers.setMain`), Turbine for Flows, and fakes over mocks (MockK when you must).
- UI: Compose testing (`createComposeRule`, semantics matchers, `useUnmergedTree`) running on the JVM via Robolectric (4.17) or on device. Espresso is only for View screens. Use UI Automator 2.4 for cross-app and system UI.
- Screenshot: the Compose Preview Screenshot Testing plugin (`com.android.compose.screenshot`, maturity unverified) or Roborazzi/Paparazzi. Cover dark mode, font scale and size classes.
- Performance: Macrobenchmark 1.5 + Baseline Profile Gradle plugin on a physical device or a managed device in CI.
- Lint: Android Lint (`warningsAsErrors` for security checks), detekt or ktlint, and Compose lint rules. Add the `play-policy-insights` / R8 analyzer checks in Android Studio.
- CI: Gradle wrapper validation, configuration cache on, `./gradlew lint testDebugUnitTest assembleRelease bundleRelease`, sign with secrets from the CI store, and verify the APK's signing certificate.

## Common mistakes in AI-written code
- Applying `org.jetbrains.kotlin.android` or using `kotlinOptions {}` with AGP 9 (both break or warn). Using `kapt(...)` for Room or Hilt instead of `ksp(...)`.
- Calling `collectAsState()` (keeps collecting in the background) instead of `collectAsStateWithLifecycle()`. Collecting flows in `LaunchedEffect(Unit)` without a lifecycle.
- `GlobalScope.launch`, `runBlocking` on the main thread, catching `CancellationException` (swallowing cancellation) in `catch (e: Exception)`.
- Setting `window.statusBarColor`, `android:fitsSystemWindows`, or hardcoded status bar heights. Forgetting `innerPadding` from `Scaffold` (content under the bars at target 35+).
- Overriding `onBackPressed()`. Locking `screenOrientation` to portrait and assuming the app never resizes.
- Hallucinated or old APIs: `rememberNavController` + string routes presented as "the new navigation", `WindowWidthSizeClass.Expanded`, `EncryptedSharedPreferences` presented as current, `Accompanist` systemuicontroller/insets/pager (all deprecated, now in Compose), `LocalLifecycleOwner` from the wrong package (it moved to `androidx.lifecycle.compose`), `Material` (M2) imports mixed with M3.
- Passing `ViewModel` or `NavController` deep into composables. Unstable `List<T>` params with mutating lists. Missing `key` in `items()`.
- `PendingIntent` without `FLAG_IMMUTABLE`/`FLAG_MUTABLE` (crashes on API 31+). Implicit intents to internal components. Exported receivers without a permission. Context-registered receivers without `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` (required at target 34+).
- Asking for `READ_MEDIA_IMAGES`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES` or `SCHEDULE_EXACT_ALARM` without a core-feature reason (Play rejection).
- LAN or local-socket code (mDNS, SSDP, raw sockets to 192.168.x) with no `ACCESS_LOCAL_NETWORK` handling. It fails at target 37.
- Reflection on `MessageQueue` internals or writes to `static final` fields (break on Android 17).
- Gson + Kotlin data classes (nulls in non-null fields). `SimpleDateFormat` instead of `java.time` (desugaring covers old APIs).
- Hardcoded `dp` text sizes, icons without descriptions, 32dp touch targets.

## Before you ship
- [ ] `targetSdk` >= 36 (Play deadline passed 2026-08-31. Extension ends 2026-11-01). `compileSdk` 37. Tested with 37 behavior changes.
- [ ] Edge-to-edge correct on gesture and 3-button nav, IME, cutouts. Predictive back works (no `onBackPressed`).
- [ ] Usable resized, in landscape, on tablet/foldable/desktop windows, and at 200% font scale. TalkBack walkthrough done.
- [ ] Release build: R8 on with optimize defaults, shrink resources, a baseline profile, an AAB, 16 KB-aligned native libs (`zipalign -c -P 16 -v 4 app.apk`).
- [ ] Manifest: every component's `exported` reviewed. FGS types declared. No unused restricted permissions. A network security config. Backup rules exclude secrets.
- [ ] Data safety form matches what the app and SDKs actually collect. In-app + web account deletion if accounts exist. Privacy policy link.
- [ ] Permissions requested in context with a denial path. Notifications have channels.
- [ ] Vitals dashboards watched after a staged rollout (crash < 1.09%, ANR < 0.47%).
- [ ] Sideloaded distribution: package + signing key registered for developer verification.

## Sources
All accessed 2026-10-09.
- https://developer.android.com/about/versions: version list, Android 17 current, QPR betas
- https://developer.android.com/blog/posts/android-17-is-here: Android 17 stable on 2026-06-16, API 37, Compose-first, config-change recreation, NPU, contact picker
- https://developer.android.com/about/versions/17/behavior-changes-17: target-37 changes (local network, CT default, BAL, large-screen no opt-out, bg audio, static final, DCL)
- https://developer.android.com/about/versions/17/behavior-changes-all: all-app Android 17 changes (memory limits, OTP delay, URI grants in Android 18, cleartext deprecation)
- https://developer.android.com/about/versions/16/behavior-changes-16: target-36 changes (edge-to-edge opt-out gone, predictive back, resizability, health perms)
- https://developer.android.com/develop/ui/compose/first: Compose-first, View libraries in maintenance
- https://support.google.com/googleplay/android-developer/answer/11926878: target API deadlines 2025/2026, extension to 2026-11-01
- https://developer.android.com/guide/practices/page-sizes: 16 KB requirement, 2027-02-01 date, tooling
- https://developer.android.com/developer-verification: developer verification timeline and account types
- https://developer.android.com/developer-verification/guides: what to register, participating stores, ADB unchanged
- https://developer.android.com/google/play/integrity/verdicts: MEETS_STRONG_INTEGRITY requirements
- dl.google.com maven-metadata: AGP 9.4.1 (2026-09-18)
- https://developer.android.com/build/releases/gradle-plugin: AGP 9.4.0 compatibility
- https://developer.android.com/build/releases/agp-9-0-0-release-notes: AGP 9 defaults and removals
- https://developer.android.com/build/releases/gradle-plugin-roadmap: AGP 10 late 2026, removals
- https://developer.android.com/build/migrate-to-built-in-kotlin: built-in Kotlin, legacy-kapt, compilerOptions
- https://gradle.org/releases/: Gradle 9.8.1
- https://kotlinlang.org/docs/releases.html: Kotlin release dates
- https://kotlinlang.org/docs/whatsnew24.html: context parameters and explicit backing fields stable, K1 removed
- https://developer.android.com/develop/ui/compose/bom/bom-mapping: BOM 2026.09.00 mapping
- https://developer.android.com/jetpack/androidx/releases/compose-material3: Expressive stabilization in 1.5
- https://developer.android.com/jetpack/androidx/releases/navigation3: Nav3 1.2.0, 1.0 date
- https://developer.android.com/jetpack/androidx/releases/room and /room3: Room 2.8.5, Room 3.0.3
- https://developer.android.com/jetpack/androidx/versions: AndroidX stable versions
- https://developer.android.com/jetpack/androidx/releases/security: security-crypto deprecated
- repo1.maven.org and dl.google.com maven-metadata: third-party library versions (Hilt, Koin, Coil, Ktor, OkHttp, Retrofit, coroutines, serialization, KSP, Robolectric, CMP 1.12.1 on 2026-09-22)
- https://developer.android.com/develop/ui/compose/performance/stability/strongskipping: strong skipping default
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization: R8 setup, `optimization {}` block
- https://developer.android.com/develop/ui/compose/system/system-bars: edge-to-edge in Compose
- https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes: breakpoints, `currentWindowAdaptiveInfo`
- https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture: back APIs
- https://developer.android.com/develop/background-work/services/fgs/changes: FGS changes 14-16
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms: exact alarm defaults
- https://support.google.com/googleplay/android-developer/answer/9888170: restricted permissions policy
- https://support.google.com/googleplay/android-developer/answer/13327111: account deletion
- https://developer.android.com/identity/sign-in/credential-manager: Credential Manager replaces legacy sign-in
- https://developer.android.com/google/play/integrity/overview: Play Integrity standard requests, verdict tiers
- https://developer.android.com/topic/performance/vitals: vitals thresholds, 2027-02 memory visibility
