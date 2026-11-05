package com.lipabill.app.pay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayLinkTest {

    private val dayMs = 86_400_000L

    @Test
    fun round_trip_hides_the_number_and_keeps_an_open_amount() {
        val link = PayLink.encode("0712345678")
        requireNotNull(link)
        assertEquals(PayLink.Kind.PHONE, link.kind)
        assertEquals("0712345678", link.phone)
        assertNull(link.amountShillings)
        assertFalse(link.shareUrl.contains("0712345678"))
        assertFalse(link.shareUrl.contains("254712345678"))
        assertTrue(link.shareUrl.startsWith(PayLink.WEB_PREFIX))
        assertEquals(link.phone, PayLink.parse(link.shareUrl)?.phone)
        assertEquals(link.phone, PayLink.parse("lipabill://p/${link.token}")?.phone)
        assertNull(PayLink.parse(link.shareUrl)?.amountShillings)
    }

    @Test
    fun share_url_is_https_landing_and_legacy_app_scheme_still_parses() {
        val link = PayLink.encode("0712345678", rotateDaily = false)
        requireNotNull(link)
        assertEquals("${PayLink.WEB_PREFIX}${link.token}", link.shareUrl)
        assertEquals(link.phone, PayLink.parse("${PayLink.APP_PREFIX}${link.token}")?.phone)
    }

    @Test
    fun round_trip_keeps_a_requested_amount() {
        val link = PayLink.encode("254712345678", 1500)
        requireNotNull(link)
        val parsed = PayLink.parse("lipabill://p/${link.token}")
        assertEquals("0712345678", parsed?.phone)
        assertEquals(1500L, parsed?.amountShillings)
    }

    @Test
    fun accepts_nine_digit_and_spaced_numbers() {
        assertEquals("0712345678", PayLink.canonicalPhone("712 345 678"))
        assertEquals("0112345678", PayLink.canonicalPhone("+254112345678"))
        assertNull(PayLink.canonicalPhone("0212345678"))
        assertNull(PayLink.encode("123"))
    }

    @Test
    fun rejects_a_tampered_code() {
        val link = PayLink.encode("0712345678", 50)
        requireNotNull(link)
        val flipped = link.token.dropLast(1) + if (link.token.last() == 'a') "b" else "a"
        assertNull(PayLink.parse("https://lipabill.app/p/$flipped?src=share"))
        assertNull(PayLink.parse("https://example.com/p/${link.token}"))
        assertNull(PayLink.parse("https://evil.example/lipabill.app/p/${link.token}"))
        assertNull(PayLink.parse("https://notlipabill.app/p/${link.token}"))
        assertNull(PayLink.parse(""))
    }

    @Test
    fun rotating_code_changes_each_day_and_rejects_older_scans() {
        val noon = 1_704_067_200_000L + (10 * dayMs) + (12 * 60 * 60 * 1000L)
        val today = PayLink.encode("0712345678", 200, rotateDaily = true, nowMillis = noon)
        val tomorrow = PayLink.encode(
            "0712345678",
            200,
            rotateDaily = true,
            nowMillis = noon + dayMs
        )
        requireNotNull(today)
        requireNotNull(tomorrow)
        assertNotEquals(today.token, tomorrow.token)
        assertEquals("0712345678", PayLink.parse(today.shareUrl, nowMillis = noon)?.phone)
        assertEquals(
            "0712345678",
            PayLink.parse(today.shareUrl, nowMillis = noon + dayMs)?.phone
        )
        assertNull(PayLink.parse(today.shareUrl, nowMillis = noon + (2 * dayMs)))
    }

    @Test
    fun fixed_code_stays_the_same_across_days() {
        val noon = 1_704_067_200_000L + (20 * dayMs) + (12 * 60 * 60 * 1000L)
        val first = PayLink.encode("0712345678", null, rotateDaily = false, nowMillis = noon)
        val later = PayLink.encode(
            "0712345678",
            null,
            rotateDaily = false,
            nowMillis = noon + (40 * dayMs)
        )
        requireNotNull(first)
        requireNotNull(later)
        assertEquals(first.token, later.token)
        assertEquals(
            "0712345678",
            PayLink.parse(first.shareUrl, nowMillis = noon + (40 * dayMs))?.phone
        )
    }

    @Test
    fun till_code_opens_as_till() {
        val link = PayLink.encode(
            kind = PayLink.Kind.TILL,
            identifier = "400200",
            amountShillings = 250,
            rotateDaily = false
        )
        requireNotNull(link)
        val parsed = PayLink.parse(link.shareUrl)
        assertEquals(PayLink.Kind.TILL, parsed?.kind)
        assertEquals("400200", parsed?.identifier)
        assertNull(parsed?.account)
        assertEquals(250L, parsed?.amountShillings)
        assertEquals("", parsed?.phone)
    }

    @Test
    fun paybill_code_keeps_business_and_account() {
        val link = PayLink.encode(
            kind = PayLink.Kind.PAYBILL,
            identifier = "888880",
            account = "Shop12",
            amountShillings = null,
            rotateDaily = false
        )
        requireNotNull(link)
        val parsed = PayLink.parse(link.shareUrl)
        assertEquals(PayLink.Kind.PAYBILL, parsed?.kind)
        assertEquals("888880", parsed?.identifier)
        assertEquals("Shop12", parsed?.account)
    }

    @Test
    fun only_one_kind_is_encoded_phone_till_or_paybill_differ() {
        val phone = PayLink.encode(
            kind = PayLink.Kind.PHONE,
            identifier = "0712345678",
            rotateDaily = false
        )
        val till = PayLink.encode(
            kind = PayLink.Kind.TILL,
            identifier = "400200",
            rotateDaily = false
        )
        val bill = PayLink.encode(
            kind = PayLink.Kind.PAYBILL,
            identifier = "400200",
            account = "ACC1",
            rotateDaily = false
        )
        requireNotNull(phone)
        requireNotNull(till)
        requireNotNull(bill)
        assertNotEquals(phone.token, till.token)
        assertNotEquals(till.token, bill.token)
        assertEquals(PayLink.Kind.PHONE, PayLink.parse(phone.shareUrl)?.kind)
        assertEquals(PayLink.Kind.TILL, PayLink.parse(till.shareUrl)?.kind)
        assertEquals(PayLink.Kind.PAYBILL, PayLink.parse(bill.shareUrl)?.kind)
    }
}
