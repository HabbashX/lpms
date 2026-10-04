package com.larv.pharmacy.security;

import com.larv.pharmacy.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. All authorization rules live here so they can be
 * reviewed in one place:
 *
 * <ul>
 *   <li>ADMIN only: users, audit, reports, settings</li>
 *   <li>ADMIN or PHARMACIST: drug writes, stock purchases, refunds, customer adjustments</li>
 *   <li>Any authenticated user: reads, sales, customers, payments, dashboard</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorWriter errorWriter;
    private final AppProperties appProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityErrorWriter errorWriter,
                          AppProperties appProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.errorWriter = errorWriter;
        this.appProperties = appProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {
                        })
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .xssProtection(xss -> xss.headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                errorWriter.write(request, response, 401, "UNAUTHORIZED",
                                        "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorWriter.write(request, response, 403, "FORBIDDEN", "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        // public
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**")
                        .permitAll()
                        // administrator only
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers("/api/v1/settings/**").hasRole("ADMIN")
                        // catalog writes and stock purchases
                        .requestMatchers(HttpMethod.POST, "/api/v1/drugs").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/drugs/*").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/drugs/*").hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/purchases")
                        .hasAnyRole("ADMIN", "PHARMACIST")
                        // financial corrections
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/*/refund")
                        .hasAnyRole("ADMIN", "PHARMACIST")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/*/adjustments")
                        .hasAnyRole("ADMIN", "PHARMACIST")
                        // everything else requires an authenticated user
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().originList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
