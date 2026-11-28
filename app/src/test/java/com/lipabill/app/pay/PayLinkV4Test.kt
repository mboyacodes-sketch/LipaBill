package com.lipabill.app.pay

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pay link v4 — cross-checked with lipabill_web `src/lib/paylink-v4.ts`.
 *
 * Uses the PUBLIC test key from `scripts/paylink/fixtures.json` (kid 200). The
 * server refuses kid 200 in production; this key only proves both sides agree.
 * [openForTest] is the server's decrypt, written here so the app's seal is checked.
 */
class PayLinkV4Test {
    private val testKey = PayLinkV4.ServerKey.fromHex(
        200,
        "04b6eba43cb779dd59328c611962ec0ec8c6a1b7efbfc60d97023895a89275355fcd3232a0115c72f00c02cc92d265e19a50ab8f31e3ec3f7a27d72c1c53825a6b"
    )!!
    private val testPrivate = java.util.Base64.getUrlDecoder()
        .decode("JKeqimtd7h3dOIzLs0c3N8g2LDPZhJGZYfSPcv33a4M")

    /** Sealed by Node (web) — the app's opener must read them byte-for-byte. */
    private val webSealed = listOf(
        Triple("16pIYsqVd7Hkm4DC6RcHIAsF66rC7o08XBK6CyiVJxWfbE20KAMoJv8kytTKluFbN87DnFWXoAtUpew8W9ZMl8", "PHONE 0712345678", 1500L),
        Triple("16pJ4tRSWrhwfkzBK70eZRSgmeZPy51abyyLdUrGcKjKOHLqcYGCBFlbIMmyi2BhmfwWOLRTziWzpbTVZyUrGU", "TILL 400200", 250L),
        Triple("1QjEFKPqjtMPLKka5ClmYzsqvvqsBAAoUUdRAwYvpRmptuoE3W1Cnp6wAWoCvx6x31sytglxsqXhxLvD9HUbjvG7u7jsY0", "PAYBILL 888880 Shop12", 0L),
    )

    @Test
    fun hkdf_matches_rfc5869_case_1() {
        val okm = PayLinkV4.hkdf(
            ikm = ByteArray(22) { 0x0b },
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm
        )
    }

    @Test
    fun web_sealed_fixtures_open_with_server_algorithm() {
        for ((token, label, amount) in webSealed) {
            val plain = openForTest(token)
            assertNotNull(label, plain)
            val kind = plain!![0].toInt()
            assertEquals(label, label.substringBefore(' '), listOf("PHONE", "TILL", "PAYBILL")[kind])
            val amountRead = ((plain[9].toLong() and 0xFF) shl 16) or
                ((plain[10].toLong() and 0xFF) shl 8) or (plain[11].toLong() and 0xFF)
            assertEquals(label, amount, amountRead)
        }
    }

