package app.pocketfree.data.db

import android.content.ContentValues
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        ExchangeRateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "pocketfree.db"

        /** Add an explicit Migration for every schema bump. Destructive fallback is deliberately NOT enabled. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}

/** Inserts the default categories the first time the database file is created. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
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
