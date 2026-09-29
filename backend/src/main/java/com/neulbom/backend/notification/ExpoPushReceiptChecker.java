package com.neulbom.backend.notification;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.config.ExternalApiExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Checks Expo delivery receipts and removes devices rejected by FCM or APNs. */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ExpoPushReceiptChecker {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushReceiptChecker.class);
    private static final String RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";

    private final ExpoPushReceiptRepository receipts;
    private final PushDeviceRepository devices;
    private final RestClient restClient;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExpoPushReceiptChecker(
            ExpoPushReceiptRepository receipts,
            PushDeviceRepository devices,
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.receipts = receipts;
        this.devices = devices;
        this.restClient = restClient;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 900_000, fixedDelay = 900_000)
    public void checkPendingReceipts() {
        List<ExpoPushReceiptEntity> pending = receipts
                .findTop100ByCheckedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(clock.instant().minus(Duration.ofMinutes(15)));
        if (pending.isEmpty()) return;
        var request = objectMapper.createObjectNode();
        var ids = request.putArray("ids");
        pending.forEach(receipt -> ids.add(receipt.getTicketId()));
        JsonNode response;
        try {
            response = executor.execute("Expo Push", () -> restClient.post()
                    .uri(RECEIPTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (RuntimeException exception) {
            log.warn("푸시 수신 확인 실패 reason={}", exception.getClass().getSimpleName());
            return;
        }
        JsonNode data = response == null ? null : response.path("data");
        for (ExpoPushReceiptEntity receipt : pending) {
            JsonNode result = data == null ? null : data.path(receipt.getTicketId());
            if (result == null || result.isMissingNode()) {
                if (receipt.getCreatedAt().isBefore(clock.instant().minus(Duration.ofHours(24)))) {
                    receipt.checked("expired", clock.instant());
                    receipts.save(receipt);
                }
                continue;
            }
            String status = result.path("status").asText("error");
            String error = result.path("details").path("error").asText("unknown");
            if ("DeviceNotRegistered".equals(error)) {
                devices.findById(receipt.getExpoPushToken()).ifPresent(devices::delete);
                continue;
            }
            receipt.checked("ok".equals(status) ? "ok" : "error", clock.instant());
            receipts.save(receipt);
            if (!"ok".equals(status)) {
                log.warn("푸시 수신 오류 notification_id={} error={}", receipt.getNotificationId(), error);
            }
        }
    }
}
