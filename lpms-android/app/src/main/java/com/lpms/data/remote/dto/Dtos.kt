package com.lpms.data.remote.dto

import kotlinx.serialization.Serializable

// ── Auth ────────────────────────────────────────────────────────────────────

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 0,
    val user: UserSummary,
) {
    @Serializable
    data class UserSummary(
        val id: Long,
        val username: String,
        val role: String,
    )
}

@Serializable
data class UserResponse(
    val id: Long,
    val username: String,
    val role: String,
    val enabled: Boolean = true,
    val mustChangePassword: Boolean = false,
    val lastLoginAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

// ── Envelopes ───────────────────────────────────────────────────────────────

/** Backend error envelope (`ErrorResponse`), `errors` omitted when null. */
@Serializable
data class ErrorResponse(
    val timestamp: String? = null,
    val status: Int = 0,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
    val errors: List<FieldViolation>? = null,
) {
    @Serializable
    data class FieldViolation(
        val field: String,
        val message: String,
    )
}

/** Backend pagination envelope (`PageResponse`), used by list screens. */
@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

// ── Shared enumerations ─────────────────────────────────────────────────────

@Serializable
enum class PaymentMethod { CASH, CARD, CREDIT, BANK_TRANSFER, INSURANCE }

@Serializable
enum class SaleStatus { COMPLETED, PARTIALLY_REFUNDED, REFUNDED }

@Serializable
enum class DosageForm {
    TABLET, CAPSULE, SYRUP, SOLUTION, INJECTION, CREAM, OINTMENT, GEL, DROPS,
    INHALER, SPRAY, POWDER, PATCH, SUPPOSITORY, OTHER,
}

@Serializable
enum class AuditAction {
    LOGIN, LOGOUT, CHANGE_PASSWORD, CREATE_USER, UPDATE_USER, CREATE_DRUG,
    UPDATE_DRUG, DELETE_DRUG, PURCHASE_STOCK, CREATE_CUSTOMER, UPDATE_CUSTOMER,
    DELETE_CUSTOMER, CREATE_SALE, REFUND, CUSTOMER_PAYMENT, CUSTOMER_ADJUSTMENT,
    UPDATE_SETTINGS,
}

/**
 * Money travels as a JSON number; `Double` is the type kotlinx maps onto it
 * without a custom serializer. Every amount the backend emits is already
 * rounded to 2 dp, and the app never does arithmetic across fields.
 */
typealias Money = Double

// ── Drugs ───────────────────────────────────────────────────────────────────

@Serializable
data class DrugResponse(
    val id: Long,
    val name: String,
    val genericName: String? = null,
    val barcode: String? = null,
    val manufacturer: String? = null,
    val category: String? = null,
    val dosageForm: DosageForm? = null,
    val strength: String? = null,
    val unit: String? = null,
    val description: String? = null,
    val minimumStockLevel: Int = 0,
    val currentQuantity: Int = 0,
    val active: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateDrugRequest(
    val name: String,
    val genericName: String? = null,
    val barcode: String? = null,
    val manufacturer: String? = null,
    val category: String? = null,
    val dosageForm: DosageForm? = null,
    val strength: String? = null,
    val unit: String? = null,
    val description: String? = null,
    val minimumStockLevel: Int? = null,
)

@Serializable
data class UpdateDrugRequest(
    val name: String,
    val genericName: String? = null,
    val barcode: String? = null,
    val manufacturer: String? = null,
    val category: String? = null,
    val dosageForm: DosageForm? = null,
    val strength: String? = null,
    val unit: String? = null,
    val description: String? = null,
    val minimumStockLevel: Int? = null,
    val active: Boolean? = null,
)

// ── Customers ───────────────────────────────────────────────────────────────

@Serializable
data class CustomerResponse(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateCustomerRequest(
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateCustomerRequest(
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class CustomerAccountResponse(
    val customer: CustomerResponse,
    val totalPurchases: Money = 0.0,
    val totalPaid: Money = 0.0,
    val totalRefunds: Money = 0.0,
    val currentDebt: Money = 0.0,
    val transactions: PageResponse<TransactionResponse> = PageResponse(),
)

@Serializable
data class TransactionResponse(
    val id: Long,
    val date: String? = null,
    val type: String,
    val description: String? = null,
    val debit: Money = 0.0,
    val credit: Money = 0.0,
    val balance: Money = 0.0,
    val reference: String? = null,
    val createdBy: String? = null,
)

@Serializable
data class PaymentResponse(
    val id: Long,
    val customerId: Long,
    val amount: Money,
    val paymentMethod: PaymentMethod,
    val notes: String? = null,
    val balanceAfter: Money = 0.0,
    val createdAt: String? = null,
)

@Serializable
data class CreatePaymentRequest(
    val amount: Money,
    val paymentMethod: PaymentMethod,
    val notes: String? = null,
)

@Serializable
data class CreateAdjustmentRequest(
    val amount: Money,
    val direction: String,
    val description: String,
)

// ── Sales ───────────────────────────────────────────────────────────────────

@Serializable
data class SaleItemResponse(
    val id: Long,
    val drugId: Long,
    val drugName: String,
    val quantity: Int,
    val unitSellingPrice: Money,
    val unitCostPrice: Money = 0.0,
    val discountAmount: Money = 0.0,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
    val refundedQuantity: Int = 0,
)

@Serializable
data class SaleResponse(
    val id: Long,
    val createdAt: String? = null,
    val customerId: Long? = null,
    val customerName: String? = null,
    val createdBy: String? = null,
    val paymentMethod: PaymentMethod,
    val status: SaleStatus,
    val subtotal: Money = 0.0,
    val discount: Money = 0.0,
    val total: Money = 0.0,
    val amountPaid: Money = 0.0,
    val amountDue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
    val refundedTotal: Money = 0.0,
    val items: List<SaleItemResponse> = emptyList(),
)

@Serializable
data class CreateSaleItemRequest(
    val drugId: Long,
    val quantity: Int,
    val unitSellingPrice: Money,
)

@Serializable
data class CreateSaleRequest(
    val customerId: Long? = null,
    val paymentMethod: PaymentMethod,
    val items: List<CreateSaleItemRequest>,
    val discount: Money = 0.0,
    val amountPaid: Money = 0.0,
)

@Serializable
data class RefundItemResponse(
    val saleItemId: Long,
    val drugName: String,
    val quantity: Int,
    val unitSellingPrice: Money,
    val unitCostPrice: Money = 0.0,
    val amount: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
)

@Serializable
data class RefundResponse(
    val id: Long,
    val saleId: Long,
    val createdAt: String? = null,
    val reason: String? = null,
    val totalAmount: Money = 0.0,
    val totalCost: Money = 0.0,
    val totalProfit: Money = 0.0,
    val createdBy: String? = null,
    val items: List<RefundItemResponse> = emptyList(),
)

@Serializable
data class RefundItemRequest(
    val saleItemId: Long,
    val quantity: Int,
)

@Serializable
data class CreateRefundRequest(
    val items: List<RefundItemRequest>,
    val reason: String? = null,
)

// ── Inventory ───────────────────────────────────────────────────────────────

@Serializable
data class StockBatchResponse(
    val id: Long,
    val drugId: Long,
    val drugName: String,
    val quantityReceived: Int,
    val remainingQuantity: Int,
    val unitPurchasePrice: Money,
    val supplier: String? = null,
    val batchNumber: String? = null,
    val expirationDate: String? = null,
    val receivedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreatePurchaseRequest(
    val drugId: Long,
    val quantity: Int,
    val unitPurchasePrice: Money,
    val supplier: String? = null,
    val batchNumber: String? = null,
    val expirationDate: String? = null,
)

@Serializable
data class DrugValuationResponse(
    val drugId: Long,
    val drugName: String,
    val totalQuantity: Long = 0,
    val totalInventoryCost: Money = 0.0,
    val weightedAverageCost: Money = 0.0,
)

@Serializable
data class LowStockDrugResponse(
    val drugId: Long,
    val name: String,
    val currentQuantity: Int,
    val minimumStockLevel: Int,
    val unit: String? = null,
)

@Serializable
data class ExpiringBatchResponse(
    val batchId: Long,
    val drugId: Long,
    val drugName: String,
    val batchNumber: String? = null,
    val expirationDate: String? = null,
    val daysUntilExpiration: Long = 0,
    val expired: Boolean = false,
    val remainingQuantity: Int,
    val unitPurchasePrice: Money,
    val supplier: String? = null,
)

// ── Dashboard ───────────────────────────────────────────────────────────────

@Serializable
data class DashboardResponse(
    val today: Today = Today(),
    val month: Month = Month(),
    val inventory: Inventory = Inventory(),
    val customers: Customers = Customers(),
    val topSellingDrugs: List<TopSellingDrug> = emptyList(),
) {
    @Serializable
    data class Today(
        val revenue: Money = 0.0,
        val profit: Money = 0.0,
        val sales: Long = 0,
    )

    @Serializable
    data class Month(
        val year: Int = 0,
        val month: Int = 0,
        val revenue: Money = 0.0,
        val profit: Money = 0.0,
        val sales: Long = 0,
    )

    @Serializable
    data class Inventory(
        val value: Money = 0.0,
        val lowStock: Long = 0,
        val expiringSoon: Long = 0,
    )

    @Serializable
    data class Customers(
        val count: Long = 0,
        val totalDebt: Money = 0.0,
    )

    @Serializable
    data class TopSellingDrug(
        val drugId: Long,
        val drugName: String,
        val quantity: Long,
        val revenue: Money,
    )
}

// ── Reports ─────────────────────────────────────────────────────────────────

@Serializable
data class DailyProfitResponse(
    val date: String? = null,
    val salesCount: Long = 0,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
)

@Serializable
data class WeeklyProfitResponse(
    val from: String? = null,
    val to: String? = null,
    val salesCount: Long = 0,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
)

@Serializable
data class MonthlyProfitResponse(
    val year: Int = 0,
    val month: Int = 0,
    val from: String? = null,
    val to: String? = null,
    val salesCount: Long = 0,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
    val averageSaleValue: Money = 0.0,
)

@Serializable
data class ProfitReportResponse(
    val from: String? = null,
    val to: String? = null,
    val salesCount: Long = 0,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
)

@Serializable
data class ProfitDetailResponse(
    val saleId: Long,
    val date: String? = null,
    val drugId: Long? = null,
    val drug: String,
    val quantity: Long,
    val revenue: Money = 0.0,
    val cost: Money = 0.0,
    val profit: Money = 0.0,
    val paymentMethod: PaymentMethod? = null,
    val employee: String? = null,
    val customerId: Long? = null,
    val customer: String? = null,
)

// ── Users ───────────────────────────────────────────────────────────────────

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String,
)

@Serializable
data class UpdateUserRequest(
    val username: String,
    val role: String,
)

@Serializable
data class UpdateUserStatusRequest(
    val enabled: Boolean,
)

@Serializable
data class UpdateUserPasswordRequest(
    val password: String,
)

// ── Settings ────────────────────────────────────────────────────────────────

@Serializable
data class SettingResponse(
    val key: String,
    val value: String,
    val description: String? = null,
    val type: String? = null,
    val updatedBy: Long? = null,
    val updatedAt: String? = null,
)

@Serializable
data class UpdateSettingsRequest(
    val settings: Map<String, String>,
)

// ── Audit ───────────────────────────────────────────────────────────────────

@Serializable
data class AuditLogResponse(
    val id: Long,
    val userId: Long? = null,
    val username: String? = null,
    val action: AuditAction,
    val entityType: String? = null,
    val entityId: Long? = null,
    val description: String? = null,
    val ipAddress: String? = null,
    val createdAt: String? = null,
)
