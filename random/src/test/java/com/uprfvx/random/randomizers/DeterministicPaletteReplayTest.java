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
    void selectedGfx004LoadedPairsReplayAcrossFreshJvmsInBothFollowModes() throws Exception {
        for (long seed : SEEDS) for (String mode : List.of("gfx004", "gfx004-follow")) {
            String first = process(seed, mode);
            assertEquals(first, process(seed, mode), "seed=" + seed + " mode=" + mode);
        }
    }

    @Test
    void actualGfx004ConsumesOnlyCosmeticStreamAndKeepsHundredGameplayDraws() {
        for (long seed : SEEDS) for (boolean follow : new boolean[]{false, true}) {
            RandomSource source = new RandomSource(); source.seed(seed);
            loadedPairReplay(source.getCosmetic(), follow);
            assertTrue(source.callsSinceSeedCosmetic() > 0);
            assertEquals(0, source.callsSinceSeedNonCosmetic());
            Random expected = new Random(seed);
            for (int i = 0; i < 100; i++) assertEquals(expected.nextInt(), source.getNonCosmetic().nextInt());
        }
    }

    private static String loadedPairReplay(Random cosmetic, boolean follow) {
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
        var randomizer = new Gen3to5PaletteRandomizer(handler, Gen3to5PaletteBoundsTest.gfx004(follow), cosmetic);
        randomizer.randomizePokemonPalettes();
        if (!randomizer.isChangesMade()) throw new AssertionError("GFX004 made no changes");
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
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        RandomSource source = new RandomSource(); source.seed(seed);
        String palettes = loadedPairReplay(source.getCosmetic(), args[1].equals("gfx004-follow"));
        StringBuilder gameplay = new StringBuilder();
        Random expected = new Random(seed);
        for (int i = 0; i < 100; i++) {
            int draw = source.getNonCosmetic().nextInt();
            if (draw != expected.nextInt()) throw new AssertionError("Gameplay RNG changed");
            gameplay.append(draw).append(',');
        }
        System.out.println("SYNTHETIC_GFX004=" + palettes + " COSMETIC_CALLS=" + source.callsSinceSeedCosmetic()
                + " GAMEPLAY=" + gameplay);
    }

    private static String process(long seed, String composition) throws Exception {
        Set<String> classpath = new LinkedHashSet<>();
        classpath.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = DeterministicPaletteReplayTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs()) classpath.add(Path.of(url.toURI()).toString());
        }
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", String.join(File.pathSeparator, classpath), (composition.startsWith("gfx004") ? DeterministicPaletteReplayTest.class : PaletteReplayProcessProbe.class).getName(), Long.toString(seed), composition)
            .redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Synthetic helper timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        // Only synthetic model fingerprints, never ROM data or hashes.
        String fingerprints = output.lines().filter(line -> line.startsWith("COMPOSITION=") || line.startsWith("SYNTHETIC_PALETTE=") || line.startsWith("SYNTHETIC_GFX004=")).reduce("", (a,b) -> a + b + "\n");
        assertFalse(fingerprints.isEmpty(), output);
        return fingerprints;
    }
}
