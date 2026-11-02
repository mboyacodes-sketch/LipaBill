package com.lipabill.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionGuessTest {

    @Test
    fun sim_wins_over_network_and_locale() {
        val guess = guessAppRegion(simIso = "ug", networkIso = "ke", localeIso = "tz")
        assertEquals(AppRegion.UGANDA, guess!!.region)
        assertEquals(RegionGuessSource.SIM, guess.source)
        assertTrue(guess.hint().contains("SIM", ignoreCase = true))
    }

    @Test
    fun network_is_used_when_sim_is_unknown() {
        val guess = guessAppRegion(simIso = "us", networkIso = "tz", localeIso = "ke")
        assertEquals(AppRegion.TANZANIA, guess!!.region)
        assertEquals(RegionGuessSource.NETWORK, guess.source)
    }

    @Test
    fun locale_is_used_when_radio_is_unknown() {
        val guess = guessAppRegion(simIso = "", networkIso = null, localeIso = "rw")
        assertEquals(AppRegion.RWANDA, guess!!.region)
        assertEquals(RegionGuessSource.LOCALE, guess.source)
    }

    @Test
    fun unknown_countries_are_not_forced_to_kenya() {
        assertNull(guessAppRegion(simIso = "us", networkIso = "gb", localeIso = "de"))
        assertNull(guessAppRegion(simIso = null, networkIso = null, localeIso = null))
    }
}
