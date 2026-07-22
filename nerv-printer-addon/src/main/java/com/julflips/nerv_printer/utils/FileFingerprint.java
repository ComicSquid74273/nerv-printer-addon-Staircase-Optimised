package com.julflips.nerv_printer.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Streaming SHA-256 fingerprints for coordination inputs.
 */
public final class FileFingerprint {
    private FileFingerprint() {
    }

    public static String sha256(Path file) throws IOException {
        Path normalized = Objects.requireNonNull(file, "file")
            .toAbsolutePath()
            .normalize();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                "This Java runtime does not provide SHA-256.",
                impossible
            );
        }

        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(normalized)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(byte[] content) {
        Objects.requireNonNull(content, "content");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                "This Java runtime does not provide SHA-256.",
                impossible
            );
        }
        return HexFormat.of().formatHex(digest.digest(content));
    }

    public static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lowerHex = character >= 'a' && character <= 'f';
            if (!digit && !lowerHex) return false;
        }
        return true;
    }
}
