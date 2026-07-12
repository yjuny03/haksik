package com.example.haksikmokjang.ai.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationResponse {
    private Double latitude;
    private Double longitude;
}
