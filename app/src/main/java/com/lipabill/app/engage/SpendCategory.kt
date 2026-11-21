package com.lipabill.app.engage

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.isBusinessLikeRegular
import com.lipabill.app.viewmodel.payeeKey

/**
 * Spend labels on top of SMS [TransactionType].
 * Auto rules + per-payee overrides (keyed by payeeKey) drive the resolved label.
 */
enum class SpendCategory(val id: String, val label: String) {
    FOOD("food", "Food"),
    ESSENTIALS("essentials", "Essentials"),
    TRANSPORT("transport", "Transport"),
    BILLS("bills", "Bills"),
    PEOPLE("people", "People"),
    SHOPPING("shopping", "Shopping"),
    AIRTIME("airtime", "Airtime"),
    INCOME("income", "Income"),
    OTHER("other", "Other");

    companion object {
        fun fromId(id: String?): SpendCategory? =
            entries.firstOrNull { it.id == id }

        /** Categories the user can pick when editing a payment. */
        val editable: List<SpendCategory> = entries.filter { it != OTHER } + OTHER
    }
}

/** Prefs encoding: `payeeKey=spendCategoryId`. */
fun encodeSpendCategoryOverride(payeeKey: String, category: SpendCategory): String =
    "$payeeKey=${category.id}"

