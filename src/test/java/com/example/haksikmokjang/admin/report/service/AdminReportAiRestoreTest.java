package com.example.haksikmokjang.admin.report.service;

import com.example.haksikmokjang.admin.report.domain.ReportProcessHistory;
import com.example.haksikmokjang.admin.report.dto.AdminReportCancelRequest;
import com.example.haksikmokjang.admin.report.repository.ReportProcessHistoryRepository;
import com.example.haksikmokjang.admin.report.repository.ReportRelatedActionItemRepository;
import com.example.haksikmokjang.ai.client.MockAiClient;
import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.repository.AiModerationAnalysisRepository;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.trust.domain.TrustPenaltyHistory;
import com.example.haksikmokjang.member.trust.service.TrustService;
import com.example.haksikmokjang.notification.service.NotificationService;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.repository.ReportRepository;
import com.example.haksikmokjang.report.repository.ReportContextSnapshotRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportAiRestoreTest {

    @Mock private ReportRepository reportRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private NotificationService notificationService;
    @Mock private AdminCommunityReportService communityReportService;
    @Mock private AdminChatReportService chatReportService;
    @Mock private AdminReviewReportService reviewReportService;
    @Mock private TrustService trustService;
    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private ReportProcessHistoryRepository historyRepository;
    @Mock private ReportRelatedActionItemRepository relatedActionItemRepository;
    @Mock private ReportRelatedActionService relatedActionService;
    @Mock private AiModerationAnalysisRepository analysisRepository;
    @Mock private ReportContextSnapshotRepository snapshotRepository;
    @Mock private Member reporter;
    @Mock private Member targetWriter;
    @Mock private Member admin;

    private AdminReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminReportService(
                reportRepository,
                memberRepository,
                notificationService,
                communityReportService,
                chatReportService,
                reviewReportService,
                trustService,
                fileAttachmentRepository,
                historyRepository,
                relatedActionItemRepository,
                relatedActionService,
                analysisRepository,
                snapshotRepository
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT", "CHAT_MESSAGE", "REVIEW"})
    void cancelAiProcessedReportRestoresTargetHistoryAndAnalysis(String targetType) {
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(10L)
                .reason("신고")
                .build();
        report.resolveByAi("AI 자동 처리");

        String beforeTargetStatus = "CHAT_MESSAGE".equals(targetType) ? null : "ACTIVE";
        String afterTargetStatus = switch (targetType) {
            case "POST", "COMMENT" -> "HIDDEN";
            case "REVIEW" -> "DELETED";
            default -> null;
        };
        Boolean beforeChatDeleted = "CHAT_MESSAGE".equals(targetType) ? false : null;
        Boolean afterChatDeleted = "CHAT_MESSAGE".equals(targetType) ? true : null;

        ReportProcessHistory history = new ReportProcessHistory(
                report, targetType, 10L,
                ReportStatus.PENDING, ReportStatus.RESOLVED,
                beforeTargetStatus, afterTargetStatus,
                beforeChatDeleted, afterChatDeleted,
                targetWriter, null,
                null, null, null,
                null, ReportProcessorType.AI
        );
        AiModerationAnalysis analysis = new AiModerationAnalysis(report, autoHideResult(), true);
        analysis.markAutoProcessed();

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(historyRepository.findTopByReportAndCanceledYnOrderByReportProcessHistoryIdDesc(report, "N"))
                .thenReturn(Optional.of(history));
        when(analysisRepository.findByReport(report)).thenReturn(Optional.of(analysis));

        AdminReportCancelRequest request = new AdminReportCancelRequest();
        request.setCancelReason("AI 오탐 복구");
        service.cancelReport(1L, "admin", request);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELED);
        assertThat(report.getProcessorType()).isNull();
        assertThat(history.isCanceled()).isTrue();
        assertThat(history.getCanceledBy()).isSameAs(admin);
        assertThat(history.getCanceledAt()).isNotNull();
        assertThat(history.getCancelReason()).isEqualTo("AI 오탐 복구");
        assertThat(analysis.getProcessingStatus()).isEqualTo(AiProcessingStatus.RESTORED);
        assertThat(analysis.getRestoredAt()).isNotNull();
        assertThat(analysis.getRestoreReason()).isEqualTo("AI 오탐 복구");

        switch (targetType) {
            case "POST", "COMMENT" -> verify(communityReportService)
                    .restoreCommunityTarget(report, "ACTIVE");
            case "CHAT_MESSAGE" -> verify(chatReportService)
                    .restoreChatMessage(report, false);
            case "REVIEW" -> verify(reviewReportService)
                    .restoreReview(report, "ACTIVE");
            default -> throw new IllegalStateException("Unexpected target type: " + targetType);
        }
        verify(trustService, never()).cancelReportPenalty(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void cancelAdminProcessedReportKeepsManualPenaltyRestorePathSeparate() {
        Report report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(10L)
                .reason("신고")
                .build();
        report.resolve(admin, "관리자 처리");
        TrustPenaltyHistory penaltyHistory = org.mockito.Mockito.mock(TrustPenaltyHistory.class);
        BigDecimal beforeMannerTemperature = new BigDecimal("36.5");
        ReportProcessHistory history = new ReportProcessHistory(
                report, "POST", 10L,
                ReportStatus.PENDING, ReportStatus.RESOLVED,
                "ACTIVE", "HIDDEN", null, null,
                targetWriter, penaltyHistory,
                beforeMannerTemperature, new BigDecimal("35.5"), BigDecimal.ONE,
                admin, ReportProcessorType.ADMIN
        );

        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(historyRepository.findTopByReportAndCanceledYnOrderByReportProcessHistoryIdDesc(report, "N"))
                .thenReturn(Optional.of(history));

        AdminReportCancelRequest request = new AdminReportCancelRequest();
        request.setCancelReason("수동 처리 취소");
        service.cancelReport(1L, "admin", request);

        verify(communityReportService).restoreCommunityTarget(report, "ACTIVE");
        verify(trustService).cancelReportPenalty(
                penaltyHistory, admin, beforeMannerTemperature, "수동 처리 취소"
        );
        verify(analysisRepository, never()).findByReport(report);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELED);
        assertThat(history.isCanceled()).isTrue();
        assertThat(history.getProcessorType()).isEqualTo(ReportProcessorType.ADMIN);
    }

    private ModerationResult autoHideResult() {
        return new ModerationResult(
                MockAiClient.POLICY_VERSION,
                ModerationCategory.SPAM_AD,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.95"),
                new BigDecimal("0.96"),
                List.of("SPAM-COMMERCIAL"),
                false,
                List.of(),
                true,
                "자동 처리 fixture",
                MockAiClient.MODEL_NAME
        );
    }
}
