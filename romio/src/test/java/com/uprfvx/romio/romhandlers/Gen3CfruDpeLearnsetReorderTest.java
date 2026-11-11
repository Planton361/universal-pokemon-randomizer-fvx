package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveLearnt;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #753: synthetic memory only. A CFRU-shaped table at 0x1000000 is installed at 0x43E20 and owned by
 * a synthetic OAK_OWNERSHIP_WITNESS_V1 (never an attestation of a real build).
 */
class Gen3CfruDpeLearnsetReorderTest {
    static final int TABLE = 0x1000000, BLOBS = 0x1002000, OWNED = 0x1000;
    static final String PROPERTY = "uprfvx.cfruOakOwnership";

    static class Fixture extends CfruDpeEvolutionFixture {
        final Gen3CfruDpeEasierTimeEvolutionTest.OakFixture oak = new Gen3CfruDpeEasierTimeEvolutionTest.OakFixture(0x1600000);
        final byte[] rom = oak.memory;
        int failAfter = -1, writes;

        Fixture() throws Exception {
            super();
            setField("rom", rom);
            setField("originalRom", rom.clone());
            getRomEntry().setRomType(Gen3Constants.RomType_FRLG);
            Move[] moves = new Move[100];
            for (int id = 1; id <= 60; id++) {
                moves[id] = new Move();
                moves[id].number = id;
            }
            setField("moves", moves);
            pointer(0x43E20, TABLE);
        }

        /** Declares the table and [BLOBS, BLOBS + OWNED) as witnessed learnset owners and binds the witness. */
        void attest() throws Exception {
            int count = Integer.parseInt(oak.p.getProperty("protected.count"));
            oak.set("protected." + count + ".type", "LEARNSET_TABLE");
            oak.range("protected." + count, TABLE, TABLE + 1440 * 4);
            oak.set("protected." + (count + 1) + ".type", "LEARNSET_ASSET");
            oak.range("protected." + (count + 1), BLOBS, BLOBS + OWNED);
            oak.set("protected.count", count + 2);
            oak.digest();
            setField("originalRom", rom.clone());
            Path file = Files.createTempFile("synthetic-oak-witness", ".properties");
            try {
                Files.write(file, oak.serialized());
                System.setProperty(PROPERTY, file.toString());
                preflightCfruDpeLearnsetReorder();
            } finally {
                System.clearProperty(PROPERTY);
                Files.delete(file);
            }
        }

        @Override protected void writeWord(int offset, int value) {
            if (writes++ == failAfter) throw new IllegalStateException("injected write fault");
            super.writeWord(offset, value);
        }

        void pointer(int at, int target) {
            int raw = target + 0x08000000;
            for (int i = 0; i < 4; i++) rom[at + i] = (byte) (raw >>> (8 * i));
        }

        /** Writes {move, level} pairs plus the terminator at {@code at}; returns {@code at}. */
        int learnset(int at, int... moveLevel) {
            for (int i = 0; i < moveLevel.length; i += 2) entry(at + i / 2 * 3, moveLevel[i], moveLevel[i + 1]);
            entry(at + moveLevel.length / 2 * 3, 0, 0xFF);
            return at;
        }

        void entry(int at, int move, int level) {
            rom[at] = (byte) move; rom[at + 1] = (byte) (move >>> 8); rom[at + 2] = (byte) level;
        }

        void slot(int species, int at) { pointer(TABLE + species * 4, at); }

        void clearSlot(int species) { Arrays.fill(rom, TABLE + species * 4, TABLE + species * 4 + 4, (byte) 0); }
    }

