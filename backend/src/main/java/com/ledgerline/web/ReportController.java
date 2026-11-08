package com.ledgerline.web;

import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.ReportService;
import com.ledgerline.web.Dtos.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read-only endpoints. Holders see only their own data; ADMIN/AUDITOR see everything. Nothing here can write. */
@RestController
@RequestMapping("/api")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/transactions")
    public List<TransactionDto> transactions(@AuthenticationPrincipal UserPrincipal me) { return reports.myTransactions(me); }

    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionDto> accountTransactions(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return reports.accountTransactions(me, id);
    }

    @GetMapping("/accounts/{id}/ledger")
    public List<LedgerEntryDto> accountLedger(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return reports.accountLedger(me, id);
    }

    @GetMapping("/accounts/{id}/statement")
    public StatementDto statement(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.statement(me, id, from, to);
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<LedgerEntryDto> ledger() { return reports.fullLedger(); }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<AuditDto> audit() { return reports.auditLog(); }

    @GetMapping("/reports/integrity")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public IntegrityDto integrity() { return reports.integrity(); }
}
