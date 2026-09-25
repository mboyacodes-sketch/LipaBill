package com.lipabill.app.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KenyaRegionGateTest {

    @Test
    fun bypass_always_allows() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = listOf("us"),
            hasSafaricomSim = false,
            bypassGate = true
        )
        assertEquals(KenyaRegionGate.Verdict.Allowed, v)
    }

    @Test
    fun kenya_iso_allows() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = listOf("ke"),
            hasSafaricomSim = false,
            bypassGate = false
        )
        assertEquals(KenyaRegionGate.Verdict.Allowed, v)
    }

    @Test
    fun safaricom_sim_allows_even_without_ke_iso() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = emptyList(),
            hasSafaricomSim = true,
            bypassGate = false
        )
        assertEquals(KenyaRegionGate.Verdict.Allowed, v)
    }

    @Test
    fun foreign_iso_without_safaricom_blocks() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = listOf("ug"),
            hasSafaricomSim = false,
            bypassGate = false
        )
        assertTrue(v is KenyaRegionGate.Verdict.Blocked)
        assertEquals("ug", (v as KenyaRegionGate.Verdict.Blocked).detectedIso)
    }

    @Test
    fun no_signal_allows_so_first_run_is_not_bricked() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = emptyList(),
            hasSafaricomSim = false,
            bypassGate = false
        )
        assertEquals(KenyaRegionGate.Verdict.Allowed, v)
    }

    @Test
    fun kenya_among_foreign_still_allows() {
        val v = KenyaRegionGate.verdictFor(
            countryIsos = listOf("us", "ke"),
            hasSafaricomSim = false,
            bypassGate = false
        )
        assertEquals(KenyaRegionGate.Verdict.Allowed, v)
    }
}
