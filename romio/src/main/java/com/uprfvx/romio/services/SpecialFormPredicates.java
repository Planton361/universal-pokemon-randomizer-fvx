package com.uprfvx.romio.services;

import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Species;

public final class SpecialFormPredicates {

    private SpecialFormPredicates() {
    }

    /**
     * Exact pilot identity policy, applied only by the detected CFRU/DPE handler.
     * IDs: DPE d887185 include/species.h; runtime: CFRU e68a701
     * form_change.c, end_turn.c, ability_battle_effects.c, mega.c, dynamax.c,
     * config.h and Tables/pokemon_tables.c. Ordinary selection must not rely on
     * generic alt-form flags: DPE stores these rows as distinct base identities.
     */
    public static CfruDpePoolCategory cfruDpePoolCategory(Species species) {
        if (species == null) {
            return CfruDpePoolCategory.INVALID;
        }
        int id = species.getSpeciesSetIdentityNumber();
        if (id <= 0 || id > 0x59F || between(id, 0xFC, 0x114) || id == 0x19C) {
            return CfruDpePoolCategory.INVALID;
        }
        // Unimplemented rows: missing DPE Base_Stats or Front_Pic/Palette entries.
        if (id == 0x2C2 || id == 0x343 || id == 0x344
                || id == 0x40C || id == 0x40E || id == 0x4C2 || id == 0x4CC) {
            return CfruDpePoolCategory.MISSING_SOURCE_DATA;
        }
        if (between(id, 0x365, 0x38C) || between(id, 0x38F, 0x396)) {
            return CfruDpePoolCategory.MEGA;
        }
        if (between(id, 0x38D, 0x38E)) {
            return CfruDpePoolCategory.PRIMAL;
        }
        if (between(id, 0x4EC, 0x50D)) {
            return CfruDpePoolCategory.GIGANTAMAX;
        }
        if (between(id, 0x592, 0x595) || between(id, 0x59D, 0x59E)) {
            return CfruDpePoolCategory.TERA_TRANSFORMATION;
        }
        if (id == 0x4B7) {
            return CfruDpePoolCategory.ETERNAMAX;
        }
        // Minior cores are persisted by TryFormRevert/GetMiniorCoreSpecies;
        // its shield row is the temporary state in this exact engine.
        if (id == 0x2E1 || id == 0x2EA || id == 0x2EF || id == 0x341
                || id == 0x346 || id == 0x347 || id == 0x3DF || id == 0x417
                || id == 0x430 || id == 0x439 || between(id, 0x4A7, 0x4A8)
                || id == 0x4B2 || between(id, 0x4B4, 0x4B6)
                || id == 0x4CF || id == 0x54E) {
            return CfruDpePoolCategory.BATTLE_TRANSFORMATION;
        }
        // No selection path guarantees a plate/memory/drive/orb, Secret Sword,
        // time of day or PC persistence. PLA_HELD_ORIGIN_ORBS is OFF at this pin,
        // so Dialga/Palkia Origin remain persistent selectable forms.
        if (between(id, 0x2CE, 0x2DF) || between(id, 0x2EB, 0x2EE)
                || id == 0x2F5 || id == 0x33D || id == 0x342
                || between(id, 0x418, 0x428)) {
            return CfruDpePoolCategory.CONDITION_DEPENDENT;
        }
        if (between(id, 0x3FC, 0x40F) || between(id, 0x4BC, 0x4E2)
                || between(id, 0x581, 0x584)) {
            return CfruDpePoolCategory.REGIONAL;
        }
        if (between(id, 0x19D, 0x1B7) || between(id, 0x2BF, 0x2F5)
                || id == 0x33F || id == 0x340 || id == 0x345
                || between(id, 0x348, 0x364) || between(id, 0x397, 0x3AA)
                || between(id, 0x410, 0x416) || between(id, 0x429, 0x431)
                || between(id, 0x437, 0x43A) || between(id, 0x43D, 0x44D)
                || between(id, 0x4A9, 0x4B1) || id == 0x4B3
                || between(id, 0x4B8, 0x4BB) || id == 0x4E7 || id == 0x4EB
                || id == 0x519 || id == 0x523 || between(id, 0x52A, 0x52C)
                || between(id, 0x55D, 0x55E) || id == 0x563 || id == 0x575
                || id == 0x585 || id == 0x588 || id == 0x58A
                || between(id, 0x58F, 0x591)) {
            return CfruDpePoolCategory.PERSISTENT_ALTERNATE;
        }
        return CfruDpePoolCategory.ORDINARY;
    }

