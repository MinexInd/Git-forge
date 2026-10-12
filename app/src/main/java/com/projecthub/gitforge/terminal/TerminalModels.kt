package com.projecthub.gitforge.terminal

/** One line of terminal output with the styling the renderer needs. */
data class TerminalLine(
    val text: String,
    val stream: Stream = Stream.STDOUT,
    val ansi: AnsiStyle = AnsiStyle.NONE,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Stream { STDOUT, STDERR, SYSTEM, COMMAND }

    val isError: Boolean get() = stream == Stream.STDERR
}

/** Subset of SGR attributes the terminal renderer understands. */
data class AnsiStyle(
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fg: AnsiColor? = null,
    val bg: AnsiColor? = null,
) {
    companion object {
        val NONE = AnsiStyle()
    }
}

/** The 16 standard ANSI colours, enough for Git, npm and pytest output. */
enum class AnsiColor {
    BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
    BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
    BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE,
}

/** Outcome of a single command submitted to a session. */
data class CommandResult(
    val command: String,
    val exitCode: Int,
    val output: List<TerminalLine>,
    val durationMs: Long,
    val cancelled: Boolean = false,
) {
    val succeeded: Boolean get() = exitCode == 0 && !cancelled
}

enum class SessionState { IDLE, STARTING, RUNNING, CANCELLED, CLOSED, FAILED }

/** A session's externally visible state. */
data class TerminalSessionState(
    val id: Long,
    val name: String,
    val state: SessionState,
    val workingDirectory: String,
    val lastExitCode: Int?,
    val runtimeLabel: String,
)

/** Environment variable entry, surfaced in the UI so users can inspect the shell. */
data class EnvEntry(val key: String, val value: String)

/**
 * Description of what the selected runtime can actually do. The UI uses this to
 * enable or disable actions and to explain limitations instead of silently
 * failing, which is the difference between an honest tool and a fake one.
 */
data class RuntimeCapabilities(
    val hasShell: Boolean,
    val hasGitCli: Boolean,
    val hasPython: Boolean,
    val hasNode: Boolean,
    val hasNpm: Boolean,
    val packageManager: String?,
    val notes: List<String>,
)
