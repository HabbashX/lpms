package com.larv.pharmacy.user;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.BusinessRuleException;
import com.larv.pharmacy.common.exception.UserNotFoundException;
import com.larv.pharmacy.common.exception.UsernameAlreadyExistsException;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.user.dto.CreateUserRequest;
import com.larv.pharmacy.user.dto.UpdateUserPasswordRequest;
import com.larv.pharmacy.user.dto.UpdateUserRequest;
import com.larv.pharmacy.user.dto.UpdateUserStatusRequest;
import com.larv.pharmacy.user.dto.UserResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Administrator-controlled user management. There is no public registration:
 * only an ADMIN can create, modify, disable or reset users.
 */
@Service
public class UserService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Role role, Boolean enabled, Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable,
                java.util.Set.of("id", "username", "role", "enabled", "createdAt", "lastLoginAt"),
                "id", org.springframework.data.domain.Sort.Direction.ASC);
        Page<User> page = userRepository.findAll(buildSpec(search, role, enabled), sanitized);
        return PageResponse.from(page, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        validatePasswordPolicy(request.password(), username);

        User user = new User();        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);

        auditService.record(AuditAction.CREATE_USER, "User", saved.getId(),
                "Created user '" + username + "' with role " + request.role());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        assertNotSelf(id, "You cannot modify your own account here");

        if (request.username() != null && !request.username().isBlank()) {
            String username = request.username().trim();
            if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
                throw new UsernameAlreadyExistsException(username);
            }
            user.setUsername(username);
        }

        Role oldRole = user.getRole();
        if (request.role() != oldRole) {
            if (oldRole == Role.ADMIN && userRepository.countByRoleAndEnabled(Role.ADMIN, true) <= 1) {
                throw new BusinessRuleException("LAST_ADMIN",
                        "At least one active administrator must remain");
            }
            user.setRole(request.role());
            // Keep existing sessions alive: authorities are re-read from the
            // database on every request, so the new role applies immediately.
        }

        User saved = userRepository.save(user);
        auditService.record(AuditAction.UPDATE_USER, "User", saved.getId(),
                "Updated user '" + saved.getUsername() + "' (role " + oldRole + " -> " + saved.getRole() + ")");
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User user = findOrThrow(id);
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        assertNotSelf(id, "You cannot enable or disable your own account");

        if (!enabled && user.getRole() == Role.ADMIN
                && userRepository.countByRoleAndEnabled(Role.ADMIN, true) <= 1) {
            throw new BusinessRuleException("LAST_ADMIN", "At least one active administrator must remain");
        }

        user.setEnabled(enabled);
        if (enabled) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        } else {
            // Invalidate every outstanding token for the disabled account.
            user.setTokenNotBefore(Instant.now());
        }
        User saved = userRepository.save(user);

        auditService.record(AuditAction.UPDATE_USER, "User", saved.getId(),
                (enabled ? "Enabled" : "Disabled") + " user '" + saved.getUsername() + "'");
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse resetPassword(Long id, UpdateUserPasswordRequest request) {
        User user = findOrThrow(id);
        assertNotSelf(id, "You cannot reset your own password here; use POST /api/v1/auth/change-password");
        validatePasswordPolicy(request.password(), user.getUsername());

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // Force re-login: all previously issued tokens become invalid.
        user.setTokenNotBefore(Instant.now());
        User saved = userRepository.save(user);

        auditService.record(AuditAction.UPDATE_USER, "User", saved.getId(),
                "Reset password for user '" + saved.getUsername() + "'");
        return UserResponse.from(saved);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void assertNotSelf(Long id, String message) {
        AuditService.currentUserId().ifPresent(actorId -> {
            if (actorId.equals(id)) {
                throw new BusinessRuleException("SELF_MODIFICATION_FORBIDDEN", message);
            }
        });
    }

    private void validatePasswordPolicy(String password, String username) {
        com.larv.pharmacy.security.PasswordPolicy.validate(password, username);
    }

    private Specification<User> buildSpec(String search, Role role, Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + PaginationUtil.escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("username")), pattern, '\\'));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public int maxFailedLoginAttempts() {
        return MAX_FAILED_LOGIN_ATTEMPTS;
    }
}
