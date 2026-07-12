# 학식목장 서비스 운영 기준 문서

작성 기준: 실제 코드 기준  
작성일: 2026-07-12

이 문서는 팀원이 학식목장 서비스의 동작 기준, 상태값, 판단 근거를 빠르게 이해하기 위한 인수인계 문서입니다. 코드와 기존 문서/화면 문구가 다르면 코드 기준으로 작성했습니다.

## 0. 5분 요약

### 0.1 서비스 한 문장 설명
학식목장은 인덕대학교 주변 사용자를 중심으로 위치 기반 매칭을 만들고, 매칭 후 채팅방에서 약속 장소/시간을 정한 뒤 식당 예약, 리뷰, 신고, 관리자 처리까지 이어지는 학식/만남 운영 서비스입니다.

### 0.2 전체 핵심 흐름

| 단계 | 사용자가 보는 흐름 | 서버 기준 핵심 객체 | 주요 코드 |
|---|---|---|---|
| 1 | 로그인 | `Member`, `UserProfile`, `OwnerProfile` | `SecurityConfig`, `LoginSuccessHandler` |
| 2 | 지도에서 주변 대기자 확인 | `MemberLocation`, `MatchingWaiting` | `MatchingWaitingService` |
| 3 | 매칭 요청/수락 | `MatchingRequest`, `MatchingStatus` | `MatchingRequestService` |
| 4 | 채팅방 입장 | `ChatRoom`, `ChatRoomMember` | `ChatRoomCreateService`, `ChatRoomService` |
| 5 | AI 식당 추천 | `Store`, `Menu`, `StoreReview`, 추천 DTO | `RestaurantRecommendationService` |
| 6 | 장소 후보 추가 | `ChatForm`, `ChatFormOption` | `ChatFormService.addOption` |
| 7 | 약속 폼 투표/확정 | `ChatFormAnswer`, `Reservation` | `ChatFormService.closeForm`, `ReservationService` |
| 8 | 채팅방 종료 후 리뷰 | `ChatReview`, `StoreReview` | `ChatReviewService`, `ReviewService` |
| 9 | 신고 접수 | `Report` | `ReportService` |
| 10 | AI 분석/관리자 처리/복구 | `AiModerationAnalysis`, `ReportProcessHistory`, `TrustPenaltyHistory` | `AiModerationService`, `AdminReportService`, `TrustService` |

### 0.3 AI가 사용되는 위치

| AI 기능 | 사용 위치 | AI 역할 | 설계 근거 |
|---|---|---|---|
| AI 식당 추천 | 챗봇 개인 추천, 채팅방 약속 장소 추천 | DB 후보 중 추천 이유 생성 | 없는 가게/메뉴 생성을 막기 위해 후보 조회와 가격/평점/거리/리뷰 수는 서버 DB 값을 사용합니다. |
| AI 신고 분석 | 신고 접수 후 비동기 분석 | 신고 내용을 자동 숨김/검토 필요/조치 없음으로 분류 | 신고는 제재로 이어질 수 있어 애매하면 자동 처리하지 않고 관리자 검토로 넘깁니다. |
| 이미지-only 신고 | 첨부만 있고 실질 텍스트가 없는 신고 | 자동 판정하지 않고 검토 필요 처리 | 현재 코드는 이미지 내용을 분석하지 않으므로 이미지-only 신고를 자동 처리하지 않습니다. |
| 일반 챗봇 답변 | 챗봇 일반 문의 | 서비스 안내형 자연어 답변 | 음식 추천 의도는 DB 추천 서비스로 분기하고, 일반 문의만 Gemini 프롬프트를 사용합니다. |

### 0.4 가장 중요한 운영 원칙

| 원칙 | 코드 기준 | 이유 |
|---|---|---|
| DB에 없는 가게/메뉴는 추천하지 않음 | `RestaurantCandidateQueryService`, `GeminiRestaurantRecommendationClient.toValidatedGeminiItem` | 추천 결과를 바로 장소 후보로 추가할 수 있어야 하므로 실제 `storeId/menuId`가 필요합니다. |
| CLOSED 가게와 ON_SALE 메뉴 없는 가게는 추천 제외 | `businessStatus != CLOSED`, `MenuStatus.ON_SALE` | 사용자가 선택 가능한 실제 후보만 노출합니다. |
| 신고 자동 처리는 보수적으로 수행 | `ModerationPolicyService.canAutoHide`, `ModerationRuleValidationService.matches` | 신고 처리는 숨김/제재와 연결되므로 ruleMatched와 높은 점수 조건이 필요합니다. |
| 복구 가능한 처리 이력을 남김 | `ReportProcessHistory`, `TrustPenaltyHistory` | 관리자 취소/복구 시 콘텐츠 상태와 매너온도를 되돌리기 위해 처리 전 값을 저장합니다. |
| 시연 데이터는 운영에서 꺼야 함 | `dev.dummy-data.enabled=true` + `app.demo-mode=true` | 대량 더미 계정/신고/리뷰가 운영 데이터와 섞이는 것을 막습니다. |

### 0.5 용어 사전

| 용어 | 의미 | 관련 코드 |
|---|---|---|
| 매칭 대기 | 사용자가 지도에 노출되기 위해 만든 대기 상태입니다. `WAITING` 상태이고 만료 전이면 지도 후보가 됩니다. | `MatchingWaiting`, `MatchingWaitingService` |
| 매칭 요청 | 한 사용자가 다른 대기자에게 매칭을 신청한 기록입니다. 수락되면 채팅방 생성으로 이어집니다. | `MatchingRequest`, `MatchingRequestService` |
| 채팅방 | 매칭 수락 후 참여자가 대화하고 약속 폼을 사용하는 공간입니다. | `ChatRoom`, `ChatRoomMember` |
| 약속 폼 | 채팅방 안에서 장소/시간 후보를 등록하고 투표하는 폼입니다. | `ChatForm`, `ChatFormService` |
| 장소 후보 | 약속 폼에 등록되는 가게 또는 장소 선택지입니다. AI 추천 결과도 장소 후보로 추가할 수 있습니다. | `ChatFormOption`, `ChatFormOptionRequest` |
| AI 식당 추천 | 사용자 위치와 선호도, 채팅방 참여자 선호도, DB 가게/메뉴/리뷰를 기준으로 추천 카드를 만드는 기능입니다. | `RestaurantRecommendationService` |
| 신고 처리 이력 | 관리자 또는 AI가 신고를 처리할 때 처리 전/후 상태와 제재 정보를 저장한 이력입니다. | `ReportProcessHistory` |
| AI 분석 이력 | 신고에 대해 AI가 내린 카테고리, 결정, 점수, 상태를 저장한 이력입니다. | `AiModerationAnalysis` |
| 복구 | 처리된 신고를 취소해 게시글/댓글/채팅/리뷰 상태와 매너온도 패널티를 되돌리는 동작입니다. | `AdminReportService.cancelReport` |
| 매너온도 | 사용자 신뢰도 점수입니다. 신고 제재나 노쇼로 감소하고, 처리 취소 시 복구될 수 있습니다. | `TrustService`, `TrustPenaltyHistory` |

### 0.6 역할별 가능 기능 표

| 기능 | 일반 사용자 `USER` | 점주 `OWNER` | 관리자 `ADMIN` | 코드 기준 |
|---|---:|---:|---:|---|
| 사용자 메인/매칭 지도 | 가능 | 불가 | 불가 | `SecurityConfig` |
| 매칭 대기/요청 | 가능 | 불가 | 불가 | `/api/matching/**` USER |
| 채팅방/약속 폼 | 참여자인 경우 가능 | 불가 | 불가 | `ChatRoomService.validateMember`, `ChatFormService` |
| 챗봇 개인 음식 추천 | 가능 | 불가 | 불가 | `/api/chatbot/**` USER |
| 채팅방 AI 식당 추천 | 채팅방 참여자인 경우 가능 | 불가 | 불가 | `RestaurantRecommendationService.recommendForChatRoom` |
| 가게 등록/수정 | 불가 | 자기 가게만 가능 | 관리자 API와 별도 | `StoreService.validateOwner` |
| 메뉴 관리 | 불가 | 자기 가게 메뉴만 가능 | 관리자 API와 별도 | `StoreService` |
| 예약 처리 | 예약 생성 가능 | 자기 가게 예약 처리 가능 | 불가 | `ReservationService` |
| 리뷰 작성 | 조건 충족 시 가능 | 답글/리뷰 신고 가능 | 불가 | `ReviewService` |
| 신고 접수 | 가능 | 일부 리뷰 신고 가능 | 불가 | `ReportService`, `ReviewService.reportReview` |
| 신고 처리/복구 | 불가 | 불가 | 가능 | `AdminReportService` |
| 점주 승인 | 불가 | 신청 가능 | 승인/거절 가능 | `AdminMemberService` |
| 관리자 대시보드 | 불가 | 불가 | 가능 | `AdminDashboardService` |

