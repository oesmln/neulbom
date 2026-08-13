package com.neulbom.backend.counseling;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.counseling.api.CounselingCentersResponse;
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

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
