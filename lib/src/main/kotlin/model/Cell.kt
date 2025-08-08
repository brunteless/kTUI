@file:Suppress("unused")

package model


data class Cell(
    val char: Char,
    val style: AnsiStyle,
) {

    companion object {
        val EMPTY = Cell(
            char = ' ',
            style = AnsiStyle.DEFAULT,
        )
    }
}
