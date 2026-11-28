package com.lipabill.app.pay

import com.lipabill.app.BuildConfig
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pay link v4 — the destination is encrypted to LipaBill's server key so the
 * phone, till, or paybill can't be read from the link, chat previews, or logs.
 * Sealing needs only the public key, so links are still made offline.
 * Opening needs the server (`/api/paylink/resolve` or the web landing).
 *
 * ```
 * token     = base62( 0x04 | kid | ephPub(33, compressed P-256) | ciphertext | tag(16) )
 * plaintext = kind | id BCD(6) | day(2) | amount(3) | accLen | account   (v3 fields)
 * okm       = HKDF-SHA256(ECDH(eph, server), salt = ephPub,
 *                         info = "LipaBill paylink v4" || serverPub(65)) → key(16) | nonce(12)
 * AAD       = the 35 header bytes
 * ```
 *
 * Platform JCA only (EC, ECDH, HmacSHA256, AES/GCM) — available from API 26.
 * Keep in lockstep with lipabill_web `src/lib/paylink-v4.ts`.
 */
object PayLinkV4 {
    const val VERSION = 4
    private const val HEADER = 35
    private const val TAG_BYTES = 16
    private const val PLAIN_MIN = 13
    private const val PLAIN_MAX = 25
    private val INFO = "LipaBill paylink v4".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    /** Server public key: [kid] + uncompressed P-256 point (65 bytes, 0x04 | x | y). */
    class ServerKey(val kid: Int, val uncompressed: ByteArray) {
        init {
            require(kid in 1..255) { "kid must be 1–255" }
            require(uncompressed.size == 65 && uncompressed[0] == 0x04.toByte()) {
                "public key must be an uncompressed P-256 point"
            }
        }

        companion object {
            fun fromHex(kid: Int, hex: String): ServerKey? = runCatching {
                ServerKey(kid, hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }.getOrNull()
        }
    }

    /**
     * Key new links are sealed to, or null to keep making v3 links.
     * Off until the website that can open v4 is live (see PAYLINK_V4_EMIT).
     */
    fun defaultKey(): ServerKey? {
        if (!BuildConfig.PAYLINK_V4_EMIT) return null
        return ServerKey.fromHex(BuildConfig.PAYLINK_V4_KID, BuildConfig.PAYLINK_V4_PUBLIC_KEY)
    }

    /** Structural check only — v4 can't be opened on the phone. */
    fun looksLike(bytes: ByteArray?): Boolean =
        bytes != null &&
            bytes.isNotEmpty() &&
            (bytes[0].toInt() and 0xFF) == VERSION &&
            bytes.size in (HEADER + PLAIN_MIN + TAG_BYTES)..(HEADER + PLAIN_MAX + TAG_BYTES)

    /** Encrypt a v4 [plain] body to [key]. Returns the raw token bytes. */
    fun seal(plain: ByteArray, key: ServerKey): ByteArray {
        require(plain.size in PLAIN_MIN..PLAIN_MAX) { "bad v4 body" }
        val params = p256()
        val generator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }
        val eph = generator.generateKeyPair()
        val ephPub = compress((eph.public as ECPublicKey).w)

        val serverPub = KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(decodePoint(key.uncompressed), params)
        )
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(eph.private)
            doPhase(serverPub, true)
            generateSecret()
        }
        val okm = hkdf(secret, salt = ephPub, info = INFO + key.uncompressed, length = 28)

        val header = byteArrayOf(VERSION.toByte(), key.kid.toByte()) + ephPub
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(okm.copyOfRange(0, 16), "AES"),
                GCMParameterSpec(TAG_BYTES * 8, okm.copyOfRange(16, 28))
            )
            updateAAD(header)
        }
        return header + cipher.doFinal(plain)
    }

    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(ikm)
        }
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            previous = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                update(previous)
                update(info)
                update(counter.toByte())
                doFinal()
            }
            val take = minOf(previous.size, length - written)
            System.arraycopy(previous, 0, out, written, take)
            written += take
            counter++
        }
        return out
    }

    internal fun p256(): ECParameterSpec {
        val generator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        return (generator.generateKeyPair().public as ECPublicKey).params
    }

    internal fun compress(point: ECPoint): ByteArray {
        val prefix = if (point.affineY.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefix.toByte()) + fixed32(point.affineX)
    }

    internal fun decodePoint(uncompressed: ByteArray): ECPoint = ECPoint(
        BigInteger(1, uncompressed.copyOfRange(1, 33)),
        BigInteger(1, uncompressed.copyOfRange(33, 65))
    )

    internal fun fixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }
}
