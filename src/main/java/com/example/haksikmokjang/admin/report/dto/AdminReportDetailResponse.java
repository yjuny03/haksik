package com.example.haksikmokjang.admin.report.dto;

import com.example.haksikmokjang.ai.moderation.dto.AiModerationAnalysisResponse;
import com.example.haksikmokjang.fileattachment.dto.FileAttachmentResponse;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import com.example.haksikmokjang.report.domain.ReportStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminReportDetailResponse {

    private Long reportId;

    private Long reporterId;
    private String reporterLoginId;
    private String reporterEmail;

    private String targetType;
    private Long targetId;

    private String reason;
    private ReportStatus status;

    private String processedByLoginId;
    private LocalDateTime processedAt;
    private String processedReason;
    private ReportProcessorType processorType;
    private AiModerationAnalysisResponse aiAnalysis;

    private String targetTitle;
    private String targetContent;
    private String targetWriterLoginId;
    private String targetStatus;
    private List<FileAttachmentResponse> attachments;
    private String contextSnapshotJson;
    private List<ReportRelatedActionItemResponse> relatedActionItems;

    // 신고 상세 응답 변환
    public static AdminReportDetailResponse from(
            Report report,
            String targetTitle,
            String targetContent,
            String targetWriterLoginId,
            String targetStatus
    ) {
        return AdminReportDetailResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getMemberId())
                .reporterLoginId(report.getReporter().getLoginId())
                .reporterEmail(report.getReporter().getEmail())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetTitle(targetTitle)
                .targetContent(targetContent)
                .targetWriterLoginId(targetWriterLoginId)
                .targetStatus(targetStatus)
                .reason(report.getReason())
                .status(report.getStatus())
                .attachments(List.of())
                .contextSnapshotJson(null)
                .relatedActionItems(List.of())
                .processedByLoginId(
                        report.getProcessedBy() != null
                                ? report.getProcessedBy().getLoginId()
                                : null
                )
                .processedAt(report.getProcessedAt())
                .processedReason(report.getProcessedReason())
                .processorType(report.getProcessorType())
                .build();
    }
    // 첨부파일 목록만 붙여서 새 DTO로 다시 만듦
    public AdminReportDetailResponse withAttachments(List<FileAttachmentResponse> attachments) {
        return AdminReportDetailResponse.builder()
                .reportId(this.reportId)
                .reporterId(this.reporterId)
                .reporterLoginId(this.reporterLoginId)
                .reporterEmail(this.reporterEmail)
                .targetType(this.targetType)
                .targetId(this.targetId)
                .reason(this.reason)
                .status(this.status)
                .processedByLoginId(this.processedByLoginId)
                .processedAt(this.processedAt)
                .processedReason(this.processedReason)
                .processorType(this.processorType)
                .aiAnalysis(this.aiAnalysis)
                .targetTitle(this.targetTitle)
                .targetContent(this.targetContent)
                .targetWriterLoginId(this.targetWriterLoginId)
                .targetStatus(this.targetStatus)
                .attachments(attachments == null ? List.of() : attachments)
                .contextSnapshotJson(this.contextSnapshotJson)
                .relatedActionItems(this.relatedActionItems == null ? List.of() : this.relatedActionItems)
                .build();
    }

    public AdminReportDetailResponse withAiAnalysis(AiModerationAnalysisResponse aiAnalysis) {
        return AdminReportDetailResponse.builder()
                .reportId(this.reportId)
                .reporterId(this.reporterId)
                .reporterLoginId(this.reporterLoginId)
                .reporterEmail(this.reporterEmail)
                .targetType(this.targetType)
                .targetId(this.targetId)
                .reason(this.reason)
                .status(this.status)
                .processedByLoginId(this.processedByLoginId)
                .processedAt(this.processedAt)
                .processedReason(this.processedReason)
                .processorType(this.processorType)
                .aiAnalysis(aiAnalysis)
                .targetTitle(this.targetTitle)
                .targetContent(this.targetContent)
                .targetWriterLoginId(this.targetWriterLoginId)
                .targetStatus(this.targetStatus)
                .attachments(this.attachments == null ? List.of() : this.attachments)
                .contextSnapshotJson(this.contextSnapshotJson)
                .relatedActionItems(this.relatedActionItems == null ? List.of() : this.relatedActionItems)
                .build();
    }

    public AdminReportDetailResponse withContextSnapshotJson(String contextSnapshotJson) {
        return AdminReportDetailResponse.builder()
                .reportId(this.reportId)
                .reporterId(this.reporterId)
                .reporterLoginId(this.reporterLoginId)
                .reporterEmail(this.reporterEmail)
                .targetType(this.targetType)
                .targetId(this.targetId)
                .reason(this.reason)
                .status(this.status)
                .processedByLoginId(this.processedByLoginId)
                .processedAt(this.processedAt)
                .processedReason(this.processedReason)
                .processorType(this.processorType)
                .aiAnalysis(this.aiAnalysis)
                .targetTitle(this.targetTitle)
                .targetContent(this.targetContent)
                .targetWriterLoginId(this.targetWriterLoginId)
                .targetStatus(this.targetStatus)
                .attachments(this.attachments == null ? List.of() : this.attachments)
                .contextSnapshotJson(contextSnapshotJson)
                .relatedActionItems(this.relatedActionItems == null ? List.of() : this.relatedActionItems)
                .build();
    }

    public AdminReportDetailResponse withRelatedActionItems(List<ReportRelatedActionItemResponse> relatedActionItems) {
        return AdminReportDetailResponse.builder()
                .reportId(this.reportId)
                .reporterId(this.reporterId)
                .reporterLoginId(this.reporterLoginId)
                .reporterEmail(this.reporterEmail)
                .targetType(this.targetType)
                .targetId(this.targetId)
                .reason(this.reason)
                .status(this.status)
                .processedByLoginId(this.processedByLoginId)
                .processedAt(this.processedAt)
                .processedReason(this.processedReason)
                .processorType(this.processorType)
                .aiAnalysis(this.aiAnalysis)
                .targetTitle(this.targetTitle)
                .targetContent(this.targetContent)
                .targetWriterLoginId(this.targetWriterLoginId)
                .targetStatus(this.targetStatus)
                .attachments(this.attachments == null ? List.of() : this.attachments)
                .contextSnapshotJson(this.contextSnapshotJson)
                .relatedActionItems(relatedActionItems == null ? List.of() : relatedActionItems)
                .build();
    }
}
