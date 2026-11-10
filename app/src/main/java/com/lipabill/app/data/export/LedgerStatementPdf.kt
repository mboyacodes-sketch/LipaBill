package com.lipabill.app.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.viewmodel.formatPeriodSpan
import com.lipabill.app.viewmodel.reversedCodes
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Statements stay short so one PDF stays readable on a phone. */
const val STATEMENT_PDF_MAX_DAYS = 30

fun canExportStatementPdf(dayCount: Long): Boolean =
    dayCount in 1L..STATEMENT_PDF_MAX_DAYS.toLong()

data class StatementTotals(
    val income: Double,
    val expense: Double,
    val fees: Double,
    /** Column headers; Fuliza uses Drawn / Access fees. */
    val incomeLabel: String = "CAME IN",
    val expenseLabel: String = "WENT OUT"
)

/**
 * Tiny header chip next to the app mark. Prefers the active filter; otherwise
 * names the rails actually present in [txs] (never assumes M-Pesa alone).
 */
fun statementSourceBadge(
    txs: List<MpesaTransaction>,
    sourceFilter: MoneySource? = null,
    fuliza: Boolean = false
): String {
    if (fuliza) return "Fuliza"
    if (sourceFilter != null) return sourceFilter.label
    val rails = txs.map { it.source }.distinct()
        .sortedBy { it.label.lowercase(Locale.US) }
    return when {
        rails.isEmpty() -> "Ledger"
        rails.size == 1 -> rails.first().label
        rails.size == 2 -> {
            val pair = "${rails[0].label}+${rails[1].label}"
            if (pair.length <= 14) pair else "2 rails"
        }
        else -> "${rails.size} rails"
    }
}

/**
 * Writes an A4 money statement for [txs] (oldest first). Call only when
 * [canExportStatementPdf] is true for the selected period.
 *
 * Header matches Request QR marks: app [logo] with a tiny [sourceLabel] badge
 * (e.g. M-Pesa) tucked on the corner — no LipaBill wordmark.
 */
fun writeLedgerStatementPdf(
    txs: List<MpesaTransaction>,
    zone: ZoneId,
    periodFrom: LocalDate,
    periodTo: LocalDate,
    totals: StatementTotals,
    output: OutputStream,
    logo: Bitmap? = null,
    sourceLabel: String = "M-Pesa"
) {
    val doc = PdfDocument()
    try {
        val pages = layoutStatementPages(
            txs = txs.sortedBy { it.timestampMillis },
            zone = zone,
            periodFrom = periodFrom,
            periodTo = periodTo
        )
        pages.forEachIndexed { index, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = doc.startPage(pageInfo)
            drawPage(
                canvas = page.canvas,
                pageIndex = index,
                pageCount = pages.size,
                periodFrom = periodFrom,
                periodTo = periodTo,
                totals = totals,
                txnCount = txs.size,
                rows = pageRows,
                showHeaderSummary = index == 0,
                logo = logo,
                sourceLabel = sourceLabel
            )
            doc.finishPage(page)
        }
        doc.writeTo(output)
    } finally {
        doc.close()
    }
}

private data class StatementRow(
    val date: String,
    val detail: String,
    val inflow: String,
    val outflow: String
)

private fun layoutStatementPages(
    txs: List<MpesaTransaction>,
    zone: ZoneId,
    periodFrom: LocalDate,
    periodTo: LocalDate
): List<List<StatementRow>> {
    val undone = reversedCodes(txs)
    val rows = txs.map { tx ->
        val at = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone)
        val party = tx.counterpartyName?.takeIf { it.isNotBlank() }
            ?: tx.counterpartyPhone?.takeIf { it.isNotBlank() }
            ?: "—"
        val mark = when {
            tx.type == TransactionType.REVERSED -> " · reversal"
            tx.code.uppercase(Locale.US) in undone -> " · reversed"
            else -> ""
        }
        val amount = tx.amount ?: 0.0
        val out = tx.type.isOutgoing()
        StatementRow(
            date = at.format(rowDate),
            detail = "${tx.type.displayLabel()} · $party$mark",
            inflow = if (!out && amount > 0.005) money(amount) else "",
            outflow = if (out && amount > 0.005) money(amount) else ""
        )
    }
    if (rows.isEmpty()) {
        return listOf(
            listOf(
                StatementRow(
                    date = formatPeriodSpan(periodFrom, periodTo),
                    detail = "No payments in this stretch",
                    inflow = "",
                    outflow = ""
                )
            )
        )
    }
    if (rows.size <= FIRST_PAGE_ROWS) return listOf(rows)
    val pages = mutableListOf<List<StatementRow>>()
    pages += rows.take(FIRST_PAGE_ROWS)
    var offset = FIRST_PAGE_ROWS
    while (offset < rows.size) {
        val end = (offset + NEXT_PAGE_ROWS).coerceAtMost(rows.size)
        pages += rows.subList(offset, end)
        offset = end
    }
    return pages
}

