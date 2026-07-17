package com.julflips.nerv_printer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UtilsMapFileSelectionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void failedMoveModeDoesNotSelectTheSameNbtTwice() throws Exception {
        Files.writeString(temporaryDirectory.resolve("a.nbt"), "a");
        Files.writeString(temporaryDirectory.resolve("b.nbt"), "b");
        ArrayList<File> started = new ArrayList<>();

        File first = Utils.getNextMapFile(
            temporaryDirectory.toFile(),
            started,
            true
        );
        File second = Utils.getNextMapFile(
            temporaryDirectory.toFile(),
            started,
            true
        );

        assertEquals("a.nbt", first.getName());
        assertEquals("b.nbt", second.getName());
        assertNotEquals(first, second);
        assertNull(
            Utils.getNextMapFile(
                temporaryDirectory.toFile(),
                started,
                true
            )
        );
    }
}
