package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.MiscTweak;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import com.uprfvx.romio.services.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Actual selection consumers + actual Gen3 asset validation, using synthetic memory only. */
class CfruDpeSpeciesPoolSafetyTest {
    private static final int GEN9 = 0x50E, REGIONAL = 0x3FC, BAD_ASSET = 0x50F;
    private static final int[] UNSAFE = {0x19C, 0xFC, 0x365, 0x38D, 0x4EC, 0x4B7,
            0x54E, 0x59D, 0x59E, 0x592, 0x341, 0x417, 0x4CF, 0x2E1, 0x2EA,
            0x2EF, 0x346, 0x347, 0x3DF, 0x430, 0x439, 0x4A7, 0x4A8, 0x4B2,
            0x4B4, 0x4B5, 0x4B6, 0x2C2, 0x343, 0x344, 0x40C, 0x4CC, 0x33D,
            0x2D0, 0x2EB, 0x2F5, 0x418};
    private static final Set<Integer> SAFE = Set.of(GEN9, REGIONAL);

    @Test
    void detectedPolicyCombinesFormsStatsLearnsetsAndRealPointerDiagnostics() throws Exception {
        Fixture f = new Fixture(true);
        assertTrue(f.usesCfruDpeRandomPoolPolicy());
        assertEquals(SAFE, identities(f.restricted.getAll(true)));
        for (int id : UNSAFE) assertTrue(f.reason(id).isPresent(), "unsafe " + id);
        assertEquals("invalid/missing front battle sprite pointer", f.reason(BAD_ASSET).orElseThrow());
        assertTrue(f.reason(GEN9).isEmpty());
        assertTrue(f.reason(REGIONAL).isEmpty());
        assertEquals("ordinary species: valid baseline data/assets",
                f.getCfruDpeRandomPoolEligibility(f.species(GEN9), f.movesets).reason());
        assertEquals("regional form: valid baseline data/assets",
                f.getCfruDpeRandomPoolEligibility(f.species(REGIONAL), f.movesets).reason());
        f.pool.getFirst().setHp(0);
        assertEquals("invalid required base stats/type", f.reason(GEN9).orElseThrow());
    }

    @Test
    void paletteAndLearnsetFailuresAreDeterministicAndNeighborStillAllowed() throws Exception {
        Fixture f = new Fixture(true);
        f.writeFixturePointer(f.paletteOffset + REGIONAL * 8, 0);
        assertEquals("invalid/missing normal palette pointer", f.reason(REGIONAL).orElseThrow());
        f.writeFixturePointer(f.paletteOffset + REGIONAL * 8, 0x9100);
        for (List<MoveLearnt> invalid : List.of(List.<MoveLearnt>of(),
                List.of(new MoveLearnt(0, 1)), List.of(new MoveLearnt(0xFFFF, 1)))) {
            f.movesets.put(GEN9, invalid);
            assertEquals("no usable learnset", f.reason(GEN9).orElseThrow());
            assertTrue(f.reason(REGIONAL).isEmpty());
        }
        assertTrue(f.reason(0x365).isPresent());
        Fixture neighbor = new Fixture(true);
        assertTrue(neighbor.reason(GEN9).isEmpty());
    }

    @Test
    void restrictionsAndRelativeExpansionCannotReintroduceUnsafeRows() throws Exception {
        Fixture f = new Fixture(true);
        Species child = f.species(0x54E);
        Species parent = f.species(GEN9);
        child.setGeneration(1); // Must be introduced through relatives, then rejected again.
        Evolution evolution = new Evolution(parent, child, EvolutionType.LEVEL, 20);
        parent.getEvolutionsFrom().add(evolution);
        child.getEvolutionsTo().add(evolution);
        GenRestrictions restrictions = new GenRestrictions(0);
        restrictions.setGenAllowed(9, true);
        restrictions.setAllowEvolutionaryRelatives(true);
        f.restricted.setRestrictions(restrictions, SpecialFormExclusionOptions.allowAllSpecialForms());
        assertEquals(Set.of(GEN9), identities(f.restricted.getAll(true)));
    }

    @Test
    void wildActuallySelectsGen9AndRegionalAndNeverUnsafeRows() throws Exception {
        Set<Integer> seen = new HashSet<>();
        Random seeds = new Random(664);
        for (int run = 0; run < 32; run++) {
            Fixture f = new Fixture(true);
            new WildEncounterRandomizer(f, settings(), new Random(seeds.nextLong())).randomizeEncounters();
            assertTrue(f.encounterWrites > 0);
            for (Encounter e : f.areas.getFirst()) seen.add(e.getSpecies().getSpeciesSetIdentityNumber());
        }
        assertEquals(SAFE, seen);
    }

