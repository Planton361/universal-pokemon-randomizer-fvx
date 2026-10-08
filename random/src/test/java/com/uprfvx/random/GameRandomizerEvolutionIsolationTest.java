package com.uprfvx.random;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import java.util.*;
import java.io.PrintStream;
import java.io.OutputStream;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-free guardrails for option flow around Evolutions.
 */
class GameRandomizerEvolutionIsolationTest {

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

    @Test
    void easierWitnessPreflightPrecedesRestrictionsUpdatersAndAllRandomizers() throws IOException {
        String source=Files.readString(gameRandomizerSourcePath());
        String body=methodBody(source,"public Results randomize(final String filename, final PrintStream log, long seed)");
        assertTrue(body.indexOf("preflightCfruEvolutionEasier") < body.indexOf("setupSpeciesRestrictions()"));
        assertTrue(body.indexOf("preflightCfruEvolutionEasier") < body.indexOf("applyUpdaters()"));
        assertTrue(body.indexOf("preflightCfruEvolutionEasier") < body.indexOf("applyRandomizers()"));
        assertTrue(body.contains("settings.isMakeEvolutionsEasier() && romHandler instanceof Gen3RomHandler"));
    }

    @Test
    void realGameRandomizerRejectsMissingWitnessBeforeRestrictionOrRandomizerMutation() throws Exception {
        class MemoryHandler extends CfruDpeEvolutionFixture {
            int restrictionsRead;
            MemoryHandler() throws Exception {super();}
            @Override public List<Integer> getMoveTutorMoves() {return List.of();}
            @Override public List<Trainer> getTrainers() {return List.of();}
            @Override public List<InGameTrade> getInGameTrades() {return List.of();}
            @Override public List<Move> getMoves() {return List.of();}
            @Override public boolean canChangeStaticPokemon() {return false;}
            @Override public com.uprfvx.romio.services.RestrictedSpeciesService getRestrictedSpeciesService() {
                restrictionsRead++; return super.getRestrictedSpeciesService();
            }
        }
        String previous=System.getProperty("uprfvx.cfruEvolutionOwnership"); System.clearProperty("uprfvx.cfruEvolutionOwnership");
        try {
            var f=new MemoryHandler(); f.populateExactSource(); byte[] before=f.memory.clone();
            Settings settings=new Settings(); settings.setMakeEvolutionsEasier(true); settings.setMakeEvolutionsEasierLvl(40);
            settings.setLimitPokemon(true); settings.setEvolutionsMod(Settings.EvolutionsMod.RANDOM);
            settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            GameRandomizer game=new GameRandomizer(settings,null,f,null,false); int reads=f.restrictionsRead;
            var result=game.randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),721);
            assertFalse(result.wasSaveSuccessful()); assertInstanceOf(RomIOException.class,result.getException());
            assertTrue(result.getException().getMessage().contains("explicit JVM opt-in missing"));
            assertEquals(reads,f.restrictionsRead); assertArrayEquals(before,f.memory);
        } finally {
            if(previous==null) System.clearProperty("uprfvx.cfruEvolutionOwnership"); else System.setProperty("uprfvx.cfruEvolutionOwnership",previous);
        }
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
