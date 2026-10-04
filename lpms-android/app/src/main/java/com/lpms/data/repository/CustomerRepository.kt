package com.lpms.data.repository

import com.lpms.data.local.CustomerDao
import com.lpms.data.local.CustomerEntity
import com.lpms.data.local.SyncOperation
import com.lpms.data.local.toEntity
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.CreateCustomerRequest
import com.lpms.data.remote.dto.TransactionResponse
import com.lpms.data.remote.dto.UpdateCustomerRequest
import com.lpms.data.sync.PendingAdjustment
import com.lpms.data.sync.PendingPayment
import com.lpms.data.sync.SyncEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Fields the customer editor collects. */
data class CustomerDraft(
    val name: String = "",
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
)

/**
 * Customers behave like drugs: Room is authoritative, writes are queued.
 *
 * The difference is the account. Balance history is a server-side aggregate, so
 * the detail screen pulls it over the network and only caches the resulting
 * debt; the payments themselves are queued so a debt can be settled offline.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val api: LpmsApi,
    private val sync: SyncEngine,
    private val executor: ApiExecutor,
    private val json: Json,
) {

    fun observeCatalog(search: String): Flow<List<CustomerEntity>> = customerDao.observeCatalog(search)

    suspend fun byLocalId(localId: String): CustomerEntity? = customerDao.byLocalId(localId)

    suspend fun save(localId: String?, draft: CustomerDraft): ApiResult<String> {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "customer_name_required"))
        }

        val now = System.currentTimeMillis()
        val id = localId ?: UUID.randomUUID().toString()
        val existing = customerDao.byLocalId(id)

        val entity = (existing ?: CustomerEntity(localId = id, name = name)).copy(
            name = name,
            phone = draft.phone.clean(),
            address = draft.address.clean(),
            notes = draft.notes.clean(),
            active = draft.active,
            needsSync = true,
            deleted = false,
            updatedAt = now,
        )
        customerDao.upsert(entity)

        // As with drugs, any edit of an existing row is an UPDATE: the replay
        // loop collapses an UPDATE on a customer the server has not seen into a
        // create, so a customer created and then edited offline replays once.
        val (operation, payload) = if (existing == null) {
            SyncOperation.CREATE_CUSTOMER to json.encodeToString(
                CreateCustomerRequest(
                    name = entity.name,
                    phone = entity.phone,
                    address = entity.address,
                    notes = entity.notes,
                ),
            )
        } else {
            SyncOperation.UPDATE_CUSTOMER to json.encodeToString(
                UpdateCustomerRequest(
                    name = entity.name,
                    phone = entity.phone,
                    address = entity.address,
                    notes = entity.notes,
                    active = entity.active,
                ),
            )
        }
        sync.enqueue(operation, id, payload)

        if (sync.state.value.online) sync.kick()

        return ApiResult.Success(id)
    }

    suspend fun delete(localId: String): ApiResult<Unit> {
        val customer = customerDao.byLocalId(localId) ?: return ApiResult.Success(Unit)
        if (customer.serverId == null) {
            customerDao.delete(customer)
            return ApiResult.Success(Unit)
        }
        customerDao.upsert(
            customer.copy(deleted = true, needsSync = true, updatedAt = System.currentTimeMillis()),
        )
        sync.enqueue(SyncOperation.DELETE_CUSTOMER, localId, payload = "")
        return ApiResult.Success(Unit)
    }

    /**
     * Records a payment against the customer's balance. Queued rather than sent
     * directly so settling a debt works with no network; the optimistic debt
     * change below is what the user sees in the meantime.
     */
    suspend fun recordPayment(
        customerLocalId: String,
        amount: Double,
        paymentMethod: String,
        notes: String? = null,
    ): ApiResult<Unit> {
        if (amount <= 0) {
            return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "payment_amount_invalid"))
        }
        val customer = customerDao.byLocalId(customerLocalId)
            ?: return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "customer_not_found"))

        customerDao.updateDebt(customerLocalId, (customer.totalDebt ?: 0.0) - amount)
        sync.enqueue(
            SyncOperation.CREATE_PAYMENT,
            customerLocalId,
            json.encodeToString(
                PendingPayment(
                    customerLocalId = customerLocalId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    notes = notes.clean(),
                ),
            ),
        )
        if (sync.state.value.online) sync.kick()
        return ApiResult.Success(Unit)
    }

    /** Adds to (or, with a negative amount, reduces) the recorded balance. */
    suspend fun recordAdjustment(
        customerLocalId: String,
        amount: Double,
        direction: String,
        description: String,
    ): ApiResult<Unit> {
        if (amount == 0.0) {
            return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "adjustment_amount_invalid"))
        }
        if (description.isBlank()) {
            return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "adjustment_reason_required"))
        }
        val customer = customerDao.byLocalId(customerLocalId)
            ?: return ApiResult.Failure(ApiError.Http(422, "VALIDATION", "customer_not_found"))

        // Backend vocabulary (TransactionDirection): DEBIT increases what the
        // customer owes, CREDIT reduces it. Mirror that optimistically so the
        // balance the owner sees matches the ledger the server will produce.
        val delta = if (direction.equals("DEBIT", ignoreCase = true)) amount else -amount
        customerDao.updateDebt(customerLocalId, (customer.totalDebt ?: 0.0) + delta)
        sync.enqueue(
            SyncOperation.CREATE_ADJUSTMENT,
            customerLocalId,
            json.encodeToString(
                PendingAdjustment(
                    customerLocalId = customerLocalId,
                    amount = amount,
                    direction = direction,
                    description = description.trim(),
                ),
            ),
        )
        if (sync.state.value.online) sync.kick()
        return ApiResult.Success(Unit)
        return ApiResult.Success(Unit)
    }

    /**
     * Pulls the server-side account (totals + transaction history) and caches
     * the resulting balance. Read-only, so it needs a connection; the cached
     * balance is what the screen falls back to offline.
     */
    suspend fun refreshAccount(localId: String): ApiResult<CustomerAccountSnapshot> {
        val customer = customerDao.byLocalId(localId)
            ?: return ApiResult.Failure(ApiError.Http(404, "NOT_FOUND", "customer_not_found"))
        val serverId = customer.serverId
            ?: return ApiResult.Failure(ApiError.Http(409, "NOT_SYNCED", "customer_not_synced"))

        return when (val result = executor.run { api.customerAccount(serverId) }) {
            is ApiResult.Success -> {
                val account = result.value
                customerDao.updateDebt(localId, account.currentDebt)
                customerDao.upsert(account.customer.toEntity(customer, System.currentTimeMillis()))
                ApiResult.Success(
                    CustomerAccountSnapshot(
                        totalPurchases = account.totalPurchases,
                        totalPaid = account.totalPaid,
                        totalRefunds = account.totalRefunds,
                        currentDebt = account.currentDebt,
                        transactions = account.transactions.content,
                    ),
                )
            }

            is ApiResult.Failure -> result
        }
    }
}

/** The account figures the customer detail screen renders. */
data class CustomerAccountSnapshot(
    val totalPurchases: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val currentDebt: Double = 0.0,
    val transactions: List<TransactionResponse> = emptyList(),
)
