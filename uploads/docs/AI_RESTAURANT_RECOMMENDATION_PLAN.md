# AI 맛집 추천 기능 계획서

## 0. 목표와 원칙

이 기능은 Gemini가 식당을 새로 찾거나 정보를 생성하는 기능이 아니다. 서버가 DB에 존재하는 가게, 메뉴, 리뷰만 후보로 조회하고 점수화한 뒤, Gemini는 그 후보 안에서 추천 이유와 사용자에게 보여줄 설명 문구만 생성한다.

핵심 원칙은 다음과 같다.

- 추천 대상은 반드시 DB에 존재하는 `Store`와 `Menu`만 사용한다.
- Gemini가 없는 가게, 없는 메뉴, 없는 가격, 없는 평점, 없는 리뷰를 만들어내면 응답을 폐기한다.
- 가격, 평점, 거리, 리뷰 수, 영업 상태, 메뉴 상태는 항상 DB 값을 최종 응답에 사용한다.
- Gemini 실패, 응답 파싱 실패, 후보 외 ID 반환 시 DB 점수 기반 fallback 추천을 제공한다.
- 기존 AI 신고 기능, 관리자 기능, 기존 지도 기능은 건드리지 않고 별도 추천 패키지/API로 확장한다.

## 1. 현재 프로젝트 구조 분석

### Store 구조

현재 가게 도메인은 `com.example.haksikmokjang.ownerpage.store.domain.Store`에 있다.

주요 필드:

- `storeId`
- `member`: 점주 `Member`
- `name`
- `address`
- `latitude`, `longitude`: `Double`, DB `DECIMAL(10,7)`
- `category`: 문자열 카테고리
- `phone`
- `operatingHours`
- `businessStatus`: `OPEN`, `BREAK_TIME`, `CLOSED`

현재 `StoreRepository`에는 반경 검색 메서드가 이미 있다.

```java
List<Store> findNearbyStores(Double lat, Double lng, Double radius)
```

이 쿼리는 Haversine 계산으로 반경 km 안의 가게를 조회하고, `business_status != 'CLOSED'` 조건을 사용한다. MVP에서는 이 쿼리를 재사용하되, 추천용으로는 거리 값까지 함께 계산하거나 서비스 레이어에서 다시 계산하는 방식이 필요하다.

현재 가게 조회 API:

- `GET /api/stores/nearby?lat=&lng=&radius=`
- `GET /api/chat/forms/stores/nearby?lat=&lng=&radius=`
- `GET /api/chat/forms/store-details/{storeId}`

채팅 약속 장소 투표에서 주변 가게와 상세 정보를 이미 사용하고 있으므로, 추천 결과는 이 화면으로 연결하기 쉽다.

### Menu 구조

메뉴 도메인은 `Menu`에 있다.

주요 필드:

- `menuId`
- `store`
- `name`
- `price`
- `salesStatus`: `ON_SALE`, `SOLD_OUT`

현재 `MenuRepository`:

```java
List<Menu> findByStore_StoreId(Long storeId)
```

추천 후보에는 `salesStatus = ON_SALE` 메뉴만 포함해야 한다. MVP에서는 Repository에 `findByStore_StoreIdAndSalesStatus(...)` 또는 여러 storeId를 한 번에 조회하는 추천 전용 쿼리를 추가하는 방향이 적절하다.

### StoreReview 구조

리뷰 도메인은 `StoreReview`에 있다.

주요 필드:

- `reviewId`
- `store`
- `member`
- `reservation`
- `rating`: 1~5
- `content`
- `status`: `ACTIVE`, `DELETED`
- `createdAt`
- `ownerReply`

현재 리뷰 조회 구조:

- `StoreReviewRepository.findByMember_LoginIdAndStatus(...)`
- `ChatStoreReviewRepository.findByStore_StoreIdAndStatusOrderByCreatedAtDesc(...)`

채팅 식당 상세 조회에서는 `ACTIVE` 리뷰만 가져와 평균 평점과 리뷰 수를 계산한다. 추천도 동일하게 `ReviewStatus.ACTIVE`만 사용해야 한다. `DELETED` 리뷰는 Gemini 입력과 점수 계산에서 제외한다.

### UserProfile 선호 음식 카테고리

