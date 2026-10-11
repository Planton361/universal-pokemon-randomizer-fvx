package com.uprfvx.romio.gbspace;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FreedSpaceTransactionTest {
    static class Ledger extends FreedSpace {
        List<String> chunks() { return freedChunks.stream().map(c -> c.start + ":" + c.end).toList(); }
        void corrupt() { freedChunks.getFirst().end += 100; }
    }
    static class BankLedger extends BankDividedFreedSpace {
        BankLedger() { super(16384, 3, new int[]{1}); }
        List<String> chunks() { return freedChunks.stream().map(c -> c.start + ":" + c.end).toList(); }
    }
    @Test void deepSameInstanceSnapshotCanBeRestoredRepeatedly() {
        Ledger a = new Ledger(); a.free(10, 8); a.free(30, 4);
        var expected = a.chunks(); var snapshot = a.snapshot();
        a.findAndUnfree(3); a.free(40, 7); a.corrupt(); a.restore(snapshot);
        assertEquals(expected, a.chunks()); assertEquals(12, a.getLengthSum());
        a.findAndUnfree(8); a.restore(snapshot); assertEquals(expected, a.chunks());
    }
    @Test void foreignAndNullSnapshotsAreRejectedWithoutMutation() {
        Ledger a = new Ledger(), b = new Ledger(); a.free(10, 8); b.free(40, 3);
        assertThrows(IllegalArgumentException.class, () -> a.restore(b.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> a.restore(null));
        assertEquals(List.of("10:17"), a.chunks());
    }
    @Test void reservationExhaustionRestoresFullChunkOrder() {
        Ledger a = new Ledger(); a.free(10, 8); a.free(30, 4); var before = a.chunks(); var s = a.snapshot();
        assertEquals(10, a.findAndUnfree(8)); assertEquals(-1, a.findAndUnfree(8));
        a.restore(s); assertEquals(before, a.chunks());
    }
    @Test void nestedFailedTransactionsRetainEarlierCommittedAllocation() {
        Ledger a = new Ledger(); a.free(10, 20); var outer = a.snapshot();
        assertEquals(10, a.findAndUnfree(4)); var inner = a.snapshot();
        assertEquals(14, a.findAndUnfree(6)); a.restore(inner);
        assertEquals(List.of("14:29"), a.chunks());
        a.restore(outer); assertEquals(List.of("10:29"), a.chunks());
    }
    @Test void bankSnapshotIncludesReservedBanksAndPreservesBoundaryOrderAndConfiguration() {
        BankLedger a = new BankLedger(); a.free(100, 8); a.free(16384 + 100, 12); a.free(32768 + 100, 6);
        var before = a.chunks(); var s = a.snapshot();
        assertEquals(100, a.findAndUnfree(8)); assertEquals(32768 + 100, a.findAndUnfree(6));
        assertEquals(-1, a.findAndUnfree(1));
        assertEquals(16384 + 100, a.findAndUnfreeInBank(12, 1));
        a.restore(s); assertEquals(before, a.chunks()); assertEquals(26, a.getLengthSum());
        assertEquals(100, a.findAndUnfree(8)); assertEquals(16384 + 100, a.findAndUnfreeInBank(12, 1));
        a.restore(s); assertEquals(before, a.chunks());
    }
}
