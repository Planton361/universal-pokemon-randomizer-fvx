package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TrainerHeldItemIdentityTest {
    @Test
    void sensibleSelectionReceivesInternalLearnsetForBothTrainerIterationModes() throws Exception {
        for (boolean highestOnly : new boolean[] {false, true}) {
            ContextHandler handler = new ContextHandler();
            Species selected = new Species(906);
            selected.setSpeciesSetIdentityNumber(1294);
            TrainerPokemon p = new TrainerPokemon();
            p.setSpecies(new Species(25));
            p.setSpecies(selected); // Replacement leaves explicit identity on the member.
            p.setResetMoves(true);
            p.setLevel(5);
            Trainer t = new Trainer();
            t.setPokemon(new ArrayList<>(List.of(p)));
            handler.team = List.of(t);
            Gen3RomEntry entry = new Gen3RomEntry(
                    Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
            entry.setRomCode("BPRE");
            entry.putIntValue("PokemonCount", 1439);
            setField(handler, "romEntry", entry);
            setField(handler, "isRomHack", true);
            setField(handler, "useCfruDpeGen9SpeciesCount", true);
            int[] dexToInternal = new int[1440];
            dexToInternal[906] = 1294;
            setField(handler, "pokedexToInternal", dexToInternal);
            Settings settings = new Settings();
            settings.setRandomizeHeldItemsForRegularTrainerPokemon(true);
            settings.setSensibleItemsOnlyForTrainers(true);
            settings.setHighestLevelGetsItemsForTrainers(highestOnly);

            new TrainerPokemonRandomizer(handler, settings, new Random(1)).randomizeTrainerHeldItems();

            assertSame(selected, handler.lookupSpecies);
            assertArrayEquals(new int[] {10, 20, 30, 0}, handler.context);
            assertSame(handler.item, p.getHeldItem());
            assertTrue(p.isResetMoves());
            assertEquals(1, handler.lookups);
        }
    }

    private static class ContextHandler extends Gen3RomHandler {
        final Item item = new Item(1, "Synthetic item");
        List<Trainer> team;
        Species lookupSpecies;
        int[] context;
        int lookups;
        @Override public List<Trainer> getTrainers() { return team; }
        @Override public List<Move> getMoves() { return List.of(); }
        @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
            return Map.of(906, List.of(new MoveLearnt(40, 1), new MoveLearnt(50, 3)),
                    1294, List.of(new MoveLearnt(10, 1), new MoveLearnt(20, 3), new MoveLearnt(30, 5)));
        }
        @Override public int[] getMovesAtLevel(Species species,
                                               Map<Integer, List<MoveLearnt>> movesets, int level) {
            lookupSpecies = species;
            lookups++;
            return super.getMovesAtLevel(species, movesets, level);
        }
        @Override public List<Item> getSensibleHeldItemsFor(TrainerPokemon p, boolean consumable,
                                                           List<Move> moves, int[] moveContext) {
            context = moveContext.clone();
            return List.of(item);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
