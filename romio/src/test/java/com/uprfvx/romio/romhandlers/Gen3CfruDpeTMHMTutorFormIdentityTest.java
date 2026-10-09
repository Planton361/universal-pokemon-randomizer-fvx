package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.exceptions.RomIOException;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic memory only. The strict test init script adds :random to testRuntimeOnly,
 * allowing reflection to exercise its real consumers without a romio production dependency. */
class Gen3CfruDpeTMHMTutorFormIdentityTest {
    private static final int TM = 0x140000, TUTOR = 0x150000;
    private static final int TM_POINTER = 0x43C68, TUTOR_POINTER = 0x120C30;
    private static final long[] SEEDS = {0, 1, 729, 20261009};

    @Test
    void reproducesTreeMapCollisionWithoutChangingSpeciesContracts() {
        Species ordinary = species(19, 19), regional = species(19, 1059);
        assertEquals(0, ordinary.compareTo(regional));
        assertNotEquals(ordinary, regional);
        assertEquals(ordinary, species(777, 19)); // Existing equality is internal identity only.
        Map<Species, boolean[]> broken = new TreeMap<>();
        broken.put(ordinary, new boolean[]{false, true});
        broken.put(regional, new boolean[]{false, false});
        assertEquals(1, broken.size());
        assertSame(ordinary, broken.keySet().iterator().next());
        assertFalse(broken.get(ordinary)[1]); // Regional value misassociated with ordinary key.
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void sameDexOwnersHaveIndependentKeysFlagsAndExactNoopRoundtrip(boolean tutor) throws Exception {
        Fixture f = fixture(true, false);
        byte[] before = f.bytes.clone();
        Map<Species, boolean[]> map = read(f, tutor);
        assertInstanceOf(LinkedHashMap.class, map);
        assertEquals(List.of(1, 19, 1059, 1060, 1439), identities(map));
        assertEquals(5, map.size());
        for (Species s : f.owners) {
            boolean[] flags = map.get(s);
            assertEquals(count(tutor) + 1, flags.length);
            assertFalse(flags[0]);
            for (int bit = 1; bit <= count(tutor); bit++)
                assertEquals(pattern(s.getSpeciesSetIdentityNumber(), bit), flags[bit]);
            assertSame(flags, map.get(species(999, s.getSpeciesSetIdentityNumber())));
        }
        assertNotSame(map.get(f.owners.get(1)), map.get(f.owners.get(2)));
        assertFalse(Arrays.equals(map.get(f.owners.get(1)), map.get(f.owners.get(2))));
        write(f, tutor, map);
        assertArrayEquals(before, f.bytes); // Includes pointers, slot 0, every absent row and neighbors.
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void fullPhysicalTableNoopRetainsAll1439PositiveOwners(boolean tutor) throws Exception {
        Fixture f = fixture(true, false);
        List<Species> all = new ArrayList<>(); all.add(null);
        for (int id = 1; id <= 1439; id++) {
            all.add(species((id % 1025) + 1, id)); // Collisions across a full native model.
            for (int b = 0; b < width(tutor); b++)
                f.bytes[base(tutor) + id * width(tutor) + b] = (byte) (id * 31 + b * 7);
        }
        set(f.handler, "speciesList", all); set(f.handler, "numRealPokemon", 1439);
        byte[] before = f.bytes.clone();
        Map<Species, boolean[]> map = read(f, tutor);
        assertEquals(1439, map.size());
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 1439).boxed().toList(), identities(map));
        write(f, tutor, map);
        assertArrayEquals(before, f.bytes);
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void ownerEditsWriteOnlyItsOwnNativeRowIncludingPecharunt(boolean tutor) throws Exception {
        for (int index = 0; index < 5; index++) {
            Fixture f = fixture(true, false);
            byte[] before = f.bytes.clone();
            Map<Species, boolean[]> map = read(f, tutor);
            Species owner = f.owners.get(index);
            boolean[] flags = map.get(owner);
            for (int bit = 1; bit < flags.length; bit++) flags[bit] = !flags[bit];
            write(f, tutor, map);
            int start = base(tutor) + owner.getSpeciesSetIdentityNumber() * width(tutor);
            for (int b = 0; b < f.bytes.length; b++) {
                byte expected = b >= start && b < start + width(tutor) ? (byte) ~before[b] : before[b];
                if (expected != f.bytes[b]) fail("unexpected byte outside owner row: " + b);
            }
            assertArrayEquals(flags, read(f, tutor).get(owner));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void singletonAndEmptyMapsPreserveReadWriteSemantics(boolean tutor) throws Exception {
        Fixture f = fixture(true, false);
        set(f.handler, "speciesList", Arrays.asList(null, f.owners.getLast()));
        set(f.handler, "numRealPokemon", 1);
        byte[] before = f.bytes.clone();
        Map<Species, boolean[]> map = read(f, tutor);
        assertEquals(List.of(1439), identities(map));
        write(f, tutor, map);
        write(f, tutor, Collections.emptyMap());
        assertArrayEquals(before, f.bytes);
        set(f.handler, "numRealPokemon", 0);
        assertTrue(read(f, tutor).isEmpty());
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void invalidOwnersAndNullFlagsCannotTouchReservedOrNeighborRows(boolean tutor) throws Exception {
        Fixture f = fixture(true, false);
        byte[] before = f.bytes.clone();
        Map<Species, boolean[]> invalid = new LinkedHashMap<>();
        for (int id : new int[]{-1, 0, 1440, 2000}) {
            boolean[] flags = new boolean[count(tutor) + 1]; Arrays.fill(flags, true);
            invalid.put(species(19, id), flags);
        }
        invalid.put(f.owners.getFirst(), null);
        write(f, tutor, invalid);
        assertArrayEquals(before, f.bytes);
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void malformedPointersRejectWithoutMutationAndTruncatedRowsRemainUnwritable(boolean tutor) throws Exception {
        for (int target : new int[]{-1, 0x200000 - width(tutor) + 1}) {
            Fixture f = fixture(true, false);
            pointer(f.bytes, tutor ? TUTOR_POINTER : TM_POINTER, target);
            byte[] before = f.bytes.clone();
            assertThrows(RomIOException.class, () -> read(f, tutor));
            assertThrows(RomIOException.class, () -> write(f, tutor, Map.of(f.owners.getFirst(), new boolean[count(tutor)+1])));
            assertArrayEquals(before, f.bytes);
        }
        Fixture f = fixture(true, false);
        pointer(f.bytes, tutor ? TUTOR_POINTER : TM_POINTER, f.bytes.length - width(tutor));
        byte[] before = f.bytes.clone();
        write(f, tutor, Map.of(f.owners.getLast(), new boolean[count(tutor)+1]));
        assertArrayEquals(before, f.bytes); // Existing row-bound guard rejects incomplete native owners.
    }

    @ParameterizedTest(name = "{displayName} [{index}] tutor={0}")
    @ValueSource(booleans = {false, true})
    void vanillaRetainsTreeMapDexOrderingCollisionAndNoopBytes(boolean tutor) throws Exception {
        Fixture f = fixture(false, false);
        Map<Species, boolean[]> map = read(f, tutor);
        assertInstanceOf(TreeMap.class, map);
        assertEquals(List.of(1, 19, 1060, 1439), identities(map));
        assertEquals(4, map.size()); // Same-Dex generic semantics intentionally unchanged.
        byte[] expected = f.bytes.clone();
        if (!tutor) for (Species s : map.keySet())
            expected[TM + s.getNumber() * 8 + 7] &= 3; // Existing Vanilla 58-bit writer clears padding bits.
        write(f, tutor, map);
        assertArrayEquals(expected, f.bytes);
    }

    @Test
    void realFullAndSanityConsumersPreserveDistinctPerOwnerRows() throws Exception {
        for (boolean tutor : new boolean[]{false, true}) {
            Fixture full = fixture(true, false); byte[] before = full.bytes.clone();
            invokeConsumer(full, 729, false, tutor ? "fullMoveTutorCompatibility" : "fullTMHMCompatibility");
            Map<Species, boolean[]> all = read(full, tutor);
            assertEquals(5, all.size());
            for (boolean[] flags : all.values()) for (int bit = 1; bit < flags.length; bit++) assertTrue(flags[bit]);
            assertOutsideOwnersUnchanged(full, before, tutor);

            Fixture sane = fixture(true, false); before = sane.bytes.clone();
            Map<Species, boolean[]> original = read(sane, tutor);
            invokeConsumer(sane, 729, false, tutor ? "ensureMoveTutorCompatSanity" : "ensureTMCompatSanity");
            Map<Species, boolean[]> actual = read(sane, tutor);
            for (int i = 0; i < sane.owners.size(); i++) {
                Species s = sane.owners.get(i); boolean[] expected = original.get(s).clone();
                expected[i + 1] = true;
                assertArrayEquals(expected, actual.get(s)); // Identity-indexed learnsets; no same-Dex substitution.
            }
            assertOutsideOwnersUnchanged(sane, before, tutor);
        }
    }

    @Test
    void realRandomConsumerMatchesExactSeedAndStableNativeOwnerOrder() throws Exception {
        for (long seed : SEEDS) for (boolean tutor : new boolean[]{false, true}) {
            Fixture f = fixture(true, false); byte[] before = f.bytes.clone();
            invokeConsumer(f, seed, false, tutor ? "randomizeMoveTutorCompatibility" : "randomizeTMHMCompatibility");
            Random expectedRng = new Random(seed);
            Map<Species, boolean[]> actual = read(f, tutor);
            for (Species s : f.owners) {
                boolean[] expected = new boolean[count(tutor) + 1];
                for (int bit = 1; bit < expected.length; bit++) expected[bit] = expectedRng.nextDouble() < 0.5;
                assertArrayEquals(expected, actual.get(s));
            }
            assertOutsideOwnersUnchanged(f, before, tutor);
        }
    }

    @Test
    void realFollowEvolutionsAndEvolutionSanityRetainIndependentSameDexOwners() throws Exception {
        for (boolean tutor : new boolean[]{false, true}) {
            Fixture f = fixture(true, true); byte[] before = f.bytes.clone();
            Map<Species, boolean[]> original = read(f, tutor);
            invokeConsumer(f, 729, false, tutor ? "ensureMoveTutorEvolutionSanity" : "ensureTMEvolutionSanity");
            Map<Species, boolean[]> sane = read(f, tutor);
            boolean[] expected = original.get(f.owners.get(3)).clone();
            boolean[] from = original.get(f.owners.get(2));
            for (int bit = 1; bit < expected.length; bit++) expected[bit] |= from[bit];
            assertArrayEquals(expected, sane.get(f.owners.get(3)));
            for (int i : new int[]{0, 1, 2, 4}) assertArrayEquals(original.get(f.owners.get(i)), sane.get(f.owners.get(i)));
            assertOutsideOwnersUnchanged(f, before, tutor);
            for (long seed : SEEDS) {
                Fixture random = fixture(true, true);
                byte[] untouched = random.bytes.clone();
                invokeConsumer(random, seed, true, tutor ? "randomizeMoveTutorCompatibility" : "randomizeTMHMCompatibility");
                Map<Species, boolean[]> flags = read(random, tutor);
                assertEquals(5, flags.size());
                Random expectedRng = new Random(seed);
                Map<Species, boolean[]> exact = new HashMap<>();
                // Same existing SpeciesSet root traversal; map replacement does not alter the helper.
                for (Species root : random.handler.getSpeciesSetInclFormes().filter(s -> s.getEvolutionsTo().isEmpty())) {
                    boolean[] bits = new boolean[count(tutor) + 1];
                    for (int bit = 1; bit < bits.length; bit++) bits[bit] = expectedRng.nextDouble() < 0.5;
                    exact.put(root, bits);
                }
                boolean[] inherited = exact.get(random.owners.get(2));
                boolean[] evolved = new boolean[count(tutor) + 1];
                for (int bit = 1; bit < evolved.length; bit++)
                    evolved[bit] = inherited[bit] || expectedRng.nextDouble() < 0.25;
                exact.put(random.owners.get(3), evolved);
                for (Species owner : random.owners) assertArrayEquals(exact.get(owner), flags.get(owner));
                assertOutsideOwnersUnchanged(random, untouched, tutor);
                from = flags.get(random.owners.get(2)); boolean[] to = flags.get(random.owners.get(3));
                for (int bit = 1; bit < from.length; bit++) if (from[bit]) assertTrue(to[bit]);
                assertFalse(Arrays.equals(flags.get(random.owners.get(1)), flags.get(random.owners.get(2))));
            }
        }
    }

    @Test
    void freshJvmSeedReplayIncludesBothNativeMapsAndFollowEvolutions() throws Exception {
        for (long seed : SEEDS) assertEquals(process(seed), process(seed));
    }

    // Public process entry point reads no file. Digest is solely synthetic in-memory output.
    public static void main(String[] args) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (boolean follow : new boolean[]{false, true}) {
            Fixture f = fixture(true, follow);
            invokeConsumer(f, Long.parseLong(args[0]), follow, "randomizeTMHMCompatibility");
            invokeConsumer(f, Long.parseLong(args[0]), follow, "randomizeMoveTutorCompatibility");
            digest.update(f.bytes);
        }
        System.out.println("SYNTHETIC_F02=" + HexFormat.of().formatHex(digest.digest()));
    }

    private static String process(long seed) throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader cl = Gen3CfruDpeTMHMTutorFormIdentityTest.class.getClassLoader(); cl != null; cl = cl.getParent())
            if (cl instanceof URLClassLoader urls) for (var url : urls.getURLs()) paths.add(new File(url.toURI()).getPath());
        String cp = String.join(File.pathSeparator, paths);
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", cp,
                Gen3CfruDpeTMHMTutorFormIdentityTest.class.getName(), Long.toString(seed)).redirectErrorStream(true).start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "fresh JVM timed out");
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), output);
        assertTrue(output.startsWith("SYNTHETIC_F02="), output);
        return output;
    }

    private static void invokeConsumer(Fixture f, long seed, boolean follow, String method) throws Exception {
        Class<?> settingsClass = Class.forName("com.uprfvx.random.Settings");
        Object settings = settingsClass.getConstructor().newInstance();
        settingsClass.getMethod("setTmsFollowEvolutions", boolean.class).invoke(settings, follow);
        settingsClass.getMethod("setTutorFollowEvolutions", boolean.class).invoke(settings, follow);
        Class<?> randomizer = Class.forName("com.uprfvx.random.randomizers.TMHMTutorCompatibilityRandomizer");
        Object consumer = randomizer.getConstructor(RomHandler.class, settingsClass, Random.class)
                .newInstance(f.handler, settings, new Random(seed));
        randomizer.getMethod(method).invoke(consumer);
    }

    private static void assertOutsideOwnersUnchanged(Fixture f, byte[] before, boolean tutor) {
        Set<Integer> owned = f.owners.stream().map(Species::getSpeciesSetIdentityNumber).collect(Collectors.toSet());
        for (int b = 0; b < before.length; b++) {
            int row = (b - base(tutor)) / width(tutor);
            boolean writable = b >= base(tutor) && b < base(tutor) + 1440 * width(tutor) && owned.contains(row);
            if (!writable && before[b] != f.bytes[b]) fail("changed unowned byte " + b);
        }
    }

    private static boolean pattern(int id, int bit) { return ((id * 17 + bit * 3) % 7) < 3; }
    private static int base(boolean tutor) { return tutor ? TUTOR : TM; }
    private static int width(boolean tutor) { return tutor ? 19 : 16; }
    private static int count(boolean tutor) { return width(tutor) * 8; }
    private static List<Integer> identities(Map<Species, boolean[]> map) {
        return map.keySet().stream().map(Species::getSpeciesSetIdentityNumber).toList();
    }
    private static Map<Species, boolean[]> read(Fixture f, boolean tutor) {
        return tutor ? f.handler.getMoveTutorCompatibility() : f.handler.getTMHMCompatibility();
    }
    private static void write(Fixture f, boolean tutor, Map<Species, boolean[]> map) {
        if (tutor) f.handler.setMoveTutorCompatibility(map); else f.handler.setTMHMCompatibility(map);
    }
    private record Fixture(SyntheticHandler handler, List<Species> owners, byte[] bytes) {}

    private static Fixture fixture(boolean nativeProfile, boolean evolution) throws Exception {
        List<Species> owners = List.of(species(1, 1), species(19, 19), species(19, 1059), species(20, 1060), species(1025, 1439));
        if (evolution) {
            Evolution e = new Evolution(owners.get(2), owners.get(3), EvolutionType.LEVEL, 20);
            owners.get(2).getEvolutionsFrom().add(e); owners.get(3).getEvolutionsTo().add(e);
        }
        byte[] memory = new byte[0x200000]; Arrays.fill(memory, (byte) 0x5A);
        pointer(memory, TM_POINTER, TM); pointer(memory, TUTOR_POINTER, TUTOR);
        Gen3RomEntry entry = Gen3RomEntry.READER.readEntriesFromFile("gen3_offsets.ini").stream()
                .filter(e -> "Fire Red (U) 1.0".equals(e.getName())).map(Gen3RomEntry::new).findFirst().orElseThrow();
        entry.putIntValue("PokemonCount", 1440); entry.putIntValue("PokemonTMHMCompat", TM);
        entry.putIntValue("MoveTutorCompatibility", TUTOR); entry.putIntValue("MoveTutorMoves", 152);
        int[] dexToInternal = new int[1600];
        for (int i = 0; i < dexToInternal.length; i++) dexToInternal[i] = i;
        for (boolean tutor : new boolean[]{false, true}) for (Species s : owners) {
            int id = nativeProfile ? s.getSpeciesSetIdentityNumber() : s.getNumber();
            int width = nativeProfile ? width(tutor) : tutor ? 19 : 8;
            for (int b = 0; b < width; b++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) if (pattern(id, b * 8 + bit + 1)) value |= 1 << bit;
                memory[base(tutor) + id * width + b] = (byte) value;
            }
        }
        SyntheticHandler handler = new SyntheticHandler(owners);
        set(handler, "romEntry", entry); set(handler, "rom", memory); set(handler, "isRomHack", nativeProfile);
        set(handler, "useCfruDpeGen9SpeciesCount", nativeProfile);
        List<Species> list = new ArrayList<>(); list.add(null); list.addAll(owners);
        set(handler, "speciesList", list); set(handler, "numRealPokemon", owners.size());
        set(handler, "pokedexToInternal", dexToInternal);
        return new Fixture(handler, owners, memory);
    }

    private static Species species(int dex, int identity) {
        Species s = new Species(dex); s.setSpeciesSetIdentityNumber(identity); s.setName("Synthetic" + identity);
        s.setPrimaryType(Type.NORMAL); return s;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try { Field field = c.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void pointer(byte[] bytes, int at, int target) {
        int value = target < 0 ? 0 : target + 0x08000000;
        for (int i = 0; i < 4; i++) bytes[at + i] = (byte) (value >> (i * 8));
    }
    private static class SyntheticHandler extends Gen3RomHandler {
        private final List<Species> owners;
        private final List<Move> moveData = new ArrayList<>();
        SyntheticHandler(List<Species> owners) {
            this.owners = owners; moveData.add(null);
            for (int id = 1; id <= 152; id++) { Move m = new Move(); m.number = id; m.type = Type.NORMAL; moveData.add(m); }
        }
        @Override public SpeciesSet getSpeciesSetInclFormes() { return new SpeciesSet(owners); }
        @Override public List<Integer> getEarlyRequiredHMMoves() { return List.of(); }
        @Override public List<Move> getMoves() { return moveData; }
        @Override public List<Integer> getTMMoves() { return java.util.stream.IntStream.rangeClosed(1, 120).boxed().toList(); }
        @Override public List<Integer> getHMMoves() { return java.util.stream.IntStream.rangeClosed(121, 128).boxed().toList(); }
        @Override public List<Integer> getMoveTutorMoves() { return java.util.stream.IntStream.rangeClosed(1, 152).boxed().toList(); }
        @Override public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
            Map<Integer, List<MoveLearnt>> result = new LinkedHashMap<>();
            for (int i = 0; i < owners.size(); i++) result.put(owners.get(i).getSpeciesSetIdentityNumber(), List.of(new MoveLearnt(i+1, 1)));
            return result;
        }
    }
}
