---
name: rc-dart-flutter
description: How to write current Dart 3.13 / Flutter 3.47 code: language features (records, patterns, sealed, primary constructors, dot shorthands), widget build performance, Impeller, Material/Cupertino packages, go_router, Riverpod/Bloc, data and storage, platform channels/Pigeon/FFI, testing, release. Use when editing *.dart, pubspec.yaml, analysis_options.yaml, build.yaml, l10n.yaml, *.arb, or Flutter android/ios/web runners.
---
# Dart and Flutter  (as of 2026-10)

> Facts are dated (see Sources). Choosing Flutter vs other stacks is in `rc-cross-platform`; this skill is how to write good Dart/Flutter
> code once chosen. Anything marked (unverified) is a lead, not a fact. Package versions: `references/packages.md`.

## Currency check
- **Flutter 3.47.7** (2026-10-08) on **Dart 3.13.5**. Flutter 3.47.0 and Dart 3.13.0 shipped 2026-08-12. Cadence: Flutter stable
  quarterly (3.41 Feb, 3.44 2026-05-18, 3.47 Aug), Dart minor alongside (3.11 2026-02-11, 3.12 2026-05-18). Only the latest stable gets
  hotfixes; there is no LTS. Beta 3.49.0-0.2.pre (2026-10-01); next stable expected 2026-11 (version unverified).
