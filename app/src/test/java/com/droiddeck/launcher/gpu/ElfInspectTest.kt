package com.droiddeck.launcher.gpu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Host-runnable tests for [ElfInspect]. */
class ElfInspectTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun elf(machine: Int = 0xB7, extra: String = ""): File {
        val h = ByteArray(64)
        h[0] = 0x7f; h[1] = 'E'.code.toByte(); h[2] = 'L'.code.toByte(); h[3] = 'F'.code.toByte()
        h[4] = 2; h[5] = 1
        h[18] = (machine and 0xff).toByte(); h[19] = ((machine shr 8) and 0xff).toByte()
        val f = tmp.newFile()
        f.writeBytes(h + extra.toByteArray())
        return f
    }

    @Test
    fun detectsAarch64Elf() {
        assertTrue(ElfInspect.isAarch64Elf(elf()))
    }

    @Test
    fun rejectsWrongMachine() {
        assertFalse(ElfInspect.isAarch64Elf(elf(machine = 0x28))) // EM_AARCH64=0xB7, 0x28 is ARM
    }

    @Test
    fun rejectsNonElf() {
        val f = tmp.newFile()
        f.writeText("definitely not an elf")
        assertFalse(ElfInspect.isAarch64Elf(f))
    }

    @Test
    fun rejectsMissingFile() {
        assertFalse(ElfInspect.isAarch64Elf(File(tmp.root, "nope.so")))
    }

    @Test
    fun findsGlibcMarker() {
        assertTrue(ElfInspect.linksGlibc(elf(extra = "NEEDED libc.so.6")))
        assertFalse(ElfInspect.linksGlibc(elf(extra = "NEEDED libc.so")))
    }

    @Test
    fun containsAsciiSpansBufferBoundaries() {
        // A 40-char needle placed at the very end of a small file covers the carry path.
        val needle = "X".repeat(40)
        val f = tmp.newFile()
        f.writeBytes(ByteArray(100) { 'A'.code.toByte() } + needle.toByteArray())
        assertTrue(ElfInspect.containsAscii(f, needle))
        assertFalse(ElfInspect.containsAscii(f, "Y".repeat(40)))
    }
}
