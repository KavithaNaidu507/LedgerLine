package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    public enum Status { ACTIVE, FROZEN }
    public static final String EXTERNAL_NUMBER = "EXTERNAL-CASH";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_number", nullable = false, unique = true, updatable = false) private String accountNumber;
    @ManyToOne @JoinColumn(name = "owner_id") private User owner;
    @Column(nullable = false) private String name;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.ACTIVE;
    @Column(name = "is_external", nullable = false) private boolean external;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected Account() {}
    public Account(String accountNumber, User owner, String name) {
        this.accountNumber = accountNumber; this.owner = owner; this.name = name;
    }
    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isExternal() { return external; }
    public Instant getCreatedAt() { return createdAt; }
}
