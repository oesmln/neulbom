package com.neulbom.backend.common.file;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.config.StorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileUploadValidator {

    private final StorageProperties storageProperties;

    public FileUploadValidator(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "업로드 파일이 없습니다.", "비어 있지 않은 파일을 전송하세요.");
        }
        if (file.getSize() > storageProperties.maxFileSize().toBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 파일 용량이 제한을 초과했습니다.", "허용 크기를 확인하세요.");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)
                || storageProperties.allowedExtensions().stream().noneMatch(extension::equalsIgnoreCase)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 확장자입니다.", "허용된 확장자를 확인하세요.");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !storageProperties.allowedMimeTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다.", "허용된 MIME type을 확인하세요.");
        }
    }
}
