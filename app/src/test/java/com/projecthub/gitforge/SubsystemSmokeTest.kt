package com.projecthub.gitforge

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.projecthub.gitforge.di.AppContainer
import com.projecthub.gitforge.ui.EditorViewModel
import com.projecthub.gitforge.ui.GitViewModel
import com.projecthub.gitforge.ui.PackagesViewModel
import com.projecthub.gitforge.ui.RepositoriesViewModel
import com.projecthub.gitforge.ui.TerminalViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Constructs every ViewModel and touches every subsystem the app builds at startup,
 * on the JVM. A failure here is a failure the user would see as a crash or a dead
 * screen, and it surfaces without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubsystemSmokeTest {

    @Test
    fun everyViewModelConstructsAndItsInitialWorkRuns() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        container.settingsRepository.snapshot()

        val failures = mutableListOf<String>()
        fun probe(name: String, block: () -> Any?) {
            runCatching { block() }.onFailure {
                failures += "$name -> ${it::class.java.name}: ${it.message}"
            }
        }

        probe("RepositoriesViewModel") { RepositoriesViewModel(container) }
        probe("TerminalViewModel") { TerminalViewModel(container) }
        probe("EditorViewModel") { EditorViewModel(container) }
        probe("GitViewModel") { GitViewModel(container) }
        probe("PackagesViewModel") { PackagesViewModel(container) }

        probe("workspace.root") { container.workspaceManager.root }
        probe("workspace.usage") { container.workspaceManager.usage() }
        probe("runtimeManager.installation") { container.runtimeManager.installation() }
        probe("runtimeProbe") { container.runtimeProbe }
        probe("terminalSessionManager") { container.terminalSessionManager }
        probe("gitEngine") { container.gitEngine }
        probe("buildRuntimeCommands") { container.buildRuntimeCommands() }
        probe("database.repos") { runBlocking { container.database.repositoryDao().all() } }
        probe("settings.snapshot") { container.settingsRepository.snapshot() }
        probe("settings.current") { runBlocking { container.settingsRepository.current() } }

        if (failures.isNotEmpty()) {
            throw AssertionError("Subsystem failures:\n" + failures.joinToString("\n"))
        }
    }
}
