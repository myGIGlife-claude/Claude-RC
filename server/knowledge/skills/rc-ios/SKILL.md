---
name: rc-ios
description: Native iOS/iPadOS app development with Swift 6, SwiftUI, UIKit, Xcode and App Store Connect, including UI/UX per the Human Interface Guidelines. Use when touching .swift, .xcodeproj, project.yml (XcodeGen), Package.swift, Info.plist, PrivacyInfo.xcprivacy, entitlements, xcconfig, fastlane, Xcode Cloud or a macOS CI runner, or when planning features like widgets, Live Activities, App Intents, Foundation Models, push (APNs), StoreKit, Sign in with Apple/passkeys, Keychain, TestFlight or App Review.
---
# Native iOS / iPadOS development (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Apple changed a lot in 2025-2026: year-based OS numbers, Liquid Glass, Swift 6.2 "approachable concurrency",
an on-device LLM API, new age-assurance rules. Training data predates most of it. Check here before writing code.
Longer tables: `references/platform-policy.md` (dates, review rules) and `references/swiftui-liquid-glass.md` (UI APIs).

## Currency check
- **OS numbering**: year-based since WWDC25. iOS 18 was followed by **iOS 26** (2025), then **iOS 27** (Sep 2026).
  Same number on iPadOS, macOS, tvOS, watchOS, visionOS (macOS 26 Tahoe, macOS 27). Current betas: iOS 27.2 (beta 3, 2026-10-05), Xcode 27.1 RC (2026-10-05).
- **Xcode 27** (released 2026-09-14): Swift 6.4, SDKs for all "27" OSes, needs macOS Tahoe 26.6+, **Apple silicon Macs only**.
  Debugs on-device iOS 17+. Xcode 27.1 adds iPhone Duo (foldable, ships 2026-10-23).
- **Swift 6.4** (2026-09-15). Before it: 6.3 (2026-03-24, `@c`, official Android SDK), 6.2 (2025-09-15, approachable
  concurrency, `InlineArray`, `Span`), 6.0 (2024-09, Swift 6 language mode).
- **App Store upload minimum**: Xcode 26 + iOS/iPadOS 26 SDK since **2026-04-28**. Apple announced Xcode 27 / iOS 27 SDK
  becomes the minimum in **April 2027** (exact day not yet given). Minimum deployment target accepted: iOS 13+.
- **Liquid Glass**: the iOS 26 design language. Building with the iOS 27 SDK **ignores `UIDesignRequiresCompatibility`**,
  so the opt-out is gone: every standard control renders as Liquid Glass.
- **UIKit scene life cycle is mandatory**: from iOS 27, apps built with the latest SDK that lack a scene-based life cycle
  fail to launch.
- **CocoaPods trunk goes read-only 2026-12-02** (planned; test run Nov 1-7). Existing pods still install; nothing new is published.
- **Age assurance**: Declared Age Range API (iOS 26+), PermissionKit Significant Change API, StoreKit age-rating property;
  required for Texas (SB 2420, from 2026-06-04) and other regions. Age rating questionnaire was redone (answers due
  2026-01-31; social-media questions required since 2026-09).
- **Foundation Models**: on-device LLM API (`LanguageModelSession`, iOS 26+). iOS 27 adds the `LanguageModel` protocol
  (plug any model), `PrivateCloudComputeLanguageModel`, image input. The model changes with OS updates: re-test prompts.
- **GitHub Actions**: `macos-26` / `macos-26-arm64` images carry Xcode 26.x; Xcode 27 is on the `xcode-27` image label
  (preview as of 2026-10). `macos-14` images are unsupported from 2026-11-02.

