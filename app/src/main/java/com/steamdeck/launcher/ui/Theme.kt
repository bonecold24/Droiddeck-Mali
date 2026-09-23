package com.steamdeck.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.steamdeck.launcher.session.SessionPrefs

/**
 * The app's colours, in the icon's three: black, white and its blue. Three ways to deal them
 * out, chosen under Setup › Theme. [primary] is what the selection pill and the Play button are
 * made of; [signal] is the small accent for rules, rings, dots and the eyebrow - the same colour
 * as [primary] in the blue themes, the blue itself in Paper, where the primary is white.
 */
class Palette(
    val id: String, val label: String, val detail: String,
    val background: Color, val surface: Color, val surfaceVariant: Color, val line: Color, val line2: Color,
    val onBackground: Color, val onSurfaceVariant: Color,
    val primary: Color, val primary2: Color, val onPrimary: Color, val signal: Color,
    val good: Color, val error: Color = Color(0xFFFF8A80),
)

object Themes {
    const val PAPER = "paper"
    const val ICON_BLUE = "blue"
    const val ELECTRIC = "electric"

    val all: List<Palette> = listOf(
        Palette(
            PAPER, "Paper on black", "White is the primary, the icon's blue is the signal for rules, rings and dots.",
            background = Color(0xFF000000), surface = Color(0xFF0F0F10), surfaceVariant = Color(0xFF191A1C), line = Color(0xFF262729), line2 = Color(0xFF343638),
            onBackground = Color(0xFFF9F9F9), onSurfaceVariant = Color(0xFF8E9196),
            primary = Color(0xFFF9F9F9), primary2 = Color(0xFFD9DADD), onPrimary = Color(0xFF000000), signal = Color(0xFF136CE0),
            good = Color(0xFF4CD37F),
        ),
        Palette(
            ICON_BLUE, "Icon blue", "Black ground, white type, the blue for everything selected, focused or pressed.",
            background = Color(0xFF050608), surface = Color(0xFF0E1116), surfaceVariant = Color(0xFF161B23), line = Color(0xFF1F2630), line2 = Color(0xFF2A3340),
            onBackground = Color(0xFFF2F4F7), onSurfaceVariant = Color(0xFF8A93A0),
            primary = Color(0xFF136CE0), primary2 = Color(0xFF0B4FB0), onPrimary = Color(0xFFFFFFFF), signal = Color(0xFF136CE0),
            good = Color(0xFF4CD37F),
        ),
        Palette(
            ELECTRIC, "Electric navy", "The blue brighter, on surfaces that lean navy instead of grey.",
            background = Color(0xFF070A10), surface = Color(0xFF0F1522), surfaceVariant = Color(0xFF16203A), line = Color(0xFF1E2A47), line2 = Color(0xFF2A3A5E),
            onBackground = Color(0xFFEEF3FF), onSurfaceVariant = Color(0xFF8B9AB8),
            primary = Color(0xFF2E86FF), primary2 = Color(0xFF136CE0), onPrimary = Color(0xFFFFFFFF), signal = Color(0xFF2E86FF),
            good = Color(0xFF4CD37F),
        ),
    )

    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: all[0]
}

val LocalPalette = staticCompositionLocalOf { Themes.byId(Themes.PAPER) }

/** The app's theme; with no [theme] given, the one saved in the preferences. The app is never light. */
@Composable
fun SteamDeckTheme(theme: String? = null, content: @Composable () -> Unit) {
    val id = theme ?: SessionPrefs.theme(LocalContext.current)
    val p = Themes.byId(id)
    val scheme = darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, secondary = p.onSurfaceVariant,
        background = p.background, onBackground = p.onBackground,
        surface = p.surface, onSurface = p.onBackground,
        surfaceVariant = p.surfaceVariant, onSurfaceVariant = p.onSurfaceVariant,
        error = p.error,
    )
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
