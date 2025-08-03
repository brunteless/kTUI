package model

sealed interface InputEvent {
    data class Key(
        val key: KeyCode,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false,
        val char: Char? = null
    ) : InputEvent

    data class Paste(val text: String) : InputEvent

    data class Resize(val cols: Int, val rows: Int) : InputEvent
}

enum class KeyCode {
    Enter, Backspace, Tab, Escape,
    Left, Right, Up, Down,
    Home, End, PageUp, PageDown,
    Insert, Delete,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    Char // generic character key
}

data class Size(val cols: Int, val rows: Int)