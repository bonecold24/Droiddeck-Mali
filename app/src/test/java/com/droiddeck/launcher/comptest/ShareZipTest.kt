package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipFile

/** Gate B1: the one-tap Share export is a ZIP with report + per-stage logs. */
class ShareZipTest {

    @Test fun zipContainsReportAndStageLogs() {
        val tmp = createTempDir("sharezip")
        val json = java.io.File(tmp, "report.json").apply { writeText("{}") }
        val md = java.io.File(tmp, "report.md").apply { writeText("# r") }
        val log1 = java.io.File(tmp, "comptest-vkcube.log").apply { writeText("vkcube out") }
        val log2 = java.io.File(tmp, "comptest-vulkaninfo.log").apply { writeText("vulkaninfo out") }
        val zip = java.io.File(tmp, "out.zip")
        CompatTestExport.buildShareZip(zip, "20261014-120000", json, md, listOf(log1, log2))
        ZipFile(zip).use { zf ->
            val names = zf.entries().toList().map { it.name }.sorted()
            assertEquals(
                listOf(
                    "droiddeck-comptest-20261014-120000.json",
                    "droiddeck-comptest-20261014-120000.md",
                    "logs/comptest-vkcube.log",
                    "logs/comptest-vulkaninfo.log",
                ),
                names,
            )
            assertEquals("vkcube out", zf.getInputStream(zf.getEntry("logs/comptest-vkcube.log"))
                .bufferedReader().readText())
        }
    }

    @Test fun zipWorksWithNoStageLogs() {
        val tmp = createTempDir("sharezip2")
        val json = java.io.File(tmp, "report.json").apply { writeText("{}") }
        val md = java.io.File(tmp, "report.md").apply { writeText("# r") }
        val zip = java.io.File(tmp, "out.zip")
        CompatTestExport.buildShareZip(zip, "run1", json, md, emptyList())
        ZipFile(zip).use { zf ->
            assertEquals(2, zf.entries().toList().size)
        }
    }
}
