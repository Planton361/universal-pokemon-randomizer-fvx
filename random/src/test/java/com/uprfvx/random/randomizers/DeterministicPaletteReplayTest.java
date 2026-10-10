package com.uprfvx.random.randomizers;

import com.uprfvx.random.random.RandomSource;
import com.uprfvx.romio.graphics.palettes.RandomColorSelector;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicPaletteReplayTest {
    static final long[] SEEDS = {0, 1, 677, 20261005658L};

    @Test
    void legacyUnseededCalibrationCanChangeSameSeedColors() {
        // Two possible constructor calibrations of the exact production weight curve.
        // Model the old unseeded initializer using explicit seeds to avoid a flaky test.
        RandomColorSelector a = legacySelector(0), b = legacySelector(1);
        a.setRandom(new Random(677)); b.setRandom(new Random(677));
        boolean changed = false;
        for (int i = 0; i < 100000 && !changed; i++) changed = !a.getRandomColor().equals(b.getRandomColor());
        assertTrue(changed, "Constructor calibration survives setRandom and changes accepted colors");
    }

    static RandomColorSelector legacySelector(long calibrationSeed) {
        return new RandomColorSelector(new Random(calibrationSeed), RandomColorSelector.Mode.HSV,
            hsv -> hsv[1] * (20 <= hsv[0] && hsv[0] <= 70 ? 3 : 1),
            new double[]{0,0,0.6}, new double[]{360,1,1});
    }

    @Test
    void allEightCompositionsReplayAcrossFreshJvmsIncludingTwentySeed677Runs() throws Exception {
        for (long seed : SEEDS) {
            String first = process(seed, "combined");
            assertEquals(8, first.lines().filter(line -> line.startsWith("COMPOSITION=")).count());
            for (int i = 1; i < (seed == 677 ? 20 : 2); i++) assertEquals(first, process(seed, "combined"), "seed=" + seed + ", fresh JVM=" + (i+1));
        }
    }

    @Test
    void vanillaPaletteOwnerReplaysInFreshProcesses() throws Exception {
        // ROM-free generic RomHandler proxy, no CFRU policy or target-only adapter.
        for (long seed : SEEDS) assertEquals(process(seed, "palette"), process(seed, "palette"));
    }

    @Test
    void dataAndWorldControlsReplayAcrossFreshModels() throws Exception {
        for (long seed : SEEDS) for (boolean data : new boolean[]{true,false}) {
            byte[] first = new CombinedReplayFixture().run(seed, data, !data, 0);
            assertArrayEquals(first, new CombinedReplayFixture().run(seed, data, !data, 0));
        }
    }

    @Test
    void cosmeticConsumptionCannotChangeGameplayState() throws Exception {
        for (long seed : SEEDS) {
            RandomSource source = new RandomSource(); source.seed(seed);
            Random expected = new Random(seed);
            for (int i = 0; i < 100; i++) {
                source.getCosmetic().nextDouble();
                assertEquals(expected.nextInt(), source.getNonCosmetic().nextInt());
            }
            assertEquals(100, source.callsSinceSeedCosmetic());
            assertEquals(100, source.callsSinceSeedNonCosmetic());
            source.seed(seed);
            assertEquals(0, source.callsSinceSeed());
            assertEquals(new Random(seed).nextLong(), source.getCosmetic().nextLong());
            assertEquals(new Random(seed).nextLong(), source.getNonCosmetic().nextLong());
        }
    }

    @Test
    void selectedFollowTypesReplaysAcrossFreshJvmsAndLeavesGameplayRngUntouched() throws Exception {
        for (long seed : SEEDS) {
            String first = process(seed, "cfru-types");
            assertEquals(first, process(seed, "cfru-types"), "seed=" + seed);
        }
    }

    @Test
    void selectedTypesSeedRoundTripsAndReplayWithSkippedParentsAndAssets() throws Exception {
        for (long seed : SEEDS) assertEquals(selectedReplay(seed), selectedReplay(seed));
    }

    @Test
    void fiveCandidateIntegrationReplaysInFreshJvmsForEachIndependentImprovementAndPalette() throws Exception {
        for(long seed : SEEDS) assertEquals(process(seed,"integration"),process(seed,"integration"),"combined seed="+seed);
    }

    // Kept here because the existing standalone probe is read-only under #724.
    public static void main(String[] args) throws Exception {
        if (args[1].equals("integration")) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for(int improvement : new int[]{0,1,2}) for(int palette : new int[]{2,4})
                digest.update(CfruDpeEvolutionTargetTest.combinedReplay(Long.parseLong(args[0]),improvement,palette,true));
            System.out.println("SYNTHETIC_INTEGRATION="+HexFormat.of().formatHex(digest.digest()));
            return;
        }
        if (args[1].startsWith("gfx004") || args[1].startsWith("gfx-combined")) { gfx004Main(args); return; }
        System.out.println("SYNTHETIC_PALETTE=" + selectedReplay(Long.parseLong(args[0])));
    }

    private static String selectedReplay(long seed) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var source = new RandomSource();
        source.seed(seed);
        for (boolean evolutions : new boolean[]{false, true}) {
            var species = Gen3to5PaletteBoundsTest.chain();
            var parent = species.stream().filter(sp -> sp.getNumber() == 1).findFirst().orElseThrow();
            parent.setNormalPalette(null);
            var child = species.stream().filter(sp -> sp.getNumber() == 2).findFirst().orElseThrow();
            child.setPrimaryType(com.uprfvx.romio.gamedata.Type.FAIRY);
            child.setSecondaryType(com.uprfvx.romio.gamedata.Type.WATER);
            var expanded = Gen3to5PaletteBoundsTest.speciesWithPalette(388);
            var expandedBefore = expanded.getNormalPalette().toBytes();
            species.add(expanded);
            var settings = Gen3to5PaletteBoundsTest.paletteSettings(true, evolutions);
            settings.setRomName("SYNTHETIC");
            settings.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
            var restored = com.uprfvx.random.Settings.fromString(settings.toString());
            assertTrue(restored.isPokemonPalettesFollowTypes());
            assertFalse(restored.isPokemonPalettesShinyFromNormal());
            assertEquals(evolutions, restored.isPokemonPalettesFollowEvolutions());
            var handler = new Gen3to5PaletteBoundsTest.SyntheticGen3(species, true, "FRLG");
            var shinies = new HashMap<com.uprfvx.romio.gamedata.Species, byte[]>();
            species.forEach(sp -> shinies.put(sp, sp.getShinyPalette().toBytes()));
            var randomizer = new Gen3to5PaletteRandomizer(handler, restored, source.getCosmetic());
            randomizer.randomizePokemonPalettes();
            assertTrue(randomizer.isChangesMade());
            assertNull(parent.getNormalPalette());
            assertArrayEquals(expandedBefore, expanded.getNormalPalette().toBytes());
            for (var sp : species.stream().sorted(Comparator.comparingInt(
                    com.uprfvx.romio.gamedata.Species::getNumber)).toList()) {
                assertArrayEquals(shinies.get(sp), sp.getShinyPalette().toBytes());
                if (sp.getNormalPalette() != null) digest.update(sp.getNormalPalette().toBytes());
            }
        }
        assertEquals(0, source.callsSinceSeedNonCosmetic());
        Random gameplay = new Random(seed);
        for (int i = 0; i < 100; i++) assertEquals(gameplay.nextInt(), source.getNonCosmetic().nextInt());
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void selectedGfx004LoadedPairsReplayAcrossFreshJvmsInBothFollowModes() throws Exception {
        for (long seed : SEEDS) for (String mode : List.of("gfx004", "gfx004-follow")) {
            String first = process(seed, mode);
            assertEquals(first, process(seed, mode), "seed=" + seed + " mode=" + mode);
        }
    }

    @Test
    void selectedFollowTypesAndShinyFromNormalReplayAcrossFreshJvms() throws Exception {
        for (long seed : SEEDS) for (String mode : List.of("gfx-combined", "gfx-combined-follow")) {
            String first = process(seed, mode);
            assertEquals(first, process(seed, mode), "seed=" + seed + " mode=" + mode);
        }
    }

    @Test
    void actualGfx004ConsumesOnlyCosmeticStreamAndKeepsHundredGameplayDraws() {
        for (long seed : SEEDS) for (boolean follow : new boolean[]{false, true}) {
            RandomSource source = new RandomSource(); source.seed(seed);
            loadedPairReplay(source.getCosmetic(), follow, false);
            assertTrue(source.callsSinceSeedCosmetic() > 0);
            assertEquals(0, source.callsSinceSeedNonCosmetic());
            Random expected = new Random(seed);
            for (int i = 0; i < 100; i++) assertEquals(expected.nextInt(), source.getNonCosmetic().nextInt());
        }
    }

    @Test
    void actualCombinedPalettePathConsumesOnlyCosmeticStream() {
        for (long seed : SEEDS) for (boolean follow : new boolean[]{false, true}) {
            RandomSource source = new RandomSource(); source.seed(seed);
            loadedPairReplay(source.getCosmetic(), follow, true);
            assertTrue(source.callsSinceSeedCosmetic() > 0);
            assertEquals(0, source.callsSinceSeedNonCosmetic());
            Random expected = new Random(seed);
            for (int i = 0; i < 100; i++) assertEquals(expected.nextInt(), source.getNonCosmetic().nextInt());
        }
    }

    private static String loadedPairReplay(Random cosmetic, boolean follow, boolean followTypes) {
        var a = Gen3to5PaletteBoundsTest.speciesWithPalette(4);
        var b = Gen3to5PaletteBoundsTest.speciesWithPalette(5);
        var c = Gen3to5PaletteBoundsTest.speciesWithPalette(6);
        a.setPrimaryType(com.uprfvx.romio.gamedata.Type.FIRE);
        b.setPrimaryType(com.uprfvx.romio.gamedata.Type.WATER);
        b.setSecondaryType(com.uprfvx.romio.gamedata.Type.FLYING);
        c.setPrimaryType(com.uprfvx.romio.gamedata.Type.FAIRY);
        Gen3to5PaletteBoundsTest.link(a, b); Gen3to5PaletteBoundsTest.link(a, c);
        var species = List.of(a, b, c);
        var handler = new Gen3to5PaletteBoundsTest.SelectedHandler(new com.uprfvx.romio.gamedata.SpeciesSet(species));
        var originals = species.stream().map(sp -> sp.getNormalPalette().toBytes()).toList();
        var settings = Gen3to5PaletteBoundsTest.gfx004(follow);
        settings.setPokemonPalettesFollowTypes(followTypes);
        var randomizer = new Gen3to5PaletteRandomizer(handler, settings, cosmetic);
        randomizer.randomizePokemonPalettes();
        if (!randomizer.isChangesMade()) throw new AssertionError("Palette composition made no changes");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < species.size(); i++) {
            var sp = species.get(i);
            if (!Arrays.equals(originals.get(i), sp.getShinyPalette().toBytes())
                    || Arrays.equals(originals.get(i), sp.getNormalPalette().toBytes())) {
                throw new AssertionError("Synthetic original-normal/shiny order");
            }
            result.append(Base64.getEncoder().encodeToString(sp.getNormalPalette().toBytes()))
                    .append(':').append(Base64.getEncoder().encodeToString(sp.getShinyPalette().toBytes())).append(';');
        }
        return result.toString();
    }

    // Same-file ROM-free fresh-JVM entry point; the existing PaletteReplayProcessProbe stays untouched.
    private static void gfx004Main(String[] args) {
        long seed = Long.parseLong(args[0]);
        RandomSource source = new RandomSource(); source.seed(seed);
        boolean follow = args[1].endsWith("follow");
        boolean followTypes = args[1].startsWith("gfx-combined");
        String palettes = loadedPairReplay(source.getCosmetic(), follow, followTypes);
        StringBuilder gameplay = new StringBuilder();
        Random expected = new Random(seed);
        for (int i = 0; i < 100; i++) {
            int draw = source.getNonCosmetic().nextInt();
            if (draw != expected.nextInt()) throw new AssertionError("Gameplay RNG changed");
            gameplay.append(draw).append(',');
        }
        System.out.println("SYNTHETIC_GFX_PALETTE=" + palettes + " COSMETIC_CALLS=" + source.callsSinceSeedCosmetic()
                + " GAMEPLAY=" + gameplay);
    }

    private static String process(long seed, String composition) throws Exception {
        Set<String> classpath = new LinkedHashSet<>();
        classpath.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = DeterministicPaletteReplayTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs()) classpath.add(Path.of(url.toURI()).toString());
        }
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", String.join(File.pathSeparator, classpath), ((composition.equals("cfru-types") || composition.startsWith("gfx004") || composition.startsWith("gfx-combined") || composition.equals("integration")) ? DeterministicPaletteReplayTest.class : PaletteReplayProcessProbe.class).getName(), Long.toString(seed), composition)
            .redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Synthetic helper timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        // Only synthetic model fingerprints, never ROM data or hashes.
        String fingerprints = output.lines().filter(line -> line.startsWith("COMPOSITION=") || line.startsWith("SYNTHETIC_PALETTE=") || line.startsWith("SYNTHETIC_GFX_PALETTE=") || line.startsWith("SYNTHETIC_INTEGRATION=")).reduce("", (a,b) -> a + b + "\n");
        assertFalse(fingerprints.isEmpty(), output);
        return fingerprints;
    }
}
