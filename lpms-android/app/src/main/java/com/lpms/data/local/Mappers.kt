package com.lpms.data.local

import com.lpms.data.remote.dto.CreateDrugRequest
import com.lpms.data.remote.dto.CreateSaleItemRequest
import com.lpms.data.remote.dto.CreateSaleRequest
import com.lpms.data.remote.dto.CustomerResponse
import com.lpms.data.remote.dto.DosageForm
import com.lpms.data.remote.dto.DrugResponse
import com.lpms.data.remote.dto.PaymentMethod
import com.lpms.data.remote.dto.SaleItemResponse
import com.lpms.data.remote.dto.SaleResponse
import com.lpms.data.remote.dto.SettingResponse
import com.lpms.data.remote.dto.UpdateCustomerRequest
import com.lpms.data.remote.dto.UpdateDrugRequest
import java.util.UUID


/**
 * Field-level translations between the wire model and the local mirror.
 *
 * Direction rules:
 *  - `toEntity` is used on every server fetch. It only *adds* or refreshes rows
 *    that have no unsynced local edits, so a pending offline change is never
 *    clobbered by a refresh.
 *  - `toCreateRequest` / `toUpdateRequest` turn local edits into API payloads.
 */

// ── Drugs ───────────────────────────────────────────────────────────────────

fun DrugResponse.toEntity(existing: DrugEntity?, now: Long): DrugEntity =
    DrugEntity(
        // Reuse the local row when we already know this server drug, so any
        // pending offline edits stay attached to it.
        localId = existing?.localId ?: UUID.randomUUID().toString(),
        serverId = id,
        name = name,
        genericName = genericName,
        barcode = barcode,
        manufacturer = manufacturer,
        category = category,
        dosageForm = dosageForm?.name,
        strength = strength,
        unit = unit,
        description = description,
        minimumStockLevel = minimumStockLevel,
        // Local edits win until they have been pushed.
        currentQuantity = existing?.takeIf { it.needsSync }?.currentQuantity ?: currentQuantity,
        active = existing?.takeIf { it.needsSync }?.active ?: active,
        lastSellPrice = existing?.lastSellPrice,
        needsSync = existing?.needsSync ?: false,
        deleted = existing?.deleted ?: false,
        updatedAt = now,
    )

fun DrugEntity.toCreateRequest(): CreateDrugRequest =
    CreateDrugRequest(
        name = name,
        genericName = genericName,
        barcode = barcode,
        manufacturer = manufacturer,
        category = category,
        dosageForm = dosageForm?.let { runCatching { DosageForm.valueOf(it) }.getOrNull() },
        strength = strength,
        unit = unit,
        description = description,
        minimumStockLevel = minimumStockLevel,
    )

fun DrugEntity.toUpdateRequest(): UpdateDrugRequest =
    UpdateDrugRequest(
        name = name,
        genericName = genericName,
        barcode = barcode,
        manufacturer = manufacturer,
        category = category,
        dosageForm = dosageForm?.let { runCatching { DosageForm.valueOf(it) }.getOrNull() },
        strength = strength,
        unit = unit,
        description = description,
        minimumStockLevel = minimumStockLevel,
        active = active,
    )

// ── Customers ───────────────────────────────────────────────────────────────

fun CustomerResponse.toEntity(existing: CustomerEntity?, now: Long): CustomerEntity =
    CustomerEntity(
        localId = existing?.localId ?: UUID.randomUUID().toString(),
        serverId = id,
        name = name,
        phone = phone,
        address = address,
        notes = notes,
        active = existing?.takeIf { it.needsSync }?.active ?: active,
        totalDebt = existing?.totalDebt,
        needsSync = existing?.needsSync ?: false,
        deleted = existing?.deleted ?: false,
        updatedAt = now,
    )

// ── Sales ───────────────────────────────────────────────────────────────────

/**
 * Server sales are cached under a deterministic local id so re-fetching the
 * same page is idempotent; offline sales use a UUID instead.
 */
fun serverSaleLocalId(serverId: Long): String = "s:$serverId"

