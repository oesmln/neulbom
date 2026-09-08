package com.neulbom.backend.counseling;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.counseling.api.CounselingCentersResponse;
import com.neulbom.backend.counseling.api.NearbyCentersResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/counseling")
public class CounselingCenterController {

    private final AuthService authService;
    private final CounselingCenterService centerService;

    public CounselingCenterController(AuthService authService, CounselingCenterService centerService) {
        this.authService = authService;
        this.centerService = centerService;
    }

    @GetMapping("/centers")
    public CounselingCentersResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "province_code") String provinceCode,
            @RequestParam(name = "district_code", required = false) String districtCode,
            @RequestParam(name = "facility_type", required = false) String facilityType,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return centerService.list(authenticatedUserId(jwt), provinceCode, districtCode, facilityType, page, limit);
    }

    /**
     * 시·도/시·군·구 표시명으로 카카오 로컬 검색을 대신 호출한다. 등록 기관 테이블과 무관하게
     * 전국 어디서든 치매안심센터·보건소·치매 진료 병원을 찾을 수 있다 (#137).
     */
    @GetMapping("/nearby")
    public NearbyCentersResponse nearby(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "province_name") String provinceName,
            @RequestParam(name = "district_name", required = false) String districtName,
            @RequestParam(name = "facility_type", required = false) String facilityType
    ) {
        return centerService.nearby(authenticatedUserId(jwt), provinceName, districtName, facilityType);
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
