package com.lipabill.app.social

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Social profile QR / share links. Shared QRs encode [Target.shareUrl] on
 * lipabill.app (Open Graph previews); [open] still prefers the native app, else
 * the platform https profile.
 */
object SocialLink {

    private const val TAG = "SocialLink"

    /** Share / QR HTTPS host for socials — OG tags live on lipabill_web `/s/…`. */
    const val WEB_SOCIAL_PREFIX = "https://lipabill.app/s/"

    /**
     * Declaration order is most-used → least-used for Kenya / East Africa
     * (chips, add dial, and carousel all follow [entries]).
     */
    enum class Platform(
        val label: String,
        val storageKey: String,
        val placeholder: String,
        val prefixHint: String
    ) {
        WHATSAPP("WhatsApp", "whatsapp", "phone number", "+"),
        FACEBOOK("Facebook", "facebook", "username or page", ""),
        INSTAGRAM("Instagram", "instagram", "username", "@"),
        TIKTOK("TikTok", "tiktok", "username", "@"),
        YOUTUBE("YouTube", "youtube", "handle", "@"),
        X("X", "x", "username", "@"),
        TELEGRAM("Telegram", "telegram", "username", "@"),
        LINKEDIN("LinkedIn", "linkedin", "profile slug", "/in/"),
        THREADS("Threads", "threads", "username", "@");

        val usesPhone: Boolean get() = this == WHATSAPP
    }

    data class Target(
        val platform: Platform,
        val handle: String
    ) {
        val webUrl: String get() = webProfileUrl(platform, handle)

        /**
         * HTTPS wrapper on lipabill.app so WhatsApp / X / Telegram link previews
         * get LipaBill Open Graph tags. Opens a short landing that continues to [webUrl].
         */
        val shareUrl: String
            get() = WEB_SOCIAL_PREFIX + platform.storageKey + "/" +
                encodePathSegment(handle)
    }

