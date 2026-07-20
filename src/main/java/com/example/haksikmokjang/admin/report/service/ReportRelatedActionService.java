package com.example.haksikmokjang.admin.report.service;

import com.example.haksikmokjang.admin.report.domain.ReportRelatedActionItem;
import com.example.haksikmokjang.admin.report.repository.ReportRelatedActionItemRepository;
import com.example.haksikmokjang.ai.moderation.domain.AutoHideBasis;
import com.example.haksikmokjang.ai.moderation.domain.ModerationCategory;
import com.example.haksikmokjang.ai.moderation.domain.ModerationDecision;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;
import com.example.haksikmokjang.ai.moderation.service.ModerationRuleValidationService;
import com.example.haksikmokjang.chat.chatmessage.domain.ChatMessage;
import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.domain.CommentStatus;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.repository.ReportContextSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportRelatedActionService {

    private static final String TARGET_TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";
    private static final String TARGET_TYPE_COMMENT = "COMMENT";
    private static final String ACTION_TYPE_AI_RELATED_HIDE = "AI_RELATED_HIDE";

    private final ReportRelatedActionItemRepository relatedActionItemRepository;
    private final ReportContextSnapshotRepository snapshotRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CommentRepository commentRepository;
    private final ModerationRuleValidationService ruleValidationService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int hideRelatedItemsAfterAiAutoProcess(
            Report report,
            ModerationResult result,
            AutoHideBasis autoHideBasis
    ) {
        if (eligibleForRelatedCommentHide(report, result, autoHideBasis)) {
            return hideRelatedCommentItems(report, result.category(), autoHideBasis);
        }

        if (!eligibleForRelatedChatMessageHide(report, result, autoHideBasis)) {
            return 0;
        }

        ChatMessage reportedMessage = chatMessageRepository.findById(report.getTargetId()).orElse(null);
        if (reportedMessage == null) {
            return 0;
        }

        List<Long> snapshotMessageIds = snapshotChatMessageIds(report);
        if (snapshotMessageIds.isEmpty()) {
            return 0;
        }

        List<ChatMessage> sameSenderMessages = snapshotMessageIds.stream()
                .filter(messageId -> !messageId.equals(report.getTargetId()))
                .map(messageId -> chatMessageRepository.findById(messageId).orElse(null))
                .filter(message -> isSameSenderVisibleMessage(report, reportedMessage, message))
                .toList();

        if (sameSenderMessages.isEmpty() || !categoryContextMatched(result.category(), reportedMessage, sameSenderMessages)) {
            return 0;
        }

        int hiddenCount = 0;
        for (ChatMessage message : sameSenderMessages) {
            if (!relatedCandidate(result.category(), message)) {
                continue;
            }
            if (relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                    report.getReportId(),
                    TARGET_TYPE_CHAT_MESSAGE,
                    message.getChatMessageId(),
                    ACTION_TYPE_AI_RELATED_HIDE
            )) {
                continue;
            }

            Boolean beforeDeleted = message.isDeleted();
            message.delete();
            relatedActionItemRepository.save(new ReportRelatedActionItem(
                    report.getReportId(),
                    TARGET_TYPE_CHAT_MESSAGE,
                    message.getChatMessageId(),
                    beforeDeleted,
                    message.isDeleted(),
                    ACTION_TYPE_AI_RELATED_HIDE,
                    relatedHideReason(result.category())
            ));
            hiddenCount++;
        }

        if (hiddenCount > 0) {
            log.info(
                    "[AI-REPORT] related chat messages hidden reportId={} category={} autoHideBasis={} hiddenCount={}",
                    report.getReportId(),
                    result.category(),
                    autoHideBasis,
                    hiddenCount
            );
        }
        return hiddenCount;
    }

    @Transactional
    public int restoreRelatedItems(Long reportId) {
        List<ReportRelatedActionItem> items = relatedActionItemRepository
                .findByReportIdAndRestoredFalseOrderByIdAsc(reportId);
        int restoredCount = 0;

        for (ReportRelatedActionItem item : items) {
            if (!ACTION_TYPE_AI_RELATED_HIDE.equals(item.getActionType())) {
                continue;
            }

            boolean restored = restoreRelatedItem(item);
            if (!restored) {
                log.warn(
                        "[AI-REPORT] related item restore skipped reportId={} targetType={} targetId={} actionType={}",
                        item.getReportId(),
                        item.getTargetType(),
                        item.getTargetId(),
                        item.getActionType()
                );
                continue;
            }
            item.markRestored();
            restoredCount++;
        }

        if (restoredCount > 0) {
            log.info("[AI-REPORT] related chat messages restored reportId={} restoredCount={}", reportId, restoredCount);
        }
        return restoredCount;
    }

    private boolean eligibleForRelatedChatMessageHide(
            Report report,
            ModerationResult result,
            AutoHideBasis autoHideBasis
    ) {
        return TARGET_TYPE_CHAT_MESSAGE.equals(report.getTargetType())
                && result.decision() == ModerationDecision.AUTO_HIDE
                && (autoHideBasis == AutoHideBasis.SERVER_RULE_MATCHED
                || autoHideBasis == AutoHideBasis.AI_HIGH_CONFIDENCE)
                && (result.category() == ModerationCategory.SPAM_AD
                || result.category() == ModerationCategory.FLOODING
                || result.category() == ModerationCategory.ABUSE
                || result.category() == ModerationCategory.SEXUAL_HARASSMENT
                || result.category() == ModerationCategory.PERSONAL_INFO);
    }

    private boolean eligibleForRelatedCommentHide(
            Report report,
            ModerationResult result,
            AutoHideBasis autoHideBasis
    ) {
        return TARGET_TYPE_COMMENT.equals(report.getTargetType())
                && result.decision() == ModerationDecision.AUTO_HIDE
                && (autoHideBasis == AutoHideBasis.SERVER_RULE_MATCHED
                || autoHideBasis == AutoHideBasis.AI_HIGH_CONFIDENCE)
                && (result.category() == ModerationCategory.ABUSE
                || result.category() == ModerationCategory.SEXUAL_HARASSMENT
                || result.category() == ModerationCategory.SPAM_AD
                || result.category() == ModerationCategory.FLOODING
                || result.category() == ModerationCategory.PERSONAL_INFO);
    }

    private List<Long> snapshotChatMessageIds(Report report) {
        return snapshotRepository.findByReport(report)
                .map(snapshot -> parseSnapshotChatMessageIds(report.getReportId(), snapshot.getSnapshotJson()))
                .orElse(List.of());
    }

    private List<Long> snapshotCommentIds(Report report) {
        return snapshotRepository.findByReport(report)
                .map(snapshot -> parseSnapshotCommentIds(report.getReportId(), snapshot.getSnapshotJson()))
                .orElse(List.of());
    }

    private List<Long> parseSnapshotChatMessageIds(Long reportId, String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode messages = root.path("context").path("messages");
            if (!messages.isArray()) {
                return List.of();
            }

            List<Long> ids = new ArrayList<>();
            for (JsonNode message : messages) {
                JsonNode id = message.path("chatMessageId");
                if (id.canConvertToLong()) {
                    ids.add(id.asLong());
                }
            }
            return ids;
        } catch (Exception exception) {
            log.warn(
                    "[AI-REPORT] related message snapshot parse failed reportId={} exceptionType={} message={}",
                    reportId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return List.of();
        }
    }

    private List<Long> parseSnapshotCommentIds(Long reportId, String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode comments = root.path("context").path("comments");
            if (!comments.isArray()) {
                return List.of();
            }

            List<Long> ids = new ArrayList<>();
            for (JsonNode comment : comments) {
                JsonNode id = comment.path("commentId");
                if (id.canConvertToLong()) {
                    ids.add(id.asLong());
                }
            }
            return ids;
        } catch (Exception exception) {
            log.warn(
                    "[AI-REPORT] related comment snapshot parse failed reportId={} exceptionType={} message={}",
                    reportId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return List.of();
        }
    }

    private boolean isSameSenderVisibleMessage(Report report, ChatMessage reportedMessage, ChatMessage message) {
        return message != null
                && !message.isDeleted()
                && message.getChatRoom().getChatRoomId().equals(reportedMessage.getChatRoom().getChatRoomId())
                && message.getSender().getMemberId().equals(reportedMessage.getSender().getMemberId())
                && !message.getSender().getMemberId().equals(report.getReporter().getMemberId());
    }

    private boolean isSameWriterActiveComment(Report report, Comment reportedComment, Comment comment) {
        return comment != null
                && comment.getStatus() == CommentStatus.ACTIVE
                && comment.getPost().getPostId().equals(reportedComment.getPost().getPostId())
                && comment.getMember().getMemberId().equals(reportedComment.getMember().getMemberId())
                && !comment.getCommentId().equals(reportedComment.getCommentId())
                && !comment.getMember().getMemberId().equals(report.getReporter().getMemberId());
    }

    private int hideRelatedCommentItems(
            Report report,
            ModerationCategory category,
            AutoHideBasis autoHideBasis
    ) {
        Comment reportedComment = commentRepository.findById(report.getTargetId()).orElse(null);
        if (reportedComment == null) {
            return 0;
        }

        List<Long> snapshotCommentIds = snapshotCommentIds(report);
        if (snapshotCommentIds.isEmpty()) {
            return 0;
        }

        List<Comment> sameWriterActiveComments = snapshotCommentIds.stream()
                .map(commentId -> commentRepository.findById(commentId).orElse(null))
                .filter(comment -> isSameWriterActiveComment(report, reportedComment, comment))
                .toList();
        List<Comment> sameWriterCommentContext = new ArrayList<>();
        sameWriterCommentContext.add(reportedComment);
        sameWriterCommentContext.addAll(sameWriterActiveComments);

        int hiddenCount = 0;
        for (Comment comment : sameWriterActiveComments) {
            if (!relatedCommentCandidate(category, comment, sameWriterCommentContext)) {
                continue;
            }
            if (relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                    report.getReportId(),
                    TARGET_TYPE_COMMENT,
                    comment.getCommentId(),
                    ACTION_TYPE_AI_RELATED_HIDE
            )) {
                continue;
            }

            String beforeStatus = comment.getStatus().name();
            comment.hide();
            relatedActionItemRepository.save(new ReportRelatedActionItem(
                    report.getReportId(),
                    TARGET_TYPE_COMMENT,
                    comment.getCommentId(),
                    beforeStatus,
                    comment.getStatus().name(),
                    ACTION_TYPE_AI_RELATED_HIDE,
                    relatedHideReason(TARGET_TYPE_COMMENT, category)
            ));
            hiddenCount++;
        }

        if (hiddenCount > 0) {
            log.info(
                    "[AI-REPORT] related comments hidden reportId={} category={} autoHideBasis={} hiddenCount={}",
                    report.getReportId(),
                    category,
                    autoHideBasis,
                    hiddenCount
            );
        }
        return hiddenCount;
    }

    private boolean categoryContextMatched(
            ModerationCategory category,
            ChatMessage reportedMessage,
            List<ChatMessage> messages
    ) {
        if (category == ModerationCategory.FLOODING
                || category == ModerationCategory.ABUSE
                || category == ModerationCategory.SEXUAL_HARASSMENT
                || category == ModerationCategory.PERSONAL_INFO) {
            return true;
        }
        String joined = messages.stream()
                .map(ChatMessage::getMessage)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return ruleValidationService.spamContextMatched(reportedMessage.getMessage() + "\n" + joined);
    }

    private boolean relatedCandidate(ModerationCategory category, ChatMessage message) {
        if (category == ModerationCategory.SPAM_AD) {
            return ruleValidationService.spamRelatedMessageCandidate(message.getMessage());
        }
        if (category == ModerationCategory.FLOODING) {
            return ruleValidationService.floodingMessageMatched(message.getMessage());
        }
        if (category == ModerationCategory.ABUSE) {
            ModerationRuleValidationService.CategoryRuleEvidence evidence =
                    ruleValidationService.inspectForCategory(ModerationCategory.ABUSE, message.getMessage());
            return !evidence.exclusionMatched()
                    && (evidence.ruleMatched()
                    || ruleValidationService.abuseRelatedMessageCandidate(message.getMessage()));
        }
        if (category == ModerationCategory.SEXUAL_HARASSMENT) {
            return ruleValidationService.sexualHarassmentRelatedMessageCandidate(message.getMessage());
        }
        if (category == ModerationCategory.PERSONAL_INFO) {
            ModerationRuleValidationService.CategoryRuleEvidence evidence =
                    ruleValidationService.inspectForCategory(ModerationCategory.PERSONAL_INFO, message.getMessage());
            return !evidence.exclusionMatched()
                    && ruleValidationService.personalInfoRelatedMessageCandidate(message.getMessage());
        }
        return false;
    }

    private boolean relatedCommentCandidate(
            ModerationCategory category,
            Comment comment,
            List<Comment> sameWriterActiveComments
    ) {
        if (category == ModerationCategory.ABUSE) {
            return ruleValidationService.abuseRelatedCommentCandidate(comment.getContent());
        }
        if (category == ModerationCategory.SEXUAL_HARASSMENT) {
            return ruleValidationService.sexualHarassmentRelatedCommentCandidate(comment.getContent());
        }
        if (category == ModerationCategory.SPAM_AD) {
            return ruleValidationService.spamRelatedCommentCandidate(comment.getContent());
        }
        if (category == ModerationCategory.FLOODING) {
            return floodingRelatedCommentCandidate(comment, sameWriterActiveComments);
        }
        if (category == ModerationCategory.PERSONAL_INFO) {
            ModerationRuleValidationService.CategoryRuleEvidence evidence =
                    ruleValidationService.inspectForCategory(ModerationCategory.PERSONAL_INFO, comment.getContent());
            return !evidence.exclusionMatched()
                    && ruleValidationService.personalInfoRelatedCommentCandidate(comment.getContent());
        }
        return false;
    }

    private boolean floodingRelatedCommentCandidate(Comment comment, List<Comment> sameWriterActiveComments) {
        String normalized = normalizeFloodingComment(comment.getContent());
        if (normalized.isBlank()) {
            return false;
        }
        long sameContentCount = sameWriterActiveComments.stream()
                .map(candidate -> normalizeFloodingComment(candidate.getContent()))
                .filter(normalized::equals)
                .count();
        return sameContentCount >= 2
                || ruleValidationService.floodingMessageMatched(comment.getContent());
    }

    private String normalizeFloodingComment(String content) {
        if (content == null) {
            return "";
        }
        return content.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[~!?.。！？]+$", "")
                .trim();
    }

    private boolean restoreRelatedItem(ReportRelatedActionItem item) {
        if (TARGET_TYPE_CHAT_MESSAGE.equals(item.getTargetType())) {
            ChatMessage message = chatMessageRepository.findById(item.getTargetId()).orElse(null);
            if (message == null) {
                return false;
            }
            if (!message.isDeleted()) {
                return false;
            }
            message.restoreDeleted(item.getBeforeDeleted());
            return true;
        }

        if (TARGET_TYPE_COMMENT.equals(item.getTargetType())) {
            Comment comment = commentRepository.findById(item.getTargetId()).orElse(null);
            if (comment == null || item.getBeforeStatus() == null) {
                return false;
            }
            if (comment.getStatus() != CommentStatus.HIDDEN) {
                return false;
            }
            comment.restoreStatus(item.getBeforeStatus());
            return true;
        }

        return false;
    }

    private String relatedHideReason(ModerationCategory category) {
        return switch (category) {
            case SPAM_AD -> "AI가 광고/스팸 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.";
            case FLOODING -> "AI가 도배 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.";
            case ABUSE -> "AI가 욕설/비방 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.";
            case SEXUAL_HARASSMENT -> "AI가 성적 괴롭힘 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.";
            case PERSONAL_INFO -> "AI가 개인정보 노출 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.";
            default -> "AI 자동 처리 맥락에 따라 관련 메시지를 함께 블라인드 처리했습니다.";
        };
    }

    private String relatedHideReason(String targetType, ModerationCategory category) {
        if (TARGET_TYPE_COMMENT.equals(targetType)) {
            return switch (category) {
                case ABUSE -> "AI가 욕설/비방 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.";
                case SEXUAL_HARASSMENT -> "AI가 성적 괴롭힘 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.";
                case SPAM_AD -> "AI가 광고/스팸 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.";
                case FLOODING -> "AI가 도배 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.";
                case PERSONAL_INFO -> "AI가 개인정보 노출 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.";
                default -> "AI 자동 처리 맥락에 따라 관련 댓글을 함께 블라인드 처리했습니다.";
            };
        }
        return relatedHideReason(category);
    }
}
