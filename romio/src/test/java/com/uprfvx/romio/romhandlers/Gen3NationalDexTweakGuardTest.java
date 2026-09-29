package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.MiscTweak;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gen3NationalDexTweakGuardTest {

    private static final int[] CFRU_DPE_RUNNING_DISALLOWED_CAN_RUN_IN_BUILDINGS_PATTERN = new int[] {
            0x10, 0xB5, 0x0A, 0x4B, 0x04, 0x00, 0x0A, 0x48,
            -1, -1, -1, -1,
            0x00, 0x28, 0x01, 0xD1, 0x01, 0x20, 0x10, 0xBD,
            0x20, 0x00, 0x07, 0x4B,
            -1, -1, -1, -1,
            0x00, 0x28, 0xF7, 0xD1,
            0x06, 0x4B, 0xD8, 0x7D, 0x05, 0x38, 0x43, 0x42,
            0x58, 0x41, 0xF2, 0xE7
    };

    private static final int NATIONAL_DEX_SCRIPT_OFFSET = 0x100;
    private static final int NATIONAL_DEX_FLAG_CHECK_OFFSET = 0x200;
    private static final int OAK_LAB_CHECK_OFFSET = 0x220;
    private static final int OAK_OUTSIDE_HOUSE_CHECK_OFFSET = 0x240;
    private static final int OAK_AIDE_CHECK_OFFSET = 0x280;
    private static final int CFRU_DPE_RUNNING_FUNCTION_OFFSET = 0x800;

    @Test
    void detectedCfruDpeGen9BpreHidesNationalDexAndKeepsOtherMiscBits() throws Exception {
        Gen3RomHandler vanilla = fixture(false);
        Gen3RomHandler cfruDpe = fixture(true);

        int vanillaAvailable = vanilla.miscTweaksAvailable();
        int cfruDpeAvailable = cfruDpe.miscTweaksAvailable();

        assertTrue(hasTweak(vanillaAvailable, MiscTweak.NATIONAL_DEX_AT_START));
        assertFalse(hasTweak(cfruDpeAvailable, MiscTweak.NATIONAL_DEX_AT_START));
        assertEquals(vanillaAvailable & ~MiscTweak.NATIONAL_DEX_AT_START.getValue(), cfruDpeAvailable);
    }

    @Test
    void detectedCfruDpeGen9BpreIgnoresStaleNationalDexRequestWithoutScriptWrites() throws Exception {
        Gen3RomHandler cfruDpe = fixture(true);
        byte[] rom = fieldValue(cfruDpe, "rom", byte[].class);
        byte[] before = rom.clone();

        // This direct apply call represents an old serialized bit reaching the patch dispatcher.
        cfruDpe.applyMiscTweak(MiscTweak.NATIONAL_DEX_AT_START);

        // The fixture contains every FRLG script/check signature the generic patch searches for.
        assertArrayEquals(before, rom);
        assertSignatureAt(rom, NATIONAL_DEX_SCRIPT_OFFSET, Gen3Constants.frlgPokedexScriptIdentifier);
        assertSignatureAt(rom, NATIONAL_DEX_FLAG_CHECK_OFFSET, Gen3Constants.frlgNatDexFlagChecker);
        assertSignatureAt(rom, OAK_LAB_CHECK_OFFSET, Gen3Constants.frlgOaksLabKantoDexChecker);
        assertSignatureAt(rom, OAK_OUTSIDE_HOUSE_CHECK_OFFSET, Gen3Constants.frlgOakOutsideHouseCheck);
        assertSignatureAt(rom, OAK_AIDE_CHECK_OFFSET, Gen3Constants.frlgOakAideCheckPrefix);
    }

    @Test
    void vanillaFireRedBpre10StillAdvertisesNationalDex() throws Exception {
        Gen3RomHandler vanilla = fixture(false);
        Gen3RomEntry entry = fieldValue(vanilla, "romEntry", Gen3RomEntry.class);

        assertEquals("BPRE", entry.getRomCode());
        assertEquals(0, entry.getVersion());
        assertTrue(hasTweak(vanilla.miscTweaksAvailable(), MiscTweak.NATIONAL_DEX_AT_START));
    }

    @Test
    void vanillaNationalDexRequestStillRunsTheExistingFrLgPatch() throws Exception {
        Gen3RomHandler vanilla = fixture(false);
        byte[] rom = fieldValue(vanilla, "rom", byte[].class);

        vanilla.applyMiscTweak(MiscTweak.NATIONAL_DEX_AT_START);

        assertEquals(0x04, Byte.toUnsignedInt(rom[NATIONAL_DEX_SCRIPT_OFFSET]));
        assertEquals(0x00, Byte.toUnsignedInt(rom[NATIONAL_DEX_SCRIPT_OFFSET + 5]));
        assertBytesAt(rom, NATIONAL_DEX_FLAG_CHECK_OFFSET, Gen3Constants.frlgE4FlagChecker);
        assertBytesAt(rom, OAK_LAB_CHECK_OFFSET, Gen3Constants.frlgOaksLabFix);
        assertBytesAt(rom, OAK_OUTSIDE_HOUSE_CHECK_OFFSET, Gen3Constants.frlgOakOutsideHouseFix);
        assertEquals(0xE0, Byte.toUnsignedInt(rom[OAK_AIDE_CHECK_OFFSET + 9]));
        assertTrue(indexOf(rom, hexBytes(Gen3Constants.frlgNatDexScript)) >= 0);
    }

    private static Gen3RomHandler fixture(boolean cfruDpeGen9Bpre) throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        byte[] rom = syntheticFrLgBytes();
        Gen3RomEntry entry = fireRedRomEntry();
        entry.putIntValue("RunIndoorsTweakOffset", 0x30);

        setField(handler, "rom", rom);
        setField(handler, "romEntry", entry);
        setField(handler, "useCfruDpeGen9SpeciesCount", cfruDpeGen9Bpre);
        handler.freeSpace(0x1000, 0x80);
        return handler;
    }

    private static byte[] syntheticFrLgBytes() {
        byte[] rom = new byte[0x20000];
        Arrays.fill(rom, Gen3Constants.freeSpaceByte);
        writeHex(rom, NATIONAL_DEX_SCRIPT_OFFSET, Gen3Constants.frlgPokedexScriptIdentifier);
        writeHex(rom, NATIONAL_DEX_FLAG_CHECK_OFFSET, Gen3Constants.frlgNatDexFlagChecker);
        writeHex(rom, OAK_LAB_CHECK_OFFSET, Gen3Constants.frlgOaksLabKantoDexChecker);
        writeHex(rom, OAK_OUTSIDE_HOUSE_CHECK_OFFSET, Gen3Constants.frlgOakOutsideHouseCheck);
        writeHex(rom, OAK_AIDE_CHECK_OFFSET, Gen3Constants.frlgOakAideCheckPrefix);
        for (int i = 0; i < CFRU_DPE_RUNNING_DISALLOWED_CAN_RUN_IN_BUILDINGS_PATTERN.length; i++) {
            int value = CFRU_DPE_RUNNING_DISALLOWED_CAN_RUN_IN_BUILDINGS_PATTERN[i];
            rom[CFRU_DPE_RUNNING_FUNCTION_OFFSET + i] = (byte) (value < 0 ? 0 : value);
        }
        return rom;
    }

    private static Gen3RomEntry fireRedRomEntry() throws Exception {
        for (Gen3RomEntry entry : Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini")) {
            if ("Fire Red (U) 1.0".equals(entry.getName())) {
                return new Gen3RomEntry(entry);
            }
        }
        throw new IllegalStateException("Fire Red (U) 1.0 ROM entry not found");
    }

    private static boolean hasTweak(int available, MiscTweak tweak) {
        return (available & tweak.getValue()) != 0;
    }

    private static void assertSignatureAt(byte[] bytes, int offset, String signature) {
        assertBytesAt(bytes, offset, signature);
    }

    private static void assertBytesAt(byte[] bytes, int offset, String hex) {
        assertArrayEquals(hexBytes(hex), Arrays.copyOfRange(bytes, offset, offset + hex.length() / 2));
    }

    private static void writeHex(byte[] bytes, int offset, String hex) {
        byte[] decoded = hexBytes(hex);
        System.arraycopy(decoded, 0, bytes, offset, decoded.length);
    }

    private static byte[] hexBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        for (int offset = 0; offset <= bytes.length - needle.length; offset++) {
            boolean match = true;
            for (int i = 0; i < needle.length; i++) {
                if (bytes[offset + i] != needle[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return offset;
            }
        }
        return -1;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T fieldValue(Object target, String name, Class<T> fieldType) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return fieldType.cast(field.get(target));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
