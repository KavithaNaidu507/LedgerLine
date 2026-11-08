package com.ledgerline.web;

import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AccountService;
import com.ledgerline.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public List<AccountDto> list(@AuthenticationPrincipal UserPrincipal me) {
        return accounts.list(me).stream().map(Dtos::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto create(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody CreateAccountRequest r) {
        return Dtos.of(accounts.create(me, r.name()));
    }

    @GetMapping("/{id}")
    public AccountDto get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return Dtos.of(accounts.getAccessible(me, id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDto status(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        return Dtos.of(accounts.setStatus(me, id, r.status()));
    }
}
