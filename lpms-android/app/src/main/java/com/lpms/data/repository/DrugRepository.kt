package com.lpms.data.repository

import com.lpms.data.local.DrugDao
import com.lpms.data.local.DrugEntity
import com.lpms.data.local.SyncOperation
import com.lpms.data.local.toCreateRequest
import com.lpms.data.local.toEntity
import com.lpms.data.local.toUpdateRequest
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.sync.PendingPurchase
import com.lpms.data.sync.SyncEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fields the drug editor collects. Deliberately separate from [DrugEntity] so a
 * form cannot accidentally carry server-managed columns (id, stock, needsSync)
 * into a create/update payload.
 */
data class DrugDraft(
    val name: String = "",
    val genericName: String? = null,
    val barcode: String? = null,
    val manufacturer: String? = null,
    val category: String? = null,
    val dosageForm: String? = null,
    val strength: String? = null,
    val unit: String? = null,
    val description: String? = null,
    val minimumStockLevel: Int = 0,
    val active: Boolean = true,
)

/**
 * Single entry point for drug reads and writes.
 *
 * Reads come from Room only, so every list works with no network. Writes also
 * go to Room first and are *then* queued: the row is visible immediately and
 * carries `needsSync = true`, and [SyncEngine] pushes it later in queue order.
 */
@Singleton
class DrugRepository @Inject constructor(
    private val drugDao: DrugDao,
    private val api: LpmsApi,
    private val sync: SyncEngine,
    private val executor: ApiExecutor,
    private val json: Json,
) {

    fun observeCatalog(search: String): Flow<List<DrugEntity>> = drugDao.observeCatalog(search)

    fun observeSellable(search: String): Flow<List<DrugEntity>> =
        drugDao.observeSellable(search, includeInactive = false)

    suspend fun byLocalId(localId: String): DrugEntity? = drugDao.byLocalId(localId)

    /**
     * Barcode lookup. The local catalog answers first, which is what makes
     * scanning work offline; the server is only consulted for a barcode this
     * device has never seen and only while online.
     */
    suspend fun findByBarcode(barcode: String): DrugEntity? {
        val local = drugDao.byBarcode(barcode)
        if (local != null) return local
        if (!sync.state.value.online) return null

        val remote = when (val result = executor.run { api.drugByBarcode(barcode) }) {
            is ApiResult.Success -> result.value
            else -> return null
        }
        val row = remote.toEntity(drugDao.byServerId(remote.id), System.currentTimeMillis())
        drugDao.upsert(row)
        return row
    }

    /**
     * Creates or updates a drug. Passing a `localId` that already exists
     * updates it; passing `null` creates a new local-only drug.
     *
     * @return the local id to navigate to, or a validation failure.
     */
    suspend fun save(localId: String?, draft: DrugDraft): ApiResult<String> {
        val name = draft.name.trim()
        if (name.isEmpty()) return validation("drug_name_required")

        val now = System.currentTimeMillis()
        val id = localId ?: UUID.randomUUID().toString()
        val existing = drugDao.byLocalId(id)

        val entity = (existing ?: DrugEntity(localId = id, name = name)).copy(
            name = name,
            genericName = draft.genericName.clean(),
            barcode = draft.barcode.clean(),
            manufacturer = draft.manufacturer.clean(),
            category = draft.category.clean(),
            dosageForm = draft.dosageForm.clean(),
            strength = draft.strength.clean(),
            unit = draft.unit.clean(),
            description = draft.description.clean(),
            minimumStockLevel = draft.minimumStockLevel.coerceAtLeast(0),
            active = draft.active,
            needsSync = true,
            deleted = false,
            updatedAt = now,
        )
        drugDao.upsert(entity)

        // A brand-new drug is a CREATE. Editing an existing row is always an
        // UPDATE, even when the server has not seen it yet: the replay loop
        // collapses an UPDATE on an id-less row into a create, so this cannot
        // produce a duplicate if the drug was created offline earlier.
        val (operation, payload) = if (existing == null) {
            SyncOperation.CREATE_DRUG to json.encodeToString(entity.toCreateRequest())
        } else {
            SyncOperation.UPDATE_DRUG to json.encodeToString(entity.toUpdateRequest())
        }
        sync.enqueue(operation, id, payload)

        return ApiResult.Success(id)
    }

    /**
     * Deletes a drug. A drug the server has never seen is simply removed; one
     * it knows about is hidden locally and deleted on the next drain, so the row
     * disappears from every screen straight away.
     */
    suspend fun delete(localId: String): ApiResult<Unit> {
        val drug = drugDao.byLocalId(localId) ?: return ApiResult.Success(Unit)
        if (drug.serverId == null) {
            drugDao.delete(drug)
            return ApiResult.Success(Unit)
        }
        drugDao.upsert(
            drug.copy(deleted = true, needsSync = true, updatedAt = System.currentTimeMillis()),
        )
        sync.enqueue(SyncOperation.DELETE_DRUG, localId, payload = "")
        return ApiResult.Success(Unit)
    }

    /** Records a stock receipt and queues the matching purchase. */
    suspend fun recordPurchase(
        drugLocalId: String,
        quantity: Int,
        unitPurchasePrice: Double,
        supplier: String? = null,
        batchNumber: String? = null,
        expirationDate: String? = null,
    ): ApiResult<Unit> {
        if (quantity <= 0) return validation("purchase_quantity_invalid")
        if (drugDao.byLocalId(drugLocalId) == null) return validation("drug_not_found")
        if (unitPurchasePrice < 0) return validation("purchase_price_invalid")

        // Stock rises immediately; the catalog refresh reconciles it later.
        drugDao.incrementStock(drugLocalId, quantity)
        sync.enqueue(
            SyncOperation.CREATE_PURCHASE,
            drugLocalId,
            json.encodeToString(
                PendingPurchase(
                    drugLocalId = drugLocalId,
                    quantity = quantity,
                    unitPurchasePrice = unitPurchasePrice,
                    supplier = supplier.clean(),
                    batchNumber = batchNumber.clean(),
                    expirationDate = expirationDate.clean(),
                ),
            ),
        )
        return ApiResult.Success(Unit)
    }

    private fun validation(message: String): ApiResult.Failure =
        ApiResult.Failure(ApiError.Http(422, "VALIDATION", message))
}

/** Trims a nullable field, collapsing blank input to "absent". */
internal fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
