package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic in-memory trainer rows; no ROM files or runtime required. */
class Gen3TrainerMovesetIdentityTest {
    private static final int DATA = 0x100, ENTRY = 40, PARTY = 0x400;
    private static final int DEX = 906, INTERNAL = 1294;
    private static final int[] CORRECT = {10, 20, 30, 0}, WRONG = {40, 50, 60, 0};
    private static final Map<Integer, List<MoveLearnt>> LEARNSETS = Map.of(
            DEX, List.of(new MoveLearnt(40, 1), new MoveLearnt(50, 3), new MoveLearnt(60, 5)),
            INTERNAL, List.of(new MoveLearnt(10, 1), new MoveLearnt(20, 3), new MoveLearnt(30, 5)),
            25, List.of(new MoveLearnt(70, 1)));

    @Test
    void explicitSpeciesWinsCollisionAndMissingInternalKeyDoesNotFallBackToDex() throws Exception {
        Fixture f = fixture(true, 3, false);
        assertArrayEquals(CORRECT, f.handler.getMovesAtLevel(f.selected, LEARNSETS, 5));
        assertArrayEquals(new int[4], f.handler.getMovesAtLevel(f.selected,
                Map.of(DEX, LEARNSETS.get(DEX)), 5));
        assertArrayEquals(new int[4], f.handler.getMovesAtLevel(f.selected, null, 5));
    }

    @Test
    void integerApiKeepsDirectInternalKeysAndExistingMissingKeyFallback() throws Exception {
        Fixture f = fixture(true, 3, false);
        assertArrayEquals(WRONG, f.handler.getMovesAtLevel(DEX, LEARNSETS, 5));
        assertArrayEquals(CORRECT, f.handler.getMovesAtLevel(INTERNAL, LEARNSETS, 5));
        assertArrayEquals(CORRECT, f.handler.getMovesAtLevel(DEX,
                Map.of(INTERNAL, LEARNSETS.get(INTERNAL)), 5));
    }

    @Test
    void ordinaryAndVanillaSpeciesKeepDexLearnsetsAndVanillaWriterLayout() throws Exception {
        Fixture extended = fixture(true, 3, false);
        assertArrayEquals(new int[] {70, 0, 0, 0},
                extended.handler.getMovesAtLevel(new Species(25), LEARNSETS, 5));
        Fixture vanilla = fixture(false, 3, false);
        assertArrayEquals(WRONG, vanilla.handler.getMovesAtLevel(vanilla.selected, LEARNSETS, 5));
        replace(vanilla, 1);
        vanilla.handler.saveTrainers();
        assertSerialized(vanilla, 1, WRONG, 16, 8);
    }

    @Test
    void genericDefaultUsesDexIdentity() {
        Species species = selectedSpecies();
        RomHandler handler = (RomHandler) Proxy.newProxyInstance(RomHandler.class.getClassLoader(),
                new Class<?>[] {RomHandler.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    assertEquals("getMovesAtLevel", method.getName());
                    assertEquals(DEX, args[0]);
                    return new Gen3RomHandler().getMovesAtLevel((int) args[0], LEARNSETS, (int) args[2]);
                });
        assertArrayEquals(WRONG, handler.getMovesAtLevel(species, LEARNSETS, 5));
    }

    @Test
    void actualSerializerHandlesItemCustomAndNormalCustomCollisionThroughSaveReloadSave() throws Exception {
        for (int flags : new int[] {3, 1}) {
            Fixture f = fixture(true, flags, false);
            replace(f, 1);
            f.handler.saveTrainers();
            assertSerialized(f, 1, CORRECT, flags == 3 ? 32 : 16, flags == 3 ? 22 : 6);
            f.handler.saveTrainers();
            f.handler.loadTrainers();
            TrainerPokemon reloaded = f.handler.loadedTrainers().getFirst().getPokemon().getFirst();
            assertSame(f.selected, reloaded.getSpecies());
            assertArrayEquals(CORRECT, reloaded.getMoves());
            f.handler.saveTrainers();
            assertSerialized(f, 1, CORRECT, flags == 3 ? 32 : 16, flags == 3 ? 22 : 6);
            // Also regenerate after reload, rather than only retaining the serialized slots.
            reloaded.setResetMoves(true);
            f.handler.saveTrainers();
            assertSerialized(f, 1, CORRECT, flags == 3 ? 32 : 16, flags == 3 ? 22 : 6);
        }
    }

    @Test
    void emptyCustomMoveRecoveryUsesSelectedInternalIdentity() throws Exception {
        Fixture f = fixture(true, 3, false);
        replace(f, 1);
        TrainerPokemon p = f.handler.loadedTrainers().getFirst().getPokemon().getFirst();
        p.setResetMoves(false);
        p.setMoves(new int[4]);
        f.handler.saveTrainers();
        assertTrue(p.isResetMoves());
        assertSerialized(f, 1, CORRECT, 32, 22);
    }

