package com.larv.pharmacy.security;

import com.larv.pharmacy.config.JwtProperties;
import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars-0123456789";
    private static final String OTHER_SECRET = "a-different-secret-also-long-enough-0987654321abcdef";
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        user.setRole(Role.PHARMACIST);
        return user;
    }

    private JwtService service(String secret, Clock clock) {
        return new JwtService(new JwtProperties(secret, 3600), clock);
    }

    @Test
    void generatesAndParsesToken() {
        JwtService service = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        String token = service.generateToken(user());
        JwtService.JwtClaims claims = service.parse(token);

        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.username()).isEqualTo("tester");
        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo(Role.PHARMACIST);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService issuer = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuer.generateToken(user());

        JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuer.generateToken(user());

        JwtService other = service(OTHER_SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        JwtService service = service(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToStartWithShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
