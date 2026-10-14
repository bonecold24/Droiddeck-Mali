package com.droiddeck.launcher.gpu

import java.io.File
import java.io.FileInputStream

/**
 * ELF / libc inspection for imported driver libraries. Pure JVM (no Android
 * dependencies) so host unit tests cover it; the Android managers delegate here.
 */
object ElfInspect {

    /** True for a 64-bit little-endian AArch64 ELF object. */
    @JvmStatic
    fun isAarch64Elf(f: File): Boolean {
        try {
            FileInputStream(f).use { ins ->
                val h = ByteArray(20)
                var n = 0
                while (n < h.size) {
                    val r = ins.read(h, n, h.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n < 20) return false
                if (h[0] != 0x7f.toByte() || h[1] != 'E'.code.toByte() ||
                    h[2] != 'L'.code.toByte() || h[3] != 'F'.code.toByte()
                ) return false
                if (h[4] != 2.toByte() || h[5] != 1.toByte()) return false // 64-bit LE
                val machine = (h[18].toInt() and 0xff) or ((h[19].toInt() and 0xff) shl 8)
                return machine == 0xB7 // EM_AARCH64
            }
        } catch (e: Exception) {
            return false
        }
    }

    /** True when the driver's linked libc is glibc (a Linux-userland driver). */
    @JvmStatic
    fun linksGlibc(f: File): Boolean = containsAscii(f, "libc.so.6")

    /**
     * Byte search that survives the needle straddling read-buffer boundaries
     * (keeps a [needle]-sized carry between reads).
     */
    @JvmStatic
    fun containsAscii(f: File, needle: String): Boolean {
        val nb = needle.toByteArray(Charsets.US_ASCII)
        try {
            f.inputStream().use { ins ->
                val buf = ByteArray(1 shl 16)
                var carry = 0
                while (true) {
                    val r = ins.read(buf, carry, buf.size - carry)
                    if (r <= 0) break
                    val len = carry + r
                    for (i in 0..len - nb.size) {
                        var j = 0
                        while (j < nb.size && buf[i + j] == nb[j]) j++
                        if (j == nb.size) return true
                    }
                    carry = minOf(nb.size - 1, len)
                    System.arraycopy(buf, len - carry, buf, 0, carry)
                }
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }
}
