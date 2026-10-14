package com.droiddeck.launcher.comptest

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ordered compatibility-test runner.
 *
 * Guarantees:
 * - per-test wall-clock timeout ([TestSpec.timeoutSec]); a hung test is recorded as
 *   TIMEOUT and the suite continues — hangs can never stall the suite.
 * - crash isolation: a [TestExecutor] that throws is recorded as CRASH and the suite
 *   continues with the next test.
 * - cancel: [cancel] stops the suite (the in-flight test is interrupted and recorded).
 * - incremental persistence: every finished test is pushed to [ResultsSink] immediately,
 *   so a killed run keeps partial results.
 *
 * Pure JVM (threads + injected clock); host-unit-testable. On-device execution of
 * individual tests is NEEDS-REAL-MALI.
 */
class CompatTestRunner(
    private val executor: TestExecutor,
    private val clock: Clock = SystemClock,
    private val telemetry: (TestSpec) -> TelemetrySnapshot? = { null },
    private val driver: () -> DriverIdentity? = { null },
    private val sink: ResultsSink? = null,
) {
    interface Clock { fun nowMs(): Long }

    object SystemClock : Clock { override fun nowMs(): Long = System.currentTimeMillis() }

    interface TestExecutor {
        /** Runs one test. May throw on crash. Must honor [RunControl.cancelled]. */
        @Throws(Exception::class)
        fun execute(spec: TestSpec, ctl: RunControl): TestRunOutcome
    }

    data class TestRunOutcome(
        val outcome: TestOutcome,
        val frameTimesMs: List<Double> = emptyList(),
        val note: String? = null,
        /** Gate A4 per-stage diagnostics (command, env, exit, output tail, log file). */
        val diagnostics: CompatDiagnostics? = null,
        /** D3D payload install result, when the stage needed it (BUG-5). */
        val payloadInstall: PayloadInstallResult? = null,
    )

    interface RunControl {
        val cancelled: AtomicBoolean
        fun progress(fraction: Double, message: String)
    }

    interface ResultsSink { fun onTestFinished(result: TestResult) }

    interface Listener {
        fun onTestStarted(spec: TestSpec, index: Int, total: Int)
        fun onTestFinished(result: TestResult, index: Int, total: Int)
        fun onSuiteProgress(done: Int, total: Int)
    }

    var listener: Listener? = null
    private val cancelled = AtomicBoolean(false)

    fun cancel() { cancelled.set(true) }
    fun isCancelled(): Boolean = cancelled.get()

    fun runAll(specs: List<TestSpec>): List<TestResult> {
        val results = ArrayList<TestResult>(specs.size)
        for ((index, spec) in specs.withIndex()) {
            if (cancelled.get()) break
            listener?.onTestStarted(spec, index, specs.size)
            val result = runOne(spec)
            results.add(result)
            runCatching { sink?.onTestFinished(result) }
            listener?.onTestFinished(result, index, specs.size)
            listener?.onSuiteProgress(index + 1, specs.size)
        }
        return results
    }

    private fun runOne(spec: TestSpec): TestResult {
        val startedAt = clock.nowMs()
        val teleStart = runCatching { telemetry(spec) }.getOrNull()
        val outcome = executeWithTimeout(spec)
        val duration = clock.nowMs() - startedAt
        val stats = FpsStats.compute(outcome.frameTimesMs)
        val teleEnd = runCatching { telemetry(spec) }.getOrNull()
        val diag = outcome.diagnostics
        return TestResult(
            specId = spec.id,
            label = spec.label,
            kind = spec.kind,
            outcome = outcome.outcome,
            startedAtMs = startedAt,
            durationMs = duration,
            avgFps = stats?.avgFps,
            onePercentLowFps = stats?.onePercentLowFps,
            maxFrameTimeMs = stats?.maxFrameTimeMs,
            telemetryStart = teleStart,
            telemetryEnd = teleEnd,
            driver = runCatching { driver() }.getOrNull(),
            note = outcome.note,
            commandLine = diag?.commandLine,
            envVars = diag?.envVars,
            exitCode = diag?.exitCode,
            outputTail = diag?.outputTail,
            outputLinesTotal = diag?.outputLinesTotal,
            stderrMerged = diag?.stderrMerged ?: false,
            logFile = diag?.logFile,
            payloadInstall = outcome.payloadInstall,
        )
    }

    private fun executeWithTimeout(spec: TestSpec): TestRunOutcome {
        val ctl = object : RunControl {
            override val cancelled = AtomicBoolean(false)
            override fun progress(fraction: Double, message: String) { /* listener progress optional */ }
        }
        val box = arrayOfNulls<Any>(1)
        val worker = Thread({
            try {
                box[0] = executor.execute(spec, ctl)
            } catch (t: Throwable) {
                box[0] = t
            }
        }, "comptest-${spec.id}")
        worker.isDaemon = true
        worker.start()

        val timeoutMs = spec.timeoutSec * 1000L
        val deadline = clock.nowMs() + timeoutMs
        while (worker.isAlive) {
            if (cancelled.get()) {
                ctl.cancelled.set(true)
                worker.interrupt()
                worker.join(2_000)
                return TestRunOutcome(TestOutcome.Cancelled, emptyList(), "Cancelled by user")
            }
            val remaining = deadline - clock.nowMs()
            if (remaining <= 0) {
                ctl.cancelled.set(true)
                worker.interrupt()
                worker.join(2_000)
                return TestRunOutcome(
                    TestOutcome.Timeout, emptyList(),
                    "Exceeded ${spec.timeoutSec}s wall-clock budget",
                )
            }
            Thread.sleep(remaining.coerceAtMost(50))
        }
        return when (val r = box[0]) {
            is TestRunOutcome -> r
            is Throwable -> TestRunOutcome(
                // Gate B3: a harness throw is not a process exit; code -1 marks
                // "no process ran" so it is never mislabelled CRASH.
                TestOutcome.FailExit(-1), emptyList(),
                "Harness exception (no process ran): ${r.javaClass.simpleName}: ${r.message}".take(500),
            )
            else -> TestRunOutcome(
                TestOutcome.FailExit(-1), emptyList(),
                "Executor returned no result (no process ran)",
            )
        }
    }
}
