package com.neulbom.backend.analysis.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import com.neulbom.backend.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoogleSttAudioConverterTest {

    private final GoogleSttAudioConverter converter = new GoogleSttAudioConverter();

    @Test
    void leavesWavUnchanged() {
        byte[] wav = new byte[]{1, 2, 3};
        assertThat(converter.convert(new SpeechToTextClient.AudioFile(wav, "answer.wav", "audio/wav")))
                .isSameAs(wav);
    }

    @Test
    void decodesAacM4aToMono16KhzPcmWav(@TempDir Path directory) throws Exception {
        assumeTrue(ffmpegAvailable());
        Path input = directory.resolve("synthetic.m4a");
        Process generator = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=16000:duration=59.97",
                "-c:a", "aac", input.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertThat(generator.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(generator.exitValue()).isZero();

        byte[] m4a = Files.readAllBytes(input);
        byte[] wav = converter.convert(new SpeechToTextClient.AudioFile(
                m4a, "synthetic.m4a", "audio/mp4"));
        byte[] mislabeledWav = converter.convert(new SpeechToTextClient.AudioFile(
                m4a, "synthetic.m4a", "audio/mpeg"));
        assertThat(mislabeledWav).isEqualTo(wav);
        assertThat(wav).startsWith(new byte[]{'R', 'I', 'F', 'F'});
        try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat format = stream.getFormat();
            assertThat(format.getEncoding()).isEqualTo(AudioFormat.Encoding.PCM_SIGNED);
            assertThat(format.getChannels()).isEqualTo(1);
            assertThat(format.getSampleRate()).isEqualTo(16_000f);
            assertThat(format.getSampleSizeInBits()).isEqualTo(16);
            assertThat(stream.getFrameLength()).isGreaterThan(59L * 16_000);
        }
    }

    @Test
    void rejectsInvalidM4a() {
        assumeTrue(ffmpegAvailable());
        assertThatThrownBy(() -> converter.convert(new SpeechToTextClient.AudioFile(
                new byte[]{1, 2, 3}, "broken.m4a", "audio/mp4")))
                .isInstanceOf(ApiException.class);
    }

    private boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
