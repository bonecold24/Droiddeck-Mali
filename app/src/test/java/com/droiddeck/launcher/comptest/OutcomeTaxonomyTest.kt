package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate B3: the outcome taxonomy. A plain non-zero exit is never "CRASH". */
class OutcomeTaxonomyTest {

    @Test fun exitZeroIsPass() {
        assertEquals(TestOutcome.Pass, TestOutcome.fromExitCode(0))
    }

    @Test fun exitOneIsFailExitNotCrash() {
        val o = TestOutcome.fromExitCode(1)
        assertTrue(o is TestOutcome.FailExit)
        assertEquals(1, (o as TestOutcome.FailExit).code)
        assertEquals("FAIL_EXIT(1)", o.label())
    }

    @Test fun exit127IsNotFound() {
        assertEquals(TestOutcome.NotFound, TestOutcome.fromExitCode(127))
        assertEquals("NOT_FOUND(127)", TestOutcome.NotFound.label())
    }

    @Test fun exitAbove128IsSignal() {
        val segv = TestOutcome.fromExitCode(139)
        assertTrue(segv is TestOutcome.Signal)
        assertEquals(11, (segv as TestOutcome.Signal).number)
        assertEquals("SIGNAL(11)", segv.label())
        assertEquals("SIGNAL(15)", TestOutcome.fromExitCode(143).label())
    }

    @Test fun taxonomyLabels() {
        assertEquals("PASS", TestOutcome.Pass.label())
        assertEquals("TIMEOUT", TestOutcome.Timeout.label())
        assertEquals("SKIPPED", TestOutcome.Skipped.label())
        assertEquals("CANCELLED", TestOutcome.Cancelled.label())
        assertEquals("PREREQ_MISSING", TestOutcome.PrereqMissing("x").label())
        assertEquals("REACHED_MENU", TestOutcome.ReachedMenu.label())
        assertEquals("REACHED_GAMEPLAY", TestOutcome.ReachedGameplay.label())
    }

    @Test fun passAndFailurePredicates() {
        assertTrue(TestOutcome.Pass.isPass)
        assertTrue(TestOutcome.ReachedMenu.isPass)
        assertTrue(TestOutcome.ReachedGameplay.isPass)
        assertFalse(TestOutcome.FailExit(1).isPass)
        assertFalse(TestOutcome.Skipped.isPass)

        assertTrue(TestOutcome.FailExit(1).isFailure)
        assertTrue(TestOutcome.NotFound.isFailure)
        assertTrue(TestOutcome.Signal(11).isFailure)
        assertTrue(TestOutcome.Timeout.isFailure)
        assertTrue(TestOutcome.PrereqMissing("x").isFailure)
        assertFalse(TestOutcome.Pass.isFailure)
        assertFalse(TestOutcome.Skipped.isFailure)
        assertFalse(TestOutcome.Cancelled.isFailure)
    }

    @Test fun serialNamesAreStable() {
        assertEquals("PASS", TestOutcome.Pass.serialName)
        assertEquals("FAIL_EXIT", TestOutcome.FailExit(1).serialName)
        assertEquals("NOT_FOUND", TestOutcome.NotFound.serialName)
        assertEquals("SIGNAL", TestOutcome.Signal(9).serialName)
        assertEquals("TIMEOUT", TestOutcome.Timeout.serialName)
        assertEquals("PREREQ_MISSING", TestOutcome.PrereqMissing("x").serialName)
        assertEquals("SKIPPED", TestOutcome.Skipped.serialName)
    }
}
