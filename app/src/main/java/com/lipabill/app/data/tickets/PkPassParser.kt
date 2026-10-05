package com.lipabill.app.data.tickets

import android.content.Context
import android.net.Uri
import com.lipabill.app.data.model.TicketBarcodeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Parsed Apple Wallet pass (.pkpass = ZIP with pass.json).
 * Signature/manifest are ignored — we only need the scannable barcode + labels.
 */
data class ParsedPkPass(
    val title: String,
    val venue: String?,
    val startsAtMillis: Long?,
    val seatOrTier: String?,
    val barcodeValue: String,
    val barcodeFormat: TicketBarcodeFormat,
    val orderId: String?,
    val notes: String?
)

object PkPassParser {

    private val END_LABELS = setOf(
        "end", "ends", "end time", "end date", "finish", "finishes",
        "until", "to", "close", "closing", "doors close"
    )

    private val CLOCK_RANGE = Regex(
        """(?i)^\s*(\d{1,2}(?::\d{2})?(?::\d{2})?\s*[ap]m|\d{1,2}:\d{2}(?::\d{2})?)\s*(?:-|–|—|to)\s*(\d{1,2}(?::\d{2})?(?::\d{2})?\s*[ap]m|\d{1,2}:\d{2}(?::\d{2})?)\s*$"""
    )

    fun isPkPassUri(uri: Uri?, mimeType: String? = null, displayName: String? = null): Boolean {
        if (uri == null) return false
        val path = listOfNotNull(uri.path, uri.lastPathSegment, displayName)
            .joinToString(" ")
            .lowercase(Locale.US)
        val mime = (mimeType ?: "").lowercase(Locale.US)
        return path.contains(".pkpass") ||
            mime == "application/vnd.apple.pkpass" ||
            mime == "application/vnd.apple.pkpasses" ||
            mime == "application/vnd-apple.pkpass"
    }

    fun parse(context: Context, uri: Uri): ParsedPkPass {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Couldn’t open the pass file")
        return stream.use { parse(it) }
    }

    fun parse(input: InputStream): ParsedPkPass {
        val passJson = readPassJson(input)
            ?: throw IllegalArgumentException("Not a valid .pkpass (missing pass.json)")
        return mapPassJson(JSONObject(passJson))
    }

