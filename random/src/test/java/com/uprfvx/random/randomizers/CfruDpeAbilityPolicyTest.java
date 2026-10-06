package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.BattleStyle;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.services.AbilityRandomizationPolicy;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exact source constants and synthetic species/memory only. Runs the production randomizer. */
class CfruDpeAbilityPolicyTest {
    private static final long[] SEEDS = {0, 1, 25, 672, 641, 20261006};
    private static final int[] COLLISIONS = {129, 112, 103, 100, 72, 73, 74, 76, 77};
    private static final AbilityRandomizationPolicy POLICY = AbilityRandomizationPolicy.cfruDpe();

    private static List<String> resource(String name) {
        var input = CfruDpeAbilityPolicyTest.class.getResourceAsStream("/cfru-dpe/" + name);
        assertNotNull(input);
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().toList();
    }

    private static Map<String, Integer> sourceConstants() {
        Map<String, Integer> constants = new LinkedHashMap<>();
        var define = Pattern.compile("^#define (ABILITY_\\w+) (0x[0-9A-Fa-f]+|[0-9]+|ABILITY_\\w+)\\b");
        resource("abilities.h").forEach(line -> {
            var match = define.matcher(line);
            if (match.find()) {
                String value = match.group(2);
                assertNull(constants.put(match.group(1), value.startsWith("ABILITY_")
                        ? constants.get(value) : Integer.decode(value)));
            }
        });
        return constants;
    }

    @Test
    void fullPinnedInventoryHasOnePrimaryOwnerPerNumericIdAndEveryAliasSharesIt() {
        var source = sourceConstants();
        Set<Integer> ids = new TreeSet<>();
        Set<String> aliases = new TreeSet<>();
        List<Integer> candidates = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (String line : resource("ability-id-inventory.tsv")) {
            if (line.startsWith("#")) continue;
            String[] c = line.split("\t", -1);
            int id = Integer.parseInt(c[0]);
            assertTrue(ids.add(id), "ambiguous numeric ownership " + id);
            assertEquals(id, Integer.decode(c[1]));
            assertEquals(id, source.get(c[2]));
            counts.merge(c[3], 1, Integer::sum);
            if (c[3].equals("RANDOMIZABLE_PRIMARY")) candidates.add(id);
            else {
                assertEquals("UNSAFE_EXCLUDE", c[3]);
                assertTrue(Set.of(59, 208).contains(id));
            }
            if (!c[4].isEmpty()) for (String alias : c[4].split(",")) {
                assertTrue(aliases.add(alias));
                assertEquals(id, source.get(alias), alias);
            }
        }
        assertEquals(new TreeSet<>(IntStream.rangeClosed(1, 254).boxed().toList()), ids);
        assertEquals(Map.of("RANDOMIZABLE_PRIMARY", 252, "UNSAFE_EXCLUDE", 2), counts);
        assertEquals(34, aliases.size());
        assertEquals(255 + 34, source.size()); // NONE plus all numeric owners and explicit aliases
        assertEquals(candidates, POLICY.candidateIds());
        assertEquals(Set.of(59, 208), POLICY.useless());
        assertEquals(179, source.get("ABILITY_BEADSOFRUIN")); // Stall's numeric owner remains negative
        assertEquals(129, source.get("ABILITY_SHARPNESS")); // Strong Jaw's one numeric ticket
        assertEquals(57, source.get("ABILITY_POISONPUPPETEER")); // Plus's numeric owner remains bad
        assertThrows(UnsupportedOperationException.class, () -> POLICY.candidateIds().add(255));
        assertThrows(UnsupportedOperationException.class, () -> POLICY.variations().get(4).add(77));
    }

