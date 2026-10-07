package com.uprfvx.random.randomizers;

import com.uprfvx.random.GameRandomizer;
import com.uprfvx.random.Settings;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.cli.SettingsProfileGenerator;
import com.uprfvx.random.random.RandomSource;
import com.uprfvx.romio.MiscTweak;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.graphics.palettes.*;
import com.uprfvx.romio.romhandlers.CfruDpeEvolutionFixture;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;

/**
 * #685: safe overlays and actual GameRandomizer orchestration over public synthetic models.
 * Uses the production evolution writer with synthetic memory; other outputs are model snapshots,
 * not a substitute for a user-owned full-ROM save/reopen/replay acceptance run.
 * Additions bitmask: GRAPHICS_PALETTES_SAFE=1, MISC_SAFE=2, SPECIAL_WILD=4.
 */
final class CombinedReplayFixture extends CfruDpeEvolutionFixture {
    final List<Move> moves = new ArrayList<>();
    final List<Item> items = new ArrayList<>();
    final List<Species> starters = new ArrayList<>();
    final List<Item> starterItems = new ArrayList<>();
    final List<EncounterArea> encounters = new ArrayList<>();
    final List<EncounterArea> specialEncounters = new ArrayList<>();
    final List<Trainer> trainers = new ArrayList<>();
    final List<StaticEncounter> statics = new ArrayList<>();
    final List<InGameTrade> trades = new ArrayList<>();
    List<Item> fieldItems = new ArrayList<>();
    List<Shop> shops = new ArrayList<>();
    List<Integer> tms = new ArrayList<>(List.of(1, 2, 3, 4));
    List<Integer> tutors = new ArrayList<>(List.of(5, 6, 7));
    Map<Species, boolean[]> tmCompatibility = new LinkedHashMap<>();
    Map<Species, boolean[]> tutorCompatibility = new LinkedHashMap<>();
    final List<Integer> miscWrites = new ArrayList<>();
    List<String> trainerNames = new ArrayList<>(List.of("Alpha"));
    List<String> classNames = new ArrayList<>(List.of("ClassA", "ClassB"));
    List<Integer> prices = new ArrayList<>(Collections.nCopies(513, 100));
    Item pcPotion;
    Species tutorialOpponent, tutorialPlayer;

