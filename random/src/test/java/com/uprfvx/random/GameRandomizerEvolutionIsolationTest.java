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
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-free guardrails for option flow around Evolutions.
 */
class GameRandomizerEvolutionIsolationTest {

    private static class EarlyGateFixture extends CfruDpeEvolutionFixture {
        int preflights, restrictions, removals, combinedRemovals, impossibleRemovals, jointEasierPreflights, jointEasierRemovals, standaloneCaps, standaloneEasier, jointTimePreflights, jointTimeRemovals, triplePreflights, tripleRemovals;
        boolean rejectRestrictions;
        EarlyGateFixture() throws Exception { super(); }
        @Override public void preflightCfruDpeTimeEvolutions() { preflights++; super.preflightCfruDpeTimeEvolutions(); }
        @Override public void removeTimeBasedEvolutions() { removals++; super.removeTimeBasedEvolutions(); }
        @Override public void removeImpossibleAndTimeBasedEvolutions(boolean estimated) {
            combinedRemovals++; super.removeImpossibleAndTimeBasedEvolutions(estimated);
        }
        @Override public void preflightCfruDpeImpossibleEasierEvolutions(int cap,boolean estimated) {
            jointEasierPreflights++;super.preflightCfruDpeImpossibleEasierEvolutions(cap,estimated);
        }
        @Override public void removeImpossibleAndMakeEasierEvolutions(int cap,boolean estimated) {
            jointEasierRemovals++;super.removeImpossibleAndMakeEasierEvolutions(cap,estimated);
        }
        @Override public void preflightCfruDpeThreeWayEvolutions(int cap,boolean estimated) {
            triplePreflights++;super.preflightCfruDpeThreeWayEvolutions(cap,estimated);
        }
        @Override public void composeThreeWayEvolutions(int cap,boolean estimated) {
            tripleRemovals++;super.composeThreeWayEvolutions(cap,estimated);
        }
        @Override public void condenseLevelEvolutions(int cap) {standaloneCaps++;super.condenseLevelEvolutions(cap);}
        @Override public void preflightCfruDpeEasierTimeEvolutions(int cap,boolean estimated) {
            jointTimePreflights++;super.preflightCfruDpeEasierTimeEvolutions(cap,estimated);
        }
        @Override public void makeEasierAndRemoveTimeEvolutions(int cap,boolean estimated) {
            jointTimeRemovals++;super.makeEasierAndRemoveTimeEvolutions(cap,estimated);
        }
        @Override public void makeEvolutionsEasier(boolean other,boolean estimated) {
            standaloneEasier++;super.makeEvolutionsEasier(other,estimated);
        }
        @Override public void removeImpossibleEvolutions(boolean moves,boolean estimated) {
            impossibleRemovals++; super.removeImpossibleEvolutions(moves,estimated);
        }
        void nativeOwners() throws Exception {
            Species giga=new Species(133);giga.setName("Eevee-Giga");giga.setSpeciesSetIdentityNumber(1270);
            species[1270]=giga;pool.set(1269,giga);
            Species clamperl=new Species(366);clamperl.setName("Clamperl");clamperl.setSpeciesSetIdentityNumber(373);
            species[373]=clamperl;pool.set(372,clamperl);
            var loaded=new ArrayList<Species>();loaded.add(null);loaded.addAll(pool);setField("speciesList",loaded);
            setField("pokesInternal",Arrays.copyOf(species,1440));int[] map=new int[1440];
            for(int id=1;id<1440;id++)map[id]=species[id].getNumber();setField("internalToPokedex",map);
        }
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

