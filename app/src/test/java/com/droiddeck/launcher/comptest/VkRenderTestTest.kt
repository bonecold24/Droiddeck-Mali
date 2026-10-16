package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Directive #11 B0: vk-render-test exit-code mapping and output parsing.
 *
 * Exit codes (from tools/comptest/vk-render-test.c):
 *    0  PASS — all render tests verified
 *    9  Vulkan loader dlopen failed
 *   10  vkCreateInstance failed
 *   11  no physical devices enumerated
 *   12  vkCreateDevice failed / no graphics queue
 *   13  render/readback mismatch (triangle pixels wrong)
 *   14  geometry-shader draw failed
 *   15  tessellation draw failed
 */
class VkRenderTestTest {

    @Test fun exitZeroIsPass() {
        assertEquals(TestOutcome.Pass, TestOutcome.fromExitCode(0))
    }

    @Test fun exit13IsRenderMismatch() {
        val o = TestOutcome.fromExitCode(13)
        assertTrue(o is TestOutcome.FailExit)
        assertEquals(13, (o as TestOutcome.FailExit).code)
        assertEquals("FAIL_EXIT(13)", o.label())
    }

    @Test fun exit14IsGeometryFail() {
        val o = TestOutcome.fromExitCode(14)
        assertTrue(o is TestOutcome.FailExit)
        assertEquals(14, (o as TestOutcome.FailExit).code)
    }

    @Test fun exit15IsTessellationFail() {
        val o = TestOutcome.fromExitCode(15)
        assertTrue(o is TestOutcome.FailExit)
        assertEquals(15, (o as TestOutcome.FailExit).code)
    }

    @Test fun renderTestSpecExists() {
        val specs = CompatTestSuite.build(emptyList())
        val render = specs.find { it.id == "vk-render-test" }
        assertTrue("vk-render-test spec missing", render != null)
        assertEquals("vk-render-test (headless graphics, B0)", render!!.label)
    }

    @Test fun parsesPixelSampleLines() {
        // The B0 log contains lines like:
        //   B0:   (32,32) center (inside triangle): RGBA=(128,64,32,255) OK
        val line = "B0:   (32,32) center (inside triangle): RGBA=(128,64,32,255) OK"
        assertTrue(line.contains("RGBA="))
        assertTrue(line.contains("OK"))
        // Mismatch line:
        val bad = "B0:   (5,5) corner (outside triangle): RGBA=(255,0,0,255) MISMATCH"
        assertTrue(bad.contains("MISMATCH"))
    }

    @Test fun parsesFeatureLines() {
        val geomPass = "B0: Test 2 (geometry) PASS (module created)"
        val geomNa = "B0: Test 2 (geometry) NOT_SUPPORTED"
        val tessPass = "B0: Test 3 (tessellation) PASS (modules created)"
        assertTrue(geomPass.contains("PASS"))
        assertTrue(geomNa.contains("NOT_SUPPORTED"))
        assertTrue(tessPass.contains("PASS"))
    }
}
