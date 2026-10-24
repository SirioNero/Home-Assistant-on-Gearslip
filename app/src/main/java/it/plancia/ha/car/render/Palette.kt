package it.plancia.ha.car.render

/**
 * Notte: il blu profondo di un abitacolo al buio, perché le luci della casa siano l'unica cosa che brilla.
 * Giorno: un grigio azzurro chiaro, senza bianchi abbaglianti.
 */
class Palette(val dark: Boolean) {
    val bgTop = if (dark) 0xFF0B111C.toInt() else 0xFFEEF2F7.toInt()
    val bgBottom = if (dark) 0xFF121A28.toInt() else 0xFFDDE4EE.toInt()
    val tile = if (dark) 0xFF161F2E.toInt() else 0xFFFFFFFF.toInt()
    val tileTop = if (dark) 0xFF1B2535.toInt() else 0xFFFBFCFE.toInt()
    val stroke = if (dark) 0x1AFFFFFF else 0x16000000
    val pressed = if (dark) 0x26FFFFFF else 0x14000000
    val text = if (dark) 0xFFEEF2F7.toInt() else 0xFF131B2A.toInt()
    val muted = if (dark) 0xFF8592A6.toInt() else 0xFF5F6B7E.toInt()
    val glyphOff = if (dark) 0xFF263043.toInt() else 0xFFE3E8EF.toInt()
    val glyphOffInk = if (dark) 0xFF7D889C.toInt() else 0xFF8A95A7.toInt()
    val ok = 0xFF5BD98C.toInt()
    val warn = 0xFFFFC857.toInt()
    val bad = 0xFFFF6B6B.toInt()
}
