package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Gen3OakLabRivalScriptTest {

    @Test
    public void oakLabRivalTrainerIdsAreExtractedByPlayerStarterSlot() {
        byte[] rom = new byte[512];
        writeOakTutorialTrainerBattle(rom, 100, 901);
        writeOakTutorialTrainerBattle(rom, 140, 902);
        writeOakTutorialTrainerBattle(rom, 180, 903);

        List<Integer> trainerIds = Gen3RomHandler.findFrlgOakLabRivalTrainerIdsByPlayerStarterSlot(rom, 64);

        assertEquals(List.of(902, 901, 903), trainerIds);
    }

    @Test
    public void oakLabRivalTrainerIdsSkipWhenTutorialCommandsAreMissing() {
        byte[] rom = new byte[512];
        writeTrainerBattle(rom, 100, 0x09, 901, 0);
        writeOakTutorialTrainerBattle(rom, 140, 902);

        List<Integer> trainerIds = Gen3RomHandler.findFrlgOakLabRivalTrainerIdsByPlayerStarterSlot(rom, 64);

        assertTrue(trainerIds.isEmpty());
    }

    @Test
    public void frlgRuntimeSourceKnownTagsIncludeRivalRows() {
        assertEquals("RIVAL1-1", Gen3RomHandler.frlgRuntimeTrainerSourceTag(0x146));
        assertEquals("RIVAL2-0", Gen3RomHandler.frlgRuntimeTrainerSourceTag(0x14B));
        assertEquals("RIVAL9-2", Gen3RomHandler.frlgRuntimeTrainerSourceTag(0x2E4));
    }

    @Test
    public void gen3RomLoadDiagnosticsReportsFailingPhaseWithoutRomPath() {
        ThrowingDiagnosticRomHandler romHandler = new ThrowingDiagnosticRomHandler("trainer load");

        Gen3RomHandler.Gen3RomLoadDiagnostics diagnostics = romHandler.loadRomForDiagnostics("<redacted>");

        assertFalse(diagnostics.loaded());
        assertEquals("trainer load", diagnostics.phase());
        assertEquals("ArrayIndexOutOfBoundsException", diagnostics.exceptionClass());
    }

    @Test
    public void gen3RomLoadDiagnosticsReportsTrainerLoadBoundsDetail() {
        ThrowingDiagnosticRomHandler romHandler = new ThrowingDiagnosticRomHandler("trainer load",
                "trainer=329 slot=1 layout=held-item-custom-moves expectedLayout=held-item-custom-moves "
                        + "bytesPerSlot=16 cfruDpeMode=false loadedSpeciesCount=412 loadedMoveCount=642 "
                        + "partyFlags=3 partyCount=2 trainerOffset=in-rom partyPointer=out-of-rom "
                        + "slotOffset=out-of-rom rawSpecies=9999 speciesStatus=out-of-bounds rawItem=13 "
                        + "itemStatus=in-bounds rawMoves=[1, 2, 0, 9999] "
                        + "moveStatus=[in-bounds, in-bounds, none, out-of-bounds]");

        Gen3RomHandler.Gen3RomLoadDiagnostics diagnostics = romHandler.loadRomForDiagnostics("<redacted>");

        assertFalse(diagnostics.loaded());
        assertEquals("trainer load", diagnostics.phase());
        assertEquals("ArrayIndexOutOfBoundsException", diagnostics.exceptionClass());
        assertEquals("trainer=329 slot=1 layout=held-item-custom-moves expectedLayout=held-item-custom-moves "
                + "bytesPerSlot=16 cfruDpeMode=false loadedSpeciesCount=412 loadedMoveCount=642 "
                + "partyFlags=3 partyCount=2 trainerOffset=in-rom partyPointer=out-of-rom "
                + "slotOffset=out-of-rom rawSpecies=9999 speciesStatus=out-of-bounds rawItem=13 "
                + "itemStatus=in-bounds rawMoves=[1, 2, 0, 9999] "
                + "moveStatus=[in-bounds, in-bounds, none, out-of-bounds]", diagnostics.detail());
    }

    @Test
    public void oakLabStarterScriptContainsSeparatePlayerAndRivalStarterSpecies() {
        byte[] rom = new byte[1024];
        writeWord(rom, 64, 1001);
        writeWord(rom, 69, 1002);
        writeWord(rom, 64 + 515, 1003);
        writeWord(rom, 64 + 520, 1004);
        writeWord(rom, 64 + 461, 1005);
        writeWord(rom, 64 + 466, 1006);

        List<Gen3RomHandler.FrlgOakLabStarterScriptSlot> slots =
                Gen3RomHandler.readFrlgOakLabStarterScriptSlots(rom, 64);

        assertEquals(3, slots.size());
        assertEquals(new Gen3RomHandler.FrlgOakLabStarterScriptSlot(0, 64, 1001, 69, 1002), slots.get(0));
        assertEquals(new Gen3RomHandler.FrlgOakLabStarterScriptSlot(1, 579, 1003, 584, 1004), slots.get(1));
        assertEquals(new Gen3RomHandler.FrlgOakLabStarterScriptSlot(2, 525, 1005, 530, 1006), slots.get(2));
    }

    @Test
    public void rawTrainerPartyOffsetCanReadTrainerRowsOutsideLoadedTrainerCount() {
        byte[] rom = new byte[2048];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int trainerId = 26;
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        int partyOffset = 1600;
        rom[trainerOffset + (trainerEntrySize - 8)] = 1;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), partyOffset);

        int firstPokemonOffset = Gen3RomHandler.getFirstRawTrainerPokemonOffset(
                rom, trainerDataOffset, trainerEntrySize, trainerId);

        assertEquals(partyOffset, firstPokemonOffset);
    }

    @Test
    public void rawTrainerPartyOffsetSkipsEmptyOrInvalidRawRows() {
        byte[] rom = new byte[2048];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int emptyTrainerId = 26;
        int invalidPointerTrainerId = 27;
        int invalidTrainerOffset = trainerDataOffset + invalidPointerTrainerId * trainerEntrySize;
        rom[invalidTrainerOffset + (trainerEntrySize - 8)] = 1;

        assertEquals(-1, Gen3RomHandler.getFirstRawTrainerPokemonOffset(
                rom, trainerDataOffset, trainerEntrySize, emptyTrainerId));
        assertEquals(-1, Gen3RomHandler.getFirstRawTrainerPokemonOffset(
                rom, trainerDataOffset, trainerEntrySize, invalidPointerTrainerId));
    }

    @Test
    public void trainerBattleRuntimeSourceMapsScriptTrainerIdToRawTrainerParty() {
        byte[] rom = new byte[2048];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int trainerId = 17;
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        int partyOffset = 1400;
        rom[trainerOffset] = 0;
        rom[trainerOffset + (trainerEntrySize - 8)] = 1;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), partyOffset);
        writeWord(rom, partyOffset + 2, 12);
        writeWord(rom, partyOffset + 4, 321);
        writeTrainerBattle(rom, 64, 0, trainerId, 0);

        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources =
                Gen3RomHandler.findFrlgTrainerBattleRuntimeSources(rom, trainerDataOffset, trainerEntrySize, 0,
                        rom.length);

        assertEquals(1, sources.size());
        Gen3RomHandler.FrlgTrainerBattleRuntimeSource source = sources.get(0);
        assertEquals(64, source.scriptOffset());
        assertEquals(trainerId, source.trainerId());
        assertEquals(trainerOffset, source.trainerOffset());
        assertEquals(partyOffset, source.partyPointer());
        assertTrue(source.trainerEntryValid());
        assertTrue(source.partyPointerValid());
        assertEquals(partyOffset, source.firstPokemonOffset());
        assertEquals(321, source.firstRawSpeciesId());
    }

    @Test
    public void trainerBattleRuntimeSourceKeepsInvalidTrainerRowsVisible() {
        byte[] rom = new byte[512];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int trainerId = 50;
        writeTrainerBattle(rom, 64, 0, trainerId, 0);

        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources =
                Gen3RomHandler.findFrlgTrainerBattleRuntimeSources(rom, trainerDataOffset, trainerEntrySize, 0,
                        rom.length);

        assertEquals(1, sources.size());
        Gen3RomHandler.FrlgTrainerBattleRuntimeSource source = sources.get(0);
        assertEquals(trainerId, source.trainerId());
        assertFalse(source.trainerEntryValid());
        assertFalse(source.partyPointerValid());
        assertEquals(-1, source.firstPokemonOffset());
        assertEquals(-1, source.firstRawSpeciesId());
    }

    @Test
    public void targetedTrainerRuntimeSourceIdsParseExplicitIdsAndKnownGroups() {
        Set<Integer> trainerIds = Gen3OakLabRivalRuntimeSourceRomTest.parseTargetTrainerIds(
                "0x14B, 331 rival2 brock");

        assertEquals(new LinkedHashSet<>(List.of(0x14B, 0x149, 0x14A, 0x19E)), trainerIds);
    }

    @Test
    public void targetedTrainerRuntimeSourceIdsParseGymGroup() {
        Set<Integer> trainerIds = Gen3OakLabRivalRuntimeSourceRomTest.parseTargetTrainerIds("gym");

        assertTrue(trainerIds.contains(0x8E));
        assertTrue(trainerIds.contains(0x19E));
        assertTrue(trainerIds.contains(0x15E));
    }

    @Test
    public void targetedTrainerRuntimeSourceIdsRejectUnknownTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> Gen3OakLabRivalRuntimeSourceRomTest.parseTargetTrainerIds("route22"));
    }

    @Test
    public void targetedTrainerRuntimeSourceFilterKeepsOnlyRequestedTrainerIds() {
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                new Gen3RomHandler.FrlgTrainerBattleRuntimeSource(10, 0, 0x14B, 0,
                        100, 1, 7, 0, 1, 200, true, true, 200, 25),
                new Gen3RomHandler.FrlgTrainerBattleRuntimeSource(20, 0, 0x19E, 0,
                        300, 1, 7, 0, 1, 400, true, true, 400, 74),
                new Gen3RomHandler.FrlgTrainerBattleRuntimeSource(30, 0, 0x120, 0,
                        500, 1, 7, 0, 1, 600, true, true, 600, 16));
        Set<Integer> trainerIds = new LinkedHashSet<>(List.of(0x14B, 0x19E));

        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> filtered =
                Gen3OakLabRivalRuntimeSourceRomTest.filterRuntimeSourcesByTrainerIds(sources, trainerIds);

        assertEquals(2, filtered.size());
        assertEquals(0x14B, filtered.get(0).trainerId());
        assertEquals(0x19E, filtered.get(1).trainerId());
    }

    @Test
    public void trainerRuntimeSourceAuditDedupesValidUnloadedTrainerIds() {
        Species[] species = speciesTable(321);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 321),
                runtimeSource(96, 3, 531, 1200, 1800, 1, 321));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rawParties = List.of(
                rawParty(531, 1200, 1800, 1, 321, 12, species));

        List<Gen3RomHandler.FrlgTrainerRuntimeSourceAuditRow> rows =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourceAuditRows(sources, rawParties, List.of(), species,
                        Gen3RomHandler.FrlgTrainerRuntimeSourceAuditMode.UNLOADED_VALID_PARTIES);

        assertEquals(1, rows.size());
        Gen3RomHandler.FrlgTrainerRuntimeSourceAuditRow row = rows.get(0);
        assertEquals(531, row.trainerId());
        assertEquals(List.of(64, 96), row.scriptOffsets());
        assertEquals(List.of(0, 3), row.battleTypes());
        assertEquals("<not loaded>", row.loadedParty());
        assertTrue(row.rawParty().contains("raw=321"));
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.VALID_RUNTIME_NOT_LOADED,
                row.classification());
    }

    @Test
    public void trainerRuntimeSourceAuditClassifiesLoadedRuntimeMismatch() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                runtimeSource(64, 0, 10, 800, 1600, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rawParties = List.of(
                rawParty(10, 800, 1600, 1, 25, 8, species));
        Trainer loadedTrainer = trainer(10, species[26], 8);

        List<Gen3RomHandler.FrlgTrainerRuntimeSourceAuditRow> rows =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourceAuditRows(sources, rawParties, List.of(loadedTrainer),
                        species, Gen3RomHandler.FrlgTrainerRuntimeSourceAuditMode.LOADED_MISMATCH);

        assertEquals(1, rows.size());
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.LOADED_AND_RUNTIME_MISMATCH,
                rows.get(0).classification());
    }

    @Test
    public void trainerRuntimeSourceAuditClassifiesLoadedRuntimeMatch() {
        Species[] species = speciesTable(25);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                runtimeSource(64, 0, 10, 800, 1600, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rawParties = List.of(
                rawParty(10, 800, 1600, 1, 25, 8, species));
        Trainer loadedTrainer = trainer(10, species[25], 8);

        List<Gen3RomHandler.FrlgTrainerRuntimeSourceAuditRow> rows =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourceAuditRows(sources, rawParties, List.of(loadedTrainer),
                        species, Gen3RomHandler.FrlgTrainerRuntimeSourceAuditMode.ALL);

        assertEquals(1, rows.size());
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.LOADED_AND_RUNTIME_MATCH,
                rows.get(0).classification());
    }

    @Test
    public void trainerRuntimeSourceAuditClassifiesInvalidPointerAndOutOfRange() {
        Species[] species = speciesTable(25);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                runtimeSource(64, 0, 22, 800, 2200, 1, 25),
                new Gen3RomHandler.FrlgTrainerBattleRuntimeSource(96, 0, 900, 0,
                        40000, -1, -1, -1, -1, -1, false, false, -1, -1));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rawParties = List.of(
                unreadableRawParty(22, 800, 2200, 1));

        List<Gen3RomHandler.FrlgTrainerRuntimeSourceAuditRow> rows =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourceAuditRows(sources, rawParties, List.of(), species,
                        Gen3RomHandler.FrlgTrainerRuntimeSourceAuditMode.INVALID);

        assertEquals(2, rows.size());
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.INVALID_POINTER,
                rows.get(0).classification());
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.OUT_OF_RANGE,
                rows.get(1).classification());
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditDetectsChangedRows() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25),
                runtimeSource(96, 3, 531, 1200, 1800, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 26, 7, species));
        Trainer outputLoadedTrainer = trainer(531, species[26], 7);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties,
                        List.of(outputLoadedTrainer), species);

        assertEquals(1, report.summary().totalRuntimeSources());
        assertEquals(1, report.summary().validRuntimeTrainerCount());
        assertEquals(1, report.summary().changedFromBaseCount());
        assertEquals(0, report.summary().unchangedFromBaseCount());
        assertEquals(0, report.summary().outputLoadedRuntimeMismatchCount());
        assertEquals(0, report.summary().outputValidRuntimeNotLoadedCount());
        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow row = report.rows().get(0);
        assertTrue(row.changedFromBase());
        assertEquals("match", row.loadedRawPartyComparison());
        assertEquals("match", row.loadedRawClassPicComparison());
        assertEquals("class=1,pic=7", row.outputRawClassPic());
        assertEquals("class=1,pic=7", row.loadedOutputClassPic());
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.LOADED_AND_RUNTIME_MATCH,
                row.outputClassification());
        assertTrue(row.outputRawParty().contains("raw=26"));
        assertTrue(row.warnings().isEmpty());
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditWarnsUnchangedRows() {
        Species[] species = speciesTable(25);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> sources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species));
        Trainer outputLoadedTrainer = trainer(531, species[25], 7);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        sources, rawParties, sources, rawParties, List.of(outputLoadedTrainer), species);

        assertEquals(0, report.summary().changedFromBaseCount());
        assertEquals(1, report.summary().unchangedFromBaseCount());
        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow row = report.rows().get(0);
        assertFalse(row.changedFromBase());
        assertTrue(row.warnings().contains("WARN unchanged valid runtime trainer"));
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditWarnsLoadedRawMismatch() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 26, 7, species));
        Trainer outputLoadedTrainer = trainer(531, species[25], 7);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties,
                        List.of(outputLoadedTrainer), species);

        assertEquals(1, report.summary().outputLoadedRuntimeMismatchCount());
        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow row = report.rows().get(0);
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.LOADED_AND_RUNTIME_MISMATCH,
                row.outputClassification());
        assertEquals("differs", row.loadedRawPartyComparison());
        assertTrue(row.warnings().contains("WARN loaded/raw mismatch"));
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditWarnsValidNotLoadedAfterStrictSync() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 26, 7, species));

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties, List.of(), species);

        assertEquals(1, report.summary().outputValidRuntimeNotLoadedCount());
        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow row = report.rows().get(0);
        assertEquals(Gen3RomHandler.FrlgTrainerRuntimeSourceClassification.VALID_RUNTIME_NOT_LOADED,
                row.outputClassification());
        assertEquals("unavailable", row.loadedRawPartyComparison());
        assertTrue(row.warnings().contains("WARN valid runtime not loaded after strict sync"));
    }

    @Test
    public void rawTrainerDiagnosticsDecodeCustomMovesAndDetectLeadingNone() {
        Species[] species = speciesTable(25);
        byte[] rom = new byte[256];
        int trainerDataOffset = 32;
        int trainerEntrySize = 40;
        int trainerId = 1;
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        int partyOffset = 160;
        rom[trainerOffset] = 1;
        rom[trainerOffset + (trainerEntrySize - 8)] = 1;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), partyOffset);
        writeWord(rom, partyOffset + 2, 9);
        writeWord(rom, partyOffset + 4, 25);
        writeWord(rom, partyOffset + 6, 0);
        writeWord(rom, partyOffset + 8, 33);
        writeWord(rom, partyOffset + 10, 45);
        writeWord(rom, partyOffset + 12, 28);

        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> rows =
                Gen3RomHandler.readFrlgRawTrainerPartyDiagnostics(rom, trainerDataOffset, trainerEntrySize,
                        List.of(trainerId), species);

        Gen3RomHandler.FrlgRawTrainerPokemonDiagnostics pokemon = rows.get(0).party().get(0);
        assertEquals(List.of(0, 33, 45, 28), pokemon.rawMoves());
        assertTrue(Gen3RomHandler.rawTrainerPokemonHasLeadingNoneMoveForDiagnostics(pokemon));
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditWarnsRawLeadingNoneMoves() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawPartyWithMoves(531, 1200, 1800, 26, 7, species, List.of(0, 33, 45, 28)));
        Trainer outputLoadedTrainer = trainer(531, species[26], 7);
        outputLoadedTrainer.getPokemon().get(0).setMoves(new int[] {0, 33, 45, 28});

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties,
                        List.of(outputLoadedTrainer), species);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow row = report.rows().get(0);
        assertTrue(row.outputRawParty().contains("moves=[0, 33, 45, 28]"));
        assertTrue(row.warnings().stream()
                .anyMatch(warning -> warning.contains("WARN output raw custom moves have leading MOVE_NONE")));
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditWarnsRoute22StarterMismatch() {
        Species[] species = speciesTable(25, 26, 27, 28, 29);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 0x146, 1200, 1800, 1, 25),
                runtimeSource(96, 0, 0x149, 1240, 1840, 2, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(0x146, 1200, 1800, 1, 25, 5, species),
                rawPartyWithSpecies(0x149, 1240, 1840, 2, 9, species, 26, 27));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 0x146, 1200, 1800, 1, 28),
                runtimeSource(96, 0, 0x149, 1240, 1840, 2, 29));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawParty(0x146, 1200, 1800, 1, 28, 5, species),
                rawPartyWithSpecies(0x149, 1240, 1840, 2, 9, species, 29, 27));
        Trainer openingRival = trainer(0x146, species[28], 5);
        Trainer route22Rival = trainer(0x149, species[29], 9);
        route22Rival.getPokemon().add(trainerPokemon(species[27], 9));

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties,
                        List.of(openingRival, route22Rival), species);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditRow route22Row = report.rows().stream()
                .filter(row -> row.trainerId() == 0x149)
                .findFirst()
                .orElseThrow();
        assertTrue(route22Row.warnings().stream()
                .anyMatch(warning -> warning.contains("WARN route22 protected starter differs")));
    }

    @Test
    public void runtimeTrainerPostRandomizationAuditIgnoresInvalidBaseCandidates() {
        Species[] species = speciesTable(25, 26);
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> baseSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 25),
                runtimeSource(96, 0, 532, 1240, 1840, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> baseRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 25, 7, species),
                unreadableRawParty(532, 1240, 1840, 1));
        List<Gen3RomHandler.FrlgTrainerBattleRuntimeSource> outputSources = List.of(
                runtimeSource(64, 0, 531, 1200, 1800, 1, 26));
        List<Gen3RomHandler.FrlgRawTrainerPartyDiagnostics> outputRawParties = List.of(
                rawParty(531, 1200, 1800, 1, 26, 7, species));
        Trainer outputLoadedTrainer = trainer(531, species[26], 7);

        Gen3RomHandler.FrlgTrainerRuntimeSourcePostRandomizationAuditReport report =
                Gen3RomHandler.buildFrlgTrainerRuntimeSourcePostRandomizationAudit(
                        baseSources, baseRawParties, outputSources, outputRawParties,
                        List.of(outputLoadedTrainer), species);

        assertEquals(2, report.summary().totalRuntimeSources());
        assertEquals(1, report.summary().validRuntimeTrainerCount());
        assertEquals(1, report.summary().invalidIgnoredCount());
        assertEquals(531, report.rows().get(0).trainerId());
    }

    @Test
    public void strictRuntimeTrainerSourceDiscoveryLoadsValidUnloadedRowsAndDedupes() {
        byte[] rom = new byte[26000];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int loadedTrainerCount = 256;
        int trainerId = 531;
        writeTrainerDataRow(rom, trainerDataOffset, trainerEntrySize, trainerId, 23000, 2);
        writeTrainerBattle(rom, 64, 0, trainerId, 0);
        writeTrainerBattle(rom, 96, 3, trainerId, 0);

        List<Gen3RomHandler.FrlgRuntimeTrainerSyncTarget> targets =
                Gen3RomHandler.findFrlgRuntimeTrainerDataRowsToLoad(rom, trainerDataOffset, trainerEntrySize,
                        loadedTrainers(loadedTrainerCount), speciesTable(100, 101));

        assertEquals(1, targets.size());
        assertEquals(trainerId, targets.get(0).trainerId());
        assertEquals("RUNTIME-SOURCE", targets.get(0).tag());
    }

    @Test
    public void strictRuntimeTrainerSourceDiscoverySkipsInvalidPointerEmptyPartyAndOversizedParty() {
        byte[] rom = new byte[26000];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int invalidPointerTrainerId = 531;
        int emptyTrainerId = 532;
        int oversizedPartyTrainerId = 533;
        int invalidSpeciesTrainerId = 534;
        int invalidTrainerOffset = trainerDataOffset + invalidPointerTrainerId * trainerEntrySize;
        int emptyTrainerOffset = trainerDataOffset + emptyTrainerId * trainerEntrySize;
        int oversizedTrainerOffset = trainerDataOffset + oversizedPartyTrainerId * trainerEntrySize;
        rom[invalidTrainerOffset + (trainerEntrySize - 8)] = 1;
        rom[emptyTrainerOffset + (trainerEntrySize - 8)] = 0;
        rom[oversizedTrainerOffset + (trainerEntrySize - 8)] = 7;
        writePointer(rom, oversizedTrainerOffset + (trainerEntrySize - 4), 23000);
        writeTrainerDataRowWithSpecies(rom, trainerDataOffset, trainerEntrySize, invalidSpeciesTrainerId,
                23100, 7);
        writeTrainerBattle(rom, 64, 0, invalidPointerTrainerId, 0);
        writeTrainerBattle(rom, 96, 0, emptyTrainerId, 0);
        writeTrainerBattle(rom, 128, 0, oversizedPartyTrainerId, 0);
        writeTrainerBattle(rom, 160, 0, invalidSpeciesTrainerId, 0);

        List<Gen3RomHandler.FrlgRuntimeTrainerSyncTarget> targets =
                Gen3RomHandler.findFrlgRuntimeTrainerDataRowsToLoad(rom, trainerDataOffset, trainerEntrySize,
                        loadedTrainers(256), speciesTable(100, 101));

        assertTrue(targets.isEmpty());
    }

    @Test
    public void strictRuntimeTrainerSourceDiscoveryCoversKnownAndNewValidRuntimeIds() {
        byte[] rom = new byte[36000];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        List<Integer> expectedTrainerIds = List.of(0x14B, 0x149, 0x14A, 0x19E, 531, 532);
        int scriptOffset = 64;
        int partyOffset = 26000;
        for (int trainerId : expectedTrainerIds) {
            writeTrainerDataRow(rom, trainerDataOffset, trainerEntrySize, trainerId, partyOffset, 2);
            writeTrainerBattle(rom, scriptOffset, 0, trainerId, 0);
            scriptOffset += 16;
            partyOffset += 64;
        }

        List<Gen3RomHandler.FrlgRuntimeTrainerSyncTarget> targets =
                Gen3RomHandler.findFrlgRuntimeTrainerDataRowsToLoad(rom, trainerDataOffset, trainerEntrySize,
                        loadedTrainers(256), speciesTable(100, 101));

        assertEquals(expectedTrainerIds, targets.stream().map(Gen3RomHandler.FrlgRuntimeTrainerSyncTarget::trainerId)
                .toList());
        assertEquals("RIVAL2-0", targets.get(0).tag());
        assertEquals("RIVAL2-1", targets.get(1).tag());
        assertEquals("RIVAL2-2", targets.get(2).tag());
        assertEquals("GYM1-LEADER", targets.get(3).tag());
        assertEquals("RUNTIME-SOURCE", targets.get(4).tag());
        assertEquals("RUNTIME-SOURCE", targets.get(5).tag());
    }

    @Test
    public void runtimeTrainerDataRowValidationRequiresFullRawPartyBounds() {
        byte[] rom = new byte[2048];
        int trainerDataOffset = 128;
        int trainerEntrySize = 40;
        int trainerId = 26;
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        rom[trainerOffset] = 1;
        rom[trainerOffset + (trainerEntrySize - 8)] = 2;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), 2024);

        assertFalse(Gen3RomHandler.isValidFrlgRuntimeTrainerDataRow(
                rom, trainerDataOffset, trainerEntrySize, trainerId));

        writePointer(rom, trainerOffset + (trainerEntrySize - 4), 1980);
        assertTrue(Gen3RomHandler.isValidFrlgRuntimeTrainerDataRow(
                rom, trainerDataOffset, trainerEntrySize, trainerId));
    }

    @Test
    public void referenceOakLabSourceUsesRivalStarterScriptVariableBeforeBattle() throws IOException {
        Path oakLabScriptPath = referenceSourcePath("data/maps/PalletTown_ProfessorOaksLab/scripts.inc");
        assumeTrue(Files.isRegularFile(oakLabScriptPath), "pret FireRed reference source is not available");

        String source = Files.readString(oakLabScriptPath);
        assertTrue(source.contains(".equ PLAYER_STARTER_SPECIES,  VAR_TEMP_2"));
        assertTrue(source.contains(".equ RIVAL_STARTER_SPECIES,   VAR_TEMP_3"));
        assertTrue(source.contains("setvar PLAYER_STARTER_SPECIES, SPECIES_BULBASAUR"));
        assertTrue(source.contains("setvar RIVAL_STARTER_SPECIES, SPECIES_CHARMANDER"));
        assertTrue(source.contains("setvar PLAYER_STARTER_SPECIES, SPECIES_SQUIRTLE"));
        assertTrue(source.contains("setvar RIVAL_STARTER_SPECIES, SPECIES_BULBASAUR"));
        assertTrue(source.contains("setvar PLAYER_STARTER_SPECIES, SPECIES_CHARMANDER"));
        assertTrue(source.contains("setvar RIVAL_STARTER_SPECIES, SPECIES_SQUIRTLE"));
        assertTrue(source.contains("trainerbattle_earlyrival TRAINER_RIVAL_OAKS_LAB_CHARMANDER"));
        assertTrue(source.contains("trainerbattle_earlyrival TRAINER_RIVAL_OAKS_LAB_BULBASAUR"));
        assertTrue(source.contains("trainerbattle_earlyrival TRAINER_RIVAL_OAKS_LAB_SQUIRTLE"));
    }

    @Test
    public void cfruSourceKeepsOakTutorialBattleButDoesNotOwnOakLabScript() throws IOException {
        Path overworldSourcePath = cfruSourcePath("src/overworld.c");
        Path configSourcePath = cfruSourcePath("src/config.h");
        Path cfruOakLabScriptPath = cfruSourcePath("assembly/overworld_scripts/PalletTown_ProfessorOaksLab.s");
        assumeTrue(Files.isRegularFile(overworldSourcePath), "CFRU source tree is not available");
        assumeTrue(Files.isRegularFile(configSourcePath), "CFRU source tree is not available");

        String overworldSource = Files.readString(overworldSourcePath);
        String configSource = Files.readString(configSourcePath);
        assertTrue(configSource.contains("#define TUTORIAL_BATTLES"));
        assertTrue(overworldSource.contains("case TRAINER_BATTLE_OAK_TUTORIAL"));
        assertTrue(overworldSource.contains("TrainerBattleLoadArgs(sOakTutorialParams, data)"));
        assertTrue(overworldSource.contains("gBattleTypeFlags |= BATTLE_TYPE_OAK_TUTORIAL"));
        assertFalse(Files.isRegularFile(cfruOakLabScriptPath),
                "If CFRU adds an owned Oak Lab script, the runtime-source diagnosis must inspect it directly.");
    }

    private static void writeOakTutorialTrainerBattle(byte[] rom, int offset, int trainerId) {
        writeTrainerBattle(rom, offset, 0x09, trainerId, 3);
    }

    private static void writeTrainerBattle(byte[] rom, int offset, int battleType, int trainerId, int helperFlags) {
        rom[offset] = 0x5C;
        rom[offset + 1] = (byte) battleType;
        writeWord(rom, offset + 2, trainerId);
        writeWord(rom, offset + 4, helperFlags);
    }

    private static void writeWord(byte[] rom, int offset, int value) {
        rom[offset] = (byte) (value & 0xFF);
        rom[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeTrainerDataRow(byte[] rom, int trainerDataOffset, int trainerEntrySize, int trainerId,
                                            int partyOffset, int partySize) {
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        rom[trainerOffset + (trainerEntrySize - 8)] = (byte) partySize;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), partyOffset);
        for (int i = 0; i < Math.min(partySize, 6); i++) {
            writeWord(rom, partyOffset + i * 8 + 2, 10 + i);
            writeWord(rom, partyOffset + i * 8 + 4, 100 + i);
        }
    }

    private static void writeTrainerDataRowWithSpecies(byte[] rom, int trainerDataOffset, int trainerEntrySize,
                                                       int trainerId, int partyOffset, int rawSpeciesId) {
        int trainerOffset = trainerDataOffset + trainerId * trainerEntrySize;
        rom[trainerOffset + (trainerEntrySize - 8)] = 1;
        writePointer(rom, trainerOffset + (trainerEntrySize - 4), partyOffset);
        writeWord(rom, partyOffset + 2, 10);
        writeWord(rom, partyOffset + 4, rawSpeciesId);
    }

    private static void writePointer(byte[] rom, int offset, int value) {
        int pointer = value + 0x8000000;
        rom[offset] = (byte) (pointer & 0xFF);
        rom[offset + 1] = (byte) ((pointer >>> 8) & 0xFF);
        rom[offset + 2] = (byte) ((pointer >>> 16) & 0xFF);
        rom[offset + 3] = (byte) ((pointer >>> 24) & 0xFF);
    }

    private static Gen3RomHandler.FrlgTrainerBattleRuntimeSource runtimeSource(int scriptOffset, int battleType,
                                                                               int trainerId, int trainerOffset,
                                                                               int partyPointer, int partySize,
                                                                               int firstRawSpeciesId) {
        return new Gen3RomHandler.FrlgTrainerBattleRuntimeSource(scriptOffset, battleType, trainerId, 0,
                trainerOffset, 1, 7, 0, partySize, partyPointer, true, true, partyPointer, firstRawSpeciesId);
    }

    private static Gen3RomHandler.FrlgRawTrainerPartyDiagnostics rawParty(int trainerId, int trainerOffset,
                                                                          int partyPointer, int partySize,
                                                                          int firstRawSpeciesId, int level,
                                                                          Species[] species) {
        String decodedSpecies = species[firstRawSpeciesId].getFullName();
        return new Gen3RomHandler.FrlgRawTrainerPartyDiagnostics(trainerId, trainerOffset, 1, 7, 0, partySize,
                partyPointer, true, List.of(new Gen3RomHandler.FrlgRawTrainerPokemonDiagnostics(0, partyPointer, level,
                firstRawSpeciesId, decodedSpecies, List.of())));
    }

    private static Gen3RomHandler.FrlgRawTrainerPartyDiagnostics rawPartyWithMoves(int trainerId, int trainerOffset,
                                                                                   int partyPointer,
                                                                                   int rawSpeciesId, int level,
                                                                                   Species[] species,
                                                                                   List<Integer> moves) {
        String decodedSpecies = species[rawSpeciesId].getFullName();
        return new Gen3RomHandler.FrlgRawTrainerPartyDiagnostics(trainerId, trainerOffset, 1, 7, 1, 1,
                partyPointer, true, List.of(new Gen3RomHandler.FrlgRawTrainerPokemonDiagnostics(0, partyPointer,
                level, rawSpeciesId, decodedSpecies, moves)));
    }

    private static Gen3RomHandler.FrlgRawTrainerPartyDiagnostics rawPartyWithSpecies(int trainerId,
                                                                                     int trainerOffset,
                                                                                     int partyPointer,
                                                                                     int partySize,
                                                                                     int level,
                                                                                     Species[] species,
                                                                                     int... rawSpeciesIds) {
        List<Gen3RomHandler.FrlgRawTrainerPokemonDiagnostics> party = new ArrayList<>();
        for (int i = 0; i < rawSpeciesIds.length; i++) {
            int rawSpeciesId = rawSpeciesIds[i];
            party.add(new Gen3RomHandler.FrlgRawTrainerPokemonDiagnostics(i, partyPointer + i * 8, level,
                    rawSpeciesId, species[rawSpeciesId].getFullName(), List.of()));
        }
        return new Gen3RomHandler.FrlgRawTrainerPartyDiagnostics(trainerId, trainerOffset, 1, 7, 0, partySize,
                partyPointer, true, party);
    }

    private static Gen3RomHandler.FrlgRawTrainerPartyDiagnostics unreadableRawParty(int trainerId, int trainerOffset,
                                                                                    int partyPointer, int partySize) {
        return new Gen3RomHandler.FrlgRawTrainerPartyDiagnostics(trainerId, trainerOffset, 1, 7, 0, partySize,
                partyPointer, false, List.of());
    }

    private static Trainer trainer(int trainerId, Species species, int level) {
        Trainer trainer = new Trainer();
        trainer.setIndex(trainerId);
        trainer.setTrainerclass(1);
        trainer.setTrainerPic(7);
        trainer.getPokemon().add(trainerPokemon(species, level));
        return trainer;
    }

    private static TrainerPokemon trainerPokemon(Species species, int level) {
        TrainerPokemon pokemon = new TrainerPokemon();
        pokemon.setSpecies(species);
        pokemon.setLevel(level);
        return pokemon;
    }

    private static List<Trainer> loadedTrainers(int loadedTrainerCount) {
        java.util.ArrayList<Trainer> trainers = new java.util.ArrayList<>();
        for (int trainerId = 1; trainerId < loadedTrainerCount; trainerId++) {
            Trainer trainer = new Trainer();
            trainer.setIndex(trainerId);
            trainers.add(trainer);
        }
        return trainers;
    }

    private static Species[] speciesTable(int... rawSpeciesIds) {
        int maxSpeciesId = 0;
        for (int rawSpeciesId : rawSpeciesIds) {
            maxSpeciesId = Math.max(maxSpeciesId, rawSpeciesId);
        }
        Species[] species = new Species[maxSpeciesId + 1];
        for (int rawSpeciesId : rawSpeciesIds) {
            Species entry = new Species(rawSpeciesId);
            entry.setName("Species" + rawSpeciesId);
            entry.setSpeciesSetIdentityNumber(rawSpeciesId);
            species[rawSpeciesId] = entry;
        }
        return species;
    }

    private static Path referenceSourcePath(String relativePath) {
        List<Path> candidates = List.of(
                Path.of("../references/pret-pokefirered", relativePath),
                Path.of("../../references/pret-pokefirered", relativePath),
                Path.of("02_external/references/pret-pokefirered", relativePath));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static Path cfruSourcePath(String relativePath) {
        List<Path> candidates = List.of(
                Path.of("../CFRU-expansion", relativePath),
                Path.of("../../CFRU-expansion", relativePath),
                Path.of("02_external/CFRU-expansion", relativePath));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static class ThrowingDiagnosticRomHandler extends Gen3RomHandler {
        private final String failingPhase;
        private final String trainerLoadDetail;

        ThrowingDiagnosticRomHandler(String failingPhase) {
            this(failingPhase, null);
        }

        ThrowingDiagnosticRomHandler(String failingPhase, String trainerLoadDetail) {
            this.failingPhase = failingPhase;
            this.trainerLoadDetail = trainerLoadDetail;
        }

        @Override
        protected void loadRomFile(String filename) {
            throwIfPhase("detection");
        }

        @Override
        public void midLoadingSetUp() {
            throwIfPhase("setup");
        }

        @Override
        public void loadItems() {
            throwIfPhase("item table load");
        }

        @Override
        public void loadSpeciesStats() {
            throwIfPhase("pokemon data load");
        }

        @Override
        public void loadEvolutions() {
            throwIfPhase("evolution load");
        }

        @Override
        public void loadMoves() {
            throwIfPhase("move table load");
        }

        @Override
        public void loadPokemonPalettes() {
            throwIfPhase("pokemon palette load");
        }

        @Override
        protected void loadTrainersForDiagnostics() {
            if ("trainer load".equals(failingPhase) && trainerLoadDetail != null) {
                throw new Gen3RomHandler.TrainerLoadBoundsException(trainerLoadDetail,
                        new ArrayIndexOutOfBoundsException());
            }
            throwIfPhase("trainer load");
        }

        @Override
        protected void estimateEvolutionLevels() {
            throwIfPhase("evolution-level estimate");
        }

        private void throwIfPhase(String phase) {
            if (phase.equals(failingPhase)) {
                throw new ArrayIndexOutOfBoundsException();
            }
        }
    }
}
