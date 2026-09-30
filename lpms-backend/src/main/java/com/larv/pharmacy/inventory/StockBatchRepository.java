package com.larv.pharmacy.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /**
     * All remaining batches of a drug in first-expiry-first-out order.
     * Callers must hold the drug row lock (all stock writers acquire it), which
     * serializes access to these rows.
     */
    @Query("""
            select b from StockBatch b
            where b.drug.id = :drugId and b.remainingQuantity > 0
            order by (case when b.expirationDate is null then 1 else 0 end), b.expirationDate asc, b.id asc
            """)
    List<StockBatch> findRemainingByDrug(@Param("drugId") Long drugId);

    @Query("select b from StockBatch b join fetch b.drug where b.drug.id = :drugId "
            + "order by (case when b.expirationDate is null then 1 else 0 end), b.expirationDate asc, b.receivedAt asc")
    List<StockBatch> findAllByDrugWithDrug(@Param("drugId") Long drugId);

    @Query("select coalesce(sum(b.remainingQuantity), 0) from StockBatch b where b.drug.id = :drugId")
    int sumRemainingByDrug(@Param("drugId") Long drugId);

    /**
     * Inventory value grouped by drug. Ordered by drug name: the aggregate
     * projection cannot accept Sort property names from the request, so the
     * service passes an unsorted pageable (page/size only).
     */
    @Query("""
            select new com.larv.pharmacy.inventory.ValuationRow(b.drug.id, d.name,
                    sum(b.remainingQuantity), sum(b.remainingQuantity * b.unitPurchasePrice))
            from StockBatch b join b.drug d
            where (:search is null or lower(d.name) like :search or lower(d.genericName) like :search)
            group by b.drug.id, d.name
            order by d.name asc
            """)
    Page<ValuationRow> valuationByDrug(@Param("search") String search, Pageable pageable);

    @Query("""
            select new com.larv.pharmacy.inventory.ValuationRow(b.drug.id, d.name,
                    sum(b.remainingQuantity), sum(b.remainingQuantity * b.unitPurchasePrice))
            from StockBatch b join b.drug d
            where b.drug.id = :drugId
            group by b.drug.id, d.name
            """)
    List<ValuationRow> valuationForDrug(@Param("drugId") Long drugId);

    @Query("""
            select new com.larv.pharmacy.inventory.StockBatchRow(b, d.name)
            from StockBatch b join b.drug d
            where (:drugId is null or b.drug.id = :drugId)
              and (:supplier is null or lower(b.supplier) like :supplier)
              and (:search is null or lower(d.name) like :search or lower(d.genericName) like :search)
            """)
    Page<StockBatchRow> search(@Param("drugId") Long drugId,
                               @Param("supplier") String supplier,
                               @Param("search") String search,
                               Pageable pageable);

    @Query("""
            select new com.larv.pharmacy.inventory.StockBatchRow(b, d.name)
            from StockBatch b join b.drug d
            where (:drugId is null or b.drug.id = :drugId)
              and (:supplier is null or lower(b.supplier) like :supplier)
              and (:search is null or lower(d.name) like :search or lower(d.genericName) like :search)
              and b.expirationDate is not null
              and b.expirationDate >= :today and b.expirationDate <= :until
            """)
    Page<StockBatchRow> expiringBetween(@Param("drugId") Long drugId,
                                        @Param("supplier") String supplier,
                                        @Param("search") String search,
                                        @Param("today") LocalDate today,
                                        @Param("until") LocalDate until,
                                        Pageable pageable);

    @Query("""
            select new com.larv.pharmacy.inventory.StockBatchRow(b, d.name)
            from StockBatch b join b.drug d
            where (:drugId is null or b.drug.id = :drugId)
              and (:supplier is null or lower(b.supplier) like :supplier)
              and (:search is null or lower(d.name) like :search or lower(d.genericName) like :search)
              and b.expirationDate is not null
              and b.expirationDate < :today
            """)
    Page<StockBatchRow> expired(@Param("drugId") Long drugId,
                                @Param("supplier") String supplier,
                                @Param("search") String search,
                                @Param("today") LocalDate today,
                                Pageable pageable);

    @Query("""
            select count(b) from StockBatch b
            where b.remainingQuantity > 0 and b.expirationDate is not null
              and b.expirationDate >= :today and b.expirationDate <= :until
            """)
    long countExpiringBetween(@Param("today") LocalDate today, @Param("until") LocalDate until);

    /** Total value of all remaining stock (sum of remaining quantity x purchase price). */
    @Query("select coalesce(sum(b.remainingQuantity * b.unitPurchasePrice), 0) from StockBatch b")
    java.math.BigDecimal totalInventoryValue();
}
