package com.droiddeck.launcher.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnScreenControlsTest {
    private lateinit var context: Context
    private lateinit var view: OnScreenControls
    private var time = 1000L

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("controller", Context.MODE_PRIVATE).edit().clear().commit()
        view = OnScreenControls(context, null)
        view.layout(0, 0, 1200, 800)
    }

    @Test fun adaptiveStickHidesUntilTouchedAndReturnsToNeutralOnRelease() {
        val left = control("ls")
        assertEquals(0, pixel(value(left, "cx"), value(left, "cy")))
        assertTrue(touch(MotionEvent.ACTION_DOWN, 7 to (540f to 400f)))
        assertEquals(7, field(left, "pressedBy"))
        assertEquals(540f, value(left, "ax"), 0f)
        assertEquals(400f, value(left, "ay"), 0f)
        assertNotEquals(0, pixel(540f, 400f))
        touch(MotionEvent.ACTION_MOVE, 7 to (580f to 420f))
        assertTrue(value(left, "kx") > 0f)
        assertTrue(value(left, "ky") > 0f)
        touch(MotionEvent.ACTION_UP, 7 to (580f to 420f))
        assertEquals(-1, field(left, "pressedBy"))
        assertEquals(0f, value(left, "kx"), 0f)
        assertEquals(0f, value(left, "ky"), 0f)
        assertEquals(0, pixel(540f, 400f))
    }

    @Test fun simultaneousSticksKeepTheirPointersAndReleaseIndependently() {
        val left = control("ls")
        val right = control("rs")
        touch(MotionEvent.ACTION_DOWN, 7 to (540f to 400f))
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 7 to (540f to 400f), 9 to (660f to 400f))
        assertEquals(7, field(left, "pressedBy"))
        assertEquals(9, field(right, "pressedBy"))
        touch(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8), 7 to (540f to 400f), 9 to (660f to 400f), 11 to (500f to 400f))
        assertEquals(7, field(left, "pressedBy"))
        touch(MotionEvent.ACTION_POINTER_UP or (2 shl 8), 7 to (540f to 400f), 9 to (660f to 400f), 11 to (500f to 400f))
        touch(MotionEvent.ACTION_MOVE, 7 to (700f to 400f), 9 to (500f to 400f))
        assertTrue(value(left, "kx") > 0f)
        assertTrue(value(right, "kx") < 0f)
        touch(MotionEvent.ACTION_POINTER_UP, 7 to (700f to 400f), 9 to (500f to 400f))
        assertEquals(-1, field(left, "pressedBy"))
        assertEquals(9, field(right, "pressedBy"))
        touch(MotionEvent.ACTION_CANCEL, 9 to (500f to 400f))
        for (stick in listOf(left, right)) {
            assertEquals(-1, field(stick, "pressedBy"))
            assertEquals(0f, value(stick, "kx"), 0f)
        }
    }

    @Test fun cancelledTouchDoesNotBecomeAStickClick() {
        touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f))
        touch(MotionEvent.ACTION_CANCEL, 1 to (540f to 400f))
        touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f))
        assertEquals(false, field(control("ls"), "clicked"))
        touch(MotionEvent.ACTION_UP, 1 to (540f to 400f))
        touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f))
        assertEquals(true, field(control("ls"), "clicked"))
    }

    @Test fun buttonsTakePriorityOverAdaptiveRegions() {
        val button = control("a")
        touch(MotionEvent.ACTION_DOWN, 3 to (value(button, "cx") to value(button, "cy")))
        assertEquals(3, field(button, "pressedBy"))
        assertEquals(-1, field(control("rs"), "pressedBy"))
    }

    @Test fun fixedSticksAndEditorRemainVisibleAtSavedPositions() {
        ControllerPrefs.setAdaptiveSticks(context, false)
        view.reload()
        val left = control("ls")
        val x = value(left, "cx")
        val y = value(left, "cy")
        assertNotEquals(0, pixel(x, y))
        assertFalse(touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f)))
        touch(MotionEvent.ACTION_DOWN, 1 to (x + 10f to y))
        assertEquals(x, value(left, "ax"), 0f)
        assertEquals(10f, value(left, "kx"), 0f)
        ControllerPrefs.setAdaptiveSticks(context, true)
        view = OnScreenControls(context, null, editing = true)
        view.layout(0, 0, 1200, 800)
        assertNotEquals(0, pixel(value(control("ls"), "cx"), value(control("ls"), "cy")))
    }

    @Test fun buttonsOnlyAndReloadReleaseAdaptiveSticks() {
        touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f))
        touch(MotionEvent.ACTION_MOVE, 1 to (570f to 400f))
        view.reload()
        assertEquals(-1, field(control("ls"), "pressedBy"))
        assertEquals(0f, value(control("ls"), "kx"), 0f)
        view.setButtonsOnly(true)
        assertFalse(touch(MotionEvent.ACTION_DOWN, 1 to (540f to 400f)))
    }

    private fun control(id: String): Any = (field(view, "controls") as List<*>).first { field(it!!, "id") == id }!!
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)!!
    private fun value(owner: Any, name: String) = field(owner, name) as Float

    private fun pixel(x: Float, y: Float): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val color = bitmap.getPixel(x.toInt(), y.toInt())
        bitmap.recycle()
        return color
    }

    private fun touch(action: Int, vararg pointers: Pair<Int, Pair<Float, Float>>): Boolean {
        val properties = pointers.map { (id, _) -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = pointers.map { (_, position) -> MotionEvent.PointerCoords().apply { x = position.first; y = position.second; pressure = 1f; size = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(1000L, time++, action, pointers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        return try { view.onTouchEvent(event) } finally { event.recycle() }
    }
}
