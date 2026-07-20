package com.example.haksikmokjang.report.service;

import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomMemberRepository;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomRepository;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.global.exception.CustomException;
import com.example.haksikmokjang.global.exception.ErrorCode;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.domain.ReportStatus;
import com.example.haksikmokjang.report.dto.ReportRequest;
import com.example.haksikmokjang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.example.haksikmokjang.notification.event.AdminNotificationRequestedEvent;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDuplicatePolicyTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private StoreReviewRepository storeReviewRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReportContextSnapshotService reportContextSnapshotService;
    @Mock private Member writer;
    @Mock private Member firstReporter;
    @Mock private Member secondReporter;

    private ReportService service;
    private Post post;
    private ReportRequest request;

    @BeforeEach
    void setUp() {
        service = new ReportService(
                reportRepository, userProfileRepository, postRepository, commentRepository,
                chatRoomRepository, chatRoomMemberRepository, chatMessageRepository,
                storeReviewRepository, memberRepository, eventPublisher, reportContextSnapshotService
        );
        post = Post.builder().member(writer).title("신고 대상").content("내용").anonymousYn("N").build();
        request = new ReportRequest();
        request.setTargetType("POST");
        request.setTargetId(10L);
        request.setReason("신고");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        org.mockito.Mockito.lenient().when(writer.getLoginId()).thenReturn("writer");
    }

    @Test
    void sameReporterWithActiveReportIsBlocked() {
        when(memberRepository.findByLoginId("reporter1")).thenReturn(Optional.of(firstReporter));
        when(reportRepository.countActiveDuplicateReports(
                eq(firstReporter), eq("POST"), eq(10L), anySet()
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.createReport("reporter1", request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void anotherReporterForSameTargetCanCreateSeparateReport() {
        when(memberRepository.findByLoginId("reporter2")).thenReturn(Optional.of(secondReporter));
        when(reportRepository.countActiveDuplicateReports(
                eq(secondReporter), eq("POST"), eq(10L), anySet()
        )).thenReturn(0L);

        service.createReport("reporter2", request);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReporter()).isSameAs(secondReporter);
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reporterCanReportAgainAfterCanceledHistoryIsExcluded() {
        when(memberRepository.findByLoginId("reporter1")).thenReturn(Optional.of(firstReporter));
        when(reportRepository.countActiveDuplicateReports(
                eq(firstReporter), eq("POST"), eq(10L), anySet()
        )).thenReturn(0L);

        service.createReport("reporter1", request);

        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void hiddenTargetIsBlockedBeforeDuplicateCreation() {
        post.hide();
        when(memberRepository.findByLoginId("reporter2")).thenReturn(Optional.of(secondReporter));

        assertThatThrownBy(() -> service.createReport("reporter2", request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_PROCESSED_CONTENT);
        verify(reportRepository, never()).countActiveDuplicateReports(any(), any(), any(), any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void secondReportForTargetCreatesSingleAdminQueueNotification() {
        when(memberRepository.findByLoginId("reporter2")).thenReturn(Optional.of(secondReporter));
        when(reportRepository.countActiveDuplicateReports(
                eq(secondReporter), eq("POST"), eq(10L), anySet()
        )).thenReturn(0L);
        when(reportRepository.countByTargetTypeAndTargetId("POST", 10L)).thenReturn(2L);
        service.createReport("reporter2", request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .filteredOn(AdminNotificationRequestedEvent.class::isInstance)
                .hasSize(1);
    }
}
