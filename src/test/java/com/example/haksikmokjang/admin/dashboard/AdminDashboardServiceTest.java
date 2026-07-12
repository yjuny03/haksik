package com.example.haksikmokjang.admin.dashboard;

import com.example.haksikmokjang.admin.dashboard.dto.AdminDashboardResponse;
import com.example.haksikmokjang.admin.dashboard.service.AdminDashboardService;
import com.example.haksikmokjang.admin.report.repository.ReportProcessHistoryRepository;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.repository.AiModerationAnalysisRepository;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.owner.domain.ApprovalStatus;
import com.example.haksikmokjang.member.signup.owner.repository.OwnerProfileRepository;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.member.trust.repository.TrustPenaltyHistoryRepository;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private OwnerProfileRepository ownerRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private AiModerationAnalysisRepository analysisRepository;
    @Mock private TrustPenaltyHistoryRepository penaltyRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ReportProcessHistoryRepository processHistoryRepository;

    @Test
    void dashboardAggregatesCountsWithoutCreatingNotifications() {
        AdminDashboardService service = new AdminDashboardService(
                memberRepository, ownerRepository, reportRepository, analysisRepository,
                penaltyRepository, userProfileRepository, processHistoryRepository
        );
        when(memberRepository.count()).thenReturn(100L);
        when(memberRepository.countByRole(MemberRole.USER)).thenReturn(70L);
        when(memberRepository.countByRole(MemberRole.OWNER)).thenReturn(25L);
        when(memberRepository.countByRole(MemberRole.ADMIN)).thenReturn(5L);
        when(memberRepository.countByLockedYn("Y")).thenReturn(3L);
        when(memberRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(4L);
        when(ownerRepository.countByApprovalStatus(ApprovalStatus.PENDING)).thenReturn(2L);
        when(ownerRepository.countByApprovalStatus(ApprovalStatus.APPROVED)).thenReturn(20L);
        when(ownerRepository.countByApprovalStatus(ApprovalStatus.REJECTED)).thenReturn(3L);
        when(reportRepository.count()).thenReturn(30L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(6L);
        when(reportRepository.countByStatus(ReportStatus.RESOLVED)).thenReturn(18L);
        when(reportRepository.countByStatus(ReportStatus.REJECTED)).thenReturn(4L);
        when(reportRepository.countByStatus(ReportStatus.CANCELED)).thenReturn(2L);
        when(reportRepository.findMostReportedTargets(any())).thenReturn(List.of());
        when(analysisRepository.countByProcessingStatus(AiProcessingStatus.AUTO_PROCESSED)).thenReturn(8L);
        when(analysisRepository.countByProcessingStatus(AiProcessingStatus.REVIEW_REQUIRED)).thenReturn(5L);
        when(analysisRepository.countByProcessingStatus(AiProcessingStatus.FAILED)).thenReturn(1L);
        when(analysisRepository.countByProcessingStatus(AiProcessingStatus.RESTORED)).thenReturn(2L);
        when(analysisRepository.findByProcessingStatusOrderByAnalyzedAtDesc(any(), any(Limit.class)))
                .thenReturn(List.of());
        when(penaltyRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(3L);
        when(penaltyRepository.countByProcessorType(ReportProcessorType.AI)).thenReturn(8L);
        when(penaltyRepository.countByProcessorType(ReportProcessorType.ADMIN)).thenReturn(12L);
        when(userProfileRepository.countByMannerTemperatureLessThanEqual(any(BigDecimal.class))).thenReturn(4L);
        when(reportRepository.findByOrderByReportIdDesc(any(Limit.class))).thenReturn(List.of());
        when(processHistoryRepository.findByProcessorTypeOrderByProcessedAtDesc(any(), any(Limit.class)))
                .thenReturn(List.of());
        when(processHistoryRepository.findByCanceledYnOrderByCanceledAtDesc(any(), any(Limit.class)))
                .thenReturn(List.of());
        when(ownerRepository.findByOrderByCreatedAtDesc(any(Limit.class))).thenReturn(List.of());

        AdminDashboardResponse dashboard = service.getDashboard();

        assertThat(dashboard.members().total()).isEqualTo(100L);
        assertThat(dashboard.members().joinedToday()).isEqualTo(4L);
        assertThat(dashboard.owners().pending()).isEqualTo(2L);
        assertThat(dashboard.reports().canceled()).isEqualTo(2L);
        assertThat(dashboard.aiModeration().autoProcessed()).isEqualTo(10L);
        assertThat(dashboard.aiModeration().failed()).isEqualTo(1L);
        assertThat(dashboard.trust().penaltiesToday()).isEqualTo(3L);
        assertThat(dashboard.trust().lowMannerUsers()).isEqualTo(4L);
    }
}
