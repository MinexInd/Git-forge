package com.projecthub.gitforge

import android.app.Application
import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            }.onFailure { AppLog.w("Startup cleanup failed", it) }

            // Warm the settings cache so the first frame uses the saved theme.
            runCatching { container.settingsRepository.current() }
                .onFailure { AppLog.w("Could not load settings", it) }
        }
    }

    companion object {
        @Volatile
        private var instance: GitForgeApplication? = null

        fun container(): AppContainer =
            instance?.container ?: error("GitForgeApplication has not been created")
    }
}
