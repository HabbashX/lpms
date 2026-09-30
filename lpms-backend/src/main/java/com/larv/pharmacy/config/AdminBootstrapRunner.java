package com.larv.pharmacy.config;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.exception.UsernameAlreadyExistsException;
import com.larv.pharmacy.security.PasswordPolicy;
import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator account at startup when none exists.
 *
 * <p>Driven by {@code ADMIN_USERNAME} and {@code ADMIN_INITIAL_PASSWORD}. The
 * password must satisfy {@link PasswordPolicy}; the new admin is forced to
 * change it on first login. The password itself is never logged. If no admin
 * exists and no initial password is configured the application still starts,
 * but no one can sign in until an operator sets the variable and restarts.</p>
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String adminUsername;
    private final String adminInitialPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                AuditService auditService,
                                @Value("${app.bootstrap.admin-username:admin}") String adminUsername,
                                @Value("${app.bootstrap.admin-initial-password:}") String adminInitialPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.adminUsername = adminUsername;
        this.adminInitialPassword = adminInitialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        String username = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();

        if (adminInitialPassword == null || adminInitialPassword.isBlank()) {
            log.warn("No administrator exists and no initial password was provided. "
                    + "Set ADMIN_INITIAL_PASSWORD (and optionally ADMIN_USERNAME), then restart "
                    + "to create the first administrator.");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            log.error("No administrator exists, but user '{}' already exists and cannot be promoted. "
                    + "Set ADMIN_USERNAME to a free username and restart.", username);
            return;
        }

        try {
            PasswordPolicy.validate(adminInitialPassword, username);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "ADMIN_INITIAL_PASSWORD does not meet the password policy: " + ex.getMessage(), ex);
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(adminInitialPassword));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        admin.setFailedLoginAttempts(0);

        try {
            userRepository.save(admin);
        } catch (UsernameAlreadyExistsException ex) {
            log.error("Could not create the initial administrator '{}': username is taken.", username);
            return;
        }

        auditService.record(AuditAction.CREATE_USER, "User", admin.getId(),
                "Bootstrap initial administrator '" + username + "' (password change required on first login)");

        log.info("Initial administrator '{}' created. A password change is required on first login.", username);
    }
}
