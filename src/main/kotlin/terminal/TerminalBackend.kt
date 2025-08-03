package terminal

import kotlinx.coroutines.channels.SendChannel
import model.InputEvent
import model.Size


interface TerminalBackend : AutoCloseable {
    fun size(): Size
    fun enterAltScreen()
    fun exitAltScreen()
    fun enableRawMode()
    fun disableRawMode()
    fun hideCursor()
    fun showCursor()
    fun enableBracketedPaste()
    fun disableBracketedPaste()
    fun clearScreen() // optional convenience
    fun write(bytes: ByteArray)
    fun flush()
    suspend fun readLoop(out: SendChannel<InputEvent>)
}
