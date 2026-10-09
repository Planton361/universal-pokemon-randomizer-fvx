package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CfruDpeEvolutionTargetTest {
    static Settings settings() {
        Settings s = new Settings(); s.setEvolutionsMod(Settings.EvolutionsMod.RANDOM);
        s.setEvosForceChange(true); return s;
    }

    @Test
    void exactTableMultipleSeedsChangesOnlySafeOrdinaryTargetsAndRoundTripsEveryWitness() throws Exception {
        Set<Integer> seen = new HashSet<>();
        for (long seed : new long[] {0, 1, 667, 20261006}) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            byte[] before = f.memory.clone();
            new EvolutionRandomizer(f, settings(), new Random(seed)).randomizeEvolutions();
            assertArrayEquals(before, f.memory, "planning must not write bytes");
            f.write();
            Set<Integer> ownedBytes = new HashSet<>();
            for (var row : CfruDpeEvolutionFixture.inventory()) {
                int offset = CfruDpeEvolutionFixture.offset(row.source(), row.slot());
                if (row.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE")
                        && f.getRestrictedSpeciesService().getAll(true).contains(f.species[row.source()])) {
                    int target = f.word(row.source(), row.slot(), 4);
                    assertNotEquals(row.target(), target, row.toString()); seen.add(target);
                    assertTrue(f.getCfruDpeRandomPoolEligibility(f.species[target], f.learnsets).eligible());
                    ownedBytes.add(offset + 4); ownedBytes.add(offset + 5);
                } else assertArrayEquals(Arrays.copyOfRange(before, offset, offset + 8), Arrays.copyOfRange(f.memory, offset, offset + 8), row.toString());
            }
            for (int i = 0; i < before.length; i++) if (!ownedBytes.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
            assertEquals(0xFE, f.word(346, 1, 6)); assertEquals(7, f.word(346, 1, 0)); assertEquals(101, f.word(346, 1, 2));
            byte[] saved = f.memory.clone();
            f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph();
            graph.forEach((source, edges) -> edges.forEach(e -> assertEquals(e.getTo().getSpeciesSetIdentityNumber(),
                    f.word(source.getSpeciesSetIdentityNumber(), e.getExtraInfo(), 4))));
            assertAcyclic(graph, 10);
            f.write(); f.write(); assertArrayEquals(saved, f.memory);
        }
        assertTrue(seen.stream().anyMatch(id -> id >= 0x50E && id <= 0x58E), "Gen9 targets");
        assertTrue(seen.stream().anyMatch(id -> id >= 0x3FC && id <= 0x417), "regional targets");
    }

    @Test
    void randomForceChangeThenRemoveTimePreservesSlotTargetsAcrossSeedsAndSaveReload() throws Exception {
        for (long seed : new long[] {0, 1, 723, 20261009, 680, 667}) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            byte[] before = f.memory.clone();
            f.preflightCfruDpeTimeEvolutions();
            new EvolutionRandomizer(f, settings(), new Random(seed)).randomizeEvolutions();
            f.preflightCfruDpeTimeEvolutions(); f.removeTimeBasedEvolutions();
            assertArrayEquals(before, f.memory);
            f.preflightCfruDpeTimeEvolutions(); f.write();
            Set<Integer> owned = new HashSet<>();
            for (var slot : CfruDpeEvolutionFixture.inventory()) {
                int o = CfruDpeEvolutionFixture.offset(slot.source(), slot.slot());
                if (slot.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE")
                        && f.getRestrictedSpeciesService().getAll(true).contains(f.species[slot.source()])) {
                    assertNotEquals(slot.target(), f.word(slot.source(), slot.slot(), 4));
                    owned.add(o + 4); owned.add(o + 5);
                }
                if (Set.of(2, 3, 22, 23, 24, 25, 28, 39).contains(slot.method())) {
                    for (int field : new int[] {0, 2, 6}) { owned.add(o + field); owned.add(o + field + 1); }
                    assertFalse(Set.of(2, 3, 22, 23, 24, 25, 28, 39).contains(f.word(slot.source(), slot.slot(), 0)));
                }
            }
            for (int i = 0; i < before.length; i++) if (!owned.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
            for (int id : new int[] {104, 133, 961, 1007}) {
                assertNotEquals(f.word(id, 0, 4), f.word(id, 1, 4));
                assertEquals(93, f.word(id, 0, 2)); assertEquals(94, f.word(id, 1, 2));
            }
            assertNotEquals(f.word(961, 0, 4), f.word(961, 2, 4));
            assertNotEquals(f.word(961, 1, 4), f.word(961, 2, 4));
            assertEquals(100, f.word(961, 2, 2));
            for (int id : new int[] {207, 215, 493, 1240}) {
                var edge = f.species[id].getEvolutionsFrom().getFirst();
                assertEquals(EvolutionType.ITEM, edge.getType());
                assertEquals(f.word(id, 0, 4), edge.getTo().getSpeciesSetIdentityNumber());
                assertTrue(edge.getTo().getEvolutionsTo().contains(edge));
            }
            byte[] saved = f.memory.clone(); f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph(); assertAcyclic(graph, 10);
            graph.forEach((source, edges) -> edges.forEach(edge -> assertEquals(
                    edge.getTo().getSpeciesSetIdentityNumber(), f.word(source.getSpeciesSetIdentityNumber(), edge.getExtraInfo(), 4))));
            f.removeTimeBasedEvolutions(); f.write(); f.write(); assertArrayEquals(saved, f.memory);
            // A second target randomization after reload also owns the converted ITEM graph.
            f.loadEvolutions(); new EvolutionRandomizer(f, settings(), new Random(seed + 1)).randomizeEvolutions();
            f.write();
            for (int id : new int[] {207, 215, 493, 1240}) {
                assertEquals(f.word(id, 0, 4), f.species[id].getEvolutionsFrom().getFirst().getTo().getSpeciesSetIdentityNumber());
            }
            f.loadEvolutions(); f.write();
        }
    }

    @Test
    void invalidTimePlanAfterStagedRandomTargetsLeavesTheEntireStagedGraphIntact() throws Exception {
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        new EvolutionRandomizer(f, settings(), new Random(723)).randomizeEvolutions();
        // Tampering after planning must fail before publishing even an earlier valid time row.
        f.entry(1365, 0, 22, 30, 1366, 1);
        byte[] before = f.memory.clone();
        var edges = List.copyOf(f.species[133].getEvolutionsFrom());
        assertThrows(RomIOException.class, f::removeTimeBasedEvolutions);
        assertEquals(edges, f.species[133].getEvolutionsFrom()); assertArrayEquals(before, f.memory);
        assertThrows(RomIOException.class, f::write); assertArrayEquals(before, f.memory);
    }

    static CfruDpeEvolutionFixture small() throws Exception {
        var f = new CfruDpeEvolutionFixture();
        f.pool.removeIf(sp -> sp.getSpeciesSetIdentityNumber() > 50);
        f.entry(1, 0, 16, 50, 2, 0);
        f.entry(4, 2, 18, 32, 5, 17);
        f.entry(7, 1, 22, 25, 8, 0); f.entry(7, 3, 23, 25, 9, 0);
        f.entry(7, 5, 254, 0x1234, 0x365, 1); // Primal variant shape
        f.entry(7, 6, 253, 1, 0x54E, 0);
        f.entry(7, 8, 0, 0xBEEF, 0, 0xFE);
        f.entry(7, 10, 0xFFFF, 0xABCD, 50, 0xFEDC);
        f.entry(7, 11, 4, 15, 1440, 0); // invalid target
        f.entry(7, 13, 4, 30, 12, 0); f.entry(7, 14, 4, 30, 12, 0); // both duplicate slots fixed
        f.restrictions(); f.loadEvolutions(); return f;
    }

    @Test
    void sharedConstraintsSplitIdentityAndFixedDuplicateTopologyAcrossSeeds() throws Exception {
        for (int seed = 0; seed < 16; seed++) {
            var f = small();
            f.species[1].setHp(10); f.species[4].setHp(10); f.species[7].setHp(10);
            // Explicitly different growth/type/BST candidates exercise real filters.
            f.species[20].setGrowthCurve(ExpCurve.SLOW); f.species[21].setPrimaryType(Type.FIRE);
            for (int id = 30; id <= 50; id++) f.species[id].setHp(100);
            Settings s = settings(); s.setEvosNoConvergence(true); s.setEvosMaxThreeStages(true);
            s.setEvosSameTyping(true); s.setEvosForceGrowth(true); s.setEvosSimilarStrength(true);
            byte[] before = f.memory.clone();
            new EvolutionRandomizer(f, s, new Random(seed)).randomizeEvolutions(); f.write(); f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph();
            assertAcyclic(graph, 3);
            Set<Species> picked = new HashSet<>();
            for (var entry : graph.entrySet()) for (Evolution e : entry.getValue()) {
                if (e.getExtraInfo() < 0) continue;
                assertTrue(picked.add(e.getTo()), "no convergence");
                assertTrue(e.getTo().hasSharedType(e.getFrom()));
                assertEquals(e.getFrom().getGrowthCurve(), e.getTo().getGrowthCurve());
                assertTrue(e.getTo().getBSTForPowerLevels() > e.getFrom().getBSTForPowerLevels());
                assertNotEquals(20, e.getTo().getSpeciesSetIdentityNumber());
                // Similar strength chooses the nearest available strength bracket:
                // candidates with HP100 are farther than plentiful HP50 controls.
                assertTrue(e.getTo().getHp() < 100);
            }
            assertNotEquals(f.word(7, 1, 4), f.word(7, 3, 4));
            for (int slot : new int[] {5,6,8,10,11,13,14}) {
                int o = CfruDpeEvolutionFixture.offset(7, slot);
                assertArrayEquals(Arrays.copyOfRange(before,o,o+8), Arrays.copyOfRange(f.memory,o,o+8));
            }
            assertEquals(32, f.word(4,2,2)); assertEquals(17, f.word(4,2,6));
        }
    }

    @Test
    void impossiblePlanRestoresGraphAndBytesAndCanRetry() throws Exception {
        var f = small(); f.pool.removeIf(sp -> sp.getSpeciesSetIdentityNumber() != 1); f.restrictions();
        byte[] before = f.memory.clone();
        var original = new ArrayList<>(f.species[1].getEvolutionsFrom());
        assertThrows(RandomizationException.class, () -> new EvolutionRandomizer(f, settings(), new Random(667)).randomizeEvolutions());
        assertEquals(original, f.species[1].getEvolutionsFrom()); assertArrayEquals(before, f.memory);
        f.write(); assertArrayEquals(before, f.memory);
        f.pool.add(f.species[3]); f.restrictions();
        new EvolutionRandomizer(f, settings(), new Random(667)).randomizeEvolutions(); f.write();
        assertEquals(3, f.word(1,0,4));
    }

    @Test
    void unsafeUnrepresentableAndMalformedPlansRejectBeforePublishing() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var f = small(); var graph = f.getTargetOnlyEvolutionGraph();
            byte[] before = f.memory.clone(); var edge = graph.get(f.species[1]).getFirst();
            switch (mode) {
                case 0 -> edge.setTo(f.species[0x365]);
                case 1 -> edge.setTo(new Species(2));
                case 2 -> graph.get(f.species[1]).clear();
                case 3 -> graph.get(f.species[7]).stream().filter(e -> e.getExtraInfo() < 0).findFirst().orElseThrow().setTo(f.species[3]);
            }
            assertThrows(RomIOException.class, () -> f.applyTargetOnlyEvolutionGraph(graph));
            assertArrayEquals(before, f.memory); f.write(); assertArrayEquals(before, f.memory);
        }
    }

    @Test
    void everyLevelGuardAndPostPlanOpaqueMutationRejectWithoutOutput() throws Exception {
        var f = small(); byte[] before = f.memory.clone();
        Settings s = settings(); s.setEvolutionsMod(Settings.EvolutionsMod.RANDOM_EVERY_LEVEL);
        var error = assertThrows(RandomizationException.class, () -> new EvolutionRandomizer(f,s,new Random(667)).randomizeEvolutions());
        assertTrue(error.getMessage().contains("RANDOM_EVERY_LEVEL")); assertArrayEquals(before, f.memory);
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        f.species[7].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 99);
        assertThrows(RomIOException.class, f::preflightSave);
        assertThrows(RomIOException.class, f::saveSpeciesStats);
        assertThrows(RomIOException.class, f::write); assertArrayEquals(before, f.memory);
    }


    @Test
    void plannedFullyOwnedSupportedTweaksWorkButOpaqueTriggersRemainUnowned() throws Exception {
        var f = small();
        f.entry(10,0,5,0,11,0); f.loadEvolutions();
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        assertTrue(f.impossible(f.species[10].getEvolutionsFrom().getFirst()));
        f.write(); assertEquals(4,f.word(10,0,0)); assertEquals(37,f.word(10,0,2));
        assertEquals(16,f.word(1,0,0)); assertEquals(50,f.word(1,0,2));
        f.loadEvolutions();
        new EvolutionRandomizer(f,settings(),new Random(668)).randomizeEvolutions();
        f.entry(4,2,18,32,5,99);
        byte[] externallyChanged = f.memory.clone();
        assertThrows(RomIOException.class,f::preflightSave);
        assertArrayEquals(externallyChanged,f.memory);
    }

    @Test
    void invalidAssetTargetIsRejectedByHandlerEvenWhenCallerBypassesPool() throws Exception {
        var f = small(); var graph = f.getTargetOnlyEvolutionGraph();
        graph.get(f.species[1]).getFirst().setTo(f.species[50]);
        Arrays.fill(f.memory,0x30000 + 50*8,0x30000 + 50*8+4,(byte)0);
        byte[] before = f.memory.clone();
        assertThrows(RomIOException.class,() -> f.applyTargetOnlyEvolutionGraph(graph));
        assertArrayEquals(before,f.memory);
    }


    @Test
    void targetTamperingOnFullyModeledPlannedRowRejectsBeforeAllOutput() throws Exception {
        var f = small(); f.entry(2,0,4,16,3,0); f.loadEvolutions();
        byte[] before = f.memory.clone();
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        f.species[2].getEvolutionsFrom().getFirst().setTo(f.species[0x365]);
        assertThrows(RomIOException.class,f::preflightSave);
        assertThrows(RomIOException.class,f::saveSpeciesStats);
        assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory);
    }

    static void assertAcyclic(Map<Species,List<Evolution>> graph, int limit) {
        for (Species source : graph.keySet()) assertTrue(depth(source,graph,new HashSet<>()) <= limit);
    }
    static int depth(Species sp, Map<Species,List<Evolution>> graph, Set<Species> path) {
        assertTrue(path.add(sp), "cycle"); int depth = 1;
        for (Evolution e : graph.getOrDefault(sp,List.of())) depth = Math.max(depth,1+depth(e.getTo(),graph,path));
        path.remove(sp); return depth;
    }
}
