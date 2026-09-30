package com.larv.pharmacy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the migration directory from the JDBC URL so one build can run on
 * MySQL (local development, tests, deployment) or PostgreSQL:
 * {@code db/migration/mysql} or {@code db/migration/postgresql}.
 *
 * <p>Only the location is customized; Spring Boot keeps ownership of the
 * {@code Flyway} bean and its initializer so that the JPA
 * {@code entityManagerFactory} still runs after the migrations have been
 * applied.</p>
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer migrationLocationCustomizer(@Value("${spring.datasource.url}") String url) {
        boolean postgres = url != null && url.startsWith("jdbc:postgresql");
        String location = postgres
                ? "classpath:db/migration/postgresql"
                : "classpath:db/migration/mysql";
        return configuration -> configuration.locations(location);
    }
}
