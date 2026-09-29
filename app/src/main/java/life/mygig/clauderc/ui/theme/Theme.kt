package life.mygig.clauderc.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import life.mygig.clauderc.data.ThemeMode

private val Clay = Color(0xFFD97757)
private val ClayDark = Color(0xFFB85C3E)

// Fixed palette (not the phone's wallpaper colours) so every screen matches the design.
private val Light = lightColorScheme(
    primary = ClayDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFFF3D9CD),
    onSecondaryContainer = Color(0xFF3A1E12),
    background = Color(0xFFFAF9F5),
    surface = Color(0xFFFAF9F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2EC),
    surfaceContainer = Color(0xFFEFECE5),
    surfaceContainerHigh = Color(0xFFE8E5DD),
    surfaceContainerHighest = Color(0xFFE1DED5),
    onSurfaceVariant = Color(0xFF5C5952),
    outline = Color(0xFF8C8880),
    outlineVariant = Color(0xFFD3CFC6),
)

private val Dark = darkColorScheme(
    primary = Clay,
    onPrimary = Color(0xFF1A0F0A),
    primaryContainer = Color(0xFF6E2A14),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondaryContainer = Color(0xFF5A3A2E),
    onSecondaryContainer = Color(0xFFF1EFE9),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF1EFE9),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF1EFE9),
    surfaceContainerLowest = Color(0xFF08080A),
    surfaceContainerLow = Color(0xFF16171B),
    surfaceContainer = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF1F2025),
    surfaceContainerHighest = Color(0xFF26272D),
    onSurfaceVariant = Color(0xFFB9B6AD),
    outline = Color(0xFF5D5A54),
    outlineVariant = Color(0xFF45433F),
)

val OkGreen = Color(0xFF4CC38A)
val BadRed = Color(0xFFE5484D)
val WarnAmber = Color(0xFFF2C46D)
val OffGrey = Color(0xFF6B6862)

/** The session log / Run a command look: green on black. */
object Term {
    val Bg = Color(0xFF000000)
    val Panel = Color(0xFF0B120C)
    val Field = Color(0xFF050805)
    val Key = Color(0xFF0F1F12)
    val Border = Color(0xFF2B5A33)
    val Fg = Color(0xFF7EE787)
    val Dim = Color(0xFF3F8F50)
    val Label = Color(0xFF5FAE6C)
    val Head = Color(0xFFCFE9D3)
    val Amber = Color(0xFFE3C565)
}

@Composable
fun ClaudeRcTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) Dark else Light
    // Status/navigation bar icons follow the app's theme, not the phone's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
