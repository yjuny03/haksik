package com.example.haksikmokjang.ai.moderation;

import com.example.haksikmokjang.admin.report.service.AdminReportService;
import com.example.haksikmokjang.admin.report.service.ReportRelatedActionService;
import com.example.haksikmokjang.ai.client.AiClient;
import com.example.haksikmokjang.ai.client.AiProviderQuotaExceededException;
import com.example.haksikmokjang.ai.client.MockAiClient;
import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.domain.AutoHideBasis;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.repository.AiModerationAnalysisRepository;
import com.example.haksikmokjang.ai.moderation.service.AiModerationService;
import com.example.haksikmokjang.ai.moderation.service.ModerationPolicyService;
import com.example.haksikmokjang.ai.moderation.service.ModerationRuleValidationService;
import com.example.haksikmokjang.ai.moderation.service.ModerationTargetContentService;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.service.NotificationService;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiModerationNotificationTest {

    @Mock private ReportRepository reportRepository;
    @Mock private AiModerationAnalysisRepository analysisRepository;
    @Mock private ModerationTargetContentService targetContentService;
    @Mock private ModerationRuleValidationService ruleService;
    @Mock private ModerationPolicyService policyService;
    @Mock private AiClient aiClient;
    @Mock private AdminReportService adminReportService;
    @Mock private ReportRelatedActionService reportRelatedActionService;
    @Mock private MemberRepository memberRepository;
    @Mock private NotificationService notificationService;
    @Mock private Report report;
    @Mock private Member reporter;
    @Mock private Member writer;
    @Mock private Member admin;

    private AiModerationService service;

    @BeforeEach
    void setUp() {
        service = new AiModerationService(
                reportRepository, analysisRepository, targetContentService,
                ruleService, policyService, aiClient, adminReportService,
                reportRelatedActionService, memberRepository, notificationService
        );
        when(reportRepository.findById(123L)).thenReturn(Optional.of(report));
        when(report.getReportId()).thenReturn(123L);
        when(report.getTargetType()).thenReturn("POST");
        when(report.getStatus()).thenReturn(ReportStatus.PENDING);
        when(report.getReporter()).thenReturn(reporter);
        when(report.getReason()).thenReturn("스팸 신고");
        when(analysisRepository.existsByReport(report)).thenReturn(false);
        when(analysisRepository.saveAndFlush(any(AiModerationAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.findAllByRole(MemberRole.ADMIN)).thenReturn(List.of(admin));
    }

    @Test
    void autoProcessedNotifiesAdminReporterAndWriterWithoutUserScores() {
        ModerationResult result = autoHideResult();
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("고수익 링크", true, false)
        );
        when(aiClient.analyzeModeration(any())).thenReturn(result);
        when(ruleService.inspectForCategory(ModerationCategory.SPAM_AD, "고수익 링크"))
                .thenReturn(new ModerationRuleValidationService.CategoryRuleEvidence(true, false, false));
        when(policyService.canAutoHide(result, true, false, false)).thenReturn(true);
        when(adminReportService.autoResolveReport(eq(123L), anyString()))
                .thenReturn(new AdminReportService.AiAutoResolveResult(writer));

        service.analyzeReport(123L);

        verify(notificationService).sendNotification(
                eq(admin), eq("ADMIN_AI_REPORT_PROCESSED"), contains("#123"),
                contains("riskScore=0.95"), eq("ADMIN_REPORT"), eq(123L)
        );
        verify(notificationService).sendNotification(
                eq(reporter), eq("REPORT"), eq("신고 처리 완료"),
                eq("신고하신 내용이 정책 위반으로 확인되어 처리되었습니다."), eq("REPORT"), eq(123L)
        );
        verify(notificationService).sendNotification(
                eq(writer), eq("REPORT"), contains("게시글"), contains("복구될 수 있으며"), eq("REPORT"), eq(123L)
        );
    }

    @Test
    void imageOnlyReportSkipsAiAndBecomesReviewRequired() {
        when(report.getReason()).thenReturn("첨부 이미지 신고");
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("이미지", false, true)
        );
        when(ruleService.inspectForCategory(ModerationCategory.OTHER, "이미지"))
                .thenReturn(new ModerationRuleValidationService.CategoryRuleEvidence(false, false, false));
        when(policyService.canAutoHide(any(), eq(false), eq(false), eq(false))).thenReturn(false);

        service.analyzeReport(123L);

        verify(aiClient, never()).analyzeModeration(any());
        verify(adminReportService, never()).autoResolveReport(any(), anyString());
        verify(notificationService).sendNotification(
                eq(admin), eq("ADMIN_AI_REVIEW_REQUIRED"), contains("#123"),
                contains("첨부 이미지는 AI 분석 대상이 아니며"), eq("ADMIN_REPORT"), eq(123L)
        );
        verify(notificationService).sendNotification(
                eq(reporter), eq("REPORT"), eq("신고 검토 중"),
                anyString(), eq("REPORT"), eq(123L)
        );
    }

    @Test
    void textWithImageAnalyzesTextOnly() {
        ModerationResult result = new ModerationResult(
                MockAiClient.POLICY_VERSION, ModerationCategory.OTHER, ModerationDecision.REVIEW_REQUIRED,
                new BigDecimal("0.40"), new BigDecimal("0.70"), List.of(), false, List.of(),
                false, "텍스트 관리자 검토", MockAiClient.MODEL_NAME
        );
        when(report.getReason()).thenReturn("첨부 이미지 신고");
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("분석할 텍스트", true, true)
        );
        when(aiClient.analyzeModeration(any())).thenReturn(result);
        when(ruleService.inspectForCategory(ModerationCategory.OTHER, "분석할 텍스트"))
                .thenReturn(new ModerationRuleValidationService.CategoryRuleEvidence(false, false, false));
        when(policyService.canAutoHide(result, false, false, false)).thenReturn(false);

        service.analyzeReport(123L);

        verify(aiClient).analyzeModeration(any());
        verify(adminReportService, never()).autoResolveReport(any(), anyString());
    }

    @Test
    void aiFailureKeepsReportPendingAndNotifiesManualReview() {
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("분석 대상", true, false)
        );
        when(aiClient.analyzeModeration(any())).thenThrow(new IllegalStateException("mock failure"));
        when(analysisRepository.save(any(AiModerationAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.analyzeReport(123L);

        ArgumentCaptor<AiModerationAnalysis> captor = ArgumentCaptor.forClass(AiModerationAnalysis.class);
        verify(analysisRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo(AiProcessingStatus.FAILED);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(notificationService).sendNotification(
                eq(admin), eq("ADMIN_AI_FAILED"), contains("수동 검토"),
                contains("신고 ID=123"), eq("ADMIN_REPORT"), eq(123L)
        );
        verify(notificationService).sendNotification(
                eq(reporter), eq("REPORT"), eq("신고 검토 중"),
                contains("정상 접수"), eq("REPORT"), eq(123L)
        );
    }

    @Test
    void quotaExceededSavesAiFailedAndKeepsReportPendingForAdminReview() {
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("분석 대상", true, false)
        );
        when(aiClient.analyzeModeration(any())).thenThrow(new AiProviderQuotaExceededException());
        when(analysisRepository.save(any(AiModerationAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.analyzeReport(123L);

        ArgumentCaptor<AiModerationAnalysis> captor = ArgumentCaptor.forClass(AiModerationAnalysis.class);
        verify(analysisRepository).save(captor.capture());
        AiModerationAnalysis analysis = captor.getValue();
        assertThat(analysis.getProcessingStatus()).isEqualTo(AiProcessingStatus.FAILED);
        assertThat(analysis.getAutoHideBasis()).isEqualTo(AutoHideBasis.AI_FAILED);
        assertThat(analysis.getReasonSummary()).isEqualTo(AiProviderQuotaExceededException.USER_MESSAGE);
        assertThat(analysis.getModelName()).isEqualTo("AI_PROVIDER");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);

        verify(adminReportService, never()).autoResolveReport(any(), anyString());
        verify(reportRelatedActionService, never()).hideRelatedItemsAfterAiAutoProcess(any(), any(), any());
        verify(notificationService).sendNotification(
                eq(admin), eq("ADMIN_AI_FAILED"), contains("수동 검토"),
                contains(AiProviderQuotaExceededException.USER_MESSAGE), eq("ADMIN_REPORT"), eq(123L)
        );
        verify(notificationService).sendNotification(
                eq(reporter), eq("REPORT"), eq("신고 검토 중"),
                contains("정상 접수"), eq("REPORT"), eq(123L)
        );
    }

    @Test
    void autoHideIsDowngradedWhenServerRuleDoesNotMatch() {
        ModerationResult result = autoHideResult();
        when(targetContentService.getSnapshot(report)).thenReturn(
                new ModerationTargetContentService.TargetContentSnapshot("일반 문장", true, false)
        );
        when(aiClient.analyzeModeration(any())).thenReturn(result);
        when(ruleService.inspectForCategory(ModerationCategory.SPAM_AD, "일반 문장"))
                .thenReturn(new ModerationRuleValidationService.CategoryRuleEvidence(false, false, false));
        when(policyService.canAutoHide(any(), eq(false), eq(false), eq(false))).thenReturn(false);

        service.analyzeReport(123L);

        ArgumentCaptor<AiModerationAnalysis> captor = ArgumentCaptor.forClass(AiModerationAnalysis.class);
        verify(analysisRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(ModerationDecision.REVIEW_REQUIRED);
        verify(adminReportService, never()).autoResolveReport(any(), anyString());
    }

    private ModerationResult autoHideResult() {
        return new ModerationResult(
                MockAiClient.POLICY_VERSION,
                ModerationCategory.SPAM_AD,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.95"), new BigDecimal("0.96"),
                List.of("SPAM-COMMERCIAL"), false, List.of(), true,
                "자동 처리 fixture", MockAiClient.MODEL_NAME
        );
    }
}
