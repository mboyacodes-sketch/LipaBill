package com.lipabill.app.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialLinkTest {

    @Test
    fun canonical_strips_at_and_url_noise() {
        assertEquals(
            "lipabill",
            SocialLink.canonicalHandle(SocialLink.Platform.INSTAGRAM, "@lipabill")
        )
        assertEquals(
            "lipabill",
            SocialLink.canonicalHandle(
                SocialLink.Platform.INSTAGRAM,
                "https://instagram.com/lipabill/"
            )
        )
        assertEquals(
            "254712345678",
            SocialLink.canonicalHandle(SocialLink.Platform.WHATSAPP, "+254 712 345 678")
        )
    }

    @Test
    fun web_urls_match_the_platform() {
        assertEquals(
            "https://instagram.com/lipabill",
            SocialLink.webProfileUrl(SocialLink.Platform.INSTAGRAM, "lipabill")
        )
        assertEquals(
            "https://wa.me/254712345678",
            SocialLink.webProfileUrl(SocialLink.Platform.WHATSAPP, "254712345678")
        )
        assertEquals(
            "https://www.tiktok.com/@lipabill",
            SocialLink.webProfileUrl(SocialLink.Platform.TIKTOK, "lipabill")
        )
    }

    @Test
    fun parse_round_trips_common_profile_urls() {
        val ig = SocialLink.parse("https://www.instagram.com/lipabill/")
        assertEquals(SocialLink.Platform.INSTAGRAM, ig?.platform)
        assertEquals("lipabill", ig?.handle)

        val x = SocialLink.parse("https://x.com/lipabill")
        assertEquals(SocialLink.Platform.X, x?.platform)
        assertEquals("lipabill", x?.handle)

        val wa = SocialLink.parse("https://wa.me/254712345678")
        assertEquals(SocialLink.Platform.WHATSAPP, wa?.platform)
        assertEquals("254712345678", wa?.handle)
    }

    @Test
    fun parse_rejects_unknown_hosts() {
        assertNull(SocialLink.parse("https://example.com/lipabill"))
        assertNull(SocialLink.parse("not a url"))
    }

    @Test
    fun share_text_names_the_network() {
        val target = SocialLink.Target(SocialLink.Platform.TELEGRAM, "lipabill")
        assertTrue(SocialLink.shareText(target).contains("Telegram"))
        assertTrue(SocialLink.shareText(target).contains(target.shareUrl))
    }

    @Test
    fun share_url_uses_lipabill_wrapper_for_previews() {
        val ig = SocialLink.Target(SocialLink.Platform.INSTAGRAM, "natgeo")
        assertEquals("https://lipabill.app/s/instagram/natgeo", ig.shareUrl)
        assertEquals("https://instagram.com/natgeo", ig.webUrl)

        val wa = SocialLink.Target(SocialLink.Platform.WHATSAPP, "254712345678")
        assertEquals("https://lipabill.app/s/whatsapp/254712345678", wa.shareUrl)
    }

    @Test
    fun platforms_are_ordered_most_used_to_least() {
        assertEquals(
            listOf(
                SocialLink.Platform.WHATSAPP,
                SocialLink.Platform.FACEBOOK,
                SocialLink.Platform.INSTAGRAM,
                SocialLink.Platform.TIKTOK,
                SocialLink.Platform.YOUTUBE,
                SocialLink.Platform.X,
                SocialLink.Platform.TELEGRAM,
                SocialLink.Platform.LINKEDIN,
                SocialLink.Platform.THREADS
            ),
            SocialLink.Platform.entries.toList()
        )
    }
}