private fun drawPage(
    canvas: Canvas,
    pageIndex: Int,
    pageCount: Int,
    periodFrom: LocalDate,
    periodTo: LocalDate,
    totals: StatementTotals,
    txnCount: Int,
    rows: List<StatementRow>,
    showHeaderSummary: Boolean,
    logo: Bitmap?,
    sourceLabel: String
) {
    val label = paint(9f, Typeface.DEFAULT_BOLD, COLOR_MUTE)
    val body = paint(10f, Typeface.DEFAULT, COLOR_INK)
    val bodyBold = paint(10f, Typeface.DEFAULT_BOLD, COLOR_INK)
    val small = paint(8f, Typeface.DEFAULT, COLOR_MUTE)
    val rule = paint(1f, Typeface.DEFAULT, COLOR_HAIRLINE).apply { style = Paint.Style.STROKE }
    val accentRule = paint(2f, Typeface.DEFAULT, COLOR_ACCENT).apply { style = Paint.Style.STROKE }

    var y = MARGIN_TOP.toFloat()
    val markBottom = drawHeaderMark(
        canvas = canvas,
        left = MARGIN_LEFT.toFloat(),
        top = y - 8f,
        logo = logo,
        sourceLabel = sourceLabel
    )
    canvas.drawText(
        "Statement",
        (PAGE_WIDTH - MARGIN_RIGHT).toFloat(),
        y + 8f,
        paint(11f, Typeface.DEFAULT_BOLD, COLOR_ACCENT).apply { textAlign = Paint.Align.RIGHT }
    )
    y = max(markBottom, y + 18f) + 10f
    canvas.drawLine(MARGIN_LEFT.toFloat(), y, (PAGE_WIDTH - MARGIN_RIGHT).toFloat(), y, accentRule)
    y += 22f

    canvas.drawText(formatPeriodSpan(periodFrom, periodTo), MARGIN_LEFT.toFloat(), y, bodyBold)
    y += 14f
    canvas.drawText(
        "Generated on this phone · not a bank statement",
        MARGIN_LEFT.toFloat(),
        y,
        small
    )
    y += 20f

    if (showHeaderSummary) {
        canvas.drawText(totals.incomeLabel, MARGIN_LEFT.toFloat(), y, label)
        canvas.drawText(totals.expenseLabel, COL_MID.toFloat(), y, label)
        canvas.drawText("NET", COL_RIGHT.toFloat(), y, label)
        y += 14f
        val net = totals.income - totals.expense
        canvas.drawText("KSh ${money(totals.income)}", MARGIN_LEFT.toFloat(), y, bodyBold)
        canvas.drawText("KSh ${money(totals.expense)}", COL_MID.toFloat(), y, bodyBold)
        canvas.drawText("KSh ${money(net)}", COL_RIGHT.toFloat(), y, bodyBold)
        y += 16f
        canvas.drawText(
            "Fees KSh ${money(totals.fees)} · $txnCount payment${if (txnCount == 1) "" else "s"}",
            MARGIN_LEFT.toFloat(),
            y,
            small
        )
        y += 18f
        canvas.drawLine(MARGIN_LEFT.toFloat(), y, (PAGE_WIDTH - MARGIN_RIGHT).toFloat(), y, rule)
        y += 18f
    } else {
        canvas.drawText("Continued", MARGIN_LEFT.toFloat(), y, small)
        y += 16f
    }

    val headerRight = paint(9f, Typeface.DEFAULT_BOLD, COLOR_MUTE).apply {
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("DATE", MARGIN_LEFT.toFloat(), y, label)
    canvas.drawText("DETAIL", COL_DETAIL.toFloat(), y, label)
    canvas.drawText("IN", COL_IN.toFloat(), y, headerRight)
    canvas.drawText("OUT", COL_OUT.toFloat(), y, headerRight)
    y += 8f
    canvas.drawLine(MARGIN_LEFT.toFloat(), y, (PAGE_WIDTH - MARGIN_RIGHT).toFloat(), y, rule)
    y += 14f

    val amountPaint = paint(10f, Typeface.DEFAULT, COLOR_INK).apply { textAlign = Paint.Align.RIGHT }
    for (row in rows) {
        if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 36f) break
        canvas.drawText(row.date, MARGIN_LEFT.toFloat(), y, body)
        canvas.drawText(ellipsize(row.detail, body, DETAIL_WIDTH), COL_DETAIL.toFloat(), y, body)
        if (row.inflow.isNotEmpty()) {
            canvas.drawText(row.inflow, COL_IN.toFloat(), y, amountPaint)
        }
        if (row.outflow.isNotEmpty()) {
            canvas.drawText(row.outflow, COL_OUT.toFloat(), y, amountPaint)
        }
        y += ROW_HEIGHT
    }

    val footerY = (PAGE_HEIGHT - 28).toFloat()
    canvas.drawLine(
        MARGIN_LEFT.toFloat(),
        footerY - 12f,
        (PAGE_WIDTH - MARGIN_RIGHT).toFloat(),
        footerY - 12f,
        rule
    )
    canvas.drawText(
        "Built from your confirmation SMS on this device. Not an official M-Pesa statement.",
        MARGIN_LEFT.toFloat(),
        footerY,
        small
    )
    if (pageCount > 1) {
        canvas.drawText(
            "Page ${pageIndex + 1} of $pageCount",
            (PAGE_WIDTH - MARGIN_RIGHT).toFloat(),
            footerY,
            paint(8f, Typeface.DEFAULT, COLOR_MUTE).apply { textAlign = Paint.Align.RIGHT }
        )
    }
}

