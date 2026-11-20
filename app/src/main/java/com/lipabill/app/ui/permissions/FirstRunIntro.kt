package com.lipabill.app.ui.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.ActionTickets
import com.lipabill.app.ui.theme.AvatarPastels
import com.lipabill.app.ui.theme.BrandNavy
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space

/** How long each country is showcased before the next takes over. */
private const val SHOWCASE_MS = 2600L

/** One full lap of the flag orbit. */
private const val ORBIT_TURN_MS = 24_000

/** Hero card stays near-black in both themes, like a physical card. */
private val HeroCard = Color(0xFF111827)
private val OnHeroCard = Color(0xFFF7F8FA)

/**
 * First-run intro: three swipeable slides (balance, send, plans), dash dots,
 * one pill CTA, and a privacy line where a login link would sit.
 */
@Composable
internal fun FirstRunIntro(
    pager: PagerState,
    region: AppRegion,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chrome = disclosureChrome()
    val slides = FirstRunWizard.INTRO_SLIDES
    val last = pager.currentPage == slides.lastIndex
    // The phone's region leads the orbit; every other country follows in order.
    val orbit = remember(region) { listOf(region) + AppRegion.entries.filter { it != region } }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SHOWCASE_MS)
            tick++
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = (chrome.side - 16.dp).coerceAtLeast(8.dp))
            ) {
                Text("Skip", color = Mute)
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = chrome.side, vertical = chrome.art),
                    contentAlignment = Alignment.Center
                ) {
                    // Send only cycles where Send/Pay work; the rest stay dimmed on the ring.
                    val live = if (index == 1) {
                        orbit.filter { it.canSendPay }.ifEmpty { listOf(region) }
                    } else {
                        orbit
                    }
                    val active = live[tick % live.size]
                    HeroStage(orbit = orbit, active = active, live = live.toSet()) {
                        when (index) {
                            0 -> BalanceHero(active)
                            1 -> SendHero(active)
                            else -> PlanHero(active)
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = chrome.side)
        ) {
            WizardDots(count = slides.size, selected = pager.currentPage)
            Spacer(modifier = Modifier.height(Space.section * 2))
            val slide = slides[pager.currentPage]
            Text(
                text = slide.title,
                style = introHeadline(),
                color = Ink
            )
            Spacer(modifier = Modifier.height(Space.block * 2))
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyLarge,
                color = Mute,
                // Two lines held so the CTA does not jump between slides.
                minLines = 2
            )
            Spacer(modifier = Modifier.height(Space.section * 2))
            WizardPrimaryButton(
                text = if (last) "Get started" else "Continue",
                onClick = onContinue
            )
            Spacer(modifier = Modifier.height(Space.section))
            Text(
                text = FirstRunWizard.INTRO_FOOTER,
                style = MaterialTheme.typography.labelMedium,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun introHeadline() = MaterialTheme.typography.displayLarge.let {
    it.copy(
        fontSize = it.fontSize * 1.08f,
        lineHeight = it.fontSize * 1.08f * 1.14f,
        fontWeight = FontWeight.SemiBold
    )
}

/** Dash dots: the current slide is a short bar, the rest are small dots. */
@Composable
internal fun WizardDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val on = i == selected
            val color by animateColorAsState(if (on) Ink else Hairline, label = "dot")
            val width by animateDpAsState(if (on) 22.dp else 6.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Soft halo + floating card frame shared by the three heroes. Country flags
 * orbit the halo behind the cards; [active] is lifted and drives the figures.
 */
@Composable
private fun HeroStage(
    orbit: List<AppRegion>,
    active: AppRegion,
    live: Set<AppRegion>,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        val halo = minOf(maxWidth, maxHeight) * 0.92f
        Box(
            modifier = Modifier
                .size(halo)
                .clip(CircleShape)
                .border(1.dp, Hairline, CircleShape)
        )
        RegionOrbit(orbit = orbit, active = active, live = live, radius = halo / 2)
        Box(modifier = Modifier.widthIn(max = 300.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** Light card stock and its ink — fixed, so the wallet reads as an object in both themes. */
private val CardStock = Color(0xFFF0F0F0)
private val OnCardStock = Color(0xFF111827)

/**
 * Black wallet: two cards tucked into the top, the pocket front with the
 * balance, and a "Money received" slip overlapping the bottom edge.
 */
@Composable
private fun BalanceHero(region: AppRegion) {
    val demo = regionDemo(region)
    Column(
        modifier = Modifier.width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(HeroCard)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
        ) {
            WalletCardEdge(digits = "3588", modifier = Modifier.padding(horizontal = 6.dp))
            WalletCardEdge(digits = "6503", modifier = Modifier.offset(y = (-6).dp))
            // Pocket front rides over the lower card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(HeroCard)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(OnHeroCard.copy(alpha = 0.22f))
                )
                Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        CircleGlyph(Icons.Filled.Add, fill = OnHeroCard.copy(alpha = 0.16f), tint = OnHeroCard)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = OnHeroCard,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Everyday account",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnHeroCard
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val balance = "${region.currencySymbol} ${demo.balance}"
                // Monospace runs wide; step down for six-figure currencies so it fits the wallet.
                val balanceSize = if (balance.length > 13) 21.sp else 25.sp
                RollingText(
                    text = balance,
                    style = introHeadline().copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = balanceSize,
                        lineHeight = balanceSize * 1.2f,
                        letterSpacing = (-0.5).sp
                    ),
                    color = OnHeroCard
                )
                Text(
                    text = "Current balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnHeroCard.copy(alpha = 0.55f)
                )
            }
        }
        // Slip tucked over the wallet's bottom edge.
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .offset(y = (-30).dp)
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(CardStock)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleGlyph(Icons.Filled.SouthWest, fill = HeroCard, tint = OnHeroCard, size = 30.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Money received",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnCardStock
                )
                Text(
                    text = "Today, 10:24 AM",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnCardStock.copy(alpha = 0.55f)
                )
            }
            RollingText(
                text = "+${region.currencySymbol} ${demo.received}.00",
                style = MaterialTheme.typography.labelMedium,
                color = OnCardStock
            )
        }
    }
}

