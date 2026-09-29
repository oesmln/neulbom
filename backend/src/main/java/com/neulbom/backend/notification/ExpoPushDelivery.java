package com.neulbom.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neulbom.backend.config.ExternalApiExecutor;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/** Sends committed diary notifications to registered guardian devices. */
@Component
public class ExpoPushDelivery {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushDelivery.class);
    private static final String SEND_URL = "https://exp.host/--/api/v2/push/send";

    private final NotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final ExpoPushReceiptRepository receipts;
    private final NotificationService notificationService;
    private final RestClient restClient;
    private final ExternalApiExecutor executor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExpoPushDelivery(
            NotificationRepository notifications,
            PushDeviceRepository devices,
            ExpoPushReceiptRepository receipts,
            NotificationService notificationService,
            @Qualifier("externalRestClient") RestClient restClient,
            ExternalApiExecutor executor,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.notifications = notifications;
        this.devices = devices;
        this.receipts = receipts;
        this.notificationService = notificationService;
        this.restClient = restClient;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        NotificationEntity notification = notifications.findById(event.notificationId()).orElse(null);
        if (notification == null || !notificationService.isOsPushEnabled(notification.getRecipientUserId(), notification.getType())) return;
        for (PushDeviceEntity device : devices.findAllByUserId(notification.getRecipientUserId())) {
            try {
                send(notification, device);
            } catch (RuntimeException exception) {
                log.warn("일기 푸시 전송 실패 notification_id={} reason={}", notification.getId(), exception.getClass().getSimpleName());
            }
        }
    }

    private void send(NotificationEntity notification, PushDeviceEntity device) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("to", device.getExpoPushToken());
        message.put("title", notification.getTitle());
        message.put("body", notification.getBody());
        message.put("sound", "default");
        message.put("priority", "high");
        if ("android".equals(device.getPlatform())) message.put("channelId", "diary");
        if (notification.getData() != null) {
            try {
                message.set("data", objectMapper.readTree(notification.getData()));
            } catch (Exception exception) {
                log.warn("푸시 데이터 파싱 실패 notification_id={}", notification.getId());
                return;
            }
        }
        JsonNode response = executor.execute("Expo Push", () -> restClient.post()
                .uri(SEND_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .body(JsonNode.class));
        JsonNode data = response == null ? null : response.path("data");
        JsonNode ticket = data == null ? null : data.isArray() ? data.path(0) : data;
        if (ticket == null || !"ok".equals(ticket.path("status").asText())) {
            String error = ticket == null ? "missing_ticket" : ticket.path("details").path("error").asText("unknown");
            if ("DeviceNotRegistered".equals(error)) devices.delete(device);
            log.warn("일기 푸시 티켓 오류 notification_id={} error={}", notification.getId(), error);
            return;
        }
        String ticketId = ticket.path("id").asText(null);
        if (ticketId != null && !ticketId.isBlank()) {
            receipts.save(new ExpoPushReceiptEntity(ticketId, device.getExpoPushToken(), notification.getId(), clock.instant()));
        }
    }
}
