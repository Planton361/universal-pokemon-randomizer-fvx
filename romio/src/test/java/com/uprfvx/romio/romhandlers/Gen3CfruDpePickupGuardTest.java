package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.ItemIDs;
import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.PickupItem;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import filefunctions.IOFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class Gen3CfruDpePickupGuardTest {
    // Public-source ABI fixtures only: no ROM file, compiler output or private artifact is loaded.
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
                items.set(id, new Item(id, "Synthetic " + raw));
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

    @Test
    void rejectsBothEntryPointsBeforeTableAccessWhenNotLoaded() throws Exception {
        Gen3RomHandler handler = new Gen3RomHandler();
        Fixture.setField(handler, "useCfruDpeGen9SpeciesCount", true);
        RomIOException read = assertThrows(RomIOException.class, handler::getPickupItems);
        RomIOException write = assertThrows(RomIOException.class, () -> handler.setPickupItems(List.of()));
        assertTrue(read.getMessage().contains("Pickup Items to Unchanged"));
        assertEquals(read.getMessage(), write.getMessage());
    }

    @Test
    void keepsAllPhysicalSlotsIncludingRepeatedDestinyKnotsAndReadSetIsIdentity() throws Exception {
        Fixture f = new Fixture();
        byte[] original = f.image.clone();
        List<PickupItem> before = f.handler.getPickupItems();
        assertEquals(29, before.size());
        for (int slot = 0; slot < 29; slot++) {
            int raw = slot < 18 ? Fixture.COMMON_IDS[slot] : Fixture.RARE_IDS[slot - 18];
            assertEquals(Gen3Constants.itemIDToStandard(raw), before.get(slot).getItem().getId());
        }
        assertNotSame(before.get(24), before.get(26));
        assertEquals(before.get(24).getItem(), before.get(28).getItem());
        assertFalse(Arrays.equals(before.get(24).getProbabilities(), before.get(28).getProbabilities()));
        f.handler.setPickupItems(before);
        assertEquals(before, f.handler.getPickupItems());
        assertArrayEquals(original, f.image);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void allTenSlidingWindowsAndExactRareTailPartition(int row) throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> slots = f.handler.getPickupItems();
        int[] nominal = {30, 10, 10, 10, 10, 10, 10, 4, 4, 1, 1};
        int[] expectedDrawCounts = {19661, 6553, 6554, 6554, 6553, 6554, 6553, 2622, 2621, 656, 655};
        int[] actualDrawCounts = new int[29];
        for (int draw = 0; draw < 65536; draw++) {
            int pick = 0;
            while (draw >= Fixture.CEILINGS[pick]) pick++;
            int slot = pick < 9 ? row + pick : 18 + row + pick - 9;
            actualDrawCounts[slot]++;
        }
        int nominalTotal = 0;
        for (int slot = 0; slot < 29; slot++) {
            int index = slot < 18 ? slot - row : slot - 18 - row;
            boolean active = index >= 0 && index < (slot < 18 ? 9 : 2);
            int pick = index + (slot < 18 ? 0 : 9);
            assertEquals(active ? nominal[pick] : 0, slots.get(slot).getProbabilities()[row]);
            assertEquals(active ? expectedDrawCounts[pick] : 0, actualDrawCounts[slot]);
            nominalTotal += slots.get(slot).getProbabilities()[row];
        }
        assertEquals(100, nominalTotal);
        // All actual levels, including 1/10/11/90/91/100 boundaries, select this row.
        for (int level = row * 10 + 1; level <= row * 10 + 10; level++) assertEquals(row, (level - 1) / 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {13, 686, 688})
    void setterBeforeGetterWritesOnly58AuthorizedBytesAndRoundtripsMappedAndUniqueIds(int raw) throws Exception {
        Fixture source = new Fixture();
        List<PickupItem> next = source.replacements(raw);
        Fixture f = new Fixture(); // setter has no prior get on this handler
        byte[] original = f.image.clone();
        f.handler.setPickupItems(next);
        assertEquals(next, f.handler.getPickupItems());
        for (int slot = 0; slot < 29; slot++) assertEquals(raw, IOFunctions.read2ByteInt(f.image, Fixture.slotOffset(slot)));
        for (int offset = 0; offset < f.image.length; offset++) {
            if (!Fixture.writable(offset)) assertEquals(original[offset], f.image[offset], "unexpected write at " + offset);
        }
    }

    @Test
    void ignoresUnreferencedDuplicateDescriptorsAndItemDecoys() throws Exception {
        Fixture f = new Fixture();
        System.arraycopy(f.image, Fixture.DESCRIPTOR, f.image, 0x21000, 64);
        System.arraycopy(f.image, Fixture.COMMON, f.image, 0x21200, 36);
        f.handler.setPickupItems(f.replacements(13));
        assertEquals(29, f.handler.getPickupItems().size());
    }

    @Test
    void distinctReplacementsForRepeatedValuesRetainPhysicalSlotOrder() throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> before = f.handler.getPickupItems();
        List<PickupItem> next = new ArrayList<>();
        for (int slot = 0; slot < 29; slot++) {
            PickupItem item = new PickupItem(f.item(Fixture.COMMON_IDS[slot % 18]));
            System.arraycopy(before.get(slot).getProbabilities(), 0, item.getProbabilities(), 0, 10);
            next.add(item);
        }
        f.handler.setPickupItems(next);
        assertEquals(next, f.handler.getPickupItems());
        assertNotEquals(next.get(24).getItem(), next.get(26).getItem());
        assertNotEquals(next.get(26).getItem(), next.get(28).getItem());
    }

    @Test
    void usesLiveRelocatedArraysRatherThanOldCopiesOrCachedOffsets() throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> next = f.replacements(686);
        int common = 0x21000, rare = 0x21400;
        System.arraycopy(f.image, Fixture.COMMON, f.image, common, 36);
        System.arraycopy(f.image, Fixture.RARE, f.image, rare, 22);
        f.pointer(Fixture.DESCRIPTOR + 16, common);
        f.pointer(Fixture.DESCRIPTOR + 20, rare);
        byte[] original = f.image.clone();
        f.handler.setPickupItems(next);
        assertEquals(next, f.handler.getPickupItems());
        int writableCount = 0;
        for (int offset = 0; offset < original.length; offset++) {
            boolean writable = offset >= common && offset < common + 36 || offset >= rare && offset < rare + 22;
            if (writable) writableCount++;
            else assertEquals(original[offset], f.image[offset], "unexpected relocated write " + offset);
        }
        assertEquals(58, writableCount);
    }

    @Test
    void truncatedImageRejectedWithoutUncheckedArrayAccess() throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> next = f.replacements(13);
        byte[] shortImage = Arrays.copyOf(f.image, Fixture.REPOINTS[0] + 3);
        Fixture.setField(f.handler, "rom", shortImage);
        byte[] original = shortImage.clone();
        assertThrows(RomIOException.class, f.handler::getPickupItems);
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        assertArrayEquals(original, shortImage);
    }

    @Test
    void canonicalMetadataRejectsForgedEligibleReplacementAtFinalSlot() throws Exception {
        for (String invalid : List.of("key item", "TM", "placeholder", "not loaded", "policy ban", "unmapped")) {
            Fixture f = new Fixture();
            List<PickupItem> next = new ArrayList<>(f.replacements(686));
            int raw = 12, id = Gen3Constants.itemIDToStandard(raw);
            if (invalid.equals("key item")) f.item(raw).setAllowed(false);
            if (invalid.equals("TM")) f.item(raw).setTM(true);
            if (invalid.equals("placeholder")) f.items.set(id, new Item(id, "item #12"));
            if (invalid.equals("not loaded")) f.items.set(id, null);
            if (invalid.equals("policy ban")) f.items.set(id, new Item(id, "Helix Fossil"));
            if (invalid.equals("unmapped")) {
                id = java.util.stream.IntStream.range(1, ItemIDs.UNIQUE_OFFSET)
                        .filter(value -> !Gen3Constants.itemIDToInternalMap.containsKey(value)).findFirst().orElseThrow();
            }
            PickupItem forged = new PickupItem(new Item(id, "Forged permitted"));
            System.arraycopy(next.get(28).getProbabilities(), 0, forged.getProbabilities(), 0, 10);
            next.set(28, forged);
            byte[] original = f.image.clone();
            assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next), invalid);
            assertArrayEquals(original, f.image, invalid);
        }
    }

    static Stream<Arguments> malformedDescriptors() {
        List<Arguments> cases = new ArrayList<>();
        Consumer<Fixture> oldBuild = f -> { for (int site : Fixture.REPOINTS) f.word(site, 0); };
        cases.add(Arguments.of("pre-v1/dormant descriptor", oldBuild));
        for (int site : Fixture.REPOINTS) {
            cases.add(Arguments.of("split live root " + site, (Consumer<Fixture>) f -> f.pointer(site, 0x20400)));
        }
        for (int i = 0; i < 10; i++) {
            int index = i;
            cases.add(Arguments.of("bridge opcode " + i, (Consumer<Fixture>) f -> f.half(Fixture.BRIDGE + index * 2, 0)));
        }
        int[][] words = {{0, 0}, {8, 0}, {8, 1}, {8, 7}, {8, 14}, {12, 2}, {52, 0}, {52, 778}, {52, 799},
                {56, Fixture.address(Fixture.CONSUMER + 2) | 1}, {60, Fixture.address(Fixture.BRIDGE + 4) | 1}};
        for (int[] word : words) {
            cases.add(Arguments.of("descriptor word " + word[0] + "=" + word[1],
                    (Consumer<Fixture>) f -> f.word(Fixture.DESCRIPTOR + word[0], word[1])));
        }
        for (int field : new int[]{4, 6, 32, 34, 36, 38}) {
            cases.add(Arguments.of("descriptor u16 " + field, (Consumer<Fixture>) f -> f.half(Fixture.DESCRIPTOR + field, IOFunctions.read2ByteInt(f.image, Fixture.DESCRIPTOR + field) + 1)));
        }
        for (int field = 40; field < 52; field++) {
            int index = field;
            cases.add(Arguments.of("width/window/reserved " + field,
                    (Consumer<Fixture>) f -> f.image[Fixture.DESCRIPTOR + index] ^= 1));
        }
        int[][] badPointers = {{16, 0}, {16, 0x02000000}, {16, 0x0A020B00}, {16, -1},
                {16, Fixture.address(Fixture.COMMON + 1)}, {16, Fixture.address(0x21FE0)},
                {20, Fixture.address(Fixture.COMMON)}, {20, Fixture.address(Fixture.COMMON + 20)},
                {16, Fixture.address(Fixture.BRIDGE)}, {16, Fixture.address(Fixture.DESCRIPTOR)},
                {16, Fixture.address(Fixture.CONSUMER)}, {16, Fixture.address(Fixture.COMMAND)},
                {16, Fixture.address(Fixture.REPOINTS[0])}, {16, Fixture.address(0xA0)},
                {24, Fixture.address(Fixture.COMMON_CEILINGS + 2)}, {28, Fixture.address(Fixture.COMMON_CEILINGS)}};
        for (int[] pointer : badPointers) {
            cases.add(Arguments.of("invalid/alias table pointer " + pointer[0] + "=" + pointer[1],
                    (Consumer<Fixture>) f -> f.word(Fixture.DESCRIPTOR + pointer[0], pointer[1])));
        }
        cases.add(Arguments.of("big endian pointer", (Consumer<Fixture>) f ->
                f.word(Fixture.DESCRIPTOR + 16, Integer.reverseBytes(Fixture.address(Fixture.COMMON)))));
        cases.add(Arguments.of("non-Thumb E5", (Consumer<Fixture>) f -> f.pointer(Fixture.COMMAND + 0xE5 * 4, Fixture.BRIDGE)));
        cases.add(Arguments.of("misaligned bridge", (Consumer<Fixture>) f -> f.word(Fixture.COMMAND + 0xE5 * 4, Fixture.address(Fixture.BRIDGE + 2) | 1)));
        cases.add(Arguments.of("bad descriptor pointer", (Consumer<Fixture>) f -> f.pointer(Fixture.BRIDGE + 20, 0x21FF0)));
        cases.add(Arguments.of("non-Thumb consumer", (Consumer<Fixture>) f -> f.pointer(Fixture.BRIDGE + 24, Fixture.CONSUMER)));
        cases.add(Arguments.of("out-of-bounds consumer", (Consumer<Fixture>) f -> f.word(Fixture.BRIDGE + 24, Fixture.address(0x22000) | 1)));
        for (int i = 0; i < 11; i++) {
            int offset = i < 9 ? Fixture.COMMON_CEILINGS + i * 4 : Fixture.RARE_CEILINGS + (i - 9) * 4;
            cases.add(Arguments.of("ceiling " + i, (Consumer<Fixture>) f -> f.word(offset, 65535)));
        }
        for (int raw : new int[]{0, 779, 799, 65535}) {
            cases.add(Arguments.of("raw item " + raw, (Consumer<Fixture>) f -> f.half(Fixture.RARE + 20, raw)));
        }
        cases.add(Arguments.of("unloaded item", (Consumer<Fixture>) f -> f.items.set(Gen3Constants.itemIDToStandard(13), null)));
        cases.add(Arguments.of("unknown item name", (Consumer<Fixture>) f -> f.items.set(Gen3Constants.itemIDToStandard(13), new Item(Gen3Constants.itemIDToStandard(13), "item #13"))));
        cases.add(Arguments.of("disallowed source item", (Consumer<Fixture>) f -> f.item(13).setAllowed(false)));
        cases.add(Arguments.of("TM source item", (Consumer<Fixture>) f -> f.item(13).setTM(true)));
        cases.add(Arguments.of("wrong profile", (Consumer<Fixture>) f -> f.entry.setRomCode("BPEE")));
        cases.add(Arguments.of("wrong game type", (Consumer<Fixture>) f -> f.entry.setRomType(Gen3Constants.RomType_Em)));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedDescriptors")
    void malformedInputRejectsReadAndWriteWithoutAnyMutation(String name, Consumer<Fixture> corrupt) throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> next = f.replacements(13);
        corrupt.accept(f);
        byte[] original = f.image.clone();
        RomIOException read = assertThrows(RomIOException.class, f.handler::getPickupItems, name);
        RomIOException write = assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next), name);
        assertEquals(read.getMessage(), write.getMessage());
        assertTrue(read.getMessage().contains("Pickup Items to Unchanged"));
        assertArrayEquals(original, f.image, name);
    }

    @Test
    void revalidatesLivePointersOnSetterAfterSuccessfulRead() throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> next = f.replacements(13);
        f.pointer(Fixture.REPOINTS[4], 0x20400);
        byte[] original = f.image.clone();
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        assertArrayEquals(original, f.image);
    }

    @Test
    void everyInvalidReplacementAtLastSlotIsRejectedBeforeAnyWrite() throws Exception {
        for (int invalid : new int[]{ItemIDs.UNIQUE_OFFSET, ItemIDs.UNIQUE_OFFSET + 779,
                ItemIDs.UNIQUE_OFFSET + 65536, ItemIDs.UNIQUE_OFFSET + 13, Integer.MAX_VALUE}) {
            Fixture f = new Fixture();
            List<PickupItem> next = new ArrayList<>(f.replacements(686));
            PickupItem bad = new PickupItem(new Item(invalid, "Forged"));
            System.arraycopy(next.get(28).getProbabilities(), 0, bad.getProbabilities(), 0, 10);
            next.set(28, bad);
            byte[] original = f.image.clone();
            assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next), "invalid ID " + invalid);
            assertArrayEquals(original, f.image);
        }
    }

    @Test
    void nullWrongCountProbabilityAndIneligiblePlansAreAtomic() throws Exception {
        Fixture f = new Fixture();
        List<PickupItem> next = new ArrayList<>(f.replacements(686));
        byte[] original = f.image.clone();
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(null));
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(List.of()));
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next.subList(0, 28)));
        next.add(next.getFirst());
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        next.removeLast();
        PickupItem last = next.set(28, null);
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        next.set(28, last);
        last.getProbabilities()[9]++;
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        last.getProbabilities()[9]--;
        f.item(686).setTM(true);
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        f.item(686).setTM(false);
        f.item(686).setAllowed(false);
        assertThrows(RomIOException.class, () -> f.handler.setPickupItems(next));
        assertArrayEquals(original, f.image);
    }
}
