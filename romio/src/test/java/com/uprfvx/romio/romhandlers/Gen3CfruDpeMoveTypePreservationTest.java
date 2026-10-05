package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic arrays only: actual move-table load/save, without ROM files or runtime claims. */
class Gen3CfruDpeMoveTypePreservationTest {
    private static final int COUNT = MoveIDs.struggle;
    private static final int DATA = 32;
    private static final int ROW_SIZE = 12;
    private static final int NAMES = DATA + (COUNT + 1) * ROW_SIZE + 32;
    private static final int NAME_LENGTH = 8;
    private static final int STRUGGLE = DATA + MoveIDs.struggle * ROW_SIZE;

    @Test
    void struggleMysterySurvivesRepeatedSavesReloadsAndFreshHandler() throws Exception {
        Fixture f = fixture(true, 0x09);
        assertEquals(Type.NORMAL, f.move().type);
        byte[] before = f.bytes().clone();
        for (int i = 0; i < 3; i++) {
            f.handler().saveMoves();
            f.handler().saveMoves();
            assertArrayEquals(before, f.bytes());
            f.handler().loadMoves();
            assertEquals(Type.NORMAL, f.move().type);
        }
        Fixture fresh = load(true, f.bytes().clone());
        fresh.handler().saveMoves();
        assertEquals(0x09, fresh.rawType());
        assertArrayEquals(before, fresh.bytes());
    }

    @Test
    void additionalOpaqueByteSurvivesWithoutNewTypeSemantics() throws Exception {
        Fixture f = fixture(true, 0x18);
        assertEquals(Type.NORMAL, f.move().type);
        f.handler().saveMoves();
        f.handler().loadMoves();
        f.handler().saveMoves();
        Fixture fresh = load(true, f.bytes().clone());
        assertEquals(Type.NORMAL, fresh.move().type);
        fresh.handler().saveMoves();
        assertEquals(0x18, fresh.rawType());
    }

