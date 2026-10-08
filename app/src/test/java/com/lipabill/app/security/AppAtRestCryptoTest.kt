package com.lipabill.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class AppAtRestCryptoTest {

    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun seal_round_trips_and_uses_a_fresh_iv() {
        val plain = "till 888880".toByteArray()
        val first = AppAtRestCrypto.seal(plain, key, SecureRandom())
        val second = AppAtRestCrypto.seal(plain, key, SecureRandom())
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, AppAtRestCrypto.open(first, key))
        assertArrayEquals(plain, AppAtRestCrypto.open(second, key))
    }

    @Test
    fun truncated_or_wrong_key_does_not_open() {
        val sealed = AppAtRestCrypto.seal("name".toByteArray(), key, SecureRandom())
        assertNull(AppAtRestCrypto.open(sealed.copyOf(8), key))
        val other = SecretKeySpec(ByteArray(32) { (it + 3).toByte() }, "AES")
        assertNull(AppAtRestCrypto.open(sealed, other))
    }
}
