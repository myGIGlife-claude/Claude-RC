package life.mygig.clauderc.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import life.mygig.clauderc.ui.components.AutoAwesome
import life.mygig.clauderc.ui.components.Folder
import life.mygig.clauderc.ui.components.Hub
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import life.mygig.clauderc.ui.components.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.api.Latest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.ui.screens.AddServiceDialog
import life.mygig.clauderc.ui.screens.TokenServiceDialog
import life.mygig.clauderc.ui.screens.ChatScreen
import life.mygig.clauderc.ui.screens.ClaudeScreen
import life.mygig.clauderc.ui.screens.ConnectionsScreen
import life.mygig.clauderc.ui.screens.DetailScreen
import life.mygig.clauderc.ui.screens.RunScreen
import life.mygig.clauderc.ui.screens.TailScreen
import life.mygig.clauderc.ui.screens.CustomKeysDialog
import life.mygig.clauderc.ui.screens.AppleDialog
import life.mygig.clauderc.ui.screens.KeystoresDialog
import life.mygig.clauderc.ui.screens.HostKeyDialog
import life.mygig.clauderc.ui.screens.LoginDialog
import life.mygig.clauderc.ui.screens.MigrateScreen
import life.mygig.clauderc.ui.screens.NewProjectScreen
import life.mygig.clauderc.ui.screens.ProjectsScreen
import life.mygig.clauderc.ui.screens.SessionsScreen
import life.mygig.clauderc.ui.screens.SettingsScreen
import life.mygig.clauderc.ui.screens.SetupDialog
import life.mygig.clauderc.ui.theme.ClaudeRcTheme

/** Fingerprint / device-PIN check, implemented by the activity. */
interface AppLock {
    fun canAuthenticate(): Boolean
    suspend fun unlock(reason: String): Boolean
}

