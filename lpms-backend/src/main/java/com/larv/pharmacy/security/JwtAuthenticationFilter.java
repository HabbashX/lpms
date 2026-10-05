package com.larv.pharmacy.security;

import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates the {@code Authorization: Bearer} token on every request and
 * populates the security context. Rejects revoked, expired, stale
 * (password/role-security events) tokens and tokens of disabled accounts.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   TokenRevocationService tokenRevocationService,
                                   SecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tokenRevocationService = tokenRevocationService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The client may still send its expired access token when refreshing.
        return request.getRequestURI().endsWith("/api/v1/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtService.JwtClaims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            errorWriter.write(request, response, 401, "INVALID_TOKEN", "Invalid or expired access token");
            return;
        }

        if (tokenRevocationService.isRevoked(claims.jti())) {
            errorWriter.write(request, response, 401, "TOKEN_REVOKED", "Access token has been revoked");
            return;
        }

        Optional<User> userOptional = userRepository.findByUsername(claims.username());
        if (userOptional.isEmpty()) {
            errorWriter.write(request, response, 401, "INVALID_TOKEN", "Access token is no longer valid");
            return;
        }
        User user = userOptional.get();

        if (!user.isEnabled()) {
            errorWriter.write(request, response, 401, "ACCOUNT_DISABLED", "Account is disabled");
            return;
        }

        // JWT iat has second precision; truncate the stored cutoff so tokens issued
        // in the same second as a password change/disabling are not rejected.
        if (claims.issuedAt() != null && user.getTokenNotBefore() != null
                && claims.issuedAt().isBefore(user.getTokenNotBefore().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
            errorWriter.write(request, response, 401, "TOKEN_REVOKED",
                    "Session is no longer valid, please log in again");
            return;
        }

        if (user.isMustChangePassword() && !isPasswordChangeAllowed(request)) {
            errorWriter.write(request, response, 403, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your password before using the API");
            return;
        }

        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeAllowed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PASSWORD_CHANGE_ALLOWED_PATHS.stream().anyMatch(uri::endsWith);
    }
}
