package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.admin.report.service.AdminReportService;
import com.example.haksikmokjang.admin.report.service.ReportRelatedActionService;
import com.example.haksikmokjang.ai.client.AiClient;
import com.example.haksikmokjang.ai.client.AiProviderQuotaExceededException;
import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;
import com.example.haksikmokjang.ai.moderation.domain.AutoHideBasis;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationPromptRequest;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.repository.AiModerationAnalysisRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.repository.ReportRepository;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiModerationService {

    private final ReportRepository reportRepository;
    private final AiModerationAnalysisRepository analysisRepository;
    private final ModerationTargetContentService targetContentService;
    private final ModerationRuleValidationService ruleValidationService;
    private final ModerationPolicyService policyService;
    private final AiClient aiClient;
    private final AdminReportService adminReportService;
    private final ReportRelatedActionService reportRelatedActionService;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeReport(Long reportId) {
        String stage = "START";
        Report report = null;
        log.info("[AI-REPORT] analyzeReport 시작 reportId={}", reportId);

        try {
            stage = "REPORT_LOOKUP";
            report = reportRepository.findById(reportId).orElse(null);
            if (report == null) {
                log.warn("[AI-REPORT] report 조회 실패 - 존재하지 않음 reportId={}", reportId);
                log.info("[AI-REPORT] analyzeReport 정상 종료 reportId={} result=REPORT_NOT_FOUND", reportId);
                return;
            }

            log.info(
                    "[AI-REPORT] report 조회 성공 reportId={} targetType={} targetId={} status={}",
                    report.getReportId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    report.getStatus()
            );

            stage = "DUPLICATE_CHECK";
            if (analysisRepository.existsByReport(report)) {
                log.info("[AI-REPORT] 기존 분석 결과 존재 - 분석 생략 reportId={}", reportId);
                log.info("[AI-REPORT] analyzeReport 정상 종료 reportId={} result=ALREADY_ANALYZED", reportId);
                return;
            }

            stage = "CONTENT_LOOKUP";
            ModerationTargetContentService.TargetContentSnapshot targetSnapshot =
                    targetContentService.getSnapshot(report);
            String content = targetSnapshot.content();
            log.info(
                    "[AI-REPORT] content 조회 결과 reportId={} targetType={} contentPresent={} contentLength={}",
                    reportId,
                    report.getTargetType(),
                    content != null && !content.isBlank(),
                    content == null ? 0 : content.length()
            );

            stage = "AI_ANALYSIS";
            ModerationResult result = requiresImageReview(report, targetSnapshot)
                    ? imageReviewResult()
                    : aiClient.analyzeModeration(new ModerationPromptRequest(
                            report.getReportId(),
                            report.getTargetType(),
                            report.getReason(),
                            content
                    ));
            log.info(
                    "[AI-REPORT] AI result reportId={} category={} decision={} riskScore={} confidenceScore={} policyCodes={} modelName={} policyVersion={}",
                    reportId,
                    result.category(),
                    result.decision(),
                    result.riskScore(),
                    result.confidenceScore(),
                    result.policyCodes(),
                    result.modelName(),
                    result.policyVersion()
            );

            stage = "RULE_VALIDATION";
            ModerationRuleValidationService.CategoryRuleEvidence ruleEvidence =
                    ruleValidationService.inspectForCategory(result.category(), content);
            boolean ruleMatched = ruleEvidence.ruleMatched();
            boolean exclusionMatched = ruleEvidence.exclusionMatched();
            boolean targetMatched = ruleEvidence.targetMatched();
            boolean highConfidenceFallback = !ruleMatched
                    && policyService.canAutoHideByHighConfidenceFallback(result, exclusionMatched, targetMatched);
            boolean policyAllowsAutoHide = policyService.canAutoHide(
                    result,
                    ruleMatched,
                    exclusionMatched,
                    targetMatched
            );
            boolean downgradedToReview = false;
            if (result.decision() == ModerationDecision.AUTO_HIDE && !policyAllowsAutoHide) {
                result = downgradeToReview(result);
                downgradedToReview = true;
            }

            stage = "AUTO_HIDE_POLICY";
            boolean processableTarget = isProcessableTarget(report.getTargetType());
            boolean canAutoHide = processableTarget
                    && report.getStatus() == ReportStatus.PENDING
                    && policyService.canAutoHide(result, ruleMatched, exclusionMatched, targetMatched);
            AutoHideBasis autoHideBasis = autoHideBasis(
                    result,
                    ruleMatched,
                    exclusionMatched,
                    canAutoHide
            );
            log.info(
                    "[AI-REPORT] rule validation 결과 reportId={} targetType={} category={} decision={} ruleMatched={} exclusionMatched={} targetMatched={} highConfidenceFallback={} autoHideBasis={}",
                    reportId,
                    report.getTargetType(),
                    result.category(),
                    result.decision(),
                    ruleMatched,
                    exclusionMatched,
                    targetMatched,
                    highConfidenceFallback,
                    autoHideBasis
            );

            stage = "ANALYSIS_SAVE";
            AiModerationAnalysis analysis = analysisRepository.saveAndFlush(
                    new AiModerationAnalysis(report, result, ruleMatched, autoHideBasis)
            );
            log.info(
                    "[AI-REPORT] analysisRepository.save 완료 reportId={} analysisId={} processingStatus={}",
                    reportId,
                    analysis.getAiModerationAnalysisId(),
                    analysis.getProcessingStatus()
            );

            log.info(
                    "[AI-REPORT] canAutoHide 결과 reportId={} targetType={} category={} decision={} riskScore={} confidenceScore={} ruleMatched={} exclusionMatched={} autoHideBasis={} canAutoHide={} processableTarget={} reportStatus={} highConfidenceFallback={} finalDecision={}",
                    reportId,
                    report.getTargetType(),
                    result.category(),
                    result.decision(),
                    result.riskScore(),
                    result.confidenceScore(),
                    ruleMatched,
                    exclusionMatched,
                    autoHideBasis,
                    canAutoHide,
                    processableTarget,
                    report.getStatus(),
                    highConfidenceFallback,
                    result.decision()
            );

            if (!canAutoHide) {
                analysis.markReviewRequired();
                logModerationDecision(
                        report,
                        result,
                        ruleMatched,
                        exclusionMatched,
                        autoHideBasis,
                        canAutoHide,
                        downgradedToReview,
                        analysis.getProcessingStatus()
                );
                notifyReviewRequired(report, result);
                log.info(
                        "[AI-REPORT] 관리자 검토 전환 reportId={} processingStatus={}",
                        reportId,
                        analysis.getProcessingStatus()
                );
                log.info("[AI-REPORT] analyzeReport 정상 종료 reportId={} result=NO_AUTO_HIDE", reportId);
                return;
            }

            stage = "AUTO_RESOLVE_BEFORE";
            log.info("[AI-REPORT] autoResolveReport 호출 직전 reportId={}", reportId);
            AdminReportService.AiAutoResolveResult autoResolveResult = adminReportService.autoResolveReport(
                    report.getReportId(),
                    "AI 자동 블라인드: " + result.category().name()
                            + " (risk=" + result.riskScore()
                            + ", confidence=" + result.confidenceScore() + ")"
            );

            stage = "AUTO_RESOLVE_AFTER";
            log.info("[AI-REPORT] autoResolveReport 호출 직후 reportId={} reportStatus={}", reportId, report.getStatus());

            stage = "RELATED_ACTION_HIDE";
            int relatedHiddenCount = hideRelatedItemsSafely(report, result, autoHideBasis);
            if (relatedHiddenCount > 0) {
                log.info(
                        "[AI-REPORT] 관련 메시지 함께 블라인드 완료 reportId={} relatedHiddenCount={}",
                        reportId,
                        relatedHiddenCount
                );
            }

            stage = "MARK_AUTO_PROCESSED";
            analysis.markAutoProcessed();
            analysisRepository.flush();
            logModerationDecision(
                    report,
                    result,
                    ruleMatched,
                    exclusionMatched,
                    autoHideBasis,
                    canAutoHide,
                    downgradedToReview,
                    analysis.getProcessingStatus()
            );
            notifyAutoProcessed(report, result, autoResolveResult.targetWriter());
            log.info(
                    "[AI-REPORT] markAutoProcessed 완료 reportId={} analysisId={} processingStatus={}",
                    reportId,
                    analysis.getAiModerationAnalysisId(),
                    analysis.getProcessingStatus()
            );

            log.info("[AI-REPORT] analyzeReport 정상 종료 reportId={} result=AUTO_PROCESSED", reportId);
        } catch (AiProviderQuotaExceededException exception) {
            log.warn(
                    "[AI-REPORT] analyzeReport AI quota exceeded reportId={} stage={} exceptionType={}",
                    reportId,
                    stage,
                    exception.getClass().getSimpleName()
            );
            if (report != null && report.getStatus() == ReportStatus.PENDING) {
                markFailedAndNotify(report, AiProviderQuotaExceededException.USER_MESSAGE);
                return;
            }
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "[AI-REPORT] analyzeReport 예외 발생 reportId={} stage={} exceptionType={} message={}",
                    reportId,
                    stage,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
            if (report != null && report.getStatus() == ReportStatus.PENDING) {
                markFailedAndNotify(report, "AI 분석을 완료하지 못해 관리자 검토가 필요합니다.");
                return;
            }
            throw exception;
        }
    }

    private int hideRelatedItemsSafely(
            Report report,
            ModerationResult result,
        AutoHideBasis autoHideBasis
    ) {
        try {
            report.getReporter().getMemberId();
            return reportRelatedActionService.hideRelatedItemsAfterAiAutoProcess(
                    report,
                    result,
                    autoHideBasis
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[AI-REPORT] related hide failed but target auto process remains reportId={} targetType={} category={} exceptionType={} message={}",
                    report.getReportId(),
                    report.getTargetType(),
                    result.category(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
            return 0;
        }
    }

    private boolean requiresImageReview(
            Report report,
            ModerationTargetContentService.TargetContentSnapshot snapshot
    ) {
        return snapshot.hasAttachments() && !snapshot.substantiveText();
    }

    private AutoHideBasis autoHideBasis(
            ModerationResult result,
            boolean ruleMatched,
            boolean exclusionMatched,
            boolean canAutoHide
    ) {
        if (exclusionMatched) {
            return AutoHideBasis.EXCLUDED_CONTEXT_REVIEW_REQUIRED;
        }
        if (!autoHideEligibleCategory(result.category())) {
            return AutoHideBasis.NOT_ELIGIBLE_CATEGORY;
        }
        if (canAutoHide) {
            return ruleMatched ? AutoHideBasis.SERVER_RULE_MATCHED : AutoHideBasis.AI_HIGH_CONFIDENCE;
        }
        if (result.decision() == ModerationDecision.REVIEW_REQUIRED
                && highRiskReviewRequired(result)) {
            return AutoHideBasis.HIGH_RISK_REVIEW_REQUIRED;
        }
        if (result.decision() == ModerationDecision.REVIEW_REQUIRED) {
            return AutoHideBasis.REVIEW_REQUIRED;
        }
        return AutoHideBasis.NOT_MATCHED;
    }

    private boolean autoHideEligibleCategory(ModerationCategory category) {
        return category == ModerationCategory.SPAM_AD
                || category == ModerationCategory.PERSONAL_INFO
                || category == ModerationCategory.FLOODING
                || category == ModerationCategory.ABUSE
                || category == ModerationCategory.SEXUAL_HARASSMENT;
    }

    private boolean highRiskReviewRequired(ModerationResult result) {
        return result.decision() == ModerationDecision.REVIEW_REQUIRED
                && result.riskScore() != null
                && result.confidenceScore() != null
                && result.riskScore().compareTo(new BigDecimal("0.90")) >= 0
                && result.confidenceScore().compareTo(new BigDecimal("0.90")) >= 0;
    }

    private void logModerationDecision(
            Report report,
            ModerationResult result,
            boolean ruleMatched,
            boolean exclusionMatched,
            AutoHideBasis autoHideBasis,
            boolean canAutoHide,
            boolean downgradedToReview,
            AiProcessingStatus processingStatus
    ) {
        log.info(
                "[AI-REPORT] 자동 처리 기준 진단 reportId={} targetType={} category={} decision={} riskScore={} confidenceScore={} ruleMatched={} exclusionMatched={} autoHideBasis={} canAutoHide={} downgradeToReview={} finalDecision={} processingStatus={}",
                report.getReportId(),
                report.getTargetType(),
                result.category(),
                result.decision(),
                result.riskScore(),
                result.confidenceScore(),
                ruleMatched,
                exclusionMatched,
                autoHideBasis,
                canAutoHide,
                downgradedToReview,
                result.decision(),
                processingStatus
        );
    }

    private ModerationResult downgradeToReview(ModerationResult result) {
        return new ModerationResult(
                result.policyVersion(), result.category(),
                com.example.haksikmokjang.ai.moderation.domain.ModerationDecision.REVIEW_REQUIRED,
                result.riskScore(), result.confidenceScore(), result.policyCodes(),
                result.containsPersonalInfo(), result.personalInfoTypes(), result.ruleEvidenceRequired(),
                result.reasonSummary(), result.evidence(), result.missingContext(),
                result.alternativeCategory(), result.alternativeReason(), result.modelName()
        );
    }

    private ModerationResult imageReviewResult() {
        return new ModerationResult(
                "MODERATION_POLICY_V1",
                com.example.haksikmokjang.ai.moderation.domain.ModerationCategory.OTHER,
                com.example.haksikmokjang.ai.moderation.domain.ModerationDecision.REVIEW_REQUIRED,
                new BigDecimal("0.30"),
                new BigDecimal("0.99"),
                List.of("IMAGE-NOT-ANALYZED"),
                false,
                List.of(),
                true,
                "첨부 이미지는 AI 분석 대상이 아니며 텍스트 근거가 부족해 관리자 검토가 필요합니다.",
                "mock-moderation-v1"
        );
    }

    private void notifyAutoProcessed(Report report, ModerationResult result, Member targetWriter) {
        notifyAdmins(
                report.getReportId(),
                "ADMIN_AI_REPORT_PROCESSED",
                "AI가 신고 #" + report.getReportId() + "을 자동 처리했습니다.",
                adminDetail(report, result)
        );
        notificationService.sendNotification(
                report.getReporter(),
                "REPORT",
                "신고 처리 완료",
                "신고하신 내용이 정책 위반으로 확인되어 처리되었습니다.",
                "REPORT",
                report.getReportId()
        );
        if (targetWriter != null) {
            notificationService.sendNotification(
                    targetWriter,
                    "REPORT",
                    targetLabel(report.getTargetType()) + " 숨김 처리 안내",
                    "작성하신 " + targetLabel(report.getTargetType())
                            + "이(가) 신고 처리 기준에 따라 숨김 처리되었습니다. 관리자 검토를 통해 복구될 수 있으며 문의가 필요한 경우 관리자에게 요청해 주세요.",
                    "REPORT",
                    report.getReportId()
            );
        }
    }

    private void notifyReviewRequired(Report report, ModerationResult result) {
        notifyAdmins(
                report.getReportId(),
                "ADMIN_AI_REVIEW_REQUIRED",
                "AI가 신고 #" + report.getReportId() + "를 관리자 검토 대상으로 분류했습니다.",
                adminDetail(report, result)
        );
        notificationService.sendNotification(
                report.getReporter(),
                "REPORT",
                "신고 검토 중",
                "신고하신 내용은 관리자 검토가 필요한 사안으로 접수되었습니다.",
                "REPORT",
                report.getReportId()
        );
    }

    private void markFailedAndNotify(Report report, String reason) {
        if (!analysisRepository.existsByReport(report)) {
            analysisRepository.save(AiModerationAnalysis.failed(report, reason, "AI_PROVIDER"));
        }
        notifyAdmins(
                report.getReportId(),
                "ADMIN_AI_FAILED",
                "AI 신고 분석에 실패했습니다. 수동 검토가 필요합니다.",
                "신고 ID=" + report.getReportId() + ", 대상=" + report.getTargetType()
                        + ", AI 유형=OTHER, decision=REVIEW_REQUIRED, riskScore=0, confidenceScore=0"
                        + ", reasonSummary=" + reason
                        + ", 상세=/api/view/admin/reports?reportId=" + report.getReportId()
        );
        notificationService.sendNotification(
                report.getReporter(),
                "REPORT",
                "신고 검토 중",
                "신고가 정상 접수되었으며 관리자 검토가 진행될 예정입니다.",
                "REPORT",
                report.getReportId()
        );
    }

    private void notifyAdmins(Long reportId, String type, String title, String content) {
        memberRepository.findAllByRole(MemberRole.ADMIN).forEach(admin ->
                notificationService.sendNotification(
                        admin, type, title, content, "ADMIN_REPORT", reportId
                )
        );
    }

    private String adminDetail(Report report, ModerationResult result) {
        return "신고 ID=" + report.getReportId()
                + ", 대상=" + report.getTargetType()
                + ", AI 유형=" + result.category()
                + ", decision=" + result.decision()
                + ", riskScore=" + result.riskScore()
                + ", confidenceScore=" + result.confidenceScore()
                + ", reasonSummary=" + result.reasonSummary()
                + ", 상세=/api/view/admin/reports?reportId=" + report.getReportId();
    }

    private String targetLabel(String targetType) {
        return switch (targetType) {
            case "POST" -> "게시글";
            case "COMMENT" -> "댓글";
            case "CHAT_MESSAGE" -> "채팅 메시지";
            case "REVIEW" -> "리뷰";
            default -> "콘텐츠";
        };
    }

    private boolean isProcessableTarget(String targetType) {
        return "POST".equals(targetType)
                || "COMMENT".equals(targetType)
                || "CHAT_MESSAGE".equals(targetType)
                || "REVIEW".equals(targetType);
    }
}
