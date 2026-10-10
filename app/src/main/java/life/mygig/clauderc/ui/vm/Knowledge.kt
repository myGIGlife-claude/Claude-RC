package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.KnowledgeRefresh
import life.mygig.clauderc.api.LauncherApi

/** Developer knowledge: the monthly refresh (switch, run now, last result). */
class KnowledgeController(private val api: LauncherApi, private val tools: VmTools) {
    private val _knowledgeRefresh = MutableStateFlow<KnowledgeRefresh?>(null)
    val knowledgeRefresh = _knowledgeRefresh.asStateFlow()
    private var knowledgePoller: Job? = null
    fun loadKnowledgeRefresh() = tools.action("Loading…") { _knowledgeRefresh.value = api.knowledgeStatus(); pollKnowledge() }
    fun setKnowledgeSchedule(on: Boolean) = tools.action("Saving…") { _knowledgeRefresh.value = api.knowledgeSchedule(on) }
    fun startKnowledgeRefresh() = tools.action("Starting the refresh…") {
        api.knowledgeRefresh()
        _knowledgeRefresh.value = api.knowledgeStatus()
        pollKnowledge()
    }
    /** While a refresh runs (it takes a while), look again every few seconds. */
    private fun pollKnowledge() {
        if (_knowledgeRefresh.value?.running != true || knowledgePoller?.isActive == true) return
        knowledgePoller = tools.scope.launch {
            var left = 3_000   // 5 s each: a little over 4 hours
            while (left-- > 0 && _knowledgeRefresh.value?.running == true) {
                delay(5_000)
                runCatching { api.knowledgeStatus() }.getOrNull()?.let { _knowledgeRefresh.value = it }
            }
        }
    }
}
