package com.droiddeck.launcher.comptest

/**
 * Shared data model for the built-in Compatibility Test mode.
 *
 * Pure Kotlin (no Android framework) so every field is host-unit-testable.
 * On-device execution (running vkcube / games) is NEEDS-REAL-MALI.
 */
enum class TestKind { SYSTEM_CHECK, GAME_TEST }

/**
 * Outcome taxonomy for a single test stage (Gate B3).
 *
 * A plain non-zero exit is NEVER labelled "CRASH": it becomes [FailExit], [NotFound]
 * (127) or [Signal] (exit > 128) via [fromExitCode]. [FailExit] with code -1 means
 * no process was spawned at all (harness/internal failure) — see the note.
 */
sealed class TestOutcome {
    /** Stable base token used in reports; [label] renders the full taxonomy string. */
    abstract val serialName: String
    /** Display order for summaries (failures group after passes). */
    abstract val sortOrder: Int

    /** Stage ran and exited 0. */
    data object Pass : TestOutcome() {
        override val serialName = "PASS"
        override val sortOrder = 0
    }
    /** Non-zero exit that is not 127 and not a signal. code==-1: no process ran (harness failure). */
    data class FailExit(val code: Int) : TestOutcome() {
        override val serialName = "FAIL_EXIT"
        override val sortOrder = 10
    }
    /** Exit 127 — the binary/command was not found. */
    data object NotFound : TestOutcome() {
        override val serialName = "NOT_FOUND"
        override val sortOrder = 11
    }
    /** Exit > 128 — killed by signal [number] (exit - 128). */
    data class Signal(val number: Int) : TestOutcome() {
        override val serialName = "SIGNAL"
        override val sortOrder = 12
    }
    /** Wall-clock timeout enforced by the runner watchdog. */
data object Timeout : TestOutcome() {
        override val serialName = "TIMEOUT"
        override val sortOrder = 13
    }
    /** A prerequisite was missing, so the stage never ran. Never a GPU verdict. */
    data class PrereqMissing(val reason: String) : TestOutcome() {
        override val serialName = "PREREQ_MISSING"
        override val sortOrder = 20
    }
    /** Not applicable (e.g. game not installed). Never a failure. */
    data object Skipped : TestOutcome() {
        override val serialName = "SKIPPED"
        override val sortOrder = 30
    }
    /** Game test reached its menu (game tests only). Counts as a pass. */
    data object ReachedMenu : TestOutcome() {
        override val serialName = "REACHED_MENU"
        override val sortOrder = 1
    }
    /** Game test reached gameplay (game tests only). Counts as a pass. */
    data object ReachedGameplay : TestOutcome() {
        override val serialName = "REACHED_GAMEPLAY"
        override val sortOrder = 2
    }
    /** Cancelled by the user. */
    data object Cancelled : TestOutcome() {
        override val serialName = "CANCELLED"
        override val sortOrder = 31
    }

    /** Full taxonomy label, e.g. "FAIL_EXIT(1)", "SIGNAL(11)", "PASS". */
    fun label(): String = when (this) {
        is Pass -> "PASS"
        is FailExit -> "FAIL_EXIT($code)"
        is NotFound -> "NOT_FOUND(127)"
        is Signal -> "SIGNAL($number)"
        is Timeout -> "TIMEOUT"
        is PrereqMissing -> "PREREQ_MISSING"
        is Skipped -> "SKIPPED"
        is ReachedMenu -> "REACHED_MENU"
        is ReachedGameplay -> "REACHED_GAMEPLAY"
        is Cancelled -> "CANCELLED"
    }

    /** True when the stage did what it was supposed to do. */
    val isPass: Boolean get() = this is Pass || this is ReachedMenu || this is ReachedGameplay
    /** True when this is a genuine failure (not a skip or a cancel). */
    val isFailure: Boolean get() =
        this is FailExit || this is NotFound || this is Signal || this is Timeout || this is PrereqMissing

    companion object {
        /**
         * Classify a raw process exit code. Never returns a "CRASH" label (Gate B3):
         * 0 → PASS, 127 → NOT_FOUND, >128 → SIGNAL(n), anything else → FAIL_EXIT(code).
         */
        fun fromExitCode(code: Int): TestOutcome = when {
            code == 0 -> Pass
            code == 127 -> NotFound
            code > 128 -> Signal(code - 128)
            else -> FailExit(code)
        }
    }
TIMEOUT,
    /**
     * A prerequisite for the test is missing in the environment (e.g. no
     * display server for vkcube, no Vulkan loader for the compute test).
     * Environmental — never a GPU/driver failure.
     */
    PREREQ_MISSING,
    /** Game not installed (or test not applicable) — never a suite failure. */
    SKIPPED,
    /** Anything else (setup failure, cancelled, internal error). */
    ERROR,}

data class TestSpec(
    val id: String,
    val label: String,
    val kind: TestKind,
    val description: String,
    /** Per-test wall-clock budget in seconds. */
    val timeoutSec: Int,
    /** e.g. "DXVK-Sarek" / "stock DXVK" for D3D cubes; null for pure-Vulkan checks. */
    val dxvkVariant: String? = null,
    /** GAME_TEST only: the installed-game key (AddedGames.gameId / Steam app id). */
    val gameId: Long? = null,
)

data class DriverIdentity(
    val name: String,
    val version: String,
    val binarySha256: String,
    val vulkanApi: String,
)

