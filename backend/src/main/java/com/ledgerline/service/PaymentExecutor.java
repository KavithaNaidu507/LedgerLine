package com.ledgerline.service;

import com.ledgerline.domain.*;
import com.ledgerline.domain.LedgerTransaction.Type;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.LedgerEntryRepository;
import com.ledgerline.repo.LedgerTransactionRepository;
import com.ledgerline.web.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONE place where money moves. Everything in post() runs in a single database transaction:
 * either all of it is saved (transaction row, 2 ledger entries, 2 balance updates, audit row) or none of it.
 */
@Service
public class PaymentExecutor {
    public record Command(Type type, Long userId, Long accountId, String toAccountNumber,
                          BigDecimal amount, String description, String key) {}
    public record Result(LedgerTransaction tx, boolean replayed) {}

    private final AccountRepository accounts;
    private final LedgerTransactionRepository txs;
    private final LedgerEntryRepository entries;
    private final AuditService audit;

    public PaymentExecutor(AccountRepository accounts, LedgerTransactionRepository txs,
                           LedgerEntryRepository entries, AuditService audit) {
        this.accounts = accounts; this.txs = txs; this.entries = entries; this.audit = audit;
    }

    @Transactional
    public Result post(Command c) {
        // 1. Work out the two account IDS (ids only: loading the entity before locking would cache a stale balance).
        //    Work out the two accounts involved. Deposits/withdrawals use the EXTERNAL account as counterparty.
        Long counterId = switch (c.type()) {
            case DEPOSIT, WITHDRAWAL -> accounts.findIdByAccountNumber(Account.EXTERNAL_NUMBER)
                    .orElseThrow(() -> new IllegalStateException("External account missing"));
            case TRANSFER -> accounts.findIdByAccountNumber(c.toAccountNumber())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DESTINATION_NOT_FOUND", "Destination account not found"));
        };
        if (counterId.equals(c.accountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Source and destination must differ");
        }

        // 2. LOCK both rows (SELECT ... FOR UPDATE) in ascending id order. Always the same order => no deadlocks.
        //    Anyone else touching these accounts now waits here until we commit/rollback, then sees fresh balances.
        Map<Long, Account> locked = new HashMap<>();
        for (Long id : new TreeSet<>(List.of(c.accountId(), counterId))) {
            locked.put(id, accounts.lockById(id).orElseThrow(
                    () -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found")));
        }
        Account mine = locked.get(c.accountId());
        Account other = locked.get(counterId);

        // 3. Authorisation: the caller must own their account (re-checked on the backend, never trusted from the UI).
        if (mine.isExternal() || mine.getOwner() == null || !mine.getOwner().getId().equals(c.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not own this account");
        }

        // 4. Idempotency: same key + same request => return the ORIGINAL result and do nothing else.
        Optional<Result> replay = replay(c);
        if (replay.isPresent()) return replay.get();

        // 5. Business rules.
        if (mine.getStatus() != Account.Status.ACTIVE || (!other.isExternal() && other.getStatus() != Account.Status.ACTIVE)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE", "Account is frozen");
        }
        Account debit = c.type() == Type.DEPOSIT ? other : mine;
        Account credit = c.type() == Type.DEPOSIT ? mine : other;
        if (!debit.isExternal() && debit.getBalance().compareTo(c.amount()) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient funds");
        }

        // 6. Write: transaction + two ledger entries + balance updates. All-or-nothing.
        LedgerTransaction t = txs.save(new LedgerTransaction(c.type(), c.amount(), debit, credit,
                c.description(), c.userId(), c.key(), hash(c)));
        debit.setBalance(debit.getBalance().subtract(c.amount()));
        credit.setBalance(credit.getBalance().add(c.amount()));
        entries.save(new LedgerEntry(t, debit, LedgerEntry.Direction.DEBIT, c.amount(), debit.getBalance()));
        entries.save(new LedgerEntry(t, credit, LedgerEntry.Direction.CREDIT, c.amount(), credit.getBalance()));
        audit.log(c.userId(), "TRANSACTION_" + c.type(), "TRANSACTION", String.valueOf(t.getId()),
                "amount=" + c.amount() + " from=" + debit.getAccountNumber() + " to=" + credit.getAccountNumber());
        return new Result(t, false);
        // On commit, PostgreSQL also verifies debits == credits (deferred trigger) and balance >= 0 (CHECK).
    }

    /** Looks up an earlier request with the same key. Same payload -> replay; different payload -> 409. */
    @Transactional(readOnly = true)
    public Optional<Result> replay(Command c) {
        return txs.findByInitiatedByAndIdempotencyKey(c.userId(), c.key()).map(t -> {
            if (!t.getRequestHash().equals(hash(c))) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different request");
            }
            return new Result(t, true);
        });
    }

    static String hash(Command c) {
        String raw = c.type() + "|" + c.accountId() + "|" + c.toAccountNumber() + "|" + c.amount().toPlainString()
                + "|" + (c.description() == null ? "" : c.description());
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
