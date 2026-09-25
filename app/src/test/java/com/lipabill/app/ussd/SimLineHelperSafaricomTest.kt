package com.lipabill.app.ussd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimLineHelperSafaricomTest {

    @Test
    fun carrier_name_safaricom_matches() {
        assertTrue(SimLineHelper.isSafaricomNetwork(carrierName = "Safaricom"))
        assertTrue(SimLineHelper.isSafaricomNetwork(carrierName = "SAFARICOM KE"))
    }

    @Test
    fun display_name_safaricom_matches() {
        assertTrue(
            SimLineHelper.isSafaricomNetwork(
                carrierName = "SIM",
                displayName = "Safaricom"
            )
        )
    }

    @Test
    fun mcc_mnc_639_02_matches() {
        assertTrue(
            SimLineHelper.isSafaricomNetwork(
                carrierName = "Unknown",
                mcc = 639,
                mnc = 2
            )
        )
    }

    @Test
    fun airtel_does_not_match() {
        assertFalse(SimLineHelper.isSafaricomNetwork(carrierName = "Airtel"))
        assertFalse(
            SimLineHelper.isSafaricomNetwork(
                carrierName = "Airtel",
                mcc = 639,
                mnc = 3
            )
        )
    }

    @Test
    fun findSafaricom_prefers_first_safaricom_line() {
        val lines = listOf(
            sim(id = 1, safaricom = false),
            sim(id = 2, safaricom = true),
            sim(id = 3, safaricom = true)
        )
        val found = SimLineHelper.findSafaricom(lines)
        assertNotNull(found)
        assertTrue(found!!.subscriptionId == 2)
    }

    @Test
    fun findSafaricom_null_when_none() {
        assertNull(SimLineHelper.findSafaricom(listOf(sim(id = 1, safaricom = false))))
        assertNull(SimLineHelper.findSafaricom(emptyList()))
    }

    private fun sim(id: Int, safaricom: Boolean) = SimLine(
        subscriptionId = id,
        simSlotIndex = id - 1,
        displayName = "SIM$id",
        carrierName = if (safaricom) "Safaricom" else "Airtel",
        numberHint = null,
        label = "SIM $id",
        isSafaricom = safaricom
    )
}
