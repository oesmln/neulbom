package com.neulbom.backend.counseling.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 카카오 로컬 검색으로 찾은 주변 기관. 등록 기관 목록과 같은 카드 형식을 쓰되,
 * provider 상태를 함께 내려 앱이 검색 링크로 폴백할 수 있게 한다.
 *
 * @param providerStatus {@code ok} · {@code not_configured} · {@code unavailable}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NearbyCentersResponse(
        List<CounselingCenterResponse> centers,
        int total,
        String providerStatus
) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_NOT_CONFIGURED = "not_configured";
    public static final String STATUS_UNAVAILABLE = "unavailable";
}