- **Dart language, newest first**
  - 3.13: **primary constructors** `class Point(var int x, var int y);` (language version >= 3.13). `final`/`var` no longer allowed
    on non-declaring parameters (breaks old `void f(final int x)` style; freezed 4.0 adapted). Type-promotion soundness fixes.
  - 3.12: private named parameters for initializing formals (`C({required this._x})` exposes `x:`).
  - 3.10 (2025-11-12): **dot shorthands** `.center`, `.all(8)`, `.new()` where the context type is known. `@required` from `package:meta` gone.
  - 3.8: null-aware collection elements `[?maybe, if (x case final y?) y]`. 3.7: wildcard `_` variables; new "tall" `dart format` style
    (formatting follows the package's language version). 3.6: digit separators `1_000_000`, pub workspaces.
  - 3.3 extension types; 3.0 records, patterns, `sealed`/`final`/`base`/`interface` class modifiers, 100% sound null safety.
  - **Macros: cancelled** (Dart blog, 2025-01-29). Augmentations were to ship separately; not in 3.13 (2026-10). Code generation
    (`build_runner`) is still the answer for JSON/immutability; primary constructors remove much hand boilerplate.
- **Flutter, last ~12 months**
  - 3.47: Material and Cupertino available as standalone packages **`material_ui` / `cupertino_ui` 1.0** (weekly releases; SDK copies
    still exist, deprecation planned for the November stable; migrate with `dart fix --apply --code=migrate_design_widgets`).
    **Impeller default on macOS/Windows/Linux.** Widget Previews stable. Multi-window still experimental. Min iOS 15, macOS 12; Android needs Java 17, AGP 9.1, Gradle 9.3.1, KGP 2.4. UIScene lifecycle required for iOS 27.
    `describeEnum` removed (use `enum.name`).
  - 3.44: Swift Package Manager is the default (CocoaPods legacy). Material/Cupertino frozen in the SDK ahead of decoupling. Hybrid
    Composition++ for Android platform views. Android projects move to AGP built-in Kotlin. `cacheExtent` deprecated (scroll cache API changed).
    `--web-hot-reload` flag deprecated (web hot reload is on by default since 3.35).
  - 3.41: Material 3 token update; `findChildIndexCallback` -> `findItemIndexCallback`; Linux merged UI/platform threads.
  - 3.38: Android default page transition is predictive back; `SnackBar` with action no longer auto-dismisses; UISceneDelegate adoption.
- **Impeller** (2026-10): iOS only engine (no opt-out); Android default on API 29+ with Vulkan, falls back to OpenGL ES otherwise;
  desktop default since 3.47 (opt-out still exists "for now"); **web does not use Impeller** (CanvasKit/skwasm).
- **Web**: HTML renderer removed; renderers are CanvasKit and skwasm (`flutter build web --wasm`). `dart:html`/`dart:js_util`/`package:js`
  -> `package:web` + `dart:js_interop` (required for Wasm).
- **Older versions (legacy projects)**
  - Flutter < 3.47: import `package:flutter/material.dart` and stay there; do not migrate to `material_ui` unless asked. iOS 13-14 still OK.
  - Dart < 3.13: no primary constructors; `final` on parameters still legal. < 3.10: no dot shorthands, write `MainAxisAlignment.center`.
    < 3.8: no `?elem`. Check `environment: sdk:` in pubspec before using any of these; the language version gates them.
  - Flutter < 3.29: HTML web renderer may still be configured. Flutter < 3.27: Impeller on Android is opt-in (default on API 29+ since 3.27).
  - go_router 18 needs Flutter 3.44 / Dart 3.12 and uses material_ui: stay on 17 for older SDKs.
  - Riverpod 2.x: `AutoDisposeNotifier`, `StateProvider` normal imports; freezed 3.0 alone lacks `when/map` (re-added in 3.1.0). Match the project's major versions.

## What changed / stop doing
| Old | New | Since |
|---|---|---|
| Pre-null-safety code, `!` everywhere, `late` to silence errors | Sound null safety; model absence with `?` + flow analysis, `late` only for real lazy init | Dart 3.0 |
| Class hierarchies + `is` chains / visitor for unions | `sealed class` + exhaustive `switch` expression with patterns | Dart 3.0 |
| Small `Pair`/`Tuple` classes, `List` returns of mixed types | Records `(int, String)`, `({int id, String name})` | Dart 3.0 |
| Wrapper classes for zero-cost typing (ids, units) | `extension type UserId(String value) {}` | Dart 3.3 |
| Waiting for macros (`@JsonCodable`) | Cancelled; use build_runner codegen or hand code / primary constructors | 2025-01 |
| `compute()`/manual `Isolate.spawn` + ports for one-off work | `await Isolate.run(() => heavy(data))` | Dart 2.19 |
| `void f(final int x)` | Plain `void f(int x)` (now an error) | Dart 3.13 |
| Field list + constructor + `this.x` boilerplate | Primary constructor `class C(final int x);` | Dart 3.13 |
| `MainAxisAlignment.center` everywhere | `.center` (dot shorthand) when the type is inferred | Dart 3.10 |
| freezed `when`/`map` | Prefer native `switch` patterns (removed in 3.0, re-added in 3.1.0); freezed classes must be `abstract`/`sealed` | freezed 3.0 (2025-02) |
| `WillPopScope` | `PopScope(canPop:, onPopInvokedWithResult:)` (predictive back) | 3.16 (`onPopInvokedWithResult` 3.24) |
| `MaterialStateProperty`, `MaterialState` | `WidgetStateProperty`, `WidgetState` | Flutter 3.22 |
| `Color.withOpacity`, `.value`, `.red` | `color.withValues(alpha: .5)`, `.toARGB32()`, `.r` (wide gamut) | Flutter 3.27 |
| `useMaterial3: false`, M2 widgets (`RaisedButton`, `FlatButton`) | M3 default; `FilledButton`, `NavigationBar`, `SegmentedButton` | 3.16 (M3 default) |
| `describeEnum(e)` | `e.name` | removed 3.47 |
| Navigator 1 `push/pop` only, string routes `onGenerateRoute` | `go_router` (Router API / "Navigator 2") for deep links, web URLs, tabs | - |
| `Provider` + `ChangeNotifier` for every large app | Riverpod 3 or Bloc 9 for large apps; Provider fine for small/legacy | Riverpod 3 (2025) |
| Riverpod `StateProvider`, `StateNotifierProvider`, `AutoDispose*` types | `Notifier`/`AsyncNotifier` + `NotifierProvider`; old ones in `legacy.dart` | Riverpod 3.0 |
| `SharedPreferences.getInstance()` | `SharedPreferencesAsync` or `SharedPreferencesWithCache` | shared_preferences 2.3 |
| `flutter_secure_storage` `encryptedSharedPreferences: true` | Deprecated in v10 (AES-GCM default, migrates data); removed in v11: upgrade via v10 first | v10 2025-12 / v11 2026-08 |
| `isar` 3.x / `hive` 2.x (pub releases 2023 / 2022) | `drift` (SQLite) or `sqflite`; `hive_ce`/`isar_community` forks only for existing data | - |
| CocoaPods-only iOS plugins | Swift Package Manager default | Flutter 3.44 |
| `dart:html`, `package:js`, HTML web renderer | `package:web`, `dart:js_interop`, CanvasKit/skwasm | Flutter 3.29 (HTML renderer removed) |
| `pedantic`, `effective_dart`, `lint` packages | `flutter_lints` 6 / `lints` or `very_good_analysis` 11 | - |
| `flutter_driver` | `integration_test` (+ Patrol for native dialogs) | - |
| Skia shader warm-up (`--cache-sksl`) | Not needed with Impeller (shaders compiled at build time) | Impeller |

## Do this

### Dart idioms (3.x)
```dart
sealed class LoadState<T> {}
class Loading<T> extends LoadState<T> {}
class Loaded<T>(final T data) extends LoadState<T>;   // primary constructor, Dart 3.13+ (else: field + Loaded(this.data))
class Failed<T>(final Object error) extends LoadState<T>;

Widget view(LoadState<User> s) => switch (s) {        // exhaustive: compiler errors on a new subclass
  Loading() => const CircularProgressIndicator(),
  Loaded(:final data) => Text(data.name),
  Failed(:final error) => ErrorView(error),
};

if (json case {'id': final int id, 'name': final String name}) { ... } // validate + destructure JSON
final (lat, lng) = parseCoords(raw);                                    // records
```
- Prefer `final` locals, `const` constructors, `switch` expressions, `if-case` for JSON shape checks over casts.
- Async: always `await` or explicitly `unawaited(...)` (`unawaited_futures` lint). Check `mounted` / `context.mounted` after every `await`
  in a `State` before using `context`. Cancel `StreamSubscription`s in `dispose`. Use `Stream`s for repeated events, `Future` for one.
- CPU work > ~16 ms (JSON > ~1 MB, image decode, crypto) goes to `Isolate.run`. Isolates do not share memory; send plain data.
  Not available on web (runs on the main thread there).

### Widget and build performance rules
- Widget = immutable config; Element = the live tree node (holds State); RenderObject = layout/paint. Rebuilding widgets is cheap;
  relayout/repaint and big subtrees are not.
- `const` constructors everywhere possible (lint `prefer_const_constructors`): const subtrees are skipped on rebuild.
- Scope rebuilds: split big `build` methods into small **widget classes** (not helper methods returning widgets); put state as low as
  possible; listen narrowly (`ref.watch(p.select((s) => s.count))`, `context.select`, `BlocSelector`, `ValueListenableBuilder`).
- Lists: `ListView.builder`/`SliverList.builder` (or `.separated`) for anything unbounded; never `ListView(children: bigList.map(...))` or
  `Column` inside `SingleChildScrollView` for long data. Give fixed extents (`itemExtent`/`prototypeItem`) when rows are uniform.
- Keys: `ValueKey(item.id)` on list items that reorder/insert/delete with state; `GlobalKey` only when you truly need cross-tree access (costly).
- Avoid in hot paths: `Opacity` (use `FadeTransition`/color alpha), `saveLayer` triggers (`ShaderMask`, `ColorFilter`, clip with antialias
  on large areas), `IntrinsicHeight/Width`, `MediaQuery.of(context)` (use `MediaQuery.sizeOf(context)` to rebuild on size only).
- `RepaintBoundary` around a frequently animating subtree (spinners, charts) so the rest is not repainted; verify with DevTools "highlight repaints".
- Images: set `cacheWidth`/`cacheHeight` (or `ResizeImage`) to the displayed size; precache hero images.
- Animations: `AnimatedBuilder` with `child:` for the static part; dispose controllers.

### UI: Material 3, Cupertino, adaptive
- M3 is the default: build themes with `ColorScheme.fromSeed(seedColor: ...)` and `ThemeData(colorScheme: ..., textTheme: ...)`;
  provide light + dark; read colors from `Theme.of(context).colorScheme`, never hard-code.
- Adaptive: `Switch.adaptive`, `Slider.adaptive`, `CircularProgressIndicator.adaptive`, `showAdaptiveDialog`; branch on
  `Theme.of(context).platform` for nav patterns. Use `LayoutBuilder`/`MediaQuery.sizeOf` breakpoints (compact < 600, medium < 840,
  expanded) and `NavigationBar` <-> `NavigationRail`. Keep `SafeArea`; Android 15+ is edge-to-edge.
- Flutter paused Material 3 Expressive and iOS 26 Liquid Glass in core (2025, exact date unverified); do not invent those widgets. New design work lands in
  `material_ui`/`cupertino_ui` (check their changelogs).
- Widget Previews: `@Preview(name: ...)` from `package:flutter/widget_previews.dart` on top-level/static functions or public no-arg widget constructors; `flutter widget-preview start`.

### Navigation
- `go_router` 18 (Flutter team package; README calls it feature-complete, focus on bug fixes): `GoRouter(routes: [...], redirect: ...)`, `MaterialApp.router`.
  `StatefulShellRoute.indexedStack` for bottom tabs keeping state; typed routes via `go_router_builder` (optional). URLs case-sensitive
  since v15. Use `context.go` (replace stack, deep link) vs `context.push` (stack on top).
- Auth redirect: `redirect` reads auth state from a `Listenable` passed as `refreshListenable`. Never trust a route guard for security
  (the server checks permissions).
- Plain `Navigator.push` is fine for a modal flow inside one screen. Handle back with `PopScope`.

### State management: pick by situation
| Situation | Pick |
|---|---|
| Local UI state (toggle, text field, animation) | `StatefulWidget` + `setState`, `ValueNotifier` |
| Small app / following Flutter's architecture guide | `ChangeNotifier` view models + `ListenableBuilder`, DI via `provider` or constructor |
| Most new medium/large apps | **Riverpod 3** (`Notifier`/`AsyncNotifier`, codegen optional); async caching, retry, test overrides built in |
| Team wants explicit events, audit trail, strict layering | **Bloc 9** (`Cubit` first; `Bloc` with events when transitions need logging/debounce) |
| Fine-grained reactive values, few deps | `signals` 7 (smaller community; fine for leaf state) |
| Existing app on Provider/GetX/MobX | Keep it; do not migrate unless asked. Do not add GetX to new code. |
- One state approach per app. `setState` is not wrong; `setState` at the root of a big tree is.

### Project structure (feature-first, layers per Flutter's architecture guide)
```
lib/
  main.dart, main_dev.dart, main_prod.dart   # flavors' entry points
  app/            router.dart, theme.dart, di.dart
  data/services/  api_client.dart (HTTP), local_db.dart       # wrap external APIs, return Future/Stream
  data/repositories/ user_repository.dart                    # single source of truth, caching, error mapping
  domain/models/  user.dart                                   # immutable models (only if logic needs a domain layer)
  features/<feature>/ view (widgets) + view_model / notifier / cubit
  l10n/           app_en.arb, app_de.arb
test/  integration_test/
```
- Dependencies go one way: view -> view model -> repository -> service. Views never call `http`/DB directly.
- Return typed results (`sealed Result<T>` or throw domain exceptions caught in the view model); never show raw exception text to users.

### Data
- HTTP: `http` for simple calls; `dio` when you need interceptors (auth refresh), cancellation, upload progress. Set timeouts; one client
  instance; map errors in the repository. Never log auth headers.
- JSON: hand-written `fromJson` with `if-case` patterns for a few models; `json_serializable` (+ `freezed` 4 for unions/copyWith) or
  `dart_mappable` for many. Codegen cost: `dart run build_runner build -d` adds seconds-minutes per change and `*.g.dart` noise;
  commit generated files or generate in CI, choose one and document it.
- Local DB: **drift** (typed SQLite, migrations, streams, web via wasm) default; `sqflite` for raw SQL on mobile only. Isar original is
  unmaintained (last pub release 2023); Hive 2 last released 2022: for new code avoid both; `hive_ce`/`isar_community` only to keep existing data.
- Key/value: `SharedPreferencesAsync`/`WithCache` for non-secret prefs; `flutter_secure_storage` 11 (Keychain / Android Keystore AES-GCM)
  for tokens. Never store tokens in shared_preferences.

### Platform integration
- **Pigeon** (29.x) generates typed Dart <-> Kotlin/Swift/C++ channel code: use it instead of hand-written `MethodChannel` string maps.
- **FFI**: `dart:ffi` + `package:ffigen` (23.x) for C libraries; `jnigen` (1.0, 2026-10) for Java/Kotlin; `swiftgen` (0.2, pre-1.0) for Swift.
  Build hooks (`hook/build.dart`, formerly native assets; since Dart 3.10, `package:hooks` 1.0) bundle native code without a plugin.
- Federated plugin = app-facing package + `_platform_interface` + per-platform packages (`_android`, `_ios`/`_darwin`, `_web`). Write
  one only if publishing; for app-only native code put a Pigeon API in the app's runner.
- When NOT to add a dependency: no plugin for one native call (Pigeon in-app), no state library for a 3-screen app, no `get_it` when
  Riverpod/provider already does DI, no `dio` for two GETs, no `intl` formatting package beyond `intl`, no analytics/ads SDKs unless the
  feature needs them. Check plugin health (recent release, SPM support, Android AGP 9 compatible) before adding.

### Flavors, i18n, accessibility
- Flavors: Android `productFlavors` + iOS schemes, `flutter run --flavor dev -t lib/main_dev.dart`; compile-time config with
  `--dart-define-from-file=env/dev.json` read via `String.fromEnvironment`. These values are in the binary: never secrets.
- i18n: `flutter: generate: true` + `l10n.yaml`, ARB files, `AppLocalizations.of(context)`; ICU plurals/selects in ARB; format dates and
  numbers with `intl` `DateFormat`/`NumberFormat` using the locale. Test RTL (`Directionality`) and text scaling up to 200%.
- Accessibility: standard widgets have semantics; custom painters/gesture widgets need `Semantics(label:, button: true, ...)`.
  `IconButton` needs `tooltip`. Tap targets >= 48x48 dp. Respect `MediaQuery.disableAnimationsOf`. Check with TalkBack/VoiceOver and
  `meetsGuideline(textContrastGuideline)` / `androidTapTargetGuideline` in widget tests.

## Security
- Obfuscation (`flutter build apk --obfuscate --split-debug-info=build/symbols`) only renames symbols; strings, assets and
  `--dart-define` values are readable. Keep secrets server-side; keep the symbols to de-obfuscate crash reports.
- Tokens: `flutter_secure_storage`; clear on logout; short-lived access tokens + refresh. Biometric gate via `local_auth` if needed.
- TLS: never `badCertificateCallback = (_, __, ___) => true` in shipped code. Certificate/SPKI pinning via a custom `SecurityContext`
  (`dio`'s `IOHttpClientAdapter.createHttpClient`) only with a rotation plan (pin backup keys); pinning does not work on web.
- Platform channels and deep links are input: validate arguments on both sides; validate `go_router` deep-link params.
- WebViews (`webview_flutter`): restrict JavaScript channels and navigation to your origins.
- Android: `android:allowBackup="false"` or backup rules for sensitive data; minimal permissions; `FLAG_SECURE` for sensitive screens.
- Supply chain: pin with committed `pubspec.lock` for apps; review new packages' publisher (verified publisher), license, permissions.

## Performance & quality
- Targets: 60/120 fps = 16.7/8.3 ms per frame for UI + raster; startup to first frame < ~1-2 s on a mid-range Android; APK size reviewed
  with `flutter build apk --analyze-size`.
- Profile in **profile mode on a real device** (`flutter run --profile`), never debug. DevTools: Performance (frame chart, jank, shader
  jank should be gone with Impeller), CPU profiler, Memory (leaks, `leak_tracker` in tests), Network, "Track widget rebuilds" and
  "Highlight repaints", App Size tool.
- Rebuild counts and raster time are what to measure; fix the biggest jank frames first, not micro-optimizations.

## Testing & tooling
- `flutter analyze` (zero warnings in CI), `dart format --set-exit-if-changed .`, `dart fix --apply` after upgrades.
- Lints: `flutter_lints` 6 (default in `flutter create`) or `very_good_analysis` 11 for stricter teams; add `unawaited_futures`,
  `use_build_context_synchronously` (in flutter_lints), `prefer_const_constructors`.
- Tests: unit (`package:test` via `flutter test`), widget (`testWidgets`, `pumpWidget`, `find`, `pump`/`pumpAndSettle`), golden
  (`matchesGoldenFile`; render on one fixed OS/CI image, fonts loaded, e.g. with `alchemist` (unverified status)), `integration_test`
  on device/emulator; **Patrol** 4 for native permission dialogs/notifications. Mocks: `mocktail` (no codegen). Riverpod:
  `ProviderContainer.test()` + overrides; Bloc: `bloc_test`.
- CI: GitHub Actions with `subosito/flutter-action` (unverified pin) or Codemagic (Flutter-focused, macOS runners included); run
  analyze, format, test, then build. Pin the Flutter version (`.fvmrc`/FVM or the action's version input).
- Release: Android App Bundle (`flutter build appbundle`) signed with an upload key, Play App Signing holds the app key; iOS
  `flutter build ipa` with automatic signing or fastlane `match`. fastlane or Codemagic/Bitrise automate uploads (Play internal track,
  TestFlight). Code push: Shorebird (third-party, store-policy limits apply; unverified current terms). Bump `version: x.y.z+build` every upload.
- Desktop/web realities: desktop is production-usable (Impeller default 3.47, multi-window experimental in 3.47); web is for
  app-like SPAs, not SEO/content sites (canvas rendering, large initial download; use `--wasm` + skwasm when browsers allow).

## Common mistakes in AI-written code
- Using primary constructors, dot shorthands or `?elem` in a package whose `sdk:` lower bound is below 3.13 / 3.10 / 3.8.
- `final` on ordinary parameters (error since 3.13). Writing `@JsonCodable()` macros (never shipped).
- Using `BuildContext` after `await` without `if (!context.mounted) return;`.
- Calling `setState` after `dispose`, not disposing controllers/subscriptions, creating controllers inside `build`.
- `MediaQuery.of(context).size` instead of `MediaQuery.sizeOf(context)`; helper methods `_buildX()` instead of widget classes for big parts.
- Riverpod 2 APIs in a Riverpod 3 project (`StateProvider`, `AutoDisposeNotifier`, `ref.watch` inside callbacks instead of `ref.read`);
  non-`abstract`/`sealed` freezed classes on freezed 3+; `BlocProvider.of` in deep code instead of `context.read/watch/select`.
- Hallucinated widgets: `CupertinoLiquidGlass`, M3 Expressive components in core, `Navigator.pushNamedAndRemoveUntil` with go_router routes.
- `withOpacity`, `MaterialStateProperty`, `WillPopScope`, `describeEnum`, `textScaleFactor` (use `TextScaler`).
- `http.get(Uri.parse(...))` with no timeout or status check; JSON parsing on the UI isolate for huge payloads.
- Secrets in `--dart-define`/`.env` assets assumed hidden; `badCertificateCallback` returning `true`.
- Golden tests generated on macOS and run on Linux CI (font/AA diffs).

## Before you ship
- [ ] `flutter analyze` clean, `dart format` clean, all tests (unit, widget, golden, integration) green in CI on pinned Flutter.
- [ ] Errors: `FlutterError.onError` + `PlatformDispatcher.instance.onError` route to crash reporting (privacy-reviewed); user-facing error states.
- [ ] Profile-mode check on a low-end Android and an iPhone: no janky frames in main flows, startup OK, `--analyze-size` reviewed.
- [ ] Semantics labels, 48 dp targets, text scaling 200%, dark mode, RTL if localized; TalkBack/VoiceOver pass.
- [ ] Release builds obfuscated with `--split-debug-info` symbols archived; no secrets in the binary; tokens in secure storage.
- [ ] Permissions minimal, privacy manifest (iOS) and Play Data safety match what is collected; no unused SDKs.
- [ ] Version/build number bumped; signed AAB/IPA from CI; staged rollout + previous build kept for rollback.

## Sources
- https://docs.flutter.dev/release/release-notes : stable list up to 3.47 (accessed 2026-10-09)
- https://storage.googleapis.com/flutter_infra_release/releases/releases_linux.json : 3.47.7 2026-10-08 with Dart 3.13.5; patch dates (2026-10-09)
- https://flutter.dev/blog/whats-new-in-flutter-3-47 : 2026-08-12, material_ui/cupertino_ui, desktop Impeller, min iOS 15/macOS 12, AGP/Gradle/Java, UIScene, previews stable (2026-10-09)
- https://flutter.dev/blog/whats-new-in-flutter-3-44 : 2026-05-20, SPM default, HCPP, Material frozen, web hot reload flag deprecation (2026-10-09)
- https://docs.flutter.dev/release/breaking-changes : 3.38-3.47 breaking changes and deprecations (2026-10-09)
- https://dart.dev/changelog : Dart 3.6-3.13 dates and language/tool changes (2026-10-09)
- https://dart.dev/language/primary-constructors : syntax, requires language 3.13 (2026-10-09)
- https://dart.dev/language/macros (via search) : macros work stopped 2025-01-29, augmentations planned separately (2026-10-09)
- https://docs.flutter.dev/perf/impeller : Impeller per platform, Android Vulkan/GLES fallback, web not Impeller, opt-out flags (2026-10-09)
- https://docs.flutter.dev/platform-integration/web/wasm : `--wasm`, JS fallback without WasmGC, `package:web`/`dart:js_interop` (2026-10-10)
- https://flutter.dev/blog/whats-new-in-flutter-3-29 : 2025-02-12, HTML web renderer removed (2026-10-10)
- https://docs.flutter.dev/platform-integration/web/building : web hot reload on by default since 3.35 (2026-10-10)
- https://dart.dev/tools/hooks : build hooks introduced in Dart 3.10 (2026-10-10)
- https://docs.flutter.dev/app-architecture/guide : MVVM layers, repositories/services, commands (2026-10-09)
- https://docs.flutter.dev/tools/widget-previewer : `@Preview`, `flutter widget-preview start` (2026-10-09)
- https://riverpod.dev/docs/whats_new : Riverpod 3 Notifier unification, legacy providers, retry, mutations, test utils (2026-10-09)
- https://pub.dev/packages/freezed/changelog : 3.0 (2025-02-25) removed when/map, 3.1.0 re-added them; 4.0 (2026-08-22) no `final` params (2026-10-09)
- https://pub.dev/packages/go_router/changelog : v15 case-sensitive URLs, v16-v18 changes, v18 needs Flutter 3.44 (2026-10-10)
- https://pub.dev/packages/flutter_secure_storage/changelog : v10/v11 Android cipher changes, encryptedSharedPreferences removed (2026-10-09)
- https://pub.dev/packages/shared_preferences : SharedPreferencesAsync/WithCache recommended, legacy API (2026-10-09)
- https://pub.dev/api/packages/<name> : versions and publish dates in references/packages.md (2026-10-09)
- https://github.com/isar/isar : v4 not production ready; GitHub API last push 2025-06-14 (2026-10-09)
