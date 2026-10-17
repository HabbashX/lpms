package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;
import androidx.room.Update;

import java.math.BigDecimal;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Local data access for offline mode.
 *
 * <p>Deliberately synchronous internals with Rx wrappers: the seeder runs inside one
 * transaction, and "is the demo data there yet" has to be answerable before the first
 * screen draws. Repositories stay on {@link Single}/{@link Flowable} so callers are
 * unchanged between modes.</p>
 */
@Dao
@TypeConverters(Converters.class)
public interface LpmsDao {

    @Query("SELECT * FROM drugs ORDER BY name COLLATE NOCASE")
    Flowable<List<DrugEntity>> observeDrugs();

    @Query("SELECT * FROM drugs WHERE active = 1 ORDER BY name COLLATE NOCASE")
    Flowable<List<DrugEntity>> observeActiveDrugs();

    @Query("SELECT * FROM drugs WHERE id = :id")
    Maybe<DrugEntity> drug(long id);

    @Query("SELECT * FROM drugs WHERE barcode = :barcode LIMIT 1")
    Maybe<DrugEntity> drugByBarcode(String barcode);

    @Query("SELECT * FROM drugs WHERE name LIKE '%' || :q || '%' "
            + "OR generic_name LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE LIMIT :limit")
    Single<List<DrugEntity>> searchDrugs(String q, int limit);

    @Query("SELECT COUNT(*) FROM drugs WHERE category_id = :categoryId")
    int drugCountForCategory(long categoryId);

    @Query("SELECT * FROM drugs WHERE category_id = :categoryId AND active = 1 "
            + "ORDER BY name COLLATE NOCASE")
    Single<List<DrugEntity>> drugsInCategory(long categoryId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDrug(DrugEntity drug);

    @Update
    void updateDrug(DrugEntity drug);

    @Query("UPDATE drugs SET current_quantity = current_quantity - :qty, "
            + "average_cost = :averageCost WHERE id = :id")
    void consumeDrug(long id, int qty, @Nullable BigDecimal averageCost);

    @Query("UPDATE drugs SET current_quantity = current_quantity + :qty, "
            + "selling_price = COALESCE(:sellingPrice, selling_price), "
            + "average_cost = :averageCost WHERE id = :id")
    void restockDrug(long id, int qty, @Nullable BigDecimal sellingPrice,
                     @Nullable BigDecimal averageCost);

    // ----------------------------------------------------------- categories

    @Query("SELECT c.*, (SELECT COUNT(*) FROM drugs d WHERE d.category_id = c.id) AS drug_count "
            + "FROM categories c ORDER BY c.name COLLATE NOCASE")
    Flowable<List<CategoryEntity>> observeCategories();

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    List<CategoryEntity> allCategories();

    @Insert
    long insertCategory(CategoryEntity category);

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    void renameCategory(long id, String name);

    @Query("DELETE FROM categories WHERE id = :id")
    int deleteCategory(long id);

    // ------------------------------------------------------------ customers

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    Flowable<List<CustomerEntity>> observeCustomers();

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :q || '%' "
            + "OR phone LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE LIMIT :limit")
    Single<List<CustomerEntity>> searchCustomers(String q, int limit);

    @Query("SELECT * FROM customers WHERE id = :id")
    Maybe<CustomerEntity> customer(long id);

    @Insert
    long insertCustomer(CustomerEntity customer);

    @Update
    void updateCustomer(CustomerEntity customer);

    @Query("UPDATE customers SET balance = balance + :delta WHERE id = :id")
    void adjustCustomerBalance(long id, @NonNull BigDecimal delta);

    // ---------------------------------------------------------------- sales

    @Insert
    long insertSale(SaleEntity sale);

    @Insert
    void insertSaleItems(List<SaleItemEntity> items);

    @Query("SELECT * FROM sales ORDER BY id DESC")
    Flowable<List<SaleEntity>> observeSales();

    @Query("SELECT * FROM sales WHERE id = :id")
    Maybe<SaleEntity> sale(long id);

    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId")
    Single<List<SaleItemEntity>> saleItems(long saleId);

    @Query("SELECT COALESCE(SUM(profit_total), 0) FROM sales WHERE created_at LIKE :dayPrefix"
            + " || '%'")
    BigDecimal profitOnDay(String dayPrefix);

    @Query("SELECT COALESCE(SUM(cost_total), 0) FROM sales")
    BigDecimal totalCostOfStockSold();

    // --------------------------------------------------------------- batches

    @Insert
    long insertBatch(StockBatchEntity batch);

    @Update
    void updateBatch(StockBatchEntity batch);

    @Query("SELECT * FROM stock_batches WHERE drug_id = :drugId ORDER BY id")
    List<StockBatchEntity> batchesForDrug(long drugId);

    @Query("SELECT * FROM stock_batches ORDER BY id DESC LIMIT :limit")
    Single<List<StockBatchEntity>> recentBatches(int limit);

    // ------------------------------------------------------------ teardown

    @Query("DELETE FROM drugs")
    void clearDrugs();

    @Query("DELETE FROM categories")
    void clearCategories();

    @Query("DELETE FROM customers")
    void clearCustomers();

    @Query("DELETE FROM sales")
    void clearSales();

    @Query("DELETE FROM stock_batches")
    void clearBatches();

    /** Single call used by the seeder and by tests to reset to a known state. */
    @NonNull
    default Completable clearAll() {
        return Completable.fromAction(() -> {
            clearBatches();
            clearSales();
            clearDrugs();
            clearCategories();
            clearCustomers();
        });
    }

    /** True once the demo data exists; avoids re-seeding on every launch. */
    @Query("SELECT COUNT(*) FROM drugs")
    int drugCount();
}
