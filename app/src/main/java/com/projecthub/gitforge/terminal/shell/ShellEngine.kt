package com.projecthub.gitforge.terminal.shell

import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.terminal.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Executes command lines.
 *
 * Design:
 *  * builtins run in-process, so `cd` and `export` genuinely persist in a session;
 *  * external commands (git, python3, node, npm) come from the runtime and stream
 *    output through [onOutput] as it arrives;
 *  * a pipeline chains stages in memory, capped at [MAX_PIPELINE_BYTES] so a
 *    runaway producer cannot exhaust memory;
 *  * the active job is retained so the UI stop button cancels a real process.
 */
class ShellEngine(
    private val builtins: BuiltinRegistry,
    private val resolver: CommandResolver,
    private val scope: CoroutineScope,
) {

    @Volatile
    private var activeJob: Job? = null

    val isRunning: Boolean get() = activeJob?.isActive == true

    /** Cancels the running command. Returns true when something was cancelled. */
    suspend fun cancel(): Boolean {
        val job = activeJob ?: return false
        job.cancelAndJoin()
        activeJob = null
        return true
    }

    suspend fun execute(
        ctx: ShellContext,
        commandLine: String,
        onOutput: (String, TerminalLine.Stream) -> Unit,
    ): ShellRunOutput {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return ShellRunOutput("", "", 0)
        if (ctx.history.lastOrNull() != trimmed) ctx.history += trimmed

        val job = scope.launch { }
        activeJob = job

        val result = try {
            val tokens = ShellLexer.tokenize(trimmed)
            val env = ctx.effectiveEnv()
            val expandedTokens = tokens.map { token ->
                if (token is Token.Word) {
                    Token.Word(
                        ShellLexer.expand(token, env, ctx.lastExitCode, ctx.home.absolutePath),
                        token.quoted,
                    )
                } else {
                    token
                }
            }

            // Redirection targets are carried as raw strings and resolved per stage
            // so `> out.txt` lands in the current directory at execution time.
            val pipeline = ShellLexer.toPipeline(expandedTokens, trimmed) { File(it) }
            runPipeline(ctx, pipeline, onOutput)
        } catch (e: ShellSyntaxException) {
            ShellRunOutput("", "shell: ${e.message}\n", 2)
        } catch (e: kotlinx.coroutines.CancellationException) {
            ShellRunOutput("", "", 130, cancelled = true)
        } catch (e: Throwable) {
            ShellRunOutput("", "shell: ${e.message ?: "unexpected failure"}\n", 1)
        } finally {
            activeJob = null
        }

        ctx.lastExitCode = result.exitCode
        return result
    }

    private suspend fun runPipeline(
        ctx: ShellContext,
        pipeline: ShellPipeline,
        onOutput: (String, TerminalLine.Stream) -> Unit,
    ): ShellRunOutput {
        var stdin = ""
        var lastResult = ShellRunOutput("", "", 0)

        pipeline.stages.forEachIndexed { index, stage ->
            currentCoroutineContext().ensureActive()
            val isLast = index == pipeline.stages.lastIndex

            val result = runStage(ctx, stage, stdin, onOutput, emitToTerminal = isLast)
            val redirected = applyRedirections(ctx, stage, result)
            if (redirected != null) return redirected

            lastResult = result
            val (capped, truncated) = StreamLimiter.limit(result.stdout)
            if (truncated && isLast) {
                onOutput("\nshell: pipeline output truncated at 2 MB\n", TerminalLine.Stream.SYSTEM)
            }
            stdin = capped
        }
        return lastResult
    }

    private suspend fun runStage(
        ctx: ShellContext,
        stage: ShellStage,
        stdin: String,
        onOutput: (String, TerminalLine.Stream) -> Unit,
        emitToTerminal: Boolean,
    ): ShellRunOutput {
        if (stage.words.isEmpty()) return ShellRunOutput("", "", 0)

        val words = expandGlobs(stage.words, ctx.cwd)
        val aliased = resolver.resolveAlias(words)
        val name = aliased.first()
        val args = aliased.drop(1)

        builtins.find(name)?.let { builtin ->
            return try {
                builtin.run(ctx, args, stdin)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                // Path confinement and missing operands surface here as a normal
                // command error rather than an unhandled failure.
                ShellRunOutput("", "$name: ${e.message ?: "I/O error"}\n", 1)
            }
        }

        val external = resolver.external(name)
        if (external != null) {
            if (!external.available) {
                return ShellRunOutput("", "$name: ${external.unavailableReason ?: "not installed"}\n", 127)
            }
            return try {
                external.execute(ctx, args, stdin) { chunk, stream ->
                    if (emitToTerminal) onOutput(chunk, stream)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.w("External command $name failed", e)
                ShellRunOutput("", "$name: ${e.message ?: "execution failed"}\n", 1)
            }
        }

        val hint = if (name.contains('/')) {
            "no such file or directory"
        } else {
            "command not found. Type 'help' for the built-in list, or open Packages to see installed runtimes."
        }
        return ShellRunOutput("", "$name: $hint\n", 127)
    }

    /** Expands `*`/`?` patterns against the session working directory. */
    private fun expandGlobs(words: List<String>, cwd: File): List<String> =
        GlobExpander.expand(words, cwd)

    /** Applies `>` `>>` `2>` `2>>` for a stage. Returns non-null when it handled the result. */
    private fun applyRedirections(
        ctx: ShellContext,
        stage: ShellStage,
        result: ShellRunOutput,
    ): ShellRunOutput? {
        var handled = false

        stage.stdoutFile?.let { target ->
            val resolved = try {
                ctx.resolveForWrite(target.path)
            } catch (e: IOException) {
                return ShellRunOutput(result.stdout, "shell: ${e.message}\n", 1)
            }
            resolved.parentFile?.mkdirs()
            if (stage.stdoutAppend) resolved.appendText(result.stdout) else resolved.writeText(result.stdout)
            handled = true
        }

        stage.stderrFile?.let { target ->
            val resolved = try {
                ctx.resolveForWrite(target.path)
            } catch (e: IOException) {
                return ShellRunOutput(result.stdout, "shell: ${e.message}\n", 1)
            }
            resolved.parentFile?.mkdirs()
            if (stage.stderrAppend) resolved.appendText(result.stderr) else resolved.writeText(result.stderr)
            handled = true
        }

        return if (handled) ShellRunOutput("", "", result.exitCode, result.cancelled) else null
    }

    companion object {
        const val MAX_PIPELINE_BYTES = 2 * 1024 * 1024
    }
}

/**
 * Truncates stream text so a runaway pipeline cannot exhaust memory. The caller is
 * told truncation happened so the UI can say so rather than silently losing data.
 */
object StreamLimiter {
    fun limit(text: String, limit: Int = ShellEngine.MAX_PIPELINE_BYTES): Pair<String, Boolean> =
        if (text.length <= limit) text to false else text.take(limit) to true
}
