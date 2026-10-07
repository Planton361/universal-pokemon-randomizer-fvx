package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.random.RandomSource;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.graphics.palettes.*;
import com.uprfvx.romio.romhandlers.RomHandler;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.util.*;

/** Public synthetic models only. Fresh-process palette initialization witness. */
public class PaletteReplayProcessProbe {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        if (args.length > 1 && args[1].equals("controls")) {
            for (boolean data : new boolean[]{true, false}) {
                byte[] bytes = new CombinedReplayFixture().run(seed, data, !data, 0);
                System.out.println("CONTROL=" + (data ? "DATA" : "WORLD") + " OUTPUT=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
            return;
        }
        if (args.length > 1 && args[1].equals("combined")) {
            for (int additions = 0; additions < 8; additions++) {
                byte[] bytes = new CombinedReplayFixture().run(seed, true, true, additions);
                System.out.println("COMPOSITION=" + additions + " OUTPUT=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
            return;
        }
        var digest = MessageDigest.getInstance("SHA-256");
        var source = new RandomSource();
        source.seed(seed);
        for (int repetition = 0; repetition < 100; repetition++) {
            SpeciesSet species = new SpeciesSet();
            for (int id = 1; id <= 151; id++) {
                Species sp = new Species(id);
                sp.setPrimaryType(Type.NORMAL);
                Palette palette = new Palette();
                for (int i = 0; i < palette.size(); i++) palette.set(i, new Color(i * 8, i * 8, i * 8));
                sp.setNormalPalette(palette);
                species.add(sp);
            }
            RomHandler handler = (RomHandler) Proxy.newProxyInstance(RomHandler.class.getClassLoader(),
                new Class[]{RomHandler.class}, (proxy, method, parameters) -> switch (method.getName()) {
                    case "getPaletteFilesID" -> "FRLG";
                    case "getSpeciesSetInclFormes" -> species;
                    case "getRestrictedSpeciesService", "getTypeService" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            Settings settings = new Settings();
            settings.setPokemonPalettesMod(Settings.PokemonPalettesMod.RANDOM);
            settings.setPokemonPalettesFollowEvolutions(true);
            new Gen3to5PaletteRandomizer(handler, settings, source.getCosmetic()).randomizePokemonPalettes();
            for (int id = 1; id <= 151; id++) {
                final int number = id;
                digest.update(species.stream().filter(sp -> sp.getNumber() == number).findFirst().orElseThrow()
                    .getNormalPalette().toBytes());
            }
        }
        System.out.println("SYNTHETIC_PALETTE=" + HexFormat.of().formatHex(digest.digest()));
    }
}
