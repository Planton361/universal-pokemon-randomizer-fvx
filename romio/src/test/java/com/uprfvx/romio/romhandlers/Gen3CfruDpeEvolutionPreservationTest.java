package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.HexFormat;
import java.util.Set;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpeEvolutionPreservationTest {
    private static final int ROW_SIZE = 16 * 8;

    @Test
    void unchangedPreservesFroslassAndMixedSlotOrderAcrossRepeatedWritesAndReloads() throws Exception {
        Fixture f = fixture(true, 531);
        // Exact pinned DPE identities: Snorunt -> Glalie / Froslass, Dawn Stone = 101.
        entry(f, 346, 0, 4, 42, 347, 0);
        entry(f, 346, 1, 7, 101, 531, 0xFE);
        entry(f, 346, 3, 23, 28, 105, 0x1234);
        entry(f, 346, 15, 0xFFFF, 0xFFFF, 531, 0xABCD);
        // Auxiliary data is unowned even on an otherwise ordinary method.
        entry(f, 104, 0, 4, 28, 105, 0x5678);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(2, f.species()[346].getEvolutionsFrom().size());
        assertEquals(531, f.species()[346].getEvolutionsFrom().get(1).getTo().getSpeciesSetIdentityNumber());
        write(f);
        write(f);
        f.handler().loadEvolutions();
        write(f);
        assertArrayEquals(before, f.bytes());
        assertEquals(0xFE, word(f, 346, 1, 6));
        assertEquals(0x5678, word(f, 104, 0, 6));
        assertEquals(2, f.species()[346].getEvolutionsFrom().size());
    }

    @Test
    void all272CurrentOpaqueMethodRowsSurviveUnchanged() throws Exception {
        // Exact method histogram from DPE d887185... Evolution Table.c (#636):
        // 79 extended ordinary rows + 89 Gigantamax + 104 Mega = 272.
        int[][] domain = {
            {16,2}, {17,1}, {18,1}, {19,7}, {20,5}, {21,7}, {22,8}, {23,6},
            {24,2}, {25,2}, {26,18}, {27,1}, {28,1}, {30,1}, {31,1}, {32,1},
            {34,1}, {35,6}, {36,1}, {37,1}, {38,1}, {39,1}, {40,2}, {41,1},
            {42,1}, {253,89}, {254,104}
        };
        Fixture f = fixture(true, 19);
        int count = 0;
        for (int[] method : domain) {
            for (int i = 0; i < method[1]; i++) {
                // Synthetic, deterministic, varied full-width parameters/auxiliary fields.
                entry(f, 1 + count / 16, count % 16, method[0],
                        (count * 257) & 0xFFFF, 19, (0xFE + count * 509) & 0xFFFF);
                count++;
            }
        }
        assertEquals(272, count);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        for (Species s : f.species()) {
            if (s != null) assertTrue(s.getEvolutionsFrom().isEmpty());
        }
        write(f);
        f.handler().loadEvolutions();
        write(f);
        assertArrayEquals(before, f.bytes(), "all 272 encoded slots, including their raw fields and order");
    }

    @Test
    void allSupportedMethodsRemainModeledAndWritable() throws Exception {
        Fixture f = fixture(true, 2);
        for (int method = 1; method <= 15; method++) entry(f, 1, method - 1, method, 30, 2, 0);
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(15, f.species()[1].getEvolutionsFrom().size());
        write(f);
        f.handler().loadEvolutions();
        assertEquals(15, f.species()[1].getEvolutionsFrom().size());
        assertArrayEquals(before, f.bytes());
        for (Evolution evo : f.species()[1].getEvolutionsFrom()) {
            evo.updateEvolutionMethod(evo.getType(), 31);
        }
        write(f);
        f.handler().loadEvolutions();
        for (Evolution evo : f.species()[1].getEvolutionsFrom()) assertEquals(31, evo.getExtraInfo());
    }

    @Test
    void holesDuplicateRelationshipsInvalidTargetsAndEmptySlotMetadataArePreserved() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 1, 2, 4, 16, 2, 0); // loader deduplicates; raw writer must not
        entry(f, 1, 4, 4, 20, 3, 0); // valid numeric target but null species
        entry(f, 1, 5, 4, 20, 1441, 0); // out-of-domain target
        entry(f, 1, 7, 0, 0xBEEF, 0, 0x1234); // method-zero metadata
        entry(f, 1, 10, 0x8000, 0xABCD, 0, 0xFEDC); // unknown encoding
        byte[] before = f.bytes().clone();
        f.handler().loadEvolutions();
        assertEquals(1, f.species()[1].getEvolutionsFrom().size());
        write(f);
        f.handler().loadEvolutions();
        assertArrayEquals(before, f.bytes());
        assertEquals(1, f.species()[1].getEvolutionsFrom().size());
    }

    @Test
    void supportedMutationAndListReplacementWorkOnFullyModeledRows() throws Exception {
        Fixture f = fixture(true, 3);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 2, 0, 5, 0, 3, 0);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 32);
        // Use the existing impossible-evolution decision, not a test-only mutation model.
        assertTrue(Gen3RomHandler.updateImpossibleEvolution(f.species()[2].getEvolutionsFrom().getFirst(), true, false));
        write(f);
        write(f); // successful writes refresh snapshots for repeated saves
        assertEquals(32, word(f, 1, 0, 2));
        assertEquals(4, word(f, 2, 0, 0));
        assertEquals(37, word(f, 2, 0, 2));
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().clear();
        f.species()[1].getEvolutionsFrom().add(new Evolution(f.species()[1], f.species()[3], EvolutionType.LEVEL, 40));
        f.species()[1].getEvolutionsFrom().add(new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL, 45));
        write(f);
        f.handler().loadEvolutions();
        assertEquals(2, f.species()[1].getEvolutionsFrom().size());
        assertEquals(3, word(f, 1, 0, 4));
        assertEquals(2, word(f, 1, 1, 4));
        f.species()[1].getEvolutionsFrom().clear();
        write(f);
        for (int i = ROW_SIZE; i < 2 * ROW_SIZE; i++) assertEquals(0, f.bytes()[i]);
    }

    @Test
    void unrelatedOpaqueSpeciesDoesNotDisableSupportedMutation() throws Exception {
        Fixture f = fixture(true, 3);
        entry(f, 1, 0, 4, 16, 2, 0);
        entry(f, 2, 7, 254, 0x1234, 3, 0xABCD);
        byte[] opaque = Arrays.copyOfRange(f.bytes(), 2 * ROW_SIZE, 3 * ROW_SIZE);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
        write(f);
        assertEquals(20, word(f, 1, 0, 2));
        assertArrayEquals(opaque, Arrays.copyOfRange(f.bytes(), 2 * ROW_SIZE, 3 * ROW_SIZE));
    }

    @Test
    void unsafeMutationFailsBeforeAnySpeciesStatsOrEvolutionWrites() throws Exception {
        for (boolean auxiliaryOnly : List.of(false, true)) {
            Fixture f = fixture(true, 3);
            entry(f, 1, 0, 4, 16, 2, 0); // safe row processed before unsafe row
            entry(f, 2, 0, 4, 30, 3, auxiliaryOnly ? 0xFE : 0);
            if (!auxiliaryOnly) entry(f, 2, 1, 23, 28, 3, 0);
            f.handler().loadEvolutions();
            f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
            f.species()[2].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 40);
            byte[] before = f.bytes().clone();
            RomIOException error = assertThrows(RomIOException.class, f.handler()::saveSpeciesStats);
            assertTrue(error.getMessage().contains("Unchanged"));
            assertArrayEquals(before, f.bytes(), "preflight before names/stats as well as earlier safe rows");
            assertThrows(RomIOException.class, () -> write(f));
            assertArrayEquals(before, f.bytes());
            Method prepare = AbstractRomHandler.class.getDeclaredMethod("prepareSaveRom");
            assertThrows(RomIOException.class, () -> invoke(prepare, f.handler()));
            assertArrayEquals(before, f.bytes(), "ordinary save preparation must reject before any output writer");
        }
    }

    @Test
    void removalAdditionReorderingAndTargetChangesCannotRepackOpaqueRows() throws Exception {
        for (int operation = 0; operation < 4; operation++) {
            Fixture f = fixture(true, 3);
            entry(f, 1, 0, 4, 16, 2, 0);
            entry(f, 1, 1, 4, 30, 3, 0);
            entry(f, 1, 4, 254, 10, 3, 0x1234);
            f.handler().loadEvolutions();
            List<Evolution> evolutions = f.species()[1].getEvolutionsFrom();
            switch (operation) {
                case 0 -> evolutions.removeFirst();
                case 1 -> evolutions.add(new Evolution(f.species()[1], f.species()[2], EvolutionType.TRADE, 0));
                case 2 -> java.util.Collections.reverse(evolutions);
                case 3 -> evolutions.getFirst().setTo(f.species()[3]);
            }
            byte[] before = f.bytes().clone();
            assertThrows(RomIOException.class, () -> write(f));
            assertArrayEquals(before, f.bytes());
        }
    }

    @Test
    void equivalentEvolutionCopiesAndEstimatedLevelChangesAreNotRawMutations() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0xFE);
        f.handler().loadEvolutions();
        Evolution copy = new Evolution(f.species()[1].getEvolutionsFrom().getFirst());
        copy.setEstimatedEvoLvl(99); // randomizer-only bookkeeping
        f.species()[1].getEvolutionsFrom().set(0, copy);
        byte[] before = f.bytes().clone();
        write(f);
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void unsupportedMethodCapacityAndMissingSnapshotFailClosed() throws Exception {
        Fixture f = fixture(true, 2);
        byte[] before = f.bytes().clone();
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
        f.handler().loadEvolutions();
        for (int i = 0; i < 17; i++) f.species()[1].getEvolutionsFrom().add(
                new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL, i));
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
        f.species()[1].getEvolutionsFrom().clear();
        f.species()[1].getEvolutionsFrom().add(
                new Evolution(f.species()[1], f.species()[2], EvolutionType.LEVEL_FEMALE_ONLY, 20));
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void rawChangesOutsideTheWriterFailWithoutOverwritingThem() throws Exception {
        Fixture f = fixture(true, 2);
        entry(f, 1, 0, 4, 16, 2, 0);
        f.handler().loadEvolutions();
        entry(f, 1, 1, 254, 10, 2, 0xFE);
        byte[] before = f.bytes().clone();
        assertThrows(RomIOException.class, () -> write(f));
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void vanillaFiveSlotWriterRetainsItsExistingBehavior() throws Exception {
        Fixture f = fixture(false, 3);
        entry(f, 1, 0, 4, 16, 2, 0xFE);
        entry(f, 1, 1, 23, 28, 3, 0);
        f.handler().loadEvolutions();
        f.species()[1].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 20);
        write(f);
        assertEquals(5, f.handler().getEvolutionSlotsPerSpeciesForDiagnostics());
        assertEquals(20, word(f, 1, 0, 2));
        assertEquals(0, word(f, 1, 0, 6));
        assertEquals(0, word(f, 1, 1, 0));
    }

    /** Entire witness and input are constructed in memory. No filesystem ingress in source tests. */
    private static class WitnessFixture extends CfruDpeEvolutionFixture {
        static final int RECORD = 0x48200;
        final Properties witness = new Properties();
        int failAt = -1, writes;
        boolean corruptReadback;

        WitnessFixture() throws Exception { super(); }

        void attest(int threshold) throws Exception {
            memory[0xAC] = 'B'; memory[0xAD] = 'P'; memory[0xAE] = 'R'; memory[0xAF] = 'E';
            memory[0xBC] = 0;
            memory[0x42EC4] = 0; memory[0x42EC5] = 0x4B; memory[0x42EC6] = 0x18; memory[0x42EC7] = 0x47;
            fullWord(0x42EC8, 0x08048001); fullWord(0x42F6C, 0x08000100);
            System.arraycopy("CFRUEVO1".getBytes(StandardCharsets.US_ASCII), 0, memory, RECORD, 8);
            fullWord(RECORD + 8, 0x08000000 + RECORD); fullWord(RECORD + 12, 0x08048001);
            int[] fields = {1, 32, 28, 1, 220, 160};
            for (int i = 0; i < fields.length; i++) super.writeWord(RECORD + 16 + i * 2, fields[i]);
            memory[RECORD + 28] = (byte) threshold;
            witness.setProperty("schema", "OWNERSHIP_WITNESS_V1"); witness.setProperty("version", "1");
            witness.setProperty("cfru.sha", "958c30ec58919ac3e13a40ddb9bd94a86651636e");
            witness.setProperty("dpe.sha", "d887185de1f6ae6a78e85c4311bbadde17041d00");
            witness.setProperty("build.id", "synthetic-build"); witness.setProperty("config.id", "synthetic-config");
            witness.setProperty("config.sha256", "0".repeat(64));
            witness.setProperty("input.size", Integer.toString(memory.length));
            witness.setProperty("input.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(memory)));
            witness.setProperty("record.offset", "0x48200");
            witness.setProperty("table.rows", "1440"); witness.setProperty("table.slots", "16"); witness.setProperty("table.entryBytes", "8");
            interval("consumer", 0x48000, 0x48100); interval("table", EVOLUTION_BASE, EVOLUTION_BASE + 1440 * 128);
            witness.setProperty("insertions.count", "2");
            interval("insertions.0", 0x100, 0x2E000); interval("insertions.1", 0x48000, 0x4C000);
            witness.setProperty("protected.count", "4");
            String[] types = {"PICKUP_CODE", "PICKUP_DATA", "OTHER_CODE", "OTHER_DATA"};
            for (int i = 0; i < 4; i++) {
                witness.setProperty("protected." + i + ".type", types[i]);
                interval("protected." + i, 0x49000 + i * 0x100, 0x49080 + i * 0x100);
            }
            setField("originalRom", memory.clone());
            loadEvolutions();
        }
        void interval(String prefix, int start, int end) {
            witness.setProperty(prefix + ".start", Integer.toString(start)); witness.setProperty(prefix + ".end", Integer.toString(end));
        }
        void bind() { acceptSyntheticEvolutionWitness(witness); }
        void fullWord(int offset, int value) {
            for (int i = 0; i < 4; i++) memory[offset + i] = (byte) (value >>> (8 * i));
        }
        @Override protected void writeWord(int offset, int value) {
            super.writeWord(offset, value);
            if (++writes == failAt) throw new RomIOException("synthetic write failure");
            if (corruptReadback) memory[offset] ^= 1;
        }
    }

    @Test
    void witnessLevelPlanCoversEverySourceLevelMethodAndPreservesAllOtherFields() throws Exception {
        var f = new WitnessFixture();
        int[] methods = {4, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 21, 22, 23, 28, 31, 32, 35, 41, 42};
        for (int i = 0; i < methods.length; i++) f.entry(1 + i / 16, i % 16, methods[i], 80, 3, 0x1234 + i);
        f.entry(3, 0, 35, 70, 4, 0xBEEF); // raw extended outgoing edge makes species 3 intermediate
        f.entry(4, 1, 40, 2999, 5, 0); // coin row also counts as a next evolution
        f.entry(5, 0, 14, 20, 6, 0); // Shedinja minimum-level parameter is read by GetMinimumLevel
        f.entry(5, 1, 17, 18, 7, 1); // Sylveon move type + friendship flag
        f.entry(5, 4, 0, 0xBEEF, 0, 0xFE);
        f.entry(5, 8, 0xFE, 0x1234, 8, 3); f.entry(5, 9, 0xFD, 1, 9, 0);
        f.entry(6, 2, 4, 15, 7, 0); f.entry(6, 3, 4, 75, 7, 0); f.entry(6, 4, 4, 75, 7, 0);
        f.attest(220); f.bind();
        byte[] before = f.memory.clone();
        f.condenseLevelEvolutions(40); f.makeEvolutionsEasier(false, false);
        Set<Integer> permitted = new HashSet<>(); permitted.add(WitnessFixture.RECORD + 28);
        for (int i = 0; i < methods.length; i++) {
            assertEquals(30, f.word(1 + i / 16, i % 16, 2));
            assertEquals(methods[i], f.word(1 + i / 16, i % 16, 0));
            assertEquals(0x1234 + i, f.word(1 + i / 16, i % 16, 6));
            int o = CfruDpeEvolutionFixture.offset(1 + i / 16, i % 16) + 2; permitted.add(o); permitted.add(o + 1);
        }
        for (int[] slot : new int[][]{{3,0}, {6,3}, {6,4}}) {
            int o = CfruDpeEvolutionFixture.offset(slot[0],slot[1]) + 2; permitted.add(o); permitted.add(o + 1);
        }
        assertEquals(30, f.word(3, 0, 2)); assertEquals(40, f.word(6, 3, 2)); assertEquals(40, f.word(6, 4, 2));
        assertEquals(15, f.word(6, 2, 2)); assertEquals(2999, f.word(4, 1, 2));
        assertEquals(160, f.memory[WitnessFixture.RECORD + 28] & 255);
        for (int i = 0; i < before.length; i++) if (!permitted.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
        byte[] after = f.memory.clone(); f.write(); f.write(); f.loadEvolutions(); f.write();
        assertArrayEquals(after, f.memory);
        f.condenseLevelEvolutions(40); assertArrayEquals(after, f.memory);
    }

    @Test
    void exact1440By16InventoryCapsAllApplicableRowsAndRetains785SlotsAnd22255Blanks() throws Exception {
        var f = new WitnessFixture(); f.populateExactSource(); f.attest(220); f.bind();
        byte[] before = f.memory.clone(); f.condenseLevelEvolutions(20);
        Set<Integer> methods = Set.of(4,8,9,10,11,12,13,14,16,18,20,21,22,23,28,31,32,35,41,42);
        Set<Integer> outgoing = new HashSet<>();
        CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.method() >= 1 && s.method() <= 42).forEach(s -> outgoing.add(s.source()));
        int capped = 0, empty = 0;
        Set<Integer> allowed = new HashSet<>(); allowed.add(WitnessFixture.RECORD + 28);
        for (var slot : CfruDpeEvolutionFixture.inventory()) {
            int o = CfruDpeEvolutionFixture.offset(slot.source(), slot.slot());
            if (methods.contains(slot.method())) {
                int expected = Math.min(slot.parameter(), outgoing.contains(slot.target()) ? 15 : 20);
                assertEquals(expected, f.word(slot.source(), slot.slot(), 2), slot.toString());
                if (expected != slot.parameter()) capped++;
                allowed.add(o + 2); allowed.add(o + 3);
            }
        }
        assertEquals(785, CfruDpeEvolutionFixture.inventory().size()); assertEquals(333,capped);
        for (int id = 0; id < 1440; id++) for (int slot = 0; slot < 16; slot++) if (f.word(id,slot,0) == 0) empty++;
        assertEquals(22255, empty);
        for (int i = 0; i < before.length; i++) if (!allowed.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
        assertEquals(2999, f.word(0x574,0,2)); assertEquals(2999, f.word(0x575,0,2));
        assertEquals(20, f.word(0x521,0,2)); assertEquals(20, f.word(0x521,1,2));
        assertEquals(771, f.word(0x22B,1,6)); // Dewott's method-35 held-item owner
        byte[] saved = f.memory.clone(); f.write(); f.loadEvolutions(); f.write(); assertArrayEquals(saved, f.memory);
    }

    @Test
    void missingOptInRejectsBeforeBytesOrGraphChangeAndUnchangedNeverRequiresWitness() throws Exception {
        String previous = System.getProperty(Gen3RomHandler.CFRU_EVOLUTION_OWNERSHIP_PROPERTY);
        System.clearProperty(Gen3RomHandler.CFRU_EVOLUTION_OWNERSHIP_PROPERTY);
        try {
            var f = new WitnessFixture(); f.entry(1,0,4,80,2,0); f.attest(220);
            byte[] before = f.memory.clone();
            assertThrows(RomIOException.class, () -> f.preflightCfruEvolutionEasier(40));
            assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40));
            assertThrows(RomIOException.class, () -> f.makeEvolutionsEasier(false,false));
            assertArrayEquals(before,f.memory); assertEquals(80,f.species[1].getEvolutionsFrom().getFirst().getExtraInfo());
            f.write(); assertArrayEquals(before,f.memory);
        } finally {
            if (previous == null) System.clearProperty(Gen3RomHandler.CFRU_EVOLUTION_OWNERSHIP_PROPERTY);
            else System.setProperty(Gen3RomHandler.CFRU_EVOLUTION_OWNERSHIP_PROPERTY,previous);
        }
    }

    @Test
    void strictWitnessRejectsSchemaRevisionConfigLayoutBoundsTypesAndUnknownKeys() throws Exception {
        String[][] invalid = {{"schema","SELF_TRUSTED"}, {"version","2"}, {"cfru.sha","0".repeat(40)},
                {"dpe.sha","0".repeat(40)}, {"config.id",""}, {"build.id","unattested build"}, {"config.sha256","unknown"},
                {"table.rows","1439"}, {"table.slots","5"}, {"table.entryBytes","6"}, {"record.offset","0x48202"},
                {"consumer.start","0x48001"}, {"consumer.end","0x50002"}, {"consumer.end","0x48000"},
                {"insertions.1.end","0x48100"}, {"insertions.0.end","0xFFFFffff"}, {"input.size","-1"},
                {"protected.count","0"}, {"protected.0.type","TRUSTED"}, {"unknown","true"}};
        for (String[] pair : invalid) {
            var f = new WitnessFixture(); f.attest(220); f.witness.setProperty(pair[0],pair[1]);
            byte[] before = f.memory.clone(); assertThrows(RomIOException.class, f::bind, Arrays.toString(pair)); assertArrayEquals(before,f.memory);
        }
        var f = new WitnessFixture(); f.attest(220); f.witness.remove("protected.3.end"); assertThrows(RomIOException.class,f::bind);
    }

    @Test
    void typedOwnerOverlapsRejectEvenWhenRecordIsContainedInEnvelope() throws Exception {
        for (String owner : List.of("consumer", "table", "protected.0", "protected.1", "protected.2", "protected.3")) {
            var f = new WitnessFixture(); f.attest(220);
            f.interval(owner, WitnessFixture.RECORD, owner.equals("table") ? WitnessFixture.RECORD + 1440 * 128 : WitnessFixture.RECORD + 32);
            assertThrows(RomIOException.class,f::bind,owner);
        }
        var f = new WitnessFixture(); f.attest(220); f.interval("protected.1",0x49000,0x49080); assertThrows(RomIOException.class,f::bind);
        f.attest(220); f.interval("insertions.0",0x100,0x4B000); assertThrows(RomIOException.class,f::bind);
    }

    @Test
    void originalIdentityMismatchCannotBeBypassedByMutatedCurrentRomDigest() throws Exception {
        var f = new WitnessFixture(); f.attest(220); f.memory[0x4F000] = 1;
        f.witness.setProperty("input.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(f.memory)));
        assertThrows(RomIOException.class,f::bind);
        var g = new WitnessFixture(); g.attest(220); g.witness.setProperty("input.size","327679"); assertThrows(RomIOException.class,g::bind);
        var h = new WitnessFixture(); h.attest(220); h.memory[CfruDpeEvolutionFixture.offset(1,0)] = 4; h.loadEvolutions();
        assertThrows(RomIOException.class,h::bind,"a changed table cannot be hidden by reloading snapshots");
    }

    @Test
    void everyHookRecordFieldReservedByteAndThresholdIsCheckedBeforeMutation() throws Exception {
        int[] corrupt = {0x42EC4,0x42EC5,0x42EC6,0x42EC7,0x42EC8,0x42EC9,0x42F6C,
                WitnessFixture.RECORD, WitnessFixture.RECORD+8, WitnessFixture.RECORD+12,
                WitnessFixture.RECORD+16,WitnessFixture.RECORD+18,WitnessFixture.RECORD+20,
                WitnessFixture.RECORD+22,WitnessFixture.RECORD+24,WitnessFixture.RECORD+26,
                WitnessFixture.RECORD+28,WitnessFixture.RECORD+29,WitnessFixture.RECORD+30,WitnessFixture.RECORD+31};
        for (int offset : corrupt) {
            var f = new WitnessFixture(); f.entry(1,0,4,80,2,0); f.attest(220); f.bind(); f.memory[offset] ^= 1;
            byte[] before = f.memory.clone(); assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40));
            assertArrayEquals(before,f.memory); assertEquals(80,f.species[1].getEvolutionsFrom().getFirst().getExtraInfo());
        }
        for (int threshold : new int[]{0,159,161,219,221,255}) {
            var f = new WitnessFixture(); f.attest(threshold); assertThrows(RomIOException.class,f::bind);
        }
    }

    @Test
    void valid160IsIdempotentAndWitnessSnapshotIsImmutable() throws Exception {
        var f = new WitnessFixture(); f.attest(160); f.bind();
        var immutable = (Gen3RomHandler.EvolutionOwnershipWitness) fieldValue(f,"evolutionOwnershipWitness");
        f.witness.setProperty("record.offset","0"); f.witness.setProperty("config.id","changed-config");
        assertEquals("synthetic-config",immutable.configId); assertEquals("synthetic-build",immutable.buildId);
        assertEquals("0".repeat(64),immutable.configSha256);
        byte[] before = f.memory.clone(); f.condenseLevelEvolutions(100); f.makeEvolutionsEasier(false,false);
        assertArrayEquals(before,f.memory);
    }

    @Test
    void duplicateRecordOutOfConsumerPointerAndChangedConsumerCodeRejectAtomically() throws Exception {
        var f = new WitnessFixture(); f.attest(220); f.bind();
        System.arraycopy(f.memory,WitnessFixture.RECORD,f.memory,0x48300,32); f.fullWord(0x48308,0x08048300);
        byte[] before = f.memory.clone(); assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40)); assertArrayEquals(before,f.memory);
        var g = new WitnessFixture(); g.attest(220); g.bind(); g.fullWord(0x42EC8,0x08048501); g.fullWord(WitnessFixture.RECORD+12,0x08048501);
        assertThrows(RomIOException.class, () -> g.condenseLevelEvolutions(40));
        var h = new WitnessFixture(); h.attest(220); h.bind(); h.memory[0x480FE] = 1;
        assertThrows(RomIOException.class, () -> h.condenseLevelEvolutions(40));
    }

    @Test
    void unknownApplicableMethodAndInvalidLevelRejectBeforeAllWritesAndGraphUpdates() throws Exception {
        for (int[] trigger : new int[][]{{43,80,3},{33,0,3},{4,101,3},{41,25,1440}}) {
            var f = new WitnessFixture(); f.entry(1,0,4,80,2,0); f.entry(2,15,trigger[0],trigger[1],trigger[2],0);
            f.attest(220); f.bind(); byte[] before = f.memory.clone();
            assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40)); assertArrayEquals(before,f.memory);
            assertEquals(80,f.species[1].getEvolutionsFrom().getFirst().getExtraInfo());
        }
    }

    @Test
    void lateStaleRawOrGraphPlanRejectsWithoutFriendshipOrEarlierRowWrites() throws Exception {
        for (boolean graph : List.of(false,true)) {
            var f = new WitnessFixture(); f.entry(1,0,4,80,2,0); f.entry(2,0,35,75,3,17); f.attest(220); f.bind();
            if (graph) f.species[1].getEvolutionsFrom().getFirst().setTo(f.species[4]);
            else f.memory[CfruDpeEvolutionFixture.offset(2,0)+6] ^= 1;
            byte[] before = f.memory.clone(); assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40)); assertArrayEquals(before,f.memory);
        }
    }

    @Test
    void writeFailureAndReadbackCorruptionRestoreAllTouchedBytesAndLeaveGraphAndPlansRetryable() throws Exception {
        for (boolean readback : List.of(false,true)) {
            var f = new WitnessFixture(); f.entry(1,0,4,80,2,0); f.entry(2,0,35,75,3,17); f.attest(220); f.bind();
            byte[] before = f.memory.clone(); f.writes = 0; f.failAt = readback ? -1 : 2; f.corruptReadback = readback;
            assertThrows(RomIOException.class, () -> f.condenseLevelEvolutions(40)); assertArrayEquals(before,f.memory);
            assertEquals(80,f.species[1].getEvolutionsFrom().getFirst().getExtraInfo()); f.failAt = -1; f.corruptReadback = false;
            f.write(); assertArrayEquals(before,f.memory);
            f.condenseLevelEvolutions(40); assertEquals(30,f.word(1,0,2)); assertEquals(40,f.word(2,0,2));
            assertEquals(160,f.memory[WitnessFixture.RECORD+28] & 255);
        }
    }

    @Test
    void inMemoryPropertiesReaderRejectsDuplicateKeysMalformedEscapesAndOversize() throws Exception {
        for (String input : List.of("schema=x\nschema=y\n", "schema=\\uZZZZ\n", "a".repeat(65537))) {
            assertThrows(RomIOException.class, () -> Gen3RomHandler.EvolutionOwnershipWitness.read(
                    new ByteArrayInputStream(input.getBytes(StandardCharsets.ISO_8859_1))));
        }
        var f = new WitnessFixture(); f.attest(220);
        java.io.ByteArrayOutputStream serialized = new java.io.ByteArrayOutputStream(); f.witness.store(serialized,null);
        assertNotNull(Gen3RomHandler.EvolutionOwnershipWitness.read(new ByteArrayInputStream(serialized.toByteArray())));
    }

    @Test
    void unusedEmptyLoaderRowsArePreservedButUnloadedActiveRowsReject() throws Exception {
        var f = new WitnessFixture(); f.attest(220);
        @SuppressWarnings("unchecked")
        var rows = (java.util.Map<Integer, ?>) fieldValue(f, "originalCfruDpeEvolutionRows");
        rows.remove(10);
        @SuppressWarnings("unchecked")
        var loaded = (List<Species>) fieldValue(f, "speciesList");
        loaded.remove(10); f.setField("numRealPokemon",1438);
        f.bind(); f.condenseLevelEvolutions(40);
        var g = new WitnessFixture(); g.entry(10,0,4,70,11,0); g.attest(220);
        @SuppressWarnings("unchecked")
        var active = (java.util.Map<Integer, ?>) fieldValue(g, "originalCfruDpeEvolutionRows");
        active.remove(10); byte[] before = g.memory.clone(); assertThrows(RomIOException.class,g::bind); assertArrayEquals(before,g.memory);
    }

    @Test
    void vanillaEasierUsesExistingLevelAndFriendshipPathWithoutAnyWitness() throws Exception {
        Fixture f = fixture(false,3); entry(f,1,0,4,80,2,0); entry(f,2,0,4,70,3,0);
        f.handler().loadEvolutions(); setField(f.handler(),"highestEvoLvl",80);
        f.handler().preflightCfruEvolutionEasier(40); f.handler().condenseLevelEvolutions(40);
        f.handler().makeEvolutionsEasier(false,false); write(f);
        assertEquals(30,word(f,1,0,2)); assertEquals(40,word(f,2,0,2)); assertEquals(40,f.handler().getHighestEvoLvl());
    }

    @Test
    void cfruIntermediateCondensationRetainsHighestLevelGateAndRoundsUp() throws Exception {
        var f = new WitnessFixture(); f.entry(1,0,4,76,2,0); f.entry(2,0,35,70,3,17); f.attest(220); f.bind();
        f.condenseLevelEvolutions(76); assertEquals(76,f.word(1,0,2)); assertEquals(76,f.getHighestEvoLvl());
        f.condenseLevelEvolutions(41); assertEquals(31,f.word(1,0,2)); assertEquals(41,f.word(2,0,2));
        assertEquals(41,f.getHighestEvoLvl());
    }

    @Test
    void pairedNincadaShedinjaLevelParametersRemainCoherentAtLowCap() throws Exception {
        var f = new WitnessFixture(); f.entry(301,0,13,20,302,0); f.entry(301,1,14,20,303,0);
        f.attest(220); f.bind(); f.condenseLevelEvolutions(10);
        assertEquals(10,f.word(301,0,2)); assertEquals(10,f.word(301,1,2));
        assertEquals(13,f.word(301,0,0)); assertEquals(14,f.word(301,1,0));
        assertEquals(302,f.word(301,0,4)); assertEquals(303,f.word(301,1,4));
        f.write(); f.loadEvolutions();
        assertEquals(10,f.species[301].getEvolutionsFrom().get(0).getExtraInfo());
        assertEquals(10,f.species[301].getEvolutionsFrom().get(1).getExtraInfo());
    }

    private static Object fieldValue(Object target,String name) throws Exception {
        Field field = Gen3RomHandler.class.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private record Fixture(Gen3RomHandler handler, byte[] bytes, Species[] species, int rowSize) {}

    private static Fixture fixture(boolean cfru, int count) throws Exception {
        Gen3RomHandler h = new Gen3RomHandler();
        Gen3RomEntry e = new Gen3RomEntry(Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        e.setRomCode("BPRE");
        e.putIntValue("PokemonCount", cfru ? 1440 : count);
        e.putIntValue("PokemonEvolutions", 0);
        int rowSize = cfru ? ROW_SIZE : 5 * 8;
        byte[] bytes = new byte[(count + 2) * rowSize];
        Arrays.fill(bytes, (count + 1) * rowSize, bytes.length, (byte) 0x5A); // neighbor canary
        Species[] species = new Species[(cfru ? 1440 : count) + 1];
        List<Species> list = new ArrayList<>();
        list.add(null);
        int[] mapping = new int[count + 1];
        for (int i = 1; i <= count; i++) {
            Species s = new Species(i);
            s.setName("Species" + i);
            s.setSpeciesSetIdentityNumber(i);
            species[i] = s;
            list.add(s);
            mapping[i] = i;
        }
        setField(h, "rom", bytes);
        setField(h, "romEntry", e);
        setField(h, "isRomHack", cfru);
        setField(h, "useCfruDpeGen9SpeciesCount", cfru);
        setField(h, "speciesList", list);
        setField(h, "numRealPokemon", count);
        setField(h, "pokes", species);
        setField(h, "pokesInternal", species);
        setField(h, "pokedexToInternal", mapping);
        return new Fixture(h, bytes, species, rowSize);
    }

    private static void entry(Fixture f, int species, int slot, int method, int parameter, int target, int auxiliary) {
        int offset = species * f.rowSize() + slot * 8;
        int[] values = {method, parameter, target, auxiliary};
        for (int i = 0; i < 4; i++) {
            f.bytes()[offset + 2 * i] = (byte) values[i];
            f.bytes()[offset + 2 * i + 1] = (byte) (values[i] >> 8);
        }
    }

    private static int word(Fixture f, int species, int slot, int field) {
        int o = species * f.rowSize() + slot * 8 + field;
        return (f.bytes()[o] & 0xFF) | ((f.bytes()[o + 1] & 0xFF) << 8);
    }

    private static void write(Fixture f) throws Exception {
        Method method = Gen3RomHandler.class.getDeclaredMethod("writeEvolutions");
        invoke(method, f.handler());
    }

    private static void invoke(Method method, Gen3RomHandler handler) throws Exception {
        method.setAccessible(true);
        try {
            method.invoke(handler);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw error;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // inherited ROM byte array
            }
        }
        throw new NoSuchFieldException(name);
    }
}
