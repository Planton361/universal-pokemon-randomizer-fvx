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
    // Inside the old learnset allocator arena [0x1219A48, 0x1600000).
    static final int ARENA_TABLE = 0x1300000, ARENA_BLOBS = 0x1302000;
    static final String PROPERTY = "uprfvx.cfruOakOwnership";
    static final String LEARNSET_PROPERTY = "uprfvx.cfruLearnsetReorderWitness";

    static class Fixture extends CfruDpeEvolutionFixture {
        final Gen3CfruDpeEasierTimeEvolutionTest.OakFixture oak = new Gen3CfruDpeEasierTimeEvolutionTest.OakFixture(0x1600000);
        final byte[] rom = oak.memory;
        final int table, blobs;
        int failAfter = -1, writes;

        Fixture() throws Exception { this(TABLE, BLOBS); }

        Fixture(int table, int blobs) throws Exception {
            super();
            this.table = table;
            this.blobs = blobs;
            setField("rom", rom);
            setField("originalRom", rom.clone());
            getRomEntry().setRomType(Gen3Constants.RomType_FRLG);
            Move[] moves = new Move[100];
            for (int id = 1; id <= 60; id++) {
                moves[id] = new Move();
                moves[id].number = id;
            }
            setField("moves", moves);
            pointer(0x43E20, table);
        }

        /** Declares the table and [blobs, blobs + OWNED) as witnessed learnset owners and binds the witness. */
        void attest() throws Exception {
            declareOwners();
            withWitness(this::preflightCfruDpeLearnsetReorder);
        }

        void declareOwners() throws Exception {
            int count = Integer.parseInt(oak.p.getProperty("protected.count"));
            oak.set("protected." + count + ".type", "LEARNSET_TABLE");
            oak.range("protected." + count, table, table + 1440 * 4);
            oak.set("protected." + (count + 1) + ".type", "LEARNSET_ASSET");
            oak.range("protected." + (count + 1), blobs, blobs + OWNED);
            oak.set("protected.count", count + 2);
            oak.digest();
            setField("originalRom", rom.clone());
        }

        interface Step { void run() throws Exception; }

        void withWitness(Step step) throws Exception {
            Path file = Files.createTempFile("synthetic-oak-witness", ".properties");
            try {
                Files.write(file, oak.serialized());
                System.setProperty(PROPERTY, file.toString());
                step.run();
            } finally {
                System.clearProperty(PROPERTY);
                Files.delete(file);
            }
        }

        /** A LEARNSET_REORDER_WITNESS_V1 as devtools/scripts/cfru_learnset_witness.py writes it. */
        Properties learnsetWitness(int... bounds) throws Exception {
            setField("originalRom", rom.clone());
            Properties p = new Properties();
            p.setProperty("schema", "LEARNSET_REORDER_WITNESS_V1");
            p.setProperty("version", "1");
            p.setProperty("cfru.sha", "196058edbc2b8b7d8c725b715b1f967ba60f5c83");
            p.setProperty("dpe.sha", "d887185de1f6ae6a78e85c4311bbadde17041d00");
            p.setProperty("elf.sha256", "1".repeat(64));
            p.setProperty("input.size", Integer.toString(rom.length));
            p.setProperty("input.sha256", HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(rom)));
            p.setProperty("table.start", "0x" + Integer.toHexString(table));
            p.setProperty("table.end", "0x" + Integer.toHexString(table + 1440 * 4));
            p.setProperty("objects.count", Integer.toString(bounds.length / 2));
            for (int i = 0; i < bounds.length; i += 2) {
                p.setProperty("objects." + i / 2 + ".start", "0x" + Integer.toHexString(bounds[i]));
                p.setProperty("objects." + i / 2 + ".end", "0x" + Integer.toHexString(bounds[i + 1]));
            }
            return p;
        }

        void bindLearnsets(Properties witness) throws Exception {
            Path file = Files.createTempFile("synthetic-learnset-witness", ".properties");
            try {
                try (var out = Files.newOutputStream(file)) { witness.store(out, null); }
                System.setProperty(LEARNSET_PROPERTY, file.toString());
                preflightCfruDpeLearnsetReorder();
            } finally {
                System.clearProperty(LEARNSET_PROPERTY);
                Files.delete(file);
            }
        }

        Object field(String name) throws Exception {
            java.lang.reflect.Field f = Gen3RomHandler.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(this);
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

        void slot(int species, int at) { pointer(table + species * 4, at); }

        void clearSlot(int species) { Arrays.fill(rom, table + species * 4, table + species * 4 + 4, (byte) 0); }
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

    @Test void aStrictWitnessIsSharedWithTheOakPreflight() throws Exception {
        Fixture f = new Fixture();
        f.slot(1, f.learnset(BLOBS, 10, 1, 20, 5));
        f.attest();
        assertNotNull(f.field("oakOwnershipWitness"));
        assertNull(f.field("learnsetReorderWitness"));
        f.withWitness(f::preflightCfruDpeOakIntro);           // reused, not protected a second time
        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(20, 1, 10, 5)));
        assertEquals(List.of(20, 1, 10, 5, 0, 0xFF), decode(f, BLOBS, 3));
    }

    @Test void ownersInTheOldAllocatorArenaOnlyEnableTheInPlaceReorder() throws Exception {
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        int at = f.learnset(ARENA_BLOBS, 10, 1, 20, 5, 30, 9);
        f.slot(1, at);
        f.declareOwners();
        RomIOException parser = assertThrows(RomIOException.class, f.oak::parse);
        assertTrue(parser.getMessage().contains("owner overlaps learnset allocator"), parser.getMessage());
        byte[] before = f.rom.clone();
        f.withWitness(() -> {
            f.preflightCfruDpeLearnsetReorder();
            // Right after the reorder preflight, before any learnset mutation: Oak still parses strictly.
            RomIOException oak = assertThrows(RomIOException.class, f::preflightCfruDpeOakIntro);
            assertTrue(oak.getMessage().contains("owner overlaps learnset allocator"), oak.getMessage());
        });
        assertArrayEquals(before, f.rom);
        assertNull(f.field("oakOwnershipWitness"));            // what Oak, evolution and the selector consult
        assertNull(f.field("oakIntroLayout"));
        assertNotNull(f.field("learnsetReorderWitness"));

        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(30, 1, 10, 5, 20, 9)));
        Set<Integer> changed = new TreeSet<>();
        for (int i = 0; i < f.rom.length; i++) if (f.rom[i] != before[i]) changed.add(i);
        assertTrue(Set.of(at, at + 1, at + 3, at + 4, at + 6, at + 7).containsAll(changed), changed.toString());
        assertEquals(List.of(30, 1, 10, 5, 20, 9, 0, 0xFF), decode(f, at, 4));
        byte[] after = f.rom.clone();
        f.withWitness(() -> assertThrows(RomIOException.class, f::preflightCfruDpeOakIntro));
        assertArrayEquals(after, f.rom);
    }

    @Test void theArenaWitnessKeepsEveryOtherOwnerCheck() throws Exception {
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        f.slot(1, f.learnset(ARENA_BLOBS, 10, 1, 20, 5));
        f.attest();
        f.slot(2, f.learnset(ARENA_BLOBS + OWNED + 0x100, 30, 1, 40, 5));   // inside the arena, not owned
        assertRejected(f, movesets(1, moves(20, 1, 10, 5), 2, moves(40, 1, 30, 5)),
                "is not a witnessed LEARNSET_ASSET owner");
        f.clearSlot(2);
        f.entry(ARENA_BLOBS, 20, 1);                                       // changed since the input
        assertRejected(f, movesets(1, moves(10, 1, 20, 5)), "changed since input");

        Fixture g = new Fixture(ARENA_TABLE, ARENA_BLOBS);                 // unowned pointer already in the input
        g.slot(1, g.learnset(ARENA_BLOBS, 10, 1, 20, 5));
        g.slot(2, g.learnset(ARENA_BLOBS + OWNED + 0x100, 30, 1, 40, 5));
        byte[] before = g.rom.clone();
        assertThrows(RomIOException.class, g::attest);
        assertArrayEquals(before, g.rom);
        assertNull(g.field("learnsetReorderWitness"));
        assertRejected(g, movesets(1, moves(20, 1, 10, 5)), "no input-bound learnset owner witness");

        Fixture h = new Fixture(ARENA_TABLE, ARENA_BLOBS);                 // bound to a different input object
        h.slot(1, h.learnset(ARENA_BLOBS, 10, 1, 20, 5));
        h.attest();
        h.setField("originalRom", h.rom.clone());
        assertRejected(h, movesets(1, moves(20, 1, 10, 5)), "no input-bound learnset owner witness for this input");

        Fixture k = new Fixture(ARENA_TABLE, ARENA_BLOBS);                 // a foreign build's witness
        k.slot(1, k.learnset(ARENA_BLOBS, 10, 1, 20, 5));
        k.declareOwners();
        k.oak.set("input.sha256", "0".repeat(64));
        byte[] untouched = k.rom.clone();
        RomIOException foreign = assertThrows(RomIOException.class, () -> k.withWitness(k::preflightCfruDpeLearnsetReorder));
        assertTrue(foreign.getMessage().contains("input identity/profile mismatch"), foreign.getMessage());
        assertArrayEquals(untouched, k.rom);
        assertNull(k.field("learnsetReorderWitness"));
    }

    @Test void loadingAnotherRomDropsTheReorderWitness() throws Exception {
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        f.slot(1, f.learnset(ARENA_BLOBS, 10, 1, 20, 5));
        f.attest();
        assertNotNull(f.field("learnsetReorderWitness"));
        try {
            f.midLoadingSetUp();
        } catch (RuntimeException ignored) {
            // The synthetic handler has no real ROM entry; the owner state is reset first.
        }
        assertNull(f.field("learnsetReorderWitness"));
        assertNull(f.field("learnsetReorderWitnessInput"));
    }

    /** Arena fixture: A shared by slots 1 and 2, B in slot 3; returns {A, A end, B, B end}. */
    private static int[] linkedLearnsets(Fixture f) {
        int a = f.learnset(ARENA_BLOBS, 10, 1, 20, 5, 30, 9), b = f.learnset(ARENA_BLOBS + 0x20, 40, 1, 50, 5);
        f.slot(1, a); f.slot(2, a); f.slot(3, b);
        return new int[] {a, a + 12, b, b + 9};
    }

    @Test void theLearnsetOnlyWitnessEnablesOnlyTheInPlaceReorder() throws Exception {
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        int[] o = linkedLearnsets(f);
        byte[] before = f.rom.clone();
        f.bindLearnsets(f.learnsetWitness(o));
        assertArrayEquals(before, f.rom);
        assertNotNull(f.field("learnsetOnlyWitness"));
        assertNull(f.field("oakOwnershipWitness"));
        assertNull(f.field("learnsetReorderWitness"));
        // Oak keeps its own strict witness; the learnset-only one attests nothing there.
        assertThrows(RomIOException.class, () -> f.withWitness(f::preflightCfruDpeOakIntro));
        assertNull(f.field("oakOwnershipWitness"));
        assertArrayEquals(before, f.rom);

        f.reorderCfruDpeMovesLearntInPlace(movesets(1, moves(30, 1, 10, 5, 20, 9), 2, moves(30, 1, 10, 5, 20, 9),
                3, moves(50, 1, 40, 5)));
        Set<Integer> allowed = new HashSet<>();
        for (int at : new int[] {o[0], o[0] + 3, o[0] + 6, o[2], o[2] + 3}) { allowed.add(at); allowed.add(at + 1); }
        for (int i = 0; i < f.rom.length; i++) if (f.rom[i] != before[i]) assertTrue(allowed.contains(i), "byte " + i);
        assertEquals(List.of(30, 1, 10, 5, 20, 9, 0, 0xFF), decode(f, o[0], 4));
        assertEquals(List.of(50, 1, 40, 5, 0, 0xFF), decode(f, o[2], 3));
        assertThrows(RomIOException.class, () -> f.setMovesLearnt(movesets(1, moves(30, 1, 10, 5, 20, 9))));
    }

    @Test void aMismatchedLearnsetWitnessIsRejectedBeforeMutation() throws Exception {
        Map<String, java.util.function.BiConsumer<Properties, int[]>> cases = new LinkedHashMap<>();
        cases.put("schema or source revision mismatch", (p, o) -> p.setProperty("cfru.sha", "0".repeat(40)));
        cases.put("input identity/profile mismatch", (p, o) -> p.setProperty("input.sha256", "0".repeat(64)));
        cases.put("table does not match the runtime root", (p, o) -> {
            p.setProperty("table.start", "0x" + Integer.toHexString(ARENA_TABLE + 4));
            p.setProperty("table.end", "0x" + Integer.toHexString(ARENA_TABLE + 4 + 1440 * 4));
        });
        cases.put("slot 1 is not the start of a witnessed object",
                (p, o) -> p.setProperty("objects.0.start", "0x" + Integer.toHexString(o[0] + 3)));
        cases.put("exactly one terminated learnset",
                (p, o) -> p.setProperty("objects.0.end", "0x" + Integer.toHexString(o[1] - 3)));
        cases.put("slot 3 is not the start of a witnessed object", (p, o) -> {
            p.setProperty("objects.count", "1"); p.remove("objects.1.start"); p.remove("objects.1.end");
        });
        cases.put("unreferenced object", (p, o) -> {
            p.setProperty("objects.count", "3");
            p.setProperty("objects.2.start", "0x" + Integer.toHexString(ARENA_BLOBS + 0x40));
            p.setProperty("objects.2.end", "0x" + Integer.toHexString(ARENA_BLOBS + 0x43));
        });
        cases.put("overlapping object bounds", (p, o) -> {
            p.setProperty("objects.1.start", "0x" + Integer.toHexString(o[0] + 3));
            p.setProperty("objects.1.end", "0x" + Integer.toHexString(o[1]));
        });
        cases.put("incomplete or unknown fields", (p, o) -> p.setProperty("objects.9.start", "0x0"));
        for (var c : cases.entrySet()) {
            Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
            int[] o = linkedLearnsets(f);
            f.entry(ARENA_BLOBS + 0x40, 0, 0xFF);                // an unreferenced terminated object
            Properties p = f.learnsetWitness(o);
            c.getValue().accept(p, o);
            byte[] before = f.rom.clone();
            RomIOException error = assertThrows(RomIOException.class, () -> f.bindLearnsets(p), c.getKey());
            assertTrue(error.getMessage().contains(c.getKey()), error.getMessage());
            assertArrayEquals(before, f.rom);
            assertNull(f.field("learnsetOnlyWitness"));
            assertRejected(f, movesets(1, moves(20, 1, 10, 5, 30, 9)), "no input-bound learnset owner witness");
        }
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        linkedLearnsets(f);
        System.setProperty(LEARNSET_PROPERTY, "/nonexistent/learnset-witness.properties");
        try {
            RomIOException error = assertThrows(RomIOException.class, f::preflightCfruDpeLearnsetReorder);
            assertTrue(error.getMessage().contains("cannot read the selected learnset witness"), error.getMessage());
        } finally {
            System.clearProperty(LEARNSET_PROPERTY);
        }
    }

    @Test void theLearnsetOnlyWitnessStaysBoundToItsInputAndOwnedBytes() throws Exception {
        Fixture f = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        int[] o = linkedLearnsets(f);
        f.bindLearnsets(f.learnsetWitness(o));
        f.slot(4, f.learnset(ARENA_BLOBS + 0x100, 30, 1, 40, 5));   // in the insert, not a linked object
        assertRejected(f, movesets(1, moves(20, 1, 10, 5, 30, 9), 4, moves(40, 1, 30, 5)),
                "is not a witnessed LEARNSET_ASSET owner");
        f.clearSlot(4);
        f.entry(o[2], 50, 1);                                     // owned bytes changed since the input
        assertRejected(f, movesets(3, moves(40, 1, 50, 5)), "changed since input");

        Fixture g = new Fixture(ARENA_TABLE, ARENA_BLOBS);
        int[] q = linkedLearnsets(g);
        g.bindLearnsets(g.learnsetWitness(q));
        g.failAfter = 3;                                          // A written, the first B write fails
        byte[] before = g.rom.clone();
        var movesets = movesets(1, moves(30, 1, 10, 5, 20, 9), 2, moves(30, 1, 10, 5, 20, 9), 3, moves(50, 1, 40, 5));
        assertThrows(IllegalStateException.class, () -> g.reorderCfruDpeMovesLearntInPlace(movesets));
        assertArrayEquals(before, g.rom);
        g.setField("originalRom", g.rom.clone());                // stale: bound to another input object
        assertRejected(g, movesets, "no input-bound learnset owner witness for this input");
        try {
            g.midLoadingSetUp();
        } catch (RuntimeException ignored) {
            // The synthetic handler has no real ROM entry; the owner state is reset first.
        }
        assertNull(g.field("learnsetOnlyWitness"));
        assertNull(g.field("learnsetOnlyWitnessInput"));
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
