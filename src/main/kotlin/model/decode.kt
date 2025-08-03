package model


internal sealed interface Decoded {
    data class Key(
        val code: KeySym,
        val ctrl: Boolean,
        val alt: Boolean,
        val shift: Boolean
    ) : Decoded

    data class Paste(val text: String) : Decoded

    data class Resize(val cols: Int, val rows: Int) : Decoded
}

internal sealed interface KeySym {
    data object Enter : KeySym
    data object Backspace : KeySym
    data object Tab : KeySym
    data object Escape : KeySym
    data object Left : KeySym
    data object Right : KeySym
    data object Up : KeySym
    data object Down : KeySym
    data object Home : KeySym
    data object End : KeySym
    data object PageUp : KeySym
    data object PageDown : KeySym
    data object Insert : KeySym
    data object Delete : KeySym
    data class Function(val n: Int) : KeySym
    data class Char(val c: kotlin.Char) : KeySym
}

internal class EscapeDecoder {
    private val sb = StringBuilder()
    private var inPaste = false

    fun feed(
        bytes: ByteArray,
        off: Int,
        len: Int,
        emit: (Decoded) -> Unit
    ) {
        var i = off
        val end = off + len
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            if (inPaste) {
                // bracketed paste ends with ESC [ 2 0 0 ~
                if (b == 0x1B) {
                    // Peek ahead safely
                    val remain = end - i
                    if (remain >= 6 &&
                        bytes[i + 1] == '['.code.toByte() &&
                        bytes[i + 2] == '2'.code.toByte() &&
                        bytes[i + 3] == '0'.code.toByte() &&
                        bytes[i + 4] == '0'.code.toByte() &&
                        bytes[i + 5] == '~'.code.toByte()
                    ) {
                        emit(Decoded.Paste(sb.toString()))
                        sb.setLength(0)
                        inPaste = false
                        i += 6
                        continue
                    }
                }
                sb.append(b.toChar())
                i++
                continue
            }

            when (b) {
                0x1B -> { // ESC
                    val consumed = parseEscape(bytes, i, end, emit)
                    i += consumed
                }
                0x7F -> { // DEL as backspace
                    emit(Decoded.Key(KeySym.Backspace, ctrl = false, alt = false, shift = false))
                    i++
                }
                0x08 -> { // BS
                    emit(Decoded.Key(KeySym.Backspace, ctrl = false, alt = false, shift = false))
                    i++
                }
                0x0D -> { // CR -> Enter
                    emit(Decoded.Key(KeySym.Enter, ctrl = false, alt = false, shift = false))
                    i++
                }
                0x09 -> { // Tab
                    emit(Decoded.Key(KeySym.Tab, ctrl = false, alt = false, shift = false))
                    i++
                }
                else -> {
                    // UTF-8 decode minimally for printable
                    val cp = decodeUtf8(bytes, i, end).also { i = it.next }
                    val ch = Character.toChars(cp.codePoint).first()
                    val key = when (cp.codePoint) {
                        0x1B -> { // ESC fallback
                            Decoded.Key(KeySym.Escape, ctrl = false, alt = false, shift = false)
                        }
                        in 32..0x10FFFF -> {
                            // map control-modifiers for ASCII letters
                            val ctrl = false
                            Decoded.Key(KeySym.Char(ch), ctrl = ctrl, alt = false, shift = false)
                        }
                        else -> Decoded.Key(KeySym.Char(ch), ctrl = false, alt = false, shift = false)
                    }
                    emit(key)
                }
            }
        }
    }

    private fun parseEscape(
        bytes: ByteArray,
        i: Int,
        end: Int,
        emit: (Decoded) -> Unit
    ): Int {
        if (i + 1 >= end) return 1
        if (bytes[i + 1] == '['.code.toByte()) {
            // CSI
            // Check for bracketed paste start: ESC [ 2 0 0 ~
            if (i + 6 <= end &&
                bytes[i + 2] == '2'.code.toByte() &&
                bytes[i + 3] == '0'.code.toByte() &&
                bytes[i + 4] == '0'.code.toByte() &&
                bytes[i + 5] == '~'.code.toByte()
            ) {
                inPaste = true
                return 6
            }
            // Arrows and common keys
            if (i + 3 <= end) {
                when (bytes[i + 2].toInt()) {
                    'A'.code -> { emit(Decoded.Key(KeySym.Up, ctrl = false, alt = false, shift = false)); return 3 }
                    'B'.code -> { emit(Decoded.Key(KeySym.Down, ctrl = false, alt = false, shift = false)); return 3 }
                    'C'.code -> { emit(Decoded.Key(KeySym.Right, ctrl = false, alt = false, shift = false)); return 3 }
                    'D'.code -> { emit(Decoded.Key(KeySym.Left, ctrl = false, alt = false, shift = false)); return 3 }
                    'H'.code -> { emit(Decoded.Key(KeySym.Home, false, alt = false, shift = false)); return 3 }
                    'F'.code -> { emit(Decoded.Key(KeySym.End, false, alt = false, shift = false)); return 3 }
                }
            }
            // CSI sequences like ESC [ 1 ~ (Home), 3 ~ (Del), etc.
            // Minimal parse of numbers then final
            var j = i + 2
            var num = 0
            var hasNum = false
            while (j < end) {
                val c = bytes[j].toInt()
                if (c in '0'.code..'9'.code) {
                    hasNum = true
                    num = num * 10 + (c - '0'.code)
                    j++
                } else {
                    break
                }
            }
            if (j < end && bytes[j] == '~'.code.toByte() && hasNum) {
                val kc = when (num) {
                    1, 7 -> KeySym.Home
                    4, 8 -> KeySym.End
                    2 -> KeySym.Insert
                    3 -> KeySym.Delete
                    5 -> KeySym.PageUp
                    6 -> KeySym.PageDown
                    15 -> KeySym.Function(5)
                    17 -> KeySym.Function(6)
                    18 -> KeySym.Function(7)
                    19 -> KeySym.Function(8)
                    20 -> KeySym.Function(9)
                    21 -> KeySym.Function(10)
                    23 -> KeySym.Function(11)
                    24 -> KeySym.Function(12)
                    else -> null
                }
                if (kc != null) {
                    emit(Decoded.Key(kc, ctrl = false, alt = false, shift = false))
                    return (j - i) + 1
                }
            }
            // Unhandled CSI: consume 2 chars to avoid stalls
            return minOf(3, end - i)
        } else if (bytes[i + 1] in 'O'.code.toByte()..'O'.code.toByte()) {
            // SS3 for F1-F4 common in some terms: ESC O P..S
            if (i + 2 < end) {
                val kc = when (bytes[i + 2].toInt()) {
                    'P'.code -> KeySym.Function(1)
                    'Q'.code -> KeySym.Function(2)
                    'R'.code -> KeySym.Function(3)
                    'S'.code -> KeySym.Function(4)
                    else -> null
                }
                if (kc != null) {
                    emit(Decoded.Key(kc, ctrl = false, alt = false, shift = false))
                    return 3
                }
            }
            return minOf(3, end - i)
        } else {
            // ESC as Alt-modifier for next char (Alt+key)
            if (i + 2 <= end) {
                val cp = decodeUtf8(bytes, i + 1, end)
                val ch = Character.toChars(cp.codePoint).first()
                emit(Decoded.Key(KeySym.Char(ch), ctrl = false, alt = true, shift = false))
                return (cp.next - i)
            }
            return 1
        }
    }

    private data class Cp(val codePoint: Int, val next: Int)

    private fun decodeUtf8(bytes: ByteArray, i: Int, end: Int): Cp {
        val b0 = bytes[i].toInt() and 0xFF
        if (b0 < 0x80) return Cp(b0, i + 1)
        if (b0 and 0xE0 == 0xC0 && i + 1 < end) {
            val b1 = bytes[i + 1].toInt() and 0x3F
            val cp = ((b0 and 0x1F) shl 6) or b1
            return Cp(cp, i + 2)
        }
        if (b0 and 0xF0 == 0xE0 && i + 2 < end) {
            val b1 = bytes[i + 1].toInt() and 0x3F
            val b2 = bytes[i + 2].toInt() and 0x3F
            val cp = ((b0 and 0x0F) shl 12) or (b1 shl 6) or b2
            return Cp(cp, i + 3)
        }
        if (b0 and 0xF8 == 0xF0 && i + 3 < end) {
            val b1 = bytes[i + 1].toInt() and 0x3F
            val b2 = bytes[i + 2].toInt() and 0x3F
            val b3 = bytes[i + 3].toInt() and 0x3F
            val cp = ((b0 and 0x07) shl 18) or (b1 shl 12) or (b2 shl 6) or b3
            return Cp(cp, i + 4)
        }
        // Fallback: treat as single byte
        return Cp(b0, i + 1)
    }
}