package com.projecthub.gitforge.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A diagnostic log that captured a token would be a new way to leak one, so the
 * redaction guarantee is asserted rather than assumed.
 */
class DiagnosticsLogTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a token written to the log is redacted`() {
        DiagnosticsLog.attach(temp.newFolder())
        DiagnosticsLog.clear()

        DiagnosticsLog.record("E", "push failed with token ghp_ABCdef0123456789ABCdef0123456789abcd")

        val text = DiagnosticsLog.snapshot().joinToString("\n")
        assertFalse("the token leaked into the log: $text", text.contains("ghp_ABCdef0123456789"))
        assertTrue("the redaction marker is missing: $text", text.contains("REDACTED"))
    }

    @Test
    fun `the buffer is bounded`() {
        DiagnosticsLog.attach(temp.newFolder())
        DiagnosticsLog.clear()

        repeat(5000) { DiagnosticsLog.record("D", "line $it") }

        assertTrue("the in-memory buffer grew without bound", DiagnosticsLog.snapshot().size <= 500)
    }
}
