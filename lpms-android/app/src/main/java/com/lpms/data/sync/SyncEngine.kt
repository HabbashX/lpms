package com.lpms.data.sync

import com.lpms.data.local.CustomerDao
import com.lpms.data.local.DrugDao
import com.lpms.data.local.OutboxDao
import com.lpms.data.local.OutboxEntity
import com.lpms.data.local.ResolvedSaleItem
import com.lpms.data.local.SaleDao
import com.lpms.data.local.SettingDao
import com.lpms.data.local.SyncOperation
import com.lpms.data.local.serverSaleLocalId
import com.lpms.data.local.toCreateRequest
import com.lpms.data.local.toEntity
import com.lpms.data.local.toUpdateRequest
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.CreateAdjustmentRequest
import com.lpms.data.remote.dto.CreateCustomerRequest
import com.lpms.data.remote.dto.CreateDrugRequest
import com.lpms.data.remote.dto.CreatePaymentRequest
import com.lpms.data.remote.dto.CreatePurchaseRequest
import com.lpms.data.remote.dto.PaymentMethod
import com.lpms.data.remote.dto.UpdateCustomerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the sync layer is currently doing, for the offline banner and the
 * "pending changes" indicator.
 */
data class SyncState(
    val online: Boolean = true,
    val syncing: Boolean = false,
    /** Mutations waiting for a connection. */
    val pending: Int = 0,
    /** Mutations the server rejected or that exhausted their retry budget. */
    val blocked: Int = 0,
    val lastError: String? = null,
    val lastSyncedAt: Long? = null,
) {
    val hasIssues: Boolean get() = blocked > 0
    val isIdle: Boolean get() = pending == 0 && blocked == 0
}

