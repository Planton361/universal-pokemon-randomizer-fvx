package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Type;
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

    // Stage A remains in the parent commit. Stage B fixtures now run the real defensive loader
    // against generated compressed buffers, not manually asserted Java palette provenance.
    @Test
    void selectedMissingChannelsArePreservedInBothFollowModes() {
        for (boolean follow : new boolean[]{false, true}) for (int missing : new int[]{1, 2, 3}) {
            Species sp = speciesWithPalette(4);
            if ((missing & 1) != 0) sp.setNormalPalette(null);
            if ((missing & 2) != 0) sp.setShinyPalette(null);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(follow), 1);
            assertDoesNotThrow(randomizer::randomizePokemonPalettes);
            assertSame(normal, sp.getNormalPalette());
            assertSame(shiny, sp.getShinyPalette());
            assertFalse(randomizer.isChangesMade());
            assertFalse(handler.getCfruDpePalettePairEligibility(sp).eligible());
        }
        Species sp = speciesWithPalette(4); sp.setNormalPalette(null);
        // The independent GFX-001 path is unchanged, including its existing missing-asset failure.
        assertThrows(NullPointerException.class, () -> selected(new SelectedHandler(new SpeciesSet(sp)),
                new Settings(), 1).randomizePokemonPalettes());
    }

    @Test
    void javaOnlyPalettesCannotSubstituteForEitherOriginalSnapshot() throws Exception {
        for (String removed : List.of("both", "originalCfruDpeNormalPaletteBytes", "originalCfruDpeShinyPaletteBytes")) {
            Species sp = speciesWithPalette(4);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp), !removed.equals("both"));
            if (!removed.equals("both")) snapshots(handler, removed).clear();
            byte[] normal = sp.getNormalPalette().toBytes(), shiny = sp.getShinyPalette().toBytes();
            var decision = handler.getCfruDpePalettePairEligibility(sp);
            assertFalse(decision.eligible());
            assertEquals("missing original pair snapshots", decision.reason());
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(false), 1);
            randomizer.randomizePokemonPalettes();
            assertArrayEquals(normal, sp.getNormalPalette().toBytes());
            assertArrayEquals(shiny, sp.getShinyPalette().toBytes());
            assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void selectedOriginalNormalBecomesDeepCopiedShinyForThreeTypesAndBothFollowModes() {
        for (boolean follow : new boolean[]{false, true}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(6);
            a.setPrimaryType(Type.FIRE); b.setPrimaryType(Type.WATER); b.setSecondaryType(Type.FLYING);
            c.setPrimaryType(Type.FAIRY);
            link(a, b); link(b, c);
            List<Species> species = List.of(a, b, c);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(species));
            List<byte[]> originals = species.stream().map(sp -> sp.getNormalPalette().toBytes()).toList();
            List<Palette> live = species.stream().map(Species::getNormalPalette).toList();
            for (Species sp : species) assertTrue(handler.getCfruDpePalettePairEligibility(sp).eligible());
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(follow), 1);
            randomizer.randomizePokemonPalettes();
            for (int i = 0; i < species.size(); i++) {
                Species sp = species.get(i);
                assertArrayEquals(originals.get(i), sp.getShinyPalette().toBytes());
                assertFalse(Arrays.equals(originals.get(i), sp.getNormalPalette().toBytes()));
                assertArrayEquals(originals.get(i), live.get(i).toBytes());
                assertNotSame(live.get(i), sp.getShinyPalette());
                assertNotSame(live.get(i).get(0), sp.getShinyPalette().get(0));
                assertEquals(i == 2 ? Type.FAIRY : i == 1 ? Type.WATER : Type.FIRE, sp.getPrimaryType(false));
            }
            assertTrue(randomizer.isChangesMade());
            assertEquals(1, a.getEvolutionsFrom().size());
        }
    }

    @Test
    void splitEvolutionCopiesColorsWithoutSharingPublishedBuffers() {
        Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(6);
        link(a, b); link(a, c);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b, c)));
        byte[] child = c.getNormalPalette().toBytes();
        selected(handler, gfx004(true), 677).randomizePokemonPalettes();
        assertArrayEquals(child, c.getShinyPalette().toBytes());
        assertNotSame(b.getNormalPalette(), c.getNormalPalette());
        assertNotSame(b.getShinyPalette(), c.getShinyPalette());
    }

    @Test
    void twoEvolutionMethodsFromSameParentKeepFollowSemantics() {
        Species a = speciesWithPalette(4), b = speciesWithPalette(5);
        link(a, b);
        Evolution second = new Evolution(a, b, EvolutionType.LEVEL, 30);
        a.getEvolutionsFrom().add(second); b.getEvolutionsTo().add(second);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
        byte[] original = b.getNormalPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(true), 1);
        assertDoesNotThrow(randomizer::randomizePokemonPalettes);
        assertArrayEquals(original, b.getShinyPalette().toBytes());
        assertFalse(Arrays.equals(original, b.getNormalPalette().toBytes()));
        assertTrue(randomizer.isChangesMade());
    }

    @Test
    void skippedParentPreservesWholeComponentButIndependentPairStillChanges() {
        Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(7);
        link(a, b); a.setShinyPalette(null);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b, c)));
        Palette childNormal = b.getNormalPalette(), childShiny = b.getShinyPalette();
        byte[] independent = c.getNormalPalette().toBytes();
        Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(true), 1);
        randomizer.randomizePokemonPalettes();
        assertSame(childNormal, b.getNormalPalette()); assertSame(childShiny, b.getShinyPalette());
        assertNull(a.getShinyPalette());
        assertFalse(Arrays.equals(independent, c.getNormalPalette().toBytes()));
        assertTrue(randomizer.isChangesMade());
    }

    @Test
    void absentExpandedAndBlankDescriptionsPreserveBothChannelsWithoutFalseChanges() {
        Species expanded = speciesWithPalette(388);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(expanded));
        Palette normal = expanded.getNormalPalette(), shiny = expanded.getShinyPalette();
        Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(false), 1);
        randomizer.randomizePokemonPalettes();
        assertSame(normal, expanded.getNormalPalette()); assertSame(shiny, expanded.getShinyPalette());
        assertFalse(randomizer.isChangesMade());
        Species sp = speciesWithPalette(4);
        Gen3to5PaletteRandomizer blank = withBody(new SpeciesSet(sp), "");
        normal = sp.getNormalPalette(); shiny = sp.getShinyPalette();
        blank.randomizePokemonPalettes();
        assertSame(normal, sp.getNormalPalette()); assertSame(shiny, sp.getShinyPalette());
        assertFalse(blank.isChangesMade());
    }

    @Test
    void malformedLaterShadeAverageAndSiblingDescriptionsNeverPublishEitherChannel() {
        for (String body : List.of("2,3,17", "A 2,17", "A 2", "A 0,3", "2,3;4,17-3",
                "2,3;4,5-3", "2,3,4/17", "notes", "99999999999999999999", "2/3/4/5/6/7/8/9/10")) {
            Species sp = speciesWithPalette(4);
            Gen3to5PaletteRandomizer randomizer = withBody(new SpeciesSet(sp), body);
            Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
            byte[] original = normal.toBytes();
            assertDoesNotThrow(randomizer::randomizePokemonPalettes, body);
            assertSame(normal, sp.getNormalPalette(), body); assertSame(shiny, sp.getShinyPalette(), body);
            assertArrayEquals(original, normal.toBytes());
            assertFalse(randomizer.isChangesMade(), body);
        }
    }

    @Test
    void lateScratchFailureAcrossPairsLeavesAllLiveBuffersAndSnapshotsUntouched() throws Exception {
        Species a = speciesWithPalette(4), b = speciesWithPalette(7);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
        byte[] originalRom = handler.buffer().clone();
        Palette an = a.getNormalPalette(), as = a.getShinyPalette(), bn = b.getNormalPalette(), bs = b.getShinyPalette();
        byte[] original = an.toBytes(), other = bn.toBytes();
        Gen3to5PaletteRandomizer randomizer = new Gen3to5PaletteRandomizer(handler, gfx004(false), new Random(1)) {
            int computed;
            @Override public void populatePalette(Palette palette, com.uprfvx.romio.graphics.palettes.PalettePopulator pp,
                    com.uprfvx.romio.graphics.palettes.TypeBaseColorList colors, PalettePartDescription[] parts) {
                super.populatePalette(palette, pp, colors, parts);
                if (++computed == 2) throw new IllegalStateException("synthetic late scratch failure");
            }
        };
        assertThrows(IllegalStateException.class, randomizer::randomizePokemonPalettes);
        assertSame(an, a.getNormalPalette()); assertSame(as, a.getShinyPalette());
        assertSame(bn, b.getNormalPalette()); assertSame(bs, b.getShinyPalette());
        assertArrayEquals(original, an.toBytes()); assertArrayEquals(other, bn.toBytes());
        assertArrayEquals(originalRom, handler.buffer());
        assertArrayEquals(original, snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(a));
        assertTrue(handler.getCfruDpePalettePairEligibility(a).eligible());
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void completeScratchWithNoChangedBytesDoesNotReportChanges() {
        Species sp = speciesWithPalette(4); sp.setShinyPalette(new Palette(sp.getNormalPalette()));
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
        Gen3to5PaletteRandomizer randomizer = new Gen3to5PaletteRandomizer(handler, gfx004(false), new Random(1)) {
            @Override public void populatePalette(Palette palette, com.uprfvx.romio.graphics.palettes.PalettePopulator pp,
                    com.uprfvx.romio.graphics.palettes.TypeBaseColorList colors, PalettePartDescription[] parts) { }
        };
        randomizer.randomizePokemonPalettes();
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void profileFormsUnownAndInvalidTypesAreExcluded() throws Exception {
        for (int kind = 0; kind < 7; kind++) {
            Species sp = speciesWithPalette(kind == 0 ? 201 : 4);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            switch (kind) {
                case 1 -> sp.setFormeNumber(1);
                case 2 -> sp.setBaseForme(speciesWithPalette(1));
                case 3 -> sp.setPrimaryType(null);
                case 4 -> handler.entry.setRomCode("AXVE");
                case 5 -> sp.setSpeciesSetIdentityNumber(0x365);
                case 6 -> sp.setActuallyCosmetic(true);
            }
            Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(false), 1);
            randomizer.randomizePokemonPalettes();
            assertSame(normal, sp.getNormalPalette()); assertSame(shiny, sp.getShinyPalette());
            assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void shortNullColorStaleAndMissingCurrentPairsAreRejected() {
        for (int kind = 0; kind < 5; kind++) {
            Species sp = speciesWithPalette(4);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            switch (kind) {
                case 0 -> sp.setNormalPalette(new Palette(15));
                case 1 -> sp.getShinyPalette().set(3, null);
                case 2 -> sp.getNormalPalette().set(3, new Color(248, 0, 0));
                case 3 -> sp.getShinyPalette().set(3, new Color(0, 0, 248));
                case 4 -> sp.setNormalPalette(null);
            }
            assertFalse(handler.getCfruDpePalettePairEligibility(sp).eligible());
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(false), 1);
            assertDoesNotThrow(randomizer::randomizePokemonPalettes);
            assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void sharedJavaPalettesAreRejectedWithinAndAcrossBothChannels() {
        for (int kind = 0; kind < 3; kind++) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(7);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            if (kind == 0) b.setNormalPalette(a.getNormalPalette());
            if (kind == 1) a.setShinyPalette(a.getNormalPalette());
            if (kind == 2) b.setShinyPalette(a.getNormalPalette());
            assertFalse(handler.getCfruDpePalettePairEligibility(a).eligible());
            byte[] before = a.getNormalPalette().toBytes();
            selected(handler, gfx004(false), 1).randomizePokemonPalettes();
            assertArrayEquals(before, a.getNormalPalette().toBytes());
        }
    }

    @Test
    void distinctJavaPairsWithDuplicateActualSaveOwnerAreRejected() throws Exception {
        Species a = speciesWithPalette(4), b = speciesWithPalette(7);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
        ((int[]) field(handler, "pokedexToInternal"))[7] = 4;
        assertEquals("duplicate save-table owner", handler.getCfruDpePalettePairEligibility(a).reason());
        assertFalse(handler.getCfruDpePalettePairEligibility(b).eligible());
        Palette an = a.getNormalPalette(), bn = b.getNormalPalette();
        Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(false), 1);
        randomizer.randomizePokemonPalettes();
        assertSame(an, a.getNormalPalette()); assertSame(bn, b.getNormalPalette());
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    void sourceIdentityDexAndOwnerMustAgree() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            Species sp = speciesWithPalette(4);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            switch (kind) {
                case 0 -> ((int[]) field(handler, "pokedexToInternal"))[4] = 0;
                case 1 -> ((Species[]) field(handler, "pokesInternal"))[4] = speciesWithPalette(4);
                case 2 -> ((int[]) field(handler, "internalToPokedex"))[4] = 7;
                case 3 -> sp.setSpeciesSetIdentityNumber(7);
            }
            assertFalse(handler.getCfruDpePalettePairEligibility(sp).eligible());
        }
    }

    @Test
    void validNonIdentityDexMappingUsesActualSaveSlotAndQueryIsReadOnly() throws Exception {
        Species sp = speciesWithPalette(252);
        sp.setSpeciesSetIdentityNumber(277); // Gen3 internal Treecko row, distinct from National Dex.
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
        int[] dexToInternal = new int[278], internalToDex = new int[278];
        Species[] owners = new Species[278];
        dexToInternal[252] = 277; internalToDex[277] = 252; owners[277] = sp;
        handler.set("pokedexToInternal", dexToInternal); handler.set("internalToPokedex", internalToDex);
        handler.set("pokesInternal", owners); handler.entry.putIntValue("PokemonCount", 277);
        handler.pointer(handler.normalTable + 277 * 8, handler.normalSource(sp));
        handler.pointer(handler.shinyTable + 277 * 8, handler.normalSource(sp) + 64);
        handler.loadPokemonPalettes();
        byte[] before = handler.buffer().clone(), original = sp.getNormalPalette().toBytes();
        byte[] snapshot = snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(sp).clone();
        Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
        for (int i = 0; i < 3; i++) assertTrue(handler.getCfruDpePalettePairEligibility(sp).eligible());
        assertArrayEquals(before, handler.buffer());
        assertArrayEquals(snapshot, snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(sp));
        assertSame(normal, sp.getNormalPalette()); assertSame(shiny, sp.getShinyPalette());
        selected(handler, gfx004(false), 1).randomizePokemonPalettes();
        handler.savePokemonPalettes();
        assertArrayEquals(sp.getNormalPalette().toBytes(), new Palette(compressors.DSDecmp.Decompress(
                handler.buffer(), handler.pointerAt(handler.normalTable + 277 * 8))).toBytes());
        assertArrayEquals(original, new Palette(compressors.DSDecmp.Decompress(
                handler.buffer(), handler.pointerAt(handler.shinyTable + 277 * 8))).toBytes());
    }

    @Test
    void pointerBoundsCorruptionAndOverlappingTablesFailClosedWithoutWrites() {
        for (int kind = 0; kind < 9; kind++) {
            Species sp = speciesWithPalette(4);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
            switch (kind) {
                case 0 -> handler.entry.putIntValue("PokemonNormalPalettes", Integer.MAX_VALUE - 7);
                case 1 -> handler.entry.putIntValue("PokemonShinyPalettes", handler.normalTable);
                case 2 -> handler.pointer(handler.normalTable + 32, handler.buffer().length);
                case 3 -> handler.pointer(handler.shinyTable + 32, handler.normalTable);
                case 4 -> handler.buffer()[handler.normalSource(sp)] = 0;
                case 5 -> handler.buffer()[handler.normalSource(sp) + 1] = (byte) 255;
                case 6 -> handler.buffer()[handler.normalSource(sp) + 8] ^= 127;
                case 7 -> handler.entry.putIntValue("PokemonCount", Integer.MAX_VALUE);
                case 8 -> handler.pointer(handler.shinyTable + 32, handler.buffer().length - 4);
            }
            byte[] before = handler.buffer().clone();
            assertFalse(handler.getCfruDpePalettePairEligibility(sp).eligible(), "kind=" + kind);
            assertArrayEquals(before, handler.buffer());
        }
    }

    @Test
    void sharedOriginalPayloadIsSafeWithUniqueEntriesAndCopyWriterReadback() {
        Species a = speciesWithPalette(4), b = speciesWithPalette(7), untouched = speciesWithPalette(388);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b, untouched)));
        handler.pointer(handler.normalTable + b.getNumber() * 8, handler.normalSource(a));
        handler.loadPokemonPalettes(); // real load snapshots for the deliberately shared source
        byte[] originalRom = handler.buffer().clone(), originalNormal = a.getNormalPalette().toBytes();
        Palette excludedNormal = untouched.getNormalPalette(), excludedShiny = untouched.getShinyPalette();
        assertTrue(handler.getCfruDpePalettePairEligibility(a).eligible());
        assertTrue(handler.getCfruDpePalettePairEligibility(b).eligible());
        selected(handler, gfx004(false), 677).randomizePokemonPalettes();
        byte[] aNormal = a.getNormalPalette().toBytes(), bNormal = b.getNormalPalette().toBytes();
        handler.savePokemonPalettes();
        assertArrayEquals(aNormal, handler.readback(a, false));
        assertArrayEquals(bNormal, handler.readback(b, false));
        assertArrayEquals(originalNormal, handler.readback(a, true));
        assertArrayEquals(originalNormal, handler.readback(b, true));
        assertNotEquals(handler.readEntry(a, false), handler.readEntry(b, false));
        assertSame(excludedNormal, untouched.getNormalPalette()); assertSame(excludedShiny, untouched.getShinyPalette());
        for (int i = 0; i < 0x20000; i++) {
            boolean entry = i >= handler.normalTable + 4 * 8 && i < handler.normalTable + 4 * 8 + 4
                    || i >= handler.normalTable + 7 * 8 && i < handler.normalTable + 7 * 8 + 4
                    || i >= handler.shinyTable + 4 * 8 && i < handler.shinyTable + 4 * 8 + 4
                    || i >= handler.shinyTable + 7 * 8 && i < handler.shinyTable + 7 * 8 + 4;
            if (!entry) assertEquals(originalRom[i], handler.buffer()[i], "synthetic write scope");
        }
        handler.savePokemonPalettes(); // existing writer may allocate again; decoded bytes must replay
        handler.loadPokemonPalettes();
        assertArrayEquals(aNormal, a.getNormalPalette().toBytes());
        assertArrayEquals(bNormal, b.getNormalPalette().toBytes());
        assertArrayEquals(originalNormal, a.getShinyPalette().toBytes());
    }

    @Test
    void malformedDanglingNullConvergentAndCyclicGraphsStopBeforeMutation() {
        for (int kind = 0; kind < 5; kind++) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(6);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b, c)));
            switch (kind) {
                case 0 -> a.getEvolutionsTo().add(null);
                case 1 -> b.getEvolutionsTo().add(new Evolution(a, b, EvolutionType.LEVEL, 16));
                case 2 -> link(speciesWithPalette(7), a);
                case 3 -> { link(a, c); link(b, c); }
                case 4 -> { link(a, b); link(b, a); }
            }
            Palette normal = a.getNormalPalette(), shiny = a.getShinyPalette();
            byte[] original = normal.toBytes();
            Gen3to5PaletteRandomizer randomizer = selected(handler, gfx004(true), 1);
            assertThrows(com.uprfvx.random.exceptions.RandomizationException.class, randomizer::randomizePokemonPalettes);
            assertSame(normal, a.getNormalPalette()); assertSame(shiny, a.getShinyPalette());
            assertArrayEquals(original, normal.toBytes());
            assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void selectedCombinedTypesUseLoadedNormalForShinyAndTypeColorNormal() {
        for (boolean followEvolutions : new boolean[]{false, true}) {
            Species fire = speciesWithPalette(4), water = speciesWithPalette(7), fairy = speciesWithPalette(1);
            fire.setPrimaryType(Type.FIRE);
            water.setPrimaryType(Type.WATER); water.setSecondaryType(Type.FLYING);
            fairy.setPrimaryType(Type.FAIRY); fairy.setSecondaryType(Type.GRASS);
            List<Species> selected = List.of(fire, water, fairy);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(selected));
            List<byte[]> originalNormal = selected.stream().map(sp -> sp.getNormalPalette().toBytes()).toList();
            List<byte[]> originalShiny = selected.stream().map(sp -> sp.getShinyPalette().toBytes()).toList();
            List<Palette> loadedNormalObjects = selected.stream().map(Species::getNormalPalette).toList();
            for (Species sp : selected) assertTrue(handler.getCfruDpePalettePairEligibility(sp).eligible());

            Settings combined = gfx004(followEvolutions);
            combined.setPokemonPalettesFollowTypes(true);
            Gen3to5PaletteRandomizer randomizer = selected(handler, combined, 724);
            randomizer.randomizePokemonPalettes();

            assertTrue(randomizer.isChangesMade());
            for (int i = 0; i < selected.size(); i++) {
                Species sp = selected.get(i);
                assertFalse(Arrays.equals(originalNormal.get(i), sp.getNormalPalette().toBytes()));
                assertArrayEquals(originalNormal.get(i), sp.getShinyPalette().toBytes());
                assertNotSame(loadedNormalObjects.get(i), sp.getShinyPalette());
                assertNotSame(loadedNormalObjects.get(i).get(0), sp.getShinyPalette().get(0));
                assertFalse(Arrays.equals(originalShiny.get(i), sp.getShinyPalette().toBytes()));
            }

            handler.savePokemonPalettes();
            for (int i = 0; i < selected.size(); i++) {
                Species sp = selected.get(i);
                assertArrayEquals(sp.getNormalPalette().toBytes(), handler.readback(sp, false));
                assertArrayEquals(originalNormal.get(i), handler.readback(sp, true));
            }
        }

        Species valid = speciesWithPalette(4), missingShiny = speciesWithPalette(7), opaque = speciesWithPalette(388);
        missingShiny.setShinyPalette(null);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(valid, missingShiny, opaque)));
        byte[] validNormalBefore = valid.getNormalPalette().toBytes();
        Palette missingNormalBefore = missingShiny.getNormalPalette();
        Palette opaqueNormalBefore = opaque.getNormalPalette(), opaqueShinyBefore = opaque.getShinyPalette();
        byte[] missingNormalBytes = missingNormalBefore.toBytes();
        byte[] opaqueNormalBytes = opaqueNormalBefore.toBytes(), opaqueShinyBytes = opaqueShinyBefore.toBytes();
        Settings combined = gfx004(false); combined.setPokemonPalettesFollowTypes(true);
        selected(handler, combined, 724).randomizePokemonPalettes();
        handler.savePokemonPalettes();
        assertFalse(Arrays.equals(validNormalBefore, valid.getNormalPalette().toBytes()));
        assertArrayEquals(valid.getNormalPalette().toBytes(), handler.readback(valid, false));
        assertSame(missingNormalBefore, missingShiny.getNormalPalette());
        assertNull(missingShiny.getShinyPalette());
        assertArrayEquals(missingNormalBytes, missingShiny.getNormalPalette().toBytes());
        assertSame(opaqueNormalBefore, opaque.getNormalPalette()); assertSame(opaqueShinyBefore, opaque.getShinyPalette());
        assertArrayEquals(opaqueNormalBytes, opaque.getNormalPalette().toBytes());
        assertArrayEquals(opaqueShinyBytes, opaque.getShinyPalette().toBytes());

        Species absent = new Species(4);
        SelectedHandler noAssets = new SelectedHandler(new SpeciesSet(absent));
        Palette absentNormal = absent.getNormalPalette(), absentShiny = absent.getShinyPalette();
        Settings noPair = gfx004(false); noPair.setPokemonPalettesFollowTypes(true);
        assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                () -> selected(noAssets, noPair, 724).randomizePokemonPalettes());
        assertSame(absentNormal, absent.getNormalPalette()); assertSame(absentShiny, absent.getShinyPalette());
        assertFalse(noAssets.getCfruDpePalettePairEligibility(absent).eligible());

        Species sp = speciesWithPalette(4);
        SelectedHandler disabledHandler = new SelectedHandler(new SpeciesSet(sp));
        Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
        Settings disabled = gfx004(false); disabled.setPokemonPalettesMod(Settings.PokemonPalettesMod.UNCHANGED);
        Gen3to5PaletteRandomizer randomizer = selected(disabledHandler, disabled, 1);
        randomizer.randomizePokemonPalettes();
        assertFalse(randomizer.isChangesMade());
        assertSame(normal, sp.getNormalPalette()); assertSame(shiny, sp.getShinyPalette());
    }

    @Test
    void missingDescriptionResourceFailsBeforeAnyPairPublication() {
        Species sp = speciesWithPalette(4);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
        Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
        Gen3to5PaletteRandomizer randomizer = new Gen3to5PaletteRandomizer(handler, gfx004(false), new Random(1)) {
            @Override public List<PaletteDescription> getPaletteDescriptions(String key) {
                throw new IllegalStateException("synthetic missing resource");
            }
        };
        assertThrows(IllegalStateException.class, randomizer::randomizePokemonPalettes);
        assertSame(normal, sp.getNormalPalette()); assertSame(shiny, sp.getShinyPalette());
    }

    @Test
    void selectedGfx001AndGfx003MatchVanillaControlAtSameSeeds() {
        for (long seed : DeterministicPaletteReplayTest.SEEDS) for (boolean follow : new boolean[]{false, true}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            Species controlA = speciesWithPalette(4), controlB = speciesWithPalette(5);
            link(a, b); link(controlA, controlB);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            // Match the actual load-normalized bytes in the generic control.
            controlA.setNormalPalette(new Palette(a.getNormalPalette()));
            controlB.setNormalPalette(new Palette(b.getNormalPalette()));
            Settings settings = new Settings(); settings.setPokemonPalettesFollowEvolutions(follow);
            selected(handler, settings, seed).randomizePokemonPalettes();
            Gen3to5PaletteRandomizer control = new Gen3to5PaletteRandomizer(proxy(new SpeciesSet(List.of(controlA, controlB))), settings, new Random(seed));
            control.randomizePokemonPalettes();
            assertArrayEquals(controlA.getNormalPalette().toBytes(), a.getNormalPalette().toBytes());
            assertArrayEquals(controlB.getNormalPalette().toBytes(), b.getNormalPalette().toBytes());
        }
    }

    @Test
    void vanillaShinyFromNormalRetainsOriginalOrderWithoutSelectedProvenance() {
        Species sp = speciesWithPalette(4);
        byte[] original = sp.getNormalPalette().toBytes();
        randomizer(gfx004(false), new SpeciesSet(sp)).randomizePokemonPalettes();
        assertArrayEquals(original, sp.getShinyPalette().toBytes());
        assertFalse(Arrays.equals(original, sp.getNormalPalette().toBytes()));
    }

    @Test
    void selectedNoChangeSaveReturnsBeforeAnyAllocation() {
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(speciesWithPalette(4)));
        byte[] original = handler.buffer().clone();
        assertDoesNotThrow(handler::savePokemonPalettes);
        assertDoesNotThrow(handler::savePokemonPalettes);
        assertArrayEquals(original, handler.buffer());
    }

    static Settings gfx004(boolean follow) {
        Settings settings = new Settings();
        settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
        settings.setPokemonPalettesShinyFromNormal(true);
        settings.setPokemonPalettesFollowEvolutions(follow);
        return settings;
    }

    static Gen3to5PaletteRandomizer selected(SelectedHandler handler, Settings settings, long seed) {
        return new Gen3to5PaletteRandomizer(handler, settings, new Random(seed));
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

    static void link(Species from, Species to) {
        Evolution edge = new Evolution(from, to, EvolutionType.LEVEL, 16);
        from.getEvolutionsFrom().add(edge);
        to.getEvolutionsTo().add(edge);
    }

    static Object field(Gen3RomHandler handler, String name) throws ReflectiveOperationException {
        var field = Gen3RomHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(handler);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<Species, byte[]> snapshots(Gen3RomHandler handler, String name) throws ReflectiveOperationException {
        return (java.util.Map<Species, byte[]>) field(handler, name);
    }

    static final class SelectedHandler extends Gen3RomHandler {
        private final SpeciesSet species;
        final com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry entry;
        final int normalTable = 0x100, shinyTable = 0x4000;
        SelectedHandler(SpeciesSet species) { this(species, true); }
        SelectedHandler(SpeciesSet species, boolean load) {
            this.species = species;
            try {
                var constructor = com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry.class.getDeclaredConstructor(String.class);
                constructor.setAccessible(true);
                entry = constructor.newInstance("SYNTHETIC GFX004");
                entry.setRomCode("BPRE"); entry.setRomType(com.uprfvx.romio.constants.Gen3Constants.RomType_FRLG);
                int count = species.stream().mapToInt(Species::getNumber).max().orElse(4);
                entry.putIntValue("PokemonCount", count);
                entry.putIntValue("PokemonNormalPalettes", normalTable);
                entry.putIntValue("PokemonShinyPalettes", shinyTable);
                int[] mapping = new int[count + 1];
                Species[] owners = new Species[count + 1];
                for (Species sp : species) { mapping[sp.getNumber()] = sp.getNumber(); owners[sp.getNumber()] = sp; }
                set("romEntry", entry); set("pokedexToInternal", mapping); set("internalToPokedex", mapping.clone());
                set("pokesInternal", owners); set("useCfruDpeGen9SpeciesCount", true); set("isRomHack", true);
                rom = new byte[0x40000];
                for (Species sp : species) {
                    if (sp.getNormalPalette() != null) payload(sp, false, sp.getNormalPalette());
                    if (sp.getShinyPalette() != null) payload(sp, true, sp.getShinyPalette());
                }
                freeSpace(0x20000, 0x10000);
                if (load) loadPokemonPalettes();
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
        private void set(String name, Object value) throws ReflectiveOperationException {
            var field = Gen3RomHandler.class.getDeclaredField(name); field.setAccessible(true); field.set(this, value);
        }
        private void payload(Species sp, boolean shiny, Palette palette) {
            int target = 0x8000 + sp.getNumber() * 128 + (shiny ? 64 : 0);
            byte[] compressed = compressors.DSCmp.compressLZ10(palette.toBytes());
            System.arraycopy(compressed, 0, rom, target, compressed.length);
            pointer((shiny ? shinyTable : normalTable) + sp.getNumber() * 8, target);
        }
        int normalSource(Species sp) { return 0x8000 + sp.getNumber() * 128; }
        void pointer(int entry, int target) { writePointer(entry, target); }
        int pointerAt(int entry) { return readPointer(entry); }
        int readEntry(Species sp, boolean shiny) { return readPointer((shiny ? shinyTable : normalTable) + sp.getNumber() * 8); }
        byte[] readback(Species sp, boolean shiny) { return new Palette(compressors.DSDecmp.Decompress(rom, readEntry(sp, shiny))).toBytes(); }
        byte[] buffer() { return rom; }
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
        return new Gen3to5PaletteRandomizer(proxy(speciesSet), settings, new Random(1));
    }

    private static RomHandler proxy(SpeciesSet speciesSet) {
        return (RomHandler) Proxy.newProxyInstance(
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
        species.setShinyPalette(new Palette(16, new Color(240, 16, 32)));
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