    @Test
    void supportedTypesRoundTripAlongsideOpaqueMoves() throws Exception {
        Fixture f = fixture(true, 0x09);
        int[] rawTypes = {0x00, 0x17, 0x0B};
        Type[] types = {Type.NORMAL, Type.FAIRY, Type.WATER};
        for (int i = 0; i < rawTypes.length; i++) f.bytes()[DATA + (i + 1) * ROW_SIZE + 2] = (byte) rawTypes[i];
        f.handler().loadMoves();
        byte[] before = f.bytes().clone();
        for (int i = 0; i < types.length; i++) assertEquals(types[i], f.handler().getMoves().get(i + 1).type);
        f.handler().saveMoves();
        f.handler().loadMoves();
        f.handler().saveMoves();
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void unrelatedWriterOwnedFieldsAndNameChangePreserveOpaqueType() throws Exception {
        Fixture f = fixture(true, 0x09);
        f.move().power = 91;
        f.move().pp = 17;
        f.move().category = MoveCategory.SPECIAL;
        f.move().effectIndex = 1;
        f.move().name = "B";
        f.handler().saveMoves();
        assertEquals(0x09, f.rawType());
        assertEquals(91, f.bytes()[STRUGGLE + 1] & 0xFF);
        assertEquals(17, f.bytes()[STRUGGLE + 4] & 0xFF);
        assertEquals(1, f.bytes()[STRUGGLE + 10] & 0xFF);
        assertEquals(1, f.bytes()[STRUGGLE] & 0xFF);
        f.handler().loadMoves();
        assertEquals(Type.NORMAL, f.move().type);
        assertEquals(91, f.move().power);
        assertEquals(17, f.move().pp);
        assertEquals(MoveCategory.SPECIAL, f.move().category);
        assertEquals("B", f.move().name);
        f.handler().saveMoves();
        assertEquals(0x09, f.rawType());
    }

    @Test
    void intentionalFireThenNormalThenFairyUsesExistingEncoding() throws Exception {
        Fixture f = fixture(true, 0x09);
        // No reload between Fire and Normal: the snapshot must refresh after writing.
        f.move().type = Type.FIRE;
        f.handler().saveMoves();
        assertEquals(0x0A, f.rawType());
        f.handler().saveMoves();
        assertEquals(Type.FIRE, load(true, f.bytes().clone()).move().type);
        f.move().type = Type.NORMAL;
        f.handler().saveMoves();
        assertEquals(0x00, f.rawType());
        f.handler().loadMoves();
        assertEquals(Type.NORMAL, f.move().type);
        f.move().type = Type.FAIRY;
        f.handler().saveMoves();
        for (int i = 0; i < 3; i++) {
            assertEquals(0x17, f.rawType());
            f.handler().saveMoves();
            f.handler().loadMoves();
            assertEquals(Type.FAIRY, f.move().type);
        }
        Fixture fresh = load(true, f.bytes().clone());
        fresh.handler().saveMoves();
        assertEquals(0x17, fresh.rawType());
    }

    @Test
    void settingNormalAgainIsAValueNoOp() throws Exception {
        Fixture f = fixture(true, 0x09);
        f.move().type = Type.NORMAL;
        f.handler().saveMoves();
        assertEquals(0x09, f.rawType());
    }

    @Test
    void reloadReplacesSnapshotsRatherThanRetainingStaleMoveObjects() throws Exception {
        Fixture f = fixture(true, 0x09);
        Move old = f.move();
        f.bytes()[STRUGGLE + 2] = 0x18;
        f.handler().loadMoves();
        assertNotSame(old, f.move());
        Map<?, ?> snapshots = (Map<?, ?>) field(f.handler(), "originalCfruDpeMoveTypes");
        assertEquals(COUNT, snapshots.size());
        assertFalse(snapshots.containsKey(old));
        f.handler().saveMoves();
        assertEquals(0x18, f.rawType());
    }

    @Test
    void vanillaKeepsPreviousOpaqueNormalizationAndDoesNotCaptureSnapshots() throws Exception {
        Fixture f = fixture(false, 0x09);
        assertEquals(Type.NORMAL, f.move().type);
        assertTrue(((Map<?, ?>) field(f.handler(), "originalCfruDpeMoveTypes")).isEmpty());
        f.handler().saveMoves();
        assertEquals(0x00, f.rawType());
        f.move().type = Type.FIRE;
        f.handler().saveMoves();
        assertEquals(0x0A, f.rawType());
        f.handler().loadMoves();
        assertEquals(Type.FIRE, f.move().type);
        f.move().type = Type.NORMAL;
        f.handler().saveMoves();
        assertEquals(0x00, f.rawType());
        assertTrue(((Map<?, ?>) field(f.handler(), "originalCfruDpeMoveTypes")).isEmpty());
    }

    private record Fixture(Gen3RomHandler handler, byte[] bytes) {
        Move move() { return handler.getMoves().get(MoveIDs.struggle); }
        int rawType() { return bytes[STRUGGLE + 2] & 0xFF; }
    }

    private static Fixture fixture(boolean cfru, int rawType) throws Exception {
        byte[] bytes = new byte[NAMES + (COUNT + 1) * NAME_LENGTH];
        for (int i = 1; i <= COUNT; i++) {
            int row = DATA + i * ROW_SIZE;
            bytes[row + 1] = 50;
            bytes[row + 3] = 100;
            bytes[row + 4] = 10;
            bytes[NAMES + i * NAME_LENGTH] = (byte) 0xFF;
        }
        bytes[STRUGGLE + 2] = (byte) rawType;
        return load(cfru, bytes);
    }

    private static Fixture load(boolean cfru, byte[] bytes) throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        var constructor = Gen3RomEntry.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Gen3RomEntry entry = constructor.newInstance("synthetic move data");
        entry.putIntValue("MoveCount", COUNT);
        entry.putIntValue("MoveData", DATA);
        entry.putIntValue("MoveNames", NAMES);
        entry.putIntValue("MoveNameLength", NAME_LENGTH);
        setField(handler, "romEntry", entry);
        setField(handler, "rom", bytes);
        setField(handler, "useCfruDpeGen9SpeciesCount", cfru);
        String[] table = new String[256];
        table[1] = "A";
        table[2] = "B";
        setField(handler, "tb", table);
        handler.d = Map.of("A", (byte) 1, "B", (byte) 2);
        handler.loadMoves();
        return new Fixture(handler, bytes);
    }

    private static Field findField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // The synthetic byte array is inherited.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        return findField(target, name).get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        findField(target, name).set(target, value);
    }
}