    CombinedReplayFixture() throws Exception {
        // Enough candidates for all constraints; descriptions cover these IDs.
        pool.removeIf(sp -> sp.getNumber() > 151);
        for (Species sp : pool) {
            sp.setAbility1(1); sp.setGenderRatio(127);
            sp.setNormalPalette(palette()); sp.setShinyPalette(palette());
            learnsets.put(sp.getNumber(), new ArrayList<>(List.of(new MoveLearnt(33, 1), new MoveLearnt(34, 10))));
            tmCompatibility.put(sp, new boolean[6]); tutorCompatibility.put(sp, new boolean[4]);
        }
        for (int id = 1; id <= 50; id++) {
            if (id % 3 == 1) entry(id, 0, 4, 16, id + 1, 0);
        }
        restrictions(); loadEvolutions();
        moves.add(null);
        for (int id = 1; id <= 100; id++) {
            Move move = new Move(); move.number = move.internalId = id; move.name = "Move" + id;
            move.power = 50; move.pp = 20; move.hitratio = 100; move.type = Type.NORMAL;
            move.category = MoveCategory.PHYSICAL; moves.add(move);
        }
        items.add(null);
        for (int id = 1; id <= 512; id++) {
            Item item = new Item(id, "Item" + id); item.setAllowed(true);
            items.add(item);
        }
        starters.addAll(List.of(species[1], species[4], species[7]));
        starterItems.addAll(List.of(items.get(1), items.get(2), items.get(3)));
        for (int i = 0; i < 3; i++) {
            EncounterArea area = new EncounterArea(); area.setDisplayName("Area" + i); area.setRate(20);
            area.setMapIndex(i); area.setEncounterType(EncounterType.WALKING);
            Encounter enc = new Encounter(); enc.setSpecies(species[i+1]); enc.setLevel(50);
            area.add(enc); encounters.add(area);
        }
        EncounterArea special = new EncounterArea(); special.setDisplayName("Special");
        special.setRate(10); special.setMapIndex(3); special.setEncounterType(EncounterType.SPECIAL);
        Encounter specialMon = new Encounter(); specialMon.setSpecies(species[10]); specialMon.setLevel(50);
        special.add(specialMon); specialEncounters.add(special);
        Trainer trainer = new Trainer(); trainer.setIndex(1);
        TrainerPokemon tp = new TrainerPokemon(); tp.setSpecies(species[1]); tp.setLevel(10);
        trainer.getPokemon().add(tp); trainers.add(trainer);
        StaticEncounter encounter = new StaticEncounter(); encounter.setSpecies(species[7]); encounter.setLevel(10);
        statics.add(encounter);
        InGameTrade trade = new InGameTrade(); trade.setGivenSpecies(species[1]); trade.setRequestedSpecies(species[4]);
        trade.setNickname("Species1"); trades.add(trade);
        fieldItems.addAll(items.subList(1, 9));
        Shop shop = new Shop(); shop.setName("Shop"); shop.setMainGame(true); shop.setSpecialShop(true);
        shop.setItems(new ArrayList<>(items.subList(1, 6))); shops.add(shop);
    }
    static Palette palette() {
        Palette result = new Palette();
        for (int i = 0; i < result.size(); i++) result.set(i, new Color(i*8, i*8, i*8));
        return result;
    }
    @SuppressWarnings("unchecked")
    static Settings settings(boolean data, boolean world, int additions) throws Exception {
        Settings s = new Settings();
        for (Method method : Settings.class.getMethods()) {
            if (method.getName().startsWith("set") && Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean.class})) method.invoke(s, false);
        }
        Method featureMethod = SettingsProfileGenerator.class.getDeclaredMethod("buildFeatureOverlays"); featureMethod.setAccessible(true);
        Map<String, Consumer<Settings>> features = (Map<String, Consumer<Settings>>) featureMethod.invoke(null);
        Method profileMethod = SettingsProfileGenerator.class.getDeclaredMethod("buildProfileOverlays"); profileMethod.setAccessible(true);
        Map<String, List<String>> profiles = (Map<String, List<String>>) profileMethod.invoke(null);
        List<String> ids = new ArrayList<>();
        if (data) {
            for (int id : new int[]{1,6,8,13,14,15,16,22}) ids.add(String.format("FVX-TRAIT-%03d",id));
            ids.addAll(profiles.get("03_MOVES_MOVESETS_FULL"));
        }
        if (world) {
            for (String profile : List.of("02_STARTERS_STATICS_TRADES_FULL","04_FOE_BASE","05_WILD_FULL","06_TM_TUTOR_FULL")) ids.addAll(profiles.get(profile));
            for (int id : new int[]{2,4,6,7,8,9}) ids.add(String.format("FVX-ITEM-%03d",id));
        }
        if ((additions & 1) != 0) ids.addAll(List.of("FVX-GFX-001","FVX-GFX-003"));
        if ((additions & 2) != 0) for (int id : new int[]{1,3,5,6,7,8,9}) ids.add(String.format("FVX-MISC-%03d",id));
        if ((additions & 4) != 0) ids.add("FVX-SPECIAL-WILD-001");
        for (String id : ids) features.get(id).accept(s);
        // Same owner closure as Workspace #684. Gen-limit is target-normalized OFF.
        if (world) {
            s.setRandomizeWildPokemon(true); s.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.ENCOUNTER_SET);
            s.setTmsHmsCompatibilityMod(Settings.TMsHMsCompatibilityMod.COMPLETELY_RANDOM);
            s.setMoveTutorsCompatibilityMod(Settings.MoveTutorsCompatibilityMod.COMPLETELY_RANDOM);
        }
        s.setCustomNames(new CustomNamesSet());
        s.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        return s;
    }
    byte[] run(long seed, boolean data, boolean world, int additions) throws Exception {
        GameRandomizer game = new GameRandomizer(settings(data, world, additions), null, this, null, false);
        Field rng = GameRandomizer.class.getDeclaredField("randomSource"); rng.setAccessible(true);
        ((RandomSource) rng.get(game)).seed(seed);
        Method apply = GameRandomizer.class.getDeclaredMethod("applyRandomizers"); apply.setAccessible(true);
        try { apply.invoke(game); } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
        write();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(buffer);
        out.write(memory);
        for (Species sp : pool) {
            out.writeInt(sp.getHp()); out.writeInt(sp.getAttack()); out.writeInt(sp.getDefense());
            out.writeInt(sp.getSpeed()); out.writeInt(sp.getSpatk()); out.writeInt(sp.getSpdef());
            out.writeUTF(String.valueOf(sp.getPrimaryType(false))); out.writeUTF(String.valueOf(sp.getSecondaryType(false)));
            out.writeInt(sp.getAbility1()); out.writeInt(sp.getAbility2()); out.writeInt(sp.getAbility3());
            out.write(sp.getNormalPalette().toBytes());
            for (MoveLearnt move : learnsets.get(sp.getNumber())) { out.writeInt(move.move); out.writeInt(move.level); }
        }
        for (Move move : moves.subList(1,moves.size())) { out.writeInt(move.power); out.writeInt(move.pp); out.writeDouble(move.hitratio); out.writeUTF(move.type.name()); }
        for (Species sp : starters) out.writeInt(sp.getNumber());
        for (EncounterArea area : encounters) for (Encounter enc : area) { out.writeInt(enc.getSpecies().getNumber()); out.writeInt(enc.getLevel()); }
        for (EncounterArea area : specialEncounters) for (Encounter enc : area) { out.writeInt(enc.getSpecies().getNumber()); out.writeInt(enc.getLevel()); }
        for (Trainer trainer : trainers) for (TrainerPokemon tp : trainer.getPokemon()) { out.writeInt(tp.getSpecies().getNumber()); out.writeInt(tp.getLevel()); }
        for (StaticEncounter enc : statics) out.writeInt(enc.getSpecies().getNumber());
        for (InGameTrade trade : trades) { out.writeInt(trade.getGivenSpecies().getNumber()); out.writeInt(trade.getRequestedSpecies().getNumber()); }
        for (Item item : fieldItems) out.writeInt(item.getId());
        for (Shop shop : shops) for (Item item : shop.getItems()) out.writeInt(item.getId());
        for (int price : prices) out.writeInt(price);
        for (String name : trainerNames) out.writeUTF(name);
        for (String name : classNames) out.writeUTF(name);
        for (int move : tms) out.writeInt(move); for (int move : tutors) out.writeInt(move);
        for (boolean[] flags : tmCompatibility.values()) for (boolean flag : flags) out.writeBoolean(flag);
        for (boolean[] flags : tutorCompatibility.values()) for (boolean flag : flags) out.writeBoolean(flag);
        for (int tweak : miscWrites) out.writeInt(tweak);
        out.writeInt(pcPotion == null ? 0 : pcPotion.getId());
        out.writeInt(tutorialOpponent == null ? 0 : tutorialOpponent.getNumber());
        out.writeInt(tutorialPlayer == null ? 0 : tutorialPlayer.getNumber());
        return buffer.toByteArray();
    }
    @Override public TypeTable getTypeTable() { return new TypeTable(Type.getAllTypes(6)); }
    @Override public String getPaletteFilesID() { return "FRLG"; }
    @Override public List<Move> getMoves() { return moves; }
    @Override public void setMovesLearnt(Map<Integer,List<MoveLearnt>> value) { var copy = new TreeMap<>(value); learnsets.clear(); learnsets.putAll(copy); }
    @Override public Map<Integer,List<Integer>> getEggMoves() { return new TreeMap<>(); }
    @Override public void setEggMoves(Map<Integer,List<Integer>> value) { }
    @Override public List<Species> getStarters() { return starters; }
    @Override public boolean setStarters(List<Species> value) { starters.clear(); starters.addAll(value); return true; }
    @Override public List<Item> getStarterHeldItems() { return starterItems; }
    @Override public void setStarterHeldItems(List<Item> value) { starterItems.clear(); starterItems.addAll(value); }
    @Override public List<Item> getItems() { return items; }
    @Override public Set<Item> getAllowedItems() { return new LinkedHashSet<>(items.subList(1,items.size())); }
    @Override public Set<Item> getNonBadItems() { return getAllowedItems(); }
    @Override public List<Item> getFieldItems() { return fieldItems; }
    @Override public void setFieldItems(List<Item> value) { fieldItems = value; }
    @Override public Set<Item> getRequiredFieldTMs() { return Set.of(); }
    @Override public List<Integer> getShopPrices() { return prices; }
    @Override public void setShopPrices(List<Integer> value) { prices = value; }
    @Override public List<Shop> getShops() { return shops; }
    @Override public void setShops(List<Shop> value) { shops = value; }
    @Override public Set<Item> getMegaStones() { return Set.of(); }
    @Override public Set<Item> getRegularShopItems() { return Set.of(); }
    @Override public Set<Item> getOPShopItems() { return Set.of(); }
    @Override public Set<Item> getEvolutionItems() { return Set.of(items.get(1)); }
    @Override public Set<Item> getXItems() { return Set.of(items.get(2)); }
    @Override public int getTMCount() { return tms.size(); }
    @Override public List<Integer> getTMMoves() { return tms; }
    @Override public void setTMMoves(List<Integer> value) { tms = value; }
    @Override public List<Integer> getHMMoves() { return List.of(99); }
    @Override public List<Integer> getMoveTutorMoves() { return tutors; }
    @Override public void setMoveTutorMoves(List<Integer> value) { tutors = value; }
    @Override public Map<Species,boolean[]> getTMHMCompatibility() { return tmCompatibility; }
    @Override public void setTMHMCompatibility(Map<Species,boolean[]> value) { tmCompatibility = value; }
    @Override public Map<Species,boolean[]> getMoveTutorCompatibility() { return tutorCompatibility; }
    @Override public void setMoveTutorCompatibility(Map<Species,boolean[]> value) { tutorCompatibility = value; }
    @Override public List<EncounterArea> getEncounters(boolean timed) { var result = new ArrayList<>(encounters); result.addAll(specialEncounters); return result; }
    @Override public void setEncounters(boolean timed, List<EncounterArea> value) { encounters.clear(); specialEncounters.clear(); for (var area : value) { (area.getEncounterType() == EncounterType.SPECIAL ? specialEncounters : encounters).add(area); } }
    @Override public int internalStringLength(String value) { return value.length(); }
    @Override public boolean setStaticPokemon(List<StaticEncounter> value) { if (value != statics) { statics.clear(); statics.addAll(value); } return true; }
    @Override public List<String> getTrainerNames() { return trainerNames; }
    @Override public void setTrainerNames(List<String> value) { trainerNames = value; }
    @Override public List<String> getTrainerClassNames() { return classNames; }
    @Override public void setTrainerClassNames(List<String> value) { classNames = value; }
    @Override public List<Integer> getDoublesTrainerClasses() { return List.of(); }
    @Override public List<Trainer> getTrainers() { return trainers; }
    @Override public List<StaticEncounter> getStaticPokemon() { return statics; }
    @Override public List<InGameTrade> getInGameTrades() { return trades; }
    @Override public void setInGameTrades(List<InGameTrade> value) { if (value != trades) { trades.clear(); trades.addAll(value); } }
    @Override public int miscTweaksAvailable() { return 0x7FFFFFFF; }
    @Override public void applyMiscTweak(MiscTweak tweak) { miscWrites.add(tweak.getValue()); }
    @Override public void setPCPotionItem(Item item) { pcPotion = item; }
    @Override public boolean setCatchingTutorial(Species opponent, Species player) { tutorialOpponent=opponent; tutorialPlayer=player; return true; }
}
