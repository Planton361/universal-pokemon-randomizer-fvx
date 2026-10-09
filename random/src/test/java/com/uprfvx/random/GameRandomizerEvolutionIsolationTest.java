package com.uprfvx.random;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.SpecialFormExclusionOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROM-free guardrails for option flow around Evolutions.
 */
class GameRandomizerEvolutionIsolationTest {

    private static class EarlyGateFixture extends CfruDpeEvolutionFixture {
        int preflights, restrictions, removals;
        boolean rejectRestrictions;
        EarlyGateFixture() throws Exception { super(); }
        @Override public void preflightCfruDpeTimeEvolutions() { preflights++; super.preflightCfruDpeTimeEvolutions(); }
        @Override public void removeTimeBasedEvolutions() { removals++; super.removeTimeBasedEvolutions(); }
        @Override public int generationOfPokemon() { return 3; }
        @Override public List<Integer> getMoveTutorMoves() { return List.of(); }
        @Override public List<Trainer> getTrainers() { return List.of(); }
        @Override public boolean canChangeStaticPokemon() { return false; }
        @Override public List<InGameTrade> getInGameTrades() { return List.of(); }
        @Override public List<Move> getMoves() { return List.of(); }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() {
            if (!rejectRestrictions) return super.getRestrictedSpeciesService();
            return new RestrictedSpeciesService(this) {
                @Override public void setRestrictions(GenRestrictions r, SpecialFormExclusionOptions options) {
                    restrictions++;
                    throw new RomIOException("synthetic stop at restrictions");
                }
            };
        }
    }