### 0.7 시연 전체 흐름

| 순서 | 시연 장면 | 확인할 기준 |
|---|---|---|
| 1 | 로그인 | `user1/qwer1234`, `owner1/qwer1234`, `admin/qwer1234` 역할별 화면 이동 |
| 2 | 매칭 지도 | 인덕대 주변 `WAITING` 대기자와 거리/필터 표시 |
| 3 | 채팅방 | 시연용 채팅방 A/B 입장과 참여자 기준 확인 |
| 4 | AI 식당 추천 | 개인 추천은 내 선호도, 채팅방 추천은 참여자 선호도 기준 문구 확인 |
| 5 | 장소 후보 추가 | 추천 카드의 실제 `storeId`가 약속 폼 후보로 추가되는지 확인 |
| 6 | 약속 폼 | 장소/시간 후보 투표와 최다 득표 확정 흐름 확인 |
| 7 | 리뷰 | 예약 완료/채팅방 종료 후 리뷰 가능 조건 확인 |
| 8 | 신고 | 게시글/댓글/채팅/리뷰 신고 접수 확인 |
| 9 | AI 처리 | 자동 숨김, 검토 필요, 실패 fallback 상태 확인 |
| 10 | 관리자 복구 | 처리 취소 시 콘텐츠 상태와 매너온도 복구 확인 |

### 0.8 확인 필요 버그 후보

| 항목 | 현재 코드 기준 | 영향 | 코드 위치 |
|---|---|---|---|
| 메뉴 수정 반영 | 권한 검증 후 `menu.updateMenuInfo(...)` 호출이 주석 처리되어 있습니다. | 점주 메뉴 수정 화면에서 값이 실제로 바뀌지 않을 수 있습니다. | `ownerpage/store/service/StoreService.java` |
| 리뷰 가능 시간 문구 불일치 | 실제 코드는 예약시간부터 3일, 에러 메시지는 30분~4시간으로 되어 있습니다. | 사용자 안내와 실제 허용 시간이 다를 수 있습니다. | `ChatFormService.getStoreReviewTarget`, `ReviewService`, `ErrorCode.UNAUTHORIZED_REVIEW` |
| AI 자동 처리 주석 불일치 | 주석은 회원 제재를 하지 않는다고 하지만 실제 코드는 AI 자동 처리 시 매너온도 5.0을 차감합니다. | 관리자/운영자가 AI 자동 처리의 패널티 여부를 오해할 수 있습니다. | `admin/report/service/AdminReportService.autoResolveReport`, `TrustService.applyAiReportPenalty` |
| 추천 context 문자열 정합성 | enum은 `MAP/CHAT_PLACE/CHATBOT`이지만 챗봇은 `"PERSONAL_RECOMMENDATION"` 문자열을 전달합니다. | 현재 로직에는 큰 영향이 없지만 context 기반 분기 추가 시 누락될 수 있습니다. | `RestaurantRecommendationContext`, `ChatbotService`, `RestaurantRecommendationService` |

## 1. 서비스 전체 구조

| 영역 | 목적 | 핵심 흐름 | 주요 코드 |
|---|---|---|---|
| 일반 사용자 | 위치 기반 매칭, 채팅, 약속, 리뷰, 신고 | 회원가입 → 위치 저장 → 매칭 대기/신청 → 채팅방 → 약속 폼 → 리뷰/신고 | `matching/**`, `chat/**`, `ownerpage/store/service/ReviewService.java`, `report/service/ReportService.java` |
| 점주 | 가게/메뉴/예약/리뷰 관리 | 점주 가입 → 관리자 승인 → 가게 등록 → 예약 처리 → 리뷰 답글/신고 | `member/signup/owner/**`, `ownerpage/store/**` |
| 관리자 | 회원/점주/신고/AI 처리 관리 | 점주 승인 → 신고 처리 → 복구 → 대시보드 확인 | `admin/member/**`, `admin/report/**`, `admin/dashboard/**` |
| AI 기능 | 신고 분류, 식당 추천 설명 생성 | 신고 접수 후 AI 분석, 식당 후보 DB 조회 후 Gemini 설명 | `ai/moderation/**`, `ai/recommendation/**` |
| 지도/위치 | 주변 대기자/가게 표시 | 위치 저장 후 반경 필터 조회 | `MemberLocation`, `MatchingWaitingService`, `StoreRepository.findNearbyStores` |
| 채팅/약속/리뷰 | 매칭 이후 만남 흐름 | 채팅방 생성 → 폼 생성/투표 → 예약 생성 → 채팅방 종료 → 리뷰 가능 | `ChatRoomCreateService`, `ChatFormService`, `ChatReviewService` |

## 2. 회원/로그인/권한 기준

### 2.1 기능 목적
회원 역할별 접근 권한과 로그인 이후 이동 기준을 분리합니다.

### 2.2 사용자 흐름
일반 사용자/점주/관리자가 로그인하면 `LoginSuccessHandler`가 역할과 승인 상태를 보고 이동 화면을 결정합니다.

### 2.3 서버 처리 흐름
`SecurityConfig`가 URL 접근을 제한하고, `LoginSuccessHandler`가 로그인 성공 후 role별 redirect를 수행합니다. 로그인 실패는 `LoginFailureHandler`와 `LoginAttemptService`가 처리합니다.

### 2.4 주요 클래스
- `global/config/SecurityConfig.java`
- `global/security/LoginSuccessHandler.java`
- `global/security/LoginFailureHandler.java`
- `global/security/LoginAttemptService.java`
- `member/core/domain/Member.java`
- `member/core/domain/MemberRole.java`
- `member/core/domain/AccountStatus.java`
- `member/signup/owner/domain/OwnerProfile.java`
- `member/signup/owner/domain/ApprovalStatus.java`

### 2.5 판단 기준

| 판단 항목 | 조건 | 결과 | 코드 위치 |
|---|---|---|---|
| 일반 사용자 | `MemberRole.USER` | `/api/view/user/main` 이동 | `LoginSuccessHandler` |
| 승인 점주 | `MemberRole.OWNER` + `ApprovalStatus.APPROVED` | `/api/view/owner/main` 이동 | `LoginSuccessHandler` |
| 승인 대기 점주 | `ApprovalStatus.PENDING` | `/api/view/owner/pending` 이동 | `LoginSuccessHandler` |
| 반려 점주 | `ApprovalStatus.REJECTED` | 세션 무효화 후 로그인 화면으로 이동 | `LoginSuccessHandler` |
| 관리자 | `MemberRole.ADMIN` | `/api/view/admin/main` 이동 | `LoginSuccessHandler` |
| 관리자 API | `/api/admin/**` | `ADMIN` 권한 필요 | `SecurityConfig` |
| 사용자 매칭 API | `/api/matching/**` | `USER` 권한 필요 | `SecurityConfig` |
| 챗봇 API | `/api/chatbot/**` | `USER` 권한 필요 | `SecurityConfig` |
| 로그인 실패 잠금 | 실패 횟수 `>= 5` | `lockedYn=Y`, 사유 `로그인 실패 5회 초과` | `Member.increaseLoginFailCount` |
| 로그인 실패 자동 해제 | 로그인 실패 잠금 후 30분 경과 | `unlock()` | `LoginAttemptService.unlockExpiredLoginFailLock` |

### 2.6 상태값 변화
- 로그인 실패: `loginFailCount + 1` → 5회 이상이면 `lockedYn=N → Y`
- 로그인 성공: `loginFailCount=0`, `lockedYn=N`, `lockedAt=null`
- 점주 승인: `PENDING → APPROVED`
- 점주 반려: `PENDING → REJECTED`
- 점주 승인/반려 취소: `APPROVED/REJECTED → PENDING`

### 2.7 예외/실패 처리
`CustomException`은 `GlobalExceptionHandler`에서 `ErrorCode`의 HTTP 상태와 메시지로 응답합니다.

### 2.8 화면에서 확인하는 방법
- 로그인 화면: `/api/view/login`
- 사용자 메인: `/api/view/user/main`
- 점주 메인: `/api/view/owner/main`
- 관리자 메인: `/api/view/admin/main`

### 2.9 테스트/시연 계정
- 관리자: `admin / qwer1234`
- 일반 사용자: `user1` ~ `user120 / qwer1234`
- 점주: `owner1` ~ `owner60 / qwer1234`

### 2.10 주의사항
- `AccountStatus.ACTIVE/WITHDRAWN/SUSPENDED` enum은 존재하지만, 현재 `SecurityConfig`의 URL 권한 판단은 role 기반입니다.
- `application.properties`에 `spring.profiles.active=local`, `spring.profiles.include=local`이 있습니다. 운영 배포 시 local profile 로딩 여부를 확인해야 합니다.

