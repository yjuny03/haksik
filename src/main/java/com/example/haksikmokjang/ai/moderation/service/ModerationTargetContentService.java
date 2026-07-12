package com.example.haksikmokjang.ai.moderation.service;

import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.report.domain.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModerationTargetContentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final FileAttachmentRepository fileAttachmentRepository;

    public String getContent(Report report) {
        return getSnapshot(report).content();
    }

    public TargetContentSnapshot getSnapshot(Report report) {
        String content = switch (report.getTargetType()) {
            case "POST" -> postRepository.findById(report.getTargetId())
                    .map(post -> joinText(post.getTitle(), post.getContent()))
                    .orElse("");
            case "COMMENT" -> commentRepository.findById(report.getTargetId())
                    .map(comment -> comment.getContent())
                    .orElse("");
            case "CHAT_MESSAGE" -> chatMessageRepository.findById(report.getTargetId())
                    .map(message -> message.getMessage())
                    .orElse("");
            case "REVIEW" -> storeReviewRepository.findById(report.getTargetId())
                    .map(review -> review.getContent() == null ? "" : review.getContent())
                    .orElse("");
            default -> "";
        };

        boolean hasAttachments = !fileAttachmentRepository
                .findByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId())
                .isEmpty();
        boolean substantiveText = "POST".equals(report.getTargetType())
                ? postRepository.findById(report.getTargetId())
                        .map(post -> !isNonTextPlaceholder(post.getContent()))
                        .orElse(false)
                : !isNonTextPlaceholder(content);
        return new TargetContentSnapshot(content, substantiveText, hasAttachments);
    }

    private String joinText(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        return (left + "\n" + right).trim();
    }

    private boolean isNonTextPlaceholder(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String normalized = content.replaceAll("\\s+", "").toLowerCase();
        return normalized.equals("이미지")
                || normalized.equals("사진")
                || normalized.equals("첨부이미지");
    }

    public record TargetContentSnapshot(
            String content,
            boolean substantiveText,
            boolean hasAttachments
    ) {
    }
}
