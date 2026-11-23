package com.lipabill.app.ui.tickets

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lipabill.app.R
import com.lipabill.app.ui.components.QrImageShape
import com.lipabill.app.ui.components.RoundedQrRenderer
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.tickets.BarcodeSpec
import com.lipabill.app.data.tickets.BarcodeSpecEncoder
import com.lipabill.app.ui.paylink.RequestQrCardSize
import com.lipabill.app.ui.paylink.RequestQrLipaSize
import com.lipabill.app.ui.paylink.RequestQrMarkPlate
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A gate code ready to show, rendered from the encoding captured off the original pass. */
class BoardingCode(
    val bitmap: ImageBitmap?,
    val format: TicketBarcodeFormat,
    /** True only when the code's EC level can absorb the centre LipaBill plate. */
    val showMark: Boolean,
    /** Printed-only code when [format] is [TicketBarcodeFormat.TEXT] (no inventable symbol). */
    val printedValue: String? = null
)

private object BoardingCodeCache {
    private val lock = Any()
    private val map = object : LinkedHashMap<String, BoardingCode>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BoardingCode>?) = size > 8
    }
    fun get(key: String): BoardingCode? = synchronized(lock) { map[key] }
    fun put(key: String, code: BoardingCode) = synchronized(lock) { map[key] = code }
}

private fun renderBoardingCode(value: String, spec: BarcodeSpec): BoardingCode? {
    if (spec.format == TicketBarcodeFormat.TEXT) {
        return BoardingCode(bitmap = null, format = TicketBarcodeFormat.TEXT, showMark = false, printedValue = value)
    }
    if (spec.format == TicketBarcodeFormat.QR_CODE) {
        val matrix = BarcodeSpecEncoder.encodeQr(value, spec, sizePx = 512) ?: return null
        val image = RoundedQrRenderer.toImageBitmap(
            matrix = matrix,
            sizePx = 512,
            foregroundColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.WHITE
        )
        return BoardingCode(image, spec.format, showMark = spec.allowsCenterMark)
    }
    val other = encodeTicketBarcode(value, spec.format, sizePx = 720) ?: return null
    return BoardingCode(other, spec.format, showMark = false)
}

/**
 * Re-renders the boarding code with the same symbology, EC level, version and charset as
 * the original pass. Passes attached before specs were captured fall back to the Request
 * QR style (EC H with the centre mark), which keeps the same decoded content.
 */
@Composable
fun rememberBoardingCode(
    value: String?,
    format: TicketBarcodeFormat?,
    serializedSpec: String?
): BoardingCode? {
    val code = value?.takeIf { it.isNotBlank() }
    // Printed-only: never go through the QR legacy fallback in effectiveSpec.
    if (format == TicketBarcodeFormat.TEXT && code != null) {
        return remember(code) {
            BoardingCode(null, TicketBarcodeFormat.TEXT, showMark = false, printedValue = code)
        }
    }
    val spec = remember(serializedSpec, format) {
        BarcodeSpecEncoder.effectiveSpec(BarcodeSpec.parse(serializedSpec), format)
    }
    val key = code?.let { "${spec.serialize()}|r1|$it" }
    var result by remember(key) { mutableStateOf(key?.let(BoardingCodeCache::get)) }
    LaunchedEffect(key) {
        if (code == null || key == null) {
            result = null
            return@LaunchedEffect
        }
        if (result != null) return@LaunchedEffect
        result = withContext(Dispatchers.Default) {
            renderBoardingCode(code, spec)?.also { BoardingCodeCache.put(key, it) }
        }
    }
    return result
}

/** Madaraka Express wordmark from the KRC ticketing site. */
@Composable
fun MadarakaExpressLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_brand_madaraka_express),
        contentDescription = "Madaraka Express",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(30.dp)
    )
}

/**
 * Same card as the Request QR: white rounded square, hairline border, LipaBill mark in the
 * centre when the code's error correction allows it. PDF417 / Aztec keep their own shape.
 */
@Composable
fun BoardingQrCard(
    code: BoardingCode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mark = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.toBitmap(width = 192, height = 192)
            ?.asImageBitmap()
    }
    val shape = RoundedCornerShape(22.dp)
    val printed = code.format == TicketBarcodeFormat.TEXT
    val square = !printed &&
        (code.format == TicketBarcodeFormat.QR_CODE ||
            code.format == TicketBarcodeFormat.AZTEC ||
            code.format == TicketBarcodeFormat.DATA_MATRIX)
    Box(
        modifier = modifier
            .then(
                if (square) {
                    Modifier.size(RequestQrCardSize)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                }
            )
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0x14000000), shape),
        contentAlignment = Alignment.Center
    ) {
        if (printed) {
            Text(
                text = code.printedValue.orEmpty(),
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .recordingPrivacyCover()
            )
        } else if (code.bitmap != null) {
            Image(
                bitmap = code.bitmap,
                contentDescription = "Boarding code",
                contentScale = if (square) ContentScale.Fit else ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (square) 12.dp else 16.dp)
                    .then(if (square) Modifier.clip(QrImageShape) else Modifier)
                    .recordingPrivacyCover()
            )
        }
        if (code.showMark && code.bitmap != null) {
            Box(
                modifier = Modifier
                    .size(RequestQrMarkPlate)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (mark != null) {
                    Image(
                        bitmap = mark,
                        contentDescription = "LipaBill",
                        modifier = Modifier
                            .size(RequestQrLipaSize)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}