    @Test
    void semanticClassesResolveFromCfruNamesRatherThanGenericIds() {
        var src = sourceConstants();
        assertEquals(src.get("ABILITY_WONDERGUARD"), POLICY.wonderGuard());
        assertEquals(ids(src, "SHADOWTAG", "MAGNETPULL", "ARENATRAP"), POLICY.trapping());
        assertEquals(ids(src, "DEFEATIST", "SLOWSTART", "TRUANT", "KLUTZ", "STALL"), POLICY.negative());
        assertEquals(ids(src, "MINUS", "PLUS", "ANTICIPATION", "FOREWARN", "FRISK", "HONEYGATHER", "AURABREAK", "RECEIVER"), POLICY.bad());
        assertEquals(ids(src, "FRIENDGUARD", "HEALER", "TELEPATHY", "SYMBIOSIS", "BATTERY"), POLICY.doubleBattle());
        assertFalse(src.containsKey("ABILITY_POWEROFALCHEMY"));
        assertFalse(POLICY.bad().contains(src.get("ABILITY_CURIOUSMEDICINE")));
        assertEquals(Set.of(src.get("ABILITY_SHELLARMOR")), POLICY.duplicateExclusions());
        assertEquals(Map.of(4, List.of(4, 75)), POLICY.variations());
        String[] names = {"STRONGJAW", "QUICKFEET", "DOWNLOAD", "UNSEENFIST", "TRANSISTOR", "DRAGONSMAW", "NEUTRALIZINGGAS", "HUNGERSWITCH", "LINGERINGAROMA"};
        for (int i = 0; i < COLLISIONS.length; i++) assertEquals(COLLISIONS[i], src.get("ABILITY_" + names[i]));
    }

    private static Set<Integer> ids(Map<String, Integer> src, String... names) {
        Set<Integer> ids = new HashSet<>();
        for (String name : names) ids.add(src.get("ABILITY_" + name));
        return ids;
    }

    private static Settings unbanned() {
        Settings settings = new Settings();
        settings.setAbilitiesMod(Settings.AbilitiesMod.RANDOMIZE);
        settings.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
        settings.setRomName("ROM-free #672 fixture");
        settings.setAllowWonderGuard(true);
        settings.setBanTrappingAbilities(false);
        settings.setBanNegativeAbilities(false);
        settings.setBanBadAbilities(false);
        settings.setAbilitiesFollowEvolutions(false);
        settings.setAbilitiesFollowMegaEvolutions(false);
        settings.setWeighDuplicateAbilitiesTogether(false);
        settings.setEnsureTwoAbilities(false);
        return settings;
    }

    private static Settings ironmon() {
        Settings settings = unbanned();
        settings.setAllowWonderGuard(false);
        settings.setBanTrappingAbilities(true);
        settings.setBanNegativeAbilities(true);
        settings.setBanBadAbilities(true);
        return settings;
    }

    private static CfruDpeEvolutionFixture fixture(int size) throws Exception {
        var f = new CfruDpeEvolutionFixture();
        f.pool.removeIf(sp -> sp.getNumber() > size);
        for (Species sp : f.pool) {
            sp.setAbility1(1); sp.setAbility2(2); sp.setAbility3(3);
        }
        f.restrictions();
        return f;
    }

    /** Attempts the requested numeric candidate first, then cycles through safe fallback choices. */
    private static class CandidateRandom extends Random {
        private final int firstIndex;
        private int calls;
        CandidateRandom(int id) { firstIndex = POLICY.candidateIds().indexOf(id); assertTrue(firstIndex >= 0); }
        @Override public int nextInt(int bound) {
            assertTrue(++calls < 1000, "candidate rejection must terminate");
            return calls == 1 ? firstIndex : (calls - 2) % bound;
        }
        @Override public double nextDouble() { return 1; } // Slot 2 absent under Ensure Two OFF
    }

    private static Set<Integer> emitted(Species sp) {
        Set<Integer> values = new HashSet<>(List.of(sp.getAbility1(), sp.getAbility2(), sp.getAbility3()));
        values.remove(0);
        return values;
    }

    private static void assertCandidateSpace(Settings settings, Set<Integer> banned) throws Exception {
        var f = fixture(1);
        for (int id : POLICY.candidateIds()) {
            Species sp = f.species[1];
            sp.setAbility1(1); sp.setAbility2(2); sp.setAbility3(3);
            new SpeciesAbilityRandomizer(f, settings, new CandidateRandom(id)).randomizeAbilities();
            if (!banned.contains(id)) assertEquals(id, sp.getAbility1(), "selectable numeric owner " + id);
            else assertNotEquals(id, sp.getAbility1(), "ban must reject " + id);
            assertTrue(Collections.disjoint(banned, emitted(sp)), "emitted banned ability " + id);
            assertTrue(POLICY.candidateIds().containsAll(emitted(sp)));
            assertEquals(0, sp.getAbility2());
            assertNotEquals(sp.getAbility1(), sp.getAbility3());
        }
    }

