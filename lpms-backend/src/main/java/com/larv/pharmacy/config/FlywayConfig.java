package com.larv.pharmacy.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Picks the migration directory from the JDBC URL so one build can run on
 * MySQL (local development, tests) and PostgreSQL (cloud deployment):
 * {@code db/migration/mysql} or {@code db/migration/postgresql}.
 *
 * <p>This is an explicit {@code Flyway} bean; Spring Boot's auto-configured
 * bean backs off ({@code @ConditionalOnMissingBean}).</p>
 */
@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource, @Value("${spring.datasource.url}") String url) {
        boolean postgres = url != null && url.startsWith("jdbc:postgresql");
        FluentConfiguration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .locations(postgres
                        ? "classpath:db/migration/postgresql"
                        : "classpath:db/migration/mysql")
                .validateOnMigrate(true);
        return new Flyway(configuration);
    }
}
