package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdSessionTest {

    @Test
    fun peek_advance_and_clear() {
        val session = UssdSession(
            auditId = 1L,
            transactionId = 2L,
            transactionCode = "QK1",
            actions = listOf(
                UssdAction.ChooseMenu(listOf("send money")),
                UssdAction.TypeValue("0712345678", label = "phone"),
                UssdAction.TypeValue("100", label = "amount")
            ),
            amount = 100.0,
            counterpartyName = "Jane",
            counterpartyPhone = "0712345678"
        )
        assertTrue(session.active)
        assertEquals("send money", (session.peekAction() as UssdAction.ChooseMenu).labels.first())
        session.advance()
        assertEquals("0712345678", (session.peekAction() as UssdAction.TypeValue).value)
        session.advance()
        session.advance()
        assertNull(session.peekAction())
        session.clear()
        assertFalse(session.active)
        assertEquals(3, session.nextStepIndex)
        assertNull(session.peekAction())
    }
}
