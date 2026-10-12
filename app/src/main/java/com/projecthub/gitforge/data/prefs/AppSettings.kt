package com.projecthub.gitforge.data.prefs

/** How the app chooses between light and dark appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Terminal font size bounds, enforced so the terminal stays usable. */
enum class TerminalFontSize(val sp: Int) { SMALL(11), MEDIUM(13), LARGE(16) }

/**
 * User preferences. Values are backed by DataStore; the in-memory mirror in
 * [SettingsRepository] keeps Compose reads synchronous.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val editorFontSizeSp: Int = 14,
    val editorTabSize: Int = 4,
    val editorSoftWrap: Boolean = false,
    val editorShowLineNumbers: Boolean = true,
    val terminalFontSize: TerminalFontSize = TerminalFontSize.MEDIUM,
    val terminalScrollbackLines: Int = 2000,
    val autoFetchOnOpen: Boolean = false,
    val confirmDestructiveGitOperations: Boolean = true,
    val warnBeforeRunningRepositoryScripts: Boolean = true,
    val lastActiveRepositoryId: Long = -1L,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val MIN_EDITOR_FONT_SP = 10
        const val MAX_EDITOR_FONT_SP = 28
        const val MIN_SCROLLBACK = 200
        const val MAX_SCROLLBACK = 20000
    }
}
