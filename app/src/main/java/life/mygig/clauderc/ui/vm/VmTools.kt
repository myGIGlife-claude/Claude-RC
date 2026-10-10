package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.CoroutineScope
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.ui.Fix

/**
 * What MainViewModel lends its feature groups: its scope, the shared spinner ([action]) and
 * the snackbar or chat banner ([say], [report]). The groups never hold the view model itself.
 */
class VmTools(
    val scope: CoroutineScope,
    private val runAction: (String, ((ApiException) -> Unit)?, suspend () -> Unit) -> Unit,
    private val sayTo: (String, String?, Fix?) -> Unit,
    private val reportTo: (ApiException) -> Unit,
) {
    /** Runs a server call with the spinner on and maps any failure to a friendly message. */
    fun action(label: String, onError: ((ApiException) -> Unit)? = null, block: suspend () -> Unit) = runAction(label, onError, block)
    fun say(text: String, label: String? = null, fix: Fix? = null) = sayTo(text, label, fix)
    fun report(e: ApiException) = reportTo(e)
}