## 3. 위치 기반 매칭 흐름

### 3.1 기능 목적
사용자 위치를 저장하고, 지도에서 조건에 맞는 매칭 대기자를 표시합니다.

### 3.2 사용자 흐름
사용자가 매칭 모드와 위치를 선택해 대기 등록을 하면 지도에 `WAITING` 마커로 표시됩니다. 다른 사용자는 마커를 선택해 매칭 신청을 보냅니다.

### 3.3 서버 처리 흐름
`MatchingWaitingService.enterWaiting`이 프로필, 기존 확정 매칭, 기존 대기, 위치, 매칭 유형을 검증한 뒤 `MatchingWaiting`을 생성합니다.

### 3.4 주요 클래스
- `matching/matchingwaiting/service/MatchingWaitingService.java`
- `matching/matchingrequest/service/MatchingRequestService.java`
- `matching/matchingwaiting/domain/MatchingWaiting.java`
- `matching/matchingrequest/domain/Matching.java`
- `matching/matchingwaiting/scheduler/MatchingWaitingScheduler.java`
- `member/core/domain/MemberLocation.java`

### 3.5 판단 기준

| 판단 항목 | 조건 | 결과 | 코드 위치 |
|---|---|---|---|
| 위치 저장 | 대기 등록 또는 위치 갱신 요청 | `MemberLocation.updateLocation` | `MatchingWaitingService.enterWaiting/updateLocation` |
| 확정 매칭 보유 | `MatchingStatus.ACCEPTED` 존재 | 새 대기 생성 차단 | `MatchingWaitingService.enterWaiting` |
| 기존 대기 보유 | `WAITING` + `expiredAt > now` | 새 대기 생성 차단 | `MatchingWaitingService.enterWaiting` |
| 기존 대기 만료 | `WAITING` + `expiredAt <= now` | 기존 대기 `EXPIRED` 처리 | `MatchingWaitingService.enterWaiting` |
| 학식 1:1 | `mode=MEAL`, `matchingType=null` 또는 `ONE_TO_ONE` | `MatchingType.ONE_TO_ONE` | `resolveMatchingType` |
| 학식 단체 | `mode=MEAL`, `matchingType=GROUP_MEAL`, `maxParticipants >= 3` | 단체 모집 | `resolveMaxParticipants` |
| 소개팅 | `mode=BLIND_DATE` | `MatchingType.ONE_TO_ONE` | `resolveMatchingType` |
| 과팅 | `mode=GROUP` | `MatchingType.GROUP_DATE` | `resolveMatchingType` |
| 지도 표시 | `status=WAITING`, `expiredAt > now` | 마커 후보 | `MatchingWaitingRepository.findMarkersByFilter` 호출 |
| 내 마커 | 조회 사용자 본인 | 필터와 무관하게 유지 | `MatchingWaitingService.getMarkers` |
| 반경 필터 | 내 위치와 대상 위치 거리 `<= radiusKm` | 마커 표시 | `matchesRadius` |
| 음식 필터 | 필터 사용 + 내 선호 음식 없음 | `PREFERRED_FOOD_CATEGORY_REQUIRED` | `validateFoodFilterAvailable` |

### 3.6 상태값 변화
- 대기 생성: 없음 → `WAITING`
- 매칭 수락 1:1: `WAITING → MATCHED`, `REQUESTED → ACCEPTED`
- 단체 모집 정원 도달: `WAITING → MATCHED`
- 대기 취소: `WAITING → CANCELED`
- 만료 스케줄러: `WAITING → EXPIRED`
- 매칭 요청: 없음 → `REQUESTED`
- 매칭 거절: `REQUESTED → REJECTED`
- 매칭 취소: `REQUESTED/ACCEPTED → CANCELED`
- 채팅방 종료: `ACCEPTED → COMPLETED`

### 3.7 예외/실패 처리
대표 예외는 `MATCHING_WAITING_ALREADY_EXISTS`, `MATCHING_ACCEPTED_ALREADY_EXISTS`, `MATCHING_SELF_REQUEST`, `MATCHING_TARGET_NOT_WAITING`, `MATCHING_PARTICIPANTS_FULL`입니다.

### 3.8 화면에서 확인하는 방법
사용자 로그인 후 매칭 지도에서 학식/소개팅/과팅 모드를 바꿔 마커를 확인합니다.

### 3.9 테스트/시연 계정
`user1` ~ `user80`은 인덕대 메인 클러스터에 주로 배치됩니다.

### 3.10 주의사항
- `MatchingWaitingStatus`에는 `STARTED`가 없고, 코드 기준 상태는 `WAITING/MATCHED/CANCELED/EXPIRED`입니다.
- `MatchingStatus`에도 `STARTED`는 없고 `REQUESTED/ACCEPTED/REJECTED/CANCELED/COMPLETED`를 사용합니다.

## 4. 채팅방/약속 폼 흐름

### 4.1 기능 목적
매칭 성사 후 참여자 간 대화, 약속 장소/시간 투표, 예약, 리뷰 작성까지 연결합니다.

### 4.2 사용자 흐름
매칭 수락 → 채팅방 생성 → 약속 폼 생성 → 장소/시간 후보 추가 → 투표 → 폼 종료 → 예약 생성/약속 확정 → 채팅방 종료 → 리뷰 작성.

### 4.3 서버 처리 흐름
`MatchingRequestService.acceptMatching`이 매칭 유형별로 채팅방을 생성합니다. `ChatFormService`가 폼 생성, 후보 추가, 투표, 종료, 예약 생성, 리뷰 가능 여부를 처리합니다.

### 4.4 주요 클래스
- `chat/chatroom/service/ChatRoomCreateService.java`
- `chat/chatroom/service/ChatRoomService.java`
- `chat/chatroom/domain/ChatRoom.java`
- `chat/chatform/service/ChatFormService.java`
- `chat/chatform/domain/ChatForm.java`
- `chat/chatform/domain/ChatFormOption.java`
- `chat/chatreview/service/ChatReviewService.java`

### 4.5 판단 기준

| 판단 항목 | 조건 | 결과 | 코드 위치 |
|---|---|---|---|
| 1:1 채팅방 | 학식 1:1 또는 소개팅 수락 | `ChatRoomType.DIRECT` | `ChatRoomCreateService.createDirectRoom` |
| 단체 학식 채팅방 | `GROUP_MEAL` 첫 수락 | 그룹 채팅방 생성, 이후 수락자는 기존 방 추가 | `MatchingRequestService.createOrJoinGroupMealChatRoom` |
| 과팅 채팅방 | `GROUP_DATE` 수락 | 대표자 2명만 `LEADER`로 생성 | `createGroupDateChatRoom` |
| 채팅방 상세 조회 | 요청자가 참여자 | 상세 조회 가능 | `ChatRoomService.getChatRoomMember` |
| 채팅방 종료 권한 | 과팅이면 `LEADER`, 그 외 참여자 | 종료 가능 | `canEndChatRoom` |
| 폼 생성 | 채팅방 참여자 + 방 `ACTIVE` | `ChatForm` 생성 후 FORM 메시지 발송 | `ChatFormService.createForm` |
| 장소 후보 추가 | 채팅방 참여자 + 미종료 PLACE 폼 | 후보 저장 | `ChatFormService.addOption` |
| AI 추천 후보 추가 | 추천 카드에서 기존 `ChatFormOptionRequest` 사용 | `ChatPlaceSource.STORE`, `storeId` 포함 후보 추가 | `ChatFormService.addOption`, `chat-form.js` |
| 투표 | 참여자 + 미종료 폼 + 해당 폼 옵션 | 기존 응답 변경 또는 신규 응답 | `answerForm` |
| 폼 종료 | 폼 생성자만 가능 | `closedYn=N → Y` | `canCloseForm`, `closeForm` |
| 약속 시간 확정 | TIME 후보 중 득표 1위 존재 | `chatRoom.appointmentAt`, `autoCloseAt=appointmentAt+1h` | `confirmAppointmentByTimeVoteResult` |
| 예약 생성 | PLACE 1위 + TIME 1위 + 미래 시간 + `placeSource=STORE` | `ReservationStatus.REQUESTED` 예약 생성 | `createReservationByVoteResult` |
| 채팅방 자동 종료 | `roomStatus=ACTIVE`, `autoCloseAt <= now` | `CLOSED` | `ChatRoomAutoCloseScheduler` |
| 식당 리뷰 가능 | 예약자 본인 + 예약 `COMPLETED` + 미작성 + 예약시간~3일 이내 | 리뷰 작성 가능 | `ChatFormService.getStoreReviewTarget`, `ReviewService.createReview` |

