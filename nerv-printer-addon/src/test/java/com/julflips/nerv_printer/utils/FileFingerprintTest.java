package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileFingerprintTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void computesKnownSha256WithoutLoadingWholeFile() throws Exception {
        Path file = temporaryDirectory.resolve("input.nbt");
        Files.writeString(file, "abc");

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223"
                + "b00361a396177a9cb410ff61f20015ad",
            FileFingerprint.sha256(file)
        );
    }

    @Test
    void fingerprintsChangeWithContent() throws Exception {
        Path first = temporaryDirectory.resolve("same-name-a.nbt");
        Path second = temporaryDirectory.resolve("same-name-b.nbt");
        Files.writeString(first, "first");
        Files.writeString(second, "second");

        assertFalse(
            FileFingerprint.sha256(first).equals(
                FileFingerprint.sha256(second)
            )
        );
    }

    @Test
    void validatesCanonicalLowercaseSha256() {
        assertTrue(FileFingerprint.isSha256("0".repeat(64)));
        assertTrue(FileFingerprint.isSha256("abcdef".repeat(10) + "abcd"));
        assertFalse(FileFingerprint.isSha256("A".repeat(64)));
        assertFalse(FileFingerprint.isSha256("0".repeat(63)));
        assertFalse(FileFingerprint.isSha256("../" + "0".repeat(61)));
        assertFalse(FileFingerprint.isSha256(null));
    }

    @Test
    void hashesInMemoryProtocolMaterial() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223"
                + "b00361a396177a9cb410ff61f20015ad",
            FileFingerprint.sha256(
                "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        );
    }
}
