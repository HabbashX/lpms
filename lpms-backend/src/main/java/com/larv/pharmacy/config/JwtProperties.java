package com.larv.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationSeconds, long refreshExpirationSeconds) {

    public Duration expiration() {
        return Duration.ofSeconds(expirationSeconds <= 0 ? 3600 : expirationSeconds);
    }

    /** Refresh token lifetime; defaults to 14 days. */
    public Duration refreshExpiration() {
        return Duration.ofSeconds(refreshExpirationSeconds <= 0 ? 14L * 24 * 3600 : refreshExpirationSeconds);
    }
}
