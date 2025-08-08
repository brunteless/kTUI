package model


@Suppress("unused")
enum class AnsiColor(code: Int) {
    Black(0),
    Red(1),
    Green(2),
    Yellow(3),
    Blue(4),
    Magenta(5),
    Cyan(6),
    White(7);

    val asForeground = code + 30
    val asBackground = code + 40

    val asBrightForeground = code + 90
    val asBrightBackground = code + 100
}
