package com.lpms.data.remote

import com.lpms.data.remote.dto.AuditLogResponse
import com.lpms.data.remote.dto.ChangePasswordRequest
import com.lpms.data.remote.dto.CreateAdjustmentRequest
import com.lpms.data.remote.dto.CreateCustomerRequest
import com.lpms.data.remote.dto.CreateDrugRequest
import com.lpms.data.remote.dto.CreatePaymentRequest
import com.lpms.data.remote.dto.CreatePurchaseRequest
import com.lpms.data.remote.dto.CreateRefundRequest
import com.lpms.data.remote.dto.CreateSaleItemRequest
import com.lpms.data.remote.dto.CreateSaleRequest
import com.lpms.data.remote.dto.CreateUserRequest
import com.lpms.data.remote.dto.CustomerAccountResponse
import com.lpms.data.remote.dto.CustomerResponse
import com.lpms.data.remote.dto.DailyProfitResponse
import com.lpms.data.remote.dto.DashboardResponse
import com.lpms.data.remote.dto.DosageForm
import com.lpms.data.remote.dto.DrugResponse
import com.lpms.data.remote.dto.DrugValuationResponse
import com.lpms.data.remote.dto.ExpiringBatchResponse
import com.lpms.data.remote.dto.LoginRequest
import com.lpms.data.remote.dto.LoginResponse
import com.lpms.data.remote.dto.LowStockDrugResponse
import com.lpms.data.remote.dto.MonthlyProfitResponse
import com.lpms.data.remote.dto.PageResponse
import com.lpms.data.remote.dto.PaymentMethod
import com.lpms.data.remote.dto.PaymentResponse
import com.lpms.data.remote.dto.ProfitDetailResponse
import com.lpms.data.remote.dto.ProfitReportResponse
import com.lpms.data.remote.dto.RefundResponse
import com.lpms.data.remote.dto.SaleResponse
import com.lpms.data.remote.dto.SaleStatus
import com.lpms.data.remote.dto.SettingResponse
import com.lpms.data.remote.dto.StockBatchResponse
import com.lpms.data.remote.dto.TransactionResponse
import com.lpms.data.remote.dto.UpdateCustomerRequest
import com.lpms.data.remote.dto.UpdateDrugRequest
import com.lpms.data.remote.dto.UpdateSettingsRequest
import com.lpms.data.remote.dto.UpdateUserPasswordRequest
import com.lpms.data.remote.dto.UpdateUserRequest
import com.lpms.data.remote.dto.UpdateUserStatusRequest
import com.lpms.data.remote.dto.UserResponse
import com.lpms.data.remote.dto.WeeklyProfitResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The whole backend contract, verified against the controllers in
 * `lpms-backend/.../com/larv/pharmacy`.
 *
 * Base URL is `BuildConfig.BASE_URL + BuildConfig.API_PREFIX + "/"`, so every
 * path here is relative to `/api/v1/`. Optional query parameters are nullable
 * — Retrofit omits them entirely rather than sending `null`.
 *
 * Paging: every list endpoint takes Spring's `Pageable`, i.e. `page` (0-based),
 * `size` and `sort` (`field,asc|desc`).
 */
interface LpmsApi {

    // ── Auth ────────────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/me")
    suspend fun me(): UserResponse

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    // ── Dashboard ───────────────────────────────────────────────────────────

    @GET("dashboard")
    suspend fun dashboard(): DashboardResponse

    // ── Drugs ───────────────────────────────────────────────────────────────

    @GET("drugs")
    suspend fun listDrugs(
        @Query("search") search: String? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("dosageForm") dosageForm: DosageForm? = null,
        @Query("active") active: Boolean? = null,
        @Query("lowStock") lowStock: Boolean? = null,
        @Query("barcode") barcode: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "name,asc",
    ): PageResponse<DrugResponse>

    @GET("drugs/barcode/{barcode}")
    suspend fun drugByBarcode(@Path("barcode") barcode: String): DrugResponse

    @GET("drugs/{id}")
    suspend fun getDrug(@Path("id") id: Long): DrugResponse

    @POST("drugs")
    suspend fun createDrug(@Body request: CreateDrugRequest): DrugResponse

    @PUT("drugs/{id}")
    suspend fun updateDrug(@Path("id") id: Long, @Body request: UpdateDrugRequest): DrugResponse

    @DELETE("drugs/{id}")
    suspend fun deleteDrug(@Path("id") id: Long): Unit

    // ── Customers ───────────────────────────────────────────────────────────

    @GET("customers")
    suspend fun listCustomers(
        @Query("search") search: String? = null,
        @Query("active") active: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "name,asc",
    ): PageResponse<CustomerResponse>

    @GET("customers/{id}")
    suspend fun getCustomer(@Path("id") id: Long): CustomerResponse

