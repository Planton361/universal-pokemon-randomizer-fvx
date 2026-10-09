package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpeEvolutionPreservationTest {
    private static final int ROW_SIZE = 16 * 8;

    private static boolean timeMethod(int method) {
        return java.util.Set.of(2, 3, 22, 23, 24, 25, 28, 39).contains(method);
    }

    private static int[] timeFreeWords(CfruDpeEvolutionFixture.SourceSlot slot) {
        int method = slot.method(), parameter = slot.parameter(), auxiliary = slot.auxiliary();
        boolean paired = java.util.Set.of(104, 133, 961, 1007).contains(slot.source());
        if (paired && method != 28) { parameter = method == 2 || method == 23 ? 93 : 94; method = 7; }
        else switch (method) {
            case 2, 3 -> method = 1;
            case 22, 23 -> method = 4;
            case 24, 25 -> { method = 35; auxiliary = parameter; parameter = 1; }
            case 28 -> { method = 7; parameter = 100; auxiliary = 0; }
            case 39 -> method = 7;
            default -> throw new AssertionError(slot);
        }
        return new int[] {method, parameter, slot.target(), auxiliary};
    }

    @Test
    void all26TimeSlotsAndEveryWordOfThe1440By16TableRoundTripWithoutUnownedWrites() throws Exception {
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        // Unrelated opaque rows retain holes, duplicates, empty metadata and full-width values.
        f.entry(1, 5, 0, 0xBEEF, 0, 0xFE);
        f.entry(1, 7, 0xFFFF, 0xABCD, 19, 0xFEDC);
        f.entry(1, 10, 4, 16, 2, 0); f.entry(1, 12, 4, 16, 2, 0);
        f.loadEvolutions();
        byte[] before = f.memory.clone(), expected = before.clone();
        var slots = CfruDpeEvolutionFixture.inventory().stream().filter(slot -> timeMethod(slot.method())).toList();
        assertEquals(26, slots.size()); assertEquals(21, slots.stream().map(CfruDpeEvolutionFixture.SourceSlot::source).distinct().count());
        for (var slot : slots) {
            int[] words = timeFreeWords(slot);
            for (int field : new int[] {0, 2, 6}) {
                int o = CfruDpeEvolutionFixture.offset(slot.source(), slot.slot()) + field;
                expected[o] = (byte) words[field / 2]; expected[o + 1] = (byte) (words[field / 2] >>> 8);
            }
        }
        f.preflightCfruDpeTimeEvolutions(); assertArrayEquals(before, f.memory);
        f.removeTimeBasedEvolutions(); assertArrayEquals(before, f.memory, "only stage bytes after full preflight");
        assertConvertedModels(f, slots);
        f.preflightCfruDpeTimeEvolutions(); f.write(); f.write();
        assertArrayEquals(expected, f.memory, "entire synthetic memory, all table words and both boundaries");
        f.loadEvolutions(); assertConvertedModels(f, slots);
        f.removeTimeBasedEvolutions(); f.write(); f.loadEvolutions(); f.write();
        assertArrayEquals(expected, f.memory, "reload, reapply and repeated save must be idempotent");
        for (int id : new int[] {104, 133, 961, 1007}) {
            assertEquals(93, f.word(id, 0, 2)); assertEquals(94, f.word(id, 1, 2));
            assertNotEquals(f.word(id, 0, 4), f.word(id, 1, 4));
        }
        assertEquals(100, f.word(961, 2, 2)); assertEquals(0, f.word(961, 2, 6));
        assertEquals(734, f.word(217, 0, 2));
        assertEquals(17, f.word(133, 2, 0), "Sylveon remains Fairy-move based");
        assertEquals(253, f.word(133, 8, 0), "Eevee transformation unchanged");
    }

    private static void assertConvertedModels(CfruDpeEvolutionFixture f,
                                              List<CfruDpeEvolutionFixture.SourceSlot> slots) {
        for (var slot : slots) {
            int[] words = timeFreeWords(slot);
            EvolutionType type = words[0] == 35 ? EvolutionType.ITEM
                    : com.uprfvx.romio.constants.Gen3Constants.evolutionTypeFromIndex(words[0]);
            int extra = words[0] == 35 ? words[3] : words[1];
            if (type.usesItem()) extra = com.uprfvx.romio.constants.Gen3Constants.itemIDToStandard(extra);
            int parameter = extra;
            assertTrue(f.species[slot.source()].getEvolutionsFrom().stream().anyMatch(edge ->
                    edge.getTo() == f.species[slot.target()] && edge.getType() == type && edge.getExtraInfo() == parameter), slot.toString());
        }
    }

    @Test
    void unexpectedTimeOwnersShapesPairsTargetsAndCollisionsRejectTheWholePlan() throws Exception {
        for (int mode = 0; mode < 17; mode++) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            switch (mode) {
                case 0 -> f.entry(1, 3, 22, 25, 2, 0); // unexpected owner
                case 1 -> f.entry(961, 2, 28, 25, 1082, 0x1112); // different Dusk range
                case 2 -> f.entry(1365, 0, 22, 30, 1366, 1); // late auxiliary
                case 3 -> f.entry(104, 1, 23, 28, 1039, 0); // wrong pair
                case 4 -> f.entry(133, 1, 3, 0, 196, 0); // duplicate target
                case 5 -> f.entry(961, 4, 7, 100, 2, 0); // Dusk stone collision
                case 6 -> f.entry(133, 12, 34, 93, 2, 0); // conditional Sun stone collision
                case 7 -> f.entry(1365, 0, 22, 30, 1440, 0); // null target
                case 8 -> f.entry(207, 0, 24, 119, 525, 0); // wrong held item
                case 9 -> f.entry(217, 0, 39, 735, 1253, 0); // wrong Peat block
                case 10 -> f.entry(104, 1, 0, 0, 0, 0); // missing paired slot
                case 11 -> f.entry(1, 4, 28, 25, 2, 0x1114); // wrong Dusk owner
                case 12 -> f.entry(961, 6, 22, 25, 1082, 0); // duplicate time source slot
                case 13 -> f.entry(0, 0, 23, 25, 2, 0); // source-zero placeholder
                case 14 -> f.entry(207, 1, 35, 1, 2, 120); // same held-item level-up route
                case 15 -> f.entry(459, 1, 1, 0, 2, 0); // friendship shadow
                case 16 -> f.entry(1365, 1, 4, 30, 2, 0); // same level route
            }
            f.loadEvolutions(); byte[] before = f.memory.clone();
            var graph = graphSnapshot(f);
            assertThrows(RomIOException.class, f::preflightCfruDpeTimeEvolutions, "mode " + mode);
            assertThrows(RomIOException.class, f::removeTimeBasedEvolutions, "mode " + mode);
            assertArrayEquals(before, f.memory); assertEquals(graph, graphSnapshot(f));
            assertTrue(f.getPreImprovedEvolutions().isEmpty());
            f.write(); assertArrayEquals(before, f.memory, "unchanged remains supported");
        }
    }

    private static List<List<Evolution>> graphSnapshot(CfruDpeEvolutionFixture f) {
        return f.getSpecies().stream().filter(java.util.Objects::nonNull)
                .map(sp -> List.copyOf(sp.getEvolutionsFrom())).toList();
    }

    @Test
    void staleRawGraphEditsAndTruncatedMemoryFailBeforeAnyWrite() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            if (mode == 0) f.entry(1365, 0, 22, 30, 1366, 1); // outside writer after load
            if (mode == 1) f.setField("rom", Arrays.copyOf(f.memory, CfruDpeEvolutionFixture.offset(1439, 15) + 7));
            if (mode >= 2) {
                f.removeTimeBasedEvolutions();
                var edge = f.species[459].getEvolutionsFrom().getFirst(); // initially fully modeled
                if (mode == 2) edge.updateEvolutionMethod(EvolutionType.LEVEL, 99);
                else edge.setTo(f.species[2]);
            }
            byte[] before = f.memory.clone(); var graph = graphSnapshot(f);
            assertThrows(RomIOException.class, f::preflightCfruDpeTimeEvolutions);
            assertThrows(RomIOException.class, f::removeTimeBasedEvolutions);
            assertThrows(RomIOException.class, f::preflightSave);
            assertThrows(RomIOException.class, f::write);
            assertArrayEquals(before, f.memory); assertEquals(graph, graphSnapshot(f));
        }
    }

    @Test
    void timePlanningPreservesModeledLevelHintsAndKeepsRawOnlyMetadataOpaque() throws Exception {
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        f.species[133].getEvolutionsFrom().forEach(edge -> edge.setEstimatedEvoLvl(42));
        f.species[459].getEvolutionsFrom().getFirst().setEstimatedEvoLvl(30);
        f.removeTimeBasedEvolutions();
        assertTrue(f.species[133].getEvolutionsFrom().stream().allMatch(edge -> edge.getEstimatedEvoLvl() == 42));
        assertEquals(30, f.species[459].getEvolutionsFrom().getFirst().getEstimatedEvoLvl());
        for (int id : new int[] {104, 961, 1007}) {
            int expected = id == 104 ? 28 : id == 961 ? 25 : 53;
            assertTrue(f.species[id].getEvolutionsFrom().stream().allMatch(edge -> edge.getEstimatedEvoLvl() == expected));
        }
        assertEquals(7, f.species[133].getEvolutionsFrom().size(), "Fairy move and Gmax retain their opaque raw ownership");
        f.write(); f.loadEvolutions(); f.write();
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> everyExactTimeSlotRejectsUnownedParameterAuxiliaryMethodAndTarget() {
        return CfruDpeEvolutionFixture.inventory().stream().filter(slot -> timeMethod(slot.method())).map(slot ->
                DynamicTest.dynamicTest(slot.sourceName() + "/" + slot.slot() + "/" + slot.methodName(), () -> {
                    for (int field = 0; field < 5; field++) {
                        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
                        int method = slot.method(), parameter = slot.parameter(), target = slot.target(), auxiliary = slot.auxiliary();
                        switch (field) {
                            case 0 -> method = 0xFFFF;
                            case 1 -> parameter ^= 1;
                            case 2 -> auxiliary ^= 1;
                            case 3 -> target = 0;
                            case 4 -> target = 0xFFFF;
                        }
                        f.entry(slot.source(), slot.slot(), method, parameter, target, auxiliary); f.loadEvolutions();
                        byte[] before = f.memory.clone(); var graph = graphSnapshot(f);
                        assertThrows(RomIOException.class, f::preflightCfruDpeTimeEvolutions, "field " + field);
                        assertThrows(RomIOException.class, f::removeTimeBasedEvolutions, "field " + field);
                        assertEquals(graph, graphSnapshot(f)); assertArrayEquals(before, f.memory);
                    }
                }));
    }

    @Test
    void foreignProfileAndMissingSourceRejectAndNativeLevelHeldItemRemainsOpaque() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            if (mode < 2) {
                Field field = Gen3RomHandler.class.getDeclaredField("romEntry"); field.setAccessible(true);
                var entry = (Gen3RomEntry) field.get(f);
                if (mode == 0) entry.setRomCode("BPEE"); else entry.putIntValue("PokemonCount", 1500);
            } else {
                var species = new ArrayList<>(f.getSpecies()); species.remove(f.species[1365]);
                f.setField("speciesList", species); f.setField("numRealPokemon", species.size() - 1);
            }
            byte[] before = f.memory.clone();
            assertThrows(RomIOException.class, f::preflightCfruDpeTimeEvolutions);
            assertThrows(RomIOException.class, f::removeTimeBasedEvolutions);
            assertArrayEquals(before, f.memory);
        }
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        f.entry(1, 8, 35, 1, 2, 120); f.loadEvolutions();
        assertEquals(1, f.species[1].getEvolutionsFrom().size(), "native extended method outside converted owners stays opaque");
        byte[] before = f.memory.clone(); f.write(); assertArrayEquals(before, f.memory);
    }

    @Test
    void actualWriteCallsTouchOnlyOwnedTimeWordsAndPreserveInverseModelLinks() throws Exception {
        var f = new FailingTimeFixture(); f.populateExactSource(); f.removeTimeBasedEvolutions();
        f.addresses.clear(); f.write();
        var owned = new java.util.HashSet<Integer>();
        for (var slot : CfruDpeEvolutionFixture.inventory()) if (timeMethod(slot.method())) {
            for (int field : new int[] {0, 2, 6}) owned.add(CfruDpeEvolutionFixture.offset(slot.source(), slot.slot()) + field);
        }
        assertFalse(f.addresses.isEmpty()); assertTrue(owned.containsAll(f.addresses));
        for (Species source : f.species) if (source != null) {
            for (Evolution edge : source.getEvolutionsFrom()) assertTrue(edge.getTo().getEvolutionsTo().contains(edge));
            for (Evolution edge : source.getEvolutionsTo()) assertTrue(edge.getFrom().getEvolutionsFrom().contains(edge));
        }
        f.addresses.clear(); f.write(); assertTrue(f.addresses.isEmpty(), "unchanged save performs no evolution writes");
    }

    private static class FailingTimeFixture extends CfruDpeEvolutionFixture {
        int writes, failAt = Integer.MAX_VALUE;
        boolean corrupt;
        final java.util.Set<Integer> addresses = new java.util.HashSet<>();
        FailingTimeFixture() throws Exception { super(); }
        @Override protected void writeWord(int offset, int value) {
            super.writeWord(offset, value);
            // The superclass constructor uses writeWord before this field initializes.
            if (addresses != null) addresses.add(offset);
            if (++writes == failAt) {
                if (corrupt) super.writeWord(offset, value ^ 1);
                else throw new RomIOException("synthetic deterministic write failure");
            }
        }
    }

    @Test
    void lateWriteExceptionsAndReadbackMismatchRestoreAllRowsAndAllowRetry() throws Exception {
        for (boolean corrupt : List.of(false, true)) {
            var f = new FailingTimeFixture(); f.populateExactSource();
            f.removeTimeBasedEvolutions(); byte[] before = f.memory.clone(); var graph = graphSnapshot(f);
            f.writes = 0; f.failAt = 28; f.corrupt = corrupt;
            assertThrows(RomIOException.class, f::write);
            assertArrayEquals(before, f.memory); assertEquals(graph, graphSnapshot(f));
            f.failAt = Integer.MAX_VALUE; f.preflightCfruDpeTimeEvolutions(); f.write();
            assertEquals(4, f.word(1365, 0, 0));
            assertEquals(35, f.word(207, 0, 0));
            f.loadEvolutions(); f.write();
        }
    }

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
