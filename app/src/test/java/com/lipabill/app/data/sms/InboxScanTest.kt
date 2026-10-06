package com.lipabill.app.data.sms

import com.lipabill.app.data.parser.MpesaSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxScanTest {

    @Test
    fun first_scan_reads_the_whole_inbox() {
        assertNull(InboxScan.newerThanMillis(historyImported = false, highWaterMillis = 5_000L))
        assertNull(InboxScan.newerThanMillis(historyImported = true, highWaterMillis = 0L))
    }

    @Test
    fun later_scan_starts_an_overlap_before_the_newest_message() {
        val newest = 10 * 60 * 1000L
        assertEquals(
            newest - InboxScan.OVERLAP_MS,
            InboxScan.newerThanMillis(historyImported = true, highWaterMillis = newest)
        )
    }

    @Test
    fun overlap_does_not_go_below_zero() {
        assertEquals(
            0L,
            InboxScan.newerThanMillis(historyImported = true, highWaterMillis = 30_000L)
        )
    }

    @Test
    fun only_a_recent_confirmation_can_match_a_pending_dial() {
        val now = 1_000_000L
        assertTrue(InboxScan.mayMatchPendingDial(now, nowMillis = now))
        assertFalse(
            InboxScan.mayMatchPendingDial(
                timestampMillis = now - InboxScan.PENDING_MATCH_MS - 1,
                nowMillis = now
            )
        )
    }

    @Test
    fun receipt_code_is_the_leading_token() {
        assertEquals(
            "THX7K2LM9P",
            MpesaSmsParser.receiptCode(
                "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN."
            )
        )
        assertNull(MpesaSmsParser.receiptCode("Confirmed without a code"))
    }
}
