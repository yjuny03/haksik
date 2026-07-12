# AI Gemini Realign Plan

## 0. 기준 문서 확인

- 현재 워크스페이스에서 확인한 공모전 기준 문서는 `uploads/docs.md`다.
- 요청에서 언급된 `docs/COMPETITION_FEATURE_SPEC.md` 경로는 현재 존재하지 않는다.
- 이 계획서는 `uploads/docs.md`의 기준을 우선 적용하고, 기존 AI 관련 문서(`uploads/AI_FEATURE_UPGRADE_PLAN.md`, `uploads/AI_MODERATION_PROMPT_POLICY.md`)와 현재 코드 구조를 함께 대조해 작성한다.
- 이 문서는 계획서이며, Java 코드와 설정 파일은 아직 수정하지 않는다.

## 1. 현재 AI 신고 자동 처리와 기준 일치 여부

### 일치하는 부분

- 신고 대상 범위는 기준과 대체로 일치한다.
  - 현재 구현은 `POST`, `COMMENT`, `CHAT_MESSAGE`, `REVIEW`를 AI 자동 처리 가능 대상으로 본다.
  - `CHAT_MEMBER` 등 텍스트 콘텐츠가 아닌 대상은 자동 처리 대상에서 제외된다.

- 자동 처리 후보 유형도 기준과 맞다.
  - `SPAM_AD`
  - `PERSONAL_INFO`
  - `FLOODING`
  - `ABUSE` 중 `ABUSE-SEVERE` 정책 코드가 있는 고강도 욕설

- 애매한 신고는 관리자 검토로 보낸다.
  - `ModerationDecision.REVIEW_REQUIRED`가 존재한다.
  - 서버 보조 규칙이 맞지 않으면 `AUTO_HIDE` 결과도 `REVIEW_REQUIRED`로 강등한다.

- 사진 신고 처리 기준도 맞다.
  - 사진만 있는 신고는 AI provider를 호출하지 않고 `REVIEW_REQUIRED` 결과를 만든다.
  - 텍스트와 사진이 함께 있으면 텍스트만 `AiClient.analyzeModeration()`에 전달한다.

- 관리자 사후 관리도 기준과 맞다.
  - `AiModerationAnalysis`에 분석 결과를 저장한다.
  - 관리자 상세에서 AI 분석 결과를 볼 수 있다.
  - 관리자 취소 시 대상 콘텐츠를 복구하고 AI 분석 상태를 `RESTORED`로 기록한다.

- 알림/대시보드 방향도 기준과 맞다.
  - AI 자동 처리, 검토 필요, 실패 알림 타입이 있다.
  - 관리자 대시보드 패키지와 테스트가 존재한다.

### 불일치 또는 정리 필요

- 최종 모델은 Gemini인데 현재 신고 provider 구현은 OpenAI 중심이다.
  - `OpenAiAiClient`
  - `OpenAiClientConfiguration`
  - `AiOpenAiProperties`
  - `ai.openai.*` 설정
  - `OpenAiAiClientTest`
  - `AiClientProviderSelectionTest`의 openai provider 검증

- 공식 provider 구조가 기준과 다르다.
  - 기준: `ai.provider=mock|gemini`
  - 현재: `ai.provider=mock|openai`

- Gemini는 기존 챗봇에서 직접 호출하지만 신고 `AiClient` provider로는 아직 없다.
  - `ChatbotService`는 `gemini.api.key`와 URL query key 방식으로 직접 호출한다.
  - 신고 moderation용 `GeminiAiClient`는 아직 없다.

- 신뢰도/매너온도 정책은 코드 주석과 실제 동작을 정리해야 한다.
  - 기준은 "AI 자동 처리 시 기존 신고 제재 정책과 연결, 복구 시 제재도 복구, AI 단건 즉시 계정 잠금은 하지 않음"이다.
  - 현재 `autoResolveReport()`는 `trustService.applyAiReportPenalty()`를 호출한다.
  - 그런데 코드 주석 일부는 "AI 자동 처리는 회원 제재를 수행하지 않는다"는 식으로 남아 있어 정책 설명과 충돌한다.
  - 계획상 정답은 제재 연동을 유지하되, AI 단건 계정 잠금 방지와 복구 시 제재 복구를 테스트로 고정하는 것이다.

## 2. OpenAI Provider 처리 판단

### 결론

OpenAI 코드는 즉시 삭제하지 말고 **1차로 비활성화, Gemini 안정화 후 제거**하는 방식이 안전하다.

### 선택지 비교

