package com.ledgerline.service;

import com.ledgerline.domain.LedgerTransaction.Type;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.PaymentExecutor.Command;
import com.ledgerline.service.PaymentExecutor.Result;
import com.ledgerline.web.ApiException;
import com.ledgerline.web.Dtos.MoneyRequest;
import com.ledgerline.web.Dtos.TransferRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Front door for money movement. NOT transactional itself: the real work is in PaymentExecutor (one DB transaction).
 * Keeping this layer outside the transaction lets us (a) audit rejected attempts even though their transaction
 * rolled back, and (b) recover if two identical requests race to the unique idempotency constraint.
 */
@Service
public class PaymentService {
    private final PaymentExecutor executor;
    private final AuditService audit;

    public PaymentService(PaymentExecutor executor, AuditService audit) {
        this.executor = executor; this.audit = audit;
    }

    public Result deposit(UserPrincipal me, Long accountId, MoneyRequest r, String key) {
        return run(new Command(Type.DEPOSIT, me.id(), accountId, null, money(r.amount()), r.description(), checkKey(key)));
    }

    public Result withdraw(UserPrincipal me, Long accountId, MoneyRequest r, String key) {
        return run(new Command(Type.WITHDRAWAL, me.id(), accountId, null, money(r.amount()), r.description(), checkKey(key)));
    }

    public Result transfer(UserPrincipal me, TransferRequest r, String key) {
        return run(new Command(Type.TRANSFER, me.id(), r.fromAccountId(), r.toAccountNumber().trim(),
                money(r.amount()), r.description(), checkKey(key)));
    }

    private Result run(Command c) {
        try {
            try {
                return executor.post(c);
            } catch (DataIntegrityViolationException e) {
                // Two identical requests raced past each other and the DB unique constraint stopped the second.
                // The first one won, so return ITS result instead of an error.
                return executor.replay(c).orElseThrow(() -> e);
            }
        } catch (ApiException e) {
            audit.log(c.userId(), "TRANSACTION_REJECTED", "ACCOUNT", String.valueOf(c.accountId()),
                    c.type() + " " + c.amount() + " -> " + e.getCode());
            throw e;
        }
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.UNNECESSARY); // validation already guarantees <= 2 decimals
    }

    private static String checkKey(String key) {
        if (key == null || key.isBlank() || key.length() < 8 || key.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Send an Idempotency-Key header (8-100 characters, e.g. a UUID)");
        }
        return key;
    }
}
