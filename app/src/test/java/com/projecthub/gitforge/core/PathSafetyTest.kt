package com.projecthub.gitforge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Path validation is the boundary between user input and the filesystem, so these
 * tests focus on the ways a path could escape the repository root.
 */
class PathSafetyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun root(): File = temp.newFolder("repo").also { it.mkdirs() }

    @Test
    fun `resolves a normal relative path inside the root`() {
        val root = root()
        val resolved = PathSafety.resolveInside(root, "src/main/App.kt")
        assertEquals(File(root, "src/main/App.kt").canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun `rejects parent directory traversal`() {
        val root = root()
        val outside = temp.newFile("secret.txt")
        assertThrowsIOException { PathSafety.resolveInside(root, "../secret.txt") }
        assertTrue(outside.exists())
    }

    @Test
    fun `rejects deep traversal that climbs above the root`() {
        val root = root()
        assertThrowsIOException { PathSafety.resolveInside(root, "a/b/../../../etc/passwd") }
    }

    @Test
    fun `rejects an absolute path`() {
        val root = root()
        assertThrowsIOException { PathSafety.resolveInside(root, "/etc/passwd") }
    }

    @Test
    fun `rejects a windows style absolute path`() {
        val root = root()
        assertThrowsIOException { PathSafety.resolveInside(root, "C:\\Windows\\System32") }
    }

    @Test
    fun `rejects a blank path`() {
        val root = root()
        assertThrowsIOException { PathSafety.resolveInside(root, "   ") }
    }

    @Test
    fun `accepts a file name containing dots that are not traversal`() {
        val root = root()
        val resolved = PathSafety.resolveInside(root, ".gitignore")
        assertEquals(File(root, ".gitignore").canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun `isInside is true for a nested path and false for a sibling`() {
        val root = root()
        val nested = File(root, "a/b/c.txt")
        val sibling = temp.newFolder("other")
        assertTrue(PathSafety.isInside(root, nested))
        assertFalse(PathSafety.isInside(root, sibling))
    }

    @Test
    fun `isInside does not treat a prefix-sharing sibling as inside`() {
        val root = File(temp.root, "repo")
        root.mkdirs()
        val lookalike = File(temp.root, "repo-evil")
        lookalike.mkdirs()
        assertFalse(PathSafety.isInside(root, lookalike))
    }

    @Test
    fun `validateNewName rejects separators and traversal tokens`() {
        assertThrowsIOException { PathSafety.validateNewName("a/b") }
        assertThrowsIOException { PathSafety.validateNewName("..") }
        assertThrowsIOException { PathSafety.validateNewName(".") }
        assertThrowsIOException { PathSafety.validateNewName("") }
    }

    @Test
    fun `validateNewName accepts an ordinary file name`() {
        PathSafety.validateNewName("MainActivity.kt")
        PathSafety.validateNewName("README.md")
    }

    @Test
    fun `safe repository folder name accepts ordinary names and rejects traversal`() {
        assertTrue(PathSafety.isSafeRepoFolderName("my-project"))
        assertTrue(PathSafety.isSafeRepoFolderName("my_project.v2"))
        assertFalse(PathSafety.isSafeRepoFolderName(".."))
        assertFalse(PathSafety.isSafeRepoFolderName("a/b"))
        assertFalse(PathSafety.isSafeRepoFolderName(""))
        assertFalse(PathSafety.isSafeRepoFolderName("   "))
    }

    @Test
    fun `relativize returns a forward slash path`() {
        val root = root()
        val nested = File(root, "a/b/c.txt")
        assertEquals("a/b/c.txt", PathSafety.relativize(root, nested))
    }

    private fun assertThrowsIOException(block: () -> Unit) {
        try {
            block()
            fail("Expected an IOException")
        } catch (_: IOException) {
            // Expected.
        }
    }
}
