package com.larv.pharmacy.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findBySaleIdOrderByCreatedAtDesc(Long saleId);

    @Query("select r from Refund r left join fetch r.items where r.id = :id")
    Optional<Refund> findByIdWithItems(@Param("id") Long id);

    long countBySaleId(Long saleId);
}
