package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encodes [bcbpPayload] as a **horizontal** PDF417 off the main thread and remembers
 * an [ImageBitmap]. The temporary Android [Bitmap] is recycled after copying into Compose.
 *
 * Default boarding style: black modules on transparent background (sits on grey header).
 */
@Composable
fun rememberHorizontalPdf417ImageBitmap(
    bcbpPayload: String?,
    width: Int = 900,
    height: Int = 160,
    foregroundColor: Int = android.graphics.Color.BLACK,
    backgroundColor: Int = android.graphics.Color.TRANSPARENT,
    allowPlaceholder: Boolean = false
): ImageBitmap? {
    val payload = bcbpPayload?.takeIf { it.isNotBlank() }
    var bitmap by remember(
        payload,
        width,
        height,
        foregroundColor,
        backgroundColor,
        allowPlaceholder
    ) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(payload, width, height, foregroundColor, backgroundColor, allowPlaceholder) {
        if (payload == null) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.Default) {
            val src = BoardingPassPdf417.encodeHorizontalBitmap(
                payload,
                width,
                height,
                foregroundColor = foregroundColor,
                backgroundColor = backgroundColor,
                allowPlaceholder = allowPlaceholder
            ) ?: return@withContext null
            try {
                val copy = src.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false) ?: src
                if (copy !== src) src.recycle()
                copy.asImageBitmap()
            } catch (_: Throwable) {
                if (!src.isRecycled) src.recycle()
                null
            }
        }
    }
    return bitmap
}
