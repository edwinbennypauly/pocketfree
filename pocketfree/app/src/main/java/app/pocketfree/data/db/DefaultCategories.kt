package app.pocketfree.data.db

import app.pocketfree.domain.TransactionType

data class CategorySeed(
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val color: Int
)

object DefaultCategories {
    private fun c(hex: Long) = hex.toInt()

    val expense = listOf(
        CategorySeed("Food", TransactionType.EXPENSE, "restaurant", c(0xFFF97316)),
        CategorySeed("Groceries", TransactionType.EXPENSE, "shopping_basket", c(0xFF22C55E)),
        CategorySeed("Transportation", TransactionType.EXPENSE, "directions_bus", c(0xFF3B82F6)),
        CategorySeed("Fuel", TransactionType.EXPENSE, "local_gas_station", c(0xFFEF4444)),
        CategorySeed("Shopping", TransactionType.EXPENSE, "shopping_bag", c(0xFFEC4899)),
        CategorySeed("Entertainment", TransactionType.EXPENSE, "movie", c(0xFF8B5CF6)),
        CategorySeed("Rent", TransactionType.EXPENSE, "home", c(0xFF0EA5E9)),
        CategorySeed("Utilities", TransactionType.EXPENSE, "bolt", c(0xFFEAB308)),
        CategorySeed("Internet and mobile", TransactionType.EXPENSE, "wifi", c(0xFF06B6D4)),
        CategorySeed("Healthcare", TransactionType.EXPENSE, "medical_services", c(0xFFF43F5E)),
        CategorySeed("Education", TransactionType.EXPENSE, "school", c(0xFF6366F1)),
        CategorySeed("Travel", TransactionType.EXPENSE, "flight", c(0xFF14B8A6)),
        CategorySeed("Personal care", TransactionType.EXPENSE, "spa", c(0xFFD946EF)),
        CategorySeed("Subscriptions", TransactionType.EXPENSE, "subscriptions", c(0xFF64748B)),
        CategorySeed("Family", TransactionType.EXPENSE, "family_restroom", c(0xFFF59E0B)),
        CategorySeed("Other", TransactionType.EXPENSE, "category", c(0xFF94A3B8))
    )

    val income = listOf(
        CategorySeed("Salary", TransactionType.INCOME, "payments", c(0xFF16A34A)),
        CategorySeed("Freelance", TransactionType.INCOME, "work", c(0xFF0D9488)),
        CategorySeed("Business", TransactionType.INCOME, "storefront", c(0xFF2563EB)),
        CategorySeed("Investments", TransactionType.INCOME, "trending_up", c(0xFF7C3AED)),
        CategorySeed("Interest", TransactionType.INCOME, "savings", c(0xFF0891B2)),
        CategorySeed("Gifts", TransactionType.INCOME, "card_giftcard", c(0xFFDB2777)),
        CategorySeed("Other", TransactionType.INCOME, "category", c(0xFF94A3B8))
    )

    val all: List<CategorySeed> get() = expense + income
}