    private static void bindJointWitness(EarlyGateFixture f) throws Exception {
        byte[] memory=f.memory;int record=0x48200;
        memory[0xAC]='B';memory[0xAD]='P';memory[0xAE]='R';memory[0xAF]='E';
        memory[0x42EC4]=0;memory[0x42EC5]=0x4B;memory[0x42EC6]=0x18;memory[0x42EC7]=0x47;
        java.util.function.BiConsumer<Integer,Integer> full=(at,value)->{for(int i=0;i<4;i++)memory[at+i]=(byte)(value>>>(i*8));};
        full.accept(0x42EC8,0x08048001);full.accept(0x42F6C,0x08000100);
        System.arraycopy("CFRUEVO1".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,memory,record,8);
        full.accept(record+8,0x08048200);full.accept(record+12,0x08048001);
        int[] fields={1,32,28,1,220,160};for(int i=0;i<fields.length;i++) {
            memory[record+16+i*2]=(byte)fields[i];memory[record+17+i*2]=(byte)(fields[i]>>>8);
        }
        memory[record+28]=(byte)220;
        Properties witness=new Properties();
        String[][] data={{"schema","OWNERSHIP_WITNESS_V1"},{"version","1"},
                {"cfru.sha","e27e2113e4d59dc76cf5da8d4c43b067addae348"},{"dpe.sha","d887185de1f6ae6a78e85c4311bbadde17041d00"},
                {"build.id","synthetic-build"},{"config.id","synthetic-config"},{"config.sha256","0".repeat(64)},
                {"input.size",Integer.toString(memory.length)},
                {"input.sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(memory))},
                {"record.offset","0x48200"},{"table.rows","1440"},{"table.slots","16"},{"table.entryBytes","8"},
                {"consumer.start","0x48000"},{"consumer.end","0x48100"},
                {"table.start","0x100"},{"table.end",Integer.toString(0x100+1440*128)},
                {"insertions.count","2"},{"insertions.0.start","0x100"},{"insertions.0.end","0x2E000"},
                {"insertions.1.start","0x48000"},{"insertions.1.end","0x4C000"},{"protected.count","4"}};
        for(String[] d:data)witness.setProperty(d[0],d[1]);
        String[] types={"PICKUP_CODE","PICKUP_DATA","OTHER_CODE","OTHER_DATA"};
        for(int i=0;i<4;i++) {
            witness.setProperty("protected."+i+".type",types[i]);
            witness.setProperty("protected."+i+".start",Integer.toString(0x49000+i*0x100));
            witness.setProperty("protected."+i+".end",Integer.toString(0x49080+i*0x100));
        }
        f.setField("originalRom",memory.clone());f.loadEvolutions();
        var bind=com.uprfvx.romio.romhandlers.Gen3RomHandler.class.getDeclaredMethod("acceptSyntheticEvolutionWitness",Properties.class);
        bind.setAccessible(true);bind.invoke(f,witness);
    }

    @Test
    void jointEasierEarlyGateValidatesWitnessSlotsAndAllForbiddenPairsBeforeUpdaters() throws Exception {
        for(int mode=0;mode<5;mode++)for(long seed:new long[]{0,1,677,20261005658L}) {
            var f=new EarlyGateFixture();f.nativeOwners();f.populateExactSource();
            if(mode==1)f.entry(156,15,4,10,3,0);
            bindJointWitness(f);f.rejectRestrictions=true;
            if(mode==2)f.memory[0x48212]^=1;
            var settings=new Settings();settings.setChangeImpossibleEvolutions(true);settings.setMakeEvolutionsEasier(true);
            settings.setMakeEvolutionsEasierLvl(20);settings.setRemoveTimeBasedEvolutions(mode>=3);
            if(mode==4)settings.setChangeImpossibleEvolutions(false);
            settings.setUpdateBaseStats(true);settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            byte[] before=f.memory.clone();
            var result=randomizer(f,settings).randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),seed);
            assertFalse(result.wasSaveSuccessful());assertEquals(mode==0||mode>=3?1:0,f.restrictions);
            assertEquals(mode>=3?0:1,f.jointEasierPreflights);
            assertEquals(0,f.jointEasierRemovals);assertEquals(0,f.standaloneCaps);assertEquals(0,f.impossibleRemovals);
            assertArrayEquals(before,f.memory);assertEquals(50,f.species[1].getHp());assertTrue(f.getPreImprovedEvolutions().isEmpty());
            if(mode==0||mode>=3)assertEquals("synthetic stop at restrictions",result.getException().getMessage());
            if(mode==3)assertEquals(1,f.triplePreflights);
            if(mode==4)assertEquals(1,f.jointTimePreflights);
        }
    }

    @Test
    void exactlyOneJointEasierDispatcherCrossSeedAndNoIndependentMutator() throws Exception {
        byte[] reference=null;
        for(long seed:new long[]{0,1,677,20261005658L}) {
            var f=new EarlyGateFixture();f.nativeOwners();f.populateExactSource();bindJointWitness(f);
            var settings=new Settings();settings.setChangeImpossibleEvolutions(true);settings.setMakeEvolutionsEasier(true);settings.setMakeEvolutionsEasierLvl(20);
            var restored=Settings.fromString(withSerializationDefaults(settings).toString());
            var method=GameRandomizer.class.getDeclaredMethod("maybeApplyEvolutionImprovements");method.setAccessible(true);
            var game=randomizer(f,restored);var rng=GameRandomizer.class.getDeclaredField("randomSource");rng.setAccessible(true);
            ((com.uprfvx.random.random.RandomSource)rng.get(game)).seed(seed);method.invoke(game);f.write();f.write();
            assertEquals(1,f.jointEasierRemovals);assertEquals(0,f.standaloneCaps);assertEquals(0,f.standaloneEasier);
            assertEquals(0,f.impossibleRemovals);assertEquals(0,f.combinedRemovals);assertEquals(0,f.removals);
            assertEquals(4,f.word(64,0,0));assertEquals(20,f.word(64,0,2));assertEquals(7,f.word(133,0,0));
            assertEquals(160,f.memory[0x4821C]&255);
            if(reference==null)reference=f.memory.clone();else assertArrayEquals(reference,f.memory);
        }
    }

