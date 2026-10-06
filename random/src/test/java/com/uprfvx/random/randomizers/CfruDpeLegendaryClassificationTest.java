package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.services.*;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Production selectors, real detected handler policy and #664 checks; synthetic memory only. */
class CfruDpeLegendaryClassificationTest {
    private static final long SEED = 20261005658L;
    private static final Set<Integer> ORDINARY = Set.of(1, 0x115, 0x44E, 0x50E);
    private static final SpeciesClassificationPolicy POLICY = SpeciesClassificationPolicy.cfruDpe();

    private static Set<Integer> legendIds() {
        var input = CfruDpeLegendaryClassificationTest.class.getResourceAsStream(
                "/cfru-dpe/legendary-species-inventory.tsv");
        assertNotNull(input);
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                .filter(line -> !line.startsWith("#"))
                .map(line -> Integer.parseInt(line.split("\t")[0], 16)).collect(Collectors.toSet());
    }

    private static Settings settings() {
        Settings s = new Settings();
        s.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST); s.setRomName("Synthetic");
        s.setStartersMod(Settings.StartersMod.COMPLETELY_RANDOM);
        s.setAllowStarterAltFormes(true); s.setStartersNoLegendaries(true);
        s.setTrainersMod(Settings.TrainersMod.RANDOM);
        s.setAllowTrainerAlternateFormes(true); s.setTrainersBlockLegendaries(true);
        s.setTrainersBlockEarlyWonderGuard(true); s.setTrainersAvoidDuplicates(true);
        s.setRandomizeWildPokemon(true); s.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        s.setAllowWildAltFormes(true); s.setBlockWildLegendaries(true);
        s.setBanIrregularAltFormes(false); // #664 must protect even when generic toggles allow forms.
        return s;
    }

    private static Settings serializedSettingsWitness() {
        Settings s = settings();
        String encoded = s.toString();
        byte[] bytes = Base64.getDecoder().decode(encoded.substring(Integer.toString(Settings.VERSION).length()));
        assertEquals(0x98, bytes[29] & 0xFF);
        Settings replay = Settings.fromString(encoded);
        assertTrue(replay.isTrainersBlockLegendaries());
        assertTrue(replay.isTrainersBlockEarlyWonderGuard()); assertTrue(replay.isTrainersAvoidDuplicates());
        return replay;
    }

    private static Set<Integer> ids(Collection<Species> pool) {
        return pool.stream().map(Species::getSpeciesSetIdentityNumber).collect(Collectors.toSet());
    }

    @Test
    void casualByte98AndFixedSeedReproduceTrainer1ArceusWithOldPolicyThenExcludeIt() throws Exception {
        Settings s = serializedSettingsWitness();
        Fixture before = new Fixture(true, Set.of(1, 0x222), 1);
        assertTrue(before.usesCfruDpeRandomPoolPolicy()); // Keep the real #664 guard in the pre-fix witness.
        assertFalse(before.species[0x222].isLegendary());
        assertTrue(before.recording.getNonLegendaries(true).contains(before.species[0x222]));
        new TrainerPokemonRandomizer(before, s, new Random(SEED)).randomizeTrainerPokes();
        assertEquals(1, before.trainers.getFirst().getIndex());
        assertEquals(0x222, before.trainers.getFirst().getPokemon().getFirst().getSpecies().getSpeciesSetIdentityNumber());

        Fixture after = new Fixture(false, Set.of(1, 0x222), 1);
        Fixture replay = new Fixture(false, Set.of(1, 0x222), 1);
        new TrainerPokemonRandomizer(after, s, new Random(SEED)).randomizeTrainerPokes();
        new TrainerPokemonRandomizer(replay, s, new Random(SEED)).randomizeTrainerPokes();
        assertEquals(Set.of(1), ids(after.trainerSpecies()));
        assertEquals(ids(after.trainerSpecies()), ids(replay.trainerSpecies()));
        assertFalse(POLICY.isLegendary(after.trainerSpecies().getFirst()));
    }

    @Test
    void startersNoLegendariesConsumesPoolExcludingEveryPolicyIdentity() throws Exception {
        Fixture f = fullFixture(); assertFullPartition(f);
        new StarterRandomizer(f, serializedSettingsWitness(), new Random(SEED)).randomizeStarters();
        assertTrue(f.recording.nonLegendRequests > 0);
        assertEquals(ORDINARY, ids(f.writtenStarters));
        assertEquals(ORDINARY, f.recording.lastNonLegendaries);
    }

    @Test
    void trainerBlockLegendariesConsumesPoolExcludingEveryPolicyIdentityAndReplays() throws Exception {
        Fixture f = fullFixture(); assertFullPartition(f);
        Fixture replay = fullFixture();
        new TrainerPokemonRandomizer(f, serializedSettingsWitness(), new Random(SEED)).randomizeTrainerPokes();
        new TrainerPokemonRandomizer(replay, serializedSettingsWitness(), new Random(SEED)).randomizeTrainerPokes();
        assertTrue(f.recording.nonLegendRequests > 0);
        assertEquals(ORDINARY, f.recording.lastNonLegendaries);
        assertEquals(ORDINARY, ids(f.trainerSpecies()));
        assertEquals(f.trainerSpecies().stream().map(Species::getSpeciesSetIdentityNumber).toList(),
                replay.trainerSpecies().stream().map(Species::getSpeciesSetIdentityNumber).toList());
    }

    @Test
    void wildBlockLegendariesConsumesPoolExcludingEveryPolicyIdentity() throws Exception {
        Fixture f = fullFixture(); assertFullPartition(f);
        Settings s = serializedSettingsWitness(); s.setCatchEmAllEncounters(true);
        new WildEncounterRandomizer(f, s, new Random(SEED)).randomizeEncounters();
        assertEquals(1, f.encounterWrites);
        assertTrue(f.recording.nonLegendRequests > 0);
        assertEquals(ORDINARY, f.recording.lastNonLegendaries);
        assertEquals(ORDINARY, ids(f.areas.getFirst().stream().map(Encounter::getSpecies).toList()));
    }

    @Test
    void optionOffStillAllowsLegendaryCandidatesAndFormAssetSafetyAlwaysNarrowsFirst() throws Exception {
        Fixture f = fullFixture(); assertFullPartition(f);
        assertTrue(f.recording.getAll(true).contains(f.species[0x410])); // persistent Deoxys-A
        assertTrue(f.recording.getAll(true).contains(f.species[0x58F])); // persistent Ogerpon mask
        for (int id : new int[] {0x372, 0x38D, 0x439, 0x4B7, 0x4F9, 0x592, 0x59D, 0x54E, 0x50F}) {
            assertFalse(f.recording.getAll(true).contains(f.species[id]), "#664 still excludes " + id);
        }
        Fixture off = new Fixture(false, Set.of(1, 0x222), 1);
        Settings s = serializedSettingsWitness(); s.setTrainersBlockLegendaries(false);
        new TrainerPokemonRandomizer(off, s, new Random(SEED)).randomizeTrainerPokes();
        assertEquals(Set.of(0x222), ids(off.trainerSpecies()));
    }

    @Test
    void nonCfruPoolPartitionsAndTrainerSequenceRetainGenericSemantics() throws Exception {
        Fixture actual = new Fixture(false, Set.of(1, 0x222, 493), 1);
        Fixture old = new Fixture(true, Set.of(1, 0x222, 493), 1);
        for (Fixture f : List.of(actual, old)) {
            f.setField("useCfruDpeGen9SpeciesCount", false); f.setField("isRomHack", false);
            f.recording.setRestrictions(null, SpecialFormExclusionOptions.allowAllSpecialForms());
            assertEquals(Set.of(1, 0x222), ids(f.recording.getNonLegendaries(true)));
            assertEquals(Set.of(493), ids(f.recording.getLegendaries(true)));
        }
        for (long seed : new long[] {0, 1, 677, SEED}) {
            new TrainerPokemonRandomizer(actual, settings(), new Random(seed)).randomizeTrainerPokes();
            new TrainerPokemonRandomizer(old, settings(), new Random(seed)).randomizeTrainerPokes();
            assertEquals(ids(old.trainerSpecies()), ids(actual.trainerSpecies()), "legacy seed " + seed);
        }
    }

    private static Fixture fullFixture() throws Exception {
        Set<Integer> all = new HashSet<>(legendIds()); all.addAll(ORDINARY);
        all.add(0x54E); all.add(0x50F); // battle-only ordinary + deliberately missing front sprite
        return new Fixture(false, all, 4);
    }

    private static void assertFullPartition(Fixture f) {
        assertEquals(187, legendIds().size());
        Set<Integer> allowedLegends = new HashSet<>(legendIds());
        allowedLegends.retainAll(ids(f.recording.getAll(true)));
        assertEquals(120, allowedLegends.size(), "#664 source-safe subset of the 187 classified rows");
        assertEquals(allowedLegends, ids(f.recording.getLegendaries(true)));
        assertEquals(ORDINARY, ids(f.recording.getNonLegendaries(true)));
        assertTrue(Collections.disjoint(legendIds(), ids(f.recording.getSpecies(true, true, false))));
        assertFalse(allowedLegends.isEmpty());
    }

    private static class RecordingService extends RestrictedSpeciesService {
        int nonLegendRequests;
        Set<Integer> lastNonLegendaries;
        RecordingService(Fixture handler) { super(handler); }
        @Override public SpeciesSet getNonLegendaries(boolean forms) {
            SpeciesSet result = super.getNonLegendaries(forms);
            nonLegendRequests++; lastNonLegendaries = ids(result);
            return result;
        }
    }

    private static class Fixture extends CfruDpeEvolutionFixture {
        private final boolean preFix;
        final RecordingService recording;
        final int count;
        List<Trainer> trainers;
        List<EncounterArea> areas;
        List<Species> writtenStarters;
        int encounterWrites;
        Fixture(boolean preFix, Set<Integer> include, int count) throws Exception {
            this.preFix = preFix; this.count = count;
            pool.removeIf(sp -> !include.contains(sp.getSpeciesSetIdentityNumber()));
            for (Species sp : pool) sp.setAbility1(1);
            // Generated synthetic memory only; cause the real #664 pointer check to reject Floragato.
            Arrays.fill(memory, 0x30000 + 0x50F * 8, 0x30000 + 0x50F * 8 + 4, (byte) 0);
            recording = new RecordingService(this);
            recording.setRestrictions(null, SpecialFormExclusionOptions.allowAllSpecialForms());
            List<TrainerPokemon> team = new ArrayList<>();
            List<Encounter> encounters = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                TrainerPokemon tp = new TrainerPokemon(); tp.setSpecies(species[1]); tp.setLevel(5); team.add(tp);
                Encounter e = new Encounter(); e.setSpecies(species[1]); e.setLevel(5); encounters.add(e);
            }
            Trainer t = new Trainer(); t.setIndex(1); t.setPokemon(team); trainers = new ArrayList<>(List.of(t));
            EncounterArea area = new EncounterArea(encounters);
            area.setIdentifiers("Synthetic", 1, EncounterType.WALKING, "Synthetic"); areas = List.of(area);
        }
        List<Species> trainerSpecies() { return trainers.getFirst().getPokemon().stream().map(TrainerPokemon::getSpecies).toList(); }
        @Override public SpeciesClassificationPolicy getSpeciesClassificationPolicy() {
            return preFix ? SpeciesClassificationPolicy.legacy() : super.getSpeciesClassificationPolicy();
        }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() { return recording; }
        @Override public int starterCount() { return count; }
        @Override public boolean setStarters(List<Species> values) { writtenStarters = values; return true; }
        @Override public List<Species> getStarters() { return Collections.nCopies(count, species[1]); }
        @Override public List<Trainer> getTrainers() { return trainers; }
        @Override public List<Integer> getEliteFourTrainers(boolean challenge) { return List.of(); }
        @Override public List<Integer> getMainPlaythroughTrainers() { return List.of(); }
        @Override public SpeciesSet getBannedFormesForTrainerPokemon() { return new SpeciesSet(); }
        @Override public int abilitiesPerSpecies() { return 0; }
        @Override public Species getAltFormeOfSpecies(Species sp, int form) { return sp; }
        @Override public List<EncounterArea> getEncounters(boolean time) { return areas; }
        @Override public void setEncounters(boolean time, List<EncounterArea> values) { areas = values; encounterWrites++; }
        @Override public SpeciesSet getBannedForWildEncounters() { return new SpeciesSet(); }
    }
}
