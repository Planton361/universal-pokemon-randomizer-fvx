package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Real consumers and real Gen3 illegal-move hook; synthetic in-memory data only. */
class CfruDpeMovePoolExclusionTest {
    @Test
    void levelUpSelectsBothAdjacentControlsAndNoSystemMoves() throws Exception {
        Fixture f = new Fixture(List.of(0x2FE, 0x39B), false, false);
        Settings settings = new Settings();
        settings.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        settings.setBlockBrokenMovesetMoves(true);
        SpeciesMovesetRandomizer randomizer = new SpeciesMovesetRandomizer(f, settings, new Random(653));
        randomizer.randomizeMovesLearnt();
        assertTrue(randomizer.isChangesMade());
        assertEquals(Set.of(0x2FE, 0x39B), new HashSet<>(f.learnset.stream().map(m -> m.move).toList()));
        assertSystemMovesAbsent(f.learnset.stream().map(m -> m.move).toList());
        assertEquals(1, f.learnsetWrites);
    }

    @Test
    void tmConsumerUsesHookWithBrokenAndFieldOptionsOnAndOff() throws Exception {
        for (boolean broken : new boolean[] {false, true}) {
            for (boolean keepField : new boolean[] {false, true}) {
                for (boolean realExtendedGuard : new boolean[] {false, true}) {
                    List<Integer> controls = realExtendedGuard ? List.of(0x2FE, 33) : List.of(0x2FE, 0x39B);
                    Fixture f = new Fixture(controls, keepField, false);
                    Settings settings = new Settings();
                    settings.setBlockBrokenTMMoves(broken);
                    settings.setKeepFieldMoveTMs(keepField);
                    TMTutorMoveRandomizer randomizer = new TMTutorMoveRandomizer(
                            consumerHandler(f, realExtendedGuard), settings, new Random(653));
                    randomizer.randomizeTMMoves();
                    assertTrue(randomizer.isTMChangesMade());
                    assertSelection(f.writtenTms, controls, keepField);
                    assertEquals(1, f.tmWrites);
                }
            }
        }
    }

    @Test
    void tutorConsumerKeepsTmHmAndFieldExclusionsCoherent() throws Exception {
        for (boolean broken : new boolean[] {false, true}) {
            for (boolean keepField : new boolean[] {false, true}) {
                for (boolean realExtendedGuard : new boolean[] {false, true}) {
                    List<Integer> controls = realExtendedGuard ? List.of(0x2FE, 33) : List.of(0x2FE, 0x39B);
                    Fixture f = new Fixture(controls, keepField, true);
                    Settings settings = new Settings();
                    settings.setBlockBrokenTutorMoves(broken);
                    settings.setKeepFieldMoveTutors(keepField);
                    TMTutorMoveRandomizer randomizer = new TMTutorMoveRandomizer(
                            consumerHandler(f, realExtendedGuard), settings, new Random(653));
                    randomizer.randomizeMoveTutorMoves();
                    assertTrue(randomizer.isTutorChangesMade());
                    assertSelection(f.writtenTutors, controls, keepField);
                    assertFalse(f.writtenTutors.contains(21), "Existing TM must not become a tutor");
                    assertEquals(1, f.tutorWrites);
                }
            }
        }
    }

    @Test
    void genericZTableDoesNotCoverCfruWitnessAndExistingExtendedGuardStillApplies() throws Exception {
        assertEquals(622, MoveIDs.breakneckBlitzPhysical);
        assertFalse(GlobalConstants.zMoves.contains(0x2FF));
        Fixture f = new Fixture(List.of(0x2FE, 33, 0x39B), false, false);
        assertTrue(f.hasExtendedBpreHackSpeciesPool());
        assertFalse(f.getIllegalMoves().contains(0x39B));
        // Aqua Cutter is outside the new ban, but the existing TM/Tutor array-bound guard
        // still excludes it. The proxy runs above isolate the common hook from that guard.
        new TMTutorMoveRandomizer(f, new Settings(), new Random(653)).randomizeTMMoves();
        assertEquals(Set.of(0x2FE, 33), new HashSet<>(f.writtenTms));
    }

    private static RomHandler consumerHandler(Fixture f, boolean realExtendedGuard) {
        if (realExtendedGuard) return f;
        // Delegate getIllegalMoves to the actual detected Gen3 handler, never a copied list.
        // Removing only the instanceof-based pre-existing guard proves Max/G-Max are
        // excluded by this repair too, and 0x39B is not banned by the new hook.
        return (RomHandler) Proxy.newProxyInstance(RomHandler.class.getClassLoader(),
                new Class<?>[] {RomHandler.class}, (proxy, method, args) -> method.invoke(f, args));
    }

