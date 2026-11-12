package app.pocketfree.data.db

import app.pocketfree.domain.Budget
import app.pocketfree.domain.Category
import app.pocketfree.domain.RecurringRule
import app.pocketfree.domain.Transaction
import java.time.LocalDate
import java.time.YearMonth

fun CategoryEntity.toDomain() = Category(id, name, type, iconKey, colorArgb, isArchived, sortOrder)

fun TransactionEntity.toDomain() = Transaction(
    id, type, amountMinor, currencyCode, categoryId, description, notes,
    LocalDate.ofEpochDay(dateEpochDay), recurringRuleId
)

fun BudgetEntity.toDomain() = Budget(YearMonth.parse(yearMonth), categoryId, limitMinor, currencyCode)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id, type, amountMinor, currencyCode, categoryId, description, notes, frequency,
    LocalDate.ofEpochDay(startEpochDay),
    endEpochDay?.let { LocalDate.ofEpochDay(it) },
    lastGeneratedEpochDay?.let { LocalDate.ofEpochDay(it) },
    isActive
)
