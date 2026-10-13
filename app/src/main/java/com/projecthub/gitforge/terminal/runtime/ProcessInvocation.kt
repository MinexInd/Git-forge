package com.projecthub.gitforge.terminal.runtime

import java.io.File

/**
 * Builds the command line used to start an executable that lives inside the app's
 * private data directory.
 *
 * Android 10 (API 29) introduced a W^X rule: an app whose `targetSdkVersion` is 29 or
 * later may not `execve()` a file under its own data directory. GitForge targets 34,
 * so a userspace binary unpacked into app storage fails to start directly and every
 * command looks broken. The supported workaround is to pass the binary to the system
 * linker, which the platform policy does permit to run app-data files:
 *
 * ```
 * /system/bin/linker64 /data/data/<pkg>/files/runtime/usr/bin/node --version
 * ```
 *
 * The logic is separated from [NativeProcess] so it can be tested on the JVM without
 * an Android device.
 */
object ProcessInvocation {

    /** The linker for 64-bit processes. Present on every 64-bit Android. */
    const val LINKER_64 = "/system/bin/linker64"

    /** The linker for 32-bit processes. Present on 32-bit Android builds. */
    const val LINKER_32 = "/system/bin/linker"

    /**
     * The full command line, including the executable as the first element.
     *
     * @param sdkInt the running Android API level.
     * @param linkerFor resolves a linker path to a file, or null when it is absent.
     *   Injectable so tests do not depend on the host filesystem.
     */
    fun command(
        executable: File,
        args: List<String>,
        sdkInt: Int,
        linkerFor: (String) -> File? = { path -> File(path).takeIf { it.exists() } },
    ): List<String> {
        val direct = listOf(executable.absolutePath) + args
        if (sdkInt < 29) return direct

        val linker = linkerFor(linkerPathFor(executable, linkerFor)) ?: return direct
        return listOf(linker.absolutePath, executable.absolutePath) + args
    }

    private fun linkerPathFor(executable: File, linkerFor: (String) -> File?): String {
        val is64Bit = elfIs64Bit(executable)
        return when (is64Bit) {
            true -> LINKER_64
            false -> LINKER_32
            // Not an ELF file, or unreadable: prefer 64-bit, which is the common case
            // on modern devices, and fall back to 32-bit if that linker is missing.
            null -> if (linkerFor(LINKER_64) != null) LINKER_64 else LINKER_32
        }
    }

    /**
     * Reads the ELF class byte (offset 4: 1 = 32-bit, 2 = 64-bit). Returns null when
     * the file is missing, unreadable or not an ELF object.
     */
    fun elfIs64Bit(file: File): Boolean? = runCatching {
        file.inputStream().use { stream ->
            val header = ByteArray(5)
            if (stream.read(header) < 5) return@use null
            if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
                header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
            ) {
                return@use null
            }
            when (header[4].toInt()) {
                1 -> false
                2 -> true
                else -> null
            }
        }
    }.getOrNull()
}
