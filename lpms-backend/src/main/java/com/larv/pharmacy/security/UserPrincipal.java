package com.larv.pharmacy.security;

import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal stored in the security context. Role authorities are
 * derived from the database user re-read on every request, so role changes
 * take effect immediately without re-issuing tokens.
 */
public record UserPrincipal(
        Long id,
        String username,
        String password,
        Role role,
        boolean enabled,
        boolean mustChangePassword) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getPassword(),
                user.getRole(), user.isEnabled(), user.isMustChangePassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