/** Top edge of a card peeking out of the wallet: card glyph + last four. */
@Composable
private fun WalletCardEdge(digits: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(CardStock)
            .border(1.dp, Color(0x14000000), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(top = 9.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.CreditCard,
            contentDescription = null,
            tint = OnCardStock.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "•••• $digits",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = OnCardStock
        )
    }
}

@Composable
private fun SendHero(region: AppRegion) {
    val demo = regionDemo(region)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Faint outer pills, then two friends, then the payee up front.
            Row(horizontalArrangement = Arrangement.spacedBy(176.dp)) {
                GhostPill()
                GhostPill()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                FacePill(Faces.Glasses, Color(0xFFBFD9F2), 76.dp)
                FacePill(Faces.Cap, Color(0xFFC4E5C2), 76.dp)
            }
            FacePill(
                Faces.Grandpa,
                Color(0xFFF5C77E),
                128.dp,
                Modifier
                    .shadow(10.dp, RoundedCornerShape(64.dp))
                    .border(4.dp, CardWhite, RoundedCornerShape(64.dp))
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        // Tilted like a card someone just slid across the table.
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .graphicsLayer {
                    rotationZ = -3f
                    cameraDistance = 12f * density
                    rotationX = 6f
                }
        ) {
            HeroRow(
                lead = null,
                title = "Send money to ${demo.payee}",
                caption = null,
                amount = "${region.currencySymbol} ${demo.sent}",
                trail = { CircleGlyph(Icons.AutoMirrored.Filled.ArrowForward, fill = HeroCard, tint = OnHeroCard, size = 34.dp) }
            )
        }
    }
}

@Composable
private fun GhostPill() {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Hairline.copy(alpha = 0.6f))
    )
}

@Composable
private fun FacePill(face: Face, fill: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = height * 0.74f, height = height)
            .clip(RoundedCornerShape(height / 2))
            .background(fill)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawFace(face) }
    }
}

/** Original flat cartoon people — a nod to the reference's avatars, not copies of them. */
private enum class Hair { Short, Curly, Cap }

private data class Face(
    val skin: Color,
    val hair: Color,
    val style: Hair,
    val shirt: Color,
    val glasses: Boolean = false,
    val beard: Color? = null,
    val grin: Boolean = false,
    val cap: Color = Color.Transparent
)

private object Faces {
    val Glasses = Face(
        skin = Color(0xFFF1C7A5),
        hair = Color(0xFF6B4423),
        style = Hair.Short,
        shirt = Color(0xFF2F4A6E),
        glasses = true
    )
    val Grandpa = Face(
        skin = Color(0xFF7A4A2E),
        hair = Color(0xFFCFCFCF),
        style = Hair.Curly,
        shirt = Color(0xFF3F5F45),
        glasses = true,
        beard = Color(0xFFBDBDBD),
        grin = true
    )
    val Cap = Face(
        skin = Color(0xFFC98B5E),
        hair = Color(0xFF2B1B12),
        style = Hair.Cap,
        shirt = Color(0xFFF2A65A),
        cap = Color(0xFFE86A8A)
    )
}

