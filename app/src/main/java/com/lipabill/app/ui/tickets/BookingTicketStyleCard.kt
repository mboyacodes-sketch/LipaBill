package com.lipabill.app.ui.tickets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.RouteBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatEventWhen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Flight booking card matching the confirmed-booking mock:
 * route above, 3-column details grid, passengers, perforated barcode stub (white).
 * Rail bookings keep a simpler compact layout.
 */
@Composable
fun BookingTicketStyleCard(
    ticket: Ticket,
    pdf417: ImageBitmap?,
    onChangeDate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    qrBitmap: ImageBitmap? = null
) {
    val model = remember(ticket) { ticket.toBookingTicketUiModel() }
    if (model.isRail) {
        RailBookingCard(
            model = model,
            pdf417 = pdf417,
            qrBitmap = qrBitmap,
            onChangeDate = onChangeDate,
            ticket = ticket,
            modifier = modifier
        )
    } else {
        FlightBookingConfirmedCard(
            model = model,
            pdf417 = pdf417,
            onChangeDate = onChangeDate,
            ticket = ticket,
            modifier = modifier
        )
    }
}

@Composable
private fun FlightBookingConfirmedCard(
    model: BookingTicketUiModel,
    pdf417: ImageBitmap?,
    onChangeDate: (() -> Unit)?,
    ticket: Ticket,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = model.headline,
            color = RouteBlue,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (model.tripLabel != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = model.tripLabel,
                color = LabelBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Outbound route
        FlightRouteRow(
            from = model.fromCode,
            to = model.toCode,
            label = if (model.isReturn) "OUTBOUND" else null
        )

        // Return route (when round-trip)
        if (model.isReturn && (model.returnFrom != null || model.returnTo != null)) {
            Spacer(modifier = Modifier.height(14.dp))
            FlightRouteRow(
                from = model.returnFrom ?: model.toCode,
                to = model.returnTo ?: model.fromCode,
                label = "RETURN",
                compact = true
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp))
                .background(CardWhite)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                if (model.isItinerary) {
                    // Itinerary: schedule-first — flight / date / time, then cabin / seat / bag
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TravelDetailCell("FLIGHT", model.flightNo ?: "—", Modifier.weight(1f))
                        TravelDetailCell("DATE", model.dateLabel ?: "—", Modifier.weight(1f))
                        TravelDetailCell("DEPART", model.timeLabel ?: "—", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TravelDetailCell("CABIN", model.classLabel ?: "—", Modifier.weight(1f))
                        TravelDetailCell("SEAT", model.seat ?: "—", Modifier.weight(1f))
                        TravelDetailCell(
                            if (model.isReturn) "RETURN FLT" else "STATUS",
                            model.returnFlightNo ?: model.statusLabel ?: "—",
                            Modifier.weight(1f)
                        )
                    }
                    if (model.isReturn && model.returnDateLabel != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TravelDetailCell("RETURN DATE", model.returnDateLabel, Modifier.weight(1f))
                            TravelDetailCell("RETURN TIME", model.returnTimeLabel ?: "—", Modifier.weight(1f))
                            TravelDetailCell("REF", model.refNo ?: "—", Modifier.weight(1f))
                        }
                    }
                } else {
                    // Receipt: ticket / class / terminal, then date / time / seat
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TravelDetailCell("FLIGHT NO.", model.flightNo ?: "—", Modifier.weight(1f))
                        TravelDetailCell("CLASS", model.classLabel ?: "—", Modifier.weight(1f))
                        TravelDetailCell("TERMINAL", model.terminal ?: "—", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TravelDetailCell("DATE", model.dateLabel ?: "—", Modifier.weight(1f))
                        TravelDetailCell("TIME", model.timeLabel ?: "—", Modifier.weight(1f))
                        TravelDetailCell("SEAT", model.seat ?: "—", Modifier.weight(1f))
                    }
                    if (model.ticketNo != null || model.totalLabel != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TravelDetailCell(
                                "TICKET NO.",
                                model.ticketNo ?: "—",
                                Modifier.weight(1.2f)
                            )
                            TravelDetailCell(
                                "TOTAL",
                                model.totalLabel ?: "—",
                                Modifier.weight(1f)
                            )
                            TravelDetailCell(
                                if (model.isReturn) "TRIP" else "REF",
                                if (model.isReturn) "Return" else (model.refNo ?: "—"),
                                Modifier.weight(0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "PASSENGERS",
                    color = LabelBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (model.passengers.isEmpty()) {
                    TravelPassengerRow(name = "Passenger", subtitle = null)
                } else {
                    model.passengers.forEachIndexed { index, p ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        TravelPassengerRow(name = p.name, subtitle = p.subtitle)
                    }
                }

                if (onChangeDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onChangeDate) {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (ticket.startsAtMillis != null) "Change date" else "Set date")
                    }
                }
            }

            TravelTicketPerforation()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pdf417 != null) {
                    Image(
                        bitmap = pdf417,
                        contentDescription = "Booking PDF417 barcode",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    )
                }
                val codeLine = model.refNo?.takeIf { it.length in 4..10 }
                    ?: model.ticketNo?.take(22)
                if (!codeLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (codeLine.length <= 10) {
                            codeLine.chunked(1).joinToString("  ")
                        } else {
                            codeLine
                        },
                        color = LabelBlue,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = model.footerHint,
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FlightRouteRow(
    from: String?,
    to: String?,
    label: String? = null,
    compact: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (label != null) {
            Text(
                text = label,
                color = LabelBlue,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = from ?: "—",
                color = RouteBlue,
                fontSize = if (compact) 16.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedFlightToDestination(
                modifier = Modifier.weight(1f),
                tint = RouteBlue.copy(alpha = if (compact) 0.4f else 0.65f)
            )
            Text(
                text = to ?: "—",
                color = RouteBlue,
                fontSize = if (compact) 16.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RailBookingCard(
    model: BookingTicketUiModel,
    pdf417: ImageBitmap?,
    qrBitmap: ImageBitmap?,
    onChangeDate: (() -> Unit)?,
    ticket: Ticket,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Booking Confirmed",
            color = RouteBlue,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.fromCode ?: "—",
                color = RouteBlue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Outlined.Train, contentDescription = null, tint = Mute, modifier = Modifier.size(26.dp))
            Text(
                text = model.toCode ?: "—",
                color = RouteBlue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp))
                .background(CardWhite)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                val whenLabel = listOfNotNull(model.dateLabel, model.timeLabel)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" / ")
                if (whenLabel.isNotBlank()) {
                    Text(whenLabel, style = HomeType.caption, color = Mute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(14.dp))
                }
                model.refNo?.let {
                    TravelDetailCell("REF NO.", it, Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    model.footerHint,
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onChangeDate != null) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    TextButton(onClick = onChangeDate) {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (ticket.startsAtMillis != null) "Change date" else "Set date")
                    }
                }
            }
            TravelTicketPerforation()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    qrBitmap != null -> {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Booking QR code",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(168.dp)
                        )
                    }
                    pdf417 != null -> {
                        Image(
                            bitmap = pdf417,
                            contentDescription = "Booking barcode",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = model.refNo ?: "Booking",
                            color = Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class PassengerUi(val name: String, val subtitle: String?)

private data class BookingTicketUiModel(
    val headline: String,
    val tripLabel: String?,
    val fromCode: String?,
    val toCode: String?,
    val returnFrom: String?,
    val returnTo: String?,
    val timeLabel: String?,
    val dateLabel: String?,
    val returnFlightNo: String?,
    val returnDateLabel: String?,
    val returnTimeLabel: String?,
    val flightNo: String?,
    val classLabel: String?,
    val terminal: String?,
    val seat: String?,
    val statusLabel: String?,
    val ticketNo: String?,
    val totalLabel: String?,
    val passengers: List<PassengerUi>,
    val refNo: String?,
    val isRail: Boolean,
    val isItinerary: Boolean,
    val isReturn: Boolean,
    val footerHint: String
)

private fun Ticket.toBookingTicketUiModel(): BookingTicketUiModel {
    val notes = notes
    val airline = notes.notesValue("Airline")
    val docKind = notes.notesValue("Doc")
    val tripKind = notes.notesValue("Trip")
    val isItinerary = docKind.equals("Itinerary", ignoreCase = true)
    val isReturn = tripKind.equals("Return", ignoreCase = true)
    val isMultiCity = tripKind.equals("Multi-city", ignoreCase = true)

    val isRail = title.contains("SGR", true) ||
        venue.orEmpty().contains("Terminus", true) ||
        notes.orEmpty().contains("SGR", true) ||
        (source == TicketSource.BOOKING_CONFIRMATION && airline == null && docKind == null)

    // Prefer clean IATA pair from venue/title — never show city fragments / OCR junk
    val routePair = parseIataRoute(venue) ?: parseIataRoute(title)
    val fromCode = extractIataCode(notes.notesValue("Origin"))
        ?: routePair?.first
        ?: extractIataCode(venue?.substringBefore("→"))
        ?: extractIataCode(title.substringAfter("·", missingDelimiterValue = "").substringBefore("→"))
    val toCode = extractIataCode(notes.notesValue("Destination"))
        ?: routePair?.second
        ?: extractIataCode(venue?.substringAfter("→"))
        ?: extractIataCode(title.substringAfter("→"))

    val returnRaw = notes.notesValue("Return")
    val returnParts = returnRaw?.split("/")?.map { it.trim() }.orEmpty()
    val returnRoutePart = returnParts.firstOrNull { it.contains("→") }
    val returnPair = parseIataRoute(returnRoutePart) ?: parseIataRoute(returnRaw)
    val returnFrom = returnPair?.first
        ?: extractIataCode(returnRoutePart?.substringBefore("→"))
        ?: if (isReturn) toCode else null
    val returnTo = returnPair?.second
        ?: extractIataCode(returnRoutePart?.substringAfter("→"))
        ?: if (isReturn) fromCode else null
    val returnFlightNo = returnParts
        .firstOrNull { Regex("""(?i)^[A-Z0-9]{2}\s?\d{2,4}$""").matches(it) }
        ?.take(10)
    val returnDateLabel = returnParts
        .firstOrNull { Regex("""(?i)\d{1,2}\s+[A-Za-z]{3}\s+\d{4}""").containsMatchIn(it) }
        ?.let { part ->
            Regex("""(?i)(\d{1,2}\s+[A-Za-z]{3}\s+\d{4})""").find(part)?.groupValues?.getOrNull(1)
        }
    val returnTimeLabel = returnParts
        .firstOrNull { Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(it) }
        ?.let { part ->
            Regex("""\b(\d{1,2}:\d{2})\b""").find(part)?.groupValues?.getOrNull(1)
        }

    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val timeLabel = zoned?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        ?: notes.notesValue("Departure time")
        ?: notes.notesValue("Dep time")
    val dateLabel = zoned?.format(DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH))
        ?: startsAtMillis?.let { formatEventWhen(it) }

    val flightNo = title.substringBefore("·").trim()
        .takeIf { it.isNotBlank() && !it.equals("Flight e-ticket", true) &&
            !it.equals("Flight itinerary", true) && !it.equals("Booking", true) }
        ?: Regex("""(?i)\b([A-Z0-9]{2}\s?\d{2,4})\b""").find(title)?.value

    val seat = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Seat", ignoreCase = true) }
        ?.removePrefix("Seat")?.trim()
        ?: notes.notesValue("Seat")

    val rawClass = notes.notesValue("Class")
        ?: notes.notesValue("Cabin")
        ?: seatOrTier?.split("·")?.map { it.trim() }
            ?.firstOrNull {
                it.contains("Economy", true) || it.contains("Business", true) ||
                    it.startsWith("Class", true) || it.startsWith("Cabin", true)
            }
            ?.replace(Regex("""(?i)^(Class|Cabin)\s*"""), "")?.trim()

    val classLabel = friendlyCabinLabel(rawClass)
    val terminal = notes.notesValue("Dep terminal") ?: notes.notesValue("Terminal")
    val ticketNo = notes.notesValue("Ticket no") ?: notes.notesValue("Ticket no.")
    val totalLabel = notes.notesValue("Total")?.let { t ->
        if (t.contains("KES", true)) t.take(22) else "KES ${t.take(18)}"
    }
    val statusLabel = notes.notesValue("Status")
    val passenger = notes.notesValue("Passenger")
    val passengers = listOfNotNull(
        passenger?.let { PassengerUi(name = it, subtitle = airline) }
    )

    val headline = when {
        isRail -> "Booking Confirmed"
        isItinerary -> "Flight Itinerary"
        docKind.equals("Receipt", true) -> "E-ticket Receipt"
        else -> "Booking Confirmed"
    }
    val tripLabel = when {
        isRail -> null
        isReturn -> "Return trip"
        isMultiCity -> "Multi-city"
        tripKind.equals("One-way", true) -> "One-way"
        else -> null
    }

    val footerHint = when {
        isRail -> "Use Ref No with your phone number at the station kiosk"
        isItinerary -> "Itinerary — add boarding pass at check-in"
        else -> "E-ticket receipt — add boarding pass at check-in"
    }

    return BookingTicketUiModel(
        headline = headline,
        tripLabel = tripLabel,
        fromCode = fromCode,
        toCode = toCode,
        returnFrom = returnFrom,
        returnTo = returnTo,
        timeLabel = timeLabel?.take(12),
        dateLabel = dateLabel?.take(18),
        returnFlightNo = returnFlightNo,
        returnDateLabel = returnDateLabel?.take(18),
        returnTimeLabel = returnTimeLabel?.take(8),
        flightNo = flightNo?.take(10),
        classLabel = classLabel?.take(14),
        terminal = terminal?.take(6),
        seat = seat?.take(4),
        statusLabel = statusLabel?.take(12),
        ticketNo = ticketNo?.take(22),
        totalLabel = totalLabel,
        passengers = passengers.map { p ->
            PassengerUi(name = p.name.take(36), subtitle = p.subtitle?.take(28))
        },
        refNo = orderId?.take(10),
        isRail = isRail,
        isItinerary = isItinerary,
        isReturn = isReturn,
        footerHint = footerHint
    )
}

/** Only real 3-letter airport codes — never city fragments like "Nairob". */
private fun extractIataCode(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    Regex("""\(([A-Z]{3})\)""").find(raw.uppercase(Locale.US))?.groupValues?.getOrNull(1)
        ?.let { code -> if (code in KNOWN_IATA) return code }
    val t = raw.trim().uppercase(Locale.US)
    if (t in KNOWN_IATA) return t
    // City / airport name → IATA
    when {
        t.contains("NAIROBI") || t.contains("JKIA") || t.contains("JOMO") -> return "NBO"
        t.contains("MOMBASA") || t.contains("MOI") -> return "MBA"
        t.contains("KISUMU") -> return "KIS"
        t.contains("ELDORET") -> return "EDL"
        t.contains("MALINDI") -> return "MYD"
    }
    return Regex("""\b([A-Z]{3})\b""").findAll(t)
        .map { it.groupValues[1] }
        .firstOrNull { it in KNOWN_IATA }
}

private fun parseIataRoute(raw: String?): Pair<String, String>? {
    if (raw.isNullOrBlank()) return null
    val m = Regex("""\b([A-Z]{3})\s*→\s*([A-Z]{3})\b""").find(raw.uppercase(Locale.US))
        ?: return null
    val a = m.groupValues[1]
    val b = m.groupValues[2]
    if (a !in KNOWN_IATA || b !in KNOWN_IATA) return null
    return a to b
}

private val KNOWN_IATA = setOf(
    "NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL"
)

private fun String?.notesValue(label: String): String? {
    val prefix = "$label:"
    return orEmpty()
        .split(" · ")
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
