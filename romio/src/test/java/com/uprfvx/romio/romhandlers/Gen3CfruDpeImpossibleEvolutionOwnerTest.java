package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Native owner regression; public source inventory and synthetic memory only. */
class Gen3CfruDpeImpossibleEvolutionOwnerTest {
    private CfruDpeEvolutionFixture fixture() throws Exception {
        var f = new CfruDpeEvolutionFixture();
        replaceOwner(f, 1270, 133);
        f.setField("pokesInternal", Arrays.copyOf(f.species, 1440));
        int[] mapping = new int[1440];
        for (int id = 1; id < mapping.length; id++) mapping[id] = f.species[id].getNumber();
        f.setField("internalToPokedex", mapping);
        // Same projection overwrite as loadSpeciesStats: later Eevee-Giga wins Dex 133.
        mapping[1270] = 133;
        Species[] dex = f.species.clone(); dex[133] = f.species[1270];
        f.setField("pokes", dex);
        return f;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void baseEeveeGetsItsOwnDayNightEdgesAndGigaDoesNot(boolean estimated) throws Exception {
        var f = fixture();
        f.entry(133, 0, 2, 0, 196, 0); f.entry(133, 1, 3, 0, 197, 0);
        f.entry(1270, 0, 253, 0, 133, 0); f.loadEvolutions();
        f.species[133].getEvolutionsFrom().forEach(e -> e.setEstimatedEvoLvl(42));
        byte[] before = f.memory.clone();
        f.removeImpossibleEvolutions(true, estimated);
        var edges = f.species[133].getEvolutionsFrom();
        assertEquals(List.of(EvolutionType.STONE, EvolutionType.STONE), edges.stream().map(Evolution::getType).toList());
        assertEquals(List.of(ItemIDs.sunStone, ItemIDs.moonStone), edges.stream().map(Evolution::getExtraInfo).toList());
        assertSame(f.species[196], edges.get(0).getTo()); assertSame(f.species[197], edges.get(1).getTo());
        assertTrue(edges.stream().allMatch(e -> e.getFrom() == f.species[133]));
        assertArrayEquals(before, f.memory); assertTrue(f.species[1270].getEvolutionsFrom().isEmpty());
        f.write();
        byte[] expected = before.clone();
        setWord(expected, 133, 0, 0, 7); setWord(expected, 133, 0, 2, 93);
        setWord(expected, 133, 1, 0, 7); setWord(expected, 133, 1, 2, 94);
        assertArrayEquals(expected, f.memory, "all 1440 physical rows and boundaries");
        f.loadEvolutions(); f.removeImpossibleEvolutions(false, estimated); f.write();
        assertArrayEquals(expected, f.memory);
    }

    @Test
    void completeOriginalEeveeGraphWritesOnlyFriendshipSlotWords() throws Exception {
        var f = fixture();
        CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source() == 133 || s.source() == 1270)
                .forEach(s -> f.entry(s.source(), s.slot(), s.method(), s.parameter(), s.target(), s.auxiliary()));
        f.loadEvolutions(); byte[] before = f.memory.clone();
        f.removeImpossibleEvolutions(false, false);
        assertEquals(EvolutionType.STONE, f.species[133].getEvolutionsFrom().get(0).getType());
        assertEquals(EvolutionType.STONE, f.species[133].getEvolutionsFrom().get(1).getType());
        byte[] expected=before.clone();
        setWord(expected,133,0,0,7); setWord(expected,133,0,2,93);
        setWord(expected,133,1,0,7); setWord(expected,133,1,2,94);
        f.write(); assertArrayEquals(expected, f.memory, "Sylveon auxiliary and G-Max slots remain exact");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameDexOrdinaryRegionalAndPersistentOwnersKeepDistinctTargets(boolean estimated) throws Exception {
        var f = fixture(); int[] ids = {67, 93, 1026, 0x410};
        int[] map = new int[1440]; for (int id = 1; id < 1440; id++) map[id] = f.species[id].getNumber();
        for (int id : ids) { replaceOwner(f, id, 67); map[id] = 67; f.entry(id, 0, 5, 0, id == 67 ? 68 : 94, 0); }
        f.setField("pokesInternal", Arrays.copyOf(f.species, 1440));
        f.setField("internalToPokedex", map); f.loadEvolutions();
        for (int id : ids) f.species[id].getEvolutionsFrom().getFirst().setEstimatedEvoLvl(45);
        byte[] before = f.memory.clone(), expected = before.clone();
        f.removeImpossibleEvolutions(false, estimated);
        for (int id : ids) {
            var e = f.species[id].getEvolutionsFrom().getFirst();
            assertEquals(EvolutionType.LEVEL, e.getType()); assertEquals(estimated ? 45 : 37, e.getExtraInfo());
            assertSame(f.species[id], e.getFrom()); assertSame(f.species[id == 67 ? 68 : 94], e.getTo());
            setWord(expected, id, 0, 0, 4); setWord(expected, id, 0, 2, estimated ? 45 : 37);
        }
        f.write(); assertArrayEquals(expected, f.memory);
    }

    @Test
    void exactMachokeHaunterSourceTradesRetainLinkCableSlots() throws Exception {
        var f = fixture();
        CfruDpeEvolutionFixture.inventory().stream().filter(s -> s.source() == 67 || s.source() == 93)
                .forEach(s -> f.entry(s.source(), s.slot(), s.method(), s.parameter(), s.target(), s.auxiliary()));
        f.loadEvolutions(); byte[] expected = f.memory.clone();
        f.removeImpossibleEvolutions(false, false);
        for (int id : new int[]{67,93}) { setWord(expected,id,0,0,4); setWord(expected,id,0,2,37); }
        f.write(); assertArrayEquals(expected,f.memory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeFeebasTradeItemUsesExistingLevelPolicy(boolean estimated) throws Exception {
        var f=fixture();
        CfruDpeEvolutionFixture.inventory().stream().filter(slot -> slot.source()==328)
                .forEach(slot -> f.entry(slot.source(),slot.slot(),slot.method(),slot.parameter(),slot.target(),slot.auxiliary()));
        f.loadEvolutions(); var edge=f.species[328].getEvolutionsFrom().get(1);
        assertEquals(EvolutionType.TRADE_ITEM,edge.getType()); edge.setEstimatedEvoLvl(43);
        byte[] expected=f.memory.clone(); f.removeImpossibleEvolutions(false,estimated);
        setWord(expected,328,1,0,4); setWord(expected,328,1,2,estimated ? 43 : 30);
        f.write(); assertArrayEquals(expected,f.memory); assertSame(f.species[329],edge.getTo());
    }

    @Test
    void unsupportedInvalidAndOpaqueRowsRemainByteExactOnNoOp() throws Exception {
        var f = fixture();
        for (int id : new int[]{0, 0xFC, 1270, 0x592, 0x343}) f.entry(id,0,5,0,94,0);
        f.entry(1,5,0,0xBEEF,0,0xCAFE); f.entry(1,7,0xFFFF,7,94,0xABCD);
        f.entry(1,10,4,16,2,0); f.entry(1,12,4,16,2,0);
        f.loadEvolutions(); byte[] before = f.memory.clone();
        f.removeImpossibleEvolutions(true,false); f.write(); assertArrayEquals(before,f.memory);
        for (int id : new int[]{0xFC,1270,0x592,0x343})
            assertEquals(EvolutionType.TRADE,f.species[id].getEvolutionsFrom().getFirst().getType());
    }

    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3,4,5,6})
    void conflictingOwnerOrEdgeRejectsBeforeEarlierOwnerMutation(int mode) throws Exception {
        var f = fixture(); f.entry(67,0,5,0,68,0); f.entry(133,0,2,0,196,0); f.loadEvolutions();
        List<Species> owners = new ArrayList<>(f.getSpecies());
        switch (mode) {
            case 0 -> owners.add(f.species[133]);
            case 1 -> { var impostor = new Species(133); impostor.setSpeciesSetIdentityNumber(133); owners.add(impostor); }
            case 2 -> f.species[133].setSpeciesSetIdentityNumber(134);
            case 3 -> { int[] map = new int[1440]; f.setField("internalToPokedex",map); }
            case 4 -> f.species[133].getEvolutionsFrom().getFirst().setTo(new Species(196));
            case 5 -> f.species[133].getEvolutionsFrom().add(new Evolution(f.species[67],f.species[196],EvolutionType.TRADE,0));
            case 6 -> f.setField("pokesInternal",f.species);
        }
        f.setField("speciesList",owners); byte[] before = f.memory.clone();
        assertThrows(RomIOException.class,() -> f.removeImpossibleEvolutions(false,false));
        assertEquals(EvolutionType.TRADE,f.species[67].getEvolutionsFrom().getFirst().getType());
        assertArrayEquals(before,f.memory); assertTrue(f.getPreImprovedEvolutions().isEmpty());
    }

    @Test
    void invalidZeroAnd1440OwnerObjectsAreNotVisited() throws Exception {
        var f = fixture(); var owners = new ArrayList<>(f.getSpecies());
        for (int id : new int[]{0,1440}) {
            var invalid = new Species(67); invalid.setSpeciesSetIdentityNumber(id);
            invalid.getEvolutionsFrom().add(new Evolution(invalid,f.species[68],EvolutionType.TRADE,0)); owners.add(invalid);
        }
        f.setField("speciesList",owners); f.loadEvolutions();
        f.removeImpossibleEvolutions(false,false);
        for (int i = owners.size()-2; i < owners.size(); i++)
            assertEquals(EvolutionType.TRADE,owners.get(i).getEvolutionsFrom().getFirst().getType());
    }

    @Test
    void vanillaPathStillVisitsOnlyDexProjection() throws Exception {
        var f = fixture(); f.entry(133,0,2,0,196,0); f.loadEvolutions();
        f.setField("useCfruDpeGen9SpeciesCount",false);
        f.removeImpossibleEvolutions(false,false);
        assertEquals(EvolutionType.HAPPINESS_DAY,f.species[133].getEvolutionsFrom().getFirst().getType());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void beautyKeepsEstimatedLevelSemantics(boolean estimated) throws Exception {
        var f = fixture(); f.entry(328,0,15,170,329,0); f.loadEvolutions();
        // Generic supported beauty field, synthetic only (native DPE Feebas uses trade-item).
        var edge = f.species[328].getEvolutionsFrom().getFirst();
        assertEquals(EvolutionType.HIGH_BEAUTY, edge.getType()); edge.setEstimatedEvoLvl(41);
        byte[] expected=f.memory.clone(); f.removeImpossibleEvolutions(false,estimated);
        assertEquals(estimated ? 41 : 35, edge.getExtraInfo());
        setWord(expected,328,0,0,4); setWord(expected,328,0,2,estimated ? 41 : 35);
        f.write(); assertArrayEquals(expected,f.memory);
    }

    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3})
    void opaqueRowsRetainExactSlotsAndRejectAmbiguousOrActiveAuxiliary(int mode) throws Exception {
        var f=fixture(); f.entry(133,0,2,0,196,0);
        switch (mode) {
            case 0 -> f.entry(133,0,2,0,196,0xBEEF);
            case 1 -> f.entry(133,3,3,0,197,0); // hole before changed edge
            case 2 -> f.entry(133,1,2,0,196,0); // duplicate modeled relationship
            case 3 -> f.entry(133,15,0xFFFF,0xCAFE,94,0xABCD);
        }
        f.loadEvolutions(); byte[] before=f.memory.clone();
        if (mode == 0 || mode == 2) {
            assertThrows(RomIOException.class,() -> f.removeImpossibleEvolutions(false,false));
            assertArrayEquals(before,f.memory); assertTrue(f.getPreImprovedEvolutions().isEmpty());
        } else {
            byte[] expected=before.clone();
            setWord(expected,133,0,0,7); setWord(expected,133,0,2,93);
            if (mode == 1) { setWord(expected,133,3,0,7); setWord(expected,133,3,2,94); }
            f.removeImpossibleEvolutions(false,false); f.write(); assertArrayEquals(expected,f.memory);
        }
    }

