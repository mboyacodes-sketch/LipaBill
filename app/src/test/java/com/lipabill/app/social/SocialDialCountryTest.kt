package com.lipabill.app.social

import com.lipabill.app.data.model.AppRegion
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialDialCountryTest {

    @Test
    fun default_follows_app_region() {
        assertEquals("254", SocialDialCountries.defaultFor(AppRegion.KENYA).dial)
        assertEquals("255", SocialDialCountries.defaultFor(AppRegion.TANZANIA).dial)
        assertEquals("256", SocialDialCountries.defaultFor(AppRegion.UGANDA).dial)
    }

    @Test
    fun split_keeps_longest_dial_match() {
        val (country, national) = SocialDialCountries.splitInternational(
            "254712345678",
            SocialDialCountries.defaultFor(AppRegion.KENYA)
        )
        assertEquals("254", country.dial)
        assertEquals("712345678", national)
    }

    @Test
    fun compose_strips_leading_zero() {
        assertEquals(
            "254712345678",
            SocialDialCountries.composeInternational("254", "0712345678")
        )
    }
}