private fun DrawScope.drawFace(face: Face) {
    val w = size.width
    val h = size.height
    val ink = Color(0xFF1F2937)
    // Shoulders and neck.
    drawRoundRect(
        color = face.shirt,
        topLeft = Offset(w * 0.08f, h * 0.80f),
        size = Size(w * 0.84f, h * 0.4f),
        cornerRadius = CornerRadius(w * 0.4f)
    )
    drawRect(face.skin.darker(), Offset(w * 0.40f, h * 0.62f), Size(w * 0.20f, h * 0.2f))
    // Ears, then head.
    drawCircle(face.skin, w * 0.07f, Offset(w * 0.22f, h * 0.50f))
    drawCircle(face.skin, w * 0.07f, Offset(w * 0.78f, h * 0.50f))
    val head = Rect(w * 0.22f, h * 0.27f, w * 0.78f, h * 0.70f)
    drawOval(face.skin, head.topLeft, head.size)
    // Hair.
    when (face.style) {
        Hair.Short -> drawArc(
            color = face.hair,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(head.left - w * 0.02f, head.top - h * 0.03f),
            size = Size(head.width + w * 0.04f, head.height * 0.62f)
        )
        Hair.Curly -> {
            val r = w * 0.085f
            for (i in 0..6) {
                val t = i / 6f
                val x = head.left + head.width * t
                val y = head.top + h * 0.03f - sin(t * Math.PI).toFloat() * h * 0.05f
                drawCircle(face.hair, r, Offset(x, y))
            }
            drawCircle(face.hair, r, Offset(head.left + w * 0.02f, head.top + h * 0.09f))
            drawCircle(face.hair, r, Offset(head.right - w * 0.02f, head.top + h * 0.09f))
        }
        Hair.Cap -> {
            drawRect(face.hair, Offset(head.left, head.top + h * 0.06f), Size(head.width, h * 0.06f))
            drawArc(
                color = face.cap,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(head.left - w * 0.03f, head.top - h * 0.06f),
                size = Size(head.width + w * 0.06f, head.height * 0.6f)
            )
            drawRoundRect(
                color = face.cap.darker(),
                topLeft = Offset(head.left - w * 0.06f, head.top + h * 0.06f),
                size = Size(head.width + w * 0.12f, h * 0.035f),
                cornerRadius = CornerRadius(h * 0.02f)
            )
        }
    }
    // Eyes, brows, glasses.
    val eyeY = h * 0.50f
    val eyes = listOf(w * 0.39f, w * 0.61f)
    eyes.forEach { x ->
        drawCircle(Color.White, w * 0.055f, Offset(x, eyeY))
        drawCircle(ink, w * 0.03f, Offset(x, eyeY + h * 0.004f))
        drawLine(
            face.hair.takeIf { face.style != Hair.Cap } ?: ink,
            Offset(x - w * 0.06f, eyeY - h * 0.055f),
            Offset(x + w * 0.05f, eyeY - h * 0.06f),
            strokeWidth = w * 0.025f,
            cap = StrokeCap.Round
        )
        if (face.glasses) {
            drawCircle(ink, w * 0.09f, Offset(x, eyeY), style = Stroke(width = w * 0.022f))
        }
    }
    if (face.glasses) {
        drawLine(ink, Offset(eyes[0] + w * 0.09f, eyeY), Offset(eyes[1] - w * 0.09f, eyeY), strokeWidth = w * 0.02f)
    }
    // Beard sits under the mouth so the grin reads on top of it.
    face.beard?.let { beard ->
        drawArc(
            color = beard,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(head.left + w * 0.04f, head.top + head.height * 0.42f),
            size = Size(head.width - w * 0.08f, head.height * 0.62f)
        )
    }
    // Cheeks and mouth.
    drawCircle(Color(0x33FF6B6B), w * 0.05f, Offset(w * 0.31f, h * 0.58f))
    drawCircle(Color(0x33FF6B6B), w * 0.05f, Offset(w * 0.69f, h * 0.58f))
    val mouth = Rect(w * 0.40f, h * 0.555f, w * 0.60f, h * 0.635f)
    if (face.grin) {
        drawArc(Color(0xFF5B1F1F), 0f, 180f, true, mouth.topLeft, mouth.size)
        drawArc(Color.White, 0f, 180f, true, mouth.topLeft, Size(mouth.width, mouth.height * 0.55f))
    } else {
        drawArc(
            ink, 20f, 140f, false, mouth.topLeft, mouth.size,
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        )
    }
}

private fun Color.darker(by: Float = 0.12f) = Color(
    red = red * (1 - by),
    green = green * (1 - by),
    blue = blue * (1 - by),
    alpha = alpha
)

