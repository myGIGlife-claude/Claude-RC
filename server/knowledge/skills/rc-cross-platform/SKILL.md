---
name: rc-cross-platform
description: Decision guide for cross-platform apps as of 2026-10: native (Kotlin+Compose, Swift+SwiftUI) vs Flutter, React Native/Expo, Kotlin/Compose Multiplatform, .NET MAUI, Capacitor, PWA, Tauri 2, Electron. Use when choosing or auditing a stack, or touching pubspec.yaml, app.json/eas.json, metro.config.js, capacitor.config.ts, tauri.conf.json, electron-builder, *.csproj with MAUI, or a KMP shared module.
---
# Cross-platform app development: decide, then build  (as of 2026-10)

> Facts are dated (see Sources). Versions move every few weeks: confirm on the primary source before pinning. "(unverified)" = a lead, not a fact.
> Per-framework profiles (plugins/bridges, build and signing, testing, a11y, security, viability) are in `references/frameworks.md`.
> Native details live in `rc-android` and `rc-ios`; web details in `rc-web-frontend`. This skill decides WHICH stack and flags what is outdated.

## Currency check

| Stack | Current stable (verified 2026-10-09) | Cadence | Notes |
|---|---|---|---|
| **Flutter / Dart** | Flutter **3.47** (2026-08-12) on **Dart 3.13** (2026-08-12) | Quarterly SDK | 3.47: `material_ui` and `cupertino_ui` **1.0 as standalone pub packages** (weekly releases), Impeller default on macOS/Windows/Linux, Widget Previews stable, **min iOS 15**, min macOS 12. Dart 3.13 added primary constructors |
| **React Native** | **0.87** (2026-08-11); 0.88 in RC | ~every 2 months; latest + previous 2 minors supported | New Architecture only since **0.82** (2025-10-08). Hermes V1 default since **0.84** (2026-02). 0.87: Strict TypeScript API default, experimental SwiftPM, Node >= 22.13, AGP 9 |
| **Expo** | **SDK 57** (2026-06-30, RN 0.86, React 19.2); **SDK 58 beta** (2026-09-15, RN 0.88 RC) | ~3 SDKs a year; Expo is moving toward more frequent non-breaking releases | SDK 56: Expo UI (SwiftUI / Jetpack Compose primitives) stable, Expo Router no longer depends on React Navigation, Expo Go SDK 56 not in stores. SDK 57: Android 7+, iOS 16.4+, Xcode 26.4+. SDK 58 beta: iOS 27 scene lifecycle, Android widgets, App Intents |
| **Kotlin Multiplatform / Compose Multiplatform** | Kotlin **2.4.21** (2026-10-08); CMP **1.12.1** (2026-09-22) | Kotlin: language release ~yearly + x.y.20; CMP follows Jetpack Compose by 1-3 months | **CMP for iOS stable since 1.8.0 (2025-05-06)**. CMP for web (Wasm) **Beta since 1.9.0 (2025-09)**. Swift export **Alpha** in Kotlin 2.4 (suspend -> async, Flow -> AsyncSequence). Targets: Android 5+, iOS 14+, macOS 13 arm64, Windows 10, Linux, WasmGC browsers |
| **.NET MAUI** | **MAUI 10** (2025-11-11), patch 10.0.110 (2026-09-22), EOS **2027-05-11**. MAUI 9 out of support since 2026-05-12 | Yearly with .NET (November) | .NET 11 (RC 1 as of 2026-10): CoreCLR is the default runtime on Android/iOS (Mono still available), NativeAOT opt-in, Material 3 on Android. Each major is supported until 6 months after the next ships, so plan a yearly upgrade |
| **Capacitor** | **8.5.3** (2026-10-07); 9.0 in alpha | ~yearly major | Cap 8: iOS 15+, Xcode 26+, Android minSdk 24 / target 36, **SwiftPM default for new iOS projects**, new System Bars plugin for edge-to-edge |
| **Tauri** | **2.12.2** (2026-10-09); 3.0 in alpha | Frequent minors | Desktop (Windows/macOS/Linux) + iOS + Android, system WebView, Rust core, capability-based permissions |
| **Electron** | **44.7.0** (2026-10-07; Chromium 152, Node 24.21); 45 in beta | New major every **8 weeks**; latest **3** majors supported | An Electron app older than ~6 months is out of support |
| **PWA** | iOS/iPadOS 26: any site added to Home Screen **opens as a web app by default**, manifest optional | Browser releases | Web Push for Home Screen apps since iOS 16.4; Declarative Web Push since iOS 18.4. Periodic Background Sync: Chromium-only, experimental |
| **Games** | Godot **4.7.2** (2026-08-18); Unity 6 line (unverified exact version) | | See the games note below |

