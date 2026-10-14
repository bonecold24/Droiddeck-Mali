package com.droiddeck.launcher.comptest

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.BuildConfig
import com.droiddeck.launcher.core.Hashes
import com.droiddeck.launcher.gpu.LinuxVulkanDriverManager
import com.droiddeck.launcher.gpu.SocNames
import com.droiddeck.launcher.runtime.GuestCommand
import com.droiddeck.launcher.runtime.LinuxRuntime
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File

/**
 * On-device [CompatTestRunner.TestExecutor].
 *
 * - System checks run inside the Linux userland via [GuestCommand] (proot):
 *   vkcube, vulkaninfo, and the D3D9/D3D11 sample cubes. These paths are
 *   NEEDS-REAL-MALI: written against the runtime's guest-command API but never
 *   executed on a Mali device.
 * - Game tests: not-installed → SKIPPED (graceful). Installed games need the
 *   unattended session-launch harness; until it is validated on-device this
 *   branch returns ERROR with a clear note instead of guessing (NEEDS-REAL-MALI).
 * - Works without any Steam login: only system-level tests and already-installed
 *   games are ever attempted.
 */
class CompatTestExecutor(
    private val context: Context,
    private val installedGameIds: Set<Long>,
    private val environment: () -> EnvironmentInfo,
) : CompatTestRunner.TestExecutor {

    override fun execute(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        if (!CompatTestSuite.isInstalled(spec, installedGameIds)) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.SKIPPED, emptyList(), "Not installed — skipped gracefully",
            )
        }
        return when (spec.kind) {
            TestKind.SYSTEM_CHECK -> runSystemCheck(spec, ctl)
            TestKind.GAME_TEST -> runGameTest(spec, ctl)
        }
    }

    fun currentEnvironment(): EnvironmentInfo = environment()

    // ------------------------------------------------------------------ system

    private fun runSystemCheck(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        if (!LinuxRuntime.isInstalled(context)) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.ERROR, emptyList(), "Linux runtime not installed — cannot run guest checks",
            )
        }
        val argv = guestArgv(spec.id)
            ?: return CompatTestRunner.TestRunOutcome(
                TestOutcome.ERROR, emptyList(), "Unknown system check '${spec.id}'",
            )
        val lines = ArrayList<String>()
        val logName = "comptest-${spec.id}"
        val exit: Int = try {
            GuestCommand.run(context, argv, logName = logName) { line ->
                if (ctl.cancelled.get()) throw CompatCancelled()
                synchronized(lines) { if (lines.size < 4000) lines.add(line) }
            }
        } catch (e: CompatCancelled) {
            return CompatTestRunner.TestRunOutcome(TestOutcome.ERROR, emptyList(), "Cancelled by user")
        } catch (t: Throwable) {
            // GuestCommand failures surface as exceptions: record CRASH, don't propagate —
            // the runner would also isolate it, but an explicit outcome keeps the note clear.
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.CRASH, emptyList(),
                "Guest command failed: ${t.javaClass.simpleName}: ${t.message}".take(500),
            )
        }
        val frames = parseFrameTimes(lines)
        val outcome = when {
            spec.id == "vulkaninfo" && exit == 0 -> TestOutcome.REACHED_MENU
            exit == 0 && frames.isNotEmpty() -> TestOutcome.REACHED_MENU
            exit == 0 -> TestOutcome.REACHED_MENU
            else -> TestOutcome.CRASH
        }
        val note = buildString {
            append("exit=$exit")
            if (frames.isNotEmpty()) append(", ${frames.size} frame samples")
            if (spec.id == "vulkaninfo") append("; full capture in $logName.log")
        }
        return CompatTestRunner.TestRunOutcome(outcome, frames, note)
    }

    /**
     * Guest command lines per system check. NEEDS-REAL-MALI: binary names and
     * display-server assumptions (X/Wayland inside the session) are unverified.
     */
    private fun guestArgv(checkId: String): List<String>? = when (checkId) {
        // vkcube: run N frames offscreen-ish; parse its fps readout into frame times.
        "vkcube" -> listOf("/usr/bin/vkcube", "--c", "600")
        "vulkaninfo" -> listOf("/usr/bin/vulkaninfo")
        // D3D9/D3D11 sample cubes: expected under the compat-test payload dir in the
        // userland image; exact paths confirmed on first device run.
        "d3d9-cube" -> listOf("/opt/comptest/d3d9-cube")
        "d3d11-cube" -> listOf("/opt/comptest/d3d11-cube")
        else -> null
    }

    /**
     * Best-effort frame-time extraction from vkcube/DXVK-HUD style output.
     * Accepts "60.0 fps" lines (converted to per-frame ms) and "frame: 16.6 ms" lines.
     * Pure — host-unit-tested.
     */
    internal fun parseFrameTimes(lines: List<String>): List<Double> =
        FrameLogParser.parseFrameTimes(lines)

    // ------------------------------------------------------------------ games

    private fun runGameTest(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        // NEEDS-REAL-MALI: unattended game launch + menu/gameplay detection is not
        // validated. Returning ERROR (not guessing REACHED_MENU) keeps the report honest.
        return CompatTestRunner.TestRunOutcome(
            TestOutcome.ERROR, emptyList(),
            "Unattended game-launch harness not yet validated on-device (NEEDS-REAL-MALI); " +
                "game '${spec.label}' left untested rather than guessed",
        )
    }

    private class CompatCancelled : RuntimeException()

    companion object {
        /** Builds the report environment block from app + driver state. No Steam login needed. */
        fun environmentOf(context: Context): EnvironmentInfo {
            val driver = activeDriver(context)
            return EnvironmentInfo(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_LABEL})",
                appCommit = BuildConfig.SOURCE_COMMIT,
                driverPackage = driver,
                protonVersion = defaultProtonLabel(context),
                kbaseUapi = KbaseUapi.probe(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                soc = SocNames.name(SocNames.model(), ""),
            )
        }

        private fun activeDriver(context: Context): DriverIdentity {
            val id = runCatching { SessionPrefs.linuxDriver(context) }.getOrNull().orEmpty()
            if (id.isEmpty()) {
                return DriverIdentity(
                    name = "runtime default",
                    version = "bundled with Linux runtime",
                    binarySha256 = "n/a (runtime-bundled)",
                    vulkanApi = "unknown — see vulkaninfo capture",
                )
            }
            val mgr = LinuxVulkanDriverManager(context)
            val lib = runCatching {
                File(mgr.getDriverDir(id), mgr.getLibraryName(id))
                    .takeIf { it.isFile }
                    ?.let { Hashes.sha256(it) }
            }.getOrNull()
            return DriverIdentity(
                name = runCatching { mgr.getDriverName(id) }.getOrDefault(id),
                version = runCatching { mgr.getDriverVersion(id) }.getOrDefault(""),
                binarySha256 = lib ?: "unreadable",
                vulkanApi = "unknown — see vulkaninfo capture",
            )
        }

        private fun defaultProtonLabel(context: Context): String =
            runCatching {
                com.droiddeck.launcher.session.ProtonDefault.selectedId(
                    context,
                    com.droiddeck.launcher.session.ComponentsManager.snapshot(context).protons.map { it.proton },
                )
            }.getOrNull() ?: "unknown"
    }
}

/** kbase CSF UAPI version probe. Best-effort; never assumed. */
object KbaseUapi {
    fun probe(): String {
        // The UAPI version is negotiated over the kbase ioctl; there is no stable
        // sysfs node for it. Report honestly rather than guessing (e.g. Samsung's 1.21).
        val candidates = listOf(
            "/sys/module/mali_kbase/version",
            "/sys/devices/platform/mali_kbase/uapi_version",
        )
        for (p in candidates) {
            val v = runCatching { File(p).readText().trim() }.getOrNull()
            if (!v.isNullOrEmpty()) return v
        }
        return "unknown (kbase ioctl probe not implemented; see vulkaninfo capture)"
    }
}
