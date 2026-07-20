package com.example.haksikmokjang.admin.report.dto;

import com.example.haksikmokjang.admin.report.domain.ReportRelatedActionItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReportRelatedActionItemResponse {

    private Long id;
    private String targetType;
    private Long targetId;
    private Boolean beforeDeleted;
    private Boolean afterDeleted;
    private String beforeStatus;
    private String afterStatus;
    private String actionType;
    private String reason;
    private boolean restored;
    private LocalDateTime createdAt;
    private LocalDateTime restoredAt;

    public static ReportRelatedActionItemResponse from(ReportRelatedActionItem item) {
        return ReportRelatedActionItemResponse.builder()
                .id(item.getId())
                .targetType(item.getTargetType())
                .targetId(item.getTargetId())
                .beforeDeleted(item.getBeforeDeleted())
                .afterDeleted(item.getAfterDeleted())
                .beforeStatus(item.getBeforeStatus())
                .afterStatus(item.getAfterStatus())
                .actionType(item.getActionType())
                .reason(item.getReason())
                .restored(item.isRestored())
                .createdAt(item.getCreatedAt())
                .restoredAt(item.getRestoredAt())
                .build();
    }
}