fun parseSpendCategoryOverrides(encoded: Set<String>): Map<String, String> {
    if (encoded.isEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>(encoded.size)
    for (entry in encoded) {
        val eq = entry.indexOf('=')
        if (eq <= 0 || eq >= entry.lastIndex) continue
        val key = entry.substring(0, eq)
        val id = entry.substring(eq + 1)
        if (key.isNotBlank() && id.isNotBlank()) out[key] = id
    }
    return out
}

/**
 * Name fragments for transport (rides / fuel), separate from food delivery.
 */
val transportNameHints: List<String> = listOf(
    "uber", "bolt", "little cab", "little ride", "faras", "faras ride", "taxi",
    "shell", "total energies", "total petrol", "rubis", "ola energy", "petrol", "diesel"
)

/**
 * Restaurant / cafe cues beyond [CategoryRules.foodDelivery].
 * Multi-token brands only — no bare "java" / "cafe" person-name traps beyond matcher contains.
 */
val foodNameHints: List<String> = listOf(
    "restaurant", "cafe", "caffè", "coffee",
    "java house", "artcaffe", "kfc", "chicken inn", "galitos", "galito's",
    "pizza inn", "dominos", "domino's", "nandos", "nando's", "subway",
    "burger king", "cold stone"
)

/** True when Insights and Habits should treat the payment as food (delivery or restaurant). */
fun isFoodSpend(type: TransactionType, counterpartyName: String?): Boolean {
    if (!type.isOutgoing() || type == TransactionType.SAVINGS || type == TransactionType.AIRTIME) {
        return false
    }
    return CategoryMatcher.matches(CategoryRules.foodDelivery, counterpartyName) ||
        CategoryMatcher.matches(
            CategoryRule(categoryId = "food_extra", nameContains = foodNameHints),
            counterpartyName
        )
}

/**
 * Utility / subscription bills beyond everyday essentials.
 * Bare MNO brands are omitted — AIRTIME type wins first, PAYBILL falls through to Bills;
 * agents named "Safaricom" must not become bill labels.
 */
val billsNameHints: List<String> = listOf(
    "dstv", "gotv", "showmax", "netflix", "zuku", "faiba", "jamii", "liquid",
    "safaricom home", "airtel home", "telkom home", "startimes", "youtube", "spotify",
    "paybill", "nhif", "social health", "sha contribution", "nssf",
    "kra pin", "kenya revenue", "itax", "housing levy"
)

/**
 * Auto spend label from SMS type + merchant name (no user override).
 */
fun autoSpendCategory(type: TransactionType, counterpartyName: String?): SpendCategory {
    when (type) {
        TransactionType.RECEIVED, TransactionType.DEPOSIT, TransactionType.REVERSED ->
            return SpendCategory.INCOME
        TransactionType.AIRTIME -> return SpendCategory.AIRTIME
        TransactionType.FULIZA -> return SpendCategory.OTHER
        TransactionType.SAVINGS -> return SpendCategory.OTHER
        TransactionType.WITHDRAW -> return SpendCategory.OTHER
        else -> Unit
    }
    if (isFoodSpend(type, counterpartyName)) {
        return SpendCategory.FOOD
    }
    if (CategoryMatcher.matches(CategoryRules.everydayEssentials, counterpartyName)) {
        // Utilities in essentials → Bills; grocers/health/school stay Essentials.
        val billsFromEssentials = listOf(
            "kplc", "kenya power", "nairobi water", "water and sewerage"
        )
        if (CategoryMatcher.matches(
                CategoryRule(categoryId = "util", nameContains = billsFromEssentials),
                counterpartyName
            )
        ) {
            return SpendCategory.BILLS
        }
        return SpendCategory.ESSENTIALS
    }
    if (CategoryMatcher.matches(
            CategoryRule(categoryId = "transport", nameContains = transportNameHints),
            counterpartyName
        )
    ) {
        return SpendCategory.TRANSPORT
    }
    if (CategoryMatcher.matches(
            CategoryRule(categoryId = "bills", nameContains = billsNameHints),
            counterpartyName
        )
    ) {
        return SpendCategory.BILLS
    }
    return when (type) {
        TransactionType.SENT, TransactionType.POCHI -> {
            val name = counterpartyName?.trim().orEmpty()
            val key = payeeKey(counterpartyName, null).orEmpty()
            if (name.isNotEmpty() && isBusinessLikeRegular(name, key, type)) {
                SpendCategory.BILLS
            } else {
                SpendCategory.PEOPLE
            }
        }
        TransactionType.PAYBILL -> SpendCategory.BILLS
        TransactionType.BUY_GOODS -> SpendCategory.SHOPPING
        else -> SpendCategory.OTHER
    }
}

/**
 * True when the auto label came only from a name fragment (transport/bills hints),
 * so the receipt can nudge “Check category”.
 */
fun needsCategoryReview(type: TransactionType, counterpartyName: String?): Boolean {
    if (counterpartyName.isNullOrBlank()) return false
    if (isFoodSpend(type, counterpartyName)) return false
    if (CategoryMatcher.matches(CategoryRules.everydayEssentials, counterpartyName)) return false
    val resolved = autoSpendCategory(type, counterpartyName)
    val fromTransport = resolved == SpendCategory.TRANSPORT &&
        CategoryMatcher.matches(
            CategoryRule(categoryId = "transport", nameContains = transportNameHints),
            counterpartyName
        )
    val fromBillsHint = resolved == SpendCategory.BILLS &&
        type != TransactionType.PAYBILL &&
        CategoryMatcher.matches(
            CategoryRule(categoryId = "bills", nameContains = billsNameHints),
            counterpartyName
        )
    return fromTransport || fromBillsHint
}

/**
 * Resolved spend category: user override for [payeeKey] wins, else auto rules.
 * [overrides] maps payeeKey → [SpendCategory.id].
 */
fun resolveSpendCategory(
    tx: MpesaTransaction,
    payeeKey: String?,
    overrides: Map<String, String>
): SpendCategory {
    if (payeeKey != null) {
        val fromKey = SpendCategory.fromId(overrides[payeeKey])
        if (fromKey != null) return fromKey
        // Legacy / alias keys the user may have taught under another spelling.
        for ((storedKey, id) in overrides) {
            if (storedKey.equals(payeeKey, ignoreCase = true)) {
                SpendCategory.fromId(id)?.let { return it }
            }
        }
    }
    return autoSpendCategory(tx.type, tx.counterpartyName)
}
