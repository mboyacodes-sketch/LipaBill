package com.lipabill.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.R

/** Clean off-white + travel-ticket blues (boarding / booking cards). */
val Canvas = Color(0xFFF6F7F9)
/** Soft chip / avatar fill — matches ticket passenger badge. */
val SoftBlue = Color(0xFFE8EEF5)
/** Primary text & high-contrast chrome. */
val Ink = Color(0xFF111827)
/** Secondary captions (neutral). Prefer [LabelBlue] for labeled fields. */
val Mute = Color(0xFF9CA3AF)
val Hairline = Color(0xFFE5E7EB)
val CardWhite = Color(0xFFFFFFFF)
val Income = Color(0xFF16A34A)
val Expense = Color(0xFFDC2626)
/**
 * Brand / primary actions — travel route blue from ticket cards.
 * Prefer this (or MaterialTheme.colorScheme.primary) for filled buttons & emphasis.
 */
val Accent = Color(0xFF2F4A6E)
/** Headings, route codes, titles — same as ticket “Booking Confirmed” / airport codes. */
val RouteBlue = Accent
/** Uppercase field labels (FLIGHT NO., DATE, …) on travel cards & similar chrome. */
val LabelBlue = Color(0xFF9BB0C9)

private val OffWhiteColors = lightColorScheme(
    primary = Accent,
    onPrimary = CardWhite,
    secondary = SoftBlue,
    onSecondary = Ink,
    tertiary = SoftBlue,
    onTertiary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = SoftBlue,
    onSurfaceVariant = Mute,
    outline = Hairline,
    error = Expense,
    onError = CardWhite
)

/** App typeface: Montserrat — geometric sans matching travel-ticket chrome. */
val GeometricSansFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)
/** Baseline size for the Settings slider (12 sp = scale 1.0). */
const val UI_FONT_BASELINE_SP = 12

/**
 * Dense layout tokens. Baselines are already “squished”; values grow/shrink
 * with the Settings font slider so larger type gets a bit more air.
 */
data class AppSpace(
    val page: Dp,
    val pageV: Dp,
    val section: Dp,
    val block: Dp,
    val gap: Dp,
    val tight: Dp,
    val row: Dp,
    val chip: Dp,
    val card: Dp,
    val cardH: Dp,
    val hero: Dp,
    val button: Dp,
    val scale: Float
)

fun appSpace(baseSp: Int): AppSpace {
    val scale = (baseSp.coerceIn(8, 18) / UI_FONT_BASELINE_SP.toFloat())
    fun s(v: Float): Dp = (v * scale).dp
    return AppSpace(
        page = s(14f),
        pageV = s(10f),
        section = s(14f),
        block = s(8f),
        gap = s(6f),
        tight = s(3f),
        row = s(8f),
        chip = s(10f),
        card = s(12f),
        cardH = s(14f),
        hero = s(10f),
        button = s(10f),
        scale = scale
    )
}

val LocalAppSpace = staticCompositionLocalOf { appSpace(UI_FONT_BASELINE_SP) }

/** App-wide spacing — use instead of raw `N.dp` so density tracks font size. */
object Space {
    val page: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.page
    val pageV: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.pageV
    val section: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.section
    val block: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.block
    val gap: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.gap
    val tight: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.tight
    val row: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.row
    val chip: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.chip
    val card: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.card
    val cardH: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.cardH
    val hero: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.hero
    val button: Dp
        @Composable @ReadOnlyComposable get() = LocalAppSpace.current.button
}

data class AppTypeScale(
    val balance: TextStyle,
    val greeting: TextStyle,
    val section: TextStyle,
    val rowTitle: TextStyle,
    val amount: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val nav: TextStyle,
    val sheetAmount: TextStyle,
    val sheetHeroAmount: TextStyle,
    val sheetTitle: TextStyle,
    /** Pay/Send text fields — regular weight, larger than body. */
    val sheetInput: TextStyle,
    val scale: Float
)