### 4.6 상태값 변화
- 채팅방: `ACTIVE → CLOSED`
- 폼: `closedYn=N → Y`
- 예약: `REQUESTED → ACCEPTED/REJECTED/CANCELED/COMPLETED`
- 매칭: `ACCEPTED → COMPLETED` when 채팅방 종료

### 4.7 예외/실패 처리
참여자가 아니면 `CHAT_MEMBER_NOT_FOUND`, 종료된 방에서 폼 생성 시 `CHAT_ROOM_CLOSED`, 종료 권한 없으면 `CHAT_ROOM_END_FORBIDDEN`입니다.

### 4.8 화면에서 확인하는 방법
채팅방 화면에서 약속 투표 생성, 장소 후보 추가, AI 식당 추천 카드, 장소 후보 추가 버튼을 확인합니다.

### 4.9 테스트/시연 계정
- 채팅방 A: `[시연] AI 한식 우선 추천 채팅방`, `user1/user2/user3`
- 채팅방 B: `[시연] AI 동률 추천 채팅방`, `user4/user5`

### 4.10 주의사항
- `ChatFormType.TIME`, `APPOINTMENT` enum은 있지만 `normalizeFormType`은 `VOTE` 외 타입을 `PLACE`로 정규화합니다.
- 폼 종료 후 예약 생성은 장소와 시간이 모두 득표되어야 하며, 시간 후보가 과거면 예약을 만들지 않습니다.

## 5. AI 식당 추천 기준

### 5.1 기능 목적
DB에 존재하는 가게/판매 중 메뉴만 후보로 삼고, Gemini는 설명 생성에만 사용합니다.

### 5.2 사용자 흐름
개인은 챗봇에서 오늘의 음식 추천을 요청하고, 채팅방에서는 약속 장소 화면에서 AI 식당 추천을 요청합니다.

### 5.3 서버 처리 흐름
`RestaurantRecommendationService`가 위치를 결정하고 후보를 조회합니다. 후보 점수 계산 후 Gemini에 후보 ID만 전달합니다. Gemini 결과는 storeId/menuId가 후보에 존재할 때만 사용됩니다.

### 5.4 주요 클래스
- `ai/recommendation/service/RestaurantRecommendationService.java`
- `ai/recommendation/service/RestaurantCandidateQueryService.java`
- `ai/recommendation/service/RestaurantScoringService.java`
- `ai/recommendation/service/GeminiRestaurantRecommendationClient.java`
- `ai/recommendation/controller/RestaurantRecommendationController.java`
- `ai/recommendation/controller/ChatRoomRestaurantRecommendationController.java`

### 5.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 개인 추천 선호도 | 로그인 사용자 `UserProfile.preferredFoodCategory` | 개인 선호 점수 반영 | `RestaurantRecommendationService.recommend` |
| 채팅방 추천 선호도 | 참여자 `preferredFoodCategory` 집계 | 채팅방 선호 점수 반영 | `collectChatPreferenceCounts` |
| 채팅방 접근 | 요청자가 채팅방 참여자 | 추천 가능 | `recommendForChatRoom` |
| 위치 우선순위 | 요청 `lat/lng` 존재 | 요청 좌표 사용 | `resolveLocation` |
| 위치 fallback | 요청 좌표 없음 + 공개 `MemberLocation` 존재 | 저장 위치 사용 | `resolveLocation` |
| 위치 없음 | 요청 좌표 없음 + 공개 위치 없음 | `LOCATION_NOT_FOUND` | `resolveLocation` |
| 기본 반경 | `radiusKm` 없음/0 이하 | 1km | `initialRadius` |
| 최대 반경 | `radiusKm > 3` | 3km 제한 | `initialRadius` |
| 후보 부족 확장 | 후보 수 `< 3` | 1km → 2km → 3km 순차 확장 | `findExpandedCandidates` |
| CLOSED 제외 | `businessStatus == CLOSED` | 후보 제외 | `RestaurantCandidateQueryService.findCandidates` |
| ON_SALE 메뉴 없음 | 판매 중 메뉴 0개 | 후보 제외 | `findCandidates` |
| ACTIVE 리뷰 | `ReviewStatus.ACTIVE` | 평균/리뷰 수 반영 | `findByStore_StoreIdInAndStatusOrderByCreatedAtDesc` |
| Gemini store 검증 | `recommendedStoreId`가 후보 map에 없음 | 폐기 | `toValidatedGeminiItem` |
| Gemini menu 검증 | `recommendedMenuId`가 후보 메뉴에 없음 | 폐기 | `toValidatedGeminiItem` |
| Gemini 실패 | API key 없음, HTTP/파싱 실패, 검증 결과 없음 | `DB_FALLBACK` | `GeminiRestaurantRecommendationClient.recommend`, `recommendInternal` |
| 가격/평점/거리/리뷰 수 | 서버 DTO는 후보 DB 값 사용 | Gemini 생성값 미사용 | `toItem`, Gemini prompt policy |

### 5.6 DB 점수 계산 기준

| 점수 항목 | 조건 → 점수 | 코드 위치 |
|---|---|---|
| 거리 | `<=300m:30`, `<=700m:22`, `<=1000m:15`, `<=2000m:8`, 그 외 `3` | `RestaurantScoringService.distanceScore` |
| 개인 선호 카테고리 | 가게 카테고리 일치 `20`, 메뉴 키워드 일치 `12` | `preferredCategoryScore` |
| 요청 카테고리 | 가게 카테고리 일치 `10` | `requestedCategoryScore` |
| 평점 | 리뷰 없음 `8`, `>=4.5:20`, `>=4.0:16`, `>=3.5:12`, 그 외 `6` | `ratingScore` |
| 리뷰 수 | `>=20:10`, `>=10:8`, `>=5:5`, `>=1:3` | `reviewCountScore` |
| 최근 긍정 키워드 | 최근 5개 리뷰 중 키워드 hit, 최대 5점 | `recentReviewScore` |
| 메뉴 다양성 | `>=5:5`, `>=2:3`, `1개:1` | `menuScore` |
| 채팅방 선호 | 참여자 선호별 카테고리 일치 `8*인원`, 메뉴 키워드 `5*인원`, 항목별 최대 24 | `chatPreferenceScore` |
| 다수결 선호 | 가게 카테고리 일치 `12`, 메뉴 키워드 `7` | `majorityPreferenceScore` |
| 추천 메뉴 | 후보 메뉴에 속하고 카테고리 키워드 일치 `6`, 아니면 `3` | `recommendedMenuScore` |

### 5.7 예외/실패 처리
후보가 없으면 `source=DB_FALLBACK`, `fallbackReason=NO_CANDIDATES`, 추천 목록 빈 배열을 반환합니다. Gemini 실패 시 DB 점수 기반 추천 문구로 응답합니다.

### 5.8 화면에서 확인하는 방법
채팅방 약속 장소 화면의 `AI 식당 추천` 버튼 또는 챗봇의 음식 추천 질문으로 확인합니다.

### 5.9 테스트/시연 계정
- 채팅방 A는 한식 2명/중식 1명이라 한식 우선 확인용입니다.
- 채팅방 B는 중식/일식 동률 확인용입니다.

### 5.10 주의사항
- `RestaurantRecommendationContext`에는 `MAP/CHAT_PLACE/CHATBOT`이 있지만, 챗봇 추천 요청은 문자열 `"PERSONAL_RECOMMENDATION"`을 context로 넣습니다. 현재 서비스 로직은 context enum 변환을 하지 않아 동작에는 영향이 없습니다.
- Gemini 추천 모델 기본값은 `gemini-3.5-flash`로 설정되어 있습니다. 실제 모델 사용 가능 여부는 배포 환경에서 확인해야 합니다.

## 6. 챗봇 기능 기준

### 6.1 기능 목적
일반 문의는 Gemini 기반 고객지원 답변으로 처리하고, 음식 추천 의도는 DB 기반 개인 추천으로 분리합니다.

### 6.2 사용자 흐름
사용자가 챗봇에 질문을 입력합니다. 메시지가 음식/맛집/메뉴/식당/밥/점심/저녁과 추천 의도를 포함하면 개인 추천이 실행됩니다.

### 6.3 서버 처리 흐름
`ChatbotController`가 메시지, lat/lng, 사용자 선호 음식을 추출해 `ChatbotService`로 전달합니다. 추천 의도면 `RestaurantRecommendationService.recommendForChatbot`으로 위임합니다.

### 6.4 주요 클래스
- `chatbot/controller/ChatbotController.java`
- `chatbot/service/ChatbotService.java`
- `ai/recommendation/service/RestaurantRecommendationService.java`

