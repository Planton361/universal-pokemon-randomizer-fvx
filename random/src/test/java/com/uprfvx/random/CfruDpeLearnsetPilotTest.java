package com.uprfvx.random;

import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.SpecialFormExclusionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic memory only; never saves or opens a ROM. */
class CfruDpeLearnsetPilotTest {
    @TempDir Path temp;

    static class Fixture extends CfruDpeEvolutionFixture {
        int restrictions, writes;
        Fixture() throws Exception { super(); }
        @Override public int generationOfPokemon() { return 3; }
        @Override public List<Integer> getMoveTutorMoves() { return List.of(); }
        @Override public List<Trainer> getTrainers() { return List.of(); }
        @Override public boolean canChangeStaticPokemon() { return false; }
        @Override public List<InGameTrade> getInGameTrades() { return List.of(); }
        @Override public List<Move> getMoves() { return List.of(); }
        @Override public void setMovesLearnt(Map<Integer, List<MoveLearnt>> movesets) {
            writes++;
            super.setMovesLearnt(movesets);
        }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() {
            return new RestrictedSpeciesService(this) {
                @Override public void setRestrictions(GenRestrictions r, SpecialFormExclusionOptions options) {
                    restrictions++;
                    throw new RomIOException("synthetic stop at restrictions");
                }
            };
        }
    }

    private static GameRandomizer game(Fixture f, Settings s) {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override protected Object[][] getContents() { return new Object[0][]; }
        };
        return new GameRandomizer(s, null, f, bundle, false);
    }

    private Settings roundTrip(Settings s, boolean file) throws Exception {
        s.setRomName("SYNTHETIC");
        s.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        if (!file) return Settings.fromString(s.toString());
        Path path = temp.resolve("synthetic-settings.rnqs");
        try (var out = new FileOutputStream(path.toFile())) { s.writeToFileFormat(out); }
        try (var in = new FileInputStream(path.toFile())) { return Settings.readFromFileFormat(in); }
    }

    @Test void unsafeOptionsAndImportedSettingsStopBeforeAnyMutation() throws Exception {
        for (boolean file : new boolean[]{false, true}) {
            for (Settings.MovesetsMod mode : Settings.MovesetsMod.values()) {
                for (boolean reorder : new boolean[]{false, true}) {
                    if (mode == Settings.MovesetsMod.UNCHANGED && !reorder) continue;
                    Fixture f = new Fixture();
                    Settings s = new Settings(); s.setMovesetsMod(mode); s.setReorderDamagingMoves(reorder);
                    s.setUpdateBaseStats(true); s.setRandomizeIntroMon(true);
                    s = roundTrip(s, file);
                    byte[] before = f.memory.clone();
                    var result = game(f, s).randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 726);
                    assertInstanceOf(RandomizationException.class, result.getException());
                    assertTrue(result.getException().getMessage().contains("learnset"));
                    assertEquals(0, f.restrictions); assertEquals(0, f.writes);
                    assertEquals(50, f.species[1].getHp()); assertArrayEquals(before, f.memory);
                }
            }
        }
    }

    @Test void safeImportedProfilePassesEarlyGateAndMovesetDispatcherDoesNotWrite() throws Exception {
        for (boolean file : new boolean[]{false, true}) {
            Fixture f = new Fixture(); Settings selected = new Settings();
            selected.setStartersMod(Settings.StartersMod.COMPLETELY_RANDOM);
            selected.setRandomizeWildPokemon(true);
            selected.setTrainersMod(Settings.TrainersMod.RANDOM);
            selected.setPickupItemsMod(Settings.PickupItemsMod.RANDOM);
            Settings s = roundTrip(selected, file);
            assertEquals(Settings.MovesetsMod.UNCHANGED, s.getMovesetsMod());
            assertFalse(s.isReorderDamagingMoves());
            assertEquals(Settings.StartersMod.COMPLETELY_RANDOM, s.getStartersMod());
            assertTrue(s.isRandomizeWildPokemon());
            assertEquals(Settings.TrainersMod.RANDOM, s.getTrainersMod());
            assertEquals(Settings.PickupItemsMod.RANDOM, s.getPickupItemsMod());
            byte[] before = f.memory.clone(); GameRandomizer g = game(f, s);
            var method = GameRandomizer.class.getDeclaredMethod("maybeRandomizeMovesets");
            method.setAccessible(true); method.invoke(g);
            assertEquals(0, f.writes); assertArrayEquals(before, f.memory);
            var result = g.randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 726);
            assertEquals("synthetic stop at restrictions", result.getException().getMessage());
            assertEquals(1, f.restrictions); assertEquals(0, f.writes); assertArrayEquals(before, f.memory);
        }
    }

    @Test void directSelectedProfileWritesFailBeforeReadingInputOrWriting() throws Exception {
        Fixture f = new Fixture(); byte[] before = f.memory.clone();
        for (boolean jambo : new boolean[]{false, true}) {
            f.setField("jamboMovesetHack", jambo);
            var error = assertThrows(RomIOException.class, () -> f.setMovesLearnt(null));
            assertTrue(error.getMessage().contains("verified allocator")); assertArrayEquals(before, f.memory);
        }
    }

    @Test void nonCfruSettingsAreNotRejectedByTheNewGate() throws Exception {
        Fixture f = new Fixture(); f.setField("useCfruDpeGen9SpeciesCount", false);
        Settings s = new Settings(); s.setMovesetsMod(Settings.MovesetsMod.METRONOME_ONLY);
        s.setReorderDamagingMoves(true);
        byte[] before = f.memory.clone();
        var result = game(f, s).randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 726);
        assertEquals("synthetic stop at restrictions", result.getException().getMessage());
        assertEquals(1, f.restrictions); assertArrayEquals(before, f.memory);
        f.setField("numRealPokemon", 0);
        assertDoesNotThrow(() -> f.setMovesLearnt(Map.of()));
        assertArrayEquals(before, f.memory);
    }
}