## What changed / stop doing
| Old advice | Do instead | Since |
|---|---|---|
| Build with the iOS 18 / Xcode 16 SDK | Xcode 26+ (uploads rejected otherwise); plan for Xcode 27 by April 2027 | 2026-04-28 |
| `UIDesignRequiresCompatibility = YES` to skip Liquid Glass | Adopt Liquid Glass; remove custom bar backgrounds that fight it | iOS 27 SDK |
| `UIApplicationDelegate`-only app (no scenes) | `UIApplicationSceneManifest` + `UIWindowSceneDelegate`, or SwiftUI `App` | iOS 27 SDK (launch failure) |
| `ObservableObject` + `@Published` + `@StateObject` for new code | `@Observable` class + `@State` (owner) / `@Bindable` / plain `let` | iOS 17 |
| `NavigationView` | `NavigationStack` / `NavigationSplitView` (doc-deprecated in the 27.2 SDK) | iOS 16 |
| `.foregroundColor`, `.cornerRadius`, `.navigationBarTitle` | `.foregroundStyle`, `.clipShape(.rect(cornerRadius:))`, `.navigationTitle` (deprecated 27.2 SDK) | iOS 15-17 |
| `onChange(of:perform:)` one-arg closure | `onChange(of:) { old, new in }` or zero-arg | iOS 17 |
| `UIScreen.main` for sizes | `GeometryReader`, `containerRelativeFrame`, the window scene's screen/trait collection | deprecated iOS 26 |
| `UIApplication.shared.keyWindow`, `windows.first` | the view's `window` / `windowScene`; SwiftUI environment | iOS 13/15 |
| `WKWebView` wrapped in `UIViewRepresentable` | SwiftUI `WebView` + `WebPage` (WebKit) | iOS 26 |
| `UIWebView`, `NSURLConnection`, `UIAlertView` | `WKWebView`/`WebView`, `URLSession` async, `UIAlertController`/`.alert` | long removed/deprecated |
| Original StoreKit (`SKPaymentQueue`, `SKProduct`), `verifyReceipt` | StoreKit 2 (`Product`, `Transaction`, `AppTransaction`) + App Store Server API | SKPaymentQueue deprecated iOS 18 |
| Completion handlers, `DispatchQueue.main.async` hops | `async`/`await`, `@MainActor`, actors, `Task` | Swift 5.5 / 6 |
| Swift 5 mode, `SWIFT_STRICT_CONCURRENCY=minimal` for new targets | Swift 6 mode; new projects: `SWIFT_DEFAULT_ACTOR_ISOLATION=MainActor`, `SWIFT_APPROACHABLE_CONCURRENCY=YES` | Xcode 26 |
| Combine for everything | async sequences, `@Observable`, `Observations` tracking; keep Combine only where APIs return publishers | Swift 5.5+ |
| XCTest for new unit tests | Swift Testing (`@Test`, `#expect`); XCTest stays for UI tests and perf | Xcode 16 |
| CocoaPods / Carthage as default | Swift Package Manager (Xcode-native) | trunk read-only 2026-12-02 |
| Storyboards / XIBs, Objective-C for new apps | SwiftUI first, UIKit in code where needed; Swift only | - |
| Core Data boilerplate stacks (`NSPersistentContainer` + FRCs) for new simple apps | SwiftData (`@Model`, `@Query`), or GRDB/SQLite when you need SQL control | iOS 17 |
| `LAContext` boolean as the only gate on a secret | Keychain item with `SecAccessControl` (`.biometryCurrentSet`) | - |
| On Demand Resources / `NSBundleResourceRequest` | Background Assets | deprecated Xcode 27 |
| Intel Macs / Rosetta CI hosts for Xcode | Apple silicon runners only (Xcode 27 does not run on Intel) | Xcode 27 |

## Do this
### Project setup
- New app: Xcode 27, SwiftUI `App` life cycle, Swift 6 language mode, deployment target iOS 17 or 18 (gets `@Observable`,
  SwiftData, Swift Testing helpers) unless analytics prove you need older. Gate iOS 26+ APIs with `if #available(iOS 26, *)`.
- Dependencies: SPM only. Split features into local packages (`Packages/Feature*/Package.swift`) for faster builds and
  clear boundaries; keep the app target thin.
- Settings in `.xcconfig` files (Debug/Release/Shared) rather than the pbxproj; or generate the project (XcodeGen
  `project.yml`, Tuist) so merges don't fight the pbxproj.