val LocalAppLock = staticCompositionLocalOf<AppLock?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel, lock: AppLock) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        ClaudeRcTheme(ThemeMode.SYSTEM) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val unlockThen = { reason: String, action: () -> Unit -> scope.launch { if (lock.unlock(reason)) action() }; Unit }

    // App lock is required: turn it on first (or set a screen lock on the phone).
    // (also when the phone's screen lock was removed since: unlock() would pass without checking anything)
    if (!s.appLock || !lock.canAuthenticate()) {
        ClaudeRcTheme(s.theme) {
            RequireLockScreen(lock) { scope.launch { if (lock.unlock("Turn on App lock")) { vm.markUnlocked(); vm.setAppLock(true) } } }
        }
        return
    }

    if (s.appLock && !unlocked) {
        ClaudeRcTheme(s.theme) {
            LockScreen { scope.launch { if (lock.unlock("Open cLaudeRC")) vm.markUnlocked() } }
        }
        return
    }

    // With App lock on, keep app content out of the Recents screen and screenshots.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        if (s.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    ClaudeRcTheme(s.theme) {
        CompositionLocalProvider(LocalAppLock provides lock) {
            val context = LocalContext.current
            val snackbar = remember { SnackbarHostState() }
            val busy by vm.busy.collectAsStateWithLifecycle()
            val tab by vm.tab.collectAsStateWithLifecycle()
            val showSettings by vm.showSettings.collectAsStateWithLifecycle()
            val status by vm.status.collectAsStateWithLifecycle()
            val latest by vm.latest.collectAsStateWithLifecycle()
            val login by vm.login.collectAsStateWithLifecycle()
            val setup by vm.setup.collectAsStateWithLifecycle()
            val showAdd by vm.showAdd.collectAsStateWithLifecycle()
            val detail by vm.detail.collectAsStateWithLifecycle()
            val showRun by vm.showRun.collectAsStateWithLifecycle()
            val chatSession by vm.chatSession.collectAsStateWithLifecycle()
            val showCustom by vm.showCustom.collectAsStateWithLifecycle()
            val showKeystores by vm.showKeystores.collectAsStateWithLifecycle()
            val showApple by vm.showApple.collectAsStateWithLifecycle()
            // Only the open flag: the wizard screen follows the rest of its state itself.
            val showMigrate by remember(vm) { vm.migrate.map { it.open }.distinctUntilChanged() }.collectAsStateWithLifecycle(vm.migrate.value.open)
            val tokenService by vm.tokenService.collectAsStateWithLifecycle()
            val tail by vm.tail.collectAsStateWithLifecycle()
            val pendingKey by vm.pendingHostKey.collectAsStateWithLifecycle()
            val clusterOffer by vm.clusterOffer.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                vm.messages.collect { m ->
                    // Newest message wins; don't queue stale ones.
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar(
                        m.text,
                        actionLabel = m.actionLabel,
                        withDismissAction = m.actionLabel == null,
                        duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        when (val f = m.fix) {
                            is Fix.Login -> vm.showLogin(f.kind)
                            is Fix.GoTo -> vm.selectTab(f.tab)
                            is Fix.StartAnyway -> vm.startProject(f.project)
                            is Fix.Retry -> f.block()
                            Fix.OpenSettings -> unlockThen("Open settings") { vm.openSettings(true) }
                            Fix.OpenClaude -> openInClaude(context)
                            null -> {}
                        }
                    }
                }
            }

            val setupNeeded = !s.isConfigured
            // Adding another server: back drops the blank one and returns to the others.
            val canCancelSetup = setupNeeded && s.servers.any { it.isConfigured && it.id != s.activeId }
            BackHandler(enabled = showSettings && !setupNeeded) { vm.openSettings(false) }
            BackHandler(enabled = canCancelSetup) { vm.removeServer(s.activeId) }
            var serverMenu by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("cLaudeRC")
                                    val sub = status?.hostname?.takeIf { it.isNotBlank() } ?: s.host
                                    if (sub.isNotBlank() && !setupNeeded) {
                                        Box {
                                            // Tap the server name to switch or add servers.
                                            Row(
                                                Modifier.clickable { serverMenu = true },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    sub,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch server")
                                            }
                                            DropdownMenu(expanded = serverMenu, onDismissRequest = { serverMenu = false }) {
                                                s.servers.filter { it.isConfigured }.forEach { srv ->
                                                    DropdownMenuItem(
                                                        text = { Text(srv.host) },
                                                        leadingIcon = {
                                                            if (srv.id == s.activeId) Icon(Icons.Filled.Check, contentDescription = "Current")
                                                        },
                                                        onClick = { serverMenu = false; vm.switchServer(srv.id) },
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Add server") },
                                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                                    onClick = { serverMenu = false; vm.addServer() },
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                if (canCancelSetup) {
                                    IconButton(onClick = { vm.removeServer(s.activeId) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel adding server")
                                    }
                                } else if (detail != null && !showSettings && !setupNeeded) {
                                    IconButton(onClick = { vm.openDetail(null) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                } else if (showSettings && !setupNeeded) {
                                    IconButton(onClick = { vm.openSettings(false) }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (!showSettings && !setupNeeded) {
                                    IconButton(onClick = { vm.selectTab(Tab.STATUS); vm.showAddService(true) }) {
                                        Icon(Icons.Filled.Add, contentDescription = "Connect a service")
                                    }
                                    IconButton(onClick = { unlockThen("Open settings") { vm.openSettings(true) } }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                    }
                                }
                            },
                        )
                        busy?.let { label ->
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!showSettings && !setupNeeded) {
                        NavigationBar {
                            // Narrow screens (e.g. a foldable's cover display) get the short label.
                            val narrow = LocalConfiguration.current.screenWidthDp < 400
                            // A dot on Connections when an app or server update is waiting there.
                            val lt = latest
                            val st = status
                            val updateWaiting = (lt?.appVersionCode ?: 0) > BuildConfig.VERSION_CODE ||
                                (st != null && (st.scriptApi < Updates.MIN_SCRIPT_API || (lt?.serverCommit != null && lt.serverCommit != st.commit)))
                            // Most used first; New in the middle; setup last.
                            TabItem(tab, Tab.SESSIONS, "Sessions", Icons.Filled.Terminal, vm)
                            TabItem(tab, Tab.PROJECTS, "Projects", Icons.Filled.Folder, vm)
                            TabItem(tab, Tab.NEW, "New", Icons.Filled.AddCircle, vm)
                            TabItem(tab, Tab.COMMAND, "Claude", Icons.Filled.AutoAwesome, vm)
                            TabItem(tab, Tab.STATUS, if (narrow) "Connect" else "Connections", Icons.Filled.Hub, vm, dot = updateWaiting)
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                // imePadding keeps focused fields above the keyboard (edge-to-edge
                // windows don't resize for it on their own).
                Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                  if (!showSettings && !setupNeeded && detail == null) UpdateBar(vm, latest, status, tab)
                  Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                        when {
                            showSettings || setupNeeded -> SettingsScreen(vm, s, firstRun = setupNeeded)
                            detail != null -> DetailScreen(vm, detail!!)
                            tab == Tab.STATUS -> ConnectionsScreen(vm)
                            tab == Tab.NEW -> NewProjectScreen(vm)
                            tab == Tab.PROJECTS -> ProjectsScreen(vm)
                            tab == Tab.SESSIONS -> SessionsScreen(vm)
                            tab == Tab.COMMAND -> ClaudeScreen(vm)
                        }
                    }
                  }
                }
            }

            login?.let { LoginDialog(vm, it) }
            setup?.let { SetupDialog(vm, it) }
            if (showAdd) AddServiceDialog(vm)
            if (showCustom) CustomKeysDialog(vm)
            if (showKeystores) KeystoresDialog(vm)
            if (showApple) AppleDialog(vm)
            tokenService?.let { id -> Catalog.byId(id)?.let { TokenServiceDialog(vm, it) } }
            tail?.let { TailScreen(vm, it) }
            if (showRun) RunScreen(vm)
            if (showMigrate) MigrateScreen(vm)
            chatSession?.let { ChatScreen(vm, it) }
            clusterOffer?.let { o ->
                AlertDialog(
                    onDismissRequest = { vm.answerClusterOffer(false) },
                    confirmButton = { TextButton(onClick = { vm.answerClusterOffer(true) }) { Text("Add to cluster") } },
                    dismissButton = { TextButton(onClick = { vm.answerClusterOffer(false) }) { Text("Not now") } },
                    title = { Text("A different Claude account") },
                    text = { Text("${o.host} is signed in as ${o.email}, not the account on your other servers. Add it to cLaudeCluster to see its usage next to the others?") },
                )
            }
            pendingKey?.let { HostKeyDialog(vm, it, s.hostKeyFingerprint.ifEmpty { s.previousFingerprint }) }
        }
    }
}

/** A bar across the top of every tab while a new app build or server scripts are waiting. */
@Composable
private fun UpdateBar(vm: MainViewModel, lt: Latest?, st: StatusData?, tab: Tab) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val newApp = lt?.appVersionCode?.takeIf { it > BuildConfig.VERSION_CODE }
    val apk = lt?.apkUrl
    val server = st != null && (st.scriptApi < Updates.MIN_SCRIPT_API || (lt?.serverCommit != null && lt.serverCommit != st.commit))
    val text: String
    val label: String
    val go: () -> Unit
    when {
        newApp != null && apk != null -> {
            text = "Update: build ${newApp - 100} is ready"; label = "Install"; go = { vm.downloadAndInstall(apk) }
        }
        server && tab != Tab.STATUS -> {
            text = "Server scripts update ready"; label = "View"; go = { vm.selectTab(Tab.STATUS) }
        }
        else -> return
    }
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(
                onClick = go, enabled = busy == null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
            ) { Text(label, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** App lock is required. Shown until it's on; a phone without a screen lock is sent to set one up. */
@Composable
private fun RequireLockScreen(lock: AppLock, onEnable: () -> Unit) {
    val context = LocalContext.current
    var recheck by remember { mutableStateOf(0) }
    val can = remember(recheck) { lock.canAuthenticate() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Text("Turn on App lock", style = MaterialTheme.typography.headlineSmall)
            Text(
                "cLaudeRC can create repos, run commands and chat with Claude on your server, so it always asks for your " +
                    "fingerprint, face or screen lock to open, and again after 30 seconds away.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (can) {
                Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("Turn on App lock") }
            } else {
                Text("This phone has no screen lock yet. Set one up (PIN, pattern, fingerprint or face), then come back.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth()) { Text("Open security settings") }
                OutlinedButton(onClick = { recheck++ }, modifier = Modifier.fillMaxWidth()) { Text("I've set one up") }
            }
        }
    }
}

/** Shown instead of the app while App lock is on and it hasn't been unlocked. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("cLaudeRC is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    current: Tab,
    tab: Tab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    vm: MainViewModel,
    dot: Boolean = false,
) {
    NavigationBarItem(
        selected = current == tab,
        onClick = { vm.openDetail(null); vm.selectTab(tab) },
        icon = {
            BadgedBox(badge = { if (dot) Badge() }) { Icon(icon, contentDescription = if (dot) "$label, update available" else null) }
        },
        label = { Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) },
    )
}
