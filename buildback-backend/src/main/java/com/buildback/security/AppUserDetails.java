package com.buildback.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.buildback.dto.UserResponse;
import com.buildback.entity.Role;
import com.buildback.entity.User;

/**
 * The logged-in user as Spring Security sees it. It is stored in the HTTP session.
 *
 * The password hash is only needed while checking the password. After a
 * successful login Spring calls eraseCredentials(), so the session never keeps it.
 * There is no toString(), so the hash cannot end up in a log line.
 */
public class AppUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final Role role;
    private final boolean emailVerified;
    private String passwordHash;

    private AppUserDetails(Long id, String name, String email, String phone, Role role, boolean emailVerified,
            String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.emailVerified = emailVerified;
        this.passwordHash = passwordHash;
    }

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(), user.isEmailVerified(),
                user.getPasswordHash());
    }

    /** Spring checks roles as authorities named "ROLE_BUYER", "ROLE_SELLER", "ROLE_ADMIN". */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** We log in with email, so email is the "username". */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public UserResponse toResponse() {
        return new UserResponse(id, name, email, phone, role);
    }
}
