package com.projecthub.gitforge.terminal.shell

import java.io.File
import java.io.IOException

/** A token produced by [ShellLexer]. */
sealed interface Token {
    data class Word(val value: String, val quoted: Boolean) : Token
    data class Operator(val symbol: String) : Token
}

/** One stage of a pipeline together with its redirections. */
data class ShellStage(
    val words: List<String>,
    val stdoutFile: File? = null,
    val stdoutAppend: Boolean = false,
    val stderrFile: File? = null,
    val stderrAppend: Boolean = false,
    val stdinFile: File? = null,
) {
    val command: String get() = words.firstOrNull().orEmpty()
    val arguments: List<String> get() = words.drop(1)
}

/** A full command line: one or more stages joined by pipes. */
data class ShellPipeline(
    val stages: List<ShellStage>,
    val raw: String,
    val background: Boolean = false,
)

/** Raised when a command line cannot be parsed; reported as a shell error, not a crash. */
class ShellSyntaxException(message: String) : IOException(message)

/**
 * Tokenises a command line.
 *
 * Supports the quoting and expansion rules a developer actually relies on:
 *  * `'single'` quotes are literal;
 *  * `"double"` quotes expand `$VAR`, `${VAR}`, `$?`, `$PWD`, `$HOME`;
 *  * backslash escapes the next character;
 *  * `|`, `>`, `>>`, `2>`, `2>>`, `<`, `&` are recognised only when unquoted.
 */
object ShellLexer {

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val word = StringBuilder()
        var wordQuoted = false
        var i = 0

        fun flush() {
            if (word.isNotEmpty() || wordQuoted) {
                tokens += Token.Word(word.toString(), wordQuoted)
                word.setLength(0)
                wordQuoted = false
            }
        }

