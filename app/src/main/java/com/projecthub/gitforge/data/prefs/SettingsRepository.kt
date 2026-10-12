package com.projecthub.gitforge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projecthub.gitforge.core.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gitforge_settings")

/**
 * Reads and writes [AppSettings] through DataStore.
 *
 * [snapshot] caches the newest value so Compose can read a setting synchronously
 * after the first collection, which avoids a flash of default styling on start-up.
 */
class SettingsRepository(private val context: Context) {

    @Volatile
    private var cache: AppSettings = AppSettings()

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs.toSettings().also { cache = it }
    }

    /** Most recently observed settings. Safe to read from a composable. */
    fun snapshot(): AppSettings = cache

    suspend fun current(): AppSettings = context.settingsDataStore.data.first().toSettings().also { cache = it }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setEditorFontSize(sp: Int) = edit {
        it[Keys.EDITOR_FONT_SP] = sp.coerceIn(AppSettings.MIN_EDITOR_FONT_SP, AppSettings.MAX_EDITOR_FONT_SP)
    }

    suspend fun setEditorTabSize(size: Int) = edit { it[Keys.EDITOR_TAB_SIZE] = size.coerceIn(1, 8) }

    suspend fun setEditorSoftWrap(enabled: Boolean) = edit { it[Keys.EDITOR_SOFT_WRAP] = enabled }

    suspend fun setEditorLineNumbers(enabled: Boolean) = edit { it[Keys.EDITOR_LINE_NUMBERS] = enabled }

    suspend fun setTerminalFontSize(size: TerminalFontSize) = edit { it[Keys.TERMINAL_FONT] = size.name }

    suspend fun setTerminalScrollback(lines: Int) = edit {
        it[Keys.TERMINAL_SCROLLBACK] = lines.coerceIn(AppSettings.MIN_SCROLLBACK, AppSettings.MAX_SCROLLBACK)
    }

    suspend fun setAutoFetch(enabled: Boolean) = edit { it[Keys.AUTO_FETCH] = enabled }

    suspend fun setConfirmDestructive(enabled: Boolean) = edit { it[Keys.CONFIRM_DESTRUCTIVE] = enabled }

    suspend fun setWarnRepoScripts(enabled: Boolean) = edit { it[Keys.WARN_REPO_SCRIPTS] = enabled }

    suspend fun setLastActiveRepository(id: Long) = edit { it[Keys.LAST_REPO_ID] = id }

    suspend fun setOnboardingCompleted(completed: Boolean) = edit { it[Keys.ONBOARDING_DONE] = completed }

    /** The client ID is public information, so it is stored with the other preferences. */
    suspend fun setOAuthClientId(id: String) = edit { it[Keys.OAUTH_CLIENT_ID] = id.trim() }

    suspend fun setGitAuthor(name: String, email: String) = edit {
        it[Keys.GIT_AUTHOR_NAME] = name.trim()
        it[Keys.GIT_AUTHOR_EMAIL] = email.trim()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        themeMode = this[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        editorFontSizeSp = (this[Keys.EDITOR_FONT_SP] ?: 14)
            .coerceIn(AppSettings.MIN_EDITOR_FONT_SP, AppSettings.MAX_EDITOR_FONT_SP),
        editorTabSize = (this[Keys.EDITOR_TAB_SIZE] ?: 4).coerceIn(1, 8),
        editorSoftWrap = this[Keys.EDITOR_SOFT_WRAP] ?: false,
        editorShowLineNumbers = this[Keys.EDITOR_LINE_NUMBERS] ?: true,
        terminalFontSize = this[Keys.TERMINAL_FONT]?.let {
            runCatching { TerminalFontSize.valueOf(it) }.getOrNull()
        } ?: TerminalFontSize.MEDIUM,
        terminalScrollbackLines = (this[Keys.TERMINAL_SCROLLBACK] ?: 2000)
            .coerceIn(AppSettings.MIN_SCROLLBACK, AppSettings.MAX_SCROLLBACK),
        autoFetchOnOpen = this[Keys.AUTO_FETCH] ?: false,
        confirmDestructiveGitOperations = this[Keys.CONFIRM_DESTRUCTIVE] ?: true,
        warnBeforeRunningRepositoryScripts = this[Keys.WARN_REPO_SCRIPTS] ?: true,
        lastActiveRepositoryId = this[Keys.LAST_REPO_ID] ?: -1L,
        onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
        oauthClientId = this[Keys.OAUTH_CLIENT_ID] ?: "",
        gitAuthorName = this[Keys.GIT_AUTHOR_NAME] ?: AppSettings.DEFAULT_GIT_AUTHOR_NAME,
        gitAuthorEmail = this[Keys.GIT_AUTHOR_EMAIL] ?: AppSettings.DEFAULT_GIT_AUTHOR_EMAIL,
    )

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val EDITOR_FONT_SP = intPreferencesKey("editor_font_sp")
        val EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val EDITOR_SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val TERMINAL_FONT = stringPreferencesKey("terminal_font")
        val TERMINAL_SCROLLBACK = intPreferencesKey("terminal_scrollback")
        val AUTO_FETCH = booleanPreferencesKey("auto_fetch")
        val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
        val WARN_REPO_SCRIPTS = booleanPreferencesKey("warn_repo_scripts")
        val LAST_REPO_ID = longPreferencesKey("last_repo_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val OAUTH_CLIENT_ID = stringPreferencesKey("oauth_client_id")
        val GIT_AUTHOR_NAME = stringPreferencesKey("git_author_name")
        val GIT_AUTHOR_EMAIL = stringPreferencesKey("git_author_email")
    }
}
