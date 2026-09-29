package com.neulbom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.config.ExternalApiExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushDeliveryTest {

    @Test
    void sendsCommittedDiaryPushAndStoresReceiptId() {
        UUID userId = UUID.randomUUID();
        NotificationEntity notification = notification(userId);
        PushDeviceEntity device = new PushDeviceEntity("ExponentPushToken[demo123]", userId, "android", Instant.now());
        NotificationRepository notifications = mock(NotificationRepository.class);
        PushDeviceRepository devices = mock(PushDeviceRepository.class);
        ExpoPushReceiptRepository receipts = mock(ExpoPushReceiptRepository.class);
        NotificationService service = mock(NotificationService.class);
        ExternalApiExecutor executor = mock(ExternalApiExecutor.class);
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(devices.findAllByUserId(userId)).thenReturn(List.of(device));
        when(service.isOsPushEnabled(userId, notification.getType())).thenReturn(true);
        when(executor.execute(eq("Expo Push"), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"status\":\"ok\",\"id\":\"ticket-1\"}}", MediaType.APPLICATION_JSON));

        new ExpoPushDelivery(notifications, devices, receipts, service, builder.build(), executor,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC))
                .onNotificationCreated(new NotificationCreatedEvent(notification.getId()));

        ArgumentCaptor<ExpoPushReceiptEntity> saved = ArgumentCaptor.forClass(ExpoPushReceiptEntity.class);
        verify(receipts).save(saved.capture());
        assertThat(saved.getValue().getTicketId()).isEqualTo("ticket-1");
        server.verify();
    }

    private NotificationEntity notification(UUID userId) {
        return new NotificationEntity(UUID.randomUUID(), userId, "새 일기", "일기를 확인해 보세요.",
                NotificationService.TYPE_DIARY_GENERATED, "info", "새 일기",
                "{\"reference_type\":\"diary\",\"reference_id\":\"" + UUID.randomUUID() + "\"}",
                "diary-test", Instant.now());
    }
}
