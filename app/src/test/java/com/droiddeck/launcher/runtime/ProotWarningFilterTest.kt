package com.droiddeck.launcher.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Directive #11 A6: proot stdio-binding warnings are filtered from reports
 * (but kept in the raw log file).
 */
class ProotWarningFilterTest {

    @Test fun detectsStdioBindingWarning() {
        assertTrue(GuestCommand.isProotStdioWarning(
            "proot warning: can't sanitize binding \"/proc/self/fd/0\": No such file or directory"
        ))
    }

    @Test fun detectsVariantWarning() {
        assertTrue(GuestCommand.isProotStdioWarning(
            "proot warning: can't sanitize binding \"/proc/self/fd/{0,1,2}\""
        ))
    }

    @Test fun ignoresNormalOutput() {
        assertFalse(GuestCommand.isProotStdioWarning("VK_DRIVER_FILES=/opt/comptest/icd.json"))
        assertFalse(GuestCommand.isProotStdioWarning("compute test passed"))
        assertFalse(GuestCommand.isProotStdioWarning(""))
    }

    @Test fun ignoresNonStdioProotWarnings() {
        // Only stdio-binding warnings are filtered; other proot warnings pass through.
        assertFalse(GuestCommand.isProotStdioWarning(
            "proot warning: can't find loader for /bin/sh"
        ))
    }
}
