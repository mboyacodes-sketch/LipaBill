package com.lipabill.app.pay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Golden vectors shared with the HTTPS landing page (`web/p/fixtures.json` +
 * `web/p/paylink.js`). Keep these tokens identical to that JSON file.
 */
class PayLinkFixturesTest {

    private data class Case(
        val kind: PayLink.Kind,
        val identifier: String,
        val account: String?,
        val amountShillings: Long?,
        val token: String,
        val rotating: Boolean = false
    )

    private val nowMillis = 1_706_702_400_000L

    private val cases = listOf(
        Case(
            PayLink.Kind.PHONE,
            "0712345678",
            null,
            null,
            "yJQLB3qDyJaVpwq7NmwIMVGZLyqH0NBy3FrV"
        ),
        Case(
            PayLink.Kind.PHONE,
            "0712345678",
            null,
            1500L,
            "yJQLB3qDyJaVpwqHUnIPKYFWOzrhjSM66n0i"
        ),
        Case(
            PayLink.Kind.TILL,
            "400200",
            null,
            250L,
            "yJG9JU9Qar2PcmIx7SavZdYYqJyCC7zQdeRu"
        ),
        Case(
            PayLink.Kind.PAYBILL,
            "888880",
            "Shop12",
            null,
            "yJoMXJPJ0EJbXJV0DRb2TO1aObwXIwxCReYC"
        ),
        Case(
            PayLink.Kind.PHONE,
            "0712345678",
            null,
            200L,
            "yJQLB3qDyJXDULjBVAQJDe8pDppO86JbHWAR",
            rotating = true
        )
    )

    @Test
    fun landing_page_fixtures_decode_in_kotlin() {
        val json = fixturesFile().readText()
        for (row in cases) {
            assertTrue("fixtures.json missing ${row.token}", json.contains(row.token))
            val parsed = PayLink.parse(row.token, nowMillis)
            assertNotNull(row.token, parsed)
            assertEquals(row.kind, parsed!!.kind)
            assertEquals(row.identifier, parsed.identifier)
            assertEquals(row.account, parsed.account)
            assertEquals(row.amountShillings, parsed.amountShillings)
            assertEquals(PayLink.WEB_PREFIX + row.token, parsed.copy(token = row.token).shareUrl)
        }
    }

    @Test
    fun rotating_fixture_rejects_two_days_later() {
        val rotating = cases.first { it.rotating }.token
        assertNotNull(PayLink.parse(rotating, nowMillis))
        assertNull(PayLink.parse(rotating, nowMillis + 2 * 86_400_000L))
    }

    private fun fixturesFile(): File {
        val candidates = listOf(
            File("../web/p/fixtures.json"),
            File("web/p/fixtures.json"),
            File(System.getProperty("user.dir"), "../web/p/fixtures.json"),
            File(System.getProperty("user.dir"), "web/p/fixtures.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("web/p/fixtures.json not found from ${System.getProperty("user.dir")}")
    }
}