    @Test
    void trainerActuallySelectsGen9AndRegionalWithExplicitLearnsetIdentity() throws Exception {
        Set<Integer> seen = new HashSet<>();
        Random seeds = new Random(665);
        for (int run = 0; run < 32; run++) {
            Fixture f = new Fixture(true);
            new TrainerPokemonRandomizer(f, settings(), new Random(seeds.nextLong())).randomizeTrainerPokes();
            for (TrainerPokemon tp : f.trainers.getFirst().getPokemon()) {
                Species chosen = tp.getSpecies();
                seen.add(chosen.getSpeciesSetIdentityNumber());
                assertArrayEquals(new int[] {33, 0, 0, 0},
                        f.getMovesAtLevel(chosen, f.movesets, tp.getLevel()));
            }
        }
        assertEquals(SAFE, seen);
    }

    @Test
    void starterActualLineupContainsBothSafeControls() throws Exception {
        Fixture f = new Fixture(true);
        new StarterRandomizer(f, settings(), new Random(664)).randomizeStarters();
        assertEquals(SAFE, identities(f.writtenStarters));
    }

    @Test
    void customStarterCannotBypassBaselineButSafeCustomIdsStillWork() throws Exception {
        for (int id : new int[] {0x54E, BAD_ASSET, 0x365}) {
            Fixture f = new Fixture(true);
            Settings s = settings();
            s.setStartersMod(Settings.StartersMod.CUSTOM);
            s.setCustomStarters(new int[] {f.getSpeciesInclFormes().indexOf(f.species(id)), 0, 0});
            assertThrows(RandomizationException.class,
                    () -> new StarterRandomizer(f, s, new Random(664)).randomizeStarters());
            assertNull(f.writtenStarters);
        }
        Fixture f = new Fixture(true);
        Settings s = settings();
        s.setStartersMod(Settings.StartersMod.CUSTOM);
        s.setCustomStarters(new int[] {1, 2, 0});
        new StarterRandomizer(f, s, new Random(664)).randomizeStarters();
        assertEquals(SAFE, identities(f.writtenStarters));
    }

    @Test
    void staticSharedGiftOwnerKeepsNullScriptPlaceholderAndSelectsBothControls() throws Exception {
        Fixture f = new Fixture(true);
        new StaticPokemonRandomizer(f, settings(), new Random(664)).randomizeStaticPokemon();
        assertTrue(f.staticWrites > 0);
        assertNull(f.statics.getFirst().getSpecies());
        assertEquals(7, f.statics.getFirst().getLevel());
        assertEquals(SAFE, identities(f.statics.stream().skip(1).map(StaticEncounter::getSpecies).toList()));
    }

    @Test
    void tradesGivenAndRequestedUseBaselineAndRetainSkipGuards() throws Exception {
        Fixture f = new Fixture(true);
        TradeRandomizer r = new TradeRandomizer(f, settings(), new Random(664));
        r.randomizeIngameTrades();
        assertTrue(f.tradeWrites > 0);
        InGameTrade randomized = f.trades.getFirst();
        assertEquals(SAFE, identities(List.of(randomized.getGivenSpecies(), randomized.getRequestedSpecies())));
        assertEquals(1, r.getSkippedNullRequestedSpeciesTrades());
        assertEquals(1, r.getSkippedUnsafeSpeciesTrades());
        assertNull(f.trades.get(1).getRequestedSpecies());
        assertEquals("?", f.trades.get(2).getGivenSpecies().getName());
    }

    @Test
    void introAndTutorialGlobalPoolsUseBaselineBeforeWriterGuards() throws Exception {
        Set<Integer> seen = new HashSet<>();
        Random seeds = new Random(666);
        for (int run = 0; run < 32; run++) {
            Fixture f = new Fixture(true);
            IntroPokemonRandomizer r = new IntroPokemonRandomizer(f, settings(), new Random(seeds.nextLong()));
            r.randomizeIntroPokemon();
            assertTrue(r.isChangesMade());
            seen.add(r.getIntroSpecies().getSpeciesSetIdentityNumber());
            Settings s = settings();
            s.setCurrentMiscTweaks(MiscTweak.RANDOMIZE_CATCHING_TUTORIAL.getValue());
            new MiscTweakRandomizer(f, s, new Random(seeds.nextLong())).applyMiscTweaks();
            assertEquals(1, f.tutorialWrites);
        }
        assertEquals(SAFE, seen);
    }

