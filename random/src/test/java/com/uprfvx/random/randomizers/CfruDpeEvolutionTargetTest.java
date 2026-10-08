package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class CfruDpeEvolutionTargetTest {
    static Settings settings() {
        Settings s = new Settings(); s.setEvolutionsMod(Settings.EvolutionsMod.RANDOM);
        s.setEvosForceChange(true); return s;
    }

    @Test
    void exactTableMultipleSeedsChangesOnlySafeOrdinaryTargetsAndRoundTripsEveryWitness() throws Exception {
        Set<Integer> seen = new HashSet<>();
        for (long seed : new long[] {0, 1, 667, 20261006}) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            byte[] before = f.memory.clone();
            new EvolutionRandomizer(f, settings(), new Random(seed)).randomizeEvolutions();
            assertArrayEquals(before, f.memory, "planning must not write bytes");
            f.write();
            Set<Integer> ownedBytes = new HashSet<>();
            for (var row : CfruDpeEvolutionFixture.inventory()) {
                int offset = CfruDpeEvolutionFixture.offset(row.source(), row.slot());
                if (row.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE")
                        && f.getRestrictedSpeciesService().getAll(true).contains(f.species[row.source()])) {
                    int target = f.word(row.source(), row.slot(), 4);
                    assertNotEquals(row.target(), target, row.toString()); seen.add(target);
                    assertTrue(f.getCfruDpeRandomPoolEligibility(f.species[target], f.learnsets).eligible());
                    ownedBytes.add(offset + 4); ownedBytes.add(offset + 5);
                } else assertArrayEquals(Arrays.copyOfRange(before, offset, offset + 8), Arrays.copyOfRange(f.memory, offset, offset + 8), row.toString());
            }
            for (int i = 0; i < before.length; i++) if (!ownedBytes.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
            assertEquals(0xFE, f.word(346, 1, 6)); assertEquals(7, f.word(346, 1, 0)); assertEquals(101, f.word(346, 1, 2));
            byte[] saved = f.memory.clone();
            f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph();
            graph.forEach((source, edges) -> edges.forEach(e -> assertEquals(e.getTo().getSpeciesSetIdentityNumber(),
                    f.word(source.getSpeciesSetIdentityNumber(), e.getExtraInfo(), 4))));
            assertAcyclic(graph, 10);
            f.write(); f.write(); assertArrayEquals(saved, f.memory);
        }
        assertTrue(seen.stream().anyMatch(id -> id >= 0x50E && id <= 0x58E), "Gen9 targets");
        assertTrue(seen.stream().anyMatch(id -> id >= 0x3FC && id <= 0x417), "regional targets");
    }

    static CfruDpeEvolutionFixture small() throws Exception {
        var f = new CfruDpeEvolutionFixture();
        f.pool.removeIf(sp -> sp.getSpeciesSetIdentityNumber() > 50);
        f.entry(1, 0, 16, 50, 2, 0);
        f.entry(4, 2, 18, 32, 5, 17);
        f.entry(7, 1, 22, 25, 8, 0); f.entry(7, 3, 23, 25, 9, 0);
        f.entry(7, 5, 254, 0x1234, 0x365, 1); // Primal variant shape
        f.entry(7, 6, 253, 1, 0x54E, 0);
        f.entry(7, 8, 0, 0xBEEF, 0, 0xFE);
        f.entry(7, 10, 0xFFFF, 0xABCD, 50, 0xFEDC);
        f.entry(7, 11, 4, 15, 1440, 0); // invalid target
        f.entry(7, 13, 4, 30, 12, 0); f.entry(7, 14, 4, 30, 12, 0); // both duplicate slots fixed
        f.restrictions(); f.loadEvolutions(); return f;
    }

    @Test
    void sharedConstraintsSplitIdentityAndFixedDuplicateTopologyAcrossSeeds() throws Exception {
        for (int seed = 0; seed < 16; seed++) {
            var f = small();
            f.species[1].setHp(10); f.species[4].setHp(10); f.species[7].setHp(10);
            // Explicitly different growth/type/BST candidates exercise real filters.
            f.species[20].setGrowthCurve(ExpCurve.SLOW); f.species[21].setPrimaryType(Type.FIRE);
            for (int id = 30; id <= 50; id++) f.species[id].setHp(100);
            Settings s = settings(); s.setEvosNoConvergence(true); s.setEvosMaxThreeStages(true);
            s.setEvosSameTyping(true); s.setEvosForceGrowth(true); s.setEvosSimilarStrength(true);
            byte[] before = f.memory.clone();
            new EvolutionRandomizer(f, s, new Random(seed)).randomizeEvolutions(); f.write(); f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph();
            assertAcyclic(graph, 3);
            Set<Species> picked = new HashSet<>();
            for (var entry : graph.entrySet()) for (Evolution e : entry.getValue()) {
                if (e.getExtraInfo() < 0) continue;
                assertTrue(picked.add(e.getTo()), "no convergence");
                assertTrue(e.getTo().hasSharedType(e.getFrom()));
                assertEquals(e.getFrom().getGrowthCurve(), e.getTo().getGrowthCurve());
                assertTrue(e.getTo().getBSTForPowerLevels() > e.getFrom().getBSTForPowerLevels());
                assertNotEquals(20, e.getTo().getSpeciesSetIdentityNumber());
                // Similar strength chooses the nearest available strength bracket:
                // candidates with HP100 are farther than plentiful HP50 controls.
                assertTrue(e.getTo().getHp() < 100);
            }
            assertNotEquals(f.word(7, 1, 4), f.word(7, 3, 4));
            for (int slot : new int[] {5,6,8,10,11,13,14}) {
                int o = CfruDpeEvolutionFixture.offset(7, slot);
                assertArrayEquals(Arrays.copyOfRange(before,o,o+8), Arrays.copyOfRange(f.memory,o,o+8));
            }
            assertEquals(32, f.word(4,2,2)); assertEquals(17, f.word(4,2,6));
        }
    }

    @Test
    void impossiblePlanRestoresGraphAndBytesAndCanRetry() throws Exception {
        var f = small(); f.pool.removeIf(sp -> sp.getSpeciesSetIdentityNumber() != 1); f.restrictions();
        byte[] before = f.memory.clone();
        var original = new ArrayList<>(f.species[1].getEvolutionsFrom());
        assertThrows(RandomizationException.class, () -> new EvolutionRandomizer(f, settings(), new Random(667)).randomizeEvolutions());
        assertEquals(original, f.species[1].getEvolutionsFrom()); assertArrayEquals(before, f.memory);
        f.write(); assertArrayEquals(before, f.memory);
        f.pool.add(f.species[3]); f.restrictions();
        new EvolutionRandomizer(f, settings(), new Random(667)).randomizeEvolutions(); f.write();
        assertEquals(3, f.word(1,0,4));
    }

    @Test
    void unsafeUnrepresentableAndMalformedPlansRejectBeforePublishing() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var f = small(); var graph = f.getTargetOnlyEvolutionGraph();
            byte[] before = f.memory.clone(); var edge = graph.get(f.species[1]).getFirst();
            switch (mode) {
                case 0 -> edge.setTo(f.species[0x365]);
                case 1 -> edge.setTo(new Species(2));
                case 2 -> graph.get(f.species[1]).clear();
                case 3 -> graph.get(f.species[7]).stream().filter(e -> e.getExtraInfo() < 0).findFirst().orElseThrow().setTo(f.species[3]);
            }
            assertThrows(RomIOException.class, () -> f.applyTargetOnlyEvolutionGraph(graph));
            assertArrayEquals(before, f.memory); f.write(); assertArrayEquals(before, f.memory);
        }
    }

    @Test
    void everyLevelGuardAndPostPlanOpaqueMutationRejectWithoutOutput() throws Exception {
        var f = small(); byte[] before = f.memory.clone();
        Settings s = settings(); s.setEvolutionsMod(Settings.EvolutionsMod.RANDOM_EVERY_LEVEL);
        var error = assertThrows(RandomizationException.class, () -> new EvolutionRandomizer(f,s,new Random(667)).randomizeEvolutions());
        assertTrue(error.getMessage().contains("RANDOM_EVERY_LEVEL")); assertArrayEquals(before, f.memory);
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        f.species[7].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL, 99);
        assertThrows(RomIOException.class, f::preflightSave);
        assertThrows(RomIOException.class, f::saveSpeciesStats);
        assertThrows(RomIOException.class, f::write); assertArrayEquals(before, f.memory);
    }


    @Test
    void plannedFullyOwnedSupportedTweaksWorkButOpaqueTriggersRemainUnowned() throws Exception {
        var f = small();
        f.entry(10,0,5,0,11,0); f.loadEvolutions();
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        assertTrue(f.impossible(f.species[10].getEvolutionsFrom().getFirst()));
        f.write(); assertEquals(4,f.word(10,0,0)); assertEquals(37,f.word(10,0,2));
        assertEquals(16,f.word(1,0,0)); assertEquals(50,f.word(1,0,2));
        f.loadEvolutions();
        new EvolutionRandomizer(f,settings(),new Random(668)).randomizeEvolutions();
        f.entry(4,2,18,32,5,99);
        byte[] externallyChanged = f.memory.clone();
        assertThrows(RomIOException.class,f::preflightSave);
        assertArrayEquals(externallyChanged,f.memory);
    }

    @Test
    void invalidAssetTargetIsRejectedByHandlerEvenWhenCallerBypassesPool() throws Exception {
        var f = small(); var graph = f.getTargetOnlyEvolutionGraph();
        graph.get(f.species[1]).getFirst().setTo(f.species[50]);
        Arrays.fill(f.memory,0x30000 + 50*8,0x30000 + 50*8+4,(byte)0);
        byte[] before = f.memory.clone();
        assertThrows(RomIOException.class,() -> f.applyTargetOnlyEvolutionGraph(graph));
        assertArrayEquals(before,f.memory);
    }


    @Test
    void targetTamperingOnFullyModeledPlannedRowRejectsBeforeAllOutput() throws Exception {
        var f = small(); f.entry(2,0,4,16,3,0); f.loadEvolutions();
        byte[] before = f.memory.clone();
        new EvolutionRandomizer(f,settings(),new Random(667)).randomizeEvolutions();
        f.species[2].getEvolutionsFrom().getFirst().setTo(f.species[0x365]);
        assertThrows(RomIOException.class,f::preflightSave);
        assertThrows(RomIOException.class,f::saveSpeciesStats);
        assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory);
    }

    /** Shared only between approved test leaves; all bytes/properties are synthetic and in memory. */
    public static void attestSyntheticOwnership(CfruDpeEvolutionFixture f) throws Exception {
        byte[] b = f.memory;
        b[0xAC]='B'; b[0xAD]='P'; b[0xAE]='R'; b[0xAF]='E'; b[0xBC]=0;
        b[0x42EC4]=0; b[0x42EC5]=0x4B; b[0x42EC6]=0x18; b[0x42EC7]=0x47;
        syntheticInt(b,0x42EC8,0x08048001); syntheticInt(b,0x42F6C,0x08000100);
        System.arraycopy("CFRUEVO1".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,b,0x48200,8);
        syntheticInt(b,0x48208,0x08048200); syntheticInt(b,0x4820C,0x08048001);
        int[] fields={1,32,28,1,220,160};
        for(int i=0;i<fields.length;i++) {b[0x48210+i*2]=(byte)fields[i]; b[0x48211+i*2]=(byte)(fields[i]>>>8);}
        b[0x4821C]=(byte)220;
        Properties p=new Properties();
        p.setProperty("schema","OWNERSHIP_WITNESS_V1"); p.setProperty("version","1");
        p.setProperty("cfru.sha","958c30ec58919ac3e13a40ddb9bd94a86651636e");
        p.setProperty("dpe.sha","d887185de1f6ae6a78e85c4311bbadde17041d00");
        p.setProperty("build.id","synthetic-build"); p.setProperty("config.id","synthetic-config");
        p.setProperty("config.sha256","0".repeat(64)); p.setProperty("input.size",Integer.toString(b.length));
        p.setProperty("input.sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)));
        p.setProperty("record.offset","0x48200"); p.setProperty("table.rows","1440");
        p.setProperty("table.slots","16"); p.setProperty("table.entryBytes","8");
        syntheticInterval(p,"table",0x100,0x100+1440*128); syntheticInterval(p,"consumer",0x48000,0x48100);
        p.setProperty("insertions.count","2"); syntheticInterval(p,"insertions.0",0x100,0x2E000);
        syntheticInterval(p,"insertions.1",0x48000,0x4C000); p.setProperty("protected.count","4");
        String[] types={"PICKUP_CODE","PICKUP_DATA","OTHER_CODE","OTHER_DATA"};
        for(int i=0;i<types.length;i++) {
            p.setProperty("protected."+i+".type",types[i]); syntheticInterval(p,"protected."+i,0x49000+i*0x100,0x49080+i*0x100);
        }
        f.setField("originalRom",b.clone()); f.loadEvolutions();
        var bind=com.uprfvx.romio.romhandlers.Gen3RomHandler.class.getDeclaredMethod("acceptSyntheticEvolutionWitness",Properties.class);
        bind.setAccessible(true); bind.invoke(f,p);
    }
    private static void syntheticInt(byte[] bytes,int at,int value) {
        for(int i=0;i<4;i++) bytes[at+i]=(byte)(value>>>(8*i));
    }
    private static void syntheticInterval(Properties p,String prefix,int start,int end) {
        p.setProperty(prefix+".start",Integer.toString(start)); p.setProperty(prefix+".end",Integer.toString(end));
    }

    @Test
    void easierWithRandomTargetsAndForceChangeCoversFullInventoryDewottAndAllOwnedBytesAcrossSeeds() throws Exception {
        Set<Integer> levels=Set.of(4,8,9,10,11,12,13,14,16,18,20,21,22,23,28,31,32,35,41,42);
        Set<Integer> outgoing=new HashSet<>();
        CfruDpeEvolutionFixture.inventory().stream().filter(s->s.method()>=1&&s.method()<=42).forEach(s->outgoing.add(s.source()));
        for(long seed:new long[]{0,1,680,20261005658L}) {
            var f=new CfruDpeEvolutionFixture(); f.populateExactSource(); attestSyntheticOwnership(f);
            f.preflightCfruEvolutionEasier(20); byte[] before=f.memory.clone();
            new EvolutionRandomizer(f,settings(),new Random(seed)).randomizeEvolutions();
            assertArrayEquals(before,f.memory,"target plan must remain staged before easier writer");
            f.condenseLevelEvolutions(20); f.makeEvolutionsEasier(false,false);
            Set<Integer> allowed=new HashSet<>(); allowed.add(0x4821C);
            int changed=0;
            for(var row:CfruDpeEvolutionFixture.inventory()) {
                int o=CfruDpeEvolutionFixture.offset(row.source(),row.slot());
                int target=f.word(row.source(),row.slot(),4);
                if(row.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE") && f.getRestrictedSpeciesService().getAll(true).contains(f.species[row.source()])) {
                    assertNotEquals(row.target(),target,row.toString()); changed++;
                    allowed.add(o+4); allowed.add(o+5);
                } else assertEquals(row.target(),target,row.toString());
                if(levels.contains(row.method())) {
                    assertEquals(Math.min(row.parameter(),outgoing.contains(target)?15:20),f.word(row.source(),row.slot(),2),row.toString());
                    allowed.add(o+2); allowed.add(o+3);
                }
                assertEquals(row.method(),f.word(row.source(),row.slot(),0)); assertEquals(row.auxiliary(),f.word(row.source(),row.slot(),6));
            }
            assertTrue(changed>500); assertEquals(4,f.word(0x22B,0,0)); assertEquals(35,f.word(0x22B,1,0));
            assertEquals(771,f.word(0x22B,1,6)); assertEquals(2999,f.word(0x574,0,2)); assertEquals(2999,f.word(0x575,0,2));
            for(int i=0;i<before.length;i++) if(!allowed.contains(i)) assertEquals(before[i],f.memory[i],"unowned byte "+i);
            byte[] after=f.memory.clone(); f.write(); f.loadEvolutions(); f.write(); f.write(); assertArrayEquals(after,f.memory);
        }
    }

    @Test
    void easierWriteFailurePreservesExistingRandomTargetPlanAndGraphForRetry() throws Exception {
        class Failing extends CfruDpeEvolutionFixture {
            boolean fail;
            Failing() throws Exception {super();}
            @Override protected void writeWord(int offset,int value) {
                super.writeWord(offset,value); if(fail) throw new RomIOException("synthetic failure");
            }
        }
        var f=new Failing(); f.populateExactSource(); attestSyntheticOwnership(f);
        new EvolutionRandomizer(f,settings(),new Random(721)).randomizeEvolutions();
        byte[] before=f.memory.clone(); Map<Integer,List<String>> graph=new HashMap<>();
        for(Species sp:f.pool) graph.put(sp.getSpeciesSetIdentityNumber(),sp.getEvolutionsFrom().stream()
                .map(e->e.getTo().getSpeciesSetIdentityNumber()+":"+e.getExtraInfo()).toList());
        f.fail=true; assertThrows(RomIOException.class,()->f.condenseLevelEvolutions(20)); assertArrayEquals(before,f.memory);
        for(Species sp:f.pool) assertEquals(graph.get(sp.getSpeciesSetIdentityNumber()),sp.getEvolutionsFrom().stream()
                .map(e->e.getTo().getSpeciesSetIdentityNumber()+":"+e.getExtraInfo()).toList());
        f.fail=false; f.condenseLevelEvolutions(20); f.write(); assertEquals(160,f.memory[0x4821C]&255);
    }

    static void assertAcyclic(Map<Species,List<Evolution>> graph, int limit) {
        for (Species source : graph.keySet()) assertTrue(depth(source,graph,new HashSet<>()) <= limit);
    }
    static int depth(Species sp, Map<Species,List<Evolution>> graph, Set<Species> path) {
        assertTrue(path.add(sp), "cycle"); int depth = 1;
        for (Evolution e : graph.getOrDefault(sp,List.of())) depth = Math.max(depth,1+depth(e.getTo(),graph,path));
        path.remove(sp); return depth;
    }
}
