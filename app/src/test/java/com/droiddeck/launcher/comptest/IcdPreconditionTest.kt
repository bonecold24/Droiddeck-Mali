package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Directive #11 A2: the /opt/comptest/icd.json read-back assertion.
 *
 * Bug history (v3-preview-4 on device): Tier0DriverDirect.install() wrote a
 * correct ICD with library_path=/opt/comptest/libvulkan_panfrost.so, but
 * buildGuestEnv() then overwrote it with the imported driver's ICD whose
 * library_path was an Android-absolute path
 * (/data/user/0/.../linux_vulkan_drivers/...) invisible inside the guest.
 * The compute test passed through the wrong path, leaving the direct-path
 * result unproven.
 *
 * The fix makes Tier0DriverDirect the only writer and asserts the
 * library_path after install and before every stage (FAIL_PRECONDITION on
 * mismatch). These tests cover the pure ICD-parsing logic.
 */
class IcdPreconditionTest {

    private val goodIcd = """
        {
            "file_format_version": "1.0.0",
            "ICD": {
                "library_path": "/opt/comptest/libvulkan_panfrost.so",
                "api_version": "1.4.0"
            }
        }
    """.trimIndent()

    private val badIcdAndroidPath = """
        {
            "file_format_version": "1.0.0",
            "ICD": {
                "library_path": "/data/user/0/com.droiddeck.launcher/files/linux_vulkan_drivers/panvk-kbase-g615-0.1.0-rc3-43d4783f/libvulkan_panfrost.so",
                "api_version": "1.4.0"
            }
        }
    """.trimIndent()

    @Test fun extractsDirectPath() {
        assertEquals(
            "/opt/comptest/libvulkan_panfrost.so",
            CompatTestExecutor.extractIcdLibraryPath(goodIcd)
        )
    }

    @Test fun extractsAndroidAbsolutePath() {
        // The v3-preview-4 bug: this path must NOT match the direct path.
        val actual = CompatTestExecutor.extractIcdLibraryPath(badIcdAndroidPath)
        assertEquals(
            "/data/user/0/com.droiddeck.launcher/files/linux_vulkan_drivers/" +
                "panvk-kbase-g615-0.1.0-rc3-43d4783f/libvulkan_panfrost.so",
            actual
        )
    }

    @Test fun androidPathDoesNotEqualDirectPath() {
        val actual = CompatTestExecutor.extractIcdLibraryPath(badIcdAndroidPath)
        // This is the assertion verifyDirectIcd makes: mismatch -> FAIL_PRECONDITION.
        assertTrueMismatch(actual, Tier0DriverDirect.GUEST_SO)
    }

    @Test fun directPathMatchesExpected() {
        val actual = CompatTestExecutor.extractIcdLibraryPath(goodIcd)
        assertEquals(Tier0DriverDirect.GUEST_SO, actual)
    }

    @Test fun invalidJsonYieldsEmpty() {
        assertEquals("", CompatTestExecutor.extractIcdLibraryPath("not json"))
    }

    @Test fun missingIcdObjectYieldsEmpty() {
        assertEquals("", CompatTestExecutor.extractIcdLibraryPath("""{"foo": 1}"""))
    }

    @Test fun missingLibraryPathYieldsEmpty() {
        assertEquals(
            "",
            CompatTestExecutor.extractIcdLibraryPath("""{"ICD": {"api_version": "1.4.0"}}""")
        )
    }

    private fun assertTrueMismatch(actual: String, expected: String) {
        if (actual == expected) {
            throw AssertionError("expected mismatch but both were '$actual'")
        }
    }
}
