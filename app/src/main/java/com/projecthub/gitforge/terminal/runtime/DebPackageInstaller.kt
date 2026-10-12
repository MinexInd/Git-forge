package com.projecthub.gitforge.terminal.runtime

import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.core.Redaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** A file entry recorded during unpacking so permissions can be applied afterwards. */
private data class UnpackedEntry(val file: File, val mode: Int, val isSymlink: Boolean)

/**
 * Unpacks a Termux `.deb` into the app-private prefix.
 *
 * A `.deb` is an `ar` archive containing `control.tar.*` and `data.tar.*`. This
 * implementation reads both with Apache Commons Compress, which is pure Java and
 * therefore works on Android without native code. The data archive is streamed, so
 * a 10 MB package never needs 10 MB of heap.
 *
 * Security properties that matter here:
 *  * the SHA-256 of the downloaded bytes is verified against the pinned manifest
 *    before a single file is written;
 *  * every entry path is resolved inside the target prefix, so a hostile or
 *    corrupted archive cannot write outside it;
 *  * absolute symlinks are rewritten to point inside the prefix rather than at
 *    `/data/data/com.termux/...`, which is what makes the packages relocatable;
 *  * executable bits from the archive are applied, because the loader refuses to
 *    run a binary that is not marked executable.
 */
class DebPackageInstaller(private val prefix: File) {

    /**
     * Verifies [expectedSha256] against the file at [debFile], then extracts it.
     * Returns the list of files written.
     */
    suspend fun install(debFile: File, expectedSha256: String, onProgress: (String) -> Unit = {}): List<File> =
        withContext(Dispatchers.IO) {
            verifyChecksum(debFile, expectedSha256)
            extract(debFile, onProgress)
        }

    /** Computes the SHA-256 of a file without loading it into memory. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyChecksum(debFile: File, expectedSha256: String) {
        if (expectedSha256.isBlank()) throw IOException("Package has no pinned checksum; refusing to install")
        val actual = sha256(debFile)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw IOException(
                "Checksum mismatch for ${debFile.name}. Expected ${expectedSha256.take(16)}..., got ${actual.take(16)}...",
            )
        }
    }

    private fun extract(debFile: File, onProgress: (String) -> Unit): List<File> {
        val written = mutableListOf<File>()
        val entries = mutableListOf<UnpackedEntry>()
        var dataArchiveSeen = false

        ArArchiveInputStream(BufferedInputStream(debFile.inputStream())).use { ar ->
            while (true) {
                val entry = ar.nextEntry ?: break
                val name = entry.name
                if (!name.startsWith("data.tar")) continue
                dataArchiveSeen = true
                onProgress("Unpacking ${debFile.name}")

                val tarStream = when {
                    name.endsWith(".xz") -> XZCompressorInputStream(ar)
                    name.endsWith(".gz") -> GZIPInputStream(ar)
                    else -> ar
                }
                TarArchiveInputStream(BufferedInputStream(tarStream)).use { tar ->
                    while (true) {
                        val tarEntry = tar.nextEntry ?: break
                        extractEntry(tarEntry, tar, entries)?.let { written += it }
                    }
                }
            }
        }

        if (!dataArchiveSeen) throw IOException("${debFile.name} is not a Debian package (no data.tar member)")

        // Permissions are applied after the stream closes: chmod during streaming
        // would be undone by a later write to the same inode.
        entries.forEach { entry ->
            if (!entry.isSymlink) {
                runCatching { entry.file.setExecutable(entry.mode and 0b001_000_000 != 0, false) }
                runCatching { entry.file.setReadable(true, true) }
                runCatching { entry.file.setWritable(true, true) }
            }
        }
        return written
    }

    private fun extractEntry(
        tarEntry: TarArchiveEntry,
        tar: TarArchiveInputStream,
        entries: MutableList<UnpackedEntry>,
    ): File? {
        val mode = tarEntry.mode
        val isDirectory = tarEntry.isDirectory
        val relative = normalise(tarEntry.name) ?: return null
        val target = File(prefix, relative)

        // Path traversal guard: a crafted name such as ../../x must not escape.
        val canonicalPrefix = prefix.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalPrefix.path + File.separator) &&
            canonicalTarget != canonicalPrefix
        ) {
            AppLog.w("Skipping archive entry that escapes the prefix: ${Redaction.redact(tarEntry.name)}")
            return null
        }

        return when {
            isDirectory -> {
                target.mkdirs()
                null
            }

            mode and 0b111_000_000_000 == 0b101_000_000_000 -> {
                // Symlink: read the link target and relocate it into the prefix.
                val linkTarget = tarEntry.linkName?.takeIf { it.isNotEmpty() }
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                if (linkTarget != null) {
                    val rewritten = relocateLink(linkTarget)
                    try {
                        java.nio.file.Files.createSymbolicLink(target.toPath(), File(rewritten).toPath())
                    } catch (_: Throwable) {
                        // Some Android filesystems refuse symlinks; a copy is an
                        // acceptable fallback for the few links Termux ships.
                        val source = File(rewritten)
                        if (source.exists() && source.isFile) source.copyTo(target, overwrite = true)
                    }
                    entries += UnpackedEntry(target, mode, isSymlink = true)
                }
                target
            }

            else -> {
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> tar.copyTo(output) }
                entries += UnpackedEntry(target, mode, isSymlink = false)
                target
            }
        }
    }

    /**
     * Rewrites an absolute Termux path so it points inside this app's prefix.
     * Without this, `/data/data/com.termux/files/usr/lib/libc++_shared.so` would be
     * a dangling link on any device where Termux is not installed.
     */
    private fun relocateLink(linkTarget: String): String {
        val termuxPrefix = "/data/data/com.termux/files/usr"
        return when {
            linkTarget.startsWith("$termuxPrefix/") ->
                File(prefix, linkTarget.removePrefix("$termuxPrefix/")).absolutePath
            linkTarget == termuxPrefix -> prefix.absolutePath
            else -> linkTarget
        }
    }

    /** Strips the Termux data prefix and rejects absolute or parent-traversing names. */
    private fun normalise(rawName: String): String? {
        var name = rawName.replace('\\', '/')
        if (name.startsWith("/")) return null
        name = name.removePrefix("./")
        val termuxPrefix = "data/data/com.termux/files/usr/"
        if (name.startsWith(termuxPrefix)) name = name.removePrefix(termuxPrefix)
        if (name.isEmpty()) return null
        val segments = name.split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." }) return null
        return segments.joinToString("/")
    }
}
