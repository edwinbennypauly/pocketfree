package app.pocketfree.domain.backup

import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Category
import app.pocketfree.domain.Money
import app.pocketfree.domain.Transaction
import app.pocketfree.domain.TransactionType
import app.pocketfree.domain.TransactionValidator
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

data class ImportTransaction(
    val date: LocalDate,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryName: String,
    val description: String,
    val notes: String
)

data class BackupCategory(
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorArgb: Int,
    val isArchived: Boolean
)

data class BackupBudget(val yearMonth: YearMonth, val categoryName: String?, val limitMinor: Long, val currencyCode: String)

data class BackupRate(val from: String, val to: String, val rate: BigDecimal)

/** What a file contains after parsing and validation. Invalid records are reported in [errors] and left out. */
data class ParsedImport(
    val transactions: List<ImportTransaction> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val rates: List<BackupRate> = emptyList(),
    val errors: List<String> = emptyList(),
    val fatalError: String? = null
)

data class NewCategory(val name: String, val type: TransactionType)

data class ImportPlan(
    val toAdd: List<ImportTransaction>,
    val duplicates: List<ImportTransaction>,
    val newCategories: List<NewCategory>
)

object ImportValidator {
    private const val MAX_CATEGORY_NAME = 60

    /** Builds a valid [ImportTransaction] or throws IllegalArgumentException with a readable reason. */
    fun build(
        rawDate: String,
        rawType: String,
        amountMinor: Long?,
        rawCurrency: String,
        category: String,
        description: String,
        notes: String,
        defaultCurrency: String
    ): ImportTransaction {
        val date = try {
            LocalDate.parse(rawDate.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Date '${rawDate.trim()}' is not in yyyy-MM-dd format.")
        }
        if (date.isBefore(TransactionValidator.MIN_DATE) || date.isAfter(TransactionValidator.MAX_DATE)) {
            throw IllegalArgumentException("Date $date is outside 2000-2100.")
        }
        val type = when (rawType.trim().lowercase()) {
            "expense" -> TransactionType.EXPENSE
            "income" -> TransactionType.INCOME
            else -> throw IllegalArgumentException("Type '${rawType.trim()}' must be expense or income.")
        }
        if (amountMinor == null) throw IllegalArgumentException("Amount is missing or not a valid positive number.")
        if (amountMinor <= 0) throw IllegalArgumentException("Amount must be greater than zero.")
        if (amountMinor > TransactionValidator.MAX_MINOR) throw IllegalArgumentException("Amount is too large.")
        val currency = rawCurrency.trim().uppercase().ifEmpty { defaultCurrency }
        if (AppCurrency.entries.none { it.code == currency }) {
            throw IllegalArgumentException("Currency '$currency' is not supported.")
        }
        val categoryName = category.trim()
        if (categoryName.isEmpty()) throw IllegalArgumentException("Category is missing.")
        if (categoryName.length > MAX_CATEGORY_NAME) throw IllegalArgumentException("Category name is too long.")
        if (description.length > TransactionValidator.MAX_DESCRIPTION) throw IllegalArgumentException("Description is too long.")
        if (notes.length > TransactionValidator.MAX_NOTES) throw IllegalArgumentException("Notes are too long.")
        return ImportTransaction(date, type, amountMinor, currency, categoryName, description.trim(), notes.trim())
    }
}

object ImportPlanner {
    private fun key(date: LocalDate, type: TransactionType, amountMinor: Long, currency: String, category: String, description: String) =
        listOf(date.toString(), type.name, amountMinor.toString(), currency, category.trim().lowercase(), description.trim().lowercase())
            .joinToString("|")

