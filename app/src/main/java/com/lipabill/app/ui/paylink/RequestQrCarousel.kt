package com.lipabill.app.ui.paylink

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lipabill.app.pay.PayLink
import com.lipabill.app.social.SocialLink
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** One ready Request QR — payment destination or social profile. */
sealed class RequestQrSlide {
    abstract val id: String
    abstract val shareUrl: String

    data class Payment(
        val kind: PayLink.Kind,
        val link: PayLink.Target
    ) : RequestQrSlide() {
        override val id: String = "pay_${kind.name}"
        override val shareUrl: String get() = link.shareUrl
    }

    data class Social(
        val target: SocialLink.Target
    ) : RequestQrSlide() {
        override val id: String = "social_${target.platform.storageKey}"
        override val shareUrl: String get() = target.shareUrl
        val platform: SocialLink.Platform get() = target.platform
    }
}

/** Small LRU so swiping reuses bitmaps instead of re-encoding. */
internal object RequestQrBitmapCache {
    private const val MaxEntries = 16
    private const val EncodePx = 512
    private val lock = Any()
    private val map = object : LinkedHashMap<String, ImageBitmap>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MaxEntries
    }

    fun getOrNull(shareUrl: String): ImageBitmap? = synchronized(lock) { map[shareUrl] }

    fun get(shareUrl: String): ImageBitmap? {
        getOrNull(shareUrl)?.let { return it }
        val encoded = encodePayQr(shareUrl, sizePx = EncodePx) ?: return null
        synchronized(lock) { map[shareUrl] = encoded }
        return encoded
    }
}

/**
 * Peek carousel: current QR centered, previous/next sneak in from the sides.
 * QR bitmaps encode lazily per visible page (not all up front).
 */
@Composable
fun RequestQrPeekCarousel(
    slides: List<RequestQrSlide>,
    mark: ImageBitmap?,
    selectedId: String,
    onSelect: (RequestQrSlide) -> Unit,
    onCopy: (RequestQrSlide) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slides.isEmpty()) return
    if (slides.size == 1) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            RequestQrSlideCard(
                slide = slides.first(),
                mark = mark,
                onCopy = { onCopy(slides.first()) }
            )
        }
        return
    }

    val startIndex = remember(slides) {
        slides.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { slides.size })

    LaunchedEffect(selectedId, slides) {
        val index = slides.indexOfFirst { it.id == selectedId }
        if (index < 0 || index == pager.currentPage || pager.isScrollInProgress) return@LaunchedEffect
        pager.scrollToPage(index)
    }
    LaunchedEffect(pager, slides, selectedId) {
        snapshotFlow { pager.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val slide = slides.getOrNull(page) ?: return@collect
                if (slide.id != selectedId) onSelect(slide)
            }
    }

    // Pad both sides by (viewport - card) / 2 so the active page is truly centered
    // and left/right peeks show the same amount of neighbor.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(RequestQrCardSize + 4.dp)
    ) {
        val sidePad = ((maxWidth - RequestQrCardSize) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePad),
            pageSize = PageSize.Fixed(RequestQrCardSize),
            pageSpacing = RequestQrPageSpacing,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val slide = slides[page]
            // 0 = fully active, 1 = fully a side peek (left and right share this).
            val pageOffset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            val focus = 1f - pageOffset
            RequestQrSlideCard(
                slide = slide,
                mark = mark,
                onCopy = { onCopy(slide) },
                modifier = Modifier
                    .graphicsLayer {
                        // Same peek scale/dim on both sides; only the active card is full size + bright.
                        val scale = lerp(RequestQrPeekScale, 1f, focus)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(RequestQrPeekAlpha, 1f, focus)
                    }
                    .blur(radius = (RequestQrPeekBlur * pageOffset).dp)
            )
        }
    }
}

@Composable
fun RequestQrSlideCard(
    slide: RequestQrSlide,
    mark: ImageBitmap?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(slide.shareUrl) {
        mutableStateOf(RequestQrBitmapCache.getOrNull(slide.shareUrl))
    }
    LaunchedEffect(slide.shareUrl) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            RequestQrBitmapCache.get(slide.shareUrl)
        }
    }

    Box(
        modifier = modifier
            .size(RequestQrCardSize)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Color(0x14000000), RoundedCornerShape(22.dp))
            .recordingPrivacyCover()
            .clickable(onClick = onCopy),
        contentAlignment = Alignment.Center
    ) {
        val qr = bitmap
        if (qr != null) {
            Image(
                bitmap = qr,
                contentDescription = when (slide) {
                    is RequestQrSlide.Payment -> "Pay code"
                    is RequestQrSlide.Social -> "${slide.platform.label} code"
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )
        }
        Box(
            modifier = Modifier
                .size(RequestQrMarkPlate)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(4.dp)
        ) {
            if (mark != null) {
                Image(
                    bitmap = mark,
                    contentDescription = "LipaBill",
                    modifier = Modifier
                        .size(RequestQrLipaSize)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            if (slide is RequestQrSlide.Social) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    SocialBrandMark(
                        platform = slide.platform,
                        size = RequestQrBrandSize
                    )
                }
            }
        }
    }
}

/** Same footprint as the payments Request QR. */
internal val RequestQrCardSize = 248.dp
internal val RequestQrPageSpacing = 12.dp
internal val RequestQrMarkPlate = 56.dp
internal val RequestQrLipaSize = 48.dp
internal val RequestQrBrandSize = 20.dp
private const val RequestQrPeekScale = 0.92f
private const val RequestQrPeekAlpha = 0.48f
private const val RequestQrPeekBlur = 7f
