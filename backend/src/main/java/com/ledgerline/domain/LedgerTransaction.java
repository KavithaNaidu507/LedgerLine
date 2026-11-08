package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One business event (deposit / withdrawal / transfer). Its money lines are LedgerEntry rows. */
@Entity
@Table(name = "transactions")
public class LedgerTransaction {
    public enum Type { DEPOSIT, WITHDRAWAL, TRANSFER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private UUID reference = UUID.randomUUID();
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Type type;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false) private String status = "COMPLETED";
    @ManyToOne(optional = false) @JoinColumn(name = "from_account_id", updatable = false) private Account fromAccount;
    @ManyToOne(optional = false) @JoinColumn(name = "to_account_id", updatable = false) private Account toAccount;
    @Column(updatable = false) private String description;
    @Column(name = "initiated_by", nullable = false, updatable = false) private Long initiatedBy;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, updatable = false) private String requestHash;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected LedgerTransaction() {}
    public LedgerTransaction(Type type, BigDecimal amount, Account from, Account to, String description,
                             Long initiatedBy, String idempotencyKey, String requestHash) {
        this.type = type; this.amount = amount; this.fromAccount = from; this.toAccount = to;
        this.description = description; this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey; this.requestHash = requestHash;
    }
    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Type getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
    public String getDescription() { return description; }
    public Long getInitiatedBy() { return initiatedBy; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}
