package com.ledgerline.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ledgerline.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes. Money is sent as a JSON string ("12.50") so no float rounding can creep in. */
public final class Dtos {
    private Dtos() {}

    // ----- requests -----
    public record RegisterRequest(@NotBlank @Size(max = 100) String fullName,
                                  @NotBlank @Email @Size(max = 150) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record CreateAccountRequest(@NotBlank @Size(max = 60) String name) {}
    public record MoneyRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
                               @Size(max = 200) String description) {}
    public record TransferRequest(@NotNull Long fromAccountId, @NotBlank String toAccountNumber,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
                                  @Size(max = 200) String description) {}
    public record StatusRequest(@NotBlank String status) {}
    public record RoleRequest(@NotBlank String role) {}

    // ----- responses -----
    public record UserDto(Long id, String email, String fullName, String role, Instant createdAt) {}
    public record AuthResponse(String token, UserDto user) {}
    public record AccountDto(Long id, String accountNumber, String name, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
                             String status, Long ownerId, String ownerName, boolean external, Instant createdAt) {}
    public record TransactionDto(Long id, UUID reference, String type, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
                                 String status, String fromAccount, String toAccount, String description,
                                 Long initiatedBy, Instant createdAt) {}
    public record LedgerEntryDto(Long id, Long transactionId, String accountNumber, String direction,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter,
                                 Instant createdAt) {}
    public record AuditDto(Long id, Long userId, String action, String entityType, String entityId, String details, Instant createdAt) {}
    public record StatementDto(AccountDto account, Instant from, Instant to,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingBalance, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredits,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDebits, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closingBalance,
                               List<LedgerEntryDto> entries) {}
    public record IntegrityDto(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDebits, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredits,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal sumOfAllBalances, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal customerFunds,
                               boolean debitsEqualCredits, boolean moneyConserved, long accountCount, long entryCount,
                               List<String> mismatches) {}

    // ----- mappers -----
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getCreatedAt());
    }
    public static AccountDto of(Account a) {
        User o = a.getOwner();
        return new AccountDto(a.getId(), a.getAccountNumber(), a.getName(), a.getBalance(), a.getStatus().name(),
                o == null ? null : o.getId(), o == null ? null : o.getFullName(), a.isExternal(), a.getCreatedAt());
    }
    public static TransactionDto of(LedgerTransaction t) {
        return new TransactionDto(t.getId(), t.getReference(), t.getType().name(), t.getAmount(), t.getStatus(),
                t.getFromAccount().getAccountNumber(), t.getToAccount().getAccountNumber(), t.getDescription(),
                t.getInitiatedBy(), t.getCreatedAt());
    }
    public static LedgerEntryDto of(LedgerEntry e) {
        return new LedgerEntryDto(e.getId(), e.getTransaction().getId(), e.getAccount().getAccountNumber(),
                e.getDirection().name(), e.getAmount(), e.getBalanceAfter(), e.getCreatedAt());
    }
    public static AuditDto of(AuditLog l) {
        return new AuditDto(l.getId(), l.getUserId(), l.getAction(), l.getEntityType(), l.getEntityId(), l.getDetails(), l.getCreatedAt());
    }
}
