package com.neulbom.backend.recording;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.neulbom.backend.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class RecordingStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void storesAndLoadsAudioFromPersistentVolume() {
        RecordingStorage storage = new RecordingStorage(new StorageProperties(
                "persistent-volume",
                storageRoot.toString(),
                "unused",
                DataSize.ofMegabytes(25),
                List.of("audio/wav"),
                List.of("wav")));
        UUID recordingId = UUID.randomUUID();
        byte[] content = "test-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "answer.wav", "audio/wav", content);

        String storageKey = storage.store(recordingId, file);
        RecordingStorage.StoredAudio loaded = storage.load(storageKey);

        assertThat(storageKey).isEqualTo("recordings/" + recordingId + ".wav");
        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.filename()).isEqualTo(recordingId + ".wav");
    }
}
