package com.example.haksikmokjang.report.service;

import com.example.haksikmokjang.chat.chatroom.domain.ChatRoom;
import com.example.haksikmokjang.chat.chatroom.domain.ChatRoomMember;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomMemberRepository;
import com.example.haksikmokjang.chat.chatroom.repository.ChatRoomRepository;
import com.example.haksikmokjang.chat.chatmessage.domain.ChatMessage;
import com.example.haksikmokjang.chat.chatmessage.repository.ChatMessageRepository;
import com.example.haksikmokjang.community.comment.domain.Comment;
import com.example.haksikmokjang.community.comment.repository.CommentRepository;
import com.example.haksikmokjang.community.post.domain.Post;
import com.example.haksikmokjang.community.post.repository.PostRepository;
import com.example.haksikmokjang.global.exception.CustomException;
import com.example.haksikmokjang.global.exception.ErrorCode;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.ownerpage.store.domain.StoreReview;
import com.example.haksikmokjang.ownerpage.store.repository.StoreReviewRepository;
import com.example.haksikmokjang.report.domain.Report;
import com.example.haksikmokjang.report.dto.ReportRequest;
import com.example.haksikmokjang.report.repository.ReportRepository;
import com.example.haksikmokjang.report.domain.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import com.example.haksikmokjang.report.event.ReportCreatedEvent;
import com.example.haksikmokjang.notification.event.AdminNotificationRequestedEvent;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserProfileRepository userProfileRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 게시글/댓글 신고
    public void createReport(String loginId, ReportRequest request) {
        Member reporter = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        //내 글/내 댓글 자진 신고 방어 로직
        if ("POST".equals(request.getTargetType())) {
            Post post = postRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
            if (post.getStatus() != com.example.haksikmokjang.community.post.domain.PostStatus.ACTIVE) {
                throw new CustomException(ErrorCode.ALREADY_PROCESSED_CONTENT);
            }
            if (post.getMember().getLoginId().equals(loginId)) {
                throw new CustomException(ErrorCode.CANNOT_REPORT_OWN_CONTENT);
            }
        } else if ("COMMENT".equals(request.getTargetType())) {
            Comment comment = commentRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
            if (comment.getStatus() != com.example.haksikmokjang.community.comment.domain.CommentStatus.ACTIVE) {
                throw new CustomException(ErrorCode.ALREADY_PROCESSED_CONTENT);
            }
            if (comment.getMember().getLoginId().equals(loginId)) {
                throw new CustomException(ErrorCode.CANNOT_REPORT_OWN_CONTENT);
            }
        }else if ("REVIEW".equals(request.getTargetType())) {
            StoreReview review = storeReviewRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

            if (review.getStatus() != com.example.haksikmokjang.ownerpage.store.domain.ReviewStatus.ACTIVE) {
                throw new CustomException(ErrorCode.ALREADY_PROCESSED_CONTENT);
            }
            if (review.getMember().getLoginId().equals(loginId)) {
                throw new CustomException(ErrorCode.CANNOT_REPORT_OWN_CONTENT);
            }
        }

        validateNoActiveDuplicate(reporter, request.getTargetType(), request.getTargetId());

        //체크 통과 완료. 신고 접수
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .build();

        reportRepository.save(report);
        notifyAdminsWhenTargetBecomesRepeated(report);
        eventPublisher.publishEvent(new ReportCreatedEvent(report.getReportId()));
    }
    // 채팅 메시지 신고
    public void reportChatMessage(
            String loginId,
            Long chatRoomId,
            Long chatMessageId,
            ReportRequest request
    ) {
        UserProfile userProfile = userProfileRepository.findByMember_LoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_PROFILE_NOT_FOUND));

        // 채팅 메시지 신고 API에서는 CHAT_MESSAGE만 저장
        if (!"CHAT_MESSAGE".equals(request.getTargetType())) {
            throw new CustomException(ErrorCode.INVALID_REPORT_TARGET);
        }

        ChatMessage chatMessage = chatMessageRepository.findById(chatMessageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (chatMessage.isDeleted()) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED_CONTENT);
        }
        validateNoActiveDuplicate(userProfile.getMember(), "CHAT_MESSAGE", chatMessageId);

        Report report = Report.builder()
                .reporter(userProfile.getMember())
                .targetType("CHAT_MESSAGE")
                .targetId(chatMessageId)
                .reason(request.getReason())
                .build();

        reportRepository.save(report);
        notifyAdminsWhenTargetBecomesRepeated(report);
        eventPublisher.publishEvent(new ReportCreatedEvent(report.getReportId()));
    }

    // 채팅방 상대 신고
    public void reportChatMember(
            String loginId,
            Long chatRoomId,
            Long memberId,
            ReportRequest request
    ) {
        UserProfile userProfile = userProfileRepository.findByMember_LoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_PROFILE_NOT_FOUND));

        // 채팅 상대 신고 API에서는 CHAT_MEMBER만 저장
        if (!"CHAT_MEMBER".equals(request.getTargetType())) {
            throw new CustomException(ErrorCode.INVALID_REPORT_TARGET);
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        ChatRoomMember reportedChatRoomMember = getReportedChatRoomMember(chatRoom, memberId);

        validateNoActiveDuplicate(
                userProfile.getMember(),
                "CHAT_MEMBER",
                reportedChatRoomMember.getChatRoomMemberId()
        );

        Report report = Report.builder()
                .reporter(userProfile.getMember())
                .targetType("CHAT_MEMBER")
                .targetId(reportedChatRoomMember.getChatRoomMemberId())
                .reason(request.getReason())
                .build();

        reportRepository.save(report);
        notifyAdminsWhenTargetBecomesRepeated(report);
        eventPublisher.publishEvent(new ReportCreatedEvent(report.getReportId()));
    }

    // 채팅방 안에서 신고당한 회원 찾기
    private ChatRoomMember getReportedChatRoomMember(ChatRoom chatRoom, Long memberId) {
        return chatRoomMemberRepository.findAllByChatRoom(chatRoom)
                .stream()
                .filter(chatRoomMember -> chatRoomMember.getMember().getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MEMBER_NOT_FOUND));
    }

    private void validateNoActiveDuplicate(Member reporter, String targetType, Long targetId) {
        long duplicateCount = reportRepository.countActiveDuplicateReports(
                reporter,
                targetType,
                targetId,
                EnumSet.of(ReportStatus.PENDING, ReportStatus.PROCESSING, ReportStatus.RESOLVED)
        );
        if (duplicateCount > 0) {
            throw new CustomException(ErrorCode.ALREADY_REPORTED);
        }
    }

    private void notifyAdminsWhenTargetBecomesRepeated(Report report) {
        long reportCount = reportRepository.countByTargetTypeAndTargetId(
                report.getTargetType(), report.getTargetId()
        );
        if (reportCount != 2) {
            return;
        }
        eventPublisher.publishEvent(new AdminNotificationRequestedEvent(
                "ADMIN_REPEATED_REPORT",
                "동일 대상 신고가 여러 건 접수되었습니다.",
                report.getTargetType() + " #" + report.getTargetId()
                        + " 대상 신고가 2건 누적되었습니다.",
                "ADMIN_REPORT",
                report.getReportId()
        ));
    }

}
