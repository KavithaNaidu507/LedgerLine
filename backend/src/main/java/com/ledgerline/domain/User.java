package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    public enum Role { ADMIN, ACCOUNT_HOLDER, AUDITOR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected User() {}
    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email; this.passwordHash = passwordHash; this.fullName = fullName; this.role = role;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
}
