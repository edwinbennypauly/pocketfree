package app.pocketfree.di

import android.content.Context
import androidx.room.Room
import app.pocketfree.data.db.AppDatabase
import app.pocketfree.data.db.BudgetDao
import app.pocketfree.data.db.CategoryDao
import app.pocketfree.data.db.ExchangeRateDao
import app.pocketfree.data.db.RecurringDao
import app.pocketfree.data.db.SeedCallback
import app.pocketfree.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(SeedCallback)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(AppDatabase.LAST_REBUILDABLE_VERSION)
            .build()

    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideExchangeRateDao(db: AppDatabase): ExchangeRateDao = db.exchangeRateDao()
    @Provides fun provideRecurringDao(db: AppDatabase): RecurringDao = db.recurringDao()
}
