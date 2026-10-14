package com.droiddeck.launcher.frontend

import android.content.Context
import android.content.Intent

object GameLaunchIntent {
    const val ACTION = "com.droiddeck.launcher.LAUNCH_GAME"
    const val EXTRA_APP_ID = "app_id"
    const val EXTRA_GAME_SOURCE = "game_source"
    private const val GAMENATIVE_ACTION = "app.gamenative.LAUNCH_GAME"

    fun accepts(action: String?): Boolean = action == Intent.ACTION_VIEW || action == ACTION || action == GAMENATIVE_ACTION

    fun read(context: Context, intent: Intent): String? = when (intent.action) {
        Intent.ACTION_VIEW -> parseSteamUri(intent) ?: GameFiles.readIntent(context, intent)
        ACTION, GAMENATIVE_ACTION -> readId(intent)
        else -> null
    }

    /**
     * steam://rungameid/<id> (and steam://run/<id>) links. Pure parsing lives
     * in [parseSteamUriParts] so it is unit-testable on a host JVM.
     */
    internal fun parseSteamUri(intent: Intent): String? = runCatching {
        val uri = intent.data ?: return null
        parseSteamUriParts(uri.scheme, uri.host, uri.pathSegments)
    }.getOrNull()

    internal fun parseSteamUriParts(scheme: String?, host: String?, pathSegments: List<String>): String? {
        if (!scheme.equals("steam", ignoreCase = true)) return null
        if (!host.equals("rungameid", ignoreCase = true) && !host.equals("run", ignoreCase = true)) return null
        return pathSegments.firstOrNull()?.takeIf(GameLaunchLink::validId)
    }

    @Suppress("DEPRECATION")
    internal fun readId(intent: Intent): String? = runCatching {
        if (intent.action != ACTION && intent.action != GAMENATIVE_ACTION) return null
        val source = intent.extras?.get(EXTRA_GAME_SOURCE)
        if (source != null && (source !is String || !source.equals("STEAM", ignoreCase = true))) return null
        val id = when (val value = intent.extras?.get(EXTRA_APP_ID)) {
            is Int -> value.takeIf { it > 0 }?.toString()
            is String -> value.takeIf(GameLaunchLink::validId)
            else -> null
        }
        id
    }.getOrNull()
}
