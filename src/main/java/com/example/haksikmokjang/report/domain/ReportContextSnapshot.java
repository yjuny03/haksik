package com.example.haksikmokjang.report.domain;

import com.example.haksikmokjang.global.entity.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REPORT_CONTEXT_SNAPSHOT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportContextSnapshot extends CreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_context_snapshot_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private Report report;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Lob
    @Column(name = "ai_input_text", nullable = false, columnDefinition = "TEXT")
    private String aiInputText;

    public ReportContextSnapshot(
            Report report,
            String targetType,
            Long targetId,
            String snapshotJson,
            String aiInputText
    ) {
        this.report = report;
        this.targetType = targetType;
        this.targetId = targetId;
        this.snapshotJson = snapshotJson == null ? "{}" : snapshotJson;
        this.aiInputText = aiInputText == null ? "" : aiInputText;
    }
}
