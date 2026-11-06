package com.lipabill.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaManifestTest {

    @Test
    fun parse_accepts_https_manifest() {
        val manifest = parseOtaManifest(
            """
            {
              "versionCode": 34,
              "versionName": "2.1.1",
              "apkUrl": "https://lipabill.app/ota/LipaBill-2.1.1.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "mandatory": true,
              "notes": "Faster crash reports"
            }
            """.trimIndent()
        )
        assertEquals(34, manifest.versionCode)
        assertEquals("2.1.1", manifest.versionName)
        assertTrue(manifest.mandatory)
        assertTrue(manifest.isNewerThan(33))
        assertFalse(manifest.isNewerThan(34))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_http_apk() {
        parseOtaManifest(
            """
            {
              "versionCode": 34,
              "versionName": "2.1.1",
              "apkUrl": "http://lipabill.app/ota/x.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_short_hash() {
        parseOtaManifest(
            """
            {
              "versionCode": 34,
              "versionName": "2.1.1",
              "apkUrl": "https://lipabill.app/ota/x.apk",
              "sha256": "deadbeef"
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_off_host_apk() {
        parseOtaManifest(
            """
            {
              "versionCode": 34,
              "versionName": "2.1.1",
              "apkUrl": "https://evil.example/ota/x.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_non_ota_path() {
        parseOtaManifest(
            """
            {
              "versionCode": 34,
              "versionName": "2.1.1",
              "apkUrl": "https://lipabill.app/downloads/x.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent()
        )
    }

    @Test
    fun sanitize_notes_strips_controls_and_caps() {
        val notes = sanitizeOtaNotes("Hello\u0000\n\n\nworld\t\t" + "x".repeat(400))
        assertFalse(notes.contains('\u0000'))
        assertTrue(notes.length <= 280)
        assertFalse(notes.contains("\n\n\n"))
    }
}