    @Test
    void actualFrlgRuntimeSourceWriterUsesSelectedInternalIdentityAndSurvivesReload() throws Exception {
        Fixture f = fixture(true, 3, true);
        assertEquals(2, f.handler.loadedTrainers().size());
        assertEquals(3, f.handler.loadedTrainers().getLast().getIndex());
        replace(f, 3);
        f.handler.saveTrainers();
        assertSerialized(f, 3, CORRECT, 32, 22);
        f.handler.loadTrainers();
        TrainerPokemon p = f.handler.loadedTrainers().getLast().getPokemon().getFirst();
        assertSame(f.selected, p.getSpecies());
        assertArrayEquals(CORRECT, p.getMoves());
        p.setResetMoves(true);
        f.handler.saveTrainers();
        assertSerialized(f, 3, CORRECT, 32, 22);
    }

    private static void replace(Fixture f, int id) {
        Trainer trainer = f.handler.loadedTrainers().stream().filter(t -> t.getIndex() == id)
                .findFirst().orElseThrow();
        TrainerPokemon p = trainer.getPokemon().getFirst();
        // A retained custom member makes the party use the custom serializer even
        // though the replaced member itself requests level-up move regeneration.
        TrainerPokemon retained = new TrainerPokemon();
        retained.setSpecies(p.getSpecies());
        retained.setLevel(5);
        retained.setMoves(new int[] {1, 2, 3, 4});
        trainer.getPokemon().add(retained);
        assertNotSame(f.selected, p.getSpecies());
        p.setSpecies(f.selected);
        p.setLevel(5);
        p.setResetMoves(true);
    }

    private record Fixture(TestHandler handler, byte[] bytes, Species selected) { }

    private static Fixture fixture(boolean cfru, int flags, boolean runtime) throws Exception {
        TestHandler handler = new TestHandler();
        Gen3RomEntry entry = new Gen3RomEntry(
                Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        entry.setRomCode("BPRE");
        entry.setRomType(Gen3Constants.RomType_FRLG);
        entry.putIntValue("PokemonCount", cfru ? 1439 : 386);
        entry.putIntValue("TrainerData", DATA);
        entry.putIntValue("TrainerCount", 2);
        entry.putIntValue("TrainerEntrySize", ENTRY);
        entry.putIntValue("TrainerNameLength", 12);
        byte[] bytes = new byte[0x4000];
        row(bytes, 1, PARTY, cfru, flags);
        if (runtime) {
            row(bytes, 3, 0x500, cfru, flags);
            bytes[0x20] = 0x5C;
            word(bytes, 0x22, 3);
        }
        Species selected = selectedSpecies();
        Species[] internal = new Species[1440];
        // Runtime discovery still validates expanded parties through classic
        // stride 16; the held-item word is seen as the second Species there.
        internal[1] = new Species(1);
        internal[25] = new Species(25);
        internal[INTERNAL] = selected;
        int[] dexToInternal = new int[1440];
        dexToInternal[25] = 25;
        dexToInternal[DEX] = INTERNAL;
        setField(handler, "romEntry", entry);
        setField(handler, "rom", bytes);
        setField(handler, "isRomHack", cfru);
        setField(handler, "useCfruDpeGen9SpeciesCount", cfru);
        setField(handler, "pokesInternal", internal);
        setField(handler, "pokedexToInternal", dexToInternal);
        // Existing runtime discovery prechecks classic offset 6/stride 16 even
        // for expanded rows. Keep synthetic move values admissible item indices.
        List<Item> items = new ArrayList<>(java.util.Collections.nCopies(100, null));
        items.set(1, new Item(1, "Item"));
        setField(handler, "items", items);
        handler.initTextTables();
        handler.freeSpace(0x1000, bytes.length - 0x1000);
        handler.loadTrainers();
        return new Fixture(handler, bytes, selected);
    }

    private static Species selectedSpecies() {
        Species s = new Species(DEX);
        s.setSpeciesSetIdentityNumber(INTERNAL);
        return s;
    }

    private static void row(byte[] bytes, int id, int party, boolean cfru, int flags) {
        int header = DATA + id * ENTRY;
        bytes[header] = (byte) flags;
        bytes[header + 4] = (byte) 0xFF;
        bytes[header + 32] = 1;
        word(bytes, header + 36, party);
        bytes[header + 39] = 8;
        word(bytes, party, 255);
        word(bytes, party + 2, 5);
        word(bytes, party + 4, 25);
        // Runtime discovery also checks offset 6 for an admissible held item.
        word(bytes, party + 6, 1);
        int moves = flags == 3 ? (cfru ? 22 : 8) : 6;
        if (flags == 3) word(bytes, party + (cfru ? 20 : 6), 1);
        for (int i = 0; i < 4; i++) word(bytes, party + moves + i * 2, i + 1);
    }

    private static void assertSerialized(Fixture f, int id, int[] expected, int stride, int movesOffset) {
        int header = DATA + id * ENTRY;
        int party = readWord(f.bytes, header + 36) | ((f.bytes[header + 38] & 0xFF) << 16);
        assertEquals(INTERNAL, readWord(f.bytes, party + 4));
        assertEquals(stride, Gen3RomHandler.trainerPokemonStride(f.bytes[header], stride == 32));
        int[] actual = new int[4];
        for (int i = 0; i < 4; i++) actual[i] = readWord(f.bytes, party + movesOffset + i * 2);
        assertArrayEquals(expected, actual);
    }

    private static int readWord(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static class TestHandler extends Gen3RomHandler {
        @Override public List<String> getTrainerClassNames() { return List.of("Synthetic class"); }
        @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() { return LEARNSETS; }
        List<Trainer> loadedTrainers() { return trainers; }
    }
}
