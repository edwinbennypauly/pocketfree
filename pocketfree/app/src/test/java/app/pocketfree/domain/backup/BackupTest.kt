package app.pocketfree.domain.backup

import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Budget
import app.pocketfree.domain.Category
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.OVERALL_BUDGET_ID
import app.pocketfree.domain.TestData
import app.pocketfree.domain.TestData.aed
import app.pocketfree.domain.TestData.categories
import app.pocketfree.domain.TestData.expense
import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.income
import app.pocketfree.domain.TestData.rent
import app.pocketfree.domain.Transaction
import app.pocketfree.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val sample: List<Transaction> = listOf(
        expense(1_250, "2026-11-10", food, "Lunch, \"Zaatar\"", id = 1),
        expense(300_000, "2026-11-01", rent, "November rent", id = 2),
        income(1_000_000, "2026-10-28", description = "=SUM(A1)", id = 3),
        TestData.tx(TransactionType.EXPENSE, 999, "2026-11-11", food, "Café ☕\nsecond line", "USD", "a note, with comma", id = 4)
    )

    // ---------- CSV ----------
    @Test fun csvExportThenImportGivesBackTheSameTransactions() {
        val csv = TransactionCsv.export(sample, TestData.categoryMap)
        val parsed = TransactionCsv.parse(csv, "AED")
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty()); assertNull(parsed.fatalError)
        assertEquals(4, parsed.transactions.size)
        val byDesc = parsed.transactions.associateBy { it.description }
        assertEquals(1_250L, byDesc["Lunch, \"Zaatar\""]!!.amountMinor)
        assertEquals("=SUM(A1)", byDesc["=SUM(A1)"]!!.description)
        assertEquals("USD", byDesc["Café ☕\nsecond line"]!!.currencyCode)
        assertEquals("a note, with comma", byDesc["Café ☕\nsecond line"]!!.notes)
    }

    @Test fun csvExportNeutralisesFormulasForSpreadsheets() {
        val csv = TransactionCsv.export(listOf(income(100, "2026-11-01", description = "=HYPERLINK(\"x\")", id = 1)), TestData.categoryMap)
        assertTrue(csv.contains("'=HYPERLINK"))
    }

    @Test fun csvColumnsMayBeInAnyOrderAndCurrencyIsOptional() {
        val p = TransactionCsv.parse("Category,Amount,Type,Date\nFood,12.5,Expense,2026-11-01\n", "SAR")
        assertEquals(1, p.transactions.size); assertEquals("SAR", p.transactions[0].currencyCode); assertEquals(1250L, p.transactions[0].amountMinor)
    }

    @Test fun csvInvalidRowsGetMeaningfulErrorsAndGoodRowsStillImport() {
        val csv = "date,type,amount,category,description\n" +
            "2026-11-01,expense,10,Food,ok\n" +
            "01/11/2026,expense,10,Food,bad date\n" +
            "2026-11-02,spend,10,Food,bad type\n" +
            "2026-11-03,expense,abc,Food,bad amount\n" +
            "2026-11-04,expense,-5,Food,negative\n" +
            "2026-11-05,expense,0,Food,zero\n" +
            "2026-11-06,expense,5,,no category\n" +
            "2026-11-07,expense,5,Food,\n"
        val p = TransactionCsv.parse(csv, "AED")
        assertEquals(2, p.transactions.size)
        assertEquals(6, p.errors.size)
        assertTrue(p.errors[0].startsWith("Row 2:") && p.errors[0].contains("yyyy-MM-dd"))
        assertTrue(p.errors[1].contains("expense, income")); assertTrue(p.errors[3].contains("positive")); assertTrue(p.errors[4].contains("greater than zero"))
    }

    @Test fun csvWithMissingRequiredColumnsIsRejectedUpFront() {
        val p = TransactionCsv.parse("when,what\n1,2\n", "AED")
        assertNotNull(p.fatalError); assertTrue(p.fatalError!!.contains("date")); assertTrue(p.transactions.isEmpty())
        assertNotNull(TransactionCsv.parse("", "AED").fatalError)
        assertNotNull(TransactionCsv.parse("date,type,amount,category\n\"unclosed", "AED").fatalError)
    }

    @Test fun csvUnsupportedCurrencyAndHugeAmountAreRejected() {
        val p = TransactionCsv.parse("date,type,amount,currency,category\n2026-11-01,expense,5,XYZ,Food\n2026-11-01,expense,99999999999999999,AED,Food\n", "AED")
        assertEquals(0, p.transactions.size); assertEquals(2, p.errors.size)
    }

    // ---------- duplicates ----------
    private fun rec(date: String, minor: Long, cat: String = "Food", desc: String = "", type: TransactionType = TransactionType.EXPENSE) =
        ImportTransaction(LocalDate.parse(date), type, minor, "AED", cat, desc, "")

    @Test fun reImportingTheSameFileAddsNothing() {
        val existing = sample.take(3)
        val records = TransactionCsv.parse(TransactionCsv.export(existing, TestData.categoryMap), "AED").transactions
        val plan = ImportPlanner.plan(records, existing, categories)
        assertEquals(0, plan.toAdd.size); assertEquals(3, plan.duplicates.size); assertTrue(plan.newCategories.isEmpty())
    }

    @Test fun duplicatesAreMatchedByCountSoRepeatPurchasesSurvive() {
        val existing = listOf(expense(500, "2026-11-01", food, "Coffee", id = 1))
        val plan = ImportPlanner.plan(listOf(rec("2026-11-01", 500, desc = "Coffee"), rec("2026-11-01", 500, desc = "Coffee")), existing, categories)
        assertEquals(1, plan.toAdd.size); assertEquals(1, plan.duplicates.size)
        val fresh = ImportPlanner.plan(listOf(rec("2026-11-01", 500, desc = "Coffee"), rec("2026-11-01", 500, desc = "Coffee")), emptyList(), categories)
        assertEquals(2, fresh.toAdd.size)
    }

    @Test fun duplicateMatchingIgnoresCaseAndSpaces() {
        val plan = ImportPlanner.plan(listOf(rec("2026-11-01", 500, "FOOD ", "  coffee")), listOf(expense(500, "2026-11-01", food, "Coffee", id = 1)), categories)
        assertEquals(1, plan.duplicates.size)
    }

    @Test fun sameCategoryNameInOtherTypeIsNotTheSameCategory() {
        val plan = ImportPlanner.plan(listOf(rec("2026-11-01", 500, "Salary", type = TransactionType.EXPENSE)), emptyList(), categories)
        assertEquals(listOf(NewCategory("Salary", TransactionType.EXPENSE)), plan.newCategories)
    }

    @Test fun unknownCategoriesAreListedOnceForCreation() {
        val plan = ImportPlanner.plan(listOf(rec("2026-11-01", 1, "Pets"), rec("2026-11-02", 2, "pets"), rec("2026-11-03", 3, "Food")), emptyList(), categories)
        assertEquals(listOf(NewCategory("Pets", TransactionType.EXPENSE)), plan.newCategories)
    }

    // ---------- JSON backup ----------
    private fun snapshot() = LedgerSnapshot(
        categories = categories + Category(9, "Old stuff", TransactionType.EXPENSE, "movie", 0x11223344, true),
        transactions = sample,
        budgets = listOf(Budget(YearMonth.of(2026, 11), OVERALL_BUDGET_ID, 300_000, "AED"), Budget(YearMonth.of(2026, 11), 1, 60_000, "AED")),
        recurring = emptyList(),
        rates = TestData.rates(AppCurrency.AED, Triple("USD", "AED", "3.6725")),
        defaultCurrency = AppCurrency.AED
    )

    @Test fun fullBackupRoundTrip() {
        val json = BackupJson.export(snapshot(), "2026-11-11T10:00:00Z")
        val p = BackupJson.parse(json, "AED")
        assertNull(p.fatalError); assertTrue(p.errors.toString(), p.errors.isEmpty())
        assertEquals(4, p.transactions.size); assertEquals(4, p.categories.size)
        assertEquals(0x11223344, p.categories.first { it.name == "Old stuff" }.colorArgb); assertTrue(p.categories.first { it.name == "Old stuff" }.isArchived)
        assertEquals(2, p.budgets.size); assertNull(p.budgets.first { it.limitMinor == 300_000L }.categoryName); assertEquals("Food", p.budgets.first { it.limitMinor == 60_000L }.categoryName)
        assertEquals(1, p.rates.size); assertEquals("3.6725", p.rates[0].rate.toPlainString())
        assertEquals("Café ☕\nsecond line", p.transactions.first { it.currencyCode == "USD" }.description)
    }

    @Test fun backupImportedTwiceIsAllDuplicatesTheSecondTime() {
        val p = BackupJson.parse(BackupJson.export(snapshot(), "t"), "AED")
        val plan = ImportPlanner.plan(p.transactions, sample, categories)
        assertEquals(0, plan.toAdd.size); assertEquals(4, plan.duplicates.size)
    }

    @Test fun garbageAndWrongFilesAreRefusedWithAReason() {
        assertTrue(BackupJson.parse("not json at all", "AED").fatalError!!.contains("not a valid backup"))
        assertTrue(BackupJson.parse("[1,2]", "AED").fatalError!!.contains("not a PocketFree backup"))
        assertTrue(BackupJson.parse("{\"transactions\":[]}", "AED").fatalError!!.contains("version"))
        assertTrue(BackupJson.parse("{\"version\":99}", "AED").fatalError!!.contains("not supported"))
    }

    @Test fun badRecordsInsideABackupAreReportedNotSilentlyDropped() {
        val json = """{"version":1,"categories":[{"name":"","type":"EXPENSE"}],
            "transactions":[{"date":"2026-11-01","type":"EXPENSE","amountMinor":500,"currency":"AED","category":"Food"},
                            {"date":"nope","type":"EXPENSE","amountMinor":500,"category":"Food"},
                            {"date":"2026-11-01","type":"EXPENSE","amountMinor":"500","category":"Food"},
                            {"date":"2026-11-01","type":"EXPENSE","amountMinor":-1,"category":"Food"}],
            "budgets":[{"yearMonth":"2026-13","limitMinor":1,"currency":"AED"}],
            "exchangeRates":[{"from":"USD","to":"USD","rate":"1"}]}"""
        val p = BackupJson.parse(json, "AED")
        assertEquals(1, p.transactions.size); assertEquals(1 + 3 + 1 + 1, p.errors.size)
        assertTrue(p.errors.any { it.startsWith("Transaction 2:") })
    }

    @Test fun emptyBackupIsValid() {
        val p = BackupJson.parse(BackupJson.export(LedgerSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), aed, AppCurrency.AED), "t"), "AED")
        assertNull(p.fatalError); assertTrue(p.transactions.isEmpty()); assertFalse(p.errors.isNotEmpty())
    }
}