`UserProfile`에는 `preferredFoodCategory` 문자열 필드가 있다.

현재 업데이트 API:

- `POST /api/members/profile/food?foodCategory=한식`

화면에는 한식, 양식, 중식, 일식 값이 사용된다. 추천 MVP에서는 이 값을 사용자 선호 카테고리로 사용하고, 비어 있으면 선호 카테고리 점수를 0점으로 처리한다.

### MemberLocation 또는 현재 위치 구조

현재 위치 저장은 `MemberLocation`에 있다.

주요 필드:

- `userProfile`
- `latitude`, `longitude`: `BigDecimal`
- `locationPublicYn`: `Y`/`N`
- `accuracyRangeM`

`MemberLocationRepository`에는 `findByUserProfile(...)`, `findByUserProfile_UserProfileId(...)`가 있다.

다만 기존 지도/채팅 화면은 브라우저 Geolocation으로 받은 `lat`, `lng`를 API 파라미터로 직접 보내는 흐름이 많다. 추천 API도 MVP에서는 요청 좌표를 우선 사용하고, 요청 좌표가 없을 때만 저장된 `MemberLocation`을 fallback으로 사용하는 설계가 좋다.

### 기존 지도/가게 조회 API 구조

현재 지도와 채팅 약속 장소 화면에서 가게를 조회하는 구조가 있다.

- `StoreController.getNearbyStores`: `/api/stores/nearby`
- `ChatFormController.getNearbyStores`: `/api/chat/forms/stores/nearby`
- `ChatFormController.getStoreDetail`: `/api/chat/forms/store-details/{storeId}`
- `ChatFormService.getStoreDetail`은 메뉴, 리뷰, 평균 평점, 리뷰 수를 이미 조립한다.

추천 기능은 이 기존 API를 수정하지 말고 별도 컨트롤러를 추가하는 것이 안전하다.

제안 패키지:

- `com.example.haksikmokjang.ai.recommendation.controller`
- `com.example.haksikmokjang.ai.recommendation.service`
- `com.example.haksikmokjang.ai.recommendation.dto`
- `com.example.haksikmokjang.ai.recommendation.repository`는 필요 시 최소화

## 2. 추천 후보 조회 기준

### 기본 흐름

1. 로그인 사용자 확인
2. 요청 좌표 `lat`, `lng` 확인
3. 없으면 `UserProfile -> MemberLocation`에서 현재 위치 조회
4. 기본 반경으로 주변 가게 조회
5. 후보가 부족하면 반경 확장
6. 영업/메뉴/리뷰 조건으로 후보 정리
7. DB 점수 계산
8. Top N 후보만 Gemini에 전달
9. Gemini 응답을 서버에서 검증
10. 검증 실패 시 DB 점수 순 fallback

### 반경 기준

MVP 기본값:

- 기본 반경: 1km
- 후보가 3개 미만이면 2km로 확장
- 그래도 부족하면 최대 3km까지 확장
- API 파라미터로 `radiusKm`를 받을 수 있지만 최대값은 서버에서 제한한다.

추천 화면에서 너무 먼 가게가 나오면 신뢰도가 떨어지므로 최대 반경은 3km 정도가 적절하다. 채팅 장소 투표는 현재 기본 3km를 쓰고 있으므로, 추천은 1km부터 시작해 확장하는 방식이 더 자연스럽다.

### 포함 조건

추천 후보에 포함할 조건:

- `Store.businessStatus != CLOSED`
- `Store.latitude`, `Store.longitude`가 null이 아님
- 판매 중인 메뉴가 1개 이상 있음
- 메뉴는 `Menu.salesStatus = ON_SALE`만 사용
- 리뷰는 `StoreReview.status = ACTIVE`만 사용
- 점주 계정이 승인된 상태인지 확인 가능한 경우 `OwnerProfile.approvalStatus = APPROVED` 조건 추가

현재 `Store` 자체에는 승인 여부 필드가 없다. 점주 승인 여부는 `OwnerProfile`에 있으므로 추천 쿼리에서 `Store.member -> OwnerProfile` 조인이 필요할 수 있다. 구현 시 `OwnerProfileRepository` 구조를 재확인하고, 승인 조건이 복잡하면 MVP 1차에서는 `Store`가 등록되어 있고 영업 종료가 아닌 가게만 대상으로 하되, 2차에서 승인 점주 조건을 추가한다.

