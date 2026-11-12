package app.pocketfree.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.pocketfree.domain.Frequency
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
 * The unique (recurringRuleId, dateEpochDay) index makes it impossible to generate the same recurring entry twice.
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
    indices = [
        Index("categoryId"),
        Index("dateEpochDay"),
        Index(value = ["recurringRuleId", "dateEpochDay"], unique = true)
    ]
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
    val updatedAt: Long,
    val recurringRuleId: Long? = null
)

/** categoryId == 0 is the overall monthly budget. yearMonth is "yyyy-MM". limitMinor 0 means cleared. */
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

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: Long,
    val description: String,
    val notes: String,
    val frequency: Frequency,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val lastGeneratedEpochDay: Long?,
    val isActive: Boolean
)
