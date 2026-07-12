package com.example.haksikmokjang.ai.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecommendedMenuResponse {
    private Long menuId;
    private String name;
    private Integer price;
    private Long imageId;
}
