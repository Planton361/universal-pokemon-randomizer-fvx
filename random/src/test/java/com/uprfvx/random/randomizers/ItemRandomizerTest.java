package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import filefunctions.IOFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import com.uprfvx.romio.MiscTweak;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.PickupItem;
import com.uprfvx.romio.gamedata.Shop;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ItemRandomizerTest extends RandomizerTest{

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void shuffleFieldItemsRetainsSameItems(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.SHUFFLE);

        Map<Item, Integer> itemCountsBefore = countFieldItems(romHandler.getFieldItems());
        System.out.println(itemCountsBefore);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();
        Map<Item, Integer> itemCountsAfter = countFieldItems(romHandler.getFieldItems());
        System.out.println(itemCountsAfter);

        assertEquals(itemCountsBefore, itemCountsAfter);
    }

    private Map<Item, Integer> countFieldItems(List<Item> fieldItems) {
        Map<Item, Integer> counts = new HashMap<>();
        for (Item item : fieldItems) {
            if (!counts.containsKey(item)) {
                counts.put(item, 0);
            }
            counts.put(item, counts.get(item) + 1);
        }
        return counts;
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomFieldItemsSetsOnlyAllowedItems(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.RANDOM);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();

        for (Item item : romHandler.getFieldItems()) {
            System.out.println(item);
            assertTrue(item.isAllowed());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomEvenFieldItemsSetsOnlyAllowedItems(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.RANDOM_EVEN);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();

        for (Item item : romHandler.getFieldItems()) {
            System.out.println(item);
            assertTrue(item.isAllowed());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomFieldItemsCanBanBadItems(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.RANDOM);
        s.setBanBadRandomFieldItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();

        for (Item item : romHandler.getFieldItems()) {
            System.out.println(item);
            assertFalse(item.isBad());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomEvenFieldItemsCanBanBadItems(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.RANDOM_EVEN);
        s.setBanBadRandomFieldItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();

        for (Item item : romHandler.getFieldItems()) {
            System.out.println(item);
            assertFalse(item.isBad());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomEvenWorks(String romName) {
        // no item appears more than one more time than any other
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setFieldItemsMod(Settings.FieldItemsMod.RANDOM_EVEN);
        new ItemRandomizer(romHandler, s, RND).randomizeFieldItems();

        Map<Item, Integer> counts = countFieldItems(romHandler.getFieldItems());
        System.out.println(counts);
        Set<Item> uniqueItems = romHandler.getMegaStones();
        Set<Integer> filteredValues = counts.entrySet().stream()
                .filter(et -> !et.getKey().isTM())
                .filter(et -> !uniqueItems.contains(et.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        int min = filteredValues.stream().min(Integer::compareTo).orElseThrow(RuntimeException::new);
        int max = filteredValues.stream().max(Integer::compareTo).get();
        System.out.println("min: " + min + ", max: " + max);
        assertTrue(max - min <= 1);
    }

    // TODO: test uniqueNoSellItems (i.e. Mega Stones)

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void shuffleShopItemsRetainsSameItems(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        Map<Item, Integer> itemCountsBefore = countItems(romHandler.getShops()
                .stream().filter(Shop::isSpecialShop).collect(Collectors.toList()));
        System.out.println(itemCountsBefore);
        new ItemRandomizer(romHandler, new Settings(), RND).shuffleShopItems();
        Map<Item, Integer> itemCountsAfter = countItems(romHandler.getShops()
                .stream().filter(Shop::isSpecialShop).collect(Collectors.toList()));
        System.out.println(itemCountsAfter);

        assertEquals(itemCountsBefore, itemCountsAfter);
    }

    private Map<Item, Integer> countItems(List<Shop> shops) {
        Map<Item, Integer> counts = new HashMap<>();
        for (Shop shop : shops) {
            for (Item item : shop.getItems()) {
                if (!counts.containsKey(item)) {
                    counts.put(item, 0);
                }
                counts.put(item, counts.get(item) + 1);
            }
        }
        return counts;
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void shuffleShopItemsCausesDifferentOrder(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        List<Item> before = new ArrayList<>();
        romHandler.getShops().stream().filter(Shop::isSpecialShop).forEach(shop -> before.addAll(shop.getItems()));
        System.out.println(before);

        new ItemRandomizer(romHandler, new Settings(), RND).shuffleShopItems();

        List<Item> after = new ArrayList<>();
        romHandler.getShops().stream().filter(Shop::isSpecialShop).forEach(shop -> after.addAll(shop.getItems()));
        System.out.println(after);
        assertNotEquals(before, after);
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizeShopItemsCanBanBadItems(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        Settings s = new Settings();
        s.setBanBadRandomShopItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeShopItems();

        for (Shop shop : romHandler.getShops()) {
            if (!shop.isSpecialShop()) {
                continue;
            }
            System.out.println(shop);
            for (Item item : shop.getItems()) {
                assertFalse(item.isBad());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizeShopItemsCanBadRegularShopItems(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        Settings s = new Settings();
        s.setBanRegularShopItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeShopItems();

        Set<Item> regularShop = romHandler.getRegularShopItems();
        for (Shop shop : romHandler.getShops()) {
            if (!shop.isSpecialShop()) {
                continue;
            }
            System.out.println(shop);
            for (Item item : shop.getItems()) {
                assertFalse(regularShop.contains(item));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizeShopItemsCanBanOverpoweredShopItems(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        Settings s = new Settings();
        s.setBanOPShopItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeShopItems();

        Set<Item> opShop = romHandler.getOPShopItems();
        for (Shop shop : romHandler.getShops()) {
            if (!shop.isSpecialShop()) {
                continue;
            }
            System.out.println(shop);
            for (Item item : shop.getItems()) {
                assertFalse(opShop.contains(item));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizeShopItemsCanGuaranteeEvolutionAndXItems(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.hasShopSupport());

        Settings s = new Settings();
        s.setGuaranteeEvolutionItems(true);
        s.setGuaranteeXItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizeShopItems();

        Set<Item> evoItems = romHandler.getEvolutionItems();
        Map<Item, Boolean> placedEvo = new HashMap<>();
        for (Item evoItem : evoItems) {
            placedEvo.put(evoItem, false);
        }
        Set<Item> xItems = romHandler.getXItems();
        Map<Item, Boolean> placedX = new HashMap<>();
        for (Item xItem : xItems) {
            placedX.put(xItem, false);
        }

        for (Shop shop : romHandler.getShops()) {
            if (!shop.isSpecialShop()) {
                continue;
            }
            System.out.println(shop);
            for (Item item : shop.getItems()) {
                if (evoItems.contains(item)) {
                    placedEvo.put(item, true);
                }
                if (xItems.contains(item)) {
                    placedX.put(item, true);
                }
            }
        }

        System.out.println("Evo: " + placedEvo);
        System.out.println("X: " + placedX);
        int placedEvoCount = (int) placedEvo.values().stream().filter(b -> b).count();
        assertEquals(evoItems.size(), placedEvoCount);
        int placedXCount = (int) placedX.values().stream().filter(b -> b).count();
        assertEquals(xItems.size(), placedXCount);
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizePickupItemsCanBanBadItems(String romName) {
        assumeTrue(getGenerationNumberOf(romName) >= 3);
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setBanBadRandomPickupItems(true);
        new ItemRandomizer(romHandler, s, RND).randomizePickupItems();

        for (PickupItem pi : romHandler.getPickupItems()) {
            System.out.println(pi);
            assertFalse(pi.getItem().isBad());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizePickupItemsBansTMsEvenIfTMsAreHoldableAndNotReusable(String romName) {
        assumeTrue(getGenerationNumberOf(romName) >= 3);
        activateRomHandler(romName);
        assumeTrue(romHandler.canTMsBeHeld());
        assumeTrue(!romHandler.isTMsReusable());

        Settings s = new Settings();
        new ItemRandomizer(romHandler, s, RND).randomizePickupItems();

        boolean tmUsed = false;
        for (PickupItem pi : romHandler.getPickupItems()) {
            System.out.println(pi);
            if (pi.getItem().isTM()) {
                tmUsed = true;
                break;
            }
        }

        assertFalse(tmUsed);
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizePickupItemsBanTMsIfTMsAreReusable(String romName) {
        assumeTrue(getGenerationNumberOf(romName) >= 3);
        activateRomHandler(romName);

        if (!romHandler.isTMsReusable()) {
            romHandler.applyMiscTweak(MiscTweak.REUSABLE_TMS);
        }

        new ItemRandomizer(romHandler, new Settings(), RND).randomizePickupItems();

        for (PickupItem pi : romHandler.getPickupItems()) {
            System.out.println(pi);
            assertFalse(pi.getItem().isTM());
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void randomizePickupItemsBanTMsIfTMsAreNotHoldable(String romName) {
        assumeTrue(getGenerationNumberOf(romName) >= 3);
        activateRomHandler(romName);
        assumeTrue(!romHandler.canTMsBeHeld());

        new ItemRandomizer(romHandler, new Settings(), RND).randomizePickupItems();

        for (PickupItem pi : romHandler.getPickupItems()) {
            System.out.println(pi);
            assertFalse(pi.getItem().isTM());
        }
    }
}

// No RandomizerTest superclass: that superclass opens real ROMs in @BeforeAll.
class CfruDpePickupSourceTest {
    static class Fixture {
        static final int COMMAND = 0x20000, BRIDGE = 0x20800, DESCRIPTOR = 0x20880,
                CONSUMER = 0x20A00, COMMON = 0x20B00, RARE = 0x20B80,
                COMMON_CEILINGS = 0x20C00, RARE_CEILINGS = 0x20C80;
        static final int[] REPOINTS = {0x14C1C, 0x15A28, 0x15C6C, 0x15C98, 0x1D054};
        static final int[] COMMON_IDS = {13, 14, 22, 3, 86, 85, 23, 21, 2, 24, 68, 93, 94, 111, 19, 25, 69, 37};
        static final int[] RARE_IDS = {21, 110, 187, 19, 34, 686, 688, 36, 688, 200, 688};
        static final int[] CEILINGS = {19661, 26214, 32768, 39322, 45875, 52429, 58982, 61604, 64225, 64881, 65536};
        final byte[] image = new byte[0x22000];
        final Gen3RomHandler handler = new Gen3RomHandler();
        final Gen3RomEntry entry;
        final List<Item> items = new ArrayList<>(Collections.nCopies(ItemIDs.UNIQUE_OFFSET + 800, null));

        Fixture() throws Exception {
            Arrays.fill(image, (byte) 0x55); // nonzero neighbors make accidental writes visible
            Constructor<Gen3RomEntry> ctor = Gen3RomEntry.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            entry = ctor.newInstance("PUBLIC SOURCE SYNTHETIC ONLY");
            entry.setRomCode("BPRE");
            entry.setRomType(Gen3Constants.RomType_FRLG);
            for (int raw = 1; raw < 799; raw++) {
                int id = Gen3Constants.itemIDToStandard(raw);
                Item item = new Item(id, "Synthetic " + raw);
                // Only public baseline Pickup entries plus one TM are enabled in this pool fixture.
                boolean baseline = Arrays.stream(COMMON_IDS).anyMatch(value -> value == idToRaw(id))
                        || Arrays.stream(RARE_IDS).anyMatch(value -> value == idToRaw(id));
                item.setAllowed(baseline || raw == 289);
                item.setTM(raw == 289);
                item.setBad(raw == 14);
                items.set(id, item);
            }
            setField(handler, "rom", image);
            setField(handler, "romEntry", entry);
            setField(handler, "items", items);
            setField(handler, "useCfruDpeGen9SpeciesCount", true);
            for (int site : REPOINTS) pointer(site, COMMAND);
            word(COMMAND + 0xE5 * 4, address(BRIDGE) | 1);
            int[] bridge = {0xB510, 0x4804, 0x4B04, 0xF000, 0xF803, 0xBC10, 0xBC01, 0x4700, 0x4718, 0x46C0};
            for (int i = 0; i < bridge.length; i++) half(BRIDGE + i * 2, bridge[i]);
            pointer(BRIDGE + 20, DESCRIPTOR);
            word(BRIDGE + 24, address(CONSUMER) | 1);
            half(CONSUMER, 0x4770); // synthetic stub, no compiled-C proof claimed
            word(DESCRIPTOR, 0x31555043);
            half(DESCRIPTOR + 4, 1);
            half(DESCRIPTOR + 6, 64);
            word(DESCRIPTOR + 8, 6);
            word(DESCRIPTOR + 12, 1);
            int[] tables = {COMMON, RARE, COMMON_CEILINGS, RARE_CEILINGS};
            int[] counts = {18, 11, 9, 2};
            for (int i = 0; i < 4; i++) {
                pointer(DESCRIPTOR + 16 + i * 4, tables[i]);
                half(DESCRIPTOR + 32 + i * 2, counts[i]);
            }
            int[] shape = {4, 2, 4, 10, 10, 9, 2, 1, 100, 0, 0, 0};
            for (int i = 0; i < shape.length; i++) image[DESCRIPTOR + 40 + i] = (byte) shape[i];
            word(DESCRIPTOR + 52, 779);
            word(DESCRIPTOR + 56, address(CONSUMER) | 1);
            word(DESCRIPTOR + 60, address(BRIDGE) | 1);
            for (int i = 0; i < COMMON_IDS.length; i++) half(COMMON + i * 2, COMMON_IDS[i]);
            for (int i = 0; i < RARE_IDS.length; i++) half(RARE + i * 2, RARE_IDS[i]);
            for (int i = 0; i < CEILINGS.length; i++) {
                word(i < 9 ? COMMON_CEILINGS + i * 4 : RARE_CEILINGS + (i - 9) * 4, CEILINGS[i]);
            }
        }

        static void setField(Object target, String name, Object value) throws Exception {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }

        static int idToRaw(int id) { return Gen3Constants.itemIDToInternal(id); }
        static int address(int offset) { return 0x08000000 + offset; }
        void word(int offset, int value) { IOFunctions.writeFullInt(image, offset, value); }
        void half(int offset, int value) { IOFunctions.write2ByteInt(image, offset, value); }
        void pointer(int offset, int target) { word(offset, address(target)); }
        Item item(int raw) { return items.get(Gen3Constants.itemIDToStandard(raw)); }
        static int slotOffset(int slot) { return slot < 18 ? COMMON + slot * 2 : RARE + (slot - 18) * 2; }
        static boolean writable(int offset) {
            return offset >= COMMON && offset < COMMON + 36 || offset >= RARE && offset < RARE + 22;
        }
        List<PickupItem> replacements(int raw) {
            return handler.getPickupItems().stream().map(old -> {
                PickupItem next = new PickupItem(item(raw));
                System.arraycopy(old.getProbabilities(), 0, next.getProbabilities(), 0, 10);
                return next;
            }).toList();
        }
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualRandomizerUsesAll29SlotsAndPreservesBytesProbabilitiesAndDeterminism(boolean banBad) throws Exception {
        Fixture first = new Fixture(), second = new Fixture();
        Settings settings = new Settings();
        settings.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
        settings.setRomName("PUBLIC SYNTHETIC");
        settings.setPickupItemsMod(Settings.PickupItemsMod.RANDOM);
        settings.setBanBadRandomPickupItems(banBad);
        Settings restored = Settings.fromString(settings.toString());
        assertEquals(Settings.PickupItemsMod.RANDOM, restored.getPickupItemsMod());
        assertEquals(banBad, restored.isBanBadRandomPickupItems());
        List<PickupItem> baseline = first.handler.getPickupItems();
        byte[] original = first.image.clone();
        CountingRandom random = new CountingRandom(717);
        ItemRandomizer writer = new ItemRandomizer(first.handler, restored, random);
        writer.randomizePickupItems();
        new ItemRandomizer(second.handler, restored, new Random(717)).randomizePickupItems();
        assertTrue(writer.isPickupChangesMade());
        assertEquals(29, random.calls); // equal Destiny Knot values still get independent draws
        assertArrayEquals(first.image, second.image);
        List<PickupItem> output = first.handler.getPickupItems();
        assertEquals(29, output.size());
        for (int slot = 0; slot < 29; slot++) {
            Item replacement = output.get(slot).getItem();
            assertTrue(replacement.isAllowed());
            assertFalse(replacement.isTM());
            if (banBad) assertFalse(replacement.isBad());
            assertArrayEquals(baseline.get(slot).getProbabilities(), output.get(slot).getProbabilities());
        }
        assertFalse(Arrays.equals(original, first.image));
        for (int offset = 0; offset < original.length; offset++) {
            if (!Fixture.writable(offset)) assertEquals(original[offset], first.image[offset], "unexpected write " + offset);
        }
        // Re-running the same seed on an already randomized table must resolve the same live descriptor.
        new ItemRandomizer(first.handler, restored, new Random(717)).randomizePickupItems();
        assertArrayEquals(first.image, second.image);
    }

    @Test
    void emptyAndOnlyTmPoolsFailBeforeMutationWithoutClaimingChanges() throws Exception {
        for (boolean keepTM : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            for (Item item : f.items) if (item != null) item.setAllowed(keepTM && item.isTM());
            byte[] original = f.image.clone();
            ItemRandomizer randomizer = new ItemRandomizer(f.handler, new Settings(), new Random(717));
            assertThrows(IllegalStateException.class, randomizer::randomizePickupItems);
            assertFalse(randomizer.isPickupChangesMade());
            assertArrayEquals(original, f.image);
        }
    }

    @Test
    void importedRandomSettingsRejectPreV1WithoutMutationOrSilentCoercion() throws Exception {
        Fixture f = new Fixture();
        f.half(Fixture.DESCRIPTOR + 4, 0);
        Settings settings = new Settings();
        settings.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
        settings.setRomName("PUBLIC SYNTHETIC");
        settings.setPickupItemsMod(Settings.PickupItemsMod.RANDOM);
        Settings restored = Settings.fromString(settings.toString());
        byte[] original = f.image.clone();
        ItemRandomizer randomizer = new ItemRandomizer(f.handler, restored, new Random(717));
        RomIOException failure = assertThrows(RomIOException.class, randomizer::randomizePickupItems);
        assertTrue(failure.getMessage().contains("Pickup Items to Unchanged"));
        assertEquals(Settings.PickupItemsMod.RANDOM, restored.getPickupItemsMod());
        assertFalse(randomizer.isPickupChangesMade());
        assertArrayEquals(original, f.image);
    }

    private static class CountingRandom extends Random {
        int calls;
        CountingRandom(long seed) { super(seed); }
        @Override public int nextInt(int bound) { calls++; return super.nextInt(bound); }
    }
}
