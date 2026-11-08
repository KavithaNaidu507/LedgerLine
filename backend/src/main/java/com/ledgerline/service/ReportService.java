package com.ledgerline.service;

import com.ledgerline.domain.*;
import com.ledgerline.domain.LedgerEntry.Direction;
import com.ledgerline.repo.*;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.web.Dtos;
import com.ledgerline.web.Dtos.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries: history, ledger, statements, audit log and the integrity report. */
@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final LedgerTransactionRepository txs;
    private final LedgerEntryRepository entries;
    private final AuditLogRepository audits;

    public ReportService(AccountService accountService, AccountRepository accounts, LedgerTransactionRepository txs,
                         LedgerEntryRepository entries, AuditLogRepository audits) {
        this.accountService = accountService; this.accounts = accounts; this.txs = txs;
        this.entries = entries; this.audits = audits;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? ZERO : v; }

    public List<TransactionDto> myTransactions(UserPrincipal me) {
        if (me.role() != User.Role.ACCOUNT_HOLDER) return txs.findTop100ByOrderByIdDesc().stream().map(Dtos::of).toList();
        List<Long> ids = accounts.findByOwnerIdOrderById(me.id()).stream().map(Account::getId).toList();
        if (ids.isEmpty()) return List.of();
        return txs.forAccounts(ids, PageRequest.of(0, 100)).stream().map(Dtos::of).toList();
    }

    public List<TransactionDto> accountTransactions(UserPrincipal me, Long accountId) {
        accountService.getAccessible(me, accountId);
        return txs.forAccounts(List.of(accountId), PageRequest.of(0, 100)).stream().map(Dtos::of).toList();
    }

    public List<LedgerEntryDto> accountLedger(UserPrincipal me, Long accountId) {
        accountService.getAccessible(me, accountId);
        return entries.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, 200)).stream().map(Dtos::of).toList();
    }

    public List<LedgerEntryDto> fullLedger() {
        return entries.findTop200ByOrderByIdDesc().stream().map(Dtos::of).toList();
    }

    public List<AuditDto> auditLog() {
        return audits.findTop200ByOrderByIdDesc().stream().map(Dtos::of).toList();
    }

    /** from/to are inclusive calendar days (UTC). Opening balance is computed from the ledger itself. */
    public StatementDto statement(UserPrincipal me, Long accountId, LocalDate from, LocalDate to) {
        Account a = accountService.getAccessible(me, accountId);
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(30);
        Instant fromI = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toI = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        BigDecimal opening = nz(entries.totalForAccountBefore(accountId, Direction.CREDIT, fromI))
                .subtract(nz(entries.totalForAccountBefore(accountId, Direction.DEBIT, fromI)));
        List<LedgerEntry> rows = entries.findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(accountId, fromI, toI);
        BigDecimal credits = ZERO, debits = ZERO;
        for (LedgerEntry e : rows) {
            if (e.getDirection() == Direction.CREDIT) credits = credits.add(e.getAmount());
            else debits = debits.add(e.getAmount());
        }
        return new StatementDto(Dtos.of(a), fromI, toI, opening, credits, debits,
                opening.add(credits).subtract(debits), rows.stream().map(Dtos::of).toList());
    }

    /** Proof report: are the books balanced, is money conserved, does every balance match its ledger? */
    public IntegrityDto integrity() {
        BigDecimal debits = nz(entries.total(Direction.DEBIT));
        BigDecimal credits = nz(entries.total(Direction.CREDIT));
        BigDecimal sumBalances = nz(accounts.sumAllBalances());
        List<String> mismatches = new ArrayList<>();
        List<Account> all = accounts.findAllByOrderById();
        for (Account a : all) {
            BigDecimal fromLedger = nz(entries.totalForAccount(a.getId(), Direction.CREDIT))
                    .subtract(nz(entries.totalForAccount(a.getId(), Direction.DEBIT)));
            if (fromLedger.compareTo(a.getBalance()) != 0) {
                mismatches.add(a.getAccountNumber() + ": stored " + a.getBalance() + " but ledger says " + fromLedger);
            }
        }
        return new IntegrityDto(debits, credits, sumBalances, nz(accounts.sumCustomerBalances()),
                debits.compareTo(credits) == 0, sumBalances.compareTo(BigDecimal.ZERO) == 0,
                all.size(), entries.count(), mismatches);
    }
}
