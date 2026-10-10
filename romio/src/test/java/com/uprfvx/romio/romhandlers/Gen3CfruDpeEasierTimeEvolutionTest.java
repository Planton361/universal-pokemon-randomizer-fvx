package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/** Source-shaped slots, synthetic bound witness and memory; no ROM/file ingress. */
class Gen3CfruDpeEasierTimeEvolutionTest {
    private static final Set<Integer> LEVEL = Set.of(4,8,9,10,11,12,13,14,16,18,20,21,22,23,28,31,32,35,41,42);
    private static final int RECORD = Gen3CfruDpeEvolutionPreservationTest.WitnessFixture.RECORD;

    static class JointFixture extends Gen3CfruDpeEvolutionPreservationTest.WitnessFixture {
        boolean armed, corrupt, neighbor, failPublication;
        int fault=-1, count;
        final List<Integer> offsets=new ArrayList<>();
        JointFixture() throws Exception { super(); }
        void afterWrite(int offset) {
            if (!armed) return;
            offsets.add(offset);
            if (++count==fault) {
                if (corrupt) memory[offset+(neighbor?4:0)]^=1;
                else throw new RomIOException("synthetic joint write exception");
            }
        }
        @Override protected void writeCfruDpeJointEvolutionPatch(int offset,byte[] value) {
            super.writeCfruDpeJointEvolutionPatch(offset,value);afterWrite(offset);
        }
        @Override protected void markImprovedEvolutions(Species owner) {
            super.markImprovedEvolutions(owner);
            if (failPublication) {failPublication=false;throw new RomIOException("synthetic publication exception");}
        }
    }

    private static JointFixture full(int threshold) throws Exception {
        var f=Gen3CfruDpeImpossibleEvolutionRawSlotTest.nativeOwners(new JointFixture());
        f.populateExactSource(); f.attest(threshold); f.bind();
        return f;
    }

    private static int word(byte[] bytes,int at) {return (bytes[at]&255)|((bytes[at+1]&255)<<8);}
    private static void word(byte[] bytes,int source,int slot,int field,int value) {
        int at=CfruDpeEvolutionFixture.offset(source,slot)+field;
        bytes[at]=(byte)value;bytes[at+1]=(byte)(value>>>8);
    }
    private static List<String> graph(JointFixture f) {
        return f.pool.stream().flatMap(owner->owner.getEvolutionsFrom().stream()).map(e->
                e.getFrom().getSpeciesSetIdentityNumber()+":"+e.getTo().getSpeciesSetIdentityNumber()+":"+
                e.getType()+":"+e.getExtraInfo()+":"+e.getEstimatedEvoLvl()+":"+e.getForme()).toList();
    }
    private static List<String> incoming(JointFixture f) {
        List<String> result=new ArrayList<>();
        for(var owner:f.species)if(owner!=null)for(var edge:owner.getEvolutionsTo()) {
            assertSame(owner,edge.getTo());
            assertEquals(1,edge.getFrom().getEvolutionsFrom().stream().filter(candidate->candidate==edge).count());
            result.add(owner.getSpeciesSetIdentityNumber()+":"+edge.getFrom().getSpeciesSetIdentityNumber()+":"+edge.getType()+":"+edge.getExtraInfo());
        }
        for(var owner:f.species)if(owner!=null)for(var edge:owner.getEvolutionsFrom()) {
            assertSame(owner,edge.getFrom());
            assertEquals(1,edge.getTo().getEvolutionsTo().stream().filter(candidate->candidate==edge).count());
        }
        return result;
    }

    private static byte[] expected(JointFixture f,int cap,boolean estimated) {
        byte[] result=f.memory.clone();
        var slots=CfruDpeEvolutionFixture.inventory();
        for(var s:slots) {
            int m=s.method(),p=s.parameter(),a=s.auxiliary();
            if(m==2||m==3) {m=s.source()==133?7:1;p=s.source()==133?(s.method()==2?93:94):0;}
            else if(m==22||m==23||m==28) {
                if(Set.of(104,961,1007).contains(s.source())) {m=7;p=s.method()==28?100:s.method()==23?93:94;a=0;}
                else m=4;
            } else if(m==24||m==25) {m=35;a=p;p=1;}
            else if(m==39)m=7;
            word(result,s.source(),s.slot(),0,m);word(result,s.source(),s.slot(),2,p);word(result,s.source(),s.slot(),6,a);
        }
        Set<Integer> outgoing=slots.stream().filter(s->s.method()>=1&&s.method()<=42).map(CfruDpeEvolutionFixture.SourceSlot::source).collect(Collectors.toSet());
        for(var s:slots) {
            int at=CfruDpeEvolutionFixture.offset(s.source(),s.slot());int method=word(result,at);
            if(LEVEL.contains(method)) word(result,s.source(),s.slot(),2,Math.min(word(result,at+2),outgoing.contains(s.target())?(3*cap+3)/4:cap));
        }
        result[RECORD+28]=(byte)160;return result;
    }