    private fun readPassJson(input: InputStream): String? {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                val base = name.substringAfterLast('/')
                if (!entry.isDirectory &&
                    base.equals("pass.json", ignoreCase = true) &&
                    !name.contains("__MACOSX")
                ) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun mapPassJson(root: JSONObject): ParsedPkPass {
        val barcode = pickBarcode(root)
            ?: throw IllegalArgumentException("This pass has no barcode to scan at the gate")

        val style = styleObject(root)
        val fields = collectFields(style)

        val org = root.optString("organizationName").trim().ifBlank { null }
        val description = root.optString("description").trim().ifBlank { null }
        val logoText = root.optString("logoText").trim().ifBlank { null }
        val serial = root.optString("serialNumber").trim().ifBlank { null }

        val title = logoText
            ?: fieldValue(fields, "event", "event name", "title", "name", "show")
            ?: description
            ?: org
            ?: firstFieldValue(style, "primaryFields")
            ?: "Apple Wallet pass"

        val venue = locationName(root)
            ?: fieldValue(fields, "venue", "location", "place", "stadium", "arena", "theatre", "theater")

        val seat = buildSeat(fields)
            ?: fieldValue(fields, "seat", "section", "row", "gate", "tier", "stand", "block")

        val whenWindow = resolveEventWindow(root, fields)

        val notes = listOfNotNull(
            org,
            description?.takeIf { it != title },
            whenWindow.end?.let { "Ends: $it" },
            whenWindow.timeText?.let { "Time: $it" }
        )
            .distinct()
            .joinToString(" · ")
            .ifBlank { null }

        return ParsedPkPass(
            title = title,
            venue = venue,
            startsAtMillis = whenWindow.start,
            seatOrTier = seat,
            barcodeValue = barcode.message,
            barcodeFormat = barcode.format,
            orderId = serial,
            notes = notes
        )
    }

    private data class BarcodePick(val message: String, val format: TicketBarcodeFormat)

    private fun pickBarcode(root: JSONObject): BarcodePick? {
        val fromArray = root.optJSONArray("barcodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> barcodeFrom(arr.optJSONObject(i)) }
        }.orEmpty()
        val legacy = barcodeFrom(root.optJSONObject("barcode"))
        val candidates = fromArray + listOfNotNull(legacy)
        if (candidates.isEmpty()) return null
        val preferred = listOf(
            TicketBarcodeFormat.QR_CODE,
            TicketBarcodeFormat.PDF_417,
            TicketBarcodeFormat.AZTEC,
            TicketBarcodeFormat.CODE_128,
            TicketBarcodeFormat.OTHER
        )
        return preferred.firstNotNullOfOrNull { fmt -> candidates.firstOrNull { it.format == fmt } }
            ?: candidates.first()
    }

    private fun barcodeFrom(obj: JSONObject?): BarcodePick? {
        if (obj == null) return null
        val message = obj.optString("message").trim()
        if (message.isEmpty()) return null
        val format = when (obj.optString("format")) {
            "PKBarcodeFormatQR" -> TicketBarcodeFormat.QR_CODE
            "PKBarcodeFormatPDF417" -> TicketBarcodeFormat.PDF_417
            "PKBarcodeFormatAztec" -> TicketBarcodeFormat.AZTEC
            "PKBarcodeFormatCode128" -> TicketBarcodeFormat.CODE_128
            else -> TicketBarcodeFormat.OTHER
        }
        return BarcodePick(message, format)
    }

    private fun styleObject(root: JSONObject): JSONObject? {
        val keys = listOf("eventTicket", "boardingPass", "coupon", "storeCard", "generic")
        for (key in keys) {
            val obj = root.optJSONObject(key)
            if (obj != null) return obj
        }
        return null
    }

    private data class PassField(
        val key: String,
        val label: String,
        val value: String,
        val hasDateStyle: Boolean,
        val hasTimeStyle: Boolean
    ) {
        val isDateField: Boolean get() = hasDateStyle || hasTimeStyle
        val name: String get() = label.ifBlank { key }.lowercase(Locale.US).trim()
        val isEnd: Boolean get() = name in END_LABELS ||
            name.startsWith("end ") ||
            name.endsWith(" end") ||
            name.contains("end time")
    }

    private fun collectFields(style: JSONObject?): List<PassField> {
        if (style == null) return emptyList()
        val buckets = listOf(
            "headerFields", "primaryFields", "secondaryFields",
            "auxiliaryFields", "backFields"
        )
        val out = mutableListOf<PassField>()
        for (bucket in buckets) {
            val arr = style.optJSONArray(bucket) ?: continue
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val value = f.optString("value").trim()
                if (value.isEmpty()) continue
                out += PassField(
                    key = f.optString("key").trim(),
                    label = f.optString("label").trim(),
                    value = value,
                    hasDateStyle = f.has("dateStyle"),
                    hasTimeStyle = f.has("timeStyle")
                )
            }
        }
        return out
    }

    private data class EventWindow(
        val start: Long?,
        val end: Long?,
        val timeText: String?
    )

    private fun resolveEventWindow(root: JSONObject, fields: List<PassField>): EventWindow {
        val timed = fields.mapNotNull { field ->
            if (!field.hasTimeStyle && !field.isEnd && !field.name.isStartClock()) return@mapNotNull null
            parseAppleDate(field.value)?.let { it to field }
        }
        val endFromLabel = timed.firstOrNull { it.second.isEnd }?.first
        val startFromLabel = timed.firstOrNull { !it.second.isEnd }?.first
        val distinctTimes = timed.map { it.first }.distinct().sorted()

        var start = startFromLabel
            ?: parseAppleDate(root.optString("relevantDate").takeIf { it.isNotBlank() })
            ?: fields.firstOrNull { it.hasDateStyle }?.let { parseAppleDate(it.value) }
        var end = endFromLabel
        if (end == null && distinctTimes.size >= 2) {
            val earliest = distinctTimes.first()
            val latest = distinctTimes.last()
            if (start == null || timed.any { it.first == start }) start = earliest
            if (latest != start) end = latest
        }
        if (start == null) {
            val labeled = fieldValue(
                fields,
                "date", "time", "doors", "doors open", "starts", "start",
                "event date", "show time", "showtime", "when", "departure", "boarding"
            )
            start = parseAppleDate(labeled)
        }
        if (start == null) {
            for (f in fields) {
                val v = f.value
                if (v.length < 8 || v.none { it.isDigit() }) continue
                if (v.any { it == '-' || it == '/' || it == 'T' || it == ',' || it == ' ' }) {
                    parseAppleDate(v)?.let { start = it }
                    if (start != null) break
                }
            }
        }
        // expirationDate is pass validity, not event start — only if nothing else
        if (start == null) {
            start = parseAppleDate(root.optString("expirationDate").takeIf { it.isNotBlank() })
        }

        val timeText = if (end != null) {
            null
        } else {
            fields.firstNotNullOfOrNull { clockRange(it.value) }
        }
        return EventWindow(start, end, timeText)
    }