/**
 * Validation of the selected Linux driver for guest use (BUG-3 diagnostic).
 * Pure data; computed by [DriverValidation] in the executor.
 */
data class DriverValidation(
    val selectedId: String,
    /** True when the selection is a loadable glibc aarch64 ICD. */
    val usable: Boolean,
    /** Machine-readable reason when not usable, e.g. "bionic-selected", "icd-missing". */
    val reason: String,
    /** Human-readable detail for the report. */
    val detail: String,
)

data class EnvironmentInfo(
    val appVersion: String,
    val appCommit: String,
    val driverPackage: DriverIdentity,
    val protonVersion: String,
    /** kbase CSF UAPI as read on the device, e.g. "1.21" — never assumed. */
    val kbaseUapi: String,
    val deviceModel: String,
    val soc: String,
    /** BUG-3: validation of the selected Linux driver (null when none selected). */
    val driverValidation: DriverValidation? = null,
)

data class TelemetrySnapshot(
    val cpuTempC: Double?,
    val gpuTempC: Double?,
    val batteryPct: Int?,
    val ramUsedMb: Long?,
    val atMs: Long,
)

/** Result of staging the D3D Tier-0 payload into the guest (BUG-5: never silent). */
data class PayloadInstallResult(
    val ok: Boolean,
    val guestPath: String = "/opt/comptest",
    val filesInstalled: List<String> = emptyList(),
    /** Null on success; human-readable reason on failure. */
    val reason: String? = null,
)

/** Gate A4 per-stage diagnostics, carried from executor to TestResult. Pure. */
data class CompatDiagnostics(
    val commandLine: String? = null,
    val envVars: Map<String, String>? = null,
    val exitCode: Int? = null,
    val outputTail: List<String>? = null,
    val outputLinesTotal: Int? = null,
    val stderrMerged: Boolean = false,
    val logFile: String? = null,
)

data class TestResult(
    val specId: String,
    val label: String,
    val kind: TestKind,
    val outcome: TestOutcome,
    val startedAtMs: Long,
    val durationMs: Long,
    val avgFps: Double?,
    val onePercentLowFps: Double?,
    val maxFrameTimeMs: Double?,
    val telemetryStart: TelemetrySnapshot?,
    val telemetryEnd: TelemetrySnapshot?,
    val driver: DriverIdentity?,
    val note: String?,
    // --- Gate A4 diagnostics ---
    /** Exact guest command line, e.g. "/usr/bin/vkcube --c 600". Null for non-guest stages. */
    val commandLine: String? = null,
    /** Env vars exported to the guest (VK_DRIVER_FILES, LD_LIBRARY_PATH, ...). */
    val envVars: Map<String, String>? = null,
    /** Raw process exit code. Null when no process ran. */
    val exitCode: Int? = null,
    /**
     * Last 100 lines of the stage output. The guest runner merges stderr into
     * stdout (redirectErrorStream), so this is the merged stream; [stderrMerged]
     * records that fact. The full output lives in [logFile].
     */
    val outputTail: List<String>? = null,
    /** Total output lines captured (outputTail may be truncated to the last 100). */
    val outputLinesTotal: Int? = null,
    /** True when stderr was merged into stdout by the guest runner. */
    val stderrMerged: Boolean = false,
    /**
     * Per-stage log file name, e.g. "comptest-vkcube.log".
     * On the phone: /data/data/com.droiddeck.launcher/files/logs/tools/<name>
     * (see GuestCommand: LinuxRuntime.logDir + SessionPaths.TOOLS_DIR).
     */
    val logFile: String? = null,
    /** Payload install result for stages that need /opt/comptest (BUG-5). */
    val payloadInstall: PayloadInstallResult? = null,
)

/** The first failing stage in suite order, for the report's plain-language verdict. */
data class FirstFailure(
    val specId: String,
    val label: String,
    val outcomeLabel: String,
    /** Why it failed first, in one or two sentences (may quote output tail). */
    val reason: String,
)

/**
 * Pure mapping of vk-compute-test exit codes to (outcome, reason).
 * Gate B2: the headless compute test uses distinct exit codes per failing
 * step so the report can say exactly what failed. Host-unit-testable.
 * Exit codes: 0=pass, 9=no Vulkan loader, 10=instance, 11=no devices,
 * 12=device, 13=compute.
 */
internal fun vkComputeExitMeaning(exit: Int): Pair<TestOutcome, String> = when (exit) {
    0 -> TestOutcome.REACHED_MENU to
        "PASS: instance + device enumeration + vector-add compute verified"
    9 -> TestOutcome.PREREQ_MISSING to
        "Vulkan loader (libvulkan.so.1) not found in guest — environmental, not a GPU failure"
    10 -> TestOutcome.CRASH to
        "vkCreateInstance failed or instance entry points missing"
    11 -> TestOutcome.CRASH to
        "no physical devices enumerated by the Vulkan loader"
    12 -> TestOutcome.CRASH to
        "no compute queue family or vkCreateDevice failed"
    13 -> TestOutcome.CRASH to
        "compute failed (shader module / pipeline / buffers / dispatch / readback verify)"
    else -> TestOutcome.CRASH to "unexpected exit code"
}

data class CompatReportData(
    val schemaVersion: Int = CompatReport.SCHEMA_VERSION,
    val generatedAtMs: Long,
    val environment: EnvironmentInfo,
    val results: List<TestResult>,
    /** Null when every stage passed or was skipped. */
    val firstFailure: FirstFailure? = null,
)
