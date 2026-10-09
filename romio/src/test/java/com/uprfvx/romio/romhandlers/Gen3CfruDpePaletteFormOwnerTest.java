package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.graphics.palettes.Palette;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import compressors.DSCmp;
import compressors.DSDecmp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Generated memory only; real Gen3 loader, snapshots, allocator and copy-save writer. */
class Gen3CfruDpePaletteFormOwnerTest {
    private static final int NORMAL = 0x100, SHINY = 0x4000, FREE = 0x50000;

    @Test void sameDexNativeFormsLoadDistinctPairsAndNoopIsByteIdentical() throws Exception {
        Fixture f = fixture(); byte[] before = f.bytes().clone();
        f.loadPokemonPalettes();
        assertEquals(0, f.owners.get(1).compareTo(f.owners.get(2)));
        assertNotEquals(f.owners.get(1), f.owners.get(2));
        for (Species sp : f.owners) for (boolean shiny : new boolean[]{false, true}) {
            assertArrayEquals(raw(sp.getSpeciesSetIdentityNumber(), shiny), palette(sp, shiny).toBytes());
            assertArrayEquals(palette(sp, shiny).toBytes(), snapshots(f, shiny).get(sp));
        }
        assertNotSame(f.owners.get(1).getNormalPalette(), f.owners.get(2).getNormalPalette());
        f.savePokemonPalettes(); assertArrayEquals(before, f.bytes());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changedChannelRepointsOnlyItsNativeEntryAndNewAllocation(boolean shiny) throws Exception {
        for (int index = 0; index < 5; index++) {
            Fixture f = fixture(); f.loadPokemonPalettes();
            Species sp = f.owners.get(index); byte[] before = f.bytes().clone();
            byte[] changed = palette(sp, shiny).toBytes(); changed[2] ^= 31;
            replace(sp, shiny, new Palette(changed)); f.savePokemonPalettes();
            int entry = table(shiny) + sp.getSpeciesSetIdentityNumber() * 8;
            assertTrue(f.pointerAt(entry) >= FREE);
            assertArrayEquals(changed, f.decode(entry));
            assertBudget(f, before, Set.of(entry));
            // Reload proves the published source, per-form preservation and unchanged opposite channel.
            f.loadPokemonPalettes();
            for (Species other : f.owners) for (boolean channel : new boolean[]{false, true})
                assertArrayEquals(other == sp && channel == shiny ? changed
                        : raw(other.getSpeciesSetIdentityNumber(), channel), palette(other, channel).toBytes());
            byte[] saved = f.bytes().clone(); f.savePokemonPalettes(); assertArrayEquals(saved, f.bytes());
        }
    }

    @Test void independentSameDexEditsCannotOverwriteEitherRepoint() throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes(); byte[] before = f.bytes().clone();
        Set<Integer> entries = new HashSet<>();
        for (Species sp : f.owners) for (boolean shiny : new boolean[]{false, true}) {
            byte[] changed = palette(sp, shiny).toBytes(); changed[4] ^= 7;
            replace(sp, shiny, new Palette(changed)); entries.add(table(shiny) + sp.getSpeciesSetIdentityNumber() * 8);
        }
        f.savePokemonPalettes(); assertBudget(f, before, entries);
        Set<Integer> pointers = new HashSet<>();
        for (Species sp : f.owners) for (boolean shiny : new boolean[]{false, true}) {
            int entry = table(shiny) + sp.getSpeciesSetIdentityNumber() * 8;
            assertTrue(pointers.add(f.pointerAt(entry)));
            assertArrayEquals(palette(sp, shiny).toBytes(), f.decode(entry));
        }
    }