### 제외 조건

- `CLOSED` 가게
- 좌표가 없는 가게
- 판매 중인 메뉴가 없는 가게
- `DELETED` 리뷰
- DB에 없는 메뉴/리뷰/가격/평점 정보
- Gemini가 새로 만든 텍스트 안의 가격/평점/리뷰 수는 표시하지 않음

## 3. DB 점수 산정 방식

처음부터 복잡한 추천 모델을 만들지 않는다. MVP는 서버에서 설명 가능한 단순 가중치로 점수화한다.

총점 100점 기준 예시:

- 거리 점수: 30점
- 사용자 선호 카테고리 일치: 20점
- 가게 카테고리 일치 또는 유사도: 10점
- 평균 평점: 20점
- 리뷰 개수: 10점
- 최근 리뷰 긍정 키워드: 5점
- 판매 중 메뉴 다양성 또는 대표 메뉴 존재: 5점

### 거리 점수

1km 기본 반경 기준:

- 300m 이하: 30점
- 700m 이하: 22점
- 1km 이하: 15점
- 2km 이하: 8점
- 그 이상: 3점

거리 계산은 Haversine 공식을 Java 서비스 레이어에 별도 유틸로 둔다. DB 조회는 반경 필터, 서버 점수 계산은 정확한 표시용 거리 산정으로 분리한다.

### 사용자 선호 카테고리 점수

`UserProfile.preferredFoodCategory`와 `Store.category` 또는 메뉴명 기반 매칭을 사용한다.

예:

- 선호 카테고리와 `Store.category`가 정확히 같음: 20점
- 메뉴명에 선호 카테고리 대표 키워드가 있음: 12점
- 선호 미설정: 0점

대표 키워드는 처음에는 코드 상수로 시작한다.

- 한식: 김치, 찌개, 덮밥, 국밥, 비빔, 제육
- 중식: 짜장, 짬뽕, 탕수, 마라
- 일식: 돈카츠, 초밥, 라멘, 우동, 덮밥
- 양식: 파스타, 피자, 스테이크, 버거, 샐러드

### 가게 카테고리 점수

가게 카테고리가 추천 요청의 `category` 필터와 일치하면 10점. 요청 카테고리가 없으면 이 항목은 0점으로 둔다.

### 평균 평점 점수

`ACTIVE` 리뷰 기준 평균 평점을 사용한다.

- 리뷰 없음: 8점
- 4.5 이상: 20점
- 4.0 이상: 16점
- 3.5 이상: 12점
- 그 외: 6점

리뷰가 없다고 무조건 제외하면 신규 가게가 불리하므로 낮은 기본점을 준다.

### 리뷰 개수 점수

- 20개 이상: 10점
- 10개 이상: 8점
- 5개 이상: 5점
- 1개 이상: 3점
- 0개: 0점

### 최근 리뷰 긍정 키워드 점수

최근 `ACTIVE` 리뷰 3~5개에서 단순 키워드 매칭을 한다.

긍정 키워드 예:

- 맛있
- 친절
- 가성비
- 양 많
- 재방문
- 깔끔
- 빠르

MVP에서는 NLP를 하지 않고 키워드 포함 수만 반영한다. 개인정보 가능성이 있는 리뷰 원문 전체를 Gemini에 많이 보내지 않도록 snippet 길이를 제한한다.

### 메뉴 다양성 또는 대표 메뉴 점수

- 판매 중 메뉴 5개 이상: 5점
- 판매 중 메뉴 2~4개: 3점
- 판매 중 메뉴 1개: 1점

대표 메뉴는 별도 필드가 없으므로 MVP에서는 판매 중 메뉴 중 첫 번째 또는 가격/이름이 정상인 메뉴를 추천 메뉴 후보로 둔다.

## 4. Gemini 입력 데이터 구조

Gemini에는 DB 점수 상위 Top N개만 전달한다.

MVP 제안:

- Top N: 5개
- 최대 리뷰 snippet: 가게당 최근 3개
- snippet 길이: 각 80자 이하
- 메뉴: 가게당 판매 중 메뉴 최대 5개

