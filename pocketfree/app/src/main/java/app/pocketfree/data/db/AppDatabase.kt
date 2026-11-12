package app.pocketfree.data.db

import android.content.ContentValues
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pocketfree.domain.DefaultCategories

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        ExchangeRateEntity::class,
        RecurringRuleEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        const val NAME = "pocketfree.db"

        /**
         * Add an explicit Migration for every schema change from version 2 onward.
         * Version 1 was the empty pre-release foundation build (it had no way to store data),
         * so it is the only version allowed to be rebuilt instead of migrated.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
        const val LAST_REBUILDABLE_VERSION = 1
    }
}

/** Inserts the default categories whenever the database is created from scratch. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        seed(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        DefaultCategories.all.forEachIndexed { index, seed ->
            val values = ContentValues().apply {
                put("name", seed.name)
                put("type", seed.type.name)
                put("iconKey", seed.iconKey)
                put("colorArgb", seed.color)
                put("isArchived", 0)
                put("isDefault", 1)
                put("sortOrder", index)
            }
            db.insert("categories", 0, values)
        }
    }
}
