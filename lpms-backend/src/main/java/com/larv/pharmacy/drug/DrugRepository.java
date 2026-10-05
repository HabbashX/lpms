package com.larv.pharmacy.drug;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DrugRepository extends JpaRepository<Drug, Long>, JpaSpecificationExecutor<Drug> {

    Optional<Drug> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Row lock used to serialize all stock mutations (purchase, sale, refund)
     * for one drug; callers must lock drugs in ascending id order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Drug d where d.id = :id")
    Optional<Drug> findWithLockById(@Param("id") Long id);

    @Query("select d from Drug d join fetch d.category where d.id = :id")
    Optional<Drug> findByIdWithCategory(@Param("id") Long id);

    @Query("select d from Drug d left join fetch d.category")
    List<Drug> findAllWithCategory();

    /** Number of active drugs whose cached quantity is at or below their minimum stock level. */
    @Query("select count(d) from Drug d where d.active = true and d.currentQuantity <= d.minimumStockLevel")
    long countLowStock();

    /** Drugs whose cached quantity is at or below their minimum stock level. */
    @Query("""
            select d from Drug d
            where d.active = true and d.currentQuantity <= d.minimumStockLevel
            and (:search is null
                 or lower(d.name) like lower(concat('%', :search, '%'))
                 or lower(d.genericName) like lower(concat('%', :search, '%')))
            order by d.currentQuantity asc, d.name asc
            """)
    org.springframework.data.domain.Page<Drug> findLowStock(@Param("search") String search,
                                                           org.springframework.data.domain.Pageable pageable);
}
