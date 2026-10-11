package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.GBConstants;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gbspace.BankDividedFreedSpace;
import com.uprfvx.romio.graphics.images.GBCImage;
import com.uprfvx.romio.romhandlers.romentries.Gen2RomEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** All three real bank variants on actual Gen1/Gen2 handlers and real Gen2 image codec. */
class AbstractGBCRomHandlerDataRewriterTransactionTest {
    static final int B=GBConstants.bankSize, PTR=B+32, SECOND=B+40, OLD=B+128, SELECTOR=100, SELECTOR2=104;
    static final byte[] DATA={1,2,3,4,5,6,7,8,9,10,11,12};
    static class State {
        final BankDividedFreedSpace ledger=new BankDividedFreedSpace(B,64,new int[]{1});
        String fault;
        int publications;
        int faultOffset;
        void checkpoint(AbstractGBRomHandler.DataRewritePhase phase, int offset) {
            if(phase==AbstractGBRomHandler.DataRewritePhase.POINTER_WRITTEN) publications++;
            if(fault!=null && phase.name().equals(fault) && (faultOffset == 0 || faultOffset == offset)) throw new RomIOException("injected "+fault);
        }
    }
    interface Controlled { State state(); }
    static class Gen1Fixture extends Gen1RomHandler implements Controlled {
        State s=new State(); public State state(){return s;}
        @Override protected BankDividedFreedSpace getFreedSpace(){return s.ledger;}
        @Override protected void dataRewriteCheckpoint(DataRewritePhase phase,int offset){s.checkpoint(phase,offset);}
        @Override protected void writeRepointPayload(int offset,byte[] data){
            if ("WRITE".equals(s.fault)) {rom[offset]=1;throw new RomIOException("partial payload");}
            super.writeRepointPayload(offset,data);
            if ("CORRUPT".equals(s.fault)) rom[offset+1]^=1;
        }
    }
    static class Gen2Fixture extends Gen2RomHandler implements Controlled {
        @Override protected void writePointer(int offset,int target){
            if ("IMAGE_POINTER_PARTIAL".equals(s.fault) && offset==201) {
                rom[offset]=(byte)target;throw new RomIOException("partial image pointer");
            }
            super.writePointer(offset,target);
        }
        State s=new State(); public State state(){return s;}
        @Override protected BankDividedFreedSpace getFreedSpace(){return s.ledger;}
        @Override protected void dataRewriteCheckpoint(DataRewritePhase phase,int offset){s.checkpoint(phase,offset);}
        @Override protected void writeRepointPayload(int offset,byte[] data){
            if ("WRITE".equals(s.fault)) {rom[offset]=1;throw new RomIOException("partial payload");}
            super.writeRepointPayload(offset,data);
            if ("CORRUPT".equals(s.fault)) rom[offset+1]^=1;
        }
    }
    static AbstractGBCRomHandler fixture(int generation) {
        AbstractGBCRomHandler h=generation==1?new Gen1Fixture():new Gen2Fixture();
        h.rom=new byte[64*B]; Arrays.fill(h.rom,(byte)0x5A);
        h.writePointer(PTR,OLD); h.writePointer(SECOND,OLD);
        h.rom[SELECTOR]=1;h.rom[SELECTOR2]=1;
        Arrays.fill(h.rom,OLD,OLD+8,(byte)0x33); return h;
    }
    static State state(AbstractGBCRomHandler h){return ((Controlled)h).state();}
    static AbstractGBRomHandler.DataRewriter<byte[]> writer(AbstractGBCRomHandler h,String variant) {
        return switch(variant) {
            case "fixed"->h.new SpecificBankDataRewriter<byte[]>(1);
            case "same"->h.new SameBankDataRewriter<byte[]>();
            case "indirect"->h.new IndirectBankDataRewriter<byte[]>(new int[]{SELECTOR,SELECTOR2});
            default->throw new IllegalArgumentException();
        };
    }
    static void rewrite(AbstractGBCRomHandler h,String variant){writer(h,variant).rewriteData(PTR,DATA,new int[]{SECOND},b->b,p->8);}
    static int target(AbstractGBCRomHandler h,String variant) {
        return h.readPointer(PTR,variant.equals("indirect")?h.rom[SELECTOR]&0xFF:1);
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}")
    @CsvSource({"1,fixed","1,same","1,indirect","2,fixed","2,same","2,indirect"})
    void variantRespectsBankPriorityEncodingAndForeignBytes(int generation,String variant) {
        AbstractGBCRomHandler h=fixture(generation);
        int expected=variant.equals("indirect")?2*B+200:B+200;
        h.freeSpace(expected,32); if(variant.equals("indirect"))h.freeSpace(B+200,32);
        byte[] before=h.rom.clone(); rewrite(h,variant); assertEquals(expected,target(h,variant));
        assertEquals(h.makeGBPointer(expected),h.readWord(PTR)); assertEquals(h.readWord(PTR),h.readWord(SECOND));
        assertArrayEquals(DATA,Arrays.copyOfRange(h.rom,expected,expected+12));
        assertArrayEquals(before,AbstractGBRomHandlerDataRewriterTransactionTest.outside(h.rom,before,expected,12,OLD,8,PTR,2,SECOND,2,SELECTOR,1,SELECTOR2,1));
        if(variant.equals("indirect")) {assertEquals(2,h.rom[SELECTOR]);assertEquals(2,h.rom[SELECTOR2]);}
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}/{2}")
    @CsvSource({"1,fixed,BEFORE_PUBLICATION","1,same,POINTER_WRITTEN","1,indirect,BANK_WRITTEN",
                "2,fixed,BEFORE_PUBLICATION","2,same,POINTER_WRITTEN","2,indirect,BANK_WRITTEN",
                "1,indirect,OLD_RELEASED","2,indirect,OLD_RELEASED"})
    void partialPublicationAndBankFaultRollbackIncludingReservedChunksThenRetry(int generation,String variant,String fault) {
        AbstractGBCRomHandler h=fixture(generation);int candidate=variant.equals("indirect")?2*B+200:B+200;
        h.freeSpace(candidate,32);h.freeSpace(3*B+200,8); if(variant.equals("indirect"))h.freeSpace(B+200,32);
        byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();state(h).fault=fault;
        var dw=writer(h,variant); assertThrows(RomIOException.class,()->dw.rewriteData(PTR,DATA,new int[]{SECOND},b->b,p->8));
        assertArrayEquals(before,h.rom);assertEquals(ledger,h.getFreedSpace().toString());
        state(h).fault=null;dw.rewriteData(PTR,DATA,new int[]{SECOND},b->b,p->8);assertEquals(candidate,target(h,variant));
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}")
    @CsvSource({"1,fixed","1,same","1,indirect","2,fixed","2,same","2,indirect"})
    void exhaustedBankAllocationPreservesOriginalAndReservedLedger(int generation,String variant) {
        AbstractGBCRomHandler h=fixture(generation);h.freeSpace(3*B+200,4);byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();
        assertThrows(RomIOException.class,()->rewrite(h,variant));assertArrayEquals(before,h.rom);assertEquals(ledger,h.getFreedSpace().toString());
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}")
    @CsvSource({"1,fixed","1,same","1,indirect","2,fixed","2,same","2,indirect"})
    void oldRangeFallbackKeepsCorrectBankAndNeighborBytes(int generation,String variant) {
        AbstractGBCRomHandler h=fixture(generation);byte[] before=h.rom.clone();byte[] smaller={9,8,7,6};
        writer(h,variant).rewriteData(PTR,smaller,new int[]{SECOND},b->b,p->8);
        assertEquals(OLD,target(h,variant)); assertArrayEquals(smaller,Arrays.copyOfRange(h.rom,OLD,OLD+4));
        assertEquals(before[OLD-1],h.rom[OLD-1]);assertEquals(before[OLD+8],h.rom[OLD+8]);assertEquals(4,h.getFreedSpace().getLengthSum());
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}")
    @CsvSource({"1,same","1,indirect","2,same","2,indirect"})
    void bankZeroAndReservedFallbackRemainCompatible(int generation,String variant) {
        AbstractGBCRomHandler h=fixture(generation); h.freeSpace(300,32);
        rewrite(h,variant);assertEquals(300,target(h,variant));
        assertEquals(h.makeGBPointer(300),h.readWord(PTR));
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}") @CsvSource({"1","2"})
    void staleBankCandidateRestoredAfterLaterFault(int generation) {
        AbstractGBCRomHandler h=fixture(generation);h.getFreedSpace().free(B+160,16);h.freeSpace(B+200,32);h.freeSpace(2*B+200,7);
        byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();state(h).fault="BEFORE_PUBLICATION";
        assertThrows(RomIOException.class,()->rewrite(h,"fixed"));assertArrayEquals(before,h.rom);assertEquals(ledger,h.getFreedSpace().toString());
        state(h).fault=null;rewrite(h,"fixed");assertEquals(B+200,target(h,"fixed"));
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}/{1}/{2}")
    @CsvSource({"1,fixed,WRITE","1,same,CORRUPT","1,indirect,WRITE","2,fixed,CORRUPT","2,same,WRITE","2,indirect,CORRUPT"})
    void bankedPayloadFailurePrecedesAllPublicationAndRestoresBytes(int generation,String variant,String fault) {
        AbstractGBCRomHandler h=fixture(generation);int candidate=variant.equals("indirect")?2*B+200:B+200;
        h.freeSpace(candidate,32);byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();state(h).fault=fault;
        assertThrows(RomIOException.class,()->rewrite(h,variant));assertArrayEquals(before,h.rom);
        assertEquals(ledger,h.getFreedSpace().toString());assertEquals(0,state(h).publications);
        state(h).fault=null;rewrite(h,variant);assertEquals(candidate,target(h,variant));
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}") @CsvSource({"1","2"})
    void secondBankSelectorFaultAndReservedFallbackRestoreExactState(int generation) {
        AbstractGBCRomHandler h=fixture(generation);h.freeSpace(B+200,32);
        byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();state(h).fault="BANK_WRITTEN";state(h).faultOffset=SELECTOR2;
        assertThrows(RomIOException.class,()->rewrite(h,"indirect"));assertArrayEquals(before,h.rom);assertEquals(ledger,h.getFreedSpace().toString());
        state(h).fault=null;rewrite(h,"indirect");assertEquals(B+200,target(h,"indirect"));
    }
    @ParameterizedTest(name="{displayName} [{index}] gen{0}") @CsvSource({"1","2"})
    void malformedEncodingAndCrossBankOldPayloadRejectBeforeReservation(int generation) {
        AbstractGBCRomHandler h=fixture(generation);h.rom[PTR+1]=(byte)0x80;byte[] before=h.rom.clone();
        assertThrows(RomIOException.class,()->rewrite(h,"same"));assertArrayEquals(before,h.rom);
        h.writePointer(PTR,2*B-4);before=h.rom.clone();
        assertThrows(RomIOException.class,()->writer(h,"same").rewriteData(PTR,DATA,b->b,p->8));assertArrayEquals(before,h.rom);
    }
    @Test void mismatchedSecondaryBankAndChangedOrOverlappingSelectorFailClosed() {
        for(int scenario=0;scenario<3;scenario++) {
            AbstractGBCRomHandler h=fixture(1);h.freeSpace(2*B+200,32);var dw=writer(h,"indirect");
            if(scenario==0)h.writePointer(SECOND,OLD+1);if(scenario==1)h.rom[SELECTOR2]=2;
            byte[] before=h.rom.clone();int which=scenario;
            assertThrows(RomIOException.class,()->{
                if(which==2)h.new IndirectBankDataRewriter<byte[]>(new int[]{PTR}).rewriteData(PTR,DATA,b->b,p->8);
                else dw.rewriteData(PTR,DATA,new int[]{SECOND},b->b,p->8);
            });assertArrayEquals(before,h.rom);
        }
    }
    @ParameterizedTest(name="{displayName} [{index}] crystal={0},bank={1}")
    @CsvSource({"false,31","false,32","false,46","true,54","true,55","true,0"})
    void actualGen2ThreeByteImageWriterRollsBackCompleteFieldAndRetries(boolean crystal,int bank) throws Exception {
        Gen2Fixture h=(Gen2Fixture)fixture(2);
        Gen2RomEntry entry=new Gen2RomEntry(Gen2RomEntry.READER.readEntriesFromFile("gen2_offsets.ini").getFirst());
        entry.setRomType(crystal ? com.uprfvx.romio.constants.Gen2Constants.Type_Crystal : com.uprfvx.romio.constants.Gen2Constants.Type_GS);AbstractGBRomHandlerDataRewriterTransactionTest.set(h,"romEntry",entry);
        int imagePointer=200, old=bank*B+400, replacement=(bank==0?0:bank+1)*B+600;
        Method write=Gen2RomHandler.class.getDeclaredMethod("writePokemonOrTrainerImagePointer",int.class,int.class);write.setAccessible(true);
        Method read=Gen2RomHandler.class.getDeclaredMethod("readPokemonOrTrainerImagePointer",int.class);read.setAccessible(true);
        Method rewrite=Gen2RomHandler.class.getDeclaredMethod("rewritePokemonOrTrainerImage",int.class,GBCImage.class);rewrite.setAccessible(true);
        // Gen2 compressed terminator: a valid one-byte old payload.
        h.rom[old]=(byte)0xFF;write.invoke(h,imagePointer,old);h.freeSpace(replacement,64);
        GBCImage image=new GBCImage.Builder(8,8,GBCImage.DEFAULT_PALETTE).build(); // Synthetic blank tile.
        byte[] before=h.rom.clone();String ledger=h.getFreedSpace().toString();h.s.fault="IMAGE_POINTER_PARTIAL";
        InvocationTargetException failure=assertThrows(InvocationTargetException.class,()->rewrite.invoke(h,imagePointer,image));
        assertInstanceOf(RomIOException.class,failure.getCause());assertArrayEquals(before,h.rom);assertEquals(ledger,h.getFreedSpace().toString());
        h.s.fault=null;rewrite.invoke(h,imagePointer,image);assertEquals(replacement,read.invoke(h,imagePointer));
        assertEquals(before[imagePointer-1],h.rom[imagePointer-1]);assertEquals(before[imagePointer+3],h.rom[imagePointer+3]);
    }
}
