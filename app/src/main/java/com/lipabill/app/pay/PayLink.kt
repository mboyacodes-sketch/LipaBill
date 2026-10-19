package com.lipabill.app.pay

import com.lipabill.app.data.parser.digitsOnly
import com.lipabill.app.data.parser.strictKenyanMobile
import java.math.BigInteger

/**
 * Short pay code for a destination and optional whole-shilling amount.
 * Shared links use `lipabill://p/{code}` so a tap opens this app.
 *
 * Version 3 packs phone, till, or paybill (one kind only).
 * Version 2 phone codes rotate each UTC day unless fixed.
 * Version 1 phone codes still decode with no day check.
 */
object PayLink {
    const val APP_PREFIX = "lipabill://p/"

    private const val VERSION_V1 = 1
    private const val VERSION_V2 = 2
    private const val VERSION_V3 = 3
    private const val WIDTH_V1 = 11
    private const val WIDTH_V2 = 13
    private const val WIDTH_V3 = 27
    private const val MAX_AMOUNT = 0xFFFFFFL
    private const val FIXED_DAY = 0xFFFF
    private const val DAY_MS = 86_400_000L
    private const val DAY_EPOCH_MS = 1_704_067_200_000L
    private const val KIND_PHONE = 0
    private const val KIND_TILL = 1
    private const val KIND_PAYBILL = 2
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val mixKey = byteArrayOf(
        0x4C, 0x69, 0x70, 0x61, 0x42, 0x69, 0x6C, 0x6C, 0x50
    )

    enum class Kind {
        PHONE,
        TILL,
        PAYBILL
    }

    data class Target(
        val kind: Kind,
        /** Local `07…` phone, till digits, or paybill business number. */
        val identifier: String,
        /** Paybill account only. */
        val account: String? = null,
        val amountShillings: Long?,
        val token: String
    ) {
        val shareUrl: String get() = APP_PREFIX + token

        /** Phone destination when [kind] is [Kind.PHONE]. */
        val phone: String get() = if (kind == Kind.PHONE) identifier else ""
    }

    fun canonicalPhone(raw: String?): String? = strictKenyanMobile(raw)

    /** Till or paybill business: 5–12 digits. */
    fun canonicalMerchantId(raw: String?): String? = digitsOnly(raw)

    /** Paybill account letters/digits, 1–12 characters. */
    fun canonicalAccount(raw: String?): String? {
        val clean = raw?.filter { it.isLetterOrDigit() }.orEmpty()
        return clean.takeIf { it.length in 1..12 }
    }

    fun dayIndex(nowMillis: Long = System.currentTimeMillis()): Int =
        ((nowMillis - DAY_EPOCH_MS) / DAY_MS).toInt().coerceIn(0, FIXED_DAY - 1)

    fun encode(
        phoneRaw: String,
        amountShillings: Long? = null,
        rotateDaily: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): Target? = encode(
        kind = Kind.PHONE,
        identifier = phoneRaw,
        account = null,
        amountShillings = amountShillings,
        rotateDaily = rotateDaily,
        nowMillis = nowMillis
    )

    fun encode(
        kind: Kind,
        identifier: String,
        account: String? = null,
        amountShillings: Long? = null,
        rotateDaily: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): Target? {
        val amount = amountShillings ?: 0L
        if (amount !in 0..MAX_AMOUNT) return null
        val day = if (rotateDaily) dayIndex(nowMillis) else FIXED_DAY
        val idDigits: String
        val accountPacked: String
        when (kind) {
            Kind.PHONE -> {
                val phone = canonicalPhone(identifier) ?: return null
                idDigits = "254" + phone.drop(1)
                accountPacked = ""
                return packV3(
                    kind = KIND_PHONE,
                    idDigits = idDigits,
                    account = accountPacked,
                    amount = amount,
                    day = day,
                    phone = phone,
                    tillOrBusiness = null,
                    accountOut = null
                )
            }
            Kind.TILL -> {
                idDigits = canonicalMerchantId(identifier) ?: return null
                accountPacked = ""
                return packV3(
                    kind = KIND_TILL,
                    idDigits = idDigits.padStart(12, '0'),
                    account = accountPacked,
                    amount = amount,
                    day = day,
                    phone = null,
                    tillOrBusiness = idDigits,
                    accountOut = null
                )
            }
            Kind.PAYBILL -> {
                idDigits = canonicalMerchantId(identifier) ?: return null
                accountPacked = canonicalAccount(account) ?: return null
                return packV3(
                    kind = KIND_PAYBILL,
                    idDigits = idDigits.padStart(12, '0'),
                    account = accountPacked,
                    amount = amount,
                    day = day,
                    phone = null,
                    tillOrBusiness = idDigits,
                    accountOut = accountPacked
                )
            }
        }
    }