/**
 * Replays deferred mutations against the API, and refreshes the local caches.
 *
 * ## How offline works
 *
 * Writes never touch the network directly. A ViewModel writes its result into
 * Room *first* — so the screen updates immediately — then hands [enqueue] a
 * payload. [drain] sends queued items strictly in insertion order, which is
 * what makes the hard case work: a drug created offline can be sold offline,
 * and the sale is only sent after the drug exists server-side.
 *
 * The queue **stops at the first failure** rather than skipping ahead, because
 * skipping would let a sale overtake the drug creation it depends on. Failures
 * are classified as retryable (offline, 5xx, timeout) or blocking (any other
 * 4xx); blocking items wait for the user, retryable ones back off
 * exponentially.
 *
 * After a successful drain the catalog is re-fetched so optimistic local state
 * (notably stock levels) converges on what the server actually recorded.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val outboxDao: OutboxDao,
    private val drugDao: DrugDao,
    private val customerDao: CustomerDao,
    private val saleDao: SaleDao,
    private val settingDao: SettingDao,
    private val api: LpmsApi,
    private val connectivity: ConnectivityMonitor,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Draining stops after this many attempts; the item then needs attention. */
    private val maxAttempts = 6

    fun start() {
        connectivity.start()
        scope.launch {
            combine(
                connectivity.online,
                outboxDao.observePendingCount(),
                outboxDao.observeBlockedCount(),
            ).collect { (online, pending, blocked) ->
                _state.update {
                    it.copy(online = online, pending = pending, blocked = blocked)
                }
                if (online) {
                    if (pending > 0) drain() else refreshIfNeverSynced()
                }
            }
        }
    }

    fun stop() {
        connectivity.stop()
    }

    // ── Producer side ───────────────────────────────────────────────────────

    /** Queue a mutation. Returns as soon as it is durably recorded. */
    suspend fun enqueue(
        operation: SyncOperation,
        entityLocalId: String,
        payload: String,
    ) {
        outboxDao.insert(
            OutboxEntity(
                operation = operation,
                entityLocalId = entityLocalId,
                payload = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
        kick()
    }

    /** Ask for a drain without waiting for it. Safe to call repeatedly. */
    fun kick() {
        scope.launch { drain() }
    }

    /** Re-queue everything the server rejected, then try again. */
    suspend fun retryBlocked() {
        outboxDao.blocked().forEach {
            outboxDao.update(it.copy(blocked = false, attempts = 0, lastError = null))
        }
        drain()
    }

    // ── Consumer side ───────────────────────────────────────────────────────

    private suspend fun drain() = mutex.withLock {
        if (!connectivity.isOnline()) return@withLock

        _state.update { it.copy(syncing = true) }
        var progressed = false

        while (true) {
            val head = outboxDao.head() ?: break

            if (head.blocked) {
                _state.update { it.copy(lastError = head.lastError) }
                break
            }

            when (val outcome = replay(head)) {
                Outcome.Done -> {
                    outboxDao.delete(head)
                    progressed = true
                }

                is Outcome.Retry -> {
                    val attempts = head.attempts + 1
                    if (attempts >= maxAttempts) {
                        outboxDao.update(
                            head.copy(attempts = attempts, blocked = true, lastError = outcome.message),
                        )
                        _state.update { it.copy(lastError = outcome.message) }
                    } else {
                        outboxDao.update(
                            head.copy(attempts = attempts, lastError = outcome.message),
                        )
                        scheduleRetry(attempts)
                    }
                    break
                }

                is Outcome.Blocked -> {
                    outboxDao.update(
                        head.copy(
                            attempts = head.attempts + 1,
                            blocked = true,
                            lastError = outcome.message,
                        ),
                    )
                    _state.update { it.copy(lastError = outcome.message) }
                    break
                }

                Outcome.Stop -> break
            }
        }

        if (progressed) refresh()
        _state.update {
            it.copy(
                syncing = false,
                lastSyncedAt = if (progressed) System.currentTimeMillis() else it.lastSyncedAt,
            )
        }
    }

    private fun scheduleRetry(attempt: Int) {
        val backoffMs = 2_000L * (1L shl attempt.coerceAtMost(5))
        scope.launch {
            delay(backoffMs)
            drain()
        }
    }

    private suspend fun replay(head: OutboxEntity): Outcome = try {
        when (head.operation) {
            SyncOperation.CREATE_DRUG -> {
                val request = json.decodeFromString<CreateDrugRequest>(head.payload)
                val response = api.createDrug(request)
                val existing = drugDao.byLocalId(head.entityLocalId)
                drugDao.upsert(response.toEntity(existing, System.currentTimeMillis()).copy(needsSync = false))
                Outcome.Done
            }

            SyncOperation.UPDATE_DRUG -> {
                val drug = drugDao.byLocalId(head.entityLocalId) ?: return Outcome.Done
                val serverId = drug.serverId
                if (serverId == null) {
                    // Never reached the server: an update on a local-only drug
                    // collapses into a create.
                    val response = api.createDrug(drug.toCreateRequest())
                    drugDao.upsert(drug.copy(serverId = response.id, needsSync = false))
                } else {
                    val response = api.updateDrug(serverId, drug.toUpdateRequest())
                    drugDao.upsert(
                        response.toEntity(drug, System.currentTimeMillis()).copy(needsSync = false),
                    )
                }
                Outcome.Done
            }

            SyncOperation.DELETE_DRUG -> {
                val drug = drugDao.byLocalId(head.entityLocalId)
                val serverId = drug?.serverId
                if (serverId != null) {
                    api.deleteDrug(serverId)
                    drugDao.deleteByServerId(serverId)
                } else if (drug != null) {
                    drugDao.delete(drug)
                }
                Outcome.Done
            }

            SyncOperation.CREATE_SALE -> replaySale(head)

            SyncOperation.CREATE_CUSTOMER -> {
                val request = json.decodeFromString<CreateCustomerRequest>(head.payload)
                val response = api.createCustomer(request)
                val existing = customerDao.byLocalId(head.entityLocalId)
                customerDao.upsert(
                    response.toEntity(existing, System.currentTimeMillis()).copy(needsSync = false),
                )
                Outcome.Done
            }

            SyncOperation.UPDATE_CUSTOMER -> {
                val customer = customerDao.byLocalId(head.entityLocalId) ?: return Outcome.Done
                val serverId = customer.serverId
                val request = UpdateCustomerRequest(
                    name = customer.name,
                    phone = customer.phone,
                    address = customer.address,
                    notes = customer.notes,
                    active = customer.active,
                )
                if (serverId == null) {
                    val response = api.createCustomer(
                        CreateCustomerRequest(
                            name = customer.name,
                            phone = customer.phone,
                            address = customer.address,
                            notes = customer.notes,
                        ),
                    )
                    customerDao.upsert(
                        response.toEntity(customer, System.currentTimeMillis()).copy(needsSync = false),
                    )
                } else {
                    val response = api.updateCustomer(serverId, request)
                    customerDao.upsert(
                        response.toEntity(customer, System.currentTimeMillis()).copy(needsSync = false),
                    )
                }
                Outcome.Done
            }

            SyncOperation.DELETE_CUSTOMER -> {
                val customer = customerDao.byLocalId(head.entityLocalId)
                val serverId = customer?.serverId
                if (serverId != null) {
                    api.deleteCustomer(serverId)
                    customerDao.deleteByServerId(serverId)
                } else if (customer != null) {
                    customerDao.delete(customer)
                }
                Outcome.Done
            }

            SyncOperation.CREATE_PAYMENT -> {
                val pending = json.decodeFromString<PendingPayment>(head.payload)
                val customerId = resolveCustomerId(pending.customerLocalId) ?: return Outcome.Blocked(
                    "Customer has not reached the server yet",
                )
                val response = api.createPayment(
                    customerId,
                    CreatePaymentRequest(
                        amount = pending.amount,
                        paymentMethod = PaymentMethod.valueOf(pending.paymentMethod),
                        notes = pending.notes,
                    ),
                )
                customerDao.updateDebt(pending.customerLocalId, response.balanceAfter)
                Outcome.Done
            }

            SyncOperation.CREATE_ADJUSTMENT -> {
                val pending = json.decodeFromString<PendingAdjustment>(head.payload)
                val customerId = resolveCustomerId(pending.customerLocalId) ?: return Outcome.Blocked(
                    "Customer has not reached the server yet",
                )
                val response = api.createAdjustment(
                    customerId,
                    CreateAdjustmentRequest(
                        amount = pending.amount,
                        direction = pending.direction,
                        description = pending.description,
                    ),
                )
                customerDao.updateDebt(pending.customerLocalId, response.balance)
                Outcome.Done
            }

            SyncOperation.CREATE_PURCHASE -> {
                val pending = json.decodeFromString<PendingPurchase>(head.payload)
                val drug = drugDao.byLocalId(pending.drugLocalId)
                val serverId = drug?.serverId ?: return Outcome.Blocked(
                    "Drug \"${drug?.name ?: pending.drugLocalId}\" has not reached the server yet",
                )
                api.purchaseStock(
                    CreatePurchaseRequest(
                        drugId = serverId,
                        quantity = pending.quantity,
                        unitPurchasePrice = pending.unitPurchasePrice,
                        supplier = pending.supplier,
                        batchNumber = pending.batchNumber,
                        expirationDate = pending.expirationDate,
                    ),
                )
                // Local stock was already incremented when the receipt was
                // recorded; the catalog refresh reconciles it with the server.
                Outcome.Done
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        classify(error)
    }

    private suspend fun replaySale(head: OutboxEntity): Outcome {
        val pending = json.decodeFromString<PendingSale>(head.payload)
        val sale = saleDao.byLocalId(pending.localId) ?: return Outcome.Done
        val storedItems = saleDao.items(pending.localId)
        if (storedItems.isEmpty()) return Outcome.Blocked("Sale has no line items")

        val resolved = ArrayList<ResolvedSaleItem>(storedItems.size)
        for (item in storedItems) {
            val drug = drugDao.byLocalId(item.drugLocalId)
                ?: return Outcome.Blocked("A drug on this sale no longer exists")
            val serverId = drug.serverId
                ?: return Outcome.Blocked("\"${drug.name}\" has not reached the server yet")
            resolved += ResolvedSaleItem(serverId, item.quantity, item.unitSellingPrice)
        }

        val customerId = pending.customerId
            ?: pending.customerLocalId?.let { customerDao.byLocalId(it)?.serverId }

        val response = api.createSale(
            sale.copy(customerId = customerId).toCreateRequest(resolved),
        )
        saleDao.markSynced(pending.localId, response.id, response.status.name)
        return Outcome.Done
    }

    private suspend fun resolveCustomerId(localId: String?): Long? =
        localId?.let { customerDao.byLocalId(it)?.serverId }

    private fun classify(error: Exception): Outcome = when (error) {
        is IOException -> Outcome.Retry("No connection")
        is HttpException -> when (val code = error.code()) {
            401 -> Outcome.Stop
            408, 429 -> Outcome.Retry("HTTP $code")
            in 500..599 -> Outcome.Retry("HTTP $code")
            else -> Outcome.Blocked("HTTP $code")
        }
        else -> Outcome.Blocked(error.message ?: error::class.java.simpleName)
    }

    /**
     * Pull the server's current view of drugs, customers, sales and settings
     * into Room right now. Called by pull-to-refresh; the drain loop calls it
     * after replaying a batch of queued mutations.
     */
    suspend fun refreshNow() {
        if (!connectivity.isOnline()) return
        refresh()
    }

    // ── Refresh ─────────────────────────────────────────────────────────────

    private var hasCompletedInitialRefresh = false

    private suspend fun refreshIfNeverSynced() {
        if (hasCompletedInitialRefresh) return
        refresh()
    }

    /**
     * Pull the server's view of the world into Room. Rows with unsynced local
     * edits are left alone by [toEntity], so a refresh never loses offline work.
     */
    private suspend fun refresh() {
        runCatching { refreshDrugs() }
        runCatching { refreshCustomers() }
        runCatching { refreshSales() }
        runCatching { refreshSettings() }
        hasCompletedInitialRefresh = true
    }

    private suspend fun refreshDrugs() {
        var page = 0
        while (page < maxPages) {
            val response = api.listDrugs(page = page, size = pageSize, sort = "name,asc")
            if (response.content.isEmpty()) break
            val now = System.currentTimeMillis()
            drugDao.upsertAll(
                response.content.map { dto ->
                    dto.toEntity(drugDao.byServerId(dto.id), now)
                },
            )
            page++
            if (page >= response.totalPages) break
        }
    }

    private suspend fun refreshCustomers() {
        var page = 0
        while (page < maxPages) {
            val response = api.listCustomers(page = page, size = pageSize, sort = "name,asc")
            if (response.content.isEmpty()) break
            val now = System.currentTimeMillis()
            customerDao.upsertAll(
                response.content.map { dto ->
                    dto.toEntity(customerDao.byServerId(dto.id), now)
                },
            )
            page++
            if (page >= response.totalPages) break
        }
    }

    private suspend fun refreshSales() {
        var page = 0
        while (page < maxPages) {
            val response = api.listSales(page = page, size = pageSize, sort = "createdAt,desc")
            if (response.content.isEmpty()) break
            response.content.forEach { dto ->
                val localId = serverSaleLocalId(dto.id)
                saleDao.insert(dto.toEntity(localId))
                saleDao.clearItems(localId)
                saleDao.insertItems(dto.items.map { it.toEntity(localId) })
            }
            page++
            if (page >= response.totalPages) break
        }
    }

    private suspend fun refreshSettings() {
        val settings = api.getSettings()
        settingDao.upsertAll(settings.map { it.toEntity(System.currentTimeMillis()) })
    }

    companion object {
        private const val pageSize = 100
        private const val maxPages = 20
    }
}

private sealed interface Outcome {
    data object Done : Outcome
    data class Retry(val message: String) : Outcome
    data class Blocked(val message: String) : Outcome
    /** Stop draining entirely — typically the session is gone. */
    data object Stop : Outcome
}
