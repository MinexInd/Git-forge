package com.projecthub.gitforge.di

import android.content.Context
import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.core.DefaultAppDispatchers
import com.projecthub.gitforge.data.db.GitForgeDatabase
import com.projecthub.gitforge.data.prefs.SettingsRepository
import com.projecthub.gitforge.github.GitHubApi
import com.projecthub.gitforge.github.GitHubDeviceFlowAuth
import com.projecthub.gitforge.git.GitEngine
import com.projecthub.gitforge.security.CredentialStore
import com.projecthub.gitforge.security.EncryptedCredentialStore
import com.projecthub.gitforge.terminal.TerminalSessionManager
import com.projecthub.gitforge.terminal.runtime.GitCommand
import com.projecthub.gitforge.terminal.runtime.NodeCommand
import com.projecthub.gitforge.terminal.runtime.NpmCommand
import com.projecthub.gitforge.terminal.runtime.PipCommand
import com.projecthub.gitforge.terminal.runtime.PythonCommand
import com.projecthub.gitforge.terminal.runtime.RuntimeManager
import com.projecthub.gitforge.terminal.runtime.RuntimeProbe
import com.projecthub.gitforge.terminal.runtime.ShellInterpreterCommand
import com.projecthub.gitforge.terminal.shell.ExternalCommand
import com.projecthub.gitforge.workspace.FileOperations
import com.projecthub.gitforge.workspace.FileTreeReader
import com.projecthub.gitforge.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import java.io.File

/**
 * Manual dependency container.
 *
 * A hand-written container is deliberate: the graph is small and explicit, it adds
 * no annotation processor to the build, and it makes the lifetime of each object
 * obvious. Everything here is a process-wide singleton; per-screen state lives in
 * ViewModels.
 */
class AppContainer(private val context: Context) {

    val dispatchers = DefaultAppDispatchers

    /** Application-lifetime scope for background work that must outlive a screen. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob()) + dispatchers.io

    // ------------------------------------------------------------------ storage

    val database: GitForgeDatabase by lazy { GitForgeDatabase.create(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val workspaceManager: WorkspaceManager by lazy { WorkspaceManager(context.filesDir) }

    val fileOperations: FileOperations by lazy { FileOperations() }

    val fileTreeReader: FileTreeReader by lazy { FileTreeReader() }

    // ---------------------------------------------------------------- security

    val credentialStore: CredentialStore by lazy {
        try {
            EncryptedCredentialStore(context)
        } catch (t: Throwable) {
            // A device with a broken Keystore must still get a working app that tells
            // the user what happened, rather than a crash on launch.
            AppLog.e("Encrypted credential storage is unavailable on this device", t)
            throw IllegalStateException(
                "GitForge could not open secure credential storage on this device. " +
                    "Sign-in is disabled until the device Keystore is available.",
                t,
            )
        }
    }

    // --------------------------------------------------------------------- git

    val gitEngine: GitEngine by lazy { GitEngine() }

    // ------------------------------------------------------------------ github

    val gitHubApi: GitHubApi by lazy { GitHubApi(tokenProvider = { credentialStore.token() }) }

    val deviceFlowAuth: GitHubDeviceFlowAuth by lazy {
        GitHubDeviceFlowAuth(clientIdProvider = { oauthClientId })
    }

    /**
     * The OAuth client ID is public information and safe in the APK: the device flow
     * exists precisely so that no client *secret* is needed. Users who prefer their
     * own app registration can override it in Settings.
     */
    var oauthClientId: String? = null

    // ----------------------------------------------------------------- terminal

    val runtimeManager: RuntimeManager by lazy { RuntimeManager(context.filesDir) }

    val runtimeProbe: RuntimeProbe by lazy { RuntimeProbe(runtimeManager) }

    val terminalSessionManager: TerminalSessionManager by lazy {
        TerminalSessionManager(
            workspaceRoot = workspaceManager.root,
            homeDir = File(runtimeManager.runtimeRoot, "home").apply { mkdirs() },
            runtimeEnvironment = { runtimeManager.environmentFor(emptyMap()) },
            runtimeLabel = { "GitForge shell" },
            scrollbackLimit = { settingsRepository.snapshot().terminalScrollbackLines },
            scope = applicationScope,
        ).also { manager ->
            manager.runtimeCommands = buildRuntimeCommands()
        }
    }

    /**
     * The external commands the shell can reach.
     *
     * `git` is JGit-backed so the terminal and the Git screen share one engine and
     * can never disagree about the index. The rest come from the installed userspace.
     */
    fun buildRuntimeCommands(): List<ExternalCommand> = listOf(
        GitCommand(gitEngine) {
            val settings = settingsRepository.snapshot()
            commitIdentity(settings)
        },
        NodeCommand(runtimeManager),
        NpmCommand(runtimeManager),
        PythonCommand(runtimeManager),
        PipCommand(runtimeManager),
        ShellInterpreterCommand(runtimeManager),
    )

    /** Commit identity, configurable in Settings and used by both Git UI and terminal. */
    fun commitIdentity(settings: com.projecthub.gitforge.data.prefs.AppSettings): Pair<String, String> =
        gitIdentity

    var gitIdentity: Pair<String, String> = ("GitForge User" to "user@gitforge.local")
        set(value) {
            field = value
            AppLog.i("Commit identity updated")
        }

    val workspaceRoot: File get() = workspaceManager.root
}
