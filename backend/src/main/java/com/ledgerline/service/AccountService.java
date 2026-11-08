package com.ledgerline.service;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.User;
import com.ledgerline.repo.AccountRepository;
import com.ledgerline.repo.UserRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.web.ApiException;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;
    private final UserRepository users;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, UserRepository users, AuditService audit) {
        this.accounts = accounts; this.users = users; this.audit = audit;
    }

    @Transactional
    public Account create(UserPrincipal me, String name) {
        User owner = users.findById(me.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found"));
        String number;
        do { number = "LL" + String.format("%010d", RANDOM.nextLong(10_000_000_000L)); }
        while (accounts.existsByAccountNumber(number));
        Account a = accounts.save(new Account(number, owner, name.trim()));
        audit.log(me.id(), "ACCOUNT_CREATED", "ACCOUNT", String.valueOf(a.getId()), number);
        return a;
    }

    /** Holders see only their own accounts. Admin/Auditor see all (including the EXTERNAL account). */
    @Transactional(readOnly = true)
    public List<Account> list(UserPrincipal me) {
        return me.role() == User.Role.ACCOUNT_HOLDER ? accounts.findByOwnerIdOrderById(me.id()) : accounts.findAllByOrderById();
    }

    /** THE ownership check used by every read endpoint. */
    @Transactional(readOnly = true)
    public Account getAccessible(UserPrincipal me, Long id) {
        Account a = accounts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found"));
        boolean staff = me.role() != User.Role.ACCOUNT_HOLDER;
        if (!staff && (a.getOwner() == null || !a.getOwner().getId().equals(me.id()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have access to this account");
        }
        return a;
    }

    @Transactional
    public Account setStatus(UserPrincipal admin, Long id, String status) {
        Account.Status s;
        try { s = Account.Status.valueOf(status); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_STATUS", "Status must be ACTIVE or FROZEN"); }
        Account a = accounts.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found"));
        if (a.isExternal()) throw new ApiException(HttpStatus.BAD_REQUEST, "SYSTEM_ACCOUNT", "The external account cannot be changed");
        a.setStatus(s);
        audit.log(admin.id(), "ACCOUNT_" + s, "ACCOUNT", String.valueOf(id), a.getAccountNumber());
        return a;
    }
}
