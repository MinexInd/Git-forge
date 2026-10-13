package com.projecthub.gitforge.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app.
 *
 * Seven destinations do not fit legibly in a bottom bar on a phone, so four are
 * primary (bottom bar) and the rest live in a navigation drawer, which is the
 * pattern Material 3 recommends for this case. On a tablet all seven appear in a
 * navigation rail.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isPrimary: Boolean,
    val description: String,
) {
    HOME(
        route = "home",
        label = "Home",
        icon = Icons.Default.Dashboard,
        isPrimary = true,
        description = "Recent projects and quick actions",
    ),
    REPOSITORIES(
        route = "repositories",
        label = "Repositories",
        icon = Icons.Default.Folder,
        isPrimary = true,
        description = "GitHub repositories and local workspaces",
    ),
    EDITOR(
        route = "editor",
        label = "Editor",
        icon = Icons.Default.Code,
        isPrimary = true,
        description = "Project explorer and code editor",
    ),
    GIT(
        route = "git",
        label = "Git",
        icon = Icons.Default.Tune,
        isPrimary = true,
        description = "Status, diffs, commits and branches",
    ),
    TERMINAL(
        route = "terminal",
        label = "Terminal",
        icon = Icons.Default.Terminal,
        isPrimary = false,
        description = "Run shell commands, Git, Python and Node",
    ),
    PACKAGES(
        route = "packages",
        label = "Packages",
        icon = Icons.Default.Extension,
        isPrimary = false,
        description = "Runtimes, npm and pip packages",
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        icon = Icons.Default.Settings,
        isPrimary = false,
        description = "Theme, editor, terminal, security and storage",
    ),
    DIAGNOSTICS(
        route = "diagnostics",
        label = "Diagnostics",
        icon = Icons.Default.BugReport,
        isPrimary = false,
        description = "App log and crash reports",
    );

    companion object {
        val primary: List<Destination> = entries.filter { it.isPrimary }
        val secondary: List<Destination> = entries.filterNot { it.isPrimary }
        val all: List<Destination> = entries.toList()

        fun fromRoute(route: String?): Destination =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}