    private static List<MoveLearnt> moves(int... moveLevel) {
        List<MoveLearnt> list = new ArrayList<>();
        for (int i = 0; i < moveLevel.length; i += 2) list.add(new MoveLearnt(moveLevel[i], moveLevel[i + 1]));
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<MoveLearnt>> movesets(Object... speciesAndMoves) {
        Map<Integer, List<MoveLearnt>> map = new TreeMap<>();
        for (int i = 0; i < speciesAndMoves.length; i += 2)
            map.put((Integer) speciesAndMoves[i], (List<MoveLearnt>) speciesAndMoves[i + 1]);
        return map;
    }

    private static void assertRejected(Fixture f, Map<Integer, List<MoveLearnt>> movesets, String reason) {
        byte[] before = f.rom.clone();
        RomIOException error = assertThrows(RomIOException.class, () -> f.reorderCfruDpeMovesLearntInPlace(movesets));
        assertTrue(error.getMessage().contains(reason), error.getMessage());
        assertArrayEquals(before, f.rom);
    }

    @Test void permutesOnlyMoveFieldsOfTheStoredEntries() throws Exception {
        Fixture f = new Fixture();
        int at = f.learnset(BLOBS, 10, 1, 20, 5, 30, 9);
        f.slot(1, at);
        f.attest();
        byte[] before = f.rom.clone();
        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(30, 1, 10, 5, 20, 9)));
        Set<Integer> changed = new TreeSet<>();
        for (int i = 0; i < f.rom.length; i++) if (f.rom[i] != before[i]) changed.add(i);
        // Move words of entries 0..2 only; levels, terminator, table and roots are untouched.
        assertTrue(Set.of(at, at + 1, at + 3, at + 4, at + 6, at + 7).containsAll(changed), changed.toString());
        assertEquals(List.of(30, 1, 10, 5, 20, 9, 0, 0xFF), decode(f, at, 4));
    }

    @Test void writesNeedAnInputBoundLearnsetOwnerWitness() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        // MATCH-like evidence alone (a valid installed table inside the CFRU insert) is not enough.
        assertRejected(f, movesets(1, moves(20, 1, 10, 5)), "no input-bound learnset owner witness");
        byte[] before = f.rom.clone();
        RomIOException missing = assertThrows(RomIOException.class, f::preflightCfruDpeLearnsetReorder);
        assertTrue(missing.getMessage().contains("-D" + PROPERTY), missing.getMessage());
        f.setField("oakIntroLayout", new Gen3RomHandler.OakIntroLayout(0x180000, 0x180100, List.of()));
        RomIOException layout = assertThrows(RomIOException.class, f::preflightCfruDpeLearnsetReorder);
        assertTrue(layout.getMessage().contains("does not attest learnset owners"), layout.getMessage());
        assertArrayEquals(before, f.rom);

        Fixture g = new Fixture();
        g.slot(1, g.learnset(BLOBS, 10, 1, 20, 5));
        g.attest();
        g.setField("originalRom", g.rom.clone());              // witness bound to a different input object
        assertRejected(g, movesets(1, moves(20, 1, 10, 5)), "no input-bound learnset owner witness for this input");
    }

    @Test void aPointerInsideTheInsertToUnownedDataIsRejected() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        f.attest();
        // Formally valid and terminated, inside the CFRU insert, but not a witnessed learnset.
        f.slot(2, f.learnset(BLOBS + OWNED + 0x100, 30, 1, 40, 5));
        assertRejected(f, movesets(1, moves(20, 1, 10, 5), 2, moves(40, 1, 30, 5)),
                "is not a witnessed LEARNSET_ASSET owner");

        Fixture g = new Fixture();                             // the same pointer already in the input
        g.slot(1, g.learnset(BLOBS, 10, 1, 20, 5));
        g.slot(2, g.learnset(BLOBS + OWNED + 0x100, 30, 1, 40, 5));
        byte[] before = g.rom.clone();
        assertThrows(RomIOException.class, g::attest);         // the witness parser refuses to bind it
        assertArrayEquals(before, g.rom);
        assertRejected(g, movesets(1, moves(20, 1, 10, 5)), "no input-bound learnset owner witness");
    }

    @Test void learnsetsChangedSinceTheInputAreRejected() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        f.attest();
        f.entry(BLOBS, 20, 1);                                 // another writer touched the owned bytes
        assertRejected(f, movesets(1, moves(10, 1, 20, 5)), "changed since input");
    }

    @Test void sharedPointersNeedIdenticalResultsAndAreWrittenOnce() throws Exception {
        Fixture f = new Fixture();
        int at = f.learnset(BLOBS, 10, 1, 20, 5);
        f.slot(1, at); f.slot(2, at); f.slot(3, at);           // slot 3 has no moveset entry: follows the blob
        f.attest();
        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(20, 1, 10, 5), 2, moves(20, 1, 10, 5)));
        assertEquals(List.of(20, 1, 10, 5, 0, 0xFF), decode(f, at, 3));
        assertEquals(2, f.writes);

        Fixture g = new Fixture();
        g.slot(1, g.learnset(BLOBS, 10, 1, 20, 5)); g.slot(2, BLOBS);
        g.attest();
        assertRejected(g, movesets(1, moves(20, 1, 10, 5), 2, moves(10, 1, 20, 5)), "reordered differently");
    }

    @Test void unknownMovesAndLevelZeroEntriesStayInPlace() throws Exception {
        Fixture f = new Fixture();
        int at = f.learnset(BLOBS, 10, 0, 999, 3, 20, 5, 30, 7);   // 999 is not a loaded move
        f.slot(1, at);
        f.attest();
        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(10, 0, 30, 5, 20, 7)));
        assertEquals(List.of(10, 0, 999, 3, 30, 5, 20, 7, 0, 0xFF), decode(f, at, 5));

        Fixture g = new Fixture();
        g.slot(1, g.learnset(BLOBS, 10, 0, 999, 3, 20, 5, 30, 7));
        g.attest();
        assertRejected(g, movesets(1, moves(20, 0, 30, 5, 10, 7)), "level-0 move");
    }

    @Test void anythingButAReorderingIsRejectedWithoutMutation() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5, 30, 9));
        f.attest();
        assertRejected(f, movesets(1, moves(30, 1, 20, 6, 10, 9)), "changes a level");
        assertRejected(f, movesets(1, moves(30, 1, 20, 5, 40, 9)), "not a reordering");
        assertRejected(f, movesets(1, moves(30, 1, 20, 5)), "fewer moves");
        assertRejected(f, movesets(1, moves(30, 1, 20, 5, 10, 9, 40, 12)), "not a reordering");
        assertRejected(f, movesets(1, moves(30, 1, 20, 5, 10, 9), 7, moves(10, 1)), "no stored learnset");
        assertRejected(f, movesets(1440, moves(10, 1)), "outside the native table");
    }

    @Test void invalidTablesPointersAndTruncatedLearnsetsAreRejected() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1));
        f.attest();
        f.slot(2, 0x100);                                      // outside the CFRU insert
        assertRejected(f, movesets(1, moves(10, 1)), "slot 2 points outside the CFRU insert");
        f.clearSlot(2);
        f.pointer(0x43E20, 0x200000);                          // valid pointer, but not inside the CFRU insert
        assertRejected(f, movesets(1, moves(10, 1)), "not inside the CFRU insert");
        Arrays.fill(f.rom, 0x43E20, 0x43E24, (byte) 0);        // no installed CFRU table: no fallback
        assertRejected(f, movesets(1, moves(10, 1)), "no CFRU gLevelUpLearnsets table");

        Fixture h = new Fixture();
        int[] full = new int[100];                             // 50 moves + terminator: the witnessed maximum
        for (int i = 0; i < full.length; i += 2) { full[i] = 1 + i / 2; full[i + 1] = 1; }
        h.slot(1, h.learnset(BLOBS, full));
        h.attest();
        h.entry(BLOBS + 50 * 3, 7, 7);                          // 51 entries, no terminator in range
        assertRejected(h, movesets(), "unterminated or truncated");
    }

    @Test void overlappingLearnsetsAndOwnersAreRejected() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5)); f.slot(2, BLOBS + 3);
        byte[] before = f.rom.clone();
        RomIOException overlap = assertThrows(RomIOException.class, f::attest);   // the preflight already plans
        assertTrue(overlap.getMessage().contains("overlap another learnset"), overlap.getMessage());
        assertArrayEquals(before, f.rom);

        Fixture h = new Fixture();
        h.slot(1, h.learnset(BLOBS, 10, 1, 20, 5));
        h.attest();
        h.setField("oakIntroLayout", new Gen3RomHandler.OakIntroLayout(BLOBS - 90, 0x40000, List.of()));
        assertRejected(h, movesets(1, moves(20, 1, 10, 5)), "overlaps DESCRIPTOR");
    }

    @Test void aWriteFaultRestoresEveryByte() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        f.slot(2, f.learnset(BLOBS + 0x40, 30, 1, 40, 5));
        f.attest();
        f.failAfter = 3;                                       // blob 1 written, blob 2 half written
        byte[] before = f.rom.clone();
        var movesets = movesets(1, moves(20, 1, 10, 5), 2, moves(40, 1, 30, 5));
        assertThrows(IllegalStateException.class, () -> f.reorderCfruDpeMovesLearntInPlace(movesets));
        assertArrayEquals(before, f.rom);
    }

    @Test void theAllocatingWriterAndOtherProfilesStayClosed() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        f.attest();
        byte[] before = f.rom.clone();
        RomIOException error = assertThrows(RomIOException.class, () -> f.setMovesLearnt(movesets(1, moves(20, 1, 10, 5))));
        assertTrue(error.getMessage().contains("verified allocator"));
        assertArrayEquals(before, f.rom);
        f.setField("useCfruDpeGen9SpeciesCount", false);
        assertFalse(f.usesCfruDpeLevelUpLearnsets());
        assertRejected(f, movesets(1, moves(20, 1, 10, 5)), "not the CFRU/DPE learnset profile");
        assertThrows(RomIOException.class, f::preflightCfruDpeLearnsetReorder);
    }

    private static List<Integer> decode(Fixture f, int at, int entries) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < entries; i++) {
            int o = at + i * 3;
            out.add((f.rom[o] & 0xFF) | (f.rom[o + 1] & 0xFF) << 8);
            out.add(f.rom[o + 2] & 0xFF);
        }
        return out;
    }
}