@Composable
private fun PlanHero(region: AppRegion) {
    val demo = regionDemo(region)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            FloatTile(Icons.Outlined.CreditCard, ActionPay, Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 14.dp).rotate(-8f))
            FloatTile(Icons.Outlined.Flight, ActionMetrics, Modifier.align(Alignment.Center).offset(x = (-14).dp, y = 22.dp), size = 76.dp)
            FloatTile(Icons.Outlined.Home, ActionTickets, Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = 0.dp).rotate(6f))
            FloatTile(Icons.Outlined.Savings, AvatarPastels[5], Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp).rotate(-4f))
            FloatTile(Icons.Outlined.ConfirmationNumber, AvatarPastels[1], Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = (-2).dp), size = 40.dp)
            PlusDot(Modifier.align(Alignment.CenterEnd).offset(x = 4.dp, y = (-18).dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    RollingText("Trip to ${demo.trip}", style = MaterialTheme.typography.labelMedium, color = Mute)
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingText(
                            text = "${region.currencySymbol} ${demo.saved}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Text(
                            text = "  of ${region.currencySymbol} ${demo.goal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mute,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                CircleGlyph(Icons.Filled.Add, fill = HeroCard, tint = OnHeroCard, size = 34.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Ink)
                )
            }
        }
    }
}

@Composable
private fun FloatTile(
    icon: ImageVector,
    fill: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(size * 0.3f))
            .clip(RoundedCornerShape(size * 0.3f))
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun PlusDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .border(1.dp, Hairline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Mute, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CircleGlyph(
    icon: ImageVector,
    fill: Color,
    tint: Color,
    size: Dp = 28.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** White pill row under a hero: optional lead glyph, label, and an amount or action. */
@Composable
private fun HeroRow(
    lead: (@Composable () -> Unit)?,
    title: String,
    caption: String?,
    trailing: String? = null,
    amount: String? = null,
    trail: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lead != null) {
            lead()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            RollingText(title, style = MaterialTheme.typography.labelMedium, color = if (amount != null) Mute else Ink)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = Mute)
            }
            if (amount != null) {
                RollingText(amount, style = MaterialTheme.typography.titleLarge, color = Ink)
            }
        }
        if (trailing != null) {
            RollingText(trailing, style = MaterialTheme.typography.labelLarge, color = Ink)
        }
        trail?.invoke()
    }
}

/** Demo figures sized to each currency, so Ksh 24k does not read as TSh 24k. */
private data class RegionDemo(
    val balance: String,
    val received: String,
    val payee: String,
    val sent: String,
    val trip: String,
    val saved: String,
    val goal: String
)

private fun regionDemo(region: AppRegion): RegionDemo = when (region) {
    AppRegion.KENYA -> RegionDemo("24,680.00", "2,000", "Wanjiku", "2,000.00", "Diani", "24,000", "30,000")
    AppRegion.TANZANIA -> RegionDemo("482,500.00", "40,000", "Neema", "40,000.00", "Zanzibar", "480,000", "600,000")
    AppRegion.UGANDA -> RegionDemo("685,000.00", "55,000", "Nakato", "55,000.00", "Jinja", "680,000", "850,000")
    AppRegion.RWANDA -> RegionDemo("236,400.00", "20,000", "Uwase", "20,000.00", "Kivu", "240,000", "300,000")
}

/** Flags circling the halo. Angle is read at layout, so the spin never recomposes. */
@Composable
private fun RegionOrbit(
    orbit: List<AppRegion>,
    active: AppRegion,
    live: Set<AppRegion>,
    radius: Dp
) {
    val turn = rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(ORBIT_TURN_MS, easing = LinearEasing)),
        label = "turn"
    )
    val r = with(LocalDensity.current) { radius.toPx() }
    orbit.forEachIndexed { i, option ->
        key(option) {
            val on = option == active
            val scale by animateFloatAsState(if (on) 1.3f else 1f, label = "flagScale")
            val dim by animateFloatAsState(if (option in live) 1f else 0.35f, label = "flagDim")
            val edge by animateColorAsState(if (on) Ink else Hairline, label = "flagEdge")
            Box(
                modifier = Modifier
                    .offset {
                        val a = Math.toRadians((turn.value + i * 360f / orbit.size - 90f).toDouble())
                        IntOffset((r * cos(a)).roundToInt(), (r * sin(a)).roundToInt())
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = dim
                    }
                    .size(32.dp)
                    .shadow(if (on) 6.dp else 0.dp, CircleShape)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(if (on) 1.5.dp else 1.dp, edge, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = option.flagEmoji, fontFamily = FontFamily.Default, fontSize = 16.sp)
            }
        }
    }
}

/** Figures roll up when the showcased country changes. */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "rolling"
    ) { value ->
        Text(text = value, style = style, color = color, maxLines = 1)
    }
}
