package com.buildback.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity = one row in the "users" table.
 *
 * This class is never sent to or received from the browser directly.
 * Requests use RegisterRequest and responses use UserResponse, so the
 * password hash can never leak and the client cannot set id/role/createdAt.
 *
 * There is deliberately no toString() so the hash is never printed in logs.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = User.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
public class User {

    /** Name of the database rule that stops two accounts having the same email. */
    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Always stored trimmed and lowercase (see EmailUtils). */
    @Column(nullable = false, length = 254)
    private String email;

    /** BCrypt hash (60 characters today); 255 leaves room for a future algorithm. */
    /**
     * Has the user clicked the link in the confirmation email?
     * Default 1 (true): accounts that existed before email confirmation, and
     * accounts the server creates itself (the first admin), count as confirmed.
     * Sign-ups from the register page start as false.
     */
    @ColumnDefault("1")
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    /** Mobile number as +91XXXXXXXXXX. Empty for accounts made before phone numbers were asked for. */
    @Column(length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected User() {
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** Sign-up from the register page, which always asks for a mobile number. */
    public User(String name, String email, String passwordHash, Role role, String phone) {
        this(name, email, passwordHash, role);
        this.phone = phone;
        this.emailVerified = false; // must click the link in the confirmation email first
    }

    /** The server sets the creation time just before the first INSERT. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }
}
