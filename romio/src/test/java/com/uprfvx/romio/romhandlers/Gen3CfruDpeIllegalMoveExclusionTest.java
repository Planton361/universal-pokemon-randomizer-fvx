package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free fixtures for the already detected profile, without loading game artifacts. */
class Gen3CfruDpeIllegalMoveExclusionTest {
    @Test
    void detectedProfileExcludesExactlyTheSourceDefinedSystemMoveBlock() throws Exception {
        List<Integer> illegal = handler(true, "BPRE").getIllegalMoves();
        assertEquals(156, illegal.size());
        assertEquals(156, new HashSet<>(illegal).size());
        assertEquals(IntStream.rangeClosed(0x2FF, 0x39A).boxed().toList(), illegal);
        for (int boundary : new int[] {0x2FF, 0x333, 0x334, 0x358, 0x359, 0x39A}) {
            assertTrue(illegal.contains(boundary), "Missing boundary " + boundary);
        }
        assertFalse(illegal.contains(0x2FE));
        assertFalse(illegal.contains(0x39B));
    }

    @Test
    void vanillaAndNonCfruProfilesRetainEmptyDefault() throws Exception {
        assertEquals(List.of(), handler(false, "BPRE").getIllegalMoves());
        assertEquals(List.of(), handler(false, "BPEE").getIllegalMoves());
        assertEquals(List.of(), handler(true, "BPEE").getIllegalMoves());
        assertEquals(List.of(), new Gen3RomHandler().getIllegalMoves());
    }

    private static Gen3RomHandler handler(boolean detected, String code) throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        var constructor = Gen3RomEntry.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Gen3RomEntry entry = constructor.newInstance("synthetic profile");
        entry.setRomCode(code);
        setField(handler, "romEntry", entry);
        setField(handler, "useCfruDpeGen9SpeciesCount", detected);
        return handler;
    }

    private static void setField(Gen3RomHandler handler, String name, Object value) throws Exception {
        Field field = Gen3RomHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
