package com.lipabill.app

import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingLinksTest {

    @Test
    fun public_urls_are_https_under_the_site() {
        assertTrue(MarketingLinks.WEBSITE.startsWith("https://"))
        assertTrue(MarketingLinks.PRIVACY.startsWith(MarketingLinks.WEBSITE))
        assertTrue(MarketingLinks.SUPPORT.startsWith(MarketingLinks.WEBSITE))
        assertTrue(MarketingLinks.SUPPORT_EMAIL.contains("@"))
    }
}