개인정보나 불필요한 사용자 정보는 보내지 않는다. 사용자 입력 중 Gemini에 전달할 수 있는 것은 선호 음식 카테고리와 요청 상황 정도로 제한한다.

Gemini 입력 예시:

```json
{
  "policy": {
    "mustUseOnlyCandidateIds": true,
    "doNotInventStoreMenuPriceReview": true,
    "useDbValuesOnly": true
  },
  "userContext": {
    "preferredFoodCategory": "한식",
    "requestPurpose": "nearby_restaurant_recommendation"
  },
  "candidates": [
    {
      "storeId": 12,
      "storeName": "학식분식",
      "category": "한식",
      "distanceMeters": 420,
      "averageRating": 4.6,
      "reviewCount": 18,
      "dbScore": 86.5,
      "menus": [
        {
          "menuId": 31,
          "name": "제육덮밥"
        },
        {
          "menuId": 32,
          "name": "김치찌개"
        }
      ],
      "reviewSnippets": [
        "제육이 맛있고 양이 많아요.",
        "점심시간에도 빨리 나왔습니다."
      ]
    }
  ]
}
```

Gemini에 가격을 보내지 않는 방향도 가능하지만, 화면에 추천 메뉴 가격을 표시하려면 서버 응답에서 DB 가격을 붙이면 된다. Gemini가 가격을 말하지 못하도록 프롬프트에 가격/평점/거리/리뷰 수 생성 금지를 명시한다.

## 5. Gemini 응답 JSON 구조

Gemini 응답은 추천 설명에 필요한 최소 필드만 받는다.

```json
{
  "recommendations": [
    {
      "recommendedStoreId": 12,
      "recommendedMenuId": 31,
      "rank": 1,
      "reason": "가까운 거리와 선호 카테고리인 한식에 잘 맞고, 최근 리뷰에서 양과 속도에 대한 만족도가 높습니다.",
      "matchedPreference": "한식 선호와 제육덮밥 메뉴가 잘 맞습니다.",
      "distanceText": "가까운 거리입니다.",
      "reviewSummary": "최근 리뷰에서 양이 많고 빠르다는 반응이 있습니다.",
      "cautionMessage": ""
    }
  ]
}
```

필드 설명:

- `recommendedStoreId`: 후보 목록에 있는 storeId만 허용
- `recommendedMenuId`: 해당 가게의 판매 중 메뉴 ID만 허용
- `rank`: 1부터 시작
- `reason`: 추천 이유
- `matchedPreference`: 선호 음식과 어떻게 맞는지
- `distanceText`: “도보권”, “가까운 편” 같은 정성 문구
- `reviewSummary`: 전달한 reviewSnippets 안에서만 요약
- `cautionMessage`: 리뷰가 적거나 영업 상태가 `BREAK_TIME`인 경우 안내 문구

`recommendedMenuId`를 우선 사용한다. 메뉴 ID 검증이 실패할 수 있으므로 `menuName`만 받는 방식은 보조로만 사용한다. 최종 응답에는 DB에서 찾은 `Menu.menuId`, `Menu.name`, `Menu.price`를 사용한다.

## 6. 서버 검증

Gemini 응답은 절대 그대로 신뢰하지 않는다.

검증 규칙:

- `recommendedStoreId`가 후보 목록에 없으면 해당 추천 폐기
- `recommendedMenuId`가 없거나 해당 가게의 `ON_SALE` 메뉴가 아니면 해당 메뉴 추천은 폐기
- `recommendedMenuId`가 실패하고 `menuName`이 있는 경우, 해당 가게 판매 중 메뉴명과 정확히 일치할 때만 사용
- Gemini가 반환한 가격, 평점, 거리, 리뷰 수는 사용하지 않음
- `reason`, `matchedPreference`, `reviewSummary`에 후보 밖 가게/메뉴 이름이 들어가면 fallback 문구로 대체
- JSON 파싱 실패, schema 불일치, 빈 응답이면 DB 점수 순위 fallback
- Gemini 호출 실패면 DB 점수 순위 fallback

fallback 응답 원칙:

- Top 1~3개를 DB 점수 순으로 반환
- 추천 이유는 서버 템플릿으로 생성
- 예: “선호 카테고리와 가까운 거리, 평점 기준으로 추천합니다.”
- fallback 여부를 응답에 `source = "DB_FALLBACK"`으로 표시

