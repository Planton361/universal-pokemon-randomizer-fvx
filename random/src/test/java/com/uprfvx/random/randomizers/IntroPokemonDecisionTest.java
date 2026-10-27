package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.MegaEvolution;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.SpeciesClassificationPolicy;
import com.uprfvx.romio.services.TypeService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntroPokemonDecisionTest {

    @Test
    public void missingOakWitnessStopsFullRandomizerBeforeRestrictionsOrOtherMutations() throws Exception {
        class Fixture extends com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture {
            boolean watching; int restrictionsAccess;
            Fixture() throws Exception {super();}
            @Override public RestrictedSpeciesService getRestrictedSpeciesService() {
                if(watching) restrictionsAccess++;
                return super.getRestrictedSpeciesService();
            }
            @Override public int generationOfPokemon() {return 3;}
            @Override public java.util.List<Integer> getMoveTutorMoves() {return java.util.List.of();}
            @Override public java.util.List<com.uprfvx.romio.gamedata.Trainer> getTrainers() {return java.util.List.of();}
            @Override public boolean canChangeStaticPokemon() {return false;}
            @Override public java.util.List<com.uprfvx.romio.gamedata.InGameTrade> getInGameTrades() {return java.util.List.of();}
            @Override public java.util.List<com.uprfvx.romio.gamedata.Move> getMoves() {return java.util.List.of();}
        }
        Fixture f=new Fixture();var settings=new Settings();settings.setRandomizeIntroMon(true);
        java.util.ResourceBundle bundle=new java.util.ListResourceBundle() {
            @Override protected Object[][] getContents() {return new Object[0][];}
        };
        var randomizer=new com.uprfvx.random.GameRandomizer(settings,null,f,bundle,false);
        byte[] before=f.memory.clone();f.watching=true;
        var result=randomizer.randomize("synthetic-unused",new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),1234);
        assertFalse(result.wasSaveSuccessful());
        assertEquals(0,f.restrictionsAccess);assertTrue(result.getException().getMessage().contains("explicit independent witness"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(before,f.memory);
    }

    @Test
    public void randomizeIntroPokemonRetriesInvalidSpeciesAndNeverAcceptsSpeciesZero() {
        Species invalid = species(0, 0, "Invalid");
        Species valid = species(25, 25, "Pikachu");
        IntroTestRomHandler handler = IntroTestRomHandler.create(List.of(invalid, valid));
        IntroPokemonRandomizer randomizer = new IntroPokemonRandomizer(handler.proxy, new Settings(), new Random(0));

        randomizer.randomizeIntroPokemon();

        assertSame(valid, handler.acceptedIntroSpecies);
        assertSame(valid, randomizer.getIntroSpecies());
        assertEquals(List.of(valid), handler.introAttempts);
    }

    @Test
    public void randomizeIntroPokemonSkipsInsteadOfLoopingWhenNoValidSpeciesExists() {
        Species invalid = species(0, 0, "Invalid");
        IntroTestRomHandler handler = IntroTestRomHandler.create(List.of(invalid));
        IntroPokemonRandomizer randomizer = new IntroPokemonRandomizer(handler.proxy, new Settings(), new Random(0));

        randomizer.randomizeIntroPokemon();

        assertEquals(List.of(), handler.introAttempts);
        assertNull(handler.acceptedIntroSpecies);
        assertNull(randomizer.getIntroSpecies());
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    public void randomizeIntroPokemonAcceptsGen789IdentityCandidateWithSpeciesNumberZero() {
        Species gen6 = species(669, 669, "Flabebe", 6);
        Species gen7IdentityOnly = species(0, 722, "Rowlet", 7);
        GenRestrictions restrictions = new GenRestrictions(0);
        restrictions.setGenAllowed(7, true);
        restrictions.setGenAllowed(8, true);
        restrictions.setGenAllowed(9, true);
        restrictions.setAllowEvolutionaryRelatives(false);
        IntroTestRomHandler handler = IntroTestRomHandler.create(List.of(gen6, gen7IdentityOnly), restrictions);
        IntroPokemonRandomizer randomizer = new IntroPokemonRandomizer(handler.proxy, new Settings(), new Random(0));

        randomizer.randomizeIntroPokemon();

        assertEquals(List.of(gen7IdentityOnly), handler.introAttempts);
        assertSame(gen7IdentityOnly, handler.acceptedIntroSpecies);
        assertSame(gen7IdentityOnly, randomizer.getIntroSpecies());
    }

    @Test
    public void randomizeIntroPokemonRejectsIdentityZeroAndSkipsEmptyRestrictedPool() {
        Species invalidIdentity = species(0, 0, "InvalidIdentity", 7);
        GenRestrictions restrictions = new GenRestrictions(0);
        restrictions.setGenAllowed(7, true);
        restrictions.setAllowEvolutionaryRelatives(false);
        IntroTestRomHandler handler = IntroTestRomHandler.create(List.of(invalidIdentity), restrictions);
        IntroPokemonRandomizer randomizer = new IntroPokemonRandomizer(handler.proxy, new Settings(), new Random(0));

        randomizer.randomizeIntroPokemon();

        assertEquals(List.of(), handler.introAttempts);
        assertNull(handler.acceptedIntroSpecies);
        assertFalse(randomizer.isChangesMade());
    }

    @Test
    public void randomizeIntroPokemonSkipsWhenRestrictedPoolIsEmpty() {
        Species gen6 = species(669, 669, "Flabebe", 6);
        GenRestrictions restrictions = new GenRestrictions(0);
        restrictions.setGenAllowed(7, true);
        restrictions.setAllowEvolutionaryRelatives(false);
        IntroTestRomHandler handler = IntroTestRomHandler.create(List.of(gen6), restrictions);
        IntroPokemonRandomizer randomizer = new IntroPokemonRandomizer(handler.proxy, new Settings(), new Random(0));

        randomizer.randomizeIntroPokemon();

        assertEquals(List.of(), handler.introAttempts);
        assertNull(handler.acceptedIntroSpecies);
        assertNull(randomizer.getIntroSpecies());
        assertFalse(randomizer.isChangesMade());
    }

    private static Species species(int number, int identityNumber, String name) {
        return species(number, identityNumber, name, 1);
    }

    private static Species species(int number, int identityNumber, String name, int generation) {
        Species species = new Species(number);
        species.setSpeciesSetIdentityNumber(identityNumber);
        species.setName(name);
        species.setGeneration(generation);
        return species;
    }

    private static class IntroTestRomHandler implements InvocationHandler {
        private final List<Species> species;
        private final SpeciesSet speciesSet;
        private final List<Species> introAttempts = new ArrayList<>();
        private RomHandler proxy;
        private RestrictedSpeciesService restrictedSpeciesService;
        private TypeService typeService;
        private Species acceptedIntroSpecies;

        private IntroTestRomHandler(List<Species> species) {
            this.species = species;
            this.speciesSet = new SpeciesSet(species);
        }

        private static IntroTestRomHandler create(List<Species> species) {
            return create(species, null);
        }

        private static IntroTestRomHandler create(List<Species> species, GenRestrictions restrictions) {
            IntroTestRomHandler handler = new IntroTestRomHandler(species);
            handler.proxy = (RomHandler) Proxy.newProxyInstance(
                    RomHandler.class.getClassLoader(), new Class<?>[] { RomHandler.class }, handler);
            handler.restrictedSpeciesService = new RestrictedSpeciesService(handler.proxy);
            handler.typeService = new TypeService(handler.proxy);
            handler.restrictedSpeciesService.setRestrictions(restrictions);
            return handler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getSpeciesClassificationPolicy" -> SpeciesClassificationPolicy.legacy();
                case "getRestrictedSpeciesService" -> restrictedSpeciesService;
                case "getTypeService" -> typeService;
                case "getSpeciesSetInclFormes", "getSpeciesSet" -> speciesSet;
                case "getSpeciesInclFormes", "getSpecies" -> species;
                case "getAltFormes", "getIrregularFormes" -> new SpeciesSet();
                case "getMegaEvolutions" -> Collections.<MegaEvolution>emptyList();
                case "setIntroPokemon" -> setIntroPokemon((Species) args[0]);
                case "toString" -> "IntroTestRomHandler";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private boolean setIntroPokemon(Species species) {
            introAttempts.add(species);
            if (species.getSpeciesSetIdentityNumber() <= 0) {
                return false;
            }
            acceptedIntroSpecies = species;
            return true;
        }
    }
}
