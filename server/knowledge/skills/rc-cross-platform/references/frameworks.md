# Framework profiles (as of 2026-10)

Versions and dates are in SKILL.md "Currency check" (verified 2026-10-09). This file compares how each stack works. Items marked (unverified)
are leads to check before relying on them.

## Comparison table

| | Native (Kotlin+Compose / Swift+SwiftUI) | Flutter | React Native + Expo | KMP + native UI | Compose Multiplatform | .NET MAUI | Capacitor | PWA | Tauri 2 | Electron |
|---|---|---|---|---|---|---|---|---|---|---|
| Language | Kotlin / Swift | Dart | TypeScript | Kotlin + Swift/Kotlin UI | Kotlin | C# / XAML | TS/JS + web framework | TS/JS | Rust + web front end | TS/JS (Node + Chromium) |
| UI rendering | Platform toolkits | Own engine (Impeller) | Real native views (Fabric) | Platform toolkits | Own (Skia) canvas | Native controls via handlers | System WebView | Browser | System WebView | Bundled Chromium |
| Targets | One each | Android, iOS, web, Windows, macOS, Linux | Android, iOS, web (Expo), others via community (Windows/macOS by Microsoft, unverified status) | Android, iOS, desktop JVM, web, server | Android, iOS, desktop, web (Beta) | Android, iOS, Mac Catalyst, Windows | Android, iOS, web | Any browser | Windows, macOS, Linux, iOS, Android | Windows, macOS, Linux |
| Platform look | Exact, day-one | Approximation; lags OS redesigns | Native components, so mostly native; custom JS UI possible | Exact | Material-like everywhere; iOS look approximated | Native controls, styling uneven | Web look unless styled (Ionic etc.) | Web look | Web look | Web look |
| Native API access | Direct | Platform channels / Pigeon / FFI / plugins (pub.dev) | TurboModules, Expo Modules API, config plugins | `expect/actual`, cinterop, Swift export (Alpha) | Same as KMP + UIKit/SwiftUI interop views | .NET bindings to full platform SDKs | Capacitor plugins (Swift/Kotlin) | Web APIs only | Rust commands, Swift/Kotlin mobile plugins | Node APIs + native addons |
| Startup | Best | Good (AOT) | Good with Hermes V1 bytecode | Best (native UI) | Good; iOS binary larger (unverified size) | Medium; improving with CoreCLR/NativeAOT (unverified figures) | WebView boot + bundle | Network/cache dependent | Fast, small | Slow-ish, heavy RAM |
| Hot reload | Compose Live Edit / SwiftUI previews | Stateful hot reload (best in class) | Fast Refresh | Previews per platform | Compose Hot Reload (desktop/JVM, unverified for iOS) | XAML + .NET hot reload | Web dev server live reload | Web | Web dev server | Web dev server |
| Store build | Gradle / Xcode | `flutter build appbundle/ipa` | EAS Build or local Gradle/Xcode | Gradle + Xcode (XCFramework / SwiftPM) | Gradle + Xcode | `dotnet publish` | Web build + `npx cap sync` + Gradle/Xcode | None (optional TWA for Play) | `tauri build` / `tauri ios|android build` | electron-builder / Electron Forge, code signing + notarization |
| OTA code updates | No | No official (Shorebird third party) | EAS Update (signed) | No | No | No | Live updates (Appflow/Capgo, third party) | Always (deploy) | Updater plugin (desktop) | autoUpdater |
| Backing | Google / Apple | Google | Meta + Expo (company) + Microsoft/Callstack/Software Mansion | JetBrains (+ Google endorses KMP for logic) | JetBrains | Microsoft | Ionic (owned by OutSystems, unverified) | Browser vendors | Tauri Programme (CrabNebula sponsors, unverified) | OpenJS Foundation |

## Per-stack notes

### Flutter / Dart
- Strengths: one rendering engine = identical pixels; fastest dev loop; strong desktop; good tooling (DevTools, Widget Previews, analyzer).
- Weaknesses: platform-look lag (no Liquid Glass or M3 Expressive in the SDK as of 2026-10; community packages imitate or embed native views); larger binaries; Dart skills don't transfer; web output is canvas/Wasm (poor SEO, heavier).
- Plugins: platform channels; use **Pigeon** for typed channels; `dart:ffi` for C; federated plugins for multi-platform.
- Design packages: `material_ui` / `cupertino_ui` 1.x need Flutter >= 3.47; the SDK libraries remain for now. Don't migrate a legacy app unless asked.
- Viability: Google-backed, quarterly releases, breaking changes documented per release with `dart fix` migrations. Watch Google's investment signals; layoffs on the team in 2024 were reported (unverified details).
- Accessibility: `Semantics` widget; custom painters need explicit semantics; test with screen readers.

