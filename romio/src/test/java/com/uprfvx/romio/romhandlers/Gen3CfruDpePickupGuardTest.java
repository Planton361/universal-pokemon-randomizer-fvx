package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpePickupGuardTest {
    @Test
    void rejectsBothEntryPointsBeforeAnyTableAccessEvenWithNoLoadedData() throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        Field profile = Gen3RomHandler.class.getDeclaredField("useCfruDpeGen9SpeciesCount");
        profile.setAccessible(true);
        profile.setBoolean(handler, true);
        // romEntry/rom are not loaded: touching either would fail before the
        // intended diagnostic. Empty writes must not silently claim support.
        RomIOException read = assertThrows(RomIOException.class, handler::getPickupItems);
        RomIOException write = assertThrows(RomIOException.class, () -> handler.setPickupItems(List.of()));
        assertTrue(read.getMessage().contains("Pickup Items to Unchanged"));
        assertEquals(read.getMessage(), write.getMessage());
    }
}