- Keep these per target: `SWIFT_VERSION = 6`, `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor` (app/UI targets; not for
  library packages doing background work), `SWIFT_APPROACHABLE_CONCURRENCY = YES`, `ENABLE_USER_SCRIPT_SANDBOXING = YES`.
- Add `PrivacyInfo.xcprivacy` from day one (see Security).

### Concurrency (Swift 6.2+)
- With MainActor default isolation, code is single-threaded unless you opt out. Mark real background work explicitly:
```swift
nonisolated struct ImageDecoder {            // not tied to the main actor
    @concurrent                               // runs off the caller's actor (Swift 6.2+)
    func decode(_ data: Data) async throws -> CGImage { /* heavy work */ }
}
```
- With approachable concurrency on (it enables `NonisolatedNonsendingByDefault`), `nonisolated async` functions run on
  the caller's actor. Use `@concurrent` when you actually want parallelism.
- Shared mutable state off the main actor = an `actor`. Values crossing actors must be `Sendable` (structs/enums of
  Sendable members are inferred). Don't sprinkle `@unchecked Sendable` or `nonisolated(unsafe)` to silence errors;
  fix ownership instead.
- Prefer structured concurrency (`async let`, `withThrowingTaskGroup`); SwiftUI `.task(id:)` cancels for you.
  Swift 6.4: `defer` can `await`; `withTaskCancellationShield` for cleanup that must finish.
- Typed throws (`throws(MyError)`, Swift 6.0) for closed error sets in libraries; plain `throws` is still fine in app code.
- Noncopyable types (`~Copyable`), `Span`, `InlineArray` are for performance-critical code, not everyday models.

### State and SwiftUI
```swift
@Observable final class Store {
    var items: [Item] = []
    func load() async throws { items = try await api.items() }
}
struct ListScreen: View {
    @State private var store = Store()        // owner; Xcode 27 makes @State a macro that creates the class once
    var body: some View {
        NavigationStack {
            List(store.items) { item in NavigationLink(value: item) { Text(item.name) } }
                .navigationDestination(for: Item.self) { ItemDetail(item: $0) }
                .navigationTitle("Items")
        }
        .task { try? await store.load() }
    }
}
struct EditItem: View { @Bindable var model: ItemModel   /* $model.name bindings */ }
```
- Inject shared services with `.environment(store)` + `@Environment(Store.self)`; avoid singletons in views.
- Architecture: start with `@Observable` models per feature + plain services (protocols only where you need a test
  double). TCA is a valid third-party choice for big teams; don't add it to small apps by default. Classic MVVM
  with `ObservableObject` is legacy style.
- Navigation: `NavigationStack(path:)` with a typed path for deep links; `NavigationSplitView` for iPad/Mac sidebar
  layouts; `TabView { Tab(...) }` API (iOS 18) with `Tab(role: .search)`; `.tabBarMinimizeBehavior(.onScrollDown)` (iOS 26).
- Persistence: SwiftData for simple object graphs with iCloud sync; GRDB or SQLite for heavy queries/migrations you
  control; Core Data is fine to keep in existing apps. UserDefaults only for small preferences; secrets go in Keychain.
- UIKit is still needed for: complex text editing, camera/scanner pipelines, very large collection views with custom
  layouts, some keyboard/gesture control. Wrap with `UIViewRepresentable`/`UIViewControllerRepresentable`; in UIKit apps
  host SwiftUI with `UIHostingController`. UIKit also supports `@Observable` tracking (automatic invalidation, iOS 26+).
- Previews: `#Preview { ... }` macro; feed previews with in-memory data (`ModelContainer(for:, configurations: .init(isStoredInMemoryOnly: true))`).

### Liquid Glass adoption (iOS 26+)
- Rebuild with the latest SDK first: standard `NavigationStack`, `TabView`, toolbars, sheets, menus adopt glass for free.
- Remove custom bar background colors / `toolbarBackground` hacks and opaque overlays under bars; let content scroll
  under them (`scrollEdgeEffectStyle`).
