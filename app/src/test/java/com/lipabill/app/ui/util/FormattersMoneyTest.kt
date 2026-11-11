package com.lipabill.app.ui.util

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FormattersMoneyTest {

    private val zone = ZoneId.systemDefault()

    private fun millis(daysFromToday: Long, hour: Int = 9, minute: Int = 30): Long {
        val date = LocalDate.now(zone).plusDays(daysFromToday)
        return date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun format_ksh_and_kes() {
        assertEquals("KSh 1,500", formatKsh(1_500.4))
        assertEquals("1,500.40", formatKes(1_500.4))
        assertEquals("—", formatKes(null))
    }

    @Test
    fun display_and_chip_labels_cover_common_types() {
        assertEquals("Sent", TransactionType.SENT.displayLabel())
        assertEquals("Buy Goods", TransactionType.BUY_GOODS.displayLabel())
        assertEquals("Pochi La Biashara", TransactionType.POCHI.displayLabel())
        assertEquals("Airtime", TransactionType.AIRTIME.displayLabel())
        assertEquals("Till", TransactionType.BUY_GOODS.chipLabel())
        assertEquals("Pochi", TransactionType.POCHI.chipLabel())
        assertEquals("Sent", TransactionType.SENT.chipLabel())
    }

    @Test
    fun activity_and_event_when_use_relative_days() {
        assertTrue(formatActivityTime(millis(0)).startsWith("Today,"))
        assertTrue(formatActivityTime(millis(-1)).startsWith("Yesterday,"))
        assertTrue(formatEventWhen(millis(1, 19, 0)).startsWith("Tomorrow"))
        assertTrue(formatEventWhen(millis(1, 19, 0)).contains("·"))
        val midnight = LocalDate.now(zone).plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(!formatEventWhen(midnight).contains("·"))
    }

    @Test
    fun event_schedule_splits_date_and_notes_time() {
        val start = millis(2, 18, 0)
        val end = millis(2, 21, 0)
        val withNotes = formatEventSchedule(start, "Ends: $end · Time: 6:00 pm – 9:00 pm")
        assertTrue(withNotes.date != null)
        assertEquals("6:00 pm – 9:00 pm", withNotes.time)
        val dateOnly = formatEventSchedule(
            LocalDate.now(zone).plusDays(4).atStartOfDay(zone).toInstant().toEpochMilli(),
            null
        )
        assertTrue(dateOnly.date != null)
        assertNull(dateOnly.time)
    }
}