- 제거
  - 장점: 최종 기획과 코드가 명확히 맞는다.
  - 위험: Gemini 구현 전 structured output, timeout, fallback 테스트 기준까지 한 번에 사라진다.
  - 적용 시점: Gemini provider와 회귀 테스트 통과 이후.

- 비활성화
  - 장점: 최종 provider 경로에서는 제외하면서 기존 참고 구현을 보존할 수 있다.
  - 위험: 레거시 코드가 남아 혼동될 수 있으므로 명확한 TODO와 테스트 제외가 필요하다.
  - 적용 시점: 1차 정렬 단계.

- 임시 유지
  - 장점: 현재 테스트와 비교 기준을 유지할 수 있다.
  - 위험: `ai.provider=openai`가 계속 공식 선택지처럼 보인다.
  - 적용 시점: Gemini 구현 직전까지만.

### 권장 처리

1. 공식 설정과 문서에서는 `openai`를 제거하고 `mock|gemini`만 남긴다.
2. `OpenAiAiClient`는 임시로 남기되 조건값을 `openai-disabled` 같은 값으로 바꿔 실수 활성화를 막는다.
3. `GeminiAiClientTest`, provider 선택 테스트, 신고 회귀 테스트가 통과하면 OpenAI 관련 파일을 삭제한다.

## 3. GeminiAiClient 추가 시 파일 목록

### 신규 파일

- `src/main/java/com/example/haksikmokjang/ai/client/AiGeminiProperties.java`
  - `@ConfigurationProperties(prefix = "ai.gemini")`
  - `apiKey`, `model`, `timeoutSeconds`, `baseUrl`

- `src/main/java/com/example/haksikmokjang/ai/client/GeminiClientConfiguration.java`
  - `@EnableConfigurationProperties(AiGeminiProperties.class)`
  - `@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")`
  - Gemini용 `RestClient` 생성

- `src/main/java/com/example/haksikmokjang/ai/client/GeminiAiClient.java`
  - `AiClient` 구현체
  - `ModerationPromptRequest`를 Gemini `generateContent` 요청으로 변환
  - Gemini 응답을 기존 `ModerationResult`로 파싱
  - 실패 시 `REVIEW_REQUIRED` fallback 반환

- `src/main/java/com/example/haksikmokjang/ai/moderation/ModerationPolicyConstants.java`
  - 선택 사항
  - `MODERATION_POLICY_V1` 같은 정책 상수를 `MockAiClient`에서 분리

- `src/test/java/com/example/haksikmokjang/ai/client/GeminiAiClientTest.java`
  - Gemini 요청/응답 contract 테스트

### 수정 파일

- `src/main/resources/application.properties`
  - `ai.openai.*`를 제거 또는 주석 처리
  - `ai.gemini.*` 추가

- `src/test/java/com/example/haksikmokjang/ai/client/AiClientProviderSelectionTest.java`
  - `openai` 선택 테스트를 `gemini` 선택 테스트로 변경

- `src/main/java/com/example/haksikmokjang/ai/moderation/service/ModerationPolicyService.java`
  - `MockAiClient.POLICY_VERSION` 의존 제거
  - provider와 무관한 정책 검증 서비스로 유지

- `src/main/java/com/example/haksikmokjang/ai/moderation/service/AiModerationService.java`
  - Gemini 실패 fallback 결과가 기존 저장/알림 흐름을 타는지 확인
  - 사진-only 신고는 지금처럼 provider 호출 전 short-circuit 유지

- `src/main/java/com/example/haksikmokjang/chatbot/service/ChatbotService.java`
  - 1차 신고 정렬에서는 수정 보류 가능
  - 2차 맛집 추천 단계에서 `ai.gemini.*` 설정 또는 공통 Gemini 호출 모듈로 통합

### 삭제 또는 비활성화 후보

- `src/main/java/com/example/haksikmokjang/ai/client/OpenAiAiClient.java`
- `src/main/java/com/example/haksikmokjang/ai/client/OpenAiClientConfiguration.java`
- `src/main/java/com/example/haksikmokjang/ai/client/AiOpenAiProperties.java`
- `src/test/java/com/example/haksikmokjang/ai/client/OpenAiAiClientTest.java`

삭제는 Gemini provider 검증 후 진행한다.

## 4. `ai.provider=mock|gemini` 전환 방식

### 목표 설정

```properties
ai.provider=${AI_PROVIDER:mock}
ai.gemini.api-key=${GEMINI_API_KEY:}
ai.gemini.model=${GEMINI_MODEL:gemini-2.5-flash}
ai.gemini.timeout-seconds=${GEMINI_TIMEOUT_SECONDS:10}
ai.gemini.base-url=${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}
```

