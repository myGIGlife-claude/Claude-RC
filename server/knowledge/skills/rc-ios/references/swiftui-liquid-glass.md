# SwiftUI / UIKit API map for iOS 26-27 (as of 2026-10-09)

Availability checked against developer.apple.com doc data. Gate with `if #available(iOS 26, *)` when the
deployment target is lower.

## Liquid Glass (iOS 26)
| Need | SwiftUI | UIKit |
|---|---|---|
| Glass on a custom control | `.glassEffect(.regular, in: .capsule)`; `.regular.tint(.blue).interactive()` | `UIGlassEffect` in a `UIVisualEffectView` |
| Several glass shapes that morph/blend | `GlassEffectContainer { ... }` + `.glassEffectID(_:in:)` | `UIGlassContainerEffect` |
| Glass buttons | `.buttonStyle(.glass)` / `.buttonStyle(.glassProminent)` | `UIButton.Configuration.glass()` |
| Content under sidebar/inspector | `.backgroundExtensionEffect()` | `UIBackgroundExtensionView` |
| Edge blur under bars | `.scrollEdgeEffectStyle(.soft, for: .top)` | `UIScrollEdgeEffect` |
| Tab bar shrinks on scroll | `.tabBarMinimizeBehavior(.onScrollDown)` | `tabBarMinimizeBehavior` on `UITabBarController` |
| Search as a tab | `Tab(role: .search)` (iOS 18+) | `UISearchTab` (iOS 18) |
| Widgets (APIs from iOS 18) | `widgetAccentedRenderingMode`, `widgetAccentable()` | - |

Rules: glass is for the navigation/control layer floating over content. Don't stack glass on glass, don't put
glass behind dense text, don't recreate bars with custom blurs. Check Reduce Transparency and Increase Contrast.
`UIDesignRequiresCompatibility` is ignored when building with the iOS 27 SDK.

## Other iOS 26 APIs worth knowing
- `WebView` + `WebPage` (import WebKit): native SwiftUI web content; `WebPage` is `@Observable`, can run JS, export PDF.
- `BGContinuedProcessingTask`: user-started background work with system progress UI.
- Foundation Models: `LanguageModelSession`, `SystemLanguageModel`, `@Generable`, tool calling.
- Declared Age Range framework.
- `UIScreen.main` deprecated.

## iOS 27 additions (Xcode 27)
- `@State` is now a macro (`State()`): a class stored in `@State` is initialized and stored once.
- `ContentBuilder` replaces type-specific builders like `ToolbarContentBuilder`, `CommandsBuilder`.
- Drag-to-reorder in any container: `reorderable()` + `reorderContainer(for:isEnabled:move:)`.
- Swipe actions outside `List`: `swipeActions(...)` + `swipeActionsContainer()`.
- Toolbars: `visibilityPriority(_:)`, `ToolbarOverflowMenu`, `topBarPinnedTrailing`, `toolbarMinimizationBehavior(_:for:)`.
- `AsyncImage` caching via `asyncImageURLSession(_:)` and `init(request:...)`.
- Document apps from file URLs: `ReadableDocument` / `WritableDocument`.
- Sheet `crossFade` transition.
- iPhone Duo (27.1): `ArrangementView` (split/overlay layouts; UIKit `UIArrangementViewController`), `ReservedRegion`
  for camera/hinge areas, `onHingeChange` / `DeviceHinge`, vertical-axis toolbars (`toolbarVerticalBehavior`).
- UIKit: compositional layouts auto-invalidate from `@Observable` reads; scene life cycle required.
- Deprecated in the 27.2 SDK docs: `NavigationView`, `foregroundColor(_:)`, `cornerRadius(_:antialiased:)`,
  `navigationBarTitle(_:)`.

## Minimal Liquid Glass control
```swift
struct PlayerControls: View {
    @Namespace private var ns
    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                Button("Back", systemImage: "backward.fill") {}
                    .glassEffect().glassEffectID("back", in: ns)
                Button("Play", systemImage: "play.fill") {}
                    .glassEffect(.regular.interactive()).glassEffectID("play", in: ns)
            }
            .labelStyle(.iconOnly)
        }
    }
}
```
