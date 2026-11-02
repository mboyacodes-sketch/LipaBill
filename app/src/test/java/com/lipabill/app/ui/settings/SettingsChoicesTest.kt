package com.lipabill.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsChoicesTest {

    @Test
    fun lock_presets_keep_a_custom_saved_value() {
        assertEquals(listOf(1, 2, 5, 10, 15, 30), lockMinuteChoices(2))
        assertEquals(listOf(1, 2, 5, 7, 10, 15, 30), lockMinuteChoices(7))
    }

    @Test
    fun text_size_snaps_to_named_choice() {
        assertEquals(10, nearestTextSizeChoice(8).sp)
        assertEquals(12, nearestTextSizeChoice(12).sp)
        assertEquals(14, nearestTextSizeChoice(15).sp)
        assertEquals(18, nearestTextSizeChoice(18).sp)
        assertEquals(18, nearestTextSizeChoice(22).sp)
        assertTrue(TEXT_SIZE_CHOICES.map { it.label } == listOf("Small", "Default", "Large", "XL"))
    }

    @Test
    fun minutes_label_is_short() {
        assertEquals("1 min", minutesLabel(1))
        assertEquals("15 min", minutesLabel(15))
    }
}
