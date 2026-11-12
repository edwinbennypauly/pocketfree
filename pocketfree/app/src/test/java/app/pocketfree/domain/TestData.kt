package app.pocketfree.domain

import java.math.BigDecimal
import java.time.LocalDate

object TestData {
    val food = Category(1, "Food", TransactionType.EXPENSE, "restaurant", 0)
    val rent = Category(2, "Rent", TransactionType.EXPENSE, "home", 0)
    val salary = Category(3, "Salary", TransactionType.INCOME, "payments", 0)
    val categories = listOf(food, rent, salary)
    val categoryMap = categories.associateBy { it.id }

    val aed = RateTable(AppCurrency.AED, emptyMap())

    private var nextId = 1L
    fun tx(
        type: TransactionType,
        minor: Long,
        date: String,
        category: Category,
        description: String = "",
        currency: String = "AED",
        notes: String = "",
        id: Long = nextId++
    ) = Transaction(id, type, minor, currency, category.id, description, notes, LocalDate.parse(date))

    fun expense(minor: Long, date: String, category: Category = food, description: String = "", currency: String = "AED", id: Long = nextId++) =
        tx(TransactionType.EXPENSE, minor, date, category, description, currency, id = id)

    fun income(minor: Long, date: String, category: Category = salary, description: String = "", currency: String = "AED", id: Long = nextId++) =
        tx(TransactionType.INCOME, minor, date, category, description, currency, id = id)

    fun rates(base: AppCurrency, vararg pairs: Triple<String, String, String>) =
        RateTable(base, pairs.associate { (it.first to it.second) to BigDecimal(it.third) })
}
