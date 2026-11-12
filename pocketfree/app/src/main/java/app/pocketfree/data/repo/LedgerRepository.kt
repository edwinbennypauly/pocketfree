package app.pocketfree.data.repo

import androidx.room.withTransaction
import app.pocketfree.data.db.AppDatabase
import app.pocketfree.data.db.BudgetDao
import app.pocketfree.data.db.BudgetEntity
import app.pocketfree.data.db.CategoryDao
import app.pocketfree.data.db.CategoryEntity
import app.pocketfree.data.db.ExchangeRateDao
import app.pocketfree.data.db.ExchangeRateEntity
import app.pocketfree.data.db.RecurringDao
import app.pocketfree.data.db.RecurringRuleEntity
import app.pocketfree.data.db.TransactionDao
import app.pocketfree.data.db.TransactionEntity
import app.pocketfree.data.db.toDomain
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Frequency
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.OVERALL_BUDGET_ID
import app.pocketfree.domain.RateTable
import app.pocketfree.domain.Recurring
import app.pocketfree.domain.TransactionType
import app.pocketfree.domain.TransactionValidator
import app.pocketfree.domain.backup.BackupJson
import app.pocketfree.domain.backup.ImportPlan
import app.pocketfree.domain.backup.ImportPlanner
import app.pocketfree.domain.backup.ParsedImport
import app.pocketfree.domain.backup.TransactionCsv
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data class Invalid(val errors: List<String>) : SaveOutcome
}

sealed interface CategoryOutcome {
    data object Saved : CategoryOutcome
    data object Deleted : CategoryOutcome
    data object Archived : CategoryOutcome
    data class Invalid(val message: String) : CategoryOutcome
}

data class ImportPreview(val parsed: ParsedImport, val plan: ImportPlan?)

data class ImportResult(
    val added: Int,
    val duplicatesSkipped: Int,
    val categoriesCreated: Int,
    val budgets: Int,
    val rates: Int,
    val rowsWithErrors: Int
)

