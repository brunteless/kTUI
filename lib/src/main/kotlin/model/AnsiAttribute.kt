package model


@Suppress("unused")
enum class AnsiAttribute(code: Int) {
    Reset(0),
    IntensityBold(1),
    IntensityFaint(2),
    Italic(3),
    Underline(4),
    BlinkSlow(5),
    BlinkFast(6),
    NegativeOn(7),
    ConcealOn(8),
    StrikethroughOn(9),
    UnderlineDouble(21),
    IntensityBoldOfF(22),
    ItalicOff(23),
    UnderlineOff(24),
    BlinkOff(25),
    NegativeOff(27),
    ConcealOff(28),
    StrikethroughOff(29);

    val asAttribute = code
}
