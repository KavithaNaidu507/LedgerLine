package com.ledgerline.repo;

import com.ledgerline.domain.Account;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    /** Returns only the id so no Account entity is cached before we lock it. */
    @Query("select a.id from Account a where a.accountNumber = :n")
    Optional<Long> findIdByAccountNumber(@Param("n") String accountNumber);
    boolean existsByAccountNumber(String accountNumber);
    List<Account> findByOwnerIdOrderById(Long ownerId);
    List<Account> findAllByOrderById();

    /** SELECT ... FOR UPDATE: other transactions wanting this row wait until we commit or roll back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);

    @Query("select sum(a.balance) from Account a")
    BigDecimal sumAllBalances();

    @Query("select sum(a.balance) from Account a where a.external = false")
    BigDecimal sumCustomerBalances();
}
