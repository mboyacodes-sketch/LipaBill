package com.lipabill.app.viewmodel

import com.lipabill.app.data.repository.SendContact
import org.junit.Assert.assertEquals
import org.junit.Test

class SendMoneyRecipientSearchTest {

    private val jane = SendContact(1, "Jane", "0712345678", "0712345678")
    private val john = SendContact(2, "John", "0722000000", "0722000000")

    @Test
    fun blank_query_keeps_past_sends() {
        assertEquals(
            listOf(jane, john),
            SendMoneyViewModel.filterRecipientSearch(listOf(jane, john), "  ")
        )
    }

    @Test
    fun name_and_digits_filter_past_sends_only() {
        assertEquals(
            listOf(jane),
            SendMoneyViewModel.filterRecipientSearch(listOf(jane, john), "jan")
        )
        assertEquals(
            listOf(john),
            SendMoneyViewModel.filterRecipientSearch(listOf(jane, john), "0722")
        )
    }
}
