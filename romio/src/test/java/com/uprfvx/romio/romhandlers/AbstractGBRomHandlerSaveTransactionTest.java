package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.CannotWriteToLocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Self-created synthetic bytes only; actual shared GB saver and filesystem publication. */
class AbstractGBRomHandlerSaveTransactionTest {
    private static final byte[] OLD = {11, 22, 33, 44, 55};
    private static final byte[] PREPARED = preparedBytes();
    @TempDir Path directory;

    private static byte[] preparedBytes() {
        byte[] bytes = new byte[20003]; // Crosses multiple readback buffers; includes every byte value.
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 37 + 19);
        return bytes;
    }

    private Fixture fixture() throws IOException {
        Path input = directory.resolve("synthetic-input.bin");
        Files.write(input, OLD);
        Fixture fixture = new Fixture();
        fixture.loadRomFile(input.toString());
        fixture.rom = PREPARED.clone();
        return fixture;
    }

    private Path output(boolean existing) throws IOException {
        Path output = directory.resolve("synthetic-output.bin");
        if (existing) Files.write(output, OLD);
        return output;
    }

    private void assertPreserved(Fixture fixture, Path output, boolean existing) throws IOException {
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
        if (existing) assertArrayEquals(OLD, Files.readAllBytes(output));
        else assertFalse(Files.exists(output));
        assertNotNull(fixture.temporary);
        assertEquals(directory.toRealPath(), fixture.temporary.getParent());
        assertFalse(Files.exists(fixture.temporary));
        try (var entries = Files.list(directory)) {
            assertEquals(existing ? 2 : 1, entries.count());
        }
    }

    enum WriteFault { PREFIX, CLOSE, SHORT, LONG, CORRUPT, MISSING_READBACK }

    @ParameterizedTest(name = "{displayName} [{index}] {0}") @EnumSource(WriteFault.class)
    void writeOrReadbackFailureKeepsExistingOutputAndCleansOwnTemporary(WriteFault fault) throws IOException {
        Fixture fixture = fixture(); Path output = output(true); fixture.writeFault = fault;
        assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertTrue(fixture.closed);
        assertEquals(0, fixture.publications);
        assertPreserved(fixture, output, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}") @EnumSource(WriteFault.class)
    void writeOrReadbackFailureNeverPublishesNewOutput(WriteFault fault) throws IOException {
        Fixture fixture = fixture(); Path output = output(false); fixture.writeFault = fault;
        assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertTrue(fixture.closed);
        assertEquals(0, fixture.publications);
        assertPreserved(fixture, output, false);
    }

    enum MoveFault { UNSUPPORTED, DENIED, NULL_MESSAGE }

    @ParameterizedTest(name = "{displayName} [{index}] {0}") @EnumSource(MoveFault.class)
    void failedAtomicReplacePreservesExistingOutputWithNoFallback(MoveFault fault) throws IOException {
        Fixture fixture = fixture(); Path output = output(true); fixture.moveFault = fault;
        if (fault == MoveFault.DENIED) {
            assertThrows(CannotWriteToLocationException.class, () -> fixture.saveRomFile(output.toString(), 7));
        } else assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertEquals(1, fixture.publications);
        assertPreserved(fixture, output, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}") @EnumSource(MoveFault.class)
    void failedAtomicMoveNeverPublishesNewOutputWithNoFallback(MoveFault fault) throws IOException {
        Fixture fixture = fixture(); Path output = output(false); fixture.moveFault = fault;
        if (fault == MoveFault.DENIED) {
            assertThrows(CannotWriteToLocationException.class, () -> fixture.saveRomFile(output.toString(), 7));
        } else assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertEquals(1, fixture.publications);
        assertPreserved(fixture, output, false);
    }

    @Test void successfulReplacePublishesExactPreparedBytes() throws IOException {
        Fixture fixture = fixture(); Path output = output(true);
        assertTrue(fixture.saveRom(output.toString(), 19, false));
        assertEquals(List.of("species", "moves", "trainers", "palettes"), fixture.preparation);
        assertSuccess(fixture, output);
    }

    @Test void successfulNewOutputPublishesExactPreparedBytes() throws IOException {
        Fixture fixture = fixture(); Path output = output(false);
        assertTrue(fixture.saveRom(output.toString(), 19, false));
        assertSuccess(fixture, output);
    }

    private void assertSuccess(Fixture fixture, Path output) throws IOException {
        assertArrayEquals(PREPARED, Files.readAllBytes(output));
        assertEquals(PREPARED.length, Files.size(output));
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
        assertTrue(fixture.closed);
        assertEquals(1, fixture.publications);
        assertFalse(Files.exists(fixture.temporary));
        try (var entries = Files.list(directory)) { assertEquals(2, entries.count()); }
    }

    @Test void retryAfterPartialFailureUsesUniqueTemporaryAndPreservesUnrelatedSibling() throws IOException {
        Fixture fixture = fixture(); Path output = output(true);
        Path unrelated = directory.resolve(".uprfvx-save-unrelated.tmp"); Files.write(unrelated, OLD);
        fixture.writeFault = WriteFault.PREFIX;
        assertFalse(fixture.saveRomFile(output.toString(), 7)); Path first = fixture.temporary;
        assertArrayEquals(OLD, Files.readAllBytes(output)); assertFalse(Files.exists(first));
        fixture.writeFault = null;
        assertTrue(fixture.saveRomFile(output.toString(), 7));
        assertNotEquals(first, fixture.temporary);
        assertArrayEquals(PREPARED, Files.readAllBytes(output));
        assertArrayEquals(OLD, Files.readAllBytes(unrelated));
    }

    @Test void sameInputAndDotDotAliasAreRejectedBeforeOpeningTemporary() throws IOException {
        Fixture fixture = fixture(); Path input = Path.of(fixture.loadedFilename());
        Path child = Files.createDirectory(directory.resolve("child"));
        for (Path alias : List.of(input, directory.resolve(".").resolve(input.getFileName()),
                child.resolve("..").resolve(input.getFileName()), Path.of("").toAbsolutePath().relativize(input))) {
            assertFalse(fixture.saveRomFile(alias.toString(), 7));
            assertNull(fixture.temporary);
            assertArrayEquals(OLD, Files.readAllBytes(input));
        }
    }

    @Test void hardlinkInputAliasIsRejectedBeforeOpeningTemporary() throws IOException {
        Fixture fixture = fixture(); Path alias = directory.resolve("hardlink.bin");
        Files.createLink(alias, Path.of(fixture.loadedFilename()));
        assertFalse(fixture.saveRomFile(alias.toString(), 7)); assertNull(fixture.temporary);
        assertArrayEquals(OLD, Files.readAllBytes(alias));
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
    }

    @Test void symlinkInputAliasIsRejectedBeforeOpeningTemporary() throws IOException {
        Fixture fixture = fixture(); Path alias = directory.resolve("symlink.bin");
        Files.createSymbolicLink(alias, Path.of(fixture.loadedFilename()));
        assertFalse(fixture.saveRomFile(alias.toString(), 7)); assertNull(fixture.temporary);
        assertTrue(Files.isSymbolicLink(alias));
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
    }

    @Test void symlinkDirectoryInputAliasIsRejectedBeforeOpeningTemporary() throws IOException {
        Fixture fixture = fixture(); Path alias = directory.resolve("directory-link");
        Files.createSymbolicLink(alias, directory);
        assertFalse(fixture.saveRomFile(alias.resolve("synthetic-input.bin").toString(), 7));
        assertNull(fixture.temporary);
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
    }

    @Test void unrelatedAndDanglingSymlinkTargetsAreRejectedWithoutFollowingOrReplacingThem() throws IOException {
        Fixture fixture = fixture(); Path referent = directory.resolve("referent.bin");
        Files.write(referent, OLD);
        Path alias = directory.resolve("output-link"); Files.createSymbolicLink(alias, referent);
        assertFalse(fixture.saveRomFile(alias.toString(), 7)); assertNull(fixture.temporary);
        assertArrayEquals(OLD, Files.readAllBytes(referent)); assertTrue(Files.isSymbolicLink(alias));
        Files.delete(referent);
        assertFalse(fixture.saveRomFile(alias.toString(), 7)); assertNull(fixture.temporary);
        assertTrue(Files.isSymbolicLink(alias)); assertFalse(Files.exists(referent));
    }

    @Test void invalidDestinationsAreRejectedBeforeOpeningTemporary() throws IOException {
        Fixture fixture = fixture();
        for (String target : List.of(directory.toString(), directory.resolve("missing-parent/output.bin").toString(),
                directory.getRoot().toString(), directory + "/bad\u0000name")) {
            assertFalse(fixture.saveRomFile(target, 7)); assertNull(fixture.temporary);
        }
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
    }

    @Test void missingInputIdentityFailsClosed() throws IOException {
        Fixture fixture = fixture(); Path output = output(true);
        Files.delete(Path.of(fixture.loadedFilename()));
        assertFalse(fixture.saveRomFile(output.toString(), 7)); assertNull(fixture.temporary);
        assertArrayEquals(OLD, Files.readAllBytes(output));
    }

    @Test void loadedSymlinkAndRelativeInputNamesStillRejectTheirRealOutputIdentity() throws IOException {
        Fixture fixture = fixture(); Path input = Path.of(fixture.loadedFilename());
        Path link = directory.resolve("loaded-link.bin"); Files.createSymbolicLink(link, input);
        for (Path loaded : List.of(link, Path.of("").toAbsolutePath().relativize(input))) {
            fixture.loadRomFile(loaded.toString()); fixture.rom = PREPARED.clone();
            assertFalse(fixture.saveRomFile(input.toString(), 7)); assertNull(fixture.temporary);
            assertArrayEquals(OLD, Files.readAllBytes(input));
        }
    }

    @Test void unloadedHandlerCannotPublishWithoutInputIdentity() throws IOException {
        Fixture fixture = new Fixture(); fixture.rom = PREPARED.clone(); Path output = output(true);
        assertFalse(fixture.saveRomFile(output.toString(), 7)); assertNull(fixture.temporary);
        assertArrayEquals(OLD, Files.readAllBytes(output));
    }

    @Test void symlinkIntroducedAfterWritingIsRejectedBeforePublication() throws IOException {
        Fixture fixture = fixture(); Path output = output(false);
        fixture.linkBeforePublish = output;
        assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertEquals(0, fixture.publications); assertFalse(Files.exists(fixture.temporary));
        assertTrue(Files.isSymbolicLink(output));
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
    }

    @Test void failedPreparationPreservesExistingOutputAndNeverCreatesTemporary() throws IOException {
        Fixture fixture = fixture(); Path output = output(true); fixture.failPreparation = true;
        assertThrows(IllegalStateException.class, () -> fixture.saveRom(output.toString(), 7, false));
        assertEquals(List.of("species", "moves"), fixture.preparation);
        assertNull(fixture.temporary); assertEquals(0, fixture.publications);
        assertArrayEquals(OLD, Files.readAllBytes(output));
    }

    @Test void failedPreparationNeverPublishesNewOutput() throws IOException {
        Fixture fixture = fixture(); Path output = output(false); fixture.failPreparation = true;
        assertThrows(IllegalStateException.class, () -> fixture.saveRom(output.toString(), 7, false));
        assertNull(fixture.temporary); assertFalse(Files.exists(output));
    }

    @Test void cleanupFailureNeverDeletesExistingOutputOrInput() throws IOException {
        Fixture fixture = fixture(); Path output = output(true);
        fixture.writeFault = WriteFault.PREFIX; fixture.failCleanup = true;
        assertFalse(fixture.saveRomFile(output.toString(), 7));
        assertTrue(Files.exists(fixture.temporary));
        assertArrayEquals(OLD, Files.readAllBytes(output));
        assertArrayEquals(OLD, Files.readAllBytes(Path.of(fixture.loadedFilename())));
        Files.delete(fixture.temporary); // Only the test-owned leftover; cleanup failure cannot promise removal.
    }

    @Test void accessDeniedMessageRetainsExistingExceptionContractAndCleansTemporary() throws IOException {
        Fixture fixture = fixture(); Path output = output(true); fixture.deniedMessage = true;
        assertThrows(CannotWriteToLocationException.class, () -> fixture.saveRomFile(output.toString(), 7));
        assertPreserved(fixture, output, true);
    }

    @Test void nonCfruGen1SharedSaverControlPublishesExactBytes() throws IOException {
        class Control extends Gen1RomHandler {
            @Override public boolean detectRom(byte[] bytes) { return true; }
        }
        Control control = new Control(); Path input = directory.resolve("gen1-synthetic.bin");
        Files.write(input, OLD); control.loadRomFile(input.toString()); control.rom = PREPARED.clone();
        Path output = output(true); assertTrue(control.saveRomFile(output.toString(), 7));
        assertArrayEquals(PREPARED, Files.readAllBytes(output)); assertArrayEquals(OLD, Files.readAllBytes(input));
    }

    @Test void nonCfruGen2SharedSaverControlPublishesExactBytes() throws IOException {
        class Control extends Gen2RomHandler {
            @Override public boolean detectRom(byte[] bytes) { return true; }
        }
        Control control = new Control(); Path input = directory.resolve("gen2-synthetic.bin");
        Files.write(input, OLD); control.loadRomFile(input.toString()); control.rom = PREPARED.clone();
        Path output = output(false); assertTrue(control.saveRomFile(output.toString(), 7));
        assertArrayEquals(PREPARED, Files.readAllBytes(output)); assertArrayEquals(OLD, Files.readAllBytes(input));
    }

    private static class Fixture extends Gen3RomHandler {
        Path temporary, linkBeforePublish;
        WriteFault writeFault;
        MoveFault moveFault;
        boolean closed, failPreparation, failCleanup, deniedMessage;
        int publications;
        List<String> preparation = new java.util.ArrayList<>();
        @Override public boolean detectRom(byte[] bytes) { return true; }
        @Override public void saveSpeciesStats() { preparation.add("species"); }
        @Override public void saveMoves() {
            preparation.add("moves");
            if (failPreparation) throw new IllegalStateException("synthetic preparation failure");
        }
        @Override public void saveTrainers() { preparation.add("trainers"); }
        @Override public void savePokemonPalettes() { preparation.add("palettes"); }
        @Override protected OutputStream openSaveOutput(Path path) throws IOException {
            temporary = path;
            if (deniedMessage) throw new IOException("Access is denied");
            return new FilterOutputStream(super.openSaveOutput(path)) {
                @Override public void write(byte[] bytes) throws IOException {
                    if (writeFault == WriteFault.PREFIX) {
                        out.write(bytes, 0, 3); throw new IOException(); // Deliberately no message.
                    }
                    if (writeFault == WriteFault.SHORT) out.write(bytes, 0, bytes.length - 1);
                    else if (writeFault == WriteFault.LONG) { out.write(bytes); out.write(0); }
                    else if (writeFault == WriteFault.CORRUPT) {
                        byte[] corrupt = bytes.clone(); corrupt[9000] ^= 1; out.write(corrupt);
                    } else out.write(bytes);
                }
                @Override public void close() throws IOException {
                    super.close(); closed = true;
                    if (writeFault == WriteFault.CLOSE) throw new IOException();
                    if (writeFault == WriteFault.MISSING_READBACK) Files.delete(path);
                    if (linkBeforePublish != null)
                        Files.createSymbolicLink(linkBeforePublish, Path.of(loadedFilename()));
                }
            };
        }
        @Override protected void publishSaveOutput(Path temporary, Path target) throws IOException {
            publications++;
            assertArrayEquals(PREPARED, Files.readAllBytes(temporary));
            if (moveFault == MoveFault.UNSUPPORTED)
                throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "synthetic");
            if (moveFault == MoveFault.DENIED) throw new AccessDeniedException(target.toString());
            if (moveFault == MoveFault.NULL_MESSAGE) throw new IOException();
            super.publishSaveOutput(temporary, target);
        }
        @Override protected void removeSaveTemporary(Path temporary) throws IOException {
            assertEquals(this.temporary, temporary);
            if (failCleanup) throw new IOException("synthetic cleanup failure");
            super.removeSaveTemporary(temporary);
        }
    }
}