class InvestmentBackupTest {
    private val sample = listOf(
        TestData.invest(300_000, "2026-11-10", id = 1),
        TestData.withdraw(50_000, "2026-11-20", id = 2),
        TestData.income(20_000, "2026-11-25", TestData.returns, "Dividend", id = 3),
        TestData.expense(1_250, "2026-11-10", TestData.food, "Lunch", id = 4)
    )
    private val cats = (TestData.categories + TestData.stocks + TestData.stocksOut + TestData.returns)

    @Test fun csvRoundTripKeepsInvestmentAndWithdrawalTypes() {
        val csv = TransactionCsv.export(sample, cats.associateBy { it.id })
        assertTrue(csv.contains(",investment,")); assertTrue(csv.contains(",withdrawal,"))
        val p = TransactionCsv.parse(csv, "AED")
        assertTrue(p.errors.toString(), p.errors.isEmpty())
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INVESTMENT, TransactionType.WITHDRAWAL, TransactionType.INCOME).toSet(), p.transactions.map { it.type }.toSet())
        assertEquals(300_000L, p.transactions.first { it.type == TransactionType.INVESTMENT }.amountMinor)
    }

    @Test fun csvAcceptsFriendlyTypeNames() {
        val p = TransactionCsv.parse("date,type,amount,category\n2026-11-01,Invest,100,Stocks\n2026-11-02,withdraw,50,Stocks\n", "AED")
        assertEquals(listOf(TransactionType.INVESTMENT, TransactionType.WITHDRAWAL), p.transactions.map { it.type })
    }

    @Test fun reImportedInvestmentsAreDuplicatesAndSameNamedCategoriesStaySeparatePerType() {
        val csv = TransactionCsv.export(sample, cats.associateBy { it.id })
        val records = TransactionCsv.parse(csv, "AED").transactions
        val plan = ImportPlanner.plan(records, sample, cats)
        assertEquals(0, plan.toAdd.size); assertEquals(4, plan.duplicates.size)
        val fresh = ImportPlanner.plan(records, emptyList(), TestData.categories)
        assertEquals(setOf(NewCategory("Stocks", TransactionType.INVESTMENT), NewCategory("Stocks", TransactionType.WITHDRAWAL), NewCategory("Investment returns", TransactionType.INCOME)), fresh.newCategories.toSet())
    }

    @Test fun jsonBackupRoundTripsInvestmentCategoriesAndTransactions() {
        val snap = LedgerSnapshot(cats, sample, emptyList(), emptyList(), TestData.aed, AppCurrency.AED)
        val p = BackupJson.parse(BackupJson.export(snap, "t"), "AED")
        assertNull(p.fatalError); assertTrue(p.errors.toString(), p.errors.isEmpty())
        assertEquals(4, p.transactions.size)
        assertEquals(2, p.categories.count { it.name == "Stocks" })
        assertTrue(p.categories.any { it.type == TransactionType.WITHDRAWAL })
    }

    @Test fun oldBackupsWithOnlyTwoTypesStillLoad() {
        val json = """{"version":1,"categories":[{"name":"Food","type":"EXPENSE"}],
            "transactions":[{"date":"2026-11-01","type":"EXPENSE","amountMinor":500,"currency":"AED","category":"Food"}]}"""
        val p = BackupJson.parse(json, "AED")
        assertEquals(1, p.transactions.size); assertTrue(p.errors.isEmpty())
    }
}
