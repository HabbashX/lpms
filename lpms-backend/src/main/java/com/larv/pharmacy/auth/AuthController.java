package com.larv.pharmacy.auth;

import com.larv.pharmacy.auth.dto.ChangePasswordRequest;
import com.larv.pharmacy.auth.dto.LoginRequest;
import com.larv.pharmacy.auth.dto.LoginResponse;
import com.larv.pharmacy.auth.dto.LogoutRequest;
import com.larv.pharmacy.auth.dto.RefreshRequest;
import com.larv.pharmacy.security.UserPrincipal;
import com.larv.pharmacy.user.UserRepository;
import com.larv.pharmacy.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, current user and password change")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    @Operation(summary = "Log in", description = "Returns a bearer access token. Rate limited per IP/username.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    @Operation(summary = "Refresh access token",
            description = "Exchanges a refresh token for a new access token and a new refresh token (rotation). "
                    + "The old refresh token becomes invalid; reusing it revokes all of the user's sessions.")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Log out", description = "Revokes the presented access token and, when supplied in the body, the refresh token.")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                       String authorization,
                                       @RequestBody(required = false) LogoutRequest request) {
        authService.logout(authorization, request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Current authenticated user")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findByUsername(principal.username())
                .map(UserResponse::from)
                .orElseThrow();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change own password",
            description = "Required after a password reset by an administrator. Invalidates all existing tokens.")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
