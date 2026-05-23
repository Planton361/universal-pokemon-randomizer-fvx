package com.uprfvx.romio.gamedata;

import com.uprfvx.romio.constants.SpeciesIDs;
import org.junit.jupiter.api.Test;

import javax.print.attribute.UnmodifiableSetException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SpeciesSetTest {

    private static final double MAX_DEVIATION = 0.1;

    private final Random random = new Random();

    @Test
    public void addingElementMultipleTimesDoesNotAffectGetRandom() {
        Species a = new Species(0);
        a.setName("A");
        Species b = new Species(1);
        b.setName("B");
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(a);
        for (int i = 0; i < 1000; i++) {
            pokes.add(b);
        }

        int[] count = new int[2];
        for (int i = 0; i < 5000; i++) {
            Species pick = pokes.getRandomSpecies(random);
            count[pick.getNumber()]++;
        }

        System.out.println(Arrays.toString(count));
        System.out.println(Math.abs(1 - ((double) count[0] / (double) count[1])));
        assertTrue(Math.abs(1 - ((double) count[0] / (double) count[1])) < MAX_DEVIATION);
    }

    @Test
    public void removingAnElementPreventsItFromGettingChosenByGetRandom() {
        Species a = new Species(0);
        a.setName("A");
        Species b = new Species(1);
        b.setName("B");
        Species c = new Species(2);
        c.setName("C");
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(a);
        pokes.add(b);
        pokes.add(c);
        pokes.remove(b);

        int[] count = new int[3];
        for (int i = 0; i < 5000; i++) {
            Species pick = pokes.getRandomSpecies(random);
            count[pick.getNumber()]++;
        }

        System.out.println(Arrays.toString(count));
        assertEquals(0, count[b.getNumber()]);
    }

    @Test
    public void readdingARemovedElementMakesItChoosableByGetRandom() {
        Species a = new Species(0);
        a.setName("A");
        Species b = new Species(1);
        b.setName("B");
        Species c = new Species(2);
        c.setName("C");
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(a);
        pokes.add(b);
        pokes.add(c);
        pokes.remove(b);
        pokes.add(b);

        int[] count = new int[3];
        for (int i = 0; i < 5000; i++) {
            Species pick = pokes.getRandomSpecies(random);
            count[pick.getNumber()]++;
        }

        System.out.println(Arrays.toString(count));
        assertTrue(count[b.getNumber()] > 0);
    }

    @Test
    public void getRandomSpeciesCountsUnownFormsAsOneFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> unownForms = unownForms(28);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(unownForms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSpecies(random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    @Test
    public void getRandomSpeciesChoosesAllowedUnownFormFromFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> unownForms = unownForms(28);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(unownForms);
        SequenceRandom random = new SequenceRandom(1, 27);

        Species pick = pokes.getRandomSpecies(random);

        assertTrue(unownForms.contains(pick));
        assertEquals(2, random.boundAt(0));
        assertEquals(28, random.boundAt(1));
    }

    @Test
    public void getRandomSpeciesCountsNamedUnownPunctuationFormsAsOneFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> unownForms = new ArrayList<>(unownNamedForms());
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(unownForms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSpecies(random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    @Test
    public void getRandomSpeciesCanChooseAllowedUnownPunctuationFormsFromFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> unownForms = new ArrayList<>(unownNamedForms());
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(unownForms);

        Set<Species> picks = new HashSet<>();
        for(int i = 0; i < unownForms.size(); i++) {
            picks.add(pokes.getRandomSpecies(new SequenceRandom(1, i)));
        }

        assertTrue(picks.containsAll(unownForms));
    }

    @Test
    public void getRandomSpeciesCountsVivillonFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.vivillon, "Vivillon");
    }

    @Test
    public void getRandomSpeciesCountsAlcremieFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.alcremie, "Alcremie");
    }

    @Test
    public void getRandomSpeciesCountsRotomFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.rotom, "Rotom");
    }

    @Test
    public void getRandomSpeciesCountsArceusFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.arceus, "Arceus");
    }

    @Test
    public void getRandomSpeciesCountsSilvallyFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.silvally, "Silvally");
    }

    @Test
    public void getRandomSpeciesCountsDeoxysFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.deoxys, "Deoxys");
    }

    @Test
    public void getRandomSpeciesCountsMiniorFormsAsOneFamilyTicket() {
        assertSelectedFamilyFormsCountAsOneTicket(SpeciesIDs.minior, "Minior");
    }

    @Test
    public void getRandomSpeciesUsesNarrowNameFallbackForSelectedFamilies() {
        Species normal = species(1, "Normal", 1);
        List<Species> rotomForms = Arrays.asList(
                species(9001, "Rotom Heat", 9001),
                species(9002, "Rotom-Wash", 9002));
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(rotomForms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSpecies(random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    @Test
    public void getRandomSpeciesFamilyTicketChoosesOnlyFilteredPoolForms() {
        Species normal = species(1, "Normal", 1);
        List<Species> allowedVivillonForms = selectedFamilyForms(SpeciesIDs.vivillon, "Vivillon", 2, 3000);
        Species omittedVivillonForm = selectedFamilyForm(SpeciesIDs.vivillon, "Vivillon", 9, 3009);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(allowedVivillonForms);

        Set<Species> picks = new HashSet<>();
        for(int i = 0; i < allowedVivillonForms.size(); i++) {
            picks.add(pokes.getRandomSpecies(new SequenceRandom(1, i)));
        }

        assertTrue(picks.containsAll(allowedVivillonForms));
        assertFalse(picks.contains(omittedVivillonForm));
    }

    @Test
    public void getRandomSimilarStrengthSpeciesCountsUnownFormsAsOneFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> unownForms = unownForms(28);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(unownForms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSimilarStrengthSpecies(0, random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    @Test
    public void getRandomSimilarStrengthSpeciesCountsSelectedFormsAsOneFamilyTicket() {
        Species normal = species(1, "Normal", 1);
        List<Species> vivillonForms = selectedFamilyForms(SpeciesIDs.vivillon, "Vivillon", 20, 3000);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(vivillonForms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSimilarStrengthSpecies(0, random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    @Test
    public void getRandomSpeciesWithoutUnownUsesRegularSpeciesTickets() {
        Species a = species(1, "A", 1);
        Species b = species(2, "B", 2);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(a, b));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(2, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    @Test
    public void getRandomSpeciesWithOneUnownFormUsesRegularSpeciesTickets() {
        Species normal = species(1, "Normal", 1);
        Species unown = unownForm(0);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(normal, unown));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(2, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    @Test
    public void getRandomSpeciesWithOneNamedUnownPunctuationFormUsesRegularSpeciesTickets() {
        Species normal = species(1, "Normal", 1);
        Species unownQuestion = species(1002, "Unown ?", 1002);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(normal, unownQuestion));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(2, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    @Test
    public void getRandomSpeciesWithOneSelectedFamilyFormUsesRegularSpeciesTickets() {
        Species normal = species(1, "Normal", 1);
        Species vivillon = selectedFamilyForm(SpeciesIDs.vivillon, "Vivillon", 0, 3000);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(normal, vivillon));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(2, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    @Test
    public void getRandomSpeciesKeepsRegionalFormsAsSingleTickets() {
        Species normal = species(1, "Normal", 1);
        Species meowth = species(52, "Meowth", 52);
        Species alolanMeowth = species(52, "Meowth-Alola", 1100);
        alolanMeowth.setBaseForme(meowth);
        alolanMeowth.addSpecialFormCategory(SpecialFormCategory.REGIONAL);
        Species galarianMeowth = species(52, "Meowth-Galar", 1101);
        galarianMeowth.setBaseForme(meowth);
        galarianMeowth.addSpecialFormCategory(SpecialFormCategory.REGIONAL);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(normal, meowth, alolanMeowth, galarianMeowth));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(4, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    @Test
    public void getRandomSpeciesKeepsExcludedLegendaryFusionFamiliesAsSingleTickets() {
        assertFamilyRemainsFlat(SpeciesIDs.giratina, "Giratina");
        assertFamilyRemainsFlat(SpeciesIDs.kyurem, "Kyurem");
        assertFamilyRemainsFlat(SpeciesIDs.necrozma, "Necrozma");
        assertFamilyRemainsFlat(SpeciesIDs.calyrex, "Calyrex");
    }

    @Test
    public void getRandomSpeciesKeepsAlcremieGigantamaxAsSingleTicketOutsideFamily() {
        Species normal = species(1, "Normal", 1);
        List<Species> alcremieForms = selectedFamilyForms(SpeciesIDs.alcremie, "Alcremie", 2, 3000);
        Species alcremieGmax = selectedFamilyForm(SpeciesIDs.alcremie, "Alcremie-Giga", 9, 3009);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(alcremieForms);
        pokes.add(alcremieGmax);
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(3, random.boundAt(0));
    }

    @Test
    public void unmodifiableSetCopiesElementsWhenInitiated() {
        Species a = new Species(0);
        a.setName("A");
        Species b = new Species(1);
        b.setName("B");
        SpeciesSet specs = SpeciesSet.unmodifiable(new HashSet<>(Arrays.asList(a, b)));
        System.out.println(specs);
        assertEquals(specs, new HashSet<>(Arrays.asList(a, b)));
    }

    @Test
    public void unmodifiableSetThrowsWhenAdding() {
        Species a = new Species(0);
        a.setName("A");
        Species b = new Species(1);
        b.setName("B");
        SpeciesSet specs = SpeciesSet.unmodifiable(Collections.singleton(a));
        assertThrows(UnmodifiableSetException.class, () -> {specs.add(b);});
    }

    @Test
    public void unmodifiableSetThrowsWhenRemoving() {
        Species a = new Species(0);
        a.setName("A");
        SpeciesSet specs = SpeciesSet.unmodifiable(Collections.singleton(a));
        assertThrows(UnmodifiableSetException.class, () -> {specs.remove(a);});
    }

    @Test
    public void unmodifiableSetThrowsWhenRemovingThroughIterator() {
        Species a = new Species(0);
        a.setName("A");
        SpeciesSet specs = SpeciesSet.unmodifiable(Collections.singleton(a));
        assertThrows(UnmodifiableSetException.class, () -> {
            Iterator<Species> it = specs.iterator();
            it.next();
            it.remove();
            System.out.println(specs); // in case nothing is thrown, shows whether the element was removed or not
        });
    }

    @Test
    public void unmodifiableSetThrowsWhenClearing() {
        Species a = new Species(0);
        a.setName("A");
        SpeciesSet specs = SpeciesSet.unmodifiable(Collections.singleton(a));
        assertThrows(UnmodifiableSetException.class, specs::clear);
    }

    @Test
    public void sortByTypesWorks() {
        SpeciesSet specs = new SpeciesSet();
        Random random = new Random();
        List<Type> types = Type.getAllTypes(7);
        for(int i = 0; i < 1000; i++){
            Species species = new Species(i);
            species.setName("Random" + i);
            species.setPrimaryType(types.get(random.nextInt(types.size())));
            if(random.nextBoolean()) {
                species.setSecondaryType(types.get(random.nextInt(types.size())));
            }
            specs.add(species);
        }

        Map<Type, SpeciesSet> specsByTypes = specs.sortByType(false);
        for(Type type : types) {
            SpeciesSet speciesOfType = specsByTypes.get(type);
            if(speciesOfType != null) {
                for (Species species : speciesOfType) {
                    assertTrue(species.hasType(type, false));
                }
            }
        }
    }

    @Test
    public void sortByTypesWorksWithChangedTypes() {
        SpeciesSet specs = new SpeciesSet();
        Random random = new Random();
        List<Type> types = Type.getAllTypes(7);
        for(int i = 0; i < 1000; i++){
            Species species = new Species(i);
            species.setName("Random" + i);
            species.setPrimaryType(types.get(random.nextInt(types.size())));
            if(random.nextBoolean()) {
                species.setSecondaryType(types.get(random.nextInt(types.size())));
            } else {
                species.setSecondaryType(null);
            }

            species.setPrimaryType(types.get(random.nextInt(types.size())));
            if(random.nextBoolean()) {
                species.setSecondaryType(types.get(random.nextInt(types.size())));
            } else {
                species.setSecondaryType(null);
            }
            specs.add(species);
        }

        Map<Type, SpeciesSet> specsByTypes = specs.sortByType(false);
        for(Type type : types) {
            SpeciesSet speciesOfType = specsByTypes.get(type);
            for (Species species : speciesOfType) {
                assertTrue(species.hasType(type, false));
            }
        }

        specsByTypes = specs.sortByType(true);
        for(Type type : types) {
            SpeciesSet speciesOfType = specsByTypes.get(type);
            for (Species species : speciesOfType) {
                assertTrue(species.hasType(type, true));
            }
        }
    }

    private static Species species(int number, String name, int speciesSetIdentityNumber) {
        Species species = new Species(number);
        species.setName(name);
        species.setSpeciesSetIdentityNumber(speciesSetIdentityNumber);
        return species;
    }

    private static void assertSelectedFamilyFormsCountAsOneTicket(int baseNumber, String familyName) {
        Species normal = species(1, "Normal", 1);
        List<Species> forms = selectedFamilyForms(baseNumber, familyName, 3, 3000 + baseNumber);
        SpeciesSet pokes = new SpeciesSet();
        pokes.add(normal);
        pokes.addAll(forms);
        SequenceRandom random = new SequenceRandom(0);

        Species pick = pokes.getRandomSpecies(random);

        assertEquals(normal, pick);
        assertEquals(2, random.boundAt(0));
    }

    private static void assertFamilyRemainsFlat(int baseNumber, String familyName) {
        Species normal = species(1, "Normal", 1);
        Species base = species(baseNumber, familyName, 4000 + baseNumber);
        Species form = species(baseNumber, familyName + "-Form", 5000 + baseNumber);
        form.setBaseForme(base);
        SpeciesSet pokes = new SpeciesSet(Arrays.asList(normal, base, form));
        SequenceRandom random = new SequenceRandom(0);

        pokes.getRandomSpecies(random);

        assertEquals(3, random.boundAt(0));
        assertEquals(1, random.callCount());
    }

    private static List<Species> selectedFamilyForms(int baseNumber, String familyName, int count, int identityStart) {
        List<Species> forms = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            forms.add(selectedFamilyForm(baseNumber, familyName, i, identityStart + i));
        }
        return forms;
    }

    private static Species selectedFamilyForm(int baseNumber, String familyName, int index, int speciesSetIdentityNumber) {
        Species form = species(baseNumber, familyName + "-" + index, speciesSetIdentityNumber);
        form.setFormeNumber(index);
        return form;
    }

    private static List<Species> unownForms(int count) {
        List<Species> unownForms = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            unownForms.add(unownForm(i));
        }
        return unownForms;
    }

    private static Species unownForm(int index) {
        Species unown = species(SpeciesIDs.unown, "Unown-" + index, 10000 + index);
        unown.setFormeNumber(index);
        return unown;
    }

    private static List<Species> unownNamedForms() {
        return Arrays.asList(
                species(SpeciesIDs.unown, "Unown", 2000),
                species(2001, "Unown B", 2001),
                species(2002, "Unown C", 2002),
                species(2003, "Unown !", 2003),
                species(2004, "Unown ?", 2004));
    }

    private static class SequenceRandom extends Random {
        private final Queue<Integer> choices = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();

        SequenceRandom(int... choices) {
            for(int choice : choices) {
                this.choices.add(choice);
            }
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            if(choices.isEmpty()) {
                return 0;
            }
            int choice = choices.remove();
            if(choice < 0 || choice >= bound) {
                throw new IllegalArgumentException("Choice " + choice + " outside bound " + bound);
            }
            return choice;
        }

        int boundAt(int index) {
            return bounds.get(index);
        }

        int callCount() {
            return bounds.size();
        }
    }
}
