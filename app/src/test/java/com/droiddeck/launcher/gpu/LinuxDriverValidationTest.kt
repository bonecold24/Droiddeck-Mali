package com.droiddeck.launcher.gpu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Host-runnable tests for [LinuxDriverValidation]. */
class LinuxDriverValidationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Minimal AArch64 ELF header + optional ASCII payload (e.g. DT_NEEDED strings). */
    private fun elfFile(payload: String = ""): File {
        val h = ByteArray(64)
        h[0] = 0x7f; h[1] = 'E'.code.toByte(); h[2] = 'L'.code.toByte(); h[3] = 'F'.code.toByte()
        h[4] = 2; h[5] = 1 // 64-bit, little-endian
        h[18] = 0xB7.toByte(); h[19] = 0x00 // EM_AARCH64
        val f = tmp.newFile()
        f.writeBytes(h + payload.toByteArray(Charsets.US_ASCII))
        return f
    }

    /** A fake installed driver dir: library + icd.json (+ optional meta.json). */
    private fun driverDir(
        libName: String = "libvulkan_panfrost.so",
        libPayload: String = "NEEDED libc.so.6",
        icd: String? = null,
    ): File {
        val dir = tmp.newFolder()
        elfFile(libPayload).copyTo(File(dir, libName))
        val libPath = File(dir, libName).absolutePath
        val icdJson = icd ?: """{"file_format_version":"1.0.0","ICD":{"library_path":${org.json.JSONObject.quote(libPath)},"api_version":"1.1.274"}}"""
        File(dir, "icd.json").writeText(icdJson)
        return dir
    }

    @Test
    fun validGlibcDriverPasses() {
        val dir = driverDir()
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertTrue("expected valid, got: ${r.reason}", r.valid)
    }

    @Test
    fun bionicDriverRejected() {
        // v2 state: AArch64 ELF but bionic libc — no libc.so.6.
        val dir = driverDir(libPayload = "NEEDED libc.so\u0000NEEDED liblog.so")
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertFalse(r.valid)
        assertTrue(r.reason.contains("glibc"))
    }

    @Test
    fun nonElfLibraryRejected() {
        val dir = tmp.newFolder()
        File(dir, "libvulkan_panfrost.so").writeText("not an elf")
        File(dir, "icd.json").writeText("{}")
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertFalse(r.valid)
    }

    @Test
    fun missingDriverDirRejected() {
        val r = LinuxDriverValidation.validateDriverDir(File(tmp.root, "nope"), "libvulkan_panfrost.so")
        assertFalse(r.valid)
    }

    @Test
    fun missingLibraryRejected() {
        val dir = tmp.newFolder()
        File(dir, "icd.json").writeText("{}")
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertFalse(r.valid)
    }

    @Test
    fun missingIcdRejected() {
        val dir = tmp.newFolder()
        elfFile("NEEDED libc.so.6").copyTo(File(dir, "libvulkan_panfrost.so"))
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertFalse(r.valid)
        assertTrue(r.reason.contains("icd.json"))
    }

    @Test
    fun corruptIcdJsonRejected() {
        val dir = driverDir(icd = "{ not json")
        val r = LinuxDriverValidation.validateDriverDir(dir, "libvulkan_panfrost.so")
        assertFalse(r.valid)
    }

    @Test
    fun icdWithoutLibraryPathRejected() {
        val dir = driverDir(icd = """{"file_format_version":"1.0.0","ICD":{"api_version":"1.1.274"}}""")
        val r = LinuxDriverValidation.validateIcdJson(File(dir, "icd.json"))
        assertFalse(r.valid)
        assertTrue(r.reason.contains("library_path"))
    }

    @Test
    fun icdWithoutApiVersionRejected() {
        val libPath = File(driverDir(), "libvulkan_panfrost.so").absolutePath
        val dir = tmp.newFolder()
        File(dir, "icd.json").writeText(
            """{"file_format_version":"1.0.0","ICD":{"library_path":${org.json.JSONObject.quote(libPath)}}}""",
        )
        val r = LinuxDriverValidation.validateIcdJson(File(dir, "icd.json"))
        assertFalse(r.valid)
        assertTrue(r.reason.contains("api_version"))
    }

    @Test
    fun icdWithDanglingLibraryPathRejected() {
        val dir = tmp.newFolder()
        File(dir, "icd.json").writeText(
            """{"file_format_version":"1.0.0","ICD":{"library_path":"/nonexistent/libvulkan_panfrost.so","api_version":"1.1.274"}}""",
        )
        val r = LinuxDriverValidation.validateIcdJson(File(dir, "icd.json"))
        assertFalse(r.valid)
    }

    @Test
    fun icdWithoutIcdObjectRejected() {
        val dir = tmp.newFolder()
        File(dir, "icd.json").writeText("""{"file_format_version":"1.0.0"}""")
        val r = LinuxDriverValidation.validateIcdJson(File(dir, "icd.json"))
        assertFalse(r.valid)
    }
}