private fun scaledStyle(
    size: Float,
    scale: Float,
    weight: FontWeight,
    line: Float = size * 1.2f,
    tracking: Float = 0f
): TextStyle = TextStyle(
    fontFamily = GeometricSansFamily,
    fontWeight = weight,
    fontSize = (size * scale).sp,
    lineHeight = (line * scale).sp,
    letterSpacing = tracking.sp
)

fun appTypeScale(baseSp: Int): AppTypeScale {
    val scale = (baseSp.coerceIn(8, 18) / UI_FONT_BASELINE_SP.toFloat())
    return AppTypeScale(
        balance = scaledStyle(60f, scale, FontWeight.Normal, line = 68f, tracking = -0.6f),
        greeting = scaledStyle(17f, scale, FontWeight.SemiBold, line = 22f),
        section = scaledStyle(15f, scale, FontWeight.SemiBold, line = 20f),
        rowTitle = scaledStyle(14f, scale, FontWeight.SemiBold, line = 18f),
        amount = scaledStyle(14f, scale, FontWeight.SemiBold, line = 18f),
        body = scaledStyle(12f, scale, FontWeight.Normal, line = 16f),
        caption = scaledStyle(11f, scale, FontWeight.Normal, line = 14f),
        label = scaledStyle(11f, scale, FontWeight.Medium, line = 14f),
        nav = scaledStyle(10f, scale, FontWeight.Medium, line = 13f),
        sheetAmount = scaledStyle(30f, scale, FontWeight.Normal, line = 34f, tracking = -0.3f),
        sheetHeroAmount = scaledStyle(48f, scale, FontWeight.Normal, line = 54f, tracking = -0.8f),
        sheetTitle = scaledStyle(18f, scale, FontWeight.SemiBold, line = 22f, tracking = -0.2f),
        sheetInput = scaledStyle(17f, scale, FontWeight.Normal, line = 22f),
        scale = scale
    )
}

val LocalAppType = staticCompositionLocalOf { appTypeScale(UI_FONT_BASELINE_SP) }

/**
 * App-wide geometric sans scale. Reads the active [LocalAppType] so Settings font size
 * applies on home, sheets, metrics, etc. — not only MaterialTheme roles.
 */
object HomeType {
    val balance: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.balance
    val greeting: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.greeting
    val section: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.section
    val rowTitle: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.rowTitle
    val amount: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.amount
    val body: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.body
    val caption: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.caption
    val label: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.label
    val nav: TextStyle
        @Composable @ReadOnlyComposable get() = LocalAppType.current.nav
}

fun lipaTypography(baseSp: Int): Typography {
    val scale = (baseSp.coerceIn(8, 18) / UI_FONT_BASELINE_SP.toFloat())
    fun s(size: Float, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
        fontFamily = GeometricSansFamily,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (size * scale * 1.3f).sp,
        letterSpacing = tracking.sp
    )
    return Typography(
        displayLarge = s(30f, FontWeight.Bold, -0.4f),
        headlineMedium = s(18f, FontWeight.Bold),
        titleLarge = s(16f, FontWeight.SemiBold),
        titleMedium = s(14f, FontWeight.SemiBold),
        bodyLarge = s(14f),
        bodyMedium = s(12f),
        labelLarge = s(12f, FontWeight.Medium),
        labelMedium = s(11f, FontWeight.Medium),
        labelSmall = s(10f, FontWeight.Medium)
    )
}

@Composable
fun LipaBillTheme(
    fontSizeSp: Int = UI_FONT_BASELINE_SP,
    content: @Composable () -> Unit
) {
    val typeScale = remember(fontSizeSp) { appTypeScale(fontSizeSp) }
    val space = remember(fontSizeSp) { appSpace(fontSizeSp) }
    val typography = remember(fontSizeSp) { lipaTypography(fontSizeSp) }
    CompositionLocalProvider(
        LocalAppType provides typeScale,
        LocalAppSpace provides space
    ) {
        MaterialTheme(
            colorScheme = OffWhiteColors,
            typography = typography,
            content = content
        )
    }
}
