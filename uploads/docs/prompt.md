# AI 기능 현재 기준 메모

기본 설정으로 서버를 실행하면 `MockAiClient`로 분석합니다.

실제 Gemini로 신고를 분석하려면 다음 설정을 사용합니다.

```properties
ai.provider=gemini
ai.gemini.api-key=${GEMINI_API_KEY}
ai.gemini.model=${GEMINI_MODEL:gemini-3.5-flash}
ai.gemini.timeout-seconds=${GEMINI_TIMEOUT_SECONDS:10}
ai.gemini.base-url=https://generativelanguage.googleapis.com
```

공식 provider는 `mock|gemini`만 유지합니다.

관리자 신고 상세의 모델 항목으로 확인합니다.

- `mock-moderation-v1`: Mock 분석
- `gemini-*`: Gemini 분석
- `AI-PROVIDER-FAILURE`: AI 호출 또는 응답 처리 실패로 관리자 검토 전환

데모 정책은 보수적으로 유지합니다.

- 일반 욕설/모욕: `REVIEW_REQUIRED`
- `ABUSE-SEVERE` 조건을 만족하는 명백한 고강도 욕설: `AUTO_HIDE` 후보
- 애매한 경우: 관리자 검토
