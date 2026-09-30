package com.neulbom.backend.recording;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.neulbom.backend.common.exception.ApiException;
import com.neulbom.backend.common.exception.ExternalServiceUnavailableException;
import com.neulbom.backend.config.StorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RecordingStorage {

    private static final byte[] ENCRYPTED_FILE_MAGIC = "NEULBOM1".getBytes(StandardCharsets.US_ASCII);
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StorageProperties properties;
    private final SecretKeySpec encryptionKey;

    public RecordingStorage(StorageProperties properties) {
        this.properties = properties;
        this.encryptionKey = decodeEncryptionKey(properties.encryptionKey());
    }

    public String store(UUID recordingId, MultipartFile file) {
        requireFilesystemStorage();
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
            writeEncryptedAtomically(target, file.getBytes());
            return root.relativize(target).toString().replace(java.io.File.separatorChar, '/');
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "녹음 파일 저장에 실패했습니다.", "잠시 후 다시 시도하세요.");
        }
    }

    public StoredAudio load(String storageKey) {
        requireFilesystemStorage();
        if (!StringUtils.hasText(storageKey)) {
            throw new ExternalServiceUnavailableException("녹음 파일 저장 키가 없습니다.");
        }
        Path root = Path.of(properties.localRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일 저장 경로가 올바르지 않습니다.", "storage 키를 확인하세요.");
        }
        try {
            byte[] content = decryptIfEncrypted(Files.readAllBytes(target));
            String contentType = Files.probeContentType(target);
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
            }
            return new StoredAudio(content, target.getFileName().toString(), contentType);
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("녹음 파일을 외부 분석 provider에 전달할 수 없습니다.");
        }
    }

    public void delete(String storageKey) {
        requireFilesystemStorage();
        if (!StringUtils.hasText(storageKey)) {
            throw new ExternalServiceUnavailableException("녹음 파일 저장 키가 없습니다.");
        }
        Path root = Path.of(properties.localRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "파일 저장 경로가 올바르지 않습니다.", "storage 키를 확인하세요.");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("녹음 파일을 삭제할 수 없습니다.");
        }
    }

    /** Encrypts plaintext recordings left by older releases before the server accepts traffic. */
    public void encryptLegacyFiles() {
        requireFilesystemStorage();
        Path recordingsRoot = Path.of(properties.localRoot()).toAbsolutePath().normalize().resolve("recordings");
        if (!Files.exists(recordingsRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(recordingsRoot)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (path.getFileName().toString().endsWith(".tmp")) {
                    Files.deleteIfExists(path);
                    continue;
                }
                byte[] content = Files.readAllBytes(path);
                if (!isEncrypted(content)) {
                    writeEncryptedAtomically(path, content);
                } else {
                    // A wrong key must fail startup instead of going unnoticed until an AI request.
                    decryptIfEncrypted(content);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("기존 녹음 파일을 암호화하지 못해 안전하게 시작할 수 없습니다.", exception);
        }
    }

    public record StoredAudio(byte[] content, String filename, String contentType) {
    }

    private void requireFilesystemStorage() {
        if (!"local".equalsIgnoreCase(properties.type())
                && !"persistent-volume".equalsIgnoreCase(properties.type())) {
            throw new ExternalServiceUnavailableException(
                    "현재 파일 저장소 adapter가 local 또는 persistent-volume만 지원합니다.");
        }
    }

    private byte[] encrypt(byte[] content) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(content);
            return ByteBuffer.allocate(ENCRYPTED_FILE_MAGIC.length + nonce.length + encrypted.length)
                    .put(ENCRYPTED_FILE_MAGIC)
                    .put(nonce)
                    .put(encrypted)
                    .array();
        } catch (Exception exception) {
            throw new ExternalServiceUnavailableException("녹음 파일을 암호화할 수 없습니다.");
        }
    }

    private byte[] decryptIfEncrypted(byte[] content) {
        if (!isEncrypted(content)) {
            // Allows a rolling deployment to read an old file before the startup migration reaches it.
            return content;
        }
        if (content.length <= ENCRYPTED_FILE_MAGIC.length + GCM_NONCE_BYTES + 16) {
            throw new ExternalServiceUnavailableException("암호화된 녹음 파일 형식이 올바르지 않습니다.");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            buffer.position(ENCRYPTED_FILE_MAGIC.length);
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(encrypted);
        } catch (AEADBadTagException exception) {
            throw new ExternalServiceUnavailableException("녹음 파일을 복호화할 수 없습니다. 암호화 키 설정을 확인하세요.");
        } catch (Exception exception) {
            throw new ExternalServiceUnavailableException("녹음 파일을 복호화할 수 없습니다.");
        }
    }

    private boolean isEncrypted(byte[] content) {
        if (content.length < ENCRYPTED_FILE_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < ENCRYPTED_FILE_MAGIC.length; index++) {
            if (content[index] != ENCRYPTED_FILE_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private SecretKeySpec decodeEncryptionKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != 32) {
                throw new IllegalStateException("녹음 암호화 키는 32바이트여야 합니다.");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("녹음 암호화 키는 올바른 Base64 값이어야 합니다.", exception);
        }
    }

    private void writeEncryptedAtomically(Path target, byte[] plaintext) throws IOException {
        Files.createDirectories(target.getParent());
        setPermissions(target.getParent(), EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Path temporary = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, encrypt(plaintext), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            setPermissions(temporary, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(target, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX development platforms still keep files under the application-private directory.
        }
    }
}
