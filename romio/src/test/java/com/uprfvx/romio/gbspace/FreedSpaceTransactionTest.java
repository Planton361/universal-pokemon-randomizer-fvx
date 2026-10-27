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
    private static FreedSpace.ProtectedRange owner(String type,int start,int end) {
        return new FreedSpace.ProtectedRange(type,start,end);
    }

    @Test void protectionCarvesExistingLedgerAndStaleSnapshotsCannotRestoreIt() {
        Ledger a=new Ledger();a.free(10,30);var stale=a.snapshot();
        a.protectRanges(List.of(owner("OAK_CONSUMER",20,30)));
        assertEquals(List.of("10:19","30:39"),a.chunks());
        assertEquals(10,a.findAndUnfree(10));assertEquals(30,a.findAndUnfree(10));assertEquals(-1,a.findAndUnfree(1));
        a.restore(stale);assertEquals(List.of("10:19","30:39"),a.chunks());
        assertThrows(IllegalStateException.class,()->a.free(19,2));
        assertThrows(IllegalStateException.class,()->a.free(20,10));
        assertEquals(List.of("10:19","30:39"),a.chunks());
        assertEquals(10,a.findAndUnfree(10));assertEquals(30,a.findAndUnfree(10));
    }

    @Test void invalidAndOverlappingBatchDoesNotPartiallyProtectOrCarve() {
        Ledger a=new Ledger();a.free(0,100);var before=a.chunks();
        assertThrows(IllegalArgumentException.class,()->new FreedSpace.ProtectedRange("",1,2));
        assertThrows(IllegalArgumentException.class,()->new FreedSpace.ProtectedRange("OAK",-1,2));
        assertThrows(IllegalArgumentException.class,()->new FreedSpace.ProtectedRange("OAK",2,2));
        assertThrows(IllegalArgumentException.class,()->a.protectRanges(null));
        assertThrows(IllegalArgumentException.class,()->a.protectRanges(List.of()));
        assertThrows(IllegalArgumentException.class,()->a.protectRanges(java.util.Arrays.asList(owner("OAK_A",10,20),null)));
        assertThrows(IllegalArgumentException.class,()->a.protectRanges(List.of(owner("OAK_A",10,20),owner("OAK_B",19,25))));
        assertEquals(before,a.chunks());assertEquals(0,a.findAndUnfree(100));
        assertThrows(IllegalArgumentException.class,()->a.restore(new Ledger().snapshot()));
    }

    @Test void activeReservationsPreventAtomicProtectionUntilReleased() {
        Ledger a=new Ledger();a.free(10,30);assertEquals(10,a.findAndUnfree(8));var before=a.chunks();
        assertThrows(IllegalStateException.class,()->a.protectRanges(List.of(owner("OAK_A",25,30),owner("OAK_B",12,16))));
        assertEquals(before,a.chunks());a.free(10,8);a.protectRanges(List.of(owner("OAK_B",12,16)));
        assertEquals(List.of("10:11","16:39"),a.chunks());
    }

    @Test void staleSnapshotWithFormerReservationCannotReinstateProtectedBytes() {
        Ledger a=new Ledger();a.free(10,30);assertEquals(10,a.findAndUnfree(10));var old=a.snapshot();
        a.free(10,10);a.protectRanges(List.of(owner("OAK_DATA",10,20)));a.restore(old);
        assertThrows(IllegalStateException.class,()->a.free(10,10));
        assertEquals(List.of("20:39"),a.chunks());assertEquals(20,a.findAndUnfree(20));
    }

    @Test void multipleRegistrationsAreAtomicAndPermitAdjacentOwners() {
        Ledger a=new Ledger();a.free(0,100);a.protectRanges(List.of(owner("OAK_A",10,20)));
        var before=a.chunks();
        assertThrows(IllegalArgumentException.class,()->a.protectRanges(List.of(owner("OAK_B",30,40),owner("OAK_C",19,21))));
        assertEquals(before,a.chunks());a.protectRanges(List.of(owner("OAK_B",20,30)));
        assertEquals(List.of("0:9","30:99"),a.chunks());
        assertThrows(IllegalStateException.class,()->a.free(19,2));
        assertThrows(IllegalArgumentException.class,()->a.free(-1,2));
        assertThrows(IllegalArgumentException.class,()->a.free(Integer.MAX_VALUE,2));
        assertThrows(IllegalArgumentException.class,()->a.findAndUnfree(0));
    }

    @Test void rollbackOfFailedWriteRetainsProtectionAndRestoresAdjacentBytesAndLedger() {
        byte[] memory=new byte[100];java.util.Arrays.fill(memory,(byte)0xff);byte[] before=memory.clone();
        Ledger a=new Ledger();a.free(10,30);var stale=a.snapshot();a.protectRanges(List.of(owner("OAK_CODE",20,30)));
        var protectedSnapshot=a.snapshot();int offset=a.findAndUnfree(10);
        try {java.util.Arrays.fill(memory,offset,offset+10,(byte)7);throw new IllegalStateException("synthetic write failure");}
        catch(IllegalStateException failure) {System.arraycopy(before,0,memory,0,memory.length);a.restore(protectedSnapshot);}
        assertArrayEquals(before,memory);assertEquals(List.of("10:19","30:39"),a.chunks());
        a.restore(stale);assertEquals(List.of("10:19","30:39"),a.chunks());
        assertEquals(10,a.findAndUnfree(10));assertEquals(30,a.findAndUnfree(10));
    }

    @Test void protectionWorksAcrossBankedAllocatorAndNormalLegacyBehaviorRemains() {
        BankLedger a=new BankLedger();a.free(100,20);a.free(16484,20);var stale=a.snapshot();
        a.protectRanges(List.of(owner("OAK_CODE",105,115),owner("OAK_DATA",16489,16499)));
        assertEquals(List.of("100:104","115:119","16484:16488","16499:16503"),a.chunks());
        assertEquals(100,a.findAndUnfree(5));assertEquals(115,a.findAndUnfree(5));
        assertEquals(16484,a.findAndUnfreeInBank(5,1));assertEquals(16499,a.findAndUnfreeInBank(5,1));
        a.restore(stale);assertEquals(List.of("100:104","115:119","16484:16488","16499:16503"),a.chunks());
        Ledger legacy=new Ledger();legacy.free(10,30);assertEquals(10,legacy.findAndUnfree(20));
        legacy.free(10,20);assertEquals(List.of("10:39"),legacy.chunks());
    }
}