    private static Set<Integer> ironmonBans() {
        Set<Integer> banned = new HashSet<>(List.of(25));
        banned.addAll(POLICY.trapping()); banned.addAll(POLICY.negative());
        banned.addAll(POLICY.bad()); banned.addAll(POLICY.doubleBattle());
        return banned;
    }

    @Test
    void everyCandidateIsSelectableWithoutOptionalBansIncludingAllActualNegatives() throws Exception {
        assertCandidateSpace(unbanned(), Set.of());
    }

    @Test
    void individualBansAndExactIronmonCandidateSpaceUseNumericRuntimeOwnership() throws Exception {
        Settings settings = unbanned(); settings.setAllowWonderGuard(false);
        assertCandidateSpace(settings, Set.of(25));
        settings = unbanned(); settings.setBanTrappingAbilities(true);
        assertCandidateSpace(settings, POLICY.trapping());
        settings = unbanned(); settings.setBanNegativeAbilities(true);
        assertCandidateSpace(settings, POLICY.negative());
        settings = unbanned(); settings.setBanBadAbilities(true);
        Set<Integer> badSingles = new HashSet<>(POLICY.bad()); badSingles.addAll(POLICY.doubleBattle());
        assertCandidateSpace(settings, badSingles);
        settings.getBattleStyle().setModification(BattleStyle.Modification.SINGLE_STYLE);
        settings.getBattleStyle().setStyle(BattleStyle.Style.DOUBLE_BATTLE);
        assertCandidateSpace(settings, POLICY.bad());
        assertEquals(230, POLICY.candidateIds().size() - ironmonBans().size());
        assertCandidateSpace(ironmon(), ironmonBans());
    }

    @Test
    void duplicateWeightingNeverRejectsRepurposedIdsOrVariesCloudNineToLingeringAroma() throws Exception {
        Settings settings = ironmon(); settings.setWeighDuplicateAbilitiesTogether(true);
        var f = fixture(1);
        for (int id : COLLISIONS) {
            f.species[1].setAbility1(1);
            new SpeciesAbilityRandomizer(f, settings, new CandidateRandom(id)).randomizeAbilities();
            assertEquals(id, f.species[1].getAbility1());
        }
        new SpeciesAbilityRandomizer(f, settings, new CandidateRandom(13)).randomizeAbilities();
        assertEquals(13, f.species[1].getAbility1());
        assertFalse(emitted(f.species[1]).contains(77));
        assertFalse(POLICY.variations().containsKey(13));
        // Shell Armor is excluded as an independent ticket but remains a genuine variation of Battle Armor.
        Queue<Integer> draws = new ArrayDeque<>(List.of(POLICY.candidateIds().indexOf(75),
                POLICY.candidateIds().indexOf(4), 1, POLICY.candidateIds().indexOf(13)));
        Random random = new Random() {
            @Override public int nextInt(int bound) { int value = draws.remove(); assertTrue(value < bound); return value; }
            @Override public double nextDouble() { return 1; }
        };
        new SpeciesAbilityRandomizer(f, settings, random).randomizeAbilities();
        assertEquals(75, f.species[1].getAbility1());
        assertEquals(13, f.species[1].getAbility3());
        assertTrue(draws.isEmpty());
    }

