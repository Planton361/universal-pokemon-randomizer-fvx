package com.uprfvx.romio.services;

import com.uprfvx.romio.constants.SpeciesIDs;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import com.uprfvx.romio.romhandlers.Gen1RomHandler;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SpeciesClassificationPolicyTest {
    private static final SpeciesClassificationPolicy POLICY = SpeciesClassificationPolicy.cfruDpe();

    private static List<String[]> inventory(String resource) {
        var input = SpeciesClassificationPolicyTest.class.getResourceAsStream("/cfru-dpe/" + resource);
        assertNotNull(input);
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                .filter(line -> !line.startsWith("#")).map(line -> line.split("\t", -1)).toList();
    }

    @Test
    void completeInventoryBindsAll94OwnersAnd187RowsToTheExactDpeIdentitySpace() throws Exception {
        var legends = inventory("legendary-species-inventory.tsv");
        Map<Integer, String[]> all = new HashMap<>();
        for (var row : inventory("species-pool-inventory.tsv")) all.put(Integer.parseInt(row[0], 16), row);
        Set<Integer> ids = new HashSet<>(), owners = new HashSet<>(), legacyOwners = new HashSet<>();
        for (var row : legends) {
            int id = Integer.parseInt(row[0], 16), base = Integer.parseInt(row[2], 16);
            assertTrue(ids.add(id)); owners.add(base);
            if (row[4].equals("UPR_LEGACY")) legacyOwners.add(base);
            if (id == base && row[4].equals("UPR_LEGACY")) {
                String name = row[3].substring("SPECIES_".length()).replace("_", "");
                var generic = Arrays.stream(SpeciesIDs.class.getFields())
                        .filter(field -> field.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
                assertTrue(new Species(generic.getInt(null)).isLegendary(), row[3]);
            }
            assertEquals(row[1], all.get(id)[1]);
            assertEquals(row[3], all.get(base)[1]);
            assertTrue(row[1].equals(row[3]) || row[1].startsWith(row[3] + "_")
                    || row[3].equals("SPECIES_URSHIFU_SINGLE") && row[1].startsWith("SPECIES_URSHIFU_RAPID"));
            // Exact DPE dex-table ownership agrees wherever that source provides a row.
            if (!Set.of("MISSING", "NONE").contains(all.get(id)[4])) assertEquals(all.get(base)[4], all.get(id)[4]);
            assertTrue(POLICY.isLegendary(new Species(id)), row[1]);
        }
        assertEquals(187, ids.size()); assertEquals(94, owners.size()); assertEquals(68, legacyOwners.size());
        for (int id = 0; id <= 0x5A0; id++) {
            // Exhaustive negatives catch numeric collisions and category expansion too.
            assertEquals(ids.contains(id), POLICY.isLegendary(new Species(id)), "internal row " + id);
        }
    }

    @Test
    void oldNumericClassifierLeaksTheRealCollisionWitnessesButPolicyBlocksThem() {
        for (int id : new int[] {0x222, 0x215, 0x2BD, 0x19A}) {
            Species sp = new Species(id);
            assertFalse(sp.isLegendary(), "pre-fix witness " + id);
            assertTrue(POLICY.isLegendary(sp));
        }
        assertTrue(POLICY.isLegendary(new Species(0x49C))); // Zacian, Gen8
        assertTrue(POLICY.isLegendary(new Species(0x59F))); // Pecharunt, Gen9 Mythical
        assertTrue(POLICY.isLegendary(new Species(0x57D))); // Koraidon, Gen9 Legendary
    }

    @Test
    void ordinaryControlsAndSeparateCategoriesStayAllowedEvenWithCollidingDexNumbers() {
        for (int id : new int[] {1, 0x115, 493, 0x44E, 0x50E, 0x3F2, 0x432, 0x565, 0x57F, 0x580, 0x598, 0x59B}) {
            assertFalse(POLICY.isLegendary(new Species(id)), "ordinary/UB/Paradox " + id);
        }
        Species ordinary = new Species(SpeciesIDs.arceus);
        ordinary.setSpeciesSetIdentityNumber(0x50E);
        assertTrue(ordinary.isLegendary());
        assertFalse(POLICY.isLegendary(ordinary), "internal identity is authoritative, not generic dex number");
        Species arceus = new Species(25); arceus.setSpeciesSetIdentityNumber(0x222);
        assertTrue(POLICY.isLegendary(arceus), "display/dex identity cannot hide Arceus");
    }

    @Test
    void modeledFormsInheritBaseOwnershipAndIndependentDpeFormsClassifyBeforeSafety() {
        Species arceus = new Species(25); arceus.setSpeciesSetIdentityNumber(0x222);
        Species modeled = new Species(25); modeled.setSpeciesSetIdentityNumber(0x5A0);
        modeled.setBaseForme(arceus); modeled.setFormeNumber(1);
        assertTrue(POLICY.isLegendary(modeled));
        for (int id : new int[] {0x410, 0x397, 0x4C5, 0x58F, 0x592, 0x59D}) {
            assertTrue(POLICY.isLegendary(new Species(id)), "independent DPE form " + id);
        }
        assertTrue(SpecialFormPredicates.cfruDpePoolCategory(new Species(0x58F)).eligible());
        assertFalse(SpecialFormPredicates.cfruDpePoolCategory(new Species(0x592)).eligible());
        assertFalse(SpecialFormPredicates.cfruDpePoolCategory(new Species(0x59D)).eligible());
    }

    @Test
    void handlerDetectionAloneSelectsPolicyAndAllLegacyNumericBehaviorIsUnchanged() throws Exception {
        var handler = new CfruDpeEvolutionFixture();
        assertSame(POLICY, handler.getSpeciesClassificationPolicy());
        handler.setField("useCfruDpeGen9SpeciesCount", false);
        assertSame(SpeciesClassificationPolicy.legacy(), handler.getSpeciesClassificationPolicy());
        assertSame(SpeciesClassificationPolicy.legacy(), new Gen1RomHandler().getSpeciesClassificationPolicy());
        for (int id = 1; id <= 0x59F; id++) {
            Species sp = new Species(id);
            assertEquals(sp.isLegendary(), handler.getSpeciesClassificationPolicy().isLegendary(sp));
            Species form = new Species(2000 + id); form.setBaseForme(sp); form.setFormeNumber(1);
            assertEquals(form.isLegendary(), handler.getSpeciesClassificationPolicy().isLegendary(form));
        }
    }
}
