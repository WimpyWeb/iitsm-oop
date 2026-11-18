package com.iitsm.domain.model;

import com.iitsm.domain.enums.Role;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/** Abstract base domain entity representing any system user account. */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String userCode;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected User() {
    }

    /** Constructs a validated user base instance. */
    protected User(String userCode, String username, String email, String fullName,
                   String passwordHash, String phone, Role role) {
        setUserCode(userCode);
        setUsername(username);
        setEmail(email);
        setFullName(fullName);
        setPasswordHash(passwordHash);
        setPhone(phone);
        setRole(role);
        this.active = true;
        this.createdAt = Instant.now();
    }

    /** Returns a human-readable display label such as "Employee E101". */
    public abstract String getDisplayIdentity();

    public Long getId() {
        return id;
    }

    public String getUserCode() {
        return userCode;
    }

    public final void setUserCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            throw new InvalidInputException("User code cannot be blank.");
        }
        this.userCode = userCode.trim().toUpperCase();
    }

    public String getUsername() {
        return username;
    }

    public final void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidInputException("Username cannot be blank.");
        }
        this.username = username.trim().toLowerCase();
    }

    public String getEmail() {
        return email;
    }

    public final void setEmail(String email) {
        if (email == null || !email.contains("@") || email.isBlank()) {
            throw new InvalidInputException("A valid email address is required.");
        }
        this.email = email.trim().toLowerCase();
    }

    public String getFullName() {
        return fullName;
    }

    public final void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new InvalidInputException("Full name cannot be blank.");
        }
        this.fullName = fullName.trim();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public final void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidInputException("Password hash cannot be blank.");
        }
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public final void setPhone(String phone) {
        this.phone = phone == null ? "" : phone.trim();
    }

    public Role getRole() {
        return role;
    }

    public final void setRole(Role role) {
        if (role == null) {
            throw new InvalidInputException("Role cannot be null.");
        }
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return Objects.equals(userCode, other.userCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userCode);
    }
}
