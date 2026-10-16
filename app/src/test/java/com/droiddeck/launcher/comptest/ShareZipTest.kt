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

    /**
     * Directive #11 A3: the export zip must contain comptest-driver-import.log
     * whenever an import step ran (or was skipped). The executor now always
     * writes the file; the zip includes every .log in the run dir.
     */
    @Test fun zipContainsDriverImportLog() {
        val tmp = createTempDir("sharezip3")
        val json = java.io.File(tmp, "report.json").apply { writeText("{}") }
        val md = java.io.File(tmp, "report.md").apply { writeText("# r") }
        val installLog = java.io.File(tmp, "comptest-driver-install.log")
            .apply { writeText("direct driver install: OK") }
        // Even the "skipped" marker must be zipped (A3: say plainly in the
        // report if the import was skipped).
        val importLog = java.io.File(tmp, "comptest-driver-import.log")
            .apply { writeText("import skipped: bundled glibc driver already present\n") }
        val zip = java.io.File(tmp, "out.zip")
        CompatTestExport.buildShareZip(zip, "run1", json, md, listOf(installLog, importLog))
        ZipFile(zip).use { zf ->
            val names = zf.entries().toList().map { it.name }
            assertTrue(names.contains("logs/comptest-driver-install.log"))
            assertTrue(names.contains("logs/comptest-driver-import.log"))
            val importText = zf.getInputStream(zf.getEntry("logs/comptest-driver-import.log"))
                .bufferedReader().readText()
            assertTrue(importText.contains("import skipped"))
        }
    }
}
