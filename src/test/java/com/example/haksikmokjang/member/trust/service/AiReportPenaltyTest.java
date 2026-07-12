package com.example.haksikmokjang.member.trust.service;

import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.repository.MemberRepository;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.member.signup.user.repository.UserProfileRepository;
import com.example.haksikmokjang.member.trust.domain.TrustPenaltyHistory;
import com.example.haksikmokjang.member.trust.domain.TrustPenaltySourceType;
import com.example.haksikmokjang.member.trust.repository.TrustPenaltyHistoryRepository;
import com.example.haksikmokjang.report.domain.ReportProcessorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportPenaltyTest {

    @Mock private MemberRepository memberRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private TrustPenaltyHistoryRepository historyRepository;
    @Mock private Member targetMember;
    @Mock private Member admin;

    @Test
    void aiPenaltyUsesManualPolicyButDoesNotLockAccountAndCanBeRestored() {
        TrustService service = new TrustService(memberRepository, userProfileRepository, historyRepository);
        UserProfile profile = UserProfile.builder()
                .member(targetMember)
                .mannerTemperature(new BigDecimal("5.0"))
                .noShowCount(0)
                .build();
        when(userProfileRepository.findByMember(targetMember)).thenReturn(Optional.of(profile));
        when(historyRepository.findByTargetMemberAndSourceTypeAndSourceId(
                targetMember, TrustPenaltySourceType.REPORT, 123L
        )).thenReturn(Optional.empty());
        when(historyRepository.save(org.mockito.ArgumentMatchers.any(TrustPenaltyHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrustService.TrustPenaltyApplyResult result = service.applyAiReportPenalty(targetMember, 123L);

        assertThat(profile.getMannerTemperature()).isEqualByComparingTo("0.0");
        verify(targetMember, org.mockito.Mockito.never()).lockByTrust(org.mockito.ArgumentMatchers.anyString());
        ArgumentCaptor<TrustPenaltyHistory> captor = ArgumentCaptor.forClass(TrustPenaltyHistory.class);
        verify(historyRepository).save(captor.capture());
        TrustPenaltyHistory penalty = captor.getValue();
        assertThat(penalty.getSourceType()).isEqualTo(TrustPenaltySourceType.REPORT);
        assertThat(penalty.getSourceId()).isEqualTo(123L);
        assertThat(penalty.getPenaltyPoint()).isEqualByComparingTo("5.0");
        assertThat(penalty.getProcessorType()).isEqualTo(ReportProcessorType.AI);
        assertThat(penalty.getReason()).isEqualTo("AI 자동 신고 제재");

        service.cancelReportPenalty(penalty, admin, result.beforeMannerTemperature(), "AI 오탐 복구");

        assertThat(profile.getMannerTemperature()).isEqualByComparingTo("5.0");
        assertThat(penalty.isCanceled()).isTrue();
        assertThat(penalty.getCanceledBy()).isSameAs(admin);
        assertThat(penalty.getCancelReason()).isEqualTo("AI 오탐 복구");
        assertThat(penalty.getCanceledAt()).isNotNull();
    }

    @Test
    void cancelManualReportPenaltyRestoresTemperatureAndOnlyItsOwnAccountLock() {
        TrustService service = new TrustService(memberRepository, userProfileRepository, historyRepository);
        Member member = Member.builder().lockedYn("N").build();
        UserProfile profile = UserProfile.builder()
                .member(member)
                .mannerTemperature(new BigDecimal("5.0"))
                .noShowCount(0)
                .build();
        when(userProfileRepository.findByMember(member)).thenReturn(Optional.of(profile));
        when(historyRepository.findByTargetMemberAndSourceTypeAndSourceId(
                member, TrustPenaltySourceType.REPORT, 321L
        )).thenReturn(Optional.empty());
        when(historyRepository.save(org.mockito.ArgumentMatchers.any(TrustPenaltyHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrustService.TrustPenaltyApplyResult result = service.applyReportPenalty(member, 321L);

        assertThat(profile.getMannerTemperature()).isEqualByComparingTo("0.0");
        assertThat(member.isLocked()).isTrue();
        assertThat(member.getLockedReason()).isEqualTo("신고 제재로 인한 계정 잠금");

        service.cancelReportPenalty(result.penaltyHistory(), admin, result.beforeMannerTemperature(), "오처리 취소");

        assertThat(profile.getMannerTemperature()).isEqualByComparingTo("5.0");
        assertThat(member.isLocked()).isFalse();
        assertThat(member.getLockedReason()).isNull();
        assertThat(result.penaltyHistory().isCanceled()).isTrue();
    }

    @Test
    void reportPenaltyUnlockDoesNotReleaseDifferentLockReason() {
        Member member = Member.builder().lockedYn("N").build();
        member.lockByTrust("노쇼 제재로 인한 계정 잠금");

        member.unlockIfLockedByReportPenalty();

        assertThat(member.isLocked()).isTrue();
        assertThat(member.getLockedReason()).isEqualTo("노쇼 제재로 인한 계정 잠금");
    }
}
