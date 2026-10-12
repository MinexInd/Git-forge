package com.projecthub.gitforge.core

import android.util.Log

/**
 * Single logging entry point. Every message is passed through [Redaction] so a
 * token embedded in an exception, a URL or a Git error string cannot reach
 * logcat, a crash report or a support bundle.
 */
object AppLog {

    private const val TAG = "GitForge"

    fun d(message: String) = Log.d(TAG, Redaction.redact(message))

    fun i(message: String) = Log.i(TAG, Redaction.redact(message))

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, Redaction.redact(message), throwable?.let { RedactedThrowable(it) })
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, Redaction.redact(message), throwable?.let { RedactedThrowable(it) })
    }

    /**
     * Wrapper that scrubs the message and stack trace of a throwable before it is
     * handed to the platform logger.
     */
    private class RedactedThrowable(cause: Throwable) :
        Throwable(Redaction.redact(cause.message)) {
        init {
            stackTrace = cause.stackTrace
        }
    }
}
