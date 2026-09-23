package com.lipabill.app.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountAddUpTest {

    @Test
    fun adds_items_and_sums_total() {
        var state = AmountAddUpState()
            .withKey("1").withKey("5").withKey("0")
            .withEntryAdded()
            .withKey("8").withKey("0")
            .withEntryAdded()
            .withKey("2").withKey("0").withKey(".").withKey("5")
        assertEquals(250.5, state.total, 0.001)
        assertEquals(2, state.lines.size)
        assertTrue(state.canUseTotal)
        assertEquals("250.5", state.withEntryAdded().totalAsAmountInput())
    }

    @Test
    fun use_total_includes_current_entry_without_explicit_add() {
        val state = AmountAddUpState(lines = listOf(100.0), entry = "50")
        assertEquals(150.0, state.total, 0.001)
        assertEquals("150", state.totalAsAmountInput())
    }

    @Test
    fun clear_resets() {
        val state = AmountAddUpState(entry = "10", lines = listOf(5.0)).cleared()
        assertEquals("", state.entry)
        assertTrue(state.lines.isEmpty())
        assertFalse(state.canUseTotal)
    }

    @Test
    fun seeded_from_payment_pad_becomes_first_line() {
        val state = AmountAddUpState.seededFrom("150")
        assertEquals(listOf(150.0), state.lines)
        assertEquals("", state.entry)
        assertEquals(150.0, state.total, 0.001)
        assertTrue(state.canUseTotal)
    }

    @Test
    fun seeded_incomplete_stays_as_entry() {
        val state = AmountAddUpState.seededFrom("12.")
        assertTrue(state.lines.isEmpty())
        assertEquals("12.", state.entry)
    }

    @Test
    fun rejects_zero_entry() {
        assertFalse(AmountAddUpState(entry = "0").canAddEntry)
        assertFalse(AmountAddUpState(entry = "").canAddEntry)
    }
}
