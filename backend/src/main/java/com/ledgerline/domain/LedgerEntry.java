package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One immutable ledger line. DEBIT lowers an account balance, CREDIT raises it. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    public enum Direction { DEBIT, CREDIT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "transaction_id", updatable = false) private LedgerTransaction transaction;
    @ManyToOne(optional = false) @JoinColumn(name = "account_id", updatable = false) private Account account;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private Direction direction;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal balanceAfter;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected LedgerEntry() {}
    public LedgerEntry(LedgerTransaction tx, Account account, Direction direction, BigDecimal amount, BigDecimal balanceAfter) {
        this.transaction = tx; this.account = account; this.direction = direction;
        this.amount = amount; this.balanceAfter = balanceAfter;
    }
    public Long getId() { return id; }
    public LedgerTransaction getTransaction() { return transaction; }
    public Account getAccount() { return account; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
