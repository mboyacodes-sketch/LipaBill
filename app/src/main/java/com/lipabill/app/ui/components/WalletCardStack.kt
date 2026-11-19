package com.lipabill.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.viewmodel.WalletCardUi
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val CARD_RATIO = 1.62f
private val CardShape = RoundedCornerShape(20.dp)
/** How far each card behind sits above the one in front. */
private val PeekStep = 20.dp
private const val MAX_PEEKS = 2
/** Resting tilt of the front card, as on the template. */
private const val FRONT_TILT_DEG = 2f
/** Max degrees the front card leans with the phone. */
private const val TILT_DEG = 6f
/** Lean below this (of 1) reads as flat. */
private const val TILT_DEADBAND = 0.04f
/** Smallest lean change worth a redraw. */
private const val TILT_STEP = 0.015f

/** App navy / mist — cards sit in the same family as SoftBlue + Accent. */
private val CardNavy = Color(0xFF2F4A6E)
private val CardNavyDeep = Color(0xFF1E3148)
private val CardMist = Color(0xFFE8EEF5)
private val CardPaper = Color(0xFFF7F8FA)

/** Colours for one wallet's card: navy face with a soft brand wash. */
data class WalletCardInk(
    val base: Color,
    val deep: Color,
    val accent: Color,
    val primary: Color,
    val muted: Color
) {
    val highlight: Color get() = accent
    val secondary: Color get() = accent

    val face: Brush get() = Brush.linearGradient(
        listOf(deep, base, lerp(base, CardMist, 0.18f))
    )

    /** Solid band a card shows while it waits behind the front one ([depth] 1 = next, 2 = after). */
    fun layer(depth: Float): Color {
        val next = lerp(base, CardMist, 0.42f)
        val after = lerp(base, CardMist, 0.72f)
        return if (depth <= 1f) next else lerp(next, after, (depth - 1f).coerceIn(0f, 1f))
    }
}

fun walletCardInk(source: MoneySource): WalletCardInk {
    val brand = walletBrand(source).fill
    // Keep MNO identity as a wash on LipaBill navy, not a neon face.
    return WalletCardInk(
        base = lerp(CardNavy, brand, 0.20f),
        deep = lerp(CardNavyDeep, brand, 0.14f),
        accent = lerp(CardMist, brand, 0.22f),
        primary = CardPaper,
        // 0.92: small labels keep 4.5:1 on the lighter washes (MTN yellow).
        muted = CardMist.copy(alpha = 0.92f)
    )
}

/**
 * Home balance as a deck of wallet cards (one per MNO line), floating over a soft ground
 * shadow. The next cards peek above the front one as colour bands. Swiping shuffles: the
 * front card swings out to the side and tucks in at the back while the next one rises into
 * place, all as one continuous motion. Tap flips the front card to [back]; it leans with the phone.
 */
