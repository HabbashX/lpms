package com.larv.pharmacy;

import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Guards the integration tests: when the local MySQL instance (or the
 * {@code lpms_test} database) is unreachable the tests are skipped instead of
 * failing the build with a Spring context error.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** Call from {@code @BeforeAll} (before the Spring context is created). */
    public static void assumeAvailable() {
        Assumptions.assumeTrue(isReachable(),
                "Local MySQL with lpms_test is not reachable - skipping integration tests");
    }

    public static boolean isReachable() {
        String url = System.getenv().getOrDefault("DB_URL",
                "jdbc:mysql://localhost:3306/lpms_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&characterEncoding=UTF-8");
        String user = System.getenv().getOrDefault("DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "root");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }
}
