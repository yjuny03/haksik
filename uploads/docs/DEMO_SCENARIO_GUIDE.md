# Demo Scenario Guide

## DataLoader 실행 설정

시연 데이터는 `ApplicationRunner` 기반 `DataLoader`가 생성합니다.

반드시 두 설정이 모두 켜져야 실행됩니다.

```properties
dev.dummy-data.enabled=true
app.demo-mode=true
```

`dev.dummy-data.enabled=true`만으로는 실행되지 않습니다. `app.demo-mode=true`가 아니면 DataLoader가 즉시 중단됩니다.

## 로그인 계정

- 관리자: `admin / qwer1234`
- 일반 사용자: `user1` ~ `user120 / qwer1234`
- 점주: `owner1` ~ `owner60 / qwer1234`
- 승인 대기 점주: `owner60 / qwer1234`

## 시연 순서

1. `user1`로 로그인합니다.
2. 매칭 지도에서 인덕대학교 주변 대기자를 확인합니다.
3. 채팅 목록에서 `[시연] AI 한식 우선 추천 채팅방`에 입장합니다.
4. 열린 약속 투표 카드에서 지도 화면을 열고 `AI 식당 추천` 버튼을 클릭합니다.
5. 추천 카드에서 가게명, 추천 메뉴, 거리, 평점/리뷰 수, 참여자 선호 문구를 확인합니다.
6. 추천 카드의 `장소 후보로 추가`를 클릭합니다.
7. 약속 폼의 장소 후보 목록에 식당이 추가되었는지 확인합니다.
8. 리뷰 작성 시연은 종료된 더미 채팅방의 리뷰 대상에서 진행합니다.
9. 커뮤니티/채팅/리뷰 화면에서 `[시연 신고]` 대상 콘텐츠를 신고합니다.
10. 관리자 화면에서 AI 신고 처리 결과를 확인합니다.
11. 관리자 대시보드에서 AI 자동 처리, 검토 필요, 중복 신고, 낮은 매너온도 사용자, 승인 대기 점주를 확인합니다.
12. 처리 취소/복구 시나리오는 `복구/취소된 신고` 상태 데이터를 사용해 확인합니다.

## 추천 시나리오

- 채팅방 A: `[시연] AI 한식 우선 추천 채팅방`
  - 참여자: `user1`, `user2`, `user3`
  - 선호 음식: `user1=한식`, `user2=한식`, `user3=중식`
  - 한식 가게/한식 메뉴가 우선되는지 확인합니다.

- 채팅방 B: `[시연] AI 동률 추천 채팅방`
  - 참여자: `user4`, `user5`
  - 선호 음식: `user4=중식`, `user5=일식`
  - 동률 처리와 DB 후보 수 우선/랜덤 처리를 확인합니다.

추천 테스트 API 예시:

```http
GET /api/chat/rooms/{chatRoomId}/ai/restaurant-recommendations?lat=37.631668&lng=127.054045&radiusKm=1&limit=3&context=CHAT_PLACE
```

DataLoader 실행 완료 로그에 실제 채팅방 ID와 API URL이 출력됩니다.

## 주요 가게/메뉴/리뷰 확인

- `인덕 제육하우스`: 한식 A, 리뷰 많고 평점 높음
- `초안산 김치찌개`: 한식 B, 더 가깝지만 리뷰 적음
- `석계 마라탕`: 중식, 중간 평점
- `월계 초밥`: 일식, 거리 조금 멀지만 리뷰 좋음
- `공릉 파스타`: 양식, 평점 보통
- `데모 품절분식` 역할: 57번 가게는 SOLD_OUT 메뉴만 있어 추천에서 제외됩니다.
- `데모 휴무가게` 역할: 58번 가게는 CLOSED 상태라 추천에서 제외됩니다.

정상 가게는 ON_SALE 메뉴 3~5개와 일부 SOLD_OUT 메뉴를 함께 가집니다. 리뷰는 ACTIVE와 DELETED가 섞여 있으며 추천 점수에는 ACTIVE 리뷰만 반영됩니다.