## 7. API 설계

### 추천 요청 API

새 컨트롤러 제안:

```text
GET /api/ai/restaurants/recommendations
```

Query parameter:

- `lat`: optional, 현재 위도
- `lng`: optional, 현재 경도
- `radiusKm`: optional, 기본 1.0
- `category`: optional, 한식/중식/일식/양식 등
- `limit`: optional, 기본 3, 최대 5
- `context`: optional, `MAP`, `CHAT_PLACE`, `MATCHING_AFTER`

좌표 우선순위:

1. 요청의 `lat`, `lng`
2. 로그인 사용자의 `MemberLocation`
3. 없으면 400 또는 “현재 위치 필요” 응답

### 선호 카테고리 기반 추천

별도 API를 만들기보다 같은 API에서 로그인 사용자의 `UserProfile.preferredFoodCategory`를 기본 선호로 사용한다.

요청 `category`가 있으면:

- 화면 필터로 간주
- 사용자 선호와 요청 카테고리를 모두 점수에 반영

요청 `category`가 없으면:

- `UserProfile.preferredFoodCategory`만 반영

### 응답 구조

```json
{
  "source": "GEMINI",
  "center": {
    "latitude": 37.6299,
    "longitude": 127.0548
  },
  "radiusKm": 1.0,
  "expandedRadiusKm": 2.0,
  "preferredFoodCategory": "한식",
  "recommendations": [
    {
      "rank": 1,
      "storeId": 12,
      "storeName": "학식분식",
      "category": "한식",
      "businessStatus": "OPEN",
      "distanceMeters": 420,
      "distanceText": "가까운 거리입니다.",
      "averageRating": 4.6,
      "reviewCount": 18,
      "recommendedMenu": {
        "menuId": 31,
        "name": "제육덮밥",
        "price": 7000,
        "imageId": 100
      },
      "storeImageId": 55,
      "reason": "가까운 거리와 선호 카테고리인 한식에 잘 맞습니다.",
      "matchedPreference": "한식 선호와 제육덮밥 메뉴가 잘 맞습니다.",
      "reviewSummary": "최근 리뷰에서 양이 많고 빠르다는 반응이 있습니다.",
      "cautionMessage": "",
      "dbScore": 86.5
    }
  ],
  "fallbackReason": null
}
```

`source` 값:

- `GEMINI`
- `DB_FALLBACK`

### 지도 화면 또는 매칭 후 식당 선택 화면 연동

지도 화면:

- 현재 위치를 얻은 뒤 `/api/ai/restaurants/recommendations?lat=&lng=&context=MAP` 호출
- 결과의 `storeId`로 기존 상세 API 또는 신규 상세 이동

채팅 약속 장소 투표:

- 기존 `/api/chat/forms/stores/nearby`와 같은 좌표를 사용
- 추천 API 결과에서 선택한 가게를 `ChatFormOptionRequest`로 추가
- 기존 `storeId`, `placeName`, `address`, `latitude`, `longitude` 구조를 재사용

매칭 후 식당 선택:

- 매칭이 성사된 채팅방 또는 약속 폼 화면에서 `context=CHAT_PLACE`로 호출
- 추천 결과를 장소 후보 추가 UI에 연결

## 8. 구현 구성 제안

### 신규 DTO

패키지:

```text
com.example.haksikmokjang.ai.recommendation.dto
```

제안 DTO:

- `RestaurantRecommendationRequest`
- `RestaurantRecommendationResponse`
- `RestaurantRecommendationItem`
- `RecommendedMenuResponse`
- `RestaurantCandidate`
- `RestaurantCandidateMenu`
- `GeminiRestaurantRecommendationRequest`
- `GeminiRestaurantRecommendationResponse`

### 신규 서비스

패키지:

```text
com.example.haksikmokjang.ai.recommendation.service
```

제안 서비스:

- `RestaurantRecommendationService`
  - 요청 좌표/사용자 선호 조회
  - 후보 조회
  - 점수 계산
  - Gemini 호출
  - 응답 검증
  - fallback 조립
- `RestaurantCandidateQueryService`
  - 주변 가게, 메뉴, 리뷰, 이미지 조회 조립
- `RestaurantScoringService`
  - 거리/카테고리/평점/리뷰/메뉴 점수 계산
