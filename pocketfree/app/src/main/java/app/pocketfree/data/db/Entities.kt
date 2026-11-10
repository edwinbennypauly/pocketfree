package app.pocketfree.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.pocketfree.domain.TransactionType

@Entity(tableName = "categories", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorArgb: Int,
    val isArchived: Boolean = false,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)

/**
 * Money is stored as [amountMinor] (fils/paise/cents) in [currencyCode].
 * Categories are referenced by id with RESTRICT, so a category with history can only be archived.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("dateEpochDay")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: Long,
    val description: String,
    val notes: String = "",
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * [categoryId] == 0 means the overall monthly budget (no foreign key on purpose:
 * SQLite treats NULLs as distinct in unique indexes, which would allow duplicates).
 * [yearMonth] is "yyyy-MM".
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["yearMonth", "categoryId"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String,
    val categoryId: Long,
    val limitMinor: Long,
    val currencyCode: String
)

/** Manual exchange rate; [rate] is a decimal string to avoid floating point. */
@Entity(tableName = "exchange_rates", primaryKeys = ["fromCode", "toCode"])
data class ExchangeRateEntity(
    val fromCode: String,
    val toCode: String,
    val rate: String
)
