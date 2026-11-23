# 단체 학식 매칭 동시 요청 문제 — 재현부터 해결까지

> 브랜치 `fix/matching-concurrency` · 커밋 `266677c` · 2026.11 (프로젝트 종료 후 개인 개선)

## 1. 문제

단체 학식 모집방은 **정원을 확인한 뒤 인원을 1 늘리는** 구조였습니다.

```java
if (targetWaiting.isFull()) throw ...;   // ① 정원 확인
targetWaiting.increaseParticipant();     // ② 인원 증가
```

①과 ② 사이에 잠금이 없어서, 같은 모집방에 수락이 동시에 들어오면 둘 다 "아직 자리 있음"을 읽고 통과할 수 있었습니다.
같은 구조가 세 곳에 있었습니다.

| 위치 | 동시에 들어오면 |
|---|---|
| 단체 학식 수락 — 정원 확인 → 인원 증가 | 정원 초과, 인원 증가가 사라짐(갱신 손실) |
| 단체 채팅방 — 기존 채팅방 조회 → 없으면 생성 | 첫 수락이 겹치면 채팅방이 여러 개로 갈라짐 |
| 매칭 신청 — 중복 신청 확인 → 저장 | 같은 사람의 신청이 여러 건 저장됨 (DB 유니크 제약 없음) |

## 2. 재현

코드를 읽고 세운 가설을 테스트로 직접 확인했습니다.

- 정원 3명 모집방(모집자 포함 → 남은 자리 2개)
- 요청 10건을 `CountDownLatch`로 동시에 출발시킴
- 경쟁 상태는 매번 드러나지 않으므로 **20라운드 반복**
- 개발 DB와 섞이지 않도록 테스트 전용 스키마 사용

### 결과 (수정 전)

| 시나리오 | 결과 |
|---|---|
| 동시 수락 — MariaDB 12.3 기본 설정 | 20라운드 모두 **10건 중 9건이 DB 오류**로 실패. 자리가 2개 남았는데 1명만 수락됨 |
| 동시 수락 — `innodb_snapshot_isolation=OFF` | 20라운드 모두 **자리 2개에 10명 수락**, 인원 수는 2로 고정, **채팅방 10개** |
| 같은 사람의 동시 중복 신청 | 20라운드 모두 **중복 저장** (10번 신청에 9~10건) |

### 처음 가설과 달랐던 점

처음에는 "정원 초과"가 날 거라고 예상했지만, 로컬 DB에서는 9건이 이런 오류로 실패했습니다.

```
Record has changed since last read in table 'matching_waiting'; try restarting transaction
```

MariaDB 11.8부터 기본으로 켜진 `innodb_snapshot_isolation` 때문입니다. REPEATABLE READ에서 내가 읽은 뒤 다른 트랜잭션이 바꾼 행을 수정하려 하면, 덮어쓰지 않고 오류를 냅니다.
즉 DB가 갱신 손실을 대신 막아 준 것이고, 서버 코드는 여전히 동시 요청을 고려하지 않았습니다. 사용자는 "정원 마감" 대신 서버 오류를 봅니다.

이 설정을 세션에서 끄고 다시 돌리자, MySQL이나 이전 MariaDB와 같은 동작이 되어 정원 초과와 채팅방 분리가 그대로 나타났습니다.
**DB 설정에 따라 증상은 달랐지만, 원인은 같았습니다.**

## 3. 해결 — 모집방 단위 비관적 락

### 왜 비관적 락인가

| 방식 | 이 상황에서는 |
|---|---|
| **비관적 락 (선택)** | 같은 모집방에 요청이 몰리는 구조라, 처음부터 줄 세워 처리하는 쪽이 맞음. 늦게 온 요청은 "정원 마감" 같은 정상 안내를 받음. 채팅방 생성까지 같은 잠금 안에서 처리됨 |
| 낙관적 락 (`@Version`) | 같은 행에서 충돌이 잦아 재시도가 많아지고, 재시도 코드가 따로 필요 |
| 조건부 UPDATE (`current < max`) | 인원 수는 지킬 수 있지만, 채팅방 분리와 중복 신청은 따로 막아야 함 |