### Older versions (legacy projects)
- **RN < 0.82** still allows the legacy bridge. Upgrading past 0.81 means every native module must support the New Architecture (TurboModules/Fabric) or be replaced. Go one RN minor at a time with the Upgrade Helper; RN 0.84 is already unsupported (2026-08).
- **Expo SDK <= 55** still uses React Navigation underneath Expo Router; SDK 56 needs the router codemod. Expo Go only runs the current SDK: older projects need a development build.
- **Flutter < 3.47**: Material/Cupertino are in the SDK (`package:flutter/material.dart`); nothing forces the switch yet. Do not migrate unless the task asks. iOS 13-14 support ends at 3.47.
- **CMP < 1.8**: iOS was Beta; K1 compiler support ended at 1.8. CMP 1.8+ needs Kotlin >= 2.1.0.
- **MAUI 8/9**: out of support (8 since 2025-05-14, 9 since 2026-05-12). **Xamarin**: out of support since **2024-05-01** (last SDKs: Android API 34, Xcode 15) and cannot meet current store target-API rules.
- **Capacitor <= 7** creates CocoaPods iOS projects; `android.adjustMarginsForEdgeToEdge` exists there and is removed in 8.
- **Electron**: anything below 42 gets no security fixes (2026-10).

## What changed / stop doing

| Old advice | Now | Since |
|---|---|---|
| "Cross-platform always saves money" | Saves on simple CRUD/content apps with one team. Costs MORE when the app lives on platform features (widgets, Live Activities, background, watch, CarPlay, deep OS integration): you pay for both the framework and native code plus bridge upkeep | Always true; worse since iOS 26 / Android 16-17 shipped big platform design changes |
| Cordova / PhoneGap | Capacitor (or a real native or RN/Flutter app). PhoneGap Build shut down 2020-10-01; Cordova plugins only via Capacitor compatibility | Years |
| Xamarin / Xamarin.Forms | .NET MAUI or .NET for Android/iOS | EOS 2024-05-01 |
| RN "bridge", async JSON messages, `NativeModules` | New Architecture: JSI, TurboModules, Fabric, Codegen; Expo Modules API for new native code | Legacy removed in 0.82 (2025-10) |
| JavaScriptCore as RN engine | Hermes (Hermes V1 default) | 0.84 (2026-02) |
| "Eject from Expo" / "bare vs managed workflow" | Continuous Native Generation (`npx expo prebuild`) + config plugins + development builds; any RN app can use Expo modules and EAS | Ejecting retired years ago (SDK 41 era, unverified) |
| Expo Go for real development | Development builds (`expo-dev-client`); Expo Go is for quick demos of the current SDK | SDK 56 Expo Go not even in stores |
| Expo Router on top of React Navigation | Expo Router has its own forked navigation core | SDK 56 |
| Flutter `material.dart` gets every new Material spec | `material_ui` / `cupertino_ui` packages ship design updates; Flutter paused Material 3 Expressive and Liquid Glass work in the SDK (2025-07) | 3.47 (2026-08) |
| Skia shader-compile jank in Flutter | Impeller everywhere (mobile earlier, desktop default in 3.47) | 3.47 |
| "KMP is only for business logic; CMP iOS is experimental" | CMP iOS is stable; shared UI is a valid choice. KMP-logic + native UI remains the safer default when platform look matters | CMP 1.8.0 (2025-05) |
| Kotlin -> Objective-C headers only | Swift export (Alpha) | Kotlin 2.4 (2026-06) |
| "PWAs can't do push on iOS" | They can, once added to the Home Screen and after a user gesture; Declarative Web Push on 18.4+ | iOS 16.4 (2023-03) |
| Mono runtime for MAUI mobile | CoreCLR default (Mono still available, NativeAOT opt-in) | .NET 11 (RC) |
| Electron: `nodeIntegration: true`, no sandbox | `contextIsolation` + sandbox + `contextBridge` preload (defaults for years); keep them on | Electron 12 (contextIsolation default), 20 (sandbox default) |

