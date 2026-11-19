package com.lipabill.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Version
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a QR [BitMatrix] with rounded finder / alignment eyes and soft module corners.
 * Works on module-sized matrices or ZXing's pixel-scaled output.
 */
object RoundedQrRenderer {

    fun toImageBitmap(
        matrix: BitMatrix,
        sizePx: Int,
        foregroundColor: Int = AndroidColor.BLACK,
        backgroundColor: Int = AndroidColor.WHITE
    ): ImageBitmap = render(matrix, sizePx, foregroundColor, backgroundColor).asImageBitmap()

    fun render(
        matrix: BitMatrix,
        sizePx: Int,
        foregroundColor: Int = AndroidColor.BLACK,
        backgroundColor: Int = AndroidColor.WHITE
    ): Bitmap {
        val modules = if (matrix.width <= 200) matrix else toModuleMatrix(matrix)
        val dim = modules.width
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val cell = sizePx.toFloat() / dim
        val quiet = quietModules(modules)
        val dataRadius = cell * 0.35f
        val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.FILL
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }

        val finderOrigins = finderOrigins(dim, quiet)
        val alignmentCenters = alignmentCenters(dim, quiet)

        for (y in 0 until dim) {
            for (x in 0 until dim) {
                if (!modules.get(x, y)) continue
                if (inFinder(x, y, finderOrigins)) continue
                if (inAlignment(x, y, alignmentCenters)) continue
                val left = x * cell
                val top = y * cell
                canvas.drawRoundRect(
                    RectF(left, top, left + cell, top + cell),
                    dataRadius,
                    dataRadius,
                    fg
                )
            }
        }

        for ((ox, oy) in finderOrigins) {
            drawEye(
                canvas = canvas,
                left = ox * cell,
                top = oy * cell,
                modules = 7,
                cell = cell,
                fg = fg,
                bg = bg,
                outerRadiusFactor = 1.55f,
                midRadiusFactor = 1.15f,
                innerRadiusFactor = 0.95f
            )
        }
        for ((cx, cy) in alignmentCenters) {
            drawEye(
                canvas = canvas,
                left = (cx - 2) * cell,
                top = (cy - 2) * cell,
                modules = 5,
                cell = cell,
                fg = fg,
                bg = bg,
                outerRadiusFactor = 1.2f,
                midRadiusFactor = 0.85f,
                innerRadiusFactor = 0.55f
            )
        }
        return bitmap
    }

    private fun drawEye(
        canvas: Canvas,
        left: Float,
        top: Float,
        modules: Int,
        cell: Float,
        fg: Paint,
        bg: Paint,
        outerRadiusFactor: Float,
        midRadiusFactor: Float,
        innerRadiusFactor: Float
    ) {
        val size = modules * cell
        val outerR = cell * outerRadiusFactor
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), outerR, outerR, fg)
        val inset1 = cell
        val midR = cell * midRadiusFactor
        canvas.drawRoundRect(
            RectF(left + inset1, top + inset1, left + size - inset1, top + size - inset1),
            midR,
            midR,
            bg
        )
        val inset2 = cell * 2
        val innerR = cell * innerRadiusFactor
        canvas.drawRoundRect(
            RectF(left + inset2, top + inset2, left + size - inset2, top + size - inset2),
            innerR,
            innerR,
            fg
        )
    }

    private fun quietModules(matrix: BitMatrix): Int {
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) return minOf(x, y)
            }
        }
        return 0
    }

    private fun finderOrigins(dim: Int, quiet: Int): List<Pair<Int, Int>> = listOf(
        quiet to quiet,
        (dim - quiet - 7) to quiet,
        quiet to (dim - quiet - 7)
    )

    private fun inFinder(x: Int, y: Int, origins: List<Pair<Int, Int>>): Boolean =
        origins.any { (ox, oy) -> x in ox until ox + 7 && y in oy until oy + 7 }

    private fun alignmentCenters(dim: Int, quiet: Int): List<Pair<Int, Int>> {
        val dataModules = dim - 2 * quiet
        if (dataModules < 25) return emptyList() // version 1 has no alignment
        val versionNumber = ((dataModules - 17) / 4).coerceIn(1, 40)
        val centers = runCatching {
            Version.getVersionForNumber(versionNumber).alignmentPatternCenters
        }.getOrNull() ?: return emptyList()
        if (centers.isEmpty()) return emptyList()
        val finders = finderOrigins(dim, quiet)
        val out = ArrayList<Pair<Int, Int>>()
        for (cy in centers) {
            for (cx in centers) {
                val x = quiet + cx
                val y = quiet + cy
                // Skip patterns that collide with finders.
                if (inFinder(x, y, finders)) continue
                if (inFinder(x - 2, y - 2, finders)) continue
                if (inFinder(x + 2, y + 2, finders)) continue
                out.add(x to y)
            }
        }
        return out
    }

    private fun inAlignment(x: Int, y: Int, centers: List<Pair<Int, Int>>): Boolean =
        centers.any { (cx, cy) -> x in (cx - 2)..(cx + 2) && y in (cy - 2)..(cy + 2) }

    /** Collapses a pixel-scaled ZXing matrix back to one bit per module. */
    private fun toModuleMatrix(pixel: BitMatrix): BitMatrix {
        val modulePx = inferModulePx(pixel)
        val dim = max(1, pixel.width / modulePx)
        val out = BitMatrix(dim)
        val half = modulePx / 2
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val sx = (x * modulePx + half).coerceIn(0, pixel.width - 1)
                val sy = (y * modulePx + half).coerceIn(0, pixel.height - 1)
                if (pixel.get(sx, sy)) out.set(x, y)
            }
        }
        return out
    }

    /** Top row of the finder is 7 modules of black — first black run ÷ 7. */
    private fun inferModulePx(matrix: BitMatrix): Int {
        var x0 = -1
        var y0 = -1
        loop@ for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    x0 = x
                    y0 = y
                    break@loop
                }
            }
        }
        if (x0 < 0) return 1
        var run = 0
        while (x0 + run < matrix.width && matrix.get(x0 + run, y0)) run++
        return max(1, (run / 7f).roundToInt().coerceAtLeast(1))
    }
}
