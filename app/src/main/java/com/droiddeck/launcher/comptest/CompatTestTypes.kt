package com.droiddeck.launcher.comptest

/**
 * Shared data model for the built-in Compatibility Test mode.
 *
 * Pure Kotlin (no Android framework) so every field is host-unit-testable.
 * On-device execution (running vkcube / games) is NEEDS-REAL-MALI.
 */
enum class TestKind { SYSTEM_CHECK, GAME_TEST }

enum class TestOutcome {
    /** Test binary ran and presented its menu / first frame. */
    REACHED_MENU,
    /** A game got past menus into actual gameplay. */
    REACHED_GAMEPLAY,
    /** The test process died (signal / uncaught exception) — suite continues. */
    CRASH,
    /** The test stopped making progress but was still alive (executor-detected). */
    HANG,
    /** Wall-clock timeout enforced by the runner watchdog. */
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
    ERROR,
}

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

data class EnvironmentInfo(
    val appVersion: String,
    val appCommit: String,
    val driverPackage: DriverIdentity,
    val protonVersion: String,
    /** kbase CSF UAPI as read on the device, e.g. "1.21" — never assumed. */
    val kbaseUapi: String,
    val deviceModel: String,
    val soc: String,
)

data class TelemetrySnapshot(
    val cpuTempC: Double?,
    val gpuTempC: Double?,
    val batteryPct: Int?,
    val ramUsedMb: Long?,
    val atMs: Long,
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
)
