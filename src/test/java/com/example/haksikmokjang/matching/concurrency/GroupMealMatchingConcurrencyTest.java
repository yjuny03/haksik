package com.example.haksikmokjang.matching.concurrency;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단체 학식 매칭 동시성 재현 테스트.
 *
 * 경쟁 상태는 매번 드러나지 않으므로, 같은 시나리오를 여러 라운드 반복해
 * "정원 초과 / 인원 수 불일치 / 채팅방 분리 / 중복 신청"이 몇 번 일어나는지 센다.
 */
class GroupMealMatchingConcurrencyTest extends MatchingIntegrationTestSupport {

    private static final int ROUNDS = 20;
    private static final int CONCURRENT_REQUESTS = 10;

    /**
     * 모집자가 단체 학식 신청 여러 건을 동시에 수락하면
     * 정원을 넘겨 수락되거나, 인원 수가 실제 수락 수와 어긋나거나, 채팅방이 여러 개로 갈라지는지 확인한다.
     * 늦게 온 수락은 서버 오류가 아니라 "정원 마감"으로 안내되어야 한다.
     */
    @Test
    void 단체학식_동시_수락() throws Exception {
        int slots = MAX_PARTICIPANTS - 1;
        int overCapacityRounds = 0;
        int countMismatchRounds = 0;
        int splitChatRoomRounds = 0;
        int wrongRejectionRounds = 0;
        List<String> roundLogs = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            GroupMealFixture fixture = createGroupMealFixture(CONCURRENT_REQUESTS);

            Map<String, AtomicInteger> outcomes = runConcurrently(
                    fixture.matchingIds().size(),
                    i -> matchingRequestService.acceptMatching(fixture.ownerMemberId(), fixture.matchingIds().get(i))
            );

            WaitingState result = readWaitingState(fixture.waitingId());
            int lateCount = CONCURRENT_REQUESTS - result.acceptedCount();
            int fullNotices = outcomes.getOrDefault("MATCHING_PARTICIPANTS_FULL", new AtomicInteger()).get();

            if (result.acceptedCount() > slots) overCapacityRounds++;
            if (result.currentParticipants() != 1 + result.acceptedCount()) countMismatchRounds++;
            if (result.distinctChatRoomIds().size() > 1) splitChatRoomRounds++;
            if (fullNotices != lateCount) wrongRejectionRounds++;

            roundLogs.add(String.format(
                    "round %2d | accepted=%d (slots=%d) current=%d status=%s chatRooms=%s outcomes=%s",
                    round, result.acceptedCount(), slots, result.currentParticipants(),
                    result.waitingStatus(), result.distinctChatRoomIds(), outcomes
            ));
        }

        System.out.println("\n===== 단체 학식 동시 수락 (" + ROUNDS + "라운드 x 동시 " + CONCURRENT_REQUESTS + "건) =====");
        roundLogs.forEach(System.out::println);
        System.out.printf("정원 초과 라운드: %d / 인원 수 불일치 라운드: %d / 채팅방 분리 라운드: %d / 정원 마감 외 안내 라운드: %d%n",
                overCapacityRounds, countMismatchRounds, splitChatRoomRounds, wrongRejectionRounds);
        SAMPLE_ERRORS.forEach((type, cause) -> System.out.println("원인 예시 | " + type + " → " + cause));
        System.out.println();

        assertThat(overCapacityRounds).as("정원 초과 라운드").isZero();
        assertThat(countMismatchRounds).as("인원 수 불일치 라운드").isZero();
        assertThat(splitChatRoomRounds).as("채팅방 분리 라운드").isZero();
        assertThat(wrongRejectionRounds).as("늦게 온 수락이 정원 마감 외로 안내된 라운드").isZero();
    }

    /**
     * 같은 사람이 같은 모집방에 동시에 여러 번 신청하면 중복 신청이 저장되는지 확인한다.
     */
    @Test
    void 같은_모집방_동시_중복_신청() throws Exception {
        int duplicateRounds = 0;
        List<String> roundLogs = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            GroupMealFixture fixture = createGroupMealFixture(0);
            Long requesterMemberId = createUser("dup").memberId();

            Map<String, AtomicInteger> outcomes = runConcurrently(
                    CONCURRENT_REQUESTS,
                    i -> matchingRequestService.requestMatching(requesterMemberId, requestFor(fixture.waitingId()))
            );

            long saved = transactionTemplate.execute(status -> entityManager.createQuery(
                            "select count(m) from Matching m where m.targetWaiting.waitingId = :waitingId " +
                                    "and m.requester.member.memberId = :memberId", Long.class)
                    .setParameter("waitingId", fixture.waitingId())
                    .setParameter("memberId", requesterMemberId)
                    .getSingleResult());

            if (saved > 1) duplicateRounds++;
            roundLogs.add(String.format("round %2d | saved=%d outcomes=%s", round, saved, outcomes));
        }

        System.out.println("\n===== 같은 모집방 동시 중복 신청 (" + ROUNDS + "라운드 x 동시 " + CONCURRENT_REQUESTS + "건) =====");
        roundLogs.forEach(System.out::println);
        System.out.printf("중복 저장 라운드: %d%n%n", duplicateRounds);

        assertThat(duplicateRounds).as("중복 저장 라운드").isZero();
    }

    // ---------- 동시 실행 도우미 ----------

    private interface IndexedTask {
        void run(int index) throws Exception;
    }

    /** 예외 종류별 첫 번째 근본 원인 메시지 (원인 파악용) */
    private static final Map<String, String> SAMPLE_ERRORS = new ConcurrentHashMap<>();

    /** 모든 스레드를 준비시킨 뒤 한 번에 출발시켜 요청이 최대한 겹치게 한다. 결과는 성공/예외 종류별 개수. */
    private Map<String, AtomicInteger> runConcurrently(int count, IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();

        for (int i = 0; i < count; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(index);
                    outcomes.computeIfAbsent("OK", k -> new AtomicInteger()).incrementAndGet();
                } catch (Exception e) {
                    outcomes.computeIfAbsent(describe(e), k -> new AtomicInteger()).incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();
        return outcomes;
    }

    private String describe(Exception e) {
        Object errorCode = null;
        try {
            errorCode = ReflectionTestUtils.invokeGetterMethod(e, "errorCode");
        } catch (Exception ignored) {
            // CustomException이 아닌 경우
        }
        if (errorCode != null) {
            return errorCode.toString();
        }
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        SAMPLE_ERRORS.putIfAbsent(e.getClass().getSimpleName(), root.getClass().getSimpleName() + ": " + root.getMessage());
        return e.getClass().getSimpleName();
    }
}