### 6.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 추천 의도 | 공백 제거 메시지에 `추천` 포함 + 음식/맛집/메뉴/식당/밥/점심/저녁 포함 | 개인 추천 실행 | `ChatbotService.isRestaurantRecommendationIntent` |
| 매칭 추천 | `매칭추천` 포함 | 음식 추천 의도 제외 | `isRestaurantRecommendationIntent` |
| 요청 카테고리 | 메시지에 한식/중식/일식/양식 포함 | 해당 카테고리 요청값 사용 | `extractRequestedCategory` |
| 카테고리 없음 | 메시지 카테고리 없음 | 프로필 선호 음식 사용 | `extractRequestedCategory` |
| 위치 없음 | `LOCATION_NOT_FOUND` | 위치 필요 안내 문구 반환 | `recommendForChatbot` |
| 후보 없음 | 추천 목록 없음 | 주변 추천 가능 가게 부족 안내 | `recommendForChatbot` |
| 일반 질문 | 추천 의도 아님 | Gemini 고객지원 프롬프트 호출 | `ChatbotService.getAiResponse` |

### 6.6 상태값 변화
챗봇 자체는 상태값을 변경하지 않습니다. 음식 추천은 조회성 동작입니다.

### 6.7 예외/실패 처리
추천 관련 `CustomException`은 사용자 안내 문구로 변환됩니다. 일반 Gemini 질문 실패 시 `" 다시 한번 물어봐주세요."`를 반환합니다.

### 6.8 화면에서 확인하는 방법
챗봇 입력창에서 `오늘의 음식 추천해줘`, `오늘 뭐 먹지`, `근처 맛집 추천`을 입력합니다.

### 6.9 테스트/시연 계정
`user1`로 로그인 후 위치가 저장된 상태에서 확인합니다.

### 6.10 주의사항
- 일반 질문 Gemini 호출은 `RestTemplate`을 직접 사용합니다.
- 식당 추천은 Gemini가 직접 가게를 만들지 않고 `RestaurantRecommendationService`의 DB 후보만 사용합니다.

## 7. 가게/메뉴/예약/리뷰 흐름

### 7.1 기능 목적
점주가 가게와 메뉴를 관리하고, 사용자는 예약 완료 후 리뷰를 작성합니다.

### 7.2 사용자 흐름
점주 승인 → 가게 등록 → 메뉴 등록/상태 관리 → 예약 요청 수신 → 예약 처리 → 리뷰 확인/답글/신고.

### 7.3 서버 처리 흐름
`StoreService`가 가게/메뉴를 관리하고, `ReservationService`가 예약 생성/처리를 담당합니다. `ReviewService`가 리뷰 작성, 수정, 삭제, 답글, 신고를 처리합니다.

### 7.4 주요 클래스
- `ownerpage/store/service/StoreService.java`
- `ownerpage/store/service/ReservationService.java`
- `ownerpage/store/service/ReviewService.java`
- `Store`, `Menu`, `Reservation`, `StoreReview`
- `BusinessStatus`, `MenuStatus`, `ReservationStatus`, `ReviewStatus`

### 7.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 가게 등록 | 점주 loginId로 Member 조회 | `BusinessStatus.OPEN` 가게 생성 | `StoreService.createStoreWithMenus` |
| 메뉴 등록 | 가게 등록 시 메뉴 또는 추가 메뉴 | `MenuStatus.ON_SALE` | `createStoreWithMenus`, `addMenuToStore` |
| 가게 수정 | 요청 loginId == 가게 owner loginId | 수정 가능 | `StoreService.updateStore` |
| 영업 상태 변경 | 요청 loginId == owner | `OPEN/BREAK_TIME/CLOSED` 변경 | `updateBusinessStatus` |
| 메뉴 추가 | 요청 loginId == owner | 메뉴 추가 가능 | `addMenuToStore` |
| 예약 생성 | 사용자 loginId + storeId | `ReservationStatus.REQUESTED` | `ReservationService.createReservation` |
| 예약 처리 | 점주 loginId == 예약 가게 owner | 상태 변경 가능 | `processReservation` |
| 리뷰 작성 | 예약자 본인 + 예약 `COMPLETED` + 예약시간~3일 + 미작성 | `ReviewStatus.ACTIVE` 리뷰 생성 | `ReviewService.createReview` |
| 내 리뷰 조회 | 작성자 loginId + `ACTIVE` | 삭제 리뷰 제외 | `getMyReviews` |
| 리뷰 삭제 | 작성자 본인 | `ACTIVE → DELETED` | `deleteReview`, `StoreReview.markAsDeleted` |
| 사장님 답글 | 리뷰 존재 | 답글 저장, 첫 답글이면 알림 | `writeOwnerReply` |
| 점주 리뷰 신고 | 리뷰 가게 owner만 | `targetType=REVIEW` 신고 생성 | `ReviewService.reportReview` |

### 7.6 상태값 변화
- 가게: `OPEN ↔ BREAK_TIME ↔ CLOSED`
- 메뉴: `ON_SALE ↔ SOLD_OUT`
- 예약: `REQUESTED → ACCEPTED/REJECTED/CANCELED/COMPLETED`
- 리뷰: `ACTIVE → DELETED`, 복구 시 이전 상태로 복원

### 7.7 예외/실패 처리
대표 예외는 `STORE_NOT_FOUND`, `RESERVATION_NOT_FOUND`, `UNAUTHORIZED_ACCESS`, `UNAUTHORIZED_REVIEW`, `RESERVATION_ALREADY_PROCESSED`, `REVIEW_NOT_FOUND`입니다.

### 7.8 화면에서 확인하는 방법
점주 화면에서 가게/메뉴/예약/리뷰 관리를 확인하고, 사용자 리뷰 작성 화면에서 예약 기반 리뷰 가능 여부를 확인합니다.

### 7.9 테스트/시연 계정
- 점주: `owner1 / qwer1234`
- 사용자: `user1 / qwer1234`

### 7.10 주의사항
- `ErrorCode.UNAUTHORIZED_REVIEW` 메시지는 “예약 완료 후 30분~4시간”이라고 되어 있지만, 실제 코드는 `reservationAt`부터 `reservationAt.plusDays(3)`까지 허용합니다.
- `StoreService.updateMenu`는 권한 검증 후 실제 `menu.updateMenuInfo(...)` 호출이 주석 처리되어 있어 메뉴 수정이 반영되지 않습니다. 확인 필요입니다.

## 8. 신고 기능 기준

### 8.1 기능 목적
사용자가 게시글, 댓글, 채팅 메시지, 리뷰 등을 신고하고 관리자/AI 처리로 연결합니다.

### 8.2 사용자 흐름
신고 대상 선택 → 사유 입력 → 신고 접수 → AI 분석 이벤트 발행 → 관리자 화면에서 처리/복구.

### 8.3 서버 처리 흐름
`ReportService`가 대상별 신고 가능 여부와 중복을 검증한 뒤 `Report`를 저장하고 `ReportCreatedEvent`를 발행합니다.

### 8.4 주요 클래스
- `report/service/ReportService.java`
- `report/domain/Report.java`
- `report/domain/ReportStatus.java`
- `report/repository/ReportRepository.java`
- `ai/moderation/service/ReportCreatedModerationListener.java`

### 8.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 게시글 신고 | `targetType=POST`, 게시글 `ACTIVE`, 본인 글 아님 | 신고 생성 | `ReportService.createReport` |
| 댓글 신고 | `targetType=COMMENT`, 댓글 `ACTIVE`, 본인 댓글 아님 | 신고 생성 | `ReportService.createReport` |
| 리뷰 신고 | `targetType=REVIEW`, 리뷰 `ACTIVE`, 본인 리뷰 아님 | 신고 생성 | `ReportService.createReport` |
| 채팅 메시지 신고 | `targetType=CHAT_MESSAGE`, 메시지 미삭제 | 신고 생성 | `ReportService.reportChatMessage` |
| 채팅 상대 신고 | `targetType=CHAT_MEMBER` | `ChatRoomMember` 대상 신고 생성 | `ReportService.reportChatMember` |
| 중복 신고 제한 | 같은 신고자 + 같은 targetType/targetId + 상태 `PENDING/PROCESSING/RESOLVED` | `ALREADY_REPORTED` | `validateNoActiveDuplicate` |
| 처리된 콘텐츠 신고 제한 | 대상이 `ACTIVE` 아님 또는 메시지 deleted | `ALREADY_PROCESSED_CONTENT` | `createReport`, `reportChatMessage` |
| 동일 대상 2건 누적 | `countByTargetTypeAndTargetId == 2` | 관리자 알림 발행 | `notifyAdminsWhenTargetBecomesRepeated` |
| 신고 접수 후 AI | 신고 저장 후 이벤트 발행 | AFTER_COMMIT AI 분석 시작 | `ReportCreatedModerationListener` |

### 8.6 상태값 변화
- 신고 생성: 없음 → `PENDING`
- 관리자 처리중: `PENDING → PROCESSING`
- 관리자/AI 처리 완료: `PENDING/PROCESSING → RESOLVED`
- 관리자 반려: `PENDING/PROCESSING → REJECTED`
- 처리 취소: `RESOLVED/REJECTED → CANCELED`
- 처리중 취소: `PROCESSING → PENDING`

