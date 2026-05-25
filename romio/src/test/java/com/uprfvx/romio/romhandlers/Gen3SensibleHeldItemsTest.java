package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Gen3SensibleHeldItemsTest {

    @Test
    public void expandedTypeWithoutGen3BoostingItemsDoesNotCrash() throws Exception {
        Gen3RomHandler romHandler = romHandlerWithItems();
        TrainerPokemon pokemon = new TrainerPokemon();
        pokemon.setSpecies(new Species(9999));

        List<Item> sensibleItems = assertDoesNotThrow(() -> romHandler.getSensibleHeldItemsFor(
                pokemon, false, List.of(move(Type.FAIRY, 60)), new int[] {0}));

        assertFalse(sensibleItems.isEmpty());
        assertTrue(sensibleItems.stream().noneMatch(item -> item == null));
        assertTrue(sensibleItems.stream().anyMatch(item -> item.getId() == ItemIDs.petayaBerry));
    }

    @Test
    public void missingSpeciesAndInvalidMoveSlotsAreSkipped() throws Exception {
        Gen3RomHandler romHandler = romHandlerWithItems();

        List<Item> sensibleItems = assertDoesNotThrow(() -> romHandler.getSensibleHeldItemsFor(
                new TrainerPokemon(), false, List.of(move(Type.FIRE, 60)), new int[] {-1, 4, 0}));

        assertFalse(sensibleItems.isEmpty());
        assertTrue(sensibleItems.stream().anyMatch(item -> item.getId() == ItemIDs.charcoal));
    }

    @Test
    public void missingMovepoolReturnsEmptyMoveset() {
        Gen3RomHandler romHandler = new Gen3RomHandler();

        int[] moveset = assertDoesNotThrow(() -> romHandler.getMovesAtLevel(9999, Map.of(), 50));

        assertArrayEquals(new int[] {0, 0, 0, 0}, moveset);
    }

    @Test
    public void nullMovesetsReturnEmptyMoveset() {
        Gen3RomHandler romHandler = new Gen3RomHandler();

        int[] moveset = assertDoesNotThrow(() -> romHandler.getMovesAtLevel(9999, null, 50));

        assertArrayEquals(new int[] {0, 0, 0, 0}, moveset);
    }

    @Test
    public void movesAtLevelSkipsNoneMovePlaceholders() {
        Gen3RomHandler romHandler = new Gen3RomHandler();

        int[] moveset = romHandler.getMovesAtLevel(25, Map.of(25, List.of(
                new MoveLearnt(0, 1),
                new MoveLearnt(59, 20),
                new MoveLearnt(44, 30),
                new MoveLearnt(427, 40))), 47);

        assertArrayEquals(new int[] {59, 44, 427, 0}, moveset);
    }

    @Test
    public void trainerMoveSlotNormalizationCompactsLeadingNoneMove() {
        int[] moveset = Gen3RomHandler.normalizeTrainerMoveSlots(new int[] {0, 59, 44, 427});

        assertArrayEquals(new int[] {59, 44, 427, 0}, moveset);
    }

    @Test
    public void finalTrainerMoveStateNormalizationCompactsOriginalPidgeyMoves() {
        TrainerPokemon pokemon = new TrainerPokemon();
        pokemon.setMoves(new int[] {0, 33, 45, 28});
        Trainer trainer = new Trainer();
        trainer.setPokemon(new ArrayList<>(List.of(pokemon)));

        Gen3RomHandler.normalizeTrainerCustomMoveStateBeforeWrite(trainer);

        assertFalse(pokemon.isResetMoves());
        assertArrayEquals(new int[] {33, 45, 28, 0}, pokemon.getMoves());
        assertTrue(trainer.pokemonHaveCustomMoves());
    }

    @Test
    public void finalTrainerMoveStateNormalizationRestoresResetWhenNoCustomMovesRemain() {
        TrainerPokemon pokemon = new TrainerPokemon();
        pokemon.setMoves(new int[] {0, 0, 0, 0});
        Trainer trainer = new Trainer();
        trainer.setPokemon(new ArrayList<>(List.of(pokemon)));

        Gen3RomHandler.normalizeTrainerCustomMoveStateBeforeWrite(trainer);

        assertTrue(pokemon.isResetMoves());
        assertFalse(trainer.pokemonHaveCustomMoves());
    }

    @Test
    public void cfruDpeHeldItemCustomMoveTrainerWriterUsesExpandedLayout() throws Exception {
        Gen3RomHandler romHandler = new Gen3RomHandler();
        setField(romHandler, "useCfruDpeGen9SpeciesCount", true);
        int[] pokedexToInternal = new int[400];
        for (int i = 0; i < pokedexToInternal.length; i++) {
            pokedexToInternal[i] = i;
        }
        setField(romHandler, "pokedexToInternal", pokedexToInternal);

        TrainerPokemon pokemon = new TrainerPokemon();
        Species species = new Species(25);
        species.setName("Pikachu");
        pokemon.setSpecies(species);
        pokemon.setLevel(9);
        pokemon.setIVs(31);
        pokemon.setAbilitySlot(2);
        pokemon.setNature((byte) 5);
        pokemon.setHeldItem(new Item(ItemIDs.potion, "Potion"));
        pokemon.setMoves(new int[] {343, 643, 116, 68});
        Trainer trainer = new Trainer();
        trainer.setPokemon(new ArrayList<>(List.of(pokemon)));

        Method trainerPokemonToBytes = Gen3RomHandler.class.getDeclaredMethod("trainerPokemonToBytes", Trainer.class);
        trainerPokemonToBytes.setAccessible(true);
        byte[] bytes = (byte[]) trainerPokemonToBytes.invoke(romHandler, trainer);

        assertEquals(Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_MOVES_SIZE, bytes.length);
        assertEquals(2, bytes[6] & 0xFF);
        assertEquals(5, bytes[7] & 0xFF);
        assertEquals(31, bytes[8] & 0xFF);
        assertEquals(Gen3Constants.itemIDToInternal(ItemIDs.potion),
                readWord(bytes, Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_ITEM_OFFSET));
        assertEquals(343, readWord(bytes, Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_MOVES_OFFSET));
        assertEquals(643, readWord(bytes, Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_MOVES_OFFSET + 2));
        assertEquals(116, readWord(bytes, Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_MOVES_OFFSET + 4));
        assertEquals(68, readWord(bytes, Gen3RomHandler.CFRU_DPE_TRAINER_MON_ITEM_CUSTOM_MOVES_OFFSET + 6));
    }

    private static Gen3RomHandler romHandlerWithItems() throws ReflectiveOperationException {
        Gen3RomHandler romHandler = new Gen3RomHandler();
        setField(romHandler, "items", itemsById(512));
        return romHandler;
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static List<Item> itemsById(int size) {
        List<Item> items = new ArrayList<>();
        items.add(null);
        for (int i = 1; i < size; i++) {
            items.add(new Item(i, "Item " + i));
        }
        return items;
    }

    private static Move move(Type type, int power) {
        Move move = new Move();
        move.type = type;
        move.power = power;
        return move;
    }
}
