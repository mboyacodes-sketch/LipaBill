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
        assertEquals(2, state.tape.size)
        assertTrue(state.canUseTotal)
        assertEquals("250.5", state.withEntryAdded().totalAsAmountInput())
    }

    @Test
    fun use_total_includes_current_entry_without_explicit_add() {
        val state = AmountAddUpState(acc = 100.0, entry = "50")
        assertEquals(150.0, state.total, 0.001)
        assertEquals("150", state.totalAsAmountInput())
    }

    @Test
    fun clear_resets() {
        val state = AmountAddUpState(entry = "10", acc = 5.0, tape = listOf("5")).cleared()
        assertEquals("", state.entry)
        assertTrue(state.tape.isEmpty())
        assertFalse(state.canUseTotal)
    }

    @Test
    fun seeded_from_payment_pad_becomes_first_line() {
        val state = AmountAddUpState.seededFrom("150")
        assertEquals(150.0, state.acc)
        assertEquals("", state.entry)
        assertEquals(150.0, state.total, 0.001)
        assertTrue(state.canUseTotal)
    }

    @Test
    fun seeded_incomplete_stays_as_entry() {
        val state = AmountAddUpState.seededFrom("12.")
        assertTrue(state.tape.isEmpty())
        assertEquals("12.", state.entry)
    }

    @Test
    fun rejects_zero_entry() {
        assertFalse(AmountAddUpState(entry = "0").canAddEntry)
        assertFalse(AmountAddUpState(entry = "").canAddEntry)
    }

    @Test
    fun multiplies_subtracts_and_divides() {
        val times = AmountAddUpState()
            .withKey("2").withKey("5").withKey("0")
            .withOperator(AmountOp.MUL)
            .withKey("3")
        assertEquals(750.0, times.total, 0.001)
        assertEquals("750", times.totalAsAmountInput())

        val minus = AmountAddUpState()
            .withKey("1").withKey("0").withKey("0").withKey("0")
            .withOperator(AmountOp.SUB)
            .withKey("2").withKey("0").withKey("0")
        assertEquals(800.0, minus.total, 0.001)

        val split = AmountAddUpState()
            .withKey("2").withKey("4").withKey("0").withKey("0")
            .withOperator(AmountOp.DIV)
            .withKey("4")
        assertEquals(600.0, split.total, 0.001)
    }

    @Test
    fun chains_multiply_then_subtract() {
        val state = AmountAddUpState()
            .withKey("2").withKey("5").withKey("0")
            .withOperator(AmountOp.MUL)
            .withKey("3")
            .withOperator(AmountOp.SUB)
            .withKey("5").withKey("0")
        assertEquals(700.0, state.total, 0.001)
        assertEquals("700", state.totalAsAmountInput())
    }

    @Test
    fun seeded_operator_applies_to_the_next_number() {
        val state = AmountAddUpState.seededFrom("150", AmountOp.MUL).withKey("2")
        assertEquals(300.0, state.total, 0.001)
    }
}
