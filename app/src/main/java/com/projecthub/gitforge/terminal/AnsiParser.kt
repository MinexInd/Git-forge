package com.projecthub.gitforge.terminal

/**
 * Minimal ANSI escape-sequence processor.
 *
 * Terminals in this app render to a Compose text list rather than to a real
 * character grid, so the parser only needs to do two things reliably:
 *  1. remove cursor/erase control sequences that have no meaning in a line list;
 *  2. translate SGR colour/attribute sequences into [AnsiStyle].
 *
 * Carriage-return based progress bars (npm and pip both use them) are collapsed to
 * their final state so the scrollback stays readable instead of filling with
 * hundreds of overwritten frames.
 */
class AnsiParser {

    private var current = AnsiStyle.NONE

    /** Style that was active when the carry's first character was written. */
    private var carryStyle = AnsiStyle.NONE

    /** Feeds a raw chunk and returns the completed lines plus any partial tail. */
    fun parse(chunk: String, carry: String): ParseOutcome {
        val buffer = StringBuilder(carry)
        val lines = mutableListOf<Pair<String, AnsiStyle>>()

        // Style in effect when the current line's first character was written.
        var lineStyle = if (buffer.isEmpty()) current else carryStyle
        var index = 0
        while (index < chunk.length) {
            val char = chunk[index]
            when {
                char == '\u001B' && index + 1 < chunk.length && chunk[index + 1] == '[' -> {
                    val end = findSequenceEnd(chunk, index + 2)
                    if (end == -1) {
                        // Sequence split across chunks; keep it for the next call.
                        buffer.append(chunk.substring(index))
                        index = chunk.length
                    } else {
                        applySgr(chunk.substring(index + 2, end))
                        index = end + 1
                    }
                }

                char == '\r' -> {
                    // Overwrite the current line: drop everything buffered so far.
                    val lastNewline = buffer.lastIndexOf("\n")
                    if (lastNewline >= 0) {
                        buffer.delete(lastNewline + 1, buffer.length)
                    } else {
                        buffer.setLength(0)
                    }
                    index++
                }

                char == '\n' -> {
                    lines += buffer.toString() to lineStyle
                    buffer.setLength(0)
                    index++
                }

                char == '\u0007' || char == '\u0008' -> index++

                else -> {
                    if (buffer.isEmpty()) lineStyle = current
                    buffer.append(char)
                    index++
                }
            }
        }

        carryStyle = if (buffer.isEmpty()) current else lineStyle
        return ParseOutcome(lines = lines, carry = buffer.toString(), style = current)
    }

    /** Strips every escape sequence from a string without styling it. */
    fun strip(input: String): String {
        val outcome = parse(input, "")
        return (outcome.lines.map { it.first } + outcome.carry).joinToString("\n")
    }

    private fun findSequenceEnd(chunk: String, from: Int): Int {
        var i = from
        while (i < chunk.length) {
            val c = chunk[i]
            if (c in '@'..'~') return i
            i++
        }
        return -1
    }

    private fun applySgr(parameters: String) {
        if (parameters.isEmpty()) {
            current = AnsiStyle.NONE
            return
        }
        val codes = parameters.split(';').mapNotNull { it.toIntOrNull() }
        if (codes.isEmpty()) return

        var style = current
        var i = 0
        while (i < codes.size) {
            when (val code = codes[i]) {
                0 -> style = AnsiStyle.NONE
                1 -> style = style.copy(bold = true)
                2 -> style = style.copy(dim = true)
                3 -> style = style.copy(italic = true)
                4 -> style = style.copy(underline = true)
                22 -> style = style.copy(bold = false, dim = false)
                23 -> style = style.copy(italic = false)
                24 -> style = style.copy(underline = false)
                39 -> style = style.copy(fg = null)
                49 -> style = style.copy(bg = null)
                in 30..37 -> style = style.copy(fg = basicColor(code - 30, bright = false))
                in 90..97 -> style = style.copy(fg = basicColor(code - 90, bright = true))
                in 40..47 -> style = style.copy(bg = basicColor(code - 40, bright = false))
                in 100..107 -> style = style.copy(bg = basicColor(code - 100, bright = true))
                38, 48 -> {
                    // 256-colour / truecolour: consume the following parameters and
                    // map to the nearest basic colour so the output still reads well.
                    val isForeground = code == 38
                    val mode = codes.getOrNull(i + 1)
                    when (mode) {
                        5 -> {
                            val index = codes.getOrNull(i + 2) ?: 0
                            val color = color256(index)
                            style = if (isForeground) style.copy(fg = color) else style.copy(bg = color)
                            i += 2
                        }
                        2 -> {
                            val r = codes.getOrNull(i + 2) ?: 0
                            val g = codes.getOrNull(i + 3) ?: 0
                            val b = codes.getOrNull(i + 4) ?: 0
                            val color = nearestBasic(r, g, b)
                            style = if (isForeground) style.copy(fg = color) else style.copy(bg = color)
                            i += 4
                        }
                    }
                }
            }
            i++
        }
        current = style
    }

    private fun basicColor(index: Int, bright: Boolean): AnsiColor {
        val base = when (index) {
            0 -> AnsiColor.BLACK
            1 -> AnsiColor.RED
            2 -> AnsiColor.GREEN
            3 -> AnsiColor.YELLOW
            4 -> AnsiColor.BLUE
            5 -> AnsiColor.MAGENTA
            6 -> AnsiColor.CYAN
            else -> AnsiColor.WHITE
        }
        if (!bright) return base
        return AnsiColor.entries[base.ordinal + 8]
    }

    private fun color256(index: Int): AnsiColor = when {
        index < 8 -> basicColor(index, false)
        index < 16 -> basicColor(index - 8, true)
        index >= 232 -> if (index > 243) AnsiColor.WHITE else AnsiColor.BRIGHT_BLACK
        else -> {
            val i = index - 16
            val r = (i / 36) * 51
            val g = ((i % 36) / 6) * 51
            val b = (i % 6) * 51
            nearestBasic(r, g, b)
        }
    }

    private fun nearestBasic(r: Int, g: Int, b: Int): AnsiColor {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val bright = max > 190
        if (max - min < 30) {
            return if (max < 60) AnsiColor.BLACK else if (bright) AnsiColor.BRIGHT_WHITE else AnsiColor.WHITE
        }
        val base = when (max) {
            r -> if (g > b) AnsiColor.YELLOW else AnsiColor.MAGENTA
            g -> if (b > r) AnsiColor.CYAN else AnsiColor.GREEN
            else -> if (r > g) AnsiColor.MAGENTA else AnsiColor.BLUE
        }
        return if (bright) AnsiColor.entries[base.ordinal + 8] else base
    }
}

data class ParseOutcome(
    val lines: List<Pair<String, AnsiStyle>>,
    val carry: String,
    val style: AnsiStyle,
)
