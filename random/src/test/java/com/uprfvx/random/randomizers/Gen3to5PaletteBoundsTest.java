package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.graphics.palettes.Color;
import com.uprfvx.romio.graphics.palettes.Palette;
import com.uprfvx.romio.graphics.palettes.PaletteDescription;
import com.uprfvx.romio.graphics.palettes.PalettePartDescription;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Gen3to5PaletteBoundsTest {

    // Workspace #725 Stage A witnesses, intentionally asserting the UNREPAIRED base behavior.
    // These are blocker evidence, not regression acceptance of a GFX004 repair.
    @Test
    void selectedMissingNormalFailsAtCopyWhereGfx001FailsAtPopulation() {
        for (boolean follow : new boolean[]{false, true}) {
            Species sp = speciesWithPalette(4);
            sp.setNormalPalette(null);
            Palette shiny = sp.getShinyPalette();
            NullPointerException copy = assertThrows(NullPointerException.class,
                    () -> selected(gfx004(follow), new SpeciesSet(sp)).randomizePokemonPalettes());
            assertTrue(Arrays.stream(copy.getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals(Palette.class.getName()) && frame.getMethodName().equals("<init>")));
            assertSame(shiny, sp.getShinyPalette());
            Settings ordinary = new Settings();
            NullPointerException population = assertThrows(NullPointerException.class,
                    () -> selected(ordinary, new SpeciesSet(sp)).randomizePokemonPalettes());
            assertFalse(Arrays.stream(population.getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals(Palette.class.getName()) && frame.getMethodName().equals("<init>")));
        }
    }

    @Test
    void selectedBothMissingFailWithoutFabricatingEitherPalette() {
        Species sp = speciesWithPalette(4);
        sp.setNormalPalette(null);
        sp.setShinyPalette(null);
        assertThrows(NullPointerException.class,
                () -> selected(gfx004(false), new SpeciesSet(sp)).randomizePokemonPalettes());
        assertNull(sp.getNormalPalette());
        assertNull(sp.getShinyPalette());
    }

    @Test
    void selectedMissingShinyIsFabricatedButHandlerRejectsItsWrite() throws Exception {
        for (boolean follow : new boolean[]{false, true}) {
            Species sp = speciesWithPalette(4);
            sp.setShinyPalette(null);
            byte[] normal = sp.getNormalPalette().toBytes();
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            remember(handler, sp);
            Gen3to5PaletteRandomizer randomizer = new Gen3to5PaletteRandomizer(handler, gfx004(follow), new Random(1));
            randomizer.randomizePokemonPalettes();
            assertArrayEquals(normal, sp.getShinyPalette().toBytes());
            assertFalse(canWrite(handler, sp, sp.getShinyPalette(), null));
            assertTrue(randomizer.isChangesMade());
        }
    }

    @Test
    void selectedValidPairsProveOriginalNormalOrderForThreeTypesAndFollowModes() {
        for (boolean follow : new boolean[]{false, true}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(6);
            a.setPrimaryType(Type.FIRE);
            b.setPrimaryType(Type.WATER); b.setSecondaryType(Type.FLYING);
            c.setPrimaryType(Type.FAIRY);
            link(a, b); link(b, c);
            for (Species sp : List.of(a, b, c)) {
                sp.getNormalPalette().set(15, new Color(sp.getNumber() * 16, 64, 128));
                sp.setShinyPalette(new Palette(16, new Color(240, 16, 32)));
            }
            List<byte[]> originals = List.of(a.getNormalPalette().toBytes(), b.getNormalPalette().toBytes(), c.getNormalPalette().toBytes());
            selected(gfx004(follow), new SpeciesSet(List.of(a, b, c))).randomizePokemonPalettes();
            for (int i = 0; i < 3; i++) {
                Species sp = List.of(a, b, c).get(i);
                assertArrayEquals(originals.get(i), sp.getShinyPalette().toBytes());
                assertFalse(Arrays.equals(originals.get(i), sp.getNormalPalette().toBytes()));
                assertNotSame(sp.getNormalPalette(), sp.getShinyPalette());
                for (int slot = 0; slot < 16; slot++) assertNotSame(sp.getNormalPalette().get(slot), sp.getShinyPalette().get(slot));
                assertArrayEquals(sp.getShinyPalette().toBytes(), new Palette(sp.getShinyPalette().toBytes()).toBytes());
            }
            assertEquals(Type.FIRE, a.getPrimaryType(false));
            assertEquals(Type.FLYING, b.getSecondaryType(false));
            assertEquals(Type.FAIRY, c.getPrimaryType(false));
            assertSame(b, a.getEvolutionsFrom().getFirst().getTo());
        }
    }

    @Test
    void selectedIncompletePaletteAndNullColorFail() {
        Species shortAsset = speciesWithPalette(4);
        shortAsset.setNormalPalette(new Palette(2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> selected(gfx004(false), new SpeciesSet(shortAsset)).randomizePokemonPalettes());
        // The shiny copy has already been published, despite the later population failure.
        assertEquals(2, shortAsset.getShinyPalette().size());
        Species nullColor = speciesWithPalette(4);
        nullColor.getNormalPalette().set(15, null);
        Palette originalShiny = nullColor.getShinyPalette();
        assertThrows(NullPointerException.class,
                () -> selected(gfx004(false), new SpeciesSet(nullColor)).randomizePokemonPalettes());
        assertSame(originalShiny, nullColor.getShinyPalette());
    }

    @Test
    void selectedUndefinedDescriptionCopiesShinyWithoutRecoloringNormal() {
        Species expanded = speciesWithPalette(388);
        expanded.setShinyPalette(new Palette(16, new Color(240, 16, 32)));
        byte[] normal = expanded.getNormalPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = selected(gfx004(false), new SpeciesSet(expanded));
        randomizer.randomizePokemonPalettes();
        assertArrayEquals(normal, expanded.getNormalPalette().toBytes());
        assertArrayEquals(normal, expanded.getShinyPalette().toBytes());
        assertTrue(randomizer.isChangesMade());
    }

    @Test
    void selectedBlankDescriptionCanReportChangesWithoutAnyChangedBytes() {
        Species sp = speciesWithPalette(4);
        byte[] normal = sp.getNormalPalette().toBytes(), shiny = sp.getShinyPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = withBody(new SpeciesSet(sp), "");
        randomizer.randomizePokemonPalettes();
        assertArrayEquals(normal, sp.getNormalPalette().toBytes());
        assertArrayEquals(shiny, sp.getShinyPalette().toBytes());
        assertTrue(randomizer.isChangesMade());
    }

    @Test
    void selectedBadShadeAverageAndSiblingBoundsThrowAfterShinyPublication() {
        for (String body : List.of("2,3,17", "A 2,17", "2,3;4,17-3")) {
            Species sp = speciesWithPalette(4);
            Palette shiny = sp.getShinyPalette();
            assertThrows(IndexOutOfBoundsException.class,
                    () -> withBody(new SpeciesSet(sp), body).randomizePokemonPalettes(), body);
            assertNotSame(shiny, sp.getShinyPalette(), body);
        }
    }

    @Test
    void selectedLatePopulationFailureLeavesPublishedShinyAndPartialNormal() {
        Species sp = speciesWithPalette(4);
        Palette shiny = sp.getShinyPalette();
        byte[] normal = sp.getNormalPalette().toBytes();
        assertThrows(IndexOutOfBoundsException.class,
                () -> withBody(new SpeciesSet(sp), "2,3,4/17").randomizePokemonPalettes());
        assertNotSame(shiny, sp.getShinyPalette());
        assertArrayEquals(normal, sp.getShinyPalette().toBytes());
        assertFalse(Arrays.equals(normal, sp.getNormalPalette().toBytes()));
    }

    @Test
    void selectedUnownAndUncertainFormAreNotFilteredBeforeMutation() throws Exception {
        for (int id : new int[]{201, 4}) {
            Species sp = speciesWithPalette(id);
            if (id == 4) sp.setFormeNumber(1);
            Palette shiny = sp.getShinyPalette();
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            remember(handler, sp);
            new Gen3to5PaletteRandomizer(handler, gfx004(false), new Random(1)).randomizePokemonPalettes();
            assertNotSame(shiny, sp.getShinyPalette());
            assertEquals(id != 201, canWrite(handler, sp, sp.getNormalPalette(), new byte[32]));
        }
    }

    @Test
    void selectedSharedJavaPaletteIsRecoloredInPlace() {
        Species a = speciesWithPalette(4), b = speciesWithPalette(7);
        b.setNormalPalette(a.getNormalPalette());
        Palette shared = a.getNormalPalette();
        byte[] original = shared.toBytes();
        selected(gfx004(false), new SpeciesSet(List.of(a, b))).randomizePokemonPalettes();
        assertSame(shared, a.getNormalPalette());
        assertSame(shared, b.getNormalPalette());
        assertFalse(Arrays.equals(original, shared.toBytes()));
    }

    @Test
    void selectedNullEvolutionFailsWhereFollowOffDoesNotInspectIt() {
        Species sp = speciesWithPalette(4);
        sp.getEvolutionsTo().add(null);
        assertThrows(NullPointerException.class,
                () -> selected(gfx004(true), new SpeciesSet(sp)).randomizePokemonPalettes());
        assertDoesNotThrow(() -> selected(gfx004(false), new SpeciesSet(sp)).randomizePokemonPalettes());
    }

    @Test
    void selectedMalformedAndDanglingEvolutionAreNotValidated() {
        Species parent = speciesWithPalette(4), child = speciesWithPalette(5), outsider = speciesWithPalette(7);
        Evolution malformed = new Evolution(parent, child, EvolutionType.LEVEL, 16);
        child.getEvolutionsTo().add(malformed); // parent reciprocal edge intentionally absent
        assertDoesNotThrow(() -> selected(gfx004(true), new SpeciesSet(List.of(parent, child))).randomizePokemonPalettes());
        Species dangling = speciesWithPalette(6);
        dangling.getEvolutionsTo().add(new Evolution(outsider, dangling, EvolutionType.LEVEL, 16));
        assertDoesNotThrow(() -> selected(gfx004(true), new SpeciesSet(dangling)).randomizePokemonPalettes());
    }

    @Test
    void originalShinyLoadProvenanceChangesWriteEligibilityWithoutChangingPublicPalettes() throws Exception {
        Species sp = speciesWithPalette(4);
        SelectedHandler loaded = new SelectedHandler(new SpeciesSet(sp));
        SelectedHandler unloaded = new SelectedHandler(new SpeciesSet(sp));
        remember(loaded, sp);
        assertTrue(loaded.usesCfruDpeRandomPoolPolicy());
        assertTrue(unloaded.usesCfruDpeRandomPoolPolicy());
        assertSame(loaded.getSpeciesSetInclFormes().iterator().next(), unloaded.getSpeciesSetInclFormes().iterator().next());
        var field = Gen3RomHandler.class.getDeclaredField("originalCfruDpeShinyPaletteBytes");
        field.setAccessible(true);
        var loadedBytes = (java.util.Map<?, ?>) field.get(loaded);
        var unloadedBytes = (java.util.Map<?, ?>) field.get(unloaded);
        assertTrue(canWrite(loaded, sp, sp.getShinyPalette(), (byte[]) loadedBytes.get(sp)));
        assertFalse(canWrite(unloaded, sp, sp.getShinyPalette(), (byte[]) unloadedBytes.get(sp)));
        // Reflection is test setup/evidence only; production cannot use it as an ownership interface.
    }

    @Test
    void distinctJavaPairsDoNotProveDistinctSaveTableOwners() throws Exception {
        Species a = speciesWithPalette(4), b = speciesWithPalette(7);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
        remember(handler, a); remember(handler, b);
        var mapping = Gen3RomHandler.class.getDeclaredField("pokedexToInternal");
        mapping.setAccessible(true);
        int[] indices = new int[8]; indices[4] = 4; indices[7] = 4;
        mapping.set(handler, indices);
        var owner = Gen3RomHandler.class.getDeclaredMethod("getCfruDpePaletteTableIndexForSave", Species.class);
        owner.setAccessible(true);
        assertEquals(owner.invoke(handler, a), owner.invoke(handler, b));
        assertNotSame(a.getNormalPalette(), b.getNormalPalette());
        assertNotSame(a.getShinyPalette(), b.getShinyPalette());
        assertTrue(canWrite(handler, a, a.getNormalPalette(), a.getNormalPalette().toBytes()));
        assertTrue(canWrite(handler, b, b.getNormalPalette(), b.getNormalPalette().toBytes()));
    }

    @Test
    void selectedCyclicEvolutionDoesNotCompleteSuccessfullyInBoundedChildProcess() throws Exception {
        // Contain the legacy helper's unbounded stack growth in a disposable, memory-limited JVM.
        java.util.Set<String> paths = new java.util.LinkedHashSet<>(Arrays.asList(
                System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) for (var url : urls.getURLs())
                paths.add(java.nio.file.Path.of(url.toURI()).toString());
        }
        Process process = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx32m", "-cp", String.join(java.io.File.pathSeparator, paths),
                getClass().getName(), "cycle").redirectErrorStream(true).start();
        try {
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains("CYCLE_ENTER"), output);
            if (finished) {
                assertNotEquals(0, process.exitValue(), output);
                assertTrue(output.contains("OutOfMemoryError") && output.contains("CopyUpEvolutionsHelper.apply"), output);
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].equals("cycle")) throw new IllegalArgumentException("Synthetic cycle probe only");
        Species a = speciesWithPalette(4), b = speciesWithPalette(5);
        link(a, b); link(b, a);
        System.out.println("CYCLE_ENTER");
        selected(gfx004(true), new SpeciesSet(List.of(a, b))).randomizePokemonPalettes();
    }

    @Test
    void selectedNoChangeSaveReturnsBeforeAnyRomOrAllocationAccess() throws Exception {
        Species sp = speciesWithPalette(4);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
        remember(handler, sp);
        // No ROM, configuration entry, free-space allocator or private data exists in this fixture.
        assertDoesNotThrow(handler::savePokemonPalettes);
        assertDoesNotThrow(handler::savePokemonPalettes);
    }

    private static Settings gfx004(boolean follow) {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        settings.setPokemonPalettesShinyFromNormal(true);
        settings.setPokemonPalettesFollowEvolutions(follow);
        return settings;
    }

    private static Gen3to5PaletteRandomizer selected(Settings settings, SpeciesSet species) {
        return new Gen3to5PaletteRandomizer(new SelectedHandler(species), settings, new Random(1));
    }

    private static Gen3to5PaletteRandomizer withBody(SpeciesSet species, String body) {
        return new Gen3to5PaletteRandomizer(new SelectedHandler(species), gfx004(false), new Random(1)) {
            @Override public List<PaletteDescription> getPaletteDescriptions(String key) {
                List<PaletteDescription> descriptions = descriptions(387);
                descriptions.set(3, new PaletteDescription("Charmander [" + body + "]"));
                return descriptions;
            }
        };
    }

    private static void link(Species from, Species to) {
        Evolution edge = new Evolution(from, to, EvolutionType.LEVEL, 16);
        from.getEvolutionsFrom().add(edge);
        to.getEvolutionsTo().add(edge);
    }

    private static void remember(Gen3RomHandler handler, Species sp) throws Exception {
        var method = Gen3RomHandler.class.getDeclaredMethod("rememberLoadedCfruDpePokemonPalette", Species.class, Palette.class, Palette.class);
        method.setAccessible(true);
        method.invoke(handler, sp, sp.getNormalPalette(), sp.getShinyPalette());
    }

    private static boolean canWrite(Gen3RomHandler handler, Species sp, Palette palette, byte[] original) throws Exception {
        var method = Gen3RomHandler.class.getDeclaredMethod("canWriteCfruDpePaletteCopy", Species.class, Palette.class, byte[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(handler, sp, palette, original);
    }

    private static final class SelectedHandler extends Gen3RomHandler {
        private final SpeciesSet species;
        SelectedHandler(SpeciesSet species) {
            this.species = species;
            try {
                var field = Gen3RomHandler.class.getDeclaredField("useCfruDpeGen9SpeciesCount");
                field.setAccessible(true);
                field.setBoolean(this, true);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }
        @Override public String getPaletteFilesID() { return "FRLG"; }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return species; }
        @Override public SpeciesSet getSpeciesSet() { return species; }
    }

    @Test
    public void Gen3to5PaletteRandomizer_usesAvailableDescriptionWhenSpeciesIsInRange() {
        Gen3to5PaletteRandomizer randomizer = randomizer();
        Species species = new Species(1);
        List<PaletteDescription> descriptions = List.of(new PaletteDescription("Bulbasaur [2,3,4]"));

        PalettePartDescription[] parts = randomizer.getPalettePartDescriptions(species, descriptions);

        assertFalse(parts[0].isBlank());
    }

    @Test
    public void Gen3to5PaletteRandomizer_defaultsMissingExpandedSpeciesDescriptionToBlank() {
        Gen3to5PaletteRandomizer randomizer = randomizer();
        Species expandedSpecies = new Species(388);
        List<PaletteDescription> descriptions = descriptions(387);

        PalettePartDescription[] parts = randomizer.getPalettePartDescriptions(expandedSpecies, descriptions);

        assertTrue(parts[0].isBlank());
    }

    @Test
    public void Gen3to5PaletteRandomizer_defaultsInvalidSpeciesNumberToBlank() {
        Gen3to5PaletteRandomizer randomizer = randomizer();
        Species invalidSpecies = new Species(0);
        List<PaletteDescription> descriptions = List.of(new PaletteDescription("Bulbasaur [2,3,4]"));

        PalettePartDescription[] parts = randomizer.getPalettePartDescriptions(invalidSpecies, descriptions);

        assertTrue(parts[0].isBlank());
    }

    @Test
    public void Gen3to5PaletteRandomizer_marksChangesMadeWhenRandomPalettesAreApplied() {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        Gen3to5PaletteRandomizer randomizer = randomizer(settings, new SpeciesSet(speciesWithPalette(1)));

        randomizer.randomizePokemonPalettes();

        assertTrue(randomizer.isChangesMade());
    }

    @Test
    public void Gen3to5PaletteRandomizer_changesInRangePaletteBytesWhenDescriptionsExist() {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        Species charmander = speciesWithPalette(4);
        byte[] originalPalette = charmander.getNormalPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = randomizer(settings, new SpeciesSet(charmander));

        randomizer.randomizePokemonPalettes();

        assertFalse(Arrays.equals(originalPalette, charmander.getNormalPalette().toBytes()));
    }

    @Test
    public void Gen3to5PaletteRandomizer_changesMadeImpliesChangedPaletteDigestWhenCandidatesExist() {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        Species squirtle = speciesWithPalette(7);
        byte[] originalPalette = squirtle.getNormalPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = randomizer(settings, new SpeciesSet(squirtle));

        randomizer.randomizePokemonPalettes();

        boolean paletteChanged = !Arrays.equals(originalPalette, squirtle.getNormalPalette().toBytes());
        assertTrue(!randomizer.isChangesMade() || paletteChanged);
    }

    @Test
    public void Settings_pokemonPalettesRandomRoundTripsThroughSettingsString() {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        settings.setPokemonPalettesFollowTypes(true);
        settings.setPokemonPalettesFollowEvolutions(true);
        settings.setPokemonPalettesShinyFromNormal(true);
        settings.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        settings.setRomName("TEST");

        Settings restored = Settings.fromString(settings.toString());

        assertEquals(Settings.PokemonPalettesMod.RANDOM, restored.getPokemonPalettesMod());
        assertTrue(restored.isPokemonPalettesFollowTypes());
        assertTrue(restored.isPokemonPalettesFollowEvolutions());
        assertTrue(restored.isPokemonPalettesShinyFromNormal());
    }

    private static Gen3to5PaletteRandomizer randomizer() {
        return randomizer(new Settings(), new SpeciesSet());
    }

    private static Gen3to5PaletteRandomizer randomizer(Settings settings, SpeciesSet speciesSet) {
        RomHandler romHandler = (RomHandler) Proxy.newProxyInstance(
                RomHandler.class.getClassLoader(),
                new Class[]{RomHandler.class},
                (proxy, method, args) -> {
                    if ("getPaletteFilesID".equals(method.getName())) {
                        return "FRLG";
                    }
                    if ("getSpeciesSetInclFormes".equals(method.getName())) {
                        return speciesSet;
                    }
                    if ("getRestrictedSpeciesService".equals(method.getName()) ||
                            "getTypeService".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new Gen3to5PaletteRandomizer(romHandler, settings, new Random(1));
    }

    private static List<PaletteDescription> descriptions(int count) {
        List<PaletteDescription> descriptions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            descriptions.add(new PaletteDescription("Species" + (i + 1) + " [2,3,4]"));
        }
        return descriptions;
    }

    private static Species speciesWithPalette(int number) {
        Species species = new Species(number);
        species.setPrimaryType(Type.NORMAL);
        species.setNormalPalette(testPalette());
        species.setShinyPalette(testPalette());
        return species;
    }

    private static Palette testPalette() {
        Palette palette = new Palette();
        for (int i = 0; i < palette.size(); i++) {
            palette.set(i, new Color(i * 8, i * 8, i * 8));
        }
        return palette;
    }
}
