package com.lipabill.app.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsInboxReaderBoundsTest {

    @Test
    fun inbox_projection_is_address_body_date_only() {
        val projection = SmsInboxReader.INBOX_PROJECTION.toList()
        assertEquals(3, projection.size)
        assertTrue(projection.any { it.contains("address", ignoreCase = true) || it == android.provider.Telephony.Sms.ADDRESS })
        assertTrue(projection.contains(android.provider.Telephony.Sms.ADDRESS))
        assertTrue(projection.contains(android.provider.Telephony.Sms.BODY))
        assertTrue(projection.contains(android.provider.Telephony.Sms.DATE))
    }

    @Test
    fun unbounded_query_has_no_date_cutoff() {
        val (selection, args) = SmsInboxReader.inboxQuery(
            extraSelection = "body LIKE ?",
            extraArgs = arrayOf("%Confirmed%"),
            newerThanMillis = null
        )
        assertFalse(selection.contains("date >"))
        assertEquals(listOf("%MPESA%", "%M-PESA%", "%Confirmed%"), args.toList())
    }

    @Test
    fun bounded_query_keeps_messages_newer_than_the_cutoff() {
        val (selection, args) = SmsInboxReader.inboxQuery(
            extraSelection = "body LIKE ?",
            extraArgs = arrayOf("%Confirmed%"),
            newerThanMillis = 1_700_000_000_000L
        )
        assertTrue(selection.contains("date > ?"))
        assertEquals("1700000000000", args.last())
    }
}
