package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.MegaEvolution;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.TypeTable;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.TypeService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrainerMovesetDecisionTest {

    @Test
    public void emptyBetterMovesetPoolKeepsResetMovesForRandomizedTrainerSpecies() {
        Species randomizedSpecies = species(10, "RandomizedSpecies");
        TrainerPokemon trainerPokemon = pokemon(randomizedSpecies, 6, 33, 81, 78, 0);
        trainerPokemon.setResetMoves(true);
        Trainer trainer = trainer(trainerPokemon);
        TrainerMovesetTestRomHandler handler = TrainerMovesetTestRomHandler.create(
                List.of(trainer), List.of(move(1, "Tackle")), Collections.emptyMap());

        new TrainerMovesetRandomizer(handler.proxy, betterRegularMovesets(), new Random(1))
                .randomizeTrainerMovesets();

        assertTrue(trainerPokemon.isResetMoves());
        assertArrayEquals(new int[] {33, 81, 78, 0}, trainerPokemon.getMoves());
        assertFalse(trainer.pokemonHaveCustomMoves());
    }

    @Test
    public void nonEmptyBetterMovesetPoolWritesMovesAndClearsResetMoves() {
        Species randomizedSpecies = species(10, "RandomizedSpecies");
        TrainerPokemon trainerPokemon = pokemon(randomizedSpecies, 6, 33, 81, 78, 0);
        trainerPokemon.setResetMoves(true);
        Trainer trainer = trainer(trainerPokemon);
        Map<Integer, List<MoveLearnt>> movesLearnt = new HashMap<>();
        movesLearnt.put(randomizedSpecies.getNumber(), List.of(new MoveLearnt(1, 1), new MoveLearnt(2, 5)));
        TrainerMovesetTestRomHandler handler = TrainerMovesetTestRomHandler.create(
                List.of(trainer), List.of(move(1, "Scratch"), move(2, "Ember")), movesLearnt);

        new TrainerMovesetRandomizer(handler.proxy, betterRegularMovesets(), new Random(1))
                .randomizeTrainerMovesets();

        assertFalse(trainerPokemon.isResetMoves());
        assertArrayEquals(new int[] {1, 2, 0, 0}, trainerPokemon.getMoves());
        assertTrue(trainer.pokemonHaveCustomMoves());
    }

    @Test
    public void betterMovesetsCompactsNoneMoveOutOfFirstSlot() {
        Species randomizedSpecies = species(10, "RandomizedSpecies");
        TrainerPokemon trainerPokemon = pokemon(randomizedSpecies, 6, 33, 81, 78, 0);
        trainerPokemon.setResetMoves(true);
        Map<Integer, List<MoveLearnt>> movesLearnt = new HashMap<>();
        movesLearnt.put(randomizedSpecies.getNumber(), List.of(
                new MoveLearnt(0, 1),
                new MoveLearnt(1, 1),
                new MoveLearnt(2, 5),
                new MoveLearnt(3, 6)));
        TrainerMovesetTestRomHandler handler = TrainerMovesetTestRomHandler.create(
                List.of(trainer(trainerPokemon)),
                List.of(move(1, "Scratch"), move(2, "Ember"), move(3, "Lick")),
                movesLearnt);

        new TrainerMovesetRandomizer(handler.proxy, betterRegularMovesets(), new Random(1))
                .randomizeTrainerMovesets();

        assertFalse(trainerPokemon.isResetMoves());
        assertArrayEquals(new int[] {1, 2, 3, 0}, trainerPokemon.getMoves());
    }

    private static Settings betterRegularMovesets() {
        Settings settings = new Settings();
        settings.setBetterRegularTrainerMovesets(true);
        return settings;
    }

    private static Trainer trainer(TrainerPokemon... pokemon) {
        Trainer trainer = new Trainer();
        trainer.setPokemon(new ArrayList<>(List.of(pokemon)));
        return trainer;
    }

    private static TrainerPokemon pokemon(Species species, int level, int... moves) {
        TrainerPokemon trainerPokemon = new TrainerPokemon();
        trainerPokemon.setSpecies(species);
        trainerPokemon.setLevel(level);
        trainerPokemon.setMoves(moves);
        return trainerPokemon;
    }

    private static Species species(int number, String name) {
        Species species = new Species(number);
        species.setName(name);
        species.setPrimaryType(Type.NORMAL);
        species.setAttack(50);
        species.setDefense(50);
        species.setSpatk(50);
        species.setSpdef(50);
        species.setSpeed(50);
        return species;
    }

    private static Move move(int number, String name) {
        Move move = new Move();
        move.number = number;
        move.name = name;
        move.type = Type.NORMAL;
        move.category = MoveCategory.PHYSICAL;
        move.power = 40;
        move.hitratio = 100;
        return move;
    }

    private static List<Move> moveTable(List<Move> moves) {
        List<Move> moveTable = new ArrayList<>();
        moveTable.add(move(0, "None"));
        moveTable.addAll(moves);
        return moveTable;
    }

    private static class TrainerMovesetTestRomHandler implements InvocationHandler {
        private final List<Trainer> trainers;
        private final List<Move> moves;
        private final Map<Integer, List<MoveLearnt>> movesLearnt;
        private final SpeciesSet speciesSet;
        private RomHandler proxy;
        private RestrictedSpeciesService restrictedSpeciesService;
        private TypeService typeService;

        private TrainerMovesetTestRomHandler(List<Trainer> trainers, List<Move> moves,
                                             Map<Integer, List<MoveLearnt>> movesLearnt) {
            this.trainers = trainers;
            this.moves = moveTable(moves);
            this.movesLearnt = movesLearnt;
            this.speciesSet = speciesSet(trainers);
        }

        private static TrainerMovesetTestRomHandler create(List<Trainer> trainers, List<Move> moves,
                                                           Map<Integer, List<MoveLearnt>> movesLearnt) {
            TrainerMovesetTestRomHandler handler = new TrainerMovesetTestRomHandler(trainers, moves, movesLearnt);
            handler.proxy = (RomHandler) Proxy.newProxyInstance(
                    RomHandler.class.getClassLoader(), new Class<?>[] {RomHandler.class}, handler);
            handler.restrictedSpeciesService = new RestrictedSpeciesService(handler.proxy);
            handler.typeService = new TypeService(handler.proxy);
            handler.restrictedSpeciesService.setRestrictions(null);
            return handler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getRestrictedSpeciesService" -> restrictedSpeciesService;
                case "getTypeService" -> typeService;
                case "getSpeciesSetInclFormes", "getSpeciesSet" -> speciesSet;
                case "getMegaEvolutions" -> Collections.<MegaEvolution>emptyList();
                case "getAltFormes", "getIrregularFormes" -> new SpeciesSet();
                case "getTrainers" -> trainers;
                case "getMoves" -> moves;
                case "getMovesLearnt" -> movesLearnt;
                case "getEggMoves" -> Collections.<Integer, List<Integer>>emptyMap();
                case "getTMHMCompatibility" -> Collections.<Species, boolean[]>emptyMap();
                case "getTMMoves", "getMoveTutorMoves" -> Collections.<Integer>emptyList();
                case "hasMoveTutors" -> false;
                case "getAltFormeOfSpecies" -> args[0];
                case "altFormesCanHaveDifferentEvolutions" -> false;
                case "abilitiesPerSpecies" -> 0;
                case "getTypeTable" -> new TypeTable(List.of(Type.NORMAL));
                case "generationOfPokemon" -> 3;
                case "getPerfectAccuracy" -> 101;
                case "toString" -> "TrainerMovesetTestRomHandler";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private static SpeciesSet speciesSet(List<Trainer> trainers) {
            SpeciesSet speciesSet = new SpeciesSet();
            for (Trainer trainer : trainers) {
                for (TrainerPokemon trainerPokemon : trainer.getPokemon()) {
                    speciesSet.add(trainerPokemon.getSpecies());
                }
            }
            return speciesSet;
        }
    }
}
