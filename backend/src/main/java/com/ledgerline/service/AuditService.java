package com.ledgerline.service;

import com.ledgerline.domain.AuditLog;
import com.ledgerline.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes append-only audit rows. Never pass passwords, tokens or secrets in "details". */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional
    public void log(Long userId, String action, String entityType, String entityId, String details) {
        repo.save(new AuditLog(userId, action, entityType, entityId, details));
    }
}
