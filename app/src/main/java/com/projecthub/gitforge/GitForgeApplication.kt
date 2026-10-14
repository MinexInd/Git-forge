package com.projecthub.gitforge

import android.app.Application
import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.core.CrashReporter
import com.projecthub.gitforge.core.DiagnosticsLog
import com.projecthub.gitforge.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application entry point.
 *
 * Also acts as the process-wide service locator so no third-party DI framework is
 * needed. Container construction is lazy, so a failure in one subsystem (for
 * example an unavailable Keystore) does not prevent the rest of the app starting.
 */
class GitForgeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Diagnostics must be available before anything else can fail. The directory
        // is app-private, so a report never leaves the device unless the user sends it.
        val diagnosticsDir = File(filesDir, "diagnostics").apply { mkdirs() }
        DiagnosticsLog.attach(diagnosticsDir)
        CrashReporter.install(diagnosticsDir)
        CrashReporter.lastCrash()?.let { AppLog.w("The previous run ended in a crash; see Diagnostics") }

        container = AppContainer(this)
        AppLog.i("GitForge starting on ${android.os.Build.MODEL} (${android.os.Build.SUPPORTED_ABIS.joinToString()})")

        // Clean scratch space left by a previous process. Repositories are untouched.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                container.workspaceManager.clearTemp()
                // Remove any stale partial download from an interrupted runtime install.
                File(container.runtimeManager.cacheDir, "").listFiles()?.forEach { stale ->
                    if (stale.isFile && stale.name.endsWith(".deb")) stale.delete()
                }
                // A staged update APK is only needed until the installer copies it.
                container.updateManager.clearStaged()
            }.onFailure { AppLog.w("Startup cleanup failed", it) }

            // Warm the settings cache so the first frame uses the saved theme, and
            // keep it warm so the synchronous mirrors of persisted settings (the
            // OAuth client ID and the commit identity) stay correct.
            runCatching {
                container.settingsRepository.current()
                container.settingsRepository.settings.collect {}
            }.onFailure { AppLog.w("Could not load settings", it) }
        }
    }

    companion object {
        @Volatile
        private var instance: GitForgeApplication? = null

        fun container(): AppContainer =
            instance?.container ?: error("GitForgeApplication has not been created")
    }
}
