package com.neulbom.backend.recording;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Encrypts legacy plaintext recordings before the application starts serving requests. */
@Component
public class RecordingEncryptionMigration implements ApplicationRunner {

    private final RecordingStorage recordingStorage;

    public RecordingEncryptionMigration(RecordingStorage recordingStorage) {
        this.recordingStorage = recordingStorage;
    }

    @Override
    public void run(ApplicationArguments args) {
        recordingStorage.encryptLegacyFiles();
    }
}
