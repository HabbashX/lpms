package com.larv.pharmacy.user;

import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.user.dto.CreateUserRequest;
import com.larv.pharmacy.user.dto.UpdateUserPasswordRequest;
import com.larv.pharmacy.user.dto.UpdateUserRequest;
import com.larv.pharmacy.user.dto.UpdateUserStatusRequest;
import com.larv.pharmacy.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Administrator-controlled user management (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Supports search by username plus role/enabled filters.")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable) {
        return userService.list(search, role, enabled, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user", description = "There is no public registration; only an ADMIN can create users.")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user (username and/or role)")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a user", description = "Disabling immediately invalidates the user's tokens.")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Reset a user's password",
            description = "Sets a temporary password, forces a password change on next login and invalidates existing tokens.")
    public UserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody UpdateUserPasswordRequest request) {
        return userService.resetPassword(id, request);
    }
}
