package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.GFXFunctions;
import com.uprfvx.romio.RomFunctions;
import com.uprfvx.romio.constants.GBConstants;
import com.uprfvx.romio.exceptions.CannotWriteToLocationException;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gbspace.FreedSpace;
import com.uprfvx.romio.romhandlers.romentries.AbstractGBRomEntry;
import com.uprfvx.romio.romhandlers.romentries.RomEntry;
import filefunctions.FileFunctions;
import filefunctions.IOFunctions;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * An abstract base class for GB/GBA {@link RomHandler}s, which standardises common GB(A) functions.
 */
public abstract class AbstractGBRomHandler extends AbstractRomHandler {

    protected static final String TEXT_TABLES_PATH = "/com/uprfvx/romio/texttables";

    protected byte[] rom;
    protected byte[] originalRom;
    private String loadedFileName;
    private long actualCRC32;

    protected boolean tmsReusable;

    @Override
    public boolean loadRom(String filename) {
        loadRomFile(filename);
        midLoadingSetUp();
        loadGameData();
        estimateEvolutionLevels();
        return true;
    }

    protected void loadRomFile(String filename) {
        byte[] loaded = loadFile(filename);
        if (!detectRom(loaded)) {
            throw new RomIOException("Could not detect ROM.");
        }
        this.rom = loaded;
        this.originalRom = new byte[rom.length];
        System.arraycopy(rom, 0, originalRom, 0, rom.length);
        loadedFileName = filename;
        this.actualCRC32 = IOFunctions.getCRC32(rom);
    }

    /**
     * Sets up various stuff which needs to be done after the ROM file has been loaded, but which is needed for loading
     * game data like {@link Species} and {@link Trainer}s. E.g. the {@link RomEntry} and text tables.
     * Expected to be overrided.
     */
    protected void midLoadingSetUp() {
        initRomEntry();
        initTextTables();
    }

    protected abstract void initRomEntry();

    protected void addRelativeOffsetToRomEntry(String newKey, String baseKey, int offset) {
        int baseOffset = getRomEntry().getIntValue(baseKey);
        if (baseOffset != 0 && getRomEntry().getIntValue(newKey) == 0) {
            getRomEntry().putIntValue(newKey, baseOffset + offset);
        }
    }

    protected abstract void initTextTables();

    /**
     * Loads the (randomizable) game data, i.e. stuff like the gettable lists of {@link Species}, {@link Move}s,
     * and {@link Trainer}s.
     */
    protected void loadGameData() {
        loadItems();
        loadSpeciesStats();
        loadEvolutions();
        loadMoves();
        loadPokemonPalettes();
        loadTrainers();
    }

    // The below are public inside this abstract class for easier testing...
    // but that's not very nice. Abstract classes are not interfaces!
    // TODO: change this to not be as ugly
    public abstract void loadEvolutions();

    public abstract void loadItems();

    @Override
    public String loadedFilename() {
        return loadedFileName;
    }

    @Override
    public boolean saveRomFile(String filename, long seed) {
        Path temporary = null;
        try {
            Path target = checkedSaveTarget(Paths.get(filename));
            temporary = Files.createTempFile(target.getParent(), ".uprfvx-save-", ".tmp");
            try (OutputStream output = openSaveOutput(temporary)) {
                output.write(rom);
            }
            verifySaveOutput(temporary);
            // Recheck identity after writing, before publishing to the final name.
            checkedSaveTarget(target);
            publishSaveOutput(temporary, target);
            temporary = null;
            return true;
        } catch (IOException ex) {
            if (ex instanceof AccessDeniedException
                    || (ex.getMessage() != null && ex.getMessage().contains("Access is denied"))) {
                throw new CannotWriteToLocationException("The randomizer cannot write to this location: " + filename);
            }
            return false;
        } catch (InvalidPathException ex) {
            return false;
        } finally {
            if (temporary != null) {
                try {
                    removeSaveTemporary(temporary);
                } catch (IOException ignored) {
                    // Preserve the save failure contract; never remove the destination as cleanup.
                }
            }
        }
    }

