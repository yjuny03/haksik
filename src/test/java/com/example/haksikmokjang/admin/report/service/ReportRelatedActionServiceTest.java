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
import com.example.haksikmokjang.chat.chatroom.domain.ChatMatchingMode;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoom;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoomType;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.domain.CommentStatus;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.BoardType;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.domain.PostCategory;
import com.example.haksikmokjang.member.core.domain.AccountStatus;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportContextSnapshot;
import com.example.haksikmokjang.report.repository.ReportContextSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRelatedActionServiceTest {

    @Mock private ReportRelatedActionItemRepository relatedActionItemRepository;
    @Mock private ReportContextSnapshotRepository snapshotRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private CommentRepository commentRepository;

    private ReportRelatedActionService service;

    @BeforeEach
    void setUp() {
        service = new ReportRelatedActionService(
                relatedActionItemRepository,
                snapshotRepository,
                chatMessageRepository,
                commentRepository,
                new ModerationRuleValidationService(),
                new ObjectMapper()
        );
    }

    @Test
    void sexualHarassmentAutoHideHidesOnlyDirectRelatedMessagesFromSameSenderInSnapshot() {
        ChatRoom room = chatRoom(1L);
        Member reporter = member(100L);
        Member sender = member(200L);
        Member other = member(300L);

        Report report = report(reporter, 10L);
        ChatMessage reported = message(10L, room, sender, "너 모텔 가자");
        ChatMessage directRelated = message(11L, room, sender, "몸 보여줘");
        ChatMessage victimReport = message(12L, room, sender, "누가 저한테 몸 보여달라고 했어요");
        ChatMessage ordinaryPraise = message(13L, room, sender, "오늘 분위기가 좋아 보여요");
        ChatMessage reporterMessage = message(14L, room, reporter, "자자");
        ChatMessage otherSenderMessage = message(15L, room, other, "자자");

        when(chatMessageRepository.findById(10L)).thenReturn(Optional.of(reported));
        when(chatMessageRepository.findById(11L)).thenReturn(Optional.of(directRelated));
        when(chatMessageRepository.findById(12L)).thenReturn(Optional.of(victimReport));
        when(chatMessageRepository.findById(13L)).thenReturn(Optional.of(ordinaryPraise));
        when(chatMessageRepository.findById(14L)).thenReturn(Optional.of(reporterMessage));
        when(chatMessageRepository.findById(15L)).thenReturn(Optional.of(otherSenderMessage));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(snapshot(report, 10L, 11L, 12L, 13L, 14L, 15L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "CHAT_MESSAGE", 11L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                sexualHarassmentAutoHideResult(),
                AutoHideBasis.AI_HIGH_CONFIDENCE
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.isDeleted()).isTrue();
        assertThat(victimReport.isDeleted()).isFalse();
        assertThat(ordinaryPraise.isDeleted()).isFalse();
        assertThat(reporterMessage.isDeleted()).isFalse();
        assertThat(otherSenderMessage.isDeleted()).isFalse();

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        ReportRelatedActionItem item = captor.getValue();
        assertThat(item.getReportId()).isEqualTo(1L);
        assertThat(item.getTargetType()).isEqualTo("CHAT_MESSAGE");
        assertThat(item.getTargetId()).isEqualTo(11L);
        assertThat(item.getBeforeDeleted()).isFalse();
        assertThat(item.getAfterDeleted()).isTrue();
        assertThat(item.getReason()).isEqualTo("AI가 성적 괴롭힘 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.");
    }

    @Test
    void personalInfoAutoHideHidesOnlyDirectRelatedMessagesFromSameSenderInSnapshot() {
        ChatRoom room = chatRoom(1L);
        Member reporter = member(100L);
        Member sender = member(200L);
        Member other = member(300L);

        Report report = report(reporter, 60L);
        ChatMessage reported = message(60L, room, sender, "저 사람 번호 010-1111-2222 박제한다");
        ChatMessage directRelated = message(61L, room, sender, "그 사람 계좌 123-456-789012 찾아가라");
        ChatMessage businessContact = message(62L, room, sender, "가게 전화번호는 010-3333-4444입니다");
        ChatMessage victimReport = message(63L, room, sender, "누가 제 번호 010-5555-6666을 올렸어요");
        ChatMessage masked = message(64L, room, sender, "저 사람 번호 010-****-1234");
        ChatMessage otherSender = message(65L, room, other, "저 사람 번호 010-7777-8888 박제한다");

        when(chatMessageRepository.findById(60L)).thenReturn(Optional.of(reported));
        when(chatMessageRepository.findById(61L)).thenReturn(Optional.of(directRelated));
        when(chatMessageRepository.findById(62L)).thenReturn(Optional.of(businessContact));
        when(chatMessageRepository.findById(63L)).thenReturn(Optional.of(victimReport));
        when(chatMessageRepository.findById(64L)).thenReturn(Optional.of(masked));
        when(chatMessageRepository.findById(65L)).thenReturn(Optional.of(otherSender));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(snapshot(report, 60L, 61L, 62L, 63L, 64L, 65L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "CHAT_MESSAGE", 61L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                autoHideResult(ModerationCategory.PERSONAL_INFO),
                AutoHideBasis.SERVER_RULE_MATCHED
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.isDeleted()).isTrue();
        assertThat(businessContact.isDeleted()).isFalse();
        assertThat(victimReport.isDeleted()).isFalse();
        assertThat(masked.isDeleted()).isFalse();
        assertThat(otherSender.isDeleted()).isFalse();

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo("CHAT_MESSAGE");
        assertThat(captor.getValue().getTargetId()).isEqualTo(61L);
        assertThat(captor.getValue().getReason()).isEqualTo("AI가 개인정보 노출 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다.");
    }

    @Test
    void commentAbuseAutoHideHidesOnlyDirectRelatedCommentsFromSameWriterInSnapshot() {
        Post post = post(1L);
        Post otherPost = post(2L);
        Member reporter = member(100L);
        Member writer = member(200L);
        Member other = member(300L);

        Report report = report(reporter, "COMMENT", 20L);
        Comment reported = comment(20L, post, writer, "너는 정말 개새끼야");
        Comment directRelated = comment(21L, post, writer, "너 진짜 병신이다");
        Comment victimReport = comment(22L, post, writer, "친구가 나한테 병신이라고 했어요");
        Comment otherWriter = comment(23L, post, other, "너 병신이다");
        Comment otherPostComment = comment(24L, otherPost, writer, "너 병신이다");
        Comment hidden = comment(25L, post, writer, "너 병신이다");
        hidden.hide();

        when(commentRepository.findById(20L)).thenReturn(Optional.of(reported));
        when(commentRepository.findById(21L)).thenReturn(Optional.of(directRelated));
        when(commentRepository.findById(22L)).thenReturn(Optional.of(victimReport));
        when(commentRepository.findById(23L)).thenReturn(Optional.of(otherWriter));
        when(commentRepository.findById(24L)).thenReturn(Optional.of(otherPostComment));
        when(commentRepository.findById(25L)).thenReturn(Optional.of(hidden));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(commentSnapshot(report, 20L, 21L, 22L, 23L, 24L, 25L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "COMMENT", 21L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                abuseAutoHideResult(),
                AutoHideBasis.SERVER_RULE_MATCHED
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(victimReport.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherWriter.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherPostComment.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(hidden.getStatus()).isEqualTo(CommentStatus.HIDDEN);

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        ReportRelatedActionItem item = captor.getValue();
        assertThat(item.getReportId()).isEqualTo(1L);
        assertThat(item.getTargetType()).isEqualTo("COMMENT");
        assertThat(item.getTargetId()).isEqualTo(21L);
        assertThat(item.getBeforeStatus()).isEqualTo("ACTIVE");
        assertThat(item.getAfterStatus()).isEqualTo("HIDDEN");
        assertThat(item.getReason()).isEqualTo("AI가 욕설/비방 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.");
    }

    @Test
    void commentSexualHarassmentAutoHideHidesOnlyDirectRelatedCommentsFromSameWriterInSnapshot() {
        Post post = post(1L);
        Member reporter = member(100L);
        Member writer = member(200L);
        Member other = member(300L);

        Report report = report(reporter, "COMMENT", 30L);
        Comment reported = comment(30L, post, writer, "작성자 몸 보여줘");
        Comment directRelated = comment(31L, post, writer, "작성자 가슴 보여줘");
        Comment example = comment(32L, post, writer, "성희롱 예시로 몸 보여줘 같은 말이 있습니다");
        Comment praise = comment(33L, post, writer, "작성자 오늘 멋져요");
        Comment otherWriter = comment(34L, post, other, "작성자 몸 보여줘");

        when(commentRepository.findById(30L)).thenReturn(Optional.of(reported));
        when(commentRepository.findById(31L)).thenReturn(Optional.of(directRelated));
        when(commentRepository.findById(32L)).thenReturn(Optional.of(example));
        when(commentRepository.findById(33L)).thenReturn(Optional.of(praise));
        when(commentRepository.findById(34L)).thenReturn(Optional.of(otherWriter));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(commentSnapshot(report, 30L, 31L, 32L, 33L, 34L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "COMMENT", 31L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                autoHideResult(ModerationCategory.SEXUAL_HARASSMENT),
                AutoHideBasis.AI_HIGH_CONFIDENCE
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(example.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(praise.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherWriter.getStatus()).isEqualTo(CommentStatus.ACTIVE);

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("AI가 성적 괴롭힘 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.");
    }

    @Test
    void commentSpamAutoHideHidesOnlyDirectSpamCommentsFromSameWriterInSnapshot() {
        Post post = post(1L);
        Member reporter = member(100L);
        Member writer = member(200L);
        Member other = member(300L);

        Report report = report(reporter, "COMMENT", 40L);
        Comment reported = comment(40L, post, writer, "수익 보장 텔레그램 https://spam.example");
        Comment directRelated = comment(41L, post, writer, "무료 가입 이벤트 문의 https://spam.example");
        Comment normalLink = comment(42L, post, writer, "공식 자료는 https://school.example 참고하세요");
        Comment quote = comment(43L, post, writer, "광고 신고 예시: 수익 보장 링크");
        Comment otherWriter = comment(44L, post, other, "수익 보장 텔레그램 https://spam.example");

        when(commentRepository.findById(40L)).thenReturn(Optional.of(reported));
        when(commentRepository.findById(41L)).thenReturn(Optional.of(directRelated));
        when(commentRepository.findById(42L)).thenReturn(Optional.of(normalLink));
        when(commentRepository.findById(43L)).thenReturn(Optional.of(quote));
        when(commentRepository.findById(44L)).thenReturn(Optional.of(otherWriter));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(commentSnapshot(report, 40L, 41L, 42L, 43L, 44L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "COMMENT", 41L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                autoHideResult(ModerationCategory.SPAM_AD),
                AutoHideBasis.SERVER_RULE_MATCHED
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(normalLink.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(quote.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherWriter.getStatus()).isEqualTo(CommentStatus.ACTIVE);

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("AI가 광고/스팸 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.");
    }

    @Test
    void commentFloodingAutoHideHidesOnlyRepeatedCommentsFromSameWriterInSnapshot() {
        Post post = post(1L);
        Member reporter = member(100L);
        Member writer = member(200L);
        Member other = member(300L);

        Report report = report(reporter, "COMMENT", 50L);
        Comment reported = comment(50L, post, writer, "점심 같이 먹자");
        Comment repeated = comment(51L, post, writer, "점심 같이 먹자");
        Comment normalDifferent = comment(52L, post, writer, "저녁은 어렵습니다");
        Comment otherWriter = comment(53L, post, other, "점심 같이 먹자");

        when(commentRepository.findById(50L)).thenReturn(Optional.of(reported));
        when(commentRepository.findById(51L)).thenReturn(Optional.of(repeated));
        when(commentRepository.findById(52L)).thenReturn(Optional.of(normalDifferent));
        when(commentRepository.findById(53L)).thenReturn(Optional.of(otherWriter));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(commentSnapshot(report, 50L, 51L, 52L, 53L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "COMMENT", 51L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                autoHideResult(ModerationCategory.FLOODING),
                AutoHideBasis.AI_HIGH_CONFIDENCE
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(repeated.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(normalDifferent.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherWriter.getStatus()).isEqualTo(CommentStatus.ACTIVE);

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("AI가 도배 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.");
    }

    @Test
    void commentPersonalInfoAutoHideHidesOnlyDirectRelatedCommentsFromSameWriterInSnapshot() {
        Post post = post(1L);
        Member reporter = member(100L);
        Member writer = member(200L);
        Member other = member(300L);

        Report report = report(reporter, "COMMENT", 70L);
        Comment reported = comment(70L, post, writer, "저 사람 번호 010-1111-2222 박제한다");
        Comment directRelated = comment(71L, post, writer, "그 사람 이메일 test@example.com 공개한다");
        Comment publicContact = comment(72L, post, writer, "학교 행정실 연락처는 010-3333-4444입니다");
        Comment quote = comment(73L, post, writer, "개인정보 예시: test@example.com");
        Comment otherWriter = comment(74L, post, other, "저 사람 번호 010-7777-8888 박제한다");

        when(commentRepository.findById(70L)).thenReturn(Optional.of(reported));
        when(commentRepository.findById(71L)).thenReturn(Optional.of(directRelated));
        when(commentRepository.findById(72L)).thenReturn(Optional.of(publicContact));
        when(commentRepository.findById(73L)).thenReturn(Optional.of(quote));
        when(commentRepository.findById(74L)).thenReturn(Optional.of(otherWriter));
        when(snapshotRepository.findByReport(report)).thenReturn(Optional.of(commentSnapshot(report, 70L, 71L, 72L, 73L, 74L)));
        when(relatedActionItemRepository.existsByReportIdAndTargetTypeAndTargetIdAndActionType(
                1L, "COMMENT", 71L, "AI_RELATED_HIDE"
        )).thenReturn(false);

        int hiddenCount = service.hideRelatedItemsAfterAiAutoProcess(
                report,
                autoHideResult(ModerationCategory.PERSONAL_INFO),
                AutoHideBasis.AI_HIGH_CONFIDENCE
        );

        assertThat(hiddenCount).isEqualTo(1);
        assertThat(directRelated.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(publicContact.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(quote.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(otherWriter.getStatus()).isEqualTo(CommentStatus.ACTIVE);

        ArgumentCaptor<ReportRelatedActionItem> captor = ArgumentCaptor.forClass(ReportRelatedActionItem.class);
        verify(relatedActionItemRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo("COMMENT");
        assertThat(captor.getValue().getTargetId()).isEqualTo(71L);
        assertThat(captor.getValue().getReason()).isEqualTo("AI가 개인정보 노출 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다.");
    }

    @Test
    void restoreRelatedItemsRestoresHiddenMessageAndMarksItemRestored() {
        ChatRoom room = chatRoom(1L);
        Member sender = member(200L);
        ChatMessage message = message(11L, room, sender, "몸 보여줘");
        message.delete();
        ReportRelatedActionItem item = new ReportRelatedActionItem(
                1L,
                "CHAT_MESSAGE",
                11L,
                false,
                true,
                "AI_RELATED_HIDE",
                "AI가 성적 괴롭힘 맥락으로 판단하여 관련 메시지를 함께 블라인드 처리했습니다."
        );

        when(relatedActionItemRepository.findByReportIdAndRestoredFalseOrderByIdAsc(1L)).thenReturn(List.of(item));
        when(chatMessageRepository.findById(11L)).thenReturn(Optional.of(message));

        int restoredCount = service.restoreRelatedItems(1L);

        assertThat(restoredCount).isEqualTo(1);
        assertThat(message.isDeleted()).isFalse();
        assertThat(item.isRestored()).isTrue();
        assertThat(item.getRestoredAt()).isNotNull();
    }

    @Test
    void restoreRelatedItemsRestoresHiddenCommentAndMarksItemRestored() {
        Post post = post(1L);
        Member writer = member(200L);
        Comment comment = comment(21L, post, writer, "너 병신이다");
        comment.hide();
        ReportRelatedActionItem item = new ReportRelatedActionItem(
                1L,
                "COMMENT",
                21L,
                "ACTIVE",
                "HIDDEN",
                "AI_RELATED_HIDE",
                "AI가 욕설/비방 맥락으로 판단하여 관련 댓글을 함께 블라인드 처리했습니다."
        );

        when(relatedActionItemRepository.findByReportIdAndRestoredFalseOrderByIdAsc(1L)).thenReturn(List.of(item));
        when(commentRepository.findById(21L)).thenReturn(Optional.of(comment));

        int restoredCount = service.restoreRelatedItems(1L);

        assertThat(restoredCount).isEqualTo(1);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(item.isRestored()).isTrue();
        assertThat(item.getRestoredAt()).isNotNull();
    }

    private Report report(Member reporter, Long targetId) {
        return report(reporter, "CHAT_MESSAGE", targetId);
    }

    private Report report(Member reporter, String targetType, Long targetId) {
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason("신고")
                .build();
        ReflectionTestUtils.setField(report, "reportId", 1L);
        return report;
    }

    private ReportContextSnapshot snapshot(Report report, Long... messageIds) {
        String messages = List.of(messageIds).stream()
                .map(id -> "{\"chatMessageId\":" + id + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new ReportContextSnapshot(
                report,
                "CHAT_MESSAGE",
                report.getTargetId(),
                "{\"context\":{\"messages\":[" + messages + "]}}",
                ""
        );
    }

    private ReportContextSnapshot commentSnapshot(Report report, Long... commentIds) {
        String comments = List.of(commentIds).stream()
                .map(id -> "{\"commentId\":" + id + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new ReportContextSnapshot(
                report,
                "COMMENT",
                report.getTargetId(),
                "{\"context\":{\"comments\":[" + comments + "]}}",
                ""
        );
    }

    private ChatRoom chatRoom(Long id) {
        ChatRoom room = new ChatRoom(ChatRoomType.DIRECT, ChatMatchingMode.MEAL, "테스트 채팅방");
        ReflectionTestUtils.setField(room, "chatRoomId", id);
        return room;
    }

    private ChatMessage message(Long id, ChatRoom room, Member sender, String content) {
        ChatMessage message = new ChatMessage(room, sender, content);
        ReflectionTestUtils.setField(message, "chatMessageId", id);
        return message;
    }

    private Post post(Long id) {
        Post post = Post.builder()
                .member(member(900L + id))
                .boardType(BoardType.GLOBAL)
                .category(PostCategory.FREE)
                .title("테스트 게시글")
                .content("본문")
                .anonymousYn("N")
                .build();
        ReflectionTestUtils.setField(post, "postId", id);
        return post;
    }

    private Comment comment(Long id, Post post, Member member, String content) {
        Comment comment = Comment.builder()
                .post(post)
                .member(member)
                .content(content)
                .anonymousYn("N")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", id);
        return comment;
    }

    private Member member(Long id) {
        Member member = Member.builder()
                .loginId("member" + id)
                .passwordHash("password")
                .email("member" + id + "@example.com")
                .role(MemberRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(member, "memberId", id);
        return member;
    }

    private ModerationResult sexualHarassmentAutoHideResult() {
        return new ModerationResult(
                "MODERATION_POLICY_V1",
                ModerationCategory.SEXUAL_HARASSMENT,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.98"),
                new BigDecimal("0.98"),
                List.of("SEXUAL-HARASSMENT-DIRECT", "SEXUAL-HARASSMENT-SEVERE"),
                false,
                List.of(),
                true,
                "성적 괴롭힘",
                "test"
        );
    }

    private ModerationResult abuseAutoHideResult() {
        return autoHideResult(ModerationCategory.ABUSE);
    }

    private ModerationResult autoHideResult(ModerationCategory category) {
        List<String> policyCodes = switch (category) {
            case ABUSE -> List.of("ABUSE-DIRECT", "ABUSE-SEVERE");
            case SEXUAL_HARASSMENT -> List.of("SEXUAL-HARASSMENT-DIRECT", "SEXUAL-HARASSMENT-SEVERE");
            case SPAM_AD -> List.of("SPAM-COMMERCIAL", "SPAM-SUSPICIOUS-URL");
            case FLOODING -> List.of("FLOOD-CONTENT-REPEAT");
            case PERSONAL_INFO -> List.of("PII-DETECTED");
            default -> List.of("TEST");
        };
        return new ModerationResult(
                "MODERATION_POLICY_V1",
                category,
                ModerationDecision.AUTO_HIDE,
                new BigDecimal("0.97"),
                new BigDecimal("0.97"),
                policyCodes,
                category == ModerationCategory.PERSONAL_INFO,
                category == ModerationCategory.PERSONAL_INFO ? List.of("PHONE") : List.of(),
                true,
                category.name(),
                "test"
        );
    }
}
