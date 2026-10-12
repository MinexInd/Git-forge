package com.projecthub.gitforge.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves that settings round-trip through real DataStore storage, not just an
 * in-memory field.
 *
 * The OAuth client ID and the commit identity used to live in plain in-memory vars
 * on the container, so they were lost on every process start. These tests exist to
 * stop that regressing: a fresh repository over the same context must observe what a
 * previous one wrote, which is exactly what a restart does.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun oauthClientIdSurvivesAFreshRepository() = runTest {
        SettingsRepository(context).setOAuthClientId("Ov23liExampleClientId")

        // A second instance stands in for the next app process.
        val reopened = SettingsRepository(context)
        assertEquals("Ov23liExampleClientId", reopened.current().oauthClientId)
    }

    @Test
    fun oauthClientIdIsTrimmedAndCanBeCleared() = runTest {
        val repo = SettingsRepository(context)
        repo.setOAuthClientId("  spaced-id  ")
        assertEquals("spaced-id", repo.current().oauthClientId)

        repo.setOAuthClientId("")
        assertEquals("", repo.current().oauthClientId)
    }

    @Test
    fun unsetOAuthClientIdDefaultsToEmptyRatherThanNull() = runTest {
        val settings = SettingsRepository(context).current()
        assertEquals("", settings.oauthClientId)
        assertTrue("a blank client ID must be treated as not configured", settings.oauthClientId.isBlank())
    }

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
        repo.setOAuthClientId("flow-client")
        repo.setGitAuthor("Grace Hopper", "grace@example.com")

        val observed = repo.settings.first()
        assertEquals("flow-client", observed.oauthClientId)
        assertEquals("Grace Hopper", observed.gitAuthorName)
        assertEquals("grace@example.com", observed.gitAuthorEmail)
    }

    @Test
    fun unrelatedFlagsDoNotDisturbTheCredentials() = runTest {
        val repo = SettingsRepository(context)
        repo.setOAuthClientId("keep-me")
        repo.setGitAuthor("Alan Turing", "alan@example.com")

        repo.setThemeMode(ThemeMode.DARK)
        repo.setOnboardingCompleted(true)

        val settings = repo.current()
        assertEquals("keep-me", settings.oauthClientId)
        assertEquals("Alan Turing", settings.gitAuthorName)
        assertEquals(ThemeMode.DARK, settings.themeMode)
    }
}