    @ParameterizedTest(name="joint full source cap={0} estimated={1}")
    @CsvSource({"1,false","20,false","24,false","30,false","100,false","20,true","24,true","30,true","100,true"})
    void completeSourceJointBudgetAndReplay(int cap,boolean estimated) throws Exception {
        var f=full(220);
        f.pool.forEach(owner->owner.getEvolutionsFrom().forEach(edge->edge.setEstimatedEvoLvl(45)));
        byte[] before=f.memory.clone(),expected=expected(f,cap,estimated);var beforeGraph=graph(f);
        f.preflightCfruDpeEasierTimeEvolutions(cap,false);
        assertArrayEquals(before,f.memory);assertEquals(beforeGraph,graph(f));assertTrue(f.getPreImprovedEvolutions().isEmpty());
        f.makeEasierAndRemoveTimeEvolutions(cap,estimated);
        assertArrayEquals(expected,f.memory,"entire memory including all1440x16x8 fields and neighbors");
        var finalGraph=graph(f);var finalIncoming=incoming(f);f.makeEasierAndRemoveTimeEvolutions(cap,estimated);f.write();f.write();
        assertArrayEquals(expected,f.memory);assertEquals(finalGraph,graph(f));assertEquals(finalIncoming,incoming(f));
        for(int id:new int[]{25,206,601}) assertArrayEquals(Arrays.copyOfRange(before,0x100+id*128,0x100+(id+1)*128),
                Arrays.copyOfRange(f.memory,0x100+id*128,0x100+(id+1)*128));
        assertEquals(7,f.word(133,0,0));assertEquals(93,f.word(133,0,2));assertEquals(7,f.word(133,1,0));
        assertEquals(17,f.word(133,2,0));assertEquals(23,f.word(133,2,2));assertEquals(1,f.word(133,2,6));
        for(var s:CfruDpeEvolutionFixture.inventory()) if(s.method()==6) {
            assertEquals(6,f.word(s.source(),s.slot(),0));assertEquals(s.parameter(),f.word(s.source(),s.slot(),2));
        }
        var reopened=Gen3CfruDpeImpossibleEvolutionRawSlotTest.nativeOwners(new JointFixture());
        System.arraycopy(f.memory,0,reopened.memory,0,f.memory.length);reopened.loadEvolutions();
        reopened.write();assertArrayEquals(expected,reopened.memory,"new handler opens/writes emitted source slots; no new witness inferred");
        f.loadEvolutions();f.write();assertArrayEquals(expected,f.memory);
    }

    private static int normalTarget(JointFixture f,int id,int level,int hour,int held,int friendship,boolean fairy) {
        int target=0;
        for(int slot=0;slot<16;slot++) {
            int m=f.word(id,slot,0),p=f.word(id,slot,2),aux=f.word(id,slot,6);
            boolean match=switch(m) {
                case 1->friendship>=(f.memory[RECORD+28]&255);
                case 2->hour>=4&&hour<20&&friendship>=(f.memory[RECORD+28]&255);
                case 3->(hour<4||hour>=20)&&friendship>=(f.memory[RECORD+28]&255);
                case 4->level>=p;
                case 17->fairy&&p==23&&(aux==0||friendship>=(f.memory[RECORD+28]&255));
                case 22->level>=p&&(hour<4||hour>=20);
                case 23->level>=p&&hour>=4&&hour<20;
                case 28->level>=p&&hour>=(aux>>>8)&&hour<(aux&255);
                case 35->level>=p&&held==aux;
                default->false;
            };
            if(match)target=f.word(id,slot,4);
        }
        return target;
    }

