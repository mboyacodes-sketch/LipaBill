package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design review only (skipped by default). Renders every skyline at five times of day:
 *
 *   SKYLINE_GALLERY=1 ./gradlew :app:testPlayDebugUnitTest --tests '*SkylineGalleryRender*'
 *
 * Output: app/build/skyline-gallery/<CITY>.png and all.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class SkylineGalleryRenderTest {

    @Test
    fun renderGallery() {
        assumeTrue(System.getenv("SKYLINE_GALLERY")?.isNotBlank() == true)
        val outDir = File(System.getProperty("user.dir"), "build/skyline-gallery").apply { mkdirs() }
        val hours = listOf(6.2f, 12f, 17.8f, 19.3f, 22f)
        val cellW = 840
        val cellH = 390
        val gap = 10
        val labelH = 34
        val all = Bitmap.createBitmap(hours.size * (cellW + gap) + gap, EventSkyline.entries.size * (cellH + labelH + gap) + gap, Bitmap.Config.ARGB_8888)
        val allCanvas = android.graphics.Canvas(all).apply { drawColor(android.graphics.Color.rgb(24, 24, 28)) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 24f }
        val scope = CanvasDrawScope()
        EventSkyline.entries.forEachIndexed { row, city ->
            val strip = Bitmap.createBitmap(hours.size * (cellW + gap) + gap, cellH + labelH + gap * 2, Bitmap.Config.ARGB_8888)
            val stripCanvas = android.graphics.Canvas(strip).apply { drawColor(android.graphics.Color.rgb(24, 24, 28)) }
            hours.forEachIndexed { col, hour ->
                val cell = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)
                val light = skyAt(hour)
                scope.draw(Density(3f), LayoutDirection.Ltr, Canvas(cell.asImageBitmap()), Size(cellW.toFloat(), cellH.toFloat())) {
                    drawEventSkyline(city, light, kotlin.math.abs("Gallery ${city.name}".hashCode()), EventPassPalette.Default.glows)
                }
                val x = (gap + col * (cellW + gap)).toFloat()
                val label = "${city.name} · ${"%.1f".format(hour)}h ${light.label}"
                stripCanvas.drawText(label, x, gap + 24f, text)
                stripCanvas.drawBitmap(cell, x, (gap + labelH).toFloat(), null)
                val y = gap + row * (cellH + labelH + gap)
                allCanvas.drawText(label, x, y + 24f, text)
                allCanvas.drawBitmap(cell, x, (y + labelH).toFloat(), null)
            }
            File(outDir, "${city.name}.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(outDir, "all.png").outputStream().use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Review sheets: noon and night side by side, four cities per sheet.
        EventSkyline.entries.chunked(4).forEachIndexed { sheetIndex, cities ->
            val sheet = Bitmap.createBitmap(2 * (cellW + gap) + gap, cities.size * (cellH + labelH + gap) + gap, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(sheet).apply { drawColor(android.graphics.Color.rgb(24, 24, 28)) }
            cities.forEachIndexed { row, city ->
                listOf(12f, 22f).forEachIndexed { col, hour ->
                    val cell = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)
                    val light = skyAt(hour)
                    scope.draw(Density(3f), LayoutDirection.Ltr, Canvas(cell.asImageBitmap()), Size(cellW.toFloat(), cellH.toFloat())) {
                        drawEventSkyline(city, light, kotlin.math.abs("Gallery ${city.name}".hashCode()), EventPassPalette.Default.glows)
                    }
                    val x = (gap + col * (cellW + gap)).toFloat()
                    val y = gap + row * (cellH + labelH + gap)
                    c.drawText("${city.name} · ${light.label}", x, y + 24f, text)
                    c.drawBitmap(cell, x, (y + labelH).toFloat(), null)
                }
            }
            File(outDir, "review-${sheetIndex + 1}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
