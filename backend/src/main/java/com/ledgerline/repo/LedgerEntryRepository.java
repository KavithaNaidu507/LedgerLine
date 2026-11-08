package com.ledgerline.repo;

import com.ledgerline.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId, Pageable page);
    List<LedgerEntry> findTop200ByOrderByIdDesc();
    List<LedgerEntry> findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(Long accountId, Instant from, Instant to);

    @Query("select sum(e.amount) from LedgerEntry e where e.direction = :d")
    BigDecimal total(@Param("d") LedgerEntry.Direction d);

    @Query("select sum(e.amount) from LedgerEntry e where e.account.id = :acc and e.direction = :d")
    BigDecimal totalForAccount(@Param("acc") Long accountId, @Param("d") LedgerEntry.Direction d);

    @Query("select sum(e.amount) from LedgerEntry e where e.account.id = :acc and e.direction = :d and e.createdAt < :before")
    BigDecimal totalForAccountBefore(@Param("acc") Long accountId, @Param("d") LedgerEntry.Direction d, @Param("before") Instant before);
}
