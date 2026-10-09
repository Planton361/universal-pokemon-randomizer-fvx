package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Entirely in-memory synthetic native tables; never loads a file or a private witness. */
class Gen3CfruDpeSpeciesBoundaryTest {
    private static final int MAX_ID = 0x59F, CAPACITY = MAX_ID + 1;
    private static final int NAMES = 0x130000, DEX = 0x140000, STATS = 0x150000;
    private static final int EVOS = 0x1A0000, NAME_LENGTH = 11;

    @Test
    void physicalCapacityIncludesReservedZeroButModelEndsAtPecharunt() throws Exception {
        var f = fixture(true, CAPACITY);
        byte[] before = f.bytes.clone();
        String diagnostics = load(f);
        Species[] model = (Species[]) get(f.handler, "pokesInternal");
        assertEquals(CAPACITY, model.length);
        assertNull(model[0]);
        assertEquals(1, model[1].getSpeciesSetIdentityNumber());
        assertEquals("Pecharunt", model[MAX_ID].getName());
        assertEquals(MAX_ID, model[MAX_ID].getSpeciesSetIdentityNumber());
        assertEquals(MAX_ID, f.handler.getCfruDpePokemonCountForDiagnostics());
        assertEquals(CAPACITY, f.entry.getIntValue("PokemonCount")); // Strict ABI metadata remains physical.
        assertEquals(CAPACITY, ((String[]) get(f.handler, "pokeNames")).length);
        assertEquals(CAPACITY, ((int[]) get(f.handler, "internalToPokedex")).length);
        assertEquals(MAX_ID, ((int[]) get(f.handler, "internalToPokedex"))[MAX_ID]);
        assertTrue(diagnostics.contains("maxInternalSpeciesId=1439"));
        assertTrue(diagnostics.contains("nativeTableCapacity=1440"));
        assertFalse(diagnostics.contains("Alien"));
        assertEquals(MAX_ID, ((List<?>) get(f.handler, "speciesList")).size() - 1);
        assertArrayEquals(before, f.bytes);
    }

    @Test
    void overlongPlausibleNameScanIsCappedBeforeUnownedAdjacentSlots() throws Exception {
        var f = fixture(true, CAPACITY);
        set(f.handler, "useCfruDpeGen9SpeciesCount", false);
        var diagnostics = new ByteArrayOutputStream(); PrintStream previous = System.err;
        try (var stream = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            System.setErr(stream); invoke(f.handler, "basicBPRE10HackSupport");
        } finally { System.setErr(previous); }
        assertFalse(diagnostics.toString(StandardCharsets.UTF_8).contains("nameWindow index=1440"));
        assertFalse(diagnostics.toString(StandardCharsets.UTF_8).contains("Alien"));
        assertEquals(CAPACITY, f.entry.getIntValue("PokemonCount"));
        load(f);
        assertEquals(CAPACITY, ((Species[]) get(f.handler, "pokesInternal")).length);
    }

    @Test
    void truncatedNameScanStillRecoversFullNativeModelFromTableProfile() throws Exception {
        var f = fixture(true, CAPACITY);
        f.bytes[NAMES + 500 * NAME_LENGTH] = (byte) 0xFF;
        set(f.handler, "useCfruDpeGen9SpeciesCount", false);
        var diagnostics = new ByteArrayOutputStream(); PrintStream previous = System.err;
        try (var stream = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            System.setErr(stream); invoke(f.handler, "basicBPRE10HackSupport");
        } finally { System.setErr(previous); }
        assertFalse(diagnostics.toString(StandardCharsets.UTF_8).contains("nameWindow index=1440"));
        assertFalse(diagnostics.toString(StandardCharsets.UTF_8).contains("Alien"));
        assertEquals(CAPACITY, f.entry.getIntValue("PokemonCount"));
        load(f);
        assertEquals("Pecharunt", ((Species[]) get(f.handler, "pokesInternal"))[MAX_ID].getName());
    }

