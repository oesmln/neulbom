package com.neulbom.backend.recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.StorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RecordingStorage {

    private final StorageProperties properties;

    public RecordingStorage(StorageProperties properties) {
        this.properties = properties;
    }

    public String store(UUID recordingId, MultipartFile file) {
        if (!"local".equalsIgnoreCase(properties.type())) {
            throw new ExternalServiceUnavailableException("현재 파일 저장소 adapter가 local만 지원합니다.");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 확장자입니다.", "허용된 확장자를 확인하세요.");
        }
        Path root = Path.of(properties.localRoot()).toAbsolutePath().normalize();
        Path target = root.resolve("recordings")
                .resolve(recordingId + "." + extension.toLowerCase(java.util.Locale.ROOT))
                .normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일 저장 경로가 올바르지 않습니다.", "storage 설정을 확인하세요.");
        }
        try {
            Files.createDirectories(target.getParent());
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return root.relativize(target).toString().replace(java.io.File.separatorChar, '/');
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "녹음 파일 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }
}
