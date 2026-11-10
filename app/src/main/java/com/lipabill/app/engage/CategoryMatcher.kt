package com.lipabill.app.engage

import com.lipabill.app.data.foldMerchantName

/**
 * A category is a list of merchant-name fragments. Matching is case-insensitive
 * and ignores extra spaces, so messy SMS names still hit.
 */
data class CategoryRule(
    val categoryId: String,
    val nameContains: List<String>
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

    val all = listOf(foodDelivery)

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

    fun matches(rule: CategoryRule, merchantName: String?): Boolean {
        val folded = foldMerchantName(merchantName) ?: return false
        val compact = folded.replace(" ", "")
        return rule.nameContains.any { raw ->
            val needle = foldMerchantName(raw) ?: return@any false
            folded.contains(needle) || compact.contains(needle.replace(" ", ""))
        }
    }
}
