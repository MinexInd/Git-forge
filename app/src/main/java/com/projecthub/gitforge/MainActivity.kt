package com.projecthub.gitforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.projecthub.gitforge.ui.GitForgeApp
import com.projecthub.gitforge.ui.theme.GitForgeTheme

/**
 * The single activity. All navigation is Compose-based, so there is one entry point
 * in the manifest and back handling is owned by the navigation graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as GitForgeApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsState(initial = container.settingsRepository.snapshot())

            GitForgeTheme(
                darkTheme = when (settings.themeMode) {
                    com.projecthub.gitforge.data.prefs.ThemeMode.SYSTEM ->
                        androidx.compose.foundation.isSystemInDarkTheme()
                    com.projecthub.gitforge.data.prefs.ThemeMode.LIGHT -> false
                    com.projecthub.gitforge.data.prefs.ThemeMode.DARK -> true
                },
                useDynamicColor = settings.useDynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GitForgeApp(container = container, settings = settings)
                }
            }
        }
    }
}
