package com.lipabill.app.engage

import com.lipabill.app.data.foldMerchantName
import com.lipabill.app.data.model.TransactionType

/**
 * A category is a list of merchant-name fragments. Matching is case-insensitive
 * and ignores extra spaces, so messy SMS names still hit.
 * [types] match by transaction type alone, such as a Fuliza draw that never leaves the wallet.
 */
data class CategoryRule(
    val categoryId: String,
    val nameContains: List<String>,
    val types: Set<TransactionType> = emptySet(),
    /** Any outgoing payment that carried a transaction cost matches. */
    val breaksOnFee: Boolean = false,
    /**
     * When true, type matches still skip shops in [CategoryRules.everydayEssentials]
     * (Naivas, KPLC, pharmacy, school, …).
     */
    val excludeEssentials: Boolean = false
)

object CategoryRules {
    val foodDelivery = CategoryRule(
        categoryId = "food_delivery",
        nameContains = listOf(
            "glovo",
            "uber eats",
            "ubereats",
            "bolt food",
            "jumia food"
        )
    )

    val fuliza = CategoryRule(
        categoryId = "fuliza",
        nameContains = emptyList(),
        types = setOf(TransactionType.FULIZA)
    )

    /**
     * Groceries, utilities, health and school: payments that are not habits to pause,
     * so Insights and Habits never suggest skipping them.
     */
    val everydayEssentials = CategoryRule(
        categoryId = "everyday_essentials",
        nameContains = listOf(
            "supermarket", "naivas", "quickmart", "carrefour", "chandarana", "magunas", "cleanshelf",
            "kplc", "kenya power", "nairobi water", "water and sewerage", "pharmacy", "chemist", "hospital", "clinic", "school", "academy"
        )
    )

    /** Any wallet outflow — used by quiet-days challenges. */
    val anyOutgoing = CategoryRule(
        categoryId = "any_outgoing",
        nameContains = emptyList(),
        types = setOf(
            TransactionType.SENT,
            TransactionType.PAYBILL,
            TransactionType.BUY_GOODS,
            TransactionType.POCHI,
            TransactionType.WITHDRAW,
            // Moving money into savings is not spending, so it does not end a quiet week.
            TransactionType.AIRTIME,
            TransactionType.UNKNOWN
        )
    )

    /**
     * Discretionary spend after payday: Buy Goods that are not everyday essentials,
     * plus known food delivery names on other types.
     */
    val discretionary = CategoryRule(
        categoryId = "discretionary",
        nameContains = foodDelivery.nameContains,
        types = setOf(TransactionType.BUY_GOODS),
        excludeEssentials = true
    )

    /** A week with no transaction costs: any payment with a fee, or a Fuliza draw, ends it. */
    val feeLike = CategoryRule(
        categoryId = "fee_like",
        nameContains = emptyList(),
        types = setOf(TransactionType.FULIZA),
        breaksOnFee = true
    )

    val all = listOf(foodDelivery, fuliza, anyOutgoing, discretionary, feeLike)

    /** A one-payee rule: `payee:Java House` matches that name only. */
    const val PAYEE_PREFIX = "payee:"

    fun byId(categoryId: String): CategoryRule? =
        all.firstOrNull { it.categoryId == categoryId }
            ?: categoryId.takeIf { it.startsWith(PAYEE_PREFIX) }
                ?.removePrefix(PAYEE_PREFIX)
                ?.takeIf { it.isNotBlank() }
                ?.let { CategoryRule(categoryId = categoryId, nameContains = listOf(it)) }
}

object CategoryMatcher {

    /** Type rules match any amount of that type; name rules match outgoing payments only. */
    fun matches(rule: CategoryRule, tx: ChallengeTransaction): Boolean = when {
        tx.amountCents <= 0L -> false
        rule.excludeEssentials && matches(CategoryRules.everydayEssentials, tx.counterpartyName) -> false
        tx.type in rule.types -> true
        rule.breaksOnFee && tx.feeCents > 0L && tx.type.isOutgoing() -> true
        else -> tx.type.isOutgoing() && matches(rule, tx.counterpartyName)
    }

    fun matches(rule: CategoryRule, merchantName: String?): Boolean {
        val folded = foldMerchantName(merchantName) ?: return false
        val compact = folded.replace(" ", "")
        return rule.nameContains.any { raw ->
            val needle = foldMerchantName(raw) ?: return@any false
            folded.contains(needle) || compact.contains(needle.replace(" ", ""))
        }
    }
}