@Singleton
class LedgerRepository @Inject constructor(
    private val db: AppDatabase,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val rateDao: ExchangeRateDao,
    private val recurringDao: RecurringDao,
    private val settings: SettingsRepository
) {
    private data class Raw(
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity>,
        val rates: List<ExchangeRateEntity>,
        val recurring: List<RecurringRuleEntity>
    )

    /** Everything, re-emitted whenever any table or the default currency changes. All screens are built from this. */
    val snapshot: Flow<LedgerSnapshot> = combine(
        combine(
            categoryDao.observeAll(),
            transactionDao.observeAll(),
            budgetDao.observeAll(),
            rateDao.observeAll(),
            recurringDao.observeAll()
        ) { c, t, b, r, rec -> Raw(c, t, b, r, rec) },
        settings.defaultCurrency
    ) { raw, currency ->
        LedgerSnapshot(
            categories = raw.categories.map { it.toDomain() },
            transactions = raw.transactions.map { it.toDomain() },
            budgets = raw.budgets.map { it.toDomain() },
            recurring = raw.recurring.map { it.toDomain() },
            rates = RateTable(
                currency,
                raw.rates.associate { (it.fromCode to it.toCode) to (it.rate.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
            ),
            defaultCurrency = currency
        )
    }.flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------ transactions

    suspend fun saveTransaction(
        id: Long?,
        type: TransactionType,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        description: String,
        notes: String,
        date: LocalDate
    ): SaveOutcome {
        val category = categoryDao.getById(categoryId)?.toDomain()
        val existing = if (id != null) transactionDao.getById(id) else null
        val errors = TransactionValidator.validate(
            type, amountMinor, currencyCode, category, description.trim(), notes.trim(), date,
            allowArchivedCategory = existing != null && existing.categoryId == categoryId
        )
        if (errors.isNotEmpty()) return SaveOutcome.Invalid(errors)
        val now = System.currentTimeMillis()
        if (existing == null) {
            transactionDao.insert(
                TransactionEntity(
                    type = type, amountMinor = amountMinor, currencyCode = currencyCode, categoryId = categoryId,
                    description = description.trim(), notes = notes.trim(), dateEpochDay = date.toEpochDay(),
                    createdAt = now, updatedAt = now
                )
            )
        } else {
            transactionDao.update(
                existing.copy(
                    type = type, amountMinor = amountMinor, currencyCode = currencyCode, categoryId = categoryId,
                    description = description.trim(), notes = notes.trim(), dateEpochDay = date.toEpochDay(),
                    updatedAt = now
                )
            )
        }
        return SaveOutcome.Saved
    }

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    /** Copies a transaction to [date] (as a normal, non-recurring entry). */
    suspend fun duplicateTransaction(id: Long, date: LocalDate): Boolean {
        val source = transactionDao.getById(id) ?: return false
        val now = System.currentTimeMillis()
        transactionDao.insert(
            source.copy(id = 0, dateEpochDay = date.toEpochDay(), createdAt = now, updatedAt = now, recurringRuleId = null)
        )
        return true
    }

    // ------------------------------------------------------------------ categories

    suspend fun saveCategory(id: Long?, name: String, type: TransactionType, iconKey: String, colorArgb: Int): CategoryOutcome {
        val clean = name.trim()
        if (clean.isEmpty()) return CategoryOutcome.Invalid("Enter a name.")
        if (clean.length > 40) return CategoryOutcome.Invalid("Name is too long (max 40 characters).")
        val all = categoryDao.getAll()
        val effectiveType = if (id != null) (all.firstOrNull { it.id == id }?.type ?: type) else type
        if (all.any { it.id != id && it.type == effectiveType && !it.isArchived && it.name.equals(clean, ignoreCase = true) }) {
            return CategoryOutcome.Invalid("A category named \"$clean\" already exists.")
        }
        if (id == null) {
            categoryDao.insert(
                CategoryEntity(name = clean, type = type, iconKey = iconKey, colorArgb = colorArgb, sortOrder = categoryDao.maxSortOrder() + 1)
            )
        } else {
            val existing = categoryDao.getById(id) ?: return CategoryOutcome.Invalid("Category not found.")
            // Only name/icon/colour change; transactions keep pointing at the same id, so history is untouched.
            categoryDao.update(existing.copy(name = clean, iconKey = iconKey, colorArgb = colorArgb))
        }
        return CategoryOutcome.Saved
    }

    /** Deletes an unused category, or archives it if it has transactions / recurring rules so no history is lost. */
    suspend fun removeCategory(id: Long): CategoryOutcome {
        val existing = categoryDao.getById(id) ?: return CategoryOutcome.Invalid("Category not found.")
        val inUse = transactionDao.countForCategory(id) > 0 || recurringDao.countForCategory(id) > 0
        return if (inUse) {
            categoryDao.update(existing.copy(isArchived = true))
            CategoryOutcome.Archived
        } else {
            db.withTransaction {
                budgetDao.deleteForCategory(id)
                categoryDao.deleteById(id)
            }
            CategoryOutcome.Deleted
        }
    }

    suspend fun restoreCategory(id: Long): CategoryOutcome {
        val existing = categoryDao.getById(id) ?: return CategoryOutcome.Invalid("Category not found.")
        val clash = categoryDao.getAll().any {
            it.id != id && it.type == existing.type && !it.isArchived && it.name.equals(existing.name, ignoreCase = true)
        }
        if (clash) return CategoryOutcome.Invalid("An active category with this name already exists. Rename one of them first.")
        categoryDao.update(existing.copy(isArchived = false))
        return CategoryOutcome.Saved
    }

    // ------------------------------------------------------------------ budgets & rates

    /** limitMinor == 0 clears the budget from [month] onward. Returns an error message or null. */
    suspend fun setBudget(month: YearMonth, categoryId: Long, limitMinor: Long, currencyCode: String): String? {
        if (limitMinor < 0) return "Budget cannot be negative."
        if (limitMinor > TransactionValidator.MAX_MINOR) return "Budget is too large."
        budgetDao.upsert(BudgetEntity(yearMonth = month.toString(), categoryId = categoryId, limitMinor = limitMinor, currencyCode = currencyCode))
        return null
    }

    suspend fun setRate(from: String, to: String, rateText: String): String? {
        val rate = RateTable.parseRate(rateText) ?: return "Enter a rate greater than zero, for example 3.6725."
        if (from == to) return "Choose two different currencies."
        rateDao.upsert(ExchangeRateEntity(from, to, rate.toPlainString()))
        return null
    }

    suspend fun clearRate(from: String, to: String) {
        rateDao.delete(from, to)
        rateDao.delete(to, from)
    }

    // ------------------------------------------------------------------ recurring

    suspend fun addRecurring(
        type: TransactionType, amountMinor: Long, currencyCode: String, categoryId: Long,
        description: String, notes: String, frequency: Frequency, start: LocalDate, end: LocalDate?
    ): SaveOutcome {
        val category = categoryDao.getById(categoryId)?.toDomain()
        val errors = TransactionValidator.validate(type, amountMinor, currencyCode, category, description.trim(), notes.trim(), start).toMutableList()
        if (end != null && end.isBefore(start)) errors.add("End date is before the start date.")
        if (errors.isNotEmpty()) return SaveOutcome.Invalid(errors)
        recurringDao.insert(
            RecurringRuleEntity(
                type = type, amountMinor = amountMinor, currencyCode = currencyCode, categoryId = categoryId,
                description = description.trim(), notes = notes.trim(), frequency = frequency,
                startEpochDay = start.toEpochDay(), endEpochDay = end?.toEpochDay(), lastGeneratedEpochDay = null, isActive = true
            )
        )
        return SaveOutcome.Saved
    }

    suspend fun setRecurringActive(id: Long, active: Boolean) {
        val rule = recurringDao.getAll().firstOrNull { it.id == id } ?: return
        recurringDao.update(rule.copy(isActive = active))
    }

    /** Stops the rule. Transactions it already created stay. */
    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    /**
     * Creates every recurring entry that is due on or before [today]. Safe to call repeatedly:
     * the unique (rule, date) index and the rule's lastGenerated date both prevent duplicates.
     */
    suspend fun runRecurring(today: LocalDate): Int {
        var generated = 0
        for (rule in recurringDao.getAll()) {
            if (!rule.isActive) continue
            val due = Recurring.dueDates(rule.toDomain(), today)
            if (due.isEmpty()) continue
            if (categoryDao.getById(rule.categoryId) == null) continue
            db.withTransaction {
                val now = System.currentTimeMillis()
                for (date in due) {
                    val rowId = transactionDao.insert(
                        TransactionEntity(
                            type = rule.type, amountMinor = rule.amountMinor, currencyCode = rule.currencyCode,
                            categoryId = rule.categoryId, description = rule.description, notes = rule.notes,
                            dateEpochDay = date.toEpochDay(), createdAt = now, updatedAt = now, recurringRuleId = rule.id
                        )
                    )
                    if (rowId != -1L) generated++
                }
                recurringDao.update(rule.copy(lastGeneratedEpochDay = due.last().toEpochDay()))
            }
        }
        return generated
    }

    // ------------------------------------------------------------------ backup

    suspend fun buildCsv(): String {
        val snap = snapshot.first()
        return TransactionCsv.export(snap.transactions, snap.categoryMap)
    }

    suspend fun buildBackupJson(): String = BackupJson.export(snapshot.first(), Instant.now().toString())

    suspend fun previewCsv(text: String): ImportPreview {
        val snap = snapshot.first()
        return preview(TransactionCsv.parse(text, snap.defaultCurrency.code), snap)
    }

    suspend fun previewJson(text: String): ImportPreview {
        val snap = snapshot.first()
        return preview(BackupJson.parse(text, snap.defaultCurrency.code), snap)
    }

    private fun preview(parsed: ParsedImport, snap: LedgerSnapshot): ImportPreview =
        if (parsed.fatalError != null) ImportPreview(parsed, null)
        else ImportPreview(parsed, ImportPlanner.plan(parsed.transactions, snap.transactions, snap.categories))

    /** Merges the previewed data into the database in one transaction: all of it or none of it. */
    suspend fun applyImport(preview: ImportPreview): ImportResult {
        val plan = preview.plan ?: return ImportResult(0, 0, 0, 0, 0, 0)
        val parsed = preview.parsed
        return db.withTransaction {
            val styles = parsed.categories.associateBy { it.type to it.name.trim().lowercase() }
            val byKey = HashMap<Pair<TransactionType, String>, Long>()
            for (c in categoryDao.getAll()) byKey[c.type to c.name.trim().lowercase()] = c.id

            val toCreate = LinkedHashMap<Pair<TransactionType, String>, String>()
            for (n in plan.newCategories) toCreate[n.type to n.name.trim().lowercase()] = n.name.trim()
            for (c in parsed.categories) {
                val key = c.type to c.name.trim().lowercase()
                if (key !in byKey && key !in toCreate) toCreate[key] = c.name.trim()
            }
            var sort = categoryDao.maxSortOrder()
            for ((key, displayName) in toCreate) {
                val style = styles[key]
                sort += 1
                byKey[key] = categoryDao.insert(
                    CategoryEntity(
                        name = displayName, type = key.first, iconKey = style?.iconKey ?: "category",
                        colorArgb = style?.colorArgb ?: 0xFF94A3B8.toInt(), isArchived = style?.isArchived ?: false, sortOrder = sort
                    )
                )
            }

            val now = System.currentTimeMillis()
            val entities = plan.toAdd.mapNotNull { r ->
                val categoryId = byKey[r.type to r.categoryName.trim().lowercase()] ?: return@mapNotNull null
                TransactionEntity(
                    type = r.type, amountMinor = r.amountMinor, currencyCode = r.currencyCode, categoryId = categoryId,
                    description = r.description, notes = r.notes, dateEpochDay = r.date.toEpochDay(), createdAt = now, updatedAt = now
                )
            }
            val inserted = transactionDao.insertAll(entities).count { it != -1L }

            var budgets = 0
            for (b in parsed.budgets) {
                val categoryId = if (b.categoryName == null) OVERALL_BUDGET_ID
                else byKey[TransactionType.EXPENSE to b.categoryName.trim().lowercase()] ?: continue
                budgetDao.upsert(BudgetEntity(yearMonth = b.yearMonth.toString(), categoryId = categoryId, limitMinor = b.limitMinor, currencyCode = b.currencyCode))
                budgets++
            }
            var rates = 0
            for (r in parsed.rates) {
                rateDao.upsert(ExchangeRateEntity(r.from, r.to, r.rate.toPlainString()))
                rates++
            }
            ImportResult(inserted, plan.duplicates.size, toCreate.size, budgets, rates, parsed.errors.size)
        }
    }

    fun currencyOptions(): List<AppCurrency> = AppCurrency.entries
}
