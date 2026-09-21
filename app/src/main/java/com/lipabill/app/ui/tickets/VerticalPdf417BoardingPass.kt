package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Encodes [bcbpPayload] as a **horizontal** PDF417 and remembers an [ImageBitmap].
 * The temporary Android [Bitmap] is recycled immediately after copying into Compose.
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
): ImageBitmap? = remember(
    bcbpPayload,
    width,
    height,
    foregroundColor,
    backgroundColor,
    allowPlaceholder
) {
    val src = bcbpPayload?.let {
        BoardingPassPdf417.encodeHorizontalBitmap(
            it,
            width,
            height,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            allowPlaceholder = allowPlaceholder
        )
    } ?: return@remember null
    try {
        val copy = src.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false) ?: src
        if (copy !== src) src.recycle()
        copy.asImageBitmap()
    } catch (_: Throwable) {
        if (!src.isRecycled) src.recycle()
        null
    }
}
