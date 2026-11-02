package com.lipabill.app.data.model

/**
 * Institution that sent a confirmation SMS. Send / Pay still run on Kenya M-Pesa USSD;
 * the ledger records every rail we can parse for the selected [AppRegion].
 */
enum class MoneySource(val label: String, val addressLikes: List<String>) {
    MPESA("M-Pesa", listOf("%MPESA%", "%M-PESA%")),
    AIRTEL_MONEY("Airtel Money", listOf("%AIRTEL%")),
    TKASH("T-Kash", listOf("%TKASH%", "%T-KASH%")),
    EQUITEL("Equitel", listOf("%EQUITEL%")),
    MIXX("Mixx", listOf("%MIXX%", "%TIGO%", "%YAS%")),
    HALOPESA("HaloPesa", listOf("%HALOPESA%", "%HALOTEL%")),
    MTN_MOMO("MTN MoMo", listOf("%MTN%")),
    KCB("KCB", listOf("%KCB%")),
    EQUITY("Equity", listOf("%EQUITY%")),
    COOP("Co-op", listOf("%COOP%", "%CO-OP%")),
    NCBA("NCBA", listOf("%NCBA%")),
    ABSA("Absa", listOf("%ABSA%")),
    STANBIC("Stanbic", listOf("%STANBIC%")),
    FAMILY("Family Bank", listOf("%FAMILY%")),
    I_AND_M("I&M", listOf("%IMBANK%", "%I&M%", "%IANDM%")),
    DTB("DTB", listOf("%DTB%")),
    STANCHART("StanChart", listOf("%STANCHART%", "%SCB%")),
    KWFT("KWFT", listOf("%KWFT%")),
    SIDIAN("Sidian", listOf("%SIDIAN%")),
    NBK("NBK", listOf("%NBK%")),
    POSTBANK("Postbank", listOf("%POSTBANK%")),
    GTBANK("GTBank", listOf("%GTBANK%")),
    CRDB("CRDB", listOf("%CRDB%")),
    NMB("NMB", listOf("%NMB%")),
    NBC("NBC", listOf("%NBC%")),
    CENTENARY("Centenary", listOf("%CENTENARY%")),
    BK("Bank of Kigali", listOf("%BK%", "%BKIGALI%", "%BANKOFKIGALI%"));

    val isWallet: Boolean
        get() = when (this) {
            MPESA,
            AIRTEL_MONEY,
            TKASH,
            EQUITEL,
            MIXX,
            HALOPESA,
            MTN_MOMO -> true
            else -> false
        }

    companion object {
        fun fromSender(address: String?, region: AppRegion? = null): MoneySource? {
            val mapped = mapSender(address, region) ?: return null
            if (region != null && mapped !in region.sources) return null
            return mapped
        }

        private fun mapSender(address: String?, region: AppRegion?): MoneySource? {
            val n = normalizeSender(address) ?: return null
            if (n == "MPESA") return MPESA
            airtelMoneyOrNull(n, region)?.let { return it }
            if (isKcbWallet(n)) return KCB
            if (n.contains("IANDM") || n == "IM" || n == "IMBANK") return I_AND_M
            return when (n) {
                "TKASH" -> TKASH
                "EQUITEL", "EAZZY" -> EQUITEL
                "TIGOPESA", "TIGO", "MIXX", "YAS", "MIXXBYYAS" -> MIXX
                "HALOPESA", "HALOTEL" -> HALOPESA
                "MTN", "MTNMOMO", "MTNMONEY", "MOMOUG", "MOMORW" -> MTN_MOMO
                "EQUITY", "EQUITYBANK", "EQBANK" -> EQUITY
                "COOP", "COOPBANK", "COOPERATIVE", "COOPERATIVEBANK" -> COOP
                "NCBA", "NCBABANK" -> NCBA
                "ABSA", "ABSABANK", "BARCLAYS" -> ABSA
                "STANBIC", "STANBICKE", "STANBICTZ", "STANBICUG" -> STANBIC
                "FAMILY", "FAMILYBANK" -> FAMILY
                "DTB", "DTBKENYA", "DTBUGANDA" -> DTB
                "STANCHART", "SCB", "STANDARDBANK", "STANDARDCHARTERED" -> STANCHART
                "KWFT" -> KWFT
                "SIDIAN", "SIDIANBANK" -> SIDIAN
                "NBK", "NATIONALBANK" -> NBK
                "POSTBANK", "KPOSB" -> POSTBANK
                "GTBANK", "GTB", "GTCO" -> GTBANK
                "CRDB", "CRDBBANK" -> CRDB
                "NMB", "NMBBANK" -> NMB
                "NBCBANK", "NBCTZ" -> NBC
                "CENTENARY", "CENTENARYBANK" -> CENTENARY
                "BKIGALI", "BANKOFKIGALI", "BK" -> BK
                else -> null
            }
        }

        /** Wallet headers, not Airtel telco promos (`AIRTEL`, `AirtelAlert`). */
        private fun airtelMoneyOrNull(n: String, region: AppRegion?): MoneySource? {
            if (n.contains("AIRTELMONEY") || n == "AIRTELMM" || n == "AIRTELMNY" || n == "AIRTELM") {
                return AIRTEL_MONEY
            }
            if (n == "AIRTEL" && region != null && region != AppRegion.KENYA) {
                return AIRTEL_MONEY
            }
            return null
        }

        /** `KCB` and `KCB Bank`. Skip `KCBInfo` / `KCBALERTS` marketing. */
        private fun isKcbWallet(n: String): Boolean {
            if (n == "KCB" || n == "KCBBANK" || n == "KCBLIVE") return true
            if (!n.startsWith("KCB")) return false
            if (n.contains("INFO") || n.contains("ALERT")) return false
            return true
        }

        fun normalizeSender(address: String?): String? {
            if (address.isNullOrBlank()) return null
            return address.trim()
                .uppercase()
                .replace("-", "")
                .replace(" ", "")
                .replace("_", "")
                .replace("&", "")
        }
    }
}