    private static GameRandomizer randomizer(EarlyGateFixture f, Settings settings) {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override protected Object[][] getContents() { return new Object[0][]; }
        };
        return new GameRandomizer(settings, null, f, bundle, false);
    }

    @Test
    void malformedTimeRowsAbortActualRandomizeBeforeRestrictionsUpdatersOrOutput() throws Exception {
        var f = new EarlyGateFixture(); f.populateExactSource();
        f.entry(1365, 0, 22, 30, 1366, 1); f.loadEvolutions();
        var settings = new Settings(); settings.setRemoveTimeBasedEvolutions(true);
        settings.setUpdateBaseStats(true); settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
        byte[] before = f.memory.clone(); int hp = f.species[1].getHp();
        var randomizer = randomizer(f, settings); f.rejectRestrictions = true;
        var result = randomizer.randomize("unused-synthetic-output", new PrintStream(OutputStream.nullOutputStream()), 723);
        assertFalse(result.wasSaveSuccessful());
        assertTrue(result.getException().getMessage().contains("CFRU_DPE_TIME_EVOLUTION_SEMANTICS_BLOCKER"));
        assertEquals(1, f.preflights); assertEquals(0, f.restrictions); assertEquals(0, f.removals);
        assertEquals(hp, f.species[1].getHp()); assertArrayEquals(before, f.memory);
        assertTrue(f.getPreImprovedEvolutions().isEmpty());
    }

    @Test
    void validTimeGateAndDisabledPresetReachRestrictionsWithoutPrematureMutation() throws Exception {
        for (boolean enabled : new boolean[] {false, true}) {
            var f = new EarlyGateFixture(); f.populateExactSource();
            if (!enabled) { f.entry(1365, 0, 22, 30, 1366, 1); f.loadEvolutions(); }
            var settings = new Settings(); settings.setRemoveTimeBasedEvolutions(enabled);
            byte[] before = f.memory.clone(); var randomizer = randomizer(f, settings); f.rejectRestrictions = true;
            var result = randomizer.randomize("unused-synthetic-output", new PrintStream(OutputStream.nullOutputStream()), 723);
            assertFalse(result.wasSaveSuccessful()); assertEquals("synthetic stop at restrictions", result.getException().getMessage());
            assertEquals(enabled ? 1 : 0, f.preflights); assertEquals(1, f.restrictions); assertEquals(0, f.removals);
            assertArrayEquals(before, f.memory);
        }
    }

    @Test
    void actualEvolutionImprovementDispatchUsesOnlyTheEnabledTimeOption() throws Exception {
        for (boolean enabled : new boolean[] {false, true}) {
            var f = new EarlyGateFixture(); f.populateExactSource();
            var settings = new Settings(); settings.setRemoveTimeBasedEvolutions(enabled);
            var randomizer = randomizer(f, settings);
            var method = GameRandomizer.class.getDeclaredMethod("maybeApplyEvolutionImprovements");
            method.setAccessible(true); method.invoke(randomizer);
            assertEquals(enabled ? 1 : 0, f.removals);
            assertEquals(enabled ? 21 : 0, f.getPreImprovedEvolutions().size());
        }
        String source = Files.readString(gameRandomizerSourcePath());
        String body = methodBody(source, "public Results randomize(final String filename, final PrintStream log, long seed)");
        assertTrue(body.indexOf("preflightCfruDpeTimeEvolutions") < body.indexOf("setupSpeciesRestrictions"));
        assertTrue(body.contains("settings.isRemoveTimeBasedEvolutions() && romHandler instanceof Gen3RomHandler"));
    }

    @Test
    void movesetsAndTrainerNamesDoNotCallEvolutionMutatorsInGameRandomizerFlow() throws IOException {
        String source = Files.readString(gameRandomizerSourcePath());
        String movesets = methodBody(source, "private void maybeRandomizeMovesets()");
        String trainerNames = methodBody(source, "private void maybeRandomizeTrainerNames()");

        assertFalse(movesets.contains("evoRandomizer"));
        assertFalse(movesets.contains("removeImpossibleEvolutions"));
        assertFalse(movesets.contains("condenseLevelEvolutions"));
        assertFalse(movesets.contains("makeEvolutionsEasier"));
        assertFalse(movesets.contains("removeTimeBasedEvolutions"));

        assertFalse(trainerNames.contains("evoRandomizer"));
        assertFalse(trainerNames.contains("removeImpossibleEvolutions"));
        assertFalse(trainerNames.contains("condenseLevelEvolutions"));
        assertFalse(trainerNames.contains("makeEvolutionsEasier"));
        assertFalse(trainerNames.contains("removeTimeBasedEvolutions"));
    }

    @Test
    void evolutionRandomizerAndEvolutionImprovementsAreOnlyBehindEvolutionSettings() throws IOException {
        String source = Files.readString(gameRandomizerSourcePath());
        String evolutions = methodBody(source, "private void maybeRandomizeEvolutions()");
        String improvements = methodBody(source, "private void maybeApplyEvolutionImprovements()");

        assertTrue(evolutions.contains("settings.getEvolutionsMod() != Settings.EvolutionsMod.UNCHANGED"));
        assertTrue(evolutions.contains("evoRandomizer.randomizeEvolutions()"));

        assertTrue(improvements.contains("settings.isChangeImpossibleEvolutions()"));
        assertTrue(improvements.contains("romHandler.removeImpossibleEvolutions"));
        assertTrue(improvements.contains("settings.isMakeEvolutionsEasier()"));
        assertTrue(improvements.contains("romHandler.condenseLevelEvolutions"));
        assertTrue(improvements.contains("romHandler.makeEvolutionsEasier"));
        assertTrue(improvements.contains("settings.isRemoveTimeBasedEvolutions()"));
        assertTrue(improvements.contains("romHandler.removeTimeBasedEvolutions"));
    }

    @Test
    void evolutionLogSectionOnlyComesFromEvolutionRandomizerChanges() throws IOException {
        String source = Files.readString(randomizationLoggerSourcePath());
        String shouldLogEvolutions = methodBody(source, "private boolean shouldLogEvolutions()");

        assertTrue(shouldLogEvolutions.contains("return evoRandomizer.isChangesMade();"));
        assertFalse(shouldLogEvolutions.contains("speciesMovesetRandomizer"));
        assertFalse(shouldLogEvolutions.contains("trainerNameRandomizer"));
    }

    private static Path gameRandomizerSourcePath() {
        Path moduleRelative = Path.of("src/main/java/com/uprfvx/random/GameRandomizer.java");
        if (Files.isRegularFile(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("random/src/main/java/com/uprfvx/random/GameRandomizer.java");
    }

    private static Path randomizationLoggerSourcePath() {
        Path moduleRelative = Path.of("src/main/java/com/uprfvx/random/log/RandomizationLogger.java");
        if (Files.isRegularFile(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("random/src/main/java/com/uprfvx/random/log/RandomizationLogger.java");
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue(signatureIndex >= 0, "Missing method signature: " + signature);

        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue(bodyStart >= 0, "Missing method body: " + signature);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }
}