## Do this

### 1. The decision procedure (run it before any code)
Answer in order; the first rule that fires usually decides.

1. **One platform only (or 90% of users on one)?** -> Native for that platform. Cross-platform cost buys nothing.
2. **Does the core value depend on the OS?** Widgets, Live Activities / Dynamic Island, App Intents / Siri, background location or BLE, audio in background, HealthKit / Health Connect, CarPlay / Android Auto, Wear OS / watchOS, camera pipelines, AR, system keyboards/extensions, newest-OS APIs on day one -> **Native** (Kotlin + Compose, Swift + SwiftUI). If you need both platforms, consider **KMP for shared logic + native UI**.
3. **Must it feel exactly like each platform** (Liquid Glass on iOS 26+, Material 3 Expressive on Android)? -> Native UI (native, or KMP + native UI, or RN/Expo with native components / Expo UI). Flutter and CMP draw their own pixels: they look "close", and lag each OS redesign.
4. **Brand-driven custom UI, identical on both, heavy animation, small team?** -> **Flutter** (Dart) or **Compose Multiplatform** (Kotlin team, Android-first). Both share UI and logic ~90%+.
5. **Team is web/React/TypeScript and wants native UI components?** -> **React Native with Expo** (EAS for builds and OTA updates). Default for JS teams.
6. **Existing Android/Kotlin app, adding iOS?** -> **KMP** (share data/domain/networking) then decide per screen: SwiftUI or CMP.
7. **Existing website/web app, needs a store presence, light device APIs** (camera, push, share, biometrics, files)? -> **Capacitor**. Must add app-like value or Apple rejects it (Guideline 4.2).
8. **No store needed, content/forms/dashboards, users are on Android or desktop, or install is optional?** -> **PWA**. Weak on iOS for background work and discovery.
9. **Desktop app:** small, secure, low memory -> **Tauri 2**; needs Node ecosystem, identical Chromium everywhere, or mature tooling (auto-update, crash reporting) -> **Electron**; Kotlin team -> CMP desktop; .NET team on Windows -> WinUI/MAUI (or Avalonia/Uno, unverified current status).
10. **.NET shop with C# skills and Windows targets?** -> **MAUI** is viable but plan yearly upgrades and expect fewer third-party libraries; otherwise prefer the options above.
11. **Game?** -> Game engine, not an app framework (see the games note).

Record the decision in the project's PLAN.md / docs with the deciding rule, so later sessions don't relitigate it.

### 2. App-type recommendations (default picks, 2026-10)

