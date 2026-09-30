package com.larv.pharmacy.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:userId is null or a.userId = :userId)
              and (:entityType is null or a.entityType = :entityType)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
            """)
    Page<AuditLog> search(@Param("action") AuditAction action,
                          @Param("userId") Long userId,
                          @Param("entityType") String entityType,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