### Bean 선택

- `MockAiClient`
  - `@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)`
  - 기본값 유지

- `GeminiAiClient`
  - `@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")`
  - `GEMINI_API_KEY`가 있을 때 실제 호출

- `OpenAiAiClient`
  - 공식 provider 선택지에서 제외
  - 임시 유지 시 `openai-disabled` 등 별도 조건값으로 변경

### provider 값 오류 처리

- `ai.provider=mock|gemini` 외 값은 애플리케이션 기동 시 `AiClient` bean이 없어 실패하도록 두는 편이 낫다.
- 운영에서는 잘못된 provider 값이 조용히 mock으로 fallback되면 실제 AI가 꺼진 상태를 놓칠 수 있다.

## 5. `GEMINI_API_KEY` 환경변수 적용 방식

### 설정 방식

- 환경 변수 이름은 기준 문서에 맞춰 `GEMINI_API_KEY`를 사용한다.
- Spring property는 `ai.gemini.api-key=${GEMINI_API_KEY:}`로 둔다.
- 로컬 개발은 OS 환경변수 또는 `application-local.properties`에서만 설정한다.
- Git에는 실제 키를 커밋하지 않는다.

### 호출 방식

- 1차 구현은 현재 챗봇과 같은 Google AI Gemini REST API 방식으로 맞추는 것이 영향이 작다.
- URL 예시는 다음 형태다.
  - `${ai.gemini.base-url}/v1beta/models/{model}:generateContent?key={apiKey}`
- 장기적으로는 API 키를 query string에 넣는 방식보다 공식 SDK 또는 header 인증이 가능한 구조를 검토한다.

### 키 누락 시 정책

- 데모/개발 안정성 우선: `ai.provider=gemini`인데 키가 없으면 `REVIEW_REQUIRED` fallback을 반환한다.
- 운영 안정성 우선: actuator health 또는 startup validation으로 키 누락을 감지한다.
- 신고 접수 자체는 AI 키 문제로 실패하면 안 된다.

## 6. Gemini 응답은 기존 `ModerationResult` JSON 구조에 맞춘다

Gemini는 새 DTO를 만들지 않고 기존 `ModerationResult` 필드로 변환되어야 한다.

필수 필드:

```json
{
  "policyVersion": "MODERATION_POLICY_V1",
  "category": "SPAM_AD",
  "decision": "REVIEW_REQUIRED",
  "riskScore": 0.0,
  "confidenceScore": 0.0,
  "policyCodes": [],
  "containsPersonalInfo": false,
  "personalInfoTypes": [],
  "ruleEvidenceRequired": true,
  "reasonSummary": "관리자 검토가 필요합니다.",
  "evidence": [],
  "missingContext": [],
  "alternativeCategory": null,
  "alternativeReason": null
}
```

검증 원칙:

- enum 값은 기존 `ModerationCategory`, `ModerationDecision`만 허용한다.
- `riskScore`, `confidenceScore`는 0~1 범위만 허용한다.
- `policyVersion`은 `MODERATION_POLICY_V1`만 허용한다.
- `SAFE`와 `AUTO_HIDE`처럼 의미가 충돌하는 결과는 검증 실패로 보고 `REVIEW_REQUIRED`로 보낸다.
- `reasonSummary`에 개인정보 원문이 포함되면 자동 처리하지 않는다.
- Gemini가 후보 외 필드나 Markdown/code fence를 반환해도 서버는 기존 구조만 파싱한다.

## 7. MockAiClient 유지 방식

- `MockAiClient`는 삭제하지 않는다.
- 기본 provider는 계속 `mock`으로 둔다.
- 목적은 다음과 같다.
  - API 키 없이 로컬 개발 가능
  - 외부 API 장애 시 공모전 데모 가능
  - 자동 처리/복구/알림/제재 테스트 결과를 고정

개선 계획:

- `MockAiClient.POLICY_VERSION`은 공통 정책 상수로 이동한다.
- 현재 키워드 기반 mock은 유지하되, 테스트 재현성이 필요하면 test profile 전용 scenario fixture를 추가한다.
- 운영 profile에서는 scenario fixture 입력을 받거나 신뢰하지 않는다.

## 8. Gemini API 실패 시 `REVIEW_REQUIRED` fallback

Gemini 호출 실패는 신고 접수 실패나 자동 숨김으로 이어지면 안 된다.

fallback 대상:

- `GEMINI_API_KEY` 누락
- timeout
- 429 rate limit
- 5xx server error
- 네트워크 예외
- `SAFETY` block 또는 `finishReason=SAFETY`
- 빈 응답
- malformed JSON
- JSON schema/의미 검증 실패

