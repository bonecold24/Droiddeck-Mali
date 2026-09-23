package com.steamdeck.launcher.input

/** The control surface shown on a secondary Android display during a Linux session. */
enum class SecondScreenMode(val id: String, val label: String) {
    NONE("none", "None"),
    KEYBOARD_TRACKPAD("keyboard-trackpad", "Keyboard + trackpad"),
    TERMINAL("terminal", "Terminal"),
}

data class SecondScreenDisplay(val id: Int, val label: String)
