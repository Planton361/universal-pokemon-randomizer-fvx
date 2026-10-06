package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import com.uprfvx.romio.services.*;


import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public source constants and synthetic memory only; never loads or writes a ROM file. */
public class CfruDpeEvolutionFixture extends Gen3RomHandler {
    public static final int EVOLUTION_BASE = 0x100;
    public static final int ROW_SIZE = 128;
    public final byte[] memory = new byte[0x50000];
    public final Species[] species = new Species[1441];
    public final Map<Integer, List<MoveLearnt>> learnsets = new HashMap<>();
    public final List<Species> pool = new ArrayList<>();
    private final RestrictedSpeciesService restricted = new RestrictedSpeciesService(this);

    public record SourceSlot(int source, int slot, int method, int parameter, int target, int auxiliary,
                             String sourceName, String methodName, String targetName, String disposition) {}

    public static List<SourceSlot> inventory() {
        InputStream stream = CfruDpeEvolutionFixture.class.getResourceAsStream("/cfru-dpe/evolution-slot-inventory.tsv");
        if (stream == null) throw new IllegalStateException("Missing exact-source inventory");
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                .filter(line -> !line.startsWith("#")).map(line -> {
                    String[] c = line.split("\t");
                    return new SourceSlot(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]),
                            Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5]), c[6], c[7], c[8], c[9]);
                }).toList();
    }

    public CfruDpeEvolutionFixture() throws Exception {
        var entry = new Gen3RomEntry(Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").getFirst());
        entry.setRomCode("BPRE");
        entry.putIntValue("PokemonCount", 1440);
        entry.putIntValue("PokemonEvolutions", EVOLUTION_BASE);
        entry.putIntValue("PokemonFrontImages", 0x30000);
        entry.putIntValue("PokemonNormalPalettes", 0x33000);
        setField("rom", memory); setField("romEntry", entry);
        setField("isRomHack", true); setField("useCfruDpeGen9SpeciesCount", true);
        List<Species> loaded = new ArrayList<>(); loaded.add(null);
        for (int id = 1; id < 1440; id++) {
            Species sp = new Species(id);
            sp.setSpeciesSetIdentityNumber(id); sp.setName("Species" + id);
            sp.setGeneration(id >= 0x50E ? 9 : 1); sp.setPrimaryType(Type.NORMAL);
            sp.setGrowthCurve(ExpCurve.MEDIUM_FAST);
            sp.setHp(50); sp.setAttack(50); sp.setDefense(50); sp.setSpeed(50); sp.setSpatk(50); sp.setSpdef(50);
            species[id] = sp; pool.add(sp); loaded.add(sp);
            learnsets.put(id, List.of(new MoveLearnt(33, 1)));
            putInt( 0x30000 + id * 8, 0x08040000);
            putInt( 0x33000 + id * 8, 0x08041000);
        }
        setField("speciesList", loaded); setField("numRealPokemon", 1439);
        setField("pokes", species); setField("pokesInternal", species);
        restrictions();
    }

    private void putInt(int offset, int value) { for (int i = 0; i < 4; i++) memory[offset + i] = (byte) (value >>> (8 * i)); }
    public boolean impossible(Evolution edge) { return Gen3RomHandler.updateImpossibleEvolution(edge, true, false); }
    public void restrictions() { restricted.setRestrictions(null, SpecialFormExclusionOptions.allowAllSpecialForms()); }
    public void populateExactSource() { inventory().forEach(s -> entry(s.source(), s.slot(), s.method(), s.parameter(), s.target(), s.auxiliary())); loadEvolutions(); }
    public void entry(int source, int slot, int method, int parameter, int target, int auxiliary) {
        int o = offset(source, slot); int[] words = {method, parameter, target, auxiliary};
        for (int i = 0; i < 4; i++) writeWord(o + 2 * i, words[i]);
    }
    public int word(int source, int slot, int field) { return readWord(offset(source, slot) + field); }
    public static int offset(int source, int slot) { return EVOLUTION_BASE + source * ROW_SIZE + slot * 8; }
    public void write() throws Exception {
        Method writer = Gen3RomHandler.class.getDeclaredMethod("writeEvolutions"); writer.setAccessible(true);
        invoke(writer);
    }
    public void preflightSave() throws Exception {
        Method prepare = AbstractRomHandler.class.getDeclaredMethod("prepareSaveRom"); prepare.setAccessible(true);
        invoke(prepare);
    }
    private void invoke(Method method) throws Exception {
        try { method.invoke(this); } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw error;
        }
    }
    public void setField(String name, Object value) throws Exception {
        for (Class<?> type = Gen3RomHandler.class; type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(this, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() { return learnsets; }
    @Override public SpeciesSet getSpeciesSetInclFormes() { return new SpeciesSet(pool); }
    @Override public SpeciesSet getSpeciesSet() { return new SpeciesSet(pool); }
    @Override public List<Species> getSpeciesInclFormes() { List<Species> all = new ArrayList<>(); all.add(null); all.addAll(pool); return all; }
    @Override public List<Species> getSpecies() { return getSpeciesInclFormes(); }
    @Override public SpeciesSet getAltFormes() { return new SpeciesSet(); }
    @Override public SpeciesSet getIrregularFormes() { return new SpeciesSet(); }
    @Override public List<MegaEvolution> getMegaEvolutions() { return List.of(); }
    @Override public RestrictedSpeciesService getRestrictedSpeciesService() { return restricted; }
    @Override public TypeService getTypeService() { return new TypeService(this); }
}
