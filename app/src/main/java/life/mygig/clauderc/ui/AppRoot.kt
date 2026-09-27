package life.mygig.clauderc.ui

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.ui.screens.AddServiceDialog
import life.mygig.clauderc.ui.screens.TokenServiceDialog
import life.mygig.clauderc.ui.screens.CommandCenterScreen
import life.mygig.clauderc.ui.screens.HostKeyDialog
import life.mygig.clauderc.ui.screens.LoginDialog
import life.mygig.clauderc.ui.screens.NewProjectScreen
import life.mygig.clauderc.ui.screens.ProjectsScreen
import life.mygig.clauderc.ui.screens.SessionsScreen
import life.mygig.clauderc.ui.screens.SettingsScreen
import life.mygig.clauderc.ui.screens.SetupDialog
import life.mygig.clauderc.ui.screens.StatusScreen
import life.mygig.clauderc.ui.screens.TailDialog
import life.mygig.clauderc.ui.theme.ClaudeRcTheme

/** Fingerprint / device-PIN check, implemented by the activity. */
interface AppLock {
    fun canAuthenticate(): Boolean
    suspend fun unlock(reason: String): Boolean
}

/** Runs an action, asking for fingerprint/PIN first when App lock is on. */
fun interface Guard {
    fun run(reason: String, action: () -> Unit)
}

val LocalGuard = staticCompositionLocalOf { Guard { _, action -> action() } }
val LocalAppLock = staticCompositionLocalOf<AppLock?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel, lock: AppLock) {
    val settings by vm.settings.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val s = settings
    if (s == null) {
        ClaudeRcTheme(ThemeMode.SYSTEM) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val guard = remember(s.appLock) {
        Guard { reason, action ->
            if (!s.appLock) action() else scope.launch { if (lock.unlock(reason)) action() }
        }
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
        CompositionLocalProvider(LocalGuard provides guard, LocalAppLock provides lock) {
            val context = LocalContext.current
            // The snackbar collector below lives for the whole screen; always use the current guard.
            val currentGuard by rememberUpdatedState(guard)
            val snackbar = remember { SnackbarHostState() }
            val busy by vm.busy.collectAsState()
            val tab by vm.tab.collectAsState()
            val showSettings by vm.showSettings.collectAsState()
            val status by vm.status.collectAsState()
            val login by vm.login.collectAsState()
            val setup by vm.setup.collectAsState()
            val showAdd by vm.showAdd.collectAsState()
            val tokenService by vm.tokenService.collectAsState()
            val tail by vm.tail.collectAsState()
            val pendingKey by vm.pendingHostKey.collectAsState()

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
                            is Fix.Login -> currentGuard.run("Log in on the server") { vm.showLogin(f.kind) }
                            is Fix.GoTo -> vm.selectTab(f.tab)
                            is Fix.StartAnyway -> vm.startProject(f.project)
                            is Fix.Retry -> f.block()
                            Fix.OpenSettings -> vm.openSettings(true)
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
                                                        text = { Text("${srv.user}@${srv.host}") },
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
                                    IconButton(onClick = { vm.openSettings(true) }) {
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
                            TabItem(tab, Tab.STATUS, "Status", Icons.Filled.CheckCircle, vm)
                            TabItem(tab, Tab.NEW, "New", Icons.Filled.AddCircle, vm)
                            TabItem(tab, Tab.PROJECTS, "Projects", Icons.AutoMirrored.Filled.List, vm)
                            TabItem(tab, Tab.SESSIONS, "Sessions", Icons.Filled.Terminal, vm)
                            TabItem(tab, Tab.COMMAND, "Command", Icons.Filled.Build, vm)
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                // imePadding keeps focused fields above the keyboard (edge-to-edge
                // windows don't resize for it on their own).
                Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    when {
                        showSettings || setupNeeded -> SettingsScreen(vm, s, firstRun = setupNeeded)
                        tab == Tab.STATUS -> StatusScreen(vm)
                        tab == Tab.NEW -> NewProjectScreen(vm)
                        tab == Tab.PROJECTS -> ProjectsScreen(vm)
                        tab == Tab.SESSIONS -> SessionsScreen(vm)
                        tab == Tab.COMMAND -> CommandCenterScreen(vm)
                    }
                }
            }

            login?.let { LoginDialog(vm, it) }
            setup?.let { SetupDialog(vm, it) }
            if (showAdd) AddServiceDialog(vm)
            tokenService?.let { id -> Catalog.byId(id)?.let { TokenServiceDialog(vm, it) } }
            tail?.let { TailDialog(vm, it) }
            pendingKey?.let { HostKeyDialog(vm, it, s.hostKeyFingerprint.ifEmpty { s.previousFingerprint }) }
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
) {
    NavigationBarItem(
        selected = current == tab,
        onClick = { vm.selectTab(tab) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
