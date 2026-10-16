package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate B2/B3: headless compute test exit-code taxonomy.
 * The vk-compute-test binary uses exit codes 0/9/10/11/12/13; the runner maps
 * them through TestOutcome.fromExitCode, which never labels anything CRASH.
 * Pure Kotlin; host-unit-testable.
 */
class VkComputeTestTest {

    @Test fun exitZeroIsPass() {
        assertEquals(TestOutcome.Pass, TestOutcome.fromExitCode(0))
        assertEquals("PASS", TestOutcome.fromExitCode(0).label())
    }

    @Test fun exitNineIsFailExitNotCrash() {
        // No Vulkan loader in the guest is environmental; the taxonomy still
        // records the raw code — never a CRASH label (Gate B3).
        val o = TestOutcome.fromExitCode(9)
        assertEquals(TestOutcome.FailExit(9), o)
        assertEquals("FAIL_EXIT(9)", o.label())
        assertTrue(o.isFailure)
    }

    @Test fun exitTenIsFailExit() {
        assertEquals(TestOutcome.FailExit(10), TestOutcome.fromExitCode(10))
    }

    @Test fun exitElevenIsFailExit() {
        // 11 = no physical devices enumerated (expected with no Mali GPU).
        assertEquals(TestOutcome.FailExit(11), TestOutcome.fromExitCode(11))
    }

    @Test fun exitTwelveIsFailExit() {
        assertEquals(TestOutcome.FailExit(12), TestOutcome.fromExitCode(12))
    }

    @Test fun exitThirteenIsFailExit() {
        assertEquals(TestOutcome.FailExit(13), TestOutcome.fromExitCode(13))
    }

    @Test fun exit127IsNotFound() {
        assertEquals(TestOutcome.NotFound, TestOutcome.fromExitCode(127))
    }

    @Test fun exitAbove128IsSignal() {
        assertEquals(TestOutcome.Signal(11), TestOutcome.fromExitCode(139))
    }

    @Test fun allDefinedCodesAreDistinct() {
        val labels = listOf(0, 9, 10, 11, 12, 13).map { TestOutcome.fromExitCode(it).label() }
        assertEquals(6, labels.toSet().size)
    }

    @Test fun noExitCodeIs everLabelledCrash() {
        // Gate B3: the word CRASH must never appear in any taxonomy label.
        for (code in listOf(0, 1, 9, 10, 11, 12, 13, 42, 127, 139)) {
            assertTrue(
                "exit $code labelled ${TestOutcome.fromExitCode(code).label()}",
                !TestOutcome.fromExitCode(code).label().contains("CRASH"),
            )
        }
    }
}
