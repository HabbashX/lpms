package com.lpms.data.repository

import com.lpms.data.local.CustomerDao
import com.lpms.data.local.DrugDao
import com.lpms.data.local.SaleDao
import com.lpms.data.local.SaleEntity
import com.lpms.data.local.SaleItemEntity
import com.lpms.data.local.SyncOperation
import com.lpms.data.local.toEntity
import com.lpms.data.sync.PendingSale
import com.lpms.data.sync.PendingSaleItem
import com.lpms.data.sync.SyncEngine
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.CreateRefundRequest
import com.lpms.data.remote.dto.RefundItemRequest
import com.lpms.data.remote.dto.SaleStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One line on the POS screen before it becomes a row. */
data class CartLine(
    val drugLocalId: String,
    val drugName: String,
    val quantity: Int,
    val unitSellingPrice: Double,
)

/** Everything the POS collects for one transaction. */
data class SaleDraft(
    val customerLocalId: String? = null,
    val paymentMethod: String,
    val discount: Double = 0.0,
    /** `null` means "paid in full". */
    val amountPaid: Double? = null,
    val lines: List<CartLine>,
    val createdBy: String? = null,
)

/**
 * The sale path is the one that has to work with the network off, so it is
 * deliberately the most careful:
 *
 *  1. validate against the **local** catalog, so stock checks reflect what this
 *     device has already sold;
 *  2. write the sale, its lines, and the stock decrement in one pass;
 *  3. only then queue the mutation.
 *
 * The queue is FIFO, so a drug created offline earlier in the session is pushed
 * before the sale that references it — which is what makes "add drug, sell it,
 * airplane mode" work end to end.
 */
