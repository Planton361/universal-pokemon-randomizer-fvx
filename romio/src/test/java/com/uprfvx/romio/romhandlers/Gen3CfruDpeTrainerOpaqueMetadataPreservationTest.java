package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** In-memory synthetic bytes only; actual trainer loader, serializer and both save paths. */
class Gen3CfruDpeTrainerOpaqueMetadataPreservationTest {
    private static final int DATA = 0x100;
    private static final int ENTRY = 40;
    private static final int PARTY = 0x400;
    private static final int OPAQUE = 30;

    @Test
    void unchangedWitnessSurvivesRepeatedSaveReloadAndFreshHandler() throws Exception {
        Fixture f = fixture(true, 0x000B);
        assertModeled(f.member());
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0x000B);
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0x000B);
        f.handler().loadTrainers();
        assertModeled(f.member());
        f.handler().saveTrainers();
        Fixture fresh = load(true, f.bytes().clone());
        assertModeled(fresh.member());
        assertOpaque(fresh, 1, 0x000B);
        fresh.handler().saveTrainers();
        assertOpaque(fresh, 1, 0x000B);
    }

    @Test
    void arbitrarySixteenBitWordsAndZeroAreOpaque() throws Exception {
        for (int word : new int[] {0x1234, 0x0000, 0xFFFF}) {
            Fixture f = fixture(true, word);
            f.handler().saveTrainers();
            f.handler().loadTrainers();
            f.handler().saveTrainers();
            assertOpaque(f, 1, word);
        }
    }

    @Test
    void modeledMutationsPersistWithoutChangingOpaqueWord() throws Exception {
        Fixture f = fixture(true, 0x000B);
        TrainerPokemon p = f.member();
        p.setLevel(77);
        p.setSpecies(f.handler().species[132]);
        p.setHeldItem(f.handler().itemTable.get(2));
        p.setMoves(new int[] {10, 20, 30, 40});
        p.setAbilitySlot(3);
        p.setNature((byte) 7);
        p.setIVs(19);
        p.setHpEVs((byte) 100);
        p.setAtkEVs((byte) 90);
        p.setDefEVs((byte) 80);
        p.setSpeedEVs((byte) 70);
        p.setSpatkEVs((byte) 60);
        p.setSpdefEVs((byte) 50);
        f.handler().saveTrainers();
        f.handler().loadTrainers();
        p = f.member();
        assertEquals(77, p.getLevel());
        assertEquals(132, p.getSpecies().getNumber());
        assertEquals(2, p.getHeldItem().getId());
        assertArrayEquals(new int[] {10, 20, 30, 40}, p.getMoves());
        assertEquals(3, p.getAbilitySlot());
        assertEquals(7, p.getNature());
        assertEquals(19, p.getIVs());
        assertArrayEquals(new byte[] {100, 90, 80, 70, 60, 50}, evs(p));
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0x000B);
    }

    @Test
    void newAndCopiedMembersDoNotInheritSnapshotEvenWhenInsertedBeforeOriginal() throws Exception {
        Fixture f = fixture(true, 0x1234);
        TrainerPokemon original = f.member();
        TrainerPokemon copied = new TrainerPokemon(original);
        TrainerPokemon created = new TrainerPokemon();
        created.setSpecies(original.getSpecies());
        created.setLevel(original.getLevel());
        created.setMoves(original.getMoves().clone());
        created.setHeldItem(original.getHeldItem());
        f.trainer().getPokemon().addFirst(copied);
        f.trainer().getPokemon().addFirst(created);
        assertEquals(1, snapshots(f).size());
        assertFalse(snapshots(f).containsKey(copied));
        assertFalse(snapshots(f).containsKey(created));
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0, 0, 0x1234);
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0, 0, 0x1234);
        f.handler().loadTrainers();
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0, 0, 0x1234);
    }

    @Test
    void reloadClearsOldIdentitiesAndLoadsCurrentRawWord() throws Exception {
        for (boolean diagnostic : new boolean[] {false, true}) {
            Fixture f = fixture(true, 0x000B);
            TrainerPokemon old = f.member();
            word(f.bytes(), partyOffset(f, 1) + OPAQUE, 0x1234);
            if (diagnostic) f.handler().loadTrainersForDiagnostics();
            else f.handler().loadTrainers();
            assertNotSame(old, f.member());
            assertEquals(1, snapshots(f).size());
            assertFalse(snapshots(f).containsKey(old));
            assertEquals(0x1234, snapshots(f).get(f.member()));
            f.trainer().getPokemon().add(old);
            f.handler().saveTrainers();
            assertOpaque(f, 1, 0x1234, 0);
        }
    }

    @Test
    void runtimeSourceUsesSameSnapshotAndWriter() throws Exception {
        Fixture f = fixture(true, 0x000B);
        row(f.bytes(), 3, 0x500, true, 0x1234);
        // Runtime discovery's existing item precheck also reads offset 6; keep it admissible.
        f.bytes()[0x506] = 1;
        f.bytes()[0x507] = 0;
        f.bytes()[0x20] = 0x5C; // synthetic trainerbattle script
        word(f.bytes(), 0x22, 3);
        f.handler().loadTrainers();
        assertEquals(2, f.handler().loadedTrainers().size());
        assertEquals(3, f.handler().loadedTrainers().getLast().getIndex());
        assertEquals(2, snapshots(f).size());
        f.handler().saveTrainers();
        assertOpaque(f, 1, 0x000B);
        assertOpaque(f, 3, 0x1234);
        f.handler().loadTrainers();
        f.handler().saveTrainers();
        assertOpaque(f, 3, 0x1234);
    }

    @Test
    void vanillaCustomRowsKeepSixteenByteLayoutAndNoSnapshots() throws Exception {
        Fixture f = fixture(false, 0x1234);
        assertTrue(snapshots(f).isEmpty());
        assertEquals(16, Gen3RomHandler.trainerPokemonStride(3, false));
        assertEquals(1, f.member().getAbilitySlot());
        assertEquals(66, f.member().getLevel());
        assertEquals(131, f.member().getSpecies().getNumber());
        assertEquals(1, f.member().getHeldItem().getId());
        assertArrayEquals(new int[] {1, 2, 3, 4}, f.member().getMoves());
        f.handler().saveTrainers();
        assertEquals(3, f.bytes()[DATA + ENTRY]);
        assertEquals(1, readWord(f.bytes(), partyOffset(f, 1) + 6));
        assertEquals(4, readWord(f.bytes(), partyOffset(f, 1) + 14));
        f.handler().loadTrainers();
        assertTrue(snapshots(f).isEmpty());
        f.member().setLevel(77);
        f.handler().saveTrainers();
        f.handler().loadTrainers();
        assertEquals(77, f.member().getLevel());
        assertArrayEquals(new int[] {1, 2, 3, 4}, f.member().getMoves());
        assertTrue(snapshots(f).isEmpty());
    }

    @Test
    void cfruNonCustomRowsDoNotAcquireSnapshots() throws Exception {
        Fixture f = fixture(true, 0x1234);
        f.bytes()[DATA + ENTRY] = 2;
        word(f.bytes(), PARTY + 6, 1);
        f.handler().loadTrainers();
        assertTrue(snapshots(f).isEmpty());
        f.handler().saveTrainers();
        assertEquals(2, f.bytes()[DATA + ENTRY]);
        f.handler().loadTrainers();
        assertEquals(1, f.member().getHeldItem().getId());
        assertTrue(snapshots(f).isEmpty());
    }

    private record Fixture(TestHandler handler, byte[] bytes) {
        Trainer trainer() { return handler.loadedTrainers().getFirst(); }
        TrainerPokemon member() { return trainer().getPokemon().getFirst(); }
    }

    private static Fixture fixture(boolean cfru, int opaque) throws Exception {
        byte[] bytes = new byte[0x4000];
        row(bytes, 1, PARTY, cfru, opaque);
        return load(cfru, bytes);
    }

    private static void row(byte[] bytes, int id, int party, boolean cfru, int opaque) {
        int header = DATA + id * ENTRY;
        bytes[header] = 3;
        bytes[header + 4] = (byte) 0xFF;
        bytes[header + 32] = 1;
        word(bytes, header + 36, party);
        bytes[header + 39] = 8;
        word(bytes, party, 255);
        word(bytes, party + 2, 66);
        word(bytes, party + 4, 131);
        if (cfru) {
            bytes[party + 6] = 2;
            bytes[party + 7] = 5;
            Arrays.fill(bytes, party + 8, party + 14, (byte) 31);
            for (int i = 0; i < 6; i++) bytes[party + 14 + i] = (byte) (10 + i);
            word(bytes, party + OPAQUE, opaque);
        }
        word(bytes, party + (cfru ? 20 : 6), 1);
        for (int i = 0; i < 4; i++) word(bytes, party + (cfru ? 22 : 8) + i * 2, i + 1);
    }

    private static Fixture load(boolean cfru, byte[] bytes) throws Exception {
        TestHandler handler = new TestHandler();
        Gen3RomEntry entry = new Gen3RomEntry(
                Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        entry.setRomType(Gen3Constants.RomType_FRLG);
        entry.putIntValue("TrainerData", DATA);
        entry.putIntValue("TrainerCount", 2);
        entry.putIntValue("TrainerEntrySize", ENTRY);
        entry.putIntValue("TrainerNameLength", 12);
        setField(handler, "romEntry", entry);
        setField(handler, "rom", bytes);
        setField(handler, "useCfruDpeGen9SpeciesCount", cfru);
        handler.species[131] = new Species(131);
        handler.species[132] = new Species(132);
        setField(handler, "pokesInternal", handler.species);
        int[] identity = new int[200];
        for (int i = 0; i < identity.length; i++) identity[i] = i;
        setField(handler, "pokedexToInternal", identity);
        handler.itemTable.add(null);
        handler.itemTable.add(new Item(1, "Synthetic item 1"));
        handler.itemTable.add(new Item(2, "Synthetic item 2"));
        setField(handler, "items", handler.itemTable);
        handler.initTextTables();
        // The fresh handler must not mark the saved party as free space.
        int savedParty = readWord(bytes, DATA + ENTRY + 36)
                | ((bytes[DATA + ENTRY + 38] & 0xFF) << 16);
        int freeStart = Math.max(0x1000, savedParty + 0x100);
        handler.freeSpace(freeStart, bytes.length - freeStart);
        handler.loadTrainers();
        return new Fixture(handler, bytes);
    }

    private static void assertModeled(TrainerPokemon p) {
        assertEquals(66, p.getLevel());
        assertEquals(131, p.getSpecies().getNumber());
        assertEquals(1, p.getHeldItem().getId());
        assertArrayEquals(new int[] {1, 2, 3, 4}, p.getMoves());
        assertEquals(2, p.getAbilitySlot());
        assertEquals(5, p.getNature());
        assertEquals(31, p.getIVs());
        assertArrayEquals(new byte[] {10, 11, 12, 13, 14, 15}, evs(p));
    }

    private static byte[] evs(TrainerPokemon p) {
        return new byte[] {p.getHpEVs(), p.getAtkEVs(), p.getDefEVs(), p.getSpeedEVs(),
                p.getSpatkEVs(), p.getSpdefEVs()};
    }

    private static int partyOffset(Fixture f, int id) {
        int header = DATA + id * ENTRY;
        return readWord(f.bytes(), header + 36) | ((f.bytes()[header + 38] & 0xFF) << 16);
    }

    private static void assertOpaque(Fixture f, int id, int... expected) {
        int header = DATA + id * ENTRY;
        assertEquals(expected.length, f.bytes()[header + 32] & 0xFF);
        assertEquals(3, f.bytes()[header]);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], readWord(f.bytes(), partyOffset(f, id) + i * 32 + OPAQUE));
        }
    }

    private static int readWord(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    @SuppressWarnings("unchecked")
    private static Map<TrainerPokemon, Integer> snapshots(Fixture f) throws Exception {
        Field field = Gen3RomHandler.class.getDeclaredField("originalCfruDpeTrainerOpaqueWords");
        field.setAccessible(true);
        return (Map<TrainerPokemon, Integer>) field.get(f.handler());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Byte array is inherited.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static class TestHandler extends Gen3RomHandler {
        final Species[] species = new Species[200];
        final List<Item> itemTable = new ArrayList<>();
        @Override public List<String> getTrainerClassNames() { return List.of("Synthetic class"); }
        List<Trainer> loadedTrainers() { return trainers; }
    }
}
