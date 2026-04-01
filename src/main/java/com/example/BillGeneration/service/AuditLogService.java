package com.example.BillGeneration.service;

import com.example.BillGeneration.dto.AuditLogResponse;
import com.example.BillGeneration.dto.PageResponse;
import com.example.BillGeneration.entity.AuditLog;
import com.example.BillGeneration.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AsyncAuditLogWriter asyncAuditLogWriter;

    public AuditLogService(AuditLogRepository auditLogRepository, AsyncAuditLogWriter asyncAuditLogWriter) {
        this.auditLogRepository = auditLogRepository;
        this.asyncAuditLogWriter = asyncAuditLogWriter;
    }

    public void record(String entityType, Long entityId, String eventType, String status, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setEventType(eventType);
        auditLog.setStatus(status);
        auditLog.setDetails(details);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncAuditLogWriter.write(auditLog);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncAuditLogWriter.write(auditLog);
            }
        });
    }

    public PageResponse<AuditLogResponse> getAuditLogs(String entityType, String eventType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Specification<AuditLog> specification = Specification.where(matchesEntityType(entityType))
                .and(matchesEventType(eventType));
        Page<AuditLog> result = auditLogRepository.findAll(specification, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(this::mapAuditLog).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private Specification<AuditLog> matchesEntityType(String entityType) {
        return (root, query, cb) -> entityType == null || entityType.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("entityType")), entityType.toLowerCase());
    }

    private Specification<AuditLog> matchesEventType(String eventType) {
        return (root, query, cb) -> eventType == null || eventType.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("eventType")), eventType.toLowerCase());
    }

    private AuditLogResponse mapAuditLog(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getEventType(),
                auditLog.getStatus(),
                auditLog.getDetails(),
                auditLog.getCreatedAt()
        );
    }
}
