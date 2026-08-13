package com.neulbom.backend.counseling;

import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.counseling.api.CounselingCentersResponse;
import com.neulbom.backend.counseling.api.CounselingAppointmentCreateRequest;
import com.neulbom.backend.counseling.api.CounselingAppointmentResponse;
import com.neulbom.backend.counseling.api.CounselingAppointmentUpdateRequest;
import com.neulbom.backend.counseling.api.CounselingAppointmentsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final CounselingAppointmentService appointmentService;

    public CounselingCenterController(
            AuthService authService,
            CounselingCenterService centerService,
            CounselingAppointmentService appointmentService
    ) {
        this.authService = authService;
        this.centerService = centerService;
        this.appointmentService = appointmentService;
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

    @PostMapping("/appointments")
    public ResponseEntity<CounselingAppointmentResponse> createAppointment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CounselingAppointmentCreateRequest request
    ) {
        return ResponseEntity.status(201).body(appointmentService.create(authenticatedUserId(jwt), request));
    }

    @GetMapping("/appointments")
    public CounselingAppointmentsResponse listAppointments(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.list(authenticatedUserId(jwt));
    }

    @PatchMapping("/appointments/{appointmentId}")
    public CounselingAppointmentResponse updateAppointment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CounselingAppointmentUpdateRequest request
    ) {
        return appointmentService.update(authenticatedUserId(jwt), appointmentId, request);
    }

    @DeleteMapping("/appointments/{appointmentId}")
    public ResponseEntity<Void> cancelAppointment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId
    ) {
        appointmentService.cancel(authenticatedUserId(jwt), appointmentId);
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
