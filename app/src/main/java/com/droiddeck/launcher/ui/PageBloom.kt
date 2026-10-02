package com.droiddeck.launcher.ui

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// A page that opens from a control (the cog beside Play) blooms out of it: the control's own
// rounded box stretches out until it is the pane, and shrinks back into the control on the way
// out. With a controller the focus ring rides that edge (FocusGlide.transit); by touch the bloom
// draws the same edge itself.

/** Where a page opened from: the control's bounds and corner radius, in px. */
internal class Origin(val bounds: Rect, val corner: Float) {
    fun translate(by: Offset) = Origin(bounds.translate(by), corner)
}

internal object PageOrigin {
    private var origin: Origin? = null
    private var at = 0L

    /** The control a page is about to open from, in root coordinates. */
    fun mark(o: Origin) {
        origin = o
        at = SystemClock.uptimeMillis()
    }

    /** The control just pressed, once: stale after a second, so a page opened another way never blooms. */
    fun take(): Origin? = origin.takeIf { SystemClock.uptimeMillis() - at < 1_000 }.also { origin = null }
}

/** The bloom's box at [p] (0 = the control, 1 = the whole of [size]), corner and all, moved by [shift]. */
internal fun bloomBox(size: Size, from: Origin, p: Float, shift: Offset = Offset.Zero): RoundRect {
    val b = from.bounds
    fun mix(a: Float, z: Float) = a + (z - a) * p
    return RoundRect(
        Rect(mix(b.left, 0f), mix(b.top, 0f), mix(b.right, size.width), mix(b.bottom, size.height)).translate(shift),
        CornerRadius(mix(from.corner, 0f)),
    )
}

/**
 * Clips to the bloom's box at [progress], filled with [fill] so it reads even over a page that
 * draws no ground of its own, and traced with [rim] when the focus ring is not there to do it.
 * At 1 nothing is clipped or drawn.
 */
internal fun Modifier.bloom(progress: () -> Float, from: Origin, fill: Color, rim: () -> Color?): Modifier =
    graphicsLayer {
        val p = progress()
        if (p >= 1f) {
            clip = false
            return@graphicsLayer
        }
        shape = BoxAt(bloomBox(size, from, p))
        clip = true
    }.drawWithContent {
        val p = progress()
        // Solid while it grows; it gives way to the pane's own backdrop over the last stretch, by
        // when the page it covered has faded out underneath.
        if (p < 1f) drawRect(fill, alpha = ((1f - p) / 0.3f).coerceIn(0f, 1f))
        drawContent()
        val r = rim()
        if (p < 1f && r != null) {
            val w = 2.dp.toPx()
            val box = bloomBox(size, from, p)
            drawRoundRect(
                r.copy(alpha = r.alpha * (1f - p * p)), Offset(box.left + w / 2f, box.top + w / 2f), Size(box.width - w, box.height - w),
                CornerRadius((box.topLeftCornerRadius.x - w / 2f).coerceAtLeast(0f)), style = Stroke(w),
            )
        }
    }

private class BoxAt(private val box: RoundRect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Rounded(box)
}
