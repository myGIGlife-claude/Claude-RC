# Design tokens, handoff and platform mapping (as of 2026-10)

## Token tiers
1. **Primitive** (reference): `color.blue.500`, `space.4`, `radius.md`. Never used directly in components.
2. **Semantic** (system): `color.surface`, `color.text.muted`, `color.danger`, `space.inset.card`. Swapped per mode/brand.
3. **Component** (optional, only when needed): `button.primary.bg`. Don't create these by default (most systems over-tokenize).

## DTCG 2025.10 format (first stable, 2025-10-28)
- Modules: **Format**, **Color**, **Resolver**. Files `.tokens` / `.tokens.json`, media type `application/design-tokens+json`.
- Token = object with `$value`, `$type` (or inherited from group), optional `$description`, `$deprecated`, `$extensions`.
- Aliases: `"{color.blue.500}"`; property-level `"$ref": "#/color/blue/500/$value/components/0"`. Groups can `$extends` another group.
- Types: color, dimension (`{value, unit: px|rem}`), fontFamily, fontWeight, duration (`{value, unit: ms|s}`), cubicBezier, number; composites strokeStyle, border, transition, shadow, gradient, typography.
- Color value is an object (`colorSpace`, `components`, optional `alpha`, optional `hex` fallback), 14 listed spaces incl. srgb, oklch, oklab and display-p3.
- **Resolver** (`.resolver.json`): sets + modifiers with contexts (e.g., theme light/dark, density) + resolution order; replaces copying whole files per mode.

```json
{
  "color": {
    "$type": "color",
    "blue": { "600": { "$value": { "colorSpace": "oklch", "components": [0.52, 0.17, 255], "hex": "#2f5fd0" } } },
    "primary": { "$value": "{color.blue.600}", "$description": "Primary actions" }
  },
  "space": { "$type": "dimension", "4": { "$value": { "value": 16, "unit": "px" } } }
}
```

## Pipeline
- Source of truth: tokens JSON in the repo (or Figma Variables exported to DTCG). Code review token changes like code.
- Build: Style Dictionary v5 (DTCG 2025.10 support partial and growing: structured colors since 5.3.0, object dimensions since 5.4.0; check release notes per type) or Terrazzo. Outputs: CSS custom properties, Tailwind v4 `@theme` CSS, Kotlin/Compose objects, Swift.
- Figma: Variables (collections + modes for light/dark/brand/density), Dev Mode for inspection and annotations, Code Connect to map Figma components to real code, the Figma MCP server lets agents read frames/variables. Native DTCG variable import/export was announced Nov 2025 (rollout date unverified); plugins cover it otherwise.
- Agent rule: if a design file or tokens exist, read them first; never eyeball hex values from screenshots when tokens exist.

## Translating tokens
**CSS** (Tailwind v4 reads CSS variables via `@theme`):
```css
@theme { --color-primary: oklch(52% .17 255); --spacing: .25rem; --radius-md: .5rem; }
:root { --color-surface: light-dark(#fff, oklch(22% .012 255)); }
```
**Compose** (M3):
```kotlin
private val Light = lightColorScheme(primary = Brand600, onPrimary = Color.White, surface = Neutral99)
private val Dark = darkColorScheme(primary = Brand300, onPrimary = Brand900, surface = Neutral10)
@Composable fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val dynamic = Build.VERSION.SDK_INT >= 31
  val ctx = LocalContext.current
  val scheme = when { dynamic && dark -> dynamicDarkColorScheme(ctx); dynamic -> dynamicLightColorScheme(ctx); dark -> Dark; else -> Light }
  MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
}
```
Extra semantic roles (success, warning) via a `CompositionLocal` data class; spacing via an object of `Dp`. Decide deliberately whether brand color beats dynamic color.

**SwiftUI**: Asset Catalog color sets with Any/Dark (+ High Contrast) appearances, exposed as `extension ShapeStyle where Self == Color { static var surface: Color { Color("surface") } }`; text styles (`.body`, `.headline`) for Dynamic Type, `@ScaledMetric` for custom spacing that should scale.

## Material 3 / Expressive vs Apple HIG / Liquid Glass
| Topic | Material 3 (+ Expressive, 2025) | Apple HIG (Liquid Glass, iOS 26; refined iOS 27) |
|---|---|---|
| Color | Tonal palettes, color roles, dynamic color (Android 12+) | System colors + semantic labels, vibrancy; tint sparingly |
| Surfaces | Tonal surface containers; elevation = tone | Content layer + floating Liquid Glass controls layer; glass never on content |
| Type | Roboto Flex / brand; type scale tokens (display…label); Expressive adds emphasized styles | SF Pro, text styles with Dynamic Type |
| Shape | Shape scale; Expressive adds shape morphing and a broad shape library | Concentric corners matching device; capsules for controls |
| Nav | Navigation bar (compact), rail (medium+), drawer/expanded rail; floating toolbars | Tab bar (minimizes on scroll), sidebar on iPad/Mac (`NavigationSplitView`), toolbars |
| Motion | Motion scheme springs: spatial vs effects, standard vs expressive | Fluid, interruptible springs; glass morphs between controls (`GlassEffectContainer`, `glassEffectID`) |
| Targets | 48x48dp | 44x44pt |
| Back | System/predictive back gesture | Edge swipe, back button in nav bar |
| Sheets | Bottom sheets, side sheets | Sheets with detents; glass sheet backgrounds |
- Liquid Glass adoption: rebuilding with the iOS 26+ SDK gives system bars/controls the material automatically; custom floating controls use `.glassEffect()` / `.buttonStyle(.glass)`. Remove custom backgrounds on toolbars/tab bars that block it. Test with Reduce Transparency, Increase Contrast and the user's clear/tinted Liquid Glass setting.
- Expressive adoption: wrap in the expressive theme/motion scheme where available (material3 1.5 line), use new components (button groups, split buttons, FAB menu, loading indicator, floating toolbars) only where they fit; keep hierarchy rules.
- Cross-platform decision rule: share brand tokens (color, type ramp intent, spacing, voice) and flows; let each platform own chrome, navigation, controls, motion feel and haptics.
