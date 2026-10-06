package com.uprfvx.romio.services;

import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Immutable semantic policy in the active handler's encoded ability ID space. */
public record AbilityRandomizationPolicy(
        List<Integer> candidateIds, int wonderGuard, Set<Integer> trapping,
        Set<Integer> negative, Set<Integer> bad, Set<Integer> doubleBattle,
        Set<Integer> useless, Set<Integer> duplicateExclusions,
        Map<Integer, List<Integer>> variations) {
    public AbilityRandomizationPolicy {
        candidateIds = List.copyOf(candidateIds);
        trapping = Set.copyOf(trapping);
        negative = Set.copyOf(negative);
        bad = Set.copyOf(bad);
        doubleBattle = Set.copyOf(doubleBattle);
        useless = Set.copyOf(useless);
        duplicateExclusions = Set.copyOf(duplicateExclusions);
        variations = variations.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /** Preserve legacy candidate ordering, rejection sampling, bans and variations. */
    public static AbilityRandomizationPolicy legacy(RomHandler handler) {
        List<Integer> duplicates = new ArrayList<>(GlobalConstants.duplicateAbilities);
        if (handler.generationOfPokemon() == 3) duplicates.add(Gen3Constants.airLockIndex);
        return new AbilityRandomizationPolicy(
                IntStream.rangeClosed(1, handler.highestAbilityIndex()).boxed().toList(),
                AbilityIDs.wonderGuard, Set.copyOf(GlobalConstants.battleTrappingAbilities),
                Set.copyOf(GlobalConstants.negativeAbilities), Set.copyOf(GlobalConstants.badAbilities),
                Set.copyOf(GlobalConstants.doubleBattleAbilities), Set.copyOf(handler.getUselessAbilities()),
                Set.copyOf(duplicates), handler.getAbilityVariations());
    }

    /**
     * CFRU e68a701aa4e68733ef8ad1e7cadb68825c0d16c2, include/constants/abilities.h.
     * Every ID 1..FE is named. Forecast is Castform-only; Portal Power is disabled
     * in src/config.h (damage_calc.c). No reserved numeric holes exist at this pin.
     * Shared numeric aliases are naturally single choices, including the Gen9 leeches:
     * classification follows the numeric runtime owner, not the displayed alias name.
     * Only Battle Armor/Shell Armor share a proven distinct-ID equivalence
     * (damage_calc.c crit checks). Other generic duplicates have been repurposed or
     * have distinct runtime semantics; notably Moxie and Grim Neigh boost different stats.
     */
    public static AbilityRandomizationPolicy cfruDpe() {
        return CFRU_DPE;
    }

    private static final AbilityRandomizationPolicy CFRU_DPE = new AbilityRandomizationPolicy(
            IntStream.rangeClosed(1, 0xFE).filter(id -> id != 0x3B && id != 0xD0).boxed().toList(),
            0x19, // Wonder Guard
            Set.of(0x17, 0x2A, 0x47), // Shadow Tag, Magnet Pull, Arena Trap
            Set.of(0x90, 0x91, 0x36, 0xCC, 0xB3), // Defeatist, Slow Start, Truant, Klutz, Stall
            Set.of(0x3A, 0x39, 0xBC, 0xBD, 0xBE, 0xDF, 0x86, 0xEA),
            // Minus, Plus, Anticipation, Forewarn, Frisk, Honey Gather, Aura Break, Receiver.
            // Power of Alchemy has no independent numeric owner; Curious Medicine (EB) is different.
            Set.of(0xE0, 0x6C, 0xE2, 0xE5, 0xE9), // Friend Guard, Healer, Telepathy, Symbiosis, Battery
            Set.of(0x3B, 0xD0), Set.of(0x4B),
            Map.of(0x04, List.of(0x04, 0x4B)));
}
