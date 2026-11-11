package com.uprfvx.random.randomizers;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.SpecialFormExclusionOptions;
import com.uprfvx.romio.services.SpeciesClassificationPolicy;
import com.uprfvx.romio.services.TypeService;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** #753: synthetic memory only; never opens or saves a ROM. No gameplay claim. */
class CfruDpeLearnsetReorderTest {
    static final int TABLE = 0x1000000, BLOBS = 0x1002000;
    // move id -> power; hit count 2 for move 5 (power 30 -> 60)
    static final int[][] POWERS = {{1, 0}, {2, 80}, {3, 40}, {4, 40}, {5, 30}, {6, 120}, {7, 40}};

    static class Fixture extends CfruDpeEvolutionFixture {
        final byte[] rom = new byte[0x1600000];
        int restrictions, allocatingWrites;

        Fixture() throws Exception {
            super();
            setField("rom", rom);
            Move[] moves = new Move[10];
            for (int[] p : POWERS) {
                moves[p[0]] = new Move();
                moves[p[0]].number = p[0];
                moves[p[0]].power = p[1];
                moves[p[0]].hitCount = p[0] == 5 ? 2 : 1;
            }
            setField("moves", moves);
            pointer(0x43E20, TABLE);
            learnsets.clear();
            // species 1 and 2 share one stored learnset with a 40-power tie; 3 has its own.
            learnset(1, BLOBS, 2, 1, 1, 0, 3, 4, 6, 8, 4, 12, 5, 16, 7, 20);
            learnset(2, BLOBS, 2, 1, 1, 0, 3, 4, 6, 8, 4, 12, 5, 16, 7, 20);
            learnset(3, BLOBS + 0x40, 6, 1, 2, 9, 3, 30);
        }

        void pointer(int at, int target) {
            int raw = target + 0x08000000;
            for (int i = 0; i < 4; i++) rom[at + i] = (byte) (raw >>> (8 * i));
        }

        void learnset(int species, int at, int... moveLevel) {
            List<MoveLearnt> list = new ArrayList<>();
            for (int i = 0; i < moveLevel.length; i += 2) {
                int o = at + i / 2 * 3;
                rom[o] = (byte) moveLevel[i]; rom[o + 1] = 0; rom[o + 2] = (byte) moveLevel[i + 1];
                list.add(new MoveLearnt(moveLevel[i], moveLevel[i + 1]));
            }
            int end = at + moveLevel.length / 2 * 3;
            rom[end] = 0; rom[end + 1] = 0; rom[end + 2] = (byte) 0xFF;
            pointer(TABLE + species * 4, at);
            learnsets.put(species, list);                     // what the CFRU reader returns for it
        }

        @Override public void setMovesLearnt(Map<Integer, List<MoveLearnt>> movesets) {
            allocatingWrites++;
            super.setMovesLearnt(movesets);
        }
        @Override public int generationOfPokemon() { return 3; }
        @Override public List<Integer> getMoveTutorMoves() { return List.of(); }
        @Override public List<Trainer> getTrainers() { return List.of(); }
        @Override public boolean canChangeStaticPokemon() { return false; }
        @Override public List<InGameTrade> getInGameTrades() { return List.of(); }
        @Override public RestrictedSpeciesService getRestrictedSpeciesService() {
            return new RestrictedSpeciesService(this) {
                @Override public void setRestrictions(GenRestrictions r, SpecialFormExclusionOptions options) {
                    restrictions++;
                    throw new RomIOException("synthetic stop at restrictions");
                }
            };
        }

