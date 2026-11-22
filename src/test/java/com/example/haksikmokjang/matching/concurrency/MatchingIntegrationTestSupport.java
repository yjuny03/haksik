package com.example.haksikmokjang.matching.concurrency;

import com.example.haksikmokjang.matching.matchingrequest.domain.Matching;
import com.example.haksikmokjang.matching.matchingrequest.domain.MatchingStatus;
import com.example.haksikmokjang.matching.matchingrequest.dto.MatchingRequestCreateRequest;
import com.example.haksikmokjang.matching.matchingrequest.service.MatchingRequestService;
import com.example.haksikmokjang.matching.matchingwaiting.domain.MatchingMode;
import com.example.haksikmokjang.matching.matchingwaiting.domain.MatchingType;
import com.example.haksikmokjang.matching.matchingwaiting.domain.MatchingWaiting;
import com.example.haksikmokjang.matching.matchingwaiting.domain.MatchingWaitingStatus;
import com.example.haksikmokjang.member.core.domain.AccountStatus;
import com.example.haksikmokjang.member.core.domain.Gender;
import com.example.haksikmokjang.member.core.domain.Member;
import com.example.haksikmokjang.member.core.domain.MemberRole;
import com.example.haksikmokjang.member.signup.user.domain.UserProfile;
import com.example.haksikmokjang.school.domain.School;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 매칭 통합 테스트 공통 준비 코드.
 *
 * 실제 트랜잭션·잠금 동작을 보기 위해 테스트 자체에는 @Transactional을 걸지 않고 데이터를 커밋한다.
 * 운영·개발 DB와 섞이지 않도록 별도 스키마(haksik_concurrency_test)를 쓴다.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        // CONCURRENCY_SESSION_VARS=innodb_snapshot_isolation=OFF 로 주면 MariaDB 11.8 이전·MySQL과 같은 동작(갱신 손실 허용)으로 확인할 수 있다.
        "spring.datasource.url=jdbc:mariadb://localhost:3306/haksik_concurrency_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&sessionVariables=${CONCURRENCY_SESSION_VARS:wait_timeout=28800}",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.show-sql=false",
        "dev.dummy-data.enabled=false",
        "dev.matching-test-data.enabled=false"
})
abstract class MatchingIntegrationTestSupport {

    protected static final int MAX_PARTICIPANTS = 3; // 모집자 포함 3명 → 남은 자리 2개

    @Autowired
    protected MatchingRequestService matchingRequestService;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected record TestUser(Long memberId, Long userProfileId) {}

    protected record GroupMealFixture(Long ownerMemberId, Long waitingId,
                                      List<Long> matchingIds, List<Long> requesterMemberIds) {}

    protected record WaitingState(int acceptedCount, int currentParticipants,
                                  MatchingWaitingStatus waitingStatus, Set<Long> distinctChatRoomIds) {}

    /** 모집자 1명 + 단체 학식 모집방(정원 3) + REQUESTED 상태 신청 requestCount건 */
    protected GroupMealFixture createGroupMealFixture(int requestCount) {
        TestUser owner = createUser("owner");

        Long waitingId = transactionTemplate.execute(status -> {
            MatchingWaiting waiting = MatchingWaiting.builder()
                    .userProfile(entityManager.getReference(UserProfile.class, owner.userProfileId()))
                    .mode(MatchingMode.MEAL)
                    .matchingType(MatchingType.GROUP_MEAL)
                    .maxParticipants(MAX_PARTICIPANTS)
                    .currentParticipants(1)
                    .status(MatchingWaitingStatus.WAITING)
                    .expiredAt(LocalDateTime.now().plusHours(1))
                    .build();
            entityManager.persist(waiting);
            return waiting.getWaitingId();
        });

        List<Long> matchingIds = new ArrayList<>();
        List<Long> requesterMemberIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            TestUser requester = createUser("req");
            requesterMemberIds.add(requester.memberId());
            matchingIds.add(transactionTemplate.execute(status -> {
                Matching matching = Matching.builder()
                        .mode(MatchingMode.MEAL)
                        .matchingType(MatchingType.GROUP_MEAL)
                        .requester(entityManager.getReference(UserProfile.class, requester.userProfileId()))
                        .target(entityManager.getReference(UserProfile.class, owner.userProfileId()))
                        .targetWaiting(entityManager.getReference(MatchingWaiting.class, waitingId))
                        .status(MatchingStatus.REQUESTED)
                        .requestedAt(LocalDateTime.now())
                        .build();
                entityManager.persist(matching);
                return matching.getMatchingId();
            }));
        }
        return new GroupMealFixture(owner.memberId(), waitingId, matchingIds, requesterMemberIds);
    }

    protected WaitingState readWaitingState(Long waitingId) {
        return transactionTemplate.execute(status -> {
            MatchingWaiting waiting = entityManager.find(MatchingWaiting.class, waitingId);
            List<Matching> accepted = entityManager.createQuery(
                            "select m from Matching m where m.targetWaiting.waitingId = :waitingId and m.status = :status",
                            Matching.class)
                    .setParameter("waitingId", waitingId)
                    .setParameter("status", MatchingStatus.ACCEPTED)
                    .getResultList();
            Set<Long> chatRoomIds = accepted.stream()
                    .map(Matching::getChatRoomId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            return new WaitingState(accepted.size(), waiting.getCurrentParticipants(), waiting.getStatus(), chatRoomIds);
        });
    }

    protected MatchingStatus readMatchingStatus(Long matchingId) {
        return transactionTemplate.execute(status -> entityManager.find(Matching.class, matchingId).getStatus());
    }

    protected MatchingRequestCreateRequest requestFor(Long waitingId) {
        MatchingRequestCreateRequest request = new MatchingRequestCreateRequest();
        ReflectionTestUtils.setField(request, "targetWaitingId", waitingId);
        return request;
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    protected TestUser createUser(String prefix) {
        String key = prefix + System.currentTimeMillis() % 100_000_000 + "_" + SEQ.incrementAndGet();
        return transactionTemplate.execute(status -> {
            School school = getOrCreateSchool();
            Member member = Member.builder()
                    .loginId(key)
                    .passwordHash("not-used")
                    .email(key + "@concurrency.test")
                    .phone("010" + String.format("%08d", SEQ.get()))
                    .role(MemberRole.USER)
                    .accountStatus(AccountStatus.ACTIVE)
                    .build();
            entityManager.persist(member);

            UserProfile profile = UserProfile.builder()
                    .member(member)
                    .school(school)
                    .name(key)
                    .nickname(key)
                    .department("테스트학과")
                    .birthDate(LocalDate.of(2000, 1, 1))
                    .gender(Gender.M)
                    .preferredFoodCategory("한식")
                    .mannerTemperature(BigDecimal.valueOf(36.5))
                    .noShowCount(0)
                    .build();
            entityManager.persist(profile);
            return new TestUser(member.getMemberId(), profile.getUserProfileId());
        });
    }

    private School getOrCreateSchool() {
        List<School> schools = entityManager.createQuery(
                        "select s from School s where s.emailDomain = :domain", School.class)
                .setParameter("domain", "concurrency.test")
                .getResultList();
        if (!schools.isEmpty()) {
            return schools.get(0);
        }
        School school = School.builder()
                .schoolName("동시성테스트대학교")
                .emailDomain("concurrency.test")
                .latitude(BigDecimal.valueOf(37.63))
                .longitude(BigDecimal.valueOf(127.05))
                .build();
        entityManager.persist(school);
        return school;
    }
}
