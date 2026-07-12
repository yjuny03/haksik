package com.example.haksikmokjang.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        MemberSummary members,
        OwnerSummary owners,
        ReportSummary reports,
        AiSummary aiModeration,
        TrustSummary trust,
        RecentSummary recent
) {
    public record MemberSummary(
            long total,
            long users,
            long owners,
            long admins,
            long locked,
            long joinedToday
    ) {
    }

    public record OwnerSummary(long pending, long approved, long rejected) {
    }

    public record ReportSummary(
            long total,
            long pending,
            long resolved,
            long rejected,
            long canceled,
            List<DuplicateTargetItem> mostReportedTargets
    ) {
    }

    public record DuplicateTargetItem(String targetType, Long targetId, long reportCount) {
    }

    public record AiSummary(
            long autoProcessed,
            long reviewRequired,
            long failed,
            long restored,
            List<AiReportItem> recentAutoProcessed,
            List<AiReportItem> recentReviewRequired
    ) {
    }

    public record AiReportItem(
            Long reportId,
            String targetType,
            String category,
            String decision,
            BigDecimal riskScore,
            BigDecimal confidenceScore,
            LocalDateTime analyzedAt
    ) {
    }

    public record TrustSummary(
            long penaltiesToday,
            long aiPenalties,
            long adminPenalties,
            long lowMannerUsers
    ) {
    }

    public record RecentSummary(
            List<ActivityItem> reports,
            List<ActivityItem> aiAutoProcessed,
            List<ActivityItem> adminProcessed,
            List<ActivityItem> ownerApplications,
            List<ActivityItem> restored
    ) {
    }

    public record ActivityItem(
            String type,
            Long id,
            String title,
            String status,
            LocalDateTime occurredAt,
            String targetUrl
    ) {
    }
}
