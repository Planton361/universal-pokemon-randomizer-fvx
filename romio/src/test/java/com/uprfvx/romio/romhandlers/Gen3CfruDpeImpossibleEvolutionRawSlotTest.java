package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    @Test
    void allExactSourceImpossibleSlotsHaveAnExclusiveMethodParameterBudget() throws Exception {
        var f=nativeOwners(new CfruDpeEvolutionFixture()); f.populateExactSource();
        byte[] before=f.memory.clone(),expected=before.clone(); int changes=0;
        for(Species owner : f.species) if(owner!=null
                && com.uprfvx.romio.services.SpecialFormPredicates.cfruDpePoolCategory(owner).eligible()) {
            for(Evolution edge : owner.getEvolutionsFrom()) if(Gen3RomHandler.shouldUpdateImpossibleEvolution(edge,true)) {
                Evolution converted=new Evolution(edge); Gen3RomHandler.updateImpossibleEvolution(converted,true,false);
                var slot=CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source()==owner.getSpeciesSetIdentityNumber()
                        && s.method()==Gen3Constants.evolutionTypeToIndex(edge.getType())
                        && s.target()==edge.getTo().getSpeciesSetIdentityNumber()).findFirst().orElseThrow();
                word(expected,slot.source(),slot.slot(),0,Gen3Constants.evolutionTypeToIndex(converted.getType()));
                word(expected,slot.source(),slot.slot(),2,converted.getType().usesItem()
                        ? Gen3Constants.itemIDToInternal(converted.getExtraInfo()) : converted.getExtraInfo()); changes++;
            }
        }
        assertEquals(37,changes,"all supported impossible slots in the exact public inventory");
        f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
        f.loadEvolutions(); f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
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
