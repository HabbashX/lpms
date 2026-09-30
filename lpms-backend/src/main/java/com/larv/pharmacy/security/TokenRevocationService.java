package com.larv.pharmacy.security;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable JWT revocation list used by logout. Revoked token ids (jti) are kept
 * both in the database (survives restarts) and in an in-memory cache that is
 * reloaded on startup, so validation stays a single map lookup per request.
 */
@Service
public class TokenRevocationService {

    private final RevokedTokenRepository repository;
    private final Clock clock;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public TokenRevocationService(RevokedTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PostConstruct
    @Transactional
    public void warmUp() {
        Instant now = clock.instant();
        repository.deleteByExpiresAtBefore(now);
        repository.findAll().forEach(t -> revoked.put(t.getJti(), t.getExpiresAt()));
    }

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null) {
            return;
        }
        Instant expiry = expiresAt == null ? clock.instant().plusSeconds(3600) : expiresAt;
        revoked.put(jti, expiry);
        repository.save(new RevokedToken(jti, expiry, clock.instant()));
    }

    public boolean isRevoked(String jti) {
        Instant expiry = jti == null ? null : revoked.get(jti);
        return expiry != null && expiry.isAfter(clock.instant());
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        repository.deleteByExpiresAtBefore(now);
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
