package com.neulbom.backend.counseling.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CounselingCentersResponse(List<CounselingCenterResponse> centers, int total) {
}
