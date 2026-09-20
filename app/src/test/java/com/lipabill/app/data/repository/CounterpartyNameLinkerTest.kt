package com.lipabill.app.data.repository

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterpartyNameLinkerTest {

    @Test
    fun fills_missing_phone_from_same_name() {
        val withPhone = sample(
            name = "John Kamau",
            phone = "0712345678"
        )
        val without = sample(
            name = "JOHN  KAMAU",
            phone = null,
            code = "B"
        )
        val enriched = CounterpartyNameLinker.enrichAll(listOf(without, withPhone))
        assertEquals("0712345678", enriched[0].counterpartyPhone)
        assertEquals("0712345678", enriched[1].counterpartyPhone)
    }

    @Test
    fun does_not_override_existing_phone() {
        val a = sample(name = "Mary", phone = "0700111222")
        val b = sample(name = "Mary", phone = "0711222333", code = "B")
        val enriched = CounterpartyNameLinker.enrichAll(listOf(a, b))
        assertEquals("0700111222", enriched[0].counterpartyPhone)
        assertEquals("0711222333", enriched[1].counterpartyPhone)
    }

    @Test
    fun skips_short_or_digit_names() {
        assertNull(CounterpartyNameLinker.normalizeName("Jo"))
        assertNull(CounterpartyNameLinker.normalizeName("123456"))
        val enriched = CounterpartyNameLinker.enrichAll(
            listOf(
                sample(name = "Jo", phone = null),
                sample(name = "Jo", phone = "0700111222", code = "B")
            )
        )
        assertNull(enriched[0].counterpartyPhone)
    }

    private fun sample(
        name: String?,
        phone: String?,
        code: String = "A"
    ) = MpesaTransaction(
        id = 0,
        code = code,
        type = TransactionType.SENT,
        amount = 100.0,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = 1L,
        balance = null,
        cost = null,
        rawBody = ""
    )
}