@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val drugDao: DrugDao,
    private val customerDao: CustomerDao,
    private val api: LpmsApi,
    private val sync: SyncEngine,
    private val executor: ApiExecutor,
    private val json: Json,
) {

    fun observeSales(): Flow<List<SaleEntity>> = saleDao.observeAll()

    /** Sales recorded offline and not yet acknowledged by the server. */
    fun observePending(): Flow<List<SaleEntity>> = saleDao.observePending()

    fun observeItems(saleLocalId: String): Flow<List<SaleItemEntity>> = saleDao.observeItems(saleLocalId)

    suspend fun byLocalId(localId: String): SaleEntity? = saleDao.byLocalId(localId)

    suspend fun items(saleLocalId: String): List<SaleItemEntity> = saleDao.items(saleLocalId)

    /**
     * Records a sale. Returns the new sale's local id, or a validation error
     * describing what the cashier needs to fix. No network call is made.
     */
    suspend fun recordSale(draft: SaleDraft): ApiResult<String> {
        if (draft.lines.isEmpty()) return validation("sale_empty")

        // Backend rule: a credit sale must be tied to a customer, because the
        // debt has to be recorded against someone's ledger.
        if (draft.paymentMethod == "CREDIT" && draft.customerLocalId == null) {
            return validation("sale_credit_requires_customer")
        }

        // Backend rule: each drug may appear only once per sale.
        val seen = mutableSetOf<String>()
        var hasDuplicate = false
        for (line in draft.lines) {
            if (!seen.add(line.drugLocalId)) {
                hasDuplicate = true
                break
            }
        }
        if (hasDuplicate) {
            return validation("sale_duplicate_drug")
        }

        // Validate every line against local state before writing anything, so a
        // rejected sale never leaves a half-applied stock change.
        for (line in draft.lines) {
            if (line.quantity <= 0) return validation("sale_quantity_invalid")
            if (line.unitSellingPrice <= 0) return validation("sale_price_invalid")
            val drug = drugDao.byLocalId(line.drugLocalId) ?: return validation("drug_not_found")
            if (!drug.active) return validation("drug_inactive")
            if (line.quantity > drug.currentQuantity) return validation("insufficient_stock")
        }

        val subtotal = draft.lines.sumOf { it.quantity * it.unitSellingPrice }

        // Backend rule: discount cannot exceed the subtotal.
        val discountValue = draft.discount
        if (discountValue < 0) return validation("sale_discount_invalid")
        if (discountValue > subtotal) return validation("sale_discount_exceeds_subtotal")

        val total = subtotal - discountValue

        // Backend rule: amountPaid null means 0 for a credit sale, otherwise the
        // full total. A sale with no customer must be paid in full.
        val paid = when {
            draft.amountPaid != null -> draft.amountPaid
            draft.paymentMethod == "CREDIT" -> 0.0
            else -> total
        }.coerceAtLeast(0.0)

        if (paid > total) return validation("sale_overpaid")
        if (draft.customerLocalId == null && paid < total) {
            return validation("sale_walkin_must_pay_in_full")
        }

        val due = total - paid

        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()

        val itemRows = draft.lines.map { line ->
            val revenue = line.quantity * line.unitSellingPrice
            SaleItemEntity(
                saleLocalId = localId,
                serverSaleItemId = null,
                drugLocalId = line.drugLocalId,
                drugServerId = drugDao.byLocalId(line.drugLocalId)?.serverId,
                drugName = line.drugName,
                quantity = line.quantity,
                unitSellingPrice = line.unitSellingPrice,
                // Cost comes from the server's FIFO batches; offline we have no
                // reliable figure, so pending sales report zero profit until the
                // catalog refresh fills it in.
                unitCostPrice = 0.0,
                discountAmount = 0.0,
                revenue = revenue,
                cost = 0.0,
                profit = 0.0,
            )
        }

        val customer = draft.customerLocalId?.let { customerDao.byLocalId(it) }
        val sale = SaleEntity(
            localId = localId,
            serverId = null,
            createdAt = now,
            customerId = customer?.serverId,
            customerLocalId = customer?.localId,
            customerName = customer?.name,
            paymentMethod = draft.paymentMethod,
            status = SaleStatus.COMPLETED.name,
            subtotal = subtotal,
            discount = draft.discount,
            total = total,
            amountPaid = paid,
            amountDue = due,
            cost = 0.0,
            profit = 0.0,
            refundedTotal = 0.0,
            createdBy = draft.createdBy,
            needsSync = true,
        )

        saleDao.insert(sale)
        saleDao.insertItems(itemRows)
        // Optimistic: the stock leaves the shelf now, whether or not the server
        // ever hears about this sale.
        itemRows.forEach { drugDao.decrementStock(it.drugLocalId, it.quantity) }
        // Remember the price so the next sale of this drug pre-fills sensibly.
        itemRows.forEach { line ->
            val drug = drugDao.byLocalId(line.drugLocalId) ?: return@forEach
            drugDao.upsert(drug.copy(lastSellPrice = line.unitSellingPrice))
        }

        sync.enqueue(
            SyncOperation.CREATE_SALE,
            localId,
            json.encodeToString(
                PendingSale(
                    localId = localId,
                    customerLocalId = customer?.localId,
                    customerId = customer?.serverId,
                    paymentMethod = draft.paymentMethod,
                    discount = draft.discount,
                    amountPaid = paid,
                    items = itemRows.map { line ->
                        PendingSaleItem(
                            drugLocalId = line.drugLocalId,
                            quantity = line.quantity,
                            unitSellingPrice = line.unitSellingPrice,
                        )
                    },
                ),
            ),
        )

        return ApiResult.Success(localId)
    }

    /**
     * Refunds part or all of a sale.
     *
     * Unlike a sale this is **not** queued: the server recomputes stock from its
     * own batch ledger and the refund has to reference server-side sale item
     * ids, which an unsynced sale does not have yet. An offline sale must sync
     * before it can be refunded.
     */
    suspend fun refund(
        saleLocalId: String,
        items: List<RefundLine>,
        reason: String? = null,
    ): ApiResult<Unit> {
        val sale = saleDao.byLocalId(saleLocalId) ?: return validation("sale_not_found")
        val serverId = sale.serverId ?: return validation("sale_not_synced")
        if (items.isEmpty()) return validation("refund_empty")
        if (items.any { it.quantity <= 0 }) return validation("refund_quantity_invalid")
        if (items.any { it.serverSaleItemId == null }) return validation("refund_items_not_synced")

        val request = CreateRefundRequest(
            items = items.map {
                RefundItemRequest(
                    saleItemId = it.serverSaleItemId
                        ?: return validation("refund_items_not_synced"),
                    quantity = it.quantity,
                )
            },
            reason = reason?.trim()?.ifEmpty { null },
        )

        // Captured before the call so the fallback below can still value the
        // refund locally if the follow-up read fails.
        val storedItems = saleDao.items(saleLocalId)

        return when (val result = executor.run { api.refundSale(serverId, request) }) {
            is ApiResult.Success -> {
                // The refund response describes the refund itself, not the sale
                // as it now stands, so re-read the sale to pick up the new
                // status, refunded total and outstanding balance.
                when (val follow = executor.run { api.getSale(serverId) }) {
                    is ApiResult.Success -> {
                        val refreshed = follow.value
                        saleDao.applyRefund(
                            localId = saleLocalId,
                            status = refreshed.status.name,
                            refundedTotal = refreshed.refundedTotal,
                            amountDue = refreshed.amountDue,
                            serverId = serverId,
                        )
                        saleDao.clearItems(saleLocalId)
                        saleDao.insertItems(refreshed.items.map { it.toEntity(saleLocalId) })
                    }

                    is ApiResult.Failure -> {
                        // The refund itself landed; only the refresh failed, so
                        // apply the local best estimate rather than telling the
                        // cashier the refund failed.
                        var refunded = 0.0
                        for (line in items) {
                            for (stored in storedItems) {
                                if (stored.serverSaleItemId == line.serverSaleItemId) {
                                    refunded += stored.unitSellingPrice * line.quantity
                                    break
                                }
                            }
                        }
                        val newRefundedTotal = sale.refundedTotal + refunded
                        var fullyRefunded = true
                        for (stored in storedItems) {
                            if (stored.serverSaleItemId == null) {
                                fullyRefunded = false
                                continue
                            }
                            var requested = 0
                            for (line in items) {
                                if (line.serverSaleItemId == stored.serverSaleItemId) {
                                    requested = line.quantity
                                    break
                                }
                            }
                            if (stored.refundedQuantity + requested < stored.quantity) {
                                fullyRefunded = false
                                break
                            }
                        }
                        saleDao.applyRefund(
                            localId = saleLocalId,
                            status = if (fullyRefunded) {
                                SaleStatus.REFUNDED.name
                            } else {
                                SaleStatus.PARTIALLY_REFUNDED.name
                            },
                            refundedTotal = newRefundedTotal,
                            amountDue = (sale.amountDue + refunded).coerceAtMost(sale.total),
                            serverId = serverId,
                        )
                    }
                }
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> result
        }
    }

    private fun validation(message: String): ApiResult.Failure =
        ApiResult.Failure(ApiError.Http(422, "VALIDATION", message))
}

/** A refund line, addressed by the server's own sale-item id. */
data class RefundLine(
    val serverSaleItemId: Long?,
    val quantity: Int,
)
