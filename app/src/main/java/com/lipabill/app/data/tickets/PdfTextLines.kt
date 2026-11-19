package com.lipabill.app.data.tickets

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.max
import kotlin.math.min

/**
 * Reads PDF text as positioned lines (top-down, in points, pages stacked) with the font size
 * of each line, so the event extractor can tell a headline from a label from a value.
 */
internal object PdfTextLines {

    fun read(doc: PDDocument, maxPages: Int = 4): List<TextLine> {
        val out = mutableListOf<TextLine>()
        var pageTop = 0f
        val pages = min(doc.numberOfPages, maxPages)
        for (p in 1..pages) {
            val collector = LineCollector(pageTop)
            collector.sortByPosition = true
            collector.startPage = p
            collector.endPage = p
            collector.getText(doc)
            collector.flush()
            out += collector.lines
            pageTop += doc.getPage(p - 1).mediaBox.height + 20f
        }
        return out
    }

    /**
     * Collects one PDF text line at a time, split into column segments: PDFBox's
     * sortByPosition joins everything on a row, so "Ticket Type:   Location:" would read as one
     * label. A gap wider than ~1.6x the font size starts a new segment.
     */
    private class LineCollector(private val pageTop: Float) : PDFTextStripper() {
        val lines = mutableListOf<TextLine>()

        private class Segment {
            val text = StringBuilder()
            var left = Float.MAX_VALUE
            var right = 0f
            var top = Float.MAX_VALUE
            var bottom = 0f
            var size = 0f
        }

        private val segments = mutableListOf<Segment>()

        override fun writeString(chunk: String?, positions: MutableList<TextPosition>?) {
            val ps = positions.orEmpty()
            if (ps.isEmpty()) {
                current().text.append(chunk.orEmpty())
                return
            }
            // Walk glyph by glyph so a wide gap inside one chunk also splits.
            for (tp in ps) {
                val seg = segments.lastOrNull()
                val gapLimit = maxOf(tp.fontSizeInPt * 1.6f, 10f)
                val target = if (seg == null || (seg.right > 0f && tp.xDirAdj - seg.right > gapLimit)) {
                    Segment().also { segments += it }
                } else {
                    seg
                }
                target.text.append(tp.unicode.orEmpty())
                val h = max(tp.heightDir, tp.fontSizeInPt * 0.7f)
                target.left = min(target.left, tp.xDirAdj)
                target.right = max(target.right, tp.xDirAdj + tp.widthDirAdj)
                target.top = min(target.top, tp.yDirAdj - h)
                target.bottom = max(target.bottom, tp.yDirAdj)
                target.size = max(target.size, tp.fontSizeInPt)
            }
        }

        private fun current(): Segment = segments.lastOrNull() ?: Segment().also { segments += it }

        override fun writeWordSeparator() {
            segments.lastOrNull()?.text?.append(' ')
        }

        override fun writeLineSeparator() {
            flush()
        }

        fun flush() {
            for (seg in segments) {
                val t = seg.text.toString().trim()
                if (t.isNotEmpty() && seg.left != Float.MAX_VALUE) {
                    lines += TextLine(t, seg.left, pageTop + seg.top, seg.right, pageTop + seg.bottom, seg.size)
                }
            }
            segments.clear()
        }
    }
}