    private static void assertSelection(List<Integer> selected, List<Integer> controls, boolean keepField) {
        assertSystemMovesAbsent(selected);
        Set<Integer> expected = new HashSet<>(controls);
        if (keepField) {
            expected.add(10);
            assertEquals(10, selected.getFirst());
        }
        assertEquals(expected.size(), selected.size());
        assertEquals(expected, new HashSet<>(selected));
        for (int banned : new int[] {20, 22, 622}) assertFalse(selected.contains(banned));
    }

    private static void assertSystemMovesAbsent(List<Integer> selected) {
        for (int id = 0x2FF; id <= 0x39A; id++) assertFalse(selected.contains(id), "System move " + id);
        for (int id : new int[] {0x2FF, 0x334, 0x359}) assertFalse(selected.contains(id));
    }

    private static final class Fixture extends Gen3RomHandler {
        private final List<Move> candidates = new ArrayList<>();
        private final List<Integer> oldMoves;
        private final boolean tutor;
        private final RestrictedSpeciesService restricted;
        private final List<MoveLearnt> learnset = new ArrayList<>(List.of(new MoveLearnt(1, 1), new MoveLearnt(1, 5)));
        private List<Integer> writtenTms;
        private List<Integer> writtenTutors;
        private int tmWrites, tutorWrites, learnsetWrites;

        Fixture(List<Integer> controls, boolean keepField, boolean tutor) throws Exception {
            this.tutor = tutor;
            oldMoves = keepField ? List.of(10, 11, 12) : List.of(11, 12);
            var constructor = Gen3RomEntry.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            Gen3RomEntry entry = constructor.newInstance("synthetic detected CFRU/DPE");
            entry.setRomCode("BPRE");
            entry.putIntValue("PokemonCount", 1440);
            setField("romEntry", entry);
            setField("isRomHack", true);
            setField("useCfruDpeGen9SpeciesCount", true);
            candidates.add(null);
            IntStream.rangeClosed(0x2FF, 0x39A).forEach(id -> candidates.add(move(id)));
            controls.forEach(id -> candidates.add(move(id)));
            for (int id : new int[] {20, 22, 622}) candidates.add(move(id));
            if (tutor) candidates.add(move(21));
            if (keepField) candidates.add(move(10));
            Species species = new Species(1);
            species.setPrimaryType(Type.NORMAL);
            species.setAttack(50);
            species.setSpatk(50);
            SpeciesSet pool = new SpeciesSet();
            pool.add(species);
            restricted = new RestrictedSpeciesService(this) {
                @Override public SpeciesSet getAll(boolean includeAltFormes) { return pool; }
            };
        }

        private void setField(String name, Object value) throws Exception {
            Field field = Gen3RomHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        }

        @Override public RestrictedSpeciesService getRestrictedSpeciesService() { return restricted; }
        @Override public List<Move> getMoves() { return candidates; }
        @Override public int getPerfectAccuracy() { return 100; }
        @Override public List<Integer> getHMMoves() { return List.of(20); }
        @Override public List<Integer> getMovesBannedFromLevelup() { return List.of(22); }
        @Override public List<Integer> getFieldMoves() { return List.of(10); }
        @Override public int getTMCount() { return oldMoves.size(); }
        @Override public List<Integer> getTMMoves() { return tutor ? List.of(21) : oldMoves; }
        @Override public boolean hasMoveTutors() { return true; }
        @Override public List<Integer> getMoveTutorMoves() { return oldMoves; }
        @Override public void setTMMoves(List<Integer> moves) { writtenTms = List.copyOf(moves); tmWrites++; }
        @Override public void setMoveTutorMoves(List<Integer> moves) { writtenTutors = List.copyOf(moves); tutorWrites++; }
        @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() { return Map.of(1, learnset); }
        @Override public void setMovesLearnt(Map<Integer, List<MoveLearnt>> moves) { learnsetWrites++; }
    }

    private static Move move(int id) {
        Move move = new Move();
        move.number = move.internalId = id;
        move.name = "Synthetic " + id;
        move.power = 80;
        move.pp = 15;
        move.hitratio = 100;
        move.hitCount = 1;
        move.type = Type.NORMAL;
        move.category = MoveCategory.PHYSICAL;
        return move;
    }
}
