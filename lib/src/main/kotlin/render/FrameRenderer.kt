package render

import model.Size
import model.AnsiAttribute
import model.AnsiStyle
import model.Frame
import terminal.TerminalDriver

class FrameRenderer(
    private val terminalDriver: TerminalDriver
) {
    private var previousFrame: Frame? = null
    private var frameSize = terminalDriver.size()
    private val outputBuffer = StringBuilder()

    fun render(frame: Frame) {
        initializeFrameDimensions(terminalDriver.size())
        
        val changedRegions = calculateDifferences(frame)
        if (changedRegions.isEmpty()) return
        
        renderOptimizedChanges(changedRegions, frame)
        flushOutput()
        updatePreviousFrame(frame)
    }

    private fun initializeFrameDimensions(size: Size) {
        if (frameSize != size) {
            frameSize = size
            previousFrame = null
        }
    }

    private fun calculateDifferences(currentFrame: Frame): List<ChangeRegion> {
        val previous = previousFrame ?: return listOf(ChangeRegion(0, currentFrame.size - 1))

        val regions = mutableListOf<ChangeRegion>()
        var changeRegionStart: Int? = null

        for (index in currentFrame.indices) {
            val isCellChanged = isCellDifferent(previous, currentFrame, index)

            when {
                isCellChanged && changeRegionStart == null -> {
                    changeRegionStart = index
                }
                !isCellChanged && changeRegionStart != null -> {
                    regions.add(ChangeRegion(changeRegionStart, index - 1))
                    changeRegionStart = null
                }
            }
        }

        changeRegionStart?.let { start ->
            regions.add(ChangeRegion(start, currentFrame.size - 1))
        }

        return regions
    }

    private fun isCellDifferent(previous: Frame, current: Frame, index: Int): Boolean {
        return index >= previous.size || current[index] != previous[index]
    }

    private fun renderOptimizedChanges(regions: List<ChangeRegion>, frame: Frame) {
        var currentStyle = AnsiStyle.DEFAULT
        
        for (region in regions) {
            moveCursorTo(region.start)
            
            for (index in region.start..region.end) {
                val cell = frame[index]

                if (currentStyle != cell.style) {
                    applyStyle(currentStyle, cell.style)
                    currentStyle = cell.style
                }

                outputBuffer.append(cell.char)

                if (shouldBreakLine(index)) {
                    val nextIndex = index + 1
                    if (nextIndex <= region.end) moveCursorTo(nextIndex)
                }
            }
        }
    }

    private fun indexToPosition(index: Int): Pair<Int, Int> {
        val row = index / frameSize.columns
        val col = index % frameSize.columns
        return Pair(row, col)
    }

    private fun moveCursorTo(index: Int) {
        val (row, col) = indexToPosition(index)
        outputBuffer.append("\u001B[${row + 1};${col + 1}H")
    }

    private fun shouldResetStyles(current: AnsiStyle, target: AnsiStyle): Boolean {
        return target.attribute == AnsiAttribute.Reset || 
               (current.attribute != AnsiAttribute.Reset && target.attribute != current.attribute)
    }

    private fun applyStyle(current: AnsiStyle, target: AnsiStyle) {
        val isFullReset = shouldResetStyles(current, target)

        if (isFullReset) outputBuffer.append("\u001B[0m")

        if (target.foreground != current.foreground || isFullReset) {
            outputBuffer.append("\u001B[${target.foreground.asForeground}m")
        }
        if (target.background != current.background || isFullReset) {
            outputBuffer.append("\u001B[${target.background.asBackground}m")
        }
        if (target.attribute != current.attribute || isFullReset) {
            outputBuffer.append("\u001B[${target.attribute.asAttribute}m")
        }
    }

    private fun shouldBreakLine(index: Int): Boolean {
        return (index + 1) % frameSize.columns == 0
    }

    private fun flushOutput() {
        if (outputBuffer.isNotEmpty()) {
            terminalDriver.write(outputBuffer)
            terminalDriver.flush()
            outputBuffer.clear()
        }
    }

    private fun updatePreviousFrame(frame: Frame) {
        previousFrame = frame.copyOf()
    }

    private data class ChangeRegion(val start: Int, val end: Int)
    
}