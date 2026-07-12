package com.example.haksikmokjang.admin.report.service;

import com.example.haksikmokjang.chat.chatmessage.domain.ChatMessage;
import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoom;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomMemberRepository;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.domain.CommentStatus;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.domain.PostStatus;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.report.domain.Report;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportedTargetRestoreTest {

    private final Member writer = mock(Member.class);

    @Test
    void postAndCommentReturnFromHiddenToTheirPreviousStatus() {
        PostRepository postRepository = mock(PostRepository.class);
        CommentRepository commentRepository = mock(CommentRepository.class);
        AdminCommunityReportService service = new AdminCommunityReportService(postRepository, commentRepository);

        Post post = Post.builder().member(writer).title("제목").content("내용").anonymousYn("N").build();
        Comment comment = Comment.builder().post(post).member(writer).content("댓글").anonymousYn("N").build();
        Report postReport = report("POST");
        Report commentReport = report("COMMENT");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        service.hideCommunityTarget(postReport);
        service.hideCommunityTarget(commentReport);
        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.HIDDEN);

        service.restoreCommunityTarget(postReport, "ACTIVE");
        service.restoreCommunityTarget(commentReport, "ACTIVE");
        assertThat(post.getStatus()).isEqualTo(PostStatus.ACTIVE);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.ACTIVE);
    }

    @Test
    void chatMessageReturnsFromDeletedToVisibleWithOriginalMessage() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        AdminChatReportService service = new AdminChatReportService(
                messageRepository,
                mock(ChatRoomMemberRepository.class)
        );
        ChatMessage message = new ChatMessage(mock(ChatRoom.class), writer, "복구할 원문");
        Report report = report("CHAT_MESSAGE");
        when(messageRepository.findById(10L)).thenReturn(Optional.of(message));

        service.hideChatMessage(report);
        assertThat(message.isDeleted()).isTrue();

        service.restoreChatMessage(report, false);
        assertThat(message.isDeleted()).isFalse();
        assertThat(message.getMessage()).isEqualTo("복구할 원문");
    }

    @Test
    void reviewReturnsFromDeletedToItsPreviousStatus() {
        StoreReviewRepository reviewRepository = mock(StoreReviewRepository.class);
        AdminReviewReportService service = new AdminReviewReportService(reviewRepository);
        StoreReview review = StoreReview.builder()
                .member(writer)
                .rating(5)
                .content("복구할 리뷰")
                .status(ReviewStatus.ACTIVE)
                .build();
        Report report = report("REVIEW");
        when(reviewRepository.findById(10L)).thenReturn(Optional.of(review));

        service.hideReview(report);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);

        service.restoreReview(report, "ACTIVE");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(review.getContent()).isEqualTo("복구할 리뷰");
    }

    private Report report(String targetType) {
        return Report.builder()
                .reporter(writer)
                .targetType(targetType)
                .targetId(10L)
                .reason("신고")
                .build();
    }
}
