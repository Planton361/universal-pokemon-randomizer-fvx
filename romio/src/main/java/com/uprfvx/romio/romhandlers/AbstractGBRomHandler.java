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

    protected class DataRewriter<E> {

        protected Function<Integer, Integer> pointerReader = AbstractGBRomHandler.this::readPointer;
        protected BiConsumer<Integer, Integer> pointerWriter = AbstractGBRomHandler.this::writePointer;

        private boolean longAlignAdresses = true;

        public boolean isLongAlignAdresses() {
            return longAlignAdresses;
        }

        public void setLongAlignAdresses(boolean longAlignAdresses) {
            this.longAlignAdresses = longAlignAdresses;
        }

        public void setPointerReader(Function<Integer, Integer> pointerReader) {
            this.pointerReader = pointerReader;
        }

        public void setPointerWriter(BiConsumer<Integer, Integer> pointerWriter) {
            this.pointerWriter = pointerWriter;
        }

        public void rewriteData(int pointerOffset, E e, Function<E, byte[]> newDataFunction,
                                Function<Integer, Integer> lengthOfOldFunction) {
            rewriteData(pointerOffset, e, new int[0], newDataFunction, lengthOfOldFunction);
        }

        public void rewriteData(int pointerOffset, E e, int[] secondaryPointerOffsets,
                                Function<E, byte[]> newDataFunction, Function<Integer, Integer> lengthOfOldFunction) {
            byte[] newData = newDataFunction.apply(e);
            int oldDataOffset = pointerReader.apply(pointerOffset);
            int oldLength = lengthOfOldFunction.apply(oldDataOffset);
            freeSpace(oldDataOffset, oldLength);
            int newDataOffset = repointAndWriteToFreeSpace(pointerOffset, newData);

            rewriteSecondaryPointers(pointerOffset, secondaryPointerOffsets, oldDataOffset, newDataOffset);
        }

        /**
         * Returns the new offset of the data.
         **/
        protected int repointAndWriteToFreeSpace(int pointerOffset, byte[] data) {
            int newOffset = findAndUnfreeSpace(data.length, longAlignAdresses);

            pointerWriter.accept(pointerOffset, newOffset);
            writeBytes(newOffset, data);

            return newOffset;
        }

        protected void rewriteSecondaryPointers(int primaryPointerOffset, int[] secondaryPointerOffsets,
                                              int oldDataOffset, int newDataOffset) {
            for (int spo : secondaryPointerOffsets) {
                int offset = pointerReader.apply(spo);
                if (spo != primaryPointerOffset && offset != oldDataOffset) {
                    System.out.println();
                    System.out.println("bad: " + spo);
                    throw new RomIOException("Invalid secondary pointer spo=0x" + Integer.toHexString(spo) +
                            ". Points to 0x" + Integer.toHexString(offset) + " instead of 0x" +
                            Integer.toHexString(oldDataOffset) + ".");
                }
                pointerWriter.accept(spo, newDataOffset);
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
