package com.neulbom.backend.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.file.FileUploadValidator;
import com.neulbom.backend.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class FileUploadValidatorTest {

    private final FileUploadValidator validator = new FileUploadValidator(new StorageProperties(
            "local",
            "./uploads",
            "neulbom-test",
            DataSize.ofMegabytes(25),
            List.of("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4", "audio/webm"),
            List.of("wav", "mp3", "m4a", "webm")
    ));

    @Test
    void acceptsDocumentedAudioFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "audio_file",
                "answer.wav",
                "audio/wav",
                new byte[]{1, 2, 3}
        );

        validator.validate(file);
    }

    @Test
    void acceptsWebmRecordedByExpoWeb() {
        MockMultipartFile file = new MockMultipartFile(
                "audio_file",
                "answer.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );

        validator.validate(file);
    }

    @Test
    void rejectsMismatchedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio_file",
                "answer.wav",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ApiException) error).error())
                        .isEqualTo("지원하지 않는 파일 형식입니다."));
    }
}
