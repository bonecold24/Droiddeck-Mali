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

    @Volatile private var comptestPayloadDone = false

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
        // Gate B2: vkcube needs a display server. Without one, its failure is
        // environmental — report PREREQ_MISSING ("display not available"),
        // never a GPU failure. Headless GPU bring-up is covered by vk-compute-test.
        if (spec.id == "vkcube") {
            val display = probeDisplay()
            if (!display.reachable) {
                return CompatTestRunner.TestRunOutcome(
                    TestOutcome.PREREQ_MISSING, emptyList(),
                    "display not available — ${display.detail}. " +
                        "GPU bring-up is tested separately by vk-compute-test; " +
                        "this is not a GPU/driver failure.",
                )
            }
        }
        val argv = guestArgv(spec.id)
            ?: return CompatTestRunner.TestRunOutcome(
                TestOutcome.ERROR, emptyList(), "Unknown system check '${spec.id}'",
            )
        val lines = ArrayList<String>()
        val logName = "comptest-${spec.id}"
        // Export the selected Linux Vulkan driver so the guest loader finds it.
        // Without VK_DRIVER_FILES, vkcube/vulkaninfo see no Mali ICD and fail.
        val extraEnv = runCatching {
            val driverId = SessionPrefs.linuxDriver(context).orEmpty()
            if (driverId.isEmpty()) emptyMap()
            else {
                val icd = com.droiddeck.launcher.gpu.LinuxVulkanDriverManager(context)
                    .getIcdPath(driverId)
                if (icd != null) mapOf("VK_DRIVER_FILES" to icd) else emptyMap()
            }
        }.getOrDefault(emptyMap())
        val exit: Int = try {
            GuestCommand.run(context, argv, logName = logName, extraEnv = extraEnv) { line ->
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
        // Gate B2: the headless compute test uses distinct exit codes per
        // failing step — surface them plainly instead of a bare "CRASH".
        if (spec.id == "vk-compute-test") {
            return computeTestOutcome(exit, lines)
        }
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
     * Maps vk-compute-test's distinct exit codes to outcomes with plain-language
     * notes. Exit 9 (no Vulkan loader) is environmental; 10–13 are driver/GPU
     * issues at the step named. Never a bare "CRASH".
     */
    private fun computeTestOutcome(
        exit: Int,
        lines: List<String>,
    ): CompatTestRunner.TestRunOutcome {
        val tail = lines.takeLast(5).joinToString(" | ").take(400)
        val (outcome, reason) = vkComputeExitMeaning(exit)
        val note = buildString {
            append("vk-compute-test exit=$exit: $reason")
            if (tail.isNotEmpty()) append("; tail: $tail")
        }.take(500)
        return CompatTestRunner.TestRunOutcome(outcome, emptyList(), note)
    }

    /**
     * Display-server reachability probe for vkcube (Gate B2).
     *
     * Runs inside the guest: checks DISPLAY/WAYLAND_DISPLAY, resolves the
     * socket path, and attempts a real AF_UNIX connect() (via python3 when
     * available, else socket-existence). Exit 0 = reachable, 1 = no display
     * variables, 2 = socket missing or unconnectable.
     */
    private fun probeDisplay(): DisplayProbe {
        val lines = ArrayList<String>()
        val exit = try {
            GuestCommand.run(
                context,
                listOf("/bin/sh", "-c", DISPLAY_PROBE_SCRIPT),
                logName = "comptest-display-probe",
            ) { line -> synchronized(lines) { if (lines.size < 100) lines.add(line) } }
        } catch (t: Throwable) {
            return DisplayProbe(false, "display probe itself failed: ${t.message}".take(300))
        }
        val detail = lines.joinToString("; ").take(400)
        return when (exit) {
            0 -> DisplayProbe(true, "display server reachable ($detail)")
            1 -> DisplayProbe(
                false,
                "no DISPLAY or WAYLAND_DISPLAY in the guest environment ($detail)",
            )
            else -> DisplayProbe(
                false,
                "display socket missing or not connectable (probe exit=$exit; $detail)",
            )
        }
    }

    private data class DisplayProbe(val reachable: Boolean, val detail: String)

    /**
     * Guest command lines per system check. NEEDS-REAL-MALI: binary names and
     * display-server assumptions (X/Wayland inside the session) are unverified.
     */
    private fun guestArgv(checkId: String): List<String>? {
        // D3D cubes and the headless compute test: ensure the payload is
        // installed in the guest first.
        if (checkId == "d3d9-cube" || checkId == "d3d11-cube" || checkId == "vk-compute-test") {
            ensureComptestPayload()
        }
        return when (checkId) {
            // vk-compute-test: headless GPU bring-up; needs no display.
            // Exit codes: 0=pass, 9=no loader, 10=instance, 11=no devices,
            // 12=device, 13=compute. See computeTestOutcome().
            "vk-compute-test" -> listOf("/opt/comptest/vk-compute-test")
            // vkcube: run N frames offscreen-ish; parse its fps readout into frame times.
            "vkcube" -> listOf("/usr/bin/vkcube", "--c", "600")
            "vulkaninfo" -> listOf("/usr/bin/vulkaninfo")
            // D3D9/D3D11 sample cubes: installed to /opt/comptest by ensureComptestPayload().
            "d3d9-cube" -> listOf("/opt/comptest/d3d9-cube")
            "d3d11-cube" -> listOf("/opt/comptest/d3d11-cube")
            else -> null
        }
    }

    /**
     * Copies the D3D Tier-0 payload (wrappers + .exe) from APK assets into the
     * guest's /opt/comptest. Runs once per process; best-effort, never throws.
     */
    private fun ensureComptestPayload() {
        if (comptestPayloadDone) return
        synchronized(this) {
            if (comptestPayloadDone) return
            comptestPayloadDone = true
        }
        try {
            val am = context.assets
            val files = am.list("comptest") ?: return
            if (files.isEmpty()) return
            // Stage to a host-visible dir, then move into the guest via proot.
            val stage = java.io.File(context.filesDir, "comptest-payload").apply { mkdirs() }
            for (n in files) {
                am.open("comptest/$n").use { inp ->
                    java.io.File(stage, n).outputStream().use { inp.copyTo(it) }
                }
                java.io.File(stage, n).setExecutable(true)
            }
            GuestCommand.run(
                context,
                listOf("/bin/sh", "-c",
                    "mkdir -p /opt/comptest && cp -f ${stage.path}/* /opt/comptest/ && chmod +x /opt/comptest/*"),
                logName = "comptest-payload-install",
            ) { }
        } catch (t: Throwable) {
            android.util.Log.w("CompatTest", "comptest payload install failed: ${t.message}")
        }
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
        /**
         * POSIX sh snippet for the display-server reachability probe (Gate B2).
         * Exit 0 = a display server is reachable; 1 = no DISPLAY/WAYLAND_DISPLAY;
         * 2 = socket missing or connect() failed.
         */
        internal const val DISPLAY_PROBE_SCRIPT = """
            probe_sock() {
              if command -v python3 >/dev/null 2>&1; then
                python3 - "$1" 2>/dev/null <<'PYEOF'
            import socket, sys
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.settimeout(3)
            try:
                s.connect(sys.argv[1])
            except Exception as e:
                print("connect failed: %s" % e)
                sys.exit(1)
            finally:
                s.close()
            print("connect ok")
            PYEOF
                return $?
              fi
              [ -S "$1" ]
            }
            if [ -n "$WAYLAND_DISPLAY" ]; then
              case "$WAYLAND_DISPLAY" in
                /*) SOCK="$WAYLAND_DISPLAY" ;;
                *) SOCK="${XDG_RUNTIME_DIR:-/tmp}/$WAYLAND_DISPLAY" ;;
              esac
              echo "wayland socket: $SOCK"
              if probe_sock "$SOCK"; then echo "WAYLAND_REACHABLE"; exit 0;
              else echo "WAYLAND_UNREACHABLE"; exit 2; fi
            elif [ -n "$DISPLAY" ]; then
              DNUM=$(printf '%s' "$DISPLAY" | sed -n 's/.*:\([0-9][0-9]*\)\(\..*\)\{0,1\}$/\1/p')
              if [ -z "$DNUM" ]; then echo "DISPLAY unparsable: $DISPLAY"; exit 2; fi
              SOCK="/tmp/.X11-unix/X$DNUM"
              echo "X socket: $SOCK"
              if probe_sock "$SOCK"; then echo "X_REACHABLE"; exit 0;
              else echo "X_UNREACHABLE"; exit 2; fi
            else
              echo "NO_DISPLAY_VARS"
              exit 1
            fi
        """.trimIndent()

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
                soc = SocNames.label(),
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
