package com.lpms.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The offline source of truth.
 *
 * Everything the user can do — browse the catalog, ring up a sale, create a
 * drug or a customer — reads and writes this database first. The network layer
 * is a synchronisation detail layered on top, never a prerequisite.
 */
@Database(
    entities = [
        OutboxEntity::class,
        DrugEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SettingEntity::class,
        CacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LpmsDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun drugDao(): DrugDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun settingDao(): SettingDao
    abstract fun cacheDao(): CacheDao
}

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LpmsDatabase =
        Room.databaseBuilder(context, LpmsDatabase::class.java, "lpms.db").build()

    @Provides
    fun provideOutboxDao(db: LpmsDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideDrugDao(db: LpmsDatabase): DrugDao = db.drugDao()

    @Provides
    fun provideCustomerDao(db: LpmsDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideSaleDao(db: LpmsDatabase): SaleDao = db.saleDao()

    @Provides
    fun provideSettingDao(db: LpmsDatabase): SettingDao = db.settingDao()

    @Provides
    fun provideCacheDao(db: LpmsDatabase): CacheDao = db.cacheDao()
}
