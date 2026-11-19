package com.lipabill.app.ui.components

import androidx.compose.ui.graphics.luminance
import com.lipabill.app.data.model.MoneySource
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletBrandTest {

    @Test
    fun everySourceInitialIsReadable() {
        MoneySource.entries.forEach { source ->
            val brand = walletBrand(source)
            val a = brand.fill.luminance() + 0.05f
            val b = brand.onFill.luminance() + 0.05f
            val ratio = maxOf(a, b) / minOf(a, b)
            assertTrue("${source.name} contrast $ratio", ratio >= 4.5f)
            assertTrue(brand.initial.isNotBlank())
        }
    }
}
