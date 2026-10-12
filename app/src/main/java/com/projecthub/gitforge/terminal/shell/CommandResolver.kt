package com.projecthub.gitforge.terminal.shell

import com.projecthub.gitforge.terminal.TerminalLine
import java.io.File

/**
 * An executable that is not a shell builtin: `git`, `python3`, `node`, `npm`.
 *
 * Implementations report [available] honestly. When a runtime is missing the
 * shell prints a real explanation instead of inventing output, which is the
 * difference between a usable tool and a mock.
 */
interface ExternalCommand {
    val name: String
    val summary: String
    val available: Boolean
    val unavailableReason: String?

    suspend fun execute(
        ctx: ShellContext,
        args: List<String>,
        stdin: String,
        onOutput: (String, TerminalLine.Stream) -> Unit,
    ): ShellRunOutput
}

/** Resolves a command name to either a builtin or an external executable. */
class CommandResolver(
    private val builtins: BuiltinRegistry,
    private val externals: List<ExternalCommand>,
) {

    private val externalByName = externals.associateBy { it.name }

    /** Returns the command plus its arguments after applying the app's aliases. */
    fun resolveAlias(words: List<String>): List<String> {
        if (words.isEmpty()) return words
        return when (words.first()) {
            // `python` is what users type; the runtime registers it as `python3`.
            "python" -> listOf("python3") + words.drop(1)
            else -> words
        }
    }

    fun external(name: String): ExternalCommand? = externalByName[name]

    fun isBuiltin(name: String): Boolean = builtins.find(name) != null

    fun availableExternals(): List<ExternalCommand> = externals

    /**
     * Returns a human-readable location for `which`, or null when the command is
     * unknown. Mirrors the contract of the `which` builtin.
     */
    fun describe(name: String): String? {
        if (builtins.find(name) != null) return "builtin:$name"
        val external = externalByName[name] ?: return null
        return if (external.available) "runtime:$name" else null
    }

    /** Location string for `which`, including unavailable runtimes with a marker. */
    fun describeForWhich(name: String): String? {
        builtins.find(name)?.let { return "builtin:$name" }
        val external = externalByName[name] ?: return null
        return if (external.available) {
            "runtime:$name"
        } else {
            "runtime:$name (${external.unavailableReason ?: "not installed"})"
        }
    }

    companion object {
        /** Walks up from [start] to find the enclosing Git working tree. */
        fun findRepositoryRoot(start: File): File? {
            var current: File? = start
            while (current != null) {
                if (File(current, ".git").exists()) return current
                current = current.parentFile
            }
            return null
        }
    }
}
