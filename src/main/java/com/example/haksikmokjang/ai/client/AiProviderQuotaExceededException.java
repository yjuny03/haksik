package com.example.haksikmokjang.ai.client;

public class AiProviderQuotaExceededException extends RuntimeException {

    public static final String USER_MESSAGE = "AI 사용량 한도 초과로 관리자 검토가 필요합니다.";

    public AiProviderQuotaExceededException() {
        super(USER_MESSAGE);
    }
}
