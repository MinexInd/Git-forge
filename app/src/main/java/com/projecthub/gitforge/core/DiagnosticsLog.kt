package com.projecthub.gitforge.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Keeps a bounded, redacted record of what the app did, in memory and on disk, so a
 * user can report a failure without needing adb or logcat.
 *
 * Both stores are capped: the in-memory buffer holds the most recent [MAX_LINES]
 * lines and the file rotates past [MAX_FILE_BYTES]. An unbounded log would itself be
 * a way to run a device out of storage.
 *
 * Every line is passed through [Redaction] before it is stored, so a token that
 * reaches a log message cannot reach a bug report either.
 */
object DiagnosticsLog {

    private const val MAX_LINES = 500
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val FILE_NAME = "gitforge.log"

    private val buffer = ArrayDeque<String>()
    private val lock = Any()

    @Volatile
    private var directory: File? = null

    // Writes are serialised on one daemon thread so logging never blocks the main
    // thread and never interleaves two partial lines.
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gitforge-log-writer").apply { isDaemon = true }
    }

    /** Points the on-disk log at the app's private diagnostics directory. */
    fun attach(dir: File) {
        synchronized(lock) { directory = dir.apply { mkdirs() } }
    }

    fun record(level: String, message: String) {
        val line = "${timestamp()}  $level  ${Redaction.redact(message)}"
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        val dir = directory ?: return
        writer.execute { runCatching { append(File(dir, FILE_NAME), line) } }
    }

    /** The most recent lines, oldest first. */
    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    /** The on-disk log, or null when nothing has been written yet. */
    fun fileText(): String? = directory?.let { dir ->
        val file = File(dir, FILE_NAME)
        if (!file.exists()) null else runCatching { file.readText() }.getOrNull()
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        directory?.let { dir ->
            File(dir, FILE_NAME).delete()
            File(dir, "$FILE_NAME.1").delete()
        }
    }

    private fun append(file: File, line: String) {
        if (file.exists() && file.length() > MAX_FILE_BYTES) {
            val previous = File(file.parentFile, "$FILE_NAME.1")
            previous.delete()
            file.renameTo(previous)
        }
        file.appendText(line + "\n")
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
