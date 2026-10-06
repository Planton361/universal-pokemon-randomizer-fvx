package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.services.AbilityRandomizationPolicy;
import com.uprfvx.romio.gamedata.MegaEvolution;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpeciesAbilityRandomizer extends Randomizer {

    public SpeciesAbilityRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    public void randomizeAbilities() {
        boolean evolutionSanity = settings.isAbilitiesFollowEvolutions();
        boolean allowWonderGuard = settings.isAllowWonderGuard();
        boolean banTrappingAbilities = settings.isBanTrappingAbilities();
        boolean banNegativeAbilities = settings.isBanNegativeAbilities();
        boolean banBadAbilities = settings.isBanBadAbilities();
        boolean megaEvolutionSanity = settings.isAbilitiesFollowMegaEvolutions();
        boolean weighDuplicatesTogether = settings.isWeighDuplicateAbilitiesTogether();
        boolean ensureTwoAbilities = settings.isEnsureTwoAbilities();
        boolean isMultiBattleOnly = settings.getBattleStyle().isOnlyMultiBattles();

        // Abilities don't exist in some games...
        if (romHandler.abilitiesPerSpecies() == 0) {
            return;
        }

        final boolean hasHiddenAbilities = (romHandler.abilitiesPerSpecies() == 3);

        final AbilityRandomizationPolicy policy = romHandler.getAbilityRandomizationPolicy();
        final List<Integer> bannedAbilities = new ArrayList<>(policy.useless());

        if (!allowWonderGuard) {
            bannedAbilities.add(policy.wonderGuard());
        }

        if (banTrappingAbilities) {
            bannedAbilities.addAll(policy.trapping());
        }

        if (banNegativeAbilities) {
            bannedAbilities.addAll(policy.negative());
        }

        if (banBadAbilities) {
            bannedAbilities.addAll(policy.bad());
            if (!isMultiBattleOnly) {
                bannedAbilities.addAll(policy.doubleBattle());
            }
        }

        if (weighDuplicatesTogether) {
            bannedAbilities.addAll(policy.duplicateExclusions());
        }

        final int maxAbility = romHandler.highestAbilityIndex();

        // copy abilities straight up evolution lines
        // still keep WG as an exception, though
        copyUpEvolutionsHelper.apply(evolutionSanity, false, pk -> {
            if (!isAbilityRandomizationCandidate(pk, maxAbility)) {
                return;
            }
            if (pk.getAbility1() != policy.wonderGuard() && pk.getAbility2() != policy.wonderGuard()
                    && pk.getAbility3() != policy.wonderGuard()) {
                // Pick first ability
                pk.setAbility1(pickRandomAbility(policy, bannedAbilities, weighDuplicatesTogether));

                // Second ability?
                if (ensureTwoAbilities || random.nextDouble() < 0.5) {
                    // Yes, second ability
                    pk.setAbility2(pickRandomAbility(policy, bannedAbilities, weighDuplicatesTogether,
                            pk.getAbility1()));
                } else {
                    // Nope
                    pk.setAbility2(0);
                }

                // Third ability?
                if (hasHiddenAbilities) {
                    pk.setAbility3(pickRandomAbility(policy, bannedAbilities, weighDuplicatesTogether,
                            pk.getAbility1(), pk.getAbility2()));
                }
            }
        }, (evFrom, evTo, toMonIsFinalEvo) -> {
            if (!isAbilityRandomizationCandidate(evFrom, maxAbility)
                    || !isAbilityRandomizationCandidate(evTo, maxAbility)) {
                return;
            }
            if (evTo.getAbility1() != policy.wonderGuard() && evTo.getAbility2() != policy.wonderGuard()
                    && evTo.getAbility3() != policy.wonderGuard()) {
                evTo.setAbility1(evFrom.getAbility1());
                evTo.setAbility2(evFrom.getAbility2());
                evTo.setAbility3(evFrom.getAbility3());
            }
        });


        romHandler.getSpeciesSetInclFormes().filter(Species::isActuallyCosmetic)
                .filter(pk -> isAbilityRandomizationCandidate(pk.getBaseForme(), maxAbility))
                .forEach(pk -> pk.copyBaseFormeAbilities(pk.getBaseForme()));

        if (megaEvolutionSanity) {
            for (MegaEvolution megaEvo : romHandler.getMegaEvolutions()) {
                if (megaEvo.getFrom().getMegaEvolutionsFrom().size() > 1)
                    continue;
                if (!isAbilityRandomizationCandidate(megaEvo.getFrom(), maxAbility)
                        || !isAbilityRandomizationCandidate(megaEvo.getTo(), maxAbility)) {
                    continue;
                }
                megaEvo.getTo().setAbility1(megaEvo.getFrom().getAbility1());
                megaEvo.getTo().setAbility2(megaEvo.getFrom().getAbility2());
                megaEvo.getTo().setAbility3(megaEvo.getFrom().getAbility3());
            }
        }

        changesMade = true;
    }

    private boolean isAbilityRandomizationCandidate(Species species, int maxAbility) {
        if (species == null || species.getBST() == 0) {
            return false;
        }
        if (species.getAbility1() == 0 && species.getAbility2() == 0 && species.getAbility3() == 0) {
            return false;
        }
        return isAbilityIdValidForRandomization(species.getAbility1(), maxAbility)
                && isAbilityIdValidForRandomization(species.getAbility2(), maxAbility)
                && isAbilityIdValidForRandomization(species.getAbility3(), maxAbility);
    }

    private boolean isAbilityIdValidForRandomization(int ability, int maxAbility) {
        return ability >= 0 && ability <= maxAbility;
    }

    private int pickRandomAbilityVariation(AbilityRandomizationPolicy policy, int selectedAbility, int... alreadySetAbilities) {
        int newAbility = selectedAbility;

        while (true) {
            Map<Integer, List<Integer>> abilityVariations = policy.variations();
            for (int baseAbility: abilityVariations.keySet()) {
                if (selectedAbility == baseAbility) {
                    List<Integer> variationsForThisAbility = abilityVariations.get(selectedAbility);
                    newAbility = variationsForThisAbility.get(random.nextInt(variationsForThisAbility.size()));
                    break;
                }
            }

            boolean repeat = false;
            for (int alreadySetAbility : alreadySetAbilities) {
                if (alreadySetAbility == newAbility) {
                    repeat = true;
                    break;
                }
            }

            if (!repeat) {
                break;
            }
        }

        return newAbility;
    }

    private int pickRandomAbility(AbilityRandomizationPolicy policy, List<Integer> bannedAbilities, boolean useVariations,
                                  int... alreadySetAbilities) {
        int newAbility;

        while (true) {
            newAbility = policy.candidateIds().get(random.nextInt(policy.candidateIds().size()));

            if (bannedAbilities.contains(newAbility)) {
                continue;
            }

            boolean repeat = false;
            for (int alreadySetAbility : alreadySetAbilities) {
                if (alreadySetAbility == newAbility) {
                    repeat = true;
                    break;
                }
            }

            if (!repeat) {
                if (useVariations) {
                    newAbility = pickRandomAbilityVariation(policy, newAbility, alreadySetAbilities);
                }
                break;
            }
        }

        return newAbility;
    }
}
