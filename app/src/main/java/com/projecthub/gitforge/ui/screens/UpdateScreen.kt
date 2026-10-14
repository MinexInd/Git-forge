package com.projecthub.gitforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projecthub.gitforge.update.UpdateCheck
import com.projecthub.gitforge.update.UpdateProgress

/**
 * Update screen: shows the installed version, asks GitHub for a newer build on the
 * app's channel, then downloads, verifies and installs it through the system
 * installer.
 *
 * Nothing here installs silently. Android always shows its own confirmation, and the
 * app only ever hands the installer an APK that it has already checksummed and
 * checked the signing certificate of.
 */
@Composable
fun UpdateScreen(
    viewModel: com.projecthub.gitforge.ui.viewmodel.UpdateViewModel,
    onOpenReleasePage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The install permission can be granted while the app is backgrounded, so the
    // state is re-read whenever this screen resumes.
    LaunchedEffect(Unit) { viewModel.refreshInstallPermission() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Update", style = MaterialTheme.typography.headlineSmall)
        Text(
            "GitForge checks the ${state.channel} channel on GitHub for a newer build. " +
                "The update is verified before Android is asked to install it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabeledValue("Installed version", state.currentVersion)
                LabeledValue("Channel", state.channel)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.checkForUpdate() }, enabled = !state.checking) {
                Text(if (state.checking) "Checking…" else "Check for updates")
            }
            if (state.checking) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            }
        }

        when (val check = state.check) {
            null -> Unit

            is UpdateCheck.UpToDate -> InfoCard(
                title = "You are up to date",
                body = "The newest build on the ${state.channel} channel is ${check.currentVersion}.",
            )

            is UpdateCheck.Failed -> InfoCard(
                title = "Update check failed",
                body = check.message,
                tone = InfoTone.ERROR,
            )

            is UpdateCheck.Available -> {
                val release = check.release
                InfoCard(
                    title = "Version ${release.version} is available",
                    body = release.notes.take(1200).ifBlank { "No release notes were provided." },
                )
                LabeledValue("Release", release.tagName)
                if (release.publishedAt.isNotBlank()) {
                    LabeledValue("Published", release.publishedAt.take(10))
                }

                when (val progress = state.progress) {
                    is UpdateProgress.Downloading -> {
                        val fraction = if (progress.totalBytes > 0) {
                            (progress.bytesRead.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            "Downloading ${progress.fileName} — ${progress.bytesRead / 1024} KB" +
                                if (progress.totalBytes > 0) " of ${progress.totalBytes / 1024} KB" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is UpdateProgress.Verifying -> Text(
                        "Verifying the download…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    UpdateProgress.ReadyToInstall -> Text(
                        "Downloaded and verified.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    UpdateProgress.Idle -> Unit
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.stagedFile == null) {
                        Button(
                            onClick = { viewModel.download(release) },
                            enabled = state.progress is UpdateProgress.Idle,
                        ) {
                            Text("Download update")
                        }
                    } else {
                        Button(onClick = { viewModel.installStaged() }) { Text("Install") }
                    }
                    OutlinedButton(onClick = { onOpenReleasePage(release.htmlUrl) }) {
                        Text("View release")
                    }
                }

                if (!state.canInstall) {
                    Text(
                        "Android needs permission to install apps from GitForge. " +
                            "Tap Install and the app opens the right settings screen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        state.message?.let { message ->
            InfoCard(title = "Update", body = message)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text(
            "Downloads are stored in app-private storage and removed after install. " +
                "The updater never sends repository contents or credentials anywhere.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class InfoTone { NEUTRAL, ERROR }

@Composable
private fun InfoCard(title: String, body: String, tone: InfoTone = InfoTone.NEUTRAL) {
    Surface(
        color = if (tone == InfoTone.ERROR) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 20,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

/** Small helper so the settings screen can render a one-line status without a card. */
@Composable
fun UpdateStatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
