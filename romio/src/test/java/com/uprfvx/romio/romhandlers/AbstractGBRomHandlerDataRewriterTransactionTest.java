package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gbspace.FreedSpace;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual shared DataRewriter and selected Gen3 trainer dispatch, entirely synthetic. */
class AbstractGBRomHandlerDataRewriterTransactionTest {
    static final int PRIMARY = 32, SECONDARY = 40, OLD = 128;
    static final byte[] DATA = {1,2,3,4,5,6,7,8,9,10,11,12};
    enum Fault { WRITE, CORRUPT, BEFORE_PUBLICATION, PRIMARY, SECONDARY, POINTER_READBACK, BEFORE_RELEASE, OLD_RELEASED }
    static class Fixture extends Gen3RomHandler {
        Fault fault;
        int expectedOriginal = OLD;
        Fixture(boolean free) {
            rom = new byte[1024]; Arrays.fill(rom, (byte) 0x5A);
            Arrays.fill(rom, OLD, OLD+8, (byte) 0x33);
            writePointer(PRIMARY, OLD); writePointer(SECONDARY, OLD);
            if (free) freeSpace(257, 64);
        }
        void rewrite() { new DataRewriter<byte[]>().rewriteData(PRIMARY, DATA, new int[]{SECONDARY, PRIMARY}, b -> b, p -> 8); }
        @Override protected void writeRepointPayload(int offset, byte[] data) {
            assertEquals(expectedOriginal, readPointer(PRIMARY)); assertEquals(expectedOriginal, readPointer(SECONDARY));
            if (fault == Fault.WRITE) { rom[offset] = 1; throw new RomIOException("injected payload write"); }
            super.writeRepointPayload(offset, data);
            if (fault == Fault.CORRUPT) rom[offset+2] ^= 1;
        }
        @Override protected void writePointer(int offset, int target) {
            if ((fault == Fault.PRIMARY && offset == PRIMARY) || (fault == Fault.SECONDARY && offset == SECONDARY)) {
                rom[offset] = (byte) target; throw new RomIOException("injected partial pointer");
            }
            super.writePointer(offset, target);
            if (fault == Fault.POINTER_READBACK && offset == SECONDARY) rom[offset] ^= 1;
        }
        @Override protected void dataRewriteCheckpoint(DataRewritePhase phase, int offset) {
            if ((fault == Fault.BEFORE_PUBLICATION && phase == DataRewritePhase.BEFORE_PUBLICATION)
                    || (fault == Fault.BEFORE_RELEASE && phase == DataRewritePhase.BEFORE_RELEASE)
                    || (fault == Fault.OLD_RELEASED && phase == DataRewritePhase.OLD_RELEASED)) {
                throw new RomIOException("injected phase " + phase);
            }
        }
    }
    @ParameterizedTest(name="{displayName} [{index}] {0}") @EnumSource(Fault.class)
    void everyFaultRestoresAllBytesAndLedgerThenRetrySucceeds(Fault fault) {
        Fixture f = new Fixture(true); byte[] before = f.rom.clone(); String ledger = f.getFreedSpace().toString();
        f.fault = fault; assertThrows(RomIOException.class, f::rewrite);
        assertArrayEquals(before, f.rom); assertEquals(ledger, f.getFreedSpace().toString());
        f.fault = null; f.rewrite();
        int target = f.readPointer(PRIMARY); assertEquals(260, target); assertEquals(target, f.readPointer(SECONDARY));
        assertArrayEquals(DATA, Arrays.copyOfRange(f.rom,target,target+DATA.length));
        assertArrayEquals(before, outside(f.rom, before, target, DATA.length, OLD, 8, PRIMARY, 4, SECONDARY, 4));
        for (int i=OLD;i<OLD+8;i++) assertEquals((byte)0xFF, f.rom[i]);
    }
    static byte[] outside(byte[] after, byte[] before, int... ranges) {
        byte[] actual=after.clone(); for(int i=0;i<ranges.length;i+=2)
            System.arraycopy(before,ranges[i],actual,ranges[i],ranges[i+1]); return actual;
    }
    @Test void largerPayloadWithNoSpaceKeepsOldReachableAndLedgerExact() {
        Fixture f = new Fixture(false); byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        assertThrows(RomIOException.class,f::rewrite); assertArrayEquals(before,f.rom);
        assertEquals(OLD,f.readPointer(PRIMARY)); assertEquals(ledger,f.getFreedSpace().toString());
        f.freeSpace(257,64); f.rewrite(); assertEquals(260,f.readPointer(PRIMARY));
    }
    @Test void staleCandidateThenFaultRestoresEvenDiscardedRangesAndSuccessRetainsGenericSemantics() {
        Fixture f = new Fixture(true); f.getFreedSpace().free(200,16); // Occupied bytes, listed free.
        byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        f.fault=Fault.BEFORE_PUBLICATION; assertThrows(RomIOException.class,f::rewrite);
        assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
        f.fault=null; f.rewrite(); assertFalse(f.getFreedSpace().toString().contains("c8-d7"));
        assertEquals((byte)0x5A,f.rom[200]); // Separate stale-success leak deliberately unchanged.
    }
    @Test void staleCandidateExhaustionRestoresLedger() {
        Fixture f = new Fixture(false); f.getFreedSpace().free(200,16);
        byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        assertThrows(RomIOException.class,f::rewrite); assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
    }
    @Test void invalidPrimaryRejectedBeforeAnyMutation() {
        Fixture f = new Fixture(true); f.rom[PRIMARY+3]=0; byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        assertThrows(IllegalArgumentException.class,f::rewrite); assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
    }
    @Test void outOfBoundsOwnerAndOldExtentAndDivergentSecondaryRejectWithoutMutation() {
        for(int scenario=0;scenario<4;scenario++) {
            Fixture f=new Fixture(true); if(scenario==3) f.writePointer(SECONDARY,OLD+1);
            byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString(); int which=scenario;
            assertThrows(RomIOException.class,()->f.new DataRewriter<byte[]>().rewriteData(
                    which==0?1022:PRIMARY,DATA,new int[]{SECONDARY},b->b,p->which==1?Integer.MAX_VALUE:which==2?0:8));
            assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
        }
    }
    @Test void overlappingPointerOwnersAreRejected() {
        Fixture f=new Fixture(true); byte[] before=f.rom.clone();
        assertThrows(RomIOException.class,()->f.new DataRewriter<byte[]>().rewriteData(PRIMARY,DATA,new int[]{PRIMARY+1},b->b,p->8));
        assertArrayEquals(before,f.rom);
    }
    @ParameterizedTest(name="{displayName} [{index}] {0}") @EnumSource(Fault.class)
    void inPlacePlanningRollbackAndRetryAreExact(Fault fault) {
        Fixture f=new Fixture(false); f.fault=fault;
        byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        byte[] smaller={9,8,7,6};
        Runnable rewrite=()->f.new DataRewriter<byte[]>().rewriteData(PRIMARY,smaller,new int[]{SECONDARY},b->b,p->8);
        assertThrows(RomIOException.class,rewrite::run); assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
        f.fault=null; rewrite.run(); assertEquals(OLD,f.readPointer(PRIMARY));
        assertArrayEquals(smaller,Arrays.copyOfRange(f.rom,OLD,OLD+4));
        assertEquals(4,f.getFreedSpace().getLengthSum());
    }
    @Test void releasedOldRangeCanMergeWithAdjacentFreeSpaceWithoutChangingNeighborBytes() {
        Fixture f=new Fixture(false); f.freeSpace(OLD+8,8); byte[] before=f.rom.clone();
        f.rewrite(); assertEquals(OLD,f.readPointer(PRIMARY)); assertEquals(before[OLD-1],f.rom[OLD-1]);
        assertEquals(before[OLD+16],f.rom[OLD+16]); assertArrayEquals(DATA,Arrays.copyOfRange(f.rom,OLD,OLD+12));
    }
    @ParameterizedTest(name="{displayName} [{index}] residue={0}") @CsvSource({"0","1","2","3"})
    void allAllocationAlignmentsPreserveForeignAndPaddingBytes(int residue) {
        Fixture f=new Fixture(false);f.freeSpace(256+residue,64);byte[] before=f.rom.clone();f.rewrite();
        int target=f.readPointer(PRIMARY);assertEquals(0,target%4);
        assertArrayEquals(before,outside(f.rom,before,target,DATA.length,OLD,8,PRIMARY,4,SECONDARY,4));
    }
    @Test void laterFailedInvocationPreservesEarlierCommittedTransaction() {
        Fixture f=new Fixture(true); f.rewrite(); byte[] committed=f.rom.clone(); String ledger=f.getFreedSpace().toString();
        f.expectedOriginal=f.readPointer(PRIMARY); f.fault=Fault.WRITE;
        assertThrows(RomIOException.class,()->f.new DataRewriter<byte[]>().rewriteData(PRIMARY,DATA,new int[]{SECONDARY},b->b,p->12));
        assertArrayEquals(committed,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
    }
    @Test void actualSelectedGen3TrainerRowFailureIsAtomicThenSaveRetryUsesRealSerializer() throws Exception {
        Fixture f=new Fixture(true); Gen3RomEntry entry=new Gen3RomEntry(Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        entry.setRomType(Gen3Constants.RomType_FRLG); entry.putIntValue("TrainerData",400); entry.putIntValue("TrainerEntrySize",40);
        entry.putIntValue("TrainerCount",2); entry.putIntValue("TrainerNameLength",12);
        set(f,"romEntry",entry); set(f,"useCfruDpeGen9SpeciesCount",true);
        int row=440, ptr=row+36;
        Arrays.fill(f.rom,row,row+40,(byte)0); f.rom[row+32]=1; f.writePointer(ptr,OLD);
        int[] ids=new int[200]; ids[25]=25; set(f,"pokedexToInternal",ids);
        Trainer tr=new Trainer(); tr.setName("Test"); tr.setIndex(1);
        TrainerPokemon tp=new TrainerPokemon(); tp.setSpecies(new Species(25)); tp.setLevel(12); tp.setMoves(new int[4]); tr.getPokemon().add(tp);
        set(f,"trainers",new ArrayList<>(List.of(tr))); f.initTextTables();
        byte[] before=f.rom.clone(); String ledger=f.getFreedSpace().toString(); f.fault=Fault.BEFORE_PUBLICATION;
        assertThrows(RomIOException.class,f::saveTrainers); assertArrayEquals(before,f.rom); assertEquals(ledger,f.getFreedSpace().toString());
        f.fault=null; f.saveTrainers(); int target=f.readPointer(ptr);
        assertEquals(12,f.readWord(target+2)); assertEquals(25,f.readWord(target+4)); assertEquals(1,f.rom[row+32]);
    }
    static void set(Object target,String name,Object value) throws Exception {
        Class<?> type=target.getClass(); while(type!=null) { try { Field field=type.getDeclaredField(name); field.setAccessible(true); field.set(target,value); return; } catch(NoSuchFieldException e) { type=type.getSuperclass(); } }
        throw new NoSuchFieldException(name);
    }
}