- Use glass sparingly for the controls layer only: `.glassEffect(.regular, in: .capsule)`, group with
  `GlassEffectContainer`; `.buttonStyle(.glass)` / `.glassProminent`. Never glass on content (lists, cards of data).
- iOS 27 additions: toolbar `visibilityPriority`, `ToolbarOverflowMenu`, `reorderable()`, `swipeActions` on any
  container, `ArrangementView` and hinge APIs for iPhone Duo. Details: `references/swiftui-liquid-glass.md`.

### Platform features (pick only what the product needs)
- **App Intents** are the single entry point for Shortcuts, Siri, Spotlight, widgets, controls and Apple Intelligence.
  Model your core actions as `AppIntent` + `AppEntity`; add `AppShortcutsProvider`. `UndoableIntent` and
  `IntentModes` (iOS 26), `LongRunningIntent` (iOS 27).
- **Widgets/Live Activities**: WidgetKit + ActivityKit; interactive widgets use `Button(intent:)`. Support the accented
  (glass) rendering mode. Live Activities can start and update via APNs push.
- **Foundation Models**: check `SystemLanguageModel.default.availability` first (device, region, Apple Intelligence
  setting); use `@Generable` structs for typed output; keep prompts short (`contextSize`, `tokenCount(for:)`).
- **Push**: request authorization in context, register, send the token to your server each launch (tokens change).
  Server uses token-based auth (.p8 key, JWT) over HTTP/2; never ship the key in the app.
- **StoreKit 2**: `Product.products(for:)`, `try await product.purchase()`, verify `VerificationResult`, listen to
  `Transaction.updates` at launch, call `transaction.finish()`. `SubscriptionStoreView` for paywalls. Test with a
  `.storekit` config file and in the sandbox.
- **Sign-in**: passkeys (`ASAuthorizationPlatformPublicKeyCredentialProvider`) + Sign in with Apple. If you offer
  third-party login, guideline 4.8 requires an equivalent privacy-focused option. Relay emails now also come from
  `private.icloud.com` (2026-08); don't allowlist only the old domain.
- **Background**: `BGTaskScheduler` (refresh/processing); `BGContinuedProcessingTask` (iOS 26) for user-started long
  jobs with progress. Background time is never guaranteed.
- **Account deletion**: any app that creates accounts must let users start deletion in the app (5.1.1(v)).

### UI/UX (HIG)
- Use system components and semantic colors (`.primary`, `Color(.systemBackground)`); support Dark Mode and
  Increased Contrast for free that way.
- Dynamic Type: system text styles (`.font(.body)`), no fixed heights on text containers; test at the largest
  accessibility sizes. `@ScaledMetric` for custom spacing/icon sizes.
