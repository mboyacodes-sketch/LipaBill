package com.lipabill.app.data.model

/**
 * Country the ledger is reading. Send / Pay dial when [canSendPay] and a
 * matching wallet SIM is present (Kenya: Safaricom or Airtel; Uganda: Airtel or MTN).
 * Tanzania / Rwanda import confirmation SMS only for now. Fuliza stays Kenya Safaricom.
 */
enum class AppRegion(
    val iso: String,
    val label: String,
    val currencyCode: String,
    val currencySymbol: String,
    val primaryWallet: MoneySource,
    val canSendPay: Boolean,
    val hasFuliza: Boolean,
    /**
     * Rough local-currency units per Kenyan shilling, for "is this a large amount" thresholds
     * written in KES (habit patterns). Not for display or totals; refresh if rates drift far.
     */
    val unitsPerKes: Double
) {
    KENYA(
        iso = "KE",
        label = "Kenya",
        currencyCode = "KES",
        currencySymbol = "Ksh",
        primaryWallet = MoneySource.MPESA,
        canSendPay = true,
        hasFuliza = true,
        unitsPerKes = 1.0
    ),
    TANZANIA(
        iso = "TZ",
        label = "Tanzania",
        currencyCode = "TZS",
        currencySymbol = "TSh",
        primaryWallet = MoneySource.MPESA,
        canSendPay = false,
        hasFuliza = false,
        unitsPerKes = 20.0
    ),
    UGANDA(
        iso = "UG",
        label = "Uganda",
        currencyCode = "UGX",
        currencySymbol = "USh",
        primaryWallet = MoneySource.MTN_MOMO,
        canSendPay = true,
        hasFuliza = false,
        unitsPerKes = 28.0
    ),
    RWANDA(
        iso = "RW",
        label = "Rwanda",
        currencyCode = "RWF",
        currencySymbol = "FRw",
        primaryWallet = MoneySource.MTN_MOMO,
        canSendPay = false,
        hasFuliza = false,
        unitsPerKes = 11.0
    );

    val sources: Set<MoneySource>
        get() = SOURCES.getValue(this)

    val inboxAddressLikes: List<String>
        get() = sources.flatMap { it.addressLikes }.distinct()

    val bodyAmountLikes: List<String>
        get() = AMOUNT_LIKES.getValue(this)

    val amountRegex: Regex
        get() = AMOUNT_REGEX.getValue(this)

    fun matchesCurrency(body: String): Boolean = amountRegex.containsMatchIn(body)

    /** Regional-indicator flag for [iso]. Drawn with the system emoji font. */
    val flagEmoji: String
        get() = isoToFlagEmoji(iso)

    companion object {
        val DEFAULT: AppRegion = KENYA

        fun fromIsoOrNull(iso: String?): AppRegion? {
            val code = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.iso.equals(code, ignoreCase = true) }
        }

        fun fromIso(iso: String?): AppRegion = fromIsoOrNull(iso) ?: DEFAULT

        /** Home-network MCC → country. Used when a SIM omits countryIso. */
        fun fromMccOrNull(mcc: Int?): AppRegion? = when (mcc) {
            639 -> KENYA
            640 -> TANZANIA
            641 -> UGANDA
            635 -> RWANDA
            else -> null
        }

        private val SOURCES: Map<AppRegion, Set<MoneySource>> = mapOf(
            KENYA to setOf(
                MoneySource.MPESA,
                MoneySource.AIRTEL_MONEY,
                MoneySource.TKASH,
                MoneySource.EQUITEL,
                MoneySource.KCB,
                MoneySource.EQUITY,
                MoneySource.COOP,
                MoneySource.NCBA,
                MoneySource.ABSA,
                MoneySource.STANBIC,
                MoneySource.FAMILY,
                MoneySource.I_AND_M,
                MoneySource.DTB,
                MoneySource.STANCHART,
                MoneySource.KWFT,
                MoneySource.SIDIAN,
                MoneySource.NBK,
                MoneySource.POSTBANK,
                MoneySource.GTBANK
            ),
            TANZANIA to setOf(
                MoneySource.MPESA,
                MoneySource.AIRTEL_MONEY,
                MoneySource.MIXX,
                MoneySource.HALOPESA,
                MoneySource.CRDB,
                MoneySource.NMB,
                MoneySource.NBC,
                MoneySource.EQUITY,
                MoneySource.STANBIC,
                MoneySource.ABSA,
                MoneySource.KCB
            ),
            UGANDA to setOf(
                MoneySource.MTN_MOMO,
                MoneySource.AIRTEL_MONEY,
                MoneySource.CENTENARY,
                MoneySource.STANBIC,
                MoneySource.EQUITY,
                MoneySource.ABSA,
                MoneySource.KCB,
                MoneySource.DTB
            ),
            RWANDA to setOf(
                MoneySource.MTN_MOMO,
                MoneySource.AIRTEL_MONEY,
                MoneySource.BK,
                MoneySource.EQUITY,
                MoneySource.KCB,
                MoneySource.I_AND_M
            )
        )

        private val AMOUNT_LIKES: Map<AppRegion, List<String>> = mapOf(
            KENYA to listOf("%Ksh%", "%KES%"),
            TANZANIA to listOf("%TZS%", "%TSh%", "%Tsh%"),
            UGANDA to listOf("%UGX%", "%USh%", "%Ush%"),
            RWANDA to listOf("%RWF%", "%Frw%", "%FRw%")
        )

        private val AMOUNT_REGEX: Map<AppRegion, Regex> = mapOf(
            KENYA to Regex("""(?:kshs?|kes)(?=\s*\d|\b)""", RegexOption.IGNORE_CASE),
            TANZANIA to Regex("""(?:tzs|tshs?)(?=\s*\d|\b)""", RegexOption.IGNORE_CASE),
            UGANDA to Regex("""(?:ugx|ushs?|ugsh)(?=\s*\d|\b)""", RegexOption.IGNORE_CASE),
            RWANDA to Regex("""(?:rwf|frw)(?=\s*\d|\b)""", RegexOption.IGNORE_CASE)
        )
    }
}

internal fun isoToFlagEmoji(iso: String): String {
    val code = iso.trim().uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
    val points = IntArray(2) { index -> 0x1F1E6 + (code[index].code - 'A'.code) }
    return String(points, 0, 2)
}

/** Process-wide region for non-Compose callers (notifications, SMS receiver). */
object ActiveRegion {
    @Volatile
    var current: AppRegion = AppRegion.DEFAULT

    @Volatile
    var enabledSources: Set<MoneySource> = AppRegion.DEFAULT.sources
}
