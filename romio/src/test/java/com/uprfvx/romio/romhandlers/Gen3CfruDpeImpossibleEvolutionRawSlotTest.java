package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Exact public source slots and synthetic memory; never loads or saves a ROM file. */
class Gen3CfruDpeImpossibleEvolutionRawSlotTest {
    static <T extends CfruDpeEvolutionFixture> T nativeOwners(T f) throws Exception {
        Species giga=new Species(133); giga.setName("Eevee-Giga"); giga.setSpeciesSetIdentityNumber(1270);
        f.species[1270]=giga; f.pool.set(1269,giga);
        // Native 373 is Dex 366: exercise Clamperl's policy using the real identity.
        Species clamperl=new Species(366); clamperl.setName("Clamperl"); clamperl.setSpeciesSetIdentityNumber(373);
        f.species[373]=clamperl; f.pool.set(372,clamperl);
        List<Species> loaded=new ArrayList<>(); loaded.add(null); loaded.addAll(f.pool);
        f.setField("speciesList",loaded);
        f.setField("pokesInternal",Arrays.copyOf(f.species,1440));
        int[] map=new int[1440]; for(int id=1;id<1440;id++) map[id]=f.species[id].getNumber();
        f.setField("internalToPokedex",map);
        Species[] dex=f.species.clone(); dex[133]=giga; f.setField("pokes",dex);
        return f;
    }

