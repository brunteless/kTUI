import kotlinx.coroutines.*
import model.InputEvent
import terminal.JavaTerminalDriver
import terminal.TerminalDriver


fun main() {
    val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val terminalDriver: TerminalDriver = JavaTerminalDriver().apply {
        enterAltScreen()
        enableRawMode()
        hideCursor()
        enableBracketedPaste()
    }

    try {

        val uiJob = ioScope.launch { terminalDriver.drawContent() }
        val readJob = terminalDriver.readLoop(ioScope)

        runBlocking {
            uiJob.invokeOnCompletion { readJob.cancel() }
            uiJob.join()
        }

    } catch (e: Exception) {
        println("Exception caught: $e")
    } finally {
        ioScope.cancel()
        terminalDriver.close()
    }
}

suspend fun TerminalDriver.drawContent() {
    try {
        inputEvents.collect { event ->
            val sb = StringBuilder()

            when (event) {
                is InputEvent.Key -> sb.append("Last pressed key: ").append(event.key)
                is InputEvent.SIGINT -> throw InterruptedException("SIGINT received")
                is InputEvent.SIGWINCH -> sb.append("SIGWINCH received, size is now ").append(size())
            }

            clearScreen()
            write(sb)
            flush()
            sb.clear()

        }
    } catch (_: InterruptedException) {
        // the function returns, it's job ends, and the TUI application is closed
        return
    }
}