- `GeminiRestaurantRecommendationClient`
  - Gemini 호출 전용
  - 기존 `GeminiAiClient`는 신고용으로 유지하고 직접 변경하지 않음

기존 AI 신고와 추천은 프롬프트, schema, fallback 기준이 다르므로 같은 클라이언트를 무리하게 공용화하지 않는다. 공통 설정인 `AiGeminiProperties`와 `RestClient` 구성만 재사용할 수 있다.

### Repository 확장

MVP에서 필요한 조회:

- 반경 내 가게 조회: 기존 `StoreRepository.findNearbyStores` 재사용 또는 추천 전용 projection 추가
- 여러 가게의 판매 중 메뉴 조회
- 여러 가게의 ACTIVE 리뷰 조회
- 가게 이미지/메뉴 이미지 조회

추천 전용으로 추가할 수 있는 메서드:

```java
List<Menu> findByStore_StoreIdInAndSalesStatus(List<Long> storeIds, MenuStatus salesStatus);
List<StoreReview> findByStore_StoreIdInAndStatusOrderByCreatedAtDesc(List<Long> storeIds, ReviewStatus status);
```

성능상 N+1을 피하려면 후보 storeId 목록으로 메뉴와 리뷰를 한 번에 조회한 뒤 Java에서 grouping 한다.

## 9. 화면 연결 제안

### 지도 화면 하단 추천 카드

위치 갱신 후 하단에 추천 카드 1~3개를 보여준다.

카드 구성:

- 가게명
- 카테고리
- 거리
- 평점/리뷰 수
- 추천 메뉴명/가격
- “AI 추천 이유 보기” 버튼
- “상세 보기” 버튼

기존 `matching-map.js`는 매칭 대기자 마커 중심이므로 직접 섞기보다 별도 추천 카드 영역을 추가하는 방식이 안전하다.

### 매칭 후 식당 선택 화면 추천 버튼

채팅 장소 투표 지도 모달에 “AI 추천” 버튼을 추가한다.

동작:

1. 현재 지도 중심 또는 사용자 위치로 추천 API 호출
2. 추천 카드 표시
3. “장소 후보로 추가” 클릭 시 기존 장소 옵션 추가 API 호출

기존 `ChatFormService.addOption`과 `ChatFormOptionRequest` 구조를 재사용할 수 있다.

### AI 추천 이유 보기 영역

추천 결과 카드에서 접을 수 있는 영역으로 제공한다.

표시 내용:

- `reason`
- `matchedPreference`
- `reviewSummary`
- `cautionMessage`

주의: Gemini 설명에는 DB 확정값 외 숫자를 넣지 않는다. 거리/평점/가격은 서버 응답 필드로 별도 표시한다.

### 가게 상세/예약 이동

추천 결과에서:

- 상세 보기: 기존 식당 상세 모달 또는 `/api/chat/forms/store-details/{storeId}` 응답 기반 화면
- 예약: 기존 예약 생성 흐름이 필요한 화면에서 storeId를 넘겨 예약 폼으로 연결
- 채팅 장소 투표: 추천 가게를 장소 후보로 추가

## 10. 테스트 시나리오

### 후보 조회

- 반경 안에 가게가 있으면 후보로 조회된다.
- 반경 안에 가게가 없으면 반경을 2km, 3km로 확장한다.
- 최대 반경에도 없으면 빈 추천 또는 “주변 가게 없음” 응답을 반환한다.
- `CLOSED` 가게는 제외된다.
- 좌표가 없는 가게는 제외된다.
- 판매 중 메뉴가 없는 가게는 제외된다.
- `SOLD_OUT` 메뉴만 있는 가게는 제외된다.
- `DELETED` 리뷰는 평점/리뷰 수/스니펫에서 제외된다.

### 점수 산정

- 선호 카테고리와 가게 카테고리가 일치하면 점수가 올라간다.
- 같은 거리라면 평점 높은 가게가 우선된다.
- 같은 평점이라면 리뷰 많은 가게가 우선된다.
- 리뷰가 없어도 판매 중 메뉴가 있으면 낮은 기본점으로 후보에 남을 수 있다.
- 최근 리뷰 긍정 키워드가 있으면 소폭 가산된다.

