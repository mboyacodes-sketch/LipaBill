package com.lipabill.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.lipabill.app.data.model.MoneySource
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletCardInkTest {

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance() + 0.05f
        val y = b.luminance() + 0.05f
        return maxOf(x, y) / minOf(x, y)
    }

    /** Card text must read on the lightest part of the face (the base colour). */
    @Test
    fun cardTextReadsOnEveryCard() {
        MoneySource.entries.forEach { source ->
            val ink = walletCardInk(source)
            val face = ink.base
            val accent = contrast(ink.accent, face)
            val primary = contrast(ink.primary, face)
            val muted = contrast(ink.muted.compositeOver(face), face)
            assertTrue("${source.name} accent $accent", accent >= 4.5f)
            assertTrue("${source.name} primary $primary", primary >= 4.5f)
            assertTrue("${source.name} muted $muted", muted >= 4.5f)
        }
    }
}
