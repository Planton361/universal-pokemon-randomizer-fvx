package com.uprfvx.random.cli;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.ItemMechanicCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.SpecialFormCategory;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.romhandlers.Gen3RomHandler;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romio.RomOpener;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.SpecialFormExclusionOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public static int invokeEligible(String[] args) {
        String inputRomPath = null;
        String outputDirPath = null;
        String settingsFilePath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--input-rom":
                    if (i + 1 >= args.length) {
                        return usageError("Missing value for input ROM argument.");
                    }
                    inputRomPath = args[++i];
                    break;
                case "-s":
                case "--settings-file":
                    if (i + 1 >= args.length) {
                        return usageError("Missing value for settings/profile argument.");
                    }
                    settingsFilePath = args[++i];
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
                    printEligibleUsage();
                    return 0;
                default:
                    return usageError("Unknown argument.");
            }
        }

        if (inputRomPath == null) {
            return usageError("Missing required argument: --input-rom");
        }
        if (settingsFilePath == null) {
            return usageError("Missing required argument: --settings-file");
        }
        if (outputDirPath == null) {
            return usageError("Missing required argument: --output-dir");
        }

        RomOpener.Results results = romOpener.openRomFile(new File(inputRomPath));
        if (!results.wasOpeningSuccessful()) {
            System.out.println("ERROR: Could not load input ROM for eligibility export: " + results.getFailType());
            return 1;
        }

        try {
            Settings settings = readSettings(Path.of(settingsFilePath));
            RomHandler romHandler = results.getRomHandler();
            settings.tweakForRom(romHandler);
            setupSpeciesRestrictions(romHandler, settings);
            exportEligibility(romHandler, settings, Path.of(outputDirPath));
            System.out.println("Wrote sanitized eligibility manifests.");
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("ERROR: Could not write sanitized eligibility manifests.");
            return 1;
        }
    }

    static void export(RomHandler romHandler, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        writeSpeciesManifest(romHandler, outputDir.resolve("species_loaded.tsv"));
        writeItemsManifest(romHandler, outputDir.resolve("items_loaded.tsv"), false);
        writeItemsManifest(romHandler, outputDir.resolve("tms_hms_loaded.tsv"), true);
    }

    static void exportEligibility(RomHandler romHandler, Settings settings, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        writeSpeciesEligibilityManifest(romHandler, settings, outputDir.resolve("species_eligible.tsv"));
    }

    private static Settings readSettings(Path path) throws IOException {
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            return Settings.readFromFileFormat(in);
        }
    }

    private static void setupSpeciesRestrictions(RomHandler romHandler, Settings settings) {
        romHandler.getRestrictedSpeciesService().setRestrictions(
                settings.isLimitPokemon() ? settings.getCurrentRestrictions() : null,
                new SpecialFormExclusionOptions(
                        settings.isAllowMegaForms(),
                        settings.isAllowGigantamaxForms(),
                        settings.isAllowRegionalFormsAcrossGenLimit()));
        if (settings.isLimitPokemon()) {
            romHandler.removeEvosForPokemonPool();
        }
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

    private static void writeSpeciesEligibilityManifest(RomHandler romHandler, Settings settings, Path outputPath)
            throws IOException {
        SpeciesSet wildEligible = buildWildEligibleSpecies(romHandler, settings);
        SpeciesSet trainerEligible = buildTrainerEligibleSpecies(romHandler, settings);
        SpeciesSet starterEligible = buildStarterEligibleSpecies(romHandler, settings);
        SpeciesSet staticEligible = buildStaticEligibleSpecies(romHandler, settings);

        List<Species> species = romHandler.getSpeciesSetInclFormes().stream()
                .filter(pk -> pk != null && pk.getFullName() != null && !pk.getFullName().isBlank())
                .sorted(Comparator.comparingInt(LoadedManifestExporter::speciesIdentity))
                .collect(Collectors.toList());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("canonical_key\tdisplay_name\tsource_or_internal_id\tform_family\tloaded\t"
                    + "eligible_wild\teligible_trainer\teligible_starter\teligible_static\teligible_any\t"
                    + "exclusion_reason_wild\texclusion_reason_trainer\texclusion_reason_starter\t"
                    + "exclusion_reason_static\tsettings_profile_label\tconfidence\n");
            for (Species pk : species) {
                boolean wild = wildEligible.contains(pk);
                boolean trainer = trainerEligible.contains(pk);
                boolean starter = starterEligible.contains(pk);
                boolean staticPokemon = staticEligible.contains(pk);
                writer.write(tsv(
                        canonicalize(pk.getFullName()),
                        pk.getFullName(),
                        String.valueOf(speciesIdentity(pk)),
                        speciesFormFamily(pk),
                        "yes",
                        yesNo(wild),
                        yesNo(trainer),
                        yesNo(starter),
                        yesNo(staticPokemon),
                        yesNo(wild || trainer || starter || staticPokemon),
                        exclusionReason(settings.isRandomizeWildPokemon(), wild),
                        exclusionReason(isTrainerPoolEnabled(settings), trainer),
                        exclusionReason(settings.getStartersMod() != Settings.StartersMod.UNCHANGED, starter),
                        exclusionReason(settings.getStaticPokemonMod() != Settings.StaticPokemonMod.UNCHANGED, staticPokemon),
                        "provided_settings_profile",
                        "Medium"
                ));
            }
        }
    }

    private static SpeciesSet buildWildEligibleSpecies(RomHandler romHandler, Settings settings) {
        if (!settings.isRandomizeWildPokemon()) {
            return new SpeciesSet();
        }
        RestrictedSpeciesService rSpecService = romHandler.getRestrictedSpeciesService();
        SpeciesSet eligible = new SpeciesSet(rSpecService.getSpecies(
                settings.isBlockWildLegendaries(), settings.isAllowWildAltFormes(), false));
        SpeciesSet banned = new SpeciesSet(romHandler.getBannedForWildEncounters());
        banned.addAll(rSpecService.getBannedFormesForPlayerPokemon());
        if (settings.getAbilitiesMod() != Settings.AbilitiesMod.RANDOMIZE) {
            banned.addAll(rSpecService.getAbilityDependentFormes());
        }
        if (settings.isBanIrregularAltFormes()) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        eligible.removeAll(banned);
        if (settings.getWildPokemonEvolutionMod() == Settings.WildPokemonEvolutionMod.BASIC_ONLY
                && !settings.isKeepWildEvolutionFamilies()) {
            eligible = eligible.filterBasic(false);
        }
        removeUnusableExtendedBpreSpecies(romHandler, eligible);
        return eligible;
    }

    private static SpeciesSet buildTrainerEligibleSpecies(RomHandler romHandler, Settings settings) {
        if (!isTrainerPoolEnabled(settings)) {
            return new SpeciesSet();
        }
        RestrictedSpeciesService rSpecService = romHandler.getRestrictedSpeciesService();
        SpeciesSet eligible = new SpeciesSet(rSpecService.getSpecies(
                settings.isTrainersBlockLegendaries(), settings.isAllowTrainerAlternateFormes(), false));
        if (settings.isTrainersUseLocalPokemon()) {
            SpeciesSet localWithRelatives =
                    romHandler.getMainGameWildPokemonSpecies(settings.isUseTimeBasedEncounters())
                            .buildFullFamilies(false);
            eligible.retainAll(localWithRelatives);
        }
        SpeciesSet banned = new SpeciesSet(romHandler.getBannedFormesForTrainerPokemon());
        if (settings.getAbilitiesMod() != Settings.AbilitiesMod.RANDOMIZE) {
            banned.addAll(rSpecService.getAbilityDependentFormes());
        }
        if (settings.isBanIrregularAltFormes()) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        eligible.removeAll(banned);
        eligible.removeIf(pk -> pk.getBST() == 0
                || (pk.getAbility1() == 0 && pk.getAbility2() == 0 && pk.getAbility3() == 0));
        removeUnusableExtendedBpreSpecies(romHandler, eligible);
        if (hasTrainerTypeOrDiversityFilter(settings)) {
            eligible.removeIf(pk -> pk.getPrimaryType(false) == null);
        }
        return eligible;
    }

    private static SpeciesSet buildStarterEligibleSpecies(RomHandler romHandler, Settings settings) {
        if (settings.getStartersMod() == Settings.StartersMod.UNCHANGED) {
            return new SpeciesSet();
        }
        RestrictedSpeciesService rSpecService = romHandler.getRestrictedSpeciesService();
        SpeciesSet eligible;
        if (settings.isAllowStarterAltFormes()) {
            eligible = new SpeciesSet(settings.isStartersNoLegendaries()
                    ? rSpecService.getNonLegendaries(true) : rSpecService.getAll(true));
            if (settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED) {
                eligible.removeAll(rSpecService.getAbilityDependentFormes());
            }
            if (settings.isBanIrregularAltFormes()) {
                eligible.removeAll(romHandler.getIrregularFormes());
            }
            eligible.removeIf(Species::isCosmeticReplacement);
            eligible.removeIf(Species::isActuallyCosmetic);
        } else {
            eligible = new SpeciesSet(settings.isStartersNoLegendaries()
                    ? rSpecService.getNonLegendaries(false) : rSpecService.getAll(false));
        }
        if (settings.isStartersNoDualTypes()) {
            eligible.removeIf(pk -> pk.hasSecondaryType(false));
        }
        if (settings.getStartersMod() == Settings.StartersMod.RANDOM_BASIC
                || settings.getStartersMod() == Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS) {
            eligible = eligible.filterBasic(false);
        }
        if (settings.getStartersMod() == Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS) {
            eligible.removeIf(pk -> pk.getStagesAfter(false) < 2);
        }
        int bstMin = settings.getStartersBSTMinimum();
        int bstMax = settings.getStartersBSTMaximum() == 0 ? 1530 : settings.getStartersBSTMaximum();
        if (bstMin != 0 || bstMax != 1530) {
            eligible.removeIf(pk -> pk.getBSTForPowerLevels() < bstMin || pk.getBSTForPowerLevels() > bstMax);
        }
        return eligible;
    }

    private static SpeciesSet buildStaticEligibleSpecies(RomHandler romHandler, Settings settings) {
        if (settings.getStaticPokemonMod() == Settings.StaticPokemonMod.UNCHANGED) {
            return new SpeciesSet();
        }
        RestrictedSpeciesService rSpecService = romHandler.getRestrictedSpeciesService();
        boolean allowAltFormes = settings.isAllowStaticAltFormes();
        SpeciesSet eligible = new SpeciesSet();
        eligible.addAll(rSpecService.getAll(allowAltFormes));
        if (allowAltFormes) {
            eligible.removeIf(Species::isCosmeticReplacement);
        }
        SpeciesSet banned = new SpeciesSet(romHandler.getBannedForStaticPokemon());
        banned.addAll(rSpecService.getBannedFormesForPlayerPokemon());
        if (settings.getAbilitiesMod() != Settings.AbilitiesMod.RANDOMIZE) {
            banned.addAll(rSpecService.getAbilityDependentFormes());
        }
        if (settings.isBanIrregularAltFormes()) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        eligible.removeAll(banned);
        return eligible;
    }

    private static boolean isTrainerPoolEnabled(Settings settings) {
        return settings.getTrainersMod() != Settings.TrainersMod.UNCHANGED
                || settings.getAdditionalBossTrainerPokemon() > 0
                || settings.getAdditionalImportantTrainerPokemon() > 0
                || settings.getAdditionalRegularTrainerPokemon() > 0;
    }

    private static boolean hasTrainerTypeOrDiversityFilter(Settings settings) {
        return settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED
                || settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED_ELITE4_GYMS
                || settings.getTrainersMod() == Settings.TrainersMod.KEEP_THEMED
                || settings.getTrainersMod() == Settings.TrainersMod.KEEP_THEME_OR_PRIMARY
                || settings.isDiverseTypesForBossTrainers()
                || settings.isDiverseTypesForImportantTrainers()
                || settings.isDiverseTypesForRegularTrainers();
    }

    private static void removeUnusableExtendedBpreSpecies(RomHandler romHandler, SpeciesSet eligible) {
        if (!(romHandler instanceof Gen3RomHandler gen3RomHandler)
                || !gen3RomHandler.hasExtendedBpreHackSpeciesPool()) {
            return;
        }
        Map<Integer, List<MoveLearnt>> movesets = gen3RomHandler.getMovesLearnt();
        eligible.removeIf(species -> !gen3RomHandler.hasUsableCfruDpeRandomPoolSpeciesAssets(species, movesets));
    }

    private static String exclusionReason(boolean featureEnabled, boolean eligible) {
        if (eligible) {
            return "";
        }
        return featureEnabled ? "excluded_by_broad_settings_or_filters" : "feature_disabled";
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

    private static void printEligibleUsage() {
        System.out.println("Usage: java [-Xmx4096M] -jar UPR-FVX.jar eligible-manifest -i <path to source ROM> -s <settings.rnqs> -o <output directory>");
        System.out.println("Writes sanitized species_eligible.tsv after ROM load and settings/profile restriction setup.");
        System.out.println("The TSV files do not include ROM paths, output-ROM paths, hashes, offsets, or raw logs.");
    }
}