    @ParameterizedTest(name="Hisui owner={0}")
    @CsvSource({"156,36,36,157,1238","555,36,36,556,1241","680,54,54,681,1246",
            "812,40,40,813,1247","820,37,37,821,1249","940,34,36,941,1250"})
    void exactHisuiOriginalAndCappedConsumerPriorities(int id,int ordinary,int hisui,int normal,int regional) throws Exception {
        for(int cap:new int[]{20,24,30}) {
            var f=full(220);
            assertEquals(normal,normalTarget(f,id,ordinary,12,0,0,false));
            assertEquals(regional,normalTarget(f,id,hisui,12,771,0,false));
            if(ordinary<hisui)assertEquals(normal,normalTarget(f,id,ordinary,12,771,0,false));
            f.makeEasierAndRemoveTimeEvolutions(cap,false);
            int a=id==812?(3*cap+3)/4:cap;
            assertEquals(a,f.word(id,0,2));assertEquals(a,f.word(id,1,2));
            for(int level:new int[]{a-1,a,a+1}) {
                assertEquals(level<a?0:normal,normalTarget(f,id,level,12,0,0,false));
                assertEquals(level<a?0:regional,normalTarget(f,id,level,12,771,0,false));
            }
        }
    }

    @ParameterizedTest(name="Rockruff distinct stones cap={0}")
    @ValueSource(ints={1,20,24,30,100})
    void rockruffStoneSelectionHasNoInheritedTimePriority(int cap) throws Exception {
        var f=full(220);f.makeEasierAndRemoveTimeEvolutions(cap,false);
        for(int hour=0;hour<24;hour++)for(int level:new int[]{1,20,25,100}) {
            assertEquals(0,normalTarget(f,961,level,hour,0,255,false));
            for(int i=0;i<3;i++) {
                assertEquals(7,f.word(961,i,0));assertEquals(new int[]{93,94,100}[i],f.word(961,i,2));
                assertEquals(new int[]{962,1046,1082}[i],itemTarget(f,961,new int[]{93,94,100}[i],0));
                assertEquals(0,f.word(961,i,6));
            }
            assertEquals(0,itemTarget(f,961,101,0));
        }
    }

    private static int itemTarget(JointFixture f,int id,int item,int held) {
        int target=0;
        for(int slot=0;slot<16;slot++) {
            int m=f.word(id,slot,0),p=f.word(id,slot,2),a=f.word(id,slot,6);
            if((m==7||m==34||m==36||m==39)&&p==item&&(m!=36||held==a))target=f.word(id,slot,4);
        }
        return target;
    }

    @ParameterizedTest(name="new generic friendship owner={0}")
    @CsvSource({"459,363","486,411","500,501","1164,1165"})
    void newlyGenericFriendshipHasIndependent160ThresholdEveryHour(int id,int target) throws Exception {
        var f=full(220);f.makeEasierAndRemoveTimeEvolutions(20,false);
        for(int hour=0;hour<24;hour++)for(int value:new int[]{0,159,160,219,220,255}) {
            assertEquals(value<160?0:target,normalTarget(f,id,1,hour,0,value,false));
        }
        assertEquals(1,f.word(id,0,0));assertEquals(0,f.word(id,0,2));
    }

    @ParameterizedTest(name="held time transfer owner={0}")
    @CsvSource({"207,120,525","215,119,514","493,118,113","1240,119,1256"})
    void conditionalHeldItemLevelOneTransfersAuxiliaryWithoutItemUse(int id,int item,int target) throws Exception {
        var f=full(220);f.makeEasierAndRemoveTimeEvolutions(1,false);
        for(int hour=0;hour<24;hour++) {
            assertEquals(0,normalTarget(f,id,1,hour,0,0,false));
            assertEquals(target,normalTarget(f,id,1,hour,item,0,false));
            assertEquals(0,itemTarget(f,id,item,item));
        }
        assertEquals(35,f.word(id,0,0));assertEquals(1,f.word(id,0,2));assertEquals(item,f.word(id,0,6));
    }

    @Test
    void eeveeOriginalVsFinalFriendshipAndAllOpaqueSlots() throws Exception {
        var f=full(220);assertEquals(808,normalTarget(f,133,1,12,0,220,true));
        assertEquals(196,normalTarget(f,133,1,12,0,220,false));
        f.makeEasierAndRemoveTimeEvolutions(20,false);
        assertEquals(0,normalTarget(f,133,1,12,0,159,true));assertEquals(808,normalTarget(f,133,1,12,0,160,true));
        assertEquals(0,normalTarget(f,133,1,12,0,220,false));
        assertEquals(253,f.word(133,8,0));assertEquals(1270,f.word(133,8,4));
    }

