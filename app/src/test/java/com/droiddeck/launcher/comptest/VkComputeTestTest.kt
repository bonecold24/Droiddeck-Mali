package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate B2: headless compute test exit-code mapping and display-probe script checks.
 * Pure Kotlin; host-unit-testable.
 */
class VkComputeTestTest {

    @Test fun exitZeroIsPass() {
        val (outcome, reason) = vkComputeExitMeaning(0)
        assertEquals(TestOutcome.REACHED_MENU, outcome)
        assertTrue(reason.contains("PASS"))
    }

    @Test fun exitNineIsPrereqMissingNotGpuFailure() {
        // No Vulkan loader in the guest is environmental — must not be labelled a GPU failure.
        val (outcome, reason) = vkComputeExitMeaning(9)
        assertEquals(TestOutcome.PREREQ_MISSING, outcome)
        assertTrue(reason.contains("libvulkan.so.1"))
    }

    @Test fun exitTenIsInstanceFailure() {
        val (outcome, reason) = vkComputeExitMeaning(10)
        assertEquals(TestOutcome.CRASH, outcome)
        assertTrue(reason.contains("vkCreateInstance"))
    }

    @Test fun exitElevenIsNoDevices() {
        val (outcome, reason) = vkComputeExitMeaning(11)
        assertEquals(TestOutcome.CRASH, outcome)
        assertTrue(reason.contains("no physical devices"))
    }

    @Test fun exitTwelveIsDeviceFailure() {
        val (outcome, reason) = vkComputeExitMeaning(12)
        assertEquals(TestOutcome.CRASH, outcome)
        assertTrue(reason.contains("vkCreateDevice"))
    }

    @Test fun exitThirteenIsComputeFailure() {
        val (outcome, reason) = vkComputeExitMeaning(13)
        assertEquals(TestOutcome.CRASH, outcome)
        assertTrue(reason.contains("compute failed"))
    }

    @Test fun unknownExitIsCrashWithPlainNote() {
        val (outcome, reason) = vkComputeExitMeaning(42)
        assertEquals(TestOutcome.CRASH, outcome)
        assertTrue(reason.contains("unexpected"))
    }

    @Test fun allDefinedCodesHaveDistinctReasons() {
        val reasons = listOf(0, 9, 10, 11, 12, 13).map { vkComputeExitMeaning(it).second }
        assertEquals(6, reasons.toSet().size)
    }
}
