package com.lpms.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Deferred mutations. Insert order is replay order; nothing here is ever
 * removed by anything but a successful (or explicitly discarded) sync.
 */
@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Delete
    suspend fun delete(item: OutboxEntity)

    @Upsert
    suspend fun update(item: OutboxEntity)

    /** Oldest unsent mutation; the FIFO head the drain loop consumes. */
    @Query("SELECT * FROM outbox ORDER BY id ASC LIMIT 1")
    suspend fun head(): OutboxEntity?

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT COUNT(*) FROM outbox WHERE blocked = 0")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE entityLocalId = :entityId")
    suspend fun deleteByEntityId(entityId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE blocked = 1")
    fun observeBlockedCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE blocked = 1 ORDER BY id ASC")
    suspend fun blocked(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface DrugDao {
    @Upsert
    suspend fun upsertAll(drugs: List<DrugEntity>)

    @Upsert
    suspend fun upsert(drug: DrugEntity)

    @Delete
    suspend fun delete(drug: DrugEntity)

    /**
     * Catalog for the list and POS screens. An empty [search] matches
     * everything; otherwise it is matched against name, generic name and
     * barcode.
     */
    @Query(
        """
        SELECT * FROM drugs
        WHERE deleted = 0
          AND (:search = '' OR name LIKE '%' || :search || '%'
               OR genericName LIKE '%' || :search || '%'
               OR barcode LIKE '%' || :search || '%')
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeCatalog(search: String): Flow<List<DrugEntity>>

    @Query(
        """
        SELECT * FROM drugs
        WHERE deleted = 0 AND (:includeInactive = 1 OR active = 1)
          AND (:search = '' OR name LIKE '%' || :search || '%'
               OR genericName LIKE '%' || :search || '%'
               OR barcode LIKE '%' || :search || '%')
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeSellable(search: String, includeInactive: Boolean): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drugs WHERE localId = :localId LIMIT 1")
    suspend fun byLocalId(localId: String): DrugEntity?

    @Query("SELECT * FROM drugs WHERE serverId = :serverId LIMIT 1")
    suspend fun byServerId(serverId: Long): DrugEntity?

    @Query("SELECT * FROM drugs WHERE barcode = :barcode AND deleted = 0 LIMIT 1")
    suspend fun byBarcode(barcode: String): DrugEntity?

    @Query("SELECT * FROM drugs WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(): List<DrugEntity>

    @Query("SELECT COUNT(*) FROM drugs WHERE deleted = 0")
    suspend fun count(): Int

    /**
     * Optimistic stock decrement applied the instant an offline sale is
     * recorded. A catalog refresh overwrites it once the sale has synced.
     */
    @Query(
        "UPDATE drugs SET currentQuantity = currentQuantity - :quantity WHERE localId = :localId",
    )
    suspend fun decrementStock(localId: String, quantity: Int)

    @Query("UPDATE drugs SET currentQuantity = currentQuantity + :quantity WHERE localId = :localId")
    suspend fun incrementStock(localId: String, quantity: Int)

    /** Called once the server has accepted a locally created/edited drug. */
    @Query("UPDATE drugs SET serverId = :serverId, needsSync = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: Long)

    @Query("DELETE FROM drugs WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM drugs WHERE serverId IS NULL")
    suspend fun deleteUnsynced()

    @Query("DELETE FROM drugs")
    suspend fun wipe()
}

@Dao
interface CustomerDao {
    @Upsert
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Upsert
    suspend fun upsert(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query(
        """
        SELECT * FROM customers
        WHERE deleted = 0
          AND (:search = '' OR name LIKE '%' || :search || '%'
               OR phone LIKE '%' || :search || '%')
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeCatalog(search: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE localId = :localId LIMIT 1")
    suspend fun byLocalId(localId: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE serverId = :serverId LIMIT 1")
    suspend fun byServerId(serverId: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(): List<CustomerEntity>

    @Query("UPDATE customers SET serverId = :serverId, needsSync = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: Long)

    @Query(
        "UPDATE customers SET totalDebt = :debt WHERE localId = :localId",
    )
    suspend fun updateDebt(localId: String, debt: Double)

    @Query("DELETE FROM customers WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM customers")
    suspend fun wipe()
}

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Upsert
    suspend fun update(sale: SaleEntity)

    @Query("SELECT * FROM sales ORDER BY createdAt DESC, rowid DESC")
    fun observeAll(): Flow<List<SaleEntity>>

    /** Sales recorded at or after [since]; used for the dashboard's live day totals. */
    @Query("SELECT * FROM sales WHERE createdAt >= :since ORDER BY createdAt ASC")
    fun observeSince(since: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE serverId IS NULL ORDER BY createdAt ASC")
    fun observePending(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE localId = :localId LIMIT 1")
    suspend fun byLocalId(localId: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE serverId = :serverId LIMIT 1")
    suspend fun byServerId(serverId: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleLocalId = :saleLocalId")
    fun observeItems(saleLocalId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE saleLocalId = :saleLocalId")
    suspend fun items(saleLocalId: String): List<SaleItemEntity>

    @Query(
        "UPDATE sales SET serverId = :serverId, needsSync = 0, status = :status WHERE localId = :localId",
    )
    suspend fun markSynced(localId: String, serverId: Long, status: String)

    @Query(
        """
        UPDATE sales SET status = :status, refundedTotal = :refundedTotal,
                         amountDue = :amountDue, serverId = COALESCE(serverId, :serverId)
        WHERE localId = :localId
        """,
    )
    suspend fun applyRefund(
        localId: String,
        status: String,
        refundedTotal: Double,
        amountDue: Double,
        serverId: Long,
    )

    @Query("DELETE FROM sale_items WHERE saleLocalId = :saleLocalId")
    suspend fun clearItems(saleLocalId: String)

    @Query("DELETE FROM sales WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM sales")
    suspend fun wipe()
}

@Dao
interface SettingDao {
    @Upsert
    suspend fun upsertAll(settings: List<SettingEntity>)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun byKey(key: String): SettingEntity?

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Query("DELETE FROM settings")
    suspend fun wipe()
}

@Dao
interface CacheDao {
    @Upsert
    suspend fun put(entry: CacheEntity)

    @Query("SELECT * FROM cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CacheEntity?

    @Query("SELECT * FROM cache WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<CacheEntity?>

    @Query("DELETE FROM cache")
    suspend fun wipe()
}
