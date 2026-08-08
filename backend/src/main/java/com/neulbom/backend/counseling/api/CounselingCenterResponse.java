package com.neulbom.backend.counseling.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CounselingCenterResponse(
        UUID centerId,
        String name,
        String facilityType,
        String provinceCode,
        String districtCode,
        String provinceName,
        String districtName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String naverMapUrl,
        String homepageUrl,
        String reservationMode,
        String sourceName,
        Instant sourceUpdatedAt
) {
}
