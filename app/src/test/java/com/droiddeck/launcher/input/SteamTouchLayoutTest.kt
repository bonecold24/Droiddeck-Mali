package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 6: touch->input for the on-screen Steam controls. Pins the default
 * element positions (the 1280x720-normalized layout table) and the
 * elementsFor selection logic that merges saved layouts with defaults.
 */
class SteamTouchLayoutTest {

    @Test fun layoutIdFallsBackToOne() {
        assertEquals(1, SteamTouchConfig.layoutIdOf(0))
        assertEquals(1, SteamTouchConfig.layoutIdOf(-3))
        assertEquals(2, SteamTouchConfig.layoutIdOf(2))
    }

    @Test fun faceButtonsSitInTheBottomRightDiamond() {
        val a = SteamTouchConfig.defaultElement(SteamTouchConfig.A, emptySet())!!
        assertEquals(1083f / 1280, a.x, 1e-6f)
        assertEquals(607f / 720, a.y, 1e-6f)
        assertTrue(a.visible)
        val y = SteamTouchConfig.defaultElement(SteamTouchConfig.Y, emptySet())!!
        assertEquals(1083f / 1280, y.x, 1e-6f)
        assertEquals(447f / 720, y.y, 1e-6f)
    }

    @Test fun faceButtonsShiftUpWhenTheRightStickIsPresent() {
        val without = SteamTouchConfig.defaultElement(SteamTouchConfig.A, emptySet())!!
        val with = SteamTouchConfig.defaultElement(SteamTouchConfig.A, setOf(SteamTouchConfig.JOYSTICK_RIGHT))!!
        assertEquals(without.y - 0.30f, with.y, 1e-6f)
        assertEquals(without.x, with.x, 1e-6f)
    }

    @Test fun dpadMovesWhenTheLeftStickIsPresent() {
        val without = SteamTouchConfig.defaultElement(SteamTouchConfig.DPAD, emptySet())!!
        val with = SteamTouchConfig.defaultElement(SteamTouchConfig.DPAD, setOf(SteamTouchConfig.JOYSTICK_LEFT))!!
        assertTrue(without.x != with.x || without.y != with.y)
        assertEquals(0.147f, with.x, 1e-6f)
        assertEquals(0.47f, with.y, 1e-6f)
    }

    @Test fun unknownElementTypesHaveNoDefault() {
        assertNull(SteamTouchConfig.defaultElement(999, emptySet()))
    }

    @Test fun macrosLineUpInARow() {
        val first = SteamTouchConfig.defaultElement(SteamTouchConfig.MACRO_0, emptySet())!!
        val second = SteamTouchConfig.defaultElement(SteamTouchConfig.MACRO_0 + 1, emptySet())!!
        assertEquals(first.y, second.y, 1e-6f)
        assertEquals(first.x + 0.057f, second.x, 1e-4f)
    }

    @Test fun elementsForFallsBackToDefaultsWithNoSavedLayout() {
        val config = SteamTouchConfig.Config(null, null, null, emptyMap())
        val elements = SteamTouchConfig.elementsFor(config, actionSet = 1, layers = emptyList())
        val types = elements.map { it.type }.toSet()
        // DEFAULT_SET plus the always-present steam/thumb/keyboard controls.
        for (type in listOf(
            SteamTouchConfig.DPAD, SteamTouchConfig.A, SteamTouchConfig.B, SteamTouchConfig.X,
            SteamTouchConfig.Y, SteamTouchConfig.SELECT, SteamTouchConfig.START,
            SteamTouchConfig.STEAM, SteamTouchConfig.THUMB, SteamTouchConfig.KEYBOARD,
        )) assertTrue("missing default element $type", type in types)
        assertTrue(elements.all { it.visible })
        assertEquals(elements.map { it.type }, elements.map { it.type }.sorted())
    }

    @Test fun elementsForPrefersTheSavedLayoutPosition() {
        val moved = SteamTouchConfig.Element(SteamTouchConfig.A, true, 0.9f, 0.1f)
        val layouts = SteamTouchConfig.Layouts(
            listOf(SteamTouchConfig.Layout(1, listOf(moved), null, null)),
            byteArrayOf(),
        )
        val config = SteamTouchConfig.Config(null, null, layouts, emptyMap())
        val a = SteamTouchConfig.elementsFor(config, 1, emptyList()).first { it.type == SteamTouchConfig.A }
        assertEquals(0.9f, a.x, 1e-6f)
        assertEquals(0.1f, a.y, 1e-6f)
    }

    @Test fun elementsForDropsElementsTheSavedLayoutHides() {
        val hidden = SteamTouchConfig.Element(SteamTouchConfig.B, false, 0f, 0f)
        val layouts = SteamTouchConfig.Layouts(
            listOf(SteamTouchConfig.Layout(1, listOf(hidden), null, null)),
            byteArrayOf(),
        )
        val config = SteamTouchConfig.Config(null, null, layouts, emptyMap())
        val types = SteamTouchConfig.elementsFor(config, 1, emptyList()).map { it.type }
        assertTrue(SteamTouchConfig.B !in types)
        assertTrue(SteamTouchConfig.A in types)
    }
}