    /** Same output as Uri.encode, without the Android stub (keeps JVM unit tests runnable). */
    private fun encodePathSegment(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in "_-!.~'()*")) {
                append(c)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    fun fromStorageKey(key: String?): Platform =
        Platform.entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) }
            ?: Platform.entries.first()

    fun canonicalHandle(platform: Platform, raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when (platform) {
            Platform.WHATSAPP -> {
                val digits = trimmed.filter { it.isDigit() }
                digits.takeIf { it.length in 9..15 }
            }
            Platform.LINKEDIN -> {
                trimmed
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .removePrefix("linkedin.com/in/")
                    .removePrefix("linkedin.com/")
                    .trim('/')
                    .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                    .take(100)
                    .takeIf { it.isNotEmpty() }
            }
            Platform.YOUTUBE -> {
                trimmed
                    .removePrefix("@")
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .removePrefix("youtube.com/")
                    .removePrefix("@")
                    .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
                    .take(100)
                    .takeIf { it.isNotEmpty() }
            }
            else -> {
                trimmed
                    .removePrefix("@")
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                    .take(64)
                    .takeIf { it.isNotEmpty() }
            }
        }
    }

    fun webProfileUrl(platform: Platform, handle: String): String {
        val clean = canonicalHandle(platform, handle) ?: handle.trim().removePrefix("@")
        return when (platform) {
            Platform.INSTAGRAM -> "https://instagram.com/$clean"
            Platform.X -> "https://x.com/$clean"
            Platform.TIKTOK -> "https://www.tiktok.com/@$clean"
            Platform.WHATSAPP -> "https://wa.me/$clean"
            Platform.TELEGRAM -> "https://t.me/$clean"
            Platform.YOUTUBE -> "https://youtube.com/@$clean"
            Platform.FACEBOOK -> "https://facebook.com/$clean"
            Platform.LINKEDIN -> "https://www.linkedin.com/in/$clean"
            Platform.THREADS -> "https://www.threads.net/@$clean"
        }
    }

    /** Native app intent when known; null means use the https URL only. */
    fun appIntent(platform: Platform, handle: String): Intent? {
        val clean = canonicalHandle(platform, handle) ?: return null
        val uri = when (platform) {
            Platform.INSTAGRAM -> Uri.parse("instagram://user?username=$clean")
            Platform.X -> Uri.parse("twitter://user?screen_name=$clean")
            Platform.TIKTOK -> Uri.parse("snssdk1233://user/profile/$clean")
            Platform.WHATSAPP -> Uri.parse("https://wa.me/$clean")
            Platform.TELEGRAM -> Uri.parse("tg://resolve?domain=$clean")
            Platform.YOUTUBE -> Uri.parse("vnd.youtube://www.youtube.com/@$clean")
            Platform.FACEBOOK -> Uri.parse("fb://facewebmodal/f?href=${Uri.encode(webProfileUrl(platform, clean))}")
            Platform.LINKEDIN -> Uri.parse("https://www.linkedin.com/in/$clean")
            Platform.THREADS -> Uri.parse("https://www.threads.net/@$clean")
        }
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun parse(raw: String?): Target? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val withoutScheme = text
            .removePrefix("https://")
            .removePrefix("http://")
        val hostAndPath = withoutScheme.substringBefore('?').substringBefore('#')
        val host = hostAndPath.substringBefore('/').lowercase().removePrefix("www.")
        val path = hostAndPath.substringAfter('/', missingDelimiterValue = "").trim('/')
        val query = text.substringAfter('?', missingDelimiterValue = "")
        val phoneQuery = query.substringAfter("phone=", missingDelimiterValue = "")
            .substringBefore('&')
        val draft = when {
            host == "instagram.com" || host == "instagr.am" -> {
                val user = path.substringBefore('/').takeIf { it.isNotBlank() && it != "p" }
                    ?: return null
                Target(Platform.INSTAGRAM, user)
            }
            host == "x.com" || host == "twitter.com" -> {
                val user = path.substringBefore('/').takeIf {
                    it.isNotBlank() && it !in setOf("i", "intent", "share", "home")
                } ?: return null
                Target(Platform.X, user)
            }
            host == "tiktok.com" -> {
                val user = path.removePrefix("@").substringBefore('/').takeIf { it.isNotBlank() }
                    ?: return null
                Target(Platform.TIKTOK, user)
            }
            host == "wa.me" || host == "api.whatsapp.com" || host == "whatsapp.com" -> {
                val phone = path.ifBlank { phoneQuery }
                    .filter { it.isDigit() }
                    .takeIf { it.length in 9..15 }
                    ?: return null
                Target(Platform.WHATSAPP, phone)
            }
            host == "t.me" || host == "telegram.me" -> {
                val user = path.substringBefore('/').takeIf { it.isNotBlank() } ?: return null
                Target(Platform.TELEGRAM, user)
            }
            host == "youtube.com" || host == "youtu.be" || host == "m.youtube.com" -> {
                val user = when {
                    path.startsWith("@") -> path.removePrefix("@").substringBefore('/')
                    path.startsWith("c/") || path.startsWith("user/") || path.startsWith("channel/") ->
                        path.substringAfter('/').substringBefore('/')
                    else -> path.substringBefore('/')
                }.takeIf { it.isNotBlank() } ?: return null
                Target(Platform.YOUTUBE, user)
            }
            host == "facebook.com" || host == "fb.com" || host == "m.facebook.com" -> {
                val user = path.substringBefore('/').takeIf {
                    it.isNotBlank() && it !in setOf("profile.php", "people", "pages", "watch", "share")
                } ?: return null
                Target(Platform.FACEBOOK, user)
            }
            host == "linkedin.com" -> {
                val user = when {
                    path.startsWith("in/") -> path.removePrefix("in/").substringBefore('/')
                    else -> path.substringBefore('/')
                }.takeIf { it.isNotBlank() } ?: return null
                Target(Platform.LINKEDIN, user)
            }
            host == "threads.net" -> {
                val user = path.removePrefix("@").substringBefore('/').takeIf { it.isNotBlank() }
                    ?: return null
                Target(Platform.THREADS, user)
            }
            else -> null
        } ?: return null
        val handle = canonicalHandle(draft.platform, draft.handle) ?: return null
        return Target(draft.platform, handle)
    }

    /**
     * Opens the native app when it can resolve the profile intent; otherwise the
     * https profile page (browser or app via App Links).
     */
    fun open(context: Context, target: Target): Boolean {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(target.webUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = appIntent(target.platform, target.handle)
        if (app != null &&
            context.packageManager.resolveActivity(app, 0) != null
        ) {
            return try {
                context.startActivity(app)
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "app_missing_${target.platform}", e)
                runCatching {
                    context.startActivity(web)
                    true
                }.onFailure {
                    Log.w(TAG, "web_open_failed", it)
                }.getOrDefault(false)
            }
        }
        return runCatching {
            context.startActivity(web)
            true
        }.onFailure {
            Log.w(TAG, "web_open_failed", it)
        }.getOrDefault(false)
    }

    fun shareText(target: Target): String =
        "Find me on ${target.platform.label}\n${target.shareUrl}"
}