### React Native + Expo
- New Architecture only (0.82+): JSI (sync calls), TurboModules (lazy native modules), Fabric (concurrent renderer), Codegen from TS specs. Hermes V1 default (0.84+).
- Expo is the recommended way to start (react.dev / reactnative.dev point to frameworks; unverified wording 2026). CNG: `app.json`/`app.config.ts` + config plugins generate `android/` and `ios/`; commit them only if you hand-edit (then you own upgrades).
- Expo UI (SDK 56+) exposes SwiftUI and Jetpack Compose primitives, so Liquid Glass / M3 controls can be real native components.
- EAS: Build (cloud builds, signing credentials managed), Submit (stores), Update (OTA, code-signing, channels/rollouts), Workflows (CI). Paid tiers; local builds stay possible (`npx expo run:*`, `eas build --local`).
- Upgrades: one minor at a time (React Native Upgrade Helper; `npx expo install --fix`). Support: latest + previous two minors.
- Testing: Jest + RNTL, Maestro for E2E (common in Expo docs), Detox alternative.

### Kotlin Multiplatform (logic) and Compose Multiplatform (UI)
- KMP: share `commonMain` (Ktor, kotlinx.serialization, coroutines, SQLDelight or Room 3 KMP, DataStore KMP, Koin). iOS consumes an XCFramework or SwiftPM package; Swift export (Alpha in 2.4) improves the Swift API (async, AsyncSequence).
- Kotlin 2.4: Swift packages as dependencies (`swiftPMDependencies` in Gradle); Package.swift generation in 2.4.20 (unverified).
- Swift language support in the JetBrains KMP IDE plugin: reported discontinued (unverified); Kotlin/Native features are unaffected. Use Xcode for Swift code.
- CMP: iOS stable since 1.8.0; VoiceOver/Full Keyboard Access supported; embed `UIKitView`/SwiftUI where native controls are needed (maps, text fields with full iOS behavior).
- Best fit: Android teams; existing Android code; apps wanting native UI on iOS but shared logic.
- Viability: JetBrains + Google endorsement for sharing logic; Android Jetpack libraries increasingly ship KMP artifacts.

### .NET MAUI
- Handlers architecture over native controls; Blazor Hybrid option for web UI inside MAUI.
- Annual major with .NET; each supported until 6 months after the next (MAUI 10 EOS 2027-05-11). .NET 11 (RC 1 in 2026-09; GA expected 2026-11, unverified date): CoreCLR default on mobile (Mono still available), NativeAOT opt-in.
- Risks: smaller ecosystem than Flutter/RN, historical stability complaints, Microsoft focus shifts; many teams pick Avalonia or Uno Platform instead (unverified current versions). Choose it for C#-heavy teams and Windows desktop + mobile.

### Capacitor
- Web app (any framework) in WKWebView/Android WebView, native projects committed and owned by you, plugins in Swift/Kotlin with a TS API. Cordova plugins mostly work.
- Cap 8: SwiftPM by default for new iOS projects, System Bars plugin, iOS 15+, minSdk 24. 9.0 is alpha (2026-10): don't adopt in production.
- Fit: existing web product, forms/content apps, B2B. Must add app-like value to pass App Review 4.2.

### PWA
- Android (Chromium): install prompt, WebAPK, push, Background Sync, Periodic Background Sync (experimental, engagement-gated), badging, share target, file handling (unverified per-feature status on Android).
- iOS/iPadOS: Home Screen web apps (default on iOS 26, manifest optional), Web Push and Declarative Web Push for Home Screen apps only, permission needs a user gesture; no Background Sync, no `beforeinstallprompt`; storage can be evicted (unverified policy for Home Screen apps); separate storage from Safari.
- EU: Apple kept Home Screen web apps in the EU after reversing its iOS 17.4 removal plan (2024, unverified details).
- Fit: no store needed, frequent content changes, low budget, desktop + Android users. Not for background work on iOS.

### Tauri 2
- Rust core, system WebView (WebView2 on Windows, WKWebView on Apple, WebKitGTK on Linux, Android WebView). Tiny bundles (claims under 600 KB minimal), low RAM.
- Security: capabilities/permissions per window, scoped plugins, audits per major/minor release. JS calls Rust via `invoke`.
- Mobile support exists (since 2.0) but the plugin ecosystem is thinner than Capacitor/RN; test WebView differences per OS.

### Electron
- Bundles Chromium + Node: consistent rendering, mature (VS Code, Slack, etc.), heavy (size, RAM).
- 8-week majors, 3 supported: plan an upgrade every ~2 months or you fall out of security support within ~6 months.
- Hardening: contextIsolation, sandbox, no nodeIntegration, contextBridge, validate IPC, CSP, Fuses, code signing + macOS notarization.

## Long-term viability signals to check (any stack)
- Release health: regular releases in the last 6 months, a published support policy, upgrade guides per release.
- Backing: company or foundation with product dependence (Meta uses RN, Google uses Flutter in its own apps, JetBrains sells KMP tooling).
- Breaking-change history: RN (architecture migration done, now mostly non-breaking releases since 0.83), Flutter (steady deprecation cycle), MAUI (Xamarin rewrite), Cordova (stagnant).
- Ecosystem: top 20 plugins you need updated for the current major.
- Hiring/team fit: what your team already writes daily beats benchmark wins.
