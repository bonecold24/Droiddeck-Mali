package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.CpuCores
import com.droiddeck.launcher.core.FexSafeTuning
import com.droiddeck.launcher.core.GameEnvironment
import com.droiddeck.launcher.core.MiniJson
import com.droiddeck.launcher.core.PerfPresets
import com.droiddeck.launcher.core.ProtonSmartDefaults
import java.io.File

/**
 * Per-game profile: { game id, Proton version, DXVK variant, FEX/Box64 flags,
 * CPU pinning, env vars, frame limiter, preset, overlay }.
 *
 * Stored as one JSON file per game (`<appId>.json`, schema version 1).
 * Parsing never throws: a corrupt file or corrupt fields fall back to safe
 * defaults, and only a missing/invalid appId yields null (no profile).
 */
data class GameProfile(
    val appId: String,
    val protonVersion: String? = null,
    val dxvkVariant: ProtonSmartDefaults.TranslationLayer? = null,
    val fexFlags: Map<String, String> = emptyMap(),
    val cpuPinning: String = "",
    val envVars: Map<String, String> = emptyMap(),
    val frameLimiterFps: Int? = null,
    val preset: PerfPresets.Preset = PerfPresets.Preset.BALANCED,
    val overlay: PerfOverlay.OverlayState = PerfOverlay.OverlayState(),
) {
    fun toJson(): String = MiniJson.stringify(linkedMapOf<String, Any?>(
        "version" to 1L,
        "appId" to appId,
        "protonVersion" to protonVersion,
        "dxvkVariant" to dxvkVariant?.id,
        "fexFlags" to fexFlags,
        "cpuPinning" to cpuPinning,
        "envVars" to envVars,
        "frameLimiterFps" to frameLimiterFps?.toLong(),
        "preset" to preset.id,
        "overlay" to mapOf(
            "enabled" to overlay.enabled,
            "showFps" to overlay.showFps,
            "showFrameTimes" to overlay.showFrameTimes,
        ),
    ))

    companion object {
        fun parse(text: String): GameProfile? {
            val root = try {
                MiniJson.parse(text) as? Map<*, *> ?: return null
            } catch (_: MiniJson.JsonException) {
                return null
            }
            val appId = (root["appId"] as? String)?.takeIf { GameEnvironment.validScope(it) } ?: return null
            return GameProfile(
                appId = appId,
                protonVersion = (root["protonVersion"] as? String)?.takeIf { ProtonCatalog.byId(it) != null },
                dxvkVariant = ProtonSmartDefaults.TranslationLayer.byId(root["dxvkVariant"] as? String),
                fexFlags = FexSafeTuning.toEnv(stringMap(root["fexFlags"])),
                cpuPinning = CpuCores.format(CpuCores.parseAny((root["cpuPinning"] as? String).orEmpty())),
                envVars = stringMap(root["envVars"]).filterKeys {
                    GameEnvironment.validName(it) && GameEnvironment.supported(it)
                }.filterValues { GameEnvironment.validValue(it) },
                frameLimiterFps = (root["frameLimiterFps"] as? Number)?.toInt()
                    ?.takeIf { PerfPresets.validFrameLimiterFps(it) },
                preset = PerfPresets.Preset.byId(root["preset"] as? String),
                overlay = parseOverlay(root["overlay"]),
            )
        }

        private fun stringMap(value: Any?): Map<String, String> {
            if (value !is Map<*, *>) return emptyMap()
            return value.entries.mapNotNull { (k, v) ->
                if (k is String && v is String) k to v else null
            }.toMap()
        }

        private fun parseOverlay(value: Any?): PerfOverlay.OverlayState {
            val m = value as? Map<*, *> ?: return PerfOverlay.OverlayState()
            fun bool(key: String, default: Boolean): Boolean = when (val v = m[key]) {
                is Boolean -> v
                else -> default
            }
            return PerfOverlay.OverlayState(
                enabled = bool("enabled", false),
                showFps = bool("showFps", true),
                showFrameTimes = bool("showFrameTimes", false),
            )
        }
    }
}

object GameProfileStore {
    private fun file(dir: File, appId: String): File? {
        if (!GameEnvironment.validScope(appId)) return null
        return File(dir, "$appId.json")
    }

    /** Null when no profile exists or the id is invalid; corrupt files yield safe defaults. */
    fun load(dir: File, appId: String): GameProfile? {
        val f = file(dir, appId) ?: return null
        if (!f.isFile) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return GameProfile.parse(text) ?: GameProfile(appId = appId)
    }

    fun save(dir: File, profile: GameProfile) {
        require(GameEnvironment.validScope(profile.appId)) { "bad appId ${profile.appId}" }
        dir.mkdirs()
        val target = File(dir, "${profile.appId}.json")
        val staged = File(dir, "${profile.appId}.json.staged")
        staged.writeText(profile.toJson())
        if (!staged.renameTo(target)) {
            staged.delete()
            throw java.io.IOException("could not write ${target.name}")
        }
    }

    fun delete(dir: File, appId: String): Boolean = (file(dir, appId)?.delete() == true)
}
