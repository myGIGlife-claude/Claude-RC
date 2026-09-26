package life.mygig.clauderc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import life.mygig.clauderc.data.ThemeMode

private val Clay = Color(0xFFD97757)
private val ClayDark = Color(0xFFB85C3E)

private val Light = lightColorScheme(
    primary = ClayDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFAF9F5),
    surface = Color(0xFFFAF9F5),
)

private val Dark = darkColorScheme(
    primary = Clay,
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF6E2A14),
    onPrimaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF1F1E1D),
    surface = Color(0xFF1F1E1D),
)

val OkGreen = Color(0xFF2E9E5B)
val BadRed = Color(0xFFD64545)

@Composable
fun ClaudeRcTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx).copy(primary = Clay) else dynamicLightColorScheme(ctx).copy(primary = ClayDark)
        }
        dark -> Dark
        else -> Light
    }
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
