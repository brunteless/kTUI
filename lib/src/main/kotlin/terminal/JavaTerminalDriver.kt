package terminal


import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import model.InputEvent
import model.Size
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import org.jline.terminal.Size as JSize


class JavaTerminalDriver(
    private val encoding: Charset = Charsets.UTF_8,
) : TerminalDriver {

    private val terminal: Terminal =
        TerminalBuilder.builder()
            .system(true)
            .jna(true) // helps Windows support
            .encoding(encoding)
            .dumb(false)
            .build()

    private val _inputEvents = MutableSharedFlow<InputEvent>()
    override val inputEvents: SharedFlow<InputEvent> = _inputEvents.asSharedFlow()

    private val writer = OutputStreamWriter(terminal.output(), encoding)
    private var inAlt = false
    private var inRaw = false
    private var bracketedPaste = false

    private val signalToEvent = mapOf(
        Terminal.Signal.INT to InputEvent.SIGINT,
        Terminal.Signal.WINCH to InputEvent.SIGWINCH
    )

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

    override fun write(stringBuilder: StringBuilder) {
        writer.write(stringBuilder.toString())
    }

    override fun flush() {
        writer.flush()
    }

    override fun readLoop(scope: CoroutineScope): Job {
        val reader = terminal.reader()

        for ((signal, event) in signalToEvent) {
            terminal.handle(signal) {
                scope.launch { _inputEvents.emit(event) }
            }
        }

        return scope.launch {
            try {
                while (true) {
                    val input = reader.read()
                    if (input == -1) break

                    _inputEvents.emit(InputEvent.Key(input.toChar()))
                }
            } catch (_: CancellationException) {
                // nothing happens, just close
            } catch (e: Exception) {
                throw e
            } finally {
                reader.close()
            }
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