    static Stream<Arguments> writeFaults() throws Exception {
        var f=full(220);byte[] before=f.memory.clone();
        Set<Integer> covered=new HashSet<>();List<Arguments> scenarios=new ArrayList<>();
        for(int cap:new int[]{20,1}) {
            byte[] expected=expected(f,cap,false);List<Integer> offsets=new ArrayList<>();
            for(int at=0x100;at<0x100+1440*128;at+=2)if(before[at]!=expected[at]||before[at+1]!=expected[at+1])offsets.add(at);
            offsets.add(RECORD+28);
            for(int i=0;i<offsets.size();i++)if(covered.add(offsets.get(i))) {
                scenarios.add(Arguments.of(i+1,false,cap,offsets.get(i)));
                scenarios.add(Arguments.of(i+1,true,cap,offsets.get(i)));
            }
        }
        assertEquals(425,covered.size(),"cap1 adds all54 level fields absent at cap20");
        return scenarios.stream();
    }

    @ParameterizedTest(name="joint write {0}, corruption={1}, cap={2}, offset={3}")
    @MethodSource("writeFaults")
    void eachActuallyAuthorizedWordAndThresholdFailureRollsBackThenRetries(int ordinal,boolean corrupt,int cap,int offset) throws Exception {
        var f=full(220);byte[] before=f.memory.clone();var graph=graph(f);var incoming=incoming(f);f.armed=true;f.fault=ordinal;f.corrupt=corrupt;
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(cap,false));
        assertEquals(offset,f.offsets.get(ordinal-1));
        assertEquals(ordinal,corrupt?f.fault:f.count);assertArrayEquals(before,f.memory);assertEquals(graph,graph(f));assertEquals(incoming,incoming(f));
        assertTrue(f.getPreImprovedEvolutions().isEmpty());
        f.fault=-1;f.count=0;f.corrupt=false;f.makeEasierAndRemoveTimeEvolutions(cap,false);f.write();
        assertEquals(160,f.memory[RECORD+28]&255);assertEquals(5,f.word(64,0,0));assertEquals(0,f.word(64,0,2));
    }

    @ParameterizedTest(name="staged target write slot={0}, corruption={1}")
    @CsvSource({"0,false","0,true","1,false","1,true"})
    void allowedTargetOnlyWordFailuresRestoreStagedPlanAndRetry(int slot,boolean corrupt) throws Exception {
        var f=full(220);var targets=f.getTargetOnlyEvolutionGraph();
        targets.get(f.species[64]).forEach(edge->edge.setTo(f.species[2]));f.applyTargetOnlyEvolutionGraph(targets);
        byte[] before=f.memory.clone(),expected=expected(f,20,false);
        word(expected,64,0,4,2);word(expected,64,1,4,2);
        int offset=CfruDpeEvolutionFixture.offset(64,slot)+4,ordinal=0;
        for(int at=0x100;at<=offset;at+=2)if(before[at]!=expected[at]||before[at+1]!=expected[at+1])ordinal++;
        var graph=graph(f);var incoming=incoming(f);f.armed=true;f.fault=ordinal;f.corrupt=corrupt;
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,false));
        assertEquals(offset,f.offsets.get(ordinal-1));assertArrayEquals(before,f.memory);
        assertEquals(graph,graph(f));assertEquals(incoming,incoming(f));assertTrue(f.getPreImprovedEvolutions().isEmpty());
        f.fault=-1;f.count=0;f.corrupt=false;f.preflightCfruDpeEasierTimeEvolutions(20,false);
        f.makeEasierAndRemoveTimeEvolutions(20,false);f.write();f.write();
        assertArrayEquals(expected,f.memory);assertEquals(2,f.word(64,0,4));assertEquals(2,f.word(64,1,4));incoming(f);
    }

    @Test
    void graphLogPublicationAndUnownedNeighborReadbackRollback() throws Exception {
        for(boolean neighbor:new boolean[]{false,true}) {
            var f=full(220);byte[] before=f.memory.clone();var graph=graph(f);
            if(neighbor){f.armed=true;f.fault=1;f.corrupt=true;f.neighbor=true;}else f.failPublication=true;
            assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,false));
            assertArrayEquals(before,f.memory);assertEquals(graph,graph(f));assertTrue(f.getPreImprovedEvolutions().isEmpty());
            f.fault=-1;f.neighbor=false;f.corrupt=false;f.makeEasierAndRemoveTimeEvolutions(20,false);f.write();
        }
    }

    @ParameterizedTest(name="joint rejection kind={0}")
    @ValueSource(ints={0,1,2,3,4,5,6,8,9,10,11,12,14,15,16,17,18})
    void allUnapprovedCollisionsSourceDriftAndBoundariesRejectWithoutPublication(int kind) throws Exception {
        var f=Gen3CfruDpeImpossibleEvolutionRawSlotTest.nativeOwners(new JointFixture());f.populateExactSource();
        switch(kind) {
            case 0->f.entry(1,0,4,10,2,0);
            case 1->{f.entry(1,0,4,10,2,0);f.entry(1,15,35,20,3,771);}
            case 2->{f.entry(1,0,7,93,2,0);f.entry(1,15,36,93,3,771);}
            case 3->f.entry(156,15,4,10,3,0);
            case 4->f.entry(961,15,7,100,3,0);
            case 5->f.entry(25,1,34,96,1022,24);
            case 6->f.entry(133,15,7,93,3,0);
            case 7->f.entry(64,0,5,0,65,7);
            case 8->f.entry(1,0,0xFFFF,50,2,0);
            case 9->f.entry(1,0,33,50,2,0);
            case 10->f.entry(1,0,4,101,2,0);
            case 11->f.entry(1,0,4,30,1440,0);
            case 12->f.entry(1,0,4,30,0,0);
            case 13->{f.entry(64,0,5,0,65,0);f.entry(64,15,5,0,65,0);}
            case 14->{f.entry(1,0,4,30,2,0);f.entry(1,15,26,942,3,0);}
            case 15->f.entry(804,0,23,38,805,0);
            case 16->f.entry(961,2,28,25,1082,0x1411);
            case 17->f.entry(1,0,4,0,2,0);
            case 18->{f.entry(459,15,1,0,3,0);}
        }
        if(kind==0)f.entry(1,15,4,20,3,0);
        f.attest(220);f.bind();
        if(kind==11||kind==12) { // Reserved/invalid raw target cannot grant active level ownership.
            assertThrows(RomIOException.class,()->f.preflightCfruDpeEasierTimeEvolutions(20,false));return;
        }
        byte[] before=f.memory.clone();var graph=graph(f);
        assertThrows(RomIOException.class,()->f.preflightCfruDpeEasierTimeEvolutions(20,false));
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,false));
        assertArrayEquals(before,f.memory);assertEquals(graph,graph(f));assertTrue(f.getPreImprovedEvolutions().isEmpty());
    }

    @Test
    void alternateTargetOnlyPlansPreserveSharedTargetsAndRejectNativePriorityDrift() throws Exception {
        for(int id:new int[]{64,123,133,156,961,25,206,601}) {
            var f=full(220);
            for(int i=0;i<f.species[id].getEvolutionsFrom().size();i++)f.species[id].getEvolutionsFrom().get(i).setEstimatedEvoLvl(11+i);
            var targets=f.getTargetOnlyEvolutionGraph();
            targets.get(f.species[id]).forEach(e->e.setTo(f.species[2]));f.applyTargetOnlyEvolutionGraph(targets);
            byte[] before=f.memory.clone();
            if(id==64||id==123) {
                f.makeEasierAndRemoveTimeEvolutions(20,false);f.write();
                assertEquals(2,f.word(id,0,4));assertEquals(2,f.word(id,1,4));
            } else {
                // A common target may remove the distinction, but cannot authorize mutation of exact native-priority/out-of-scope rows.
                assertThrows(RomIOException.class,()->f.preflightCfruDpeEasierTimeEvolutions(20,false));assertArrayEquals(before,f.memory);
            }
        }
    }

    @ParameterizedTest(name="committed joint drift kind={0}")
    @ValueSource(ints={0,1,2,3,4,5,6})
    void committedJointSaveAndReplayRejectThresholdGraphWitnessAndRawDrift(int kind) throws Exception {
        var f=full(220);f.makeEasierAndRemoveTimeEvolutions(20,false);
        switch(kind) {
            case 0->f.memory[RECORD+28]=(byte)220;
            case 1->f.memory[CfruDpeEvolutionFixture.offset(1,0)+6]^=1;
            case 2->f.memory[0x48004]^=1;
            case 3->f.memory[RECORD+18]^=1;
            case 4->f.species[64].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL,19);
            case 5->{var target=f.getTargetOnlyEvolutionGraph();target.get(f.species[64]).forEach(e->e.setTo(f.species[2]));f.applyTargetOnlyEvolutionGraph(target);}
            case 6->f.memory[0x100]^=1;
        }
        byte[] before=f.memory.clone();var graph=graph(f);
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,false));
        assertThrows(RomIOException.class,f::write);assertArrayEquals(before,f.memory);assertEquals(graph,graph(f));
    }

    @ParameterizedTest(name="joint invalid cap={0}")
    @ValueSource(ints={0,101,-1})
    void unusableCapsRejectBeforeMutation(int cap) throws Exception {
        var f=full(220);byte[] before=f.memory.clone();var model=graph(f);
        assertThrows(RomIOException.class,()->f.preflightCfruDpeEasierTimeEvolutions(cap,false));
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(cap,false));
        assertArrayEquals(before,f.memory);assertEquals(model,graph(f));assertTrue(f.getPreImprovedEvolutions().isEmpty());
    }

    @Test
    void reverseGraphDriftRejectsReadOnlyPreflightAndReplay() throws Exception {
        for(boolean committed:new boolean[]{false,true}) {
            var f=full(220);if(committed)f.makeEasierAndRemoveTimeEvolutions(20,false);
            var edge=f.species[1].getEvolutionsFrom().getFirst();edge.getTo().getEvolutionsTo().removeIf(candidate->candidate==edge);
            byte[] before=f.memory.clone();var graph=graph(f);
            assertThrows(RomIOException.class,()->f.preflightCfruDpeEasierTimeEvolutions(20,false));
            assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,false));
            assertArrayEquals(before,f.memory);assertEquals(graph,graph(f));
        }
    }

    public static void main(String[] args) throws Exception {
        long seed=Long.parseLong(args[0]);var f=full(220);
        f.pool.forEach(owner->owner.getEvolutionsFrom().forEach(edge->edge.setEstimatedEvoLvl(1+(int)(seed%100))));
        f.makeEasierAndRemoveTimeEvolutions(20,true);f.write();f.write();
        System.out.println("SYNTHETIC_F06C="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(f.memory))
                +":"+String.join(";",incoming(f)));
    }

    @Test
    void freshJvmJointReplayIsDeterministicForAllFixedSeeds() throws Exception {
        for(long seed:new long[]{0,1,677,20261005658L})assertEquals(process(seed),process(seed));
    }

    private static String process(long seed) throws Exception {
        Set<String> classpath=new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for(ClassLoader loader=Gen3CfruDpeEasierTimeEvolutionTest.class.getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof java.net.URLClassLoader urls)for(var url:urls.getURLs())classpath.add(java.nio.file.Path.of(url.toURI()).toString());
        var command=List.of(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                String.join(java.io.File.pathSeparator,classpath),Gen3CfruDpeEasierTimeEvolutionTest.class.getName(),Long.toString(seed));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));
        String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),result);assertTrue(result.startsWith("SYNTHETIC_F06C="),result);return result;
    }

    @Test
    void replayOptionDriftIndependentPlansAndForbiddenPairsStayFailClosed() throws Exception {
        var f=full(160);f.makeEasierAndRemoveTimeEvolutions(20,false);byte[] before=f.memory.clone();
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(24,false));
        assertThrows(RomIOException.class,()->f.makeEasierAndRemoveTimeEvolutions(20,true));
        assertThrows(RomIOException.class,()->f.condenseLevelEvolutions(20));
        assertThrows(RomIOException.class,()->f.removeImpossibleEvolutions(false,false));
        assertThrows(RomIOException.class,()->f.makeEvolutionsEasier(false,false));
        assertThrows(RomIOException.class,()->f.makeEvolutionsEasier(false,true));
        assertThrows(RomIOException.class,()->f.preflightCfruEvolutionOptions(true,true));
        assertThrows(RomIOException.class,()->f.preflightCfruDpeImpossibleEvolutions(true,true));assertArrayEquals(before,f.memory);
        var g=full(220);g.removeImpossibleEvolutions(false,false);byte[] staged=g.memory.clone();
        assertThrows(RomIOException.class,()->g.makeEasierAndRemoveTimeEvolutions(20,false));assertArrayEquals(staged,g.memory);
        var t=full(220);t.removeTimeBasedEvolutions();byte[] time=t.memory.clone();
        assertThrows(RomIOException.class,()->t.makeEasierAndRemoveTimeEvolutions(20,false));assertArrayEquals(time,t.memory);
        var h=full(220);h.condenseLevelEvolutions(20);byte[] easier=h.memory.clone();
        assertThrows(RomIOException.class,()->h.makeEasierAndRemoveTimeEvolutions(20,false));assertArrayEquals(easier,h.memory);
    }
}
