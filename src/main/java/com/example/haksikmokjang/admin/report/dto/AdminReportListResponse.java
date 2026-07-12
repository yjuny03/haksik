package com.example.haksikmokjang.admin.report.dto;

import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import com.example.haksikmokjang.report.domain.ReportStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminReportListResponse {

    private Long reportId;

    private Long reporterId;
    private String reporterLoginId;

    private String targetType;
    private Long targetId;

    private String reason;
    private ReportStatus status;

    private String processedByLoginId;
    private LocalDateTime processedAt;
    private ReportProcessorType processorType;
    private long sameTargetReportCount;

    // 신고 목록 응답 변환
    public static AdminReportListResponse from(Report report, long sameTargetReportCount) {
        return AdminReportListResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getMemberId())
                .reporterLoginId(report.getReporter().getLoginId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .status(report.getStatus())
                .processedByLoginId(
                        report.getProcessedBy() != null
                                ? report.getProcessedBy().getLoginId()
                                : null
                )
                .processedAt(report.getProcessedAt())
                .processorType(report.getProcessorType())
                .sameTargetReportCount(sameTargetReportCount)
                .build();
    }
}
