package com.example.haksikmokjang.ai.moderation;

import com.example.haksikmokjang.ai.moderation.service.ModerationTargetContentService;
import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.fileattachment.domain.FileAttachment;
import com.example.haksikmokjang.fileattachment.repository.FileAttachmentRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.report.domain.Report;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationTargetContentServiceTest {

    @Test
    void postWithRequiredTitleButNoBodyAndAnImageIsTreatedAsImageOnly() {
        PostRepository postRepository = mock(PostRepository.class);
        FileAttachmentRepository attachmentRepository = mock(FileAttachmentRepository.class);
        ModerationTargetContentService service = new ModerationTargetContentService(
                postRepository,
                mock(CommentRepository.class),
                mock(ChatMessageRepository.class),
                mock(StoreReviewRepository.class),
                attachmentRepository
        );
        Post post = Post.builder().title("사진 신고").content(" ").anonymousYn("N").build();
        Report report = mock(Report.class);
        when(report.getTargetType()).thenReturn("POST");
        when(report.getTargetId()).thenReturn(10L);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(attachmentRepository.findByTargetTypeAndTargetId("POST", 10L))
                .thenReturn(List.of(mock(FileAttachment.class)));

        ModerationTargetContentService.TargetContentSnapshot snapshot = service.getSnapshot(report);

        assertThat(snapshot.hasAttachments()).isTrue();
        assertThat(snapshot.substantiveText()).isFalse();
        assertThat(snapshot.content()).contains("사진 신고");
    }
}
