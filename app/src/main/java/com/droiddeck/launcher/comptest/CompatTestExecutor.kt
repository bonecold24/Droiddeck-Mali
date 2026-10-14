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
 * - Stage 0 ("stage0-env") runs first: an environment probe recording kernel,
 *   device properties, SELinux, /dev/mali0, ICD selection, driver files,
 *   storage/RAM and logcat AVC denials. It never fails the suite for bad
 *   findings — the findings are data for the stages after it.
 * - System checks run inside the Linux userland via [GuestCommand] (proot):
 *   vkcube, vulkaninfo, and the D3D9/D3D11 sample cubes. These paths are
 *   NEEDS-REAL-MALI: written against the runtime's guest-command API but never
 *   executed on a Mali device.
 * - Every stage records the exact command line, exported env vars, exit code
 *   and the last 100 output lines (Gate A4). The full output lives in the
 *   per-stage log file under <filesDir>/logs/tools/ (see [CompatTestExport]).
 * - Outcome taxonomy is Gate B3: PASS / FAIL_EXIT(code) / NOT_FOUND(127) /
 *   SIGNAL(n) / TIMEOUT / PREREQ_MISSING / SKIPPED. A non-zero exit is never
 *   labelled CRASH.
 * - Game tests: not-installed → SKIPPED (graceful). Installed games need the
 *   unattended session-launch harness; until it is validated on-device this
 *   branch returns PREREQ_MISSING with a clear note instead of guessing
 *   (NEEDS-REAL-MALI).
 * - Works without any Steam login: only system-level tests and already-installed
 *   games are ever attempted.
 */
