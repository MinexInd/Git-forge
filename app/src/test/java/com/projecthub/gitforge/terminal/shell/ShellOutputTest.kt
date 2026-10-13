package com.projecthub.gitforge.terminal.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Proves that a builtin's output actually reaches the terminal.
 *
 * Builtins run in-process and return their stdout in the result rather than
 * streaming it, so if the pipeline forgets to forward that result the user sees a
 * command that produces nothing at all.
 */
class ShellOutputTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun engine(root: File): ShellEngine {
        val scope = CoroutineScope(Dispatchers.Default)
        return ShellEngine(BuiltinRegistry(), CommandResolver(BuiltinRegistry(), emptyList()), scope)
    }

    private fun context(root: File): ShellContext = ShellContext(
        cwd = root,
        env = mutableMapOf(),
        home = root,
        workspaceRoot = root,
    )

    private fun run(root: File, line: String): Pair<String, ShellRunOutput> {
        val collected = StringBuilder()
        val outcome = runBlocking {
            engine(root).execute(context(root), line) { chunk, _ -> collected.append(chunk) }
        }
        return collected.toString() to outcome
    }

    @Test
    fun `echo reaches the terminal`() {
        val root = temp.newFolder()
        val (streamed, outcome) = run(root, "echo hello")

        assertTrue("echo produced no streamed output", streamed.contains("hello"))
        assertEquals(0, outcome.exitCode)
    }

    @Test
    fun `pwd reaches the terminal`() {
        val root = temp.newFolder()
        val (streamed, _) = run(root, "pwd")

        assertTrue("pwd produced no streamed output: '$streamed'", streamed.contains(root.absolutePath))
    }

    @Test
    fun `ls lists real files and streams them`() {
        val root = temp.newFolder()
        File(root, "alpha.txt").writeText("a")
        File(root, "beta.txt").writeText("b")

        val (streamed, _) = run(root, "ls")

        assertTrue("ls did not list alpha.txt: '$streamed'", streamed.contains("alpha.txt"))
        assertTrue("ls did not list beta.txt: '$streamed'", streamed.contains("beta.txt"))
    }

    @Test
    fun `cat streams file contents`() {
        val root = temp.newFolder()
        File(root, "note.txt").writeText("contents-visible\n")

        val (streamed, _) = run(root, "cat note.txt")

        assertTrue("cat produced no streamed output: '$streamed'", streamed.contains("contents-visible"))
    }

    @Test
    fun `redirection suppresses terminal output and writes the file`() {
        val root = temp.newFolder()
        val (streamed, outcome) = run(root, "echo redirected > out.txt")

        assertTrue("stdout should be empty when redirected: '$streamed'", streamed.isBlank())
        assertEquals("redirected\n", File(root, "out.txt").readText())
        assertEquals(0, outcome.exitCode)
    }

    @Test
    fun `redirecting stderr still prints stdout`() {
        val root = temp.newFolder()
        val (streamed, _) = run(root, "echo visible 2> err.txt")

        assertTrue("stdout must still be shown when only stderr is redirected: '$streamed'", streamed.contains("visible"))
        assertTrue("err.txt should have been created", File(root, "err.txt").exists())
    }
}