    @POST("customers")
    suspend fun createCustomer(@Body request: CreateCustomerRequest): CustomerResponse

    @PUT("customers/{id}")
    suspend fun updateCustomer(
        @Path("id") id: Long,
        @Body request: UpdateCustomerRequest,
    ): CustomerResponse

    @DELETE("customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Long): Unit

    @GET("customers/{id}/account")
    suspend fun customerAccount(
        @Path("id") id: Long,
        @Query("transactionPage") transactionPage: Int = 0,
        @Query("transactionSize") transactionSize: Int = 20,
    ): CustomerAccountResponse

    @POST("customers/{id}/payments")
    suspend fun createPayment(
        @Path("id") id: Long,
        @Body request: CreatePaymentRequest,
    ): PaymentResponse

    @POST("customers/{id}/adjustments")
    suspend fun createAdjustment(
        @Path("id") id: Long,
        @Body request: CreateAdjustmentRequest,
    ): TransactionResponse

    // ── Sales ───────────────────────────────────────────────────────────────

    @POST("sales")
    suspend fun createSale(@Body request: CreateSaleRequest): SaleResponse

    @GET("sales")
    suspend fun listSales(
        @Query("customerId") customerId: Long? = null,
        @Query("paymentMethod") paymentMethod: PaymentMethod? = null,
        @Query("status") status: SaleStatus? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "createdAt,desc",
    ): PageResponse<SaleResponse>

    @GET("sales/{id}")
    suspend fun getSale(@Path("id") id: Long): SaleResponse

    @POST("sales/{id}/refund")
    suspend fun refundSale(
        @Path("id") id: Long,
        @Body request: CreateRefundRequest,
    ): RefundResponse

    // ── Inventory ───────────────────────────────────────────────────────────

    @POST("inventory/purchases")
    suspend fun purchaseStock(@Body request: CreatePurchaseRequest): StockBatchResponse

    @GET("inventory/batches")
    suspend fun listBatches(
        @Query("drugId") drugId: Long? = null,
        @Query("supplier") supplier: String? = null,
        @Query("search") search: String? = null,
        @Query("expired") expired: Boolean? = null,
        @Query("expiringDays") expiringDays: Int? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "expirationDate,asc",
    ): PageResponse<StockBatchResponse>

    @GET("inventory/drugs/{drugId}")
    suspend fun batchesForDrug(@Path("drugId") drugId: Long): List<StockBatchResponse>

    @GET("inventory/valuation")
    suspend fun valuation(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageResponse<DrugValuationResponse>

    @GET("inventory/low-stock")
    suspend fun lowStock(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageResponse<LowStockDrugResponse>

    @GET("inventory/expiring")
    suspend fun expiring(
        @Query("days") days: Int? = null,
        @Query("expired") expired: Boolean? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageResponse<ExpiringBatchResponse>

    // ── Reports ─────────────────────────────────────────────────────────────

    @GET("reports/profit/daily")
    suspend fun dailyProfit(@Query("date") date: String? = null): DailyProfitResponse

    @GET("reports/profit/weekly")
    suspend fun weeklyProfit(@Query("date") date: String? = null): WeeklyProfitResponse

    @GET("reports/profit/monthly")
    suspend fun monthlyProfit(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
    ): MonthlyProfitResponse

    @GET("reports/profit")
    suspend fun profitReport(
        @Query("preset") preset: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ProfitReportResponse

    @GET("reports/profit/details")
    suspend fun profitDetails(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("drug") drug: Long? = null,
        @Query("category") category: Long? = null,
        @Query("employee") employee: Long? = null,
        @Query("paymentMethod") paymentMethod: PaymentMethod? = null,
        @Query("customer") customer: Long? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageResponse<ProfitDetailResponse>

    // ── Users ───────────────────────────────────────────────────────────────

    @GET("users")
    suspend fun listUsers(
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("enabled") enabled: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "username,asc",
    ): PageResponse<UserResponse>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): UserResponse

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): UserResponse

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body request: UpdateUserRequest): UserResponse

    @PATCH("users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") id: Long,
        @Body request: UpdateUserStatusRequest,
    ): UserResponse

    @PATCH("users/{id}/password")
    suspend fun resetUserPassword(
        @Path("id") id: Long,
        @Body request: UpdateUserPasswordRequest,
    ): UserResponse

    // ── Settings ────────────────────────────────────────────────────────────

    @GET("settings")
    suspend fun getSettings(): List<SettingResponse>

    @PUT("settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): List<SettingResponse>

    // ── Audit ───────────────────────────────────────────────────────────────

    @GET("audit")
    suspend fun searchAudit(
        @Query("action") action: String? = null,
        @Query("userId") userId: Long? = null,
        @Query("entityType") entityType: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "createdAt,desc",
    ): PageResponse<AuditLogResponse>
}
