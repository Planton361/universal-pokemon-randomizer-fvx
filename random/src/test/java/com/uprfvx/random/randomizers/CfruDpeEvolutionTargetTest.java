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

    @Test
    void randomForceChangeThenRemoveTimePreservesSlotTargetsAcrossSeedsAndSaveReload() throws Exception {
        for (long seed : new long[] {0, 1, 723, 20261009, 680, 667}) {
            var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
            byte[] before = f.memory.clone();
            f.preflightCfruDpeTimeEvolutions();
            new EvolutionRandomizer(f, settings(), new Random(seed)).randomizeEvolutions();
            f.preflightCfruDpeTimeEvolutions(); f.removeTimeBasedEvolutions();
            assertArrayEquals(before, f.memory);
            f.preflightCfruDpeTimeEvolutions(); f.write();
            Set<Integer> owned = new HashSet<>();
            for (var slot : CfruDpeEvolutionFixture.inventory()) {
                int o = CfruDpeEvolutionFixture.offset(slot.source(), slot.slot());
                if (slot.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE")
                        && f.getRestrictedSpeciesService().getAll(true).contains(f.species[slot.source()])) {
                    assertNotEquals(slot.target(), f.word(slot.source(), slot.slot(), 4));
                    owned.add(o + 4); owned.add(o + 5);
                }
                if (Set.of(2, 3, 22, 23, 24, 25, 28, 39).contains(slot.method())) {
                    for (int field : new int[] {0, 2, 6}) { owned.add(o + field); owned.add(o + field + 1); }
                    assertFalse(Set.of(2, 3, 22, 23, 24, 25, 28, 39).contains(f.word(slot.source(), slot.slot(), 0)));
                }
            }
            for (int i = 0; i < before.length; i++) if (!owned.contains(i)) assertEquals(before[i], f.memory[i], "unowned byte " + i);
            for (int id : new int[] {104, 133, 961, 1007}) {
                assertNotEquals(f.word(id, 0, 4), f.word(id, 1, 4));
                assertEquals(93, f.word(id, 0, 2)); assertEquals(94, f.word(id, 1, 2));
            }
            assertNotEquals(f.word(961, 0, 4), f.word(961, 2, 4));
            assertNotEquals(f.word(961, 1, 4), f.word(961, 2, 4));
            assertEquals(100, f.word(961, 2, 2));
            for (int id : new int[] {207, 215, 493, 1240}) {
                var edge = f.species[id].getEvolutionsFrom().getFirst();
                assertEquals(EvolutionType.ITEM, edge.getType());
                assertEquals(f.word(id, 0, 4), edge.getTo().getSpeciesSetIdentityNumber());
                assertTrue(edge.getTo().getEvolutionsTo().contains(edge));
            }
            byte[] saved = f.memory.clone(); f.loadEvolutions();
            var graph = f.getTargetOnlyEvolutionGraph(); assertAcyclic(graph, 10);
            graph.forEach((source, edges) -> edges.forEach(edge -> assertEquals(
                    edge.getTo().getSpeciesSetIdentityNumber(), f.word(source.getSpeciesSetIdentityNumber(), edge.getExtraInfo(), 4))));
            f.removeTimeBasedEvolutions(); f.write(); f.write(); assertArrayEquals(saved, f.memory);
            // A second target randomization after reload also owns the converted ITEM graph.
            f.loadEvolutions(); new EvolutionRandomizer(f, settings(), new Random(seed + 1)).randomizeEvolutions();
            f.write();
            for (int id : new int[] {207, 215, 493, 1240}) {
                assertEquals(f.word(id, 0, 4), f.species[id].getEvolutionsFrom().getFirst().getTo().getSpeciesSetIdentityNumber());
            }
            f.loadEvolutions(); f.write();
        }
    }

    @Test
    void invalidTimePlanAfterStagedRandomTargetsLeavesTheEntireStagedGraphIntact() throws Exception {
        var f = new CfruDpeEvolutionFixture(); f.populateExactSource();
        new EvolutionRandomizer(f, settings(), new Random(723)).randomizeEvolutions();
        // Tampering after planning must fail before publishing even an earlier valid time row.
        f.entry(1365, 0, 22, 30, 1366, 1);
        byte[] before = f.memory.clone();
        var edges = List.copyOf(f.species[133].getEvolutionsFrom());
        assertThrows(RomIOException.class, f::removeTimeBasedEvolutions);
        assertEquals(edges, f.species[133].getEvolutionsFrom()); assertArrayEquals(before, f.memory);
        assertThrows(RomIOException.class, f::write); assertArrayEquals(before, f.memory);
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
        attestSyntheticOwnership(f, f.memory, 0x100);
    }
    private static void attestSyntheticOwnership(CfruDpeEvolutionFixture f, byte[] b, int table) throws Exception {
        b[0xAC]='B'; b[0xAD]='P'; b[0xAE]='R'; b[0xAF]='E'; b[0xBC]=0;
        b[0x42EC4]=0; b[0x42EC5]=0x4B; b[0x42EC6]=0x18; b[0x42EC7]=0x47;
        syntheticInt(b,0x42EC8,0x08048001); syntheticInt(b,0x42F6C,0x08000000+table);
        System.arraycopy("CFRUEVO1".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,b,0x48200,8);
        syntheticInt(b,0x48208,0x08048200); syntheticInt(b,0x4820C,0x08048001);
        int[] fields={1,32,28,1,220,160};
        for(int i=0;i<fields.length;i++) {b[0x48210+i*2]=(byte)fields[i]; b[0x48211+i*2]=(byte)(fields[i]>>>8);}
        b[0x4821C]=(byte)220;
        Properties p=new Properties();
        p.setProperty("schema","OWNERSHIP_WITNESS_V1"); p.setProperty("version","1");
        p.setProperty("cfru.sha","e27e2113e4d59dc76cf5da8d4c43b067addae348");
        p.setProperty("dpe.sha","d887185de1f6ae6a78e85c4311bbadde17041d00");
        p.setProperty("build.id","synthetic-build"); p.setProperty("config.id","synthetic-config");
        p.setProperty("config.sha256","0".repeat(64)); p.setProperty("input.size",Integer.toString(b.length));
        p.setProperty("input.sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)));
        p.setProperty("record.offset","0x48200"); p.setProperty("table.rows","1440");
        p.setProperty("table.slots","16"); p.setProperty("table.entryBytes","8");
        syntheticInterval(p,"table",table,table+1440*128); syntheticInterval(p,"consumer",0x48000,0x48100);
        p.setProperty("insertions.count","2"); syntheticInterval(p,"insertions.0",table,table+1440*128);
        syntheticInterval(p,"insertions.1",0x48000,0x4C000); p.setProperty("protected.count","4");
        String[] types={"PICKUP_CODE","PICKUP_DATA","OTHER_CODE","OTHER_DATA"};
        for(int i=0;i<types.length;i++) {
            p.setProperty("protected."+i+".type",types[i]);
            int[] starts = {0x49000, 0x4B000, 0x4A000, 0x4A200};
            int[] ends = {0x49600, 0x4B200, 0x4A100, 0x4A300};
            syntheticInterval(p,"protected."+i,starts[i],ends[i]);
        }
        f.setField("originalRom",b.clone()); f.loadEvolutions();
        var bind=com.uprfvx.romio.romhandlers.Gen3RomHandler.class.getDeclaredMethod("acceptSyntheticEvolutionWitness",Properties.class);
        bind.setAccessible(true); bind.invoke(f,p);
    }
    /** One synthetic selected input with independent data/code owners for all three subsystems. */
    static final class CombinedFixture extends CfruDpeEvolutionFixture {
        static final int TABLE = 0x50000, COMMON = 0x4B000, RARE = 0x4B080;
        final byte[] bytes;
        boolean palettePhase;
        CombinedFixture() throws Exception {
            populateExactSource();
            bytes = Arrays.copyOf(memory, 0xA0000);
            System.arraycopy(memory, EVOLUTION_BASE, bytes, TABLE, 1440*128);
            Arrays.fill(bytes, EVOLUTION_BASE, EVOLUTION_BASE+1440*128, (byte)0);
            setField("rom", bytes);
            var entryField = com.uprfvx.romio.romhandlers.Gen3RomHandler.class.getDeclaredField("romEntry");
            entryField.setAccessible(true);
            var entry = (com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry) entryField.get(this);
            entry.setRomType(com.uprfvx.romio.constants.Gen3Constants.RomType_FRLG);
            entry.putIntValue("PokemonEvolutions", TABLE);
            entry.putIntValue("PokemonShinyPalettes", 0x36000);
            entry.putIntValue("PokemonNormalPalettes", 0x33000);
            int[] mapping = java.util.stream.IntStream.range(0,1441).toArray();
            setField("pokedexToInternal", mapping); setField("internalToPokedex", mapping.clone());
            for (int id : new int[]{4,5,6}) for (boolean shiny : new boolean[]{false,true}) {
                var palette = Gen3to5PaletteBoundsTest.speciesWithPalette(id);
                byte[] packed = compressors.DSCmp.compressLZ10((shiny ? palette.getShinyPalette() : palette.getNormalPalette()).toBytes());
                int at = 0x45000+id*128+(shiny?64:0);
                System.arraycopy(packed,0,bytes,at,packed.length);
                syntheticInt(bytes,(shiny?0x36000:0x33000)+id*8,0x08000000+at);
            }
            species[4].setPrimaryType(Type.FIRE); species[5].setPrimaryType(Type.WATER); species[6].setPrimaryType(Type.FAIRY);
            palettePhase=true; loadPokemonPalettes(); palettePhase=false;
            installPickup();
            attestSyntheticOwnership(this, bytes, TABLE);
        }
        @Override public SpeciesSet getSpeciesSetInclFormes() {
            return palettePhase ? new SpeciesSet(List.of(species[4],species[5],species[6])) : super.getSpeciesSetInclFormes();
        }
        @Override public SpeciesSet getSpeciesSet() { return getSpeciesSetInclFormes(); }
        @Override public String getPaletteFilesID() { return "FRLG"; }
        private void installPickup() throws Exception {
            int command=0x49000, bridge=0x49400, descriptor=0x49480, consumer=0x49500;
            for(int site:new int[]{0x14C1C,0x15A28,0x15C6C,0x15C98,0x1D054}) syntheticInt(bytes,site,0x08000000+command);
            syntheticInt(bytes,command+0xE5*4,0x08000001+bridge);
            int[] instructions={0xB510,0x4804,0x4B04,0xF000,0xF803,0xBC10,0xBC01,0x4700,0x4718,0x46C0};
            for(int i=0;i<instructions.length;i++) writeWord(bridge+i*2,instructions[i]);
            syntheticInt(bytes,bridge+20,0x08000000+descriptor); syntheticInt(bytes,bridge+24,0x08000001+consumer);
            writeWord(consumer,0x4770); // public synthetic stub, never a compiled consumer claim
            syntheticInt(bytes,descriptor,0x31555043); writeWord(descriptor+4,1); writeWord(descriptor+6,64);
            syntheticInt(bytes,descriptor+8,6); syntheticInt(bytes,descriptor+12,1);
            int[] tables={COMMON,RARE,0x4B100,0x4B180}, counts={18,11,9,2};
            for(int i=0;i<4;i++) {syntheticInt(bytes,descriptor+16+i*4,0x08000000+tables[i]); writeWord(descriptor+32+i*2,counts[i]);}
            int[] shape={4,2,4,10,10,9,2,1,100,0,0,0};
            for(int i=0;i<shape.length;i++) bytes[descriptor+40+i]=(byte)shape[i];
            syntheticInt(bytes,descriptor+52,779); syntheticInt(bytes,descriptor+56,0x08000001+consumer);
            syntheticInt(bytes,descriptor+60,0x08000001+bridge);
            int[] common={13,14,22,3,86,85,23,21,2,24,68,93,94,111,19,25,69,37};
            int[] rare={21,110,187,19,34,686,688,36,688,200,688};
            for(int i=0;i<common.length;i++) writeWord(COMMON+i*2,common[i]);
            for(int i=0;i<rare.length;i++) writeWord(RARE+i*2,rare[i]);
            int[] ceilings={19661,26214,32768,39322,45875,52429,58982,61604,64225,64881,65536};
            for(int i=0;i<ceilings.length;i++) syntheticInt(bytes,i<9?0x4B100+i*4:0x4B180+(i-9)*4,ceilings[i]);
            List<Item> items = new ArrayList<>(Collections.nCopies(com.uprfvx.romio.constants.ItemIDs.UNIQUE_OFFSET+800,null));
            for(int raw=1;raw<779;raw++) {
                int id=com.uprfvx.romio.constants.Gen3Constants.itemIDToStandard(raw);
                Item item=new Item(id,"Synthetic "+raw); item.setAllowed(Arrays.stream(common).anyMatch(n->n==rawId(item)) || Arrays.stream(rare).anyMatch(n->n==rawId(item)));
                items.set(id,item);
            }
            setField("items",items);
        }
        private static int rawId(Item item) { return com.uprfvx.romio.constants.Gen3Constants.itemIDToInternal(item.getId()); }
    }

    static byte[] combinedReplay(long seed, int improvement, int paletteMode, boolean paletteEnabled) throws Exception {
        var f = new CombinedFixture();
        var rng = new com.uprfvx.random.random.RandomSource(); rng.seed(seed);
        Settings settings=settings(); settings.setPickupItemsMod(Settings.PickupItemsMod.RANDOM);
        byte[] before=f.bytes.clone();
        if(improvement==1) f.preflightCfruEvolutionEasier(40);
        if(improvement==2) f.preflightCfruDpeTimeEvolutions();
        new EvolutionRandomizer(f,settings,rng.getNonCosmetic()).randomizeEvolutions();
        if(improvement==1) {f.condenseLevelEvolutions(40);f.makeEvolutionsEasier(false,false);}
        if(improvement==2) f.removeTimeBasedEvolutions();
        new ItemRandomizer(f,settings,rng.getNonCosmetic()).randomizePickupItems();
        f.write();
        assertEquals(29,f.getPickupItems().size());
        Set<Integer> owned=new HashSet<>();
        for(var slot:CfruDpeEvolutionFixture.inventory()) {
            int o=CombinedFixture.TABLE+slot.source()*128+slot.slot()*8;
            if(slot.disposition().equals("ORDINARY_TARGET_RANDOMIZABLE") && f.getRestrictedSpeciesService().getAll(true).contains(f.species[slot.source()])) {
                owned.add(o+4);owned.add(o+5); assertNotEquals(slot.target(), filefunctions.IOFunctions.read2ByteInt(f.bytes,o+4));
            }
            if(improvement==1 && Set.of(4,8,9,10,11,12,13,14,16,18,20,21,22,23,28,31,32,35,41,42).contains(slot.method())) {owned.add(o+2);owned.add(o+3);}
            if(improvement==2 && Set.of(2,3,22,23,24,25,28,39).contains(slot.method())) for(int field:new int[]{0,2,6}) {owned.add(o+field);owned.add(o+field+1);}
        }
        if(improvement==1) owned.add(0x4821C);
        for(int i=0;i<36;i++) owned.add(CombinedFixture.COMMON+i);
        for(int i=0;i<22;i++) owned.add(CombinedFixture.RARE+i);
        for(int i=0;i<before.length;i++) if(!owned.contains(i)) assertEquals(before[i],f.bytes[i],"combined unowned byte "+i);
        byte[] committed=f.bytes.clone();f.loadEvolutions();f.write();assertArrayEquals(committed,f.bytes);
        // Real provenance query and cosmetic path on three independently loaded palette owners.
        f.palettePhase=true;
        long gameplayCalls=rng.callsSinceSeedNonCosmetic();
        List<byte[]> normals=List.of(f.species[4].getNormalPalette().toBytes(),f.species[5].getNormalPalette().toBytes(),f.species[6].getNormalPalette().toBytes());
        List<byte[]> shinies=List.of(f.species[4].getShinyPalette().toBytes(),f.species[5].getShinyPalette().toBytes(),f.species[6].getShinyPalette().toBytes());
        if(paletteEnabled) {
            Settings graphics=new Settings();graphics.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
            graphics.setPokemonPalettesFollowTypes(paletteMode==2);graphics.setPokemonPalettesShinyFromNormal(paletteMode==4);
            var palettes=new Gen3to5PaletteRandomizer(f,graphics,rng.getCosmetic()); palettes.randomizePokemonPalettes();
            assertTrue(palettes.isChangesMade());
            for(int i=0;i<3;i++) {
                assertFalse(Arrays.equals(normals.get(i),f.species[i+4].getNormalPalette().toBytes()));
                assertArrayEquals(paletteMode==4?normals.get(i):shinies.get(i),f.species[i+4].getShinyPalette().toBytes());
            }
        }
        assertEquals(gameplayCalls,rng.callsSinceSeedNonCosmetic());assertArrayEquals(committed,f.bytes);
        java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();result.write(f.bytes);
        for(int i=4;i<=6;i++) {result.write(f.species[i].getNormalPalette().toBytes());result.write(f.species[i].getShinyPalette().toBytes());}
        for(int i=0;i<100;i++) result.write(java.nio.ByteBuffer.allocate(4).putInt(rng.getNonCosmetic().nextInt()).array());
        return result.toByteArray();
    }

    @Test
    void pickupTargetsForceChangeAndEachIndependentEvolutionPaletteOptionHaveDisjointWritesAndGameplayRng() throws Exception {
        for(long seed:new long[]{0,1,677,20261005658L}) for(int improvement:new int[]{0,1,2}) {
            byte[] control=combinedReplay(seed,improvement,2,false);
            for(int palette:new int[]{2,4}) {
                byte[] output=combinedReplay(seed,improvement,palette,true);
                assertArrayEquals(output,combinedReplay(seed,improvement,palette,true));
                assertArrayEquals(Arrays.copyOfRange(control,control.length-400,control.length),Arrays.copyOfRange(output,output.length-400,output.length));
            }
        }
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
