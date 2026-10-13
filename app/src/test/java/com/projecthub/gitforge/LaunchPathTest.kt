package com.projecthub.gitforge

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.projecthub.gitforge.di.AppContainer
import com.projecthub.gitforge.ui.RepositoriesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the exact code the app runs on launch, on the JVM, so a crash on start
 * shows up in `gradlew test` without needing a device. The startup path touches the
 * settings repository, the workspace, the database and the credential store before
 * the first frame is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LaunchPathTest {

    @Test
    fun everySubsystemTheLaunchPathTouchesInitialisesOnThisDevice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)

        container.settingsRepository.snapshot()

        val failures = mutableListOf<String>()
        listOf<Pair<String, () -> Any?>>(
            "settings" to { runBlocking { container.settingsRepository.current() } },
            "workspace" to { container.workspaceManager.root },
            "database" to { runBlocking { container.database.repositoryDao().all() } },
            "fileTree" to { container.fileTreeReader },
            "gitEngine" to { container.gitEngine },
            "credentialStore" to { container.credentialStore.hasToken() },
            "repositoryViewModel" to { RepositoriesViewModel(container) },
        ).forEach { (name, probe) ->
            runCatching { probe() }.onFailure { failures += "$name -> ${it::class.java.name}: ${it.message}" }
        }

        if (failures.isNotEmpty()) {
            throw AssertionError("Launch path failures:\n" + failures.joinToString("\n"), Throwable(failures.first()))
        }
    }
}