        List<Integer> stored(int at, int entries) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < entries; i++) out.add(rom[at + i * 3] & 0xFF);
            return out;
        }
    }

    private static void reorder(Fixture f, long seed) {
        Settings s = new Settings();
        s.setReorderDamagingMoves(true);
        new SpeciesMovesetRandomizer(f, s, new Random(seed)).orderDamagingMovesByDamage();
    }

    @Test void reordersDamagingMovesInPlaceByPowerTimesHitCount() throws Exception {
        Fixture f = new Fixture();
        byte[] before = f.rom.clone();
        reorder(f, 753);
        // Damaging slots (power > 1, level > 0) sorted ascending by power x hitCount:
        // 40,40,40 (tie), 60 (move 5), 80 (move 2), 120 (move 6); move 1 (power 0) and slot order of levels kept.
        List<Integer> moves = f.stored(BLOBS, 8);
        assertEquals(1, moves.get(1));
        assertEquals(Set.of(3, 4, 7), Set.of(moves.get(0), moves.get(2), moves.get(3)));
        assertEquals(List.of(5, 2, 6), moves.subList(4, 7));
        assertEquals(List.of(3, 2, 6), f.stored(BLOBS + 0x40, 3));
        for (int i = 0; i < f.rom.length; i++) {
            boolean moveByte = (i >= BLOBS && i < BLOBS + 8 * 3 || i >= BLOBS + 0x40 && i < BLOBS + 0x40 + 3 * 3)
                    && (i - (i >= BLOBS + 0x40 ? BLOBS + 0x40 : BLOBS)) % 3 != 2;
            if (!moveByte) assertEquals(before[i], f.rom[i], "byte 0x" + Integer.toHexString(i));
        }
        assertEquals(0, f.allocatingWrites);
    }

    @Test void sharedLearnsetsGetOneOrderingAndRepeatDeterministically() throws Exception {
        for (long seed = 0; seed < 32; seed++) {
            Fixture f = new Fixture(), g = new Fixture();
            reorder(f, seed);
            reorder(g, seed);
            assertArrayEquals(f.rom, g.rom, "seed " + seed);
            List<Integer> one = f.learnsets.get(1).stream().map(ml -> ml.move).toList();
            assertEquals(one, f.learnsets.get(2).stream().map(ml -> ml.move).toList(), "seed " + seed);
            assertEquals(one, f.stored(BLOBS, 7), "seed " + seed);
        }
    }

    private static GameRandomizer game(Fixture f, Settings s) {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override protected Object[][] getContents() { return new Object[0][]; }
        };
        return new GameRandomizer(s, null, f, bundle, false);
    }

    @Test void onlyUnchangedWithReorderPassesTheGateAndOnlyForAValidTable() throws Exception {
        Fixture f = new Fixture();
        Settings s = new Settings();
        s.setReorderDamagingMoves(true);
        var result = game(f, s).randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 753);
        assertEquals("synthetic stop at restrictions", result.getException().getMessage());
        assertEquals(1, f.restrictions);

        for (Settings.MovesetsMod mode : Settings.MovesetsMod.values()) {
            if (mode == Settings.MovesetsMod.UNCHANGED) continue;
            for (boolean reorder : new boolean[]{false, true}) {
                Fixture g = new Fixture();
                Settings t = new Settings(); t.setMovesetsMod(mode); t.setReorderDamagingMoves(reorder);
                byte[] before = g.rom.clone();
                var rejected = game(g, t).randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 753);
                assertInstanceOf(RandomizationException.class, rejected.getException(), mode + "/" + reorder);
                assertEquals(0, g.restrictions); assertArrayEquals(before, g.rom);
            }
        }

        Fixture h = new Fixture();
        h.pointer(TABLE + 4, 0x100);                          // malformed table: rejected before any step
        var rejected = game(h, s).randomize("unused", new PrintStream(OutputStream.nullOutputStream()), 753);
        assertInstanceOf(RandomizationException.class, rejected.getException());
        assertTrue(rejected.getException().getMessage().contains("in-place learnset reorder rejected"));
        assertEquals(0, h.restrictions);
    }

    /** Pinned from 1a9b59db: the non-CFRU path must still write via setMovesLearnt, unchanged. */
    @Test void vanillaReorderIsUnchanged() {
        Map<Integer, List<MoveLearnt>> movesets = new TreeMap<>();
        movesets.put(1, new ArrayList<>(List.of(new MoveLearnt(2, 1), new MoveLearnt(1, 0), new MoveLearnt(3, 4),
                new MoveLearnt(6, 8), new MoveLearnt(4, 12), new MoveLearnt(5, 16), new MoveLearnt(7, 20))));
        movesets.put(2, new ArrayList<>(List.of(new MoveLearnt(2, 1), new MoveLearnt(1, 0), new MoveLearnt(3, 4),
                new MoveLearnt(6, 8), new MoveLearnt(4, 12), new MoveLearnt(5, 16), new MoveLearnt(7, 20))));
        List<Move> moves = new ArrayList<>(Collections.nCopies(10, (Move) null));
        for (int[] p : POWERS) {
            Move m = new Move(); m.number = p[0]; m.power = p[1]; m.hitCount = p[0] == 5 ? 2 : 1;
            moves.set(p[0], m);
        }
        List<Map<Integer, List<MoveLearnt>>> written = new ArrayList<>();
        RomHandler[] handler = new RomHandler[1];
        handler[0] = (RomHandler) Proxy.newProxyInstance(RomHandler.class.getClassLoader(),
                new Class<?>[]{RomHandler.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRestrictedSpeciesService" -> new RestrictedSpeciesService(handler[0]);
                    case "getTypeService" -> new TypeService(handler[0]);
                    case "getSpeciesClassificationPolicy" -> SpeciesClassificationPolicy.legacy();
                    case "getMovesLearnt" -> movesets;
                    case "getMoves" -> moves;
                    case "setMovesLearnt" -> { written.add(movesetsOf(args[0])); yield null; }
                    case "hashCode" -> 0;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Random random = new Random(753);
        new SpeciesMovesetRandomizer(handler[0], new Settings(), random).orderDamagingMovesByDamage();
        assertEquals(1, written.size());
        assertEquals(VANILLA_1, written.get(0).get(1).stream().map(ml -> ml.move).toList());
        assertEquals(VANILLA_2, written.get(0).get(2).stream().map(ml -> ml.move).toList());
        assertEquals(VANILLA_NEXT, random.nextLong());
    }

    // Vanilla breaks the 40-power tie per species: equal lists may differ (#753 needs one ordering).
    static final List<Integer> VANILLA_1 = List.of(3, 1, 7, 4, 5, 2, 6), VANILLA_2 = List.of(4, 1, 7, 3, 5, 2, 6);
    static final long VANILLA_NEXT = 4264036426451243289L;

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<MoveLearnt>> movesetsOf(Object value) {
        return (Map<Integer, List<MoveLearnt>>) value;
    }
}