    @Test
    void exactlyOneEasierTimeDispatcherAndSeparateThreeWayPlan() throws Exception {
        for(long seed:new long[]{0,1,677,20261005658L}) {
            var f=new EarlyGateFixture();f.nativeOwners();f.populateExactSource();bindJointWitness(f);
            var settings=new Settings();settings.setMakeEvolutionsEasier(true);settings.setRemoveTimeBasedEvolutions(true);
            settings.setMakeEvolutionsEasierLvl(20);
            var restored=Settings.fromString(withSerializationDefaults(settings).toString());
            byte[] before=f.memory.clone();f.rejectRestrictions=true;
            var result=randomizer(f,restored).randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),seed);
            assertEquals("synthetic stop at restrictions",result.getException().getMessage());
            assertEquals(1,f.jointTimePreflights);assertArrayEquals(before,f.memory);
            var method=GameRandomizer.class.getDeclaredMethod("maybeApplyEvolutionImprovements");method.setAccessible(true);
            method.invoke(randomizer(f,restored));f.write();f.write();
            assertEquals(1,f.jointTimeRemovals);assertEquals(0,f.standaloneCaps);assertEquals(0,f.standaloneEasier);
            assertEquals(0,f.removals);assertEquals(0,f.impossibleRemovals);assertEquals(0,f.combinedRemovals);
            assertEquals(5,f.word(64,0,0));assertEquals(1,f.word(459,0,0));assertEquals(160,f.memory[0x4821C]&255);
            var g=new EarlyGateFixture();g.nativeOwners();g.populateExactSource();bindJointWitness(g);
            restored.setChangeImpossibleEvolutions(true);
            g.rejectRestrictions=true;
            before=g.memory.clone();result=randomizer(g,restored).randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),seed);
            assertEquals("synthetic stop at restrictions",result.getException().getMessage());
            assertEquals(1,g.restrictions);assertEquals(1,g.triplePreflights);assertEquals(0,g.jointTimePreflights);assertArrayEquals(before,g.memory);
            method.invoke(randomizer(g,restored));g.write();g.write();
            assertEquals(1,g.tripleRemovals);assertEquals(0,g.jointTimeRemovals);assertEquals(0,g.jointEasierRemovals);
            assertEquals(0,g.standaloneCaps);assertEquals(0,g.standaloneEasier);assertEquals(0,g.impossibleRemovals);
            assertEquals(0,g.combinedRemovals);assertEquals(0,g.removals);
            assertEquals(4,g.word(64,0,0));assertEquals(7,g.word(459,0,0));assertEquals(160,g.memory[0x4821C]&255);
        }
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

    @Test
    void combinedOptionsWithoutASourceOwnedPairRejectBeforeRestrictionsOrAnyMutation() throws Exception {
        for (long seed : new long[]{0, 1, 677, 20261005658L}) for (boolean palettes : new boolean[]{false, true}) {
            var f = new EarlyGateFixture(); f.populateExactSource(); f.rejectRestrictions = true;
            var settings = new Settings();
            settings.setMakeEvolutionsEasier(!palettes); settings.setRemoveTimeBasedEvolutions(!palettes);
            settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
            settings.setPokemonPalettesFollowTypes(palettes); settings.setPokemonPalettesShinyFromNormal(palettes);
            settings.setEvolutionsMod(Settings.EvolutionsMod.RANDOM); settings.setEvosForceChange(true);
            settings.setPickupItemsMod(Settings.PickupItemsMod.RANDOM);
            settings.setUpdateBaseStats(true); settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            var restored = Settings.fromString(withSerializationDefaults(settings).toString());
            byte[] before = f.memory.clone(); var edges = List.copyOf(f.species[133].getEvolutionsFrom());
            var result = randomizer(f, restored).randomize("UNUSED_SYNTHETIC_OUTPUT",
                    new PrintStream(OutputStream.nullOutputStream()), seed);
            assertFalse(result.wasSaveSuccessful());
            assertTrue(result.getException().getMessage().contains(palettes ? "Follow Types + Shiny From Normal" : "explicit JVM opt-in"));
            assertEquals(0, f.preflights); assertEquals(0, f.restrictions); assertEquals(0, f.removals);
            assertArrayEquals(before, f.memory); assertEquals(edges, f.species[133].getEvolutionsFrom());
            assertEquals(50, f.species[1].getHp()); assertTrue(f.getPreImprovedEvolutions().isEmpty());
            assertEquals(!palettes, restored.isMakeEvolutionsEasier());
            assertEquals(!palettes, restored.isRemoveTimeBasedEvolutions());
            assertEquals(palettes, restored.isPokemonPalettesFollowTypes());
            assertEquals(palettes, restored.isPokemonPalettesShinyFromNormal());
        }
    }

    @Test
    void jointImpossibleEasierRejectsMissingWitnessBeforeAnyRestrictionsOrUpdaters() throws Exception {
        for (boolean easier : new boolean[]{true}) for(long seed : new long[]{0,1,677,20261005658L}) {
            var f = new EarlyGateFixture(); f.populateExactSource(); f.rejectRestrictions=true;
            var settings=new Settings(); settings.setChangeImpossibleEvolutions(true);
            settings.setMakeEvolutionsEasier(easier); settings.setRemoveTimeBasedEvolutions(!easier);
            settings.setUpdateBaseStats(true); settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            byte[] before=f.memory.clone();
            var result=randomizer(f,settings).randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),seed);
            assertFalse(result.wasSaveSuccessful());
            assertTrue(result.getException().getMessage().contains("explicit JVM opt-in missing"));
            assertEquals(0,f.restrictions); assertEquals(0,f.preflights); assertEquals(0,f.removals);
            assertEquals(50,f.species[1].getHp()); assertArrayEquals(before,f.memory);
            assertTrue(f.getPreImprovedEvolutions().isEmpty());
        }
    }

    @Test
    void malformedImpossibleSlotFailsBeforeUnrelatedUpdaterOrRandomizerAndValidGateIsReadOnly() throws Exception {
        for(boolean malformed:new boolean[]{false,true}) {
            var f=new EarlyGateFixture();
            f.setField("pokesInternal",Arrays.copyOf(f.species,1440));
            int[] map=new int[1440]; for(int id=1;id<1440;id++) map[id]=id;
            f.setField("internalToPokedex",map); f.entry(133,0,2,0,196,malformed?0xBEEF:0); f.loadEvolutions();
            f.rejectRestrictions=true; var settings=new Settings(); settings.setChangeImpossibleEvolutions(true);
            settings.setUpdateBaseStats(true); settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            byte[] before=f.memory.clone();
            var result=randomizer(f,settings).randomize("UNUSED_SYNTHETIC_OUTPUT",new PrintStream(OutputStream.nullOutputStream()),732);
            assertFalse(result.wasSaveSuccessful());
            assertEquals(malformed?0:1,f.restrictions);
            assertTrue(result.getException().getMessage().contains(malformed?"auxiliary":"synthetic stop at restrictions"));
            assertEquals(EvolutionType.HAPPINESS_DAY,f.species[133].getEvolutionsFrom().getFirst().getType());
            assertEquals(50,f.species[1].getHp()); assertArrayEquals(before,f.memory); assertTrue(f.getPreImprovedEvolutions().isEmpty());
        }
    }

    @Test
    void combinedEarlyGateIsReadOnlyAndMalformedSlotRejectsBeforeRestrictionsUpdatersOrOutput() throws Exception {
        for(boolean malformed:new boolean[]{false,true}) {
            var f=new EarlyGateFixture();f.nativeOwners();f.populateExactSource();f.rejectRestrictions=true;
            if(malformed) {f.entry(1365,0,22,30,1366,1);f.loadEvolutions();}
            var settings=new Settings();settings.setChangeImpossibleEvolutions(true);settings.setRemoveTimeBasedEvolutions(true);
            settings.setUpdateBaseStats(true);settings.setBaseStatisticsMod(Settings.BaseStatisticsMod.RANDOM);
            byte[] before=f.memory.clone();var result=randomizer(f,settings).randomize("UNUSED_SYNTHETIC_OUTPUT",
                    new PrintStream(OutputStream.nullOutputStream()),733);
            assertFalse(result.wasSaveSuccessful());
            assertEquals(malformed?0:1,f.restrictions);assertEquals(0,f.preflights);
            assertEquals(0,f.removals);assertEquals(0,f.impossibleRemovals);assertEquals(0,f.combinedRemovals);
            assertEquals(50,f.species[1].getHp());assertArrayEquals(before,f.memory);assertTrue(f.getPreImprovedEvolutions().isEmpty());
            assertTrue(result.getException().getMessage().contains(malformed?"unexpected shape":"synthetic stop at restrictions"));
        }
    }

    @Test
    void actualCombinedDispatchUsesOneJointPlanAndNeverEitherIndependentMutator() throws Exception {
        for(long seed:new long[]{0,1,677,20261005658L}) {
            var f=new EarlyGateFixture();f.nativeOwners();f.populateExactSource();byte[] before=f.memory.clone();
            var settings=new Settings();settings.setChangeImpossibleEvolutions(true);settings.setRemoveTimeBasedEvolutions(true);
            var restored=Settings.fromString(withSerializationDefaults(settings).toString());
            assertTrue(restored.isChangeImpossibleEvolutions());assertTrue(restored.isRemoveTimeBasedEvolutions());
            var method=GameRandomizer.class.getDeclaredMethod("maybeApplyEvolutionImprovements");method.setAccessible(true);
            var game=randomizer(f,restored);var rng=GameRandomizer.class.getDeclaredField("randomSource");rng.setAccessible(true);
            ((com.uprfvx.random.random.RandomSource)rng.get(game)).seed(seed);method.invoke(game);
            assertEquals(1,f.combinedRemovals);assertEquals(0,f.impossibleRemovals);assertEquals(0,f.removals);
            assertArrayEquals(before,f.memory);f.write();f.loadEvolutions();
            assertEquals(7,f.word(459,0,0));assertEquals(93,f.word(459,0,2));
            assertEquals(7,f.word(486,0,0));assertEquals(94,f.word(486,0,2));
            assertEquals(4,f.word(1365,0,0));assertEquals(6,f.word(123,0,0));assertEquals(6,f.word(123,2,0));
            assertEquals(6,f.word(373,2,0));assertEquals(6,f.word(373,3,0));
        }
    }

    @Test
    void guiLoadsAndSavesEveryPokemonPaletteSetting() throws IOException {
        String source = Files.readString(randomizerGuiSourcePath());
        String loadSettings = methodBody(source, "private void restoreStateFromSettings(Settings settings)");
        String saveSettings = methodBody(source, "private Settings createSettingsFromState(CustomNamesSet customNames)");

        assertTrue(loadSettings.contains("ppalUnchangedRadioButton.setSelected(settings.getPokemonPalettesMod() == Settings.PokemonPalettesMod.UNCHANGED);"));
        assertTrue(loadSettings.contains("ppalRandomRadioButton.setSelected(settings.getPokemonPalettesMod() == Settings.PokemonPalettesMod.RANDOM);"));
        assertTrue(loadSettings.contains("ppalFollowTypesCheckBox.setSelected(settings.isPokemonPalettesFollowTypes());"));
        assertTrue(loadSettings.contains("ppalFollowEvolutionsCheckBox.setSelected(settings.isPokemonPalettesFollowEvolutions());"));
        assertTrue(loadSettings.contains("ppalShinyFromNormalCheckBox.setSelected(settings.isPokemonPalettesShinyFromNormal());"));

        assertTrue(saveSettings.contains("settings.setPokemonPalettesMod(ppalUnchangedRadioButton.isSelected(), ppalRandomRadioButton.isSelected());"));
        assertTrue(saveSettings.contains("settings.setPokemonPalettesFollowTypes(ppalFollowTypesCheckBox.isSelected());"));
        assertTrue(saveSettings.contains("settings.setPokemonPalettesFollowEvolutions(ppalFollowEvolutionsCheckBox.isSelected());"));
        assertTrue(saveSettings.contains("settings.setPokemonPalettesShinyFromNormal(ppalShinyFromNormalCheckBox.isSelected());"));
    }

    private static Settings withSerializationDefaults(Settings settings) {
        settings.setRomName("SYNTHETIC"); settings.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST); return settings;
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

    private static Path randomizerGuiSourcePath() {
        Path moduleRelative = Path.of("src/main/java/com/uprfvx/random/gui/RandomizerGUI.java");
        if (Files.isRegularFile(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("random/src/main/java/com/uprfvx/random/gui/RandomizerGUI.java");
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
