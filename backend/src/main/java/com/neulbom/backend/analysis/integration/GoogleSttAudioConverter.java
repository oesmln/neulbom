package com.neulbom.backend.analysis.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Decodes Android AAC/M4A recordings before sending them to Google STT. */
@Component
public class GoogleSttAudioConverter {

    private static final Duration CONVERSION_TIMEOUT = Duration.ofSeconds(20);

    public byte[] convert(SpeechToTextClient.AudioFile audioFile) {
        if (!isMp4Audio(audioFile.contentType()) && !isMp4Container(audioFile.content())) {
            return audioFile.content();
        }

        Path directory = null;
        try {
            directory = Files.createTempDirectory("neulbom-stt-");
            Path input = directory.resolve("input.m4a");
            Path output = directory.resolve("output.wav");
            Files.write(input, audioFile.content());

            Process process = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                    "-i", input.toString(), "-map", "0:a:0", "-vn",
                    "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
                    "-t", "61", "-f", "wav", output.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(CONVERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ExternalServiceUnavailableException("음성 파일 변환 시간이 초과되었습니다.");
            }
            if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) <= 44) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "음성 파일을 변환할 수 없습니다.", "녹음 파일을 다시 확인하세요.");
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("음성 파일 변환이 중단되었습니다.");
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("음성 파일 변환을 실행할 수 없습니다.");
        } finally {
            if (directory != null) {
                deleteQuietly(directory.resolve("input.m4a"));
                deleteQuietly(directory.resolve("output.wav"));
                deleteQuietly(directory);
            }
        }
    }

    private boolean isMp4Audio(String contentType) {
        String normalized = contentType == null ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "audio/mp4".equals(normalized) || "audio/x-m4a".equals(normalized);
    }

    private boolean isMp4Container(byte[] content) {
        return content != null && content.length >= 12
                && content[4] == 'f' && content[5] == 't'
                && content[6] == 'y' && content[7] == 'p';
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cleanup must not replace the conversion result or error.
        }
    }
}