    @Test
    void nonCfruHandlerDoesNotAcquireNewBansAndGenericFormOptionsStillApply() throws Exception {
        Fixture f = new Fixture(false);
        assertFalse(f.usesCfruDpeRandomPoolPolicy());
        assertEquals(new HashSet<>(f.pool), f.restricted.getAll(true));
        assertTrue(f.reason(BAD_ASSET).isEmpty());
        assertTrue(f.reason(0x54E).isEmpty());
        f.species(0x365).addSpecialFormCategory(SpecialFormCategory.MEGA);
        f.restricted.setRestrictions(null, SpecialFormExclusionOptions.defaults());
        assertFalse(f.restricted.getAll(true).contains(f.species(0x365)));
        assertTrue(f.restricted.getAll(true).contains(f.species(0x54E)));
    }

    private static Settings settings() {
        Settings s = new Settings();
        s.setRandomizeWildPokemon(true);
        s.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        s.setAllowWildAltFormes(true);
        s.setTrainersMod(Settings.TrainersMod.RANDOM);
        s.setTrainersBlockEarlyWonderGuard(false);
        s.setAllowTrainerAlternateFormes(true);
        s.setStartersMod(Settings.StartersMod.COMPLETELY_RANDOM);
        s.setAllowStarterAltFormes(true);
        s.setStaticPokemonMod(Settings.StaticPokemonMod.COMPLETELY_RANDOM);
        s.setAllowStaticAltFormes(true);
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setBanIrregularAltFormes(false);
        s.setAbilitiesMod(Settings.AbilitiesMod.RANDOMIZE);
        return s;
    }

    private static Set<Integer> identities(Collection<Species> species) {
        Set<Integer> result = new HashSet<>();
        species.forEach(sp -> result.add(sp.getSpeciesSetIdentityNumber()));
        return result;
    }

    private static final class Fixture extends Gen3RomHandler {
        final List<Species> pool = new ArrayList<>();
        final Map<Integer, List<MoveLearnt>> movesets = new HashMap<>();
        final RestrictedSpeciesService restricted;
        final byte[] memory = new byte[0x10000];
        final int frontOffset = 0x100, paletteOffset = 0x3100;
        List<EncounterArea> areas;
        List<Trainer> trainers;
        List<StaticEncounter> statics;
        List<InGameTrade> trades;
        List<Species> writtenStarters;
        int encounterWrites, staticWrites, tradeWrites, tutorialWrites;

        Fixture(boolean cfru) throws Exception {
            var constructor = Gen3RomEntry.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            Gen3RomEntry entry = constructor.newInstance("Synthetic species pool");
            entry.setRomCode("BPRE");
            entry.putIntValue("PokemonCount", 1440);
            entry.putIntValue("PokemonFrontImages", frontOffset);
            entry.putIntValue("PokemonNormalPalettes", paletteOffset);
            setField("romEntry", entry);
            setField("isRomHack", cfru);
            setField("useCfruDpeGen9SpeciesCount", cfru);
            setField("rom", memory);
            Species[] internal = new Species[1441];
            List<Integer> ids = new ArrayList<>(List.of(GEN9, REGIONAL, BAD_ASSET));
            for (int id : UNSAFE) ids.add(id);
            for (int id : ids) {
                // Shared NatDex number deliberately differs from the explicit internal ID (#661).
                Species sp = new Species(25);
                sp.setSpeciesSetIdentityNumber(id);
                sp.setName("Species" + id);
                sp.setGeneration(id == REGIONAL ? 7 : 9);
                sp.setPrimaryType(Type.NORMAL);
                sp.setHp(50); sp.setAttack(50); sp.setDefense(50);
                sp.setSpeed(50); sp.setSpatk(50); sp.setSpdef(50);
                sp.setAbility1(1);
                pool.add(sp); internal[id] = sp;
                movesets.put(id, List.of(new MoveLearnt(33, 1)));
                writeFixturePointer(frontOffset + id * 8, id == BAD_ASSET ? 0 : 0x9000);
                writeFixturePointer(paletteOffset + id * 8, 0x9100);
            }
            setField("pokesInternal", internal);
            restricted = new RestrictedSpeciesService(this);
            restricted.setRestrictions(null, SpecialFormExclusionOptions.allowAllSpecialForms());
            Encounter a = new Encounter(); a.setSpecies(species(GEN9)); a.setLevel(5);
            Encounter b = new Encounter(); b.setSpecies(species(REGIONAL)); b.setLevel(5);
            EncounterArea area = new EncounterArea(new ArrayList<>(List.of(a, b)));
            area.setIdentifiers("Synthetic", 1, EncounterType.WALKING, "Synthetic");
            areas = List.of(area);
            TrainerPokemon tp = new TrainerPokemon(); tp.setSpecies(species(GEN9)); tp.setLevel(5);
            Trainer trainer = new Trainer(); trainer.setIndex(1); trainer.setPokemon(new ArrayList<>(List.of(tp)));
            trainers = new ArrayList<>(List.of(trainer));
            StaticEncounter placeholder = new StaticEncounter(); placeholder.setLevel(7);
            StaticEncounter gift = new StaticEncounter(); gift.setSpecies(species(GEN9)); gift.setLevel(5);
            StaticEncounter stat = new StaticEncounter(); stat.setSpecies(species(REGIONAL)); stat.setLevel(5);
            statics = List.of(placeholder, gift, stat);
            Species invalid = new Species(0); invalid.setName("?");
            trades = List.of(trade(species(GEN9), species(REGIONAL)),
                    trade(species(GEN9), null), trade(invalid, species(GEN9)));
        }

