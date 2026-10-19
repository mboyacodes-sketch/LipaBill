package com.lipabill.app.ui.util

import kotlin.math.roundToInt

/**
 * Cursor and selection for a Send / Pay field, independent of Compose.
 * [start] and [end] may differ while text is selected.
 */
data class SheetCaret(
    val text: String = "",
    val start: Int = 0,
    val end: Int = text.length
) {
    fun insert(chunk: String, sanitize: (String) -> String = ::sanitizeSheetLine): SheetCaret {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        val raw = text.replaceRange(from, to, chunk)
        val clean = sanitize(raw)
        val cursor = (from + chunk.length).coerceIn(0, clean.length)
        return SheetCaret(clean, cursor, cursor)
    }

    fun backspace(sanitize: (String) -> String = ::sanitizeSheetLine): SheetCaret {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        if (from != to) {
            val clean = sanitize(text.removeRange(from, to))
            val cursor = from.coerceIn(0, clean.length)
            return SheetCaret(clean, cursor, cursor)
        }
        if (from == 0) return this
        val clean = sanitize(text.removeRange(from - 1, from))
        val cursor = (from - 1).coerceIn(0, clean.length)
        return SheetCaret(clean, cursor, cursor)
    }

    fun applyUser(
        nextText: String,
        selStart: Int,
        selEnd: Int,
        sanitize: (String) -> String = ::sanitizeSheetLine
    ): SheetCaret {
        val clean = sanitize(nextText)
        val max = clean.length
        return if (clean == nextText) {
            SheetCaret(clean, selStart.coerceIn(0, max), selEnd.coerceIn(0, max))
        } else {
            val cursor = selEnd.coerceIn(0, max)
            SheetCaret(clean, cursor, cursor)
        }
    }
}

/** One line. Newlines from a paste become spaces so the field stays single-line. */
fun sanitizeSheetLine(raw: String): String =
    raw.replace('\r', ' ').replace('\n', ' ')

/**
 * Scroll offset that keeps [cursorX] inside the visible width.
 * Null when the caret is already on screen.
 */
fun caretScrollTarget(
    cursorX: Float,
    viewportPx: Int,
    currentPx: Int,
    padPx: Float
): Int? {
    if (viewportPx <= 0) return null
    val pad = padPx.coerceAtMost(viewportPx / 3f)
    val left = currentPx.toFloat()
    val right = left + viewportPx
    val raw = when {
        cursorX < left + pad -> cursorX - pad
        cursorX > right - pad -> cursorX - viewportPx + pad
        else -> return null
    }
    return raw.roundToInt()
}
