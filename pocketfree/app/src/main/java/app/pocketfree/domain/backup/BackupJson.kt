package app.pocketfree.domain.backup

import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.Money
import app.pocketfree.domain.OVERALL_BUDGET_ID
import app.pocketfree.domain.TransactionType
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeParseException

object BackupJson {
    const val FORMAT_VERSION = 1

    fun export(snapshot: LedgerSnapshot, exportedAt: String): String {
        val categories = snapshot.categories.map {
            mapOf(
                "name" to it.name,
                "type" to it.type.name,
                "iconKey" to it.iconKey,
                "colorArgb" to it.colorArgb.toLong(),
                "archived" to it.isArchived
            )
        }
        val transactions = snapshot.transactions
            .sortedWith(compareBy({ it.date }, { it.id }))
            .mapNotNull { t ->
                val category = snapshot.categoryMap[t.categoryId] ?: return@mapNotNull null
                mapOf(
                    "date" to t.date.toString(),
                    "type" to t.type.name,
                    "amountMinor" to t.amountMinor,
                    "currency" to t.currencyCode,
                    "category" to category.name,
                    "description" to t.description,
                    "notes" to t.notes
                )
            }
        val budgets = snapshot.budgets.mapNotNull { b ->
            val categoryName: String? = if (b.categoryId == OVERALL_BUDGET_ID) null
            else snapshot.categoryMap[b.categoryId]?.name ?: return@mapNotNull null
            mapOf(
                "yearMonth" to b.yearMonth.toString(),
                "category" to categoryName,
                "limitMinor" to b.limitMinor,
                "currency" to b.currencyCode
            )
        }
        val rates = ArrayList<Map<String, Any?>>()
        for (from in AppCurrency.entries) for (to in AppCurrency.entries) {
            if (from == to) continue
            val rate = snapshot.rates.rateOrNull(from.code, to.code) ?: continue
            rates.add(mapOf("from" to from.code, "to" to to.code, "rate" to rate.toPlainString()))
        }
        val root = linkedMapOf<String, Any?>(
            "app" to "PocketFree",
            "version" to FORMAT_VERSION,
            "exportedAt" to exportedAt,
            "defaultCurrency" to snapshot.defaultCurrency.code,
            "categories" to categories,
            "transactions" to transactions,
            "budgets" to budgets,
            "exchangeRates" to rates
        )
        return Json.write(root)
    }

    fun parse(text: String, defaultCurrency: String): ParsedImport {
        val root = try {
            Json.parse(text)
        } catch (e: JsonException) {
            return ParsedImport(fatalError = "This is not a valid backup file: ${e.message}")
        }
        if (root !is Map<*, *>) return ParsedImport(fatalError = "This is not a PocketFree backup (top level is not an object).")
        val version = (root["version"] as? Long)
        if (version == null) return ParsedImport(fatalError = "This file has no backup version, so it is probably not a PocketFree backup.")
        if (version != FORMAT_VERSION.toLong()) {
            return ParsedImport(fatalError = "Backup version $version is not supported by this app (supports version $FORMAT_VERSION).")
        }

        val errors = ArrayList<String>()
        val categories = ArrayList<BackupCategory>()
        for ((i, item) in list(root["categories"]).withIndex()) {
            val m = item as? Map<*, *>
            if (m == null) { errors.add("Category ${i + 1}: not an object."); continue }
            val name = (m["name"] as? String)?.trim().orEmpty()
            val type = parseType(m["type"] as? String)
            val color = (m["colorArgb"] as? Long)?.toInt() ?: 0xFF94A3B8.toInt()
            if (name.isEmpty() || type == null) { errors.add("Category ${i + 1}: needs a name and a type (EXPENSE, INCOME, INVESTMENT or WITHDRAWAL)."); continue }
            categories.add(BackupCategory(name, type, (m["iconKey"] as? String) ?: "category", color, (m["archived"] as? Boolean) ?: false))
        }

        val transactions = ArrayList<ImportTransaction>()
        for ((i, item) in list(root["transactions"]).withIndex()) {
            val m = item as? Map<*, *>
            if (m == null) { errors.add("Transaction ${i + 1}: not an object."); continue }
            try {
                transactions.add(
                    ImportValidator.build(
                        rawDate = (m["date"] as? String).orEmpty(),
                        rawType = (m["type"] as? String).orEmpty(),
                        amountMinor = m["amountMinor"] as? Long,
                        rawCurrency = (m["currency"] as? String).orEmpty(),
                        category = (m["category"] as? String).orEmpty(),
                        description = (m["description"] as? String).orEmpty(),
                        notes = (m["notes"] as? String).orEmpty(),
                        defaultCurrency = defaultCurrency
                    )
                )
            } catch (e: IllegalArgumentException) {
                errors.add("Transaction ${i + 1}: ${e.message}")
            }
        }

        val budgets = ArrayList<BackupBudget>()
        for ((i, item) in list(root["budgets"]).withIndex()) {
            val m = item as? Map<*, *>
            val ym = try { YearMonth.parse((m?.get("yearMonth") as? String).orEmpty()) } catch (e: DateTimeParseException) { null }
            val limit = m?.get("limitMinor") as? Long
            val currency = (m?.get("currency") as? String).orEmpty()
            if (m == null || ym == null || limit == null || limit < 0 || AppCurrency.entries.none { it.code == currency }) {
                errors.add("Budget ${i + 1}: invalid month, limit or currency.")
                continue
            }
            budgets.add(BackupBudget(ym, (m["category"] as? String)?.trim()?.ifEmpty { null }, limit, currency))
        }

        val rates = ArrayList<BackupRate>()
        for ((i, item) in list(root["exchangeRates"]).withIndex()) {
            val m = item as? Map<*, *>
            val from = (m?.get("from") as? String).orEmpty()
            val to = (m?.get("to") as? String).orEmpty()
            val rate = (m?.get("rate") as? String)?.let { app.pocketfree.domain.RateTable.parseRate(it) }
            val known = AppCurrency.entries.map { it.code }
            if (m == null || rate == null || from !in known || to !in known || from == to) {
                errors.add("Exchange rate ${i + 1}: invalid.")
                continue
            }
            rates.add(BackupRate(from, to, rate))
        }
        return ParsedImport(transactions, categories, budgets, rates, errors)
    }

    private fun list(value: Any?): List<Any?> = (value as? List<*>) ?: emptyList<Any?>()

    private fun parseType(raw: String?): TransactionType? = when (raw?.trim()?.uppercase()) {
        "EXPENSE" -> TransactionType.EXPENSE
        "INCOME" -> TransactionType.INCOME
        "INVESTMENT" -> TransactionType.INVESTMENT
        "WITHDRAWAL" -> TransactionType.WITHDRAWAL
        else -> null
    }
}
