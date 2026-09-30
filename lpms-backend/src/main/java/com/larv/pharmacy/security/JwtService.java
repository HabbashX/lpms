package com.larv.pharmacy.security;

import com.larv.pharmacy.config.JwtProperties;
import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates HS256 JWTs. The secret comes from the environment
 * ({@code JWT_SECRET}) and is validated to be long enough at startup.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters long. Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public long expirationSeconds() {
        return expiration.getSeconds();
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and signature-verifies a raw token.
     *
     * @throws JwtException when the token is invalid, malformed or expired
     */
    public JwtClaims parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtClaims(
                claims.getId(),
                claims.getSubject(),
                claims.get("uid", Long.class),
                Role.valueOf(claims.get("role", String.class)),
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
    }

    public record JwtClaims(String jti, String username, Long userId, Role role,
                            Instant issuedAt, Instant expiresAt) {
    }
}
