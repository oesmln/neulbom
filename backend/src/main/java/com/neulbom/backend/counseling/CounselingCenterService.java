package com.neulbom.backend.counseling;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import java.util.List;
import java.util.Set;

import com.neulbom.backend.common.exception.ApiException;

import com.neulbom.backend.counseling.api.CounselingCenterResponse;
import com.neulbom.backend.counseling.api.CounselingCentersResponse;
import com.neulbom.backend.counseling.api.NearbyCentersResponse;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CounselingCenterService {

    private static final Set<String> FACILITY_TYPES = Set.of("hospital", "dementia_center", "public_health_center");

    private final CounselingCenterRepository centerRepository;
    private final UserRepository userRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final Clock clock;

    /** facility_type → 카카오 검색 키워드. "병원"은 일반 신경과가 아니라 치매 진료를 우선 찾는다. */
    static final Map<String, String> NEARBY_KEYWORDS = new LinkedHashMap<>();
    static {
        NEARBY_KEYWORDS.put("dementia_center", "치매안심센터");
        NEARBY_KEYWORDS.put("public_health_center", "보건소");
        NEARBY_KEYWORDS.put("hospital", "치매 진료 병원");
    }
    private static final int NEARBY_PAGE_SIZE = 15;
    private static final String KAKAO_SOURCE_NAME = "카카오 로컬";

    public CounselingCenterService(
            CounselingCenterRepository centerRepository,
            UserRepository userRepository,
            KakaoLocalClient kakaoLocalClient,
            Clock clock
    ) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.kakaoLocalClient = kakaoLocalClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CounselingCentersResponse list(java.util.UUID authenticatedUserId, String provinceCode, String districtCode, String facilityType, int page, int limit) {
        userRepository.findById(authenticatedUserId).filter(UserEntity::isActive)
                .orElseThrow(() -> new AccessDeniedException("활성 사용자만 상담 센터를 조회할 수 있습니다."));
        if (page < 1 || limit < 1 || limit > 100) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "page/limit을 확인하세요.");
        if (facilityType != null && !facilityType.isBlank() && !FACILITY_TYPES.contains(facilityType)) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "facility_type 허용값을 확인하세요.");
        if (provinceCode == null || provinceCode.isBlank()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "province_code가 필요합니다.");
        List<CounselingCenterResponse> filtered = centerRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .filter(center -> provinceCode == null || provinceCode.isBlank() || provinceCode.equals(center.getProvinceCode()))
                .filter(center -> districtCode == null || districtCode.isBlank() || districtCode.equals(center.getDistrictCode()))
                .filter(center -> facilityType == null || facilityType.isBlank() || facilityType.equals(center.getFacilityType()))
                .map(this::toResponse)
                .toList();
        int from = Math.min((page - 1) * limit, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        return new CounselingCentersResponse(filtered.subList(from, to), filtered.size());
    }

    /**
     * 카카오 로컬 검색으로 주변 기관을 찾아 등록 기관과 같은 카드 형식으로 돌려준다.
     * provider가 없거나 실패하면 503 대신 빈 목록과 {@code provider_status}를 내려 앱이
     * 검색 링크로 폴백할 수 있게 한다 — 등록 기관 목록은 이 경로와 무관하게 계속 동작한다.
     */
    public NearbyCentersResponse nearby(UUID authenticatedUserId, String provinceName, String districtName, String facilityType) {
        userRepository.findById(authenticatedUserId).filter(UserEntity::isActive)
                .orElseThrow(() -> new AccessDeniedException("활성 사용자만 상담 센터를 조회할 수 있습니다."));
        if (provinceName == null || provinceName.isBlank()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "province_name이 필요합니다.");
        if (facilityType != null && !facilityType.isBlank() && !NEARBY_KEYWORDS.containsKey(facilityType)) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", "facility_type 허용값을 확인하세요.");
        if (!kakaoLocalClient.isConfigured()) {
            return new NearbyCentersResponse(List.of(), 0, NearbyCentersResponse.STATUS_NOT_CONFIGURED);
        }
        String region = districtName == null || districtName.isBlank() ? provinceName.trim() : provinceName.trim() + " " + districtName.trim();
        List<String> types = facilityType == null || facilityType.isBlank() ? List.copyOf(NEARBY_KEYWORDS.keySet()) : List.of(facilityType);
        List<CounselingCenterResponse> centers = new ArrayList<>();
        try {
            for (String type : types) {
                for (KakaoLocalClient.Place place : kakaoLocalClient.search(region + " " + NEARBY_KEYWORDS.get(type), NEARBY_PAGE_SIZE)) {
                    centers.add(toResponse(place, type, provinceName.trim(), districtName));
                }
            }
        } catch (ExternalServiceUnavailableException exception) {
            return new NearbyCentersResponse(List.of(), 0, NearbyCentersResponse.STATUS_UNAVAILABLE);
        }
        return new NearbyCentersResponse(centers, centers.size(), NearbyCentersResponse.STATUS_OK);
    }

    private CounselingCenterResponse toResponse(KakaoLocalClient.Place place, String facilityType, String provinceName, String districtName) {
        // 카카오 장소 ID로 결정적 UUID를 만들어 앱이 목록 키로 쓸 수 있게 한다. DB에는 저장하지 않는다.
        UUID id = UUID.nameUUIDFromBytes(("kakao-place:" + place.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CounselingCenterResponse(id, place.name(), facilityType, null, null, provinceName,
                districtName == null || districtName.isBlank() ? null : districtName.trim(), place.address(), place.latitude(),
                place.longitude(), place.phone(), null, place.placeUrl(), "external_link", KAKAO_SOURCE_NAME, clock.instant());
    }

    private CounselingCenterResponse toResponse(CounselingCenterEntity center) {
        return new CounselingCenterResponse(center.getId(), center.getName(), center.getFacilityType(), center.getProvinceCode(),
                center.getDistrictCode(), center.getProvinceName(), center.getDistrictName(), center.getAddress(), center.getLatitude(),
                center.getLongitude(), center.getPhone(), center.getMapUrl(), center.getWebsiteUrl(), center.getReservationMode(),
                center.getSourceName(), center.getSourceUpdatedAt());
    }
}