        Species species(int id) { return pool.stream().filter(sp -> sp.getSpeciesSetIdentityNumber() == id).findFirst().orElseThrow(); }
        Optional<String> reason(int id) {
            var eligibility = getCfruDpeRandomPoolEligibility(species(id), movesets);
            return eligibility.eligible() ? Optional.empty() : Optional.of(eligibility.reason());
        }
        void writeFixturePointer(int offset, int pointer) {
            int value = pointer == 0 ? 0 : pointer + 0x08000000;
            for (int i = 0; i < 4; i++) memory[offset + i] = (byte) (value >>> (8 * i));
        }
        void setField(String name, Object value) throws Exception {
            Class<?> type = Gen3RomHandler.class;
            while (type != null) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(this, value); return; }
                catch (NoSuchFieldException e) { type = type.getSuperclass(); }
            }
            throw new NoSuchFieldException(name);
        }
        static InGameTrade trade(Species given, Species requested) {
            InGameTrade trade = new InGameTrade();
            trade.setGivenSpecies(given); trade.setRequestedSpecies(requested);
            trade.setNickname(given.getName()); trade.setOtName("Test"); trade.setIVs(new int[6]);
            return trade;
        }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() { return restricted; }
        @Override public TypeService getTypeService() { return new TypeService(this); }
        @Override public TypeTable getTypeTable() { return new TypeTable(List.of(Type.NORMAL)); }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return new SpeciesSet(pool); }
        @Override public SpeciesSet getSpeciesSet() { return new SpeciesSet(pool); }
        @Override public List<Species> getSpeciesInclFormes() { List<Species> result = new ArrayList<>(); result.add(null); result.addAll(pool); return result; }
        @Override public List<Species> getSpecies() { return getSpeciesInclFormes(); }
        @Override public SpeciesSet getAltFormes() { return new SpeciesSet(); }
        @Override public SpeciesSet getIrregularFormes() { return new SpeciesSet(); }
        @Override public List<MegaEvolution> getMegaEvolutions() { return List.of(); }
        @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() { return movesets; }
        @Override public List<EncounterArea> getEncounters(boolean time) { return areas; }
        @Override public void setEncounters(boolean time, List<EncounterArea> encounters) { areas = encounters; encounterWrites++; }
        @Override public SpeciesSet getBannedForWildEncounters() { return new SpeciesSet(); }
        @Override public SpeciesSet getBannedFormesForTrainerPokemon() { return new SpeciesSet(); }
        @Override public SpeciesSet getBannedForStaticPokemon() { return new SpeciesSet(); }
        @Override public int starterCount() { return 2; }
        @Override public boolean setStarters(List<Species> starters) { writtenStarters = starters; return true; }
        @Override public List<Species> getStarters() { return List.of(species(GEN9), species(REGIONAL)); }
        @Override public List<StaticEncounter> getStaticPokemon() { return statics; }
        @Override public boolean setStaticPokemon(List<StaticEncounter> encounters) { statics = encounters; staticWrites++; return true; }
        @Override public List<InGameTrade> getInGameTrades() { return trades; }
        @Override public void setInGameTrades(List<InGameTrade> values) { trades = values; tradeWrites++; }
        @Override public Set<Item> getAllowedItems() { return Set.of(); }
        @Override public List<Trainer> getTrainers() { return trainers; }
        @Override public List<Integer> getEliteFourTrainers(boolean challenge) { return List.of(); }
        @Override public List<Integer> getMainPlaythroughTrainers() { return List.of(); }
        @Override public Species getAltFormeOfSpecies(Species species, int form) { return species; }
        @Override public int abilitiesPerSpecies() { return 0; }
        @Override public int miscTweaksAvailable() { return MiscTweak.RANDOMIZE_CATCHING_TUTORIAL.getValue(); }
        @Override public boolean setCatchingTutorial(Species player, Species opponent) {
            assertTrue(SAFE.contains(player.getSpeciesSetIdentityNumber()));
            assertTrue(SAFE.contains(opponent.getSpeciesSetIdentityNumber()));
            tutorialWrites++;
            return true;
        }
        @Override public boolean setIntroPokemon(Species species) { assertTrue(SAFE.contains(species.getSpeciesSetIdentityNumber())); return true; }
    }
}
