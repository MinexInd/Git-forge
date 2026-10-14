package com.projecthub.gitforge.update

/**
 * Compares dotted version strings and release tags.
 *
 * The updater must decide whether a release is newer than the installed build, and
 * doing that with string comparison gets `1.10.0` vs `1.9.0` wrong. This parses the
 * numeric components and compares them in order, so no version ordering decision
 * depends on string ordering.
 */
object VersionComparator {

    /** Strips a leading `v`/`V` and any build metadata, then splits on `.`, `-`, `+`. */
    fun parse(raw: String): List<Int> {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return emptyList()
        val head = trimmed.substringBefore('+').substringBefore('-')
        return head.split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    /** Negative when [a] is older than [b], zero when equal, positive when newer. */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l - r
        }
        return 0
    }

    /** True when [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