| App type | Pick | Why |
|---|---|---|
| Banking, health, fitness with sensors, wearables | Native (or KMP logic + native UI) | Background, health APIs, security reviews, platform UX |
| Widgets/Live Activities are a headline feature | Native (extensions must be native anyway) | Widget/Live Activity code is SwiftUI/Glance regardless of framework |
| E-commerce, marketplace, social feed | RN/Expo or Flutter; native if top-tier perf matters | Shared UI, OTA updates, web reuse (Expo web) |
| Internal/enterprise tool, field forms | Flutter, RN/Expo, MAUI (C# shop), or PWA (MDM-managed Android) | Speed to ship, one codebase |
| Content/news/blog with app store presence | PWA first; Capacitor if a store listing is required | Lowest cost; but must exceed "repackaged website" |
| Utility with system integration (VPN, keyboard, launcher, file provider, SSH client) | Native | Extensions, background, OS APIs |
| Camera/AR/ML on device, real-time audio/video | Native (or RN/Flutter with native modules if UI is simple) | Pipelines and latency |
| Brand-heavy consumer app, same look both OSes | Flutter or CMP | Pixel control |
| Android app adding iOS later | KMP (shared) + SwiftUI or CMP | Reuse tested Kotlin |
| Desktop companion for a web product | Tauri 2 (or Electron if Node-heavy) | Reuse the web front end |
| Offline-first field app with sync | Native or Flutter/KMP (SQLite/Room) | Robust background sync; PWAs are fragile on iOS |
| 2D/3D game | Godot or Unity | Engines, not UI frameworks |

### 3. Red flags: "a website in a container" feel

| Red flag | Fix |
|---|---|
| Web-style navigation: no swipe-back on iOS, back button exits on Android, hamburger instead of tab bar | Native stack/tab navigators (Expo Router native Stack/NativeTabs, Flutter Navigator + platform pages), predictive back on Android |
| Text selection, rubber-band scroll, tap highlight, zoom on whole UI | Disable on chrome, keep on content; native scroll physics |
| Blank white screen or spinner on cold start | Native splash, cache shell, measure TTI; prefetch data |
| Custom-drawn controls that ignore Dynamic Type / font scale, dark mode, reduced motion | System text styles, honor OS settings, test at 200% text |
| No offline state; network errors show browser error pages | Offline UI, retry, cached data |
| Web fonts and hover-only affordances on touch | System fonts or embedded; 44pt/48dp targets |
| Keyboard covers inputs, no "next"/"done" handling | Inset handling, input types, autofill hints |
| Permission prompt on first launch, no rationale | Ask in context, explain first |
| Old OS look (pre-Liquid Glass bars on iOS 26, non-edge-to-edge on Android 15+) | Update to current platform components; edge-to-edge is enforced on targetSdk 35+ |
| Push opens the home screen, not the item | Deep links / universal links / app links wired end to end |
| Login via in-app WebView | System browser auth session (ASWebAuthenticationSession / Custom Tabs), passkeys |

### 4. Shared-code boundaries that work
- Share: domain models, validation, networking, persistence, sync, business rules, analytics events (privacy-first: only what the feature needs).
- Keep per platform: navigation shell (if UI is native), permissions UX, notifications, widgets/extensions, payments UI (StoreKit / Play Billing rules differ), accessibility tuning.
- One bridge per capability, typed: RN TurboModule/Expo Module with Codegen types; Flutter Pigeon (typed platform channels) or FFI; KMP `expect/actual` or interfaces injected from Swift; Capacitor plugin with a TS definition; Tauri command + capability.

### 5. When NOT to add a dependency
- No cross-platform framework for a single-platform app.
- No third-party plugin for one small native call: write a 30-line native module/plugin you own. Abandoned plugins are the #1 upgrade blocker in RN, Flutter and Capacitor.
- No state-management, navigation or UI kit on top of what the framework/Expo already ships unless a concrete need appears.
- No analytics, ad or attribution SDKs by default (privacy labels, Data safety, binary size, startup time).
- Before adding a plugin, check: last release < 6 months, supports current framework major (RN New Architecture, Flutter 3.47, Capacitor 8), open-issue health, license, native permissions it adds.

### 6. Migration and exit costs

| From -> To | Cost | Notes |
|---|---|---|
| Native -> anything | High (rewrite UI) | Keep native if it works; add KMP for new shared logic |
| Cordova -> Capacitor | Low-medium | Same web code; swap plugins; regenerate native projects |
| Xamarin.Forms -> MAUI | Medium-high | Official migration guide; renderers -> handlers; many libraries gone |
| RN legacy arch -> New Arch | Medium | Per native module; replace unmaintained ones |
| RN bare -> Expo (CNG) | Low-medium | Move native edits into config plugins |
| Android native -> KMP | Low per module | Move pure Kotlin to `commonMain` incrementally; Java/Android deps need KMP replacements |
| KMP logic -> CMP UI | Per screen | Can mix SwiftUI and CMP screens |
| Flutter / RN / CMP -> native | Full UI rewrite; logic rewrite unless KMP | Exit is the costliest for Flutter (Dart doesn't reuse) |
| Capacitor/PWA -> native | Full rewrite of the client; backend unaffected | Keep APIs clean so the client is replaceable |
| Electron -> Tauri | Medium | Front end mostly reusable; Node main process -> Rust commands; WebView differences per OS |

Design for exit: keep the backend API stable and documented, keep business rules out of UI code, and avoid framework-specific cloud lock-in (OTA, push, auth) without an export path.

### 7. Games (one paragraph)
Use an engine, not an app framework. **Godot 4.7** (MIT license, free, 2D strong, 3D improving, C#/GDScript; 4.7.2 on 2026-08-18) for indie and 2D; **Unity 6** (C#, largest asset/ads ecosystem, check current license terms) for commercial 3D/mobile; Flutter's Flame or RN are fine only for simple casual games. Wrap store/platform services (IAP, Game Center/Play Games) via the engine's plugins.

## Security
- **Bridges are attack surface:** every RN/Expo native module, Flutter channel, Capacitor plugin and Tauri command must validate input as if from untrusted code. In WebView-based apps (Capacitor, Tauri, Electron) an XSS becomes native code execution: strict CSP, no remote code in the app origin, no `eval`.
- **Electron:** keep `contextIsolation: true`, `sandbox: true`, `nodeIntegration: false`; expose a minimal `contextBridge` API; validate IPC senders; block `will-navigate`/`setWindowOpenHandler` to unknown origins; use Electron Fuses; stay on a supported major (8-week cadence).
- **Tauri:** grant the minimum capabilities per window; scope fs/shell/http plugins to exact paths/URLs; never allow arbitrary shell.
- **Secrets:** a JS bundle, Dart snapshot or .NET assembly is readable by anyone with the APK/IPA. No API keys with spend power in the client; use a backend or short-lived tokens. Store tokens in Keychain/Keystore (`expo-secure-store`, `flutter_secure_storage`, MAUI `SecureStorage`, Capacitor secure storage plugin you vet).
- **OTA updates** (EAS Update, Capgo/Appflow-style): sign updates (EAS code signing), roll out gradually, keep rollback. App Review guideline 2.5.2 forbids downloaded code that "introduces or changes features or functionality"; the Developer Program License Agreement permits interpreted code only if it doesn't change the app's primary purpose (clause number unverified).
- **Supply chain:** npm/pub/NuGet/Cargo/Gradle all in one app: lock files, Dependabot/Renovate, audit native permissions plugins add to the manifest/Info.plist and privacy manifests (`PrivacyInfo.xcprivacy`, required for listed SDKs).
- **Deep links:** verify App Links / Universal Links; treat link parameters as untrusted.
- Mobile baseline: OWASP MASVS/MASTG apply unchanged to cross-platform apps.

## Performance & quality
- Targets (same as native): cold start to first interactive frame < 1.5 s on a mid-range Android (measure, don't guess), 60/120 fps scrolling without jank, memory stable on long lists, APK/IPA size tracked per release.
- Expected characteristics: native and CMP/Flutter AOT start fast; RN with Hermes V1 + bytecode is close for most apps; Capacitor/PWA depend on WebView and bundle size (lazy-load routes, small JS); Electron idles at 100+ MB RAM (unverified figure) vs Tauri's system WebView (much smaller binary, but WebKitGTK on Linux and WebKit on macOS behave differently from Chromium).
- Measure with platform tools: Android Studio profiler / Macrobenchmark / Baseline Profiles (also for Flutter/RN Android), Instruments (Time Profiler, Hangs), Flutter DevTools, React Native DevTools + Perfetto, Lighthouse for PWAs.
- Accessibility: test with TalkBack and VoiceOver on real devices whatever the stack. Flutter needs `Semantics` on custom widgets; RN needs `accessibilityRole/Label`; CMP has VoiceOver support since 1.8; WebView apps use semantic HTML + ARIA.

## Testing & tooling
- Flutter: `flutter test` (unit/widget), `integration_test`, golden tests; `flutter analyze` + `dart format`; Widget Previews.
- RN/Expo: Jest (preset is now a separate package since 0.85), React Native Testing Library, Maestro or Detox for E2E; EAS Build / EAS Submit / EAS Update / EAS Workflows for CI; TypeScript strict.
- KMP: `kotlin.test` in `commonTest`, run on every target (`allTests`); XCTest on the Swift side; Android Compose UI tests.
- MAUI: xUnit/NUnit for logic; .NET 11 RC 1 adds device test templates (`androidtest`, `iostest`) runnable with `dotnet test`.
- Capacitor/PWA/Tauri/Electron: Vitest + Playwright for the web layer; native smoke tests on devices; Electron Playwright support; Tauri WebDriver (unverified maturity).
- Always: real-device test matrix (oldest supported OS + newest), store-build CI with signing secrets in CI only, crash reporting without PII.

## Common mistakes in AI-written code
- Writing RN bridge-era code: `NativeModules.X`, `RCT_EXPORT_MODULE` without TurboModule spec, `requireNativeComponent` Paper components, JSC flags. Use Codegen specs / Expo Modules API.
- Telling the user to "eject" Expo or to edit `ios/`/`android/` in a CNG project (changes get wiped by `prebuild`); use config plugins.
- Using Expo Go for features needing custom native code; use a development build.
- Importing deep RN paths (`react-native/Libraries/...`): broken by the Strict TypeScript API in 0.87.
- Flutter: assuming Liquid Glass or Material 3 Expressive widgets exist in `cupertino`/`material` (they don't, 2026-10); inventing `CupertinoLiquidGlass`-style APIs. Check `cupertino_ui`/`material_ui` changelogs.
- Flutter: `WillPopScope` (replaced by `PopScope`), `MaterialStateProperty` (now `WidgetStateProperty`) and other renamed APIs (verify on the breaking-changes page).
- KMP: putting Android-only libraries in `commonMain`; old `kotlin-multiplatform-mobile` plugin/KMM naming; Kotlin/Native old memory model `freeze()` (gone).
- MAUI: Xamarin.Forms renderers, `Device.BeginInvokeOnMainThread` (use `MainThread`/`Dispatcher`), `Application.MainPage` setter patterns deprecated in MAUI 9+ (unverified detail).
- Capacitor: Cordova `config.xml`/`cordova-plugin-*` as first choice; `capacitor.config.json` assumptions (TS config is default); missing SPM support in custom plugins (Cap 8 default).
- Electron: `remote` module (removed in 14), `nodeIntegration: true`, loading remote URLs with Node access.
- PWA: assuming background sync, periodic sync or silent push on iOS; asking for push permission without a user gesture; relying on `beforeinstallprompt` on iOS (it does not exist there).
- Claiming "one codebase, zero native code": every serious app still needs native code for push setup, widgets, signing and store config.

## Before you ship
- [ ] Decision recorded with the rule that chose the stack; native-only features listed with how they're implemented.
- [ ] Framework and every plugin on a supported version (RN latest 3 minors, Electron latest 3 majors, MAUI in support window, Expo current or previous SDK).
- [ ] Platform feel check against the red-flag table on a real iPhone (iOS 26+) and Android (15+ edge-to-edge).
- [ ] TalkBack + VoiceOver pass, 200% text, dark mode, reduced motion.
- [ ] No secrets in the bundle; tokens in Keychain/Keystore; bridges validate input; WebView apps have CSP; Electron/Tauri hardening applied.
- [ ] Privacy: minimal permissions, privacy manifest and Play Data safety match the SDKs actually shipped, no ad/tracking SDKs unless required.
- [ ] Release builds signed in CI, versionCode/build number monotonic, OTA updates signed with rollback, staged rollout.
- [ ] Crash reporting and startup/jank metrics on release builds; cold start measured on a low-end Android.
- [ ] Exit path: backend API independent of the client framework.

## Sources
- https://docs.flutter.dev/release/release-notes : Flutter 3.47 latest stable (accessed 2026-10-09)
- https://docs.flutter.dev/release/release-notes/release-notes-3.47.0 : Impeller desktop, macOS 12 minimum (2026-10-09)
- https://flutter.dev/blog/whats-new-in-flutter-3-47 : 2026-08-12, material_ui/cupertino_ui 1.0, iOS 15 minimum, quarterly cadence (2026-10-09)
- https://dart.dev/changelog : Dart 3.13 (2026-08-12), 3.12, 3.11 (2026-10-09)
- https://github.com/flutter/flutter/issues/170310 : Flutter paused Liquid Glass / M3 Expressive in core, work moves to packages (2026-10-09)
- https://reactnative.dev/versions : RN 0.87 latest (2026-10-09)
- https://reactnative.dev/blog : RN 0.80-0.87 release dates and highlights (New Arch only 0.82, Hermes V1 default 0.84) (2026-10-09)
- https://reactnative.dev/blog/2026/08/11/react-native-0.87 : 0.87 requirements and breaking changes (2026-10-09)
- https://github.com/reactwg/react-native-releases/blob/main/docs/support.md : latest + previous two minors supported (2026-10-09)
- https://expo.dev/changelog : SDK 56/57/58-beta dates (2026-10-09)
- https://expo.dev/changelog/sdk-56 : Expo UI stable, Router independent of React Navigation, Hermes v1 (2026-10-09)
- https://expo.dev/changelog/sdk-57 : RN 0.86, cadence direction (2026-10-09)
- https://expo.dev/changelog/sdk-58-beta : RN 0.88 RC, iOS 27, widgets, App Intents (2026-10-09)
- https://docs.expo.dev/versions/latest/ : SDK 57 platform minimums (2026-10-09)
- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html : CMP 1.12.1, targets, release alignment (2026-10-09)
- https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/ : CMP iOS stable 2025-05-06 (2026-10-09)
- https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/ : CMP web Beta (2026-10-09)
- https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/ : Kotlin 2.4, Swift export Alpha (2026-10-09)
- https://dotnet.microsoft.com/en-us/platform/support/policy/maui : MAUI 10 dates, support policy (2026-10-09)
- https://dotnet.microsoft.com/en-us/platform/support/policy/xamarin : Xamarin EOS 2024-05-01 (2026-10-09)
- https://learn.microsoft.com/en-us/dotnet/maui/whats-new/dotnet-11 : .NET 11 CoreCLR default (Mono available), NativeAOT opt-in, Material 3, device test templates (2026-10-09)
- https://www.electronjs.org/docs/latest/breaking-changes : contextIsolation default (12), sandbox default (20), remote removed (14) (2026-10-09)
- https://capacitorjs.com/docs/updating/8-0 : Capacitor 8 requirements, SPM default (2026-10-09)
- https://github.com/ionic-team/capacitor/releases : 8.5.3 latest, 9.0 alpha (2026-10-09)
- https://github.com/tauri-apps/tauri/releases : Tauri 2.12.2 (2026-10-09), 3.0.0-alpha (2026-10-09)
- https://v2.tauri.app/start/ : Tauri platforms, capabilities, system WebView (2026-10-09)
- https://releases.electronjs.org/ : Electron 44.7.0, Chromium 152, Node 24.21 (2026-10-09)
- https://www.electronjs.org/docs/latest/tutorial/electron-timelines : 8-week cadence, 3 majors supported (2026-10-09)
- https://webkit.org/blog/16993/news-from-wwdc25-web-technology-coming-this-fall-in-safari-26-beta/ : iOS 26 Home Screen web app default (2026-10-09)
- https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/ : Web Push iOS 16.4 (2026-10-09)
- https://webkit.org/blog/16535/meet-declarative-web-push/ : Declarative Web Push iOS 18.4 (2026-10-09)
- https://developer.mozilla.org/en-US/docs/Web/API/Web_Periodic_Background_Synchronization_API : Chromium-only, experimental (2026-10-09)
- https://developer.apple.com/app-store/review/guidelines/ : 4.2 minimum functionality, 4.7 mini apps (2026-10-09)
- https://godotengine.org/article/maintenance-release-godot-4-7-2/ : Godot 4.7.2 (2026-10-09)