    /**
     * Splits [records] into new ones and duplicates of what is already stored.
     * Duplicates are matched by count: if the database already holds 1 such transaction and the file has 2,
     * one is a duplicate and one is new, so genuine repeat purchases are never lost.
     */
    fun plan(records: List<ImportTransaction>, existing: List<Transaction>, categories: List<Category>): ImportPlan {
        val categoryById = categories.associateBy { it.id }
        val remaining = HashMap<String, Int>()
        for (t in existing) {
            val name = categoryById[t.categoryId]?.name ?: continue
            val k = key(t.date, t.type, t.amountMinor, t.currencyCode, name, t.description)
            remaining[k] = (remaining[k] ?: 0) + 1
        }
        val toAdd = ArrayList<ImportTransaction>()
        val duplicates = ArrayList<ImportTransaction>()
        for (r in records) {
            val k = key(r.date, r.type, r.amountMinor, r.currencyCode, r.categoryName, r.description)
            val left = remaining[k] ?: 0
            if (left > 0) {
                remaining[k] = left - 1
                duplicates.add(r)
            } else toAdd.add(r)
        }
        val known = categories.map { it.type to it.name.trim().lowercase() }.toSet()
        val newCategories = LinkedHashMap<Pair<TransactionType, String>, NewCategory>()
        for (r in toAdd) {
            val k = r.type to r.categoryName.trim().lowercase()
            if (k !in known && k !in newCategories) newCategories[k] = NewCategory(r.categoryName.trim(), r.type)
        }
        return ImportPlan(toAdd, duplicates, newCategories.values.toList())
    }
}

object TransactionCsv {
    val HEADER = listOf("date", "type", "amount", "currency", "category", "description", "notes")

    fun export(txs: List<Transaction>, categories: Map<Long, Category>): String {
        val rows = ArrayList<List<String>>()
        rows.add(HEADER)
        for (t in txs.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })) {
            rows.add(
                listOf(
                    t.date.toString(),
                    t.type.name.lowercase(),
                    Money.toDecimal(t.amountMinor).toPlainString(),
                    t.currencyCode,
                    safe(categories[t.categoryId]?.name ?: "Unknown"),
                    safe(t.description),
                    safe(t.notes)
                )
            )
        }
        return Csv.write(rows)
    }

    /** Stops spreadsheet apps from running text such as =HYPERLINK(...) as a formula. */
    private fun safe(text: String): String =
        if (text.isNotEmpty() && text[0] in "=+-@\t") "'$text" else text

    private fun unsafe(text: String): String =
        if (text.length > 1 && text[0] == '\'' && text[1] in "=+-@\t") text.substring(1) else text

    fun parse(text: String, defaultCurrency: String): ParsedImport {
        val rows = try {
            Csv.parse(text)
        } catch (e: CsvException) {
            return ParsedImport(fatalError = e.message)
        }
        if (rows.isEmpty()) return ParsedImport(fatalError = "The file is empty.")
        val header = rows[0].map { it.trim().lowercase() }
        val required = listOf("date", "type", "amount", "category")
        val missing = required.filter { it !in header }
        if (missing.isNotEmpty()) {
            return ParsedImport(
                fatalError = "Missing column(s): ${missing.joinToString(", ")}. Expected a header like: ${HEADER.joinToString(",")}"
            )
        }
        fun col(row: List<String>, name: String): String {
            val i = header.indexOf(name)
            return if (i >= 0 && i < row.size) row[i] else ""
        }
        val records = ArrayList<ImportTransaction>()
        val errors = ArrayList<String>()
        for ((index, row) in rows.drop(1).withIndex()) {
            val rowNumber = index + 1
            try {
                val amountText = col(row, "amount")
                if (amountText.trim().startsWith("-")) {
                    throw IllegalArgumentException("Amount must be positive; use the type column for expense or income.")
                }
                records.add(
                    ImportValidator.build(
                        rawDate = col(row, "date"),
                        rawType = col(row, "type"),
                        amountMinor = Money.parseToMinor(amountText),
                        rawCurrency = col(row, "currency"),
                        category = unsafe(col(row, "category")),
                        description = unsafe(col(row, "description")),
                        notes = unsafe(col(row, "notes")),
                        defaultCurrency = defaultCurrency
                    )
                )
            } catch (e: IllegalArgumentException) {
                errors.add("Row $rowNumber: ${e.message}")
            }
        }
        return ParsedImport(transactions = records, errors = errors)
    }
}
