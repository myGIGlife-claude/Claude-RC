package life.mygig.clauderc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Warning, error and status colours for one theme; read them with `MaterialTheme.statusColors`.
 * Text sets need 4.5:1 on the surfaces they sit on, dots and bars 3:1 (StatusColorsTest pins both).
 * Plain values so the test can check them without a composition.
 */
@Immutable
data class StatusColors(
    val warnContainer: Color,
    val onWarnContainer: Color,
    val warnBorder: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val errorBorder: Color,
    val okText: Color,
    /** The softer green of a plugin's "Installed" label, which sits on the darkest light surface. */
    val installedText: Color,
    val warnText: Color,
    val badText: Color,
    /** Error wording and destructive text buttons: softer than [badText] in the dark theme. */
    val errorText: Color,
    val okDot: Color,
    val warnDot: Color,
    val badDot: Color,
    val offDot: Color,
)

val LightStatusColors = StatusColors(
    warnContainer = Color(0xFFFBF0D8),
    onWarnContainer = Color(0xFF3B2A00),
    warnBorder = Color(0xFFD9BE7E),
    errorContainer = Color(0xFFFBE6E6),
    onErrorContainer = Color(0xFF4A0F12),
    errorBorder = Color(0xFFE8B4B6),
    okText = Color(0xFF17744A),
    installedText = Color(0xFF146B44),
    warnText = Color(0xFF8A5A00),
    badText = Color(0xFFB3262B),
    errorText = Color(0xFFB3262B),
    okDot = Color(0xFF1E8E5A),
    warnDot = Color(0xFFB07A00),
    badDot = Color(0xFFC93A3F),
    offDot = Color(0xFF8C8880),
)

val DarkStatusColors = StatusColors(
    warnContainer = Color(0xFF2A2418),
    onWarnContainer = Color(0xFFF1EFE9),
    warnBorder = Color(0xFF5C4A24),
    errorContainer = Color(0xFF2A1718),
    onErrorContainer = Color(0xFFF1EFE9),
    errorBorder = Color(0xFF6A2A2D),
    okText = Color(0xFF4CC38A),
    installedText = Color(0xFF7FD4A8),
    warnText = Color(0xFFF2C46D),
    badText = Color(0xFFE5484D),
    errorText = Color(0xFFF09A9D),
    okDot = Color(0xFF4CC38A),
    warnDot = Color(0xFFF2C46D),
    badDot = Color(0xFFE5484D),
    offDot = Color(0xFF6B6862),
)
