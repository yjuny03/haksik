# Gemini AI Moderation Setup

## Local properties

Use Gemini for AI moderation with these properties:

```properties
ai.provider=gemini
ai.gemini.api-key=${GEMINI_API_KEY}
ai.gemini.model=${GEMINI_MODEL:gemini-3.5-flash}
ai.gemini.timeout-seconds=${GEMINI_TIMEOUT_SECONDS:10}
ai.gemini.base-url=https://generativelanguage.googleapis.com
```

Do not commit real API keys.

Official AI moderation provider values are:

```text
mock
gemini
```

`mock` keeps all AI analysis local for tests and demos. `gemini` calls the Gemini API.

## Check available models

Model availability can differ by API key, project, region, and Google rollout status. Check the models available to the current key before changing `ai.gemini.model`:

```http
GET https://generativelanguage.googleapis.com/v1beta/models?key=GEMINI_API_KEY
```

Use a model that supports `generateContent`, for example:

```text
gemini-3.5-flash
```

If Gemini returns `404 NOT_FOUND` for a model, the application keeps the existing fallback behavior and records the moderation result as `REVIEW_REQUIRED`.

## Current demo policy

- General abuse or insults: `REVIEW_REQUIRED`
- Clearly severe abuse with `ABUSE-SEVERE`: eligible for `AUTO_HIDE`
- Ambiguous content: `REVIEW_REQUIRED`
- Image-only reports: `REVIEW_REQUIRED`
