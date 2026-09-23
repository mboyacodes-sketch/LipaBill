package com.lipabill.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun sanitize_keeps_digits_and_two_decimal_places() {
        assertEquals("1500.5", sanitizeAmountInput("1,500.5"))
        assertEquals("1500.56", sanitizeAmountInput("1500.567"))
        assertEquals("0.5", sanitizeAmountInput(".5"))
        assertEquals("0.", sanitizeAmountInput("0."))
        assertEquals("", sanitizeAmountInput(""))
    }

    @Test
    fun sanitize_strips_leading_zeros() {
        assertEquals("5", sanitizeAmountInput("05"))
        assertEquals("0", sanitizeAmountInput("00"))
        assertEquals("0.5", sanitizeAmountInput("00.5"))
    }

    @Test
    fun display_groups_thousands_while_typing() {
        assertEquals("0", formatMoneyInputDisplay(""))
        assertEquals("1,500", formatMoneyInputDisplay("1500"))
        assertEquals("1,500.", formatMoneyInputDisplay("1500."))
        assertEquals("1,500.5", formatMoneyInputDisplay("1500.5"))
        assertEquals("1,500.50", formatMoneyInputDisplay("1500.50"))
        assertEquals("1,234,567", formatMoneyInputDisplay("1234567"))
    }

    @Test
    fun labels_include_kes_prefix() {
        assertEquals("KES 0", formatMoneyInputLabel(""))
        assertEquals("KES 1,500", formatMoneyInputLabel("1500"))
        assertEquals("KES 1,500.00", formatKesMoney(1500.0))
        assertEquals("KES 1,500.50", formatKesMoney(1500.5))
    }
}
