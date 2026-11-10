package app.pocketfree.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY type, sortOrder, name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    fun observeCount(): Flow<Int>
}

@Dao
interface TransactionDao {
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>
}