@Composable
fun WalletCardStack(
    cards: List<WalletCardUi>,
    selected: MoneySource?,
    flipped: Boolean,
    onSelect: (MoneySource) -> Unit,
    onFlip: () -> Unit,
    onOpenRequest: () -> Unit,
    modifier: Modifier = Modifier,
    flipHint: String = "Tilt, tap to flip",
    /** Tests only: freezes a leftward shuffle at this progress (0..1). */
    swipePreview: Float? = null,
    back: @Composable ColumnScope.(WalletCardUi, WalletCardInk) -> Unit
) {
    if (cards.isEmpty()) return
    val count = cards.size
    val multiple = count > 1
    val motion = rememberMotionEnabled()
    val scope = rememberCoroutineScope()

    // Shown front card. Updated at once on a swipe so the deck never waits on the ledger.
    // Tracked by wallet, not list position, so a rebuilt card list can never swap the front card.
    var frontSource by remember { mutableStateOf(selected ?: cards.first().source) }
    var pending by remember { mutableStateOf<MoneySource?>(null) }
    /** Shuffle progress 0..1; at 1 the deck has moved one card and [frontSource] advances. */
    val shuffle = remember { Animatable(0f) }
    /** +1 = front card goes to the back (next wallet); -1 = back card comes to the front. */
    var direction by remember { mutableIntStateOf(1) }
    /** Ticks after each settle; restarts the float. */
    var settles by remember { mutableIntStateOf(0) }

    val frontIndex = cards.indexOfFirst { it.source == frontSource }.let { if (it < 0) 0 else it }
    val frontCard = cards[frontIndex]
    /** True while a finger drives the deck; then the animation takes over with no gap. */
    var dragging by remember { mutableStateOf(false) }
    /** Set during and just after a swipe so its release can never read as a tap (flip). */
    var swiping by remember { mutableStateOf(false) }

    /** Runs the rest of a shuffle, then makes the new front card current. */
    suspend fun finishShuffle(step: Int, notify: Boolean) {
        direction = step
        if (motion) {
            shuffle.animateTo(1f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
        }
        val next = (frontIndex + step + count) % count
        if (notify) pending = cards[next].source
        frontSource = cards[next].source
        shuffle.snapTo(0f)
        settles++
        if (notify) onSelect(cards[next].source)
    }

    LaunchedEffect(selected, count) {
        val target = cards.indexOfFirst { it.source == selected }.coerceAtLeast(0)
        if (pending != null) {
            if (selected == pending) pending = null
            return@LaunchedEffect
        }
        val current = frontIndex
        if (selected == null || cards[target].source == frontSource) return@LaunchedEffect
        // Switched from elsewhere (a sheet, Settings): shuffle to it when it is a neighbour.
        when (target) {
            (current + 1) % count -> finishShuffle(1, notify = false)
            (current - 1 + count) % count -> finishShuffle(-1, notify = false)
            else -> { frontSource = cards[target].source; settles++ }
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val deckVisible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // State held without `by` so tilt frames redraw graphicsLayer only, not CardFace.
    val tiltState = rememberTilt(enabled = motion && deckVisible)
    val flip by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "walletCardFlip"
    )
    // Optional float; FLOAT_BREATHS=0 keeps the deck still after settle (no idle redraw).
    val bob = remember { Animatable(0f) }
    LaunchedEffect(settles, motion, deckVisible) {
        if (!motion || !deckVisible || FLOAT_BREATHS <= 0) {
            bob.snapTo(0f)
            return@LaunchedEffect
        }
        repeat(FLOAT_BREATHS) {
            bob.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
            bob.animateTo(0f, tween(1500, easing = FastOutSlowInEasing))
        }
    }
    // Live drag uses a float state so we do not launch a coroutine per pixel.
    var dragProgress by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardHeight: Dp = (maxWidth / CARD_RATIO).coerceAtMost(240.dp)
            val peeks = minOf(MAX_PEEKS, count - 1)
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            // One source at a time: the finger while dragging, otherwise the animation. Never
            // falls back to a reset value between them (that flashed the old card back).
            val progress = swipePreview ?: if (dragging) dragProgress else shuffle.value
            val step = if (swipePreview != null) 1 else direction
            val lastDepth = (count - 1).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight + PeekStep * peeks + FLOAT_GAP + GroundHeight)
                    .pointerInput(multiple, frontIndex) {
                        if (!multiple) return@pointerInput
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragged = 0f
                                dragProgress = 0f
                                dragging = true
                                swiping = true
                            },
                            onDragEnd = {
                                scope.launch {
                                    launch { kotlinx.coroutines.delay(350); swiping = false }
                                    val p = dragProgress
                                    // Hand over at the same progress before the finger lets go of it.
                                    shuffle.snapTo(p)
                                    dragging = false
                                    dragProgress = 0f
                                    if (p >= SHUFFLE_COMMIT) {
                                        finishShuffle(direction, notify = true)
                                    } else {
                                        shuffle.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    shuffle.snapTo(dragProgress)
                                    dragging = false
                                    swiping = false
                                    dragProgress = 0f
                                    shuffle.animateTo(0f, spring())
                                }
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragged += amount
                                // Left = next wallet, right = previous; the finger leads the first half.
                                direction = if (dragged <= 0f) 1 else -1
                                dragProgress = (abs(dragged) / (size.width * SWING)).coerceIn(0f, 0.5f)
                            }
                        )
                    }
            ) {
                // Ground shadow: the deck hovers above it; it tightens as the card rises.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.84f)
                        .height(GroundHeight)
                        .graphicsLayer {
                            val lift = bob.value
                            scaleX = 1f - 0.06f * lift
                            alpha = 1f - 0.25f * lift
                        }
                        .drawBehind {
                            // A circular falloff squashed flat, so every edge of the shadow is soft.
                            scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                                drawCircle(
                                    Brush.radialGradient(
                                        0f to Color.Black.copy(alpha = 0.20f),
                                        0.45f to Color.Black.copy(alpha = 0.09f),
                                        1f to Color.Transparent,
                                        center = center,
                                        radius = size.width / 2f
                                    ),
                                    radius = size.width / 2f,
                                    center = center
                                )
                            }
                        }
                )
                cards.indices.map { k -> k to (k - frontIndex + count) % count }
                    .sortedByDescending { it.second }
                    .forEach { (k, rest) ->
                        val card = cards[k]
                        // Where this card is right now, mid-shuffle.
                        val moving = (step > 0 && rest == 0) || (step < 0 && rest == count - 1)
                        val eased = smoothstep(progress)
                        val depth = when {
                            !moving -> rest - step * progress
                            step > 0 -> lastDepth * eased
                            else -> lastDepth * (1f - eased)
                        }
                        val swing = if (moving) kotlin.math.sin(Math.PI * progress).toFloat() else 0f
                        // The swinging card passes behind (or in front of) the deck at the turn.
                        val onTop = when {
                            !moving -> false
                            step > 0 -> progress < 0.5f
                            else -> progress >= 0.5f
                        }
                        val isFront = rest == 0 && progress == 0f
                        StackCard(
                            card = card,
                            depth = depth,
                            height = cardHeight,
                            swingX = -step * swing * widthPx * SWING,
                            swingTilt = -step * swing * 9f,
                            bob = bob,
                            motionOn = depth < 0.5f,
                            tiltState = tiltState,
                            flip = if (isFront) flip else 0f,
                            interactive = isFront,
                            multiple = multiple,
                            onFlip = { if (!swiping) onFlip() },
                            onOpenRequest = onOpenRequest,
                            onNext = { scope.launch { finishShuffle(1, notify = true) } },
                            onPrevious = { scope.launch { finishShuffle(-1, notify = true) } },
                            back = back,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(if (onTop) 10f else -depth)
                        )
                    }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (multiple) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEachIndexed { i, _ ->
                    val on = i == frontIndex
                    val width by animateFloatAsState(if (on) 30f else 16f, tween(240), label = "dot")
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(if (on) Ink else Hairline)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = Mute, modifier = Modifier.size(16.dp))
            Text(
                text = if (multiple) "$flipHint, swipe to switch" else flipHint,
                style = HomeType.caption,
                color = Mute
            )
        }
    }
}

