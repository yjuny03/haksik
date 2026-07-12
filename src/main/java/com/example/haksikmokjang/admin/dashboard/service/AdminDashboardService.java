package com.example.haksikmokjang.admin.dashboard.service;

import com.example.haksikmokjang.admin.dashboard.dto.AdminDashboardResponse;
import com.example.haksikmokjang.admin.report.domain.ReportProcessHistory;
import com.example.haksikmokjang.admin.report.repository.ReportProcessHistoryRepository;
import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.repository.AiModerationAnalysisRepository;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.owner.domain.ApprovalStatus;
import com.example.haksikmokjang.member.signup.owner.domain.OwnerProfile;
import com.example.haksikmokjang.member.signup.owner.repository.OwnerProfileRepository;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.member.trust.repository.TrustPenaltyHistoryRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final Limit RECENT_LIMIT = Limit.of(5);
    private static final BigDecimal LOW_MANNER_THRESHOLD = new BigDecimal("10.0");

    private final MemberRepository memberRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final ReportRepository reportRepository;
    private final AiModerationAnalysisRepository analysisRepository;
    private final TrustPenaltyHistoryRepository penaltyHistoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ReportProcessHistoryRepository processHistoryRepository;

    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        var members = new AdminDashboardResponse.MemberSummary(
                memberRepository.count(),
                memberRepository.countByRole(MemberRole.USER),
                memberRepository.countByRole(MemberRole.OWNER),
                memberRepository.countByRole(MemberRole.ADMIN),
                memberRepository.countByLockedYn("Y"),
                memberRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart)
        );

        var owners = new AdminDashboardResponse.OwnerSummary(
                ownerProfileRepository.countByApprovalStatus(ApprovalStatus.PENDING),
                ownerProfileRepository.countByApprovalStatus(ApprovalStatus.APPROVED),
                ownerProfileRepository.countByApprovalStatus(ApprovalStatus.REJECTED)
        );

        List<AdminDashboardResponse.DuplicateTargetItem> duplicateTargets = reportRepository
                .findMostReportedTargets(PageRequest.of(0, 5))
                .stream()
                .map(item -> new AdminDashboardResponse.DuplicateTargetItem(
                        item.getTargetType(), item.getTargetId(), item.getReportCount()
                ))
                .toList();
        var reports = new AdminDashboardResponse.ReportSummary(
                reportRepository.count(),
                reportRepository.countByStatus(ReportStatus.PENDING),
                reportRepository.countByStatus(ReportStatus.RESOLVED),
                reportRepository.countByStatus(ReportStatus.REJECTED),
                reportRepository.countByStatus(ReportStatus.CANCELED),
                duplicateTargets
        );

        List<AiModerationAnalysis> recentAuto = analysisRepository
                .findByProcessingStatusOrderByAnalyzedAtDesc(AiProcessingStatus.AUTO_PROCESSED, RECENT_LIMIT);
        List<AiModerationAnalysis> recentReview = analysisRepository
                .findByProcessingStatusOrderByAnalyzedAtDesc(AiProcessingStatus.REVIEW_REQUIRED, RECENT_LIMIT);
        long restoredAiCount = analysisRepository.countByProcessingStatus(AiProcessingStatus.RESTORED);
        var ai = new AdminDashboardResponse.AiSummary(
                analysisRepository.countByProcessingStatus(AiProcessingStatus.AUTO_PROCESSED) + restoredAiCount,
                analysisRepository.countByProcessingStatus(AiProcessingStatus.REVIEW_REQUIRED),
                analysisRepository.countByProcessingStatus(AiProcessingStatus.FAILED),
                restoredAiCount,
                recentAuto.stream().map(this::toAiItem).toList(),
                recentReview.stream().map(this::toAiItem).toList()
        );

        var trust = new AdminDashboardResponse.TrustSummary(
                penaltyHistoryRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        todayStart, tomorrowStart
                ),
                penaltyHistoryRepository.countByProcessorType(ReportProcessorType.AI),
                penaltyHistoryRepository.countByProcessorType(ReportProcessorType.ADMIN),
                userProfileRepository.countByMannerTemperatureLessThanEqual(LOW_MANNER_THRESHOLD)
        );

        List<Report> recentReports = reportRepository.findByOrderByReportIdDesc(RECENT_LIMIT);
        List<ReportProcessHistory> adminProcessed = processHistoryRepository
                .findByProcessorTypeOrderByProcessedAtDesc(ReportProcessorType.ADMIN, RECENT_LIMIT);
        List<ReportProcessHistory> restored = processHistoryRepository
                .findByCanceledYnOrderByCanceledAtDesc("Y", RECENT_LIMIT);
        List<OwnerProfile> ownerApplications = ownerProfileRepository.findByOrderByCreatedAtDesc(RECENT_LIMIT);
        var recent = new AdminDashboardResponse.RecentSummary(
                recentReports.stream().map(this::toReportActivity).toList(),
                recentAuto.stream().map(this::toAiActivity).toList(),
                adminProcessed.stream().map(this::toAdminActivity).toList(),
                ownerApplications.stream().map(this::toOwnerActivity).toList(),
                restored.stream().map(this::toRestoreActivity).toList()
        );

        return new AdminDashboardResponse(members, owners, reports, ai, trust, recent);
    }

    private AdminDashboardResponse.AiReportItem toAiItem(AiModerationAnalysis analysis) {
        return new AdminDashboardResponse.AiReportItem(
                analysis.getReport().getReportId(),
                analysis.getReport().getTargetType(),
                analysis.getCategory().name(),
                analysis.getDecision().name(),
                analysis.getRiskScore(),
                analysis.getConfidenceScore(),
                analysis.getAnalyzedAt()
        );
    }

    private AdminDashboardResponse.ActivityItem toReportActivity(Report report) {
        return new AdminDashboardResponse.ActivityItem(
                "REPORT", report.getReportId(), "신고 #" + report.getReportId(), report.getStatus().name(),
                report.getCreatedAt(), "/api/view/admin/reports?reportId=" + report.getReportId()
        );
    }

    private AdminDashboardResponse.ActivityItem toAiActivity(AiModerationAnalysis analysis) {
        return new AdminDashboardResponse.ActivityItem(
                "AI", analysis.getReport().getReportId(), "AI " + analysis.getCategory().name(),
                analysis.getProcessingStatus().name(), analysis.getAnalyzedAt(),
                "/api/view/admin/reports?reportId=" + analysis.getReport().getReportId()
        );
    }

    private AdminDashboardResponse.ActivityItem toAdminActivity(ReportProcessHistory history) {
        return new AdminDashboardResponse.ActivityItem(
                "ADMIN_REPORT", history.getReport().getReportId(), "관리자 신고 처리",
                history.getAfterReportStatus().name(), history.getProcessedAt(),
                "/api/view/admin/reports?reportId=" + history.getReport().getReportId()
        );
    }

    private AdminDashboardResponse.ActivityItem toOwnerActivity(OwnerProfile owner) {
        return new AdminDashboardResponse.ActivityItem(
                "OWNER", owner.getOwnerProfileId(), owner.getBusinessName(), owner.getApprovalStatus().name(),
                owner.getCreatedAt(), "/api/view/admin/owners?status=" + owner.getApprovalStatus().name()
        );
    }

    private AdminDashboardResponse.ActivityItem toRestoreActivity(ReportProcessHistory history) {
        return new AdminDashboardResponse.ActivityItem(
                "RESTORE", history.getReport().getReportId(), "신고 처리 취소/복구",
                "CANCELED", history.getCanceledAt(),
                "/api/view/admin/reports?reportId=" + history.getReport().getReportId()
        );
    }
}