    @Test void sharedSourcePayloadHasDistinctEntriesAndJavaSnapshots() throws Exception {
        Fixture f = fixture(); Species a = f.owners.get(1), b = f.owners.get(2);
        for (boolean shiny : new boolean[]{false, true})
            f.pointer(table(shiny) + b.getSpeciesSetIdentityNumber() * 8,
                    f.pointerAt(table(shiny) + a.getSpeciesSetIdentityNumber() * 8));
        f.loadPokemonPalettes();
        assertNotSame(a.getNormalPalette(), b.getNormalPalette());
        assertArrayEquals(a.getNormalPalette().toBytes(), b.getNormalPalette().toBytes());
        byte[] before = f.bytes().clone(), original = a.getNormalPalette().toBytes();
        byte[] changed = b.getNormalPalette().toBytes(); changed[6] ^= 7; b.setNormalPalette(new Palette(changed));
        f.savePokemonPalettes(); assertBudget(f, before, Set.of(NORMAL + b.getSpeciesSetIdentityNumber() * 8));
        assertArrayEquals(original, f.decode(NORMAL + a.getSpeciesSetIdentityNumber() * 8));
        assertArrayEquals(original, snapshots(f, false).get(b));
        assertArrayEquals(changed, f.decode(NORMAL + b.getSpeciesSetIdentityNumber() * 8));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1440, 2000})
    void invalidNativeIdsNeverReadOrWriteReservedAndNeighborEntries(int id) throws Exception {
        Fixture f = fixture(); Species bad = species(19, id);
        f.select(List.of(bad)); byte[] before = f.bytes().clone(); f.loadPokemonPalettes();
        assertNull(bad.getNormalPalette()); assertNull(bad.getShinyPalette());
        bad.setNormalPalette(new Palette(raw(19, false)));
        assertThrows(RomIOException.class, f::savePokemonPalettes); assertArrayEquals(before, f.bytes());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void duplicateOrConflictingNativeObjectsFailBeforeAnyMutation(int kind) throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes(); Species a = f.owners.get(1);
        Species conflicting = kind == 0 ? a : species(kind == 1 ? 19 : 20, 19);
        List<Species> model = new ArrayList<>(f.owners); model.add(conflicting); f.select(model);
        byte[] before = f.bytes().clone(); a.setNormalPalette(new Palette(raw(777, false)));
        assertThrows(RomIOException.class, f::savePokemonPalettes); assertArrayEquals(before, f.bytes());
        assertDoesNotThrow(f::loadPokemonPalettes);
        assertNull(a.getNormalPalette()); assertNull(conflicting.getNormalPalette());
        assertArrayEquals(before, f.bytes());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void mismatchedSourceArrayDexOrUnknownObjectIsUnowned(int kind) throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes(); Species sp = f.owners.get(2);
        if (kind == 0) f.internal[1020] = species(19, 1020);
        if (kind == 1) f.dex[1020] = 20;
        if (kind == 2) sp.setSpeciesSetIdentityNumber(1019);
        byte[] before = f.bytes().clone(); sp.setNormalPalette(new Palette(raw(999, false)));
        assertThrows(RomIOException.class, f::savePokemonPalettes); assertArrayEquals(before, f.bytes());
        f.loadPokemonPalettes(); assertNull(sp.getNormalPalette()); assertNull(sp.getShinyPalette());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void malformedAssetChannelIsSkippedWithoutInventingProvenance(int kind) throws Exception {
        Fixture f = fixture(); Species sp = f.owners.get(2); int entry = NORMAL + 1020 * 8;
        int source = f.pointerAt(entry);
        switch (kind) {
            case 0 -> f.pointer(entry, -1);
            case 1 -> f.pointer(entry, NORMAL + 8);
            case 2 -> f.bytes()[source] = 0x55;
            case 3 -> f.bytes()[source + 1] = 64;
            case 4 -> f.pointer(entry, f.bytes().length - 2);
            case 5 -> { f.bytes()[source + 4] = (byte) 0x80; f.bytes()[source + 5] = (byte) 0xF0; f.bytes()[source + 6] = 0; }
            case 6 -> f.pointer(entry, SHINY);
        }
        byte[] before = f.bytes().clone(); assertDoesNotThrow(f::loadPokemonPalettes);
        assertNull(sp.getNormalPalette()); assertFalse(snapshots(f, false).containsKey(sp));
        assertNotNull(sp.getShinyPalette()); assertFalse(f.getCfruDpePalettePairEligibility(sp).eligible());
        sp.setNormalPalette(new Palette(raw(1020, false))); f.savePokemonPalettes();
        assertArrayEquals(before, f.bytes());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void malformedOrOverlappingTablesRejectChangedSaveBeforeRepoints(int kind) throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes();
        f.entry.putIntValue("PokemonShinyPalettes", switch (kind) {
            case 0 -> NORMAL; case 1 -> f.bytes().length - 8; case 2 -> 0; default -> SHINY + 1;
        });
        byte[] before = f.bytes().clone(); f.owners.getFirst().setNormalPalette(new Palette(raw(999, false)));
        assertThrows(RomIOException.class, f::savePokemonPalettes); assertArrayEquals(before, f.bytes());
        assertDoesNotThrow(f::loadPokemonPalettes);
        for (Species sp : f.owners) assertNull(sp.getNormalPalette());
    }

    @Test void repeatedSaveRetainsLoadedSnapshotsAndBothRepointedChannels() throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes(); Species sp = f.owners.get(2);
        byte[] original = snapshots(f, false).get(sp).clone();
        byte[] changed = sp.getNormalPalette().toBytes(); changed[10] ^= 15;
        sp.setNormalPalette(new Palette(changed)); f.savePokemonPalettes();
        byte[] beforeSecond = f.bytes().clone(); f.savePokemonPalettes();
        assertBudget(f, beforeSecond, Set.of(NORMAL + 1020 * 8));
        assertArrayEquals(original, snapshots(f, false).get(sp));
        assertArrayEquals(changed, f.decode(NORMAL + 1020 * 8));
        assertArrayEquals(raw(1020, true), f.decode(SHINY + 1020 * 8));
    }

    @Test void lateStaleSourceFailsWholeOwnerPreflightBeforeEarlyValidChange() throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes();
        for (Species sp : List.of(f.owners.getFirst(), f.owners.getLast()))
            sp.setNormalPalette(new Palette(raw(555, false)));
        f.pointer(NORMAL + 1439 * 8, f.pointerAt(NORMAL + 19 * 8));
        byte[] before = f.bytes().clone(); assertThrows(RomIOException.class, f::savePokemonPalettes);
        assertArrayEquals(before, f.bytes());
    }

    @Test void fullPhysical1440RowsRetain1439NativePairsIncludingSameDexOwners() throws Exception {
        Fixture f = fixture(); List<Species> all = new ArrayList<>();
        for (int id = 1; id <= 1439; id++) all.add(species(id % 1025 + 1, id));
        f.install(all); byte[] before = f.bytes().clone(); f.loadPokemonPalettes();
        assertEquals(1439, snapshots(f, false).size()); assertEquals(1439, snapshots(f, true).size());
        for (Species sp : all) assertArrayEquals(raw(sp.getSpeciesSetIdentityNumber(), true), sp.getShinyPalette().toBytes());
        f.savePokemonPalettes(); assertArrayEquals(before, f.bytes());
        assertNotNull(all.getLast().getNormalPalette());
    }

    @Test void gfx004FirstOwnerRestrictionAndUnownPreservationRemain() throws Exception {
        Fixture f = fixture(); f.loadPokemonPalettes();
        assertFalse(f.getCfruDpePalettePairEligibility(f.owners.get(1)).eligible());
        assertFalse(f.getCfruDpePalettePairEligibility(f.owners.get(2)).eligible());
        Species unown = species(201, 201); f.install(List.of(unown)); f.loadPokemonPalettes();
        byte[] before = f.bytes().clone(); unown.setNormalPalette(new Palette(raw(777, false)));
        assertFalse(f.getCfruDpePalettePairEligibility(unown).eligible());
        f.savePokemonPalettes(); assertArrayEquals(before, f.bytes());
    }

    @Test void adjacentTablesUseExactly1440RowsNot1441() throws Exception {
        Fixture f = fixture(); int adjacent = NORMAL + 1440 * 8;
        System.arraycopy(f.bytes(), SHINY, f.bytes(), adjacent, 1440 * 8);
        f.entry.putIntValue("PokemonShinyPalettes", adjacent);
        f.loadPokemonPalettes();
        Species last = f.owners.getLast();
        assertArrayEquals(raw(1439, true), last.getShinyPalette().toBytes());
        byte[] before = f.bytes().clone(); f.savePokemonPalettes(); assertArrayEquals(before, f.bytes());
        byte[] changed = last.getShinyPalette().toBytes(); changed[2] ^= 7;
        last.setShinyPalette(new Palette(changed)); f.savePokemonPalettes();
        assertBudget(f, before, Set.of(adjacent + 1439 * 8));
        assertArrayEquals(changed, f.decode(adjacent + 1439 * 8));
    }

    @Test void sourceSupportedGen1to9AndPersistentRegionalOwnersRoundtrip() throws Exception {
        Fixture f = fixture();
        int[][] ids = {{1,1}, {152,152}, {252,277}, {387,440}, {495,548}, {650,758},
                {722,939}, {810,1102}, {906,1294}, {1025,1439}, {19,19}, {19,1020}, {741,958}, {741,1043}};
        List<Species> selected = new ArrayList<>();
        for (int[] pair : ids) selected.add(species(pair[0], pair[1]));
        f.install(selected); f.loadPokemonPalettes(); byte[] before = f.bytes().clone();
        Set<Integer> entries = new HashSet<>();
        for (Species sp : selected) {
            assertArrayEquals(raw(sp.getSpeciesSetIdentityNumber(), false), sp.getNormalPalette().toBytes());
            byte[] changed = sp.getShinyPalette().toBytes(); changed[8] ^= 31;
            sp.setShinyPalette(new Palette(changed)); entries.add(SHINY + sp.getSpeciesSetIdentityNumber() * 8);
        }
        f.savePokemonPalettes(); assertBudget(f, before, entries); f.loadPokemonPalettes();
        for (Species sp : selected) {
            byte[] changed = raw(sp.getSpeciesSetIdentityNumber(), true); changed[8] ^= 31;
            assertArrayEquals(changed, sp.getShinyPalette().toBytes());
        }
    }

    @Test void omittedSourceAssetsStayAbsentAndCannotGainSnapshotFromBaseDex() throws Exception {
        Fixture f = fixture(); Species omitted = species(25, 1036);
        f.install(List.of(species(25,25), omitted));
        // Selected DPE leaves 1036 without an explicit palette initializer (zero pointer).
        f.pointer(NORMAL + 1036 * 8, 0); f.pointer(SHINY + 1036 * 8, 0);
        f.loadPokemonPalettes(); byte[] before = f.bytes().clone();
        assertNull(omitted.getNormalPalette()); assertNull(omitted.getShinyPalette());
        assertFalse(snapshots(f, false).containsKey(omitted));
        omitted.setNormalPalette(new Palette(raw(25, false))); f.savePokemonPalettes();
        assertArrayEquals(before, f.bytes());
    }

    @Test void nativeReadDoesNotNeedFirstDexProjectionButGfx004StillDoes() throws Exception {
        Fixture f = fixture(); f.reverse[19] = 0; f.loadPokemonPalettes();
        assertArrayEquals(raw(1020, false), f.owners.get(2).getNormalPalette().toBytes());
        assertFalse(f.getCfruDpePalettePairEligibility(f.owners.get(1)).eligible());
        byte[] before = f.bytes().clone(); f.savePokemonPalettes(); assertArrayEquals(before, f.bytes());
    }

    @Test void nonSelectedLoaderRetainsLegacyDexProjection() throws Exception {
        Fixture f = fixture(); set(f, "useCfruDpeGen9SpeciesCount", false); f.loadPokemonPalettes();
        Species base = f.owners.get(1), form = f.owners.get(2);
        assertArrayEquals(base.getNormalPalette().toBytes(), form.getNormalPalette().toBytes());
        assertArrayEquals(base.getShinyPalette().toBytes(), form.getShinyPalette().toBytes());
    }

    private static void assertBudget(Fixture f, byte[] before, Set<Integer> entries) {
        List<int[]> allocations = new ArrayList<>();
        for (int entry : entries) {
            int pointer = f.pointerAt(entry);
            assertTrue(pointer >= FREE);
            allocations.add(new int[]{pointer, pointer + DSCmp.compressLZ10(f.decode(entry)).length});
        }
        for (int i = 0; i < before.length; i++) {
            final int offset = i;
            boolean allowed = entries.stream().anyMatch(e -> offset >= e && offset < e + 4)
                    || allocations.stream().anyMatch(a -> offset >= a[0] && offset < a[1]);
            if (!allowed && before[i] != f.bytes()[i])
                fail("byte outside native pointer/exact new compressed allocation budget: " + i);
        }
        // Includes original payloads, tags, slot zero, absent rows, adjacent and allocator sentinels.
    }
    private static Palette palette(Species sp, boolean shiny) { return shiny ? sp.getShinyPalette() : sp.getNormalPalette(); }
    private static void replace(Species sp, boolean shiny, Palette p) { if (shiny) sp.setShinyPalette(p); else sp.setNormalPalette(p); }
    private static int table(boolean shiny) { return shiny ? SHINY : NORMAL; }
    private static byte[] raw(int id, boolean shiny) {
        byte[] b = new byte[32]; for (int i = 0; i < 16; i++) {
            int color = (id * 17 + i * 47 + (shiny ? 997 : 0)) & 0x7FFF;
            b[i * 2] = (byte) color; b[i * 2 + 1] = (byte) (color >> 8);
        } return b;
    }
    private static Species species(int dex, int id) {
        Species s = new Species(dex); s.setSpeciesSetIdentityNumber(id); s.setName("Synthetic " + id); return s;
    }
    private static Fixture fixture() throws Exception {
        // Selected DPE IDs: Bulbasaur, Rattata, Alolan Rattata, Treecko, Pecharunt.
        return new Fixture(List.of(species(1, 1), species(19, 19), species(19, 1020), species(252, 277), species(1025, 1439)));
    }
    private static void set(Fixture f, String name, Object value) throws Exception {
        Field field = Gen3RomHandler.class.getDeclaredField(name); field.setAccessible(true); field.set(f, value);
    }
    @SuppressWarnings("unchecked") private static Map<Species, byte[]> snapshots(Fixture f, boolean shiny) throws Exception {
        Field field = Gen3RomHandler.class.getDeclaredField(shiny ? "originalCfruDpeShinyPaletteBytes" : "originalCfruDpeNormalPaletteBytes");
        field.setAccessible(true); return (Map<Species, byte[]>) field.get(f);
    }
    private static final class Fixture extends Gen3RomHandler {
        final Gen3RomEntry entry; final Species[] internal = new Species[1440];
        final int[] dex = new int[1440], reverse = new int[1440]; List<Species> owners;
        Fixture(List<Species> selected) throws Exception {
            var constructor = Gen3RomEntry.class.getDeclaredConstructor(String.class); constructor.setAccessible(true);
            entry = constructor.newInstance("SYNTHETIC F03"); entry.setRomCode("BPRE"); entry.setRomType(Gen3Constants.RomType_FRLG);
            entry.putIntValue("PokemonCount", 1440); entry.putIntValue("PokemonNormalPalettes", NORMAL);
            entry.putIntValue("PokemonShinyPalettes", SHINY); set(this, "romEntry", entry);
            set(this, "useCfruDpeGen9SpeciesCount", true); set(this, "isRomHack", true);
            set(this, "pokesInternal", internal); set(this, "internalToPokedex", dex); set(this, "pokedexToInternal", reverse);
            rom = new byte[0x60000]; Arrays.fill(rom, (byte) 0x5A); freeSpace(FREE, 0x10000); install(selected);
        }
        void select(List<Species> selected) throws Exception {
            owners = selected; List<Species> model = new ArrayList<>(); model.add(null); model.addAll(selected);
            set(this, "speciesList", model); set(this, "numRealPokemon", selected.size());
        }
        void install(List<Species> selected) throws Exception {
            Arrays.fill(internal, null); Arrays.fill(dex, 0); Arrays.fill(reverse, 0);
            for (Species sp : selected) {
                int id = sp.getSpeciesSetIdentityNumber(); internal[id] = sp; dex[id] = sp.getNumber();
                if (reverse[sp.getNumber()] == 0) reverse[sp.getNumber()] = id;
                for (boolean shiny : new boolean[]{false, true}) {
                    int source = 0x10000 + id * 128 + (shiny ? 64 : 0);
                    byte[] compressed = DSCmp.compressLZ10(raw(id, shiny));
                    System.arraycopy(compressed, 0, rom, source, compressed.length); pointer(table(shiny) + id * 8, source);
                }
            }
            select(selected);
        }
        void pointer(int entry, int target) { writePointer(entry, target); }
        int pointerAt(int entry) { return readPointer(entry, true); }
        byte[] decode(int entry) { return new Palette(DSDecmp.Decompress(rom, pointerAt(entry))).toBytes(); }
        byte[] bytes() { return rom; }
    }
}
