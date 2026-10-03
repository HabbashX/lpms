package com.lpms.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Operations queued while offline ─────────────────────────────────────────

/**
 * Kinds of mutation the app can defer. Stored as `name()` in [OutboxEntity].
 */
enum class SyncOperation {
    CREATE_DRUG,
    UPDATE_DRUG,
    DELETE_DRUG,
    CREATE_SALE,
    CREATE_CUSTOMER,
    UPDATE_CUSTOMER,
    DELETE_CUSTOMER,
    CREATE_PAYMENT,
    CREATE_ADJUSTMENT,
    CREATE_PURCHASE,
}

/**
 * One deferred mutation, replayed against the API in insertion order.
 *
 * Ordering matters: a sale can reference a drug that was created offline, so a
 * strictly FIFO drain guarantees the drug exists server-side before the sale
 * that uses it is sent. The queue therefore stops at the first failure instead
 * of skipping ahead.
 *
 * `payload` holds the request DTO as JSON — decoded back into its typed form
 * only when the item is actually sent, so the queue survives schema evolution.
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["operation", "entityLocalId"])],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: SyncOperation,
    val entityLocalId: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
    /** Last transport/server error, kept for the "needs attention" screen. */
    val lastError: String? = null,
    /**
     * Set when the server rejected the item with a non-retryable error, or the
     * retry budget ran out. Blocked heads stop the queue until resolved.
     */
    val blocked: Boolean = false,
)

// ── Cached catalog ──────────────────────────────────────────────────────────

/**
 * Local mirror of a drug. This is what the POS and drug list read, so the app
 * stays fully usable with no network.
 *
 * [currentQuantity] is optimistic while offline: selling decrements it here
 * immediately, and a catalog refresh from the server overwrites it once the
 * queued sales have been replayed.
 */
@Entity(
    tableName = "drugs",
    indices = [Index(value = ["serverId"], unique = true), Index("name")],
)
data class DrugEntity(
    /** Client-generated UUID. Stable across sync; never sent to the server. */
    @PrimaryKey val localId: String,
    /** Server id, `null` until the drug has been pushed at least once. */
    val serverId: Long? = null,
    val name: String,
    val genericName: String? = null,
    val barcode: String? = null,
    val manufacturer: String? = null,
    val category: String? = null,
    val dosageForm: String? = null,
    val strength: String? = null,
    val unit: String? = null,
    val description: String? = null,
    val minimumStockLevel: Int = 0,
    val currentQuantity: Int = 0,
    val active: Boolean = true,
    /**
     * Price this drug last sold for on this device.
     *
     * The backend keeps prices on *sale items*, not on the drug master, so
     * there is no server field to mirror. This is a purely local convenience
     * used to pre-fill the POS price field; it is never sent anywhere.
     */
    val lastSellPrice: Double? = null,
    /** Has local edits not yet acknowledged by the server. */
    val needsSync: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["serverId"], unique = true), Index("name")],
)
data class CustomerEntity(
    @PrimaryKey val localId: String,
    val serverId: Long? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    /** Cached from `GET /customers/{id}/account`; `null` until first fetched. */
    val totalDebt: Double? = null,
    val needsSync: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = 0,
)

// ── Cached sales ────────────────────────────────────────────────────────────

/**
 * A sale is written here *before* it is sent anywhere. Queued sales carry
 * `serverId = null` and `needsSync = true`, and appear in the sales list marked
 * as pending so the cashier can see what has not reached the server yet.
 */
@Entity(
    tableName = "sales",
    indices = [Index(value = ["serverId"], unique = true), Index("createdAt")],
)
data class SaleEntity(
    @PrimaryKey val localId: String,
    val serverId: Long? = null,
    val createdAt: Long,
    val customerId: Long? = null,
    val customerLocalId: String? = null,
    val customerName: String? = null,
    val paymentMethod: String,
    val status: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val amountPaid: Double,
    val amountDue: Double,
    val cost: Double,
    val profit: Double,
    val refundedTotal: Double,
    /** `sale.sale_id` when the user is known, else the display name. */
    val createdBy: String? = null,
    val needsSync: Boolean = false,
)

@Entity(
    tableName = "sale_items",
    indices = [Index("saleLocalId")],
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleLocalId: String,
    /** Server-side item id; `null` until the sale has synced (or for offline sales). */
    val serverSaleItemId: Long? = null,
    /** Link back to the drug row; survives the drug's id changing on sync. */
    val drugLocalId: String,
    val drugServerId: Long? = null,
    val drugName: String,
    val quantity: Int,
    val unitSellingPrice: Double,
    val unitCostPrice: Double,
    val discountAmount: Double = 0.0,
    val revenue: Double,
    val cost: Double,
    val profit: Double,
    val refundedQuantity: Int = 0,
)

// ── Settings + generic cache ────────────────────────────────────────────────

/**
 * Last-known server settings. Read when the network is down so the expiring
 * window and sale rules still apply offline; writes are queued.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val description: String? = null,
    val updatedAt: Long = 0,
)

/**
 * Small JSON blobs keyed by a stable name — `"dashboard"`, `"report:daily"` —
 * so the home screen and reports have something to render offline.
 */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long,
)
