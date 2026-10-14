package com.droiddeck.launcher.frontend

import com.droiddeck.launcher.core.GameEnvironment
import com.droiddeck.launcher.core.MiniJson
import java.io.File

/**
 * Per-game EULA acceptance. The EULA dialog is shown once per game: the first
 * launch is gated on [isAccepted], and accepting records the timestamp.
 * A corrupt store is treated as "not accepted" (safe side) and repaired on
 * the next accept.
 */
object GameEula {
    const val FILE = "eula.json"

    data class Acceptance(val appId: String, val acceptedAtMs: Long)

    private fun read(dir: File): MutableMap<String, Long> {
        val f = File(dir, FILE)
        if (!f.isFile) return mutableMapOf()
        return try {
            val root = MiniJson.parse(f.readText()) as? Map<*, *> ?: return mutableMapOf()
            root.entries.mapNotNull { (k, v) ->
                if (k is String && v is Number && GameEnvironment.validScope(k)) k to v.toLong() else null
            }.toMap().toMutableMap()
        } catch (_: MiniJson.JsonException) {
            mutableMapOf()
        }
    }

    private fun write(dir: File, records: Map<String, Long>) {
        dir.mkdirs()
        val target = File(dir, FILE)
        val staged = File(dir, "$FILE.staged")
        staged.writeText(MiniJson.stringify(records.mapValues { it.value }))
        if (!staged.renameTo(target)) {
            staged.delete()
            throw java.io.IOException("could not write $FILE")
        }
    }

    fun isAccepted(dir: File, appId: String): Boolean =
        GameEnvironment.validScope(appId) && read(dir).containsKey(appId)

    fun acceptance(dir: File, appId: String): Acceptance? {
        if (!GameEnvironment.validScope(appId)) return null
        val at = read(dir)[appId] ?: return null
        return Acceptance(appId, at)
    }

    fun accept(dir: File, appId: String, atMs: Long = System.currentTimeMillis()) {
        require(GameEnvironment.validScope(appId)) { "bad appId $appId" }
        val records = read(dir)
        records[appId] = atMs
        write(dir, records)
    }

    fun revoke(dir: File, appId: String): Boolean {
        if (!GameEnvironment.validScope(appId)) return false
        val records = read(dir)
        if (records.remove(appId) == null) return false
        write(dir, records)
        return true
    }
}