    @Test
    fun app_seal_round_trips_for_every_kind() {
        val cases = listOf(
            Triple(PayLink.Kind.PHONE, "0712345678", null as String?),
            Triple(PayLink.Kind.TILL, "400200", null),
            Triple(PayLink.Kind.PAYBILL, "888880", "Shop12"),
            Triple(PayLink.Kind.PAYBILL, "247247", "ACC123456789"),
        )
        for ((kind, id, account) in cases) {
            val target = PayLink.encode(kind, id, account, 1_500, rotateDaily = false, v4Key = testKey)
            assertNotNull("$kind $id", target)
            val token = target!!.token
            // Captured into lipabill_web fixtures (v4.fromApp) so Node opens app-sealed links.
            println("FROM_APP|$kind|$id|${account ?: "-"}|$token")
            assertTrue("token length ${token.length}", token.length in 80..120)
            assertTrue(PayLink.isSealed(target.shareUrl))
            assertTrue(PayLink.isPayLink(target.shareUrl))
            // Unreadable without the server key.
            assertNull(PayLink.parse(target.shareUrl))
            assertFalse(token.contains(id))

            val plain = openForTest(token)
            assertNotNull(plain)
            val accLen = plain!![12].toInt()
            assertEquals(account?.length ?: 0, accLen)
            if (account != null) {
                assertEquals(account, String(plain.copyOfRange(13, 13 + accLen), Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun each_seal_is_unique() {
        val a = PayLink.encode("0712345678", 100, rotateDaily = false, v4Key = testKey)!!.token
        val b = PayLink.encode("0712345678", 100, rotateDaily = false, v4Key = testKey)!!.token
        assertFalse(a == b)
    }

    @Test
    fun tampered_token_fails_to_open() {
        val token = PayLink.encode("0712345678", 100, rotateDaily = false, v4Key = testKey)!!.token
        val i = 60
        val flipped = token.substring(0, i) + (if (token[i] == 'A') 'B' else 'A') + token.substring(i + 1)
        assertNull(openForTest(flipped))
    }

    @Test
    fun default_build_still_makes_readable_v3_links() {
        // PAYLINK_V4_EMIT is false until the website can open v4.
        val target = PayLink.encode("0712345678", 1_500)
        assertNotNull(target)
        assertEquals(target!!.identifier, PayLink.parse(target.shareUrl)?.identifier)
        assertFalse(PayLink.isSealed(target.shareUrl))
    }

    @Test
    fun resolved_answers_are_revalidated() {
        val token = "x".repeat(90)
        assertEquals("0712345678", PayLink.fromResolved("PHONE", "0712345678", null, 50, token)?.identifier)
        assertEquals("Shop12", PayLink.fromResolved("PAYBILL", "888880", "Shop12", null, token)?.account)
        assertNull(PayLink.fromResolved("PHONE", "12345", null, null, token))
        assertNull(PayLink.fromResolved("PAYBILL", "888880", null, null, token))
        assertNull(PayLink.fromResolved("TILL", "400200", null, -1, token))
        assertNull(PayLink.fromResolved("TILL", "400200", null, 0x1_000_000, token))
        assertNull(PayLink.fromResolved("BANK", "400200", null, null, token))
    }

    // ── server-side decrypt, for tests only ──────────────────────────────────

    private fun openForTest(token: String): ByteArray? = runCatching {
        val bytes = base62(token)
        if (!PayLinkV4.looksLike(bytes)) return null
        val header = bytes.copyOfRange(0, 35)
        val ephPub = bytes.copyOfRange(2, 35)
        val params = PayLinkV4.p256()
        val factory = KeyFactory.getInstance("EC")
        val serverPrivate = factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, testPrivate), params))
        val ephPublic = factory.generatePublic(ECPublicKeySpec(decompress(ephPub), params)) as ECPublicKey
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(serverPrivate)
            doPhase(ephPublic, true)
            generateSecret()
        }
        val okm = PayLinkV4.hkdf(secret, ephPub, "LipaBill paylink v4".toByteArray() + testKey.uncompressed, 28)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(okm.copyOfRange(0, 16), "AES"), GCMParameterSpec(128, okm.copyOfRange(16, 28)))
            updateAAD(header)
            doFinal(bytes.copyOfRange(35, bytes.size))
        }
    }.getOrNull()

    private fun decompress(compressed: ByteArray): ECPoint {
        val curve = PayLinkV4.p256().curve
        val p = (curve.field as ECFieldFp).p
        val x = BigInteger(1, compressed.copyOfRange(1, 33))
        val rhs = x.pow(3).add(curve.a.multiply(x)).add(curve.b).mod(p)
        var y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        if (y.testBit(0) != (compressed[0].toInt() == 0x03)) y = p.subtract(y)
        return ECPoint(x, y)
    }

    private fun base62(token: String): ByteArray {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        var value = BigInteger.ZERO
        for (ch in token) value = value.multiply(BigInteger.valueOf(62)).add(BigInteger.valueOf(alphabet.indexOf(ch).toLong()))
        val raw = value.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
