package com.lipabill.app.engage

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

    fun byId(categoryId: String): CategoryRule? = all.firstOrNull { it.categoryId == categoryId }
}

object CategoryMatcher {

    fun matches(rule: CategoryRule, merchantName: String?): Boolean {
        val folded = fold(merchantName) ?: return false
        val compact = folded.replace(" ", "")
        return rule.nameContains.any { raw ->
            val needle = fold(raw) ?: return@any false
            folded.contains(needle) || compact.contains(needle.replace(" ", ""))
        }
    }

    private fun fold(raw: String?): String? {
        val text = raw?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return null
        return text.replace(Regex("\\s+"), " ")
    }
}