    @Test
    void ownerTraversalIsStableIdOrderEvenWhenLoadedListIsReversed() throws Exception {
        class RecordingFixture extends CfruDpeEvolutionFixture {
            final List<Integer> visited=new ArrayList<>();
            RecordingFixture() throws Exception { super(); }
            @Override protected void markImprovedEvolutions(Species owner) {
                visited.add(owner.getSpeciesSetIdentityNumber()); super.markImprovedEvolutions(owner);
            }
        }
        var f=new RecordingFixture();
        f.setField("pokesInternal",Arrays.copyOf(f.species,1440));
        int[] map=new int[1440]; for (int id=1;id<1440;id++) map[id]=id;
        f.setField("internalToPokedex",map);
        for (int id : new int[]{133,93,67}) f.entry(id,0,5,0,94,0);
        f.loadEvolutions(); var owners=new ArrayList<>(f.getSpecies()); Collections.reverse(owners.subList(1,owners.size()));
        f.setField("speciesList",owners); f.removeImpossibleEvolutions(false,false);
        assertEquals(List.of(67,93,133),f.visited);
    }

    private static void replaceOwner(CfruDpeEvolutionFixture f, int id, int dex) throws Exception {
        Species owner = new Species(dex); owner.setSpeciesSetIdentityNumber(id); owner.setName("Owner" + id);
        f.species[id] = owner; f.pool.set(id - 1, owner);
        List<Species> loaded = new ArrayList<>(); loaded.add(null); loaded.addAll(f.pool);
        f.setField("speciesList", loaded);
    }

    private static void setWord(byte[] bytes,int source,int slot,int field,int value) {
        int offset=CfruDpeEvolutionFixture.offset(source,slot)+field;
        bytes[offset]=(byte)value; bytes[offset+1]=(byte)(value>>>8);
    }
}