    @Test
    void unchangedSavePreservesEveryByteIncludingReservedAndNeighborSentinels() throws Exception {
        var f = fixture(true, CAPACITY);
        load(f); f.handler.loadEvolutions();
        byte[] before = f.bytes.clone();
        f.handler.saveSpeciesStats();
        assertArrayEquals(before, f.bytes);
    }

    @Test
    void pecharuntRemainsWritableWithoutTouchingAdjacentNameStatDexOrEvolutionBytes() throws Exception {
        var f = fixture(true, CAPACITY);
        load(f); f.handler.loadEvolutions();
        Species last = ((Species[]) get(f.handler, "pokesInternal"))[MAX_ID];
        last.setName("Pecha"); last.setHp(91);
        byte[] before = f.bytes.clone();
        f.handler.saveSpeciesStats();
        assertEquals(91, f.bytes[STATS + MAX_ID * Gen3Constants.baseStatsEntrySize] & 255);
        int name = NAMES + MAX_ID * NAME_LENGTH, stats = STATS + MAX_ID * Gen3Constants.baseStatsEntrySize;
        for (int i = 0; i < before.length; i++) {
            if ((i < name || i >= name + NAME_LENGTH) && (i < stats || i >= stats + Gen3Constants.baseStatsEntrySize)) {
                assertEquals(before[i], f.bytes[i], "unowned byte " + i);
            }
        }
    }

    @Test
    void malformedCapacitiesRejectBeforeModelOrByteMutation() throws Exception {
        for (int count : new int[]{0, MAX_ID, CAPACITY + 1, 2000}) {
            var f = fixture(true, count); byte[] before = f.bytes.clone();
            assertThrows(RomIOException.class, f.handler::loadSpeciesStats);
            assertArrayEquals(before, f.bytes);
            assertNull(get(f.handler, "pokesInternal"));
        }
    }

    @Test
    void incompleteTablesRejectBeforeAnySaveMutation() throws Exception {
        for (String table : List.of("PokemonNames", "PokemonStats", "PokedexOrder")) {
            var f = fixture(true, CAPACITY); load(f); f.handler.loadEvolutions();
            f.entry.putIntValue(table, f.bytes.length - 1);
            byte[] before = f.bytes.clone();
            assertThrows(RomIOException.class, f.handler::saveSpeciesStats);
            assertArrayEquals(before, f.bytes);
        }
    }

    @Test
    void phantomOrReservedModelSlotRejectsBeforeSaveMutation() throws Exception {
        for (boolean phantom : new boolean[]{true, false}) {
            var f = fixture(true, CAPACITY); load(f); f.handler.loadEvolutions();
            Species[] model = (Species[]) get(f.handler, "pokesInternal");
            if (phantom) {
                model = Arrays.copyOf(model, CAPACITY + 1); model[CAPACITY] = species(CAPACITY);
            } else model[0] = species(0);
            set(f.handler, "pokesInternal", model);
            byte[] before = f.bytes.clone();
            assertThrows(RomIOException.class, f.handler::saveSpeciesStats);
            assertArrayEquals(before, f.bytes);
        }
    }

    @Test
    void nativeLearnsetAndCompatibilityOffsetsRespectPositiveSpeciesBoundary() throws Exception {
        var f = fixture(true, CAPACITY);
        assertEquals(CAPACITY * 4, Gen3RomHandler.cfruDpeLevelUpLearnsetsTableLength());
        assertEquals(100, Gen3RomHandler.cfruDpeLevelUpLearnsetPointerOffset(100, 0));
        assertEquals(100 + MAX_ID * 4, Gen3RomHandler.cfruDpeLevelUpLearnsetPointerOffset(100, MAX_ID));
        assertThrows(RomIOException.class, () -> Gen3RomHandler.cfruDpeLevelUpLearnsetPointerOffset(100, CAPACITY));
        assertThrows(RomIOException.class, () -> Gen3RomHandler.cfruDpeLevelUpLearnsetPointerOffset(100, -1));
        for (String method : List.of("getCfruDpeTmHmCompatibilityOffsetForSpecies", "getCfruDpeMoveTutorCompatibilityOffsetForSpecies")) {
            for (int id : new int[]{0, 1, MAX_ID, CAPACITY}) {
                int offset = (int) invoke(f.handler, method, new Class<?>[]{int.class, Species.class}, 0x170000, species(id));
                if (id == 0 || id == CAPACITY) assertEquals(-1, offset);
                else assertTrue(offset >= 0x170000);
            }
        }
    }

