package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.SpeciesIDs;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Gen3CfruDpeSpeciesGenerationTest {

    @Test
    public void cfruDpeGenerationFallsBackToSpeciesSetIdentityForProblemNames() throws Exception {
        Gen3RomHandler romHandler = cfruDpeRomHandler();

        Stream.of(
                problemSpecies("Stonjorner", SpeciesIDs.stonjourner, 8),
                problemSpecies("Squawkbily", SpeciesIDs.squawkabilly, 9),
                problemSpecies("Centskorch", SpeciesIDs.centiskorch, 8),
                problemSpecies("Polchgeist", SpeciesIDs.poltchageist, 9),
                problemSpecies("RoarinMoon", SpeciesIDs.roaringMoon, 9),
                problemSpecies("Enamorus", SpeciesIDs.enamorous, 8),
                problemSpecies("Flab\u00e9b\u00e9", SpeciesIDs.flabebe, 6),
                problemSpecies("Baculegion", SpeciesIDs.basculegion, 8),
                problemSpecies("BruteBonet", SpeciesIDs.bruteBonnet, 9)
        ).forEach(species -> assertEquals(species.expectedGeneration,
                generationOf(romHandler, species.species),
                species.species.getName()));
    }

    @Test
    public void vanillaGenerationStillUsesSpeciesNumber() throws Exception {
        Gen3RomHandler romHandler = baseRomHandler();
        Species species = species(25, SpeciesIDs.stonjourner, "Stonjorner");

        assertEquals(1, generationOf(romHandler, species));
    }

    @Test
    public void cfruDpeTableProfileCanRecoverWhenNameScanStopsBeforeGen9() throws Exception {
        Gen3RomHandler romHandler = cfruDpeRomHandler();
        Gen3RomEntry romEntry = fieldValue(romHandler, "romEntry", Gen3RomEntry.class);
        byte[] rom = new byte[0x200000];
        int statsOffset = 0x160000;
        romEntry.putIntValue("PokemonStats", statsOffset);
        writePointer(rom, 0x3EA7C, 0x170000);
        writePointer(rom, 0x43C68, 0x172000);
        writePointer(rom, 0x125A8C, 0x178000);
        writePointer(rom, Gen3Constants.moveNamesPointer, 0x179000);
        writePointer(rom, Gen3Constants.moveDataPointer, 0x17D000);
        writePlausibleStats(rom, statsOffset, 824);
        writePlausibleStats(rom, statsOffset, 1000);
        writePlausibleStats(rom, statsOffset, 1294);
        writePlausibleStats(rom, statsOffset, 1439);
        setField(romHandler, "rom", rom);

        assertTrue(hasCfruDpeGen9TableProfile(romHandler));
    }

    private static ProblemSpecies problemSpecies(String name, int identityNumber, int expectedGeneration) {
        return new ProblemSpecies(species(25, identityNumber, name), expectedGeneration);
    }

    private static Species species(int number, int speciesSetIdentityNumber, String name) {
        Species species = new Species(number);
        species.setName(name);
        species.setSpeciesSetIdentityNumber(speciesSetIdentityNumber);
        return species;
    }

    private static int generationOf(Gen3RomHandler romHandler, Species species) {
        try {
            Method method = Gen3RomHandler.class.getDeclaredMethod("generationOf", Species.class);
            method.setAccessible(true);
            return (int) method.invoke(romHandler, species);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasCfruDpeGen9TableProfile(Gen3RomHandler romHandler) {
        try {
            Method method = Gen3RomHandler.class.getDeclaredMethod("hasCfruDpeGen9TableProfile");
            method.setAccessible(true);
            return (boolean) method.invoke(romHandler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writePlausibleStats(byte[] rom, int statsOffset, int speciesId) {
        int offset = statsOffset + speciesId * Gen3Constants.baseStatsEntrySize;
        rom[offset + Gen3Constants.bsHPOffset] = 80;
        rom[offset + Gen3Constants.bsAttackOffset] = 80;
        rom[offset + Gen3Constants.bsDefenseOffset] = 80;
        rom[offset + Gen3Constants.bsSpeedOffset] = 80;
        rom[offset + Gen3Constants.bsSpAtkOffset] = 80;
        rom[offset + Gen3Constants.bsSpDefOffset] = 80;
        rom[offset + Gen3Constants.bsPrimaryTypeOffset] = 0;
        rom[offset + Gen3Constants.bsSecondaryTypeOffset] = 0;
    }

    private static void writePointer(byte[] rom, int offset, int value) {
        int pointer = value + 0x8000000;
        rom[offset] = (byte) (pointer & 0xFF);
        rom[offset + 1] = (byte) ((pointer >>> 8) & 0xFF);
        rom[offset + 2] = (byte) ((pointer >>> 16) & 0xFF);
        rom[offset + 3] = (byte) ((pointer >>> 24) & 0xFF);
    }

    private static Gen3RomHandler cfruDpeRomHandler() throws Exception {
        Gen3RomHandler romHandler = baseRomHandler();
        Gen3RomEntry romEntry = fieldValue(romHandler, "romEntry", Gen3RomEntry.class);
        romEntry.setRomCode("BPRE");
        romEntry.putIntValue("PokemonCount", Gen3Constants.unhackedMaxPokedex + 1);
        setField(romHandler, "isRomHack", true);
        return romHandler;
    }

    private static Gen3RomHandler baseRomHandler() throws Exception {
        Gen3RomHandler romHandler = new Gen3RomHandler();
        setField(romHandler, "romEntry", fireRedRomEntry());
        return romHandler;
    }

    private static Gen3RomEntry fireRedRomEntry() throws Exception {
        for (Gen3RomEntry entry : Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini")) {
            if ("Fire Red (U) 1.0".equals(entry.getName())) {
                return new Gen3RomEntry(entry);
            }
        }
        throw new IllegalStateException("Fire Red (U) 1.0 ROM entry not found");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T fieldValue(Object target, String name, Class<T> fieldType) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return fieldType.cast(field.get(target));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private record ProblemSpecies(Species species, int expectedGeneration) {
    }
}
