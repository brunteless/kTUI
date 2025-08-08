package model

sealed interface InputEvent {
    data class Key(val key: Char): InputEvent
    data object SIGINT: InputEvent
    data object SIGWINCH: InputEvent
}