package com.droiddeck.launcher.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-runnable tests for [LinuxDriverMigration.decide]: the Gate A2
 * selection-migration rule.
 *
 * Cases:
 *  - fresh install (no selection)        -> migrate to the bundled driver
 *  - v2 upgrade (bionic selection)       -> migrate to the bundled driver
 *  - corrupted selection (missing files) -> migrate to the bundled driver
 *  - manual valid glibc choice           -> kept, never overridden
 */
class LinuxDriverMigrationTest {

    private val bundled = "panvk-kbase-glibc-0.1.0-rc3"

    @Test
    fun freshInstallMigratesToBundled() {
        val d = LinuxDriverMigration.decide(
            currentSelection = "",
            isValid = { false },
            bundledId = { bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Migrate(bundled), d)
    }

    @Test
    fun v2BionicSelectionMigratesToBundled() {
        // v2 left the Android/bionic PanVK package selected as the Linux driver.
        val d = LinuxDriverMigration.decide(
            currentSelection = "panvk-kbase-bionic-0.1.0-rc3",
            isValid = { id -> id != "panvk-kbase-bionic-0.1.0-rc3" },
            bundledId = { bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Migrate(bundled), d)
    }

    @Test
    fun corruptedSelectionMigratesToBundled() {
        // Selection points at a driver whose files were wiped: not installed.
        val d = LinuxDriverMigration.decide(
            currentSelection = "ghost-driver-id",
            isValid = { false },
            bundledId = { bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Migrate(bundled), d)
    }

    @Test
    fun manualValidGlibcChoiceIsKept() {
        // The user hand-picked a working glibc driver: never override it.
        val d = LinuxDriverMigration.decide(
            currentSelection = "user-picked-glibc-driver",
            isValid = { it == "user-picked-glibc-driver" },
            bundledId = { bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Keep, d)
    }

    @Test
    fun bundledDriverItselfIsKept() {
        // Already on the bundled driver (e.g. re-imported after a wipe):
        // no preference rewrite needed.
        val d = LinuxDriverMigration.decide(
            currentSelection = bundled,
            isValid = { false }, // files were wiped; bundledId() re-imports the same id
            bundledId = { bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Keep, d)
    }

    @Test
    fun noBundledDriverAvailableLeavesSelectionAlone() {
        // APK without a bundled driver (or import failed): report unavailable
        // instead of clearing the user's selection.
        val d = LinuxDriverMigration.decide(
            currentSelection = "panvk-kbase-bionic-0.1.0-rc3",
            isValid = { false },
            bundledId = { null },
        )
        assertEquals(LinuxDriverMigration.Decision.Unavailable, d)
    }

    @Test
    fun invalidEmptySelectionWithNoBundleIsUnavailable() {
        val d = LinuxDriverMigration.decide(
            currentSelection = "",
            isValid = { false },
            bundledId = { null },
        )
        assertEquals(LinuxDriverMigration.Decision.Unavailable, d)
    }

    @Test
    fun validBundledSelectionIsKeptWithoutReimport() {
        // Valid selection: bundledId() must not even be consulted.
        var consulted = false
        val d = LinuxDriverMigration.decide(
            currentSelection = bundled,
            isValid = { true },
            bundledId = { consulted = true; bundled },
        )
        assertEquals(LinuxDriverMigration.Decision.Keep, d)
        assertTrue("bundledId() should not run when the selection is valid", !consulted)
    }
}
