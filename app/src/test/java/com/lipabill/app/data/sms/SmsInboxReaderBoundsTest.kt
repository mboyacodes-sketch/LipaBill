package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
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
        val likes = AppRegion.KENYA.inboxAddressLikes
        assertEquals(likes + listOf("%Confirmed%"), args.toList())
        assertTrue(likes.contains("%MPESA%"))
        assertTrue(likes.contains("%KCB%"))
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

    @Test
    fun bank_only_query_omits_mno_address_likes() {
        val bankLikes = setOf(MoneySource.KCB).flatMap { it.addressLikes }.distinct()
        val (selection, args) = SmsInboxReader.inboxQuery(
            extraSelection = "body LIKE ?",
            extraArgs = arrayOf("%Confirmed%"),
            newerThanMillis = null,
            addressLikes = bankLikes
        )
        assertTrue(args.any { it.contains("KCB", ignoreCase = true) })
        assertFalse(args.any { it.contains("MPESA", ignoreCase = true) })
        assertFalse(selection.contains("date >"))
    }

    @Test
    fun bank_then_mno_query_includes_both_address_likes() {
        val likes = setOf(MoneySource.KCB, MoneySource.MPESA)
            .flatMap { it.addressLikes }
            .distinct()
        val (_, args) = SmsInboxReader.inboxQuery(
            extraSelection = "body LIKE ?",
            extraArgs = arrayOf("%Confirmed%"),
            newerThanMillis = null,
            addressLikes = likes
        )
        assertTrue(args.any { it.contains("KCB", ignoreCase = true) })
        assertTrue(args.any { it.contains("MPESA", ignoreCase = true) })
    }
}
