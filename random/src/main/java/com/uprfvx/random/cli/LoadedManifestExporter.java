package com.uprfvx.random.cli;

import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.ItemMechanicCategory;
import com.uprfvx.romio.gamedata.SpecialFormCategory;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LoadedManifestExporter {

    private static final RomOpener romOpener = new RomOpener();
    private static final Pattern NON_ASCII_MARK = Pattern.compile("\\p{M}+");
    private static final Pattern NON_KEY_CHARS = Pattern.compile("[^a-z0-9]+");

    private LoadedManifestExporter() {
    }

    public static int invoke(String[] args) {
        String inputRomPath = null;
        String outputDirPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--input-rom":
                    if (i + 1 >= args.length) {
                        return usageError("Missing value for input ROM argument.");
                    }
                    inputRomPath = args[++i];
                    break;
                case "-o":
                case "--output-dir":
                    if (i + 1 >= args.length) {
                        return usageError("Missing value for output directory argument.");
                    }
                    outputDirPath = args[++i];
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    return 0;
                default:
                    return usageError("Unknown argument.");
            }
        }

        if (inputRomPath == null) {
            return usageError("Missing required argument: --input-rom");
        }
        if (outputDirPath == null) {
            return usageError("Missing required argument: --output-dir");
        }

        RomOpener.Results results = romOpener.openRomFile(new File(inputRomPath));
        if (!results.wasOpeningSuccessful()) {
            System.out.println("ERROR: Could not load input ROM for manifest export: " + results.getFailType());
            return 1;
        }

        try {
            export(results.getRomHandler(), Path.of(outputDirPath));
            System.out.println("Wrote sanitized loaded manifests.");
            return 0;
        } catch (IOException e) {
            System.out.println("ERROR: Could not write sanitized loaded manifests.");
            return 1;
        }
    }

    static void export(RomHandler romHandler, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        writeSpeciesManifest(romHandler, outputDir.resolve("species_loaded.tsv"));
        writeItemsManifest(romHandler, outputDir.resolve("items_loaded.tsv"), false);
        writeItemsManifest(romHandler, outputDir.resolve("tms_hms_loaded.tsv"), true);
    }

    private static void writeSpeciesManifest(RomHandler romHandler, Path outputPath) throws IOException {
        List<Species> species = romHandler.getSpeciesSetInclFormes().stream()
                .filter(pk -> pk != null && pk.getFullName() != null && !pk.getFullName().isBlank())
                .sorted(Comparator.comparingInt(LoadedManifestExporter::speciesIdentity))
                .collect(Collectors.toList());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("canonical_key\tdisplay_name\tsource_internal_id\tform_family\tis_loaded\tallowed\tbanned\tmechanic_gated\n");
            for (Species pk : species) {
                writer.write(tsv(
                        canonicalize(pk.getFullName()),
                        pk.getFullName(),
                        String.valueOf(speciesIdentity(pk)),
                        speciesFormFamily(pk),
                        "yes",
                        "yes",
                        "",
                        ""
                ));
            }
        }
    }

    private static void writeItemsManifest(RomHandler romHandler, Path outputPath, boolean tmHmOnly) throws IOException {
        Set<Item> allowedItems = romHandler.getAllowedItems();
        Set<Item> nonBadItems = romHandler.getNonBadItems();
        List<Item> items = romHandler.getItems().stream()
                .filter(item -> item != null && item.getName() != null && !item.getName().isBlank())
                .filter(item -> !tmHmOnly || isTmHmItem(item))
                .sorted(Comparator.comparingInt(Item::getId))
                .collect(Collectors.toList());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("canonical_key\tdisplay_name\tsource_internal_id\titem_family\tis_loaded\tallowed\tbanned\tmechanic_gated\tis_tm\tis_hm\n");
            for (Item item : items) {
                writer.write(tsv(
                        canonicalize(tmHmLabel(item)),
                        item.getName(),
                        String.valueOf(item.getId()),
                        itemFamily(item),
                        "yes",
                        yesNo(allowedItems.contains(item)),
                        yesNo(!nonBadItems.contains(item)),
                        yesNo(!item.getMechanicCategories().isEmpty()),
                        yesNo(item.isTM()),
                        yesNo(isHmItem(item))
                ));
            }
        }
    }

    private static int speciesIdentity(Species species) {
        return species.getSpeciesSetIdentityNumber() > 0 ? species.getSpeciesSetIdentityNumber() : species.getNumber();
    }

    private static String speciesFormFamily(Species species) {
        String normalized = compact(species.getFullName());
        for (String family : List.of("unown", "vivillon", "alcremie", "minior", "rotom", "arceus", "silvally", "deoxys")) {
            if (normalized.startsWith(family)) {
                return capitalize(family);
            }
        }
        if (species.getSpecialFormCategories().contains(SpecialFormCategory.REGIONAL)
                || species.getSpecialFormCategories().contains(SpecialFormCategory.REGIONAL_BRANCH)) {
            return "Regional Forms";
        }
        if (species.getSpecialFormCategories().contains(SpecialFormCategory.MEGA)) {
            return "Mega";
        }
        if (species.getSpecialFormCategories().contains(SpecialFormCategory.GIGANTAMAX)) {
            return "GMax";
        }
        return "";
    }

    private static String itemFamily(Item item) {
        String normalized = compact(item.getName());
        if (item.isTM()) {
            return "TM";
        }
        if (isHmItem(item)) {
            return "HM";
        }
        if (normalized.endsWith("berry")) {
            return "Berry";
        }
        if (normalized.endsWith("gem")) {
            return "Gem";
        }
        if (normalized.endsWith("ball")) {
            return "Ball";
        }
        if (normalized.endsWith("memory") || item.hasMechanicCategory(ItemMechanicCategory.SILVALLY_MEMORY)) {
            return "Memory";
        }
        if (normalized.endsWith("plate") || item.hasMechanicCategory(ItemMechanicCategory.ARCEUS_PLATE)) {
            return "Plate";
        }
        if (normalized.endsWith("drive") || item.hasMechanicCategory(ItemMechanicCategory.GENESECT_DRIVE)) {
            return "Drive";
        }
        if (normalized.endsWith("nectar") || item.hasMechanicCategory(ItemMechanicCategory.NECTAR_FORM_CHANGE)) {
            return "Nectar";
        }
        return "";
    }

    private static boolean isTmHmItem(Item item) {
        return item.isTM() || isHmItem(item);
    }

    private static boolean isHmItem(Item item) {
        return canonicalize(item.getName()).matches("hm\\d{1,3}.*");
    }

    private static String tmHmLabel(Item item) {
        String canonical = canonicalize(item.getName());
        if (canonical.matches("(tm|hm)\\d{1,3}.*")) {
            return canonical.replaceFirst("^((?:tm|hm)\\d{1,3}).*$", "$1").toUpperCase(Locale.ROOT);
        }
        return item.getName();
    }

    private static String canonicalize(String value) {
        String ascii = foldAscii(value).toLowerCase(Locale.ROOT);
        return NON_KEY_CHARS.matcher(ascii).replaceAll("_").replaceAll("^_+|_+$", "");
    }

    private static String compact(String value) {
        return canonicalize(value).replace("_", "");
    }

    private static String foldAscii(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('`', '\'')
                .replace('´', '\'')
                .replace('‐', '-')
                .replace('‑', '-')
                .replace('–', '-')
                .replace('—', '-');
        return NON_ASCII_MARK.matcher(normalized).replaceAll("");
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String tsv(String... values) {
        return List.of(values).stream().map(LoadedManifestExporter::escapeTsv)
                .collect(Collectors.joining("\t")) + "\n";
    }

    private static String escapeTsv(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static int usageError(String text) {
        System.out.println("ERROR: " + text);
        printUsage();
        return 1;
    }

    private static void printUsage() {
        System.out.println("Usage: java [-Xmx4096M] -jar UPR-FVX.jar loaded-manifest -i <path to source ROM> -o <output directory>");
        System.out.println("Writes sanitized species_loaded.tsv, items_loaded.tsv, and tms_hms_loaded.tsv.");
        System.out.println("The TSV files do not include ROM paths, output-ROM paths, hashes, offsets, or raw logs.");
    }
}