    private fun String.isStartClock(): Boolean {
        return this == "time" || this == "start" || this == "starts" ||
            this == "start time" || this == "doors" || this == "doors open" ||
            this == "show time" || this == "showtime" || this == "from"
    }

    /** "7:00 PM - 10:00 PM" or "19:00–22:00", kept as written when it is not two full dates. */
    private fun clockRange(raw: String): String? {
        val match = CLOCK_RANGE.matchEntire(raw.trim()) ?: return null
        val from = match.groupValues[1].trim()
        val to = match.groupValues[2].trim()
        if (from.equals(to, ignoreCase = true)) return null
        return "$from – $to"
    }

    private fun fieldValue(fields: List<PassField>, vararg needles: String): String? {
        for (needle in needles) {
            val n = needle.lowercase(Locale.US)
            fields.firstOrNull {
                it.label.lowercase(Locale.US) == n || it.key.lowercase(Locale.US) == n
            }?.value?.let { return it }
        }
        for (needle in needles) {
            val n = needle.lowercase(Locale.US)
            fields.firstOrNull {
                it.label.lowercase(Locale.US).contains(n) ||
                    it.key.lowercase(Locale.US).contains(n)
            }?.value?.let { return it }
        }
        return null
    }

    private fun firstFieldValue(style: JSONObject?, arrayKey: String): String? {
        val arr = style?.optJSONArray(arrayKey) ?: return null
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i)?.optString("value")?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun buildSeat(fields: List<PassField>): String? {
        fun find(vararg names: String): String? = fieldValue(fields, *names)
        val section = find("section", "sect")
        val row = find("row")
        val seat = find("seat")
        val parts = listOfNotNull(
            section?.let { "Sec $it" },
            row?.let { "Row $it" },
            seat?.let { "Seat $it" }
        )
        return parts.takeIf { it.size >= 2 }?.joinToString(" · ")
    }

    private fun locationName(root: JSONObject): String? {
        val locations: JSONArray = root.optJSONArray("locations") ?: return null
        for (i in 0 until locations.length()) {
            val loc = locations.optJSONObject(i) ?: continue
            val text = loc.optString("relevantText").trim().ifBlank { null }
                ?: loc.optString("name").trim().ifBlank { null }
            if (text != null) return text
        }
        return null
    }

    private fun parseAppleDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        runCatching {
            return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }
        runCatching {
            return java.time.ZonedDateTime.parse(trimmed).toInstant().toEpochMilli()
        }
        runCatching {
            return java.time.Instant.parse(trimmed).toEpochMilli()
        }
        runCatching {
            val local = java.time.LocalDateTime.parse(trimmed)
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        runCatching {
            val local = java.time.LocalDate.parse(trimmed)
            return local.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mmZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "d MMM yyyy HH:mm",
            "d MMM yyyy",
            "MMM d, yyyy h:mm a",
            "MMM d, yyyy",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            val fmt = SimpleDateFormat(p, Locale.US).apply {
                isLenient = true
                // Keep device zone for human-written dates; UTC only for Z-suffixed patterns
                if (!p.contains("Z")) {
                    timeZone = TimeZone.getDefault()
                } else {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            runCatching {
                val normalized = trimmed.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
                fmt.parse(normalized)?.time?.let { return it }
            }
        }
        return null
    }
}
