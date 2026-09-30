package com.larv.pharmacy.audit;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        String entityType,
        Long entityId,
        String description,
        String ipAddress,
        Instant createdAt) {
}
