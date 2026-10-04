package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpeEvolutionPreservationTest {
    private static final int ROW_SIZE = 16 * 8;

    @Test
    void unchangedPreservesFroslassAndMixedSlotOrderAcrossRepeatedWritesAndReloads() throws Exception {
        Fixture f = fixture(true, 531);
        // Exact pinned DPE identities: Snorunt -> Glalie / Froslass, Dawn Stone = 101.
        entry(f, 346, 0, 4, 42, 347, 0);
        entry(f, 346, 1, 7, 101, 531, 0xFE);
        entry(f, 346, 3, 23, 28, 105, 0x1234);
        entry(f, 346, 15, 0xFFFF, 0xFFFF, 531, 0xABCD);
        // Auxiliary data is unowned even on an otherwise ordinary method.
        entry(f, 104, 0, 4, 28, 105, 0x5678);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(2, f.species()[346].getEvolutionsFrom().size());
        assertEquals(531, f.species()[346].getEvolutionsFrom().get(1).getTo().getSpeciesSetIdentityNumber());
        write(f);
        write(f);
        f.handler().loadEvolutions();
        write(f);
        assertArrayEquals(before, f.bytes());
        assertEquals(0xFE, word(f, 346, 1, 6));
        assertEquals(0x5678, word(f, 104, 0, 6));
        assertEquals(2, f.species()[346].getEvolutionsFrom().size());
    }

    @Test
    void all272CurrentOpaqueMethodRowsSurviveUnchanged() throws Exception {
        // Exact method histogram from DPE d887185... Evolution Table.c (#636):
        // 79 extended ordinary rows + 89 Gigantamax + 104 Mega = 272.
        int[][] domain = {
            {16,2}, {17,1}, {18,1}, {19,7}, {20,5}, {21,7}, {22,8}, {23,6},
            {24,2}, {25,2}, {26,18}, {27,1}, {28,1}, {30,1}, {31,1}, {32,1},
            {34,1}, {35,6}, {36,1}, {37,1}, {38,1}, {39,1}, {40,2}, {41,1},
            {42,1}, {253,89}, {254,104}
        };
        Fixture f = fixture(true, 19);
        int count = 0;
        for (int[] method : domain) {
            for (int i = 0; i < method[1]; i++) {
                // Synthetic, deterministic, varied full-width parameters/auxiliary fields.
                entry(f, 1 + count / 16, count % 16, method[0],
                        (count * 257) & 0xFFFF, 19, (0xFE + count * 509) & 0xFFFF);
                count++;
            }
        }
        assertEquals(272, count);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        for (Species s : f.species()) {
            if (s != null) assertTrue(s.getEvolutionsFrom().isEmpty());
        }
        write(f);
        f.handler().loadEvolutions();
        write(f);
        assertArrayEquals(before, f.bytes(), "all 272 encoded slots, including their raw fields and order");
    }

    @Test
    void allSupportedMethodsRemainModeledAndWritable() throws Exception {
        Fixture f = fixture(true, 2);
        for (int method = 1; method <= 15; method++) entry(f, 1, method - 1, method, 30, 2, 0);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(15, f.species()[1].getEvolutionsFrom().size());
        write(f);
        f.handler().loadEvolutions();
        assertEquals(15, f.species()[1].getEvolutionsFrom().size());
        assertArrayEquals(before, f.bytes());
        for (Evolution evo : f.species()[1].getEvolutionsFrom()) {
            evo.updateEvolutionMethod(evo.getType(), 31);
        }
        write(f);
        f.handler().loadEvolutions();
        for (Evolution evo : f.species()[1].getEvolutionsFrom()) assertEquals(31, evo.getExtraInfo());
    }

    @Test
    void holesDuplicateRelationshipsInvalidTargetsAndEmptySlotMetadataArePreserved() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 1, 2, 4, 16, 2, 0); // loader deduplicates; raw writer must not
        entry(f, 1, 4, 4, 20, 3, 0); // valid numeric target but null species
        entry(f, 1, 5, 4, 20, 1441, 0); // out-of-domain target
        entry(f, 1, 7, 0, 0xBEEF, 0, 0x1234); // method-zero metadata
        entry(f, 1, 10, 0x8000, 0xABCD, 0, 0xFEDC); // unknown encoding
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(1, f.species()[1].getEvolutionsFrom().size());
        write(f);
        f.handler().loadEvolutions();
        assertArrayEquals(before, f.bytes());
        assertEquals(1, f.species()[1].getEvolutionsFrom().size());
    }

    @Test
    void supportedMutationAndListReplacementWorkOnFullyModeledRows() throws Exception {
        Fixture f = fixture(true, 3);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 2, 0, 5, 0, 3, 0);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 32);
        // Use the existing impossible-evolution decision, not a test-only mutation model.
        assertTrue(Gen3RomHandler.updateImpossibleEvolution(f.species()[2].getEvolutionsFrom().getFirst(), true, false));
        write(f);
        write(f); // successful writes refresh snapshots for repeated saves
        assertEquals(32, word(f, 1, 0, 2));
        assertEquals(4, word(f, 2, 0, 0));
        assertEquals(37, word(f, 2, 0, 2));
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().clear();
        f.species()[1].getEvolutionsFrom().add(new Evolution(f.species()[1], f.species()[3], EvolutionType.LEVEL, 40));
        f.species()[1].getEvolutionsFrom().add(new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL, 45));
        write(f);
        f.handler().loadEvolutions();
        assertEquals(2, f.species()[1].getEvolutionsFrom().size());
        assertEquals(3, word(f, 1, 0, 4));
        assertEquals(2, word(f, 1, 1, 4));
        f.species()[1].getEvolutionsFrom().clear();
        write(f);
        for (int i = ROW_SIZE; i < 2 * ROW_SIZE; i++) assertEquals(0, f.bytes()[i]);
    }

    @Test
    void unrelatedOpaqueSpeciesDoesNotDisableSupportedMutation() throws Exception {
        Fixture f = fixture(true, 3);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 2, 7, 254, 0x1234, 3, 0xABCD);
        byte[] opaque = Arrays.copyOfRange(f.bytes(), 2 * ROW_SIZE, 3 * ROW_SIZE);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
        write(f);
        assertEquals(20, word(f, 1, 0, 2));
        assertArrayEquals(opaque, Arrays.copyOfRange(f.bytes(), 2 * ROW_SIZE, 3 * ROW_SIZE));
    }

    @Test
    void unsafeMutationFailsBeforeAnySpeciesStatsOrEvolutionWrites() throws Exception {
        for (boolean auxiliaryOnly : List.of(false, true)) {
            Fixture f = fixture(true, 3);
            entry(f, 1, 0, 4, 16, 2, 0); // safe row processed before unsafe row
            entry(f, 2, 0, 4, 30, 3, auxiliaryOnly ? 0xFE : 0);
            if (!auxiliaryOnly) entry(f, 2, 1, 23, 28, 3, 0);
            f.handler().loadEvolutions();
            f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
            f.species()[2].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 40);
            byte[] before = f.bytes().clone();
            RomIOException error = assertThrows(RomIOException.class, f.handler()::saveSpeciesStats);
            assertTrue(error.getMessage().contains("Unchanged"));
            assertArrayEquals(before, f.bytes(), "preflight before names/stats as well as earlier safe rows");
            assertThrows(RomIOException.class, () -> write(f));
            assertArrayEquals(before, f.bytes());
            Method prepare = AbstractRomHandler.class.getDeclaredMethod("prepareSaveRom");
            assertThrows(RomIOException.class, () -> invoke(prepare, f.handler()));
            assertArrayEquals(before, f.bytes(), "ordinary save preparation must reject before any output writer");
        }
    }

    @Test
    void removalAdditionReorderingAndTargetChangesCannotRepackOpaqueRows() throws Exception {
        for (int operation = 0; operation < 4; operation++) {
            Fixture f = fixture(true, 3);
            entry(f, 1, 0, 4, 16, 2, 0);
            entry(f, 1, 1, 4, 30, 3, 0);
            entry(f, 1, 4, 254, 10, 3, 0x1234);
            f.handler().loadEvolutions();
            List<Evolution> evolutions = f.species()[1].getEvolutionsFrom();
            switch (operation) {
                case 0 -> evolutions.removeFirst();
                case 1 -> evolutions.add(new Evolution(f.species()[1], f.species()[2], EvolutionType.TRADE, 0));
                case 2 -> java.util.Collections.reverse(evolutions);
                case 3 -> evolutions.getFirst().setTo(f.species()[3]);
            }
            byte[] before = f.bytes().clone();
            assertThrows(RomIOException.class, () -> write(f));
            assertArrayEquals(before, f.bytes());
        }
    }

    @Test
    void equivalentEvolutionCopiesAndEstimatedLevelChangesAreNotRawMutations() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0xFE);
        f.handler().loadEvolutions();
        Evolution copy = new Evolution(f.species()[1].getEvolutionsFrom().getFirst());
        copy.setEstimatedEvoLvl(99); // randomizer-only bookkeeping
        f.species()[1].getEvolutionsFrom().set(0, copy);
        byte[] before = f.bytes().clone();
        write(f);
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void unsupportedMethodCapacityAndMissingSnapshotFailClosed() throws Exception {
        Fixture f = fixture(true, 2);
        byte[] before = f.bytes().clone();
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
        f.handler().loadEvolutions();
        for (int i = 0; i < 17; i++) f.species()[1].getEvolutionsFrom().add(
                new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL, i));
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
        f.species()[1].getEvolutionsFrom().clear();
        f.species()[1].getEvolutionsFrom().add(
                new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL_FEMALE_ONLY, 20));
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void rawChangesOutsideTheWriterFailWithoutOverwritingThem() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0);
        f.handler().loadEvolutions();
        entry(f, 1, 1, 254, 10, 2, 0xFE);
        byte[] before = f.bytes().clone();
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void vanillaFiveSlotWriterRetainsItsExistingBehavior() throws Exception {
        Fixture f = fixture(false, 3);
        entry(f, 1, 0, 4, 16, 2, 0xFE);
        entry(f, 1, 1, 23, 28, 3, 0);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
        write(f);
        assertEquals(5, f.handler().getEvolutionSlotsPerSpeciesForDiagnostics());
        assertEquals(20, word(f, 1, 0, 2));
        assertEquals(0, word(f, 1, 0, 6));
        assertEquals(0, word(f, 1, 1, 0));
    }

    private record Fixture(Gen3RomHandler handler, byte[] bytes, Species[] species, int rowSize) {}

    private static Fixture fixture(boolean cfru, int count) throws Exception {
        Gen3RomHandler h = new Gen3RomHandler();
        Gen3RomEntry e = new Gen3RomEntry(Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        e.setRomCode("BPRE");
        e.putIntValue("PokemonCount", cfru ? 1440 : count);
        e.putIntValue("PokemonEvolutions", 0);
        int rowSize = cfru ? ROW_SIZE : 5 * 8;
        byte[] bytes = new byte[(count + 2) * rowSize];
        Arrays.fill(bytes, (count + 1) * rowSize, bytes.length, (byte) 0x5A); // neighbor canary
        Species[] species = new Species[(cfru ? 1440 : count) + 1];
        List<Species> list = new ArrayList<>();
        list.add(null);
        int[] mapping = new int[count + 1];
        for (int i = 1; i <= count; i++) {
            Species s = new Species(i);
            s.setName("Species" + i);
            s.setSpeciesSetIdentityNumber(i);
            species[i] = s;
            list.add(s);
            mapping[i] = i;
        }
        setField(h, "rom", bytes);
        setField(h, "romEntry", e);
        setField(h, "isRomHack", cfru);
        setField(h, "useCfruDpeGen9SpeciesCount", cfru);
        setField(h, "speciesList", list);
        setField(h, "numRealPokemon", count);
        setField(h, "pokes", species);
        setField(h, "pokesInternal", species);
        setField(h, "pokedexToInternal", mapping);
        return new Fixture(h, bytes, species, rowSize);
    }

    private static void entry(Fixture f, int species, int slot, int method, int parameter, int target, int auxiliary) {
        int offset = species * f.rowSize() + slot * 8;
        int[] values = {method, parameter, target, auxiliary};
        for (int i = 0; i < 4; i++) {
            f.bytes()[offset + 2 * i] = (byte) values[i];
            f.bytes()[offset + 2 * i + 1] = (byte) (values[i] >> 8);
        }
    }

    private static int word(Fixture f, int species, int slot, int field) {
        int o = species * f.rowSize() + slot * 8 + field;
        return (f.bytes()[o] & 0xFF) | ((f.bytes()[o + 1] & 0xFF) << 8);
    }

    private static void write(Fixture f) throws Exception {
        Method method = Gen3RomHandler.class.getDeclaredMethod("writeEvolutions");
        invoke(method, f.handler());
    }

    private static void invoke(Method method, Gen3RomHandler handler) throws Exception {
        method.setAccessible(true);
        try {
            method.invoke(handler);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw error;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // inherited ROM byte array
            }
        }
        throw new NoSuchFieldException(name);
    }
}
