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
    void selectedCombinedTypesRejectsAndDisabledGfx004DoesNothing() {
        Species sp = speciesWithPalette(4);
        SelectedHandler handler = new SelectedHandler(new SpeciesSet(sp));
        Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
        Settings combined = gfx004(false); combined.setPokemonPalettesFollowTypes(true);
        assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                () -> selected(handler, combined, 1).randomizePokemonPalettes());
        Settings disabled = gfx004(false); disabled.setPokemonPalettesMod(Settings.PokemonPalettesMod.UNCHANGED);
        Gen3to5PaletteRandomizer randomizer = selected(handler, disabled, 1);
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

    private static List<PaletteDescription> descriptions(int count) {
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
