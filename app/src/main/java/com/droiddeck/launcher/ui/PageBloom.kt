package com.droiddeck.launcher.ui

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

// A page that opens from a control (the cog beside Play) blooms out of it: a circle growing from
// the control until it fills the pane, and folding back into it on the way out.

internal object PageOrigin {
    private var bounds: Rect? = null
    private var at = 0L

    /** The control a page is about to open from, in root coordinates. */
    fun mark(r: Rect) {
        bounds = r
        at = SystemClock.uptimeMillis()
    }

    /** The control just pressed, once: stale after a second, so a page opened another way never blooms. */
    fun take(): Rect? = bounds.takeIf { SystemClock.uptimeMillis() - at < 1_000 }.also { bounds = null }
}

/**
 * Clips to a circle around [origin] (in this element's own coordinates) that grows from [from] px
 * to past the farthest corner as [progress] goes 0 to 1, filled with [fill] so the disc reads even
 * over a page that draws no ground of its own, a [rim] tracing its edge, and a small zoom in from
 * the same point. At 1 nothing is clipped.
 */
internal fun Modifier.bloom(progress: () -> Float, origin: Offset, from: Float, fill: Color, rim: Color): Modifier =
    graphicsLayer {
        val p = progress()
        if (p >= 1f) {
            clip = false
            return@graphicsLayer
        }
        shape = CircleAt(origin, radius(size, origin, from, p))
        clip = true
        val zoom = 0.94f + 0.06f * p
        scaleX = zoom
        scaleY = zoom
        if (size.width > 0f && size.height > 0f) transformOrigin = TransformOrigin(origin.x / size.width, origin.y / size.height)
    }.drawWithContent {
        val p = progress()
        // Solid while it grows; it gives way to the pane's own backdrop over the last stretch, by
        // when the page it covered has faded out underneath.
        if (p < 1f) drawRect(fill, alpha = ((1f - p) / 0.3f).coerceIn(0f, 1f))
        drawContent()
        if (p < 1f) {
            val width = 2.dp.toPx()
            drawCircle(rim.copy(alpha = rim.alpha * (1f - p)), radius(size, origin, from, p) - width / 2f, origin, style = Stroke(width))
        }
    }

private fun radius(size: Size, origin: Offset, from: Float, p: Float): Float {
    val far = max(
        max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
        max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)),
    )
    return from + (far - from) * p
}

private class CircleAt(private val centre: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply { addOval(Rect(centre, radius)) })
}
