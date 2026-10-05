package com.larv.pharmacy.auth;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.auth.dto.ChangePasswordRequest;
import com.larv.pharmacy.auth.dto.LoginRequest;
import com.larv.pharmacy.auth.dto.LoginResponse;
import com.larv.pharmacy.auth.dto.RefreshRequest;
import com.larv.pharmacy.common.exception.AccountDisabledException;
import com.larv.pharmacy.common.exception.AccountLockedException;
import com.larv.pharmacy.common.exception.InvalidCredentialsException;
import com.larv.pharmacy.common.exception.PharmacyException;
import com.larv.pharmacy.common.exception.InvalidRequestException;
import com.larv.pharmacy.security.JwtService;
import com.larv.pharmacy.security.RefreshTokenService;
import com.larv.pharmacy.security.PasswordPolicy;
import com.larv.pharmacy.security.TokenRevocationService;
import com.larv.pharmacy.security.UserPrincipal;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Authentication flow: login (with brute-force protection), logout (token
 * revocation), current-user lookup and self-service password change.
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenRevocationService tokenRevocationService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter,
                       AuditService auditService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenRevocationService = tokenRevocationService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String ip = AuditService.currentIp();
        rateLimiter.check(ip, request.username());

        User user = userRepository.findWithLockByUsername(request.username())
                .orElse(null);

        if (user == null) {
            auditService.record(null, AuditAction.LOGIN, "User", null,
                    "Failed login attempt for unknown user '" + request.username() + "'", ip);
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        if (!user.isEnabled()) {
            auditService.record(user.getId(), AuditAction.LOGIN, "User", user.getId(),
                    "Login attempt for disabled user '" + user.getUsername() + "'", ip);
            throw new AccountDisabledException();
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()).getSeconds() + 1);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);
            auditService.record(user.getId(), AuditAction.LOGIN, "User", user.getId(),
                    "Failed login attempt for user '" + user.getUsername() + "'", ip);
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                throw new AccountLockedException(LOCK_DURATION.getSeconds());
            }
            throw new InvalidCredentialsException();
        }

        // success
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);
        rateLimiter.success(ip, request.username());

        auditService.record(user.getId(), AuditAction.LOGIN, "User", user.getId(),
                "User '" + user.getUsername() + "' logged in", ip);
        return buildLoginResponse(user);
    }

    /** Rotates the refresh token and issues a new access token. */
    @Transactional(noRollbackFor = PharmacyException.class)
    public LoginResponse refresh(RefreshRequest request) {
        User user = refreshTokenService.consume(request.refreshToken().trim());
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.expirationSeconds(),
                refreshToken,
                refreshTokenService.lifetime().getSeconds(),
                new LoginResponse.UserSummary(user.getId(), user.getUsername(), user.getRole()));
    }

    @Transactional
    public void logout(String authorizationHeader, String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authorizationHeader.substring(7).trim();
        try {
            JwtService.JwtClaims claims = jwtService.parse(token);
            tokenRevocationService.revoke(claims.jti(), claims.expiresAt());
            auditService.record(AuditAction.LOGOUT, "User", claims.userId(),
                    "User '" + claims.username() + "' logged out");
        } catch (Exception ignored) {
            // Logging out with an already invalid token is a no-op.
        }
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = userRepository.findWithLockByUsername(principal.username())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidRequestException("INVALID_CURRENT_PASSWORD",
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidRequestException("PASSWORD_REUSE",
                    "New password must be different from the current password");
        }
        PasswordPolicy.validate(request.newPassword(), user.getUsername());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // Invalidate every token issued before this change.
        user.setTokenNotBefore(clock.instant());
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditService.record(AuditAction.CHANGE_PASSWORD, "User", user.getId(),
                "User '" + user.getUsername() + "' changed their password");
    }
}
