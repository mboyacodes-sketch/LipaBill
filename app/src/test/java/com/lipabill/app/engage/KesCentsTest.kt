package com.lipabill.app.engage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KesCentsTest {

    @Test
    fun kes_to_cents_rounds_half_up() {
        assertEquals(1_500_00L, kesToCents(1_500.0))
        assertEquals(1L, kesToCents(0.005))
        assertEquals(0L, kesToCents(0.004))
        assertNull(kesToCents(null))
        assertNull(kesToCents(Double.NaN))
        assertNull(kesToCents(Double.POSITIVE_INFINITY))
    }

    @Test
    fun cents_to_kes() {
        assertEquals(12.34, centsToKes(1_234L), 0.0)
        assertEquals(0.0, centsToKes(0L), 0.0)
    }
}
