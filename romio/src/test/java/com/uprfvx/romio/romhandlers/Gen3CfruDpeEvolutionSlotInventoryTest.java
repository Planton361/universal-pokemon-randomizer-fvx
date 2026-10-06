package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.Evolution;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpeEvolutionSlotInventoryTest {
    @Test
    void completeExactSourceInventoryMatchesProductionAdapterAndUnchangedWriter() throws Exception {
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        var rows = CfruDpeEvolutionFixture.inventory();
        assertEquals(785, rows.size());
        assertEquals(785, rows.stream().map(s -> s.source() + ":" + s.slot()).distinct().count());
        var histogram = rows.stream().collect(Collectors.groupingBy(CfruDpeEvolutionFixture.SourceSlot::method, Collectors.counting()));
        assertEquals(79, rows.stream().filter(s -> s.method() >= 16 && s.method() <= 42).count());
        assertEquals(89, histogram.get(253)); assertEquals(104, histogram.get(254));
        assertFalse(histogram.containsKey(29)); assertFalse(histogram.containsKey(33)); assertFalse(histogram.containsKey(15));
        var graph = f.getTargetOnlyEvolutionGraph();
        assertEquals(592, graph.values().stream().mapToInt(List::size).sum());
        for (var row : rows) {
            List<Evolution> edges = graph.get(f.species[row.source()]);
            var owned = edges.stream().filter(e -> e.getExtraInfo() == row.slot()).toList();
            if (row.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE")) {
                assertEquals(1, owned.size(), row.toString());
                assertSame(f.species[row.target()], owned.getFirst().getTo());
            } else {
                assertEquals("TRANSFORMATION_PRESERVE_RAW", row.disposition()); assertTrue(owned.isEmpty());
            }
        }
        int empty = 0;
        for (int id = 0; id < 1440; id++) for (int slot = 0; slot < 16; slot++) if (f.word(id, slot, 0) == 0) empty++;
        assertEquals(22255, empty); // Full 1440 x 16 table, including source-zero placeholder.
        byte[] before = f.memory.clone(); f.write(); f.loadEvolutions(); f.write();
        assertArrayEquals(before, f.memory);
    }
}
