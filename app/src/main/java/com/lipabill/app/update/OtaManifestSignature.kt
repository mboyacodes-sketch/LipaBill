package com.lipabill.app.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ECDSA P-256 / SHA-256 signature over the exact bytes of latest.json, published
 * next to it as latest.json.sig (base64 DER). The SHA-256 inside the manifest comes
 * from the same host as the APK, so this is what stops a compromised host from
 * pointing installs at a different build or flagging one mandatory.
 *
 * Sign: openssl dgst -sha256 -sign keystore/ota-manifest-signing.pem latest.json | base64
 */
fun verifyOtaManifestSignature(
    manifestBytes: ByteArray,
    signatureBase64: String,
    publicKeyBase64: String
): Boolean = runCatching {
    val key = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
    val sig = Base64.getMimeDecoder().decode(signatureBase64.trim())
    Signature.getInstance("SHA256withECDSA").run {
        initVerify(key)
        update(manifestBytes)
        verify(sig)
    }
}.getOrDefault(false)

/** Rejects unsigned or tampered manifests before any field is parsed. */
fun parseSignedOtaManifest(
    manifestBytes: ByteArray,
    signatureBase64: String,
    publicKeyBase64: String
): OtaManifest {
    require(publicKeyBase64.isNotBlank()) { "manifest_key_missing" }
    require(verifyOtaManifestSignature(manifestBytes, signatureBase64, publicKeyBase64)) {
        "manifest_signature_invalid"
    }
    return parseOtaManifest(manifestBytes.toString(Charsets.UTF_8))
}