fallback 결과:

- `category=OTHER`
- `decision=REVIEW_REQUIRED`
- `riskScore=0`
- `confidenceScore=0`
- `policyCodes=["AI-PROVIDER-FAILURE"]` 또는 구체 오류 코드
- `reasonSummary="AI 분석을 완료하지 못해 관리자 검토가 필요합니다."`
- `modelName=ai.gemini.model`

저장/알림:

- `AiModerationAnalysis`는 `FAILED` 또는 `REVIEW_REQUIRED`로 저장한다.
- 신고 `Report` 상태는 `PENDING`으로 유지한다.
- 관리자에게 `ADMIN_AI_FAILED` 또는 `ADMIN_AI_REVIEW_REQUIRED` 알림을 보낸다.
- 자동 숨김, 제재, 대상 상태 변경은 수행하지 않는다.

## 9. 사진-only 신고 처리

현재 구조는 기준과 맞으므로 유지한다.

처리 방식:

1. `ModerationTargetContentService`가 대상 텍스트와 첨부 여부를 snapshot으로 반환한다.
2. `hasAttachments=true`이고 `substantiveText=false`이면 Gemini를 호출하지 않는다.
3. 내부 `ModerationResult`를 생성한다.
   - `category=OTHER`
   - `decision=REVIEW_REQUIRED`
   - `policyCodes=["IMAGE-NOT-ANALYZED"]`
   - `reasonSummary="첨부 이미지는 AI 분석 대상이 아니며 텍스트 근거가 부족해 관리자 검토가 필요합니다."`
4. 분석 결과 저장 후 관리자 검토 알림을 보낸다.

텍스트+사진:

- `hasAttachments=true`이고 `substantiveText=true`이면 텍스트만 Gemini에 전달한다.
- 이미지는 Gemini 입력에 포함하지 않는다.

## 10. 기존 기능 회귀 테스트 목록

### AI provider/클라이언트

- `AiClientProviderSelectionTest`
  - `ai.provider=mock`이면 `MockAiClient`
  - `ai.provider=gemini`이면 `GeminiAiClient`
  - 공식 경로에서 `openai`가 선택되지 않음

- `GeminiAiClientTest`
  - 정상 Gemini JSON을 `ModerationResult`로 파싱
  - Gemini safety block은 `REVIEW_REQUIRED` fallback
  - malformed JSON은 `REVIEW_REQUIRED` fallback
  - timeout, 429, 5xx는 `REVIEW_REQUIRED` fallback
  - system instruction과 `<untrusted_content>` 경계가 요청에 포함됨

- `MockModerationPolicyTest`
  - mock provider의 자동 처리/검토/무조치 시나리오 유지

### 신고 자동 처리

- `AiModerationNotificationTest`
  - 자동 처리 성공 시 관리자/신고자/작성자 알림 유지
  - 사진-only 신고는 Gemini 호출 없이 관리자 검토
  - 텍스트+사진은 텍스트만 Gemini 호출
  - provider 실패 시 `PENDING` 유지와 관리자 검토 알림
  - 서버 보조 규칙 불일치 시 자동 처리 금지

- `ModerationTargetContentServiceTest`
  - `POST`, `COMMENT`, `CHAT_MESSAGE`, `REVIEW` 텍스트 추출 유지
  - 첨부만 있는 대상의 `substantiveText=false` 판단 유지

### 신고/복구

- `AdminReportAiRestoreTest`
  - `POST`, `COMMENT`, `CHAT_MESSAGE`, `REVIEW` AI 자동 처리 취소 시 대상 복구
  - `AiModerationAnalysis.processingStatus=RESTORED`
  - `restoreReason`, `restoredAt` 기록
  - AI 처리 제재 이력이 있으면 함께 복구

- `ReportedTargetRestoreTest`
  - 게시글/댓글/채팅/리뷰 기존 복구 기능 유지

- `ReportDuplicatePolicyTest`
  - 중복 신고 방지 유지
  - 취소 이후 재신고 정책 유지

### 제재/매너온도

- `AiReportPenaltyTest`
  - AI 자동 처리 시 기존 신고 제재 정책과 연결
  - AI 단건으로 즉시 계정 잠금이 발생하지 않음
  - 복구 시 `TrustPenaltyHistory` 취소와 매너온도 복구
  - 수동 관리자 제재 복구 경로와 AI 제재 복구 경로가 충돌하지 않음

### 알림