## 신고 테스트 대상

- `[시연 신고] 스팸 게시글`
- `[시연 신고] 개인정보 댓글`
- `[시연 신고] 일반 욕설 채팅 메시지`
- `[시연 신고] 욕설/허위 리뷰`
- `[시연 신고] 사진-only 게시글`
- `[시연 신고] 사진-only 리뷰`

일부 대상은 신고 전 상태로 남고, 일부는 이미 AI 자동 처리/검토 필요/복구 상태로 생성되어 관리자 화면과 대시보드를 바로 확인할 수 있습니다.

## AI 신고 자동 처리 시연 문장

아래 문장은 운영 정책 시연용입니다. 실제 시연에서는 관리자 화면에서 `decision`, `processingStatus`, `autoHideBasis`, 관련 항목 목록, 복구 상태를 함께 확인합니다.

| 대상 | 문장 예시 | 기대 결과 |
|---|---|---|
| 스팸 | `무조건 수익 보장 가입하면 지급 https://spam.example` | `SPAM_AD/AUTO_HIDE`, 자동 블라인드 |
| 도배 | `광고글 광고글 광고글 광고글` | `FLOODING/AUTO_HIDE`, 자동 블라인드 |
| 욕설 | `너는 정말 개새끼야` | `ABUSE/AUTO_HIDE`, 자동 블라인드 |
| 성적 괴롭힘 | `작성자 몸 보여줘` | `SEXUAL_HARASSMENT/AUTO_HIDE`, 자동 블라인드 |
| 개인정보 | `저 사람 번호 010-1234-5678 박제한다` | `PERSONAL_INFO/AUTO_HIDE`, 자동 블라인드 |
| 피해 신고 | `친구가 나한테 병신이라고 했어요` | 자동 처리 제외, 관리자 검토 |
| 인용/예시 | `성희롱 예시로 몸 보여줘 같은 말이 있습니다` | 자동 처리 제외, 관리자 검토 |
| 공개 연락처 | `가게 전화번호는 02-123-4567입니다` | 개인정보 related hide 제외 또는 관리자 검토 |

관련 항목 함께 블라인드는 신고 대상이 자동 처리된 뒤에만 실행됩니다.

- 채팅 메시지: `SPAM_AD`, `FLOODING`, `ABUSE`, `SEXUAL_HARASSMENT`, `PERSONAL_INFO`
- 댓글: `ABUSE`, `SEXUAL_HARASSMENT`, `SPAM_AD`, `FLOODING`, `PERSONAL_INFO`
- 다른 작성자, 신고자, 피해 신고/인용/예시 문맥, 정상 링크/공개 연락처는 함께 숨기지 않습니다.
- 관련 항목이 여러 건 숨겨져도 매너온도와 알림은 신고 대상 1건 기준입니다.
- 관리자 복구 버튼을 누르면 신고 대상과 `ReportRelatedActionItem`에 저장된 관련 항목이 함께 복구되고, 관련 항목은 `복구 완료`로 표시됩니다.
- Gemini 무료 API 한도 초과가 발생하면 `AI_FAILED`로 저장되고 신고는 `PENDING` 상태로 남아 관리자 수동 처리가 가능합니다.

## 채팅방 ID 찾는 방법

DataLoader 실행 완료 후 콘솔에 아래 형식으로 출력됩니다.

```text
CHAT A : [시연] AI 한식 우선 추천 채팅방 / id={id}
CHAT B : [시연] AI 동률 추천 채팅방 / id={id}
```

브라우저에서는 채팅 목록에서 같은 방 이름을 찾거나 `/api/view/user/chat/rooms/{chatRoomId}`로 직접 접근합니다.

## 재실행 정책

DataLoader는 loginId, roomName, post title, review content prefix, report reporter/target key, store owner, menu store/name 기준으로 기존 데이터를 재사용합니다.

hard delete를 사용하지 않으며, 재실행해도 같은 시연 데이터가 과도하게 중복 생성되지 않도록 구성되어 있습니다.
