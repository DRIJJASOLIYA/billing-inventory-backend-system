package com.example.BillGeneration.service;

import com.example.BillGeneration.entity.AuditLog;
import com.example.BillGeneration.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncAuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditLogWriter.class);

    private final AuditLogRepository auditLogRepository;

    public AsyncAuditLogWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("auditExecutor")
    public void write(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Failed to persist audit log for entityType={} entityId={} eventType={}",
                    auditLog.getEntityType(), auditLog.getEntityId(), auditLog.getEventType(), ex);
        }
    }
}
