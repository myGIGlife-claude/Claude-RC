---
name: rc-android
description: "Native Android development (Kotlin, Jetpack Compose, Material 3, Gradle/AGP) as of November 2026, covering platform behavior changes (API 35-37), Play policy and target-API deadlines, architecture, security, performance, testing and UI/UX. Use when you write or review Android code: build.gradle(.kts), libs.versions.toml, AndroidManifest.xml, Compose screens, ViewModels, Room, WorkManager, permissions, R8 rules, Play Console releases, or when you upgrade targetSdk, AGP, Kotlin or the Compose BOM."
---
# Native Android development (as of 2026-11)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Platform details and the full version table are in `references/platform-and-policy.md`. Read it before raising `targetSdk` or answering a Play policy question.

## Currency check

| Thing | Current (verified 2026-11-19) | Notes |
|---|---|---|
| Newest stable Android | **Android 17, API 37**. Stable 2026-06-16 | Android 17 QPR1 (API 37.1) reached Platform Stability at Beta 6, with a final API diff. Its final-release date is unverified. No Android 18 beta found as of 2026-11-19 (press reports only, unverified). Android 16 = API 36, 15 = API 35 |
| Play target API | New apps and updates: **API 36** since 2026-08-31 (Wear OS/Automotive OS: 35, XR: 34. TV: 34 since 2025-08-31) | The 2026-11-01 extension window has passed. **API 37 is required from August 2027** per the Android developers blog (2026-11-18). The Play Help page does not list it yet |
| 16 KB page size | Play: updates that don't support 16 KB **blocked from 2027-02-01** (wording on the page-sizes doc) | Only affects apps with native `.so` files (including those from SDKs). AGP >= 8.5.1 and NDK r28+ align by default |
| Developer verification | Live since **2026-09-30** in BR/ID/SG/TH on certified devices (Android 8 / API 26+), for installs from participating stores. **Global rollout starts January 2027** with Play, Galaxy Store, Honor, Oppo, Xiaomi, Transsion and Vivo, then all install sources over several months | `adb install` is unchanged. Play registers ~99% of its apps automatically. "Limited distribution" accounts (hobbyists) now allow up to **50** devices (was 20). Developer ID Status API and Developer Console API exist for CI |
| Play adaptive quality | **April 2027**: new apps and updates need baseline Tier 3 adaptive quality (foldables) plus foldable screenshots (3 foldable + 2 phone). Games: screenshots April 2027, Tier 3 **August 2027** | Apps that miss it can't publish or update and may lose visibility. Un-updated apps stay downloadable. Foldable screenshot upload is live in Play Console |
| Kotlin | **2.4.21** (2026-10-08). 2.4.0 language release on 2026-06-03. **2.5.0-RC** published 2026-11-11 (stable planned December 2026) | K1 is gone. Context parameters and explicit backing fields are **stable in 2.4** |
| AGP | **9.5.1** (9.5.0 and 9.5.1 are on Google Maven, 9.6.0 alphas too. 9.5 notes: Gradle >= 9.6.0, JDK 17, build-tools 36.0.0, max API 37. Exact release dates unverified) | AGP 9.0 (2026-01) turned on built-in Kotlin and the new DSL. **AGP 10 is planned for late 2026** and removes the opt-outs |
| Gradle | **9.9.2** (2026-11-19) | |
| KSP | **2.3.14** (2026-11-13). KSP2, versioned separately from Kotlin since 2.3 | |
| Compose BOM | **2026.11.01** (2026-11-18): Compose 1.13.0, material3 **1.5.0 stable**, adaptive 1.3.0 | The Compose release post says the BOM needs Kotlin 2.2+ and compileSdk 37.1. The M3 notes say the core compileSdk 37 requirement was dropped and only `material3-a2ui` needs 37.1, so check your build. Stable in M3 1.5: `ButtonGroup`, `FloatingToolbar`, top app bars, `BottomAppBar`, `SearchBar`. `LoadingIndicator` and `MaterialShapes` stay experimental |
| Navigation 3 | **1.2.2** (2026-11-18). 1.2 added deep links and richer predictive back. First stable 1.0.0 shipped 2025-11-19 | Use it for new Compose apps. Nav2 is listed with the View libraries in maintenance |
| Room | **2.8.5** and **Room 3.0.3** (`androidx.room3`, KSP-only, coroutine-first, KMP) | Room 3 has a new package and Maven group, so it can live side by side with 2.x |
| Lifecycle / Activity | 2.11.1 / 1.13.0 | |
| Others (stable) | DataStore 1.3.0, WorkManager 2.12.0, Credentials 1.6.0, AppFunctions 1.0.0 (2026-11-18), Hilt (Dagger) 2.60.1, Koin 4.2.2, Coil 3.6.3, Ktor 3.6.1, OkHttp 5.5.1, Retrofit 3.0.0, coroutines 1.11.0, kotlinx.serialization 1.13.0, Compose Multiplatform 1.12.2 (1.13.0-rc01 is out) | |
| Compose 1.13 deprecations (2026-11-18) | Compose Material 2 library (no longer maintained), value-based `BasicTextField` and M2 `TextField` (use `TextFieldState`), `VisualTransformation` (use `OutputTransformation`/`InputTransformation`), `ConstraintLayout`/`MotionLayout` for Compose. `Modifier.onFocusedBoundsChanged` is now a no-op | FlexBox and Grid are stable and supersede `FlowRow`/`FlowColumn` for new code. `computedStateOf` is new (prefer it to `derivedStateOf` unless you need cached heavy results). M3 value-based `TextField` is not deprecated in 1.5.0 but is in 1.6.0-alpha01 |
| Policy shift | **Compose-first** (announced with Android 17, 2026-05-19): new APIs, libraries and guidance target Compose only. Fragment, RecyclerView, ViewPager(2), ConstraintLayout, CoordinatorLayout, MDC-Views, Preference and DataBinding get **critical fixes only** | Views still work. Interop is supported in both directions |

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
agp = "9.5.1"
kotlin = "2.4.21"
composeBom = "2026.11.01"
[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.14" }
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
- KMP: share data and domain modules (Room 3, DataStore, Ktor, kotlinx.serialization and lifecycle-viewmodel are all KMP). Compose Multiplatform (1.12.2) shares UI on iOS (stable) and desktop. Web is still maturing. Under AGP 9, a module with `kotlin("multiplatform")` can't also apply `com.android.application/library`: use the Android-KMP library plugin.

### Compose UI
- Theme: `MaterialTheme` with dynamic color (`dynamicLightColorScheme(ctx)` on API 31+, brand fallback), a dark theme, and M3 type and shape scales. Material 3 Expressive: `ButtonGroup`, `FloatingToolbar` and expressive top app bars are stable in material3 **1.5.0** (2026-11-18). `LoadingIndicator` and `MaterialShapes` are still experimental, and `LocalMotionScheme` is deprecated or removed (the 1.5.0 notes disagree: use `MaterialTheme.motionScheme`). On 1.4 all of it needs `@OptIn`. `Slider`/`RangeSlider` moved to `SliderState` (source-breaking). Prefer `FlexBox`/`Grid` (stable in 1.13) over `FlowRow` for new wrapping layouts, and state-based `TextField` overloads.
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
- **Sideloaded APKs**: the signing key must stay stable, and register package + key for developer verification (live since 2026-09-30 in four countries, global rollout from January 2027).
- Logs: no PII or tokens in `Log.*`. Strip release logging with R8 `-assumenosideeffects`.

## Performance & quality
- Play bad-behavior thresholds (28-day): user-perceived crash rate **1.09%** (8% per phone model), user-perceived ANR **0.47%** (8% per model), excessive partial wake locks **5%** of sessions. Memory vitals (memory by RAM tier, bitmap memory 200 MB for background/user-perceived services, 400 MB cached) and DEX code optimization (apps with more than 10 MB of DEX need at least 25% optimized/obfuscated/shrunk) may affect store visibility from **February 2027**.
- Startup: aim for a cold start well under 1 s on mid-range hardware. Use baseline + startup profiles, lazy initialization (no work in `Application.onCreate`, and use App Startup only where needed), and R8 on. Measure with Macrobenchmark `StartupTimingMetric`, not stopwatch logs.
- Jank: `FrameTimingMetric`, the JankStats library, and Perfetto traces (`androidx.tracing`). In Compose, check recomposition counts in the Layout Inspector.
- Memory: avoid leaking Activity/Context into singletons. Use LeakCanary in debug, `ApplicationExitInfo` for kill reasons (Android 17: "MemoryLimiter:AnonSwap"), and `ProfilingManager` for field heap dumps.
- Battery: respect Doze and App Standby buckets. Batch work with WorkManager constraints and release wake locks. Avoid polling, and use FCM for server-to-device pushes.
- Turn on `StrictMode` (thread + VM policies, `penaltyLog`) in debug builds only.

## Testing & tooling
- Unit: JUnit 4 (Android instrumentation is still JUnit 4. JUnit 5 for JVM-only modules is fine via a third-party plugin), `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `Dispatchers.setMain`), Turbine for Flows, and fakes over mocks (MockK when you must).
- UI: Compose testing (`createComposeRule`, semantics matchers, `useUnmergedTree`) running on the JVM via Robolectric (4.18.1) or on device. Espresso is only for View screens. Use UI Automator 2.4.1 for cross-app and system UI.
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
- [ ] `targetSdk` >= 36 (Play deadline passed 2026-08-31. Extension window ended 2026-11-01). Plan API 37 before August 2027. `compileSdk` 37 (37.1 if your Compose version asks). Tested with 37 behavior changes.
- [ ] Edge-to-edge correct on gesture and 3-button nav, IME, cutouts. Predictive back works (no `onBackPressed`).
- [ ] Usable resized, in landscape, on tablet/foldable/desktop windows, and at 200% font scale. TalkBack walkthrough done.
- [ ] Release build: R8 on with optimize defaults, shrink resources, a baseline profile, an AAB, 16 KB-aligned native libs (`zipalign -c -P 16 -v 4 app.apk`).
- [ ] Manifest: every component's `exported` reviewed. FGS types declared. No unused restricted permissions. A network security config. Backup rules exclude secrets.
- [ ] Data safety form matches what the app and SDKs actually collect. In-app + web account deletion if accounts exist. Privacy policy link.
- [ ] Permissions requested in context with a denial path. Notifications have channels.
- [ ] Vitals dashboards watched after a staged rollout (crash < 1.09%, ANR < 0.47%).
- [ ] Sideloaded distribution: package + signing key registered for developer verification.

## Sources
Accessed 2026-11-19, except lines marked "(2026-10-09)", which were not re-fetched in the November refresh.
- https://developer.android.com/about/versions/17/qpr1/release-notes: QPR1 Platform Stability at Beta 6, API diff 37.1 (page lists betas only up to 2026-08-17)
- https://developer.android.com/blog/posts/android-17-is-here (2026-10-09): Android 17 stable on 2026-06-16, API 37, Compose-first, config-change recreation, NPU, contact picker
- https://developer.android.com/about/versions/17/behavior-changes-17: target-37 changes re-read (local network, CT default, BAL, large-screen no opt-out, bg audio, static final, DCL, OTP, widget bitmap cap)
- https://developer.android.com/about/versions/17/behavior-changes-all (2026-10-09): all-app Android 17 changes (memory limits, OTP delay, URI grants in Android 18, cleartext deprecation)
- https://developer.android.com/about/versions/16/behavior-changes-16 (2026-10-09): target-36 changes
- https://developer.android.com/develop/ui/compose/first (2026-10-09): Compose-first, View libraries in maintenance
- https://support.google.com/googleplay/android-developer/answer/11926878: target API 36 deadline 2026-08-31, form factors, extension to 2026-11-01 (no API 37 date listed)
- https://developer.android.com/blog/posts/google-play-adaptive-quality-standards (published 2026-11-18, re-read 2026-11-19): April 2027 Tier 3 and 3 foldable + 2 phone screenshots, August 2027 new and updated apps must target API 37
- https://dl.google.com/android/maven2/ maven-metadata.xml (2026-11-19): AGP 9.5.1 (9.6.0-alpha04 newest), Compose BOM 2026.11.01, material3 1.5.0, Room 2.8.5, Room3 3.0.3, Navigation3 1.2.2 (1.3.0-alpha05), AppFunctions 1.0.0, WorkManager 2.12.0, DataStore 1.3.0
- https://developer.android.com/guide/practices/page-sizes (2026-11-19): 16 KB updates blocked from 2027-02-01; https://developer.android.com/about/versions/17/behavior-changes-17 (2026-11-19): target 37 behavior list
- https://developer.android.com/blog/posts/elevating-game-quality-foldables-large-screens-google-play: games dates (April and August 2027)
- https://developer.android.com/guide/practices/page-sizes: 16 KB requirement, 2027-02-01 date, tooling
- https://developer.android.com/developer-verification: verification timeline (Android 8+, four countries, 2027 global)
- https://developer.android.com/blog/posts/android-developer-verification-2027: global rollout January 2027, seven distributors, 50-device limited accounts, APIs
- https://developer.android.com/google/play/integrity/verdicts (2026-10-09): MEETS_STRONG_INTEGRITY requirements
- https://developer.android.com/build/releases/gradle-plugin: AGP 9.5 compatibility (Gradle 9.6.0, JDK 17, build-tools 36.0.0, API 37)
- dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml: AGP 9.5.1 present, 9.6.0 alphas, lastUpdated 2026-11-16
- https://developer.android.com/build/releases/agp-9-0-0-release-notes (2026-10-09): AGP 9 defaults and removals
- https://developer.android.com/build/releases/gradle-plugin-roadmap: AGP 10 late 2026, removed APIs and flags
- https://developer.android.com/build/migrate-to-built-in-kotlin (2026-10-09): built-in Kotlin, legacy-kapt, compilerOptions
- https://gradle.org/releases/: Gradle 9.9.2 (2026-11-19)
- https://kotlinlang.org/docs/releases.html: Kotlin 2.4.21, 2.5.0 planned December 2026
- repo1.maven.org kotlin-stdlib maven-metadata: 2.5.0-RC on 2026-11-11, latest stable 2.4.21
- https://kotlinlang.org/docs/whatsnew24.html (2026-10-09): context parameters and explicit backing fields stable, K1 removed
- https://developer.android.com/develop/ui/compose/bom/bom-mapping: BOM 2026.11.01 (ui/animation/foundation 1.13.0, material3 1.5.0, adaptive 1.3.0)
- https://developer.android.com/blog/posts/jetpack-compose-november-2026-release: Compose 1.13, deprecations, FlexBox/Grid stable, compileSdk 37.1 note
- https://developer.android.com/jetpack/androidx/releases/compose-material3: M3 1.5.0 stable contents and still-experimental APIs
- https://developer.android.com/blog/posts/build-it-your-way-a-next-era-of-android-development: Nav3 1.2 stable, AppFunctions beta (2026-10-28)
- https://developer.android.com/jetpack/androidx/releases/navigation3: Nav3 1.2.2
- https://developer.android.com/jetpack/androidx/versions: AndroidX stable versions (Activity, Lifecycle, DataStore, WorkManager, Room, Room 3, Credentials, Biometric, Benchmark, Window, UI Automator, AppFunctions, security-crypto 1.1.0)
- repo1.maven.org maven-metadata: KSP 2.3.14, Hilt 2.60.1, Koin 4.2.2, Coil 3.6.3, Ktor 3.6.1, OkHttp 5.5.1, Retrofit 3.0.0, coroutines 1.11.0, serialization 1.13.0, Compose Multiplatform 1.12.2, Robolectric 4.18.1, MockK 1.14.12, Turbine 1.2.1
- dl.google.com maven-metadata: Play Integrity 1.6.0
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
- https://developer.android.com/topic/performance/vitals: thresholds 1.09% / 0.47% / 5%, memory, bitmap and DEX criteria, visibility from February 2027
