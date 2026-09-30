package com.larv.pharmacy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        Cors cors,
        Bootstrap bootstrap) {

    public record Cors(String allowedOrigins) {
        public List<String> originList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return List.of(allowedOrigins.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Bootstrap(String adminUsername, String adminInitialPassword) {
    }
}
