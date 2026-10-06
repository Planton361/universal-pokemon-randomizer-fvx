package com.uprfvx.romio.services;

import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen exact-source inventory, independent of ROM decoding and display names/dex numbers. */
class CfruDpeSpeciesPoolInventoryTest {
    static Stream<String> inventory() {
        var input = CfruDpeSpeciesPoolInventoryTest.class.getResourceAsStream(
                "/cfru-dpe/species-pool-inventory.tsv");
        assertNotNull(input);
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                .lines().filter(line -> !line.startsWith("#"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inventory")
    void everyExactSourceSlotHasTheReviewedDisposition(String row) {
        String[] columns = row.split("\t");
        Species sp = new Species(25); // Identical display/dex identity must not hide the internal row.
        sp.setSpeciesSetIdentityNumber(Integer.parseInt(columns[0], 16));
        sp.setName("Uninformative");
        var category = SpecialFormPredicates.cfruDpePoolCategory(sp);
        assertEquals(columns[2], category.name());
        assertEquals(columns[3].equals("YES"), category.eligible());
        assertFalse(category.reason().isBlank());
        if (category.eligible()) {
            for (int column : new int[] {5, 6, 7, 8}) assertNotEquals("MISSING", columns[column]);
        }
    }

    @Test
    void inventoryIsCompleteUniqueAndBoundedAndNullIsRejected() {
        List<String> rows = inventory().toList();
        assertEquals(1440, rows.size());
        assertEquals(1440, rows.stream().map(row -> row.split("\t")[0]).distinct().count());
        assertFalse(SpecialFormPredicates.cfruDpePoolCategory(null).eligible());
        Species outside = new Species(0x5A0);
        assertFalse(SpecialFormPredicates.cfruDpePoolCategory(outside).eligible());
    }

    @Test
    void oneOrdinarySpeciesPerGenerationAndEveryRegionalFamilyRemainEligible() {
        for (int id : new int[] {1, 152, 0x115, 0x1B8, 0x223, 0x2F6, 0x3AB, 0x44E, 0x50E}) {
            assertEquals(SpecialFormPredicates.CfruDpePoolCategory.ORDINARY,
                    SpecialFormPredicates.cfruDpePoolCategory(new Species(id)));
        }
        for (int id : new int[] {0x3FC, 0x4BC, 0x4D2, 0x581}) {
            assertEquals(SpecialFormPredicates.CfruDpePoolCategory.REGIONAL,
                    SpecialFormPredicates.cfruDpePoolCategory(new Species(id)));
        }
    }
}
