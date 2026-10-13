package com.projecthub.gitforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.projecthub.gitforge.data.prefs.AppSettings
import com.projecthub.gitforge.data.prefs.TerminalFontSize
import com.projecthub.gitforge.data.prefs.ThemeMode
import com.projecthub.gitforge.di.AppContainer
import com.projecthub.gitforge.github.GitHubDeviceFlowAuth
import com.projecthub.gitforge.ui.RepositoriesViewModel
import com.projecthub.gitforge.ui.auth.GitHubAuthOverlayService
import com.projecthub.gitforge.ui.auth.rememberOverlayPermissionRequest
import com.projecthub.gitforge.ui.components.BannerTone
import com.projecthub.gitforge.ui.components.DestructiveConfirmDialog
import com.projecthub.gitforge.ui.components.InfoBanner
import com.projecthub.gitforge.ui.components.RowDivider
import com.projecthub.gitforge.ui.components.SectionHeader
import com.projecthub.gitforge.ui.components.StatusChip
import com.projecthub.gitforge.ui.theme.CodeTextStyle

/**
 * Settings: appearance, editor, terminal, Git identity, GitHub account, storage and
 * about.
 *
 * Every setting here is persisted through DataStore and takes effect immediately.
 * Security-sensitive items explain what the app does and does not store.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    repositoriesViewModel: RepositoriesViewModel,
    onOpenUrl: (String) -> Unit,
    onOpenDiagnostics: () -> Unit = {},
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = container.settingsRepository.snapshot(),
    )
    val reposState by repositoriesViewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // The effective client ID: the saved override, else the value compiled in.
    val effectiveClientId = container.oauthClientId

    // The floating window is the preferred path for Method A: it stays on screen while
    // the user is in the browser. Refusing the permission degrades to the in-app dialog.
    var useFloatingWindow by remember { mutableStateOf(false) }
    val overlayPermission = rememberOverlayPermissionRequest { granted ->
        if (granted) {
            useFloatingWindow = true
            GitHubAuthOverlayService.start(appContext)
        }
    }

    // When the floating window signs in, the service has already stored the token, so
    // refresh the state the moment the app comes back to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && useFloatingWindow) {
                repositoriesViewModel.refreshGitHubState()
                repositoriesViewModel.loadRemoteRepositories(reset = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showTokenDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var deviceFlowState by remember { mutableStateOf<DeviceFlowUiState?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ---------------------------------------------------------- appearance
        item { SectionHeader("Appearance") }
        item {
            SettingsCard {
                Column {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { container.settingsRepository.setThemeMode(mode) } },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.DARK -> "Dark"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = "Dynamic colour",
                        subtitle = "Use the system wallpaper palette on Android 12 and newer.",
                        checked = settings.useDynamicColor,
                        onCheckedChange = { scope.launch { container.settingsRepository.setDynamicColor(it) } },
                    )
                }
            }
        }

        // -------------------------------------------------------------- editor
        item { SectionHeader("Editor") }
        item {
            SettingsCard {
                Column {
                    SliderRow(
                        title = "Font size: ${settings.editorFontSizeSp}sp",
                        value = settings.editorFontSizeSp.toFloat(),
                        range = AppSettings.MIN_EDITOR_FONT_SP.toFloat()..AppSettings.MAX_EDITOR_FONT_SP.toFloat(),
                        steps = AppSettings.MAX_EDITOR_FONT_SP - AppSettings.MIN_EDITOR_FONT_SP - 1,
                        onValueChange = { scope.launch { container.settingsRepository.setEditorFontSize(it.toInt()) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Show line numbers",
                        subtitle = null,
                        checked = settings.editorShowLineNumbers,
                        onCheckedChange = { scope.launch { container.settingsRepository.setEditorLineNumbers(it) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Soft wrap long lines",
                        subtitle = "Wrap instead of scrolling horizontally.",
                        checked = settings.editorSoftWrap,
                        onCheckedChange = { scope.launch { container.settingsRepository.setEditorSoftWrap(it) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = "Tab size: ${settings.editorTabSize}",
                        value = settings.editorTabSize.toFloat(),
                        range = 1f..8f,
                        steps = 6,
                        onValueChange = { scope.launch { container.settingsRepository.setEditorTabSize(it.toInt()) } },
                    )
                }
            }
        }

        // ------------------------------------------------------------ terminal
        item { SectionHeader("Terminal") }
        item {
            SettingsCard {
                Column {
                    Text("Font size", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalFontSize.entries.forEach { size ->
                            FilterChip(
                                selected = settings.terminalFontSize == size,
                                onClick = { scope.launch { container.settingsRepository.setTerminalFontSize(size) } },
                                label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    RowDivider()
                    SliderRow(
                        title = "Scrollback: ${settings.terminalScrollbackLines} lines",
                        value = settings.terminalScrollbackLines.toFloat(),
                        range = AppSettings.MIN_SCROLLBACK.toFloat()..AppSettings.MAX_SCROLLBACK.toFloat(),
                        steps = 0,
                        onValueChange = { scope.launch { container.settingsRepository.setTerminalScrollback(it.toInt()) } },
                    )
                    Text(
                        "Older output is dropped once the limit is reached, which keeps memory use bounded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ----------------------------------------------------------------- git
        item { SectionHeader("Git") }
        item {
            SettingsCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Commit identity", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${container.gitIdentity.first} <${container.gitIdentity.second}>",
                                style = CodeTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showIdentityDialog = true }) { Text("Change") }
                    }
                    RowDivider()
                    SwitchRow(
                        title = "Confirm destructive Git operations",
                        subtitle = "Ask before deleting branches or reverting commits.",
                        checked = settings.confirmDestructiveGitOperations,
                        onCheckedChange = { scope.launch { container.settingsRepository.setConfirmDestructive(it) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Warn before running repository scripts",
                        subtitle = "Ask before npm install can execute lifecycle scripts.",
                        checked = settings.warnBeforeRunningRepositoryScripts,
                        onCheckedChange = { scope.launch { container.settingsRepository.setWarnRepoScripts(it) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Fetch on open",
                        subtitle = "Fetch from origin automatically when a repository is opened.",
                        checked = settings.autoFetchOnOpen,
                        onCheckedChange = { scope.launch { container.settingsRepository.setAutoFetch(it) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        message = "GitForge never force-pushes and never discards uncommitted changes without asking.",
                        tone = BannerTone.INFO,
                    )
                }
            }
        }

        // -------------------------------------------------------- github/security
        item { SectionHeader("GitHub and security") }
        item {
            SettingsCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (reposState.gitHubConnected) {
                                    reposState.gitHubLogin ?: "Connected"
                                } else {
                                    "Not connected"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (reposState.gitHubConnected) {
                                    "Token stored in the Android Keystore-encrypted credential store."
                                } else {
                                    "Connect to browse, clone, pull and push your repositories."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (reposState.gitHubConnected) {
                            StatusChip("connected", MaterialTheme.colorScheme.secondary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (!reposState.gitHubConnected) {
                        Button(
                            // The device flow cannot start without a client ID, so this is the
                            // one control that stays disabled until the user configures it.
                            enabled = effectiveClientId != null,
                            onClick = {
                                if (overlayPermission.granted) {
                                    useFloatingWindow = true
                                    GitHubAuthOverlayService.start(appContext)
                                } else {
                                    // Ask for the floating-window permission first; the
                                    // launcher callback starts the service once granted, and
                                    // falls through to the in-app dialog if it is refused.
                                    overlayPermission.request()
                                    useFloatingWindow = false
                                }
                            },
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect with GitHub")
                        }
                        if (effectiveClientId == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Disabled until an OAuth client ID is available. Set one below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Opens a floating window so the code stays visible while you " +
                                    "approve it in a browser. Signing in here still requires you " +
                                    "to confirm on GitHub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { deviceFlowState = DeviceFlowUiState.Preparing }) {
                                Text("Use the in-app dialog instead")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showTokenDialog = true }) {
                            Text("Use a personal access token instead")
                        }
                    } else {
                        OutlinedButton(onClick = { showDisconnectConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Disconnect and remove token")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Permissions requested", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    GitHubDeviceFlowAuth.SCOPE_EXPLANATIONS.forEach { (scopeName, explanation) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(scopeName, style = CodeTextStyle, modifier = Modifier.width(90.dp))
                            Text(
                                explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("How credentials are handled", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "The token is encrypted with a key held in the Android Keystore.",
                        "It is never written into a repository, a remote URL, a log or the terminal history.",
                        "It is never sent anywhere except github.com over HTTPS.",
                        "Signing out deletes it immediately.",
                    ).forEach { line ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showClientIdDialog = true }) {
                        Text("Change OAuth client ID")
                    }
                    Text(
                        if (settings.oauthClientId.isBlank()) {
                            "Using the client ID built into this app. You can override it here."
                        } else {
                            "Saved. The client ID is public information, so it is kept with your other preferences."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // -------------------------------------------------------------- storage
        item { SectionHeader("Storage") }
        item {
            SettingsCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Workspace", style = MaterialTheme.typography.titleSmall)
                            MonoPath(container.workspaceManager.root.absolutePath)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val usage = reposState.storageUsage
                    Text(
                        text = usage?.let {
                            "${reposState.localRepositories.size} repositories · ${formatBytes(it.totalBytes)} used"
                        } ?: "Calculating…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Free space available to the app: " +
                            formatBytes(container.workspaceManager.root.usableSpace),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCleanupConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clean temporary files")
                        }
                        OutlinedButton(onClick = { repositoriesViewModel.refreshLocal() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recompute")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Import and export use the Android Storage Access Framework, so GitForge needs no broad " +
                            "storage permission. Repositories stay available offline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------------------------------------------------------------- about
        item { SectionHeader("About") }
        item {
            SettingsCard {
                Column {
                    Text("GitForge", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Code anywhere. Build everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    InlineLabel("Version", com.projecthub.gitforge.BuildConfig.VERSION_NAME)
                    InlineLabel("Build", "${com.projecthub.gitforge.BuildConfig.VERSION_CODE} (${com.projecthub.gitforge.BuildConfig.BUILD_TYPE})")
                    InlineLabel("Application ID", com.projecthub.gitforge.BuildConfig.APPLICATION_ID)
                    InlineLabel("Min SDK", "26 (Android 8.0)")
                    InlineLabel("Device ABI", container.runtimeManager.abiSummary())

                    Spacer(Modifier.height(12.dp))
                    Text("Third-party components", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        "JGit" to "Eclipse Distribution License 1.0",
                        "OkHttp" to "Apache-2.0",
                        "kotlinx.serialization" to "Apache-2.0",
                        "Apache Commons Compress" to "Apache-2.0",
                        "XZ for Java" to "Public Domain",
                        "AndroidX, Jetpack Compose" to "Apache-2.0",
                        "Termux runtime packages" to "Downloaded at your request, not bundled",
                    ).forEach { (name, licence) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                licence,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RowDivider()
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onOpenDiagnostics) {
                        Text("Open Diagnostics — app log and crash reports")
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- device flow

    deviceFlowState?.let { flowState ->
        DeviceFlowDialog(
            container = container,
            state = flowState,
            onStateChange = { deviceFlowState = it },
            onCopyCode = { code -> clipboard.setText(AnnotatedString(code)) },
            onOpenBrowser = onOpenUrl,
            onSuccess = {
                repositoriesViewModel.refreshGitHubState()
                repositoriesViewModel.loadRemoteRepositories(reset = true)
                deviceFlowState = null
            },
            onDismiss = { deviceFlowState = null },
        )
    }

    if (showTokenDialog) {
        TokenInputDialog(
            onDismiss = { showTokenDialog = false },
            onSave = { token ->
                scope.launch {
                    container.credentialStore.save(token, null, null, "bearer")
                    // Verify the token before the user relies on it: a token that
                    // GitHub rejects is worse than no token, because it looks signed in.
                    runCatching { container.gitHubApi.currentUser() }
                        .onSuccess { user ->
                            container.credentialStore.save(token, user.login, null, "bearer")
                            repositoriesViewModel.refreshGitHubState()
                            repositoriesViewModel.loadRemoteRepositories(reset = true)
                        }
                        .onFailure {
                            container.credentialStore.clear()
                            repositoriesViewModel.refreshGitHubState()
                        }
                    showTokenDialog = false
                }
            },
        )
    }

    if (showDisconnectConfirm) {
        DestructiveConfirmDialog(
            title = "Disconnect GitHub?",
            explanation = "The stored token is deleted from the encrypted credential store. Local repositories stay " +
                "on the device, but cloning, pulling and pushing will stop working until you connect again.",
            confirmLabel = "Disconnect",
            onConfirm = {
                repositoriesViewModel.disconnectGitHub()
                showDisconnectConfirm = false
            },
            onDismiss = { showDisconnectConfirm = false },
        )
    }

    if (showCleanupConfirm) {
        DestructiveConfirmDialog(
            title = "Clean temporary files?",
            explanation = "Deletes GitForge's scratch directory. Repositories and their Git history are not touched.",
            confirmLabel = "Clean",
            onConfirm = {
                container.workspaceManager.clearTemp()
                showCleanupConfirm = false
            },
            onDismiss = { showCleanupConfirm = false },
        )
    }

    if (showIdentityDialog) {
        IdentityDialog(
            initialName = settings.gitAuthorName,
            initialEmail = settings.gitAuthorEmail,
            onDismiss = { showIdentityDialog = false },
            onSave = { name, email ->
                scope.launch { container.settingsRepository.setGitAuthor(name, email) }
                showIdentityDialog = false
            },
        )
    }

    if (showClientIdDialog) {
        ClientIdDialog(
            initial = effectiveClientId.orEmpty(),
            onDismiss = { showClientIdDialog = false },
            onSave = { id ->
                // Persisted, so the client ID survives a restart instead of having
                // to be re-entered on every launch.
                scope.launch { container.settingsRepository.setOAuthClientId(id) }
                showClientIdDialog = false
            },
        )
    }
}

// ------------------------------------------------------------------- sub-pieces

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

/** The state machine for the device-flow sign-in dialog. */
sealed interface DeviceFlowUiState {
    data object Preparing : DeviceFlowUiState
    data class Waiting(val userCode: String, val verificationUri: String, val remainingSeconds: Int) : DeviceFlowUiState
    data class Failed(val message: String) : DeviceFlowUiState
}

