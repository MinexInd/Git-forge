package com.projecthub.gitforge.ui.auth

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Asks for the "display over other apps" permission and reports whether it is held.
 *
 * `SYSTEM_ALERT_WINDOW` has no runtime dialog: the only way to grant it is the system
 * settings screen, so this returns from that screen and re-reads the setting. The
 * caller decides what to do when the permission is refused, because the flow still
 * works through the in-app dialog without it.
 */
@Composable
fun rememberOverlayPermissionRequest(
    onResult: (granted: Boolean) -> Unit = {},
): OverlayPermissionRequest {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(GitHubAuthOverlayService.canDrawOverlays(context))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        granted = GitHubAuthOverlayService.canDrawOverlays(context)
        onResult(granted)
    }

    // The settings screen reports no result, so re-check whenever we come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = GitHubAuthOverlayService.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return OverlayPermissionRequest(granted) {
        if (GitHubAuthOverlayService.canDrawOverlays(context)) {
            onResult(true)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher.launch(intent)
        }
    }
}

/** Whether the overlay permission is held, plus a way to ask for it. */
class OverlayPermissionRequest(
    @JvmField val granted: Boolean,
    private val requestFn: () -> Unit,
) {
    fun request() = requestFn()
}