### 바꾼 것

```java
// MatchingWaitingRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from MatchingWaiting w where w.waitingId = :waitingId")
Optional<MatchingWaiting> findByIdForUpdate(@Param("waitingId") Long waitingId);
```

신청·수락·거절·신청 취소·확정 취소 5개 메서드가 **시작하자마자** 이 모집방을 잠급니다.

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public Long acceptMatching(Long memberId, Long matchingId) {
    lockWaitingOf(matchingId);   // 모집방 ID만 먼저 조회해서 잠금
    // ... 이후 매칭·인원·기존 채팅방 조회
}
```

### 왜 "맨 처음"에, 왜 READ COMMITTED인가

기존 코드에서 모집방을 읽는 부분만 잠금 조회로 바꾸면, 두 가지 이유로 앞 요청의 결과를 보지 못합니다.

1. **영속성 컨텍스트** — 잠금 전에 매칭 엔티티를 먼저 읽으면, 그 시점 상태가 JPA 1차 캐시에 남습니다. 잠금을 얻은 뒤 다시 조회해도 캐시의 옛 값이 쓰입니다.
2. **스냅샷** — REPEATABLE READ에서는 트랜잭션의 첫 조회 시점 스냅샷을 계속 봅니다. 잠금을 얻은 뒤에도 앞 요청이 커밋한 인원 증가·채팅방 생성·신청 저장이 보이지 않습니다.

그래서 매칭 엔티티를 읽지 않고 **모집방 ID만 먼저 조회해서 잠그고**, 해당 메서드는 **READ COMMITTED**로 바꿔 잠금 이후 조회가 직전 트랜잭션의 커밋 결과를 보게 했습니다.

### 함께 고친 것

정원이 차면 모집방 상태가 `MATCHED`로 바뀝니다. 그런데 기존 코드는 상태를 먼저 확인해서, 늦게 온 요청이 "상대방이 매칭 대기 상태가 아닙니다"로 안내됐습니다.
정원 확인을 상태 확인보다 앞으로 옮겨 **정원 마감** 안내가 나오도록 바로잡았습니다.

## 4. 결과

같은 테스트, 같은 조건으로 다시 돌렸고, **두 DB 설정 모두 결과가 같습니다.**

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| 동시 수락 | 9건 서버 오류 / 또는 10명 수락·채팅방 10개 | 20라운드 모두 **정확히 2명 수락, 인원 3/3, 채팅방 1개**. 나머지 8건은 정원 마감 안내 |
| 동시 중복 신청 | 9~10건 저장 | 20라운드 모두 **1건만 저장**. 나머지 9건은 이미 신청함 안내 |

잠금을 넣은 메서드가 요청 한 건일 때도 기존처럼 동작하는지 6개 테스트로 확인했고, 전체 테스트 108개가 모두 통과합니다.

## 5. 테스트

| 테스트 | 내용 |
|---|---|
| `GroupMealMatchingConcurrencyTest` | 동시 수락, 동시 중복 신청 재현 (20라운드 × 동시 10건) |
| `MatchingRequestFlowTest` | 거절 / 수락 후 거절 불가 / 신청 취소 / 참가자 확정 취소 / 모집자 확정 취소 / 정원 마감 후 신청 |

```bash
./gradlew test --tests 'com.example.haksikmokjang.matching.concurrency.*'

# MySQL·이전 MariaDB와 같은 동작으로 확인
CONCURRENCY_SESSION_VARS=innodb_snapshot_isolation=OFF ./gradlew cleanTest test --tests 'com.example.haksikmokjang.matching.concurrency.*'
```

실제 트랜잭션과 잠금을 확인하기 위해 테스트는 롤백하지 않고 커밋하며, `haksik_concurrency_test` 스키마를 자동으로 만들어 씁니다.

## 6. 한계

- 잠금 단위가 모집방이라, 한 방에 요청이 몰리면 그 방의 요청은 순서대로 처리됩니다. 다른 방에는 영향이 없습니다.
- 재현은 로컬 MariaDB 12.3에서 했습니다.
