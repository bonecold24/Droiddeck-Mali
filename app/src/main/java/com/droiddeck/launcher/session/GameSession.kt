package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.FexPreset
import com.droiddeck.launcher.core.FexSafeTuning
import com.droiddeck.launcher.core.PerfPresets
import com.droiddeck.launcher.core.ProtonSmartDefaults
import com.droiddeck.launcher.frontend.GameEula
import java.io.File

/**
 * Game launch pipeline orchestrator:
 *
 * EULA gate -> env composition (profile + preset + Mali verdict clamp) ->
 * spawn -> monitor -> stop / crash capture -> clean teardown.
 *
 * Process I/O goes through [GameProcessSpawner] (fake in unit tests,
 * [ProcessBuilderSpawner] on the device); the log tail supplier is
 * SessionLogCapture::tailLines on the device.
 */
class GameSession(
    spawner: GameProcessSpawner,
    private val files: Files,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logTail: () -> List<String> = { emptyList() },
) {
    data class Files(val eulaDir: File, val reportDir: File, val profileDir: File)

    data class LaunchRequest(
        val appId: String,
        val command: List<String>,
        val baseEnv: Map<String, String> = emptyMap(),
        val workDir: File? = null,
        val profile: GameProfile? = null,
        val layer: ProtonSmartDefaults.TranslationLayer = ProtonSmartDefaults.TranslationLayer.DXVK_STOCK,
    )

    sealed interface LaunchOutcome {
        /** The EULA dialog must be shown and accepted before this game may launch. */
        object EulaRequired : LaunchOutcome
        data class Started(val sessionId: String) : LaunchOutcome
        data class Rejected(val reason: String) : LaunchOutcome
    }

    private data class ActiveSession(val sessionId: String, val startedAtMs: Long, val request: LaunchRequest)

    private val processes = GameProcessManager(spawner)
    private val lock = Any()
    private val active = linkedMapOf<String, ActiveSession>()

    fun launch(request: LaunchRequest): LaunchOutcome {
        if (!GameEula.isAccepted(files.eulaDir, request.appId)) return LaunchOutcome.EulaRequired
        if (request.command.isEmpty()) return LaunchOutcome.Rejected("empty command")
        val sessionId = "${request.appId}-${clock()}"
        val env = buildLaunchEnv(request.profile, request.layer, request.baseEnv)
        return try {
            processes.start(request.appId, request.command, env, request.workDir)
            synchronized(lock) { active[request.appId] = ActiveSession(sessionId, clock(), request) }
            LaunchOutcome.Started(sessionId)
        } catch (t: Throwable) {
            LaunchOutcome.Rejected(t.message ?: t.toString())
        }
    }

    fun isRunning(appId: String): Boolean = processes.isRunning(appId)

    /**
     * User-initiated stop: kills the game, reaps the session, writes no
     * crash report. Returns the exit code when known.
     */
    fun stop(appId: String): Int? {
        synchronized(lock) { active.remove(appId) }
        return processes.kill(appId)
    }

    /**
     * Crash check: if the game died on its own, build a crash report (process
     * death + last log tail) and persist it unless the exit was clean.
     * Returns the report, or null when there is nothing to report.
     */
    fun checkCrashed(appId: String): GameCrashReport? {
        val session = synchronized(lock) { active[appId] } ?: return null
        if (processes.isRunning(appId)) return null
        synchronized(lock) { active.remove(appId) }
        val code = processes.exitCodeOf(appId)
        processes.reap()
        val report = GameCrashReport.build(
            appId = appId,
            sessionId = session.sessionId,
            startedAtMs = session.startedAtMs,
            endedAtMs = clock(),
            exit = code?.let { ProcessExit.Code(it) } ?: ProcessExit.Unknown,
            tail = logTail(),
        )
        if (!report.clean) GameCrashReportStore.write(files.reportDir, report)
        return if (report.clean) null else report
    }

    /** Clean exit: kill everything tracked; no orphaned processes remain. */
    fun close() {
        synchronized(lock) { active.clear() }
        processes.killAll()
    }

    companion object {
        /**
         * Compose the launch environment: base <- FEX preset <- safe FEX flags
         * <- profile env vars <- frame limiter <- overlay <- Mali verdict clamp.
         * Later sources win; the Mali clamp always runs last.
         */
        fun buildLaunchEnv(
            profile: GameProfile?,
            layer: ProtonSmartDefaults.TranslationLayer,
            base: Map<String, String>,
        ): Map<String, String> {
            val mapping = profile?.let { PerfPresets.for9300Plus(it.preset) }
                ?: PerfPresets.for9300Plus(PerfPresets.Preset.BALANCED)
            val env = base.toMutableMap()
            env.putAll(FexPreset.environment(mapping.fexPresetId))
            if (profile != null) {
                env.putAll(FexSafeTuning.toEnv(profile.fexFlags))
                env.putAll(profile.envVars)
                val fps = profile.frameLimiterFps ?: mapping.frameLimiterFps
                env.putAll(PerfPresets.frameLimiterEnv(fps, layer, existingDxvkConfig = env["DXVK_CONFIG"]))
                env.putAll(PerfOverlay.env(profile.overlay, layer, existingHud = env["DXVK_HUD"]))
            } else {
                env.putAll(PerfPresets.frameLimiterEnv(mapping.frameLimiterFps, layer,
                    existingDxvkConfig = env["DXVK_CONFIG"]))
            }
            // The clamp never introduces nulls (it only replaces values with the
            // safe constants), and this map has none to begin with.
            return ProtonSmartDefaults.clampEnvForMali(env).mapValues { it.value as String }
        }

        /** Explicit profile pinning wins; otherwise the preset's 9300+ default. */
        fun effectiveCpuPinning(profile: GameProfile?, mapping: PerfPresets.Mapping): String =
            profile?.cpuPinning?.takeIf { it.isNotBlank() } ?: mapping.cpuPinning
    }
}
