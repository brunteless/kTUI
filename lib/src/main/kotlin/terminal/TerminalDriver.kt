package terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import model.InputEvent
import model.Size


interface TerminalDriver : AutoCloseable {
    val inputEvents: SharedFlow<InputEvent>
    fun size(): Size
    fun enterAltScreen()
    fun exitAltScreen()
    fun enableRawMode()
    fun disableRawMode()
    fun hideCursor()
    fun showCursor()
    fun enableBracketedPaste()
    fun disableBracketedPaste()
    fun clearScreen()
    fun write(stringBuilder: StringBuilder)
    fun flush()
    fun readLoop(scope: CoroutineScope): Job
}