    fun parse(raw: String?, nowMillis: Long = System.currentTimeMillis()): Target? {
        val token = tokenFrom(raw) ?: return null
        decodeV3(token, nowMillis)?.let { return it.copy(token = token) }
        decodeV2(token, nowMillis)?.let { return it.copy(token = token) }
        decodeV1(token)?.let { return it.copy(token = token) }
        return null
    }

    private fun packV3(
        kind: Int,
        idDigits: String,
        account: String,
        amount: Long,
        day: Int,
        phone: String?,
        tillOrBusiness: String?,
        accountOut: String?
    ): Target? {
        if (idDigits.length != 12 || idDigits.any { !it.isDigit() }) return null
        val packed = ByteArray(WIDTH_V3)
        packed[0] = VERSION_V3.toByte()
        packed[1] = kind.toByte()
        writeBcd(packed, 2, idDigits)
        packed[8] = ((day shr 8) and 0xFF).toByte()
        packed[9] = (day and 0xFF).toByte()
        packed[10] = ((amount shr 16) and 0xFF).toByte()
        packed[11] = ((amount shr 8) and 0xFF).toByte()
        packed[12] = (amount and 0xFF).toByte()
        packed[13] = account.length.toByte()
        val accountBytes = account.toByteArray(Charsets.US_ASCII)
        for (i in 0 until 12) {
            packed[14 + i] = accountBytes.getOrElse(i) { 0 }
        }
        packed[26] = checksum(packed, until = 26)
        val token = encodeBytes(mix(packed))
        val resolvedKind = when (kind) {
            KIND_TILL -> Kind.TILL
            KIND_PAYBILL -> Kind.PAYBILL
            else -> Kind.PHONE
        }
        val identifier = phone ?: tillOrBusiness ?: return null
        return Target(
            kind = resolvedKind,
            identifier = identifier,
            account = accountOut,
            amountShillings = amount.takeIf { it > 0 },
            token = token
        )
    }

    private fun tokenFrom(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val body = linkBody(trimmed) ?: return null
        val clean = body
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .substringBefore('/')
        if (clean.length !in 8..40) return null
        if (clean.any { alphabet.indexOf(it) < 0 }) return null
        return clean
    }

    private fun linkBody(trimmed: String): String? {
        val lower = trimmed.lowercase()
        val appMarker = "lipabill://p/"
        val webMarker = "https://lipabill.app/p/"
        val httpMarker = "http://lipabill.app/p/"
        return when {
            lower.startsWith(appMarker) -> trimmed.substring(appMarker.length)
            lower.startsWith(webMarker) -> trimmed.substring(webMarker.length)
            lower.startsWith(httpMarker) -> trimmed.substring(httpMarker.length)
            lower.contains("://") || lower.contains('/') || lower.contains('.') -> null
            else -> trimmed
        }
    }

    private fun decodeV3(token: String, nowMillis: Long): Target? {
        val mixed = decodeBytes(token, WIDTH_V3) ?: return null
        val packed = mix(mixed)
        if ((packed[0].toInt() and 0xFF) != VERSION_V3) return null
        if (packed[26] != checksum(packed, until = 26)) return null
        if (!dayOk(packed, 8, nowMillis)) return null
        val kindByte = packed[1].toInt() and 0xFF
        val idDigits = readBcd(packed, 2, 12)
        if (idDigits.length != 12) return null
        val amount = readAmount(packed, 10)
        if (amount > MAX_AMOUNT) return null
        val accountLen = packed[13].toInt() and 0xFF
        if (accountLen > 12) return null
        val account = if (accountLen == 0) {
            ""
        } else {
            String(packed.copyOfRange(14, 14 + accountLen), Charsets.US_ASCII)
        }
        return when (kindByte) {
            KIND_PHONE -> {
                if (accountLen != 0) return null
                val phone = canonicalPhone(idDigits) ?: return null
                Target(Kind.PHONE, phone, null, amount.takeIf { it > 0 }, "")
            }
            KIND_TILL -> {
                if (accountLen != 0) return null
                val till = canonicalMerchantId(idDigits.trimStart('0')) ?: return null
                Target(Kind.TILL, till, null, amount.takeIf { it > 0 }, "")
            }
            KIND_PAYBILL -> {
                val business = canonicalMerchantId(idDigits.trimStart('0')) ?: return null
                val acc = canonicalAccount(account) ?: return null
                Target(Kind.PAYBILL, business, acc, amount.takeIf { it > 0 }, "")
            }
            else -> null
        }
    }

