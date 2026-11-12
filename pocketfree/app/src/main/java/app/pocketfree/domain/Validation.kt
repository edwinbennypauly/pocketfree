package app.pocketfree.domain

import java.time.LocalDate

object TransactionValidator {
    /** Largest accepted amount: 100,000,000,000.00 in a 2-decimal currency. */
    const val MAX_MINOR = 10_000_000_000_000L
    const val MAX_DESCRIPTION = 200
    const val MAX_NOTES = 2000
    val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)

    fun validate(
        type: TransactionType,
        amountMinor: Long,
        currencyCode: String,
        category: Category?,
        description: String,
        notes: String,
        date: LocalDate,
        allowArchivedCategory: Boolean = false
    ): List<String> {
        val errors = ArrayList<String>()
        if (amountMinor <= 0) errors.add("Amount must be greater than zero.")
        if (amountMinor > MAX_MINOR) errors.add("Amount is too large.")
        if (AppCurrency.entries.none { it.code == currencyCode }) errors.add("Unsupported currency: $currencyCode.")
        if (category == null) {
            errors.add("Choose a category.")
        } else {
            if (category.type != type) errors.add("Category does not match the transaction type.")
            if (category.isArchived && !allowArchivedCategory) errors.add("That category is archived.")
        }
        if (description.length > MAX_DESCRIPTION) errors.add("Description is too long (max $MAX_DESCRIPTION characters).")
        if (notes.length > MAX_NOTES) errors.add("Notes are too long (max $MAX_NOTES characters).")
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) errors.add("Date must be between 2000 and 2100.")
        return errors
    }
}
