package com.lipabill.app.data.tickets

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colours a Wallet pass declares in pass.json (`backgroundColor`, `foregroundColor`,
 * `labelColor`), as opaque ARGB ints. Stored on the ticket as `bg=#RRGGBB;fg=…;lb=…`.
 */
data class PassColors(
    val background: Int? = null,
    val foreground: Int? = null,
    val label: Int? = null
) {
    val isEmpty: Boolean get() = background == null && foreground == null && label == null

    fun serialize(): String? = listOfNotNull(
        background?.let { "bg=${hex(it)}" },
        foreground?.let { "fg=${hex(it)}" },
        label?.let { "lb=${hex(it)}" }
    ).joinToString(";").ifBlank { null }

    companion object {
        /** From the three pass.json strings; unparseable values are dropped. */
        fun fromPassJson(background: String?, foreground: String?, label: String?): PassColors? =
            PassColors(parseCss(background), parseCss(foreground), parseCss(label))
                .takeUnless { it.isEmpty }

        fun parse(stored: String?): PassColors? {
            if (stored.isNullOrBlank()) return null
            val map = stored.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            return PassColors(
                parseCss(map["bg"]),
                parseCss(map["fg"]),
                parseCss(map["lb"])
            ).takeUnless { it.isEmpty }
        }

        private val RGB = Regex(
            """(?i)^\s*rgba?\(\s*(\d{1,3}(?:\.\d+)?)\s*,\s*(\d{1,3}(?:\.\d+)?)\s*,\s*(\d{1,3}(?:\.\d+)?)\s*(?:,\s*[\d.]+\s*)?\)\s*$"""
        )
        private val HEX = Regex("""(?i)^\s*#?([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})\s*$""")

        /** `rgb(r, g, b)` (Apple's documented form), `rgba(...)`, `#RGB`, `#RRGGBB`, `#AARRGGBB`. */
        fun parseCss(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            RGB.matchEntire(raw)?.let { m ->
                val (r, g, b) = m.destructured
                return opaque(channel(r), channel(g), channel(b))
            }
            HEX.matchEntire(raw)?.let { m ->
                val h = m.groupValues[1]
                return when (h.length) {
                    3 -> opaque(
                        h[0].digitToInt(16) * 17,
                        h[1].digitToInt(16) * 17,
                        h[2].digitToInt(16) * 17
                    )
                    6 -> (0xFF000000 or h.toLong(16)).toInt()
                    else -> (0xFF000000 or (h.toLong(16) and 0xFFFFFF)).toInt()
                }
            }
            return null
        }

        private fun channel(v: String): Int = v.toDouble().toInt().coerceIn(0, 255)
        private fun opaque(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        private fun hex(c: Int): String = String.format(Locale.US, "#%06X", c and 0xFFFFFF)

        /** WCAG relative luminance, 0 (black) … 1 (white). */
        fun luminance(c: Int): Double {
            fun lin(v: Int): Double {
                val s = v / 255.0
                return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
        }

        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}
