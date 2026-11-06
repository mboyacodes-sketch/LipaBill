package com.lipabill.app.update

import java.net.URI

/**
 * Hosted at [BuildConfig.OTA_MANIFEST_URL] for internal/sideload builds.
 *
 * Example:
 * ```json
 * {
 *   "versionCode": 34,
 *   "versionName": "2.1.1",
 *   "apkUrl": "https://lipabill.app/ota/LipaBill-2.1.1.apk",
 *   "sha256": "…",
 *   "mandatory": false,
 *   "notes": "Payment crash diagnostics"
 * }
 * ```
 */
data class OtaManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val mandatory: Boolean = false,
    val notes: String = ""
) {
    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode
}

/** Only APKs under https://lipabill.app/ota/ are accepted. */
const val OTA_APK_HOST = "lipabill.app"
const val OTA_APK_PATH_PREFIX = "/ota/"

fun parseOtaManifest(json: String): OtaManifest {
    val apkUrl = jsonString(json, "apkUrl")?.trim().orEmpty()
    val sha = jsonString(json, "sha256")?.trim()?.lowercase().orEmpty()
    requireTrustedApkUrl(apkUrl)
    require(sha.matches(Regex("[a-f0-9]{64}"))) { "sha256 must be 64 hex chars" }
    val versionCode = jsonInt(json, "versionCode")
        ?: error("versionCode required")
    val versionName = jsonString(json, "versionName")?.trim().orEmpty()
    require(versionName.isNotEmpty()) { "versionName required" }
    return OtaManifest(
        versionCode = versionCode,
        versionName = versionName.take(32),
        apkUrl = apkUrl,
        sha256 = sha,
        mandatory = jsonBoolean(json, "mandatory") ?: false,
        notes = sanitizeOtaNotes(jsonString(json, "notes").orEmpty())
    )
}

/** Rejects off-host / non-HTTPS / path-traversal apkUrl values. */
fun requireTrustedApkUrl(apkUrl: String) {
    val uri = runCatching { URI(apkUrl) }.getOrElse { error("apkUrl invalid") }
    require(uri.scheme.equals("https", ignoreCase = true)) { "apkUrl must be https" }
    require(uri.host.equals(OTA_APK_HOST, ignoreCase = true)) { "apkUrl host not allowed" }
    require(uri.userInfo.isNullOrEmpty()) { "apkUrl must not include credentials" }
    require(uri.port == -1 || uri.port == 443) { "apkUrl port not allowed" }
    val path = uri.path.orEmpty()
    require(path.startsWith(OTA_APK_PATH_PREFIX)) { "apkUrl path not allowed" }
    require(!path.contains("..")) { "apkUrl path invalid" }
    require(path.length > OTA_APK_PATH_PREFIX.length) { "apkUrl path empty" }
}

/** Strip control chars / runaway newlines so a tampered manifest cannot spam the dialog. */
fun sanitizeOtaNotes(raw: String): String {
    val cleaned = buildString(raw.length.coerceAtMost(320)) {
        var newlines = 0
        for (ch in raw.trim()) {
            when {
                ch == '\n' -> {
                    if (newlines < 2) {
                        append('\n')
                        newlines++
                    }
                }
                ch == '\t' || ch == ' ' -> {
                    append(' ')
                    newlines = 0
                }
                !ch.isISOControl() && ch.code in 0x20..0x10FFFF -> {
                    append(ch)
                    newlines = 0
                }
            }
        }
    }
    return cleaned.replace(Regex(" +"), " ").trim().take(280)
}

/** Tiny JSON field reader — avoids org.json so JVM unit tests stay mock-free. */
internal fun jsonString(json: String, key: String): String? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(json) ?: return null
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}

internal fun jsonInt(json: String, key: String): Int? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(json) ?: return null
    return match.groupValues[1].toIntOrNull()
}

internal fun jsonBoolean(json: String, key: String): Boolean? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        .find(json) ?: return null
    return match.groupValues[1].equals("true", ignoreCase = true)
}
