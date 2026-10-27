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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;


public class Gen3to5PaletteBoundsTest {

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void actualNormalRandomizerThenIntroSyncChecksOwnershipBeforeSave(boolean follow, boolean intro) throws Exception {
        int sourceId = com.uprfvx.romio.constants.SpeciesIDs.nidoranFemale;
        Species source = speciesWithPalette(sourceId), child = speciesWithPalette(sourceId + 1);
        Species target = speciesWithPalette(com.uprfvx.romio.constants.SpeciesIDs.pikachu);
        source.setName("Nidoran F"); child.setName("Nidorina"); target.setName("Pikachu");
        target.setNormalPalette(new Palette(16, new Color(224, 64, 16)));
        link(source, child);
        var handler = new SelectedHandler(List.of(source, child, target));
        handler.configureIntro(List.of(source, target));
        byte[] memory = handler.buffer().clone(); String ranges = handler.allocatorRanges();
        byte[] original = source.getNormalPalette().toBytes(), targetColors = target.getNormalPalette().toBytes();
        assertFalse(Arrays.equals(original, targetColors));
        var originals = snapshots(handler, "originalCfruDpeNormalPaletteBytes");
        byte[] snapshot = originals.get(source);
        assertArrayEquals(original, snapshot);
        for (Species sp : List.of(source, child, target))
            assertTrue(handler.getCfruDpeNormalPaletteEligibility(sp).eligible());
        int entry = handler.normalTable + sourceId * 8;
        int originalPointer = handler.pointerAt(entry), targetPointer = handler.readEntry(target, false);
        assertNotEquals(originalPointer, targetPointer);
        var randomizer = selected(handler, paletteSettings(false, follow), 677);
        randomizer.randomizePokemonPalettes(); assertTrue(randomizer.isChangesMade());
        Palette recolored = source.getNormalPalette(); byte[] changed = recolored.toBytes();
        assertFalse(Arrays.equals(original, changed)); assertFalse(Arrays.equals(targetColors, changed));
        assertArrayEquals(memory, handler.buffer()); assertEquals(ranges, handler.allocatorRanges());
        assertSame(snapshot, originals.get(source)); assertArrayEquals(original, snapshot);
        if (intro) {
            // This legacy fixture has no #78 witness; it must now fail closed.
            assertThrows(com.uprfvx.romio.exceptions.RomIOException.class,()->handler.setIntroPokemon(target));
            assertArrayEquals(memory,handler.buffer());
        }
        assertEquals(originalPointer, handler.pointerAt(entry));
        assertSame(recolored, source.getNormalPalette()); assertArrayEquals(changed, recolored.toBytes());
        assertSame(snapshot, originals.get(source)); assertArrayEquals(original, snapshot);
        assertEquals(ranges, handler.allocatorRanges());
        byte[] beforeSave = handler.buffer().clone();
        assertDoesNotThrow(handler::savePokemonPalettes);
        for (Species sp : List.of(source, child, target)) {
            assertArrayEquals(sp.getNormalPalette().toBytes(), handler.readback(sp, false));
            assertArrayEquals(sp.getShinyPalette().toBytes(), handler.readback(sp, true));
        }
        assertSame(recolored, source.getNormalPalette()); assertArrayEquals(changed, recolored.toBytes());
        assertSame(snapshot, originals.get(source)); assertArrayEquals(original, snapshot);
    }

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
        // A feature-level RANDOM request with no eligible channel is a controlled non-PASS.
        var normalOnly = selected(new SelectedHandler(new SpeciesSet(sp)), paletteSettings(false, false), 1);
        var failure = assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                normalOnly::randomizePokemonPalettes);
        assertTrue(failure.getMessage().endsWith("NO_ELIGIBLE_NORMAL_PALETTES"));
        assertFalse(normalOnly.isChangesMade());
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
            Settings settings = paletteSettings(false, follow);
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

    static Gen3to5PaletteRandomizer selected(Gen3RomHandler handler, Settings settings, long seed) {
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

    private static final class SyntheticOakFixture {
        final byte[] memory;
        final java.util.Properties p = new java.util.Properties();
        static final int D = 0x180000, B = 0x180100, SB = 0x180200, SC = 0x180300,
                CB = 0x180400, CC = 0x180500, FRONT = 0x20000, PALETTE = 0x24000,
                BACK = 0x27000, SHINY = 0x2a000;
        SyntheticOakFixture() { this(0x200000); }
        SyntheticOakFixture(int size) {
            memory = new byte[size];
            memory[0xac]='B'; memory[0xad]='P'; memory[0xae]='R'; memory[0xaf]='E';
            put32(0x128,0x08000000+FRONT);put32(0x12c,0x08000000+BACK);
            put32(0x130,0x08000000+PALETTE);put32(0x134,0x08000000+SHINY);
            for (int i=0;i<1440;i++) {
                put32(FRONT+i*8,0x08040000);put16(FRONT+i*8+4,8);put32(PALETTE+i*8,0x08040200);
                put32(BACK+i*8,0x08040400);put16(BACK+i*8+4,8);put32(SHINY+i*8,0x08040600);
            }
            memory[0x40000]=0x10;memory[0x40001]=8;memory[0x40200]=0x10;memory[0x40201]=32;
            memory[0x40400]=0x10;memory[0x40401]=8;memory[0x40600]=0x10;memory[0x40601]=32;
            System.arraycopy("CFROAK01".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,memory,D,8);
            put32(D+8,0x08000000+D); put32(D+16,1); put32(D+28,0x08000000+B);
            put32(D+36,0x08000001+SB); put32(D+40,0x08000001+SC);
            put32(D+44,0x08000001+CB); put32(D+48,0x08000001+CC);
            put32(D+52,0x08000128); put32(D+56,0x08000130);
            int[] offs={12,14,20,22,24,26,32,34,60,62,64,66,68,70,92,94};
            int[] vals={1,96,1440,29,92,2,180,1,8,8,28,20,24,4,29,0};
            for(int i=0;i<offs.length;i++) put16(D+offs[i],vals[i]);
            byte[] bits=java.util.HexFormat.of().parseHex("feffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0f0000e0fffffffffffffffffffffffffffdffffebfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffb3f0000fd03dfffffffffffffffffdf21ffffff1f000000000080ffffffffffffffff7fffffffffffaf780000fefefdffffffffffffffffffffffff7ffe0bfffb6fffffff0f000000c0ffffffffffffffbfffffffffffffffffc39f");
            System.arraycopy(bits,0,memory,B,bits.length);
            put32(0x3ea7c,0x08090000);put32(0x43e20,0x08090000);
            for(int i=0;i<1440;i++)put32(0x90000+4*i,0x08093000);
            memory[0x93002]=(byte)255;
            bridge(SB,SC); bridge(CB,CC);
            hook(0x130f2c,SB);hook(0x12fa78,CB);
            // Synthetic inherited #77 evolution and Pickup roots and physical objects.
            put32(0x42f6c,0x08050000);put32(0x42ec4,0x47184b00);put32(0x42ec8,0x08030001);
            System.arraycopy("CFRUEVO1".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,memory,0x35000,8);
            put32(0x35008,0x08035000);put32(0x3500c,0x08030001);
            int[] evo={1,32,28,1,220,160};for(int i=0;i<evo.length;i++)put16(0x35010+2*i,evo[i]);
            memory[0x3501c]=(byte)220;
            for(int at:new int[]{0x14c1c,0x15a28,0x15c6c,0x15c98,0x1d054})put32(at,0x08082000);
            put32(0x82000+0xe5*4,0x08083001);
            int[] pickop={0xb510,0x4804,0x4b04,0xf000,0xf803,0xbc10,0xbc01,0x4700,0x4718,0x46c0};
            for(int i=0;i<pickop.length;i++)put16(0x83000+2*i,pickop[i]);
            put32(0x83014,0x08084000);put32(0x83018,0x08083101);
            put32(0x84000,0x31555043);put16(0x84004,1);put16(0x84006,64);put32(0x84008,6);put32(0x8400c,1);
            int[] picktables={0x84100,0x84140,0x84180,0x841c0};
            int[] pickcounts={18,11,9,2};for(int i=0;i<4;i++) {put32(0x84010+4*i,0x08000000+picktables[i]);put16(0x84020+2*i,pickcounts[i]);}
            int[] shape={4,2,4,10,10,9,2,1,100,0,0,0};for(int i=0;i<shape.length;i++)memory[0x84028+i]=(byte)shape[i];
            put32(0x84034,779);put32(0x84038,0x08083101);put32(0x8403c,0x08083001);
            int[] ceilings={19661,26214,32768,39322,45875,52429,58982,61604,64225,64881,65536};
            for(int i=0;i<ceilings.length;i++)put32(i<9?0x84180+4*i:0x841c0+4*(i-9),ceilings[i]);
            set("schema","OAK_OWNERSHIP_WITNESS_V1"); set("version","1");
            set("cfru.sha","196058edbc2b8b7d8c725b715b1f967ba60f5c83");
            set("dpe.sha","d887185de1f6ae6a78e85c4311bbadde17041d00");
            set("build.id","synthetic-build"); set("config.id","synthetic-config"); set("config.sha256","0".repeat(64));
            set("input.size",memory.length); set("selector.offset",92); set("selector.width",2);
            range("descriptor",D,D+96); range("bitmap",B,B+180); range("sprite.bridge",SB,SB+28);
            range("sprite.consumer",SC,SC+64); range("cry.bridge",CB,CB+28); range("cry.consumer",CC,CC+64);
            set("insertions.count",1); range("insertions.0",D,D+0x1000);
            String[] types={"EVOLUTION_CODE","EVOLUTION_DATA","PICKUP_CODE","PICKUP_DATA","FRONT_TABLE",
                    "PALETTE_TABLE","FRONT_ASSET","PALETTE_ASSET","SPRITE_HOOK","CRY_HOOK","OTHER_CODE","OTHER_DATA",
                    "EVOLUTION_RECORD","PICKUP_DATA","LEARNSET_TABLE","LEARNSET_ASSET","BACK_TABLE","BACK_ASSET","SHINY_TABLE","SHINY_ASSET"};
            int[] starts={0x30000,0x50000,0x83000,0x82000,FRONT,PALETTE,0x40000,0x40200,0x130f2c,0x12fa78,0x34000,0x37000,
                    0x35000,0x84000,0x90000,0x93000,BACK,0x40400,SHINY,0x40600};
            int[] lengths={0x80,1440*128,0x200,0x400,1440*8,1440*8,0x100,0x100,20,20,0x80,0x80,32,0x200,1440*4,0x200,1440*8,0x100,1440*8,0x100};
            set("protected.count",types.length);
            for(int i=0;i<types.length;i++) {set("protected."+i+".type",types[i]);range("protected."+i,starts[i],starts[i]+lengths[i]);}
            digest();
        }
        void hook(int at,int target) {
            int[] op={0xb510,0x4c03,0xf000,0xf803,0xbc10,0xbc02,0x4708,0x4720};
            for(int i=0;i<op.length;i++)put16(at+2*i,op[i]);put32(at+16,0x08000001+target);
        }
        void bridge(int at,int consumer) {
            int[] op={0xb510,0x4904,0x4b04,0xf000,0xf803,0xbc10,0xbc01,0x4700,0x4718,0x46c0};
            for(int i=0;i<op.length;i++)put16(at+2*i,op[i]);
            put32(at+20,0x08000000+D);put32(at+24,0x08000001+consumer);
        }
        void put16(int at,int n) {memory[at]=(byte)n;memory[at+1]=(byte)(n>>>8);}
        void put32(int at,int n) {put16(at,n);put16(at+2,n>>>16);}
        void set(String k,Object v) {p.setProperty(k,v.toString());}
        void range(String k,int start,int end) {set(k+".start",start);set(k+".end",end);}
        void digest() {
            try {set("input.sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(memory)));}
            catch(java.security.NoSuchAlgorithmException e) {throw new AssertionError(e);}
        }
        byte[] serialized() throws Exception {
            var out=new java.io.ByteArrayOutputStream();p.store(out,null);return out.toByteArray();
        }
    }

    static class OakPaletteHandler extends Gen3RomHandler {
        final List<Species> owners;
        final SyntheticOakFixture fixture;
        OakPaletteHandler(List<Species> owners,boolean intro) throws Exception {
            this.owners=owners;fixture=new SyntheticOakFixture();rom=fixture.memory;
            var ctor=com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);var entry=ctor.newInstance("synthetic Oak GFX");entry.setRomCode("BPRE");
            entry.setRomType(com.uprfvx.romio.constants.Gen3Constants.RomType_FRLG);
            entry.putIntValue("PokemonCount",1440);entry.putIntValue("PokemonFrontImages",SyntheticOakFixture.FRONT);
            entry.putIntValue("PokemonNormalPalettes",SyntheticOakFixture.PALETTE);
            entry.putIntValue("PokemonShinyPalettes",SyntheticOakFixture.SHINY);
            Species[] nativeOwners=new Species[1440];int[] dex=new int[1440],reverse=new int[1440];
            for(Species sp:owners) {
                int id=sp.getSpeciesSetIdentityNumber();nativeOwners[id]=sp;dex[id]=sp.getNumber();reverse[sp.getNumber()]=id;
                for(boolean shiny:new boolean[]{false,true}) {
                    int asset=(shiny?0xb8000:0xb0000)+id*128;
                    byte[] data=compressors.DSCmp.compressLZ10((shiny?sp.getShinyPalette():sp.getNormalPalette()).toBytes());
                    System.arraycopy(data,0,rom,asset,data.length);
                    writePointer((shiny?SyntheticOakFixture.SHINY:SyntheticOakFixture.PALETTE)+id*8,asset);
                }
            }
            fixture.set("protected.count",22);fixture.set("protected.20.type","PALETTE_ASSET");fixture.range("protected.20",0xb0000,0xb2000);
            fixture.set("protected.21.type","SHINY_ASSET");fixture.range("protected.21",0xb8000,0xba000);
            setField("romEntry",entry);setField("pokesInternal",nativeOwners);setField("internalToPokedex",dex);
            setField("pokedexToInternal",reverse);setField("useCfruDpeGen9SpeciesCount",true);setField("isRomHack",true);
            var construct=Gen3RomHandler.class.getDeclaredMethod("constructPokemonList");construct.setAccessible(true);construct.invoke(this);
            freeSpace(0x1a0000,0x10000);fixture.digest();originalRom=rom.clone();loadPokemonPalettes();
            if(intro) {
                var install=Gen3RomHandler.class.getDeclaredMethod("installOakWitness",java.io.InputStream.class,java.util.function.Supplier.class);
                install.setAccessible(true);install.invoke(this,new java.io.ByteArrayInputStream(fixture.serialized()),null);
            }
        }
        void setField(String name,Object value) throws Exception {var f=Gen3RomHandler.class.getDeclaredField(name);f.setAccessible(true);f.set(this,value);}
        byte[] memory() {return rom;}
        int ptr(int offset) {return readPointer(offset,true);}
        byte[] palette(Species sp) {return new Palette(compressors.DSDecmp.Decompress(rom,ptr(SyntheticOakFixture.PALETTE+sp.getSpeciesSetIdentityNumber()*8))).toBytes();}
        @Override public String getPaletteFilesID() {return "FRLG";}
    }

    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void nativeOakIntroAndNormalPaletteRandomizationComposeAndReplay(boolean follow,boolean intro) throws Exception {
        byte[] replay=null;long next=0;
        for(int repeat=0;repeat<2;repeat++) {
            Species source=speciesWithPalette(29),child=speciesWithPalette(30),target=speciesWithPalette(25);
            source.setName("Nidoran F");child.setName("Nidorina");target.setName("Pikachu");link(source,child);
            var handler=new OakPaletteHandler(List.of(source,child,target),intro);
            int normal=handler.ptr(SyntheticOakFixture.PALETTE+29*8),shiny=handler.ptr(SyntheticOakFixture.SHINY+29*8);
            byte[] front=Arrays.copyOfRange(handler.memory(),SyntheticOakFixture.FRONT+29*8,SyntheticOakFixture.FRONT+29*8+8);
            Random rng=new Random(677);var randomizer=new Gen3to5PaletteRandomizer(handler,paletteSettings(false,follow),rng);
            byte[] before=handler.memory().clone();randomizer.randomizePokemonPalettes();assertTrue(randomizer.isChangesMade());
            assertArrayEquals(before,handler.memory());
            if(intro) {
                assertTrue(handler.setIntroPokemon(target));byte[] expected=before.clone();
                expected[SyntheticOakFixture.D+92]=25;expected[SyntheticOakFixture.D+93]=0;assertArrayEquals(expected,handler.memory());
            } else assertEquals(29,(handler.memory()[SyntheticOakFixture.D+92]&255));
            assertEquals(normal,handler.ptr(SyntheticOakFixture.PALETTE+29*8));
            assertEquals(shiny,handler.ptr(SyntheticOakFixture.SHINY+29*8));
            assertArrayEquals(front,Arrays.copyOfRange(handler.memory(),SyntheticOakFixture.FRONT+29*8,SyntheticOakFixture.FRONT+29*8+8));
            handler.savePokemonPalettes();for(Species sp:List.of(source,child,target))assertArrayEquals(sp.getNormalPalette().toBytes(),handler.palette(sp));
            if(repeat==0) {replay=handler.memory().clone();next=rng.nextLong();}
            else {assertArrayEquals(replay,handler.memory());assertEquals(next,rng.nextLong());}
        }
    }

    @Test void fullRandomizerRegistersBoundSyntheticOwnersBeforeRestrictions() throws Exception {
        Species source=speciesWithPalette(29);source.setName("Nidoran F");source.setGeneration(1);
        source.setHp(50);source.setAttack(50);source.setDefense(50);source.setSpeed(50);source.setSpatk(50);source.setSpdef(50);
        class Fixture extends OakPaletteHandler {
            int restrictionCalls;
            Fixture() throws Exception {super(List.of(source),false);}
            @Override public java.util.Map<Integer,java.util.List<com.uprfvx.romio.gamedata.MoveLearnt>> getMovesLearnt() {
                return java.util.Map.of(29,java.util.List.of(new com.uprfvx.romio.gamedata.MoveLearnt(33,1)));
            }
            @Override public com.uprfvx.romio.services.RestrictedSpeciesService getRestrictedSpeciesService() {
                return new com.uprfvx.romio.services.RestrictedSpeciesService(this) {
                    @Override public void setRestrictions(com.uprfvx.romio.gamedata.GenRestrictions r,
                            com.uprfvx.romio.services.SpecialFormExclusionOptions options) {
                        restrictionCalls++;
                        assertThrows(IllegalStateException.class,()->getFreedSpace().free(SyntheticOakFixture.D,1));
                        assertThrows(IllegalStateException.class,()->getFreedSpace().free(SyntheticOakFixture.PALETTE,1));
                        throw new com.uprfvx.romio.exceptions.RomIOException("synthetic restriction stop");
                    }
                };
            }
            @Override public int generationOfPokemon() {return 3;}
            @Override public java.util.List<Integer> getMoveTutorMoves() {return java.util.List.of();}
            @Override public java.util.List<com.uprfvx.romio.gamedata.Trainer> getTrainers() {return java.util.List.of();}
            @Override public boolean canChangeStaticPokemon() {return false;}
            @Override public java.util.List<com.uprfvx.romio.gamedata.InGameTrade> getInGameTrades() {return java.util.List.of();}
            @Override public java.util.List<com.uprfvx.romio.gamedata.Move> getMoves() {return java.util.List.of();}
        }
        var f=new Fixture();var settings=new Settings();settings.setRandomizeIntroMon(true);
        java.util.ResourceBundle bundle=new java.util.ListResourceBundle() {@Override protected Object[][] getContents(){return new Object[0][];}};
        var randomizer=new com.uprfvx.random.GameRandomizer(settings,null,f,bundle,false);byte[] before=f.memory().clone();
        java.nio.file.Path synthetic=java.nio.file.Files.createTempFile("synthetic-oak-witness-", ".properties");
        try {
            java.nio.file.Files.write(synthetic,f.fixture.serialized());System.setProperty("uprfvx.cfruOakOwnership",synthetic.toString());
            var result=randomizer.randomize("unused-synthetic",new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),677);
            assertFalse(result.wasSaveSuccessful());assertEquals("synthetic restriction stop",result.getException().getMessage());
            assertEquals(1,f.restrictionCalls);assertArrayEquals(before,f.memory());
        } finally {System.clearProperty("uprfvx.cfruOakOwnership");java.nio.file.Files.deleteIfExists(synthetic);}
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
        SelectedHandler(List<Species> nativeOwners) throws ReflectiveOperationException {
            // Same generated-memory fixture, but retain native IDs/zero Dex rows and use
            // the real Species-list constructor instead of a pre-collapsed SpeciesSet.
            species = null;
            var constructor = com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true); entry = constructor.newInstance("SYNTHETIC native owner list");
            entry.setRomCode("BPRE"); entry.setRomType(com.uprfvx.romio.constants.Gen3Constants.RomType_FRLG);
            entry.putIntValue("PokemonCount", 1440);
            entry.putIntValue("PokemonNormalPalettes", normalTable); entry.putIntValue("PokemonShinyPalettes", shinyTable);
            Species[] owners = new Species[1440]; int[] dex = new int[1440], reverse = new int[1440];
            rom = new byte[0x60000];
            for (Species sp : nativeOwners) {
                int id = sp.getSpeciesSetIdentityNumber(); owners[id] = sp; dex[id] = sp.getNumber();
                if (sp.getNumber() > 0 && reverse[sp.getNumber()] == 0) reverse[sp.getNumber()] = id;
                for (boolean shiny : new boolean[]{false, true}) {
                    Palette palette = shiny ? sp.getShinyPalette() : sp.getNormalPalette();
                    if (palette == null) continue;
                    int target = 0x10000 + id * 128 + (shiny ? 64 : 0);
                    byte[] compressed = compressors.DSCmp.compressLZ10(palette.toBytes());
                    System.arraycopy(compressed, 0, rom, target, compressed.length);
                    pointer((shiny ? shinyTable : normalTable) + id * 8, target);
                }
            }
            set("romEntry", entry); set("pokesInternal", owners); set("internalToPokedex", dex);
            set("pokedexToInternal", reverse); set("useCfruDpeGen9SpeciesCount", true); set("isRomHack", true);
            var construct = Gen3RomHandler.class.getDeclaredMethod("constructPokemonList");
            construct.setAccessible(true); construct.invoke(this);
            freeSpace(0x50000, 0x10000); loadPokemonPalettes();
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
        void configureIntro(List<Species> owners) {
            entry.putIntValue("PokemonFrontImages", 0x8000);
            entry.putIntValue("IntroImageOffset", 0xC000);
            entry.putIntValue("IntroCryOffset", 0xC020); entry.putIntValue("IntroOtherOffset", 0xC024);
            for (Species sp : owners) {
                int asset = 0xE000 + sp.getSpeciesSetIdentityNumber() * 64;
                byte[] compressed = compressors.DSCmp.compressLZ10(new byte[0x800]);
                System.arraycopy(compressed, 0, rom, asset, compressed.length);
                pointer(0x8000 + sp.getSpeciesSetIdentityNumber() * 8, asset);
            }
        }
        void pointer(int entry, int target) { writePointer(entry, target); }
        int pointerAt(int entry) { return readPointer(entry); }
        int readEntry(Species sp, boolean shiny) { return readPointer((shiny ? shinyTable : normalTable)
                + (species == null ? sp.getSpeciesSetIdentityNumber() : sp.getNumber()) * 8); }
        byte[] readback(Species sp, boolean shiny) { return new Palette(compressors.DSDecmp.Decompress(rom, readEntry(sp, shiny))).toBytes(); }
        byte[] buffer() { return rom; }
        String allocatorRanges() { return getFreedSpace().toString(); }
        @Override public String getPaletteFilesID() { return "FRLG"; }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return species == null ? super.getSpeciesSetInclFormes() : species; }
        @Override public SpeciesSet getSpeciesSet() { return species == null ? super.getSpeciesSet() : species; }
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
                assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                        randomizer::randomizePokemonPalettes);
                assertFalse(randomizer.isChangesMade());
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
            // Only FRLG is a real selected CFRU/DPE profile. Give that branch truthful
            // loader provenance and compare against the same decoded bytes in vanilla.
            RomHandler handler = !types && paletteId.equals("FRLG") ? new SelectedHandler(selected)
                    : new SyntheticGen3(selected, types, paletteId);
            if (!types && paletteId.equals("FRLG")) for (Species sp : selected) vanilla.stream()
                    .filter(v -> v.getNumber() == sp.getNumber()).findFirst().orElseThrow()
                    .setNormalPalette(new Palette(sp.getNormalPalette()));
            var original = new Gen3to5PaletteRandomizer(new SyntheticGen3(vanilla, false, paletteId),
                    paletteSettings(types, evolutions), new Random(724));
            original.randomizePokemonPalettes();
            var control = new Gen3to5PaletteRandomizer(handler,
                    paletteSettings(types, evolutions), new Random(724));
            control.randomizePokemonPalettes();
            for (Species sp : selected) assertArrayEquals(vanilla.stream()
                    .filter(v -> v.getNumber() == sp.getNumber()).findFirst().orElseThrow()
                    .getNormalPalette().toBytes(), sp.getNormalPalette().toBytes(),
                    paletteId + ": types=" + types + ", evolutions=" + evolutions);
            assertTrue(original.isChangesMade());
        }
    }

    @Test
    void normalOnlyMissingChannelsPreserveAssetsAndRecolorValidOwnersInBothModes() throws Exception {
        for (boolean follow : new boolean[]{false, true}) for (int missing : new int[]{1, 2, 3}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            link(a, b);
            if ((missing & 1) != 0) a.setNormalPalette(null);
            if ((missing & 2) != 0) a.setShinyPalette(null);
            SelectedHandler handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            Palette missingNormal = a.getNormalPalette(), missingShiny = a.getShinyPalette();
            byte[] child = b.getNormalPalette().toBytes(), shiny = b.getShinyPalette().toBytes();
            byte[] buffer = handler.buffer().clone();
            var randomizer = selected(handler, paletteSettings(false, follow), 677);
            assertDoesNotThrow(randomizer::randomizePokemonPalettes);
            assertTrue(randomizer.isChangesMade());
            if ((missing & 1) != 0) assertSame(missingNormal, a.getNormalPalette());
            else assertNotSame(missingNormal, a.getNormalPalette()); // missing shiny alone is eligible
            assertSame(missingShiny, a.getShinyPalette());
            assertFalse(Arrays.equals(child, b.getNormalPalette().toBytes()));
            assertArrayEquals(shiny, b.getShinyPalette().toBytes());
            assertArrayEquals(buffer, handler.buffer()); // randomizer never touches pointers/ROM memory
            assertDoesNotThrow(handler::savePokemonPalettes);
            assertArrayEquals(b.getNormalPalette().toBytes(), handler.readback(b, false));
            assertArrayEquals(shiny, handler.readback(b, true));
            assertArrayEquals(Arrays.copyOfRange(buffer, 0, handler.normalTable),
                    Arrays.copyOfRange(handler.buffer(), 0, handler.normalTable));
        }
    }

    @Test
    void normalOnlyCanonicalBadEggRecolorsSavesReopensAndReplaysBothFollowModes() throws Exception {
        for (boolean follow : new boolean[]{false, true}) for (long seed : new long[]{1, 677, 724, 20261020}) {
            byte[] replay = null; long nextDraw = 0;
            for (int repetition = 0; repetition < 2; repetition++) {
                SelectedHandler handler = badEggHandler();
                Species valid = handler.getSpecies().get(1), child = handler.getSpecies().get(2), egg = handler.getSpecies().get(3);
                assertSame(egg, ((Species[]) field(handler, "pokesInternal"))[0x19C]);
                assertEquals(0, ((int[]) field(handler, "internalToPokedex"))[0x19C]);
                assertTrue(handler.getCfruDpeNormalPaletteEligibility(valid).eligible());
                assertFalse(handler.getCfruDpeNormalPaletteEligibility(egg).blocked());
                assertNull(egg.getNormalPalette()); assertNull(egg.getShinyPalette());
                byte[] memory = handler.buffer().clone(); String ranges = handler.allocatorRanges();
                byte[] original = valid.getNormalPalette().toBytes(), originalChild = child.getNormalPalette().toBytes();
                byte[] shiny = valid.getShinyPalette().toBytes(), childShiny = child.getShinyPalette().toBytes();
                Random rng = new Random(seed);
                var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, follow), rng);
                java.io.ByteArrayOutputStream report = new java.io.ByteArrayOutputStream();
                java.io.PrintStream previous = System.err;
                try (var capture = new java.io.PrintStream(report)) {
                    System.setErr(capture); randomizer.randomizePokemonPalettes();
                } finally { System.setErr(previous); }
                assertTrue(randomizer.isChangesMade());
                assertTrue(report.toString(java.nio.charset.StandardCharsets.UTF_8)
                        .matches("(?s).*changedNormal=[1-9][0-9]* .*"));
                assertArrayEquals(memory, handler.buffer()); assertEquals(ranges, handler.allocatorRanges());
                assertFalse(Arrays.equals(original, valid.getNormalPalette().toBytes()));
                assertFalse(Arrays.equals(originalChild, child.getNormalPalette().toBytes()));
                assertArrayEquals(shiny, valid.getShinyPalette().toBytes());
                assertArrayEquals(childShiny, child.getShinyPalette().toBytes());
                assertArrayEquals(original, snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(valid));
                assertArrayEquals(originalChild, snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(child));
                assertFalse(snapshots(handler, "originalCfruDpeNormalPaletteBytes").containsKey(egg));
                assertFalse(snapshots(handler, "originalCfruDpeShinyPaletteBytes").containsKey(egg));
                handler.savePokemonPalettes(); // Actual transactional copy writer, no synthetic save override.
                assertNormalCopyBudget(handler, memory, List.of(valid, child));
                assertNull(egg.getNormalPalette()); assertNull(egg.getShinyPalette());
                assertArrayEquals(original, snapshots(handler, "originalCfruDpeNormalPaletteBytes").get(valid));
                SelectedHandler reopened = badEggHandler();
                System.arraycopy(handler.buffer(), 0, reopened.buffer(), 0, handler.buffer().length);
                reopened.loadPokemonPalettes(); // Fresh native model, actual loader and snapshot capture.
                for (int index : new int[]{1, 2}) {
                    Species expected = handler.getSpecies().get(index), actual = reopened.getSpecies().get(index);
                    assertArrayEquals(expected.getNormalPalette().toBytes(), actual.getNormalPalette().toBytes());
                    assertArrayEquals(expected.getShinyPalette().toBytes(), actual.getShinyPalette().toBytes());
                }
                Species readEgg = reopened.getSpecies().get(3);
                assertNull(readEgg.getNormalPalette()); assertNull(readEgg.getShinyPalette());
                assertFalse(snapshots(reopened, "originalCfruDpeNormalPaletteBytes").containsKey(readEgg));
                assertFalse(snapshots(reopened, "originalCfruDpeShinyPaletteBytes").containsKey(readEgg));
                byte[] saved = reopened.buffer().clone(); reopened.savePokemonPalettes();
                assertArrayEquals(saved, reopened.buffer());
                long draw = rng.nextLong();
                if (repetition == 0) { replay = saved; nextDraw = draw; }
                else { assertArrayEquals(replay, saved); assertEquals(nextDraw, draw); }
            }
        }
    }

    @Test
    void normalOnlyBadEggCorruptionStopsBothFollowModesBeforeRngAndForcedWriterMutation() throws Exception {
        for (boolean follow : new boolean[]{false, true}) for (int kind = 0; kind < 12; kind++) {
            SelectedHandler handler = badEggHandler(); Species valid = handler.getSpecies().get(1), egg = handler.getSpecies().get(3);
            Species[] internal = (Species[]) field(handler, "pokesInternal");
            int[] dex = (int[]) field(handler, "internalToPokedex");
            switch (kind) {
                case 0 -> handler.getSpecies().add(egg);
                case 1 -> { Species other = speciesWithPalette(0); other.setSpeciesSetIdentityNumber(0x19C); handler.getSpecies().add(other); }
                case 2 -> egg.setName("Impostor");
                case 3 -> { egg.setSpeciesSetIdentityNumber(413); internal[413] = egg; internal[0x19C] = null; }
                case 4 -> dex[0x19C] = 4;
                case 5 -> egg.setNormalPalette(new Palette(valid.getNormalPalette()));
                case 6 -> egg.setShinyPalette(new Palette(valid.getShinyPalette()));
                case 7 -> snapshots(handler, "originalCfruDpeNormalPaletteBytes").put(egg, valid.getNormalPalette().toBytes());
                case 8 -> snapshots(handler, "originalCfruDpeShinyPaletteBytes").put(egg, valid.getShinyPalette().toBytes());
                case 9 -> internal[413] = egg;
                case 10 -> ((int[]) field(handler, "pokedexToInternal"))[1] = 0x19C;
                case 11 -> handler.entry.putIntValue("PokemonCount", 1439);
                default -> throw new AssertionError(kind);
            }
            assertGlobalNormalOwnerStop(handler, valid, follow);
        }
    }

    private static SelectedHandler badEggHandler() throws Exception {
        Species valid = speciesWithPalette(4), child = speciesWithPalette(5), egg = speciesWithPalette(0), unused = speciesWithPalette(0);
        valid.setName("Charmander"); child.setName("Charmeleon"); link(valid, child);
        egg.setName("Bad Egg"); egg.setSpeciesSetIdentityNumber(0x19C);
        unused.setName("?"); unused.setSpeciesSetIdentityNumber(0xFC);
        var handler = new SelectedHandler(List.of(valid, child, egg, unused));
        assertEquals(Arrays.asList(null, valid, child, egg), handler.getSpecies());
        return handler;
    }

    private static void assertNormalCopyBudget(SelectedHandler handler, byte[] before, List<Species> changed) {
        List<int[]> allowed = new ArrayList<>();
        for (Species sp : changed) {
            int entry = handler.normalTable + sp.getSpeciesSetIdentityNumber() * 8;
            int pointer = handler.pointerAt(entry);
            int length = compressors.DSCmp.compressLZ10(handler.readback(sp, false)).length;
            assertTrue(pointer >= 0x50000 && pointer + length <= handler.buffer().length);
            allowed.add(new int[]{entry, entry + 4}); allowed.add(new int[]{pointer, pointer + length});
            assertArrayEquals(sp.getNormalPalette().toBytes(), handler.readback(sp, false));
        }
        for (int offset = 0; offset < before.length; offset++) if (before[offset] != handler.buffer()[offset]) {
            boolean inBudget = false;
            for (int[] range : allowed) if (offset >= range[0] && offset < range[1]) inBudget = true;
            assertTrue(inBudget, "write outside normal pointer/new payload budget: " + offset);
        }
        // Entire sentinel rows, shiny table/payloads, tags, source data and adjacent bytes are outside this budget.
    }

    @Test
    void normalOnlyRawDuplicateCannotDisappearThroughSpeciesSetBeforePreflight() throws Exception {
        for (boolean sameObject : new boolean[]{false, true}) for (boolean follow : new boolean[]{false, true}) {
            Species valid = speciesWithPalette(4), other = speciesWithPalette(7);
            valid.setName("Charmander"); other.setName("Squirtle");
            var handler = new SelectedHandler(List.of(valid, other));
            handler.getSpecies().add(sameObject ? other : speciesWithPalette(7));
            assertEquals(4, handler.getSpecies().size()); assertEquals(2, handler.getSpeciesSet().size());
            assertGlobalNormalOwnerStop(handler, valid, follow);
        }
    }

    private static void assertGlobalNormalOwnerStop(SelectedHandler handler, Species valid, boolean follow) throws Exception {
        byte[] memory = handler.buffer().clone(); String ranges = handler.allocatorRanges();
        List<Palette> palettes = new ArrayList<>(); List<byte[]> colors = new ArrayList<>();
        List<java.util.Map<Species, byte[]>> originals = new ArrayList<>();
        for (String name : List.of("originalCfruDpeNormalPaletteBytes", "originalCfruDpeShinyPaletteBytes")) {
            java.util.Map<Species, byte[]> copy = new java.util.IdentityHashMap<>();
            snapshots(handler, name).forEach((sp, bytes) -> copy.put(sp, bytes.clone())); originals.add(copy);
        }
        for (Species sp : handler.getSpecies()) if (sp != null) {
            for (Palette palette : new Palette[]{sp.getNormalPalette(), sp.getShinyPalette()}) {
                palettes.add(palette); colors.add(palette == null ? null : palette.toBytes());
            }
        }
        Random rng = new Random(677);
        var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, follow), rng);
        var error = assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                randomizer::randomizePokemonPalettes);
        assertEquals("CFRU/DPE GFX-001/003: INVALID_NATIVE_OWNER", error.getMessage());
        assertFalse(randomizer.isChangesMade()); assertEquals(new Random(677).nextLong(), rng.nextLong());
        int index = 0;
        for (Species sp : handler.getSpecies()) if (sp != null) {
            for (Palette palette : new Palette[]{sp.getNormalPalette(), sp.getShinyPalette()}) {
                assertSame(palettes.get(index), palette);
                assertArrayEquals(colors.get(index++), palette == null ? null : palette.toBytes());
            }
        }
        assertArrayEquals(memory, handler.buffer()); assertEquals(ranges, handler.allocatorRanges());
        byte[] changed = valid.getNormalPalette().toBytes(); changed[2] ^= 7;
        Palette forced = new Palette(changed); valid.setNormalPalette(forced);
        assertThrows(com.uprfvx.romio.exceptions.RomIOException.class, handler::savePokemonPalettes);
        assertSame(forced, valid.getNormalPalette()); assertArrayEquals(changed, forced.toBytes());
        assertArrayEquals(memory, handler.buffer()); assertEquals(ranges, handler.allocatorRanges());
        int channel = 0;
        for (String name : List.of("originalCfruDpeNormalPaletteBytes", "originalCfruDpeShinyPaletteBytes")) {
            var current = snapshots(handler, name); var before = originals.get(channel++);
            assertEquals(before.size(), current.size()); before.forEach((sp, bytes) -> assertArrayEquals(bytes, current.get(sp)));
        }
    }

    @Test
    void normalOnlyNativeFormsMissingChannelsAndUnchangedOgerponRemainPositiveControls() throws Exception {
        for (boolean follow : new boolean[]{false, true}) for (int missing : new int[]{0, 1, 2, 3}) {
            Species valid = speciesWithPalette(4), base = speciesWithPalette(19), form = speciesWithPalette(19);
            valid.setName("Charmander"); base.setName("Rattata"); form.setName("Rattata");
            form.setSpeciesSetIdentityNumber(1020);
            if ((missing & 1) != 0) form.setNormalPalette(null);
            if ((missing & 2) != 0) form.setShinyPalette(null);
            List<Species> owners = new ArrayList<>(List.of(valid, base, form));
            for (int id = 0x592; id <= 0x595; id++) {
                Species special = speciesWithPalette(0); special.setSpeciesSetIdentityNumber(id);
                special.setName("Ogerpon"); owners.add(special);
            }
            var handler = new SelectedHandler(owners);
            assertEquals(owners, handler.getSpecies().subList(1, handler.getSpecies().size()));
            assertNotEquals(base, form); assertTrue(handler.getCfruDpeNormalPaletteEligibility(base).eligible());
            assertEquals((missing & 1) == 0, handler.getCfruDpeNormalPaletteEligibility(form).eligible());
            byte[] memory = handler.buffer().clone(); String ranges = handler.allocatorRanges();
            Palette shiny = form.getShinyPalette(); byte[] normal = valid.getNormalPalette().toBytes();
            var randomizer = selected(handler, paletteSettings(false, follow), 677);
            randomizer.randomizePokemonPalettes(); assertTrue(randomizer.isChangesMade());
            assertFalse(Arrays.equals(normal, valid.getNormalPalette().toBytes()));
            assertSame(shiny, form.getShinyPalette()); if ((missing & 1) != 0) assertNull(form.getNormalPalette());
            assertArrayEquals(memory, handler.buffer()); assertEquals(ranges, handler.allocatorRanges());
            handler.savePokemonPalettes();
            assertArrayEquals(valid.getNormalPalette().toBytes(), handler.readback(valid, false));
            for (Species special : owners.subList(3, 7)) {
                assertNull(special.getNormalPalette()); assertNull(special.getShinyPalette());
                for (int table : new int[]{handler.normalTable, handler.shinyTable}) {
                    int entry = table + special.getSpeciesSetIdentityNumber() * 8;
                    assertArrayEquals(Arrays.copyOfRange(memory, entry, entry + 8),
                            Arrays.copyOfRange(handler.buffer(), entry, entry + 8));
                }
                assertFalse(snapshots(handler, "originalCfruDpeNormalPaletteBytes").containsKey(special));
                assertFalse(snapshots(handler, "originalCfruDpeShinyPaletteBytes").containsKey(special));
            }
        }
    }

    @Test
    void normalOnlySkippedParentKeepsFullLegacyColorPlansAndRngPrefix() throws Exception {
        for (boolean follow : new boolean[]{false, true}) {
            Species parent = speciesWithPalette(4), child = speciesWithPalette(5), split = speciesWithPalette(6);
            parent.setNormalPalette(null); link(parent, child); link(parent, split);
            var handler = new SelectedHandler(new SpeciesSet(List.of(parent, child, split)));
            var randomizer = selected(handler, paletteSettings(false, follow), 677);
            randomizer.randomizePokemonPalettes();
            var field = Gen3to5PaletteRandomizer.class.getDeclaredField("typeBaseColorLists");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var plans = (java.util.Map<Species, TypeBaseColorList>) field.get(randomizer);
            assertEquals(3, plans.size()); assertTrue(plans.containsKey(parent));
            var prevo = TypeBaseColorList.class.getDeclaredField("prevo"); prevo.setAccessible(true);
            assertSame(follow ? plans.get(parent) : null, prevo.get(plans.get(child)));
            assertSame(follow ? plans.get(parent) : null, prevo.get(plans.get(split)));
            assertNull(parent.getNormalPalette());
        }
    }

    @Test
    void normalOnlyRejectsMalformedGraphBeforeAnyRngOrMutation() {
        for (int fixture = 0; fixture < 7; fixture++) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            link(a, b);
            if (fixture == 0) link(b, a);
            if (fixture == 1) a.getEvolutionsFrom().add(null);
            if (fixture == 2) b.getEvolutionsTo().clear();
            if (fixture == 3) b.getEvolutionsTo().getFirst().setTo(a);
            if (fixture == 4) b.getEvolutionsTo().add(new Evolution(speciesWithPalette(7), b, EvolutionType.LEVEL, 20));
            if (fixture == 5) a.getEvolutionsFrom().getFirst().setTo(null);
            if (fixture == 6) b.getEvolutionsTo().getFirst().setFrom(null);
            var handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            byte[] normal = a.getNormalPalette().toBytes(), shiny = a.getShinyPalette().toBytes();
            var random = new Random(677);
            var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, true), random);
            assertThrows(com.uprfvx.random.exceptions.RandomizationException.class, randomizer::randomizePokemonPalettes);
            assertEquals(new Random(677).nextLong(), random.nextLong());
            assertArrayEquals(normal, a.getNormalPalette().toBytes()); assertArrayEquals(shiny, a.getShinyPalette().toBytes());
            assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void normalOnlyLateFailureAndZeroChangeNeverPublishOrPretendToPass() {
        for (boolean fail : new boolean[]{false, true}) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            var handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            Palette originalA = a.getNormalPalette(), originalB = b.getNormalPalette();
            byte[] normalA = originalA.toBytes(), normalB = originalB.toBytes();
            var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, false), new Random(677)) {
                int calls;
                @Override public void populatePalette(Palette palette,
                        com.uprfvx.romio.graphics.palettes.PalettePopulator pp, TypeBaseColorList colors,
                        PalettePartDescription[] parts) {
                    if (fail) {
                        super.populatePalette(palette, pp, colors, parts);
                        if (++calls == 2) throw new IllegalStateException("synthetic late scratch failure");
                    }
                }
            };
            if (fail) assertThrows(IllegalStateException.class, randomizer::randomizePokemonPalettes);
            else assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                    randomizer::randomizePokemonPalettes);
            assertSame(originalA, a.getNormalPalette()); assertSame(originalB, b.getNormalPalette());
            assertArrayEquals(normalA, a.getNormalPalette().toBytes()); assertArrayEquals(normalB, b.getNormalPalette().toBytes());
            assertFalse(randomizer.isChangesMade());
            byte[] before = handler.buffer().clone(); handler.savePokemonPalettes();
            assertArrayEquals(before, handler.buffer());
        }
    }

    @Test
    void normalOnlyInvalidOwnersAndStaleChannelsStopBeforeEarlierValidMutation() throws Exception {
        for (int fixture = 0; fixture < 6; fixture++) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            var handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            if (fixture == 0) b.setSpeciesSetIdentityNumber(4);
            if (fixture == 1) handler.pointer(handler.normalTable + 5 * 8, handler.shinyTable);
            if (fixture == 2) b.getNormalPalette().set(2, null);
            if (fixture == 3) b.setNormalPalette(new Palette(15));
            if (fixture == 4) b.setNormalPalette(null); // loaded snapshot still exists
            if (fixture == 5) handler.entry.putIntValue("PokemonShinyPalettes", handler.normalTable);
            Palette normal = a.getNormalPalette(), shiny = a.getShinyPalette();
            byte[] before = handler.buffer().clone();
            var randomizer = selected(handler, paletteSettings(false, false), 1);
            assertThrows(com.uprfvx.random.exceptions.RandomizationException.class, randomizer::randomizePokemonPalettes);
            assertSame(normal, a.getNormalPalette()); assertSame(shiny, a.getShinyPalette());
            assertArrayEquals(before, handler.buffer()); assertFalse(randomizer.isChangesMade());
        }
    }

    @Test
    void normalOnlyMissingSnapshotsSharedJavaAndUnavailableDescriptionsArePreserved() throws Exception {
        for (int fixture = 0; fixture < 5; fixture++) {
            Species a = speciesWithPalette(4), b = speciesWithPalette(5);
            var handler = new SelectedHandler(new SpeciesSet(List.of(a, b)));
            if (fixture == 0) snapshots(handler, "originalCfruDpeNormalPaletteBytes").remove(a);
            if (fixture == 1) a.setShinyPalette(a.getNormalPalette());
            if (fixture == 2) a.setNormalPalette(b.getNormalPalette()); // stale and shared: strict stale gate wins
            Palette normal = a.getNormalPalette();
            final int kind = fixture;
            var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, false), new Random(677)) {
                @Override public List<PaletteDescription> getPaletteDescriptions(String key) {
                    var rows = descriptions(5);
                    if (kind == 3) rows.set(3, new PaletteDescription("[]"));
                    if (kind == 4) rows.set(3, new PaletteDescription("[2,3/17]"));
                    return rows;
                }
            };
            if (fixture == 2) assertThrows(com.uprfvx.random.exceptions.RandomizationException.class,
                    randomizer::randomizePokemonPalettes);
            else { randomizer.randomizePokemonPalettes(); assertTrue(randomizer.isChangesMade()); }
            assertSame(normal, a.getNormalPalette());
        }
        var handler = new SelectedHandler(new SpeciesSet(speciesWithPalette(388)));
        var empty = selected(handler, paletteSettings(false, false), 677);
        assertThrows(com.uprfvx.random.exceptions.RandomizationException.class, empty::randomizePokemonPalettes);
        assertFalse(empty.isChangesMade());
    }

    @Test
    void nonGen3DpAndBwNormalPalettePathsRetainLegacyBehavior() {
        for (String id : List.of("DP", "BW")) for (boolean follow : new boolean[]{false, true}) {
            SpeciesSet species = chain();
            RomHandler handler = (RomHandler) Proxy.newProxyInstance(RomHandler.class.getClassLoader(),
                    new Class[]{RomHandler.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPaletteFilesID" -> id;
                        case "generationOfPokemon" -> id.equals("DP") ? 4 : 5;
                        case "getSpeciesSetInclFormes" -> species;
                        case "getRestrictedSpeciesService", "getTypeService" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            byte[] before = species.stream().findFirst().orElseThrow().getNormalPalette().toBytes();
            var randomizer = new Gen3to5PaletteRandomizer(handler, paletteSettings(false, follow), new Random(677));
            randomizer.randomizePokemonPalettes(); assertTrue(randomizer.isChangesMade());
            assertFalse(Arrays.equals(before, species.stream().findFirst().orElseThrow().getNormalPalette().toBytes()));
        }
    }

    @Test
    void normalOnlyUnchangedSettingDoesNotConsumeRngOrTouchLivePalettes() {
        Species sp = speciesWithPalette(4);
        var handler = new SelectedHandler(new SpeciesSet(sp));
        Palette normal = sp.getNormalPalette(), shiny = sp.getShinyPalette();
        var random = new Random(677);
        var randomizer = new Gen3to5PaletteRandomizer(handler, new Settings(), random);
        randomizer.randomizePokemonPalettes();
        assertFalse(randomizer.isChangesMade()); assertSame(normal, sp.getNormalPalette());
        assertSame(shiny, sp.getShinyPalette()); assertEquals(new Random(677).nextLong(), random.nextLong());
    }

    @Test
    void normalOnlyConvergentGraphRetainsLegacyFirstParentColorsAndDraws() throws Exception {
        Species a = speciesWithPalette(4), b = speciesWithPalette(5), c = speciesWithPalette(6);
        link(a, c); link(b, c);
        var species = new SpeciesSet(List.of(a, b, c));
        var handler = new SelectedHandler(species);
        Species x = speciesWithPalette(4), y = speciesWithPalette(5), z = speciesWithPalette(6);
        link(x, z); link(y, z);
        var control = new SpeciesSet(List.of(x, y, z));
        for (Species sp : control) sp.setNormalPalette(new Palette(species.stream()
                .filter(pk -> pk.getNumber() == sp.getNumber()).findFirst().orElseThrow().getNormalPalette()));
        var sourceRng = new com.uprfvx.random.random.RandomSource(); sourceRng.seed(677);
        var legacyRng = new com.uprfvx.random.random.RandomSource(); legacyRng.seed(677);
        var settings = paletteSettings(false, true);
        new Gen3to5PaletteRandomizer(handler, settings, sourceRng.getCosmetic()).randomizePokemonPalettes();
        new Gen3to5PaletteRandomizer(new SyntheticGen3(control, false, "FRLG"), settings,
                legacyRng.getCosmetic()).randomizePokemonPalettes();
        assertEquals(legacyRng.callsSinceSeedCosmetic(), sourceRng.callsSinceSeedCosmetic());
        for (Species sp : control) assertArrayEquals(sp.getNormalPalette().toBytes(), species.stream()
                .filter(pk -> pk.getNumber() == sp.getNumber()).findFirst().orElseThrow().getNormalPalette().toBytes());
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
        RomHandler handler = types ? new SyntheticGen3(species, true, "FRLG") : new SelectedHandler(species);
        return new Gen3to5PaletteRandomizer(handler, settings, new Random(seed));
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
