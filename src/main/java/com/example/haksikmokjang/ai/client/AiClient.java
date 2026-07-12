package com.example.haksikmokjang.ai.client;

import com.example.haksikmokjang.ai.moderation.dto.ModerationPromptRequest;
import com.example.haksikmokjang.ai.moderation.dto.ModerationResult;

public interface AiClient {

    ModerationResult analyzeModeration(ModerationPromptRequest request);
}
