package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic source-policy tests: no ROM fixture or file IO. */
class HospitalityPolicyTest {
    static Species mon(int id, int ability) {
        Species s = new Species(1013);
        s.setSpeciesSetIdentityNumber(id);
        s.setName("Synthetic");
        s.setPrimaryType(Type.GRASS);
        s.setGrowthCurve(ExpCurve.MEDIUM_FAST);
        s.setHp(70); s.setAttack(60); s.setDefense(80); s.setSpeed(50); s.setSpatk(100); s.setSpdef(80);
        s.setAbility1(ability); s.setAbility2(0); s.setAbility3(0x61);
        return s;
    }
    static class Handler extends Gen3RomHandler {
        final SpeciesSet species = new SpeciesSet();
        Handler(boolean mode, Species... mons) throws Exception {
            var f = Gen3RomHandler.class.getDeclaredField("useCfruDpeGen9SpeciesCount");
            f.setAccessible(true); f.set(this, mode);
            species.addAll(Arrays.asList(mons));
        }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return species; }
        @Override public List<MegaEvolution> getMegaEvolutions() { return List.of(); }
        @Override public int generationOfPokemon() { return 3; }
        @Override public Map<Integer, List<Integer>> getAbilityVariations() { return Map.of(); }
        byte[] write(Species s) throws Exception {
            rom = new byte[64]; Arrays.fill(rom, (byte)0x55);
            rom[Gen3Constants.bsAbility1Offset] = (byte)s.getAbility1();
            rom[Gen3Constants.bsAbility2Offset] = (byte)s.getAbility2();
            rom[Gen3Constants.bsHiddenAbilityOffset] = (byte)s.getAbility3();
            Method method = Gen3RomHandler.class.getDeclaredMethod("saveBasicPokeStats", Species.class, int.class);
            method.setAccessible(true); method.invoke(this, s, 0);
            return rom;
        }
    }
    static void preserved(Species s) {
        assertEquals(0x6C, s.getAbility1()); assertEquals(0, s.getAbility2()); assertEquals(0x61, s.getAbility3());
    }
    @Test void preservesBothFormsAndRandomizesOrdinarySpecies() throws Exception {
        Species a = mon(0x589, 0x6C), b = mon(0x58A, 0x6C), ordinary = mon(113, 0x6C);
        Handler handler = new Handler(true, a, b, ordinary);
        Settings settings = new Settings(); settings.setEnsureTwoAbilities(true);
        new SpeciesAbilityRandomizer(handler, settings, new Random(11)).randomizeAbilities();
        preserved(a); preserved(b);
        assertFalse(handler.preservesHospitalityAbilities(ordinary));
        assertNotEquals(0, ordinary.getAbility2());
        assertEquals("Hospitality", handler.abilityNameForSpecies(0x6C, a));
        assertNotEquals("Hospitality", handler.abilityNameForSpecies(0x6C, ordinary));
        assertNotEquals("Hospitality", handler.abilityNameForSpecies(0x61, a));
    }
    @Test void excludesOldInputsAndOtherFormatsFromPreservation() throws Exception {
        Species old = mon(0x589, 0xC1), current = mon(0x58A, 0x6C);
        assertFalse(new Handler(true).preservesHospitalityAbilities(old));
        assertFalse(new Handler(false).preservesHospitalityAbilities(current));
        assertFalse(new Handler(true).preservesHospitalityAbilities(null));
        assertFalse(new Handler(true).preservesHospitalityAbilities(mon(1013, 0x6C)));
    }
    @Test void doesNotOverwriteHospitalityByEvolutionOrCosmeticPropagation() throws Exception {
        Species parent = mon(0x587, 1), a = mon(0x589, 0x6C), b = mon(0x58A, 0x6C);
        Evolution ev = new Evolution(parent, a, EvolutionType.LEVEL, 30);
        parent.getEvolutionsFrom().add(ev); a.getEvolutionsTo().add(ev);
        b.setBaseForme(parent); b.setActuallyCosmetic(true);
        Handler handler = new Handler(true, parent, a, b);
        Settings settings = new Settings(); settings.setAbilitiesFollowEvolutions(true);
        new SpeciesAbilityRandomizer(handler, settings, new Random(12)).randomizeAbilities();
        preserved(a); preserved(b);
    }
    @Test void writerPreservesNoneAndHeatproofWithoutAffectingOrdinaryWriter() throws Exception {
        for (int id : new int[]{0x589, 0x58A}) {
            Species s = mon(id, 0x6C); byte[] out = new Handler(true).write(s);
            assertEquals(0x6C, out[Gen3Constants.bsAbility1Offset] & 255);
            assertEquals(0, out[Gen3Constants.bsAbility2Offset] & 255);
            assertEquals(0x61, out[Gen3Constants.bsHiddenAbilityOffset] & 255);
        }
        byte[] out = new Handler(true).write(mon(113, 0x6C));
        assertEquals(0x6C, out[Gen3Constants.bsAbility2Offset] & 255);
    }
}
