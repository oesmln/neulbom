package com.neulbom.backend.notification;

import java.time.Clock;
import java.util.UUID;

import com.neulbom.backend.notification.api.PushDeviceRequest;
import com.neulbom.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceService {

    private final PushDeviceRepository devices;
    private final UserRepository users;
    private final Clock clock;

    public PushDeviceService(PushDeviceRepository devices, UserRepository users, Clock clock) {
        this.devices = devices;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void register(UUID userId, PushDeviceRequest request) {
        requireGuardian(userId);
        PushDeviceEntity device = devices.findById(request.expoPushToken()).orElse(null);
        if (device == null) {
            devices.save(new PushDeviceEntity(request.expoPushToken(), userId, request.platform(), clock.instant()));
        } else {
            device.assignTo(userId, request.platform(), clock.instant());
        }
    }

    @Transactional
    public void unregister(UUID userId, PushDeviceRequest request) {
        requireGuardian(userId);
        devices.findById(request.expoPushToken())
                .filter(device -> userId.equals(device.getUserId()))
                .ifPresent(devices::delete);
    }

    private void requireGuardian(UUID userId) {
        users.findById(userId)
                .filter(user -> user.isActive() && "guardian".equals(user.getRole()))
                .orElseThrow(() -> new AccessDeniedException("보호자 계정만 푸시를 등록할 수 있습니다."));
    }
}
