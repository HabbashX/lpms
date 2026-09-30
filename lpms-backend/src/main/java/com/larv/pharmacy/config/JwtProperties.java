package com.larv.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationSeconds) {

    public Duration expiration() {
        return Duration.ofSeconds(expirationSeconds <= 0 ? 3600 : expirationSeconds);
    }
}