    @Test
    void exactIronmonSeedMatrixThreeSlotsAndDeterministicReplay() throws Exception {
        Settings settings = Settings.fromString(ironmon().toString());
        assertEquals(Settings.AbilitiesMod.RANDOMIZE, settings.getAbilitiesMod());
        assertFalse(settings.isAllowWonderGuard()); assertTrue(settings.isBanTrappingAbilities());
        assertTrue(settings.isBanNegativeAbilities()); assertTrue(settings.isBanBadAbilities());
        assertFalse(settings.isAbilitiesFollowEvolutions()); assertFalse(settings.isAbilitiesFollowMegaEvolutions());
        assertFalse(settings.isWeighDuplicateAbilitiesTogether()); assertFalse(settings.isEnsureTwoAbilities());
        for (long seed : SEEDS) {
            var f = fixture(1439); var replay = fixture(1439);
            new SpeciesAbilityRandomizer(f, settings, new Random(seed)).randomizeAbilities();
            new SpeciesAbilityRandomizer(replay, settings, new Random(seed)).randomizeAbilities();
            Set<Integer> seen = new HashSet<>(); int zeroSlot2 = 0;
            for (Species sp : f.pool) {
                assertArrayEquals(tuple(sp), tuple(replay.species[sp.getNumber()]), "seed " + seed);
                assertTrue(POLICY.candidateIds().containsAll(emitted(sp)));
                assertTrue(Collections.disjoint(ironmonBans(), emitted(sp)));
                assertTrue(sp.getAbility1() > 0 && sp.getAbility3() > 0);
                assertNotEquals(sp.getAbility1(), sp.getAbility3());
                if (sp.getAbility2() == 0) zeroSlot2++;
                else { assertNotEquals(sp.getAbility1(), sp.getAbility2()); assertNotEquals(sp.getAbility2(), sp.getAbility3()); }
                for (int id : tuple(sp)) assertTrue(id >= 0 && id <= 0xFE);
                seen.addAll(emitted(sp));
            }
            assertTrue(zeroSlot2 > 0 && zeroSlot2 < f.pool.size());
            Set<Integer> expected = new HashSet<>(POLICY.candidateIds()); expected.removeAll(ironmonBans());
            assertEquals(expected, seen, "complete 230-ID coverage at seed " + seed);
            for (int id : COLLISIONS) assertTrue(seen.contains(id));
            System.out.println("seed=" + seed + " species=" + f.pool.size() + " distinct=" + seen.size()
                    + " zeroSlot2=" + zeroSlot2 + " replay=PASS");
        }
    }

    @Test
    void actualRandomizerThreeSlotTuplesWriteAndReloadThroughExistingGen3Writer() throws Exception {
        var loader = Gen3RomHandler.class.getDeclaredMethod("loadBasicPokeStats", Species.class, int.class);
        var writer = Gen3RomHandler.class.getDeclaredMethod("saveBasicPokeStats", Species.class, int.class);
        loader.setAccessible(true); writer.setAccessible(true);
        int[] offsets = {Gen3Constants.bsAbility1Offset, Gen3Constants.bsAbility2Offset, Gen3Constants.bsHiddenAbilityOffset};
        for (long seed : SEEDS) {
            var f = fixture(30);
            f.setField("items", new ArrayList<>());
            for (Species sp : f.pool) {
                int base = 0x40000 + sp.getNumber() * Gen3Constants.baseStatsEntrySize;
                Arrays.fill(f.memory, base, base + 6, (byte) 50);
                for (int slot = 0; slot < 3; slot++) f.memory[base + offsets[slot]] = (byte) (slot + 1);
                loader.invoke(f, sp, base);
            }
            new SpeciesAbilityRandomizer(f, ironmon(), new Random(seed)).randomizeAbilities();
            for (Species sp : f.pool) {
                int[] expected = tuple(sp);
                // #641 intentionally preserves legacy slot-1 fallback for a changed tuple.
                if (expected[1] == 0) expected[1] = expected[0];
                int base = 0x40000 + sp.getNumber() * Gen3Constants.baseStatsEntrySize;
                writer.invoke(f, sp, base); writer.invoke(f, sp, base);
                for (int slot = 0; slot < 3; slot++) assertEquals(expected[slot], f.memory[base + offsets[slot]] & 0xFF);
                loader.invoke(f, sp, base);
                assertArrayEquals(expected, tuple(sp));
                assertTrue(Collections.disjoint(ironmonBans(), emitted(sp)));
                assertTrue(POLICY.candidateIds().containsAll(emitted(sp)));
                writer.invoke(f, sp, base);
                for (int slot = 0; slot < 3; slot++) assertEquals(expected[slot], f.memory[base + offsets[slot]] & 0xFF);
            }
        }
    }

