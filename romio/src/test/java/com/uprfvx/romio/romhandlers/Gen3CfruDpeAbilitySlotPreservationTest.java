package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Synthetic byte arrays only; exercises the actual base-stat loader and writer. */
class Gen3CfruDpeAbilitySlotPreservationTest {
    private static final int OFFSET = Gen3Constants.baseStatsEntrySize;
    private static final int[] ABILITY_OFFSETS = {
            Gen3Constants.bsAbility1Offset, Gen3Constants.bsAbility2Offset,
            Gen3Constants.bsHiddenAbilityOffset
    };

    @Test
    void unchangedZeroSlot2SurvivesRepeatedSavesAndFreshHandlerReload() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        byte[] before = f.bytes().clone();
        assertTuple(f, 0x41, 0, 0x22);
        save(f);
        save(f);
        assertArrayEquals(before, f.bytes());
        reload(f);
        save(f);
        Fixture fresh = load(true, f.bytes());
        assertTuple(fresh, 0x41, 0, 0x22);
        save(fresh);
        assertArrayEquals(before, fresh.bytes());
    }

    @Test
    void unchangedNonzeroSlot2AndHiddenAbilityRemainExact() throws Exception {
        Fixture f = fixture(true, 0x41, 0x80, 0xFE);
        byte[] before = f.bytes().clone();
        save(f);
        reload(f);
        save(f);
        assertTuple(f, 0x41, 0x80, 0xFE);
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void allZeroTupleRemainsZero() throws Exception {
        Fixture f = fixture(true, 0, 0, 0);
        byte[] before = f.bytes().clone();
        save(f);
        reload(f);
        save(f);
        assertTuple(f, 0, 0, 0);
        assertArrayEquals(before, f.bytes());
    }

    @Test
    void statMutationPreservesAbilitiesAndWritesStat() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        f.species().setHp(99);
        f.species().setAttack(100);
        save(f);
        reload(f);
        assertEquals(99, f.species().getHp());
        assertEquals(100, f.species().getAttack());
        assertTuple(f, 0x41, 0, 0x22);
        save(f);
        assertRawTuple(f, 0x41, 0, 0x22);
    }

    @Test
    void typeMutationPreservesAbilitiesAndWritesTypes() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        f.species().setPrimaryType(Type.FAIRY);
        f.species().setSecondaryType(Type.WATER);
        save(f);
        reload(f);
        assertEquals(Type.FAIRY, f.species().getPrimaryType(false));
        assertEquals(Type.WATER, f.species().getSecondaryType(false));
        assertTuple(f, 0x41, 0, 0x22);
        save(f);
        assertRawTuple(f, 0x41, 0, 0x22);
    }

    @Test
    void itemMutationPreservesAbilitiesAndWritesItem() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        f.species().setGuaranteedHeldItem(new Item(1, "Synthetic item"));
        save(f);
        reload(f);
        assertEquals(1, f.species().getGuaranteedHeldItem().getId());
        assertTuple(f, 0x41, 0, 0x22);
    }

    @Test
    void eachIntentionalNonzeroAbilityMutationWritesAndReloads() throws Exception {
        for (int slot = 0; slot < 3; slot++) {
            Fixture f = fixture(true, 0x41, 0x42, 0x22);
            int[] expected = {0x41, 0x42, 0x22};
            expected[slot] = 0x81 + slot;
            switch (slot) {
                case 0 -> f.species().setAbility1(expected[slot]);
                case 1 -> f.species().setAbility2(expected[slot]);
                case 2 -> f.species().setAbility3(expected[slot]);
            }
            save(f);
            assertRawTuple(f, expected);
            save(f);
            reload(f);
            assertTuple(f, expected);
            save(f);
            assertRawTuple(f, expected);
        }
    }

    @Test
    void intentionalZeroSlot2RetainsLegacyFallbackIncludingRepeatedSave() throws Exception {
        Fixture f = fixture(true, 0x41, 0x42, 0x22);
        f.species().setAbility2(0);
        save(f);
        assertRawTuple(f, 0x41, 0x41, 0x22);
        assertEquals(0, f.species().getAbility2(), "writer does not rewrite the randomizer model");
        save(f);
        assertRawTuple(f, 0x41, 0x41, 0x22);
        reload(f);
        assertTuple(f, 0x41, 0x41, 0x22);
        save(f);
        assertRawTuple(f, 0x41, 0x41, 0x22);
    }

    @Test
    void changesToSlot1OrHiddenWithLoadedZeroSlot2AlsoUseFallback() throws Exception {
        for (boolean hiddenOnly : new boolean[] {false, true}) {
            Fixture f = fixture(true, 0x41, 0, 0x22);
            if (hiddenOnly) f.species().setAbility3(0x23);
            else f.species().setAbility1(0x43);
            int slot1 = hiddenOnly ? 0x41 : 0x43;
            int hidden = hiddenOnly ? 0x23 : 0x22;
            save(f);
            save(f);
            reload(f);
            assertTuple(f, slot1, slot1, hidden);
        }
    }

    @Test
    void revertingToOriginalTupleAfterMutationIsComparedToLastWrittenTuple() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        f.species().setAbility2(0x42);
        save(f);
        f.species().setAbility2(0);
        save(f);
        reload(f);
        assertTuple(f, 0x41, 0x41, 0x22);
    }

    @Test
    void snapshotsDoNotLeakBetweenSpeciesWithEqualModelIdentity() throws Exception {
        Fixture f = fixture(true, 0x41, 0, 0x22);
        Species other = new Species(f.species().getNumber());
        f.bytes()[2 * OFFSET + Gen3Constants.bsAbility1Offset] = 0x51;
        f.bytes()[2 * OFFSET + Gen3Constants.bsHiddenAbilityOffset] = 0x32;
        invoke("loadBasicPokeStats", f.handler(), other, 2 * OFFSET);
        save(f);
        invoke("saveBasicPokeStats", f.handler(), other, 2 * OFFSET);
        assertRawTuple(f, 0x41, 0, 0x22);
        assertEquals(0, f.bytes()[2 * OFFSET + Gen3Constants.bsAbility2Offset]);
    }

    @Test
    void vanillaZeroSlot2StillFallsBackAndHiddenByteIsUntouched() throws Exception {
        Fixture f = fixture(false, 0x41, 0, 0x22);
        assertEquals(2, f.handler().abilitiesPerSpecies());
        assertTuple(f, 0x41, 0, 0);
        save(f);
        reload(f);
        save(f);
        assertTuple(f, 0x41, 0x41, 0);
        assertRawTuple(f, 0x41, 0x41, 0x22);
    }

    private record Fixture(Gen3RomHandler handler, byte[] bytes, Species species) {}

    private static Fixture fixture(boolean cfru, int... abilities) throws Exception {
        byte[] bytes = new byte[4 * OFFSET];
        Arrays.fill(bytes, 0, OFFSET, (byte) 0x5A);
        Arrays.fill(bytes, 3 * OFFSET, bytes.length, (byte) 0x5A);
        for (int row = 1; row <= 2; row++) {
            for (int stat = 0; stat < 6; stat++) bytes[row * OFFSET + stat] = 80;
        }
        for (int slot = 0; slot < 3; slot++) bytes[OFFSET + ABILITY_OFFSETS[slot]] = (byte) abilities[slot];
        return load(cfru, bytes);
    }

    private static Fixture load(boolean cfru, byte[] bytes) throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        setField(handler, "rom", bytes);
        setField(handler, "items", new ArrayList<Item>());
        setField(handler, "useCfruDpeGen9SpeciesCount", cfru);
        Fixture f = new Fixture(handler, bytes, new Species(1));
        reload(f);
        return f;
    }

    private static void reload(Fixture f) throws Exception {
        invoke("loadBasicPokeStats", f.handler(), f.species(), OFFSET);
    }

    private static void save(Fixture f) throws Exception {
        invoke("saveBasicPokeStats", f.handler(), f.species(), OFFSET);
        for (int i = 0; i < OFFSET; i++) {
            assertEquals((byte) 0x5A, f.bytes()[i], "preceding row canary");
            assertEquals((byte) 0x5A, f.bytes()[3 * OFFSET + i], "following row canary");
        }
    }

    private static void assertTuple(Fixture f, int... expected) {
        assertArrayEquals(expected, new int[] {
                f.species().getAbility1(), f.species().getAbility2(), f.species().getAbility3()
        });
    }

    private static void assertRawTuple(Fixture f, int... expected) {
        int[] actual = new int[3];
        for (int i = 0; i < 3; i++) actual[i] = f.bytes()[OFFSET + ABILITY_OFFSETS[i]] & 0xFF;
        assertArrayEquals(expected, actual);
    }

    private static void invoke(String name, Gen3RomHandler handler, Species species, int offset) throws Exception {
        Method method = Gen3RomHandler.class.getDeclaredMethod(name, Species.class, int.class);
        method.setAccessible(true);
        method.invoke(handler, species, offset);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // ROM byte array is inherited.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