### 8.7 예외/실패 처리
자기 콘텐츠 신고는 `CANNOT_REPORT_OWN_CONTENT`, 중복 신고는 `ALREADY_REPORTED`, 이미 처리된 콘텐츠는 `ALREADY_PROCESSED_CONTENT`입니다.

### 8.8 화면에서 확인하는 방법
커뮤니티 상세, 채팅방, 리뷰 화면에서 신고 후 관리자 신고 목록을 확인합니다.

### 8.9 테스트/시연 계정
`user1`로 신고 접수, `admin`으로 관리자 화면 확인.

### 8.10 주의사항
- 사용자 요청 목차에는 `POST/COMMENT/CHAT_MESSAGE/REVIEW`만 있었지만 코드에는 `CHAT_MEMBER` 신고도 존재합니다.

## 9. AI 신고 자동 처리 기준

### 9.1 기능 목적
신고 접수 후 텍스트를 AI/규칙으로 분류해 자동 숨김 또는 관리자 검토로 나눕니다.

### 9.2 사용자 흐름
신고 접수 → AI 분석 → 자동 처리 또는 관리자 검토 → 알림 발송.

### 9.3 서버 처리 흐름
`ReportCreatedModerationListener`가 커밋 이후 `AiModerationService.analyzeReport`를 호출합니다. `AiClient`는 `ai.provider` 설정에 따라 `mock` 또는 `gemini` 구현이 사용됩니다.

### 9.4 주요 클래스
- `ai/client/MockAiClient.java`
- `ai/client/GeminiAiClient.java`
- `ai/client/GeminiClientConfiguration.java`
- `ai/moderation/service/AiModerationService.java`
- `ai/moderation/service/ModerationPolicyService.java`
- `ai/moderation/service/ModerationRuleValidationService.java`
- `ai/moderation/service/ModerationTargetContentService.java`

### 9.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| provider 선택 | `ai.provider=mock` 또는 미설정 | `MockAiClient` 사용 | `@ConditionalOnProperty` |
| provider 선택 | `ai.provider=gemini` | `GeminiAiClient`/`RestClient` 사용 | `GeminiClientConfiguration` |
| 공식 provider | `mock`, `gemini` | OpenAI/GPT provider 없음 | `application.properties`, `GeminiClientConfiguration` |
| AI 분석 대상 | `POST/COMMENT/CHAT_MESSAGE/REVIEW` 텍스트 | title/content/message/review content 추출 | `ModerationTargetContentService` |
| 사진-only | 첨부 있음 + 실질 텍스트 없음 | `REVIEW_REQUIRED`, `IMAGE-NOT-ANALYZED` | `requiresImageReview`, `imageReviewResult` |
| 스팸 자동 후보 | URL + 상업 문구 + 고위험 문구 또는 상업 단서 3개 이상 | `SPAM_AD/AUTO_HIDE` 후보 | `ModerationRuleValidationService.inspect`, `MockAiClient` |
| 개인정보 자동 후보 | 주민번호 또는 개인정보 + 제3자/유포 맥락 | `PERSONAL_INFO/AUTO_HIDE` 후보 | `inspect`, `MockAiClient` |
| 도배 자동 후보 | 반복 토큰/문구/문자 패턴 | `FLOODING/AUTO_HIDE` 후보 | `inspect`, `MockAiClient` |
| 일반 욕설 | 욕설 탐지, 강화 조건 미충족 | `REVIEW_REQUIRED` | `MockAiClient` |
| 고강도 욕설 | 심한 욕설 + 대상 지칭 + 예외 맥락 없음 | `ABUSE/AUTO_HIDE` 후보 | `severeAbuseMatched` |
| ruleMatched | 카테고리별 서버 규칙과 텍스트 일치 | 자동 처리 가능 조건 중 하나 | `ModerationRuleValidationService.matches` |
| AUTO_HIDE 가능 | 유효 결과 + `AUTO_HIDE` + ruleMatched + evidenceRequired + policyCodes + 점수 기준 | 자동 숨김 가능 | `ModerationPolicyService.canAutoHide` |
| 기본 자동 처리 점수 | SPAM/PERSONAL_INFO/FLOODING risk/confidence `>=0.90` | 자동 처리 가능 | `ModerationPolicyService` |
| 고강도 욕설 점수 | ABUSE + `ABUSE-SEVERE` + risk/confidence `>=0.95` | 자동 처리 가능 | `ModerationPolicyService` |
| 자동 처리 대상 | targetType이 `POST/COMMENT/CHAT_MESSAGE/REVIEW` | 자동 숨김 가능 | `AiModerationService.isProcessableTarget` |
| AI 실패 | timeout/HTTP/JSON/검증 실패 | `REVIEW_REQUIRED` fallback | `GeminiAiClient.fallback`, `markFailedAndNotify` |

### 9.6 상태값 변화
- 분석 저장: `AiProcessingStatus.SUCCEEDED/REVIEW_REQUIRED/NO_ACTION/FAILED`
- 자동 처리: `AUTO_PROCESSED`
- 복구: `RESTORED`
- 신고 자동 처리: `ReportStatus.PENDING → RESOLVED`

### 9.7 예외/실패 처리
Gemini API key 없음, HTTP 실패, JSON 파싱 실패, safety block 등은 `REVIEW_REQUIRED` fallback으로 전환됩니다.

### 9.8 화면에서 확인하는 방법
관리자 신고 상세에서 AI 분석 결과, riskScore, confidenceScore, decision, processingStatus를 확인합니다.

### 9.9 테스트/시연 계정
`admin / qwer1234`, 신고 대상은 `[시연 신고]` prefix 데이터.

### 9.10 주의사항
- AI 자동 처리도 `TrustService.applyAiReportPenalty`로 5.0점을 차감하지만, `lockAccountWhenZero=false`라 단일 AI 처리로 즉시 계정 잠금하지 않습니다.
- `AiGeminiProperties` record 내부 fallback은 `timeoutSeconds <= 0`일 때 10초지만, `application.properties` 기본값은 30초입니다.

## 10. 신고 처리 복구 기준

### 10.1 기능 목적
관리자 오처리 또는 AI 자동 처리 취소 시 숨김 콘텐츠와 제재 이력을 복구합니다.

### 10.2 사용자 흐름
관리자 신고 상세 → 처리 취소/복구 → 대상 콘텐츠 상태 복원 → 알림 발송.

### 10.3 서버 처리 흐름
`AdminReportService.cancelReport`가 최근 미취소 `ReportProcessHistory`를 찾고, 대상 타입별 restore service를 호출합니다.

### 10.4 주요 클래스
- `admin/report/service/AdminReportService.java`
- `admin/report/domain/ReportProcessHistory.java`
- `AdminCommunityReportService`
- `AdminChatReportService`
- `AdminReviewReportService`
- `TrustService`

### 10.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 처리중 취소 | 신고 상태 `PROCESSING` | `PENDING`으로 되돌림 | `Report.cancelProcess` |
| 처리 완료 취소 | 신고 상태 `RESOLVED` | 대상 복구 + 제재 복구 가능 | `AdminReportService.cancelReport` |
| 반려 취소 | 신고 상태 `REJECTED` | 신고 `CANCELED`, 신고자 알림 | `cancelReport` |
| 게시글 복구 | 이전 상태가 `NOT_FOUND` 아님 | `Post.restoreStatus(beforeStatus)` | `AdminCommunityReportService` |
| 댓글 복구 | 이전 상태가 `NOT_FOUND` 아님 | `Comment.restoreStatus(beforeStatus)` | `AdminCommunityReportService` |
| 리뷰 복구 | 이전 상태가 `NOT_FOUND` 아님 | `StoreReview.restoreStatus(beforeStatus)` | `AdminReviewReportService` |
| 채팅 메시지 복구 | `beforeChatDeleted` 저장값 존재 | `restoreDeleted(beforeChatDeleted)` | `AdminChatReportService` |
| AI 분석 복구 표시 | 처리 이력 `processorType=AI` | `AiProcessingStatus.RESTORED` | `cancelReport` |
| 매너온도 복구 | 이력에 `penaltyHistory` 있음 | 처리 전 매너온도로 복구 | `TrustService.cancelReportPenalty` |
| 이력 취소 표시 | 취소 수행 | `canceledYn=Y`, `canceledBy`, `canceledAt`, `cancelReason` 저장 | `ReportProcessHistory.cancel` |

### 10.6 상태값 변화
- `ReportStatus.RESOLVED/REJECTED → CANCELED`
- `AiProcessingStatus.AUTO_PROCESSED → RESTORED`
- `TrustPenaltyHistory.canceledYn=N → Y`
- 콘텐츠 상태는 처리 전 상태로 복원됩니다.