    private fun decodeV2(token: String, nowMillis: Long): Target? {
        val mixed = decodeBytes(token, WIDTH_V2) ?: return null
        val packed = mix(mixed)
        if ((packed[0].toInt() and 0xFF) != VERSION_V2) return null
        if (packed[12] != checksum(packed, until = 12)) return null
        if (!dayOk(packed, 7, nowMillis)) return null
        return phoneTarget(packed, phoneStart = 1, amountAt = 9)
    }

    private fun decodeV1(token: String): Target? {
        val mixed = decodeBytes(token, WIDTH_V1) ?: return null
        val packed = mix(mixed)
        if ((packed[0].toInt() and 0xFF) != VERSION_V1) return null
        if (packed[10] != checksum(packed, until = 10)) return null
        return phoneTarget(packed, phoneStart = 1, amountAt = 7)
    }

    private fun dayOk(packed: ByteArray, dayAt: Int, nowMillis: Long): Boolean {
        val day = ((packed[dayAt].toInt() and 0xFF) shl 8) or (packed[dayAt + 1].toInt() and 0xFF)
        if (day == FIXED_DAY) return true
        val today = dayIndex(nowMillis)
        return day == today || day == today - 1
    }

    private fun phoneTarget(packed: ByteArray, phoneStart: Int, amountAt: Int): Target? {
        val digits = readBcd(packed, phoneStart, 12)
        val phone = canonicalPhone(digits) ?: return null
        val amount = readAmount(packed, amountAt)
        if (amount > MAX_AMOUNT) return null
        return Target(Kind.PHONE, phone, null, amount.takeIf { it > 0 }, "")
    }

    private fun readAmount(packed: ByteArray, at: Int): Long =
        ((packed[at].toInt() and 0xFF).toLong() shl 16) or
            ((packed[at + 1].toInt() and 0xFF).toLong() shl 8) or
            (packed[at + 2].toInt() and 0xFF).toLong()

    private fun writeBcd(packed: ByteArray, start: Int, digits: String) {
        for (i in 0 until 6) {
            val hi = digits[i * 2].digitToInt()
            val lo = digits[i * 2 + 1].digitToInt()
            packed[start + i] = ((hi shl 4) or lo).toByte()
        }
    }

    private fun readBcd(packed: ByteArray, start: Int, digitCount: Int): String {
        val out = StringBuilder(digitCount)
        for (i in 0 until digitCount / 2) {
            val b = packed[start + i].toInt() and 0xFF
            val hi = b shr 4
            val lo = b and 0x0F
            if (hi > 9 || lo > 9) return ""
            out.append(hi)
            out.append(lo)
        }
        return out.toString()
    }

    private fun checksum(packed: ByteArray, until: Int): Byte {
        var sum = 0
        for (i in 0 until until) sum = sum xor (packed[i].toInt() and 0xFF)
        return sum.toByte()
    }

    private fun mix(data: ByteArray): ByteArray = ByteArray(data.size) { i ->
        (data[i].toInt() xor mixKey[i % mixKey.size].toInt()).toByte()
    }

    private fun encodeBytes(data: ByteArray): String {
        var value = BigInteger(1, data)
        if (value == BigInteger.ZERO) return alphabet.substring(0, 1)
        val base = BigInteger.valueOf(62)
        val out = StringBuilder()
        while (value > BigInteger.ZERO) {
            val div = value.divideAndRemainder(base)
            out.append(alphabet[div[1].toInt()])
            value = div[0]
        }
        return out.reverse().toString()
    }

    private fun decodeBytes(token: String, width: Int): ByteArray? {
        var value = BigInteger.ZERO
        val base = BigInteger.valueOf(62)
        for (ch in token) {
            val idx = alphabet.indexOf(ch)
            if (idx < 0) return null
            value = value.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        val raw = value.toByteArray()
        val stripped = if (raw.isNotEmpty() && raw[0] == 0.toByte()) {
            raw.copyOfRange(1, raw.size)
        } else {
            raw
        }
        if (stripped.size > width) return null
        return ByteArray(width - stripped.size) + stripped
    }
}
