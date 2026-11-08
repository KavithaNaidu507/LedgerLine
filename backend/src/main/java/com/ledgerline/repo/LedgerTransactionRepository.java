package com.ledgerline.repo;

import com.ledgerline.domain.LedgerTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
    Optional<LedgerTransaction> findByInitiatedByAndIdempotencyKey(Long initiatedBy, String idempotencyKey);
    List<LedgerTransaction> findTop100ByOrderByIdDesc();

    @Query("select t from LedgerTransaction t where t.fromAccount.id in :ids or t.toAccount.id in :ids order by t.id desc")
    List<LedgerTransaction> forAccounts(@Param("ids") List<Long> ids, Pageable page);
}
