package samples


import model.InputEvent
import model.KeyCode
import model.Size
import terminal.JavaTerminalBackend
import terminal.TerminalBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


suspend fun main() = withContext(Dispatchers.Default) {
    val backend = JavaTerminalBackend()
    val events = Channel<InputEvent>(Channel.UNLIMITED)

    try {
        backend.enterAltScreen()
        backend.enableRawMode()
        backend.enableBracketedPaste()
        backend.clearScreen()

        val reader = launch { backend.readLoop(events) }
        val ui = launch { runLoop(backend, events) }

        ui.join()
        reader.cancelAndJoin()
    } finally {
        backend.close()
    }
}

private suspend fun runLoop(
    backend: TerminalBackend,
    events: ReceiveChannel<InputEvent>
) {
    var running = true
    var size: Size = backend.size()
    var tick = 0L

    fun drawStatus() {
        // Simple single-write buffer
        val sb = StringBuilder(256)
        // Move home
        sb.append("\u001B[H")
        sb.append("M1 demo | ")
        sb.append("size: ").append(size.cols).append("x").append(size.rows)
        sb.append(" | tick: ").append(tick)
        sb.append(" | ESC to quit")
        // Clear rest of line
        sb.append("\u001B[K")
        // Move cursor to bottom row, first col, write hint
        sb.append("\u001B[").append(size.rows).append(";1H")
        sb.append("Keys: arrows, text, paste (bracketed), resize")
        sb.append("\u001B[K")
        backend.write(sb.toString().toByteArray())
        backend.flush()
    }

    drawStatus()

    while (running) {
        val ev = events.receive()
        when (ev) {
            is InputEvent.Resize -> {
                size = Size(ev.cols, ev.rows)
                drawStatus()
            }
            is InputEvent.Paste -> {
                tick++
                drawStatus()
            }
            is InputEvent.Key -> {
                // Exit on ESC or Ctrl+C
                if (ev.key == KeyCode.Escape || (ev.ctrl && ev.char?.uppercaseChar() == 'C')
                ) {
                    running = false
                } else {
                    tick++
                    drawStatus()
                }
            }
        }
    }
}