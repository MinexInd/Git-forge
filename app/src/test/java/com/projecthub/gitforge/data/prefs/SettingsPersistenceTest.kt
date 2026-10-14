package com.projecthub.gitforge.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves that settings round-trip through real DataStore storage, not just an
 * in-memory field.
 *
 * The commit identity used to live in a plain in-memory var on the container, so it
 * was lost on every process start. These tests exist to stop that regressing: a fresh
 * repository over the same context must observe what a previous one wrote, which is
 * exactly what a restart does.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun commitIdentitySurvivesAFreshRepository() = runTest {
        SettingsRepository(context).setGitAuthor("Ada Lovelace", "ada@example.com")

        val reopened = SettingsRepository(context)
        val settings = reopened.current()
        assertEquals("Ada Lovelace", settings.gitAuthorName)
        assertEquals("ada@example.com", settings.gitAuthorEmail)
    }

    @Test
    fun commitIdentityDefaultsAreUsable() = runTest {
        val settings = SettingsRepository(context).current()
        assertEquals(AppSettings.DEFAULT_GIT_AUTHOR_NAME, settings.gitAuthorName)
        assertEquals(AppSettings.DEFAULT_GIT_AUTHOR_EMAIL, settings.gitAuthorEmail)
    }

    @Test
    fun settingsFlowEmitsPersistedValues() = runTest {
        val repo = SettingsRepository(context)
        repo.setGitAuthor("Grace Hopper", "grace@example.com")

        val observed = repo.settings.first()
        assertEquals("Grace Hopper", observed.gitAuthorName)
        assertEquals("grace@example.com", observed.gitAuthorEmail)
    }

    @Test
    fun unrelatedFlagsDoNotDisturbTheCredentials() = runTest {
        val repo = SettingsRepository(context)
        repo.setGitAuthor("Alan Turing", "alan@example.com")

        repo.setThemeMode(ThemeMode.DARK)
        repo.setOnboardingCompleted(true)

        val settings = repo.current()
        assertEquals("Alan Turing", settings.gitAuthorName)
        assertEquals(ThemeMode.DARK, settings.themeMode)
    }
}