/**
 * App logo plate with a tiny source chip on the bottom-end — same idea as the
 * Request QR centre mark (LipaBill + platform badge).
 * @return bottom Y of the mark block for layout below.
 */
private fun drawHeaderMark(
    canvas: Canvas,
    left: Float,
    top: Float,
    logo: Bitmap?,
    sourceLabel: String
): Float {
    val plate = RectF(left, top, left + LOGO_PLATE, top + LOGO_PLATE)
    val plateFill = paint(1f, Typeface.DEFAULT, COLOR_WHITE).apply { style = Paint.Style.FILL }
    val plateStroke = paint(1f, Typeface.DEFAULT, COLOR_HAIRLINE).apply { style = Paint.Style.STROKE }
    canvas.drawRoundRect(plate, LOGO_RADIUS, LOGO_RADIUS, plateFill)
    canvas.drawRoundRect(plate, LOGO_RADIUS, LOGO_RADIUS, plateStroke)

    val inset = 3f
    val logoRect = RectF(
        plate.left + inset,
        plate.top + inset,
        plate.right - inset,
        plate.bottom - inset
    )
    if (logo != null) {
        val clip = Path().apply {
            addRoundRect(logoRect, LOGO_INNER_RADIUS, LOGO_INNER_RADIUS, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(logo, null, logoRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        // Fallback glyph when the launcher mipmap is missing.
        val mark = paint(18f, Typeface.DEFAULT_BOLD, COLOR_ACCENT)
        canvas.drawText("L", logoRect.centerX() - 6f, logoRect.centerY() + 6f, mark)
    }

    val badge = sourceLabel.trim().ifBlank { "Ledger" }.let { raw ->
        if (raw.length <= 14) raw else raw.take(13) + "…"
    }
    val badgeText = paint(7f, Typeface.DEFAULT_BOLD, COLOR_ACCENT)
    val textW = badgeText.measureText(badge)
    val badgePadX = 4f
    val badgePadY = 2.5f
    val badgeH = 11f
    val badgeW = min(LOGO_PLATE + 18f, textW + badgePadX * 2f)
    val badgeRect = RectF(
        plate.right - badgeW * 0.55f,
        plate.bottom - badgeH * 0.55f,
        plate.right - badgeW * 0.55f + badgeW,
        plate.bottom - badgeH * 0.55f + badgeH
    )
    // Keep the chip on the page.
    if (badgeRect.right > PAGE_WIDTH - MARGIN_RIGHT) {
        val shift = badgeRect.right - (PAGE_WIDTH - MARGIN_RIGHT)
        badgeRect.offset(-shift, 0f)
    }
    canvas.drawRoundRect(badgeRect, 3.5f, 3.5f, plateFill)
    canvas.drawRoundRect(badgeRect, 3.5f, 3.5f, plateStroke)
    canvas.drawText(
        badge,
        badgeRect.left + badgePadX,
        badgeRect.bottom - badgePadY - 1f,
        badgeText
    )
    return max(plate.bottom, badgeRect.bottom)
}

private fun paint(size: Float, typeface: Typeface, color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = size
        this.typeface = typeface
        this.color = color
    }

private fun money(value: Double): String =
    String.format(Locale.US, "%,.2f", value)

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > maxWidth) end--
    return text.take(max(1, end)) + "…"
}

// A4 at 72 dpi.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN_LEFT = 48
private const val MARGIN_RIGHT = 48
private const val MARGIN_TOP = 56
private const val MARGIN_BOTTOM = 48
private const val COL_DETAIL = 120
private const val COL_MID = 230
private const val COL_RIGHT = 400
private const val COL_IN = 470
private const val COL_OUT = PAGE_WIDTH - MARGIN_RIGHT
private const val DETAIL_WIDTH = 250f
private const val ROW_HEIGHT = 16f
private const val FIRST_PAGE_ROWS = 26
private const val NEXT_PAGE_ROWS = 36
private const val LOGO_PLATE = 36f
private const val LOGO_RADIUS = 9f
private const val LOGO_INNER_RADIUS = 7f

private val rowDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private const val COLOR_INK = 0xFF111827.toInt()
private const val COLOR_MUTE = 0xFF6B7280.toInt()
private const val COLOR_HAIRLINE = 0xFFD1D5DB.toInt()
private const val COLOR_ACCENT = 0xFF2F4A6E.toInt()
private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
