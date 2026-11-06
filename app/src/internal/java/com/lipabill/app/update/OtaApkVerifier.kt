package com.lipabill.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Confirms a downloaded OTA APK is our package and signed with the same
 * certificate(s) as the currently installed build before opening the installer.
 */
internal object OtaApkVerifier {

    fun requireMatchesInstalled(context: Context, apk: File) {
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("apk_unreadable")

        require(archive.packageName == context.packageName) { "apk_package_mismatch" }

        val expected = installedCertDigests(context)
        require(expected.isNotEmpty()) { "apk_no_installed_signer" }
        val actual = archiveCertDigests(archive)
        require(actual.isNotEmpty() && actual.any { it in expected }) {
            "apk_signer_mismatch"
        }
    }

    private fun installedCertDigests(context: Context): Set<String> {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                certDigestsFromSigningInfo(info.signingInfo)
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
            }
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun archiveCertDigests(info: android.content.pm.PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            certDigestsFromSigningInfo(info.signingInfo)
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
        }
    }

    private fun certDigestsFromSigningInfo(
        signingInfo: android.content.pm.SigningInfo?
    ): Set<String> {
        if (signingInfo == null) return emptySet()
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signers?.map { sha256Hex(it.toByteArray()) }.orEmpty().toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
