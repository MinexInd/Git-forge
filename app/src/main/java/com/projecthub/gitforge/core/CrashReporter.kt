package com.projecthub.gitforge.core

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the stack trace of an unhandled exception so the user can see and report it
 * after the app restarts.
 *
 * The trace is written to a file rather than shown immediately: at the moment an
 * unhandled exception reaches here the process is dying, so there is no reliable
 * opportunity to draw UI. The next launch reads the file and offers it in
 * Diagnostics.
 *
 * The trace passes through [Redaction] on the way out, because an exception message
 * can embed a URL or header carrying a token.
 */
object CrashReporter {

    private const val FILE_NAME = "last-crash.txt"

    @Volatile
    private var directory: File? = null

    @Volatile
    private var installed = false

    fun install(dir: File) {
        if (installed) return
        installed = true
        directory = dir.apply { mkdirs() }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            // Always delegate: swallowing the exception here would leave the process
            // in an undefined state and hide the crash from the system.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the recorded crash, or null when the previous run was clean. */
    fun lastCrash(): String? = directory?.let { dir ->
        val file = File(dir, FILE_NAME)
        if (!file.exists()) null else runCatching { file.readText() }.getOrNull()
    }

    /**
     * Records an exception that was caught and handled. It is appended to the crash
     * file so a handled failure is still reportable, without pretending the app died.
     */
    fun recordHandled(throwable: Throwable) {
        val dir = directory ?: return
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()
        runCatching {
            File(dir, FILE_NAME).appendText(
                Redaction.redact("\n--- handled error ---\n$stack"),
            )
        }
    }

    fun clear() {
        directory?.let { File(it, FILE_NAME).delete() }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val dir = directory ?: return
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()

        val header = buildString {
            appendLine("GitForge crash report")
            appendLine("Time:   ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Thread: ${thread.name}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("ABI:    ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("App:    ${com.projecthub.gitforge.BuildConfig.VERSION_NAME} (${com.projecthub.gitforge.BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("--- stack trace ---")
        }

        val recent = DiagnosticsLog.snapshot().takeLast(80)
        val trail = if (recent.isEmpty()) "" else
            "\n--- recent app log ---\n" + recent.joinToString("\n")

        File(dir, FILE_NAME).writeText(Redaction.redact(header + stack + trail))
    }
}
