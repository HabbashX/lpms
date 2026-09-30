package com.larv.pharmacy.audit;

import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Read-only audit trail (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "action", "id");

    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuditController(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "Search audit log entries",
            description = "Filter by action, user, entity type and date range. Sorted by createdAt desc by default.")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {

        Pageable sanitized = PaginationUtil.sanitize(pageable, SORTABLE, "createdAt", Sort.Direction.DESC);
        Page<AuditLog> page = auditService.search(action, userId, entityType, from, to, sanitized);

        Map<Long, String> usernames = userRepository.findAllById(
                        page.getContent().stream().map(AuditLog::getUserId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.larv.pharmacy.user.User::getId, com.larv.pharmacy.user.User::getUsername, (a, b) -> a));

        return PageResponse.from(page, entry -> new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getUserId() == null ? null : usernames.get(entry.getUserId()),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDescription(),
                entry.getIpAddress(),
                entry.getCreatedAt()));
    }
}
