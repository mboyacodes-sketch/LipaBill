package com.lipabill.app.ui.paylink

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.R
import com.lipabill.app.social.SocialLink

private val InstagramBrush = Brush.linearGradient(
    listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF), Color(0xFF515BD4))
)

@Composable
fun SocialBrandMark(
    platform: SocialLink.Platform,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    // WhatsApp’s mark already is the green bubble + white phone — don’t plate or tint it.
    if (platform == SocialLink.Platform.WHATSAPP) {
        Image(
            painter = painterResource(R.drawable.ic_brand_whatsapp),
            contentDescription = platform.label,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
        return
    }

    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (platform == SocialLink.Platform.INSTAGRAM) {
                    Modifier.background(InstagramBrush)
                } else {
                    Modifier.background(platform.solidColor())
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(platform.brandIconRes()),
            contentDescription = platform.label,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@DrawableRes
private fun SocialLink.Platform.brandIconRes(): Int = when (this) {
    SocialLink.Platform.INSTAGRAM -> R.drawable.ic_brand_instagram
    SocialLink.Platform.X -> R.drawable.ic_brand_x
    SocialLink.Platform.TIKTOK -> R.drawable.ic_brand_tiktok
    SocialLink.Platform.WHATSAPP -> R.drawable.ic_brand_whatsapp
    SocialLink.Platform.TELEGRAM -> R.drawable.ic_brand_telegram
    SocialLink.Platform.YOUTUBE -> R.drawable.ic_brand_youtube
    SocialLink.Platform.FACEBOOK -> R.drawable.ic_brand_facebook
    SocialLink.Platform.LINKEDIN -> R.drawable.ic_brand_linkedin
    SocialLink.Platform.THREADS -> R.drawable.ic_brand_threads
}

private fun SocialLink.Platform.solidColor(): Color = when (this) {
    SocialLink.Platform.INSTAGRAM -> Color(0xFFE1306C)
    SocialLink.Platform.X -> Color(0xFF000000)
    SocialLink.Platform.TIKTOK -> Color(0xFF010101)
    SocialLink.Platform.WHATSAPP -> Color(0xFF25D366)
    SocialLink.Platform.TELEGRAM -> Color(0xFF229ED9)
    SocialLink.Platform.YOUTUBE -> Color(0xFFFF0000)
    SocialLink.Platform.FACEBOOK -> Color(0xFF1877F2)
    SocialLink.Platform.LINKEDIN -> Color(0xFF0A66C2)
    SocialLink.Platform.THREADS -> Color(0xFF000000)
}
