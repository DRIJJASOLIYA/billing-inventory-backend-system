package com.example.BillGeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String entityType;
    private Long entityId;
    private String eventType;
    private String status;
    private String details;
    private LocalDateTime createdAt;
}
