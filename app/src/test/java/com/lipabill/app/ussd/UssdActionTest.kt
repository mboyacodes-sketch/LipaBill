package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Test

class UssdActionTest {

    @Test
    fun describe_uses_first_label_or_value() {
        assertEquals(
            "send money",
            UssdAction.ChooseMenu(labels = listOf("send money", "send")).describe()
        )
        assertEquals("menu", UssdAction.ChooseMenu(labels = emptyList()).describe())
        assertEquals(
            "0712345678",
            UssdAction.TypeValue(value = "0712345678", label = "phone").describe()
        )
    }
}
