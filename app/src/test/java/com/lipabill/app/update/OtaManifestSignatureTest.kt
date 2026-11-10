package com.lipabill.app.update

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaManifestSignatureTest {

    private val manifest = """
        {
          "versionCode": 35,
          "versionName": "2.2.0",
          "apkUrl": "https://lipabill.app/ota/LipaBill-2.2.0-internal.apk",
          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "mandatory": false,
          "notes": ""
        }
    """.trimIndent().toByteArray()

    private val keys = newKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

    @Test
    fun valid_signature_parses() {
        val parsed = parseSignedOtaManifest(manifest, sign(manifest, keys), publicKey)
        assertEquals(35, parsed.versionCode)
    }

    @Test
    fun signature_with_trailing_newline_still_verifies() {
        assertTrue(verifyOtaManifestSignature(manifest, sign(manifest, keys) + "\n", publicKey))
    }

    @Test
    fun tampered_manifest_is_rejected() {
        val sig = sign(manifest, keys)
        val tampered = String(manifest).replace("\"mandatory\": false", "\"mandatory\": true")
        assertFalse(verifyOtaManifestSignature(tampered.toByteArray(), sig, publicKey))
    }

    @Test
    fun signature_from_another_key_is_rejected() {
        assertFalse(verifyOtaManifestSignature(manifest, sign(manifest, newKeyPair()), publicKey))
    }

    @Test
    fun garbage_signature_is_rejected() {
        assertFalse(verifyOtaManifestSignature(manifest, "not-base64!!", publicKey))
        assertFalse(verifyOtaManifestSignature(manifest, "", publicKey))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missing_public_key_fails_closed() {
        parseSignedOtaManifest(manifest, sign(manifest, keys), "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalid_signature_fails_before_parsing() {
        parseSignedOtaManifest(manifest, sign("other".toByteArray(), keys), publicKey)
    }

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun sign(bytes: ByteArray, pair: KeyPair): String {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(bytes)
            sign()
        }
        return Base64.getEncoder().encodeToString(der)
    }
}
