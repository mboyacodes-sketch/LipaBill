package com.lipabill.app.data.sms

import org.junit.Assert.assertEquals
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
}