- Hit targets at least 44x44 pt; respect safe areas (don't `ignoresSafeArea` on interactive content).
- SF Symbols for icons (`Image(systemName:)`), with accessibility labels on icon-only buttons.
- VoiceOver: meaningful `accessibilityLabel`, combine row elements (`.accessibilityElement(children: .combine)`),
  custom actions for swipe-only features. Voice Control needs visible labels matching accessibility labels.
- Respect Reduce Motion (`@Environment(\.accessibilityReduceMotion)`) and Reduce Transparency (glass adapts).
- Haptics: `.sensoryFeedback(.success, trigger:)` in SwiftUI; sparingly.
- Sheets: `.presentationDetents([.medium, .large])` for secondary tasks; full screen covers only for immersive flows.
- iPad: support all orientations, Split View/Stage Manager and the iPadOS 26 windowing (resizable windows, menu bar via
  `.commands`); never assume a fixed window size. Keyboard shortcuts with `.keyboardShortcut`.
- Localization: String Catalogs (`.xcstrings`), `LocalizedStringResource`, `.formatted()` for numbers/dates.
  Never concatenate localized fragments.
- App Store now shows Accessibility Nutrition Labels (voluntary now, Apple says they will become required).

## Security
- **Secrets**: Keychain only. Use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (or `AfterFirstUnlockThisDeviceOnly`
  for background use). Never UserDefaults, plist, or hard-coded API keys (anything in the binary is public).
- **Biometrics**: `LAContext.evaluatePolicy` alone returns a Bool that a hooked app can fake. Protect the secret
  itself with `SecAccessControlCreateWithFlags(..., .biometryCurrentSet, ...)`; use Secure Enclave keys
  (`SecureEnclave.P256` in CryptoKit) for signing.
- **Data protection**: default class is "complete until first user authentication"; use `.completeFileProtection`
  for sensitive files written with `Data.write(to:options:)`.
- **Network**: ATS on (HTTPS, TLS 1.2+). No `NSAllowsArbitraryLoads`; narrow `NSExceptionDomains` if ever needed.
  Pinning: declarative `NSPinnedDomains` in Info.plist, pin public keys (SPKI), ship a backup pin.
- **Deep links**: validate every universal link / URL scheme parameter as untrusted input; prefer universal links
  (apple-app-site-association) over custom schemes (any app can claim a scheme). Never perform state-changing actions
  from a link without user confirmation.
- **WebViews**: don't expose native bridges (`WKScriptMessageHandler`) to arbitrary origins; check `frameInfo.securityOrigin`.
- **Jailbreak detection** is a speed bump, not a control; never base security on it. Do server-side checks with App Attest
  (`DCAppAttestService`) for abuse prevention.
- **Privacy manifest** (`PrivacyInfo.xcprivacy`): declare tracking domains, collected data types, and required-reason
  APIs (categories: `FileTimestamp`, `SystemBootTime`, `DiskSpace`, `ActiveKeyboards`, `UserDefaults`, prefixed
  `NSPrivacyAccessedAPICategory`). Uploads without reasons are rejected (since 2024-05-01). Third-party SDKs on Apple's
  list must ship their own signed manifest.
- **Tracking**: ATT prompt (`ATTrackingManager.requestTrackingAuthorization`) before any cross-app tracking, with a
  purpose string; fingerprinting is banned even if the user allows tracking. EU: alternative ATT prompt in iOS 27.2.
- **AI data**: guideline 5.1.2(i) requires disclosure and explicit permission before sending personal data to a
  third-party AI service.
- Run Xcode 27's `audit-xcode-security-settings` skill and adopt "Enhanced Security" build settings where possible.

## Performance & quality
- Targets: first frame fast enough to avoid the launch watchdog (Apple's long-standing guidance is ~400 ms to first frame; unverified for 2026); no
  main-thread hangs > 250 ms (Xcode Organizer "Hangs"); zero crashes in Organizer for the last version; memory below
  jetsam limits on the oldest supported device.
- Tools: Instruments (Time Profiler, SwiftUI instrument for long view body updates, Hangs, Allocations/Leaks, App
  Launch, Energy), MetricKit (`MXMetricManager`) for field data, Xcode Organizer.
- SwiftUI pitfalls: huge `body`s with many dependencies (split into small views so fewer re-render), computing heavy
  values in `body`, `AnyView` everywhere, `GeometryReader` inside every list row, unstable `id`s in `ForEach`
  (causes full reloads), `@Observable` models that expose one giant array everyone reads.
- Use `List`/`LazyVStack` for long content; images via `AsyncImage` with caching (iOS 27 `asyncImageURLSession`) or a
  proper cache; downsample big images.
- Foundation Models: profile with the Foundation Models instrument; prewarm sessions; stream responses.

## Testing & tooling
- **Swift Testing** for unit/integration tests: `@Test`, `#expect`, `#require`, `@Suite`, parameterized
  `@Test(arguments:)`, traits (`.tags`, `.timeLimit`), exit tests and attachments (6.2), test repetition (6.4).
  XCTest and Swift Testing run side by side; 6.4 lets `XCTAssert` and `#expect` work in either.
- **XCUITest / XCUIAutomation** for UI flows; use accessibility identifiers, not label text.
- Snapshot tests: pointfreeco `swift-snapshot-testing` (third-party); pin simulator model and OS to avoid flakes.
- Lint/format: `swift format` (bundled with the toolchain since Swift 6) and/or SwiftLint.
- CI: Xcode Cloud (25 free compute hours/month with the developer program), GitHub Actions macOS runners
  (`macos-26`, `xcode-27` labels), or fastlane on any of them. No Mac locally: generate the project with XcodeGen,
  build with `xcodebuild` on a hosted macOS runner, upload with `xcrun altool`/App Store Connect API or fastlane
  `pilot`. Use App Store Connect API keys (.p8) stored as CI secrets, not Apple ID passwords.
- Signing: automatic signing for local work; CI uses `-allowProvisioningUpdates` with an API key, or fastlane `match`.
- Command lines: `xcodebuild -scheme App -destination 'platform=iOS Simulator,name=iPhone 17' test`,
  `swift test` for packages, `xcrun simctl` for simulators, `xcrun devicectl` for devices.

## Common mistakes in AI-written code
- Writing `ObservableObject`/`@Published`/`@StateObject` for new iOS 17+ code, or mixing `@StateObject` with an
  `@Observable` class (use `@State`).
- Using `NavigationView`, `.navigationBarTitle`, `.foregroundColor`, `.cornerRadius`, `UIScreen.main.bounds`.
- Inventing APIs: there is no `.liquidGlass()` modifier (it is `.glassEffect`), no `SwiftUI.WebView` in SwiftUI module
  (it is in WebKit, `import WebKit`), no `FoundationModels.chat()`; check names in docs.
- Adding `@MainActor` everywhere or `DispatchQueue.main.async` inside async code when the target already defaults to
  MainActor; or marking heavy work `nonisolated async` expecting it to leave the main actor (with approachable concurrency it does not; use `@concurrent`).
- Silencing concurrency errors with `@unchecked Sendable`, `nonisolated(unsafe)`, or `@preconcurrency import` instead of fixing data flow.
- `Task { }` in `onAppear` without cancellation; use `.task`.
- Assuming an iOS 18 SDK build can be uploaded, or that `UIDesignRequiresCompatibility` still works.
- App-delegate-only UIKit templates without a scene manifest (won't launch with the iOS 27 SDK).
- `SKPaymentQueue` purchase code, receipt parsing with OpenSSL, calling `verifyReceipt`.
- Storing tokens in `UserDefaults`; `NSAllowsArbitraryLoads = YES` "for development" that ships.
- Using `Podfile` for new dependencies; telling users to `pod install`.
- Forgetting the privacy manifest, purpose strings (`NS*UsageDescription`), or account deletion, which all cause rejections.
- Hard-coded font sizes, colors that ignore Dark Mode, icon buttons without accessibility labels, tap targets < 44 pt.
- Treating the on-device model as always available or as a fact source; it can be unavailable and it hallucinates.

## Before you ship
- [ ] Built with the required Xcode/SDK (Xcode 26+ now; Xcode 27 before April 2027); arm64 only.
- [ ] Swift 6 mode, no concurrency warnings, no `@unchecked Sendable` without a comment explaining why.
- [ ] Scene-based life cycle; Liquid Glass reviewed on iPhone, iPad (windowed) and iPhone Duo if supported.
- [ ] `PrivacyInfo.xcprivacy` complete; App Privacy answers in App Store Connect match it; purpose strings present.
- [ ] ATT only if tracking; AI data-sharing consent if sending personal data to a third-party AI.
- [ ] Account deletion in app; Sign in with Apple / equivalent if third-party login.
- [ ] Age rating questionnaire answered (incl. social media); age-assurance APIs where required.
- [ ] Export compliance: `ITSAppUsesNonExemptEncryption` set in Info.plist (NO if only HTTPS/standard OS crypto).
- [ ] Dynamic Type at max size, VoiceOver pass, Dark Mode, Reduce Motion, 44 pt targets; accessibility labels in ASC.
- [ ] Secrets in Keychain; ATS without global exceptions; deep links validated.
- [ ] Crash-free and hang-free in TestFlight (internal, then external testers need Beta App Review).
- [ ] No CocoaPods-only dependencies left without an SPM path (trunk read-only 2026-12-02).

## Sources
- https://developer.apple.com/news/upcoming-requirements/ : SDK minimum (Xcode 26 since 2026-04-28), iOS 13+ target, age rating deadline; accessed 2026-10-09
- https://developer.apple.com/news/ : Sep 9 2026 submissions open + April 2027 iOS 27 SDK minimum; Texas age assurance (2026-06-03); social media age questions (2026-07-09); EU ATT alt prompt; Sign in with Apple domain; iPhone Duo; accessed 2026-10-09
- https://developer.apple.com/documentation/xcode-release-notes/xcode-27-release-notes : Swift 6.4, SDKs, macOS 26.6, Apple silicon only, ODR deprecated, ld64 removed; accessed 2026-10-09
- https://developer.apple.com/news/releases/ : Xcode 27 release 2026-09-14, iOS 27.2 beta 3 and Xcode 27.1 RC 2026-10-05; accessed 2026-10-09
- https://developer.apple.com/xcode/ : Xcode 27 features (coding intelligence, Swift Testing + XCTest); accessed 2026-10-09
- https://www.swift.org/blog/swift-6.4-released/ : Swift 6.4 features and date; accessed 2026-10-09
- https://www.swift.org/blog/swift-6.3-released/ : Swift 6.3 date (2026-03-24), @c, Android SDK; accessed 2026-10-09
- https://www.swift.org/blog/swift-6.2-released/ : approachable concurrency, @concurrent, InlineArray/Span, Swift Testing exit tests; accessed 2026-10-09
- https://developer.apple.com/documentation/xcode/build-settings-reference : SWIFT_APPROACHABLE_CONCURRENCY, SWIFT_DEFAULT_ACTOR_ISOLATION, SWIFT_STRICT_CONCURRENCY; accessed 2026-10-09
- https://www.donnywals.com/should-you-opt-in-to-swift-6-2s-main-actor-isolation/ : (lead) Xcode 26 new-project defaults; accessed 2026-10-09
- https://developer.apple.com/documentation/bundleresources/information-property-list/uidesignrequirescompatibility : ignored when building for iOS 27+; accessed 2026-10-09
- https://developer.apple.com/documentation/updates/uikit : scene life cycle required from iOS 27; iOS 27 UIKit APIs; accessed 2026-10-09
- https://developer.apple.com/documentation/updates/swiftui : iOS 27 SwiftUI additions, @State macro; accessed 2026-10-09
- https://developer.apple.com/documentation/updates/foundationmodels : Foundation Models changes 2026; accessed 2026-10-09
- https://developer.apple.com/documentation/updates/appintents : App Intents 2026 changes; accessed 2026-10-09
- https://developer.apple.com/documentation/webkit/webview-swift.struct : SwiftUI WebView/WebPage iOS 26; accessed 2026-10-09
- https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api : required-reason categories, 2024-05-01 enforcement; accessed 2026-10-09
- https://developer.apple.com/app-store/review/guidelines/ : 5.1.1(v), 5.1.2(i), 4.7, 1.2, 3.1.1(a); accessed 2026-10-09
- https://developer.apple.com/help/app-store-connect/manage-app-accessibility/overview-of-accessibility-nutrition-labels : labels voluntary, later required; accessed 2026-10-09
- https://developer.apple.com/xcode-cloud/ : 25 free compute hours/month; accessed 2026-10-09
- https://blog.cocoapods.org/CocoaPods-Specs-Repo/ : trunk read-only 2026-12-02, test run Nov 1-7; accessed 2026-10-09
- https://github.com/actions/runner-images (images/macos, issue #14404) : macos-26 Xcode 26.x, xcode-27 label preview, macos-14 end 2026-11-02; accessed 2026-10-09
- Apple doc JSON for deprecations (NavigationView/foregroundColor/cornerRadius deprecated 27.2 SDK, UIScreen.main iOS 26, SKPaymentQueue iOS 18, onChange(of:perform:) iOS 17); accessed 2026-10-09