- `NotificationAudienceTest`
  - 사용자 알림과 관리자 알림 분리
  - `ADMIN_AI_REPORT_PROCESSED`, `ADMIN_AI_REVIEW_REQUIRED`, `ADMIN_AI_FAILED`는 관리자 알림 목록에서 확인

- `AdminNotificationEventListenerTest`
  - 알림 실패가 신고 처리 트랜잭션을 깨지 않음

- 추가 필요
  - 알림 읽음 API 소유권 검증
  - AI 알림 내용에 개인정보 원문 미노출

### 대시보드

- `AdminDashboardServiceTest`
  - 미처리 신고 수
  - AI 자동 처리 수
  - AI 검토 필요 수
  - AI 실패 수
  - 복구/RESTORED 수
  - 최근 처리 이력에서 `processorType=AI|ADMIN` 구분

- 프론트 회귀
  - `admin-main.html` KPI 렌더링
  - `admin-reports.html` AI 분석 표시
  - `admin-report.js` 취소/복구 후 상태 갱신
  - `admin-notifications.js` 관리자 알림 갱신

## 11. AI 맛집 추천 도입 단계

### 결론

AI 맛집 추천은 신고 provider 정리 후 별도 단계에서 붙인다.

### 이유

- 신고 moderation은 이미 구현되어 있고 Gemini provider 전환만 집중하면 된다.
- 맛집 추천은 DB 후보 조회, 메뉴/리뷰 집계, 위치 계산, 추천 API, 챗봇 연결까지 범위가 크다.
- 두 작업을 동시에 하면 회귀 범위가 커져 기존 신고/복구/알림/제재 안정성이 떨어진다.

### 권장 단계

1. 1단계: AI 신고 기능을 `mock|gemini` provider 구조로 정리
2. 2단계: Gemini 실패 fallback, 사진-only 관리자 검토, 복구/제재 회귀 테스트 고정
3. 3단계: DB 기반 맛집 후보 조회 서비스 구현
   - 사용자 선호 음식
   - 사용자 위치
   - 가게 카테고리
   - 판매 중 메뉴
   - 활성 리뷰와 평점
4. 4단계: Gemini는 후보 ID 안에서 추천 이유와 설명만 생성
5. 5단계: 기존 `ChatbotService`의 음식 추천 의도를 새 추천 서비스로 위임

### 추천 안전 규칙

- Gemini가 DB에 없는 가게나 메뉴를 만들면 폐기한다.
- 가격, 평점, 영업 상태, 메뉴 판매 상태는 DB 값만 사용한다.
- Gemini 실패 시 DB 점수 기반 fallback을 반환한다.
- 후보가 없으면 허구 추천 대신 조건 완화 안내를 반환한다.

## 12. 실행 순서

1. 기준 문서 위치 정리
   - `uploads/docs.md`를 공식 문서로 둘지, `docs/COMPETITION_FEATURE_SPEC.md`로 이동할지 결정

2. provider 설정 정리
   - 공식 provider를 `mock|gemini`로 고정
   - `GEMINI_API_KEY` 기반 설정 도입
   - OpenAI는 비활성화

3. Gemini provider 구현
   - `GeminiAiClient`
   - `AiGeminiProperties`
   - `GeminiClientConfiguration`
   - `GeminiAiClientTest`

4. fallback과 이미지 정책 검증
   - Gemini 실패는 `REVIEW_REQUIRED`
   - 사진-only 신고는 Gemini 호출 없음
   - 텍스트+사진은 텍스트만 분석

5. 기존 기능 회귀 테스트
   - 신고
   - 복구
   - 알림
   - 제재/매너온도
   - 대시보드

6. OpenAI 제거
   - Gemini 경로가 안정화된 뒤 삭제

7. AI 맛집 추천 추가
   - DB 후보 기반 추천부터 구현
   - Gemini는 설명 생성에만 사용

## 13. 최종 목표 상태

- 공식 AI provider는 `mock|gemini`다.
- `MockAiClient`는 테스트/데모용으로 유지한다.
- `GeminiAiClient`는 기존 `ModerationResult` JSON 구조를 그대로 사용한다.
- Gemini 실패는 항상 관리자 검토 fallback으로 간다.
- 사진-only 신고는 Gemini를 호출하지 않는다.
- 기존 AI 신고 자동 처리, 복구, 알림, 제재, 대시보드 기능은 삭제하지 않는다.
- OpenAI 코드는 영향 범위를 확인한 뒤 비활성화하고, Gemini 안정화 후 제거한다.
- AI 맛집 추천은 신고 기능 정리 후 DB 후보 기반으로 붙인다.
