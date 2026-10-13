package com.projecthub.gitforge.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.projecthub.gitforge.BuildConfig
import com.projecthub.gitforge.core.CrashReporter
import com.projecthub.gitforge.core.DiagnosticsLog
import com.projecthub.gitforge.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows what the app has logged and any crash from the previous run, so a failure can
 * be reported without adb or logcat.
 *
 * Everything shown here has already passed through [com.projecthub.gitforge.core.Redaction],
 * so tokens do not appear. Sharing is always an explicit user action; nothing is ever
 * uploaded on its own.
 */
@Composable
fun DiagnosticsScreen(container: AppContainer) {
    val context = LocalContext.current
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var deviceInfo by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun refresh() {
        logLines = withContext(Dispatchers.IO) {
            (DiagnosticsLog.fileText()?.lines() ?: DiagnosticsLog.snapshot()).filter { it.isNotBlank() }
        }
        crashText = withContext(Dispatchers.IO) { CrashReporter.lastCrash() }
    }

    LaunchedEffect(refreshKey) {
        if (deviceInfo.isEmpty()) {
            deviceInfo = buildString {
                appendLine("App:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Package: ${BuildConfig.APPLICATION_ID}")
                appendLine("Device:  ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("ABI:     ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            }
        }
        refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deviceInfo.trimEnd(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (crashText != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Previous run crashed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            crashText!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { share(context, crashText ?: logLines.joinToString("\n")) }) {
                    Text("Share")
                }
                OutlinedButton(onClick = { copy(context, crashText ?: logLines.joinToString("\n")) }) {
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        CrashReporter.clear()
                        DiagnosticsLog.clear()
                        crashText = null
                        logLines = emptyList()
                    },
                ) { Text("Clear") }
            }
        }

        item {
            Text("Log (${logLines.size} lines)", style = MaterialTheme.typography.titleMedium)
        }

        if (logLines.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(logLines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Button(onClick = { refreshKey++ }) {
                Text("Refresh")
            }
        }
    }
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GitForge diagnostics")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitForge diagnostics", text))
}
