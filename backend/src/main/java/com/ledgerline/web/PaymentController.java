package com.ledgerline.web;

import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.PaymentExecutor.Result;
import com.ledgerline.service.PaymentService;
import com.ledgerline.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Only ACCOUNT_HOLDERs can move money (and only from accounts they own). Auditors/admins get 403. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ACCOUNT_HOLDER')")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<TransactionDto> deposit(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody MoneyRequest r) {
        return respond(payments.deposit(me, id, r, key));
    }

    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<TransactionDto> withdraw(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody MoneyRequest r) {
        return respond(payments.withdraw(me, id, r, key));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionDto> transfer(@AuthenticationPrincipal UserPrincipal me,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody TransferRequest r) {
        return respond(payments.transfer(me, r, key));
    }

    /** 201 = executed now. 200 + "Idempotent-Replay: true" = duplicate request, original result returned. */
    private static ResponseEntity<TransactionDto> respond(Result res) {
        ResponseEntity.BodyBuilder b = ResponseEntity.status(res.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
        if (res.replayed()) b.header("Idempotent-Replay", "true");
        return b.body(Dtos.of(res.tx()));
    }
}
