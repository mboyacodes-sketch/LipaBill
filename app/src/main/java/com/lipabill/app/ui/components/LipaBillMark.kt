package com.lipabill.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lipabill.app.R

/** Launcher mark — navy tile with the LipaBill L. */
@Composable
fun LipaBillMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val context = LocalContext.current
    val bitmap = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.toBitmap(width = 192, height = 192)
            ?.asImageBitmap()
    } ?: return
    val radius = size * 0.22f
    Image(
        bitmap = bitmap,
        contentDescription = "LipaBill",
        modifier = modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(radius))
            .clip(RoundedCornerShape(radius))
    )
}
