package com.larv.pharmacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security layer against the real filter chain: authentication, role-based
 * authorization, token revocation, forced password change and lockout.
 *
 * <p>Users are seeded directly through the repository with distinct usernames
 * so the in-memory login rate limiter (per ip|username) never interferes
 * between tests. Everything rolls back after each test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    private static final String ADMIN_PASSWORD = "Lpms#Test2026";
    private static final String EMPLOYEE_PASSWORD = "Employee#Test2026";
    private static final String PHARMACIST_PASSWORD = "Pharmacist#Test2026";

    @BeforeAll
    static void requireDatabase() {
        TestDatabase.assumeAvailable();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        upsert("it.admin", ADMIN_PASSWORD, Role.ADMIN, false);
        upsert("it.emp", EMPLOYEE_PASSWORD, Role.EMPLOYEE, false);
        upsert("it.pharm", PHARMACIST_PASSWORD, Role.PHARMACIST, false);
        upsert("it.force", "Temp#Pass2026", Role.PHARMACIST, true);
        upsert("it.lock", "Lock#Pass2026", Role.EMPLOYEE, false);
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/drugs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it.emp\",\"password\":\"Wrong#Pass9\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginMeAndLogoutRoundTrip() throws Exception {
        String token = login("it.emp", EMPLOYEE_PASSWORD);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it.emp"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // revoked token must not work anymore
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeIsForbiddenOnAdminEndpoints() throws Exception {
        String token = login("it.emp", EMPLOYEE_PASSWORD);

        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/reports/profit/daily").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/drugs").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"dosageForm\":\"TABLET\",\"minimumStockLevel\":0}"))
                .andExpect(status().isForbidden());

        // authenticated reads and sales stay available
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sales").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        String token = login("it.admin", ADMIN_PASSWORD);

        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/v1/reports/profit/daily").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").exists());
        mvc.perform(get("/api/v1/audit").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/drugs").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Sec Drug\",\"barcode\":\"IT-SEC-" + unique()
                                + "\",\"category\":\"IT-Sec\",\"dosageForm\":\"TABLET\","
                                + "\"minimumStockLevel\":0}"))
                .andExpect(status().isCreated());
    }

    @Test
    void pharmacistCanReadReportsButNotAdminOnlyEndpoints() throws Exception {
        String token = login("it.pharm", PHARMACIST_PASSWORD);

        // reports are available to pharmacists
        for (String report : new String[] {
                "/api/v1/reports/profit/daily",
                "/api/v1/reports/profit/weekly",
                "/api/v1/reports/profit/monthly",
                "/api/v1/reports/profit?preset=last30days",
                "/api/v1/reports/profit/details?page=0&size=5" }) {
            mvc.perform(get(report).header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }

        // everything that manages the system stays admin-only
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/settings").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mustChangePasswordBlocksNormalEndpointsUntilChanged() throws Exception {
        String token = login("it.force", "Temp#Pass2026");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp#Pass2026\","
                                + "\"newPassword\":\"Fresh#Pass2026\"}"))
                .andExpect(status().isNoContent());

        // gate lifted with a fresh token
        String fresh = login("it.force", "Fresh#Pass2026");
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(fresh)))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeValidatesCurrentAndNewPassword() throws Exception {
        String token = login("it.emp", EMPLOYEE_PASSWORD);

        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Totally#Wrong1\","
                                + "\"newPassword\":\"Fresh#Pass2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        // 8 chars but no digit -> passes bean validation, fails the password policy
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + EMPLOYEE_PASSWORD + "\","
                                + "\"newPassword\":\"abcdefgh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void accountLocksAfterFiveFailedAttempts() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            int expected = attempt < 5 ? 401 : 423;
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"it.lock\",\"password\":\"Bad#Pass99\"}"))
                    .andExpect(status().is(expected));
        }

        // still locked even with the correct password
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it.lock\",\"password\":\"Lock#Pass2026\"}"))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private void upsert(String username, String rawPassword, Role role, boolean mustChange) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(mustChange);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTokenNotBefore(null);
        userRepository.save(user);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
