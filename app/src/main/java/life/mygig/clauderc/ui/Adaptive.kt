package life.mygig.clauderc.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable

/** How much room the window gives: phone, foldable inner screen / tablet portrait, wide. */
enum class LayoutKind { COMPACT, MEDIUM, EXPANDED }

/** Material window-size-class width breakpoints (dp): compact < 600 <= medium < 840 <= expanded. */
fun layoutKind(widthDp: Int): LayoutKind = when {
    widthDp >= 840 -> LayoutKind.EXPANDED
    widthDp >= 600 -> LayoutKind.MEDIUM
    else -> LayoutKind.COMPACT
}

/** The window's layout kind; follows resizes, folds and unfolds. Decided by width only, never device type or orientation. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun currentLayoutKind(): LayoutKind = layoutKind(currentWindowAdaptiveInfo().windowSizeClass.minWidthDp)