    @Test
    void invalidCompatibilityMapEntryCannotWriteNeighboringTableRows() throws Exception {
        var f = fixture(true, CAPACITY);
        byte[] before = f.bytes.clone();
        f.handler.setTMHMCompatibility(Map.of(species(CAPACITY), new boolean[129], species(0), new boolean[129]));
        f.handler.setMoveTutorCompatibility(Map.of(species(CAPACITY), new boolean[129], species(0), new boolean[129]));
        assertArrayEquals(before, f.bytes);
    }

    @Test
    void nativeEvolutionRowCannotAddressReservedOrPostPecharuntSpecies() throws Exception {
        var f = fixture(true, CAPACITY);
        assertEquals(EVOS + MAX_ID * 128, invoke(f.handler, "getEvolutionRowOffset",
                new Class<?>[]{int.class, Species.class}, EVOS, species(MAX_ID)));
        for (int id : new int[]{0, CAPACITY}) assertThrows(RomIOException.class, () -> invoke(f.handler,
                "getEvolutionRowOffset", new Class<?>[]{int.class, Species.class}, EVOS, species(id)));
    }

    @Test
    void fullEvolutionTablePreservesZeroAndInvalidTargetWhileModelingPecharuntBoundary() throws Exception {
        var f = fixture(true, CAPACITY);
        word(f.bytes, EVOS, 0xFD); word(f.bytes, EVOS + 4, 1); // Reserved row stays raw.
        word(f.bytes, EVOS + 128, 4); word(f.bytes, EVOS + 130, 16);
        word(f.bytes, EVOS + 132, MAX_ID); // Last legitimate target.
        int last = EVOS + MAX_ID * 128;
        word(f.bytes, last, 4); word(f.bytes, last + 2, 20);
        word(f.bytes, last + 4, CAPACITY); // Invalid target remains opaque, never modeled.
        load(f); f.handler.loadEvolutions();
        Species[] model = (Species[]) get(f.handler, "pokesInternal");
        assertSame(model[MAX_ID], model[1].getEvolutionsFrom().getFirst().getTo());
        assertTrue(model[MAX_ID].getEvolutionsFrom().isEmpty());
        byte[] before = f.bytes.clone(); f.handler.saveSpeciesStats();
        assertArrayEquals(before, f.bytes);
    }

    @Test
    void genericNonCfruCountRetainsInclusiveMaxIdSemantics() throws Exception {
        var f = fixture(false, CAPACITY); load(f);
        Species[] model = (Species[]) get(f.handler, "pokesInternal");
        assertEquals(CAPACITY + 1, model.length);
        assertEquals("Alien", model[CAPACITY].getName());
        assertEquals(CAPACITY, f.handler.getCfruDpePokemonCountForDiagnostics());
    }

    @Test
    void vanillaSpeciesBoundaryRemainsUnchanged() throws Exception {
        var f = fixture(false, 25); set(f.handler, "isRomHack", false); load(f);
        assertEquals(26, ((Species[]) get(f.handler, "pokesInternal")).length);
        assertEquals(25, f.handler.getCfruDpePokemonCountForDiagnostics());
    }

    private static String load(Fixture f) throws Exception {
        var bytes = new ByteArrayOutputStream(); PrintStream previous = System.err;
        try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setErr(stream); f.handler.loadSpeciesStats();
        } finally { System.setErr(previous); }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private record Fixture(Gen3RomHandler handler, Gen3RomEntry entry, byte[] bytes) {}

