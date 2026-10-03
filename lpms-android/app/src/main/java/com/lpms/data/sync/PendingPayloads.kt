package com.lpms.data.sync

import com.lpms.data.local.SaleItemEntity
import kotlinx.serialization.Serializable

// The outbox stores each deferred mutation as JSON. These are the payloads that
// are *not* already a request DTO — they carry local identifiers that have to
// be resolved into server identifiers at replay time.

/**
 * An offline sale.
 *
 * Items reference drugs by `drugLocalId`, not `drugId`, because a drug created
 * on this device has no server id yet. The drain loop maps them after the
 * FIFO queue has already pushed any pending drug creations.
 */
@Serializable
data class PendingSale(
    val localId: String,
    val customerLocalId: String? = null,
    val customerId: Long? = null,
    val paymentMethod: String,
    val discount: Double = 0.0,
    val amountPaid: Double = 0.0,
    val items: List<PendingSaleItem>,
)

@Serializable
data class PendingSaleItem(
    val drugLocalId: String,
    val quantity: Int,
    val unitSellingPrice: Double,
)

/** A customer payment, queued until the customer has a server id. */
@Serializable
data class PendingPayment(
    val customerLocalId: String,
    val amount: Double,
    val paymentMethod: String,
    val notes: String? = null,
)

/** A customer balance adjustment, queued until the customer has a server id. */
@Serializable
data class PendingAdjustment(
    val customerLocalId: String,
    val amount: Double,
    val direction: String,
    val description: String,
)

/** A stock receipt (purchase), queued until the drug has a server id. */
@Serializable
data class PendingPurchase(
    val drugLocalId: String,
    val quantity: Int,
    val unitPurchasePrice: Double,
    val supplier: String? = null,
    val batchNumber: String? = null,
    val expirationDate: String? = null,
)

/** Convenience bridge so callers can turn a stored item list into a payload. */
fun List<SaleItemEntity>.toPendingItems(): List<PendingSaleItem> = map {
    PendingSaleItem(
        drugLocalId = it.drugLocalId,
        quantity = it.quantity,
        unitSellingPrice = it.unitSellingPrice,
    )
}
