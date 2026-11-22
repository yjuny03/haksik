package com.example.haksikmokjang.matching.concurrency;

import com.example.haksikmokjang.global.exception.CustomException;
import com.example.haksikmokjang.global.exception.ErrorCode;
import com.example.haksikmokjang.matching.matchingrequest.domain.MatchingStatus;
import com.example.haksikmokjang.matching.matchingwaiting.domain.MatchingWaitingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잠금을 추가한 매칭 메서드들이 요청 한 건일 때 기존처럼 동작하는지 확인한다.
 * (신청·수락·거절·신청 취소·확정 취소)
 */
class MatchingRequestFlowTest extends MatchingIntegrationTestSupport {

    @Test
    void 거절하면_요청은_REJECTED_모집방은_그대로() {
        GroupMealFixture f = createGroupMealFixture(1);

        matchingRequestService.rejectMatching(f.ownerMemberId(), f.matchingIds().get(0));

        assertThat(readMatchingStatus(f.matchingIds().get(0))).isEqualTo(MatchingStatus.REJECTED);
        WaitingState state = readWaitingState(f.waitingId());
        assertThat(state.currentParticipants()).isEqualTo(1);
        assertThat(state.waitingStatus()).isEqualTo(MatchingWaitingStatus.WAITING);
    }

    @Test
    void 이미_수락한_요청은_거절할_수_없다() {
        GroupMealFixture f = createGroupMealFixture(1);
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(0));

        assertThatThrownBy(() -> matchingRequestService.rejectMatching(f.ownerMemberId(), f.matchingIds().get(0)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MATCHING_ALREADY_PROCESSED);
    }

    @Test
    void 신청자가_보낸_요청을_취소하면_CANCELED() {
        GroupMealFixture f = createGroupMealFixture(1);

        matchingRequestService.cancelSentMatching(f.requesterMemberIds().get(0), f.matchingIds().get(0));

        assertThat(readMatchingStatus(f.matchingIds().get(0))).isEqualTo(MatchingStatus.CANCELED);
        assertThat(readWaitingState(f.waitingId()).currentParticipants()).isEqualTo(1);
    }

    @Test
    void 참가자가_확정을_취소하면_인원이_줄고_모집방이_다시_열린다() {
        GroupMealFixture f = createGroupMealFixture(2);
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(0));
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(1));
        assertThat(readWaitingState(f.waitingId()).waitingStatus()).isEqualTo(MatchingWaitingStatus.MATCHED);

        matchingRequestService.cancelAcceptedMatching(f.requesterMemberIds().get(0), f.matchingIds().get(0));

        assertThat(readMatchingStatus(f.matchingIds().get(0))).isEqualTo(MatchingStatus.CANCELED);
        WaitingState state = readWaitingState(f.waitingId());
        assertThat(state.currentParticipants()).isEqualTo(2);
        assertThat(state.acceptedCount()).isEqualTo(1);
        assertThat(state.waitingStatus()).isEqualTo(MatchingWaitingStatus.WAITING);
    }

    @Test
    void 모집자가_확정을_취소하면_모집방과_연결된_매칭이_모두_취소된다() {
        GroupMealFixture f = createGroupMealFixture(2);
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(0));

        matchingRequestService.cancelAcceptedMatching(f.ownerMemberId(), f.matchingIds().get(0));

        assertThat(readWaitingState(f.waitingId()).waitingStatus()).isEqualTo(MatchingWaitingStatus.CANCELED);
        assertThat(readMatchingStatus(f.matchingIds().get(0))).isEqualTo(MatchingStatus.CANCELED);
        assertThat(readMatchingStatus(f.matchingIds().get(1))).isEqualTo(MatchingStatus.CANCELED);
    }

    @Test
    void 정원이_찬_모집방에_신청하면_정원_마감으로_안내된다() {
        GroupMealFixture f = createGroupMealFixture(2);
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(0));
        matchingRequestService.acceptMatching(f.ownerMemberId(), f.matchingIds().get(1));
        Long lateMemberId = createUser("late").memberId();

        assertThatThrownBy(() -> matchingRequestService.requestMatching(lateMemberId, requestFor(f.waitingId())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MATCHING_PARTICIPANTS_FULL);
    }
}
