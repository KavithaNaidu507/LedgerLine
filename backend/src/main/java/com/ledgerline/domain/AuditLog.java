package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", updatable = false) private Long userId;
    @Column(nullable = false, updatable = false) private String action;
    @Column(name = "entity_type", updatable = false) private String entityType;
    @Column(name = "entity_id", updatable = false) private String entityId;
    @Column(updatable = false) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected AuditLog() {}
    public AuditLog(Long userId, String action, String entityType, String entityId, String details) {
        this.userId = userId; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.details = details;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
