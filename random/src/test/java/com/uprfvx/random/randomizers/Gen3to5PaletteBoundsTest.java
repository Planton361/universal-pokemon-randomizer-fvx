package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.graphics.palettes.Color;
import com.uprfvx.romio.graphics.palettes.TypeBaseColorList;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.TypeService;
import java.lang.reflect.Field;
import com.uprfvx.romio.graphics.palettes.Palette;
import com.uprfvx.romio.graphics.palettes.PaletteDescription;
import com.uprfvx.romio.graphics.palettes.PalettePartDescription;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;


public class Gen3to5PaletteBoundsTest {

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

    @Test
    void selectedNullPrimaryIsPreservedWhileUnchangedGfx001StillRecolors() throws Exception {
        Species untyped = speciesWithPalette(4);
        untyped.setPrimaryType(Type.fromInt(255));
        byte[] before = untyped.getNormalPalette().toBytes();
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new TypeBaseColorList(untyped, true, new Random(724)));
        assertEquals("Could not get an unused TypeColor of type null in less than 100 tries.", error.getMessage());
        var typed = selected(new SpeciesSet(untyped), true, false, 724);
        assertDoesNotThrow(typed::randomizePokemonPalettes);
        assertFalse(typed.isChangesMade());
        assertArrayEquals(before, untyped.getNormalPalette().toBytes());
        selected(new SpeciesSet(untyped), false, false, 724).randomizePokemonPalettes();
        assertFalse(Arrays.equals(before, untyped.getNormalPalette().toBytes()));
    }

    @Test
    void duplicateTypesExhaustColorListButSelectedPreflightPreservesAsset() throws Exception {
        Species duplicate = new Species(4);
        // A noncanonical setter order can retain FIRE/FIRE. The normal loader
        // uses primary-then-secondary and normalizes this to a single type.
        duplicate.setSecondaryType(Type.FIRE); duplicate.setPrimaryType(Type.FIRE);
        duplicate.setNormalPalette(testPalette());
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new TypeBaseColorList(duplicate, true, new Random(724)));
        assertTrue(error.getMessage().contains("TypeColor of type FIRE"));
        Palette original = duplicate.getNormalPalette();
        var randomizer = selected(new SpeciesSet(duplicate), true, false, 724);
        assertDoesNotThrow(randomizer::randomizePokemonPalettes);
        assertFalse(randomizer.isChangesMade());
        assertSame(original, duplicate.getNormalPalette());
    }

    @Test
    void selectedNullTypeWithNoDescriptionOrAssetIsPreserved() throws Exception {
        Species expanded = new Species(388);
        var randomizer = selected(new SpeciesSet(expanded), true, false, 724);
        assertDoesNotThrow(randomizer::randomizePokemonPalettes);
        assertFalse(randomizer.isChangesMade());
        assertNull(expanded.getNormalPalette());
    }

    @Test
    void selectedMissingNormalPaletteIsPreservedAndGfx001BoundaryIsUnchanged() throws Exception {
        for (boolean types : new boolean[]{false, true}) {
            Species missing = speciesWithPalette(4);
            missing.setNormalPalette(null);
            var randomizer = selected(new SpeciesSet(missing), types, false, 724);
            if (types) {
                assertDoesNotThrow(randomizer::randomizePokemonPalettes);
                assertFalse(randomizer.isChangesMade());
            } else {
                assertThrows(NullPointerException.class, randomizer::randomizePokemonPalettes);
            }
            assertNull(missing.getNormalPalette());
        }
    }

    @Test
    void selectedBlankDescriptionDoesNotRecolorOrReportChanges() throws Exception {
        Species expanded = speciesWithPalette(388);
        byte[] before = expanded.getNormalPalette().toBytes();
        var randomizer = selected(new SpeciesSet(expanded), true, false, 724);
        randomizer.randomizePokemonPalettes();
        assertArrayEquals(before, expanded.getNormalPalette().toBytes());
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void sourceClassificationEveryDeclaredTypeIncludingFairyHasEnoughColors() throws Exception {
        for (Type primary : Type.values()) for (Type secondary : Type.values()) {
            Species species = speciesWithPalette(1);
            species.setPrimaryType(primary);
            species.setSecondaryType(secondary);
            assertDoesNotThrow(() -> selected(new SpeciesSet(species), true, false, 724)
                    .randomizePokemonPalettes(), primary + "/" + secondary);
        }
        Species missingSecondary = speciesWithPalette(4);
        assertDoesNotThrow(() -> new TypeBaseColorList(missingSecondary, true, new Random(724)));
    }

    @Test
    void selectedInvalidLaterPartPreservesWholePalette() throws Exception {
        Species species = speciesWithPalette(4);
        byte[] before = species.getNormalPalette().toBytes();
        var randomizer = withDescription(species, "[2,3,4/17]", 724);
        assertDoesNotThrow(randomizer::randomizePokemonPalettes);
        assertFalse(randomizer.isChangesMade());
        assertArrayEquals(before, species.getNormalPalette().toBytes());
    }

    @Test
    void selectedIncompleteSiblingAndAverageArePreserved() throws Exception {
        for (String body : List.of("[2,3;4,5-6]", "[A2]", "[2,3;4,5-0]", "[A0,2,3]")) {
            Species species = speciesWithPalette(4);
            byte[] before = species.getNormalPalette().toBytes();
            var randomizer = withDescription(species, body, 724);
            assertDoesNotThrow(randomizer::randomizePokemonPalettes, body);
            assertFalse(randomizer.isChangesMade());
            assertArrayEquals(before, species.getNormalPalette().toBytes());
        }
    }

    @Test
    void selectedSingleDualFairyActuallyChangeNormalAndPreserveShiny() throws Exception {
        Species fire = speciesWithPalette(4), water = speciesWithPalette(7), fairy = speciesWithPalette(1);
        fire.setPrimaryType(Type.FIRE);
        water.setPrimaryType(Type.WATER);
        fairy.setPrimaryType(Type.FAIRY);
        fairy.setSecondaryType(Type.GRASS);
        var species = new SpeciesSet(List.of(fire, water, fairy));
        var originals = new java.util.HashMap<Species, byte[]>();
        var shinies = new java.util.HashMap<Species, byte[]>();
        species.forEach(sp -> { originals.put(sp, sp.getNormalPalette().toBytes());
                                shinies.put(sp, sp.getShinyPalette().toBytes()); });
        var randomizer = selected(species, true, false, 724);
        randomizer.randomizePokemonPalettes();
        assertTrue(randomizer.isChangesMade());
        assertEquals(3, species.stream().filter(sp ->
                !Arrays.equals(originals.get(sp), sp.getNormalPalette().toBytes())).count());
        for (Species sp : species) {
            assertEquals(16, sp.getNormalPalette().size());
            assertArrayEquals(shinies.get(sp), sp.getShinyPalette().toBytes());
        }
        Species typedFire = speciesWithPalette(1), typedWater = speciesWithPalette(1);
        typedFire.setPrimaryType(Type.FIRE); typedWater.setPrimaryType(Type.WATER);
        selected(new SpeciesSet(typedFire), true, false, 724).randomizePokemonPalettes();
        selected(new SpeciesSet(typedWater), true, false, 724).randomizePokemonPalettes();
        assertFalse(Arrays.equals(typedFire.getNormalPalette().toBytes(), typedWater.getNormalPalette().toBytes()));
        // Identical shape/seed, changing only the secondary type also changes output.
        Species dual = speciesWithPalette(1);
        dual.setPrimaryType(Type.FIRE); dual.setSecondaryType(Type.WATER);
        selected(new SpeciesSet(dual), true, false, 724).randomizePokemonPalettes();
        assertFalse(Arrays.equals(typedFire.getNormalPalette().toBytes(), dual.getNormalPalette().toBytes()));
    }

    @Test
    void selectedPartsUseActualPrimaryAndSecondaryBaseColors() throws Exception {
        for (Type secondary : new Type[]{null, Type.WATER, Type.FAIRY}) {
            Species sp = speciesWithPalette(4);
            sp.setPrimaryType(Type.FIRE); sp.setSecondaryType(secondary);
            var expected = new TypeBaseColorList(sp, true, new Random(724));
            byte[] primary = expected.getBaseColor(0).toBytes();
            byte[] second = expected.getBaseColor(1).toBytes();
            withDescription(sp, "[2-B/3-B]", 724).randomizePokemonPalettes();
            assertArrayEquals(primary, sp.getNormalPalette().get(1).toBytes());
            assertArrayEquals(second, sp.getNormalPalette().get(2).toBytes());
        }
    }

    @Test
    void selectedNoAssetsCannotTriggerPaletteCopySave() throws Exception {
        Species missing = new Species(4);
        var handler = new SyntheticGen3(new SpeciesSet(missing), true, "FRLG");
        var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(true, false), new Random(724));
        randomizer.randomizePokemonPalettes();
        assertNull(missing.getNormalPalette()); assertNull(missing.getShinyPalette());
        // Real writer's unchanged short circuit must succeed without any ROM buffer,
        // pointer table, free-space allocator or fabricated original loaded bytes.
        assertDoesNotThrow(handler::savePokemonPalettes);
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void selectedMissingShinyDoesNotBlockValidNormal() throws Exception {
        Species sp = speciesWithPalette(4);
        sp.setShinyPalette(null);
        var randomizer = selected(new SpeciesSet(sp), true, false, 724);
        assertDoesNotThrow(randomizer::randomizePokemonPalettes);
        assertTrue(randomizer.isChangesMade());
        assertNull(sp.getShinyPalette());
    }

    @Test
    void selectedShortNullColorInvalidIdentityAndUncertainFormsArePreserved() throws Exception {
        for (int fixture = 0; fixture < 8; fixture++) {
            Species sp = speciesWithPalette(4);
            switch (fixture) {
                case 0 -> sp.setNormalPalette(new Palette(15));
                case 1 -> sp.getNormalPalette().set(2, null);
                case 2 -> sp.setBaseForme(speciesWithPalette(4));
                case 3 -> sp.setFormeNumber(1);
                case 4 -> sp.setSpeciesSetIdentityNumber(0x365); // Mega row, even without form flags
                case 5 -> sp.setSpeciesSetIdentityNumber(0xFC); // unused row
                case 6 -> sp = speciesWithPalette(201); // writer explicitly preserves Unown
                case 7 -> sp = speciesWithPalette(0);
            }
            Palette original = sp.getNormalPalette();
            var randomizer = selected(new SpeciesSet(sp), true, false, 724);
            assertDoesNotThrow(randomizer::randomizePokemonPalettes);
            assertFalse(randomizer.isChangesMade());
            assertSame(original, sp.getNormalPalette());
        }
    }

    @Test
    void selectedDexAliasesSharedPaletteAndUnownedObjectsArePreserved() throws Exception {
        for (boolean sharedPalette : new boolean[]{false, true}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(sharedPalette ? 7 : 4);
            b.setSpeciesSetIdentityNumber(1001);
            if (sharedPalette) b.setNormalPalette(a.getNormalPalette());
            Palette originalA = a.getNormalPalette(), originalB = b.getNormalPalette();
            var randomizer = selected(new SpeciesSet(List.of(a,b)), true, false, 724);
            randomizer.randomizePokemonPalettes();
            assertFalse(randomizer.isChangesMade());
            assertSame(originalA, a.getNormalPalette()); assertSame(originalB, b.getNormalPalette());
        }
        Species unowned = speciesWithPalette(4);
        var handler = new SyntheticGen3(new SpeciesSet(unowned), true, "FRLG") {
            @Override public SpeciesSet getSpeciesSet() { return new SpeciesSet(); }
        };
        var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(true, false), new Random(724));
        randomizer.randomizePokemonPalettes();
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void selectedBlankAverageOnlyIgnoredAndTooManyPartsDoNotPretendToRecolor() throws Exception {
        for (String body : List.of("[]", "[note]", "[A2,3,4]", "[0,0,0]",
                "[2/3/4/5/6/7/8/9/10]", "[2,3/17]")) {
            Species sp = speciesWithPalette(4);
            byte[] before = sp.getNormalPalette().toBytes();
            var randomizer = withDescription(sp, body, 724);
            randomizer.randomizePokemonPalettes();
            assertFalse(randomizer.isChangesMade(), body);
            assertArrayEquals(before, sp.getNormalPalette().toBytes(), body);
        }
    }

    @Test
    void selectedSkippedParentMissingPrevoSplitAndTypeChangesGetValidTypedPlans() throws Exception {
        for (int fixture = 0; fixture < 4; fixture++) {
            Species parent = speciesWithPalette(1), child = speciesWithPalette(2), split = speciesWithPalette(3);
            parent.setPrimaryType(Type.GRASS); child.setPrimaryType(Type.FAIRY); split.setPrimaryType(Type.WATER);
            link(parent, child); link(parent, split);
            var species = new SpeciesSet(List.of(parent, child, split));
            if (fixture == 0) parent.setNormalPalette(null);
            if (fixture == 1) parent.setPrimaryType(null);
            if (fixture == 2) species.remove(parent);
            byte[] childBefore = child.getNormalPalette().toBytes(), splitBefore = split.getNormalPalette().toBytes();
            Palette parentBefore = parent.getNormalPalette();
            selected(species, true, true, 724).randomizePokemonPalettes();
            assertFalse(Arrays.equals(childBefore, child.getNormalPalette().toBytes()));
            assertFalse(Arrays.equals(splitBefore, split.getNormalPalette().toBytes()));
            if (fixture < 3) assertSame(parentBefore, parent.getNormalPalette());
        }
    }

    @Test
    void selectedCyclicOrMalformedGraphFailsBeforeAnyPaletteChange() throws Exception {
        for (int malformed : new int[]{0, 1, 2, 3}) {
            Species a = speciesWithPalette(1), b = speciesWithPalette(2);
            link(a, b);
            if (malformed == 0) link(b, a);
            if (malformed == 1) b.getEvolutionsTo().getFirst().setTo(a);
            if (malformed == 2) a.getEvolutionsFrom().add(null);
            if (malformed == 3) {
                Species unavailable = speciesWithPalette(7);
                Evolution external = new Evolution(unavailable, b, EvolutionType.LEVEL, 16);
                b.getEvolutionsTo().addFirst(external);
            }
            byte[] before = a.getNormalPalette().toBytes();
            var randomizer = selected(new SpeciesSet(List.of(a,b)), true, true, 724);
            assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                    randomizer::randomizePokemonPalettes);
            assertFalse(randomizer.isChangesMade());
            assertArrayEquals(before, a.getNormalPalette().toBytes());
        }
    }

    @Test
    void selectedLateScratchFailureCannotMutateEarlierPaletteOrShiny() throws Exception {
        Species a = speciesWithPalette(1), b = speciesWithPalette(4);
        byte[] normal = a.getNormalPalette().toBytes(), shiny = a.getShinyPalette().toBytes();
        var randomizer = new Gen3to5PaletteRandomizer(new SyntheticGen3(new SpeciesSet(List.of(a,b)), true, "FRLG"),
                paletteSettings(true, false), new Random(724)) {
            int populated;
            @Override public void populatePalette(Palette palette,
                    com.uprfvx.romio.graphics.palettes.PalettePopulator pp, TypeBaseColorList colors,
                    PalettePartDescription[] parts) {
                super.populatePalette(palette, pp, colors, parts);
                if (++populated == 2) throw new IllegalStateException("synthetic late failure");
            }
        };
        assertThrows(IllegalStateException.class, randomizer::randomizePokemonPalettes);
        assertFalse(randomizer.isChangesMade());
        assertArrayEquals(normal, a.getNormalPalette().toBytes());
        assertArrayEquals(shiny, a.getShinyPalette().toBytes());
    }

    @Test
    void vanillaFrlgAndRseTypesAndSelectedGfx001Gfx003RetainExactBehavior() throws Exception {
        for (String paletteId : List.of("FRLG", "E")) for (boolean types : new boolean[]{false, true})
                for (boolean evolutions : new boolean[]{false, true}) {
            SpeciesSet vanilla = chain(), selected = chain();
            var original = new Gen3to5PaletteRandomizer(new SyntheticGen3(vanilla, false, paletteId),
                    paletteSettings(types, evolutions), new Random(724));
            original.randomizePokemonPalettes();
            var control = new Gen3to5PaletteRandomizer(new SyntheticGen3(selected, true, paletteId),
                    paletteSettings(types, evolutions), new Random(724));
            control.randomizePokemonPalettes();
            for (Species sp : selected) assertArrayEquals(vanilla.stream()
                    .filter(v -> v.getNumber() == sp.getNumber()).findFirst().orElseThrow()
                    .getNormalPalette().toBytes(), sp.getNormalPalette().toBytes(),
                    paletteId + ": types=" + types + ", evolutions=" + evolutions);
            assertTrue(original.isChangesMade());
        }
    }

    static SpeciesSet chain() {
        Species a = speciesWithPalette(1), b = speciesWithPalette(2), c = speciesWithPalette(3);
        a.setPrimaryType(Type.GRASS); b.setPrimaryType(Type.GRASS); c.setPrimaryType(Type.GRASS);
        link(a,b); link(b,c);
        return new SpeciesSet(List.of(a,b,c));
    }

    static void link(Species from, Species to) {
        Evolution evolution = new Evolution(from, to, EvolutionType.LEVEL, 16);
        from.getEvolutionsFrom().add(evolution); to.getEvolutionsTo().add(evolution);
    }

    static Gen3to5PaletteRandomizer withDescription(Species species, String body, long seed) throws Exception {
        return new Gen3to5PaletteRandomizer(new SyntheticGen3(new SpeciesSet(species), true, "FRLG"),
                paletteSettings(true, false), new Random(seed)) {
            @Override public List<PaletteDescription> getPaletteDescriptions(String key) {
                List<PaletteDescription> rows = descriptions(4);
                rows.set(3, new PaletteDescription(body));
                return rows;
            }
        };
    }

    static Gen3to5PaletteRandomizer selected(SpeciesSet species, boolean types, boolean evolutions,
                                            long seed) throws Exception {
        Settings settings = paletteSettings(types, evolutions);
        return new Gen3to5PaletteRandomizer(new SyntheticGen3(species, true, "FRLG"), settings, new Random(seed));
    }

    static Settings paletteSettings(boolean types, boolean evolutions) {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        settings.setPokemonPalettesFollowTypes(types);
        settings.setPokemonPalettesFollowEvolutions(evolutions);
        return settings;
    }

    static class SyntheticGen3 extends Gen3RomHandler {
        final SpeciesSet species;
        final String paletteId;
        SyntheticGen3(SpeciesSet species, boolean selected, String paletteId) throws Exception {
            this.species = species;
            this.paletteId = paletteId;
            Field policy = Gen3RomHandler.class.getDeclaredField("useCfruDpeGen9SpeciesCount");
            policy.setAccessible(true);
            policy.set(this, selected);
        }
        @Override public SpeciesSet getSpeciesSet() { return species; }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return species; }
        @Override public String getPaletteFilesID() { return paletteId; }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() { return null; }
        @Override public TypeService getTypeService() { return null; }
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

    static List<PaletteDescription> descriptions(int count) {
        List<PaletteDescription> descriptions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            descriptions.add(new PaletteDescription("Species" + (i + 1) + " [2,3,4]"));
        }
        return descriptions;
    }

    static Species speciesWithPalette(int number) {
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