### Gemini 검증

- Gemini가 후보 목록에 없는 `storeId`를 반환하면 폐기하고 DB fallback을 사용한다.
- Gemini가 해당 가게에 없는 `menuId`를 반환하면 메뉴 추천은 DB fallback 메뉴로 대체하거나 전체 추천을 폐기한다.
- Gemini가 없는 메뉴명을 반환하면 폐기한다.
- Gemini가 가격/평점/거리/리뷰 수를 반환해도 최종 응답에는 DB 값을 사용한다.
- Gemini 응답 JSON이 깨지면 DB fallback을 사용한다.
- Gemini 호출 실패 시 DB fallback을 사용한다.

### API/화면

- 현재 위치 기반 추천 API가 로그인 사용자에게 응답한다.
- 요청 좌표가 없고 저장 위치도 없으면 위치 필요 응답을 준다.
- 지도 화면에서 추천 카드가 표시된다.
- 채팅 장소 투표 화면에서 추천 가게를 후보로 추가할 수 있다.
- 추천 결과에서 가게 상세로 이동할 수 있다.

## 11. 단계별 구현 순서

### 1단계: DB 후보와 점수 기반 추천만 구현

- 추천 전용 DTO/Service/Controller 추가
- 주변 가게 조회, 판매 중 메뉴 조회, ACTIVE 리뷰 조회
- 거리 계산과 단순 점수 산정
- `source=DB_FALLBACK` 형태로 먼저 응답
- 이 단계에서는 Gemini를 호출하지 않는다.

목표: DB에 존재하는 가게와 메뉴만으로 안정적인 추천 응답을 만든다.

### 2단계: Gemini 설명 생성 추가

- `GeminiRestaurantRecommendationClient` 추가
- Top 5 후보만 Gemini에 전달
- JSON 응답 파싱
- 서버 검증 후 `source=GEMINI` 응답
- 실패 시 1단계 DB fallback 유지

목표: Gemini 실패가 사용자 기능 실패로 이어지지 않게 한다.

### 3단계: 화면 연결

- 지도 화면 하단 추천 카드
- 채팅 장소 투표 모달 추천 버튼
- 추천 이유 보기 UI
- 가게 상세/장소 후보 추가 연결

목표: 기존 지도/채팅 흐름을 깨지 않고 추천을 보조 기능으로 붙인다.

### 4단계: 테스트와 데모 데이터 정리

- 후보 조회 테스트
- 점수 계산 테스트
- Gemini 응답 검증 테스트
- fallback 테스트
- 메뉴 없는 가게 제외 테스트
- 공모전 데모용 가게/메뉴/리뷰 데이터 준비

## 12. 보안과 개인정보 주의사항

- Gemini에는 사용자 이름, 닉네임, 학과, 생년월일, 성별, 정확한 회원 ID를 보내지 않는다.
- 위치는 추천 계산에는 필요하지만 Gemini에는 좌표 원문 대신 거리 값만 전달한다.
- 리뷰는 최근 일부 snippet만 보내고, 길이를 제한한다.
- 리뷰 작성자 정보는 보내지 않는다.
- Gemini가 생성한 문구에 DB 후보 밖 정보가 섞이면 폐기하거나 서버 fallback 문구로 대체한다.
- API 응답에서 가격/평점/거리/리뷰 수는 DB 값만 사용한다.

## 13. MVP에서 하지 않을 것

- 음식 추천 모델 학습
- 사용자별 장기 행동 로그 기반 개인화
- 리뷰 감성 분석 모델 도입
- 가게/메뉴 자동 생성
- 기존 AI 신고 정책 변경
- 기존 관리자/제재/대시보드 기능 변경

## 14. 공모전 발표 포인트

- 단순 챗봇 추천이 아니라 DB 검증 기반 추천이다.
- Gemini가 hallucination으로 없는 가게를 만들 수 없도록 서버 검증을 둔다.
- 위치, 선호 카테고리, 평점, 리뷰, 메뉴를 함께 고려한다.
- Gemini 장애 시에도 DB 점수 기반 추천으로 서비스가 계속 동작한다.
- 기존 지도/채팅 약속 장소 투표와 자연스럽게 연결된다.
- 개인정보 최소화 원칙으로 AI 입력 데이터를 제한한다.
