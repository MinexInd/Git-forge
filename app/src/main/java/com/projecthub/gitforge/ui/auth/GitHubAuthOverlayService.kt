package com.projecthub.gitforge.ui.auth

import android.animation.ValueAnimator
import android.app.Notification
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.projecthub.gitforge.GitForgeApplication
import com.projecthub.gitforge.R
import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.core.Redaction
import com.projecthub.gitforge.github.GitHubException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the GitHub device flow in a floating window that stays on screen while the
 * user types the code into a browser.
 *
 * Why a window and not a dialog: the device flow requires the user to leave the app.
 * A normal dialog is destroyed with the activity, so the user comes back to nothing.
 * A `TYPE_APPLICATION_OVERLAY` window owned by a foreground service survives that,
 * which is the only way the code and the polling state stay visible.
 *
 * The token is never drawn in the window, put in the notification, or written to a
 * log. It goes straight into the encrypted credential store.
 */
class GitHubAuthOverlayService : Service() {

    /**
     * Sign-in has to outlive the activity, so the flow runs on the service's own
     * scope and is cancelled when the service is destroyed.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var codeView: TextView? = null
    private var statusView: TextView? = null
    private var openBrowserButton: Button? = null

    private var userCode: String = ""
    private var verificationUri: String = DEFAULT_VERIFICATION_URI

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_DISMISS) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView != null) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.auth_notification_starting)))
        showOverlay()
        beginFlow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the flow

    private fun beginFlow() {
        val container = (application as GitForgeApplication).container

        scope.launch {
            val clientId = container.oauthClientId
            if (clientId.isNullOrBlank()) {
                fail(getString(R.string.auth_error_no_client_id))
                return@launch
            }

            val started = runCatching { container.deviceFlowAuth.start() }
                .getOrElse { error ->
                    fail(messageFor(error))
                    return@launch
                }

            userCode = started.userCode
            verificationUri = started.verificationUri.ifBlank { DEFAULT_VERIFICATION_URI }
            codeView?.text = userCode
            statusView?.text = getString(R.string.auth_status_waiting)
            updateNotification(getString(R.string.auth_notification_waiting))

            // Offer the browser immediately. The user still has to confirm, which is
            // what keeps this a deliberate action and not a silent background login.
            openBrowserButton?.isEnabled = true

            val result = runCatching {
                container.deviceFlowAuth.awaitToken(
                    deviceCode = started.deviceCode,
                    intervalSeconds = started.intervalSeconds,
                    expiresInSeconds = started.expiresInSeconds,
                )
            }

            result.onSuccess { token ->
                // Verify before storing: a token GitHub rejects is worse than none,
                // because the UI would show the user as signed in.
                val login = runCatching { container.gitHubApi.currentUser().login }.getOrNull()
                container.credentialStore.save(
                    token = token.accessToken,
                    login = login,
                    scopes = token.scope,
                    tokenType = token.tokenType,
                )
                AppLog.i("Device flow completed; token stored in the encrypted credential store")
                toast(getString(R.string.auth_success, login ?: getString(R.string.auth_success_fallback)))
                stopSelf()
            }.onFailure { error ->
                fail(messageFor(error))
            }
        }
    }

    private fun fail(message: String) {
        toast(message)
        statusView?.text = message
        stopSelf()
    }

    /** Never surface a raw exception: it can carry a request body with a token in it. */
    private fun messageFor(error: Throwable): String = when (error) {
        is GitHubException -> Redaction.redact(error.message ?: "GitHub sign-in failed.")
        else -> "GitHub sign-in failed. Check your network connection and try again."
    }

    // -------------------------------------------------------------- the overlay

    private fun showOverlay() {
        if (!canDrawOverlays(this)) {
            // The caller only starts this service after the permission check, so this
            // is a belt-and-braces guard rather than the normal path.
            toast(getString(R.string.auth_error_no_overlay_permission))
            stopSelf()
            return
        }

        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#1F2933"))
                setStroke(dp(1), Color.parseColor("#3E4C59"))
            }
            elevation = dp(12).toFloat()
        }

        val title = TextView(this).apply {
            text = getString(R.string.auth_overlay_title)
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }

        val instructions = TextView(this).apply {
            text = getString(R.string.auth_overlay_instructions)
            setTextColor(Color.parseColor("#CBD2D9"))
            textSize = 12f
            setPadding(0, dp(6), 0, dp(10))
        }

        val code = TextView(this).apply {
            text = "——————"
            setTextColor(Color.parseColor("#9FEF00"))
            textSize = 26f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            // Long-press to copy, so the user can paste it into the browser.
            setOnLongClickListener {
                copyToClipboard(userCode)
                toast(getString(R.string.auth_code_copied))
                true
            }
        }
        codeView = code

        val status = TextView(this).apply {
            text = getString(R.string.auth_status_preparing)
            setTextColor(Color.parseColor("#CBD2D9"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(10))
        }
        statusView = status

        val openBrowser = Button(this).apply {
            text = getString(R.string.auth_open_browser)
            isEnabled = false
            setOnClickListener { openVerificationPage() }
        }
        openBrowserButton = openBrowser

        val close = Button(this).apply {
            text = getString(R.string.auth_cancel)
            setOnClickListener { stopSelf() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(openBrowser, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        panel.addView(title)
        panel.addView(instructions)
        panel.addView(code)
        panel.addView(status)
        panel.addView(buttons)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Not focusable, so the browser keeps the keyboard. This is the whole
            // point of an overlay here: type the code without losing the app.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }

        makeDraggable(panel, params)
        panel.alpha = 0f
        manager.addView(panel, params)
        overlayView = panel

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener { panel.alpha = it.animatedValue as Float }
            start()
        }
    }

    /** Drag to move the window out of the way of whatever the user is reading. */
    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    runCatching { windowManager?.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        codeView = null
        statusView = null
        openBrowserButton = null
    }

    // ------------------------------------------------------------------ helpers

    private fun openVerificationPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.auth_error_no_browser)) }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitHub device code", text))
    }

    private fun toast(message: String) {
        // The service has no activity, so use the application context.
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildNotification(text: String): android.app.Notification {
        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_LOW,
                ).setName(getString(R.string.auth_channel_name)).build(),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.auth_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    companion object {
        private const val CHANNEL_ID = "gitforge_github_auth"
        private const val NOTIFICATION_ID = 4101

        const val ACTION_DISMISS = "com.projecthub.gitforge.auth.DISMISS"
        const val DEFAULT_VERIFICATION_URI = "https://github.com/login/device"

        /** True when the app is allowed to draw the floating window. */
        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun start(context: Context) {
            val intent = Intent(context, GitHubAuthOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GitHubAuthOverlayService::class.java))
        }
    }
}
