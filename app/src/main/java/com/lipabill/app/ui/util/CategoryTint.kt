package com.lipabill.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.theme.LipaPalette
import com.lipabill.app.ui.theme.LocalLipaColors

private val categoryOrder = listOf(
    TransactionType.SENT,
    TransactionType.RECEIVED,
    TransactionType.PAYBILL,
    TransactionType.BUY_GOODS,
    TransactionType.POCHI,
    TransactionType.AIRTIME,
    TransactionType.SAVINGS,
    TransactionType.WITHDRAW,
    TransactionType.DEPOSIT,
    TransactionType.REVERSED,
    TransactionType.FULIZA,
    TransactionType.UNKNOWN
)

fun List<TransactionType>.inCategoryOrder(): List<TransactionType> =
    distinct().sortedBy { type ->
        categoryOrder.indexOf(type).let { if (it < 0) categoryOrder.size else it }
    }

/** Light disc behind the avatar initial. Same in both themes, like the other avatars. */
fun TransactionType.categoryDisc(): Color = when (this) {
    TransactionType.SENT -> Color(0xFFD5E0EE)
    TransactionType.RECEIVED -> Color(0xFFD7EADF)
    TransactionType.PAYBILL -> Color(0xFFE0DCEC)
    TransactionType.BUY_GOODS -> Color(0xFFF0E2D4)
    TransactionType.POCHI -> Color(0xFFD4E8E6)
    TransactionType.WITHDRAW -> Color(0xFFE8E0D8)
    TransactionType.DEPOSIT -> Color(0xFFE2E8D4)
    TransactionType.REVERSED -> Color(0xFFF0D8DC)
    TransactionType.FULIZA -> Color(0xFFF3E6CC)
    TransactionType.SAVINGS -> Color(0xFFDCE8D4)
    TransactionType.AIRTIME -> Color(0xFFE4DCEC)
    TransactionType.UNKNOWN -> Color(0xFFE6E8EC)
}

/** Muted caption and selected-chip ink. */
@Composable
@ReadOnlyComposable
fun TransactionType.categoryInk(): Color {
    val dark = LocalLipaColors.current.canvas == LipaPalette.Dark.canvas
    return if (dark) categoryInkDark() else categoryInkLight()
}

/** Selected chip fill. Quiet on the home canvas. */
@Composable
@ReadOnlyComposable
fun TransactionType.categoryWash(): Color {
    val dark = LocalLipaColors.current.canvas == LipaPalette.Dark.canvas
    return if (dark) categoryInk().copy(alpha = 0.22f) else categoryDisc()
}

private fun TransactionType.categoryInkLight(): Color = when (this) {
    TransactionType.SENT -> Color(0xFF4C6280)
    TransactionType.RECEIVED -> Color(0xFF3D7A56)
    TransactionType.PAYBILL -> Color(0xFF5C5878)
    TransactionType.BUY_GOODS -> Color(0xFF8A6848)
    TransactionType.POCHI -> Color(0xFF3E6F6C)
    TransactionType.WITHDRAW -> Color(0xFF7A6558)
    TransactionType.DEPOSIT -> Color(0xFF5E6E42)
    TransactionType.REVERSED -> Color(0xFF8A5560)
    TransactionType.FULIZA -> Color(0xFF8A6A32)
    TransactionType.SAVINGS -> Color(0xFF4F6B48)
    TransactionType.AIRTIME -> Color(0xFF6A5A80)
    TransactionType.UNKNOWN -> Color(0xFF6B7280)
}

private fun TransactionType.categoryInkDark(): Color = when (this) {
    TransactionType.SENT -> Color(0xFFB7C6D8)
    TransactionType.RECEIVED -> Color(0xFF9ED4B4)
    TransactionType.PAYBILL -> Color(0xFFC4BED4)
    TransactionType.BUY_GOODS -> Color(0xFFD8C0A8)
    TransactionType.POCHI -> Color(0xFFA9D0CC)
    TransactionType.WITHDRAW -> Color(0xFFD2C4B8)
    TransactionType.DEPOSIT -> Color(0xFFC5D2A8)
    TransactionType.REVERSED -> Color(0xFFE0B4BC)
    TransactionType.FULIZA -> Color(0xFFE4D0A0)
    TransactionType.SAVINGS -> Color(0xFFB7CBA8)
    TransactionType.AIRTIME -> Color(0xFFD0C4E0)
    TransactionType.UNKNOWN -> Color(0xFFA7B0BE)
}
