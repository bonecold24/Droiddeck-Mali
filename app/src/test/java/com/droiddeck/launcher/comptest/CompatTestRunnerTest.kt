package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class CompatTestRunnerTest {
    private class FixedClock(var t: Long = 1_000L) : CompatTestRunner.Clock {
        override fun nowMs(): Long = t
    }

    private fun executorOf(outcome: TestOutcome, frames: List<Double> = emptyList(), note: String? = null) =
        object : CompatTestRunner.TestExecutor {
            override fun execute(spec: TestSpec, ctl: CompatTestRunner.RunControl): CompatTestRunner.TestRunOutcome =
                CompatTestRunner.TestRunOutcome(outcome, frames, note)
        }

    private fun spec(id: String, timeoutSec: Int = 5) =
        TestSpec(id, id, TestKind.SYSTEM_CHECK, "desc", timeoutSec)

    @Test fun outcomesAndStatsFlowIntoResults() {
        val runner = CompatTestRunner(executorOf(TestOutcome.ReachedGameplay, List(600) { 1000.0 / 60.0 }))
        val results = runner.runAll(listOf(spec("vkcube")))
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals(TestOutcome.ReachedGameplay, r.outcome)
        assertTrue(r.avgFps!! > 59.9)
        assertTrue(r.onePercentLowFps!! > 59.9)
    }

    @Test fun crashingExecutorIsIsolatedAndSuiteContinues() {
        var calls = 0
        val exec = object : CompatTestRunner.TestExecutor {
            override fun execute(spec: TestSpec, ctl: CompatTestRunner.RunControl): CompatTestRunner.TestRunOutcome {
                calls++
                if (spec.id == "boom") throw RuntimeException("segfault in vkcube")
                return CompatTestRunner.TestRunOutcome(TestOutcome.ReachedMenu, emptyList(), null)
            }
        }
        val runner = CompatTestRunner(exec)
        val results = runner.runAll(listOf(spec("boom"), spec("vulkaninfo"), spec("d3d9")))
        assertEquals(3, calls)
        assertEquals(3, results.size)
        assertEquals(TestOutcome.FailExit(-1), results[0].outcome)
        assertTrue(results[0].note!!.contains("segfault"))
        assertEquals(TestOutcome.ReachedMenu, results[1].outcome)
        assertEquals(TestOutcome.ReachedMenu, results[2].outcome)
    }

    @Test fun timeoutProducesTimeoutOutcomeWithoutStalling() {
        val clock = FixedClock()
        val exec = object : CompatTestRunner.TestExecutor {
            override fun execute(spec: TestSpec, ctl: CompatTestRunner.RunControl): CompatTestRunner.TestRunOutcome {
                // simulate a hung test; the watchdog clock advances in lockstep
                while (!ctl.cancelled.get()) {
                    clock.t += 1_000
                    Thread.sleep(1)
                }
                return CompatTestRunner.TestRunOutcome(TestOutcome.ReachedMenu, emptyList(), null)
            }
        }
        val runner = CompatTestRunner(exec, clock)
        val start = System.currentTimeMillis()
        val results = runner.runAll(listOf(spec("hang", timeoutSec = 2)))
        val elapsed = System.currentTimeMillis() - start
        assertEquals(1, results.size)
        assertEquals(TestOutcome.Timeout, results[0].outcome)
        assertTrue("suite must not stall: took ${elapsed}ms", elapsed < 10_000)
    }

    @Test fun cancelBetweenTestsStopsSuite() {
        var calls = 0
        val exec = object : CompatTestRunner.TestExecutor {
            override fun execute(spec: TestSpec, ctl: CompatTestRunner.RunControl): CompatTestRunner.TestRunOutcome {
                calls++
                return CompatTestRunner.TestRunOutcome(TestOutcome.ReachedMenu, emptyList(), null)
            }
        }
        val runner = CompatTestRunner(exec)
        runner.listener = object : CompatTestRunner.Listener {
            override fun onTestStarted(spec: TestSpec, index: Int, total: Int) {}
            override fun onTestFinished(result: TestResult, index: Int, total: Int) {
                if (index == 1) runner.cancel()
            }
            override fun onSuiteProgress(done: Int, total: Int) {}
        }
        val results = runner.runAll(listOf(spec("a"), spec("b"), spec("c")))
        assertEquals(2, calls)
        assertEquals(2, results.size)
    }

    @Test fun partialResultsArePersistedAsItGoes() {
        val seen = mutableListOf<TestResult>()
        val runner = CompatTestRunner(
            executorOf(TestOutcome.ReachedMenu),
            sink = object : CompatTestRunner.ResultsSink {
                override fun onTestFinished(result: TestResult) { seen.add(result) }
            },
        )
        runner.runAll(listOf(spec("a"), spec("b")))
        assertEquals(listOf("a", "b"), seen.map { it.specId })
    }

    @Test fun executorDetectedHangIsRecordedAsTimeout() {
        val runner = CompatTestRunner(executorOf(TestOutcome.Timeout, note = "no frames for 5s"))
        val results = runner.runAll(listOf(spec("game")))
        assertEquals(TestOutcome.Timeout, results[0].outcome)
        assertEquals("no frames for 5s", results[0].note)
    }

    @Test fun skippedOutcomeSurvives() {
        val runner = CompatTestRunner(executorOf(TestOutcome.Skipped, note = "not installed"))
        val results = runner.runAll(listOf(spec("game", 60)))
        assertEquals(TestOutcome.Skipped, results[0].outcome)
    }
}
