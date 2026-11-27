package life.mygig.clauderc.widget

/** Where a widget tap asked the app to go (see [WidgetLink]). */
sealed interface WidgetTap {
    data object Sessions : WidgetTap
    data class Session(val name: String) : WidgetTap
    data object NewRepo : WidgetTap
    data object Keys : WidgetTap
    data object NewChat : WidgetTap
    data object Cluster : WidgetTap
}

/**
 * The Intent extras [WidgetLink.EXTRA_OPEN] and [WidgetLink.EXTRA_SESSION] as a tap; null when there is
 * none, or the values are unknown or invalid (any app can start MainActivity with extras).
 */
fun parseWidgetTap(open: String?, session: String?): WidgetTap? = when (open) {
    WidgetLink.OPEN_SESSIONS -> WidgetTap.Sessions
    WidgetLink.OPEN_SESSION -> session?.takeIf { WidgetLink.validSession(it) }?.let { WidgetTap.Session(it) }
    WidgetLink.OPEN_NEW_REPO -> WidgetTap.NewRepo
    WidgetLink.OPEN_KEYS -> WidgetTap.Keys
    WidgetLink.OPEN_NEW_CHAT -> WidgetTap.NewChat
    WidgetLink.OPEN_CLUSTER -> WidgetTap.Cluster
    else -> null
}
