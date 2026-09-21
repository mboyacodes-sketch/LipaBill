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
import com.lipabill.app.data.model.BoardingPassSnapshot
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
 * Boarding pass matching the confirmed-booking layout:
 * route above, 3-column details, passenger, perforated barcode stub.
 */
@Composable
fun BoardingPassStyleCard(
    snapshot: BoardingPassSnapshot,
    pdf417: ImageBitmap,
    orderId: String?,
    onReplace: (() -> Unit)?,
    modifier: Modifier = Modifier,
    headline: String = "Boarding Pass",
    barcodeIsQr: Boolean = false
) {
    val model = remember(snapshot, orderId) { snapshot.toBoardingPassUiModel(orderId) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = headline,
            color = RouteBlue,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.fromCode ?: "—",
                color = RouteBlue,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedFlightToDestination(
                modifier = Modifier.weight(1f),
                tint = RouteBlue.copy(alpha = 0.65f)
            )
            Text(
                text = model.toCode ?: "—",
                color = RouteBlue,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    TravelDetailCell("FLIGHT NO.", model.flightNo ?: "—", Modifier.weight(1f))
                    TravelDetailCell("CLASS", model.classLabel ?: "—", Modifier.weight(1f))
                    TravelDetailCell("GATE", model.gate ?: "—", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TravelDetailCell("DATE", model.dateLabel ?: "—", Modifier.weight(1f))
                    TravelDetailCell("TIME", model.timeLabel ?: "—", Modifier.weight(1f))
                    TravelDetailCell("SEAT", model.seat ?: "—", Modifier.weight(1f))
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
                TravelPassengerRow(
                    name = model.passenger ?: "Passenger",
                    subtitle = model.zone?.let { "Zone $it" }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please arrive 30–40 mins before departure",
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onReplace != null) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    TextButton(onClick = onReplace) {
                        Text("Replace boarding pass")
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
                if (barcodeIsQr) {
                    Image(
                        bitmap = pdf417,
                        contentDescription = "Boarding pass QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(168.dp)
                    )
                } else {
                    Image(
                        bitmap = pdf417,
                        contentDescription = "PDF417 boarding pass barcode",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    )
                }
                if (!model.ticketNo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = model.ticketNo.chunked(1).joinToString("  "),
                        color = LabelBlue,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private data class BoardingPassUiModel(
    val fromCode: String?,
    val toCode: String?,
    val flightNo: String?,
    val classLabel: String?,
    val gate: String?,
    val dateLabel: String?,
    val timeLabel: String?,
    val seat: String?,
    val passenger: String?,
    val zone: String?,
    val ticketNo: String?
)

private fun BoardingPassSnapshot.toBoardingPassUiModel(orderId: String?): BoardingPassUiModel {
    val notes = notes
    val fromCode = extractIata(notes.notesValue("Origin"))
        ?: extractIata(venue?.substringBefore("→"))
        ?: extractIata(title.orEmpty().substringAfter("·", missingDelimiterValue = "").substringBefore("→"))
    val toCode = extractIata(notes.notesValue("Destination"))
        ?: extractIata(venue?.substringAfter("→"))
        ?: extractIata(title.orEmpty().substringAfter("→"))

    val zoned = startsAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val boardingTime = notes.notesValue("Boarding")
    val departTime = notes.notesValue("Departure time") ?: notes.notesValue("Dep time")
    val timeLabel = boardingTime ?: departTime
        ?: zoned?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

    val dateLabel = zoned?.format(DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH))
        ?: startsAtMillis?.let { formatEventWhen(it) }

    val flightNo = (title ?: "").substringBefore("·").trim()
        .takeIf { it.isNotBlank() && it != "Boarding pass" }
        ?: Regex("""(?i)\b([A-Z0-9]{2}\s?\d{2,4})\b""").find(title.orEmpty())?.value

    val seat = seatOrTier
        ?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Seat", ignoreCase = true) }
        ?.removePrefix("Seat")?.trim()
        ?: notes.notesValue("Seat")

    val gate = notes.notesValue("Gate")
        ?: seatOrTier?.split("·")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("Gate", ignoreCase = true) }
            ?.removePrefix("Gate")?.trim()

    val rawClass = notes.notesValue("Cabin")
        ?: notes.notesValue("Class")
        ?: seatOrTier?.split("·")?.map { it.trim() }
            ?.firstOrNull {
                it.contains("Economy", true) || it.contains("Business", true) ||
                    it.startsWith("Class", true) || it.startsWith("Cabin", true)
            }

    val ticketNo = notes.notesValue("Ticket no")
        ?: notes.notesValue("Ticket no.")
        ?: orderId

    return BoardingPassUiModel(
        fromCode = fromCode,
        toCode = toCode,
        flightNo = flightNo,
        classLabel = friendlyCabinLabel(rawClass),
        gate = gate,
        dateLabel = dateLabel,
        timeLabel = timeLabel,
        seat = seat,
        passenger = notes.notesValue("Passenger"),
        zone = notes.notesValue("Zone"),
        ticketNo = ticketNo
    )
}

private fun extractIata(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    Regex("""\(([A-Z]{3})\)""").find(raw.uppercase(Locale.US))?.groupValues?.getOrNull(1)
        ?.let { return it }
    val t = raw.trim().uppercase(Locale.US)
    if (Regex("""^[A-Z]{3}$""").matches(t)) return t
    return Regex("""\b([A-Z]{3})\b""").find(t)?.groupValues?.getOrNull(1)
}

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
