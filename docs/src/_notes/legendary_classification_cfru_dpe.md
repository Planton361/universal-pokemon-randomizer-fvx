# CFRU/DPE Legendary/Mythical classification — Workspace #677

Contract: [Workspace #677](https://github.com/Planton361/firered-gen9-randomizer-workspace/issues/677).
Runtime witness: [#676 CONTROL blocker](https://github.com/Planton361/firered-gen9-randomizer-workspace/issues/676#issuecomment-6026039172).

**CONFIRMED CURRENT STATE:** On UPR base
`48f657b3958bc2ff902fe85ca866ba11c5250a50`, the sanitized Casual evidence
has seed `20261005658`, trainer-misc byte 29 `0x98` (Block Legendaries,
No Early Wonder Guard, Avoid Duplicates), yet trainer #1 selects Arceus.
This is a classification defect, not a settings/operator error.

**INTENDED FUTURE STATE:** Component review/merge, separate Workspace integration
and revision-bound C/I runtime acceptance remain pending. This change supplies
ROM-free production-path evidence only.

## Architecture and scope

`RomHandler.getSpeciesClassificationPolicy()` defaults to a singleton immutable
legacy policy delegating to the unchanged `Species.isLegendary()`.
`Gen3RomHandler` selects the CFRU/DPE singleton only under its existing
`useCfruDpeGen9SpeciesCount` detection flag.
`RestrictedSpeciesService` applies that policy to both complementary Legendary
and non-Legendary partitions after the existing #664 eligibility filter.
Starter, Trainer and Wild selectors consume those partitions without new
CFRU/DPE exceptions. No selection/RNG algorithm is changed.

The CFRU/DPE policy uses `getSpeciesSetIdentityNumber()` rather than display
names or generic dex numbers. Explicit DPE base/form rows classify forms loaded
as independent identities; modeled forms also inherit their base owner's
classification. Classification never grants eligibility: #664 still rejects
battle/temporary/condition-dependent/asset-unsafe rows, even with the generic
form options enabled. There is no form expansion.

The other generic Species helpers (strong Legendary, Ultra Beast), ability,
evolution, moveset, held-item, AI, QoL and engine policies are unchanged.
Other consumers of the central partitions receive the corrected partitions;
this does not add a new category or refactor their separate settings semantics.
Legacy Java proxy fixtures explicitly return the same legacy policy because
InvocationHandler proxies do not automatically execute interface defaults.
Those fixture edits change no assertions or production policies.

## Revision-bound inventory

Internal IDs are bound to [DPE include/species.h](https://github.com/Planton361/Dynamic-Pokemon-Expansion-Gen-9/blob/d887185de1f6ae6a78e85c4311bbadde17041d00/include/species.h).
Base/form dex ownership is cross-checked against that pin's
[Species_To_Pokdex_Table.c](https://github.com/Planton361/Dynamic-Pokemon-Expansion-Gen-9/blob/d887185de1f6ae6a78e85c4311bbadde17041d00/src/Species_To_Pokdex_Table.c)
and the existing #664 source-only 1,440-row inventory. Ogerpon Tera rows have no
dex-table entry at this pin; their named species.h family still has the Ogerpon
semantic owner, and #664 excludes them from selection.

Semantics retain every one of the 68 owners in the base UPR `Species.java`
Legendary list, including Mythicals, Phione, Type: Null, Silvally, Cosmog and
Cosmoem. The 26 later owners are the entries tagged Sub-Legendary, Restricted
Legendary or Mythical in the project's already pinned
[Showdown pokedex.ts](https://github.com/smogon/pokemon-showdown/blob/b1156ff19204e48089e2384eb2c9c1a8004f57ce/data/pokedex.ts).
The full 94-owner union independently equals that reference's full base-owner
Legendary/Mythical tag set: 44 Sub-Legendary + 27 Restricted Legendary + 23 Mythical.
Ultra Beasts and all Paradox-only entries remain outside the category, including
Walking Wake, Iron Leaves, Gouging Fire, Raging Bolt, Iron Boulder and Iron Crown.
Koraidon/Miraidon carry the Restricted Legendary tag and remain blocked.

Exact counts:

| Inventory | Count |
|---|---:|
| Semantic base owners (Gen1–9) | 94 |
| Encoded base/form rows | 187 |
| #664 source-safe classified rows | 120 |
| Classified rows already excluded by #664 | 67 |

Base owners by introduction generation: Gen1 5; Gen2 6; Gen3 10; Gen4 14;
Gen5 13; Gen6 6; Gen7 16 (including Meltan/Melmetal); Gen8 12 (including Enamorus);
Gen9 12. All bases and every form-to-base binding are listed in
`romio/src/testFixtures/resources/cfru-dpe/legendary-species-inventory.tsv`,
including source revisions and source-text SHA256s. Reconstruct the binding by
resolving the 68 UPR names and 26 later tagged owners to species.h symbols,
including each named base/form family, with Urshifu Rapid/Single sharing the
Urshifu Single base owner. Cross-check IDs/symbols/dex ownership against #664's
inventory. No ROM/dex decoding, display-name heuristic or runtime dependency
is used by the production policy.

| Witness | DPE internal ID | Result |
|---|---|---|
| Arceus | `0x222` | old generic classifier false; policy true |
| Uxie | `0x215` | old generic classifier false; policy true |
| Meloetta | `0x2BD` | old generic classifier false; policy true |
| Deoxys | `0x19A` | old generic classifier false; policy true |
| Zacian (Gen8) | `0x49C` | policy true |
| Koraidon (Gen9 Legendary) | `0x57D` | policy true |
| Pecharunt (Gen9 Mythical) | `0x59F` | policy true |

Ordinary positive controls: Bulbasaur `0x001`, Treecko `0x115`, Grookey `0x44E`,
Sprigatito `0x50E`. Internal `493` (Lopunny) also remains non-Legendary despite
colliding with generic Arceus. Tests deliberately separate internal identity
from generic/display numbers in both directions.

## ROM-free verification

The 5 new romio tests check all 187 rows, all 94 owners, the complete internal
negative space, collision witnesses, modeled/independent forms, the actual
Gen3 detection flag, a non-Gen3 default, and unchanged legacy semantics for
all internal numbers and modeled base relationships.

The 6 new random tests run the actual Starter/Trainer/Wild selectors with the
actual Gen3 policy and synthetic in-memory stats/learnsets/asset pointers.
Their complete input includes all 187 classified rows, four ordinary controls,
a battle-only ordinary form and a deliberately invalid front-sprite pointer.
Every no-Legendary candidate partition equals the ordinary controls; all 120
otherwise eligible classified rows are excluded, and the other 67 remain
excluded by #664. Starters and duplicate-free trainer teams contain every
ordinary control; Catch 'Em All Wild selects every control. OFF still allows
Arceus. Legacy trainer seeds `0`, `1`, `677`, `20261005658` retain the pre-policy
sequence.

The deterministic pre-fix trainer witness overrides only the policy with the
old generic classifier, retaining the detected handler and real #664 checks.
The two-species fixture (Bulbasaur/Arceus), serialized byte `0x98` and seed
`20261005658` emits trainer #1 Arceus before repair, then Bulbasaur after repair,
with deterministic replay. This reproduces the failure mechanism without
claiming to recreate the private full-ROM RNG stream.

Final relevant result, verified from JUnit XML (Gradle ignoreFailures disabled):

| Module | Suites | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| romio | 23 | 1,628 | 0 | 0 | 0 |
| random | 24 | 150 | 0 | 0 | 0 |
| Total | 47 | 1,778 | 0 | 0 | 0 |

This includes existing #664 1,442 inventory tests + 11 production safety tests,
asset guard, generation restrictions, ability, evolution, learnset/move,
Trainer special/additional/identity, Wild, starter/rival and settings regressions.
`git diff --check = PASS`.

For exact replay, save the following as a temporary Gradle init script, then run
`./gradlew -I /tmp/upr-677-relevant-tests.gradle :romio:test :random:test --console=plain`:

```groovy
allprojects {
    afterEvaluate {
        tasks.withType(Test).configureEach {
            ignoreFailures = false
            filter {
                setExcludePatterns('*RomTest*', '*RomSmokeTest*', '*RomHandler*Test')
                if (project.name == 'romio') {
                    setIncludePatterns('com.uprfvx.romio.services.*Test',
                        '*SpeciesSetTest', '*GenRestrictionsTest', '*Gen3CfruDpe*Test',
                        '*Gen3TrainerMovesetIdentityTest', '*Gen3Evolution*Test', '*Evolution*Test')
                }
                if (project.name == 'random') {
                    setIncludePatterns('*CfruDpe*Test', '*GameRandomizer*Test',
                        '*TrainerTypeDiversityGuardTest', '*TrainerHeldItemIdentityTest',
                        '*TrainerAdditionalPokemonTest', '*TrainerSpecialRulesTest', '*TrainerBattleStyleTest',
                        '*TrainerNameRandomizerTest', '*TrainerClassSpriteSyncRandomizerTest', '*TradeRandomizerTest',
                        '*TrainerMovesetDecisionTest', '*LearnsetDecisionTest', '*SpeciesAbilityDecisionTest',
                        '*WildCatchLevelDecisionTest', '*EvolutionFilterOptionsTest', '*ItemDecisionTest',
                        '*TradeDecisionTest', '*TMTutorMoveDecisionTest',
                        '*SettingsProfileGeneratorTest')
                }
            }
        }
    }
}
```

## Existing failures outside the classification gate

An expanded candidate selection additionally ran IntroPokemonDecisionTest:
5 tests, 3 assertion failures, 0 errors/skips. Those three assertions expect
invalid identity-zero entries to reach the Intro writer despite the existing
SpecialFormPredicates baseline filter. The default romio selection also exposed
PlayerCharacterGraphicsTest's RSE separate/sheet equality failure.

Both classes were replayed after temporarily restoring the three changed
production files and the Intro test to their exact base-commit contents; the
new #677 tests were excluded from compilation for that replay. The same four
assertion failures occurred (the two named classes: 9 tests, 4 failures,
0 errors/skips). Candidate sources were restored before the final green run.
These are reproduced base-behavior failures, not #677 regressions. They are
reported rather than repaired or changing their assertions. The Graphics
failure's underlying environment/resource cause remains UNKNOWN. The final
classification gate excludes these two unrelated classes and does not claim a
green unrestricted whole-repository suite.

The first exploratory multi-task command accidentally used the romio task's
default test selection because CLI --tests options scoped to the final random
task. That romio selection reported 8 skips; no ROM path/property was supplied
and no ROM artifact was accessed. The final acceptance selection explicitly
excludes opt-in ROM test classes, reports zero skips and runs no testROMs task.

## Artifact safety and handoff

Workspace main `762054f19c6f070110b2fde3f08e90697a604cd5`, CFRU
`e68a701aa4e68733ef8ad1e7cadb68825c0d16c2`, DPE
`d887185de1f6ae6a78e85c4311bbadde17041d00`, pret
`e060ab955b5dc9ac1c4904c2cd141683615cf477` and all Workspace Gitlinks are unchanged.
UPR target was verified at the exact base before creating
`fix/677-cfru-dpe-legendary-classification`.

No ROMs, randomizer outputs, saves/states, private builds, .env/secrets or tool
binary contents were inspected. Gradle/JUnit compiled and executed source tests;
no Lunar invocation or release build occurred. `gradle-wrapper.jar` and
`Lunar_Compress_1.90_x64.dll` remain `BASELINE_TRACKED_UNTOUCHED`.

`UPR_CFRU_DPE_LEGENDARY_CLASSIFICATION_POLICY_READY`

`Runtime/ROM = NOT_RUN`; `PR NOT MERGED`; `UPSTREAM_CONTRIBUTION = DEFERRED`.
No product-scope CONFLICT was found. Runtime effectiveness on a newly generated
private Casual/IronMON output remains UNKNOWN until the separate acceptance gate.

Exactly one next step: CONTROL reviews the exact UPR-FVX PR head for #677 and,
on PASS, hands it to the user for the merge decision.