/** How far (of the deck width) a card swings out at the turn of a shuffle. */
private const val SWING = 0.62f
/** Progress past which letting go completes the shuffle instead of springing back. */
private const val SHUFFLE_COMMIT = 0.16f
/** Float cycles after each settle before the card rests. */
/** Float breaths after each settle, then still: floats without endless idle redraws. */
private const val FLOAT_BREATHS = 3
private val FLOAT_GAP = 14.dp
private val FLOAT_RISE = 5.dp
private val GroundHeight = 26.dp

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Pose of a card at [depth] (0 = front, 1 = next, 2 = after), interpolated between. */
private data class DeckPose(val lift: Dp, val scale: Float, val tilt: Float)

private fun deckPose(depth: Float): DeckPose {
    val d = depth.coerceIn(0f, MAX_PEEKS.toFloat() + 1f)
    return DeckPose(
        lift = -(PeekStep * d.coerceAtMost(MAX_PEEKS.toFloat())),
        scale = 1f - 0.035f * d,
        tilt = FRONT_TILT_DEG * (1f - d).coerceAtLeast(0f)
    )
}

/**
 * One card anywhere in the deck. At [depth] 0 it is the full face (with tilt, glow, flip and
 * a deep floating shadow); further back a solid colour band covers it. Beyond the visible
 * peeks it fades out, so a card arriving at or leaving the back never pops.
 */
@Composable
private fun StackCard(
    card: WalletCardUi,
    depth: Float,
    height: Dp,
    swingX: Float,
    swingTilt: Float,
    bob: Animatable<Float, *>,
    motionOn: Boolean,
    tiltState: State<Offset>,
    flip: Float,
    interactive: Boolean,
    multiple: Boolean,
    onFlip: () -> Unit,
    onOpenRequest: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    back: @Composable ColumnScope.(WalletCardUi, WalletCardInk) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = remember(card.source) { walletCardInk(card.source) }
    val pose = deckPose(depth)
    val band = depth.coerceIn(0f, 1f)
    val lean = 1f - band
    val hidden = (depth - MAX_PEEKS).coerceIn(0f, 1f)
    // Fixed elevations — continuous shadow elevation with tilt was a GPU tax.
    val elevation = if (lean > 0.55f) 14.dp else if (lean > 0.15f) 8.dp else 3.dp
    Box(
        modifier = modifier
            .padding(bottom = FLOAT_GAP + GroundHeight / 2)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val float = if (motionOn) bob.value else 0f
                val tilt = if (motionOn) tiltState.value else Offset.Zero
                translationX = swingX
                translationY = pose.lift.toPx() - FLOAT_RISE.toPx() * float
                scaleX = pose.scale
                scaleY = pose.scale
                rotationZ = pose.tilt + swingTilt
                rotationX = -tilt.y * TILT_DEG * lean
                rotationY = flip + tilt.x * TILT_DEG * lean
                cameraDistance = 16f * density
                alpha = 1f - hidden
            }
            .shadow(
                elevation = elevation,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(CardShape)
            .background(ink.face)
            .then(
                if (interactive) {
                    Modifier
                        .clickable(role = Role.Button, onClickLabel = "Flip card", onClick = onFlip)
                        .semantics {
                            if (multiple) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Next wallet") { onNext(); true },
                                    CustomAccessibilityAction("Previous wallet") { onPrevious(); true }
                                )
                            }
                        }
                } else {
                    Modifier.clearAndSetSemantics { }
                }
            )
    ) {
        if (flip > 90f) {
            // Counter-rotate so the back reads the right way round.
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardBack(card, ink, back)
            }
        } else if (band < 1f) {
            CardFace(card, ink, onOpenRequest = if (interactive) onOpenRequest else null)
        }
        if (band > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(ink.layer(depth).copy(alpha = band)))
        }
    }
}

