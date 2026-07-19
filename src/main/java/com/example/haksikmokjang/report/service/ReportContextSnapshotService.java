package com.example.haksikmokjang.report.service;

import com.example.haksikmokjang.chat.chatmessage.domain.ChatMessage;
import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.domain.CommentStatus;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportContextSnapshot;
import com.example.haksikmokjang.report.repository.ReportContextSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportContextSnapshotService {

    private static final int POST_CONTENT_LIMIT = 1200;
    private static final int CONTEXT_CONTENT_LIMIT = 500;

    private final ReportContextSnapshotRepository snapshotRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createSnapshot(Report report) {
        if (snapshotRepository.existsByReport(report)) {
            return;
        }

        SnapshotData snapshotData = switch (report.getTargetType()) {
            case "COMMENT" -> commentSnapshot(report);
            case "CHAT_MESSAGE" -> chatMessageSnapshot(report);
            case "POST" -> postSnapshot(report);
            case "REVIEW" -> reviewSnapshot(report);
            default -> defaultSnapshot(report);
        };

        snapshotRepository.save(new ReportContextSnapshot(
                report,
                report.getTargetType(),
                report.getTargetId(),
                snapshotData.snapshotJson(),
                snapshotData.aiInputText()
        ));
    }

    private SnapshotData commentSnapshot(Report report) {
        Comment reported = commentRepository.findById(report.getTargetId()).orElse(null);
        if (reported == null) {
            return missingSnapshot(report, "신고 대상 댓글이 삭제되었거나 존재하지 않습니다.");
        }

        Post post = reported.getPost();
        List<Comment> allComments = commentRepository.findByPostAndStatusOrderByCommentIdAsc(post, CommentStatus.ACTIVE);
        int reportedIndex = indexOfComment(allComments, reported.getCommentId());
        List<Comment> selected = window(allComments, reportedIndex, 3, 3);

        List<Map<String, Object>> commentItems = selected.stream()
                .map(comment -> Map.<String, Object>of(
                        "commentId", comment.getCommentId(),
                        "writerLoginId", comment.getMember().getLoginId(),
                        "createdAt", timeText(comment.getCreatedAt()),
                        "content", truncate(comment.getContent(), CONTEXT_CONTENT_LIMIT),
                        "reportedTarget", comment.getCommentId().equals(reported.getCommentId())
                ))
                .toList();

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("targetType", "COMMENT");
        json.put("targetId", reported.getCommentId());
        json.put("reportedTarget", Map.of(
                "commentId", reported.getCommentId(),
                "content", truncate(reported.getContent(), CONTEXT_CONTENT_LIMIT),
                "writerLoginId", reported.getMember().getLoginId()
        ));
        json.put("context", Map.of(
                "post", Map.of(
                        "postId", post.getPostId(),
                        "title", truncate(post.getTitle(), 200),
                        "content", truncate(post.getContent(), POST_CONTENT_LIMIT),
                        "writerLoginId", post.getMember().getLoginId()
                ),
                "comments", commentItems
        ));

        String aiInput = """
                reported_target:
                [COMMENT #%s]
                %s
                
                context:
                [PARENT_POST #%s]
                title: %s
                content: %s
                
                [COMMENTS_AROUND_TARGET]
                %s""".formatted(
                reported.getCommentId(),
                safeText(reported.getContent()),
                post.getPostId(),
                safeText(post.getTitle()),
                safeText(truncate(post.getContent(), POST_CONTENT_LIMIT)),
                commentContextText(commentItems)
        );

        return new SnapshotData(toJson(json), aiInput);
    }

    private SnapshotData chatMessageSnapshot(Report report) {
        ChatMessage reported = chatMessageRepository.findById(report.getTargetId()).orElse(null);
        if (reported == null) {
            return missingSnapshot(report, "신고 대상 채팅 메시지가 삭제되었거나 존재하지 않습니다.");
        }

        List<ChatMessage> roomMessages = chatMessageRepository.findAllByChatRoomOrderByCreatedAtAsc(reported.getChatRoom())
                .stream()
                .filter(message -> !message.isDeleted() || message.getChatMessageId().equals(reported.getChatMessageId()))
                .toList();
        int reportedIndex = indexOfMessage(roomMessages, reported.getChatMessageId());
        List<ChatMessage> selected = window(roomMessages, reportedIndex, 5, 2);

        List<Map<String, Object>> messageItems = selected.stream()
                .map(message -> Map.<String, Object>of(
                        "chatMessageId", message.getChatMessageId(),
                        "senderLoginId", message.getSender().getLoginId(),
                        "createdAt", timeText(message.getCreatedAt()),
                        "messageType", message.getMessageType().name(),
                        "message", truncate(message.getMessage(), CONTEXT_CONTENT_LIMIT),
                        "reportedTarget", message.getChatMessageId().equals(reported.getChatMessageId())
                ))
                .toList();

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("targetType", "CHAT_MESSAGE");
        json.put("targetId", reported.getChatMessageId());
        json.put("reportedTarget", Map.of(
                "chatMessageId", reported.getChatMessageId(),
                "senderLoginId", reported.getSender().getLoginId(),
                "messageType", reported.getMessageType().name(),
                "message", truncate(reported.getMessage(), CONTEXT_CONTENT_LIMIT)
        ));
        json.put("context", Map.of(
                "chatRoomId", reported.getChatRoom().getChatRoomId(),
                "messages", messageItems
        ));

        String aiInput = """
                reported_target:
                [CHAT_MESSAGE #%s]
                sender: %s
                message: %s
                
                context:
                [CHAT_MESSAGES_AROUND_TARGET]
                %s""".formatted(
                reported.getChatMessageId(),
                reported.getSender().getLoginId(),
                safeText(reported.getMessage()),
                chatContextText(messageItems)
        );

        return new SnapshotData(toJson(json), aiInput);
    }

    private SnapshotData postSnapshot(Report report) {
        return postRepository.findById(report.getTargetId())
                .map(post -> {
                    String target = joinText(post.getTitle(), post.getContent());
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("targetType", "POST");
                    json.put("targetId", post.getPostId());
                    json.put("reportedTarget", Map.of(
                            "postId", post.getPostId(),
                            "title", truncate(post.getTitle(), 200),
                            "content", truncate(post.getContent(), POST_CONTENT_LIMIT),
                            "writerLoginId", post.getMember().getLoginId()
                    ));
                    json.put("context", Map.of("usage", "POST 자동 처리는 신고 게시글 제목/본문만 사용합니다."));
                    return new SnapshotData(toJson(json), target);
                })
                .orElseGet(() -> missingSnapshot(report, "신고 대상 게시글이 삭제되었거나 존재하지 않습니다."));
    }

    private SnapshotData reviewSnapshot(Report report) {
        return storeReviewRepository.findById(report.getTargetId())
                .map(review -> {
                    String content = review.getContent() == null ? "" : review.getContent();
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("targetType", "REVIEW");
                    json.put("targetId", report.getTargetId());
                    json.put("reportedTarget", Map.of(
                            "reviewId", report.getTargetId(),
                            "content", truncate(content, POST_CONTENT_LIMIT),
                            "writerLoginId", review.getMember().getLoginId()
                    ));
                    json.put("context", Map.of("usage", "REVIEW는 단일 리뷰 내용만 사용합니다."));
                    return new SnapshotData(toJson(json), content);
                })
                .orElseGet(() -> missingSnapshot(report, "신고 대상 리뷰가 삭제되었거나 존재하지 않습니다."));
    }

    private SnapshotData defaultSnapshot(Report report) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("targetType", report.getTargetType());
        json.put("targetId", report.getTargetId());
        json.put("reportedTarget", Map.of("description", "자동 처리 대상이 아니며 관리자 검토 중심입니다."));
        json.put("context", Map.of("usage", "MEMBER/CHAT_MEMBER 등 회원 신고는 과도한 활동 이력을 저장하지 않습니다."));
        return new SnapshotData(toJson(json), "");
    }

    private SnapshotData missingSnapshot(Report report, String message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("targetType", report.getTargetType());
        json.put("targetId", report.getTargetId());
        json.put("reportedTarget", Map.of("missing", true, "message", message));
        json.put("context", Map.of());
        return new SnapshotData(toJson(json), message);
    }

    private int indexOfComment(List<Comment> comments, Long commentId) {
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).getCommentId().equals(commentId)) {
                return i;
            }
        }
        return 0;
    }

    private int indexOfMessage(List<ChatMessage> messages, Long messageId) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getChatMessageId().equals(messageId)) {
                return i;
            }
        }
        return 0;
    }

    private <T> List<T> window(List<T> values, int index, int before, int after) {
        if (values.isEmpty()) {
            return List.of();
        }
        int safeIndex = Math.max(0, Math.min(index, values.size() - 1));
        int from = Math.max(0, safeIndex - before);
        int to = Math.min(values.size(), safeIndex + after + 1);
        return new ArrayList<>(values.subList(from, to));
    }

    private String commentContextText(List<Map<String, Object>> comments) {
        return comments.stream()
                .map(item -> "[COMMENT #%s reportedTarget=%s writer=%s]\n%s".formatted(
                        item.get("commentId"),
                        item.get("reportedTarget"),
                        item.get("writerLoginId"),
                        item.get("content")
                ))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String chatContextText(List<Map<String, Object>> messages) {
        return messages.stream()
                .map(item -> "[CHAT_MESSAGE #%s reportedTarget=%s sender=%s]\n%s".formatted(
                        item.get("chatMessageId"),
                        item.get("reportedTarget"),
                        item.get("senderLoginId"),
                        item.get("message")
                ))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String joinText(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        return (left + "\n" + right).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String timeText(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"SNAPSHOT_JSON_SERIALIZATION_FAILED\"}";
        }
    }

    private record SnapshotData(String snapshotJson, String aiInputText) {
    }
}