    private static CfruDpeEvolutionFixture fullEevee() throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source()==133 || s.source()==1270)
                .forEach(s -> f.entry(s.source(),s.slot(),s.method(),s.parameter(),s.target(),s.auxiliary()));
        // Future/unknown slots, holes and empty metadata are unowned preservation bytes.
        f.entry(133,10,0xFFFF,0xBEEF,94,0xCAFE);
        f.entry(133,13,35,19,94,0xBEEF); f.entry(133,15,0,0xCAFE,0,0xBEEF);
        f.loadEvolutions(); return f;
    }

    @Test
    void fullEeveeOnlyFourOwnedWordsChangeAndEveryOtherTableByteStaysExact() throws Exception {
        var f=fullEevee(); byte[] before=f.memory.clone(),expected=before.clone();
        word(expected,133,0,0,7); word(expected,133,0,2,93);
        word(expected,133,1,0,7); word(expected,133,1,2,94);
        f.preflightCfruDpeImpossibleEvolutions(false,false);
        assertArrayEquals(before,f.memory); assertTrue(f.getPreImprovedEvolutions().isEmpty());
        f.removeImpossibleEvolutions(false,false); assertArrayEquals(before,f.memory);
        f.removeImpossibleEvolutions(true,false); f.write(); f.write();
        assertArrayEquals(expected,f.memory,"all 1440x16x8 rows, all fields and both boundaries");
        assertEquals(196,f.word(133,0,4)); assertEquals(197,f.word(133,1,4));
        assertEquals(1,f.word(133,2,6)); assertEquals(253,f.word(133,8,0));
        assertEquals(1270,f.word(133,8,4)); assertEquals(253,f.word(1270,0,0));
        for(int replay=0;replay<3;replay++) {
            f.loadEvolutions(); assertEquals(7,f.species[133].getEvolutionsFrom().size());
            f.preflightCfruDpeImpossibleEvolutions(false,false); f.removeImpossibleEvolutions(false,false); f.write();
            assertArrayEquals(expected,f.memory);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void allExactSourceImpossibleSlotsHaveAnExclusiveMethodParameterBudget(boolean estimated) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.populateExactSource();
        f.pool.forEach(owner -> owner.getEvolutionsFrom().forEach(edge -> edge.setEstimatedEvoLvl(45)));
        byte[] before=f.memory.clone(),expected=before.clone(); int candidates=0,preserved=0,changes=0;
        var inventory=CfruDpeEvolutionFixture.inventory();
        for(var slot : inventory) if(Set.of(2,3,5,6).contains(slot.method())
                && com.uprfvx.romio.services.SpecialFormPredicates.cfruDpePoolCategory(f.species[slot.source()]).eligible()) {
            candidates++;
            if(slot.method()==6) {
                assertEquals(1,inventory.stream().filter(item -> item.source()==slot.source() && item.method()==7
                        && item.parameter()==slot.parameter() && item.target()==slot.target() && item.auxiliary()==0).count());
                preserved++; continue;
            }
            int method=slot.method()==5 ? 4 : 7;
            int parameter=slot.method()==5 ? (estimated ? 45 : 37) : (slot.method()==2 ? 93 : 94);
            word(expected,slot.source(),slot.slot(),0,method); word(expected,slot.source(),slot.slot(),2,parameter);
            changes++;
        }
        assertEquals(37,candidates); assertEquals(17,preserved); assertEquals(20,changes);
        f.preflightCfruDpeImpossibleEvolutions(false,false); assertArrayEquals(before,f.memory);
        f.removeImpossibleEvolutions(false,estimated); f.write(); assertArrayEquals(expected,f.memory);
        f.loadEvolutions(); f.removeImpossibleEvolutions(false,estimated); f.write(); assertArrayEquals(expected,f.memory);
    }

    @ParameterizedTest
    @CsvSource({"61,1,2,187,186", "79,1,2,187,199", "95,0,1,199,208", "112,1,0,88,517",
            "117,0,1,201,230", "123,0,1,199,212", "123,2,3,732,1252", "125,1,0,89,519",
            "126,0,1,90,520", "137,1,0,218,233", "233,0,1,91,527", "328,1,0,176,329",
            "362,1,0,92,530", "373,2,0,192,374", "373,3,1,193,375", "790,1,0,177,791", "792,1,0,178,793"})
    void allSeventeenPinnedTradeItemPairsKeepTheirOriginalSlotsAndItemChoice(
            int source,int trade,int item,int parameter,int target) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source()==source)
                .forEach(s -> f.entry(s.source(),s.slot(),s.method(),s.parameter(),s.target(),s.auxiliary()));
        f.entry(source,15,0xFFFF,0xBEEF,94,0xCAFE); f.loadEvolutions();
        assertEquals(6,f.word(source,trade,0)); assertEquals(7,f.word(source,item,0));
        for(int slot : new int[]{trade,item}) {
            assertEquals(parameter,f.word(source,slot,2)); assertEquals(target,f.word(source,slot,4));
            assertEquals(0,f.word(source,slot,6));
        }
        byte[] before=f.memory.clone(); var beforeGraph=graph(f);
        for(boolean estimated : new boolean[]{false,true}) {
            f.species[source].getEvolutionsFrom().forEach(e -> e.setEstimatedEvoLvl(45));
            f.preflightCfruDpeImpossibleEvolutions(false,false); f.removeImpossibleEvolutions(false,estimated);
            assertEquals(beforeGraph,graph(f)); assertTrue(f.getPreImprovedEvolutions().isEmpty());
            f.write(); assertArrayEquals(before,f.memory); f.loadEvolutions();
            assertEquals(target,itemUseTarget(f,source,parameter));
        }
    }

    @Test
    void scytherAndClamperlKeepTwoDistinctItemChoicesWithoutLevelTriggers() throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.populateExactSource();
        f.removeImpossibleEvolutions(false,false); f.write(); f.loadEvolutions();
        for(int source : new int[]{123,373})
            for(int slot=0;slot<16;slot++) assertNotEquals(4,f.word(source,slot,0));
        assertEquals(212,itemUseTarget(f,123,199)); assertEquals(1252,itemUseTarget(f,123,732));
        assertEquals(374,itemUseTarget(f,373,192)); assertEquals(375,itemUseTarget(f,373,193));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void nonredundantClamperlKeepsToothLevelAndScaleWaterStonePolicy(boolean estimated) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        f.entry(373,2,6,192,374,0); f.entry(373,3,6,193,375,0); f.loadEvolutions();
        f.species[373].getEvolutionsFrom().forEach(e -> e.setEstimatedEvoLvl(45));
        byte[] expected=f.memory.clone();
        word(expected,373,2,0,4); word(expected,373,2,2,estimated ? 45 : 30);
        word(expected,373,3,0,7); word(expected,373,3,2,97);
        f.removeImpossibleEvolutions(false,estimated); f.write(); assertArrayEquals(expected,f.memory);
        assertEquals(375,itemUseTarget(f,373,97));
        f.loadEvolutions(); f.removeImpossibleEvolutions(false,estimated); f.write(); assertArrayEquals(expected,f.memory);
    }

    @ParameterizedTest
    @CsvSource({"95,199,208", "328,176,329", "123,732,1252", "61,187,186", "79,187,199", "117,201,230"})
    void nonredundantTradeItemsRetainTheExistingSpeciesSpecificPolicy(int source,int item,int target) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.entry(source,3,6,item,target,0); f.loadEvolutions();
        byte[] expected=f.memory.clone(); int method=source==79 ? 7 : 4;
        int parameter=source==61 ? 37 : source==117 ? 40 : source==79 ? 97 : 30;
        word(expected,source,3,0,method); word(expected,source,3,2,parameter);
        f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
    }

    @Test
    void sameDexForeignOwnerAndDifferentItemOrTargetDoNotProveRedundancy() throws Exception {
        var f=new CfruDpeEvolutionFixture(); Species regional=new Species(95);
        regional.setName("SyntheticRegionalOwner"); regional.setSpeciesSetIdentityNumber(1026);
        f.species[1026]=regional; f.pool.set(1025,regional); nativeOwners(f);
        f.entry(95,0,6,199,208,0); f.entry(95,2,7,201,208,0); f.entry(95,3,7,199,94,0);
        f.entry(1026,0,7,199,208,0); f.loadEvolutions(); byte[] expected=f.memory.clone();
        word(expected,95,0,0,4); word(expected,95,0,2,30);
        f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void sourceProvenPairRequiresItsTargetsToRemainTogetherInAValidatedTargetPlan(boolean together) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        f.entry(123,0,6,199,212,0); f.entry(123,1,7,199,212,0); f.loadEvolutions();
        var targets=f.getTargetOnlyEvolutionGraph();
        targets.get(f.species[123]).stream().filter(e -> together || e.getExtraInfo()==0).forEach(e -> e.setTo(f.species[2]));
        f.applyTargetOnlyEvolutionGraph(targets); byte[] before=f.memory.clone(); var beforeGraph=graph(f);
        if(together) {
            byte[] expected=before.clone(); word(expected,123,0,4,2); word(expected,123,1,4,2);
            f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
            assertEquals(6,f.word(123,0,0)); assertEquals(2,itemUseTarget(f,123,199));
        } else {
            var error=assertThrows(RomIOException.class,() -> f.preflightCfruDpeImpossibleEvolutions(false,false));
            assertTrue(error.getMessage().contains("item alternative drift"));
            assertThrows(RomIOException.class,() -> f.removeImpossibleEvolutions(false,false));
            assertArrayEquals(before,f.memory); assertEquals(beforeGraph,graph(f)); assertTrue(f.getPreImprovedEvolutions().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,5,6})
    void ambiguousConditionalOrDriftedItemAlternativeRejectsBeforeEarlierOwnerPublication(int mode) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.entry(67,0,5,0,68,0);
        f.entry(95,0,6,199,208,0); f.entry(95,1,7,199,208,0);
        if(mode==0) f.entry(95,4,7,199,208,0);
        if(mode==1) f.entry(95,1,7,199,208,1);
        if(mode==5) f.entry(95,4,6,199,208,0);
        if(mode==6) { f.entry(95,0,6,101,208,0); f.entry(95,1,7,101,208,0); } // Dawn Stone is gender-gated even with aux zero.
        f.loadEvolutions();
        if(mode==2) f.entry(95,1,7,201,208,0);
        if(mode==3) f.entry(95,1,7,199,94,0);
        if(mode==4) f.entry(95,1,7,199,208,0xBEEF);
        assertEarlyImpossibleFailure(f);
    }

    @ParameterizedTest
    @ValueSource(ints={1,4,8,9,10,11,12,13,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,35,37,38,40,41,42})
    void newUnconditionalLevelCannotCompeteWithAnyOtherNormalConsumerTarget(int method) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.entry(64,0,5,0,65,0);
        f.entry(67,0,5,0,68,0); f.entry(67,15,method,40,94,0); f.loadEvolutions();
        assertEarlyImpossibleFailure(f);
    }

    @ParameterizedTest
    @ValueSource(ints={7,34,36,39})
    void newStoneCannotCompeteWithConditionalOrOrdinaryUseOfTheSameItem(int method) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.entry(67,0,5,0,68,0);
        f.entry(133,0,2,0,196,0); f.entry(133,15,method,93,94,0); f.loadEvolutions();
        assertEarlyImpossibleFailure(f);
    }

    @ParameterizedTest
    @ValueSource(ints={7,34,36,39})
    void preservedItemRouteMustAlsoBeUnambiguousAgainstEveryItemConsumer(int method) throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.entry(67,0,5,0,68,0);
        f.entry(95,0,6,199,208,0); f.entry(95,1,7,199,208,0);
        f.entry(95,15,method,199,94,0); f.loadEvolutions(); assertEarlyImpossibleFailure(f);
    }

    @Test
    void missingScytherItemAlternativesRejectDualLevelThirtyTargets() throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        f.entry(123,0,6,199,212,0); f.entry(123,2,6,732,1252,0); f.loadEvolutions();
        assertEarlyImpossibleFailure(f);
    }

    @Test
    void preservedPairStillRequiresAnExactGraphAtSaveAndVanillaConversionIsUnchanged() throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture());
        f.entry(95,0,6,199,208,0); f.entry(95,1,7,199,208,0); f.loadEvolutions();
        byte[] before=f.memory.clone(); f.removeImpossibleEvolutions(false,false);
        f.species[95].getEvolutionsFrom().getFirst().setTo(f.species[94]);
        assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory);
        f.loadEvolutions(); f.setField("useCfruDpeGen9SpeciesCount",false);
        f.removeImpossibleEvolutions(false,false);
        assertEquals(EvolutionType.LEVEL,f.species[95].getEvolutionsFrom().getFirst().getType());
        assertEquals(30,f.species[95].getEvolutionsFrom().getFirst().getExtraInfo());
    }

    private static void assertEarlyImpossibleFailure(CfruDpeEvolutionFixture f) throws Exception {
        byte[] before=f.memory.clone(); var beforeGraph=graph(f); var logs=new TreeMap<>(f.getPreImprovedEvolutions());
        assertThrows(RomIOException.class,() -> f.preflightCfruDpeImpossibleEvolutions(false,false));
        assertThrows(RomIOException.class,() -> f.removeImpossibleEvolutions(false,false));
        assertArrayEquals(before,f.memory); assertEquals(beforeGraph,graph(f)); assertEquals(logs,f.getPreImprovedEvolutions());
    }

    // Independent CFRU item-use consumer for unconditional source slots: last matching
    // target wins. It never treats a trade slot as an item-use or level-up route.
    private static int itemUseTarget(CfruDpeEvolutionFixture f,int source,int item) {
        int target=0;
        for(int slot=0;slot<16;slot++) if(f.word(source,slot,0)==7 && f.word(source,slot,2)==item)
            target=f.word(source,slot,4);
        return target;
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18})
    void lateInvalidOwnerSlotOrPhysicalDriftRejectsWithoutAnyPublication(int mode) throws Exception {
        var f=fullEevee(); f.entry(67,0,5,0,68,0);
        if(mode==0) f.entry(133,12,2,0,196,0); // duplicate same method/parameter/target
        if(mode==1) f.entry(133,0,2,0,196,0xBEEF);
        if(mode==2) f.entry(133,1,3,0,197,0xCAFE);
        f.loadEvolutions();
        switch(mode) {
            case 3 -> f.entry(133,0,2,0,197,0);
            case 4 -> f.entry(133,0,0,0,0,0);
            case 5 -> f.entry(133,0,253,0,196,0);
            case 6 -> f.entry(133,2,17,23,808,0xBEEF);
            case 7 -> { f.entry(133,0,0,0,0,0); f.entry(133,9,2,0,196,0); }
            case 8 -> f.species[133].getEvolutionsFrom().getFirst().setTo(f.species[197]);
            case 9 -> f.species[133].getEvolutionsFrom().getFirst().setFrom(f.species[67]);
            case 10 -> f.species[133].getEvolutionsFrom().add(new Evolution(f.species[133],f.species[196],EvolutionType.HAPPINESS_DAY,0));
            case 11 -> f.setField("rom",Arrays.copyOf(f.memory,CfruDpeEvolutionFixture.offset(1439,15)+7));
            case 12 -> f.entry(0,3,0,0xBEEF,0,0xCAFE);
            case 13 -> f.species[133].getEvolutionsFrom().getFirst().setTo(new Species(0));
            case 14 -> { Species invalid=new Species(196); invalid.setSpeciesSetIdentityNumber(1440); f.species[133].getEvolutionsFrom().getFirst().setTo(invalid); }
            case 15 -> { var loaded=new ArrayList<>(f.getSpecies()); loaded.remove(f.species[133]); f.setField("speciesList",loaded); f.setField("numRealPokemon",loaded.size()-1); }
            case 16 -> f.species[133].getEvolutionsFrom().getFirst().setForme(1);
            case 17 -> {
                var field=Gen3RomHandler.class.getDeclaredField("romEntry"); field.setAccessible(true);
                ((com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry)field.get(f)).putIntValue("PokemonEvolutions",0x108);
            }
            case 18 -> {
                var field=Gen3RomHandler.class.getDeclaredField("romEntry"); field.setAccessible(true);
                ((com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry)field.get(f)).putIntValue("PokemonCount",1439);
            }
        }
        byte[] before=f.memory.clone(); var graph=graph(f); var logs=new TreeMap<>(f.getPreImprovedEvolutions());
        assertThrows(RomIOException.class,() -> f.preflightCfruDpeImpossibleEvolutions(false,false));
        assertThrows(RomIOException.class,() -> f.removeImpossibleEvolutions(false,false));
        assertArrayEquals(before,f.memory); assertEquals(graph,graph(f)); assertEquals(logs,f.getPreImprovedEvolutions());
        assertEquals(EvolutionType.TRADE,f.species[67].getEvolutionsFrom().getFirst().getType());
    }

    @Test
    void stagedTargetPlanKeepsItsSourceAnchoredTargetWordsWhileF05ChangesOnlyItsOwnWords() throws Exception {
        var f=fullEevee(); byte[] expected=f.memory.clone();
        var graph=f.getTargetOnlyEvolutionGraph();
        var day=graph.get(f.species[133]).stream().filter(e -> e.getExtraInfo()==0).findFirst().orElseThrow();
        day.setTo(f.species[2]); f.applyTargetOnlyEvolutionGraph(graph);
        word(expected,133,0,4,2); word(expected,133,0,0,7); word(expected,133,0,2,93);
        word(expected,133,1,0,7); word(expected,133,1,2,94);
        f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
        assertSame(f.species[2],f.species[133].getEvolutionsFrom().getFirst().getTo());
        f.loadEvolutions(); f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
    }

    @Test
    void targetPlanningAfterStagedF05RejectsWithoutChangingTheAcceptedGraph() throws Exception {
        var f=fullEevee(); f.removeImpossibleEvolutions(false,false);
        byte[] before=f.memory.clone(); var graph=graph(f);
        assertThrows(RomIOException.class,f::getTargetOnlyEvolutionGraph);
        assertArrayEquals(before,f.memory); assertEquals(graph,graph(f));
        f.write(); assertEquals(7,f.word(133,0,0));
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,5,6})
    void postPlanGraphOrRawDriftRejectsBeforeAnyOwnedWrite(int mode) throws Exception {
        var f=fullEevee(); f.removeImpossibleEvolutions(false,false);
        if(mode==0) f.species[133].getEvolutionsFrom().getFirst().updateEvolutionMethod(EvolutionType.LEVEL,99);
        if(mode==1) f.species[133].getEvolutionsFrom().getFirst().setTo(f.species[2]);
        if(mode==2) f.entry(133,2,17,23,808,0xCAFE);
        if(mode==3) f.entry(0,1,0,0xBEEF,0,0xCAFE);
        if(mode==4) {
            var loaded=new ArrayList<>(f.getSpecies()); loaded.remove(f.species[133]);
            f.setField("speciesList",loaded); f.setField("numRealPokemon",loaded.size()-1);
        }
        if(mode==5) {
            Species[] slots=Arrays.copyOf(f.species,1440); slots[133]=new Species(133);
            slots[133].setSpeciesSetIdentityNumber(133); f.setField("pokesInternal",slots);
        }
        if(mode==6) {
            int[] map=new int[1440]; for(int id=1;id<1440;id++) map[id]=f.species[id].getNumber();
            map[133]=999; f.setField("internalToPokedex",map);
        }
        byte[] before=f.memory.clone(); var graph=graph(f);
        assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory); assertEquals(graph,graph(f));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void eachOwnedWordWriteFailureAndReadbackFailureRollsBackTheEntireTable(boolean corrupt) throws Exception {
        class Failing extends CfruDpeEvolutionFixture {
            int count,failAt=Integer.MAX_VALUE; boolean corruptAtFailure;
            Failing() throws Exception {super();}
            @Override protected void writeWord(int offset,int value) {
                super.writeWord(offset,value);
                if(++count==failAt) {
                    if(corruptAtFailure) super.writeWord(offset,value^1);
                    else throw new RomIOException("synthetic F05 write fault");
                }
            }
        }
        for(int fail=1;fail<=4;fail++) {
            var f=nativeOwners(new Failing());
            CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source()==133)
                    .forEach(s -> f.entry(s.source(),s.slot(),s.method(),s.parameter(),s.target(),s.auxiliary()));
            f.loadEvolutions(); f.removeImpossibleEvolutions(false,false);
            byte[] before=f.memory.clone(); var graph=graph(f);
            f.count=0; f.failAt=fail; f.corruptAtFailure=corrupt;
            assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory); assertEquals(graph,graph(f));
            f.failAt=Integer.MAX_VALUE; f.write(); assertEquals(7,f.word(133,0,0));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void fullSourceRollbackAtAllFortyChangedWordsKeepsEveryRedundantPairAndRetries(boolean corrupt) throws Exception {
        class Failing extends CfruDpeEvolutionFixture {
            int count,failAt=Integer.MAX_VALUE;
            Failing() throws Exception {super();}
            @Override protected void writeWord(int offset,int value) {
                super.writeWord(offset,value);
                if(++count==failAt) {
                    if(corrupt) super.writeWord(offset,value^1);
                    else throw new RomIOException("synthetic full-source F05 write fault");
                }
            }
        }
        for(int fail=1;fail<=40;fail++) {
            var f=nativeOwners(new Failing()); f.populateExactSource();
            byte[] before=f.memory.clone(),expected=before.clone();
            for(var s : CfruDpeEvolutionFixture.inventory()) if(Set.of(2,3,5).contains(s.method())) {
                word(expected,s.source(),s.slot(),0,s.method()==5 ? 4 : 7);
                word(expected,s.source(),s.slot(),2,s.method()==5 ? 37 : s.method()==2 ? 93 : 94);
            }
            f.removeImpossibleEvolutions(false,false); var plannedGraph=graph(f);
            f.count=0; f.failAt=fail;
            assertThrows(RomIOException.class,f::write); assertArrayEquals(before,f.memory);
            assertEquals(plannedGraph,graph(f));
            f.count=0; f.failAt=Integer.MAX_VALUE; f.write(); assertEquals(40,f.count);
            assertArrayEquals(expected,f.memory); f.loadEvolutions();
            f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
            assertEquals(212,itemUseTarget(f,123,199)); assertEquals(1252,itemUseTarget(f,123,732));
        }
    }

    @Test
    void freshJvmReplayPreservesExactSlotsAndFullRowAcrossFixedSeeds() throws Exception {
        for(long seed : new long[]{0,1,677,20261005658L}) assertEquals(process(seed),process(seed));
    }

    public static void main(String[] args) throws Exception {
        var f=fullEevee(); long seed=Long.parseLong(args[0]);
        f.species[133].getEvolutionsFrom().forEach(e -> e.setEstimatedEvoLvl(1+(int)(seed%100)));
        f.removeImpossibleEvolutions(false,true); f.write(); f.loadEvolutions(); f.write();
        System.out.println("SYNTHETIC_F05="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(f.memory)));
    }

    private static String process(long seed) throws Exception {
        Set<String> classpath=new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for(ClassLoader l=Gen3CfruDpeImpossibleEvolutionRawSlotTest.class.getClassLoader();l!=null;l=l.getParent())
            if(l instanceof URLClassLoader urls) for(var url:urls.getURLs()) classpath.add(Path.of(url.toURI()).toString());
        Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                String.join(File.pathSeparator,classpath),Gen3CfruDpeImpossibleEvolutionRawSlotTest.class.getName(),Long.toString(seed))
                .redirectErrorStream(true).start();
        assertTrue(p.waitFor(30,TimeUnit.SECONDS)); String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,p.exitValue(),output); assertTrue(output.contains("SYNTHETIC_F05="),output); return output;
    }

    private static List<List<String>> graph(CfruDpeEvolutionFixture f) {
        return f.pool.stream().map(s -> s.getEvolutionsFrom().stream().map(e ->
                e.getFrom().getSpeciesSetIdentityNumber()+"/"+e.getTo().getSpeciesSetIdentityNumber()+"/"+e.getType()+"/"+e.getExtraInfo()+"/"+e.getForme()).toList()).toList();
    }
    private static void word(byte[] bytes,int source,int slot,int field,int value) {
        int o=CfruDpeEvolutionFixture.offset(source,slot)+field; bytes[o]=(byte)value; bytes[o+1]=(byte)(value>>>8);
    }
}
