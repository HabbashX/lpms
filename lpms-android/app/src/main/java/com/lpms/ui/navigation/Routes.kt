package com.lpms.ui.navigation

/**
 * Route table.
 *
 * Everything user-visible in the app is addressable by one of these strings;
 * screens never build navigation keys by hand.
 */
object Routes {

    // ── Auth ────────────────────────────────────────────────────────────────
    const val Login = "login"
    const val ChangePassword = "change-password"

    // ── Shell ───────────────────────────────────────────────────────────────
    const val Main = "main"
    const val Home = "home"
    const val More = "more"

    // ── Sales ───────────────────────────────────────────────────────────────
    const val Pos = "pos"
    const val Sales = "sales"
    const val SaleDetail = "sale/{saleId}"
    const val ArgSaleId = "saleId"
    // ── Catalogue ───────────────────────────────────────────────────────────
    const val Drugs = "drugs"
    const val DrugEdit = "drug/{drugId}"
    const val ArgDrugId = "drugId"
    const val NewDrugId = "new"

    // ── Customers ───────────────────────────────────────────────────────────
    const val Customers = "customers"
    const val CustomerDetail = "customer/{customerId}"
    const val ArgCustomerId = "customerId"
    const val NewCustomerId = "new"

    // ── Operations ──────────────────────────────────────────────────────────
    const val Inventory = "inventory"
    const val Reports = "reports"

    // ── Administration ──────────────────────────────────────────────────────
    const val Users = "users"
    const val Audit = "audit"
    const val Settings = "settings"

    fun saleDetail(saleLocalId: String) = "sale/$saleLocalId"
    fun drugEdit(drugId: Long) = "drug?drugId=$drugId"
    fun newCustomer() = "customer/$NewCustomerId"
    fun customerDetail(customerId: String) = "customer/$customerId"
}