    private static boolean between(int id, int first, int last) {
        return id >= first && id <= last;
    }

    /** Complete ordinary-pool decision, including an explicit reason on YES and NO. */
    public record CfruDpePoolEligibility(boolean eligible, String reason) {
    }

    public enum CfruDpePoolCategory {
        ORDINARY(true, "ordinary species"),
        REGIONAL(true, "regional form"),
        PERSISTENT_ALTERNATE(true, "persistent alternate form"),
        INVALID(false, "null/egg/unused/invalid species identity"),
        MISSING_SOURCE_DATA(false, "missing required source data/assets"),
        MEGA(false, "Mega transformation"),
        PRIMAL(false, "Primal transformation"),
        GIGANTAMAX(false, "G-Max transformation"),
        ETERNAMAX(false, "Eternamax transformation"),
        TERA_TRANSFORMATION(false, "Tera transformation"),
        BATTLE_TRANSFORMATION(false, "temporary battle/ability transformation"),
        CONDITION_DEPENDENT(false, "item/move/time/PC-dependent form");

        private final boolean eligible;
        private final String reason;

        CfruDpePoolCategory(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }

        public boolean eligible() { return eligible; }
        public String reason() { return reason; }
    }

    public static boolean isSpeciesAllowed(Species species, GenRestrictions restrictions,
                                           SpecialFormExclusionOptions options) {
        SpecialFormExclusionOptions effectiveOptions = options == null ? SpecialFormExclusionOptions.defaults() : options;
        return hasUsableSpeciesIdentity(species)
                && isAllowedBySpecialFormOptions(species, effectiveOptions)
                && isAllowedByGeneration(species, restrictions, effectiveOptions);
    }

    public static boolean hasUsableSpeciesIdentity(Species species) {
        return species != null && species.getSpeciesSetIdentityNumber() > 0;
    }

    public static boolean isAllowedBySpecialFormOptions(Species species, SpecialFormExclusionOptions options) {
        if (species == null) {
            return false;
        }
        SpecialFormExclusionOptions effectiveOptions = options == null ? SpecialFormExclusionOptions.defaults() : options;
        if (species.isMegaForm() && !effectiveOptions.isIncludeMegaForms()) {
            return false;
        }
        if (species.isGigantamaxForm() && !effectiveOptions.isIncludeGigantamaxForms()) {
            return false;
        }
        if (species.isIrregularSpecialForm() && !effectiveOptions.isIncludeIrregularSpecialForms()) {
            return false;
        }
        return true;
    }

    public static boolean isAllowedByGeneration(Species species, GenRestrictions restrictions,
                                                SpecialFormExclusionOptions options) {
        if (species == null) {
            return false;
        }
        if (restrictions == null) {
            return true;
        }
        int generation = effectiveGenerationForDirectLimit(species, options);
        return generation > 0 && restrictions.isGenAllowed(generation);
    }

    public static boolean isAllowedAfterEvolutionaryRelativeExpansion(Species species, GenRestrictions restrictions,
                                                                      SpecialFormExclusionOptions options) {
        SpecialFormExclusionOptions effectiveOptions = options == null ? SpecialFormExclusionOptions.defaults() : options;
        if (!hasUsableSpeciesIdentity(species) || !isAllowedBySpecialFormOptions(species, effectiveOptions)) {
            return false;
        }
        return !species.dependsOnRegionalFormForEligibility()
                || isAllowedByGeneration(species, restrictions, effectiveOptions);
    }

    public static int effectiveGenerationForDirectLimit(Species species, SpecialFormExclusionOptions options) {
        if (species == null) {
            return -1;
        }
        SpecialFormExclusionOptions effectiveOptions = options == null ? SpecialFormExclusionOptions.defaults() : options;
        if (species.dependsOnRegionalFormForEligibility()) {
            if (effectiveOptions.isAllowRegionalFormsAcrossGenLimit()) {
                return species.getRegionalBaseFamilyGeneration();
            }
            return species.getRegionalFormGeneration();
        }
        return species.getGeneration();
    }
}