### 10.7 예외/실패 처리
`PENDING` 또는 이미 `CANCELED`인 신고를 취소하면 `INVALID_INPUT_VALUE`입니다. 복구 이력이 없으면 `INVALID_INPUT_VALUE`입니다.

### 10.8 화면에서 확인하는 방법
관리자 신고 상세에서 처리 취소 버튼을 사용하고, 대상 게시글/댓글/리뷰/채팅 메시지 표시 상태를 확인합니다.

### 10.9 테스트/시연 계정
`admin / qwer1234`

### 10.10 주의사항
- AI 자동 처리는 주석상 “회원 제재는 수행하지 않는다”라고 되어 있으나, 실제 코드는 `applyAiReportPenalty`로 5.0점을 차감합니다. 단, 즉시 계정 잠금은 하지 않습니다.

## 11. 신뢰도/매너온도 기준

### 11.1 기능 목적
비매너, 신고 처리, 노쇼에 따라 사용자 매너온도를 관리합니다.

### 11.2 사용자 흐름
채팅방 종료 후 상대 평가 또는 신고 처리 결과로 매너온도가 변합니다.

### 11.3 서버 처리 흐름
`ChatReviewService`는 매너 점수와 노쇼 여부를 반영하고, `TrustService`는 신고/노쇼 제재와 복구를 처리합니다.

### 11.4 주요 클래스
- `chat/chatreview/service/ChatReviewService.java`
- `member/trust/service/TrustService.java`
- `member/trust/domain/TrustPenaltyHistory.java`
- `member/signup/user/domain/UserProfile.java`

### 11.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 매너 평가 5점 | `mannerScore=5` | `+0.5` | `ChatReviewService.getMannerDelta` |
| 매너 평가 4점 | `mannerScore=4` | `+0.3` | `getMannerDelta` |
| 매너 평가 3점 | `mannerScore=3` | `0.0` | `getMannerDelta` |
| 매너 평가 2점 | `mannerScore=2` | `-0.3` | `getMannerDelta` |
| 매너 평가 1점 | `mannerScore=1` | `-0.5` | `getMannerDelta` |
| 노쇼 | `noShow=true` | `-10.0`, noShowCount 증가 | `TrustService.applyNoShowPenalty` |
| 관리자 신고 제재 | 관리자 `RESOLVED` | `-5.0`, 0 이하이면 잠금 | `applyReportPenalty` |
| AI 신고 제재 | AI 자동 처리 | `-5.0`, 단일 처리 즉시 잠금 없음 | `applyAiReportPenalty` |
| 중복 제재 방지 | 같은 target/source 미취소 이력 존재 | 추가 차감 없음 | `applyPenalty` |
| 패널티 복구 | 처리 취소 | 이전 매너온도로 복원 | `cancelReportPenalty` |
| 계정 잠금 | 매너온도 `<= 0` + 잠금 허용 제재 | `lockedYn=Y` | `lockIfTrustZero` |

### 11.6 상태값 변화
- `UserProfile.mannerTemperature` 증가/감소
- `UserProfile.noShowCount + 1`
- `Member.lockedYn=N → Y`
- 제재 복구: `TrustPenaltyHistory.canceledYn=N → Y`

### 11.7 예외/실패 처리
대상 사용자 프로필이 없으면 `USER_PROFILE_NOT_FOUND`입니다.

### 11.8 화면에서 확인하는 방법
마이페이지 신뢰 정보, 관리자 회원 상세/대시보드에서 확인합니다.

### 11.9 테스트/시연 계정
낮은 매너온도 사용자는 DataLoader 시연 데이터에 포함됩니다.

### 11.10 주의사항
AI 자동 처리도 매너온도는 차감하지만 `lockAccountWhenZero=false`라 단건 AI 처리로 즉시 잠금하지 않습니다.

## 12. 관리자 기능 기준

### 12.1 기능 목적
관리자가 회원, 점주 승인, 신고, AI 처리 현황을 관리합니다.

### 12.2 사용자 흐름
관리자 로그인 → 대시보드 확인 → 점주 승인/반려 → 신고 처리/복구 → 알림 확인.

### 12.3 서버 처리 흐름
`AdminDashboardService`가 요약 카드를 구성하고, `AdminMemberService`/`AdminReportService`가 개별 처리를 수행합니다.

### 12.4 주요 클래스
- `admin/dashboard/service/AdminDashboardService.java`
- `admin/member/service/AdminMemberService.java`
- `admin/report/service/AdminReportService.java`
- `notification/service/NotificationService.java`

### 12.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| 회원 요약 | 전체/USER/OWNER/ADMIN/잠금/오늘 가입 | 대시보드 카드 | `AdminDashboardService.getDashboard` |
| 점주 승인 대기 | `ApprovalStatus.PENDING` count | 점주 카드 | `OwnerProfileRepository.countByApprovalStatus` |
| 신고 요약 | 전체/PENDING/RESOLVED/REJECTED/CANCELED | 신고 카드 | `AdminDashboardService` |
| 동일 대상 신고 | targetType/targetId별 count 상위 5 | 중복 신고 표시 | `ReportRepository.findMostReportedTargets` |
| AI 자동 처리 카운트 | `AUTO_PROCESSED + RESTORED` | AI 처리 카드 | `AdminDashboardService` |
| AI 검토 필요 | `REVIEW_REQUIRED` | AI 검토 카드 | `AdminDashboardService` |
| AI 실패 | `FAILED` | AI 실패 카드 | `AdminDashboardService` |
| 낮은 매너온도 | `mannerTemperature <= 10.0` | 신뢰도 카드 | `LOW_MANNER_THRESHOLD` |
| 관리자 알림 | 지정된 `ADMIN_*` 타입 | 관리자 알림 목록 표시 | `NotificationService.ADMIN_NOTIFICATION_TYPES` |

### 12.6 상태값 변화
점주 승인/반려/취소, 신고 처리/반려/취소, 회원 잠금 해제가 관리자 기능에서 수행됩니다.

### 12.7 예외/실패 처리
이미 처리된 점주 신청은 `OWNER_ALREADY_PROCESSED`, 이미 처리된 신고는 `REPORT_ALREADY_PROCESSED`입니다.

### 12.8 화면에서 확인하는 방법
`/api/view/admin/main`, `/api/view/admin/reports`, `/api/view/admin/owners`

### 12.9 테스트/시연 계정
`admin / qwer1234`

### 12.10 주의사항
관리자 대시보드의 AI 처리 카운트는 `AUTO_PROCESSED + RESTORED`를 합산합니다.

## 13. DataLoader / 시연 데이터 기준

### 13.1 기능 목적
배포 시연용 대량 데이터와 고정 시나리오 데이터를 생성합니다.

### 13.2 사용자 흐름
개발/시연 환경에서 설정을 켠 뒤 애플리케이션을 실행하면 `ApplicationRunner`가 데이터를 생성합니다.

### 13.3 서버 처리 흐름
`DataLoader`는 `@ConditionalOnProperty(dev.dummy-data.enabled=true)`로 Bean 등록되고, 실행 시 `app.demo-mode=true`를 추가 확인합니다.

### 13.4 주요 클래스
- `global/dev/DataLoader.java`
- `DEMO_SCENARIO_GUIDE.md`
- `FINAL_RELEASE_CHECKLIST.md`

### 13.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| Bean 등록 | `dev.dummy-data.enabled=true` | DataLoader 활성 | `@ConditionalOnProperty` |
| 실행 방어 | `app.demo-mode != true` | 데이터 생성 중단 | `DataLoader.run` |
| 관리자 계정 | `admin` 없음 | 생성 | `getOrCreateAdmin` |
| 사용자 | `user1`~`user120` | 없으면 생성 | `createUsersAndMatchingData` |
| 점주 | `owner1`~`owner60` | 없으면 생성, owner60 승인대기 | `createOwners`, `createOwnerProfileIfNeeded` |
| 가게 | 58개 | 인덕대/보조 클러스터 분산 | `createStores` |
| CLOSED 가게 | index 58 | `BusinessStatus.CLOSED` | `createStore`, `updateStore` |
| SOLD_OUT 전용 가게 | index 57 | 메뉴 전부 `SOLD_OUT` | `createMenusForStores` |
| 메뉴 | 정상 가게 3~5개 ON_SALE + 일부 SOLD_OUT | 추천 후보 확보 | `createMenusForStores` |
| 리뷰 | ACTIVE/DELETED 혼합 | 추천 점수/제외 테스트 | `createScenarioReviews` |
| 대기 중복 방지 | 사용자별 활성 WAITING 존재 | 새로 만들지 않음 | `hasActiveWaiting` |
| 채팅방 A | user1/user2 한식, user3 중식 | 한식 우선 추천 테스트 | `createDemoChatRooms` |
| 채팅방 B | user4 중식, user5 일식 | 동률 테스트 | `createDemoChatRooms` |
| 신고 데이터 | `[시연 신고]` prefix | 신고/AI/관리자 시연 | `createReportData` |

