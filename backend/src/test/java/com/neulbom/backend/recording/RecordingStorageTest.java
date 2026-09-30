package com.neulbom.backend.recording;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Files;
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
    void storesAndLoadsAudioFromPersistentVolume() throws Exception {
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
        assertThat(Files.readAllBytes(storageRoot.resolve(storageKey))).isNotEqualTo(content);
        storage.delete(storageKey);
        assertThat(Files.exists(storageRoot.resolve(storageKey))).isFalse();
    }

    @Test
    void migratesPlaintextRecordingsAndKeepsThemReadable() throws Exception {
        RecordingStorage storage = new RecordingStorage(new StorageProperties(
                "persistent-volume", storageRoot.toString(), "unused", DataSize.ofMegabytes(25),
                List.of("audio/wav"), List.of("wav")));
        Path legacy = storageRoot.resolve("recordings/legacy.wav");
        Files.createDirectories(legacy.getParent());
        byte[] content = "legacy-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(legacy, content);

        storage.encryptLegacyFiles();

        assertThat(Files.readAllBytes(legacy)).isNotEqualTo(content);
        assertThat(storage.load("recordings/legacy.wav").content()).isEqualTo(content);
    }
}