@Composable
private fun DeviceFlowDialog(
    container: AppContainer,
    state: DeviceFlowUiState,
    onStateChange: (DeviceFlowUiState) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is DeviceFlowUiState.Preparing) {
            val result = runCatching {
                val deviceCode = container.deviceFlowAuth.start()
                onStateChange(
                    DeviceFlowUiState.Waiting(
                        userCode = deviceCode.userCode,
                        verificationUri = deviceCode.verificationUri,
                        remainingSeconds = deviceCode.expiresInSeconds,
                    ),
                )
                container.deviceFlowAuth.awaitToken(
                    deviceCode = deviceCode.deviceCode,
                    intervalSeconds = deviceCode.intervalSeconds,
                    expiresInSeconds = deviceCode.expiresInSeconds,
                ) to deviceCode
            }
            result.onSuccess { (token, deviceCode) ->
                container.credentialStore.save(
                    token = token.accessToken,
                    login = runCatching { container.gitHubApi.currentUser().login }.getOrNull(),
                    scopes = token.scope,
                    tokenType = token.tokenType,
                )
                onSuccess()
            }.onFailure { error ->
                onStateChange(
                    DeviceFlowUiState.Failed(error.message ?: "GitHub sign-in failed."),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = {
            Text(
                when (state) {
                    is DeviceFlowUiState.Preparing -> "Starting GitHub sign-in…"
                    is DeviceFlowUiState.Waiting -> "Enter this code on GitHub"
                    is DeviceFlowUiState.Failed -> "Sign-in failed"
                },
            )
        },
        text = {
            when (state) {
                is DeviceFlowUiState.Preparing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Requesting a device code from GitHub…")
                    }
                }

                is DeviceFlowUiState.Waiting -> {
                    Column {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                state.userCode,
                                style = CodeTextStyle.copy(
                                    fontSize = androidx.compose.ui.unit.TextUnit(
                                        26f,
                                        androidx.compose.ui.unit.TextUnitType.Sp,
                                    ),
                                ),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Open ${state.verificationUri} in a browser, enter the code, and approve access for GitForge.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Waiting for approval… the code expires in about ${state.remainingSeconds / 60} minutes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onCopyCode(state.userCode) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy code")
                            }
                            Button(onClick = { onOpenBrowser(state.verificationUri) }) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open browser")
                            }
                        }
                    }
                }

                is DeviceFlowUiState.Failed -> {
                    Text(state.message)
                }
            }
        },
        confirmButton = {
            if (state is DeviceFlowUiState.Failed) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TokenInputDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal access token") },
        text = {
            Column {
                Text(
                    "Create a fine-grained token on GitHub with repository contents read/write access. " +
                        "The token is encrypted on this device and never leaves it except to github.com.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This value is not logged, not echoed to the terminal, and not written into any Git remote.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(token.trim()) }, enabled = token.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IdentityDialog(
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit identity") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Used as the author and committer on commits you create, from both the Git screen and the terminal.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim().ifBlank { "GitForge User" }, email.trim().ifBlank { "user@gitforge.local" }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ClientIdDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var id by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OAuth client ID") },
        text = {
            Column {
                Text(
                    "Method A needs a public OAuth App client ID. This is not a secret and is safe to store on " +
                        "the device, so it is saved with your other preferences and does not need re-entering later. " +
                        "On GitHub open Settings, Developer settings, OAuth Apps, create an app, enable Device Flow, " +
                        "then paste its client ID here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Never paste a client secret here: the device flow does not use one, and GitForge has nowhere " +
                        "safe to keep a secret in the APK.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(id.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
