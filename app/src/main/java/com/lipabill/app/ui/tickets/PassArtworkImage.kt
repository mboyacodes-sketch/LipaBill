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
import androidx.compose.ui.platform.LocalContext
import com.lipabill.app.data.tickets.PassArtworkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPassImage(relativePath: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relativePath) {
        if (relativePath.isNullOrBlank()) {
            image = null
            return@LaunchedEffect
        }
        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            PassArtworkStore.decode(context, relativePath)
        }
        image = bitmap?.asImageBitmap()
    }
    return image
}
