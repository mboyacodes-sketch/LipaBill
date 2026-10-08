package com.lipabill.app.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptOpenRequestTest {

    @Test
    fun extras_name_the_payment() {
        val request = receiptOpenRequestFrom(
            extraId = 42L,
            extraCode = "ABC123",
            dataString = null
        )
        assertEquals(ReceiptOpenRequest(42L, "ABC123"), request)
    }

    @Test
    fun uri_fills_in_when_extras_are_missing() {
        val request = receiptOpenRequestFrom(
            extraId = 0L,
            extraCode = null,
            dataString = receiptOpenData(42L, "ABC123")
        )
        assertEquals(ReceiptOpenRequest(42L, "ABC123"), request)
    }

    @Test
    fun code_still_opens_when_the_row_id_was_not_ready() {
        val request = receiptOpenRequestFrom(
            extraId = 0L,
            extraCode = null,
            dataString = "lipabill://receipt/0?code=ABC123"
        )
        assertEquals(ReceiptOpenRequest(null, "ABC123"), request)
    }

    @Test
    fun a_code_that_is_not_a_short_token_is_ignored() {
        val request = receiptOpenRequestFrom(
            extraId = 42L,
            extraCode = "ABC123'; DROP TABLE transactions",
            dataString = null
        )
        assertEquals(ReceiptOpenRequest(42L, null), request)
        assertNull(
            receiptOpenRequestFrom(
                extraId = 0L,
                extraCode = "not a code",
                dataString = "lipabill://receipt/0?code=" + "A".repeat(40)
            )
        )
    }

    @Test
    fun launcher_intent_is_not_a_receipt() {
        assertNull(
            receiptOpenRequestFrom(
                extraId = 0L,
                extraCode = null,
                dataString = null
            )
        )
    }
}
