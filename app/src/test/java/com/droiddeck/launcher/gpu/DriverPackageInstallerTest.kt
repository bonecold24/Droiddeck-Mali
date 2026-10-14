package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Host-runnable tests for [DriverPackageInstaller]: versioned ids, coexistence of
 * several versions side by side, idempotent reinstall, and rollback lookup.
 */
class DriverPackageInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fakeAarch64So(seed: String): ByteArray {
        val h = ByteArray(64)
        h[0] = 0x7f; h[1] = 'E'.code.toByte(); h[2] = 'L'.code.toByte(); h[3] = 'F'.code.toByte()
        h[4] = 2; h[5] = 1
        h[18] = 0xB7.toByte(); h[19] = 0
        return h + seed.toByteArray()
    }

    /** Builds a minimal verifiable package dir for [packageVersion] with .so content [seed]. */
    private fun pkg(packageVersion: String, seed: String, name: String = "PanVK Kbase (test)"): File {
        val dir = tmp.newFolder()
        val so = fakeAarch64So(seed)
        File(dir, "libvulkan_panfrost.so").writeBytes(so)
        val soSha = sha256Hex(so)
        File(dir, "panvk_kbase_icd.aarch64.json").writeText(
            """{"ICD": {"api_version": "1.4.363", "library_arch": "64", "library_path": "./libvulkan_panfrost.so"}, "file_format_version": "1.0.1"}""")
        File(dir, "meta.json").writeText(
            """{"schemaVersion": 1, "name": "$name", "libraryName": "libvulkan_panfrost.so", "abi": "linux-aarch64-glibc", "backend": "panvk-kbase", "profile": "g720-v12-csf", "packageVersion": "$packageVersion"}""")
        val files = listOf("libvulkan_panfrost.so", "panvk_kbase_icd.aarch64.json", "meta.json", "MANIFEST.json", "SHA256SUMS.txt")
        File(dir, "MANIFEST.json").writeText(
            """{"profile": "g720-v12-csf", "abi": "linux-aarch64-glibc", "binarySha256": "$soSha", "files": [${files.joinToString(", ") { "\"$it\"" }}]}""")
        // A checksum file cannot checksum itself: cover every other listed file.
        val covered = files.filter { it != "SHA256SUMS.txt" }
        File(dir, "SHA256SUMS.txt").writeText(covered.joinToString("\n") { "${sha256Hex(File(dir, it).readBytes())}  $it" } + "\n")
        return dir
    }

    @Test
    fun installCreatesVersionedId() {
        val root = tmp.newFolder()
        val installed = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        // name + packageVersion + binarySha256 prefix, sanitized, coexisting side by side.
        assertTrue("id=${installed.id}", installed.id.startsWith("panvk-kbase-test-0.1.0-rc3-universal-local1-"))
        assertEquals(8, installed.id.substringAfterLast("-").length)
        assertTrue(File(installed.dir, "libvulkan_panfrost.so").isFile)
        assertTrue(File(installed.dir, "icd.json").isFile)
        assertTrue(File(installed.dir, "meta.json").isFile)
    }

    @Test
    fun installRewritesIcdLibraryPathAbsolute() {
        val root = tmp.newFolder()
        val installed = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        val icd = JSONObject(File(installed.dir, "icd.json").readText())
        val libPath = icd.getJSONObject("ICD").getString("library_path")
        assertEquals(File(installed.dir, "libvulkan_panfrost.so").absolutePath, libPath)
        assertEquals("1.4.363", icd.getJSONObject("ICD").getString("api_version"))
    }

    @Test
    fun installRecordsManagerMeta() {
        val root = tmp.newFolder()
        val installed = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        val meta = JSONObject(File(installed.dir, "meta.json").readText())
        assertEquals("PanVK Kbase (test)", meta.getString("name"))
        assertEquals("0.1.0-rc3-universal+local1", meta.getString("driverVersion"))
        assertEquals("linux-aarch64-glibc", meta.getString("abi"))
        assertEquals("panvk-kbase", meta.getString("backend"))
        assertEquals(64, meta.getString("binarySha256").length)
        assertEquals("libvulkan_panfrost.so", meta.getString("libraryName"))
    }

    @Test
    fun reinstallIsIdempotent() {
        val root = tmp.newFolder()
        val first = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        val second = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        assertEquals(first.id, second.id)
        assertEquals(1, root.listFiles { f -> f.isDirectory }!!.size)
    }

    @Test
    fun twoVersionsCoexistSideBySide() {
        val root = tmp.newFolder()
        val v1 = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        val v2 = DriverPackageInstaller.install(pkg("0.1.0-rc4", "v2"), root)
        assertNotEquals(v1.id, v2.id)
        assertTrue(File(root, v1.id).isDirectory)
        assertTrue(File(root, v2.id).isDirectory)
        // The old version is kept: rollback stays possible.
        val versions = DriverPackageInstaller.listVersions(root, "PanVK Kbase (test)")
        assertEquals(listOf(v2.id, v1.id), versions.map { it.id })
    }

    @Test
    fun previousVersionFindsRollbackTarget() {
        val root = tmp.newFolder()
        val v1 = DriverPackageInstaller.install(pkg("0.1.0-rc3-universal+local1", "v1"), root)
        val v2 = DriverPackageInstaller.install(pkg("0.1.0-rc4", "v2"), root)
        assertEquals(v1.id, DriverPackageInstaller.previousVersion(root, v2.id)?.id)
        assertNull(DriverPackageInstaller.previousVersion(root, v1.id))
        assertNull(DriverPackageInstaller.previousVersion(root, "no-such-id"))
    }

    @Test
    fun tamperedPackageIsRejected() {
        val root = tmp.newFolder()
        val dir = pkg("0.1.0-rc3-universal+local1", "v1")
        File(dir, "libvulkan_panfrost.so").appendBytes("evil".toByteArray())
        try {
            DriverPackageInstaller.install(dir, root)
            throw AssertionError("tampered package must not install")
        } catch (e: DriverPackageException) {
            // expected; nothing registered
        }
        assertEquals(0, root.listFiles()!!.size)
    }

    @Test
    fun nonElfLibraryIsRejected() {
        val root = tmp.newFolder()
        val dir = tmp.newFolder()
        File(dir, "libvulkan_panfrost.so").writeText("not an elf at all")
        val soSha = sha256Hex("not an elf at all".toByteArray())
        File(dir, "panvk_kbase_icd.aarch64.json").writeText(
            """{"ICD": {"api_version": "1.4.363", "library_arch": "64", "library_path": "./libvulkan_panfrost.so"}, "file_format_version": "1.0.1"}""")
        File(dir, "meta.json").writeText(
            """{"schemaVersion": 1, "name": "PanVK Kbase (test)", "libraryName": "libvulkan_panfrost.so", "abi": "linux-aarch64-glibc", "backend": "panvk-kbase", "profile": "g720-v12-csf", "packageVersion": "0.1.0"}""")
        File(dir, "MANIFEST.json").writeText(
            """{"profile": "g720-v12-csf", "abi": "linux-aarch64-glibc", "binarySha256": "$soSha", "files": ["libvulkan_panfrost.so", "panvk_kbase_icd.aarch64.json", "meta.json", "MANIFEST.json", "SHA256SUMS.txt"]}""")
        File(dir, "SHA256SUMS.txt").writeText("$soSha  libvulkan_panfrost.so\n")
        try {
            DriverPackageInstaller.install(dir, root)
            throw AssertionError("non-ELF library must not install")
        } catch (e: DriverPackageException) {
            assertTrue(e.message!!.contains("AArch64"))
        }
    }
}
