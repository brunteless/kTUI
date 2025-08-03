package terminal


import model.Decoded
import model.EscapeDecoder
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.channels.SendChannel
import org.jline.reader.EndOfFileException
import org.jline.terminal.Size as JSize
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import model.InputEvent
import model.KeyCode
import model.KeySym
import model.Size
import kotlin.coroutines.coroutineContext

class JavaTerminalBackend(
    private val encoding: Charset = Charsets.UTF_8
) : TerminalBackend {

    private val terminal: Terminal =
        TerminalBuilder.builder()
            .system(true)
            .jna(true) // helps Windows support
            .encoding(encoding)
            .dumb(false)
            .build()

    private val out = terminal.output()
    private val writer = OutputStreamWriter(out, encoding)
    private var inAlt = false
    private var inRaw = false
    private var bracketedPaste = false

    init {
        terminal.handle(Terminal.Signal.INT) { close() }
        terminal.handle(Terminal.Signal.WINCH) { /* JLine will update size */ }
    }

    override fun size(): Size = terminal.size.toTui()

    override fun enterAltScreen() {
        if (!inAlt) {
            // smcup
            terminal.puts(InfoCmp.Capability.enter_ca_mode)
            terminal.flush()
            inAlt = true
        }
    }

    override fun exitAltScreen() {
        if (inAlt) {
            // rmcup
            terminal.puts(InfoCmp.Capability.exit_ca_mode)
            terminal.flush()
            inAlt = false
        }
    }

    override fun enableRawMode() {
        if (!inRaw) {
            terminal.enterRawMode()
            terminal.echo(false)
            inRaw = true
        }
    }

    override fun disableRawMode() {
        if (inRaw) {
            terminal.echo(true)
            // JLine restores attributes when closing; ensure cooked mode
            // by leaving raw mode via closing non-raw reader session
            inRaw = false
        }
    }

    override fun hideCursor() {
        terminal.writer().print("\u001B[?25l")
        terminal.flush()
    }

    override fun showCursor() {
        terminal.writer().print("\u001B[?25h")
        terminal.flush()
    }

    override fun enableBracketedPaste() {
        if (!bracketedPaste) {
            // CSI ? 2004 h
            writer.write("\u001B[?2004h")
            writer.flush()
            bracketedPaste = true
        }
    }

    override fun disableBracketedPaste() {
        if (bracketedPaste) {
            // CSI ? 2004 l
            writer.write("\u001B[?2004l")
            writer.flush()
            bracketedPaste = false
        }
    }

    override fun clearScreen() {
        terminal.puts(InfoCmp.Capability.clear_screen)
        terminal.puts(InfoCmp.Capability.cursor_home)
        terminal.flush()
    }

    override fun write(bytes: ByteArray) {
        out.write(bytes)
    }

    override fun flush() {
        out.flush()
    }

    override suspend fun readLoop(out: SendChannel<InputEvent>) {
        val input = terminal.input()
        val decoder = EscapeDecoder()
        val buf = ByteArray(4096)

        try {
            while (true) {
                // Non-blocking-ish read: JLine input stream is blocking; use available to coalesce
                val available = input.available()
                val read = if (available > 0) {
                    input.read(buf, 0, minOf(available, buf.size))
                } else {
                    val r = input.read()
                    if (r < 0) break
                    buf[0] = r.toByte()
                    1
                }
                if (read < 0) break
                decoder.feed(buf, 0, read) { ev ->
                    when (ev) {
                        is Decoded.Key -> out.trySend(ev.toInputEvent())
                        is Decoded.Paste -> out.trySend(InputEvent.Paste(ev.text))
                        is Decoded.Resize -> out.trySend(
                            InputEvent.Resize(ev.cols, ev.rows)
                        )
                    }
                }
                // Cooperative cancellation point
                if (!coroutineContext.isActive) break
                yield()
            }
        } catch (_: EndOfFileException) {
            // terminal closed
        } catch (_: CancellationException) {
            // normal
        }
    }

    override fun close() {
        try {
            if (bracketedPaste) disableBracketedPaste()
            showCursor()
            if (inAlt) exitAltScreen()
            disableRawMode()
        } finally {
            terminal.close()
        }
    }

    private fun JSize.toTui(): Size = Size(columns, rows)
}

private fun Decoded.Key.toInputEvent(): InputEvent.Key =
    InputEvent.Key(
        key = when (code) {
            KeySym.Enter -> KeyCode.Enter
            KeySym.Backspace -> KeyCode.Backspace
            KeySym.Tab -> KeyCode.Tab
            KeySym.Escape -> KeyCode.Escape
            KeySym.Left -> KeyCode.Left
            KeySym.Right -> KeyCode.Right
            KeySym.Up -> KeyCode.Up
            KeySym.Down -> KeyCode.Down
            KeySym.Home -> KeyCode.Home
            KeySym.End -> KeyCode.End
            KeySym.PageUp -> KeyCode.PageUp
            KeySym.PageDown -> KeyCode.PageDown
            KeySym.Insert -> KeyCode.Insert
            KeySym.Delete -> KeyCode.Delete
            is KeySym.Function -> when (code.n) {
                1 -> KeyCode.F1
                2 -> KeyCode.F2
                3 -> KeyCode.F3
                4 -> KeyCode.F4
                5 -> KeyCode.F5
                6 -> KeyCode.F6
                7 -> KeyCode.F7
                8 -> KeyCode.F8
                9 -> KeyCode.F9
                10 -> KeyCode.F10
                11 -> KeyCode.F11
                else -> KeyCode.F12
            }
            is KeySym.Char -> KeyCode.Char
        },
        ctrl = ctrl,
        alt = alt,
        shift = shift,
        char = (code as? KeySym.Char)?.c
    )