class CompatTestExecutor(
    private val context: Context,
    private val installedGameIds: Set<Long>,
    private val environment: () -> EnvironmentInfo,
) : CompatTestRunner.TestExecutor {

    @Volatile private var comptestPayloadDone = false
    @Volatile private var payloadResult: PayloadInstallResult? = null

    override fun execute(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        if (!CompatTestSuite.isInstalled(spec, installedGameIds)) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.Skipped, emptyList(), "Not installed — skipped gracefully",
            )
        }
        return when (spec.kind) {
            TestKind.SYSTEM_CHECK -> runSystemCheck(spec, ctl)
            TestKind.GAME_TEST -> runGameTest(spec, ctl)
        }
    }

    fun currentEnvironment(): EnvironmentInfo = environment()

    /** Last payload-install result (null when never attempted). For tests/reporting. */
    fun lastPayloadResult(): PayloadInstallResult? = payloadResult

    // ------------------------------------------------------------------ system

    private fun runSystemCheck(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        if (!LinuxRuntime.isInstalled(context)) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.PrereqMissing("Linux runtime not installed — cannot run guest checks"),
                emptyList(), "PREREQ_MISSING: Linux runtime not installed",
            )
        }
        // BUG-3 diagnostic: the selected Linux driver must be a loadable glibc
        // aarch64 ICD. A bionic selection can never work in the guest — report it
        // as PREREQ_MISSING (not a GPU failure) with the reason spelled out.
        // (The auto-switch to the bundled glibc driver is Gate A2's migration;
        // this check is the diagnostic half and stays correct with or without it.)
        val validation = validateLinuxDriver(context)
        if (validation != null && !validation.usable && spec.id != "stage0-env") {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.PrereqMissing("Selected Linux driver is not guest-usable: ${validation.reason}"),
                emptyList(),
                "PREREQ_MISSING: ${validation.detail}",
            )
        }
        if (spec.id == "stage0-env") return runStage0Probe(spec, ctl, validation)

        val argv = guestArgv(spec.id)
            ?: return CompatTestRunner.TestRunOutcome(
                TestOutcome.PrereqMissing("Unknown system check '${spec.id}'"),
                emptyList(), "PREREQ_MISSING: unknown check id '${spec.id}'",
            )
        // BUG-5: the D3D payload install is no longer silent. If it failed, the
        // D3D stages cannot run — PREREQ_MISSING with the recorded reason.
        val payload = if (spec.id == "d3d9-cube" || spec.id == "d3d11-cube") {
            ensureComptestPayload()
        } else null
        if (payload != null && !payload.ok) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.PrereqMissing("D3D payload install failed: ${payload.reason}"),
                emptyList(),
                "PREREQ_MISSING: payload install to ${payload.guestPath} failed: ${payload.reason}",
            ).withPayload(payload)
        }

        val logName = "comptest-${spec.id}"
        // Gate A2: make sure the Linux driver selection is a usable glibc
        // driver before the guest tries to load it — migrates v2's bionic
        // selection (or a corrupt one) to the bundled glibc driver.
        runCatching { com.droiddeck.launcher.gpu.GlibcDriverBundle.ensureValidSelection(context) }
        // Export the selected Linux Vulkan driver so the guest loader finds it.
        // Without VK_DRIVER_FILES, vkcube/vulkaninfo see no Mali ICD and fail.
        val extraEnv = buildGuestEnv()
        val lines = ArrayList<String>()
        val exit: Int = try {
            GuestCommand.run(context, argv, logName = logName, extraEnv = extraEnv) { line ->
                if (ctl.cancelled.get()) throw CompatCancelled()
                synchronized(lines) { lines.add(line) }
            }
        } catch (e: CompatCancelled) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.Cancelled, emptyList(), "Cancelled by user",
            ).withPayload(payload)
        } catch (t: Throwable) {
            // GuestCommand failures surface as exceptions: no process ran, so this
            // is FailExit(-1) — never mislabelled CRASH (Gate B3).
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.FailExit(-1), emptyList(),
                "Guest command failed before/without a process exit: " +
                    "${t.javaClass.simpleName}: ${t.message}".take(400),
            ).withPayload(payload)
        }
        // Gate B3: classify the raw exit code; a plain non-zero exit is FAIL_EXIT,
        // 127 is NOT_FOUND, >128 is SIGNAL. Never CRASH.
        val outcome = TestOutcome.fromExitCode(exit)
        val tail = lines.takeLast(100)
        val note = buildString {
            append(outcome.label())
            append(": exit=$exit")
            if (tail.isNotEmpty()) {
                val lastMeaningful = tail.takeLast(3).filter { it.isNotBlank() }
                if (lastMeaningful.isNotEmpty()) {
                    append(" — last output: ")
                    append(lastMeaningful.joinToString(" ⏎ ").take(300))
                }
            }
            if (spec.id == "vulkaninfo") append("; full capture in $logName.log")
        }
        return CompatTestRunner.TestRunOutcome(outcome, parseFrameTimes(lines), note)
            .withDiagnostics(
                commandLine = argv.joinToString(" "),
                envVars = extraEnv,
                exitCode = exit,
                outputTail = tail,
                outputLinesTotal = lines.size,
                logFile = "$logName.log",
            )
            .withPayload(payload)
    }

    /** Env exported to every guest stage (recorded per-stage for diagnostics). */
    private fun buildGuestEnv(): Map<String, String> {
        val env = LinkedHashMap<String, String>()
        runCatching {
            val driverId = SessionPrefs.linuxDriver(context).orEmpty()
            if (driverId.isNotEmpty()) {
                val icd = LinuxVulkanDriverManager(context).getIcdPath(driverId)
                if (icd != null) env["VK_DRIVER_FILES"] = icd
                // Pass the driver .so path so the Stage 0 probe can ls -l it.
                val mgr = LinuxVulkanDriverManager(context)
                val so = File(mgr.getDriverDir(driverId), mgr.getLibraryName(driverId))
                if (so.isFile) env["COMPTEST_DRIVER_SO"] = so.absolutePath
            }
        }
        return env
    }

    // ------------------------------------------------------- Stage 0 probe

    /**
     * Gate A4 Stage 0: environment probe. Runs BEFORE vkcube and records everything
     * a later failure might need: kernel, device props, SELinux, /dev/mali0 (+errno),
     * kbase version, ICD selection, driver files, storage/RAM, AVC denials.
     *
     * The probe itself PASSes when it ran — bad findings are data, not failure.
     * Only a missing runtime or a hard error makes it PREREQ_MISSING/FAIL_EXIT.
     */
    private fun runStage0Probe(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
        validation: DriverValidation?,
    ): CompatTestRunner.TestRunOutcome {
        val out = ArrayList<String>()
        val logName = "comptest-stage0-env"
        out += "===== Stage 0 environment probe (Android side) ====="
        out += androidProbeLines()
        out += "===== Stage 0 environment probe (guest side) ====="
        val extraEnv = buildGuestEnv()
        val guestArgv = listOf("/bin/sh", "-c", STAGE0_GUEST_SCRIPT)
        val exit: Int = try {
            GuestCommand.run(context, guestArgv, logName = logName, extraEnv = extraEnv) { line ->
                if (ctl.cancelled.get()) throw CompatCancelled()
                synchronized(out) { out.add(line) }
            }
        } catch (e: CompatCancelled) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.Cancelled, emptyList(), "Cancelled by user",
            )
        } catch (t: Throwable) {
            return CompatTestRunner.TestRunOutcome(
                TestOutcome.FailExit(-1), emptyList(),
                "Stage 0 probe harness failed: ${t.javaClass.simpleName}: ${t.message}".take(400),
            )
        }
        if (validation != null) {
            out += "===== Linux driver validation (BUG-3) ====="
            out += "selectedId=${validation.selectedId}"
            out += "usable=${validation.usable}"
            out += "reason=${validation.reason}"
            out += "detail=${validation.detail}"
        }
        val outcome = if (exit == 0) TestOutcome.Pass else TestOutcome.fromExitCode(exit)
        return CompatTestRunner.TestRunOutcome(
            outcome, emptyList(),
            "Stage 0 probe ${outcome.label()}: environment recorded; see $logName.log",
        ).withDiagnostics(
            commandLine = guestArgv.joinToString(" "),
            envVars = extraEnv,
            exitCode = exit,
            outputTail = out.takeLast(100),
            outputLinesTotal = out.size,
            logFile = "$logName.log",
        )
    }

    /** Android-side probe lines: getprop, SELinux, AVC denials. Best-effort. */
    private fun androidProbeLines(): List<String> {
        val lines = ArrayList<String>()
        fun sh(cmd: String): String = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        }.getOrDefault("<unavailable>")
        lines += "getprop ro.build.version.release=${sh("getprop ro.build.version.release")}"
        lines += "getprop ro.build.version.sdk=${sh("getprop ro.build.version.sdk")}"
        lines += "getprop ro.build.fingerprint=${sh("getprop ro.build.fingerprint")}"
        lines += "getprop ro.build.display.id=${sh("getprop ro.build.display.id")}"
        lines += "getprop ro.hardware=${sh("getprop ro.hardware")}"
        lines += "getprop ro.hardware.chipname=${sh("getprop ro.hardware.chipname")}"
        lines += "getprop ro.product.model=${sh("getprop ro.product.model")}"
        lines += "getprop ro.product.manufacturer=${sh("getprop ro.product.manufacturer")}"
        lines += "Build.MANUFACTURER=${Build.MANUFACTURER} Build.MODEL=${Build.MODEL} " +
            "Build.DEVICE=${Build.DEVICE} Build.HARDWARE=${Build.HARDWARE}"
        lines += "SELinux getenforce=${sh("getenforce")}"
        val avc = sh("logcat -d -b all 2>/dev/null | grep -i 'avc.*denied' | tail -20")
        lines += if (avc.isBlank() || avc == "<unavailable>") {
            "avc denied lines: none captured"
        } else {
            "avc denied lines:\n$avc"
        }
        return lines
    }

    // ------------------------------------------------------------------ games

    private fun runGameTest(
        spec: TestSpec,
        ctl: CompatTestRunner.RunControl,
    ): CompatTestRunner.TestRunOutcome {
        // NEEDS-REAL-MALI: unattended game launch + menu/gameplay detection is not
        // validated. PREREQ_MISSING (not ERROR) keeps the report honest: the harness
        // prerequisite is missing, so this is not a GPU verdict.
        return CompatTestRunner.TestRunOutcome(
            TestOutcome.PrereqMissing(
                "Unattended game-launch harness not yet validated on-device (NEEDS-REAL-MALI)",
            ),
            emptyList(),
            "PREREQ_MISSING: game '${spec.label}' left untested — launch harness unavailable",
        )
    }

    private class CompatCancelled : RuntimeException()

    companion object {
        /**
         * Guest-side Stage 0 probe script. Best-effort: every section prints
         * something even when a tool is missing. VK_DRIVER_FILES and
         * COMPTEST_DRIVER_SO arrive via extraEnv (see buildGuestEnv).
         */
        const val STAGE0_GUEST_SCRIPT = """
echo "--- uname ---"; uname -r 2>&1
echo "--- /dev/mali0 ---"; ls -l /dev/mali0 2>&1
if command -v python3 >/dev/null 2>&1; then
python3 -c "
import os, errno
try:
    fd = os.open('/dev/mali0', os.O_RDWR)
    print('mali0 open: OK fd=%d' % fd)
    os.close(fd)
except OSError as e:
    print('mali0 open: FAIL errno=%d (%s)' % (e.errno, errno.errorcode.get(e.errno, '?')))
" 2>&1
else
  echo "python3: not found; fallback open test via head"
  head -c 0 /dev/mali0 2>&1 && echo "mali0 open(head): OK" || echo "mali0 open(head): FAIL exit=${'$'}?"
fi
echo "--- kbase version nodes ---"
for p in /sys/module/mali_kbase/version /sys/devices/platform/mali_kbase/uapi_version; do echo "${'$'}p: ${'$'}(cat "${'$'}p" 2>&1)"; done
echo "--- ICD selection ---"; echo "VK_DRIVER_FILES=${'$'}VK_DRIVER_FILES"
if [ -n "${'$'}VK_DRIVER_FILES" ]; then echo "--- ICD json ---"; cat "${'$'}VK_DRIVER_FILES" 2>&1 | head -30; fi
echo "--- glibc driver .so ---"; ls -l "${'$'}COMPTEST_DRIVER_SO" 2>&1
echo "--- storage ---"; df -h / /data 2>&1 | head -10
echo "--- memory ---"; (free -m 2>/dev/null || cat /proc/meminfo 2>/dev/null | head -5) 2>&1
echo "--- vulkan loader files ---"; ls /usr/share/vulkan/icd.d/ 2>&1 | head; ls /etc/vulkan/icd.d/ 2>&1 | head
"""

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
                driverValidation = validateLinuxDriver(context),
            )
        }

        /**
         * BUG-3 diagnostic: is the selected Linux driver actually loadable in the
         * guest? Checks (a) the manager's ABI metadata via isLinuxRuntimeUsable,
         * (b) the ICD json exists and parses, (c) the .so is an AArch64 ELF and
         * does not look bionic (no liblog.so NEEDED). Returns null when no Linux
         * driver is selected at all.
         */
        fun validateLinuxDriver(context: Context): DriverValidation? {
            val id = runCatching { SessionPrefs.linuxDriver(context) }.getOrNull().orEmpty()
            if (id.isEmpty()) return null
            val mgr = LinuxVulkanDriverManager(context)
            fun invalid(reason: String, detail: String) =
                DriverValidation(selectedId = id, usable = false, reason = reason, detail = detail)
            if (!runCatching { mgr.isLinuxRuntimeUsable(id) }.getOrDefault(false)) {
                val abi = runCatching { mgr.getAbi(id) }.getOrDefault("?")
                return invalid(
                    "not-glibc-abi",
                    "Selected Linux driver '$id' reports ABI '$abi' — only glibc drivers " +
                        "load in the Linux guest (bionic .so cannot be dlopen()ed by glibc).",
                )
            }
            val icdPath = runCatching { mgr.getIcdPath(id) }.getOrNull()
            if (icdPath.isNullOrEmpty() || !File(icdPath).isFile) {
                return invalid(
                    "icd-missing",
                    "Selected Linux driver '$id' has no ICD manifest (getIcdPath=$icdPath).",
                )
            }
            val icdText = runCatching { File(icdPath).readText() }.getOrNull()
            if (icdText.isNullOrBlank() || !icdText.contains("library_path")) {
                return invalid(
                    "icd-unparseable",
                    "ICD manifest at $icdPath is missing or has no library_path.",
                )
            }
            val soFile = runCatching {
                File(mgr.getDriverDir(id), mgr.getLibraryName(id))
            }.getOrNull()
            if (soFile == null || !soFile.isFile) {
                return invalid(
                    "so-missing",
                    "Driver library file not found for '$id'.",
                )
            }
            val elfCheck = checkElfAarch64Glibc(soFile)
            if (!elfCheck.first) {
                return invalid("elf-check-failed", elfCheck.second)
            }
            return DriverValidation(
                selectedId = id, usable = true, reason = "ok",
                detail = "Driver '$id' validated: glibc ABI, ICD present, ${soFile.name} " +
                    "is AArch64 ELF (${soFile.length()} bytes).",
            )
        }

        /**
         * Minimal ELF check, pure Kotlin: magic + EM_AARCH64 + not bionic.
         * Returns (ok, detail). Bionic heuristic: a NEEDED entry for liblog.so,
         * which only exists on Android.
         */
        internal fun checkElfAarch64Glibc(so: File): Pair<Boolean, String> {
            return runCatching {
                so.inputStream().use { inp ->
                    val hdr = ByteArray(64)
                    var read = 0
                    while (read < 64) {
                        val n = inp.read(hdr, read, 64 - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read < 20) return false to "File too small for ELF header."
                    if (!(hdr[0] == 0x7f.toByte() && hdr[1] == 'E'.code.toByte() &&
                            hdr[2] == 'L'.code.toByte() && hdr[3] == 'F'.code.toByte())
                    ) return false to "Not an ELF file (bad magic)."
                    val machine = (hdr[18].toInt() and 0xff) or ((hdr[19].toInt() and 0xff) shl 8)
                    if (machine != 183) return false to "Not AArch64 (e_machine=$machine)."
                    // Scan the whole file for a bionic-only NEEDED entry.
                    val bytes = so.readBytes()
                    val needle = "liblog.so".toByteArray()
                    outer@ for (i in 0..bytes.size - needle.size) {
                        for (j in needle.indices) {
                            if (bytes[i + j] != needle[j]) continue@outer
                        }
                        // Found "liblog.so"; require a NUL terminator to avoid false positives.
                        if (i + needle.size < bytes.size && bytes[i + needle.size] == 0.toByte()) {
                            return false to
                                "Links liblog.so — this is a bionic (Android) build, " +
                                "not loadable by glibc in the Linux guest."
                        }
                    }
                    return true to "AArch64 ELF, no bionic liblog.so dependency."
                }
            }.getOrDefault(false to "ELF check threw an exception.")
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

    /**
     * Guest command lines per system check. NEEDS-REAL-MALI: binary names and
     * display-server assumptions (X/Wayland inside the session) are unverified.
     */
    private fun guestArgv(checkId: String): List<String>? {
        return when (checkId) {
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
     * guest's /opt/comptest. Runs once per process. BUG-5: returns the result
     * instead of swallowing failures — callers record it in the report.
     */
    internal fun ensureComptestPayload(): PayloadInstallResult {
        payloadResult?.let { return it }
        synchronized(this) {
            payloadResult?.let { return it }
            val result = doEnsureComptestPayload()
            payloadResult = result
            return result
        }
    }

    private fun doEnsureComptestPayload(): PayloadInstallResult {
        val guestPath = "/opt/comptest"
        try {
            val am = context.assets
            val files = am.list("comptest")
                ?: return PayloadInstallResult(false, guestPath, reason = "APK assets/comptest missing (am.list returned null)")
            if (files.isEmpty()) {
                return PayloadInstallResult(false, guestPath, reason = "APK assets/comptest is empty — D3D cubes not bundled")
            }
            // Stage to a host-visible dir, then move into the guest via proot.
            val stage = File(context.filesDir, "comptest-payload").apply { mkdirs() }
            for (n in files) {
                am.open("comptest/$n").use { inp ->
                    File(stage, n).outputStream().use { inp.copyTo(it) }
                }
                File(stage, n).setExecutable(true)
            }
            // BUG-4: quote the staged path inside sh -c.
            val exit = GuestCommand.run(
                context,
                listOf(
                    "/bin/sh", "-c",
                    "mkdir -p /opt/comptest && cp -f \"${stage.path}\"/* /opt/comptest/ && chmod +x /opt/comptest/* && ls /opt/comptest",
                ),
                logName = "comptest-payload-install",
            ) { }
            if (exit != 0) {
                return PayloadInstallResult(
                    false, guestPath,
                    reason = "guest install command exited $exit (see comptest-payload-install.log)",
                )
            }
            return PayloadInstallResult(true, guestPath, files.toList())
        } catch (t: Throwable) {
            android.util.Log.w("CompatTest", "comptest payload install failed: ${t.message}")
            return PayloadInstallResult(
                false, guestPath,
                reason = "${t.javaClass.simpleName}: ${t.message}".take(300),
            )
        }
    }

    /**
     * Best-effort frame-time extraction from vkcube/DXVK-HUD style output.
     * Accepts "60.0 fps" lines (converted to per-frame ms) and "frame: 16.6 ms" lines.
     * Pure — host-unit-tested.
     */
    internal fun parseFrameTimes(lines: List<String>): List<Double> =
        FrameLogParser.parseFrameTimes(lines)
}

/** Attaches Gate A4 diagnostics to a [CompatTestRunner.TestRunOutcome]. Pure. */
fun CompatTestRunner.TestRunOutcome.withDiagnostics(
    commandLine: String? = null,
    envVars: Map<String, String>? = null,
    exitCode: Int? = null,
    outputTail: List<String>? = null,
    outputLinesTotal: Int? = null,
    logFile: String? = null,
): CompatTestRunner.TestRunOutcome = copy(
    diagnostics = CompatDiagnostics(
        commandLine = commandLine,
        envVars = envVars,
        exitCode = exitCode,
        outputTail = outputTail?.takeLast(100),
        outputLinesTotal = outputLinesTotal,
        stderrMerged = outputTail != null,
        logFile = logFile,
    ),
)

/** Attaches the D3D payload-install result (BUG-5) to an outcome. Pure. */
fun CompatTestRunner.TestRunOutcome.withPayload(
    payload: PayloadInstallResult?,
): CompatTestRunner.TestRunOutcome = copy(payloadInstall = payload)

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
