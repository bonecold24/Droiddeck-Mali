package com.droiddeck.launcher.gpu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Directive #10, task 2: reproduce the on-phone import failure against the
 * REAL assets extracted from the published v3-preview-3 APK.
 *
 * Run with -Dv3preview3.glibc.assets=<extracted assets/linux-drivers/panvk-kbase-glibc>
 * (from droiddeck-mali-v3-preview-UNTESTED.apk). Skipped when absent.
 *
 * - v3Preview3AssetsFailVerifyWithoutFilesArray: the "before" run. The
 *   upstream MANIFEST.json ships binarySha256 but NO files[] array, so
 *   DriverPackage.verify (the real import path used on the phone by
 *   GlibcDriverBundle.ensureBundledImported) throws "MANIFEST.json lists no
 *   files." — exactly why the glibc driver never reached linux_vulkan_drivers
 *   on the phone (Tier-0 2026-10-16) and the migration kept the bionic driver.
 * - v3Preview3AssetsVerifyAndInstallWithMergedFilesArray: the "after" run.
 *   Merges the files[] list into MANIFEST.json exactly as the fixed CI step
 *   does, then verify + install succeed and the installed driver is glibc.
 */
class BundledGlibcDriverImportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun v3Assets(): File {
        val path = System.getProperty("v3preview3.glibc.assets")
        assumeTrue("set -Dv3preview3.glibc.assets=<extracted v3-preview-3 glibc assets dir>", path != null)
        val dir = File(path!!)
        assumeTrue("assets dir exists: $path", dir.isDirectory)
        return dir
    }

    @Test
    fun v3Preview3AssetsFailVerifyWithoutFilesArray() {
        val dir = v3Assets()
        // Sanity: this is the real v3-preview-3 layout.
        assertTrue(File(dir, "libvulkan_panfrost.so").isFile)
        assertTrue(File(dir, "panfrost_icd.aarch64.json").isFile)
        assertTrue(File(dir, "MANIFEST.json").isFile)
        try {
            DriverPackage.verify(dir)
            fail("v3-preview-3 assets must NOT verify: upstream MANIFEST.json has no files[]")
        } catch (e: DriverPackageException) {
            println("BEFORE (reproduces phone bug): ${e.message}")
            assertTrue(
                "expected 'lists no files', got: ${e.message}",
                e.message!!.contains("lists no files"),
            )
        }
    }

    @Test
    fun v3Preview3AssetsVerifyAndInstallWithMergedFilesArray() {
        val src = v3Assets()
        val dir = tmp.newFolder()
        src.listFiles()!!.forEach { f -> f.copyTo(File(dir, f.name)) }
        // Merge the files[] list into MANIFEST.json exactly as the fixed CI
        // step does (preserving upstream metadata like mesaCommit/kbaseUapi).
        val manifestFile = File(dir, "MANIFEST.json")
        val manifest = JSONObject(manifestFile.readText())
        val files = dir.listFiles { f -> f.isFile }!!.map { it.name }.sorted()
        manifest.put("files", files)
        manifestFile.writeText(manifest.toString(2))
        println("AFTER (fixed APK layout): MANIFEST.json files[] = $files")

        val report = DriverPackage.verify(dir)
        assertTrue("installed package must be glibc", report.pkg.isGlibc)
        assertEquals("linux-aarch64-glibc", report.pkg.abi)
        println("verify OK: ${report.pkg.name} ${report.pkg.packageVersion} abi=${report.pkg.abi}")

        val driversRoot = tmp.newFolder()
        val installed = DriverPackageInstaller.install(dir, driversRoot)
        assertTrue("installed abi must be glibc", installed.abi.contains("glibc", ignoreCase = true))
        val installedDir = File(driversRoot, installed.id)
        assertTrue(installedDir.isDirectory)
        // The installer rewrites the ICD library_path to the absolute installed path.
        val icd = JSONObject(File(installedDir, "icd.json").readText())
        val libPath = icd.getJSONObject("ICD").getString("library_path")
        assertTrue("library_path must be absolute, got: $libPath", libPath.startsWith("/"))
        assertTrue("library_path must name the installed .so", libPath.endsWith("/${report.pkg.libraryName}"))
        println("install OK: id=${installed.id} library_path=$libPath")
    }
}
