package com.example.haksikmokjang.ai.moderation.repository;

import com.example.haksikmokjang.ai.moderation.domain.AiModerationAnalysis;
import com.example.haksikmokjang.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Limit;
import com.example.haksikmokjang.ai.moderation.domain.AiProcessingStatus;

public interface AiModerationAnalysisRepository extends JpaRepository<AiModerationAnalysis, Long> {

    Optional<AiModerationAnalysis> findByReport(Report report);

    boolean existsByReport(Report report);

    long countByProcessingStatus(AiProcessingStatus processingStatus);

    List<AiModerationAnalysis> findByProcessingStatusOrderByAnalyzedAtDesc(
            AiProcessingStatus processingStatus,
            Limit limit
    );
}