        while (i < input.length) {
            val c = input[i]
            when {
                c == '\\' && i + 1 < input.length -> {
                    word.append(input[i + 1])
                    i += 2
                }

                c == '\'' -> {
                    val end = input.indexOf('\'', i + 1)
                    if (end == -1) throw ShellSyntaxException("Unterminated single quote")
                    word.append(input, i + 1, end)
                    wordQuoted = true
                    i = end + 1
                }

                c == '"' -> {
                    val sb = StringBuilder()
                    var j = i + 1
                    var closed = false
                    while (j < input.length) {
                        if (input[j] == '\\' && j + 1 < input.length) {
                            sb.append(input[j + 1]); j += 2; continue
                        }
                        if (input[j] == '"') { closed = true; break }
                        sb.append(input[j]); j++
                    }
                    if (!closed) throw ShellSyntaxException("Unterminated double quote")
                    word.append(sb)
                    wordQuoted = true
                    i = j + 1
                }

                c == '|' -> { flush(); tokens += Token.Operator("|"); i++ }

                c == '>' -> {
                    flush()
                    if (i + 1 < input.length && input[i + 1] == '>') {
                        tokens += Token.Operator(">>"); i += 2
                    } else {
                        tokens += Token.Operator(">"); i++
                    }
                }

                c == '<' -> { flush(); tokens += Token.Operator("<"); i++ }

                c == '&' -> {
                    flush()
                    if (i + 1 < input.length && input[i + 1] == '&') {
                        tokens += Token.Operator("&&"); i += 2
                    } else {
                        tokens += Token.Operator("&"); i++
                    }
                }

                c == '2' && i + 1 < input.length && input[i + 1] == '>' -> {
                    flush()
                    if (i + 2 < input.length && input[i + 2] == '>') {
                        tokens += Token.Operator("2>>"); i += 3
                    } else {
                        tokens += Token.Operator("2>"); i += 2
                    }
                }

                c.isWhitespace() -> { flush(); i++ }

                else -> { word.append(c); i++ }
            }
        }
        flush()
        return tokens
    }

    /**
     * Expands variables and `~` in a word. Expansion happens after tokenising so a
     * variable containing a space cannot inject an extra argument.
     */
    fun expand(word: Token.Word, env: Map<String, String>, lastExitCode: Int, home: String): String {
        if (!word.quoted && word.value == "~") return home
        if (!word.quoted && word.value.startsWith("~/")) {
            return home + word.value.removePrefix("~")
        }

        val sb = StringBuilder()
        var i = 0
        val value = word.value
        while (i < value.length) {
            val c = value[i]
            if (c == '$') {
                if (i + 1 < value.length && value[i + 1] == '{') {
                    val end = value.indexOf('}', i + 2)
                    if (end != -1) {
                        val name = value.substring(i + 2, end)
                        sb.append(lookup(name, env, lastExitCode, home))
                        i = end + 1
                        continue
                    }
                }
                if (i + 1 < value.length && value[i + 1] == '?') {
                    sb.append(lastExitCode); i += 2; continue
                }
                val match = Regex("""[A-Za-z_][A-Za-z0-9_]*""").find(value, i + 1)
                if (match != null && match.range.first == i + 1) {
                    sb.append(lookup(match.value, env, lastExitCode, home))
                    i = match.range.last + 1
                    continue
                }
                sb.append('$'); i++
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun lookup(name: String, env: Map<String, String>, lastExitCode: Int, home: String): String =
        when (name) {
            "?" -> lastExitCode.toString()
            "HOME" -> env["HOME"] ?: home
            else -> env[name].orEmpty()
        }

    /** Splits tokens into a pipeline, attaching redirections to their stage. */
    fun toPipeline(tokens: List<Token>, raw: String, resolvePath: (String) -> File): ShellPipeline {
        if (tokens.isEmpty()) throw ShellSyntaxException("Empty command")

        val stages = mutableListOf<ShellStage>()
        var currentWords = mutableListOf<String>()
        var stdoutFile: File? = null
        var stdoutAppend = false
        var stderrFile: File? = null
        var stderrAppend = false
        var stdinFile: File? = null
        var background = false

        fun finishStage() {
            if (currentWords.isNotEmpty()) {
                stages += ShellStage(
                    words = currentWords,
                    stdoutFile = stdoutFile,
                    stdoutAppend = stdoutAppend,
                    stderrFile = stderrFile,
                    stderrAppend = stderrAppend,
                    stdinFile = stdinFile,
                )
            }
            currentWords = mutableListOf()
            stdoutFile = null
            stdoutAppend = false
            stderrFile = null
            stderrAppend = false
            stdinFile = null
        }

        var i = 0
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Word -> {
                    currentWords += token.value
                    i++
                }

                is Token.Operator -> when (token.symbol) {
                    "|" -> { finishStage(); i++ }
                    "&" -> { background = true; i++ }
                    "&&" -> throw ShellSyntaxException("'&&' chaining is not supported; run the commands separately")
                    ">", ">>" -> {
                        val target = tokens.getOrNull(i + 1) as? Token.Word
                            ?: throw ShellSyntaxException("Expected a file name after '${token.symbol}'")
                        stdoutFile = resolvePath(target.value)
                        stdoutAppend = token.symbol == ">>"
                        i += 2
                    }
                    "2>", "2>>" -> {
                        val target = tokens.getOrNull(i + 1) as? Token.Word
                            ?: throw ShellSyntaxException("Expected a file name after '${token.symbol}'")
                        stderrFile = resolvePath(target.value)
                        stderrAppend = token.symbol == "2>>"
                        i += 2
                    }
                    "<" -> {
                        val target = tokens.getOrNull(i + 1) as? Token.Word
                            ?: throw ShellSyntaxException("Expected a file name after '<'")
                        stdinFile = resolvePath(target.value)
                        i += 2
                    }
                    else -> throw ShellSyntaxException("Unsupported operator '${token.symbol}'")
                }
            }
        }
        finishStage()

        if (stages.isEmpty()) throw ShellSyntaxException("Empty command")
        return ShellPipeline(stages = stages, raw = raw, background = background)
    }
}
