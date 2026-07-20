package com.example.haksikmokjang.admin.report.domain;

import com.example.haksikmokjang.global.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "REPORT_RELATED_ACTION_ITEM",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_REPORT_RELATED_ACTION_ITEM",
                columnNames = {"report_id", "target_type", "target_id", "action_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportRelatedActionItem extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_related_action_item_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "before_deleted")
    private Boolean beforeDeleted;

    @Column(name = "after_deleted")
    private Boolean afterDeleted;

    @Column(name = "before_status", length = 30)
    private String beforeStatus;

    @Column(name = "after_status", length = 30)
    private String afterStatus;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "restored", nullable = false)
    private boolean restored;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    public ReportRelatedActionItem(
            Long reportId,
            String targetType,
            Long targetId,
            Boolean beforeDeleted,
            Boolean afterDeleted,
            String actionType,
            String reason
    ) {
        this.reportId = reportId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeDeleted = beforeDeleted;
        this.afterDeleted = afterDeleted;
        this.actionType = actionType;
        this.reason = reason;
        this.restored = false;
    }

    public ReportRelatedActionItem(
            Long reportId,
            String targetType,
            Long targetId,
            String beforeStatus,
            String afterStatus,
            String actionType,
            String reason
    ) {
        this.reportId = reportId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeDeleted = null;
        this.afterDeleted = null;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.actionType = actionType;
        this.reason = reason;
        this.restored = false;
    }

    public void markRestored() {
        if (this.restored) {
            return;
        }
        this.restored = true;
        this.restoredAt = LocalDateTime.now();
    }
}
