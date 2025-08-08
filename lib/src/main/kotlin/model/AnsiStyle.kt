package model

data class AnsiStyle(
    val foreground: AnsiColor,
    val background: AnsiColor,
    val attribute: AnsiAttribute,
) {
    companion object {
        val DEFAULT = AnsiStyle(
            foreground = AnsiColor.White,
            background = AnsiColor.Black,
            attribute = AnsiAttribute.Reset,
        )
    }
}
