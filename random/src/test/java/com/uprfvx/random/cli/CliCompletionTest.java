package com.uprfvx.random.cli;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.romio.exceptions.RomIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliCompletionTest {
    @TempDir Path directory;

    private static GameRandomizer.Results result(Exception failure) throws Exception {
        Constructor<GameRandomizer.Results> ctor = GameRandomizer.Results.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        GameRandomizer.Results result = ctor.newInstance();
        Field error = GameRandomizer.Results.class.getDeclaredField("e");
        error.setAccessible(true);
        error.set(result, failure);
        return result;
    }

    @Test
    void failedResultsNeverPublishSuccessOrOverwriteAnExistingLog() throws Exception {
        for (Exception failure : new Exception[]{
                new RomIOException("Set Pickup Items to Unchanged"),
                new IOException("Synthetic save failure")}) {
            Path log = directory.resolve("synthetic-output.log");
            Files.writeString(log, "older log");
            PrintStream original = System.out;
            ByteArrayOutputStream messages = new ByteArrayOutputStream();
            try (PrintStream capture = new PrintStream(messages, true, StandardCharsets.UTF_8)) {
                System.setOut(capture);
                assertFalse(CliRandomizer.completeRandomization(result(failure),
                        directory.resolve("synthetic-output").toString(), new byte[]{42}, true));
            } finally {
                System.setOut(original);
            }
            assertEquals("older log", Files.readString(log));
            assertTrue(messages.toString(StandardCharsets.UTF_8).contains(failure.getMessage()));
            assertFalse(messages.toString(StandardCharsets.UTF_8).contains("Randomized successfully!"));
        }
    }

    @Test
    void successfulResultsKeepTheExistingSuccessAndLogConvention() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(messages, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertTrue(CliRandomizer.completeRandomization(result(null),
                    directory.resolve("synthetic-output").toString(), new byte[]{42}, true));
        } finally {
            System.setOut(original);
        }
        assertArrayEquals(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF, 42},
                Files.readAllBytes(directory.resolve("synthetic-output.log")));
        assertTrue(messages.toString(StandardCharsets.UTF_8).contains("Randomized successfully!"));
        assertFalse(messages.toString(StandardCharsets.UTF_8).contains("ERROR:"));
    }
}
