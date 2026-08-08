package com.neulbom.backend.counseling;

import java.util.List;
import java.util.Set;

import com.neulbom.backend.common.exception.ApiException;

import com.neulbom.backend.counseling.api.CounselingCenterResponse;
import com.neulbom.backend.counseling.api.CounselingCentersResponse;
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

    public CounselingCenterService(CounselingCenterRepository centerRepository, UserRepository userRepository) {
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
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

    private CounselingCenterResponse toResponse(CounselingCenterEntity center) {
        return new CounselingCenterResponse(center.getId(), center.getName(), center.getFacilityType(), center.getProvinceCode(),
                center.getDistrictCode(), center.getProvinceName(), center.getDistrictName(), center.getAddress(), center.getLatitude(),
                center.getLongitude(), center.getPhone(), center.getMapUrl(), center.getWebsiteUrl(), center.getReservationMode(),
                center.getSourceName(), center.getSourceUpdatedAt());
    }
}
