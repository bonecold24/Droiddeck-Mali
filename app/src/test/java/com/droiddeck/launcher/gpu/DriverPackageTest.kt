package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Host-runnable tests for [DriverPackage] parsing and SHA-256 verification.
 *
 * The fixtures are synthetic packages built in a temp dir that mirror the real
 * panvk-kbase-g720 package layout (libvulkan_panfrost.so + panvk_kbase_icd.aarch64.json +
 * meta.json + MANIFEST.json + SHA256SUMS.txt + SOURCE.json + BUILD-PROVENANCE.txt).
 * The real 24 MB package is verified separately via -Dmali.driver.package (see bottom).
 */
class DriverPackageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Minimal AArch64 ELF header so fixtures also pass the ELF sanity check. */
    private fun fakeAarch64So(extra: ByteArray = "fake-driver".toByteArray()): ByteArray {
        val h = ByteArray(64)
        h[0] = 0x7f; h[1] = 'E'.code.toByte(); h[2] = 'L'.code.toByte(); h[3] = 'F'.code.toByte()
        h[4] = 2; h[5] = 1 // 64-bit, little-endian
        h[18] = 0xB7.toByte(); h[19] = 0 // EM_AARCH64
        return h + extra
    }

    private fun writePackage(
        dir: File,
        soBytes: ByteArray = fakeAarch64So(),
        abi: String = "android-aarch64-bionic",
        packageVersion: String = "0.1.0-rc3-universal+local1",
    ): File {
        dir.mkdirs()
        File(dir, "libvulkan_panfrost.so").writeBytes(soBytes)
        val soSha = sha256Hex(soBytes)
        val icd = """{
    "ICD": {
        "api_version": "1.4.363",
        "library_arch": "64",
        "library_path": "./libvulkan_panfrost.so"
    },
    "file_format_version": "1.0.1"
}"""
        File(dir, "panvk_kbase_icd.aarch64.json").writeText(icd)
        val meta = """{
  "schemaVersion": 1,
  "name": "PanVK Kbase (mission local build)",
  "libraryName": "libvulkan_panfrost.so",
  "abi": "$abi",
  "backend": "panvk-kbase",
  "profile": "g720-v12-csf",
  "packageVersion": "$packageVersion",
  "minApi": 33,
  "validation": "UNVALIDATED - no Mali hardware run"
}"""
        File(dir, "meta.json").writeText(meta)
        val manifest = """{
  "profile": "g720-v12-csf",
  "abi": "$abi",
  "binarySha256": "$soSha",
  "files": [
    "libvulkan_panfrost.so",
    "panvk_kbase_icd.aarch64.json",
    "meta.json",
    "MANIFEST.json",
    "SOURCE.json",
    "SHA256SUMS.txt",
    "BUILD-PROVENANCE.txt"
  ]
}"""
        File(dir, "MANIFEST.json").writeText(manifest)
        File(dir, "SHA256SUMS.txt").writeText("$soSha  libvulkan_panfrost.so\n")
        File(dir, "SOURCE.json").writeText("""{"sourceRepo": "zenithblue-oss/panvk-kbase-android"}""")
        File(dir, "BUILD-PROVENANCE.txt").writeText("PanVK kbase test build\nBuilt for tests, not a device run.\n")
        return dir
    }

    @Test
    fun goodPackageParses() {
        val pkg = DriverPackage.parse(writePackage(tmp.newFolder()))
        assertEquals("PanVK Kbase (mission local build)", pkg.name)
        assertEquals("0.1.0-rc3-universal+local1", pkg.packageVersion)
        assertEquals("android-aarch64-bionic", pkg.abi)
        assertEquals("panvk-kbase", pkg.backend)
        assertEquals("g720-v12-csf", pkg.profile)
        assertEquals("libvulkan_panfrost.so", pkg.libraryName)
        assertEquals("1.4.363", pkg.apiVersion)
        assertTrue(pkg.binarySha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(8, pkg.shaPrefix.length)
        assertTrue(pkg.provenance.contains("PanVK kbase test build"))
        assertTrue(pkg.isBionic)
    }

    @Test
    fun goodPackageVerifies() {
        val result = DriverPackage.verify(writePackage(tmp.newFolder()))
        assertEquals("0.1.0-rc3-universal+local1", result.pkg.packageVersion)
        // The fixture's SHA256SUMS.txt only covers the .so, so the other manifest files
        // are reported as uncovered warnings, not failures.
        assertTrue(result.warnings.any { it.contains("SHA256SUMS") || it.contains("checksum") })
    }

    @Test
    fun tamperedLibraryFailsBothHashChecks() {
        val dir = writePackage(tmp.newFolder())
        File(dir, "libvulkan_panfrost.so").appendBytes("tampered".toByteArray())
        try {
            DriverPackage.verify(dir)
            fail("tampered .so must not verify")
        } catch (e: DriverPackageException) {
            assertTrue("message names the .so: ${e.message}", e.message!!.contains("libvulkan_panfrost.so"))
        }
    }

    @Test
    fun tamperedManifestBinaryShaFails() {
        val dir = writePackage(tmp.newFolder())
        val manifest = File(dir, "MANIFEST.json").readText()
        val tampered = manifest.replace(Regex("\"binarySha256\": \"[0-9a-f]{64}\""), "\"binarySha256\": \"${"0".repeat(64)}\"")
        File(dir, "MANIFEST.json").writeText(tampered)
        try {
            DriverPackage.verify(dir)
            fail("manifest with wrong binarySha256 must not verify")
        } catch (e: DriverPackageException) {
            assertTrue("message names binarySha256: ${e.message}", e.message!!.contains("binarySha256"))
        }
    }

    @Test
    fun tamperedSha256SumsEntryFails() {
        val dir = writePackage(tmp.newFolder())
        File(dir, "SHA256SUMS.txt").writeText("${"f".repeat(64)}  libvulkan_panfrost.so\n")
        try {
            DriverPackage.verify(dir)
            fail("wrong SHA256SUMS entry must not verify")
        } catch (e: DriverPackageException) {
            assertTrue("message names SHA256SUMS: ${e.message}", e.message!!.contains("SHA256SUMS"))
        }
    }

    @Test
    fun missingManifestFileFails() {
        val dir = writePackage(tmp.newFolder())
        assertTrue(File(dir, "SOURCE.json").delete())
        try {
            DriverPackage.verify(dir)
            fail("package missing a manifest-listed file must not verify")
        } catch (e: DriverPackageException) {
            assertTrue("message says incomplete: ${e.message}", e.message!!.contains("SOURCE.json"))
        }
    }

    @Test
    fun malformedManifestFails() {
        val dir = writePackage(tmp.newFolder())
        File(dir, "MANIFEST.json").writeText("{ not json")
        try {
            DriverPackage.parse(dir)
            fail("malformed MANIFEST.json must not parse")
        } catch (e: DriverPackageException) {
            assertTrue(e.message!!.contains("MANIFEST.json"))
        }
    }

    @Test
    fun malformedSha256SumsLineFails() {
        val dir = writePackage(tmp.newFolder())
        File(dir, "SHA256SUMS.txt").writeText("this is not a checksum line\n")
        try {
            DriverPackage.verify(dir)
            fail("malformed SHA256SUMS.txt must not verify")
        } catch (e: DriverPackageException) {
            assertTrue(e.message!!.contains("SHA256SUMS"))
        }
    }

    @Test
    fun missingManifestJsonFails() {
        val dir = tmp.newFolder()
        try {
            DriverPackage.parse(dir)
            fail("missing MANIFEST.json must not parse")
        } catch (e: DriverPackageException) {
            assertTrue(e.message!!.contains("MANIFEST.json"))
        }
    }

    @Test
    fun glibcAbiIsRecognized() {
        val so = fakeAarch64So("libc.so.6".toByteArray())
        val pkg = DriverPackage.parse(writePackage(tmp.newFolder(), soBytes = so, abi = "linux-aarch64-glibc"))
        assertTrue(pkg.isGlibc)
    }

    @Test
    fun parseSha256SumsHandlesBothSeparators() {
        val map = DriverPackage.parseSha256Sums("aa".repeat(32) + "  a.so\n" + "bb".repeat(32) + " *b.so\n")
        assertEquals("aa".repeat(32), map["a.so"])
        assertEquals("bb".repeat(32), map["b.so"])
    }

    /**
     * Verifies the REAL built package when -Dmali.driver.package points at it
     * (e.g. driver-work/packages/panvk-kbase-g720-v0.1.0-rc3-universal+local1).
     * Skipped silently when the property is absent (repo CI has no 24 MB fixture).
     */
    @Test
    fun realPackageVerifiesWhenPresent() {
        val path = System.getProperty("mali.driver.package") ?: return
        val result = DriverPackage.verify(File(path))
        assertEquals("0.1.0-rc3-universal+local1", result.pkg.packageVersion)
        assertEquals("0423314bdc4780e34daa4c1fed3cfd1b1ad39be3e203e0ea9c4c234b892564b9", result.pkg.binarySha256)
        assertEquals("1.4.363", result.pkg.apiVersion)
        println("real package verified: ${result.pkg.name} ${result.pkg.packageVersion} sha8=${result.pkg.shaPrefix}")
    }
}
