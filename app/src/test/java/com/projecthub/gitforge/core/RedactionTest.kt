package com.projecthub.gitforge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Credential redaction is a security control, so it is tested against the exact
 * shapes that appear in real Git and GitHub output.
 */
class RedactionTest {

    @Test
    fun `redacts a token embedded in an https remote url`() {
        val input = "fatal: could not read from https://ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345@github.com/owner/repo.git"
        val output = Redaction.redact(input)
        assertFalse("the token must not survive redaction", output.contains("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"))
        assertTrue("the host must be preserved for the message to be useful", output.contains("github.com"))
    }

    @Test
    fun `redacts a bearer authorization header`() {
        val input = "Authorization: Bearer ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"
        val output = Redaction.redact(input)
        assertFalse(output.contains("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"))
        assertTrue(output.contains("Authorization"))
    }

    @Test
    fun `redacts a classic personal access token anywhere in a message`() {
        val token = "ghp_" + "a".repeat(36)
        assertFalse(Redaction.redact("token=$token").contains(token))
    }

    @Test
    fun `redacts a fine grained personal access token`() {
        val token = "github_pat_11ABCDEFG0abcdefghijklmnop"
        assertFalse(Redaction.redact("using $token now").contains(token))
    }

    @Test
    fun `redacts an oauth access token`() {
        val token = "gho_" + "b".repeat(36)
        assertFalse(Redaction.redact("auth $token").contains(token))
    }

    @Test
    fun `leaves ordinary text untouched`() {
        val text = "On branch main\nnothing to commit, working tree clean"
        assertEquals(text, Redaction.redact(text))
    }

    @Test
    fun `leaves a short hexadecimal string alone so commit ids stay readable`() {
        val text = "commit 4f3a1c9"
        assertEquals(text, Redaction.redact(text))
    }

    @Test
    fun `containsSecret detects a token and ignores clean text`() {
        assertTrue(Redaction.containsSecret("ghp_" + "c".repeat(36)))
        assertFalse(Redaction.containsSecret("nothing to see here"))
    }

    @Test
    fun `redact handles null and empty input without throwing`() {
        assertEquals("", Redaction.redact(null))
        assertEquals("", Redaction.redact(""))
    }
}
