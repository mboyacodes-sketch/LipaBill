package com.lipabill.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.R
import com.lipabill.app.ui.privacy.LocalRecordingPrivacy
import kotlinx.coroutines.flow.MutableStateFlow

/** Pay and send sheet width. The rest of the UI follows the window size. */
val ContentMaxWidth = 600.dp

/**
 * Night palette keeps the same navy family as the day screens.
 * Surfaces step from a blue-black canvas to a lifted card. Text is off-white,
 * not pure white. Money green and red are lifted so they stay readable.
 * Passes do not use this palette — they stay on [LipaPalette.Light].
 */
data class LipaColors(
    val canvas: Color,
    val softBlue: Color,
    val ink: Color,
    val mute: Color,
    val hairline: Color,
    val card: Color,
    val income: Color,
    val expense: Color,
    val accent: Color,
    val labelBlue: Color,
    val onAccent: Color
)

object LipaPalette {
    val Light = LipaColors(
        canvas = Color(0xFFF6F7F9),
        softBlue = Color(0xFFE8EEF5),
        ink = Color(0xFF111827),
        mute = Color(0xFF6B7280),
        hairline = Color(0xFFE5E7EB),
        card = Color(0xFFFFFFFF),
        income = Color(0xFF16A34A),
        expense = Color(0xFFDC2626),
        accent = Color(0xFF2F4A6E),
        labelBlue = Color(0xFF9BB0C9),
        onAccent = Color(0xFFFFFFFF)
    )

    val Dark = LipaColors(
        canvas = Color(0xFF12161C),
        softBlue = Color(0xFF2A3544),
        ink = Color(0xFFF2F4F7),
        mute = Color(0xFFA7B0BE),
        hairline = Color(0xFF2E3846),
        card = Color(0xFF1C232D),
        income = Color(0xFF3DDB86),
        expense = Color(0xFFF07167),
        accent = Color(0xFF3D628C),
        labelBlue = Color(0xFF8FA4BE),
        onAccent = Color(0xFFF7F8FA)
    )
}

val LocalLipaColors = staticCompositionLocalOf { LipaPalette.Light }

val Canvas: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.canvas
val SoftBlue: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.softBlue
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.ink
val Mute: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.mute
val Hairline: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.hairline
val CardWhite: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.card
val Income: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.income
val Expense: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.expense
/** Debit amounts on the home list follow the primary text color. */
val Debit: Color
    @Composable @ReadOnlyComposable get() = Ink
/** Travel-route navy, lifted in dark mode so filled buttons stay visible. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.accent
/** Text and icons that sit on [Accent]. */
val OnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.onAccent
val RouteBlue: Color
    @Composable @ReadOnlyComposable get() = Accent
val LabelBlue: Color
    @Composable @ReadOnlyComposable get() = LocalLipaColors.current.labelBlue

/** Letters on the light avatar discs. Stays dark in both themes. */
val OnPastel = Color(0xFF111827)

/** Icons on the light Pay / Metrics / Passes discs. Stays navy in both themes. */
val BrandNavy = Color(0xFF2F4A6E)

/** Pastel fills for Pay / Metrics / Passes action circles. Same in both themes. */
val ActionPay = Color(0xFFD6E4F2)
val ActionMetrics = Color(0xFFF3E9C8)
val ActionTickets = Color(0xFFDCE8F5)
/** Deterministic avatar backgrounds (home, sheets, frequent). */
val AvatarPastels = listOf(
    Color(0xFFDCE8F5),
    Color(0xFFE8DCF5),
    Color(0xFFDCF5E8),
    Color(0xFFF5E8DC),
    Color(0xFFE8F0DC),
    Color(0xFFF5DCDC)
)

/** Boarding passes, bookings, and event tickets stay paper-colored in dark mode. */
@Composable
fun PaperTicket(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLipaColors provides LipaPalette.Light, content = content)
}

fun avatarPastel(seed: Int): Color {
    val i = kotlin.math.abs(seed) % AvatarPastels.size
    return AvatarPastels[i]
}

private fun LipaColors.toScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = softBlue,
        onSecondary = ink,
        tertiary = softBlue,
        onTertiary = ink,
        background = canvas,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = softBlue,
        onSurfaceVariant = mute,
        outline = hairline,
        error = expense,
        onError = onAccent
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = softBlue,
        onSecondary = ink,
        tertiary = softBlue,
        onTertiary = ink,
        background = canvas,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceVariant = softBlue,
        onSurfaceVariant = mute,
        outline = hairline,
        error = expense,
        onError = onAccent
    )
}

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
    val scale: Float
)

fun appSpace(baseSp: Int): AppSpace {
    val scale = (baseSp.coerceIn(8, 18) / UI_FONT_BASELINE_SP.toFloat())
    fun s(v: Float): Dp = (v * scale).dp
    return AppSpace(
        page = s(12f),
        pageV = s(8f),
        section = s(10f),
        block = s(6f),
        gap = s(4f),
        tight = s(2f),
        row = s(6f),
        chip = s(8f),
        card = s(10f),
        cardH = s(11f),
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
    val sheetAmount: TextStyle,
    val sheetHeroAmount: TextStyle,
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
        balance = scaledStyle(44f, scale, FontWeight.Normal, line = 52f, tracking = -0.4f),
        greeting = scaledStyle(17f, scale, FontWeight.SemiBold, line = 22f),
        section = scaledStyle(15f, scale, FontWeight.SemiBold, line = 20f),
        rowTitle = scaledStyle(14f, scale, FontWeight.SemiBold, line = 18f),
        amount = scaledStyle(14f, scale, FontWeight.SemiBold, line = 18f),
        body = scaledStyle(12f, scale, FontWeight.Normal, line = 16f),
        caption = scaledStyle(11f, scale, FontWeight.Normal, line = 14f),
        label = scaledStyle(11f, scale, FontWeight.Medium, line = 14f),
        sheetAmount = scaledStyle(30f, scale, FontWeight.Normal, line = 34f, tracking = -0.3f),
        sheetHeroAmount = scaledStyle(48f, scale, FontWeight.Normal, line = 54f, tracking = -0.8f),
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val typeScale = remember(fontSizeSp) { appTypeScale(fontSizeSp) }
    val space = remember(fontSizeSp) { appSpace(fontSizeSp) }
    val typography = remember(fontSizeSp) { lipaTypography(fontSizeSp) }
    val colors = if (darkTheme) LipaPalette.Dark else LipaPalette.Light
    val app = LocalContext.current.applicationContext as? LipaBillApp
    val privacyFlow = remember(app) {
        app?.recordingPrivacy ?: MutableStateFlow(false)
    }
    val recordingPrivacy by privacyFlow.collectAsStateWithLifecycle()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val bar = if (darkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
            activity.enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            activity.window.decorView.setBackgroundColor(colors.canvas.toArgb())
        }
    }
    CompositionLocalProvider(
        LocalAppType provides typeScale,
        LocalAppSpace provides space,
        LocalLipaColors provides colors,
        LocalRecordingPrivacy provides recordingPrivacy
    ) {
        MaterialTheme(
            colorScheme = colors.toScheme(darkTheme),
            typography = typography,
            content = content
        )
    }
}

private fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
