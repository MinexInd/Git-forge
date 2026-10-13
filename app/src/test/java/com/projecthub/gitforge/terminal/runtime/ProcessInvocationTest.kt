package com.projecthub.gitforge.terminal.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The W^X workaround is the difference between every terminal command working and
 * every terminal command failing on Android 10+, so it is pinned down here.
 */
class ProcessInvocationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val linker64 = File("/system/bin/linker64")
    private val linker32 = File("/system/bin/linker")

    /** Pretends both system linkers exist, whatever the host has. */
    private val linkersPresent: (String) -> File? = { path ->
        when (path) {
            ProcessInvocation.LINKER_64 -> linker64
            ProcessInvocation.LINKER_32 -> linker32
            else -> null
        }
    }

    private fun elf(is64Bit: Boolean): File {
        val file = temp.newFile()
        val classByte: Byte = if (is64Bit) 2 else 1
        file.writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), classByte))
        return file
    }

    @Test
    fun `api 28 executes the binary directly`() {
        val binary = elf(is64Bit = true)
        val command = ProcessInvocation.command(binary, listOf("--version"), sdkInt = 28, linkerFor = linkersPresent)

        assertEquals(listOf(binary.absolutePath, "--version"), command)
    }

    @Test
    fun `api 29 routes a 64-bit binary through the 64-bit linker`() {
        val binary = elf(is64Bit = true)
        val command = ProcessInvocation.command(binary, listOf("--version"), sdkInt = 29, linkerFor = linkersPresent)

        assertEquals(listOf("/system/bin/linker64", binary.absolutePath, "--version"), command)
    }

    @Test
    fun `a 32-bit binary uses the 32-bit linker`() {
        val binary = elf(is64Bit = false)
        val command = ProcessInvocation.command(binary, emptyList(), sdkInt = 33, linkerFor = linkersPresent)

        assertEquals(listOf("/system/bin/linker", binary.absolutePath), command)
    }

    @Test
    fun `when the linker is missing the binary is still started directly`() {
        val binary = elf(is64Bit = true)
        val command = ProcessInvocation.command(binary, listOf("x"), sdkInt = 33, linkerFor = { null })

        assertEquals(listOf(binary.absolutePath, "x"), command)
    }

    @Test
    fun `a non-elf file falls back to the 64-bit linker`() {
        val file = temp.newFile().apply { writeText("#!/bin/sh\necho hi\n") }
        val command = ProcessInvocation.command(file, emptyList(), sdkInt = 33, linkerFor = linkersPresent)

        assertEquals(listOf("/system/bin/linker64", file.absolutePath), command)
    }

    @Test
    fun `elf class is read from the header`() {
        assertTrue(ProcessInvocation.elfIs64Bit(elf(is64Bit = true)) == true)
        assertFalse(ProcessInvocation.elfIs64Bit(elf(is64Bit = false)) == true)
    }

    @Test
    fun `a truncated or missing file has no elf class`() {
        val tiny = temp.newFile().apply { writeBytes(byteArrayOf(0x7F)) }
        assertNull(ProcessInvocation.elfIs64Bit(tiny))
        assertNull(ProcessInvocation.elfIs64Bit(File(temp.root, "does-not-exist")))
    }
}