### 13.6 상태값 변화
DataLoader는 기존 데이터를 hard delete하지 않고, 없는 데이터 생성 또는 일부 시연 기준 업데이트를 수행합니다.

### 13.7 예외/실패 처리
필수 시연 채팅방 생성 실패 시 `IllegalStateException`을 던지는 지점이 있습니다.

### 13.8 화면에서 확인하는 방법
실행 로그의 계정/채팅방 ID/API URL을 확인합니다.

### 13.9 테스트/시연 계정
- 관리자: `admin / qwer1234`
- 사용자: `user1` ~ `user120 / qwer1234`
- 점주: `owner1` ~ `owner60 / qwer1234`

### 13.10 주의사항
- 운영에서는 절대 `app.demo-mode=true`로 실행하지 않습니다.
- `bootRun --spring.main.web-application-type=none`으로 DataLoader 2회 자동 완주 확인은 프로세스가 종료되지 않아 수동 확인이 필요합니다.

## 14. 배포 환경 기준

### 14.1 기능 목적
AI, 지도, demo mode, secret 설정을 운영 환경에 맞게 분리합니다.

### 14.2 사용자 흐름
배포 전 환경변수와 외부 콘솔 설정을 완료한 뒤 운영 profile로 실행합니다.

### 14.3 서버 처리 흐름
`application.properties`와 `AiGeminiProperties`가 환경변수 기반 값을 읽습니다.

### 14.4 주요 파일
- `src/main/resources/application.properties`
- `build.gradle`
- `.gitignore`
- `ai/client/AiGeminiProperties.java`

### 14.5 판단 기준

| 판단 항목 | 기준 | 결과 | 코드 위치 |
|---|---|---|---|
| AI provider | `AI_PROVIDER=gemini` | Gemini moderation client 사용 | `GeminiClientConfiguration` |
| 기본 provider | 미설정 또는 `mock` | Mock client 사용 | `MockAiClient` |
| Gemini API key | `GEMINI_API_KEY` | Gemini 호출 가능 | `application.properties` |
| Gemini 모델 | `GEMINI_MODEL` | 신고 분석 모델 | `AiGeminiProperties` |
| 추천 모델 | `GEMINI_RECOMMENDATION_MODEL` | 식당 추천 설명 모델 | `GeminiRestaurantRecommendationClient` |
| timeout 권장 | `GEMINI_TIMEOUT_SECONDS=30` | 연결/읽기 timeout | `application.properties` |
| Naver Map | `NAVER_MAP_CLIENT_ID` | 지도 JS client id | `application.properties`, templates |
| secret 제외 | local/secret/oauth properties | build resources 제외 | `build.gradle.processResources` |
| demo mode | `app.demo-mode=false` | DataLoader 실행 방지 | `DataLoader` |

### 14.6 상태값 변화
배포 설정은 상태값을 바꾸지 않지만, AI provider와 DataLoader 실행 여부를 결정합니다.

### 14.7 예외/실패 처리
Gemini API key가 없으면 추천은 DB fallback, 신고는 provider 설정에 따라 mock 또는 Gemini fallback을 사용합니다.

### 14.8 화면에서 확인하는 방법
지도 화면에서 Naver Map 로딩, AI 추천/신고에서 fallback 여부, 관리자 대시보드를 확인합니다.

### 14.9 테스트/시연 계정
운영에서는 시연 계정 생성을 권장하지 않습니다.

### 14.10 주의사항
- `application-local.properties`가 로컬에 있더라도 빌드 산출물에는 제외되도록 `build.gradle`에 설정되어 있습니다.
- Naver Cloud Console에 배포 도메인을 등록해야 지도 인증 오류가 나지 않습니다.
- HTTPS 환경에서 위치 권한이 정상 동작합니다. localhost 예외를 제외하면 브라우저 위치 API는 보안 컨텍스트가 필요합니다.

## 15. 팀원이 자주 헷갈릴 수 있는 부분

### Q. 챗봇 추천과 채팅방 추천은 뭐가 다른가?
챗봇 추천은 로그인 사용자 1명의 `preferredFoodCategory`와 위치 기준입니다. 채팅방 추천은 채팅방 참여자 전체의 `preferredFoodCategory`를 집계하고 다수결/참여자 선호 점수를 추가 반영합니다.

### Q. Gemini가 직접 가게를 고르는가?
아닙니다. 서버가 DB 후보를 먼저 만들고 Gemini에는 후보 `storeId/menuId`만 전달합니다. Gemini가 후보에 없는 ID를 반환하면 폐기됩니다.

### Q. 왜 DB 후보 기반으로 추천하는가?
가격, 평점, 거리, 리뷰 수, 메뉴 판매 상태는 DB 값이 기준입니다. 없는 가게/메뉴를 생성하지 않기 위해 후보 검증을 서버에서 수행합니다.

### Q. AI가 신고를 무조건 자동 처리하는가?
아닙니다. `AUTO_HIDE`, ruleMatched, policyCodes, 점수 기준, 대상 타입이 모두 맞아야 자동 처리합니다. 아니면 관리자 검토로 넘깁니다.

### Q. 욕설은 왜 대부분 관리자 검토인가?
일반 욕설은 맥락 판단이 필요하므로 `REVIEW_REQUIRED`입니다. 고강도 욕설 + 대상 지칭 + 예외 맥락 없음 + 높은 점수 조건일 때만 자동 숨김 후보입니다.

### Q. 사진 신고는 왜 자동 처리하지 않는가?
이미지 분석 로직이 없어서 첨부가 있고 실질 텍스트가 없으면 `IMAGE-NOT-ANALYZED`로 관리자 검토가 필요합니다.

### Q. 신고 취소하면 데이터가 어떻게 복구되는가?
처리 전 상태를 `ReportProcessHistory`에 저장해두고, 취소 시 게시글/댓글/리뷰 상태 또는 채팅 메시지 deleted 값을 원래 값으로 되돌립니다. 제재 이력이 있으면 매너온도도 처리 전 값으로 복구합니다.

### Q. DataLoader는 운영에서도 켜도 되는가?
아니요. `dev.dummy-data.enabled=true`와 `app.demo-mode=true`가 모두 필요하지만, 운영에서는 둘 다 꺼야 합니다.

### Q. 지도에서 현재 위치가 안 뜨면 뭘 확인해야 하는가?
브라우저 위치 권한, HTTPS 여부, Naver Map client id, Naver Cloud Console의 도메인 등록, `NAVER_MAP_CLIENT_ID` 환경변수를 확인합니다.

## 16. 주요 주의사항

| 항목 | 코드 기준 | 주의 |
|---|---|---|
| 리뷰 작성 가능 시간 | 예약시간부터 3일 | `ErrorCode.UNAUTHORIZED_REVIEW` 메시지는 30분~4시간이라고 되어 있어 불일치 |
| AI 자동 제재 | AI 자동 처리도 매너온도 5.0 차감 | 주석에는 회원 제재를 수행하지 않는다고 되어 있으나 실제 코드는 차감함 |
| AI 자동 잠금 | AI 단건 처리로 즉시 잠금하지 않음 | `lockAccountWhenZero=false` |
| 메뉴 수정 | 권한 검증 후 실제 update 호출 주석 처리 | 메뉴 수정 화면 동작 확인 필요 |
| local profile | `spring.profiles.active=local`, `include=local` 존재 | 운영 profile 분리 필요 |
| CHAT_MEMBER 신고 | 코드에 존재 | 요청 목차의 4개 대상 외 추가 신고 유형 |

## 17. 시연 추천 순서

1. `user1 / qwer1234` 로그인
2. 매칭 지도에서 인덕대 주변 대기자 확인
3. `[시연] AI 한식 우선 추천 채팅방` 입장
4. 약속 장소 화면에서 `AI 식당 추천` 클릭
5. 추천 카드의 가게/메뉴/거리/평점/리뷰 수/AI 이유 확인
6. `장소 후보로 추가` 클릭
7. 약속 폼에서 장소 후보와 시간 후보 투표 확인
8. 예약 완료 또는 종료된 채팅방에서 리뷰 작성 가능 조건 확인
9. `[시연 신고]` 게시글/댓글/채팅/리뷰 중 하나 신고
10. `admin / qwer1234` 로그인
11. 관리자 신고 상세에서 AI 신고 처리 결과 확인
12. 처리 취소/복구 실행
13. 관리자 대시보드에서 AI 자동 처리, 검토 필요, 복구, 중복 신고, 낮은 매너온도, 점주 승인 대기 카드 확인
