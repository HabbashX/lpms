package com.larv.pharmacy.audit;

import com.larv.pharmacy.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Writes audit records. Never logs or persists secrets (passwords, tokens).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private final ZoneId zone;

    public AuditService(AuditRepository auditRepository, java.time.Clock clock) {
        this.auditRepository = auditRepository;
        this.zone = clock.getZone();
    }

    /**
     * Records an action for the currently authenticated user
     * (or a system actor when there is no security context).
     */
    public void record(AuditAction action, String entityType, Long entityId, String description) {
        record(currentUserId().orElse(null), action, entityType, entityId, description, currentIp());
    }

    /** Records an action on behalf of an explicit user (e.g. failed login). */
    public void record(Long userId, AuditAction action, String entityType, Long entityId,
                       String description, String ipAddress) {
        try {
            String trimmedDescription = truncate(description, 500);
            auditRepository.save(new AuditLog(userId, action, entityType, entityId, trimmedDescription, ipAddress));
        } catch (Exception e) {
            // Auditing must never break the business operation it describes,
            // but the failure is always surfaced in the logs.
            log.error("Failed to write audit record action={} entityType={} entityId={}",
                    action, entityType, entityId, e);
        }
    }

    public Page<AuditLog> search(AuditAction action, Long userId, String entityType,
                                 LocalDate from, LocalDate to, Pageable pageable) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return auditRepository.search(action, userId, entityType, fromInstant, toInstant, pageable);
    }

    public static Optional<Long> currentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                return Optional.of(principal.id());
            }
        } catch (Exception ignored) {
            // no security context (e.g. system bootstrap)
        }
        return Optional.empty();
    }

    public static String currentIp() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // outside of a web request
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