fun SaleResponse.toEntity(localId: String = serverSaleLocalId(id)): SaleEntity =
    SaleEntity(
        localId = localId,
        serverId = id,
        createdAt = parseIsoToMillis(createdAt),
        customerId = customerId,
        customerLocalId = null,
        customerName = customerName,
        paymentMethod = paymentMethod.name,
        status = status.name,
        subtotal = subtotal,
        discount = discount,
        total = total,
        amountPaid = amountPaid,
        amountDue = amountDue,
        cost = cost,
        profit = profit,
        refundedTotal = refundedTotal,
        createdBy = createdBy,
        needsSync = false,
    )

fun SaleItemResponse.toEntity(saleLocalId: String): SaleItemEntity =
    SaleItemEntity(
        saleLocalId = saleLocalId,
        serverSaleItemId = id,
        drugLocalId = "",
        drugServerId = drugId,
        drugName = drugName,
        quantity = quantity,
        unitSellingPrice = unitSellingPrice,
        unitCostPrice = unitCostPrice,
        discountAmount = discountAmount,
        revenue = revenue,
        cost = cost,
        profit = profit,
        refundedQuantity = refundedQuantity,
    )

/** A sale line whose drug id has already been resolved to the server's id. */
data class ResolvedSaleItem(
    val drugId: Long,
    val quantity: Int,
    val unitSellingPrice: Double,
)

fun SaleEntity.toCreateRequest(items: List<ResolvedSaleItem>): CreateSaleRequest =
    CreateSaleRequest(
        customerId = customerId,
        paymentMethod = PaymentMethod.valueOf(paymentMethod),
        items = items.map {
            CreateSaleItemRequest(
                drugId = it.drugId,
                quantity = it.quantity,
                unitSellingPrice = it.unitSellingPrice,
            )
        },
        discount = discount,
        amountPaid = amountPaid,
    )

// ── Settings ────────────────────────────────────────────────────────────────

fun SettingResponse.toEntity(now: Long): SettingEntity =
    SettingEntity(
        key = key,
        value = value,
        description = description,
        updatedAt = now,
    )

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Parses the backend's ISO-8601 timestamps (`2026-10-01T12:34:56.789Z`, with
 * or without milliseconds, with `Z` or a `±HH:MM` offset).
 *
 * Hand-rolled rather than `java.time`: the app's `minSdk` is 24 and core
 * library desugaring is deliberately not enabled, so `Instant`/`OffsetDateTime`
 * are unavailable below API 26. Anything unparseable falls back to "now"
 * instead of crashing a list render.
 */
private val ISO_TIMESTAMP = Regex(
    """^(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?""" +
        """(?:Z|z|([+-])(\d{2}):?(\d{2}))?$""",
)

fun parseIsoToMillis(iso: String?): Long {
    if (iso.isNullOrBlank()) return System.currentTimeMillis()
    val match = ISO_TIMESTAMP.matchEntire(iso.trim()) ?: return System.currentTimeMillis()

    val (year, month, day) = match.destructured.let {
        Triple(it.component1().toInt(), it.component2().toInt(), it.component3().toInt())
    }
    val hour = match.groupValues[4].toInt()
    val minute = match.groupValues[5].toInt()
    val second = match.groupValues[6].toInt()
    val fraction = match.groupValues[7].padEnd(3, '0').take(3).toIntOrNull() ?: 0

    var millis = epochDay(year, month, day) * 86_400_000L +
        hour * 3_600_000L + minute * 60_000L + second * 1_000L + fraction

    val sign = match.groupValues[8]
    if (sign.isNotEmpty()) {
        val offsetMinutes =
            (match.groupValues[9].toIntOrNull() ?: 0) * 60 +
                (match.groupValues[10].toIntOrNull() ?: 0)
        millis -= if (sign == "-") -offsetMinutes * 60_000L else offsetMinutes * 60_000L
    }
    return millis
}

/** Days since 1970-01-01 using the proleptic Gregorian calendar. */
private fun epochDay(year: Int, month: Int, day: Int): Long {
    val adjustedYear = if (month <= 2) year - 1 else year
    val era = (if (adjustedYear >= 0) adjustedYear else adjustedYear - 399) / 400
    val yearOfEra = adjustedYear - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097L + dayOfEra - 719_468L
}

fun UpdateCustomerRequest.toEntityPatch(
    existing: CustomerEntity,
    now: Long,
): CustomerEntity = existing.copy(
    name = name,
    phone = phone,
    address = address,
    notes = notes,
    active = active ?: existing.active,
    needsSync = true,
    updatedAt = now,
)