    @Test
    void loadedWonderGuardAndInvalidSpeciesKeepLegacyPreservationGuards() throws Exception {
        var f = fixture(5);
        f.species[1].setAbility1(POLICY.wonderGuard());
        f.species[2].setAbility1(255);
        f.species[3].setAbility1(0); f.species[3].setAbility2(0); f.species[3].setAbility3(0);
        f.species[4].setAbility1(-1);
        int[][] before = f.pool.stream().map(CfruDpeAbilityPolicyTest::tuple).toArray(int[][]::new);
        new SpeciesAbilityRandomizer(f, ironmon(), new Random(672)).randomizeAbilities();
        for (int i = 1; i <= 4; i++) assertArrayEquals(before[i - 1], tuple(f.species[i]));
        assertFalse(Arrays.equals(before[4], tuple(f.species[5])));
    }

    @Test
    void nonCfruPolicyAndProductionSeedReplayPreserveGenericListsAndGen3Variations() throws Exception {
        var f = fixture(100); f.setField("useCfruDpeGen9SpeciesCount", false);
        var p = f.getAbilityRandomizationPolicy();
        assertEquals(IntStream.rangeClosed(1, 77).boxed().toList(), p.candidateIds());
        assertEquals(Set.copyOf(GlobalConstants.battleTrappingAbilities), p.trapping());
        assertEquals(Set.copyOf(GlobalConstants.negativeAbilities), p.negative());
        assertEquals(Set.copyOf(GlobalConstants.badAbilities), p.bad());
        assertEquals(Set.copyOf(GlobalConstants.doubleBattleAbilities), p.doubleBattle());
        assertEquals(Set.copyOf(Gen3Constants.uselessAbilities), p.useless());
        Set<Integer> dup = new HashSet<>(GlobalConstants.duplicateAbilities); dup.add(77);
        assertEquals(dup, p.duplicateExclusions());
        assertEquals(Gen3Constants.abilityVariations, p.variations());
        assertEquals(List.of(13, 77), p.variations().get(13));
        for (long seed : SEEDS) {
            for (boolean weighting : new boolean[] {false, true}) {
                for (Species sp : f.pool) { sp.setAbility1(1); sp.setAbility2(2); sp.setAbility3(0); }
                Settings settings = ironmon(); settings.setWeighDuplicateAbilitiesTogether(weighting);
                List<int[]> expected = legacyTuples(seed, settings, f.pool.size());
                new SpeciesAbilityRandomizer(f, settings, new Random(seed)).randomizeAbilities();
                for (int i = 0; i < f.pool.size(); i++) assertArrayEquals(expected.get(i), tuple(f.pool.get(i)), "legacy seed=" + seed + " weighting=" + weighting);
            }
        }
    }

    /** Frozen pre-policy Gen3 rejection/variation behavior, to catch random sequence changes. */
    private static List<int[]> legacyTuples(long seed, Settings settings, int count) {
        Random random = new Random(seed);
        Set<Integer> banned = new HashSet<>(Gen3Constants.uselessAbilities);
        banned.add(25); banned.addAll(GlobalConstants.battleTrappingAbilities);
        banned.addAll(GlobalConstants.negativeAbilities); banned.addAll(GlobalConstants.badAbilities);
        banned.addAll(GlobalConstants.doubleBattleAbilities);
        if (settings.isWeighDuplicateAbilitiesTogether()) { banned.addAll(GlobalConstants.duplicateAbilities); banned.add(77); }
        List<int[]> tuples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int first = legacyPick(random, banned, settings.isWeighDuplicateAbilitiesTogether());
            int second = random.nextDouble() < 0.5 ? legacyPick(random, banned, settings.isWeighDuplicateAbilitiesTogether(), first) : 0;
            tuples.add(new int[] {first, second, 0});
        }
        return tuples;
    }

    private static int legacyPick(Random random, Set<Integer> banned, boolean weight, int... already) {
        while (true) {
            int id = random.nextInt(77) + 1;
            if (banned.contains(id) || contains(already, id)) continue;
            if (weight) while (true) {
                List<Integer> variants = Gen3Constants.abilityVariations.get(id);
                int variation = variants == null ? id : variants.get(random.nextInt(variants.size()));
                if (!contains(already, variation)) return variation;
            }
            return id;
        }
    }

    private static boolean contains(int[] values, int id) { return Arrays.stream(values).anyMatch(value -> value == id); }
    private static int[] tuple(Species sp) { return new int[] {sp.getAbility1(), sp.getAbility2(), sp.getAbility3()}; }
}
