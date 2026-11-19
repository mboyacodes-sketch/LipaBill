package com.lipabill.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.lipabill.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.MoneySource

/**
 * Brand look for a wallet or bank. [logo] is null until an official logo file is added
 * under res/drawable (see [walletBrand]); until then a brand-coloured initial stands in.
 */
data class WalletBrand(
    val fill: Color,
    val onFill: Color,
    val initial: String,
    @DrawableRes val logo: Int? = null,
    /** Width ÷ height of [logo]; wide wordmarks get a pill badge instead of a circle. */
    val logoAspect: Float = 1f
)

private val White = Color(0xFFFFFFFF)

/**
 * One entry per source. Colours are darkened brand hues so the white initial passes 4.5:1.
 * To add a logo: drop e.g. `wallet_logo_mpesa.xml` (vector) or `.webp` into res/drawable and set
 * `logo = R.drawable.wallet_logo_mpesa` here; every screen that shows the wallet picks it up.
 */
fun walletBrand(source: MoneySource): WalletBrand = when (source) {
    // Logos: Wikimedia Commons renders of "M-PESA LOGO-01.svg" and "Airtel Africa logo.svg"
    // (public-domain files; the marks remain Safaricom's / Airtel's trademarks).
    MoneySource.MPESA -> WalletBrand(Color(0xFF15803D), White, "M", R.drawable.wallet_logo_mpesa, logoAspect = 512f / 190f)
    MoneySource.AIRTEL_MONEY -> WalletBrand(Color(0xFFD0021B), White, "A", R.drawable.wallet_logo_airtel, logoAspect = 251f / 256f)
    MoneySource.MTN_MOMO -> WalletBrand(Color(0xFFFFCB05), Color(0xFF111827), "M")
    MoneySource.TKASH -> WalletBrand(Color(0xFF5B2C83), White, "T")
    MoneySource.EQUITEL -> WalletBrand(Color(0xFF8E1F1D), White, "E")
    MoneySource.MIXX -> WalletBrand(Color(0xFF0B3C8C), White, "M")
    MoneySource.HALOPESA -> WalletBrand(Color(0xFFC2410C), White, "H")
    MoneySource.KCB -> WalletBrand(Color(0xFF006B3F), White, "K")
    MoneySource.EQUITY -> WalletBrand(Color(0xFF8E1F1D), White, "E")
    MoneySource.COOP -> WalletBrand(Color(0xFF00573F), White, "C")
    MoneySource.NCBA -> WalletBrand(Color(0xFF1C2E5A), White, "N")
    MoneySource.ABSA -> WalletBrand(Color(0xFFAF144B), White, "A")
    MoneySource.STANBIC -> WalletBrand(Color(0xFF0033A1), White, "S")
    MoneySource.FAMILY -> WalletBrand(Color(0xFF1E3A8A), White, "F")
    MoneySource.I_AND_M -> WalletBrand(Color(0xFF9F1239), White, "I")
    MoneySource.DTB -> WalletBrand(Color(0xFF1F3B73), White, "D")
    MoneySource.STANCHART -> WalletBrand(Color(0xFF0B6A3F), White, "S")
    MoneySource.KWFT -> WalletBrand(Color(0xFF7C2D92), White, "K")
    MoneySource.SIDIAN -> WalletBrand(Color(0xFF0F5132), White, "S")
    MoneySource.NBK -> WalletBrand(Color(0xFF1E3A8A), White, "N")
    MoneySource.POSTBANK -> WalletBrand(Color(0xFFB91C1C), White, "P")
    MoneySource.GTBANK -> WalletBrand(Color(0xFFC2410C), White, "G")
    MoneySource.CRDB -> WalletBrand(Color(0xFF15803D), White, "C")
    MoneySource.NMB -> WalletBrand(Color(0xFF1D4ED8), White, "N")
    MoneySource.NBC -> WalletBrand(Color(0xFF1E3A8A), White, "N")
    MoneySource.CENTENARY -> WalletBrand(Color(0xFF7F1D1D), White, "C")
    MoneySource.BK -> WalletBrand(Color(0xFF1E40AF), White, "B")
}

/** Round wallet/bank mark used wherever a payment's "from" is shown. Decorative: the name sits beside it. */
@Composable
fun WalletMark(
    source: MoneySource,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val brand = walletBrand(source)
    val wide = brand.logo != null && brand.logoAspect > 1.2f
    val shape = if (wide) RoundedCornerShape(percent = 28) else CircleShape
    Box(
        modifier = modifier
            .height(size)
            .width(if (wide) size * brand.logoAspect.coerceAtMost(2.4f) * 0.9f else size)
            .clip(shape)
            .background(if (brand.logo != null) White else brand.fill)
            // A white logo badge needs an edge on white screens (From row, receipts).
            .then(if (brand.logo != null) Modifier.border(1.dp, Color.Black.copy(alpha = 0.08f), shape) else Modifier)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        if (brand.logo != null) {
            Image(
                painter = painterResource(brand.logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * if (wide) 0.12f else 0.18f)
            )
        } else {
            Text(
                text = brand.initial,
                color = brand.onFill,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}