    private static Fixture fixture(boolean nativeProfile, int count) throws Exception {
        Gen3RomEntry entry = Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").stream()
                .filter(e -> "Fire Red (U) 1.0".equals(e.getName())).map(Gen3RomEntry::new).findFirst().orElseThrow();
        entry.putIntValue("PokemonCount", count); entry.putIntValue("PokemonNames", NAMES);
        entry.putIntValue("PokemonNameLength", NAME_LENGTH); entry.putIntValue("PokemonStats", STATS);
        entry.putIntValue("PokedexOrder", DEX); entry.putIntValue("PokemonEvolutions", EVOS);
        byte[] memory = new byte[0x200000];
        for (int id = 1; id <= 1460; id++) {
            name(memory, id, id == MAX_ID ? "Pecharunt" : id >= CAPACITY ? "Alien" : "Mon");
            int o = STATS + id * Gen3Constants.baseStatsEntrySize;
            Arrays.fill(memory, o, o + 6, (byte) 80);
            memory[DEX + (id - 1) * 2] = (byte) id;
            memory[DEX + (id - 1) * 2 + 1] = (byte) (id >> 8);
        }
        name(memory, 824, "Xerneas"); name(memory, 1000, "Hakamo-o"); name(memory, 1294, "Sprigatito");
        Arrays.fill(memory, EVOS + CAPACITY * 128, EVOS + (CAPACITY + 1) * 128, (byte) 0x5A);
        pointer(memory, 0x3EA7C, 0x170000); pointer(memory, 0x43C68, 0x172000);
        pointer(memory, 0x125A8C, 0x178000); pointer(memory, 0x42F6C, EVOS);
        pointer(memory, 0x45C50, 0x176000);
        pointer(memory, 0x120C30, 0x174000); pointer(memory, 0xE5440, 0x178800);
        pointer(memory, 0xFC00, 0x1D0000);
        pointer(memory, Gen3Constants.moveNamesPointer, 0x179000);
        pointer(memory, Gen3Constants.moveDataPointer, 0x17D000);
        var handler = new Gen3RomHandler();
        set(handler, "romEntry", entry); set(handler, "rom", memory);
        set(handler, "isRomHack", true); set(handler, "useCfruDpeGen9SpeciesCount", nativeProfile);
        String[] tb = new String[256]; Map<String, Byte> d = new HashMap<>();
        for (int c = 32; c < 127; c++) { tb[c] = Character.toString(c); d.put(tb[c], (byte) c); }
        set(handler, "tb", tb); set(handler, "d", d);
        return new Fixture(handler, entry, memory);
    }

    private static Species species(int id) {
        Species s = new Species(id); s.setSpeciesSetIdentityNumber(id); s.setName("Mon"); return s;
    }
    private static void name(byte[] bytes, int id, String name) {
        int o = NAMES + id * NAME_LENGTH;
        Arrays.fill(bytes, o, o + NAME_LENGTH, Gen3Constants.textPadding);
        byte[] text = name.getBytes(StandardCharsets.US_ASCII); System.arraycopy(text, 0, bytes, o, text.length);
        bytes[o + text.length] = Gen3Constants.textTerminator;
    }
    private static void word(byte[] bytes, int o, int value) {
        bytes[o] = (byte) value; bytes[o + 1] = (byte) (value >> 8);
    }
    private static void pointer(byte[] bytes, int o, int target) {
        int value = target + 0x08000000;
        for (int i = 0; i < 4; i++) bytes[o + i] = (byte) (value >> (i * 8));
    }
    private static Object invoke(Object target, String name) throws Exception { return invoke(target, name, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(target, args); }
        catch (InvocationTargetException e) { if (e.getCause() instanceof RuntimeException r) throw r; throw e; }
    }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
}