@Composable
private fun CardFace(card: WalletCardUi, ink: WalletCardInk, onOpenRequest: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Soft static highlight — tilt no longer invalidates this face.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.62f, size.height * 0.42f),
                        radius = size.width * 0.6f
                    )
                )
            }
    ) {
        Watermark(card.source.label, ink)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 18.dp, top = 22.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.source.label.uppercase(),
                        color = ink.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(card.network, if (card.source.isWallet) "Mobile money" else "Bank")
                            .joinToString(" · "),
                        color = ink.accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .offset(x = 6.dp, y = (-4).dp)
                        .then(
                            if (onOpenRequest != null) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClickLabel = "Request money",
                                    onClick = onOpenRequest
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.QrCode2,
                        contentDescription = "Request",
                        tint = ink.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                LipaBillMark(size = 52.dp, elevated = false)
            }
            NumberRow(card.lastDigits, ink)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    CardLabel("AVAILABLE BALANCE", ink)
                    Spacer(modifier = Modifier.height(4.dp))
                    BalanceAmountRow(
                        balance = card.balance,
                        amountStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        contentColor = ink.primary
                    )
                }
                Column(modifier = Modifier.width(72.dp)) {
                    CardLabel("SIM", ink)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.simSlot?.let { "••/$it" } ?: "••/••",
                        color = ink.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                WalletMark(card.source, size = 38.dp)
            }
        }
    }
}

/** Card back: a magnetic-stripe band, then the caller's content. */
@Composable
private fun CardBack(
    card: WalletCardUi,
    ink: WalletCardInk,
    back: @Composable ColumnScope.(WalletCardUi, WalletCardInk) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Watermark(card.source.label, ink)
        Box(
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .height(36.dp)
                .background(Color.Black.copy(alpha = 0.55f))
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 22.dp, top = 72.dp, bottom = 20.dp),
            content = { back(card, ink) }
        )
    }
}

@Composable
private fun NumberRow(lastDigits: String?, ink: WalletCardInk) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 14.sp, letterSpacing = 1.sp)) {
                append("• • • •    • • • •    • • • •    ")
            }
            withStyle(SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)) {
                append(lastDigits ?: "• • • •")
            }
        },
        color = ink.accent,
        maxLines = 1
    )
}

@Composable
private fun BoxScope.Watermark(label: String, ink: WalletCardInk) {
    Text(
        text = label.lowercase(),
        color = ink.accent.copy(alpha = 0.07f),
        fontSize = 96.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = 34.dp)
    )
}

/** Small spaced caps label on a card ("AVAILABLE BALANCE", "SIM"). */
@Composable
fun CardLabel(text: String, ink: WalletCardInk) {
    Text(
        text = text,
        style = TextStyle(fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
        color = ink.muted
    )
}

/** False when the system's animator scale is 0 ("Remove animations"). */
@Composable
private fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

/**
 * Phone lean relative to how it was held when the card appeared, in -1..1 on each axis,
 * smoothed. Zero when motion is off or the phone has no motion sensor.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            state.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        var baseline: Offset? = null
        var smooth = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = Offset(-event.values[0], event.values[1])
                val start = baseline ?: raw.also { baseline = it }
                val lean = Offset(
                    ((raw.x - start.x) / 4f).coerceIn(-1f, 1f),
                    ((raw.y - start.y) / 4f).coerceIn(-1f, 1f)
                )
                smooth = Offset(smooth.x + (lean.x - smooth.x) * 0.12f, smooth.y + (lean.y - smooth.y) * 0.12f)
                // Drift slowly to the new resting angle so the card does not stay leaned.
                baseline = Offset(start.x + (raw.x - start.x) * 0.01f, start.y + (raw.y - start.y) * 0.01f)
                // Ignore sensor noise: a still phone must not redraw the card every frame.
                val shown = state.value
                val settled = if (abs(smooth.x) < TILT_DEADBAND && abs(smooth.y) < TILT_DEADBAND) Offset.Zero else smooth
                if (abs(settled.x - shown.x) >= TILT_STEP || abs(settled.y - shown.y) >= TILT_STEP) {
                    state.value = settled
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return state
}
