package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.report.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportCreatedModerationListener {

    private final AiModerationService aiModerationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReportCreatedEvent event) {
        try {
            aiModerationService.analyzeReport(event.reportId());
        } catch (RuntimeException exception) {
            // 신고 접수 커밋은 AI 분석 실패와 분리한다. 원문/개인정보는 로그에 남기지 않는다.
            log.error("Mock AI moderation failed. reportId={}", event.reportId(), exception);
        }
    }
}
