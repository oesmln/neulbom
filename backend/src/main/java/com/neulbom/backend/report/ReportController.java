package com.neulbom.backend.report;

import java.time.LocalDate;
import java.util.UUID;

import com.neulbom.backend.auth.service.AuthService;
import com.neulbom.backend.report.api.BenchmarkResponse;
import com.neulbom.backend.report.api.DashboardResponse;
import com.neulbom.backend.report.api.GuardianReportResponse;
import com.neulbom.backend.report.api.HistoryResponse;
import com.neulbom.backend.report.api.ReportExportResponse;
import com.neulbom.backend.report.api.ScreeningResultResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final AuthService authService;
    private final ReportService reportService;

    public ReportController(AuthService authService, ReportService reportService) {
        this.authService = authService;
        this.reportService = reportService;
    }

    @GetMapping("/screenings/{sessionId}/result")
    public ScreeningResultResponse getScreeningResult(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reportService.getScreeningResult(authenticatedUserId(jwt), sessionId);
    }

    @GetMapping("/analysis/cognitive/{userId}/history")
    public HistoryResponse getHistory(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "30") int limit,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String aggregation
    ) {
        return reportService.getHistory(authenticatedUserId(jwt), userId, limit, fromDate, toDate, aggregation);
    }

    @GetMapping("/analysis/cognitive/{userId}/benchmark")
    public BenchmarkResponse getBenchmark(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "province_code") String provinceCode,
            @RequestParam(name = "district_code", required = false) String districtCode,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String aggregation
    ) {
        return reportService.getBenchmark(authenticatedUserId(jwt), userId, provinceCode, districtCode, fromDate, toDate, aggregation);
    }

    @GetMapping("/dashboard/{userId}")
    public DashboardResponse getDashboard(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        return reportService.getDashboard(authenticatedUserId(jwt), userId);
    }

    @GetMapping("/guardian/{guardianId}/report")
    public GuardianReportResponse getGuardianReport(
            @PathVariable UUID guardianId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "elder_id") UUID elderId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "Asia/Seoul") String timezone
    ) {
        return reportService.getGuardianReport(authenticatedUserId(jwt), guardianId, elderId, date, fromDate, toDate, timezone);
    }

    @GetMapping("/guardian/{guardianId}/report/export")
    public ResponseEntity<ReportExportResponse> requestReportExport(
            @PathVariable UUID guardianId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "elder_id") UUID elderId,
            @RequestParam(name = "from_date") LocalDate fromDate,
            @RequestParam(name = "to_date") LocalDate toDate,
            @RequestParam String format,
            @RequestParam(required = false, defaultValue = "Asia/Seoul") String timezone
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reportService.requestExport(
                authenticatedUserId(jwt), guardianId, elderId, fromDate, toDate, format, timezone));
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return authService.authenticatedUserId(jwt.getSubject());
    }
}
