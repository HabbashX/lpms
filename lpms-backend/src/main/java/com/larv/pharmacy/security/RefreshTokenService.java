package com.larv.pharmacy.security;

import com.larv.pharmacy.common.exception.PharmacyException;
import com.larv.pharmacy.config.JwtProperties;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes opaque refresh tokens.
 *
 * <ul>
 *   <li>Tokens are 256-bit random values; only their SHA-256 hash is persisted.</li>
 *   <li>Every refresh rotates the token: the presented one is revoked and a new one issued.</li>
 *   <li>Presenting an already-revoked token is treated as theft: all of that user's
 *       refresh tokens are revoked.</li>
 *   <li>Tokens issued before the user's {@code tokenNotBefore} (password change/reset,
 *       disable) or for disabled users are rejected.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
                               JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public Duration lifetime() {
        return properties.refreshExpiration();
    }

    /** Creates and stores a new refresh token; returns the raw value (shown to the client once). */
    @Transactional
    public String issue(User user) {
        Instant now = clock.instant();
        String raw = newRawToken();
        repository.save(new RefreshToken(user.getId(), hash(raw), now, now.plus(properties.refreshExpiration())));
        return raw;
    }

    /** Validates and consumes a refresh token, returning the owner so a new pair can be issued. */
    @Transactional(noRollbackFor = PharmacyException.class)
    public User consume(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = repository.findWithLockByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        if (token.getRevokedAt() != null) {
            // Reuse of a rotated/revoked token: assume compromise, kill every session.
            repository.revokeAllForUser(token.getUserId(), now);
            throw invalid();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        User user = userRepository.findById(token.getUserId()).orElseThrow(RefreshTokenService::invalid);
        if (!user.isEnabled()
                || (user.getTokenNotBefore() != null && token.getCreatedAt().isBefore(user.getTokenNotBefore()))) {
            token.setRevokedAt(now);
            throw invalid();
        }
        token.setRevokedAt(now);
        return user;
    }

    /** Best-effort revocation of one token (logout). Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findWithLockByTokenHash(hash(rawToken.trim()))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void purgeExpired() {
        repository.deleteByExpiresAtBefore(clock.instant());
    }

    private static PharmacyException invalid() {
        return new InvalidRefreshTokenException();
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static final class InvalidRefreshTokenException extends PharmacyException {
        InvalidRefreshTokenException() {
            super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
    }
}