    private Path checkedSaveTarget(Path requested) throws IOException {
        Path absolute = requested.toAbsolutePath();
        if (absolute.getFileName() == null || loadedFilename() == null) {
            throw new IOException("Invalid save destination or missing input identity");
        }
        // Resolve the directory first, so parent-directory aliases cannot hide input identity.
        Path target = absolute.getParent().toRealPath().resolve(absolute.getFileName());
        Path input = Paths.get(loadedFilename()).toRealPath();
        if (target.equals(input)) {
            throw new IOException("Save destination is the loaded input");
        }
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException ex) {
            return target;
        }
        // Reject even dangling symlinks, directories and special files; do not follow their targets.
        if (!attributes.isRegularFile() || Files.isSameFile(input, target)) {
            throw new IOException("Unsafe save destination");
        }
        return target;
    }

    private void verifySaveOutput(Path temporary) throws IOException {
        if (Files.size(temporary) != rom.length) {
            throw new IOException("Incomplete save output");
        }
        try (InputStream input = Files.newInputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int offset = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > rom.length - offset
                        || !Arrays.equals(buffer, 0, count, rom, offset, offset + count)) {
                    throw new IOException("Save output readback mismatch");
                }
                offset += count;
            }
            if (offset != rom.length) {
                throw new IOException("Incomplete save output readback");
            }
        }
    }

    // Narrow I/O seams for deterministic transaction fault tests. Production never falls back to a plain move.
    protected OutputStream openSaveOutput(Path temporary) throws IOException {
        return Files.newOutputStream(temporary);
    }

    protected void publishSaveOutput(Path temporary, Path target) throws IOException {
        // ATOMIC_MOVE makes replacement provider-specific: unsupported replacement fails closed.
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    protected void removeSaveTemporary(Path temporary) throws IOException {
        Files.deleteIfExists(temporary);
    }

    @Override
    public boolean saveRomDirectory(String filename) {
        // do nothing, because GB games don't really have a concept of a filesystem
        return true;
    }

    @Override
    public boolean hasGameUpdateLoaded() {
        return false;
    }

    @Override
    public boolean loadGameUpdate(String filename) {
        // do nothing, as GB games don't have external game updates
        return true;
    }

    @Override
    public void removeGameUpdate() {
        // do nothing, as GB games don't have external game updates
    }

    @Override
    public String getGameUpdateVersion() {
        // do nothing, as GB games don't have external game updates
        return null;
    }

    @Override
    public void printRomDiagnostics(PrintStream logStream) {
        Path p = Paths.get(loadedFileName);
        logStream.println("File name: " + p.getFileName().toString());
        long crc = IOFunctions.getCRC32(originalRom);
        logStream.println("Original ROM CRC32: " + String.format("%08X", crc));
    }

    @Override
    public boolean hasPhysicalSpecialSplit() {
        // Default value for Gen1-Gen3.
        // Handlers can override again in case of ROM hacks etc.
        return false;
    }

    @Override
    public boolean hasPokemonPaletteSupport() {
        return true;
    }

    public abstract boolean detectRom(byte[] rom);


    protected static byte[] loadFile(String filename) {
        try {
            return FileFunctions.readFileFullyIntoBuffer(filename);
        } catch (IOException ex) {
            throw new RomIOException(ex);
        }
    }

    protected static byte[] loadFilePartial(String filename, int maxBytes) {
        try {
            File fh = new File(filename);
            if (!fh.exists() || !fh.isFile() || !fh.canRead()) {
                return new byte[0];
            }
            long fileSize = fh.length();
            if (fileSize > Integer.MAX_VALUE) {
                return new byte[0];
            }
            FileInputStream fis = new FileInputStream(filename);
            byte[] file = FileFunctions.readFullyIntoBuffer(fis, Math.min((int) fileSize, maxBytes));
            fis.close();
            return file;
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    protected void readByteIntoFlags(boolean[] flags, int offsetIntoFlags, int offsetIntoROM) {
        int thisByte = rom[offsetIntoROM] & 0xFF;
        for (int i = 0; i < 8 && (i + offsetIntoFlags) < flags.length; i++) {
            flags[offsetIntoFlags + i] = ((thisByte >> i) & 0x01) == 0x01;
        }
    }

    protected byte getByteFromFlags(boolean[] flags, int offsetIntoFlags) {
        int thisByte = 0;
        for (int i = 0; i < 8 && (i + offsetIntoFlags) < flags.length; i++) {
            thisByte |= (flags[offsetIntoFlags + i] ? 1 : 0) << i;
        }
        return (byte) thisByte;
    }

    /**
     * Reads a nybble at <code>offset</code> in ROM. If high is true, reads the high nybble.
     */
    protected int readNybble(int offset, boolean high) {
        int b = rom[offset] & 0xFF;
        if (high) {
            b >>= 4;
        }
        return b & 0xF;
    }

    /**
     * Writes a nybble to <code>offset</code> in ROM. If high is true, writes the high nybble.
     */
    protected void writeNybble(int offset, boolean high, int value) {
        value &= 0xF;
        int mask = 0xF;
        if (high) {
            value <<= 4;
        } else {
            mask <<= 4;
        }
        rom[offset] = (byte) ((rom[offset] & mask) | value);
    }

    protected int readWord(int offset) {
        return readWord(rom, offset);
    }
    
	protected final void writeByte(int offset, byte value) {
		rom[offset] = value;
	}

	protected final void writeBytes(int offset, byte[] values) {
        if (offset == 0) {
            throw new IllegalArgumentException("Not allowed to write at offset 0 of the ROM.");
        }
        writeBytes(rom, offset, values);
	}

	protected final void writeBytes(byte[] data, int offset, byte[] values) {
		System.arraycopy(values, 0, data, offset, values.length);
	}

	protected int readWord(byte[] data, int offset) {
		return (data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8);
	}

	protected void writeWord(int offset, int value) {
		writeWord(rom, offset, value);
	}

	protected void writeWord(byte[] data, int offset, int value) {
		data[offset] = (byte) (value % 0x100);
		data[offset + 1] = (byte) ((value / 0x100) % 0x100);
	}

	protected boolean matches(byte[] data, int offset, byte[] needle) {
		for (int i = 0; i < needle.length; i++) {
			if (offset + i >= data.length) {
				return false;
			}
			if (data[offset + i] != needle[i]) {
				return false;
			}
		}
		return true;
	}

    /**
     * Returns the length of some data, which is ended by a single-byte terminator.
     * Counts the terminator towards the length.
     */
    protected int lengthOfDataWithTerminatorAt(int offset, byte terminator) {
        return lengthOfDataWithTerminatorsAt(offset, terminator, 1);
    }

    /**
     * Returns the length of some data using single-byte terminators.
     * Counts the terminators towards the length.
     */
    protected int lengthOfDataWithTerminatorsAt(int offset, byte terminator, int terminatorAmount) {
        int length = 0;
        int terminatorCount = 0;
        do {
            if (rom[offset + length] == terminator) terminatorCount++;
            length++;
        } while (terminatorCount < terminatorAmount);
        return length;
    }

    /**
     * Returns the length of some data, which is ended by a multibyte terminator.
     * Counts the terminator towards the length.
     */
    protected int lengthOfDataWithTerminatorAt(int offset, byte[] terminator) {
        int startOfTerminatorOffset = RomFunctions.searchForFirst(rom, offset, terminator);
        return startOfTerminatorOffset - offset + terminator.length;
    }

    protected abstract int readPointer(int offset);

    protected abstract void writePointer(int offset, int pointer);

    protected enum DataRewritePhase {
        PAYLOAD_WRITTEN, BEFORE_PUBLICATION, POINTER_WRITTEN, BANK_WRITTEN, BEFORE_RELEASE, OLD_RELEASED
    }

    /** Narrow fault seams; rollback bypasses these writers and restores captured bytes directly. */
    protected void writeRepointPayload(int offset, byte[] data) {
        writeBytes(offset, data);
    }

    protected void dataRewriteCheckpoint(DataRewritePhase phase, int offset) { }

    protected class DataRewriter<E> {
        protected Function<Integer, Integer> pointerReader = AbstractGBRomHandler.this::readPointer;
        protected BiConsumer<Integer, Integer> pointerWriter = AbstractGBRomHandler.this::writePointer;
        private boolean longAlignAdresses = true;
        protected int pointerSize = GBConstants.longSize;
        private IntPredicate pointerTargetValidator = target -> true;

        public boolean isLongAlignAdresses() { return longAlignAdresses; }
        public void setLongAlignAdresses(boolean value) { longAlignAdresses = value; }
        public void setPointerReader(Function<Integer, Integer> reader) { pointerReader = reader; }
        public void setPointerWriter(BiConsumer<Integer, Integer> writer) { pointerWriter = writer; }

        /** Custom codecs must declare their complete byte footprint and representable targets. */
        public void setPointerFormat(int size, IntPredicate targetValidator) {
            if (size < 1 || targetValidator == null) throw new IllegalArgumentException("Invalid pointer format.");
            pointerSize = size;
            pointerTargetValidator = targetValidator;
        }

        protected int[] bankOwnerOffsets() { return new int[0]; }
        protected void validateOriginalPointer(int offset) { }
        protected void validateOriginalPayload(int offset, int length) { }
        protected void validatePointerTarget(int offset, int target) {
            checkRange(target, 1);
            if (!pointerTargetValidator.test(target)) throw new RomIOException("Unrepresentable pointer target.");
        }
        protected int readPublishedPointer(int offset, int target) { return pointerReader.apply(offset); }
        protected void publishBanks(int target) { }
        protected int reserve(int pointerOffset, int length) {
            return findAndUnfreeSpace(length, longAlignAdresses);
        }

        private void checkRange(int offset, int length) {
            if (offset <= 0 || length < 1 || offset > rom.length - length) {
                throw new RomIOException("Invalid DataRewriter byte range.");
            }
        }
        private boolean overlaps(int a, int al, int b, int bl) {
            return (long) a < (long) b + bl && (long) b < (long) a + al;
        }
        private void addOwner(List<Integer> offsets, List<byte[]> bytes, int offset, int length) {
            checkRange(offset, length);
            for (int i = 0; i < offsets.size(); i++) {
                if (offset == offsets.get(i) && length == bytes.get(i).length) return;
                if (overlaps(offset, length, offsets.get(i), bytes.get(i).length)) {
                    throw new RomIOException("Overlapping DataRewriter owners.");
                }
            }
            offsets.add(offset);
            bytes.add(Arrays.copyOfRange(rom, offset, offset + length));
        }

        public void rewriteData(int pointerOffset, E e, Function<E, byte[]> newDataFunction,
                                Function<Integer, Integer> lengthOfOldFunction) {
            rewriteData(pointerOffset, e, new int[0], newDataFunction, lengthOfOldFunction);
        }

        public void rewriteData(int pointerOffset, E e, int[] secondaryPointerOffsets,
                                Function<E, byte[]> newDataFunction, Function<Integer, Integer> lengthOfOldFunction) {
            byte[] data = newDataFunction.apply(e).clone();
            if (data.length < 1 || data.length > rom.length - GBConstants.longSize)
                throw new RomIOException("Invalid DataRewriter payload size.");
            List<Integer> pointers = new ArrayList<>();
            pointers.add(pointerOffset);
            for (int offset : secondaryPointerOffsets) if (!pointers.contains(offset)) pointers.add(offset);
            List<Integer> owners = new ArrayList<>();
            List<byte[]> ownerBytes = new ArrayList<>();
            for (int offset : pointers) {
                addOwner(owners, ownerBytes, offset, pointerSize);
                validateOriginalPointer(offset);
            }
            for (int offset : bankOwnerOffsets()) addOwner(owners, ownerBytes, offset, 1);
            int oldOffset = pointerReader.apply(pointerOffset);
            checkRange(oldOffset, 1);
            int oldLength = lengthOfOldFunction.apply(oldOffset);
            checkRange(oldOffset, oldLength);
            validateOriginalPayload(oldOffset, oldLength);
            for (int offset : pointers) {
                if (pointerReader.apply(offset) != oldOffset) throw new RomIOException("Divergent DataRewriter pointer.");
            }
            for (int i = 0; i < owners.size(); i++) {
                if (overlaps(oldOffset, oldLength, owners.get(i), ownerBytes.get(i).length)) {
                    throw new RomIOException("Payload overlaps a pointer or bank owner.");
                }
            }
            byte[] oldBytes = Arrays.copyOfRange(rom, oldOffset, oldOffset + oldLength);
            FreedSpace allocator = getFreedSpace();
            FreedSpace.Snapshot ledger = allocator.snapshot();
            int newOffset = -1;
            byte[] replacementBytes = null;
            boolean oldRegistered = false;
            try {
                try {
                    newOffset = reserve(pointerOffset, data.length);
                } catch (RomIOException exhausted) {
                    // Model old-range reuse on scratch bytes only. The original remains reachable.
                    allocator.restore(ledger);
                    byte[] live = rom;
                    byte[] planning = live.clone();
                    Arrays.fill(planning, oldOffset, oldOffset + oldLength, getFreeSpaceByte());
                    allocator.free(oldOffset, oldLength);
                    oldRegistered = true;
                    try {
                        rom = planning;
                        newOffset = reserve(pointerOffset, data.length);
                    } finally {
                        rom = live;
                    }
                }
                checkRange(newOffset, data.length);
                if (!oldRegistered && overlaps(newOffset, data.length, oldOffset, oldLength)) {
                    throw new RomIOException("Allocation overlaps the live original payload.");
                }
                for (int i = 0; i < owners.size(); i++) {
                    if (overlaps(newOffset, data.length, owners.get(i), ownerBytes.get(i).length)) {
                        throw new RomIOException("Allocation overlaps pointer or bank owner.");
                    }
                }
                for (int offset : pointers) validatePointerTarget(offset, newOffset);
                replacementBytes = Arrays.copyOfRange(rom, newOffset, newOffset + data.length);
                writeRepointPayload(newOffset, data);
                dataRewriteCheckpoint(DataRewritePhase.PAYLOAD_WRITTEN, newOffset);
                verifyPayload(newOffset, data);
                dataRewriteCheckpoint(DataRewritePhase.BEFORE_PUBLICATION, newOffset);
                verifyPayload(newOffset, data);
                for (int offset : pointers) {
                    pointerWriter.accept(offset, newOffset);
                    dataRewriteCheckpoint(DataRewritePhase.POINTER_WRITTEN, offset);
                }
                publishBanks(newOffset);
                for (int offset : pointers) {
                    if (readPublishedPointer(offset, newOffset) != newOffset) {
                        throw new RomIOException("DataRewriter pointer readback mismatch.");
                    }
                }
                verifyPayload(newOffset, data);
                dataRewriteCheckpoint(DataRewritePhase.BEFORE_RELEASE, oldOffset);
                if (oldRegistered) {
                    // Unreserved old fragments already belong to the ledger. Never free them twice.
                    for (int i = oldOffset; i < oldOffset + oldLength; i++) {
                        if (i < newOffset || i >= newOffset + data.length) writeByte(i, getFreeSpaceByte());
                    }
                } else {
                    freeSpace(oldOffset, oldLength);
                }
                dataRewriteCheckpoint(DataRewritePhase.OLD_RELEASED, oldOffset);
                verifyPayload(newOffset, data);
            } catch (RuntimeException failure) {
                if (replacementBytes != null) System.arraycopy(replacementBytes, 0, rom, newOffset, replacementBytes.length);
                System.arraycopy(oldBytes, 0, rom, oldOffset, oldBytes.length);
                for (int i = 0; i < owners.size(); i++) {
                    System.arraycopy(ownerBytes.get(i), 0, rom, owners.get(i), ownerBytes.get(i).length);
                }
                allocator.restore(ledger);
                throw failure;
            }
        }

        private void verifyPayload(int offset, byte[] expected) {
            for (int i = 0; i < expected.length; i++) {
                if (rom[offset + i] != expected[i]) throw new RomIOException("DataRewriter payload readback mismatch.");
            }
        }
    }

	protected void freeSpace(int offset, int length) {
		if (length < 1) {
			throw new IllegalArgumentException("length must be at least 1.");
		}
		for (int i = 0; i < length; i++) {
			writeByte(offset + i, getFreeSpaceByte());
		}
        getFreedSpace().free(offset, length);
	}

    /**
     * Both end points included.
     */
    protected void freeSpaceBetween(int start, int end) {
        freeSpace(start, end - start + 1);
    }

	protected int findAndUnfreeSpace(int length) {
        return findAndUnfreeSpace(length, true);
	}

    /**
     * At least Pokémon palettes in R/S/FR/LG need to be long aligned,
     * probably more types of data than that though. If they are not long aligned,
     * the games soft-lock and/or crash, which isn't fun to debug.
     * If you aren't very sure about not needing to long-align, don't use this method directly.
     *
     * @param length The number of bytes to find space for.
     * @param longAligned Does the found adress need to be long-aligned?
     */
    protected int findAndUnfreeSpace(int length, boolean longAligned) {
        int foundOffset;
        length += longAligned ? GBConstants.longSize : 0;
        do {
            foundOffset = getFreedSpace().findAndUnfree(length);
        } while (isRomSpaceUsed(foundOffset, length));

        if (foundOffset == -1) {
            throw new RomIOException("ROM full. Can't find " + length + " free bytes anywhere.");
        }

        if (longAligned) {
            int shift = GBConstants.longSize - (foundOffset % GBConstants.longSize);
            shift = shift == GBConstants.longSize ? 0 : shift;
            freeSpace(foundOffset + length - (4 - shift), 4 - shift);
            foundOffset += shift;
        }
        return foundOffset;
    }

	protected boolean isRomSpaceUsed(int offset, int length) {
		if (offset < 0)
			return false;
		// manual check if the space is still unused, because
		// the deprecated RomFunctions methods (or any future badly written code)
		// can in theory use the freed spaces "by accident".
		for (int i = 0; i < length; i++) {
			if (rom[offset + i] != getFreeSpaceByte()) {
				return true;
			}
		}
		return false;
	}

    protected abstract FreedSpace getFreedSpace();

	protected abstract byte getFreeSpaceByte();

    @Override
    public ExpCurve[] getExpCurves() {
        return new ExpCurve[]{ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW};
    }

    @Override
    public boolean isTMsReusable() {
        return tmsReusable;
    }

    @Override
    public boolean canTMsBeHeld() { return true; }

    @Override
    public boolean hasTypeEffectivenessSupport() {
        return true;
    }

    @Override
    public boolean hasGuaranteedWildHeldItems() {
        return false; // no held items in Gen 1 and Gen 2 has no guaranteed ones still
    }

    @Override
	public List<BufferedImage> getAllPokemonImages() {
		List<BufferedImage> bims = new ArrayList<>();
		for (Species pk : getSpeciesSet()) {
			bims.add(createPokemonImageGetter(pk).getFull());
		}
		return bims;
	}

    @Override
    public boolean hasPokemonImageGetter() {
        return true;
    }

    public abstract static class GBPokemonImageGetter extends PokemonImageGetter {

        public GBPokemonImageGetter(Species pk) {
            super(pk);
        }

        @Override
        public BufferedImage getFull() {
            setIncludePalette(true);

            BufferedImage frontNormal = get();
            BufferedImage backNormal = setBack(true).get();
            BufferedImage backShiny = setShiny(true).get();
            BufferedImage frontShiny = setBack(false).get();

            return GFXFunctions.stitchToGrid(new BufferedImage[][] { { frontNormal, backNormal }, { frontShiny, backShiny } });
        }
    }

    @Override
    public abstract AbstractGBRomEntry getRomEntry();

    @Override
    public boolean isRomValid(PrintStream logStream) {
        if (logStream != null) {
            System.out.println("Checking CRC32 validity");
            System.out.println("CRC32, expected:\t" + Long.toHexString(getRomEntry().getExpectedCRC32()));
            System.out.println("CRC32, actual:  \t" + Long.toHexString(actualCRC32));
        }
        return getRomEntry().getExpectedCRC32() == actualCRC32;
    }

}
