package life.mygig.clauderc.ui.components

import androidx.compose.ui.text.TextStyle

/** Digits of equal width, so a number that changes in place doesn't make the text around it jump. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
