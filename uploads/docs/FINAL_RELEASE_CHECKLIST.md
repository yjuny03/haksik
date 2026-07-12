# Final Release Checklist

작성일: 2026-07-12

## 결론

현재 상태는 **자동 테스트 기준 배포 전 차단 항목 해소**입니다.

- `./gradlew.bat test`: **통과**
- 기존 실패 7건: **수정 완료**
- 추가 발견된 더미 데이터 중복 실패 1건: **수정 완료**
- 브라우저 전체 시연 플로우: **배포 후보 환경에서 수동 확인 필요**
- DataLoader 2회 완주 실행: `bootRun` 프로세스가 종료되지 않아 자동 검증은 미완료, 코드/테스트 충돌은 재사용 방식으로 보완

## 수정 결과

기존 실패 7건 처리:

- `AdminOwnerDummyDataSetupTest`
  - 기존 `loginId` 또는 `businessNumber`가 있으면 재사용/skip하도록 수정
- `ReviewReadyChatRoomDummyTest`
  - 과거 `pageuser01/ownerpage01` 전제를 `user1/user2/owner1` 기준으로 정리
  - 계정이 없으면 테스트가 자체 생성하도록 수정
- `MatchingTestDataGeneratorTest`
  - 기존 UserProfile 보정 시 공개 업데이트 메서드를 사용해 선호 카테고리/생년월일 반영 안정화
- `ReservationDummyTest`
  - `user1/owner1` 기준으로 계정/가게를 재사용 생성
  - 예약 더미는 requestMemo 기준으로 중복 생성 방지
- `ReservationServiceTest`
  - 현재 서비스 알림 문구인 `예약 수락`, `[식당명] 식당 예약이 수락되었습니다.` 기준으로 기대값 갱신
- `StoreDummyTest`
  - owner 계정과 가게를 없으면 생성, 있으면 재사용하도록 수정
- `SignUpTest`
  - 고정 `schoolId=273`, 고정 약관 ID 의존 제거
  - 테스트 내부에서 학교/약관을 확보하고 실제 ID로 회원가입 요청

추가 안정화:

- `CommunityDummyDataTest`
  - `qwer`, `1234` 계정과 게시글/댓글을 natural key 기준으로 재사용하도록 수정
- `build.gradle`
  - 운영 artifact에 local/secret 설정 파일이 섞이지 않도록 `processResources`에서 제외
- `application.properties`
  - `GEMINI_TIMEOUT_SECONDS` 기본값을 30초로 변경

## 테스트 결과

실행 명령:

```powershell
.\gradlew.bat test
```

결과:

- `BUILD SUCCESSFUL`
- 95 tests completed
- 0 failed

## 권한/라우팅 확인

코드 기준 확인 결과:

- 채팅방 AI 추천 API
  - `RestaurantRecommendationService.recommendForChatRoom`에서 `chatRoomMemberRepository.existsByChatRoomAndMember`로 참여자 여부 확인
- 약속 폼 장소 후보 추가
  - `ChatFormService.addOption`에서 `checkChatRoomMember`로 참여자 여부 확인
- 관리자 API
  - `SecurityConfig`에서 `/api/admin/**`, `/api/view/admin/**`를 `ADMIN` 권한으로 제한
- 점주 가게/메뉴/예약 관리
  - `StoreService`, `ReservationService`, `ReviewService`에서 store owner loginId 비교 후 불일치 시 `UNAUTHORIZED_ACCESS`
- 신고 처리/복구
  - 관리자 신고 처리 컨트롤러는 `/api/admin/**` 하위 경로로 보호

## DataLoader 재실행 안정성

실행 조건:

```properties
dev.dummy-data.enabled=true
app.demo-mode=true
```

확인/보완 사항:

- `app.demo-mode=true`가 아니면 DataLoader가 즉시 중단됩니다.
- `dev.dummy-data.enabled=true`만으로는 실행되지 않습니다.
- 사용자, 점주, 가게, 메뉴, 리뷰, 게시글, 신고, 채팅방은 natural key 기준 재사용 구조입니다.
- 테스트 더미 데이터도 재실행 시 중복 제약에 걸리지 않도록 재사용 방식으로 정리했습니다.
- `bootRun --args="--spring.main.web-application-type=none --dev.dummy-data.enabled=true --app.demo-mode=true"` 실행은 프로세스가 종료되지 않아 자동 2회 완주 검증은 완료하지 못했습니다.

배포 후보 DB에서 남은 수동 확인:

- DataLoader 2회 실행 전후 `member`, `store`, `menu`, `store_review`, `chat_room`, `report`, `matching_waiting` count 비교
- 시연 고정 채팅방/가게/신고 데이터가 중복으로 늘지 않는지 확인

## 배포 설정

현재 기본값:

```properties
dev.dummy-data.enabled=false
app.demo-mode=false
ai.provider=${AI_PROVIDER:mock}
ai.gemini.api-key=${GEMINI_API_KEY:}
ai.gemini.timeout-seconds=${GEMINI_TIMEOUT_SECONDS:30}
naver.map.client-id=${NAVER_MAP_CLIENT_ID:}
```

확인 완료:

- `.gitignore`에 `src/main/resources/application-local.properties` 제외 규칙 존재
- `git ls-files` 기준 `application-local.properties`, `application-secret.properties`, `application-oauth.properties`는 추적되지 않음
- `processResources` 결과 `build/resources/main`에 local/secret/oauth properties가 포함되지 않음

운영 배포 전 확인:

- 운영에서는 `dev.dummy-data.enabled=false`
- 운영에서는 `app.demo-mode=false`
- 운영 profile에서 local 설정이 로드되지 않는지 확인
- 운영 DB 접속 정보는 환경변수 또는 배포 secret으로 주입
- 이미 공유된 Gemini key가 있다면 회전

## Naver Map 배포 도메인 설정

Naver Cloud Platform 콘솔에서 Maps API 애플리케이션의 Web 서비스 URL에 배포 도메인을 등록해야 합니다.

등록 예시:

- `https://your-production-domain.com`
- `https://www.your-production-domain.com`
- 로컬 테스트가 필요하면 `http://localhost:8080`

확인 화면:

- 매칭 지도
- 채팅방 약속 장소 선택 지도
- 점주 가게 등록/수정 지도

## Gemini 환경변수 설정

권장 환경변수:

```bash
AI_PROVIDER=gemini
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3.5-flash
GEMINI_RECOMMENDATION_MODEL=gemini-3.5-flash
GEMINI_TIMEOUT_SECONDS=30
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
```

확인 항목:

- API key는 저장소에 커밋하지 않음
- 배포 플랫폼 secret/env로만 주입
- quota/billing 제한 확인
- Gemini 장애 시 AI 추천과 AI 신고 fallback 확인
- 추천 응답에 DB에 없는 가게명/메뉴명이 노출되지 않는지 확인

## 남은 수동 확인 항목

- [ ] 배포 후보 환경에서 브라우저 시연 플로우 1회 완주
- [ ] DataLoader 2회 실행 후 주요 테이블 중복 폭증 없음 확인
- [ ] 챗봇 개인 추천 fallback 확인
- [ ] 채팅방 참여자 기반 추천 fallback 확인
- [ ] 장소 후보 추가 후 약속 폼 반영 확인
- [ ] AI 신고 자동 처리/검토 필요/복구 확인
- [ ] 관리자 대시보드와 알림 확인
- [ ] Naver Map 운영 도메인 등록 후 브라우저 콘솔 인증 오류 없음 확인
- [ ] Gemini 운영 API key와 timeout 30초 적용 확인
